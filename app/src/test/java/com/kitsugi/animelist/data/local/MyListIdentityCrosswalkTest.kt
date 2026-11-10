package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.CrossProviderIds
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Listem → Tümü` çapraz kimlik köprüsünün saf kısmı (Context/ağ gerektirmez).
 *
 * Bu haritalar boş kaldığında Bangumi/Kitsu/Shikimori satırları birleşik listede yalnızca
 * başlıkla eşleşebiliyor; Çince katalog başlıkları hiç eşmediği için aynı yapım iki kart
 * sayılıyordu (786 tekil içerik → 1502 kart).
 */
class MyListIdentityCrosswalkTest {

    private fun entry(
        id: Int,
        title: String,
        source: String,
        malId: Int? = null,
        type: MediaType = MediaType.Anime,
        year: Int? = 2023,
        titleEnglish: String? = null
    ) = MediaEntry(
        id = id,
        title = title,
        titleEnglish = titleEnglish,
        type = type,
        status = WatchStatus.Completed,
        score = 8,
        progress = 12,
        total = 12,
        source = source,
        malId = malId,
        year = year
    )

    @Test
    fun bangumiRowGainsMalKeyFromTheInvertedMappingCache() {
        val bangumi = entry(id = 5, title = "葬送のフリーレン", source = "bangumi", malId = 500_057_162)
        val subjectToMal = MyListIdentityCrosswalk
            .reverseBangumiMapping(mapOf("bgm_a_52991" to 57162))

        val extras = MyListIdentityCrosswalk.extraKeysFor(bangumi, ids = null, bangumiSubjectToMal = subjectToMal)

        assertEquals(setOf("Anime:mal:52991"), extras)
    }

    @Test
    fun mangaRowUsesTheBookNamespaceOfTheSameMapping() {
        val bangumiManga = entry(
            id = 8,
            title = "葬送のフリーレン",
            source = "bangumi",
            malId = 500_057_162,
            type = MediaType.Manga
        )
        val subjectToMal = MyListIdentityCrosswalk
            .reverseBangumiMapping(mapOf("bgm_m_52991" to 57162))

        assertEquals(
            setOf("Manga:mal:52991"),
            MyListIdentityCrosswalk.extraKeysFor(bangumiManga, null, subjectToMal)
        )
    }

    @Test
    fun cachedCrossIdentityClosesEveryOpenNamespace() {
        val kitsu = entry(id = 6, title = "Kitsu satırı", source = "kitsu", malId = 300_123_456)
        val ids = CrossProviderIds(malId = 52991, aniListId = 51004, bangumiSubjectId = 57162, tmdbId = 999)

        val extras = MyListIdentityCrosswalk.extraKeysFor(kitsu, ids, emptyMap())

        assertEquals(
            setOf("Anime:mal:52991", "Anime:anilist:51004", "Anime:bangumi:57162", "Anime:tmdb:999"),
            extras
        )
    }

    @Test
    fun rowOwnProviderNamespaceIsNeverOverriddenByTheCache() {
        val mal = entry(id = 7, title = "Frieren", source = "mal", malId = 52991)
        val staleCache = CrossProviderIds(malId = 11111, aniListId = 51004)

        val extras = MyListIdentityCrosswalk.extraKeysFor(mal, staleCache, emptyMap())

        assertFalse(
            "A stale cached MAL id must never be added next to the row's own MAL id " +
                "(two ids in one namespace would block every merge on that axis).",
            extras.contains("Anime:mal:11111")
        )
        assertEquals(setOf("Anime:anilist:51004"), extras)
    }

    @Test
    fun ambiguousBangumiMappingIsDropped() {
        val raw = mapOf("bgm_a_52991" to 57162, "bgm_a_52992" to 57162)

        assertTrue(
            "One条目 tied to two MAL records is not a bridge; leaving the rows apart beats " +
                "merging two productions.",
            MyListIdentityCrosswalk.reverseBangumiMapping(raw).isEmpty()
        )
    }

    @Test
    fun onlyExactTitleYearCrossTypeCandidatesRequestBridgeResolution() {
        val anime = entry(id = 40, title = "Same film", source = "anilist", year = 2025)
        val movie = entry(id = 41, title = "Same film", source = "simkl", type = MediaType.Movie, year = 2025)
        val otherYear = entry(id = 42, title = "Same film", source = "simkl", type = MediaType.Movie, year = 2024)
        val manga = entry(id = 43, title = "Same film", source = "mal", type = MediaType.Manga, year = 2025)

        assertEquals(
            setOf(40, 41),
            MyListIdentityCrosswalk.crossTypeResolutionCandidates(listOf(anime, movie, otherYear, manga))
        )
    }

    @Test
    fun nonAnimeMangaPreferenceEntriesAreIgnored() {
        val raw = mapOf(
            "bgm_r_52991" to 900,        // REAL (film/dizi) — no MAL axis for that type
            "bgm_a_notanumber" to 4,
            "unrelated_key" to 3,
            "bgm_a_52991" to 0          // subject id 0 = never learned
        )

        assertTrue(MyListIdentityCrosswalk.reverseBangumiMapping(raw).isEmpty())
    }

    @Test
    fun onlyRowsWithoutASharedAxisAskForACrosswalk() {
        assertFalse(
            "MAL row already carries the shared axis",
            MyListIdentityCrosswalk.needsCrosswalk(entry(id = 9, title = "x", source = "mal", malId = 52991))
        )
        assertTrue(
            "Bangumi row only carries its own namespace",
            MyListIdentityCrosswalk.needsCrosswalk(entry(id = 10, title = "x", source = "bangumi", malId = 500_057_162))
        )
        assertFalse(
            "Movie row is bridged by its TMDB id",
            MyListIdentityCrosswalk.needsCrosswalk(
                entry(id = 11, title = "x", source = "tmdb", type = MediaType.Movie).copy(tmdbId = 42)
            )
        )
        assertTrue(
            "Bangumi movie row has neither TMDB nor Simkl identity yet",
            MyListIdentityCrosswalk.needsCrosswalk(
                entry(id = 12, title = "x", source = "bangumi", type = MediaType.Movie, malId = 500_057_162)
            )
        )
    }

    @Test
    fun reverseKitsuMappingInvertsScopedAndGlobalKeysByMediaType() {
        val raw = mapOf(
            "kitsu_media_global_a_16498" to 7442,
            "kitsu_media_user42_a_59177" to 49001,
            "kitsu_media_global_m_23390" to 12500,
            "unrelated_pref" to 99
        )

        val reversed = MyListIdentityCrosswalk.reverseKitsuMapping(raw)

        assertEquals(16498, reversed["Anime:7442"])
        assertEquals(59177, reversed["Anime:49001"])
        assertEquals(23390, reversed["Manga:12500"])
        assertNull(reversed["Manga:7442"])
    }

    @Test
    fun kitsuRowReceivesMalKeyFromReverseKitsuMappingEvenWithoutCacheBundle() {
        val kitsu = entry(id = 20, title = "Attack on Titan", source = "kitsu", malId = 300_007_442)
        val kitsuMap = MyListIdentityCrosswalk.reverseKitsuMapping(mapOf("kitsu_media_global_a_16498" to 7442))

        val extras = MyListIdentityCrosswalk.extraKeysFor(
            entry = kitsu,
            ids = null,
            bangumiSubjectToMal = emptyMap(),
            kitsuMediaToMal = kitsuMap
        )

        assertEquals(setOf("Anime:mal:16498"), extras)
    }

    @Test
    fun indexCrossIdentitySnapshotLinksKitsuAniListBangumiWithoutLeakingAnimeTmdb() {
        val bundle = CrossProviderIds(
            malId = 16498,
            aniListId = 16498,
            kitsuId = 7442,
            shikimoriId = 16498,
            bangumiSubjectId = 55870,
            tmdbId = 1429,
            simklId = 39687
        )
        val indexed = MyListIdentityCrosswalk.indexCrossIdentitySnapshot(
            mapOf("Anime:mal:16498" to bundle)
        )

        assertEquals(16498, indexed["Anime:kitsu:7442"]?.malId)
        assertEquals(7442, indexed["Anime:bangumi:55870"]?.kitsuId)
        assertEquals(7442, indexed["Anime:simkl:39687"]?.kitsuId)
        assertNull("Anime için TMDB anahtarı sezon çakışması yaratacağından indekslenmemeli", indexed["Anime:tmdb:1429"])
    }

    @Test
    fun ambiguousKitsuMappingIsDropped() {
        val raw = mapOf(
            "kitsu_media_global_a_52588" to 46900,
            "kitsu_media_global_a_52589" to 46900
        )

        assertTrue(MyListIdentityCrosswalk.reverseKitsuMapping(raw).isEmpty())
    }
}
