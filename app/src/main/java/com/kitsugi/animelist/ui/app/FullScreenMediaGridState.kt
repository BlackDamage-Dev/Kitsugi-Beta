package com.kitsugi.animelist.ui.app

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform

data class FullScreenMediaGridState(
    val title: String,
    val categoryType: ExploreCategoryType,
    val platform: ExplorePlatform,
    val initialResults: List<JikanSearchResult>,
    /** Opt-in for the new mobile Explore finite fallback; legacy grids remain unchanged. */
    val mobileFiniteResults: Boolean = false
) {
    // In-memory pagination while a detail page is pushed above the grid.
    var savedStateKey: String? = null
    var openingStackDepth: Int = 0
    var cachedResults: List<JikanSearchResult>? = null
    var cachedPage: Int? = null
    var cachedHasMore: Boolean? = null
}
