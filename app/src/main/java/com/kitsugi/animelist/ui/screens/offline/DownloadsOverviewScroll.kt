package com.kitsugi.animelist.ui.screens.offline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Scroll policy for the storage overview shared by all three download tabs.
 *
 * Deltas are *consumed list movement*, not raw finger movement: an empty page,
 * a horizontal pager swipe or an overscroll edge cannot hide the card. Travel
 * is accumulated per direction, so tiny back-and-forth movements do not toggle
 * it. Layout changes caused by the card animation are never scroll input and
 * therefore cannot create a hide/show feedback loop.
 */
internal class DownloadsOverviewScrollPolicy(
    private val hideThresholdPx: Float,
    private val revealThresholdPx: Float
) {
    var isVisible: Boolean = true
        private set

    private var directionalTravelPx = 0f

    fun onConsumedScroll(deltaY: Float, atTop: Boolean, hasItems: Boolean): Boolean {
        if (!hasItems || atTop) {
            reset()
            return isVisible
        }
        if (deltaY == 0f || !deltaY.isFinite()) return isVisible

        // A direction change starts a new intention; old travel must not leak
        // into the next gesture and reveal/hide the card unexpectedly.
        if (directionalTravelPx != 0f && (directionalTravelPx > 0f) != (deltaY > 0f)) {
            directionalTravelPx = 0f
        }
        directionalTravelPx += deltaY

        when {
            directionalTravelPx <= -hideThresholdPx -> {
                isVisible = false
                directionalTravelPx = 0f
            }
            directionalTravelPx >= revealThresholdPx -> {
                isVisible = true
                directionalTravelPx = 0f
            }
        }
        return isVisible
    }

    fun endGesture() {
        directionalTravelPx = 0f
    }

    fun reset() {
        isVisible = true
        directionalTravelPx = 0f
    }
}

internal data class DownloadsScrollSnapshot(
    val atTop: Boolean,
    val hasItems: Boolean
)

internal class DownloadsOverviewScrollState internal constructor(
    visible: Boolean,
    val nestedScrollConnection: NestedScrollConnection
) {
    val isVisible: Boolean = visible
}

@Composable
internal fun rememberDownloadsOverviewScrollState(
    activePage: Int,
    videoListState: LazyListState,
    subtitleListState: LazyListState,
    imageGridState: LazyGridState,
    audioListState: LazyListState? = null,
    mangaListState: LazyListState? = null,
    contentRevision: Any? = Unit
): DownloadsOverviewScrollState {
    val density = LocalDensity.current
    val policy = remember(density) {
        DownloadsOverviewScrollPolicy(
            hideThresholdPx = with(density) { 28.dp.toPx() },
            // Revealing needs less travel than hiding: returning to the storage
            // information feels immediate, without reacting to every tiny drag.
            revealThresholdPx = with(density) { 14.dp.toPx() }
        )
    }
    var visible by remember(policy) { mutableStateOf(true) }
    val currentPage by rememberUpdatedState(activePage)

    fun snapshot(): DownloadsScrollSnapshot = if (audioListState != null) {
        when (currentPage) {
            0 -> DownloadsScrollSnapshot(
                atTop = videoListState.firstVisibleItemIndex == 0 &&
                    videoListState.firstVisibleItemScrollOffset == 0,
                hasItems = videoListState.layoutInfo.totalItemsCount > 0
            )
            1 -> DownloadsScrollSnapshot(
                atTop = audioListState.firstVisibleItemIndex == 0 &&
                    audioListState.firstVisibleItemScrollOffset == 0,
                hasItems = audioListState.layoutInfo.totalItemsCount > 0
            )
            2 -> DownloadsScrollSnapshot(
                atTop = subtitleListState.firstVisibleItemIndex == 0 &&
                    subtitleListState.firstVisibleItemScrollOffset == 0,
                hasItems = subtitleListState.layoutInfo.totalItemsCount > 0
            )
            3 -> DownloadsScrollSnapshot(
                atTop = imageGridState.firstVisibleItemIndex == 0 &&
                    imageGridState.firstVisibleItemScrollOffset == 0,
                hasItems = imageGridState.layoutInfo.totalItemsCount > 0
            )
            4 -> if (mangaListState != null) DownloadsScrollSnapshot(
                atTop = mangaListState.firstVisibleItemIndex == 0 &&
                    mangaListState.firstVisibleItemScrollOffset == 0,
                hasItems = mangaListState.layoutInfo.totalItemsCount > 0
            ) else DownloadsScrollSnapshot(atTop = true, hasItems = false)
            else -> DownloadsScrollSnapshot(atTop = true, hasItems = false)
        }
    } else {
        when (currentPage) {
            0 -> DownloadsScrollSnapshot(
                atTop = videoListState.firstVisibleItemIndex == 0 &&
                    videoListState.firstVisibleItemScrollOffset == 0,
                hasItems = videoListState.layoutInfo.totalItemsCount > 0
            )
            1 -> DownloadsScrollSnapshot(
                atTop = subtitleListState.firstVisibleItemIndex == 0 &&
                    subtitleListState.firstVisibleItemScrollOffset == 0,
                hasItems = subtitleListState.layoutInfo.totalItemsCount > 0
            )
            else -> DownloadsScrollSnapshot(
                atTop = imageGridState.firstVisibleItemIndex == 0 &&
                    imageGridState.firstVisibleItemScrollOffset == 0,
                hasItems = imageGridState.layoutInfo.totalItemsCount > 0
            )
        }
    }

    val connection = remember(policy, videoListState, audioListState, subtitleListState, imageGridState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // SideEffect also carries the continuation of a user fling.
                // Accept it so a quick flick hides/reveals the card even when
                // the initial touch travel was below the threshold.
                if ((source == NestedScrollSource.UserInput || source == NestedScrollSource.SideEffect) &&
                    abs(consumed.y) > abs(consumed.x) && consumed.y != 0f
                ) {
                    val position = snapshot()
                    visible = policy.onConsumedScroll(
                        deltaY = consumed.y,
                        atTop = position.atTop,
                        hasItems = position.hasItems
                    )
                }
                // Never consume the gesture: the list keeps its natural speed
                // and fling; only the overview's occupied height animates.
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                policy.endGesture()
                return Velocity.Zero
            }
        }
    }

    // Each tab keeps its own scroll position, but changing tabs always gives
    // the user the storage context again (also covers empty/search-empty tabs).
    LaunchedEffect(activePage, contentRevision, policy) {
        policy.reset()
        visible = true
    }

    // Reaching the real top, or deleting the last item, reveals the card.
    // Do not reset on "fits in viewport": after a collapse the taller viewport
    // may fit a short list, and resetting there would oscillate forever.
    LaunchedEffect(policy, videoListState, audioListState, subtitleListState, imageGridState) {
        snapshotFlow { snapshot() }.collect { position ->
            if (!position.hasItems || position.atTop) {
                policy.reset()
                visible = true
            }
        }
    }

    return DownloadsOverviewScrollState(visible, connection)
}

/**
 * Removes the card *and its vertical padding* from layout, so the tabs and list
 * move into the freed space instead of being covered by a floating panel.
 * A small upward slide + fade is paired with a non-overshooting height tween.
 */
@Composable
internal fun DownloadsOverviewReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.clipToBounds(),
        enter = expandVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top
        ) + fadeIn(
            animationSpec = tween(200, easing = LinearOutSlowInEasing)
        ) + slideInVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing)
        ) { fullHeight -> -fullHeight / 6 },
        exit = shrinkVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top
        ) + fadeOut(
            animationSpec = tween(160, easing = FastOutLinearInEasing)
        ) + slideOutVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        ) { fullHeight -> -fullHeight / 6 },
        label = "downloadsOverviewReveal",
        content = content
    )
}
