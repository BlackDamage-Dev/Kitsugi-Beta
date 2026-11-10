package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import com.kitsugi.animelist.data.remote.JikanSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** A source can fail without replacing the selected mode or hiding other sources. */
data class ExploreSourceState(
    val payload: ExplorePayload? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCached: Boolean = false,
    val errorCode: PlatformErrorCode? = null
)

internal suspend fun loadExploreSource(
    cached: ExplorePayload?,
    forceRefresh: Boolean,
    fetch: suspend () -> ExplorePayload,
    readOffline: suspend () -> ExplorePayload?,
    timeoutMillis: Long = 60_000L,
    context: android.content.Context? = null,
    platformLabel: String? = null
): ExploreSourceState {
    val usableCache = cached?.takeIf { it.hasCatalogContent() }
    if (!forceRefresh && usableCache != null) return ExploreSourceState(payload = usableCache)
    var fetchError: Throwable? = null
    try {
        val payload = withTimeoutOrNull(timeoutMillis) { fetch() }
        currentCoroutineContext().ensureActive()
        if (payload != null && payload.hasCatalogContent()) return ExploreSourceState(payload = payload)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Fall back per source, not by selecting a different platform.
        fetchError = e
    }
    currentCoroutineContext().ensureActive()
    val offline = usableCache ?: try {
        readOffline()?.takeIf { it.hasCatalogContent() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    val failureCode = PlatformFailureRegistry.reportAndResolve(platformLabel, fetchError)
    return ExploreSourceState(
        payload = offline,
        errorCode = failureCode,
        error = if (offline != null) {
            (context ?: com.kitsugi.animelist.KitsugiApplication.getInstance())
                ?.getString(R.string.explore_source_refresh_failed_cached)
                ?: "Could not refresh; showing the last saved content."
        } else {
            (context ?: com.kitsugi.animelist.KitsugiApplication.getInstance())
                ?.getString(R.string.explore_source_unavailable)
                ?: "Could not load content. The source is empty or temporarily unavailable."
        },
        isCached = offline != null
    )
}

internal fun ExplorePayload.hasCatalogContent(): Boolean = listOf(
    topAnime, airingAnime, upcomingAnime, topManga, publishingManga, trendingAnime,
    movieAnime, seasonalAnime, trendingManga, newlyAddedAnime,
    newlyAddedManga, upcomingMediaTmdb, topRatedAnime.orEmpty(), topRatedManga.orEmpty(),
    bangumiTvShows.orEmpty(), bangumiMovies.orEmpty(),
    manhwaManhua.orEmpty(), novels.orEmpty()
).any { it.isNotEmpty() }

/** Keep source/type/ID together: TMDB film and TV IDs can overlap, and same malId can appear with different tmdbId. */
internal fun JikanSearchResult.exploreIdentity(): String =
    "${source.lowercase()}:${type.name.lowercase()}:${malId}:${tmdbId ?: 0}:${cs3Url?.hashCode() ?: 0}"

data class ExploreSourceSection(
    val platform: ExplorePlatform,
    val category: ExploreCategoryType,
    val title: String,
    val results: List<JikanSearchResult>,
    val group: ExploreCategoryGroup = category.defaultExploreGroup()
) {
    val key: String get() = "${platform.name}_${category.name}"
}

internal const val SIMKL_PERSONAL_CONTINUE_SERIES_TITLE = "İzlemeye Devam Et (Dizi & Anime)"
internal const val SIMKL_PERSONAL_CONTINUE_MOVIES_TITLE = "İzlemeye Devam Et (Film)"
internal const val SIMKL_PERSONAL_PLANNED_SERIES_TITLE = "Planladıklarım (Dizi & Anime)"
internal const val SIMKL_PERSONAL_PLANNED_MOVIES_TITLE = "Planladıklarım (Film)"
internal const val SIMKL_PERSONAL_KEY_PREFIX = "simkl_personal_"

/**
 * Payload fields are overloaded by legacy providers (topManga can be TV shows!).
 * Keep each source's categories separate rather than merging incompatible fields.
 */
internal fun sourceSections(platform: ExplorePlatform, p: ExplorePayload): List<ExploreSourceSection> {
    require(platform != ExplorePlatform.ALL)
    fun section(
        category: ExploreCategoryType,
        title: String,
        items: List<JikanSearchResult>,
        group: ExploreCategoryGroup = category.defaultExploreGroup()
    ) = ExploreSourceSection(
        platform, category, title, items.distinctBy { it.exploreIdentity() }, group
    )

    // Kaynak sayfası ve Tümü sayfası aynı sıralı bölüm listesini kullanır. Anime rafları
    // önce kendi içinde, manga rafları sonra; kaynak-özel raflar en sonda yer alır.
    if (platform == ExplorePlatform.TMDB) {
        return listOf(
            // Film/dizi kaynağını Anime/Manga adı altında yanlış gruplayıp filtrelememek için
            // TMDB'nin kaynak-özel rafları kendi medya ailesini bildirir.
            section(ExploreCategoryType.TOP_ANIME, "Trend Her Şey", p.topAnime, ExploreCategoryGroup.MIXED),
            section(ExploreCategoryType.TRENDING_ANIME, "Trend Animeler", p.trendingAnime, ExploreCategoryGroup.ANIME),
            section(ExploreCategoryType.AIRING_ANIME, "Trend Diziler", p.airingAnime, ExploreCategoryGroup.TV),
            section(ExploreCategoryType.MOVIE_ANIME, "Trend Filmler", p.movieAnime, ExploreCategoryGroup.MOVIES),
            section(ExploreCategoryType.NEWLY_ADDED_ANIME, "Popüler Animeler", p.newlyAddedAnime, ExploreCategoryGroup.ANIME),
            section(ExploreCategoryType.TOP_MANGA, "Popüler Diziler", p.topManga, ExploreCategoryGroup.TV),
            section(ExploreCategoryType.UPCOMING_ANIME, "Popüler Filmler", p.upcomingAnime, ExploreCategoryGroup.MOVIES),
            section(ExploreCategoryType.TRENDING_MANGA, "En Yüksek Puanlı Animeler", p.trendingManga, ExploreCategoryGroup.ANIME),
            section(ExploreCategoryType.SEASONAL_ANIME, "En Yüksek Puanlı Diziler", p.seasonalAnime, ExploreCategoryGroup.TV),
            section(ExploreCategoryType.PUBLISHING_MANGA, "En Yüksek Puanlı Filmler", p.publishingManga, ExploreCategoryGroup.MOVIES),
            section(ExploreCategoryType.UPCOMING_MEDIA_TMDB, "Yaklaşan Film ve Diziler", p.upcomingMediaTmdb, ExploreCategoryGroup.MIXED)
        )
    }
    if (platform == ExplorePlatform.SIMKL) {
        return listOf(
            // Simkl'in video kategorileri de aynı keşfet listesiyle çizilir; sadece medya grubu ayrıdır.
            section(ExploreCategoryType.TOP_ANIME, "Trend Diziler", p.topAnime, ExploreCategoryGroup.TV),
            section(ExploreCategoryType.TRENDING_ANIME, "Trend Animeler", p.trendingAnime, ExploreCategoryGroup.ANIME),
            section(ExploreCategoryType.MOVIE_ANIME, "Trend Filmler", p.movieAnime, ExploreCategoryGroup.MOVIES),
            section(ExploreCategoryType.AIRING_ANIME, "Yayındaki Diziler", p.airingAnime, ExploreCategoryGroup.TV),
            section(ExploreCategoryType.TRENDING_MANGA, "Yayındaki Animeler", p.trendingManga, ExploreCategoryGroup.ANIME),
            section(ExploreCategoryType.NEWLY_ADDED_ANIME, "Popüler Animeler", p.newlyAddedAnime, ExploreCategoryGroup.ANIME),
            section(ExploreCategoryType.TOP_MANGA, "En İyi Diziler", p.topManga, ExploreCategoryGroup.TV),
            section(ExploreCategoryType.UPCOMING_ANIME, "Yeni Filmler", p.upcomingAnime, ExploreCategoryGroup.MOVIES)
        )
    }

    val topAnimeTitle = if (
        platform == ExplorePlatform.MAL || platform == ExplorePlatform.SHIKIMORI || platform == ExplorePlatform.BANGUMI
    ) "En İyi Animeler" else "Popüler Animeler"
    val topMangaTitle = if (
        platform == ExplorePlatform.MAL || platform == ExplorePlatform.SHIKIMORI || platform == ExplorePlatform.BANGUMI
    ) "En İyi Mangalar" else "Popüler Mangalar"

    return listOf(
        // Anime: popüler, trend/puan, yayın durumu ve sezon/yeni eklenen rafları.
        section(ExploreCategoryType.TOP_ANIME, topAnimeTitle, p.topAnime),
        section(ExploreCategoryType.TRENDING_ANIME, "Trend Animeler", p.trendingAnime),
        section(ExploreCategoryType.TOP_RATED_ANIME, "En Yüksek Puanlı Animeler", p.topRatedAnime.orEmpty()),
        section(ExploreCategoryType.AIRING_ANIME, "Yayındaki Animeler", p.airingAnime),
        section(ExploreCategoryType.UPCOMING_ANIME, "Yaklaşan Animeler", p.upcomingAnime),
        section(ExploreCategoryType.MOVIE_ANIME, "Anime Filmleri", p.movieAnime),
        section(ExploreCategoryType.SEASONAL_ANIME, "Bu Sezon", p.seasonalAnime),
        section(ExploreCategoryType.NEWLY_ADDED_ANIME, "Yeni Eklenen Animeler", p.newlyAddedAnime),
        // Manga: popüler, trend/puan, yayındaki ve yeni eklenen raflar.
        section(ExploreCategoryType.TOP_MANGA, topMangaTitle, p.topManga),
        section(ExploreCategoryType.TRENDING_MANGA, "Trend Mangalar", p.trendingManga),
        section(ExploreCategoryType.TOP_RATED_MANGA, "En Yüksek Puanlı Mangalar", p.topRatedManga.orEmpty()),
        section(ExploreCategoryType.PUBLISHING_MANGA, "Yayındaki Mangalar", p.publishingManga),
        section(ExploreCategoryType.NEWLY_ADDED_MANGA, "Yeni Eklenen Mangalar", p.newlyAddedManga),
        // Kaynağa özgü türler, genel anime/manga raflarını bölmeden en sona alınır.
        section(ExploreCategoryType.MANHWA_MANHUA, "Manhwa & Manhua", p.manhwaManhua.orEmpty()),
        section(ExploreCategoryType.NOVELS, "Noveller & Light Novel", p.novels.orEmpty())
    ) + if (platform == ExplorePlatform.BANGUMI) listOf(
        section(ExploreCategoryType.BANGUMI_TV, "Bangumi Dizileri", p.bangumiTvShows.orEmpty()),
        section(ExploreCategoryType.BANGUMI_MOVIES, "Bangumi Filmleri", p.bangumiMovies.orEmpty())
    ) else emptyList()
}

/** Keep every category under its own source, in a stable source order. */
fun allSourceSections(
    states: Map<ExplorePlatform, ExploreSourceState>,
    showAdultContent: Boolean
): List<ExploreSourceSection> = ExplorePlatform.sources.flatMap { platform ->
    states[platform]?.payload?.let { payload ->
        val filtered = sourceSections(platform, payload.forSource(platform)).map { section ->
            section.copy(results = section.results.filter { showAdultContent || !it.isAdult })
        }
        val keepAniListPlaceholders = platform == ExplorePlatform.AniList && filtered.any { it.results.isNotEmpty() }
        // AniList keeps its complete supported shelf model visible only when actual AniList
        // catalog data exists. Empty shelves render lazily; legacy Kitsu fallback rows are not
        // mislabeled as AniList categories. Other providers hide empty shelves after loading.
        filtered.filter { it.results.isNotEmpty() || keepAniListPlaceholders }
    }.orEmpty()
}

/** Mirrors the lazy DSL: intro (+ sticky navigation) + each header/rows/footer. */
internal fun allSourceHeaderIndices(
    sections: Map<ExplorePlatform, List<ExploreSourceSection>>,
    collapsed: Set<ExplorePlatform>,
    startIndex: Int,
    hasJumpBar: Boolean = true,
    extraItemsAfterIntro: Int = 0
): Map<ExplorePlatform, Int> {
    // intro her zaman var; yapışkan kaynak çubuğu yalnızca gösteriliyorsa eklenir;
    // ortak "Yakında Yayında" şeridi gibi intro sonrası ek öğeler de sayılır.
    var index = startIndex + if (hasJumpBar) 2 else 1
    index += extraItemsAfterIntro
    return ExplorePlatform.sources.associateWith { platform ->
        val header = index++
        if (platform !in collapsed) index += sections[platform].orEmpty().size + 1
        header
    }
}

/** Legacy single-source fallback data must never be labelled as another provider in ALL. */
internal fun ExplorePayload.forSource(platform: ExplorePlatform): ExplorePayload {
    fun List<JikanSearchResult>.owned() = filter {
        when (platform) {
            ExplorePlatform.MAL -> it.source.equals("mal", true) || it.source.equals("jikan", true)
            else -> it.source.equals(platform.name, true)
        }
    }
    return copy(
        topAnime = topAnime.owned(), airingAnime = airingAnime.owned(),
        upcomingAnime = upcomingAnime.owned(), topManga = topManga.owned(),
        publishingManga = publishingManga.owned(), trendingAnime = trendingAnime.owned(),
        movieAnime = movieAnime.owned(), seasonalAnime = seasonalAnime.owned(),
        trendingManga = trendingManga.owned(), newlyAddedAnime = newlyAddedAnime.owned(),
        newlyAddedManga = newlyAddedManga.owned(), upcomingMediaTmdb = upcomingMediaTmdb.owned(),
        // airingSoonAnime FİLTRELENMEZ: "Yakında Yayında" şeridi tüm kaynaklarda aynı ortak
        // takvim verisini gerçek kimlikleriyle (anilist/mal/tmdb) taşır — kaynağa özel sözde
        // kimlik bulunmadığından owned() filtresi bu alanı boşaltmak yerine olduğu gibi korur.
        // Tümü modunda bu alan kaynak şeridi olarak RENDER EDİLMEZ (bak. allSourcesExploreSections).
        airingSoonAnime = airingSoonAnime,
        topRatedAnime = topRatedAnime.orEmpty().owned(), topRatedManga = topRatedManga.orEmpty().owned(),
        bangumiTvShows = bangumiTvShows.orEmpty().owned(), bangumiMovies = bangumiMovies.orEmpty().owned(),
        manhwaManhua = manhwaManhua.orEmpty().owned(), novels = novels.orEmpty().owned()
    )
}

/**
 * ALL: one random, unique, horizontal-artwork item per available source/shelf.
 * Missing shelves are skipped; manga/book items are rejected by media type (not by
 * legacy *_MANGA field names, which TMDB/SIMKL also use for video shelves).
 */
fun allSourceHeroes(
    sections: List<ExploreSourceSection>,
    airingSoon: List<JikanSearchResult> = emptyList(),
    random: kotlin.random.Random = kotlin.random.Random.Default
): List<JikanSearchResult> = selectHeroItems(
    sections = sections,
    itemsPerSection = HERO_PER_SECTION_ALL,
    airingSoon = airingSoon,
    random = random
)

/** Test/compatibility shortcut; the UI prepares artwork asynchronously before admission. */
fun allSourceHeroes(
    states: Map<ExplorePlatform, ExploreSourceState>,
    showAdultContent: Boolean
): List<JikanSearchResult> = allSourceHeroes(allSourceSections(states, showAdultContent))
