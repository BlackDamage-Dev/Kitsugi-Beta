package com.kitsugi.animelist.core.network

import com.kitsugi.animelist.core.diagnostics.KitsugiLog
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Locale

/**
 * **KitsugiNetworkLogInterceptor** — her ağ denemesini kalıcı günlüğe yazar (v2.4.342).
 *
 * Amaç: kullanıcı "hangi kaynak, ne zaman, hangi kodla" cevap verdiğini sonradan
 * görebilsin. Kullanıcı geri bildirimi: *"arka planda olan biteni o anda kopyalayamıyorum"*.
 *
 * ## Gizlilik ve gürültü kuralları
 *  - Yalnızca **yöntem + host + yol** yazılır. Sorgu dizesi, başlıklar ve gövde **asla**
 *    yazılmaz (jeton/anahtar sızmasın).
 *  - Görsel/CDN trafiği gibi 2xx ve hızlı GET'ler yalnızca liste/API host'larında kaydedilir;
 *    böylece on binlerce poster isteği günlüğü boğmaz.
 *  - Hata (>=400) ve istisnalar **her zaman** kaydedilir; kota başlıkları
 *    (`Retry-After`, `X-RateLimit-*`) da eklenir — "kota mı, oturum mu, servis mi" sorusunun
 *    doğrudan kanıtı budur.
 *
 * Bu sınıf hiçbir koşulda isteği durdurmaz/bozmaz: günlük yazımı tamamen `try/catch`
 * içindedir ve hata olsa bile istek normal akışına devam eder.
 */
class KitsugiNetworkLogInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val startedAt = System.currentTimeMillis()
        val isInterestingHost = INTERESTING_HOSTS.any { host.contains(it) }

        return try {
            val response = chain.proceed(request)
            runCatching {
                val elapsed = System.currentTimeMillis() - startedAt
                val code = response.code
                val shouldLog = code >= 400 || isInterestingHost || request.method != "GET" || elapsed > 3_000L
                if (shouldLog) {
                    val level = when {
                        code >= 500 -> KitsugiLog.Level.ERROR
                        code >= 400 -> KitsugiLog.Level.WARN
                        else -> KitsugiLog.Level.DEBUG
                    }
                    KitsugiLog.log(
                        level = level,
                        channel = KitsugiLog.Channel.NET,
                        tag = host,
                        message = "${request.method} ${pathOf(request.url.encodedPath)} → HTTP $code",
                        detail = buildString {
                            append(elapsed).append(" ms")
                            quotaHint(response)?.let { append(" | ").append(it) }
                        }
                    )
                }
            }
            response
        } catch (io: IOException) {
            runCatching {
                KitsugiLog.e(
                    KitsugiLog.Channel.NET,
                    host,
                    "${request.method} ${pathOf(request.url.encodedPath)} → bağlantı hatası",
                    "${System.currentTimeMillis() - startedAt} ms | ${io.javaClass.simpleName}: ${io.message}",
                    io
                )
            }
            throw io
        } catch (t: Throwable) {
            runCatching {
                KitsugiLog.e(
                    KitsugiLog.Channel.NET,
                    host,
                    "${request.method} ${pathOf(request.url.encodedPath)} → beklenmeyen hata",
                    t.javaClass.simpleName,
                    t
                )
            }
            throw t
        }
    }

    private fun pathOf(path: String): String {
        val trimmed = if (path.length > 120) path.take(120) + "…" else path
        return trimmed.ifBlank { "/" }
    }

    /** Kota/limit ile ilgili yanıt başlıkları — "neden 429" sorusunun doğrudan kanıtı. */
    private fun quotaHint(response: Response): String? {
        val retryAfter = response.header("Retry-After")
        val limit = response.header("X-RateLimit-Limit")
        val remaining = response.header("X-RateLimit-Remaining")
        val reset = response.header("X-RateLimit-Reset")
        if (retryAfter == null && limit == null && remaining == null && reset == null) return null
        return buildString {
            append("kota")
            retryAfter?.let { append(" retry-after=").append(it) }
            limit?.let { append(" limit=").append(it) }
            remaining?.let { append(" kalan=").append(it) }
            reset?.let { append(" sıfırlama=").append(it) }
        }
    }

    private companion object {
        /**
         * Liste/metadata API host'ları: buralarda **başarılı** istekler de kaydedilir, çünkü
         * "hiç istek gitmiyor mu, gidiyor da cevap mı gelmiyor" ayrımı ancak böyle yapılabilir.
         */
        val INTERESTING_HOSTS = listOf(
            "anilist.co",
            "myanimelist.net",
            "jikan",
            "kitsu",
            "shikimori",
            "bgm.tv",
            "simkl",
            "themoviedb",
            "tmdb",
            "arm.haglund.dev",
            "graphql"
        ).map { it.lowercase(Locale.US) }
    }
}
