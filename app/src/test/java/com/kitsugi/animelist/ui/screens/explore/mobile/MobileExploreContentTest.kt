package com.kitsugi.animelist.ui.screens.explore.mobile

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.explore.ExplorePayload
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.screens.explore.ExploreSourceState
import org.junit.Assert.*
import org.junit.Test

class MobileExploreContentTest {
    private fun item(source: String, type: MediaType, id: Int = 1, adult: Boolean = false) =
        JikanSearchResult(id, "Item $id", "", type, null, null, adult, "poster", null, source)
    private fun payload() = ExplorePayload(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test fun selectedSourceSwitchDoesNotKeepPreviousPageOrHero() {
        val ani = item("anilist", MediaType.Anime)
        val movie = item("tmdb", MediaType.Movie)
        val first = buildMobileExploreContent(MobileExploreInput.Selected(ExplorePlatform.AniList, ExploreSourceState(payload = payload().copy(topAnime = listOf(ani)))), false, true)
        val second = buildMobileExploreContent(MobileExploreInput.Selected(ExplorePlatform.TMDB, ExploreSourceState(payload = payload().copy(movieAnime = listOf(movie)))), false, true)
        assertEquals(ani, first.pages.single { it.tab == MobileExploreTab.ANIME }.hero)
        assertEquals(movie, second.pages.single { it.tab == MobileExploreTab.MOVIES }.hero)
        assertEquals(null, second.pages.single { it.tab == MobileExploreTab.ANIME }.hero)
        assertTrue(second.pages.flatMap { it.shelves }.all { it.source == ExplorePlatform.TMDB })
    }

    @Test fun allPreservesIndependentStatusAndOnlyOnePersonalPayload() {
        val tv = item("simkl", MediaType.TvShow)
        val p = payload().copy(topAnime = listOf(tv), simklContinueSeries = listOf(tv))
        val cached = ExploreSourceState(payload = p, error = "offline", isCached = true)
        val failed = ExploreSourceState(error = "failure")
        val input = MobileExploreInput.All(mapOf(ExplorePlatform.SIMKL to cached, ExplorePlatform.TMDB to failed), PersonalPayload(ExplorePlatform.SIMKL, p))
        val content = buildMobileExploreContent(input, false, true)
        val series = content.pages.single { it.tab == MobileExploreTab.SERIES }
        assertEquals(1, series.shelves.count { it.origin == MobileShelfOrigin.SIMKL_CONTINUE })
        assertEquals(cached, series.sourceStatuses.single { it.source == ExplorePlatform.SIMKL }.state)
        assertEquals(failed, series.sourceStatuses.single { it.source == ExplorePlatform.TMDB }.state)
        assertEquals(null, series.sourceStatuses.single { it.source == ExplorePlatform.BANGUMI }.state)
        assertEquals(MobileShelfAction.WATCH_HISTORY, mobileShelfAction(series.shelves.first { it.origin == MobileShelfOrigin.SIMKL_CONTINUE }))
        assertEquals(MobileShelfAction.FINITE_GRID_REQUIRES_UI, mobileShelfAction(series.shelves.first { it.origin == MobileShelfOrigin.CATALOG }))
    }

    @Test fun emptyLoadingAndUnsupportedAreNotConfused() {
        val input = MobileExploreInput.Selected(ExplorePlatform.TMDB, ExploreSourceState(isLoading = true))
        val pages = buildMobileExploreContent(input, false, true).pages
        val movies = pages.single { it.tab == MobileExploreTab.MOVIES }
        val manga = pages.single { it.tab == MobileExploreTab.MANGA }
        assertTrue(movies.supported)
        assertTrue(movies.sourceStatuses.single().state!!.isLoading)
        assertTrue(movies.shelves.isEmpty())
        assertFalse(manga.supported)
        assertTrue(manga.sourceStatuses.isEmpty())
    }

    @Test fun staleContentAndFailureRemainVisibleDuringOtherSourceLoading() {
        val tv = item("simkl", MediaType.TvShow)
        val cached = ExploreSourceState(payload = payload().copy(topAnime = listOf(tv)), isCached = true, error = "offline")
        val loading = ExploreSourceState(isLoading = true)
        val input = MobileExploreInput.All(mapOf(ExplorePlatform.SIMKL to cached, ExplorePlatform.TMDB to loading))
        val page = buildMobileExploreContent(input, false, true).pages.single { it.tab == MobileExploreTab.SERIES }
        assertEquals(listOf(ExplorePlatform.TMDB), page.loadingSources)
        assertEquals(listOf(ExplorePlatform.SIMKL), page.showingCachedSources)
        assertEquals(listOf(ExplorePlatform.SIMKL), page.failedSources.map { it.source })
        assertTrue(page.awaitingSourceState) // Bangumi has not reported yet.
        assertTrue(page.readyForScrollRestore) // stale, real content remains on screen.
        assertFalse(page.showNoShelves)
    }

    @Test fun unresolvedOrFailedEmptyPageNeverClaimsAConfirmedEmptyCatalog() {
        val missing = MobileExploreInput.All(emptyMap())
        val missingPage = buildMobileExploreContent(missing, false, true).pages.single { it.tab == MobileExploreTab.MANGA }
        assertTrue(missingPage.awaitingSourceState)
        assertFalse(missingPage.showNoShelves)
        assertFalse(missingPage.readyForScrollRestore)
        val failure = MobileExploreInput.Selected(ExplorePlatform.AniList, ExploreSourceState(error = "failure"))
        val errorPage = buildMobileExploreContent(failure, false, true).pages.single { it.tab == MobileExploreTab.MANGA }
        assertFalse(errorPage.showNoShelves)
        assertFalse(errorPage.readyForScrollRestore)
        assertEquals(1, errorPage.failedSources.size)
        val idle = MobileExploreInput.Selected(ExplorePlatform.AniList, ExploreSourceState())
        val idlePage = buildMobileExploreContent(idle, false, true).pages.single { it.tab == MobileExploreTab.MANGA }
        assertTrue(idlePage.awaitingSourceState)
        assertFalse(idlePage.showNoShelves)
        assertFalse(idlePage.readyForScrollRestore)
        val success = MobileExploreInput.Selected(ExplorePlatform.AniList, ExploreSourceState(payload = payload()))
        val emptyPage = buildMobileExploreContent(success, false, true).pages.single { it.tab == MobileExploreTab.MANGA }
        assertFalse(emptyPage.awaitingSourceState)
        assertTrue(emptyPage.showNoShelves)
        assertTrue(emptyPage.readyForScrollRestore)
    }

    @Test fun partialFailureDoesNotClaimAllSourcesAreEmpty() {
        val ok = ExploreSourceState(payload = payload())
        val failure = ExploreSourceState(error = "offline")
        val states = ExplorePlatform.sources.associateWith { if (it == ExplorePlatform.MAL) failure else ok }
        val page = buildMobileExploreContent(MobileExploreInput.All(states), false, true).pages.single { it.tab == MobileExploreTab.MANGA }
        assertFalse(page.awaitingSourceState)
        assertFalse(page.showNoShelves)
        assertFalse(page.readyForScrollRestore)
        assertEquals(listOf(ExplorePlatform.MAL), page.failedSources.map { it.source })
    }

    @Test fun adultFilterAppliesBeforeHeroAndPersonalShelf() {
        val adult = item("simkl", MediaType.TvShow, adult = true)
        val p = payload().copy(topAnime = listOf(adult), simklContinueSeries = listOf(adult))
        val content = buildMobileExploreContent(MobileExploreInput.Selected(ExplorePlatform.SIMKL, ExploreSourceState(payload = p)), false, true)
        val series = content.pages.single { it.tab == MobileExploreTab.SERIES }
        assertTrue(series.shelves.isEmpty())
        assertEquals(null, series.hero)
    }

    @Test fun allDoesNotRelabelAnilistKitsuFallback() {
        val kitsu = item("kitsu", MediaType.Anime)
        val p = payload().copy(topAnime = listOf(kitsu))
        val content = buildMobileExploreContent(MobileExploreInput.All(mapOf(ExplorePlatform.AniList to ExploreSourceState(payload = p))), true, true)
        assertTrue(content.pages.flatMap { it.shelves }.isEmpty())
        assertEquals(listOf(kitsu), content.rejected.map { it.item })
    }
}
