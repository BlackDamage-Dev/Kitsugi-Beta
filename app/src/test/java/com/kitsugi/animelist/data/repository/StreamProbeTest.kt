package com.kitsugi.animelist.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamProbeTest {

    private fun source(url: String, type: String?) = StreamSource(
        addonName = "CloudStream",
        name = "test",
        title = "test",
        url = url,
        infoHash = null,
        fileIndex = null,
        isCS = true,
        streamTypeHint = type
    )

    @Test
    fun `extensionless HLS hint is probeable but DASH remains out of scope`() {
        assertTrue(StreamProbe.isProbeable(source("https://cdn.example/manifest?id=1", "M3U8")))
        assertTrue(StreamProbe.isProbeable(source("https://cdn.example/manifest?id=2", "HLS")))
        assertFalse(StreamProbe.isProbeable(source("https://cdn.example/manifest?id=3", "DASH")))
    }

    @Test
    fun `torrent hint is never sent to HTTP stream probe`() {
        assertFalse(StreamProbe.isProbeable(source("https://cdn.example/file", "TORRENT")))
        assertFalse(StreamProbe.isProbeable(source("https://cdn.example/file", "MAGNET")))
    }

    @Test
    fun `probe cache separates transport type and request headers`() {
        val url = "https://cdn.example.com/manifest?id=1"
        val hls = source(url, "M3U8")
        val dash = source(url, "DASH")
        val authenticated = hls.copy(requestHeaders = mapOf("Authorization" to "Bearer one"))

        assertNotEquals(StreamProbe.cacheKeyFor(hls), StreamProbe.cacheKeyFor(dash))
        assertNotEquals(StreamProbe.cacheKeyFor(hls), StreamProbe.cacheKeyFor(authenticated))
    }
}
