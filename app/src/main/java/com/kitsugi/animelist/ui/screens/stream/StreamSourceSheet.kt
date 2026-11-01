package com.kitsugi.animelist.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.repository.StreamProbe
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.components.addons.FilterChip
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Sıralama modeli
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Akış listesinin sıralama biçimi.
 *
 * v2.4.341: Sıralama artık listede sürekli duran bir satır değil; kaynak panelinin
 * (alt açılır panel) içinde seçilen bir mod. "Kullanışlı" üç mod var:
 *  - [SOURCE_ORDER] eklentilerin kendi verdiği sıra (varsayılan, en hızlı),
 *  - [QUALITY] ölçülen/bildirilen çözünürlük yüksekliğine göre,
 *  - [SIZE] dosya boyutuna göre (torrent kaynaklarında ayırt edici).
 */
enum class StreamSortMode {
    SOURCE_ORDER,
    QUALITY,
    SIZE
}

/** [StreamSortMode] → kısa etiket (paneldeki chip'ler için). */
@Composable
internal fun StreamSortMode.label(): String = when (this) {
    StreamSortMode.SOURCE_ORDER -> stringResource(R.string.stream_sort_source_order)
    StreamSortMode.QUALITY -> stringResource(R.string.stream_sort_quality_short)
    StreamSortMode.SIZE -> stringResource(R.string.stream_sort_size_short)
}

private val STREAM_SIZE_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*(TB|TIB|GB|GIB|MB|MIB)""", RegexOption.IGNORE_CASE)

/** Akışın bildirilen/ölçülen boyut etiketini bayta çevirir. Bilinmiyorsa null. */
internal fun streamSizeBytes(stream: StreamSource): Long? {
    val label = resolveStreamSizeLabel(stream, StreamProbe.cached(stream)) ?: return null
    val match = STREAM_SIZE_REGEX.find(label) ?: return null
    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].uppercase(Locale.ROOT)
    val factor = when {
        unit.startsWith("T") -> 1024L * 1024L * 1024L * 1024L
        unit.startsWith("G") -> 1024L * 1024L * 1024L
        else -> 1024L * 1024L
    }
    return (amount * factor).toLong()
}

/**
 * Eklenti gruplarını bozmadan her grubun içindeki akışları [mode]'a göre sıralar.
 * Varsayılan modda liste hiç kopyalanmaz (gereksiz tahsis yok).
 */
internal fun List<AddonFetchState>.sortedByStreamMode(mode: StreamSortMode): List<AddonFetchState> {
    if (mode == StreamSortMode.SOURCE_ORDER) return this
    // Sıralama SADECE grup içinde kalsaydı "Kalite: yüksekten düşüğe" yanıltıcı olurdu:
    // üstteki eklentinin 720p'si, alttaki eklentinin 4K'sından önce görünürdü.
    // Bu yüzden gruplar da kendi en iyi akış değerine göre sıralanır.
    return map { state ->
        when (mode) {
            // Kanıt yoksa (height == null) en alta düşer, uydurma değer üretilmez.
            StreamSortMode.QUALITY -> state.copy(
                streams = state.streams.sortedByDescending {
                    resolveStreamQuality(it, StreamProbe.cached(it)).height ?: -1
                }
            )
            StreamSortMode.SIZE -> state.copy(
                streams = state.streams.sortedByDescending { streamSizeBytes(it) ?: -1L }
            )
            StreamSortMode.SOURCE_ORDER -> state
        }
    }.sortedByDescending { state ->
        // Dal tipleri tek türde tutulur (Long): Int/Long karışımı when ifadesinde
        // ortak Comparable türü çıkarmayı belirsizleştiriyordu.
        when (mode) {
            StreamSortMode.QUALITY -> state.streams.maxOfOrNull {
                resolveStreamQuality(it, StreamProbe.cached(it)).height?.toLong() ?: -1L
            } ?: -1L
            StreamSortMode.SIZE -> state.streams.maxOfOrNull { streamSizeBytes(it) ?: -1L } ?: -1L
            StreamSortMode.SOURCE_ORDER -> 0L
        }
    }
}

/**
 * Ad bir CloudStream eklentisine mi ait?
 *
 * [StreamViewModel] CS eklentilerini `"⚡ <eklenti adı>"` biçiminde adlandırır; Stremio
 * eklentileri düz addır. Tek eklenti yenileme/doğrulama yolu yalnızca CS tarafında
 * olduğundan panel bu ayrımı yapmak zorunda (aksi halde düğme ölü kalır).
 */
internal fun String.isCloudstreamAddon(): Boolean = trimStart().startsWith("⚡")

/** Panelin okuduğu / değiştirdiği sıralama durumu (tek kaynak). */
internal class StreamSortState(
    val mode: StreamSortMode,
    val onChange: (StreamSortMode) -> Unit
)

internal val LocalStreamSort = compositionLocalOf { StreamSortState(StreamSortMode.SOURCE_ORDER) {} }

// ─────────────────────────────────────────────────────────────────────────────
// Panel tetikleyicisi (arama çubuğunun yanındaki kompakt buton)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Arama alanının hemen yanında duran, kaynak/sıralama panelini açan kompakt buton.
 * Eskiden listede koca bir "Tüm kaynaklar" kartı + ayrı bir "Sıralama" satırı vardı;
 * ikisi tek bir ikon butona indirildi, tüm kontroller panele taşındı.
 */
@Composable
internal fun StreamSourcePanelButton(
    active: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) accentColor.copy(alpha = 0.16f) else KitsugiColors.SurfaceSoft.copy(alpha = 0.9f))
            .border(
                width = 1.dp,
                color = if (active) accentColor.copy(alpha = 0.55f) else KitsugiColors.Border.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .tvClickable(shape = RoundedCornerShape(14.dp)) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = stringResource(R.string.stream_open_source_panel),
            tint = if (active) accentColor else KitsugiColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        if (isLoading) {
            CircularProgressIndicator(
                color = accentColor,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(40.dp)
            )
        }
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Alt açılır panel: kaynaklar + sıralama + eklenti işlemleri
// ─────────────────────────────────────────────────────────────────────────────

/**
 * "Kaynaklar ve sıralama" paneli.
 *
 * İçerik bilinçli olarak eklenti odaklı: hangi eklenti kaç sonuç verdi, hangisi hata
 * verdi / doğrulama istiyor, tek dokunuşla yeniden dene veya doğrula, eklenti
 * ayarlarını aç. Sıralama da burada seçilir — listede sıralama satırı kalmaz.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StreamSourceSheet(
    addonStates: List<AddonFetchState>,
    allStreams: List<StreamSource>,
    selectedAddonFilter: String?,
    accentColor: Color,
    onFilterChange: (String?) -> Unit,
    onVerifyPlugin: ((addonName: String) -> Unit)?,
    onRetryPlugin: ((addonName: String) -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    /** Tüm kaynakları baştan arar (yalnızca CloudStream olmayan eklentiler için tek yol). */
    onRefreshAll: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sort = LocalStreamSort.current
    // Yerel kopyalar: smart-cast güvenliği ve gereksiz parametre yakalama yok.
    val openSettings = onOpenSettings
    // Tek eklenti yenileme/doğrulama yalnızca CloudStream eklentilerinde çalışır
    // (ViewModel eklentiyi "⚡ <ad>" ile eşler). Stremio eklentilerinde bu butonlar
    // sessizce hiçbir şey yapmıyordu; onlar için paneldeki "Yeniden ara" kullanılır.
    val retryPluginCallback = onRetryPlugin
    val verifyPluginCallback = onVerifyPlugin
    val refreshAllCallback = onRefreshAll
    var addonQuery by rememberSaveable { mutableStateOf("") }
    var onlyWithResults by rememberSaveable { mutableStateOf(false) }

    val readyCount = addonStates.count { it.streams.isNotEmpty() }
    val loadingCount = addonStates.count { it.isLoading }
    val failedAddons = addonStates.filter { it.error != null && it.addonName.isCloudstreamAddon() }

    val visibleAddons = remember(addonStates, addonQuery, onlyWithResults) {
        addonStates.filter { state ->
            val matchesQuery = addonQuery.isBlank() ||
                state.addonName.contains(addonQuery.trim(), ignoreCase = true)
            val matchesResults = !onlyWithResults || state.streams.isNotEmpty()
            matchesQuery && matchesResults
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // ── Başlık ────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = accentColor, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stream_panel_title),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.stream_panel_summary, readyCount, addonStates.size, allStreams.size),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (refreshAllCallback != null) {
                    IconButton(
                        onClick = { refreshAllCallback(); onDismiss() },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = accentColor.copy(alpha = 0.16f),
                            contentColor = accentColor
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.stream_panel_refresh_all),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.kapat), tint = KitsugiColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Sıralama ──────────────────────────────────────────────────────
            SheetSectionLabel(stringResource(R.string.stream_panel_sort_title), accentColor)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamSortMode.entries.forEach { mode ->
                    FilterChip(
                        text = mode.label(),
                        selected = sort.mode == mode,
                        onClick = { sort.onChange(mode) },
                        accentColor = accentColor
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Eklentiler başlığı + toplu işlemler ───────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetSectionLabel(stringResource(R.string.stream_panel_addons_title), accentColor)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${addonStates.size}",
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.weight(1f))
                if (openSettings != null) {
                    TextButton(
                        onClick = { onDismiss(); openSettings() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            stringResource(R.string.stream_panel_addon_settings),
                            color = accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (retryPluginCallback != null && failedAddons.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { failedAddons.forEach { state -> retryPluginCallback(state.addonName) } },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "${stringResource(R.string.stream_panel_retry_failed)} (${failedAddons.size})",
                            color = accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Eklenti arama + "yalnızca sonuç verenler" ───────────────────
            if (addonStates.size > 5) {
                OutlinedTextField(
                    value = addonQuery,
                    onValueChange = { addonQuery = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(R.string.stream_search_addons),
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (addonQuery.isNotEmpty()) {
                            IconButton(onClick = { addonQuery = "" }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.temizle), tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        focusedBorderColor = accentColor.copy(alpha = 0.7f),
                        unfocusedBorderColor = KitsugiColors.Border.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    text = stringResource(R.string.stream_panel_only_results),
                    selected = onlyWithResults,
                    onClick = { onlyWithResults = !onlyWithResults },
                    accentColor = accentColor
                )
                Spacer(Modifier.weight(1f))
                if (loadingCount > 0) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stream_source_loading),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Eklenti listesi ──────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item(key = "all_sources") {
                    AddonSheetRow(
                        name = stringResource(R.string.stream_all_sources),
                        status = stringResource(R.string.stream_video_count, allStreams.size),
                        selected = selectedAddonFilter == null,
                        accentColor = accentColor
                    ) {
                        onFilterChange(null)
                        onDismiss()
                    }
                }

                items(visibleAddons, key = { it.addonName }) { state ->
                    val status = when {
                        state.isLoading -> stringResource(R.string.stream_source_loading)
                        state.requiresVerification -> stringResource(R.string.stream_source_verify)
                        state.error != null -> stringResource(R.string.stream_source_error)
                        else -> stringResource(R.string.stream_video_count, state.streams.size)
                    }
                    // Eklenti bazlı işlemler: hata/boş sonuç → yeniden dene,
                    // doğrulama gerekiyorsa → doğrula. Kullanıcı paneli kapatmadan
                    // tek dokunuşla o eklentiyi tekrar çalıştırabilir.
                    val canRetryThisAddon = retryPluginCallback != null && state.addonName.isCloudstreamAddon()
                    val retryAction: (() -> Unit)? =
                        if (canRetryThisAddon &&
                            (state.error != null || (!state.isLoading && state.streams.isEmpty()))
                        ) {
                            { retryPluginCallback(state.addonName) }
                        } else null
                    val verifyAction: (() -> Unit)? =
                        if (verifyPluginCallback != null &&
                            state.requiresVerification &&
                            state.addonName.isCloudstreamAddon()
                        ) {
                            { verifyPluginCallback(state.addonName) }
                        } else null
                    AddonSheetRow(
                        name = state.addonName,
                        status = status,
                        selected = selectedAddonFilter == state.addonName,
                        accentColor = accentColor,
                        isError = state.error != null,
                        onRetry = retryAction,
                        onVerify = verifyAction
                    ) {
                        onFilterChange(state.addonName)
                        onDismiss()
                    }
                }

                if (visibleAddons.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.stream_no_addon_match),
                            modifier = Modifier.padding(vertical = 18.dp),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Paneldeki tek eklenti satırı: ada dokun → yalnızca o eklentiyi filtrele.
 * Sağdaki ikonlar hata durumunda yenileme, doğrulama gerektiğinde doğrulama sunar.
 */
@Composable
private fun AddonSheetRow(
    name: String,
    status: String,
    selected: Boolean,
    accentColor: Color,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onVerify: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = if (selected) accentColor.copy(alpha = 0.14f) else Color.Transparent,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = if (selected) 0.28f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.trim().take(1).uppercase(Locale.ROOT),
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = status,
                        color = if (isError) Color(0xFFE57373) else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(17.dp))
                }
            }
        }
        if (onVerify != null) {
            IconButton(
                onClick = onVerify,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = accentColor)
            ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = stringResource(R.string.stream_source_verify), modifier = Modifier.size(17.dp))
            }
        }
        if (onRetry != null) {
            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = if (isError) Color(0xFFE57373) else KitsugiColors.TextSecondary)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.stream_panel_retry_failed), modifier = Modifier.size(17.dp))
            }
        }
    }
}
