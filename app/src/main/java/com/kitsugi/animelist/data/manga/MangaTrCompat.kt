package com.kitsugi.animelist.data.manga

import android.net.Uri

/**
 * Kullanıcının özel `Kitsugi-Manga-Plugins` reposundaki tek aktif eklenti olan
 * Manga-TR (eu.kanade.tachiyomi.extension.tr.mangatr, 1.6.24 / KeiSource 1.6)
 * için uygulama tarafı uyumluluk bilgisi.
 *
 * Eklentinin gerçek çalışma biçimi (repo kaynak kodundan doğrulandı):
 *  - Kanonik alan adı `https://manga-tr.com`, deeplink hostları `manga-tr.com` ve
 *    `www.manga-tr.com`; detay yolları tek segment `manga-*.html`.
 *  - Arama `/arama.html?icerik=...`, liste `/manga-list-sayfala.html`.
 *  - Bölüm listesi, detay HTML'indeki `listKey` ile imzalanmış
 *    POST `/cek/fetch_pages_manga.php` çağrısıyla gelir (20 + 100' lük sayfalar).
 *  - Okuyucu sayfası TEK KULLANIMLIK gate token + iki aşamalı XOR ile şifrelenmiş
 *    config içerir; görseller karıştırılmış yatay şeritler halinde gelir ve APK'nın
 *    kendi `scrambledImageInterceptor`'ı bunları çözer.
 *  - `Server: ddos-guard` her yanıtta bulunur; bu yüzden körü körüne "yeniden dene"
 *    yapmak bölüm anahtarını/geçit token'ını geçersiz kılar.
 *  - Üye girişi / bot koruması gerektiğinde eklenti IOException fırlatır ve çözüm
 *    aynı oturumda WebView üzerinden doğrulamadır.
 *
 * Bu nesne kaynak kodunu taklit ETMEZ; sadece yukarıdaki davranışın uygulama
 * tarafında doğru ele alınmasını sağlar.
 */
object MangaTrCompat {

    const val PACKAGE_NAME = "eu.kanade.tachiyomi.extension.tr.mangatr"
    const val EXTENSION_ID = "eu_kanade_tachiyomi_extension_tr_mangatr"

    const val CANONICAL_DOMAIN = "manga-tr.com"
    const val CANONICAL_BASE_URL = "https://manga-tr.com"

    /** Gerçekten bilinen alan adları. Rastgele TLD türetmek bu kaynak için zararlıdır. */
    val KNOWN_DOMAINS = listOf(
        "manga-tr.com",
        "www.manga-tr.com",
        "mangatr.net",
        "www.mangatr.net",
        "manga-tr.net",
        "www.manga-tr.net",
    )

    private val VERIFICATION_MARKERS = listOf(
        "webview", "üye girişi", "uye girisi", "bot korumasını", "bot korumasini",
        "captcha", "doğrulama", "dogrulama", "verify.php", "challenge",
        "güvenlik doğrulaması", "guvenlik dogrulamasi",
    )
    // Not: "ddos-guard" bilinçli olarak marker DEĞİL — site bunu HER yanıta
    // ekliyor. Genel 403/503 hatalarını "üye girişi gerekli" sanmamalıyız.

    /** Eklentinin hata mesajı WebView / üye girişi / bot koruması gerektiriyor mu? */
    fun isVerificationError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val lower = message.lowercase()
        return VERIFICATION_MARKERS.any { it in lower }
    }

    fun isMangaTr(source: MangaSource): Boolean =
        source.pkgName.equals(PACKAGE_NAME, ignoreCase = true) ||
            MangaTrCompat.isMangaTrBaseUrl(source.baseUrl)

    fun isMangaTrBaseUrl(baseUrl: String): Boolean {
        val host = runCatching { Uri.parse(baseUrl).host.orEmpty() }.getOrDefault("")
            .lowercase()
        return host == CANONICAL_DOMAIN || host == "www.$CANONICAL_DOMAIN" || host == "mangatr.net"
    }

    fun isMangaTrExtensionId(extensionId: String): Boolean =
        extensionId.equals(EXTENSION_ID, ignoreCase = true) ||
            extensionId.replace("_", ".").equals(PACKAGE_NAME, ignoreCase = true)

    /**
     * WebView doğrulaması için bölüm URL'i.
     * Eklenti chapter.url'i domain'siz (`/manga-.../bolum...`) verir; `baseUrl` ile
     * birleştirmek gerekir. Yanlış/boş baseUrl durumunda kanonik adrese düşeriz.
     */
    fun readerUrl(source: MangaSource, chapterUrl: String): String {
        if (chapterUrl.startsWith("http://", true) || chapterUrl.startsWith("https://", true)) {
            return chapterUrl
        }
        val configured = source.baseUrl.trim().trimEnd('/')
        val base = if (configured.startsWith("http", true)) configured else CANONICAL_BASE_URL
        val path = if (chapterUrl.startsWith("/")) chapterUrl else "/$chapterUrl"
        return base + path
    }
}
