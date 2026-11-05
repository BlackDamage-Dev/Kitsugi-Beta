package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MdbListClientTest {

    @Test
    fun lookupUrl_prefersImdbAndSafelyEncodesApiKey() {
        val url = requireNotNull(MdbListClient.buildLookupUrl(
            apiKey = "key with&symbols",
            imdbId = " TT1234567 ",
            tmdbId = 42
        ))

        assertEquals("https", url.scheme)
        assertEquals("mdblist.com", url.host)
        assertEquals("/api/", url.encodedPath)
        assertEquals("key with&symbols", url.queryParameter("apikey"))
        assertEquals("tt1234567", url.queryParameter("i"))
        assertNull(url.queryParameter("tmdb"))
    }

    @Test
    fun lookupUrlFallsBackToPositiveTmdbId() {
        val url = requireNotNull(
            MdbListClient.buildLookupUrl(apiKey = "personal-key", imdbId = null, tmdbId = 550)
        )

        assertEquals("550", url.queryParameter("tmdb"))
        assertNull(url.queryParameter("i"))
    }

    @Test
    fun fallbackPrefersNonEmptyTmdbRatingsWhenImdbLookupIsEmpty() {
        val emptyImdb = MdbListRatings(imdbId = "tt1234567")
        val fromTmdb = MdbListRatings(tmdb = 8.3, imdbId = null)

        val selected = MdbListClient.selectRatingsWithFallback(
            imdbResult = emptyImdb,
            tmdbResult = fromTmdb,
            preferredImdbId = "tt1234567"
        )

        requireNotNull(selected)
        assertEquals(8.3, selected.tmdb!!, 0.001)
        assertEquals("tt1234567", selected.imdbId)
    }

    @Test
    fun fallbackKeepsImdbResultWhenItHasScores() {
        val imdb = MdbListRatings(imdb = 8.1, imdbId = "tt1234567")
        val tmdb = MdbListRatings(tmdb = 8.4)

        val selected = MdbListClient.selectRatingsWithFallback(imdb, tmdb, "tt1234567")

        assertEquals(imdb, selected)
    }

    @Test
    fun lookupUrlRejectsMissingOrInvalidIdentity() {
        assertNull(MdbListClient.buildLookupUrl("key", imdbId = null, tmdbId = null))
        assertNull(MdbListClient.buildLookupUrl("key", imdbId = "tt123", tmdbId = null))
        assertNull(MdbListClient.buildLookupUrl("key", imdbId = null, tmdbId = 0))
        assertNull(MdbListClient.buildLookupUrl("", imdbId = "tt1234567", tmdbId = null))
    }

    @Test
    fun parseRatingsSupportsCurrentArrayResponseAndMultipleScoreFormats() {
        val result = MdbListClient.parseRatings(
            """
            {
              "response": true,
              "ratings": [
                {"source":"IMDb", "value":"8.5/10"},
                {"source":"Rotten Tomatoes", "value":"92%"},
                {"source":"Rotten Tomatoes Audience", "score":87},
                {"source":"Metacritic", "value":"78/100"},
                {"source":"Letterboxd", "value":"4.1/5"},
                {"source":"TMDb", "value":"7.9/10"},
                {"source":"Trakt", "score":81}
              ]
            }
            """.trimIndent(),
            imdbId = "tt0133093"
        )

        requireNotNull(result)
        assertEquals(8.5, result.imdb!!, 0.001)
        assertEquals(92, result.tomatoes)
        assertEquals(87, result.tomatoesAudience)
        assertEquals(78, result.metacritic)
        assertEquals(4.1, result.letterboxd!!, 0.001)
        assertEquals(7.9, result.tmdb!!, 0.001)
        assertEquals(81.0, result.trakt!!, 0.001)
        assertEquals("tt0133093", result.imdbId)
    }

    @Test
    fun parseRatingsSupportsObjectRatingsAndTheMovieDbAlias() {
        val result = MdbListClient.parseRatings(
            """
            {
              "response": true,
              "imdbid": "tt1234567",
              "ratings": {
                "imdb": {"value": "8.6/10"},
                "rottentomatoes": {"value": "94%"},
                "themoviedb": {"value": "8.2/10"},
                "letterboxd": 4.0
              }
            }
            """.trimIndent(),
            imdbId = null
        )

        requireNotNull(result)
        assertEquals(8.6, result.imdb!!, 0.001)
        assertEquals(94, result.tomatoes)
        assertEquals(8.2, result.tmdb!!, 0.001)
        assertEquals(4.0, result.letterboxd!!, 0.001)
        assertEquals("tt1234567", result.imdbId)
    }

    @Test
    fun parseRatingsSupportsLegacyFieldsAndRejectsFailedResponses() {
        val legacy = MdbListClient.parseRatings(
            """{"response":"True","imdbid":"tt0133093","imdbrating":"8.7","tomatoesrating":"95%","tomatoesaudiencerating":"88%","metacriticrating":"82","letterboxdrating":"4.0/5","tmdbrating":"7.8","traktrating":"80%"}""",
            imdbId = null
        )

        requireNotNull(legacy)
        assertEquals(8.7, legacy.imdb!!, 0.001)
        assertEquals(95, legacy.tomatoes)
        assertEquals(88, legacy.tomatoesAudience)
        assertEquals(82, legacy.metacritic)
        assertEquals(4.0, legacy.letterboxd!!, 0.001)
        assertEquals(7.8, legacy.tmdb!!, 0.001)
        assertEquals(80.0, legacy.trakt!!, 0.001)
        assertEquals("tt0133093", legacy.imdbId)

        assertNull(MdbListClient.parseRatings("""{"response":false,"ratings":[]}""", imdbId = null))
        assertNull(MdbListClient.parseRatings("not-json", imdbId = null))
    }

    @Test
    fun parseRatingsFallsBackToLegacyFieldsWhenCurrentRatingsListIsEmpty() {
        val result = MdbListClient.parseRatings(
            """{"response":true,"ratings":[],"imdbrating":"8.1","tmdbrating":"7.9"}""",
            imdbId = "tt1234567"
        )

        requireNotNull(result)
        assertEquals(8.1, result.imdb!!, 0.001)
        assertEquals(7.9, result.tmdb!!, 0.001)
    }

    @Test
    fun parseRatingsTreatsZeroScoresAsMissing() {
        // MDBList bazı kayıtlar için puan yerine 0 döndürür; ekranda "0 Metacritic" gibi
        // anlamsız kutular görünmemesi için bu değerler "puan yok" kabul edilir.
        val result = MdbListClient.parseRatings(
            """
            {
              "response": true,
              "ratings": [
                {"source":"IMDb", "value":"8.4/10"},
                {"source":"Metacritic", "value":"0/100"},
                {"source":"Letterboxd", "value":"0/5"},
                {"source":"TMDb", "rating":0},
                {"source":"Rotten Tomatoes", "value":"91%"}
              ]
            }
            """.trimIndent(),
            imdbId = null
        )

        requireNotNull(result)
        assertEquals(8.4, result.imdb!!, 0.001)
        assertEquals(91, result.tomatoes)
        assertNull(result.metacritic)
        assertNull(result.letterboxd)
        assertNull(result.tmdb)
        assertNull(result.trakt)
    }

    @Test
    fun parseRatingsReturnsEmptyRatingsWhenEveryScoreIsZero() {
        val result = MdbListClient.parseRatings(
            """{"response":true,"ratings":[{"source":"IMDb","value":"0.0/10"},{"source":"Metacritic","value":0}]}""",
            imdbId = null
        )

        requireNotNull(result)
        // Boş (ama geçerli) bir yanıt: kart çizilmez, önbellek negatif kaydı korunur.
        assertTrue(result.isEmpty)
    }

    @Test
    fun quotaHeadersParseLimitRemainingAndReset() {
        val quota = requireNotNull(
            MdbListClient.parseQuotaHeaders("25000", "1784", "1734393600")
        )

        assertEquals(25000, quota.limit ?: -1)
        assertEquals(1784, quota.remaining ?: -1)
        assertEquals(1734393600L, quota.resetAtEpochSeconds ?: 0L)
        assertFalse(quota.isExhausted)
        assertTrue(quota.isKnown)
        assertEquals(23216, quota.used ?: -1)
    }

    @Test
    fun quotaHeadersRejectGarbageInsteadOfInventingNumbers() {
        // Başlık yoksa/bozuksa sayı uydurulmaz; arayüz "bilinmiyor" gösterir.
        assertNull(MdbListClient.parseQuotaHeaders(null, null, null))
        assertNull(MdbListClient.parseQuotaHeaders("abc", "xyz", "0"))
    }

    @Test
    fun quotaResetHeaderAcceptsMilliseconds() {
        assertEquals(1734393600L, MdbListClient.normalizeEpochSeconds(1734393600000L) ?: 0L)
        assertEquals(1734393600L, MdbListClient.normalizeEpochSeconds(1734393600L) ?: 0L)
        assertNull(MdbListClient.normalizeEpochSeconds(null))
    }

    @Test
    fun userQuotaReadsPaidPlanLimitFromResponseInsteadOfFreeLimit() {
        val quota = requireNotNull(
            MdbListClient.parseUserQuota(
                """{"api_requests":25000,"api_requests_count":1000,"patron_status":"active_patron","patreon_pledge":3}""",
                null
            )
        )

        // Ücretli plan: limit asla sabit 1.000 varsayılmaz.
        assertEquals(25000, quota.limit ?: -1)
        assertEquals(24000, quota.remaining ?: -1)
        assertEquals(MdbListPlan.SUPPORTER, quota.plan)
    }

    @Test
    fun userQuotaDetectsFreePlanAndExhaustedQuota() {
        val quota = requireNotNull(
            MdbListClient.parseUserQuota(
                """{"api_requests":1000,"api_requests_count":1000,"patron_status":"former_patron","patreon_pledge":0}""",
                null
            )
        )

        assertEquals(1000, quota.limit ?: -1)
        assertEquals(0, quota.remaining ?: -1)
        assertEquals(MdbListPlan.FREE, quota.plan)
        assertTrue(quota.isExhausted)
    }

    @Test
    fun userQuotaKeepsHeaderValuesWhenBodyIsUnreadable() {
        val headers = MdbListQuota(
            limit = 25000,
            remaining = 24999,
            resetAtEpochSeconds = 2_000_000_000L
        )

        val quota = requireNotNull(MdbListClient.parseUserQuota("not-json", headers))

        assertEquals(25000, quota.limit ?: -1)
        assertEquals(24999, quota.remaining ?: -1)
        assertEquals(2_000_000_000L, quota.resetAtEpochSeconds ?: 0L)
        assertEquals(MdbListPlan.UNKNOWN, quota.plan)
    }

    @Test
    fun sensitiveUrlRedactorHidesCredentialParametersButPreservesOtherQueryValues() {
        val url = "https://example.test/item?api_key=tmdb-secret&language=tr-TR&apikey=mdb-secret&token=login-token"

        val redacted = SensitiveUrlRedactor.redact(url)

        assertFalse(redacted.contains("tmdb-secret"))
        assertFalse(redacted.contains("mdb-secret"))
        assertFalse(redacted.contains("login-token"))
        assertTrue(redacted.contains("api_key=[REDACTED]"))
        assertTrue(redacted.contains("apikey=[REDACTED]"))
        assertTrue(redacted.contains("token=[REDACTED]"))
        assertTrue(redacted.contains("language=tr-TR"))
    }

    @Test
    fun parseRatingsUsesRealPayloadScalesAndPopcornAudienceAlias() {
        val result = MdbListClient.parseRatings(
            """
            {
              "title":"Jaws",
              "imdbid":"tt0073195",
              "ratings":[
                {"source":"imdb","value":8,"score":80},
                {"source":"tomatoes","value":98,"score":98},
                {"source":"popcorn","value":90,"score":90},
                {"source":"metacritic","value":87,"score":87},
                {"source":"tmdb","value":76,"score":76},
                {"source":"trakt","value":78,"score":78},
                {"source":"letterboxd","value":4.1,"score":82}
              ]
            }
            """.trimIndent(),
            imdbId = null
        )

        requireNotNull(result)
        assertEquals(8.0, result.imdb!!, 0.001)
        assertEquals(98, result.tomatoes)
        assertEquals(90, result.tomatoesAudience)
        assertEquals(87, result.metacritic)
        assertEquals(7.6, result.tmdb!!, 0.001)
        assertEquals(78.0, result.trakt!!, 0.001)
        assertEquals(4.1, result.letterboxd!!, 0.001)
        assertEquals("tt0073195", result.imdbId)
    }

    @Test
    fun mediaIdsKeepRealMalIdUsedByPopularAniListResults() {
        // Attack on Titan gibi AniList sonuçları stableId alanında doğrudan MAL ID taşır.
        // Bu değer null yapılırsa IMDb çözümü ve dolayısıyla MDBList isteği hiç başlamaz.
        val ids = resolveMdbListMediaIds(
            source = "anilist",
            storedId = 16_498,
            resolvedMalId = null,
            resolvedTmdbId = null
        )

        assertEquals(16_498, ids.malId)
        assertNull(ids.aniListId)
        assertTrue(ids.hasLookupIdentity)
    }

    @Test
    fun mediaIdsKeepProviderNamespacesSeparate() {
        val aniList = resolveMdbListMediaIds("anilist", 100_016_498, null, null)
        val tmdb = resolveMdbListMediaIds("tmdb", 1_429, null, null)
        val kitsu = resolveMdbListMediaIds("kitsu", 300_004_219, null, null)

        assertNull(aniList.malId)
        assertEquals(16_498, aniList.aniListId)
        assertNull(tmdb.malId)
        assertEquals(1_429, tmdb.tmdbId)
        assertNull(kitsu.malId)
        assertEquals(4_219, kitsu.kitsuId)
    }
}
