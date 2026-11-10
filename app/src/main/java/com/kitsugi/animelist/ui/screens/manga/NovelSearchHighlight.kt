package com.kitsugi.animelist.ui.screens.manga

/** Non-overlapping, case-insensitive UTF-16 ranges for the same search used by navigation. */
internal fun novelSearchRanges(text: String, query: String): List<IntRange> {
    if (query.isBlank() || query.length > 100 || text.isEmpty()) return emptyList()
    val result = mutableListOf<IntRange>()
    var start = 0
    while (start <= text.length - query.length) {
        val index = text.indexOf(query, startIndex = start, ignoreCase = true)
        if (index < 0) break
        result.add(index until index + query.length)
        start = index + query.length
    }
    return result
}
