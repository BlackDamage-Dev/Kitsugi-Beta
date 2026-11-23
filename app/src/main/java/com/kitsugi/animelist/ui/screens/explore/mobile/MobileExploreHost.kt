package com.kitsugi.animelist.ui.screens.explore.mobile

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform

/**
 * Mobile screen plumbing, deliberately independent of the visual agent's files.
 * The owner supplies visual slots, source/status actions, and detail/navigation callbacks.
 * No API requests, source selection, routing, error invention or theme changes happen here.
 */
@Composable
internal fun MobileExploreHost(
    selectedSource: ExplorePlatform,
    content: MobileExploreContent,
    tabBar: @Composable (selectedIndex: Int, onSelected: (Int) -> Unit) -> Unit,
    hero: @Composable (item: JikanSearchResult?, isLoading: Boolean) -> Unit,
    shelf: @Composable (data: MobileExploreShelfData, onSeeAllClick: (() -> Unit)?) -> Unit,
    sourceStatus: @Composable (tab: MobileExploreTab, status: MobileSourceStatus) -> Unit,
    emptyPage: @Composable (page: MobileExplorePageData) -> Unit,
    onOpenShelf: (MobileExploreShelfData) -> Unit,
    modifier: Modifier = Modifier,
    extraItems: LazyListScope.(MobileExplorePageData) -> Unit = {},
    onActiveListChanged: (androidx.compose.foundation.lazy.LazyListState) -> Unit = {},
    overlayTabBar: Boolean = false,
    heroCarousel: (@Composable (page: MobileExplorePageData, isLoading: Boolean) -> Unit)? = null,
) {
    // Reject malformed input early rather than pairing a tab with another tab's hero/results.
    require(content.pages.map { it.tab } == MobileExploreTab.entries)
    MobileExplorePager(
        selectedSource = selectedSource,
        contentReady = { tab -> content.pages[tab.ordinal].readyForScrollRestore },
        tabBar = tabBar,
        modifier = modifier,
        onActiveListChanged = onActiveListChanged,
        overlayTabBar = overlayTabBar,
        page = { tab, listState ->
            val page = content.pages[tab.ordinal]
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("mobile_explore_page_list"),
                // Matches clearance above the floating bottom navigation pill (portrait) or gesture bar (landscape).
                contentPadding = PaddingValues(
                    bottom = (if (isLandscape) 36.dp else 104.dp) + navBarBottom
                ),
            ) {
                if (overlayTabBar && !page.supported) {
                    item(key = "mobile_top_spacer_${tab.name}") {
                        Spacer(
                            modifier = Modifier
                                .statusBarsPadding()
                                .height(if (isLandscape) 86.dp else 118.dp)
                        )
                    }
                } else {
                    item(key = "mobile_hero_${tab.name}") {
                        val loading = page.hero == null && (page.awaitingSourceState || page.loadingSources.isNotEmpty())
                        if (heroCarousel != null) {
                            heroCarousel(page, loading)
                        } else {
                            hero(page.hero, loading)
                        }
                    }
                }
                extraItems(page)
                items(page.sourceStatuses, key = { "mobile_status_${it.source.name}" }) { status ->
                    sourceStatus(tab, status)
                }
                items(page.shelves, key = { it.key }) { data ->
                    // Stable saveable subtree for a shelf's own horizontal LazyRow position.
                    key(data.key) {
                        val callback: (() -> Unit)? = if (mobileShelfAction(data) == MobileShelfAction.NONE) null else ({ onOpenShelf(data) })
                        shelf(data, callback)
                    }
                }
                if (!page.supported || page.showNoShelves) {
                    item(key = "mobile_empty_${tab.name}") { emptyPage(page) }
                }
            }
        },
    )
}
