package com.kitsugi.animelist.ui.screens.manga

import kotlin.math.roundToInt

/** Map a normalized seek position to an EPUB spine index. Chapters need not be equal lengths. */
internal fun epubChapterForFraction(fraction: Float, chapterCount: Int): Int {
    if (chapterCount <= 1 || !fraction.isFinite()) return 0
    return (fraction.coerceIn(0f, 1f) * (chapterCount - 1)).roundToInt().coerceIn(0, chapterCount - 1)
}
