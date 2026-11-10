package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.AniListSearchClient
import com.kitsugi.animelist.model.MediaType

/** One compact, visible-first request. Remaining AniList shelves are fetched as they enter view. */
internal val ANILIST_EXPLORE_SHELF_CATEGORIES = listOf(
    ExploreCategoryType.TOP_ANIME,
    ExploreCategoryType.TRENDING_ANIME,
    ExploreCategoryType.TOP_RATED_ANIME,
    ExploreCategoryType.AIRING_ANIME,
    ExploreCategoryType.UPCOMING_ANIME,
    ExploreCategoryType.MOVIE_ANIME,
    ExploreCategoryType.SEASONAL_ANIME,
    ExploreCategoryType.NEWLY_ADDED_ANIME,
    ExploreCategoryType.TOP_MANGA,
    ExploreCategoryType.TRENDING_MANGA,
    ExploreCategoryType.TOP_RATED_MANGA,
    ExploreCategoryType.PUBLISHING_MANGA,
    ExploreCategoryType.NEWLY_ADDED_MANGA,
    ExploreCategoryType.MANHWA_MANHUA,
    ExploreCategoryType.NOVELS
)

internal val ANILIST_INITIAL_EXPLORE_CATEGORIES = listOf(
    ExploreCategoryType.TOP_ANIME,
    ExploreCategoryType.TRENDING_ANIME,
    ExploreCategoryType.TOP_RATED_ANIME,
    ExploreCategoryType.AIRING_ANIME,
    ExploreCategoryType.UPCOMING_ANIME,
    ExploreCategoryType.MOVIE_ANIME
)

internal const val ANILIST_SHELF_BATCH_DEBOUNCE_MS = 180L

/** An AniList GraphQL alias and the shared Kitsugi category it fills. */
internal data class AniListExploreShelfRequest(
    val category: ExploreCategoryType,
    val spec: AniListSearchClient.ExploreShelfSpec
)

/**
 * Converts visible categories into AniList alias requests. Manhwa/Manhua uses AniList's
 * countryOfOrigin filter for both Korea and China; it is one UI shelf backed by two aliases.
 */
internal fun aniListExploreShelfRequests(
    categories: Collection<ExploreCategoryType>,
    season: String,
    seasonYear: Int
): List<AniListExploreShelfRequest> = buildList {
    categories.distinct().forEach { category ->
        fun addShelf(
            alias: String,
            mediaType: MediaType,
            sort: List<String>,
            status: String? = null,
            format: String? = null,
            shelfSeason: String? = null,
            shelfYear: Int? = null,
            countryOfOrigin: String? = null
        ) {
            this@buildList.add(
                AniListExploreShelfRequest(
                    category = category,
                    spec = AniListSearchClient.ExploreShelfSpec(
                        alias = alias,
                        mediaType = mediaType,
                        sort = sort,
                        status = status,
                        format = format,
                        season = shelfSeason,
                        seasonYear = shelfYear,
                        countryOfOrigin = countryOfOrigin
                    )
                )
            )
        }

        when (category) {
            ExploreCategoryType.TOP_ANIME -> addShelf("topAnime", MediaType.Anime, listOf("POPULARITY_DESC"))
            ExploreCategoryType.TRENDING_ANIME -> addShelf("trendingAnime", MediaType.Anime, listOf("TRENDING_DESC"))
            ExploreCategoryType.TOP_RATED_ANIME -> addShelf("topRatedAnime", MediaType.Anime, listOf("SCORE_DESC"))
            ExploreCategoryType.AIRING_ANIME -> addShelf("airingAnime", MediaType.Anime, listOf("POPULARITY_DESC"), status = "RELEASING")
            ExploreCategoryType.UPCOMING_ANIME -> addShelf("upcomingAnime", MediaType.Anime, listOf("POPULARITY_DESC"), status = "NOT_YET_RELEASED")
            ExploreCategoryType.MOVIE_ANIME -> addShelf("movieAnime", MediaType.Anime, listOf("POPULARITY_DESC"), format = "MOVIE")
            ExploreCategoryType.SEASONAL_ANIME -> addShelf(
                "seasonalAnime", MediaType.Anime, listOf("POPULARITY_DESC"),
                shelfSeason = season, shelfYear = seasonYear
            )
            ExploreCategoryType.NEWLY_ADDED_ANIME -> addShelf("newlyAddedAnime", MediaType.Anime, listOf("ID_DESC"))
            ExploreCategoryType.TOP_MANGA -> addShelf("topManga", MediaType.Manga, listOf("POPULARITY_DESC"))
            ExploreCategoryType.TRENDING_MANGA -> addShelf("trendingManga", MediaType.Manga, listOf("TRENDING_DESC", "POPULARITY_DESC"))
            ExploreCategoryType.TOP_RATED_MANGA -> addShelf("topRatedManga", MediaType.Manga, listOf("SCORE_DESC"))
            ExploreCategoryType.PUBLISHING_MANGA -> addShelf("publishingManga", MediaType.Manga, listOf("POPULARITY_DESC"), status = "RELEASING")
            ExploreCategoryType.NEWLY_ADDED_MANGA -> addShelf("newlyAddedManga", MediaType.Manga, listOf("ID_DESC"))
            ExploreCategoryType.MANHWA_MANHUA -> {
                addShelf("manhwaKorea", MediaType.Manga, listOf("POPULARITY_DESC"), countryOfOrigin = "KR")
                addShelf("manhuaChina", MediaType.Manga, listOf("POPULARITY_DESC"), countryOfOrigin = "CN")
            }
            ExploreCategoryType.NOVELS -> addShelf("novels", MediaType.Manga, listOf("POPULARITY_DESC"), format = "NOVEL")
            ExploreCategoryType.BANGUMI_TV,
            ExploreCategoryType.BANGUMI_MOVIES,
            ExploreCategoryType.UPCOMING_MEDIA_TMDB -> Unit
        }
    }
}
