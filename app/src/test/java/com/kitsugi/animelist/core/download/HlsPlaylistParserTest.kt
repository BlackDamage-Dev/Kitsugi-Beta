package com.kitsugi.animelist.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HLS playlist ayrıştırıcısının, indirme motorunun dayandığı davranışları
 * doğruladığı testler.
 *
 * Kapsam bilinçli olarak **saf** (ağ/Android bağımlılığı olmayan) katmana
 * odaklanmıştır; 429 davranışı [HlsDownloadPolicy] testleriyle doğrulanır.
 */
class HlsPlaylistParserTest {

    /**
     * Ayrıştırıcı `android.util.Log`'u doğrudan çağırmaz; [HlsLog] köprüsü üzerinden
     * yazar. Testte bu köprü sessize alınır ki saf JVM ortamında gürültü/uyarı olmasın.
     */
    @org.junit.Before
    fun silenceParserLogs() {
        HlsLog.sink = { }
    }

    // ── Master playlist ──────────────────────────────────────────────────

    @Test
    fun `master playlist en yuksek bant genisligini secer`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            1080/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=854x480
            480/index.m3u8
        """.trimIndent()

        assertTrue(HlsPlaylistParser.isMasterPlaylist(master))
        val parsed = HlsPlaylistParser.parseMaster(master, "https://cdn.example.com/v/master.m3u8")
        assertNotNull(parsed)
        assertEquals(3, parsed!!.variants.size)

        val best = HlsPlaylistParser.selectVariant(parsed)
        assertEquals("https://cdn.example.com/v/1080/index.m3u8", best?.url)
        assertEquals(1080, best?.height)
    }

    @Test
    fun `ses only varyantlar video seciminde atlanir`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,CODECS="mp4a.40.2"
            audio/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            video720/index.m3u8
        """.trimIndent()

        val parsed = HlsPlaylistParser.parseMaster(master, "https://cdn.example.com/master.m3u8")!!
        val best = HlsPlaylistParser.selectVariant(parsed)
        assertEquals("https://cdn.example.com/video720/index.m3u8", best?.url)
    }

    @Test
    fun `master olmayan medya playlisti master sayilmaz`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            seg0.ts
        """.trimIndent()
        assertFalse(HlsPlaylistParser.isMasterPlaylist(media))
        assertNull(HlsPlaylistParser.parseMaster(media, "https://cdn.example.com/i.m3u8"))
    }

    @Test
    fun `tirnak icindeki virgul attribute ayrimini bozmaz`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1000,CODECS="avc1.4d401f,mp4a.40.2"
            v.m3u8
        """.trimIndent()
        val parsed = HlsPlaylistParser.parseMaster(master, "https://cdn.example.com/master.m3u8")!!
        assertEquals("avc1.4d401f,mp4a.40.2", parsed.variants.first().codecs)
    }

    // ── Medya playlist ───────────────────────────────────────────────────

    @Test
    fun `goreli segment adresleri playlist adresine gore cozulur`() {
        val media = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            seg-0.ts
            #EXTINF:6.0,
            ../other/seg-1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = HlsPlaylistParser.parseMedia(
            media,
            "https://cdn.example.com/hls/abc/media.m3u8?v=1"
        )
        assertEquals(2, parsed.segments.size)
        assertEquals("https://cdn.example.com/hls/abc/seg-0.ts", parsed.segments[0].url)
        assertEquals("https://cdn.example.com/hls/other/seg-1.ts", parsed.segments[1].url)
        assertTrue(parsed.ended)
    }

    @Test
    fun `endlist yoksa akis bitmemis sayilir`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            seg-0.ts
        """.trimIndent()
        val parsed = HlsPlaylistParser.parseMedia(media, "https://cdn.example.com/i.m3u8")
        assertFalse(parsed.ended)
    }

    @Test
    fun `medya sequence ve target duration okunur`() {
        val media = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:1234
            #EXT-X-TARGETDURATION:8
            #EXTINF:8.0,
            a.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val parsed = HlsPlaylistParser.parseMedia(media, "https://cdn.example.com/i.m3u8")
        assertEquals(1234L, parsed.segments.first().sequence)
        assertEquals(8, parsed.targetDurationSeconds)
    }

    // ── AES-128 ──────────────────────────────────────────────────────────

    @Test
    fun `aes 128 anahtari ve iv cozulur`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x0000000000000000000000000000002A
            #EXTINF:6.0,
            seg0.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val parsed = HlsPlaylistParser.parseMedia(media, "https://cdn.example.com/hls/media.m3u8")
        val key = parsed.segments.first().key
        assertNotNull(key)
        assertTrue(key!!.isEncrypted)
        assertEquals("https://cdn.example.com/hls/key.bin", key.uri)
        assertEquals(16, key.iv!!.size)
        assertEquals(0x2A.toByte(), key.iv!![15])
        assertTrue(parsed.encrypted)
    }

    @Test
    fun `method none sifreli sayilmaz`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:6.0,
            seg0.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val parsed = HlsPlaylistParser.parseMedia(media, "https://cdn.example.com/i.m3u8")
        assertNull(parsed.segments.first().key)
        assertFalse(parsed.encrypted)
    }

    @Test
    fun `gecersiz iv null doner`() {
        assertNull(HlsPlaylistParser.parseIv("0xZZ"))
    }

    // ── Byte range ───────────────────────────────────────────────────────

    @Test
    fun `byte range agirlikli playlistte offset zincirlenir`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:1000@0
            all.ts
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:2000
            all.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val parsed = HlsPlaylistParser.parseMedia(media, "https://cdn.example.com/i.m3u8")
        assertEquals(1000L, parsed.segments[0].byteRange?.length)
        assertEquals(0L, parsed.segments[0].byteRange?.offset)
        // Offset belirtilmemişse önceki segmentin bitişini izler.
        assertEquals(2000L, parsed.segments[1].byteRange?.length)
        assertEquals(1000L, parsed.segments[1].byteRange?.offset)
    }

    // ── fMP4 init segment ────────────────────────────────────────────────

    @Test
    fun `ext x map init segmenti cozulur`() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg0.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val parsed = HlsPlaylistParser.parseMedia(media, "https://cdn.example.com/hls/media.m3u8")
        assertEquals("https://cdn.example.com/hls/init.mp4", parsed.initSegment?.url)
    }

    // ── Hata durumları ───────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `segment icermeyen playlist hata verir`() {
        HlsPlaylistParser.parseMedia(
            "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-ENDLIST",
            "https://cdn.example.com/i.m3u8"
        )
    }

    @Test
    fun `klasorden bagimsiz adres mutlak kalir`() {
        assertEquals(
            "https://other.example.com/x.ts",
            HlsPlaylistParser.resolveUrl("https://cdn.example.com/a/b.m3u8", "https://other.example.com/x.ts")
        )
    }
}
