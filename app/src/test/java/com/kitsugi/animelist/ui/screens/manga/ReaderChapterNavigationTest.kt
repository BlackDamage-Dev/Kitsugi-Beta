package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderChapterNavigationTest {
    private val urls = listOf("four", "three", "two", "one")

    @Test fun skipsCompletedInBothDirections() {
        assertEquals(0, findUnreadChapterIndex(urls, 2, -1, setOf("three")))
        assertEquals(3, findUnreadChapterIndex(urls, 1, 1, setOf("two")))
    }

    @Test fun handlesBoundariesInvalidDirectionAndAllCompleted() {
        assertNull(findUnreadChapterIndex(urls, 0, -1, emptySet()))
        assertNull(findUnreadChapterIndex(urls, 3, 1, emptySet()))
        assertNull(findUnreadChapterIndex(urls, 2, 0, emptySet()))
        assertNull(findUnreadChapterIndex(urls, -1, 1, emptySet()))
        assertNull(findUnreadChapterIndex(urls, 2, -1, setOf("three", "four")))
    }
}
