package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Session-only auto-scroll. A user gesture takes priority; we resume after it ends. */
@Composable
internal fun WebtoonAutoScroll(
    enabled: Boolean,
    listState: LazyListState,
    speedDpPerSecond: Int,
    onEnd: () -> Unit,
) {
    val pixelsPerStep = autoScrollStepPixels(speedDpPerSecond, LocalDensity.current.density)
    val currentOnEnd by rememberUpdatedState(onEnd)
    LaunchedEffect(enabled, listState, pixelsPerStep) {
        if (!enabled) return@LaunchedEffect
        while (isActive) {
            delay(50)
            if (listState.isScrollInProgress) continue
            if (!listState.canScrollForward) {
                currentOnEnd()
                break
            }
            // A user drag may cancel this mutation; isolating it in a child means
            // the auto-scroll loop is not cancelled and can resume afterwards.
            coroutineScope { launch { listState.scrollBy(pixelsPerStep) }.join() }
        }
    }
}

/** 50 ms step, measured in screen-independent pixels per second. */
internal fun autoScrollStepPixels(speedDpPerSecond: Int, density: Float): Float =
    speedDpPerSecond.coerceIn(10, 160) * density.coerceAtLeast(0f) / 20f
