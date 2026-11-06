package com.kitsugi.animelist.data.manga

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Çok adlı arama hattı testleri.
 *
 * "Yerel / çevrilmiş ad" modunun gerçekten çalışması iki sözleşmeye bağlıdır:
 *  1. Kaynak, verilen ad listesini **sırayla** dener ve ilk dolu sonuçta durur.
 *  2. Eleme, listedeki **herhangi** bir adla eşleşen sonucu tutar (yerel adla aranıp
 *     orijinal adla dönen kart kaybolmaz).
 */
class MangaMultiTitleSearchTest {

    private class FakeSource(
        override val name: String,
        override val baseUrl: String,
        override val lang: String = "tr",
        val resultsByQuery: Map<String, List<MangaDetails>>,
        val failOnQuery: Set<String> = emptySet(),
    ) : MangaSource {
        val searchedQueries = mutableListOf<String>()

        override val pkgName: String = "test.${name.lowercase()}"

        override suspend fun fetchPopularManga(page: Int): MangaSourceResult =
            MangaSourceResult(emptyList(), hasNextPage = false)

        override suspend fun fetchSearchManga(page: Int, query: String): MangaSourceResult {
            searchedQueries += query
            if (query in failOnQuery) throw java.io.IOException("boom: $query")
            return MangaSourceResult(
                mangas = resultsByQuery[query].orEmpty(),
                hasNextPage = false,
            )
        }

        override suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails =
            MangaDetails(url = mangaUrl, title = mangaUrl)

        override suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter> = emptyList()

        override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = emptyList()
    }

    private fun details(title: String, url: String = "https://example.org/${title.hashCode()}") =
        MangaDetails(url = url, title = title, thumbnailUrl = null)

    @Test
    fun multiQueryScoringAcceptsResultMatchedUnderAnyAcceptedTitle() {
        val source = FakeSource("Manga-TR", "https://manga-tr.com", resultsByQuery = emptyMap())
        val accepted = listOf("역대급 영지 설계사", "Dahi Malikane Geliştiricisi")
            .map { CanonicalMangaResolver.resolve(it) }

        val localizedHit = details("Dahi Malikane Geliştiricisi")
        val unrelated = details("Alakasız Bir Şey")

        // Çok sorgulu elemenin kullandığı kural: her sonuç için EN İYİ skor.
        val localizedScore = accepted.maxOf { SearchResultScorer.score(source, it, localizedHit) }
        val unrelatedScore = accepted.maxOf { SearchResultScorer.score(source, it, unrelated) }

        assertTrue("Yerel adla eşleşen sonuç pozitif skor almalı", localizedScore > 0.0)
        assertEquals("Alakasız sonuç hiçbir adla eşleşmemeli", 0.0, unrelatedScore, 0.0001)
    }

    @Test
    fun singleQueryScoringKeepsExactMatch() {
        val source = FakeSource("Manga-TR", "https://manga-tr.com", resultsByQuery = emptyMap())
        val query = CanonicalMangaResolver.resolve("Solo Leveling")

        assertTrue(SearchResultScorer.score(source, query, details("Solo Leveling")) >= 1.0)
        assertEquals(
            0.0,
            SearchResultScorer.score(source, query, details("One Piece")),
            0.0001
        )
    }

    @Test
    fun sourceIsSearchedInPlanOrderAndStopsOnFirstHit() = runBlocking {
        val plan = MangaTitleVariantBuilder.build(
            query = "The Greatest Estate Developer",
            mode = MangaSearchTitleMode.LOCALIZED,
            originalTitles = listOf("역대급 영지 설계사"),
            localizedTitles = listOf("Dahi Malikane Geliştiricisi"),
        )

        // Yalnızca Türkçe ad sonuç veriyor → ilk denemede durmalı, orijinal ada hiç gitmemeli.
        val source = FakeSource(
            name = "Manga-TR",
            baseUrl = "https://manga-tr.com",
            resultsByQuery = mapOf(
                "Dahi Malikane Geliştiricisi" to listOf(details("Dahi Malikane Geliştiricisi")),
                "역대급 영지 설계사" to listOf(details("역대급 영지 설계사")),
            ),
        )

        val outcome = searchWithPlan(source, plan)

        assertEquals("Dahi Malikane Geliştiricisi", outcome.second)
        assertEquals(
            "İlk isabetten sonra ek istek atılmamalı",
            listOf("Dahi Malikane Geliştiricisi"),
            source.searchedQueries
        )
    }

    @Test
    fun fallsBackToOriginalTitleWhenLocalizedTitleIsEmpty() = runBlocking {
        val plan = MangaTitleVariantBuilder.build(
            query = "The Greatest Estate Developer",
            mode = MangaSearchTitleMode.LOCALIZED,
            originalTitles = listOf("역대급 영지 설계사"),
            localizedTitles = listOf("Dahi Malikane Geliştiricisi"),
        )

        val source = FakeSource(
            name = "Manga-TR",
            baseUrl = "https://manga-tr.com",
            resultsByQuery = mapOf(
                "The Greatest Estate Developer" to listOf(details("The Greatest Estate Developer")),
            ),
        )

        val outcome = searchWithPlan(source, plan)

        assertEquals("The Greatest Estate Developer", outcome.second)
        assertTrue(source.searchedQueries.contains("Dahi Malikane Geliştiricisi"))
        assertTrue(source.searchedQueries.contains("The Greatest Estate Developer"))
    }

    @Test
    fun originalModeTriesOriginalTitleFirst() = runBlocking {
        val plan = MangaTitleVariantBuilder.build(
            query = "The Greatest Estate Developer",
            mode = MangaSearchTitleMode.ORIGINAL,
            originalTitles = listOf("역대급 영지 설계사"),
            localizedTitles = listOf("Dahi Malikane Geliştiricisi"),
        )

        val source = FakeSource(
            name = "Mangitto",
            baseUrl = "https://mangitto.example",
            resultsByQuery = mapOf(
                "역대급 영지 설계사" to listOf(details("역대급 영지 설계사")),
            ),
        )

        val outcome = searchWithPlan(source, plan)

        assertEquals("역대급 영지 설계사", outcome.second)
        assertEquals("역대급 영지 설계사", source.searchedQueries.first())
    }

    @Test
    fun throwingFirstVariantDoesNotAbortTheWholeSearch() = runBlocking {
        val plan = MangaTitleVariantBuilder.build(
            query = "The Greatest Estate Developer",
            mode = MangaSearchTitleMode.LOCALIZED,
            originalTitles = listOf("역대급 영지 설계사"),
            localizedTitles = listOf("Dahi Malikane Geliştiricisi"),
        )

        val source = FakeSource(
            name = "MangaciX",
            baseUrl = "https://mangacix.example",
            resultsByQuery = mapOf(
                "The Greatest Estate Developer" to listOf(details("The Greatest Estate Developer")),
            ),
            failOnQuery = setOf("Dahi Malikane Geliştiricisi"),
        )

        val outcome = searchWithPlan(source, plan)

        assertEquals("The Greatest Estate Developer", outcome.second)
    }

    @Test
    fun identityPlanIsNeverEmptySoSearchAlwaysRuns() {
        val plan = MangaSearchPlan.identity("Berserk", MangaSearchTitleMode.LOCALIZED)
        assertTrue(plan.safeVariants().isNotEmpty())
    }

    /**
     * ViewModel'deki `searchSourceWithFallback` ile aynı sıra/kısa devre davranışı.
     * (ViewModel Android'e bağlı olduğu için mantık burada ayrıca sabitlenir.)
     */
    private suspend fun searchWithPlan(
        source: FakeSource,
        plan: MangaSearchPlan,
    ): Pair<MangaSourceResult, String?> {
        var last: MangaSourceResult? = null
        var lastTitle: String? = null
        plan.safeVariants().forEachIndexed { index, variant ->
            val result = try {
                source.fetchSearchManga(1, variant)
            } catch (e: Exception) {
                if (index == 0 && plan.safeVariants().size == 1) throw e
                return@forEachIndexed
            }
            lastTitle = variant
            last = result
            if (result.mangas.isNotEmpty()) return result to variant
        }
        return (last ?: MangaSourceResult(emptyList(), false)) to lastTitle
    }
}
