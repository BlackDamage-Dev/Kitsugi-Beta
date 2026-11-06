package com.kitsugi.animelist.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.R
import com.kitsugi.animelist.LocaleCache
import com.kitsugi.animelist.data.manga.CanonicalMangaResolver
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSearchAlternates
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.MangaSourceResult
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import com.kitsugi.animelist.data.manga.stableSourceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// ─── ViewModel ────────────────────────────────────────────────────────────────

class MangaBrowseViewModel(
    private val repository: MangaSourceRepository,
    /** Hata metinleri için uygulama bağlamı (dil değişince doğru dilde gelir). */
    private val appContext: android.content.Context? = null,
) : ViewModel() {

    private fun errorText(resId: Int): String =
        appContext?.getString(resId) ?: ""

    data class UiState(
        val sources: List<MangaSource> = emptyList(),
        val sourceStates: List<MangaSourceFetchState> = emptyList(),
        val selectedSourceFilter: MangaSource? = null,
        val popularMangas: List<MangaDetails> = emptyList(),
        val isLoadingPopular: Boolean = false,
        val popularError: String? = null,
        val popularErrorCode: String? = null,
        val hasNextPage: Boolean = false,
        val sourceFilterSelections: Map<Int, Int>? = null,
        val currentPage: Int = 1,
        val searchQuery: String = "",
        /** Aramada denenen tüm başlıklar (çok dilli arama). */
        val searchTitles: List<String> = emptyList()
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val paginationJobs = mutableListOf<Job>()

    /** Otomatik "daha fazla yükle" çağrısının aynı sayfayı tekrar istemesini önler. */
    private var lastAutoLoadKey: String? = null

    var lastInitialQuery: String? = null
    var lastInitialSourceKey: String? = null

    /**
     * Son "Oku" aramasının yedek ad listesi.
     * Aynı sorgu ama farklı dil sırası (ör. İngilizce → Orijinal ad) yeni bir
     * arama sayılır; ekran bu sayede tekrar arar.
     */
    var lastSearchFingerprint: List<String> = emptyList()

    /**
     * Eklenti Portalı'ndan tek kaynakla açılış: eşleşen kaynağı seçip popüler
     * listesini yükler. Kaynak henüz bellekte yoksa false döner (ekran, kaynak
     * listesi gelince yeniden dener).
     */
    fun applyInitialSource(key: String): Boolean {
        val target = _ui.value.sources.firstOrNull { it.stableSourceKey() == key } ?: return false
        selectSourceFilter(target)
        return true
    }

    fun reset() {
        lastInitialQuery = null
        lastInitialSourceKey = null
        lastSearchFingerprint = emptyList()
        lastAutoLoadKey = null
        alternatesUsed = emptyList()
        paginationJobs.forEach { it.cancel() }
        paginationJobs.clear()
        searchJob?.cancel()
        loadJob?.cancel()
        // Kaynakları sıfırla ama listede tut — refreshSources tekrar dolduracak.
        val available = orderSourcesForLanguage(repository.getAvailableSources())
        _ui.update {
            UiState(
                sources = available,
                selectedSourceFilter = available.firstOrNull()
            )
        }
        if (available.isNotEmpty()) fetchPopularMangas(1)
    }

    init { refreshSources() }

    fun refreshSources() {
        lastAutoLoadKey = null
        val available = orderSourcesForLanguage(repository.getAvailableSources())
        _ui.update { s ->
            // Silinmiş / devre dışı bırakılmış bir kaynak seçili kalmasın.
            val selected = s.selectedSourceFilter?.takeIf { old ->
                available.any { it.stableSourceKey() == old.stableSourceKey() }
            } ?: available.firstOrNull()
            s.copy(sources = available, selectedSourceFilter = selected)
        }
        if (_ui.value.searchQuery.isBlank()) fetchPopularMangas(1)
    }

    fun selectSourceFilter(source: MangaSource?) {
        lastAutoLoadKey = null
        _ui.update { it.copy(selectedSourceFilter = source, sourceFilterSelections = null) }
        if (_ui.value.searchQuery.isBlank() && source != null) {
            _ui.update { it.copy(popularMangas = emptyList(), currentPage = 1, hasNextPage = false, popularError = null) }
            fetchPopularMangas(1)
        }
    }

    fun fetchPopularMangas(page: Int = 1) {
        val src = _ui.value.selectedSourceFilter ?: _ui.value.sources.firstOrNull() ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(isLoadingPopular = true, popularError = null, popularErrorCode = null) }
            try {
                val selections = _ui.value.sourceFilterSelections
                val result = repository.fetchBrowsePage(src, page, selections)
                _ui.update { s ->
                    s.copy(
                        popularMangas = if (page == 1) result.mangas else s.popularMangas + result.mangas,
                        isLoadingPopular = false,
                        popularError = null,
                        popularErrorCode = null,
                        hasNextPage = result.hasNextPage,
                        currentPage = page
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update {
                    it.copy(
                        isLoadingPopular = false,
                        popularError = e.localizedMessage ?: e.message ?: "Liste yüklenemedi",
                        popularErrorCode = SourceFailureClassifier.classifyCode(e),
                        hasNextPage = false
                    )
                }
            }
        }
    }

    fun applySourceFilters(selections: Map<Int, Int>) {
        if (_ui.value.selectedSourceFilter == null || _ui.value.searchQuery.isNotBlank()) return
        _ui.update { it.copy(sourceFilterSelections = selections.toMap(), popularMangas = emptyList(), currentPage = 1, hasNextPage = false, popularError = null) }
        fetchPopularMangas(1)
    }

    fun search(query: String, alternates: List<String> = emptyList()) {
        lastAutoLoadKey = null
        loadJob?.cancel()
        alternatesUsed = alternates
        _ui.update { it.copy(searchQuery = query) }
        paginationJobs.forEach { it.cancel() }
        paginationJobs.clear()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) {
                delay(300)
                parallelSearch(query)
            } else {
                _ui.update { it.copy(sourceStates = emptyList()) }
                fetchPopularMangas(1)
            }
        }
    }

    private val searchGate = Semaphore(6)

    /** Animeden gelen ek başlıklar (romaji / İngilizce / Japonca). */
    private var alternatesUsed: List<String> = emptyList()

    // ─── Dil tercihi ─────────────────────────────────────────────────────────
    /** Uygulama içinde seçilen dil (yoksa sistem dili). */
    private val preferredLanguage: String
        get() {
            val tag = LocaleCache.localeTag
            val code = tag.takeIf { it.isNotBlank() && it != LocaleCache.UNSET }
            return (code ?: java.util.Locale.getDefault().language)
                .substringBefore('-')
                .lowercase()
        }

    /**
     * Kaynakları uygulama diline göre öne alır.
     *
     * Kararlı sıralama kullanıldığı için aynı gruptaki kaynakların mevcut
     * öncelik/sağlık sırası korunur — sadece "benim dilim" olanlar başa geçer.
     */
    private fun orderSourcesForLanguage(sources: List<MangaSource>): List<MangaSource> {
        val lang = preferredLanguage
        if (lang.isBlank()) return sources
        return sources.sortedByDescending { if (sourceMatchesLanguage(it, lang)) 1 else 0 }
    }

    private fun sourceMatchesLanguage(source: MangaSource, lang: String): Boolean =
        source.lang.trim().lowercase()
            .split(',', '/', ';', '|')
            .map { it.trim().substringBefore('-') }
            .any { it == lang }

    /**
     * Bir kaynakta aramayı **çok dilli** dener.
     *
     * 1. Kullanıcının yazdığı başlık,
     * 2. kanonik çekirdek/ASCII varyant,
     * 3. animeden gelen diğer başlıklar (romaji / İngilizce / Japonca / yerel).
     *
     * Kaynak hangi dilde kayıtlı olursa olsun eser bulunur. Sonuç bulunduğunda
     * hangi sorgunun işe yaradığı da döner — sayfalama ve puanlama bunu kullanır.
     */
    private suspend fun searchSourceWithFallback(
        source: MangaSource,
        query: String,
        page: Int = 1,
        alternates: List<String> = emptyList(),
    ): SourceSearchOutcome {
        val initial = source.fetchSearchManga(page, query)
        if (initial.mangas.isNotEmpty() || page != 1) {
            return SourceSearchOutcome(initial, query, usedAlternateTitle = false, attempts = 1)
        }

        val canonical = CanonicalMangaResolver.resolve(query)
        val candidates = buildList {
            if (canonical.core.isNotBlank() && !canonical.core.equals(query, ignoreCase = true)) {
                add(canonical.core)
            }
            if (canonical.ascii.isNotBlank() && !canonical.ascii.equals(query, ignoreCase = true)) {
                add(canonical.ascii)
            }
            addAll(MangaSearchAlternates.build(query, alternates, preferredLanguage))
        }
            .map { it.trim() }
            .filter { it.length >= 2 && !it.equals(query, ignoreCase = true) }
            .distinctBy { CanonicalMangaResolver.resolve(it).compact.ifBlank { it.lowercase() } }
            .take(MangaSearchAlternates.MAX_TITLE_ATTEMPTS)

        var attempts = 1
        candidates.forEach { candidate ->
            attempts++
            val result = try {
                source.fetchSearchManga(1, candidate)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            if (result != null && result.mangas.isNotEmpty()) {
                return SourceSearchOutcome(result, candidate, usedAlternateTitle = true, attempts = attempts)
            }
        }
        return SourceSearchOutcome(initial, query, usedAlternateTitle = false, attempts = attempts)
    }

    private fun filterResultsTwoPass(source: MangaSource, query: String, mangas: List<MangaDetails>): List<MangaDetails> {
        // The site may match aliases that do not resemble its display title.
        if (source.pkgName.endsWith(".mangatr", ignoreCase = true)) return mangas
        val strict = repository.postProcessSearchResults(source, query, mangas, relaxScoring = false)
        return if (strict.isNotEmpty()) {
            strict
        } else {
            repository.postProcessSearchResults(source, query, mangas, relaxScoring = true)
                .filterNot { manga ->
                    val lower = manga.title.lowercase()
                    !query.contains("dj", ignoreCase = true) &&
                    !query.contains("doujin", ignoreCase = true) &&
                    (lower.contains("dj") || lower.contains("doujinshi") || lower.contains("fan comic"))
                }
        }
    }

    private suspend fun parallelSearch(query: String) {
        val alternates = alternatesUsed
        val titles = MangaSearchAlternates.build(query, alternates, preferredLanguage)
        val sources = orderSourcesForLanguage(
            repository.getSearchCandidateSources(includeTrustedFallbacks = true)
        )
        _ui.update {
            it.copy(
                sourceStates = sources.map { s -> MangaSourceFetchState(s, isLoading = true) },
                selectedSourceFilter = null,
                searchTitles = titles,
            )
        }
        supervisorScope {
            sources.forEach { src ->
                launch { searchOneSource(src, query, alternates) }
            }
        }
    }

    private suspend fun searchOneSource(
        src: MangaSource,
        query: String,
        alternates: List<String> = alternatesUsed,
    ) {
        searchGate.withPermit {
            try {
                val outcome = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    withContext(Dispatchers.IO) {
                        searchSourceWithFallback(src, query, 1, alternates)
                    }
                } ?: throw java.io.IOException(errorText(R.string.manga_error_search_timeout).ifBlank { "Kaynak araması zaman aşımına uğradı" })
                repository.recordSearchSuccess(src)

                // Sonuç, farklı bir dildeki başlıkla geldiyse başlık
                // benzerliği düşük çıkar; bu durumda kaynağa güvenilir.
                val strict = filterResultsTwoPass(src, outcome.usedQuery, outcome.result.mangas)
                val accepted = when {
                    strict.isNotEmpty() -> strict
                    outcome.usedAlternateTitle -> outcome.result.mangas.distinctBy { it.url }
                    else -> emptyList()
                }
                patchState(
                    key = src.stableSourceKey(),
                    loading = false,
                    mangas = accepted,
                    error = null,
                    page = 1,
                    hasNext = outcome.result.hasNextPage,
                    errorCode = null,
                    resolvedQuery = outcome.usedQuery,
                    titleAttempts = outcome.attempts,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                repository.recordSearchFailure(src, e)
                patchState(
                    key = src.stableSourceKey(),
                    loading = false,
                    mangas = emptyList(),
                    error = e.localizedMessage ?: e.message ?: errorText(R.string.manga_error_generic).ifBlank { "search_error" },
                    page = 1,
                    hasNext = false,
                    errorCode = SourceFailureClassifier.classifyCode(e),
                )
            }
        }
    }

    private fun patchState(
        key: String,
        loading: Boolean,
        mangas: List<MangaDetails>,
        error: String?,
        page: Int = 1,
        hasNext: Boolean = true,
        errorCode: String? = null,
        resolvedQuery: String? = null,
        titleAttempts: Int = 0,
    ) {
        _ui.update { s ->
            s.copy(
                sourceStates = s.sourceStates.map {
                    if (it.source.stableSourceKey() == key) {
                        it.copy(
                            isLoading = loading,
                            mangas = mangas,
                            error = error,
                            errorCode = if (error == null) null else (errorCode ?: it.errorCode),
                            currentPage = page,
                            hasNextPage = hasNext,
                            resolvedQuery = resolvedQuery ?: it.resolvedQuery,
                            titleAttempts = if (titleAttempts > 0) titleAttempts else it.titleAttempts,
                        )
                    } else it
                }
            )
        }
    }

    fun retrySearch() {
        val state = _ui.value
        val query = state.searchQuery
        if (query.isBlank()) return
        val targets = retrySearchTargets(state.sourceStates, state.sources, state.selectedSourceFilter)
        if (targets.isEmpty()) return
        searchJob?.cancel()
        paginationJobs.forEach { it.cancel() }
        paginationJobs.clear()
        val targetKeys = targets.map { it.stableSourceKey() }.toSet()
        _ui.update { current ->
            val existing = current.sourceStates.map {
                if (it.source.stableSourceKey() in targetKeys) it.copy(isLoading = true, error = null, errorCode = null) else it
            }
            val existingKeys = existing.map { it.source.stableSourceKey() }.toSet()
            current.copy(sourceStates = existing + targets.filter { it.stableSourceKey() !in existingKeys }.map { MangaSourceFetchState(it, isLoading = true) })
        }
        searchJob = viewModelScope.launch {
            supervisorScope {
                targets.forEach { src ->
                    launch { searchOneSource(src, query, alternatesUsed) }
                }
            }
        }
    }

    fun retryBrowsePage() {
        val state = _ui.value
        if (state.searchQuery.isBlank() && !state.isLoadingPopular) {
            fetchPopularMangas(if (state.popularMangas.isEmpty()) 1 else state.currentPage + 1)
        }
    }

    /** Retries only the source whose session was updated, not every installed extension. */
    fun retrySource(source: MangaSource) {
        val query = _ui.value.searchQuery
        if (query.isBlank()) {
            selectSourceFilter(source)
            return
        }
        paginationJobs.removeAll { it.isCompleted }
        paginationJobs += viewModelScope.launch {
            _ui.update { state ->
                state.copy(sourceStates = state.sourceStates.map {
                    if (it.source.stableSourceKey() == source.stableSourceKey()) {
                        it.copy(isLoading = true, error = null, errorCode = null)
                    } else it
                })
            }
            searchOneSource(source, query)
        }
    }

    fun loadNextPage() {
        val state = _ui.value
        if (state.searchQuery.isBlank()) {
            if (!state.hasNextPage || state.isLoadingPopular) return
            val page = state.currentPage + 1
            val key = "popular|${state.selectedSourceFilter?.stableSourceKey() ?: "ALL"}|$page"
            if (key == lastAutoLoadKey) return
            lastAutoLoadKey = key
            fetchPopularMangas(page)
        } else {
            val candidates = state.sourceStates.filter { fetchState ->
                fetchState.hasNextPage && !fetchState.isLoading &&
                    (state.selectedSourceFilter == null ||
                        fetchState.source.stableSourceKey() == state.selectedSourceFilter?.stableSourceKey())
            }
            if (candidates.isEmpty()) return
            val key = "search|${state.searchQuery}|" + candidates.joinToString(",") {
                "${it.source.stableSourceKey()}:${it.currentPage + 1}"
            }
            if (key == lastAutoLoadKey) return
            lastAutoLoadKey = key
            candidates.forEach { loadNextPage(it.source) }
        }
    }

    fun loadNextPage(source: MangaSource) {
        val state = _ui.value.sourceStates.firstOrNull { it.source.stableSourceKey() == source.stableSourceKey() } ?: return
        if (state.isLoading || !state.hasNextPage) return

        val page = state.currentPage + 1
        val query = state.resolvedQuery ?: _ui.value.searchQuery
        val usedAlternateTitle = state.resolvedQuery != null && !state.resolvedQuery.equals(_ui.value.searchQuery, ignoreCase = true)

        val job = viewModelScope.launch {
            _ui.update { s ->
                s.copy(sourceStates = s.sourceStates.map {
                    if (it.source.stableSourceKey() == source.stableSourceKey()) it.copy(isLoading = true) else it
                })
            }
            try {
                val outcome = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    withContext(Dispatchers.IO) { searchSourceWithFallback(source, query, page) }
                } ?: throw java.io.IOException(errorText(R.string.manga_error_search_timeout).ifBlank { "search_timeout" })
                repository.recordSearchSuccess(source)
                val strict = filterResultsTwoPass(source, outcome.usedQuery, outcome.result.mangas)
                val matched = when {
                    strict.isNotEmpty() -> strict
                    usedAlternateTitle || outcome.usedAlternateTitle ->
                        outcome.result.mangas.distinctBy { it.url }
                    else -> emptyList()
                }
                _ui.update { s ->
                    s.copy(sourceStates = s.sourceStates.map {
                        if (it.source.stableSourceKey() == source.stableSourceKey()) {
                            it.copy(
                                isLoading = false,
                                mangas = (it.mangas + matched).distinctBy { m -> m.url },
                                currentPage = page,
                                hasNextPage = outcome.result.hasNextPage && matched.isNotEmpty()
                            )
                        } else it
                    })
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                repository.recordSearchFailure(source, e)
                _ui.update { s ->
                    s.copy(sourceStates = s.sourceStates.map {
                        if (it.source.stableSourceKey() == source.stableSourceKey()) {
                            it.copy(isLoading = false, error = e.message ?: errorText(R.string.manga_error_generic).ifBlank { "error" }, errorCode = SourceFailureClassifier.classifyCode(e), hasNextPage = false)
                        } else it
                    })
                }
            }
        }
        paginationJobs.add(job)
    }

    class Factory(
        private val repository: MangaSourceRepository,
        private val appContext: android.content.Context? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaBrowseViewModel(repository, appContext) as T
    }
}

/** Bir kaynakta yapılan çok dilli aramanın sonucu. */
private data class SourceSearchOutcome(
    val result: MangaSourceResult,
    val usedQuery: String,
    val usedAlternateTitle: Boolean,
    val attempts: Int,
)
