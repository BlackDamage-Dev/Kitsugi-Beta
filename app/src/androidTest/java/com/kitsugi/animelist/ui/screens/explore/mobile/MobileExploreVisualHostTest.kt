package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.utils.ScrollVisibilityState
import com.kitsugi.animelist.utils.rememberScrollConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileExploreVisualHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollToTopUsesActivePageAndNotifiesParent() {
        val pages = MobileExploreTab.entries.map { tab ->
            MobileExplorePageData(tab, true, emptyList(), emptyList(), null)
        }
        lateinit var active: LazyListState
        var resets = 0
        compose.setContent {
            MobileExploreVisualHost(
                selectedSource = ExplorePlatform.TMDB,
                content = MobileExploreContent(pages, emptyList()),
                onSourceClick = {}, actions = {}, shelfTitle = { it.originalTitle },
                alreadyInList = { false }, getMediaEntry = { null }, onItemClick = {}, onLongClickItem = {},
                onOpenShelf = { _, _ -> }, sourceStatus = { _, _ -> }, emptyPage = {},
                titleLanguage = "ROMAJI", scoreFormat = "POINT_10", hideScores = false,
                blurAdultMedia = false, modifier = Modifier.fillMaxSize(),
                extraItems = { items(40) { index -> Text("Item $index", Modifier.height(80.dp)) } },
                onActiveListChanged = { active = it }, showScrollToTopFab = true,
                onScrollReset = { resets++ },
            )
        }
        compose.onNodeWithTag("mobile_explore_page_list").performScrollToIndex(20)
        compose.runOnIdle { assertTrue(active.firstVisibleItemIndex > 1) }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.yukari_cik)
        compose.onNodeWithContentDescription(label).assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, active.firstVisibleItemIndex)
            assertEquals(0, active.firstVisibleItemScrollOffset)
            assertTrue(resets > 0)
        }
    }

    @Test fun verticalGesturesReachExistingBottomBarScrollConnection() {
        val bar = ScrollVisibilityState(initialVisible = true)
        val pages = MobileExploreTab.entries.map { MobileExplorePageData(it, true, emptyList(), emptyList(), null) }
        compose.setContent {
            MobileExploreVisualHost(
                selectedSource = ExplorePlatform.TMDB,
                content = MobileExploreContent(pages, emptyList()),
                onSourceClick = {}, actions = {}, shelfTitle = { it.originalTitle },
                alreadyInList = { false }, getMediaEntry = { null }, onItemClick = {}, onLongClickItem = {},
                onOpenShelf = { _, _ -> }, sourceStatus = { _, _ -> }, emptyPage = {},
                titleLanguage = "ROMAJI", scoreFormat = "POINT_10", hideScores = false, blurAdultMedia = false,
                modifier = Modifier.fillMaxSize().nestedScroll(rememberScrollConnection(bar)),
                extraItems = { items(40) { index -> Text("Item $index", Modifier.height(80.dp)) } },
            )
        }
        compose.onNodeWithTag("mobile_explore_page_list").performTouchInput { swipeUp() }
        compose.runOnIdle { assertFalse(bar.isVisible) }
        compose.onNodeWithTag("mobile_explore_page_list").performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue(bar.isVisible) }
    }

    @Test fun heroAndFirstShelfFollowReferenceVerticalRhythm() {
        // Requires a test window at least 384dp wide; LocalConfiguration alone cannot
        // enlarge it. Compare layout geometry, not an actual screenshot or artwork.
        val media = JikanSearchResult(42, "Real series", "", MediaType.TvShow, null, 8, false, null, null, "tmdb")
        val shelf = MobileExploreShelfData(
            MobileExploreTab.SERIES, ExplorePlatform.TMDB, ExplorePlatform.TMDB,
            ExploreCategoryType.TOP_ANIME, MobileShelfOrigin.CATALOG, "Trend diziler", listOf(media), false,
        )
        val pages = MobileExploreTab.entries.map { tab ->
            MobileExplorePageData(tab, true, if (tab == shelf.tab) listOf(shelf) else emptyList(), emptyList(), if (tab == shelf.tab) media else null)
        }
        compose.setContent {
            MobileExploreVisualHost(
                selectedSource = ExplorePlatform.TMDB, content = MobileExploreContent(pages, emptyList()),
                onSourceClick = {}, actions = {}, shelfTitle = { it.originalTitle }, alreadyInList = { false },
                getMediaEntry = { null }, onItemClick = {}, onLongClickItem = {}, onOpenShelf = { _, _ -> },
                sourceStatus = { _, _ -> }, emptyPage = {}, titleLanguage = "ROMAJI", scoreFormat = "POINT_10",
                hideScores = false, blurAdultMedia = false, modifier = Modifier.size(384.dp, 683.dp),
            )
        }
        val hero = compose.onNodeWithTag(MobileExploreTestTags.HERO).fetchSemanticsNode().boundsInRoot
        val card = compose.onAllNodesWithTag(MobileExploreTestTags.CARD)[0].fetchSemanticsNode().boundsInRoot
        val scale = compose.density.density
        val gap = (card.top - hero.bottom) / scale
        // The reference has ~36dp between the hero and first poster. Text scaling,
        // fonts and accessibility may legitimately enlarge it.
        assertTrue("hero/poster gap=$gap dp", gap in 30f..45f)
    }

    @Test fun visualSlotsForwardSourceSelectionAndOriginalShelfWithVisibleTitle() {
        val media = JikanSearchResult(42, "Real series", "", MediaType.TvShow, null, null, false, null, null, "tmdb")
        val shelf = MobileExploreShelfData(
            tab = MobileExploreTab.SERIES,
            source = ExplorePlatform.TMDB,
            originPlatform = ExplorePlatform.TMDB,
            category = ExploreCategoryType.TOP_ANIME,
            origin = MobileShelfOrigin.CATALOG,
            originalTitle = "Original",
            results = listOf(media),
            requiresSpecialContinuation = false,
        )
        val pages = MobileExploreTab.entries.map { tab ->
            MobileExplorePageData(tab, true, if (tab == shelf.tab) listOf(shelf) else emptyList(), emptyList(), null)
        }
        var sourceClicks = 0
        var opened: MobileExploreShelfData? = null
        var openedTitle: String? = null
        compose.setContent {
            MobileExploreVisualHost(
                selectedSource = ExplorePlatform.TMDB,
                content = MobileExploreContent(pages, emptyList()),
                onSourceClick = { sourceClicks++ }, actions = {}, shelfTitle = { "Localized shelf" },
                alreadyInList = { false }, getMediaEntry = { null }, onItemClick = {}, onLongClickItem = {},
                onOpenShelf = { value, title -> opened = value; openedTitle = title },
                sourceStatus = { _, _ -> }, emptyPage = {}, titleLanguage = "ROMAJI",
                scoreFormat = "POINT_10", hideScores = false, blurAdultMedia = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        compose.onNodeWithTag(MobileExploreTestTags.SOURCE_PILL).performClick()
        compose.onNodeWithTag(MobileExploreTestTags.SEE_ALL).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, sourceClicks)
            assertEquals(shelf, opened)
            assertEquals("Localized shelf", openedTitle)
        }
    }
}
