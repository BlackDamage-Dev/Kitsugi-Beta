package com.kitsugi.animelist.ui.screens.manga.components

/** Only clearly landscape pages are rotated; square/portrait pages are untouched. */
internal fun readerWideRotationDegrees(width: Int, height: Int, preference: String): Int {
    if (width <= 0 || height <= 0 || width.toLong() * 5 < height.toLong() * 6) return 0
    return when (preference) { "Clockwise" -> 90; "CounterClockwise" -> -90; else -> 0 }
}
