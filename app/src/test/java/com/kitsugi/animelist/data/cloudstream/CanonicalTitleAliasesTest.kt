package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kanonik başlık evreni: MAL kaydından açılan akışın AniList kaydıyla AYNI başlık
 * kümesiyle aramasını sağlar (AniList + Jikan birleşimi).
 */
class CanonicalTitleAliasesTest {

    @Test
    fun parsesAniListTitlesAndSynonyms() {
        val body = """
            {
              "data": {
                "Media": {
                  "title": {
                    "romaji": "Shingeki no Kyojin",
                    "english": "Attack on Titan",
                    "native": "進撃の巨人"
                  },
                  "synonyms": ["AoT", "SnK"]
                }
              }
            }
        """.trimIndent()

        val titles = CanonicalTitleAliases.parseAniListTitles(body)

        assertTrue(titles.contains("Shingeki no Kyojin"))
        assertTrue(titles.contains("Attack on Titan"))
        assertTrue(titles.contains("進撃の巨人"))
        assertTrue(titles.contains("AoT"))
        assertEquals(titles.size, titles.distinct().size)
    }

    @Test
    fun parsesAllJikanTitleVariants() {
        val body = """
            {
              "data": {
                "title": "Shingeki no Kyojin",
                "title_english": "Attack on Titan",
                "title_japanese": "進撃の巨人",
                "titles": [
                  { "type": "Default", "title": "Shingeki no Kyojin" },
                  { "type": "Synonym", "title": "AoT" },
                  { "type": "Turkish", "title": "Titanların Saldırısı" }
                ]
              }
            }
        """.trimIndent()

        val titles = CanonicalTitleAliases.parseJikanTitles(body)

        assertTrue(titles.contains("Titanların Saldırısı"))
        assertTrue(titles.contains("AoT"))
        assertEquals(titles.size, titles.distinct().size)
    }

    @Test
    fun brokenPayloadsYieldNoAliases() {
        assertEquals(emptyList<String>(), CanonicalTitleAliases.parseAniListTitles("nope"))
        assertEquals(emptyList<String>(), CanonicalTitleAliases.parseAniListTitles("{}"))
        assertEquals(emptyList<String>(), CanonicalTitleAliases.parseJikanTitles("nope"))
        assertEquals(emptyList<String>(), CanonicalTitleAliases.parseJikanTitles("""{"data":{}}"""))
    }
}
