package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubePlaybackSourceCacheTest {
    private val nowMs = 1_800_000_000_000L

    @Test
    fun cachedSourceIsReusedBeforeItsTtl() {
        val cache = YouTubePlaybackSourceCache(ttlMs = 60_000L)
        val source = source("https://cdn/video")

        cache.put("abcdefghijk", source, nowMs)

        assertEquals(source, cache.get("abcdefghijk", nowMs + 59_999L))
        assertNull(cache.get("abcdefghijk", nowMs + 60_000L))
    }

    @Test
    fun earliestVideoOrAudioSignatureExpiryWins() {
        val cache = YouTubePlaybackSourceCache(ttlMs = 60 * 60 * 1000L)
        val videoExpiry = nowMs / 1000L + 600L
        val audioExpiry = nowMs / 1000L + 120L
        val source = source(
            "https://cdn/video?expire=$videoExpiry",
            "https://cdn/audio?expire=$audioExpiry"
        )

        cache.put("abcdefghijk", source, nowMs)

        // Audio expires after 120 s and the cache applies a 60 s safety margin.
        assertEquals(source, cache.get("abcdefghijk", nowMs + 59_999L))
        assertNull(cache.get("abcdefghijk", nowMs + 60_000L))
    }

    @Test
    fun hlsPathExpiryIsAlsoHonoured() {
        val cache = YouTubePlaybackSourceCache(ttlMs = 60 * 60 * 1000L)
        val expiry = nowMs / 1000L + 120L
        val source = source("https://manifest.googlevideo.com/api/manifest/hls/expire/$expiry/id/x")

        cache.put("abcdefghijk", source, nowMs)

        assertNull(cache.get("abcdefghijk", nowMs + 60_000L))
    }

    @Test
    fun cacheIsLruAndCanInvalidateSingleVideo() {
        val cache = YouTubePlaybackSourceCache(maxEntries = 2, ttlMs = 60_000L)
        val first = source("https://cdn/1")
        cache.put("first", first, nowMs)
        cache.put("second", source("https://cdn/2"), nowMs)
        assertEquals(first, cache.get("first", nowMs))

        cache.put("third", source("https://cdn/3"), nowMs)

        assertNull(cache.get("second", nowMs))
        assertEquals(2, cache.size())
        cache.remove("first")
        assertNull(cache.get("first", nowMs))
        assertEquals(1, cache.size())
    }

    private fun source(video: String, audio: String? = null) = TrailerPlaybackSource(
        videoUrl = video,
        audioUrl = audio,
        alternatives = listOf(TrailerPlaybackSource(videoUrl = "$video-alt"))
    )
}
