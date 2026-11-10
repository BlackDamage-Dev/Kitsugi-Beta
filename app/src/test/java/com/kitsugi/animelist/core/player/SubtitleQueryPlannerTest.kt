package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleQueryPlannerTest {
    @Test fun seriesQueriesKeepEpisodeAndNeverUseMovieFallback() {
        val queries = SubtitleQueryPlanner.build(false, "tt12345", 100, "Test Show", 2, 5)
        assertEquals(listOf("tt12345:2:5", "kitsu:100:5", "Test Show:2:5"), queries.map { it.id })
        assertTrue(queries.all { it.type == "series" })
    }

    @Test fun movieAndInvalidIdentifiers() {
        val queries = SubtitleQueryPlanner.build(true, "not-imdb", -1, " Film ", 0, 1)
        assertEquals(listOf(SubtitleQueryPlanner.Query("movie", "Film")), queries)
    }

    @Test fun noIdentifiersReturnsEmpty() {
        assertTrue(SubtitleQueryPlanner.build(false, null, null, " ", 1, 1).isEmpty())
    }

    @Test fun zeroSeasonPreservesSpecials() {
        assertEquals("tt42:0:3", SubtitleQueryPlanner.build(false, "tt42", null, null, 0, 3).single().id)
    }
    @Test fun tmdbQueriesUseProviderProvenIdFormat() {
        assertEquals("tmdb:1396:1:1", SubtitleQueryPlanner.build(false, null, null, null, 1, 1, 1396).single().id)
        assertEquals("tmdb:278", SubtitleQueryPlanner.build(true, null, null, null, 0, 1, 278).single().id)
    }
}
