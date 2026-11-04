package com.kitsugi.animelist.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Cloudflare 403/503 challenge'larını WebView üzerinden otomatik çözen OkHttp interceptor'ı.
 *
 * Akış:
 *  request → chain.proceed()
 *    → 403/503 + "Server: cloudflare" header?
 *      EVET: WebViewResolver(url) → 15s içinde cf_clearance cookie bekle
 *            → cookie bulundu: request'e Cookie header ekle → retry
 *      HAYIR: normal yanıt döndür
 *
 * Kullanım: OkHttpClient.Builder().addInterceptor(CloudflareInterceptor(context))
 * NOT: WebView main thread'de çalışmalıdır — Handler(Looper.getMainLooper()) ile tetiklenir.
 */
class CloudflareInterceptor(private val context: Context) : Interceptor {

    companion object {
        private const val TAG = "CloudflareInterceptor"
        // 15s: slow hosts need more time, but we will cancel immediately on exit
        private const val TIMEOUT_MS = 15_000L
        private const val CF_CLEARANCE_COOKIE = "cf_clearance"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (com.kitsugi.animelist.data.manga.isMangaTrHost(request.url.host)) return chain.proceed(request)
        val response = chain.proceed(request)

        // Sadece 403/503 + Cloudflare server header'ı varsa müdahale et
        if (!isCloudflareChallenge(response)) {
            return response
        }

        Log.d(TAG, "Cloudflare challenge tespit edildi: ${request.url}")
        response.close()

        // WebView ile cookie'yi çöz
        val cookie = resolveWithWebView(request.url.toString(), chain.call())
        if (cookie == null) {
            Log.w(TAG, "Cloudflare cookie alınamadı (timeout). Orijinal isteği tekrarlıyoruz.")
            // Cookie olmadan tekrar dene (en azından bazı kaynaklarda çalışır)
            return chain.proceed(request)
        }

        val existingCookie = request.header("Cookie")
        val mergedCookie = if (!existingCookie.isNullOrBlank()) {
            if (existingCookie.contains(CF_CLEARANCE_COOKIE)) existingCookie else "$existingCookie; $CF_CLEARANCE_COOKIE=$cookie"
        } else {
            "$CF_CLEARANCE_COOKIE=$cookie"
        }
        val retryRequest = request.newBuilder()
            .header("Cookie", mergedCookie)
            .header("User-Agent", NuvioOkHttpProvider.USER_AGENT)
            .build()
        return chain.proceed(retryRequest)
    }

    private fun isCloudflareChallenge(response: Response): Boolean {
        if (response.code != 403 && response.code != 503) return false
        val server = response.header("Server") ?: response.header("server") ?: ""
        val cfRay = response.header("CF-RAY") ?: response.header("cf-ray") ?: ""
        return server.contains("cloudflare", ignoreCase = true) || cfRay.isNotEmpty()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(url: String, call: okhttp3.Call): String? {
        val latch = CountDownLatch(1)
        val cfClearance = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val webViewRef = java.util.concurrent.atomic.AtomicReference<WebView?>(null)
        var timeoutTask: Runnable? = null

        // WebView lifecycle methods must stay on the main thread. Success, timeout and
        // cancellation all funnel through this one-shot teardown, so none can destroy the
        // same Chromium instance twice (and success no longer leaves it resident forever).
        fun releaseOnMainThread() {
            if (!settled.compareAndSet(false, true)) return
            timeoutTask?.let { handler.removeCallbacks(it) }
            val webView = webViewRef.getAndSet(null)
            if (webView != null) {
                try { (webView.parent as? android.view.ViewGroup)?.removeView(webView) } catch (_: Throwable) {}
                try { webView.stopLoading() } catch (_: Throwable) {}
                try { webView.webViewClient = WebViewClient() } catch (_: Throwable) {}
                try { webView.webChromeClient = null } catch (_: Throwable) {}
                try { webView.destroy() } catch (_: Throwable) {}
            }
            latch.countDown()
        }

        handler.post {
            if (settled.get() || call.isCanceled()) {
                releaseOnMainThread()
                return@post
            }
            try {
                val webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = NuvioOkHttpProvider.USER_AGENT

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            // Let WebView handle redirects itself; re-loading here can loop.
                            return false
                        }

                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            if (settled.get() || call.isCanceled()) {
                                releaseOnMainThread()
                                return
                            }
                            // Her sayfa yüklenişinde cf_clearance cookie'yi kontrol et
                            val httpUrl = pageUrl.toHttpUrlOrNull() ?: return
                            val cookieString = runCatching {
                                CookieManager.getInstance().getCookie(httpUrl.toString())
                            }.getOrNull() ?: return

                            val headers = okhttp3.Headers.Builder().add("Set-Cookie", cookieString).build()
                            val parsed = Cookie.parseAll(httpUrl, headers)
                            val found = parsed.find { it.name == CF_CLEARANCE_COOKIE }?.value
                                ?: cookieString.split(";")
                                    .map { it.trim() }
                                    .firstOrNull { it.startsWith("$CF_CLEARANCE_COOKIE=") }
                                    ?.removePrefix("$CF_CLEARANCE_COOKIE=")

                            if (!found.isNullOrBlank()) {
                                cfClearance.set(found)
                                Log.d(TAG, "cf_clearance cookie bulundu!")
                                releaseOnMainThread()
                            }
                        }
                    }
                }
                webViewRef.set(webView)

                // Timeout: varsa son bir kez cookie'yi al, sonra WebView'ı her durumda kapat.
                timeoutTask = Runnable {
                    if (!settled.get()) {
                        Log.w(TAG, "WebView timeout: cf_clearance 15s içinde alınamadı — $url")
                        runCatching { CookieManager.getInstance().getCookie(url) }
                            .getOrNull()
                            ?.split(";")
                            ?.map { it.trim() }
                            ?.firstOrNull { it.startsWith("$CF_CLEARANCE_COOKIE=") }
                            ?.removePrefix("$CF_CLEARANCE_COOKIE=")
                            ?.takeIf { it.isNotBlank() }
                            ?.let(cfClearance::set)
                        releaseOnMainThread()
                    }
                }.also { handler.postDelayed(it, TIMEOUT_MS) }
                webView.loadUrl(url)
            } catch (e: Exception) {
                Log.e(TAG, "WebView başlatma hatası: ${e.message}", e)
                releaseOnMainThread()
            }
        }

        // Wait in a cancellation-aware loop. A deadline fallback guarantees cleanup even if
        // the delayed timeout callback was postponed by a busy main thread.
        val deadline = System.currentTimeMillis() + TIMEOUT_MS + 1000L
        while (latch.count > 0 && System.currentTimeMillis() < deadline) {
            if (call.isCanceled()) {
                Log.w(TAG, "Call was canceled, destroying WebView resolver.")
                handler.post { releaseOnMainThread() }
                break
            }
            try {
                latch.await(100L, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                handler.post { releaseOnMainThread() }
                break
            }
        }
        if (latch.count > 0) handler.post { releaseOnMainThread() }

        return cfClearance.get()
    }
}
