package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics. ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.MangaWebtoonPreferences
import com.kitsugi.animelist.data.manga.loader.MangaImageTileDecoder
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.screens.manga.reader.MangaWebtoonState
import com.kitsugi.animelist.data.manga.WebtoonLayout
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlin.math.abs

/**
 * A width-fitted, seamless strip. Lazy tiles are drawing units, NOT independently zoomable or
 * scrollable images. Long images are decoded by region and never measured as gigantic children.
 * A single viewport transform scales the list (whose logical height shrinks accordingly), so the
 * last pixel remains reachable even when zoomed. Default side padding and inter-page gap are zero.
 */
@Composable
fun MangaWebtoonReader(
    pages: List<MangaPage>,
    state: MangaWebtoonState,
    loader: MangaPageLoaderV2,
    preferences: MangaWebtoonPreferences = MangaWebtoonPreferences(),
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    saturation: Int = 100,
    contrast: Int = 100,
    hue: Int = 0,
    zoomEnabled: Boolean = true,
    backgroundColor: Color = Color.Black,
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    onLongPressPage: ((Int) -> Unit)? = null,
    onInteraction: () -> Unit = {},
    hasNextChapter: Boolean = false,
    nextChapterName: String? = null,
    nextChapterVariantCount: Int = 0,
    onGoToNextChapter: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val decoder = remember(pages) { MangaImageTileDecoder() }
    DisposableEffect(decoder) { onDispose { decoder.clear() } }
    val colorFilter = remember(colorFilterType, saturation, contrast, hue) {
        if (saturation != 100 || contrast != 100 || hue != 0) {
            readerColorMatrix(colorFilterType.name, saturation, contrast, hue)?.let {
                ColorFilter.colorMatrix(ColorMatrix(it))
            } ?: mangaPageColorFilter(colorFilterType)
        } else {
            mangaPageColorFilter(colorFilterType)
        }
    }
    val density = LocalDensity.current
    val tap by rememberUpdatedState(onCenterTap)
    val forward by rememberUpdatedState(onNextPage)
    val backward by rememberUpdatedState(onPrevPage)
    val longPressPage by rememberUpdatedState(onLongPressPage)
    val interaction by rememberUpdatedState(onInteraction)
    val tapToScroll by rememberUpdatedState(preferences.tapToScroll)
    val allowZoom by rememberUpdatedState(zoomEnabled)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().clipToBounds().background(backgroundColor)
            .testTag("webtoon_strip")
            .onSizeChanged { state.viewport = it; state.clampPan() }
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    interaction()
                    var horizontal = false
                    var transformed = false
                    var accumulated = Offset.Zero
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val count = event.changes.count { it.pressed }
                            val pan = event.calculatePan()
                            accumulated += pan
                            if (count > 1 && allowZoom) {
                                transformed = true
                                state.isTransforming = true
                                state.transform(event.calculateZoom(), event.calculateCentroid(useCurrent = false), pan)
                                event.changes.forEach { it.consume() }
                            } else if (transformed) {
                                // A remaining finger cannot accidentally fling a page after a pinch.
                                event.changes.forEach { it.consume() }
                            } else if (state.zoom > 1f) {
                                if (abs(accumulated.x) > viewConfiguration.touchSlop && abs(accumulated.x) > abs(accumulated.y)) {
                                    horizontal = true
                                    state.isTransforming = true
                                }
                                if (horizontal) {
                                    state.panBy(pan.x)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        state.isTransforming = false
                    }
                }
            }
            .pointerInput(state) {
                detectTapGestures(
                    onDoubleTap = { point ->
                        interaction()
                        if (allowZoom) state.transform(if (state.zoom > 1f) 1f / state.zoom else 2f, point)
                    },
                    onLongPress = {
                        interaction()
                        longPressPage?.invoke(state.position().pageIndex)
                    },
                    onTap = { point ->
                        when {
                            tapToScroll && point.y < size.height * .25f -> backward()
                            tapToScroll && point.y > size.height * .75f -> forward()
                            else -> tap()
                        }
                    },
                )
            },
    ) {
        val widthPx = with(density) { (maxWidth - (preferences.sidePaddingDp * 2).dp).roundToPx() }.coerceAtLeast(1)
        SideEffect { state.contentWidthPx = widthPx }
        LazyColumn(
            state = state.listState,
            userScrollEnabled = !state.isTransforming,
            contentPadding = PaddingValues(horizontal = preferences.sidePaddingDp.dp),
            modifier = Modifier.fillMaxWidth().requiredHeight(maxHeight / state.zoom)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    scaleX = state.zoom
                    scaleY = state.zoom
                    translationX = state.panX
                    transformOrigin = TransformOrigin(.5f, 0f)
                },
        ) {
            items(state.layout.tiles, key = { it.key }, contentType = { "image_strip" }) { tile ->
                val page = pages.getOrNull(tile.pageIndex) ?: return@items
                LaunchedEffect(page) { loader.loadPage(page) }
                key(page) {
                    Column(Modifier.fillMaxWidth()) {
                        WebtoonTile(
                            page, tile, widthPx, (widthPx * state.zoom).toInt(), decoder,
                            colorFilter, loader, backgroundColor,
                        )
                        // No hardcoded spacer, border, page number, shadow or fit-height box.
                        if (tile.isLast && tile.pageIndex < pages.lastIndex && preferences.pageGapDp > 0) {
                            Spacer(Modifier.height(preferences.pageGapDp.dp))
                        }
                    }
                }
            }
            item(key = "chapter_transition_footer", contentType = "chapter_footer") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val foreground = if (backgroundColor == Color.White) Color.Black else Color.White
                    Text(stringResource(R.string.reader_chapter_end), color = foreground.copy(alpha = .7f))
                    if (hasNextChapter) {
                        KitsugiButton(
                            onClick = onGoToNextChapter,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                        ) {
                            Text(nextChapterName ?: stringResource(R.string.sonraki_bolum), color = Color.White)
                        }
                        if (nextChapterVariantCount > 1) {
                            Text(
                                text = stringResource(R.string.manga_next_chapter_multi, nextChapterVariantCount),
                                color = foreground.copy(alpha = .55f),
                                fontSize = 11.sp,
                            )
                        }
                    } else {
                        Text(stringResource(R.string.inline_you_re_on_the_last_4e9fc31), color = foreground.copy(alpha = .5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WebtoonTile(
    page: MangaPage,
    tile: WebtoonLayout.Tile,
    widthPx: Int,
    decodeWidthPx: Int,
    decoder: MangaImageTileDecoder,
    colorFilter: ColorFilter?,
    loader: MangaPageLoaderV2,
    background: Color,
) {
    val density = LocalDensity.current
    val file = page.cachedImageFile ?: page.cachedFile
    val status = page.status
    var attempt by remember(page, tile.key) { mutableIntStateOf(0) }
    // Restart only when the required sampling level changes, not at every pinch pixel.
    val sample = tile.image?.sourceRegion(tile.top, tile.bottom)?.let {
        WebtoonLayout.sampleSize(it.width, it.height, decodeWidthPx)
    } ?: 0
    val result by produceState<Result<android.graphics.Bitmap>?>(null, file, status, tile, sample, attempt) {
        if (status != MangaPageStatus.Ready || file == null) value = null
        if (status == MangaPageStatus.Ready && tile.image != null && file != null) {
            value = try {
                Result.success(decoder.decode(file, tile, decodeWidthPx))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }
    Box(
        Modifier.fillMaxWidth()
            .height(with(density) { tile.heightPx(widthPx).toDp() })
            .background(background).testTag("webtoon_tile_${tile.key}"),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = result?.getOrNull()
        when {
            bitmap != null -> Image(
                bitmap.asImageBitmap(),
                contentDescription = if (tile.part == 0) stringResource(R.string.manga_sayfasi_x, tile.pageIndex + 1) else null,
                contentScale = ContentScale.FillBounds,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
            )
            status == MangaPageStatus.Error || result?.isFailure == true -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.inline_page_1_s_couldn_t_11d924a, tile.pageIndex + 1), color = Color(0xFFEF5350))
                TextButton(onClick = {
                    attempt++
                    if (status == MangaPageStatus.Error || file?.exists() != true) loader.retryPage(page)
                }) { Text(stringResource(R.string.reader_retry), color = Color(0xFF9D7BFF)) }
            }
            else -> KitsugiPlasmaLoader(size = 36.dp)
        }
    }
}

/** Bounds invalid and extreme dimensions before passing them to Compose layout. */
internal fun webtoonAspectRatio(width: Int, height: Int): Float? =
    if (width <= 0 || height <= 0) null else (width.toFloat() / height).coerceIn(0.02f, 100f)
