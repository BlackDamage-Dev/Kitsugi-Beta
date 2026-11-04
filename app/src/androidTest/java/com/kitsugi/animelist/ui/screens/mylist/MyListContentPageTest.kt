package com.kitsugi.animelist.ui.screens.mylist

import android.content.res.Configuration
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.ProfileAndListSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual lazy content, not just the result counter. */
@RunWith(AndroidJUnit4::class)
class MyListContentPageTest {
    @get:Rule val compose = createComposeRule()

    private fun show(layout: String, separated: Boolean, connected: Boolean = true, landscape: Boolean = false,
        filtered: Boolean = false, entryCount: Int = 12) {
        val entries = (1..entryCount).map {
            MediaEntry(id = it, title = "Test title $it", type = MediaType.Anime,
                status = WatchStatus.Watching, score = null, progress = 0, total = null,
                source = "kitsu")
        }
        val configuration = Configuration().apply {
            orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = if (landscape) 1000 else 400
            screenHeightDp = if (landscape) 600 else 800
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalConfiguration provides configuration, LocalIsTvDevice provides false) {
                    Box(Modifier.width(if (landscape) 1000.dp else 400.dp).height(600.dp)) {
                        MyListContentPage(
                            pageTabIndex = MY_LIST_KITSU_TAB_INDEX,
                            selectedTabIndex = MY_LIST_KITSU_TAB_INDEX,
                            isConnected = connected,
                            isSimklSessionExpired = false,
                            pageEntries = entries,
                            sourceBadgesByEntryId = emptyMap(),
                            visibleEntries = if (filtered) emptyList() else entries,
                            groupedVisibleEntries = groupMyListEntriesByStatus(if (filtered) emptyList() else entries),
                            searchQuery = "",
                            selectedStatusFilterId = "all",
                            selectedListLayoutId = layout,
                            appSettings = AppSettings(profile = ProfileAndListSettings(separatedListStyle = separated)),
                            pageScrollState = rememberLazyListState(),
                            onLogin = {}, onRefresh = {}, onEntryClick = {}, onIncrementProgress = {},
                            onPosterLongClick = {}, accentColor = Color.Cyan, horizontalPadding = 12.dp
                        )
                    }
                }
            }
        }
    }

    @Test fun landscapeGroupedGridDisplaysCardsBelowCount() {
        show("grid_2col", separated = true, landscape = true)
        compose.onNodeWithText("12 sonuç").assertIsDisplayed()
        compose.onNodeWithText("Test title 1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Test title 2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun flatGridDisplaysCardsBelowCount() {
        show("grid_2col", separated = false)
        compose.onNodeWithText("12 sonuç").assertIsDisplayed()
        compose.onNodeWithText("Test title 1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun filteredEmptyListShowsZeroInsteadOfOldCards() {
        show("grid_2col", separated = true, filtered = true)
        compose.onNodeWithText("0 sonuç").assertIsDisplayed()
        compose.onAllNodesWithText("Test title 1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun largeGroupedGridKeepsCardsVisibleAfterScrollingAwayAndBack() {
        show("grid_2col", separated = true, entryCount = 844)
        val list = compose.onNodeWithTag("my_list_content")
        compose.onNodeWithText("844 sonuç").assertIsDisplayed()
        compose.onNodeWithText("Test title 1", useUnmergedTree = true).assertIsDisplayed()
        list.performScrollToIndex(52) // count, header, then 50 rows of two
        compose.waitForIdle()
        compose.onNodeWithText("Test title 101", useUnmergedTree = true).assertIsDisplayed()
        list.performScrollToIndex(2)
        compose.waitForIdle()
        compose.onNodeWithText("Test title 1", useUnmergedTree = true).assertIsDisplayed()
        list.performTouchInput { swipeUp() }
        compose.waitForIdle()
        list.performScrollToIndex(2)
        compose.onNodeWithText("Test title 1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun largeFlatGridKeepsCardsVisibleAfterScrollingAwayAndBack() {
        show("grid_2col", separated = false, entryCount = 844)
        val list = compose.onNodeWithTag("my_list_content")
        list.performScrollToIndex(51) // count, then 50 rows of two
        compose.waitForIdle()
        compose.onNodeWithText("Test title 101", useUnmergedTree = true).assertIsDisplayed()
        list.performScrollToIndex(1)
        compose.waitForIdle()
        compose.onNodeWithText("Test title 1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun disconnectedProviderStillDisplaysCachedEntries() {
        show("comfortable", separated = true, connected = false)
        compose.onNodeWithText("12 sonuç").assertIsDisplayed()
        compose.onNodeWithText("Test title 1", useUnmergedTree = true).assertIsDisplayed()
    }
}
