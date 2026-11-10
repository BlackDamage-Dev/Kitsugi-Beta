package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbAccountContractTest {
    private fun entry(
        id: Int = 0,
        type: MediaType = MediaType.Movie,
        source: String = TMDB_ACCOUNT_SOURCE,
        tmdbId: Int? = 550,
        status: WatchStatus = WatchStatus.Planned,
        score: Int? = null,
        favorite: Boolean = false
    ) = MediaEntry(
        id = id,
        title = "Demon Slayer: Kimetsu no Yaiba Infinity Castle",
        type = type,
        status = status,
        score = score,
        progress = 0,
        total = null,
        isFavorite = favorite,
        source = source,
        tmdbId = tmdbId,
        year = 2025
    )

    @Test
    fun ratingUsesTmdbScaleAndZeroMeansDelete() {
        assertNull(tmdbRatingValue(null))
        assertNull(tmdbRatingValue(0))
        assertEquals(8.0, tmdbRatingValue(8)!!, 0.0)
        assertEquals(10.0, tmdbRatingValue(99)!!, 0.0)
        assertEquals(7.5, tmdbRatingValue(7.25)!!, 0.0)
        assertEquals(7.0, tmdbRatingValue(7.24)!!, 0.0)
        assertNull(tmdbRatingValue(0.0))
    }

    @Test
    fun requestTokenExpiresAndRejectsMissingOrInvalidTimestamps() {
        val now = 1_800_000_000_000L
        assertTrue(isTmdbRequestTokenUsable(now - TMDB_REQUEST_TOKEN_TTL_MILLIS + 1, now))
        assertFalse(isTmdbRequestTokenUsable(now - TMDB_REQUEST_TOKEN_TTL_MILLIS, now))
        assertFalse(isTmdbRequestTokenUsable(0L, now))
        assertTrue(isTmdbRequestTokenUsable(now + 30_000L, now)) // small wall-clock skew
        assertFalse(isTmdbRequestTokenUsable(now + 61_000L, now))
    }

    @Test
    fun nativeWatchlistAndHalfRatingAreNotRebuiltFromLocalStatus() {
        val local = entry(id = 42, status = WatchStatus.Watching, score = 2).copy(
            tmdbWatchlist = false,
            tmdbRating = 2.0
        )
        val remote = entry(status = WatchStatus.Planned, score = 8).copy(
            tmdbWatchlist = true,
            tmdbRating = 7.5
        )

        val merged = mergeTmdbAccountEntry(remote, local)

        assertEquals(WatchStatus.Watching, merged.status)
        assertEquals(true, merged.tmdbWatchlist)
        assertEquals(7.5, merged.tmdbRating!!, 0.0)
        assertEquals(8, merged.score)
    }

    @Test
    fun onlyPlannedMapsToWatchlist() {
        assertTrue(tmdbWatchlistValue(WatchStatus.Planned))
        assertFalse(tmdbWatchlistValue(WatchStatus.Watching))
        assertFalse(tmdbWatchlistValue(WatchStatus.Completed))
        assertFalse(supportsTmdbAccountType(MediaType.Anime))
        assertTrue(supportsTmdbAccountType(MediaType.Movie))
        assertTrue(supportsTmdbAccountType(MediaType.TvShow))
    }

    @Test
    fun directAccountWritesRemainEnabledWhenAutomaticSyncIsOff() {
        assertTrue(shouldAttemptTmdbWrite(syncEnabled = false, source = TMDB_ACCOUNT_SOURCE))
        assertFalse(shouldAttemptTmdbWrite(syncEnabled = false, source = "simkl"))
        assertTrue(shouldAttemptTmdbWrite(syncEnabled = true, source = "simkl"))
    }

    @Test
    fun remoteRefreshPreservesUserFieldsAndDoesNotCopyMalIntoTmdbSlot() {
        val local = entry(id = 42, status = WatchStatus.Watching, score = 3, favorite = false)
            .copy(progress = 4, notes = "keep", malId = 12345, updatedAt = 99)
        val remote = entry(status = WatchStatus.Planned, score = 9, favorite = true)

        val merged = mergeTmdbAccountEntry(remote, local)

        assertEquals(42, merged.id)
        assertEquals(WatchStatus.Watching, merged.status)
        assertEquals(4, merged.progress)
        assertEquals("keep", merged.notes)
        assertEquals(9, merged.score)
        assertTrue(merged.isFavorite)
        assertNull("A sibling MAL id must not become a TMDB id", merged.malId)
        assertEquals(99L, merged.updatedAt)
    }

    @Test
    fun fieldIntentSkipsKitsugiOnlyEditsAndNeverDeletesOnInsert() {
        val previous = entry(status = WatchStatus.Watching, score = null, favorite = false)
        val localOnlyEdit = previous.copy(progress = 12, notes = "Kitsugi only", startDate = "2026-10-24")
        assertEquals(TmdbWriteFields.None, tmdbWriteFieldsForUpdate(previous, localOnlyEdit))
        assertEquals(TmdbWriteFields.None, tmdbWriteFieldsForInsert(previous))

        val planned = previous.copy(status = WatchStatus.Planned)
        assertEquals(TmdbWriteFields(watchlist = true), tmdbWriteFieldsForInsert(planned))
        assertEquals(TmdbWriteFields.All, TmdbWriteFields.fromToken(null))
        assertEquals(TmdbWriteFields.None, TmdbWriteFields.fromToken(""))
        assertEquals(
            TmdbWriteFields(watchlist = true, rating = true),
            TmdbWriteFields.fromToken(TmdbWriteFields(watchlist = true, rating = true).toToken())
        )
    }

    @Test
    fun fieldIntentWritesOnlyChangedNativeValuesAndHonorsNativeWatchlist() {
        val previous = entry(status = WatchStatus.Planned, score = 7, favorite = false).copy(
            tmdbWatchlist = true,
            tmdbRating = 7.5
        )
        assertEquals(
            TmdbWriteFields.None,
            tmdbWriteFieldsForUpdate(previous, previous.copy(status = WatchStatus.Watching, progress = 3))
        )
        assertEquals(
            TmdbWriteFields(favorite = true),
            tmdbWriteFieldsForUpdate(previous, previous.copy(isFavorite = true))
        )
        assertEquals(
            TmdbWriteFields(rating = true),
            tmdbWriteFieldsForUpdate(previous, previous.copy(tmdbRating = 8.0, score = 8))
        )
        assertEquals(
            TmdbWriteFields(watchlist = true),
            tmdbWriteFieldsForUpdate(
                previous.copy(tmdbWatchlist = null),
                previous.copy(tmdbWatchlist = null, status = WatchStatus.Watching)
            )
        )
    }

    @Test
    fun remotePreflightSkipsIdempotentWritesButKeepsExplicitRatingDelete() {
        assertEquals(
            TmdbWriteFields.None,
            TmdbWriteFields.All.onlyChangesFrom(
                remoteWatchlist = true, remoteFavorite = false, remoteRating = 7.5,
                desiredWatchlist = true, desiredFavorite = false, desiredRating = 7.25
            )
        )
        val previous = entry(score = 7).copy(tmdbWatchlist = true, tmdbRating = 7.5)
        val cleared = previous.copy(score = null, tmdbRating = null)
        val clearIntent = tmdbWriteFieldsForUpdate(previous, cleared)
        assertEquals(TmdbWriteFields(rating = true), clearIntent)
        assertEquals(
            clearIntent,
            clearIntent.onlyChangesFrom(
                remoteWatchlist = true, remoteFavorite = false, remoteRating = 7.5,
                desiredWatchlist = true, desiredFavorite = false, desiredRating = null
            )
        )
        assertEquals(
            TmdbWriteFields.None,
            clearIntent.onlyChangesFrom(
                remoteWatchlist = true, remoteFavorite = false, remoteRating = null,
                desiredWatchlist = true, desiredFavorite = false, desiredRating = null
            )
        )
    }

    @Test
    fun crossProviderWritesPreserveNativeStateUnlessTheScoreReallyChanges() {
        val existing = entry(status = WatchStatus.Completed, score = 8).copy(
            tmdbWatchlist = true,
            tmdbRating = 7.5
        )
        val sibling = entry(source = "simkl", status = WatchStatus.Watching, score = 8)

        val unchanged = resolveTmdbNativeWriteState(sibling, existing)
        assertTrue(unchanged.watchlist)
        assertEquals(7.5, unchanged.rating!!, 0.0)

        val changed = resolveTmdbNativeWriteState(sibling.copy(score = 9), existing)
        assertTrue(changed.watchlist)
        assertEquals(9.0, changed.rating!!, 0.0)
        val mirroredBeforeWrite = existing.copy(score = 9, tmdbRating = null)
        assertEquals(
            9.0,
            resolveTmdbNativeWriteState(sibling.copy(score = 9), mirroredBeforeWrite).rating!!,
            0.0
        )

        val newAccountItem = resolveTmdbNativeWriteState(
            sibling.copy(status = WatchStatus.Planned),
            existingAccountEntry = null
        )
        assertTrue(newAccountItem.watchlist)
        assertEquals(8.0, newAccountItem.rating!!, 0.0)
    }

    @Test
    fun snapshotIsIdempotentAndKeepsMovieAndTvNamespacesSeparate() {
        val movie = entry(id = 0, tmdbId = 550)
        val tv = entry(id = 0, type = MediaType.TvShow, tmdbId = 550)
        val merged = mergeTmdbAccountSnapshot(listOf(movie, tv, movie), emptyList())

        assertEquals(2, merged.size)
        assertTrue(merged.all { it.source == TMDB_ACCOUNT_SOURCE })
        assertTrue(isTmdbAccountSource(merged.first().source))
    }
}
