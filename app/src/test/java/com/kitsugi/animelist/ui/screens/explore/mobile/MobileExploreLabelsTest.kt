package com.kitsugi.animelist.ui.screens.explore.mobile

import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.ExplorePayload
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.screens.explore.sourceSections
import org.junit.Assert.*
import org.junit.Test

class MobileExploreLabelsTest {
    @Test fun allExistingCatalogTitlesHaveResourceMappings() {
        val p = ExplorePayload(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        for (platform in ExplorePlatform.sources) {
            for (section in sourceSections(platform, p)) {
                assertTrue("$platform ${section.title}", mobileExploreCatalogTitleRes(section.title) != null)
            }
        }
        // SIMKL's upcoming anime is deliberately added by the mobile mapper.
        assertEquals(R.string.explore_source_upcoming_anime, mobileExploreCatalogTitleRes("Yaklaşan Animeler"))
        assertEquals(null, mobileExploreCatalogTitleRes("unknown catalog title"))
    }

    @Test fun personalTitlesUseOriginAndTypeNotCategoryOrLocalizedText() {
        val base = MobileExploreShelfData(MobileExploreTab.SERIES, ExplorePlatform.SIMKL, ExplorePlatform.TMDB,
            ExploreCategoryType.AIRING_ANIME, MobileShelfOrigin.SIMKL_CONTINUE, "", emptyList(), true)
        assertEquals(R.string.explore_simkl_continue_watching_series, mobileExploreShelfTitleRes(base))
        assertEquals(R.string.explore_simkl_continue_watching_movies, mobileExploreShelfTitleRes(base.copy(tab = MobileExploreTab.MOVIES)))
        assertEquals(R.string.explore_simkl_plantowatch_series, mobileExploreShelfTitleRes(base.copy(origin = MobileShelfOrigin.SIMKL_PLANNED)))
        assertEquals(R.string.explore_simkl_plantowatch_movies, mobileExploreShelfTitleRes(base.copy(origin = MobileShelfOrigin.SIMKL_PLANNED, tab = MobileExploreTab.MOVIES)))
        assertEquals(null, mobileExploreShelfTitleRes(base.copy(tab = MobileExploreTab.ANIME)))
        assertEquals(null, mobileExploreShelfTitleRes(base.copy(tab = MobileExploreTab.MANGA)))
    }
}
