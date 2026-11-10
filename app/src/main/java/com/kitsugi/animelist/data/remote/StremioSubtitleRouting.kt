package com.kitsugi.animelist.data.remote

/** Manifest-derived subtitle resource capabilities. A null prefix list means unrestricted IDs. */
data class StremioSubtitleResourceSpec(
    val types: List<String>?,
    val idPrefixes: List<String>?
)

data class SubtitleQueryId(
    val type: String,
    val id: String,
    /** Can be sent to an unfiltered addon without additional namespace evidence. */
    val safeForUnrestrictedAddon: Boolean = true
)

data class StremioSubtitleRoute(
    val name: String,
    val manifestUrl: String,
    val types: String?,
    val idPrefixes: List<String>?
)

data class StremioSubtitleTask(
    val addonName: String,
    val manifestUrl: String,
    val requestType: String,
    val videoId: String
)

/**
 * Parse the resource actually advertised by each addon. String resources inherit top-level
 * capabilities; object resources use their own prefixes (omission means no prefix restriction).
 */
object StremioSubtitleRouting {
    private val SERIES_TYPES = listOf("series", "anime")
    private val MOVIE_TYPES = listOf("movie", "animemovie", "anime")

    fun parseSubtitleResource(
        resources: List<*>?,
        manifestTypes: List<String>,
        manifestIdPrefixes: List<String>
    ): StremioSubtitleResourceSpec? {
        resources ?: return null
        for (resource in resources) {
            when (resource) {
                is String -> if (resource == "subtitles") {
                    return StremioSubtitleResourceSpec(
                        types = manifestTypes.takeIf { it.isNotEmpty() },
                        idPrefixes = manifestIdPrefixes.takeIf { it.isNotEmpty() }
                    )
                }
                is Map<*, *> -> if (resource["name"] == "subtitles") {
                    val types = stringList(resource["types"]).ifEmpty { manifestTypes }
                    val prefixes = if (resource.containsKey("idPrefixes")) {
                        stringList(resource["idPrefixes"]).takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                    return StremioSubtitleResourceSpec(
                        types = types.takeIf { it.isNotEmpty() },
                        idPrefixes = prefixes
                    )
                }
            }
        }
        return null
    }

    fun resolveRequestType(declaredTypesCsv: String?, contentType: String): String? {
        val declared = declaredTypesCsv?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty()
        val wanted = contentType.lowercase()
        if (declared.isEmpty() || "*" in declared) return wanted
        if (wanted in declared) return wanted
        val aliases = if (wanted == "movie") MOVIE_TYPES else SERIES_TYPES
        return aliases.firstOrNull { it in declared }
    }

    fun acceptsPrefix(prefixes: List<String>?, id: String): Boolean =
        prefixes.isNullOrEmpty() || prefixes.any { prefix -> prefix.isNotBlank() && id.startsWith(prefix, ignoreCase = true) }

    /**
     * Route each addon only to identities it declares. Unrestricted addons get the highest
     * confidence candidate only; they are never sprayed with unrelated numeric/custom IDs.
     */
    fun buildTasks(
        routes: List<StremioSubtitleRoute>,
        contentType: String,
        candidates: List<SubtitleQueryId>
    ): List<StremioSubtitleTask> {
        val result = LinkedHashSet<StremioSubtitleTask>()
        routes.forEach { route ->
            val type = resolveRequestType(route.types, contentType) ?: return@forEach
            // No prefix filter means the manifest permits every ID namespace; querying each
            // verified identity improves recall. A restricted addon receives only matching IDs.
            val matchingIds = if (route.idPrefixes.isNullOrEmpty()) {
                candidates.filter { it.safeForUnrestrictedAddon }
            } else {
                candidates.filter { acceptsPrefix(route.idPrefixes, it.id) }
            }
            matchingIds.forEach { candidate ->
                val requestType = resolveRequestType(route.types, candidate.type) ?: type
                result += StremioSubtitleTask(route.name, route.manifestUrl, requestType, candidate.id)
            }
        }
        return result.toList()
    }

    private fun stringList(value: Any?): List<String> =
        (value as? List<*>)?.filterIsInstance<String>()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}

/**
 * Build only documented/common identity namespaces from resolved IDs. Namespace spelling for
 * custom prefixes is supplied by the addon's manifest, never guessed globally.
 */
object SubtitleIdentityCandidates {
    fun build(
        movie: Boolean,
        season: Int,
        episode: Int,
        imdbId: String?,
        kitsuId: Int?,
        aniListId: Int?,
        malId: Int?,
        tmdbId: Int?,
        tvdbId: Int? = null,
        bangumiId: Int? = null,
        simklId: Int? = null
    ): List<SubtitleQueryId> {
        val safeSeason = season.coerceAtLeast(1)
        val safeEpisode = episode.coerceAtLeast(1)
        val type = if (movie) "movie" else "series"
        val result = LinkedHashMap<String, SubtitleQueryId>()
        fun add(id: String?, safeForUnrestrictedAddon: Boolean = true) {
            if (id.isNullOrBlank()) return
            result.putIfAbsent(id, SubtitleQueryId(type, id, safeForUnrestrictedAddon))
        }
        fun namespaced(
            prefix: String,
            value: Int?,
            episodeOnly: Boolean = false,
            safeForUnrestrictedAddon: Boolean = false
        ) {
            val numericId = value?.takeIf { it > 0 } ?: return
            val base = "$prefix:$numericId"
            add(
                when {
                    movie -> base
                    episodeOnly -> "$base:$safeEpisode"
                    else -> "$base:$safeSeason:$safeEpisode"
                },
                safeForUnrestrictedAddon
            )
        }

        if (!imdbId.isNullOrBlank()) {
            val cleanImdb = imdbId.trim()
            add(if (movie) cleanImdb else "$cleanImdb:$safeSeason:$safeEpisode")
        }
        namespaced("tmdb", tmdbId, safeForUnrestrictedAddon = true)
        namespaced("kitsu", kitsuId, episodeOnly = true, safeForUnrestrictedAddon = true)
        namespaced("anilist", aniListId, episodeOnly = true)
        namespaced("mal", malId, episodeOnly = true)
        namespaced("myanimelist", malId, episodeOnly = true)
        namespaced("tvdb", tvdbId)
        namespaced("bgm", bangumiId, episodeOnly = true)
        namespaced("simkl", simklId)
        return result.values.toList()
    }
}
