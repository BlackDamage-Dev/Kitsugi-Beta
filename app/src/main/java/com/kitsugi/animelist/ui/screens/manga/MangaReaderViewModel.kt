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
    private val scanlatorPrefs = com.kitsugi.animelist.data.manga.MangaScanlatorPreferences.get(context)
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

    /**
     * Henüz diske yazılmamış ilerleme.
     *
     * Debounce 500 ms olduğu için kullanıcı son sayfada okuyucudan hızlıca
     * çıkarsa kayıt kayboluyordu ("son kaldığım sayfayı hatırlamıyor").
     * [onCleared] bu son değeri ayrı bir IO kapsamında yazar.
     */
    private var pendingProgress: MangaChapterProgressEntity? = null
    private val flushScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

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
        val loginRequired: Boolean = false,
        /** Üye girişi / bot koruması gerekiyorsa WebView'da açılacak bölüm adresi. */
        val verificationUrl: String? = null,
        /** Bölümlerin çevirmene göre gruplanmış hâli (aynı bölüm = tek satır). */
        val chapterGroups: List<com.kitsugi.animelist.data.manga.MangaChapterGroup> = emptyList(),
        /** Bu manga için hatırlanan çevirmen (bölüm geçişinde ön seçim). */
        val preferredScanlator: String? = null,
        /** Aynı bölümün birden çok çevirmeni varsa açılacak seçim penceresi. */
        val pendingChoice: com.kitsugi.animelist.data.manga.ChapterChoiceRequest? = null
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
                // Tek doğruluk kaynağı: MangaSettingIds. Eski sürümlerin yazdığı
                // "GrayScale", "Inverted", "Warm", "Cool", "WebtoonZoom" gibi
                // değerler burada geriye dönük uyumlu olarak çözülür.
                val mode = MangaSettingIds.readingMode(settings.mangaReadingMode)
                val filter = MangaSettingIds.colorFilter(settings.mangaColorFilter)
                val fit = MangaSettingIds.fitMode(settings.mangaFitMode)
                // R2: Webtoon modunda 3+1 keep, pager modunda 2+1 keep preload
                loader.preloadAhead = if (mode == ReadingMode.Webtoon) 3 else 2
                loader.keepBehind   = 1
                // Kullanıcı az önce okuyucudan bir ayar değiştirdiyse, DataStore'dan
                // gelen (henüz güncellenmemiş) değer ekranı geri sıçratmasın.
                val keepLocal = System.currentTimeMillis() - lastLocalPrefChangeAt < 800L
                if (!keepLocal) {
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
                val groups = com.kitsugi.animelist.data.manga.MangaChapterGrouping.group(chapters)
                _uiState.update {
                    it.copy(
                        chapterList = chapters,
                        chapterGroups = groups,
                        isLoadingChapters = false,
                        preferredScanlator = scanlatorPrefs.preferredScanlator(mangaDetails.url)
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
     * Sayfa değiştikçe Room'a anlık olarak kaydeder.
     * Bölüm tamamlandıysa isCompleted = true olarak işaretler.
     */
    // F5: Progress debounce — 500ms bekle, hızlı swipe'ta tekrar tetiklenirse iptal et
    fun onPageChanged(chapterUrl: String, pageIndex: Int, totalPages: Int) {
        _uiState.update { it.copy(currentPageIndex = pageIndex) }
        val isCompleted = totalPages > 0 && pageIndex >= totalPages - 1
        val entity = MangaChapterProgressEntity(
            chapterUrl    = chapterUrl,
            mangaUrl      = mangaDetails.url,
            chapterName   = _uiState.value.currentChapter.name,
            lastPageIndex = pageIndex,
            totalPages    = totalPages,
            isCompleted   = isCompleted,
            updatedAt     = System.currentTimeMillis()
        )
        pendingProgress = entity
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500L)
            dao.upsert(entity)
            pendingProgress = null
        }
    }

    // ─── Okuma modu değiştirme ────────────────────────────────────────────────

    fun setReadingMode(mode: ReadingMode) {
        lastLocalPrefChangeAt = System.currentTimeMillis()
        // İyimser (optimistic) güncelleme: DataStore asenkron olduğu için mod
        // değişimi ~1 kare gecikmeli görünüyordu. Önce ekranı güncelle.
        _uiState.update { it.copy(readingMode = mode) }
        viewModelScope.launch {
            settingsDataStore.setMangaReadingMode(MangaSettingIds.readingModeId(mode))
        }
    }

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
        val ordered = groupsInReadingOrder()
        val idx = currentGroupIndex(ordered)
        if (idx >= 0 && idx < ordered.size - 1) {
            requestGroup(ordered[idx + 1], ChapterChoiceReason.Next, isForward = true)
        }
    }

    fun goToPreviousChapter() {
        val ordered = groupsInReadingOrder()
        val idx = currentGroupIndex(ordered)
        if (idx > 0) {
            requestGroup(ordered[idx - 1], ChapterChoiceReason.Previous, isForward = false)
        }
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
        get() {
            val ordered = groupsInReadingOrder()
            val idx = currentGroupIndex(ordered)
            return idx >= 0 && idx < ordered.size - 1
        }

    /** Sonraki bölümün adı (bölüm sonu geçiş kartında göstermek için); yoksa null. */
    val nextChapterName: String?
        get() {
            val ordered = groupsInReadingOrder()
            val idx = currentGroupIndex(ordered)
            return if (idx >= 0 && idx < ordered.size - 1) ordered[idx + 1].displayName else null
        }

    /** Sonraki bölümün çevirmen sayısı (>1 ise "N çevirmen" rozeti gösterilir). */
    val nextChapterVariantCount: Int
        get() {
            val ordered = groupsInReadingOrder()
            val idx = currentGroupIndex(ordered)
            return if (idx >= 0 && idx < ordered.size - 1) ordered[idx + 1].variantCount else 0
        }

    val hasPreviousChapter: Boolean
        get() {
            val ordered = groupsInReadingOrder()
            val idx = currentGroupIndex(ordered)
            return idx > 0
        }

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
        super.onCleared()
        // Son ilerlemeyi kaçırmamak için bekleyen (debounce edilmiş) kaydı yaz.
        pendingProgress?.let { entity ->
            flushScope.launch { runCatching { dao.upsert(entity) } }
        }
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
