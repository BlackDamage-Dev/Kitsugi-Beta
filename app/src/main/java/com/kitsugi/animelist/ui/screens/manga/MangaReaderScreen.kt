package com.kitsugi.animelist.ui.screens.manga
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.Slider
import androidx.compose.material3.SliderDefaults
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.ui.screens.manga.components.MangaPagerReader
import com.kitsugi.animelist.ui.screens.manga.components.readerSpreadCount
import com.kitsugi.animelist.ui.screens.manga.components.readerSpreadForPage
import com.kitsugi.animelist.ui.screens.manga.components.readerSpreadLast
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
import com.kitsugi.animelist.ui.screens.manga.components.WebtoonAutoScroll
import com.kitsugi.animelist.ui.screens.manga.components.ReaderVolumeKeys
import com.kitsugi.animelist.ui.screens.manga.components.readerBackgroundColor
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Manga Okuyucu Ana Ekranı
 *
 * — Pager modu (LTR / RTL / Dikey) → MangaPagerReader
 * — Webtoon modu (sürekli dikey) → MangaWebtoonReader
 * — Üst ve alt barlar merkeze tıklanınca gizlenir/gösterilir (immersive)
 * — Alt çekme menüsünden okuma modu anında değiştirilebilir
 * — Sayfa ilerlemesi ve bölüm bilgisi alt çubukta gösterilir
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    chapter: MangaChapter,
    mangaDetails: com.kitsugi.animelist.data.manga.MangaDetails,
    source: MangaSource,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val readerActivity = remember(context) { context.findActivity() }
    var orientationSetByReader by remember(readerActivity) { mutableStateOf<Int?>(null) }
    DisposableEffect(readerActivity) {
        val originalOrientation = readerActivity?.requestedOrientation
        onDispose {
            if (readerActivity != null && !readerActivity.isFinishing && !readerActivity.isChangingConfigurations && originalOrientation != null &&
                orientationSetByReader != null && readerActivity.requestedOrientation == orientationSetByReader) {
                readerActivity.requestedOrientation = originalOrientation
            }
        }
    }
    val viewModel: MangaReaderViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}_${chapter.url}",
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

    val scope      = rememberCoroutineScope()
    var menuVisible     by remember { mutableStateOf(true) }
    var showSettings    by remember { mutableStateOf(false) }
    var editLongTapGrid by remember { mutableStateOf(false) }
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
    val listState     = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

    var hasRestoredProgress by remember(currentChapter.url) { mutableStateOf(false) }
    WebtoonAutoScroll(
        enabled = autoScrollEnabled && readingMode == ReadingMode.Webtoon && !showSettings &&
            !uiState.isLoadingPages && hasRestoredProgress && pages.isNotEmpty(),
        listState = listState,
        speedDpPerSecond = uiState.webtoonAutoScrollSpeed,
        onEnd = {
            autoScrollEnabled = false
            viewModel.onWebtoonChapterEnd(currentChapter.url, pages.size)
            if (uiState.webtoonAutoNext && viewModel.hasNextChapter) viewModel.goToNextChapter()
        }
    )

    ReaderVolumeKeys(
        enabled = uiState.volumeKeys && !showSettings && hasRestoredProgress && !uiState.isLoadingPages && pages.isNotEmpty(),
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

    // Pager'daki sayfa değişimini takip et — V2 prefetch tetikle
    LaunchedEffect(pagerState.currentPage, synchronizingMode, hasRestoredProgress, readingMode, useSpreads, coverAlone) {
        if (hasRestoredProgress && !synchronizingMode && previousReadingMode == readingMode &&
            previousSpread == (useSpreads to coverAlone) && !uiState.isLoadingPages && readingMode != ReadingMode.Webtoon && pages.isNotEmpty()) {
            val pageIndex = readerSpreadLast(pagerState.currentPage, pages.size, useSpreads, coverAlone).coerceIn(0, pages.lastIndex)
            viewModel.onPageChanged(currentChapter.url, pageIndex, pages.size)
            // R2: Sayfa değiştikçe V2 loader'da 3 ileri prefetch'i başlat
            loader.onPageChanged(pageIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ─── Okuyucu İçeriği ─────────────────────────────────────────────────
        if (uiState.isLoadingPages) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF7C4DFF),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.inline_loading_page_list_31ccd61), color = Color.White.copy(0.6f), fontSize = 13.sp)
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
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    uiState.pagesErrorCode?.let { code ->
                        Spacer(Modifier.height(10.dp))
                        com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code)
                    }
                    Spacer(Modifier.height(24.dp))
                    KitsugiButton(
                        onClick = { viewModel.retryLoadPages() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                    ) {
                        Text("Tekrar Dene", color = Color.White)
                    }
                }
            }
        } else if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.inline_no_pages_found_d05c840), color = Color.White.copy(0.5f))
            }
        } else {
            when (readingMode) {
                ReadingMode.Webtoon -> MangaWebtoonReader(
                    pages        = pages,
                    listState    = listState,
                    loader       = loader,
                    colorFilterType = uiState.colorFilterType,
                    saturation = uiState.saturation,
                    contrast = uiState.contrast,
                    hue = uiState.hue,
                    fitMode      = uiState.fitMode,
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
                    onCenterTap  = { if (autoScrollEnabled) autoScrollEnabled = false else menuVisible = !menuVisible },
                    onPageChanged = { idx ->
                        if (hasRestoredProgress && !synchronizingMode && !uiState.isLoadingPages) {
                            viewModel.onPageChanged(currentChapter.url, idx, pages.size)
                            // R2: Webtoon scroll'da V2 prefetch tetikle
                            loader.onPageChanged(idx)
                        }
                    },
                    onNextPage   = {
                        val current = listState.firstVisibleItemIndex
                        if (uiState.webtoonTapScroll) {
                            scope.launch { listState.animateScrollBy((listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) * readerScrollFraction(uiState.webtoonScrollPercent)) }
                        } else if (current < pages.size - 1) {
                            scope.launch {
                                listState.animateScrollToItem(current + 1)
                            }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage   = {
                        val current = listState.firstVisibleItemIndex
                        if (uiState.webtoonTapScroll) {
                            scope.launch { listState.animateScrollBy((listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) * -readerScrollFraction(uiState.webtoonScrollPercent)) }
                        } else if (current > 0) {
                            scope.launch {
                                listState.animateScrollToItem(current - 1)
                            }
                        } else {
                            viewModel.goToPreviousChapter()
                        }
                    }
                )
                else -> MangaPagerReader(
                    pages        = pages,
                    pagerState   = pagerState,
                    loader       = loader,
                    readingMode  = readingMode,
                    colorFilterType = uiState.colorFilterType,
                    saturation = uiState.saturation,
                    contrast = uiState.contrast,
                    hue = uiState.hue,
                    fitMode      = uiState.fitMode,
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
                    onCenterTap  = { menuVisible = !menuVisible },
                    onNextPage   = {
                        if (pagerState.currentPage < pagerState.pageCount - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage   = {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        } else {
                            viewModel.goToPreviousChapter()
                        }
                    }
                )
            }
        }

        // ─── Özel Parlaklık Katmanı ───────────────────────────────────────────
        if (uiState.customBrightness < 1.0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 1.0f - uiState.customBrightness))
            )
        }

        // ─── Üst Bar ─────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = menuVisible,
            enter   = fadeIn() + slideInVertically { -it },
            exit    = fadeOut() + slideOutVertically { -it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.85f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            mangaDetails.title,
                            color     = Color.White,
                            fontSize  = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines  = 1,
                            overflow  = TextOverflow.Ellipsis
                        )
                        Text(
                            currentChapter.name,
                            color    = Color.White.copy(0.65f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = stringResource(R.string.okuyucu_ayarlari),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // ─── Alt Bar (Aniyomi Parity) ──────────────────────────────────────────
        AnimatedVisibility(
            visible = menuVisible && pages.isNotEmpty(),
            enter   = fadeIn() + slideInVertically { it },
            exit    = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val barBackgroundColor = Color(0xFF161622).copy(alpha = 0.95f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBackgroundColor)
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. ChapterNavigator (Aniyomi Style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Chapter Button
                    IconButton(
                        onClick = { viewModel.goToPreviousChapter() },
                        enabled = viewModel.hasPreviousChapter
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.onceki_bolum),
                            tint = if (viewModel.hasPreviousChapter) Color.White else Color.White.copy(0.3f)
                        )
                    }

                    // Slider Container
                    if (pages.size > 1) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF26263B))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentPage + 1}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            var sliderValue by remember(currentPage) { mutableStateOf((currentPage + 1).toFloat()) }
                            Slider(
                                value = sliderValue,
                                onValueChange = {
                                    sliderValue = it
                                    val targetPage = it.toInt() - 1
                                    scope.launch {
                                        if (readingMode == ReadingMode.Webtoon) {
                                            listState.scrollToItem(targetPage.coerceIn(0, pages.size - 1))
                                        } else {
                                            pagerState.scrollToPage(readerSpreadForPage(targetPage.coerceIn(0, pages.size - 1), useSpreads, coverAlone))
                                        }
                                    }
                                },
                                valueRange = 1f..pages.size.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFF7C4DFF),
                                    inactiveTrackColor = Color(0xFF3D3D5C),
                                    thumbColor = Color(0xFF7C4DFF)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Text(
                                text = "${pages.size}",
                                color = Color.White.copy(0.6f),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Next Chapter Button
                    IconButton(
                        onClick = { viewModel.goToNextChapter() },
                        enabled = viewModel.hasNextChapter
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.sonraki_bolum),
                            tint = if (viewModel.hasNextChapter) Color.White else Color.White.copy(0.3f)
                        )
                    }
                }

                // 2. BottomReaderBar (Aniyomi Style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Reading Mode cycle
                    val readingModeIcon = when (readingMode) {
                        ReadingMode.RightToLeft -> Icons.AutoMirrored.Filled.MenuBook
                        ReadingMode.LeftToRight -> Icons.Default.SwapHoriz
                        ReadingMode.Vertical    -> Icons.Default.ViewStream
                        ReadingMode.Webtoon     -> Icons.Default.ViewStream
                    }
                    IconButton(onClick = {
                        val nextMode = when (readingMode) {
                            ReadingMode.RightToLeft -> ReadingMode.LeftToRight
                            ReadingMode.LeftToRight -> ReadingMode.Vertical
                            ReadingMode.Vertical    -> ReadingMode.Webtoon
                            ReadingMode.Webtoon     -> ReadingMode.RightToLeft
                        }
                        viewModel.setReadingMode(nextMode)
                    }) {
                        Icon(
                            imageVector = readingModeIcon,
                            contentDescription = stringResource(R.string.okuma_yonu),
                            tint = Color.White
                        )
                    }

                    // Quick Orientation cycle
                    IconButton(onClick = {
                        val activity = readerActivity
                        if (activity != null) {
                            val next = if (activity.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            orientationSetByReader = next
                            activity.requestedOrientation = next
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = stringResource(R.string.ekran_dondurme),
                            tint = Color.White
                        )
                    }

                    // Quick Fit Mode cycle
                    IconButton(onClick = {
                        val nextFit = when (uiState.fitMode) {
                            MangaFitMode.FitScreen -> MangaFitMode.FitWidth
                            MangaFitMode.FitWidth  -> MangaFitMode.FitHeight
                            MangaFitMode.FitHeight -> MangaFitMode.FitScreen
                        }
                        viewModel.setFitMode(nextFit)
                    }) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = stringResource(R.string.sigdirma_modu),
                            tint = Color.White
                        )
                    }

                    // Settings Dialog Trigger
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.ayarlar),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // ─── Okuma Modu Ayarlar Paneli ────────────────────────────────────────────
    if (showSettings) {
        val settingsScroll = rememberScrollState()
        KitsugiSheetOrDialog(
            onDismiss = { showSettings = false },
            innerColumnScrollState = settingsScroll
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(settingsScroll)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.inline_reader_settings_82b7841),
                    color     = Color.White,
                    fontSize  = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier  = Modifier.padding(bottom = 16.dp)
                )

                ReaderSwitch(stringResource(R.string.reader_series_mode), uiState.seriesModeOverride, viewModel::setSeriesModeOverride)
                // Okuma Yönü
                Text(
                    stringResource(R.string.manga_reading_direction),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        ReadingMode.RightToLeft to "RTL (Manga)",
                        ReadingMode.LeftToRight to "LTR",
                        ReadingMode.Vertical    to "Dikey",
                        ReadingMode.Webtoon     to "Webtoon"
                    )
                    modes.forEach { (mode, label) ->
                        FilterChip(
                            selected = readingMode == mode,
                            onClick  = { viewModel.setReadingMode(mode) },
                            label    = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor       = Color(0xFF7C4DFF),
                                containerColor           = Color(0xFF2A2A40),
                                labelColor               = Color.White
                            )
                        )
                    }
                }
                if (readingMode == ReadingMode.LeftToRight || readingMode == ReadingMode.RightToLeft) {
                    ReaderSwitch(stringResource(R.string.reader_double_page), uiState.doublePage, viewModel::setDoublePage)
                    if (uiState.doublePage) ReaderSwitch(stringResource(R.string.reader_cover_alone), uiState.coverAlone, viewModel::setCoverAlone)
                }
                Spacer(Modifier.height(12.dp))

                // Görsel Sığdırma Modu
                Text(
                    stringResource(R.string.inline_image_fit_mode_3eed46c),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fits = listOf(
                        MangaFitMode.FitScreen to stringResource(R.string.manga_fit_screen),
                        MangaFitMode.FitWidth  to "Genişlik",
                        MangaFitMode.FitHeight to "Yükseklik"
                    )
                    fits.forEach { (fit, label) ->
                        FilterChip(
                            selected = uiState.fitMode == fit,
                            onClick  = { viewModel.setFitMode(fit) },
                            label    = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor       = Color(0xFF7C4DFF),
                                containerColor           = Color(0xFF2A2A40),
                                labelColor               = Color.White
                            )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.reader_background), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Black" to R.string.reader_background_black, "DarkGray" to R.string.reader_background_gray, "White" to R.string.reader_background_white, "Auto" to R.string.reader_background_auto).forEach { (value, label) ->
                        FilterChip(selected = uiState.readerBackground == value, onClick = { viewModel.setReaderBackground(value) }, label = { Text(stringResource(label)) })
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Renk Filtresi
                Text(
                    "Renk Filtresi",
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        ColorFilterType.Normal    to "Normal",
                        ColorFilterType.Grayscale to "Siyah-Beyaz",
                        ColorFilterType.Sepia     to "Sepya",
                        ColorFilterType.Invert    to "Ters Negatif"
                    )
                    filters.forEach { (filter, label) ->
                        FilterChip(
                            selected = uiState.colorFilterType == filter,
                            onClick  = { viewModel.setColorFilter(filter) },
                            label    = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor       = Color(0xFF7C4DFF),
                                containerColor           = Color(0xFF2A2A40),
                                labelColor               = Color.White
                            )
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text(stringResource(R.string.reader_saturation), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 50, 100, 150, 200).forEach { value ->
                        FilterChip(selected = uiState.saturation == value, onClick = { viewModel.setSaturation(value) }, label = { Text("$value%") })
                    }
                }
                Text(stringResource(R.string.reader_contrast), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 75, 100, 125, 150, 200).forEach { value ->
                        FilterChip(selected = uiState.contrast == value, onClick = { viewModel.setContrast(value) }, label = { Text("$value%") })
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text(stringResource(R.string.reader_hue), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-180, -90, 0, 90, 180).forEach { value ->
                        FilterChip(selected = uiState.hue == value, onClick = { viewModel.setHue(value) }, label = { Text("$value°") })
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (readingMode == ReadingMode.Webtoon) {
                    Text(stringResource(R.string.reader_webtoon_padding), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 16, 32, 64).forEach { value ->
                            FilterChip(selected = uiState.webtoonPadding == value, onClick = { viewModel.setWebtoonPadding(value) }, label = { Text("$value dp") })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.reader_webtoon_gap), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 4, 16, 32).forEach { value ->
                            FilterChip(selected = uiState.webtoonGap == value, onClick = { viewModel.setWebtoonGap(value) }, label = { Text("$value dp") })
                        }
                    }
                    ReaderSwitch(stringResource(R.string.reader_webtoon_tap_scroll), uiState.webtoonTapScroll, viewModel::setWebtoonTapScroll)
                    Text(stringResource(R.string.reader_webtoon_scroll_amount), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(50, 80, 100).forEach { percent ->
                            FilterChip(selected = uiState.webtoonScrollPercent == percent, onClick = { viewModel.setWebtoonScrollPercent(percent) }, label = { Text("$percent%") })
                        }
                    }
                    ReaderSwitch(stringResource(R.string.reader_webtoon_auto_next), uiState.webtoonAutoNext, viewModel::setWebtoonAutoNext)
                    ReaderSwitch(stringResource(R.string.reader_webtoon_auto_scroll), autoScrollEnabled) { autoScrollEnabled = it }
                    Text(stringResource(R.string.reader_auto_scroll_hint), color = Color.White.copy(0.6f), fontSize = 11.sp)
                    Text(stringResource(R.string.reader_auto_scroll_speed), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(20, 40, 80, 120).forEach { speed ->
                            FilterChip(selected = uiState.webtoonAutoScrollSpeed == speed, onClick = { viewModel.setWebtoonAutoScrollSpeed(speed) }, label = { Text(stringResource(R.string.reader_scroll_speed_value, speed)) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(stringResource(R.string.reader_preload_ahead), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-1, 0, 1, 2, 3, 5).forEach { count ->
                        FilterChip(selected = uiState.preloadAhead == count, onClick = { viewModel.setPreloadAhead(count) }, label = {
                            Text(if (count < 0) stringResource(R.string.reader_preload_auto) else count.toString())
                        })
                    }
                }
                ReaderSwitch(stringResource(R.string.reader_skip_read_chapters), uiState.skipReadChapters, viewModel::setSkipReadChapters)
                ReaderSwitch(stringResource(R.string.reader_pause_progress), uiState.pauseProgressSaving, viewModel::setPauseProgressSaving)
                if (uiState.pauseProgressSaving) Text(stringResource(R.string.reader_pause_progress_hint), color = Color.White.copy(0.6f), fontSize = 11.sp)
                ReaderSwitch(stringResource(R.string.reader_show_page_number), uiState.showPageNumber, viewModel::setShowPageNumber)
                ReaderSwitch(stringResource(R.string.reader_keep_screen_on), uiState.keepScreenOn, viewModel::setKeepScreenOn)
                ReaderSwitch(stringResource(R.string.reader_immersive), uiState.immersive, viewModel::setImmersive)
                ReaderSwitch(stringResource(R.string.reader_crop_borders), uiState.cropBorders, viewModel::setCropBorders)
                ReaderSwitch(stringResource(R.string.reader_crop_borders_webtoon), uiState.cropBordersWebtoon, viewModel::setCropBordersWebtoon)
                ReaderSwitch(stringResource(R.string.reader_webtoon_zoom), uiState.webtoonZoom, viewModel::setWebtoonZoom)
                if (uiState.cropBorders || uiState.cropBordersWebtoon) Text(stringResource(R.string.reader_crop_borders_hint), color = Color.White.copy(0.6f), fontSize = 11.sp)
                for (webtoon in listOf(false, true)) {
                    Text(stringResource(if (webtoon) R.string.reader_wide_rotation_webtoon else R.string.reader_wide_rotation), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val selected = if (webtoon) uiState.wideRotationWebtoon else uiState.wideRotation
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Off" to R.string.reader_wide_off, "Clockwise" to R.string.reader_wide_clockwise, "CounterClockwise" to R.string.reader_wide_counterclockwise).forEach { (value, label) ->
                            FilterChip(selected = selected == value, onClick = { viewModel.setWideRotation(value, webtoon) }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                if (uiState.wideRotation != "Off" || uiState.wideRotationWebtoon != "Off") Text(stringResource(R.string.reader_wide_rotation_hint), color = Color.White.copy(0.6f), fontSize = 11.sp)
                Text(stringResource(R.string.reader_tap_layout), color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Default" to R.string.reader_tap_default, "LeftHand" to R.string.reader_tap_left_hand, "RightHand" to R.string.reader_tap_right_hand, "Custom" to R.string.reader_tap_custom).forEach { (layout, label) ->
                        FilterChip(selected = uiState.tapLayout == layout, onClick = { viewModel.setTapLayout(layout) }, label = { Text(stringResource(label)) })
                    }
                }
                if (uiState.tapLayout == "LeftHand" || uiState.tapLayout == "RightHand") Text(stringResource(R.string.reader_tap_one_hand_hint), color = Color.White.copy(0.6f), fontSize = 11.sp)
                if (uiState.tapLayout == "Custom") {
                    ReaderSwitch(stringResource(R.string.reader_tap_edit_long), editLongTapGrid) { editLongTapGrid = it }
                    Text(stringResource(R.string.reader_tap_grid_hint), color = Color.White.copy(0.6f), fontSize = 11.sp)
                    val grid = if (editLongTapGrid) uiState.longTapGrid else uiState.tapGrid
                    for (row in 0..2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (column in 0..2) {
                                val index = row * 3 + column
                                val action = when (grid[index]) { 'P' -> R.string.reader_tap_previous; 'N' -> R.string.reader_tap_next; 'M' -> R.string.reader_tap_menu; else -> R.string.reader_tap_none }
                                FilterChip(selected = false, modifier = Modifier.weight(1f), onClick = { viewModel.cycleTapGridCell(index, editLongTapGrid) },
                                    label = { Text(stringResource(action), fontSize = 11.sp) })
                            }
                        }
                    }
                    TextButton(onClick = viewModel::resetTapGrids) { Text(stringResource(R.string.reader_tap_reset), color = Color.White) }
                }
                ReaderSwitch(stringResource(R.string.reader_invert_taps), uiState.invertTapZones, viewModel::setInvertTapZones)
                ReaderSwitch(stringResource(R.string.reader_volume_keys), uiState.volumeKeys, viewModel::setVolumeKeys)
                if (uiState.volumeKeys) ReaderSwitch(stringResource(R.string.reader_invert_volume_keys), uiState.invertVolumeKeys, viewModel::setInvertVolumeKeys)
                Spacer(Modifier.height(16.dp))

                // Özel Parlaklık Sürgüsü
                Text(
                    stringResource(R.string.inline_reader_brightness_e5e9f7a),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessLow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Slider(
                        value = uiState.customBrightness,
                        onValueChange = { viewModel.setCustomBrightness(it) },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF7C4DFF),
                            inactiveTrackColor = Color(0xFF2A2A40),
                            thumbColor = Color(0xFF7C4DFF)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { viewModel.setCustomBrightness(1.0f) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(R.string.slider_reset_to_default),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.BrightnessHigh,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun ReadingMode.label() = when (this) {
    ReadingMode.RightToLeft -> "Sağdan Sola"
    ReadingMode.LeftToRight -> "Soldan Sağa"
    ReadingMode.Vertical    -> "Dikey"
    ReadingMode.Webtoon     -> "Webtoon"
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
private fun ReaderSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        com.kitsugi.animelist.ui.theme.gradient.Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = label })
    }
}
