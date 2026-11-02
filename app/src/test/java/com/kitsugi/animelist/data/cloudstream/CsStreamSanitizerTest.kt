package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.data.repository.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-18 kök neden regresyon testleri — "HDFilmCehennemi / FilmMakinesi oynatmıyor".
 *
 * Eklentilerin `substringAfter("https")` kısayolu bozuk adres üretiyordu
 * (`httpshttp://…`). Böyle bir adres oynatıcıya verildiğinde ExoPlayer şemayı
 * çözemiyor ve anında hata veriyordu: kullanıcı "kaynak görünüyor ama oynatmıyor"
 * diyordu. Bu filtre bozuk adresi oynatıcıya hiç ulaştırmaz.
 *
 * Ayrıca HDFilmCehennemi'nin çöp-adres filtresi `contains(".ts")` kullandığı için
 * `master.txt` gibi GEÇERLİ adresler de eleniyordu (".ts" alt dizesi "master.txt"
 * içinde geçer). [CsStreamSanitizer.isBrokenStreamUrl] kasıtlı olarak dar tutuldu:
 * yalnızca kanıtı açık olan adresleri eler.
 */
class CsStreamSanitizerTest {

    private fun source(url: String?, title: String = "Kaynak") = StreamSource(
        addonName = "HDFilmCehennemi",
        name = "HDFilmCehennemi • $title",
        title = title,
        url = url,
        infoHash = null,
        fileIndex = null,
        isCS = true
    )

    // ─── isBrokenStreamUrl ───────────────────────────────────────────────────

    @Test
    fun doubleSchemeUrls_areBroken() {
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl("httpshttp://playmix.uno/hls/a/master.txt"))
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl("httpshttps://cdn.example/x.m3u8"))
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl("httphttps://cdn.example/x.mp4"))
        // Çift şema adresin ortasında da olsa bozuktur.
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl("https://proxy.example/?u=httpshttp://a/b.m3u8"))
    }

    @Test
    fun schemelessOrBlankUrls_areBroken() {
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl(null))
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl(""))
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl("   "))
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl("//cdn.example/x.m3u8"))
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl("cdn.example/x.m3u8"))
    }

    @Test
    fun hostlessUrls_areBroken() {
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl("https://"))
        assertTrue(CsStreamSanitizer.isBrokenStreamUrl("https:///hls/a/master.m3u8"))
    }

    @Test
    fun realTurkishCdnUrls_areNotBroken() {
        // HDFilmCehennemi — rapidrame (tanı raporundan gerçek örnek)
        assertFalse(
            CsStreamSanitizer.isBrokenStreamUrl(
                "https://s427.rapidrame.com/hls2/01/00026/mf1zpcyt94wa_,l,n,.urlset/master.m3u8" +
                    "?t=Ttu573_nmiYJ&s=1785632745&e=14400&f=133662&i=0.0&sp=0&n=lozcj87"
            )
        )
        // FilmMakinesi — CloseLoad master.txt
        assertFalse(
            CsStreamSanitizer.isBrokenStreamUrl("https://playmix.uno/hls/abc123/master.txt?s=1&d=")
        )
        assertFalse(
            CsStreamSanitizer.isBrokenStreamUrl("https://closeload.filmmakinesi.to/hls/x/index.m3u8")
        )
    }

    @Test
    fun masterTxtIsNotMistakenForTsFragment() {
        // ".ts" alt dizesi "master.txt" içinde geçer — bu adres GEÇERLİDİR.
        assertFalse(CsStreamSanitizer.isBrokenStreamUrl("https://streambox.xyz/hls/a/master.txt"))
        assertFalse(CsStreamSanitizer.isBrokenStreamUrl("https://x.example/tracks.txt"))
        assertFalse(CsStreamSanitizer.isBrokenStreamUrl("https://x.example/playlist.txt"))
    }

    // ─── sanitize: bozuk adres elenmesi ─────────────────────────────────────

    @Test
    fun sanitize_dropsBrokenUrl_keepsHealthyOne() {
        val broken = source("httpshttp://playmix.uno/hls/a/master.txt", "Bozuk")
        val healthy = source(
            "https://s427.rapidrame.com/hls2/01/00026/x.urlset/master.m3u8?t=abc",
            "Rapidrame"
        )

        val (kept, dropped) = CsStreamSanitizer.sanitize("HDFilmCehennemi", listOf(broken, healthy))

        assertEquals(1, dropped)
        assertEquals(1, kept.size)
        assertEquals(healthy.url, kept[0].url)
    }

    @Test
    fun sanitize_returnsEmpty_whenEveryUrlIsBroken() {
        // Tüm kaynaklar bozuksa orijinal listeye DÖNÜLMEZ: bozuk adres oynatıcıyı
        // yine çökertir. Kullanıcı "kaynak yok" görmeli, "hata" değil.
        val streams = listOf(
            source("httpshttp://a.example/master.txt", "A"),
            source("httpshttps://b.example/x.m3u8", "B"),
            source("//c.example/x.mp4", "C")
        )

        val (kept, dropped) = CsStreamSanitizer.sanitize("FilmMakinesi", streams)

        assertTrue(kept.isEmpty())
        assertEquals(3, dropped)
    }

    @Test
    fun sanitize_keepsTorrentSourcesWithoutUrl() {
        // URL'si olmayan (infoHash/magnet tabanlı) kaynaklar bu filtreye takılmamalı.
        val torrent = StreamSource(
            addonName = "Torrentio",
            name = "Torrentio • 1080p",
            title = "Movie.1080p",
            url = null,
            infoHash = "a9471d2ae83f0f2b4c5d6e7f8091a2b3c4d5e6f7",
            fileIndex = null
        )

        val (kept, _) = CsStreamSanitizer.sanitize("Torrentio", listOf(torrent))

        assertEquals(1, kept.size)
        assertEquals(torrent.infoHash, kept[0].infoHash)
    }

    @Test
    fun sanitize_emptyInput_returnsEmpty() {
        val (kept, dropped) = CsStreamSanitizer.sanitize("HDFilmCehennemi", emptyList())
        assertTrue(kept.isEmpty())
        assertEquals(0, dropped)
    }

    @Test
    fun sanitize_keepsSameUrlWhenPlaybackHeadersOrTypeDiffer() {
        val base = source("https://cdn.example/video.mp4").copy(requestHeaders = mapOf("Referer" to "https://first.example/"))
        val otherReferer = base.copy(requestHeaders = mapOf("referer" to "https://second.example/"))
        val sameHeadersDifferentCase = base.copy(requestHeaders = mapOf("referer" to "https://first.example/"))
        val otherType = base.copy(mediaType = com.kitsugi.animelist.core.player.StreamMediaType.HLS)
        val (kept, dropped) = CsStreamSanitizer.sanitize(
            "Anizm", listOf(base, otherReferer, sameHeadersDifferentCase, otherType)
        )
        assertEquals(listOf(base, otherReferer, otherType), kept)
        assertEquals(1, dropped)
    }
}
