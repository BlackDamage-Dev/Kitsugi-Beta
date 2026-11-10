package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class ReaderSpeechTextTest {
    @Test fun splitsAtWhitespaceAndKeepsWords() {
        val chunks = splitSpeechText("hello world this is text", 10)
        assertEquals("hello world this is text", chunks.joinToString(" "))
        assertTrue(chunks.all { it.length <= 10 })
    }

    @Test fun preservesSurrogatePairsAtBoundaries() {
        val text = "abc😀def😀ghi"
        val chunks = splitSpeechText(text, 4)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.none { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
    }

    @Test fun boundsLongUnbrokenTextAndEmptyInput() {
        assertTrue(splitSpeechText(" \n ").isEmpty())
        val chunks = splitSpeechText("x".repeat(10_001), 2000)
        assertEquals(6, chunks.size)
        assertTrue(chunks.all { it.length <= 2000 })
    }
    @Test fun nextChapterHonorsOptionAndBookBounds() {
        assertEquals(1, nextSpeechChapter(0, 3, true))
        assertEquals(2, nextSpeechChapter(1, 3, true))
        assertNull(nextSpeechChapter(2, 3, true))
        assertNull(nextSpeechChapter(0, 3, false))
        assertNull(nextSpeechChapter(-1, 3, true))
        assertNull(nextSpeechChapter(0, 0, true))
        assertNull(nextSpeechChapter(0, Int.MIN_VALUE, true))
    }
    @Test fun cyclesBoundedVoicePitch() {
        assertEquals(1.2f, nextNovelSpeechPitch(1f))
        assertEquals(1.5f, nextNovelSpeechPitch(1.2f))
        assertEquals(0.8f, nextNovelSpeechPitch(1.5f))
        assertEquals(1f, nextNovelSpeechPitch(0.8f))
        assertEquals(0.8f, nextNovelSpeechPitch(Float.NaN))
    }
    @Test fun timerCyclesAndUsesMonotonicRemainingTime() {
        assertEquals(listOf(5, 15, 30, 60, 0), listOf(0, 5, 15, 30, 60).map(::nextSpeechTimerMinutes))
        assertEquals(0L, speechTimerRemainingMillis(10, 11))
        assertEquals(10L, speechTimerRemainingMillis(20, 10))
    }
    @Test fun skipsEmptySegmentsAndStopsAtChapterEdges() {
        val lines = listOf("first", " ", "second", "", "third")
        assertEquals(2, adjacentSpeechSegment(lines, 0, true))
        assertEquals(0, adjacentSpeechSegment(lines, 2, false))
        assertEquals(4, adjacentSpeechSegment(lines, lines.size, false))
        assertNull(adjacentSpeechSegment(lines, 4, true))
        assertNull(adjacentSpeechSegment(lines, 0, false))
        assertNull(adjacentSpeechSegment(emptyList(), 0, true))
    }
}
