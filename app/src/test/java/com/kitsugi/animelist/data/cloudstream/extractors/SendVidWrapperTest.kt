package com.kitsugi.animelist.data.cloudstream.extractors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendVidWrapperTest {

    @Test
    fun identifiesOnlySendVidHosts() {
        assertTrue(SendVidWrapper.isSendVidUrl("https://sendvid.com/abc123"))
        assertTrue(SendVidWrapper.isSendVidUrl("https://media.sendvid.net/embed/abc123"))
        assertFalse(SendVidWrapper.isSendVidUrl("https://sendvid.com.example/abc123"))
        assertFalse(SendVidWrapper.isSendVidUrl("https://example.com/sendvid/abc123"))
    }

    @Test
    fun normalizesDirectLookingPageToEmbedUrl() {
        assertEquals(
            "https://sendvid.com/embed/abc123",
            SendVidWrapper.pageUrl("https://sendvid.com/abc123.mp4?download=1")
        )
        assertEquals(
            "https://sendvid.com/embed/abc123?token=x",
            SendVidWrapper.pageUrl("https://sendvid.com/embed/abc123?token=x")
        )
    }

    @Test
    fun extractsVideoSourceAndCarriesHotlinkHeaders() {
        val html = """
            <html><script>var video_source = "https://cdn.example/episode.mp4?sig=1";</script></html>
        """.trimIndent()

        val result = SendVidWrapper.extractFromHtml(html, "https://sendvid.com/embed/abc123")

        assertEquals(1, result.size)
        assertEquals("https://cdn.example/episode.mp4?sig=1", result[0].url)
        assertEquals("MP4", result[0].label)
        assertEquals("https://sendvid.com/embed/abc123", result[0].headers["Referer"])
        assertEquals("https://sendvid.com", result[0].headers["Origin"])
        assertTrue(result[0].headers["User-Agent"].orEmpty().isNotBlank())
    }

    @Test
    fun extractsOpenGraphAndHtml5SourcesWithoutDuplicateCards() {
        val html = """
            <meta content="https://cdn.example/episode.m3u8" property="og:video:secure_url">
            <video><source type="video/mp4" src="https://cdn.example/episode.mp4"></video>
            <script>var duplicate = "https://cdn.example/episode.mp4";</script>
        """.trimIndent()

        val result = SendVidWrapper.extractFromHtml(html, "https://sendvid.com/embed/abc123")

        assertEquals(2, result.size)
        assertTrue(result.any { it.isM3u8 && it.url.endsWith("episode.m3u8") })
        assertTrue(result.any { !it.isM3u8 && it.url.endsWith("episode.mp4") })
    }

    @Test
    fun acceptsMediaExtensionInQueryString() {
        val html = "<script>var video_source = \"https://cdn.example/get?file=episode.m3u8&sig=1\";</script>"

        val result = SendVidWrapper.extractFromHtml(html, "https://sendvid.com/embed/abc123")

        assertEquals(1, result.size)
        assertTrue(result[0].isM3u8)
    }

    @Test
    fun doesNotReturnProviderUnavailablePageAsMedia() {
        val html = "<div>This video is temporarily unavailable</div>"

        assertTrue(SendVidWrapper.isTemporarilyUnavailable(html))
        assertTrue(SendVidWrapper.extractFromHtml(html, "https://sendvid.com/embed/abc123").isEmpty())
    }
}
