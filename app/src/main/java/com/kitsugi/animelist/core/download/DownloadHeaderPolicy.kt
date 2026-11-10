package com.kitsugi.animelist.core.download

import android.net.Uri
import java.util.Locale

/**
 * İndirme isteklerinde kullanılacak HTTP başlıklarını **tamamlar**.
 *
 * ## Neden gerekli?
 *
 * Oynatıcı (ExoPlayer yolu) başlıkları `PlayerMediaSourceFactory` içinde
 * Referer/Origin/User-Agent olarak zenginleştiriyor. Ama indirme yolu
 * ham `StreamSource.requestHeaders` haritasını kullanıyordu; CS3 eklentileri
 * çoğu zaman yalnızca `Referer` veriyor, `Origin` **hiç** vermiyor. Close/Rapid
 * CDN ailesi ise `Origin`'i embed host'una göre doğruluyor:
 *
 *  - Referer: `https://www.hdfilmcehennemi.nl/...`
 *  - CDN beklediği Origin: `https://www.hdfilmcehennemi.nl`
 *
 * Origin eksik/yanlış olduğunda sunucu 403 **veya** WAF tarafında 429 dönebiliyor.
 * Ayrıca bu CDN'ler aynı IP'den gelen "tarayıcı olmayan" istekleri (UA yok,
 * Accept yok) daha erken sınırlıyor.
 *
 * Bu yardımcı, **var olan başlıkları ezmeden** yalnızca eksikleri tamamlar.
 */
object DownloadHeaderPolicy {

    /** Bilinen sağlayıcı ana adresleri (referer boşsa son çare). */
    private val PROVIDER_HOME: List<Pair<String, String>> = listOf(
        "hdfilmcehennemi" to "https://www.hdfilmcehennemi.nl",
        "filmmakinesi" to "https://filmmakinesi.to",
        "filmakinesi" to "https://filmmakinesi.to",
        "closeload" to "https://closeload.filmmakinesi.to",
        "rapidrame" to "https://rapidrame.com",
        "rplayer" to "https://rplayer.org",
        "playmix" to "https://playmix.uno",
        "dizilla" to "https://dizilla.now",
        "dizibox" to "https://www.dizibox.live",
        "sezonlukdizi" to "https://sezonlukdizi.cc",
        "asyawatch" to "https://asyawatch.com"
    )

    /**
     * @param streamUrl  indirilecek akış adresi (playlist veya dosya)
     * @param headers    kaynağın bildirdiği başlıklar (`StreamSource.requestHeaders`)
     * @param userAgent  varsayılan tarayıcı UA'sı
     */
    fun complete(
        streamUrl: String,
        headers: Map<String, String>,
        userAgent: String
    ): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        // Anahtar harflendirmesi farklı olabildiği için normalize ederek taşı.
        headers.forEach { (k, v) -> if (v.isNotBlank()) out[canonicalKey(k)] = v }

        val host = runCatching { Uri.parse(streamUrl).host?.lowercase(Locale.ROOT) }.getOrNull()

        // 1) Referer: yoksa sağlayıcının ana adresi, o da yoksa akışın kendi host'u.
        if (out["Referer"].isNullOrBlank()) {
            val home = host?.let { h -> PROVIDER_HOME.firstOrNull { h.contains(it.first) }?.second }
            val referer = home ?: host?.let { "https://$it/" }
            if (!referer.isNullOrBlank()) out["Referer"] = if (referer.endsWith("/")) referer else "$referer/"
        }

        // 2) Origin: Referer'ın şeması+host'u. CDN'ler bunu doğruluyor.
        if (out["Origin"].isNullOrBlank()) {
            val origin = out["Referer"]?.let { originOf(it) }
                ?: host?.let { "https://$it" }
            if (!origin.isNullOrBlank()) out["Origin"] = origin
        }

        // 3) Tarayıcı benzeri başlıklar: WAF/bot sayaçları bunlara bakıyor.
        if (out["User-Agent"].isNullOrBlank()) out["User-Agent"] = userAgent
        if (out["Accept"].isNullOrBlank()) out["Accept"] = "*/*"
        if (out["Accept-Language"].isNullOrBlank()) out["Accept-Language"] = "tr-TR,tr;q=0.9,en;q=0.8"

        return out
    }

    /** `https://host/a/b?c` → `https://host` */
    internal fun originOf(url: String): String? = runCatching {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0 && uri.port != defaultPort(scheme)) ":${uri.port}" else ""
        "$scheme://$host$port"
    }.getOrNull()

    private fun defaultPort(scheme: String): Int = if (scheme.equals("https", true)) 443 else 80

    /** HTTP başlık adları büyük/küçük harf duyarsız; tek biçime indirger. */
    private fun canonicalKey(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
        "referer" -> "Referer"
        "origin" -> "Origin"
        "user-agent" -> "User-Agent"
        "cookie" -> "Cookie"
        "accept" -> "Accept"
        "accept-language" -> "Accept-Language"
        "authorization" -> "Authorization"
        else -> raw
    }
}
