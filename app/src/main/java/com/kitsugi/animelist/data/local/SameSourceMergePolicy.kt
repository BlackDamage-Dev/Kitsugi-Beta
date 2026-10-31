package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType

/** Pure field-merge policy used by same-provider duplicate cleanup and import batching. */
internal object SameSourceMergePolicy {
    /** Same upgrade-only title rules as smartImport, without requiring a Room entity. */
    fun preserveTitles(existing: MediaEntry, imported: MediaEntry): MediaEntry {
        val importedTitle = imported.title.trim()
        val existingTitle = existing.title.trim()
        val title = when {
            importedTitle.isEmpty() -> existing.title
            TitleScriptPolicy.hasCjkCharacters(importedTitle) &&
                TitleScriptPolicy.isLatinText(existingTitle) &&
                !existingTitle.startsWith("Bangumi #") -> existing.title
            else -> imported.title
        }
        val titleEnglish = imported.titleEnglish?.takeIf { TitleScriptPolicy.isLatinText(it) }
            ?: existing.titleEnglish ?: imported.titleEnglish
        val titleJapanese = imported.titleJapanese?.takeIf { it.isNotBlank() }
            ?: existing.titleJapanese?.takeIf { it.isNotBlank() }
            ?: importedTitle.takeIf { TitleScriptPolicy.hasCjkCharacters(it) }
        return imported.copy(title = title, titleEnglish = titleEnglish, titleJapanese = titleJapanese)
    }

    fun resolveAddedAt(entry: MediaEntry, existingAddedAt: Long = 0L, nowSeconds: Long? = null): Long {
        val incoming = com.kitsugi.animelist.model.normalizeToEpochSeconds(entry.addedAt)
        if (incoming > 0L) return incoming
        val existing = com.kitsugi.animelist.model.normalizeToEpochSeconds(existingAddedAt)
        if (existing > 0L) return existing
        val updated = com.kitsugi.animelist.model.normalizeToEpochSeconds(entry.updatedAt)
        if (updated > 0L) return updated
        return nowSeconds ?: 0L
    }

    fun merge(
        survivor: MediaEntry,
        incoming: MediaEntry,
        preferIncomingUserFields: Boolean
    ): MediaEntry {
    val userPrimary = if (preferIncomingUserFields) incoming else survivor
    val userSecondary = if (preferIncomingUserFields) survivor else incoming
    val titleMerged = preserveTitles(survivor, incoming)
    // Anime + Movie çakışmasında gerçek MAL kimliği olan Anime türü korunur
    val resolvedType = when {
        survivor.type != incoming.type &&
            (survivor.type == MediaType.Anime || incoming.type == MediaType.Anime) &&
            (com.kitsugi.animelist.model.MediaIdentity.realMalId(survivor) != null ||
                com.kitsugi.animelist.model.MediaIdentity.realMalId(incoming) != null) -> MediaType.Anime
        else -> survivor.type
    }
    val realMal = com.kitsugi.animelist.model.MediaIdentity.realMalId(survivor)
        ?: com.kitsugi.animelist.model.MediaIdentity.realMalId(incoming)
    return survivor.copy(
        title = titleMerged.title,
        titleEnglish = titleMerged.titleEnglish,
        titleJapanese = titleMerged.titleJapanese,
        subtitle = userPrimary.subtitle.takeIf { it.isNotBlank() } ?: userSecondary.subtitle,
        type = resolvedType,
        status = userPrimary.status,
        score = userPrimary.score ?: userSecondary.score,
        progress = maxOf(survivor.progress, incoming.progress),
        total = survivor.total ?: incoming.total,
        isFavorite = survivor.isFavorite || incoming.isFavorite,
        isAdult = survivor.isAdult || incoming.isAdult,
        malId = realMal ?: survivor.malId ?: incoming.malId,
        imageUrl = survivor.imageUrl?.takeIf { it.isNotBlank() }
            ?: incoming.imageUrl?.takeIf { it.isNotBlank() },
        year = survivor.year ?: incoming.year,
        synopsis = survivor.synopsis?.takeIf { it.isNotBlank() }
            ?: incoming.synopsis?.takeIf { it.isNotBlank() },
        startDate = userPrimary.startDate?.takeIf { it.isNotBlank() } ?: userSecondary.startDate,
        endDate = userPrimary.endDate?.takeIf { it.isNotBlank() } ?: userSecondary.endDate,
        notes = userPrimary.notes?.takeIf { it.isNotBlank() } ?: userSecondary.notes,
        tags = userPrimary.tags?.takeIf { it.isNotBlank() } ?: userSecondary.tags,
        priority = userPrimary.priority ?: userSecondary.priority,
        isRepeating = survivor.isRepeating || incoming.isRepeating,
        repeatCount = maxOf(survivor.repeatCount, incoming.repeatCount),
        repeatValue = maxOf(survivor.repeatValue, incoming.repeatValue),
        volumeProgress = maxOf(survivor.volumeProgress, incoming.volumeProgress),
        isPrivate = userPrimary.isPrivate || userSecondary.isPrivate,
        isHiddenFromStatusLists = userPrimary.isHiddenFromStatusLists || userSecondary.isHiddenFromStatusLists,
        updatedAt = maxOf(survivor.updatedAt, incoming.updatedAt),
        addedAt = resolveAddedAt(incoming, existingAddedAt = survivor.addedAt),
        aniListEntryId = survivor.aniListEntryId ?: incoming.aniListEntryId,
        malListId = survivor.malListId ?: incoming.malListId,
        tmdbId = survivor.tmdbId?.takeIf { it > 0 } ?: incoming.tmdbId?.takeIf { it > 0 },
        simklId = survivor.simklId?.takeIf { it > 0 } ?: incoming.simklId?.takeIf { it > 0 },
        tmdbWatchlist = survivor.tmdbWatchlist ?: incoming.tmdbWatchlist,
        tmdbRating = survivor.tmdbRating ?: incoming.tmdbRating
    )
}

}
