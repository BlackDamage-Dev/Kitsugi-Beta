package com.kitsugi.animelist.ui.tv.manga

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.ui.screens.manga.MangaReaderViewModel
import com.kitsugi.animelist.ui.screens.manga.readerScrollFraction
import com.kitsugi.animelist.ui.screens.manga.readerPreloadCount
import com.kitsugi.animelist.ui.screens.manga.components.MangaPagerReader
import com.kitsugi.animelist.ui.screens.manga.components.readerSpreadCount
import com.kitsugi.animelist.ui.screens.manga.components.readerSpreadForPage
import com.kitsugi.animelist.ui.screens.manga.components.readerSpreadLast
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
import com.kitsugi.animelist.ui.screens.manga.components.WebtoonAutoScroll
import com.kitsugi.animelist.ui.screens.manga.components.ReaderVolumeKeys
import com.kitsugi.animelist.ui.screens.manga.components.readerBackgroundColor
import com.kitsugi.animelist.ui.utils.requestFocusAfterFrames
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * TV-Native Manga Okuyucu Ekranı.
 *
 * — D-pad odaklı sayfa çevirme (RTL/LTR)
 * — Webtoon modunda D-pad yukarı/aşağı tuşları ile yumuşak kaydırma desteği
 * — DPAD_CENTER (Select) tuşu ile HUD menüsünü açıp/kapatma
 * — TV'ye özel HUD barları ve D-pad uyumlu Ayarlar Paneli
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvMangaReaderScreen(
    chapter: MangaChapter,
    mangaDetails: MangaDetails,
    source: MangaSource,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: MangaReaderViewModel = viewModel(
        key = "tv_${source.name}_${mangaDetails.url}_${chapter.url}",
        factory = MangaReaderViewModel.Factory(
            context = context,
            source = source,
            mangaDetails = mangaDetails,
            initialChapter = chapter
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    val pages by viewModel.pages.collectAsState()
    com.kitsugi.animelist.ui.screens.manga.components.ReaderKeepScreenOn(uiState.keepScreenOn)
    com.kitsugi.animelist.ui.screens.manga.components.ReaderImmersiveMode(uiState.immersive)

    val currentChapter = uiState.currentChapter
    val readingMode = uiState.readingMode
    val currentPage = uiState.currentPageIndex
    val loader = viewModel.loader

    val scope = rememberCoroutineScope()
    var hudVisible by remember { mutableStateOf(true) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var autoScrollEnabled by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) autoScrollEnabled = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(readingMode) {
        if (readingMode != ReadingMode.Webtoon) autoScrollEnabled = false
    }

    val useSpreads = uiState.doublePage && (readingMode == ReadingMode.LeftToRight || readingMode == ReadingMode.RightToLeft)
    val coverAlone = useSpreads && uiState.coverAlone
    val pagerState = rememberPagerState(initialPage = 0) { readerSpreadCount(pages.size, useSpreads, coverAlone) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

    var hasRestoredProgress by remember(currentChapter.url) { mutableStateOf(false) }
    WebtoonAutoScroll(
        enabled = autoScrollEnabled && readingMode == ReadingMode.Webtoon && !showSettingsPanel &&
            !uiState.isLoadingPages && hasRestoredProgress && pages.isNotEmpty(),
        listState = listState,
        speedDpPerSecond = uiState.webtoonAutoScrollSpeed,
        onEnd = {
            autoScrollEnabled = false
            viewModel.onWebtoonChapterEnd(currentChapter.url, pages.size)
            if (uiState.webtoonAutoNext && viewModel.hasNextChapter) viewModel.goToNextChapter()
        }
    )

    // Focus yönetimi
    val screenFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }

    ReaderVolumeKeys(
        enabled = uiState.volumeKeys && !showSettingsPanel && hasRestoredProgress && !uiState.isLoadingPages && pages.isNotEmpty(),
        inverted = uiState.invertVolumeKeys,
        onNext = {
            autoScrollEnabled = false
            if (readingMode == ReadingMode.Webtoon) {
                if (listState.canScrollForward) scope.launch {
                    listState.animateScrollBy((listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) * readerScrollFraction(uiState.webtoonScrollPercent))
                } else {
                    viewModel.onWebtoonChapterEnd(currentChapter.url, pages.size)
                    viewModel.goToNextChapter()
                }
            } else if (pagerState.currentPage < pagerState.pageCount - 1) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            } else viewModel.goToNextChapter()
        },
        onPrevious = {
            autoScrollEnabled = false
            if (readingMode == ReadingMode.Webtoon) {
                if (listState.canScrollBackward) scope.launch {
                    listState.animateScrollBy((listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) * -readerScrollFraction(uiState.webtoonScrollPercent))
                } else viewModel.goToPreviousChapter()
            } else if (pagerState.currentPage > 0) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            } else viewModel.goToPreviousChapter()
        }
    )

    var previousReadingMode by remember(currentChapter.url) { mutableStateOf(readingMode) }
    var previousSpread by remember(currentChapter.url) { mutableStateOf(useSpreads to coverAlone) }
    var synchronizingMode by remember(currentChapter.url) { mutableStateOf(false) }

    LaunchedEffect(readingMode, useSpreads, coverAlone) {
        if (previousReadingMode != readingMode || previousSpread != (useSpreads to coverAlone)) {
            previousReadingMode = readingMode
            previousSpread = useSpreads to coverAlone
            if (hasRestoredProgress && !uiState.isLoadingPages && pages.isNotEmpty()) {
                synchronizingMode = true
                try {
                    val target = currentPage.coerceIn(0, pages.lastIndex)
                    if (readingMode == ReadingMode.Webtoon) listState.scrollToItem(target)
                    else pagerState.scrollToPage(readerSpreadForPage(target, useSpreads, coverAlone))
                } finally {
                    synchronizingMode = false
                }
            }
        }
    }

    LaunchedEffect(pages, currentPage, uiState.isLoadingPages) {
        if (!uiState.isLoadingPages && pages.isNotEmpty() && !hasRestoredProgress) {
            synchronizingMode = true
            try {
                val targetPage = currentPage.coerceIn(0, pages.size - 1)
                if (readingMode == ReadingMode.Webtoon) {
                    listState.scrollToItem(targetPage)
                } else {
                    pagerState.scrollToPage(readerSpreadForPage(targetPage, useSpreads, coverAlone))
                }
                hasRestoredProgress = true
            } finally {
                synchronizingMode = false
            }
        }
    }

    // Pager sayfa değişimi progress kaydetme
    LaunchedEffect(pagerState.currentPage, synchronizingMode, hasRestoredProgress, readingMode, useSpreads, coverAlone) {
        if (hasRestoredProgress && !synchronizingMode && previousReadingMode == readingMode &&
            previousSpread == (useSpreads to coverAlone) && !uiState.isLoadingPages && readingMode != ReadingMode.Webtoon && pages.isNotEmpty()) {
            val pageIndex = readerSpreadLast(pagerState.currentPage, pages.size, useSpreads, coverAlone).coerceIn(0, pages.lastIndex)
            viewModel.onPageChanged(currentChapter.url, pageIndex, pages.size)
        }
    }

    // HUD ilk açılışta veya sayfa odağa geldiğinde otomatik focuslansın.
    // requestFocusAfterFrames: composable node'un layout tree'ye attach olması için
    // 2 frame bekler, ardından 4 deneme yapar — böylece "FocusRequester is not
    // initialized" hatası timing sorunundan değil, gerçek bir yapısal sorundan
    // kaynaklanıyorsa daha kolay tespit edilir.
    LaunchedEffect(Unit) {
        screenFocusRequester.requestFocusAfterFrames(frames = 2)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(screenFocusRequester)
            .onPreviewKeyEvent { event ->
                if (showSettingsPanel) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (autoScrollEnabled) autoScrollEnabled = false
                            // HUD menüsünü aç/kapat
                            hudVisible = !hudVisible
                            if (hudVisible) {
                                scope.launch {
                                    backButtonFocusRequester.requestFocusAfterFrames(frames = 1)
                                }
                            } else {
                                // HUD kapanınca screen node zaten attach — direkt istemek güvenli
                                scope.launch {
                                    screenFocusRequester.requestFocusAfterFrames(frames = 1)
                                }
                            }
                            true
                        }
                        // Webtoon modunda özel dikey kaydırma desteği
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (readingMode == ReadingMode.Webtoon && !hudVisible) {
                                scope.launch {
                                    listState.scrollBy(400f)
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (readingMode == ReadingMode.Webtoon && !hudVisible) {
                                scope.launch {
                                    listState.scrollBy(-400f)
                                }
                                true
                            } else false
                        }
                        // Pager modunda D-pad sol/sağ ile sayfa çevirme
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (readingMode != ReadingMode.Webtoon && !hudVisible) {
                                val isRtl = readingMode == ReadingMode.RightToLeft
                                if (isRtl) {
                                    // RTL modunda sol basıldığında sonraki sayfaya (index + 1)
                                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                    } else {
                                        viewModel.goToNextChapter()
                                    }
                                } else {
                                    // LTR modunda sol basıldığında önceki sayfaya (index - 1)
                                    if (pagerState.currentPage > 0) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                    } else {
                                        viewModel.goToPreviousChapter()
                                    }
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (readingMode != ReadingMode.Webtoon && !hudVisible) {
                                val isRtl = readingMode == ReadingMode.RightToLeft
                                if (isRtl) {
                                    // RTL modunda sağ basıldığında önceki sayfaya
                                    if (pagerState.currentPage > 0) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                    } else {
                                        viewModel.goToPreviousChapter()
                                    }
                                } else {
                                    // LTR modunda sağ basıldığında sonraki sayfaya
                                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                    } else {
                                        viewModel.goToNextChapter()
                                    }
                                }
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {

        // ─── Okuyucu İçerik Katmanı ───────────────────────────────────────────
        if (uiState.isLoadingPages) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KitsugiPlasmaLoader(size = 52.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.inline_loading_chapter_pages_043ccff), color = Color.White.copy(0.7f), fontSize = 13.sp)
                }
            }
        } else if (uiState.pagesError != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = uiState.pagesError ?: stringResource(R.string.ui_error_generic),
                        color = Color.White.copy(0.7f),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.retryLoadPages() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Tekrar Dene", color = Color.White)
                    }
                }
            }
        } else if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.inline_no_readable_pages_found_1c8f2d6), color = Color.White.copy(0.5f))
            }
        } else {
            when (readingMode) {
                ReadingMode.Webtoon -> MangaWebtoonReader(
                    pages = pages,
                    listState = listState,
                    loader = loader,
                    colorFilterType = uiState.colorFilterType,
                    saturation = uiState.saturation,
                    contrast = uiState.contrast,
                    hue = uiState.hue,
                    fitMode = uiState.fitMode,
                    pageBackground = readerBackgroundColor(uiState.readerBackground),
                    autoBackground = uiState.readerBackground == "Auto",
                    showPageNumber = uiState.showPageNumber,
                    invertTapZones = uiState.invertTapZones,
                    tapLayout = uiState.tapLayout,
                    tapGrid = uiState.tapGrid,
                    longTapGrid = uiState.longTapGrid,
                    sidePadding = uiState.webtoonPadding,
                    pageGap = uiState.webtoonGap,
                    cropBorders = uiState.cropBordersWebtoon,
                    zoomEnabled = uiState.webtoonZoom,
                    wideRotation = uiState.wideRotationWebtoon,
                    autoNext = uiState.webtoonAutoNext && !autoScrollEnabled,
                    hasNextChapter = viewModel.hasNextChapter,
                    nextChapterName = if (uiState.skipReadChapters) null else viewModel.nextChapterName,
                    onGoToNextChapter = { viewModel.goToNextChapter() },
                    onChapterEnd = { if (hasRestoredProgress && !uiState.isLoadingPages) viewModel.onWebtoonChapterEnd(currentChapter.url, pages.size) },
                    onCenterTap = {
                        if (autoScrollEnabled) autoScrollEnabled = false
                        hudVisible = !hudVisible
                        if (hudVisible) {
                            scope.launch {
                                try { backButtonFocusRequester.requestFocus() } catch (e: Exception) {}
                            }
                        }
                    },
                    onPageChanged = { idx ->
                        if (hasRestoredProgress && !synchronizingMode && !uiState.isLoadingPages) {
                            viewModel.onPageChanged(currentChapter.url, idx, pages.size)
                        }
                    },
                    onNextPage = {
                        val current = listState.firstVisibleItemIndex
                        if (uiState.webtoonTapScroll) {
                            scope.launch { listState.animateScrollBy((listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) * readerScrollFraction(uiState.webtoonScrollPercent)) }
                        } else if (current < pages.size - 1) {
                            scope.launch { listState.animateScrollToItem(current + 1) }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage = {
                        val current = listState.firstVisibleItemIndex
                        if (uiState.webtoonTapScroll) {
                            scope.launch { listState.animateScrollBy((listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) * -readerScrollFraction(uiState.webtoonScrollPercent)) }
                        } else if (current > 0) {
                            scope.launch { listState.animateScrollToItem(current - 1) }
                        } else {
                            viewModel.goToPreviousChapter()
                        }
                    }
                )
                else -> MangaPagerReader(
                    pages = pages,
                    pagerState = pagerState,
                    loader = loader,
                    readingMode = readingMode,
                    colorFilterType = uiState.colorFilterType,
                    saturation = uiState.saturation,
                    contrast = uiState.contrast,
                    hue = uiState.hue,
                    fitMode = uiState.fitMode,
                    pageBackground = readerBackgroundColor(uiState.readerBackground),
                    autoBackground = uiState.readerBackground == "Auto",
                    showPageNumber = uiState.showPageNumber,
                    invertTapZones = uiState.invertTapZones,
                    tapLayout = uiState.tapLayout,
                    tapGrid = uiState.tapGrid,
                    longTapGrid = uiState.longTapGrid,
                    doublePage = useSpreads,
                    coverAlone = coverAlone,
                    prefetchPages = readerPreloadCount(uiState.preloadAhead, webtoon = false),
                    cropBorders = uiState.cropBorders,
                    wideRotation = uiState.wideRotation,
                    onCenterTap = {
                        hudVisible = !hudVisible
                        if (hudVisible) {
                            scope.launch {
                                try { backButtonFocusRequester.requestFocus() } catch (e: Exception) {}
                            }
                        }
                    },
                    onNextPage = {
                        if (pagerState.currentPage < pagerState.pageCount - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        } else {
                            viewModel.goToPreviousChapter()
                        }
                    }
                )
            }
        }

        // ─── Özel Karartma Parlaklık Filtresi ──────────────────────────────────
        if (uiState.customBrightness < 1.0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 1.0f - uiState.customBrightness))
            )
        }

        // ─── TV HUD: Üst Bar ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.9f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .focusRequester(backButtonFocusRequester)
                            .onFocusChanged { isBackFocused = it.isFocused },
                        colors = IconButtonDefaults.colors(
                            containerColor = if (isBackFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mangaDetails.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentChapter.name,
                            color = Color.White.copy(0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    var isSettingsFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showSettingsPanel = true },
                        modifier = Modifier.onFocusChanged { isSettingsFocused = it.isFocused },
                        colors = IconButtonDefaults.colors(
                            containerColor = if (isSettingsFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = stringResource(R.string.okuyucu_ayarlari),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // ─── TV HUD: Alt İlerleme ve Bölüm Çubuğu ────────────────────────────────
        AnimatedVisibility(
            visible = hudVisible && pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.9f))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column {
                    // Sayfa Göstergesi ve Önceki/Sonraki Butonları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        var isPrevFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { viewModel.goToPreviousChapter() },
                            enabled = viewModel.hasPreviousChapter,
                            modifier = Modifier.onFocusChanged { isPrevFocused = it.isFocused },
                            colors = IconButtonDefaults.colors(
                                containerColor = if (isPrevFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.NavigateBefore,
                                contentDescription = stringResource(R.string.onceki_bolum),
                                tint = if (viewModel.hasPreviousChapter) Color.White else Color.White.copy(0.3f)
                            )
                        }

                        // Sayfa İlerleme Metni
                        val currentText = "${currentPage + 1} / ${pages.size}"
                        val readingLabel = when (readingMode) {
                            ReadingMode.RightToLeft -> "Sağdan Sola"
                            ReadingMode.LeftToRight -> "Soldan Sağa"
                            ReadingMode.Vertical -> "Dikey"
                            ReadingMode.Webtoon -> "Webtoon"
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentText,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mod: $readingLabel",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        var isNextFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { viewModel.goToNextChapter() },
                            enabled = viewModel.hasNextChapter,
                            modifier = Modifier.onFocusChanged { isNextFocused = it.isFocused },
                            colors = IconButtonDefaults.colors(
                                containerColor = if (isNextFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = stringResource(R.string.sonraki_bolum),
                                tint = if (viewModel.hasNextChapter) Color.White else Color.White.copy(0.3f)
                            )
                        }
                    }
                }
            }
        }

        // ─── TV Ayarlar Kontrol Paneli Overlay ───────────────────────────────────
        if (showSettingsPanel) {
            TvMangaReaderControls(
                viewModel = viewModel,
                autoScrollEnabled = autoScrollEnabled,
                onAutoScrollChange = { autoScrollEnabled = it },
                onDismiss = {
                    showSettingsPanel = false
                    scope.launch {
                        try { backButtonFocusRequester.requestFocus() } catch (e: Exception) {}
                    }
                }
            )
        }
    }
}
