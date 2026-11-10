package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM tests use the repository's real org.json test dependency. */
class MediaEntryBackupTmdbTest {
    private fun entry(id: Int = 0) = MediaEntry(
        id = id,
        title = "Dune",
        subtitle = "Film",
        type = MediaType.Movie,
        status = WatchStatus.Watching,
        score = 8,
        progress = 1,
        total = null,
        isFavorite = true,
        source = TMDB_ACCOUNT_SOURCE,
        tmdbId = 438631,
        tmdbWatchlist = false,
        tmdbRating = 7.5,
        notes = "keep"
    )

    @Test
    fun nativeFieldsSurviveExportImportRoundTrip() {
        val restored = MediaEntryBackup.importFromJson(
            MediaEntryBackup.exportToJson(listOf(entry()))
        ).single()

        assertEquals(TMDB_ACCOUNT_SOURCE, restored.source)
        assertEquals(438631, restored.tmdbId)
        assertEquals(false, restored.tmdbWatchlist)
        assertEquals(7.5, restored.tmdbRating!!, 0.0)
        assertEquals(8, restored.score)
        assertEquals("keep", restored.notes)
    }

    @Test
    fun legacyBackupDoesNotEraseNativeFieldsAndExplicitFalseStillMerges() {
        val root = JSONObject(MediaEntryBackup.exportToJson(listOf(entry())))
        val item = root.getJSONArray("entries").getJSONObject(0)
        item.remove("tmdbWatchlist")
        item.remove("tmdbRating")
        // Force an actual update; otherwise preserving every field correctly produces no DB write.
        val legacy = MediaEntryBackup.importFromJson(root.toString()).single().copy(progress = 2)
        assertNull(legacy.tmdbWatchlist)
        assertNull(legacy.tmdbRating)

        val current = entry(id = 42).copy(tmdbWatchlist = true, tmdbRating = 9.0)
        val mergedLegacy = MediaEntryBackup.mergeAndSyncEntries(listOf(current), listOf(legacy))
        assertEquals(true, mergedLegacy.toUpdate.single().tmdbWatchlist)
        assertEquals(9.0, mergedLegacy.toUpdate.single().tmdbRating!!, 0.0)

        val explicitFalse = legacy.copy(tmdbWatchlist = false, tmdbRating = 7.5)
        val mergedFalse = MediaEntryBackup.mergeAndSyncEntries(listOf(current), listOf(explicitFalse))
        assertFalse(mergedFalse.toUpdate.single().tmdbWatchlist!!)
        assertEquals(7.5, mergedFalse.toUpdate.single().tmdbRating!!, 0.0)
    }
}
