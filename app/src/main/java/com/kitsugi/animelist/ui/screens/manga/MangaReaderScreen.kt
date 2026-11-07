package com.kitsugi.animelist.ui.screens.manga

import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.music.AmbientAudioEngine
import com.kitsugi.animelist.core.music.AmbientAutoplayStarter
import com.kitsugi.animelist.core.music.AmbientPlaybackService
import com.kitsugi.animelist.core.music.AutoplayGate
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.manga.*
import com.kitsugi.animelist.data.music.MusicCatalogRepository
import com.kitsugi.animelist.data.music.MusicRecommendationEngine
import com.kitsugi.animelist.data.music.MusicStateStore
import com.kitsugi.animelist.data.music.ProceduralSoundCatalog
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiScrollToTopFab
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog
import com.kitsugi.animelist.ui.components.MangaSourceAuthBanner
import com.kitsugi.animelist.ui.components.MangaSourceLoginDialog
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.screens.manga.components.MangaAmbientPanel
import com.kitsugi.animelist.ui.screens.manga.components.MangaChapterChoiceDialog
import com.kitsugi.animelist.ui.screens.manga.components.MangaPagerReader
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
import com.kitsugi.animelist.ui.screens.manga.reader.*
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.requestFocusAfterFrames
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One reader, one action dispatcher, one settings surface for mobile, keyboard and TV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    chapter: MangaChapter,
    mangaDetails: MangaDetails,
    source: MangaSource,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val readerActivity = remember(context) { context.readerActivity() }
    var orientationSetByReader by remember(readerActivity) { mutableStateOf<Int?>(null) }
    DisposableEffect(readerActivity) {
        val originalOrientation = readerActivity?.requestedOrientation
        onDispose {
            if (readerActivity != null &&
                !readerActivity.isFinishing &&
                !readerActivity.isChangingConfigurations &&
                originalOrientation != null &&
                orientationSetByReader != null &&
                readerActivity.requestedOrientation == orientationSetByReader
            ) {
                readerActivity.requestedOrientation = originalOrientation
            }
        }
    }

    val model: MangaReaderViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}_${chapter.url}",
        factory = MangaReaderViewModel.Factory(
            context = context,
            source = source,
            mangaDetails = mangaDetails,
            initialChapter = chapter,
        ),
    )
    val ui by model.uiState.collectAsState()
    val pages by model.pages.collectAsState()

    var menuVisible by rememberSaveable { mutableStateOf(true) }
    var overlay by remember { mutableStateOf(ReaderOverlay.None) }
    var fullscreenOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val fullscreen = fullscreenOverride ?: (ui.settingsLoaded && ui.autoFullscreen)

    var showGallery by remember { mutableStateOf(false) }
    var galleryInitialIndex by remember { mutableStateOf(ui.currentPageIndex) }
    var showMusicPanel by remember { mutableStateOf(false) }
    var showSourceLogin by remember { mutableStateOf(false) }
    var showMangaTrLogin by remember { mutableStateOf(false) }
    var verificationUrl by remember { mutableStateOf<String?>(null) }

    val overlayOpen = overlay != ReaderOverlay.None ||
        showGallery ||
        showMusicPanel ||
        showSourceLogin ||
        showMangaTrLogin ||
        verificationUrl != null ||
        ui.pendingChoice != null

    val session = rememberMangaReaderSession(model, ui, pages, overlayOpen)
    val focus = remember { FocusRequester() }
    var contentFocused by remember { mutableStateOf(false) }

    val musicEngine = remember { AmbientAudioEngine.get(context.applicationContext) }
    val readerSettingsStore = remember { SettingsDataStore(context.applicationContext) }
    val readerAppSettings by readerSettingsStore.settingsFlow.collectAsState(initial = AppSettings())
    val musicIsPlaying by musicEngine.isPlaying.collectAsState()

    fun leaveReader() {
        session.stop()
        model.flushProgress()
        onBack()
    }

    fun back() {
        when {
            overlay != ReaderOverlay.None -> overlay = ReaderOverlay.None
            showGallery -> showGallery = false
            showMusicPanel -> showMusicPanel = false
            showSourceLogin -> showSourceLogin = false
            showMangaTrLogin -> showMangaTrLogin = false
            verificationUrl != null -> verificationUrl = null
            ui.pendingChoice != null -> model.dismissChapterChoice()
            ui.readingMode == ReadingMode.Webtoon && session.webtoon.zoom > 1f -> session.resetZoom()
            menuVisible -> menuVisible = false
            else -> leaveReader()
        }
    }

    fun rotate() {
        context.readerActivity()?.let { activity ->
            val next = if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            orientationSetByReader = next
            activity.requestedOrientation = next
        }
    }

    fun toggleFullscreen() {
        val next = !fullscreen
        fullscreenOverride = next
        if (next) menuVisible = false
    }

    fun open(value: ReaderOverlay) {
        session.stop()
        overlay = value
    }

    fun dispatch(action: ReaderAction, repeated: Boolean = false) {
        if (action != ReaderAction.ToggleAutoScroll) session.stop()
        when (action) {
            ReaderAction.Forward -> session.advance(1, repeated)
            ReaderAction.Backward -> session.advance(-1, repeated)
            ReaderAction.FirstPage -> session.seek(0)
            ReaderAction.LastPage -> session.lastPage()
            ReaderAction.NextChapter -> model.goToNextChapter()
            ReaderAction.PreviousChapter -> model.goToPreviousChapter()
            ReaderAction.ToggleMenu -> menuVisible = !menuVisible
            ReaderAction.Settings -> open(ReaderOverlay.Settings)
            ReaderAction.Chapters -> open(ReaderOverlay.Chapters)
            ReaderAction.GoToPage -> if (pages.isNotEmpty()) open(ReaderOverlay.GoToPage)
            ReaderAction.Retry -> model.retryVisiblePages()
            ReaderAction.Fullscreen -> toggleFullscreen()
            ReaderAction.Rotate -> rotate()
            ReaderAction.CycleMode -> model.setReadingMode(MangaReaderPreferences.nextReadingMode(ui.readingMode))
            ReaderAction.CycleFit -> model.setFitMode(MangaFitMode.entries[(ui.fitMode.ordinal + 1) % MangaFitMode.entries.size])
            ReaderAction.CycleBackground -> {
                val choices = listOf("Black", "DarkGray", "White")
                model.setWebtoonPreferences(
                    ui.webtoon.copy(background = choices[(choices.indexOf(ui.webtoon.background) + 1) % choices.size])
                )
            }
            ReaderAction.ToggleAutoScroll -> if (ui.readingMode == ReadingMode.Webtoon && session.isReady) {
                session.autoScroll = !session.autoScroll
                if (session.autoScroll) menuVisible = false
            }
            ReaderAction.Help -> open(ReaderOverlay.Shortcuts)
            ReaderAction.ZoomIn -> session.webtoon.zoomBy(1.25f)
            ReaderAction.ZoomOut -> session.webtoon.zoomBy(.8f)
            ReaderAction.ResetZoom -> session.resetZoom()
            ReaderAction.Back -> back()
        }
    }

    BackHandler(onBack = ::back)
    LaunchedEffect(overlayOpen, menuVisible) {
        if (!overlayOpen) focus.requestFocusAfterFrames(frames = 2)
        if (menuVisible) session.stop()
    }
    MangaReaderWindowEffects(fullscreen, menuVisible, ui.webtoon.keepScreenOn)

    val useSpreads = ui.doublePage && (ui.readingMode == ReadingMode.LeftToRight || ui.readingMode == ReadingMode.RightToLeft)
    val coverAlone = useSpreads && ui.coverAlone
    val spreads = remember(pages.size, useSpreads, coverAlone) {
        buildReaderSpreads(pages.size, useSpreads, coverAlone)
    }

    LaunchedEffect(ui.readingMode, ui.webtoonZoom) {
        session.resetZoom()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(session.background)
            .testTag("manga_reader")
            .focusRequester(focus)
            .onFocusChanged { contentFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                handleMangaReaderKey(
                    event,
                    ReaderKeyContext(
                        ui.readingMode,
                        overlayOpen,
                        contentFocused,
                        menuVisible,
                        ui.webtoon.volumeKeys,
                    ),
                    ::dispatch,
                )
            }
            .focusable(),
    ) {
        when {
            ui.isLoadingPages || !ui.settingsLoaded -> ReaderMessage(
                message = stringResource(R.string.inline_loading_page_list_31ccd61),
                loading = true,
            )
            ui.pagesError != null -> ReaderErrorMessage(
                ui = ui,
                source = source,
                onRetry = model::retryLoadPages,
                onSourceLogin = { showSourceLogin = true },
                onMangaTrLogin = { showMangaTrLogin = true },
                onOpenVerificationUrl = { verificationUrl = it },
            )
            pages.isEmpty() -> ReaderMessage(stringResource(R.string.inline_no_pages_found_d05c840))
            ui.readingMode == ReadingMode.Webtoon -> MangaWebtoonReader(
                pages = pages,
                state = session.webtoon,
                loader = model.loader,
                preferences = ui.webtoon,
                colorFilterType = ui.colorFilterType,
                saturation = ui.saturation,
                contrast = ui.contrast,
                hue = ui.hue,
                zoomEnabled = ui.webtoonZoom,
                backgroundColor = session.background,
                onCenterTap = { dispatch(ReaderAction.ToggleMenu) },
                onNextPage = { session.advance(1) },
                onPrevPage = { session.advance(-1) },
                onLongPressPage = { pageIndex ->
                    session.stop()
                    galleryInitialIndex = pageIndex
                    showGallery = true
                },
                onInteraction = session::stop,
                hasNextChapter = model.hasNextChapter,
                nextChapterName = if (ui.skipReadChapters) null else model.nextChapterName,
                nextChapterVariantCount = model.nextChapterVariantCount,
                onGoToNextChapter = { dispatch(ReaderAction.NextChapter) },
            )
            else -> MangaPagerReader(
                pages = pages,
                pagerState = session.pager,
                loader = model.loader,
                readingMode = ui.readingMode,
                colorFilterType = ui.colorFilterType,
                saturation = ui.saturation,
                contrast = ui.contrast,
                hue = ui.hue,
                fitMode = ui.fitMode,
                pageBackground = session.background,
                resetZoomSignal = session.resetZoomSignal,
                autoBackground = ui.readerBackground == "Auto",
                showPageNumber = ui.webtoon.showPageNumber,
                invertTapZones = ui.invertTapZones,
                tapLayout = ui.tapLayout,
                tapGrid = ui.tapGrid,
                longTapGrid = ui.longTapGrid,
                doublePage = useSpreads,
                coverAlone = coverAlone,
                stitchDualPages = ui.stitchDualPages,
                prefetchPages = readerPreloadCount(ui.preloadAhead, webtoon = false),
                cropBorders = ui.cropBorders,
                wideRotation = ui.wideRotation,
                onCenterTap = { dispatch(ReaderAction.ToggleMenu) },
                onSourceLogin = { showSourceLogin = true },
                onLongPressPage = { pageIndex ->
                    session.stop()
                    galleryInitialIndex = pageIndex
                    showGallery = true
                },
                onNextPage = { session.advance(1) },
                onPrevPage = { session.advance(-1) },
            )
        }

        if (ui.customBrightness < 1f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - ui.customBrightness)))
        }

        // Tek ve çift sayfa moduna uygun sayaç (sağ alt köşe, tek sayaç)
        if (!menuVisible && ui.hiddenPageCounter && pages.isNotEmpty() && !ui.isLoadingPages) {
            val totalPages = pages.size
            val pageLabel = if (ui.readingMode == ReadingMode.Webtoon) {
                (session.webtoon.listState.firstVisibleItemIndex + 1).coerceIn(1, totalPages).toString()
            } else {
                val spread = spreads.getOrNull(session.pager.currentPage)
                when {
                    spread == null -> (session.pager.currentPage + 1).coerceIn(1, totalPages).toString()
                    spread.first != spread.last -> "${spread.first + 1}-${spread.last + 1}"
                    else -> (spread.first + 1).coerceIn(1, totalPages).toString()
                }
            }
            Text(
                text = "$pageLabel / $totalPages",
                color = Color.White.copy(0.85f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = 14.dp)
                    .background(Color.Black.copy(0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        if (ui.readingMode == ReadingMode.Webtoon && pages.isNotEmpty()) {
            val showScrollToTop by remember {
                derivedStateOf {
                    session.webtoon.listState.firstVisibleItemIndex > 1 ||
                        session.webtoon.listState.firstVisibleItemScrollOffset > 400
                }
            }
            KitsugiScrollToTopFab(
                visible = showScrollToTop,
                isLandscape = LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                bottomBarScrollState = null,
                fallbackBottomBarVisible = menuVisible,
                bottomBarHeight = 128.dp,
                onClick = { session.seek(0) },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(.9f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderToolbarButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.geri), "Esc", ::leaveReader)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .androidxClickable { open(ReaderOverlay.Chapters) },
                ) {
                    Text(
                        mangaDetails.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        ui.currentChapter.name,
                        color = Color.White.copy(.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (pages.isNotEmpty()) {
                    IconButton(onClick = {
                        session.stop()
                        galleryInitialIndex = ui.currentPageIndex
                        showGallery = true
                    }) {
                        Icon(
                            Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.manga_reader_open_gallery),
                            tint = Color.White,
                        )
                    }
                }
                if (readerAppSettings.musicEnabled) {
                    IconButton(onClick = {
                        session.stop()
                        showMusicPanel = true
                    }) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = stringResource(R.string.music_panel_title),
                            tint = if (musicIsPlaying) Color(0xFF9D7CFF) else Color.White,
                        )
                    }
                }
                // Settings used to have a second identical trigger here. It now exists only below.
            }
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xF5161622))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                if (pages.isNotEmpty() && !ui.isLoadingPages) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ReaderToolbarButton(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.onceki_bolum),
                            "P",
                            { dispatch(ReaderAction.PreviousChapter) },
                            model.hasPreviousChapter,
                        )
                        var slider by remember { mutableFloatStateOf(ui.currentPageIndex.toFloat()) }
                        var dragging by remember { mutableStateOf(false) }
                        LaunchedEffect(ui.currentPageIndex) {
                            if (!dragging) slider = ui.currentPageIndex.toFloat()
                        }
                        TextButton(onClick = { open(ReaderOverlay.GoToPage) }) {
                            Text("${ui.currentPageIndex + 1}", color = Color.White)
                        }
                        Slider(
                            value = slider.coerceIn(0f, (pages.size - 1).coerceAtLeast(0).toFloat()),
                            onValueChange = { dragging = true; slider = it },
                            onValueChangeFinished = { session.seek(slider.roundToInt()); dragging = false },
                            valueRange = 0f..(pages.size - 1).coerceAtLeast(1).toFloat(),
                            enabled = pages.size > 1 && session.isReady,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${pages.size}",
                            color = Color.White.copy(.65f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        ReaderToolbarButton(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            stringResource(R.string.sonraki_bolum),
                            "N",
                            { dispatch(ReaderAction.NextChapter) },
                            model.hasNextChapter,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val readingModeIcon = when (ui.readingMode) {
                        ReadingMode.RightToLeft -> Icons.AutoMirrored.Filled.MenuBook
                        ReadingMode.LeftToRight -> Icons.Default.SwapHoriz
                        ReadingMode.Vertical -> Icons.Default.ViewStream
                        ReadingMode.Webtoon -> Icons.Default.ViewStream
                    }
                    ReaderToolbarButton(
                        readingModeIcon,
                        stringResource(R.string.okuma_yonu),
                        "D",
                        {
                            val nextMode = when (ui.readingMode) {
                                ReadingMode.RightToLeft -> ReadingMode.LeftToRight
                                ReadingMode.LeftToRight -> ReadingMode.Vertical
                                ReadingMode.Vertical -> ReadingMode.Webtoon
                                ReadingMode.Webtoon -> ReadingMode.RightToLeft
                            }
                            model.setReadingMode(nextMode)
                        },
                    )
                    ReaderToolbarButton(
                        if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        stringResource(R.string.reader_fullscreen),
                        "F",
                        ::toggleFullscreen,
                    )
                    ReaderToolbarButton(
                        if (session.autoScroll) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(R.string.reader_auto_scroll),
                        "A",
                        { dispatch(ReaderAction.ToggleAutoScroll) },
                        ui.readingMode == ReadingMode.Webtoon,
                    )
                    ReaderToolbarButton(
                        Icons.Default.SwapHoriz,
                        "Yön değiştir",
                        "R",
                        {
                            val flipped = when (ui.readingMode) {
                                ReadingMode.RightToLeft -> ReadingMode.LeftToRight
                                ReadingMode.LeftToRight -> ReadingMode.RightToLeft
                                ReadingMode.Vertical -> ReadingMode.Webtoon
                                ReadingMode.Webtoon -> ReadingMode.Vertical
                            }
                            model.setReadingMode(flipped)
                        },
                    )
                    ReaderToolbarButton(
                        Icons.Default.Settings,
                        stringResource(R.string.okuyucu_ayarlari),
                        "S",
                        { open(ReaderOverlay.Settings) },
                    )
                }
            }
        }
    }

    MangaReaderOverlayHost(
        overlay = overlay,
        model = model,
        ui = ui,
        pageCount = pages.size,
        onDismiss = { overlay = ReaderOverlay.None },
        onSeek = session::seek,
        onRotate = ::rotate,
        onOpenOverlay = { overlay = it },
    )

    ui.pendingChoice?.let { request ->
        MangaChapterChoiceDialog(
            request = request,
            onConfirm = { pickedChapter, rememberScanlator, autoContinue ->
                model.confirmChapterChoice(
                    chapter = pickedChapter,
                    rememberScanlator = rememberScanlator,
                    autoContinue = autoContinue,
                )
            },
            onDismiss = { model.dismissChapterChoice() },
        )
    }

    if (showGallery && pages.isNotEmpty()) {
        MangaPageGallerySheet(
            pages = pages,
            currentIndex = ui.currentPageIndex,
            initialFocusIndex = galleryInitialIndex,
            accentColor = Color(0xFF7C4DFF),
            loader = model.loader,
            onSelect = { index ->
                showGallery = false
                session.seek(index)
            },
            onDismiss = { showGallery = false },
        )
    }

    MangaReaderAmbientOrchestrator(
        mangaDetails = mangaDetails,
        musicEngine = musicEngine,
        readerSettingsStore = readerSettingsStore,
        readerAppSettings = readerAppSettings,
        showMusicPanel = showMusicPanel,
        onDismissMusicPanel = { showMusicPanel = false },
    )

    if (showMangaTrLogin) {
        MangaTrLoginDialog(onDismiss = {
            showMangaTrLogin = false
            model.retryLoadPages()
        })
    }

    if (verificationUrl != null) {
        KitsugiWebViewDialog(
            title = stringResource(R.string.manga_source_verify_in_browser),
            url = verificationUrl!!,
            onDismiss = {
                verificationUrl = null
                model.retryLoadPages()
            },
        )
    }

    if (showSourceLogin) {
        MangaSourceLoginDialog(
            source = source,
            onSessionUpdated = { model.retryLoadPages() },
            onDismiss = { showSourceLogin = false },
        )
    }
}

@Composable
private fun ReaderToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    key: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF66509D) else Color.Transparent),
    ) {
        Icon(icon, "$label ($key)", tint = if (enabled) Color.White else Color.White.copy(.3f))
    }
}

@Composable
private fun ReaderMessage(
    message: String,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
    errorCode: String? = null,
) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) CircularProgressIndicator(color = Color(0xFF9D7BFF))
            Text(message, color = Color.White.copy(.8f), textAlign = TextAlign.Center)
            errorCode?.let { com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = it) }
            if (onRetry != null) KitsugiButton(onClick = onRetry) { Text(stringResource(R.string.reader_retry)) }
        }
    }
}

@Composable
private fun ReaderErrorMessage(
    ui: MangaReaderViewModel.UiState,
    source: MangaSource,
    onRetry: () -> Unit,
    onSourceLogin: () -> Unit,
    onMangaTrLogin: () -> Unit,
    onOpenVerificationUrl: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = Color.White.copy(0.5f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = ui.pagesError ?: stringResource(R.string.ui_error_generic),
                color = Color.White.copy(0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            ui.pagesErrorCode?.let { code ->
                Spacer(Modifier.height(10.dp))
                com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code)
            }
            if (ui.loginRequired) {
                Spacer(Modifier.height(16.dp))
                KitsugiButton(onClick = onMangaTrLogin) {
                    Text(stringResource(R.string.giris_yap), color = Color.White)
                }
            }
            if (ui.verificationUrl != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.manga_source_verify_hint),
                    color = Color.White.copy(0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                KitsugiButton(
                    onClick = { onOpenVerificationUrl(ui.verificationUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                ) {
                    Text(stringResource(R.string.manga_source_verify_in_browser), color = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
            if (PlatformErrorCode.fromId(ui.pagesErrorCode) == PlatformErrorCode.MangaAuth) {
                MangaSourceAuthBanner(
                    source = source,
                    onLogin = onSourceLogin,
                    onRetry = onRetry,
                )
            } else {
                KitsugiButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                ) {
                    Text(stringResource(R.string.reader_retry), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MangaReaderAmbientOrchestrator(
    mangaDetails: MangaDetails,
    musicEngine: AmbientAudioEngine,
    readerSettingsStore: SettingsDataStore,
    readerAppSettings: AppSettings,
    showMusicPanel: Boolean,
    onDismissMusicPanel: () -> Unit,
) {
    val context = LocalContext.current
    val readerScope = rememberCoroutineScope()
    val musicIsPlaying by musicEngine.isPlaying.collectAsState()
    val musicActiveTitle by musicEngine.activeTitle.collectAsState()
    val musicActivePreset by musicEngine.activePresetId.collectAsState()
    val musicMasterVol by musicEngine.masterVolume.collectAsState()
    val musicChannels by musicEngine.channels.collectAsState()

    val musicCatalog = remember { MusicCatalogRepository.get() }
    val onlineResults by musicCatalog.results.collectAsState()
    val onlineSearching by musicCatalog.isSearching.collectAsState()
    val onlineError by musicCatalog.searchError.collectAsState()
    val onlineQuery by musicCatalog.query.collectAsState()
    val onlineSources by musicCatalog.sources.collectAsState()

    val sleepTimer by musicEngine.sleepTimer.collectAsState()
    val mixProfiles by musicEngine.profiles.collectAsState()
    val musicFavorites by musicEngine.favorites.collectAsState()
    val musicRecentIds by musicEngine.recentIds.collectAsState()

    val ambientDownloads by musicEngine.downloadManager.downloaded.collectAsState()
    val ambientActive by musicEngine.downloadManager.active.collectAsState()

    val downloadedIds = remember(ambientDownloads) { ambientDownloads.keys }
    val downloadProgress = remember(ambientActive) {
        ambientActive
            .filterValues { it.downloading }
            .mapValues { it.value.progress }
    }

    val musicStateStore = remember(context.applicationContext) {
        MusicStateStore(context.applicationContext)
    }
    var musicHydrated by remember { mutableStateOf(false) }

    LaunchedEffect(musicStateStore) {
        val storedFavorites = musicStateStore.favorites.first()
        if (storedFavorites.isNotEmpty()) musicEngine.setFavorites(storedFavorites)
        val storedProfiles = musicStateStore.profiles.first()
        if (storedProfiles.isNotEmpty()) musicEngine.setProfiles(storedProfiles)
        val storedRecent = musicStateStore.recent.first()
        if (storedRecent.isNotEmpty()) musicEngine.setRecentIds(storedRecent)
        musicHydrated = true
    }

    LaunchedEffect(musicHydrated) {
        if (!musicHydrated) return@LaunchedEffect
        musicEngine.favorites.collect { musicStateStore.saveFavorites(it) }
    }
    LaunchedEffect(musicHydrated) {
        if (!musicHydrated) return@LaunchedEffect
        musicEngine.profiles.collect { musicStateStore.saveProfiles(it) }
    }
    LaunchedEffect(musicHydrated) {
        if (!musicHydrated) return@LaunchedEffect
        musicEngine.recentIds.collect { musicStateStore.saveRecent(it) }
    }

    LaunchedEffect(readerAppSettings) {
        musicCatalog.syncSettings(readerAppSettings)
    }

    var musicRecommendations by remember(
        mangaDetails.genre,
        mangaDetails.description,
        readerAppSettings.musicAutoRecommend,
    ) {
        mutableStateOf(
            if (!readerAppSettings.musicAutoRecommend) {
                MusicRecommendationEngine.recommend(
                    genres = emptyList(),
                    pool = ProceduralSoundCatalog.sounds,
                )
            } else {
                MusicRecommendationEngine.recommend(
                    genres = mangaDetails.genre,
                    pool = ProceduralSoundCatalog.sounds,
                    title = mangaDetails.title,
                    description = mangaDetails.description,
                )
            }
        )
    }
    var musicMatchNote by remember(mangaDetails.title) { mutableStateOf<String?>(null) }
    var musicEnriching by remember(mangaDetails.title) { mutableStateOf(false) }

    LaunchedEffect(mangaDetails.title, mangaDetails.genre, readerAppSettings.musicAutoRecommend) {
        if (!readerAppSettings.musicAutoRecommend) return@LaunchedEffect
        val local = musicRecommendations
        if (local.confidence != com.kitsugi.animelist.data.music.ContentMoodResolver.Confidence.NONE) {
            return@LaunchedEffect
        }
        musicEnriching = true
        val enriched = com.kitsugi.animelist.data.music.MangaMoodEnricher.enrich(
            title = mangaDetails.title,
            alternativeTitles = mangaDetails.alternativeTitles,
        )
        musicEnriching = false
        if (enriched.isEmpty) return@LaunchedEffect

        val merged = (mangaDetails.genre + enriched.genres).distinct()
        val rec = MusicRecommendationEngine.recommend(
            genres = merged,
            pool = ProceduralSoundCatalog.sounds,
            title = mangaDetails.title,
            description = mangaDetails.description,
        )
        musicRecommendations = rec
        musicMatchNote = "7 kaynak tarandı · ${enriched.genres.size} tür/etiket"
    }

    var autoPlayHandled by remember(mangaDetails.url) { mutableStateOf(false) }

    LaunchedEffect(readerAppSettings.musicAutoPlay) {
        if (!readerAppSettings.musicAutoPlay) autoPlayHandled = false
    }

    LaunchedEffect(
        mangaDetails.url,
        readerAppSettings.musicEnabled,
        readerAppSettings.musicAutoPlay,
        readerAppSettings.musicAutoPlayIncludeOnline,
        readerAppSettings.musicAutoPlayHeadphonesOnly,
        readerAppSettings.musicAutoPlayMuteOnSilentDevice,
        musicRecommendations,
    ) {
        if (autoPlayHandled) return@LaunchedEffect
        if (!readerAppSettings.musicEnabled) return@LaunchedEffect
        if (!readerAppSettings.musicAutoPlay) return@LaunchedEffect
        if (musicEngine.channels.value.isNotEmpty()) return@LaunchedEffect

        if (!AutoplayGate.canAutoplay(context, readerAppSettings)) {
            Log.i("MangaReader", "Otomatik başlatma atlandı — kulaklık yok veya cihaz sessiz modda")
            autoPlayHandled = true
            return@LaunchedEffect
        }

        val delaySec = readerAppSettings.musicAutoPlayDelaySeconds.coerceIn(0, 30)
        if (delaySec > 0) kotlinx.coroutines.delay(delaySec * 1000L)

        if (musicEngine.channels.value.isNotEmpty()) {
            autoPlayHandled = true
            return@LaunchedEffect
        }

        val recommendation = musicRecommendations
        val outcome = AmbientAutoplayStarter.start(
            engine = musicEngine,
            catalog = musicCatalog,
            settings = readerAppSettings,
            mood = recommendation.mood,
            detectedGenres = recommendation.detectedGenres,
            fallbackGenres = mangaDetails.genre,
            presetId = recommendation.presets.firstOrNull()?.sound?.id,
            firstSound = recommendation.sounds.firstOrNull()?.sound,
            targetVolume = readerAppSettings.musicAutoPlayVolume,
        )
        outcome.note?.let { musicMatchNote = it }
        autoPlayHandled = true
    }

    LaunchedEffect(musicEngine) {
        musicEngine.lastRejection.collect { message ->
            if (message.isNullOrBlank()) return@collect
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            musicEngine.clearRejection()
        }
    }

    LaunchedEffect(readerAppSettings.musicMasterVolume) {
        musicEngine.setMasterVolume(readerAppSettings.musicMasterVolume)
    }

    LaunchedEffect(readerAppSettings.musicContinueOnExit) {
        musicEngine.continueInBackground = readerAppSettings.musicContinueOnExit
    }

    // ── Okuyucudan çıkınca ortam sesi KESİN durur ────────────────────────
    // ⚠️ KÖK NEDEN (kullanıcı şikâyeti: "okuma sayfasından çıkınca, geri
    // gidince veya kapatınca müzik durmalı"): motor + ön plan servisi
    // okuyucu kapansa da çalmaya devam ediyordu; `musicContinueOnExit`
    // açıkken bu tasarlanmış davranıştı ama kullanıcı okumayı bırakınca
    // sesin sürmesini istemiyor. Kompozisyon kapanırken (geri tuşu /
    // kapatma / başka ekrana geçiş) kanallar ve servis her durumda
    // durdurulur. Bölüm geçişleri aynı kompozisyonda kaldığı için
    // etkilenmez.
    DisposableEffect(Unit) {
        onDispose {
            musicEngine.stopAllChannels()
            AmbientPlaybackService.stop(context)
        }
    }

    if (showMusicPanel) {
        KitsugiSheetOrDialog(
            onDismiss = onDismissMusicPanel,
            heightFraction = 0.78f,
            sheetGesturesEnabled = false,
        ) {
            MangaAmbientPanel(
                recommendations = musicRecommendations,
                matchNote = when {
                    musicEnriching -> "Kaynaklar taranıyor…"
                    else -> musicMatchNote
                },
                isPlaying = musicIsPlaying,
                activeTitle = musicActiveTitle,
                activePresetId = musicActivePreset,
                masterVolume = musicMasterVol,
                channels = musicChannels,
                maxChannels = com.kitsugi.animelist.data.music.MAX_AMBIENT_CHANNELS,
                onMasterVolumeChanged = { musicEngine.setMasterVolume(it) },
                onPlayPreset = { preset -> musicEngine.playPreset(preset) },
                onPlaySound = { sound ->
                    val level = if (sound.origin == com.kitsugi.animelist.data.music.AmbientOrigin.PROCEDURAL) {
                        readerAppSettings.musicAmbientVolume
                    } else {
                        readerAppSettings.musicChannelVolume
                    }
                    musicEngine.addChannel(sound, level.coerceIn(0.05f, 1f))
                },
                onChannelVolumeChanged = { index, v -> musicEngine.setChannelVolume(index, v) },
                onChannelMuteToggle = { index -> musicEngine.toggleMute(index) },
                onChannelRemove = { index -> musicEngine.stopChannel(index) },
                onRetryChannel = { index -> musicEngine.retryChannel(index) },
                onTogglePlay = { musicEngine.togglePlayPause() },
                onStopAll = { musicEngine.stopAllChannels() },
                onDismiss = onDismissMusicPanel,
                onlineResults = onlineResults,
                isSearching = onlineSearching,
                searchError = onlineError,
                searchQuery = onlineQuery,
                sourceStates = onlineSources,
                onSearchQueryChanged = { musicCatalog.setQuery(it) },
                onSearch = {
                    if (!onlineSearching) {
                        readerScope.launch {
                            musicCatalog.search(onlineQuery, readerAppSettings)
                        }
                    }
                },
                onPlayOnline = { sound ->
                    readerScope.launch {
                        val resolved = musicCatalog.resolveUrl(sound)
                        val playable = if (resolved != null && resolved != sound.streamUrl) {
                            sound.copy(streamUrl = resolved)
                        } else sound
                        musicEngine.addChannel(playable)
                    }
                },
                sleepTimer = sleepTimer,
                onStartTimer = { minutes -> musicEngine.startSleepTimer(minutes) },
                onCancelTimer = { musicEngine.cancelSleepTimer() },
                onPauseTimer = { musicEngine.pauseSleepTimer() },
                onResumeTimer = { musicEngine.resumeSleepTimer() },
                onExtendTimer = { extra -> musicEngine.extendSleepTimer(extra) },
                profiles = mixProfiles,
                favorites = musicFavorites,
                recentIds = musicRecentIds,
                onSaveProfile = { name -> musicEngine.saveCurrentAsProfile(name) },
                onApplyProfile = { profile -> musicEngine.applyProfile(profile) },
                onDeleteProfile = { id -> musicEngine.deleteProfile(id) },
                onToggleFavorite = { id -> musicEngine.toggleFavorite(id) },
                musicEnabled = readerAppSettings.musicEnabled,
                onMusicEnabledChanged = { enabled ->
                    readerScope.launch { readerSettingsStore.setMusicEnabled(enabled) }
                    if (!enabled) musicEngine.stopAllChannels()
                },
                autoPlay = readerAppSettings.musicAutoPlay,
                onAutoPlayChanged = { enabled ->
                    readerScope.launch { readerSettingsStore.setMusicAutoPlay(enabled) }
                },
                autoPlayDelaySeconds = readerAppSettings.musicAutoPlayDelaySeconds,
                autoPlayHeadphonesOnly = readerAppSettings.musicAutoPlayHeadphonesOnly,
                onAutoPlayHeadphonesOnlyChanged = { enabled ->
                    readerScope.launch { readerSettingsStore.setMusicAutoPlayHeadphonesOnly(enabled) }
                },
                autoPlayMuteOnSilent = readerAppSettings.musicAutoPlayMuteOnSilentDevice,
                onAutoPlayMuteOnSilentChanged = { enabled ->
                    readerScope.launch { readerSettingsStore.setMusicAutoPlayMuteOnSilent(enabled) }
                },
                autoPlayIncludeOnline = readerAppSettings.musicAutoPlayIncludeOnline,
                onAutoPlayIncludeOnlineChanged = { enabled ->
                    readerScope.launch { readerSettingsStore.setMusicAutoPlayIncludeOnline(enabled) }
                },
                onToggleSource = { sourceId ->
                    musicCatalog.toggleSource(sourceId)
                    val nowEnabled = musicCatalog.enabledOverride(sourceId) ?: true
                    readerScope.launch {
                        when (sourceId) {
                            com.kitsugi.animelist.data.music.MusicSourceId.JAMENDO ->
                                readerSettingsStore.setMusicJamendoEnabled(nowEnabled)
                            com.kitsugi.animelist.data.music.MusicSourceId.FREESOUND ->
                                readerSettingsStore.setMusicFreesoundEnabled(nowEnabled)
                            com.kitsugi.animelist.data.music.MusicSourceId.OPENVERSE ->
                                readerSettingsStore.setMusicOpenverseEnabled(nowEnabled)
                            com.kitsugi.animelist.data.music.MusicSourceId.ARCHIVE ->
                                readerSettingsStore.setMusicArchiveEnabled(nowEnabled)
                            com.kitsugi.animelist.data.music.MusicSourceId.AUDIUS ->
                                readerSettingsStore.setMusicAudiusEnabled(nowEnabled)
                            else -> Unit
                        }
                    }
                },
                downloadedIds = downloadedIds,
                downloadProgress = downloadProgress,
                onDownloadSound = { sound ->
                    readerScope.launch {
                        val file = musicEngine.downloadManager.download(
                            sound = sound,
                            repository = musicCatalog,
                        )
                        android.widget.Toast.makeText(
                            context,
                            if (file != null) "${sound.title} indirildi — artık çevrimdışı çalınabilir"
                            else "İndirilemedi",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                downloadedBytes = remember(ambientDownloads) {
                    ambientDownloads.values.sumOf { it.length() }
                },
                onClearDownloads = {
                    val count = musicEngine.downloadManager.clearAll()
                    android.widget.Toast.makeText(
                        context,
                        "$count ses indirmesi silindi",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
                onExportProfile = { profile ->
                    val json = musicEngine.exportProfile(profile)
                    val clipboard = context.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE
                    ) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText("Kitsugi karışım", json)
                    )
                    readerScope.launch {
                        android.widget.Toast.makeText(
                            context,
                            "Karışım panoya kopyalandı",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onImportProfile = {
                    val clipboard = context.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE
                    ) as? android.content.ClipboardManager
                    val text = clipboard?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    val imported = musicEngine.importProfile(text)
                    if (imported != null) {
                        musicEngine.setProfiles(musicEngine.profiles.value + imported)
                        readerScope.launch {
                            android.widget.Toast.makeText(
                                context,
                                "Karışım içe aktarıldı: ${imported.name}",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        readerScope.launch {
                            android.widget.Toast.makeText(
                                context,
                                "Panoda geçerli bir karışım yok",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun MangaPageGallerySheet(
    pages: List<MangaPage>,
    currentIndex: Int,
    initialFocusIndex: Int,
    accentColor: Color,
    loader: com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.manga_reader_gallery_title),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.manga_reader_gallery_hint),
                            color = Color.White.copy(0.6f),
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = "${currentIndex + 1}/${pages.size}",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.kapat), tint = Color.White)
                    }
                }

                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
                    initialFirstVisibleItemIndex = (initialFocusIndex - 2).coerceAtLeast(0),
                )
                LaunchedEffect(initialFocusIndex) {
                    if (initialFocusIndex > 0) {
                        runCatching { gridState.scrollToItem(initialFocusIndex) }
                    }
                }
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    state = gridState,
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 96.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 4.dp,
                        bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = pages.size,
                        key = { index -> pages[index].index },
                    ) { index ->
                        val page = pages[index]
                        LaunchedEffect(page.index) { loader.loadPage(page) }
                        val selected = index == currentIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.22f) else Color(0xFF1B1B26))
                                .androidxClickable {
                                    if (page.status == MangaPageStatus.Error) {
                                        loader.retryPage(page)
                                    } else {
                                        onSelect(index)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (page.status == MangaPageStatus.Ready) {
                                val src = page.imageUrl ?: page.url
                                val cachedModel = remember(page.index, page.status, src) {
                                    runCatching {
                                        val file = page.cachedImageFile ?: loader.cache.getImageFile(src)
                                        if (file.exists()) file else src
                                    }.getOrDefault(src)
                                }
                                AsyncImage(
                                    model = cachedModel,
                                    contentDescription = stringResource(
                                        R.string.manga_reader_page_number,
                                        index + 1,
                                    ),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else if (page.status == MangaPageStatus.Error) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.manga_retry),
                                        tint = Color.White.copy(0.6f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.manga_retry),
                                        color = Color.White.copy(0.6f),
                                        fontSize = 9.sp,
                                    )
                                }
                            } else {
                                CircularProgressIndicator(
                                    color = Color.White.copy(0.25f),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(accentColor.copy(alpha = 0.15f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )

internal fun buildReaderSpreads(pageCount: Int, dualPage: Boolean, firstPageSolo: Boolean): List<IntRange> {
    if (pageCount <= 0) return emptyList()
    if (!dualPage) return (0 until pageCount).map { it..it }
    val spreads = mutableListOf<IntRange>()
    var i = 0
    if (firstPageSolo && pageCount > 0) {
        spreads.add(0..0)
        i = 1
    }
    while (i < pageCount) {
        if (i + 1 < pageCount) {
            spreads.add(i..(i + 1))
            i += 2
        } else {
            spreads.add(i..i)
            i += 1
        }
    }
    return spreads
}
