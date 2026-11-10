package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.*
import org.junit.Test

class CsStreamFormatTest {
    @Test fun explicitTypeSurvivesExtensionlessUrls() {
        assertTrue(CsStreamFormat.isAdaptive("M3U8"))
        assertTrue(CsStreamFormat.isAdaptive("DASH"))
        assertFalse(CsStreamFormat.isAdaptive("VIDEO")) // VIDEO is also the default for embeds
        assertEquals("M3U8", CsStreamFormat.choose("M3U8", false, false))
        assertEquals("DASH", CsStreamFormat.choose("DASH", false, false))
        assertEquals("DASH", CsStreamFormat.choose("DASH", true, false))
        assertEquals("M3U8", CsStreamFormat.choose(null, true, false))
        assertNull(CsStreamFormat.choose("VIDEO", false, false))
    }
}
