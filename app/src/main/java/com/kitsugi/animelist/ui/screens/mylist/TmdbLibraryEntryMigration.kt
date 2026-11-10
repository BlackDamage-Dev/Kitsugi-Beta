package com.kitsugi.animelist.ui.screens.mylist

import android.util.Log
import com.kitsugi.animelist.data.local.MediaEntryDao
import com.kitsugi.animelist.data.local.MediaEntryEntity
import com.kitsugi.animelist.model.MediaIdentity
import java.util.Locale

/**
 * TMDB catalog metadata and a TMDB account Watchlist are different things. Older
 * favorite-button code nevertheless inserted catalog rows with source=tmdb, falsely making
 * them look like account membership. Move those legacy rows to the local movie/TV list
 * provider without ever deleting a remote provider record.
 *
 * A legacy row is merged into an existing Simkl row only when a TMDB or Simkl ID agrees and
 * the provider types are compatible. Title alone is deliberately not enough. If the match is
 * ambiguous, the row is only relabelled and left for the normal identity resolver to handle.
 */
internal object TmdbLibraryEntryMigration {
    private const val TAG = "TmdbLibraryMigration"

    internal data class Plan(
        val updates: List<MediaEntryEntity>,
        val deleteIds: List<Int>
    ) {
        val isEmpty: Boolean get() = updates.isEmpty() && deleteIds.isEmpty()
    }

    suspend fun run(dao: MediaEntryDao) {
        val plan = plan(dao.getAll())
        if (plan.isEmpty) return
        // Local-only transaction: no Simkl/TMDB delete or remote sync is triggered here.
        dao.smartImportTransaction(
            toInsert = emptyList(),
            toUpdate = plan.updates,
            toDeleteIds = plan.deleteIds
        )
        Log.i(TAG, "Legacy TMDB rows repaired: ${plan.updates.size} updated, ${plan.deleteIds.size} merged")
    }

    internal fun plan(entries: List<MediaEntryEntity>): Plan {
        val originalById = entries.associateBy { it.id }
        val working = entries.filterNot { isLegacyTmdb(it) }.toMutableList()
        val deleted = mutableListOf<Int>()

        entries.filter { isLegacyTmdb(it) }.forEach { legacy ->
            val legacyTmdbId = legacyTmdbId(legacy)
            val converted = legacy.copy(
                source = "simkl",
                // In a source=tmdb row the legacy malId slot is a TMDB id, not a MAL id.
                // Keep it only in tmdbId; never copy it into Simkl's MAL field.
                malId = null,
                tmdbId = legacyTmdbId
            )
            val matches = working.filter { candidate ->
                isSimkl(candidate) && compatibleTypes(legacy.type, candidate.type) &&
                    idsAgree(legacy, legacyTmdbId, candidate) &&
                    hasRequiredTypeEvidence(legacy, candidate)
            }
            val target = matches.singleOrNull()
            if (target == null) {
                // Zero or multiple matches: do not guess which user record should disappear.
                working += converted
            } else {
                working.removeAll { it.id == target.id }
                working += merge(target, legacy, converted)
                deleted += legacy.id
            }
        }

        val updates = working.mapNotNull { current ->
            val original = originalById[current.id]
            if (original != null && original != current) current else null
        }
        return Plan(updates = updates, deleteIds = deleted.distinct())
    }

    private fun isLegacyTmdb(entry: MediaEntryEntity): Boolean =
        entry.source.trim().lowercase(Locale.ROOT) in setOf("tmdb", "themoviedb")

    private fun isSimkl(entry: MediaEntryEntity): Boolean =
        entry.source.trim().lowercase(Locale.ROOT) == "simkl"

    private fun legacyTmdbId(entry: MediaEntryEntity): Int? =
        entry.tmdbId?.takeIf { it > 0 } ?: entry.malId?.takeIf { it > 0 }

    private fun compatibleTypes(left: String, right: String): Boolean {
        if (left == right) return true
        val videoTypes = setOf("Movie", "TvShow")
        return (left == "Anime" && right in videoTypes) || (right == "Anime" && left in videoTypes)
    }

    /** A cross-type numeric ID is not enough on its own: TMDB movie/TV IDs share a number space. */
    private fun hasRequiredTypeEvidence(legacy: MediaEntryEntity, target: MediaEntryEntity): Boolean {
        if (legacy.type == target.type) return true
        val legacyYear = legacy.year
        val targetYear = target.year
        if (legacyYear == null || targetYear == null || legacyYear != targetYear) return false
        val legacyTitles = listOfNotNull(legacy.title, legacy.titleEnglish, legacy.titleJapanese)
            .map(MediaIdentity::normalizedTitle).filter { it.length >= 2 }.toSet()
        val targetTitles = listOfNotNull(target.title, target.titleEnglish, target.titleJapanese)
            .map(MediaIdentity::normalizedTitle).filter { it.length >= 2 }.toSet()
        return legacyTitles.intersect(targetTitles).isNotEmpty()
    }

    private fun idsAgree(
        legacy: MediaEntryEntity,
        legacyTmdbId: Int?,
        target: MediaEntryEntity
    ): Boolean {
        val targetTmdbId = target.tmdbId?.takeIf { it > 0 }
        val legacySimklId = legacy.simklId?.takeIf { it > 0 }
        val targetSimklId = target.simklId?.takeIf { it > 0 }
        val tmdbAgrees = legacyTmdbId != null && targetTmdbId == legacyTmdbId
        val simklAgrees = legacySimklId != null && targetSimklId == legacySimklId
        if (!tmdbAgrees && !simklAgrees) return false
        if (legacyTmdbId != null && targetTmdbId != null && legacyTmdbId != targetTmdbId) return false
        if (legacySimklId != null && targetSimklId != null && legacySimklId != targetSimklId) return false
        return true
    }

    private fun merge(
        target: MediaEntryEntity,
        legacy: MediaEntryEntity,
        convertedLegacy: MediaEntryEntity
    ): MediaEntryEntity {
        // Imports can be older than a local edit. Prefer the newest user state, while OR-ing
        // favorite/adult flags so a provider import cannot erase a user's favorite.
        val preferred = if (legacy.updatedAt >= target.updatedAt) legacy else target
        val other = if (preferred === legacy) target else legacy
        fun latestText(value: String?, fallback: String?): String? =
            value?.takeIf { it.isNotBlank() } ?: fallback

        return target.copy(
            title = latestText(preferred.title, other.title).orEmpty(),
            subtitle = latestText(preferred.subtitle, other.subtitle).orEmpty(),
            // Keep Simkl's own type (Movie vs Anime) and source; the combined view handles
            // the other provider's type through verified identity keys.
            type = target.type,
            status = preferred.status,
            score = preferred.score,
            progress = preferred.progress,
            total = preferred.total ?: other.total,
            isFavorite = target.isFavorite || legacy.isFavorite,
            isAdult = target.isAdult || legacy.isAdult,
            source = "simkl",
            malId = target.malId,
            imageUrl = latestText(preferred.imageUrl, other.imageUrl),
            year = preferred.year ?: other.year,
            synopsis = latestText(preferred.synopsis, other.synopsis),
            startDate = latestText(preferred.startDate, other.startDate),
            endDate = latestText(preferred.endDate, other.endDate),
            notes = latestText(preferred.notes, other.notes),
            tags = latestText(preferred.tags, other.tags),
            priority = preferred.priority ?: other.priority,
            isRepeating = preferred.isRepeating,
            repeatCount = preferred.repeatCount,
            repeatValue = preferred.repeatValue,
            volumeProgress = preferred.volumeProgress,
            isPrivate = preferred.isPrivate,
            isHiddenFromStatusLists = preferred.isHiddenFromStatusLists,
            updatedAt = maxOf(target.updatedAt, legacy.updatedAt),
            titleEnglish = latestText(preferred.titleEnglish, other.titleEnglish),
            titleJapanese = latestText(preferred.titleJapanese, other.titleJapanese),
            aniListEntryId = target.aniListEntryId ?: legacy.aniListEntryId,
            malListId = target.malListId ?: legacy.malListId,
            tmdbId = target.tmdbId ?: convertedLegacy.tmdbId,
            simklId = target.simklId ?: legacy.simklId
        )
    }
}
