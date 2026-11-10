package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.repository.StreamInfoResolver
import com.kitsugi.animelist.data.repository.StreamProbe
import com.kitsugi.animelist.data.repository.StreamSorter
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.screens.stream.StreamInfoChips
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.RadioButton
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background

/**
 * KAYNAK sheet'i — **yalnızca sunucu (eklenti akışı) listesini** gösterir.
 *
 * v2.4.341: Kalite seçenekleri ile kaynak listesi ayrıldı. Bu sheet'e oynatıcıdaki
 * kalite butonuna UZUN basarak veya Kalite sheet'indeki "Kaynak" aksiyonuyla
 * geçilir. Kaynak seçimi anında oynatma o kaynağa geçer.
 */
@Composable
fun SourceSheet(
    streamSources: List<StreamSource>,
    selectedIndex: Int,
    isLoading: Boolean,
    onSelect: (Int) -> Unit,
    onOpenQualitySheet: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissSheet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    PlayerSheet(onDismissRequest = onDismissRequest, dismissEvent = dismissSheet) {
        Column(modifier = modifier) {
            TrackSheetTitle(title = stringResource(R.string.player_source_sheet_title)) {
                TextButton(onClick = onOpenQualitySheet) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(imageVector = Icons.Default.HighQuality, contentDescription = null)
                        Text(stringResource(R.string.player_quality_sheet_title))
                    }
                }
            }

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = KitsugiColors.Accent)
                    }
                }

                streamSources.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.player_source_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.45f))
                            .padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextSecondary,
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.player_source_sheet_desc) + " • " +
                            stringResource(R.string.player_source_count, streamSources.size),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextSecondary,
                    )
                    LazyColumn {
                        itemsIndexed(streamSources) { index, source ->
                            SourceRow(
                                source = source,
                                isSelected = index == selectedIndex,
                                onClick = { onSelect(index) },
                            )
                            if (index < streamSources.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceRow(
    source: StreamSource,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Yalnızca kaynağın AÇIKÇA bildirdiği çözünürlük gösterilir (tahmin yok).
    val declaredHeight = StreamInfoResolver.csQualityHeightOrNull(source.qualityValue)
        ?: StreamSorter.parseQualityValue(source.quality)
    val qualityLabel = source.quality?.takeIf { it.isNotBlank() }
        ?: declaredHeight?.let { StreamInfoResolver.heightToLabel(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = source.addonName,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                    fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (qualityLabel != null) {
                    Text(
                        text = qualityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = KitsugiColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (!source.name.isNullOrBlank()) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = KitsugiColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Boyut + seeder/peer + dil + codec rozetleri (kanıt yoksa gizlenir).
            StreamInfoChips(
                stream = source,
                measured = StreamProbe.cached(source.url),
                accentColor = KitsugiColors.Accent,
                compactSwarm = false,
                gap = 4.dp
            )
        }
    }
}
