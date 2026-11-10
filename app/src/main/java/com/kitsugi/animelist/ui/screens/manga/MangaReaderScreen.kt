package com.kitsugi.animelist.ui.screens.manga

import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.buildMangaDisplayPages
import com.kitsugi.animelist.data.manga.mangaDisplayPageIndexFor
import com.kitsugi.animelist.data.manga.nextMangaColorFilter
import com.kitsugi.animelist.data.manga.nextMangaReadingMode
import com.kitsugi.animelist.data.manga.shouldUseDoublePage
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.screens.manga.components.MangaPagerReader
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    chapter: MangaChapter,
    mangaDetails: com.kitsugi.animelist.data.manga.MangaDetails,
    source: MangaSource,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: MangaReaderViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}_${chapter.url}",
        factory = MangaReaderViewModel.Factory(context, source, mangaDetails, chapter),
    )
    val uiState by viewModel.uiState.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val currentChapter = uiState.currentChapter
    val readingMode = uiState.readingMode
    val currentPage = uiState.currentPageIndex
    val readerOptions = uiState.readerOptions
    val loader = viewModel.loader
    val speechState by viewModel.speechState.collectAsState()
    val scope = rememberCoroutineScope()
    var menuVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(readerOptions.keepScreenOn, readerOptions.fullscreen, activity) {
        val window = activity?.window
        if (window != null) {
            if (readerOptions.keepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (readerOptions.fullscreen) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val doublePage = shouldUseDoublePage(readingMode, isLandscape, readerOptions)
    val displayPages = remember(pages, doublePage) { buildMangaDisplayPages(pages, doublePage) }
    val pagerState = rememberPagerState(
        initialPage = mangaDisplayPageIndexFor(displayPages, currentPage),
    ) { displayPages.size }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    var restoredChapterUrl by remember { mutableStateOf<String?>(null) }
    var pagerSyncTarget by remember { mutableStateOf<Int?>(null) }

    // Restore once per chapter. Layout changes reuse the real page index, never a group index.
    LaunchedEffect(currentChapter.url, pages) {
        if (pages.isEmpty() || restoredChapterUrl == currentChapter.url) return@LaunchedEffect
        val target = currentPage.coerceIn(0, pages.lastIndex)
        if (readingMode == ReadingMode.Webtoon) {
            pagerSyncTarget = null
            listState.scrollToItem(target)
        } else {
            val displayIndex = mangaDisplayPageIndexFor(displayPages, target)
            pagerSyncTarget = displayIndex
            pagerState.scrollToPage(displayIndex)
        }
        restoredChapterUrl = currentChapter.url
    }

    LaunchedEffect(readingMode, doublePage) {
        if (pages.isEmpty() || restoredChapterUrl != currentChapter.url) return@LaunchedEffect
        val target = currentPage.coerceIn(0, pages.lastIndex)
        if (readingMode == ReadingMode.Webtoon) {
            pagerSyncTarget = null
            listState.scrollToItem(target)
        } else {
            val displayIndex = mangaDisplayPageIndexFor(displayPages, target)
            pagerSyncTarget = displayIndex
            if (pagerState.currentPage != displayIndex) pagerState.scrollToPage(displayIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage, displayPages, readingMode, restoredChapterUrl) {
        if (restoredChapterUrl == currentChapter.url && readingMode != ReadingMode.Webtoon && pages.isNotEmpty()) {
            val syncTarget = pagerSyncTarget
            if (syncTarget != null) {
                if (pagerState.currentPage != syncTarget) return@LaunchedEffect
                pagerSyncTarget = null
            }
            val displayPage = displayPages.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
            viewModel.onPageChanged(currentChapter.url, displayPage.pages.last().index, pages.size)
            loader.onPageChanged(displayPage.firstPageIndex)
        }
    }

    fun nextPage() {
        if (readingMode == ReadingMode.Webtoon) {
            val current = listState.firstVisibleItemIndex
            if (current < pages.lastIndex) scope.launch { listState.animateScrollToItem(current + 1) }
            else viewModel.goToNextChapter()
        } else if (pagerState.currentPage < displayPages.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        } else viewModel.goToNextChapter()
    }

    fun previousPage() {
        if (readingMode == ReadingMode.Webtoon) {
            val current = listState.firstVisibleItemIndex
            if (current > 0) scope.launch { listState.animateScrollToItem(current - 1) }
            else viewModel.goToPreviousChapter()
        } else if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else viewModel.goToPreviousChapter()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(readerOptions.readerTheme.backgroundArgb))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val isVolumeKey = keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
                    keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
                if (!readerOptions.volumeKeys || !isVolumeKey || event.type != KeyEventType.KeyDown ||
                    event.nativeKeyEvent.repeatCount > 0 || showSettings
                ) return@onPreviewKeyEvent false
                val volumeDown = keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
                val next = if (readerOptions.reverseVolumeKeys) !volumeDown else volumeDown
                if (next) nextPage() else previousPage()
                true
            },
    ) {
        if (uiState.isLoadingPages) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF7C4DFF), strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.inline_loading_page_list_31ccd61), color = Color.White.copy(0.6f), fontSize = 13.sp)
                }
            }
        } else if (uiState.pagesError != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(uiState.pagesError ?: stringResource(R.string.ui_error_generic), color = Color.White.copy(0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
                    uiState.pagesErrorCode?.let { code ->
                        Spacer(Modifier.height(10.dp))
                        com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code)
                    }
                    Spacer(Modifier.height(24.dp))
                    KitsugiButton(
                        onClick = viewModel::retryLoadPages,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                    ) { Text("Tekrar Dene", color = Color.White) }
                }
            }
        } else if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.inline_no_pages_found_d05c840), color = Color.White.copy(0.5f))
            }
        } else if (readingMode == ReadingMode.Webtoon) {
            MangaWebtoonReader(
                pages = pages,
                listState = listState,
                loader = loader,
                colorFilterType = uiState.colorFilterType,
                fitMode = uiState.fitMode,
                readerOptions = readerOptions,
                hasNextChapter = viewModel.hasNextChapter,
                nextChapterName = viewModel.nextChapterName,
                onGoToNextChapter = viewModel::goToNextChapter,
                onCenterTap = { menuVisible = !menuVisible },
                onPageChanged = { index ->
                    if (restoredChapterUrl == currentChapter.url) {
                        viewModel.onPageChanged(currentChapter.url, index, pages.size)
                        loader.onPageChanged(index)
                    }
                },
                onNextPage = ::nextPage,
                onPrevPage = ::previousPage,
            )
        } else {
            MangaPagerReader(
                pages = pages,
                pagerState = pagerState,
                loader = loader,
                readingMode = readingMode,
                colorFilterType = uiState.colorFilterType,
                fitMode = uiState.fitMode,
                doublePage = doublePage,
                readerOptions = readerOptions,
                onCenterTap = { menuVisible = !menuVisible },
                onNextPage = ::nextPage,
                onPrevPage = ::previousPage,
            )
        }

        if (uiState.customBrightness < 1f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - uiState.customBrightness)))
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent)),
                ).padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.geri), tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mangaDetails.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(currentChapter.name, color = Color.White.copy(0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (mangaDetails.isNovelContent && pages.any { it.textParagraphs != null }) {
                        IconButton(onClick = viewModel::toggleSpeechPlayback) {
                            Icon(
                                if (speechState == com.kitsugi.animelist.data.manga.MangaNovelSpeechController.State.Speaking) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                stringResource(R.string.okuyucu_speech), tint = Color.White,
                            )
                        }
                        if (speechState != com.kitsugi.animelist.data.manga.MangaNovelSpeechController.State.Idle) {
                            IconButton(onClick = viewModel::stopSpeech) {
                                Icon(Icons.Filled.Stop, stringResource(R.string.okuyucu_stop_speech), tint = Color.White)
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.toggleCurrentPageBookmark() }) {
                        Icon(
                            if (uiState.bookmarks.any { it.chapterUrl == currentChapter.url && it.pageIndex == currentPage }) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            stringResource(R.string.okuyucu_add_bookmark), tint = Color.White,
                        )
                    }
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.okuyucu_bookmarks), tint = Color.White)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.okuyucu_ayarlari), tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = menuVisible && pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF161622).copy(alpha = 0.95f))
                    .navigationBarsPadding().padding(vertical = 12.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::goToPreviousChapter, enabled = viewModel.hasPreviousChapter) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onceki_bolum), tint = Color.White.copy(if (viewModel.hasPreviousChapter) 1f else 0.3f))
                    }
                    if (pages.size > 1) {
                        Row(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF26263B)).padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${currentPage + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            var sliderValue by remember(currentPage) { mutableStateOf((currentPage + 1).toFloat()) }
                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                onValueChangeFinished = {
                                    val target = sliderValue.toInt().minus(1).coerceIn(0, pages.lastIndex)
                                    scope.launch {
                                        if (readingMode == ReadingMode.Webtoon) listState.scrollToItem(target)
                                        else pagerState.scrollToPage(mangaDisplayPageIndexFor(displayPages, target))
                                    }
                                },
                                valueRange = 1f..pages.size.toFloat(),
                                colors = SliderDefaults.colors(activeTrackColor = Color(0xFF7C4DFF), inactiveTrackColor = Color(0xFF3D3D5C), thumbColor = Color(0xFF7C4DFF)),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                            Text(
                                "${pages.size}" + if (readerOptions.showProgressPercent) " • ${((currentPage + 1) * 100 / pages.size)}%" else "",
                                color = Color.White.copy(0.6f), fontSize = 12.sp,
                            )
                        }
                    } else Spacer(Modifier.weight(1f))
                    IconButton(onClick = viewModel::goToNextChapter, enabled = viewModel.hasNextChapter) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.sonraki_bolum), tint = Color.White.copy(if (viewModel.hasNextChapter) 1f else 0.3f))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    val modeIcon = when (readingMode) {
                        ReadingMode.RightToLeft -> Icons.AutoMirrored.Filled.MenuBook
                        ReadingMode.LeftToRight -> Icons.Default.SwapHoriz
                        ReadingMode.Vertical, ReadingMode.Webtoon -> Icons.Default.ViewStream
                    }
                    IconButton(onClick = { viewModel.setReadingMode(nextMangaReadingMode(readingMode)) }) {
                        Icon(modeIcon, stringResource(R.string.okuma_yonu), tint = Color.White)
                    }
                    IconButton(onClick = {
                        val orientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        } else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        context.findActivity()?.let { it.requestedOrientation = orientation }
                    }) {
                        Icon(Icons.Default.ScreenRotation, stringResource(R.string.ekran_dondurme), tint = Color.White)
                    }
                    IconButton(onClick = {
                        val next = when (uiState.fitMode) {
                            MangaFitMode.FitScreen -> MangaFitMode.FitWidth
                            MangaFitMode.FitWidth -> MangaFitMode.FitHeight
                            MangaFitMode.FitHeight -> MangaFitMode.FitScreen
                        }
                        viewModel.setFitMode(next)
                    }) {
                        Icon(Icons.Default.CropFree, stringResource(R.string.sigdirma_modu), tint = Color.White)
                    }
                    // Replaces the redundant direction shortcut with the requested colour shortcut.
                    IconButton(onClick = { viewModel.setColorFilter(nextMangaColorFilter(uiState.colorFilterType)) }) {
                        Icon(
                            Icons.Default.ColorLens,
                            mangaColorFilterLabel(uiState.colorFilterType),
                            tint = if (uiState.colorFilterType == ColorFilterType.Normal) Color.White else Color(0xFF80CBC4),
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.BrightnessHigh, stringResource(R.string.manga_brightness), tint = Color.White)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.ayarlar), tint = Color.White)
                    }
                }
            }
        }
    }

    if (showBookmarks) {
        MangaBookmarkSheet(
            bookmarks = uiState.bookmarks,
            onDismiss = { showBookmarks = false },
            onDelete = viewModel::removeBookmark,
            onOpen = { bookmark ->
                showBookmarks = false
                if (bookmark.chapterUrl != currentChapter.url) {
                    val targetChapter = uiState.chapterList.firstOrNull { it.url == bookmark.chapterUrl }
                    if (targetChapter != null) viewModel.goToChapter(targetChapter, bookmark.pageIndex)
                }
                scope.launch {
                    if (readingMode == ReadingMode.Webtoon) listState.scrollToItem(bookmark.pageIndex.coerceIn(0, pages.lastIndex))
                    else pagerState.scrollToPage(mangaDisplayPageIndexFor(displayPages, bookmark.pageIndex.coerceIn(0, pages.lastIndex)))
                }
            },
        )
    }

    if (showSettings) {
        MangaReaderSettingsSheet(
            readingMode = readingMode,
            colorFilter = uiState.colorFilterType,
            fitMode = uiState.fitMode,
            brightness = uiState.customBrightness,
            options = readerOptions,
            onReadingModeChange = viewModel::setReadingMode,
            onColorFilterChange = viewModel::setColorFilter,
            onFitModeChange = viewModel::setFitMode,
            onBrightnessChange = viewModel::setCustomBrightness,
            onOptionsChange = viewModel::setReaderOptions,
            onDismiss = { showSettings = false },
        )
    }
}

private fun mangaColorFilterLabel(filter: ColorFilterType): String = when (filter) {
    ColorFilterType.Normal -> "Renk: Normal"
    ColorFilterType.Grayscale -> "Renk: Siyah-beyaz"
    ColorFilterType.Sepia -> "Renk: Sepya"
    ColorFilterType.Invert -> "Renk: Ters negatif"
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
