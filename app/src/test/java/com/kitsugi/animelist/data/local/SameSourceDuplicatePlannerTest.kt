package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SameSourceDuplicatePlannerTest {
    private fun entry(
        id: Int,
        title: String = "Title $id",
        source: String = "anilist",
        type: MediaType = MediaType.Anime,
        malId: Int? = null,
        tmdbId: Int? = null,
        simklId: Int? = null,
        year: Int? = null,
        total: Int? = null
    ) = MediaEntry(
        id = id, title = title, subtitle = "", type = type,
        status = com.kitsugi.animelist.model.WatchStatus.Planned,
        score = null, progress = 0, total = total, source = source,
        malId = malId, tmdbId = tmdbId, simklId = simklId, year = year
    )

    /** The pre-2.4.388 algorithm, kept only as a differential-test oracle. */
    private suspend fun legacy(entries: List<MediaEntry>): List<List<Int>> =
        SameSourceDuplicatePlanner.legacyPlanForTest(entries).clusters.map { cluster -> cluster.map { it.id } }

    @Test
    fun randomizedLibrariesMatchLegacyClusteringExactly() = runBlocking {
        val random = Random(387)
        repeat(250) { iteration ->
            val rows = (1..45).map { id ->
                val family = random.nextInt(12)
                entry(
                    id = id,
                    title = if (random.nextBoolean()) "Series $family" else "Different ${random.nextInt(20)}",
                    source = listOf("anilist", "AniList", "mal", "simkl", "bangumi")[random.nextInt(5)],
                    type = if (random.nextInt(5) == 0) MediaType.Movie else MediaType.Anime,
                    malId = if (random.nextBoolean()) 100_000_000 + family else null,
                    tmdbId = if (random.nextBoolean()) family + 1 else null,
                    simklId = if (random.nextBoolean()) family + 100 else null,
                    year = if (random.nextBoolean()) 2000 + family else null,
                    total = if (random.nextBoolean()) 12 + family else null
                )
            }
            assertEquals("iteration=$iteration", legacy(rows), SameSourceDuplicatePlanner.plan(rows).clusters.map { c -> c.map { it.id } })
        }
    }

    @Test
    fun sharedBridgeDoesNotMergeConflictingProductions() = runBlocking {
        val rows = listOf(
            entry(1, "Bridge", malId = 100_000_001),
            entry(2, "Bridge", malId = 100_000_001, tmdbId = 10),
            entry(3, "Bridge", malId = 100_000_002, tmdbId = 20)
        )
        assertEquals(listOf(listOf(1, 2)), SameSourceDuplicatePlanner.plan(rows).clusters.map { c -> c.map { it.id } })
    }

    @Test
    fun laterMemberTokensRemainIndexed() = runBlocking {
        val rows = listOf(
            entry(1, "First", malId = 100_000_001),
            entry(2, "Second", malId = 100_000_001, simklId = 77),
            entry(3, "Third", simklId = 77)
        )
        assertEquals(listOf(listOf(1, 2, 3)), SameSourceDuplicatePlanner.plan(rows).clusters.map { c -> c.map { it.id } })
    }

    @Test
    fun crossTypeMalBridgeIsIndexedEvenWhenMovieKeysOmitMal() = runBlocking {
        val rows = listOf(
            entry(1, "Anime", source = "simkl", malId = 500_000_009),
            entry(2, "Movie release", source = "simkl", type = MediaType.Movie, malId = 9)
        )
        // realMalId deliberately rejects the movie's MAL-like value; title differs, so no merge.
        assertTrue(SameSourceDuplicatePlanner.plan(rows).clusters.isEmpty())
    }

    @Test
    fun crossTypeSimklBridgeMergesAnimeMovieWhenEpisodeGuardAllows() = runBlocking {
        val rows = listOf(
            entry(1, "Anime", source = "simkl", malId = 9, total = 1),
            entry(2, "Movie release", source = "simkl", type = MediaType.Movie, malId = 9)
        )
        assertEquals(listOf(listOf(1, 2)), SameSourceDuplicatePlanner.plan(rows).clusters.map { c -> c.map { it.id } })
    }

    @Test
    fun distinctRowsDoNotCreateCandidateComparisons() = runBlocking {
        val rows = (1..5_000).map { entry(it, malId = 100_000_000 + it) }
        val plan = SameSourceDuplicatePlanner.plan(rows)
        assertTrue(plan.clusters.isEmpty())
        assertEquals(0, plan.candidateComparisons)
    }

    @Test
    fun largeDuplicateClusterIsLinearInCandidateChecks() = runBlocking {
        val rows = (1..3_000).map { entry(it, "Same show", malId = 100_000_042) }
        val plan = SameSourceDuplicatePlanner.plan(rows)
        assertEquals(1, plan.clusters.size)
        assertEquals(3_000, plan.clusters.single().size)
        assertTrue("comparisons=${plan.candidateComparisons}", plan.candidateComparisons <= rows.size)
    }

    @Test
    fun cancelledPlanningDoesNotReturnAPartialCommitPlan() = runBlocking {
        val rows = (1..20_000).map { entry(it, malId = 100_000_000 + it) }
        var completed = false
        val job = launch {
            SameSourceDuplicatePlanner.plan(rows)
            completed = true
        }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(!completed)
    }
}
