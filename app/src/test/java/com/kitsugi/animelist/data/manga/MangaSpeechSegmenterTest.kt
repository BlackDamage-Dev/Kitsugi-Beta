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

    @Test
    fun splitsParagraphsIntoSentencesAndIgnoresImages() {
        val paragraphs = listOf(
            "[img:file:///path/to/cover.jpg]",
            "Birinci cümle burada bitti! İkinci cümle ise soru mu? Üçüncü cümle devam ediyor...",
            "İkinci paragraf tek cümleden oluşuyor."
        )
        val segments = MangaSpeechSegmenter.segmentParagraphs(paragraphs)
        assertEquals(4, segments.size)

        // First paragraph (index 1 in original list because index 0 was image):
        assertEquals(1, segments[0].paragraphIndex)
        assertEquals(0, segments[0].sentenceIndex)
        assertEquals("Birinci cümle burada bitti!", segments[0].text)

        assertEquals(1, segments[1].paragraphIndex)
        assertEquals(1, segments[1].sentenceIndex)
        assertEquals("İkinci cümle ise soru mu?", segments[1].text)

        assertEquals(1, segments[2].paragraphIndex)
        assertEquals(2, segments[2].sentenceIndex)
        assertEquals("Üçüncü cümle devam ediyor...", segments[2].text)

        // Second paragraph (index 2 in original list):
        assertEquals(2, segments[3].paragraphIndex)
        assertEquals(0, segments[3].sentenceIndex)
        assertEquals("İkinci paragraf tek cümleden oluşuyor.", segments[3].text)
    }
}
