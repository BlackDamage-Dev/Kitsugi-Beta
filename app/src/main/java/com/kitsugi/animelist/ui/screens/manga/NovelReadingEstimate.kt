package com.kitsugi.animelist.ui.screens.manga

internal data class NovelReadingEstimate(val percent: Int, val minutesRemaining: Int)

/** Segment-boundary estimate, not precise text position; 1,000 UTF-16 units/minute. */
internal fun novelReadingOffsets(segments: List<String>): LongArray {
    val offsets = LongArray(segments.size + 1)
    for (i in segments.indices) offsets[i + 1] = offsets[i] + segments[i].length.toLong() + 1L
    return offsets
}

internal fun novelReadingEstimate(offsets: LongArray, index: Int, atEnd: Boolean): NovelReadingEstimate {
    if (offsets.size < 2 || offsets.last() <= 0) return NovelReadingEstimate(0, 0)
    val total = offsets.last()
    val current = if (atEnd) total else offsets[index.coerceIn(0, offsets.lastIndex - 1)].coerceIn(0L, total)
    val remaining = total - current
    return NovelReadingEstimate(((current.toDouble() / total) * 100).toInt().coerceIn(0, 100), ((remaining + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
}

/** Locate the segment containing a character-proportional book position. */
internal fun novelSegmentAtPercent(offsets: LongArray, percent: Float): Int {
    if (offsets.size < 2 || offsets.last() <= 0 || !percent.isFinite()) return 0
    val target = (offsets.last() * (percent.coerceIn(0f, 100f) / 100.0)).toLong()
    var low = 0
    var high = offsets.lastIndex - 1
    while (low < high) {
        val middle = low + (high - low + 1) / 2
        if (offsets[middle] <= target) low = middle else high = middle - 1
    }
    return low
}
