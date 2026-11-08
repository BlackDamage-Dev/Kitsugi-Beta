package com.kitsugi.animelist.ui.screens.explore.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// Plain object: updating it during scroll must not recompose the whole page on every frame.
private class MobileScrollSnapshot(var index: Int, var offset: Int)

private val MobileScrollSnapshotSaver = listSaver<MobileScrollSnapshot, Int>(
    save = { listOf(it.index, it.offset) },
    restore = { MobileScrollSnapshot(it[0], it[1]) },
)

/**
 * Preserve the last real position while loading placeholders temporarily clamp LazyListState.
 * The caller must set contentReady=false while the real list is replaced by a placeholder;
 * keeping stale real content on screen should instead pass true.
 */
@Composable
private fun rememberMobileRetainedScrollState(contentReady: Boolean): LazyListState {
    val position = rememberSaveable(saver = MobileScrollSnapshotSaver) { MobileScrollSnapshot(0, 0) }
    val listState = rememberLazyListState(position.index, position.offset)
    LaunchedEffect(listState, contentReady) {
        if (contentReady) {
            if (listState.firstVisibleItemIndex != position.index || listState.firstVisibleItemScrollOffset != position.offset) {
                listState.scrollToItem(position.index, position.offset)
            }
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    position.index = index
                    position.offset = offset
                }
        }
    }
    return listState
}

/**
 * UI-independent pager host. The visual tab strip and page contents are supplied by the
 * integrator; this file neither calls the network nor accesses the other agent's files.
 * It must be placed in a height-constrained mobile parent. Selected page is saveable;
 * each (source, page) has an independent saveable vertical list and row state subtree.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MobileExplorePager(
    selectedSource: ExplorePlatform,
    contentReady: (MobileExploreTab) -> Boolean,
    tabBar: @Composable (selectedIndex: Int, onSelected: (Int) -> Unit) -> Unit,
    page: @Composable (tab: MobileExploreTab, listState: LazyListState) -> Unit,
    modifier: Modifier = Modifier,
    onActiveListChanged: (LazyListState) -> Unit = {},
) {
    val tabs = MobileExploreTab.entries
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    Column(modifier = modifier) {
        tabBar(pagerState.currentPage) { index ->
            if (index in tabs.indices) scope.launch { pagerState.animateScrollToPage(index) }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            key = { index -> tabs[index].name },
        ) { index ->
            val tab = tabs[index]
            stateHolder.SaveableStateProvider("${selectedSource.name}:${tab.name}") {
                val listState = rememberMobileRetainedScrollState(contentReady(tab))
                if (index == pagerState.currentPage) {
                    LaunchedEffect(listState, selectedSource, index) { onActiveListChanged(listState) }
                }
                page(tab, listState)
            }
        }
    }
}
