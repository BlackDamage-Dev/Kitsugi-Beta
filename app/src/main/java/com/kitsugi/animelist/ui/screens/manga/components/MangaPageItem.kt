package com.kitsugi.animelist.ui.screens.manga.components

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.size.Scale
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
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Sayfa nesnesi ilk kez görüldüğünde yüklemeyi başlat.
    // Key olarak identityHashCode kullanıyoruz: aynı sayfa nesnesi varsa yeniden tetiklenmez,
    // ama yeni bir bölüme geçildiğinde (yeni page nesneleri) effect yeniden çalışır.
    LaunchedEffect(page, loader) {
        // Cache maintenance may have evicted a page which was already marked Ready.
        val imageUrl = page.imageUrl
        val missing = page.status == MangaPageStatus.Ready && imageUrl != null &&
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                !loader.cache.getImageFile(loader.cacheKey(page, imageUrl)).isFile
            }
        if (missing) loader.retryPage(page) else loader.loadPage(page)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(readingMode) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val leftEdge = width * 0.33f
                    val rightEdge = width * 0.66f

                    if (offset.x < leftEdge) {
                        if (readingMode == ReadingMode.RightToLeft) {
                            onNextPage()
                        } else {
                            onPrevPage()
                        }
                    } else if (offset.x > rightEdge) {
                        if (readingMode == ReadingMode.RightToLeft) {
                            onPrevPage()
                        } else {
                            onNextPage()
                        }
                    } else {
                        onCenterTap()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (page.status) {
            MangaPageStatus.Ready -> {
                val imageUrl = page.imageUrl ?: page.url
                val cacheFile = loader.cache.getImageFile(loader.cacheKey(page, imageUrl))
                // Never bypass the extension client for a cache miss (headers/cookies may be required).
                val modelToLoad: Any = cacheFile

                val context = LocalContext.current
                val displayError = remember(page, modelToLoad) { mutableStateOf<Throwable?>(null) }
                val retryGeneration = remember(page, modelToLoad) { mutableIntStateOf(0) }
                val imageRequest = remember(modelToLoad, retryGeneration.intValue) {
                    ImageRequest.Builder(context)
                        .data(modelToLoad)
                        // Telephoto's subsampling supplies detail for supported formats. Keep
                        // Coil's preview bounded for very tall pages and older GPU canvases.
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

                val contentScale = when (fitMode) {
                    MangaFitMode.FitScreen -> ContentScale.Fit
                    MangaFitMode.FitWidth -> ContentScale.FillWidth
                    MangaFitMode.FitHeight -> ContentScale.FillHeight
                }

                val composeColorFilter = when (colorFilterType) {
                    ColorFilterType.Normal -> null
                    ColorFilterType.Grayscale -> {
                        val matrix = ColorMatrix(
                            floatArrayOf(
                                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                                0f,      0f,      0f,      1f, 0f
                            )
                        )
                        ColorFilter.colorMatrix(matrix)
                    }
                    ColorFilterType.Sepia -> {
                        val matrix = ColorMatrix(
                            floatArrayOf(
                                0.393f, 0.769f, 0.189f, 0f, 0f,
                                0.349f, 0.686f, 0.168f, 0f, 0f,
                                0.272f, 0.534f, 0.131f, 0f, 0f,
                                0f,     0f,     0f,     1f, 0f
                            )
                        )
                        ColorFilter.colorMatrix(matrix)
                    }
                    ColorFilterType.Invert -> {
                        val matrix = ColorMatrix(
                            floatArrayOf(
                                -1f,  0f,  0f, 0f, 1f,
                                 0f, -1f,  0f, 0f, 1f,
                                 0f,  0f, -1f, 0f, 1f,
                                 0f,  0f,  0f, 1f, 0f
                            )
                        )
                        ColorFilter.colorMatrix(matrix)
                    }
                }

                if (displayError.value == null) {
                    key(modelToLoad, retryGeneration.intValue) {
                        val zoomableState = rememberZoomableState()
                        val imageState = rememberZoomableImageState(zoomableState)
                        ZoomableAsyncImage(
                            model = imageRequest,
                            contentDescription = stringResource(R.string.manga_sayfasi_x, page.index + 1),
                            state = imageState,
                            contentScale = contentScale,
                            colorFilter = composeColorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    ErrorPageContent(pageIndex = page.index, onRetry = {
                        displayError.value = null
                        retryGeneration.intValue++
                        loader.retryPage(page, invalidateCache = true)
                    })
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
        Text(
            text     = "${page.index + 1}",
            color    = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
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
                    imageVector         = Icons.HourglassBottom,
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
                imageVector        = Icons.BrokenImage,
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
