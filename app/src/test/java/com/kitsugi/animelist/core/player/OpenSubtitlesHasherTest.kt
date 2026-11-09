package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenSubtitlesHasherTest {

    @Test
    fun testComputeWithInvalidUrlScheme() {
        val result = OpenSubtitlesHasher.compute("ftp://example.com/video.mp4")
        assertNull(result)
    }

    @Test
    fun testComputeWithEmptyUrl() {
        val result = OpenSubtitlesHasher.compute("")
        assertNull(result)
    }

    @Test
    fun testHashResultDataClass() {
        val result = OpenSubtitlesHasher.HashResult("abc123xyz", 987654321L)
        assertEquals("abc123xyz", result.hash)
        assertEquals(987654321L, result.size)
    }

    @Test
    fun hashOnlyAcceptsTrueByteRanges() {
        com.kitsugi.animelist.testing.LocalHttpTestServer(listOf(
            com.kitsugi.animelist.testing.LocalHttpTestServer.Response(headers = mapOf("Content-Length" to "131072")),
            com.kitsugi.animelist.testing.LocalHttpTestServer.Response(206, mapOf("Content-Range" to "bytes 0-65535/131072"), ByteArray(65536)),
            com.kitsugi.animelist.testing.LocalHttpTestServer.Response(206, mapOf("Content-Range" to "bytes 65536-131071/131072"), ByteArray(65536))
        )).use { server ->
            val result = OpenSubtitlesHasher.compute(server.url + "/video")
            assertEquals(OpenSubtitlesHasher.HashResult("0000000000020000", 131072), result)
            assertEquals("HEAD", server.takeRequest().method)
            assertEquals("bytes=0-65535", server.takeRequest().headers["range"])
            assertEquals("bytes=65536-131071", server.takeRequest().headers["range"])
        }
    }

    @Test
    fun serverIgnoringRangeIsRejectedWithoutReadingVideo() {
        com.kitsugi.animelist.testing.LocalHttpTestServer(listOf(
            com.kitsugi.animelist.testing.LocalHttpTestServer.Response(headers = mapOf("Content-Length" to "999999999")),
            com.kitsugi.animelist.testing.LocalHttpTestServer.Response(body = "full video".toByteArray())
        )).use { server ->
            assertNull(OpenSubtitlesHasher.compute(server.url + "/video"))
            assertEquals("HEAD", server.takeRequest().method)
            assertEquals("GET", server.takeRequest().method)
        }
    }

    @Test fun hlsAndDashCannotBeHashed() {
        assertNull(OpenSubtitlesHasher.compute("https://example.com/playlist.m3u8?token=x"))
        assertNull(OpenSubtitlesHasher.compute("https://example.com/video.mpd"))
    }
}
