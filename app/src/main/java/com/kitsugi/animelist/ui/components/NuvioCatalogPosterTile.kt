package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.icons.KitsugiIcons
import com.kitsugi.animelist.ui.screens.explore.mobile.mobileExploreDisplayScore
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.utils.PreferenceHelpers
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.toTurkishGenre

private const val NuvioCatalogPosterAspect = 0.675f
private val NuvioCatalogPosterShape = RoundedCornerShape(12.dp)

/**
 * Shared NuvioMobile-inspired compact 2:3 catalog poster tile used across all
 * "Tümünü Gör" (See All) screens (`MobileExploreFiniteGrid` and `FullScreenMediaGridPage`).
 *
 * Keeps a clean vertical 2:3 poster aspect in both Portrait and Landscape multi-column
 * grids so cards never crush or stretch across 3–7 columns.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NuvioCatalogPosterTile(
    result: JikanSearchResult,
    alreadyInList: Boolean,
    mediaEntry: MediaEntry?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    rankIndex: Int? = null,
    onQuickAddClick: (() -> Unit)? = null,
) {
    val accent = LocalKitsugiAccent.current
    val displayTitle = remember(result, titleLanguage) {
        result.getDisplayTitle(titleLanguage).ifBlank { result.title }
    }
    val metaLine = remember(result.genres, result.year) {
        val genre = result.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.toTurkishGenre()
        val year = result.year?.takeIf { it > 0 }?.toString()
        listOfNotNull(genre, year).joinToString(" • ")
    }
    val scoreText = remember(result.score, mediaEntry?.score, scoreFormat, hideScores) {
        if (hideScores) null
        else {
            val raw = mediaEntry?.score ?: result.score
            val normalized = mobileExploreDisplayScore(raw)
            if (normalized != null) {
                PreferenceHelpers.formatScore(normalized, scoreFormat, hideScores).takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }
    val artUrl = remember(result.imageUrl, result.backdropUrl) {
        result.imageUrl?.takeIf { it.isNotBlank() } ?: result.backdropUrl?.takeIf { it.isNotBlank() }
    }
    var artFailed by remember(artUrl) { mutableStateOf(false) }
    val isInList = alreadyInList || mediaEntry != null
    val rankNumber = result.rank ?: rankIndex

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = displayTitle }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(NuvioCatalogPosterAspect)
                .clip(NuvioCatalogPosterShape)
                .background(KitsugiColors.Surface)
                .border(0.75.dp, KitsugiColors.Border.copy(alpha = 0.55f), NuvioCatalogPosterShape),
        ) {
            if (artUrl != null && !artFailed) {
                KitsugiNsfwImage(
                    model = artUrl,
                    contentDescription = null,
                    isAdult = result.isAdult,
                    blurAdultMedia = blurAdultMedia,
                    onLoadingFailed = { artFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(KitsugiColors.SurfaceElevated, KitsugiColors.SurfaceStrong)
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = KitsugiIcons.PlayCircle,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // Sol üst: Sıra (#1) veya Listede onay rozeti
            if (rankNumber != null) {
                val isTop = rankNumber == 1
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isTop) KitsugiColors.AccentYellow.copy(alpha = 0.92f)
                            else Color.Black.copy(alpha = 0.72f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "#$rankNumber",
                        color = if (isTop) Color.Black else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            } else if (isInList && onQuickAddClick == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = KitsugiIcons.Check,
                        contentDescription = stringResource(R.string.mobile_explore_in_list),
                        tint = KitsugiColors.OnAccent,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            // Sağ üst: Hızlı ekle / düzenle butonu (FullScreenMediaGridPage) veya minimal puan rozeti
            if (onQuickAddClick != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(38.dp)
                        .clickable(role = Role.Button, onClick = onQuickAddClick)
                        .padding(6.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (isInList) accent else Color.Black.copy(alpha = 0.72f)
                            )
                            .border(
                                width = 0.75.dp,
                                color = if (isInList) accent else Color.White.copy(alpha = 0.28f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isInList) KitsugiIcons.Check else KitsugiIcons.Add,
                            contentDescription = stringResource(
                                if (isInList) R.string.mobile_explore_in_list else R.string.listeye_ekle
                            ),
                            tint = if (isInList) KitsugiColors.OnAccent else Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            } else if (scoreText != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = KitsugiIcons.Star,
                        contentDescription = null,
                        tint = KitsugiColors.AccentYellow,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = scoreText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            // QuickAdd aktifken puanı sağ altta minimal göster
            if (onQuickAddClick != null && scoreText != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.74f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = KitsugiIcons.Star,
                        contentDescription = null,
                        tint = KitsugiColors.AccentYellow,
                        modifier = Modifier.size(9.dp),
                    )
                    Text(
                        text = scoreText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = displayTitle,
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (metaLine.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metaLine,
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
