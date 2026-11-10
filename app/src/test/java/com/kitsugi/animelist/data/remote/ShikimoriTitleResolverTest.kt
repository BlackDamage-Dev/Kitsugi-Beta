package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShikimoriTitleResolverTest {

    @Test
    fun graphQlQueryRequestsEnglishAndJapaneseTitlesForAnimeAndManga() {
        assertEquals(
            "{ animes(ids: \"8,9\", limit: 2, censored: false) { id english japanese } }",
            ShikimoriTitleResolver.buildQuery(ShikimoriPosterResolver.Kind.ANIME, listOf(8, 9))
        )
        assertEquals(
            "{ mangas(ids: \"17\", limit: 1, censored: false) { id english japanese } }",
            ShikimoriTitleResolver.buildQuery(ShikimoriPosterResolver.Kind.MANGA, listOf(17))
        )
    }

    @Test
    fun parseResponseReadsStringAndArrayTitleVariants() {
        val body = """
            {"data":{"animes":[
              {"id":"8","english":"Kaiju No. 8","japanese":"怪獣8号"},
              {"id":9,"english":["English title",""],"japanese":["", "日本語タイトル"]}
            ]}}
        """.trimIndent()

        val parsed = ShikimoriTitleResolver.parseResponse(body, ShikimoriPosterResolver.Kind.ANIME)

        assertEquals(ShikimoriTitleResolver.Names("Kaiju No. 8", "怪獣8号"), parsed?.get(8))
        assertEquals(ShikimoriTitleResolver.Names("English title", "日本語タイトル"), parsed?.get(9))
        assertNull(ShikimoriTitleResolver.parseResponse("{}", ShikimoriPosterResolver.Kind.ANIME))
    }

    @Test
    fun searchResultsDisplayResolvedTitleAccordingToUserPreference() {
        val raw = JikanSearchResult(
            malId = 8, // Shikimori'nin kendi ID'si; realMalId ayrı tutulur.
            title = "Kaijuu 8-gou",
            subtitle = "TV",
            type = MediaType.Anime,
            total = 12,
            score = 8,
            isAdult = false,
            imageUrl = null,
            year = 2024,
            source = "shikimori",
            realMalId = 52588,
            titleRomaji = "Kaijuu 8-gou"
        )

        val enriched = ShikimoriTitleResolver.applyToSearchResults(
            listOf(raw),
            mapOf(8 to ShikimoriTitleResolver.Names("Kaiju No. 8", "怪獣8号"))
        ).single()

        assertEquals("Kaiju No. 8", enriched.getDisplayTitle("ENGLISH"))
        assertEquals("Kaijuu 8-gou", enriched.getDisplayTitle("ROMAJI"))
        assertEquals("怪獣8号", enriched.getDisplayTitle("NATIVE"))
        assertEquals(8, enriched.malId)
        assertEquals(52588, enriched.realMalId)
    }

    @Test
    fun unresolvedTitlesKeepRomajiFallbackInsteadOfClaimingItIsEnglish() {
        val raw = JikanSearchResult(
            malId = 114,
            title = "Kaijuu 8-gou",
            subtitle = "TV",
            type = MediaType.Anime,
            total = null,
            score = null,
            isAdult = false,
            imageUrl = null,
            year = null,
            source = "shikimori",
            titleRomaji = "Kaijuu 8-gou"
        )

        val unresolved = ShikimoriTitleResolver.applyToSearchResults(
            listOf(raw),
            mapOf(114 to ShikimoriTitleResolver.Names())
        ).single()

        assertNull(unresolved.titleEnglish)
        assertEquals("Kaijuu 8-gou", unresolved.getDisplayTitle("ENGLISH"))
        assertEquals("Kaijuu 8-gou", unresolved.getDisplayTitle("ROMAJI"))
    }
}
