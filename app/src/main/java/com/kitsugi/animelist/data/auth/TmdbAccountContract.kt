package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlin.math.roundToInt

/**
 * Real TMDB account membership, as opposed to the historical catalog-only `tmdb` source.
 * The badge/identity layer maps both values to the TMDB provider; the migration must not.
 */
const val TMDB_ACCOUNT_SOURCE: String = "tmdb_account"

/** TMDB v3 request tokens expire after about 15 minutes. */
const val TMDB_REQUEST_TOKEN_TTL_MILLIS: Long = 15L * 60L * 1000L

fun isTmdbRequestTokenUsable(createdAtMillis: Long, nowMillis: Long): Boolean =
    createdAtMillis > 0L &&
        createdAtMillis <= nowMillis + 60_000L &&
        nowMillis - createdAtMillis < TMDB_REQUEST_TOKEN_TTL_MILLIS
const val TMDB_LEGACY_CATALOG_SOURCE: String = "tmdb"

fun isTmdbAccountSource(source: String): Boolean =
    source.trim().equals(TMDB_ACCOUNT_SOURCE, ignoreCase = true)

/** Direct native account edits stay real even when automatic/cross-provider sync is off. */
fun shouldAttemptTmdbWrite(syncEnabled: Boolean, source: String): Boolean =
    syncEnabled || isTmdbAccountSource(source)

/** TMDB has no native Watching/Completed/etc. library status. */
fun tmdbWatchlistValue(status: WatchStatus): Boolean = status == WatchStatus.Planned

/** Convert an Int or Double score to TMDB's half-point, 0.5..10 rating scale. */
fun tmdbRatingValue(score: Number?): Double? {
    val value = score?.toDouble() ?: return null
    if (value <= 0.0) return null
    val halfPoints = (value.coerceIn(0.5, 10.0) * 2).roundToInt() / 2.0
    return halfPoints.coerceIn(0.5, 10.0)
}

fun supportsTmdbAccountType(type: MediaType): Boolean =
    type == MediaType.Movie || type == MediaType.TvShow

data class TmdbNativeWriteState(
    val watchlist: Boolean,
    val rating: Double?
)

/**
 * Field-level user intent for TMDB writes. Kitsugi-only edits must not rewrite unrelated
 * native account state, and an insert must not interpret absent values as remote deletes.
 * The token is persisted inside pending-sync JSON so retries keep the original intent.
 */
data class TmdbWriteFields(
    val watchlist: Boolean = false,
    val favorite: Boolean = false,
    val rating: Boolean = false
) {
    val any: Boolean get() = watchlist || favorite || rating

    fun toToken(): String = buildString {
        if (watchlist) append('w')
        if (favorite) append('f')
        if (rating) append('r')
    }

    companion object {
        val None = TmdbWriteFields()
        val All = TmdbWriteFields(watchlist = true, favorite = true, rating = true)

        fun fromToken(token: String?): TmdbWriteFields {
            if (token == null) return All // Legacy pending jobs predate field-level intent.
            require(token.all { it == 'w' || it == 'f' || it == 'r' }) { "Bozuk TMDB yazma maskesi" }
            return TmdbWriteFields(
                watchlist = 'w' in token,
                favorite = 'f' in token,
                rating = 'r' in token
            )
        }
    }
}

/** Remove idempotent mutations after reading the official per-item account state. */
fun TmdbWriteFields.onlyChangesFrom(
    remoteWatchlist: Boolean,
    remoteFavorite: Boolean,
    remoteRating: Double?,
    desiredWatchlist: Boolean,
    desiredFavorite: Boolean,
    desiredRating: Double?
): TmdbWriteFields = TmdbWriteFields(
    watchlist = watchlist && desiredWatchlist != remoteWatchlist,
    favorite = favorite && desiredFavorite != remoteFavorite,
    rating = rating && tmdbRatingValue(desiredRating) != tmdbRatingValue(remoteRating)
)

/** Positive-only create intent: false/null absence is not a destructive remote operation. */
fun tmdbWriteFieldsForInsert(entry: MediaEntry): TmdbWriteFields = TmdbWriteFields(
    watchlist = entry.tmdbWatchlist != null || entry.status == WatchStatus.Planned,
    favorite = entry.isFavorite,
    rating = entry.tmdbRating != null || entry.score != null
)

/**
 * Diff before sibling mirroring. Native Watchlist wins over status; the legacy status
 * fallback participates only while both rows have no native Watchlist value.
 */
fun tmdbWriteFieldsForUpdate(previous: MediaEntry?, current: MediaEntry): TmdbWriteFields {
    if (previous == null) return tmdbWriteFieldsForInsert(current)
    return TmdbWriteFields(
        watchlist = previous.tmdbWatchlist != current.tmdbWatchlist ||
            (previous.tmdbWatchlist == null && current.tmdbWatchlist == null &&
                tmdbWatchlistValue(previous.status) != tmdbWatchlistValue(current.status)),
        favorite = previous.isFavorite != current.isFavorite,
        rating = previous.tmdbRating != current.tmdbRating || previous.score != current.score
    )
}

/**
 * Resolve native fields without letting a sibling provider's integer score/status erase
 * TMDB-only state. A real score change still propagates; an unchanged rounded score keeps
 * the existing half-point value. New account items use the legacy Planned fallback.
 */
fun resolveTmdbNativeWriteState(entry: MediaEntry, existingAccountEntry: MediaEntry?): TmdbNativeWriteState {
    if (isTmdbAccountSource(entry.source) || existingAccountEntry == null) {
        return TmdbNativeWriteState(
            watchlist = entry.tmdbWatchlist ?: tmdbWatchlistValue(entry.status),
            rating = entry.tmdbRating?.let { tmdbRatingValue(it) } ?: tmdbRatingValue(entry.score)
        )
    }
    return TmdbNativeWriteState(
        watchlist = existingAccountEntry.tmdbWatchlist ?: tmdbWatchlistValue(existingAccountEntry.status),
        rating = if (existingAccountEntry.score == entry.score) {
            existingAccountEntry.tmdbRating?.let { tmdbRatingValue(it) } ?: tmdbRatingValue(entry.score)
        } else {
            tmdbRatingValue(entry.score)
        }
    )
}

/**
 * A account-state response is deliberately independent from MediaEntry: a detail screen can
 * display TMDB's state without manufacturing a local list row.
 */
data class TmdbAccountState(
    val favorite: Boolean = false,
    val watchlist: Boolean = false,
    val rating: Double? = null
)

/**
 * Merge a complete TMDB account item with local state. Remote membership, favorite and score
 * are authoritative for their own fields; user progress and editorial fields are never reset
 * by a provider refresh.
 */
fun mergeTmdbAccountEntry(remote: MediaEntry, local: MediaEntry?): MediaEntry {
    if (local == null) return remote.copy(source = TMDB_ACCOUNT_SOURCE)
    return remote.copy(
        id = local.id,
        source = TMDB_ACCOUNT_SOURCE,
        status = local.status,
        progress = local.progress,
        total = local.total ?: remote.total,
        startDate = local.startDate,
        endDate = local.endDate,
        notes = local.notes,
        tags = local.tags,
        priority = local.priority,
        isRepeating = local.isRepeating,
        repeatCount = local.repeatCount,
        repeatValue = local.repeatValue,
        volumeProgress = local.volumeProgress,
        isPrivate = local.isPrivate,
        isHiddenFromStatusLists = local.isHiddenFromStatusLists,
        updatedAt = local.updatedAt,
        // Native account fields come from TMDB, not from a stale sibling row.
        isFavorite = remote.isFavorite,
        score = remote.score,
        tmdbWatchlist = remote.tmdbWatchlist,
        tmdbRating = remote.tmdbRating,
        // Keep richer local metadata when a paged account response omits it.
        subtitle = remote.subtitle.ifBlank { local.subtitle },
        synopsis = remote.synopsis ?: local.synopsis,
        imageUrl = remote.imageUrl ?: local.imageUrl,
        year = remote.year ?: local.year,
        titleEnglish = remote.titleEnglish ?: local.titleEnglish,
        titleJapanese = remote.titleJapanese ?: local.titleJapanese,
        // A TMDB-source row must never reinterpret a sibling's MAL id as a TMDB id.
        // Keep the provider-specific slot empty; the crosswalk can resolve MAL safely.
        malId = remote.malId,
        simklId = local.simklId ?: remote.simklId,
        tmdbId = remote.tmdbId ?: local.tmdbId,
        aniListEntryId = local.aniListEntryId
    )
}

/**
 * Select a local row only by a verified identity. Title-only matching is intentionally not
 * enough to overwrite user fields; a same-type TMDB ID is the normal path.
 */
fun findTmdbLocalMatch(remote: MediaEntry, localEntries: List<MediaEntry>): MediaEntry? {
    val exact = localEntries.filter {
        it.tmdbId != null && it.tmdbId == remote.tmdbId && it.type == remote.type
    }
    if (exact.size == 1) return exact.single()
    if (exact.size > 1) return exact.maxByOrNull { it.updatedAt }
    return localEntries.filter {
        MediaIdentity.sameMedia(it, remote, allowTitle = false, allowCrossType = false)
    }.maxByOrNull { it.updatedAt }
}

/** Prepare an idempotent snapshot while preserving fields from all verified local siblings. */
fun mergeTmdbAccountSnapshot(
    remoteEntries: List<MediaEntry>,
    localEntries: List<MediaEntry>
): List<MediaEntry> {
    val usedLocalIds = mutableSetOf<Int>()
    return remoteEntries.map { remote ->
        val candidates = localEntries.filter { it.id <= 0 || it.id !in usedLocalIds }
        // Prefer the existing TMDB row for its own user state; only use a verified
        // sibling when this is the first TMDB import for that production.
        val local = findTmdbLocalMatch(remote, candidates.filter { isTmdbAccountSource(it.source) })
            ?: findTmdbLocalMatch(remote, candidates)
        if (local != null && local.id > 0) usedLocalIds += local.id
        mergeTmdbAccountEntry(remote, local)
    }.distinctBy { "${it.type.name}:${it.tmdbId}" }
}
