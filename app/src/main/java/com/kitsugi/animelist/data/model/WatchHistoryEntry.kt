package com.kitsugi.animelist.data.model

/**
 * A watch-history item represents one season/episode, not one particular CDN URL.
 * The addon and stream fields are hints for re-fetching the selected source. CDN URLs,
 * headers and subtitle links can expire, so they must not be treated as permanent links.
 * New fields are nullable so older Gson/SharedPreferences records remain readable.
 */
data class WatchHistoryEntry(
    val animeId: String,
    val animeTitle: String,
    val posterUrl: String?,
    val episode: Int,
    val season: Int,
    val isMovie: Boolean = false,
    val quality: String? = null,
    val source: String? = null,
    val watchedAtMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val malId: Int? = null,
    val aniListId: Int? = null,
    val tmdbId: Int? = null,
    // Retained for backwards compatibility/diagnostics, never assumed to be reusable.
    val streamUrl: String? = null,
    val streamHeaders: Map<String, String>? = null,
    val streamTitle: String? = null,
    val streamName: String? = null,
    val cs3Url: String? = null,
    val cs3ApiName: String? = null,
    // Stable provider identity and selected-stream fingerprint (no manifest credentials).
    val addonId: String? = null,
    val streamInfoHash: String? = null,
    val streamFileIndex: Int? = null,
    val streamUrlKey: String? = null,
    val streamAudioKind: String? = null,
    val streamLanguages: List<String>? = null,
    val bangumiId: Int? = null,
    val imdbId: String? = null,
    val mediaType: String? = null,
    val titleEnglish: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    val synonyms: List<String>? = null,
    val startYear: Int? = null
)
