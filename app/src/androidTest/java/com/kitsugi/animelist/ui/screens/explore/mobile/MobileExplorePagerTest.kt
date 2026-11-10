package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Separate from the visual agent's tests. Requires an Android device/emulator. */
@RunWith(AndroidJUnit4::class)
class MobileExplorePagerTest {
    @get:Rule val compose = createComposeRule()
    private val source = mutableStateOf(ExplorePlatform.AniList)
    private val ready = mutableStateOf(true)

    private fun setPager() {
        compose.setContent {
            Column(Modifier.fillMaxSize()) {
                MobileExplorePager(
                    selectedSource = source.value,
                    contentReady = { ready.value },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    tabBar = { selected, onSelected ->
                        Row { MobileExploreTab.entries.forEachIndexed { index, tab ->
                            Text("${tab.name} ${if (selected == index) "selected" else ""}", Modifier.testTag("tab_$index").clickable { onSelected(index) })
                        } }
                    },
                    page = { tab, state ->
                        LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("list_${tab.name}")) {
                            if (ready.value) {
                                items((0..59).toList()) { index -> Text("${source.value.name}_${tab.name}_$index", Modifier.height(60.dp)) }
                            } else {
                                item { Text("loading", Modifier.height(60.dp)) }
                            }
                        }
                    },
                )
            }
        }
    }

    @Test fun tabClickChangesPageAndReturnsToPreviousScrollPosition() {
        setPager()
        compose.onNodeWithTag("list_SERIES").performScrollToIndex(30)
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("AniList_MOVIES_0").assertIsDisplayed()
        compose.onNodeWithTag("tab_0").performClick()
        compose.onNodeWithText("AniList_SERIES_30").assertIsDisplayed()
    }

    @Test fun scrollPositionIsScopedBySourceAndRestoredAfterReturning() {
        setPager()
        compose.onNodeWithTag("list_SERIES").performScrollToIndex(30)
        compose.runOnIdle { source.value = ExplorePlatform.MAL }
        compose.onNodeWithText("MAL_SERIES_0").assertIsDisplayed()
        compose.runOnIdle { source.value = ExplorePlatform.AniList }
        compose.onNodeWithText("AniList_SERIES_30").assertIsDisplayed()
    }

    @Test fun loadingPlaceholderDoesNotOverwriteTheRealPosition() {
        setPager()
        compose.onNodeWithTag("list_SERIES").performScrollToIndex(30)
        compose.runOnIdle { ready.value = false }
        compose.onNodeWithText("loading").assertIsDisplayed()
        compose.runOnIdle { ready.value = true }
        compose.onNodeWithText("AniList_SERIES_30").assertIsDisplayed()
    }

    @Test fun swipeChangesTheSelectedTab() {
        setPager()
        compose.onNodeWithTag("list_SERIES").performTouchInput { swipeLeft() }
        compose.onNodeWithText("AniList_MOVIES_0").assertIsDisplayed()
        compose.onNodeWithText("MOVIES selected").assertIsDisplayed()
    }
}
