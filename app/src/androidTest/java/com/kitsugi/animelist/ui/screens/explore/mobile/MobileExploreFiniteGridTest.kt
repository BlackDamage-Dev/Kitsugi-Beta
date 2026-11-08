package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileExploreFiniteGridTest {
    @get:Rule val compose = createComposeRule()

    @Test fun finiteLimitAndBackAreVisibleAndFunctional() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var back = false
        val item = JikanSearchResult(7, "Finite result", "", MediaType.Anime, null, null, false, null, null, "anilist")
        compose.setContent {
            MaterialTheme {
                MobileExploreFiniteGrid(
                    title = "Sample shelf", results = listOf(item), alreadyInList = { false },
                    getMediaEntry = { null }, onItemClick = {}, onLongClickItem = {},
                    onBack = { back = true }, showAdultContent = false, titleLanguage = "ROMAJI", scoreFormat = "POINT_10",
                    hideScores = false, blurAdultMedia = true,
                )
            }
        }
        compose.onNodeWithText("Sample shelf").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.mobile_grid_loaded_results_only)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.mobile_grid_back)).performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test fun hundredPointProviderScoreKeepsOriginalInClickCallback() {
        val result = JikanSearchResult(13, "Provider rating", "", MediaType.Anime, null, 85, false, null, null, "simkl")
        var clicked: JikanSearchResult? = null
        compose.setContent {
            MaterialTheme {
                MobileExploreFiniteGrid(
                    title = "Shelf", results = listOf(result), alreadyInList = { false },
                    getMediaEntry = { null }, onItemClick = { clicked = it }, onLongClickItem = {}, onBack = {},
                    showAdultContent = false, titleLanguage = "ROMAJI", scoreFormat = "POINT_5",
                    hideScores = false, blurAdultMedia = false,
                )
            }
        }
        compose.onNodeWithText("Provider rating", useUnmergedTree = true).performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(result, clicked) }
    }

    @Test fun adultSettingChangesVisibleResultsWithoutReloading() {
        val showAdult = mutableStateOf(false)
        val adult = JikanSearchResult(8, "Adult fixture", "", MediaType.Manga, null, null, true, null, null, "anilist")
        compose.setContent {
            MaterialTheme {
                MobileExploreFiniteGrid(
                    title = "Shelf", results = listOf(adult), alreadyInList = { false },
                    getMediaEntry = { null }, onItemClick = {}, onLongClickItem = {}, onBack = {},
                    showAdultContent = showAdult.value, titleLanguage = "ROMAJI", scoreFormat = "POINT_10",
                    hideScores = false, blurAdultMedia = true,
                )
            }
        }
        compose.onNodeWithText("Adult fixture", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { showAdult.value = true }
        compose.onNodeWithText("Adult fixture", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { showAdult.value = false }
        compose.onNodeWithText("Adult fixture", useUnmergedTree = true).assertDoesNotExist()
    }
}
