package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class NovelSearchHighlightTest {
    @Test fun findsEveryNonOverlappingMatch() {
        assertEquals(listOf(0..2, 4..6), novelSearchRanges("One ONE 😀", "one"))
        assertEquals(listOf(0..1, 2..3), novelSearchRanges("aaaaa", "aa"))
        assertEquals(listOf(2..3), novelSearchRanges("😀😀", "😀").drop(1))
    }
    @Test fun ignoresEmptyAndOverlongSearch() {
        assertTrue(novelSearchRanges("abc", " ").isEmpty())
        assertTrue(novelSearchRanges("abc", "x".repeat(101)).isEmpty())
        assertTrue(novelSearchRanges("", "a").isEmpty())
    }
}
