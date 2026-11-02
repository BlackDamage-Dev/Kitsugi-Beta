package com.kitsugi.animelist.data.cloudstream

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Cloudstream CS3 sağlayıcıları için özel OkHttp istemci fabrikası.
 *
 * Şu özellikleri sağlar:
 *  1. SSL Bypass (ignoreSSL): NeonSpor gibi geçersiz/süresi dolmuş SSL sertifikası
 *     kullanan Türkçe yayın sunucularından kaynaklanan SSLHandshakeException hatalarını engeller.
 *  2. Video Interceptor Enjeksiyonu: CS3 eklentilerin `getVideoInterceptor()` metodundan
 *     dönen interceptor'ı her segment isteğine (chunk) enjekte eder. Bu sayede:
 *       - Vidmoly, Mp4Upload, Wishfast gibi sunucuların Referer/User-Agent kontrollerini aşar.
 *       - 403 Forbidden ve donma hatalarını giderir.
 *
 * Cloudstream Referansı: CS3IPlayer.kt L1893-1901 (ignoreSSL) ve L1940-1947 (interceptor).
 */
object CsVideoInterceptorFactory {

    private const val TAG = "CsVideoInterceptorFactory"

    /** Construct the same link for provider interception in playback and verification. */
    @Suppress("DEPRECATION")
    fun videoInterceptor(
        provider: com.lagradost.cloudstream3.MainAPI?,
        name: String,
        url: String,
        headers: Map<String, String>,
        quality: Int?,
        mediaType: com.kitsugi.animelist.core.player.StreamMediaType?
    ): Interceptor? = runCatching {
        provider ?: return null
        val format = com.kitsugi.animelist.core.player.StreamFormatPolicy.resolve(url, mediaType)
        val link = com.lagradost.cloudstream3.utils.ExtractorLink(
            source = provider.name,
            name = name,
            url = url,
            referer = CsStreamHeaders.value(headers, "Referer").orEmpty(),
            quality = quality ?: 1080,
            headers = headers,
            extractorData = null,
            type = when (format) {
                com.kitsugi.animelist.core.player.StreamMediaType.HLS -> com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                com.kitsugi.animelist.core.player.StreamMediaType.DASH -> com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH
                else -> com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
            },
            audioTracks = emptyList()
        )
        provider.getVideoInterceptor(link)
    }.getOrNull()

    // Trust-all X509TrustManager: tüm sunucu sertifikalarını geçerli kabul eder.
    // Bu sadece CS kaynaklar için kullanılır, genel HTTP isteklerinde değil.
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
    }

    /**
     * CS3 video oynatmak için optimize edilmiş OkHttpClient oluşturur.
     *
     * @param interceptor Eklentinin getVideoInterceptor() metodundan dönen interceptor (opsiyonel).
     *                    Varsa, her video segment isteğine enjekte edilir.
     * @param headers     İstek başlıkları haritası (Referer, User-Agent vb.).
     * @param ignoreSSL   SSL doğrulamasını devre dışı bırakır (varsayılan: true).
     *                    NeonSpor ve benzeri geçersiz sertifikalı siteler için gerekli.
     * @return Yapılandırılmış OkHttpClient.
     */
    fun buildClient(
        interceptor: Interceptor? = null,
        headers: Map<String, String> = emptyMap(),
        ignoreSSL: Boolean = true
    ): OkHttpClient {
        Log.d(TAG, "Building CS OkHttpClient: ignoreSSL=$ignoreSSL, hasInterceptor=${interceptor != null}, headers=${headers.keys}")

        return OkHttpClient.Builder().apply {
            dns(com.kitsugi.animelist.core.network.IPv4FirstDns())
            connectTimeout(20, TimeUnit.SECONDS)
            readTimeout(20, TimeUnit.SECONDS)
            writeTimeout(10, TimeUnit.SECONDS)
            followRedirects(true)
            followSslRedirects(true)

            // WebView ve sistem genelindeki çerezleri (Cloudflare bypass dahil) oynatıcıyla paylaşmak için
            try {
                val cookieJar = uy.kohesive.injekt.Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java).cookieJar
                cookieJar(cookieJar)
                Log.d(TAG, "Shared cookieJar injected successfully into player client")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to inject shared cookieJar: ${e.message}")
            }

            // SSL bypass — geçersiz sertifikalı sunucular için
            if (ignoreSSL) {
                try {
                    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    hostnameVerifier { _, _ -> true }
                    Log.d(TAG, "SSL verification disabled for CS source")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to configure SSL bypass", e)
                }
            }

            // Header interceptor: Referer, User-Agent vb. her isteğe eklenir
            val finalHeaders = headers.toMap()
            addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder().apply {
                    finalHeaders.forEach { (key, value) -> header(key, value) }
                    // A request-specific User-Agent (for example during a media probe)
                    // must not be replaced by the default when no override was supplied.
                    if (finalHeaders.keys.none { it.equals("user-agent", ignoreCase = true) } &&
                        original.header("User-Agent").isNullOrBlank()
                    ) {
                        header("User-Agent", com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT)
                    }
                }.build()
                chain.proceed(request)
            }
            Log.d(TAG, "Header interceptor added: ${finalHeaders.keys}")

            // Eklenti interceptor'ı: token yenileme, Cloudflare bypass vb. için
            if (interceptor != null) {
                addInterceptor(interceptor)
                Log.d(TAG, "Provider video interceptor injected")
            }
        }.build()
    }

    /**
     * Sadece SSL bypass yapan basit bir istemci (header veya interceptor olmaksızın).
     * Hızlı fallback durumları için kullanılır.
     */
    fun buildBasicSslBypassClient(): OkHttpClient {
        return buildClient(interceptor = null, headers = emptyMap(), ignoreSSL = true)
    }
}
