package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class ShikimoriDetailParserTest {

    @Test
    fun animeMetadataKeepsNativeFieldsAndNormalizesTitles() {
        val nextEpisodeAt = "2026-10-23T15:30:00+03:00"
        val parsed = ShikimoriDetailParser.parse(
            JSONObject(
                """
                {
                  "name": "Tenki no Ko",
                  "russian": "Дитя погоды",
                  "english": ["Weathering with You", null, "Weathering With You"],
                  "japanese": ["天気の子"],
                  "synonyms": ["Tenki no Ko", "Weathering with You"],
                  "license_name_ru": "Дитя погоды. Полное собрание",
                  "kind": "movie",
                  "status": "released",
                  "episodes": 1,
                  "episodes_aired": 0,
                  "aired_on": "2019-07-19",
                  "next_episode_at": "$nextEpisodeAt",
                  "fansubbers": ["Fansub One", "Fansub One"],
                  "fandubbers": ["Dub One"],
                  "licensors": ["Wakanim"],
                  "favoured": true
                }
                """.trimIndent()
            ),
            MediaType.Movie
        )

        assertEquals("Weathering with You", parsed.englishTitle)
        assertEquals("天気の子", parsed.japaneseTitle)
        assertEquals("Дитя погоды", parsed.russianTitle)
        assertEquals("Movie", parsed.format)
        assertEquals("Finished Airing", parsed.status)
        assertEquals("Summer 2019", parsed.season)
        assertEquals("SUMMER", parsed.rawSeason)
        assertEquals(0, parsed.episodesAired)
        assertEquals(listOf("Wakanim"), parsed.licensors)
        assertEquals(listOf("Fansub One"), parsed.fansubbers)
        assertEquals(listOf("Dub One"), parsed.fandubbers)
        assertTrue(parsed.isFavourite)
        assertTrue(parsed.synonyms.contains("Дитя погоды. Полное собрание"))

        val expectedEpoch = OffsetDateTime.parse(nextEpisodeAt).toInstant().epochSecond
        assertEquals("1|$expectedEpoch", parsed.nextAiringEpisode)
    }

    @Test
    fun nativeStatsReadRussianStatusesAndMergeRewatching() {
        val parsed = ShikimoriDetailParser.parse(
            JSONObject(
                """
                {
                  "kind": "tv",
                  "rates_scores_stats": [
                    {"name": 10, "value": 12},
                    {"name": "9", "value": 8},
                    {"name": 9, "value": 2},
                    {"name": 0, "value": 999}
                  ],
                  "rates_statuses_stats": [
                    {"name": "Запланировано", "value": 5},
                    {"name": "Просмотрено", "value": 20},
                    {"name": "Смотрю", "value": 3},
                    {"name": "Пересматриваю", "value": 2},
                    {"name": "Брошено", "value": 1},
                    {"name": "Отложено", "value": 4}
                  ]
                }
                """.trimIndent()
            ),
            MediaType.Anime
        )

        assertEquals(22, parsed.scoredBy)
        assertEquals(35, parsed.members)
        assertEquals(5, parsed.nativeStats.watching)
        assertEquals(20, parsed.nativeStats.completed)
        assertEquals(5, parsed.nativeStats.planned)
        assertEquals(1, parsed.nativeStats.dropped)
        assertEquals(4, parsed.nativeStats.paused)
        assertEquals(
            listOf(KitsugiScoreStat(9, 10), KitsugiScoreStat(10, 12)),
            parsed.nativeStats.scoreDistribution
        )
    }

    @Test
    fun zeroOnlyScoreBucketsAreNotARealDistribution() {
        val parsed = ShikimoriDetailParser.parse(
            JSONObject(
                """{"kind":"tv","rates_scores_stats":[{"name":1,"value":0},{"name":10,"value":0}]}"""
            ),
            MediaType.Anime
        )

        assertTrue(parsed.nativeStats.scoreDistribution.isEmpty())
        assertNull(parsed.scoredBy)
    }

    @Test
    fun nativeStatsReadEnglishMangaStatuses() {
        val parsed = ShikimoriDetailParser.parse(
            JSONObject(
                """
                {
                  "kind": "manga",
                  "rates_statuses_stats": [
                    {"name": "reading", "value": 4},
                    {"name": "rereading", "value": 2},
                    {"name": "plan_to_read", "value": 3},
                    {"name": "on_hold", "value": 1}
                  ]
                }
                """.trimIndent()
            ),
            MediaType.Manga
        )

        assertEquals(6, parsed.nativeStats.watching)
        assertEquals(3, parsed.nativeStats.planned)
        assertEquals(1, parsed.nativeStats.paused)
        assertEquals(10, parsed.members)
    }

    @Test
    fun mangaMetadataUsesMangaSemanticsAndDropsNullTitles() {
        val parsed = ShikimoriDetailParser.parse(
            JSONObject(
                """
                {
                  "name": "Kawaii dake ja Nai Shikimori-san",
                  "english": [null],
                  "japanese": [null],
                  "kind": "manga",
                  "status": "ongoing",
                  "volumes": 20,
                  "chapters": 178,
                  "publishers": [{"id": 1, "name": "Kodansha"}],
                  "licensors": ["Kodansha USA"]
                }
                """.trimIndent()
            ),
            MediaType.Manga
        )

        assertNull(parsed.englishTitle)
        assertNull(parsed.japaneseTitle)
        assertNull(parsed.nextAiringEpisode)
        assertNull(parsed.episodesAired)
        assertEquals("Publishing", parsed.status)
        assertEquals("Manga", parsed.format)
        assertEquals(20, parsed.volumes)
        assertEquals(listOf("Kodansha"), parsed.publishers)
        assertEquals(listOf("Kodansha USA"), parsed.licensors)
        assertTrue(parsed.fansubbers.isEmpty())
        assertTrue(parsed.fandubbers.isEmpty())
    }

    @Test
    fun nextEpisodeDoesNotInventNumberWhenAiredCountIsMissing() {
        val parsed = ShikimoriDetailParser.parse(
            JSONObject("""{"kind":"tv","next_episode_at":"2026-10-23T15:30:00+03:00"}"""),
            MediaType.Anime
        )

        assertTrue(parsed.nextAiringEpisode.orEmpty().startsWith("-1|"))
    }

    @Test
    fun externalLinksExposeLanguageAndSeparateStreamingSites() {
        assertEquals(
            "EN",
            ShikimoriDetailParser.externalLinkLanguage("wikipedia", "https://en.wikipedia.org/wiki/Weathering_with_You")
        )
        assertEquals(
            "TR",
            ShikimoriDetailParser.externalLinkLanguage("wikipedia", "https://tr.wikipedia.org/wiki/Tenki_no_Ko")
        )
        assertNull(ShikimoriDetailParser.externalLinkLanguage("official_site", "https://example.com"))

        assertTrue(ShikimoriDetailParser.isStreamingSite("Ivi"))
        assertTrue(ShikimoriDetailParser.isStreamingSite("Kinopoisk HD"))
        assertTrue(ShikimoriDetailParser.isStreamingSite("Amazon"))
        assertTrue(ShikimoriDetailParser.isStreamingSite("YouTube"))
        assertFalse(ShikimoriDetailParser.isStreamingSite("Kinopoisk"))
        assertFalse(ShikimoriDetailParser.isStreamingSite("Vikipedi"))
    }
}
