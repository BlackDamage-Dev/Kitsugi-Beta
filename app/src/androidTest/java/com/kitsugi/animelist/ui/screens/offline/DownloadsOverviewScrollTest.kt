package com.kitsugi.animelist.ui.screens.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only regression for the storage card reveal/collapse and freed layout space. */
@RunWith(AndroidJUnit4::class)
class DownloadsOverviewScrollTest {
    @get:Rule val compose = createComposeRule()

    private fun setScreen(itemCount: Int) {
        compose.setContent {
            MaterialTheme {
                TestHost(itemCount)
            }
        }
    }

    @Composable
    private fun TestHost(itemCount: Int) {
        val videoState = rememberLazyListState()
        val subtitleState = rememberLazyListState()
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val overview = rememberDownloadsOverviewScrollState(
            activePage = 0,
            videoListState = videoState,
            subtitleListState = subtitleState,
            imageGridState = gridState
        )
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            DownloadsOverviewReveal(
                visible = overview.isVisible,
                modifier = Modifier.fillMaxWidth().testTag("overview_reveal")
            ) {
                Box(
                    Modifier.fillMaxWidth().height(96.dp).background(Color.DarkGray).testTag("overview_card")
                )
            }
            Box(Modifier.fillMaxWidth().height(56.dp).background(Color.Gray).testTag("tabs"))
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .nestedScroll(overview.nestedScrollConnection)
            ) {
                LazyColumn(
                    state = videoState,
                    modifier = Modifier.fillMaxSize().testTag("list")
                ) {
                    items(itemCount) { index ->
                        Box(Modifier.fillMaxWidth().height(80.dp).background(if (index % 2 == 0) Color.Blue else Color.Green))
                    }
                }
            }
        }
    }

    @Test
    fun swipeUpHidesCardAndSwipeDownRestoresItWithoutCoveringTabs() {
        setScreen(itemCount = 100)
        val tabs = compose.onNodeWithTag("tabs")
        val initialTabsTop = tabs.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("overview_card").assertIsDisplayed()

        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("overview_card"))
                .fetchSemanticsNodes().isEmpty()
        }
        val collapsedTabsTop = tabs.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Tabs must move into the freed space", collapsedTabsTop < initialTabsTop)
        tabs.assertIsDisplayed()

        compose.onNodeWithTag("list").performTouchInput { swipeDown() }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("overview_card"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("overview_card").assertIsDisplayed()
        tabs.assertIsDisplayed()
    }

    @Test
    fun emptyListKeepsCardVisible() {
        setScreen(itemCount = 0)
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag("overview_card").assertIsDisplayed()
    }
}
