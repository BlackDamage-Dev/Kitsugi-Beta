package com.kitsugi.animelist.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.KitsugiRanking
import com.kitsugi.animelist.data.remote.KitsugiScoreStat
import com.kitsugi.animelist.data.remote.KitsugiStats
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border

/**
 * Detay sayfası "Grafikler" sekmesi.
 *
 * Üç bölüm tek bir görsel dil kullanır: başlıkta vurgu renginde ikon kutusu, gövde
 * `KitsugiColors.Surface` kartı ve vurgu rengiyle türetilmiş (tema gradyanına duyarlı)
 * grafikler. Durum renkleri uygulamanın kendi paletinden gelir; puan çubukları
 * kullanıcının tema vurgusuna göre ölçeklenir (koyu → açık), böylece ekran temayla
 * bütünleşir. Yatay kaydırma yok: durum lejantı sarar, puan çubukları kartı doldurur.
 */
@Composable
fun StatsTabContent(
    state: DetailTabState<KitsugiStats?>,
    source: String = ""
) {
    val isTurkish = isTurkishLocale()

    when (state) {
        is DetailTabState.Loading -> {
            DetailStatsLoadingSkeleton()
        }
        is DetailTabState.Error -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isTurkish) stringResource(R.string.ui_statistics_load_failed) else "Failed to load statistics.",
                    color = KitsugiColors.AccentRed,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                DetailTabErrorCodeChip(platformErrorCode = state.platformErrorCode)
            }
        }
        is DetailTabState.Success -> {
            val stats = state.data
            if (stats == null) {
                NoStatsText(isTurkish)
            } else {
                val statusSlices = buildStatusSlices(stats)
                val hasAnything = stats.rankings.isNotEmpty() ||
                    statusSlices.isNotEmpty() ||
                    stats.scoreDistribution.isNotEmpty()

                if (!hasAnything) {
                    NoStatsText(isTurkish)
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        if (stats.rankings.isNotEmpty()) {
                            RankingsCard(stats.rankings, isTurkish)
                        }
                        if (statusSlices.isNotEmpty()) {
                            StatusDistributionCard(statusSlices, isTurkish)
                        }
                        if (stats.scoreDistribution.isNotEmpty()) {
                            ScoreDistributionCard(stats.scoreDistribution, isTurkish)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoStatsText(isTurkish: Boolean) {
    Text(
        text = if (isTurkish) {
            stringResource(R.string.inline_no_statistics_data_found_6c1750a)
        } else {
            "No statistics available."
        },
        color = KitsugiColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

// ── Kart iskeleti ────────────────────────────────────────────────────────────

@Composable
private fun StatsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun StatsCardHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: String? = null
) {
    val accent = LocalKitsugiAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── 1. Sıralama ──────────────────────────────────────────────────────────────

@Composable
private fun RankingsCard(rankings: List<KitsugiRanking>, isTurkish: Boolean) {
    StatsCard {
        StatsCardHeader(
            icon = Icons.EmojiEvents,
            title = stringResource(R.string.detail_stats_rankings_title),
            subtitle = stringResource(R.string.detail_stats_rankings_subtitle)
        )
        Spacer(modifier = Modifier.height(4.dp))
        rankings.forEachIndexed { index, ranking ->
            if (index > 0) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = KitsugiColors.Border.copy(alpha = 0.18f),
                    modifier = Modifier.padding(start = 52.dp)
                )
            }
            RankingRow(ranking, isTurkish)
        }
    }
}

@Composable
private fun RankingRow(ranking: KitsugiRanking, isTurkish: Boolean) {
    val isPopular = isPopularRanking(ranking)
    val tone = if (isPopular) KitsugiColors.AccentRed else KitsugiColors.AccentOrange

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = 0.13f))
                .border(1.dp, tone.copy(alpha = 0.38f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#${ranking.rank}",
                color = tone,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatRankingContext(ranking, isTurkish),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPopular) Icons.FavoriteBorder else Icons.StarBorder,
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(
                        if (isPopular) R.string.detail_stats_rank_popular else R.string.detail_stats_rank_rated
                    ),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── 2. Durum dağılımı ────────────────────────────────────────────────────────

private data class StatusSlice(
    val label: String,
    val count: Int,
    val color: Color
)

@Composable
private fun buildStatusSlices(stats: KitsugiStats): List<StatusSlice> {
    val watching = stats.watching ?: 0
    val completed = stats.completed ?: 0
    val planned = stats.planned ?: 0
    val dropped = stats.dropped ?: 0
    val paused = stats.paused ?: 0
    val slices = mutableListOf<StatusSlice>()
    if (watching > 0) {
        slices += StatusSlice(stringResource(R.string.detail_stats_status_watching), watching, StatusWatching)
    }
    if (completed > 0) {
        slices += StatusSlice(stringResource(R.string.detail_stats_status_completed), completed, StatusCompleted)
    }
    if (planned > 0) {
        slices += StatusSlice(stringResource(R.string.detail_stats_status_planned), planned, StatusPlanned)
    }
    if (dropped > 0) {
        slices += StatusSlice(stringResource(R.string.detail_stats_status_dropped), dropped, StatusDropped)
    }
    if (paused > 0) {
        slices += StatusSlice(stringResource(R.string.detail_stats_status_paused), paused, StatusPaused)
    }
    return slices
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusDistributionCard(slices: List<StatusSlice>, isTurkish: Boolean) {
    val total = slices.sumOf { it.count }
    StatsCard {
        StatsCardHeader(
            icon = Icons.DonutLarge,
            title = stringResource(R.string.detail_stats_status_title),
            subtitle = stringResource(R.string.detail_stats_status_subtitle, formatNumber(total))
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(KitsugiColors.SurfaceSoft)
        ) {
            slices.forEachIndexed { index, slice ->
                if (index > 0) Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(slice.count.toFloat())
                        .background(slice.color)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.forEach { slice -> StatusLegendChip(slice, total, isTurkish) }
        }
    }
}

@Composable
private fun StatusLegendChip(slice: StatusSlice, total: Int, isTurkish: Boolean) {
    val share = if (total > 0) slice.count * 100.0 / total else 0.0
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(slice.color.copy(alpha = 0.12f))
            .border(1.dp, slice.color.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(slice.color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = slice.label,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatNumber(slice.count),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = formatPercent(share, isTurkish),
            color = slice.color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── 3. Puan dağılımı ─────────────────────────────────────────────────────────

@Composable
private fun ScoreDistributionCard(distribution: List<KitsugiScoreStat>, isTurkish: Boolean) {
    val amounts = distribution.associate { it.score to it.amount }
    val scores = if (amounts.keys.any { it > 10 }) {
        (10..100 step 10).toList()
    } else {
        (1..10).toList()
    }
    val maxVotes = (scores.maxOfOrNull { amounts[it] ?: 0 } ?: 0).coerceAtLeast(1)
    val totalVotes = distribution.sumOf { it.amount }
    val average = if (totalVotes > 0) {
        distribution.sumOf { it.score.toLong() * it.amount }.toDouble() / totalVotes
    } else {
        0.0
    }

    StatsCard {
        StatsCardHeader(
            icon = Icons.BarChart,
            title = stringResource(R.string.detail_stats_score_title),
            subtitle = if (totalVotes > 0) {
                stringResource(
                    R.string.detail_stats_score_subtitle,
                    formatNumber(totalVotes),
                    formatDecimal(average, isTurkish)
                )
            } else {
                null
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            scores.forEach { score ->
                ScoreBarColumn(
                    score = score,
                    amount = amounts[score] ?: 0,
                    maxVotes = maxVotes,
                    isTurkish = isTurkish,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScoreBarColumn(
    score: Int,
    amount: Int,
    maxVotes: Int,
    isTurkish: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = LocalKitsugiAccent.current
    val ratio = (amount.toFloat() / maxVotes.coerceAtLeast(1)).coerceIn(0f, 1f)
    val isTop = amount > 0 && amount >= maxVotes
    // Tema vurgusundan türetilen ölçek: en yüksek puan tam vurgu renginde, diğerleri
    // tema gradyanına uyumlu biçimde daha yumuşak. Sabit gökkuşağı paleti kaldırıldı.
    val barColor = if (isTop) accent else lerp(accent.copy(alpha = 0.45f), accent, ratio)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (amount > 0) formatCompact(amount, isTurkish) else "",
            color = if (isTop) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SCORE_BAR_AREA_HEIGHT)
                .clip(RoundedCornerShape(9.dp))
                .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.55f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (amount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .fillMaxHeight(ratio.coerceAtLeast(0.06f))
                        .clip(
                            RoundedCornerShape(
                                topStart = 7.dp,
                                topEnd = 7.dp,
                                bottomStart = 3.dp,
                                bottomEnd = 3.dp
                            )
                        )
                        .background(barColor)
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = "$score",
            color = if (isTop) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Tema paleti ──────────────────────────────────────────────────────────────

/** Durum renkleri uygulamanın vurgu paletinden seçildi (neon AniList renkleri yerine). */
private val StatusWatching = Color(0xFF34D399)
private val StatusCompleted = Color(0xFF60A5FA)
private val StatusPlanned = Color(0xFF818CF8)
private val StatusDropped = Color(0xFFFB7185)
private val StatusPaused = Color(0xFF94A3B8)

private val SCORE_BAR_AREA_HEIGHT = 108.dp

// ── Biçimlendirme ────────────────────────────────────────────────────────────

private fun isTurkishLocale(): Boolean =
    java.util.Locale.getDefault().language.equals("tr", ignoreCase = true)

private fun isPopularRanking(ranking: KitsugiRanking): Boolean =
    ranking.type.equals("POPULAR", ignoreCase = true) ||
        ranking.context.lowercase().contains("popular")

private fun formatRankingContext(ranking: KitsugiRanking, isTurkish: Boolean): String {
    val ctxLower = ranking.context.lowercase().trim()
    val isPopular = isPopularRanking(ranking)
    val isAllTime = ranking.allTime || ctxLower.contains("all time")

    val season = when (ranking.season?.uppercase() ?: run {
        when {
            ctxLower.contains("winter") -> "WINTER"
            ctxLower.contains("spring") -> "SPRING"
            ctxLower.contains("summer") -> "SUMMER"
            ctxLower.contains("fall") -> "FALL"
            else -> null
        }
    }) {
        "WINTER", "KIŞ" -> if (isTurkish) "Kış" else "Winter"
        "SPRING", "İLKBAHAR" -> if (isTurkish) "İlkbahar" else "Spring"
        "SUMMER", "YAZ" -> if (isTurkish) "Yaz" else "Summer"
        "FALL", "SONBAHAR" -> if (isTurkish) "Sonbahar" else "Fall"
        else -> null
    }

    val year = ranking.year
        ?: Regex("\\b(19|20)\\d{2}\\b").find(ranking.context)?.value?.toIntOrNull()

    if (isTurkish) {
        if (isAllTime) {
            return if (isPopular) "Tüm Zamanların En Popüleri" else "Tüm Zamanların En Yüksek Puanlısı"
        }
        if (season != null && year != null) {
            return if (isPopular) "$year $season Sezonunun En Popüleri" else "$year $season Sezonunun En Yüksek Puanlısı"
        }
        if (year != null) {
            return if (isPopular) "$year Yılının En Popüleri" else "$year Yılının En Yüksek Puanlısı"
        }
        return if (isPopular) "En Popüler" else "En Yüksek Puanlı"
    }

    if (isAllTime) {
        return if (isPopular) "Most Popular All Time" else "Highest Rated All Time"
    }
    if (season != null && year != null) {
        return if (isPopular) "Most Popular $season $year" else "Highest Rated $season $year"
    }
    if (year != null) {
        return if (isPopular) "Most Popular $year" else "Highest Rated $year"
    }
    return if (isPopular) "Most Popular" else "Highest Rated"
}

private fun localeFor(isTurkish: Boolean): java.util.Locale =
    if (isTurkish) java.util.Locale("tr", "TR") else java.util.Locale.US

private fun formatNumber(number: Int): String =
    java.text.NumberFormat.getInstance(localeFor(isTurkishLocale())).format(number)

/** Grafik eksenlerinde dar kolonlara sığan kısa sayı: `6,7B` / `81B` / `1,2Mn`. */
private fun formatCompact(number: Int, isTurkish: Boolean): String {
    fun scaled(value: Double, unit: String): String {
        val decimals = if (value < 10) 1 else 0
        return String.format(localeFor(isTurkish), "%.${decimals}f%s", value, unit)
    }
    return when {
        number >= 1_000_000 -> scaled(number / 1_000_000.0, if (isTurkish) "Mn" else "M")
        number >= 1_000 -> scaled(number / 1_000.0, if (isTurkish) "B" else "K")
        else -> formatNumber(number)
    }
}

private fun formatDecimal(value: Double, isTurkish: Boolean): String =
    String.format(localeFor(isTurkish), "%.1f", value)

/** Türkçe yazımda yüzde işareti başa gelir: `%81,2` — İngilizcede `81.2%`. */
private fun formatPercent(value: Double, isTurkish: Boolean): String {
    val text = String.format(localeFor(isTurkish), "%.1f", value)
    return if (isTurkish) "%$text" else "$text%"
}
