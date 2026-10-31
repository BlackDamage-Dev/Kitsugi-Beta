package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import com.kitsugi.animelist.model.MediaType
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/** Varsayılan olarak gösterilecek çip sayısı; gerisi "Daha fazla" ile açılır. */
private const val COLLAPSED_CHIP_COUNT = 8

/**
 * Arama geçmişi bölümü — kompakt tasarım.
 * • Kısa (1 harf) ve tekrar eden sorgular gizlenir.
 * • Varsayılan olarak tek bloğa sığan [COLLAPSED_CHIP_COUNT] çip gösterilir; geri kalanı açılır.
 * • Çipler ince ve küçük; dokunma alanları korunur.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchHistorySection(
    history: List<SearchHistoryItem>,
    onHistoryItemClick: (SearchHistoryItem) -> Unit,
    onRemoveItem: (SearchHistoryItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current

    // Gürültüyü azalt: boş/1 harfli sorguları at, büyük-küçük harf farkı olan tekrarları birleştir
    val visibleHistory = remember(history) {
        val seen = HashSet<String>()
        history.filter { item ->
            val q = item.query.trim()
            q.length >= 2 && seen.add(q.lowercase())
        }
    }

    if (visibleHistory.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    val canCollapse = visibleHistory.size > COLLAPSED_CHIP_COUNT
    val shownItems = if (expanded || !canCollapse) visibleHistory
                     else visibleHistory.take(COLLAPSED_CHIP_COUNT)
    val hiddenCount = visibleHistory.size - shownItems.size

    Column(modifier = modifier.fillMaxWidth()) {
        // Başlık satırı (ince)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.History,
                    contentDescription = null,
                    tint = KitsugiColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.son_aramalar),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canCollapse) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (expanded) "Daha az" else "+$hiddenCount",
                            color = accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                        KitsugiChevronIcon(
                            expanded = expanded,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Delete,
                        contentDescription = stringResource(R.string.tumunu_temizle),
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            shownItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .tvClickable(shape = RoundedCornerShape(8.dp)) { onHistoryItemClick(item) }
                        .padding(start = 8.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.query.trim(),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )

                    IconButton(
                        onClick = { onRemoveItem(item) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.kaldir),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
