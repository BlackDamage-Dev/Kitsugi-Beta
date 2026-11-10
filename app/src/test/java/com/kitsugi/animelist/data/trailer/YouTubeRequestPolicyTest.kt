package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeRequestPolicyTest {
    @Test
    fun recognizesYouTubeAndGoogleVideoHostsWithoutMatchingLookalikes() {
        assertTrue(YouTubeRequestPolicy.isYouTubeMediaUrl("https://www.youtube.com/watch?v=abcdefghijk"))
        assertTrue(YouTubeRequestPolicy.isYouTubeMediaUrl("https://manifest.googlevideo.com/api/manifest/hls_playlist/x"))
        assertTrue(YouTubeRequestPolicy.isYouTubeMediaUrl("https://rr2---sn-a5mekn7e.googlevideo.com/videoplayback"))
        assertTrue(YouTubeRequestPolicy.isYouTubeMediaUrl("https://youtu.be/abcdefghijk"))
        assertFalse(YouTubeRequestPolicy.isYouTubeMediaUrl("https://googlevideo.com.attacker.example/video"))
        assertFalse(YouTubeRequestPolicy.isYouTubeMediaUrl("https://v.animethemes.moe/AttackOnTitan-OP1.webm"))
    }

    @Test
    fun hlsManifestNeverGetsQueryRangeChunkingButProgressiveGoogleVideoDoes() {
        val manifest = "https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/1/id/abc"
        val progressive = "https://rr2---sn-a5mekn7e.googlevideo.com/videoplayback?expire=1"

        assertFalse(
            YouTubeRequestPolicy.shouldUseChunkedGoogleVideoRanges(
                videoUrl = manifest,
                audioUrl = null,
                isCloudstream = false,
                isHls = true
            )
        )
        assertTrue(
            YouTubeRequestPolicy.shouldUseChunkedGoogleVideoRanges(
                videoUrl = progressive,
                audioUrl = null,
                isCloudstream = false,
                isHls = false
            )
        )
        assertFalse(
            YouTubeRequestPolicy.shouldUseChunkedGoogleVideoRanges(
                videoUrl = progressive,
                audioUrl = null,
                isCloudstream = true,
                isHls = false
            )
        )
    }

    @Test
    fun youtubeHeadersAreConsistentAndThemeFilesGetTheirOwnOrigin() {
        val youtube = YouTubeRequestPolicy.headersForInlineUrl(
            "https://rr2---sn-a5mekn7e.googlevideo.com/videoplayback"
        )
        assertEquals(YouTubeRequestPolicy.STREAM_USER_AGENT, youtube["User-Agent"])
        assertEquals(YouTubeRequestPolicy.YOUTUBE_REFERER, youtube["Referer"])
        assertEquals("https://www.youtube.com", youtube["Origin"])

        val theme = YouTubeRequestPolicy.headersForInlineUrl(
            "https://v.animethemes.moe/AttackOnTitan-OP1.webm"
        )
        assertEquals("https://v.animethemes.moe/", theme["Referer"])
        org.junit.Assert.assertNull(theme["Origin"])
        assertEquals("video/*,application/octet-stream,*/*;q=0.8", theme["Accept"])
        assertEquals(YouTubeRequestPolicy.STREAM_USER_AGENT, theme["User-Agent"])
    }

    @Test
    fun sourceSpecificClientUserAgentSurvivesHeaderMerging() {
        val source = TrailerPlaybackSource(
            videoUrl = "https://rr2---sn-a5mekn7e.googlevideo.com/videoplayback",
            requestHeaders = mapOf(
                "user-agent" to "VisionOS/0.1",
                "X-Source-Client" to "visionos",
                "X-Blank" to " "
            )
        )

        val headers = YouTubeRequestPolicy.headersForPlaybackSource(source)

        assertEquals("VisionOS/0.1", headers["User-Agent"])
        assertEquals("visionos", headers["X-Source-Client"])
        assertFalse(headers.keys.any { it.equals("user-agent", ignoreCase = true) && it != "User-Agent" })
        assertFalse(headers.containsKey("X-Blank"))
        assertEquals(YouTubeRequestPolicy.YOUTUBE_REFERER, headers["Referer"])
    }

    @Test
    fun signedExpirySupportsQueryAndHlsPathForms() {
        assertEquals(
            1_900_000_000L,
            YouTubeRequestPolicy.expiryEpochSeconds(
                "https://rr1---sn-x.googlevideo.com/videoplayback?id=x&expire=1900000000"
            )
        )
        assertEquals(
            1_900_000_001L,
            YouTubeRequestPolicy.expiryEpochSeconds(
                "https://manifest.googlevideo.com/api/manifest/hls/expire/1900000001/id/x"
            )
        )
        org.junit.Assert.assertNull(YouTubeRequestPolicy.expiryEpochSeconds("https://cdn.example/video/1900000000"))
    }
}

