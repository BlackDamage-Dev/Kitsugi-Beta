package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HLS master playlist → gerçek kalite varyantları.
 *
 * Bu testler, rapidrame / closeload gibi tek `master.m3u8` veren kaynaklarda
 * (HDFilmCehennemi, FilmMakinesi) oynatıcının "Kalite" sayfasının **tahmin
 * yapmadan** gerçek varyantları gösterebilmesini doğrular.
 */
class HlsMasterPlaylistTest {

    private val rapidrameMaster = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-STREAM-INF:BANDWIDTH=5300000,AVERAGE-BANDWIDTH=4800000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
        index-v1.m3u8?t=token123
        #EXT-X-STREAM-INF:BANDWIDTH=2600000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
        index-v2.m3u8?t=token123
        #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=854x480,CODECS="avc1.42c01e,mp4a.40.2"
        index-v3.m3u8?t=token123
    """.trimIndent()

    private val masterUrl = "https://s430.rapidrame.com/hls2/01/00037/rsyi6h14btcs/master.m3u8?t=token123"

    @Test
    fun `parses every variant sorted from highest to lowest`() {
        val variants = HlsMasterPlaylist.parse(masterUrl, rapidrameMaster)

        assertEquals(3, variants.size)
        assertEquals(listOf(1080, 720, 480), variants.map { it.height })
        assertEquals(listOf("1080p", "720p", "480p"), variants.map { it.label })
        assertEquals(5300000L, variants.first().bandwidth)
        assertTrue(variants.first().url.startsWith("https://s430.rapidrame.com/hls2/01/00037/rsyi6h14btcs/"))
        assertEquals("https://s430.rapidrame.com/hls2/01/00037/rsyi6h14btcs/index-v1.m3u8?t=token123", variants.first().url)
    }

    @Test
    fun `media playlist without variants yields empty list`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            segment1.ts
            #EXTINF:6.0,
            segment2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertTrue(HlsMasterPlaylist.parse(masterUrl, media).isEmpty())
    }

    @Test
    fun `relative variant without query inherits master token`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=2600000,RESOLUTION=1280x720
            720/index.m3u8
        """.trimIndent()

        val variants = HlsMasterPlaylist.parse(masterUrl, master)

        assertEquals(1, variants.size)
        assertEquals(
            "https://s430.rapidrame.com/hls2/01/00037/rsyi6h14btcs/720/index.m3u8?t=token123",
            variants.single().url
        )
    }

    @Test
    fun `absolute variant urls are kept as written`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=2600000,RESOLUTION=1280x720
            https://cdn.example.com/hls/720/index.m3u8?sig=abc
        """.trimIndent()

        val variants = HlsMasterPlaylist.parse(masterUrl, master)

        assertEquals("https://cdn.example.com/hls/720/index.m3u8?sig=abc", variants.single().url)
    }

    @Test
    fun `audio only renditions are not offered as video quality`() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Türkçe",LANGUAGE="tr",URI="audio/tr.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="aud"
            video/720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2",AUDIO="aud"
            audio/only.m3u8
        """.trimIndent()

        val variants = HlsMasterPlaylist.parse(masterUrl, master)

        assertEquals(1, variants.size)
        assertEquals(720, variants.single().height)
    }

    @Test
    fun `same resolution keeps the highest bandwidth variant`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720
            low/720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3200000,RESOLUTION=1280x720
            high/720.m3u8
        """.trimIndent()

        val variants = HlsMasterPlaylist.parse(masterUrl, master)

        assertEquals(1, variants.size)
        assertEquals(3200000L, variants.single().bandwidth)
        assertEquals("https://s430.rapidrame.com/hls2/01/00037/rsyi6h14btcs/high/720.m3u8?t=token123", variants.single().url)
    }

    @Test
    fun `at the same height AVC wins over HEVC`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=6200000,RESOLUTION=1920x1080,CODECS="hvc1.2.4.L120.B0,mp4a.40.2"
            hevc/1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4800000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            avc/1080.m3u8
        """.trimIndent()

        val variants = HlsMasterPlaylist.parse(masterUrl, master)

        assertEquals(1, variants.size)
        // Yüksek bit hızlı HEVC değil, cihazlarda her zaman çözülebilen AVC seçilir.
        assertTrue(variants.single().url.contains("avc/1080.m3u8"))
    }

    @Test
    fun `variant with separate audio rendition must not switch url`() {
        // Ayrı ses grubu (URI'li EXT-X-MEDIA) varsa varyant playlist'ine geçmek SESİ kaybeder.
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Türkçe",LANGUAGE="tr",URI="audio/tr.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",URI="audio/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=4800000,RESOLUTION=1920x1080,CODECS="avc1.640028",AUDIO="aud"
            video/1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2600000,RESOLUTION=1280x720,CODECS="avc1.4d401f",AUDIO="aud"
            video/720.m3u8
        """.trimIndent()

        val variants = HlsMasterPlaylist.parse(masterUrl, master)

        assertEquals(2, variants.size)
        assertTrue(variants.all { it.requiresMasterAudio })
        assertTrue(variants.none { it.canSwitchUrl })
        // Seçenek URL değiştirmemeli → motor çözünürlük tavanı kullanılır (ses korunur).
        assertTrue(variants.map { QualityOption.fromHls(it) }.all { it.switchUrl == null })
    }

    @Test
    fun `variant without audio group can switch url`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=2600000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            video/720.m3u8
        """.trimIndent()

        val variant = HlsMasterPlaylist.parse(masterUrl, master).single()

        assertTrue(variant.canSwitchUrl)
        assertEquals(
            "https://s430.rapidrame.com/hls2/01/00037/rsyi6h14btcs/video/720.m3u8?t=token123",
            QualityOption.fromHls(variant).switchUrl
        )
    }

    @Test
    fun `height is recovered from variant name when resolution is missing`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,NAME="1080p"
            1080/index.m3u8
        """.trimIndent()

        val variants = HlsMasterPlaylist.parse(masterUrl, master)

        assertEquals("1080p", variants.single().label)
        assertEquals(1080, variants.single().height)
    }

    @Test
    fun `bandwidth formatting is human readable`() {
        assertEquals("4.8 Mbps", HlsVariant.formatBandwidth(4_800_000L).replace(',', '.'))
        assertEquals("850 kbps", HlsVariant.formatBandwidth(850_000L))
    }

    @Test
    fun `resolveUri rejects unusable targets`() {
        assertNull(HlsMasterPlaylist.resolveUri(masterUrl, "   "))
        assertNull(HlsMasterPlaylist.resolveUri(masterUrl, "mailto:x@y.z"))
    }
}
