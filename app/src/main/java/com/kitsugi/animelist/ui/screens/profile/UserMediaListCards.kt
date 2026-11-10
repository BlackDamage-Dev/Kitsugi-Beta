@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.kitsugiNeonGlow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R


@Composable
internal fun UserMediaGridCard(
    item: UserMediaListItem,
    blurAdultMedia: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .kitsugiNeonGlow(cardShape)
            .clip(cardShape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = cardShape, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(KitsugiColors.SurfaceStrong)
        ) {
            KitsugiNsfwImage(
                model = item.imageUrl,
                contentDescription = item.title,
                isAdult = item.isAdult,
                blurAdultMedia = blurAdultMedia,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Status Badge Top Left
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KitsugiColors.Background.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = item.status.localizedLabel(),
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Score Badge Top Right
            if (item.score != null) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(KitsugiColors.Background.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Star,
                            contentDescription = null,
                            tint = KitsugiColors.AccentYellow,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "%.1f".format(item.score),
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                text = item.title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${item.progress} / ${item.total ?: "?"}",
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )
        }
    }
}


@Composable
internal fun UserMediaRowCard(
    item: UserMediaListItem,
    blurAdultMedia: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .kitsugiNeonGlow(cardShape)
            .clip(cardShape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = cardShape, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp, 84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.SurfaceStrong)
        ) {
            KitsugiNsfwImage(
                model = item.imageUrl,
                contentDescription = item.title,
                isAdult = item.isAdult,
                blurAdultMedia = blurAdultMedia,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.status.localizedLabel(),
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.inline_progress_1_s_2_s_7c471e4, item.progress, item.total ?: "?"),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (item.score != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(KitsugiColors.SurfaceStrong)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Star,
                    contentDescription = null,
                    tint = KitsugiColors.AccentYellow,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "%.1f".format(item.score),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}


@Composable
internal fun UserMediaListStatusBottomSheet(
    items: List<UserMediaListItem>,
    selectedStatus: WatchStatus?,
    mediaType: MediaType,
    onStatusSelected: (WatchStatus?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    val totalCount = items.size
    val watchingCount = items.count { it.status == WatchStatus.Watching }
    val completedCount = items.count { it.status == WatchStatus.Completed }
    val plannedCount = items.count { it.status == WatchStatus.Planned }
    val pausedCount = items.count { it.status == WatchStatus.Paused }
    val droppedCount = items.count { it.status == WatchStatus.Dropped }

    val statusItems = listOf(
        Triple(null, stringResource(R.string.notif_filter_all), Icons.FormatListBulleted to totalCount),
        Triple(WatchStatus.Watching, if (mediaType == MediaType.Anime) stringResource(R.string.izliyor) else "Okuyor", Icons.PlayCircle to watchingCount),
        Triple(WatchStatus.Completed, stringResource(R.string.cs_diag_completed), Icons.CheckCircle to completedCount),
        Triple(WatchStatus.Planned, "Planlanan", Icons.Schedule to plannedCount),
        Triple(WatchStatus.Paused, "Durduruldu", Icons.PauseCircle to pausedCount),
        Triple(WatchStatus.Dropped, stringResource(R.string.system_status_dropped), Icons.StopCircle to droppedCount)
    )

    KitsugiSheetOrDialog(onDismiss = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statusItems.forEach { (status, title, iconAndCount) ->
                val (icon, count) = iconAndCount
                val isSelected = selectedStatus == status

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.15f) else KitsugiColors.SurfaceStrong.copy(alpha = 0.4f)
                        )
                        .tvClickable(shape = RoundedCornerShape(16.dp), onClick = {
                            onStatusSelected(status)
                            onDismissRequest()
                        })
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = title,
                            color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = count.toString(),
                        color = if (isSelected) accentColor else KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
