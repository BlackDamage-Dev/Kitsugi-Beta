package com.kitsugi.animelist.ui.screens.explore.mobile

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiScrollToTopFab
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.utils.ScrollVisibilityState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Visual-to-host adapter, not yet installed in the real Explore screen. The caller owns
 * the source sheet, actual action buttons, localized titles, status/retry UI and routes.
 * Put it in a height-constrained parent; do not add another horizontal padding around it.
 */
@Composable
internal fun MobileExploreVisualHost(
    selectedSource: ExplorePlatform,
    content: MobileExploreContent,
    onSourceClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    shelfTitle: @Composable (MobileExploreShelfData) -> String = { shelf ->
        mobileExploreShelfTitleRes(shelf)?.let { stringResource(it) } ?: shelf.originalTitle
    },
    alreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: (JikanSearchResult) -> Unit,
    onOpenShelf: (MobileExploreShelfData, String) -> Unit,
    sourceStatus: @Composable (MobileExploreTab, MobileSourceStatus) -> Unit,
    emptyPage: @Composable (MobileExplorePageData) -> Unit,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    modifier: Modifier = Modifier,
    extraItems: LazyListScope.(MobileExplorePageData) -> Unit = {},
    onActiveListChanged: (LazyListState) -> Unit = {},
    showScrollToTopFab: Boolean = false,
    bottomBarScrollState: ScrollVisibilityState? = null,
    isBottomBarVisible: Boolean = true,
    onScrollReset: () -> Unit = {},
) {
    var activeList by remember { mutableStateOf<LazyListState?>(null) }
    val currentReset by rememberUpdatedState(onScrollReset)
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(activeList) {
        val state = activeList ?: return@LaunchedEffect
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> if (index == 0 && offset == 0) currentReset() }
    }
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            MobileExploreHeader(selectedPlatform = selectedSource, onSourceClick = onSourceClick, actions = actions)
            MobileExploreHost(
                selectedSource = selectedSource,
                content = content,
                modifier = Modifier.weight(1f),
                tabBar = { selectedIndex, onSelected ->
                    MobileExploreTabs(selectedIndex = selectedIndex, onTabSelected = onSelected)
                },
                hero = { item, isLoading ->
                    MobileExploreHero(item, isLoading, blurAdultMedia, onItemClick, modifier = Modifier.padding(top = 5.dp, bottom = 5.dp))
                },
                shelf = { data, open ->
                    val title = shelfTitle(data)
                    MobileExploreShelf(
                        title = title,
                        source = data.source,
                        results = data.results,
                        isLoading = false, // This host emits only populated shelves; stale content stays visible.
                        alreadyInList = alreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onItemClick,
                        onLongClickItem = onLongClickItem,
                        onSeeAllClick = open?.let { { onOpenShelf(data, title) } },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores,
                        blurAdultMedia = blurAdultMedia,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                },
                sourceStatus = sourceStatus,
                emptyPage = emptyPage,
                // The slot above uses the host callback only as an availability signal: it
                // captures the composable-localized title and invokes the caller itself.
                onOpenShelf = { },
                extraItems = extraItems,
                onActiveListChanged = { state ->
                    activeList = state
                    onActiveListChanged(state)
                },
            )
        }
        if (showScrollToTopFab) {
            KitsugiScrollToTopFab(
                visible = (activeList?.firstVisibleItemIndex ?: 0) > 1,
                isLandscape = isLandscape,
                bottomBarScrollState = bottomBarScrollState,
                fallbackBottomBarVisible = isBottomBarVisible,
                onClick = {
                    currentReset()
                    activeList?.let { state -> scope.launch { state.animateScrollToItem(0) } }
                },
                modifier = Modifier.align(Alignment.BottomEnd).testTag("mobile_explore_scroll_to_top"),
            )
        }
    }
}
