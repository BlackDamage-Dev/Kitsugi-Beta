package com.kitsugi.animelist.data.cloudstream.extractors

import java.net.URI
import java.util.Locale

/**
 * SendVid fallback used when the CloudStream extractor only understands the HLS
 * variant of the host. SendVid commonly exposes a page-looking `/<id>.mp4`
 * URL; sending that URL directly to Media3 makes it receive the provider's HTML
 * page instead of the actual video.
 *
 * This wrapper only follows the provider's normal embed page and extracts a URL
 * that the page itself publishes. It does not bypass access controls or attempt
 * to revive a provider-declared unavailable video.
 */
object SendVidWrapper {

    data class ExtractedVideo(
        val url: String,
        val label: String,
        val isM3u8: Boolean,
        val headers: Map<String, String> = emptyMap()
    )

    private val supportedHosts = setOf(
        "sendvid.com",
        "www.sendvid.com",
        "sendvid.net",
        "www.sendvid.net"
    )

    private val mediaUrlRegex = Regex(
        """https?://[^\s"'<>]+(?:\.m3u8|\.mp4)[^\s"'<>]*""",
        RegexOption.IGNORE_CASE
    )

    private val attributeRegex = Regex(
        """([A-Za-z_:][A-Za-z0-9_:-]*)\s*=\s*([\"'])(.*?)\2""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Kitsugi/2.4"

    /** True only for SendVid's own host, not for look-alike domains. */
    fun isSendVidUrl(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host?.lowercase(Locale.ROOT) }.getOrNull()
            ?: return false
        return host in supportedHosts ||
            host.endsWith(".sendvid.com") ||
            host.endsWith(".sendvid.net")
    }

    /**
     * Converts a provider URL or the misleading `/<id>.mp4` URL to the normal
     * embed page. Existing `/embed/<id>` URLs are kept intact because they may
     * carry a provider token in the query string.
     */
    fun pageUrl(url: String): String {
        if (!isSendVidUrl(url)) return url
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url
        val path = uri.path.orEmpty().trimEnd('/')
        if (path.substringBefore('?').contains("/embed/", ignoreCase = true)) return url

        val id = path.substringAfterLast('/')
            .substringBefore('?')
            .removeSuffix(".mp4")
            .removeSuffix(".m3u8")
            .removeSuffix(".html")
            .trim()
        if (id.isBlank() || id == path) return url

        // SendVid ids are URL-safe. Do not turn an arbitrary path into a new
        // request if it contains control characters or another slash.
        if (!id.matches(Regex("[A-Za-z0-9_-]{2,160}"))) return url
        return "https://sendvid.com/embed/$id"
    }

    /** Provider page text explicitly says that this item is unavailable. */
    fun isTemporarilyUnavailable(pageContent: String?): Boolean {
        val lower = pageContent.orEmpty().lowercase(Locale.ROOT)
        return lower.contains("this video is temporarily unavailable") ||
            lower.contains("video is temporarily unavailable") ||
            lower.contains("this video has been removed") ||
            lower.contains("video not found") ||
            lower.contains("video does not exist")
    }

    /**
     * Extracts the media URL published in SendVid's normal embed page.
     * Candidates are deliberately limited to the page's video metadata/source
     * fields; arbitrary scripts are not executed.
     */
    fun extractFromHtml(pageContent: String, embedUrl: String): List<ExtractedVideo> {
        if (pageContent.isBlank() || isTemporarilyUnavailable(pageContent)) return emptyList()

        val pageUrl = pageUrl(embedUrl)
        val candidates = LinkedHashSet<String>()

        // The current player uses video_source; older pages expose OpenGraph
        // metadata or a regular HTML5 <source> element.
        Regex(
            """\bvideo_source\s*=\s*[\"']([^\"']+)[\"']""",
            RegexOption.IGNORE_CASE
        ).findAll(pageContent).forEach { addCandidate(candidates, it.groupValues[1], pageUrl) }

        Regex("""<meta\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(pageContent)
            .forEach { match ->
                val attrs = attributes(match.value)
                val property = (attrs["property"] ?: attrs["name"]).orEmpty().lowercase(Locale.ROOT)
                if (property == "og:video" || property == "og:video:secure_url") {
                    addCandidate(candidates, attrs["content"], pageUrl)
                }
            }

        Regex("""<(?:video|source)\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(pageContent)
            .forEach { match ->
                val attrs = attributes(match.value)
                addCandidate(candidates, attrs["src"] ?: attrs["data-src"], pageUrl)
            }

        // Some archived SendVid pages only leave the absolute MP4 in a script.
        mediaUrlRegex.findAll(unescape(pageContent)).forEach { addCandidate(candidates, it.value, pageUrl) }

        val headers = mapOf(
            "Referer" to pageUrl,
            "Origin" to (originOf(pageUrl) ?: "https://sendvid.com"),
            "User-Agent" to USER_AGENT
        )
        return candidates.take(4).map { media ->
            val lowerMedia = media.lowercase(Locale.ROOT)
            val isM3u8 = lowerMedia.contains(".m3u8")
            ExtractedVideo(
                url = media,
                label = if (isM3u8) "HLS" else "MP4",
                isM3u8 = isM3u8,
                headers = headers
            )
        }
    }

    private fun addCandidate(out: MutableSet<String>, raw: String?, baseUrl: String) {
        val value = raw?.let(::unescape)?.trim()?.trim('"', '\'') ?: return
        if (value.isBlank() || value.startsWith("data:", true) || value.startsWith("blob:", true)) return
        val absolute = runCatching {
            val normalized = when {
                value.startsWith("//") -> "https:$value"
                value.startsWith("http://", true) || value.startsWith("https://", true) -> value
                else -> URI(baseUrl).resolve(value).toString()
            }
            normalized
        }.getOrNull() ?: return
        val lowerAbsolute = absolute.lowercase(Locale.ROOT)
        val lowerPath = runCatching { URI(absolute).path.orEmpty().lowercase(Locale.ROOT) }.getOrDefault(lowerAbsolute)
        // A few archived routes put the media extension in a query parameter
        // (`/get?file=episode.mp4`) rather than in the path.
        if (!lowerAbsolute.contains(".mp4") && !lowerAbsolute.contains(".m3u8")) return
        if (lowerPath.contains("thumb") || lowerPath.contains("poster") || lowerPath.contains("preview")) return
        out.add(absolute)
    }

    private fun attributes(tag: String): Map<String, String> =
        attributeRegex.findAll(tag).associate { match ->
            match.groupValues[1].lowercase(Locale.ROOT) to unescape(match.groupValues[3])
        }

    private fun unescape(value: String): String = value
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u003F", "?", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#x2f;", "/", ignoreCase = true)
        .replace("&#47;", "/", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)

    private fun originOf(url: String): String? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val port = if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
        "$scheme://$host$port"
    }.getOrNull()
}
