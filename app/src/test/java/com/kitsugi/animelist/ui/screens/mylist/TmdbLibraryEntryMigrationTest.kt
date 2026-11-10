package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.data.local.MediaEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbLibraryEntryMigrationTest {
    private fun entity(
        id: Int,
        source: String,
        type: String = "Movie",
        malId: Int? = null,
        tmdbId: Int? = null,
        simklId: Int? = null,
        title: String = "Demon Slayer: Infinity Castle",
        updatedAt: Long = 0L
    ) = MediaEntryEntity(
        id = id,
        title = title,
        subtitle = "Film",
        type = type,
        status = "Planned",
        score = null,
        progress = 0,
        total = null,
        isFavorite = false,
        isAdult = false,
        source = source,
        malId = malId,
        imageUrl = null,
        year = 2025,
        synopsis = null,
        startDate = null,
        endDate = null,
        notes = null,
        tags = null,
        priority = null,
        isRepeating = false,
        repeatCount = 0,
        repeatValue = 0,
        volumeProgress = 0,
        isPrivate = false,
        isHiddenFromStatusLists = false,
        updatedAt = updatedAt,
        tmdbId = tmdbId,
        simklId = simklId
    )

    @Test
    fun realAccountRowsAreNotTreatedAsLegacyCatalogRows() {
        val account = entity(id = 7, source = "tmdb_account", malId = null, tmdbId = 550)
        val plan = TmdbLibraryEntryMigration.plan(listOf(account))

        assertTrue(plan.isEmpty)
        assertEquals("tmdb_account", account.source)
    }

    @Test
    fun legacyTmdbRowBecomesSimklWithoutReinterpretingItsIdAsMal() {
        val plan = TmdbLibraryEntryMigration.plan(
            listOf(entity(id = 1, source = "tmdb", malId = 12345))
        )

        assertTrue(plan.deleteIds.isEmpty())
        val repaired = plan.updates.single()
        assertEquals("simkl", repaired.source)
        assertEquals(12345, repaired.tmdbId)
        assertNull(repaired.malId)
    }

    @Test
    fun matchingSimklRowIsKeptAndNewerUserStateSurvives() {
        val simkl = entity(id = 2, source = "simkl", tmdbId = 12345, simklId = 987)
            .copy(title = "Provider title", updatedAt = 10, progress = 1, isFavorite = false)
        val legacy = entity(id = 1, source = "tmdb", malId = 12345, updatedAt = 20)
            .copy(title = "User title", status = "Completed", progress = 1, score = 9, isFavorite = true)

        val plan = TmdbLibraryEntryMigration.plan(listOf(simkl, legacy))

        assertEquals(listOf(1), plan.deleteIds)
        val merged = plan.updates.single()
        assertEquals(2, merged.id)
        assertEquals("simkl", merged.source)
        assertEquals("Completed", merged.status)
        assertEquals(9, merged.score)
        assertTrue(merged.isFavorite)
        assertEquals(987, merged.simklId)
        assertEquals(12345, merged.tmdbId)
    }

    @Test
    fun ambiguousOrUnverifiedMatchesAreNotDeleted() {
        val first = entity(id = 2, source = "simkl", tmdbId = 12345)
        val second = entity(id = 3, source = "simkl", tmdbId = 12345)
        val legacy = entity(id = 1, source = "tmdb", malId = 12345)
        val sameTitleOnly = entity(id = 4, source = "simkl", tmdbId = 99999)

        val plan = TmdbLibraryEntryMigration.plan(listOf(first, second, legacy, sameTitleOnly))

        assertTrue(plan.deleteIds.isEmpty())
        assertEquals(1, plan.updates.count { it.id == 1 && it.source == "simkl" })
        assertFalse(plan.updates.any { it.id == 4 })
    }

    @Test
    fun movieAndTvShowWithSameTmdbNumberAreNotMerged() {
        val legacy = entity(id = 1, source = "tmdb", type = "Movie", malId = 12345)
        val tv = entity(id = 2, source = "simkl", type = "TvShow", tmdbId = 12345)

        val plan = TmdbLibraryEntryMigration.plan(listOf(legacy, tv))

        assertTrue(plan.deleteIds.isEmpty())
        assertEquals("simkl", plan.updates.single { it.id == 1 }.source)
    }

    @Test
    fun crossTypeSameNumberWithDifferentTitlesIsNotDeleted() {
        val legacy = entity(id = 1, source = "tmdb", type = "Anime", malId = 12345, title = "Anime title")
        val movie = entity(id = 2, source = "simkl", type = "Movie", tmdbId = 12345, title = "Unrelated movie")

        val plan = TmdbLibraryEntryMigration.plan(listOf(legacy, movie))

        assertTrue(plan.deleteIds.isEmpty())
        assertEquals("simkl", plan.updates.single { it.id == 1 }.source)
    }

    @Test
    fun crossTypeSameNumberWithMatchingTitleAndYearCanMerge() {
        val legacy = entity(id = 1, source = "tmdb", type = "Anime", malId = 12345)
        val movie = entity(id = 2, source = "simkl", type = "Movie", tmdbId = 12345)

        val plan = TmdbLibraryEntryMigration.plan(listOf(legacy, movie))

        assertEquals(listOf(1), plan.deleteIds)
        assertTrue(plan.updates.none { it.id == 1 })
    }
}
