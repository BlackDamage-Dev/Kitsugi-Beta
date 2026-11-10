package com.kitsugi.animelist.ui.screens.profile.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.app.RankedStatItem
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCard
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCardHeader
import com.kitsugi.animelist.ui.screens.profile.KitsugiUserProfileViewModel
import com.kitsugi.animelist.ui.screens.profile.OtherUserProfileState
import com.kitsugi.animelist.ui.screens.profile.PositionalStatItemCard
import com.kitsugi.animelist.ui.screens.profile.ProfileFilterChip
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.toEnglishGenreForSearch

@Composable
fun UserProfileStatsTab(
    state: OtherUserProfileState,
    accentColor: Color,
    viewModel: KitsugiUserProfileViewModel,
    onGenreClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onFavoriteStaffClick: (Int, String, String?, String?) -> Unit,
    onFavoriteStudioClick: ((Int, String, String?, String?) -> Unit)?
) {
    val statsMediaType = viewModel.statsMediaType
    val statsSubTab = viewModel.statsSubTab
    var scoreDistType by remember { mutableStateOf(0) }
    var lengthDistType by remember { mutableStateOf(0) }
    var releaseYearDistType by remember { mutableStateOf(0) }
    var startYearDistType by remember { mutableStateOf(0) }

    val overview = if (statsMediaType == 0) state.animeOverviewStats else state.mangaOverviewStats
    if (statsSubTab == 0) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KitsugiStatsCard {
                KitsugiStatsCardHeader(
                    title = stringResource(R.string.profile_stats_general),
                    icon = Icons.BarChart,
                    subtitle = stringResource(R.string.profile_tab_statistics),
                    accentColor = accentColor
                )
                Spacer(Modifier.height(12.dp))
                ProfileStatsMediaTypeSwitcher(
                    selected = statsMediaType,
                    accentColor = accentColor,
                    onSelected = { viewModel.statsMediaType = it }
                )
            }

            if (overview != null) {
                DetailedProfileStatsOverview(
                    overview = overview,
                    isAnime = statsMediaType == 0,
                    accentColor = accentColor,
                    scoreDistType = scoreDistType,
                    lengthDistType = lengthDistType,
                    releaseYearDistType = releaseYearDistType,
                    startYearDistType = startYearDistType,
                    onScoreDistTypeChange = { scoreDistType = it },
                    onLengthDistTypeChange = { lengthDistType = it },
                    onReleaseYearDistTypeChange = { releaseYearDistType = it },
                    onStartYearDistTypeChange = { startYearDistType = it }
                )
            } else {
                KitsugiStatsCard {
                    Text(
                        text = stringResource(R.string.istatistik_yuklenemedi),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    } else {
        val currentList: List<RankedStatItem> = when (statsSubTab) {
            1 -> overview?.genreList.orEmpty()
            2 -> overview?.tagList.orEmpty()
            3 -> overview?.staffList.orEmpty()
            4 -> overview?.voiceActorList.orEmpty()
            5 -> overview?.studioList.orEmpty()
            else -> emptyList()
        }
        val rankedTitle = when (statsSubTab) {
            1 -> stringResource(R.string.detail_section_genres)
            2 -> stringResource(R.string.profile_section_tags)
            3 -> stringResource(R.string.profile_section_staff)
            4 -> stringResource(R.string.character_tab_voice_actors)
            else -> stringResource(R.string.detail_section_studios)
        }
        val rankedIcon = when (statsSubTab) {
            1 -> Icons.Movie
            2 -> Icons.Label
            3 -> Icons.Groups
            4 -> Icons.Person
            else -> Icons.Sell
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KitsugiStatsCard {
                KitsugiStatsCardHeader(
                    title = rankedTitle,
                    icon = rankedIcon,
                    subtitle = stringResource(R.string.profile_tab_statistics),
                    accentColor = accentColor
                )
                Spacer(Modifier.height(12.dp))
                if (statsSubTab in 1..3) {
                    ProfileStatsMediaTypeSwitcher(
                        selected = statsMediaType,
                        accentColor = accentColor,
                        onSelected = { viewModel.statsMediaType = it }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ProfileFilterChip(
                        isSelected = viewModel.statsSortType == 0,
                        text = stringResource(R.string.baslik_sayisi),
                        onClick = { viewModel.statsSortType = 0 },
                        accentColor = accentColor
                    )
                    ProfileFilterChip(
                        isSelected = viewModel.statsSortType == 1,
                        text = stringResource(R.string.harcanan_sure),
                        onClick = { viewModel.statsSortType = 1 },
                        accentColor = accentColor
                    )
                    ProfileFilterChip(
                        isSelected = viewModel.statsSortType == 2,
                        text = stringResource(R.string.ortalama_puan),
                        onClick = { viewModel.statsSortType = 2 },
                        accentColor = accentColor
                    )
                }
            }

            val sortedList = when (viewModel.statsSortType) {
                0 -> currentList.sortedByDescending { it.count }
                1 -> currentList.sortedByDescending { it.timeSpentMinutes ?: it.chaptersRead ?: 0 }
                else -> currentList.sortedByDescending { it.meanScore }
            }
            if (sortedList.isEmpty()) {
                KitsugiStatsCard {
                    Text(
                        text = stringResource(R.string.istatistik_verisi_bulunamadi),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sortedList.forEachIndexed { index, item ->
                        PositionalStatItemCard(
                            rank = index + 1,
                            title = item.name,
                            count = item.count,
                            meanScore = item.meanScore,
                            timeSpentMinutes = item.timeSpentMinutes,
                            chaptersRead = item.chaptersRead,
                            imageUrl = item.imageUrl,
                            accentColor = accentColor,
                            onClick = {
                                when (statsSubTab) {
                                    1 -> onGenreClick(item.name.toEnglishGenreForSearch())
                                    2 -> onTagClick(item.name)
                                    3, 4 -> if (item.id != null) onFavoriteStaffClick(item.id, "anilist", item.name, item.imageUrl)
                                    5 -> if (item.id != null) onFavoriteStudioClick?.invoke(item.id, "anilist", item.name, item.imageUrl) else onTagClick(item.name)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatsMediaTypeSwitcher(
    selected: Int,
    accentColor: Color,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceStrong)
            .padding(4.dp)
    ) {
        listOf(
            stringResource(R.string.media_type_anime),
            stringResource(R.string.profile_manga_list)
        ).forEachIndexed { index, label ->
            val isSelected = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) accentColor else Color.Transparent)
                    .clickable { onSelected(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
