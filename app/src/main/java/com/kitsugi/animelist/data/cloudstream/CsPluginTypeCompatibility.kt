package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.model.MediaType
import java.util.Locale

/** Runtime CloudStream provider type checks used before a title search is dispatched. */
internal object CsPluginTypeCompatibility {
    fun infer(
        mediaType: MediaType?,
        isMovie: Boolean,
        supportedTypes: Iterable<String>
    ): MediaType? {
        if (mediaType != null) return mediaType
        if (isMovie) return MediaType.Movie

        val declared = supportedTypes
            .map { it.substringAfterLast('.').trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()
        if (declared.isEmpty()) return null

        val hasTvSeries = declared.any { it in setOf("tvseries", "tv series", "asiandrama", "documentary") }
        val hasAnime = declared.any { it in setOf("anime", "ova", "cartoon", "animemovie", "anime movie") }
        val hasMovie = declared.any { it == "movie" || it == "animemovie" || it == "anime movie" }

        return when {
            hasTvSeries && !hasAnime -> MediaType.TvShow
            hasAnime && !hasTvSeries -> MediaType.Anime
            hasMovie && !hasTvSeries && !hasAnime -> MediaType.Movie
            else -> null
        }
    }

    /**
     * Preserve the caller's explicit movie bit even when the catalog category is Anime. Anime
     * movies commonly share `MediaType.Anime` but still need the movie search/load pipeline.
     */
    fun resolveIsMovie(mediaType: MediaType?, isMovie: Boolean?): Boolean =
        isMovie == true || mediaType == MediaType.Movie

    fun supports(mediaType: MediaType?, supportedTypes: Iterable<String>): Boolean {
        if (mediaType == null) return true

        val declared = supportedTypes
            .map { it.substringAfterLast('.').trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()

        // Some older/community extensions omit their type declarations. Keep them eligible
        // rather than silently hiding a provider whose runtime metadata is incomplete.
        if (declared.isEmpty() || "nsfw" in declared || "adult" in declared) return true

        // Catalog types describe the catalog's classification, not the content a
        // provider can actually find. TMDB/Simkl may call the very same anime a
        // TvShow or Movie, and community providers frequently declare only one
        // video type despite carrying other types. Do not reject them before search;
        // title, season and movie/episode checks still apply to the results.
        val expected = when (mediaType) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> setOf(
                "anime", "anime movie", "animemovie", "ova", "cartoon",
                "tvseries", "tv series", "asiandrama", "documentary", "movie"
            )
            MediaType.Manga -> setOf("manga", "comic")
        }
        return declared.any { it in expected }
    }

    fun description(mediaType: MediaType): String = when (mediaType) {
        MediaType.Anime -> "anime"
        MediaType.Manga -> "manga"
        MediaType.Movie -> "film"
        MediaType.TvShow -> "dizi"
    }
}
