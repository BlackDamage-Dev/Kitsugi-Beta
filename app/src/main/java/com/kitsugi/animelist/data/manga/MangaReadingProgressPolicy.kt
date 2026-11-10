package com.kitsugi.animelist.data.manga

/** Shared completion rule; entering the first pixel of a long last image is never enough. */
object MangaReadingProgressPolicy {
    fun isComplete(mode: ReadingMode, pageIndex: Int, total: Int, bottomReached: Boolean?, alreadyComplete: Boolean): Boolean {
        if (alreadyComplete) return true
        if (total <= 0 || pageIndex !in 0 until total) return false
        return bottomReached ?: (mode != ReadingMode.Webtoon && pageIndex == total - 1)
    }
}
