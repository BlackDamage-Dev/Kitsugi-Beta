package com.kitsugi.animelist.core.network

import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Liste kaynaklarının (Simkl, Bangumi, TMDB, Shikimori, Jikan, AniList, Kitsu) HTTP hatalarını
 * [PlatformFailureRegistry]'ye bildirir.
 *
 * Neden ağ katmanında? Kaynak istemcilerinin çoğu başarısız yanıtta `null` döndürüp hatayı
 * yutuyor; ağ katmanı ise her kaynağın her uç noktasındaki gerçek HTTP kodunu tek yerden görür.
 *
 * Notlar:
 *  - Yalnızca 400+ yanıtlar kaydedilir; 304 (önbellek) gibi yanıtlar hata sayılmaz.
 *  - Yalnızca alan adı ve yol kaydedilir; sorgu dizesi (API anahtarları) asla kaydedilmez.
 *  - Gövde okunmaz, yanıt değiştirilmez.
 *  - Görsel/video istekleri hiç kaydedilmez: Coil aynı OkHttp istemcisini kullandığı için
 *    tek bir bozuk poster (404) kaynağın API'si çöküyormuş gibi görünüyordu.
 */
class PlatformFailureInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        // Coil görsel isteklerinde `Accept: image/*` gönderir; uzantısız CDN adresleri de
        // bu sayede API hatası sayılmaz (ağ katmanındaki host/uzantı filtresine ek güvence).
        val accept = request.header("Accept").orEmpty()
        val mediaRequest = accept.startsWith("image/") || accept.startsWith("video/") ||
            accept.startsWith("audio/")
        if (response.code >= 400 && !mediaRequest) {
            runCatching {
                val simklRateLimitMarker = if (
                    request.url.host.equals("api.simkl.com", ignoreCase = true) && response.code == 400
                ) {
                    runCatching {
                        response.peekBody(SimklRateLimitPolicy.MAX_ERROR_BODY_BYTES).string()
                    }.getOrDefault("")
                        .takeIf { SimklRateLimitPolicy.isRateLimitMessage(it) }
                        ?.let { "rate_limit" }
                } else null

                PlatformFailureRegistry.reportHttp(
                    host = request.url.host,
                    httpCode = response.code,
                    path = request.url.encodedPath,
                    diagnosticHint = simklRateLimitMarker,
                )
            }
        }
        return response
    }
}
