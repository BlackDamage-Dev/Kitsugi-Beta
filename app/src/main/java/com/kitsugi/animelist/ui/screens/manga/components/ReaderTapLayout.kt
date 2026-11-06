package com.kitsugi.animelist.ui.screens.manga.components

/** Window-relative viewport used when a Webtoon image is taller than the screen. */
data class ReaderTapViewport(val top: Float, val height: Float)

internal fun readerTapY(offset: Float, itemTop: Float, viewport: ReaderTapViewport?, itemHeight: Int): Float =
    if (viewport != null && viewport.height > 0f) (itemTop + offset - viewport.top) / viewport.height
    else offset / itemHeight.coerceAtLeast(1)

internal enum class ReaderTapAction { MENU, NEXT, PREVIOUS, NONE }

internal const val DEFAULT_READER_TAP_GRID = "PMNPMNPMN"
internal const val DEFAULT_READER_LONG_TAP_GRID = "XXXXXXXXX"

internal fun validReaderTapGrid(grid: String): Boolean = grid.length == 9 && grid.all { it in "PMNX" }
internal fun readerTapGridsKeepMenu(tap: String, longPress: String): Boolean =
    validReaderTapGrid(tap) && validReaderTapGrid(longPress) && 'M' in tap

internal fun cycleReaderTapGrid(grid: String, index: Int, direction: Int = 1): String {
    if (!validReaderTapGrid(grid) || index !in 0..8 || direction !in listOf(-1, 1)) return grid
    val actions = "PMNX"
    val next = (actions.indexOf(grid[index]) + direction + actions.length) % actions.length
    return grid.replaceRange(index, index + 1, actions[next].toString())
}

/** Normalized physical coordinates, independent of UI layout direction. */
internal fun readerTapAction(layout: String, rtl: Boolean, inverted: Boolean, x: Float, y: Float,
    customGrid: String = DEFAULT_READER_TAP_GRID): ReaderTapAction {
    if (!x.isFinite() || !y.isFinite()) return ReaderTapAction.MENU
    val px = x.coerceIn(0f, 1f)
    val py = y.coerceIn(0f, 1f)
    val base = when (layout) {
        "Custom" -> {
            val grid = if (validReaderTapGrid(customGrid)) customGrid else DEFAULT_READER_TAP_GRID
            val column = (px * 3).toInt().coerceIn(0, 2)
            val row = (py * 3).toInt().coerceIn(0, 2)
            when (grid[row * 3 + column]) {
                'P' -> ReaderTapAction.PREVIOUS
                'N' -> ReaderTapAction.NEXT
                'X' -> ReaderTapAction.NONE
                else -> ReaderTapAction.MENU
            }
        }
        // The bottom half is easy to reach with either thumb; the top corners
        // keep both the menu and previous action available without gestures.
        "LeftHand" -> if (py >= 0.5f) ReaderTapAction.NEXT else if (px < 0.5f) ReaderTapAction.MENU else ReaderTapAction.PREVIOUS
        "RightHand" -> if (py >= 0.5f) ReaderTapAction.NEXT else if (px >= 0.5f) ReaderTapAction.MENU else ReaderTapAction.PREVIOUS
        else -> if (px in 0.33f..0.66f) ReaderTapAction.MENU
            else if ((px < 0.33f) == rtl) ReaderTapAction.NEXT else ReaderTapAction.PREVIOUS
    }
    return if (!inverted || base == ReaderTapAction.MENU || base == ReaderTapAction.NONE) base
    else if (base == ReaderTapAction.NEXT) ReaderTapAction.PREVIOUS else ReaderTapAction.NEXT
}
