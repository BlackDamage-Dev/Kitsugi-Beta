package com.kitsugi.animelist.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kaynak bazlı indirme politikası testleri.
 *
 * Bu testler, "HDFilmCehennemi / FilmMakinesi indirmesi yarıda 429 veriyor"
 * şikâyetinin çözümünün temelini doğrular: bu CDN ailesinde istekler arasında
 * **ölçülü bekleme** uygulanmalı, `Retry-After` doğru ayrıştırılmalı ve
 * bilinmeyen host'larda bu kadar agresif davranılmamalı.
 */
class HlsDownloadPolicyTest {

    @Test
    fun `close rapid cdn hostlari ozel politika alir`() {
        val hosts = listOf(
            "https://s427.rapidrame.com/hls/a/b/seg-1.ts",
            "https://closeload.filmmakinesi.to/hls/x/master.txt",
            "https://rplayer.org/stream/x.m3u8",
            "https://playmix.uno/hls/abc/media.m3u8",
            "https://www.hdfilmcehennemi.nl/video/x.ts",
            "https://filmmakinesi.to/v/x.ts"
        )
        hosts.forEach { url ->
            val policy = HlsPolicySelector.forUrl(url)
            assertEquals(
                "Beklenen Close/Rapid politikası: $url",
                HlsDownloadPolicy.CLOSE_RAPID_CDN.minRequestIntervalMs,
                policy.minRequestIntervalMs
            )
        }
    }

    @Test
    fun `close rapid politikasi tarayici hizinin altinda kalir`() {
        // Tarayıcı oynatıcı ~3-5 req/sn yapıyor ve limit yemiyor.
        // 250 ms aralık = 4 req/sn → güvenli bant.
        val interval = HlsDownloadPolicy.CLOSE_RAPID_CDN.minRequestIntervalMs
        assertTrue("Aralık çok küçük: $interval", interval >= 200L)
        assertTrue("Aralık gereğinden büyük: $interval", interval <= 400L)
    }

    @Test
    fun `bilinmeyen host varsayilan politikayi alir`() {
        val policy = HlsPolicySelector.forUrl("https://example.com/x.m3u8")
        assertEquals(HlsDownloadPolicy.DEFAULT, policy)
    }

    @Test
    fun `playlist ve segment farkli hostta ise daha yavasi secilir`() {
        val policy = HlsPolicySelector.forPlaylistAndSegment(
            playlistUrl = "https://fast.example.com/master.m3u8",
            segmentUrl = "https://s427.rapidrame.com/seg.ts"
        )
        assertEquals(
            HlsDownloadPolicy.CLOSE_RAPID_CDN.minRequestIntervalMs,
            policy.minRequestIntervalMs
        )
    }

    @Test
    fun `retry after saniye olarak okunur`() {
        assertEquals(12_000L, HlsDownloadPolicy.parseRetryAfterMs("12"))
        assertEquals(0L, HlsDownloadPolicy.parseRetryAfterMs("0"))
    }

    @Test
    fun `retry after cok buyuk degerler sinirlanir`() {
        // Sunucu 1 saat derse 2 dakikaya kırpılır; indirme asılı kalmasın.
        assertEquals(120_000L, HlsDownloadPolicy.parseRetryAfterMs("3600"))
    }

    @Test
    fun `retry after gecersizse null doner`() {
        assertEquals(null, HlsDownloadPolicy.parseRetryAfterMs(null))
        assertEquals(null, HlsDownloadPolicy.parseRetryAfterMs(""))
        assertEquals(null, HlsDownloadPolicy.parseRetryAfterMs("-5"))
        assertEquals(null, HlsDownloadPolicy.parseRetryAfterMs("yakinda"))
    }

    @Test
    fun `retry after http tarihi desteklenir`() {
        val now = System.currentTimeMillis()
        val date = "Wed, 21 Oct 2015 07:28:00 GMT"
        // Geçmiş tarih → 0 (negatif bekleme olmaz).
        val parsed = HlsDownloadPolicy.parseRetryAfterMs(date, now)
        assertEquals(0L, parsed)
    }

    @Test
    fun `her politikada deneme ve geri cekilme makul`() {
        listOf(
            HlsDownloadPolicy.DEFAULT,
            HlsDownloadPolicy.CLOSE_RAPID_CDN,
            HlsDownloadPolicy.CLOUDFLARE_CDN
        ).forEach { p ->
            assertTrue("Deneme sayısı çok düşük", p.maxAttemptsPerSegment >= 4)
            assertTrue("Deneme sayısı çok yüksek (kaynağı döver)", p.maxAttemptsPerSegment <= 10)
            assertTrue("Geri çekilme tabanı çok düşük", p.backoffBaseMs >= 500L)
            assertTrue("Tek bekleme üst sınırı kaynağı bekletir", p.maxSingleWaitMs <= 60_000L)
            assertTrue("Hız düşürme tavanı tabandan küçük olamaz", p.maxRequestIntervalMs >= p.minRequestIntervalMs)
        }
    }
}
