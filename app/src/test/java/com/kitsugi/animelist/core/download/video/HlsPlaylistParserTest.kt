package com.kitsugi.animelist.core.download.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HLS liste çözümleyicisi.
 *
 * Buradaki her senaryo sahada kırılmış bir indirmeye karşılık geliyor:
 *  - `#EXT-X-MAP` okunmazsa fMP4 parçaları oynatılamayan bir dosya üretiyor.
 *  - Göreli segment adresleri çözülmezse istek 404'e düşüyor.
 *  - `#EXT-X-BYTERANGE` yok sayılırsa tek segmentli dev dosyalarda yanlış baytlar yazılıyor.
 */
class HlsPlaylistParserTest {

    @Test
    fun masterPlaylistIsDetectedAndBestVariantPicked() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3500000,RESOLUTION=1920x1080
            1080/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=3840x2160
            2160/index.m3u8
        """.trimIndent()

        assertTrue(HlsPlaylistParser.isMasterPlaylist(master))
        val variants = HlsPlaylistParser.parseMaster(master, "https://cdn.example.com/hls/master.m3u8")
        assertEquals(3, variants.size)
        assertEquals("https://cdn.example.com/hls/360/index.m3u8", variants[0].url)
        assertEquals(360, variants[0].height)

        val best = HlsPlaylistParser.pickBestVariant(variants)
        // 4K varyantı cihaz için riskli; 1080p seçilmeli.
        assertEquals("https://cdn.example.com/hls/1080/index.m3u8", best?.url)
    }

    @Test
    fun variantOnlyAbove1080FallsBackToSmallest() {
        val best = HlsPlaylistParser.pickBestVariant(
            listOf(
                HlsVariant("https://a/4k.m3u8", bandwidth = 9000000, height = 2160),
                HlsVariant("https://a/1440.m3u8", bandwidth = 5000000, height = 1440)
            )
        )
        assertEquals("https://a/1440.m3u8", best?.url)
        assertNull(HlsPlaylistParser.pickBestVariant(emptyList()))
    }

    @Test
    fun mediaPlaylistResolvesSegmentsAndInitSegment() {
        val media = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:6
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg-1.m4s
            #EXTINF:6.0,
            https://other.example.com/seg-2.m4s
        """.trimIndent()

        assertFalse(HlsPlaylistParser.isMasterPlaylist(media))
        val playlist = HlsPlaylistParser.parseMedia(media, "https://cdn.example.com/hls/index.m3u8")

        assertTrue(playlist.isFragmentedMp4)
        assertEquals("https://cdn.example.com/hls/init.mp4", playlist.initSegmentUrl)
        assertEquals(2, playlist.segments.size)
        assertEquals("https://cdn.example.com/hls/seg-1.m4s", playlist.segments[0].url)
        assertEquals("https://other.example.com/seg-2.m4s", playlist.segments[1].url)
        assertEquals(0, playlist.segments[0].sequence)
        assertEquals(1, playlist.segments[1].sequence)
    }

    @Test
    fun byteRangesAccumulateWhenOffsetIsOmitted() {
        val media = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:1000@720
            media.mp4
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:500
            media.mp4
        """.trimIndent()

        val playlist = HlsPlaylistParser.parseMedia(media, "https://cdn.example.com/hls/index.m3u8")
        assertEquals(0L until 720L, playlist.initSegmentRange)
        assertEquals(2, playlist.segments.size)
        assertEquals(720L until 1720L, playlist.segments[0].byteRange)
        // OFFSET verilmemişse önceki aralığın bittiği yerden devam eder.
        assertEquals(1720L until 2220L, playlist.segments[1].byteRange)
    }

    @Test
    fun aesKeyIsCarriedToFollowingSegmentsOnly() {
        val media = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x0123456789ABCDEF0123456789ABCDEF
            #EXTINF:6.0,
            enc-1.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:6.0,
            plain-2.ts
        """.trimIndent()

        val playlist = HlsPlaylistParser.parseMedia(media, "https://cdn.example.com/hls/index.m3u8")
        assertEquals("AES-128", playlist.segments[0].key?.method)
        assertEquals("https://cdn.example.com/hls/key.bin", playlist.segments[0].key?.uri)
        assertEquals("0x0123456789ABCDEF0123456789ABCDEF", playlist.segments[0].key?.ivHex)
        assertNull(playlist.segments[1].key)
    }

    @Test
    fun attributeParserHandlesQuotedCommas() {
        val attrs = HlsPlaylistParser.parseAttributes(
            """BANDWIDTH=1280000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=1280x720"""
        )
        assertEquals("1280000", attrs["BANDWIDTH"])
        assertEquals("avc1.4d401f,mp4a.40.2", attrs["CODECS"])
        assertEquals("1280x720", attrs["RESOLUTION"])
    }
}
