package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCodeGenerator
import com.kitsugi.animelist.data.diagnostics.PlatformErrorPlatform
import com.kitsugi.animelist.data.manga.model.SourceErrorCategory
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

class SourceFailureClassifierTest {

    @Test
    fun assignsStableMangaCodesToAllSupportedCategories() {
        val samples = listOf(
            TimeoutException("read timed out") to "KTS-SRC-9001",
            IOException("HTTP 429 Too Many Requests") to "KTS-SRC-9002",
            IOException("Cloudflare CAPTCHA challenge") to "KTS-SRC-9003",
            IOException("HTTP 404 chapter not found") to "KTS-SRC-9004",
            UnknownHostException("Unable to resolve host") to "KTS-SRC-9005",
            IOException("HTTP 403 Forbidden") to "KTS-SRC-9006",
            ParserException("Failed to parse page HTML") to "KTS-SRC-9007",
            IllegalStateException("unexpected provider response") to "KTS-SRC-9008",
        )

        samples.forEach { (error, expectedCode) ->
            assertEquals(expectedCode, SourceFailureClassifier.classifyCode(error))
        }
    }

    @Test
    fun protectionSignatureTakesPrecedenceOverForbiddenHttpStatus() {
        assertEquals(
            SourceErrorCategory.Captcha,
            SourceFailureClassifier.classifyCategory(IOException("HTTP 403 Forbidden by Cloudflare")),
        )
        assertEquals(
            SourceErrorCategory.Auth,
            SourceFailureClassifier.classifyCategory(IOException("HTTP 403 Forbidden")),
        )
    }

    @Test
    fun missingHostInterceptorIsNotAWebsiteCaptcha() {
        val error = IllegalStateException("CloudflareInterceptor must be present in default client")
        assertEquals(SourceErrorCategory.Unknown, SourceFailureClassifier.classifyCategory(error))
        assertEquals("KTS-SRC-9008", SourceFailureClassifier.classifyCode(error))

        val linkage = NoSuchMethodError("eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor")
        assertEquals(SourceErrorCategory.Unknown, SourceFailureClassifier.classifyCategory(linkage))
        assertEquals("KTS-SRC-9008", SourceFailureClassifier.classifyCode(linkage))
    }

    @Test
    fun errorCategoryAlsoDeterminesHealthStatus() {
        assertEquals(SourceHealthStatus.RateLimited, SourceFailureClassifier.classifyStatus(IOException("429 Too Many Requests")))
        assertEquals(
            SourceErrorCategory.Timeout,
            SourceFailureClassifier.classifyCategory(IOException("Zaman aşımı")),
        )
        assertEquals(SourceHealthStatus.CaptchaRequired, SourceFailureClassifier.classifyStatus(IOException("Turnstile verification required")))
        assertEquals(SourceHealthStatus.Degraded, SourceFailureClassifier.classifyStatus(ParserException("parser error")))
    }

    @Test
    fun codeGeneratorKeepsPlatformRangesAndPrefixesStable() {
        assertEquals("KTS-SRC-9001", PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9001))
        assertEquals("KTS-CSP-9140", PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.CloudStream, 9140))
    }

    @Test(expected = IllegalArgumentException::class)
    fun codeGeneratorRejectsValuesOutsidePlatformRange() {
        PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9101)
    }

    @Test
    fun codeCatalogAcceptsStableIdsAndRejectsUnknownIds() {
        assertEquals(PlatformErrorCode.MangaCaptcha, PlatformErrorCode.fromId("kts-src-9003"))
        assertEquals(PlatformErrorCode.MangaCaptcha, PlatformErrorCode.fromId("9003"))
        assertNull(PlatformErrorCode.fromId("KTS-SRC-9999"))
        assertNull(PlatformErrorCode.fromId("KTS-CSP-9003"))
        assertNull(PlatformErrorCode.forMangaCategory(SourceErrorCategory.None))
    }

    private class ParserException(message: String) : IOException(message)
}
