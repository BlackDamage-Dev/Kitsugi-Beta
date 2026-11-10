package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaEntry

/** A successfully fetched, complete account snapshot. Partial responses are never returned. */
data class TmdbAccountSnapshot(
    val entries: List<MediaEntry>,
    val watchlist: List<MediaEntry>,
    val favorites: List<MediaEntry>,
    val rated: List<MediaEntry>,
    val fetchedAtMs: Long = System.currentTimeMillis()
) {
    val watchlistCount: Int get() = watchlist.size
    val favoriteCount: Int get() = favorites.size
    val ratedCount: Int get() = rated.size
}

data class TmdbListSummary(
    val id: Int,
    val name: String,
    val description: String = "",
    val itemCount: Int = 0
)
