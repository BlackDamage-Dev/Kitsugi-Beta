package com.kitsugi.animelist.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIdentityDisambiguationTest {

    private fun testEntry(
        id: Int = 1,
        title: String,
        titleEnglish: String? = null,
        titleJapanese: String? = null,
        type: MediaType = MediaType.Anime,
        status: WatchStatus = WatchStatus.Completed,
        source: String = "kitsu",
        malId: Int? = null,
        year: Int? = null
    ): MediaEntry = MediaEntry(
        id = id,
        title = title,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese,
        type = type,
        status = status,
        score = 8,
        progress = 1,
        total = 1,
        source = source,
        malId = malId,
        year = year
    )

    @Test
    fun franchiseMoviesWithDifferentEnglishTitlesDoNotMatchEvenIfJapaneseTitleIsIdentical() {
        // Date A Bullet: Dead or Bullet vs Date A Bullet: Nightmare or Queen
        // Both share the franchise Japanese title "デート・ア・バレット"
        val movie1 = testEntry(
            id = 1,
            title = "Date A Bullet: Dead or Bullet",
            titleEnglish = "Date A Bullet: Dead or Bullet",
            titleJapanese = "デート・ア・バレット",
            source = "kitsu",
            malId = 300_016_086
        )

        val movie2 = testEntry(
            id = 2,
            title = "Date A Bullet: Nightmare or Queen",
            titleEnglish = "Date A Bullet: Nightmare or Queen",
            titleJapanese = "デート・ア・バレット",
            source = "kitsu",
            malId = 300_016_140
        )

        // They must NOT be identified as the same media!
        val same = MediaIdentity.sameMedia(movie1, movie2, allowTitle = true)
        assertFalse("Franchise movies with distinct titles must not match merely by shared franchise Japanese title", same)
    }

    @Test
    fun identicalPrimaryTitlesMatchCorrectly() {
        val entry1 = testEntry(
            id = 1,
            title = "Date A Bullet: Nightmare or Queen",
            titleJapanese = "デート・ア・バレット",
            source = "kitsu",
            malId = 300_016_140
        )

        val entry2 = testEntry(
            id = 2,
            title = "Date A Bullet: Nightmare or Queen",
            titleJapanese = "デート・ア・バレット",
            source = "mal",
            malId = 42000
        )

        val same = MediaIdentity.sameMedia(entry1, entry2, allowTitle = true)
        assertTrue("Entries with identical primary title must match", same)
    }

    @Test
    fun primaryTitleMatchingEnglishTitleMatchesCorrectly() {
        val entryRomaji = testEntry(
            id = 1,
            title = "Shingeki no Kyojin",
            titleEnglish = "Attack on Titan",
            source = "mal",
            malId = 16498
        )

        val entryEnglish = testEntry(
            id = 2,
            title = "Attack on Titan",
            titleEnglish = "Attack on Titan",
            source = "kitsu",
            malId = 300_007_442
        )

        val same = MediaIdentity.sameMedia(entryRomaji, entryEnglish, allowTitle = true)
        assertTrue("Entry with English title matching other entry's primary title must match", same)
    }

    @Test
    fun conflictingProviderIdsAreExplicitlyReportedEvenWhenTitlesMatch() {
        val first = testEntry(
            id = 1,
            title = "Same Title",
            source = "mal",
            malId = 1234,
            year = 2024
        )
        val second = testEntry(
            id = 2,
            title = "Same Title",
            source = "mal",
            malId = 5678,
            year = 2024
        )

        assertFalse(MediaIdentity.sameMedia(first, second))
        assertEquals(setOf("Anime:mal"), MediaIdentity.conflictingIdentityKeys(first, second))
    }

    @Test
    fun shikimoriIdsAreNamespacedCorrectly() {
        val shikiEntry = testEntry(
            id = 1,
            title = "Some Anime",
            source = "shikimori",
            malId = 400_050_000 // Shikimori offset
        )

        val keys = MediaIdentity.keys(shikiEntry)
        assertTrue("Keys should contain Shikimori namespaced key", keys.contains("Anime:shikimori:50000"))
    }

    @Test
    fun shikimoriAndMalRowsMatchByTargetIdEvenWhenDisplayTitlesDiffer() {
        val shikimori = testEntry(
            id = 1,
            title = "Zom 100: Zombie ni Naru made ni Shitai 100 no Koto",
            titleEnglish = null,
            source = "shikimori",
            malId = 54_112,
            year = 2023
        )
        val mal = testEntry(
            id = 2,
            title = "Zom 100: Bucket List of the Dead",
            titleEnglish = "Zom 100: Bucket List of the Dead",
            source = "mal",
            malId = 54_112,
            year = 2023
        )

        assertTrue(MediaIdentity.keys(shikimori).contains("Anime:mal:54112"))
        assertTrue(MediaIdentity.sameMedia(shikimori, mal, allowTitle = false))
        assertEquals(54_112, MediaIdentity.realMalId(shikimori))
        assertEquals(
            54_112,
            MediaIdentity.realMalId(shikimori.copy(malId = 400_054_112))
        )
    }

    @Test
    fun simklStableIdWindowIsRecognisedForAniListRowsOnly() {
        // AniList owns 100M..299M, but a *Simkl* row is the only one that may carry 150M..249M.
        val anilistRow = testEntry(id = 1, title = "Show", source = "anilist", malId = 100_000_870)
        assertEquals(setOf("Anime:anilist:870"), MediaIdentity.keys(anilistRow))

        val bangumiRow = testEntry(id = 2, title = "Show", source = "bangumi", malId = 500_000_870)
        assertEquals(setOf("Anime:bangumi:870"), MediaIdentity.keys(bangumiRow))
    }

    @Test
    fun legacyTmdbIdStaysOnTmdbAxisAndNeverBecomesMal() {
        val legacy = testEntry(
            id = 1,
            title = "Demon Slayer: Infinity Castle",
            source = "tmdb",
            type = MediaType.Movie,
            malId = 12345
        )

        assertEquals(setOf("Movie:tmdb:12345"), MediaIdentity.keys(legacy))
        assertEquals(null, MediaIdentity.realMalId(legacy))
    }

    @Test
    fun aSingleAgreeingIdInANameSpaceProvesIdentityEvenWithLegacyExtras() {
        val enriched = testEntry(id = 1, title = "Köprü", source = "bangumi", malId = 500_057_162)
        val other = testEntry(id = 2, title = "Frieren", source = "anilist", malId = 52991)

        // The row's own namespace plus a crosswalk hit can hold two ids at once. Old behavior
        // kept the last value only, so a matching id could still be reported as a conflict.
        val enrichedKeys = MediaIdentity.keys(enriched, setOf("Anime:mal:52991", "Anime:anilist:51004"))
        val otherKeys = MediaIdentity.keys(other)

        assertTrue(
            MediaIdentity.conflictingIdentityKeys(enriched.type, other.type, enrichedKeys, otherKeys).isEmpty()
        )
        assertTrue(MediaIdentity.sameMedia(enriched, other, enrichedKeys, otherKeys))
    }

    @Test
    fun seasonSynonymVariantsMatchSecondSeasonAndSeasonTwoWithoutMergingSeasonOne() {
        val ordinal = testEntry(id = 1, title = "Kaiju No. 8 2nd Season", source = "kitsu", malId = 300_049_001, year = 2025)
        val cardinal = testEntry(id = 2, title = "Kaiju No. 8 Season 2", source = "anilist", malId = 59177, year = 2025)
        val seasonOne = testEntry(id = 3, title = "Kaiju No. 8", source = "anilist", malId = 52588, year = 2024)

        assertTrue(MediaIdentity.librarySameMedia(ordinal, cardinal))
        assertFalse(MediaIdentity.librarySameMedia(ordinal, seasonOne))
    }

    @Test
    fun animeTmdbDifferenceDoesNotVetoAuthoritativeMalMatch() {
        val anilist = testEntry(id = 1, title = "Attack on Titan", source = "anilist", malId = 16498, year = 2013)
            .copy(tmdbId = 1429)
        val simkl = testEntry(id = 2, title = "Attack on Titan", source = "simkl", malId = 16498, year = 2013)
            .copy(tmdbId = 88888)

        assertTrue(MediaIdentity.authoritativeConflictingIdentityKeys(anilist, simkl).isEmpty())
        assertTrue(MediaIdentity.librarySameMedia(anilist, simkl))
    }

    @Test
    fun sameAnimeMovieAcrossTypeMatchesSingleEpisodeAnimeAndMovieOnly() {
        val movie = testEntry(
            id = 1,
            title = "Demon Slayer: Kimetsu no Yaiba Infinity Castle",
            source = "simkl",
            type = MediaType.Movie,
            year = 2025
        ).copy(total = 1, tmdbId = 1311031)
        val singleEpAnime = testEntry(
            id = 2,
            title = "Demon Slayer: Kimetsu no Yaiba Infinity Castle",
            source = "simkl",
            malId = 59192,
            type = MediaType.Anime,
            year = 2025
        ).copy(total = 1, tmdbId = 1311031)
        val tvSeriesAnime = testEntry(
            id = 3,
            title = "Demon Slayer: Kimetsu no Yaiba",
            source = "simkl",
            malId = 38000,
            type = MediaType.Anime,
            year = 2019
        ).copy(total = 26)

        assertTrue(MediaIdentity.sameAnimeMovieAcrossType(movie, singleEpAnime))
        assertFalse(MediaIdentity.sameAnimeMovieAcrossType(movie, tvSeriesAnime))
    }
}
