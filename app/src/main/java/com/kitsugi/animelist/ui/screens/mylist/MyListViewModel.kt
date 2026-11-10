package com.kitsugi.animelist.ui.screens.mylist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.IdentityCacheRevision
import com.kitsugi.animelist.data.local.MyListIdentityCrosswalk
import com.kitsugi.animelist.data.local.SubtitleCorrection
import com.kitsugi.animelist.model.EntryAutoRules
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydrator
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.kitsugi.animelist.data.auth.SourceSyncStatus
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MyListViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // ─── Sync mesajları için Channel ─────────────────────────────────────────
    // Screen, bu channel'dan mesajları toplayarak kullanıcıya gösterir.
    private val _syncMessages = Channel<String>(Channel.BUFFERED)
    val syncMessages = _syncMessages.receiveAsFlow()



    // Repository is used for list edits; the app root owns the shared Room observer.
    private val repository: MediaEntryRepository = run {
        val db = KitsugiDatabase.getDatabase(context)
        MediaEntryRepository(
            dao = db.mediaEntryDao(),
            pendingSyncDao = db.pendingSyncDao(),
            context = context,
            onExternalSyncMessage = { msg ->
                viewModelScope.launch { _syncMessages.send(msg) }
            }
        )
    }

    val entriesFlow: StateFlow<List<MediaEntry>> = repository.entriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    data class SyncHealth(
        val lines: List<String> = emptyList(),
        val pending: Int = 0,
        val isError: Boolean = false
    ) {
        val visible: Boolean get() = lines.isNotEmpty()
    }

    private val _syncHealth = MutableStateFlow(SyncHealth())
    val syncHealth: StateFlow<SyncHealth> = _syncHealth

    fun refreshSyncHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            val perSource = runCatching { repository.pendingSyncBySource() }.getOrDefault(emptyMap())
            val snapshots = SourceSyncStatus.snapshotAll(context)
            val lines = mutableListOf<String>()
            var totalPending = 0
            var anyError = false
            SourceSyncStatus.Source.entries.forEach { source ->
                val pending = perSource[source] ?: 0
                totalPending += pending
                val snapshot = snapshots[source] ?: SourceSyncStatus.Snapshot()
                val text = SourceSyncStatus.describe(
                    context = context,
                    source = source,
                    snapshot = snapshot,
                    pending = pending
                )
                if (text.isNotBlank()) {
                    lines += text
                    if (SourceSyncStatus.isError(snapshot.reason)) anyError = true
                }
            }
            _syncHealth.value = SyncHealth(
                lines = lines.take(4),
                pending = totalPending,
                isError = anyError
            )
        }
    }

    fun retryAllSourcesSyncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val sent = runCatching { repository.retryPendingSyncNow() }.getOrDefault(0)
            refreshSyncHealth()
            if (sent > 0) {
                runCatching {
                    _syncMessages.send(
                        context.getString(com.kitsugi.animelist.R.string.sync_retried_n, sent)
                    )
                }
            }
        }
    }

    internal var cachedPreparedSearch: PreparedMyListSearch? = null
    internal var cachedSearchResult: MyListSearchUiCache? = null

    init {
        refreshSyncHealth()
    }

    private val _library = MutableStateFlow<MyListLibraryState?>(null)
    internal val library: StateFlow<MyListLibraryState?> = _library.asStateFlow()
    internal var cachedPresentation: MyListPresentation? = null
    private var preparationJob: Job? = null
    private var requestedEntries: List<MediaEntry>? = null
    private var requestedAdultVisibility: Boolean? = null
    private var requestedIdentityRevision: Long? = null
    private var readyIdentityRevision: Long? = null
    private var repairsStarted = false

    /**
     * Warm the library as soon as the app's existing Room snapshot arrives, even while
     * another tab is visible. Only one cancellable preparation runs per snapshot.
     */
    internal fun prepareLibrary(entries: List<MediaEntry>, showAdultContent: Boolean) {
        val identityRevision = IdentityCacheRevision.current()
        val ready = _library.value?.let {
            it.sourceIndex.inputEntries === entries && it.sourceIndex.showAdultContent == showAdultContent &&
                it.combinedLibrary != null && readyIdentityRevision == identityRevision
        } == true
        if (requestedEntries === entries && requestedAdultVisibility == showAdultContent &&
            requestedIdentityRevision == identityRevision && (preparationJob?.isActive == true || ready)
        ) return
        requestedEntries = entries
        requestedAdultVisibility = showAdultContent
        requestedIdentityRevision = identityRevision
        preparationJob?.cancel()
        preparationJob = viewModelScope.launch(Dispatchers.Default) {
            val index = indexMyListSources(entries, showAdultContent)
            currentCoroutineContext().ensureActive()
            // Provider pages can render without waiting for cross-provider merging.
            withContext(Dispatchers.Main.immediate) {
                if (requestedEntries === entries && requestedAdultVisibility == showAdultContent &&
                    requestedIdentityRevision == identityRevision
                ) {
                    _library.value = MyListLibraryState(index, null)
                }
            }
            val extras = try {
                MyListIdentityCrosswalk.build(context, index.allEntries)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MyListViewModel", "Kimlik köprüsü okunamadı", error)
                emptyMap()
            }
            val job = currentCoroutineContext()[Job]
            val items = try {
                groupMyListEntries(index.allEntries, extras) { job?.ensureActive() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("MyListViewModel", "Birleşik liste hazırlanamadı", error)
                // Keep the library usable if an unexpected grouping error occurs.
                index.allEntries.map { entry ->
                    MyListLibraryItem(entry, listOfNotNull(sourceBadgeId(entry.source)))
                }
            }
            currentCoroutineContext().ensureActive()
            val combined = CombinedMyListLibrary(
                sourceIndex = index,
                items = items,
                entries = items.map { it.entry },
                sourceBadgesByEntryId = items.associate { it.entry.id to it.sourceIds }
            )
            withContext(Dispatchers.Main.immediate) {
                if (requestedEntries === entries && requestedAdultVisibility == showAdultContent &&
                    requestedIdentityRevision == identityRevision
                ) {
                    readyIdentityRevision = identityRevision
                    _library.value = MyListLibraryState(index, combined)
                    // Identity mappings can be learned without a Room row changing. Give
                    // the just-prepared snapshot a frame to appear before refreshing it.
                    if (IdentityCacheRevision.current() != identityRevision) {
                        viewModelScope.launch {
                            delay(250)
                            if (requestedEntries === entries && requestedAdultVisibility == showAdultContent &&
                                requestedIdentityRevision == identityRevision
                            ) {
                                prepareLibrary(entries, showAdultContent)
                            }
                        }
                    }
                }
            }
        }
    }

    /** One-time maintenance starts only on opening Listem, not during app warm-up. */
    internal fun startRepairs() {
        if (repairsStarted) return
        repairsStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            val dao = KitsugiDatabase.getDatabase(context).mediaEntryDao()
            suspend fun runRepair(name: String, block: suspend () -> Unit) {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    android.util.Log.w("MyListViewModel", "$name bakımı atlandı", error)
                }
            }
            runRepair("Simkl") { repairBrokenSimklSubtitles(dao) }
            runRepair("Kitsu") { KitsuIdentityMigration.runIfNeeded(context, dao) }
            runRepair("Yetişkin içerik") { AdultFlagBackfillMigration.runIfNeeded(context, dao) }
        }
    }

    /**
     * Bangumi satırlarının Latin/İngilizce ad tamamlama taramasını (yeniden) başlatır.
     *
     * @param titleLanguage güncel başlık dili; `null` ise ayardan okunur. Dil yalnızca
     *        İngilizce zenginleştirme kademesini etkiler — CJK gösterimi düzelten LATIN
     *        kademesi her dilde çalışır, böylece dil sonradan değişse bile veri hazırdır.
     *        Taramalar tamamlayıcıdaki kilitle sıraya girer; üst üste çağrı güvenlidir.
     */
    fun refreshBangumiTitles(titleLanguage: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val language = titleLanguage
                    ?: runCatching { SettingsDataStore(context).settingsFlow.first().titleLanguage }
                        .getOrDefault("ENGLISH")
                BangumiLibraryTitleHydrator.hydrate(KitsugiDatabase.getDatabase(context), language)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MyListViewModel", "Bangumi başlık tamamlama atlandı: ${error.message}")
            }
        }
    }

    /**
     * Eski Simkl importlarından kalan bozuk subtitle'ları düzeltir.
     * Örn: "movies.uppercase() â€¢ 2011" → "Film • 2011"
     */
    private suspend fun repairBrokenSimklSubtitles(
        dao: com.kitsugi.animelist.data.local.MediaEntryDao
    ) {
        val brokenPatterns = listOf("uppercase()", "â€¢", "â€")
        val all = dao.getAll()
        val broken = all.filter { entity ->
            entity.source == "simkl" &&
                brokenPatterns.any { entity.subtitle.contains(it) }
        }
        val repaired = broken.map { entity ->
            val typeLabel = when (entity.type) {
                "Movie" -> "Film"
                "TvShow" -> "Dizi"
                "Anime" -> "Anime"
                else -> "Manga"
            }
            val fixedSubtitle = if (entity.year != null && entity.year > 0) {
                "$typeLabel • ${entity.year}"
            } else {
                typeLabel
            }
            SubtitleCorrection(entity.id, entity.subtitle, fixedSubtitle)
        }
        if (repaired.isNotEmpty()) dao.repairSimklSubtitles(repaired)
    }

    fun incrementEntryProgress(entry: MediaEntry) {
        val nextProgress = if (entry.total != null) {
            (entry.progress + 1).coerceAtMost(entry.total)
        } else {
            entry.progress + 1
        }

        val nextStatus = if (entry.total != null && nextProgress >= entry.total) {
            WatchStatus.Completed
        } else if (entry.status == WatchStatus.Planned) {
            WatchStatus.Watching
        } else {
            entry.status
        }

        // Ortak kural motoru (EntryAutoRules): "İzlemeye başla" anında başlangıç tarihi
        // bugüne dolar, son bölüme gelindiğinde bitiş tarihi yazılır. Listem ekranındaki
        // +1 kısayolu da artık düzenleyiciyle birebir aynı davranıyor.
        val auto = EntryAutoRules.resolveOnStatusChange(
            status = nextStatus,
            startDate = entry.startDate,
            endDate = entry.endDate,
            progress = nextProgress,
            total = entry.total,
            statusChanged = nextStatus != entry.status
        )

        val updatedEntry = entry.copy(
            progress = auto.progress,
            status = nextStatus,
            startDate = auto.startDate,
            endDate = auto.endDate
        )

        viewModelScope.launch {
            repository.update(updatedEntry)
        }
    }

    fun insertEntry(entry: MediaEntry) {
        viewModelScope.launch {
            repository.insert(entry)
        }
    }

    /**
     * Bangumi liste ekranından ekleme/düzenleme: diğer kaynaklarla aynı hat kullanılır —
     * kayıt önce yerele yazılır, sonra canlı eşitleme Bangumi dahil altı kaynağa birden
     * yayılır (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi).
     *
     * Eski akış önce Bangumi'ye yazıyor, başarısız olursa kaydı yerele bile kaydetmiyordu:
     * kullanıcının seçtiği durum/puan/tarih/bölüm kayboluyor ve hiçbir kaynağa gidilmiyordu.
     * Artık senkron sonucu repository geri çağrısıyla (_syncMessages) bildirilir; başarısız
     * yazımlar bekleyen senkron kuyruğunda yeniden denenir.
     */
    fun saveBangumiEntry(entry: MediaEntry, isNew: Boolean) {
        viewModelScope.launch {
            val persisted = entry.copy(source = BangumiIdNamespace.SOURCE)
            if (isNew) {
                repository.insert(persisted.copy(id = 0), syncExternal = true)
            } else {
                repository.update(persisted, syncExternal = true)
            }
        }
    }

    fun updateEntry(entry: MediaEntry, advancedScores: List<Double>? = null) {
        viewModelScope.launch {
            repository.update(entry, advancedScores = advancedScores)
        }
    }

    fun deleteEntryById(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}
