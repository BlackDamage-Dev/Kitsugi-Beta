package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class NovelReadingEstimateTest {
    @Test fun estimatesFromCharacterOffsetsRatherThanParagraphCount() {
        val offsets = novelReadingOffsets(listOf("x".repeat(999), "x".repeat(2999)))
        assertEquals(NovelReadingEstimate(0, 4), novelReadingEstimate(offsets, 0, false))
        assertEquals(NovelReadingEstimate(25, 3), novelReadingEstimate(offsets, 1, false))
        assertEquals(NovelReadingEstimate(100, 0), novelReadingEstimate(offsets, 1, true))
    }
    @Test fun clampsIndicesAndHandlesEmptyBooks() {
        assertEquals(NovelReadingEstimate(0, 0), novelReadingEstimate(novelReadingOffsets(emptyList()), -1, false))
        assertEquals(NovelReadingEstimate(0, 1), novelReadingEstimate(novelReadingOffsets(listOf("hi")), -20, false))
    }
    @Test fun seekUsesTextLengthAndClamps() {
        val offsets = novelReadingOffsets(listOf("a".repeat(999), "b".repeat(2999), "last"))
        assertEquals(0, novelSegmentAtPercent(offsets, 0f))
        assertEquals(1, novelSegmentAtPercent(offsets, 50f))
        assertEquals(2, novelSegmentAtPercent(offsets, 100f))
        assertEquals(0, novelSegmentAtPercent(offsets, Float.NaN))
        assertEquals(0, novelSegmentAtPercent(longArrayOf(0), 40f))
    }
}
