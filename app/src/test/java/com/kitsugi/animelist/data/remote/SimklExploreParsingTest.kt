package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simkl Keşfet / En İyiler (`SimklApiClient.getBestMedia` → [parseBestMediaArray])
 * yanıt çözümlemesi.
 *
 * Vitrin logo araması, şerit kartları ve detay eşlemesi bu kimliklere dayanır;
 * Simkl kimlikleri uç noktaya göre `simkl_id`/`simkl`, `tmdb`/`tmdb_id` ve
 * sayı ya da metin `mal` olarak gelebildiği için hepsi okunmalıdır. Eksik
 * okunduklarında Simkl içerikleri vitrinde logosuz (düz metin başlık) kalıyordu.
 */
class SimklExploreParsingTest {

    private val fixture = """
    [
      {
        "title": "Sousou no Frieren",
        "year": 2023,
        "poster": "frieren-poster",
        "fanart": "frieren-fanart",
        "anime_type": "tv",
        "ids": { "simkl_id": 1234567, "mal": "52991", "tmdb": 209867 },
        "ratings": { "simkl": { "rating": 9.2 } }
      },
      {
        "title": "Re:Zero kara Hajimeru Isekai Seikatsu",
        "year": 2016,
        "poster": "rezero-poster",
        "anime_type": "tv",
        "ids": { "simkl": 987654, "mal": 31240, "tmdb_id": 65930 },
        "ratings": { "mal": { "rating": 8.7 } }
      },
      {
        "title": "Kimi no Na wa.",
        "year": 2016,
        "poster": "movie-poster",
        "anime_type": "movie",
        "ids": { "simkl_id": 445566 },
        "ratings": { "simkl": { "rating": 9.0 } }
      },
      {
        "title": "Kimliksiz Kayit",
        "year": 2020
      }
    ]
    """.trimIndent()

    @Test fun readsEverySimklIdentityKeyVariant() {
        val items = parseBestMediaArray(fixture, MediaType.Anime, 20)
        assertEquals(4, items.size)

        // `simkl_id` + metin `mal` + `tmdb`
        val frieren = items[0]
        assertEquals(1234567, frieren.malId)
        assertEquals(52991, frieren.realMalId)
        assertEquals(209867, frieren.tmdbId)

        // `simkl` (kısa anahtar) + sayı `mal` + `tmdb_id`
        val reZero = items[1]
        assertEquals(987654, reZero.malId)
        assertEquals(31240, reZero.realMalId)
        assertEquals(65930, reZero.tmdbId)
    }

    @Test fun missingIdsNeverBecomeMalOrTmdbIdentities() {
        val items = parseBestMediaArray(fixture, MediaType.Anime, 20)
        val noIds = items[3]
        assertTrue(noIds.malId > 0)
        assertNull(noIds.realMalId)
        assertNull(noIds.tmdbId)
    }

    @Test fun animeMovieKeepsMovieHintAndSimklRating() {
        val items = parseBestMediaArray(fixture, MediaType.Anime, 20)
        val movie = items[2]
        assertTrue(movie.subtitle.contains("Film"))
        assertEquals(90, movie.score)
    }

    @Test fun cjkRawTitleIsReplacedByLatinAlternative() {
        val items = parseBestMediaArray(
            """[{"title":"葬送のフリーレン","title_en":"Frieren: Beyond Journey's End","year":2023,"ids":{"simkl_id":1}}]""",
            MediaType.Anime,
            20
        )
        assertEquals("Frieren: Beyond Journey's End", items[0].title)
        assertEquals("葬送のフリーレン", items[0].titleJapanese)
    }

    @Test fun englishTitleIsPreferredWithoutDestroyingRomajiAndNativeTitles() {
        val items = parseBestMediaArray(
            """
            [{
              "title": "Kaijuu 8 Gou",
              "en_title": "Kaiju No. 8",
              "title_romaji": "Kaijuu 8-gou",
              "title_native": "怪獣8号",
              "anime_type": "tv",
              "ids": { "simkl_id": 42 }
            }]
            """.trimIndent(),
            MediaType.Anime,
            20
        )

        val kaiju = items.single()
        assertEquals("Kaiju No. 8", kaiju.title)
        assertEquals("Kaiju No. 8", kaiju.titleEnglish)
        assertEquals("Kaijuu 8-gou", kaiju.titleRomaji)
        assertEquals("怪獣8号", kaiju.titleJapanese)
        assertEquals("Kaiju No. 8", kaiju.getDisplayTitle("ENGLISH"))
        assertEquals("Kaijuu 8-gou", kaiju.getDisplayTitle("ROMAJI"))
        assertEquals("怪獣8号", kaiju.getDisplayTitle("NATIVE"))
    }

    @Test fun latinRawTitleFallsBackToRomajiInsteadOfPretendingToBeEnglish() {
        val titles = resolveSimklTitleVariants(
            org.json.JSONObject(
                """{"title":"Kaijuu 8 Gou","en_title":null,"title_romaji":null,"title_native":"怪獣8号"}"""
            )
        )

        assertEquals("Kaijuu 8 Gou", titles.display)
        assertNull(titles.english)
        assertEquals("Kaijuu 8 Gou", titles.romaji)
        assertEquals("怪獣8号", titles.native)
    }

    @Test fun posterAndFanartBecomeSimklCdnUrls() {
        val items = parseBestMediaArray(fixture, MediaType.Anime, 20)
        assertEquals("https://simkl.in/posters/frieren-poster_m.webp", items[0].imageUrl)
        assertEquals("https://simkl.in/fanart/frieren-fanart_medium.webp", items[0].backdropUrl)
        assertNull(items[3].imageUrl)
    }

    @Test fun tvAndMovieShelvesLabelTheirType() {
        val tv = parseBestMediaArray(fixture, MediaType.TvShow, 20)
        val movie = parseBestMediaArray(fixture, MediaType.Movie, 20)
        assertTrue(tv[0].subtitle.startsWith("Dizi"))
        assertEquals(MediaType.TvShow, tv[0].type)
        assertTrue(movie[0].subtitle.startsWith("Film"))
        assertEquals(MediaType.Movie, movie[0].type)
    }
}
