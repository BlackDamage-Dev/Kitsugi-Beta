package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.theme.KitsugiColors

@Composable
fun TmdbCategoriesSection(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    accentColor: Color,
    filteredTopAnime: List<JikanSearchResult>,
    filteredAiringAnime: List<JikanSearchResult>,
    filteredMovieAnime: List<JikanSearchResult>,
    filteredTopManga: List<JikanSearchResult>,
    filteredUpcomingAnime: List<JikanSearchResult>,
    filteredSeasonalAnime: List<JikanSearchResult>,
    filteredPublishingManga: List<JikanSearchResult>,
    filteredTrendingAnime: List<JikanSearchResult>,
    filteredNewlyAddedAnime: List<JikanSearchResult>,
    filteredTrendingManga: List<JikanSearchResult>,
    filteredUpcomingMediaTmdb: List<JikanSearchResult>,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>) -> Unit,
    onOpenAiringCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trendingAllTitle = stringResource(R.string.explore_source_trending_all)
    val trendingAnimeTitle = stringResource(R.string.explore_source_trending_anime)
    val trendingShowsTitle = stringResource(R.string.explore_source_trending_tv)
    val trendingMoviesTitle = stringResource(R.string.explore_source_trending_movies)
    val popularAnimeTitle = stringResource(R.string.explore_source_popular_anime)
    val popularShowsTitle = stringResource(R.string.explore_source_popular_tv)
    val popularMoviesTitle = stringResource(R.string.explore_source_popular_movies)
    val upcomingMoviesAndShowsTitle = stringResource(R.string.explore_source_upcoming_tv_movies)
    val topRatedAnimeTitle = stringResource(R.string.explore_source_top_rated_anime)
    val topRatedShowsTitle = stringResource(R.string.explore_source_top_rated_tv)
    val topRatedMoviesTitle = stringResource(R.string.explore_source_top_rated_movies)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.Surface.copy(alpha = 0.4f))
                .clickable { onExpandedChange(!isExpanded) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.GridView,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.explore_categories_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            }
            KitsugiChevronIcon(
                expanded = isExpanded,
                contentDescription = stringResource(R.string.genislet_daralt),
                tint = KitsugiColors.TextSecondary,
                modifier = Modifier
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 4.dp, end = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.explore_category_tv_movies),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ExploreCategoryChip(
                            label = trendingAllTitle,
                            onClick = { onSeeAllSection(trendingAllTitle, ExploreCategoryType.TOP_ANIME, filteredTopAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = trendingShowsTitle,
                            onClick = { onSeeAllSection(trendingShowsTitle, ExploreCategoryType.AIRING_ANIME, filteredAiringAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = trendingMoviesTitle,
                            onClick = { onSeeAllSection(trendingMoviesTitle, ExploreCategoryType.MOVIE_ANIME, filteredMovieAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = popularShowsTitle,
                            onClick = { onSeeAllSection(popularShowsTitle, ExploreCategoryType.TOP_MANGA, filteredTopManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = popularMoviesTitle,
                            onClick = { onSeeAllSection(popularMoviesTitle, ExploreCategoryType.UPCOMING_ANIME, filteredUpcomingAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = upcomingMoviesAndShowsTitle,
                            onClick = { onSeeAllSection(upcomingMoviesAndShowsTitle, ExploreCategoryType.UPCOMING_MEDIA_TMDB, filteredUpcomingMediaTmdb) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = topRatedShowsTitle,
                            onClick = { onSeeAllSection(topRatedShowsTitle, ExploreCategoryType.SEASONAL_ANIME, filteredSeasonalAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = topRatedMoviesTitle,
                            onClick = { onSeeAllSection(topRatedMoviesTitle, ExploreCategoryType.PUBLISHING_MANGA, filteredPublishingManga) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.media_type_anime),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ExploreCategoryChip(
                            label = trendingAnimeTitle,
                            onClick = { onSeeAllSection(trendingAnimeTitle, ExploreCategoryType.TRENDING_ANIME, filteredTrendingAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = popularAnimeTitle,
                            onClick = { onSeeAllSection(popularAnimeTitle, ExploreCategoryType.NEWLY_ADDED_ANIME, filteredNewlyAddedAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = topRatedAnimeTitle,
                            onClick = { onSeeAllSection(topRatedAnimeTitle, ExploreCategoryType.TRENDING_MANGA, filteredTrendingManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = upcomingMoviesAndShowsTitle,
                            onClick = { onSeeAllSection(upcomingMoviesAndShowsTitle, ExploreCategoryType.UPCOMING_MEDIA_TMDB, filteredUpcomingMediaTmdb) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.yayin_takvimi),
                            onClick = onOpenAiringCalendar
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DefaultCategoriesSection(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    accentColor: Color,
    filteredSeasonalAnime: List<JikanSearchResult>,
    filteredTopAnime: List<JikanSearchResult>,
    filteredTrendingAnime: List<JikanSearchResult>,
    filteredTopRatedAnime: List<JikanSearchResult> = emptyList(),
    filteredAiringAnime: List<JikanSearchResult> = emptyList(),
    filteredUpcomingAnime: List<JikanSearchResult> = emptyList(),
    filteredMovieAnime: List<JikanSearchResult>,
    filteredNewlyAddedAnime: List<JikanSearchResult>,
    filteredTopManga: List<JikanSearchResult>,
    filteredTrendingManga: List<JikanSearchResult>,
    filteredTopRatedManga: List<JikanSearchResult> = emptyList(),
    filteredPublishingManga: List<JikanSearchResult>,
    filteredNewlyAddedManga: List<JikanSearchResult>,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>) -> Unit,
    onOpenAiringCalendar: () -> Unit,
    onOpenMangaReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.Surface.copy(alpha = 0.4f))
                .clickable { onExpandedChange(!isExpanded) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.GridView,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.explore_categories_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            }
            KitsugiChevronIcon(
                expanded = isExpanded,
                contentDescription = stringResource(R.string.genislet_daralt),
                tint = KitsugiColors.TextSecondary,
                modifier = Modifier
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 4.dp, end = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.media_type_anime),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_top_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_top_anime), ExploreCategoryType.TOP_ANIME, filteredTopAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_trending_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_trending_anime), ExploreCategoryType.TRENDING_ANIME, filteredTrendingAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_top_rated_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_top_rated_anime), ExploreCategoryType.TOP_RATED_ANIME, filteredTopRatedAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_airing_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_airing_anime), ExploreCategoryType.AIRING_ANIME, filteredAiringAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_upcoming_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_upcoming_anime), ExploreCategoryType.UPCOMING_ANIME, filteredUpcomingAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_movie_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_movie_anime), ExploreCategoryType.MOVIE_ANIME, filteredMovieAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_seasonal_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_seasonal_anime), ExploreCategoryType.SEASONAL_ANIME, filteredSeasonalAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_newly_added_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_newly_added_anime), ExploreCategoryType.NEWLY_ADDED_ANIME, filteredNewlyAddedAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_airing_soon),
                            onClick = onOpenAiringCalendar
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Manga",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_top_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_top_manga), ExploreCategoryType.TOP_MANGA, filteredTopManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_trending_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_trending_manga), ExploreCategoryType.TRENDING_MANGA, filteredTrendingManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_top_rated_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_top_rated_manga), ExploreCategoryType.TOP_RATED_MANGA, filteredTopRatedManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_publishing_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_publishing_manga), ExploreCategoryType.PUBLISHING_MANGA, filteredPublishingManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_newly_added_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_newly_added_manga), ExploreCategoryType.NEWLY_ADDED_MANGA, filteredNewlyAddedManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_read_manga),
                            onClick = onOpenMangaReader
                        )
                    }
                }
            }
        }
    }
}
