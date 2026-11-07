package com.kitsugi.animelist.ui.screens.manga.reader

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.kitsugi.animelist.data.manga.WebtoonLayout
import com.kitsugi.animelist.data.manga.WebtoonViewportTransform
import kotlin.math.abs

/** One transform and one scrolling owner for the ENTIRE chapter, never one per image. */
class MangaWebtoonState(val listState: LazyListState) {
    var layout by mutableStateOf(WebtoonLayout(emptyList()))
        internal set
    var viewport by mutableStateOf(IntSize.Zero)
        internal set
    var contentWidthPx by mutableIntStateOf(0)
        internal set
    var zoom by mutableFloatStateOf(1f)
        private set
    var panX by mutableFloatStateOf(0f)
        private set
    var isTransforming by mutableStateOf(false)
        internal set

    fun transform(factor: Float, centroid: Offset, pan: Offset = Offset.Zero) {
        val transform = WebtoonViewportTransform.calculate(
            zoom, panX, factor, centroid.x, centroid.y, pan.x, pan.y, viewport.width,
        )
        zoom = transform.zoom
        panX = transform.panX
        listState.dispatchRawDelta(transform.scrollDelta)
    }

    fun zoomBy(factor: Float) = transform(factor, Offset(viewport.width / 2f, viewport.height / 2f))
    fun resetZoom() = zoomBy(1f / zoom)
    fun panBy(delta: Float) = transform(1f, Offset.Zero, Offset(delta, 0f))

    fun position(): WebtoonLayout.Position = layout.position(
        listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, contentWidthPx,
    )

    suspend fun scrollToPage(page: Int, fraction: Float = 0f) {
        val target = layout.target(page, fraction, contentWidthPx.coerceAtLeast(1))
        listState.scrollToItem(target.itemIndex, target.offsetPx)
    }

    suspend fun advance(direction: Int, percent: Int, animate: Boolean = true) {
        val logicalViewport = (viewport.height / zoom).toInt()
        val distance = WebtoonLayout.scrollDistance(logicalViewport, percent) * direction
        if (animate) listState.animateScrollBy(distance)
        else listState.dispatchRawDelta(distance)
    }

    fun isLayoutSettled(): Boolean {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return false
        return if (first.index == layout.tiles.size) first.key == "chapter_transition_footer"
        else layout.tiles.getOrNull(first.index)?.key == first.key
    }

    fun lastImageBottomVisible(): Boolean {
        val last = layout.tiles.lastIndex
        if (last < 0) return false
        if (listState.firstVisibleItemIndex > last) return true
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == last } ?: return false
        return item.offset + item.size <= info.viewportEndOffset
    }

    fun clampPan() {
        val maxPan = viewport.width * (zoom - 1f) / 2f
        if (abs(panX) > maxPan) panX = panX.coerceIn(-maxPan, maxPan)
    }
}
