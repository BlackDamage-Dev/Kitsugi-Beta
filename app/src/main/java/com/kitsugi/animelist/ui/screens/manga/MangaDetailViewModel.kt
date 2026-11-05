package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.R
import android.content.Context
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaChapterGroup
import com.kitsugi.animelist.data.manga.MangaChapterGrouping
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.local.MangaChapterProgressEntity
import com.kitsugi.animelist.data.manga.MangaScanlatorPreferences
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MangaDetailScreen için ViewModel.
 *
 * Sorumluluklar:
 * - Bölüm listesini eklentiden çekmek
 * - Room'dan okuma ilerlemesini okuyup her bölümün okundu/devam durumunu hesaplamak
 * - "Oku / Devam Et" butonunun hedef bölümünü belirlemek
 *
 * NOT: Mevcut okuyucu ve navigasyon akışına dokunmaz; yalnızca detay ekranını besler.
 */
class MangaDetailViewModel(
    private val context: Context,
    val source: MangaSource,
    initialDetails: MangaDetails
) : ViewModel() {

    private val db = KitsugiDatabase.getDatabase(context)
    private val progressDao = db.mangaChapterProgressDao()
    private val scanlatorPrefs = MangaScanlatorPreferences.get(context)

    /** Detay ekranındaki manga URL'si (bölüm ve çevirmen tercihleri için anahtar). */
    private val mangaUrl: String get() = _uiState.value.details.url

    /** Bir bölümün okuma durumunu UI'a taşıyan sarmalayıcı. */
    data class ChapterRow(
        val chapter: MangaChapter,
        val isCompleted: Boolean = false,
        val lastPageIndex: Int = 0,
        val totalPages: Int = 0,
        val isInProgress: Boolean = false
    )

    /**
     * Aynı bölümün tüm çevirmenlerini tek satırda temsil eder.
     *
     * @property group gruplanmış bölüm (çevirmen sürümleri)
     * @property primary varsayılan açılacak sürüm (tercih → ilerleme → en yeni)
     * @property variants tüm sürümlerin okuma durumları
     */
    data class ChapterGroupRow(
        val group: MangaChapterGroup,
        val primary: ChapterRow,
        val variants: List<ChapterRow>,
        val isCompleted: Boolean = false,
        val isInProgress: Boolean = false
    ) {
        /**
         * Bölüm araması eşleşmesi: ad parçası **veya** numara.
         * "42" araması 42, 42.0 ve 42.5 bölümlerini de bulur.
         */
        fun matchesQuery(query: String): Boolean {
            val q = query.trim()
            if (q.isEmpty()) return true
            if (group.displayName.contains(q, ignoreCase = true)) return true
            return variants.any { chapterRowMatchesQuery(it, q) }
        }
    }

    data class UiState(
        val details: MangaDetails,
        val isLoadingDetails: Boolean = false,
        val isLoadingChapters: Boolean = true,
        val chapters: List<ChapterRow> = emptyList(),
        val error: String? = null,
        /** Hatanın manga kaynak kodu (KTS-SRC-...). */
        val errorCode: String? = null,
        /** "Devam Et" için hedef bölüm; yoksa null (baştan okunur). */
        val resumeChapter: MangaChapter? = null,
        /** Aynı bölümün farklı çevirmenleri tek satırda gruplanmış hâli. */
        val groups: List<ChapterGroupRow> = emptyList(),
        /** true: çevirmenler birleşik gösterilir, false: her sürüm ayrı satır. */
        val mergedScanlators: Boolean = true,
        /** Açılmış (çevirmen listesi görünen) grup anahtarları. */
        val expandedGroupKeys: Set<String> = emptySet(),
        /** Bu manga için hatırlanan çevirmen. */
        val preferredScanlator: String? = null,
        /** Çok çevirmenli bölümlerde soru sormadan devam et. */
        val autoContinue: Boolean = false,
        /** Bölüm listesinde geçen tüm çevirmenler (filtre çipleri için). */
        val scanlators: List<String> = emptyList(),
        /** Seçili çevirmen filtresi (null = tümü). */
        val scanlatorFilter: String? = null,
        /** Bölüm listesi içinde arama (numara veya ad parçası). */
        val chapterQuery: String = "",
        /** Yalnızca okunmamış bölümleri göster. */
        val onlyUnread: Boolean = false
    ) {
        /** Çevirmen filtresi uygulanmış gruplar. */
        private val scanlatorFilteredGroups: List<ChapterGroupRow>
            get() {
                val filter = scanlatorFilter?.trim()?.lowercase()
                if (filter.isNullOrEmpty()) return groups
                return groups.filter { row ->
                    row.variants.any { variant ->
                        variant.chapter.scanlator?.trim()?.lowercase() == filter
                    }
                }
            }

        /**
         * Ekranın gösterdiği gruplar: çevirmen filtresi + bölüm araması +
         * "yalnızca okunmamış" filtresi birlikte uygulanır.
         */
        val visibleGroups: List<ChapterGroupRow>
            get() {
                var result = scanlatorFilteredGroups
                val query = chapterQuery.trim()
                if (query.isNotEmpty()) {
                    result = result.filter { it.matchesQuery(query) }
                }
                if (onlyUnread) {
                    result = result.filter { !it.isCompleted }
                }
                return result
            }

        /** Ayrık (merge kapalı) görünüm için aynı filtreler. */
        val visibleChapters: List<ChapterRow>
            get() {
                val filter = scanlatorFilter?.trim()?.lowercase()
                val query = chapterQuery.trim()
                return chapters.filter { row ->
                    val scanlatorOk = filter.isNullOrEmpty() ||
                        row.chapter.scanlator?.trim()?.lowercase() == filter
                    // Birleşik/açık görünüm aynı arama kurallarını kullanır:
                    // "42" araması 42 ve 42.5 bölümlerini bulur (sadece ad değil).
                    val queryOk = chapterRowMatchesQuery(row, query)
                    val unreadOk = !onlyUnread || !row.isCompleted
                    scanlatorOk && queryOk && unreadOk
                }
            }

        /** Filtrelerden herhangi biri etkin mi? (boş durum metnini seçmek için) */
        val hasActiveChapterFilter: Boolean
            get() = chapterQuery.isNotBlank() || onlyUnread || !scanlatorFilter.isNullOrBlank()
    }

    private val _uiState = MutableStateFlow(UiState(details = initialDetails))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Manga-TR detay sayfası hem kapak/aciklama hem de imzalı `listKey` içerir.
        // Eskiden refreshDetails() ve loadChapters() AYNI ANDA iki ayrı istek atıyordu;
        // DDoS-Guard oturumu/rate-limit yüzünden bölüm listesi sık sık boş düşüyordu.
        viewModelScope.launch {
            refreshDetails()
            loadChapters()
        }
    }

    /** Kapak/açıklama gibi alanları tazelemek için detayları yeniden çeker (best-effort). */
    private suspend fun refreshDetails() {
            _uiState.update { it.copy(isLoadingDetails = true) }
            val initial = _uiState.value.details
            try {
                val fresh = withContext(Dispatchers.IO) {
                    val timeout = if (source.pkgName.endsWith(".mangatr", ignoreCase = true)) 200_000L else 15_000L
                    kotlinx.coroutines.withTimeout(timeout) {
                        source.fetchMangaDetails(initial.url)
                    }
                }

                // Bazı kaynaklar fetchMangaDetails başarısız olunca title = url döndürür.
                // URL'e benzeyen (/ ile başlayan veya :// içeren) bir title anlamlı değil
                // demektir — bu durumda mevcut (browse sayfasından gelen) title'ı koru.
                fun String?.isUrlLike() = this != null && (startsWith("/") || contains("://"))
                fun String?.isMeaningful() = !isNullOrBlank() && !isUrlLike()

                val merged = initial.copy(
                    title        = fresh.title.takeIf { it.isMeaningful() } ?: initial.title,
                    thumbnailUrl = fresh.thumbnailUrl.takeIf { it.isMeaningful() }
                                       ?: initial.thumbnailUrl,
                    description  = fresh.description?.takeIf { it.isNotBlank() }
                                       ?: initial.description,
                    author       = fresh.author?.takeIf { it.isNotBlank() } ?: initial.author,
                    artist       = fresh.artist?.takeIf { it.isNotBlank() } ?: initial.artist,
                    genre        = fresh.genre.takeIf { it.isNotEmpty() } ?: initial.genre,
                    status       = if (fresh.status != MangaStatus.Unknown) fresh.status else initial.status,
                    source       = initial.source.takeIf { it.isNotBlank() } ?: fresh.source
                )
                _uiState.update { it.copy(details = merged, isLoadingDetails = false) }
            } catch (_: Exception) {
                // Detay tazeleme başarısız olsa bile elimizdeki initialDetails ile devam ederiz.
                _uiState.update { it.copy(isLoadingDetails = false) }
            }
    }

    fun loadChapters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChapters = true, error = null, errorCode = null) }
            var lastError: Exception? = null
            // Ağ gecikmelerine karşı 2 deneme yap
            repeat(2) attempt@{ attempt ->
                try {
                    val mangaUrl = _uiState.value.details.url
                    val chapters = withContext(Dispatchers.IO) {
                        kotlinx.coroutines.withTimeout(180_000L) {
                            source.fetchChapterList(mangaUrl).distinctBy { it.url }
                        }
                    }
                    val progressList = withContext(Dispatchers.IO) {
                        progressDao.getAllForManga(mangaUrl)
                    }
                    val progressByUrl = progressList.associateBy { it.chapterUrl }

                    val rows = chapters.map { ch ->
                        val p = progressByUrl[ch.url]
                        ChapterRow(
                            chapter = ch,
                            isCompleted = p?.isCompleted == true,
                            lastPageIndex = p?.lastPageIndex ?: 0,
                            totalPages = p?.totalPages ?: 0,
                            isInProgress = p != null && p.isCompleted.not() && p.lastPageIndex > 0
                        )
                    }

                    // "Devam Et" hedefi: en son güncellenen, tamamlanmamış bölüm.
                    val resume = progressList
                        .filter { !it.isCompleted }
                        .maxByOrNull { it.updatedAt }
                        ?.let { prog -> chapters.firstOrNull { it.url == prog.chapterUrl } }

                    // Aynı bölümün farklı çevirmen sürümlerini tek satırda topla.
                    val preferred = scanlatorPrefs.preferredScanlator(mangaUrl)
                    val rowsByUrl = rows.associateBy { it.chapter.url }
                    val groups = MangaChapterGrouping.group(chapters).map { group ->
                        val variantRows = group.variants.map { chapter ->
                            rowsByUrl[chapter.url] ?: ChapterRow(chapter = chapter)
                        }
                        val primaryChapter = MangaChapterGrouping.pickVariant(
                            group = group,
                            preferredScanlator = preferred,
                            hasProgress = { chapter -> rowsByUrl[chapter.url]?.isInProgress == true }
                        )
                        ChapterGroupRow(
                            group = group,
                            primary = variantRows.firstOrNull { it.chapter.url == primaryChapter.url }
                                ?: variantRows.first(),
                            variants = variantRows,
                            isCompleted = variantRows.any { it.isCompleted },
                            isInProgress = variantRows.any { it.isInProgress }
                        )
                    }

                    // Filtre çipleri: en çok bölümü olan çevirmen önce.
                    val scanlators = chapters
                        .mapNotNull { it.scanlator?.trim()?.takeIf { name -> name.isNotEmpty() } }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                        .map { it.key }

                    _uiState.update {
                        it.copy(
                            isLoadingChapters = false,
                            chapters = rows,
                            groups = groups,
                            scanlators = scanlators,
                            resumeChapter = resume,
                            preferredScanlator = preferred,
                            autoContinue = scanlatorPrefs.autoContinue(mangaUrl),
                            error = if (rows.isEmpty()) context.getString(R.string.manga_error_no_chapters) else null,
                            errorCode = null
                        )
                    }
                    return@launch  // Başarılı — döngüden çık
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    lastError = e
                    if (attempt == 0) {
                        kotlinx.coroutines.delay(1_500L)  // Kısa bekleme sonrası tekrar dene
                    }
                }
            }
            // Her iki deneme de başarısız
            _uiState.update {
                it.copy(
                    isLoadingChapters = false,
                    error = lastError?.localizedMessage ?: lastError?.message
                        ?: context.getString(R.string.manga_error_chapters_load),
                    errorCode = SourceFailureClassifier.classifyCode(lastError)
                )
            }
        }
    }

    /**
     * "Oku / Devam Et" butonuna basıldığında açılacak bölümü döndürür.
     * - Devam edilecek bölüm varsa onu,
     * - Yoksa listedeki en eski (baştan) bölümü döndürür.
     */
    fun chapterToOpen(): MangaChapter? {
        val state = _uiState.value
        state.resumeChapter?.let { return it }
        // Bölüm listesi en yeniden eskiye sıralı geldiği için "baştan oku" = son eleman.
        return state.groups.lastOrNull()?.primary?.chapter
            ?: state.chapters.lastOrNull()?.chapter
    }

    // ─── Çevirmen (scanlator) yönetimi ───────────────────────────────────────

    // ─── Okundu / okunmadı işaretleme ────────────────────────────────────────

    /**
     * Bir bölüm sürümünü okundu/okunmadı olarak işaretler.
     *
     * Başka bir uygulamada okunmuş bölümler için "okundu" işaretlemek, ilerleme
     * yeniden yazılmadan listede doğru durumu görmeyi sağlar. Grup içindeki tek
     * bir çevirmen sürümü de işaretlenebilir.
     */
    fun toggleChapterCompleted(chapter: MangaChapter) {
        viewModelScope.launch {
            val existing = runCatching { progressDao.getProgress(chapter.url) }.getOrNull()
            val nowCompleted = !(existing?.isCompleted ?: false)
            val totalPages = existing?.totalPages ?: 0
            val newLastPage = if (nowCompleted) (totalPages - 1).coerceAtLeast(0) else 0

            runCatching {
                progressDao.upsert(
                    MangaChapterProgressEntity(
                        chapterUrl = chapter.url,
                        mangaUrl = mangaUrl,
                        chapterName = chapter.name,
                        lastPageIndex = newLastPage,
                        totalPages = totalPages,
                        isCompleted = nowCompleted,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            applyCompletion(chapter.url, nowCompleted, totalPages, newLastPage)
        }
    }

    /** Tüm görünür bölümleri (filtre uygulanmışsa yalnız onları) okundu işaretler. */
    fun markVisibleAsCompleted() {
        val targets = _uiState.value.visibleGroups
            .filter { !it.isCompleted }
            .map { it.primary.chapter }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            targets.forEach { chapter ->
                runCatching {
                    val existing = progressDao.getProgress(chapter.url)
                    progressDao.upsert(
                        MangaChapterProgressEntity(
                            chapterUrl = chapter.url,
                            mangaUrl = mangaUrl,
                            chapterName = chapter.name,
                            lastPageIndex = ((existing?.totalPages ?: 0) - 1).coerceAtLeast(0),
                            totalPages = existing?.totalPages ?: 0,
                            isCompleted = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            targets.forEach { chapter -> applyCompletion(chapter.url, true, 0, 0) }
        }
    }

    /** DB'ye yazdıktan sonra listeyi ağa çıkmadan günceller. */
    private fun applyCompletion(url: String, completed: Boolean, totalPages: Int, lastPageIndex: Int) {
        _uiState.update { state ->
            fun ChapterRow.patched(): ChapterRow {
                if (chapter.url != url) return this
                return copy(
                    isCompleted = completed,
                    isInProgress = !completed && lastPageIndex > 0,
                    lastPageIndex = lastPageIndex,
                    totalPages = if (totalPages > 0) totalPages else this.totalPages
                )
            }

            val chapters = state.chapters.map { it.patched() }
            val rowsByUrl = chapters.associateBy { it.chapter.url }
            val groups = state.groups.map { groupRow ->
                val variants = groupRow.variants.map { it.patched() }
                groupRow.copy(
                    variants = variants,
                    primary = rowsByUrl[groupRow.primary.chapter.url] ?: groupRow.primary,
                    isCompleted = variants.any { it.isCompleted },
                    isInProgress = variants.any { it.isInProgress }
                )
            }
            state.copy(chapters = chapters, groups = groups)
        }
    }

    /** Bölüm araması: numara ("42") veya ad parçası. */
    fun setChapterQuery(query: String) {
        _uiState.update { it.copy(chapterQuery = query) }
    }

    /** "Yalnızca okunmamış" filtresini aç/kapa. */
    fun toggleOnlyUnread() {
        _uiState.update { it.copy(onlyUnread = !it.onlyUnread) }
    }

    /** Tüm bölüm filtrelerini sıfırla. */
    fun clearChapterFilters() {
        _uiState.update { it.copy(chapterQuery = "", onlyUnread = false, scanlatorFilter = null) }
    }

    /** Çevirmen filtresi: null → tümü. */
    fun setScanlatorFilter(scanlator: String?) {
        _uiState.update { it.copy(scanlatorFilter = scanlator) }
    }

    /** Birleşik/ayrık görünüm arasında geçiş yapar. */
    fun toggleMergedScanlators() {
        _uiState.update { it.copy(mergedScanlators = !it.mergedScanlators) }
    }

    /** Grup satırına dokunulduğunda çevirmen listesini aç/kapa yapar. */
    fun toggleGroup(groupKey: String) {
        _uiState.update { state ->
            val expanded = state.expandedGroupKeys
            state.copy(
                expandedGroupKeys = if (groupKey in expanded) expanded - groupKey else expanded + groupKey
            )
        }
    }

    /** Kullanıcı belirli bir çevirmen sürümünü açtığında tercihi kaydeder. */
    fun rememberVariant(groupKey: String, chapter: MangaChapter) {
        val scanlator = chapter.scanlator?.takeIf { it.isNotBlank() }
        if (scanlator != null) {
            scanlatorPrefs.setPreferredScanlator(mangaUrl, scanlator)
            scanlatorPrefs.setGroupChoice(mangaUrl, groupKey, scanlator)
        }
    }

    fun clearPreferredScanlator() {
        scanlatorPrefs.clearPreferredScanlator(mangaUrl)
        _uiState.update { it.copy(preferredScanlator = null) }
    }

    fun setAutoContinue(enabled: Boolean) {
        scanlatorPrefs.setAutoContinue(mangaUrl, enabled)
        _uiState.update { it.copy(autoContinue = enabled) }
    }


    class Factory(
        private val context: Context,
        private val source: MangaSource,
        private val details: MangaDetails
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaDetailViewModel(context, source, details) as T
    }
}

/**
 * Tek bir bölüm satırı arama sorgusuyla eşleşiyor mu?
 *
 * Ad parçası **veya** numara: "42" araması 42, 42.0 ve 42.5 bölümlerini bulur.
 * (Birleşik ve açık görünüm aynı kuralı kullansın diye ortak yardımcı.)
 */
private fun chapterRowMatchesQuery(row: MangaDetailViewModel.ChapterRow, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (row.chapter.name.contains(q, ignoreCase = true)) return true
    val parsed = q.replace(',', '.').toFloatOrNull() ?: return false
    val number = MangaChapterGrouping.chapterNumberOf(row.chapter)
    return number > 0f && (number == parsed || number.toInt() == parsed.toInt())
}

