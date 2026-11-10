package com.kitsugi.animelist.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MangaReadHistoryEntity
import com.kitsugi.animelist.data.local.WatchHistoryManager
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Geçmiş sayfasının üst sekmeleri. */
enum class HistoryTab { All, Watched, Read }

/**
 * **Geçmiş** sayfasının ViewModel'i.
 *
 * ## Neden ViewModel
 * Önceki sürüm okuma geçmişini doğrudan Composable içinde `LaunchedEffect` + Room ile
 * okuyordu. Sayfa `AnimatedContent`/`SaveableStateProvider` geçişlerinde yeniden kompoze
 * edildiğinde liste her seferinde yeniden yükleniyor (boş → dolu titreşimi) ve silme
 * sonrası yenileme elle tetikleniyordu. Burada:
 *  - izleme geçmişi [WatchHistoryManager.history] akışından reaktif olarak gelir,
 *  - okuma geçmişi Room'dan **bir kez** okunur ve silmelerde yerinde güncellenir,
 *  - tek bir [HistoryUiState] ile sekmeler, sayaçlar ve yükleme durumu tutarlı kalır.
 *
 * Okuma geçmişi için ayrı bir Flow yok: tablo yalnızca okuyucu tarafından yazıldığından
 * `refresh()` çağrısı yeterlidir (geçmiş ekranı açılırken ve sekme değişiminde).
 */
class HistoryViewModel(
    private val context: Context
) : ViewModel() {

    /** Geçmiş listesinde gösterilecek okuma kaydı. */
    data class ReadRow(
        val entity: MangaReadHistoryEntity,
        val isInProgress: Boolean
    )

    data class UiState(
        val watched: List<WatchHistoryEntry> = emptyList(),
        val read: List<ReadRow> = emptyList(),
        val selectedTab: HistoryTab = HistoryTab.All,
        val isLoadingRead: Boolean = true,
        val reloadTick: Int = 0
    ) {
        val isWatchedEmpty: Boolean get() = watched.isEmpty()
        val isReadEmpty: Boolean get() = read.isEmpty()
        val isEmpty: Boolean get() = watched.isEmpty() && read.isEmpty() && !isLoadingRead
    }

    private val dao = KitsugiDatabase.getDatabase(context.applicationContext).mangaReadHistoryDao()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeWatched()
        refreshRead()
    }

    /** İzleme geçmişi: oynatıcı yazdıkça liste kendini günceller. */
    private fun observeWatched() {
        viewModelScope.launch {
            WatchHistoryManager.history.collect { entries ->
                _uiState.update { it.copy(watched = entries) }
            }
        }
    }

    /** Okuma geçmişini Room'dan yeniden okur (IO). */
    fun refreshRead() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRead = it.read.isEmpty()) }
            val rows = withContext(Dispatchers.IO) {
                runCatching {
                    dao.getAll().map { entity ->
                        ReadRow(
                            entity = entity,
                            isInProgress = !entity.isCompleted && entity.lastPageIndex > 0
                        )
                    }
                }.getOrDefault(emptyList())
            }
            _uiState.update { it.copy(read = rows, isLoadingRead = false) }
        }
    }

    fun selectTab(tab: HistoryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /** Tek bir okuma kaydını siler (liste yeniden yüklenmeden yerinde güncellenir). */
    fun removeRead(row: ReadRow) {
        viewModelScope.launch {
            val id = row.entity.id
            withContext(Dispatchers.IO) {
                runCatching { dao.remove(id) }
            }
            _uiState.update { state -> state.copy(read = state.read.filterNot { it.entity.id == id }) }
        }
    }

    /**
     * Seçili sekmeye göre geçmişi temizler.
     * - All / Watched → izleme geçmişi
     * - All / Read    → okuma geçmişi
     */
    fun clear(tab: HistoryTab) {
        viewModelScope.launch {
            if (tab != HistoryTab.Read) {
                WatchHistoryManager.clearAll()
            }
            if (tab != HistoryTab.Watched) {
                withContext(Dispatchers.IO) { runCatching { dao.clearAll() } }
                _uiState.update { it.copy(read = emptyList()) }
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(context) as T
    }
}
