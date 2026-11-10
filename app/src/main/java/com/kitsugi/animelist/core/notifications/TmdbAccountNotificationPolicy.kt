package com.kitsugi.animelist.core.notifications

import com.kitsugi.animelist.data.remote.TmdbAccountSnapshot
import com.kitsugi.animelist.model.MediaEntry

internal const val TMDB_SNAPSHOT_PREFS_PREFIX = "tmdb_account_snapshot_fingerprints_v2_"

/** Baselines are account-scoped; a different TMDB user always starts with a fresh baseline. */
internal fun tmdbSnapshotPreferenceKey(accountId: Int): String {
    require(accountId > 0) { "TMDB account id gerekli" }
    return TMDB_SNAPSHOT_PREFS_PREFIX + accountId
}

internal fun MediaEntry.accountKey(): String = "${type.name}:$tmdbId"

internal fun tmdbSnapshotFingerprints(snapshot: TmdbAccountSnapshot): Set<String> =
    snapshot.entries.mapTo(linkedSetOf()) { entry ->
        val key = entry.accountKey()
        val watchlist = snapshot.watchlist.any { it.accountKey() == key }
        val favorite = snapshot.favorites.any { it.accountKey() == key }
        val rating = snapshot.rated.firstOrNull { it.accountKey() == key }?.tmdbRating
        "$key|w=$watchlist|f=$favorite|r=${rating ?: 0}"
    }

/** First complete snapshot is a baseline; later removals/re-additions are detected by key. */
internal fun newTmdbAccountEntries(
    previousFingerprints: Set<String>?,
    snapshot: TmdbAccountSnapshot
): List<MediaEntry> {
    if (previousFingerprints == null) return emptyList()
    val previousKeys = previousFingerprints.mapTo(hashSetOf()) { it.substringBefore('|') }
    return snapshot.entries.filter { it.accountKey() !in previousKeys }
}
