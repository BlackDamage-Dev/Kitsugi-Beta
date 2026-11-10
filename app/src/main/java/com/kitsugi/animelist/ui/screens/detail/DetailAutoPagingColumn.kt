package com.kitsugi.animelist.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pager neighbours must not reveal more items while a different tab is selected. */
internal val LocalDetailPagingActive = staticCompositionLocalOf { true }

/**
 * Incremental rendering for lists inside the detail page's existing vertical scroller.
 * Do not nest a vertically scrolling LazyColumn here: portrait pages are measured with
 * unbounded height by the outer LazyColumn/pager. Data is already fetched by the tab's
 * ViewModel; this pages its presentation, without extra requests or a load-more button.
 *
 * The penultimate row is the prefetch marker. boundsInWindow is clipped by the parent
 * viewport, so merely composing an off-screen row (or a neighbouring page) cannot drain
 * the list. A new marker is installed after each batch; it must actually become visible
 * before the next batch can be revealed. Existing rows keep their composition identity.
 */
@Composable
internal fun <T> DetailAutoPagingColumn(
    items: List<T>,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    spacing: Dp = 10.dp,
    batchSize: Int = 12,
    active: Boolean = LocalDetailPagingActive.current,
    itemContent: @Composable (T) -> Unit
) {
    require(columns > 0)
    require(batchSize > 0)
    var revealedCount by rememberSaveable(items, batchSize) { mutableIntStateOf(batchSize) }
    val visibleCount = revealedCount.coerceAtMost(items.size)
    val hasMore = visibleCount < items.size
    val rowCount = (visibleCount + columns - 1) / columns
    val markerRow = (rowCount - 2).coerceAtLeast(0)
    var markerVisible by remember(items, visibleCount, columns) { mutableStateOf(false) }
    LaunchedEffect(markerVisible, active, visibleCount) {
        if (active && markerVisible && hasMore) {
            revealedCount = nextDetailPageCount(visibleCount, items.size, batchSize)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        repeat(rowCount) { row ->
            key(row) {
                Row(
                    modifier = Modifier.fillMaxWidth().then(
                        if (hasMore && row == markerRow) {
                            Modifier.onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                markerVisible = bounds.width > 0f && bounds.height > 0f
                            }
                        } else Modifier
                    ),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    repeat(columns) { column ->
                        val index = row * columns + column
                        if (index < visibleCount) {
                            Box(Modifier.weight(1f)) { itemContent(items[index]) }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
