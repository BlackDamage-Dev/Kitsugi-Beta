package com.kitsugi.animelist.ui.screens.explore.mobile

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.FullScreenMediaGridState
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import org.junit.Assert.*
import org.junit.Test

class MobileExploreNavigationTest {
    private fun item(source: String = "kitsu") = JikanSearchResult(1, "Result", "", MediaType.Anime, null, null, false, null, null, source)
    private fun shelf(origin: MobileShelfOrigin = MobileShelfOrigin.CATALOG, items: List<JikanSearchResult> = listOf(item())) = MobileExploreShelfData(
        tab = MobileExploreTab.ANIME, source = ExplorePlatform.KITSU, originPlatform = ExplorePlatform.AniList,
        category = ExploreCategoryType.TOP_ANIME, origin = origin, originalTitle = "Original", results = items,
        requiresSpecialContinuation = true,
    )

    @Test fun legacyGridIsUnaffectedByDefault() {
        val legacy = FullScreenMediaGridState("title", ExploreCategoryType.TOP_ANIME, ExplorePlatform.AniList, listOf(item("anilist")))
        assertFalse(legacy.mobileFiniteResults)
    }

    @Test fun finiteGridRetainsShelfIdentityAndDoesNotPretendToBeOriginalProvider() {
        val originalResults = mutableListOf(item())
        val state = mobileFiniteGridState(shelf(items = originalResults), "Local title")
        originalResults.clear()
        assertEquals("Local title", state.title)
        assertEquals(ExplorePlatform.KITSU, state.platform)
        assertEquals(ExploreCategoryType.TOP_ANIME, state.categoryType)
        assertEquals(listOf(item()), state.initialResults)
        assertTrue(state.mobileFiniteResults)
    }

    @Test fun continueIsNotOpenedAsACatalogGrid() {
        assertEquals(MobileShelfAction.WATCH_HISTORY, mobileShelfAction(shelf(origin = MobileShelfOrigin.SIMKL_CONTINUE)))
        assertEquals(MobileShelfAction.NONE, mobileShelfAction(shelf(items = emptyList())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun continueCannotBeConvertedToFiniteGrid() {
        mobileFiniteGridState(shelf(origin = MobileShelfOrigin.SIMKL_CONTINUE), "Continue")
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyShelfCannotBeConvertedToFiniteGrid() {
        mobileFiniteGridState(shelf(items = emptyList()), "Empty")
    }
}
