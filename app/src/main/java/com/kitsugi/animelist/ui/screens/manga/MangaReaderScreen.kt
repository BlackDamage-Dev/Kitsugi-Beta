package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.kitsugi.animelist.ui.theme.gradient.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.Slider
import androidx.compose.material3.SliderDefaults
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
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

    val scope      = rememberCoroutineScope()
    var menuVisible     by remember { mutableStateOf(true) }
    var showSettings    by remember { mutableStateOf(false) }

    // Bölüm değişince pager/list state'i YENİDEN oluştur. Aksi halde yeni bölümün
    // sayfaları eski bölümün konumunda açılıyor, "Tekrar Dene" sonrası scroll
    // bir daha çalışmıyordu.
    val pagerState = key(currentChapter.url) {
        rememberPagerState(initialPage = 0) { pages.size }
    }
    val listState = key(currentChapter.url) {
        rememberLazyListState()
    }

    // Tek seferlik restore artık bölüm URL'i + gerçekten yüklenmiş sayfa listesi
    // ile anahtarlanıyor. Eski kod currentPage değişince tekrar tetikleniyor ve
    // bölüm geçişinde eski sayfa listesiyle yanlış konuma kaydırıyordu.
    var restoredChapterUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentChapter.url, pages, uiState.isLoadingPages) {
        if (!uiState.isLoadingPages && pages.isNotEmpty() && restoredChapterUrl != currentChapter.url) {
            restoredChapterUrl = currentChapter.url
            val targetPage = currentPage.coerceIn(0, pages.size - 1)
            if (readingMode == ReadingMode.Webtoon) {
                listState.scrollToItem(targetPage)
            } else {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    // Pager'daki sayfa değişimini takip et — V2 prefetch tetikle
    // Webtoon modunda pagerState DİKKATE ALINMAZ: pagerState.currentPage her zaman
    // 0 kalır ve listState'in bildirdiği gerçek ilerlemeyi 0 ile ezerdi!
    LaunchedEffect(pagerState.currentPage) {
        if (restoredChapterUrl == currentChapter.url &&
            pages.isNotEmpty() &&
            readingMode != ReadingMode.Webtoon
        ) {
            viewModel.onPageChanged(currentChapter.url, pagerState.currentPage, pages.size)
            // R2: Sayfa değiştikçe V2 loader'da 3 ileri prefetch'i başlat
            loader.onPageChanged(pagerState.currentPage)
        }
    }

    // Manga-TR / DDoS-Guard bot koruması: aynı WebView cookie oturumunda doğrula,
    // kapatınca sayfaları yeniden çek (kapı token'ı tek kullanımlık olduğu için
    // körü körüne retry değil, taze sayfa listesi gerekir).
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
                    KitsugiArtIcon(art = KitsugiArt.Warning, size = 48.dp)
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
                    uiState.verificationUrl?.let { verifyUrl ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.manga_source_verify_hint),
                            color = Color.White.copy(0.5f),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        KitsugiButton(
                            onClick = { verificationUrl = verifyUrl },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                        ) {
                            Text(stringResource(R.string.manga_source_verify_in_browser), color = Color.White)
                        }
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
                    fitMode      = uiState.fitMode,
                    hasNextChapter = viewModel.hasNextChapter,
                    nextChapterName = viewModel.nextChapterName,
                    onGoToNextChapter = { viewModel.goToNextChapter() },
                    onCenterTap  = { menuVisible = !menuVisible },
                    onPageChanged = { idx ->
                        if (restoredChapterUrl == currentChapter.url) {
                            viewModel.onPageChanged(currentChapter.url, idx, pages.size)
                            // R2: Webtoon scroll'da V2 prefetch tetikle
                            loader.onPageChanged(idx)
                        }
                    },
                    onNextPage   = {
                        val current = listState.firstVisibleItemIndex
                        if (current < pages.size - 1) {
                            scope.launch {
                                listState.animateScrollToItem(current + 1)
                            }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage   = {
                        val current = listState.firstVisibleItemIndex
                        if (current > 0) {
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
                    fitMode      = uiState.fitMode,
                    onCenterTap  = { menuVisible = !menuVisible },
                    onNextPage   = {
                        if (pagerState.currentPage < pages.size - 1) {
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
                            Icons.ArrowBack,
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
                            Icons.MenuBook,
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
                            imageVector = Icons.ArrowBack,
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
                            imageVector = Icons.ArrowForward,
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
                        ReadingMode.RightToLeft -> Icons.MenuBook
                        ReadingMode.LeftToRight -> Icons.SwapHoriz
                        ReadingMode.Vertical    -> Icons.ViewStream
                        ReadingMode.Webtoon     -> Icons.ViewStream
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
                            imageVector = Icons.ScreenRotation,
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
                            imageVector = Icons.CropFree,
                            contentDescription = stringResource(R.string.sigdirma_modu),
                            tint = Color.White
                        )
                    }

                    // Settings Dialog Trigger
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Settings,
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
        KitsugiSheetOrDialog(
            onDismiss = { showSettings = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.inline_reader_settings_82b7841),
                    color     = Color.White,
                    fontSize  = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier  = Modifier.padding(bottom = 16.dp)
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
                        imageVector = Icons.BrightnessLow,
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
                            imageVector = Icons.RestartAlt,
                            contentDescription = stringResource(R.string.slider_reset_to_default),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.BrightnessHigh,
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
