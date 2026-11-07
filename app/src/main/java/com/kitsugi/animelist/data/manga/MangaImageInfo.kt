package com.kitsugi.animelist.data.manga

/** Header metadata, not a decoded full-size bitmap. Orientation follows EXIF (1..8). */
data class MangaImageInfo(val rawWidth: Int, val rawHeight: Int, val orientation: Int = 1) {
    init { require(rawWidth > 0 && rawHeight > 0) }
    val width: Int get() = if (orientation in 5..8) rawHeight else rawWidth
    val height: Int get() = if (orientation in 5..8) rawWidth else rawHeight

    /** Map an upright, full-width strip to the corresponding rectangle in the encoded image. */
    fun sourceRegion(top: Int, bottom: Int): ImageRegion {
        require(top in 0 until bottom && bottom <= height)
        return when (orientation) {
            3, 4 -> ImageRegion(0, rawHeight - bottom, rawWidth, rawHeight - top)
            5, 6 -> ImageRegion(top, 0, bottom, rawHeight)
            7, 8 -> ImageRegion(rawWidth - bottom, 0, rawWidth - top, rawHeight)
            else -> ImageRegion(0, top, rawWidth, bottom)
        }
    }
}

data class ImageRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
