package com.kitsugi.animelist.ui.screens.explore

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiHorizontalMediaSection

/** Render provider shelves and their on-demand states with the same section model as All mode. */
fun LazyListScope.sourceExploreSections(
    sections: List<ExploreSourceSection>,
    platform: ExplorePlatform,
    isLoading: Boolean,
    loadedCategories: Set<ExploreCategoryType>,
    loadingCategories: Set<ExploreCategoryType>,
    failedCategories: Set<ExploreCategoryType>,
    onLoadCategory: (ExplorePlatform, ExploreCategoryType) -> Unit,
    onRetryCategory: (ExplorePlatform, ExploreCategoryType) -> Unit,
    isAlreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: (JikanSearchResult) -> Unit,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>) -> Unit,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean
) {
    val populated = sections.filter { it.results.isNotEmpty() }
    // AniList has a complete category model even when some shelves have not been requested yet.
    // Their rows are lazy placeholders; other providers retain the compact loading preview.
    val visibleSections = when {
        platform == ExplorePlatform.AniList -> sections
        populated.isNotEmpty() -> populated
        isLoading -> sections.take(3)
        else -> emptyList()
    }

    visibleSections.forEach { section ->
        item(key = "source_category_${section.key}") {
            val sectionTitle = localizedExploreSectionTitle(section.title)
            ExploreSourceShelfRow(
                section = section.copy(title = sectionTitle),
                isProviderLoading = isLoading,
                isCategoryLoaded = section.category in loadedCategories,
                isCategoryLoading = section.category in loadingCategories,
                isCategoryFailed = section.category in failedCategories,
                onLoadCategory = { onLoadCategory(platform, section.category) },
                onRetryCategory = { onRetryCategory(platform, section.category) },
                isAlreadyInList = isAlreadyInList,
                getMediaEntry = getMediaEntry,
                onItemClick = onItemClick,
                onLongClickItem = onLongClickItem,
                onSeeAllSection = { _, category, results ->
                    onSeeAllSection(sectionTitle, category, results)
                },
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia
            )
        }
    }
}

/** A shelf row shared by single-source and All-source feeds. */
@Composable
internal fun ExploreSourceShelfRow(
    section: ExploreSourceSection,
    isProviderLoading: Boolean,
    isCategoryLoaded: Boolean,
    isCategoryLoading: Boolean,
    isCategoryFailed: Boolean,
    onLoadCategory: () -> Unit,
    onRetryCategory: () -> Unit,
    isAlreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: ((JikanSearchResult) -> Unit)?,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>) -> Unit,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean
) {
    val isAniListShelf = section.platform == ExplorePlatform.AniList
    val isInitialAniListShelf = section.category in ANILIST_INITIAL_EXPLORE_CATEGORIES
    val currentLoad = androidx.compose.runtime.rememberUpdatedState(onLoadCategory)
    val currentRetry = androidx.compose.runtime.rememberUpdatedState(onRetryCategory)

    androidx.compose.runtime.LaunchedEffect(
        section.key, isProviderLoading, isCategoryLoaded, isCategoryLoading, isCategoryFailed
    ) {
        if (isAniListShelf && !isProviderLoading && !isCategoryLoaded &&
            !isCategoryLoading && !isCategoryFailed
        ) {
            currentLoad.value()
        }
    }

    // Do not turn all unrequested AniList categories into a network fan-out during the first
    // payload load. Initial shelves are loaded in one alias query; the rest start as the user
    // scrolls into them after that first request has settled.
    if (isAniListShelf && isProviderLoading && !isInitialAniListShelf &&
        !isCategoryLoaded && !isCategoryLoading
    ) return
    if (isAniListShelf && isCategoryLoaded && section.results.isEmpty()) return
    if (isAniListShelf && isCategoryFailed && section.results.isEmpty() && !isProviderLoading) {
        ExploreShelfRetryRow(section.title) { currentRetry.value() }
        Spacer(modifier = Modifier.height(26.dp))
        return
    }

    val showLoading = if (isAniListShelf) {
        isCategoryLoading || (isProviderLoading && isInitialAniListShelf) ||
            (!isCategoryLoaded && !isCategoryFailed && !isProviderLoading)
    } else {
        isProviderLoading
    }
    if (!isAniListShelf && section.results.isEmpty() && !showLoading) return

    KitsugiHorizontalMediaSection(
        title = section.title,
        results = section.results,
        isLoading = showLoading,
        alreadyInList = isAlreadyInList,
        getMediaEntry = getMediaEntry,
        onItemClick = onItemClick,
        onLongClickItem = onLongClickItem,
        onSeeAllClick = {
            onSeeAllSection(section.title, section.category, section.results)
        },
        titleLanguage = titleLanguage,
        scoreFormat = scoreFormat,
        hideScores = hideScores,
        blurAdultMedia = blurAdultMedia
    )
    Spacer(modifier = Modifier.height(26.dp))
}
