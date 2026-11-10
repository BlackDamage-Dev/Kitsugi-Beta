package com.kitsugi.animelist.ui.screens.manga

/** -1 retains the existing adaptive defaults; explicit values remain bounded. */
internal fun readerPreloadCount(preference: Int, webtoon: Boolean): Int =
    if (preference < 0) if (webtoon) 3 else 2 else preference.coerceIn(0, 5)

internal fun readerScrollFraction(percent: Int): Float = percent.coerceIn(25, 100) / 100f

private val progressTimestamp = java.util.concurrent.atomic.AtomicLong(0)

/** Strictly increasing in this process, even for multiple events in one millisecond. */
internal fun nextReaderProgressTimestamp(): Long = progressTimestamp.updateAndGet { previous ->
    maxOf(System.currentTimeMillis(), if (previous == Long.MAX_VALUE) previous else previous + 1)
}
