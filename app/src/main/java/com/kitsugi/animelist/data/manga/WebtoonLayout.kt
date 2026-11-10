package com.kitsugi.animelist.data.manga

import kotlin.math.roundToInt

/**
 * One logical page can contain many lazy drawing tiles. These are NOT nested scrolling pages:
 * all tiles live in one LazyColumn, at the same width and exact source aspect ratio.
 */
class WebtoonLayout(val images: List<MangaImageInfo?>) {
    data class Tile(
        val pageIndex: Int,
        val part: Int,
        val top: Int,
        val bottom: Int,
        val image: MangaImageInfo?,
        val isLast: Boolean,
    ) {
        val key: String get() = "$pageIndex:$part"
        // Round shared boundaries, not every individual height: no cumulative 1px seams/drift.
        fun heightPx(widthPx: Int): Int = image?.let {
            val scale = widthPx.toDouble() / it.width
            ((bottom * scale).roundToInt() - (top * scale).roundToInt()).coerceAtLeast(1)
        } ?: widthPx.coerceAtLeast(1)
    }

    private val starts = IntArray(images.size)
    val tiles: List<Tile> = buildList {
        images.forEachIndexed { page, image ->
            starts[page] = size
            if (image == null) {
                add(Tile(page, 0, 0, 0, null, true))
            } else {
                // Square source tiles keep each layout and decoded bitmap bounded even at 1:1000.
                var top = 0
                var part = 0
                while (top < image.height) {
                    val bottom = (top.toLong() + image.width).coerceAtMost(image.height.toLong()).toInt()
                    add(Tile(page, part++, top, bottom, image, bottom == image.height))
                    top = bottom
                }
            }
        }
    }

    fun firstTile(pageIndex: Int): Int = starts.getOrElse(pageIndex) { 0 }

    data class Position(val pageIndex: Int, val fraction: Float = 0f)
    data class Target(val itemIndex: Int, val offsetPx: Int)

    fun position(itemIndex: Int, itemOffsetPx: Int, contentWidthPx: Int): Position {
        val tile = tiles.getOrNull(itemIndex) ?: return Position((images.size - 1).coerceAtLeast(0), 1f)
        val image = tile.image ?: return Position(tile.pageIndex)
        val localFraction = (itemOffsetPx.toFloat() / tile.heightPx(contentWidthPx)).coerceIn(0f, 1f)
        val sourceY = tile.top + localFraction * (tile.bottom - tile.top)
        return Position(tile.pageIndex, (sourceY / image.height).coerceIn(0f, 1f))
    }

    /** Fractions, rather than raw display pixels, survive density, rotation, margins and tiling. */
    fun target(pageIndex: Int, fraction: Float, widthPx: Int): Target {
        if (images.isEmpty()) return Target(0, 0)
        val page = pageIndex.coerceIn(images.indices)
        val image = images[page] ?: return Target(firstTile(page), 0)
        val safeFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        val y = safeFraction.toDouble() * image.height
        val part = (y / image.width).toInt().coerceAtMost((image.height - 1) / image.width)
        val index = firstTile(page) + part
        val tile = tiles[index]
        val offset = ((y - tile.top) / (tile.bottom - tile.top) * tile.heightPx(widthPx)).roundToInt()
        return Target(index, offset)
    }

    companion object {
        const val MAX_DECODE_EDGE = 2048

        /** Power-of-two subsampling with an absolute texture/memory bound. */
        fun sampleSize(width: Int, height: Int, targetWidth: Int): Int {
            require(width > 0 && height > 0)
            val target = targetWidth.coerceIn(1, MAX_DECODE_EDGE)
            var sample = 1
            while (width / (sample.toLong() * 2) >= target) sample *= 2
            while ((width.toLong() + sample - 1) / sample > MAX_DECODE_EDGE ||
                (height.toLong() + sample - 1) / sample > MAX_DECODE_EDGE
            ) sample *= 2
            return sample
        }

        fun scrollDistance(viewportPx: Int, percent: Int): Float =
            viewportPx.coerceAtLeast(0) * percent.coerceIn(25, 100) / 100f
    }
}
