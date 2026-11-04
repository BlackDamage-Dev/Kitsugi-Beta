package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.utils.localizedLabel
import android.content.res.Configuration
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens

import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayScore
import com.kitsugi.animelist.utils.toFriendlySourceLabel

import androidx.compose.ui.unit.Dp

/** Dikey kartta başlık/meta alanının sabit yüksekliği (tüm kartlar aynı boyutta görünsün diye). */
internal val EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT = 182.dp

/** TV kartında başlık/meta alanının sabit yüksekliği. */
internal val EXPLORE_CARD_TV_TEXT_HEIGHT = 76.dp

/** Shared shelf and landscape dimensions for loaded cards and their placeholders. */
internal fun exploreShelfCardWidth(isLandscape: Boolean): Dp =
    if (isLandscape) 260.dp else 180.dp

internal fun exploreLandscapePosterWidth(screenWidthDp: Int): Dp = when {
    screenWidthDp >= 1200 -> 110.dp
    screenWidthDp >= 800 -> 100.dp
    else -> 90.dp
}

internal fun exploreLandscapePosterHeight(screenWidthDp: Int): Dp = when {
    screenWidthDp >= 1200 -> 160.dp
    screenWidthDp >= 800 -> 145.dp
    else -> 130.dp
}

@Composable
fun KitsugiExploreMediaCard(
    result: JikanSearchResult,
    alreadyInList: Boolean = false,
    mediaEntry: com.kitsugi.animelist.model.MediaEntry? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false,
    forceVertical: Boolean = false,
    rankIndex: Int? = null,
    onQuickAddClick: (() -> Unit)? = null
) {
    val accentColor = LocalKitsugiAccent.current
    // PERF: başlık/meta gibi string'ler her recomposition'da yeniden
    // hesaplanmasın; ilgili girdiler değiştiğinde bir kez üretilsin.
    val displayTitle = remember(result, titleLanguage) { result.getDisplayTitle(titleLanguage) }
    val isTv = LocalIsTv.current
    // TV'de her zaman dikey poster, isLandscape yoksayılır
    // Mobilde orijinal landscape/portrait mantığı korunur
    val isLandscape = !isTv && !forceVertical &&
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // TV: KitsugiTvTokens referanslı kompakt shape
    // PERF: RoundedCornerShape her recomp'da new'lenmesin.
    val cardShape = remember(isTv) {
        if (isTv) KitsugiTvTokens.Shapes.posterCard else RoundedCornerShape(24.dp)
    }
    val focusScale = if (isTv) KitsugiTvTokens.Cards.focusedScale else 1.08f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .kitsugiNeonGlow(cardShape)
            .tvClickable(
                shape = cardShape,
                scaleFocused = focusScale,
                onLongClick = onLongClick,
                onClick = onClick
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.Surface
        )
    ) {
        // ── TV: Her zaman kompakt dikey poster (2:3) ─────────────────────────
        if (isTv) {
            Column {
                // Poster görseli — tam genişlik, sabit yükseklik
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KitsugiTvTokens.Cards.posterHeight)
                        .clip(KitsugiTvTokens.Shapes.posterCard)
                        .background(KitsugiColors.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    KitsugiNsfwImage(
                        model = result.imageUrl,
                        contentDescription = displayTitle,
                        isAdult = result.isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = Modifier.fillMaxSize(),
                        initials = displayTitle
                    )

                    KitsugiSourceBadge(
                        source = result.source,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )

                    if (mediaEntry != null) {
                        StatusBadge(
                            status = mediaEntry.status,
                            showIconOnly = true,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                        )
                    }

                    val rankNumber = result.rank ?: rankIndex
                    if (rankNumber != null) {
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
                    }

                    if (onQuickAddClick != null) {
                        // Köşeye yapışık rozet: padding yok, posterin kendi clip'i köşeyi kıvar.
                        KitsugiQuickAddButton(
                            inList = alreadyInList || mediaEntry != null,
                            onClick = onQuickAddClick,
                            modifier = Modifier.align(Alignment.TopEnd),
                            size = 28.dp
                        )
                    }
                }

                // Başlık + meta bilgi
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EXPLORE_CARD_TV_TEXT_HEIGHT)
                        .clipToBounds()
                        .padding(
                            horizontal = KitsugiTvTokens.Spacing.sm,
                            vertical = KitsugiTvTokens.Spacing.sm
                        )
                ) {
                    Text(
                        text = displayTitle,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (mediaEntry != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = entryProgressText(mediaEntry),
                                color = statusColor(mediaEntry.status),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val displayScore = mediaEntry.getDisplayScore(scoreFormat, hideScores)
                            val isUnrated = displayScore == "unrated" || displayScore == "Unrated" || displayScore == "Puanlanmamış" || displayScore == "Puan yok"
                            val scoreLabel = if (isUnrated) "—" else displayScore
                            Spacer(modifier = Modifier.width(4.dp))
                            KitsugiEmojiLabel(
                                label = scoreLabel,
                                icon = if (isUnrated) null else Icons.StarSolid,
                                iconSize = 9.dp,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        EpisodeProgressBar(
                            progress = mediaEntry.progress,
                            total = mediaEntry.total,
                            color = statusColor(mediaEntry.status),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = buildMetaText(result, scoreFormat, hideScores),
                            color = if (alreadyInList) KitsugiColors.AccentGreen else accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        // ── MOBİL LANDSCAPE ──────────────────────────────────────────────────
        } else if (isLandscape) {
            val landscapeScreenWidthDp = LocalConfiguration.current.screenWidthDp
            // Yatay kartlar: satır yüksekliği poster yüksekliğine sabit → tüm kartlar aynı boyutta
            val landscapeRowHeight = exploreLandscapePosterHeight(landscapeScreenWidthDp)
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .height(landscapeRowHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val posterWidth = exploreLandscapePosterWidth(landscapeScreenWidthDp)
                val posterHeight = exploreLandscapePosterHeight(landscapeScreenWidthDp)
                Box(
                    modifier = Modifier
                        .size(width = posterWidth, height = posterHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(KitsugiColors.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    KitsugiNsfwImage(
                        model = result.imageUrl,
                        contentDescription = displayTitle,
                        isAdult = result.isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = Modifier.fillMaxSize(),
                        initials = displayTitle
                    )

                    KitsugiSourceBadge(
                        source = result.source,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )

                    if (mediaEntry != null) {
                        StatusBadge(
                            status = mediaEntry.status,
                            showIconOnly = true,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                        )
                    }

                    val rankNumber = result.rank ?: rankIndex
                    if (rankNumber != null) {
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
                    }

                    if (onQuickAddClick != null) {
                        KitsugiQuickAddButton(
                            inList = alreadyInList || mediaEntry != null,
                            onClick = onQuickAddClick,
                            modifier = Modifier.align(Alignment.TopEnd),
                            size = 26.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = displayTitle,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = result.subtitle,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (mediaEntry != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entryProgressText(mediaEntry),
                                color = statusColor(mediaEntry.status),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val displayScore = mediaEntry.getDisplayScore(scoreFormat, hideScores)
                            val isUnrated = displayScore == "unrated" || displayScore == "Unrated" || displayScore == "Puanlanmamış" || displayScore == "Puan yok"
                            val scoreLabel = if (isUnrated) "—" else displayScore
                            Spacer(modifier = Modifier.width(6.dp))
                            KitsugiEmojiLabel(
                                label = scoreLabel,
                                icon = if (isUnrated) null else Icons.StarSolid,
                                iconSize = 9.dp,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        EpisodeProgressBar(
                            progress = mediaEntry.progress,
                            total = mediaEntry.total,
                            color = statusColor(mediaEntry.status),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = buildMetaText(result, scoreFormat, hideScores, includeTypeAndYear = false),
                            color = if (alreadyInList) {
                                KitsugiColors.AccentGreen
                            } else {
                                accentColor
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Yayın geri sayımı — tüm kaynaklar (nextAiringEpisode dolu ise): tarih + geri sayım
                    if (!result.nextAiringEpisode.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(5.dp))
                        NextAiringChip(
                            nextAiringEpisode = result.nextAiringEpisode
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(KitsugiColors.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    KitsugiNsfwImage(
                        model = result.imageUrl,
                        contentDescription = displayTitle,
                        isAdult = result.isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = Modifier.fillMaxSize(),
                        initials = displayTitle
                    )

                    KitsugiSourceBadge(
                        source = result.source,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )

                    if (mediaEntry != null) {
                        StatusBadge(
                            status = mediaEntry.status,
                            showIconOnly = false,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                        )
                    }

                    val rankNumber = result.rank ?: rankIndex
                    if (rankNumber != null) {
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
                    }

                    if (onQuickAddClick != null) {
                        KitsugiQuickAddButton(
                            inList = alreadyInList || mediaEntry != null,
                            onClick = onQuickAddClick,
                            modifier = Modifier.align(Alignment.TopEnd),
                            size = 28.dp
                        )
                    }
                }

                // Tüm kartlar aynı boyutta: metin alanı sabit yükseklikte (kısa/uzun başlık fark etmez)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT)
                        .clipToBounds()
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = displayTitle,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = result.subtitle,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (mediaEntry != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entryProgressText(mediaEntry),
                                color = statusColor(mediaEntry.status),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val displayScore = mediaEntry.getDisplayScore(scoreFormat, hideScores)
                            val isUnrated = displayScore == "unrated" || displayScore == "Unrated" || displayScore == "Puanlanmamış" || displayScore == "Puan yok"
                            val scoreLabel = if (isUnrated) "—" else displayScore
                            Spacer(modifier = Modifier.width(6.dp))
                            KitsugiEmojiLabel(
                                label = scoreLabel,
                                icon = if (isUnrated) null else Icons.StarSolid,
                                iconSize = 9.dp,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        EpisodeProgressBar(
                            progress = mediaEntry.progress,
                            total = mediaEntry.total,
                            color = statusColor(mediaEntry.status),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = buildMetaText(result, scoreFormat, hideScores, includeTypeAndYear = false),
                            color = accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Yayın geri sayımı — tüm kaynaklar (nextAiringEpisode dolu ise): tarih + geri sayım
                    if (!result.nextAiringEpisode.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        NextAiringChip(
                            nextAiringEpisode = result.nextAiringEpisode
                        )
                    }
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

private fun buildMetaText(
    result: JikanSearchResult,
    scoreFormat: String,
    hideScores: Boolean,
    includeTypeAndYear: Boolean = true
): String {
    val parts = buildList {
        if (includeTypeAndYear) {
            val typeLabel = when (result.type) {
                MediaType.Anime -> "ANIME"
                MediaType.Manga -> "MANGA"
                MediaType.Movie -> "FİLM"
                MediaType.TvShow -> "DİZİ"
            }
            add(typeLabel)
            if (result.year != null) add(result.year.toString())
        }
        if (!hideScores) {
            val scoreStr = result.getDisplayScore(scoreFormat, hideScores)
            val isUnrated = scoreStr.isEmpty() || scoreStr == "N/A" || scoreStr == "0" || scoreStr == "unrated" || scoreStr == "Unrated" || scoreStr == "Puanlanmamış" || scoreStr == "Puan yok"
            val formattedScore = if (isUnrated) {
                "—"
            } else if (!scoreStr.contains("★") && !scoreStr.contains("☆") && !scoreStr.contains("😊") && !scoreStr.contains("😐") && !scoreStr.contains("🙁")) {
                "★ $scoreStr"
            } else {
                scoreStr
            }
            add(formattedScore)
        }

        val sourceLabel = result.source.toFriendlySourceLabel()
        // Eklenti (CS3) sonuçlarında sahte ID gösterme — yalnızca eklenti adı yeterlidir.
        if (result.cs3Url.isNullOrBlank()) add("$sourceLabel #${result.malId}") else add(sourceLabel)
        if (result.isAdult) add("+18")
    }

    return parts.joinToString(" • ")
}