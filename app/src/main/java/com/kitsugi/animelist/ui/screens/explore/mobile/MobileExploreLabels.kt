package com.kitsugi.animelist.ui.screens.explore.mobile

import com.kitsugi.animelist.R

/** Resource mapping is display-only: routing and stable shelf keys never use translated text. */
internal fun mobileExploreCatalogTitleRes(title: String): Int? = when (title) {
    "Trend Her Şey" -> R.string.explore_source_trending_all
    "Trend Animeler" -> R.string.explore_source_trending_anime
    "Trend Mangalar" -> R.string.explore_source_trending_manga
    "Trend Diziler" -> R.string.explore_source_trending_tv
    "Trend Filmler" -> R.string.explore_source_trending_movies
    "Popüler Animeler" -> R.string.explore_source_popular_anime
    "Popüler Mangalar" -> R.string.explore_source_popular_manga
    "Popüler Diziler" -> R.string.explore_source_popular_tv
    "Popüler Filmler" -> R.string.explore_source_popular_movies
    "En İyi Animeler" -> R.string.explore_source_best_anime
    "En İyi Mangalar" -> R.string.explore_source_best_manga
    "En İyi Diziler" -> R.string.explore_source_best_tv
    "En Yüksek Puanlı Animeler" -> R.string.explore_source_top_rated_anime
    "En Yüksek Puanlı Mangalar" -> R.string.explore_source_top_rated_manga
    "En Yüksek Puanlı Diziler" -> R.string.explore_source_top_rated_tv
    "En Yüksek Puanlı Filmler" -> R.string.explore_source_top_rated_movies
    "Yayındaki Animeler" -> R.string.explore_source_airing_anime
    "Yayındaki Mangalar" -> R.string.explore_publishing_manga
    "Yayındaki Diziler" -> R.string.explore_source_airing_tv
    "Yaklaşan Animeler" -> R.string.explore_source_upcoming_anime
    "Yaklaşan Film ve Diziler" -> R.string.explore_source_upcoming_tv_movies
    "Anime Filmleri" -> R.string.explore_source_anime_movies
    "Bu Sezon" -> R.string.explore_source_current_season
    "Yeni Eklenen Animeler" -> R.string.explore_source_newly_added_anime
    "Yeni Eklenen Mangalar" -> R.string.explore_source_newly_added_manga
    "Yeni Filmler" -> R.string.explore_source_new_movies
    "Manhwa & Manhua" -> R.string.explore_source_manhwa_manhua
    "Noveller & Light Novel" -> R.string.explore_source_novels
    "Bangumi Dizileri" -> R.string.explore_source_bangumi_tv
    "Bangumi Filmleri" -> R.string.explore_source_bangumi_movies
    else -> null
}

/** Returns null for malformed personal shelves instead of guessing another media type. */
internal fun mobileExploreShelfTitleRes(shelf: MobileExploreShelfData): Int? = when (shelf.origin) {
    MobileShelfOrigin.CATALOG -> mobileExploreCatalogTitleRes(shelf.originalTitle)
    MobileShelfOrigin.SIMKL_CONTINUE -> when (shelf.tab) {
        MobileExploreTab.SERIES -> R.string.explore_simkl_continue_watching_series
        MobileExploreTab.MOVIES -> R.string.explore_simkl_continue_watching_movies
        else -> null
    }
    MobileShelfOrigin.SIMKL_PLANNED -> when (shelf.tab) {
        MobileExploreTab.SERIES -> R.string.explore_simkl_plantowatch_series
        MobileExploreTab.MOVIES -> R.string.explore_simkl_plantowatch_movies
        else -> null
    }
}
