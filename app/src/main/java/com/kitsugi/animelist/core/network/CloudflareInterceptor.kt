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
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Cloudflare & DDoS-Guard 403/503 JS challenge solver backed by Android WebView.
 *
 * Note: KeiSource inspects `client.interceptors` by `javaClass.simpleName == "CloudflareInterceptor"`,
 * so the class name and package are part of the extension host ABI.
 *
 * Because extension-level interceptors (such as Manga-TR's `DDoSGuardInterceptor`) are appended
 * after `NetworkHelper`'s base interceptors, `chain.proceed(request)` always lets the extension's
 * own lightweight solver run first. WebView resolution is invoked only if the response coming back
 * up the chain is still an unresolved 403/503 protection challenge on a GET request.
 */
class CloudflareInterceptor(private val context: Context) : Interceptor {

    companion object {
        private const val TAG = "CloudflareInterceptor"
        private const val TIMEOUT_MS = 15_000L
        private const val CF_CLEARANCE_COOKIE = "cf_clearance"
        private val DDOS_CLEARANCE_PREFIXES = listOf("__ddg1", "__ddg2", "__ddg8", "__ddg9", "__ddg10")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!request.method.equals("GET", ignoreCase = true)) {
            return response
        }
        if (!isProtectionChallenge(response)) {
            return response
        }
        val mainLooper = Looper.getMainLooper() ?: return response
        if (Thread.currentThread() === mainLooper.thread) {
            return response
        }

        val isDdosGuard = isDdosGuardHeader(response)
        val effectiveUserAgent = request.header("User-Agent")
            ?.takeIf { it.isNotBlank() }
            ?: NuvioOkHttpProvider.USER_AGENT

        Log.d(TAG, "Protection challenge (${response.code}, ddos=$isDdosGuard) detected: ${request.url}")
        response.close()

        val resolvedCookieHeader = resolveWithWebView(
            url = request.url.toString(),
            userAgent = effectiveUserAgent,
            expectDdosGuard = isDdosGuard,
            call = chain.call(),
        )

        // Persist any newly acquired WebView cookies into NetworkHelper's WebViewCookieJar
        // so subsequent pagination, detail, chapter, and image requests reuse the session.
        runCatching {
            val helper = uy.kohesive.injekt.Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java)
            helper.cookieJar.syncFromWebView(request.url.toString())
        }

        if (resolvedCookieHeader.isNullOrBlank()) {
            Log.w(TAG, "Protection cookie could not be resolved via WebView (timeout): ${request.url}")
            return chain.proceed(request)
        }

        val existingCookie = request.header("Cookie")
        val mergedCookie = mergeCookieStrings(existingCookie, resolvedCookieHeader)
        val retryRequest = request.newBuilder()
            .header("Cookie", mergedCookie)
            .header("User-Agent", effectiveUserAgent)
            .build()
        return chain.proceed(retryRequest)
    }

    private fun isProtectionChallenge(response: Response): Boolean {
        if (response.code != 403 && response.code != 503) return false
        val server = response.header("Server") ?: response.header("server") ?: ""
        val cfRay = response.header("CF-RAY") ?: response.header("cf-ray") ?: ""
        return server.contains("cloudflare", ignoreCase = true) ||
            cfRay.isNotEmpty() ||
            server.contains("ddos-guard", ignoreCase = true)
    }

    private fun isDdosGuardHeader(response: Response): Boolean {
        val server = response.header("Server") ?: response.header("server") ?: ""
        return server.contains("ddos-guard", ignoreCase = true)
    }

    private fun mergeCookieStrings(existing: String?, incoming: String): String {
        val map = linkedMapOf<String, String>()
        fun parseInto(raw: String?) {
            raw?.split(";")?.forEach { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    map[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                }
            }
        }
        parseInto(existing)
        parseInto(incoming)
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun hasClearanceCookie(cookieString: String, expectDdosGuard: Boolean, pageTitle: String?): Boolean {
        val pairs = cookieString.split(";").map { it.trim() }
        val hasCf = pairs.any { it.startsWith("$CF_CLEARANCE_COOKIE=") && it.substringAfter('=').isNotBlank() }
        if (hasCf) return true
        if (!expectDdosGuard) return false

        val hasDdg = pairs.any { entry ->
            val name = entry.substringBefore('=').trim()
            val value = entry.substringAfter('=', "").trim()
            value.isNotEmpty() && DDOS_CLEARANCE_PREFIXES.any { prefix -> name.startsWith(prefix) }
        }
        if (!hasDdg) return false
        val lowerTitle = pageTitle.orEmpty().lowercase()
        val stillChallengeTitle = lowerTitle.contains("ddos-guard") ||
            lowerTitle.contains("just a moment") ||
            lowerTitle.contains("please wait")
        return !stillChallengeTitle
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(
        url: String,
        userAgent: String,
        expectDdosGuard: Boolean,
        call: okhttp3.Call,
    ): String? {
        val latch = CountDownLatch(1)
        val resolvedCookies = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val webViewRef = java.util.concurrent.atomic.AtomicReference<WebView?>(null)
        var timeoutTask: Runnable? = null

        fun releaseOnMainThread() {
            if (!settled.compareAndSet(false, true)) return
            timeoutTask?.let { handler.removeCallbacks(it) }
            runCatching { CookieManager.getInstance().flush() }
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
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                val webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = userAgent
                    runCatching { cookieManager.setAcceptThirdPartyCookies(this, true) }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean = false

                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            if (settled.get() || call.isCanceled()) {
                                releaseOnMainThread()
                                return
                            }
                            val httpUrl = pageUrl.toHttpUrlOrNull() ?: return
                            val cookieString = runCatching {
                                cookieManager.getCookie(httpUrl.toString())
                            }.getOrNull().orEmpty()

                            if (cookieString.isNotBlank() &&
                                hasClearanceCookie(cookieString, expectDdosGuard, view.title)
                            ) {
                                resolvedCookies.set(cookieString)
                                Log.d(TAG, "Clearance cookies acquired via WebView for ${httpUrl.host}")
                                releaseOnMainThread()
                            }
                        }
                    }
                }
                webViewRef.set(webView)

                timeoutTask = Runnable {
                    if (!settled.get()) {
                        Log.w(TAG, "WebView timeout after ${TIMEOUT_MS}ms — $url")
                        runCatching { cookieManager.getCookie(url) }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() && hasClearanceCookie(it, expectDdosGuard, pageTitle = null) }
                            ?.let(resolvedCookies::set)
                        releaseOnMainThread()
                    }
                }.also { handler.postDelayed(it, TIMEOUT_MS) }
                webView.loadUrl(url)
            } catch (e: Exception) {
                Log.e(TAG, "WebView init error: ${e.message}", e)
                releaseOnMainThread()
            }
        }

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

        return resolvedCookies.get()
    }
}
