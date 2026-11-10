package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaSpeechSegmenterTest {
    @Test
    fun blankTextDoesNotCreateAnUtterance() {
        assertTrue(MangaSpeechSegmenter.segment("  \n ").isEmpty())
    }

    @Test
    fun everySegmentFitsEngineLimitAndTextIsNotDuplicated() {
        val text = "A complete sentence. ".repeat(400)
        val segments = MangaSpeechSegmenter.segment(text)
        assertTrue(segments.size > 1)
        assertTrue(segments.all { it.length <= MangaSpeechSegmenter.MAX_UTTERANCE_CHARS })
        assertEquals(text.trim(), segments.joinToString(" "))
    }

    @Test
    fun punctuationIsPreferredOverCuttingAWord() {
        val text = "word ".repeat(500) + "Final sentence. " + "next ".repeat(400)
        val segments = MangaSpeechSegmenter.segment(text)
        assertEquals('.', segments.first().last())
        assertEquals(text.trim(), segments.joinToString(" "))
    }

    @Test
    fun uninterruptedLongTextIsStillBounded() {
        val segments = MangaSpeechSegmenter.segment("x".repeat(7001))
        assertEquals(listOf(3000, 3000, 1001), segments.map { it.length })
    }
}
