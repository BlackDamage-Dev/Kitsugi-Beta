package com.kitsugi.animelist.data.local

/** Result of a translation attempt. Partial output is useful to display, but is not a cache hit. */
data class TranslationOutcome(val text: String, val fullyTranslated: Boolean)

/**
 * Old whole-text cache rows may have been produced by earlier token/partial handling.
 * Give complete results a new namespace; keep validated per-chunk cache reuse.
 */
internal fun completedTranslationCacheMaterial(text: String, source: String, target: String): String =
    "translation-complete-v2_${text}_${source}_${target}"

private val protectedTranslationToken = Regex("⟪\\s*([@#/!])\\s*(\\d+)\\s*⟫")

/**
 * Validate only markers that actually occur in this chunk, including closing links and
 * repeated spoilers. Tokens in another chunk must not make this chunk fail validation.
 */
internal fun protectedTranslationTokensIntact(sourceChunk: String, translatedChunk: String): Boolean {
    fun counts(text: String): Map<String, Int> = protectedTranslationToken.findAll(text)
        .map { "${it.groupValues[1]}${it.groupValues[2]}" }
        .groupingBy { it }
        .eachCount()
    return counts(sourceChunk) == counts(translatedChunk)
}

/** Avoid splitting a protected marker when an oversized word has to be cut. */
internal fun safeTranslationChunkCut(text: CharSequence, maxSize: Int): Int {
    if (text.isEmpty()) return 0
    val limit = maxSize.coerceIn(1, text.length)
    if (limit == text.length) return limit
    val open = text.lastIndexOf('⟪', startIndex = limit - 1)
    if (open < 0) return limit
    val close = text.indexOf('⟫', startIndex = open + 1)
    return if (close >= limit && open > 0) open else limit
}
