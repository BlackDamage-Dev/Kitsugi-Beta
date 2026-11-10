package com.kitsugi.animelist.ui.screens.manga

import kotlinx.coroutines.ensureActive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MangaChapterProgressEntity
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceStateStore
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.kitsugi.animelist.data.manga.loader.MangaCache
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import com.kitsugi.animelist.data.manga.SourceFailureClassifier

import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaReaderOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MangaReaderScreen için ViewModel.
 *
 * Sorumluluklar:
 * - Aktif bölümü ve sayfa listesini yönetmek
 * - Okuma modunu (LTR/RTL/Webtoon) kalıcı yapmak
 * - Her sayfa değişiminde Room'a ilerlemeyi kaydetmek
 * - Bir sonraki/önceki bölüme geçişi yönetmek
 */
class MangaReaderViewModel(
    private val context: Context,
    val source: MangaSource,
    val mangaDetails: MangaDetails,
    initialChapter: MangaChapter
) : ViewModel() {

    private val db  = KitsugiDatabase.getDatabase(context)
    private val dao = db.mangaChapterProgressDao()
    private val cache = MangaCache(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val sourceStateStore = MangaSourceStateStore(context)
    val loader = MangaPageLoaderV2(context, source, cache)
    val pages: StateFlow<List<MangaPage>> = loader.pages

    // F5: Debounce için Job referansı — hızlı swipe'ta Room IO flood önler
    private var progressSaveJob: kotlinx.coroutines.Job? = null
    private val preferenceWriteMutex = Mutex()
    private var pendingPreferenceWrites = 0
    private var hasAppliedAutomaticMode = false

    // ─── UI Durumu ────────────────────────────────────────────────────────────

    data class UiState(
        val currentChapter: MangaChapter,
        val chapterList: List<MangaChapter> = emptyList(),
        val currentPageIndex: Int = 0,
        val readingMode: ReadingMode = ReadingMode.RightToLeft,
        val colorFilterType: ColorFilterType = ColorFilterType.Normal,
        val fitMode: MangaFitMode = MangaFitMode.FitScreen,
        val customBrightness: Float = 1.0f,
        val readerOptions: MangaReaderOptions = MangaReaderOptions(),
        val isLoadingChapters: Boolean = false,
        val error: String? = null,
        val isLoadingPages: Boolean = false,
        val pagesError: String? = null,
        /** Sayfa yükleme hatasının manga kaynak kodu (KTS-SRC-...). */
        val pagesErrorCode: String? = null
    )

    private val _uiState = MutableStateFlow(UiState(currentChapter = initialChapter))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadChapterList()
        restoreProgress(initialChapter)
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataStore.mangaReaderPreferencesFlow.collect { persisted ->
                if (pendingPreferenceWrites > 0) return@collect
                val preferences = persisted.sanitized()
                val shouldAutoSelectWebtoon = !hasAppliedAutomaticMode &&
                    preferences.options.autoWebtoon && isWebtoonContent()
                hasAppliedAutomaticMode = true
                val mode = if (shouldAutoSelectWebtoon) ReadingMode.Webtoon else preferences.readingMode
                loader.preloadAhead = if (mode == ReadingMode.Webtoon) 3 else 2
                loader.keepBehind = 1
                _uiState.update {
                    it.copy(
                        readingMode = mode,
                        colorFilterType = preferences.colorFilter,
                        fitMode = preferences.fitMode,
                        customBrightness = preferences.brightness,
                        readerOptions = preferences.options,
                    )
                }
            }
        }
    }

    private fun isWebtoonContent(): Boolean {
        val tokens = listOf("webtoon", "manhwa", "manhua", "long strip")
        return (mangaDetails.genre + mangaDetails.source).any { value ->
            val normalized = value.lowercase()
            tokens.any(normalized::contains)
        }
    }

    // ─── Bölüm listesini yükle ────────────────────────────────────────────────

    private fun loadChapterList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChapters = true, error = null) }
            val startedAt = System.currentTimeMillis()
            try {
                val chapters = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    source.fetchChapterList(mangaDetails.url)
                }
                sourceStateStore.recordOperationSuccess(
                    source = source,
                    operation = "chapters",
                    elapsedMs = System.currentTimeMillis() - startedAt,
                )
                _uiState.update { it.copy(chapterList = chapters, isLoadingChapters = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) kotlinx.coroutines.currentCoroutineContext().ensureActive()
                sourceStateStore.recordOperationFailure(
                    source = source,
                    operation = "chapters",
                    reason = e.message,
                    statusOverride = classifySourceStatus(e),
                    elapsedMs = System.currentTimeMillis() - startedAt,
                )
                _uiState.update { it.copy(isLoadingChapters = false, error = e.message) }
            }
        }
    }

    // ─── İlerleme kaydetme ───────────────────────────────────────────────────

    /**
     * Sayfa değiştikçe Room'a anlık olarak kaydeder.
     * Bölüm tamamlandıysa isCompleted = true olarak işaretler.
     */
    // F5: Progress debounce — 500ms bekle, hızlı swipe'ta tekrar tetiklenirse iptal et
    fun onPageChanged(chapterUrl: String, pageIndex: Int, totalPages: Int) {
        if (chapterUrl != _uiState.value.currentChapter.url || totalPages <= 0) return
        val safePageIndex = pageIndex.coerceIn(0, totalPages - 1)
        _uiState.update { it.copy(currentPageIndex = safePageIndex) }
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500L)
            val isCompleted = safePageIndex >= totalPages - 1
            dao.upsert(
                MangaChapterProgressEntity(
                    chapterUrl    = chapterUrl,
                    mangaUrl      = mangaDetails.url,
                    chapterName   = _uiState.value.currentChapter.name,
                    lastPageIndex = safePageIndex,
                    totalPages    = totalPages,
                    isCompleted   = isCompleted,
                    updatedAt     = System.currentTimeMillis()
                )
            )
        }
    }

    // ─── Okuma modu değiştirme ────────────────────────────────────────────────

    fun setReadingMode(mode: ReadingMode) {
        // Update immediately; waiting for a DataStore round-trip caused visible mode reversions.
        hasAppliedAutomaticMode = true
        _uiState.update { it.copy(readingMode = mode) }
        loader.preloadAhead = if (mode == ReadingMode.Webtoon) 3 else 2
        persistPreference { settingsDataStore.setMangaReadingMode(mode.name) }
    }

    fun setColorFilter(filter: ColorFilterType) {
        _uiState.update { it.copy(colorFilterType = filter) }
        persistPreference { settingsDataStore.setMangaColorFilter(filter.name) }
    }

    fun setFitMode(fitMode: MangaFitMode) {
        _uiState.update { it.copy(fitMode = fitMode) }
        persistPreference { settingsDataStore.setMangaFitMode(fitMode.name) }
    }

    fun setCustomBrightness(brightness: Float) {
        val value = if (brightness.isFinite()) brightness.coerceIn(0.1f, 1f) else 1f
        _uiState.update { it.copy(customBrightness = value) }
        persistPreference { settingsDataStore.setMangaBrightness(value) }
    }

    fun setReaderOptions(options: MangaReaderOptions) {
        val value = options.sanitized()
        _uiState.update { it.copy(readerOptions = value) }
        persistPreference { settingsDataStore.setMangaReaderOptions(value) }
    }

    private fun persistPreference(write: suspend () -> Unit) {
        pendingPreferenceWrites++
        viewModelScope.launch {
            try {
                preferenceWriteMutex.withLock { write() }
            } finally {
                pendingPreferenceWrites--
            }
        }
    }

    fun updateReaderOptions(update: (MangaReaderOptions) -> MangaReaderOptions) {
        setReaderOptions(update(_uiState.value.readerOptions))
    }

    // ─── Bölüm gezinme ───────────────────────────────────────────────────────

    // F6: Bölüm değişiminde eski queue'yu temizle — yanlış sayfa yükleme önler
    fun goToChapter(chapter: MangaChapter) {
        progressSaveJob?.cancel()
        loader.resetQueue()
        _uiState.update { it.copy(currentChapter = chapter, currentPageIndex = 0) }
        restoreProgress(chapter)
    }

    fun goToNextChapter() {
        val list = _uiState.value.chapterList
        val current = _uiState.value.currentChapter
        val idx = list.indexOfFirst { it.url == current.url }
        if (idx <= 0) return
        if (!_uiState.value.readerOptions.skipReadChapters) {
            goToChapter(list[idx - 1])
            return
        }
        val originUrl = current.url
        viewModelScope.launch {
            for (candidateIndex in idx - 1 downTo 0) {
                val candidate = list[candidateIndex]
                val progress = dao.getProgress(candidate.url)
                if (progress?.isCompleted != true) {
                    // A second tap must not let an older async search change the new chapter.
                    if (_uiState.value.currentChapter.url == originUrl) goToChapter(candidate)
                    return@launch
                }
            }
        }
    }

    fun goToPreviousChapter() {
        val list    = _uiState.value.chapterList
        val current = _uiState.value.currentChapter
        val idx     = list.indexOfFirst { it.url == current.url }
        if (idx >= 0 && idx < list.size - 1) goToChapter(list[idx + 1])
    }

    val hasNextChapter: Boolean
        get() {
            val list = _uiState.value.chapterList
            val idx  = list.indexOfFirst { it.url == _uiState.value.currentChapter.url }
            return idx > 0
        }

    /** Sonraki bölümün adı (bölüm sonu geçiş kartında göstermek için); yoksa null. */
    val nextChapterName: String?
        get() {
            val list = _uiState.value.chapterList
            val idx  = list.indexOfFirst { it.url == _uiState.value.currentChapter.url }
            return if (idx > 0) list[idx - 1].name else null
        }

    val hasPreviousChapter: Boolean
        get() {
            val list = _uiState.value.chapterList
            val idx  = list.indexOfFirst { it.url == _uiState.value.currentChapter.url }
            return idx >= 0 && idx < list.size - 1
        }

    private var pageListJob: kotlinx.coroutines.Job? = null

    private fun restoreProgress(chapter: MangaChapter) {
        pageListJob?.cancel()
        pageListJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPages = true, pagesError = null, pagesErrorCode = null) }
            val saved = dao.getProgress(chapter.url)
            val pageIndex = if (saved != null && !saved.isCompleted) saved.lastPageIndex else 0
            _uiState.update { it.copy(currentPageIndex = pageIndex) }
            try {
                loader.loadPageList(chapter)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val restoredIndex = pageIndex.coerceIn(0, (loader.pages.value.size - 1).coerceAtLeast(0))
                _uiState.update { it.copy(currentPageIndex = restoredIndex) }
                loader.onPageChanged(restoredIndex)
                if (loader.pages.value.isEmpty()) {
                    _uiState.update { it.copy(isLoadingPages = false, pagesError = "Sayfalar yüklenemedi. İnternet bağlantınızı kontrol edip tekrar deneyin.") }
                } else {
                    _uiState.update { it.copy(isLoadingPages = false) }
                }
            } catch (e: com.kitsugi.animelist.data.manga.CaptchaRequiredException) {
                // F9: Captcha tespiti — kullanıcıya anlamlı mesaj göster
                _uiState.update {
                    it.copy(
                        isLoadingPages = false,
                        pagesError = "⚠️ ${e.sourceName}: Bu bölümü açmak için captcha doğrulaması gerekiyor. Tarayıcıda açmayı deneyin."
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) kotlinx.coroutines.currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(isLoadingPages = false, pagesError = e.localizedMessage ?: e.message ?: "Sayfalar yüklenirken bir hata oluştu.", pagesErrorCode = SourceFailureClassifier.classifyCode(e)) }
            }
        }
    }

    fun retryLoadPages() {
        restoreProgress(_uiState.value.currentChapter)
    }

    private fun classifySourceStatus(error: Throwable): SourceHealthStatus {
        return SourceFailureClassifier.classifyStatus(error)
    }

    override fun onCleared() {
        super.onCleared()
        loader.recycle()
    }

    // ─── Factory ─────────────────────────────────────────────────────────────

    class Factory(
        private val context: Context,
        private val source: MangaSource,
        private val mangaDetails: MangaDetails,
        private val initialChapter: MangaChapter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaReaderViewModel(context, source, mangaDetails, initialChapter) as T
    }
}
