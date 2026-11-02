package com.kitsugi.animelist.data.cloudstream.extractors

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.URI
import java.util.Locale

/**
 * FP-34 – Voe (voe.sx) extractor wrapper.
 * Stream URL'sini JS değişkenlerinde (hls/mp4) veya Base64 ile saklar.
 */
object VoeWrapper {
    private const val TAG = "VoeWrapper"

    // The bundled CloudStream Voe extractor exposes these rotating aliases as
    // subclasses of the same F7 resolver. Keep the application fallback in
    // sync with that list, but match hosts exactly (no lookalike domains).
    private val voeHosts = setOf(
        "voe.sx",
        "voeload.com",
        "tubelessceliolymph.com",
        "simpulumlamerop.com",
        "urochsunloath.com",
        "nathanfromsubject.com",
        "yip.su",
        "metagnathtuggers.com",
        "donaldlineelse.com",
        "charlestoughrace.com",
        // Additional Voe subclasses bundled by community plugins.
        "crystaltreatmenteast.com",
        "ralphysuccessfull.org",
        "bryantenunder.com",
        "pamelachangemission.com"
    )

    data class ExtractedVideo(
        val url: String,
        val label: String,
        val isM3u8: Boolean,
        val headers: Map<String, String> = emptyMap()
    )

    fun extractFromHtml(pageContent: String, embedUrl: String): List<ExtractedVideo> {
        val videos = mutableListOf<ExtractedVideo>()
        try {
            // Voe's CDN may bind the signed URL to the exact embed page, not
            // merely the host root. Preserve that page as Referer and provide
            // the matching origin for the player and HLS segment requests.
            val pageReferer = embedUrl.takeIf { it.startsWith("http", ignoreCase = true) }
                ?: "https://voe.sx/"
            val pageOrigin = runCatching {
                val uri = java.net.URI(pageReferer)
                val scheme = uri.scheme ?: "https"
                val host = uri.host ?: "voe.sx"
                "$scheme://$host"
            }.getOrDefault("https://voe.sx")
            val headers = mapOf(
                "Referer" to pageReferer,
                "Origin" to pageOrigin
            )

            // Yöntem 1: 'hls': '...' veya 'mp4': '...'
            Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(pageContent)?.groupValues?.get(1)?.let { url ->
                val decoded = tryBase64Decode(url) ?: url
                if (decoded.startsWith("http") && decoded.contains(".m3u8")) {
                    videos.add(ExtractedVideo(decoded, "HLS", true, headers))
                }
            }
            Regex("""['"]mp4['"]\s*:\s*['"]([^'"]+)['"]""").find(pageContent)?.groupValues?.get(1)?.let { url ->
                val decoded = tryBase64Decode(url) ?: url
                if (decoded.startsWith("http") && decoded.contains(".mp4")) {
                    videos.add(ExtractedVideo(decoded, "MP4", false, headers))
                }
            }

            // Yöntem 2: atob('...') decode
            if (videos.isEmpty()) {
                Regex("""atob\(['"]([A-Za-z0-9+/=]+)['"]\)""").findAll(pageContent).forEach { m ->
                    val decoded = tryBase64Decode(m.groupValues[1])
                    if (!decoded.isNullOrBlank() && decoded.startsWith("http")) {
                        val isM3u8 = decoded.contains(".m3u8")
                        videos.add(ExtractedVideo(decoded, if (isM3u8) "HLS" else "MP4", isM3u8, headers))
                    }
                }
            }

            // Yöntem 3: Voe'nin güncel F7 paketlemesi. CloudStream'in yerel
            // Voe extractor'ı bunu çözer; wrapper da aynı resmi sayfa verisini
            // çözebilmeli ki extractor ABI'si değiştiğinde kaynak kaybolmasın.
            // Bu, sayfanın yayınladığı application/json değerini açar; erişim
            // kontrolünü atlamaz ve yalnızca source/direct_access_url alanlarını
            // kabul eder.
            if (videos.isEmpty()) {
                val f7Json = extractF7Json(pageContent)
                if (!f7Json.isNullOrBlank()) {
                    val json = runCatching { JSONObject(f7Json) }.getOrNull()
                    val hls = json?.optString("source")
                        ?.takeIf { it.startsWith("http", ignoreCase = true) }
                    val mp4 = json?.optString("direct_access_url")
                        ?.takeIf { it.startsWith("http", ignoreCase = true) }
                    if (hls != null) {
                        videos.add(ExtractedVideo(hls, "HLS", true, headers))
                    }
                    if (mp4 != null) {
                        videos.add(ExtractedVideo(mp4, "MP4", false, headers))
                    }
                }
            }

            Log.d(TAG, "Voe extraction: ${videos.size} video")
        } catch (e: Exception) {
            Log.e(TAG, "Voe extraction hatası: ${e.message}")
        }
        return videos
    }

    /** Extracts and decodes Voe's application/json F7 payload. */
    private fun extractF7Json(pageContent: String): String? {
        val script = Regex(
            """<script\b[^>]*type\s*=\s*["']application/json["'][^>]*>([\s\S]*?)</script>""",
            RegexOption.IGNORE_CASE
        ).find(pageContent)?.groupValues?.getOrNull(1)?.trim()
            ?.replace("&quot;", "\"")
            ?.replace("&#39;", "'")
            ?: return null

        val encoded = runCatching {
            if (script.startsWith("[")) {
                org.json.JSONArray(script).optString(0).takeIf { it.isNotBlank() }
            } else {
                script.trim().trim('"', '\'')
            }
        }.getOrNull() ?: script.trim().trim('"', '\'')
        if (encoded.isBlank()) return null

        return runCatching {
            var value = rot13(encoded)
            listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&").forEach { pattern ->
                value = value.replace(pattern, "_")
            }
            value = value.replace("_", "")
            val first = String(base64DecodeBytes(value), Charsets.UTF_8)
            val shifted = first.map { (it.code - 3).toChar() }.joinToString("")
            val second = String(base64DecodeBytes(shifted.reversed()), Charsets.UTF_8)
            second.takeIf { it.trimStart().startsWith("{") }
        }.getOrNull()
    }

    private fun base64DecodeBytes(input: String): ByteArray {
        val clean = input.trim()
        return runCatching { java.util.Base64.getDecoder().decode(clean) }.getOrNull()
            ?: runCatching { java.util.Base64.getMimeDecoder().decode(clean) }.getOrNull()
            ?: runCatching { Base64.decode(clean, Base64.DEFAULT) }.getOrNull()
            ?: ByteArray(0)
    }

    private fun rot13(input: String): String = input.map { c ->
        when (c) {
            in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
            in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
            else -> c
        }
    }.joinToString("")

    private fun tryBase64Decode(s: String): String? = try {
        val d = String(base64DecodeBytes(s)).trim()
        if (d.startsWith("http")) d else null
    } catch (_: Exception) { null }

    fun isVoeUrl(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host?.lowercase(Locale.ROOT) }.getOrNull()
            ?: return false
        return voeHosts.any { host == it || host.endsWith(".$it") }
    }
}
