package com.kitsugi.animelist.ui.screens.explore.mobile

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.ExplorePayload
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import org.junit.Assert.*
import org.junit.Test

class MobileExploreMappingTest {
    private fun item(
        source: String,
        id: Int,
        type: MediaType,
        adult: Boolean = false,
        subject: Int? = null,
    ) = JikanSearchResult(
        malId = id, title = "Title $id", subtitle = "", type = type, total = null,
        score = null, isAdult = adult, imageUrl = null, year = null, source = source,
        bangumiSubjectType = subject,
    )

    private fun payload() = ExplorePayload(
        topAnime = emptyList(), airingAnime = emptyList(), upcomingAnime = emptyList(),
        topManga = emptyList(), publishingManga = emptyList(), trendingAnime = emptyList(),
        movieAnime = emptyList(), seasonalAnime = emptyList(),
    )

    private fun map(
        source: ExplorePlatform, data: ExplorePayload, adult: Boolean = false,
        separateNovels: Boolean = true, fallback: Boolean = false,
    ) = mobileExploreCatalogSections(source, data, adult, separateNovels, fallback)

    @Test fun tabOrderMatchesVisualContract() {
        assertEquals(listOf(MobileExploreTab.SERIES, MobileExploreTab.MOVIES, MobileExploreTab.ANIME, MobileExploreTab.MANGA), MobileExploreTab.entries)
    }

    @Test fun sourceAndTabCapabilitiesCoverAll32Combinations() {
        val expected = mapOf(
            ExplorePlatform.ALL to setOf(MobileExploreTab.SERIES, MobileExploreTab.MOVIES, MobileExploreTab.ANIME, MobileExploreTab.MANGA),
            ExplorePlatform.AniList to setOf(MobileExploreTab.ANIME, MobileExploreTab.MANGA),
            ExplorePlatform.MAL to setOf(MobileExploreTab.ANIME, MobileExploreTab.MANGA),
            ExplorePlatform.TMDB to setOf(MobileExploreTab.SERIES, MobileExploreTab.MOVIES, MobileExploreTab.ANIME),
            ExplorePlatform.SIMKL to setOf(MobileExploreTab.SERIES, MobileExploreTab.MOVIES, MobileExploreTab.ANIME),
            ExplorePlatform.KITSU to setOf(MobileExploreTab.ANIME, MobileExploreTab.MANGA),
            ExplorePlatform.SHIKIMORI to setOf(MobileExploreTab.ANIME, MobileExploreTab.MANGA),
            ExplorePlatform.BANGUMI to MobileExploreTab.entries.toSet(),
        )
        assertEquals(8, expected.size)
        for (platform in ExplorePlatform.entries) for (tab in MobileExploreTab.entries) {
            assertEquals("$platform $tab", tab in expected.getValue(platform), mobileSourceSupportsTab(platform, tab))
        }
    }

    @Test fun representativePayloadsMapToAll32ModeAndTabCombinations() {
        fun fixture(source: ExplorePlatform): ExplorePayload {
            val name = source.name.lowercase()
            val anime = item(name, 1, MediaType.Anime, subject = if (source == ExplorePlatform.BANGUMI) 2 else null)
            val manga = item(name, 2, MediaType.Manga, subject = if (source == ExplorePlatform.BANGUMI) 1 else null)
            val tv = item(name, 3, MediaType.TvShow, subject = if (source == ExplorePlatform.BANGUMI) 6 else null)
            val movie = item(name, 4, MediaType.Movie, subject = if (source == ExplorePlatform.BANGUMI) 6 else null)
            return when (source) {
                ExplorePlatform.TMDB -> payload().copy(airingAnime = listOf(tv), movieAnime = listOf(movie), trendingAnime = listOf(anime))
                ExplorePlatform.SIMKL -> payload().copy(topAnime = listOf(tv), movieAnime = listOf(movie), trendingAnime = listOf(anime))
                ExplorePlatform.BANGUMI -> payload().copy(topAnime = listOf(anime), topManga = listOf(manga), bangumiTvShows = listOf(tv), bangumiMovies = listOf(movie))
                ExplorePlatform.ALL -> error("ALL does not have a provider payload")
                else -> payload().copy(topAnime = listOf(anime), topManga = listOf(manga))
            }
        }
        val bySource = ExplorePlatform.sources.associateWith { source ->
            val mapped = map(source, fixture(source))
            assertTrue(mapped.rejected.isEmpty())
            assertTrue(mapped.shelves.all { it.source == source && it.results.all { media -> media.source.equals(source.name, true) } })
            mapped.shelves
        }
        val allShelves = ExplorePlatform.sources.flatMap { bySource.getValue(it) }
        for (mode in ExplorePlatform.entries) for (tab in MobileExploreTab.entries) {
            val shelves = if (mode == ExplorePlatform.ALL) allShelves else bySource.getValue(mode)
            val expected = mobileSourceSupportsTab(mode, tab)
            assertEquals("$mode $tab", expected, shelves.any { it.tab == tab })
        }
        assertEquals(allShelves.size, allShelves.map { it.key }.distinct().size)
    }

    @Test fun ordinaryProvidersUseSectionMeaningAndRetainIdentities() {
        for (source in listOf(ExplorePlatform.AniList, ExplorePlatform.MAL, ExplorePlatform.KITSU, ExplorePlatform.SHIKIMORI, ExplorePlatform.BANGUMI)) {
            val tag = source.name.lowercase()
            val anime = item(tag, 1, MediaType.Anime, subject = if (source == ExplorePlatform.BANGUMI) 2 else null)
            val animeMovie = item(tag, 2, MediaType.Movie, subject = if (source == ExplorePlatform.BANGUMI) 2 else null)
            val manga = item(tag, 1, MediaType.Manga, subject = if (source == ExplorePlatform.BANGUMI) 1 else null)
            val mapped = map(source, payload().copy(topAnime = listOf(anime), movieAnime = listOf(animeMovie), topManga = listOf(manga)))
            assertEquals(3, mapped.shelves.size)
            assertTrue(mapped.rejected.isEmpty())
            assertEquals(listOf(MobileExploreTab.ANIME, MobileExploreTab.ANIME, MobileExploreTab.MANGA), mapped.shelves.map { it.tab })
            assertEquals(listOf(anime, animeMovie, manga), mapped.shelves.flatMap { it.results })
            assertEquals(3, mapped.shelves.map { it.key }.distinct().size)
        }
    }

    @Test fun tmdbAnimeMovieRemainsAnimeAndMixedListsAreSplitSafely() {
        val show = item("tmdb", 1, MediaType.TvShow)
        val movie = item("tmdb", 1, MediaType.Movie)
        val animeMovie = item("tmdb", 2, MediaType.Movie)
        val mapped = map(ExplorePlatform.TMDB, payload().copy(
            topAnime = listOf(show, movie), trendingAnime = listOf(animeMovie), topManga = listOf(show),
        ))
        val mixed = mapped.shelves.filter { it.category == ExploreCategoryType.TOP_ANIME }
        assertEquals(listOf(MobileExploreTab.SERIES, MobileExploreTab.MOVIES), mixed.map { it.tab })
        assertEquals(listOf(show, movie), mixed.flatMap { it.results })
        assertTrue(mixed.all { it.requiresSpecialContinuation })
        assertEquals(MobileExploreTab.ANIME, mapped.shelves.single { it.category == ExploreCategoryType.TRENDING_ANIME }.tab)
        assertEquals(MobileExploreTab.SERIES, mapped.shelves.single { it.category == ExploreCategoryType.TOP_MANGA }.tab)
        assertTrue(mapped.rejected.isEmpty())
    }

    @Test fun simklLegacyNamesResolveToTheirActualShelves() {
        val tv = item("simkl", 1, MediaType.TvShow)
        val movie = item("simkl", 2, MediaType.Movie)
        val anime = item("simkl", 3, MediaType.Anime)
        val shelves = map(ExplorePlatform.SIMKL, payload().copy(topAnime = listOf(tv), topManga = listOf(tv), movieAnime = listOf(movie), trendingManga = listOf(anime))).shelves
        assertEquals(MobileExploreTab.SERIES, shelves.single { it.category == ExploreCategoryType.TOP_ANIME }.tab)
        assertEquals(MobileExploreTab.SERIES, shelves.single { it.category == ExploreCategoryType.TOP_MANGA }.tab)
        assertEquals(MobileExploreTab.MOVIES, shelves.single { it.category == ExploreCategoryType.MOVIE_ANIME }.tab)
        assertEquals(MobileExploreTab.ANIME, shelves.single { it.category == ExploreCategoryType.TRENDING_MANGA }.tab)
    }

    @Test fun bangumiRealAndNamespaceAreNotConfusedWithAnimeOrManga() {
        val realTv = item("bangumi", 1, MediaType.TvShow, subject = 6)
        val realMovie = item("bangumi", 2, MediaType.Movie, subject = 6)
        val wrongNamespace = item("bangumi", 3, MediaType.Anime, subject = 6)
        val mapped = map(ExplorePlatform.BANGUMI, payload().copy(topAnime = listOf(wrongNamespace), bangumiTvShows = listOf(realTv), bangumiMovies = listOf(realMovie)))
        assertEquals(listOf(MobileExploreTab.SERIES, MobileExploreTab.MOVIES), mapped.shelves.map { it.tab })
        assertEquals(listOf(wrongNamespace), mapped.rejected.map { it.item })
        assertEquals(MobileExploreRejection.MEDIA_TYPE_MISMATCH, mapped.rejected.single().reason)
    }

    @Test fun anilistFallbackIsAttributedToKitsuAndNeverUsesAnilistPagination() {
        val ani = item("anilist", 1, MediaType.Anime)
        val kitsu = item("kitsu", 1, MediaType.Anime)
        val data = payload().copy(topAnime = listOf(ani, kitsu))
        val selected = map(ExplorePlatform.AniList, data, fallback = true)
        assertEquals(listOf(ExplorePlatform.AniList, ExplorePlatform.KITSU), selected.shelves.map { it.source })
        assertEquals(listOf(ani, kitsu), selected.shelves.flatMap { it.results })
        assertTrue(selected.shelves.last().requiresSpecialContinuation)
        assertEquals(ExplorePlatform.AniList, selected.shelves.last().originPlatform)
        val allMode = map(ExplorePlatform.AniList, data)
        assertEquals(listOf(ani), allMode.shelves.single().results)
        assertEquals(listOf(kitsu), allMode.rejected.map { it.item })
    }

    @Test fun unknownSourcesAndIncorrectMediaAreQuarantinedNotRelabelled() {
        val unknown = item("unknown", 1, MediaType.Anime)
        val mangaInAnime = item("anilist", 2, MediaType.Manga)
        val mapped = map(ExplorePlatform.AniList, payload().copy(topAnime = listOf(unknown, mangaInAnime)))
        assertTrue(mapped.shelves.isEmpty())
        assertEquals(listOf(MobileExploreRejection.UNKNOWN_OR_UNEXPECTED_SOURCE, MobileExploreRejection.MEDIA_TYPE_MISMATCH), mapped.rejected.map { it.reason })
    }

    @Test fun malAliasesAdultFilterNovelPreferenceAndDistinctness() {
        val anime = item("jikan", 1, MediaType.Anime)
        val adult = item("mal", 2, MediaType.Anime, adult = true)
        val manga = item("mal", 1, MediaType.Manga)
        val novel = item("MAL", 3, MediaType.Manga)
        val p = payload().copy(topAnime = listOf(anime, anime, adult), topManga = listOf(manga), novels = listOf(novel))
        val filtered = map(ExplorePlatform.MAL, p, separateNovels = false)
        assertEquals(listOf(anime), filtered.shelves.single { it.tab == MobileExploreTab.ANIME }.results)
        assertEquals(listOf(manga, novel), filtered.shelves.single { it.tab == MobileExploreTab.MANGA }.results)
        assertTrue(filtered.shelves.single { it.tab == MobileExploreTab.MANGA }.requiresSpecialContinuation)
        val unfiltered = map(ExplorePlatform.MAL, p, adult = true, separateNovels = true)
        assertEquals(listOf(anime, adult), unfiltered.shelves.single { it.tab == MobileExploreTab.ANIME }.results)
        assertEquals(2, unfiltered.shelves.count { it.tab == MobileExploreTab.MANGA })
    }

    @Test fun simklPersonalShelvesRetainOriginAndRouteSemantics() {
        val tv = item("simkl", 1, MediaType.TvShow)
        val movie = item("simkl", 2, MediaType.Movie)
        val invalid = item("tmdb", 3, MediaType.Movie)
        val p = payload().copy(simklContinueSeries = listOf(tv), simklContinueMovies = listOf(movie),
            simklPlannedSeries = listOf(tv), simklPlannedMovies = listOf(movie, invalid))
        for (origin in listOf(ExplorePlatform.TMDB, ExplorePlatform.SIMKL)) {
            val result = mobileExplorePersonalSections(origin, p, false)
            assertEquals(4, result.shelves.size)
            assertEquals(4, result.shelves.map { it.key }.distinct().size)
            assertTrue(result.shelves.all { it.source == ExplorePlatform.SIMKL && it.originPlatform == origin && it.requiresSpecialContinuation })
            assertEquals(2, result.shelves.count { it.origin == MobileShelfOrigin.SIMKL_CONTINUE })
            assertEquals(2, result.shelves.count { it.origin == MobileShelfOrigin.SIMKL_PLANNED })
            assertEquals(listOf(invalid), result.rejected.map { it.item })
        }
    }

    @Test fun everyRealSourceSectionWithRepresentativeItemsMapsWithoutDataLoss() {
        for (platform in ExplorePlatform.sources) {
            val name = platform.name.lowercase()
            val subject = platform == ExplorePlatform.BANGUMI
            val anime = item(name, 101, MediaType.Anime, subject = if (subject) 2 else null)
            val animeMovie = item(name, 102, MediaType.Movie, subject = if (subject) 2 else null)
            val manga = item(name, 103, MediaType.Manga, subject = if (subject) 1 else null)
            val tv = item(name, 104, MediaType.TvShow, subject = if (subject) 6 else null)
            val movie = item(name, 105, MediaType.Movie, subject = if (subject) 6 else null)
            val data = when (platform) {
                ExplorePlatform.TMDB -> payload().copy(
                    topAnime = listOf(tv, movie), trendingAnime = listOf(animeMovie), airingAnime = listOf(tv),
                    movieAnime = listOf(movie), newlyAddedAnime = listOf(animeMovie), topManga = listOf(tv),
                    upcomingAnime = listOf(movie), trendingManga = listOf(animeMovie), seasonalAnime = listOf(tv),
                    publishingManga = listOf(movie), upcomingMediaTmdb = listOf(tv, movie),
                )
                ExplorePlatform.SIMKL -> payload().copy(
                    topAnime = listOf(tv), trendingAnime = listOf(anime), movieAnime = listOf(movie),
                    airingAnime = listOf(tv), trendingManga = listOf(anime), newlyAddedAnime = listOf(anime),
                    topManga = listOf(tv), upcomingAnime = listOf(movie), upcomingMediaTmdb = listOf(anime),
                )
                else -> payload().copy(
                    topAnime = listOf(anime), trendingAnime = listOf(anime), topRatedAnime = listOf(anime),
                    airingAnime = listOf(anime), upcomingAnime = listOf(anime), movieAnime = listOf(animeMovie),
                    seasonalAnime = listOf(anime), newlyAddedAnime = listOf(anime),
                    topManga = listOf(manga), trendingManga = listOf(manga), topRatedManga = listOf(manga),
                    publishingManga = listOf(manga), newlyAddedManga = listOf(manga),
                    manhwaManhua = listOf(manga), novels = listOf(manga),
                    bangumiTvShows = if (subject) listOf(tv) else null,
                    bangumiMovies = if (subject) listOf(movie) else null,
                )
            }
            val result = map(platform, data)
            val expected = when (platform) {
                ExplorePlatform.TMDB -> 13 // 11 source sections, two split by media type.
                ExplorePlatform.SIMKL -> 9 // 8 common + legacy upcoming-anime field.
                ExplorePlatform.BANGUMI -> 17
                else -> 15
            }
            assertEquals("$platform shelf count", expected, result.shelves.size)
            assertEquals("$platform rejected", emptyList<MobileExploreRejectedItem>(), result.rejected)
            assertEquals("$platform keys", expected, result.shelves.map { it.key }.distinct().size)
            assertTrue(result.shelves.all { it.source == platform && it.results.isNotEmpty() })
        }
    }

    @Test fun simklUpcomingAnimeLegacyFieldIsNotDroppedOrSentToTmdbContinuation() {
        val upcoming = item("simkl", 97, MediaType.Anime)
        val result = map(ExplorePlatform.SIMKL, payload().copy(upcomingMediaTmdb = listOf(upcoming)))
        val shelf = result.shelves.single { it.category == ExploreCategoryType.UPCOMING_MEDIA_TMDB }
        assertEquals(MobileExploreTab.ANIME, shelf.tab)
        assertEquals(ExplorePlatform.SIMKL, shelf.source)
        assertEquals("Yaklaşan Animeler", shelf.originalTitle)
        assertEquals(listOf(upcoming), shelf.results)
        assertTrue(shelf.requiresSpecialContinuation)
        assertEquals(MobileShelfAction.FINITE_GRID_REQUIRES_UI, mobileShelfAction(shelf))
    }

    @Test fun heroUsesActiveTabAndCanSelectMangaWithoutGlobalPolicyChanges() {
        val anime = item("anilist", 1, MediaType.Anime).copy(backdropUrl = "https://example.test/anime")
        val mangaPoster = item("anilist", 2, MediaType.Manga).copy(imageUrl = "https://example.test/manga")
        val mangaBackdrop = item("anilist", 3, MediaType.Manga).copy(backdropUrl = "https://example.test/book")
        val shelves = map(ExplorePlatform.AniList, payload().copy(topAnime = listOf(anime), topManga = listOf(mangaPoster, mangaBackdrop))).shelves
        assertEquals(anime, mobileExploreHeroCandidate(MobileExploreTab.ANIME, shelves))
        assertEquals(mangaBackdrop, mobileExploreHeroCandidate(MobileExploreTab.MANGA, shelves))
        assertEquals(null, mobileExploreHeroCandidate(MobileExploreTab.MOVIES, shelves))
        assertEquals(mangaPoster, mobileExploreHeroCandidate(MobileExploreTab.MANGA, shelves.map { it.copy(results = it.results.filter { result -> result != mangaBackdrop }) }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun allIsNeverAcceptedAsAnApiPayload() {
        map(ExplorePlatform.ALL, payload())
    }
}
