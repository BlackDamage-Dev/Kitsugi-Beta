package com.kitsugi.animelist.core.notifications

import com.kitsugi.animelist.data.remote.TmdbAccountSnapshot
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbAccountNotificationPolicyTest {
    private fun entry(type: MediaType = MediaType.Movie, id: Int = 550) = MediaEntry(
        id = 0,
        title = "$type $id",
        subtitle = "",
        type = type,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        source = "tmdb_account",
        tmdbId = id,
        tmdbWatchlist = true
    )

    private fun snapshot(entries: List<MediaEntry>) = TmdbAccountSnapshot(
        entries = entries,
        watchlist = entries,
        favorites = emptyList(),
        rated = emptyList(),
        fetchedAtMs = 1L
    )

    @Test
    fun baselineIsAccountScopedAndFirstSnapshotIsSilent() {
        assertTrue(tmdbSnapshotPreferenceKey(11) != tmdbSnapshotPreferenceKey(22))
        val snapshot = snapshot(listOf(entry()))
        assertTrue(newTmdbAccountEntries(null, snapshot).isEmpty())
    }

    @Test
    fun onlyNewTypeSensitiveKeysNotifyAndRemovalRearmsAddition() {
        val movie = entry()
        val first = snapshot(listOf(movie))
        val baseline = tmdbSnapshotFingerprints(first)
        assertTrue(newTmdbAccountEntries(baseline, first).isEmpty())

        val tvSameNumber = entry(type = MediaType.TvShow)
        val withTv = snapshot(listOf(movie, tvSameNumber))
        assertEquals(listOf(tvSameNumber), newTmdbAccountEntries(baseline, withTv))

        val afterRemoval = tmdbSnapshotFingerprints(snapshot(emptyList()))
        assertEquals(listOf(movie), newTmdbAccountEntries(afterRemoval, first))
    }
}
