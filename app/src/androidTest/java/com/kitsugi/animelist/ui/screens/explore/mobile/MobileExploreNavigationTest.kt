package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsugi.animelist.DetailScreen
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileExploreNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyAndFiniteGridUseDifferentDestinations() {
        lateinit var nav: AppNavigationState
        compose.setContent { nav = AppNavigationState(rememberSaveableStateHolder()) }
        val item = JikanSearchResult(1, "Result", "", MediaType.TvShow, null, null, false, null, null, "simkl")
        val base = MobileExploreShelfData(
            MobileExploreTab.SERIES, ExplorePlatform.SIMKL, ExplorePlatform.SIMKL,
            ExploreCategoryType.TOP_ANIME, MobileShelfOrigin.CATALOG, "Catalog", listOf(item), true,
        )
        compose.runOnIdle {
            assertTrue(nav.openMobileExploreShelf(base.copy(origin = MobileShelfOrigin.SIMKL_CONTINUE), "History"))
            assertEquals(DetailScreen.WatchHistory, nav.detailBackStack.last())
            assertEquals(null, nav.fullScreenGridState)
            nav.popDetailStack()
            assertTrue(nav.openMobileExploreShelf(base, "Finite"))
            assertTrue(nav.fullScreenGridState!!.mobileFiniteResults)
            assertEquals(listOf(item), nav.fullScreenGridState!!.initialResults)
        }
    }
}
