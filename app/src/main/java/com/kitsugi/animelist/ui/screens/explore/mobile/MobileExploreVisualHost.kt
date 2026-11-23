package com.kitsugi.animelist.ui.screens.explore.mobile

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiScrollToTopFab
import com.kitsugi.animelist.ui.navigation.LocalNuvioOverHero
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.ScrollVisibilityState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val MAX_HERO_CAROUSEL_ITEMS = 8

/**
 * Collects up to [MAX_HERO_CAROUSEL_ITEMS] distinct hero showcase items for a tab page across
 * all loaded sources, always putting [page.hero] first when present.
 */
private fun collectHeroCarouselItems(page: MobileExplorePageData): List<JikanSearchResult> {
    val result = mutableListOf<JikanSearchResult>()
    val seenKeys = hashSetOf<String>()
    fun tryAdd(candidate: JikanSearchResult) {
        if (result.size >= MAX_HERO_CAROUSEL_ITEMS) return
        val hasArt = !candidate.backdropUrl.isNullOrBlank() || !candidate.imageUrl.isNullOrBlank()
        if (!hasArt && result.isNotEmpty()) return
        val identity = "${candidate.source.lowercase()}:${candidate.malId}:${candidate.title.lowercase()}"
        if (seenKeys.add(identity)) {
            result += candidate
        }
    }
    page.hero?.let(::tryAdd)
    val catalogShelves = page.shelves.filter { it.origin != MobileShelfOrigin.SIMKL_CONTINUE }
    // First pass: prefer items with wide backdrop artwork across shelves (round-robin by shelf)
    val maxDepth = catalogShelves.maxOfOrNull { it.results.size } ?: 0
    for (idx in 0 until minOf(maxDepth, 4)) {
        for (shelf in catalogShelves) {
            val item = shelf.results.getOrNull(idx) ?: continue
            if (!item.backdropUrl.isNullOrBlank()) tryAdd(item)
            if (result.size >= MAX_HERO_CAROUSEL_ITEMS) return result
        }
    }
    // Second pass: fill remaining slots with any item that has poster artwork
    for (idx in 0 until minOf(maxDepth, 4)) {
        for (shelf in catalogShelves) {
            val item = shelf.results.getOrNull(idx) ?: continue
            tryAdd(item)
            if (result.size >= MAX_HERO_CAROUSEL_ITEMS) return result
        }
    }
    return result
}

/**
 * Mobile/Tablet Discover visual host combining NuvioMobile's edge-to-edge hero carousel,
 * floating glass top bar + category pills, 16:9 Continue Watching cards, and clean catalog shelves
 * across all 7 Kitsugi sources.
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

    val isScrolledPastTop by remember(activeList) {
        derivedStateOf {
            val state = activeList ?: return@derivedStateOf false
            state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 96
        }
    }

    LaunchedEffect(activeList) {
        val state = activeList ?: return@LaunchedEffect
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> if (index == 0 && offset == 0) currentReset() }
    }

    Box(modifier = modifier) {
        MobileExploreHost(
            selectedSource = selectedSource,
            content = content,
            modifier = Modifier.fillMaxSize(),
            overlayTabBar = true,
            tabBar = { selectedIndex, onSelected ->
                val activePageSupported = content.pages.getOrNull(selectedIndex)?.supported ?: true
                val overHeroNow = !isScrolledPastTop && activePageSupported
                val effectiveScrimColor by animateColorAsState(
                    targetValue = if (overHeroNow) Color.Transparent else KitsugiColors.Background.copy(alpha = 0.94f),
                    animationSpec = tween(durationMillis = 220),
                    label = "explore_topbar_scrim",
                )
                CompositionLocalProvider(LocalNuvioOverHero provides overHeroNow) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(effectiveScrimColor)
                            .statusBarsPadding()
                            .padding(top = if (isLandscape) 6.dp else 4.dp, bottom = 4.dp),
                    ) {
                        MobileExploreHeader(
                            selectedPlatform = selectedSource,
                            onSourceClick = onSourceClick,
                            actions = actions,
                            isLandscapeTopBar = isLandscape,
                            overHero = overHeroNow,
                        )
                        MobileExploreTabs(
                            selectedIndex = selectedIndex,
                            onTabSelected = onSelected,
                            overHero = overHeroNow,
                        )
                    }
                }
            },
            hero = { item, isLoading ->
                MobileExploreHero(
                    item = item,
                    isLoading = isLoading,
                    blurAdultMedia = blurAdultMedia,
                    onItemClick = onItemClick,
                    titleLanguage = titleLanguage,
                    scoreFormat = scoreFormat,
                    hideScores = hideScores,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            },
            heroCarousel = { page, isLoading ->
                val heroItems = remember(page) { collectHeroCarouselItems(page) }
                MobileExploreHero(
                    item = page.hero,
                    isLoading = isLoading,
                    blurAdultMedia = blurAdultMedia,
                    onItemClick = onItemClick,
                    heroItems = heroItems,
                    titleLanguage = titleLanguage,
                    scoreFormat = scoreFormat,
                    hideScores = hideScores,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
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
                    isContinueWatching = data.origin == MobileShelfOrigin.SIMKL_CONTINUE,
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
