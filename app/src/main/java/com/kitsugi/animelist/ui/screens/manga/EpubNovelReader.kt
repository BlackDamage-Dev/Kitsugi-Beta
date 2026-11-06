package com.kitsugi.animelist.ui.screens.manga

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.screens.manga.components.ReaderKeepScreenOn
import com.kitsugi.animelist.ui.screens.manga.components.ReaderImmersiveMode
import com.kitsugi.animelist.ui.screens.manga.components.ReaderVolumeKeys
import com.kitsugi.animelist.ui.screens.manga.components.autoScrollStepPixels
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

private const val EPUB_ORIGIN = "https://epub.kitsugi.invalid/"
private const val EPUB_MAX_BYTES = 50L * 1024 * 1024
private data class PendingEpubFind(val chapter: Int, val query: String, val ordinal: Int)

/** Coalesce WebView scroll events while still flushing on chapter change and stop. */
private class EpubProgressSaver(private val prefs: SharedPreferences, private val key: String) {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    fun schedule(chapter: Int, scroll: Int) {
        cancel()
        val task = Runnable { pending = null; save(chapter, scroll) }
        pending = task
        handler.postDelayed(task, 400)
    }
    fun save(chapter: Int, scroll: Int) {
        cancel()
        prefs.edit().putInt("${key}_chapter", chapter).putInt("${key}_scroll", scroll.coerceAtLeast(0)).apply()
    }
    fun cancel() { pending?.let(handler::removeCallbacks); pending = null }
}

/** A local, sandboxed EPUB 2/3 spine reader; scripts and network access are disabled. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EpubNovelReader(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val prefs = remember { context.getSharedPreferences("novel_epub_reader", android.content.Context.MODE_PRIVATE) }
    val bookKey = remember(uri) { readerSeriesId(uri.toString()) }
    val progressSaver = remember(bookKey) { EpubProgressSaver(prefs, bookKey) }
    var book by remember(uri) { mutableStateOf<EpubBook?>(null) }
    var error by remember(uri) { mutableStateOf(false) }
    var chapter by remember(uri) { mutableIntStateOf(0) }
    var html by remember(uri) { mutableStateOf<String?>(null) }
    var htmlChapter by remember(uri) { mutableIntStateOf(-1) }
    var fontSize by remember { mutableIntStateOf(prefs.getInt("font_size", 20).coerceIn(10, 48)) }
    var theme by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, 4)) }
    var lineHeight by remember { mutableIntStateOf(prefs.getInt("line_height", 150).coerceIn(120, 200)) }
    var margin by remember { mutableIntStateOf(prefs.getInt("margin", 24).coerceIn(12, 40)) }
    var fontFamily by remember { mutableStateOf(
        prefs.getString("font_family", null)?.takeIf { it in setOf("Serif", "Sans", "Mono") }
            ?: if (prefs.getBoolean("serif", true)) "Serif" else "Sans"
    ) }
    var volumeKeys by remember { mutableStateOf(prefs.getBoolean("volume_keys", false)) }
    var invertVolumeKeys by remember { mutableStateOf(prefs.getBoolean("invert_volume_keys", false)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keep_screen_on", false)) }
    var immersive by remember { mutableStateOf(prefs.getBoolean("immersive", false)) }
    var alignment by remember { mutableIntStateOf(prefs.getInt("alignment", 0).coerceIn(0, 2)) }
    var letterSpacing by remember { mutableIntStateOf(prefs.getInt("letter_spacing_tenths", 0).coerceIn(0, 10)) }
    var hyphenation by remember { mutableStateOf(prefs.getBoolean("hyphenation", false)) }
    var paragraphGap by remember { mutableIntStateOf(prefs.getInt("paragraph_gap", -1).coerceIn(-1, 24)) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember(uri) { mutableStateOf(false) }
    var autoScroll by remember(uri) { mutableStateOf(false) }
    var autoNextScroll by remember { mutableStateOf(prefs.getBoolean("auto_next_scroll", false)) }
    var pendingAutoScrollChapter by remember(uri) { mutableStateOf<Int?>(null) }
    var scrollTimerMinutes by remember(uri) { mutableIntStateOf(0) }
    var scrollTimerDeadline by remember(uri) { mutableLongStateOf(0L) }
    var autoScrollSpeed by remember { mutableIntStateOf(prefs.getInt("auto_scroll_speed", 40).coerceIn(20, 120)) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsPlaying by remember(uri) { mutableStateOf(false) }
    var ttsPaused by remember(uri) { mutableStateOf(false) }
    var showTtsVoices by remember { mutableStateOf(false) }
    var ttsVoice by remember { mutableStateOf(prefs.getString("tts_voice", "").orEmpty()) }
    var ttsVoiceUnavailable by remember { mutableStateOf(false) }
    var ttsTimerMinutes by remember(uri) { mutableIntStateOf(0) }
    var ttsTimerDeadline by remember(uri) { mutableLongStateOf(0L) }
    var ttsError by remember(uri) { mutableStateOf(false) }
    var ttsSession by remember { mutableIntStateOf(0) }
    var ttsIndex by remember(uri) { mutableIntStateOf(0) }
    var ttsSegments by remember(uri) { mutableStateOf<List<String>?>(null) }
    var ttsRate by remember { mutableFloatStateOf(prefs.getFloat("tts_rate", 1f).takeIf { it.isFinite() }?.coerceIn(0.8f, 1.5f) ?: 1f) }
    var ttsPitch by remember { mutableFloatStateOf(prefs.getFloat("tts_pitch", 1f).takeIf { it.isFinite() }?.coerceIn(0.8f, 1.5f) ?: 1f) }
    var ttsContinue by remember { mutableStateOf(prefs.getBoolean("tts_continue", false)) }
    var loadedChapter by remember { mutableIntStateOf(-1) }
    var pendingScroll by remember(uri) { mutableStateOf<Int?>(null) }
    var pendingFragment by remember(uri) { mutableStateOf<String?>(null) }
    var showChapterSeek by remember(uri) { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showBookSearch by remember { mutableStateOf(false) }
    var searchResult by remember(uri) { mutableStateOf<EpubSearchResults?>(null) }
    var pendingFind by remember(uri) { mutableStateOf<PendingEpubFind?>(null) }
    var searchingBook by remember(uri) { mutableStateOf(false) }
    var searchJob by remember(uri) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showBookmarks by remember(bookKey) { mutableStateOf(false) }
    var bookmarks by remember(bookKey) {
        mutableStateOf(if (prefs.contains("${bookKey}_bookmarks")) {
            decodeNovelBookmarks(prefs.getString("${bookKey}_bookmarks", ""))
        } else if (prefs.contains("${bookKey}_bookmark_chapter")) {
            listOf(NovelBookmark(prefs.getInt("${bookKey}_bookmark_chapter", 0).coerceAtLeast(0),
                prefs.getInt("${bookKey}_bookmark_scroll", 0).coerceAtLeast(0)))
        } else emptyList())
    }
    fun saveBookmarks(value: List<NovelBookmark>) {
        bookmarks = value
        prefs.edit().putString("${bookKey}_bookmarks", encodeNovelBookmarks(value)).apply()
    }

    fun stopTts() {
        ttsSession++
        ttsPlaying = false
        ttsPaused = false
        ttsSegments = null
        ttsIndex = 0
        tts?.stop()
    }

    fun chapterBase(path: String): String = EPUB_ORIGIN + path.split('/').joinToString("/") { Uri.encode(it) }
    fun openChapter(index: Int, scroll: Int = 0, fragment: String? = null, continueSpeech: Boolean = false) {
        val currentBook = book ?: return
        if (index !in currentBook.chapters.indices) return
        pendingFind = null
        pendingAutoScrollChapter = null
        autoScroll = false
        if ((ttsPlaying || ttsPaused) && !continueSpeech) stopTts()
        if (index != chapter && loadedChapter == chapter && pageReady) {
            webView?.let { progressSaver.save(loadedChapter, it.scrollY) }
        }
        val anchor = fragment?.takeIf { it.isNotEmpty() && it.length <= 2048 }
        if (index == chapter && loadedChapter == index && pageReady) {
            pendingFragment = null
            pendingScroll = null
            if (anchor != null) webView?.loadUrl(chapterBase(currentBook.chapters[index]) + "#" + Uri.encode(anchor))
            else webView?.scrollTo(0, scroll.coerceAtLeast(0))
        } else {
            pendingFragment = anchor
            pendingScroll = if (anchor == null) scroll.coerceAtLeast(0) else null
            chapter = index
        }
    }
    fun advanceSpeechChapter(): Boolean {
        val next = nextSpeechChapter(chapter, book?.chapters?.size ?: 0, ttsContinue) ?: return false
        ttsSession++ // Ignore late callbacks from the previous chapter.
        ttsSegments = null
        ttsIndex = 0
        openChapter(next, continueSpeech = true)
        return true
    }
    val foreground = if (theme == 0 || theme == 3) Color.White else Color(0xFF25211E)
    val background = when (theme) { 1 -> Color(0xFFF4ECD8); 2 -> Color.White; 3 -> Color.Black; 4 -> Color(0xFFE5EFDD); else -> Color(0xFF11121A) }

    DisposableEffect(context) {
        var disposed = false
        val handler = Handler(Looper.getMainLooper())
        val engine = TextToSpeech(context) { status ->
            handler.post {
                if (!disposed) {
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (!ttsReady) ttsError = true
                }
            }
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
                    if (!disposed && ttsPlaying && utteranceId == "$ttsSession:$ttsIndex") {
                        stopTts()
                        ttsError = true
                    }
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
            engine.stop()
            engine.shutdown()
        }
    }
    LaunchedEffect(scrollTimerDeadline) {
        val deadline = scrollTimerDeadline
        if (deadline != 0L) {
            kotlinx.coroutines.delay(speechTimerRemainingMillis(deadline, android.os.SystemClock.elapsedRealtime()))
            if (scrollTimerDeadline != deadline) return@LaunchedEffect
            autoScroll = false
            pendingAutoScrollChapter = null
            scrollTimerDeadline = 0L
            scrollTimerMinutes = 0
        }
    }
    LaunchedEffect(ttsTimerDeadline) {
        val deadline = ttsTimerDeadline
        if (deadline > 0) {
            kotlinx.coroutines.delay(speechTimerRemainingMillis(deadline, android.os.SystemClock.elapsedRealtime()))
            if (ttsTimerDeadline != deadline) return@LaunchedEffect
            stopTts()
            ttsTimerDeadline = 0L
            ttsTimerMinutes = 0
        }
    }
    LaunchedEffect(ttsReady, tts, ttsVoice) {
        val engine = tts
        if (ttsReady && engine != null) ttsVoiceUnavailable = !applyNovelSpeechVoice(engine, ttsVoice)
    }
    LaunchedEffect(ttsPlaying, book, chapter, ttsSession) {
        val currentBook = book ?: return@LaunchedEffect
        if (!ttsPlaying || ttsSegments != null) return@LaunchedEffect
        try {
            val result = withContext(Dispatchers.IO) { currentBook.chapterSpeech(chapter) }
            if (result.isEmpty()) {
                if (!advanceSpeechChapter()) { stopTts(); ttsError = true }
            } else { ttsIndex = 0; ttsSegments = result }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            stopTts()
            ttsError = true
        }
    }
    LaunchedEffect(ttsPlaying, ttsSegments, ttsIndex, ttsReady, ttsSession) {
        val segments = ttsSegments ?: return@LaunchedEffect
        val engine = tts ?: return@LaunchedEffect
        if (!ttsPlaying || !ttsReady) return@LaunchedEffect
        if (ttsIndex >= segments.size) {
            if (!advanceSpeechChapter()) stopTts()
        } else {
            engine.setSpeechRate(ttsRate)
            engine.setPitch(ttsPitch)
            ttsVoiceUnavailable = !applyNovelSpeechVoice(engine, ttsVoice)
            if (engine.speak(segments[ttsIndex], TextToSpeech.QUEUE_FLUSH, null, "$ttsSession:$ttsIndex") == TextToSpeech.ERROR) {
                stopTts()
                ttsError = true
            }
        }
    }

    LaunchedEffect(uri) {
        stopTts()
        var temp: File? = null
        var opened: EpubBook? = null
        try {
            val result = withContext(Dispatchers.IO) {
                val file = File.createTempFile("epub-reader-", ".epub", context.cacheDir)
                temp = file
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            total += count
                            require(total <= EPUB_MAX_BYTES) { "EPUB too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: throw IOException("Cannot open document")
                EpubBook.open(file).also { opened = it }
            }
            chapter = prefs.getInt("${bookKey}_chapter", 0).coerceIn(0, result.chapters.lastIndex)
            pendingScroll = prefs.getInt("${bookKey}_scroll", 0).coerceAtLeast(0)
            book = result
            awaitCancellation()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = true
        } finally {
            opened?.close()
            temp?.delete()
        }
    }
    LaunchedEffect(book, chapter, fontSize, theme, lineHeight, margin, fontFamily, alignment, letterSpacing, paragraphGap, hyphenation) {
        val currentBook = book ?: return@LaunchedEffect
        html = null
        htmlChapter = -1
        val targetChapter = chapter
        try {
            val content = withContext(Dispatchers.IO) { currentBook.chapterHtml(targetChapter, fontSize, theme, lineHeight, margin, fontFamily != "Sans", alignment, letterSpacing, paragraphGap, fontFamily, hyphenation) }
            htmlChapter = targetChapter
            html = content
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = true
        }
    }
    LaunchedEffect(query, showSearch, html, webView, pageReady, loadedChapter, chapter) {
        if (showSearch && query.isNotBlank() && html != null && pageReady && loadedChapter == chapter) {
            webView?.findAllAsync(query.trim())
        } else if (!showSearch || query.isBlank()) {
            pendingFind = null
            webView?.clearMatches()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                autoScroll = false
                pendingAutoScrollChapter = null
                if (ttsPlaying || ttsPaused) stopTts()
                if (pageReady && loadedChapter == chapter) webView?.let { progressSaver.save(loadedChapter, it.scrollY) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(pendingAutoScrollChapter, pageReady, loadedChapter, chapter) {
        if (pendingAutoScrollChapter == chapter && pageReady && loadedChapter == chapter) {
            pendingAutoScrollChapter = null
            autoScroll = true
        }
    }
    LaunchedEffect(autoScroll, autoScrollSpeed, webView, pageReady, ttsPlaying, showSearch, showContents, showBookmarks, showBookSearch, showTtsVoices, showChapterSeek, density, autoNextScroll, chapter, book) {
        val view = webView ?: return@LaunchedEffect
        if (!autoScroll || !pageReady || ttsPlaying || showSearch || showContents || showBookmarks || showBookSearch || showTtsVoices || showChapterSeek) return@LaunchedEffect
        // WebView may report no scroll range briefly after onPageFinished, before layout.
        delay(250)
        if (loadedChapter != chapter || view.height <= 0 || view.contentHeight <= 0) {
            autoScroll = false
            return@LaunchedEffect
        }
        var remainder = 0f
        while (isActive) {
            delay(50)
            if (!view.canScrollVertically(1)) {
                val next = nextSpeechChapter(chapter, book?.chapters?.size ?: 0, autoNextScroll)
                if (next == null) autoScroll = false else { openChapter(next); pendingAutoScrollChapter = next }
                break
            }
            remainder += autoScrollStepPixels(autoScrollSpeed, density)
            val pixels = remainder.toInt()
            if (pixels > 0) { view.scrollBy(0, pixels); remainder -= pixels }
        }
    }
    DisposableEffect(bookKey) {
        onDispose {
            webView?.let { view ->
                if (pageReady && loadedChapter == chapter && loadedChapter >= 0) progressSaver.save(loadedChapter, view.scrollY)
                else progressSaver.cancel()
                view.stopLoading()
                view.destroy()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ReaderKeepScreenOn(keepScreenOn)
        ReaderImmersiveMode(immersive)
        ReaderVolumeKeys(enabled = volumeKeys && pageReady && !ttsPlaying && !ttsPaused && !showSearch && !showBookSearch &&
            !showContents && !showBookmarks && !showTtsVoices && !showChapterSeek, inverted = invertVolumeKeys,
            onNext = { autoScroll = false; webView?.let { it.scrollBy(0, (it.height * 0.8f).toInt()) } },
            onPrevious = { autoScroll = false; webView?.let { it.scrollBy(0, -(it.height * 0.8f).toInt()) } })
        Column(Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_txt_close), color = foreground) }
                Text(stringResource(R.string.reader_epub_title), color = foreground, modifier = Modifier.weight(1f))
                TextButton(onClick = { theme = (theme + 1) % 5; prefs.edit().putInt("theme", theme).apply() }) {
                    Text(stringResource(when (theme) { 1 -> R.string.reader_theme_sepia; 2 -> R.string.reader_theme_light; 3 -> R.string.reader_theme_oled; 4 -> R.string.reader_theme_green; else -> R.string.reader_theme_dark }), color = foreground)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(10); prefs.edit().putInt("font_size", fontSize).apply() }) { Text("A−", color = foreground) }
                Text("${fontSize}px", color = foreground)
                TextButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(48); prefs.edit().putInt("font_size", fontSize).apply() }) { Text("A+", color = foreground) }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = chapter > 0, onClick = {
                    openChapter(chapter - 1)
                }) { Text("‹", color = foreground) }
                Text("${chapter + 1}/${book?.chapters?.size ?: 0}", color = foreground)
                TextButton(enabled = chapter < (book?.chapters?.lastIndex ?: -1), onClick = {
                    openChapter(chapter + 1)
                }) { Text("›", color = foreground) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    fontFamily = when (fontFamily) { "Serif" -> "Sans"; "Sans" -> "Mono"; else -> "Serif" }
                    prefs.edit().putString("font_family", fontFamily).putBoolean("serif", fontFamily != "Sans").apply()
                }) { Text(fontFamily, color = foreground) }
                TextButton(onClick = { keepScreenOn = !keepScreenOn; prefs.edit().putBoolean("keep_screen_on", keepScreenOn).apply() }) {
                    Text(stringResource(if (keepScreenOn) R.string.reader_novel_keep_screen_on else R.string.reader_novel_keep_screen_off), color = foreground)
                }
                TextButton(onClick = { volumeKeys = !volumeKeys; prefs.edit().putBoolean("volume_keys", volumeKeys).apply() }) {
                    Text(stringResource(if (volumeKeys) R.string.reader_novel_volume_on else R.string.reader_novel_volume_off), color = foreground)
                }
                if (volumeKeys) TextButton(onClick = { invertVolumeKeys = !invertVolumeKeys; prefs.edit().putBoolean("invert_volume_keys", invertVolumeKeys).apply() }) {
                    Text(stringResource(if (invertVolumeKeys) R.string.reader_novel_volume_inverted else R.string.reader_novel_volume_normal), color = foreground)
                }
                TextButton(onClick = { immersive = !immersive; prefs.edit().putBoolean("immersive", immersive).apply() }) {
                    Text(stringResource(if (immersive) R.string.reader_immersive_on else R.string.reader_immersive_off), color = foreground)
                }
                TextButton(onClick = { lineHeight = if (lineHeight >= 200) 120 else lineHeight + 20; prefs.edit().putInt("line_height", lineHeight).apply() }) { Text("${lineHeight}%", color = foreground) }
                TextButton(onClick = { margin = when (margin) { 12 -> 24; 24 -> 40; else -> 12 }; prefs.edit().putInt("margin", margin).apply() }) { Text(stringResource(R.string.reader_txt_margin, margin), color = foreground) }
                TextButton(onClick = { alignment = (alignment + 1) % 3; prefs.edit().putInt("alignment", alignment).apply() }) {
                    Text(stringResource(when (alignment) { 1 -> R.string.reader_text_align_start; 2 -> R.string.reader_text_align_justify; else -> R.string.reader_text_align_original }), color = foreground)
                }
                TextButton(onClick = { letterSpacing = when (letterSpacing) { 0 -> 5; 5 -> 10; else -> 0 }; prefs.edit().putInt("letter_spacing_tenths", letterSpacing).apply() }) {
                    Text(stringResource(R.string.reader_text_letter_spacing, letterSpacing / 10f), color = foreground)
                }
                TextButton(onClick = { hyphenation = !hyphenation; prefs.edit().putBoolean("hyphenation", hyphenation).apply() }) {
                    Text(stringResource(if (hyphenation) R.string.reader_hyphenation_on else R.string.reader_hyphenation_off), color = foreground)
                }
                TextButton(onClick = { paragraphGap = when (paragraphGap) { -1 -> 0; 0 -> 8; 8 -> 16; 16 -> 24; else -> -1 }; prefs.edit().putInt("paragraph_gap", paragraphGap).apply() }) {
                    Text(if (paragraphGap < 0) stringResource(R.string.reader_epub_paragraph_original) else stringResource(R.string.reader_epub_paragraph_gap, paragraphGap), color = foreground)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = pageReady && !ttsPlaying, onClick = { autoScroll = !autoScroll }) {
                    Text(stringResource(if (autoScroll) R.string.reader_auto_scroll_stop else R.string.reader_auto_scroll_start), color = foreground)
                }
                TextButton(onClick = {
                    autoNextScroll = !autoNextScroll
                    if (!autoNextScroll) pendingAutoScrollChapter = null
                    prefs.edit().putBoolean("auto_next_scroll", autoNextScroll).apply()
                }) { Text(stringResource(if (autoNextScroll) R.string.reader_epub_auto_next_on else R.string.reader_epub_auto_next_off), color = foreground) }
                TextButton(onClick = {
                    scrollTimerMinutes = nextSpeechTimerMinutes(scrollTimerMinutes)
                    scrollTimerDeadline = if (scrollTimerMinutes == 0) 0L else android.os.SystemClock.elapsedRealtime() + scrollTimerMinutes * 60_000L
                }) {
                    Text(if (scrollTimerMinutes == 0) stringResource(R.string.reader_scroll_timer_off) else stringResource(R.string.reader_scroll_timer_minutes, scrollTimerMinutes), color = foreground)
                }
                TextButton(onClick = {
                    autoScrollSpeed = when (autoScrollSpeed) { 20 -> 40; 40 -> 80; 80 -> 120; else -> 20 }
                    prefs.edit().putInt("auto_scroll_speed", autoScrollSpeed).apply()
                }) { Text(stringResource(R.string.reader_novel_auto_scroll_speed, autoScrollSpeed), color = foreground) }
                TextButton(enabled = book != null && ttsReady, onClick = {
                    if (ttsPlaying) {
                        ttsPlaying = false
                        ttsPaused = true
                        ttsSession++
                        tts?.stop()
                    } else {
                        autoScroll = false
                        pendingAutoScrollChapter = null
                        ttsError = false
                        ttsSession++
                        if (!ttsPaused) { ttsIndex = 0; ttsSegments = null }
                        ttsPaused = false
                        ttsPlaying = true
                    }
                }) { Text(stringResource(when { ttsPlaying -> R.string.reader_tts_pause; ttsPaused -> R.string.reader_tts_resume; else -> R.string.reader_epub_tts_chapter }), color = foreground) }
                if (ttsPlaying || ttsPaused) {
                    val lines = ttsSegments.orEmpty()
                    val previous = adjacentSpeechSegment(lines, ttsIndex, false)
                    val next = adjacentSpeechSegment(lines, ttsIndex, true)
                    TextButton(enabled = previous != null, onClick = {
                        if (previous != null) { ttsSession++; tts?.stop(); ttsIndex = previous }
                    }) { Text(stringResource(R.string.reader_tts_previous_segment), color = foreground) }
                    TextButton(enabled = next != null, onClick = {
                        if (next != null) { ttsSession++; tts?.stop(); ttsIndex = next }
                    }) { Text(stringResource(R.string.reader_tts_next_segment), color = foreground) }
                }
                if (ttsPlaying || ttsPaused) TextButton(onClick = { stopTts() }) { Text(stringResource(R.string.reader_txt_tts_stop), color = foreground) }
                TextButton(onClick = {
                    ttsRate = when (ttsRate) { 0.8f -> 1f; 1f -> 1.2f; 1.2f -> 1.5f; else -> 0.8f }
                    prefs.edit().putFloat("tts_rate", ttsRate).apply()
                }) { Text(stringResource(R.string.reader_txt_tts_speed, ttsRate), color = foreground) }
                TextButton(onClick = {
                    ttsPitch = nextNovelSpeechPitch(ttsPitch)
                    prefs.edit().putFloat("tts_pitch", ttsPitch).apply()
                }) { Text(stringResource(R.string.reader_tts_pitch, ttsPitch), color = foreground) }
                TextButton(enabled = ttsReady, onClick = { showTtsVoices = true }) { Text(stringResource(R.string.reader_tts_voice), color = foreground) }
                TextButton(onClick = {
                    ttsTimerMinutes = nextSpeechTimerMinutes(ttsTimerMinutes)
                    ttsTimerDeadline = if (ttsTimerMinutes == 0) 0L else android.os.SystemClock.elapsedRealtime() + ttsTimerMinutes * 60_000L
                }) {
                    Text(if (ttsTimerMinutes == 0) stringResource(R.string.reader_tts_timer_off) else stringResource(R.string.reader_tts_timer_minutes, ttsTimerMinutes), color = foreground)
                }
                TextButton(onClick = {
                    ttsContinue = !ttsContinue
                    prefs.edit().putBoolean("tts_continue", ttsContinue).apply()
                }) { Text(stringResource(if (ttsContinue) R.string.reader_epub_tts_continue_on else R.string.reader_epub_tts_continue_off), color = foreground) }
                TextButton(enabled = book != null, onClick = { showChapterSeek = !showChapterSeek }) {
                    Text(stringResource(R.string.reader_epub_seek), color = foreground)
                }
                TextButton(enabled = book != null, onClick = { showContents = true }) { Text(stringResource(R.string.reader_epub_contents), color = foreground) }
                TextButton(enabled = book != null, onClick = { showSearch = !showSearch }) { Text(stringResource(R.string.reader_txt_search), color = foreground) }
                TextButton(enabled = book != null && loadedChapter >= 0 && bookmarks.size < 200, onClick = {
                    saveBookmarks(addNovelBookmark(bookmarks, NovelBookmark(loadedChapter, (webView?.scrollY ?: 0).coerceAtLeast(0))))
                }) { Text(stringResource(R.string.reader_txt_bookmark), color = foreground) }
                TextButton(enabled = book != null, onClick = { showBookmarks = true }) {
                    Text(stringResource(R.string.reader_bookmarks, bookmarks.size), color = foreground)
                }
            }
            if (ttsPlaying || ttsPaused) Text(stringResource(R.string.reader_tts_pause_hint), color = foreground, modifier = Modifier.padding(horizontal = 16.dp))
            if (ttsVoiceUnavailable) Text(stringResource(R.string.reader_tts_voice_unavailable), color = foreground, modifier = Modifier.padding(horizontal = 16.dp))
            if (showTtsVoices && ttsReady) tts?.let { engine ->
                NovelSpeechVoicePicker(engine, ttsVoice, foreground, background,
                    onSelect = { ttsVoice = it; prefs.edit().putString("tts_voice", it).apply() },
                    onDismiss = { showTtsVoices = false })
            }
            if (ttsError) Text(stringResource(R.string.reader_epub_tts_error), color = foreground, modifier = Modifier.padding(horizontal = 16.dp))
            if (ttsPlaying) Text(stringResource(if (ttsSegments == null) R.string.reader_epub_tts_loading else R.string.reader_epub_tts_hint), color = foreground, modifier = Modifier.padding(horizontal = 16.dp))
            if (showSearch) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = {
                        searchJob?.cancel(); searchingBook = false; searchResult = null; pendingFind = null
                        query = it.take(100)
                    }, singleLine = true,
                        modifier = Modifier.weight(1f), textStyle = TextStyle(color = foreground),
                        label = { Text(stringResource(R.string.reader_txt_search), color = foreground) })
                    TextButton(enabled = query.isNotBlank(), onClick = { webView?.findNext(false) }) { Text("‹", color = foreground) }
                    TextButton(enabled = query.isNotBlank(), onClick = { webView?.findNext(true) }) { Text("›", color = foreground) }
                }
                TextButton(enabled = query.isNotBlank() && book != null && !searchingBook, onClick = {
                    val currentBook = book ?: return@TextButton
                    val searched = query.trim()
                    searchJob?.cancel()
                    showBookSearch = true
                    searchingBook = true
                    searchResult = null
                    searchJob = scope.launch {
                        try {
                            searchResult = withContext(Dispatchers.IO) {
                                currentBook.search(searched) { coroutineContext.ensureActive() }
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            searchResult = EpubSearchResults(emptyList(), incomplete = true)
                        } finally {
                            if (searchJob === coroutineContext[Job]) searchingBook = false
                        }
                    }
                }) { Text(stringResource(R.string.reader_epub_search_book), color = foreground) }
            }
            val activeBook = book
            if (showChapterSeek && activeBook != null) EpubChapterSeekBar(chapter, activeBook.chapters.size, foreground) { target ->
                openChapter(target)
            }
            if (showBookSearch && activeBook != null) {
                Dialog(onDismissRequest = { showBookSearch = false; searchJob?.cancel(); searchingBook = false }) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).background(background).padding(16.dp)) {
                        Text(stringResource(R.string.reader_epub_search_book), color = foreground)
                        if (searchingBook) CircularProgressIndicator()
                        searchResult?.let { result ->
                            if (result.incomplete) Text(stringResource(R.string.reader_epub_search_incomplete), color = foreground)
                            if (result.matches.isEmpty()) Text(stringResource(R.string.reader_epub_search_empty), color = foreground)
                            LazyColumn {
                                itemsIndexed(result.matches) { _, match ->
                                    TextButton(onClick = {
                                        showBookSearch = false
                                        showSearch = true
                                        openChapter(match.chapter)
                                        pendingScroll = null
                                        pendingFind = PendingEpubFind(match.chapter, query.trim(), match.ordinal)
                                        if (match.chapter == loadedChapter && pageReady) webView?.findAllAsync(query.trim())
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(activeBook.chapterTitles[match.chapter] ?: stringResource(R.string.reader_epub_chapter, match.chapter + 1), color = foreground)
                                            Text(match.excerpt, color = foreground)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (showBookmarks && activeBook != null) NovelBookmarkDialog(
                bookmarks = bookmarks, bookKey = bookKey, kind = "epub", foreground = foreground, background = background,
                label = { mark ->
                    val title = activeBook.chapterTitles.getOrNull(mark.index)
                        ?: stringResource(R.string.reader_epub_chapter, mark.index + 1)
                    stringResource(R.string.reader_bookmark_epub_position, title, mark.offset)
                },
                exportLabel = { mark ->
                    val title = activeBook.chapterTitles.getOrNull(mark.index)
                        ?: context.getString(R.string.reader_epub_chapter, mark.index + 1)
                    context.getString(R.string.reader_bookmark_epub_position, title, mark.offset)
                },
                canNavigate = { it.index in activeBook.chapters.indices },
                onNavigate = { mark -> showBookmarks = false; openChapter(mark.index, mark.offset) },
                onChange = ::saveBookmarks,
                onDismiss = { showBookmarks = false }
            )
            if (showContents && activeBook != null) {
                Dialog(onDismissRequest = { showContents = false }) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).background(background).padding(16.dp)) {
                        Text(stringResource(R.string.reader_epub_contents), color = foreground)
                        LazyColumn {
                            if (activeBook.tableOfContents.isNotEmpty()) {
                                itemsIndexed(activeBook.tableOfContents) { _, entry ->
                                    TextButton(onClick = { openChapter(entry.chapter, fragment = entry.fragment); showContents = false },
                                        modifier = Modifier.fillMaxWidth().padding(start = (entry.depth * 12).dp)) {
                                        Text(entry.title, color = foreground, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                item { Text(stringResource(R.string.reader_epub_all_sections), color = foreground, modifier = Modifier.padding(top = 12.dp)) }
                            }
                            itemsIndexed(activeBook.chapters) { index, _ ->
                                TextButton(onClick = { openChapter(index); showContents = false }, modifier = Modifier.fillMaxWidth()) {
                                    Text(activeBook.chapterTitles[index] ?: stringResource(R.string.reader_epub_chapter, index + 1), color = foreground, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
            when {
                error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.reader_epub_error), color = foreground, modifier = Modifier.padding(24.dp))
                }
                activeBook == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Box(Modifier.fillMaxSize()) {
                    key(activeBook) {
                        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                            WebView(ctx).apply {
                                setOnTouchListener { _, event ->
                                    if (event.actionMasked == MotionEvent.ACTION_DOWN) { autoScroll = false; pendingAutoScrollChapter = null }
                                    false
                                }
                                setOnScrollChangeListener { _, _, y, _, _ ->
                                    if (pageReady && loadedChapter == chapter && loadedChapter >= 0)
                                        progressSaver.schedule(loadedChapter, y)
                                }
                                settings.javaScriptEnabled = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.domStorageEnabled = false
                                settings.setSupportMultipleWindows(false)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val url = request.url
                                        if (url.scheme != "https" || url.host != "epub.kitsugi.invalid") return true
                                        val path = EpubBook.resolveEpubPath("", url.encodedPath.orEmpty().removePrefix("/")) ?: return true
                                        val target = activeBook.chapters.indexOf(path)
                                        if (target < 0) return true
                                        // Same-document anchors are handled by WebView without fetching another document.
                                        if (target == chapter && url.fragment != null) return false
                                        openChapter(target, fragment = url.fragment)
                                        return true
                                    }
                                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                                        val url = request.url
                                        val path = if (url.scheme == "https" && url.host == "epub.kitsugi.invalid")
                                            EpubBook.resolveEpubPath("", url.encodedPath.orEmpty().removePrefix("/")) else null
                                        val bytes = try { path?.let { activeBook.read(it) } } catch (_: Exception) { null }
                                        val mime = when (path?.substringAfterLast('.', "")?.lowercase()) {
                                            "css" -> "text/css"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"
                                            "gif" -> "image/gif"; "webp" -> "image/webp"; "svg" -> "image/svg+xml"
                                            "woff" -> "font/woff"; "woff2" -> "font/woff2"; "ttf" -> "font/ttf"
                                            else -> "application/octet-stream"
                                        }
                                        return WebResourceResponse(mime, null, ByteArrayInputStream(bytes ?: ByteArray(0)))
                                    }
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        val finishedPath = url?.let { finished ->
                                            val parsed = Uri.parse(finished)
                                            if (parsed.scheme == "https" && parsed.host == "epub.kitsugi.invalid")
                                                EpubBook.resolveEpubPath("", parsed.encodedPath.orEmpty().removePrefix("/")) else null
                                        }
                                        if (loadedChapter != chapter || finishedPath != activeBook.chapters.getOrNull(chapter)) return
                                        pageReady = true
                                        val anchor = pendingFragment
                                        val scroll = pendingScroll
                                        progressSaver.save(chapter, scroll ?: if (anchor != null) 0 else view.scrollY)
                                        pendingFragment = null
                                        pendingScroll = null
                                        if (anchor != null) {
                                            val targetChapter = chapter
                                            view.post {
                                                if (loadedChapter == targetChapter && chapter == targetChapter)
                                                    view.loadUrl(chapterBase(activeBook.chapters[targetChapter]) + "#" + Uri.encode(anchor))
                                            }
                                        } else if (scroll != null) {
                                            val targetChapter = chapter
                                            view.post { if (loadedChapter == targetChapter && chapter == targetChapter) view.scrollTo(0, scroll) }
                                        }
                                    }
                                }
                                setFindListener { active, count, done ->
                                    val target = pendingFind
                                    if (done && target != null && loadedChapter == target.chapter && chapter == target.chapter && query.trim() == target.query) {
                                        if (count > 0 && active < target.ordinal.coerceAtMost(count - 1)) findNext(true)
                                        else pendingFind = null
                                    }
                                }
                                webView = this
                            }
                        }, update = { view ->
                            val content = html?.takeIf { htmlChapter == chapter }
                            val renderKey = chapter to content
                            if (content != null && view.tag != renderKey) {
                                if (loadedChapter == chapter && loadedChapter >= 0 && pendingFragment == null && pendingScroll == null) {
                                    pendingScroll = view.scrollY
                                    progressSaver.save(loadedChapter, view.scrollY)
                                }
                                loadedChapter = chapter
                                view.tag = renderKey
                                pageReady = false
                                // Encode the path as URL segments without exposing a local file path.
                                val base = chapterBase(activeBook.chapters[chapter])
                                view.loadDataWithBaseURL(base, content, "text/html", "UTF-8", null)
                            }
                        })
                    }
                    if (html == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
