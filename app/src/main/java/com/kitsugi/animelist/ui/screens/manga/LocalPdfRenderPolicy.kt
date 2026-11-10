package com.kitsugi.animelist.ui.screens.manga

import kotlin.math.min
import kotlin.math.sqrt

/** Bound both total pixels and longest edge before allocating a page bitmap. */
internal fun localPdfRenderSize(width: Int, height: Int): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val area = width.toDouble() * height.toDouble()
    val scale = min(3.0, min(3000.0 / maxOf(width, height), sqrt(4_000_000.0 / area)))
    if (!scale.isFinite() || scale <= 0) return null
    val w = (width * scale).toInt().coerceIn(1, 3000)
    val h = (height * scale).toInt().coerceIn(1, 3000)
    return w to h
}
