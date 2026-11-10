package com.kitsugi.animelist.ui.screens.mylist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.annotation.StringRes
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Listem'de seçilebilen TEK bir sıralama ölçütü.
 */
data class MyListSortEntry(
    val descId: String,
    val ascId: String?,
    @StringRes val titleRes: Int
)

val myListSortEntries = listOf(
    MyListSortEntry("newest", "oldest", R.string.mylist_sort_added),
    MyListSortEntry("title", "title_desc", R.string.mylist_sort_title),
    MyListSortEntry("score", "score_asc", R.string.detail_stat_score),
    MyListSortEntry("progress", "progress_asc", R.string.mylist_sort_progress),
    MyListSortEntry("favorites", null, R.string.mylist_sort_favorites),
    MyListSortEntry("start_date_desc", "start_date_asc", R.string.mylist_sort_start),
    MyListSortEntry("end_date_desc", "end_date_asc", R.string.mylist_sort_end),
    MyListSortEntry("year_desc", "year_asc", R.string.mylist_sort_year),
    MyListSortEntry("updated_desc", "updated_asc", R.string.mylist_sort_updated),
    MyListSortEntry("repeat_desc", "repeat_asc", R.string.mylist_sort_repeat),
    MyListSortEntry("priority_desc", "priority_asc", R.string.mylist_sort_priority)
)

/** Dokunulduğunda sıradaki sıralama kimliği: azalan → artan → azalan. */
fun MyListSortEntry.nextSortId(current: String): String = when {
    ascId != null && current == ascId -> descId
    ascId != null && current == descId -> ascId
    else -> descId
}

data class SortMenuItem(
    val descId: String,
    val ascId: String,
    val title: String
)

/**
 * Sıralama menüsü — **tek kavram = tek satır**.
 *
 * `ascId == descId` olan satır yön değiştirmez (Varsayılan, Favoriler). "Son eklenen" /
 * "İlk eklenen" ayrı satırlar değil, `newest ↔ oldest` ikilisiyle aynı satırın iki yönüdür;
 * satıra dokunmak yönü çevirir. Menü, filtre panelindeki kompakt çiplerle aynı kaynaktan
 * ([com.kitsugi.animelist.ui.screens.mylist.myListSortChoices]) beslenir.
 */
val myListSortMenuItems = listOf(
    SortMenuItem("default", "default", KitsugiStrings.get(R.string.mylist_sort_default)),
    SortMenuItem("newest", "oldest", KitsugiStrings.get(R.string.mylist_sort_added)),
    SortMenuItem("title", "title_desc", KitsugiStrings.get(R.string.mylist_sort_title)),
    SortMenuItem("score", "score_asc", KitsugiStrings.get(R.string.mylist_sort_score)),
    SortMenuItem("progress", "progress_asc", KitsugiStrings.get(R.string.mylist_sort_progress)),
    SortMenuItem("favorites", "favorites", KitsugiStrings.get(R.string.mylist_sort_favorites)),
    SortMenuItem("updated_desc", "updated_asc", KitsugiStrings.get(R.string.mylist_sort_updated)),
    SortMenuItem("start_date_desc", "start_date_asc", KitsugiStrings.get(R.string.mylist_sort_start)),
    SortMenuItem("end_date_desc", "end_date_asc", KitsugiStrings.get(R.string.mylist_sort_end)),
    SortMenuItem("year_desc", "year_asc", KitsugiStrings.get(R.string.mylist_sort_year)),
    SortMenuItem("priority_desc", "priority_asc", KitsugiStrings.get(R.string.mylist_sort_priority)),
    SortMenuItem("repeat_desc", "repeat_asc", KitsugiStrings.get(R.string.mylist_sort_repeat))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiMyListSortMenu(
    expanded: Boolean,
    selectedSortId: String,
    onSortSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!expanded) return

    val accentColor = LocalKitsugiAccent.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = KitsugiColors.SurfaceStrong,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.45f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                .kitsugiVerticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            myListSortMenuItems.forEach { item ->
                val isDescActive = selectedSortId == item.descId
                val isAscActive = selectedSortId == item.ascId
                val isSelected = isDescActive || isAscActive

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) KitsugiColors.SurfaceSoft else KitsugiColors.SurfaceStrong)
                        .tvClickable(shape = RoundedCornerShape(18.dp)) {
                            // Yön çevirme: seçili satır ters yöne geçer, yönsüz satır sabit kalır.
                            val nextSortId = when {
                                item.ascId == item.descId -> item.descId
                                isDescActive -> item.ascId
                                else -> item.descId
                            }
                            onSortSelected(nextSortId)
                            onDismissRequest()
                        }
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.title,
                        color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = if (isAscActive) Icons.ArrowUpward else Icons.ArrowDownward,
                            contentDescription = if (isAscActive) "Artan" else "Azalan",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
