package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.utils.localizedLabel
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.theme.LocalIsTv
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors

import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayScore
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@OptIn(
    ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun KitsugiMediaEntryCard(
    entry: MediaEntry,
    layoutId: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onEditClick: (() -> Unit)? = null,
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false,
    onPosterLongClick: ((String) -> Unit)? = null,
    sourceBadges: List<String>? = null,
    reserveSourceStripSpace: Boolean = false
) {
    val additionalSources = remember(entry.source, sourceBadges) {
        additionalLibraryBadgeSources(entry.source, sourceBadges)
            .filter { KitsugiPlatformLogos.resFor(it) != null }
    }
    val cardShape = RoundedCornerShape(
        when (layoutId) {
            "compact", "minimalist" -> 20.dp
            "large" -> 28.dp
            "grid_2col" -> 16.dp
            else -> 24.dp
        }
    )

    // One frame contains both the content and its linked-source badges in every layout.
    Card(
        modifier = modifier
            .kitsugiNeonGlow(cardShape)
            .tvClickable(
                shape = cardShape,
                scaleFocused = if (layoutId == "grid_2col") 1.05f else 1.08f,
                onClick = onClick
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface)
    ) {
        when (layoutId) {
            "compact" -> CompactMediaEntryCard(
                entry = entry,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                onEditClick = onEditClick,
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = onPosterLongClick
            )

            "large" -> LargeMediaEntryCard(
                entry = entry,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                onEditClick = onEditClick,
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = onPosterLongClick
            )

            "minimalist" -> MinimalistMediaEntryCard(
                entry = entry,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                onEditClick = onEditClick,
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = onPosterLongClick
            )

            "grid_2col" -> PosterGridMediaEntryCard(
                entry = entry,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = onPosterLongClick
            )

            else -> ComfortableMediaEntryCard(
                entry = entry,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                onEditClick = onEditClick,
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = onPosterLongClick
            )
        }

        if (additionalSources.isNotEmpty()) {
            if (layoutId == "grid_2col") Spacer(Modifier.height(4.dp))
            LibrarySourceBadgeStrip(sources = additionalSources)
        } else if (reserveSourceStripSpace && layoutId == "grid_2col") {
            Spacer(Modifier.height(38.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CompactMediaEntryCard(
    entry: MediaEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    onPosterLongClick: ((String) -> Unit)?
) {
    val statusColor = statusColor(entry.status)

    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.Surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterView(
                entry = entry,
                width = 50,
                height = 70,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = onPosterLongClick,
                onPosterClick = onClick
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mediaTypeLabel(entry),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (entry.year != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "•",
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.year.toString(),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entry.source != "manual") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "•",
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.source.toFriendlySourceLabel().uppercase(),
                            color = LocalKitsugiAccent.current,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entry.isFavorite) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.HeartSolid,
                            contentDescription = stringResource(R.string.favori_label),
                            tint = LocalKitsugiAccent.current,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = entry.getDisplayTitle(titleLanguage),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusPill(
                        text = entry.status.localizedLabel(),
                        color = statusColor
                    )

                    Text(
                        text = entryProgressText(entry),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (!hideScores && (entry.score ?: 0) > 0) {
                        val displayScore = entry.getDisplayScore(scoreFormat, hideScores)
                        ScorePill(
                            score = displayScore,
                            scoreFormat = scoreFormat
                        )
                    }
                }
            }

            val isTv = LocalIsTv.current
            if (!isTv && onEditClick != null) {
                Spacer(modifier = Modifier.width(10.dp))
                QuickEditButton(
                    onClick = onEditClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ComfortableMediaEntryCard(
    entry: MediaEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    onPosterLongClick: ((String) -> Unit)?
) {
    val statusColor = statusColor(entry.status)

    val shape = RoundedCornerShape(24.dp)
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.Surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterView(
                entry = entry,
                width = 72,
                height = 112,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = onPosterLongClick,
                onPosterClick = onClick
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mediaTypeLabel(entry),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (entry.source != "manual") {
                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = entry.source.toFriendlySourceLabel().uppercase(),
                            color = LocalKitsugiAccent.current,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entry.year != null) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = entry.year.toString(),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entry.isFavorite) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                imageVector = Icons.HeartSolid,
                                contentDescription = null,
                                tint = LocalKitsugiAccent.current,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Favori",
                                color = LocalKitsugiAccent.current,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = entry.getDisplayTitle(titleLanguage),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = entry.subtitle,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!entry.synopsis.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = entry.synopsis,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                EpisodeProgressBar(
                    progress = entry.progress,
                    total = entry.total,
                    color = statusColor,
                    modifier = Modifier.fillMaxWidth(0.95f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusPill(
                        text = entry.status.localizedLabel(),
                        color = statusColor
                    )

                    Text(
                        text = entryProgressText(entry),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (!hideScores && (entry.score ?: 0) > 0) {
                        val displayScore = entry.getDisplayScore(scoreFormat, hideScores)
                        ScorePill(
                            score = displayScore,
                            scoreFormat = scoreFormat
                        )
                    }
                }
            }

            val isTv = LocalIsTv.current
            if (!isTv && onEditClick != null) {
                Spacer(modifier = Modifier.width(10.dp))
                QuickEditButton(
                    onClick = onEditClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LargeMediaEntryCard(
    entry: MediaEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    onPosterLongClick: ((String) -> Unit)?
) {
    val statusColor = statusColor(entry.status)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val shape = RoundedCornerShape(28.dp)
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.Surface
        )
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol tarafta poster görseli
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = 210.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(statusColor.copy(alpha = 0.24f))
                ) {
                    KitsugiNsfwImage(
                        model = entry.imageUrl,
                        contentDescription = entry.title,
                        isAdult = entry.isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = Modifier.fillMaxSize(),
                        initials = entry.title,
                        initialsColor = statusColor,
                        initialsStyle = MaterialTheme.typography.displaySmall
                    )

                    PosterCornerTag(
                        text = entry.status.localizedLabel(),
                        tint = statusColor,
                        shape = TopEndCornerShape,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                    KitsugiSourceBadge(
                        source = entry.source,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }

                Spacer(modifier = Modifier.width(18.dp))

                // Sağ tarafta bilgiler
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mediaTypeLabel(entry),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (entry.source != "manual") {
                            Text(
                                text = entry.source.toFriendlySourceLabel().uppercase(),
                                color = LocalKitsugiAccent.current,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (entry.year != null) {
                            Text(
                                text = entry.year.toString(),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (entry.isFavorite) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                imageVector = Icons.HeartSolid,
                                contentDescription = null,
                                tint = LocalKitsugiAccent.current,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Favori",
                                color = LocalKitsugiAccent.current,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = entry.getDisplayTitle(titleLanguage),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = entry.subtitle,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!entry.synopsis.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = entry.synopsis,
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = entryProgressText(entry),
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (!hideScores && (entry.score ?: 0) > 0) {
                                val displayScore = entry.getDisplayScore(scoreFormat, hideScores)
                                Spacer(modifier = Modifier.width(8.dp))
                                ScorePill(
                                    score = displayScore,
                                    scoreFormat = scoreFormat
                                )
                            }
                        }

                        val isTv = LocalIsTv.current
                        if (!isTv && onEditClick != null) {
                            QuickEditButton(
                                onClick = onEditClick
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                LargePosterView(
                    entry = entry,
                    statusColor = statusColor,
                    blurAdultMedia = blurAdultMedia,
                    onPosterLongClick = onPosterLongClick,
                    onPosterClick = onClick
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = mediaTypeLabel(entry),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (entry.source != "manual") {
                        Text(
                            text = entry.source.toFriendlySourceLabel().uppercase(),
                            color = LocalKitsugiAccent.current,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entry.year != null) {
                        Text(
                            text = entry.year.toString(),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entry.isFavorite) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                imageVector = Icons.HeartSolid,
                                contentDescription = null,
                                tint = LocalKitsugiAccent.current,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Favori",
                                color = LocalKitsugiAccent.current,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = entry.getDisplayTitle(titleLanguage),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = entry.subtitle,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!entry.synopsis.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = entry.synopsis,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = entryProgressText(entry),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )

                            if (!hideScores && (entry.score ?: 0) > 0) {
                                val displayScore = entry.getDisplayScore(scoreFormat, hideScores)
                                Spacer(modifier = Modifier.width(8.dp))
                                ScorePill(
                                    score = displayScore,
                                    scoreFormat = scoreFormat
                                )
                            }
                        }

                    val isTv = LocalIsTv.current
                    if (!isTv && onEditClick != null) {
                        QuickEditButton(
                            onClick = onEditClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * Listem satır kartlarındaki düzenleme kısayolu. Kayıt zaten listede olduğu için
 * "+" (listeye ekle / +1 bölüm) değil kalem ikonu gösterilir; tıklanınca detay
 * sayfasına girmeden liste düzenleme sayfası açılır.
 */
@Composable
private fun QuickEditButton(
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(accentColor.copy(alpha = 0.12f))
            .border(
                width = 1.5.dp,
                color = accentColor.copy(alpha = 0.55f),
                shape = shape
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Edit,
            contentDescription = stringResource(R.string.duzenle),
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterView(
    entry: MediaEntry,
    width: Int,
    height: Int,
    blurAdultMedia: Boolean = false,
    onPosterLongClick: ((String) -> Unit)? = null,
    onPosterClick: (() -> Unit)? = null
) {
    val statusColor = statusColor(entry.status)
    val imageUrl = entry.imageUrl
    val isTv = LocalIsTv.current
    val posterModifier = if (isTv) {
        Modifier
    } else if (onPosterLongClick != null && !imageUrl.isNullOrBlank()) {
        Modifier.combinedClickable(
            onLongClick = { onPosterLongClick(imageUrl) },
            onClick = { onPosterClick?.invoke() }
        )
    } else if (onPosterClick != null) {
        Modifier.tvClickable(shape = RoundedCornerShape(14.dp)) { onPosterClick() }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(statusColor.copy(alpha = 0.22f))
            .then(posterModifier),
        contentAlignment = Alignment.Center
    ) {
        // AniHyou paritesi: öncelik rozeti (0=düşük/1=orta/2=yüksek).
        // "Düşük önceliği göster" kapalıysa yalnızca orta+yüksek seviyede görünür.
        val priorityLevel = entry.priority
        if (priorityLevel != null && (priorityLevel > 0 || com.kitsugi.animelist.data.settings.KitsugiContentPrefs.showLowPriority)) {
            val seed = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.priorityColorSeed(priorityLevel)
            val priorityBg = when (priorityLevel) {
                0 -> seed?.let { Color(it) } ?: Color(0xFF607D8B)
                1 -> seed?.let { Color(it) } ?: Color(0xFFFFB300)
                2 -> seed?.let { Color(it) } ?: Color(0xFFE53935)
                else -> seed?.let { Color(it) } ?: Color(0xFF9E9E9E)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(TopEndCornerShape)
                    .background(priorityBg)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = priorityLevel.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        KitsugiNsfwImage(
            model = imageUrl,
            contentDescription = entry.title,
            isAdult = entry.isAdult,
            blurAdultMedia = blurAdultMedia,
            modifier = Modifier.fillMaxSize(),
            initials = entry.title,
            initialsColor = statusColor,
            initialsStyle = MaterialTheme.typography.titleLarge
        )

        // Platform kaynak rozeti — sol alt köşeye yapışık; küçük posterlerde
        // küçültülür ki görseli gereğinden fazla kaplamasın.
        KitsugiSourceBadge(
            source = entry.source,
            size = when {
                width <= 56 -> 16.dp
                width <= 76 -> 18.dp
                else -> 22.dp
            },
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LargePosterView(
    entry: MediaEntry,
    statusColor: Color,
    blurAdultMedia: Boolean = false,
    onPosterLongClick: ((String) -> Unit)? = null,
    onPosterClick: (() -> Unit)? = null
) {
    val imageUrl = entry.imageUrl
    val isTv = LocalIsTv.current
    val posterModifier = if (isTv) {
        Modifier
    } else if (onPosterLongClick != null && !imageUrl.isNullOrBlank()) {
        Modifier.combinedClickable(
            onLongClick = { onPosterLongClick(imageUrl) },
            onClick = { onPosterClick?.invoke() }
        )
    } else if (onPosterClick != null) {
        Modifier.tvClickable(shape = RoundedCornerShape(22.dp)) { onPosterClick() }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(statusColor.copy(alpha = 0.24f))
            .then(posterModifier)
    ) {
        KitsugiNsfwImage(
            model = imageUrl,
            contentDescription = entry.title,
            isAdult = entry.isAdult,
            blurAdultMedia = blurAdultMedia,
            modifier = Modifier.fillMaxSize(),
            initials = entry.title,
            initialsColor = statusColor,
            initialsStyle = MaterialTheme.typography.displaySmall
        )

        // Durum rozeti — sağ üst köşeye yapışık, görseli kaplamayan kompakt kapsül
        PosterCornerTag(
            text = entry.status.localizedLabel(),
            tint = statusColor,
            shape = TopEndCornerShape,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // Platform kaynak rozeti — sol alt
        KitsugiSourceBadge(
            source = entry.source,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f).compositeOver(KitsugiColors.Surface))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

@Composable
private fun ScorePill(
    score: String,
    scoreFormat: String,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val trimmed = score.trim()
    val isUnrated = trimmed.isEmpty() || trimmed == "—" || trimmed.equals("unrated", ignoreCase = true) ||
            trimmed == "Puanlanmamış" || trimmed == "Puan yok" || trimmed == "null" || trimmed == "0" || trimmed == "0/10"
    if (isUnrated) return

    val label = when {
        scoreFormat == "POINT_5" || scoreFormat == "POINT_3" -> trimmed
        trimmed.startsWith("★") -> trimmed
        else -> "★ $trimmed"
    }
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(KitsugiColors.Surface)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        color = KitsugiColors.TextPrimary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

private fun statusColor(status: WatchStatus): Color {
    return when (status) {
        WatchStatus.Watching   -> KitsugiColors.AccentBlue
        WatchStatus.Completed  -> KitsugiColors.AccentGreen
        WatchStatus.Planned    -> KitsugiColors.AccentOrange
        WatchStatus.Dropped    -> KitsugiColors.AccentRed
        WatchStatus.Paused     -> KitsugiColors.AccentPurple
        WatchStatus.Repeating  -> KitsugiColors.AccentBlue   // Rewatching → mavi (aktif izleme gibi)
    }
}

private fun mediaTypeLabel(entry: MediaEntry): String {
    return when (entry.type) {
        MediaType.Anime -> "ANIME"
        MediaType.Manga -> "MANGA"
        MediaType.Movie -> "MOVIE"
        MediaType.TvShow -> "TV SHOW"
    }
}

private fun compactMetaText(entry: MediaEntry): String {
    val parts = buildList {
        add(entryProgressText(entry))
        if (entry.year != null) add(entry.year.toString())
        if (entry.source != "manual") add(entry.source.toFriendlySourceLabel().uppercase())
    }

    return parts.joinToString(" • ")
}

private fun entryProgressText(entry: MediaEntry): String {
    val unit = when (entry.type) {
        MediaType.Anime -> "bölüm"
        MediaType.Manga -> "chapter"
        else -> "bölüm"
    }

    val totalText = entry.total?.toString() ?: "?"
    return "${entry.progress}/$totalText $unit"
}

private fun scoreText(entry: MediaEntry): String {
    return if (entry.score == null) {
        "Puan yok"
    } else {
        "${entry.score}/10"
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MinimalistMediaEntryCard(
    entry: MediaEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    onPosterLongClick: ((String) -> Unit)?
) {
    val statusColor = statusColor(entry.status)

    val shape = RoundedCornerShape(24.dp)
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.Surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterView(
                entry = entry,
                width = 72,
                height = 112,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = onPosterLongClick,
                onPosterClick = onClick
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mediaTypeLabel(entry),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (entry.source != "manual") {
                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = entry.source.toFriendlySourceLabel().uppercase(),
                            color = LocalKitsugiAccent.current,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entry.year != null) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = entry.year.toString(),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entry.isFavorite) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                imageVector = Icons.HeartSolid,
                                contentDescription = null,
                                tint = LocalKitsugiAccent.current,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Favori",
                                color = LocalKitsugiAccent.current,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = entry.getDisplayTitle(titleLanguage),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = entry.subtitle,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusPill(
                        text = entry.status.localizedLabel(),
                        color = statusColor
                    )

                    Text(
                        text = entryProgressText(entry),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (!hideScores && (entry.score ?: 0) > 0) {
                        val displayScore = entry.getDisplayScore(scoreFormat, hideScores)
                        ScorePill(
                            score = displayScore,
                            scoreFormat = scoreFormat
                        )
                    }
                }
            }

            val isTv = LocalIsTv.current
            if (!isTv && onEditClick != null) {
                Spacer(modifier = Modifier.width(10.dp))
                QuickEditButton(
                    onClick = onEditClick
                )
            }
        }
    }
}

// ─── Poster köşe rozetleri ──────────────────────────────────────────────────

/** Sağ üst köşeye tam oturan rozet biçimi: dış köşe düz, iç köşeler yumuşak. */
private val TopEndCornerShape = RoundedCornerShape(
    topStart = 6.dp, topEnd = 0.dp, bottomStart = 6.dp, bottomEnd = 6.dp
)

/** Sağ alt köşeye tam oturan rozet biçimi. */
private val BottomEndCornerShape = RoundedCornerShape(
    topStart = 6.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 0.dp
)

/**
 * Poster köşelerine **yapışık** kompakt rozet: küçük yazı, sıkı iç boşluk ve
 * yarı saydam koyu zemin. [shape] posterin o köşesine oturacak biçimde düz
 * bırakılır; böylece rozet görselin üzerinde yüzüyormuş gibi durmaz, köşenin
 * kendisiyle bütünleşir ve görseli minimum kaplar.
 */
@Composable
private fun PosterCornerTag(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
    icon: ImageVector? = null
) {
    Box(
        modifier = modifier
            .widthIn(max = 108.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.66f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(9.dp)
                )
            }
            Text(
                text = text,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── 2-Sütun Grid Poster Kartı ──────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGridMediaEntryCard(
    entry: MediaEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    onPosterLongClick: ((String) -> Unit)?
) {
    val statusColor = statusColor(entry.status)
    val accentColor = LocalKitsugiAccent.current
    val imageUrl = entry.imageUrl
    val isTv = LocalIsTv.current
    val cardShape = RoundedCornerShape(16.dp)

    val posterModifier = if (isTv) {
        Modifier
    } else if (onPosterLongClick != null && !imageUrl.isNullOrBlank()) {
        Modifier.combinedClickable(
            onLongClick = { onPosterLongClick(imageUrl) },
            onClick = onClick
        )
    } else {
        Modifier.tvClickable(shape = RoundedCornerShape(20.dp), onClick = onClick)
    }

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(KitsugiColors.Surface)
    ) {
        // ─── Poster görseli ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(statusColor.copy(alpha = 0.22f))
                .then(posterModifier)
        ) {
            KitsugiNsfwImage(
                model = imageUrl,
                contentDescription = entry.getDisplayTitle(titleLanguage),
                isAdult = entry.isAdult,
                blurAdultMedia = blurAdultMedia,
                modifier = Modifier.fillMaxSize(),
                initials = entry.title,
                initialsColor = statusColor,
                initialsStyle = MaterialTheme.typography.titleLarge
            )

            // ─── Köşe rozetleri ─────────────────────────────────────────────
            // Rozetler posterin köşelerine TAM yapışık durur ve görselin üzerini
            // minimum kaplar. Sıra: sol üst = favori, sağ üst = durum,
            // sol alt = kaynak, sağ alt = puan.
            if (entry.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(accentColor)
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.StarSolid,
                        contentDescription = stringResource(R.string.puan),
                        tint = KitsugiColors.OnAccent,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }

            // Durum rozeti — üst sağ köşeye tam yapışık
            PosterCornerTag(
                text = entry.status.localizedLabel(),
                tint = statusColor,
                shape = TopEndCornerShape,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // Platform kaynak rozeti — sol alt köşe (köşeye tam yapışık)
            KitsugiSourceBadge(
                source = entry.source,
                modifier = Modifier.align(Alignment.BottomStart)
            )

            // Puan rozeti — sağ alt köşe
            if (!hideScores && (entry.score ?: 0) > 0) {
                PosterCornerTag(
                    text = entry.getDisplayScore(scoreFormat, hideScores),
                    icon = Icons.StarSolid,
                    tint = KitsugiColors.AccentYellow,
                    shape = BottomEndCornerShape,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }

        // ─── Başlık + İlerleme ──────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(
                text = entry.getDisplayTitle(titleLanguage),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entryProgressText(entry),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}