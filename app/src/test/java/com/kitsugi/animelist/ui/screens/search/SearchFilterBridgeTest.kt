package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaFormat
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterBridgeTest {

    @Test
    fun commonSelectionsAreCarriedIntoEveryProviderRequest() {
        val state = SearchUiState(
            selectedEngine = SearchSourceEngine.MAL,
            selectedMediaType = MediaType.Anime,
            genres = listOf("Action"),
            tags = listOf("Time Travel"),
            selectedFormats = listOf(KitsugiMediaFormat.TV, KitsugiMediaFormat.MOVIE),
            selectedStatuses = listOf(KitsugiMediaStatus.RELEASING),
            startYear = 2024,
            endYear = 2026,
            minScore = 70
        )

        val mal = state.effectiveMalFilters()
        assertEquals(listOf("tv", "movie"), mal.types)
        assertEquals(listOf(1, 78), mal.genres)
        assertEquals("currently_airing", mal.status)
        assertEquals(2024, mal.startYear)
        assertEquals(2026, mal.endYear)
        assertEquals(7.0, mal.minScore ?: -1.0, 0.0)

        val tmdb = state.effectiveTmdbFilters()
        assertTrue(28 in tmdb.genres)
        assertEquals(2024, tmdb.startYear)
        assertEquals(2026, tmdb.endYear)

        val shikimori = state.effectiveShikimoriFilters()
        assertTrue(1 in shikimori.genres)
        assertTrue(77 in shikimori.genres)
        assertEquals(listOf("tv", "movie"), shikimori.kinds)

        val kitsu = state.effectiveKitsuFilters()
        assertTrue("tv" in kitsu.subtypes)
        assertTrue("movie" in kitsu.subtypes)
        assertTrue("action" in kitsu.categories)
        // Time Travel is a tag in the common taxonomy, not a Kitsu category slug;
        // it must not be sent as an unsupported category and blank the response.
        assertTrue("time travel" !in kitsu.categories)
        assertEquals("current", kitsu.statuses.single())

        val simkl = state.effectiveSimklFilters()
        assertEquals(listOf("action"), simkl.genres)
        assertEquals("2024", simkl.year)
    }

    @Test
    fun providerSelectionsAndCommonSelectionsAreUnionedWithoutDroppingEither() {
        val state = SearchUiState(
            selectedEngine = SearchSourceEngine.SIMKL,
            selectedMediaType = MediaType.Anime,
            genres = listOf("Action", "Comedy"),
            malSpecificFilters = MalSpecificFilters(types = listOf("ova")),
            simklSpecificFilters = SimklSpecificFilters(genres = listOf("horror"))
        )

        val mal = state.effectiveMalFilters()
        assertEquals(listOf("ova"), mal.types)
        assertEquals(listOf(1, 4), mal.genres)

        val simkl = state.effectiveSimklFilters()
        assertEquals(listOf("horror", "action", "comedy"), simkl.genres)
        assertEquals("horror", simkl.genre)
    }

    @Test
    fun filterOnlyRequestRemainsActiveForEverySelectedProvider() {
        val common = SearchUiState(
            selectedEngine = SearchSourceEngine.ALL,
            tags = listOf("institution"),
            startYear = 2025
        )
        assertTrue(common.hasFiltersApplied)
        assertEquals(2, common.activeFilterCount)

        SearchSourceEngine.entries
            .filter { it != SearchSourceEngine.ALL }
            .forEach { engine ->
                val state = common.copy(selectedEngine = engine)
                assertTrue("$engine lost the common filter", state.hasFiltersApplied)
            }
    }
}
