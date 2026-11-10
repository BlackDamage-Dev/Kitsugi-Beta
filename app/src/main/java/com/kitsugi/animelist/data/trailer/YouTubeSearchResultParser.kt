package com.kitsugi.animelist.data.trailer

private val VIDEO_RENDERER_OBJECT_REGEX = Regex("\"videoRenderer\"\\s*:\\s*\\{")
private val SEARCH_VIDEO_ID_FIELD_REGEX =
    Regex("\"videoId\"\\s*:\\s*\"([A-Za-z0-9_-]{11})\"")

/**
 * Extracts the first real video from a YouTube search page's `ytInitialData` object.
 * Channel and playlist renderers are ignored. No Android or JSON-library dependency is
 * needed here so the potentially fragile HTML parsing can be tested as plain Kotlin.
 */
internal fun extractFirstYouTubeSearchVideoId(html: String): String? {
    val initialData = extractYouTubeInitialData(html) ?: return null
    var searchFrom = 0
    while (searchFrom < initialData.length) {
        val rendererMatch = VIDEO_RENDERER_OBJECT_REGEX.find(initialData, searchFrom) ?: return null
        val objectStart = rendererMatch.range.last
        val renderer = readBalancedJsonObject(initialData, objectStart)
        if (renderer != null) {
            val videoId = SEARCH_VIDEO_ID_FIELD_REGEX.find(renderer)?.groupValues?.get(1)
            if (videoId != null) return videoId
        }
        searchFrom = objectStart + 1
    }
    return null
}

private fun extractYouTubeInitialData(html: String): String? {
    val marker = "ytInitialData"
    var searchFrom = 0
    while (searchFrom < html.length) {
        val markerIndex = html.indexOf(marker, searchFrom)
        if (markerIndex < 0) return null
        val objectStart = html.indexOf('{', markerIndex + marker.length)
        if (objectStart < 0 || objectStart - markerIndex > 128) {
            searchFrom = markerIndex + marker.length
            continue
        }
        val assignment = html.substring(markerIndex + marker.length, objectStart)
        if (!assignment.contains('=') && !assignment.contains(':')) {
            searchFrom = markerIndex + marker.length
            continue
        }
        return readBalancedJsonObject(html, objectStart)
    }
    return null
}

/** Finds the end of a JavaScript JSON object without being fooled by braces in strings. */
private fun readBalancedJsonObject(source: String, start: Int): String? {
    if (start !in source.indices || source[start] != '{') return null
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until source.length) {
        val char = source[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(start, index + 1)
            }
        }
    }
    return null
}
