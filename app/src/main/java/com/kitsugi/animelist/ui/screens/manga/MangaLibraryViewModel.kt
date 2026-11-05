package com.kitsugi.animelist.ui.screens.manga

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MangaLibraryEntity
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.download.MangaChapterDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manga kitaplığı ekranının ViewModel'i.
 *
 * Sorumluluklar:
 * - Kitaplıktaki serileri (Room) okumak ve kategori/sıralama ile filtrelemek
 * - Kategori atamak, kitaplıktan çıkarmak
 * - İndirilmiş bölüm sayısını ve okuma ilerlemesini göstermek
 *
 * ## Neden kaynak nesnesi tutulmuyor?
 * `MangaSource` bir interface; Room'da saklanamaz. Kitaplık satırında **kaynak adı**
 * saklanır ve ekran açıldığında `availableSources` üzerinden yeniden çözülür. Kaynak
 * kaldırılmışsa satır yine görünür ama "kaynak yok" olarak işaretlenir (Mihon'daki
 * "source unavailable" davranışı) — kullanıcı kitaplığını kaybetmez.
 */
class MangaLibraryViewModel(
    private val context: Context,
    private val availableSources: () -> List<MangaSource>
) : ViewModel() {

    private val db = KitsugiDatabase.getDatabase(context)
    private val dao = db.mangaLibraryDao()
    private val downloader = MangaChapterDownloader(context)

    /** Sıralama seçenekleri — Mihon kitaplığındaki 9 sıralamanın en çok kullanılanları. */
    enum class SortMode {
        /** Son eklenen/güncellenen önce. Varsayılan. */
        RecentlyUpdated,
        /** Alfabetik A → Z. */
        TitleAsc,
        /** Alfabetik Z → A. */
        TitleDesc,
        /** En çok okunmamış bölümü olan önce. */
        UnreadDesc,
        /** Son okunan bölüm adına göre (devam edilenler önce). */
        LastRead
    }

    data class LibraryRow(
        val entity: MangaLibraryEntity,
        /** Kaynak hâlâ kurulu mu? Değilse satır açılamaz. */
        val sourceAvailable: Boolean,
        /** İndirilmiş bölüm sayısı (çevrimdışı okunabilirlik göstergesi). */
        val downloadedChapters: Int
    )

    data class UiState(
        val rows: List<LibraryRow> = emptyList(),
        val categories: List<String> = emptyList(),
        val selectedCategory: String? = null,
        val sortMode: SortMode = SortMode.RecentlyUpdated,
        val query: String = "",
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val sources = availableSources()
            val entities = withContext(Dispatchers.IO) { dao.getAll() }
            val categories = withContext(Dispatchers.IO) { dao.categories() }
            val rows = withContext(Dispatchers.IO) {
                entities.map { entity ->
                    val source = sources.firstOrNull { it.name == entity.sourceName }
                    LibraryRow(
                        entity = entity,
                        sourceAvailable = source != null,
                        downloadedChapters = if (source != null) {
                            runCatching {
                                downloader.downloadedCount(source, entity.title, entity.url)
                            }.getOrDefault(0)
                        } else 0
                    )
                }
            }
            _uiState.update {
                it.copy(rows = rows, categories = categories, isLoading = false)
            }
        }
    }

    fun setCategoryFilter(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setSortMode(mode: SortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    /** Kitaplığa ekler veya var olan satırı günceller (detay ekranındaki ★ düğmesi). */
    fun addToLibrary(source: MangaSource, details: MangaDetails, category: String = "") {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsert(
                    MangaLibraryEntity(
                        id = MangaLibraryEntity.key(source.name, details.url),
                        sourceName = source.name,
                        url = details.url,
                        title = details.title,
                        thumbnailUrl = details.thumbnailUrl,
                        author = details.author,
                        category = category,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            reload()
        }
    }

    fun removeFromLibrary(row: LibraryRow) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.delete(row.entity.id) }
            reload()
        }
    }

    fun setCategory(row: LibraryRow, category: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.setCategory(row.entity.id, category) }
            reload()
        }
    }

    /**
     * Kitaplık kartındaki ilerlemeyi (okunan/toplam bölüm) günceller.
     *
     * Detay ekranı bölüm listesini yükledikten sonra çağırır; böylece kitaplık ekranı
     * her açılışta tüm kaynaklara ağ isteği atmaz (Mihon'daki "son tarama" önbelleği).
     */
    fun updateProgress(sourceName: String, mangaUrl: String, total: Int, read: Int, lastChapterName: String?, lastChapterUrl: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = dao.get(sourceName, mangaUrl) ?: return@withContext
                dao.upsert(
                    existing.copy(
                        totalChapters = total,
                        readChapters = read,
                        lastChapterName = lastChapterName ?: existing.lastChapterName,
                        lastChapterUrl = lastChapterUrl ?: existing.lastChapterUrl,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /** Görünür satırlar: kategori + arama + sıralama uygulanmış hâlde. */
    fun visibleRows(state: UiState = _uiState.value): List<LibraryRow> {
        val needle = state.query.trim().lowercase()
        val filtered = state.rows.filter { row ->
            (state.selectedCategory == null ||
                row.entity.category.equals(state.selectedCategory, ignoreCase = true)) &&
                (needle.isEmpty() ||
                    row.entity.title.lowercase().contains(needle) ||
                    row.entity.author?.lowercase()?.contains(needle) == true)
        }
        return when (state.sortMode) {
            SortMode.RecentlyUpdated -> filtered.sortedByDescending { it.entity.updatedAt }
            SortMode.TitleAsc -> filtered.sortedBy { it.entity.title.lowercase() }
            SortMode.TitleDesc -> filtered.sortedByDescending { it.entity.title.lowercase() }
            SortMode.UnreadDesc -> filtered.sortedByDescending {
                (it.entity.totalChapters - it.entity.readChapters).coerceAtLeast(0)
            }
            SortMode.LastRead -> filtered.sortedByDescending { it.entity.lastChapterName != null }
        }
    }

    class Factory(
        private val context: Context,
        private val sources: () -> List<MangaSource>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaLibraryViewModel(context, sources) as T
    }
}
