package com.kitsugi.animelist.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreShelfDimensionsTest {
    @Test fun shelfWidthsFollowOrientation() {
        assertEquals(180.dp, exploreShelfCardWidth(false))
        assertEquals(260.dp, exploreShelfCardWidth(true))
    }

    @Test fun landscapePosterDimensionsFollowScreenWidthBreakpoints() {
        listOf(
            Triple(799, 90.dp, 130.dp),
            Triple(800, 100.dp, 145.dp),
            Triple(1199, 100.dp, 145.dp),
            Triple(1200, 110.dp, 160.dp),
        ).forEach { (screenWidth, posterWidth, posterHeight) ->
            assertEquals(posterWidth, exploreLandscapePosterWidth(screenWidth))
            assertEquals(posterHeight, exploreLandscapePosterHeight(screenWidth))
        }
    }
}
