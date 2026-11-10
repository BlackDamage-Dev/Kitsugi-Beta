package com.kitsugi.animelist.core.download

import java.util.Locale

/**
 * İndirme sırasında "kaynağa özel" davranış kuralları.
 *
 * ## Neden böyle bir şey var?
 *
 * HDFilmCehennemi ve FilmMakinesi gibi Türkçe video siteleri içeriklerini
 * `closeload.*` / `rapidrame.*` / `rplayer.*` / `playmix.uno` ailesindeki
 * CDN'ler üzerinden sunuyor. Bu CDN'lerin **hız sınırı (rate limit)** davranışı
 * standart değil:
 *
 *  - `.ts` segmentlerini **çok hızlı** istemek → `HTTP 429 Too Many Requests`.
 *    Tarayıcıda oynatıcı segmentleri sırayla ve tampon boyu kadar (2-6 saniyelik
 *    parçalar hâlinde) çektiği için limit hiç tetiklenmez; bizim indirici ise
 *    mümkün olduğunca hızlı ardı ardına istek attığı için yarıda 429 yiyor.
 *  - 429 yanıtı çoğu zaman `Retry-After` **içermiyor**; içerse bile değer
 *    saniye değil rastgele olabiliyor.
 *  - Aynı IP'den kısa sürede tekrar tekrar denemek cezayı **uzatıyor**
 *    (üstel geri çekilme olmadan yapılan 3 hızlı deneme, 429'u 403'e çeviriyor).
 *  - Bazı host'lar `Range` başlığı verilince 200 ile tüm dosyayı döndürüyor;
 *    bu durumda yarım kalan dosyaya ekleme yapmak **bozuk video** üretiyor.
 *
 * Bu yüzden indirme motoru artık kaynak bazlı bir politika kullanır: segmentler
 * arasında ölçülü bir bekleme, 429'da `Retry-After`/üstel geri çekilme, ve
 * limitin sürekli tekrar etmesi durumunda **hızı düşürerek** devam etme.
 *
 * Politika tamamen veri odaklıdır; yeni bir CDN eklendiğinde yalnızca buradaki
 * tabloya satır eklenir.
 */
data class HlsDownloadPolicy(
    /** Aynı host'a iki istek arasında en az beklenecek süre (ms). */
    val minRequestIntervalMs: Long,
    /** 429/503 sonrası üstel geri çekilmenin tabanı (ms). */
    val backoffBaseMs: Long,
    /** Tek bir 429 beklemesi için üst sınır (ms). */
    val maxSingleWaitMs: Long,
    /** Segment başına en fazla deneme sayısı. */
    val maxAttemptsPerSegment: Int,
    /**
     * `Retry-After` başlığı yoksa uygulanacak varsayılan bekleme (ms).
     * CDN'ler genelde 1-3 saniyelik pencere kullanıyor.
     */
    val defaultRetryAfterMs: Long,
    /**
     * `Range` isteğine 206 yerine 200 dönen host'larda çoklu bağlantı/parçalı
     * indirme güvenli değildir. Bu bayrak yalnızca teşhis amaçlıdır.
     */
    val supportsByteRange: Boolean,
    /** 429 görüldüğünde hızı düşürme oranı (ör. 2.0 → aralık iki katına çıkar). */
    val throttleMultiplier: Double,
    /** Hız düşürme sonrası izin verilen en yüksek istek aralığı (ms). */
    val maxRequestIntervalMs: Long,
    /** Bu host'larda indirme başlamadan kısa bir "ısınma" beklemesi uygulanır. */
    val warmupDelayMs: Long
) {
    companion object {

        /** Bilinmeyen/iyimser host'lar: sıkı olmayan, güvenli varsayılan. */
        val DEFAULT = HlsDownloadPolicy(
            minRequestIntervalMs = 60L,
            backoffBaseMs = 1_500L,
            maxSingleWaitMs = 30_000L,
            maxAttemptsPerSegment = 6,
            defaultRetryAfterMs = 2_000L,
            supportsByteRange = true,
            throttleMultiplier = 2.0,
            maxRequestIntervalMs = 3_000L,
            warmupDelayMs = 0L
        )

        /**
         * Close/Rapid CDN ailesi (HDFilmCehennemi, FilmMakinesi ve türevleri).
         *
         * Ölçümler: tarayıcı oynatıcı ~4-6 saniyelik segmentler için ortalama
         * 3-5 req/sn yapıyor ve limit yemiyor; 15+ req/sn'de 429 geliyor.
         * 250 ms aralık (~4 req/sn) güvenli bant; 429 görülürse iki katına çıkar.
         */
        val CLOSE_RAPID_CDN = HlsDownloadPolicy(
            minRequestIntervalMs = 250L,
            backoffBaseMs = 2_000L,
            maxSingleWaitMs = 45_000L,
            maxAttemptsPerSegment = 8,
            defaultRetryAfterMs = 3_000L,
            supportsByteRange = true,
            throttleMultiplier = 2.0,
            maxRequestIntervalMs = 2_500L,
            warmupDelayMs = 400L
        )

        /** Genel amaçlı "agresif" CDN'ler (Cloudflare önünde, 429'u sevmeyen). */
        val CLOUDFLARE_CDN = HlsDownloadPolicy(
            minRequestIntervalMs = 120L,
            backoffBaseMs = 1_500L,
            maxSingleWaitMs = 30_000L,
            maxAttemptsPerSegment = 7,
            defaultRetryAfterMs = 2_500L,
            supportsByteRange = true,
            throttleMultiplier = 2.0,
            maxRequestIntervalMs = 3_000L,
            warmupDelayMs = 200L
        )

        /** Host parçaları → politika. Sıra önemli: ilk eşleşen kazanır. */
        internal val HOST_RULES: List<Pair<List<String>, HlsDownloadPolicy>> = listOf(
            listOf(
                "closeload", "rapidrame", "rplayer", "playmix",
                "hdfilmcehennemi", "filmmakinesi", "filmakinesi",
                "siteminder", "turkish123", "dizibox", "dizilla",
                "asyawatch", "sezonlukdizi", "hdfilmdelisi"
            ) to CLOSE_RAPID_CDN,
            listOf(
                "cloudflare", "workers.dev", "r2.dev", "b-cdn.net",
                "bunnycdn", "akamaized", "fastly", "cloudfront"
            ) to CLOUDFLARE_CDN
        )

        /**
         * Sunucunun kendi bildirdiği `Retry-After` değerini ms'e çevirir.
         * Hem saniye (ör. `12`) hem de HTTP tarihi biçimini destekler.
         */
        fun parseRetryAfterMs(raw: String?, nowMs: Long = System.currentTimeMillis()): Long? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            value.toLongOrNull()?.let { seconds ->
                if (seconds < 0) return null
                return (seconds * 1_000L).coerceAtMost(120_000L)
            }
            // HTTP-date: "Wed, 21 Oct 2015 07:28:00 GMT"
            return runCatching {
                val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                fmt.timeZone = java.util.TimeZone.getTimeZone("GMT")
                val target = fmt.parse(value)?.time ?: return null
                (target - nowMs).coerceIn(0L, 120_000L)
            }.getOrNull()
        }
    }
}

/** URL'ye bakıp uygun politikayı seçer. */
object HlsPolicySelector {

    fun forUrl(url: String): HlsDownloadPolicy {
        // Saf JVM (java.net.URI) kullanılıyor: bu sınıf birim testlerinde Android
        // çalışma zamanı olmadan da çalışabilmeli.
        val host = runCatching { java.net.URI(url).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
        if (host.isNotBlank()) {
            for ((needles, policy) in HlsDownloadPolicy.HOST_RULES) {
                if (needles.any { host.contains(it) }) return policy
            }
        }
        return HlsDownloadPolicy.DEFAULT
    }

    /** Segment ve playlist farklı host'larda olabilir; ikisinin de en yavaşını al. */
    fun forPlaylistAndSegment(playlistUrl: String, segmentUrl: String): HlsDownloadPolicy {
        val a = forUrl(playlistUrl)
        val b = forUrl(segmentUrl)
        return if (b.minRequestIntervalMs > a.minRequestIntervalMs) b else a
    }
}
