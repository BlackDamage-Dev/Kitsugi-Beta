package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.model.WatchHistoryEntry
import java.security.MessageDigest
import java.util.Locale

/** Pure identity and list operations, shared by the picker, player and persistence layer. */
object WatchHistoryRules {
    const val MAX_ENTRIES = 200

    fun mediaId(
        aniListId: Int?,
        malId: Int?,
        tmdbId: Int?,
        bangumiId: Int?,
        title: String,
        isMovie: Boolean,
        cs3Url: String? = null,
        cs3ApiName: String? = null,
        startYear: Int? = null
    ): String = when {
        aniListId != null && aniListId > 0 -> "anilist:$aniListId"
        malId != null && malId > 0 -> "mal:$malId"
        tmdbId != null && tmdbId > 0 -> "tmdb:${if (isMovie) "movie" else "series"}:$tmdbId"
        bangumiId != null && bangumiId > 0 -> "bangumi:$bangumiId"
        !cs3Url.isNullOrBlank() && !cs3ApiName.isNullOrBlank() ->
            "cs:${sha256("${cs3ApiName.lowercase(Locale.ROOT)}|$cs3Url")}"
        else -> "title:${sha256("${normalizeTitle(title)}|$isMovie|${startYear ?: ""}")}"
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun normalizeTitle(title: String): String = title.trim().lowercase(Locale.ROOT)
        .replace(Regex("[\\p{Punct}\\s]+"), " ").trim()

    /**
     * Old entries used bare numeric IDs or title/URL hashCodes. Match them by their
     * actual stored IDs first, never by a coincidentally equal number from another service.
     * Title fallback is only used when at least one side lacks external IDs.
     */
    fun sameMedia(a: WatchHistoryEntry, b: WatchHistoryEntry): Boolean {
        if (a.isMovie != b.isMovie) return false
        val pairs = listOf(
            a.aniListId to b.aniListId,
            a.malId to b.malId,
            a.tmdbId to b.tmdbId,
            a.bangumiId to b.bangumiId
        ).filter { (left, right) -> left != null && right != null && left > 0 && right > 0 }
        if (pairs.isNotEmpty()) return pairs.all { (left, right) -> left == right }

        val aHasIds = listOf(a.aniListId, a.malId, a.tmdbId, a.bangumiId).any { it != null && it > 0 }
        val bHasIds = listOf(b.aniListId, b.malId, b.tmdbId, b.bangumiId).any { it != null && it > 0 }
        if (aHasIds && bHasIds) return false
        if (a.animeId == b.animeId) return true
        if (a.startYear != null && b.startYear != null && a.startYear != b.startYear) return false
        val title = normalizeTitle(a.animeTitle)
        return title.isNotEmpty() && title == normalizeTitle(b.animeTitle)
    }

    fun findMatching(history: List<WatchHistoryEntry>, entry: WatchHistoryEntry): WatchHistoryEntry? =
        history.firstOrNull { it.season == entry.season && it.episode == entry.episode && sameMedia(it, entry) }

    /** Separate legacy bare IDs that happened to belong to different metadata services. */
    fun normalizeIds(history: List<WatchHistoryEntry>): List<WatchHistoryEntry> {
        val owners = mutableMapOf<String, WatchHistoryEntry>()
        return history.map { entry ->
            var key = entry.animeId
            if (owners[key]?.let { !sameMedia(it, entry) } == true) {
                key = mediaId(
                    entry.aniListId, entry.malId, entry.tmdbId, entry.bangumiId,
                    entry.animeTitle, entry.isMovie, entry.cs3Url, entry.cs3ApiName, entry.startYear
                )
                if (owners[key]?.let { !sameMedia(it, entry) } == true) {
                    key += ":${sha256("${entry.animeTitle}|${entry.startYear}|${entry.isMovie}")}"
                }
            }
            val normalized = if (key == entry.animeId) entry else entry.copy(animeId = key)
            owners.putIfAbsent(key, normalized)
            normalized
        }.distinctBy { Triple(it.animeId, it.season, it.episode) }
    }

    /** Completed videos replay from the start; otherwise keep even short partial progress. */
    fun resumePosition(entry: WatchHistoryEntry): Long {
        val position = entry.positionMs.coerceAtLeast(0L)
        val duration = entry.durationMs
        if (duration > 0L && position >= duration - 10_000L && position.toDouble() / duration >= 0.95) return 0L
        return if (duration > 0L) position.coerceAtMost(duration) else position
    }

    /** Preserve the existing key and progress while upgrading an old record with new source data. */
    fun record(history: List<WatchHistoryEntry>, entry: WatchHistoryEntry): List<WatchHistoryEntry> {
        val safeHistory = normalizeIds(history)
        val sameTitle = safeHistory.filter { sameMedia(it, entry) }
        var key = sameTitle.firstOrNull { it.animeId == entry.animeId }?.animeId
            ?: sameTitle.firstOrNull()?.animeId ?: entry.animeId
        if (safeHistory.any { it.animeId == key && !sameMedia(it, entry) }) {
            key = mediaId(entry.aniListId, entry.malId, entry.tmdbId, entry.bangumiId,
                entry.animeTitle, entry.isMovie, entry.cs3Url, entry.cs3ApiName, entry.startYear)
            if (safeHistory.any { it.animeId == key && !sameMedia(it, entry) }) {
                key += ":${sha256("${entry.animeTitle}|${entry.startYear}|${entry.isMovie}")}"
            }
        }
        // Keep one media key across seasons and upgrade mixed legacy/new IDs together.
        val normalized = safeHistory.map { if (sameMedia(it, entry)) it.copy(animeId = key) else it }
        val old = findMatching(normalized, entry)
        val base = entry.copy(animeId = key)
        val updated = if (old == null) base else base.copy(
            animeId = key,
            animeTitle = entry.animeTitle.takeIf { it.isNotBlank() } ?: old.animeTitle,
            posterUrl = entry.posterUrl?.takeIf { it.isNotBlank() } ?: old.posterUrl,
            positionMs = entry.positionMs.takeIf { it > 0L } ?: old.positionMs,
            durationMs = entry.durationMs.takeIf { it > 0L } ?: old.durationMs,
            malId = entry.malId ?: old.malId,
            aniListId = entry.aniListId ?: old.aniListId,
            tmdbId = entry.tmdbId ?: old.tmdbId,
            bangumiId = entry.bangumiId ?: old.bangumiId,
            imdbId = entry.imdbId ?: old.imdbId,
            mediaType = entry.mediaType ?: old.mediaType,
            titleEnglish = entry.titleEnglish ?: old.titleEnglish,
            titleRomaji = entry.titleRomaji ?: old.titleRomaji,
            titleNative = entry.titleNative ?: old.titleNative,
            synonyms = entry.synonyms?.takeIf { it.isNotEmpty() } ?: old.synonyms,
            startYear = entry.startYear ?: old.startYear
        )
        return (listOf(updated) + normalized.filterNot {
            it.season == entry.season && it.episode == entry.episode && sameMedia(it, entry)
        }).distinctBy { Triple(it.animeId, it.season, it.episode) }.take(MAX_ENTRIES)
    }

    fun updateProgress(
        history: List<WatchHistoryEntry>,
        animeId: String,
        season: Int,
        episode: Int,
        positionMs: Long,
        durationMs: Long,
        watchedAtMs: Long
    ): List<WatchHistoryEntry> {
        val index = history.indexOfFirst { it.animeId == animeId && it.season == season && it.episode == episode }
        if (index < 0) return history
        val old = history[index]
        val duration = when {
            durationMs > 0L -> durationMs
            old.durationMs > 0L && positionMs <= old.durationMs -> old.durationMs
            else -> 0L // An old duration cannot prove completion of a longer/unknown stream.
        }
        val position = positionMs.coerceAtLeast(0L).let { if (duration > 0L) it.coerceAtMost(duration) else it }
        val updated = old.copy(positionMs = position, durationMs = duration, watchedAtMs = watchedAtMs)
        return listOf(updated) + history.filterIndexed { i, _ -> i != index }
    }

    fun remove(history: List<WatchHistoryEntry>, animeId: String, season: Int, episode: Int): List<WatchHistoryEntry> =
        history.filterNot { it.animeId == animeId && it.season == season && it.episode == episode }
}
