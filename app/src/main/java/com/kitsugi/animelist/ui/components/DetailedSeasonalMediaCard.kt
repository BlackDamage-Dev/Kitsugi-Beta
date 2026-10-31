package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun DetailedSeasonalMediaCard(
    result: JikanSearchResult,
    alreadyInList: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleLanguage: String = "ROMAJI",
    blurAdultMedia: Boolean = false,
    onQuickAddClick: (() -> Unit)? = null
) {
    val accentColor = LocalKitsugiAccent.current
    // PERF: başlık metni her recomp'da yeniden hesaplanmasın.
    val displayTitle = remember(result, titleLanguage) { result.getDisplayTitle(titleLanguage) }

    // Yayın geri sayımı — NextAiringFormat ile tüm kaynaklarda aynı:
    // "Bölüm 2 · 2026-10-15 · 6 gün sonra yayında" (yayın tarihi + geri sayım)
    val nextAiringEpisode = result.nextAiringEpisode
    var airingText by remember(nextAiringEpisode) { mutableStateOf("") }

    if (!nextAiringEpisode.isNullOrBlank()) {
        val parsed = remember(nextAiringEpisode) { com.kitsugi.animelist.utils.NextAiringFormat.parse(nextAiringEpisode) }
        if (parsed.isMachineFormat) {
            LaunchedEffect(parsed) {
                while (true) {
                    airingText = com.kitsugi.animelist.utils.NextAiringFormat.chipText(parsed)
                    val targetEpoch = parsed.epoch ?: break
                    val remaining = targetEpoch - System.currentTimeMillis() / 1000L
                    if (remaining <= 0L) break
                    val delayMs = if (remaining < 3600L) 30_000L
                                  else if (remaining < 86400L) 10_000L
                                  else 60_000L
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        } else {
            airingText = parsed.legacyText.orEmpty()
        }
    }

    val fallbackAiringText = remember(result) {
        val typeName = when (result.type) {
            com.kitsugi.animelist.model.MediaType.Anime -> "Anime"
            com.kitsugi.animelist.model.MediaType.Manga -> "Manga"
            com.kitsugi.animelist.model.MediaType.Movie -> "Film"
            com.kitsugi.animelist.model.MediaType.TvShow -> "Dizi"
        }
        if (result.total != null && result.total > 0) {
            "$typeName (${result.total} bölüm)"
        } else {
            result.subtitle.split(", ").firstOrNull() ?: typeName
        }
    }

    // Score format like 86%
    val displayScore = remember(result.rawScoreDouble, result.score) {
        val scoreVal = result.rawScoreDouble ?: result.score?.toDouble()
        if (scoreVal != null && scoreVal > 0) {
            if (scoreVal <= 10.0) {
                val percentage = (scoreVal * 10).toInt()
                "$percentage%"
            } else {
                "${scoreVal.toInt()}%"
            }
        } else {
            null
        }
    }

    // Extract genres from subtitle
    val genresText = remember(result.subtitle) {
        val parts = result.subtitle.split(", ")
        val filtered = parts.filter { part ->
            part != "TV" && part != "Manga" && part != "Anime" && part != "Film" && part != "Dizi" &&
            part != "Special" && part != "OVA" && part != "ONA" && part != "Movie" &&
            part.toIntOrNull() == null
        }
        if (filtered.isNotEmpty()) filtered.joinToString(", ") else result.subtitle
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (alreadyInList) KitsugiColors.AccentGreen.copy(alpha = 0.45f) else KitsugiColors.Border,
                shape = RoundedCornerShape(20.dp)
            )
            .tvClickable(
                shape = RoundedCornerShape(20.dp),
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
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
            // Poster
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KitsugiColors.SurfaceSoft)
            ) {
                KitsugiNsfwImage(
                        model = result.imageUrl,
                        contentDescription = displayTitle,
                        isAdult = result.isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = Modifier.fillMaxSize(),
                        initials = displayTitle
                    )

                if (onQuickAddClick != null) {
                    KitsugiQuickAddButton(
                        inList = alreadyInList,
                        onClick = onQuickAddClick,
                        modifier = Modifier.align(Alignment.TopEnd),
                        size = 24.dp,
                        cornerRadius = 9.dp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Metadata Column
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

                // Airing Countdown or Fallback Text
                Text(
                    text = if (airingText.isNotBlank()) airingText else fallbackAiringText,
                    color = if (airingText.isNotBlank()) KitsugiColors.AccentOrange else KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Score Percentage
                if (displayScore != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Star,
                            contentDescription = stringResource(R.string.detail_stat_score),
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = displayScore,
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Genres Text
                if (genresText.isNotBlank()) {
                    Text(
                        text = genresText,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
