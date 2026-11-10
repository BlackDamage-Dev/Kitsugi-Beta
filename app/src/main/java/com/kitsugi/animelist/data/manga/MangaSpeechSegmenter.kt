package com.kitsugi.animelist.data.manga

/** Engine-independent deterministic segmentation; prefer natural sentence/word boundaries. */
object MangaSpeechSegmenter {
    const val MAX_UTTERANCE_CHARS = 3000

    fun segment(text: String): List<String> {
        val result = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.length > MAX_UTTERANCE_CHARS) {
            val split = remaining.substring(0, MAX_UTTERANCE_CHARS)
                .lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
                .takeIf { it > MAX_UTTERANCE_CHARS / 3 }
                ?: remaining.lastIndexOf(' ', MAX_UTTERANCE_CHARS).takeIf { it > MAX_UTTERANCE_CHARS / 3 }
                ?: (MAX_UTTERANCE_CHARS - 1)
            result += remaining.substring(0, split + 1).trim()
            remaining = remaining.substring(split + 1).trim()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }
}
