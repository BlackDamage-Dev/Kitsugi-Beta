package com.kitsugi.animelist.data.local

import android.content.SharedPreferences
import com.google.gson.Gson
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Exercises the real serializer, write generation and debounce with in-memory preferences. */
class WatchHistoryStoreTest {
    private fun entry(position: Long = 0) = WatchHistoryEntry(
        animeId = "anilist:42", animeTitle = "Example", posterUrl = null,
        season = 1, episode = 1, aniListId = 42, durationMs = 100_000,
        positionMs = position, watchedAtMs = 1
    )

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for $label")
    }

    @Test fun clearSupersedesAnOlderDelayedProgressWrite() {
        val store = WatchHistoryStore(MemoryPreferences())
        store.saveHistory(listOf(entry(10_000)))
        await("initial record") { store.getHistory().singleOrNull()?.positionMs == 10_000L }
        store.saveHistoryDebounced(listOf(entry(25_000)), debounceMs = 100)
        store.saveHistory(emptyList())
        await("clear") { store.getHistory().isEmpty() }
        Thread.sleep(250)
        assertTrue(store.getHistory().isEmpty())
    }

    @Test fun lastImmediateWriteWinsEvenWhenSerializationFinishesOutOfOrder() {
        val store = WatchHistoryStore(MemoryPreferences())
        repeat(100) { store.saveHistory(listOf(entry(it.toLong()))) }
        await("last write") { store.getHistory().singleOrNull()?.positionMs == 99L }
        Thread.sleep(100)
        assertEquals(99L, store.getHistory().single().positionMs)
    }

    @Test fun flushWritesTheLatestDebouncedProgressImmediately() {
        val store = WatchHistoryStore(MemoryPreferences())
        store.saveHistoryDebounced(listOf(entry(10_000)), debounceMs = 60_000)
        store.saveHistoryDebounced(listOf(entry(70_000)), debounceMs = 60_000)
        store.flush()
        await("flushed progress") { store.getHistory().singleOrNull()?.positionMs == 70_000L }
        assertEquals(70_000L, store.getHistory().single().positionMs)
    }

    @Test fun persistedEntriesAndLegacyNumericCollisionsSurviveReload() {
        val prefs = MemoryPreferences()
        val a = entry(12_000).copy(animeId = "7", animeTitle = "Ani", aniListId = 7)
        val b = entry(34_000).copy(animeId = "7", animeTitle = "Mal", aniListId = null, malId = 7)
        prefs.edit().putString("history_list", Gson().toJson(listOf(a, b))).apply()
        val loaded = WatchHistoryStore(prefs).getHistory()
        assertEquals(2, loaded.size)
        assertEquals(listOf("7", "mal:7"), loaded.map { it.animeId })
        assertEquals(listOf(12_000L, 34_000L), loaded.map { it.positionMs })
    }
}

/** Implements the Android interface without requiring an Android runtime or mocking framework. */
internal open class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any>()
    override fun getAll(): MutableMap<String, *> = synchronized(values) { values.toMutableMap() }
    override fun getString(key: String?, defValue: String?): String? = synchronized(values) { values[key] as? String ?: defValue }
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(values) { (values[key] as? Set<String>)?.toMutableSet() ?: defValues }
    override fun getInt(key: String?, defValue: Int): Int = synchronized(values) { values[key] as? Int ?: defValue }
    override fun getLong(key: String?, defValue: Long): Long = synchronized(values) { values[key] as? Long ?: defValue }
    override fun getFloat(key: String?, defValue: Float): Float = synchronized(values) { values[key] as? Float ?: defValue }
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(values) { values[key] as? Boolean ?: defValue }
    override fun contains(key: String?): Boolean = synchronized(values) { values.containsKey(key) }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = set(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = set(key, values?.toSet())
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = set(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = set(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = set(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = set(key, value)
        override fun remove(key: String?): SharedPreferences.Editor = set(key, null)
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        private fun set(key: String?, value: Any?): SharedPreferences.Editor {
            requireNotNull(key)
            updates[key] = value
            return this
        }
        override fun commit(): Boolean {
            synchronized(values) {
                if (clear) values.clear()
                for ((key, value) in updates) if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }
        override fun apply() { commit() }
    }
}
