package com.kitsugi.animelist.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** SharedPreferences store with ordered, coalesced writes for player progress. */
class WatchHistoryStore internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
    )

    private val gson = Gson()
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var generation = 0L
    private var pendingJob: Job? = null
    private var pendingEntries: List<WatchHistoryEntry>? = null

    fun getHistory(): List<WatchHistoryEntry> {
        return try {
            val json = prefs.getString("history_list", null) ?: return emptyList()
            val type = object : TypeToken<List<WatchHistoryEntry?>>() {}.type
            val entries = gson.fromJson<List<WatchHistoryEntry?>>(json, type).orEmpty().filterNotNull()
                .filter { runCatching { it.animeId.isNotBlank() && it.animeTitle.isNotBlank() }.getOrDefault(false) }
            WatchHistoryRules.normalizeIds(entries).take(WatchHistoryRules.MAX_ENTRIES)
        } catch (e: Exception) {
            Log.w("WatchHistoryStore", "Could not read watch history", e)
            emptyList()
        }
    }

    /** Critical writes supersede both older IO jobs and any pending progress snapshot. */
    fun saveHistory(entries: List<WatchHistoryEntry>) {
        val snapshot = entries.toList()
        synchronized(lock) {
            val version = ++generation
            pendingJob?.cancel()
            pendingJob = null
            pendingEntries = null
            saveScope.launch { writeIfCurrent(snapshot, version) }
        }
    }

    /** At most one write per window; the most recent snapshot wins. */
    fun saveHistoryDebounced(entries: List<WatchHistoryEntry>, debounceMs: Long = DEBOUNCE_MS) {
        synchronized(lock) {
            generation++
            pendingEntries = entries.toList()
            if (pendingJob?.isActive == true) return
            pendingJob = saveScope.launch {
                delay(debounceMs)
                val work = synchronized(lock) {
                    val snapshot = pendingEntries
                    pendingEntries = null
                    pendingJob = null
                    snapshot?.let { it to generation }
                }
                if (work != null) writeIfCurrent(work.first, work.second)
            }
        }
    }

    /** Request an immediate IO write on player exit/backgrounding. */
    fun flush() {
        val work = synchronized(lock) {
            pendingJob?.cancel()
            pendingJob = null
            val snapshot = pendingEntries
            pendingEntries = null
            snapshot?.let { it to generation }
        } ?: return
        saveScope.launch { writeIfCurrent(work.first, work.second) }
    }

    private fun writeIfCurrent(entries: List<WatchHistoryEntry>, version: Long) {
        try {
            val json = gson.toJson(entries)
            synchronized(lock) {
                // An older serialization may finish after a newer delete, clear or progress update.
                if (version == generation) prefs.edit().putString("history_list", json).apply()
            }
        } catch (e: Exception) {
            Log.w("WatchHistoryStore", "Could not save watch history", e)
        }
    }

    companion object {
        const val DEBOUNCE_MS = 2500L
    }
}
