package com.kitsugi.animelist.data.repository

import org.junit.Assert.*
import org.junit.Test

class SubtitleSearchQueriesTest {
    @Test fun stableIdsAreDecodedWithoutMistakingKitsuForMal() {
        assertEquals(SubtitleResolverIds(null, 123, 456), subtitleResolverIds(300_000_456, 100_000_123))
        assertEquals(SubtitleResolverIds(42, null, null), subtitleResolverIds(42, null))
        assertEquals(SubtitleResolverIds(null, null, null), subtitleResolverIds(500_000_042, null))
    }

    @Test fun episodeQueriesKeepTheirIdentityAndAliases() {
        val q = buildSubtitleQueries("tt2575684", 12, listOf("Date A Live", "date a live", "デート・ア・ライブ"), false, 2, 3)
        assertEquals(listOf("tt2575684:2:3", "kitsu:12:3", "Date A Live:2:3", "デート・ア・ライブ:2:3"), q.map { it.id })
        assertTrue(q.all { it.type == "series" })
        assertEquals("Date A Live", q[2].title)
    }

    @Test fun moviesUseMovieIdsAndInvalidIdsAreIgnored() {
        val q = buildSubtitleQueries("bad", 23, listOf("Movie"), true, 0, 1)
        assertEquals(listOf("kitsu:23", "Movie"), q.map { it.id })
        assertTrue(q.all { it.type == "movie" })
    }
}
