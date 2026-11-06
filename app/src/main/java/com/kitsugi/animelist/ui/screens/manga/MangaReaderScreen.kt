package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog
import com.kitsugi.animelist.ui.screens.manga.components.MangaChapterChoiceDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiScrollToTopFab
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.MangaSourceAuthBanner
import com.kitsugi.animelist.ui.components.MangaSourceLoginDialog
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.screens.manga.components.MangaPagerReader
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
import kotlinx.coroutines.launch

/**
 * Manga Okuyucu Ana Ekranı
 *
 * — Pager modu (LTR / RTL / Dikey) → [MangaPagerReader]
 * — Webtoon modu (sürekli dikey) → [MangaWebtoonReader]
 * — Üst ve alt barlar merkeze tıklanınca gizlenir/gösterilir (immersive)
 * — Alt çekme menüsünden okuma modu anında değiştirilebilir
 * — Sayfa ilerlemesi ve bölüm bilgisi alt çubukta gösterilir
 *
 * ## v2.4.341 — sayfa galerisi + hızlı yukarı kaydırma
 *  - Barlar açıkken alt barın üstünde **sayfa galerisi şeridi** görünür; sayfalar oradan
 *    seçilerek (tek dokunuşla) atlanır. Şeritteki görsele dokunmak ilgili sayfaya gider.
 *  - Aynı şeritte "galeri" düğmesi tam ekran **sayfa galerisi**ni açar (ızgara + sayfa
 *    numarası); bu galeride bir sayfaya dokunmak o sayfaya atlar.
 *  - Webtoon modunda aşağı kaydırınca Keşfet sayfasındaki [KitsugiScrollToTopFab] birebir
 *    aynı düğme belirir ve en üstteki sayfaya yumuşakça döner.
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

    val currentChapter = uiState.currentChapter
    val readingMode = uiState.readingMode
    val currentPage = uiState.currentPageIndex
    val loader = viewModel.loader

    val scope = rememberCoroutineScope()
    var menuVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var showChapterPicker by remember { mutableStateOf(false) }
    var showSourceLogin by remember { mutableStateOf(false) }
    var showMangaTrLogin by remember { mutableStateOf(false) }
    if (showMangaTrLogin) {
        MangaTrLoginDialog(onDismiss = {
            showMangaTrLogin = false
            viewModel.retryLoadPages()
        })
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

    var galleryInitialIndex by remember { mutableStateOf(currentPage) }

    var fullscreenOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val fullscreen = fullscreenOverride ?: (uiState.settingsLoaded && uiState.autoFullscreen)
    MangaReaderSystemBars(fullscreen = fullscreen)

    val pagerState = rememberPagerState(initialPage = currentPage) { pages.size }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

    var hasRestoredProgress by remember(currentChapter.url) { mutableStateOf(false) }

    LaunchedEffect(currentChapter.url) {
        hasRestoredProgress = false
    }

    LaunchedEffect(pages, currentPage) {
        if (pages.isNotEmpty() && !hasRestoredProgress) {
            hasRestoredProgress = true
            val targetPage = currentPage.coerceIn(0, pages.size - 1)
            if (readingMode == ReadingMode.Webtoon) {
                listState.scrollToItem(targetPage)
            } else {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    // Pager'daki sayfa değişimini takip et — V2 prefetch tetikle
    LaunchedEffect(pagerState.currentPage) {
        if (hasRestoredProgress && pages.isNotEmpty()) {
            viewModel.onPageChanged(currentChapter.url, pagerState.currentPage, pages.size)
            // R2: Sayfa değiştikçe V2 loader'da 3 ileri prefetch'i başlat
            loader.onPageChanged(pagerState.currentPage)
            // Otomatik bölüm geçişi (Mihon "always show chapter transition" karşılığı):
            // son sayfada beklemek yerine sıradaki bölüme geçilir.
            if (uiState.autoAdvanceChapter &&
                pagerState.currentPage >= pages.size - 1 &&
                viewModel.hasNextChapter
            ) {
                viewModel.goToNextChapter()
            }
        }
    }

    // Webtoon modunda da bölüm sonu otomatik geçişi: son görünür öğe son sayfa ise.
    LaunchedEffect(listState.firstVisibleItemIndex, readingMode) {
        if (readingMode != ReadingMode.Webtoon) return@LaunchedEffect
        if (!hasRestoredProgress || pages.isEmpty()) return@LaunchedEffect
        if (uiState.autoAdvanceChapter &&
            listState.firstVisibleItemIndex >= pages.size - 1 &&
            viewModel.hasNextChapter
        ) {
            viewModel.goToNextChapter()
        }
    }

    // ── Ekranı açık tut ──────────────────────────────────────────────────────
    // Okuyucuda sistem uykuya geçerse uzun bölüm okurken sayfa birden kapanıyordu.
    val localView = LocalView.current
    LaunchedEffect(uiState.keepScreenOn) {
        localView.keepScreenOn = uiState.keepScreenOn
    }

    // ── İlerlemeyi kaybetme ──────────────────────────────────────────────────
    // Sayfa kaydı 500 ms debounce'lu; kullanıcı hemen çıkarsa son sayfa kaybolmasın diye
    // arkaya geçişte ve ekran kapanırken bekleyen kayıt anında yazılır.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) viewModel.flushProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.flushProgress()
            localView.keepScreenOn = false
        }
    }

    /** Herhangi bir sayfaya (moddan bağımsız) animasyonlu geçiş. */
    fun goToPage(index: Int) {
        val target = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        scope.launch {
            if (readingMode == ReadingMode.Webtoon) {
                listState.animateScrollToItem(target)
            } else {
                pagerState.animateScrollToPage(target)
            }
        }
        viewModel.onPageChanged(currentChapter.url, target, pages.size)
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
                    Text(
                        stringResource(R.string.inline_loading_page_list_31ccd61),
                        color = Color.White.copy(0.6f),
                        fontSize = 13.sp
                    )
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
                        textAlign = TextAlign.Center
                    )
                    uiState.pagesErrorCode?.let { code ->
                        Spacer(Modifier.height(10.dp))
                        com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code)
                    }
                    if (uiState.loginRequired) {
                        Spacer(Modifier.height(16.dp))
                        KitsugiButton(onClick = { showMangaTrLogin = true }) {
                            Text(stringResource(R.string.giris_yap), color = Color.White)
                        }
                    }
                    if (uiState.verificationUrl != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.manga_source_verify_hint),
                            color = Color.White.copy(0.5f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        KitsugiButton(
                            onClick = { verificationUrl = uiState.verificationUrl },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                        ) {
                            Text(stringResource(R.string.manga_source_verify_in_browser), color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    if (PlatformErrorCode.fromId(uiState.pagesErrorCode) == PlatformErrorCode.MangaAuth) {
                        MangaSourceAuthBanner(
                            source = source,
                            onLogin = { showSourceLogin = true },
                            onRetry = { viewModel.retryLoadPages() },
                        )
                    } else {
                        KitsugiButton(
                            onClick = { viewModel.retryLoadPages() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                        ) {
                            Text(stringResource(R.string.manga_retry), color = Color.White)
                        }
                    }
                }
            }
        } else if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.inline_no_pages_found_d05c840),
                    color = Color.White.copy(0.5f)
                )
            }
        } else {
            when (readingMode) {
                ReadingMode.Webtoon -> MangaWebtoonReader(
                    pages = pages,
                    listState = listState,
                    loader = loader,
                    colorFilterType = uiState.colorFilterType,
                    fitMode = uiState.fitMode,
                    cropBorders = uiState.cropBorders,
                    hasNextChapter = viewModel.hasNextChapter,
                    nextChapterName = viewModel.nextChapterName,
                    onGoToNextChapter = { viewModel.goToNextChapter() },
                    onCenterTap = { menuVisible = !menuVisible },
                    onSourceLogin = { showSourceLogin = true },
                    onPageChanged = { index ->
                        if (hasRestoredProgress) {
                            viewModel.onPageChanged(currentChapter.url, index, pages.size)
                        }
                    },
                    // Kullanıcı isteği: sayfaya uzun basmak sayfa galerisini açar.
                    onLongPressPage = { pageIndex ->
                        galleryInitialIndex = pageIndex
                        showGallery = true
                    },
                    onNextPage = {
                        val current = listState.firstVisibleItemIndex
                        if (current < pages.size - 1) {
                            scope.launch { listState.animateScrollToItem(current + 1) }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage = {
                        val current = listState.firstVisibleItemIndex
                        if (current > 0) {
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
                    fitMode = uiState.fitMode,
                    cropBorders = uiState.cropBorders,
                    onCenterTap = { menuVisible = !menuVisible },
                    onSourceLogin = { showSourceLogin = true },
                    onLongPressPage = { pageIndex ->
                        galleryInitialIndex = pageIndex
                        showGallery = true
                    },
                    onNextPage = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage = {
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

        // ─── Hızlı Yukarı Kaydırma (Keşfet ile birebir aynı bileşen) ──────────
        // Yalnızca dikey (webtoon) okuma modunda anlamlı: kullanıcı yüzlerce sayfalık
        // bir bölümün ortasından tek dokunuşla başa dönebilir.
        if (readingMode == ReadingMode.Webtoon && pages.isNotEmpty()) {
            val showScrollToTop by remember {
                androidx.compose.runtime.derivedStateOf {
                    listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 400
                }
            }
            KitsugiScrollToTopFab(
                visible = showScrollToTop,
                isLandscape = LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                bottomBarScrollState = null,
                fallbackBottomBarVisible = menuVisible,
                // Alt bar 2 satır: gezinme sürgüsü (≈48dp) + araç satırı (48dp)
                // + dikey dolgu. Düğme bu yüksekliğin üstünde durmalı.
                bottomBarHeight = 128.dp,
                onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                    viewModel.onPageChanged(currentChapter.url, 0, pages.size)
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // ─── Üst Bar ─────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
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
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Bölüm adına dokunmak bölüm listesini açar: okuyucudan çıkmadan
                        // başka bir bölüme geçmek için geri dönmek gerekmiyor.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.androidxClickable { showChapterPicker = true }
                        ) {
                            Text(
                                currentChapter.name,
                                color = Color.White.copy(0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (uiState.chapterList.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Rounded.ExpandMore,
                                    contentDescription = stringResource(R.string.bolumler),
                                    tint = Color.White.copy(0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    if (pages.isNotEmpty()) {
                        // Sayfa galerisi kısayolu — üst bardan da erişilebilir.
                        IconButton(onClick = {
                            galleryInitialIndex = currentPage
                            showGallery = true
                        }) {
                            Icon(
                                Icons.Rounded.GridView,
                                contentDescription = stringResource(R.string.manga_reader_open_gallery),
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = { fullscreenOverride = !fullscreen }) {
                        Icon(
                            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = stringResource(if (fullscreen) R.string.manga_exit_fullscreen else R.string.manga_enter_fullscreen),
                            tint = Color.White
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

        // ─── Bölüm sonu ipucu ────────────────────────────────────────────────
        // Bölümün son sayfasına gelince menüyü açmadan "Sonraki bölüm" düğmesi çıkar;
        // 100+ sayfalık bölümü bitirip alt barı aramak zorunda kalmıyorsun.
        val atLastPage = pages.isNotEmpty() && currentPage >= pages.size - 1
        if (!menuVisible) {
            AnimatedVisibility(
                visible = atLastPage,
                enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (viewModel.hasPreviousChapter) {
                        KitsugiButton(
                            onClick = { viewModel.goToPreviousChapter() },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF26263B)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.onceki_bolum),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.onceki_bolum),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (viewModel.hasNextChapter) {
                        KitsugiButton(
                            onClick = { viewModel.goToNextChapter() },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                        ) {
                            Text(
                                text = stringResource(R.string.sonraki_bolum) +
                                    (viewModel.nextChapterName?.let { " · $it" } ?: ""),
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ─── Alt Bar (Aniyomi Parity) + Sayfa Galerisi Şeridi ────────────────
        AnimatedVisibility(
            visible = menuVisible && pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val barBackgroundColor = Color(0xFF161622).copy(alpha = 0.95f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBackgroundColor)
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. ChapterNavigator (Aniyomi Style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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

                            var sliderValue by remember(currentPage) {
                                mutableStateOf((currentPage + 1).toFloat())
                            }
                            Slider(
                                value = sliderValue,
                                onValueChange = {
                                    sliderValue = it
                                    val targetPage = it.toInt() - 1
                                    scope.launch {
                                        if (readingMode == ReadingMode.Webtoon) {
                                            listState.scrollToItem(targetPage.coerceIn(0, pages.size - 1))
                                        } else {
                                            pagerState.scrollToPage(targetPage.coerceIn(0, pages.size - 1))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Reading Mode cycle
                    val readingModeIcon = when (readingMode) {
                        ReadingMode.RightToLeft -> Icons.AutoMirrored.Filled.MenuBook
                        ReadingMode.LeftToRight -> Icons.Default.SwapHoriz
                        ReadingMode.Vertical -> Icons.Default.ViewStream
                        ReadingMode.Webtoon -> Icons.Default.ViewStream
                    }
                    IconButton(onClick = {
                        val nextMode = when (readingMode) {
                            ReadingMode.RightToLeft -> ReadingMode.LeftToRight
                            ReadingMode.LeftToRight -> ReadingMode.Vertical
                            ReadingMode.Vertical -> ReadingMode.Webtoon
                            ReadingMode.Webtoon -> ReadingMode.RightToLeft
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
                        val activity = context.findActivity()
                        if (activity != null) {
                            val currentOrientation = activity.requestedOrientation
                            if (currentOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
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
                            MangaFitMode.FitWidth -> MangaFitMode.FitHeight
                            MangaFitMode.FitHeight -> MangaFitMode.FitScreen
                            else -> MangaFitMode.FitScreen
                        }
                        viewModel.setFitMode(nextFit)
                    }) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = stringResource(R.string.sigdirma_modu),
                            tint = Color.White
                        )
                    }

                    // Sayfa galerisi — alt bardan da tek dokunuşla erişilir
                    // (üst barda ▦, sayfada uzun basış da aynı galeriyi açar).
                    IconButton(onClick = {
                        galleryInitialIndex = currentPage
                        showGallery = true
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.manga_reader_open_gallery),
                            tint = Color.White
                        )
                    }

                    // Settings Dialog Trigger
                    IconButton(onClick = { fullscreenOverride = !fullscreen }) {
                        Icon(
                            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = stringResource(if (fullscreen) R.string.manga_exit_fullscreen else R.string.manga_enter_fullscreen),
                            tint = Color.White
                        )
                    }
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

    // ─── Bölüm seçici (okuyucudan çıkmadan bölüm değiştirme) ────────────────

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

    if (showChapterPicker) {
        MangaChapterPickerSheet(
            chapters = uiState.chapterList
                .sortedWith(compareBy({ it.chapterNumber < 0f }, { it.chapterNumber })),
            currentUrl = currentChapter.url,
            accentColor = Color(0xFF7C4DFF),
            onSelect = { picked ->
                showChapterPicker = false
                if (picked.url != currentChapter.url) viewModel.goToChapter(picked)
            },
            onDismiss = { showChapterPicker = false }
        )
    }

    // ─── Tam Ekran Sayfa Galerisi ────────────────────────────────────────────
    if (showGallery && pages.isNotEmpty()) {
        MangaPageGallerySheet(
            pages = pages,
            currentIndex = currentPage,
            initialFocusIndex = galleryInitialIndex,
            accentColor = Color(0xFF7C4DFF),
            loader = loader,
            onSelect = { index ->
                showGallery = false
                goToPage(index)
            },
            onDismiss = { showGallery = false }
        )
    }

    // ─── Okuma Modu Ayarlar Paneli ────────────────────────────────────────────
    if (showSettings) {
        // Reader-settings paneli kısa bir bottom sheet: varsayılan %90 yükseklik
        // boş ekran bırakıyordu (ekran görüntüsündeki "çok yer kaplıyor" geri bildirimi).
        KitsugiSheetOrDialog(
            onDismiss = { showSettings = false },
            heightFraction = 0.62f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Sheet kısa tutulduğu için içerik kaydırılabilir olmalı;
                    // aksi hâlde küçük ekranlarda parlaklık sürgüsü kesiliyordu.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.inline_reader_settings_82b7841),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Okuma Yönü
                Text(
                    stringResource(R.string.manga_reading_direction),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        ReadingMode.RightToLeft to "RTL (Manga)",
                        ReadingMode.LeftToRight to "LTR",
                        ReadingMode.Vertical to "Dikey",
                        ReadingMode.Webtoon to "Webtoon"
                    )
                    modes.forEach { (mode, label) ->
                        FilterChip(
                            selected = readingMode == mode,
                            onClick = { viewModel.setReadingMode(mode) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor = Color(0xFF7C4DFF),
                                containerColor = Color(0xFF2A2A40),
                                labelColor = Color.White
                            )
                        )
                    }
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fits = listOf(
                        MangaFitMode.FitScreen to stringResource(R.string.manga_fit_screen),
                        MangaFitMode.FitWidth to "Genişlik",
                        MangaFitMode.FitHeight to "Yükseklik"
                    )
                    fits.forEach { (fit, label) ->
                        FilterChip(
                            selected = uiState.fitMode == fit,
                            onClick = { viewModel.setFitMode(fit) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor = Color(0xFF7C4DFF),
                                containerColor = Color(0xFF2A2A40),
                                labelColor = Color.White
                            )
                        )
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        ColorFilterType.Normal to "Normal",
                        ColorFilterType.Grayscale to "Siyah-Beyaz",
                        ColorFilterType.Sepia to "Sepya",
                        ColorFilterType.Invert to "Ters Negatif"
                    )
                    filters.forEach { (filter, label) ->
                        FilterChip(
                            selected = uiState.colorFilterType == filter,
                            onClick = { viewModel.setColorFilter(filter) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor = Color(0xFF7C4DFF),
                                containerColor = Color(0xFF2A2A40),
                                labelColor = Color.White
                            )
                        )
                    }
                }
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
                Spacer(Modifier.height(16.dp))

                // ── Bölüm geçişi davranışı (Mihon: "Skip read chapters") ─────────
                // Okurken sıradaki bölüme geçerken zaten okunmuş bölümlerin atlanması.
                ReaderSettingChip(
                    label = stringResource(R.string.manga_skip_read_chapters),
                    selected = uiState.skipReadChapters,
                    accent = Color(0xFF7C4DFF),
                    onClick = { viewModel.setSkipReadChapters(!uiState.skipReadChapters) }
                )
                Spacer(Modifier.height(8.dp))

                // ── Kenarlık kırpma (Mihon: crop borders) ───────────────────────
                ReaderSettingChip(
                    label = stringResource(R.string.manga_crop_borders),
                    selected = uiState.cropBorders,
                    accent = Color(0xFF7C4DFF),
                    onClick = { viewModel.setCropBorders(!uiState.cropBorders) }
                )
                Spacer(Modifier.height(8.dp))

                // ── Bölüm bitince otomatik geçiş (Mihon: chapter transition) ────
                ReaderSettingChip(
                    label = stringResource(R.string.manga_auto_advance_chapter),
                    selected = uiState.autoAdvanceChapter,
                    accent = Color(0xFF7C4DFF),
                    onClick = { viewModel.setAutoAdvanceChapter(!uiState.autoAdvanceChapter) }
                )
                Spacer(Modifier.height(8.dp))

                // ── Otomatik tam ekran ─────────────────────────────────────────
                ReaderSettingChip(
                    label = stringResource(R.string.manga_auto_fullscreen),
                    selected = uiState.autoFullscreen,
                    accent = Color(0xFF7C4DFF),
                    onClick = {
                        val next = !uiState.autoFullscreen
                        viewModel.setAutoFullscreen(next)
                        fullscreenOverride = next
                    }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSourceLogin) {
        MangaSourceLoginDialog(
            source = source,
            onSessionUpdated = { viewModel.retryLoadPages() },
            onDismiss = { showSourceLogin = false },
        )
    }
}

/**
 * Okuyucu ayar panelindeki tek satırlık aç/kapa anahtarı.
 *
 * Panelde yer kazanmak için Switch yerine çip kullanılıyor: aynı durumu (açık/kapalı)
 * tek dokunuşla değiştirir ve panelin 0.62f yüksekliğine sığar.
 */
@Composable
private fun ReaderSettingChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color(0xFF2A2A40))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color.White.copy(0.85f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (selected) "AÇIK" else "KAPALI",
            color = if (selected) accent else Color.White.copy(0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tam ekran sayfa galerisi
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bölümdeki tüm sayfaların ızgara galerisi.
 *
 * Amaç: 100+ sayfalık bir bölümde slider'ı milimetrik sürüklemek yerine hedef sayfayı
 * görsel olarak bulup tek dokunuşla atlamak. Seçilen sayfa çağırana bildirilir.
 */
@Composable
private fun MangaPageGallerySheet(
    pages: List<com.kitsugi.animelist.data.manga.MangaPage>,
    currentIndex: Int,
    /** Galeri açıldığında odaklanacak sayfa (şeritten gelindiğinde o sayfa). */
    initialFocusIndex: Int,
    accentColor: Color,
    loader: com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        // Tam ekran: sistem çubuklarını da kapsar, tek elle kullanım için üstten kapatma
        // düğmesi ve alt boşluk navigation bar'a göre hesaplanır.
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.manga_reader_gallery_title),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.manga_reader_gallery_hint),
                            color = Color.White.copy(0.6f),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${currentIndex + 1}/${pages.size}",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.kapat), tint = Color.White)
                    }
                }

                // Galeri, o an okunan sayfada açılır; kullanıcı hedefi aramak zorunda kalmaz.
                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
                    initialFirstVisibleItemIndex = (initialFocusIndex - 2).coerceAtLeast(0)
                )
                androidx.compose.runtime.LaunchedEffect(initialFocusIndex) {
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
                        bottom = 24.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars
                            .asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = pages.size,
                        key = { index -> pages[index].index }
                    ) { index ->
                        val page = pages[index]
                        // Galeri küçük resimleri de aynı önbellekten beslenir.
                        LaunchedEffect(page.index) { loader.loadPage(page) }
                        val selected = index == currentIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.22f) else Color(0xFF1B1B26))
                                .androidxClickable {
                                    // Hatalı sayfaya dokunmak onu yeniden dener,
                                    // sağlıklı sayfaya dokunmak o sayfaya atlar.
                                    if (page.status == MangaPageStatus.Error) {
                                        loader.retryPage(page)
                                    } else {
                                        onSelect(index)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (page.status == MangaPageStatus.Ready) {
                                // Galeri küçük resimleri de okuyucunun disk önbelleğini kullanır:
                                // telephoto yüklenen dosyayı (varsa) doğrudan gösterir, aksi hâlde URL.
                                val source = page.imageUrl ?: page.url
                                val cachedModel = remember(page.index, page.status, source) {
                                    runCatching {
                                        val file = loader.cache.getImageFile(source)
                                        if (file.exists()) file else source
                                    }.getOrDefault(source)
                                }
                                AsyncImage(
                                    model = cachedModel,
                                    contentDescription = stringResource(
                                        R.string.manga_reader_page_number,
                                        index + 1
                                    ),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (page.status == MangaPageStatus.Error) {
                                // Hatalı sayfa galeride de görünür: dokununca yeniden dener.
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.manga_retry),
                                        tint = Color.White.copy(0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.manga_retry),
                                        color = Color.White.copy(0.6f),
                                        fontSize = 9.sp
                                    )
                                }
                            } else {
                                CircularProgressIndicator(
                                    color = Color.White.copy(0.25f),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
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
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(accentColor.copy(alpha = 0.15f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bölüm seçici alt sayfası.
 *
 * Okuyucudan çıkmadan bölüm değiştirmek için: liste kaydırılabilir, geçerli bölüm
 * accent renkle işaretli ve otomatik olarak o bölüme kaydırılır.
 */
@Composable
private fun MangaChapterPickerSheet(
    chapters: List<MangaChapter>,
    currentUrl: String,
    accentColor: Color,
    onSelect: (MangaChapter) -> Unit,
    onDismiss: () -> Unit
) {
    val currentIndex = chapters.indexOfFirst { it.url == currentUrl }.coerceAtLeast(0)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0)
    )
    KitsugiSheetOrDialog(onDismiss = onDismiss, heightFraction = 0.72f) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.bolumler) + " (${chapters.size})",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.kapat),
                        tint = Color.White.copy(0.8f)
                    )
                }
            }
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = chapters,
                    key = { it.url }
                ) { item ->
                    val isCurrent = item.url == currentUrl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) accentColor.copy(alpha = 0.16f) else Color.Transparent
                            )
                            .androidxClickable { onSelect(item) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            color = if (isCurrent) accentColor else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrent) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ripple'sız dokunma.
 *
 * Galeri ızgarasında yüzlerce küçük resim aynı anda çizildiği için varsayılan ripple
 * görsel gürültü üretiyordu; dokunma geri bildirimi seçili sayfanın vurgusuyla veriliyor.
 */
@Composable
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )

private fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
