package com.kitsugi.animelist.ui.screens.manga.components

import com.kitsugi.animelist.data.manga.ReadingMode

internal enum class MangaReaderTapAction { Previous, Menu, Next }

/** Vertical pages use top/bottom zones; continuous scrolling must never jump on a tap. */
internal fun readerTapAction(mode: ReadingMode, xFraction: Float, yFraction: Float): MangaReaderTapAction {
    if (mode == ReadingMode.Webtoon) return MangaReaderTapAction.Menu
    val fraction = if (mode == ReadingMode.Vertical) yFraction else xFraction
    val action = when {
        fraction < 1f / 3f -> MangaReaderTapAction.Previous
        fraction > 2f / 3f -> MangaReaderTapAction.Next
        else -> MangaReaderTapAction.Menu
    }
    return if (mode == ReadingMode.RightToLeft) when (action) {
        MangaReaderTapAction.Previous -> MangaReaderTapAction.Next
        MangaReaderTapAction.Next -> MangaReaderTapAction.Previous
        MangaReaderTapAction.Menu -> action
    } else action
}
