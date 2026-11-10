package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AllSourcesExploreTest {
    private fun media(source: String = "anilist", id: Int = 1, type: MediaType = MediaType.Anime, adult: Boolean = false) =
        JikanSearchResult(id, "Title $id", "", type, null, 8, adult, null, 2026, source,
            backdropUrl = "https://example.test/backdrop_$id.jpg")

    private fun payload(items: List<JikanSearchResult> = listOf(media())) = ExplorePayload(
        topAnime = items, airingAnime = emptyList(), upcomingAnime = emptyList(), topManga = emptyList(),
        publishingManga = emptyList(), trendingAnime = emptyList(), movieAnime = emptyList(), seasonalAnime = emptyList()
    )

    @Test fun allIsAModeNotAnotherApi() {
        // Tümü bir görünüm modudur; Bangumi eklendikten sonra 7 gerçek API kaynağı vardır.
        assertEquals(7, ExplorePlatform.sources.size)
        assertFalse(ExplorePlatform.ALL in ExplorePlatform.sources)
        assertTrue(ExplorePlatform.BANGUMI in ExplorePlatform.sources)
    }

    @Test fun categoriesStayGroupedUnderTheirSource() {
        val states = ExplorePlatform.sources.reversed().associateWith {
            val item = media(it.name.lowercase())
            ExploreSourceState(payload(listOf(item)).copy(trendingAnime = listOf(item)))
        }
        val sections = allSourceSections(states, false)
        val expectedPlatformOrder = ExplorePlatform.sources.flatMap { platform ->
            List(if (platform == ExplorePlatform.AniList) ANILIST_EXPLORE_SHELF_CATEGORIES.size else 2) { platform }
        }
        assertEquals(expectedPlatformOrder, sections.map { it.platform })
        assertEquals(sections.size, sections.map { it.key }.distinct().size)
        assertEquals(ExplorePlatform.sources.size, allSourceHeroes(states, false).size)
    }

    @Test fun allSourceHeroIncludesOneAttributedHighlightFromEachProvider() {
        val states = ExplorePlatform.sources.associateWith { platform ->
            ExploreSourceState(payload(listOf(media(platform.name.lowercase(), id = platform.ordinal + 1))))
        }

        val heroes = allSourceHeroes(states, showAdultContent = false)

        assertEquals(ExplorePlatform.sources.map { it.name.lowercase() }.toSet(), heroes.map { it.source }.toSet())
    }

    @Test fun movieAndTvIdsDoNotCollide() {
        val movie = media("tmdb", 10, MediaType.Movie)
        val show = media("tmdb", 10, MediaType.TvShow)
        val states = mapOf(ExplorePlatform.TMDB to ExploreSourceState(payload(listOf(movie, movie, show))))
        assertEquals(listOf(movie, show), allSourceSections(states, false).single().results)
    }

    @Test fun sourceIdentityAndMediaTypeArePreserved() {
        val anime = media("anilist", 1)
        val manga = media("anilist", 1, MediaType.Manga)
        val other = media("kitsu", 1)
        assertEquals(3, listOf(anime, manga, other).distinctBy { it.exploreIdentity() }.size)
    }

    @Test fun adultFilteringAppliesToEveryCategoryAndHero() {
        val adult = media(adult = true)
        val states = mapOf(ExplorePlatform.AniList to ExploreSourceState(payload(listOf(adult)).copy(topRatedAnime = listOf(adult))))
        assertTrue(allSourceSections(states, false).isEmpty())
        assertTrue(allSourceHeroes(states, false).isEmpty())
        assertEquals(2, allSourceSections(states, true).count { it.results.isNotEmpty() })
        assertEquals(listOf(adult), allSourceHeroes(states, true))
    }

    @Test fun legacyKitsuFallbackIsNotShownUnderAniList() {
        val kitsu = media("kitsu")
        val cached = payload(listOf(kitsu))
        val states = mapOf(ExplorePlatform.AniList to ExploreSourceState(cached), ExplorePlatform.KITSU to ExploreSourceState(cached))
        assertEquals(listOf(ExplorePlatform.KITSU), allSourceSections(states, false).map { it.platform })
        assertFalse(cached.forSource(ExplorePlatform.AniList).hasCatalogContent())
    }

    @Test fun malAndJikanAliasesBelongToMal() {
        val cached = payload(listOf(media("jikan"), media("MAL", 2)))
        assertEquals(2, cached.forSource(ExplorePlatform.MAL).topAnime.size)
    }

    @Test fun tmdbShowsAreNotLabelledAsManga() {
        val item = media("tmdb", type = MediaType.TvShow)
        val p = payload(emptyList()).copy(topManga = listOf(item))
        val section = allSourceSections(mapOf(ExplorePlatform.TMDB to ExploreSourceState(p)), false).single()
        assertEquals("Popüler Diziler", section.title)
        assertEquals(ExplorePlatform.TMDB, section.platform)
        assertEquals(ExploreCategoryType.TOP_MANGA, section.category)
    }

    @Test fun bangumiRealShelvesAreSeparateAndStayUnderBangumi() {
        val tv = media("bangumi", id = 41, type = MediaType.TvShow)
        val movie = media("bangumi", id = 42, type = MediaType.Movie)
        val p = payload(emptyList()).copy(bangumiTvShows = listOf(tv), bangumiMovies = listOf(movie))

        val sections = allSourceSections(mapOf(ExplorePlatform.BANGUMI to ExploreSourceState(p)), false)

        assertEquals(
            listOf(ExploreCategoryType.BANGUMI_TV, ExploreCategoryType.BANGUMI_MOVIES),
            sections.map { it.category }
        )
        assertEquals(listOf("Bangumi Dizileri", "Bangumi Filmleri"), sections.map { it.title })
        assertEquals(listOf(tv, movie), sections.map { it.results.single() })
        assertTrue(p.hasCatalogContent())
        assertTrue(p.forSource(ExplorePlatform.MAL).bangumiTvShows.orEmpty().isEmpty())
    }

    @Test fun bangumiNewlyAddedMangaIsNotDroppedFromTheContentSections() {
        val newlyAdded = media("bangumi", id = 91, type = MediaType.Manga)
        val p = payload(emptyList()).copy(newlyAddedManga = listOf(newlyAdded))

        val section = allSourceSections(
            mapOf(ExplorePlatform.BANGUMI to ExploreSourceState(p)),
            showAdultContent = false
        ).single()

        assertEquals(ExploreCategoryType.NEWLY_ADDED_MANGA, section.category)
        assertEquals(listOf(newlyAdded), section.results)
    }

    @Test fun scoreChartsAreDistinctFromPopularityCharts() {
        val popular = media()
        val rated = media(id = 2)
        val states = mapOf(ExplorePlatform.AniList to ExploreSourceState(payload(listOf(popular)).copy(topRatedAnime = listOf(rated))))
        val sections = allSourceSections(states, false).filter { it.results.isNotEmpty() }
        assertEquals(listOf(ExploreCategoryType.TOP_ANIME, ExploreCategoryType.TOP_RATED_ANIME), sections.map { it.category })
        assertEquals(listOf(rated), sections.last().results)
    }

    @Test fun anilistKeepsItsFullCategoryModelWhileInitialNetworkBatchStaysSmall() {
        val categories = sourceSections(ExplorePlatform.AniList, payload(emptyList())).map { it.category }
        assertEquals(ANILIST_EXPLORE_SHELF_CATEGORIES, categories)
        assertEquals(6, ANILIST_INITIAL_EXPLORE_CATEGORIES.size)
        assertEquals(6, aniListExploreShelfRequests(ANILIST_INITIAL_EXPLORE_CATEGORIES, "FALL", 2026).size)
    }

    @Test fun anilistUnrequestedShelvesRemainAsLazyRowsAfterInitialCatalogLoad() {
        val sections = allSourceSections(
            mapOf(ExplorePlatform.AniList to ExploreSourceState(payload(listOf(media())))),
            showAdultContent = false
        )
        assertEquals(ANILIST_EXPLORE_SHELF_CATEGORIES.size, sections.size)
        assertEquals(listOf(media()), sections.first().results)
        assertTrue(sections.drop(1).all { it.results.isEmpty() })
    }

    @Test fun anilistManhwaShelfQueriesBothKoreanAndChineseOrigins() {
        val requests = aniListExploreShelfRequests(
            listOf(ExploreCategoryType.MANHWA_MANHUA),
            season = "FALL",
            seasonYear = 2026
        )
        assertEquals(listOf("KR", "CN"), requests.map { it.spec.countryOfOrigin })
        assertTrue(requests.all { it.category == ExploreCategoryType.MANHWA_MANHUA })
    }

    @Test fun standardSourcesKeepAnimeAndMangaShelvesInALogicalStableOrder() {
        val anime = media(id = 1)
        val manga = media(id = 20, type = MediaType.Manga)
        val p = payload(listOf(anime)).copy(
            trendingAnime = listOf(media(id = 2)),
            topRatedAnime = listOf(media(id = 3)),
            airingAnime = listOf(media(id = 4)),
            upcomingAnime = listOf(media(id = 5)),
            movieAnime = listOf(media(id = 6, type = MediaType.Movie)),
            seasonalAnime = listOf(media(id = 7)),
            newlyAddedAnime = listOf(media(id = 8)),
            topManga = listOf(manga),
            trendingManga = listOf(media("anilist", 21, MediaType.Manga)),
            topRatedManga = listOf(media("anilist", 22, MediaType.Manga)),
            publishingManga = listOf(media("anilist", 23, MediaType.Manga)),
            newlyAddedManga = listOf(media("anilist", 24, MediaType.Manga)),
            manhwaManhua = listOf(media("anilist", 25, MediaType.Manga)),
            novels = listOf(media("anilist", 26, MediaType.Manga))
        )
        val expected = listOf(
            ExploreCategoryType.TOP_ANIME,
            ExploreCategoryType.TRENDING_ANIME,
            ExploreCategoryType.TOP_RATED_ANIME,
            ExploreCategoryType.AIRING_ANIME,
            ExploreCategoryType.UPCOMING_ANIME,
            ExploreCategoryType.MOVIE_ANIME,
            ExploreCategoryType.SEASONAL_ANIME,
            ExploreCategoryType.NEWLY_ADDED_ANIME,
            ExploreCategoryType.TOP_MANGA,
            ExploreCategoryType.TRENDING_MANGA,
            ExploreCategoryType.TOP_RATED_MANGA,
            ExploreCategoryType.PUBLISHING_MANGA,
            ExploreCategoryType.NEWLY_ADDED_MANGA,
            ExploreCategoryType.MANHWA_MANHUA,
            ExploreCategoryType.NOVELS
        )

        ExplorePlatform.sources
            .filter { it != ExplorePlatform.TMDB && it != ExplorePlatform.SIMKL }
            .forEach { platform ->
                assertEquals(expected, sourceSections(platform, p).filter { it.results.isNotEmpty() }.map { it.category })
            }
    }

    @Test fun tmdbAndAllUseTheSameTrendPopularAndRatedSectionOrder() {
        val p = payload(listOf(media("tmdb", 1))).copy(
            trendingAnime = listOf(media("tmdb", 2)),
            airingAnime = listOf(media("tmdb", 3, MediaType.TvShow)),
            movieAnime = listOf(media("tmdb", 4, MediaType.Movie)),
            newlyAddedAnime = listOf(media("tmdb", 5)),
            topManga = listOf(media("tmdb", 6, MediaType.TvShow)),
            upcomingAnime = listOf(media("tmdb", 7, MediaType.Movie)),
            trendingManga = listOf(media("tmdb", 8)),
            seasonalAnime = listOf(media("tmdb", 9, MediaType.TvShow)),
            publishingManga = listOf(media("tmdb", 10, MediaType.Movie)),
            upcomingMediaTmdb = listOf(media("tmdb", 11, MediaType.Movie))
        )
        val expected = listOf(
            ExploreCategoryType.TOP_ANIME,
            ExploreCategoryType.TRENDING_ANIME,
            ExploreCategoryType.AIRING_ANIME,
            ExploreCategoryType.MOVIE_ANIME,
            ExploreCategoryType.NEWLY_ADDED_ANIME,
            ExploreCategoryType.TOP_MANGA,
            ExploreCategoryType.UPCOMING_ANIME,
            ExploreCategoryType.TRENDING_MANGA,
            ExploreCategoryType.SEASONAL_ANIME,
            ExploreCategoryType.PUBLISHING_MANGA,
            ExploreCategoryType.UPCOMING_MEDIA_TMDB
        )
        val singleSource = sourceSections(ExplorePlatform.TMDB, p).filter { it.results.isNotEmpty() }
        val allMode = allSourceSections(
            mapOf(ExplorePlatform.TMDB to ExploreSourceState(p)),
            showAdultContent = false
        )

        assertEquals(expected, singleSource.map { it.category })
        assertEquals(singleSource.map { it.key }, allMode.map { it.key })
    }

    @Test fun sourceJumpIndicesAccountForCollapsedAndEmptyGroups() {
        val states = ExplorePlatform.sources.associateWith { ExploreSourceState(payload(listOf(media(it.name.lowercase())))) }
        val sections = allSourceSections(states, false).groupBy { it.platform }
        val expanded = allSourceHeaderIndices(sections, emptySet(), startIndex = 2)
        assertEquals(listOf(4, 21, 24, 27, 30, 33, 36), ExplorePlatform.sources.map { expanded[it] })
        val collapsed = allSourceHeaderIndices(sections, setOf(ExplorePlatform.AniList), startIndex = 2)
        assertEquals(5, collapsed[ExplorePlatform.MAL])
        val empty = allSourceHeaderIndices(emptyMap(), emptySet(), startIndex = 1)
        assertEquals(listOf(3, 5, 7, 9, 11, 13, 15), ExplorePlatform.sources.map { empty[it] })
    }

    @Test fun airingSoonShelfDataSurvivesSourceFiltering() {
        // Ortak "Yakında Yayında" verisi gerçek kimliklerini taşır (anilist/mal/tmdb) ve hiçbir
        // kaynağa ait etiketlenmediğinden owned() filtresi bu alanı boşaltmamalı — aksi hâlde
        // Tümü modundan önbelleğe yazılan payload tek-kaynak görünümünde şeridi kaybederdi.
        val shared = media("anilist", id = 77)
        val kitsu = media("kitsu", id = 3)
        val payload = payload(listOf(kitsu)).copy(airingSoonAnime = listOf(shared))
        val filtered = payload.forSource(ExplorePlatform.KITSU)
        assertEquals(listOf(shared), filtered.airingSoonAnime)
        assertEquals(listOf(kitsu), filtered.topAnime)
    }

    @Test fun airingSoonShelfItemShiftsSourceJumpIndices() {
        val states = ExplorePlatform.sources.associateWith { ExploreSourceState(payload(listOf(media(it.name.lowercase())))) }
        val sections = allSourceSections(states, false).groupBy { it.platform }
        val withoutShelf = allSourceHeaderIndices(sections, emptySet(), startIndex = 2, extraItemsAfterIntro = 0)
        val withShelf = allSourceHeaderIndices(sections, emptySet(), startIndex = 2, extraItemsAfterIntro = 1)
        ExplorePlatform.sources.forEach { platform ->
            assertEquals(withoutShelf.getValue(platform) + 1, withShelf.getValue(platform))
        }
        // Şerit gösterilmiyorsa indeksler eski davranışla bire bir aynı kalır.
        assertEquals(withoutShelf, allSourceHeaderIndices(sections, emptySet(), startIndex = 2))
    }

    @Test fun successfulCacheAvoidsNetwork() = runBlocking {
        val cached = payload()
        val state = loadExploreSource(cached, false, { error("Network must not be called") }, { error("Disk must not be called") })
        assertSame(cached, state.payload)
        assertNull(state.error)
    }

    @Test fun forcedRefreshReplacesCache() = runBlocking {
        val fresh = payload(listOf(media(id = 2)))
        val state = loadExploreSource(payload(), true, { fresh }, { null })
        assertSame(fresh, state.payload)
        assertNull(state.error)
    }

    @Test fun failedRefreshKeepsContentAndMarksItCached() = runBlocking {
        val cached = payload()
        val state = loadExploreSource(cached, true, { error("Offline") }, { null })
        assertSame(cached, state.payload)
        assertNotNull(state.error)
        assertTrue(state.isCached)
    }

    @Test fun emptyResponseFallsBackToDiskRatherThanCachingSuccess() = runBlocking {
        val offline = payload()
        val state = loadExploreSource(null, false, { payload(emptyList()) }, { offline })
        assertSame(offline, state.payload)
        assertNotNull(state.error)
        assertTrue(state.isCached)
    }

    @Test fun corruptDiskCacheDoesNotCrashTheFeed() = runBlocking {
        val state = loadExploreSource(null, false, { error("Offline") }, { error("Invalid JSON") })
        assertNull(state.payload)
        assertNotNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test fun slowSourceTimesOutIndependently() = runBlocking {
        val state = loadExploreSource(null, false, { awaitCancellation() }, { null }, timeoutMillis = 20)
        assertNull(state.payload)
        assertNotNull(state.error)
    }

    @Test fun cancellationDoesNotReadOfflineOrPublishSuccess() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var returned = false
        var diskRead = false
        val job = launch {
            loadExploreSource(null, false, { entered.complete(Unit); awaitCancellation() }, { diskRead = true; payload() })
            returned = true
        }
        entered.await()
        job.cancelAndJoin()
        assertFalse(returned)
        assertFalse(diskRead)
    }

    @Test fun nativeKindShelvesAppearWhenTheirProviderReturnsData() {
        // Kitsu/MAL/Shikimori/AniList expose both kinds; Bangumi provides its BOOK_NOVEL subtype.
        val kitsuManhwa = media("kitsu", 7, MediaType.Manga)
        val kitsuNovel = media("kitsu", 8, MediaType.Manga)
        val kitsuState = ExploreSourceState(
            payload(listOf(media("kitsu"))).copy(manhwaManhua = listOf(kitsuManhwa), novels = listOf(kitsuNovel))
        )
        val kitsuSections = allSourceSections(mapOf(ExplorePlatform.KITSU to kitsuState), false)
        assertEquals(
            listOf(ExploreCategoryType.MANHWA_MANHUA, ExploreCategoryType.NOVELS),
            kitsuSections.filter { it.category == ExploreCategoryType.MANHWA_MANHUA || it.category == ExploreCategoryType.NOVELS }
                .map { it.category }
        )
        assertTrue(kitsuSections.all { it.platform == ExplorePlatform.KITSU })

        // Bangumi has a separate official BOOK_NOVEL subtype; its manga/date shelf stays distinct.
        val recentManga = media("bangumi", 9, MediaType.Manga)
        val bangumiNovel = media("bangumi", 10, MediaType.Manga)
        val bangumi = payload(listOf(media("bangumi"))).copy(
            newlyAddedManga = listOf(recentManga), novels = listOf(bangumiNovel)
        )
        val bangumiSections = allSourceSections(mapOf(ExplorePlatform.BANGUMI to ExploreSourceState(bangumi)), false)
        assertEquals(
            listOf(ExploreCategoryType.NEWLY_ADDED_MANGA, ExploreCategoryType.NOVELS),
            bangumiSections.filter { it.category == ExploreCategoryType.NEWLY_ADDED_MANGA || it.category == ExploreCategoryType.NOVELS }
                .map { it.category }
        )
    }

    @Test fun fastAndFailedSourcesDoNotWaitForSlowSource() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val slow = async { loadExploreSource(null, false, { gate.await(); payload() }, { null }) }
        val fast = async { loadExploreSource(null, false, { payload() }, { null }) }
        val failed = async { loadExploreSource(null, false, { error("Unavailable") }, { null }) }
        assertNotNull(fast.await().payload)
        assertNotNull(failed.await().error)
        assertFalse(slow.isCompleted)
        gate.complete(Unit)
        assertNotNull(slow.await().payload)
    }
}
