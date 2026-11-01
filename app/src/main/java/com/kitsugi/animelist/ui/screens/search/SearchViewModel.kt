package com.kitsugi.animelist.ui.screens.search

import android.app.Application
import com.kitsugi.animelist.data.platform.ListPlatform
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiCountryOfOrigin
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaFormat
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSeason
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSortSearch
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSource
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.repository.SearchHistoryRepository
import com.kitsugi.animelist.data.remote.KitsugiBangumiClient
import com.kitsugi.animelist.data.remote.KitsugiShikimoriClient
import com.kitsugi.animelist.data.remote.KitsuExploreClient
import com.kitsugi.animelist.data.remote.SimklApiClient
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/** Common format chips constrain TMDB's two media families as well as AniList/MAL. */
private fun List<String>.forMediaType(mediaType: MediaType): List<String> {
    val printTypes = setOf("manga", "novel", "one_shot", "one-shot", "one shot", "manhwa", "manhua", "lightnovel", "light_novel")
    return filter { (it.lowercase() in printTypes) == (mediaType == MediaType.Manga) }
}

private fun List<MediaType>.filterByCommonFormats(state: SearchUiState): List<MediaType> {
    if (state.selectedFormats.isEmpty()) return this
    val allowed = state.selectedFormats.mapNotNull { format ->
        when (format.apiValue) {
            "MOVIE" -> MediaType.Movie
            "TV", "TV_SHORT", "SPECIAL", "OVA", "ONA", "MUSIC" -> MediaType.TvShow
            // TMDB has no print catalog.  A manga-only common selection must not
            // accidentally return unrelated movies or TV shows.
            else -> null
        }
    }.toSet()
    return filter { it in allowed }
}

/**
 * Search ViewModel.
 * MoeList SearchViewModel.kt ve AniHyou SearchViewModel.kt'den ilham alÄ±narak
 * Kitsugi'nun JikanApiClient (Jikan + AniList fallback) altyapÄ±sÄ±na uyarlanmÄ±ÅŸtÄ±r.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = JikanApiClient()
    private val settingsDataStore = SettingsDataStore(application)
    private val database = KitsugiDatabase.getDatabase(application)
    private val searchHistoryRepository = SearchHistoryRepository(database.searchHistoryDao())

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var showAdultContentState = false
    private var searchHistoryEnabledState = true
    private var searchJob: Job? = null
    private var undoHistoryDismissJob: Job? = null

    // ── Ortak filtre çatısı: kurum (stüdyo/yapımcı/şirket/kanal) araması ────
    private val _institutionSearchResults = MutableStateFlow<List<JikanSearchResult>>(emptyList())
    val institutionSearchResults: StateFlow<List<JikanSearchResult>> = _institutionSearchResults.asStateFlow()
    private var institutionSearchJob: Job? = null

    private val mixedMediaEngines = setOf(
        SearchSourceEngine.ANILIST, SearchSourceEngine.MAL,
        SearchSourceEngine.SHIKIMORI, SearchSourceEngine.KITSU,
        SearchSourceEngine.BANGUMI
    )

    /**
     * Arama nesli (generation) sayacÄ±.
     *
     * Her yeni arama bu sayacÄ± artÄ±rÄ±r. Arka planda kalan iptal edilmiÅŸ bir Ã§alÄ±ÅŸma
     * yalnÄ±zca kendi nesli gÃ¼ncel nesle eÅŸitse UI durumuna yazabilir; aksi halde eski
     * sorgunun (Ã§oÄŸu zaman boÅŸ/baÅŸarÄ±sÄ±z) sonuÃ§larÄ± yeni sorgunun Ã¼zerine yazÄ±yor ve
     * "TÃ¼mÃ¼" ekranÄ±nda kaynaklara gÃ¶re karÄ±ÅŸÄ±k/eksik sonuÃ§ gÃ¶rÃ¼nÃ¼yordu.
     */
    private val searchGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * "TÃ¼mÃ¼" aramasÄ±nda tek bir kaynaÄŸÄ±n yanÄ±tÄ± iÃ§in tanÄ±nan sÃ¼re. Eski deÄŸer 7 sn idi;
     * yavaÅŸ mobil baÄŸlantÄ±larda (Ã¶r. 125 KB/s) AniList/MAL yanÄ±tÄ± bu sÃ¼reyi aÅŸÄ±nca kaynak
     * tamamen kayboluyor ve kullanÄ±cÄ± "aradÄ±ÄŸÄ±m ÅŸey Ã§Ä±kmÄ±yor" durumuyla karÅŸÄ±laÅŸÄ±yordu.
     * Åeffaf yÃ¼kleme durumu (shimmer) korunduÄŸu iÃ§in sÃ¼re artÄ±ÅŸÄ± UX'i bozmaz.
     */
    private val allSourceTimeoutMs = 20_000L

    /** "TÃ¼mÃ¼" rafÄ±nda gÃ¶sterilecek kart sayÄ±sÄ±. */
    private val allShelfLimit = 10

    /**
     * "TÃ¼mÃ¼" rafÄ±nda kaynaklardan Ã§ekilecek aday havuzu.
     *
     * GÃ¶sterilenden geniÅŸ tutulur: alakalÄ±lÄ±k sÄ±ralamasÄ±nÄ±n yeniden dizmeye alanÄ±
     * olur, bÃ¶ylece kaynaÄŸÄ±n popÃ¼lerlik/gevÅŸek eÅŸleÅŸme sÄ±ralamasÄ± rafÄ±n baÅŸÄ±nÄ±
     * iÅŸgal edemez.
     */
    private val allShelfFetchLimit = 20

    /**
     * Arama ekranÄ±nda kaynak seÃ§imi kalÄ±cÄ±lÄ±ÄŸÄ±: kullanÄ±cÄ± hangi kaynaÄŸÄ±/kapsamÄ± seÃ§tiyse
     * uygulama yeniden aÃ§Ä±ldÄ±ÄŸÄ±nda oradan devam eder.
     */
    private var searchSelectionRestored = false
    private var restoringSearchSelection = false

    private data class TabSearchState(
        val query: String = "",
        val results: List<JikanSearchResult> = emptyList(),
        val hasSearched: Boolean = false,
        val errorMessage: String? = null
    )

    /**
     * Immutable request boundary.  Paging must not read the live filter sheet state: a
     * user can open the sheet while page 2 is in flight.  The generation and this snapshot
     * make every page of one search use the same source, scope and effective filters.
     */
    private data class SearchRequestSnapshot(
        val state: SearchUiState,
        val showAdult: Boolean,
        val generation: Int
    )

    @Volatile
    private var activeSearchSnapshot: SearchRequestSnapshot? = null

    private val stateCache = mutableMapOf<String, TabSearchState>()

    private fun getTabKey(tab: KitsugiSearchTab, platform: SearchPlatform, mediaType: MediaType): String {
        return "${tab.name}_${platform.name}_${mediaType.name}"
    }

    private fun saveCurrentStateToCache() {
        val currentState = _uiState.value
        val key = getTabKey(currentState.currentTab, currentState.selectedPlatform, currentState.selectedMediaType)
        stateCache[key] = TabSearchState(
            query = currentState.query,
            results = currentState.results,
            hasSearched = currentState.hasSearched,
            errorMessage = currentState.errorMessage
        )
    }

    private fun loadStateFromCache(tab: KitsugiSearchTab = _uiState.value.currentTab, platform: SearchPlatform, mediaType: MediaType): TabSearchState {
        val key = getTabKey(tab, platform, mediaType)
        return stateCache[key] ?: TabSearchState()
    }

    /** Sekme + motor + kapsam + platform + tÃ¼r seÃ§imini disk'e yazar. */
    private fun persistSearchSelection() {
        if (restoringSearchSelection) return
        val state = _uiState.value
        viewModelScope.launch {
            runCatching {
                settingsDataStore.setLastSearchEngineAndScope(state.selectedEngine.name, state.selectedScope.name)
                settingsDataStore.setLastSearchSelection(state.currentTab.name, state.selectedPlatform.name, state.selectedMediaType.name)
            }
        }
    }

    /**
     * KalÄ±cÄ± seÃ§im geri yÃ¼klenirken kaynak motoruna gÃ¶re UI durumunu senkron tutar.
     * Arama baÅŸlatmaz â€” aÃ§Ä±lÄ±ÅŸta yalnÄ±zca seÃ§ili gÃ¶rÃ¼nen kaynak dÃ¼zeltilir.
     */
    private fun applySearchSelection(engine: SearchSourceEngine, scope: SearchScope, tab: KitsugiSearchTab) {
        val platform = when (engine) {
            SearchSourceEngine.ALL -> SearchPlatform.All
            SearchSourceEngine.ANILIST -> SearchPlatform.AniList
            SearchSourceEngine.MAL -> SearchPlatform.MAL
            SearchSourceEngine.TMDB -> SearchPlatform.TMDB
            SearchSourceEngine.SHIKIMORI -> SearchPlatform.Shikimori
            SearchSourceEngine.KITSU -> SearchPlatform.Kitsu
            SearchSourceEngine.SIMKL -> SearchPlatform.Simkl
            SearchSourceEngine.BANGUMI -> SearchPlatform.Bangumi
        }
        val mediaType = when (scope) {
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> MediaType.Manga
            SearchScope.MOVIE -> MediaType.Movie
            SearchScope.TV -> MediaType.TvShow
            else -> MediaType.Anime
        }
        _uiState.update {
            it.copy(
                selectedEngine = engine,
                selectedScope = scope,
                currentTab = tab,
                selectedPlatform = platform,
                selectedMediaType = mediaType
            )
        }
    }

    init {
        viewModelScope.launch {
            // KalÄ±cÄ± kaynak seÃ§imini uygula (kullanÄ±cÄ± henÃ¼z arama yapmadÄ±ysa)
            if (!searchSelectionRestored) {
                searchSelectionRestored = true
                val engineName = runCatching { settingsDataStore.lastSearchEngineFlow.firstOrNull() }.getOrNull().orEmpty()
                val scopeName = runCatching { settingsDataStore.lastSearchScopeFlow.firstOrNull() }.getOrNull().orEmpty()
                val engine = SearchSourceEngine.entries.firstOrNull { it.name == engineName }
                val scope = SearchScope.entries.firstOrNull { it.name == scopeName }
                if (engine != null && engine != _uiState.value.selectedEngine &&
                    _uiState.value.query.isBlank() && !_uiState.value.hasSearched
                ) {
                    val scopes = engine.availableScopes()
                    val effectiveScope = scope?.takeIf { it in scopes } ?: scopes.first()
                    restoringSearchSelection = true
                    applySearchSelection(
                        engine = engine,
                        scope = effectiveScope,
                        tab = when (effectiveScope) {
                            SearchScope.CHARACTER -> KitsugiSearchTab.Character
                            SearchScope.STAFF -> KitsugiSearchTab.Staff
                            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> KitsugiSearchTab.Manga
                            else -> when (engine) {
                                SearchSourceEngine.ALL -> KitsugiSearchTab.All
                                SearchSourceEngine.ANILIST -> KitsugiSearchTab.Anime
                                SearchSourceEngine.MAL -> KitsugiSearchTab.MAL
                                SearchSourceEngine.TMDB -> KitsugiSearchTab.TMDB
                                SearchSourceEngine.SHIKIMORI -> KitsugiSearchTab.Shikimori
                                SearchSourceEngine.KITSU -> KitsugiSearchTab.Kitsu
                                SearchSourceEngine.SIMKL -> KitsugiSearchTab.Simkl
                                SearchSourceEngine.BANGUMI -> KitsugiSearchTab.Bangumi
                            }
                        }
                    )
                    restoringSearchSelection = false
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                showAdultContentState = settings.showAdultContent
                searchHistoryEnabledState = settings.searchHistoryEnabled
                _uiState.update { it.copy(searchHistoryEnabled = settings.searchHistoryEnabled) }
            }
        }
        viewModelScope.launch {
            searchHistoryRepository.getRecentSearchHistory().collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
    }

    fun setQuery(value: String) {
        if (value != _uiState.value.query) {
            // Metin deÄŸiÅŸtiÄŸi anda eski sorgunun sonuÃ§/isteklerini geÃ§ersiz kÄ±l.
            // Yazarken eski sorgunun sonuÃ§larÄ± yeni metnin altÄ±nda gÃ¶rÃ¼nmemeli.
            searchGeneration.incrementAndGet()
            searchJob?.cancel()
            _uiState.update {
                it.copy(
                    query = value,
                    results = emptyList(),
                    multiResults = MultiPlatformResults(),
                    isLoading = false,
                    hasSearched = false,
                    errorMessage = null,
                    hasNextPage = false
                )
            }
        }
        saveCurrentStateToCache()

        if (value.isBlank() && !_uiState.value.hasFiltersApplied) {
            clearResults()
        }
        // NOT: Burada bilinÃ§li olarak arama BAÅLATILMIYOR.
        //
        // Eski davranÄ±ÅŸ her tuÅŸ vuruÅŸundan 400 ms sonra `search()` Ã§aÄŸÄ±rÄ±yordu
        // (debounced live search). KullanÄ±cÄ± daha "naruto" yazarken "n", "na",
        // "nar" ... sorgularÄ± yedi motora birden gidiyor, yarÄ±da kesilen istekler
        // raflarda alakasÄ±z/eksik sonuÃ§ bÄ±rakÄ±yordu. Arama artÄ±k yalnÄ±zca aÃ§Ä±k bir
        // onayla tetiklenir: klavyedeki "Ara" tuÅŸu veya arama butonu â†’ [onSearchAction].
    }

    /** Klavyedeki "Ara" onay tuÅŸu veya arama butonu: aramayÄ± baÅŸlatan tek yol. */
    fun onSearchAction() {
        search(resetPage = true)
    }

    /** RafÄ±n sorgusunu, kapsamÄ±nÄ± ve gerÃ§ekten gÃ¶rÃ¼nen kayÄ±tlarÄ±nÄ± tek seferde devral. */
    fun openSourceSearch(
        engine: SearchSourceEngine,
        query: String,
        scope: SearchScope,
        shelf: List<JikanSearchResult>,
        carryOver: CommonSearchFilters? = null
    ) {
        searchGeneration.incrementAndGet()
        searchJob?.cancel()
        val selectedScope = scope.takeIf { it in engine.availableScopes() } ?: engine.availableScopes().first()
        val mediaType = when (selectedScope) {
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> MediaType.Manga
            SearchScope.TV -> MediaType.TvShow
            SearchScope.MOVIE -> MediaType.Movie
            else -> MediaType.Anime
        }
        val tab = when (engine) {
            SearchSourceEngine.ANILIST -> KitsugiSearchTab.Anime
            SearchSourceEngine.MAL -> KitsugiSearchTab.MAL
            SearchSourceEngine.TMDB -> KitsugiSearchTab.TMDB
            SearchSourceEngine.SHIKIMORI -> KitsugiSearchTab.Shikimori
            SearchSourceEngine.KITSU -> KitsugiSearchTab.Kitsu
            SearchSourceEngine.SIMKL -> KitsugiSearchTab.Simkl
            SearchSourceEngine.BANGUMI -> KitsugiSearchTab.Bangumi
            SearchSourceEngine.ALL -> KitsugiSearchTab.All
        }
        val inherited = carryOver
            ?: SearchFilterHandoff.consume()
            ?: SearchCommonFiltersBridge.harvest(_uiState.value)
        val projected = SearchCommonFiltersBridge.applyTo(
            state = _uiState.value,
            target = engine,
            common = inherited,
            scope = selectedScope
        )
        _uiState.update {
            projected.copy(
                query = query,
                selectedEngine = engine,
                selectedScope = selectedScope,
                currentTab = tab,
                selectedPlatform = when (engine) {
                    SearchSourceEngine.ANILIST -> SearchPlatform.AniList
                    SearchSourceEngine.MAL -> SearchPlatform.MAL
                    SearchSourceEngine.TMDB -> SearchPlatform.TMDB
                    SearchSourceEngine.SHIKIMORI -> SearchPlatform.Shikimori
                    SearchSourceEngine.KITSU -> SearchPlatform.Kitsu
                    SearchSourceEngine.SIMKL -> SearchPlatform.Simkl
                    SearchSourceEngine.BANGUMI -> SearchPlatform.Bangumi
                    SearchSourceEngine.ALL -> SearchPlatform.All
                },
                selectedMediaType = mediaType,
                results = shelf,
                hasSearched = shelf.isNotEmpty(),
                errorMessage = null,
                page = 1,
                hasNextPage = false
            )
        }
        // Taşınan filtreler tek başına da aramayı tetikler: "Tümü" sayfasında
        // sorgu yazmadan yalnızca tür seçen "Tümünü Gör" sayfası boş açılmasın.
        if (query.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(seedResults = shelf)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ORTAK FİLTRE ÇATISI (Common Filter Roof) — genel API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kanonik filtre setini mevcut ekrana uygular.
     * "Tümü" motorunda yedi kaynağın paketi birden güncellenir.
     */
    fun applyCommonFilters(common: CommonSearchFilters, triggerSearch: Boolean = true) {
        _uiState.update { current ->
            SearchCommonFiltersBridge.applyTo(current, current.selectedEngine, common)
        }
        if (triggerSearch) search(resetPage = true)
    }

    /**
     * Ortak puan aralığını uygular.
     * Kanonik set her çağrıda taze okunur ([SearchCommonFiltersBridge.harvest]);
     * böylece kaydırıcı sürüklenirken başka bir kanonik alan (ör. yıl) yanlışlıkla
     * eski değerine döndürülmez.
     */
    fun setCommonScoreRange(minScore: Int?, maxScore: Int?, triggerSearch: Boolean = false) {
        val common = SearchCommonFiltersBridge.harvest(_uiState.value).copy(
            minScore = minScore,
            maxScore = maxScore
        )
        applyCommonFilters(common, triggerSearch)
    }

    /** Ortak yayın yılı aralığını uygular (puan aralığına aynı tazelik kuralı). */
    fun setCommonYearRange(startYear: Int?, endYear: Int?, triggerSearch: Boolean = false) {
        val common = SearchCommonFiltersBridge.harvest(_uiState.value).copy(
            startYear = startYear,
            endYear = endYear
        )
        applyCommonFilters(common, triggerSearch)
    }

    /** Kurum seçimi: kaynağın kendi paketine yazılır, kanonik listede korunur. */
    fun selectInstitution(
        engine: SearchSourceEngine,
        kind: CommonInstitutionKind,
        id: Int,
        name: String
    ) {
        if (id <= 0 || name.isBlank()) return
        // "Tümü" ekranında kurum türü hangi kaynağa aitse oraya yazılır.
        val targetEngine = if (engine == SearchSourceEngine.ALL) {
            SearchCommonFiltersBridge.engineForInstitutionKind(kind)
        } else {
            engine
        }
        if (!SearchCommonFiltersBridge.supportsInstitutions(targetEngine)) return
        val entry = CommonInstitution(
            engine = targetEngine,
            id = id,
            name = name.trim(),
            kind = kind
        )
        _uiState.update { current ->
            val merged = current.institutions
                .filterNot { it.engine == entry.engine && it.kind == entry.kind } + entry
            val withList = current.copy(institutions = merged)
            when (targetEngine) {
                SearchSourceEngine.MAL -> withList.copy(
                    malSpecificFilters = withList.malSpecificFilters.copy(
                        producerId = if (kind == CommonInstitutionKind.PRODUCER) id
                        else withList.malSpecificFilters.producerId,
                        magazineId = if (kind == CommonInstitutionKind.MAGAZINE) id
                        else withList.malSpecificFilters.magazineId
                    )
                )
                SearchSourceEngine.TMDB -> withList.copy(
                    tmdbSpecificFilters = withList.tmdbSpecificFilters.copy(
                        companyId = if (kind == CommonInstitutionKind.COMPANY) id
                        else withList.tmdbSpecificFilters.companyId,
                        networkId = if (kind == CommonInstitutionKind.NETWORK) id
                        else withList.tmdbSpecificFilters.networkId
                    )
                )
                else -> withList
            }
        }
        search(resetPage = true)
    }

    /** Seçili kurumu kaldırır ve ilgili motor paketindeki kimliği temizler. */
    fun removeInstitution(institution: CommonInstitution) {
        _uiState.update { current ->
            val remaining = current.institutions.filterNot { it.key == institution.key }
            val withList = current.copy(institutions = remaining)
            when (institution.engine) {
                SearchSourceEngine.MAL -> withList.copy(
                    malSpecificFilters = withList.malSpecificFilters.copy(
                        producerId = if (institution.kind == CommonInstitutionKind.PRODUCER) null
                        else withList.malSpecificFilters.producerId,
                        magazineId = if (institution.kind == CommonInstitutionKind.MAGAZINE) null
                        else withList.malSpecificFilters.magazineId
                    )
                )
                SearchSourceEngine.TMDB -> withList.copy(
                    tmdbSpecificFilters = withList.tmdbSpecificFilters.copy(
                        companyId = if (institution.kind == CommonInstitutionKind.COMPANY) null
                        else withList.tmdbSpecificFilters.companyId,
                        networkId = if (institution.kind == CommonInstitutionKind.NETWORK) null
                        else withList.tmdbSpecificFilters.networkId
                    )
                )
                else -> withList
            }
        }
        search(resetPage = true)
    }

    /** Tüm kurum seçimlerini (tüm kaynaklar için) temizler. */
    fun clearInstitutions() {
        _uiState.update { current ->
            current.copy(
                institutions = emptyList(),
                malSpecificFilters = current.malSpecificFilters.copy(
                    producerId = null,
                    magazineId = null
                ),
                tmdbSpecificFilters = current.tmdbSpecificFilters.copy(
                    companyId = null,
                    networkId = null
                )
            )
        }
        search(resetPage = true)
    }

    /**
     * Sıralama seçimi: seçilen anahtar hem motorun paketine hem de kanonik
     * sıralamaya yazılır. Böylece "Tümü" aramasında 7 kaynak aynı anlamda
     * sıralanır ve "Tümünü Gör" sayfası aynı sıralamayla açılır.
     */
    fun applySortKey(engine: SearchSourceEngine, sortKey: String) {
        _uiState.update { current ->
            val withSort = when (engine) {
                SearchSourceEngine.ANILIST, SearchSourceEngine.ALL -> current.copy(
                    aniListSpecificFilters = current.aniListSpecificFilters.copy(sort = sortKey)
                )
                SearchSourceEngine.MAL -> current.copy(
                    malSpecificFilters = current.malSpecificFilters.copy(
                        orderBy = sortKey,
                        // Alfabetik sıralama tek başına "A-Z" demektir; Jikan'ın
                        // varsayılan yönü `desc` olduğu için açıkça `asc` yazılır.
                        sortDirection = if (sortKey == "title") "asc" else "desc"
                    )
                )
                SearchSourceEngine.TMDB -> current.copy(
                    tmdbSpecificFilters = current.tmdbSpecificFilters.copy(sortBy = sortKey)
                )
                SearchSourceEngine.SHIKIMORI -> current.copy(
                    shikimoriSpecificFilters = current.shikimoriSpecificFilters.copy(order = sortKey)
                )
                SearchSourceEngine.KITSU -> current.copy(
                    kitsuSpecificFilters = current.kitsuSpecificFilters.copy(sort = sortKey)
                )
                SearchSourceEngine.SIMKL -> current.copy(
                    simklSpecificFilters = current.simklSpecificFilters.copy(sort = sortKey)
                )
                SearchSourceEngine.BANGUMI -> current.copy(
                    bangumiSpecificFilters = current.bangumiSpecificFilters.copy(sort = sortKey)
                )
            }
            val canonical = SearchCommonFiltersBridge.canonicalSortKey(engine, sortKey)
            val common = SearchCommonFiltersBridge.harvest(withSort).copy(sortKey = canonical)
            // Ortak çatı: aynı sıralamayı tüm kaynaklara yay (çevrilemeyenler kendi
            // varsayılanında kalır).
            SearchCommonFiltersBridge.applyTo(withSort, SearchSourceEngine.ALL, common)
        }
        search(resetPage = true)
    }

    // ── Kurum araması (kurum seçim sheet'i) ────────────────────────────────

    fun searchInstitutions(kind: CommonInstitutionKind, query: String) {
        institutionSearchJob?.cancel()
        val clean = query.trim()
        if (clean.isBlank() || !SearchCommonFiltersBridge.canSearchInstitutionKind(kind)) {
            _institutionSearchResults.value = emptyList()
            return
        }
        institutionSearchJob = viewModelScope.launch {
            val results = runCatching {
                when (kind) {
                    CommonInstitutionKind.PRODUCER ->
                        apiClient.searchMalProducers(clean, page = 1)
                    CommonInstitutionKind.COMPANY -> TmdbApiClient().searchCompany(clean, page = 1)
                    else -> emptyList()
                }
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                emptyList()
            }
            _institutionSearchResults.value = results
        }
    }

    fun clearInstitutionSearch() {
        institutionSearchJob?.cancel()
        _institutionSearchResults.value = emptyList()
    }

    fun setTab(tab: KitsugiSearchTab) {
        val currentQuery = _uiState.value.query.trim()
        val currentMultiResults = _uiState.value.multiResults
        saveCurrentStateToCache()

        val mappedPlatform = when (tab) {
            KitsugiSearchTab.TMDB -> SearchPlatform.TMDB
            KitsugiSearchTab.MAL -> SearchPlatform.MAL
            KitsugiSearchTab.Kitsu -> SearchPlatform.Kitsu
            KitsugiSearchTab.Shikimori -> SearchPlatform.Shikimori
            KitsugiSearchTab.Simkl -> SearchPlatform.Simkl
            KitsugiSearchTab.Bangumi -> SearchPlatform.Bangumi
            KitsugiSearchTab.Anime, KitsugiSearchTab.Manga -> SearchPlatform.AniList
            else -> SearchPlatform.All
        }
        val mappedMediaType = when (tab) {
            KitsugiSearchTab.Manga -> MediaType.Manga
            KitsugiSearchTab.TMDB -> MediaType.Movie
            else -> MediaType.Anime
        }

        val targetEngine = when (tab) {
            KitsugiSearchTab.Anime, KitsugiSearchTab.Manga -> SearchSourceEngine.ANILIST
            KitsugiSearchTab.MAL -> SearchSourceEngine.MAL
            KitsugiSearchTab.TMDB -> SearchSourceEngine.TMDB
            KitsugiSearchTab.Shikimori -> SearchSourceEngine.SHIKIMORI
            KitsugiSearchTab.Kitsu -> SearchSourceEngine.KITSU
            KitsugiSearchTab.Simkl -> SearchSourceEngine.SIMKL
            KitsugiSearchTab.Bangumi -> SearchSourceEngine.BANGUMI
            else -> SearchSourceEngine.ALL
        }
        val targetScope = when (tab) {
            KitsugiSearchTab.Manga -> SearchScope.MANGA
            else -> _uiState.value.selectedScope.takeIf { it in targetEngine.availableScopes() }
                ?: SearchScope.ALL_MIXED
        }
        val cached = loadStateFromCache(tab, mappedPlatform, mappedMediaType)

        // Keep active query if user was actively searching!
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query

        // Seed immediate results from multiResults if switching from All or with active query
        val seedResults = when {
            cached.results.isNotEmpty() -> cached.results
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.TMDB && currentMultiResults.tmdbResults.isNotEmpty() -> currentMultiResults.tmdbResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Anime && currentMultiResults.aniListResults.isNotEmpty() -> currentMultiResults.aniListResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.MAL && currentMultiResults.malResults.isNotEmpty() -> currentMultiResults.malResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Shikimori && currentMultiResults.shikimoriResults.isNotEmpty() -> currentMultiResults.shikimoriResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Kitsu && currentMultiResults.kitsuResults.isNotEmpty() -> currentMultiResults.kitsuResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Simkl && currentMultiResults.simklResults.isNotEmpty() -> currentMultiResults.simklResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Bangumi && currentMultiResults.bangumiResults.isNotEmpty() -> currentMultiResults.bangumiResults
            else -> emptyList()
        }

        val hasSearched = cached.hasSearched || seedResults.isNotEmpty()

        _uiState.update {
            it.copy(
                currentTab = tab,
                selectedEngine = targetEngine,
                selectedScope = targetScope,
                selectedPlatform = mappedPlatform,
                selectedMediaType = mappedMediaType,
                query = effectiveQuery,
                results = seedResults,
                page = 1,
                hasNextPage = true,
                hasSearched = hasSearched,
                errorMessage = cached.errorMessage
            )
        }

        persistSearchSelection()
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }

    fun setMediaType(value: MediaType) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val currentPlatform = _uiState.value.selectedPlatform
        val targetPlatform = if (value == MediaType.Manga && currentPlatform == SearchPlatform.TMDB) {
            SearchPlatform.All
        } else {
            currentPlatform
        }
        val cached = loadStateFromCache(_uiState.value.currentTab, targetPlatform, value)
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query
        _uiState.update { 
            it.copy(
                selectedMediaType = value,
                selectedPlatform = targetPlatform,
                query = effectiveQuery,
                results = if (currentQuery.isNotBlank() && cached.results.isEmpty()) it.results else cached.results,
                hasSearched = cached.hasSearched || (currentQuery.isNotBlank() && it.hasSearched),
                errorMessage = cached.errorMessage
            )
        }
        persistSearchSelection()
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }

    fun setPlatform(value: SearchPlatform) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val engine = when (value) {
            SearchPlatform.All -> SearchSourceEngine.ALL
            SearchPlatform.AniList -> SearchSourceEngine.ANILIST
            SearchPlatform.MAL -> SearchSourceEngine.MAL
            SearchPlatform.TMDB -> SearchSourceEngine.TMDB
            SearchPlatform.Shikimori -> SearchSourceEngine.SHIKIMORI
            SearchPlatform.Kitsu -> SearchSourceEngine.KITSU
            SearchPlatform.Simkl -> SearchSourceEngine.SIMKL
            SearchPlatform.Bangumi -> SearchSourceEngine.BANGUMI
            SearchPlatform.CS3 -> SearchSourceEngine.ALL
        }
        val scope = _uiState.value.selectedScope.takeIf { it in engine.availableScopes() }
            ?: engine.availableScopes().first()
        val tab = when (engine) {
            SearchSourceEngine.ALL -> KitsugiSearchTab.All
            SearchSourceEngine.ANILIST -> if (scope == SearchScope.MANGA) KitsugiSearchTab.Manga else KitsugiSearchTab.Anime
            SearchSourceEngine.MAL -> KitsugiSearchTab.MAL
            SearchSourceEngine.TMDB -> KitsugiSearchTab.TMDB
            SearchSourceEngine.SHIKIMORI -> KitsugiSearchTab.Shikimori
            SearchSourceEngine.KITSU -> KitsugiSearchTab.Kitsu
            SearchSourceEngine.SIMKL -> KitsugiSearchTab.Simkl
            SearchSourceEngine.BANGUMI -> KitsugiSearchTab.Bangumi
        }
        val cached = loadStateFromCache(tab, value, _uiState.value.selectedMediaType)
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query
        _uiState.update {
            it.copy(
                selectedEngine = engine,
                selectedScope = scope,
                currentTab = tab,
                selectedPlatform = value,
                query = effectiveQuery,
                results = if (currentQuery.isNotBlank() && cached.results.isEmpty()) it.results else cached.results,
                hasSearched = cached.hasSearched || (currentQuery.isNotBlank() && it.hasSearched),
                errorMessage = cached.errorMessage
            )
        }
        persistSearchSelection()
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }

    fun setPlatformAndMediaType(platform: SearchPlatform, mediaType: MediaType) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val cached = loadStateFromCache(_uiState.value.currentTab, platform, mediaType)
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query
        _uiState.update {
            it.copy(
                selectedPlatform = platform,
                selectedMediaType = mediaType,
                query = effectiveQuery,
                results = if (currentQuery.isNotBlank() && cached.results.isEmpty()) it.results else cached.results,
                hasSearched = cached.hasSearched || (currentQuery.isNotBlank() && it.hasSearched),
                errorMessage = cached.errorMessage
            )
        }
        persistSearchSelection()
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }

    fun setFilterSheetOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isFilterSheetOpen = isOpen) }
    }

    // â”€â”€ Legacy updateFilters (used by GenresTagsSheet & SearchFilterSheet) â”€â”€
    // ── Legacy updateFilters (used by GenresTagsSheet & SearchFilterSheet) ──
    //
    // ORTAK FİLTRE ÇATISI: Tür/etiket seçimi artık tek bir yol üzerinden yürür.
    // Seçim hem kanonik alanlara (genres / excludedGenres / tags) hem de seçili
    // motorun kendi paketine yazılır. Önceden yalnızca kanonik alanlar
    // güncelleniyordu; bu yüzden AniList sayfasındaki "Türler & Etiketler"
    // satırı seçimden sonra "0 seçili" gösteriyor ve uygulanan filtre kaybolmuş
    // gibi görünüyordu. "Tümü" motorunda yedi kaynağın paketi birden güncellenir.
    //
    // Yıl/puan aralığı buradan DEĞİŞTİRİLMEZ: çağıran sheet yalnızca tür ve
    // etiketleri düzenler; daha önce bu alanlar sessizce sıfırlanıyordu.
    fun updateFilters(filters: SearchFilters) {
        val common = SearchCommonFiltersBridge.harvest(_uiState.value).copy(
            genres = filters.genres,
            excludedGenres = filters.excludedGenres,
            tags = filters.tags
        )
        _uiState.update { current ->
            SearchCommonFiltersBridge.applyTo(current, current.selectedEngine, common)
        }
        search(resetPage = true)
    }

    /** UI-only selection (v2.4.341); do not start an unfiltered provider search. */
    fun updateOrganizations(organizations: List<OrgFilter>) {
        _uiState.update { it.copy(organizations = organizations.distinctBy { org -> org.canonicalKey }) }
        val filters = _uiState.value
        android.util.Log.d("KitsugiSearch", "Arama filtreleri: tÃ¼r=${filters.genres.size} " +
            "etiket=${filters.tags.size} kurum=${filters.organizations.size} | " +
            "kurum saÄŸlayÄ±cÄ± eÅŸlemesi henÃ¼z etkin deÄŸil")
    }

    fun resetFilters() {
        _uiState.update { current ->
            val cleared = current.copy(
                genres = emptyList(),
                excludedGenres = emptyList(),
                tags = emptyList(),
                institutions = emptyList(),
                organizations = emptyList(),
                selectedFormats = emptyList(),
                selectedStatuses = emptyList(),
                country = null,
                selectedSources = emptyList(),
                startYear = null,
                endYear = null,
                season = null,
                minScore = null,
                maxScore = null,
                minEpCh = null,
                maxEpCh = null,
                minDuration = null,
                maxDuration = null,
                onMyList = null,
                isDoujin = null,
                isAdultFilter = null,
                detailSearchFilterRequest = null,
                sortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
                isSortDescending = true,
                page = 1,
                hasNextPage = true
            )
            when (current.selectedEngine) {
                SearchSourceEngine.ANILIST -> cleared.copy(aniListSpecificFilters = AniListSpecificFilters())
                SearchSourceEngine.MAL -> cleared.copy(malSpecificFilters = MalSpecificFilters())
                SearchSourceEngine.TMDB -> cleared.copy(tmdbSpecificFilters = TmdbSpecificFilters())
                SearchSourceEngine.SHIKIMORI -> cleared.copy(shikimoriSpecificFilters = ShikimoriSpecificFilters())
                SearchSourceEngine.KITSU -> cleared.copy(kitsuSpecificFilters = KitsuSpecificFilters())
                SearchSourceEngine.SIMKL -> cleared.copy(simklSpecificFilters = SimklSpecificFilters())
                SearchSourceEngine.BANGUMI -> cleared.copy(bangumiSpecificFilters = BangumiSpecificFilters())
                SearchSourceEngine.ALL -> cleared.copy(
                    aniListSpecificFilters = AniListSpecificFilters(),
                    malSpecificFilters = MalSpecificFilters(),
                    tmdbSpecificFilters = TmdbSpecificFilters(),
                    shikimoriSpecificFilters = ShikimoriSpecificFilters(),
                    kitsuSpecificFilters = KitsuSpecificFilters(),
                    simklSpecificFilters = SimklSpecificFilters(),
                    bangumiSpecificFilters = BangumiSpecificFilters()
                )
            }
        }
        search(resetPage = true)
    }

    // â”€â”€ AniHyou-style reactive filter events â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun setFormats(values: List<KitsugiMediaFormat>) {
        _uiState.update { it.copy(selectedFormats = values) }
        search(resetPage = true)
    }

    fun setStatuses(values: List<KitsugiMediaStatus>) {
        _uiState.update { it.copy(selectedStatuses = values) }
        search(resetPage = true)
    }

    fun setCountry(value: KitsugiCountryOfOrigin?) {
        _uiState.update { it.copy(country = value) }
        search(resetPage = true)
    }

    fun setSources(values: List<KitsugiMediaSource>) {
        _uiState.update { it.copy(selectedSources = values) }
        search(resetPage = true)
    }

    fun setStartYear(value: Int?) {
        _uiState.update { it.copy(startYear = value) }
        search(resetPage = true)
    }

    fun setEndYear(value: Int?) {
        _uiState.update { it.copy(endYear = value) }
        search(resetPage = true)
    }

    fun setSeason(value: KitsugiMediaSeason?) {
        _uiState.update { it.copy(season = value) }
        search(resetPage = true)
    }

    fun setMinScore(value: Int?) {
        _uiState.update { it.copy(minScore = value) }
        search(resetPage = true)
    }

    fun setMaxScore(value: Int?) {
        _uiState.update { it.copy(maxScore = value) }
        search(resetPage = true)
    }

    fun setEpCh(range: IntRange?) {
        _uiState.update { it.copy(minEpCh = range?.first, maxEpCh = range?.last) }
        search(resetPage = true)
    }

    fun setDuration(range: IntRange?) {
        _uiState.update { it.copy(minDuration = range?.first, maxDuration = range?.last) }
        search(resetPage = true)
    }

    fun setSort(sortSearch: KitsugiMediaSortSearch, isDescending: Boolean) {
        _uiState.update { it.copy(sortSearch = sortSearch, isSortDescending = isDescending) }
        search(resetPage = true)
    }

    fun setOnMyList(value: Boolean?) {
        _uiState.update { it.copy(onMyList = value) }
        search(resetPage = true)
    }

    fun setIsDoujin(value: Boolean?) {
        _uiState.update { it.copy(isDoujin = value) }
        search(resetPage = true)
    }

    fun setIsAdultFilter(value: Boolean?) {
        _uiState.update { it.copy(isAdultFilter = value) }
        search(resetPage = true)
    }

    fun setShowMoreFilters(show: Boolean) {
        _uiState.update { it.copy(showMoreFilters = show) }
    }

    fun setEngine(engine: SearchSourceEngine) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val scopes = engine.availableScopes()
        val targetScope = if (_uiState.value.selectedScope in scopes) _uiState.value.selectedScope else scopes.first()
        
        val mappedTab = when (engine) {
            SearchSourceEngine.ALL -> KitsugiSearchTab.All
            SearchSourceEngine.ANILIST -> if (targetScope == SearchScope.MANGA) KitsugiSearchTab.Manga else KitsugiSearchTab.Anime
            SearchSourceEngine.MAL -> KitsugiSearchTab.MAL
            SearchSourceEngine.TMDB -> KitsugiSearchTab.TMDB
            SearchSourceEngine.SHIKIMORI -> KitsugiSearchTab.Shikimori
            SearchSourceEngine.KITSU -> KitsugiSearchTab.Kitsu
            SearchSourceEngine.SIMKL -> KitsugiSearchTab.Simkl
            SearchSourceEngine.BANGUMI -> KitsugiSearchTab.Bangumi
        }
        val mappedPlatform = when (engine) {
            SearchSourceEngine.ALL -> SearchPlatform.All
            SearchSourceEngine.ANILIST -> SearchPlatform.AniList
            SearchSourceEngine.MAL -> SearchPlatform.MAL
            SearchSourceEngine.TMDB -> SearchPlatform.TMDB
            SearchSourceEngine.SHIKIMORI -> SearchPlatform.Shikimori
            SearchSourceEngine.KITSU -> SearchPlatform.Kitsu
            SearchSourceEngine.SIMKL -> SearchPlatform.Simkl
            SearchSourceEngine.BANGUMI -> SearchPlatform.Bangumi
        }

        // Ortak filtre çatısı: motor değişse de tür/etiket/yıl/puan/sıralama
        // seçimleri yeni motorun parametrelerine çevrilerek korunur.
        val carried = SearchCommonFiltersBridge.harvest(_uiState.value)
        _uiState.update {
            val switched = it.copy(
                selectedEngine = engine,
                selectedScope = targetScope,
                currentTab = mappedTab,
                selectedPlatform = mappedPlatform,
                page = 1,
                hasNextPage = true
            )
            SearchCommonFiltersBridge.applyTo(switched, engine, carried, targetScope)
        }
        persistSearchSelection()
        search(resetPage = true)
    }

    fun setScope(scope: SearchScope) {
        saveCurrentStateToCache()
        val engine = _uiState.value.selectedEngine
        val mappedMediaType = when (scope) {
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> MediaType.Manga
            SearchScope.MOVIE -> MediaType.Movie
            SearchScope.TV -> MediaType.TvShow
            else -> MediaType.Anime
        }
        val mappedTab = when (scope) {
            SearchScope.CHARACTER -> KitsugiSearchTab.Character
            SearchScope.STAFF -> KitsugiSearchTab.Staff
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> KitsugiSearchTab.Manga
            else -> if (engine == SearchSourceEngine.ALL) KitsugiSearchTab.All else _uiState.value.currentTab
        }

        _uiState.update {
            it.copy(
                selectedScope = scope,
                selectedMediaType = mappedMediaType,
                currentTab = mappedTab,
                page = 1,
                hasNextPage = true
            )
        }
        persistSearchSelection()
        search(resetPage = true)
    }

    fun updateAniListFilters(filters: AniListSpecificFilters) {
        _uiState.update { it.copy(aniListSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateMalFilters(filters: MalSpecificFilters) {
        _uiState.update { it.copy(malSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateTmdbFilters(filters: TmdbSpecificFilters) {
        _uiState.update { it.copy(tmdbSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateShikimoriFilters(filters: ShikimoriSpecificFilters) {
        _uiState.update { it.copy(shikimoriSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateKitsuFilters(filters: KitsuSpecificFilters) {
        _uiState.update { it.copy(kitsuSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateSimklFilters(filters: SimklSpecificFilters) {
        _uiState.update { it.copy(simklSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateBangumiFilters(filters: BangumiSpecificFilters) {
        _uiState.update { it.copy(bangumiSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun resetEngineFilters(engine: SearchSourceEngine) {
        _uiState.update {
            when (engine) {
                SearchSourceEngine.ANILIST -> it.copy(aniListSpecificFilters = AniListSpecificFilters())
                SearchSourceEngine.MAL -> it.copy(malSpecificFilters = MalSpecificFilters())
                SearchSourceEngine.TMDB -> it.copy(tmdbSpecificFilters = TmdbSpecificFilters())
                SearchSourceEngine.SHIKIMORI -> it.copy(shikimoriSpecificFilters = ShikimoriSpecificFilters())
                SearchSourceEngine.KITSU -> it.copy(kitsuSpecificFilters = KitsuSpecificFilters())
                SearchSourceEngine.SIMKL -> it.copy(simklSpecificFilters = SimklSpecificFilters())
                SearchSourceEngine.BANGUMI -> it.copy(bangumiSpecificFilters = BangumiSpecificFilters())
                SearchSourceEngine.ALL -> it.copy(
                    aniListSpecificFilters = AniListSpecificFilters(),
                    malSpecificFilters = MalSpecificFilters(),
                    tmdbSpecificFilters = TmdbSpecificFilters(),
                    shikimoriSpecificFilters = ShikimoriSpecificFilters(),
                    kitsuSpecificFilters = KitsuSpecificFilters(),
                    simklSpecificFilters = SimklSpecificFilters(),
                    bangumiSpecificFilters = BangumiSpecificFilters()
                )
            }
        }
        resetFilters()
    }

    fun getActiveFilterCountForEngine(engine: SearchSourceEngine): Int {
        val state = _uiState.value
        return when (engine) {
            SearchSourceEngine.ANILIST -> state.commonFilterCount + state.aniListSpecificFilters.activeCount
            SearchSourceEngine.MAL -> state.commonFilterCount + state.malSpecificFilters.activeCount
            SearchSourceEngine.TMDB -> state.commonFilterCount + state.tmdbSpecificFilters.activeCount
            SearchSourceEngine.SHIKIMORI -> state.commonFilterCount + state.shikimoriSpecificFilters.activeCount
            SearchSourceEngine.KITSU -> state.commonFilterCount + state.kitsuSpecificFilters.activeCount
            SearchSourceEngine.SIMKL -> state.commonFilterCount + state.simklSpecificFilters.activeCount
            SearchSourceEngine.BANGUMI -> state.commonFilterCount + state.bangumiSpecificFilters.activeCount
            SearchSourceEngine.ALL -> state.activeFilterCount
        }
    }

    private fun planAllSourcesForState(state: SearchUiState, scope: SearchScope): AllSourcePlan {
        val base = planAllSources(scope)
        val request = state.detailSearchFilterRequest ?: return base
        return base.copy(
            aniList = base.aniList && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.ANILIST, request),
            mal = base.mal && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.MAL, request),
            tmdb = base.tmdb && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.TMDB, request),
            shikimori = base.shikimori && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.SHIKIMORI, request),
            kitsu = base.kitsu && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.KITSU, request),
            simkl = base.simkl && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.SIMKL, request),
            bangumi = base.bangumi && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.BANGUMI, request)
        )
    }

    /**
     * Profil / detay sayfasÄ±ndan bir tÃ¼r (genre) adÄ±na tÄ±klandÄ±ÄŸÄ±nda Ã§aÄŸrÄ±lÄ±r.
     * [genreEnglish] her zaman Ä°ngilizce API adÄ± olmalÄ± (Kitsugi translation map zaten dÃ¶nÃ¼ÅŸtÃ¼rÃ¼r).
     */
    fun setGenreFilter(genreEnglish: String) {
        val cleanGenre = translateToEnglishForSearch(genreEnglish)
        val isAniListGenre = isOfficialAniListGenre(cleanGenre)
        _uiState.update {
            it.copy(
                query = "",
                genres = if (isAniListGenre) listOf(cleanGenre) else emptyList(),
                excludedGenres = emptyList(),
                tags = if (!isAniListGenre) listOf(cleanGenre) else emptyList(),
                selectedFormats = emptyList(),
                selectedStatuses = emptyList(),
                country = null,
                selectedSources = emptyList(),
                startYear = null,
                endYear = null,
                season = null,
                minScore = null,
                maxScore = null,
                minEpCh = null,
                maxEpCh = null,
                minDuration = null,
                maxDuration = null,
                detailSearchFilterRequest = null,
                sortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
                isSortDescending = true,
                hasSearched = false
            )
        }
        search()
    }

    /**
     * Profil / detay sayfasÄ±ndan bir etikete (tag) tÄ±klandÄ±ÄŸÄ±nda Ã§aÄŸrÄ±lÄ±r.
     */
    fun setTagFilter(tag: String) {
        val cleanTag = translateToEnglishForSearch(tag)
        _uiState.update {
            it.copy(
                query = "",
                genres = emptyList(),
                excludedGenres = emptyList(),
                tags = listOf(cleanTag),
                selectedFormats = emptyList(),
                selectedStatuses = emptyList(),
                country = null,
                selectedSources = emptyList(),
                startYear = null,
                endYear = null,
                season = null,
                minScore = null,
                maxScore = null,
                minEpCh = null,
                maxEpCh = null,
                minDuration = null,
                maxDuration = null,
                detailSearchFilterRequest = null,
                sortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
                isSortDescending = true,
                hasSearched = false
            )
        }
        search()
    }

    /**
     * AyrÄ±ntÄ± sayfalarÄ±ndaki (Detail Pages) tÄ±klanabilir tÃ¼m metadata Ã¶ÄŸelerini
     * (TÃ¼r, Tema, Demografi, Etiket/Keyword, Sezon+YÄ±l, StÃ¼dyo, Dergi, Kanal, Format vb.)
     * tek bir kanonik model Ã¼zerinden filtre olarak ayarlar ve aramayÄ± anÄ±nda tetikler.
     */
    fun applyDetailFilterRequest(request: DetailSearchFilterRequest) {
        // Detail facets are intentionally always searched in ALL mode. `source` remains
        // available for provenance, while `tagSource` carries the tag's native taxonomy.
        val cleanGenre = request.genre?.let(::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
        val cleanTheme = request.theme?.let(::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
        val cleanDemographic = request.demographic?.let(::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
        val cleanTag = request.tag?.let(::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
        val season = CanonicalFilterBridge.normalizeSeason(request.season)
        val year = request.year?.takeIf { it > 0 }
        // StÃ¼dyo/yapÄ±mcÄ±/dergi/kanal kimlik facetleri (detay sayfasÄ± Ã§ip tÄ±klamalarÄ±).
        val producerFacetId = request.producerId?.takeIf { it > 0 } ?: request.studioId?.takeIf { it > 0 }
        val magazineFacetId = request.magazineId?.takeIf { it > 0 }
        val magazineFacetName = request.magazineName?.trim()?.takeIf { it.isNotBlank() }
        val networkFacetId = request.networkId?.takeIf { it > 0 }
        val normalizedRequest = request.copy(
            source = SearchSourceEngine.ALL.id,
            genre = cleanGenre,
            theme = cleanTheme,
            demographic = cleanDemographic,
            tag = cleanTag,
            season = season,
            year = year
        )
        val targetScope = CanonicalFilterBridge.autoScopeGuard(SearchSourceEngine.ALL, request.mediaType)
        val facetTerms = listOfNotNull(cleanGenre, cleanTheme, cleanDemographic)
        val anyFacetTerm = (facetTerms + listOfNotNull(cleanTag)).firstOrNull()
        val isManga = request.mediaType == MediaType.Manga
        val isTv = request.mediaType == MediaType.TvShow
        val tmdbTagId = request.keywordId?.takeIf {
            it > 0 && CanonicalFilterBridge.isTmdbTagSource(request.tagSource)
        }
        val supports = { engine: SearchSourceEngine ->
            CanonicalFilterBridge.supportsDetailFilter(engine, normalizedRequest)
        }
        val aniListOfficialGenre = facetTerms.firstOrNull()?.takeIf {
            cleanTag == null && CanonicalFilterBridge.isOfficialAniListGenre(it)
        }
        val aniListTagTerms = buildList {
            if (cleanTag != null) add(cleanTag)
            facetTerms.filterNot { it == aniListOfficialGenre }.forEach { add(it) }
        }.distinct()
        val bangumiTag = request.tag?.takeIf {
            request.tagSource.equals("bangumi", ignoreCase = true) || request.tagSource.equals("bgm", ignoreCase = true)
        }?.trim()?.takeIf { it.isNotBlank() } ?: cleanTag
        val bangumiTags = listOfNotNull(cleanGenre, cleanTheme, cleanDemographic, bangumiTag).distinct()
        val kitsuCategories = listOfNotNull(cleanGenre, cleanTheme, cleanDemographic, cleanTag).distinct()

        val aniListFilters = if (supports(SearchSourceEngine.ANILIST)) {
            AniListSpecificFilters(
                season = season,
                seasonYear = year,
                startYear = if (season == null) year else null,
                endYear = if (season == null) year else null,
                genres = listOfNotNull(aniListOfficialGenre),
                tags = aniListTagTerms
            )
        } else AniListSpecificFilters()

        val malGenreId = anyFacetTerm?.let {
            CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull()
        }
        val malFilters = if (supports(SearchSourceEngine.MAL)) {
            MalSpecificFilters(
                season = season?.lowercase(java.util.Locale.ROOT),
                seasonYear = year,
                genres = listOfNotNull(malGenreId),
                startYear = if (season == null) year else null,
                endYear = if (season == null) year else null,
                producerId = producerFacetId,
                magazineId = magazineFacetId
            )
        } else MalSpecificFilters()

        val tmdbGenreIds = if (supports(SearchSourceEngine.TMDB) && cleanTag == null) {
            facetTerms.mapNotNull { CanonicalFilterBridge.mapTmdbGenreId(it, isTv) }.distinct()
        } else emptyList()
        val tmdbFilters = if (supports(SearchSourceEngine.TMDB)) {
            TmdbSpecificFilters(
                isMovie = request.mediaType != MediaType.TvShow,
                genres = tmdbGenreIds,
                startYear = if (season == null) year else null,
                endYear = if (season == null) year else null,
                keyword = tmdbTagId?.toString(),
                networkId = networkFacetId,
                // Yapım şirketi facet'i yalnızca TMDB kaynaklı detay sayfalarında
                // gerçek bir TMDB şirket kimliğidir; başka kaynakların stüdyo
                // kimliğini buraya yazmak yanlış sonuç verirdi.
                companyId = if (request.source.equals("tmdb", ignoreCase = true)) {
                    request.studioId?.takeIf { it > 0 }
                } else null
            )
        } else TmdbSpecificFilters()

        val shikimoriGenreId = anyFacetTerm?.let {
            CanonicalFilterBridge.mapAnimeGenreId(it)
                ?: CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull()
        }
        val shikimoriSeason = when {
            season != null && year != null -> "${season.lowercase(java.util.Locale.ROOT)}_$year"
            year != null -> year.toString()
            else -> null
        }
        val shikimoriFilters = if (supports(SearchSourceEngine.SHIKIMORI)) {
            ShikimoriSpecificFilters(
                season = shikimoriSeason,
                genres = listOfNotNull(shikimoriGenreId)
            )
        } else ShikimoriSpecificFilters()

        val kitsuFilters = if (supports(SearchSourceEngine.KITSU)) {
            KitsuSpecificFilters(
                season = season?.lowercase(java.util.Locale.ROOT),
                seasonYear = year,
                categories = kitsuCategories
            )
        } else KitsuSpecificFilters()

        val simklGenre = anyFacetTerm?.let(CanonicalFilterBridge::mapSimklGenre)
        val simklFilters = if (supports(SearchSourceEngine.SIMKL)) {
            SimklSpecificFilters(
                genre = simklGenre,
                year = year?.toString()
            )
        } else SimklSpecificFilters()

        val bangumiFilters = if (supports(SearchSourceEngine.BANGUMI)) {
            BangumiSpecificFilters(
                tags = bangumiTags,
                yearFrom = if (season == null) year else null,
                yearTo = if (season == null) year else null
            )
        } else BangumiSpecificFilters()

        val seasonEnum = season?.let { value ->
            KitsugiMediaSeason.entries.firstOrNull { it.apiValue == value }
        }
        // Kimliksiz dergi kayÄ±tlarÄ±nda (id<=0) filtreyi ada gÃ¶re metin aramasÄ±na indirge:
        // kimlik facetini hiÃ§bir motor uygulayamaz, talep kayÄ±tlÄ± kalÄ±rsa tÃ¼m motorlar
        // kapanÄ±p boÅŸ sonuÃ§ ekranÄ± aÃ§Ä±lÄ±r. AmaÃ§: asla verisiz/Ã¶lÃ¼ ekran kalmamasÄ±n.
        val degradeMagazineToQuery = magazineFacetId == null && magazineFacetName != null &&
            facetTerms.isEmpty() && cleanTag == null && season == null && year == null &&
            producerFacetId == null && networkFacetId == null
        val fallbackQuery = if (degradeMagazineToQuery) magazineFacetName else ""
        // Detay sayfasından gelen kurum facetleri kanonik kurum listesine de yazılır:
        // böylece kurum sheet'inde çip olarak görünür, tek tek kaldırılabilir ve bir
        // önceki aramadan kalan kurum seçimleri bu aramaya sızmaz (liste her seferinde
        // yeniden kurulur). Yalnızca facet gerçekten o kaynağa aitse eklenir.
        val institutionSource = request.source.lowercase(java.util.Locale.ROOT)
        val inheritedInstitutions = buildList {
            val fallbackName: (Int) -> String = { id ->
                getApplication<Application>().getString(R.string.search_institution_unknown_name, id)
            }
            if (institutionSource == "mal" || institutionSource == "jikan") {
                producerFacetId?.let { id ->
                    add(
                        CommonInstitution(
                            engine = SearchSourceEngine.MAL,
                            id = id,
                            name = request.studioName?.trim()?.takeIf { it.isNotBlank() } ?: fallbackName(id),
                            kind = CommonInstitutionKind.PRODUCER
                        )
                    )
                }
                magazineFacetId?.let { id ->
                    add(
                        CommonInstitution(
                            engine = SearchSourceEngine.MAL,
                            id = id,
                            name = magazineFacetName ?: fallbackName(id),
                            kind = CommonInstitutionKind.MAGAZINE
                        )
                    )
                }
            }
            if (institutionSource == "tmdb") {
                networkFacetId?.let { id ->
                    add(
                        CommonInstitution(
                            engine = SearchSourceEngine.TMDB,
                            id = id,
                            name = request.networkName?.trim()?.takeIf { it.isNotBlank() } ?: fallbackName(id),
                            kind = CommonInstitutionKind.NETWORK
                        )
                    )
                }
                request.studioId?.takeIf { it > 0 }?.let { id ->
                    add(
                        CommonInstitution(
                            engine = SearchSourceEngine.TMDB,
                            id = id,
                            name = request.studioName?.trim()?.takeIf { it.isNotBlank() } ?: fallbackName(id),
                            kind = CommonInstitutionKind.COMPANY
                        )
                    )
                }
            }
        }.distinctBy { it.key }
        val storedFilterRequest = if (degradeMagazineToQuery) null else normalizedRequest
        _uiState.update { current ->
            current.copy(
                query = fallbackQuery,
                selectedEngine = SearchSourceEngine.ALL,
                selectedScope = targetScope,
                currentTab = KitsugiSearchTab.All,
                selectedPlatform = SearchPlatform.All,
                selectedMediaType = request.mediaType,
                detailSearchFilterRequest = storedFilterRequest,
                genres = listOfNotNull(cleanGenre ?: cleanTheme ?: cleanDemographic),
                tags = listOfNotNull(cleanTag),
                institutions = inheritedInstitutions,
                excludedGenres = emptyList(),
                selectedFormats = emptyList(),
                selectedStatuses = emptyList(),
                country = null,
                selectedSources = emptyList(),
                startYear = year,
                endYear = year,
                season = seasonEnum,
                minScore = null,
                maxScore = null,
                minEpCh = null,
                maxEpCh = null,
                minDuration = null,
                maxDuration = null,
                onMyList = null,
                isDoujin = null,
                isAdultFilter = null,
                sortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
                isSortDescending = true,
                aniListSpecificFilters = aniListFilters,
                malSpecificFilters = malFilters,
                tmdbSpecificFilters = tmdbFilters,
                shikimoriSpecificFilters = shikimoriFilters,
                kitsuSpecificFilters = kitsuFilters,
                simklSpecificFilters = simklFilters,
                bangumiSpecificFilters = bangumiFilters,
                page = 1,
                hasNextPage = true,
                hasSearched = false
            )
        }
        search(resetPage = true)
    }

    private fun isOfficialAniListGenre(genre: String): Boolean {
        val officialGenres = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
            "Hentai", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
            "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
            "Supernatural", "Thriller"
        )
        return officialGenres.any { it.equals(genre.trim(), ignoreCase = true) }
    }


    private fun translateToEnglishForSearch(label: String): String =
        SearchTranslation.translateToEnglishForSearch(label)


    private fun getJikanGenreId(genreOrTag: String?): Int? {
        // Ã–nce TÃ¼rkÃ§e ise Ä°ngilizce'ye Ã§evir, sonra ID'yi bul
        val eng = if (genreOrTag != null) SearchTranslation.translateToEnglishForSearch(genreOrTag) else return null
        return when (eng.lowercase().trim()) {
            "action"           -> 1
            "adventure"        -> 2
            "racing"           -> 3
            "comedy"           -> 4
            "avant garde"      -> 5
            "mythology"        -> 6
            "mystery"          -> 7
            "drama"            -> 8
            "ecchi"            -> 9
            "fantasy"          -> 10
            "magic"            -> 10
            "strategy game"    -> 11
            "hentai"           -> 12
            "historical"       -> 13
            "horror"           -> 14
            "kids"             -> 15
            "martial arts"     -> 17
            "mecha"            -> 18
            "music"            -> 19
            "parody"           -> 20
            "samurai"          -> 21
            "romance"          -> 22
            "school"           -> 23
            "sci-fi"           -> 24
            "cyberpunk"        -> 24
            "shoujo ai"        -> 25
            "shounen ai"       -> 26
            "space"            -> 27
            "space opera"      -> 27
            "sports"           -> 30
            "super power"      -> 31
            "superhero"        -> 31
            "vampire"          -> 32
            "harem"            -> 33
            "slice of life"    -> 36
            "iyashikei"        -> 36
            "supernatural"     -> 37
            "youkai"           -> 37
            "military"         -> 38
            "detective"        -> 39
            "psychological"    -> 40
            "suspense","thriller" -> 41
            "seinen"           -> 42
            "josei"            -> 43
            "gourmet"          -> 47
            "workplace", "work" -> 48
            "adult cast"       -> 50
            "cgdct"            -> 52
            "cute girls doing cute things" -> 52
            "childcare"        -> 53
            "combat sports"    -> 54
            "delinquents"      -> 56
            "educational"      -> 57
            "gag humor"        -> 58
            "surreal comedy"   -> 58
            "gore"             -> 59
            "body horror"      -> 59
            "high stakes game", "death game" -> 60
            "idols"            -> 61
            "isekai"           -> 62
            "love polygon", "love triangle" -> 64
            "medicine", "medical" -> 66
            "organized crime", "mafia", "yakuza", "criminal organization" -> 67
            "otaku culture"    -> 68
            "performing arts", "showbiz" -> 69
            "pets", "animals"  -> 70
            "reincarnation"    -> 71
            "reverse harem"    -> 72
            "survival"         -> 75
            "post-apocalyptic" -> 75
            "time travel", "time loop", "time manipulation" -> 77
            "video games", "video game", "e-sports" -> 79
            "visual arts", "photography", "drawing" -> 80
            "boys' love", "boys love" -> 26
            "yuri"             -> 25
            "shounen"          -> 27
            "shoujo"           -> 25
            "seinen"           -> 42
            "josei"            -> 43
            else               -> null
        }
    }

    private fun getTmdbGenreId(genreOrTag: String?, isMovie: Boolean): Int? {
        val eng = if (genreOrTag != null) SearchTranslation.translateToEnglishForSearch(genreOrTag) else return null
        return when (eng.lowercase().trim()) {
            "action", "battle royale", "martial arts", "superhero" -> if (isMovie) 28 else 10759
            "adventure", "isekai", "survival", "post-apocalyptic" -> if (isMovie) 12 else 10759
            "comedy", "parody", "gag humor", "surreal comedy", "slapstick" -> 35
            "drama", "tragedy", "coming of age", "romance" -> 18
            "fantasy", "magic", "supernatural", "alchemy", "youkai", "mythology" -> if (isMovie) 14 else 10765
            "horror", "gore", "body horror", "cosmic horror" -> 27
            "mystery", "detective", "conspiracy", "noir" -> 9648
            "sci-fi", "cyberpunk", "space opera", "time travel", "time loop" -> if (isMovie) 878 else 10765
            "thriller", "suspense", "psychological", "espionage", "terrorism" -> 53
            "music", "band", "dancing", "musical theater" -> 10402
            "historical", "medieval", "ancient china", "samurai", "vikings" -> 36
            "crime", "mafia", "yakuza", "organized crime", "gangs" -> 80
            "family", "childcare", "parenthood" -> 10751
            "military", "war", "guns" -> if (isMovie) 10752 else 10768
            "animation", "anime" -> 16
            "documentary", "educational", "biographical" -> 99
            "western" -> if (isMovie) 37 else null
            "kids", "cgdct" -> 10762
            else -> null
        }
    }

    // AniList'e gÃ¶nderilecek tÃ¼r adÄ±nÄ± normalize eder (TÃ¼rkÃ§e â†’ Ä°ngilizce)
    private fun getAniListGenreName(genre: String?): String? {
        if (genre == null) return null
        return SearchTranslation.translateToEnglishForSearch(genre)
    }

    private fun getAniListGenreNames(genres: List<String>): List<String> =
        genres.mapNotNull { getAniListGenreName(it) }

    private fun cleanSearchQuery(query: String): String {
        return query
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("([a-zA-Z])([0-9])"), "$1 $2")
            .replace(Regex("([0-9])([a-zA-Z])"), "$1 $2")
            .replace("-", " ")
            .replace("_", " ")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun generateFallbackQueries(query: String): List<String> {
        val list = mutableListOf<String>()
        val raw = query.trim()
        if (raw.isBlank()) return list

        val cleaned = cleanSearchQuery(raw)
        if (cleaned.isNotBlank() && cleaned != raw) {
            list.add(cleaned)
        }

        // Vowel typo correction (e.g. shorlock -> sherlock)
        val lower = raw.lowercase()
        if (lower.contains("shorl")) list.add(raw.replace(Regex("shorl", RegexOption.IGNORE_CASE), "sherl"))
        if (lower.contains("attak")) list.add(raw.replace(Regex("attak", RegexOption.IGNORE_CASE), "attack"))
        if (lower.contains("demn")) list.add(raw.replace(Regex("demn", RegexOption.IGNORE_CASE), "demon"))

        return list.distinct()
    }

    /**
     * Bu aramada kullanÄ±cÄ±ya gÃ¶sterilecek kaynak hata rozetlerini sÃ¼zer.
     *
     * KÃ–K NEDEN:
     * [PlatformFailureRegistry] sÃ¼reÃ§ genelindedir ve aÄŸ katmanÄ± (PlatformFailureInterceptor)
     * her 4xx/5xx yanÄ±tÄ±nÄ± kaydeder. Bir kaynak rafÄ±nÄ± baÅŸarÄ±yla doldursa bile o kaynaÄŸÄ±n
     * iÃ§ Ã§aÄŸrÄ±larÄ±ndan biri (yedek host denemesi, sayfa sondasÄ±, karakter aramasÄ±, arka
     * planda Ã§alÄ±ÅŸan bildirim/takvim isteÄŸi) 404 dÃ¶ndÃ¼ÄŸÃ¼nde, arama ekranÄ± bu kaydÄ±
     * "KayÄ±t bulunamadÄ± (HTTP 404)" rozeti olarak sonuÃ§larÄ±n Ã¼stÃ¼ne basÄ±yordu. KullanÄ±cÄ±
     * ise Kitsu/AniList raflarÄ±nda sonuÃ§larÄ± gÃ¶rÃ¼yordu â†’ Ã§eliÅŸkili, gÃ¼ven kÄ±rÄ±cÄ± rozet.
     *
     * KURAL:
     *  - "TÃ¼mÃ¼" (raf) aramasÄ±nda rozet yalnÄ±zca **planlanmÄ±ÅŸ olduÄŸu hÃ¢lde boÅŸ kalan**
     *    kaynaklar iÃ§in gÃ¶sterilir; sonuÃ§ Ã¼reten kaynak baÅŸarÄ±lÄ± sayÄ±lÄ±r.
     *  - Tek kaynak sekmesinde rozet yalnÄ±zca o kaynaÄŸÄ±n sonucu hiÃ§ yoksa gÃ¶sterilir.
     */
    private fun visibleSourceErrorCodes(
        state: SearchUiState,
        sinceMillis: Long,
    ): Map<ListPlatform, PlatformErrorCode> {
        val failures = PlatformFailureRegistry.failuresSince(sinceMillis)
        if (failures.isEmpty()) return emptyMap()

        val shelfSearch = (state.currentTab == KitsugiSearchTab.All ||
            state.selectedEngine == SearchSourceEngine.ALL) &&
            state.selectedScope != SearchScope.CHARACTER &&
            state.selectedScope != SearchScope.STAFF

        if (shelfSearch) {
            val multi = state.multiResults
            val plan = planAllSourcesForState(state, state.selectedScope)
            // PlanlanmÄ±ÅŸ + sonuÃ§ Ã¼retmiÅŸ kaynaklar "baÅŸarÄ±lÄ±" sayÄ±lÄ±r.
            val healthy = buildSet {
                if (plan.aniList && multi.aniListResults.isNotEmpty()) add(ListPlatform.ANILIST)
                if (plan.mal && multi.malResults.isNotEmpty()) add(ListPlatform.MAL)
                if (plan.tmdb && multi.tmdbResults.isNotEmpty()) add(ListPlatform.TMDB)
                if (plan.shikimori && multi.shikimoriResults.isNotEmpty()) add(ListPlatform.SHIKIMORI)
                if (plan.kitsu && multi.kitsuResults.isNotEmpty()) add(ListPlatform.KITSU)
                if (plan.simkl && multi.simklResults.isNotEmpty()) add(ListPlatform.SIMKL)
                if (plan.bangumi && multi.bangumiResults.isNotEmpty()) add(ListPlatform.BANGUMI)
            }
            return failures.filterKeys { it !in healthy }
        }

        // Tek kaynak sekmesi (AniList, MAL, Kitsuâ€¦): yalnÄ±zca sonuÃ§ yoksa rozet gÃ¶ster.
        if (state.selectedEngine != SearchSourceEngine.ALL) {
            if (state.results.isNotEmpty()) return emptyMap()
            val platform = state.selectedEngine.toListPlatform() ?: return emptyMap()
            return failures[platform]?.let { mapOf(platform to it) } ?: emptyMap()
        }

        // Karakter/Personel kapsamlarÄ±: sonuÃ§ varsa rozet gÃ¶sterilmez.
        return if (state.results.isEmpty()) failures else emptyMap()
    }

    /** Arama motoru â†’ hata kaydÄ±ndaki liste kaynaÄŸÄ± eÅŸlemesi. */
    private fun SearchSourceEngine.toListPlatform(): ListPlatform? = when (this) {
        SearchSourceEngine.ANILIST -> ListPlatform.ANILIST
        SearchSourceEngine.MAL -> ListPlatform.MAL
        SearchSourceEngine.TMDB -> ListPlatform.TMDB
        SearchSourceEngine.SHIKIMORI -> ListPlatform.SHIKIMORI
        SearchSourceEngine.KITSU -> ListPlatform.KITSU
        SearchSourceEngine.SIMKL -> ListPlatform.SIMKL
        SearchSourceEngine.BANGUMI -> ListPlatform.BANGUMI
        SearchSourceEngine.ALL -> null
    }

    /**
     * Aramanın tamamı başarısız olduğunda kodu seçer. Tek kaynaklı aramada o kaynağın
     * kaydı; "Tümü" aramasında bu süre içindeki en son kaynak hatası kullanılır.
     */
    private fun searchFailureCode(
        engine: SearchSourceEngine,
        error: Throwable,
        startedAt: Long,
    ): com.kitsugi.animelist.data.platform.PlatformErrorCode =
        if (engine == SearchSourceEngine.ALL) {
            PlatformFailureRegistry.mostRecentSince(startedAt)?.code
                ?: com.kitsugi.animelist.data.platform.PlatformErrorCode.COM_1020
        } else {
            PlatformFailureRegistry.reportAndResolve(engine.id, error)
        }

    fun search(resetPage: Boolean = true, seedResults: List<JikanSearchResult> = emptyList()) {
        val state = _uiState.value
        if (state.query.isBlank() && !state.hasFiltersApplied) {
            clearResults()
            return
        }

        searchJob?.cancel()

        // Bu Ã§alÄ±ÅŸmanÄ±n nesli. Sonraki aramalar sayacÄ± ilerletir; o anda hÃ¢lÃ¢ Ã§alÄ±ÅŸan
        // (iptali yutan) eski Ã§alÄ±ÅŸmalar UI durumuna yazamaz.
        val generation = searchGeneration.incrementAndGet()
        val searchStartedAt = System.currentTimeMillis()
        // Capture before the loading-state update.  The state object is immutable, so
        // effective provider bridges derived from it remain stable for every page.
        activeSearchSnapshot = SearchRequestSnapshot(
            state = state,
            showAdult = showAdultContentState,
            generation = generation
        )

        val queryNotBlank = state.query.isNotBlank()
        val newHistoryItem = if (queryNotBlank) {
            SearchHistoryItem(
                query = state.query.trim(),
                platform = state.selectedPlatform,
                mediaType = state.selectedMediaType,
                timestamp = System.currentTimeMillis(),
                engine = state.selectedEngine,
                scope = state.selectedScope
            )
        } else null

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                platformErrorCode = null,
                sourceErrorCodes = emptyMap(),
                page = if (resetPage) 1 else it.page,
                hasNextPage = true,
                results = seedResults,
                hasSearched = seedResults.isNotEmpty(),
                // "TÃ¼mÃ¼" aramasÄ±nda yÃ¼kleme bayraklarÄ± hemen (kapsama gÃ¶re) yazÄ±lÄ±r:
                // aksi halde raflar bir kare boyunca boÅŸ + yÃ¼klenmiyor gÃ¶rÃ¼nÃ¼p
                // kayboluyor, sonra shimmer ile geri geliyordu (titreme).
                multiResults = if (state.selectedEngine == SearchSourceEngine.ALL) {
                    planAllSourcesForState(state, state.selectedScope).toLoadingState()
                } else {
                    it.multiResults
                }
            )
        }

        searchJob = viewModelScope.launch {
            try {
                val rawQuery = state.query.trim()
                val (fetched, hasNext) = executeSearchForPage(
                    rawQuery,
                    page = 1,
                    generation = generation,
                    requestState = state,
                    requestShowAdult = showAdultContentState
                )
                // KaynaÄŸÄ±n gevÅŸek eÅŸleÅŸme/popÃ¼lerlik kuyruÄŸunu alta it; sorguyla
                // gerÃ§ekten Ã¶rtÃ¼ÅŸenleri Ã¶ne al.
                val results = SearchRelevance.rank(rawQuery, fetched)

                ensureActive()
                if (searchGeneration.get() != generation) return@launch

                if (newHistoryItem != null && searchHistoryEnabledState && results.isNotEmpty()) {
                    searchHistoryRepository.insertSearchQuery(newHistoryItem)
                }

                ensureActive()

                _uiState.update {
                    if (searchGeneration.get() != generation) return@update it
                    // Yenileme baÅŸarÄ±sÄ±z olsa bile kullanÄ±cÄ±yÄ± getiren raf kaydÄ± kaybolmaz.
                    val visible = mergeSourceSearchResults(seedResults, results)
                    it.copy(
                        results = visible,
                        isLoading = false,
                        hasSearched = true,
                        page = 1,
                        hasNextPage = hasNext,
                        errorMessage = if (visible.isEmpty()) KitsugiStrings.get(R.string.inline_no_results_found_5d2ab77) else null,
                        sourceErrorCodes = visibleSourceErrorCodes(it, searchStartedAt),
                        platformErrorCode = null
                    )
                }
                if (searchGeneration.get() == generation) saveCurrentStateToCache()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val failureCode = searchFailureCode(state.selectedEngine, e, searchStartedAt)
                _uiState.update {
                    if (searchGeneration.get() != generation) return@update it
                    it.copy(
                        isLoading = false,
                        errorMessage = if (it.results.isEmpty()) e.message ?: KitsugiStrings.get(R.string.arama_sirasinda_bir_hata_olustu) else null,
                        platformErrorCode = if (it.results.isEmpty()) failureCode else null,
                        sourceErrorCodes = visibleSourceErrorCodes(it, searchStartedAt),
                        hasNextPage = false
                    )
                }
                if (searchGeneration.get() == generation) saveCurrentStateToCache()
            }
        }
    }

    fun loadMore() {
        val liveState = _uiState.value
        if (liveState.isLoading || liveState.isLoadingMore || !liveState.hasNextPage) return
        val generation = searchGeneration.get()
        val request = activeSearchSnapshot?.takeIf { it.generation == generation }
            ?: SearchRequestSnapshot(liveState, showAdultContentState, generation)
        val state = request.state
        if (state.query.isBlank() && !state.hasFiltersApplied) return

        val nextPage = liveState.page + 1
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                val (moreResults, hasNext) = executeSearchForPage(
                    state.query.trim(),
                    page = nextPage,
                    generation = generation,
                    requestState = state,
                    requestShowAdult = request.showAdult
                )
                _uiState.update { current ->
                    // Nesil deÄŸiÅŸtiyse (araya yeni bir arama girdiyse) sayfalamayÄ± uygulama.
                    if (searchGeneration.get() != generation) return@update current
                    val currentIds = current.results.map { SearchRelevance.keyOf(it) }.toSet()
                    val uniqueNew = moreResults.filter { SearchRelevance.keyOf(it) !in currentIds }
                    current.copy(
                        results = current.results + uniqueNew,
                        page = nextPage,
                        // A provider may repeat its last page.  Do not keep an infinite
                        // scroll alive when the aggregate page added nothing new.
                        hasNextPage = hasNext && uniqueNew.isNotEmpty(),
                        isLoadingMore = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private suspend fun executeSearchForPage(
        queryText: String,
        page: Int,
        generation: Int = searchGeneration.get(),
        scopeOverride: SearchScope? = null,
        requestState: SearchUiState? = null,
        requestShowAdult: Boolean? = null
    ): Pair<List<JikanSearchResult>, Boolean> {
        val snapshot = activeSearchSnapshot?.takeIf { it.generation == generation }
        val state = requestState ?: snapshot?.state ?: _uiState.value
        val showAdult = requestShowAdult ?: snapshot?.showAdult ?: showAdultContentState

        val engine = state.selectedEngine
        val scope = scopeOverride ?: state.selectedScope

        // KaynaÄŸÄ±n "TÃ¼mÃ¼" rafÄ± anime + manga sorgularÄ±nÄ± birlikte kullanÄ±r.
        // Tam sayfa da aynÄ± iki uÃ§ noktayÄ± sayfalayarak sorgulamalÄ±.
        if (scope == SearchScope.ALL_MIXED && engine in mixedMediaEngines) {
            return supervisorScope {
                suspend fun fetch(mediaScope: SearchScope): Result<Pair<List<JikanSearchResult>, Boolean>> =
                    try {
                        Result.success(
                            executeSearchForPage(
                                queryText = queryText,
                                page = page,
                                generation = generation,
                                scopeOverride = mediaScope,
                                requestState = state,
                                requestShowAdult = showAdult
                            )
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                val anime = async { fetch(SearchScope.ANIME) }
                val manga = async { fetch(SearchScope.MANGA) }
                val animeOutcome = anime.await()
                val mangaOutcome = manga.await()
                // Ä°ki istek de HATA verdiyse bunu "SonuÃ§ bulunamadÄ±" diye yutma: gerÃ§ek mesajÄ± gÃ¶ster.
                if (animeOutcome.isFailure && mangaOutcome.isFailure) {
                    throw animeOutcome.exceptionOrNull() ?: mangaOutcome.exceptionOrNull()
                        ?: IllegalStateException("Arama baÅŸarÄ±sÄ±z")
                }
                val (animeResults, animeNext) = animeOutcome.getOrNull() ?: Pair(emptyList(), false)
                val (mangaResults, mangaNext) = mangaOutcome.getOrNull() ?: Pair(emptyList(), false)
                Pair(mergeSourceSearchResults(animeResults, mangaResults), animeNext || mangaNext)
            }
        }

        // If a specific engine is chosen (not ALL):
        if (engine != SearchSourceEngine.ALL) {
            when (engine) {
                SearchSourceEngine.MAL -> {
                    val f = state.effectiveMalFilters()
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val res = apiClient.searchMalCharacters(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        SearchScope.STAFF -> {
                            val res = apiClient.searchMalPeople(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        SearchScope.STUDIO -> {
                            val res = apiClient.searchMalProducers(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val malTypes = f.types.ifEmpty { listOfNotNull(f.type) }
                            val mediaTypes = malTypes.forMediaType(mediaType)
                            if (malTypes.isNotEmpty() && mediaTypes.isEmpty()) return Pair(emptyList(), false)
                            val requestedTypes = if (mediaTypes.isEmpty()) listOf<String?>(null) else mediaTypes.map { it }
                            val requestedStatuses = f.statuses.ifEmpty { listOfNotNull(f.status) }
                            val statuses = if (requestedStatuses.isEmpty()) listOf<String?>(null) else requestedStatuses.map { it }
                            suspend fun searchMalWithType(type: String?, status: String?): List<JikanSearchResult> =
                                apiClient.searchMalAdvanced(
                                    query = queryText,
                                    mediaType = mediaType,
                                    showAdultContent = showAdult,
                                    status = status,
                                    format = type,
                                    genres = f.genres.takeIf { it.isNotEmpty() },
                                    excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                                    rating = f.rating,
                                    minScore = f.minScore,
                                    maxScore = f.maxScore,
                                    producerId = f.producerId,
                                    magazineId = f.magazineId,
                                    letter = f.letter,
                                    sort = f.sortDirection,
                                    orderBy = f.orderBy,
                                    page = page,
                                    season = f.season,
                                    seasonYear = f.seasonYear,
                                    startYear = f.startYear,
                                    endYear = f.endYear
                                )
                            val requests = requestedTypes.flatMap { type -> statuses.map { status -> type to status } }
                            val typeResults = if (requests.size == 1) {
                                listOf(searchMalWithType(requests.first().first, requests.first().second))
                            } else {
                                coroutineScope {
                                    requests.map { (type, status) ->
                                        async(Dispatchers.IO) { searchMalWithType(type, status) }
                                    }.map { it.await() }
                                }
                            }
                            val res = typeResults.flatten().distinctBy { SearchRelevance.keyOf(it) }
                            return Pair(res, typeResults.any { it.size >= 24 })
                        }
                    }
                }
                SearchSourceEngine.TMDB -> {
                    val f = state.effectiveTmdbFilters()
                    when (scope) {
                        SearchScope.STAFF -> {
                            val res = TmdbApiClient().searchPerson(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        SearchScope.STUDIO -> {
                            val res = TmdbApiClient().searchCompany(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        else -> {
                            // TMDB's discover endpoint has no free-text query parameter and
                            // `with_keywords` accepts an ID, not the user's title.  Search first
                            // whenever text is present, then apply the fields available in the
                            // search payload locally.  This preserves the query and avoids the
                            // old "text passed as keyword ID => empty results" failure.
                            if (queryText.isNotBlank()) {
                                val discoveryCountry = if (scope == SearchScope.K_DRAMA) "KR" else f.originCountry
                                val requiresDiscovery = scope == SearchScope.K_DRAMA || discoveryCountry != null || f.minVoteCount != null ||
                                    f.minRuntime != null || f.maxRuntime != null || f.watchProviderId != null ||
                                    f.networkId != null || f.keyword != null || f.tvStatus != null || f.tvType != null
                                val baseMediaKinds = if (scope == SearchScope.ALL_MIXED) {
                                    listOf(MediaType.Movie, MediaType.TvShow)
                                } else {
                                    listOf(if (scope == SearchScope.MOVIE || (scope != SearchScope.TV && state.selectedMediaType == MediaType.Movie)) MediaType.Movie else MediaType.TvShow)
                                }
                                val mediaKinds = baseMediaKinds.filterByCommonFormats(state)
                                if (mediaKinds.isEmpty()) return Pair(emptyList(), false)
                                if (requiresDiscovery) {
                                    val client = TmdbApiClient()
                                    val res = mediaKinds.flatMap { kind ->
                                        client.searchAdvanced(
                                            query = queryText,
                                            isMovie = kind == MediaType.Movie,
                                            page = page,
                                            sortBy = f.sortBy,
                                            genres = f.genres.takeIf { it.isNotEmpty() },
                                            excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                                            originCountry = discoveryCountry,
                                            startYear = f.startYear,
                                            endYear = f.endYear,
                                            minScore = f.minScore,
                                            maxScore = f.maxScore,
                                            minVoteCount = f.minVoteCount,
                                            minRuntime = f.minRuntime,
                                            maxRuntime = f.maxRuntime,
                                            watchProviderId = f.watchProviderId,
                                            networkId = f.networkId,
                                            companyId = f.companyId,
                                            keyword = f.keyword,
                                            tvStatus = f.tvStatus,
                                            tvType = f.tvType,
                                            includeAdult = f.includeAdult
                                        )
                                    }.distinctBy { SearchRelevance.keyOf(it) }
                                    return Pair(res, res.size >= 20)
                                }
                                val raw = TmdbApiClient().search(queryText, page = page)
                                val res = raw.filter {
                                    it.type in mediaKinds &&
                                        (scope !in setOf(SearchScope.MOVIE, SearchScope.TV, SearchScope.K_DRAMA) ||
                                            (scope == SearchScope.MOVIE && it.type == MediaType.Movie) ||
                                            (scope != SearchScope.MOVIE && it.type == MediaType.TvShow)) &&
                                        (showAdult || !it.isAdult) &&
                                        (f.genres.isEmpty() || f.genres.all { id -> id in it.genreIds }) &&
                                        (f.excludedGenres.none { id -> id in it.genreIds }) &&
                                        (f.startYear == null || (it.year ?: Int.MIN_VALUE) >= f.startYear) &&
                                        (f.endYear == null || (it.year ?: Int.MAX_VALUE) <= f.endYear) &&
                                        (f.minScore == null || (it.score ?: 0).toDouble() >= f.minScore) &&
                                        (f.maxScore == null || (it.score ?: 10).toDouble() <= f.maxScore)
                                }
                                return Pair(res, raw.size >= 20)
                            }
                            val baseMediaKinds = if (scope == SearchScope.ALL_MIXED) {
                                listOf(MediaType.Movie, MediaType.TvShow)
                            } else {
                                listOf(if (scope == SearchScope.MOVIE || (scope != SearchScope.TV && state.selectedMediaType == MediaType.Movie)) MediaType.Movie else MediaType.TvShow)
                            }
                            val mediaKinds = baseMediaKinds.filterByCommonFormats(state)
                            if (mediaKinds.isEmpty()) return Pair(emptyList(), false)
                            val country = if (scope == SearchScope.K_DRAMA) "KR" else f.originCountry
                            val res = mediaKinds.flatMap { kind ->
                                TmdbApiClient().discoverAdvanced(
                                    isMovie = kind == MediaType.Movie,
                                    page = page,
                                    sortBy = f.sortBy,
                                    genres = f.genres,
                                    excludedGenres = f.excludedGenres,
                                    originCountry = country,
                                    startYear = f.startYear,
                                    endYear = f.endYear,
                                    minScore = f.minScore,
                                    maxScore = f.maxScore,
                                    minVoteCount = f.minVoteCount,
                                    minRuntime = f.minRuntime,
                                    maxRuntime = f.maxRuntime,
                                    watchProviderId = f.watchProviderId,
                                    networkId = f.networkId,
                                    companyId = f.companyId,
                                    // TMDB `with_keywords` yalnızca SAYISAL anahtar kelime
                                    // kimliği kabul eder. Serbest metin buraya gönderildiğinde
                                    // TMDB boş liste döndürüyordu; metin sorgusu artık anahtar
                                    // kelime olarak gönderilmez (sorgu metni diğer kaynaklarda
                                    // aranmaya devam eder, bu raf filtre kataloğunu gösterir).
                                    keyword = if (queryText.isNotBlank()) {
                                        queryText.trim().takeIf { it.all(Char::isDigit) }
                                    } else {
                                        f.keyword
                                    },
                                    tvStatus = f.tvStatus,
                                    tvType = f.tvType,
                                    includeAdult = f.includeAdult
                                )
                            }.distinctBy { SearchRelevance.keyOf(it) }
                            return Pair(res, res.size >= 20)
                        }
                    }
                }
                SearchSourceEngine.SHIKIMORI -> {
                    val f = state.effectiveShikimoriFilters()
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val res = KitsugiShikimoriClient.searchCharacters(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        SearchScope.STAFF -> {
                            val res = KitsugiShikimoriClient.searchPeople(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val kinds = if (scope == SearchScope.MANHWA) listOf("manhwa")
                                else if (scope == SearchScope.MANHUA) listOf("manhua")
                                else if (scope == SearchScope.LIGHT_NOVEL) listOf("light_novel")
                                else f.kinds.ifEmpty { null }
                            val res = KitsugiShikimoriClient.searchMediaAdvanced(
                                mediaType = mediaType,
                                query = queryText,
                                page = page,
                                order = f.order,
                                kinds = kinds,
                                statuses = f.statuses.ifEmpty { null },
                                season = f.season,
                                score = f.minScore,
                                duration = f.duration,
                                rating = f.rating,
                                genres = f.genres,
                                excludedGenres = f.excludedGenres,
                                studioId = f.studioId,
                                publisherId = f.publisherId,
                                censored = f.censored
                            )
                            return Pair(res, res.size >= 24)
                        }
                    }
                }
                SearchSourceEngine.BANGUMI -> {
                    val f = state.effectiveBangumiFilters()
                    // Bangumi arama uÃ§larÄ± sayfa baÅŸÄ±na EN FAZLA 20 kayÄ±t dÃ¶ndÃ¼rÃ¼r (sunucu limiti
                    // sessizce 20'ye kÄ±sar). 24 istenirse offset her sayfada 4 kayÄ±t atlar ve
                    // `size >= 24` kontrolÃ¼ "sonraki sayfa yok" sonucu verir.
                    val bangumiPageSize = BangumiApiClient.SEARCH_PAGE_SIZE
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val res = KitsugiBangumiClient.searchCharacters(
                                queryText, page = page, limit = bangumiPageSize, includeAdult = showAdult
                            )
                            return Pair(res, res.size >= bangumiPageSize)
                        }
                        SearchScope.STAFF -> {
                            val res = KitsugiBangumiClient.searchPeople(
                                queryText, page = page, limit = bangumiPageSize, career = f.career
                            )
                            return Pair(res, res.size >= bangumiPageSize)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val res = KitsugiBangumiClient.searchMediaAdvanced(
                                mediaType = mediaType,
                                query = queryText,
                                page = page,
                                limit = bangumiPageSize,
                                sort = f.sort,
                                tags = f.tags,
                                yearFrom = f.yearFrom,
                                yearTo = f.yearTo,
                                minScore = f.minScore,
                                includeAdult = f.nsfw || showAdult,
                                comicsOnly = f.comicsOnly
                            )
                            return Pair(res, res.size >= bangumiPageSize)
                        }
                    }
                }
                SearchSourceEngine.KITSU -> {
                    val f = state.effectiveKitsuFilters()
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val res = KitsuExploreClient.searchCharacters(queryText, page = page)
                            return Pair(res, res.size >= 20)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val subtypes = if (scope == SearchScope.MANGA) listOf("manga")
                                else if (scope == SearchScope.MANHWA) listOf("manhwa")
                                else if (scope == SearchScope.MANHUA) listOf("manhua")
                                else if (scope == SearchScope.LIGHT_NOVEL) listOf("novel")
                                else f.subtypes.ifEmpty { null }
                            val res = KitsuExploreClient.searchMediaAdvanced(
                                mediaType = mediaType,
                                query = queryText,
                                page = page,
                                sort = f.sort,
                                subtypes = subtypes,
                                statuses = f.statuses.ifEmpty { null },
                                season = f.season,
                                seasonYear = f.seasonYear,
                                categories = f.categories,
                                ageRating = f.ageRating,
                                streamers = f.streamers,
                                minRating = f.minRating
                            )
                            return Pair(res, res.size >= 20)
                        }
                    }
                }
                SearchSourceEngine.SIMKL -> {
                    val f = state.effectiveSimklFilters()
                    val simklType = when (scope) {
                        SearchScope.MOVIE -> "movies"
                        SearchScope.TV -> "tv"
                        SearchScope.ANIME -> "anime"
                        else -> "anime"
                    }
                    val genres = f.genres.ifEmpty { listOfNotNull(f.genre) }
                    val hasCustomFilters = f.activeCount > 0
                    suspend fun searchSimklType(type: String): List<JikanSearchResult> {
                        if (queryText.isBlank() && !hasCustomFilters && f.trendingPeriod.isNotBlank()) {
                            return SimklApiClient().getTrendingPeriodPage(type, f.trendingPeriod, page, pageSize = 20)
                        }
                        if (genres.size <= 1) {
                            return SimklApiClient().searchAdvanced(
                                type = type,
                                query = queryText,
                                subtype = f.subtype,
                                genre = genres.firstOrNull(),
                                country = f.country,
                                year = f.year,
                                sort = f.sort,
                                page = page
                            )
                        }
                        // The Simkl discovery path accepts one genre segment. Query each
                        // selected genre and merge unique records instead of silently using
                        // only the first chip.
                        return coroutineScope {
                            genres.map { selectedGenre ->
                                async(Dispatchers.IO) {
                                    SimklApiClient().searchAdvanced(
                                        type = type,
                                        query = queryText,
                                        subtype = f.subtype,
                                        genre = selectedGenre,
                                        country = f.country,
                                        year = f.year,
                                        sort = f.sort,
                                        page = page
                                    )
                                }
                            }.flatMap { it.await() }.distinctBy { SearchRelevance.keyOf(it) }
                        }
                    }
                    val res = if (queryText.isNotBlank() && scope == SearchScope.ALL_MIXED && !hasCustomFilters) {
                        // Raf da Ã¼Ã§ Simkl kataloÄŸunu (anime, dizi, film) birlikte arÄ±yor.
                        SimklApiClient().search(queryText, limit = 20, page = page)
                    } else if (scope == SearchScope.ALL_MIXED) {
                        coroutineScope {
                            listOf("anime", "tv", "movies").map { type ->
                                async(Dispatchers.IO) { searchSimklType(type) }
                            }.flatMap { it.await() }.distinctBy { SearchRelevance.keyOf(it) }
                        }
                    } else {
                        searchSimklType(simklType)
                    }
                    return Pair(res, res.size >= 20)
                }
                SearchSourceEngine.ANILIST -> {
                    val f = state.effectiveAniListFilters()
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val paged = apiClient.searchCharacters(queryText, page = page, perPage = 24)
                            return Pair(paged.results, paged.hasNextPage)
                        }
                        SearchScope.STAFF -> {
                            val paged = apiClient.searchStaff(queryText, page = page, perPage = 24)
                            return Pair(paged.results, paged.hasNextPage)
                        }
                        SearchScope.STUDIO -> {
                            val paged = apiClient.searchStudios(queryText, page = page, perPage = 24)
                            return Pair(paged.results, paged.hasNextPage)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val country = if (scope == SearchScope.MANHWA) "KR" else if (scope == SearchScope.MANHUA) "CN" else f.country
                            val format = if (scope == SearchScope.LIGHT_NOVEL) "NOVEL" else null
                            val formats = if (f.formats.isNotEmpty()) f.formats else if (format != null) listOf(format) else null

                            val paged = apiClient.searchAniListPaged(
                                query = queryText,
                                mediaType = mediaType,
                                showAdultContent = showAdult,
                                formats = formats,
                                statuses = f.statuses.ifEmpty { null },
                                season = f.season,
                                seasonYear = f.seasonYear,
                                genres = f.genres.ifEmpty { null },
                                excludedGenres = f.excludedGenres.ifEmpty { null },
                                tags = f.tags.ifEmpty { null },
                                minYear = f.startYear,
                                maxYear = f.endYear,
                                minScore = f.minScore,
                                maxScore = f.maxScore,
                                minEpCh = f.minEpCh,
                                maxEpCh = f.maxEpCh,
                                minDuration = f.minDuration,
                                // Sorgu varken varsayılan popülerlik sıralaması
                                // gönderilmez: AniList kendi alakalılık sıralamasını
                                // kullanır. "Tümü" rafı bunu zaten böyle yapıyordu;
                                // kaynağa özel AniList sayfası yapmadığı için aynı
                                // sorgu rafta ve sayfada farklı sırada geliyordu.
                                sort = if (queryText.isNotBlank() && f.sort == "POPULARITY_DESC") {
                                    emptyList()
                                } else {
                                    listOf(f.sort)
                                },
                                country = country,
                                sources = f.sources.ifEmpty { null },
                                isAdult = f.isAdult,
                                // AniList "Lisansörler" seçimi `licensedBy_in`, "minimum
                                // etiket güveni" ise `minimumTagRank` olarak gönderilir.
                                isLicensed = null,
                                licensedBy = f.licensedBy.ifEmpty { null },
                                minimumTagRank = f.minimumTagRank,
                                minVolumes = f.minVolumes,
                                maxVolumes = f.maxVolumes,
                                page = page,
                                perPage = 24
                            )
                            return Pair(paged.results, paged.hasNextPage)
                        }
                    }
                }
                SearchSourceEngine.ALL -> Unit
            }
        }

        // 0. SeÃ§enek C: "TÃ¼mÃ¼ (All-in-One Ã‡oklu Platform Arama)"
        //
        // Karakter/Personel kapsamlarÄ± bu dala GÄ°RMEZ: "TÃ¼mÃ¼" motoru seÃ§iliyken
        // karakter aramasÄ± da yedi medya rafÄ±nÄ± tetikliyordu ve Karakter sekmesi bu
        // medya kayÄ±tlarÄ±nÄ± kiÅŸi satÄ±rÄ± gibi listeliyordu (alakasÄ±z sonuÃ§).
        val isShelfSearch = (state.currentTab == KitsugiSearchTab.All || engine == SearchSourceEngine.ALL) &&
            scope != SearchScope.CHARACTER && scope != SearchScope.STAFF
        if (isShelfSearch) {
            val plan = planAllSourcesForState(state, scope)
            _uiState.update { it.copy(multiResults = plan.toLoadingState()) }
            return supervisorScope {
                // Kapsam duyarlÄ± Ã§oklu platform aramasÄ±: her kaynak yalnÄ±zca kapsamÄ±n
                // gerÃ§ekten istediÄŸi iÃ§erik ailesini sorgular.
                //   ALL_MIXED                â†’ anime + manga (dÃ¶nÃ¼ÅŸÃ¼mlÃ¼ birleÅŸtirilir)
                //   ANIME                    â†’ yalnÄ±zca anime
                //   MANGA/MANHWA/MANHUA/LN   â†’ yalnÄ±zca manga
                //   TV/MOVIE/K_DRAMA         â†’ yalnÄ±zca TMDB + Simkl (film/dizi)
                val wantAnime = scope.wantsAnimeSources && !scope.isLiveActionScope
                val wantManga = scope.wantsMangaSources && !scope.isLiveActionScope
                val kitsuSubtypes = when (scope) {
                    SearchScope.MANHWA -> listOf("manhwa")
                    SearchScope.MANHUA -> listOf("manhua")
                    SearchScope.LIGHT_NOVEL -> listOf("novel")
                    else -> null
                }
                val aniListCountry = when (scope) {
                    SearchScope.MANHWA -> "KR"
                    SearchScope.MANHUA -> "CN"
                    else -> null
                }
                // Film/dizi kapsamlarÄ±nda TMDB'nin karma (movie+tv) yanÄ±tÄ± istenen
                // tÃ¼re sÃ¼zÃ¼lÃ¼r; "Diziler" rafÄ±nda film, "Filmler" rafÄ±nda dizi Ã§Ä±kmaz.
                val tmdbAllowedTypes: Set<MediaType>? = when (scope) {
                    SearchScope.TV, SearchScope.K_DRAMA -> setOf(MediaType.TvShow)
                    SearchScope.MOVIE -> setOf(MediaType.Movie)
                    else -> null
                }
                // AniList'e sorgu varken POPULARITY_DESC gÃ¶nderilmez: boÅŸ sort listesi
                // deÄŸiÅŸkeni hiÃ§ yazmaz ve AniList kendi alakalÄ±lÄ±k (SEARCH_MATCH)
                // sÄ±ralamasÄ±nÄ± kullanÄ±r. PopÃ¼lerlik sÄ±ralamasÄ±, sorguyla gevÅŸek
                // eÅŸleÅŸen popÃ¼ler kayÄ±tlarÄ± rafÄ±n baÅŸÄ±na taÅŸÄ±yordu.
                val aniListShelfSort = if (queryText.isNotBlank()) emptyList() else listOf("POPULARITY_DESC")

                val resultsLock = Any()
                val combinedResults = mutableListOf<JikanSearchResult>()

                fun rethrowIfCancellation(error: Throwable) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                }

                fun onPlatformCompleted(
                    platformResults: List<JikanSearchResult>,
                    updater: (MultiPlatformResults) -> MultiPlatformResults
                ) {
                    // Bayat Ã§alÄ±ÅŸma korumasÄ±: iptal edilen (artÄ±k geÃ§ersiz) bir arama,
                    // kaynak sonuÃ§larÄ±nÄ± yeni aramanÄ±n Ã¼zerine YAZAMAZ â€” aksi halde yeni
                    // sorgunun AniList/MAL satÄ±rlarÄ± boÅŸalÄ±rken Shikimori/Kitsu satÄ±rlarÄ±
                    // eski sorgunun (alakasÄ±z) sonuÃ§larÄ±yla doluyordu.
                    if (searchGeneration.get() != generation) {
                        android.util.Log.d(
                            "SearchViewModel",
                            "Bayat arama nesli (gen=$generation, gÃ¼ncel=${searchGeneration.get()}) â€” kaynak sonucu yazÄ±lmadÄ±"
                        )
                        return
                    }
                    _uiState.update { current ->
                        val newMulti = updater(current.multiResults)
                        val merged = synchronized(resultsLock) {
                            combinedResults.addAll(platformResults)
                            // Anahtara medya tÃ¼rÃ¼ de girer: Kitsu, Shikimori, MAL ve
                            // TMDB'de anime ile manga (film ile dizi) AYRI kimlik uzayÄ±
                            // kullanÄ±r, aynÄ± numara iki farklÄ± kayda aittir. TÃ¼rsÃ¼z
                            // anahtar karma aramada meÅŸru kayÄ±tlarÄ± sessizce siliyordu.
                            combinedResults.distinctBy { SearchRelevance.keyOf(it) }
                        }
                        current.copy(
                            multiResults = newMulti,
                            results = merged,
                            hasSearched = true
                        )
                    }
                }

                suspend fun fetchSource(block: suspend () -> List<JikanSearchResult>): List<JikanSearchResult> =
                    runCatching { block() }.getOrElse { error ->
                        rethrowIfCancellation(error)
                        emptyList()
                    }

                /**
                 * Bir kaynaÄŸÄ±n anime + manga uÃ§larÄ±nÄ± paralel Ã§alÄ±ÅŸtÄ±rÄ±r, sonuÃ§larÄ±
                 * dÃ¶nÃ¼ÅŸÃ¼mlÃ¼ birleÅŸtirir (manga, anime altÄ±nda ezilmesin), sorguyla
                 * Ã¶rtÃ¼ÅŸenleri Ã¶ne alÄ±r ve raf sÄ±nÄ±rÄ±na indirir.
                 *
                 * [active] false ise aÄŸ isteÄŸi hiÃ§ yapÄ±lmaz: raf boÅŸ + yÃ¼klenmiyor
                 * kalÄ±r ve Ã§izilmez.
                 */
                suspend fun fetchMediaShelf(
                    active: Boolean,
                    anime: suspend () -> List<JikanSearchResult>,
                    manga: suspend () -> List<JikanSearchResult>
                ): List<JikanSearchResult> {
                    if (!active || (!wantAnime && !wantManga)) return emptyList()
                    val raw = withTimeoutOrNull(allSourceTimeoutMs) {
                        fetchSource {
                            coroutineScope {
                                val animePart = if (wantAnime) {
                                    async(Dispatchers.IO) { fetchSource(anime) }
                                } else {
                                    null
                                }
                                val mangaPart = if (wantManga) {
                                    async(Dispatchers.IO) { fetchSource(manga) }
                                } else {
                                    null
                                }
                                SearchRelevance.interleave(
                                    animePart?.await() ?: emptyList(),
                                    mangaPart?.await() ?: emptyList()
                                )
                            }
                        }
                    } ?: emptyList()
                    return SearchRelevance.refine(queryText, raw).take(allShelfLimit)
                }

                /** Tek iÃ§erik ailesi olan kaynaklar (TMDB, Simkl) iÃ§in. */
                suspend fun fetchSingleShelf(
                    active: Boolean,
                    block: suspend (Int) -> List<JikanSearchResult>
                ): List<JikanSearchResult> {
                    if (!active) return emptyList()
                    val raw = withTimeoutOrNull(allSourceTimeoutMs) { fetchSource { block(page) } } ?: emptyList()
                    return SearchRelevance.refine(queryText, raw).take(allShelfLimit)
                }

                fun appendPage(previous: List<JikanSearchResult>, next: List<JikanSearchResult>): List<JikanSearchResult> =
                    if (page <= 1) next else (previous + next).distinctBy { SearchRelevance.keyOf(it) }

                val aniListDef = async(Dispatchers.IO) {
                    val f = state.effectiveAniListFilters()
                    val globalAniListGenres = state.genres.filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }
                        .map(::translateToEnglishForSearch)
                    val globalAniListTags = (state.tags + state.genres.filterNot { CanonicalFilterBridge.isOfficialAniListGenre(it) })
                        .map(::translateToEnglishForSearch).distinct()
                    val requestedFormats = f.formats.ifEmpty { state.selectedFormats.map { it.apiValue } }
                    val animeFormats = requestedFormats.forMediaType(MediaType.Anime)
                    val mangaFormats = requestedFormats.forMediaType(MediaType.Manga)
                    val res = fetchMediaShelf(
                        active = plan.aniList,
                        anime = {
                            if (requestedFormats.isNotEmpty() && animeFormats.isEmpty()) emptyList()
                            else apiClient.searchAniListPaged(
                                query = queryText,
                                mediaType = MediaType.Anime,
                                showAdultContent = showAdult,
                                formats = animeFormats.takeIf { it.isNotEmpty() },
                                statuses = f.statuses.ifEmpty { state.selectedStatuses.map { it.apiValue } }.takeIf { it.isNotEmpty() },
                                season = f.season ?: state.season?.apiValue,
                                seasonYear = f.seasonYear ?: state.startYear,
                                genres = f.genres.ifEmpty { globalAniListGenres }.takeIf { it.isNotEmpty() },
                                excludedGenres = f.excludedGenres.ifEmpty { state.excludedGenres.filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }.map(::translateToEnglishForSearch) }.takeIf { it.isNotEmpty() },
                                tags = f.tags.ifEmpty { globalAniListTags }.takeIf { it.isNotEmpty() },
                                minYear = f.startYear ?: state.startYear,
                                maxYear = f.endYear ?: state.endYear,
                                sort = if (f.sort == "POPULARITY_DESC") aniListShelfSort else listOf(f.sort),
                                country = aniListCountry ?: f.country,
                                sources = f.sources.takeIf { it.isNotEmpty() },
                                isAdult = f.isAdult,
                                licensedBy = f.licensedBy.takeIf { it.isNotEmpty() },
                                minimumTagRank = f.minimumTagRank,
                                minVolumes = f.minVolumes,
                                maxVolumes = f.maxVolumes,
                                page = page,
                                perPage = allShelfFetchLimit
                            ).results
                        },
                        manga = {
                            apiClient.searchAniListPaged(
                                query = queryText,
                                mediaType = MediaType.Manga,
                                showAdultContent = showAdult,
                                formats = mangaFormats.takeIf { it.isNotEmpty() },
                                statuses = f.statuses.ifEmpty { state.selectedStatuses.map { it.apiValue } }.takeIf { it.isNotEmpty() },
                                season = f.season ?: state.season?.apiValue,
                                seasonYear = f.seasonYear ?: state.startYear,
                                genres = f.genres.ifEmpty { globalAniListGenres }.takeIf { it.isNotEmpty() },
                                excludedGenres = f.excludedGenres.ifEmpty { state.excludedGenres.filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }.map(::translateToEnglishForSearch) }.takeIf { it.isNotEmpty() },
                                tags = f.tags.ifEmpty { globalAniListTags }.takeIf { it.isNotEmpty() },
                                minYear = f.startYear ?: state.startYear,
                                maxYear = f.endYear ?: state.endYear,
                                sort = if (f.sort == "POPULARITY_DESC") aniListShelfSort else listOf(f.sort),
                                country = aniListCountry ?: f.country,
                                sources = f.sources.takeIf { it.isNotEmpty() },
                                isAdult = f.isAdult,
                                licensedBy = f.licensedBy.takeIf { it.isNotEmpty() },
                                minimumTagRank = f.minimumTagRank,
                                minVolumes = f.minVolumes,
                                maxVolumes = f.maxVolumes,
                                page = page,
                                perPage = allShelfFetchLimit
                            ).results
                        }
                    )
                    onPlatformCompleted(res) { it.copy(aniListResults = appendPage(it.aniListResults, res), isLoadingAniList = false) }
                    res
                }

                val malDef = async(Dispatchers.IO) {
                    val f = state.effectiveMalFilters()
                    suspend fun searchMal(type: MediaType): List<JikanSearchResult> {
                        val allRequestedTypes = f.types.ifEmpty { listOfNotNull(f.type) }
                        val requestedTypes = allRequestedTypes.forMediaType(type)
                        if (allRequestedTypes.isNotEmpty() && requestedTypes.isEmpty()) return emptyList()
                        val requestedStatuses = f.statuses.ifEmpty { listOfNotNull(f.status) }
                        suspend fun fetch(format: String?, status: String?): List<JikanSearchResult> = apiClient.searchMalAdvanced(
                            query = queryText,
                            mediaType = type,
                            showAdultContent = showAdult,
                            status = status,
                            format = format,
                            genres = f.genres.takeIf { it.isNotEmpty() },
                            excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                            rating = f.rating,
                            minScore = f.minScore,
                            maxScore = f.maxScore,
                            producerId = f.producerId,
                            magazineId = f.magazineId,
                            letter = f.letter,
                            sort = f.sortDirection,
                            orderBy = f.orderBy,
                            page = page,
                            season = f.season,
                            seasonYear = f.seasonYear,
                            startYear = f.startYear,
                            endYear = f.endYear
                        )
                        val formats = if (requestedTypes.isEmpty()) listOf<String?>(null) else requestedTypes.map { it }
                        val statuses = if (requestedStatuses.isEmpty()) listOf<String?>(null) else requestedStatuses.map { it }
                        val requests = formats.flatMap { format -> statuses.map { status -> format to status } }
                        return if (requests.size == 1) fetch(requests.first().first, requests.first().second)
                        else coroutineScope {
                            requests.map { (format, status) ->
                                async(Dispatchers.IO) { fetch(format, status) }
                            }.flatMap { it.await() }.distinctBy { SearchRelevance.keyOf(it) }
                        }
                    }
                    val res = fetchMediaShelf(
                        active = plan.mal,
                        anime = { searchMal(MediaType.Anime) },
                        manga = { searchMal(MediaType.Manga) }
                    )
                    onPlatformCompleted(res) { it.copy(malResults = appendPage(it.malResults, res), isLoadingMal = false) }
                    res
                }

                val tmdbDef = async(Dispatchers.IO) {
                    val f = state.effectiveTmdbFilters()
                    val res = fetchSingleShelf(active = plan.tmdb) {
                        val client = TmdbApiClient()
                        val allowed = tmdbAllowedTypes
                        if (queryText.isNotBlank()) {
                            val discoveryCountry = if (scope == SearchScope.K_DRAMA) "KR" else f.originCountry
                            val needsDiscovery = scope == SearchScope.K_DRAMA || discoveryCountry != null || f.minVoteCount != null ||
                                f.minRuntime != null || f.maxRuntime != null || f.watchProviderId != null ||
                                f.networkId != null || f.keyword != null || f.tvStatus != null || f.tvType != null
                            val baseMediaKinds = allowed?.toList() ?: listOf(MediaType.Movie, MediaType.TvShow)
                            val mediaKinds = baseMediaKinds.filterByCommonFormats(state)
                            if (mediaKinds.isEmpty()) return@fetchSingleShelf emptyList()
                            if (needsDiscovery) {
                                mediaKinds.flatMap { type ->
                                    client.searchAdvanced(
                                        query = queryText,
                                        isMovie = type == MediaType.Movie,
                                        page = page,
                                        sortBy = f.sortBy,
                                        genres = f.genres.takeIf { it.isNotEmpty() },
                                        excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                                        originCountry = discoveryCountry,
                                        startYear = f.startYear ?: state.startYear,
                                        endYear = f.endYear ?: state.endYear,
                                        minScore = f.minScore,
                                        maxScore = f.maxScore,
                                        minVoteCount = f.minVoteCount,
                                        minRuntime = f.minRuntime,
                                        maxRuntime = f.maxRuntime,
                                        watchProviderId = f.watchProviderId,
                                        networkId = f.networkId,
                                        companyId = f.companyId,
                                        keyword = f.keyword,
                                        tvStatus = f.tvStatus,
                                        tvType = f.tvType,
                                        includeAdult = f.includeAdult
                                    )
                                }.distinctBy { SearchRelevance.keyOf(it) }
                            } else {
                                val raw = client.search(queryText, page = page)
                                raw.filter {
                                    it.type in mediaKinds &&
                                        (showAdult || !it.isAdult) &&
                                        (f.genres.isEmpty() || f.genres.all { id -> id in it.genreIds }) &&
                                        (f.excludedGenres.none { id -> id in it.genreIds }) &&
                                        (f.startYear == null || (it.year ?: Int.MIN_VALUE) >= f.startYear) &&
                                        (f.endYear == null || (it.year ?: Int.MAX_VALUE) <= f.endYear) &&
                                        (f.minScore == null || (it.score ?: 0).toDouble() >= f.minScore) &&
                                        (f.maxScore == null || (it.score ?: 10).toDouble() <= f.maxScore)
                                }
                            }
                        } else {
                            val baseMediaKinds = allowed?.toList() ?: listOf(MediaType.Movie, MediaType.TvShow)
                            val mediaKinds = baseMediaKinds.filterByCommonFormats(state)
                            if (mediaKinds.isEmpty()) return@fetchSingleShelf emptyList()
                            mediaKinds.flatMap { type ->
                                val isMovie = type == MediaType.Movie
                                client.discoverAdvanced(
                                    isMovie = isMovie,
                                    page = page,
                                    sortBy = f.sortBy,
                                    genres = f.genres.takeIf { it.isNotEmpty() },
                                    excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                                    originCountry = if (scope == SearchScope.K_DRAMA) "KR" else f.originCountry,
                                    startYear = f.startYear ?: state.startYear,
                                    endYear = f.endYear ?: state.endYear,
                                    minScore = f.minScore,
                                    maxScore = f.maxScore,
                                    minVoteCount = f.minVoteCount,
                                    minRuntime = f.minRuntime,
                                    maxRuntime = f.maxRuntime,
                                    watchProviderId = f.watchProviderId,
                                    networkId = f.networkId,
                                    companyId = f.companyId,
                                    keyword = f.keyword,
                                    tvStatus = f.tvStatus,
                                    tvType = f.tvType,
                                    includeAdult = f.includeAdult
                                )
                            }.distinctBy { SearchRelevance.keyOf(it) }
                        }
                    }
                    onPlatformCompleted(res) { it.copy(tmdbResults = appendPage(it.tmdbResults, res), isLoadingTmdb = false) }
                    res
                }

                val shikimoriDef = async(Dispatchers.IO) {
                    val f = state.effectiveShikimoriFilters()
                    suspend fun searchShikimori(type: MediaType): List<JikanSearchResult> {
                        val requestedKinds = f.kinds.forMediaType(type)
                        if (f.kinds.isNotEmpty() && requestedKinds.isEmpty()) return emptyList()
                        return KitsugiShikimoriClient.searchMediaAdvanced(
                            mediaType = type,
                            query = queryText,
                            page = page,
                            limit = allShelfFetchLimit,
                            order = f.order,
                            kinds = requestedKinds.takeIf { it.isNotEmpty() },
                            statuses = f.statuses.takeIf { it.isNotEmpty() },
                            season = f.season,
                            score = f.minScore,
                            duration = f.duration,
                            rating = f.rating,
                            genres = f.genres.takeIf { it.isNotEmpty() },
                            excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                            studioId = f.studioId,
                            publisherId = f.publisherId,
                            censored = f.censored
                        )
                    }
                    val res = fetchMediaShelf(
                        active = plan.shikimori,
                        anime = { searchShikimori(MediaType.Anime) },
                        manga = { searchShikimori(MediaType.Manga) }
                    )
                    onPlatformCompleted(res) { it.copy(shikimoriResults = appendPage(it.shikimoriResults, res), isLoadingShikimori = false) }
                    res
                }

                val bangumiDef = async(Dispatchers.IO) {
                    val f = state.effectiveBangumiFilters()
                    suspend fun searchBangumi(type: MediaType) = KitsugiBangumiClient.searchMediaAdvanced(
                        mediaType = type,
                        query = queryText,
                        page = page,
                        limit = BangumiApiClient.SEARCH_PAGE_SIZE,
                        sort = f.sort,
                        tags = f.tags,
                        yearFrom = f.yearFrom,
                        yearTo = f.yearTo,
                        minScore = f.minScore,
                        includeAdult = f.nsfw || showAdult,
                        comicsOnly = f.comicsOnly
                    )
                    val res = fetchMediaShelf(
                        active = plan.bangumi,
                        anime = { searchBangumi(MediaType.Anime) },
                        manga = { searchBangumi(MediaType.Manga) }
                    )
                    onPlatformCompleted(res) { it.copy(bangumiResults = appendPage(it.bangumiResults, res), isLoadingBangumi = false) }
                    res
                }

                val kitsuDef = async(Dispatchers.IO) {
                    val f = state.effectiveKitsuFilters()
                    val requestedKitsuSubtypes = f.subtypes
                    val res = fetchMediaShelf(
                        active = plan.kitsu,
                        anime = {
                            val animeSubtypes = requestedKitsuSubtypes.forMediaType(MediaType.Anime)
                            if (requestedKitsuSubtypes.isNotEmpty() && animeSubtypes.isEmpty()) emptyList()
                            else KitsuExploreClient.searchMediaAdvanced(
                                mediaType = MediaType.Anime,
                                query = queryText,
                                page = page,
                                limit = allShelfFetchLimit,
                                sort = f.sort,
                                subtypes = animeSubtypes.takeIf { it.isNotEmpty() },
                                statuses = f.statuses.takeIf { it.isNotEmpty() },
                                season = f.season,
                                seasonYear = f.seasonYear,
                                categories = f.categories.takeIf { it.isNotEmpty() },
                                ageRating = f.ageRating,
                                streamers = f.streamers.takeIf { it.isNotEmpty() },
                                minRating = f.minRating
                            )
                        },
                        manga = {
                            val mangaSubtypes = (kitsuSubtypes ?: requestedKitsuSubtypes).forMediaType(MediaType.Manga)
                            val hasMangaSubtypeFilter = kitsuSubtypes != null || requestedKitsuSubtypes.isNotEmpty()
                            if (hasMangaSubtypeFilter && mangaSubtypes.isEmpty()) emptyList()
                            else KitsuExploreClient.searchMediaAdvanced(
                                mediaType = MediaType.Manga,
                                query = queryText,
                                page = page,
                                limit = allShelfFetchLimit,
                                sort = f.sort,
                                subtypes = mangaSubtypes.takeIf { it.isNotEmpty() },
                                statuses = f.statuses.takeIf { it.isNotEmpty() },
                                season = f.season,
                                seasonYear = f.seasonYear,
                                categories = f.categories.takeIf { it.isNotEmpty() },
                                ageRating = f.ageRating,
                                streamers = f.streamers.takeIf { it.isNotEmpty() },
                                minRating = f.minRating
                            )
                        }
                    )
                    onPlatformCompleted(res) { it.copy(kitsuResults = appendPage(it.kitsuResults, res), isLoadingKitsu = false) }
                    res
                }

                val simklDef = async(Dispatchers.IO) {
                    val f = state.effectiveSimklFilters()
                    val res = fetchSingleShelf(active = plan.simkl) {
                        val client = SimklApiClient()
                        val genres = f.genres.ifEmpty { listOfNotNull(f.genre) }
                        val simklTypes = if (scope == SearchScope.ALL_MIXED) {
                            listOf("anime", "tv", "movies")
                        } else {
                            listOf(scope.simklTypeFilter ?: "anime")
                        }

                        suspend fun fetchSimklType(type: String): List<JikanSearchResult> {
                            if (queryText.isNotBlank() && f.activeCount == 0) {
                                return client.search(queryText, type = type, limit = allShelfFetchLimit, page = page)
                            }
                            if (genres.size <= 1) {
                                return client.searchAdvanced(
                                    type = type,
                                    query = queryText,
                                    subtype = f.subtype,
                                    genre = genres.firstOrNull(),
                                    country = f.country,
                                    year = f.year,
                                    sort = f.sort,
                                    limit = allShelfFetchLimit,
                                    page = page
                                )
                            }
                            return coroutineScope {
                                genres.map { selectedGenre ->
                                    async(Dispatchers.IO) {
                                        client.searchAdvanced(
                                            type = type,
                                            query = queryText,
                                            subtype = f.subtype,
                                            genre = selectedGenre,
                                            country = f.country,
                                            year = f.year,
                                            sort = f.sort,
                                            limit = allShelfFetchLimit,
                                            page = page
                                        )
                                    }
                                }.flatMap { it.await() }.distinctBy { SearchRelevance.keyOf(it) }
                            }
                        }

                        if (simklTypes.size == 1) {
                            fetchSimklType(simklTypes.single())
                        } else {
                            coroutineScope {
                                simklTypes.map { type ->
                                    async(Dispatchers.IO) { fetchSimklType(type) }
                                }.flatMap { it.await() }.distinctBy { SearchRelevance.keyOf(it) }
                            }
                        }
                    }
                    onPlatformCompleted(res) { it.copy(simklResults = appendPage(it.simklResults, res), isLoadingSimkl = false) }
                    res
                }

                val aniListRes = runCatching { aniListDef.await() }.getOrElse { error -> rethrowIfCancellation(error); emptyList() }
                val malRes = runCatching { malDef.await() }.getOrElse { error -> rethrowIfCancellation(error); emptyList() }
                val tmdbRes = runCatching { tmdbDef.await() }.getOrElse { error -> rethrowIfCancellation(error); emptyList() }
                val shikimoriRes = runCatching { shikimoriDef.await() }.getOrElse { error -> rethrowIfCancellation(error); emptyList() }
                val kitsuRes = runCatching { kitsuDef.await() }.getOrElse { error -> rethrowIfCancellation(error); emptyList() }
                val simklRes = runCatching { simklDef.await() }.getOrElse { error -> rethrowIfCancellation(error); emptyList() }
                val bangumiRes = runCatching { bangumiDef.await() }.getOrElse { error -> rethrowIfCancellation(error); emptyList() }

                val finalCombined = SearchRelevance.dedupe(
                    aniListRes + malRes + tmdbRes + shikimoriRes + kitsuRes + simklRes + bangumiRes
                )

                if (searchGeneration.get() != generation) {
                    // Bu Ã§alÄ±ÅŸma arada baÅŸlayan yeni bir arama tarafÄ±ndan geÃ§ersiz kÄ±lÄ±ndÄ±;
                    // hiÃ§bir ÅŸey yazmÄ±yoruz (Ã§aÄŸÄ±ran tarafÄ±n ensureActive() kontrolÃ¼ de var).
                    return@supervisorScope Pair(_uiState.value.results, false)
                }

                _uiState.update {
                    it.copy(
                        multiResults = MultiPlatformResults(
                            aniListResults = appendPage(it.multiResults.aniListResults, aniListRes),
                            malResults = appendPage(it.multiResults.malResults, malRes),
                            tmdbResults = appendPage(it.multiResults.tmdbResults, tmdbRes),
                            shikimoriResults = appendPage(it.multiResults.shikimoriResults, shikimoriRes),
                            kitsuResults = appendPage(it.multiResults.kitsuResults, kitsuRes),
                            simklResults = appendPage(it.multiResults.simklResults, simklRes),
                            bangumiResults = appendPage(it.multiResults.bangumiResults, bangumiRes),
                            isLoadingAniList = false,
                            isLoadingMal = false,
                            isLoadingTmdb = false,
                            isLoadingShikimori = false,
                            isLoadingKitsu = false,
                            isLoadingSimkl = false,
                            isLoadingBangumi = false
                        ),
                        results = finalCombined
                    )
                }

                // Each provider is fetched with the requested page.  An empty aggregate
                // page is the only reliable stop signal because the seven APIs expose
                // different pagination metadata.
                Pair(finalCombined, finalCombined.isNotEmpty())
            }
        }

        // 1. MyAnimeList (MAL)
        if (state.currentTab == KitsugiSearchTab.MAL || state.selectedPlatform == SearchPlatform.MAL) {
            val results = apiClient.searchMALOnly(
                query = queryText,
                mediaType = state.selectedMediaType,
                showAdultContent = showAdult
            )
            return Pair(results, false)
        }

        // 2. Shikimori (Rusya / Shikimori.one)
        if (state.currentTab == KitsugiSearchTab.Shikimori || state.selectedPlatform == SearchPlatform.Shikimori) {
            val f = state.effectiveShikimoriFilters()
            val results = when (state.selectedScope) {
                SearchScope.CHARACTER -> KitsugiShikimoriClient.searchCharacters(queryText, page = page, limit = 24)
                SearchScope.STAFF -> KitsugiShikimoriClient.searchPeople(queryText, page = page, limit = 24, kind = f.peopleKind)
                else -> {
                    val targetType = if (state.selectedScope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL) || state.selectedMediaType == MediaType.Manga) {
                        MediaType.Manga
                    } else {
                        MediaType.Anime
                    }
                    KitsugiShikimoriClient.searchMediaAdvanced(
                        mediaType = targetType,
                        query = queryText,
                        page = page,
                        limit = 24,
                        order = f.order,
                        kinds = f.kinds.takeIf { it.isNotEmpty() },
                        statuses = f.statuses.takeIf { it.isNotEmpty() },
                        season = f.season,
                        score = f.minScore,
                        duration = f.duration,
                        rating = f.rating,
                        genres = f.genres.takeIf { it.isNotEmpty() },
                        excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                        studioId = f.studioId,
                        publisherId = f.publisherId,
                        censored = f.censored
                    )
                }
            }
            return Pair(results, results.size >= 20)
        }

        // 3. Kitsu
        if (state.currentTab == KitsugiSearchTab.Kitsu || state.selectedPlatform == SearchPlatform.Kitsu) {
            val f = state.effectiveKitsuFilters()
            val results = when (state.selectedScope) {
                SearchScope.CHARACTER -> KitsuExploreClient.searchCharacters(queryText, page = page, limit = 20)
                else -> {
                    val targetType = if (state.selectedScope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL) || state.selectedMediaType == MediaType.Manga) {
                        MediaType.Manga
                    } else {
                        MediaType.Anime
                    }
                    KitsuExploreClient.searchMediaAdvanced(
                        mediaType = targetType,
                        query = queryText,
                        page = page,
                        limit = 20,
                        sort = f.sort,
                        subtypes = f.subtypes.takeIf { it.isNotEmpty() },
                        statuses = f.statuses.takeIf { it.isNotEmpty() },
                        season = f.season,
                        seasonYear = f.seasonYear,
                        categories = f.categories.takeIf { it.isNotEmpty() },
                        ageRating = f.ageRating,
                        streamers = f.streamers.takeIf { it.isNotEmpty() },
                        minRating = f.minRating
                    )
                }
            }
            return Pair(results, results.size >= 20)
        }

        // 4. Simkl
        if (state.currentTab == KitsugiSearchTab.Simkl || state.selectedPlatform == SearchPlatform.Simkl) {
            val simklType = when (state.selectedMediaType) {
                MediaType.Movie -> "movie"
                MediaType.TvShow -> "tv"
                MediaType.Anime -> "anime"
                else -> null
            }
            val results = SimklApiClient().search(queryText, type = simklType, limit = 20, page = page)
            return Pair(results, results.size >= 20)
        }

        // 4b. Bangumi (Ã‡in / bgm.tv)
        if (state.currentTab == KitsugiSearchTab.Bangumi || state.selectedPlatform == SearchPlatform.Bangumi) {
            val f = state.effectiveBangumiFilters()
            val bangumiPageSize = BangumiApiClient.SEARCH_PAGE_SIZE
            val results = when (state.selectedScope) {
                SearchScope.CHARACTER -> KitsugiBangumiClient.searchCharacters(
                    queryText, page = page, limit = bangumiPageSize, includeAdult = showAdult
                )
                SearchScope.STAFF -> KitsugiBangumiClient.searchPeople(
                    queryText, page = page, limit = bangumiPageSize, career = f.career
                )
                else -> {
                    val targetType = if (state.selectedScope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL) || state.selectedMediaType == MediaType.Manga) {
                        MediaType.Manga
                    } else {
                        MediaType.Anime
                    }
                    KitsugiBangumiClient.searchMediaAdvanced(
                        mediaType = targetType,
                        query = queryText,
                        page = page,
                        limit = bangumiPageSize,
                        sort = f.sort,
                        tags = f.tags,
                        yearFrom = f.yearFrom,
                        yearTo = f.yearTo,
                        minScore = f.minScore,
                        includeAdult = f.nsfw || showAdult,
                        comicsOnly = f.comicsOnly
                    )
                }
            }
            return Pair(results, results.size >= bangumiPageSize)
        }

        // 5. Karakter Arama (AniHyou)
        if (state.currentTab == KitsugiSearchTab.Character) {
            val paged = apiClient.searchCharacters(queryText, page = page, perPage = 24)
            return Pair(paged.results, paged.hasNextPage)
        }

        // 6. Personel / Seslendirmen Arama (AniHyou)
        if (state.currentTab == KitsugiSearchTab.Staff) {
            val paged = apiClient.searchStaff(queryText, page = page, perPage = 24)
            return Pair(paged.results, paged.hasNextPage)
        }

        // 7. TMDB Platformu (Film & Dizi)
        if (state.currentTab == KitsugiSearchTab.TMDB || state.selectedPlatform == SearchPlatform.TMDB) {
            val tmdbGenreId = getTmdbGenreId(state.genres.firstOrNull() ?: state.tags.firstOrNull(), state.selectedMediaType == MediaType.Movie)
            val results = if (queryText.isBlank() && tmdbGenreId != null) {
                TmdbApiClient().discoverByGenre(tmdbGenreId, state.selectedMediaType == MediaType.Movie)
            } else if (queryText.isNotBlank()) {
                TmdbApiClient().search(queryText, page = page)
            } else {
                emptyList()
            }
            return Pair(results, results.size >= 20)
        }

        // 5. Anime / Manga Arama (AniHyou StandardÄ±nda AniList GraphQL Motoru)
        val targetMediaType = if (state.currentTab == KitsugiSearchTab.Manga || state.selectedMediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime
        val formatStrings = state.selectedFormats.map { it.apiValue }
        val statusStrings = state.selectedStatuses.map { it.apiValue }
        val sourceStrings = state.selectedSources.map { it.apiValue }
        val aniListCountry = state.country?.code
        val aniListGenres = getAniListGenreNames(state.genres.filter { isOfficialAniListGenre(it) })
        val aniListExcludedGenres = getAniListGenreNames(state.excludedGenres.filter { isOfficialAniListGenre(it) })
        val aniListTags = state.tags.toMutableList().apply {
            addAll(state.genres.filter { !isOfficialAniListGenre(it) })
        }.distinct()
        val effectiveSort = state.effectiveSortApiValue
        val aniListSort = when {
            effectiveSort == "SEARCH_MATCH" && queryText.isNotBlank() -> emptyList()
            effectiveSort == "SEARCH_MATCH" -> listOf("POPULARITY_DESC")
            else -> listOf(effectiveSort)
        }
        val isDoujinFlag = when (state.isDoujin) {
            true -> false
            false -> true
            null -> null
        }
        val fallbacks = generateFallbackQueries(queryText)

        return runCatching {
            var paged = apiClient.searchAniListPaged(
                query = queryText,
                mediaType = targetMediaType,
                showAdultContent = showAdult,
                formats = formatStrings.takeIf { it.isNotEmpty() },
                statuses = statusStrings.takeIf { it.isNotEmpty() },
                season = state.season?.apiValue,
                genres = aniListGenres.takeIf { it.isNotEmpty() },
                excludedGenres = aniListExcludedGenres.takeIf { it.isNotEmpty() },
                tags = aniListTags.takeIf { it.isNotEmpty() },
                minYear = state.startYear,
                maxYear = state.endYear,
                minScore = state.minScore,
                maxScore = state.maxScore,
                minEpCh = state.minEpCh,
                maxEpCh = state.maxEpCh,
                minDuration = state.minDuration,
                maxDuration = state.maxDuration,
                sort = aniListSort,
                country = aniListCountry,
                sources = sourceStrings.takeIf { it.isNotEmpty() },
                isAdult = state.isAdultFilter,
                isLicensed = isDoujinFlag,
                page = page,
                perPage = 24
            )

            // Ä°lk sayfada sonuÃ§ boÅŸsa fallback sorgularÄ±nÄ± dene
            if (paged.results.isEmpty() && page == 1 && queryText.isNotBlank()) {
                for (fb in fallbacks) {
                    if (fb != queryText) {
                        val fbPaged = apiClient.searchAniListPaged(
                            query = fb,
                            mediaType = targetMediaType,
                            showAdultContent = showAdult,
                            formats = formatStrings.takeIf { it.isNotEmpty() },
                            statuses = statusStrings.takeIf { it.isNotEmpty() },
                            season = state.season?.apiValue,
                            genres = aniListGenres.takeIf { it.isNotEmpty() },
                            excludedGenres = aniListExcludedGenres.takeIf { it.isNotEmpty() },
                            tags = aniListTags.takeIf { it.isNotEmpty() },
                            minYear = state.startYear,
                            maxYear = state.endYear,
                            minScore = state.minScore,
                            maxScore = state.maxScore,
                            minEpCh = state.minEpCh,
                            maxEpCh = state.maxEpCh,
                            minDuration = state.minDuration,
                            maxDuration = state.maxDuration,
                            sort = aniListSort,
                            country = aniListCountry,
                            sources = sourceStrings.takeIf { it.isNotEmpty() },
                            isAdult = state.isAdultFilter,
                            isLicensed = isDoujinFlag,
                            page = 1,
                            perPage = 24
                        )
                        if (fbPaged.results.isNotEmpty()) {
                            paged = fbPaged
                            break
                        }
                    }
                }
            }

            // Kitsu fallback â€” AniList boÅŸsa ve ilk sayfadaysa
            if (paged.results.isEmpty() && page == 1 && queryText.isNotBlank()) {
                val kitRes = runCatching<List<JikanSearchResult>> {
                    com.kitsugi.animelist.data.remote.KitsuExploreClient.searchAnime(
                        query = queryText,
                        mediaType = targetMediaType
                    )
                }.getOrElse { emptyList() }
                if (kitRes.isNotEmpty()) {
                    return@runCatching Pair(kitRes, false)
                }
            }

            Pair(paged.results, paged.hasNextPage)
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            Pair(emptyList(), false)
        }
    }

    private suspend fun executeSearchForQuery(queryText: String): List<JikanSearchResult> =
        executeSearchForPage(queryText, 1).first

    fun clearHistory() {
        clearHistory(keepPinned = false)
    }

    fun clearHistory(keepPinned: Boolean) {
        undoHistoryDismissJob?.cancel()
        _uiState.update { it.copy(lastDeletedHistoryItem = null) }
        viewModelScope.launch {
            searchHistoryRepository.clearSearchHistory(keepPinned = keepPinned)
        }
    }

    fun removeHistoryItem(item: SearchHistoryItem) {
        undoHistoryDismissJob?.cancel()
        _uiState.update { it.copy(lastDeletedHistoryItem = item) }
        viewModelScope.launch {
            searchHistoryRepository.deleteSearchQuery(item.query)
        }
        undoHistoryDismissJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5_000L)
            _uiState.update { current ->
                if (current.lastDeletedHistoryItem?.query == item.query) {
                    current.copy(lastDeletedHistoryItem = null)
                } else {
                    current
                }
            }
        }
    }

    fun undoRemoveHistoryItem() {
        val deleted = _uiState.value.lastDeletedHistoryItem ?: return
        undoHistoryDismissJob?.cancel()
        _uiState.update { it.copy(lastDeletedHistoryItem = null) }
        viewModelScope.launch {
            searchHistoryRepository.restoreSearchQuery(deleted)
        }
    }

    fun dismissUndoHistory() {
        undoHistoryDismissJob?.cancel()
        _uiState.update { it.copy(lastDeletedHistoryItem = null) }
    }

    fun togglePinHistoryItem(item: SearchHistoryItem) {
        viewModelScope.launch {
            searchHistoryRepository.togglePinSearchQuery(item)
        }
    }

    fun toggleSearchHistoryRecording() {
        val nextEnabled = !_uiState.value.searchHistoryEnabled
        viewModelScope.launch {
            settingsDataStore.setSearchHistoryEnabled(nextEnabled)
        }
    }

    fun fillQueryFromHistory(queryText: String) {
        val cleaned = queryText.trim()
        if (cleaned.isEmpty()) return
        setQuery(cleaned)
    }

    fun applyHistoryItem(item: SearchHistoryItem) {
        undoHistoryDismissJob?.cancel()
        _uiState.update {
            it.copy(
                query = item.query.trim(),
                lastDeletedHistoryItem = null
            )
        }
        search(resetPage = true)
    }

    fun applyHistoryItemWithSource(item: SearchHistoryItem) {
        undoHistoryDismissJob?.cancel()
        saveCurrentStateToCache()
        val targetEngine = item.engine
        val scopes = targetEngine.availableScopes()
        val targetScope = if (item.scope in scopes) item.scope else scopes.first()

        val mappedPlatform = when (targetEngine) {
            SearchSourceEngine.ALL -> SearchPlatform.All
            SearchSourceEngine.ANILIST -> SearchPlatform.AniList
            SearchSourceEngine.MAL -> SearchPlatform.MAL
            SearchSourceEngine.TMDB -> SearchPlatform.TMDB
            SearchSourceEngine.SHIKIMORI -> SearchPlatform.Shikimori
            SearchSourceEngine.KITSU -> SearchPlatform.Kitsu
            SearchSourceEngine.SIMKL -> SearchPlatform.Simkl
            SearchSourceEngine.BANGUMI -> SearchPlatform.Bangumi
        }
        val mappedMediaType = when (targetScope) {
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> MediaType.Manga
            SearchScope.MOVIE -> MediaType.Movie
            SearchScope.TV -> MediaType.TvShow
            else -> MediaType.Anime
        }
        val mappedTab = when {
            targetScope == SearchScope.CHARACTER -> KitsugiSearchTab.Character
            targetScope == SearchScope.STAFF -> KitsugiSearchTab.Staff
            targetEngine == SearchSourceEngine.ALL -> KitsugiSearchTab.All
            targetEngine == SearchSourceEngine.ANILIST &&
                targetScope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL) -> KitsugiSearchTab.Manga
            targetEngine == SearchSourceEngine.ANILIST -> KitsugiSearchTab.Anime
            targetEngine == SearchSourceEngine.MAL -> KitsugiSearchTab.MAL
            targetEngine == SearchSourceEngine.TMDB -> KitsugiSearchTab.TMDB
            targetEngine == SearchSourceEngine.SHIKIMORI -> KitsugiSearchTab.Shikimori
            targetEngine == SearchSourceEngine.KITSU -> KitsugiSearchTab.Kitsu
            targetEngine == SearchSourceEngine.SIMKL -> KitsugiSearchTab.Simkl
            targetEngine == SearchSourceEngine.BANGUMI -> KitsugiSearchTab.Bangumi
            else -> KitsugiSearchTab.All
        }

        _uiState.update {
            it.copy(
                query = item.query.trim(),
                selectedEngine = targetEngine,
                selectedScope = targetScope,
                selectedPlatform = mappedPlatform,
                selectedMediaType = mappedMediaType,
                currentTab = mappedTab,
                page = 1,
                hasNextPage = true,
                lastDeletedHistoryItem = null
            )
        }
        persistSearchSelection()
        search(resetPage = true)
    }

    fun clearResults() {
        searchGeneration.incrementAndGet()
        searchJob?.cancel()
        _uiState.update { 
            it.copy(
                results = emptyList(),
                multiResults = MultiPlatformResults(),
                hasSearched = false,
                errorMessage = null
            )
        }
        saveCurrentStateToCache()
    }

    /**
     * Arama Ã§ubuÄŸundaki X butonuna basÄ±ldÄ±ÄŸÄ±nda Ã§aÄŸrÄ±lÄ±r.
     * Hem sorgu metnini hem arama sonuÃ§larÄ±nÄ± sÄ±fÄ±rlar â†’ geÃ§miÅŸ gÃ¶rÃ¼nÃ¼mÃ¼ne dÃ¶nÃ¼ÅŸ.
     */
    fun clearQuery() {
        searchGeneration.incrementAndGet()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                multiResults = MultiPlatformResults(),
                hasSearched = false,
                errorMessage = null
            )
        }
        saveCurrentStateToCache()
    }

    /**
     * Bir eklenti seÃ§ildiÄŸinde Ã§aÄŸrÄ±lÄ±r.
     * [apiName] null ise ve [keepPlatformCs3] true ise, platform CS3'te (Eklentiler) kalÄ±r ama tÃ¼m eklentiler aranÄ±r.
     * [apiName] null ise ve [keepPlatformCs3] false ise, platform All (TÃ¼mÃ¼) olur.
     */
    fun setSelectedPlugin(apiName: String?, keepPlatformCs3: Boolean = false) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val targetPlatform = if (keepPlatformCs3 || apiName != null) SearchPlatform.CS3 else SearchPlatform.All
        val cached = loadStateFromCache(_uiState.value.currentTab, targetPlatform, _uiState.value.selectedMediaType)
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query
        _uiState.update {
            it.copy(
                selectedPluginApiName = apiName,
                selectedPlatform = targetPlatform,
                query = effectiveQuery,
                results = if (currentQuery.isNotBlank() && cached.results.isEmpty()) it.results else cached.results,
                hasSearched = cached.hasSearched || (currentQuery.isNotBlank() && it.hasSearched),
                errorMessage = cached.errorMessage
            )
        }
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }
}
