package com.kitsugi.animelist.data.trailer

import java.net.URI
import java.util.Locale

/**
 * Request policy shared by the detail-page inline player, Media3 and libmpv.
 *
 * The inline player used to rely on Media3's generic Cloudstream headers while libmpv
 * received no headers at all. That is not equivalent for signed YouTube/googlevideo
 * streams: they need a YouTube referer and a browser-like user agent. Direct theme files
 * also need the same origin/referer parity when they are played by libmpv.
 */
internal object YouTubeRequestPolicy {
    const val STREAM_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
    const val YOUTUBE_REFERER = "https://www.youtube.com/"
    private const val YOUTUBE_ORIGIN = "https://www.youtube.com"
    private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"

    fun headersForInlineUrl(
        url: String,
        overrides: Map<String, String> = emptyMap()
    ): Map<String, String> {
        if (isYouTubeMediaUrl(url)) return youtubePlaybackHeaders(overrides)

        val headers = linkedMapOf(
            "User-Agent" to STREAM_USER_AGENT,
            "Accept-Language" to ACCEPT_LANGUAGE,
            // Bazı CDN'ler (animethemes CDN'i dâhil) tarayıcı dışı istemcilere Accept
            // başlığı olmadan 403 dönebiliyor.
            "Accept" to "video/*,application/octet-stream,*/*;q=0.8"
        )
        val origin = originOf(url)
        if (origin != null) {
            // `Origin` BİLİNÇLİ olarak gönderilmiyor: yabancı bir Origin başlığı bazı
            // CDN'lerde CORS kuralına takılıp 403 üretiyor ve küçük oynatıcı sessizce
            // "oynatılamadı" kartına düşüyordu. Referer yeterli ve daha güvenli.
            headers["Referer"] = "$origin/"
        }
        return mergeHeaders(headers, overrides)
    }

    fun headersForPlaybackSource(source: TrailerPlaybackSource): Map<String, String> =
        headersForInlineUrl(source.videoUrl, source.requestHeaders)

    fun youtubePlaybackHeaders(overrides: Map<String, String> = emptyMap()): Map<String, String> {
        val merged = linkedMapOf(
            "User-Agent" to STREAM_USER_AGENT,
            "Accept-Language" to ACCEPT_LANGUAGE,
            "Referer" to YOUTUBE_REFERER,
            "Origin" to YOUTUBE_ORIGIN
        )
        return mergeHeaders(merged, overrides)
    }

    private fun mergeHeaders(
        base: Map<String, String>,
        overrides: Map<String, String>
    ): Map<String, String> {
        val merged = LinkedHashMap(base)
        overrides.forEach { (name, value) ->
            if (name.isBlank() || value.isBlank()) return@forEach
            val existingName = merged.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            if (existingName != null) {
                merged[existingName] = value
            } else {
                merged[name] = value
            }
        }
        return merged
    }

    /** Reads YouTube's signed expiry from either a query parameter or an HLS path segment. */
    fun expiryEpochSeconds(url: String): Long? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val queryExpiry = uri.rawQuery.orEmpty()
            .split('&')
            .firstOrNull { it.substringBefore('=') == "expire" }
            ?.substringAfter('=', missingDelimiterValue = "")
            ?.toLongOrNull()
        if (queryExpiry != null) return queryExpiry

        val segments = uri.path.orEmpty().split('/')
        val expireIndex = segments.indexOf("expire")
        if (expireIndex < 0) return null
        return segments.getOrNull(expireIndex + 1)?.toLongOrNull()
    }

    fun isYouTubeMediaUrl(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return isGoogleVideoHost(host) ||
            host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com") ||
            host == "youtu.be" || host.endsWith(".youtu.be")
    }

    fun isGoogleVideoUrl(url: String?): Boolean = hostOf(url)?.let(::isGoogleVideoHost) == true

    /**
     * The query-parameter range workaround is only for progressive googlevideo files.
     * Applying it to an HLS manifest/segment changes the signed playlist request and can
     * make an otherwise valid manifest fail before Media3 sees any duration.
     */
    fun shouldUseChunkedGoogleVideoRanges(
        videoUrl: String,
        audioUrl: String?,
        isCloudstream: Boolean,
        isHls: Boolean
    ): Boolean = !isCloudstream && !isHls &&
        (isGoogleVideoUrl(videoUrl) || isGoogleVideoUrl(audioUrl))

    fun shouldUseYouTubeDataSource(
        videoUrl: String,
        audioUrl: String?,
        isCloudstream: Boolean
    ): Boolean = !isCloudstream &&
        (isYouTubeMediaUrl(videoUrl) || isYouTubeMediaUrl(audioUrl))

    fun isGoogleVideoHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT).trimEnd('.')
        return normalized == "googlevideo.com" || normalized.endsWith(".googlevideo.com")
    }

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url.trim()).host?.lowercase(Locale.ROOT)?.trimEnd('.') }
            .getOrNull()
    }

    private fun originOf(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val scheme = uri.scheme?.takeIf { it.equals("http", true) || it.equals("https", true) }
            ?.lowercase(Locale.ROOT) ?: "https"
        val defaultPort = if (scheme == "http") 80 else 443
        val port = uri.port.takeIf { it > 0 && it != defaultPort }?.let { ":$it" }.orEmpty()
        return "$scheme://${host.lowercase(Locale.ROOT)}$port"
    }
}
