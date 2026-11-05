package com.kitsugi.animelist.ui.screens.manga

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaReaderPrefs
import com.kitsugi.animelist.data.manga.MangaReaderPreferences
import com.kitsugi.animelist.data.manga.MangaSettingIds
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog
import com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.screens.manga.components.MangaChapterChoiceDialog
import com.kitsugi.animelist.ui.screens.manga.components.MangaChapterPickerSheet
import com.kitsugi.animelist.ui.screens.manga.components.MangaPagerReader
import com.kitsugi.animelist.ui.screens.manga.components.MangaReaderBottomBar
import com.kitsugi.animelist.ui.screens.manga.components.MangaReaderSettingsSheet
import com.kitsugi.animelist.ui.screens.manga.components.MangaReaderSystemUi
import com.kitsugi.animelist.ui.screens.manga.components.MangaReaderTopBar
import com.kitsugi.animelist.ui.screens.manga.components.MangaVerticalPagerReader
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
import com.kitsugi.animelist.ui.screens.manga.components.findActivityOrNull
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.launch

/**
 * Manga okuyucu ana ekranı.
 *
 * Okuma modları
 * -------------
 * — [ReadingMode.RightToLeft] / [ReadingMode.LeftToRight] → yatay sayfalama
 * — [ReadingMode.Vertical]  → dikey sayfalama (her ekranda bir sayfa)
 * — [ReadingMode.Webtoon]   → kesintisiz dikey kaydırma
 *
 * Tam ekran
 * ---------
 * Üstteki/sağdaki "tam ekran" ikonu artık gerçekten `systemBars` gizler
 * ([MangaReaderSystemUi]). Ekranın ortasına dokunmak uygulamanın kendi
 * üst/alt barlarını açar-kapar; uzun basmak ayarları açar.
 *
 * Kısayollar
 * ----------
 * — Ses tuşları: sayfa ileri/geri (ayarlardan açılabilir)
 * — Sol/sağ dokunma: sayfa çevir; orta: menü
 * — Çift dokunma: zoom; uzun basma: ayarlar
 */
@Composable
fun MangaReaderScreen(
    chapter: MangaChapter,
    mangaDetails: MangaDetails,
    source: MangaSource,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: MangaReaderViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}_${chapter.url}",
        factory = MangaReaderViewModel.Factory(
            context = context,
            source = source,
            mangaDetails = mangaDetails,
            initialChapter = chapter,
        )
    )

    var showMangaTrLogin by remember { mutableStateOf(false) }
    if (showMangaTrLogin) {
        MangaTrLoginDialog(onDismiss = {
            showMangaTrLogin = false
            viewModel.retryLoadPages()
        })
    }
    val uiState by viewModel.uiState.collectAsState()
    var promptedLoginForChapter by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.loginRequired, uiState.currentChapter.url) {
        if (uiState.loginRequired && promptedLoginForChapter != uiState.currentChapter.url) {
            promptedLoginForChapter = uiState.currentChapter.url
            showMangaTrLogin = true
        }
    }
    val pages by viewModel.pages.collectAsState()

    val readerPreferences = remember(context) { MangaReaderPreferences.get(context) }
    val readerPrefs by readerPreferences.state.collectAsState()

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    val currentChapter = uiState.currentChapter
    val readingMode = uiState.readingMode
    val currentPage = uiState.currentPageIndex
    val loader = viewModel.loader

    var menuVisible by rememberSaveable { mutableStateOf(true) }
    // Tam ekran tercihi: okuyucuya girişte varsayılan olarak sistem çubukları gizli
    var fullscreen by rememberSaveable { mutableStateOf(readerPreferences.current.immersiveByDefault) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showChapterPicker by rememberSaveable { mutableStateOf(false) }

    val pagerState = key(currentChapter.url) {
        rememberPagerState(pageCount = { pages.size })
    }
    val listState = key(currentChapter.url) {
        rememberLazyListState()
    }

    // İlerleme kaydı, ilk konum geri yüklendikten SONRA açılır; aksi hâlde
    // okuyucu açılır açılmaz "sayfa 0" yazıp kayıtlı konumu eziyordu.
    var progressTrackingEnabled by remember(currentChapter.url) { mutableStateOf(false) }

    // ── Konum geri yükleme (bölüm / mod değişiminde de çalışır) ─────────────
    LaunchedEffect(currentChapter.url, readingMode, uiState.isLoadingPages, pages.size) {
        if (uiState.isLoadingPages || pages.isEmpty()) return@LaunchedEffect
        val target = currentPage.coerceIn(0, pages.lastIndex)
        if (readingMode == ReadingMode.Webtoon) {
            if (listState.firstVisibleItemIndex != target) listState.scrollToItem(target)
        } else {
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        }
        progressTrackingEnabled = true
    }

    // ── Sayfa değişimi bildirimi (tek yol: ilerleme + prefetch) ─────────────
    val reportPage: (Int) -> Unit = { index ->
        if (progressTrackingEnabled && pages.isNotEmpty()) {
            viewModel.onPageChanged(currentChapter.url, index, pages.size)
            loader.onPageChanged(index)
        }
    }

    val isScrollMode = readingMode == ReadingMode.Webtoon

    // ── Gezinme yardımcıları ────────────────────────────────────────────────
    fun navigateNext() {
        if (pages.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isScrollMode) {
            val current = listState.firstVisibleItemIndex
            if (current < pages.lastIndex) {
                scope.launch { listState.animateScrollToItem(current + 1) }
            } else {
                viewModel.goToNextChapter()
            }
        } else {
            if (pagerState.currentPage < pages.lastIndex) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            } else {
                viewModel.goToNextChapter()
            }
        }
    }

    fun navigatePrevious() {
        if (pages.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isScrollMode) {
            val current = listState.firstVisibleItemIndex
            if (current > 0) {
                scope.launch { listState.animateScrollToItem(current - 1) }
            } else {
                viewModel.goToPreviousChapter()
            }
        } else {
            if (pagerState.currentPage > 0) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            } else {
                viewModel.goToPreviousChapter()
            }
        }
    }

    fun seekTo(index: Int) {
        if (pages.isEmpty()) return
        val target = index.coerceIn(0, pages.lastIndex)
        scope.launch {
            if (isScrollMode) listState.scrollToItem(target) else pagerState.scrollToPage(target)
        }
    }

    fun toggleFullscreen() {
        fullscreen = !fullscreen
        // Tam ekrana geçerken uygulamanın kendi barlarını da gizle:
        // "tam ekran" beklentisi budur. Çıkarken barlar geri gelir.
        menuVisible = !fullscreen
    }

    fun cycleOrientation() {
        val activity = context.findActivityOrNull() ?: return
        activity.requestedOrientation =
            if (activity.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            ) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
    }

    val updatePrefs: ((MangaReaderPrefs) -> MangaReaderPrefs) -> Unit = { transform ->
        readerPreferences.update(transform)
    }

    // ── Sistem çubukları (gerçek tam ekran) + ekranı açık tut ───────────────
    MangaReaderSystemUi(
        enabled = fullscreen,
        keepScreenOn = readerPrefs.keepScreenOn,
        reassertKey = showSettings to showChapterPicker,
    )

    // Ses tuşlarıyla sayfa çevirme için odak gerekir.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val volumeKeysEnabled = readerPrefs.volumeKeysEnabled
    val onKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        when {
            !volumeKeysEnabled -> false
            event.type != KeyEventType.KeyDown -> false
            event.key == Key.VolumeDown -> {
                navigateNext()
                true
            }
            event.key == Key.VolumeUp -> {
                navigatePrevious()
                true
            }
            else -> false
        }
    }

    var verificationUrl by remember { mutableStateOf<String?>(null) }
    if (verificationUrl != null) {
        KitsugiWebViewDialog(
            title = stringResource(R.string.manga_source_verify_in_browser),
            url = verificationUrl!!,
            onDismiss = {
                verificationUrl = null
                viewModel.retryLoadPages()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent(onKeyEvent)
    ) {
        // ── Okuyucu içeriği ─────────────────────────────────────────────────
        when {
            uiState.isLoadingPages && pages.isEmpty() -> {
                ReaderLoadingState()
            }

            uiState.pagesError != null -> {
                ReaderErrorState(
                    message = uiState.pagesError ?: stringResource(R.string.ui_error_generic),
                    errorCode = uiState.pagesErrorCode,
                    loginRequired = uiState.loginRequired,
                    verificationUrl = uiState.verificationUrl,
                    onLoginClick = { showMangaTrLogin = true },
                    onVerifyClick = { verifyUrl -> verificationUrl = verifyUrl },
                    onRetry = { viewModel.retryLoadPages() },
                )
            }

            pages.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.inline_no_pages_found_d05c840),
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }

            readingMode == ReadingMode.Webtoon -> {
                MangaWebtoonReader(
                    pages = pages,
                    listState = listState,
                    loader = loader,
                    colorFilterType = uiState.colorFilterType,
                    fitMode = uiState.fitMode,
                    mirrorPages = readerPrefs.mirrorPages,
                    tapZonesEnabled = readerPrefs.tapZonesEnabled,
                    invertTapZones = readerPrefs.invertTapZones,
                    onCenterTap = { menuVisible = !menuVisible },
                    onLongPress = { showSettings = true },
                    onPageChanged = reportPage,
                    onNextPage = { navigateNext() },
                    onPrevPage = { navigatePrevious() },
                    hasNextChapter = viewModel.hasNextChapter,
                    nextChapterName = viewModel.nextChapterName,
                    nextChapterVariantCount = viewModel.nextChapterVariantCount,
                    showChapterTransition = readerPrefs.showChapterTransition,
                    onGoToNextChapter = { viewModel.goToNextChapter() },
                )
            }

            readingMode == ReadingMode.Vertical -> {
                MangaVerticalPagerReader(
                    pages = pages,
                    pagerState = pagerState,
                    loader = loader,
                    colorFilterType = uiState.colorFilterType,
                    fitMode = uiState.fitMode,
                    mirrorPages = readerPrefs.mirrorPages,
                    showPageNumber = readerPrefs.showPageNumber,
                    tapZonesEnabled = readerPrefs.tapZonesEnabled,
                    invertTapZones = readerPrefs.invertTapZones,
                    onCenterTap = { menuVisible = !menuVisible },
                    onLongPress = { showSettings = true },
                    onNextPage = { navigateNext() },
                    onPrevPage = { navigatePrevious() },
                    onPageChanged = reportPage,
                )
            }

            else -> {
                MangaPagerReader(
                    pages = pages,
                    pagerState = pagerState,
                    loader = loader,
                    readingMode = readingMode,
                    colorFilterType = uiState.colorFilterType,
                    fitMode = uiState.fitMode,
                    mirrorPages = readerPrefs.mirrorPages,
                    showPageNumber = readerPrefs.showPageNumber,
                    tapZonesEnabled = readerPrefs.tapZonesEnabled,
                    invertTapZones = readerPrefs.invertTapZones,
                    onCenterTap = { menuVisible = !menuVisible },
                    onLongPress = { showSettings = true },
                    onNextPage = { navigateNext() },
                    onPrevPage = { navigatePrevious() },
                    onPageChanged = reportPage,
                )
            }
        }

        // ── Özel parlaklık katmanı (yalnızca karartma yönünde) ──────────────
        if (uiState.customBrightness < 1.0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 1.0f - uiState.customBrightness))
            )
        }

        // ── Üst bar ─────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            MangaReaderTopBar(
                title = mangaDetails.title,
                chapterName = currentChapter.name,
                fullscreen = fullscreen,
                scanlator = currentChapter.scanlator,
                onBack = onBack,
                onToggleFullscreen = { toggleFullscreen() },
                onOpenChapterList = { showChapterPicker = true },
                onOpenSettings = { showSettings = true },
            )
        }

        // ── Alt bar ─────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = menuVisible && pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            MangaReaderBottomBar(
                currentPage = currentPage,
                pageCount = pages.size,
                readingMode = readingMode,
                fitMode = uiState.fitMode,
                fullscreen = fullscreen,
                hasPreviousChapter = viewModel.hasPreviousChapter,
                hasNextChapter = viewModel.hasNextChapter,
                onSeek = { index -> seekTo(index) },
                onPreviousChapter = { viewModel.goToPreviousChapter() },
                onNextChapter = { viewModel.goToNextChapter() },
                onOpenChapterList = { showChapterPicker = true },
                onCycleReadingMode = {
                    viewModel.setReadingMode(MangaSettingIds.nextReadingMode(readingMode))
                },
                onCycleFitMode = {
                    viewModel.setFitMode(MangaSettingIds.nextFitMode(uiState.fitMode))
                },
                onRotate = { cycleOrientation() },
                onToggleFullscreen = { toggleFullscreen() },
                onOpenSettings = { showSettings = true },
            )
        }
    }

    // ── Ayar paneli ──────────────────────────────────────────────────────────
    if (showSettings) {
        MangaReaderSettingsSheet(
            readingMode = readingMode,
            fitMode = uiState.fitMode,
            colorFilterType = uiState.colorFilterType,
            brightness = uiState.customBrightness,
            prefs = readerPrefs,
            onReadingModeSelected = { viewModel.setReadingMode(it) },
            onFitModeSelected = { viewModel.setFitMode(it) },
            onColorFilterSelected = { viewModel.setColorFilter(it) },
            onBrightnessChanged = { viewModel.setCustomBrightness(it) },
            onBrightnessReset = { viewModel.setCustomBrightness(1.0f) },
            onPrefsChanged = updatePrefs,
            onDismiss = { showSettings = false },
        )
    }

    // ── Bölüm listesi ────────────────────────────────────────────────────────
    if (showChapterPicker) {
        MangaChapterPickerSheet(
            groups = uiState.chapterGroups,
            currentChapterUrl = currentChapter.url,
            preferredScanlator = uiState.preferredScanlator,
            isLoading = uiState.isLoadingChapters,
            onSelect = { selected ->
                showChapterPicker = false
                if (selected.url != currentChapter.url) {
                    viewModel.goToChapter(selected)
                }
            },
            onDismiss = { showChapterPicker = false },
        )
    }

    // ── Çevirmen seçim penceresi (aynı bölümün birden çok çevirmeni) ─────────
    uiState.pendingChoice?.let { request ->
        MangaChapterChoiceDialog(
            request = request,
            onConfirm = { chapter, remember, autoContinue ->
                viewModel.confirmChapterChoice(
                    chapter = chapter,
                    rememberScanlator = remember,
                    autoContinue = autoContinue,
                )
            },
            onDismiss = { viewModel.dismissChapterChoice() },
        )
    }
}

// ─── Yükleme / hata durumları ────────────────────────────────────────────────

@Composable
private fun ReaderLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = KitsugiColors.Accent, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.inline_loading_page_list_31ccd61),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ReaderErrorState(
    message: String,
    errorCode: String?,
    loginRequired: Boolean = false,
    verificationUrl: String? = null,
    onLoginClick: () -> Unit = {},
    onVerifyClick: (String) -> Unit = {},
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            if (errorCode != null) {
                Spacer(Modifier.height(10.dp))
                PlatformErrorExplanationBlock(code = errorCode)
            }
            if (loginRequired) {
                Spacer(Modifier.height(16.dp))
                KitsugiButton(onClick = onLoginClick) {
                    Text(stringResource(R.string.giris_yap), color = Color.White)
                }
            }
            if (verificationUrl != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.manga_source_verify_hint),
                    color = Color.White.copy(0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                KitsugiButton(
                    onClick = { onVerifyClick(verificationUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Text(stringResource(R.string.manga_source_verify_in_browser), color = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
            KitsugiButton(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KitsugiColors.Accent,
                    contentColor = KitsugiColors.OnAccent,
                ),
            ) {
                Text(stringResource(R.string.manga_reader_retry), color = KitsugiColors.OnAccent)
            }
        }
    }
}
