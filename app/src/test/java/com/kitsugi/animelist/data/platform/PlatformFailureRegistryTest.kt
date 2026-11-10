package com.kitsugi.animelist.data.platform

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PlatformFailureRegistryTest {

    @Before
    fun setUp() {
        PlatformFailureRegistry.clear()
    }

    @Test
    fun mapsSourceHostsToListPlatforms() {
        assertEquals(ListPlatform.SIMKL, ListPlatform.fromHost("api.simkl.com"))
        assertEquals(ListPlatform.BANGUMI, ListPlatform.fromHost("api.bgm.tv"))
        assertEquals(ListPlatform.TMDB, ListPlatform.fromHost("api.themoviedb.org"))
        assertEquals(ListPlatform.SHIKIMORI, ListPlatform.fromHost("shikimori.one"))
        assertEquals(ListPlatform.MAL, ListPlatform.fromHost("api.jikan.moe"))
        assertEquals(ListPlatform.ANILIST, ListPlatform.fromHost("graphql.anilist.co"))
        assertEquals(ListPlatform.KITSU, ListPlatform.fromHost("kitsu.io"))
        assertEquals(ListPlatform.GENERIC, ListPlatform.fromHost("example.com"))
        assertEquals(ListPlatform.GENERIC, ListPlatform.fromHost(null))
    }

    @Test
    fun hostMatchingRequiresDomainBoundary() {
        // "notsimkl.com" simkl alan adı değildir; yalnızca tam alan adı veya alt alan adı eşleşir.
        assertEquals(ListPlatform.GENERIC, ListPlatform.fromHost("notsimkl.com"))
        assertEquals(ListPlatform.SIMKL, ListPlatform.fromHost("API.SIMKL.COM."))
    }

    @Test
    fun httpFailureIsClassifiedWithPlatformSpecificCode() {
        val rateLimited = PlatformFailureRegistry.reportHttp("api.simkl.com", 429, "/sync/activities")
        assertEquals(PlatformErrorCode.SMK_8004, rateLimited)

        val unauthorized = PlatformFailureRegistry.reportHttp("api.simkl.com", 401, "/sync/activities")
        assertEquals(PlatformErrorCode.SMK_8002, unauthorized)
    }

    @Test
    fun simklWriteLockHttp400MapsToRateLimitWithoutStoringResponseBody() {
        val code = PlatformFailureRegistry.reportHttp(
            host = "api.simkl.com",
            httpCode = 400,
            path = "/sync/history",
            diagnosticHint = "rate_limit",
        )

        assertEquals(PlatformErrorCode.SMK_8004, code)
        val reason = PlatformFailureRegistry.latest("simkl")?.rawReason.orEmpty()
        assertTrue(reason.contains("rate_limit"))
        assertFalse(reason.contains("access_token"))
    }

    @Test
    fun unknownHostIsNotRecorded() {
        assertNull(PlatformFailureRegistry.reportHttp("example.com", 500, "/x"))
        assertNull(PlatformFailureRegistry.latest("example.com"))
    }

    @Test
    fun latestReturnsRecentFailureAndExpiresAfterWindow() {
        PlatformFailureRegistry.reportHttp("api.themoviedb.org", 401, "/3/tv/1")
        val now = System.currentTimeMillis()

        val fresh = PlatformFailureRegistry.latest("tmdb", now = now)
        assertEquals(PlatformErrorCode.TMD_7001, fresh?.code)

        val expired = PlatformFailureRegistry.latest(
            "tmdb",
            now = now + PlatformFailureRegistry.DEFAULT_MAX_AGE_MS + 1_000L,
        )
        assertNull(expired)
    }

    @Test
    fun rawReasonNeverContainsQueryString() {
        // Sorgu dizesi API anahtarı taşıyabilir; yalnızca yol kaydedilmelidir.
        PlatformFailureRegistry.reportHttp("api.themoviedb.org", 401, "/3/tv/1")
        val reason = PlatformFailureRegistry.latest("tmdb")?.rawReason.orEmpty()
        assertFalse(reason.contains("api_key"))
        assertFalse(reason.contains("?"))
    }

    @Test
    fun cancellationIsNotRecorded() {
        val code = PlatformFailureRegistry.report("simkl", error = CancellationException("cancelled"))
        assertNull(code)
        assertNull(PlatformFailureRegistry.latest("simkl"))
    }

    @Test
    fun exceptionIsClassifiedAndRecorded() {
        val code = PlatformFailureRegistry.report("simkl", error = IOException("Connection reset by peer"))
        assertEquals(PlatformErrorCode.COM_1005, code)
        assertEquals(PlatformErrorCode.COM_1005, PlatformFailureRegistry.latest("simkl")?.code)
    }

    @Test
    fun anilistServiceDownIsClassifiedAsAnl2008EvenOnHttp403OrGenericPlatform() {
        val downEx = com.kitsugi.animelist.data.remote.AniListServiceDownException(
            "AniList API geçici olarak devre dışı: The AniList API has been temporarily disabled due to severe stability issues."
        )
        assertEquals(
            PlatformErrorCode.ANL_2008,
            PlatformErrorCatalog.classify(
                platform = ListPlatform.ANILIST,
                error = downEx,
                httpCode = 403,
            )
        )
        assertEquals(
            PlatformErrorCode.ANL_2008,
            PlatformErrorCatalog.classify(error = downEx)
        )
    }

    @Test
    fun emptyOrUnclassifiedFollowUpReportDoesNotOverwriteFreshConcreteFailure() {
        PlatformFailureRegistry.report(
            platformLabel = "anilist",
            httpCode = 403,
            rawMessage = "AniList API geçici olarak devre dışı: The AniList API has been temporarily disabled due to severe stability issues.",
        )
        assertEquals(PlatformErrorCode.ANL_2008, PlatformFailureRegistry.latest("AniList")?.code)

        // Üst katman boş hata (null) veya belirsiz sarmalayıcı bildirse bile ANL_2008 korunur.
        assertEquals(PlatformErrorCode.ANL_2008, PlatformFailureRegistry.reportAndResolve("AniList", null))
        assertEquals(
            PlatformErrorCode.ANL_2008,
            PlatformFailureRegistry.reportAndResolve("AniList", RuntimeException("AniList bildirim isteği yanıtsız kaldı"))
        )
    }

    @Test
    fun extractAniListServiceDownMessageParsesGraphQlOutagePayload() {
        val json403 = """{"data":null,"errors":[{"message":"The AniList API has been temporarily disabled due to severe stability issues.","status":403}]}"""
        assertEquals(
            "The AniList API has been temporarily disabled due to severe stability issues.",
            com.kitsugi.animelist.data.remote.KitsugiApiBase.extractAniListServiceDownMessage(json403)
        )
        assertNull(
            com.kitsugi.animelist.data.remote.KitsugiApiBase.extractAniListServiceDownMessage(
                """{"errors":[{"message":"Invalid token","status":401}]}"""
            )
        )
    }
}
