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
    private val seriesId = readerSeriesId(source.pkgName, source.originalBaseUrl, source.lang, source.name, mangaDetails.url)
    val loader = MangaPageLoaderV2(context, source, cache)
    val pages: StateFlow<List<MangaPage>> = loader.pages

    // F5: Debounce için Job referansı — hızlı swipe'ta Room IO flood önler
    private val progressSaveJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val pendingProgress = mutableMapOf<String, MangaChapterProgressEntity>()
    private val completedChapterUrls = mutableSetOf<String>()
    private val sessionOnlyCompletedUrls = mutableSetOf<String>()

    // ─── UI Durumu ────────────────────────────────────────────────────────────

    data class UiState(
        val currentChapter: MangaChapter,
        val chapterList: List<MangaChapter> = emptyList(),
        val currentPageIndex: Int = 0,
        val readingMode: ReadingMode = ReadingMode.RightToLeft,
        val seriesModeOverride: Boolean = false,
        val colorFilterType: ColorFilterType = ColorFilterType.Normal,
        val saturation: Int = 100,
        val contrast: Int = 100,
        val hue: Int = 0,
        val fitMode: MangaFitMode = MangaFitMode.FitScreen,
        val customBrightness: Float = 1.0f,
        val webtoonPadding: Int = 0,
        val webtoonGap: Int = 4,
        val showPageNumber: Boolean = true,
        val keepScreenOn: Boolean = false,
        val immersive: Boolean = false,
        val cropBorders: Boolean = false,
        val cropBordersWebtoon: Boolean = false,
        val webtoonZoom: Boolean = true,
        val wideRotation: String = "Off",
        val wideRotationWebtoon: String = "Off",
        val invertTapZones: Boolean = false,
        val tapLayout: String = "Default",
        val tapGrid: String = "PMNPMNPMN",
        val longTapGrid: String = "XXXXXXXXX",
        val webtoonTapScroll: Boolean = false,
        val skipReadChapters: Boolean = false,
        val pauseProgressSaving: Boolean = false,
        val webtoonAutoNext: Boolean = false,
        val readerBackground: String = "Black",
        val doublePage: Boolean = false,
        val coverAlone: Boolean = false,
        val webtoonAutoScrollSpeed: Int = 40,
        val webtoonScrollPercent: Int = 80,
        val preloadAhead: Int = -1,
        val volumeKeys: Boolean = false,
        val invertVolumeKeys: Boolean = false,
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

    private fun updatePreload(count: Int) {
        if (loader.preloadAhead == count) return
        loader.preloadAhead = count
        if (!_uiState.value.isLoadingPages && loader.pages.value.isNotEmpty()) loader.onPageChanged(_uiState.value.currentPageIndex)
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val filter = when (settings.mangaColorFilter) {
                    "Normal" -> ColorFilterType.Normal
                    "Grayscale" -> ColorFilterType.Grayscale
                    "Sepia" -> ColorFilterType.Sepia
                    "Invert" -> ColorFilterType.Invert
                    else -> ColorFilterType.Normal
                }
                val fit = when (settings.mangaFitMode) {
                    "FitScreen" -> MangaFitMode.FitScreen
                    "FitWidth" -> MangaFitMode.FitWidth
                    "FitHeight" -> MangaFitMode.FitHeight
                    else -> MangaFitMode.FitScreen
                }
                updatePreload(readerPreloadCount(settings.mangaPreloadAhead, _uiState.value.readingMode == ReadingMode.Webtoon))
                _uiState.update {
                    it.copy(
                        colorFilterType = filter,
                        saturation = settings.mangaSaturation,
                        contrast = settings.mangaContrast,
                        hue = settings.mangaHue,
                        fitMode = fit,
                        webtoonPadding = settings.mangaWebtoonPadding,
                        webtoonGap = settings.mangaWebtoonGap,
                        showPageNumber = settings.mangaShowPageNumber,
                        keepScreenOn = settings.mangaKeepScreenOn,
                        immersive = settings.mangaImmersive,
                        cropBorders = settings.mangaCropBorders,
                        cropBordersWebtoon = settings.mangaCropBordersWebtoon,
                        webtoonZoom = settings.mangaWebtoonZoom,
                        wideRotation = settings.mangaWideRotation,
                        wideRotationWebtoon = settings.mangaWideRotationWebtoon,
                        invertTapZones = settings.mangaInvertTapZones,
                        tapLayout = settings.mangaTapLayout,
                        tapGrid = settings.mangaTapGrid,
                        longTapGrid = settings.mangaLongTapGrid,
                        webtoonTapScroll = settings.mangaWebtoonTapScroll,
                        skipReadChapters = settings.mangaSkipReadChapters,
                        webtoonAutoNext = settings.mangaWebtoonAutoNext,
                        readerBackground = settings.mangaReaderBackground,
                        doublePage = settings.mangaDoublePage,
                        coverAlone = settings.mangaCoverAlone,
                        webtoonAutoScrollSpeed = settings.mangaWebtoonAutoScrollSpeed,
                        webtoonScrollPercent = settings.mangaWebtoonScrollPercent,
                        preloadAhead = settings.mangaPreloadAhead,
                        volumeKeys = settings.mangaVolumeKeys,
                        invertVolumeKeys = settings.mangaInvertVolumeKeys,
                        customBrightness = settings.mangaBrightness
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.mangaReadingModeForSeries(seriesId).collect { (globalMode, seriesMode) ->
                val mode = ReadingMode.values().firstOrNull { it.name == (seriesMode ?: globalMode) }
                    ?: ReadingMode.RightToLeft
                updatePreload(readerPreloadCount(_uiState.value.preloadAhead, mode == ReadingMode.Webtoon))
                loader.keepBehind = 1
                _uiState.update { it.copy(readingMode = mode, seriesModeOverride = seriesMode != null) }
            }
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
        if (totalPages <= 0 || chapterUrl != _uiState.value.currentChapter.url) return
        val safePageIndex = pageIndex.coerceIn(0, totalPages - 1)
        val chapterName = _uiState.value.currentChapter.name
        val paused = _uiState.value.pauseProgressSaving
        val decision = readerProgressDecision(paused, chapterUrl in completedChapterUrls,
            chapterUrl in sessionOnlyCompletedUrls, _uiState.value.readingMode == ReadingMode.Webtoon,
            safePageIndex, totalPages)
        val isCompleted = decision.completed
        if (isCompleted) {
            if (paused) sessionOnlyCompletedUrls.add(chapterUrl) else completedChapterUrls.add(chapterUrl)
        }
        _uiState.update { it.copy(currentPageIndex = safePageIndex) }
        if (!decision.persist) return
        progressSaveJobs[chapterUrl]?.cancel()
        val progress = MangaChapterProgressEntity(
            chapterUrl = chapterUrl,
            mangaUrl = mangaDetails.url,
            chapterName = chapterName,
            lastPageIndex = safePageIndex,
            totalPages = totalPages,
            isCompleted = isCompleted,
            updatedAt = nextReaderProgressTimestamp()
        )
        pendingProgress[chapterUrl] = progress
        val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                kotlinx.coroutines.delay(500L)
                dao.upsertIfNewer(progress)
                if (pendingProgress[chapterUrl] === progress) pendingProgress.remove(chapterUrl)
            } finally {
                if (progressSaveJobs[chapterUrl] === kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]) {
                    progressSaveJobs.remove(chapterUrl)
                }
            }
        }
        progressSaveJobs[chapterUrl] = job
        job.start()
    }

    fun onWebtoonChapterEnd(chapterUrl: String, totalPages: Int) {
        if (chapterUrl != _uiState.value.currentChapter.url || totalPages <= 0) return
        if (_uiState.value.pauseProgressSaving) sessionOnlyCompletedUrls.add(chapterUrl)
        else completedChapterUrls.add(chapterUrl)
        onPageChanged(chapterUrl, totalPages - 1, totalPages)
    }

    /** Session-only progress suppression; this is not an anonymous browsing mode. */
    fun setPauseProgressSaving(value: Boolean) {
        if (_uiState.value.pauseProgressSaving == value) return
        if (value) {
            progressSaveJobs.values.toList().forEach { it.cancel() }
            progressSaveJobs.clear()
            pendingProgress.clear()
        } else sessionOnlyCompletedUrls.clear()
        _uiState.update { it.copy(pauseProgressSaving = value) }
    }

    // ─── Okuma modu değiştirme ────────────────────────────────────────────────

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            settingsDataStore.setMangaReadingModeForSeries(seriesId, mode.name)
        }
    }

    fun setSeriesModeOverride(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaSeriesModeEnabled(seriesId, enabled) }
    }

    fun setSaturation(value: Int) { viewModelScope.launch { settingsDataStore.setMangaSaturation(value) } }
    fun setHue(value: Int) { viewModelScope.launch { settingsDataStore.setMangaHue(value) } }
    fun setContrast(value: Int) { viewModelScope.launch { settingsDataStore.setMangaContrast(value) } }

    fun setColorFilter(filter: ColorFilterType) {
        viewModelScope.launch {
            settingsDataStore.setMangaColorFilter(filter.name)
        }
    }

    fun setFitMode(fitMode: MangaFitMode) {
        viewModelScope.launch {
            settingsDataStore.setMangaFitMode(fitMode.name)
        }
    }

    fun setCustomBrightness(brightness: Float) {
        viewModelScope.launch {
            settingsDataStore.setMangaBrightness(brightness)
        }
    }

    fun setWebtoonPadding(value: Int) {
        viewModelScope.launch { settingsDataStore.setMangaWebtoonPadding(value) }
    }

    fun setWebtoonGap(value: Int) {
        viewModelScope.launch { settingsDataStore.setMangaWebtoonGap(value) }
    }

    fun setShowPageNumber(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaShowPageNumber(value) }
    }

    fun setWideRotation(value: String, webtoon: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaWideRotation(value, webtoon) }
    }

    fun setWebtoonZoom(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaWebtoonZoom(value) }
    }

    fun setCropBordersWebtoon(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaCropBordersWebtoon(value) }
    }

    fun setCropBorders(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaCropBorders(value) }
    }

    fun setImmersive(value: Boolean) { viewModelScope.launch { settingsDataStore.setMangaImmersive(value) } }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaKeepScreenOn(value) }
    }

    fun cycleTapGridCell(index: Int, longPress: Boolean, direction: Int = 1) {
        val grid = if (longPress) _uiState.value.longTapGrid else _uiState.value.tapGrid
        val updated = com.kitsugi.animelist.ui.screens.manga.components.cycleReaderTapGrid(grid, index, direction)
        val tap = if (longPress) _uiState.value.tapGrid else updated
        val held = if (longPress) updated else _uiState.value.longTapGrid
        if (!com.kitsugi.animelist.ui.screens.manga.components.readerTapGridsKeepMenu(tap, held)) return
        _uiState.update { if (longPress) it.copy(longTapGrid = updated) else it.copy(tapGrid = updated) }
        viewModelScope.launch { settingsDataStore.setMangaTapGrid(updated, longPress) }
    }

    fun resetTapGrids() {
        _uiState.update { it.copy(tapGrid = "PMNPMNPMN", longTapGrid = "XXXXXXXXX") }
        viewModelScope.launch { settingsDataStore.resetMangaTapGrids() }
    }

    fun setTapLayout(value: String) {
        viewModelScope.launch { settingsDataStore.setMangaTapLayout(value) }
    }

    fun setInvertTapZones(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaInvertTapZones(value) }
    }

    fun setWebtoonTapScroll(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaWebtoonTapScroll(value) }
    }

    fun setSkipReadChapters(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaSkipReadChapters(value) }
    }

    fun setVolumeKeys(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaVolumeKeys(value) }
    }

    fun setInvertVolumeKeys(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaInvertVolumeKeys(value) }
    }

    fun setWebtoonScrollPercent(value: Int) {
        viewModelScope.launch { settingsDataStore.setMangaWebtoonScrollPercent(value) }
    }

    fun setPreloadAhead(value: Int) {
        viewModelScope.launch { settingsDataStore.setMangaPreloadAhead(value) }
    }

    fun setWebtoonAutoScrollSpeed(value: Int) {
        viewModelScope.launch { settingsDataStore.setMangaWebtoonAutoScrollSpeed(value) }
    }

    fun setDoublePage(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaDoublePage(value) }
    }

    fun setCoverAlone(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaCoverAlone(value) }
    }

    fun setReaderBackground(value: String) {
        if (value in listOf("Black", "DarkGray", "White", "Auto")) {
            viewModelScope.launch { settingsDataStore.setMangaReaderBackground(value) }
        }
    }

    fun setWebtoonAutoNext(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaWebtoonAutoNext(value) }
    }

    // ─── Bölüm gezinme ───────────────────────────────────────────────────────

    // F6: Bölüm değişiminde eski queue'yu temizle — yanlış sayfa yükleme önler
    fun goToChapter(chapter: MangaChapter) {
        loader.resetQueue()
        _uiState.update { it.copy(currentChapter = chapter, currentPageIndex = 0, isLoadingPages = true) }
        restoreProgress(chapter)
    }

    private var chapterNavigationJob: kotlinx.coroutines.Job? = null

    fun goToNextChapter() = navigateAdjacentChapter(-1)
    fun goToPreviousChapter() = navigateAdjacentChapter(1)

    private fun navigateAdjacentChapter(step: Int) {
        val state = _uiState.value
        val chapters = state.chapterList
        val currentUrl = state.currentChapter.url
        val currentIndex = chapters.indexOfFirst { it.url == currentUrl }
        if (currentIndex < 0) return
        val adjacentIndex = currentIndex + step
        if (adjacentIndex !in chapters.indices) return
        chapterNavigationJob?.cancel()
        if (!state.skipReadChapters) {
            goToChapter(chapters[adjacentIndex])
            return
        }
        chapterNavigationJob = viewModelScope.launch {
            val completed = dao.getCompletedChapters(mangaDetails.url).mapTo(mutableSetOf()) { it.chapterUrl }
            // Include completions still waiting for their debounced Room write.
            completed.addAll(completedChapterUrls)
            if (_uiState.value.pauseProgressSaving) completed.addAll(sessionOnlyCompletedUrls)
            val target = findUnreadChapterIndex(chapters.map { it.url }, currentIndex, step, completed)
            // Do not navigate from a stale request after the user opened another chapter.
            if (_uiState.value.currentChapter.url == currentUrl) {
                if (target != null) goToChapter(chapters[target])
                else android.widget.Toast.makeText(context, com.kitsugi.animelist.R.string.reader_no_unread_chapter, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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
            if (saved?.isCompleted == true) completedChapterUrls.add(chapter.url)
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
        // ViewModel scope is cancelled during clearing. Preserve the last debounced
        // values in a short independent IO job, with timestamp-guarded writes.
        val unsaved = pendingProgress.values.toList()
        progressSaveJobs.values.forEach { it.cancel() }
        if (unsaved.isNotEmpty()) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                unsaved.forEach { progress ->
                    try { dao.upsertIfNewer(progress) }
                    catch (e: Exception) { android.util.Log.e("MangaReader", "Unable to save pending progress", e) }
                }
            }
        }
        loader.recycle()
        super.onCleared()
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

/** The source list is ordered newest first; step selects either neighbouring direction. */
internal fun findUnreadChapterIndex(urls: List<String>, current: Int, step: Int, completed: Set<String>): Int? {
    if (step != -1 && step != 1 || current !in urls.indices) return null
    var index = current + step
    while (index in urls.indices) {
        if (urls[index] !in completed) return index
        index += step
    }
    return null
}

/** Stable, unambiguous identity without storing source URLs in preference names. */
internal fun readerSeriesId(vararg parts: String): String {
    val input = parts.joinToString("") { "${it.length}:$it" }
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
