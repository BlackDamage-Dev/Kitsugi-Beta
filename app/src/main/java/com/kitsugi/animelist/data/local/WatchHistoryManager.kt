package com.kitsugi.animelist.data.local

import android.content.Context
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One authoritative history for the stream picker, player and history screen. */
object WatchHistoryManager {
    private lateinit var store: WatchHistoryStore
    private val lock = Any()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _history = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
    val history: StateFlow<List<WatchHistoryEntry>> = _history.asStateFlow()
    private val loaded = MutableStateFlow(false)
    private var initializing = false
    private val beforeLoad = mutableListOf<(List<WatchHistoryEntry>) -> List<WatchHistoryEntry>>()

    fun init(context: Context) = init(WatchHistoryStore(context.applicationContext))

    internal fun init(historyStore: WatchHistoryStore) {
        synchronized(lock) {
            if (initializing) return
            initializing = true
            store = historyStore
        }
        ioScope.launch {
            val diskHistory = store.getHistory()
            synchronized(lock) {
                // Replay operations in order, including deletes/clear. A simple merge would
                // resurrect deleted records and could overwrite history during startup.
                val hadChanges = beforeLoad.isNotEmpty()
                val result = beforeLoad.fold(diskHistory) { list, operation -> operation(list) }
                beforeLoad.clear()
                _history.value = result
                loaded.value = true
                if (hadChanges) store.saveHistory(result)
            }
        }
    }

    suspend fun awaitLoaded() { loaded.first { it } }

    fun find(animeId: String, season: Int, episode: Int): WatchHistoryEntry? =
        history.value.firstOrNull { it.animeId == animeId && it.season == season && it.episode == episode }

    fun findMatching(entry: WatchHistoryEntry): WatchHistoryEntry? =
        WatchHistoryRules.findMatching(history.value, entry)

    /** Records a source while retaining the prior position and legacy ID, if any. */
    fun record(entry: WatchHistoryEntry): WatchHistoryEntry = synchronized(lock) {
        val operation: (List<WatchHistoryEntry>) -> List<WatchHistoryEntry> = { WatchHistoryRules.record(it, entry) }
        mutateLocked(immediate = true, operation = operation)
        _history.value.first()
    }

    /** Update the actual source on player switches, or create a record for the next episode. */
    fun recordSource(
        animeId: String,
        season: Int,
        previousEpisode: Int,
        episode: Int,
        source: com.kitsugi.animelist.data.repository.StreamSource,
        resolvedUrl: String
    ) {
        val now = System.currentTimeMillis()
        mutate(immediate = true) { history ->
            val target = history.firstOrNull { it.animeId == animeId && it.season == season && it.episode == episode }
            val previous = history.firstOrNull { it.animeId == animeId && it.season == season && it.episode == previousEpisode }
            val template = target ?: previous?.copy(episode = episode, positionMs = 0L, durationMs = 0L)
            if (template == null) history else WatchHistoryRules.record(
                history, WatchHistorySource.withSource(template.copy(watchedAtMs = now), source, resolvedUrl)
            )
        }
    }

    fun updateProgress(animeId: String, episode: Int, positionMs: Long, durationMs: Long, season: Int = 1) {
        val now = System.currentTimeMillis()
        mutate(immediate = false) {
            WatchHistoryRules.updateProgress(it, animeId, season, episode, positionMs, durationMs, now)
        }
    }

    fun updateStreamUrl(
        animeId: String,
        episode: Int,
        streamUrl: String,
        streamHeaders: Map<String, String>?,
        season: Int = 1
    ) {
        mutate(immediate = true) { history ->
            history.map {
                if (it.animeId == animeId && it.season == season && it.episode == episode)
                    it.copy(streamUrl = streamUrl, streamHeaders = streamHeaders)
                else it
            }
        }
    }

    fun remove(animeId: String, episode: Int, season: Int = 1) {
        mutate(immediate = true) { WatchHistoryRules.remove(it, animeId, season, episode) }
    }

    fun removeMany(keys: Collection<Pair<String, Int>>) {
        if (keys.isEmpty()) return
        val keySet = keys.toHashSet()
        mutate(immediate = true) { current ->
            current.filterNot { keySet.contains(it.animeId to it.episode) }
        }
    }

    fun removeSeries(animeId: String) {
        mutate(immediate = true) { current ->
            current.filterNot { it.animeId == animeId }
        }
    }

    fun restore(entries: List<WatchHistoryEntry>) {
        if (entries.isEmpty()) return
        mutate(immediate = true) { current ->
            val existing = current.mapTo(HashSet()) { it.animeId to it.episode }
            val restored = entries.filterNot { existing.contains(it.animeId to it.episode) }
            if (restored.isEmpty()) current
            else (current + restored)
                .sortedByDescending { it.watchedAtMs }
                .take(WatchHistoryRules.MAX_ENTRIES)
        }
    }

    fun resetPosition(animeId: String, episode: Int, season: Int = 1) {
        mutate(immediate = true) { current ->
            val index = current.indexOfFirst { it.animeId == animeId && it.episode == episode && it.season == season }
            if (index == -1 || current[index].positionMs == 0L) current
            else {
                val list = current.toMutableList()
                list[index] = list[index].copy(positionMs = 0L)
                list
            }
        }
    }

    fun clearAll() { mutate(immediate = true) { emptyList() } }

    fun flush() {
        synchronized(lock) {
            if (::store.isInitialized) store.flush()
        }
    }

    private fun mutate(immediate: Boolean, operation: (List<WatchHistoryEntry>) -> List<WatchHistoryEntry>) {
        synchronized(lock) { mutateLocked(immediate, operation) }
    }

    private fun mutateLocked(immediate: Boolean, operation: (List<WatchHistoryEntry>) -> List<WatchHistoryEntry>) {
        if (!loaded.value) beforeLoad.add(operation)
        val updated = operation(_history.value)
        if (updated == _history.value) return
        _history.value = updated
        if (loaded.value && ::store.isInitialized) {
            if (immediate) store.saveHistory(updated) else store.saveHistoryDebounced(updated)
        }
    }
}
