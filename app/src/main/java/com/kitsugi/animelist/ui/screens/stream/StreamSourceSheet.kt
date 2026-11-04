package com.kitsugi.animelist.ui.screens.stream

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.repository.StreamInfoResolver
import com.kitsugi.animelist.data.repository.StreamProbe
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Akış sıralama modu — kaynak panelindeki tek kompakt bardan seçilir.
 *
 *  - [SOURCE_ORDER] varsayılan: eklentilerin döndürdüğü ham sıra,
 *  - [QUALITY] ölçülen/bildirilen çözünürlük yüksekliğine göre,
 *  - [SIZE] dosya boyutuna göre (torrent kaynaklarında ayırt edici).
 */
enum class StreamSortMode {
    SOURCE_ORDER,
    QUALITY,
    SIZE
}

@Composable
internal fun sortModeLabel(mode: StreamSortMode): String = stringResource(
    when (mode) {
        StreamSortMode.SOURCE_ORDER -> R.string.stream_sort_source
        StreamSortMode.QUALITY -> R.string.stream_sort_quality_short
        StreamSortMode.SIZE -> R.string.stream_sort_size
    }
)

@Composable
internal fun sortModeShortLabel(mode: StreamSortMode): String = stringResource(
    when (mode) {
        StreamSortMode.SOURCE_ORDER -> R.string.stream_sort_source_short
        StreamSortMode.QUALITY -> R.string.stream_sort_quality_short
        StreamSortMode.SIZE -> R.string.stream_sort_size
    }
)

/** [StreamSortMode] → kısa etiket (geriye dönük uyumluluk). */
@Composable
internal fun StreamSortMode.label(): String = sortModeLabel(this)

/** Akışın bildirilen/ölçülen boyut etiketini bayta çevirir. Bilinmiyorsa 0L. */
internal fun streamSizeBytes(stream: StreamSource): Long =
    StreamInfoResolver.resolveSizeBytes(stream, StreamProbe.cached(stream))

/**
 * Eklenti gruplarını bozmadan her grubun içindeki akışları [mode]'a göre sıralar.
 * Varsayılan modda liste hiç kopyalanmaz (gereksiz tahsis yok).
 */
internal fun List<AddonFetchState>.sortedByStreamMode(mode: StreamSortMode): List<AddonFetchState> {
    if (mode == StreamSortMode.SOURCE_ORDER) return this
    return map { state ->
        when (mode) {
            StreamSortMode.QUALITY -> state.copy(
                streams = state.streams.sortedByDescending {
                    resolveStreamQuality(it, StreamProbe.cached(it)).height ?: -1
                }
            )
            StreamSortMode.SIZE -> state.copy(
                streams = state.streams.sortedByDescending {
                    StreamInfoResolver.resolveSizeBytes(it, StreamProbe.cached(it))
                }
            )
            StreamSortMode.SOURCE_ORDER -> state
        }
    }.sortedByDescending { state ->
        when (mode) {
            StreamSortMode.QUALITY -> state.streams.maxOfOrNull {
                resolveStreamQuality(it, StreamProbe.cached(it)).height?.toLong() ?: -1L
            } ?: -1L
            StreamSortMode.SIZE -> state.streams.maxOfOrNull {
                StreamInfoResolver.resolveSizeBytes(it, StreamProbe.cached(it))
            } ?: -1L
            StreamSortMode.SOURCE_ORDER -> 0L
        }
    }
}

/** Ad bir CloudStream eklentisine mi ait? */
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
            imageVector = Icons.Tune,
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
// Alt açılır panel: TEK 48 dp kontrol barı düzeni
// ─────────────────────────────────────────────────────────────────────────────

/**
 * "Kaynaklar ve sıralama" paneli — TEK 48 dp kontrol barı düzeni.
 *
 * Başlık, özet, sıralama çipi, arama, yenileme (rozetli), filtre ve kapat kontrolleri
 * 48 dp'lik tek kontrol barında toplanır; kaynak listesi barın hemen altından başlar.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    /** Tüm kaynakları baştan arar. */
    onRefreshAll: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var addonQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var onlyWithResults by rememberSaveable { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val loadingCount = addonStates.count { it.isLoading }
    val readyCount = addonStates.count { it.streams.isNotEmpty() }
    val errorStates = remember(addonStates) { addonStates.filter { it.error != null } }

    val visible = remember(addonStates, addonQuery, onlyWithResults) {
        addonStates.filter { state ->
            state.addonName.contains(addonQuery.trim(), ignoreCase = true) &&
                (!onlyWithResults || state.streams.isNotEmpty())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Background,
        dragHandle = { BottomSheetDefaults.DragHandle(modifier = Modifier.height(22.dp)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            // ── TEK kontrol barı ────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = KitsugiColors.Surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, KitsugiColors.Border.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (searchActive) {
                        // Arama modu: bar aynı yükseklikte arama alanına dönüşür.
                        Icon(
                            Icons.Search,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.padding(start = 8.dp).size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            value = addonQuery,
                            onValueChange = { addonQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextPrimary),
                            cursorBrush = SolidColor(accentColor),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (addonQuery.isEmpty()) {
                                        Text(
                                            stringResource(R.string.stream_search_addons),
                                            color = KitsugiColors.TextMuted,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        IconButton(
                            onClick = { addonQuery = ""; searchActive = false },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Close,
                                stringResource(R.string.kapat),
                                tint = KitsugiColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    } else {
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.stream_sources_title),
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(
                                    R.string.stream_source_summary_compact,
                                    readyCount,
                                    addonStates.size,
                                    allStreams.size
                                ),
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StreamSortChip(accentColor = accentColor)
                        SheetBarIconButton(
                            icon = Icons.Search,
                            contentDescription = stringResource(R.string.stream_search_addons)
                        ) { searchActive = true }
                        if (loadingCount > 0) {
                            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = accentColor, strokeWidth = 1.8.dp)
                            }
                        } else {
                            SheetBarIconButton(
                                icon = Icons.Refresh,
                                contentDescription = if (errorStates.isNotEmpty()) {
                                    stringResource(R.string.stream_retry_errors, errorStates.size)
                                } else {
                                    stringResource(R.string.stream_refresh_all)
                                },
                                badgeCount = errorStates.size,
                                badgeColor = accentColor
                            ) {
                                if (errorStates.isNotEmpty()) {
                                    errorStates.forEach { onRetryPlugin?.invoke(it.addonName) }
                                } else {
                                    if (onRefreshAll != null) {
                                        onRefreshAll()
                                    } else {
                                        addonStates.forEach { onRetryPlugin?.invoke(it.addonName) }
                                    }
                                }
                            }
                        }
                        Box {
                            SheetBarIconButton(
                                icon = Icons.MoreVert,
                                contentDescription = stringResource(R.string.stream_more_options),
                                showDot = onlyWithResults,
                                dotColor = accentColor
                            ) { overflowOpen = true }
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                                containerColor = KitsugiColors.Surface
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.stream_only_with_results),
                                            color = KitsugiColors.TextPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    trailingIcon = {
                                        if (onlyWithResults) {
                                            Icon(
                                                Icons.Check,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = { onlyWithResults = !onlyWithResults }
                                )
                                if (onOpenSettings != null) {
                                    HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.4f))
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.stream_addon_settings),
                                                color = KitsugiColors.TextPrimary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Settings,
                                                contentDescription = null,
                                                tint = KitsugiColors.TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            overflowOpen = false
                                            onDismiss()
                                            onOpenSettings()
                                        }
                                    )
                                }
                            }
                        }
                        SheetBarIconButton(
                            icon = Icons.Close,
                            contentDescription = stringResource(R.string.kapat)
                        ) { onDismiss() }
                    }
                }
            }

            // ── Kaynak listesi: barın hemen altında, maksimum alan ──────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item(key = "all") {
                    SourcePickerRow(
                        name = stringResource(R.string.stream_all_sources),
                        status = stringResource(R.string.stream_video_count, allStreams.size),
                        selected = selectedAddonFilter == null,
                        accentColor = accentColor
                    ) { onFilterChange(null) }
                }
                items(visible, key = { it.addonName }) { state ->
                    val status = when {
                        state.isLoading -> stringResource(R.string.stream_source_loading)
                        state.requiresVerification -> stringResource(R.string.stream_source_verify)
                        state.error != null -> stringResource(R.string.stream_source_error)
                        else -> stringResource(R.string.stream_video_count, state.streams.size)
                    }
                    SourcePickerRow(
                        name = state.addonName,
                        status = status,
                        selected = selectedAddonFilter == state.addonName,
                        accentColor = accentColor,
                        isError = state.error != null,
                        trailing = {
                            when {
                                state.requiresVerification && onVerifyPlugin != null ->
                                    SheetRowAction(Icons.VerifiedUser, stringResource(R.string.stream_source_verify), accentColor) {
                                        onVerifyPlugin(state.addonName)
                                    }
                                state.error != null && onRetryPlugin != null ->
                                    SheetRowAction(Icons.Refresh, stringResource(R.string.yenile), accentColor) {
                                        onRetryPlugin(state.addonName)
                                    }
                            }
                        }
                    ) { onFilterChange(state.addonName) }
                }
                if (visible.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.stream_no_addon_match),
                            modifier = Modifier.padding(16.dp),
                            color = KitsugiColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/** Sıralama çipi: mevcut modu gösterir, dokununca 3 seçenekli menü açar (tek bar içinde). */
@Composable
private fun StreamSortChip(accentColor: Color) {
    val sort = LocalStreamSort.current
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { menuOpen = true },
            shape = RoundedCornerShape(9.dp),
            color = accentColor.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
        ) {
            Row(
                Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Sort, contentDescription = null, tint = accentColor, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    sortModeShortLabel(sort.mode),
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Icon(Icons.ArrowDropDown, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = KitsugiColors.Surface
        ) {
            StreamSortMode.entries.forEach { mode ->
                val active = mode == sort.mode
                DropdownMenuItem(
                    text = {
                        Text(
                            sortModeLabel(mode),
                            color = if (active) accentColor else KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = {
                        if (active) {
                            Icon(Icons.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        }
                    },
                    onClick = {
                        sort.onChange(mode)
                        menuOpen = false
                    }
                )
            }
        }
    }
}

/** Kontrol barı ikon düğmesi; isteğe bağlı sayı rozeti (hata adedi) veya nokta (filtre aktif). */
@Composable
private fun SheetBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    badgeCount: Int = 0,
    badgeColor: Color = Color.Transparent,
    showDot: Boolean = false,
    dotColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    Box {
        IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
            Icon(icon, contentDescription, tint = KitsugiColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
        if (badgeCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .sizeIn(minWidth = 14.dp, minHeight = 14.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badgeCount > 9) "9+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (showDot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

/** Liste satırı içi küçük eylem (yeniden dene / doğrula). */
@Composable
private fun SheetRowAction(icon: ImageVector, contentDescription: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SourcePickerRow(
    name: String,
    status: String,
    selected: Boolean,
    accentColor: Color,
    isError: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    status,
                    color = if (isError) KitsugiColors.AccentRed else KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(4.dp))
            }
            if (selected) {
                Icon(Icons.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}
