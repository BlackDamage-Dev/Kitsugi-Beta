package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * Tek bir manga sayfası — hem yatay/dikey sayfalama hem webtoon modunda kullanılır.
 *
 * Özellikler
 * ----------
 *  • Telephoto `ZoomableAsyncImage`: pinch-to-zoom, çift dokunma ile yakınlaştırma,
 *    dev görsellerde kiremit (subsampling) çizim → OOM riski yok.
 *  • Dokunma bölgeleri (Mihon paritesi): sol/orta/sağ veya 3×3 ızgara.
 *  • Uzun basma → okuyucu menüsü/ayarları (Mihon "show on long tap").
 *  • Yatay aynalama (`mirrorPages`): aynalanmış servis edilen sayfaları düzeltir.
 *  • Sığdırma modları: FitScreen / FitWidth / FitHeight / Crop / Original.
 *  • Renk filtreleri: Normal / Warm / Cool / Grayscale / Sepia / Invert + Saturation / Contrast / Hue.
 *  • Otomatik arkaplan rengi ve kenar boşluğu kırpma desteği.
 *  • Sağ altta sayfa numarası rozeti (kapatılabilir).
 */
@Composable
fun MangaPageItem(
    page: MangaPage,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode = ReadingMode.RightToLeft,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    saturation: Int = 100,
    contrast: Int = 100,
    hue: Int = 0,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    pageBackground: Color = Color.Black,
    autoBackground: Boolean = false,
    cropBorders: Boolean = false,
    mirrorPages: Boolean = false,
    showPageNumber: Boolean = true,
    tapZonesEnabled: Boolean = true,
    invertTapZones: Boolean = false,
    tapLayout: String = "Default",
    tapGrid: String = DEFAULT_READER_TAP_GRID,
    longTapGrid: String = DEFAULT_READER_LONG_TAP_GRID,
    tapViewport: ReaderTapViewport? = null,
    zoomEnabled: Boolean = true,
    resetZoomSignal: Int = 0,
    wideRotation: String = "Off",
    imageOverride: java.io.File? = null,
    tapsEnabled: Boolean = true,
    onCenterTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    /** Sayfaya uzun basınca galeri açılır. */
    onLongPressPage: ((Int) -> Unit)? = null,
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    onSourceLogin: (() -> Unit)? = null,
    /** Webtoon'da yer tutucu boyutu için paylaşılan oran hafızası. */
    sizeMemory: MangaPageSizeMemory? = null,
    modifier: Modifier = Modifier,
) {
    val isScrollMode = readingMode == ReadingMode.Webtoon
    val haptics = LocalHapticFeedback.current
    val currentCenterTap by rememberUpdatedState(onCenterTap)
    val currentNextPage by rememberUpdatedState(onNextPage)
    val currentPrevPage by rememberUpdatedState(onPrevPage)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentLongPressPage by rememberUpdatedState(onLongPressPage)
    var itemTop by remember(page) { mutableFloatStateOf(0f) }
    val currentViewport by rememberUpdatedState(tapViewport)

    // Sayfa nesnesi ilk kez görüldüğünde yüklemeyi başlat.
    LaunchedEffect(page, loader) {
        val imageUrl = page.imageUrl
        val missing = page.status == MangaPageStatus.Ready && imageUrl != null &&
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                !loader.cache.getImageFile(loader.cacheKey(page, imageUrl)).isFile
            }
        if (missing) loader.retryPage(page) else loader.loadPage(page)
    }

    val rootModifier = if (isScrollMode) {
        modifier.fillMaxWidth().wrapContentHeight()
    } else {
        modifier.fillMaxSize()
    }

    val backgroundFile = if (autoBackground && page.status == MangaPageStatus.Ready) {
        loader.cache.getImageFile(page.imageUrl ?: page.url)
    } else null
    val actualBackground = rememberReaderAutoBackground(backgroundFile, autoBackground, pageBackground)

    // TEK dokunma yolu: Initial-pass gözlemci (readerTapGestures).
    // ZoomableAsyncImage Main pass'ta olayları tükettiği için detectTapGestures
    // zoom'lu sayfanın üstünde ölüyordu; bu gözlemci olayları çocuklardan ÖNCE
    // izler ve tüketmez → Default/Custom/LeftHand/RightHand düzenlerinin tümünde
    // dokunma, uzun basma ve zoom birlikte çalışır.
    val tapModifier = if (!tapsEnabled) {
        Modifier
    } else {
        Modifier
            .then(if (tapViewport != null) Modifier.onGloballyPositioned { itemTop = it.positionInWindow().y } else Modifier)
            .readerTapGestures(
                tapZonesEnabled = tapZonesEnabled,
                rtl = readingMode == ReadingMode.RightToLeft,
                invertTapZones = invertTapZones,
                tapLayout = tapLayout.ifBlank { "Default" },
                tapGrid = tapGrid,
                longTapGrid = longTapGrid,
                haptics = haptics,
                yFraction = { offset, height -> readerTapY(offset.y, itemTop, currentViewport, height) },
                onAction = { action ->
                    when (action) {
                        ReaderTapAction.MENU -> currentCenterTap()
                        ReaderTapAction.NEXT -> currentNextPage()
                        ReaderTapAction.PREVIOUS -> currentPrevPage()
                        ReaderTapAction.NONE -> Unit
                    }
                },
                onLongPress = {
                    currentLongPress()
                    currentLongPressPage?.invoke(page.index)
                },
            )
    }

    Box(
        modifier = rootModifier
            .background(actualBackground)
            .then(tapModifier),
        contentAlignment = Alignment.Center,
    ) {
        when (page.status) {
            MangaPageStatus.Ready -> {
                val imageUrl = page.imageUrl ?: page.url
                val cacheFile = page.cachedImageFile?.takeIf { it.exists() && it.length() > 0L }
                    ?: loader.cache.getImageFile(loader.cacheKey(page, imageUrl))
                val croppedFile = rememberReaderCrop(
                    source = cacheFile,
                    enabled = cropBorders && imageOverride == null,
                    rotation = if (imageOverride == null) wideRotation else "Off"
                )?.file
                val file: Any = imageOverride
                    ?: croppedFile
                    ?: page.localUri?.let(android.net.Uri::parse)
                    ?: if (cacheFile.exists()) cacheFile else (page.cachedFile ?: imageUrl)

                val context = LocalContext.current
                val displayError = remember(page, file) { mutableStateOf<Throwable?>(null) }
                val retryGeneration = remember(page, file) { mutableIntStateOf(0) }
                val imageRequest = remember(file, retryGeneration.intValue) {
                    ImageRequest.Builder(context)
                        .data(file)
                        .size(2048, 2048)
                        .scale(Scale.FIT)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .crossfade(200)
                        .listener(
                            onError = { _, result -> displayError.value = result.throwable },
                            onSuccess = { _, _ -> displayError.value = null }
                        )
                        .build()
                }

                val contentScale = fitMode.toContentScale()
                val composeColorFilter = remember(colorFilterType, saturation, contrast, hue) {
                    readerColorMatrix(colorFilterType.name, saturation, contrast, hue)?.let {
                        ColorFilter.colorMatrix(ColorMatrix(it))
                    } ?: mangaPageColorFilter(colorFilterType) ?: colorFilterType.toComposeColorFilter()
                }

                if (displayError.value == null) {
                    key(file, retryGeneration.intValue, zoomEnabled) {
                        val zoomableState = rememberZoomableState(
                            zoomSpec = ZoomSpec(maxZoomFactor = if (zoomEnabled) 2f else 1f)
                        )
                        val imageState = rememberZoomableImageState(zoomableState)
                        LaunchedEffect(resetZoomSignal) { if (resetZoomSignal > 0) zoomableState.resetZoom() }

                        val imageModifier = (if (isScrollMode) {
                            Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .onSizeChanged { size -> sizeMemory?.record(size.width, size.height) }
                        } else {
                            Modifier.fillMaxSize()
                        }).then(
                            if (mirrorPages) Modifier.graphicsLayer { scaleX = -1f } else Modifier
                        ).then(
                            if (cropBorders && croppedFile == null) {
                                Modifier
                                    .graphicsLayer {
                                        scaleX = 1.04f
                                        scaleY = 1.04f
                                    }
                                    .clipToBounds()
                            } else {
                                Modifier
                            }
                        )

                        ZoomableAsyncImage(
                            model = imageRequest,
                            contentDescription = stringResource(R.string.manga_sayfasi_x, page.index + 1),
                            state = imageState,
                            contentScale = contentScale,
                            colorFilter = composeColorFilter,
                            modifier = imageModifier,
                        )
                    }
                } else {
                    ErrorPageContent(
                        pageIndex = page.index,
                        isScrollMode = isScrollMode,
                        placeholderAspect = sizeMemory?.aspectRatio,
                        errorCode = page.errorCode,
                        onSourceLogin = onSourceLogin,
                        onRetry = {
                            displayError.value = null
                            retryGeneration.intValue++
                            loader.retryPage(page, invalidateCache = true)
                        }
                    )
                }
            }

            MangaPageStatus.Error -> {
                ErrorPageContent(
                    pageIndex = page.index,
                    isScrollMode = isScrollMode,
                    placeholderAspect = sizeMemory?.aspectRatio,
                    errorCode = page.errorCode,
                    onSourceLogin = onSourceLogin,
                    onRetry = { loader.retryPage(page, invalidateCache = true) },
                )
            }

            else -> {
                LoadingPageContent(
                    page = page,
                    isScrollMode = isScrollMode,
                    placeholderAspect = sizeMemory?.aspectRatio,
                )
            }
        }

        // Sayfa numarası rozeti (webtoon'da her sayfada göstermek gürültü yapar)
        if (showPageNumber && !isScrollMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${page.index + 1}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * Webtoon modunda yer tutucu boyutu için "son bilinen en-boy oranı" hafızası.
 */
class MangaPageSizeMemory {
    var aspectRatio by mutableStateOf(0.7f)
        private set

    fun record(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val ratio = widthPx.toFloat() / heightPx.toFloat()
        if (ratio.isFinite() && ratio > 0.08f && ratio < 4f) aspectRatio = ratio
    }
}

// ─── Ölçek / filtre eşlemeleri (tek yerden) ──────────────────────────────────

/** [MangaFitMode] → Compose [ContentScale]. */
internal fun MangaFitMode.toContentScale(): ContentScale = when (this) {
    MangaFitMode.FitScreen -> ContentScale.Fit
    MangaFitMode.FitWidth -> ContentScale.FillWidth
    MangaFitMode.FitHeight -> ContentScale.FillHeight
    MangaFitMode.Crop -> ContentScale.Crop
    MangaFitMode.Original -> ContentScale.None
}

/**
 * [ColorFilterType] → Compose [ColorFilter].
 */
internal fun ColorFilterType.toComposeColorFilter(): ColorFilter? = when (this) {
    ColorFilterType.Normal -> null

    ColorFilterType.Warm -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                1.06f, 0.02f, 0.00f, 0f, 6f,
                0.01f, 1.00f, 0.00f, 0f, 2f,
                0.00f, 0.00f, 0.90f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )

    ColorFilterType.Cool -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.92f, 0.00f, 0.00f, 0f, -4f,
                0.00f, 1.00f, 0.02f, 0f, 2f,
                0.00f, 0.03f, 1.08f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )

    ColorFilterType.Grayscale -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )

    ColorFilterType.Sepia -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )

    ColorFilterType.Invert -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

// ─── Yükleme / hata yer tutucuları ───────────────────────────────────────────

@Composable
private fun LoadingPageContent(
    page: MangaPage,
    isScrollMode: Boolean,
    placeholderAspect: Float?,
) {
    Box(
        modifier = if (isScrollMode) {
            Modifier.fillMaxWidth().aspectRatio(placeholderAspect ?: 0.7f)
        } else {
            Modifier.fillMaxSize()
        }.background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        when (page.status) {
            MangaPageStatus.DownloadImage,
            MangaPageStatus.LoadPage -> KitsugiPlasmaLoader(size = 46.dp)

            MangaPageStatus.Queue -> Icon(
                imageVector = Icons.HourglassBottom,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(40.dp),
            )

            else -> CircularProgressIndicator(
                color = KitsugiColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun ErrorPageContent(
    pageIndex: Int,
    isScrollMode: Boolean,
    placeholderAspect: Float?,
    errorCode: String? = null,
    onSourceLogin: (() -> Unit)? = null,
    onRetry: () -> Unit,
) {
    Box(
        modifier = if (isScrollMode) {
            Modifier.fillMaxWidth().aspectRatio(placeholderAspect ?: 0.7f)
        } else {
            Modifier.fillMaxSize()
        }.background(Color(0xFF120A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.BrokenImage,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(52.dp),
            )
            Text(
                text = stringResource(R.string.inline_page_1_s_couldn_t_11d924a, pageIndex + 1),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
            if (PlatformErrorCode.fromId(errorCode) == PlatformErrorCode.MangaAuth && onSourceLogin != null) {
                TextButton(onClick = onSourceLogin) {
                    Text(stringResource(R.string.manga_source_login_action), color = Color(0xFFEF5350))
                }
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.manga_reader_retry), color = KitsugiColors.Accent)
            }
        }
    }
}

fun readerBackgroundColor(value: String): Color = when (value) {
    "White" -> Color.White
    "DarkGray" -> Color(0xFF303030)
    else -> Color.Black
}
