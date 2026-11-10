package com.kitsugi.animelist.ui.components.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.utils.KitsugiStrings
import java.text.NumberFormat
import java.util.Locale

/** Shared chart palette and card treatment used by detail and profile statistics. */
@Composable
fun KitsugiStatsCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KitsugiColors.Surface)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.25f), shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun KitsugiStatsCardHeader(
    title: String,
    icon: ImageVector = Icons.BarChart,
    subtitle: String? = null,
    trailing: String? = null,
    accentColor: Color = LocalKitsugiAccent.current
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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

@Composable
fun KitsugiStatsMetricTile(
    label: String,
    value: String,
    icon: ImageVector = Icons.BarChart,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalKitsugiAccent.current
) {
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.56f))
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.20f), shape)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(17.dp))
        }
        Text(
            text = value,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

data class KitsugiStatsSegment(
    val label: String,
    val count: Int,
    val color: Color
)

/** Detail-page style segmented bar with wrapped count/percentage legend chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiStatsDistributionBar(
    items: List<KitsugiStatsSegment>,
    modifier: Modifier = Modifier
) {
    val visibleItems = remember(items) { items.filter { it.count > 0 } }
    val total = remember(visibleItems) { visibleItems.sumOf { it.count } }
    if (visibleItems.isEmpty() || total <= 0) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(KitsugiColors.SurfaceSoft)
        ) {
            visibleItems.forEach { item ->
                Box(
                    modifier = Modifier
                        .weight(item.count.toFloat())
                        .fillMaxHeight()
                        .background(item.color)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visibleItems.forEach { item ->
                val percent = item.count * 100.0 / total
                val shape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(item.color.copy(alpha = 0.12f))
                        .border(1.dp, item.color.copy(alpha = 0.28f), shape)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(item.color)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.label,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = numberFormat().format(item.count),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = percentFormat(percent),
                        color = item.color,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class KitsugiStatsBarEntry(
    val label: String,
    val value: Float,
    val color: Color? = null,
    val valueLabel: String? = null
)

/**
 * Responsive vertical distribution chart. A short series fills the card width like the
 * detail-page score chart; long year/genre series stay readable in a horizontal scroller.
 */
@Composable
fun KitsugiStatsBarChart(
    items: List<KitsugiStatsBarEntry>,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalKitsugiAccent.current,
    maxBarHeight: Dp = 108.dp,
    maxVisibleBars: Int = 12
) {
    if (items.isEmpty()) return
    val positiveMax = remember(items) { items.maxOfOrNull { it.value.coerceAtLeast(0f) } ?: 0f }
    val maxValue = positiveMax.takeIf { it > 0f } ?: 1f
    val scrollable = items.size > maxVisibleBars
    val scrollState = rememberScrollState()

    val chartRow = @Composable { fixedWidth: Boolean ->
        Row(
            modifier = if (fixedWidth) Modifier.horizontalScroll(scrollState) else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (fixedWidth) 8.dp else 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            items.forEach { item ->
                val ratio = (item.value.coerceAtLeast(0f) / maxValue).coerceIn(0f, 1f)
                val isTop = item.value > 0f && ratio >= 0.999f
                val barColor = item.color ?: if (isTop) {
                    accentColor
                } else {
                    lerp(accentColor.copy(alpha = 0.42f), accentColor, ratio)
                }
                Column(
                    modifier = if (fixedWidth) Modifier.width(30.dp) else Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.valueLabel ?: formatChartValue(item.value),
                        color = if (isTop) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxBarHeight)
                            .clip(RoundedCornerShape(9.dp))
                            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (item.value > 0f) {
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
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = item.label,
                        color = if (isTop) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = if (isTop) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        chartRow(scrollable)
    }
}

/** Theme-aligned horizontal progress bars for score distributions and single-series stats. */
@Composable
fun KitsugiStatsProgressList(
    items: List<KitsugiStatsSegment>,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalKitsugiAccent.current,
    maxCount: Int = items.maxOfOrNull { it.count } ?: 1
) {
    if (items.isEmpty()) return
    val safeMax = maxCount.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            val ratio = (item.count.toFloat() / safeMax).coerceIn(0f, 1f)
            val color = if (item.color == Color.Unspecified) accentColor else item.color
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = numberFormat().format(item.count),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.72f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

private fun numberFormat(): NumberFormat =
    NumberFormat.getIntegerInstance(KitsugiStrings.locale())

private fun percentFormat(value: Double): String =
    String.format(KitsugiStrings.locale(), "%.1f%%", value)

private fun formatChartValue(value: Float): String {
    if (value >= 1000f) return numberFormat().format(value.toLong())
    return if (value % 1f == 0f) {
        numberFormat().format(value.toInt())
    } else {
        String.format(KitsugiStrings.locale(), "%.1f", value)
    }
}
