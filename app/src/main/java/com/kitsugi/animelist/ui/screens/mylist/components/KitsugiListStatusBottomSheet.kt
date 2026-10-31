package com.kitsugi.animelist.ui.screens.mylist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.LocalDismissAnimated
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

data class StatusItemData(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val count: Int
)

/** Profile-style category sheet. Selecting a row applies it and animates dismissal. */
@Composable
fun KitsugiListStatusBottomSheet(
    entries: List<MediaEntry>,
    selectedStatusFilterId: String,
    showAdultContent: Boolean = false,
    onStatusSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val statusLabels = mapOf(
        "all" to stringResource(R.string.mylist_filter_all),
        "watching" to stringResource(R.string.mylist_filter_watching),
        "completed" to stringResource(R.string.mylist_filter_completed),
        "planned" to stringResource(R.string.mylist_filter_planned),
        "paused" to stringResource(R.string.mylist_filter_paused),
        "dropped" to stringResource(R.string.mylist_filter_dropped),
        "adult" to stringResource(R.string.filter_adult),
        "favorites" to stringResource(R.string.mylist_filter_favorites)
    )

    val watchingCount = entries.count { it.status == WatchStatus.Watching }
    val completedCount = entries.count { it.status == WatchStatus.Completed }
    val plannedCount = entries.count { it.status == WatchStatus.Planned }
    val pausedCount = entries.count { it.status == WatchStatus.Paused }
    val droppedCount = entries.count { it.status == WatchStatus.Dropped }
    val adultCount = entries.count { it.isAdult }
    val favoritesCount = entries.count { it.isFavorite }

    val statusItems = mutableListOf(
        StatusItemData("all", statusLabels.getValue("all"), Icons.FormatListBulleted, entries.size),
        StatusItemData("watching", statusLabels.getValue("watching"), Icons.PlayCircle, watchingCount),
        StatusItemData("completed", statusLabels.getValue("completed"), Icons.CheckCircle, completedCount),
        StatusItemData("planned", statusLabels.getValue("planned"), Icons.Schedule, plannedCount),
        StatusItemData("paused", statusLabels.getValue("paused"), Icons.PauseCircle, pausedCount),
        StatusItemData("dropped", statusLabels.getValue("dropped"), Icons.StopCircle, droppedCount)
    )

    if (showAdultContent && adultCount > 0) {
        statusItems.add(
            StatusItemData("adult", statusLabels.getValue("adult"), Icons.Star, adultCount)
        )
    }

    if (favoritesCount > 0) {
        statusItems.add(
            StatusItemData("favorites", statusLabels.getValue("favorites"), Icons.Star, favoritesCount)
        )
    }

    val scrollState = rememberScrollState()
    KitsugiSheetOrDialog(
        onDismiss = onDismissRequest,
        innerColumnScrollState = scrollState
    ) {
        val dismissAnimated = LocalDismissAnimated.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statusItems.forEach { item ->
                ListStatusRow(
                    item = item,
                    selected = selectedStatusFilterId == item.id,
                    onClick = {
                        onStatusSelected(item.id)
                        dismissAnimated()
                    }
                )
            }
        }
    }
}

/** Same subdued selection rows as the profile category sheet, with list counts. */
@Composable
private fun ListStatusRow(
    item: StatusItemData,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.15f)
                else KitsugiColors.SurfaceStrong.copy(alpha = 0.4f)
            )
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = item.title,
            modifier = Modifier.weight(1f),
            color = if (selected) accent else KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold
        )
        Text(
            text = item.count.toString(),
            color = if (selected) accent else KitsugiColors.TextMuted,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
