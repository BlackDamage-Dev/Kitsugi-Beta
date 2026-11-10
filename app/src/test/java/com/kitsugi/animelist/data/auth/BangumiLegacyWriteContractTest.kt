package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bangumi **resmî Legacy PHP API** yazma sözleşmeleri (E87) + oturum süresi doldu anahtarları (E95).
 *
 * Kök neden: `bangumi/server` (Go `/v0`) yazma uçları `mw.NeedScope(ScopeWriteCollection)`
 * ile korunur; `parseTokenScope` yalnızca JSON **nesnesi** çözer (`TestParseTokenScope_NonObject`),
 * dizi yazan OAuth oturumları `403 insufficient token scope` alır. Aynı Bearer token,
 * resmî Legacy uçlarında (`POST /collection/{id}/update`, `POST /subject/{id}/update/watched_eps`)
 * sorunsuz çalışır — bu testler sözleşmeyi sabitler.
 */
class BangumiLegacyWriteContractTest {

    // ── E87: type → Legacy `CollectionStatusType` eşlemesi ──────────────────────

    @Test
    fun collectionTypeMapsToOfficialLegacyStatusStrings() {
        assertEquals("wish", BangumiApiClient.collectionTypeToLegacyStatus(BangumiApiClient.CollectionType.WISH))
        assertEquals("collect", BangumiApiClient.collectionTypeToLegacyStatus(BangumiApiClient.CollectionType.DONE))
        assertEquals("do", BangumiApiClient.collectionTypeToLegacyStatus(BangumiApiClient.CollectionType.DOING))
        assertEquals("on_hold", BangumiApiClient.collectionTypeToLegacyStatus(BangumiApiClient.CollectionType.ON_HOLD))
        assertEquals("dropped", BangumiApiClient.collectionTypeToLegacyStatus(BangumiApiClient.CollectionType.DROPPED))
    }

    // ── E87: Legacy form alanları — gönderilmeyen alan SUNUCUDA SIFIRLANIR ─────

    @Test
    fun legacyFormAlwaysSendsStatusAndPrivacy() {
        val fields = BangumiApiClient.buildLegacyCollectionFormFields(existing = null, type = BangumiApiClient.CollectionType.DOING)
        assertEquals("do", fields["status"])
        assertEquals("0", fields["privacy"])
        // Puan/not yokken `rating`/`comment`/`tags` GÖNDERİLMEZ: OpenAPI sözleşmesi
        // "rating 不填默认重置为未评分" der; var olmayan puanı sıfırlamak veri kaybıdır.
        assertFalse(fields.containsKey("rating"))
        assertFalse(fields.containsKey("comment"))
        assertFalse(fields.containsKey("tags"))
    }

    @Test
    fun legacyFormFillsUntouchedFieldsFromExistingCollection() {
        val existing = BangumiApiClient.BangumiUserCollection(
            subjectId = 10380,
            subjectType = BangumiApiClient.SubjectType.ANIME,
            type = BangumiApiClient.CollectionType.DONE,
            rate = 9,
            comment = "Eluned",
            tags = listOf("SF", "key社"),
            epStatus = 24,
            volStatus = 0,
            private = true,
            updatedAt = null,
            subject = null
        )
        val fields = BangumiApiClient.buildLegacyCollectionFormFields(
            existing = existing,
            type = BangumiApiClient.CollectionType.DONE
        )
        assertEquals("collect", fields["status"])
        assertEquals("9", fields["rating"])
        assertEquals("Eluned", fields["comment"])
        assertEquals("SF key社", fields["tags"])
        assertEquals("1", fields["privacy"])
    }

    @Test
    fun legacyFormKeepsServerLimitsFromSanitizers() {
        val longComment = "x".repeat(500)
        val fields = BangumiApiClient.buildLegacyCollectionFormFields(
            existing = null,
            type = BangumiApiClient.CollectionType.DOING,
            comment = longComment
        )
        // 380 kod noktası sınırı (`MAX_COLLECTION_COMMENT_CHARS`) Legacy yolunda da geçerli.
        assertTrue((fields["comment"]?.length ?: 0) <= BangumiApiClient.MAX_COLLECTION_COMMENT_CHARS)
    }

    // ── E87: Legacy PHP uçları HTTP 200 ile hata gövdesi döndürebilir ──────────

    @Test
    fun legacyWriteErrorDetectsEmbeddedInvalidToken() {
        val body = """{"request":"收藏","code":401,"error":"invalid_token"}"""
        val err = BangumiApiClient.parseLegacyWriteError(httpCode = 200, body = body)
        assertNotNull(err)
        assertEquals(401, err!!.code)
    }

    @Test
    fun legacyWriteErrorPassesThroughRealHttpFailures() {
        val err = BangumiApiClient.parseLegacyWriteError(httpCode = 500, body = "boom")
        assertNotNull(err)
        assertEquals(500, err!!.code)
    }

    @Test
    fun legacyWatchedEpsAccepted202IsSuccessEvenThoughErrorFieldSaysAccepted() {
        val exactReportBody = """{"request":"/subject/267715/update/watched_eps","code":202,"error":"Accepted"}"""
        assertNull(BangumiApiClient.parseLegacyWriteError(httpCode = 200, body = exactReportBody))
    }

    @Test
    fun legacyAcceptedBodyDoesNotMaskRealTransportFailure() {
        val body = """{"code":202,"error":"Accepted"}"""
        val err = BangumiApiClient.parseLegacyWriteError(httpCode = 400, body = body)
        assertNotNull(err)
        assertEquals(400, err!!.code)
    }

    @Test
    fun legacyWriteErrorTreatsSuccessBodyAsNonError() {
        assertNull(BangumiApiClient.parseLegacyWriteError(httpCode = 200, body = ""))
        assertNull(BangumiApiClient.parseLegacyWriteError(httpCode = 200, body = """{"request":"ok"}"""))
    }

    // ── E95: oturum süresi doldu anahtarları ───────────────────────────────────

    @Test
    fun sessionExpiredKeyUsesCanonicalProviderNames() {
        assertEquals("mal_session_expired", ExternalAuthManager.sessionExpiredKey("MyAnimeList"))
        assertEquals("kitsu_session_expired", ExternalAuthManager.sessionExpiredKey("kitsu"))
        assertEquals("shikimori_session_expired", ExternalAuthManager.sessionExpiredKey("shikimori"))
        assertEquals("bangumi_session_expired", ExternalAuthManager.sessionExpiredKey("bgm.tv"))
        assertEquals("anilist_session_expired", ExternalAuthManager.sessionExpiredKey("anilist"))
        assertEquals("simkl_session_expired", ExternalAuthManager.sessionExpiredKey("Simkl"))
    }
}
