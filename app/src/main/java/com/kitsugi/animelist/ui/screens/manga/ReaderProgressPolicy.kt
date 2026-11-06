package com.kitsugi.animelist.ui.screens.manga

internal data class ReaderProgressDecision(val completed: Boolean, val persist: Boolean)

/** A paused session may track completion in memory, but never schedules a new write. */
internal fun readerProgressDecision(
    paused: Boolean,
    previouslyCompleted: Boolean,
    sessionCompleted: Boolean,
    webtoon: Boolean,
    currentPage: Int,
    totalPages: Int
): ReaderProgressDecision {
    val completed = previouslyCompleted || (paused && sessionCompleted) ||
        (!webtoon && totalPages > 0 && currentPage >= totalPages - 1)
    return ReaderProgressDecision(completed, persist = !paused)
}
