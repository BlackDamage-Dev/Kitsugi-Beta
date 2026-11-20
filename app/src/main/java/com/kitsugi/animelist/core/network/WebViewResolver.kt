package com.kitsugi.animelist.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * T1.11: WebViewResolver
 *
 * Cloudflare Turnstile / IUAM challenge'ı WebView ile çözer ve çerezleri döndürür.
 * CloudStream WebViewResolver.kt referansından adapte edildi.
 *
 * Kullanım:
 *   val cookies = WebViewResolver.resolve(context, "https://site.com/manga/page")
 *   // Ardından bu cookie'leri OkHttp isteğine ekle
 *
 * NOT: WebView UI thread gerektirdiğinden, bu fonksiyon ana thread'de çağrılmalıdır.
 * Coroutine içinden kullanırken Dispatchers.Main kullanın.
 */
object WebViewResolver {

    private const val TAG = "WebViewResolver"
    private const val DEFAULT_TIMEOUT_MS = 15_000L
    private const val CF_CHALLENGE_INDICATOR = "cf_clearance"

    /**
     * Verilen URL'i WebView ile açar ve Cloudflare challenge'ı çözer.
     *
     * @param context Activity veya Application context
     * @param url Challenge içeren URL
     * @param timeoutMs Maksimum bekleme süresi (ms)
     * @return Cookie map (boş olabilir — challenge başarısız olunca)
     */
    suspend fun resolve(
        context: Context,
        url: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Map<String, String> = withContext(Dispatchers.Main) {
        // v2.4.492 — BELLEK VE ÇÖKME DÜZELTMESİ
        //
        // Eski kodda:
        //  * zaman aşımında WebView **hiç yok edilmiyordu** (`withTimeoutOrNull` yalnızca
        //    coroutine'i iptal ediyordu; `invokeOnCancellation` iptal edilmiş bir
        //    continuation'da her zaman çalışmayabiliyordu) → her CF denemesi bir WebView,
        //    renderer süreci ve onlarca MB native bellek sızdırıyordu,
        //  * "challenge çözüldü" kararı `!url.contains("challenge")` idi; yani Cloudflare
        //    `/arama/...` gibi normal bir adrese yönlendirdiğinde meydan okuma HÂLÂ ekranda
        //    olsa bile boş çerezle "çözüldü" deniyordu,
        //  * `webView.destroy()` ana iş parçacığında, sayfa yüklenirken çağrılabiliyordu.
        //
        // 2026-11-20 günlüğünde bunun kanıtı: `WebViewResolver` 120 sn bekleyen istekler,
        // art arda LOS çöp toplamaları (33-57 MB) ve `MPEG4Writer` çökmesinden önceki GC
        // fırtınası. Artık WebView her durumda (başarı, iptal, zaman aşımı) tek sefer
        // yok edilir ve karar gerçek `cf_clearance`/DDoS-Guard çerezine dayanır.
        val cookies = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Map<String, String>> { continuation ->
                val webView = createWebView(context)
                var settled = false

                fun finish(cookieString: String) {
                    if (settled) return
                    settled = true
                    CookieManager.getInstance().flush()
                    runCatching { webView.stopLoading() }
                    runCatching { webView.webViewClient = WebViewClient() }
                    runCatching { webView.destroy() }
                    if (continuation.isActive) {
                        continuation.resume(parseCookieString(cookieString))
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        if (settled) return
                        val target = pageUrl ?: url
                        val cookieString = runCatching {
                            CookieManager.getInstance().getCookie(target)
                        }.getOrNull().orEmpty()

                        if (hasClearanceCookie(cookieString)) {
                            Log.d(TAG, "Challenge resolved for $url → ${parseCookieString(cookieString).size} cookies")
                            finish(cookieString)
                        } else {
                            Log.d(TAG, "Challenge page still showing: $target")
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        // Cloudflare challenge sayfaları arasındaki yönlendirmelere izin ver
                        return false
                    }
                }

                continuation.invokeOnCancellation {
                    // İptalde de WebView MUTLAKA yok edilir (sızıntı yok).
                    runCatching { webView.stopLoading() }
                    runCatching { webView.destroy() }
                    Log.d(TAG, "WebView cancelled for $url")
                }

                webView.loadUrl(url)
            }
        }

        if (cookies != null) return@withContext cookies

        Log.w(TAG, "WebView resolver timed out for $url")
        // Zaman aşımı: mevcut çerezleri dön (kısmi başarı). WebView burada zaten yok edildi;
        // yalnızca CookieManager okunur.
        parseCookieString(runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull().orEmpty())
    }

    /** Gerçek bir Cloudflare/DDoS-Guard geçiş çerezi var mı? (boş `cf_clearance` sayılmaz) */
    private fun hasClearanceCookie(cookieString: String): Boolean {
        if (cookieString.isBlank()) return false
        return cookieString.split(";").any { part ->
            val name = part.substringBefore('=').trim()
            val value = part.substringAfter('=', "").trim()
            value.isNotEmpty() && (
                name.equals(CF_CHALLENGE_INDICATOR, ignoreCase = true) ||
                    name.startsWith("__ddg", ignoreCase = true) ||
                    name.startsWith("ddg", ignoreCase = true)
                )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = KitsugiUserAgent.DEFAULT
                // Cloudflare için JS gerekli
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    /**
     * `name1=value1; name2=value2; ...` formatındaki cookie string'ini Map'e çevirir.
     */
    private fun parseCookieString(cookieString: String): Map<String, String> {
        return cookieString
            .split(";")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx == -1) null
                else {
                    val key = part.substring(0, idx).trim()
                    val value = part.substring(idx + 1).trim()
                    if (key.isNotBlank()) key to value else null
                }
            }
            .toMap()
    }

    /**
     * Belirli bir URL için mevcut çerezleri çeker (WebView açmadan).
     * Önceki resolve() çağrısından kalan çerezler için kullanılır.
     */
    fun getCachedCookies(url: String): Map<String, String> {
        val cookieString = CookieManager.getInstance().getCookie(url) ?: return emptyMap()
        return parseCookieString(cookieString)
    }

    /** Tüm çerezleri temizle (hesap çıkışı vb.) */
    fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}

/** Kitsugi kullanıcı ajanı sabitleri */
object KitsugiUserAgent {
    const val DEFAULT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
