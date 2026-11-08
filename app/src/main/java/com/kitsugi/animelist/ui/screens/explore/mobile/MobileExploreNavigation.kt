package com.kitsugi.animelist.ui.screens.explore.mobile

import com.kitsugi.animelist.DetailScreen
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.ui.app.FullScreenMediaGridState

/**
 * Use only from the new mobile Explore screen. It deliberately does not change the existing
 * full-grid callback used by search, TV or legacy screens. Continue-watching is a history
 * route, while unverified catalog/personal queries open a clearly labelled finite grid.
 */
fun AppNavigationState.openMobileExploreShelf(shelf: MobileExploreShelfData, title: String): Boolean {
    when (mobileShelfAction(shelf)) {
        MobileShelfAction.NONE -> return false
        MobileShelfAction.WATCH_HISTORY -> navigateToDetail(DetailScreen.WatchHistory)
        MobileShelfAction.FINITE_GRID_REQUIRES_UI -> {
            fullScreenGridState = mobileFiniteGridState(shelf, title)
        }
    }
    return true
}

fun mobileFiniteGridState(shelf: MobileExploreShelfData, title: String): FullScreenMediaGridState {
    require(mobileShelfAction(shelf) == MobileShelfAction.FINITE_GRID_REQUIRES_UI)
    return FullScreenMediaGridState(
        title = title,
        categoryType = shelf.category,
        platform = shelf.source,
        initialResults = shelf.results.toList(),
        mobileFiniteResults = true,
    )
}
