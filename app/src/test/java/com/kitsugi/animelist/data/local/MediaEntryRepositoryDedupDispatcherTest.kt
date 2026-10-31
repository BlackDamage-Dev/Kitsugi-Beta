package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors

/**
 * Regression for the v2.4.387 Listem ANR: the caller may be Main.immediate, but the
 * snapshot/plan/commit path must hop to Default before touching the library.
 */
class MediaEntryRepositoryDedupDispatcherTest {
    @Test
    fun deduplicationDoesNotRunOnCallerThread() {
        val caller = Executors.newSingleThreadExecutor { r -> Thread(r, "listem-caller") }.asCoroutineDispatcher()
        val executor = caller as? java.io.Closeable
        try {
            runBlocking(caller) {
                val callerThread = Thread.currentThread().name
                val dao = mock<MediaEntryDao>()
                var snapshotThread: String? = null
                val rows = listOf(1, 2).map { id ->
                    MediaEntryEntity(
                        id = id, title = "Same", subtitle = "", type = MediaType.Anime.name,
                        status = WatchStatus.Planned.name, score = null, progress = 0, total = null,
                        isFavorite = false, isAdult = false, source = "anilist", malId = 100_000_042,
                        imageUrl = null, year = null, synopsis = null, startDate = null, endDate = null,
                        notes = null, tags = null, priority = null, isRepeating = false,
                        repeatCount = 0, repeatValue = 0, volumeProgress = 0,
                        isPrivate = false, isHiddenFromStatusLists = false
                    )
                }
                whenever(dao.getAll()).thenAnswer {
                    snapshotThread = Thread.currentThread().name
                    rows
                }
                val repository = MediaEntryRepository(dao)
                assertEquals(1, repository.deduplicateSameSourceDuplicates())
                assertNotEquals(callerThread, snapshotThread)
                assertTrue(snapshotThread?.startsWith("DefaultDispatcher") == true)
                verify(dao).deduplicateTransaction(any(), any(), any())
            }
        } finally {
            executor?.close()
        }
    }
}
