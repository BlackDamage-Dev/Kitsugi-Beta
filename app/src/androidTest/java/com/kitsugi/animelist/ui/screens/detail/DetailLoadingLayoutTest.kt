package com.kitsugi.animelist.ui.screens.detail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailLoadingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun relationAndRecommendationPlaceholdersMatchLoadedCardsOnCompactAndWideScreens() {
        val loading = mutableStateOf(true)
        val recommendation = mutableStateOf(false)
        val wide = mutableStateOf(false)
        val relation = KitsugiRelation(1, "Title", "Sequel", null, MediaType.Anime, "anilist")
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (wide.value) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = if (wide.value) 900 else 320
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    Box(Modifier.width(if (wide.value) 500.dp else 300.dp).testTag("content")) {
                        if (recommendation.value) {
                            if (loading.value) DetailRecommendationsLoadingSkeleton(itemCount = 1)
                            else RecommendationsTabContent(DetailTabState.Success(listOf(relation)), onRecommendationClick = {})
                        } else {
                            if (loading.value) DetailRelationsLoadingSkeleton(itemCount = 1)
                            else RelationsTabContent(DetailTabState.Success(listOf(relation)), onRelationClick = {})
                        }
                    }
                }
            }
        }
        listOf(false, true).forEach { isWide ->
            listOf(false, true).forEach { isRecommendation ->
                compose.runOnIdle { wide.value = isWide; recommendation.value = isRecommendation; loading.value = true }
                val placeholder = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
                compose.runOnIdle { loading.value = false }
                val loaded = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
                assertEquals(loaded.width, placeholder.width, 1f)
                assertEquals("wide=$isWide recommendation=$isRecommendation", loaded.height, placeholder.height, 1f)
            }
        }
    }
}
