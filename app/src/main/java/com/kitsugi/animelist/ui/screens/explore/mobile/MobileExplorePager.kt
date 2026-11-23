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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
    tabs: List<MobileExploreTab> = MobileExploreTab.entries,
    onActiveListChanged: (LazyListState) -> Unit = {},
) {
    val effectiveTabs = tabs.ifEmpty { MobileExploreTab.entries }
    var preferredTabName by rememberSaveable { mutableStateOf(effectiveTabs.first().name) }
    val pagerState = rememberPagerState(
        initialPage = effectiveTabs.indexOfFirst { it.name == preferredTabName }.coerceAtLeast(0),
        pageCount = { effectiveTabs.size },
    )
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage, effectiveTabs) {
        effectiveTabs.getOrNull(pagerState.currentPage)?.let { current ->
            preferredTabName = current.name
        }
    }

    LaunchedEffect(effectiveTabs) {
        val targetIdx = effectiveTabs.indexOfFirst { it.name == preferredTabName }
        when {
            targetIdx >= 0 && targetIdx != pagerState.currentPage -> pagerState.scrollToPage(targetIdx)
            targetIdx < 0 -> {
                // If the previous tab (e.g. SERIES) isn't supported by the newly selected
                // anime-focused provider, prefer ANIME when available so users land on rich content.
                val fallbackIdx = effectiveTabs.indexOf(MobileExploreTab.ANIME).takeIf { it >= 0 } ?: 0
                preferredTabName = effectiveTabs[fallbackIdx].name
                if (pagerState.currentPage != fallbackIdx) {
                    pagerState.scrollToPage(fallbackIdx)
                }
            }
        }
    }

    Column(modifier = modifier) {
        val safeSelected = pagerState.currentPage.coerceIn(0, (effectiveTabs.size - 1).coerceAtLeast(0))
        tabBar(safeSelected) { index ->
            if (index in effectiveTabs.indices) {
                preferredTabName = effectiveTabs[index].name
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            key = { index -> effectiveTabs[index].name },
        ) { index ->
            val tab = effectiveTabs[index]
            stateHolder.SaveableStateProvider("${selectedSource.name}:${tab.name}") {
                val listState = rememberMobileRetainedScrollState(contentReady(tab))
                if (index == pagerState.currentPage) {
                    LaunchedEffect(listState, selectedSource, tab) { onActiveListChanged(listState) }
                }
                page(tab, listState)
            }
        }
    }
}
