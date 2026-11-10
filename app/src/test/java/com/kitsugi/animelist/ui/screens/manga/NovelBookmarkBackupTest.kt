package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class NovelBookmarkBackupTest {
    @Test fun roundTripsAndPreservesExistingNotesOnConflict() {
        val existing = listOf(NovelBookmark(1, 2, "original"))
        val incoming = listOf(NovelBookmark(1, 2, "different"), NovelBookmark(3, 4, "Örnek 😀"))
        val decoded = decodeNovelBookmarkBackup(encodeNovelBookmarkBackup("epub", "abc", incoming), "epub", "abc")
        assertEquals(incoming, decoded)
        assertEquals(listOf(existing[0], incoming[1]), mergeNovelBookmarks(existing, decoded))
    }
    @Test fun rejectsWrongDocumentKindAndCorruption() {
        val backup = encodeNovelBookmarkBackup("text", "key", listOf(NovelBookmark(1, 0)))
        assertThrows(IllegalArgumentException::class.java) { decodeNovelBookmarkBackup(backup, "epub", "key") }
        assertThrows(IllegalArgumentException::class.java) { decodeNovelBookmarkBackup(backup, "text", "other") }
        assertThrows(IllegalArgumentException::class.java) { decodeNovelBookmarkBackup(backup + "garbage", "text", "key") }
    }
    @Test fun neverEvictsAtCapacity() {
        val existing = (0 until 200).map { NovelBookmark(it, 0) }
        assertEquals(existing, mergeNovelBookmarks(existing, listOf(NovelBookmark(201, 0))))
    }
}
