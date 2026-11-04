package com.kitsugi.animelist.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExploreShelfLoadingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingAndLoadedShelvesHaveTheSameHeightAcrossOrientationsAndTv() {
        val loading = mutableStateOf(true)
        val orientation = mutableStateOf(Configuration.ORIENTATION_PORTRAIT)
        val width = mutableStateOf(400)
        val tv = mutableStateOf(false)
        val result = JikanSearchResult(
            malId = 1, title = "Title", subtitle = "Subtitle", type = MediaType.Anime,
            total = null, score = null, isAdult = false, imageUrl = null, year = null,
            source = "anilist",
        )
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                this.orientation = orientation.value
                screenWidthDp = width.value
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalIsTv provides tv.value,
                LocalIsTvDevice provides tv.value,
            ) {
                MaterialTheme {
                    Box(Modifier.testTag("shelf")) {
                        KitsugiHorizontalMediaSection(
                            title = "Shelf", results = if (loading.value) emptyList() else listOf(result),
                            isLoading = loading.value, alreadyInList = { false }, onItemClick = {},
                            showTitle = false,
                        )
                    }
                }
            }
        }

        // Include every landscape poster breakpoint, and TV's always-vertical card.
        listOf(
            Triple(Configuration.ORIENTATION_PORTRAIT, 400, false),
            Triple(Configuration.ORIENTATION_LANDSCAPE, 700, false),
            Triple(Configuration.ORIENTATION_LANDSCAPE, 900, false),
            Triple(Configuration.ORIENTATION_LANDSCAPE, 1200, false),
            Triple(Configuration.ORIENTATION_LANDSCAPE, 1200, true),
        ).forEach { (direction, screenWidth, isTv) ->
            compose.runOnIdle {
                orientation.value = direction
                width.value = screenWidth
                tv.value = isTv
                loading.value = true
            }
            val placeholderHeight = compose.onNodeWithTag("shelf").fetchSemanticsNode().boundsInRoot.height
            compose.runOnIdle { loading.value = false }
            val loadedHeight = compose.onNodeWithTag("shelf").fetchSemanticsNode().boundsInRoot.height
            assertEquals("orientation=$direction width=$screenWidth tv=$isTv", loadedHeight, placeholderHeight, 1f)
        }
    }
}
