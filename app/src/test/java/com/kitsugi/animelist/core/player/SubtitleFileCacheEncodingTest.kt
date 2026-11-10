package com.kitsugi.animelist.core.player

import android.content.Context
import com.kitsugi.animelist.testing.LocalHttpTestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.security.MessageDigest

class SubtitleFileCacheEncodingTest {
    private val text = "1\n00:00:01,000 --> 00:00:02,000\nTürkçe: ışık, özgür, değişim\n"

    @Test fun downloadedTurkishLegacySubtitleIsStoredAsUtf8() = runBlocking {
        val dir = Files.createTempDirectory("subtitle-cache-test-").toFile()
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(dir)
        try {
            LocalHttpTestServer(listOf(LocalHttpTestServer.Response(
                headers = mapOf("Content-Type" to "application/x-subrip; charset=utf-8"),
                body = text.toByteArray(Charset.forName("windows-1254"))
            ))).use { server ->
                val result = SubtitleFileCache.cacheSubtitle(context, server.url + "/file.srt", language = "tur")
                assertNotNull(result)
                assertEquals(text, result!!.readText(Charsets.UTF_8))
                assertEquals("GET", server.takeRequest().method)
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun existingLegacyCacheIsConvertedInsteadOfReusedRaw() = runBlocking {
        val dir = Files.createTempDirectory("subtitle-cache-test-").toFile()
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(dir)
        val url = "https://example.test/file.srt"
        val hash = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val cache = File(dir, "subtitles").apply { mkdirs() }
        File(cache, "$hash.srt").writeBytes(text.toByteArray(Charset.forName("windows-1254")))
        try {
            val result = SubtitleFileCache.cacheSubtitle(context, url, language = "tr")
            assertNotNull(result)
            assertEquals(text, result!!.readText(Charsets.UTF_8))
        } finally { dir.deleteRecursively() }
    }
}
