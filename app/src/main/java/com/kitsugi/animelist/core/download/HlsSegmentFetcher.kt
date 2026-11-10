package com.kitsugi.animelist.core.download

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * HLS segment/playlist isteklerini **kaynak dostu** biçimde yapan istemci.
 *
 * ## Çözdüğü problem (HTTP 429)
 *
 * Eski indirici her segmenti 3 kez, arada 1-2 saniye bekleyerek deniyordu. Bir CDN
 * "dakikada X istek" sınırı uyguladığında bu strateji işe yaramıyor:
 *
 *  1. Denemeler arasında **istek hızı düşürülmüyor** — 3 deneme de aynı hızda gidiyor,
 *     yani limit zaten doluysa üçü de 429 alıyor.
 *  2. `Retry-After` başlığı **okunmuyor** — sunucu "30 saniye bekle" derken biz 2 sn
 *     sonra tekrar vuruyoruz. Bu, cezayı uzatıyor.
 *  3. `KitsugiHttpClient` içindeki `RetryInterceptor` sessizce **2 gizli deneme** daha
 *     yapıyor; yani tek bir segment 9 gerçek istek üretebiliyor. Durum daha da kötüleşiyor.
 *  4. Host bazlı bir hız tavanı yok; birden çok indirme aynı host'u paralel dövüyor.
 *
 * Bu sınıf:
 *  - Host başına **global** minimum istek aralığı uygular (tüm indirmeler paylaşır),
 *  - 429'da `Retry-After` → yoksa üstel geri çekilme + jitter uygular,
 *  - 429 tekrar ettikçe o host için **hızı kademeli düşürür** (tavanlı),
 *  - başarılı isteklerde hızı yavaşça toparlar (histerezis),
 *  - 403/404 gibi kalıcı hatalarda boşuna denemez.
 */
class HlsSegmentFetcher(
    private val client: OkHttpClient,
    private val tag: String = "HlsFetcher"
) {

    companion object {
        /**
         * Host başına son istek zamanı. Tüm indirme işleri (aynı anda birden fazla
         * bölüm indirilse bile) bu tabloyu paylaşır — asıl limit sunucu tarafında
         * IP bazlı olduğu için uygulama içinde de tek noktadan ölçmek gerekir.
         */
        private val lastRequestAtMs = ConcurrentHashMap<String, Long>()

        /** 429 sonrası host'un "kilitli" olduğu zaman. */
        private val cooldownUntilMs = ConcurrentHashMap<String, Long>()

        /** Host başına dinamik istek aralığı (ms). 429 aldıkça büyür, başarıda küçülür. */
        private val adaptiveIntervalMs = ConcurrentHashMap<String, Long>()

        /** Host'un arka arkaya kaç 429 aldığı. */
        private val consecutive429 = ConcurrentHashMap<String, Int>()

        private val throttleLock = Any()

        /** Hız sınırı muhasebesi için host anahtarı. */
        private fun hostOf(url: String): String =
            runCatching { java.net.URI(url).host?.lowercase(java.util.Locale.ROOT) }
                .getOrNull()
                ?: url

        /** Test/teşhis için sıfırlar. */
        fun resetThrottleState() {
            lastRequestAtMs.clear()
            cooldownUntilMs.clear()
            adaptiveIntervalMs.clear()
            consecutive429.clear()
        }
    }

    /** Son segment isteğinde görülen HTTP kodu (teşhis için). */
    val lastHttpStatus = AtomicLong(0L)

    /**
     * Host'a uygun aralığı bekler. Çağrı öncesi ve sonrası `mark` çağrılarıyla
     * kilitler; burada yalnızca bekleme yapılır.
     */
    private suspend fun waitForHostSlot(host: String, policy: HlsDownloadPolicy) {
        val effective = adaptiveIntervalMs[host]?.coerceAtLeast(policy.minRequestIntervalMs)
            ?: policy.minRequestIntervalMs
        val cooldown = cooldownUntilMs[host] ?: 0L
        val now = System.currentTimeMillis()
        val waitUntilCooldown = cooldown - now
        if (waitUntilCooldown > 0) {
            Log.d(tag, "429 soğuma beklemesi ${waitUntilCooldown}ms — $host")
            delay(waitUntilCooldown)
        }
        val last = lastRequestAtMs[host] ?: 0L
        val elapsed = System.currentTimeMillis() - last
        val wait = effective - elapsed
        if (wait > 0) delay(wait + Random.nextLong(0, 40L))
    }

    private fun markRequest(host: String) {
        lastRequestAtMs[host] = System.currentTimeMillis()
    }

    /** 429 alındı: host'u soğumaya al ve aralığı büyüt. */
    private fun onRateLimited(host: String, policy: HlsDownloadPolicy, waitMs: Long) {
        synchronized(throttleLock) {
            cooldownUntilMs[host] = System.currentTimeMillis() + waitMs
            val current = adaptiveIntervalMs[host] ?: policy.minRequestIntervalMs
            adaptiveIntervalMs[host] =
                (current * policy.throttleMultiplier).toLong().coerceAtMost(policy.maxRequestIntervalMs)
            consecutive429[host] = (consecutive429[host] ?: 0) + 1
            Log.w(
                tag,
                "429 → $host soğuma ${waitMs}ms, aralık ${adaptiveIntervalMs[host]}ms (üst üste ${consecutive429[host]})"
            )
        }
    }

    /** Başarılı istek: aralığı yavaşça toparla (down-shift). */
    private fun onSuccess(host: String, policy: HlsDownloadPolicy) {
        synchronized(throttleLock) {
            consecutive429.remove(host)
            val current = adaptiveIntervalMs[host] ?: return
            if (current <= policy.minRequestIntervalMs) {
                adaptiveIntervalMs.remove(host)
                return
            }
            adaptiveIntervalMs[host] = (current * 0.75).toLong().coerceAtLeast(policy.minRequestIntervalMs)
        }
    }

    /**
     * Bir segmenti (veya playlist'i) indirir ve **bellekte** döndürür.
     *
     * Bellekte tutulmasının iki nedeni var:
     *  1. AES-128 şifreli segmentler yazılmadan önce çözülmek zorunda.
     *  2. Yarım kalan bir yanıt (bağlantı koptu) dosyaya hiç yazılmaz → bozuk
     *     video oluşmaz. Tipik HLS segmenti 1-6 MB, bu güvenli bir bedel.
     */
    suspend fun fetchBytes(
        url: String,
        headers: Map<String, String>,
        byteRange: HlsByteRange? = null,
        policy: HlsDownloadPolicy
    ): ByteArray {
        val host = hostOf(url)
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < policy.maxAttemptsPerSegment) {
            attempt++
            waitForHostSlot(host, policy)
            markRequest(host)

            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) ->
                // Range ve User-Agent bizim kontrolümüzde.
                if (!k.equals("host", true) && !k.equals("content-length", true) && !k.equals("transfer-encoding", true)) {
                    requestBuilder.header(k, v)
                }
            }
            byteRange?.let { requestBuilder.header("Range", "bytes=${it.offset}-${it.offset + it.length - 1}") }

            var response: Response? = null
            try {
                response = client.newCall(requestBuilder.build()).execute()
                lastHttpStatus.set(response.code.toLong())

                if (response.isSuccessful) {
                    val body = response.body ?: throw IOException("Boş yanıt gövdesi")
                    val bytes = body.bytes()
                    if (bytes.isEmpty()) throw IOException("Sıfır baytlık segment")
                    onSuccess(host, policy)
                    return bytes
                }

                val code = response.code
                val retryAfterMs = HlsDownloadPolicy.parseRetryAfterMs(
                    response.header("Retry-After") ?: response.header("retry-after")
                )

                when {
                    // 429 / 503: sunucu "yavaşla" diyor.
                    code == 429 || code == 503 || code == 509 -> {
                        val backoff = (policy.backoffBaseMs * (1L shl (attempt - 1).coerceAtMost(4)))
                            .coerceAtMost(policy.maxSingleWaitMs)
                        val baseWait = retryAfterMs ?: backoff.coerceAtLeast(policy.defaultRetryAfterMs)
                        val jitter = Random.nextLong(0, 400L)
                        val waitMs = (baseWait + jitter).coerceAtMost(policy.maxSingleWaitMs)
                        onRateLimited(host, policy, waitMs)
                        lastError = IOException("HTTP $code (hız sınırı) — ${waitMs}ms bekleniyor")
                        Log.w(tag, "HTTP $code · deneme $attempt/${policy.maxAttemptsPerSegment} · bekleme ${waitMs}ms · $url")

                        // Deneme hakkı bittiyse "bekle ve devam et" bilgisini çağırana taşı.
                        if (attempt >= policy.maxAttemptsPerSegment) {
                            throw RateLimitedException(code, waitMs, url)
                        }
                        // Bekleme zaten cooldown olarak uygulanacak; burada tekrar beklemeyiz.
                        continue
                    }

                    // 5xx: geçici sunucu hatası, kısa üstel backoff.
                    code in 500..599 -> {
                        val waitMs = (500L * (1L shl (attempt - 1).coerceAtMost(3))).coerceAtMost(8_000L)
                        lastError = IOException("HTTP $code")
                        Log.w(tag, "HTTP $code · deneme $attempt · ${waitMs}ms sonra tekrar · $url")
                        delay(waitMs)
                        continue
                    }

                    // 408 / 425: istek zaman aşımı — yeniden dene.
                    code == 408 || code == 425 -> {
                        lastError = IOException("HTTP $code")
                        delay(1_000L * attempt)
                        continue
                    }

                    // 403 / 404 / 410: kalıcı. Yeniden denemek kotayı boşa harcar.
                    code == 403 || code == 404 || code == 410 -> {
                        throw PermanentDownloadException("HTTP $code — kaynak erişilemez: $url")
                    }

                    // 416: byte range sunucuda desteklenmiyor/geçersiz → menzilsiz dene.
                    code == 416 && byteRange != null -> {
                        Log.w(tag, "416 — Range olmadan tekrar denenecek: $url")
                        return fetchBytes(url, headers, byteRange = null, policy = policy)
                    }

                    else -> throw PermanentDownloadException("HTTP $code — $url")
                }
            } catch (e: RateLimitedException) {
                throw e
            } catch (e: PermanentDownloadException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                Log.w(tag, "Ağ hatası (deneme $attempt/${policy.maxAttemptsPerSegment}): ${e.message} — $url")
                if (attempt >= policy.maxAttemptsPerSegment) throw e
                delay((700L * attempt + Random.nextLong(0, 300L)).coerceAtMost(6_000L))
            } finally {
                runCatching { response?.close() }
            }
        }

        throw lastError ?: IOException("Segment indirilemedi: $url")
    }

    /** Playlist metni indirir (aynı hız sınırı kurallarına uyar). */
    suspend fun fetchText(
        url: String,
        headers: Map<String, String>,
        policy: HlsDownloadPolicy
    ): String = String(fetchBytes(url, headers, byteRange = null, policy = policy), Charsets.UTF_8)
}

/**
 * Kalıcı hata (403/404/410 gibi). Yeniden denemek kotayı boşa harcar ve
 * 429 riskini artırır; bu yüzden tekrar denenmez.
 */
class PermanentDownloadException(message: String) : IOException(message)

/**
 * Segment/playlist isteği hız sınırı nedeniyle **tamamen** tükendiğinde fırlatılır.
 * İndirici bunu görünce işi "kalıcı hata" değil, "daha sonra devam et" olarak işaretler.
 */
class RateLimitedException(
    val httpCode: Int,
    val suggestedWaitMs: Long,
    val url: String
) : IOException("HTTP $httpCode — hız sınırı aşıldı (${suggestedWaitMs}ms): $url")
