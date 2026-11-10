package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arama rafı alakalılığı, kimlik tekilleştirmesi ve kapsam → kaynak planı testleri.
 *
 * Bu üç davranış "Tümü" arama sayfasında görülen hataların kaynağıydı:
 *  - aynı numaralı anime/manga kayıtlarından biri sessizce siliniyordu,
 *  - kaynakların popülerlik sıralaması alakasız kayıtları rafın başına koyuyordu,
 *  - kapsamın üretemediği içerik ailesi için de kaynak sorgulanıyordu.
 */
class SearchRelevanceTest {

    private fun result(
        id: Int,
        title: String,
        source: String = "anilist",
        type: MediaType = MediaType.Anime,
        titleEnglish: String? = null,
        titleJapanese: String? = null
    ) = JikanSearchResult(
        malId = id,
        title = title,
        subtitle = "",
        type = type,
        total = null,
        score = null,
        isAdult = false,
        imageUrl = null,
        year = null,
        source = source,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese
    )

    // ── Kimlik tekilleştirme ────────────────────────────────────────────────

    @Test fun sameIdFromAnimeAndMangaIsNotCollapsed() {
        val anime = result(1, "Cowboy Bebop", source = "kitsu", type = MediaType.Anime)
        val manga = result(1, "Cowboy Bebop", source = "kitsu", type = MediaType.Manga)
        assertEquals(2, SearchRelevance.dedupe(listOf(anime, manga)).size)
    }

    @Test fun sameIdFromMovieAndTvIsNotCollapsed() {
        val movie = result(603, "The Matrix", source = "tmdb", type = MediaType.Movie)
        val show = result(603, "The Matrix", source = "tmdb", type = MediaType.TvShow)
        assertEquals(2, SearchRelevance.dedupe(listOf(movie, show)).size)
    }

    @Test fun trueDuplicateCollapses() {
        val first = result(20, "Naruto", source = "mal")
        val second = result(20, "Naruto", source = "mal")
        assertEquals(1, SearchRelevance.dedupe(listOf(first, second)).size)
    }

    // ── Alakalılık sıralaması ───────────────────────────────────────────────

    @Test fun queryMatchRisesAbovePopularityDump() {
        val popular = listOf(result(1, "One Piece"), result(2, "Bleach"), result(3, "Dragon Ball"))
        val match = result(4, "Naruto Shippuuden")
        val ranked = SearchRelevance.rank("naruto", popular + match)
        assertEquals("Naruto Shippuuden", ranked.first().title)
        assertEquals("hiçbir kayıt silinmemeli", 4, ranked.size)
    }

    @Test fun englishAliasOfRomajiTitleIsRecognised() {
        val alias = result(1, "Shingeki no Kyojin", titleEnglish = "Attack on Titan")
        val other = result(2, "Gintama")
        val ranked = SearchRelevance.rank("attack on titan", listOf(other, alias))
        assertEquals("Shingeki no Kyojin", ranked.first().title)
    }

    @Test fun turkishDiacriticsDoNotBreakMatch() {
        val item = result(1, "Çocuk ve Büyücü")
        val other = result(2, "Bambaşka")
        val ranked = SearchRelevance.rank("cocuk", listOf(other, item))
        assertEquals("Çocuk ve Büyücü", ranked.first().title)
    }

    @Test fun sourceOrderIsPreservedWithinSameTier() {
        val first = result(1, "Naruto Shippuuden")
        val second = result(2, "Naruto SD")
        assertEquals(
            listOf("Naruto Shippuuden", "Naruto SD"),
            SearchRelevance.rank("naruto", listOf(first, second)).map { it.title }
        )
    }

    @Test fun blankQueryLeavesListUntouched() {
        val items = listOf(result(1, "Zebra"), result(2, "Apple"))
        assertEquals(items, SearchRelevance.rank("", items))
        assertEquals(items, SearchRelevance.rank("   ", items))
    }

    @Test fun unmatchedAliasIsKeptAtTheTailNotDropped() {
        // Bangumi/Shikimori gibi kaynaklar eş anlamlıdan eşleşip tamamen farklı
        // yazımlı (Çince/Japonca) başlık dönebilir; bunlar silinmez, sona itilir.
        val native = result(1, "進撃の巨人", titleJapanese = "進撃の巨人")
        val match = result(2, "Attack on Titan")
        val ranked = SearchRelevance.rank("attack on titan", listOf(native, match))
        assertEquals(listOf("Attack on Titan", "進撃の巨人"), ranked.map { it.title })
    }

    @Test fun foldNormalisesTurkishAndAccentedLetters() {
        assertEquals("iissgguuoocc", SearchRelevance.fold("IıŞşĞğÜüÖöÇç"))
        assertEquals("one piece film z", SearchRelevance.fold("  One-Piece  FILM:Z! "))
    }

    @Test fun capitalDottedIMatchesLowercaseQuery() {
        // 'İ'.lowercase() "i" + birleşik nokta üretir; işaret yok sayılmazsa
        // "İstanbul" → "i stanbul" olur ve "istanbul" sorgusu eşleşmezdi.
        assertEquals("istanbul", SearchRelevance.fold("İstanbul"))
        val item = result(1, "İstanbul Muhafızları")
        val other = result(2, "Bambaşka Bir Yer")
        assertEquals(
            "İstanbul Muhafızları",
            SearchRelevance.rank("istanbul", listOf(other, item)).first().title
        )
    }

    // ── Karma kapsam birleştirme ────────────────────────────────────────────

    @Test fun interleaveKeepsMangaVisibleInMixedShelf() {
        val anime = (1..10).map { result(it, "Anime $it", type = MediaType.Anime) }
        val manga = (1..10).map { result(100 + it, "Manga $it", type = MediaType.Manga) }
        val shelf = SearchRelevance.interleave(anime, manga).take(10)
        assertEquals(5, shelf.count { it.type == MediaType.Manga })
        assertEquals(5, shelf.count { it.type == MediaType.Anime })
    }

    @Test fun interleaveToleratesEmptySide() {
        val manga = listOf(result(1, "Manhwa Title", type = MediaType.Manga))
        assertEquals(manga, SearchRelevance.interleave(emptyList(), manga))
        assertEquals(manga, SearchRelevance.interleave(manga, emptyList()))
    }

    // ── Kapsam → kaynak planı ───────────────────────────────────────────────

    @Test fun printScopesNeverAskLiveActionSources() {
        for (scope in listOf(
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL
        )) {
            val plan = planAllSources(scope)
            assertFalse("$scope TMDB istememeli", plan.tmdb)
            assertFalse("$scope Simkl istememeli", plan.simkl)
            assertTrue("$scope AniList istemeli", plan.aniList)
            assertTrue("$scope Kitsu istemeli", plan.kitsu)
            assertTrue("$scope manga verisi istemeli", scope.wantsMangaSources)
            assertFalse("$scope anime verisi istememeli", scope.wantsAnimeSources)
            assertFalse(plan.toLoadingState().isLoadingTmdb)
            assertTrue(plan.toLoadingState().isLoadingKitsu)
        }
    }

    /**
     * Canlı çekim kapsamları **anime veritabanlarını** kesinlikle sorgulamamalı.
     *
     * Bangumi bu kuralın istisnasıdır: tek katalogda anime (type=2), 书籍 (type=1) ve
     * **三次元** (type=6) dizileri/filmleri birlikte indeksler. Canlı API probu
     * (`probe/result.txt` T3–T5) `type=6` sorgusunda 是，大臣 (rank 1), 三国演义 ve
     * `keyword="reply 1988"` → 请回答1988 kayıtlarını döndürdü. Bu yüzden Bangumi
     * dizi/film kapsamlarında **istenmelidir**; aksi hâlde "Bangumi dizileri/filmleri
     * gelmiyor" hatası geri döner (R6/R14).
     */
    @Test fun liveActionScopesAskOnlyLiveActionCapableSources() {
        for (scope in listOf(SearchScope.TV, SearchScope.MOVIE, SearchScope.K_DRAMA)) {
            val plan = planAllSources(scope)
            assertTrue("$scope TMDB istemeli", plan.tmdb)
            assertTrue("$scope Simkl istemeli", plan.simkl)
            assertTrue("$scope Bangumi 三次元 kataloğu için istenmeli", plan.bangumi)
            assertFalse("$scope AniList istememeli", plan.aniList)
            assertFalse("$scope MAL istememeli", plan.mal)
            assertFalse("$scope Shikimori istememeli", plan.shikimori)
            assertFalse("$scope Kitsu istememeli", plan.kitsu)
        }
    }

    @Test fun mixedScopeAsksEverySource() {
        val plan = planAllSources(SearchScope.ALL_MIXED)
        assertTrue(plan.aniList && plan.mal && plan.tmdb && plan.shikimori &&
            plan.kitsu && plan.simkl && plan.bangumi)
        assertNull(SearchScope.ALL_MIXED.simklTypeFilter)
        assertTrue(plan.toLoadingState().isAnyLoading)
        assertTrue("yükleme durumu sonuç içermez", plan.toLoadingState().isEmpty)
    }

    @Test fun animeScopeKeepsSimklButDropsTmdb() {
        val plan = planAllSources(SearchScope.ANIME)
        assertTrue(plan.simkl)
        assertFalse("TMDB anime ile live-action uyarlamayı ayırt edemez", plan.tmdb)
        assertEquals("anime", SearchScope.ANIME.simklTypeFilter)
    }

    @Test fun simklTypeFilterFollowsScope() {
        assertEquals("tv", SearchScope.TV.simklTypeFilter)
        assertEquals("tv", SearchScope.K_DRAMA.simklTypeFilter)
        assertEquals("movie", SearchScope.MOVIE.simklTypeFilter)
    }
}
