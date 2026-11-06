package com.kitsugi.animelist.ui.screens.manga.components

/** Exclusive pixel rectangle in a sampled thumbnail. */
internal data class ReaderCropBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Conservative light/dark solid-border detector. All sampled pixels in each
 * removed row/column must match the four corners; uncertain images are left alone.
 */
internal fun readerCropBounds(pixels: IntArray, width: Int, height: Int): ReaderCropBounds? {
    if (width < 16 || height < 16 || width.toLong() * height != pixels.size.toLong()) return null
    val reference = pixels[0]
    fun channel(c: Int, shift: Int) = c ushr shift and 255
    fun similar(c: Int): Boolean = channel(c, 24) >= 250 &&
        kotlin.math.abs(channel(c, 16) - channel(reference, 16)) <= 12 &&
        kotlin.math.abs(channel(c, 8) - channel(reference, 8)) <= 12 &&
        kotlin.math.abs(channel(c, 0) - channel(reference, 0)) <= 12
    if (channel(reference, 24) < 250) return null
    val channels = listOf(channel(reference, 16), channel(reference, 8), channel(reference, 0))
    if (!(channels.all { it >= 240 } || channels.all { it <= 15 })) return null
    if (!similar(pixels[width - 1]) || !similar(pixels[(height - 1) * width]) || !similar(pixels.last())) return null
    if (pixels.all(::similar)) return null // Blank pages should not be resized.
    fun row(y: Int) = (0 until width).all { x -> similar(pixels[y * width + x]) }
    fun col(x: Int) = (0 until height).all { y -> similar(pixels[y * width + x]) }
    val maxX = width / 4
    val maxY = height / 4
    var left = 0; var right = width; var top = 0; var bottom = height
    while (top < maxY && row(top)) top++
    while (height - bottom < maxY && row(bottom - 1)) bottom--
    while (left < maxX && col(left)) left++
    while (width - right < maxX && col(right - 1)) right--
    // Keep a small safety margin around the detected content.
    left = (left - maxOf(2, width / 100)).coerceAtLeast(0)
    right = (right + maxOf(2, width / 100)).coerceAtMost(width)
    top = (top - maxOf(2, height / 100)).coerceAtLeast(0)
    bottom = (bottom + maxOf(2, height / 100)).coerceAtMost(height)
    return if (left == 0 && right == width && top == 0 && bottom == height) null
    else ReaderCropBounds(left, top, right, bottom)
}
