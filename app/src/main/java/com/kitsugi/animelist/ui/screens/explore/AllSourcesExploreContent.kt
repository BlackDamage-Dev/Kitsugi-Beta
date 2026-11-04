package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.PlatformErrorCodeChip
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.foundation.ExperimentalFoundationApi
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiHorizontalMediaSection
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiShimmerMediaRow
import com.kitsugi.animelist.ui.components.kitsugiCardFrame
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
internal fun localizedExploreSectionTitle(title: String): String {
    if (title == "Yayındaki Mangalar") {
        val locales = LocalConfiguration.current.locales
        val lang = if (locales.isEmpty) "tr" else locales.get(0)?.language.orEmpty()
        return if (lang.startsWith("tr", ignoreCase = true)) {
            "Yayındaki Mangalar"
        } else {
            stringResource(R.string.explore_publishing_manga)
        }
    }
    val titleRes = when (title) {
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
        "Yayındaki Mangalar" -> R.string.explore_source_airing_manga
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
        SIMKL_PERSONAL_CONTINUE_SERIES_TITLE -> R.string.explore_simkl_continue_watching_series
        SIMKL_PERSONAL_CONTINUE_MOVIES_TITLE -> R.string.explore_simkl_continue_watching_movies
        SIMKL_PERSONAL_PLANNED_SERIES_TITLE -> R.string.explore_simkl_plantowatch_series
        SIMKL_PERSONAL_PLANNED_MOVIES_TITLE -> R.string.explore_simkl_plantowatch_movies
        else -> return title
    }
    return stringResource(titleRes)
}

/** One source = one distinct destination, with all its own categories underneath it. */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.allSourcesExploreSections(
    states: Map<ExplorePlatform, ExploreSourceState>,
    /**
     * Kaynak → bölümler. Çağrı tarafında `remember` ile önbelleklenir; bu fonksiyon
     * LazyListScope içinde her yeniden kompozisyonda çalıştığı için burada yeniden
     * hesaplanmamalıdır (forSource + distinctBy maliyeti kare düşüşü üretiyordu).
     */
    sectionsByPlatform: Map<ExplorePlatform, List<ExploreSourceSection>>,
    /** Kaynak → benzersiz içerik sayısı (SourceHeader rozeti için, önceden hesaplanmış). */
    distinctCounts: Map<ExplorePlatform, Int>,
    airingSoonShelf: List<JikanSearchResult> = emptyList(),
    airingSoonTitle: String = "",
    airingSoonIsLoading: Boolean = false,
    onOpenAiringCalendar: (() -> Unit)? = null,
    collapsedSources: Set<ExplorePlatform>,
    onToggleSource: (ExplorePlatform) -> Unit,
    startIndex: Int,
    onJumpToIndex: (Int) -> Unit,
    onRetrySource: (ExplorePlatform) -> Unit,
    loadedCategories: Set<ExploreCategoryType>,
    loadingCategories: Set<ExploreCategoryType>,
    failedCategories: Set<ExploreCategoryType>,
    onLoadCategory: (ExplorePlatform, ExploreCategoryType) -> Unit,
    onRetryCategory: (ExplorePlatform, ExploreCategoryType) -> Unit,
    alreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: ((JikanSearchResult) -> Unit)?,
    onSeeAllSection: (String, ExploreCategoryType, List<JikanSearchResult>, ExplorePlatform) -> Unit,
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false,
    showSourceJumpBar: Boolean = true
) {
    val sections = sectionsByPlatform
    val hasAiringSoonItem = airingSoonShelf.isNotEmpty() || airingSoonIsLoading
    val headerIndices = allSourceHeaderIndices(
        sections, collapsedSources, startIndex, showSourceJumpBar,
        extraItemsAfterIntro = if (hasAiringSoonItem) 1 else 0
    )
    item(key = "all_sources_intro") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(stringResource(R.string.explore_all_sources_title), style = MaterialTheme.typography.headlineSmall,
                color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.inline_each_platform_s_trending_top_231becd),
                style = MaterialTheme.typography.bodyMedium, color = KitsugiColors.TextSecondary)
        }
    }
    // Tümü modunda ortak geri sayımlı şerit — tek takvim verisi, tüm kaynakların üstünde.
    if (hasAiringSoonItem) {
        item(key = "all_airing_soon") {
            Column(Modifier.padding(bottom = 20.dp)) {
                ExploreAiringSoonSection(
                    title = airingSoonTitle.ifBlank { stringResource(R.string.explore_airing_soon) },
                    airingSoonAnime = airingSoonShelf,
                    isLoading = airingSoonIsLoading,
                    alreadyInList = alreadyInList,
                    onItemClick = onItemClick,
                    onLongClickItem = onLongClickItem ?: {},
                    onOpenAiringCalendar = onOpenAiringCalendar ?: {},
                    accentColor = KitsugiColors.Accent,
                    titleLanguage = titleLanguage,
                    blurAdultMedia = blurAdultMedia
                )
            }
        }
    }
    if (showSourceJumpBar) {
        stickyHeader(key = "all_sources_navigation") {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .kitsugiCardFrame(RoundedCornerShape(18.dp), highlightColor = KitsugiColors.SurfaceElevated.copy(alpha = 0.78f)),
            shape = RoundedCornerShape(18.dp),
            color = KitsugiColors.Surface.copy(alpha = 0.96f),
            tonalElevation = 2.dp
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "back_to_explore_top") {
                    BackToExploreChip { onJumpToIndex(0) }
                }
                items(ExplorePlatform.sources, key = { it.name }) { platform ->
                    SourceJumpChip(platform, states[platform]?.isLoading == true) {
                        onJumpToIndex(headerIndices.getValue(platform))
                    }
                }
            }
        }
    }
    }
    ExplorePlatform.sources.forEach { platform ->
        val state = states[platform] ?: ExploreSourceState(isLoading = true)
        val sourceSections = sections[platform].orEmpty()
        val expanded = platform !in collapsedSources
        item(key = "all_source_${platform.name}") {
            val platformLabel = platform.localizedLabel()
            val sectionTitles = sourceSections.map { localizedExploreSectionTitle(it.title) }
            SourceHeader(
                platform = platform,
                state = state,
                sections = sourceSections,
                distinctItemCount = distinctCounts[platform] ?: 0,
                expanded = expanded,
                onToggle = { onToggleSource(platform) },
                onRetry = { onRetrySource(platform) },
                onCategory = { index ->
                    sourceSections.getOrNull(index)?.let { section ->
                        val sectionTitle = sectionTitles.getOrNull(index) ?: section.title
                        onSeeAllSection(
                            "$platformLabel • $sectionTitle",
                            section.category,
                            section.results,
                            platform
                        )
                    }
                }
            )
        }
        if (expanded) {
            items(sourceSections, key = { "all_${it.key}" }) { section ->
                val sectionTitle = localizedExploreSectionTitle(section.title)
                val platformLabel = platform.localizedLabel()
                // The source heading owns the rows; the same row model is used in single-source mode.
                CompositionLocalProviderForSource(platform) {
                    ExploreSourceShelfRow(
                        section = section.copy(title = sectionTitle),
                        isProviderLoading = state.isLoading,
                        isCategoryLoaded = section.category in loadedCategories,
                        isCategoryLoading = section.category in loadingCategories,
                        isCategoryFailed = section.category in failedCategories,
                        onLoadCategory = { onLoadCategory(platform, section.category) },
                        onRetryCategory = { onRetryCategory(platform, section.category) },
                        isAlreadyInList = alreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onItemClick,
                        onLongClickItem = onLongClickItem,
                        onSeeAllSection = { title, category, results ->
                            onSeeAllSection("$platformLabel · $title", category, results, platform)
                        },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores,
                        blurAdultMedia = blurAdultMedia
                    )
                }
            }
            item(key = "all_source_footer_${platform.name}") {
                if (sourceSections.isEmpty() && state.isLoading) {
                    Column(Modifier.padding(vertical = 16.dp)) {
                        val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        com.kitsugi.animelist.ui.components.KitsugiShimmerResponsiveMediaRow(
                            cardWidth = com.kitsugi.animelist.ui.components.exploreShelfCardWidth(isLandscape),
                            isLandscape = isLandscape,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            cardSpacing = 12.dp,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onToggleSource(platform) }) { Text(stringResource(R.string.explore_source_collapse, platform.localizedShortName())) }
                    val next = ExplorePlatform.sources.getOrNull(ExplorePlatform.sources.indexOf(platform) + 1)
                    if (next != null) {
                        TextButton(onClick = { onJumpToIndex(headerIndices.getValue(next)) }) {
                            Text(stringResource(R.string.explore_source_next, next.localizedShortName()))
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = sourceColor(platform).copy(alpha = 0.25f))
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun CompositionLocalProviderForSource(platform: ExplorePlatform, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        com.kitsugi.animelist.ui.theme.LocalKitsugiAccent provides sourceColor(platform), content = content
    )
}

private fun sourceColor(platform: ExplorePlatform): Color = when (platform) {
    ExplorePlatform.AniList -> Color(0xFF65BFFF)
    ExplorePlatform.MAL -> Color(0xFF8EAFFF)
    ExplorePlatform.TMDB -> Color(0xFF54D9B4)
    ExplorePlatform.SIMKL -> Color(0xFFC2ADFF)
    ExplorePlatform.KITSU -> Color(0xFFFFA080)
    ExplorePlatform.SHIKIMORI -> Color(0xFFD1BEEC)
    ExplorePlatform.BANGUMI -> Color(0xFFF09199)
    ExplorePlatform.ALL -> Color(0xFFE9AD65)
}

@Composable
private fun BackToExploreChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(KitsugiColors.SurfaceElevated.copy(alpha = 0.82f))
            .kitsugiCardFrame(shape, highlightColor = KitsugiColors.TextMuted.copy(alpha = 0.20f))
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.KeyboardArrowUp,
            contentDescription = null,
            tint = KitsugiColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(stringResource(R.string.kesfet), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SourceJumpChip(platform: ExplorePlatform, loading: Boolean, onClick: () -> Unit) {
    val color = sourceColor(platform)
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.09f))
            .kitsugiCardFrame(shape, highlightColor = color.copy(alpha = 0.32f))
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        KitsugiPlatformLogo(platformId = platform.name, size = 18.dp)
        Text(
            text = platform.localizedShortName(),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = color,
                strokeWidth = 1.5.dp
            )
        }
    }
}

@Composable
private fun SourceHeader(
    platform: ExplorePlatform,
    state: ExploreSourceState,
    sections: List<ExploreSourceSection>,
    distinctItemCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onCategory: (Int) -> Unit
) {
    val color = sourceColor(platform)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.17f), KitsugiColors.Surface)), RoundedCornerShape(24.dp))
            .kitsugiCardFrame(RoundedCornerShape(24.dp), highlightColor = color.copy(alpha = 0.3f))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KitsugiPlatformLogo(platformId = platform.name, size = 42.dp)
            Column(Modifier.weight(1f)) {
                Text(platform.localizedLabel(), color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.explore_source_category_count, sections.size, distinctItemCount),
                    color = color, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onToggle) {
                KitsugiChevronIcon(
                    expanded = expanded,
                    contentDescription = stringResource(if (expanded) R.string.explore_source_collapse else R.string.explore_source_expand, platform.localizedLabel()),
                    tint = color,
                    modifier = Modifier
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(platform.localizedDescription(), color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (state.isLoading) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = color)
            Text(stringResource(if (state.payload != null) R.string.explore_source_refreshing else R.string.explore_source_preparing),
                modifier = Modifier.padding(top = 6.dp), color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else if (state.error != null) {
            Text(state.error, Modifier.padding(top = 10.dp), color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            state.errorCode?.let { code -> PlatformErrorCodeChip(code = code, modifier = Modifier.padding(top = 6.dp)) }
            TextButton(onClick = onRetry) { Text(stringResource(R.string.explore_source_retry, platform.localizedShortName()), color = color) }
        } else if (sections.isEmpty()) {
            Text(stringResource(R.string.explore_source_no_matching_content), Modifier.padding(top = 10.dp),
                color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (expanded && sections.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections.size) { index ->
                    val section = sections[index]
                    SuggestionChip(onClick = { onCategory(index) }, label = {
                        Text(localizedExploreSectionTitle(section.title), color = color)
                    })
                }
            }
        }
    }
}
