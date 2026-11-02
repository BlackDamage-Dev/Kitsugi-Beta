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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.player.QualityOption as ManifestQualityOption
import com.kitsugi.animelist.data.repository.StreamInfoResolver
import com.kitsugi.animelist.data.repository.StreamSorter
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.RadioButton
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background

/**
 * KALİTE sheet'i — **yalnızca kalite seçeneklerini** listeler.
 *
 * v2.4.341 düzeltmesi: Bu sheet bir süre "Kalite / Kaynak" başlığıyla kaynak
 * (sunucu) listesini de içeriyordu; iki farklı seçim aynı ekrana karışmıştı.
 * Artık:
 *  - Kalite  → bu dosya ([QualitySheet]); seçenekler kaynağın
 *    AÇIKÇA bildirdiği çözünürlüklerden veya akışın HLS/DASH manifestinden üretilir.
 *  - Kaynak  → [SourceSheet]; eklenti/sunucu listesi ayrı sheet.
 *
 * Kalite seçildiğinde o çözünürlüğe sahip en iyi kaynak oynatılır; kullanıcı
 * kaynak seçmek isterse başlıktaki "Kaynak listesi" aksiyonunu kullanır.
 */
@Composable
fun QualitySheet(
    streamSources: List<StreamSource>,
    selectedIndex: Int,
    isLoading: Boolean,
    onSelectQuality: (sourceIndex: Int) -> Unit,
    onOpenSourceSheet: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissSheet: Boolean = false,
    modifier: Modifier = Modifier,
    /** Açık akışın gerçek video kaliteleri (yüksekten alçağa HLS/DASH). */
    options: List<ManifestQualityOption> = emptyList(),
    /** Seçili kalite; `null` = Otomatik (adaptif) seçim. */
    selectedOption: ManifestQualityOption? = null,
    /** Kalite listesi ölçülüyor mu? (manifest indiriliyor) */
    isLoadingVariants: Boolean = false,
    /** `null` → Otomatik; aksi hâlde seçilen kalite. */
    onSelectVariant: (ManifestQualityOption?) -> Unit = {},
    /** "Otomatik" satırının altındaki açıklama (ör. "Kalite profili: 720p"). */
    autoSubtitle: String? = null,
) {
    val declaredOptions = buildQualityOptions(streamSources)
    val autoOption = declaredOptions.firstOrNull { it.isAuto }
    val qualityOptions = declaredOptions.filterNot { it.isAuto }
    val selectableManifestVariants = options.filter { it.isSelectable }

    // Mevcut kaynak "otomatik" (en iyi kalite) seçimiyle aynı mı?
    val autoActive = autoOption != null && autoOption.targetSourceIndex == selectedIndex
    val currentQuality = qualityOptions.firstOrNull {
        !autoActive && it.targetSourceIndex == selectedIndex
    }

    PlayerSheet(onDismissRequest = onDismissRequest, dismissEvent = dismissSheet) {
        Column(modifier = modifier) {
            TrackSheetTitle(title = stringResource(R.string.player_quality_sheet_title)) {
                TextButton(onClick = onOpenSourceSheet) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(imageVector = Icons.Default.SwapVert, contentDescription = null)
                        Text(stringResource(R.string.player_source_sheet_title))
                    }
                }
            }

            if (isLoading || isLoadingVariants) {
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

            val hasNoQualities = qualityOptions.isEmpty() && selectableManifestVariants.isEmpty()
            if (hasNoQualities) {
                // Kaynaklar çözünürlük bildirmiyor ve manifest varyantı yok → kalite listesi YOK.
                // Kullanıcıya neden boş olduğu ve kaynak listesine nasıl gideceği anlatılır.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.45f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.player_quality_none_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.player_quality_none_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextSecondary,
                    )
                    TextButton(onClick = onOpenSourceSheet) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(imageVector = Icons.Default.SwapVert, contentDescription = null)
                            Text(stringResource(R.string.player_quality_open_sources))
                        }
                    }
                }
                return@Column
            }

            val autoLabel = stringResource(R.string.quality_auto)
            LazyColumn {
                if (selectableManifestVariants.isNotEmpty()) {
                    // ── Açık akışın kendi HLS / DASH manifest varyantları ──────────
                    item(key = "manifest_auto") {
                        QualityRow(
                            label = autoLabel,
                            subtitle = autoSubtitle,
                            isSelected = selectedOption == null,
                            onClick = { onSelectVariant(null) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    itemsIndexed(
                        items = selectableManifestVariants,
                        key = { _, variant -> "manifest_${variant.key}" },
                    ) { index, variant ->
                        QualityRow(
                            label = variant.label.ifBlank { autoLabel },
                            subtitle = variant.bandwidthText,
                            isSelected = selectedOption != null && variant == selectedOption,
                            onClick = { onSelectVariant(variant) },
                        )
                        if (index < selectableManifestVariants.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                } else {
                    // ── Kaynakların açıkça bildirdiği çözünürlük seçenekleri ───────
                    item(key = "quality_auto") {
                        val option = autoOption ?: return@item
                        QualityOptionRow(
                            option = option,
                            isSelected = autoActive,
                            onClick = { onSelectQuality(option.targetSourceIndex) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    itemsIndexed(qualityOptions, key = { _, option -> "quality_${option.qualityValue}" }) { index, option ->
                        QualityOptionRow(
                            option = option,
                            isSelected = !autoActive && currentQuality?.qualityValue == option.qualityValue,
                            onClick = { onSelectQuality(option.targetSourceIndex) },
                        )
                        if (index < qualityOptions.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Geriye dönük uyumluluk köprüsü.
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
    options: List<ManifestQualityOption> = emptyList(),
    selectedOption: ManifestQualityOption? = null,
    isLoadingVariants: Boolean = false,
    onSelectVariant: (ManifestQualityOption?) -> Unit = {},
    autoSubtitle: String? = null,
) {
    QualitySheet(
        streamSources = streamSources,
        selectedIndex = selectedIndex,
        isLoading = isLoading,
        onSelectQuality = onSelect,
        onOpenSourceSheet = {},
        onDismissRequest = onDismissRequest,
        dismissSheet = dismissSheet,
        modifier = modifier,
        options = options,
        selectedOption = selectedOption,
        isLoadingVariants = isLoadingVariants,
        onSelectVariant = onSelectVariant,
        autoSubtitle = autoSubtitle,
    )
}

/**
 * Bir kalite seçeneği. [qualityValue] kaynağın **bildirdiği** çözünürlük yüksekliğidir;
 * -1 ise "Otomatik (en iyi kalite)" satırıdır.
 */
data class DeclaredQualityOption(
    val label: String,
    val description: String?,
    val qualityValue: Int,
    val sourceCount: Int,
    val targetSourceIndex: Int,
    val isAuto: Boolean = false,
)

/**
 * Kaynak listesinden kalite seçenekleri üretir.
 *
 * Kurallar:
 *  - Hiçbir kaynak çözünürlük bildirmiyorsa yalnızca "Otomatik" döner ve
 *    arayüz kalite listesini boş kabul eder (bkz. [QualitySheet]).
 *  - Seçenek başına hedef kaynak: o kalitedeki mevcut seçili kaynak; yoksa
 *    listedeki ilk kaynak (liste zaten kaliteye göre sıralı gelir).
 *  - Uydurma yok: yalnızca `qualityValue` veya etiketten AÇIKÇA okunabilen
 *    çözünürlükler kullanılır.
 */
fun buildQualityOptions(streamSources: List<StreamSource>): List<DeclaredQualityOption> {
    if (streamSources.isEmpty()) return emptyList()

    fun declaredHeight(source: StreamSource): Int? =
        StreamInfoResolver.csQualityHeightOrNull(source.qualityValue)
            ?: StreamSorter.parseQualityValue(source.quality)

    val known = streamSources.mapIndexedNotNull { index, source ->
        declaredHeight(source)?.let { index to it }
    }

    // En iyi kaynak: en yüksek bildirilen çözünürlük, eşitlikte listedeki ilk kaynak.
    val bestIndex = known.maxByOrNull { it.second }?.first ?: 0

    val grouped = known.groupBy(
        keySelector = { it.second },
        valueTransform = { it.first },
    ).toSortedMap(reverseOrder())

    val options = mutableListOf(
        DeclaredQualityOption(
            label = "",
            description = null,
            qualityValue = -1,
            sourceCount = streamSources.size,
            targetSourceIndex = bestIndex,
            isAuto = true,
        )
    )

    grouped.forEach { (height, indices) ->
        options += DeclaredQualityOption(
            label = StreamInfoResolver.heightToLabel(height),
            description = null,
            qualityValue = height,
            sourceCount = indices.size,
            targetSourceIndex = indices.first(),
        )
    }

    return options
}

@Composable
private fun QualityOptionRow(
    option: DeclaredQualityOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (option.isAuto) stringResource(R.string.player_quality_auto_best) else option.label
    val description = if (option.isAuto) {
        stringResource(R.string.player_quality_auto_best_desc)
    } else {
        stringResource(R.string.player_quality_declared_desc) + " • " +
            stringResource(R.string.player_quality_source_count, option.sourceCount)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = KitsugiColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
