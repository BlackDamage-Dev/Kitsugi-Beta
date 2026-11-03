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
    initialChapter: MangaChapter,
    /** Yerel ZIP/CBZ arşivi. Verilmezse chapter.url'den otomatik çözülür. */
    archiveFile: java.io.File? = null
) : ViewModel() {

    private val db  = KitsugiDatabase.getDatabase(context)
    private val dao = db.mangaChapterProgressDao()
    private val cache = MangaCache(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val sourceStateStore = MangaSourceStateStore(context)
    // Named argument ŞART: MangaPageLoaderV2'nin 4. parametresi preloadAhead,
    // 6. parametresi archiveFile. Konumsal geçiş arşiv yolunu preload sayısına
    // yazıyordu ve ZIP/CBZ okuyucu hiçbir zaman aktifleşmiyordu.
    val loader = MangaPageLoaderV2(
        context = context,
        source = source,
        cache = cache,
        archiveFile = archiveFile ?: resolveArchive(initialChapter)
    )
    val pages: StateFlow<List<MangaPage>> = loader.pages

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
    private var progressSaveJob: kotlinx.coroutines.Job? = null

    // ─── UI Durumu ────────────────────────────────────────────────────────────

    data class UiState(
        val currentChapter: MangaChapter,
        val chapterList: List<MangaChapter> = emptyList(),
        val currentPageIndex: Int = 0,
        val readingMode: ReadingMode = ReadingMode.RightToLeft,
        val colorFilterType: ColorFilterType = ColorFilterType.Normal,
        val fitMode: MangaFitMode = MangaFitMode.FitScreen,
        val customBrightness: Float = 1.0f,
        val isLoadingChapters: Boolean = false,
        val error: String? = null,
        val isLoadingPages: Boolean = false,
        val pagesError: String? = null,
        /** Sayfa yükleme hatasının manga kaynak kodu (KTS-SRC-...). */
        val pagesErrorCode: String? = null,
        /** Üye girişi / bot koruması gerekiyorsa WebView'da açılacak bölüm adresi. */
        val verificationUrl: String? = null
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

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val mode = when (settings.mangaReadingMode) {
                    "LeftToRight" -> ReadingMode.LeftToRight
                    "RightToLeft" -> ReadingMode.RightToLeft
                    "Vertical" -> ReadingMode.Vertical
                    "Webtoon" -> ReadingMode.Webtoon
                    else -> ReadingMode.RightToLeft
                }
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
                // R2: Webtoon modunda 3+1 keep, pager modunda 2+1 keep preload
                loader.preloadAhead = if (mode == ReadingMode.Webtoon) 3 else 2
                loader.keepBehind   = 1
                _uiState.update {
                    it.copy(
                        readingMode = mode,
                        colorFilterType = filter,
                        fitMode = fit,
                        customBrightness = settings.mangaBrightness
                    )
                }
            }
        }
    }

    // ─── Bölüm listesini yükle ────────────────────────────────────────────────

    private suspend fun loadChapterList() {
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

    // ─── İlerleme kaydetme ───────────────────────────────────────────────────

    /**
     * Sayfa değiştikçe Room'a anlık olarak kaydeder.
     * Bölüm tamamlandıysa isCompleted = true olarak işaretler.
     */
    // F5: Progress debounce — 500ms bekle, hızlı swipe'ta tekrar tetiklenirse iptal et
    fun onPageChanged(chapterUrl: String, pageIndex: Int, totalPages: Int) {
        _uiState.update { it.copy(currentPageIndex = pageIndex) }
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500L)
            val isCompleted = totalPages > 0 && pageIndex >= totalPages - 1
            dao.upsert(
                MangaChapterProgressEntity(
                    chapterUrl    = chapterUrl,
                    mangaUrl      = mangaDetails.url,
                    chapterName   = _uiState.value.currentChapter.name,
                    lastPageIndex = pageIndex,
                    totalPages    = totalPages,
                    isCompleted   = isCompleted,
                    updatedAt     = System.currentTimeMillis()
                )
            )
        }
    }

    // ─── Okuma modu değiştirme ────────────────────────────────────────────────

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            settingsDataStore.setMangaReadingMode(mode.name)
        }
    }

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

    // ─── Bölüm gezinme ───────────────────────────────────────────────────────

    // F6: Bölüm değişiminde eski queue'yu temizle — yanlış sayfa yükleme önler
    fun goToChapter(chapter: MangaChapter) {
        loader.resetQueue()
        _uiState.update { it.copy(currentChapter = chapter, currentPageIndex = 0) }
        restoreProgress(chapter)
    }

    fun goToNextChapter() {
        val list    = _uiState.value.chapterList
        val current = _uiState.value.currentChapter
        val idx     = list.indexOfFirst { it.url == current.url }
        if (idx > 0) goToChapter(list[idx - 1])   // Listede yeniden eskiye doğru
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
            _uiState.update {
                it.copy(
                    isLoadingPages = true,
                    pagesError = null,
                    pagesErrorCode = null,
                    verificationUrl = null,
                )
            }
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
                    val lastMsg = "Sayfalar yüklenemedi. İnternet bağlantınızı kontrol edip tekrar deneyin."
                    _uiState.update {
                        it.copy(
                            isLoadingPages = false,
                            pagesError = lastMsg,
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
                        pagesError = "⚠️ ${e.sourceName}: ${e.message ?: "Üye girişi veya bot koruması gerekiyor."}",
                        verificationUrl = com.kitsugi.animelist.data.manga.MangaTrCompat.readerUrl(source, chapter.url)
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val message = e.localizedMessage ?: e.message ?: "Sayfalar yüklenirken bir hata oluştu."
                val needsVerification = com.kitsugi.animelist.data.manga.MangaTrCompat.isVerificationError(message)
                _uiState.update {
                    it.copy(
                        isLoadingPages = false,
                        pagesError = message,
                        pagesErrorCode = SourceFailureClassifier.classifyCode(e),
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
        super.onCleared()
        loader.recycle()
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
