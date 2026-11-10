package com.kitsugi.animelist.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.4.258 regresyon testi: FilmMakinesi / HDFilmCehennemi (CloseLoad, RapidVid, rapidrame vb.)
 * üzerindeki DÜZ MP4 dosyaları HLS olarak işaretlenmemeli; aksi halde ExoPlayer
 * application/x-mpegURL MIME ile MP4'ü çözemez ve oynatma başarısız olur.
 */
class PlayerHlsDetectionTest {

    @Test
    fun progressiveMp4_onKnownHosts_isNotHls() {
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://srv.closeload.top/hash/video.mp4?t=123"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://rapidrame.com/abc/film.mp4"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://cdn.hdfilmcehennemi.nl/x/movie.mkv"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://filmmakinesi.to/files/film.webm"))
    }

    @Test
    fun hlsManifests_onKnownHosts_stayHls() {
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://closeload.top/hls/abc123/video.mp4/master.txt"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://rapidrame.com/x/index.m3u8?token=1"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://cdn.example.com/stream/master.m3u8"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://vidmixi.com/list/abc"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://rapidrame.com/x/playlist.txt"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://sistenn.uns.bio/v4/abc123?k=token"))
        // YouTube's signed HLS endpoint has no .m3u8 suffix; it must still use HlsMediaSource.
        assertTrue(
            PlayerMediaSourceFactory.isHlsUrl(
                "https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/123/id/abc"
            )
        )
        assertTrue(
            PlayerMediaSourceFactory.isHlsUrl(
                "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/123/id/abc"
            )
        )
        assertFalse(
            PlayerMediaSourceFactory.isHlsUrl("https://manifest.googlevideo.com/api/manifest/dash/id/abc")
        )
    }

    @Test
    fun progressiveWithQuery_isNotHls() {
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://cdn.example.com/video.mp4?sig=abc"))
    }

    /**
     * v2.4.304 regresyon testi: '/hls/' gibi ZAYIF yol belirteçleri progressive uzantı
     * kontrolünden SONRA koşmalı. closeload/rapidrame CDN'lerinin '/hls/.../1080p.mp4'
     * biçimindeki düz dosyaları aksi halde HLS MIME'ı ile ExoPlayer'a verilip çözülemiyordu.
     */
    @Test
    fun progressiveMp4_underHlsPath_isNotHls() {
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://cdn.playmix.uno/hls/04c0b9/1080p.mp4"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://srv.closeload.top/hls/x/film.mp4?token=abc"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://n1.rapidrame.com/hls/a/b/720p.mkv"))
    }

    @Test
    fun extensionlessManifests_onHlsPaths_stayHls() {
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://closeload.top/hls/abc123/"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://playmix.uno/hls/abc/master.txt"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://rapidrame.com/playlist/x/"))
    }

    @Test
    fun explicitCloudStreamTypeHintClassifiesExtensionlessAdaptiveUrls() {
        val url = "https://cdn.example.com/manifest?id=1"
        assertTrue(PlayerMediaSourceFactory.isHlsUrl(url, "M3U8"))
        assertFalse(PlayerMediaSourceFactory.isDashUrl(url, "M3U8"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl(url, "DASH"))
        assertTrue(PlayerMediaSourceFactory.isDashUrl(url, "DASH"))
    }

    @Test
    fun mpdPathOutranksWeakPlayerPathWhenPluginEmitsGenericVideoHint() {
        // Anizm v2 / TrAnimeIzle v27 can identify an MPD URL but emit VIDEO, not DASH.
        val url = "https://cdn.example.com/player/manifest.mpd?token=abc"
        assertFalse(PlayerMediaSourceFactory.isHlsUrl(url, "VIDEO"))
        assertTrue(PlayerMediaSourceFactory.isDashUrl(url, "VIDEO"))
    }

    @Test
    fun blankUrl_isNotHls() {
        assertFalse(PlayerMediaSourceFactory.isHlsUrl(""))
    }

    /**
     * 2026-10-18 tanı raporundan GERÇEK adresler (HDFilmCehennemi 54 / FilmMakinesi 64).
     * Eklenti `INFER_TYPE!!` NPE'si nedeniyle bu adresleri hiç bildiremiyordu; eklenti
     * düzeltmesinden sonra oynatıcının bunları doğru MIME ile alması gerekiyor.
     */
    @Test
    fun realTurkishCdnStreams_areClassifiedCorrectly() {
        // HDFilmCehennemi → rapidrame (token'lı master playlist)
        assertTrue(
            PlayerMediaSourceFactory.isHlsUrl(
                "https://s427.rapidrame.com/hls2/01/00026/mf1zpcyt94wa_,l,n,.urlset/master.m3u8" +
                    "?t=Ttu573_nmiYJ&s=1785632745&e=14400&f=133662&i=0.0&sp=0&n=lozcj87"
            )
        )
        // FilmMakinesi → CloseLoad / playmix master.txt
        assertTrue(
            PlayerMediaSourceFactory.isHlsUrl("https://playmix.uno/hls/abc123def456/master.txt?s=1&d=")
        )
        // master.txt sonrası seçilen gerçek varyant
        assertTrue(
            PlayerMediaSourceFactory.isHlsUrl("https://box-1093-w.vmeas.cloud/hls/x.urlset/index-v1-a1.m3u8")
        )
        // Uzantısız ama bilinen HLS CDN'i → progressive sayılıp Range ile parçalanmamalı
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://rapidrame.com/e/lozcj87hjrh6fuof"))

        // Yerel indirilen dosyalar (başlıkta hdfilmcehennemi / filmmakinesi / stream geçse bile!) ASLA HLS sayılmamalı
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("file:///storage/emulated/0/Download/Kitsugi/Video/HDFilmCehennemi_Film.ts"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("file:///storage/emulated/0/Download/Kitsugi/Video/FilmMakinesi_Bölüm_1.mp4"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("content://com.android.providers.downloads/stream/999"))
    }
}
