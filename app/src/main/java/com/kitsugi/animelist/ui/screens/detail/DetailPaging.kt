package com.kitsugi.animelist.ui.screens.detail

/** Advances one presentation batch, never beyond the available data or through Int overflow. */
internal fun nextDetailPageCount(visibleCount: Int, totalCount: Int, batchSize: Int): Int {
    require(visibleCount >= 0)
    require(totalCount >= 0)
    require(batchSize > 0)
    return minOf(totalCount.toLong(), visibleCount.toLong() + batchSize).toInt()
}
