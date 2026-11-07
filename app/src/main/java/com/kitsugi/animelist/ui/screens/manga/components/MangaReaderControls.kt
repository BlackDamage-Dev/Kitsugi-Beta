package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaChapterGroup
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaReaderPrefs
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text

/**
 * Manga okuyucusunun üst/alt barları ve ayar panelleri.
 *
 * Bu dosya, daha önce `MangaReaderScreen.kt` içine gömülü olan ~450 satırlık
 * UI bloğunu (üst bar, alt bar, bölüm şeridi, ayar paneli) dışarı taşır:
 *  • ekran dosyası yalnızca durum/akış yönetir,
 *  • aynı `FilterChip` bloğu 3 kez kopyalanmıyor ([MangaChoiceChips]),
 *  • TV okuyucusu ve telefon okuyucusu aynı görsel dili kullanabiliyor.
 */

private val BarBackground = Color(0xFF161622).copy(alpha = 0.95f)
private val BarPillBackground = Color(0xFF26263B)
/**
 * Okuyucu vurgusu — uygulamanın **tema vurgu rengi** ile birebir aynıdır.
 * (Önceden sabit mor `0xFF7C4DFF` idi; kullanıcı temasını değiştirdiğinde
 * okuyucu aykırı kalıyordu.)
 */
private val ReaderAccent: Color
    @Composable
    get() = LocalKitsugiAccent.current

// ─── Üst bar ─────────────────────────────────────────────────────────────────

@Composable
fun MangaReaderTopBar(
    title: String,
    chapterName: String,
    fullscreen: Boolean,
    /** Açık bölümün çevirmeni — başlıkta küçük rozet olarak gösterilir. */
    scanlator: String? = null,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenChapterList: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.geri),
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chapterName,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!scanlator.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        // Vurgu rengi kullanıcı temasından geldiği için yazı rengi
                        // `OnAccent` (vurgu üstünde otomatik kontrast) seçilir.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ReaderAccent)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = scanlator,
                                color = KitsugiColors.OnAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp),
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onOpenChapterList) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.manga_reader_chapters),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    imageVector = if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (fullscreen) {
                        stringResource(R.string.manga_reader_exit_fullscreen)
                    } else {
                        stringResource(R.string.manga_reader_fullscreen)
                    },
                    tint = Color.White,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = stringResource(R.string.okuyucu_ayarlari),
                    tint = Color.White,
                )
            }
        }
    }
}

// ─── Alt bar ─────────────────────────────────────────────────────────────────

@Composable
fun MangaReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    readingMode: ReadingMode,
    fitMode: MangaFitMode,
    fullscreen: Boolean,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onSeek: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenChapterList: () -> Unit,
    onCycleReadingMode: () -> Unit,
    onCycleFitMode: () -> Unit,
    onRotate: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BarBackground)
            .navigationBarsPadding()
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ── 1. Bölüm gezinme + sayfa şeridi ──────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousChapter, enabled = hasPreviousChapter) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.onceki_bolum),
                    tint = if (hasPreviousChapter) Color.White else Color.White.copy(alpha = 0.3f),
                )
            }

            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(BarPillBackground)
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${currentPage + 1}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    var dragValue by remember { mutableStateOf<Float?>(null) }
                    Slider(
                        value = dragValue ?: (currentPage + 1).toFloat(),
                        onValueChange = { dragValue = it },
                        onValueChangeFinished = {
                            dragValue?.let { value -> onSeek(value.toInt() - 1) }
                            dragValue = null
                        },
                        valueRange = 1f..pageCount.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = ReaderAccent,
                            inactiveTrackColor = Color(0xFF3D3D5C),
                            thumbColor = ReaderAccent,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )

                    Text(
                        text = "$pageCount",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            IconButton(onClick = onNextChapter, enabled = hasNextChapter) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.sonraki_bolum),
                    tint = if (hasNextChapter) Color.White else Color.White.copy(alpha = 0.3f),
                )
            }
        }

        // ── 2. Hızlı işlemler ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderIconButton(
                icon = Icons.AutoMirrored.Filled.List,
                label = stringResource(R.string.manga_reader_chapters),
                onClick = onOpenChapterList,
            )
            ReaderIconButton(
                icon = readingMode.icon(),
                label = stringResource(R.string.manga_reader_reading_mode, readingMode.readerLabel()),
                onClick = onCycleReadingMode,
            )
            ReaderIconButton(
                icon = Icons.Default.CropFree,
                label = stringResource(R.string.manga_reader_fit_mode, fitMode.readerLabel()),
                onClick = onCycleFitMode,
            )
            ReaderIconButton(
                icon = Icons.Default.ScreenRotation,
                label = stringResource(R.string.ekran_dondurme),
                onClick = onRotate,
            )
            ReaderIconButton(
                icon = if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                label = if (fullscreen) {
                    stringResource(R.string.manga_reader_exit_fullscreen)
                } else {
                    stringResource(R.string.manga_reader_fullscreen)
                },
                onClick = onToggleFullscreen,
            )
            ReaderIconButton(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.ayarlar),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun ReaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White)
    }
}

// ─── Ayar paneli ─────────────────────────────────────────────────────────────

@Composable
fun MangaReaderSettingsSheet(
    readingMode: ReadingMode,
    fitMode: MangaFitMode,
    colorFilterType: ColorFilterType,
    brightness: Float,
    prefs: MangaReaderPrefs,
    onReadingModeSelected: (ReadingMode) -> Unit,
    onFitModeSelected: (MangaFitMode) -> Unit,
    onColorFilterSelected: (ColorFilterType) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onBrightnessReset: () -> Unit,
    onPrefsChanged: ((MangaReaderPrefs) -> MangaReaderPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.inline_reader_settings_82b7841),
                color = KitsugiColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            SectionLabel(stringResource(R.string.manga_reading_direction))
            MangaChoiceChips(
                options = listOf(
                    ReadingMode.RightToLeft to stringResource(R.string.manga_direction_rtl),
                    ReadingMode.LeftToRight to stringResource(R.string.manga_direction_ltr),
                    ReadingMode.Vertical to stringResource(R.string.manga_reader_mode_vertical),
                    ReadingMode.Webtoon to stringResource(R.string.manga_reader_mode_webtoon),
                ),
                selected = readingMode,
                onSelect = onReadingModeSelected,
            )

            SectionLabel(stringResource(R.string.inline_image_fit_mode_3eed46c))
            MangaChoiceChips(
                options = listOf(
                    MangaFitMode.FitScreen to stringResource(R.string.manga_fit_screen),
                    MangaFitMode.FitWidth to stringResource(R.string.manga_fit_width),
                    MangaFitMode.FitHeight to stringResource(R.string.manga_fit_height),
                    MangaFitMode.Crop to stringResource(R.string.manga_fit_crop),
                    MangaFitMode.Original to stringResource(R.string.manga_fit_original),
                ),
                selected = fitMode,
                onSelect = onFitModeSelected,
            )

            SectionLabel(stringResource(R.string.manga_color_filter))
            MangaChoiceChips(
                options = listOf(
                    ColorFilterType.Normal to stringResource(R.string.manga_filter_normal),
                    ColorFilterType.Warm to stringResource(R.string.manga_filter_warm),
                    ColorFilterType.Cool to stringResource(R.string.manga_filter_cool),
                    ColorFilterType.Grayscale to stringResource(R.string.manga_filter_grayscale),
                    ColorFilterType.Sepia to stringResource(R.string.manga_filter_sepia),
                    ColorFilterType.Invert to stringResource(R.string.manga_filter_inverted),
                ),
                selected = colorFilterType,
                onSelect = onColorFilterSelected,
            )

            SectionLabel(stringResource(R.string.inline_reader_brightness_e5e9f7a))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.BrightnessLow,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChanged,
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = ReaderAccent,
                        inactiveTrackColor = KitsugiColors.SurfaceStrong,
                        thumbColor = ReaderAccent,
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = onBrightnessReset, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = stringResource(R.string.slider_reset_to_default),
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Default.BrightnessHigh,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Okuyucu davranışları ────────────────────────────────────────
            ReaderToggleRow(
                title = stringResource(R.string.manga_reader_fullscreen_default),
                description = stringResource(R.string.manga_reader_fullscreen_default_desc),
                checked = prefs.immersiveByDefault,
                onCheckedChange = { value ->
                    onPrefsChanged { it.copy(immersiveByDefault = value) }
                },
            )
            ReaderToggleRow(
                title = stringResource(R.string.manga_reader_keep_screen_on),
                description = stringResource(R.string.manga_reader_keep_screen_on_desc),
                checked = prefs.keepScreenOn,
                onCheckedChange = { value ->
                    onPrefsChanged { it.copy(keepScreenOn = value) }
                },
            )
            ReaderToggleRow(
                title = stringResource(R.string.manga_reader_tap_zones),
                description = stringResource(R.string.manga_reader_tap_zones_desc),
                checked = prefs.tapZonesEnabled,
                onCheckedChange = { value ->
                    onPrefsChanged { it.copy(tapZonesEnabled = value) }
                },
            )
            if (prefs.tapZonesEnabled) {
                ReaderToggleRow(
                    title = stringResource(R.string.manga_reader_invert_tap_zones),
                    description = stringResource(R.string.manga_reader_invert_tap_zones_desc),
                    checked = prefs.invertTapZones,
                    onCheckedChange = { value ->
                        onPrefsChanged { it.copy(invertTapZones = value) }
                    },
                )
            }
            ReaderToggleRow(
                title = stringResource(R.string.manga_reader_volume_keys),
                description = stringResource(R.string.manga_reader_volume_keys_desc),
                checked = prefs.volumeKeysEnabled,
                onCheckedChange = { value ->
                    onPrefsChanged { it.copy(volumeKeysEnabled = value) }
                },
            )
            ReaderToggleRow(
                title = stringResource(R.string.manga_reader_page_number),
                description = stringResource(R.string.manga_reader_page_number_desc),
                checked = prefs.showPageNumber,
                onCheckedChange = { value ->
                    onPrefsChanged { it.copy(showPageNumber = value) }
                },
            )
            ReaderToggleRow(
                title = stringResource(R.string.manga_reader_mirror),
                description = stringResource(R.string.manga_reader_mirror_desc),
                checked = prefs.mirrorPages,
                onCheckedChange = { value ->
                    onPrefsChanged { it.copy(mirrorPages = value) }
                },
            )
            ReaderToggleRow(
                title = stringResource(R.string.manga_reader_chapter_transition),
                description = stringResource(R.string.manga_reader_chapter_transition_desc),
                checked = prefs.showChapterTransition,
                onCheckedChange = { value ->
                    onPrefsChanged { it.copy(showChapterTransition = value) }
                },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = KitsugiColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
    )
}

@Composable
private fun <T> MangaChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    // Fazla seçenek satırı taşırmasın: 3'lü gruplar hâlinde alt alta dizilir.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ReaderAccent.copy(alpha = 0.2f),
                            selectedLabelColor = ReaderAccent,
                            containerColor = KitsugiColors.SurfaceStrong,
                            labelColor = KitsugiColors.TextPrimary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = KitsugiColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, color = KitsugiColors.TextMuted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ─── Bölüm seçici ────────────────────────────────────────────────────────────

@Composable
fun MangaChapterPickerSheet(
    groups: List<MangaChapterGroup>,
    currentChapterUrl: String,
    preferredScanlator: String?,
    isLoading: Boolean,
    onSelect: (MangaChapter) -> Unit,
    onDismiss: () -> Unit,
) {
    var expandedKeys by remember { mutableStateOf(emptySet<String>()) }
    // Sabit 460.dp yerine ekranın yüksekliğine göre: yatay telefonda taşmaz,
    // büyük ekranda daha çok bölüm görünür.
    val pickerMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.62f

    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.manga_reader_chapters),
                color = KitsugiColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            if (groups.any { it.hasMultipleVariants }) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.manga_picker_grouped_hint),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }

            when {
                isLoading && groups.isEmpty() -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = ReaderAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.inline_loading_page_list_31ccd61),
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                }

                groups.isEmpty() -> {
                    Text(
                        stringResource(R.string.manga_reader_no_chapters),
                        color = KitsugiColors.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = pickerMaxHeight)) {
                        items(groups, key = { it.key }) { group ->
                            val isCurrentGroup = group.containsUrl(currentChapterUrl)
                            val multiple = group.hasMultipleVariants
                            val isExpanded = multiple && group.key in expandedKeys

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isCurrentGroup) ReaderAccent.copy(alpha = 0.18f) else Color.Transparent
                                        )
                                        .clickable {
                                            if (multiple) {
                                                expandedKeys = if (isExpanded) {
                                                    expandedKeys - group.key
                                                } else {
                                                    expandedKeys + group.key
                                                }
                                            } else {
                                                onSelect(group.variants.first())
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = group.displayName,
                                        color = if (isCurrentGroup) ReaderAccent else KitsugiColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrentGroup) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (multiple) {
                                        Text(
                                            text = stringResource(R.string.manga_chapter_variants, group.variantCount),
                                            color = if (isCurrentGroup) ReaderAccent else KitsugiColors.TextMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = KitsugiColors.TextMuted,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    } else if (isCurrentGroup) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = ReaderAccent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    group.variants.forEach { chapter ->
                                        val isCurrent = chapter.url == currentChapterUrl
                                        val isPreferred = !preferredScanlator.isNullOrBlank() &&
                                            chapter.scanlator?.equals(preferredScanlator, ignoreCase = true) == true
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isCurrent) ReaderAccent.copy(alpha = 0.12f) else Color.Transparent
                                                )
                                                .clickable { onSelect(chapter) }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(
                                                        if (isCurrent) ReaderAccent else KitsugiColors.TextMuted.copy(alpha = 0.6f)
                                                    )
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = chapter.scanlator ?: stringResource(R.string.manga_choice_unknown_scanlator),
                                                color = if (isCurrent) ReaderAccent else KitsugiColors.TextSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (isPreferred) {
                                                Text(
                                                    text = stringResource(R.string.manga_choice_preferred),
                                                    color = ReaderAccent.copy(alpha = 0.9f),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            } else if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = ReaderAccent,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(KitsugiColors.Border.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Etiket yardımcıları ─────────────────────────────────────────────────────

internal fun ReadingMode.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    ReadingMode.RightToLeft -> Icons.AutoMirrored.Filled.MenuBook
    ReadingMode.LeftToRight -> Icons.Default.SwapHoriz
    ReadingMode.Vertical -> Icons.Default.ViewAgenda
    ReadingMode.Webtoon -> Icons.Default.ViewStream
}

@Composable
internal fun ReadingMode.readerLabel(): String = when (this) {
    ReadingMode.RightToLeft -> stringResource(R.string.manga_direction_rtl)
    ReadingMode.LeftToRight -> stringResource(R.string.manga_direction_ltr)
    ReadingMode.Vertical -> stringResource(R.string.manga_reader_mode_vertical)
    ReadingMode.Webtoon -> stringResource(R.string.manga_reader_mode_webtoon)
}

@Composable
internal fun MangaFitMode.readerLabel(): String = when (this) {
    MangaFitMode.FitScreen -> stringResource(R.string.manga_fit_screen)
    MangaFitMode.FitWidth -> stringResource(R.string.manga_fit_width)
    MangaFitMode.FitHeight -> stringResource(R.string.manga_fit_height)
    MangaFitMode.Crop -> stringResource(R.string.manga_fit_crop)
    MangaFitMode.Original -> stringResource(R.string.manga_fit_original)
    MangaFitMode.Stretch -> "Uzat"
    MangaFitMode.SmartFit -> "Akıllı"
}
