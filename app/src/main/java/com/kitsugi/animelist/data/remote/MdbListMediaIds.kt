package com.kitsugi.animelist.data.remote

import java.util.Locale

/**
 * Source-safe identities used for an MDBList lookup.
 *
 * `JikanSearchResult.malId` and `MediaEntry.malId` are legacy stable-ID slots: depending on
 * the source they can contain a MAL, AniList, Kitsu, Bangumi, Shikimori, Simkl or TMDB ID.
 * Keeping those namespaces separate prevents a rating request for an unrelated title.
 */
internal data class MdbListMediaIds(
    val malId: Int? = null,
    val aniListId: Int? = null,
    val tmdbId: Int? = null,
    val kitsuId: Int? = null,
    val bangumiId: Int? = null,
    val shikimoriId: Int? = null
) {
    val hasLookupIdentity: Boolean
        get() = malId != null || aniListId != null || tmdbId != null ||
            kitsuId != null || bangumiId != null
}

private fun Int?.positiveOrNull(): Int? = this?.takeIf { it > 0 }
private fun Int?.realMalOrNull(): Int? = this?.takeIf { it in 1..99_999_999 }

/** Decode only IDs guaranteed by the source; never reinterpret an offset ID as a MAL ID. */
internal fun resolveMdbListMediaIds(
    source: String,
    storedId: Int?,
    resolvedMalId: Int?,
    resolvedTmdbId: Int?
): MdbListMediaIds {
    val normalizedSource = source.trim().lowercase(Locale.ROOT)
    val stored = storedId.positiveOrNull()
    val realMal = resolvedMalId.realMalOrNull()
    val tmdb = resolvedTmdbId.positiveOrNull()

    return when (normalizedSource) {
        "anilist", "al" -> {
            val aniListIds = resolveMdbListAniListIds(
                stableId = stored ?: 0,
                realMalId = realMal
            )
            MdbListMediaIds(
                malId = aniListIds.malId,
                aniListId = aniListIds.aniListId,
                tmdbId = tmdb
            )
        }

        "kitsu" -> MdbListMediaIds(
            // Legacy library rows may store a real MAL ID; canonical rows use 300M+Kitsu ID.
            malId = realMal ?: stored.realMalOrNull(),
            tmdbId = tmdb,
            kitsuId = KitsuIdNamespace.rawIdFromStable(stored)
        )

        "bangumi", "bgm", "bgm.tv" -> MdbListMediaIds(
            malId = realMal,
            tmdbId = tmdb,
            bangumiId = BangumiIdNamespace.rawIdOrNull(stored)
        )

        "shikimori", "shiki" -> MdbListMediaIds(
            malId = realMal,
            tmdbId = tmdb,
            shikimoriId = stored?.let { id ->
                if (id in 400_000_001..499_999_999) id - 400_000_000 else id
            }
        )

        "tmdb", "themoviedb" -> MdbListMediaIds(
            malId = realMal,
            // TMDB discovery results keep their own ID in the legacy malId slot.
            tmdbId = tmdb ?: stored
        )

        "simkl" -> MdbListMediaIds(
            // A Simkl ID is neither a MAL nor a TMDB ID.
            malId = realMal,
            tmdbId = tmdb
        )

        else -> MdbListMediaIds(
            malId = realMal ?: stored.realMalOrNull(),
            tmdbId = tmdb
        )
    }
}
