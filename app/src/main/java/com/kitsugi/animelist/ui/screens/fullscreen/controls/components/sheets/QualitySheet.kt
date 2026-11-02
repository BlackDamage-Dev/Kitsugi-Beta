package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.kitsugi.animelist.core.player.QualityOption
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.RadioButton
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Oynatıcının **Kalite** sayfası.
 *
 * İki bölüm vardır:
 *
 *  1. **Kalite** — açık akışın kendi manifestinden okunan gerçek video kaliteleri
 *     ([options]): HLS master varyantları (URL değiştirilir) ve DASH `Representation`'ları
 *     (motorun çözünürlük tavanı uygulanır). Eklenti kalite bildirmese bile (rapidrame /
 *     closeload → `Qualities.Unknown`) burada `1080p / 720p / 480p` görünür.
 *  2. **Kaynak** — diğer eklenti/kaynak linkleri (eski davranış korunur).
 *
 * Ölçülebilir varyant yoksa uydurma bir liste basılmaz; kullanıcıya nedenini
 * açıklayan metin gösterilir.
 */
@Composable
fun SourcesSheet(
    streamSources: List<StreamSource>,
    selectedIndex: Int,
    isLoading: Boolean,
    onSelect: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    dismissSheet: Boolean = false,
    modifier: Modifier = Modifier,
    /** Açık akışın gerçek video kaliteleri (yüksekten alçağa). */
    options: List<QualityOption> = emptyList(),
    /** Seçili kalite; `null` = Otomatik (adaptif) seçim. */
    selectedOption: QualityOption? = null,
    /** Kalite listesi ölçülüyor mu? (manifest indiriliyor) */
    isLoadingVariants: Boolean = false,
    /** `null` → Otomatik; aksi hâlde seçilen kalite. */
    onSelectVariant: (QualityOption?) -> Unit = {},
    /** "Otomatik" satırının altındaki açıklama (ör. "Kalite profili: 720p"). */
    autoSubtitle: String? = null,
) {
    PlayerSheet(onDismissRequest = onDismissRequest, dismissEvent = dismissSheet) {
        Column(modifier = modifier) {
            val autoLabel = stringResource(R.string.quality_auto)
            TrackSheetTitle(title = stringResource(R.string.quality_sheet_title))

            if (isLoading && streamSources.isEmpty() && options.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = KitsugiColors.Accent)
                }
                return@Column
            }

            LazyColumn {
                // ── Kalite (gerçek video varyantları) ────────────────────────
                when {
                    isLoadingVariants -> item(key = "quality-loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(color = KitsugiColors.Accent)
                        }
                    }

                    options.isEmpty() -> item(key = "quality-empty") {
                        Text(
                            text = stringResource(R.string.quality_no_variants_message),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    else -> {
                        item(key = "quality-auto") {
                            QualityRow(
                                label = autoLabel,
                                subtitle = autoSubtitle,
                                isSelected = selectedOption == null,
                                onClick = { onSelectVariant(null) },
                            )
                        }
                        items(
                            items = options,
                            key = { option -> "quality-${option.key}" },
                        ) { option ->
                            if (option.declaredBySource) {
                                // Eklentinin bildirdiği tek kalite: seçilecek başka bir şey
                                // olmadığı için işaretli gösterilir, tıklama sunucuda kalite
                                // değiştirmez.
                                QualityRow(
                                    label = option.label,
                                    subtitle = stringResource(R.string.quality_declared_by_source),
                                    isSelected = false,
                                    onClick = {},
                                )
                            } else {
                                QualityRow(
                                    label = option.label.ifBlank { autoLabel },
                                    subtitle = option.bandwidthText,
                                    isSelected = selectedOption != null && option == selectedOption,
                                    onClick = { onSelectVariant(option) },
                                )
                            }
                        }
                    }
                }

                // ── Kaynak (eklenti linkleri) ───────────────────────────────
                if (streamSources.isNotEmpty()) {
                    item(key = "sources-header") {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        TrackSheetTitle(title = stringResource(R.string.quality_sheet_sources_title))
                    }
                    itemsIndexed(
                        items = streamSources,
                        key = { index, source -> "source-$index-${source.addonName}-${source.url.orEmpty()}" },
                    ) { index, source ->
                        QualitySourceRow(
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

/** Tek bir kalite satırı (Otomatik veya bir HLS varyantı). */
@Composable
private fun QualityRow(
    label: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QualitySourceRow(
    source: StreamSource,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.addonName,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val declaredQuality = source.quality?.trim()?.takeIf { it.isNotEmpty() }
                ?: source.qualityValue?.let { "${it}p" }
            if (!declaredQuality.isNullOrBlank()) {
                Text(
                    text = declaredQuality,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!source.name.isNullOrBlank()) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
