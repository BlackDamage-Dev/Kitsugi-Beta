package com.kitsugi.animelist.data.local

import com.google.gson.Gson
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHistoryRulesTest {
    private fun entry(
        id: String = "anilist:42",
        title: String = "Example",
        season: Int = 1,
        episode: Int = 1,
        position: Long = 45_000,
        duration: Long = 100_000,
        aniListId: Int? = 42,
        malId: Int? = 7
    ) = WatchHistoryEntry(
        animeId = id, animeTitle = title, posterUrl = "poster", season = season,
        episode = episode, positionMs = position, durationMs = duration,
        aniListId = aniListId, malId = malId, watchedAtMs = 1
    )

    @Test fun legacyJsonCanBeReadAndUpgradedWithoutLosingPosition() {
        val json = """{"animeId":"42","animeTitle":"Example","posterUrl":"poster","season":1,"episode":1,"positionMs":45000,"durationMs":100000,"aniListId":42,"malId":7,"source":"Old provider"}"""
        val old = Gson().fromJson(json, WatchHistoryEntry::class.java)
        assertNull(old.addonId)
        assertNull(old.synonyms)
        val refreshed = entry(position = 0, duration = 0).copy(addonId = "cs:provider", source = "Provider")
        val result = WatchHistoryRules.record(listOf(old), refreshed).single()
        assertEquals("42", result.animeId)
        assertEquals(45_000L, result.positionMs)
        assertEquals(100_000L, result.durationMs)
        assertEquals("cs:provider", result.addonId)
    }

    @Test fun externalIdNamespacesAndMovieTypesDoNotCollide() {
        val ani = WatchHistoryRules.mediaId(7, null, null, null, "A", false)
        val mal = WatchHistoryRules.mediaId(null, 7, null, null, "B", false)
        val film = WatchHistoryRules.mediaId(null, null, 7, null, "C", true)
        val series = WatchHistoryRules.mediaId(null, null, 7, null, "C", false)
        assertEquals(4, setOf(ani, mal, film, series).size)
        assertFalse(WatchHistoryRules.sameMedia(entry(id = "7", aniListId = 7, malId = null),
            entry(id = "7", aniListId = null, malId = 7)))
    }

    @Test fun legacyNumericCollisionsAreSeparatedWithoutDroppingEitherItem() {
        val ani = entry(id = "7", title = "Ani", aniListId = 7, malId = null)
        val mal = entry(id = "7", title = "Mal", aniListId = null, malId = 7)
        val normalized = WatchHistoryRules.normalizeIds(listOf(ani, mal))
        assertEquals(2, normalized.size)
        assertEquals("7", normalized[0].animeId)
        assertEquals("mal:7", normalized[1].animeId)
        val third = entry(id = "mal:8", title = "Third", aniListId = null, malId = 8)
        assertEquals(3, WatchHistoryRules.record(listOf(ani, mal), third).size)
        assertEquals(2, WatchHistoryRules.record(listOf(ani), mal).size)
    }

    @Test fun changingSeasonsKeepsBothProgressValuesAndRemovalIsScoped() {
        val one = entry(season = 1, position = 30_000)
        val two = entry(season = 2, position = 60_000)
        var result = WatchHistoryRules.record(listOf(one), two)
        result = WatchHistoryRules.updateProgress(result, one.animeId, 2, 1, 70_000, 100_000, 5)
        assertEquals(30_000L, result.first { it.season == 1 }.positionMs)
        assertEquals(70_000L, result.first { it.season == 2 }.positionMs)
        val remaining = WatchHistoryRules.remove(result, one.animeId, 1, 1)
        assertEquals(listOf(2), remaining.map { it.season })
    }

    @Test fun oldAndNewKeysForSameTitleNormalizeAcrossSeasons() {
        val old = entry(id = "42", season = 1)
        val newer = entry(id = "anilist:42", season = 2, position = 18_000)
        val refreshed = newer.copy(positionMs = 0, durationMs = 0)
        val result = WatchHistoryRules.record(listOf(old, newer), refreshed)
        assertEquals(2, result.size)
        assertEquals(1, result.map { it.animeId }.distinct().size)
        assertEquals(18_000L, result.first { it.season == 2 }.positionMs)
        assertEquals(45_000L, result.first { it.season == 1 }.positionMs)
    }

    @Test fun missingDurationDoesNotMarkAStreamAsFinished() {
        val updated = WatchHistoryRules.updateProgress(listOf(entry()), "anilist:42", 1, 1, 55_000, 0, 2).single()
        assertEquals(100_000L, updated.durationMs)
        assertEquals(55_000L, WatchHistoryRules.resumePosition(updated))
        val unknown = entry(duration = 0, position = 10_000)
        assertEquals(10_000L, WatchHistoryRules.resumePosition(unknown))
        val beyondOldDuration = WatchHistoryRules.updateProgress(listOf(entry()), "anilist:42", 1, 1, 120_000, 0, 3).single()
        assertEquals(0L, beyondOldDuration.durationMs)
        assertEquals(120_000L, WatchHistoryRules.resumePosition(beyondOldDuration))
    }

    @Test fun rewindIsSavedAndPositionsAreClamped() {
        val rewound = WatchHistoryRules.updateProgress(listOf(entry()), "anilist:42", 1, 1, 15_000, 100_000, 2).single()
        assertEquals(15_000L, rewound.positionMs)
        val overflow = WatchHistoryRules.updateProgress(listOf(rewound), "anilist:42", 1, 1, 120_000, 100_000, 3).single()
        assertEquals(100_000L, overflow.positionMs)
        assertEquals(0L, WatchHistoryRules.resumePosition(overflow))
        val negative = WatchHistoryRules.updateProgress(listOf(rewound), "anilist:42", 1, 1, -5, 100_000, 4).single()
        assertEquals(0L, negative.positionMs)
    }

    @Test fun completionThresholdOnlyRestartsTrulyFinishedVideos() {
        assertEquals(0L, WatchHistoryRules.resumePosition(entry(position = 99_000)))
        assertEquals(90_000L, WatchHistoryRules.resumePosition(entry(position = 90_000)))
        assertEquals(500L, WatchHistoryRules.resumePosition(entry(position = 500, duration = 1_000)))
    }

    @Test fun updatesDoNotResurrectDeletedEntries() {
        val history = WatchHistoryRules.remove(listOf(entry()), "anilist:42", 1, 1)
        assertTrue(WatchHistoryRules.updateProgress(history, "anilist:42", 1, 1, 9_000, 100_000, 1).isEmpty())
    }

    @Test fun metadataIsPreservedWhenARefreshOmitsOptionalFields() {
        val original = entry().copy(titleEnglish = "English title", synonyms = listOf("Alt"), startYear = 2020)
        val refreshed = entry(position = 0, duration = 0).copy(posterUrl = null, synonyms = null)
        val merged = WatchHistoryRules.record(listOf(original), refreshed).single()
        assertEquals("poster", merged.posterUrl)
        assertEquals("English title", merged.titleEnglish)
        assertEquals(listOf("Alt"), merged.synonyms)
        assertEquals(2020, merged.startYear)
    }

    @Test fun olderTitleOnlyRecordCanBeUpgradedButDifferentYearsStaySeparate() {
        val old = entry(id = "old-hash", aniListId = null, malId = null).copy(startYear = 2020)
        val match = entry(id = "anilist:42").copy(startYear = 2020)
        val remake = match.copy(startYear = 2024, aniListId = 99, malId = 88)
        assertTrue(WatchHistoryRules.sameMedia(old, match))
        assertFalse(WatchHistoryRules.sameMedia(old, remake))
        assertNotEquals(WatchHistoryRules.mediaId(null, null, null, null, "Example", false, startYear = 2020),
            WatchHistoryRules.mediaId(null, null, null, null, "Example", false, startYear = 2024))
    }

    @Test fun historyIsBoundedToTheMostRecentTwoHundredEntries() {
        var result = emptyList<WatchHistoryEntry>()
        for (episode in 1..205) result = WatchHistoryRules.record(result, entry(episode = episode))
        assertEquals(200, result.size)
        assertEquals(205, result.first().episode)
        assertEquals(6, result.last().episode)
    }
}
