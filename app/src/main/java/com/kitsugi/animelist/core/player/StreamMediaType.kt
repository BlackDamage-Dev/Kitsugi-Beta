package com.kitsugi.animelist.core.player

import java.net.URI
import java.util.Locale

/** Media type supplied by an extractor or established from the HTTP response. */
enum class StreamMediaType {
    HLS,
    DASH,
    PROGRESSIVE
}

/**
 * Keep an explicit extractor/response hint through to the player. A VIDEO hint alone is
 * not proof that a URL is a video (CloudStream also infers VIDEO for embed pages).
 * Explicit manifest names still beat an inferred VIDEO hint; otherwise a known
 * progressive stream must not be mistaken for HLS merely because its path says /stream/.
 */
object StreamFormatPolicy {
    fun resolve(url: String, hint: StreamMediaType? = null): StreamMediaType? {
        if (url.isBlank()) return hint
        if (hint == StreamMediaType.HLS || hint == StreamMediaType.DASH) return hint

        val clean = url.lowercase(Locale.ROOT)
        val path = try {
            URI(clean).path.orEmpty()
        } catch (_: Exception) {
            clean.substringBefore('?')
        }

        if ((clean.contains("manifest.googlevideo.com") &&
                (clean.contains("hls_playlist") || clean.contains("hls_variant"))) ||
            clean.contains("m3u8") || clean.contains("master.txt") ||
            clean.contains("playlist.txt") || clean.contains("video.txt") ||
            clean.contains("tracks.txt") || clean.contains("vidmixi.com/list") ||
            clean.contains("vidmixi.com/m3u")
        ) return StreamMediaType.HLS
        if (path.endsWith(".mpd") || path.contains(".mpd/")) return StreamMediaType.DASH

        if (hint == StreamMediaType.PROGRESSIVE || PROGRESSIVE_PATH_REGEX.containsMatchIn(path)) {
            return StreamMediaType.PROGRESSIVE
        }
        if (clean.contains("/hls/") || clean.contains("/hls") ||
            clean.contains("/m3u8/") || clean.contains("/playlist/") ||
            clean.contains("/hls-vod/")
        ) return StreamMediaType.HLS

        if (HLS_HOST_OR_PATH_MARKERS.any { clean.contains(it) }) return StreamMediaType.HLS
        if (clean.contains("/stream/") || clean.contains("/player/") || clean.contains("/embed/")) {
            return StreamMediaType.HLS
        }
        return null
    }

    private val PROGRESSIVE_PATH_REGEX = Regex("""\.(mp4|mkv|webm|avi|m4v)$""")
    private val HLS_HOST_OR_PATH_MARKERS = listOf(
        "vidmixi", "dosyaload", "playmix.uno", "playmix", "closeload", "rapidrame",
        "rplayer", "hdfilmcehennemi", "filmmakinesi", "filmakinesi", "index-v", "index-a",
        "imgsapi", "molystream", "macellan", "vmeas", "vidpapi", "hdplayersystem",
        "imagestoo", "pichive", "dizilla"
    )
}
