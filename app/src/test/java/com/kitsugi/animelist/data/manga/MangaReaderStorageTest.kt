package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MangaReaderStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun derivedCachePrunesOldestButAlwaysKeepsActivePage() {
        val directory = temporary.newFolder("render-cache")
        val oldest = File(directory, "old.webp").apply { writeBytes(ByteArray(6)); setLastModified(1_000L) }
        val middle = File(directory, "middle.webp").apply { writeBytes(ByteArray(5)); setLastModified(2_000L) }
        val active = File(directory, "active.webp").apply { writeBytes(ByteArray(4)); setLastModified(3_000L) }
        MangaReaderStorage.pruneDerivedCache(directory, maxBytes = 8, keep = active)
        assertFalse(oldest.exists())
        assertFalse(middle.exists())
        assertTrue(active.exists())
        assertEquals(4L, active.length())
    }

    @Test
    fun filesWithinBudgetArePreserved() {
        val directory = temporary.newFolder("small-cache")
        val first = File(directory, "first.webp").apply { writeBytes(ByteArray(4)) }
        val second = File(directory, "second.webp").apply { writeBytes(ByteArray(4)) }
        MangaReaderStorage.pruneDerivedCache(directory, maxBytes = 8)
        assertTrue(first.exists())
        assertTrue(second.exists())
    }
}
