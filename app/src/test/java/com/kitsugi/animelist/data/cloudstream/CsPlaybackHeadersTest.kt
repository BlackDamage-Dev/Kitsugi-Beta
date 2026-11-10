package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.*
import org.junit.Test

class CsPlaybackHeadersTest {
    @Test fun extractorHeadersOverrideCaseInsensitivelyWithoutLosingContext() {
        val headers = CsPlaybackHeaders.merge(
            mapOf("Cookie" to "session=1", "Origin" to "https://embed.example", "user-agent" to "old"),
            mapOf("User-Agent" to "new", "referer" to "https://embed.example/player")
        )
        assertEquals("session=1", CsPlaybackHeaders.value(headers, "cookie"))
        assertEquals("https://embed.example", CsPlaybackHeaders.value(headers, "origin"))
        assertEquals("new", CsPlaybackHeaders.value(headers, "user-agent"))
        assertEquals(1, headers.keys.count { it.equals("user-agent", true) })
    }

    @Test fun explicitRefererIsRetained() {
        val headers = CsPlaybackHeaders.withReferer(mapOf("referer" to "https://a.example/path"), "https://b.example/")
        assertEquals("https://a.example/path", CsPlaybackHeaders.value(headers, "Referer"))
        assertEquals("https://b.example/", CsPlaybackHeaders.value(CsPlaybackHeaders.withReferer(emptyMap(), "https://b.example/"), "referer"))
    }

    @Test fun transientFailuresDoNotMeanGone() {
        assertTrue(CsPlaybackHeaders.isDefinitelyGone(404))
        assertTrue(CsPlaybackHeaders.isDefinitelyGone(410))
        listOf(200, 206, 400, 401, 403, 405, 429, 500).forEach {
            assertFalse(CsPlaybackHeaders.isDefinitelyGone(it))
        }
    }
}
