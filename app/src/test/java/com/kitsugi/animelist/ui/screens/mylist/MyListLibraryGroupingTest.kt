package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.data.remote.BangumiTitleCache
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyListLibraryGroupingTest {

    private fun entry(
        id: Int,
        title: String,
        source: String,
        malId: Int? = null,
        type: MediaType = MediaType.Anime,
        year: Int? = 2024
    ) = MediaEntry(
        id = id,
        title = title,
        type = type,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        source = source,
        malId = malId,
        year = year
    )

    @Test
    fun everyProviderTabDeduplicatesBeforeFiltersAndCounts() {
        val sources = listOf("anilist", "mal", "simkl", "kitsu", "shikimori", "bangumi")
        val rows = sources.flatMapIndexed { index, source ->
            val nativeId = when (source) {
                "anilist" -> 100_000_123
                "kitsu" -> 300_000_123
                "shikimori" -> 400_000_123
                "bangumi" -> 500_000_123
                else -> 123
            }
            val old = entry(index * 2 + 1, "Same production", source, nativeId)
                .copy(updatedAt = 10, progress = 1)
            // Updated time, not insertion ID, decides which duplicate supplies status.
            listOf(old, old.copy(id = index * 2, updatedAt = 20,
                progress = 12, status = WatchStatus.Completed))
        }
        val tabs = groupMyListProviderTabs(rows)
        val populatedTabs = tabs.filterValues { it.isNotEmpty() }
        assertEquals(6, populatedTabs.size)
        populatedTabs.forEach { (tab, entries) ->
            assertEquals(1, entries.size)
            assertTrue(myListSourceMatchesTab(tab, entries.single().source, entries.single().malId))
            assertEquals(12, entries.single().progress)
            assertEquals(WatchStatus.Completed, entries.single().status)
        }
        assertEquals(1, groupMyListEntries(rows).size)
    }

    @Test
    fun providerTabsKeepConflictingSeasonsAndDoNotBorrowOtherProvidersProgress() {
        val simkl = entry(1, "Kaiju No. 8 Season 2", "simkl", 123).copy(progress = 6)
        val duplicate = simkl.copy(id = 2, title = "Alternate title")
        val differentSeason = simkl.copy(id = 3, malId = 456)
        val anilist = entry(4, simkl.title, "anilist", 123).copy(progress = 11)
        val tabs = groupMyListProviderTabs(listOf(simkl, duplicate, differentSeason, anilist))
        assertEquals(2, tabs.getValue(MY_LIST_SIMKL_TAB_INDEX).size)
        assertTrue(tabs.getValue(MY_LIST_SIMKL_TAB_INDEX).all { it.progress == 6 })
        assertEquals(11, tabs.getValue(MY_LIST_ANILIST_TAB_INDEX).single().progress)
    }

    @Test
    fun groupsSameTitleAcrossProvidersAndCollectsTheirBadges() {
        val mal = entry(id = 12, title = "Frieren: Beyond Journey's End", source = "mal", malId = 52991)
        val anilist = entry(id = 10, title = "Sousou no Frieren", source = "anilist", malId = 100_000_870)
            .copy(titleEnglish = "Frieren: Beyond Journey's End")
        val kitsu = entry(id = 8, title = "Frieren: Beyond Journey's End", source = "kitsu", malId = 300_123_456)

        val grouped = groupMyListEntries(listOf(mal, anilist, kitsu))

        assertEquals(1, grouped.size)
        // Yeni kural: Temsilci önceliği AniList > MAL > Kitsu > Shikimori > Simkl > TMDB
        assertEquals(anilist.id, grouped.single().entry.id)
        assertEquals(listOf("mal", "anilist", "kitsu"), grouped.single().sourceIds)
    }


    @Test
    fun combinedRepresentativeKeepsAdultFlagFromAnyProvider() {
        val shikimori = entry(id = 12, title = "Same title", source = "shikimori", malId = 52991)
            .copy(isAdult = true)
        val anilist = entry(id = 10, title = "Same title", source = "anilist", malId = 52991)

        val grouped = groupMyListEntries(listOf(shikimori, anilist))

        assertEquals(1, grouped.size)
        assertEquals(anilist.id, grouped.single().entry.id)
        assertTrue("Combined card should stay blurred if any provider marks it adult", grouped.single().entry.isAdult)
        assertEquals(listOf("shikimori", "anilist"), grouped.single().sourceIds)
    }

    @Test
    fun doesNotGroupDifferentMediaTypesOrConflictingProviderIds() {
        val first = entry(id = 4, title = "Same Title", source = "mal", malId = 1234)
        val otherMalRecord = entry(id = 3, title = "Same Title", source = "mal", malId = 5678)
        val manga = entry(id = 2, title = "Same Title", source = "kitsu", malId = 300_000_004, type = MediaType.Manga)

        val grouped = groupMyListEntries(listOf(first, otherMalRecord, manga))

        assertEquals(3, grouped.size)
        assertFalse(grouped.any { it.sourceIds.size > 1 })
    }

    @Test
    fun bangumiCopyMergesIntoCombinedLibraryViaCrosswalkKeys() {
        val anilist = entry(id = 10, title = "Sousou no Frieren", source = "anilist", malId = 52991)
            .copy(titleEnglish = "Frieren: Beyond Journey's End")
        // Bangumi içe aktarması yalnızca bgm.tv'nin kendi kimliğini ve Çince katalog başlığını taşır.
        val bangumi = entry(id = 20, title = "葬送のフリーレン", source = "bangumi", malId = 500_057_162)

        // Without the crosswalk the same production is counted twice (the reported inflation).
        assertEquals(2, groupMyListEntries(listOf(anilist, bangumi)).size)

        val grouped = groupMyListEntries(
            entries = listOf(anilist, bangumi),
            identityExtras = mapOf(bangumi.id to setOf("Anime:mal:52991"))
        )

        assertEquals(1, grouped.size)
        assertEquals("AniList stays the combined tile's representative", anilist.id, grouped.single().entry.id)
        assertEquals(listOf("anilist", "bangumi"), grouped.single().sourceIds)
    }

    @Test
    fun crosswalkInheritsAdultFlagAndDoesNotInventCrossTypeMerges() {
        val bangumi = entry(id = 21, title = "葬送のフリーレン", source = "bangumi", malId = 500_057_162)
            .copy(isAdult = true)
        val manga = entry(id = 22, title = "Sousou no Frieren", source = "mal", malId = 52991, type = MediaType.Manga)

        // The crosswalk key is only ever offered for the row's own media type.
        val grouped = groupMyListEntries(listOf(manga, bangumi), mapOf(bangumi.id to setOf("Anime:mal:52991")))
        assertEquals(2, grouped.size)
    }

    @Test
    fun animeMovieCopiesMergeOnlyWithVerifiedCrosswalkIdentity() {
        val anime = entry(id = 30, title = "Demon Slayer: Infinity Castle", source = "anilist", malId = 52991)
        val movie = entry(id = 31, title = "Demon Slayer: Infinity Castle", source = "simkl", type = MediaType.Movie)
            .copy(tmdbId = 101)

        // A shared title/year is only a lookup candidate, not proof of identity.
        assertEquals(2, groupMyListEntries(listOf(anime, movie)).size)

        val grouped = groupMyListEntries(
            entries = listOf(anime, movie),
            identityExtras = mapOf(movie.id to setOf("Movie:mal:52991"))
        )
        assertEquals(1, grouped.size)
        assertEquals(anime.id, grouped.single().entry.id)
        assertEquals(setOf("anilist", "simkl"), grouped.single().sourceIds.toSet())
    }

    @Test
    fun crossTypeVideoIdNeedsMatchingTitleAndYear() {
        val anime = entry(id = 32, title = "A film", source = "anilist").copy(tmdbId = 777)
        val sameMovie = entry(id = 33, title = "A film", source = "simkl", type = MediaType.Movie)
            .copy(tmdbId = 777)
        val otherYear = sameMovie.copy(id = 34, year = 2023)
        val otherTitle = sameMovie.copy(id = 35, title = "Another film")

        assertEquals(1, groupMyListEntries(listOf(anime, sameMovie)).size)
        assertEquals(2, groupMyListEntries(listOf(anime, otherYear)).size)
        assertEquals(2, groupMyListEntries(listOf(anime, otherTitle)).size)
    }

    @Test
    fun crossTypeConflictingIdsAndMovieTvShowPairsStaySeparate() {
        val anime = entry(id = 36, title = "A film", source = "mal", malId = 100)
        val conflictingMovie = entry(id = 37, title = "A film", source = "simkl", type = MediaType.Movie)
            .copy(tmdbId = 888)
        val tvShow = entry(id = 38, title = "A film", source = "simkl", type = MediaType.TvShow)
            .copy(tmdbId = 888)

        val conflicting = groupMyListEntries(
            entries = listOf(anime, conflictingMovie),
            identityExtras = mapOf(conflictingMovie.id to setOf("Movie:mal:999", "Movie:tmdb:888"))
        )
        assertEquals(2, conflicting.size)
        assertEquals(2, groupMyListEntries(listOf(conflictingMovie, tvShow)).size)
    }

    @Test
    fun bangumiTabOnlyShowsStableBangumiCollectionEntries() {
        val importedBangumi = entry(
            id = 3,
            title = "Bangumi collection item",
            source = "bgm.tv",
            malId = 500_000_876
        )
        val manualLocal = entry(
            id = 2,
            title = "Local-only item",
            source = "bangumi",
            malId = null
        )
        val simklCopy = entry(
            id = 1,
            title = "Same title from another provider",
            source = "simkl",
            malId = 500_000_876
        )

        assertTrue(myListSourceMatchesTab(MY_LIST_BANGUMI_TAB_INDEX, importedBangumi.source, importedBangumi.malId))
        assertFalse(myListSourceMatchesTab(MY_LIST_BANGUMI_TAB_INDEX, manualLocal.source, manualLocal.malId))
        assertFalse(myListSourceMatchesTab(MY_LIST_BANGUMI_TAB_INDEX, simklCopy.source, simklCopy.malId))
        assertEquals(1, listOf(importedBangumi, manualLocal, simklCopy).count { isBangumiListMember(it.source, it.malId) })
    }

    @Test
    fun tmdbTabShowsOnlyRealAccountRowsAndCombinedViewStillGroupsThem() {
        val account = entry(id = 40, title = "Same film", source = "tmdb_account", type = MediaType.Movie)
            .copy(tmdbId = 550)
        val catalog = entry(id = 41, title = "Same film", source = "tmdb", type = MediaType.Movie)
            .copy(tmdbId = 550)
        val simkl = entry(id = 42, title = "Same film", source = "simkl", type = MediaType.Movie)
            .copy(tmdbId = 550)

        assertTrue(myListSourceMatchesTab(MY_LIST_TMDB_TAB_INDEX, account.source))
        assertFalse(myListSourceMatchesTab(MY_LIST_TMDB_TAB_INDEX, catalog.source))
        assertEquals("tmdb", sourceBadgeId(account.source))
        assertEquals(1, groupMyListEntries(listOf(account, simkl)).size)
        assertEquals(MY_LIST_TAB_COUNT, 8)
    }

    @Test
    fun normalizesProviderAliasesForBadges() {
        val grouped = groupMyListEntries(
            listOf(
                entry(id = 2, title = "Alias title", source = "jikan", malId = 999),
                entry(id = 1, title = "Alias title", source = "myanimelist", malId = 1000)
            )
        )

        assertEquals(2, grouped.size) // Conflicting MAL IDs must remain separate.
        assertTrue(grouped.all { it.sourceIds == listOf("mal") })
        assertEquals("mal", sourceBadgeId("Jikan"))
        assertEquals("shikimori", sourceBadgeId("Shiki"))
        assertEquals(null, sourceBadgeId("manual"))
    }

    @Test
    fun snapshotIsolatesEachSourceTabAndEnrichesMissingEnglishAndLatinTitlesFromSiblings() {
        val anilist = entry(id = 1, title = "Kaijuu 8-gou", source = "anilist", malId = 52588)
            .copy(titleEnglish = "Kaiju No. 8", titleJapanese = "怪獣8号")
        val mal = entry(id = 2, title = "Kaijuu 8-gou", source = "mal", malId = 52588)
            .copy(titleEnglish = "Kaiju No. 8", titleJapanese = "怪獣8号")
        // Simkl ve Shikimori liste API'leri İngilizce ve özgün başlıkları boş bırakır.
        val simkl = entry(id = 3, title = "Kaijuu 8-gou", source = "simkl", malId = 52588)
        val kitsu = entry(id = 4, title = "Kaiju No. 8", source = "kitsu", malId = 300_046_900)
        val shikimori = entry(id = 5, title = "Kaijuu 8-gou", source = "shikimori", malId = 52588)
        // Bangumi yalnızca Çince katalog adını ve kendi subject kimliğini taşır.
        val bangumi = entry(id = 6, title = "怪兽8号", source = "bangumi", malId = 500_404_804)

        val snapshot = buildMyListLibrarySnapshot(
            entries = listOf(anilist, mal, simkl, kitsu, shikimori, bangumi),
            identityExtras = mapOf(
                kitsu.id to setOf("Anime:mal:52588"),
                bangumi.id to setOf("Anime:mal:52588")
            )
        )

        // Tümü: tek birleşik kart + 6 kaynak rozeti
        assertEquals(1, snapshot.entriesForTab(MY_LIST_ALL_TAB_INDEX).size)
        assertEquals(
            listOf("anilist", "mal", "simkl", "kitsu", "shikimori", "bangumi"),
            snapshot.combinedItems.single().sourceIds
        )

        // Her kaynak sekmesi YALNIZCA kendi kaydını içerir ve "kaiju no 8" aramasında 1 sonuç döner.
        val expectedByTab = listOf(
            MY_LIST_ANILIST_TAB_INDEX to anilist.id,
            MY_LIST_MAL_TAB_INDEX to mal.id,
            MY_LIST_SIMKL_TAB_INDEX to simkl.id,
            MY_LIST_KITSU_TAB_INDEX to kitsu.id,
            MY_LIST_SHIKIMORI_TAB_INDEX to shikimori.id,
            MY_LIST_BANGUMI_TAB_INDEX to bangumi.id
        )
        for ((tabIndex, expectedId) in expectedByTab) {
            val tabEntries = snapshot.entriesForTab(tabIndex)
            assertEquals("Tab $tabIndex must hold only its own source entry", 1, tabEntries.size)
            assertEquals(expectedId, tabEntries.single().id)

            val searched = filterMyListEntries(
                entries = tabEntries,
                searchQuery = "kaiju no 8",
                selectedStatusFilterId = "all",
                selectedTypeFilterId = "all",
                selectedFavoriteFilterId = "all",
                selectedScoreFilterId = "all",
                selectedYearFilterId = "all",
                selectedExtraFilterId = "all",
                searchAliasesByEntryId = snapshot.searchAliasesByEntryId
            )
            assertEquals("Tab $tabIndex must match 'kaiju no 8' with 1 own result", listOf(expectedId), searched.map { it.id })
        }

        // Simkl, Shikimori ve Bangumi satırları kendi kimliklerini bozmadan İngilizce/Latin ad kazanır.
        val enrichedSimkl = snapshot.entriesForTab(MY_LIST_SIMKL_TAB_INDEX).single()
        assertEquals("simkl", enrichedSimkl.source)
        assertEquals("Kaiju No. 8", enrichedSimkl.titleEnglish)
        assertEquals("怪獣8号", enrichedSimkl.titleJapanese)

        val enrichedBangumi = snapshot.entriesForTab(MY_LIST_BANGUMI_TAB_INDEX).single()
        assertEquals("bangumi", enrichedBangumi.source)
        assertEquals(500_404_804, enrichedBangumi.malId)
        assertEquals("Kaijuu 8-gou", enrichedBangumi.title)
        assertEquals("Kaiju No. 8", enrichedBangumi.titleEnglish)

        // Özgün Çince başlıkla arama da hem Tümü hem Bangumi sekmesinde eşleşmeye devam eder.
        val cjkOnBangumi = filterMyListEntries(
            entries = snapshot.entriesForTab(MY_LIST_BANGUMI_TAB_INDEX),
            searchQuery = "怪兽8号",
            selectedStatusFilterId = "all",
            selectedTypeFilterId = "all",
            selectedFavoriteFilterId = "all",
            selectedScoreFilterId = "all",
            selectedYearFilterId = "all",
            selectedExtraFilterId = "all",
            searchAliasesByEntryId = snapshot.searchAliasesByEntryId
        )
        assertEquals(listOf(bangumi.id), cjkOnBangumi.map { it.id })
    }

    @Test
    fun snapshotUsesBangumiTitleCacheWhenBangumiHasNoOtherSibling() {
        val bangumiOnly = entry(id = 40, title = "怪兽8号", source = "bangumi", malId = 500_404_804)

        val snapshot = buildMyListLibrarySnapshot(
            entries = listOf(bangumiOnly),
            bangumiTitleLookup = { rawId ->
                if (rawId == 404_804) {
                    BangumiTitleCache.LatinTitles(
                        romaji = "Kaijuu 8-gou",
                        english = "Kaiju No. 8",
                        native = "怪獣8号"
                    )
                } else {
                    null
                }
            }
        )

        val tabEntry = snapshot.entriesForTab(MY_LIST_BANGUMI_TAB_INDEX).single()
        assertEquals("Kaijuu 8-gou", tabEntry.title)
        assertEquals("Kaiju No. 8", tabEntry.titleEnglish)

        val searched = filterMyListEntries(
            entries = listOf(tabEntry),
            searchQuery = "kaiju no 8",
            selectedStatusFilterId = "all",
            selectedTypeFilterId = "all",
            selectedFavoriteFilterId = "all",
            selectedScoreFilterId = "all",
            selectedYearFilterId = "all",
            selectedExtraFilterId = "all",
            searchAliasesByEntryId = snapshot.searchAliasesByEntryId
        )
        assertEquals(listOf(bangumiOnly.id), searched.map { it.id })
    }

    @Test
    fun snapshotHidesCrosswalkBridgedAdultMediaAcrossAllTabsWhenIncludeAdultIsFalse() {
        val anilistAdult = entry(id = 50, title = "Adult Title", source = "anilist", malId = 99111)
            .copy(isAdult = true)
        val bangumiUnflagged = entry(id = 51, title = "成人作品", source = "bangumi", malId = 500_077_777)

        val hiddenSnapshot = buildMyListLibrarySnapshot(
            entries = listOf(anilistAdult, bangumiUnflagged),
            identityExtras = mapOf(bangumiUnflagged.id to setOf("Anime:mal:99111")),
            includeAdult = false
        )

        assertTrue(hiddenSnapshot.entriesForTab(MY_LIST_ALL_TAB_INDEX).isEmpty())
        assertTrue(hiddenSnapshot.entriesForTab(MY_LIST_ANILIST_TAB_INDEX).isEmpty())
        assertTrue(hiddenSnapshot.entriesForTab(MY_LIST_BANGUMI_TAB_INDEX).isEmpty())
    }

    @Test
    fun attackOnTitanTmdbSeasonConflictDoesNotSplitVerifiedMalAndKitsuGroup() {
        val simkl = entry(id = 1, title = "Shingeki no Kyojin", source = "simkl", malId = 16498, year = 2013)
            .copy(titleEnglish = "Attack on Titan", tmdbId = 99999, simklId = 39687)
        val anilist = entry(id = 2, title = "Shingeki no Kyojin", source = "anilist", malId = 16498, year = 2013)
            .copy(titleEnglish = "Attack on Titan", tmdbId = 1429, isFavorite = true)
        val mal = entry(id = 3, title = "Shingeki no Kyojin", source = "mal", malId = 16498, year = 2013)
            .copy(titleEnglish = "Attack on Titan")
        val bangumi = entry(id = 4, title = "進撃の巨人", source = "bangumi", malId = 500_055_870, year = 2013)
        val shikimori = entry(id = 5, title = "Shingeki no Kyojin", source = "shikimori", malId = 400_016_498, year = 2013)
            .copy(titleEnglish = "Attack on Titan")
        val kitsu = entry(id = 6, title = "Attack on Titan", source = "kitsu", malId = 300_007_442, year = 2013)

        val extras = mapOf(
            bangumi.id to setOf("Anime:mal:16498"),
            shikimori.id to setOf("Anime:mal:16498"),
            kitsu.id to setOf("Anime:mal:16498")
        )
        val grouped = groupMyListEntries(
            listOf(simkl, anilist, mal, bangumi, shikimori, kitsu),
            identityExtras = extras
        )

        assertEquals("Farklı Anime TMDB ID'si doğrulanmış MAL/Kitsu grubunu bölmemeli", 1, grouped.size)
        val merged = grouped.single()
        assertEquals(anilist.id, merged.entry.id)
        assertTrue("Favori bayrağı birleşik karta taşınmalı", merged.entry.isFavorite)
        assertEquals(
            setOf("simkl", "anilist", "mal", "bangumi", "shikimori", "kitsu"),
            merged.sourceIds.toSet()
        )
    }

    @Test
    fun kaijuNo8Season2OrdinalVsSeasonNumberMergesKitsuWithAniListGroup() {
        val kitsu = entry(
            id = 10,
            title = "Kaiju No. 8 2nd Season",
            source = "kitsu",
            malId = 300_049_001,
            year = 2025
        )
        val anilist = entry(
            id = 11,
            title = "Kaijuu 8-gou 2nd Season",
            source = "anilist",
            malId = 59177,
            year = 2025
        ).copy(titleEnglish = "Kaiju No. 8 Season 2")
        val mal = entry(
            id = 12,
            title = "Kaijuu 8-gou 2nd Season",
            source = "mal",
            malId = 59177,
            year = 2025
        )
        val simkl = entry(
            id = 13,
            title = "Kaiju No. 8 Season 2",
            source = "simkl",
            malId = 59177,
            year = 2025
        )
        val shikimori = entry(
            id = 14,
            title = "Kaijuu 8-gou 2nd Season",
            source = "shikimori",
            malId = 400_059_177,
            year = 2025
        )

        val grouped = groupMyListEntries(listOf(kitsu, anilist, mal, simkl, shikimori))

        assertEquals("2nd Season ile Season 2 yazılışı tek kartta birleşmeli", 1, grouped.size)
        assertEquals(
            setOf("kitsu", "anilist", "mal", "simkl", "shikimori"),
            grouped.single().sourceIds.toSet()
        )
    }

    @Test
    fun differentSeasonsNeverMergeEvenWithSeasonSynonymExpansion() {
        val season1 = entry(id = 1, title = "Kaiju No. 8", source = "anilist", malId = 52588, year = 2024)
        val season2 = entry(id = 2, title = "Kaiju No. 8 2nd Season", source = "kitsu", malId = 300_049_001, year = 2025)

        val grouped = groupMyListEntries(listOf(season1, season2))

        assertEquals(2, grouped.size)
    }

    @Test
    fun pass2UnionFindMergesCompatibleGroupsEvenWithIncompatibleMiddleGroup() {
        val g0 = entry(id = 1, title = "Shared Alias", source = "anilist", malId = 1001, year = 2024)
        val g1Incompatible = entry(id = 2, title = "Shared Alias", source = "mal", malId = 2002, year = 2024)
        val g2CompatibleWithG0 = entry(id = 3, title = "Different Main Title", source = "kitsu", malId = 300_000_099, year = 2024)
            .copy(titleEnglish = "Shared Alias")

        val extras = mapOf(g2CompatibleWithG0.id to setOf("Anime:mal:1001"))
        val grouped = groupMyListEntries(
            listOf(g0, g1Incompatible, g2CompatibleWithG0),
            identityExtras = extras
        )

        assertEquals("Ortadaki uyumsuz grup G0 ile G2'nin birleşmesini engellememeli", 2, grouped.size)
        val g0Merged = grouped.first { it.entry.id == g0.id }
        assertEquals(listOf("anilist", "kitsu"), g0Merged.sourceIds)
    }

    @Test
    fun demonSlayerInfinityCastleMovieAndAnimeSameSourceDeduplicatesInSimkl() {
        val tmdbAddedMovie = entry(
            id = 20,
            title = "Demon Slayer: Kimetsu no Yaiba Infinity Castle",
            source = "simkl",
            malId = null,
            type = MediaType.Movie,
            year = 2025
        ).copy(
            status = WatchStatus.Completed,
            score = 8,
            progress = 1,
            total = 1,
            isFavorite = true,
            tmdbId = 1311031
        )
        val simklImportedAnime = entry(
            id = 21,
            title = "Demon Slayer: Kimetsu no Yaiba Infinity Castle",
            source = "simkl",
            malId = 59192,
            type = MediaType.Anime,
            year = 2025
        ).copy(
            status = WatchStatus.Completed,
            score = 8,
            progress = 1,
            total = 1,
            isFavorite = false,
            tmdbId = 1311031,
            simklId = 2258525
        )

        val deduped = deduplicateSingleSourceEntries(listOf(tmdbAddedMovie, simklImportedAnime))

        assertEquals("Simkl sekmesinde aynı anime filmi iki kez gösterilmemeli", 1, deduped.size)
        assertTrue("Favori yıldızı tekilleştirilmiş kartta korunmalı", deduped.single().isFavorite)
    }
}
