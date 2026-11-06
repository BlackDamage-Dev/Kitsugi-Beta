package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.R
import kotlinx.coroutines.ensureActive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MangaChapterProgressEntity
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaChapterNumberResolver
import com.kitsugi.animelist.data.manga.MangaChapterSequence
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
import com.kitsugi.animelist.data.manga.ChapterChoiceReason
import com.kitsugi.animelist.data.manga.ChapterChoiceRequest
import com.kitsugi.animelist.data.manga.ChapterVariantInfo
import com.kitsugi.animelist.data.manga.MangaChapterGroup
import com.kitsugi.animelist.data.manga.MangaChapterGrouping
import com.kitsugi.animelist.data.manga.MangaSettingIds

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
    initialChapter: MangaChapter,
    /** Yerel ZIP/CBZ arşivi. Verilmezse chapter.url'den otomatik çözülür. */
    archiveFile: java.io.File? = null
) : ViewModel() {

    private val db  = KitsugiDatabase.getDatabase(context)
    private val dao = db.mangaChapterProgressDao()
    private val cache = MangaCache(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val sourceStateStore = MangaSourceStateStore(context)
    private val seriesId = readerSeriesId(source.pkgName, source.originalBaseUrl, source.lang, source.name, mangaDetails.url)
    private val scanlatorPrefs = com.kitsugi.animelist.data.manga.MangaScanlatorPreferences.get(context)
    private val downloader = com.kitsugi.animelist.data.manga.download.MangaChapterDownloader(context)

    // Named argument ŞART: MangaPageLoaderV2'nin 4. parametresi preloadAhead,
    // 6. parametresi archiveFile. Konumsal geçiş arşiv yolunu preload sayısına
    // yazıyordu ve ZIP/CBZ okuyucu hiçbir zaman aktifleşmiyordu.
    val loader = MangaPageLoaderV2(
        context = context,
        source = source,
        cache = cache,
        archiveFile = archiveFile ?: resolveArchive(initialChapter),
        archiveResolver = { chapter -> archiveFor(chapter) }
    )
    val pages: StateFlow<List<MangaPage>> = loader.pages

    /**
     * Bölümün indirilmiş arşivini bulur.
     *
     * Dosya adı numara/sıra tabanlı olduğu için o an bilinen bölüm listesindeki sıra
     * kullanılır; liste henüz yüklenmemişse 0 ile denenir. Bulunamazsa null döner ve
     * okuyucu normal akışa (ağ) düşer.
     */
    private fun archiveFor(chapter: MangaChapter): java.io.File? = runCatching {
        val index = _uiState.value.chapterList
            .sortedWith(compareBy({ it.chapterNumber < 0f }, { it.chapterNumber }))
            .indexOfFirst { it.url == chapter.url }
        downloader.existingArchive(
            source = source,
            mangaTitle = mangaDetails.title,
            mangaUrl = mangaDetails.url,
            chapter = chapter,
            index = if (index >= 0) index else 0
        )
    }.getOrNull()

    /** `file://...` veya mutlak yol olarak gelen bölümleri yerel arşiv kabul et. */
    private fun resolveArchive(chapter: MangaChapter): java.io.File? {
        val url = chapter.url
        if (!url.startsWith("file://") && !url.startsWith("/")) return null
        val file = java.io.File(url.removePrefix("file://"))
        return file.takeIf {
            it.isFile && it.extension.lowercase() in setOf("zip", "cbz")
        }
    }

    // F5: Debounce için Job referansı — hızlı swipe'ta Room IO flood önler
    private val progressSaveJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val pendingProgress = mutableMapOf<String, MangaChapterProgressEntity>()
    private val completedChapterUrls = mutableSetOf<String>()
    private val sessionOnlyCompletedUrls = mutableSetOf<String>()

    private val flushScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

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
        val keepScreenOn: Boolean = true,
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
        val autoAdvanceChapter: Boolean = false,
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
        val pagesErrorCode: String? = null,
        val loginRequired: Boolean = false,
        /** Üye girişi / bot koruması gerekiyorsa WebView'da açılacak bölüm adresi. */
        val verificationUrl: String? = null,
        /** Bölümlerin çevirmene göre gruplanmış hâli (aynı bölüm = tek satır). */
        val chapterGroups: List<com.kitsugi.animelist.data.manga.MangaChapterGroup> = emptyList(),
        /** Bu manga için hatırlanan çevirmen (bölüm geçişinde ön seçim). */
        val preferredScanlator: String? = null,
        /** Aynı bölümün birden çok çevirmeni varsa açılacak seçim penceresi. */
        val pendingChoice: com.kitsugi.animelist.data.manga.ChapterChoiceRequest? = null,
        /** Okundu olarak işaretlenmiş bölüm URL'leri — atlama için. */
        val readChapterUrls: Set<String> = emptySet(),
        val autoFullscreen: Boolean = true,
        val settingsLoaded: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState(currentChapter = initialChapter))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        // Manga-TR gibi imzalı/gate'li kaynaklarda bölüm listesi ile okuyucu
        // sayfasını AYNI ANDA istemek rate-limit ve tek kullanımlık token
        // çakışmasına yol açıyordu. Sıralı çalıştırıyoruz.
        viewModelScope.launch {
            loadChapterList()
            restoreProgress(initialChapter)
        }
    }

    private fun updatePreload(count: Int) {
        if (loader.preloadAhead == count) return
        loader.preloadAhead = count
        if (!_uiState.value.isLoadingPages && loader.pages.value.isNotEmpty()) loader.onPageChanged(_uiState.value.currentPageIndex)
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                // Tek doğruluk kaynağı: MangaSettingIds. Eski sürümlerin yazdığı
                // "GrayScale", "Inverted", "Warm", "Cool", "WebtoonZoom" gibi
                // değerler burada geriye dönük uyumlu olarak çözülür.
                val mode = MangaSettingIds.readingMode(settings.mangaReadingMode)
                val filter = MangaSettingIds.colorFilter(settings.mangaColorFilter)
                val fit = MangaSettingIds.fitMode(settings.mangaFitMode)
                updatePreload(readerPreloadCount(settings.mangaPreloadAhead, mode == ReadingMode.Webtoon))
                loader.keepBehind = 1
                // Kullanıcı az önce okuyucudan bir ayar değiştirdiyse, DataStore'dan
                // gelen (henüz güncellenmemiş) değer ekranı geri sıçratmasın.
                val keepLocal = System.currentTimeMillis() - lastLocalPrefChangeAt < 800L
                if (!keepLocal) {
                    _uiState.update {
                        it.copy(
                            readingMode = mode,
                            colorFilterType = filter,
                            saturation = settings.mangaSaturation,
                            contrast = settings.mangaContrast,
                            hue = settings.mangaHue,
                            fitMode = fit,
                            customBrightness = settings.mangaBrightness,
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
                            autoAdvanceChapter = settings.mangaAutoAdvanceChapter,
                            webtoonAutoNext = settings.mangaWebtoonAutoNext,
                            readerBackground = settings.mangaReaderBackground,
                            doublePage = settings.mangaDoublePage,
                            coverAlone = settings.mangaCoverAlone,
                            webtoonAutoScrollSpeed = settings.mangaWebtoonAutoScrollSpeed,
                            webtoonScrollPercent = settings.mangaWebtoonScrollPercent,
                            preloadAhead = settings.mangaPreloadAhead,
                            volumeKeys = settings.mangaVolumeKeys,
                            invertVolumeKeys = settings.mangaInvertVolumeKeys,
                            autoFullscreen = settings.mangaAutoFullscreen,
                            settingsLoaded = true
                        )
                    }
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

    fun setAutoFullscreen(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setMangaAutoFullscreen(enabled)
        }
    }

    // ─── Bölüm listesini yükle ────────────────────────────────────────────────

    private suspend fun loadChapterList() {
        _uiState.update { it.copy(isLoadingChapters = true, error = null) }
            val startedAt = System.currentTimeMillis()
            try {
                val chapters = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    source.fetchChapterList(mangaDetails.url).distinctBy { it.url }
                }
                sourceStateStore.recordOperationSuccess(
                    source = source,
                    operation = "chapters",
                    elapsedMs = System.currentTimeMillis() - startedAt,
                )
                // Okunan bölümler "skip read" için tek seferde çekilir (Room).
                val readUrls = runCatching {
                    dao.getAllForManga(mangaDetails.url)
                        .filter { it.isCompleted }
                        .map { it.chapterUrl }
                        .toSet()
                }.getOrDefault(emptySet())
                val groups = com.kitsugi.animelist.data.manga.MangaChapterGrouping.group(chapters)
                _uiState.update {
                    it.copy(
                        chapterList = chapters,
                        chapterGroups = groups,
                        isLoadingChapters = false,
                        preferredScanlator = scanlatorPrefs.preferredScanlator(mangaDetails.url),
                        readChapterUrls = readUrls
                    )
                }
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

    // ─── İlerleme kaydetme ───────────────────────────────────────────────────

    /**
     * Okuyucudan çıkarken / uygulama arkaya geçerken çağrılır.
     *
     * Debounce (500 ms) bekleyen ilerleme varsa **hemen** yazılır; aksi hâlde kullanıcı son
     * sayfadan çıkıp geri döndüğünde "son okunan sayfa" bir önceki sayfada kalıyordu.
     */
    fun flushProgress() {
        if (pendingProgress.isEmpty()) return
        val unsaved = pendingProgress.values.toList()
        pendingProgress.clear()
        progressSaveJobs.values.forEach { it.cancel() }
        progressSaveJobs.clear()
        flushScope.launch {
            unsaved.forEach { progress ->
                runCatching {
                    dao.upsertIfNewer(progress)
                    com.kitsugi.animelist.data.local.MangaReadHistoryManager.record(
                        sourceName = source.name,
                        mangaUrl = mangaDetails.url,
                        mangaTitle = mangaDetails.title,
                        thumbnailUrl = mangaDetails.thumbnailUrl,
                        chapterUrl = progress.chapterUrl,
                        chapterName = progress.chapterName,
                        lastPageIndex = progress.lastPageIndex,
                        totalPages = progress.totalPages,
                        isCompleted = progress.isCompleted
                    )
                }
            }
        }
    }

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
            if (paused) sessionOnlyCompletedUrls.add(chapterUrl) else {
                completedChapterUrls.add(chapterUrl)
                _uiState.update { it.copy(readChapterUrls = it.readChapterUrls + chapterUrl) }
            }
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
                runCatching {
                    com.kitsugi.animelist.data.local.MangaReadHistoryManager.record(
                        sourceName = source.name,
                        mangaUrl = mangaDetails.url,
                        mangaTitle = mangaDetails.title,
                        thumbnailUrl = mangaDetails.thumbnailUrl,
                        chapterUrl = progress.chapterUrl,
                        chapterName = progress.chapterName,
                        lastPageIndex = progress.lastPageIndex,
                        totalPages = progress.totalPages,
                        isCompleted = progress.isCompleted
                    )
                }
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
        lastLocalPrefChangeAt = System.currentTimeMillis()
        // İyimser (optimistic) güncelleme: DataStore asenkron olduğu için mod
        // değişimi ~1 kare gecikmeli görünüyordu. Önce ekranı güncelle.
        _uiState.update { it.copy(readingMode = mode) }
        viewModelScope.launch {
            settingsDataStore.setMangaReadingMode(MangaSettingIds.readingModeId(mode))
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
        lastLocalPrefChangeAt = System.currentTimeMillis()
        _uiState.update { it.copy(colorFilterType = filter) }
        viewModelScope.launch {
            settingsDataStore.setMangaColorFilter(MangaSettingIds.colorFilterId(filter))
        }
    }

    fun setFitMode(fitMode: MangaFitMode) {
        lastLocalPrefChangeAt = System.currentTimeMillis()
        _uiState.update { it.copy(fitMode = fitMode) }
        viewModelScope.launch {
            settingsDataStore.setMangaFitMode(MangaSettingIds.fitModeId(fitMode))
        }
    }

    /**
     * Parlaklık sürgüsü: ekran ANINDA güncellenir, diske yazma 250 ms
     * geciktirilir. Önceden her sürgü adımı ayrı bir DataStore yazımı
     * tetikliyordu (sürgü takılması + gereksiz disk yükü).
     */
    fun setCustomBrightness(brightness: Float) {
        lastLocalPrefChangeAt = System.currentTimeMillis()
        _uiState.update { it.copy(customBrightness = brightness) }
        brightnessSaveJob?.cancel()
        brightnessSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250L)
            settingsDataStore.setMangaBrightness(brightness)
        }
    }

    /** Bölüm bitince otomatik geçiş tercihi — kalıcı. */
    fun setAutoAdvanceChapter(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaAutoAdvanceChapter(enabled) }
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
        // Kullanıcı bir çevirmen seçtiyse sonraki bölümlerde de o çevirmen önerilir.
        chapter.scanlator?.takeIf { it.isNotBlank() }?.let { scanlator ->
            scanlatorPrefs.setPreferredScanlator(mangaDetails.url, scanlator)
        }
        loader.resetQueue()
        _uiState.update {
            it.copy(
                currentChapter = chapter,
                currentPageIndex = 0,
                pendingChoice = null,
                preferredScanlator = scanlatorPrefs.preferredScanlator(mangaDetails.url)
            )
        }
        restoreProgress(chapter)
    }

    /**
     * Bölüm gruplarını **okuma sırasına** (1 → N) göre dizer.
     *
     * Kaynaklar bölüm listesini kimi zaman yeniden eskiye, kimi zaman eskiden yeniye
     * döndürür. Okuyucudaki ileri/geri ve bölüm sonu kartı bu sayede kaynağın listeleme
     * yönünden bağımsız olarak her zaman doğru çalışır.
     */
    private fun groupsInReadingOrder(): List<MangaChapterGroup> {
        val groups = _uiState.value.chapterGroups
        if (groups.isEmpty()) return emptyList()
        val withNumbers = groups.map { group ->
            val number = if (group.chapterNumber >= 0f) group.chapterNumber
            else MangaChapterNumberResolver.resolve(
                mangaTitle = mangaDetails.title,
                chapterName = group.displayName,
                declaredNumber = group.chapterNumber
            )
            group to number
        }
        val numbered = withNumbers.filter { it.second >= 0f }
        val unnumbered = withNumbers.filter { it.second < 0f }
        return if (numbered.size >= 2) {
            numbered.sortedBy { it.second }.map { it.first } + unnumbered.map { it.first }
        } else {
            groups.reversed()
        }
    }

    /** Açık bölümün okuma sırasındaki grup indeksi (yoksa −1). */
    private fun currentGroupIndex(ordered: List<MangaChapterGroup> = groupsInReadingOrder()): Int {
        val state = _uiState.value
        if (ordered.isEmpty()) return -1
        val direct = ordered.indexOfFirst { it.containsUrl(state.currentChapter.url) }
        if (direct >= 0) return direct
        // Navigasyondan gelen bölüm listede birebir yoksa (URL farklı olabilir)
        // bölüm numarasına göre eşleştir.
        val key = MangaChapterGrouping.groupKey(state.currentChapter)
        return ordered.indexOfFirst { it.key == key }
    }

    fun goToNextChapter() {
        val target = nextGroup() ?: return
        requestGroup(target, ChapterChoiceReason.Next, isForward = true)
    }

    fun goToPreviousChapter() {
        val target = previousGroup() ?: return
        requestGroup(target, ChapterChoiceReason.Previous, isForward = false)
    }

    /** Sıradaki grup — okunanları atlama tercihi ([UiState.skipReadChapters]) uygulanmış hâlde. */
    private fun nextGroup(): MangaChapterGroup? {
        val ordered = groupsInReadingOrder()
        val idx = currentGroupIndex(ordered)
        if (idx < 0) return null
        val candidates = ordered.drop(idx + 1)
        if (!_uiState.value.skipReadChapters) return candidates.firstOrNull()
        val read = _uiState.value.readChapterUrls
        return candidates.firstOrNull { group -> group.variants.none { it.url in read } }
            ?: candidates.firstOrNull()
    }

    private fun previousGroup(): MangaChapterGroup? {
        val ordered = groupsInReadingOrder()
        val idx = currentGroupIndex(ordered)
        if (idx <= 0) return null
        val candidates = ordered.take(idx).reversed()
        if (!_uiState.value.skipReadChapters) return candidates.firstOrNull()
        val read = _uiState.value.readChapterUrls
        return candidates.firstOrNull { group -> group.variants.none { it.url in read } }
            ?: candidates.firstOrNull()
    }

    /**
     * Hedef gruba geçiş.
     *
     * Aynı bölümün birden çok çevirmeni varsa ve kullanıcı "bir daha sorma"
     * seçeneğini açmadıysa [UiState.pendingChoice] yayınlanır → UI ayrıntılı
     * seçim penceresini gösterir.
     */
    private fun requestGroup(
        group: MangaChapterGroup,
        reason: ChapterChoiceReason,
        isForward: Boolean,
    ) {
        if (group.variants.isEmpty()) return
        if (group.variantCount == 1) {
            goToChapter(group.variants.first())
            return
        }

        val preferred = scanlatorPrefs.preferredScanlator(mangaDetails.url)
        val remembered = scanlatorPrefs.groupChoice(mangaDetails.url, group.key)
        val autoContinue = scanlatorPrefs.autoContinue(mangaDetails.url)

        if (autoContinue && preferred != null) {
            val chosen = MangaChapterGrouping.variantForScanlator(group, preferred)
            if (chosen != null) {
                goToChapter(chosen)
                return
            }
        }

        // Hedef bölümün tüm çevirmenleri için okuma ilerlemesini topla: pencere
        // "hangisinde kaldım?" sorusunu da yanıtlayabilsin.
        val fromChapterUrl = _uiState.value.currentChapter.url
        viewModelScope.launch {
            val progressByUrl = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    dao.getAllForManga(mangaDetails.url)
                }
            }.getOrDefault(emptyList()).associateBy { it.chapterUrl }

            val variants = group.variants.map { chapter ->
                val progress = progressByUrl[chapter.url]
                ChapterVariantInfo(
                    chapter = chapter,
                    scanlator = chapter.scanlator,
                    lastPageIndex = progress?.lastPageIndex ?: 0,
                    totalPages = progress?.totalPages ?: 0,
                    isCompleted = progress?.isCompleted == true,
                    updatedAt = progress?.updatedAt ?: 0L,
                )
            }

            // Kullanıcı bu arada başka bir bölüme geçtiyse eski isteği gösterme.
            if (_uiState.value.currentChapter.url != fromChapterUrl) return@launch

            _uiState.update {
                it.copy(
                    pendingChoice = ChapterChoiceRequest(
                        mangaUrl = mangaDetails.url,
                        groupKey = group.key,
                        chapterName = group.displayName,
                        variants = variants,
                        preferredScanlator = preferred ?: remembered,
                        reason = reason,
                        isForward = isForward,
                    )
                )
            }
        }
    }

    /** Seçim penceresinde "Devam et" — seçilen çevirmenle bölüme geçer. */
    fun confirmChapterChoice(
        chapter: MangaChapter,
        rememberScanlator: Boolean,
        autoContinue: Boolean,
    ) {
        val request = _uiState.value.pendingChoice
        _uiState.update { it.copy(pendingChoice = null) }
        if (rememberScanlator) {
            scanlatorPrefs.setPreferredScanlator(mangaDetails.url, chapter.scanlator)
            request?.let { scanlatorPrefs.setGroupChoice(mangaDetails.url, it.groupKey, chapter.scanlator) }
        }
        scanlatorPrefs.setAutoContinue(mangaDetails.url, autoContinue)
        goToChapter(chapter)
    }

    /** Seçim penceresini kapatır (bölüm değişmez). */
    fun dismissChapterChoice() {
        _uiState.update { it.copy(pendingChoice = null) }
    }

    val hasNextChapter: Boolean
        get() = nextGroup() != null

    /** Sonraki bölümün adı (bölüm sonu geçiş kartında göstermek için); yoksa null. */
    val nextChapterName: String?
        get() = nextGroup()?.displayName

    /** Sonraki bölümün çevirmen sayısı (>1 ise "N çevirmen" rozeti gösterilir). */
    val nextChapterVariantCount: Int
        get() = nextGroup()?.variantCount ?: 0

    val hasPreviousChapter: Boolean
        get() = previousGroup() != null

    val previousChapterName: String?
        get() = previousGroup()?.displayName

    private var pageListJob: kotlinx.coroutines.Job? = null
    private var brightnessSaveJob: kotlinx.coroutines.Job? = null

    /** Okuyucudan yapılan son yerel ayar değişikliğinin zamanı (ms). */
    private var lastLocalPrefChangeAt = 0L

    private fun restoreProgress(chapter: MangaChapter) {
        pageListJob?.cancel()
        pageListJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingPages = true,
                    pagesError = null,
                    pagesErrorCode = null,
                    verificationUrl = null,
                )
            }
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
                    _uiState.update {
                        it.copy(
                            isLoadingPages = false,
                            pagesError = context.getString(R.string.manga_reader_error_pages),
                            pagesErrorCode = null,
                            verificationUrl = null,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingPages = false) }
                }
            } catch (e: com.kitsugi.animelist.data.manga.CaptchaRequiredException) {
                // Manga-TR: "WebView üzerinden üye girişi" / "Bot Korumasını geçin".
                // Sadece mesaj göstermek yetmiyor; aynı cookie oturumunda doğrulama
                // yapıp tekrar denemek için bölüm URL'ini UI'a veriyoruz.
                _uiState.update {
                    it.copy(
                        isLoadingPages = false,
                        pagesError = context.getString(R.string.manga_reader_error_captcha, e.sourceName),
                        verificationUrl = com.kitsugi.animelist.data.manga.MangaTrCompat.readerUrl(source, chapter.url)
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val message = e.localizedMessage ?: e.message ?: context.getString(R.string.manga_reader_error_pages_generic)
                val needsVerification = com.kitsugi.animelist.data.manga.MangaTrCompat.isVerificationError(message)
                val loginReq = com.kitsugi.animelist.data.manga.isMangaTrLoginRequired(source.pkgName, message)
                _uiState.update {
                    it.copy(
                        isLoadingPages = false,
                        pagesError = message,
                        pagesErrorCode = SourceFailureClassifier.classifyCode(e),
                        loginRequired = loginReq,
                        verificationUrl = if (needsVerification) {
                            com.kitsugi.animelist.data.manga.MangaTrCompat.readerUrl(source, chapter.url)
                        } else null
                    )
                }
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
        val unsaved = pendingProgress.values.toList()
        progressSaveJobs.values.forEach { it.cancel() }
        progressSaveJobs.clear()
        if (unsaved.isNotEmpty()) {
            flushScope.launch {
                unsaved.forEach { progress ->
                    try {
                        dao.upsertIfNewer(progress)
                    } catch (e: Exception) {
                        android.util.Log.e("MangaReader", "Unable to save pending progress", e)
                    }
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
        private val initialChapter: MangaChapter,
        private val archiveFile: java.io.File? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaReaderViewModel(context, source, mangaDetails, initialChapter, archiveFile) as T
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
