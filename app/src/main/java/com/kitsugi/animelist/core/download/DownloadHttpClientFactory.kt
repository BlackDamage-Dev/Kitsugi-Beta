package com.kitsugi.animelist.core.download

import com.kitsugi.animelist.data.cloudstream.CsVideoInterceptorFactory
import com.kitsugi.animelist.data.model.AnimeDownload
import okhttp3.OkHttpClient
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Çevrimiçi oynatıcı ([com.kitsugi.animelist.core.player.PlayerMediaSourceFactory]) ile birebir
 * aynı ağ yeteneklerini (`ignoreSSL`, paylaşımlı `CookieJar`, otomatik `Referer`/`Origin`
 * türetimi ve CS3 `getVideoInterceptor`) video indiriciye sağlayan istemci fabrikası.
 */
object DownloadHttpClientFactory {

    private val PUBLIC_SUBTITLE_HOSTS = listOf(
        "opensubtitles.",
        "subdl.",
        "strem.io",
        "wyzie.",
        "jimaku.",
        "animetosho."
    )

    fun buildClient(download: AnimeDownload): Pair<OkHttpClient, Map<String, String>> {
        val rawHeaders = download.requestHeaders
        val addonKey = download.addonName?.takeIf { it.isNotBlank() }
            ?: download.source?.takeIf { it.isNotBlank() }

        val provider = if ((download.isCS || !addonKey.isNullOrBlank()) && !addonKey.isNullOrBlank()) {
            runCatching {
                com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(addonKey)
            }.getOrNull()
        } else {
            null
        }

        val effectiveHeaders = buildEffectiveHeaders(
            rawHeaders = rawHeaders,
            videoUrl = download.url,
            providerMainUrl = provider?.mainUrl
        )

        @Suppress("DEPRECATION")
        val videoInterceptor = if (provider != null && !addonKey.isNullOrBlank()) {
            runCatching {
                val isM3u8 = HlsManifestParser.isLikelyHlsUrl(download.url)
                val qualityNum = download.quality.filter { it.isDigit() }.toIntOrNull() ?: 1080
                val link = com.lagradost.cloudstream3.utils.ExtractorLink(
                    source = addonKey,
                    name = download.streamTitle ?: download.streamName ?: addonKey,
                    url = download.url,
                    referer = effectiveHeaders.entries
                        .firstOrNull { it.key.equals("referer", ignoreCase = true) }
                        ?.value.orEmpty(),
                    quality = qualityNum,
                    headers = effectiveHeaders,
                    extractorData = null,
                    type = if (isM3u8) {
                        com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                    } else {
                        com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                    },
                    audioTracks = emptyList()
                )
                provider.getVideoInterceptor(link)
            }.getOrNull()
        } else {
            null
        }

        val baseClient = CsVideoInterceptorFactory.buildClient(
            interceptor = videoInterceptor,
            headers = effectiveHeaders,
            ignoreSSL = true
        )

        val downloadClient = baseClient.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        return downloadClient to effectiveHeaders
    }

    /**
     * Sağlayıcının verdiği başlıklarda eksik olan `Referer`, `Origin` ve `User-Agent`
     * alanlarını çevrimiçi oynatıcıyla aynı kurallarla tamamlar.
     */
    fun buildEffectiveHeaders(
        rawHeaders: Map<String, String>?,
        videoUrl: String,
        providerMainUrl: String? = null
    ): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        rawHeaders?.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                out[k] = v
            }
        }

        val videoOrigin = originOfUrl(videoUrl)

        if (!out.keys.any { it.equals("referer", ignoreCase = true) }) {
            val fallbackReferer = providerMainUrl?.takeIf { it.isNotBlank() }?.let { "${it.trimEnd('/')}/" }
                ?: videoOrigin?.let { "$it/" }
            if (!fallbackReferer.isNullOrBlank()) {
                out["Referer"] = fallbackReferer
            }
        }

        if (!out.keys.any { it.equals("origin", ignoreCase = true) }) {
            val refererVal = out.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value
            val derivedOrigin = refererVal?.let { originOfUrl(it) }
                ?: videoOrigin
                ?: providerMainUrl?.let { originOfUrl(it) }
            if (!derivedOrigin.isNullOrBlank()) {
                out["Origin"] = derivedOrigin
            }
        }

        if (!out.keys.any { it.equals("user-agent", ignoreCase = true) }) {
            out["User-Agent"] = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT
        }

        return out
    }

    /**
     * Altyazı indirme istekleri için uygun başlıkları belirler:
     * - Eklenti / embed sunucusu altyazılarında (`closeload`, `rapidvid`, `vidmoxy`, `contentx` vb.)
     *   `Referer` + `Origin` + `User-Agent` korunur (aksi hâlde 403 Forbidden döner).
     * - Üçüncü parti genel altyazı servislerinde (`OpenSubtitles`, `SubDL` vb.) yabancı `Origin`
     *   başlığı gönderilmez, yalnızca `User-Agent` korunur.
     */
    fun resolveSubtitleHeaders(
        subtitleUrl: String,
        videoUrl: String,
        effectiveVideoHeaders: Map<String, String>
    ): Map<String, String> {
        val subHost = hostOfUrl(subtitleUrl)?.lowercase(Locale.ROOT)
        val userAgent = effectiveVideoHeaders.entries
            .firstOrNull { it.key.equals("user-agent", ignoreCase = true) }
            ?.value

        if (subHost != null && PUBLIC_SUBTITLE_HOSTS.any { subHost.contains(it) }) {
            return if (!userAgent.isNullOrBlank()) mapOf("User-Agent" to userAgent) else emptyMap()
        }

        val videoHost = hostOfUrl(videoUrl)?.lowercase(Locale.ROOT)
        val refererVal = effectiveVideoHeaders.entries
            .firstOrNull { it.key.equals("referer", ignoreCase = true) }
            ?.value
        val refererHost = refererVal?.let { hostOfUrl(it)?.lowercase(Locale.ROOT) }

        if (subHost != null && (
                subHost == videoHost ||
                    subHost == refererHost ||
                    sharesRegistrableDomain(subHost, videoHost) ||
                    sharesRegistrableDomain(subHost, refererHost)
                )
        ) {
            return effectiveVideoHeaders
        }

        // Eklentiden gelen gömülü VTT/SRT sunucularında (ör. video srv9.cdnimages.cyou iken
        // altyazı cdn.closeload.top üzerinde olduğunda) Referer yine embed adresi olmalıdır.
        return buildMap {
            if (!userAgent.isNullOrBlank()) put("User-Agent", userAgent)
            if (!refererVal.isNullOrBlank()) put("Referer", refererVal)
            refererVal?.let { originOfUrl(it) }?.let { put("Origin", it) }
        }
    }

    fun originOfUrl(url: String): String? {
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return null
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val port = if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            null
        }
    }

    private fun hostOfUrl(url: String): String? =
        runCatching { URI(url.trim()).host }.getOrNull()

    private fun sharesRegistrableDomain(h1: String?, h2: String?): Boolean {
        if (h1.isNullOrBlank() || h2.isNullOrBlank()) return false
        val p1 = h1.split('.').takeLast(2).joinToString(".")
        val p2 = h2.split('.').takeLast(2).joinToString(".")
        return p1.contains('.') && p1 == p2
    }
}
