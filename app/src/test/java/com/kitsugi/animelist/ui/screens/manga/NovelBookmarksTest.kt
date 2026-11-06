package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class NovelBookmarksTest {
    @Test fun roundTripDeduplicatesAndPreservesOffsets() {
        val marks = listOf(NovelBookmark(4, 100), NovelBookmark(0, 0), NovelBookmark(4, 100))
        assertEquals(listOf(NovelBookmark(4, 100), NovelBookmark(0, 0)), decodeNovelBookmarks(encodeNovelBookmarks(marks)))
    }
    @Test fun malformedAndNegativeDataIsIgnored() {
        assertEquals(listOf(NovelBookmark(2, 10)), decodeNovelBookmarks("2:10,-1:0,2:-4,x:0,1:2:3,999999999999:1"))
        assertTrue(decodeNovelBookmarks("x".repeat(12_001)).isEmpty())
    }
    @Test fun limitDoesNotEvictAndDuplicateDoesNotAdd() {
        val marks = (0 until 200).map { NovelBookmark(it, 0) }
        assertEquals(marks, addNovelBookmark(marks, NovelBookmark(999, 0)))
        assertEquals(marks, addNovelBookmark(marks, marks.first()))
        assertEquals(200, decodeNovelBookmarks((0..250).joinToString(",") { "$it:0" }).size)
    }
    @Test fun notesRoundTripAndLegacyMigration() {
        val marks = listOf(NovelBookmark(3, 90, "Merhaba, dünya: 😀\nnot"), NovelBookmark(5, 0))
        assertEquals(marks, decodeNovelBookmarks(encodeNovelBookmarks(marks)))
        assertEquals(listOf(NovelBookmark(3, 90), NovelBookmark(5, 0)), decodeNovelBookmarks("3:90,5:0"))
        assertEquals(marks, updateNovelBookmarkNote(listOf(NovelBookmark(3, 90), NovelBookmark(5, 0)), NovelBookmark(3, 90), marks.first().note))
        assertEquals(marks, updateNovelBookmarkNote(marks, marks.first(), "x".repeat(501)))
        assertEquals(marks, addNovelBookmark(marks, NovelBookmark(3, 90, "duplicate position")))
        assertTrue(decodeNovelBookmarks("v2|3:90:!!!").isEmpty())
    }
    @Test fun exportIncludesLocationsAndUnicodeNotes() {
        val marks = listOf(NovelBookmark(1, 2, "İlk not 😀\nikinci satır"), NovelBookmark(2, 0))
        val text = buildNovelBookmarkExport("Yer imleri", marks) { "Bölüm ${it.index} / ${it.offset}" }
        assertEquals("Yer imleri\n\nBölüm 1 / 2\nİlk not 😀\nikinci satır\n\nBölüm 2 / 0\n", text)
    }
}
