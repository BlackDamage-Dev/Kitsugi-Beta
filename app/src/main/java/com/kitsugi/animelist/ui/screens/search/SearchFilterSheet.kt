package com.kitsugi.animelist.ui.screens.search
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.kitsugi.animelist.ui.theme.gradient.RangeSlider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
private fun localizedSearchFormatLabel(format: String): String = when (format) {
    "TV" -> "TV"
    "MOVIE" -> stringResource(R.string.search_format_movie)
    "SPECIAL" -> stringResource(R.string.search_format_special)
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MUSIC" -> stringResource(R.string.search_format_music)
    "MANGA" -> "Manga"
    "NOVEL" -> stringResource(R.string.search_format_novel)
    "ONE_SHOT" -> stringResource(R.string.search_format_one_shot)
    "DOUJIN" -> stringResource(R.string.search_source_doujinshi)
    "MANHWA" -> stringResource(R.string.search_chip_manhwa)
    "MANHUA" -> "Manhua"
    else -> format
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    mediaType: MediaType,
    currentFilters: SearchFilters,
    onDismiss: () -> Unit,
    onApplyFilters: (SearchFilters) -> Unit,
    onResetFilters: () -> Unit
) {
    val isTv = LocalIsTv.current
    val accentColor = LocalKitsugiAccent.current
    val filterListState = rememberLazyListState()

    var tempFilters by remember(currentFilters) { mutableStateOf(currentFilters) }

    val animeFormats = listOf("TV", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC")
    val mangaFormats = listOf("MANGA", "NOVEL", "ONE_SHOT", "DOUJIN", "MANHWA", "MANHUA")
    val formats = if (mediaType == MediaType.Manga) mangaFormats else animeFormats

    val animeStatuses = listOf(
        "AIRING" to stringResource(R.string.search_status_airing),
        "FINISHED" to stringResource(R.string.search_status_finished),
        "UPCOMING" to stringResource(R.string.search_status_upcoming)
    )
    val mangaStatuses = listOf(
        "PUBLISHING" to stringResource(R.string.search_status_publishing),
        "FINISHED" to stringResource(R.string.search_status_finished),
        "HIATUS" to stringResource(R.string.search_status_hiatus_filter),
        "DISCONTINUED" to stringResource(R.string.search_status_discontinued)
    )
    val statuses = if (mediaType == MediaType.Manga) mangaStatuses else animeStatuses

    val seasons = listOf(
        "WINTER" to stringResource(R.string.search_season_winter_chip),
        "SPRING" to stringResource(R.string.search_season_spring_chip),
        "SUMMER" to stringResource(R.string.search_season_summer_chip),
        "FALL" to stringResource(R.string.search_season_fall_chip)
    )

    val genres = listOf(
        "Aksiyon", "Macera", "Komedi", "Dram", "Fantastik",
        "Korku", "Gizem", "Romantizm", "Sci-Fi", "Spor",
        "Doğaüstü", "Gerilim", "Psikoloji", "Müzik", "Okul", "Tarihi", "Mecha"
    )

    val popularTags = listOf(
        "Isekai", "Magic", "Super Power", "Military", "Survival",
        "Cyberpunk", "Martial Arts", "Space", "Post-Apocalyptic", "Vampire"
    )

    val sortOptions = listOf(
        "POPULARITY_DESC" to stringResource(R.string.search_sort_popularity_chip),
        "SCORE_DESC" to "⭐ Puan",
        "TITLE_ROMAJI_ASC" to stringResource(R.string.search_sort_title_asc),
        "TITLE_ROMAJI_DESC" to stringResource(R.string.search_sort_title_desc)
    )

    var yearRange by remember(tempFilters.minYear, tempFilters.maxYear) {
        mutableStateOf(
            (tempFilters.minYear?.toFloat() ?: 1970f)..(tempFilters.maxYear?.toFloat() ?: 2026f)
        )
    }

    var scoreRange by remember(tempFilters.minScore, tempFilters.maxScore) {
        mutableStateOf(
            (tempFilters.minScore?.toFloat() ?: 0f)..(tempFilters.maxScore?.toFloat() ?: 100f)
        )
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        heightFraction = 0.88f,
        innerScrollState = filterListState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_filter_advanced),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )

                if (!tempFilters.isDefault()) {
                    OutlinedButton(
                        onClick = {
                            tempFilters = SearchFilters()
                            yearRange = 1970f..2026f
                            scoreRange = 0f..100f
                            onResetFilters()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Clear, contentDescription = stringResource(R.string.temizle), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.temizle), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filters Scrollable Content
            LazyColumn(
                state = filterListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Format
                item {
                    FilterSectionTitle(stringResource(R.string.search_filter_media_format))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        formats.forEach { format ->
                            val isSelected = tempFilters.format == format
                            FilterChip(
                                label = localizedSearchFormatLabel(format),
                                isSelected = isSelected,
                                onClick = {
                                    tempFilters = tempFilters.copy(format = if (isSelected) null else format)
                                }
                            )
                        }
                    }
                }

                // 2. Status
                item {
                    FilterSectionTitle(stringResource(R.string.search_filter_release_status))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        statuses.forEach { (value, label) ->
                            val isSelected = tempFilters.status == value
                            FilterChip(
                                label = localizedSourceSearchLabel(label),
                                isSelected = isSelected,
                                onClick = {
                                    tempFilters = tempFilters.copy(status = if (isSelected) null else value)
                                }
                            )
                        }
                    }
                }

                // 3. Season (Anime Only)
                if (mediaType != MediaType.Manga) {
                    item {
                        FilterSectionTitle(stringResource(R.string.search_filter_season))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            seasons.forEach { (value, label) ->
                                val isSelected = tempFilters.season == value
                                FilterChip(
                                    label = label,
                                    isSelected = isSelected,
                                    onClick = {
                                        tempFilters = tempFilters.copy(season = if (isSelected) null else value)
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Year Range Slider
                item {
                    val displayYearText = if (tempFilters.minYear == null && tempFilters.maxYear == null) {
                        stringResource(R.string.mylist_filter_all)
                    } else {
                        "${tempFilters.minYear ?: 1970} - ${tempFilters.maxYear ?: 2026}"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterSectionTitle(stringResource(R.string.search_filter_year_range))
                        Text(
                            text = displayYearText,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    RangeSlider(
                        value = yearRange,
                        onValueChange = { range ->
                            yearRange = range
                            tempFilters = tempFilters.copy(
                                minYear = if (range.start.roundToInt() == 1970) null else range.start.roundToInt(),
                                maxYear = if (range.endInclusive.roundToInt() == 2026) null else range.endInclusive.roundToInt()
                            )
                        },
                        valueRange = 1970f..2026f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = accentColor,
                            inactiveTrackColor = KitsugiColors.SurfaceSoft,
                            thumbColor = accentColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 5. Average Score Range Slider
                item {
                    val displayScoreText = if (tempFilters.minScore == null && tempFilters.maxScore == null) {
                        "Tümü"
                    } else {
                        "%${tempFilters.minScore ?: 0} - %${tempFilters.maxScore ?: 100}"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterSectionTitle("Ortalama Puan Aralığı")
                        Text(
                            text = displayScoreText,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    RangeSlider(
                        value = scoreRange,
                        onValueChange = { range ->
                            scoreRange = range
                            tempFilters = tempFilters.copy(
                                minScore = if (range.start.roundToInt() == 0) null else range.start.roundToInt(),
                                maxScore = if (range.endInclusive.roundToInt() == 100) null else range.endInclusive.roundToInt()
                            )
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = accentColor,
                            inactiveTrackColor = KitsugiColors.SurfaceSoft,
                            thumbColor = accentColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 6. Genre Multi-Select (Include / Exclude / None)
                item {
                    FilterSectionTitle(stringResource(R.string.search_filter_genres_include_exclude))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        genres.forEach { genre ->
                            val isIncluded = tempFilters.genres.contains(genre)
                            val isExcluded = tempFilters.excludedGenres.contains(genre)
                            GenreCycleChip(
                                label = SearchTranslation.displayLabel(genre),
                                isIncluded = isIncluded,
                                isExcluded = isExcluded,
                                onClick = {
                                    if (isIncluded) {
                                        // Include -> Exclude
                                        tempFilters = tempFilters.copy(
                                            genres = tempFilters.genres - genre,
                                            excludedGenres = tempFilters.excludedGenres + genre
                                        )
                                    } else if (isExcluded) {
                                        // Exclude -> None
                                        tempFilters = tempFilters.copy(
                                            excludedGenres = tempFilters.excludedGenres - genre
                                        )
                                    } else {
                                        // None -> Include
                                        tempFilters = tempFilters.copy(
                                            genres = tempFilters.genres + genre
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // 7. Tag Multi-Select
                item {
                    FilterSectionTitle(stringResource(R.string.search_filter_tags))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        popularTags.forEach { tag ->
                            val isSelected = tempFilters.tags.contains(tag)
                            FilterChip(
                                label = SearchTranslation.displayLabel(tag),
                                isSelected = isSelected,
                                onClick = {
                                    tempFilters = tempFilters.copy(
                                        tags = if (isSelected) tempFilters.tags - tag else tempFilters.tags + tag
                                    )
                                }
                            )
                        }
                    }
                }

                // 8. Sort
                item {
                    FilterSectionTitle(stringResource(R.string.search_filter_sort))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        sortOptions.forEach { (value, label) ->
                            val isSelected = tempFilters.sort == value
                            FilterChip(
                                label = localizedSourceSearchLabel(label),
                                isSelected = isSelected,
                                onClick = {
                                    tempFilters = tempFilters.copy(sort = value)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = KitsugiColors.TextPrimary
                    )
                ) {
                    Text(stringResource(R.string.iptal), fontWeight = FontWeight.SemiBold)
                }

                KitsugiButton(
                    onClick = {
                        onApplyFilters(tempFilters)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.search_filter_apply), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(title: String) {
    Text(
        text = title,
        color = KitsugiColors.TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (isSelected) accentColor.copy(alpha = 0.15f)
                else KitsugiColors.SurfaceSoft.copy(alpha = 0.5f)
            )
            .border(
                width = 1.5.dp,
                color = if (isSelected) accentColor else Color.Transparent,
                shape = shape
            )
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun GenreCycleChip(
    label: String,
    isIncluded: Boolean,
    isExcluded: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val emeraldGreen = Color(0xFF10B981)
    val roseRed = Color(0xFFEF4444)

    val bgColor = when {
        isIncluded -> emeraldGreen.copy(alpha = 0.12f)
        isExcluded -> roseRed.copy(alpha = 0.12f)
        else -> KitsugiColors.SurfaceSoft.copy(alpha = 0.5f)
    }

    val borderColor = when {
        isIncluded -> emeraldGreen
        isExcluded -> roseRed
        else -> Color.Transparent
    }

    val textColor = when {
        isIncluded -> emeraldGreen
        isExcluded -> roseRed
        else -> KitsugiColors.TextPrimary
    }

    val icon = when {
        isIncluded -> Icons.Add
        isExcluded -> Icons.Remove
        else -> null
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bgColor)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = shape
            )
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isIncluded || isExcluded) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
