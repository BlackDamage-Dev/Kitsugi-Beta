package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiIdNamespaceTest {

    private fun sampleAnimeSubject(
        id: Int,
        name: String,
        nameCn: String,
        date: String?,
        eps: Int,
        infobox: Map<String, List<String>> = emptyMap()
    ) = BangumiApiClient.BangumiSubject(
        id = id,
        type = BangumiApiClient.SubjectType.ANIME,
        name = name,
        nameCn = nameCn,
        summary = "",
        date = date,
        platform = "TV",
        nsfw = false,
        locked = false,
        eps = eps,
        totalEpisodes = eps,
        volumes = 0,
        images = null,
        rating = null,
        collection = null,
        tags = emptyList(),
        metaTags = emptyList(),
        infobox = infobox
    )

    @Test
    fun `Bangumi subject types and platform map to supported Kitsugi types`() {
        assertEquals(MediaType.Anime, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.ANIME, "TV"))
        assertEquals(MediaType.Movie, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.ANIME, "剧场版"))
        assertEquals(MediaType.Manga, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.BOOK, "漫画"))
        assertEquals(MediaType.TvShow, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.REAL, "电视剧"))
        assertEquals(MediaType.Movie, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.REAL, "电影"))
    }

    @Test
    fun `unsupported subject types are not treated as anime`() {
        assertFalse(BangumiIdNamespace.supportsSubjectType(BangumiApiClient.SubjectType.GAME))
        assertFalse(BangumiIdNamespace.supportsSubjectType(BangumiApiClient.SubjectType.MUSIC))
        assertFalse(BangumiIdNamespace.supportsSubjectType(0))
    }

    @Test
    fun `media type maps back to the correct Bangumi subject type`() {
        assertEquals(BangumiApiClient.SubjectType.ANIME, BangumiIdNamespace.subjectTypeFor(MediaType.Anime))
        assertEquals(BangumiApiClient.SubjectType.BOOK, BangumiIdNamespace.subjectTypeFor(MediaType.Manga))
        assertEquals(BangumiApiClient.SubjectType.REAL, BangumiIdNamespace.subjectTypeFor(MediaType.Movie))
        assertEquals(BangumiApiClient.SubjectType.REAL, BangumiIdNamespace.subjectTypeFor(MediaType.TvShow))
    }

    @Test
    fun `movie platform mapping is explicit`() {
        assertTrue(BangumiIdNamespace.isMoviePlatform("电影"))
        assertTrue(BangumiIdNamespace.isMoviePlatform("劇場版"))
        assertFalse(BangumiIdNamespace.isMoviePlatform("电视剧"))
    }

    @Test
    fun `selectUniqueSubjectMatch normalizes NFKC fullwidth digits and matches infobox aliases`() {
        val kaijuSubject = sampleAnimeSubject(
            id = 400_602,
            name = "怪獣8号",
            nameCn = "怪兽8号",
            date = "2024-04-13",
            eps = 12,
            infobox = mapOf(
                "别名" to listOf("Kaiju No. 8", "Kaijuu 8-gou")
            )
        )

        // Fullwidth `８` from AniList `title.native = "怪獣８号"`
        assertEquals(
            400_602,
            BangumiIdNamespace.selectUniqueSubjectMatch(
                subjects = listOf(kaijuSubject),
                query = "怪獣８号",
                expectedYear = 2024,
                expectedSubjectType = BangumiApiClient.SubjectType.ANIME
            )?.id
        )
        // Infobox English alias
        assertEquals(
            400_602,
            BangumiIdNamespace.selectUniqueSubjectMatch(
                subjects = listOf(kaijuSubject),
                query = "Kaiju No. 8",
                expectedYear = 2024,
                expectedSubjectType = BangumiApiClient.SubjectType.ANIME
            )?.id
        )
    }

    @Test
    fun `selectUniqueSubjectMatch disambiguates homonyms using expectedYear`() {
        val hxh1999 = sampleAnimeSubject(
            id = 1704,
            name = "HUNTER×HUNTER",
            nameCn = "全职猎人",
            date = "1999-10-16",
            eps = 62
        )
        val hxh2011 = sampleAnimeSubject(
            id = 10357,
            name = "HUNTER×HUNTER",
            nameCn = "全职猎人",
            date = "2011-10-02",
            eps = 148
        )

        assertEquals(
            10357,
            BangumiIdNamespace.selectUniqueSubjectMatch(
                subjects = listOf(hxh1999, hxh2011),
                query = "HUNTER×HUNTER",
                expectedYear = 2011,
                expectedSubjectType = BangumiApiClient.SubjectType.ANIME
            )?.id
        )
        assertEquals(
            1704,
            BangumiIdNamespace.selectUniqueSubjectMatch(
                subjects = listOf(hxh1999, hxh2011),
                query = "HUNTER×HUNTER",
                expectedYear = 1999,
                expectedSubjectType = BangumiApiClient.SubjectType.ANIME
            )?.id
        )
        // Without expectedYear, two distinct homonyms remain safely ambiguous (null)
        assertNull(
            BangumiIdNamespace.selectUniqueSubjectMatch(
                subjects = listOf(hxh1999, hxh2011),
                query = "HUNTER×HUNTER",
                expectedYear = null,
                expectedSubjectType = BangumiApiClient.SubjectType.ANIME
            )
        )
    }

    @Test
    fun `mapped subject still requires a title alias instead of year-only acceptance`() {
        val wrongSameYear = sampleAnimeSubject(
            id = 999,
            name = "Unrelated Show",
            nameCn = "",
            date = "2019-04-01",
            eps = 13
        )
        assertNull(
            BangumiIdNamespace.selectUniqueSubjectMatch(
                subjects = listOf(wrongSameYear),
                query = "ぼくたちは勉強ができない",
                expectedYear = 2019,
                expectedSubjectType = BangumiApiClient.SubjectType.ANIME,
                allowYearOnlyFallback = false
            )
        )
        // Legacy search fallback remains unchanged for its existing callers.
        assertEquals(
            999,
            BangumiIdNamespace.selectUniqueSubjectMatch(
                subjects = listOf(wrongSameYear),
                query = "ぼくたちは勉強ができない",
                expectedYear = 2019,
                expectedSubjectType = BangumiApiClient.SubjectType.ANIME
            )?.id
        )
    }

    @Test
    fun `movie identity search accepts animated and live action films but not TV subjects`() {
        assertEquals(
            listOf(BangumiApiClient.SubjectType.ANIME, BangumiApiClient.SubjectType.REAL),
            BangumiIdNamespace.subjectTypesFor(MediaType.Movie)
        )

        // Canlı Bangumi kaydı: Coraline #103470, type=2 (ANIME), platform=剧场版.
        assertTrue(
            BangumiIdNamespace.isCompatibleWithMediaType(
                MediaType.Movie,
                BangumiApiClient.SubjectType.ANIME,
                "剧场版"
            )
        )
        assertTrue(
            BangumiIdNamespace.isCompatibleWithMediaType(
                MediaType.Movie,
                BangumiApiClient.SubjectType.REAL,
                "电影"
            )
        )
        assertFalse(
            BangumiIdNamespace.isCompatibleWithMediaType(
                MediaType.Movie,
                BangumiApiClient.SubjectType.ANIME,
                "TV"
            )
        )
        assertFalse(
            BangumiIdNamespace.isCompatibleWithMediaType(
                MediaType.Movie,
                BangumiApiClient.SubjectType.REAL,
                "电视剧"
            )
        )
        assertFalse(
            BangumiIdNamespace.isCompatibleWithMediaType(
                MediaType.Movie,
                BangumiApiClient.SubjectType.BOOK,
                "漫画"
            )
        )
    }

    @Test
    fun `Coraline search selects Bangumi animated movie 103470 and rejects TV decoys`() {
        val coraline = subject(
            id = 103_470,
            type = BangumiApiClient.SubjectType.ANIME,
            platform = "剧场版",
            nameCn = "鬼妈妈"
        )
        val animeTvDecoy = subject(
            id = 103_469,
            type = BangumiApiClient.SubjectType.ANIME,
            platform = "TV"
        )
        val realTvDecoy = subject(
            id = 103_471,
            type = BangumiApiClient.SubjectType.REAL,
            platform = "电视剧"
        )

        val selected = BangumiIdNamespace.selectUniqueSubjectMatch(
            subjects = listOf(animeTvDecoy, coraline, realTvDecoy),
            query = "Coraline",
            expectedYear = 2009,
            expectedSubjectTypes = BangumiIdNamespace.subjectTypesFor(MediaType.Movie),
            mediaType = MediaType.Movie
        )

        assertEquals(103_470, selected?.id)
    }

    private fun subject(id: Int, type: Int, platform: String, nameCn: String = "") =
        BangumiApiClient.BangumiSubject(
            id = id,
            type = type,
            name = "Coraline",
            nameCn = nameCn,
            summary = "",
            date = "2009-02-06",
            platform = platform,
            nsfw = false,
            locked = false,
            eps = 1,
            totalEpisodes = 1,
            volumes = 0,
            images = null,
            rating = null,
            collection = null,
            tags = emptyList(),
            metaTags = emptyList(),
            infobox = emptyMap()
        )
}
