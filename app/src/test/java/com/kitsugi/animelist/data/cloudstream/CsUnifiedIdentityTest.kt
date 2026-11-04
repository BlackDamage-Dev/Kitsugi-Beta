package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsUnifiedIdentityTest {

    @Test
    fun testCanonicalKeyPrefersImdb() {
        val key = CsUnifiedIdentity.canonicalKey(
            imdbId = "tt1234567", tmdbId = 42, malId = 1, title = "X", year = 2020
        )
        assertEquals("imdb:tt1234567", key)
    }

    @Test
    fun testAliasKeysContainEveryKnownNamespace() {
        val keys = CsUnifiedIdentity.aliasKeys(
            imdbId = "tt1234567", tmdbId = 42, malId = 7, aniListId = 9,
            kitsuId = 11, bangumiId = 13, title = "Some Title", year = 2021,
            mediaType = MediaType.Anime
        )
        assertTrue(keys.contains("imdb:tt1234567"))
        assertTrue(keys.contains("tmdb:tv:42"))
        assertTrue(keys.contains("mal:7"))
        assertTrue(keys.contains("anilist:9"))
        assertTrue(keys.contains("kitsu:11"))
        assertTrue(keys.contains("bangumi:13"))
        assertTrue(keys.any { it.startsWith("title:") && it.endsWith(":2021") })
    }

    @Test
    fun testTmdbNamespaceSeparatesMovieAndTv() {
        val movie = CsUnifiedIdentity.aliasKeys(tmdbId = 42, mediaType = MediaType.Movie)
        val tv = CsUnifiedIdentity.aliasKeys(tmdbId = 42, mediaType = MediaType.TvShow)
        assertEquals(listOf("tmdb:movie:42"), movie)
        assertEquals(listOf("tmdb:tv:42"), tv)
    }

    @Test
    fun testInvalidImdbIsIgnored() {
        val keys = CsUnifiedIdentity.aliasKeys(imdbId = "12345", malId = 5)
        assertEquals(listOf("mal:5"), keys)
    }

    @Test
    fun testTitleAliasRequiresYear() {
        val withoutYear = CsUnifiedIdentity.aliasKeys(title = "Some Title", year = null)
        assertTrue(withoutYear.isEmpty())
        val withYear = CsUnifiedIdentity.aliasKeys(title = "Some Title", year = 1999)
        assertEquals(1, withYear.size)
        assertTrue(withYear.first().startsWith("title:"))
    }

    @Test
    fun testNoIdentityReturnsNullCanonicalKey() {
        assertNull(CsUnifiedIdentity.canonicalKey(title = null, year = null))
    }

    @Test
    fun testSameWorkFromDifferentSourcesSharesAlias() {
        // AniList kaynağından: mal+anilist biliniyor.
        val fromAniList = CsUnifiedIdentity.aliasKeys(malId = 100, aniListId = 200, title = "Show", year = 2022)
        // TMDB kaynağından: çözümleyici mal kimliğini de döndürüyor.
        val fromTmdb = CsUnifiedIdentity.aliasKeys(tmdbId = 300, malId = 100, title = "Show", year = 2022, mediaType = MediaType.TvShow)
        assertFalse(fromAniList.intersect(fromTmdb.toSet()).isEmpty())
    }
}
