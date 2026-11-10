package com.kitsugi.animelist.ui.screens.explore

enum class ExploreCategoryType {
    TOP_ANIME,
    TOP_RATED_ANIME,
    TOP_RATED_MANGA,
    TRENDING_ANIME,
    AIRING_ANIME,
    UPCOMING_ANIME,
    MOVIE_ANIME,
    SEASONAL_ANIME,
    TOP_MANGA,
    PUBLISHING_MANGA,
    TRENDING_MANGA,
    NEWLY_ADDED_ANIME,
    NEWLY_ADDED_MANGA,
    /** Bangumi REAL (type=6) live-action shelves. */
    BANGUMI_TV,
    BANGUMI_MOVIES,
    /**
     * Kaynağın yerel "ek tür" rafları (Bangumi REAL raflarıyla aynı fikir):
     * MANHWA_MANHUA = Kore/Çin çizgi romanları, NOVELS = light novel & romanlar.
     * Raflar yalnızca karşılıkları olan kaynaklarda doldurulur: MAL/Shikimori/AniList/Kitsu
     * manhwa & novel; Bangumi ise BOOK_NOVEL roman rafı sunar.
     */
    MANHWA_MANHUA,
    NOVELS,
    /** TMDB'ye özgü "Yakında Yayında" içerikleri — getUpcomingMedia() ile sayfalanır */
    UPCOMING_MEDIA_TMDB
}
