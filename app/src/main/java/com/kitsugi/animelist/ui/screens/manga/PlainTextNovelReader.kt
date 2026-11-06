package com.kitsugi.animelist.ui.screens.manga

import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.screens.manga.components.ReaderKeepScreenOn
import com.kitsugi.animelist.ui.screens.manga.components.ReaderImmersiveMode
import com.kitsugi.animelist.ui.screens.manga.components.ReaderVolumeKeys
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.screens.manga.components.WebtoonAutoScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

private const val MAX_NOVEL_BYTES = 5 * 1024 * 1024

/** Identify EPUB by its ZIP signature, not an unreliable document-provider MIME type. */
@Composable
internal fun NovelDocumentReader(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isEpub by produceState<Boolean?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val header = ByteArray(4)
                    var size = 0
                    while (size < header.size) {
                        val count = input.read(header, size, header.size - size)
                        if (count <= 0) break
                        size += count
                    }
                    size == 4 && header[0] == 0x50.toByte() && header[1] == 0x4b.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                } ?: false
            } catch (_: IOException) { false } catch (_: SecurityException) { false }
        }
    }
    when (isEpub) {
        true -> EpubNovelReader(uri, onDismiss)
        false -> PlainTextNovelReader(uri, onDismiss)
        null -> Dialog(onDismissRequest = onDismiss) { CircularProgressIndicator() }
    }
}

/** Local UTF-8 TXT reading is deliberately separate from the image-based source reader. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun PlainTextNovelReader(uri: Uri, onDismiss: () -> Unit, fictionBook: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember { context.getSharedPreferences("novel_txt_reader", android.content.Context.MODE_PRIVATE) }
    val bookKey = remember(uri) { readerSeriesId(uri.toString()) }
    var fontSize by remember { mutableIntStateOf(preferences.getInt("font_size", 20).coerceIn(10, 48)) }
    var lineHeight by remember { mutableIntStateOf(preferences.getInt("line_height", 150).coerceIn(120, 200)) }
    var theme by remember { mutableIntStateOf(preferences.getInt("theme", 0).coerceIn(0, 4)) }
    var fontFamily by remember { mutableStateOf(
        preferences.getString("font_family", null)?.takeIf { it in setOf("Serif", "Sans", "Mono") }
            ?: if (preferences.getBoolean("serif", true)) "Serif" else "Sans"
    ) }
    var volumeKeys by remember { mutableStateOf(preferences.getBoolean("volume_keys", false)) }
    var invertVolumeKeys by remember { mutableStateOf(preferences.getBoolean("invert_volume_keys", false)) }
    var keepScreenOn by remember { mutableStateOf(preferences.getBoolean("keep_screen_on", false)) }
    var immersive by remember { mutableStateOf(preferences.getBoolean("immersive", false)) }
    var margin by remember { mutableIntStateOf(preferences.getInt("margin", 24).coerceIn(12, 40)) }
    var alignment by remember { mutableIntStateOf(preferences.getInt("alignment", 0).coerceIn(0, 2)) }
    var letterSpacing by remember { mutableIntStateOf(preferences.getInt("letter_spacing_tenths", 0).coerceIn(0, 10)) }
    var hyphenation by remember { mutableStateOf(preferences.getBoolean("hyphenation", false)) }
    var lineGap by remember { mutableIntStateOf(preferences.getInt("line_gap", 0).coerceIn(0, 16)) }
    var autoScroll by remember(uri) { mutableStateOf(false) }
    var scrollTimerMinutes by remember(uri) { mutableIntStateOf(0) }
    var scrollTimerDeadline by remember(uri) { mutableLongStateOf(0L) }
    var autoScrollSpeed by remember { mutableIntStateOf(preferences.getInt("auto_scroll_speed", 40).coerceIn(20, 120)) }
    var searchOpen by remember { mutableStateOf(false) }
    var seekOpen by remember(uri) { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchMiss by remember { mutableStateOf(false) }
    var searchJob by remember(uri) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showBookmarks by remember(bookKey) { mutableStateOf(false) }
    var bookmarks by remember(bookKey) {
        mutableStateOf(if (preferences.contains("${bookKey}_bookmarks")) {
            decodeNovelBookmarks(preferences.getString("${bookKey}_bookmarks", ""))
        } else if (preferences.contains("${bookKey}_bookmark")) {
            listOf(NovelBookmark(preferences.getInt("${bookKey}_bookmark", 0).coerceAtLeast(0), 0))
        } else emptyList())
    }
    fun saveBookmarks(value: List<NovelBookmark>) {
        bookmarks = value
        preferences.edit().putString("${bookKey}_bookmarks", encodeNovelBookmarks(value)).apply()
    }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsPlaying by remember { mutableStateOf(false) }
    var ttsPaused by remember { mutableStateOf(false) }
    var showTtsVoices by remember { mutableStateOf(false) }
    var ttsVoice by remember { mutableStateOf(preferences.getString("tts_voice", "").orEmpty()) }
    var ttsVoiceUnavailable by remember { mutableStateOf(false) }
    var ttsTimerMinutes by remember(uri) { mutableIntStateOf(0) }
    var ttsTimerDeadline by remember(uri) { mutableLongStateOf(0L) }
    var ttsIndex by remember { mutableIntStateOf(0) }
    var ttsSession by remember { mutableIntStateOf(0) }
    var ttsRate by remember { mutableFloatStateOf(preferences.getFloat("tts_rate", 1f).takeIf { it.isFinite() }?.coerceIn(0.8f, 1.5f) ?: 1f) }
    var ttsPitch by remember { mutableFloatStateOf(preferences.getFloat("tts_pitch", 1f).takeIf { it.isFinite() }?.coerceIn(0.8f, 1.5f) ?: 1f) }
    val scope = rememberCoroutineScope()
    var ttsScrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var paragraphs by remember(uri) { mutableStateOf<List<String>?>(null) }
    var originalParagraphs by remember(uri) { mutableStateOf<List<String>?>(null) }
    var showReplacement by remember(uri) { mutableStateOf(false) }
    var replacementBusy by remember(uri) { mutableStateOf(false) }
    var replacementError by remember(uri) { mutableStateOf(false) }
    var replacementActive by remember(uri) { mutableStateOf(false) }
    var error by remember(uri) { mutableStateOf(false) }
    var restored by remember(uri) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val readingStats = remember(paragraphs) { novelReadingOffsets(paragraphs.orEmpty()) }
    val background = when (theme) { 1 -> Color(0xFFF4ECD8); 2 -> Color.White; 3 -> Color.Black; 4 -> Color(0xFFE5EFDD); else -> Color(0xFF11121A) }
    val foreground = if (theme == 0 || theme == 3) Color(0xFFF3F1F7) else Color(0xFF25211E)

    fun searchText(forward: Boolean) {
        searchJob?.cancel()
        searchJob = scope.launch {
            val lines = paragraphs.orEmpty()
            val start = listState.firstVisibleItemIndex
            val searched = query
            val found = withContext(Dispatchers.Default) { findNovelText(lines, searched, start, forward) }
            if (searched != query) return@launch
            if (found == null) searchMiss = true else { searchMiss = false; listState.scrollToItem(found) }
        }
    }

    DisposableEffect(context) {
        var disposed = false
        val handler = Handler(Looper.getMainLooper())
        val engine = TextToSpeech(context) { status ->
            handler.post { if (!disposed) ttsReady = status == TextToSpeech.SUCCESS }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                handler.post {
                    if (!disposed && ttsPlaying && utteranceId == "$ttsSession:$ttsIndex") ttsIndex++
                }
            }
            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) {
                handler.post {
                    if (!disposed && utteranceId == "$ttsSession:$ttsIndex") ttsPlaying = false
                }
            }
        })
        tts = engine
        onDispose {
            disposed = true
            ttsPlaying = false
            ttsPaused = false
            ttsReady = false
            tts = null
            ttsScrollJob?.cancel()
            engine.stop()
            engine.shutdown()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                autoScroll = false
                if (ttsPlaying || ttsPaused) {
                    ttsPlaying = false
                    ttsPaused = false
                    ttsSession++
                    ttsScrollJob?.cancel()
                    tts?.stop()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(scrollTimerDeadline) {
        val deadline = scrollTimerDeadline
        if (deadline != 0L) {
            kotlinx.coroutines.delay(speechTimerRemainingMillis(deadline, android.os.SystemClock.elapsedRealtime()))
            if (scrollTimerDeadline != deadline) return@LaunchedEffect
            autoScroll = false
            scrollTimerDeadline = 0L
            scrollTimerMinutes = 0
        }
    }
    LaunchedEffect(ttsTimerDeadline) {
        val deadline = ttsTimerDeadline
        if (deadline > 0) {
            kotlinx.coroutines.delay(speechTimerRemainingMillis(deadline, android.os.SystemClock.elapsedRealtime()))
            if (ttsTimerDeadline != deadline) return@LaunchedEffect
            ttsPlaying = false
            ttsPaused = false
            ttsSession++
            ttsScrollJob?.cancel()
            tts?.stop()
            ttsTimerDeadline = 0L
            ttsTimerMinutes = 0
        }
    }
    LaunchedEffect(ttsReady, tts, ttsVoice) {
        val engine = tts
        if (ttsReady && engine != null) ttsVoiceUnavailable = !applyNovelSpeechVoice(engine, ttsVoice)
    }
    WebtoonAutoScroll(
        enabled = autoScroll && restored && !ttsPlaying && !searchOpen && !showBookmarks && !showTtsVoices && !showReplacement && !seekOpen &&
            paragraphs?.isNotEmpty() == true && listState.layoutInfo.viewportSize.height > 0,
        listState = listState,
        speedDpPerSecond = autoScrollSpeed,
        onEnd = { autoScroll = false }
    )
    LaunchedEffect(ttsPlaying, ttsIndex, ttsReady, paragraphs, ttsSession) {
        val lines = paragraphs ?: return@LaunchedEffect
        val engine = tts ?: return@LaunchedEffect
        if (ttsPlaying && ttsReady) {
            if (ttsIndex >= lines.size) {
                ttsPlaying = false
            } else if (lines[ttsIndex].isBlank()) {
                ttsIndex = (ttsIndex until lines.size).firstOrNull { lines[it].isNotBlank() } ?: lines.size
            } else {
                val spokenIndex = ttsIndex
                ttsScrollJob?.cancel()
                ttsScrollJob = scope.launch { listState.animateScrollToItem(spokenIndex) }
                engine.setSpeechRate(ttsRate)
                engine.setPitch(ttsPitch)
                ttsVoiceUnavailable = !applyNovelSpeechVoice(engine, ttsVoice)
                if (engine.speak(lines[ttsIndex], TextToSpeech.QUEUE_FLUSH, null, "$ttsSession:$ttsIndex") == TextToSpeech.ERROR) {
                    ttsPlaying = false
                }
            }
        }
    }

    LaunchedEffect(uri) {
        ttsPlaying = false
        ttsPaused = false
        ttsSession++
        ttsScrollJob?.cancel()
        tts?.stop()
        restored = false
        error = false
        paragraphs = try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffered = input.buffered()
                    buffered.mark(4)
                    val header = ByteArray(4)
                    var headerSize = 0
                    while (headerSize < header.size) {
                        val count = buffered.read(header, headerSize, header.size - headerSize)
                        if (count <= 0) break
                        headerSize += count
                    }
                    buffered.reset()
                    val zip = fictionBook && headerSize == 4 && header[0] == 0x50.toByte() && header[1] == 0x4b.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                    val bytes = if (zip) {
                        val temporary = java.io.File.createTempFile("reader-fb2-", ".zip", context.cacheDir)
                        try {
                            val operation = kotlinx.coroutines.currentCoroutineContext()
                            temporary.outputStream().use { out ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                while (true) {
                                    operation.ensureActive()
                                    val count = buffered.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    total += count
                                    require(total <= MAX_FB2_ARCHIVE_BYTES) { "FictionBook archive too large" }
                                    out.write(buffer, 0, count)
                                }
                            }
                            readFictionBookArchive(temporary)
                        } finally { temporary.delete() }
                    } else readBoundedNovel(buffered, MAX_NOVEL_BYTES)
                    if (fictionBook) parseFictionBookText(bytes) else {
                        val decoder = Charsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                        splitNovelText(decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF"))
                    }
                } ?: throw java.io.IOException("Cannot open document")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = true
            null
        }
        originalParagraphs = paragraphs
        paragraphs?.let { lines ->
            if (lines.isNotEmpty()) {
                listState.scrollToItem(
                    preferences.getInt("${bookKey}_index", 0).coerceIn(0, lines.lastIndex),
                    preferences.getInt("${bookKey}_offset", 0).coerceAtLeast(0)
                )
            }
            restored = true
        }
    }
    DisposableEffect(uri) {
        onDispose {
            if (restored) preferences.edit().putInt("${bookKey}_index", listState.firstVisibleItemIndex)
                .putInt("${bookKey}_offset", listState.firstVisibleItemScrollOffset).apply()
        }
    }
    LaunchedEffect(uri, restored) {
        if (restored) snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged().debounce(400)
            .collect { (index, offset) -> preferences.edit().putInt("${bookKey}_index", index)
                .putInt("${bookKey}_offset", offset).apply() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ReaderKeepScreenOn(keepScreenOn)
        ReaderImmersiveMode(immersive)
        ReaderVolumeKeys(enabled = volumeKeys && restored && !ttsPlaying && !ttsPaused && !searchOpen && !seekOpen &&
            !showBookmarks && !showReplacement && !showTtsVoices, inverted = invertVolumeKeys,
            onNext = {
                autoScroll = false
                val distance = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) * 0.8f
                if (distance > 0) scope.launch { listState.animateScrollBy(distance) }
            }, onPrevious = {
                autoScroll = false
                val distance = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) * 0.8f
                if (distance > 0) scope.launch { listState.animateScrollBy(-distance) }
            })
        Column(Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_txt_close), color = foreground) }
                Text(stringResource(R.string.reader_txt_title), color = foreground, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    theme = (theme + 1) % 5
                    preferences.edit().putInt("theme", theme).apply()
                }) { Text(stringResource(when (theme) { 1 -> R.string.reader_theme_sepia; 2 -> R.string.reader_theme_light; 3 -> R.string.reader_theme_oled; 4 -> R.string.reader_theme_green; else -> R.string.reader_theme_dark }), color = foreground) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(10); preferences.edit().putInt("font_size", fontSize).apply() }) { Text("A−", color = foreground) }
                Text("${fontSize}sp", color = foreground)
                TextButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(48); preferences.edit().putInt("font_size", fontSize).apply() }) { Text("A+", color = foreground) }
                TextButton(onClick = {
                    fontFamily = when (fontFamily) { "Serif" -> "Sans"; "Sans" -> "Mono"; else -> "Serif" }
                    preferences.edit().putString("font_family", fontFamily).putBoolean("serif", fontFamily != "Sans").apply()
                }) { Text(fontFamily, color = foreground) }
                TextButton(onClick = { keepScreenOn = !keepScreenOn; preferences.edit().putBoolean("keep_screen_on", keepScreenOn).apply() }) {
                    Text(stringResource(if (keepScreenOn) R.string.reader_novel_keep_screen_on else R.string.reader_novel_keep_screen_off), color = foreground)
                }
                TextButton(onClick = { volumeKeys = !volumeKeys; preferences.edit().putBoolean("volume_keys", volumeKeys).apply() }) {
                    Text(stringResource(if (volumeKeys) R.string.reader_novel_volume_on else R.string.reader_novel_volume_off), color = foreground)
                }
                if (volumeKeys) TextButton(onClick = { invertVolumeKeys = !invertVolumeKeys; preferences.edit().putBoolean("invert_volume_keys", invertVolumeKeys).apply() }) {
                    Text(stringResource(if (invertVolumeKeys) R.string.reader_novel_volume_inverted else R.string.reader_novel_volume_normal), color = foreground)
                }
                TextButton(onClick = { immersive = !immersive; preferences.edit().putBoolean("immersive", immersive).apply() }) {
                    Text(stringResource(if (immersive) R.string.reader_immersive_on else R.string.reader_immersive_off), color = foreground)
                }
                TextButton(onClick = { lineHeight = if (lineHeight >= 200) 120 else lineHeight + 20; preferences.edit().putInt("line_height", lineHeight).apply() }) { Text("${lineHeight}%", color = foreground) }
                TextButton(onClick = { margin = when (margin) { 12 -> 24; 24 -> 40; else -> 12 }; preferences.edit().putInt("margin", margin).apply() }) { Text(stringResource(R.string.reader_txt_margin, margin), color = foreground) }
                TextButton(onClick = { alignment = (alignment + 1) % 3; preferences.edit().putInt("alignment", alignment).apply() }) {
                    Text(stringResource(when (alignment) { 1 -> R.string.reader_text_align_start; 2 -> R.string.reader_text_align_justify; else -> R.string.reader_text_align_original }), color = foreground)
                }
                TextButton(onClick = { letterSpacing = when (letterSpacing) { 0 -> 5; 5 -> 10; else -> 0 }; preferences.edit().putInt("letter_spacing_tenths", letterSpacing).apply() }) {
                    Text(stringResource(R.string.reader_text_letter_spacing, letterSpacing / 10f), color = foreground)
                }
                TextButton(onClick = { hyphenation = !hyphenation; preferences.edit().putBoolean("hyphenation", hyphenation).apply() }) {
                    Text(stringResource(if (hyphenation) R.string.reader_hyphenation_on else R.string.reader_hyphenation_off), color = foreground)
                }
                TextButton(onClick = { lineGap = if (lineGap >= 16) 0 else lineGap + 4; preferences.edit().putInt("line_gap", lineGap).apply() }) {
                    Text(stringResource(R.string.reader_txt_line_gap, lineGap), color = foreground)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = restored && !ttsPlaying, onClick = { autoScroll = !autoScroll }) {
                    Text(stringResource(if (autoScroll) R.string.reader_auto_scroll_stop else R.string.reader_auto_scroll_start), color = foreground)
                }
                TextButton(onClick = {
                    scrollTimerMinutes = nextSpeechTimerMinutes(scrollTimerMinutes)
                    scrollTimerDeadline = if (scrollTimerMinutes == 0) 0L else android.os.SystemClock.elapsedRealtime() + scrollTimerMinutes * 60_000L
                }) {
                    Text(if (scrollTimerMinutes == 0) stringResource(R.string.reader_scroll_timer_off) else stringResource(R.string.reader_scroll_timer_minutes, scrollTimerMinutes), color = foreground)
                }
                TextButton(onClick = {
                    autoScrollSpeed = when (autoScrollSpeed) { 20 -> 40; 40 -> 80; 80 -> 120; else -> 20 }
                    preferences.edit().putInt("auto_scroll_speed", autoScrollSpeed).apply()
                }) { Text(stringResource(R.string.reader_novel_auto_scroll_speed, autoScrollSpeed), color = foreground) }
                TextButton(enabled = restored, onClick = { seekOpen = !seekOpen }) {
                    Text(stringResource(R.string.reader_novel_seek), color = foreground)
                }
                TextButton(enabled = restored, onClick = { showReplacement = true; replacementError = false }) {
                    Text(stringResource(if (replacementActive) R.string.reader_replacement_active else R.string.reader_replacement_open), color = foreground)
                }
                TextButton(onClick = { searchOpen = !searchOpen; searchMiss = false }) { Text(stringResource(R.string.reader_txt_search), color = foreground) }
                TextButton(enabled = restored && bookmarks.size < 200, onClick = {
                    saveBookmarks(addNovelBookmark(bookmarks, NovelBookmark(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)))
                }) { Text(stringResource(R.string.reader_txt_bookmark), color = foreground) }
                TextButton(enabled = restored, onClick = { showBookmarks = true }) {
                    Text(stringResource(R.string.reader_bookmarks, bookmarks.size), color = foreground)
                }
                TextButton(enabled = ttsReady && restored, onClick = {
                    if (ttsPlaying) {
                        ttsPlaying = false
                        ttsPaused = true
                        ttsSession++
                        ttsScrollJob?.cancel()
                        tts?.stop()
                    } else {
                        autoScroll = false
                        ttsSession++
                        if (!ttsPaused) ttsIndex = listState.firstVisibleItemIndex
                        ttsPaused = false
                        ttsPlaying = true
                    }
                }) { Text(stringResource(when { ttsPlaying -> R.string.reader_tts_pause; ttsPaused -> R.string.reader_tts_resume; else -> R.string.reader_txt_tts_play }), color = foreground) }
                if (ttsPlaying || ttsPaused) {
                    val lines = paragraphs.orEmpty()
                    val previous = adjacentSpeechSegment(lines, ttsIndex, false)
                    val next = adjacentSpeechSegment(lines, ttsIndex, true)
                    TextButton(enabled = previous != null, onClick = {
                        if (previous != null) { ttsSession++; ttsScrollJob?.cancel(); tts?.stop(); ttsIndex = previous }
                    }) { Text(stringResource(R.string.reader_tts_previous_segment), color = foreground) }
                    TextButton(enabled = next != null, onClick = {
                        if (next != null) { ttsSession++; ttsScrollJob?.cancel(); tts?.stop(); ttsIndex = next }
                    }) { Text(stringResource(R.string.reader_tts_next_segment), color = foreground) }
                }
                if (ttsPlaying || ttsPaused) TextButton(onClick = {
                    ttsPlaying = false; ttsPaused = false; ttsSession++; ttsScrollJob?.cancel(); tts?.stop()
                }) { Text(stringResource(R.string.reader_txt_tts_stop), color = foreground) }
                TextButton(onClick = {
                    ttsRate = when (ttsRate) { 0.8f -> 1f; 1f -> 1.2f; 1.2f -> 1.5f; else -> 0.8f }
                    preferences.edit().putFloat("tts_rate", ttsRate).apply()
                }) { Text(stringResource(R.string.reader_txt_tts_speed, ttsRate), color = foreground) }
                TextButton(onClick = {
                    ttsPitch = nextNovelSpeechPitch(ttsPitch)
                    preferences.edit().putFloat("tts_pitch", ttsPitch).apply()
                }) { Text(stringResource(R.string.reader_tts_pitch, ttsPitch), color = foreground) }
                TextButton(enabled = ttsReady, onClick = { showTtsVoices = true }) { Text(stringResource(R.string.reader_tts_voice), color = foreground) }
                TextButton(onClick = {
                    ttsTimerMinutes = nextSpeechTimerMinutes(ttsTimerMinutes)
                    ttsTimerDeadline = if (ttsTimerMinutes == 0) 0L else android.os.SystemClock.elapsedRealtime() + ttsTimerMinutes * 60_000L
                }) {
                    Text(if (ttsTimerMinutes == 0) stringResource(R.string.reader_tts_timer_off) else stringResource(R.string.reader_tts_timer_minutes, ttsTimerMinutes), color = foreground)
                }
            }
            if (restored && paragraphs.orEmpty().isNotEmpty()) {
                val isAtEnd = listState.layoutInfo.totalItemsCount > 0 && !listState.canScrollForward
                val estimate = novelReadingEstimate(readingStats, listState.firstVisibleItemIndex, isAtEnd)
                Text(stringResource(R.string.reader_novel_estimate, estimate.percent, estimate.minutesRemaining),
                    color = foreground, modifier = Modifier.padding(horizontal = 16.dp))
                if (seekOpen) NovelSeekBar(estimate.percent, foreground) { percent ->
                    autoScroll = false; ttsPlaying = false; ttsPaused = false; ttsSession++; ttsScrollJob?.cancel(); tts?.stop()
                    val index = novelSegmentAtPercent(readingStats, percent)
                    scope.launch { listState.scrollToItem(index) }
                }
            }
            if (ttsPlaying || ttsPaused) Text(stringResource(R.string.reader_tts_pause_hint), color = foreground, modifier = Modifier.padding(horizontal = 16.dp))
            if (ttsVoiceUnavailable) Text(stringResource(R.string.reader_tts_voice_unavailable), color = foreground, modifier = Modifier.padding(horizontal = 16.dp))
            if (showTtsVoices && ttsReady) tts?.let { engine ->
                NovelSpeechVoicePicker(engine, ttsVoice, foreground, background,
                    onSelect = { ttsVoice = it; preferences.edit().putString("tts_voice", it).apply() },
                    onDismiss = { showTtsVoices = false })
            }
            if (showReplacement) NovelReplacementDialog(
                foreground = foreground, background = background, busy = replacementBusy, error = replacementError,
                onApply = { find, replace ->
                    val original = originalParagraphs
                    if (original != null) scope.launch {
                        replacementBusy = true; replacementError = false
                        try {
                            val replaced = withContext(Dispatchers.Default) {
                                val operation = kotlinx.coroutines.currentCoroutineContext()
                                applyNovelLiteralReplacement(original, find, replace) { operation.ensureActive() }
                            }
                            autoScroll = false; ttsPlaying = false; ttsPaused = false; ttsSession++; ttsScrollJob?.cancel(); tts?.stop()
                            searchJob?.cancel(); searchMiss = false
                            paragraphs = replaced; replacementActive = true; showReplacement = false
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: IllegalArgumentException) { replacementError = true } finally { replacementBusy = false }
                    }
                },
                onReset = {
                    autoScroll = false; ttsPlaying = false; ttsPaused = false; ttsSession++; ttsScrollJob?.cancel(); tts?.stop()
                    searchJob?.cancel(); searchMiss = false
                    paragraphs = originalParagraphs; replacementActive = false; showReplacement = false
                },
                onDismiss = { showReplacement = false }
            )
            if (showBookmarks) NovelBookmarkDialog(
                bookmarks = bookmarks, bookKey = bookKey, kind = "text", foreground = foreground, background = background,
                label = { mark -> stringResource(R.string.reader_bookmark_line, mark.index + 1) },
                exportLabel = { mark -> context.getString(R.string.reader_bookmark_line, mark.index + 1) },
                onNavigate = { mark ->
                    showBookmarks = false
                    autoScroll = false
                    ttsPlaying = false; ttsPaused = false; ttsSession++; ttsScrollJob?.cancel(); tts?.stop()
                    scope.launch {
                        val last = paragraphs.orEmpty().lastIndex.coerceAtLeast(0)
                        listState.scrollToItem(mark.index.coerceIn(0, last), mark.offset)
                    }
                },
                onChange = ::saveBookmarks,
                onDismiss = { showBookmarks = false }
            )
            if (searchOpen) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { searchJob?.cancel(); query = it.take(100); searchMiss = false },
                        modifier = Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = foreground),
                        label = { Text(stringResource(R.string.reader_txt_search), color = foreground) })
                    TextButton(enabled = query.isNotBlank() && restored, onClick = { searchText(false) }) {
                        Text(stringResource(R.string.reader_txt_search_previous), color = foreground)
                    }
                    TextButton(enabled = query.isNotBlank() && restored, onClick = { searchText(true) }) {
                        Text(stringResource(R.string.reader_txt_search_next), color = foreground)
                    }
                }
                if (searchMiss) Text(stringResource(R.string.reader_txt_not_found), color = foreground, modifier = Modifier.padding(horizontal = 16.dp))
            }
            when {
                error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (fictionBook) R.string.reader_fb2_error else R.string.reader_txt_error), color = foreground, modifier = Modifier.padding(24.dp))
                }
                paragraphs == null || !restored -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = margin.dp, vertical = 16.dp)) {
                    items(paragraphs.orEmpty()) { line ->
                        val display = remember(line, query, searchOpen) {
                            val value = line.ifEmpty { "\u00a0" }
                            buildAnnotatedString {
                                append(value)
                                if (searchOpen) for (range in novelSearchRanges(value, query)) {
                                    addStyle(SpanStyle(color = Color.Black, background = Color(0xFFFFD85A)), range.first, range.last + 1)
                                }
                            }
                        }
                        SelectionContainer { Text(
                            text = display,
                            style = TextStyle(hyphens = if (hyphenation) Hyphens.Auto else Hyphens.None),
                            color = foreground,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * lineHeight / 100f).sp,
                            fontFamily = when (fontFamily) { "Mono" -> FontFamily.Monospace; "Sans" -> FontFamily.SansSerif; else -> FontFamily.Serif },
                            letterSpacing = if (letterSpacing == 0) TextUnit.Unspecified else (letterSpacing / 10f).sp,
                            textAlign = when (alignment) { 1 -> TextAlign.Start; 2 -> TextAlign.Justify; else -> null },
                            modifier = Modifier.fillMaxWidth().padding(bottom = lineGap.dp)
                        ) }
                    }
                }
            }
        }
    }
}

/** Bound individual Text layouts without splitting UTF-16 surrogate pairs. */
internal fun splitNovelText(text: String): List<String> {
    val result = mutableListOf<String>()
    for (line in text.replace("\r\n", "\n").replace('\r', '\n').lineSequence()) {
        require(result.size < 100_000) { "Too many lines" }
        if (line.isEmpty()) { result.add(""); continue }
        var start = 0
        while (start < line.length) {
            var end = (start + 2048).coerceAtMost(line.length)
            if (end < line.length && Character.isHighSurrogate(line[end - 1]) && Character.isLowSurrogate(line[end])) end--
            require(result.size < 100_000) { "Too many text segments" }
            result.add(line.substring(start, end))
            start = end
        }
    }
    return result
}

/** Read at most the configured size on all supported Android versions. */
internal fun readBoundedNovel(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        require(output.size() <= limit - count) { "Text document is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

/** Search wraps once, including the current paragraph as the last candidate. */
internal fun findNovelText(lines: List<String>, query: String, current: Int, forward: Boolean = true): Int? {
    if (lines.isEmpty() || query.isBlank()) return null
    val start = current.coerceIn(0, lines.lastIndex)
    for (offset in 1..lines.size) {
        val index = if (forward) (start + offset) % lines.size else (start - offset + lines.size) % lines.size
        if (lines[index].contains(query, ignoreCase = true)) return index
    }
    return null
}
