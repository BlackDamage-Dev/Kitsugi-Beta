package com.kitsugi.animelist.data.local

import com.google.gson.Gson
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import com.kitsugi.animelist.data.repository.StreamSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WatchHistoryManagerTest {
    private class BlockingPreferences : MemoryPreferences() {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        private val blockFirstRead = AtomicBoolean(true)
        override fun getString(key: String?, defValue: String?): String? {
            if (blockFirstRead.compareAndSet(true, false)) {
                readStarted.countDown()
                check(releaseRead.await(10, TimeUnit.SECONDS)) { "History read was not released" }
            }
            return super.getString(key, defValue)
        }
    }

    @Test fun operationsDuringInitialLoadReplayInOrderWithoutResurrectingDeletedEntries() = runBlocking {
        fun item(id: Int, position: Long) = WatchHistoryEntry(
            animeId = "anilist:$id", animeTitle = "Title $id", posterUrl = null,
            season = 1, episode = 1, aniListId = id, durationMs = 100_000, positionMs = position
        )
        val oldA = item(10, 10_000)
        val oldB = item(20, 20_000)
        val newC = item(30, 30_000)
        val prefs = BlockingPreferences()
        prefs.edit().putString("history_list", Gson().toJson(listOf(oldA, oldB))).apply()
        val store = WatchHistoryStore(prefs)
        WatchHistoryManager.init(store)
        assertTrue(prefs.readStarted.await(5, TimeUnit.SECONDS))
        try {
            WatchHistoryManager.remove(oldA.animeId, episode = 1, season = 1)
            WatchHistoryManager.record(newC)
            WatchHistoryManager.updateProgress(oldB.animeId, 1, 55_000, 100_000, 1)
            val next = StreamSource(
                addonName = "Provider", name = "Server", title = "Episode 2",
                url = "https://fresh.test/2", infoHash = null, fileIndex = null,
                addonId = "cs:provider", isCS = true,
                cs3PageUrl = "https://provider.test/show", cs3ApiName = "Provider"
            )
            WatchHistoryManager.recordSource(oldB.animeId, 1, 1, 2, next, "https://fresh.test/2")
        } finally {
            prefs.releaseRead.countDown()
        }
        WatchHistoryManager.awaitLoaded()
        val history = WatchHistoryManager.history.value
        assertFalse(history.any { it.animeId == oldA.animeId })
        assertTrue(history.any { it.animeId == newC.animeId })
        assertEquals(55_000L, history.single { it.animeId == oldB.animeId && it.episode == 1 }.positionMs)
        val next = history.single { it.animeId == oldB.animeId && it.episode == 2 }
        assertEquals(0L, next.positionMs)
        assertEquals("cs:provider", next.addonId)
        assertEquals("https://provider.test/show", next.cs3Url)

        WatchHistoryManager.updateProgress(oldB.animeId, 1, 73_000, 100_000, 1)
        WatchHistoryManager.clearAll()
        WatchHistoryManager.flush()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (store.getHistory().isNotEmpty() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(store.getHistory().isEmpty())
    }
}
