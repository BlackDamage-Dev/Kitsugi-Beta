package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.explore.ExplorePayload
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.screens.explore.ExploreSourceState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileExploreHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tabShelvesAndCallbackKeepActualSourceAndTab() {
        val movie = JikanSearchResult(5, "Movie", "", MediaType.Movie, null, null, false, "poster", null, "tmdb")
        val payload = ExplorePayload(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(movie), emptyList())
        val content = buildMobileExploreContent(MobileExploreInput.Selected(ExplorePlatform.TMDB, ExploreSourceState(payload = payload)), false, true)
        var opened: MobileExploreShelfData? = null
        compose.setContent {
            MobileExploreHost(
                selectedSource = ExplorePlatform.TMDB, content = content, modifier = Modifier.fillMaxSize(),
                tabBar = { _, select -> Row { MobileExploreTab.entries.forEachIndexed { index, tab ->
                    Text(tab.name, Modifier.testTag("tab_$index").clickable { select(index) })
                } } },
                hero = { item, _ -> Text("hero ${item?.title ?: "none"}") },
                shelf = { data, onSeeAll -> Text("shelf ${data.source}", Modifier.testTag("shelf").clickable { onSeeAll?.invoke() }) },
                sourceStatus = { _, _ -> }, emptyPage = {}, onOpenShelf = { opened = it },
            )
        }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("hero Movie").assertIsDisplayed()
        compose.onNodeWithTag("shelf").performClick()
        compose.runOnIdle {
            assertEquals(MobileExploreTab.MOVIES, opened?.tab)
            assertEquals(ExplorePlatform.TMDB, opened?.source)
            assertEquals(listOf(movie), opened?.results)
        }
    }
}
