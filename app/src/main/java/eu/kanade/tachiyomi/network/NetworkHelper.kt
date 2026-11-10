package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.kitsugi.animelist.core.network.CloudflareInterceptor
import com.kitsugi.animelist.core.network.IPv4FirstDns
import com.lagradost.nicehttp.ignoreAllSSLErrors
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import eu.kanade.tachiyomi.network.interceptor.WireDecompressionInterceptor

class NetworkHelper(context: Context) {
    val cookieJar = WebViewCookieJar(context)

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        // ZORUNLU (extensionLib 1.6 / KeiSource): keiyoushi.source.KeiSource.client ilk
        // erisimde host uygulamanin varsayilan client'inda su uc sinif adinin bulunmasini
        // `check(...)` ile dogrular: UncaughtExceptionInterceptor, UserAgentInterceptor,
        // CloudflareInterceptor. Eksikse eklenti ilk istekte
        // IllegalStateException("... must be present in default client") firlatir ve
        // kaynak hic calismaz (Kitsugi'da 72/77 TR eklentisi bu siniftadir).
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor { defaultUserAgentProvider() })
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val host = url.host

            var matchedSource: com.kitsugi.animelist.data.manga.MangaSource? = null
            try {
                val loadedSources = com.kitsugi.animelist.data.manga.MangaExtensionLoader.getLoadedSources()
                matchedSource = loadedSources.find { src ->
                    val origUri = android.net.Uri.parse(src.originalBaseUrl)
                    val origHost = origUri.host
                    origHost != null && origHost.equals(host, ignoreCase = true)
                }
            } catch (e: Exception) {
                android.util.Log.e("NetworkHelper", "Error scanning sources for domain redirect", e)
            }

            val activeDomain = matchedSource?.let {
                com.kitsugi.animelist.data.manga.SourceConfigStore(context).getActiveDomain(it)
            }
            val rewrittenUrl = if (activeDomain != null && !activeDomain.equals(host, ignoreCase = true)) {
                runCatching { url.newBuilder().host(activeDomain).build() }.getOrNull()
            } else {
                null
            }

            val newRequestBuilder = if (rewrittenUrl != null) {
                val reqBuilder = request.newBuilder().url(rewrittenUrl)
                val referer = request.header("Referer")
                if (referer != null && activeDomain != null) {
                    reqBuilder.header("Referer", referer.replaceFirst(host, activeDomain, ignoreCase = true))
                }
                reqBuilder
            } else {
                request.newBuilder()
            }

            // Manga-TR image CDN requires the site origin, while the source client retains its interceptors.
            if ((host.equals("mngtr.site", true) || host.endsWith(".mngtr.site", true)) && request.header("Referer").isNullOrBlank()) {
                newRequestBuilder.header("Referer", "https://manga-tr.com")
            }

            val hasUserAgent = request.header("User-Agent")
            if (hasUserAgent.isNullOrBlank() || 
                hasUserAgent.startsWith("okhttp", ignoreCase = true) || 
                hasUserAgent.equals("Mozilla/5.0", ignoreCase = true)
            ) {
                newRequestBuilder.header("User-Agent", defaultUserAgentProvider())
            }
            chain.proceed(newRequestBuilder.build())
        }
        .ignoreAllSSLErrors()
        .addInterceptor(com.lagradost.cloudstream3.network.CloudflareKiller())
        .addInterceptor(com.lagradost.cloudstream3.network.DdosGuardKiller(alwaysBypass = false))
        // KeiSource'un zorunlu tuttugu ucuncu sinif adi. Zincirin EN SONUNA eklenir:
        // boylece ham ag yanitini once bu interceptor gorur, CloudflareKiller/DdosGuardKiller
        // cozemediyse WebView ile cf_clearance alinir; cozulmusse no-op olarak gecer.
        .addInterceptor(CloudflareInterceptor(context))
        // Wire-level decompression (br/gzip/deflate) & Accept-Encoding normalizer so extension
        // interceptors (DDoSGuardInterceptor.peekBody, check.js) and Jsoup parsers always see
        // decoded bodies even when headersBuilder() manually sets Accept-Encoding.
        .addNetworkInterceptor(WireDecompressionInterceptor())
        .build()

    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT
}
