package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioStreamRoutingTest {

    // ── İstek tipi: addon'un beyan ettiği tür kullanılır ─────────────────────

    @Test
    fun animeOnlyAddonIsQueriedWithAnimeTypeNotSeries() {
        // AIOStreams #930: /stream/anime/... çalışır, /stream/series/... boş döner.
        assertEquals("anime", StremioStreamRouting.resolveRequestType("anime", "series"))
    }

    @Test
    fun movieRequestFallsBackToAnimeMovieOrAnimeWhenAddonDeclaresOnlyThose() {
        assertEquals("animemovie", StremioStreamRouting.resolveRequestType("animemovie", "movie"))
        assertEquals("anime", StremioStreamRouting.resolveRequestType("anime", "movie"))
    }

    @Test
    fun exactTypeWinsWhenDeclared() {
        assertEquals("series", StremioStreamRouting.resolveRequestType("movie,series,anime", "series"))
        assertEquals("movie", StremioStreamRouting.resolveRequestType("movie,series,anime", "movie"))
    }

    @Test
    fun addonWithoutMatchingTypeIsRejected() {
        assertNull(StremioStreamRouting.resolveRequestType("movie", "series"))
    }

    @Test
    fun undeclaredOrAnyTypeAcceptsContentType() {
        assertEquals("series", StremioStreamRouting.resolveRequestType(null, "series"))
        assertEquals("series", StremioStreamRouting.resolveRequestType("", "series"))
        assertEquals("movie", StremioStreamRouting.resolveRequestType(StremioStreamRouting.ANY_TYPE, "movie"))
    }

    // ── Manifest ayrıştırma: kısa ve tam resource biçimleri ─────────────────

    @Test
    fun shortStreamResourceInheritsManifestTypesAndPrefixes() {
        val spec = StremioStreamRouting.parseStreamResource(
            resources = listOf("catalog", "stream", "subtitles"),
            manifestTypes = listOf("movie", "series"),
            manifestIdPrefixes = listOf("tt")
        )
        assertEquals(listOf("movie", "series"), spec?.types)
        assertEquals(listOf("tt"), spec?.idPrefixes)
    }

    @Test
    fun fullStreamResourceOverridesManifestLevelTypesAndPrefixes() {
        // Torrentio tarzı: tür ve prefix stream resource'una özgü.
        val resources: List<Any> = listOf(
            "catalog",
            mapOf(
                "name" to "stream",
                "types" to listOf("movie", "series", "anime"),
                "idPrefixes" to listOf("tt", "kitsu")
            )
        )
        val spec = StremioStreamRouting.parseStreamResource(
            resources = resources,
            manifestTypes = listOf("movie"),
            manifestIdPrefixes = listOf("tt")
        )
        assertEquals(listOf("movie", "series", "anime"), spec?.types)
        assertEquals(listOf("tt", "kitsu"), spec?.idPrefixes)
    }

    @Test
    fun fullStreamResourceWithoutOwnPrefixesDoesNotInheritTopLevelIdFilter() {
        val resources: List<Any> = listOf(mapOf("name" to "stream"))
        val spec = StremioStreamRouting.parseStreamResource(
            resources = resources,
            manifestTypes = listOf("series"),
            manifestIdPrefixes = listOf("kitsu:")
        )
        assertEquals(listOf("series"), spec?.types)
        assertNull(spec?.idPrefixes)
    }

    @Test
    fun manifestWithoutStreamResourceHasNoStreamSpec() {
        val spec = StremioStreamRouting.parseStreamResource(
            resources = listOf("catalog", "meta", "subtitles"),
            manifestTypes = listOf("anime"),
            manifestIdPrefixes = listOf("kitsu")
        )
        assertNull(spec)
    }

    @Test
    fun bareStreamWithoutAnyTypesYieldsNullTypes() {
        val spec = StremioStreamRouting.parseStreamResource(
            resources = listOf("stream"),
            manifestTypes = emptyList(),
            manifestIdPrefixes = emptyList()
        )
        assertNull(spec?.types)
        assertNull(spec?.idPrefixes)
    }

    // ── Prefix eşleşmesi: "kitsu" ile "kitsu:" aynı aile ────────────────────

    @Test
    fun barePrefixKitsuMatchesKitsuVideoIds() {
        assertTrue(StremioStreamRouting.idPrefixAccepts(listOf("kitsu"), "kitsu:42:3"))
        assertTrue(StremioStreamRouting.idPrefixAccepts(listOf("kitsu:"), "kitsu:42:3"))
    }

    @Test
    fun emptyPrefixListAcceptsEveryId() {
        assertTrue(StremioStreamRouting.idPrefixAccepts(emptyList(), "tt1234567:1:2"))
    }

    @Test
    fun prefixListRejectsForeignIds() {
        assertTrue(!StremioStreamRouting.idPrefixAccepts(listOf("tt"), "kitsu:42:3"))
    }

    // ── Görev üretimi ───────────────────────────────────────────────────────

    private val torrentioLike = StremioAddonRoute(
        name = "Torrentio",
        manifestUrl = "https://torrentio.example/manifest.json",
        streamTypes = "movie,series,anime",
        idPrefixes = listOf("tt", "kitsu"),
        subtitleTypes = null
    )

    @Test
    fun torrentioLikeAddonGetsBothKitsuAndImdbTasksForSeries() {
        // Önceki kod yalnızca "kitsu:" (iki nokta üst üste) arıyordu; "kitsu" olan addon kaçıyordu.
        val tasks = buildStremioStreamTasks(
            routes = listOf(torrentioLike),
            contentType = "series",
            imdbVideoId = "tt1234567:1:5",
            kitsuVideoId = "kitsu:42:5"
        )
        assertEquals(
            listOf(
                StremioStreamTask("Torrentio", torrentioLike.manifestUrl, "series", "kitsu:42:5"),
                StremioStreamTask("Torrentio", torrentioLike.manifestUrl, "series", "tt1234567:1:5")
            ),
            tasks
        )
    }

    @Test
    fun manifestCanRouteTmdbAnilistAndMalIdsIndependently() {
        val routes = listOf(
            torrentioLike.copy(name = "TMDB", idPrefixes = listOf("tmdb:")),
            torrentioLike.copy(name = "AniList", idPrefixes = listOf("anilist:")),
            torrentioLike.copy(name = "MAL", idPrefixes = listOf("myanimelist:"))
        )
        val tasks = buildStremioStreamTasks(
            routes = routes,
            contentType = "series",
            imdbVideoId = "tt1234567:2:8",
            kitsuVideoId = "kitsu:42:8",
            additionalVideoIds = listOf("tmdb:101:2:8", "anilist:202:8", "myanimelist:303:8")
        )
        assertEquals(
            listOf("tmdb:101:2:8", "anilist:202:8", "myanimelist:303:8"),
            tasks.map { it.videoId }
        )
    }

    @Test
    fun animeOnlyKitsuAddonGetsTaskWithAnimeRequestType() {
        val kitsuAddon = StremioAddonRoute(
            name = "Kitsu Stream",
            manifestUrl = "https://kitsu.example/manifest.json",
            streamTypes = "anime",
            idPrefixes = listOf("kitsu"),
            subtitleTypes = null
        )
        val tasks = buildStremioStreamTasks(
            routes = listOf(kitsuAddon),
            contentType = "series",
            imdbVideoId = "tt1234567:1:5",
            kitsuVideoId = "kitsu:42:5"
        )
        assertEquals(
            listOf(StremioStreamTask("Kitsu Stream", kitsuAddon.manifestUrl, "anime", "kitsu:42:5")),
            tasks
        )
    }

    @Test
    fun imdbOnlyAddonIsNotQueriedWithKitsuId() {
        val imdbAddon = torrentioLike.copy(name = "ImdbOnly", idPrefixes = listOf("tt"))
        val tasks = buildStremioStreamTasks(
            routes = listOf(imdbAddon),
            contentType = "movie",
            imdbVideoId = "tt1234567",
            kitsuVideoId = "kitsu:42"
        )
        assertEquals(listOf("tt1234567"), tasks.map { it.videoId })
    }

    @Test
    fun unrestrictedStreamAddonDoesNotReceiveUnadvertisedCustomIdentityFamilies() {
        val addon = StremioAddonRoute(
            name = "NoPrefix",
            manifestUrl = "https://no-prefix.example/manifest.json",
            streamTypes = "series",
            idPrefixes = emptyList(),
            subtitleTypes = null
        )
        val tasks = buildStremioStreamTasks(
            routes = listOf(addon),
            contentType = "series",
            imdbVideoId = "tt1234567:1:5",
            kitsuVideoId = "kitsu:42:5",
            additionalVideoIds = listOf("tmdb:101:1:5", "anilist:202:5", "simkl:303:1:5")
        )
        assertEquals(listOf("kitsu:42:5", "tt1234567:1:5"), tasks.map { it.videoId })
    }

    @Test
    fun subtitleOnlyLegacyRowIsNotQueriedForStreams() {
        val subtitleOnly = StremioAddonRoute(
            name = "Subs",
            manifestUrl = "https://subs.example/manifest.json",
            streamTypes = null,
            idPrefixes = emptyList(),
            subtitleTypes = "movie,series"
        )
        val tasks = buildStremioStreamTasks(
            routes = listOf(subtitleOnly),
            contentType = "series",
            imdbVideoId = "tt1234567:1:5",
            kitsuVideoId = null
        )
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun prefixlessAddonGetsEveryAvailableIdWithContentType() {
        val open = StremioAddonRoute(
            name = "Open",
            manifestUrl = "https://open.example/manifest.json",
            streamTypes = null,
            idPrefixes = emptyList(),
            subtitleTypes = null
        )
        val tasks = buildStremioStreamTasks(
            routes = listOf(open),
            contentType = "movie",
            imdbVideoId = "tt1234567",
            kitsuVideoId = "kitsu:42"
        )
        assertEquals(listOf("movie", "movie"), tasks.map { it.requestType })
        assertEquals(listOf("kitsu:42", "tt1234567"), tasks.map { it.videoId })
    }
}
