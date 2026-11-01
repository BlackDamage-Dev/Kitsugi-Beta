package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailSearchFilterRequestTest {

    @Test
    fun resolvesLocalizedSeasonAndYearAcrossRawAndDisplayValues() {
        val metadata = resolveDetailSeasonMetadata(
            rawSeason = "WINTER",
            displayedSeason = "Kış 2025"
        )

        assertEquals("WINTER", metadata.apiSeason)
        assertEquals(2025, metadata.year)
        assertEquals("Kış 2025", metadata.displayLabel)
    }

    @Test
    fun derivesAnimeSeasonFromStartDateWhenProviderOmitsIt() {
        val metadata = resolveDetailSeasonMetadata(
            rawSeason = null,
            displayedSeason = null,
            startDate = "2024-08-13",
            deriveFromDate = true
        )

        assertEquals("SUMMER", metadata.apiSeason)
        assertEquals(2024, metadata.year)
        assertEquals("Yaz 2024", metadata.displayLabel)
    }

    @Test
    fun allSourcesTreatsDetailTagAndSeasonAsActiveFiltersForBlankSearch() {
        val state = SearchUiState(
            selectedEngine = SearchSourceEngine.ALL,
            tags = listOf("time travel"),
            startYear = 2025,
            endYear = 2025,
            season = KitsugiMediaSeason.WINTER
        )

        assertTrue(state.hasFiltersApplied)
        assertEquals(3, state.activeFilterCount)
    }

    @Test
    fun mapsTurkishSpringNameToCanonicalSeason() {
        assertEquals("SPRING", CanonicalFilterBridge.normalizeSeason("İlkbahar 2023"))
    }

    @Test
    fun seasonFacetOnlyRunsOnProvidersThatCanPreserveSeasonMeaning() {
        val request = DetailSearchFilterRequest(
            source = "bangumi",
            mediaType = MediaType.Anime,
            season = "WINTER",
            year = 2025
        )

        assertTrue(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.ANILIST, request))
        assertTrue(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.MAL, request))
        assertTrue(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.SHIKIMORI, request))
        assertTrue(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.KITSU, request))
        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.TMDB, request))
        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.SIMKL, request))
        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.BANGUMI, request))
    }

    @Test
    fun knownGenreMapsToAllCompatibleAnimeEngines() {
        val request = DetailSearchFilterRequest(
            source = "tmdb",
            mediaType = MediaType.Anime,
            genre = "Action"
        )

        SearchSourceEngine.entries
            .filter { it != SearchSourceEngine.ALL }
            .forEach { engine ->
                assertTrue("Expected $engine to support Action", CanonicalFilterBridge.supportsDetailFilter(engine, request))
            }
    }

    @Test
    fun unknownTagIsOmittedByProvidersWithoutTagOrGenreTaxonomyMatch() {
        val request = DetailSearchFilterRequest(
            source = "anilist",
            mediaType = MediaType.Anime,
            tag = "A Highly Specific Unknown Tag",
            tagSource = "anilist"
        )

        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.ANILIST, request))
        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.KITSU, request))
        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.BANGUMI, request))
        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.MAL, request))
        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.TMDB, request))
        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.SHIKIMORI, request))
        assertFalse(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.SIMKL, request))
    }

    @Test
    fun tmdbKeywordRequiresAnIdFromTmdbTagNamespace() {
        val request = DetailSearchFilterRequest(
            source = "anilist",
            mediaType = MediaType.Movie,
            tag = "time travel",
            tagSource = "tmdb",
            keywordId = 123
        )

        assertTrue(CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.TMDB, request))
        assertFalse(
            CanonicalFilterBridge.supportsDetailFilter(
                SearchSourceEngine.TMDB,
                request.copy(tagSource = "anilist")
            )
        )
    }
}
