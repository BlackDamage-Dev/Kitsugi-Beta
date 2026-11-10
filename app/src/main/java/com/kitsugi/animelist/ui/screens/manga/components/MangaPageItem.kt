package com.kitsugi.animelist.ui.screens.manga.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.request.transformations
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.MangaReaderOptions
import com.kitsugi.animelist.data.manga.MangaTapNavigation
import com.kitsugi.animelist.data.manga.MangaZoomStart
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.WhiteBorderCropTransformation
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
fun MangaPageItem(
    page: MangaPage,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode = ReadingMode.RightToLeft,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    readerOptions: MangaReaderOptions = MangaReaderOptions(),
    totalPages: Int = 1,
    tapNavigationEnabled: Boolean = true,
    allowWideRotation: Boolean = true,
    tapHorizontalOrigin: Float = 0f,
    tapHorizontalSpan: Float = 1f,
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(page) { loader.loadPage(page) }

    page.textParagraphs?.let { paragraphs ->
        MangaNovelPageItem(
            paragraphs = paragraphs,
            pageIndex = page.index,
            totalPages = totalPages,
            options = readerOptions,
            onCenterTap = onCenterTap,
            modifier = modifier,
        )
        return
    }

    val usesZoomableGestures = page.status == MangaPageStatus.Ready &&
        (readingMode != ReadingMode.Webtoon || readerOptions.webtoonZoom)
    val tapModifier = if (tapNavigationEnabled && !usesZoomableGestures && readerOptions.tapNavigation != MangaTapNavigation.Disabled) {
        Modifier.readerTapNavigation(readingMode, readerOptions.tapNavigation, onCenterTap, onNextPage, onPrevPage)
    } else {
        Modifier
    }
    val background = if (readerOptions.bookEffect) Color(0xFF17130E) else Color.Black
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val longTapModifier = if (readerOptions.longTapNavigation) {
        Modifier.pointerInput(page.url, readerOptions.longTapNavigation) {
            detectTapGestures(onLongPress = {
                if (zoomableState.zoomState.zoomFraction == null || zoomableState.zoomState.zoomFraction == 0f) onNextPage()
            })
        }
    } else Modifier

    Box(
        modifier = modifier.fillMaxSize().background(background).onSizeChanged { viewportSize = it }.then(longTapModifier).then(tapModifier),
        contentAlignment = Alignment.Center,
    ) {
        when (page.status) {
            MangaPageStatus.Ready -> {
                val imageUrl = page.imageUrl ?: page.url
                val cacheFile = loader.cache.getImageFile(imageUrl)
                val modelToLoad: Any = if (cacheFile.exists()) cacheFile else imageUrl
                val context = LocalContext.current
                var isWide by remember(modelToLoad) { mutableStateOf(false) }
                val imageRequest = remember(modelToLoad, readerOptions.cropBorders, readerOptions.highColorMode) {
                    ImageRequest.Builder(context)
                        .data(modelToLoad)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .bitmapConfig(if (readerOptions.highColorMode) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
                        .crossfade(200)
                        .listener(onSuccess = { _, result -> isWide = result.image.width > result.image.height })
                        .apply {
                            if (readerOptions.cropBorders) transformations(WhiteBorderCropTransformation())
                        }
                        .build()
                }
                val contentScale = if (readingMode == ReadingMode.Webtoon) ContentScale.FillWidth else when (fitMode) {
                    MangaFitMode.FitScreen -> ContentScale.Fit
                    MangaFitMode.FitWidth -> ContentScale.FillWidth
                    MangaFitMode.FitHeight -> ContentScale.FillHeight
                }
                val colorFilter = remember(colorFilterType, readerOptions.bookEffect) {
                    mangaColorFilter(colorFilterType, readerOptions.bookEffect)
                }
                val shouldRotate = readerOptions.rotateWidePages && allowWideRotation && isWide && readingMode != ReadingMode.Webtoon
                val imageModifier = Modifier.graphicsLayer { alpha = readerOptions.pageOpacity }

                BoxWithConstraints(modifier = if (readingMode == ReadingMode.Webtoon) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
                    val sizedModifier = when {
                        shouldRotate -> imageModifier.requiredSize(maxHeight, maxWidth).graphicsLayer { rotationZ = 90f }
                        readingMode == ReadingMode.Webtoon -> imageModifier.fillMaxWidth()
                        else -> imageModifier.fillMaxSize()
                    }
                    if (readingMode == ReadingMode.Webtoon && !readerOptions.webtoonZoom) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = stringResource(R.string.manga_sayfasi_x, page.index + 1),
                            contentScale = contentScale,
                            colorFilter = colorFilter,
                            modifier = sizedModifier,
                        )
                    } else {
                        val zoomableState = rememberZoomableState()
                        val imageState = rememberZoomableImageState(zoomableState)
                        LaunchedEffect(page, readerOptions.zoomStart) {
                            zoomableState.resetZoom(withAnimation = false)
                        }
                        ZoomableAsyncImage(
                            model = imageRequest,
                            contentDescription = stringResource(R.string.manga_sayfasi_x, page.index + 1),
                            state = imageState,
                            contentScale = contentScale,
                            alignment = when (readerOptions.zoomStart) {
                                MangaZoomStart.Left -> Alignment.CenterStart
                                MangaZoomStart.Right -> Alignment.CenterEnd
                                MangaZoomStart.Center -> Alignment.Center
                                MangaZoomStart.Automatic -> if (readingMode == ReadingMode.RightToLeft) {
                                    Alignment.CenterEnd
                                } else Alignment.CenterStart
                            },
                            colorFilter = colorFilter,
                            onClick = if (tapNavigationEnabled && readerOptions.tapNavigation != MangaTapNavigation.Disabled) {
                                { offset ->
                                    handleReaderTap(
                                        offset = offset,
                                        viewportSize = viewportSize,
                                        readingMode = readingMode,
                                        navigation = readerOptions.tapNavigation,
                                        horizontalOrigin = tapHorizontalOrigin,
                                        horizontalSpan = tapHorizontalSpan,
                                        onCenterTap = onCenterTap,
                                        onNextPage = onNextPage,
                                        onPrevPage = onPrevPage,
                                    )
                                }
                            } else null,
                            modifier = sizedModifier,
                        )
                    }
                }
            }
            MangaPageStatus.Error -> ErrorPageContent(page.index) { loader.retryPage(page) }
            else -> LoadingPageContent(page)
        }

        if (readerOptions.showPageNumbers) {
            Text(
                text = "${page.index + 1}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

private fun handleReaderTap(
    offset: Offset,
    viewportSize: IntSize,
    readingMode: ReadingMode,
    navigation: MangaTapNavigation,
    horizontalOrigin: Float,
    horizontalSpan: Float,
    onCenterTap: () -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
) {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return
    val x = horizontalOrigin + (offset.x / viewportSize.width) * horizontalSpan
    val y = offset.y / viewportSize.height
    val inverted = navigation == MangaTapNavigation.Inverted
    val primaryNext = if (readingMode == ReadingMode.Vertical) {
        y > 0.66f
    } else if (readingMode == ReadingMode.RightToLeft) {
        x < 0.33f
    } else {
        x > 0.66f
    }
    val secondaryPrevious = if (readingMode == ReadingMode.Vertical) {
        y < 0.33f
    } else if (readingMode == ReadingMode.RightToLeft) {
        x > 0.66f
    } else {
        x < 0.33f
    }
    when {
        primaryNext -> if (inverted) onPrevPage() else onNextPage()
        secondaryPrevious -> if (inverted) onNextPage() else onPrevPage()
        else -> onCenterTap()
    }
}

private fun Modifier.readerTapNavigation(
    readingMode: ReadingMode,
    navigation: MangaTapNavigation,
    onCenterTap: () -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
): Modifier = pointerInput(readingMode, navigation) {
    detectTapGestures { offset ->
        val inverted = navigation == MangaTapNavigation.Inverted
        val primaryNext = if (readingMode == ReadingMode.Vertical) {
            offset.y > size.height * 0.66f
        } else if (readingMode == ReadingMode.RightToLeft) {
            offset.x < size.width * 0.33f
        } else {
            offset.x > size.width * 0.66f
        }
        val secondaryPrevious = if (readingMode == ReadingMode.Vertical) {
            offset.y < size.height * 0.33f
        } else if (readingMode == ReadingMode.RightToLeft) {
            offset.x > size.width * 0.66f
        } else {
            offset.x < size.width * 0.33f
        }
        when {
            primaryNext -> if (inverted) onPrevPage() else onNextPage()
            secondaryPrevious -> if (inverted) onNextPage() else onPrevPage()
            else -> onCenterTap()
        }
    }
}

private fun mangaColorFilter(type: ColorFilterType, bookEffect: Boolean): ColorFilter? {
    if (type == ColorFilterType.Normal && !bookEffect) return null
    val values = when (type) {
        ColorFilterType.Normal -> floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        ColorFilterType.Grayscale -> floatArrayOf(
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        ColorFilterType.Sepia -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        ColorFilterType.Invert -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
    if (bookEffect) {
        values[4] += 10f
        values[9] += 3f
        values[14] -= 12f
    }
    return ColorFilter.colorMatrix(ColorMatrix(values))
}

@Composable
private fun LoadingPageContent(page: MangaPage) {
    Box(
        modifier = Modifier.fillMaxWidth().height(480.dp).background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        when (page.status) {
            MangaPageStatus.DownloadImage, MangaPageStatus.LoadPage -> KitsugiPlasmaLoader(size = 46.dp)
            MangaPageStatus.Queue -> Icon(
                imageVector = Icons.Default.HourglassBottom,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(40.dp),
            )
            else -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun ErrorPageContent(pageIndex: Int, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(360.dp).background(Color(0xFF120A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(52.dp),
            )
            Text(
                text = stringResource(R.string.inline_page_1_s_couldn_t_11d924a, pageIndex + 1),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onRetry) { Text("Tekrar Dene", color = Color(0xFF7C4DFF)) }
        }
    }
}
