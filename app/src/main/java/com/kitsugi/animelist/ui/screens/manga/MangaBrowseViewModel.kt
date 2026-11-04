package com.kitsugi.animelist.ui.screens.manga

import androidx.lifecycle.ViewModel
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.data.manga.CanonicalMangaResolver
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.MangaSourceResult
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

class MangaBrowseViewModel(private val repository: MangaSourceRepository) : ViewModel() {

    data class UiState(
        val sources: List<MangaSource> = emptyList(),
        val sourceStates: List<MangaSourceFetchState> = emptyList(),
        val selectedSourceFilter: MangaSource? = null,
        val popularMangas: List<MangaDetails> = emptyList(),
        val isLoadingPopular: Boolean = false,
        val popularError: String? = null,
        val hasNextPage: Boolean = false,
        val sourceFilterSelections: Map<Int, Int>? = null,
        val currentPage: Int = 1,
        val searchQuery: String = ""
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
        lastAutoLoadKey = null
        paginationJobs.forEach { it.cancel() }
        paginationJobs.clear()
        searchJob?.cancel()
        loadJob?.cancel()
        val available = repository.getAvailableSources()
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
        val available = repository.getAvailableSources()
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
            _ui.update { it.copy(isLoadingPopular = true, popularError = null) }
            try {
                val selections = _ui.value.sourceFilterSelections
                val result = repository.fetchBrowsePage(src, page, selections)
                _ui.update { s ->
                    s.copy(
                        popularMangas  = if (page == 1) result.mangas else s.popularMangas + result.mangas,
                        isLoadingPopular = false,
                        popularError   = null,
                        hasNextPage    = result.hasNextPage,
                        currentPage    = page
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update {
                    it.copy(isLoadingPopular = false, popularError = e.localizedMessage ?: e.message ?: "Liste yüklenemedi")
                }
            }
        }
    }

    fun applySourceFilters(selections: Map<Int, Int>) {
        if (_ui.value.selectedSourceFilter == null || _ui.value.searchQuery.isNotBlank()) return
        _ui.update { it.copy(sourceFilterSelections = selections.toMap(), popularMangas = emptyList(), currentPage = 1, hasNextPage = false, popularError = null) }
        fetchPopularMangas(1)
    }

    fun search(query: String) {
        lastAutoLoadKey = null
        loadJob?.cancel()
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

    private suspend fun searchSourceWithFallback(source: MangaSource, query: String, page: Int = 1): MangaSourceResult {
        val initial = source.fetchSearchManga(page, query)
        if (initial.mangas.isNotEmpty()) return initial

        // Fallback: If 0 results on page 1, try canonical core query if different from raw
        if (page == 1) {
            val canonical = CanonicalMangaResolver.resolve(query)
            val fallbackQuery = when {
                canonical.core.isNotBlank() && !canonical.core.equals(query, ignoreCase = true) -> canonical.core
                canonical.ascii.isNotBlank() && !canonical.ascii.equals(query, ignoreCase = true) -> canonical.ascii
                else -> null
            }
            if (!fallbackQuery.isNullOrBlank() && fallbackQuery.length >= 2) {
                return try { source.fetchSearchManga(1, fallbackQuery) } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    initial
                }
            }
        }
        return initial
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
        val sources = repository.getSearchCandidateSources(includeTrustedFallbacks = true)
        _ui.update { it.copy(sourceStates = sources.map { s -> MangaSourceFetchState(s, isLoading = true) }, selectedSourceFilter = null) }
        supervisorScope {
            sources.forEach { src ->
                launch { searchOneSource(src, query) }
            }
        }
    }

    private suspend fun searchOneSource(src: MangaSource, query: String) {
        searchGate.withPermit {
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    withContext(Dispatchers.IO) { searchSourceWithFallback(src, query, 1) }
                } ?: throw java.io.IOException("Kaynak araması zaman aşımına uğradı")
                repository.recordSearchSuccess(src)
                val matched = filterResultsTwoPass(src, query, result.mangas)
                patchState(src.stableSourceKey(), false, matched, null, page = 1, hasNext = result.hasNextPage)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                repository.recordSearchFailure(src, e)
                patchState(
                    src.stableSourceKey(), false, emptyList(), e.localizedMessage ?: e.message ?: e.javaClass.simpleName,
                    page = 1, hasNext = false, errorCode = SourceFailureClassifier.classifyCode(e)
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
    ) {
        _ui.update { s ->
            s.copy(sourceStates = s.sourceStates.map {
                if (it.source.stableSourceKey() == key) {
                    it.copy(
                        isLoading = loading,
                        mangas = mangas,
                        error = error,
                        // Hata kodu artık düşmüyor: eski kod `it.errorCode`'u koruduğu
                        // için ilk aramada oluşan kod hiçbir zaman UI'a ulaşmıyordu.
                        errorCode = errorCode,
                        currentPage = page,
                        hasNextPage = hasNext
                    )
                } else it
            })
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
            supervisorScope { targets.forEach { src -> launch { searchOneSource(src, query) } } }
        }
    }

    fun retryBrowsePage() {
        val state = _ui.value
        if (state.searchQuery.isBlank() && !state.isLoadingPopular) {
            fetchPopularMangas(if (state.popularMangas.isEmpty()) 1 else state.currentPage + 1)
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
            // "Tümü" seçiliyken (selectedSourceFilter == null) eskiden hiçbir
            // sayfalama yapılmıyordu — arama sadece ilk sayfada kalıyordu.
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
            candidates.forEach { loadMoreSearchForSource(it.source, it.currentPage + 1) }
        }
    }

    private fun loadMoreSearchForSource(source: MangaSource, page: Int) {
        val query = _ui.value.searchQuery
        if (query.isBlank()) return

        paginationJobs.removeAll { it.isCompleted }
        paginationJobs += viewModelScope.launch {
            _ui.update { s ->
                s.copy(sourceStates = s.sourceStates.map {
                    if (it.source.stableSourceKey() == source.stableSourceKey()) it.copy(isLoading = true) else it
                })
            }
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    withContext(Dispatchers.IO) { searchSourceWithFallback(source, query, page) }
                } ?: throw java.io.IOException("Kaynak araması zaman aşımına uğradı")
                repository.recordSearchSuccess(source)
                val matched = filterResultsTwoPass(source, query, result.mangas)
                _ui.update { s ->
                    s.copy(sourceStates = s.sourceStates.map {
                        if (it.source.stableSourceKey() == source.stableSourceKey()) {
                            it.copy(
                                isLoading = false,
                                mangas = it.mangas + matched,
                                currentPage = page,
                                hasNextPage = result.hasNextPage && matched.isNotEmpty()
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
                            it.copy(isLoading = false, error = e.message ?: "Hata", errorCode = SourceFailureClassifier.classifyCode(e), hasNextPage = false)
                        } else it
                    })
                }
            }
        }
    }

    class Factory(private val repository: MangaSourceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MangaBrowseViewModel(repository) as T
    }
}
