package com.kitsugi.animelist.data.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformErrorCatalogStructuredHttpTest {

    @Test
    fun kitsuMediaId42919CannotTurnReal422IntoRateLimit() {
        val code = PlatformErrorCatalog.classifyMessage(
            platformLabel = "Kitsu",
            message = "Kitsu kaydı güncellenemedi (HTTP 422)",
            details = "progress cannot exceed length of media; kitsu=42919"
        )
        assertEquals(PlatformErrorCode.COM_1017, code)
    }

    @Test
    fun bangumiStable500IdsCannotBecomeAniListServerErrors() {
        val code = PlatformErrorCatalog.classifyMessage(
            platformLabel = "AniList",
            message = "Kimlik doğrulaması gerektiği için dış hesaplara aktarılmadı",
            details = "MAL ID=500012426; Anime:bangumi:12426"
        )
        assertEquals(PlatformErrorCode.COM_1020, code)
    }

    @Test
    fun simklNotFoundReceiptAndNumericIdsAreNotHttp429() {
        val code = PlatformErrorCatalog.classifyMessage(
            platformLabel = "Simkl",
            message = "130 kayıt Simkl kataloğunda bulunamadı",
            details = "not_found; simkl=42919; tmdb=500123"
        )
        assertEquals(PlatformErrorCode.COM_1020, code)
    }

    @Test
    fun explicitHttp429StillUsesPlatformRateLimitCode() {
        val code = PlatformErrorCatalog.classifyMessage(
            platformLabel = "Simkl",
            message = "Simkl isteği reddetti (429)",
            details = "simkl=12345"
        )
        assertEquals(PlatformErrorCode.SMK_8004, code)
    }

    @Test
    fun explicitAniListServerStatusIsPreserved() {
        val code = PlatformErrorCatalog.classifyText(
            platform = ListPlatform.ANILIST,
            text = "HTTP 503 Service Unavailable"
        )
        assertEquals(PlatformErrorCode.ANL_2003, code)
    }
}
