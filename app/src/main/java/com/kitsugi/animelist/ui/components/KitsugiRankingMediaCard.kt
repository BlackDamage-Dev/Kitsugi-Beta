package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun KitsugiRankingMediaCard(
    result: JikanSearchResult,
    rankIndex: Int,
    alreadyInList: Boolean = false,
    mediaEntry: com.kitsugi.animelist.model.MediaEntry? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleLanguage: String = "ENGLISH",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false,
    onQuickAddClick: (() -> Unit)? = null
) {
    val accentColor = LocalKitsugiAccent.current
    // PERF: başlık/shape her recomp'da yeniden üretilmesin.
    val displayTitle = remember(result, titleLanguage) { result.getDisplayTitle(titleLanguage) }

    val shape = remember { RoundedCornerShape(20.dp) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .kitsugiNeonGlow(shape)
            .tvClickable(
                shape = shape,
                onClick = onClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.Surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Poster Box with Rank Badge
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(KitsugiColors.SurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                if (!result.imageUrl.isNullOrBlank()) {
                    KitsugiNsfwImage(
                        model = result.imageUrl,
                        contentDescription = displayTitle,
                        isAdult = result.isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = Modifier.fillMaxSize(),
                        initials = displayTitle
                    )
                } else {
                    Text(
                        text = displayTitle.take(2).uppercase(),
                        color = accentColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }

                // Rank Badge (#1, #2, #3, etc.)
                val rankNumber = result.rank ?: rankIndex
                val isTopRank = rankNumber == 1
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(bottomEnd = 10.dp))
                        .background(
                            if (isTopRank) Color(0xFFFFD700).copy(alpha = 0.92f)
                            else Color.Black.copy(alpha = 0.72f)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "#$rankNumber",
                        color = if (isTopRank) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                if (mediaEntry != null) {
                    StatusBadge(
                        status = mediaEntry.status,
                        showIconOnly = true,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                    )
                }

                // Platform source badge
                KitsugiSourceBadge(
                    source = result.source,
                    modifier = Modifier.align(Alignment.BottomStart)
                )

                if (onQuickAddClick != null) {
                    // Liste kartında poster küçük: rozet köşeye yapışık ve daha kompakt.
                    KitsugiQuickAddButton(
                        inList = alreadyInList || mediaEntry != null,
                        onClick = onQuickAddClick,
                        modifier = Modifier.align(Alignment.TopEnd),
                        size = 24.dp,
                        cornerRadius = 9.dp
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title
                Text(
                    text = displayTitle,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle (Format + Total Episodes/Chapters)
                val formatText = buildFormatText(result)
                if (formatText.isNotEmpty()) {
                    Text(
                        text = formatText,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (mediaEntry != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = entryProgressText(mediaEntry),
                            color = statusColor(mediaEntry.status),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (mediaEntry.score != null) {
                            KitsugiEmojiLabel(
                                label = "${mediaEntry.score}",
                                icon = Icons.StarSolid,
                                iconSize = 9.dp,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    EpisodeProgressBar(
                        progress = mediaEntry.progress,
                        total = mediaEntry.total,
                        color = statusColor(mediaEntry.status),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))

                    // Score Line
                    if (!hideScores) {
                        val scoreVal = result.rawScoreDouble
                            ?: result.score?.let { it.toDouble() }
                        if (scoreVal != null && scoreVal > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Star,
                                    contentDescription = stringResource(R.string.detail_stat_score),
                                    tint = Color(0xFFFFB800),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f", scoreVal),
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Members / Popularity Line
                    val membersVal = result.members ?: result.favorites
                    if (membersVal != null && membersVal > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Group,
                                contentDescription = stringResource(R.string.uyeler),
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatNumber(membersVal),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Yayın geri sayımı — tüm kaynaklar (nextAiringEpisode dolu ise): tarih + geri sayım
                if (!result.nextAiringEpisode.isNullOrBlank()) {
                    NextAiringChip(nextAiringEpisode = result.nextAiringEpisode)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    status: com.kitsugi.animelist.model.WatchStatus,
    showIconOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val color = statusColor(status)
    val icon = when (status) {
        com.kitsugi.animelist.model.WatchStatus.Watching   -> Icons.PlayArrow
        com.kitsugi.animelist.model.WatchStatus.Completed  -> Icons.Check
        com.kitsugi.animelist.model.WatchStatus.Planned    -> Icons.Schedule
        com.kitsugi.animelist.model.WatchStatus.Dropped    -> Icons.Close
        com.kitsugi.animelist.model.WatchStatus.Paused     -> Icons.Pause
        com.kitsugi.animelist.model.WatchStatus.Repeating  -> Icons.Repeat
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 10.dp))
            .background(color.copy(alpha = 0.92f))
            .padding(
                horizontal = if (showIconOnly) 5.dp else 6.dp,
                vertical = if (showIconOnly) 5.dp else 3.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showIconOnly) {
            Icon(
                imageVector = icon,
                contentDescription = status.localizedLabel(),
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
        } else {
            Text(
                text = status.localizedLabel(),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun EpisodeProgressBar(
    progress: Int,
    total: Int?,
    color: Color = LocalKitsugiAccent.current,
    modifier: Modifier = Modifier
) {
    val fraction = remember(progress, total) {
        val maxVal = if (total != null && total > 0) total else if (progress > 0) progress else 1
        (progress.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(KitsugiColors.SurfaceSoft)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color)
        )
    }
}

private fun statusColor(status: com.kitsugi.animelist.model.WatchStatus): Color {
    return when (status) {
        com.kitsugi.animelist.model.WatchStatus.Watching   -> KitsugiColors.AccentBlue
        com.kitsugi.animelist.model.WatchStatus.Completed  -> KitsugiColors.AccentGreen
        com.kitsugi.animelist.model.WatchStatus.Planned    -> KitsugiColors.AccentOrange
        com.kitsugi.animelist.model.WatchStatus.Dropped    -> KitsugiColors.AccentRed
        com.kitsugi.animelist.model.WatchStatus.Paused     -> KitsugiColors.AccentPurple
        com.kitsugi.animelist.model.WatchStatus.Repeating  -> KitsugiColors.AccentBlue
    }
}

private fun entryProgressText(entry: com.kitsugi.animelist.model.MediaEntry): String {
    val unit = when (entry.type) {
        com.kitsugi.animelist.model.MediaType.Anime -> "bölüm"
        com.kitsugi.animelist.model.MediaType.Manga -> "chapter"
        else -> "bölüm"
    }

    val totalText = entry.total?.toString() ?: "?"
    return "${entry.progress}/$totalText $unit"
}

private fun buildFormatText(result: JikanSearchResult): String {
    val typeName = when (result.type) {
        MediaType.Anime -> "Anime"
        MediaType.Manga -> "Manga"
        MediaType.Movie -> "Film"
        MediaType.TvShow -> "Dizi"
    }

    return if (result.total != null && result.total > 0) {
        val countLabel = if (result.type == MediaType.Manga) "bölüm" else "bölüm"
        "$typeName (${result.total} $countLabel)"
    } else {
        result.subtitle.ifBlank { typeName }
    }
}

private val trLocale = Locale("tr", "TR")
private val trNumberFormat = NumberFormat.getInstance(trLocale)

private fun formatNumber(number: Int): String {
    return trNumberFormat.format(number)
}
