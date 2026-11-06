package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class PlainTextNovelReaderTest {
    @Test fun normalizesLinesAndKeepsBlankLines() {
        assertEquals(listOf("first", "", "second", "third"), splitNovelText("first\r\n\r\nsecond\rthird"))
    }

    @Test fun doesNotSplitSurrogatePairs() {
        val text = "x".repeat(2047) + "😀" + "z"
        assertEquals(listOf("x".repeat(2047), "😀z"), splitNovelText(text))
    }

    @Test fun boundedInputRejectsOversizedFile() {
        assertArrayEquals(byteArrayOf(1, 2), readBoundedNovel(byteArrayOf(1, 2).inputStream(), 2))
        assertThrows(IllegalArgumentException::class.java) { readBoundedNovel(byteArrayOf(1, 2, 3).inputStream(), 2) }
    }

    @Test fun textSearchWrapsAndIgnoresCase() {
        val lines = listOf("one", "TWO", "three two")
        assertEquals(2, findNovelText(lines, "two", 1))
        assertEquals(1, findNovelText(lines, "two", 2))
        assertEquals(2, findNovelText(lines, "two", 0, forward = false))
        assertEquals(1, findNovelText(lines, "two", 2, forward = false))
        assertNull(findNovelText(lines, "absent", 0))
        assertNull(findNovelText(lines, " ", 0))
    }

    @Test fun seriesIdsDoNotConfuseParts() {
        assertNotEquals(readerSeriesId("ab", "c"), readerSeriesId("a", "bc"))
        assertEquals(readerSeriesId("source", "series"), readerSeriesId("source", "series"))
    }
}
