package com.kitsugi.animelist.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `smartImport` fark tespiti — "hiçbir şey değişmediyse veritabanına yazma" kuralının
 * çekirdeği. `updatedAt` yalnızca yerel defter zamanıdır; tek başına değişmesi satırı
 * "değişmiş" saymamalıdır, yoksa her yenileme tüm tabloyu yeniden yazar.
 */
class MediaEntryPayloadDiffTest {

    private fun entity(
        progress: Int = 5,
        score: Int? = 8,
        status: String = "Watching",
        updatedAt: Long = 1_000L,
        title: String = "Sousou no Frieren"
    ) = MediaEntryEntity(
        id = 42,
        title = title,
        subtitle = "Anime • 2023",
        type = "Anime",
        status = status,
        score = score,
        progress = progress,
        total = 28,
        isFavorite = false,
        isAdult = false,
        source = "anilist",
        malId = 52991,
        imageUrl = "https://example.invalid/cover.jpg",
        year = 2023,
        synopsis = null,
        startDate = "2023-09-29",
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
        titleEnglish = "Frieren: Beyond Journey's End",
        titleJapanese = "葬送のフリーレン",
        aniListEntryId = 7,
        malListId = null,
        tmdbId = null,
        simklId = null
    )

    @Test
    fun `rows differing only in updatedAt count as unchanged`() {
        assertTrue(entity(updatedAt = 1_000L).samePayloadAs(entity(updatedAt = 9_999L)))
    }

    @Test
    fun `progress change is detected`() {
        assertFalse(entity(progress = 5).samePayloadAs(entity(progress = 6)))
    }

    @Test
    fun `score change is detected`() {
        assertFalse(entity(score = 8).samePayloadAs(entity(score = 9)))
    }

    @Test
    fun `status change is detected`() {
        assertFalse(entity(status = "Watching").samePayloadAs(entity(status = "Completed")))
    }

    @Test
    fun `title change is detected`() {
        assertFalse(
            entity(title = "Sousou no Frieren").samePayloadAs(entity(title = "Frieren"))
        )
    }
}
