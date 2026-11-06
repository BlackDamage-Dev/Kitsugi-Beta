package com.kitsugi.animelist.ui.screens.manga.components

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.HourglassBottom
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Yatay (LTR/RTL) ve dikey pager okuma modları için tek sayfa bileşeni.
 *
 * — Telephoto'nun ZoomableAsyncImage bileşeni ile:
 *     • Çift tıklama ile yakınlaştırma/uzaklaştırma
 *     • Parmakla pinch-to-zoom
 *     • Büyük çözünürlüklü resimlerin kiremit döşemeli (subsampling) çizimi
 *     • OOM (Out of Memory) riskini sıfıra indirme
 *
 * — Sayfa Ready olana kadar yükleme göstergesi gösterilir.
 * — Hata durumunda "Tekrar Dene" butonu görünür.
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
    showPageNumber: Boolean = true,
    invertTapZones: Boolean = false,
    tapLayout: String = "Default",
    tapGrid: String = DEFAULT_READER_TAP_GRID,
    longTapGrid: String = DEFAULT_READER_LONG_TAP_GRID,
    tapViewport: ReaderTapViewport? = null,
    cropBorders: Boolean = false,
    zoomEnabled: Boolean = true,
    wideRotation: String = "Off",
    imageOverride: java.io.File? = null,
    tapsEnabled: Boolean = true,
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentCenterTap by rememberUpdatedState(onCenterTap)
    val currentNextPage by rememberUpdatedState(onNextPage)
    val currentPrevPage by rememberUpdatedState(onPrevPage)
    var itemTop by remember(page) { mutableFloatStateOf(0f) }
    val currentViewport by rememberUpdatedState(tapViewport)

    // Sayfa nesnesi ilk kez görüldüğünde yüklemeyi başlat.
    // Key olarak identityHashCode kullanıyoruz: aynı sayfa nesnesi varsa yeniden tetiklenmez,
    // ama yeni bir bölüme geçildiğinde (yeni page nesneleri) effect yeniden çalışır.
    LaunchedEffect(System.identityHashCode(page)) {
        loader.loadPage(page)
    }

    val backgroundFile = if (autoBackground && page.status == MangaPageStatus.Ready) loader.cache.getImageFile(page.imageUrl ?: page.url) else null
    val actualBackground = rememberReaderAutoBackground(backgroundFile, autoBackground, pageBackground)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(actualBackground)
            .then(if (tapLayout != "Default" && tapViewport != null) Modifier.onGloballyPositioned { itemTop = it.positionInWindow().y } else Modifier)
            .then(if (tapsEnabled) Modifier.pointerInput(readingMode, invertTapZones, tapLayout, tapGrid, longTapGrid) {
                fun dispatch(offset: androidx.compose.ui.geometry.Offset, grid: String) {
                    val y = readerTapY(offset.y, itemTop, currentViewport, size.height)
                    when (readerTapAction(tapLayout, readingMode == ReadingMode.RightToLeft, invertTapZones,
                        offset.x / size.width.coerceAtLeast(1), y, grid)) {
                        ReaderTapAction.MENU -> currentCenterTap()
                        ReaderTapAction.NEXT -> currentNextPage()
                        ReaderTapAction.PREVIOUS -> currentPrevPage()
                        ReaderTapAction.NONE -> Unit
                    }
                }
                detectTapGestures(onTap = { dispatch(it, tapGrid) },
                    onLongPress = if (tapLayout == "Custom" && longTapGrid.any { it != 'X' }) ({ dispatch(it, longTapGrid) }) else null)
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when (page.status) {
            MangaPageStatus.Ready -> {
                val imageUrl = page.imageUrl ?: page.url
                val cacheFile = loader.cache.getImageFile(imageUrl)
                val context = LocalContext.current
                val croppedFile = rememberReaderCrop(cacheFile, cropBorders && imageOverride == null, if (imageOverride == null) wideRotation else "Off")?.file
                val modelToLoad: Any = imageOverride ?: croppedFile ?: if (cacheFile.exists()) cacheFile else imageUrl
                val imageRequest = remember(modelToLoad) {
                    ImageRequest.Builder(context)
                        .data(modelToLoad)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(200)
                        .build()
                }

                val contentScale = when (fitMode) {
                    MangaFitMode.FitScreen -> ContentScale.Fit
                    MangaFitMode.FitWidth -> ContentScale.FillWidth
                    MangaFitMode.FitHeight -> ContentScale.FillHeight
                }

                val composeColorFilter = remember(colorFilterType, saturation, contrast, hue) {
                    readerColorMatrix(colorFilterType.name, saturation, contrast, hue)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
                }

                androidx.compose.runtime.key(zoomEnabled) {
                    val zoomableState = rememberZoomableState(
                        zoomSpec = me.saket.telephoto.zoomable.ZoomSpec(maxZoomFactor = if (zoomEnabled) 2f else 1f)
                    )
                    val imageState = rememberZoomableImageState(zoomableState)
                    ZoomableAsyncImage(
                        model       = imageRequest,
                        contentDescription = stringResource(R.string.manga_sayfasi_x, page.index + 1),
                        state       = imageState,
                        contentScale = contentScale,
                        colorFilter = composeColorFilter,
                        modifier    = Modifier.fillMaxSize()
                    )
                }
            }

            MangaPageStatus.Error -> {
                ErrorPageContent(
                    pageIndex = page.index,
                    onRetry   = { loader.retryPage(page) }
                )
            }

            else -> {
                LoadingPageContent(page = page)
            }
        }

        // Sol alt köşede sayfa numarası
        if (showPageNumber) Text(
            text     = "${page.index + 1}",
            color    = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun LoadingPageContent(page: MangaPage) {
    Box(
        modifier          = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .background(Color(0xFF0A0A0A)),
        contentAlignment  = Alignment.Center
    ) {
        when (page.status) {
            MangaPageStatus.DownloadImage,
            MangaPageStatus.LoadPage -> {
                KitsugiPlasmaLoader(size = 46.dp)
            }
            MangaPageStatus.Queue -> {
                Icon(
                    imageVector         = Icons.Default.HourglassBottom,
                    contentDescription  = null,
                    tint                = Color.White.copy(alpha = 0.3f),
                    modifier            = Modifier.size(40.dp)
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun ErrorPageContent(pageIndex: Int, onRetry: () -> Unit) {
    Box(
        modifier          = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(Color(0xFF120A0A)),
        contentAlignment  = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector        = Icons.Default.BrokenImage,
                contentDescription = null,
                tint               = Color(0xFFEF5350),
                modifier           = Modifier.size(52.dp)
            )
            Text(
                text      = stringResource(R.string.inline_page_1_s_couldn_t_11d924a, pageIndex + 1),
                color     = Color.White.copy(alpha = 0.7f),
                fontSize  = 13.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(top = 8.dp)
            )
            TextButton(onClick = onRetry) {
                Text("Tekrar Dene", color = Color(0xFF7C4DFF))
            }
        }
    }
}


fun readerBackgroundColor(value: String): Color = when (value) {
    "White" -> Color.White
    "DarkGray" -> Color(0xFF303030)
    else -> Color.Black
}
