package com.kitsugi.animelist.data.remote

/**
 * Builds provider-specific Stremio video IDs from resolved external IDs.
 * The manifest router decides which of these identity families each addon is willing to receive.
 */
data class StremioVideoLookup(
    val contentType: String,
    val imdbVideoId: String?,
    val kitsuVideoId: String?,
    val additionalVideoIds: List<String> = emptyList()
) {
    val allVideoIds: List<String>
        get() = (listOfNotNull(kitsuVideoId, imdbVideoId) + additionalVideoIds).distinct()
}

object StremioVideoId {
    fun forContent(
        isMovie: Boolean,
        imdbId: String?,
        kitsuId: Int?,
        season: Int,
        episode: Int,
        tmdbId: Int? = null,
        aniListId: Int? = null,
        malId: Int? = null,
        tvdbId: Int? = null,
        bangumiId: Int? = null,
        simklId: Int? = null
    ): StremioVideoLookup {
        val type = if (isMovie) "movie" else "series"
        val safeSeason = season.coerceAtLeast(1)
        val safeEpisode = episode.coerceAtLeast(1)
        val safeImdbId = imdbId?.trim()?.takeIf { it.isNotEmpty() }
        val safeKitsuId = kitsuId?.takeIf { it > 0 }

        fun namespaced(prefix: String, value: Int?): String? {
            val id = value?.takeIf { it > 0 } ?: return null
            val root = "$prefix:$id"
            return when {
                isMovie -> root
                prefix == "kitsu" || prefix == "anilist" || prefix == "mal" || prefix == "myanimelist" || prefix == "bgm" -> "$root:$safeEpisode"
                else -> "$root:$safeSeason:$safeEpisode"
            }
        }

        val imdbVideoId = when {
            safeImdbId == null -> null
            isMovie -> safeImdbId
            else -> "$safeImdbId:$safeSeason:$safeEpisode"
        }
        val kitsuVideoId = when {
            safeKitsuId == null -> null
            isMovie -> "kitsu:$safeKitsuId"
            else -> "kitsu:$safeKitsuId:$safeEpisode"
        }
        val additional = listOfNotNull(
            namespaced("tmdb", tmdbId),
            namespaced("anilist", aniListId),
            namespaced("mal", malId),
            namespaced("myanimelist", malId),
            namespaced("tvdb", tvdbId),
            namespaced("bgm", bangumiId),
            namespaced("simkl", simklId)
        )

        return StremioVideoLookup(
            contentType = type,
            imdbVideoId = imdbVideoId,
            kitsuVideoId = kitsuVideoId,
            additionalVideoIds = additional
        )
    }
}
