package com.kitsugi.animelist.ui.screens.manga

/** Split long text to fit Android TTS limits without separating UTF-16 surrogate pairs. */
internal fun splitSpeechText(text: String, requestedLimit: Int = 2000): List<String> {
    val limit = requestedLimit.coerceIn(2, 2000)
    val chunks = ArrayList<String>()
    var start = 0
    while (start < text.length) {
        while (start < text.length && text[start].isWhitespace()) start++
        if (start >= text.length) break
        var end = minOf(start + limit, text.length)
        if (end < text.length) {
            val halfway = start + limit / 2
            var boundary = end - 1
            while (boundary >= halfway && !text[boundary].isWhitespace()) boundary--
            if (boundary >= halfway) end = boundary + 1
            if (end < text.length && end > start && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        }
        if (end <= start) end = minOf(start + 2, text.length)
        val chunk = text.substring(start, end).trim()
        if (chunk.isNotEmpty()) chunks.add(chunk)
        start = end
    }
    return chunks
}

/** A speech session can continue only to a valid following chapter. */
internal fun nextSpeechChapter(current: Int, chapterCount: Int, enabled: Boolean): Int? =
    if (enabled && chapterCount > 0 && current >= 0 && current < chapterCount - 1) current + 1 else null

/** Fixed, bounded pitch steps; non-step or corrupt preferences return to the first step. */
internal fun nextNovelSpeechPitch(pitch: Float): Float = when (pitch) {
    0.8f -> 1f
    1f -> 1.2f
    1.2f -> 1.5f
    else -> 0.8f
}

/** Zero disables the session timer; positive values are minutes. */
internal fun nextSpeechTimerMinutes(minutes: Int): Int = when (minutes) {
    0 -> 5
    5 -> 15
    15 -> 30
    30 -> 60
    else -> 0
}

internal fun speechTimerRemainingMillis(deadline: Long, now: Long): Long =
    if (deadline <= now) 0 else deadline - now

/** Skip to a neighboring non-empty utterance without crossing chapter boundaries. */
internal fun adjacentSpeechSegment(lines: List<String>, current: Int, forward: Boolean): Int? {
    if (lines.isEmpty()) return null
    val start = if (forward) current.coerceIn(-1, lines.lastIndex) + 1 else current.coerceIn(0, lines.size) - 1
    if (forward) {
        for (i in start until lines.size) if (lines[i].isNotBlank()) return i
    } else {
        for (i in start downTo 0) if (lines[i].isNotBlank()) return i
    }
    return null
}
