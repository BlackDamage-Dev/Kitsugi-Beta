package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamFormatPolicyTest {
    @Test
    fun `extractor hints survive extensionless urls`() {
        assertEquals(StreamMediaType.HLS, StreamFormatPolicy.resolve("https://cdn.example/get?id=1", StreamMediaType.HLS))
        assertEquals(StreamMediaType.DASH, StreamFormatPolicy.resolve("https://cdn.example/stream?id=1", StreamMediaType.DASH))
        assertEquals(StreamMediaType.PROGRESSIVE, StreamFormatPolicy.resolve("https://cdn.example/stream/id", StreamMediaType.PROGRESSIVE))
    }

    @Test
    fun `definite manifests beat inferred video hint`() {
        assertEquals(StreamMediaType.HLS, StreamFormatPolicy.resolve("https://anizmplayer.com/cdn/x/master.m3u8?md5=ABC", StreamMediaType.PROGRESSIVE))
        assertEquals(StreamMediaType.HLS, StreamFormatPolicy.resolve("https://cdn.example/hls/video.mp4/master.txt", StreamMediaType.PROGRESSIVE))
        assertEquals(StreamMediaType.DASH, StreamFormatPolicy.resolve("https://cdn.example/a/manifest.mpd?token=x", StreamMediaType.PROGRESSIVE))
    }

    @Test
    fun `progressive suffix wins weak hls host markers`() {
        assertEquals(StreamMediaType.PROGRESSIVE, StreamFormatPolicy.resolve("https://rapidrame.com/hls/a/720p.mp4?x=y"))
        assertEquals(StreamMediaType.HLS, StreamFormatPolicy.resolve("https://rapidrame.com/playlist/a"))
        assertNull(StreamFormatPolicy.resolve("https://cdn.example/get?id=123"))
    }
}
