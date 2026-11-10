package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.model.MediaType
import java.util.Locale

/**
 * The filter sheets have two state bags: the common (legacy) bag and one bag for the
 * selected provider.  Keeping the merge here makes the request boundary explicit and,
 * more importantly, prevents a provider request from silently dropping a selection made
 * in GenresTagsSheet.
 *
 * These functions are pure.  SearchViewModel takes a SearchUiState snapshot before it
 * starts a request and calls them only with that immutable snapshot, so page 2 uses the
 * exact same effective filters as page 1.
 */

private fun <T> distinctValues(vararg lists: List<T>): List<T> =
    lists.asSequence().flatten().distinct().toList()

private fun stricterMin(specific: Int?, common: Int?): Int? =
    listOfNotNull(specific, common).maxOrNull()

private fun stricterMax(specific: Int?, common: Int?): Int? =
    listOfNotNull(specific, common).minOrNull()

private fun stricterMinDouble(specific: Double?, common: Double?): Double? =
    listOfNotNull(specific, common).maxOrNull()

private fun stricterMaxDouble(specific: Double?, common: Double?): Double? =
    listOfNotNull(specific, common).minOrNull()

private fun normalizedCommonTerms(state: SearchUiState): List<String> =
    (state.genres + state.tags)
        .map { SearchTranslation.translateToEnglishForSearch(it).trim() }
        .filter { it.isNotBlank() }
        .distinct()

private fun normalizedCommonStatuses(state: SearchUiState): List<String> =
    state.selectedStatuses.map { it.apiValue }.distinct()

private fun normalizedCommonFormats(state: SearchUiState): List<String> =
    state.selectedFormats.map { it.apiValue }.distinct()

private fun mapMalStatus(status: String, isManga: Boolean): String? = when (
    status.uppercase(Locale.ROOT)
) {
    "FINISHED" -> if (isManga) "finished" else "finished_airing"
    "RELEASING" -> if (isManga) "publishing" else "currently_airing"
    "NOT_YET_RELEASED" -> "upcoming"
    "CANCELLED" -> if (isManga) "discontinued" else "finished_airing"
    "HIATUS" -> if (isManga) "hiatus" else "currently_airing"
    else -> status.takeIf { it.isNotBlank() }
}

private fun mapShikimoriStatus(status: String): String? = when (
    status.uppercase(Locale.ROOT)
) {
    "FINISHED" -> "released"
    "RELEASING" -> "ongoing"
    "NOT_YET_RELEASED" -> "anons"
    "CANCELLED" -> "discontinued"
    "HIATUS" -> "paused"
    else -> status.lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
}

private fun mapKitsuStatus(status: String): String? = when (
    status.uppercase(Locale.ROOT)
) {
    "FINISHED" -> "finished"
    "RELEASING" -> "current"
    "NOT_YET_RELEASED" -> "upcoming"
    "CANCELLED" -> "cancelled"
    "HIATUS" -> "tba"
    else -> status.lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
}

private fun malTypeFor(format: String, isManga: Boolean): String? {
    val normalized = format.uppercase(Locale.ROOT)
    val printFormats = setOf("MANGA", "NOVEL", "ONE_SHOT")
    if (isManga != (normalized in printFormats)) return null
    return when (normalized) {
        "TV" -> "tv"
        "TV_SHORT" -> "tv_special"
        "MOVIE" -> "movie"
        "SPECIAL" -> "special"
        "OVA" -> "ova"
        "ONA" -> "ona"
        "MUSIC" -> "music"
        "MANGA" -> "manga"
        "NOVEL" -> "novel"
        "ONE_SHOT" -> "one_shot"
        else -> format.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
    }
}

private fun commonFormatsForMedia(state: SearchUiState): List<String> {
    val isManga = state.selectedMediaType == MediaType.Manga || state.selectedScope.isPrintScope
    return normalizedCommonFormats(state).filter { format ->
        val isPrintFormat = format.uppercase(Locale.ROOT) in setOf("MANGA", "NOVEL", "ONE_SHOT")
        isManga == isPrintFormat
    }
}

private fun kitsuSubtypeFor(format: String): String? = when (format.uppercase(Locale.ROOT)) {
    "TV" -> "tv"
    "TV_SHORT" -> "tv"
    "MOVIE" -> "movie"
    "SPECIAL" -> "special"
    "OVA" -> "ova"
    "ONA" -> "ona"
    "MANGA" -> "manga"
    "NOVEL" -> "novel"
    "ONE_SHOT" -> "one-shot"
    else -> null
}

private fun shikimoriKindFor(format: String): String? = when (format.uppercase(Locale.ROOT)) {
    "TV" -> "tv"
    "TV_SHORT" -> "tv"
    "MOVIE" -> "movie"
    "SPECIAL" -> "special"
    "OVA" -> "ova"
    "ONA" -> "ona"
    "MANGA" -> "manga"
    "NOVEL" -> "light_novel"
    "ONE_SHOT" -> "one_shot"
    else -> null
}

private fun commonYearStart(state: SearchUiState, specific: Int?): Int? =
    stricterMin(specific, state.startYear)

private fun commonYearEnd(state: SearchUiState, specific: Int?): Int? =
    stricterMax(specific, state.endYear)

private fun commonScoreMin(state: SearchUiState, specific: Int?): Int? =
    stricterMin(specific, state.minScore)

private fun commonScoreMax(state: SearchUiState, specific: Int?): Int? =
    stricterMax(specific, state.maxScore)

private fun commonScoreMin10(state: SearchUiState, specific: Double?): Double? =
    stricterMinDouble(specific, state.minScore?.toDouble()?.div(10.0))

private fun commonScoreMax10(state: SearchUiState, specific: Double?): Double? =
    stricterMaxDouble(specific, state.maxScore?.toDouble()?.div(10.0))

private fun commonSort(state: SearchUiState, providerDefault: String): String =
    if (state.sortSearch == com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSortSearch.SEARCH_MATCH) {
        providerDefault
    } else {
        state.effectiveSortApiValue
    }

internal fun SearchUiState.effectiveAniListFilters(): AniListSpecificFilters {
    val specific = aniListSpecificFilters
    val includedGenres = genres
        .filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }
        .map { SearchTranslation.translateToEnglishForSearch(it) }
    val excluded = excludedGenres
        .filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }
        .map { SearchTranslation.translateToEnglishForSearch(it) }
    val commonTags = (tags + genres.filterNot { CanonicalFilterBridge.isOfficialAniListGenre(it) })
        .map { SearchTranslation.translateToEnglishForSearch(it) }
        .filter(SearchTaxonomy::isKnownAniListTag)
    val commonExcludedTags = excludedGenres
        .filterNot { CanonicalFilterBridge.isOfficialAniListGenre(it) }
        .map { SearchTranslation.translateToEnglishForSearch(it) }
        .filter(SearchTaxonomy::isKnownAniListTag)

    val season = specific.season ?: this.season?.apiValue
    val seasonYear = specific.seasonYear ?: startYear?.takeIf { season != null }
    return specific.copy(
        formats = distinctValues(specific.formats, commonFormatsForMedia(this)),
        statuses = distinctValues(specific.statuses, normalizedCommonStatuses(this)),
        season = season,
        seasonYear = seasonYear,
        startYear = commonYearStart(this, specific.startYear),
        endYear = commonYearEnd(this, specific.endYear),
        country = specific.country ?: country?.code,
        sources = distinctValues(specific.sources, selectedSources.map { it.apiValue }),
        genres = distinctValues(specific.genres, includedGenres),
        excludedGenres = distinctValues(specific.excludedGenres, excluded),
        tags = distinctValues(specific.tags.filter(SearchTaxonomy::isKnownAniListTag), commonTags),
        excludedTags = distinctValues(specific.excludedTags.filter(SearchTaxonomy::isKnownAniListTag), commonExcludedTags),
        minScore = commonScoreMin(this, specific.minScore),
        maxScore = commonScoreMax(this, specific.maxScore),
        minEpCh = stricterMin(specific.minEpCh, minEpCh),
        maxEpCh = stricterMax(specific.maxEpCh, maxEpCh),
        minDuration = stricterMin(specific.minDuration, minDuration),
        maxDuration = stricterMax(specific.maxDuration, maxDuration),
        onMyList = specific.onMyList ?: onMyList,
        isDoujin = specific.isDoujin ?: isDoujin,
        isAdult = specific.isAdult ?: isAdultFilter,
        sort = commonSort(this, specific.sort)
    )
}

internal fun SearchUiState.effectiveMalFilters(): MalSpecificFilters {
    val specific = malSpecificFilters
    val isManga = selectedMediaType == MediaType.Manga || selectedScope.isPrintScope
    val included = normalizedCommonTerms(this).mapNotNull {
        CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull()
    }
    val excluded = excludedGenres.mapNotNull {
        CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull()
    }
    val mappedStatuses = normalizedCommonStatuses(this).mapNotNull { mapMalStatus(it, isManga) }
    val sourceStatuses = distinctValues(listOfNotNull(specific.status), specific.statuses, mappedStatuses)
    val status = specific.status ?: sourceStatuses.firstOrNull()
    val formatTypes = normalizedCommonFormats(this).mapNotNull { malTypeFor(it, isManga) }
    val sourceTypes = distinctValues(listOfNotNull(specific.type), specific.types, formatTypes)
    val selectedType = specific.type ?: sourceTypes.firstOrNull()

    return specific.copy(
        types = sourceTypes,
        type = selectedType,
        statuses = sourceStatuses,
        status = status,
        season = specific.season ?: season?.apiValue?.lowercase(Locale.ROOT),
        seasonYear = specific.seasonYear ?: startYear,
        genres = distinctValues(specific.genres, included),
        excludedGenres = distinctValues(specific.excludedGenres, excluded),
        minScore = commonScoreMin10(this, specific.minScore),
        maxScore = commonScoreMax10(this, specific.maxScore),
        startYear = commonYearStart(this, specific.startYear),
        endYear = commonYearEnd(this, specific.endYear),
        orderBy = commonSort(this, specific.orderBy)
    )
}

internal fun SearchUiState.effectiveTmdbFilters(): TmdbSpecificFilters {
    val specific = tmdbSpecificFilters
    val isTv = selectedScope.isLiveActionScope && selectedScope != SearchScope.MOVIE ||
        selectedMediaType == MediaType.TvShow
    val included = normalizedCommonTerms(this).mapNotNull {
        CanonicalFilterBridge.mapTmdbGenreId(it, isTv)
    }
    val excluded = excludedGenres.mapNotNull {
        CanonicalFilterBridge.mapTmdbGenreId(it, isTv)
    }
    return specific.copy(
        isMovie = specific.isMovie.takeIf { selectedScope !in setOf(SearchScope.TV, SearchScope.K_DRAMA) }
            ?: !isTv,
        sortBy = commonSort(this, specific.sortBy),
        genres = distinctValues(specific.genres, included),
        excludedGenres = distinctValues(specific.excludedGenres, excluded),
        originCountry = specific.originCountry ?: country?.code,
        startYear = commonYearStart(this, specific.startYear),
        endYear = commonYearEnd(this, specific.endYear),
        minScore = commonScoreMin10(this, specific.minScore),
        maxScore = commonScoreMax10(this, specific.maxScore),
        includeAdult = specific.includeAdult || isAdultFilter == true
    )
}

internal fun SearchUiState.effectiveShikimoriFilters(): ShikimoriSpecificFilters {
    val specific = shikimoriSpecificFilters
    val isManga = selectedMediaType == MediaType.Manga || selectedScope.isPrintScope
    val included = normalizedCommonTerms(this).mapNotNull {
        CanonicalFilterBridge.mapAnimeGenreId(it) ?: CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull()
    }
    val excluded = excludedGenres.mapNotNull {
        CanonicalFilterBridge.mapAnimeGenreId(it) ?: CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull()
    }
    val mappedStatuses = normalizedCommonStatuses(this).mapNotNull(::mapShikimoriStatus)
    val seasonValue = specific.season ?: when {
        season != null && startYear != null -> "${season.apiValue.lowercase(Locale.ROOT)}_$startYear"
        startYear != null -> startYear.toString()
        else -> null
    }
    val commonKinds = commonFormatsForMedia(this).mapNotNull(::shikimoriKindFor)
    return specific.copy(
        kinds = distinctValues(specific.kinds, commonKinds),
        statuses = distinctValues(specific.statuses, mappedStatuses),
        season = seasonValue,
        minScore = stricterMin(specific.minScore, minScore?.div(10)),
        genres = distinctValues(specific.genres, included),
        excludedGenres = distinctValues(specific.excludedGenres, excluded),
        censored = specific.censored && isAdultFilter != true,
        order = commonSort(this, specific.order).lowercase(Locale.ROOT)
    )
}

internal fun SearchUiState.effectiveKitsuFilters(): KitsuSpecificFilters {
    val specific = kitsuSpecificFilters
    // Kitsu's `filter[categories]` is a controlled taxonomy, not a free-text tag
    // endpoint.  Only pass canonical category slugs; unknown common tags must not turn
    // the whole request into an empty-result filter.
    val categories = normalizedCommonTerms(this).mapNotNull(SearchFacetBridge::mapKitsuCategory)
    val canonicalSpecificCategories = specific.categories.mapNotNull(SearchFacetBridge::mapKitsuCategory)
    val statuses = normalizedCommonStatuses(this).mapNotNull(::mapKitsuStatus)
    val subtypes = commonFormatsForMedia(this).mapNotNull(::kitsuSubtypeFor)
    return specific.copy(
        subtypes = distinctValues(specific.subtypes, subtypes),
        statuses = distinctValues(specific.statuses, statuses),
        season = specific.season ?: season?.apiValue?.lowercase(Locale.ROOT),
        seasonYear = specific.seasonYear ?: startYear,
        categories = distinctValues(canonicalSpecificCategories, categories),
        minRating = stricterMin(specific.minRating, minScore),
        sort = commonSort(this, specific.sort).let { it.lowercase(Locale.ROOT) }
    )
}

internal fun SearchUiState.effectiveSimklFilters(): SimklSpecificFilters {
    val specific = simklSpecificFilters
    val genres = distinctValues(
        listOfNotNull(specific.genre),
        specific.genres,
        normalizedCommonTerms(this).mapNotNull(CanonicalFilterBridge::mapSimklGenre)
    )
    return specific.copy(
        genres = genres,
        genre = specific.genre ?: genres.firstOrNull(),
        country = specific.country ?: country?.code?.lowercase(Locale.ROOT),
        year = specific.year ?: startYear?.toString(),
        sort = commonSort(this, specific.sort).lowercase(Locale.ROOT)
    )
}

internal fun SearchUiState.effectiveBangumiFilters(): BangumiSpecificFilters {
    val specific = bangumiSpecificFilters
    val terms = distinctValues(
        specific.tags.mapNotNull(SearchFacetBridge::mapBangumiTag),
        (genres + tags).mapNotNull(SearchFacetBridge::mapBangumiTag)
    )
    return specific.copy(
        tags = terms,
        yearFrom = commonYearStart(this, specific.yearFrom),
        yearTo = commonYearEnd(this, specific.yearTo),
        season = specific.season ?: season?.apiValue,
        seasonYear = specific.seasonYear ?: startYear,
        minScore = stricterMin(specific.minScore, minScore?.div(10)),
        nsfw = specific.nsfw || isAdultFilter == true,
        comicsOnly = specific.comicsOnly && selectedScope !in setOf(SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)
    )
}

/** Common filters count even when a provider-specific sheet is open. */
internal val SearchUiState.commonFilterCount: Int
    get() = listOf(
        genres.size,
        excludedGenres.size,
        tags.size,
        selectedFormats.size,
        selectedStatuses.size,
        selectedSources.size,
        if (country != null) 1 else 0,
        if (startYear != null || endYear != null) 1 else 0,
        if (season != null) 1 else 0,
        if (minScore != null || maxScore != null) 1 else 0,
        if (minEpCh != null || maxEpCh != null) 1 else 0,
        if (minDuration != null || maxDuration != null) 1 else 0,
        if (onMyList != null) 1 else 0,
        if (isDoujin != null) 1 else 0,
        if (isAdultFilter != null) 1 else 0
    ).sum()
