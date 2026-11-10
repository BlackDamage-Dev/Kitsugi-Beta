package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.fandom.WikidataParsers
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TmdbAnimeCharacterPortraitEnrichmentTest {

    @Before
    fun clearCaches() {
        DetailCache.clear()
        KitsugiIdResolver.clearCache()
    }

    @Test
    fun armRelationsSeparateMovieAndTvCollisionsCorrectly() {
        val mixedArmJson = """
            [
              {
                "mal": 999,
                "anilist": 9990,
                "kitsu": 9991,
                "themoviedb": 372058,
                "themoviedb-season": 1,
                "media": "TV",
                "imdb": "tt0000999"
              },
              {
                "mal": 32281,
                "anilist": 21519,
                "kitsu": 11614,
                "themoviedb": 372058,
                "themoviedb-season": null,
                "media": "MOVIE",
                "imdb": "tt5311514"
              }
            ]
        """.trimIndent()

        val movieRelations = KitsugiIdResolver.parseArmRelations(mixedArmJson, MediaType.Movie)
        assertEquals(1, movieRelations.size)
        assertEquals(32281, movieRelations[0].optInt("mal", -1))
        assertEquals(21519, movieRelations[0].optInt("anilist", -1))
        assertEquals(11614, movieRelations[0].optInt("kitsu", -1))
        assertEquals("tt5311514", movieRelations[0].optString("imdb", ""))

        val tvRelations = KitsugiIdResolver.parseArmRelations(mixedArmJson, MediaType.TvShow)
        assertEquals(1, tvRelations.size)
        assertEquals(999, tvRelations[0].optInt("mal", -1))
        assertEquals(9990, tvRelations[0].optInt("anilist", -1))
        assertEquals("tt0000999", tvRelations[0].optString("imdb", ""))
    }

    @Test
    fun wikidataParserExtractsMalAniListKitsuAndBangumiIds() {
        val wikidataJson = """
            {
              "entities": {
                "Q26708312": {
                  "labels": { "en": { "value": "Your Name" }, "ja": { "value": "君の名は。" } },
                  "claims": {
                    "P4086": [{ "mainsnak": { "datavalue": { "value": "32281" } } }],
                    "P8729": [{ "mainsnak": { "datavalue": { "value": "21519" } } }],
                    "P11495": [{ "mainsnak": { "datavalue": { "value": "11614" } } }],
                    "P12095": [{ "mainsnak": { "datavalue": { "value": "160209" } } }],
                    "P345": [{ "mainsnak": { "datavalue": { "value": "tt5311514" } } }]
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = WikidataParsers.parseEntities(wikidataJson)["Q26708312"]
        assertNotNull(parsed)
        assertEquals(32281, parsed?.malAnimeId)
        assertEquals(21519, parsed?.aniListAnimeId)
        assertEquals(11614, parsed?.kitsuAnimeId)
        assertEquals(160209, parsed?.bangumiSubjectId)
        assertEquals("tt5311514", parsed?.imdbId)
    }

    @Test
    fun tmdbCreditsMarksVoiceRolesAndClearsActorPhotoOnVoiceSlots() = runBlocking {
        val creditsJson = """
            {
              "cast": [
                {"id": 225730, "name": "Ryunosuke Kamiki", "character": "Taki Tachibana (voice)", "profile_path": "/kamiki.jpg"},
                {"id": 1255387, "name": "Mone Kamishiraishi", "character": "Mitsuha Miyamizu (seslendirme)", "profile_path": "/mone.jpg"}
              ],
              "crew": []
            }
        """.trimIndent()

        val result = TmdbCreditsClient.fetchCredits(
            tmdbId = 372058,
            isMovie = true,
            apiKey = "test-key",
            language = "en-US",
            executeGet = { creditsJson }
        )

        assertEquals(2, result.first.size)
        val taki = result.first[0]
        val mitsuha = result.first[1]
        assertEquals("Taki Tachibana", taki.name)
        assertTrue(taki.isVoiceRole)
        assertNull(taki.imageUrl)
        assertEquals("https://image.tmdb.org/t/p/w185/kamiki.jpg", taki.voiceActors.single().imageUrl)

        assertEquals("Mitsuha Miyamizu", mitsuha.name)
        assertTrue(mitsuha.isVoiceRole)
        assertNull(mitsuha.imageUrl)
        assertEquals("https://image.tmdb.org/t/p/w185/mone.jpg", mitsuha.voiceActors.single().imageUrl)
    }

    @Test
    fun yourNameColdDetailCacheAndAniListOutageStillPopulatesFictionalPortraits() = runBlocking {
        val tmdbCast = listOf(
            KitsugiCharacter(
                id = 225730,
                name = "Taki Tachibana",
                role = "Supporting",
                imageUrl = null,
                source = "tmdb",
                isVoiceRole = true,
                voiceActors = listOf(KitsugiVoiceActor(id = 225730, name = "Ryunosuke Kamiki", language = "Japanese", imageUrl = "https://tmdb/kamiki.jpg", source = "tmdb"))
            ),
            KitsugiCharacter(
                id = 1255387,
                name = "Mitsuha Miyamizu",
                role = "Supporting",
                imageUrl = null,
                source = "tmdb",
                isVoiceRole = true,
                voiceActors = listOf(KitsugiVoiceActor(id = 1255387, name = "Mone Kamishiraishi", language = "Japanese", imageUrl = "https://tmdb/mone.jpg", source = "tmdb"))
            ),
            KitsugiCharacter(
                id = 1492329,
                name = "Katsuhiko Teshigawara",
                role = "Supporting",
                imageUrl = null,
                source = "tmdb",
                isVoiceRole = true,
                voiceActors = listOf(KitsugiVoiceActor(id = 1492329, name = "Ryo Narita", language = "Japanese", imageUrl = "https://tmdb/narita.jpg", source = "tmdb"))
            ),
            KitsugiCharacter(
                id = 555233,
                name = "Sayaka Natori",
                role = "Supporting",
                imageUrl = null,
                source = "tmdb",
                isVoiceRole = true,
                voiceActors = listOf(KitsugiVoiceActor(id = 555233, name = "Aoi Yuuki", language = "Japanese", imageUrl = "https://tmdb/aoi.jpg", source = "tmdb"))
            )
        )

        val deps = KitsugiCharacterClient.TmdbCharacterEnrichmentDeps(
            resolveTmdbAnimeIds = { _, _, _ ->
                TmdbAnimeIdCandidates(malIds = listOf(32281), aniListIds = listOf(21519), kitsuIds = listOf(11614))
            },
            resolveSimklCrossIds = { _, _, _, _ -> null },
            // Simulate total AniList HTTP 403/503 outage!
            fetchAniListMedia = { Pair(emptyList(), null) },
            searchAniListCharacter = { _, _ -> null },
            fetchShikimoriFast = { malId, _ ->
                if (malId == 32281) {
                    listOf(
                        KitsugiCharacter(1, "Tachibana, Taki", "Main", "https://shikimori/taki.jpg", source = "shikimori"),
                        KitsugiCharacter(2, "Miyamizu, Mitsuha", "Main", "https://shikimori/mitsuha.jpg", source = "shikimori"),
                        KitsugiCharacter(3, "Teshigawara, Katsuhiko", "Supporting", "https://shikimori/teshi.jpg", source = "shikimori"),
                        KitsugiCharacter(4, "Natori, Sayaka", "Supporting", "https://shikimori/sayaka.jpg", source = "shikimori")
                    )
                } else emptyList()
            },
            fetchJikanList = { _, _ -> emptyList() },
            searchJikanMalIds = { _, _, _ -> emptyList() },
            fetchKitsuList = { emptyList() },
            fetchBangumiList = { _, _, _ -> emptyList() },
            fetchFandomPortraits = { _, _, _, _ -> emptyMap() },
            searchMultiSourcePortrait = { _, _ -> null }
        )

        val enriched = KitsugiCharacterClient().enrichTmdbCastCharacters(
            tmdbChars = tmdbCast,
            effectiveTmdbId = 372058,
            mediaType = MediaType.Movie,
            mediaDetail = null, // Cold cache race: detail hasn't finished yet!
            titleCandidates = listOf("Senin Adın."),
            deps = deps
        )

        assertEquals(4, enriched.size)
        assertTrue(enriched.none { it.isRealMediaRole })
        assertEquals("https://shikimori/taki.jpg", enriched[0].imageUrl)
        assertEquals("https://shikimori/mitsuha.jpg", enriched[1].imageUrl)
        assertEquals("https://shikimori/teshi.jpg", enriched[2].imageUrl)
        assertEquals("https://shikimori/sayaka.jpg", enriched[3].imageUrl)
        // Seiyuu headshots preserved untouched!
        assertEquals("https://tmdb/kamiki.jpg", enriched[0].voiceActors.single().imageUrl)
        assertEquals("https://tmdb/mone.jpg", enriched[1].voiceActors.single().imageUrl)
    }

    @Test
    fun tmdbTvSeriesReplacesDuplicateVoiceActorHeadshotsWithFictionalPortraits() = runBlocking {
        // On TMDB TV entries without "(voice)", TmdbCreditsClient initially sets char.imageUrl = actorImageUrl.
        val seiyuuUrl = "https://image.tmdb.org/t/p/w185/wasabi.jpg"
        val tvCast = listOf(
            KitsugiCharacter(
                id = 9001,
                name = "Doraemon",
                role = "Supporting",
                imageUrl = seiyuuUrl,
                source = "tmdb",
                isVoiceRole = false,
                voiceActors = listOf(KitsugiVoiceActor(id = 9001, name = "Wasabi Mizuta", language = "Japanese", imageUrl = seiyuuUrl, source = "tmdb"))
            ),
            // Same seiyuu voicing a second character (personId collision regression check!)
            KitsugiCharacter(
                id = 9001,
                name = "Mini-Dora",
                role = "Supporting",
                imageUrl = seiyuuUrl,
                source = "tmdb",
                isVoiceRole = false,
                voiceActors = listOf(KitsugiVoiceActor(id = 9001, name = "Wasabi Mizuta", language = "Japanese", imageUrl = seiyuuUrl, source = "tmdb"))
            )
        )

        val detail = KitsugiMediaDetail(
            realMalId = 57911,
            title = "Doraemon",
            synopsis = null,
            imageUrl = null,
            score = 8,
            status = null,
            year = 2005,
            type = MediaType.TvShow,
            genres = listOf("Animasyon", "Komedi")
        )

        val deps = KitsugiCharacterClient.TmdbCharacterEnrichmentDeps(
            resolveTmdbAnimeIds = { _, _, _ -> TmdbAnimeIdCandidates() },
            resolveSimklCrossIds = { _, _, _, _ -> null },
            fetchAniListMedia = { Pair(emptyList(), null) },
            searchAniListCharacter = { _, _ -> null },
            fetchShikimoriFast = { _, _ -> emptyList() },
            fetchJikanList = { _, _ -> emptyList() },
            searchJikanMalIds = { _, _, _ -> emptyList() },
            fetchKitsuList = { emptyList() },
            fetchBangumiList = { _, _, _ -> emptyList() },
            fetchFandomPortraits = { _, _, _, _ -> emptyMap() },
            searchMultiSourcePortrait = { name, _ ->
                when (name) {
                    "Doraemon" -> "https://myanimelist/doraemon.jpg"
                    "Mini-Dora" -> "https://myanimelist/minidora.jpg"
                    else -> null
                }
            }
        )

        val enriched = KitsugiCharacterClient().enrichTmdbCastCharacters(
            tmdbChars = tvCast,
            effectiveTmdbId = 57911,
            mediaType = MediaType.TvShow,
            mediaDetail = detail,
            titleCandidates = listOf("Doraemon"),
            deps = deps
        )

        assertEquals("https://myanimelist/doraemon.jpg", enriched[0].imageUrl)
        assertEquals("https://myanimelist/minidora.jpg", enriched[1].imageUrl)
        assertEquals(seiyuuUrl, enriched[0].voiceActors.single().imageUrl)
        assertEquals(seiyuuUrl, enriched[1].voiceActors.single().imageUrl)
    }

    @Test
    fun franchiseMovieAniListCharacterSearchDoesNotRejectMainCastOutsideTopPopularMedia() {
        val doraemonSearchResponse = """
            {
              "data": {
                "Page": {
                  "characters": [
                    {
                      "id": 2469,
                      "name": {
                        "full": "Doraemon",
                        "native": "ドラえもん",
                        "alternative": ["MS-903"]
                      },
                      "image": {
                        "large": "https://s4.anilist.co/file/anilistcdn/character/large/b2469.png",
                        "medium": "https://s4.anilist.co/file/anilistcdn/character/medium/b2469.png"
                      },
                      "media": {
                        "nodes": [
                          {"id": 2471, "idMal": 2471, "type": "ANIME"},
                          {"id": 8687, "idMal": 8687, "type": "ANIME"}
                        ]
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        // Specific Doraemon movie MAL ID 54925 is NOT in Doraemon's top popular media list:
        // must still return Doraemon instead of dropping him to "DO" initials!
        val hit = KitsugiCharacterClient().parseBestAniListCharacterSearchHit(
            response = doraemonSearchResponse,
            name = "Doraemon",
            realMalId = 54925
        )
        assertNotNull(hit)
        assertEquals("https://s4.anilist.co/file/anilistcdn/character/large/b2469.png", hit?.imageUrl)
    }

    @Test
    fun detailCachePropagateCharacterPortraitUpdatesExistingMediaCharactersLive() {
        DetailCache.putMediaCharacters(
            source = "tmdb",
            id = 372058,
            list = listOf(
                KitsugiCharacter(
                    id = 225730,
                    name = "Taki Tachibana",
                    role = "Supporting",
                    imageUrl = null,
                    source = "tmdb",
                    isVoiceRole = true,
                    voiceActors = listOf(KitsugiVoiceActor(id = 225730, name = "Ryunosuke Kamiki", language = "Japanese", imageUrl = "https://tmdb/kamiki.jpg", source = "tmdb"))
                )
            )
        )
        val beforeVersion = DetailCache.characterPortraitVersion.value

        DetailCache.propagateCharacterPortrait("Taki Tachibana", "https://jikan/taki_portrait.jpg")

        assertTrue(DetailCache.characterPortraitVersion.value > beforeVersion)
        val updated = DetailCache.getMediaCharacters("tmdb", 372058)?.single()
        assertEquals("https://jikan/taki_portrait.jpg", updated?.imageUrl)
        assertEquals(false, updated?.isRealMediaRole)
    }

    @Test
    fun detailCachePropagateCharacterPortraitRejectsVoiceActorPhotoAcrossTmdbSizes() {
        val actorW185 = "https://image.tmdb.org/t/p/w185/castellaneta.jpg"
        val actorW300 = "https://image.tmdb.org/t/p/w300/castellaneta.jpg"
        val homerPortrait = "https://upload.wikimedia.org/wikipedia/en/0/02/Homer_Simpson_2006.png"

        DetailCache.putMediaCharacters(
            source = "simkl",
            id = 700_045_600,
            list = listOf(
                KitsugiCharacter(
                    id = 198,
                    name = "Homer Simpson / Abe Simpson / Barney Gumble / Krusty the Clown",
                    role = "Ana Karakter",
                    imageUrl = null,
                    source = "tmdb",
                    isVoiceRole = true,
                    isRealMediaRole = false,
                    voiceActors = listOf(
                        KitsugiVoiceActor(
                            id = 198,
                            name = "Dan Castellaneta",
                            language = "Oyuncu",
                            imageUrl = actorW185,
                            source = "tmdb"
                        )
                    )
                )
            )
        )

        // Attempting to propagate the actor's w300 photo must be rejected!
        DetailCache.propagateCharacterPortrait(
            "Homer Simpson / Abe Simpson / Barney Gumble / Krusty the Clown",
            actorW300
        )
        val afterActorAttempt = DetailCache.getMediaCharacters("simkl", 700_045_600)?.single()
        assertNull(afterActorAttempt?.imageUrl)
        assertNull(DetailCache.getCachedCharacterPortrait("Homer Simpson"))

        // Propagating the actual character portrait by primary name updates the multi-role row!
        DetailCache.propagateCharacterPortrait("Homer Simpson", homerPortrait)
        val afterRealPortrait = DetailCache.getMediaCharacters("simkl", 700_045_600)?.single()
        assertEquals(homerPortrait, afterRealPortrait?.imageUrl)
        assertEquals(
            homerPortrait,
            DetailCache.getCachedCharacterPortrait("Homer Simpson / Abe Simpson / Barney Gumble / Krusty the Clown")
        )
    }
}
