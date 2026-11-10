package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameSourceMergePolicyTest {
    private fun entry(id: Int) = MediaEntry(
        id = id, title = "Resolved Title", subtitle = "", type = MediaType.Anime,
        status = WatchStatus.Watching, score = null, progress = 0, total = null,
        source = "tmdb", tmdbId = 42
    )

    @Test
    fun nativeTmdbUserFieldsSurviveWhenSurvivorLacksThem() {
        val survivor = entry(1)
        val incoming = entry(2).copy(tmdbWatchlist = true, tmdbRating = 8.5)
        val merged = SameSourceMergePolicy.merge(survivor, incoming, preferIncomingUserFields = false)
        assertEquals(true, merged.tmdbWatchlist)
        assertEquals(8.5, merged.tmdbRating!!, 0.0)
        assertEquals(1, merged.id)
    }

    @Test
    fun explicitSurvivorTmdbValuesAreNotOverwritten() {
        val merged = SameSourceMergePolicy.merge(
            entry(1).copy(tmdbWatchlist = false, tmdbRating = 7.0),
            entry(2).copy(tmdbWatchlist = true, tmdbRating = 9.0),
            preferIncomingUserFields = true
        )
        assertEquals(false, merged.tmdbWatchlist)
        assertEquals(7.0, merged.tmdbRating!!, 0.0)
    }

    @Test
    fun progressFlagsAndNewerUserFieldsArePreserved() {
        val survivor = entry(1).copy(progress = 8, repeatCount = 2, volumeProgress = 3, isFavorite = true, updatedAt = 100)
        val incoming = entry(2).copy(
            progress = 5, repeatCount = 4, volumeProgress = 1, isRepeating = true,
            isPrivate = true, score = 9, notes = "new note", updatedAt = 200,
            title = "進撃の巨人", titleJapanese = "進撃の巨人"
        )
        val merged = SameSourceMergePolicy.merge(survivor, incoming, preferIncomingUserFields = true)
        assertEquals(8, merged.progress)
        assertEquals(4, merged.repeatCount)
        assertEquals(3, merged.volumeProgress)
        assertTrue(merged.isFavorite)
        assertTrue(merged.isRepeating)
        assertTrue(merged.isPrivate)
        assertEquals(9, merged.score)
        assertEquals("new note", merged.notes)
        assertEquals("Resolved Title", merged.title)
        assertEquals("進撃の巨人", merged.titleJapanese)
        assertEquals(200L, merged.updatedAt)
    }

    @Test
    fun blankIncomingMetadataDoesNotEraseResolvedMetadata() {
        val survivor = entry(1).copy(imageUrl = "https://img", synopsis = "synopsis", year = 2020, total = 24)
        val merged = SameSourceMergePolicy.merge(survivor, entry(2).copy(imageUrl = " ", synopsis = ""), false)
        assertEquals("https://img", merged.imageUrl)
        assertEquals("synopsis", merged.synopsis)
        assertEquals(2020, merged.year)
        assertEquals(24, merged.total)
        assertFalse(merged.tmdbWatchlist ?: false)
    }
}
