package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsStreamHeadersTest {
    @Test
    fun `extractor referer survives embedding and origin follows it`() {
        val headers = CsStreamHeaders.extracted(
            parent = mapOf("Referer" to "https://anizm.example/episode", "Cookie" to "session=a"),
            child = mapOf("user-agent" to "HostUA"),
            extractorReferer = "https://www.yourupload.com/embed/abc",
            fallbackReferer = "https://anizm.example/",
            mediaUrl = "https://vidcache.example/video.mp4",
            userAgent = "DefaultUA"
        )
        assertEquals("https://www.yourupload.com/embed/abc", CsStreamHeaders.value(headers, "referer"))
        assertEquals("https://www.yourupload.com", CsStreamHeaders.value(headers, "Origin"))
        assertEquals("HostUA", CsStreamHeaders.value(headers, "USER-AGENT"))
        assertEquals("session=a", CsStreamHeaders.value(headers, "cookie"))
        assertEquals(1, headers.keys.count { it.equals("referer", true) })
    }

    @Test
    fun `child header overrides extractor referer and explicitly supplied origin`() {
        val headers = CsStreamHeaders.extracted(
            parent = mapOf("origin" to "https://origin.example", "referer" to "https://old.example"),
            child = mapOf("REFERER" to "https://new.example/watch", "User-Agent" to "Custom"),
            extractorReferer = "https://extractor.example/",
            fallbackReferer = "https://fallback.example/",
            mediaUrl = "https://cdn.example/file",
            userAgent = "Default"
        )
        assertEquals("https://new.example/watch", CsStreamHeaders.value(headers, "referer"))
        assertEquals("https://origin.example", CsStreamHeaders.value(headers, "Origin"))
        assertEquals(1, headers.keys.count { it.equals("referer", true) })
    }

    @Test
    fun `origin can wait until an embed is resolved`() {
        val pending = CsStreamHeaders.extracted(
            emptyMap(), emptyMap(), null, "https://site.example/", "https://embed.example/e/1", "UA", addOrigin = false
        )
        assertNull(CsStreamHeaders.value(pending, "origin"))
        val final = CsStreamHeaders.extracted(
            pending, emptyMap(), "https://embed.example:8443/e/1", null, "https://cdn.example/video", "UA"
        )
        assertEquals("https://embed.example:8443", CsStreamHeaders.value(final, "origin"))
    }
}
