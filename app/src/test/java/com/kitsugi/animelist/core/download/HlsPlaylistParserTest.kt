package com.kitsugi.animelist.core.download

import org.junit.Assert.*
import org.junit.Test

/**
 * T-IND-3 (2026-11-09): HLS ayrıştırıcısının JVM birim testleri.
 * Kapsam: master varyant seçimi, AES-128 anahtar/IV, BYTERANGE, EXT-X-MAP, göreli adreste
 * mutlaklaştırma, DRM reddi. Hepsi indirme dosyasının BOZUK inmesini engelleyen kurallar.
 */
class HlsPlaylistParserTest {

    private val mediaUrl = "https://srv9.cdnimages1912.cyou/hls/film/mp4-uYHaTijMbIV.mp4/txt/playlist.txt"

    @Test
    fun masterVariantPicksHighestBandwidth() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080
            1080p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720
            720p.m3u8
        """.trimIndent()
        assertTrue(HlsPlaylistParser.isMasterPlaylist(master))
        assertEquals(
            "https://srv9.cdnimages1912.cyou/hls/film/mp4-uYHaTijMbIV.mp4/txt/1080p.m3u8",
            HlsPlaylistParser.pickVariantUrl(master, mediaUrl)
        )
    }

    @Test
    fun mediaPlaylistParsesSegmentsAndInit() {
        val media = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg-0-v1-a1.ts?t=abc
            #EXTINF:6.0,
            seg-1-v1-a1.ts?t=abc
        """.trimIndent()
        val plan = HlsPlaylistParser.parseMediaPlaylist(media, mediaUrl)
        assertNull(plan.unsupportedReason)
        assertEquals(2, plan.segments.size)
        assertEquals(
            "https://srv9.cdnimages1912.cyou/hls/film/mp4-uYHaTijMbIV.mp4/txt/init.mp4",
            plan.initSegmentUrl
        )
        assertEquals(
            "https://srv9.cdnimages1912.cyou/hls/film/mp4-uYHaTijMbIV.mp4/txt/seg-0-v1-a1.ts?t=abc",
            plan.segments[0].url
        )
        assertNull(plan.segments[0].keyUri)
        assertFalse(plan.segments[0].hasRange)
    }

    @Test
    fun aes128KeyAndSequenceIv() {
        val media = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:7
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin?token=1",IV=0x0000000000000000000000000000000F
            #EXTINF:4.0,
            a.ts
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin?token=1"
            #EXTINF:4.0,
            b.ts
        """.trimIndent()
        val plan = HlsPlaylistParser.parseMediaPlaylist(media, mediaUrl)
        assertEquals(2, plan.segments.size)
        assertEquals("key.bin?token=1", plan.segments[0].keyUri)
        assertEquals(7, plan.segments[0].seq)
        val iv0 = HlsPlaylistParser.ivBytesFor(plan.segments[0])
        assertEquals(15, iv0[15].toInt())
        // IV verilmeyen segment: media sequence (8) big-endian
        assertEquals(8, plan.segments[1].seq)
        val iv1 = HlsPlaylistParser.ivBytesFor(plan.segments[1])
        assertEquals(8, iv1[15].toInt())
        assertEquals(0, iv1[0].toInt())
    }

    @Test
    fun sampleAesIsRejectedCleanly() {
        val media = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://x",KEYFORMAT="com.apple.streamingkeydelivery"
            #EXTINF:4.0,
            a.ts
        """.trimIndent()
        val plan = HlsPlaylistParser.parseMediaPlaylist(media, mediaUrl)
        assertNotNull(plan.unsupportedReason)
        assertTrue(plan.unsupportedReason!!.contains("DRM"))
    }

    @Test
    fun byterangeProducesRangesNotDuplicateFullDownloads() {
        val media = """
            #EXTM3U
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:1000@0
            movie.mp4
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:1000@1000
            movie.mp4
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:500
            movie.mp4
        """.trimIndent()
        val plan = HlsPlaylistParser.parseMediaPlaylist(media, mediaUrl)
        assertEquals(3, plan.segments.size)
        assertEquals(0L, plan.segments[0].rangeStart)
        assertEquals(1000L, plan.segments[0].rangeLen)
        assertEquals(1000L, plan.segments[1].rangeStart)
        // offset verilmeyen üçüncü aralık öncekinin sonundan sürer
        assertEquals(2000L, plan.segments[2].rangeStart)
        assertEquals(500L, plan.segments[2].rangeLen)
        assertTrue(plan.segments.all { it.hasRange })
    }

    @Test
    fun keyNoneClearsEncryption() {
        val media = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="k.bin"
            #EXTINF:4.0,
            a.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4.0,
            b.ts
        """.trimIndent()
        val plan = HlsPlaylistParser.parseMediaPlaylist(media, mediaUrl)
        assertNotNull(plan.segments[0].keyUri)
        assertNull(plan.segments[1].keyUri)
    }

    @Test
    fun resolveUrlHandlesRelativeAndAbsolute() {
        assertEquals(
            "https://h/x/y/seg.ts",
            HlsPlaylistParser.resolveUrl("https://h/x/y/pl.m3u8", "seg.ts")
        )
        assertEquals(
            "https://h/x/seg.ts",
            HlsPlaylistParser.resolveUrl("https://h/x/y/pl.m3u8", "../seg.ts")
        )
        assertEquals(
            "https://b/seg.ts",
            HlsPlaylistParser.resolveUrl("https://h/x/y/pl.m3u8", "https://b/seg.ts")
        )
    }
}
