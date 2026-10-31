package com.kitsugi.animelist.ui.screens.profile.tabs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.app.CountryStatItem
import com.kitsugi.animelist.ui.app.DetailedUserOverviewStats
import com.kitsugi.animelist.ui.app.FormatStatItem
import com.kitsugi.animelist.ui.app.LengthStatItem
import com.kitsugi.animelist.ui.app.ReleaseYearStatItem
import com.kitsugi.animelist.ui.app.ScoreStatItem
import com.kitsugi.animelist.ui.app.StartYearStatItem
import com.kitsugi.animelist.ui.app.StatusStatItem
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsBarChart
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsBarEntry
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCard
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCardHeader
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsDistributionBar
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsMetricTile
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsSegment
import com.kitsugi.animelist.ui.screens.profile.FilterChipItem
import com.kitsugi.animelist.ui.screens.profile.ProfileFilterChip
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiStrings
import androidx.compose.ui.res.stringResource

/**
 * Common, source-neutral presentation for detailed AniList-shaped profile statistics.
 * The backing data may come from AniList or Kitsugi's own public profile cache; the same
 * detail-page chart cards and filters are used in either case.
 */
@Composable
fun DetailedProfileStatsOverview(
    overview: DetailedUserOverviewStats,
    isAnime: Boolean,
    accentColor: Color,
    scoreDistType: Int,
    lengthDistType: Int,
    releaseYearDistType: Int,
    startYearDistType: Int,
    onScoreDistTypeChange: (Int) -> Unit,
    onLengthDistTypeChange: (Int) -> Unit,
    onReleaseYearDistTypeChange: (Int) -> Unit,
    onStartYearDistTypeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OverviewMetricsCard(overview, isAnime, accentColor)

        if (overview.scoreList.isNotEmpty()) {
            AnalyticsChartCard(
                title = stringResource(R.string.detail_stat_score),
                icon = Icons.BarChart,
                subtitle = stringResource(
                    R.string.detail_stats_score_subtitle,
                    overview.scoreList.sumOf { it.count }.toString(),
                    formatProfileNumber(overview.meanScore, 1)
                ),
                accentColor = accentColor
            ) {
                ModeFilterRow(
                    selected = scoreDistType,
                    labels = listOf(
                        stringResource(R.string.baslik_sayisi),
                        if (isAnime) stringResource(R.string.harcanan_sure) else stringResource(R.string.profile_stat_chapters_read)
                    ),
                    onSelected = onScoreDistTypeChange,
                    accentColor = accentColor
                )
                Spacer(Modifier.height(8.dp))
                val scores = (1..10).map { score ->
                    val item = overview.scoreList.firstOrNull { it.score == score }
                    val count = item?.count ?: 0
                    KitsugiStatsBarEntry(
                        label = score.toString(),
                        value = if (scoreDistType == 0) count.toFloat() else (item?.minutesWatched ?: 0).toFloat(),
                        valueLabel = count.takeIf { scoreDistType == 0 }?.toString()
                    )
                }
                KitsugiStatsBarChart(items = scores, accentColor = accentColor)
            }
        }

        if (overview.lengthList.isNotEmpty()) {
            AnalyticsChartCard(
                title = if (isAnime) stringResource(R.string.ui_stats_episode_count) else stringResource(R.string.ui_stats_volume_episode_count),
                icon = Icons.Movie,
                accentColor = accentColor
            ) {
                ModeFilterRow(
                    selected = lengthDistType,
                    labels = listOf(
                        stringResource(R.string.baslik_sayisi),
                        if (isAnime) stringResource(R.string.harcanan_sure) else stringResource(R.string.profile_stat_chapters_read),
                        stringResource(R.string.ortalama_puan)
                    ),
                    onSelected = onLengthDistTypeChange,
                    accentColor = accentColor
                )
                Spacer(Modifier.height(8.dp))
                val entries = overview.lengthList.map { item ->
                    KitsugiStatsBarEntry(
                        label = item.length,
                        value = when (lengthDistType) {
                            0 -> item.count.toFloat()
                            1 -> item.minutesWatched.toFloat()
                            else -> item.meanScore.toFloat()
                        }
                    )
                }
                KitsugiStatsBarChart(items = entries, accentColor = accentColor)
            }
        }

        if (overview.statusList.isNotEmpty()) {
            AnalyticsChartCard(
                title = stringResource(R.string.durum_dagilimi),
                icon = Icons.DonutLarge,
                subtitle = stringResource(R.string.detail_stats_status_subtitle, overview.statusList.sumOf { it.count }),
                accentColor = accentColor
            ) {
                KitsugiStatsDistributionBar(
                    items = overview.statusList.map { status -> statusSegment(status, isAnime, accentColor) }
                )
            }
        }

        if (overview.formatList.isNotEmpty()) {
            AnalyticsChartCard(
                title = stringResource(R.string.tur_dagilimi),
                icon = Icons.Movie,
                accentColor = accentColor
            ) {
                KitsugiStatsDistributionBar(
                    items = overview.formatList.mapIndexed { index, format ->
                        KitsugiStatsSegment(
                            label = formatLabel(format.format),
                            count = format.count,
                            color = categoryColor(index, accentColor)
                        )
                    }
                )
            }
        }

        if (overview.countryList.isNotEmpty()) {
            AnalyticsChartCard(
                title = stringResource(R.string.ulke_dagilimi),
                icon = Icons.Public,
                accentColor = accentColor
            ) {
                KitsugiStatsDistributionBar(
                    items = overview.countryList.mapIndexed { index, item ->
                        KitsugiStatsSegment(
                            label = countryLabel(item.country),
                            count = item.count,
                            color = categoryColor(index, accentColor)
                        )
                    }
                )
            }
        }

        if (overview.releaseYearList.isNotEmpty()) {
            AnalyticsChartCard(
                title = stringResource(R.string.yayin_yili),
                icon = Icons.CalendarToday,
                accentColor = accentColor
            ) {
                YearMetricFilter(selected = releaseYearDistType, onSelected = onReleaseYearDistTypeChange, accentColor = accentColor, isAnime = isAnime)
                Spacer(Modifier.height(8.dp))
                KitsugiStatsBarChart(
                    items = overview.releaseYearList
                        .filter { it.releaseYear > 0 }
                        .sortedBy { it.releaseYear }
                        .map { yearEntry(it, releaseYearDistType) },
                    accentColor = accentColor,
                    maxVisibleBars = 10
                )
            }
        }

        if (overview.startYearList.isNotEmpty()) {
            AnalyticsChartCard(
                title = if (isAnime) stringResource(R.string.ui_stats_watch_year) else stringResource(R.string.ui_stats_read_year),
                icon = Icons.EventAvailable,
                accentColor = accentColor
            ) {
                YearMetricFilter(selected = startYearDistType, onSelected = onStartYearDistTypeChange, accentColor = accentColor, isAnime = isAnime)
                Spacer(Modifier.height(8.dp))
                KitsugiStatsBarChart(
                    items = overview.startYearList
                        .filter { it.startYear > 0 }
                        .sortedBy { it.startYear }
                        .map { yearEntry(it, startYearDistType) },
                    accentColor = accentColor,
                    maxVisibleBars = 10
                )
            }
        }
    }
}

@Composable
private fun OverviewMetricsCard(
    overview: DetailedUserOverviewStats,
    isAnime: Boolean,
    accentColor: Color
) {
    val progressLabel = if (isAnime) {
        stringResource(R.string.profile_stat_episodes_watched)
    } else {
        stringResource(R.string.profile_stat_chapters_read)
    }
    val timeLabel = if (isAnime) {
        stringResource(R.string.profile_stat_days_watched_short)
    } else {
        stringResource(R.string.profile_stat_volumes_read)
    }
    val plannedLabel = if (isAnime) {
        stringResource(R.string.profile_stat_days_planned)
    } else {
        stringResource(R.string.profile_status_planned)
    }

    KitsugiStatsCard {
        KitsugiStatsCardHeader(
            title = stringResource(R.string.profile_stats_general),
            icon = Icons.BarChart,
            subtitle = if (isAnime) stringResource(R.string.media_type_anime) else stringResource(R.string.profile_manga_list),
            accentColor = accentColor
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KitsugiStatsMetricTile(
                label = stringResource(R.string.profile_stat_total_entries),
                value = overview.count.toString(),
                icon = Icons.Movie,
                modifier = Modifier.weight(1f),
                accentColor = accentColor
            )
            KitsugiStatsMetricTile(
                label = progressLabel,
                value = overview.episodesWatched.toString(),
                icon = if (isAnime) Icons.PlayCircle else Icons.MenuBook,
                modifier = Modifier.weight(1f),
                accentColor = accentColor
            )
            KitsugiStatsMetricTile(
                label = timeLabel,
                value = formatProfileNumber(overview.daysWatched, 1),
                icon = Icons.AccessTime,
                modifier = Modifier.weight(1f),
                accentColor = accentColor
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KitsugiStatsMetricTile(
                label = plannedLabel,
                value = formatProfileNumber(overview.plannedDaysOrCount, 1),
                icon = Icons.Schedule,
                modifier = Modifier.weight(1f),
                accentColor = accentColor
            )
            KitsugiStatsMetricTile(
                label = stringResource(R.string.profile_stat_average_score),
                value = formatProfileNumber(overview.meanScore, 2),
                icon = Icons.Star,
                modifier = Modifier.weight(1f),
                accentColor = accentColor
            )
            KitsugiStatsMetricTile(
                label = stringResource(R.string.profile_stat_standard_deviation),
                value = formatProfileNumber(overview.standardDeviation, 1),
                icon = Icons.BarChart,
                modifier = Modifier.weight(1f),
                accentColor = accentColor
            )
        }
    }
}

@Composable
private fun AnalyticsChartCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String? = null,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    KitsugiStatsCard {
        KitsugiStatsCardHeader(title = title, icon = icon, subtitle = subtitle, accentColor = accentColor)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ModeFilterRow(
    selected: Int,
    labels: List<String>,
    onSelected: (Int) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            FilterChipItem(
                selected = selected == index,
                text = label,
                onClick = { onSelected(index) },
                accentColor = accentColor
            )
        }
    }
}

@Composable
private fun YearMetricFilter(
    selected: Int,
    onSelected: (Int) -> Unit,
    accentColor: Color,
    isAnime: Boolean
) {
    ModeFilterRow(
        selected = selected,
        labels = listOf(
            stringResource(R.string.baslik_sayisi),
            if (isAnime) stringResource(R.string.harcanan_sure) else stringResource(R.string.profile_stat_chapters_read),
            stringResource(R.string.ortalama_puan)
        ),
        onSelected = onSelected,
        accentColor = accentColor
    )
}

@Composable
private fun statusSegment(item: StatusStatItem, isAnime: Boolean, accentColor: Color): KitsugiStatsSegment {
    val status = item.status.uppercase()
    val label = when (status) {
        "CURRENT", "WATCHING", "READING" -> if (isAnime) stringResource(R.string.profile_status_watching) else stringResource(R.string.profile_status_reading)
        "COMPLETED" -> stringResource(R.string.profile_status_completed)
        "PLANNING", "PLANNED" -> if (isAnime) stringResource(R.string.profile_status_plan_to_watch_short) else stringResource(R.string.profile_status_plan_to_read_short)
        "PAUSED", "ON_HOLD" -> stringResource(R.string.profile_status_on_hold)
        "DROPPED" -> stringResource(R.string.profile_status_dropped)
        "REPEATING", "REWATCHING", "REREADING" -> if (isAnime) stringResource(R.string.profile_status_rewatching) else stringResource(R.string.profile_status_rereading)
        else -> item.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
    val color = when (status) {
        "CURRENT", "WATCHING", "READING", "REPEATING", "REWATCHING", "REREADING" -> KitsugiColors.AccentGreen
        "COMPLETED" -> KitsugiColors.AccentBlue
        "PLANNING", "PLANNED" -> KitsugiColors.AccentIndigo
        "PAUSED", "ON_HOLD" -> KitsugiColors.TextMuted
        "DROPPED" -> KitsugiColors.AccentRed
        else -> accentColor
    }
    return KitsugiStatsSegment(label, item.count, color)
}

private fun formatLabel(raw: String): String = when (raw.uppercase()) {
    "TV" -> "TV"
    "TV_SHORT" -> KitsugiStrings.get(R.string.search_format_tv_short)
    "MOVIE" -> KitsugiStrings.get(R.string.search_format_movie)
    "SPECIAL" -> KitsugiStrings.get(R.string.search_format_special)
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MUSIC" -> KitsugiStrings.get(R.string.search_filter_music_video)
    "MANGA" -> KitsugiStrings.get(R.string.profile_manga_list)
    "NOVEL" -> KitsugiStrings.get(R.string.search_format_novel)
    "ONE_SHOT" -> "One-Shot"
    else -> raw.replace('_', ' ')
}

private fun countryLabel(raw: String): String = when (raw.uppercase()) {
    "JP" -> KitsugiStrings.get(R.string.search_country_japan)
    "KR" -> KitsugiStrings.get(R.string.search_country_south_korea)
    "CN" -> KitsugiStrings.get(R.string.search_country_china)
    "TW" -> KitsugiStrings.get(R.string.search_country_taiwan)
    else -> raw
}

@Composable
private fun categoryColor(index: Int, accentColor: Color): Color = when (index % 6) {
    0 -> accentColor
    1 -> KitsugiColors.AccentBlue
    2 -> KitsugiColors.AccentTeal
    3 -> KitsugiColors.AccentPurple
    4 -> KitsugiColors.AccentOrange
    else -> KitsugiColors.AccentPink
}

private fun yearEntry(item: ReleaseYearStatItem, metric: Int) = KitsugiStatsBarEntry(
    label = item.releaseYear.toString(),
    value = when (metric) {
        0 -> item.count.toFloat()
        1 -> item.minutesWatched.toFloat()
        else -> item.meanScore.toFloat()
    }
)

private fun yearEntry(item: StartYearStatItem, metric: Int) = KitsugiStatsBarEntry(
    label = item.startYear.toString(),
    value = when (metric) {
        0 -> item.count.toFloat()
        1 -> item.minutesWatched.toFloat()
        else -> item.meanScore.toFloat()
    }
)

private fun formatProfileNumber(value: Double, decimals: Int): String =
    String.format(KitsugiStrings.locale(), "%.${decimals}f", value)
