package com.kitsugi.animelist.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalTranslationValueTest {
    @Test
    fun standaloneTechnicalTokensStayProtected() {
        assertTrue(isTechnicalTranslationValue("[[Page|Label]]"))
        assertTrue(isTechnicalTranslationValue("{{Infobox}}"))
        assertTrue(isTechnicalTranslationValue("https://example.com/a"))
        assertTrue(isTechnicalTranslationValue("File:poster.jpg"))
    }

    @Test
    fun proseBeginningWithWikiMarkupStillGetsTranslated() {
        assertFalse(isTechnicalTranslationValue("[[Page|Label]] is a story about two friends."))
        assertFalse(isTechnicalTranslationValue("{{Infobox}} The series began in 2020."))
        assertFalse(isTechnicalTranslationValue("[https://example.com Link] This is a long description."))
    }
}
