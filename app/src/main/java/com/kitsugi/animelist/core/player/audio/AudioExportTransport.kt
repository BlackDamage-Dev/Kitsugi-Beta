package com.kitsugi.animelist.core.player.audio

import java.net.URI
import java.util.Locale

/** Prefer the plugin's actual ExtractorLink type to guessing from a CDN path. */
internal object AudioExportTransport {
    private val mediaExtensions = setOf(
        ".mp4", ".m4a", ".m4v", ".mkv", ".webm", ".mp3", ".flac", ".wav",
        ".aac", ".ac3", ".eac3", ".ogg", ".oga", ".opus", ".ts", ".m2ts",
        ".mov", ".flv", ".3gp", ".3g2", ".amr", ".aiff", ".mpg", ".mpeg"
    )

    fun known(url: String, declared: String?): String? {
        if (declared == "hls" || declared == "dash") return declared
        val path = runCatching { URI(url).path.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return when {
            path.endsWith(".m3u8") -> "hls"
            path.endsWith(".mpd") -> "dash"
            path.endsWith(".flv") -> "flv"
            else -> null
        }
    }

    /** Native MediaExtractor's redirect stack must not forward plugin credentials cross-host. */
    fun requiresSafeCopy(headers: Map<String, String>): Boolean = headers.keys.any { key ->
        key.lowercase(Locale.ROOT) !in setOf("user-agent", "accept", "accept-language", "referer", "origin")
    }

    /** Inspect ambiguous URLs in ONE response, even if CloudStream's default type was VIDEO. */
    fun needsSniff(url: String, declared: String?): Boolean {
        if (known(url, declared) != null ||
            !url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            return false
        }
        val path = runCatching { URI(url).path.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return mediaExtensions.none(path::endsWith)
    }
}
