package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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
) {
    // Reject malformed input early rather than pairing a tab with another tab's hero/results.
    require(content.pages.map { it.tab } == MobileExploreTab.entries)
    MobileExplorePager(
        selectedSource = selectedSource,
        contentReady = { tab -> content.pages[tab.ordinal].readyForScrollRestore },
        tabBar = tabBar,
        modifier = modifier,
        onActiveListChanged = onActiveListChanged,
        page = { tab, listState ->
            val page = content.pages[tab.ordinal]
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("mobile_explore_page_list"),
                // Matches the existing Explore list's clearance above bottom navigation.
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item(key = "mobile_hero_${tab.name}") {
                    val loading = page.hero == null && (page.awaitingSourceState || page.loadingSources.isNotEmpty())
                    hero(page.hero, loading)
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
