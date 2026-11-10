package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alt kaynaklı çapraz kimliğin saf kararları: satır hangi kaynaktan gelirse gelsin
 * doğru ID alanına düşmeli, hangi kaynağın yazılabileceği ve "atlandı" ile "hata"
 * ayrımı burada belirlenir. (Ağ çağrıları bu kararların üstünde çalışır.)
 */
class CrossProviderIdentityTest {

    @Test
    fun realMalIdIsKeptInMalNamespace() {
        val ids = CrossProviderIds.decode(storedId = 21, source = "mal", type = MediaType.Anime)
        assertEquals(21, ids.malId)
        assertNull(ids.aniListId)
        assertNull(ids.kitsuId)
        assertNull(ids.bangumiSubjectId)
    }

    @Test
    fun aniListOffsetIsDecodedOnlyAsAniListMediaId() {
        val ids = CrossProviderIds.decode(storedId = 100_057_123, source = "anilist", type = MediaType.Anime)
        assertEquals(57_123, ids.aniListId)
        assertNull("AniList sentetik kimliği MAL ID sayılmamalı", ids.malId)
    }

    @Test
    fun overlappingSimklAndAniListWindowsAreDisambiguatedBySource() {
        // AniList penceresi 100M..299M, Simkl penceresi 150M..249M → örtüşür.
        val simklRow = CrossProviderIds.decode(storedId = 150_000_001, source = "simkl", type = MediaType.Anime)
        assertEquals(1, simklRow.simklId)
        assertNull(simklRow.aniListId)

        val aniListRow = CrossProviderIds.decode(storedId = 150_000_001, source = "anilist", type = MediaType.Anime)
        assertEquals(50_000_001, aniListRow.aniListId)
        assertNull(aniListRow.simklId)
    }

    @Test
    fun kitsuShikimoriAndBangumiOffsetsDecodeToTheirOwnIds() {
        val kitsu = CrossProviderIds.decode(storedId = 300_012_345, source = "kitsu", type = MediaType.Anime)
        assertEquals(12_345, kitsu.kitsuId)
        assertNull(kitsu.malId)

        val shikimori = CrossProviderIds.decode(storedId = 400_006_789, source = "shikimori", type = MediaType.Anime)
        assertEquals(6_789, shikimori.shikimoriId)
        assertNull("Shikimori numeric ID asla MAL ID değildir", shikimori.malId)

        val bangumi = CrossProviderIds.decode(storedId = 500_035_658, source = "bangumi", type = MediaType.Anime)
        assertEquals(35_658, bangumi.bangumiSubjectId)
        assertNull(bangumi.malId)
    }

    @Test
    fun movieAndTvRowsNeverExposeStoredIdAsMalId() {
        val movie = CrossProviderIds.decode(
            storedId = 3_000,
            source = "tmdb",
            type = MediaType.Movie,
            entrySimklId = 44,
            entryTmdbId = 603
        )
        assertNull(movie.malId)
        assertEquals(603, movie.tmdbId)
        assertEquals(44, movie.simklId)
    }

    @Test
    fun cacheKeyIsScopedByNamespaceAndType() {
        val fromMal = CrossProviderIds.cacheKey(MediaType.Anime, 5114, "Fullmetal Alchemist: Brotherhood", null, 2009)
        val fromAniList = CrossProviderIds.cacheKey(MediaType.Anime, 100_005_114, "Fullmetal Alchemist: Brotherhood", null, 2009)
        val fromSimkl = CrossProviderIds.cacheKey(MediaType.Anime, 150_000_004, "Fullmetal Alchemist", null, 2009)
        val fromManga = CrossProviderIds.cacheKey(MediaType.Manga, 5114, "Fullmetal Alchemist", null, 2009)
        assertEquals("Anime:mal:5114", fromMal)
        assertEquals("Anime:anilist:5114", fromAniList)
        assertEquals("Anime:simkl:4", fromSimkl)
        assertEquals("Manga:mal:5114", fromManga)
    }

    @Test
    fun cacheKeyFallsBackToTitleAndYearWhenRowHasNoId() {
        val key = CrossProviderIds.cacheKey(MediaType.Anime, null, "86: Eighty Six", null, 2021)
        assertEquals("Anime:t:86eightysix:2021", key)
    }

    @Test
    fun mergeNeverOverwritesAlreadyKnownIds() {
        val row = CrossProviderIds(malId = 21)
        val cached = CrossProviderIds(malId = 999, kitsuId = 42, bangumiSubjectId = 35658)
        val merged = row.mergedWith(cached)
        assertEquals(21, merged.malId)
        assertEquals(42, merged.kitsuId)
        assertEquals(35658, merged.bangumiSubjectId)
    }

    @Test
    fun unresolvedIdentityIsReportedAsEmpty() {
        assertTrue(CrossProviderIds().isEmpty)
        assertFalse(CrossProviderIds(malId = 21).isEmpty)
        assertTrue(CrossProviderIds(malId = 21).hasAnimeMangaIdentity)
        assertFalse(CrossProviderIds(malId = 21).hasVideoIdentity)
        assertTrue(CrossProviderIds(tmdbId = 603).hasVideoIdentity)
    }

    @Test
    fun jsonCarriesEveryIdAndProvenance() {
        val ids = CrossProviderIds(
            malId = 21,
            aniListId = 57_123,
            kitsuId = 12_345,
            shikimoriId = 6_789,
            bangumiSubjectId = 35_658,
            tmdbId = 603,
            simklId = 44,
            via = mapOf("mal" to "ARM")
        )
        val json = ids.toJson()
        assertEquals(21, json.getInt("mal"))
        assertEquals(57_123, json.getInt("anilist"))
        assertEquals(12_345, json.getInt("kitsu"))
        assertEquals(6_789, json.getInt("shikimori"))
        assertEquals(35_658, json.getInt("bangumi"))
        assertEquals(603, json.getInt("tmdb"))
        assertEquals(44, json.getInt("simkl"))
        assertEquals("ARM", json.getJSONObject("via").getString("mal"))
    }

    @Test
    fun eachServiceKeepsItsOwnMediaTypeLimits() {
        assertTrue(CrossSyncPolicy.supportsKitsu(MediaType.Manga))
        assertTrue(CrossSyncPolicy.supportsShikimori(MediaType.Manga))
        assertFalse("Simkl mangaları tanımaz", CrossSyncPolicy.supportsSimkl(MediaType.Manga))
        assertTrue(CrossSyncPolicy.supportsSimkl(MediaType.TvShow))
        assertTrue(CrossSyncPolicy.supportsBangumi(MediaType.Movie))
        assertFalse(CrossSyncPolicy.supportsAniList(MediaType.Movie))
        assertFalse(CrossSyncPolicy.supportsMal(MediaType.TvShow))
        assertFalse(CrossSyncPolicy.supportsKitsu(null))
    }

    @Test
    fun simklMovieIdentityIsIncompleteUntilConnectedBangumiIsResolved() {
        val connected = ConnectedSources(
            aniList = false,
            mal = false,
            kitsu = false,
            shikimori = false,
            simkl = true,
            bangumi = true
        )
        val simklOnly = CrossProviderIds(simklId = 123_456, tmdbId = 15_539)

        assertFalse(
            "Simkl/TMDB kimliği, bağlı Bangumi aramasını erken bitirmemeli",
            CrossSyncPolicy.isIdentityComplete(MediaType.Movie, simklOnly, connected)
        )
        assertTrue(
            CrossSyncPolicy.isIdentityComplete(
                MediaType.Movie,
                simklOnly.copy(bangumiSubjectId = 103_470),
                connected
            )
        )
        assertTrue(
            "Bangumi bağlı değilse Simkl/TMDB kimliği tek başına yeterli",
            CrossSyncPolicy.isIdentityComplete(
                MediaType.Movie,
                simklOnly,
                connected.copy(bangumi = false)
            )
        )
    }

    @Test
    fun bangumiMovieIdentityDoesNotWaitForDisconnectedSimkl() {
        val bangumiOnlyConnection = ConnectedSources(
            aniList = false,
            mal = false,
            kitsu = false,
            shikimori = false,
            simkl = false,
            bangumi = true
        )
        assertTrue(
            CrossSyncPolicy.isIdentityComplete(
                MediaType.Movie,
                CrossProviderIds(bangumiSubjectId = 103_470),
                bangumiOnlyConnection
            )
        )
        assertFalse(
            CrossSyncPolicy.isIdentityComplete(
                MediaType.Movie,
                CrossProviderIds(),
                bangumiOnlyConnection
            )
        )
    }

    @Test
    fun bangumiRowFansOutOnlyWithVerifiedIdentity() {
        val bangumiOnly = CrossProviderIds(bangumiSubjectId = 35_658)
        assertFalse(CrossSyncPolicy.allowFanOut(isBangumiRow = true, ids = bangumiOnly))
        assertTrue(
            CrossSyncPolicy.allowFanOut(
                isBangumiRow = true,
                ids = bangumiOnly.copy(malId = 21)
            )
        )
        assertTrue(
            CrossSyncPolicy.allowFanOut(
                isBangumiRow = false,
                ids = CrossProviderIds()
            )
        )
    }

    @Test
    fun rawProviderIdsBelow100MAreNeverDecodedAsMal() {
        // Shikimori içe aktarması ham target_id yazar; eski Kitsu/Bangumi satırları da ham
        // kimlik taşıyabilir. Bunlar MAL sayılırsa çözümleyici MAL adımını atlıyor ve
        // (Bangumi'de) subject_id sahte MAL kimliği gibi kullanılıp yayılım kapısı açılıyordu.
        val shikimori = CrossProviderIds.decode(storedId = 5114, source = "shikimori", type = MediaType.Anime)
        assertEquals(5114, shikimori.shikimoriId)
        assertNull("Shikimori ham target_id MAL kimliği değildir", shikimori.malId)

        val bangumi = CrossProviderIds.decode(storedId = 35_658, source = "bangumi", type = MediaType.Anime)
        assertEquals(35_658, bangumi.bangumiSubjectId)
        assertNull("Bangumi ham subject_id MAL kimliği değildir", bangumi.malId)
        assertFalse(CrossSyncPolicy.allowFanOut(isBangumiRow = true, ids = bangumi))

        val kitsu = CrossProviderIds.decode(storedId = 12_345, source = "kitsu", type = MediaType.Anime)
        assertEquals(12_345, kitsu.kitsuId)
        assertNull(kitsu.malId)
    }

    @Test
    fun bangumiMovieRowKeepsSubjectIdForCrossResolution() {
        val movie = CrossProviderIds.decode(storedId = 500_035_658, source = "bangumi", type = MediaType.Movie)
        assertEquals(35_658, movie.bangumiSubjectId)
        assertNull(movie.malId)
    }

    @Test
    fun cacheKeyUsesSourceNamespaceForRawIds() {
        val shikimori = CrossProviderIds.cacheKey(MediaType.Anime, 5114, "FMA", null, 2009, source = "shikimori")
        val bangumi = CrossProviderIds.cacheKey(MediaType.Anime, 5114, "FMA", null, 2009, source = "bangumi")
        val mal = CrossProviderIds.cacheKey(MediaType.Anime, 5114, "FMA", null, 2009, source = "mal")
        assertEquals("Anime:shikimori:5114", shikimori)
        assertEquals("Anime:bangumi:5114", bangumi)
        assertEquals("Anime:mal:5114", mal)
    }

    @Test
    fun cacheKeyDisambiguatesOverlapBySourceWhenKnown() {
        val aniList = CrossProviderIds.cacheKey(MediaType.Anime, 150_000_001, "X", null, 2020, source = "anilist")
        val simkl = CrossProviderIds.cacheKey(MediaType.Anime, 150_000_001, "X", null, 2020, source = "simkl")
        assertEquals("Anime:anilist:50000001", aniList)
        assertEquals("Anime:simkl:1", simkl)
    }

    @Test
    fun sharesAnyIdRequiresSameNamespaceMatch() {
        val a = CrossProviderIds(malId = 21, aniListId = 57_123)
        assertTrue(a.sharesAnyIdWith(CrossProviderIds(aniListId = 57_123, kitsuId = 99)))
        assertTrue(a.sharesAnyIdWith(CrossProviderIds(malId = 21)))
        assertFalse(a.sharesAnyIdWith(CrossProviderIds(malId = 22, aniListId = 1)))
        // Aynı sayı farklı uzayda eşleşme sayılmaz.
        assertFalse(a.sharesAnyIdWith(CrossProviderIds(kitsuId = 21)))
        // TMDB anime/mangada kanıt değil (sezonlar tek TMDB dizisinde toplanır).
        val animeRow = CrossProviderIds(tmdbId = 603)
        assertFalse(animeRow.sharesAnyIdWith(CrossProviderIds(tmdbId = 603), includeTmdb = false))
        assertTrue(animeRow.sharesAnyIdWith(CrossProviderIds(tmdbId = 603), includeTmdb = true))
    }

    @Test
    fun bangumiVideoRowFansOutWithTmdbIdentity() {
        val ids = CrossProviderIds(bangumiSubjectId = 35_658, tmdbId = 603)
        assertTrue(CrossSyncPolicy.allowFanOut(isBangumiRow = true, ids = ids))
    }

    @Test
    fun skippedSourcesAreFinalButErrorsAreRetryable() {
        assertFalse(CrossSyncPolicy.shouldRetry(errors = emptyList(), skipped = listOf("Kitsu eşleşmesi yok")))
        assertTrue(CrossSyncPolicy.shouldRetry(errors = listOf("HTTP 500"), skipped = emptyList()))
    }
}
