package com.kitsugi.animelist.ui.screens.explore.mobile

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.ExplorePayload
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.screens.explore.ExploreSourceSection
import com.kitsugi.animelist.ui.screens.explore.exploreIdentity
import com.kitsugi.animelist.ui.screens.explore.sourceSections

/** Page order is part of the v2 visual contract. This is not an API media-type enum. */
enum class MobileExploreTab { SERIES, MOVIES, ANIME, MANGA }

/**
 * Query identity is deliberately independent of localized display text. Catalog continuation
 * remains unverified: callers must not attach a grid/pagination callback until its query is
 * checked against the source's actual first page. Personal destinations are separate.
 */
enum class MobileShelfOrigin { CATALOG, SIMKL_CONTINUE, SIMKL_PLANNED }

data class MobileExploreShelfData(
    val tab: MobileExploreTab,
    val source: ExplorePlatform,
    val originPlatform: ExplorePlatform,
    val category: ExploreCategoryType,
    val origin: MobileShelfOrigin,
    val originalTitle: String,
    val results: List<JikanSearchResult>,
    /** Mixed catalogs, fallback and combined novel shelves can never reuse the old grid query. */
    val requiresSpecialContinuation: Boolean,
) {
    // Localized titles must never be used as persistent keys or query identifiers.
    val key: String get() = "${originPlatform.name}:${source.name}:${tab.name}:${origin.name}:${category.name}"
}

internal data class MobileExploreRejectedItem(
    val section: ExploreSourceSection,
    val item: JikanSearchResult,
    val reason: MobileExploreRejection,
)

internal enum class MobileExploreRejection { UNKNOWN_OR_UNEXPECTED_SOURCE, MEDIA_TYPE_MISMATCH }

internal data class MobileExploreMappingResult(
    val shelves: List<MobileExploreShelfData>,
    /** Never silently relabel an unexpected or ambiguous item as the selected source. */
    val rejected: List<MobileExploreRejectedItem>,
)

/** A documented catalog capability, not a claim that a request succeeded or returned data. */
internal fun mobileSourceSupportsTab(platform: ExplorePlatform, tab: MobileExploreTab): Boolean = when (platform) {
    ExplorePlatform.ALL -> ExplorePlatform.sources.any { mobileSourceSupportsTab(it, tab) }
    ExplorePlatform.TMDB, ExplorePlatform.SIMKL -> tab != MobileExploreTab.MANGA
    ExplorePlatform.BANGUMI -> true
    ExplorePlatform.AniList, ExplorePlatform.MAL, ExplorePlatform.KITSU, ExplorePlatform.SHIKIMORI ->
        tab == MobileExploreTab.ANIME || tab == MobileExploreTab.MANGA
}

private fun sourceForResult(source: String): ExplorePlatform? = when (source.trim().lowercase()) {
    "anilist" -> ExplorePlatform.AniList
    "mal", "jikan" -> ExplorePlatform.MAL
    "tmdb" -> ExplorePlatform.TMDB
    "simkl" -> ExplorePlatform.SIMKL
    "kitsu" -> ExplorePlatform.KITSU
    "shikimori" -> ExplorePlatform.SHIKIMORI
    "bangumi" -> ExplorePlatform.BANGUMI
    else -> null
}

/** Legacy category names are overloaded per source; do not infer the tab from the name alone. */
private fun sectionTabs(platform: ExplorePlatform, category: ExploreCategoryType): Set<MobileExploreTab> {
    if (platform == ExplorePlatform.TMDB) return when (category) {
        ExploreCategoryType.TOP_ANIME, ExploreCategoryType.UPCOMING_MEDIA_TMDB ->
            setOf(MobileExploreTab.SERIES, MobileExploreTab.MOVIES)
        ExploreCategoryType.AIRING_ANIME, ExploreCategoryType.TOP_MANGA, ExploreCategoryType.SEASONAL_ANIME ->
            setOf(MobileExploreTab.SERIES)
        ExploreCategoryType.MOVIE_ANIME, ExploreCategoryType.UPCOMING_ANIME, ExploreCategoryType.PUBLISHING_MANGA ->
            setOf(MobileExploreTab.MOVIES)
        ExploreCategoryType.TRENDING_ANIME, ExploreCategoryType.NEWLY_ADDED_ANIME, ExploreCategoryType.TRENDING_MANGA ->
            setOf(MobileExploreTab.ANIME)
        else -> emptySet()
    }
    if (platform == ExplorePlatform.SIMKL) return when (category) {
        ExploreCategoryType.TOP_ANIME, ExploreCategoryType.AIRING_ANIME, ExploreCategoryType.TOP_MANGA ->
            setOf(MobileExploreTab.SERIES)
        ExploreCategoryType.MOVIE_ANIME, ExploreCategoryType.UPCOMING_ANIME -> setOf(MobileExploreTab.MOVIES)
        ExploreCategoryType.TRENDING_ANIME, ExploreCategoryType.NEWLY_ADDED_ANIME, ExploreCategoryType.TRENDING_MANGA,
        ExploreCategoryType.UPCOMING_MEDIA_TMDB -> setOf(MobileExploreTab.ANIME)
        else -> emptySet()
    }
    if (platform == ExplorePlatform.ALL) return emptySet()
    return when (category) {
        ExploreCategoryType.TOP_ANIME, ExploreCategoryType.TOP_RATED_ANIME,
        ExploreCategoryType.TRENDING_ANIME, ExploreCategoryType.AIRING_ANIME,
        ExploreCategoryType.UPCOMING_ANIME, ExploreCategoryType.MOVIE_ANIME,
        ExploreCategoryType.SEASONAL_ANIME, ExploreCategoryType.NEWLY_ADDED_ANIME -> setOf(MobileExploreTab.ANIME)
        ExploreCategoryType.TOP_MANGA, ExploreCategoryType.TOP_RATED_MANGA,
        ExploreCategoryType.TRENDING_MANGA, ExploreCategoryType.PUBLISHING_MANGA,
        ExploreCategoryType.NEWLY_ADDED_MANGA, ExploreCategoryType.MANHWA_MANHUA,
        ExploreCategoryType.NOVELS -> setOf(MobileExploreTab.MANGA)
        ExploreCategoryType.BANGUMI_TV -> if (platform == ExplorePlatform.BANGUMI) setOf(MobileExploreTab.SERIES) else emptySet()
        ExploreCategoryType.BANGUMI_MOVIES -> if (platform == ExplorePlatform.BANGUMI) setOf(MobileExploreTab.MOVIES) else emptySet()
        ExploreCategoryType.UPCOMING_MEDIA_TMDB -> emptySet()
    }
}

private fun matchesTab(item: JikanSearchResult, tab: MobileExploreTab, section: ExploreSourceSection): Boolean {
    // Bangumi subject namespace is stronger evidence than a coincidental numeric ID.
    if (section.platform == ExplorePlatform.BANGUMI && item.bangumiSubjectType != null) {
        val expected = when (tab) {
            MobileExploreTab.SERIES, MobileExploreTab.MOVIES -> 6
            MobileExploreTab.ANIME -> 2
            MobileExploreTab.MANGA -> 1
        }
        if (item.bangumiSubjectType != expected) return false
    }
    return when (tab) {
        MobileExploreTab.SERIES -> item.type == MediaType.TvShow
        MobileExploreTab.MOVIES -> item.type == MediaType.Movie
        // TMDB's anime endpoints contain both TV and movie records. Classification follows
        // the endpoint/section, not Movie vs TvShow; regular anime sources can return Movie.
        MobileExploreTab.ANIME -> item.type == MediaType.Anime || item.type == MediaType.Movie ||
            (section.platform == ExplorePlatform.TMDB && item.type == MediaType.TvShow)
        MobileExploreTab.MANGA -> item.type == MediaType.Manga
    }
}

/**
 * Pure adapter for one provider payload. In ALL mode [allowAniListKitsuFallback] must be false;
 * in a selected AniList page it may be true, splitting Kitsu fallback into attributed shelves.
 * This never changes the payload, makes network calls, or silently drops quarantined items.
 */
internal fun mobileExploreCatalogSections(
    platform: ExplorePlatform,
    payload: ExplorePayload,
    showAdultContent: Boolean,
    separateNovelsManga: Boolean,
    allowAniListKitsuFallback: Boolean = false,
): MobileExploreMappingResult {
    require(platform != ExplorePlatform.ALL) { "ALL is a display mode, not a source payload" }
    val effectivePayload = if (separateNovelsManga || platform == ExplorePlatform.TMDB || platform == ExplorePlatform.SIMKL) {
        payload
    } else {
        payload.copy(topManga = payload.topManga + payload.novels.orEmpty(), novels = emptyList())
    }
    val shelves = mutableListOf<MobileExploreShelfData>()
    val rejected = mutableListOf<MobileExploreRejectedItem>()
    val sections = sourceSections(platform, effectivePayload)
    // SIMKL's loader stores anime/best/upcoming in this legacy field, but the shared
    // sourceSections helper only exposes it for TMDB. Preserve the real SIMKL shelf.
    val mobileSections = if (platform == ExplorePlatform.SIMKL && sections.none { it.category == ExploreCategoryType.UPCOMING_MEDIA_TMDB }) {
        sections + ExploreSourceSection(platform, ExploreCategoryType.UPCOMING_MEDIA_TMDB, "Yaklaşan Animeler", effectivePayload.upcomingMediaTmdb)
    } else sections
    for (section in mobileSections) {
        val tabs = sectionTabs(platform, section.category)
        if (tabs.isEmpty()) continue
        val grouped = linkedMapOf<Pair<MobileExploreTab, ExplorePlatform>, MutableList<JikanSearchResult>>()
        for (item in section.results) {
            if (!showAdultContent && item.isAdult) continue
            val actualSource = sourceForResult(item.source)
            val allowedFallback = allowAniListKitsuFallback && platform == ExplorePlatform.AniList && actualSource == ExplorePlatform.KITSU
            if (actualSource == null || (actualSource != platform && !allowedFallback)) {
                rejected += MobileExploreRejectedItem(section, item, MobileExploreRejection.UNKNOWN_OR_UNEXPECTED_SOURCE)
                continue
            }
            val tab = tabs.firstOrNull { matchesTab(item, it, section) }
            if (tab == null) {
                rejected += MobileExploreRejectedItem(section, item, MobileExploreRejection.MEDIA_TYPE_MISMATCH)
                continue
            }
            grouped.getOrPut(tab to actualSource) { mutableListOf() }.add(item)
        }
        for ((identity, items) in grouped) {
            val (tab, actualSource) = identity
            shelves += MobileExploreShelfData(
                tab = tab,
                source = actualSource,
                originPlatform = platform,
                category = section.category,
                origin = MobileShelfOrigin.CATALOG,
                originalTitle = section.title,
                results = items.distinctBy { it.exploreIdentity() },
                requiresSpecialContinuation = tabs.size > 1 || actualSource != platform ||
                    (platform == ExplorePlatform.SIMKL && section.category == ExploreCategoryType.UPCOMING_MEDIA_TMDB) ||
                    (!separateNovelsManga && section.category == ExploreCategoryType.TOP_MANGA && payload.novels.orEmpty().isNotEmpty()),
            )
        }
    }
    return MobileExploreMappingResult(shelves, rejected)
}

/**
 * The Simkl watchlists are populated on both the TMDB and Simkl pages. The provider of
 * the personal data remains Simkl even when the containing page is TMDB; [originPlatform]
 * is retained because the existing grid's legacy behavior depends on that page. In ALL,
 * the caller must choose one authoritative payload rather than add both copies.
 */
internal fun mobileExplorePersonalSections(
    originPlatform: ExplorePlatform,
    payload: ExplorePayload,
    showAdultContent: Boolean,
): MobileExploreMappingResult {
    require(originPlatform == ExplorePlatform.TMDB || originPlatform == ExplorePlatform.SIMKL)
    val shelves = mutableListOf<MobileExploreShelfData>()
    val rejected = mutableListOf<MobileExploreRejectedItem>()
    fun add(
        tab: MobileExploreTab,
        category: ExploreCategoryType,
        origin: MobileShelfOrigin,
        items: List<JikanSearchResult>,
    ) {
        // These are identity markers, not visible titles. The UI localizes from origin + tab.
        val section = ExploreSourceSection(originPlatform, category, "", items)
        val accepted = mutableListOf<JikanSearchResult>()
        for (item in items) {
            if (!showAdultContent && item.isAdult) continue
            if (sourceForResult(item.source) != ExplorePlatform.SIMKL) {
                rejected += MobileExploreRejectedItem(section, item, MobileExploreRejection.UNKNOWN_OR_UNEXPECTED_SOURCE)
            } else if (!matchesTab(item, tab, section)) {
                rejected += MobileExploreRejectedItem(section, item, MobileExploreRejection.MEDIA_TYPE_MISMATCH)
            } else {
                accepted += item
            }
        }
        if (accepted.isNotEmpty()) shelves += MobileExploreShelfData(
            tab = tab,
            source = ExplorePlatform.SIMKL,
            originPlatform = originPlatform,
            category = category,
            origin = origin,
            originalTitle = "",
            results = accepted.distinctBy { it.exploreIdentity() },
            // Continue goes to watch history; planned needs a personal-list grid, never a catalog query.
            requiresSpecialContinuation = true,
        )
    }
    add(MobileExploreTab.SERIES, ExploreCategoryType.AIRING_ANIME, MobileShelfOrigin.SIMKL_CONTINUE, payload.simklContinueSeries)
    add(MobileExploreTab.MOVIES, ExploreCategoryType.MOVIE_ANIME, MobileShelfOrigin.SIMKL_CONTINUE, payload.simklContinueMovies)
    add(MobileExploreTab.SERIES, ExploreCategoryType.AIRING_ANIME, MobileShelfOrigin.SIMKL_PLANNED, payload.simklPlannedSeries)
    add(MobileExploreTab.MOVIES, ExploreCategoryType.MOVIE_ANIME, MobileShelfOrigin.SIMKL_PLANNED, payload.simklPlannedMovies)
    return MobileExploreMappingResult(shelves, rejected)
}

/**
 * A deterministic, local first candidate from the active tab only. Unlike shared video hero
 * selection this may select books; it never changes the global hero policy or fetches artwork.
 * The screen remains responsible for loading/error handling and cancelling stale selections.
 */
internal fun mobileExploreHeroCandidate(
    tab: MobileExploreTab,
    shelves: List<MobileExploreShelfData>,
): JikanSearchResult? {
    val candidates = shelves.asSequence().filter { it.tab == tab }.flatMap { it.results.asSequence() }.toList()
    return candidates.firstOrNull { !it.backdropUrl.isNullOrBlank() }
        ?: candidates.firstOrNull { !it.imageUrl.isNullOrBlank() }
}
