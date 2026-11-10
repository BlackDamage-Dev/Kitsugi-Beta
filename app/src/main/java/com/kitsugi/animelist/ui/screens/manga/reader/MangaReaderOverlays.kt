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
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
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
    KitsugiSheetOrDialog(onDismiss = onDismiss, innerColumnScrollState = scroll) {
        ReaderPanel(
            onDismiss = onDismiss,
            modifier = Modifier.kitsugiVerticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
            toggleKeys = listOf(Key.S),
        ) {
            ReaderPanelTitle(stringResource(R.string.okuyucu_ayarlari))
            com.kitsugi.animelist.ui.screens.manga.components.MangaReaderSettingsSheet(
                options = ui.options,
                onOptionsChange = { block -> model.updateOptions(block) },
            )
            TextButton(onClick = onOpenShortcuts) {
                Text(stringResource(R.string.reader_shortcuts), color = Color(0xFFB39DFF))
            }
            TextButton(onClick = onRotate) { Text(stringResource(R.string.ekran_dondurme), color = Color(0xFFB39DFF)) }
            TextButton(onClick = { model.updateOptions { it.resetCalibration() } }) {
                Text(stringResource(R.string.manga_reader_reset_calibration), color = Color(0xFFB39DFF))
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
        add("Q" to stringResource(R.string.manga_reader_bar_style))
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
