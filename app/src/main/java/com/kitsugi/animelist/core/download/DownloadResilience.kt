package com.kitsugi.animelist.core.download

import java.io.IOException
import java.net.UnknownHostException
import kotlin.random.Random

/**
 * İndirme hattı dayanıklılık çekirdeği (T-IND-1 — 2026-11-09).
 *
 * NEDEN VAR:
 * HDFilmCehennemi / FilmMakinesi gibi Türk kaynakları videoları **geciçi (rotasyonlu) CDN
 * host'larında** yayınlıyor (srvN.cdnimagesNNNN.cyou, sNNN.rapidrame.com/hls2, closeload.*,
 * rapidrame.*, rplayer.*). Bu host'lar saatler içinde ölüp yenileniyor:
 *  - İndirme kuyruğa eklendiğinde çözülen URL, indirme SERVİSİ çalıştığında (ya da kullanıcı
 *    "devam et" dediğinde) çoktan ölmüş olabiliyor → `UnknownHostException: Unable to resolve
 *    host "srv8.cdnimages936.cyou"` (ekrandaki kırmızı hata).
 *  - CDN'ler Referer/Origin olmadan 404, hızlı segment isteklerine 429 dönüyor. Eski kodda
 *    segment başına indirici retry'ı (3) × [com.kitsugi.animelist.core.network.RetryInterceptor]
 *    retry'ı (2+1) = 9'a kadar istek kısa pencerede gönderilip 429 sarmalı yaratıyordu.
 *
 * Bu dosya SAF JVM Kotlin'dir (Android bağımlılığı yok) → birim testleri app/src/test altında.
 */
object DownloadResilience {

    /** Segment/playlist isteğinin HTTP düzeyi hatası (kod + Retry-After). */
    class SegmentHttpException(
        val code: Int,
        val retryAfterMs: Long?,
        message: String
    ) : IOException(message)

    enum class ErrorKind {
        /** DNS çözülemedi — host ölmüş ya da sağlayıcı DNS'i blokluyor. */
        DNS,
        /** 429 Too Many Requests — hız sınırı. */
        RATE_LIMIT,
        /** 5xx — CDN/sunucu tarafı. */
        SERVER,
        /** 404/410 — URL bayat (rotasyon sonrası yol yok). */
        STALE_URL,
        /** 403 vb. — yetki/başlık sorunu. */
        FORBIDDEN,
        /** Bağlantı/zaman aşımı gibi geçici ağ hataları. */
        NETWORK,
        OTHER
    }

    /**
     * Bir indirme hatasını sınıflandırır. Zincirdeki (cause) ilk anlamlı hataya bakar:
     * OkHttp hataları [java.net.UnknownHostException]'ı cause olarak sarar.
     */
    fun classify(error: Throwable?): ErrorKind {
        var cur: Throwable? = error
        var depth = 0
        while (cur != null && depth < 8) {
            when {
                cur is UnknownHostException -> return ErrorKind.DNS
                cur is SegmentHttpException -> return when (cur.code) {
                    429 -> ErrorKind.RATE_LIMIT
                    404, 410 -> ErrorKind.STALE_URL
                    403, 401 -> ErrorKind.FORBIDDEN
                    in 500..599 -> ErrorKind.SERVER
                    else -> ErrorKind.OTHER
                }
            }
            val msg = cur.message?.lowercase()
            if (msg != null) {
                if (msg.contains("unable to resolve host") || msg.contains("no address associated")) {
                    return ErrorKind.DNS
                }
            }
            cur = cur.cause
            depth++
        }
        val top = error
        return when {
            top is IOException -> ErrorKind.NETWORK
            top is java.net.SocketTimeoutException -> ErrorKind.NETWORK
            else -> ErrorKind.OTHER
        }
    }

    /**
     * Bu hata türünde ADRES YENİDEN ÇÖZÜMÜ (embed sayfasından taze link çıkarma) denemeye değer mi?
     * DNS ve bayat-URL hatalarında yeni bir embed çözümü başka (canlı) bir CDN host'u verebilir.
     * Hız sınırında da tek seferlik yeniden çözüm faydalı olabilir (aynı anda başka host döner).
     */
    fun worthReResolving(kind: ErrorKind): Boolean =
        kind == ErrorKind.DNS || kind == ErrorKind.STALE_URL || kind == ErrorKind.RATE_LIMIT

    /**
     * Kaynak/CDN ailesine göre indirme politikası.
     *
     * @param minSegmentIntervalMs iki segment isteği başlangıcı arasına konan ASGARİ süre
     *   (patlama → 429 engelimi). 0 = sınırsız.
     * @param segmentAttempts      segment başına toplam deneme.
     * @param playlistAttempts       playlist indirme başına toplam deneme.
     * @param backoffBaseMs / backoffCapMs üstel geri çekilme tabanı/tavanı.
     * @param retryAfterCapMs        sunucunun Retry-After değerine uyulacak azami süre.
     * @param allowReResolve         hata durumunda embed adresinden taze link çözümü izinli mi.
     * @param maxReResolve           tek indirmede azami yeniden çözüm sayısı.
     */
    data class SourceDownloadPolicy(
        val name: String,
        val minSegmentIntervalMs: Long,
        val segmentAttempts: Int,
        val playlistAttempts: Int,
        val backoffBaseMs: Long,
        val backoffCapMs: Long,
        val retryAfterCapMs: Long,
        val allowReResolve: Boolean,
        val maxReResolve: Int
    )

    /** Türk CDN ailesi: yavaş-tempo + uzun backoff + yeniden çözüm. */
    val POLICY_TR_CDN = SourceDownloadPolicy(
        name = "TR_CDN",
        minSegmentIntervalMs = 250L,
        segmentAttempts = 6,
        playlistAttempts = 4,
        backoffBaseMs = 1_500L,
        backoffCapMs = 30_000L,
        retryAfterCapMs = 45_000L,
        allowReResolve = true,
        maxReResolve = 2
    )

    /** Genel kaynaklar: mevcut davranışa yakın, ama backoff'lu. */
    val POLICY_DEFAULT = SourceDownloadPolicy(
        name = "DEFAULT",
        minSegmentIntervalMs = 0L,
        segmentAttempts = 4,
        playlistAttempts = 3,
        backoffBaseMs = 1_000L,
        backoffCapMs = 15_000L,
        retryAfterCapMs = 20_000L,
        allowReResolve = true,
        maxReResolve = 1
    )

    /** Close/Rapid (JWPlayer "dc_") CDN ailesinin host imzaları. */
    private val TR_CDN_HOST_MARKS = listOf(
        "cdnimages", "rapidrame", "rplayer", "closeload", "playmix",
        "hdfilmcehennemi", "filmmakinesi", "filmakinesi", "hdfilm"
    )

    /** Eklenti adı imzaları (CloudStream addon adları). */
    private val TR_CDN_SOURCE_MARKS = listOf(
        "hdfilm", "filmmakinesi", "film makinesi", "close", "rapid"
    )

    fun policyFor(sourceName: String?, url: String?): SourceDownloadPolicy {
        val src = sourceName?.lowercase() ?: ""
        val host = url?.lowercase() ?: ""
        val bySource = TR_CDN_SOURCE_MARKS.any { src.contains(it) }
        val byHost = TR_CDN_HOST_MARKS.any { host.contains(it) }
        return if (bySource || byHost) POLICY_TR_CDN else POLICY_DEFAULT
    }

    /**
     * Üstel geri çekilme + jitter: attempt 1 → base, 2 → 2×base, 4×base … cap'e kadar.
     * Jitter ±%20 (aynı anda başlayan çoklu indirmelerin senkronize retry patlamasını kırar).
     */
    fun backoffMs(attempt: Int, policy: SourceDownloadPolicy, random: Random = Random): Long {
        val shift = (attempt - 1).coerceIn(0, 20)
        val raw = policy.backoffBaseMs shl shift
        val capped = minOf(raw, policy.backoffCapMs)
        val jitter = (capped * 0.2).toLong()
        val delta = if (jitter > 0) random.nextLong(-jitter, jitter + 1) else 0L
        return (capped + delta).coerceIn(0, policy.backoffCapMs)
    }

    /** `Retry-After` başlığını ms'ye çevirir (saniye ya da HTTP-tarih biçimi). */
    fun parseRetryAfterMs(headerValue: String?): Long? {
        val v = headerValue?.trim() ?: return null
        v.toLongOrNull()?.let { secs ->
            if (secs in 0..3600) return secs * 1000L
        }
        return try {
            val date = java.time.ZonedDateTime.parse(
                v,
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
            )
            val delta = java.time.Duration.between(java.time.Instant.now(), date.toInstant()).toMillis()
            if (delta in 0..3_600_000) delta else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Segment URL'sinin HOST'tan bağımsız kimliği (yol + anlamlı sorgu yok).
     * Rotasyon aynı yolun başka host'ta yaşaması demek olduğundan resume eşlemesi
     * host yok sayılarak yapılır.
     */
    fun segmentPathKey(url: String): String = try {
        val uri = java.net.URI(url)
        uri.rawPath ?: url
    } catch (e: Exception) {
        url.substringBefore('?')
    }

    /**
     * Adres yeniden çözümü sonrası YENİ playlist ile eski ilerlemeyi uzlaştırır.
     * Dönüş: devam edilecek segment indisi; null → indirme BAŞTAN başlar.
     * Kural: segment yol dizileri birebir aynıysa (host farkı önemsiz) kaldığımız indis güvenlidir;
     * değilse baytlar farklı video dilimlerine denk geleceği için baştan başlanır.
     */
    fun reconcileResumeIndex(
        oldSegmentUrls: List<String>,
        newSegmentUrls: List<String>,
        currentIndex: Int
    ): Int {
        if (currentIndex <= 0) return 0
        if (oldSegmentUrls.size != newSegmentUrls.size) return 0
        for (i in oldSegmentUrls.indices) {
            if (segmentPathKey(oldSegmentUrls[i]) != segmentPathKey(newSegmentUrls[i])) return 0
        }
        return currentIndex.coerceAtMost(newSegmentUrls.size)
    }
}
