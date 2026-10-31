package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogos
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.components.kitsugiNeonGlow
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayScore
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Arama sonucu satır bileşeni.
 * MoeList'in arama sonucu liste öğesinden ve AniHyou SearchView.kt'nin
 * sonuç gösterim yaklaşımından ilham alınarak Kitsugi'ya uyarlanmıştır.
 */
@Composable
fun SearchResultRow(
    result: JikanSearchResult,
    alreadyInList: Boolean,
    mediaEntry: com.kitsugi.animelist.model.MediaEntry? = null,
    onItemClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    showAddButton: Boolean = true
) {
    val accentColor = LocalKitsugiAccent.current
    val displayTitle = remember(result, titleLanguage) { result.getDisplayTitle(titleLanguage) }
    val isTv = LocalIsTv.current

    val rowShape = remember(isTv) { if (isTv) KitsugiTvTokens.Shapes.posterCard else RoundedCornerShape(16.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .kitsugiNeonGlow(rowShape)
            .clip(rowShape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = rowShape, onClick = onItemClick)
            .padding(if (isTv) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Kapak görseli
        val thumbWidth = if (isTv) KitsugiTvTokens.Cards.searchThumbWidth else 56.dp
        val thumbHeight = if (isTv) KitsugiTvTokens.Cards.searchThumbHeight else 80.dp
        Box(
            modifier = Modifier
                .size(width = thumbWidth, height = thumbHeight)
                .clip(if (isTv) KitsugiTvTokens.Shapes.posterCard else RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceStrong)
        ) {
            KitsugiNsfwImage(
                model = result.imageUrl,
                contentDescription = displayTitle,
                isAdult = result.isAdult,
                modifier = Modifier.matchParentSize()
            )
        }

        // Bilgiler
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayTitle,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (result.subtitle.isNotBlank()) {
                Text(
                    text = result.subtitle,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (mediaEntry != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor(mediaEntry.status).copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = mediaEntry.status.localizedLabel(),
                            color = statusColor(mediaEntry.status),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = entryProgressText(mediaEntry),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )

                    val displayScore = mediaEntry.getDisplayScore(scoreFormat, hideScores)
                    val isUnrated = displayScore == "unrated" || displayScore == "Unrated" || displayScore == "Puanlanmamış" || displayScore == "Puan yok"
                    val scoreText = if (isUnrated) stringResource(R.string.score_unrated) else "★ $displayScore"
                    Text(
                        text = scoreText,
                        color = KitsugiColors.AccentOrange,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                EpisodeProgressBar(
                    progress = mediaEntry.progress,
                    total = mediaEntry.total,
                    color = statusColor(mediaEntry.status),
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!hideScores) {
                        val displayScore = result.getDisplayScore(scoreFormat, hideScores)
                        val isUnrated = displayScore == "unrated" || displayScore == "Unrated" || displayScore == "Puanlanmamış" || displayScore == "Puan yok"
                        val scoreText = if (isUnrated) stringResource(R.string.score_unrated) else "★ $displayScore"
                        Text(
                            text = scoreText,
                            color = KitsugiColors.AccentOrange,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (result.year != null) {
                        Text(
                            text = "${result.year}",
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = result.type.name,
                        color = accentColor.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    val sourceLabel = result.source.toFriendlySourceLabel()
                    val sourceColor = when (result.source.lowercase()) {
                        "anilist" -> KitsugiColors.AccentBlue
                        "tmdb"    -> Color(0xFFFFB800)
                        else -> accentColor
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.SurfaceStrong)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Kaynak rozetinde artık platformun ORİJİNAL logosu da var
                            // (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi).
                            // Logo tanınmayan kaynaklarda (CS3 eklentileri) yalnızca ad kalır.
                            if (KitsugiPlatformLogos.resFor(result.source) != null) {
                                KitsugiPlatformLogo(
                                    platformId = result.source,
                                    size = 12.dp,
                                    cornerRadius = 3.dp
                                )
                            }
                            Text(
                                text = sourceLabel,
                                color = sourceColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (showAddButton) {
            // Ekle / Eklendi butonu (TV'de odaklanabilir Box + tvClickable)
            val buttonShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(buttonShape)
                    .background(
                        if (alreadyInList) KitsugiColors.AccentGreen.copy(alpha = 0.20f)
                        else accentColor.copy(alpha = 0.15f)
                    )
                    .tvClickable(
                        shape = buttonShape,
                        enabled = !alreadyInList,
                        onClick = onAddClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (alreadyInList) Icons.Check else Icons.Add,
                    contentDescription = if (alreadyInList) "Listede" else "Listeye ekle",
                    tint = if (alreadyInList) KitsugiColors.AccentGreen else accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
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

/**
 * Karakter ve Personel sonuçları için özel kart bileşeni (AniHyou Search tarzı).
 */
@Composable
fun CharacterStaffResultRow(
    result: JikanSearchResult,
    isStaff: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val isTv = LocalIsTv.current
    val rowShape = if (isTv) KitsugiTvTokens.Shapes.posterCard else RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = rowShape, onClick = onClick)
            .padding(if (isTv) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Avatar Görseli
        val thumbSize = if (isTv) 64.dp else 56.dp
        Box(
            modifier = Modifier
                .size(thumbSize)
                .clip(RoundedCornerShape(14.dp))
                .background(KitsugiColors.SurfaceStrong)
        ) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = result.imageUrl,
                contentDescription = result.title,
                isAdult = result.isAdult,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }

        // İsim ve Alt Bilgiler
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = result.title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!result.subtitle.isNullOrBlank()) {
                Text(
                    text = result.subtitle,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favori sayısı & Tip rozeti
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (result.total != null && result.total > 0) {
                    val formattedFavorites = when {
                        result.total >= 1000 -> String.format(java.util.Locale.US, "%.1fk", result.total / 1000.0)
                        else -> result.total.toString()
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.AccentRed.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Favorite,
                                contentDescription = stringResource(R.string.search_accessibility_favorite),
                                tint = KitsugiColors.AccentRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = formattedFavorites,
                                color = KitsugiColors.AccentRed,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isStaff) "Personel" else "Karakter",
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

