package com.kitsugi.animelist.ui.screens.manga.components

/** Mean of sufficiently opaque edge pixels; transparent or invalid images leave the fallback. */
internal fun readerAverageEdgeColor(pixels: IntArray, width: Int, height: Int): Int? {
    if (width <= 0 || height <= 0 || width.toLong() * height > pixels.size) return null
    var red = 0L; var green = 0L; var blue = 0L; var count = 0L
    for (y in 0 until height) for (x in 0 until width) {
        if (x != 0 && y != 0 && x != width - 1 && y != height - 1) continue
        val color = pixels[y * width + x]
        if ((color ushr 24) < 230) continue
        red += (color ushr 16) and 255; green += (color ushr 8) and 255; blue += color and 255; count++
    }
    if (count == 0L) return null
    return (0xff000000L or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)).toInt()
}
