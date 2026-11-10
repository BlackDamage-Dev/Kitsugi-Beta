package com.kitsugi.animelist.ui.screens.manga

/** Replace literal text while retaining segment indices used by progress and bookmarks. */
internal fun applyNovelLiteralReplacement(
    segments: List<String>,
    find: String,
    replacement: String,
    checkCancelled: () -> Unit = {}
): List<String> {
    require(find.isNotEmpty() && find.length <= 100 && replacement.length <= 100) { "Invalid rule" }
    val result = ArrayList<String>(segments.size)
    var total = 0L
    for (segment in segments) {
        checkCancelled()
        val output = StringBuilder(minOf(segment.length, 4096))
        var start = 0
        while (true) {
            val match = segment.indexOf(find, start)
            if (match < 0) {
                val remaining = segment.length - start
                require(output.length + remaining <= 4096) { "Replacement expands a segment too much" }
                output.append(segment, start, segment.length)
                break
            }
            val length = match - start + replacement.length
            require(output.length + length <= 4096) { "Replacement expands a segment too much" }
            output.append(segment, start, match).append(replacement)
            start = match + find.length
        }
        total += output.length
        require(total <= 10L * 1024 * 1024) { "Replacement expands document too much" }
        result.add(output.toString())
    }
    return result
}
