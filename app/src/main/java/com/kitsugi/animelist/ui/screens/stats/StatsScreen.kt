package com.kitsugi.animelist.ui.screens.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsBarChart
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsBarEntry
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCard
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCardHeader
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsDistributionBar
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsMetricTile
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsProgressList
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsSegment
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.plainLabel

/** Local-library statistics, presented with the same chart cards as media details. */
@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onBack: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var selectedMediaType by rememberSaveable { mutableIntStateOf(0) }
    val isAnime = selectedMediaType == 0
    val accentColor = LocalKitsugiAccent.current
    val statusStats = if (isAnime) uiState.animeStatusStats else uiState.mangaStatusStats
    val scoreStats = if (isAnime) uiState.animeScoreStats else uiState.mangaScoreStats
    val formatStats = if (isAnime) uiState.animeFormatStats else uiState.mangaFormatStats
    val yearStats = if (isAnime) uiState.animeYearStats else uiState.mangaYearStats
    val meanScore = if (isAnime) uiState.meanScore else uiState.mangaMeanScore
    val scoreStandardDeviation = if (isAnime) uiState.animeScoreStandardDeviation else uiState.mangaScoreStandardDeviation

    LaunchedEffect(uiState.isLoading, uiState.totalAnime, uiState.totalManga) {
        if (!uiState.isLoading && uiState.totalAnime == 0 && uiState.totalManga > 0) {
            selectedMediaType = 1
        } else if (!uiState.isLoading && uiState.totalManga == 0 && uiState.totalAnime > 0) {
            selectedMediaType = 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.ArrowBack,
                    contentDescription = stringResource(R.string.geri),
                    tint = KitsugiColors.TextPrimary
                )
            }
            Spacer(Modifier.padding(start = 4.dp))
            Text(
                text = stringResource(R.string.istatistikler),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                KitsugiPlasmaLoader(size = 52.dp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 4 }
                ) {
                    KitsugiStatsCard {
                        KitsugiStatsCardHeader(
                            title = stringResource(R.string.profile_stats_general),
                            icon = Icons.BarChart,
                            subtitle = stringResource(R.string.profile_records),
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KitsugiStatsMetricTile(
                                label = stringResource(R.string.media_type_anime),
                                value = uiState.totalAnime.toString(),
                                icon = Icons.PlayCircle,
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor
                            )
                            KitsugiStatsMetricTile(
                                label = stringResource(R.string.profile_manga_list),
                                value = uiState.totalManga.toString(),
                                icon = Icons.MenuBook,
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor
                            )
                            KitsugiStatsMetricTile(
                                label = stringResource(R.string.profile_my_favorites),
                                value = uiState.totalFavorites.toString(),
                                icon = Icons.Favorite,
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KitsugiStatsMetricTile(
                                label = stringResource(R.string.profile_stat_episodes_watched),
                                value = uiState.totalEpisodesWatched.toString(),
                                icon = Icons.Slideshow,
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor
                            )
                            KitsugiStatsMetricTile(
                                label = stringResource(R.string.profile_stat_chapters_read),
                                value = uiState.totalChaptersRead.toString(),
                                icon = Icons.MenuBook,
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor
                            )
                            KitsugiStatsMetricTile(
                                label = stringResource(R.string.profile_stat_days_watched_short),
                                value = String.format(com.kitsugi.animelist.utils.KitsugiStrings.locale(), "%.1f", uiState.daysWatched),
                                icon = Icons.AccessTime,
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KitsugiStatsMetricTile(
                                label = stringResource(R.string.profile_stat_average_score),
                                value = if (meanScore > 0f) String.format(com.kitsugi.animelist.utils.KitsugiStrings.locale(), "%.1f", meanScore) else "–",
                                icon = Icons.Star,
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor
                            )
                            KitsugiStatsMetricTile(
                                label = stringResource(R.string.profile_stat_standard_deviation),
                                value = if (meanScore > 0f) String.format(com.kitsugi.animelist.utils.KitsugiStrings.locale(), "%.1f", scoreStandardDeviation) else "–",
                                icon = Icons.BarChart,
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor
                            )
                        }
                    }
                }

                if (uiState.totalManga > 0) {
                    KitsugiStatsCard {
                        KitsugiStatsCardHeader(
                            title = stringResource(R.string.istatistikler),
                            icon = Icons.Movie,
                            subtitle = stringResource(R.string.profile_tab_statistics),
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(KitsugiColors.SurfaceStrong)
                                .padding(4.dp)
                        ) {
                            listOf(stringResource(R.string.media_type_anime), stringResource(R.string.profile_manga_list)).forEachIndexed { index, label ->
                                val selected = selectedMediaType == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (selected) accentColor else Color.Transparent)
                                        .clickable { selectedMediaType = index }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) KitsugiColors.Background else KitsugiColors.TextMuted,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (statusStats.isNotEmpty()) {
                    KitsugiStatsCard {
                        KitsugiStatsCardHeader(
                            title = if (isAnime) stringResource(R.string.anime_durum_dagilimi) else stringResource(R.string.manga_durum_dagilimi),
                            icon = Icons.DonutLarge,
                            subtitle = stringResource(R.string.detail_stats_status_subtitle, statusStats.sumOf { it.count }),
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(12.dp))
                        KitsugiStatsDistributionBar(
                            items = statusStats.map { stat ->
                                KitsugiStatsSegment(
                                    label = stat.status.plainLabel(),
                                    count = stat.count,
                                    color = statusColor(stat.status)
                                )
                            }
                        )
                    }
                }

                if (scoreStats.isNotEmpty()) {
                    KitsugiStatsCard {
                        val mean = if (isAnime) uiState.meanScore else uiState.mangaMeanScore
                        KitsugiStatsCardHeader(
                            title = if (isAnime) stringResource(R.string.anime_puan_dagilimi) else "${stringResource(R.string.profile_manga_list)} ${stringResource(R.string.puan_dagilimi)}",
                            icon = Icons.BarChart,
                            subtitle = stringResource(R.string.detail_stats_score_subtitle, scoreStats.sumOf { it.count }.toString(), String.format(com.kitsugi.animelist.utils.KitsugiStrings.locale(), "%.1f", mean)),
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(12.dp))
                        KitsugiStatsProgressList(
                            items = scoreStats.sortedByDescending { it.score }.map { stat ->
                                KitsugiStatsSegment(
                                    label = stringResource(R.string.x_puan, stat.score),
                                    count = stat.count,
                                    color = Color.Unspecified
                                )
                            },
                            accentColor = accentColor
                        )
                    }
                }

                if (formatStats.isNotEmpty()) {
                    KitsugiStatsCard {
                        KitsugiStatsCardHeader(
                            title = stringResource(R.string.tur_dagilimi),
                            icon = Icons.Movie,
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(12.dp))
                        KitsugiStatsDistributionBar(
                            items = formatStats.mapIndexed { index, stat ->
                                KitsugiStatsSegment(formatLabel(stat.format), stat.count, formatColor(index, accentColor))
                            }
                        )
                    }
                }

                if (yearStats.isNotEmpty()) {
                    KitsugiStatsCard {
                        KitsugiStatsCardHeader(
                            title = stringResource(R.string.yayin_yili),
                            icon = Icons.CalendarToday,
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(12.dp))
                        KitsugiStatsBarChart(
                            items = yearStats.map { KitsugiStatsBarEntry(it.year.toString(), it.count.toFloat()) },
                            accentColor = accentColor,
                            maxVisibleBars = 10
                        )
                    }
                }

                if (uiState.totalAnime == 0 && uiState.totalManga == 0) {
                    KitsugiEmptyState(
                        title = stringResource(R.string.istatistik_icin_liste_gerekli),
                        subtitle = stringResource(R.string.anime_veya_manga_ekledikten_sonra_istatistikler_burada_gorunur),
                        icon = Icons.BarChart
                    )
                }
            }
        }
    }
}

private fun statusColor(status: WatchStatus): Color = when (status) {
    WatchStatus.Watching, WatchStatus.Repeating -> Color(0xFF34D399)
    WatchStatus.Completed -> Color(0xFF60A5FA)
    WatchStatus.Planned -> Color(0xFF818CF8)
    WatchStatus.Dropped -> Color(0xFFFB7185)
    WatchStatus.Paused -> Color(0xFF94A3B8)
}

@Composable
private fun formatLabel(raw: String): String = when (raw.uppercase()) {
    "ANIME" -> stringResource(R.string.media_type_anime)
    "MANGA" -> stringResource(R.string.profile_manga_list)
    "MOVIE" -> stringResource(R.string.search_format_movie)
    "TVSHOW" -> stringResource(R.string.profile_tv_show_entries)
    else -> raw
}

@Composable
private fun formatColor(index: Int, accentColor: Color): Color = when (index % 5) {
    0 -> accentColor
    1 -> KitsugiColors.AccentBlue
    2 -> KitsugiColors.AccentTeal
    3 -> KitsugiColors.AccentPurple
    else -> KitsugiColors.AccentOrange
}
