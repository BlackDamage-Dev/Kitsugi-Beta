package com.kitsugi.animelist.data.repository

import org.junit.Assert.*
import org.junit.Test

class StremioSubtitleRoutingTest {
    @Test fun selectsDeclaredSubtitleType() {
        assertEquals("anime", StremioSubtitleRouting.requestType("anime", "series"))
        assertEquals("animemovie", StremioSubtitleRouting.requestType("animemovie", "movie"))
        assertEquals("series", StremioSubtitleRouting.requestType("*", "series"))
        assertEquals("movie", StremioSubtitleRouting.requestType(null, "movie"))
        assertNull(StremioSubtitleRouting.requestType("movie", "series"))
    }

    @Test fun explicitSeasonMustMatch() {
        assertTrue(subtitleSeasonMatches("S02E03", 2))
        assertFalse(subtitleSeasonMatches("S01E03", 2))
        assertFalse(subtitleSeasonMatches("2x03", 1))
        assertTrue(subtitleSeasonMatches("episode 03", 2))
    }

    @Test fun titleFallbackDoesNotMatchSequelsOrShortSubstrings() {
        assertTrue(subtitleTitleMatches("Date A Live", listOf("DATE A LIVE")))
        assertFalse(subtitleTitleMatches("Date A Live", listOf("Date A Live II")))
        assertFalse(subtitleTitleMatches("Live", listOf("Date A Live")))
        assertFalse(subtitleTitleMatches("", listOf("")))
    }
}
