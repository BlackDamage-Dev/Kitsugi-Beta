package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Tümü" filtre ↔ kaynak köprüsünün saf mantık testleri.
 *
 * Amaç: filtre sayfasından gelen bir terim yüzünden kaynağa geçersiz parametre
 * gidip HTTP 400 / KTS-COM-1016 üretmesin; filtreyi uygulayamayan kaynak
 * filtresiz popüler sonuç döndürmesin.
 */
class SearchFacetBridgeTest {

    private val fullPlan = AllSourcePlan(
        aniList = true, mal = true, tmdb = true, shikimori = true,
        kitsu = true, simkl = true, bangumi = true
    )

    private fun facets(vararg terms: String) = SearchFacetSnapshot(
        terms = terms.toList(),
        mediaType = MediaType.Anime
    )

    @Test
    fun `official AniList genres go to genre_in and demographics go to tag_in`() {
        val snapshot = facets("Action", "Shounen")
        assertEquals(listOf("Action"), SearchFacetBridge.aniListGenres(snapshot))
        // "Shounen" AniList'te tür değil etikettir; `genre_in`'e gitmez.
        assertTrue(SearchFacetBridge.aniListTags(snapshot).contains("Shounen"))
        assertFalse(SearchTaxonomy.isOfficialApiGenre("Shounen"))
    }

    @Test
    fun `unknown AniList tag is never sent to the API`() {
        val snapshot = facets("Action", "Totally Made Up Tag")
        assertEquals(listOf("Action"), SearchFacetBridge.aniListGenres(snapshot))
        assertTrue(SearchFacetBridge.aniListTags(snapshot).isEmpty())
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.ANILIST, snapshot))
    }

    @Test
    fun `Kitsu categories are mapped to slugs and unknown terms disable the shelf`() {
        val known = facets("Action")
        assertEquals(listOf("action"), SearchFacetBridge.kitsuCategories(known))
        assertTrue(SearchFacetBridge.supportsTerms(SearchSourceEngine.KITSU, known))

        val unknown = facets("Totally Made Up Tag")
        assertTrue(SearchFacetBridge.kitsuCategories(unknown).isEmpty())
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.KITSU, unknown))
    }

    @Test
    fun `unsupported facet does not leak unfiltered results`() {
        // "Battle Royale" yalnızca AniList taksonomisinde var; MAL/TMDB/Simkl vb.
        // bu terimi kendi API'lerinde temsil edemez → rafları kapatılmalı.
        val restricted = SearchFacetBridge.restrict(fullPlan, facets("Battle Royale"))
        assertTrue(restricted.aniList)
        assertFalse(restricted.mal)
        assertFalse(restricted.tmdb)
        assertFalse(restricted.shikimori)
        assertFalse(restricted.kitsu)
        assertFalse(restricted.simkl)
        assertFalse(restricted.bangumi)
        assertEquals(setOf("anilist"), restricted.activePlatformIds())
    }

    @Test
    fun `no facets keeps the scope plan untouched`() {
        val restricted = SearchFacetBridge.restrict(fullPlan, SearchFacetSnapshot())
        assertEquals(fullPlan, restricted)
        assertEquals(7, restricted.activePlatformIds().size)
    }

    @Test
    fun `season facet closes providers that only support a year`() {
        val snapshot = SearchFacetSnapshot(season = "WINTER", startYear = 2026)
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.TMDB, snapshot))
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.SIMKL, snapshot))
        assertTrue(SearchFacetBridge.supportsTerms(SearchSourceEngine.ANILIST, snapshot))
    }

    @Test
    fun `producer id namespace is restricted to Jikan MAL`() {
        val snapshot = SearchFacetSnapshot(producerId = 42)
        assertTrue(SearchFacetBridge.supportsTerms(SearchSourceEngine.MAL, snapshot))
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.ANILIST, snapshot))
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.SHIKIMORI, snapshot))
    }

    @Test
    fun `text query plus facets disables TMDB and Simkl instead of returning unfiltered results`() {
        val snapshot = SearchFacetSnapshot(
            terms = listOf("Action"),
            hasQuery = true,
            mediaType = MediaType.Anime
        )
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.TMDB, snapshot))
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.SIMKL, snapshot))
        assertTrue(SearchFacetBridge.supportsTerms(SearchSourceEngine.ANILIST, snapshot))
    }

    @Test
    fun `Bangumi tags are translated to native wiki labels, never ASCII keys`() {
        val native = SearchFacetBridge.mapBangumiTag("Isekai")
        assertTrue(!native.isNullOrBlank())
        assertTrue(native != "isekai")
        assertTrue(SearchFacetBridge.mapBangumiTag("Totally Made Up Tag") == null)
    }

    @Test
    fun `unknown excluded term never silently becomes an unfiltered shelf`() {
        val snapshot = SearchFacetSnapshot(
            excludedTerms = listOf("Totally Made Up Tag"),
            mediaType = MediaType.Anime
        )
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.ANILIST, snapshot))
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.MAL, snapshot))
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.TMDB, snapshot))
        assertFalse(SearchFacetBridge.supportsTerms(SearchSourceEngine.SHIKIMORI, snapshot))
    }

    @Test
    fun `engine specific filter plus a global facet keeps other supporting shelves`() {
        val snapshot = SearchFacetSnapshot(
            terms = listOf("Action"),
            engineSpecificActive = mapOf(SearchSourceEngine.MAL to 1),
            mediaType = MediaType.Anime
        )
        val restricted = SearchFacetBridge.restrict(fullPlan, snapshot)
        // Global "Action" her kaynağa çevrilebildiği için yalnızca MAL'a özel demet
        // diğer rafları kapatmaz; kaynağa özel demet yine de sadece MAL'a uygulanır.
        assertTrue(restricted.aniList)
        assertTrue(restricted.mal)
        assertTrue(restricted.tmdb)
        assertTrue(restricted.shikimori)
        assertTrue(restricted.kitsu)
        assertTrue(restricted.simkl)
        assertTrue(restricted.bangumi)
    }

    @Test
    fun `engine specific filter without global facets closes unfiltered shelves`() {
        val snapshot = SearchFacetSnapshot(
            engineSpecificActive = mapOf(SearchSourceEngine.MAL to 1),
            mediaType = MediaType.Anime
        )
        val restricted = SearchFacetBridge.restrict(fullPlan, snapshot)
        assertTrue(restricted.mal)
        assertFalse(restricted.aniList)
        assertFalse(restricted.tmdb)
        assertFalse(restricted.shikimori)
        assertFalse(restricted.kitsu)
        assertFalse(restricted.simkl)
        assertFalse(restricted.bangumi)
    }

    @Test
    fun `detail request facet count counts logical facets not provider translations`() {
        val request = DetailSearchFilterRequest(
            source = "anilist",
            mediaType = MediaType.Anime,
            genre = "Action",
            tag = "Isekai"
        )
        assertEquals(2, request.activeFacetCount)
    }
}
