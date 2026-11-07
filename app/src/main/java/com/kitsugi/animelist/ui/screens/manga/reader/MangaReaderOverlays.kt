package com.kitsugi.animelist.ui.screens.manga.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.*
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.screens.manga.MangaReaderViewModel
import com.kitsugi.animelist.ui.utils.requestFocusAfterFrames

internal enum class ReaderOverlay { None, Settings, Chapters, GoToPage, Shortcuts }

@Composable
internal fun MangaReaderOverlayHost(
    overlay: ReaderOverlay,
    model: MangaReaderViewModel,
    ui: MangaReaderViewModel.UiState,
    pageCount: Int,
    onDismiss: () -> Unit,
    onSeek: (Int) -> Unit,
    onRotate: () -> Unit,
    onOpenOverlay: (ReaderOverlay) -> Unit = {},
) {
    when (overlay) {
        ReaderOverlay.None -> Unit
        ReaderOverlay.Settings -> ReaderSettings(model, ui, onRotate, onDismiss, onOpenShortcuts = { onOpenOverlay(ReaderOverlay.Shortcuts) })
        ReaderOverlay.Chapters -> {
            val current = ui.chapterList.indexOfFirst { it.url == ui.currentChapter.url }.coerceAtLeast(0)
            val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
            KitsugiSheetOrDialog(onDismiss = onDismiss, innerScrollState = list) {
                ReaderPanel(onDismiss = onDismiss, toggleKeys = listOf(Key.C)) {
                    ReaderPanelTitle(stringResource(R.string.reader_chapters))
                    if (ui.isLoadingChapters) CircularProgressIndicator(Modifier.padding(24.dp))
                    else if (ui.error != null) Text(ui.error, color = Color(0xFFEF9A9A), modifier = Modifier.padding(16.dp))
                    else LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                        items(ui.chapterList.ifEmpty { listOf(ui.currentChapter) }, key = { it.url }) { chapter ->
                            TextButton(
                                onClick = { model.goToChapter(chapter); onDismiss() },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            ) {
                                Text(
                                    chapter.name, color = if (chapter.url == ui.currentChapter.url) Color(0xFFB39DFF) else Color.White,
                                    fontWeight = if (chapter.url == ui.currentChapter.url) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        ReaderOverlay.GoToPage -> GoToPageDialog(ui.currentPageIndex, pageCount, onDismiss, onSeek)
        ReaderOverlay.Shortcuts -> ReaderShortcuts(ui.readingMode, onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettings(
    model: MangaReaderViewModel,
    ui: MangaReaderViewModel.UiState,
    onRotate: () -> Unit,
    onDismiss: () -> Unit,
    onOpenShortcuts: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    var editLongTapGrid by remember { mutableStateOf(false) }
    KitsugiSheetOrDialog(onDismiss = onDismiss, innerColumnScrollState = scroll) {
        ReaderPanel(
            onDismiss = onDismiss,
            modifier = Modifier.verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
            toggleKeys = listOf(Key.S),
        ) {
            ReaderPanelTitle(stringResource(R.string.okuyucu_ayarlari))
            ReaderToggle(stringResource(R.string.reader_series_mode), ui.seriesModeOverride, model::setSeriesModeOverride)
            ReaderSettingTitle(stringResource(R.string.manga_reading_direction))
            ReaderChoices(
                listOf(
                    ReadingMode.RightToLeft to stringResource(R.string.manga_direction_rtl),
                    ReadingMode.LeftToRight to stringResource(R.string.manga_direction_ltr),
                    ReadingMode.Vertical to stringResource(R.string.manga_direction_vertical),
                    ReadingMode.Webtoon to "Webtoon",
                ), ui.readingMode, model::setReadingMode,
            )
            if (ui.readingMode == ReadingMode.LeftToRight || ui.readingMode == ReadingMode.RightToLeft) {
                ReaderToggle(stringResource(R.string.reader_double_page), ui.doublePage, model::setDoublePage)
                if (ui.doublePage) {
                    ReaderToggle(stringResource(R.string.reader_cover_alone), ui.coverAlone, model::setCoverAlone)
                    ReaderToggle("Sayfaları birleştir (kitap görünümü)", ui.stitchDualPages, model::setStitchDualPages)
                }
            }
            if (ui.readingMode == ReadingMode.Webtoon) {
                Text(stringResource(R.string.reader_webtoon_hint), color = Color.White.copy(.65f), fontSize = 12.sp)
                ReaderSettingTitle(stringResource(R.string.reader_side_padding))
                ReaderChoices(listOf(0, 16, 32, 64).map { it to "$it dp" }, ui.webtoon.sidePaddingDp) {
                    model.setWebtoonPreferences(ui.webtoon.copy(sidePaddingDp = it))
                }
                ReaderSettingTitle(stringResource(R.string.reader_page_gap))
                ReaderChoices(listOf(0, 4, 16, 32).map { it to "$it dp" }, ui.webtoon.pageGapDp) {
                    model.setWebtoonPreferences(ui.webtoon.copy(pageGapDp = it))
                }
                ReaderSettingTitle(stringResource(R.string.reader_scroll_amount))
                ReaderChoices(listOf(50, 75, 90, 100).map { it to "$it%" }, ui.webtoon.scrollPercent) {
                    model.setWebtoonPreferences(ui.webtoon.copy(scrollPercent = it))
                }
                ReaderToggle(stringResource(R.string.reader_tap_scroll), ui.webtoon.tapToScroll) {
                    model.setWebtoonPreferences(ui.webtoon.copy(tapToScroll = it))
                }
                var speed by remember(ui.autoScrollSpeed) { mutableFloatStateOf(ui.autoScrollSpeed.toFloat()) }
                ReaderSettingTitle("Otomatik kaydırma hızı: ${speed.toInt()} dp/sn")
                Slider(
                    value = speed, valueRange = 20f..500f,
                    onValueChange = { speed = it },
                    onValueChangeFinished = {
                        model.setAutoScrollSpeed(speed.toInt())
                        model.setWebtoonPreferences(ui.webtoon.copy(autoScrollDpPerSecond = speed.toInt()))
                    },
                )
                ReaderToggle("Webtoon yakınlaştırma", ui.webtoonZoom, model::setWebtoonZoom)
            } else {
                ReaderSettingTitle(stringResource(R.string.inline_image_fit_mode_3eed46c))
                ReaderChoices(
                    listOf(
                        MangaFitMode.FitScreen to stringResource(R.string.manga_fit_screen),
                        MangaFitMode.FitWidth to stringResource(R.string.manga_fit_width),
                        MangaFitMode.FitHeight to stringResource(R.string.manga_fit_height),
                    ), ui.fitMode, model::setFitMode,
                )
            }
            ReaderToggle("Kontroller gizliyken sayfa sayacı", ui.hiddenPageCounter, model::setHiddenPageCounter)
            TextButton(onClick = { onDismiss(); onOpenShortcuts() }) {
                Text("Klavye Kısayolları", color = Color(0xFFB39DFF))
            }
            ReaderSettingTitle(stringResource(R.string.manga_color_filter))
            ReaderChoices(
                listOf(
                    ColorFilterType.Normal to stringResource(R.string.manga_filter_normal),
                    ColorFilterType.Grayscale to stringResource(R.string.manga_filter_grayscale),
                    ColorFilterType.Sepia to stringResource(R.string.reader_sepia),
                    ColorFilterType.Invert to stringResource(R.string.manga_filter_inverted),
                ), ui.colorFilterType, model::setColorFilter,
            )
            ReaderSettingTitle(stringResource(R.string.reader_saturation))
            ReaderChoices(listOf(0, 50, 100, 150, 200).map { it to "$it%" }, ui.saturation, model::setSaturation)
            ReaderSettingTitle(stringResource(R.string.reader_contrast))
            ReaderChoices(listOf(50, 75, 100, 125, 150, 200).map { it to "$it%" }, ui.contrast, model::setContrast)
            ReaderSettingTitle(stringResource(R.string.reader_hue))
            ReaderChoices(listOf(-180, -90, 0, 90, 180).map { it to "$it°" }, ui.hue, model::setHue)
            ReaderSettingTitle(stringResource(R.string.reader_background))
            ReaderChoices(
                listOf(
                    "Black" to stringResource(R.string.reader_black),
                    "DarkGray" to stringResource(R.string.reader_dark_gray),
                    "White" to stringResource(R.string.reader_white),
                ), ui.webtoon.background,
            ) { model.setWebtoonPreferences(ui.webtoon.copy(background = it)) }
            ReaderSettingTitle(stringResource(R.string.manga_brightness))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = ui.customBrightness, valueRange = .1f..1f,
                    onValueChange = model::setCustomBrightness, modifier = Modifier.weight(1f),
                )
                Text("${(ui.customBrightness * 100).toInt()}%", color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
            ReaderSettingTitle(stringResource(R.string.reader_preload_ahead))
            ReaderChoices(
                listOf(-1, 0, 1, 2, 3, 5).map { count ->
                    count to (if (count < 0) stringResource(R.string.reader_preload_auto) else count.toString())
                }, ui.preloadAhead, model::setPreloadAhead,
            )
            ReaderToggle(stringResource(R.string.reader_skip_read_chapters), ui.skipReadChapters, model::setSkipReadChapters)
            ReaderToggle(stringResource(R.string.reader_pause_progress), ui.pauseProgressSaving, model::setPauseProgressSaving)
            ReaderToggle(stringResource(R.string.reader_crop_borders), ui.cropBorders, model::setCropBorders)
            ReaderToggle(stringResource(R.string.reader_volume_keys), ui.webtoon.volumeKeys) {
                model.setWebtoonPreferences(ui.webtoon.copy(volumeKeys = it))
            }
            if (ui.webtoon.volumeKeys) {
                ReaderToggle(stringResource(R.string.reader_invert_volume_keys), ui.invertVolumeKeys, model::setInvertVolumeKeys)
            }
            ReaderToggle(stringResource(R.string.reader_page_number), ui.webtoon.showPageNumber) {
                model.setWebtoonPreferences(ui.webtoon.copy(showPageNumber = it))
            }
            ReaderToggle(stringResource(R.string.reader_keep_screen_on), ui.webtoon.keepScreenOn) {
                model.setWebtoonPreferences(ui.webtoon.copy(keepScreenOn = it))
            }
            if (ui.readingMode != ReadingMode.Webtoon) {
                ReaderSettingTitle(stringResource(R.string.reader_wide_rotation))
                ReaderChoices(
                    listOf(
                        "Off" to stringResource(R.string.reader_wide_off),
                        "Clockwise" to stringResource(R.string.reader_wide_clockwise),
                        "CounterClockwise" to stringResource(R.string.reader_wide_counterclockwise),
                    ), ui.wideRotation,
                ) { model.setWideRotation(it, false) }
                ReaderSettingTitle(stringResource(R.string.reader_tap_layout))
                ReaderChoices(
                    listOf(
                        "Default" to stringResource(R.string.reader_tap_default),
                        "LeftHand" to stringResource(R.string.reader_tap_left_hand),
                        "RightHand" to stringResource(R.string.reader_tap_right_hand),
                        "Custom" to stringResource(R.string.reader_tap_custom),
                    ), ui.tapLayout, model::setTapLayout,
                )
                if (ui.tapLayout == "Custom") {
                    ReaderToggle(stringResource(R.string.reader_tap_edit_long), editLongTapGrid) { editLongTapGrid = it }
                    val grid = if (editLongTapGrid) ui.longTapGrid else ui.tapGrid
                    for (row in 0..2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (column in 0..2) {
                                val index = row * 3 + column
                                val action = when (grid[index]) {
                                    'P' -> R.string.reader_tap_previous
                                    'N' -> R.string.reader_tap_next
                                    'M' -> R.string.reader_tap_menu
                                    else -> R.string.reader_tap_none
                                }
                                FilterChip(
                                    selected = false,
                                    modifier = Modifier.weight(1f),
                                    onClick = { model.cycleTapGridCell(index, editLongTapGrid) },
                                    label = { Text(stringResource(action), fontSize = 11.sp) },
                                )
                            }
                        }
                    }
                    TextButton(onClick = model::resetTapGrids) {
                        Text(stringResource(R.string.reader_tap_reset), color = Color(0xFFB39DFF))
                    }
                }
                ReaderToggle(stringResource(R.string.reader_invert_taps), ui.invertTapZones, model::setInvertTapZones)
            }
            TextButton(onClick = onRotate) { Text(stringResource(R.string.ekran_dondurme), color = Color(0xFFB39DFF)) }
            TextButton(onClick = { model.setWebtoonPreferences(MangaWebtoonPreferences()); model.setCustomBrightness(1f) }) {
                Text(stringResource(R.string.reader_reset_preferences), color = Color(0xFFB39DFF))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Dialogs/sheets are separate windows: handle Escape INSIDE them, not only on the reader.
 *
 * [toggleKeys] — bu paneli AÇAN kısayol tuşları. Panel açıkken aynı tuşa tekrar
 * basmak paneli kapatır (aç/kapa). Ön tuş (preview) aşamasında yakalandığı için
 * içerideki sürgü/buton odağı ne olursa olsun çalışır; yazı alanına yazılan
 * karakterleri çalmaz (yalnızca bu tuşlar dinlenir).
 */
@Composable
private fun ReaderPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    toggleKeys: List<Key> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF10101B))
            .focusRequester(focus).onPreviewKeyEvent { event ->
                if (event.key == Key.Escape || event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyDown) onDismiss()
                    true
                } else if (toggleKeys.any { it == event.key }) {
                    // Panel kendi kısayoluyla da kapanır: tuşa ikinci kez basmak = kapat.
                    if (event.type == KeyEventType.KeyDown) onDismiss()
                    true
                } else false
            }.focusable().then(modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
    // The TV sheet host requests focus after three frames; then focus this inner key scope.
    LaunchedEffect(Unit) { focus.requestFocusAfterFrames(frames = 4) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ReaderChoices(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value, onClick = { onSelected(value) }, label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF4C347B), selectedLabelColor = Color.White,
                    containerColor = Color(0xFF262638), labelColor = Color.White.copy(.85f),
                ),
            )
        }
    }
}

@Composable
private fun ReaderToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ReaderSettingTitle(label: String) {
    Text(label, color = Color.White.copy(.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ReaderPanelTitle(label: String) {
    Text(label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
}

@Composable
private fun GoToPageDialog(currentPage: Int, pageCount: Int, onDismiss: () -> Unit, onSeek: (Int) -> Unit) {
    var text by remember { mutableStateOf((currentPage + 1).toString()) }
    val page = text.toIntOrNull()
    val valid = page != null && page in 1..pageCount
    val focus = remember { FocusRequester() }
    fun submit() { if (valid) { onSeek(page!! - 1); onDismiss() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { value -> text = value.filter(Char::isDigit).take(7) },
                singleLine = true, isError = !valid,
                label = { Text(stringResource(R.string.reader_page_range, pageCount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.focusRequester(focus).onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            if (event.type == KeyEventType.KeyDown) submit()
                            true
                        }
                        Key.Escape, Key.Back -> {
                            if (event.type == KeyEventType.KeyDown) onDismiss()
                            true
                        }
                        // Açan kısayol (G) ikinci kez basıldığında paneli kapatır.
                        // Alan yalnızca rakam kabul ettiği için G hiçbir zaman
                        // yazıya karışmaz.
                        Key.G -> {
                            if (event.type == KeyEventType.KeyDown) onDismiss()
                            true
                        }
                        else -> false
                    }
                },
            )
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = valid) { Text(stringResource(R.string.reader_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
    LaunchedEffect(Unit) { focus.requestFocusAfterFrames(frames = 2) }
}

@Composable
private fun ReaderShortcuts(mode: ReadingMode, onDismiss: () -> Unit) {
    val list = rememberLazyListState()
    val shortcuts = buildList {
        add("← / →" to stringResource(R.string.reader_keys_horizontal))
        add("↑ / ↓ · J / K" to stringResource(R.string.reader_keys_vertical))
        add("Space / Shift+Space · PgDn / PgUp" to stringResource(R.string.reader_keys_screen))
        add("Home / End" to stringResource(R.string.reader_keys_ends))
        add("N / P · ] / [ · Ctrl+→ / Ctrl+←" to stringResource(R.string.reader_keys_chapters))
        add("M / Enter" to stringResource(R.string.reader_keys_menu))
        add("S" to stringResource(R.string.okuyucu_ayarlari))
        add("C" to stringResource(R.string.reader_chapters))
        add("G" to stringResource(R.string.reader_go_to_page))
        add("R" to stringResource(R.string.reader_retry))
        add("F" to stringResource(R.string.reader_fullscreen))
        add("O" to stringResource(R.string.ekran_dondurme))
        add("D" to stringResource(R.string.reader_keys_mode))
        if (mode != ReadingMode.Webtoon) add("I" to stringResource(R.string.reader_keys_fit))
        add("B" to stringResource(R.string.reader_background))
        if (mode == ReadingMode.Webtoon) {
            add("A" to stringResource(R.string.reader_auto_scroll))
            add("+ / −" to stringResource(R.string.reader_keys_zoom))
        }
        add("0" to stringResource(R.string.reader_keys_reset_zoom))
        add("H / ? / F1" to stringResource(R.string.reader_shortcuts))
        add("Esc / Back" to stringResource(R.string.reader_keys_back))
        add("Tab / Shift+Tab · Enter" to stringResource(R.string.reader_keys_focus))
    }
    KitsugiSheetOrDialog(onDismiss = onDismiss, innerScrollState = list) {
        ReaderPanel(onDismiss = onDismiss, toggleKeys = listOf(Key.H, Key.Slash, Key.F1)) {
            ReaderPanelTitle(stringResource(R.string.reader_shortcuts))
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 16.dp)) {
                item {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text(stringResource(R.string.reader_keys_scope), color = Color.White.copy(.6f))
                        Text(
                            stringResource(R.string.reader_keys_toggle),
                            color = Color(0xFFB39DFF).copy(alpha = .85f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                items(shortcuts) { (keys, label) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(keys, color = Color(0xFFB39DFF), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(label, color = Color.White.copy(.85f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
