package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YouTube HLS media playlist ayrıştırıcısı.
 *
 * Bu ayrıştırıcı indirme yolunun tek doğruluk kaynağıdır: init segmenti atlanırsa
 * dosya hiç açılmaz, segment sırası bozulursa video donar. Bu yüzden saf metin
 * üzerinden test edilir.
 */
class YouTubeHlsMediaPlaylistParserTest {

    private val baseUrl = "https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/1/id/abc/master.m3u8?sig=x"

    @Test
    fun parsesInitSegmentAndSegmentsInOrder() {
        val playlist = YouTubeHlsMediaPlaylistParser.parse(
            playlistUrl = baseUrl,
            body = """
                #EXTM3U
                #EXT-X-VERSION:6
                #EXT-X-TARGETDURATION:4
                #EXT-X-MAP:URI="init.mp4?sq=1"
                #EXTINF:4.0,
                seg-0.m4s
                #EXTINF:4.0,
                /absolute/seg-1.m4s
            """.trimIndent()
        )

        val parsed = requireNotNull(playlist)
        assertTrue(parsed.isUsable)
        assertEquals("https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/1/id/abc/init.mp4?sq=1", parsed.initSegmentUrl)
        assertEquals(2, parsed.segmentUrls.size)
        assertEquals("https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/1/id/abc/seg-0.m4s", parsed.segmentUrls[0])
        assertEquals("https://manifest.googlevideo.com/absolute/seg-1.m4s", parsed.segmentUrls[1])
    }

    @Test
    fun transportStreamStreamsAreMarkedAsTsWithoutInitSegment() {
        val playlist = requireNotNull(
            YouTubeHlsMediaPlaylistParser.parse(
                playlistUrl = baseUrl,
                body = """
                    #EXTM3U
                    #EXTINF:10,
                    chunk-1.ts
                """.trimIndent()
            )
        )
        assertTrue(playlist.isTransportStream)
        assertNull(playlist.initSegmentUrl)
    }

    @Test
    fun encryptedPlaylistIsParsedButNotUsable() {
        val playlist = requireNotNull(
            YouTubeHlsMediaPlaylistParser.parse(
                playlistUrl = baseUrl,
                body = """
                    #EXTM3U
                    #EXT-X-KEY:METHOD=AES-128,URI="https://example.com/key"
                    #EXTINF:4.0,
                    seg-0.m4s
                """.trimIndent()
            )
        )
        assertTrue(playlist.isEncrypted)
        // Şifreli segmentleri birleştirmek bozuk dosya üretir; indirme başka akışa düşmeli.
        assertFalse(playlist.isUsable)
    }

    @Test
    fun methodNoneIsNotTreatedAsEncryption() {
        val playlist = requireNotNull(
            YouTubeHlsMediaPlaylistParser.parse(
                playlistUrl = baseUrl,
                body = """
                    #EXTM3U
                    #EXT-X-KEY:METHOD=NONE
                    #EXTINF:4.0,
                    seg-0.m4s
                """.trimIndent()
            )
        )
        assertFalse(playlist.isEncrypted)
        assertTrue(playlist.isUsable)
    }

    @Test
    fun masterPlaylistReturnsEmptySegmentListSoCallerCanDescend() {
        val playlist = requireNotNull(
            YouTubeHlsMediaPlaylistParser.parse(
                playlistUrl = baseUrl,
                body = """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360
                    variant/360.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080
                    variant/1080.m3u8
                """.trimIndent()
            )
        )
        assertTrue(playlist.segmentUrls.isEmpty())
        assertFalse(playlist.isUsable)
    }

    @Test
    fun nonPlaylistBodiesReturnNull() {
        assertNull(YouTubeHlsMediaPlaylistParser.parse(baseUrl, ""))
        assertNull(YouTubeHlsMediaPlaylistParser.parse(baseUrl, "<html>403 Forbidden</html>"))
    }
}
