package com.kitsugi.animelist.data.cloudstream

import java.net.URI
import java.util.Locale

/** Decisions from a bounded GET response. UNKNOWN is deliberately not treated as dead. */
internal enum class CsMediaResponseKind { HLS, DASH, VIDEO, HTML, DEAD, UNKNOWN }

internal object CsMediaResponsePolicy {
    fun classify(status: Int, contentType: String?, sample: ByteArray): CsMediaResponseKind {
        if (status == 404 || status == 410) return CsMediaResponseKind.DEAD
        if (status !in 200..299) return CsMediaResponseKind.UNKNOWN

        val text = sample.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val lower = text.lowercase(Locale.ROOT)
        if (text.startsWith("#EXTM3U", ignoreCase = true)) return CsMediaResponseKind.HLS
        if ((lower.startsWith("<?xml") || lower.startsWith("<mpd")) && lower.contains("<mpd")) {
            return CsMediaResponseKind.DASH
        }
        if (lower.startsWith("<") && listOf("<!doctype html", "<html", "<head", "<body", "<form", "<script", "<div", "<meta").any(lower::contains)) {
            return CsMediaResponseKind.HTML
        }

        val mime = contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        return when {
            mime.contains("mpegurl") -> CsMediaResponseKind.HLS
            mime.contains("dash+xml") -> CsMediaResponseKind.DASH
            mime == "text/html" || mime == "application/xhtml+xml" -> CsMediaResponseKind.HTML
            mime.startsWith("video/") || mime.startsWith("audio/") ||
                mime == "application/octet-stream" -> CsMediaResponseKind.VIDEO
            else -> CsMediaResponseKind.UNKNOWN
        }
    }

    /** An explicit player/document path still needs extraction even if a plugin marked it HLS. */
    fun isObviousEmbedPage(url: String): Boolean = try {
        val path = URI(url).path.orEmpty().lowercase(Locale.ROOT).trimEnd('/')
        path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".php") ||
            Regex("""/(?:embed|player|e|v)/[^/]+$""").containsMatchIn(path)
    } catch (_: Exception) {
        true
    }

    /** These are download endpoints, not Drive preview/embed pages. Never discard their query. */
    fun isDriveDownloadUrl(url: String): Boolean = try {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val path = uri.path.orEmpty().lowercase(Locale.ROOT).trimEnd('/')
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            ((host == "drive.usercontent.google.com" && path == "/download") ||
                ((host == "drive.google.com" || host == "docs.google.com") && path == "/uc"))
    } catch (_: Exception) {
        false
    }
}
