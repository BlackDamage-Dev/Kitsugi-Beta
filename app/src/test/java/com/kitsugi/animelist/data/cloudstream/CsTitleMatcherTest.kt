package com.kitsugi.animelist.data.cloudstream

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SearchResponse
import org.junit.Assert.*
import org.junit.Test

/**
 * CsTitleMatcher için kapsamlı JVM unit testleri.
 *
 * Kapsam:
 *  - Anime (sezon 1, sezon 2+, Roman rakam sezonlar)
 *  - Dizi (DiziBox, Dizilla, DiziPal, RecTV naming patterns)
 *  - Film (yıl baskın eşleştirme)
 *  - Slug tabanlı sezon çıkarma (tüm bilinen Türk site URL formatları)
 *  - Alternatif başlık (romaji, Türkçe, İngilizce)
 *  - Kısmi eşleştirme ve fallback senaryoları
 *  - getBestTitleSimilarity fonksiyonu
 */
class CsTitleMatcherTest {

    @Test
    fun testPlainTitleVariantsStartWithRawTitleBeforeAliases() {
        val variants = CsTitleMatcher.buildPlainTitleVariants(
            main = "The Raw Title",
            alts = listOf("Localized Alias"),
            isMovie = false
        )

        assertEquals("The Raw Title", variants.first())
        assertTrue(variants.contains("Localized Alias"))
        assertFalse(variants.any { it.contains("S2", ignoreCase = true) || it.contains("episode 3", ignoreCase = true) })
    }

    @Test
    fun testFindBestMatchRejectsUnrelatedSeasonNavigationResult() {
        val match = CsTitleMatcher.findBestMatch(
            results = listOf(createSearchResponse("Dragon Ball", 1986)),
            mainTitle = "Spy x Family",
            altTitles = listOf("Spy Family"),
            targetYear = 2022,
            targetSeason = 2,
            targetEpisode = 1,
            isMovie = false
        )

        assertNull("An unrelated fallback result must not be loaded", match)
    }

    // ─── Season-Specific Anime Variant Tests ─────────────────────────────────

    @Test
    fun testBuildTitleVariantsWithSeason() {
        val main = "Jujutsu Kaisen"
        val alts = listOf("Sorcery Fight")

        val variants = CsTitleMatcher.buildTitleVariants(main, alts, season = 2)

        assertTrue("Should contain '2. Sezon' variant", variants.contains("Jujutsu Kaisen 2. Sezon"))
        assertTrue("Should contain 'Season 2' variant", variants.contains("Jujutsu Kaisen Season 2"))
        assertTrue("Should contain 'S2' variant", variants.contains("Jujutsu Kaisen S2"))
        assertTrue("Should contain alt '2. Sezon' variant", variants.contains("Sorcery Fight 2. Sezon"))
        assertTrue("Should contain alt 'Season 2' variant", variants.contains("Sorcery Fight Season 2"))
        // Base title must still be included
        assertTrue("Should still contain base title", variants.contains("Jujutsu Kaisen"))
    }

    @Test
    fun testBuildTitleVariantsSeason1NoPrependedSeasonSuffixes() {
        val variants = CsTitleMatcher.buildTitleVariants("Demon Slayer", emptyList(), season = 1)
        // For season 1, season-specific queries should NOT be prepended
        assertFalse("Season-1 should not prepend '1. Sezon' queries", variants.contains("Demon Slayer 1. Sezon"))
        // But base title should be there
        assertTrue(variants.contains("Demon Slayer"))
    }

    @Test
    fun testBuildTitleVariantsFilmNoSeason() {
        val main = "Avengers: Endgame"
        val variants = CsTitleMatcher.buildTitleVariants(main, emptyList(), season = 1)
        // Base title must be present
        assertTrue(variants.contains("Avengers: Endgame"))
        // ASCII normalized version
        assertTrue("ASCII normalized version should exist", variants.any { it.contains("Avengers") && it.contains("Endgame") })
    }

    @Test
    fun testBuildTitleVariantsWithTurkishChars() {
        val main = "Şahane Aile"
        val variants = CsTitleMatcher.buildTitleVariants(main, emptyList(), season = 1)
        // ASCII version: Ş→S, should appear
        assertTrue("ASCII version of Turkish title should appear",
            variants.any { it.contains("Sahane") || it.contains("sahane") })
    }

    // ─── FindBestMatch: Anime Season Matching ────────────────────────────────

    @Test
    fun testFindBestMatchWithSeason() {
        val results = listOf(
            createSearchResponse("Jujutsu Kaisen", 2020),
            createSearchResponse("Jujutsu Kaisen 2. Sezon", 2023),
            createSearchResponse("Jujutsu Kaisen 3. Sezon", 2025)
        )

        val matchS2 = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Jujutsu Kaisen",
            altTitles = emptyList(),
            targetYear = 2023,
            targetSeason = 2
        )
        assertNotNull("Season 2 match should not be null", matchS2)
        assertEquals("Jujutsu Kaisen 2. Sezon", matchS2?.name)

        val matchS1 = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Jujutsu Kaisen",
            altTitles = emptyList(),
            targetYear = 2020,
            targetSeason = 1
        )
        assertNotNull("Season 1 match should not be null", matchS1)
        assertEquals("Jujutsu Kaisen", matchS1?.name)
    }

    @Test
    fun testFindBestMatchSequelStandalone() {
        val results = listOf(
            createSearchResponse("Tsue to Tsurugi no Wistoria", 2024),
            createSearchResponse("Tsue to Tsurugi no Wistoria Season 2", 2026)
        )

        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Tsue to Tsurugi no Wistoria Season 2",
            altTitles = emptyList(),
            targetYear = 2026,
            targetSeason = 1
        )
        assertNotNull(match)
        assertEquals("Tsue to Tsurugi no Wistoria Season 2", match?.name)
    }

    @Test
    fun testFindBestMatchImplicitSeasonPenalty() {
        // When we search for season 3, a result with no season marker should be penalised
        val results = listOf(
            createSearchResponse("Attack on Titan", 2013),               // implicit S1
            createSearchResponse("Attack on Titan 3. Sezon", 2018)        // explicit S3
        )
        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Attack on Titan",
            altTitles = listOf("Shingeki no Kyojin"),
            targetYear = 2018,
            targetSeason = 3
        )
        assertNotNull(match)
        assertEquals("Attack on Titan 3. Sezon", match?.name)
    }

    @Test
    fun testFindBestMatchRomanNumeralSeason() {
        // Roman numeral seasons are common on some Turkish sites
        val results = listOf(
            createSearchResponse("Vinland Saga", 2019),
            createSearchResponse("Vinland Saga II", 2023)
        )
        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Vinland Saga Season 2",
            altTitles = listOf("Vinland Saga"),
            targetYear = 2023,
            targetSeason = 2
        )
        assertNotNull(match)
        assertEquals("Vinland Saga II", match?.name)
    }

    @Test
    fun testFindBestMatchRomanNumeralIII() {
        val results = listOf(
            createSearchResponse("Overlord I", 2015),
            createSearchResponse("Overlord II", 2018),
            createSearchResponse("Overlord III", 2018),
            createSearchResponse("Overlord IV", 2022)
        )
        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Overlord",
            altTitles = emptyList(),
            targetYear = 2018,
            targetSeason = 3
        )
        assertNotNull(match)
        assertEquals("Overlord III", match?.name)
    }

    // ─── FindBestMatch: Film Scenarios ───────────────────────────────────────

    @Test
    fun testFindBestMatchFilmYearBoosted() {
        val results = listOf(
            createSearchResponse("Inception", 2010),
            createSearchResponse("Inception 2 Fan Film", 2020)
        )
        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Inception",
            altTitles = emptyList(),
            targetYear = 2010,
            targetSeason = 1
        )
        assertNotNull(match)
        assertEquals("Inception", match?.name)
    }

    @Test
    fun testFindBestMatchFilmNoYear() {
        // No year available — should still match on title similarity alone
        val results = listOf(
            createSearchResponse("Interstellar", null),
            createSearchResponse("Interstella 5555", null)
        )
        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Interstellar",
            altTitles = emptyList(),
            targetYear = null,
            targetSeason = 1
        )
        assertNotNull(match)
        assertEquals("Interstellar", match?.name)
    }

    @Test
    fun testFindBestMatchFilmAltTitle() {
        // Plugin returns the Turkish name, we search by the English title
        val results = listOf(
            createSearchResponse("Uzay Yolculuğu 2001", 1968),
            createSearchResponse("2001: A Space Odyssey", 1968)
        )
        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "2001: A Space Odyssey",
            altTitles = listOf("Uzay Yolculuğu 2001"),
            targetYear = 1968,
            targetSeason = 1
        )
        assertNotNull(match)
        // Either match is acceptable — just verify we found something
        assertNotNull(match?.name)
    }

    // ─── FindBestMatch: Turkish TV Series Scenarios ──────────────────────────

    @Test
    fun testFindBestMatchDiziBoxPattern() {
        // DiziBox names episodes as "Serie Title 3. Sezon 5. Bölüm" inside episode objects,
        // but the search result itself is just the series title.
        // At the findBestMatch level, we should match the series by title.
        val results = listOf(
            createSearchResponse("Kuzey Yıldızı İlk Aşk", null),
            createSearchResponse("Kuzey Yarım Küre", null)
        )
        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Kuzey Yıldızı İlk Aşk",
            altTitles = emptyList(),
            targetYear = null,
            targetSeason = 1
        )
        assertNotNull(match)
        assertEquals("Kuzey Yıldızı İlk Aşk", match?.name)
    }

    @Test
    fun testFindBestMatchDizillaPattern() {
        // Dizilla typically lists TV series without season in the title.
        val results = listOf(
            createSearchResponse("Çukur", 2017),
            createSearchResponse("Çukur 2. Sezon", 2018),
            createSearchResponse("Çukurova Masalı", 2021)
        )
        val matchS2 = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Çukur",
            altTitles = emptyList(),
            targetYear = 2018,
            targetSeason = 2
        )
        assertNotNull(matchS2)
        assertEquals("Çukur 2. Sezon", matchS2?.name)
    }

    @Test
    fun testFindBestMatchKoreanDramaPattern() {
        // KoreanTurk & AsyaWatch: Korean dramas with Korean title in parentheses
        val results = listOf(
            createSearchResponse("İtme Beni (밀어내기)", 2023),
            createSearchResponse("Kimse Bilmez", 2023)
        )
        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "İtme Beni",
            altTitles = listOf("밀어내기", "Push Me"),
            targetYear = 2023,
            targetSeason = 1
        )
        assertNotNull(match)
        assertEquals("İtme Beni (밀어내기)", match?.name)
    }

    @Test
    fun unicodeAliasesAndFranchiseSuffixesArePreserved() {
        assertEquals("naruto shippuden", CsTitleMatcher.normalizeTitleForMatch("Naruto: Shippuden"))
        assertEquals("呪術廻戦", CsTitleMatcher.normalizeTitleForMatch("呪術廻戦"))
        assertEquals(1.0, CsTitleMatcher.getBestTitleSimilarity("呪術廻戦", "Jujutsu Kaisen", listOf("呪術廻戦")), 0.001)
        val nativeOnlyMatch = CsTitleMatcher.findBestMatch(
            results = listOf(createSearchResponse("呪術廻戦", "https://example.com/jujutsu", null, TvType.Anime)),
            mainTitle = "Jujutsu Kaisen",
            altTitles = listOf("呪術廻戦"),
            targetYear = null,
            targetSeason = 1,
            isMovie = false
        )
        assertEquals("呪術廻戦", nativeOnlyMatch?.name)
        assertEquals("Naruto Shippuden", CsTitleMatcher.extractCleanBaseTitle("Naruto Shippuden"))
    }

    @Test
    fun matcherNormalizesTurkishDottedIAndAsciiTransliterations() {
        assertEquals("istanbul cagisi", CsTitleMatcher.normalizeTitleForMatch("İstanbul Çağısı"))
        val match = CsTitleMatcher.findBestMatch(
            results = listOf(createSearchResponse("İstanbul Çağrısı", 2024)),
            mainTitle = "Istanbul Cagrisi",
            altTitles = emptyList(),
            targetYear = 2024,
            targetSeason = 1,
            isMovie = false
        )

        assertEquals("İstanbul Çağrısı", match?.name)
    }

    @Test
    fun matcherRejectsUnrelatedAnimeForTurkishSeriesTitle() {
        val unrelatedAnimeTrailerResult = createSearchResponse(
            "Arifureta: From Commonplace to World's Strongest Season 2",
            "https://example.com/arifureta-season-2",
            2022,
            TvType.Anime
        )

        val match = CsTitleMatcher.findBestMatch(
            results = listOf(unrelatedAnimeTrailerResult),
            mainTitle = "Osmanlı Tokadı",
            altTitles = emptyList(),
            targetYear = 2013,
            targetSeason = 1,
            isMovie = false
        )

        assertNull("An unrelated provider result must never supply this title's trailer", match)
    }

    @Test
    fun matcherRejectsOnlyFirstWordFranchiseCollision() {
        val results = listOf(
            createSearchResponse("Naruto", 2002),
            createSearchResponse("Naruto Shippuden", 2007)
        )

        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Naruto Shippuden",
            altTitles = emptyList(),
            targetYear = 2007,
            targetSeason = 1,
            isMovie = false
        )

        assertEquals("Naruto Shippuden", match?.name)
    }

    @Test
    fun singleWordFranchiseAliasCannotOverrideAFullTitleMismatch() {
        val results = listOf(
            createSearchResponse("Naruto", 2002),
            createSearchResponse("Naruto Shippuden", 2007)
        )

        val match = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Naruto Shippuden",
            altTitles = listOf("Naruto"),
            targetYear = 2007,
            targetSeason = 1,
            isMovie = false
        )

        assertEquals("Naruto Shippuden", match?.name)
    }

    @Test
    fun matcherRejectsExplicitWrongSeasonForSeasonOne() {
        val result = createSearchResponse("Demon Slayer 2. Sezon", 2021)

        val match = CsTitleMatcher.findBestMatch(
            results = listOf(result),
            mainTitle = "Demon Slayer",
            altTitles = emptyList(),
            targetYear = 2021,
            targetSeason = 1,
            isMovie = false
        )

        assertNull("A mismatched season must not be used as a fallback", match)
        assertFalse(
            CsTitleMatcher.isCandidateMetadataCompatible(
                result, "Demon Slayer", emptyList(), 2021, 1, 1, isMovie = false
            )
        )
    }

    @Test
    fun mediaTypeDisambiguatesSameNamedMovieAndSeries() {
        val series = createSearchResponse("The Silent Voice", "https://example.com/series", 2024, TvType.TvSeries)
        val movie = createSearchResponse("The Silent Voice", "https://example.com/movie", 2024, TvType.Movie)
        val live = createSearchResponse("The Silent Voice", "https://example.com/live", 2024, TvType.Live)

        val movieMatch = CsTitleMatcher.findBestMatch(
            results = listOf(series, movie),
            mainTitle = "The Silent Voice",
            altTitles = emptyList(),
            targetYear = 2024,
            targetSeason = 1,
            isMovie = true
        )
        val seriesMatch = CsTitleMatcher.findBestMatch(
            results = listOf(movie, series),
            mainTitle = "The Silent Voice",
            altTitles = emptyList(),
            targetYear = 2024,
            targetSeason = 1,
            isMovie = false
        )
        val liveMatch = CsTitleMatcher.findBestMatch(
            results = listOf(live),
            mainTitle = "The Silent Voice",
            altTitles = emptyList(),
            targetYear = 2024,
            targetSeason = 1,
            isMovie = true
        )

        assertEquals(movie.url, movieMatch?.url)
        assertEquals(series.url, seriesMatch?.url)
        assertNull(liveMatch)
    }

    @Test
    fun misTaggedExactTitleIsKeptWhenProviderOnlyReturnsTheOtherType() {
        // TR plugins frequently tag every result as Movie (or every result as TvSeries).
        // A 1.00 title used to be hard-rejected → "isim gitti, devamı gelmedi".
        val movieTaggedSeries = createSearchResponse(
            "Supernatural",
            "https://example.com/supernatural",
            2005,
            TvType.Movie
        )
        val seriesMatch = CsTitleMatcher.findBestMatch(
            results = listOf(movieTaggedSeries),
            mainTitle = "Supernatural",
            altTitles = listOf("SUPERNATURAL スーパーナチュラル"),
            targetYear = 2005,
            targetSeason = 1,
            targetEpisode = 1,
            isMovie = false
        )
        assertEquals(movieTaggedSeries.url, seriesMatch?.url)

        val seriesTaggedMovie = createSearchResponse(
            "Inception",
            "https://example.com/inception",
            2010,
            TvType.TvSeries
        )
        val movieMatch = CsTitleMatcher.findBestMatch(
            results = listOf(seriesTaggedMovie),
            mainTitle = "Inception",
            altTitles = emptyList(),
            targetYear = 2010,
            isMovie = true
        )
        assertEquals(seriesTaggedMovie.url, movieMatch?.url)
    }

    @Test
    fun unnumberedMovieQueryNeverAcceptsOnlySequelWithoutYearProof() {
        // Kullanıcı "Avatar" (2009) izle dediğinde sitede yalnızca devam filmi (Avatar 2) varsa
        // yanlış filmi oynatmamalı. Önceki davranış bu adayı skor eşiğinden geçiriyordu.
        val sequelOnly = listOf(
            createSearchResponse("Avatar 2 izle", "https://example.com/avatar-2", null, TvType.Movie)
        )
        assertNull(
            "Devam filmi tek başına, numarasız film sorgusunu karşılamamalı",
            CsTitleMatcher.findBestMatch(sequelOnly, "Avatar", emptyList(), 2009, 1, null, true)
        )
        assertFalse(
            "Metadata denetimi de devam filmini yedek aday olarak kabul etmemeli",
            CsTitleMatcher.isCandidateMetadataCompatible(
                sequelOnly[0], "Avatar", emptyList(), 2009, 1, null, true
            )
        )

        // Asıl film mevcutsa o seçilmeli (devam filmi sıralamada önde olsa bile).
        val withOriginal = listOf(
            createSearchResponse("Avatar 2 izle", "https://example.com/avatar-2", null, TvType.Movie),
            createSearchResponse("Avatar izle", "https://example.com/avatar", 2009, TvType.Movie)
        )
        assertEquals(
            "Avatar izle",
            CsTitleMatcher.findBestMatch(withOriginal, "Avatar", emptyList(), 2009, 1, null, true)?.name
        )
    }

    @Test
    fun movieLookupDoesNotChooseAnotherSequelOrDistantYear() {
        val results = listOf(
            createSearchResponse("Cars", "https://example.com/cars", 2006, TvType.Movie),
            createSearchResponse("Cars 2", "https://example.com/cars-2", 2011, TvType.Movie)
        )

        val original = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Cars",
            altTitles = emptyList(),
            targetYear = 2006,
            targetSeason = 1,
            isMovie = true
        )
        val sequel = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Cars 2",
            altTitles = listOf("Cars"),
            targetYear = 2011,
            targetSeason = 1,
            isMovie = true
        )

        assertEquals("Cars", original?.name)
        assertEquals("Cars 2", sequel?.name)

        val ambiguousSequel = CsTitleMatcher.findBestMatch(
            results = listOf(createSearchResponse("Cars", "https://example.com/cars", null, TvType.Movie)),
            mainTitle = "Cars 2",
            altTitles = listOf("Cars"),
            targetYear = null,
            targetSeason = 1,
            isMovie = true
        )
        assertNull("A base-title alias alone cannot prove the sequel", ambiguousSequel)

        val leadingNumberTitle = CsTitleMatcher.findBestMatch(
            results = listOf(createSearchResponse("2001: A Space Odyssey", "https://example.com/2001", null, TvType.Movie)),
            mainTitle = "2001: A Space Odyssey",
            altTitles = emptyList(),
            targetYear = 1968,
            targetSeason = 1,
            isMovie = true
        )
        assertEquals("A leading title number is not a release year", "2001: A Space Odyssey", leadingNumberTitle?.name)

        val distantSameTitle = CsTitleMatcher.findBestMatch(
            results = listOf(createSearchResponse("Inception", "https://example.com/fan-film", 2020, TvType.Movie)),
            mainTitle = "Inception",
            altTitles = emptyList(),
            targetYear = 2010,
            targetSeason = 1,
            isMovie = true
        )
        assertNull("A same-title result from a distant movie year must be rejected", distantSameTitle)
    }

    @Test
    fun seasonAndEpisodeParsersSupportJapaneseAndChineseFormats() {
        assertEquals(2, CsTitleMatcher.parseSeasonFromTitle("呪術廻戦 第2期"))
        assertEquals(2, CsTitleMatcher.parseSeasonFromTitle("咒术回战 第二季"))
        assertEquals(3, CsTitleMatcher.parseSeasonFromTitle("咒术回战 第三季"))
        assertEquals(5, CsTitleMatcher.parseEpisodeFromTitle("呪術廻戦 第2期 第5話"))
        assertEquals(5, CsTitleMatcher.parseEpisodeFromTitle("咒术回战 第三季 第5集"))
        assertEquals(12, CsTitleMatcher.parseEpisodeFromTitle("第十二話"))
        assertEquals(5, CsTitleMatcher.parseEpisodeFromTitle("Show S02.E05"))
        assertEquals(5, CsTitleMatcher.parseEpisodeFromTitle("Show 2x05"))
    }

    // ─── ParseSeasonFromSlug Tests ────────────────────────────────────────────

    @Test
    fun testParseSeasonFromSlug() {
        // Standard formats
        assertEquals(2, CsTitleMatcher.parseSeasonFromSlug("https://example.com/anime/naruto-shippuden-2-sezon"))
        assertEquals(3, CsTitleMatcher.parseSeasonFromSlug("https://example.com/sezon-3/naruto"))
        assertEquals(2, CsTitleMatcher.parseSeasonFromSlug("https://example.com/anime/s2/naruto"))
        assertEquals(2, CsTitleMatcher.parseSeasonFromSlug("https://example.com/anime/naruto-2/"))
        assertEquals(4, CsTitleMatcher.parseSeasonFromSlug("https://example.com/anime/overlord-iv"))
        assertNull(CsTitleMatcher.parseSeasonFromSlug("https://example.com/anime/naruto"))
    }

    @Test
    fun testParseSeasonFromSlugTurkAnimeSiteFormats() {
        // TurkAnime: "naruto-shippuden-2-sezon-izle"
        assertEquals(2, CsTitleMatcher.parseSeasonFromSlug("https://www.turkanime.tv/anime/naruto-shippuden-2-sezon-izle"))
        // AnimeciX: "anime/attack-on-titan-season-3"
        assertEquals(3, CsTitleMatcher.parseSeasonFromSlug("https://anm.cx/anime/attack-on-titan-season-3"))
        // Animeler: "anime/black-clover-3/"
        assertEquals(3, CsTitleMatcher.parseSeasonFromSlug("https://animeler.pw/anime/black-clover-3/"))
        // AnimPow: "anime/jujutsu-kaisen-2-sezon"
        assertEquals(2, CsTitleMatcher.parseSeasonFromSlug("https://animpow.com/anime/jujutsu-kaisen-2-sezon"))
    }

    @Test
    fun testParseSeasonFromSlugAsyaWatchFormats() {
        // AsyaWatch: K-drama slug often doesn't have season marker — should return null
        assertNull(CsTitleMatcher.parseSeasonFromSlug("https://asyawatch.com/drama/itme-beni"))
        // But if it does have a season
        assertEquals(2, CsTitleMatcher.parseSeasonFromSlug("https://asyawatch.com/drama/itme-beni-2"))
    }

    @Test
    fun testParseSeasonFromSlugDiziSiteFormats() {
        // DiziBox: "dizi/dizinin-adi-2-sezon"
        assertEquals(2, CsTitleMatcher.parseSeasonFromSlug("https://www.dizibox.live/dizi/gece-yarisi-2-sezon"))
        // DiziPal: "dizi/cukur/sezon-3"
        assertEquals(3, CsTitleMatcher.parseSeasonFromSlug("https://dizipal950.com/dizi/cukur/sezon-3"))
        // Dizilla: sometimes no season
        assertNull(CsTitleMatcher.parseSeasonFromSlug("https://dizilla.nl/dizi/cukur"))
    }

    @Test
    fun testParseSeasonFromSlugEdgeCases() {
        // URL contains year that looks like it could be mistaken for season
        // "2001" should not be parsed as season (only 2-10 for trailing digit)
        assertNull(CsTitleMatcher.parseSeasonFromSlug("https://example.com/film/2001-uzay-macerasi"))
        // Trailing digit = 1 should not be parsed (only 2-10 range for trailing)
        assertNull(CsTitleMatcher.parseSeasonFromSlug("https://example.com/anime/naruto-1"))
        // Trailing digit = 11 should not be parsed (>10 limit)
        assertNull(CsTitleMatcher.parseSeasonFromSlug("https://example.com/anime/naruto-11"))
    }

    // ─── FindBestMatch: URL Slug Season Tests ─────────────────────────────────

    @Test
    fun testFindBestMatchWithSlugSeason() {
        val results = listOf(
            createSearchResponse("Jujutsu Kaisen", "https://example.com/jujutsu-kaisen-1", 2020),
            createSearchResponse("Jujutsu Kaisen", "https://example.com/jujutsu-kaisen-2-sezon", 2023)
        )

        val matchS2 = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Jujutsu Kaisen",
            altTitles = emptyList(),
            targetYear = 2023,
            targetSeason = 2
        )
        assertNotNull(matchS2)
        assertEquals("https://example.com/jujutsu-kaisen-2-sezon", matchS2?.url)
    }

    @Test
    fun testFindBestMatchSlugSeasonOverridesImplicit() {
        // Both results have the same name "Sword Art Online"
        // One has slug season-2, other has slug season-1
        val results = listOf(
            createSearchResponse("Sword Art Online", "https://example.com/sao-1", 2012),
            createSearchResponse("Sword Art Online", "https://example.com/sao-season-2", 2014)
        )
        val matchS2 = CsTitleMatcher.findBestMatch(
            results = results,
            mainTitle = "Sword Art Online",
            altTitles = emptyList(),
            targetYear = 2014,
            targetSeason = 2
        )
        assertNotNull(matchS2)
        assertEquals("https://example.com/sao-season-2", matchS2?.url)
    }

    // ─── ParseSeasonFromTitle Tests ───────────────────────────────────────────

    @Test
    fun testParseSeasonFromTitle() {
        assertEquals(2, CsTitleMatcher.parseSeasonFromTitle("Jujutsu Kaisen 2. Sezon"))
        assertEquals(3, CsTitleMatcher.parseSeasonFromTitle("Attack on Titan Season 3"))
        assertEquals(2, CsTitleMatcher.parseSeasonFromTitle("Sword Art Online S2"))
        assertEquals(4, CsTitleMatcher.parseSeasonFromTitle("Overlord 4"))
        assertEquals(2, CsTitleMatcher.parseSeasonFromTitle("Vinland Saga II"))
        assertEquals(3, CsTitleMatcher.parseSeasonFromTitle("Overlord III"))
        assertEquals(4, CsTitleMatcher.parseSeasonFromTitle("Overlord IV"))
        assertEquals(4, CsTitleMatcher.parseSeasonFromTitle("Overlord IV (2022)"))
        assertEquals(5, CsTitleMatcher.parseSeasonFromTitle("Overlord V"))
        assertEquals(3, CsTitleMatcher.parseSeasonFromTitle("Mushoku Tensei III: Isekai Ittara Honki Dasu"))
        assertEquals("Mushoku Tensei: Isekai Ittara Honki Dasu", CsTitleMatcher.extractCleanBaseTitle("Mushoku Tensei III: Isekai Ittara Honki Dasu"))
        assertNull(CsTitleMatcher.parseSeasonFromTitle("Naruto"))
        assertNull(CsTitleMatcher.parseSeasonFromTitle("Death Note"))
    }

    @Test
    fun testParseSeasonFromTitleTurkishFormats() {
        assertEquals(2, CsTitleMatcher.parseSeasonFromTitle("Boku no Hero Academia 2. Sezon"))
        assertEquals(3, CsTitleMatcher.parseSeasonFromTitle("Kimetsu no Yaiba 3. sezon"))
        assertEquals(4, CsTitleMatcher.parseSeasonFromTitle("One Punch Man 4.Sezon"))
    }

    // ─── GetBestTitleSimilarity Tests ─────────────────────────────────────────

    @Test
    fun testGetBestTitleSimilarityExactMatch() {
        val sim = CsTitleMatcher.getBestTitleSimilarity(
            "Jujutsu Kaisen",
            "Jujutsu Kaisen",
            emptyList()
        )
        assertEquals(1.0, sim, 0.001)
    }

    @Test
    fun testGetBestTitleSimilarityAltMatch() {
        // Candidate matches an alt title
        val sim = CsTitleMatcher.getBestTitleSimilarity(
            "Sorcery Fight",
            "Jujutsu Kaisen",
            listOf("Sorcery Fight", "呪術廻戦")
        )
        assertTrue("Alt title should give very high similarity", sim > 0.90)
    }

    @Test
    fun testGetBestTitleSimilarityTurkishAscii() {
        // Turkish characters should be normalized for comparison
        val sim = CsTitleMatcher.getBestTitleSimilarity(
            "Sahane Aile",   // ASCII version of "Şahane Aile"
            "Şahane Aile",
            emptyList()
        )
        assertTrue("Turkish char normalization should give high similarity", sim > 0.85)
    }

    @Test
    fun testGetBestTitleSimilarityUnrelated() {
        val sim = CsTitleMatcher.getBestTitleSimilarity(
            "Boruto",
            "Attack on Titan",
            emptyList()
        )
        assertTrue("Unrelated titles should have low similarity", sim < 0.30)
    }

    // ─── SimplifyTitle & ToAsciiTitle ────────────────────────────────────────

    @Test
    fun testSimplifyTitle() {
        val simplified = CsTitleMatcher.simplifyTitle("Kimetsu no Yaiba: Mugen Ressha-hen!")
        assertFalse("Simplified title should not contain !", simplified.contains("!"))
        assertFalse("Simplified title should not contain :", simplified.contains(":"))
    }

    @Test
    fun testToAsciiTitle() {
        val ascii = CsTitleMatcher.toAsciiTitle("Şahane Aile — Çok İyi")
        assertEquals("Sahane Aile Cok Iyi", ascii)
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private fun createSearchResponse(
        name: String,
        url: String = "http://example.com",
        year: Int?,
        type: TvType = TvType.Anime
    ): SearchResponse {
        val cls = Class.forName("com.lagradost.cloudstream3.AnimeSearchResponse")
        val ctor = cls.constructors
            .filter { !it.isSynthetic && it.parameterTypes.none { p -> p.name.contains("DefaultConstructorMarker") } }
            .firstOrNull { it.parameterCount >= 3 }
            ?: cls.constructors.firstOrNull { it.parameterCount >= 3 }
            ?: throw IllegalStateException("Could not find AnimeSearchResponse constructor")
        val args = Array<Any?>(ctor.parameterCount) { null }
        val paramTypes = ctor.parameterTypes
        for (i in 0 until ctor.parameterCount) {
            val pType = paramTypes[i]
            when {
                i == 0 -> args[i] = name
                i == 1 -> args[i] = url
                i == 2 -> args[i] = "DummyProvider"
                i == 3 && pType.isAssignableFrom(type.javaClass) -> args[i] = type
                pType == java.lang.Integer.TYPE -> args[i] = 0
                pType == java.lang.Boolean.TYPE -> args[i] = false
                pType == java.lang.Long.TYPE -> args[i] = 0L
                pType == java.lang.Float.TYPE -> args[i] = 0f
                pType == java.lang.Double.TYPE -> args[i] = 0.0
                pType == java.util.Set::class.java -> args[i] = emptySet<Any>()
                pType == java.util.Map::class.java -> args[i] = emptyMap<Any, Any>()
                else -> args[i] = null
            }
        }

        val instance = ctor.newInstance(*args) as SearchResponse

        if (year != null) {
            try {
                val field = cls.getDeclaredField("year")
                field.isAccessible = true
                field.set(instance, year)
            } catch (_: Exception) {}
        }
        return instance
    }

    private fun createSearchResponse(name: String, year: Int?): SearchResponse {
        return createSearchResponse(name, "http://example.com", year)
    }
}
