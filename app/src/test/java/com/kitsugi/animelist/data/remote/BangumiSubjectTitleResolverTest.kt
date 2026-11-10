package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.model.MediaType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BangumiSubjectTitleResolver]'ın saf (ağ gerektirmeyen) yardımcıları: AniList yanıtını
 * birebir başlık eşleşmesiyle doğrulama, stableId dönüşümü ve medya türü eşlemesi.
 */
class BangumiSubjectTitleResolverTest {

    private fun request(rawId: Int, title: String, type: MediaType = MediaType.Anime) =
        BangumiSubjectTitleResolver.Request(rawId, title, type)

    private fun mediaObject(native: String, romaji: String?, english: String?, synonyms: List<String> = emptyList()): JSONObject {
        val title = JSONObject().put("native", native)
        romaji?.let { title.put("romaji", it) }
        english?.let { title.put("english", it) }
        val obj = JSONObject().put("title", title)
        obj.put("synonyms", JSONArray(synonyms))
        return obj
    }

    private fun responseFor(chunkSize: Int, mediaPerAlias: List<List<JSONObject>>): JSONObject {
        val data = JSONObject()
        for (index in 0 until chunkSize) {
            val page = JSONObject().put("media", JSONArray(mediaPerAlias.getOrNull(index).orEmpty()))
            data.put("a$index", page)
        }
        return JSONObject().put("data", data)
    }

    @Test
    fun fromRow_convertsStableIdsAndRejectsInvalid() {
        assertEquals(7947, BangumiSubjectTitleResolver.Request.fromRow(500_007_947, "x", "anime")?.rawId)
        assertEquals(7947, BangumiSubjectTitleResolver.Request.fromRow(7947, "x", "anime")?.rawId)
        assertNull(BangumiSubjectTitleResolver.Request.fromRow(0, "x", "anime"))
        assertNull(BangumiSubjectTitleResolver.Request.fromRow(-3, "x", "anime"))
    }

    @Test
    fun mediaTypeFromKey_mapsBangumiKeys() {
        assertEquals(MediaType.Manga, BangumiSubjectTitleResolver.mediaTypeFromKey("manga"))
        assertEquals(MediaType.Anime, BangumiSubjectTitleResolver.mediaTypeFromKey("anime"))
        assertEquals(MediaType.Anime, BangumiSubjectTitleResolver.mediaTypeFromKey("ANIME"))
        assertEquals(MediaType.TvShow, BangumiSubjectTitleResolver.mediaTypeFromKey("tv"))
    }

    @Test
    fun romajiOnlyCacheDoesNotSuppressEnglishLookup() {
        val partial = BangumiTitleCache.LatinTitles(
            romaji = "Shingeki no Kyojin",
            english = null,
            native = "進撃の巨人"
        )
        assertFalse(BangumiSubjectTitleResolver.hasReliableEnglish(partial))
        assertTrue(
            BangumiSubjectTitleResolver.hasReliableEnglish(
                partial.copy(english = "Attack on Titan")
            )
        )
        // Eski cache'de English=Romaji şeklinde saklanan değer de yeniden doğrulanır.
        assertFalse(
            BangumiSubjectTitleResolver.hasReliableEnglish(
                partial.copy(english = "Shingeki no Kyojin")
            )
        )
    }

    @Test
    fun mediaTypeFromKey_keepsMovieDistinct() {
        assertEquals(MediaType.Movie, BangumiSubjectTitleResolver.mediaTypeFromKey("movie"))
        assertEquals(MediaType.TvShow, BangumiSubjectTitleResolver.mediaTypeFromKey("tvshow"))
    }

    @Test
    fun animeMovieUsesAniListButBangumiRealMovieUsesTmdb() {
        val animeMovie = request(1, "映画", MediaType.Movie).copy(
            subjectType = BangumiApiClient.SubjectType.ANIME
        )
        val realMovie = request(2, "电影", MediaType.Movie).copy(
            subjectType = BangumiApiClient.SubjectType.REAL
        )

        assertTrue(BangumiSubjectTitleResolver.shouldTryAniList(animeMovie))
        assertFalse(BangumiSubjectTitleResolver.shouldTryTmdb(animeMovie))
        assertFalse(BangumiSubjectTitleResolver.shouldTryAniList(realMovie))
        assertTrue(BangumiSubjectTitleResolver.shouldTryTmdb(realMovie))
    }

    @Test
    fun negativeCacheRequiresEveryApplicableProviderToFinishWithoutEnglish() {
        val romajiOnly = BangumiTitleCache.LatinTitles(
            romaji = "Shingeki no Kyojin",
            english = null,
            native = "進撃の巨人"
        )
        val realMovie = request(1, "電影", MediaType.Movie).copy(
            subjectType = BangumiApiClient.SubjectType.REAL
        )
        val unknownMovie = request(2, "電影", MediaType.Movie)
        val anime = request(3, "進撃の巨人", MediaType.Anime)

        assertFalse(BangumiSubjectTitleResolver.shouldMarkNegative(realMovie, false, false, romajiOnly))
        assertFalse(BangumiSubjectTitleResolver.shouldMarkNegative(realMovie, true, false, romajiOnly))
        assertTrue(BangumiSubjectTitleResolver.shouldMarkNegative(realMovie, false, true, romajiOnly))

        // Tür bilinmiyorsa hem AniList hem TMDB geçişi tamamlanmalıdır.
        assertFalse(BangumiSubjectTitleResolver.shouldMarkNegative(unknownMovie, true, false, romajiOnly))
        assertTrue(BangumiSubjectTitleResolver.shouldMarkNegative(unknownMovie, true, true, romajiOnly))
        // Anime için uygun tek kaynak AniList'tir; TMDB sonucu gerekmez.
        assertTrue(BangumiSubjectTitleResolver.shouldMarkNegative(anime, true, false, romajiOnly))
        assertFalse(
            BangumiSubjectTitleResolver.shouldMarkNegative(
                anime,
                completedAniList = true,
                completedTmdb = true,
                titles = romajiOnly.copy(english = "Attack on Titan")
            )
        )
    }

    @Test
    fun matchAniListResponse_acceptsExactNativeMatch() {
        val chunk = listOf(request(7947, "魔法少女まどか☆マギカ"))
        val response = responseFor(
            1,
            listOf(
                listOf(
                    mediaObject(
                        native = "魔法少女まどか☆マギカ",
                        romaji = "Mahou Shoujo Madoka★Magica",
                        english = "Puella Magi Madoka Magica"
                    )
                )
            )
        )
        val result = BangumiSubjectTitleResolver.matchAniListResponse(chunk, response)
        val latin = result.resolved[7947]
        assertEquals("Mahou Shoujo Madoka★Magica", latin?.romaji)
        assertEquals("Puella Magi Madoka Magica", latin?.english)
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun matchAniListResponse_acceptsSynonymMatch() {
        val chunk = listOf(request(316, "機動戦士ガンダム00"))
        val response = responseFor(
            1,
            listOf(
                listOf(
                    mediaObject(
                        native = "機動戦士ガンダムダブルオー",
                        romaji = "Mobile Suit Gundam 00",
                        english = "Mobile Suit Gundam 00",
                        synonyms = listOf("機動戦士ガンダム00")
                    )
                )
            )
        )
        val result = BangumiSubjectTitleResolver.matchAniListResponse(chunk, response)
        assertEquals("Mobile Suit Gundam 00", result.resolved[316]?.romaji)
    }

    @Test
    fun matchAniListResponse_rejectsWrongTitle() {
        val chunk = listOf(request(7947, "魔法少女まどか☆マギカ"))
        val response = responseFor(
            1,
            listOf(
                listOf(
                    mediaObject(
                        native = "魔法少女サイト",
                        romaji = "Mahou Shoujo Site",
                        english = "Magical Girl Site"
                    )
                )
            )
        )
        val result = BangumiSubjectTitleResolver.matchAniListResponse(chunk, response)
        assertTrue(result.resolved.isEmpty())
        assertEquals(listOf(7947), result.unmatched)
    }

    @Test
    fun matchAniListResponse_nullResponseYieldsEmpty() {
        val chunk = listOf(request(1, "x"))
        val result = BangumiSubjectTitleResolver.matchAniListResponse(chunk, null)
        assertTrue(result.resolved.isEmpty())
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun matchAniListResponse_multipleAliasesAlignedByIndex() {
        val chunk = listOf(
            request(1, "シュタインズ・ゲート"),
            request(2, "プリンス・オブ・ストライド オルタナティブ")
        )
        val response = responseFor(
            2,
            listOf(
                listOf(mediaObject("シュタインズ・ゲート", "Steins;Gate", "Steins;Gate")),
                listOf(mediaObject("プリンス・オブ・ストライド オルタナティブ", "Prince of Stride: Alternative", null))
            )
        )
        val result = BangumiSubjectTitleResolver.matchAniListResponse(chunk, response)
        assertEquals("Steins;Gate", result.resolved[1]?.romaji)
        assertEquals("Prince of Stride: Alternative", result.resolved[2]?.romaji)
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun matchAniListResponse_extractsIdMalForBangumiCrosswalkBridge() {
        val kaijuObj = mediaObject("怪獣８号", "Kaijuu 8-gou", "Kaiju No. 8").put("idMal", 52588)
        val chunk = listOf(request(400_602, "怪獣8号"))
        val response = responseFor(1, listOf(listOf(kaijuObj)))

        val result = BangumiSubjectTitleResolver.matchAniListResponse(chunk, response)
        assertEquals("Kaiju No. 8", result.resolved[400_602]?.english)
        assertEquals(52588, result.matchedMalIds[400_602])
    }
}
