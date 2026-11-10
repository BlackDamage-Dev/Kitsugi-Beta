package com.kitsugi.animelist.data.manga

/** Engine-independent deterministic segmentation; prefer natural sentence/word boundaries. */
object MangaSpeechSegmenter {
    const val MAX_UTTERANCE_CHARS = 3000

    data class SpeechSegment(
        val paragraphIndex: Int,
        val sentenceIndex: Int,
        val text: String,
    )

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

    /**
     * Splits a paragraph into natural sentences.
     * Respects sentence terminators (. ! ? … \n) and handles inline image tags.
     */
    fun splitIntoSentences(paragraph: String): List<String> {
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("[img:") && trimmed.endsWith("]")) {
            return emptyList()
        }

        val sentences = mutableListOf<String>()
        val regex = Regex("""(?<=[.!?…])\s+(?=[^\s])""")
        val parts = trimmed.split(regex)
        for (part in parts) {
            val clean = part.trim()
            if (clean.isNotEmpty()) {
                if (clean.length > MAX_UTTERANCE_CHARS) {
                    sentences.addAll(segment(clean))
                } else {
                    sentences.add(clean)
                }
            }
        }
        return if (sentences.isEmpty()) listOf(trimmed) else sentences
    }

    /**
     * Segments multiple paragraphs into sentence-level speech segments with
     * paragraph and sentence indexing for highlight tracking.
     */
    fun segmentParagraphs(paragraphs: List<String>): List<SpeechSegment> {
        val result = mutableListOf<SpeechSegment>()
        paragraphs.forEachIndexed { pIdx, paragraph ->
            val sentences = splitIntoSentences(paragraph)
            sentences.forEachIndexed { sIdx, sentence ->
                val clean = sentence.trim()
                if (clean.isNotEmpty()) {
                    result.add(SpeechSegment(pIdx, sIdx, clean))
                }
            }
        }
        return result
    }
}
