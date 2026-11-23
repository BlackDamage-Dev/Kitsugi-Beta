package com.kitsugi.animelist.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailTabIndexMappingTest {
    @Test
    fun videoPageDoesNotTriggerRemoteTabLoading() {
        assertNull(detailDataTabIndexForPage(DETAIL_VIDEO_PAGE_INDEX))
    }

    @Test
    fun pagesAroundVideoTabKeepTheirLegacyDataIndexes() {
        assertEquals(0, detailDataTabIndexForPage(0)) // Info
        assertNull(detailDataTabIndexForPage(1)) // Videos - no remote data
        assertEquals(2, detailDataTabIndexForPage(2)) // Characters
        assertEquals(3, detailDataTabIndexForPage(3)) // Staff
        assertEquals(7, detailDataTabIndexForPage(7)) // Reviews
        assertEquals(8, detailDataTabIndexForPage(8)) // Episodes
    }
}
