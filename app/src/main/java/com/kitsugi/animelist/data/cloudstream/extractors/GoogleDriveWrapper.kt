package com.kitsugi.animelist.data.cloudstream.extractors

import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject

/**
 * Google Drive playback helpers used only as an application-side fallback.
 *
 * Anizm and TrAnimeIzle already have their own Drive resolver. This small
 * parser keeps the application from sending a Drive HTML warning/preview page
 * to Media3 when an older plugin or a different source emits the Drive URL
 * directly. It does not bypass permissions, quotas, or confirmation pages.
 */
object GoogleDriveWrapper {

    data class Playback(val url: String, val height: Int = 0)

    private val driveHosts = setOf(
        "drive.google.com",
        "docs.google.com",
        "drive.usercontent.google.com"
    )

    private val apiKeyRegex = Regex("""AIza[0-9A-Za-z_-]{35}""")
    private val readyDownloadRegex = Regex(
        """https://drive\.usercontent\.google\.com/download\?[^\"'<>\s]+""",
        RegexOption.IGNORE_CASE
    )
    private val inputRegex = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attributeRegex = Regex(
        """([A-Za-z_:][A-Za-z0-9_:-]*)\s*=\s*([\"'])(.*?)\2""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun isDriveUrl(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host?.lowercase(Locale.ROOT) }.getOrNull()
            ?: return false
        return host in driveHosts
    }

    /** A URL that asks Drive for bytes rather than rendering a preview page. */
    fun isDriveDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val path = uri.path.orEmpty()
        return (host == "drive.usercontent.google.com" && path == "/download") ||
            ((host == "drive.google.com" || host == "docs.google.com") && path == "/uc")
    }

    fun fileId(url: String): String? {
        if (!isDriveUrl(url)) return null
        val normalized = url.replace("\\/", "/")
        return Regex("""/d/([A-Za-z0-9_-]{6,})""").find(normalized)?.groupValues?.getOrNull(1)
            ?: Regex("""[?&]id=([A-Za-z0-9_-]{6,})""").find(normalized)?.groupValues?.getOrNull(1)
    }

    fun playbackApiKeys(previewHtml: String): List<String> =
        apiKeyRegex.findAll(previewHtml).map { it.value }.distinct().take(4).toList()

    fun playbackStreams(body: String): List<Playback> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val transcodes = root.optJSONObject("mediaStreamingData")
            ?.optJSONObject("formatStreamingData")
            ?.optJSONArray("progressiveTranscodes") ?: return emptyList()
        val out = ArrayList<Playback>()
        for (index in 0 until transcodes.length()) {
            val item = transcodes.optJSONObject(index) ?: continue
            val url = item.optString("url").takeIf { it.startsWith("http", ignoreCase = true) }
                ?: continue
            val height = item.optJSONObject("transcodeMetadata")?.optInt("height", 0) ?: 0
            out += Playback(url, height)
        }
        return out.distinctBy { it.url }.sortedByDescending { it.height }
    }

    /**
     * Parses Drive's large-file warning page. Returning a URL only when the
     * page contains an actual form/link prevents the common `confirm=t` HTML
     * false positive.
     */
    fun downloadUrl(body: String): String? {
        if (body.isBlank()) return null
        val html = unescape(body)
        readyDownloadRegex.find(html)?.value?.let { return it }

        val action = Regex(
            """<form\b[^>]*action\s*=\s*([\"'])(.*?)\1""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(2)?.trim()?.takeIf {
            it.startsWith("https://drive.usercontent.google.com/download", ignoreCase = true)
        } ?: return null

        val fields = inputRegex.findAll(html).mapNotNull { tag ->
            val attrs = attributeRegex.findAll(tag.value).associate {
                it.groupValues[1].lowercase(Locale.ROOT) to unescape(it.groupValues[3])
            }
            val name = attrs["name"]?.takeIf { it.isNotBlank() }
            val value = attrs["value"] ?: ""
            name?.let { it to value }
        }.toList()
        if (fields.isEmpty()) return null
        val query = fields.joinToString("&") {
            "${URLEncoder.encode(it.first, "UTF-8")}=${URLEncoder.encode(it.second, "UTF-8")}"
        }
        return action + (if (action.contains('?')) "&" else "?") + query
    }

    fun mediaHeaders(): Map<String, String> = mapOf(
        "Referer" to "https://drive.google.com/",
        "Origin" to "https://drive.google.com"
    )

    private fun unescape(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("\\/", "/")
}
