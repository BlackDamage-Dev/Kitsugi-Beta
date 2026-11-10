package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioSubtitleRoutingTest {
    @Test
    fun shortSubtitleResourceInheritsManifestTypesAndPrefixes() {
        val spec = StremioSubtitleRouting.parseSubtitleResource(
            resources = listOf("stream", "subtitles"),
            manifestTypes = listOf("movie", "series"),
            manifestIdPrefixes = listOf("tt")
        )
        assertEquals(listOf("movie", "series"), spec?.types)
        assertEquals(listOf("tt"), spec?.idPrefixes)
    }

    @Test
    fun objectSubtitleResourceUsesItsOwnPrefixesAndTypes() {
        val spec = StremioSubtitleRouting.parseSubtitleResource(
            resources = listOf(mapOf("name" to "subtitles", "types" to listOf("series"), "idPrefixes" to listOf("kitsu"))),
            manifestTypes = listOf("movie"),
            manifestIdPrefixes = listOf("tt")
        )
        assertEquals(listOf("series"), spec?.types)
        assertEquals(listOf("kitsu"), spec?.idPrefixes)
    }

    @Test
    fun objectWithoutPrefixesDoesNotAccidentallyInheritTopLevelStreamIdPrefix() {
        val spec = StremioSubtitleRouting.parseSubtitleResource(
            resources = listOf(mapOf("name" to "subtitles", "types" to listOf("movie", "series"))),
            manifestTypes = listOf("movie"),
            manifestIdPrefixes = listOf("tt")
        )
        assertNull(spec?.idPrefixes)
    }

    @Test
    fun noSubtitleResourceReturnsNull() {
        assertNull(StremioSubtitleRouting.parseSubtitleResource(listOf("stream"), listOf("movie"), listOf("tt")))
    }

    @Test
    fun prefixSpecificAddonReceivesOnlyMatchingProviderIdentity() {
        val identities = SubtitleIdentityCandidates.build(
            movie = false, season = 2, episode = 4,
            imdbId = "tt1234567", kitsuId = 77, aniListId = 88, malId = 99, tmdbId = 123
        )
        val routes = listOf(
            StremioSubtitleRoute("IMDb-only", "https://imdb/manifest.json", "movie,series", listOf("tt")),
            StremioSubtitleRoute("Kitsu-only", "https://kitsu/manifest.json", "anime", listOf("kitsu:")),
            StremioSubtitleRoute("MAL-only", "https://mal/manifest.json", "series", listOf("myanimelist:")),
            StremioSubtitleRoute("wrong type", "https://movie/manifest.json", "movie", listOf("tt"))
        )
        val tasks = StremioSubtitleRouting.buildTasks(routes, "series", identities)
        assertEquals(3, tasks.size)
        assertEquals("tt1234567:2:4", tasks.single { it.addonName == "IMDb-only" }.videoId)
        assertEquals("anime", tasks.single { it.addonName == "Kitsu-only" }.requestType)
        assertEquals("myanimelist:99:4", tasks.single { it.addonName == "MAL-only" }.videoId)
        assertTrue(tasks.none { it.addonName == "wrong type" })
    }

    @Test
    fun unrestrictedManifestCanReceiveEveryVerifiedIdentityCandidate() {
        val identities = SubtitleIdentityCandidates.build(
            movie = true, season = 0, episode = 1,
            imdbId = "tt1234567", kitsuId = 77, aniListId = 88, malId = 99, tmdbId = 123
        )
        val tasks = StremioSubtitleRouting.buildTasks(
            routes = listOf(StremioSubtitleRoute("generic", "https://example/manifest.json", "movie,series", null)),
            contentType = "movie",
            candidates = identities
        )
        assertEquals(3, tasks.size)
        assertTrue(tasks.any { it.videoId == "tt1234567" })
        assertTrue(tasks.any { it.videoId == "tmdb:123" })
        assertTrue(tasks.any { it.videoId == "kitsu:77" })
        assertTrue(tasks.none {
            it.videoId.startsWith("anilist:") || it.videoId.startsWith("mal:") ||
                it.videoId.startsWith("myanimelist:") || it.videoId.startsWith("simkl:") || it.videoId.startsWith("bgm:")
        })
    }

    @Test
    fun customNamespaceIsUsedOnlyWhenTheAddonDeclaresThatPrefix() {
        val candidates = SubtitleIdentityCandidates.build(
            movie = false, season = 1, episode = 4,
            imdbId = "tt1234567", kitsuId = null, aniListId = null, malId = null, tmdbId = null,
            simklId = 606, bangumiId = 707
        )
        val tasks = StremioSubtitleRouting.buildTasks(
            routes = listOf(StremioSubtitleRoute("Simkl Sub", "https://simkl/manifest.json", "series", listOf("simkl:"))),
            contentType = "series",
            candidates = candidates
        )
        assertEquals(listOf("simkl:606:1:4"), tasks.map { it.videoId })
    }

    @Test
    fun animeIdsUseProviderAppropriateEpisodeShapes() {
        val candidates = SubtitleIdentityCandidates.build(
            movie = false, season = 2, episode = 4,
            imdbId = "tt1234567", kitsuId = 77, aniListId = 88, malId = 99, tmdbId = 123
        )
        assertEquals("tt1234567:2:4", candidates.first { it.id.startsWith("tt") }.id)
        assertEquals("kitsu:77:4", candidates.first { it.id.startsWith("kitsu:") }.id)
        assertEquals("anilist:88:4", candidates.first { it.id.startsWith("anilist:") }.id)
        assertEquals("mal:99:4", candidates.first { it.id.startsWith("mal:") }.id)
        assertEquals("tmdb:123:2:4", candidates.first { it.id.startsWith("tmdb:") }.id)
    }
}
