package com.kitsugi.animelist.ui.screens.explore

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
import com.kitsugi.animelist.data.remote.AiringEntry
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class AiringCalendarLoadingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun gridCardMatchesPlaceholderInBothOrientations() {
        val loading = mutableStateOf(true)
        val landscape = mutableStateOf(false)
        val entry = AiringEntry(
            aniListId = 1, malId = null, title = "A title", titleEnglish = null,
            titleNative = null, coverUrl = null, episode = 1, airingAt = 4102444800L,
            dayOfWeek = Calendar.MONDAY,
        )
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape.value) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    Box(Modifier.width(if (landscape.value) 350.dp else 180.dp).testTag("card")) {
                        if (loading.value) {
                            KitsugiShimmerProvider {
                                LocalShimmerBrush.current?.let { AiringSkeletonCard(landscape.value, it) }
                            }
                        } else {
                            AiringEntryGridCard(entry, isInWatchingList = false)
                        }
                    }
                }
            }
        }
        listOf(false, true).forEach { wide ->
            compose.runOnIdle { landscape.value = wide; loading.value = true }
            val placeholder = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { loading.value = false }
            val loaded = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
            assertEquals(loaded.width, placeholder.width, 1f)
            assertEquals("landscape=$wide", loaded.height, placeholder.height, 1f)
        }
    }
}
