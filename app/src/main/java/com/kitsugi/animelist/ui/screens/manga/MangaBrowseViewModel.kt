package com.kitsugi.animelist.ui.screens.manga

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.manga.CanonicalMangaResolver
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSearchPlan
import com.kitsugi.animelist.data.manga.MangaSearchTitleMode
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.MangaSourceResult
import com.kitsugi.animelist.data.manga.MangaTitleVariantResolver
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import com.kitsugi.animelist.data.manga.stableSourceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

// ─── Sorting ──────────────────────────────────────────────────────────────────

/** Manga Keşfet sıralama ölçütü. */
enum class MangaSortMode {
    /** Sorguya benzerlik + kaynak güvenilirliği (varsayılan). */
    RELEVANCE,

    /** Başlık A→Z. */
    TITLE,
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class MangaBrowseViewModel(
    private val repository: MangaSourceRepository,
    private val appContext: Context? = null,
) : ViewModel() {

    companion object {
        /** "Tümü" popüler görünümünde birleştirilecek en fazla kaynak sayısı. */
        private const val POPULAR_SOURCE_LIMIT = 3

        /**
         * Bir kaynağın TÜM ad varyantlarını denemesi için üst sınır. Ad sayısı arttıkça
         * toplam süre bir miktar uzar; 45 sn, yavaş eklentilerde bile takılı kalmadan
         * sonuç döndürmeyi garanti eder.
         */
        private const val SEARCH_TIMEOUT_MS = 45_000L
    }

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
        val searchTitles: List<String> = emptyList(),
        /** Aktif arama modu: yerel/çevrilmiş ad mı, orijinal ad mı? */
        val titleMode: MangaSearchTitleMode = MangaSearchTitleMode.LOCALIZED,
        /** Aynı eser için çözümlenen adlar (yerel + orijinal). */
        val titlePlan: MangaSearchPlan? = null,
        /** Başlık listesi çözümleniyor mu? (arama başlamadan önce kısa süre) */
        val isResolvingTitles: Boolean = false,
        val sortMode: MangaSortMode = MangaSortMode.RELEVANCE,
    ) {
        /** Arama tamamlandı mı? (tüm kaynaklar yanıt verdi) */
        val isSearchSettled: Boolean
            get() = sourceStates.isNotEmpty() && sourceStates.none { it.isLoading }

        /** Toplam benzersiz sonuç sayısı — "Tümü" chip'i ile aynı sayı. */
        val totalResultCount: Int
            get() = sourceStates.sumOf { it.mangas.size }

        /** Denenen toplam başlık varyantı (bilgi satırı için). */
        val triedTitleCount: Int
            get() = sourceStates.maxOfOrNull { it.triedTitleCount } ?: titlePlan?.variants?.size ?: 0
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val paginationJobs = mutableListOf<Job>()

    var lastInitialQuery: String? = null
    var lastInitialSourceKey: String? = null
    var lastSearchFingerprint: List<String> = emptyList()

    /**
     * Son uygulanan başlık modu. Ekran yeniden açıldığında (`reset()` sonrası) mod
     * korunur; kullanıcı "Yerel ad" modunu seçtiyse geri döndüğünde de o mod açılır.
     */
    private var persistedTitleMode: MangaSearchTitleMode = MangaSearchTitleMode.LOCALIZED
    private var persistedSortMode: MangaSortMode = MangaSortMode.RELEVANCE

    fun reset() {
        lastInitialQuery = null
        lastInitialSourceKey = null
        lastSearchFingerprint = emptyList()
        paginationJobs.forEach { it.cancel() }
        paginationJobs.clear()
        searchJob?.cancel()
        loadJob?.cancel()
        val available = repository.getAvailableSources()
        persistedTitleMode = _ui.value.titleMode
        persistedSortMode = _ui.value.sortMode
        // DİKKAT: kaynak filtresi BİLEREK null bırakılır ("Tümü"). Eskiden ilk kaynak
        // seçili geliyordu; kullanıcı için bu "kendiliğinden bir eklentiye tıklanmış"
        // gibi görünüyordu.
        _ui.update {
            UiState(
                sources = available,
                selectedSourceFilter = null,
                titleMode = persistedTitleMode,
                sortMode = persistedSortMode,
            )
        }
        if (available.isNotEmpty()) fetchPopularMangas(1)
    }

    init { refreshSources() }

    fun refreshSources() {
        val available = repository.getAvailableSources()
        // Mevcut seçim korunur; seçim yoksa "Tümü" olarak kalır.
        _ui.update { s -> s.copy(sources = available) }
        if (_ui.value.searchQuery.isBlank()) fetchPopularMangas(1)
    }

    /**
     * Eklenti Portalı'ndan tek kaynakla açılış: eşleşen kaynağı seçip popüler
     * listesini yükler. Kaynak henüz bellekte yoksa false döner.
     */
    fun applyInitialSource(key: String): Boolean {
        val target = _ui.value.sources.firstOrNull { it.stableSourceKey() == key } ?: return false
        selectSourceFilter(target)
        return true
    }

    /**
     * "Tümü" (null) veya belirli bir kaynak filtresi.
     *
     * NOT: Arama sırasında kaynak filtresi değişse bile **yeniden arama yapılmaz**;
     * sonuçlar zaten tüm kaynaklardan gelmiş durumda, filtre yalnızca gösterimi değiştirir.
     * Bu, "Tümü"den çıkma / kart açıp geri dönünce filtrenin kayması sorununu da önler.
     */
    fun selectSourceFilter(source: MangaSource?) {
        val state = _ui.value
        val sameSelection = when {
            source == null -> state.selectedSourceFilter == null
            state.selectedSourceFilter == null -> false
            else -> state.selectedSourceFilter.stableSourceKey() == source.stableSourceKey()
        }
        if (sameSelection) return

        _ui.update { it.copy(selectedSourceFilter = source) }
        // Popüler görünümde kaynak değişimi listeyi yeniden doldurur. Arama sırasında
        // ise hiçbir ağ isteği yapılmaz: filtre yalnızca görünümü süzer.
        if (state.searchQuery.isBlank()) {
            _ui.update { it.copy(popularMangas = emptyList(), currentPage = 1) }
            fetchPopularMangas(1)
        }
    }

    /** Yerel/çevrilmiş ad ↔ orijinal ad arasında geçiş yapar ve aramayı yeniler. */
    fun setTitleMode(mode: MangaSearchTitleMode) {
        if (_ui.value.titleMode == mode) return
        persistedTitleMode = mode
        _ui.update { it.copy(titleMode = mode) }
        val query = _ui.value.searchQuery
        if (query.isNotBlank()) search(query)
    }

    fun setSortMode(mode: MangaSortMode) {
        persistedSortMode = mode
        _ui.update { it.copy(sortMode = mode) }
    }

    /**
     * Popüler (ana) liste.
     *
     * "Tümü" seçiliyken (filtre null) en iyi 3 kaynağın popüler listesi birleştirilir;
     * böylece mobil keşif ekranı da video keşif ekranı gibi gerçek bir "Tümü"
     * görünümü sunar — sahte şekilde ilk eklentiye "tıklanmış" görünmez.
     */
    fun fetchPopularMangas(page: Int = 1) {
        val state = _ui.value
        val explicit = state.selectedSourceFilter
        val sources = when {
            explicit != null -> listOf(explicit)
            else -> repository.getAvailableSources().take(POPULAR_SOURCE_LIMIT)
        }
        if (sources.isEmpty()) return

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(isLoadingPopular = true, popularError = null, popularErrorCode = null) }
            try {
                val results = supervisorScope {
                    sources.map { src ->
                        async(Dispatchers.IO) {
                            src to runCatching {
                                if (explicit != null && state.sourceFilterSelections != null) {
                                    repository.fetchBrowsePage(src, page, state.sourceFilterSelections)
                                } else {
                                    repository.fetchPopular(src, page)
                                }
                            }.getOrElse { e ->
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                repository.recordPopularFailure(src, e)
                                null
                            }
                        }
                    }.awaitAll()
                }

                val merged = LinkedHashMap<String, MangaDetails>()
                var anyNext = false
                results.forEach { (src, result) ->
                    if (result == null) return@forEach
                    if (result.hasNextPage) anyNext = true
                    result.mangas.forEach { manga ->
                        val key = "${src.stableSourceKey()}|${manga.url}"
                        merged.putIfAbsent(key, manga)
                    }
                }

                _ui.update { s ->
                    s.copy(
                        popularMangas = if (page == 1) merged.values.toList() else s.popularMangas + merged.values.toList(),
                        isLoadingPopular = false,
                        popularError = null,
                        popularErrorCode = null,
                        hasNextPage = anyNext,
                        currentPage = page,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update {
                    it.copy(
                        isLoadingPopular = false,
                        popularError = e.localizedMessage ?: e.message ?: "Liste yüklenemedi",
                        popularErrorCode = SourceFailureClassifier.classifyCode(e),
                        hasNextPage = false,
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

    fun clearSourceFilters() {
        if (_ui.value.selectedSourceFilter == null) return
        _ui.update { it.copy(sourceFilterSelections = null, popularMangas = emptyList(), currentPage = 1, hasNextPage = false, popularError = null) }
        fetchPopularMangas(1)
    }

    fun retryBrowsePage() {
        val state = _ui.value
        if (state.searchQuery.isBlank() && !state.isLoadingPopular) {
            fetchPopularMangas(if (state.popularMangas.isEmpty()) 1 else state.currentPage + 1)
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
            val mode = _ui.value.titleMode
            val plan = state.titlePlan ?: resolvePlan(query, mode, targets)
            supervisorScope {
                targets.forEach { src ->
                    launch {
                        searchGate.withPermit {
                            var usedTitle: String? = null
                            var triedCount = plan.variants.size
                            try {
                                val outcome = kotlinx.coroutines.withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                                    withContext(Dispatchers.IO) {
                                        searchSourceWithFallback(src, plan, 1) { usedTitle = it }
                                    }
                                } ?: throw java.io.IOException("Kaynak araması zaman aşımına uğradı")

                                val (result, title) = outcome
                                repository.recordSearchSuccess(src)
                                val matched = filterResultsTwoPass(src, plan, result.mangas)
                                patchState(
                                    src.stableSourceKey(),
                                    loading = false,
                                    mangas = matched,
                                    error = null,
                                    page = 1,
                                    hasNext = result.hasNextPage,
                                    usedTitle = title,
                                    triedCount = triedCount,
                                )
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                repository.recordSearchFailure(src, e)
                                patchState(
                                    src.stableSourceKey(),
                                    loading = false,
                                    mangas = emptyList(),
                                    error = e.message ?: "Hata",
                                    page = 1,
                                    hasNext = false,
                                    usedTitle = usedTitle,
                                    triedCount = triedCount,
                                    errorCode = SourceFailureClassifier.classifyCode(e),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun search(query: String, alternates: List<String> = emptyList()) {
        loadJob?.cancel()
        _ui.update { it.copy(searchQuery = query, searchTitles = alternates) }
        paginationJobs.forEach { it.cancel() }
        paginationJobs.clear()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) {
                delay(300)
                parallelSearch(query)
            } else {
                _ui.update { it.copy(sourceStates = emptyList(), titlePlan = null, isResolvingTitles = false) }
                fetchPopularMangas(1)
            }
        }
    }

    private val searchGate = Semaphore(6)

    /**
     * Kaynağı sırayla verilen adlarla arar; ilk **dolu** sonuçta durur.
     *
     * Sıra [MangaSearchPlan.variants] sırasıdır: "Yerel ad" modunda önce çevrilmiş/
     * Türkçe ad, sonra sorgunun kendisi; "Orijinal ad" modunda önce Japonca/Çince/Korece
     * ad. Eklenti Türkçe tuttuğu için yerel ad genelde ilk denemede tutar; tutmazsa
     * orijinal adla da denenir. Böylece buton "gerçekten" çalışır.
     */
    private suspend fun searchSourceWithFallback(
        source: MangaSource,
        plan: MangaSearchPlan,
        page: Int = 1,
        onTitleTried: (String) -> Unit = {},
    ): Pair<MangaSourceResult, String?> {
        val variants = plan.safeVariants()
        var lastResult: MangaSourceResult? = null
        var lastTitle: String? = null
        var firstError: Throwable? = null
        var successCount = 0

        suspend fun tryVariant(title: String, pageForCall: Int): MangaSourceResult? {
            onTitleTried(title)
            return try {
                source.fetchSearchManga(pageForCall, title).also { successCount++ }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (firstError == null) firstError = e
                null
            }
        }

        for (variant in variants) {
            val result = tryVariant(variant, page) ?: continue
            lastTitle = variant
            lastResult = result
            if (result.mangas.isNotEmpty()) return result to variant
            // Sıradaki adı denerken arama sayfası değişmez; sonuç boşsa devam.
        }

        // Son çare: canonical çekirdek (stop-word/noktalama temizlenmiş hâli).
        if (page == 1) {
            val fallback = CanonicalMangaResolver.resolve(plan.query)
            val extra = listOf(fallback.core, fallback.ascii)
                .map { it.trim() }
                .filter { it.length >= 2 && variants.none { v -> v.equals(it, ignoreCase = true) } }
                .distinct()
            for (candidate in extra) {
                val result = tryVariant(candidate, 1) ?: continue
                lastTitle = candidate
                lastResult = result
                if (result.mangas.isNotEmpty()) return result to candidate
            }
        }

        // Hiçbir ad denemesi tamamlanamadıysa (hepsi hata verdi) hatayı yukarı taşı:
        // kaynak sağlığı/kota sayaçları ancak böyle doğru güncellenir.
        if (successCount == 0) firstError?.let { throw it }

        return (lastResult ?: MangaSourceResult(emptyList(), hasNextPage = false)) to lastTitle
    }

    /**
     * İki geçişli eleme: sıkı skor → tutmazsa gevşek skor.
     * Kabul edilen ad listesi tüm varyantları kapsar; yerel adla aranıp orijinal adla
     * dönen sonuç da listede kalır.
     */
    private fun filterResultsTwoPass(
        source: MangaSource,
        plan: MangaSearchPlan,
        mangas: List<MangaDetails>,
    ): List<MangaDetails> {
        val accepted = plan.matchTitles
        val strict = repository.postProcessSearchResults(source, accepted, mangas, relaxScoring = false)
        return if (strict.isNotEmpty()) {
            strict
        } else {
            repository.postProcessSearchResults(source, accepted, mangas, relaxScoring = true)
                .filterNot { manga ->
                    val lower = manga.title.lowercase()
                    !plan.query.contains("dj", ignoreCase = true) &&
                        !plan.query.contains("doujin", ignoreCase = true) &&
                        (lower.contains("dj") || lower.contains("doujinshi") || lower.contains("fan comic"))
                }
        }
    }

    private suspend fun parallelSearch(query: String) {
        val mode = _ui.value.titleMode
        val sources = repository.getSearchCandidateSources(includeTrustedFallbacks = true)

        // 1) Başlık planı: aynı eserin yerel + orijinal adları (ağ/çeviri, önbellekli).
        _ui.update {
            it.copy(
                sourceStates = sources.map { s -> MangaSourceFetchState(s, isLoading = true) },
                selectedSourceFilter = null,
                isResolvingTitles = true,
            )
        }
        val plan = resolvePlan(query, mode, sources)
        // Kullanıcı bu arada yazmaya devam ettiyse eski arama iptal edilir.
        if (_ui.value.searchQuery.trim() != query.trim()) return

        _ui.update { it.copy(titlePlan = plan, isResolvingTitles = false) }

        // 2) Her kaynak kendi ad varyantlarını sırayla dener.
        supervisorScope {
            sources.forEach { src ->
                launch {
                    searchGate.withPermit {
                        var usedTitle: String? = null
                        var triedCount = plan.variants.size
                        try {
                            val outcome = kotlinx.coroutines.withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                                withContext(Dispatchers.IO) {
                                    searchSourceWithFallback(src, plan, 1) { usedTitle = it }
                                }
                            } ?: throw java.io.IOException("Kaynak araması zaman aşımına uğradı")

                            val (result, title) = outcome
                            usedTitle = title
                            triedCount = plan.variants.size
                            repository.recordSearchSuccess(src)
                            val matched = filterResultsTwoPass(src, plan, result.mangas)
                            patchState(
                                src.stableSourceKey(),
                                loading = false,
                                mangas = matched,
                                error = null,
                                page = 1,
                                hasNext = result.hasNextPage,
                                usedTitle = title,
                                triedCount = triedCount,
                            )
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            repository.recordSearchFailure(src, e)
                            patchState(
                                src.stableSourceKey(),
                                loading = false,
                                mangas = emptyList(),
                                error = e.message ?: "Hata",
                                page = 1,
                                hasNext = false,
                                usedTitle = usedTitle,
                                triedCount = triedCount,
                                errorCode = SourceFailureClassifier.classifyCode(e),
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun resolvePlan(
        query: String,
        mode: MangaSearchTitleMode,
        sources: List<MangaSource>,
    ): MangaSearchPlan {
        val context = appContext ?: return MangaSearchPlan.identity(query, mode)
        return MangaTitleVariantResolver.planFor(
            context = context,
            query = query,
            mode = mode,
            extensionLanguage = dominantSourceLanguage(sources),
        )
    }

    /**
     * Yüklü eklentilerin baskın dili ("Yerel ad" modunda çeviri hedefi).
     * TR eklentileri çoğunluktaysa `"tr"`, değilse en sık görülen dil.
     */
    private fun dominantSourceLanguage(sources: List<MangaSource>): String? =
        sources.map { it.lang.trim().lowercase() }
            .filter { it.isNotBlank() && it != "all" && it != "multi" }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    private fun patchState(
        key: String,
        loading: Boolean,
        mangas: List<MangaDetails>,
        error: String?,
        page: Int = 1,
        hasNext: Boolean = true,
        usedTitle: String? = null,
        triedCount: Int = 0,
        errorCode: String? = null,
    ) {
        _ui.update { s ->
            s.copy(
                sourceStates = s.sourceStates.map {
                    if (it.source.stableSourceKey() == key) {
                        it.copy(
                            isLoading = loading,
                            mangas = mangas,
                            error = error,
                            // Sınıflandırılmış kod (KTS-SRC-xxxx) çipte gösterilir; ham
                            // hata metni kod olarak kullanılmaz.
                            errorCode = if (error == null) null else (errorCode ?: it.errorCode),
                            currentPage = page,
                            hasNextPage = hasNext,
                            usedTitle = usedTitle,
                            triedTitleCount = maxOf(it.triedTitleCount, triedCount),
                        )
                    } else it
                }
            )
        }
    }

    /** Retries only the source whose session was updated or which encountered an error. */
    fun retrySource(source: MangaSource) {
        val query = _ui.value.searchQuery
        if (query.isBlank()) {
            selectSourceFilter(source)
            return
        }
        val plan = _ui.value.titlePlan ?: MangaSearchPlan.identity(query, _ui.value.titleMode)
        paginationJobs.removeAll { it.isCompleted }
        paginationJobs += viewModelScope.launch {
            patchState(source.stableSourceKey(), loading = true, mangas = emptyList(), error = null, errorCode = null)
            var usedTitle: String? = null
            var triedCount = plan.variants.size
            try {
                val outcome = kotlinx.coroutines.withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        searchSourceWithFallback(source, plan, 1) { usedTitle = it }
                    }
                } ?: throw java.io.IOException("Kaynak araması zaman aşımına uğradı")
                val (result, title) = outcome
                usedTitle = title
                repository.recordSearchSuccess(source)
                val matched = filterResultsTwoPass(source, plan, result.mangas)
                patchState(
                    source.stableSourceKey(),
                    loading = false,
                    mangas = matched,
                    error = null,
                    page = 1,
                    hasNext = result.hasNextPage,
                    usedTitle = title,
                    triedCount = triedCount,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                repository.recordSearchFailure(source, e)
                patchState(
                    source.stableSourceKey(),
                    loading = false,
                    mangas = emptyList(),
                    error = e.message ?: "Hata",
                    page = 1,
                    hasNext = false,
                    usedTitle = usedTitle,
                    triedCount = triedCount,
                    errorCode = SourceFailureClassifier.classifyCode(e),
                )
            }
        }
    }

    fun loadNextPage() {
        val state = _ui.value
        if (state.searchQuery.isBlank()) {
            if (state.hasNextPage && !state.isLoadingPopular) {
                fetchPopularMangas(state.currentPage + 1)
            }
        } else {
            val src = state.selectedSourceFilter
            if (src != null) {
                val fetchState = state.sourceStates.firstOrNull { it.source.stableSourceKey() == src.stableSourceKey() }
                if (fetchState != null && fetchState.hasNextPage && !fetchState.isLoading) {
                    loadMoreSearchForSource(src, fetchState.currentPage + 1)
                }
            }
        }
    }

    private fun loadMoreSearchForSource(source: MangaSource, page: Int) {
        val state = _ui.value
        val query = state.searchQuery
        if (query.isBlank()) return
        val plan = state.titlePlan ?: MangaSearchPlan.identity(query, state.titleMode)

        paginationJobs.removeAll { it.isCompleted }
        paginationJobs += viewModelScope.launch {
            _ui.update { s ->
                s.copy(sourceStates = s.sourceStates.map {
                    if (it.source.stableSourceKey() == source.stableSourceKey()) it.copy(isLoading = true) else it
                })
            }
            try {
                val outcome = kotlinx.coroutines.withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { searchSourceWithFallback(source, plan, page) }
                } ?: throw java.io.IOException("Kaynak araması zaman aşımına uğradı")

                val (result, title) = outcome
                repository.recordSearchSuccess(source)
                val matched = filterResultsTwoPass(source, plan, result.mangas)
                _ui.update { s ->
                    s.copy(sourceStates = s.sourceStates.map {
                        if (it.source.stableSourceKey() == source.stableSourceKey()) {
                            it.copy(
                                isLoading = false,
                                mangas = it.mangas + matched,
                                currentPage = page,
                                hasNextPage = result.hasNextPage,
                                usedTitle = title ?: it.usedTitle,
                                // Sayfalama sırasında da denenen ad sayısı görünür kalsın.
                                triedTitleCount = maxOf(it.triedTitleCount, plan.variants.size),
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
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Hata",
                                errorCode = SourceFailureClassifier.classifyCode(e),
                                hasNextPage = false
                            )
                        } else it
                    })
                }
            }
        }
    }

    class Factory(
        private val repository: MangaSourceRepository,
        private val appContext: Context? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaBrowseViewModel(repository, appContext) as T
    }
}
