package com.kitsugi.animelist.core.player

/** Build only identifiers that refer to the current work and episode. */
object SubtitleQueryPlanner {
    data class Query(val type: String, val id: String)

    fun build(isMovie: Boolean, imdbId: String?, kitsuId: Int?, title: String?, season: Int, episode: Int, tmdbId: Int? = null): List<Query> {
        val type = if (isMovie) "movie" else "series"
        val ids = mutableListOf<String>()
        imdbId?.trim()?.takeIf { it.matches(Regex("tt[0-9]+")) }?.let {
            ids += if (isMovie) it else "$it:${season.coerceAtLeast(0)}:$episode"
        }
        tmdbId?.takeIf { it > 0 }?.let {
            ids += if (isMovie) "tmdb:$it" else "tmdb:$it:${season.coerceAtLeast(0)}:$episode"
        }
        kitsuId?.takeIf { it > 0 }?.let {
            ids += if (isMovie) "kitsu:$it" else "kitsu:$it:$episode"
        }
        title?.trim()?.takeIf { it.isNotEmpty() }?.let {
            ids += if (isMovie) it else "$it:${season.coerceAtLeast(0)}:$episode"
        }
        return ids.distinct().map { Query(type, it) }
    }
}
