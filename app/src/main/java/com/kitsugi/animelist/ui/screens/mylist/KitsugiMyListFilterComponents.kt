package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.screens.mylist.components.myListSortMenuItems
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

internal data class StatusFilter(
    val id: String,
    val title: String,
    val status: WatchStatus?,
    val titleRes: Int? = null
)

internal data class TypeFilter(
    val id: String,
    val title: String,
    val type: MediaType?,
    val titleRes: Int? = null
)

internal data class FavoriteFilter(
    val id: String,
    val title: String,
    val titleRes: Int? = null
)

internal data class SortOption(
    val id: String,
    val title: String,
    val titleRes: Int? = null
)

internal data class ScoreFilter(
    val id: String,
    val title: String,
    val titleRes: Int? = null
)

internal data class YearFilter(
    val id: String,
    val title: String,
    val titleRes: Int? = null
)

internal data class ExtraFilter(
    val id: String,
    val title: String,
    val titleRes: Int? = null
)

@Composable
internal fun myListLocalizedTitle(title: String, titleRes: Int?): String =
    titleRes?.let { stringResource(it) } ?: title

internal val statusFilters = listOf(
    StatusFilter("all", "Tümü", null, R.string.mylist_filter_all),
    StatusFilter("watching", "İzleniyor", WatchStatus.Watching, R.string.mylist_filter_watching),
    StatusFilter("completed", "Tamamlandı", WatchStatus.Completed, R.string.mylist_filter_completed),
    StatusFilter("planned", "Planlandı", WatchStatus.Planned, R.string.mylist_filter_planned),
    StatusFilter("dropped", "Bırakıldı", WatchStatus.Dropped, R.string.mylist_filter_dropped),
    StatusFilter("paused", "Durduruldu", WatchStatus.Paused, R.string.mylist_filter_paused)
)

internal val typeFilters = listOf(
    TypeFilter("all", "Tümü", null, R.string.mylist_filter_all),
    TypeFilter("anime", "Anime", MediaType.Anime, R.string.mylist_filter_anime),
    TypeFilter("manga", "Manga", MediaType.Manga, R.string.mylist_filter_manga),
    TypeFilter("movie", "Film", MediaType.Movie, R.string.mylist_filter_movie),
    TypeFilter("tvshow", "Dizi", MediaType.TvShow, R.string.mylist_filter_tvshow)
)

internal val favoriteFilters = listOf(
    FavoriteFilter("all", "Tümü", R.string.mylist_filter_all),
    FavoriteFilter("favorites", "Favoriler", R.string.mylist_filter_favorites)
)

internal val scoreFilters = listOf(
    ScoreFilter("all", "Tümü"),
    ScoreFilter("high", "Çok İyi (8-10)"),
    ScoreFilter("mid", "İyi (5-7)"),
    ScoreFilter("low", "Düşük (1-4)"),
    ScoreFilter("unrated", "Puansız")
)

internal val yearFilters = listOf(
    YearFilter("all", "Tümü", R.string.mylist_filter_all),
    YearFilter("new", "2025 ve Sonrası", R.string.mylist_year_new),
    YearFilter("2020s", "2020 - 2024", R.string.mylist_year_2020s),
    YearFilter("2010s", "2010'lar", R.string.mylist_year_2010s),
    YearFilter("2000s", "2000'ler", R.string.mylist_year_2000s),
    YearFilter("classic", "90'lar ve Öncesi", R.string.mylist_year_classic)
)

internal val extraFilters = listOf(
    ExtraFilter("all", "Tümü", R.string.mylist_filter_all),
    ExtraFilter("repeating", "Tekrar İzlenenler", R.string.mylist_extra_repeating),
    ExtraFilter("private", "Özel / Gizli", R.string.mylist_extra_private),
    ExtraFilter("ongoing", "Tamamlanmamış", R.string.mylist_extra_ongoing)
)

internal val sortOptions = listOf(
    SortOption("newest", "Son eklenen", R.string.mylist_sort_newest),
    SortOption("oldest", "İlk eklenen", R.string.mylist_sort_oldest),
    SortOption("title", "Ada göre", R.string.mylist_sort_title),
    SortOption("score", "Puan"),
    SortOption("progress", "İlerleme", R.string.mylist_sort_progress),
    SortOption("favorites", "Favoriler", R.string.mylist_sort_favorites),
    SortOption("start_date_desc", "Başlangıç (Yeni)", R.string.mylist_sort_start_new),
    SortOption("start_date_asc", "Başlangıç (Eski)", R.string.mylist_sort_start_old),
    SortOption("end_date_desc", "Bitiş (Yeni)", R.string.mylist_sort_end_new),
    SortOption("end_date_asc", "Bitiş (Eski)", R.string.mylist_sort_end_old),
    SortOption("year_desc", "Yayın Yılı (Yeni)", R.string.mylist_sort_year_new),
    SortOption("year_asc", "Yayın Yılı (Eski)", R.string.mylist_sort_year_old),
    SortOption("updated_desc", "Son Güncelleme", R.string.mylist_sort_updated),
    SortOption("priority_desc", "Öncelik", R.string.mylist_sort_priority)
)

@Composable
internal fun AddActionButton(
    title: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                color = if (primary) {
                    accentColor
                } else {
                    KitsugiColors.Surface
                }
            )
            .tvClickable(shape = RoundedCornerShape(22.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (primary) {
                KitsugiColors.Background
            } else {
                accentColor
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
internal fun FilterLabel(
    text: String
) {
    Text(
        text = text.uppercase(),
        color = KitsugiColors.TextMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
internal fun StatusFilterRow(
    selectedStatusFilterId: String,
    onStatusSelected: (String) -> Unit
) {
    FilterChipRow(
        items = statusFilters.map { it.id to myListLocalizedTitle(it.title, it.titleRes) },
        selectedId = selectedStatusFilterId,
        onSelected = onStatusSelected
    )
}

@Composable
internal fun TypeFilterRow(
    selectedTypeFilterId: String,
    onTypeSelected: (String) -> Unit
) {
    FilterChipRow(
        items = typeFilters.map { it.id to myListLocalizedTitle(it.title, it.titleRes) },
        selectedId = selectedTypeFilterId,
        onSelected = onTypeSelected
    )
}

@Composable
internal fun FavoriteFilterRow(
    selectedFavoriteFilterId: String,
    onFavoriteFilterSelected: (String) -> Unit
) {
    FilterChipRow(
        items = favoriteFilters.map { it.id to myListLocalizedTitle(it.title, it.titleRes) },
        selectedId = selectedFavoriteFilterId,
        onSelected = onFavoriteFilterSelected
    )
}

@Composable
internal fun ScoreFilterRow(
    selectedScoreFilterId: String,
    onScoreSelected: (String) -> Unit
) {
    FilterChipRow(
        items = scoreFilters.map { it.id to myListLocalizedTitle(it.title, it.titleRes) },
        selectedId = selectedScoreFilterId,
        onSelected = onScoreSelected
    )
}

@Composable
internal fun YearFilterRow(
    selectedYearFilterId: String,
    onYearSelected: (String) -> Unit
) {
    FilterChipRow(
        items = yearFilters.map { it.id to myListLocalizedTitle(it.title, it.titleRes) },
        selectedId = selectedYearFilterId,
        onSelected = onYearSelected
    )
}

@Composable
internal fun ExtraFilterRow(
    selectedExtraFilterId: String,
    onExtraSelected: (String) -> Unit
) {
    FilterChipRow(
        items = extraFilters.map { it.id to myListLocalizedTitle(it.title, it.titleRes) },
        selectedId = selectedExtraFilterId,
        onSelected = onExtraSelected
    )
}

@Composable
internal fun SortFilterRow(
    selectedSortId: String,
    onSortSelected: (String) -> Unit
) {
    FilterChipRow(
        items = sortOptions.map { it.id to myListLocalizedTitle(it.title, it.titleRes) },
        selectedId = selectedSortId,
        onSelected = onSortSelected
    )
}

/**
 * Filtre panelinin en üstündeki içerik türü hapları (Anime / Manga / Karakterler /
 * Ekip / Stüdyo). Hem eski gömülü panelde hem de yeni premium açılır panelde
 * (KitsugiMyListFilterSheet) ortak kullanılır.
 */
@Composable
internal fun MyListContentTypeRow(
    selectedTypeFilterId: String,
    onTypeSelected: (String) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val typeItems = listOf(
            "anime" to "Anime",
            "manga" to "Manga",
            "characters" to "Karakterler",
            "staff" to "Ekip",
            "studios" to "Stüdyo"
        )
        typeItems.forEach { (id, label) ->
            val isSelected = selectedTypeFilterId == id || (id == "anime" && selectedTypeFilterId == "all")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else KitsugiColors.SurfaceStrong.copy(alpha = 0.4f))
                    .tvClickable(shape = RoundedCornerShape(12.dp), onClick = {
                        if (id == "anime" || id == "manga") onTypeSelected(id)
                    })
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                KitsugiEmojiLabel(
                    label = label,
                    icon = if (isSelected) Icons.Check else null,
                    iconSize = 13.dp,
                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun RichMyListFilterPanel(
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedFavoriteFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String,
    selectedSortId: String,
    onStatusSelected: (String) -> Unit,
    onTypeSelected: (String) -> Unit,
    onFavoriteSelected: (String) -> Unit,
    onScoreSelected: (String) -> Unit,
    onYearSelected: (String) -> Unit,
    onExtraSelected: (String) -> Unit,
    onSortSelected: (String) -> Unit,
    onResetFilters: () -> Unit,
    onHideFilters: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(KitsugiColors.Surface.copy(alpha = 0.65f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Content Types
        MyListContentTypeRow(
            selectedTypeFilterId = selectedTypeFilterId,
            onTypeSelected = onTypeSelected
        )

        // Section: Status Filters
        Column {
            FilterLabel(text = "Durum")
            Spacer(modifier = Modifier.height(6.dp))
            StatusFilterRow(
                selectedStatusFilterId = selectedStatusFilterId,
                onStatusSelected = onStatusSelected
            )
        }

        // Section: Media Type
        Column {
            FilterLabel(text = "Medya Türü")
            Spacer(modifier = Modifier.height(6.dp))
            TypeFilterRow(
                selectedTypeFilterId = selectedTypeFilterId,
                onTypeSelected = onTypeSelected
            )
        }

        // Section: Score Range
        Column {
            FilterLabel(text = "Puan Aralığı")
            Spacer(modifier = Modifier.height(6.dp))
            ScoreFilterRow(
                selectedScoreFilterId = selectedScoreFilterId,
                onScoreSelected = onScoreSelected
            )
        }

        // Section: Release Year
        Column {
            FilterLabel(text = stringResource(R.string.yayin_yili))
            Spacer(modifier = Modifier.height(6.dp))
            YearFilterRow(
                selectedYearFilterId = selectedYearFilterId,
                onYearSelected = onYearSelected
            )
        }

        // Section: Extra Options
        Column {
            FilterLabel(text = "Ekstra / Özel")
            Spacer(modifier = Modifier.height(6.dp))
            ExtraFilterRow(
                selectedExtraFilterId = selectedExtraFilterId,
                onExtraSelected = onExtraSelected
            )
        }

        // Section: Sort (Sıralama)
        Column {
            FilterLabel(text = stringResource(R.string.siralama))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                myListSortMenuItems.forEach { item ->
                    val isDescActive = selectedSortId == item.descId
                    val isAscActive = selectedSortId == item.ascId
                    val isSelected = isDescActive || isAscActive
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isSelected) accentColor else KitsugiColors.Surface
                            )
                            .tvClickable(shape = RoundedCornerShape(999.dp), onClick = {
                                val nextId = if (isDescActive) item.ascId else item.descId
                                onSortSelected(nextId)
                            })
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.title,
                                color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = if (isAscActive) Icons.ArrowUpward else Icons.ArrowDownward,
                                    contentDescription = null,
                                    tint = KitsugiColors.Background,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons: Hide & Reset Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onHideFilters)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Filtreleri gizle",
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onResetFilters)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.temizle),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    items: List<Pair<String, String>>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            ListFilterChip(
                title = item.second,
                selected = selectedId == item.first,
                onClick = {
                    onSelected(item.first)
                }
            )
        }
    }
}

@Composable
private fun ListFilterChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                color = if (selected) {
                    accentColor.copy(alpha = 0.15f)
                } else {
                    KitsugiColors.SurfaceStrong.copy(alpha = 0.4f)
                }
            )
            .tvClickable(shape = RoundedCornerShape(999.dp), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) {
                accentColor
            } else {
                KitsugiColors.TextSecondary
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
internal fun EmptyListResultCard(
    searchQuery: String,
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedFavoriteFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String,
    selectedSortId: String
) {
    val activeStatusTitle = statusFilters.firstOrNull { it.id == selectedStatusFilterId }?.let { myListLocalizedTitle(it.title, it.titleRes) } ?: stringResource(R.string.mylist_filter_all)
    val activeTypeTitle = typeFilters.firstOrNull { it.id == selectedTypeFilterId }?.let { myListLocalizedTitle(it.title, it.titleRes) } ?: stringResource(R.string.mylist_filter_all)
    val activeFavoriteTitle = favoriteFilters.firstOrNull { it.id == selectedFavoriteFilterId }?.let { myListLocalizedTitle(it.title, it.titleRes) } ?: stringResource(R.string.mylist_filter_all)
    val activeScoreTitle = scoreFilters.firstOrNull { it.id == selectedScoreFilterId }?.let { myListLocalizedTitle(it.title, it.titleRes) } ?: stringResource(R.string.mylist_filter_all)
    val activeYearTitle = yearFilters.firstOrNull { it.id == selectedYearFilterId }?.let { myListLocalizedTitle(it.title, it.titleRes) } ?: stringResource(R.string.mylist_filter_all)
    val activeExtraTitle = extraFilters.firstOrNull { it.id == selectedExtraFilterId }?.let { myListLocalizedTitle(it.title, it.titleRes) } ?: stringResource(R.string.mylist_filter_all)
    val activeSortTitle = getSortTitle(selectedSortId)

    KitsugiEmptyState(
        title = stringResource(R.string.sonuc_bulunamadi),
        subtitle = if (searchQuery.isBlank()) {
            stringResource(R.string.mylist_empty_filtered)
        } else {
            stringResource(R.string.mylist_empty_search_query, searchQuery)
        },
        icon = Icons.FilterList
    )
}
