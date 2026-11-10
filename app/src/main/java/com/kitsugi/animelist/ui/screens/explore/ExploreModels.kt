package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult

// ALL bir görünüm modudur; API isteklerinde yalnızca sources kullanılır.
enum class ExplorePlatform(
    val label: String,
    val emoji: String = "⚡",
    val shortName: String = label,
    val description: String = ""
) {
    ALL("Tümü", "🌐", "Tümü", "7 kaynağı birlikte keşfet: anime, manga, film ve diziler"),
    AniList("AniList", "⚡", "AniList", "Trend, popüler ve güncel sezon anime & mangaları"),
    MAL("MyAnimeList", "🏆", "MAL", "En yüksek puanlı, yaklaşan ve klasik MyAnimeList arşivi"),
    TMDB("TMDB", "🎬", "TMDB", "Trend filmler, popüler diziler ve vizyondaki yapımlar"),
    SIMKL("Simkl", "📺", "Simkl", "Simkl en iyiler, TV dizileri ve anime listeleri"),
    KITSU("Kitsu", "🦊", "Kitsu", "Kitsu popüler, trend ve en sevilen içerikleri"),
    SHIKIMORI("Shikimori", "🌸", "Shikimori", "Shikimori güncel anime ve manga sıralamaları"),
    BANGUMI("Bangumi", "🎌", "Bangumi", "Bangumi (bgm.tv) anime, manga, canlı dizi ve film keşfi");

    companion object {
        val sources: List<ExplorePlatform> = entries.filter { it != ALL }
    }
}

data class ExplorePayload(
    val topAnime: List<JikanSearchResult>,
    val airingAnime: List<JikanSearchResult>,
    val upcomingAnime: List<JikanSearchResult>,
    val topManga: List<JikanSearchResult>,
    val publishingManga: List<JikanSearchResult>,
    val trendingAnime: List<JikanSearchResult>,
    val movieAnime: List<JikanSearchResult>,
    val seasonalAnime: List<JikanSearchResult>,
    val simklContinueMovies: List<JikanSearchResult> = emptyList(),
    val simklPlannedMovies: List<JikanSearchResult> = emptyList(),
    val simklContinueSeries: List<JikanSearchResult> = emptyList(),
    val simklPlannedSeries: List<JikanSearchResult> = emptyList(),
    val airingSoonAnime: List<JikanSearchResult> = emptyList(),
    val trendingManga: List<JikanSearchResult> = emptyList(),
    val newlyAddedAnime: List<JikanSearchResult> = emptyList(),
    val newlyAddedManga: List<JikanSearchResult> = emptyList(),
    /** TMDB'ye özgü upcoming medya listesi — trendingManga'dan bağımsız */
    val upcomingMediaTmdb: List<JikanSearchResult> = emptyList(),
    // Nullable for older Gson disk caches which don't contain these fields.
    val topRatedAnime: List<JikanSearchResult>? = null,
    val topRatedManga: List<JikanSearchResult>? = null,
    /** Bangumi REAL categories: live-action TV/drama and live-action films. */
    val bangumiTvShows: List<JikanSearchResult>? = null,
    val bangumiMovies: List<JikanSearchResult>? = null,
    /**
     * Kaynağa özgü tür rafları:
     * Manhwa/Manhua ve Light Novel/romanlar. Bir kaynak desteklemiyorsa boş kalır ve
     * keşfet kategorisi o kaynak için gösterilmez.
     */
    val manhwaManhua: List<JikanSearchResult>? = null,
    val novels: List<JikanSearchResult>? = null
)

/** Keşfet kısayollarındaki görsel gruplama; kategori ve veri rafı aynı modelden üretilir. */
enum class ExploreCategoryGroup {
    ANIME,
    MANGA,
    TV,
    MOVIES,
    MIXED,
    OTHER
}

fun ExploreCategoryType.defaultExploreGroup(): ExploreCategoryGroup = when (this) {
    ExploreCategoryType.TOP_ANIME,
    ExploreCategoryType.TOP_RATED_ANIME,
    ExploreCategoryType.TRENDING_ANIME,
    ExploreCategoryType.AIRING_ANIME,
    ExploreCategoryType.UPCOMING_ANIME,
    ExploreCategoryType.MOVIE_ANIME,
    ExploreCategoryType.SEASONAL_ANIME,
    ExploreCategoryType.NEWLY_ADDED_ANIME -> ExploreCategoryGroup.ANIME

    ExploreCategoryType.TOP_MANGA,
    ExploreCategoryType.TOP_RATED_MANGA,
    ExploreCategoryType.PUBLISHING_MANGA,
    ExploreCategoryType.TRENDING_MANGA,
    ExploreCategoryType.NEWLY_ADDED_MANGA,
    ExploreCategoryType.MANHWA_MANHUA,
    ExploreCategoryType.NOVELS -> ExploreCategoryGroup.MANGA

    ExploreCategoryType.BANGUMI_TV -> ExploreCategoryGroup.TV
    ExploreCategoryType.BANGUMI_MOVIES -> ExploreCategoryGroup.MOVIES
    ExploreCategoryType.UPCOMING_MEDIA_TMDB -> ExploreCategoryGroup.MIXED
}
