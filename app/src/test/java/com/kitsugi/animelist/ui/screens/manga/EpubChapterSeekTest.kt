package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class EpubChapterSeekTest {
    @Test fun handlesEdgesAndInvalidValues() {
        assertEquals(0, epubChapterForFraction(Float.NaN, 10))
        assertEquals(0, epubChapterForFraction(0.5f, 0))
        assertEquals(0, epubChapterForFraction(-5f, 8))
        assertEquals(7, epubChapterForFraction(2f, 8))
        assertEquals(2, epubChapterForFraction(0.5f, 5))
    }
}
