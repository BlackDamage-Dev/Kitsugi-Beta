package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2

/**
 * Yatay sayfa çevirmeli okuma modu (LTR / RTL / Dikey Pager).
 *
 * — LTR (Soldan Sağa): Manhua, Batı çizgi romanları için
 * — RTL (Sağdan Sola): Japonca Manga için
 *
 * RTL modu, sayfaları tersine çevirerek HorizontalPager'ı
 * sağdan sola kaydıracak şekilde yapılandırır.
 *
 * Her sayfa MangaPageItem bileşeni ile çizilir:
 *   • Telephoto ZoomableAsyncImage → pinch-to-zoom, double-tap
 *   • MangaPageLoaderV2 ile 3 ileri prefetch + Coil cache
 */
@Composable
fun MangaPagerReader(
    pages: List<MangaPage>,
    pagerState: PagerState,
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
    doublePage: Boolean = false,
    coverAlone: Boolean = false,
    prefetchPages: Int = 2,
    cropBorders: Boolean = false,
    wideRotation: String = "Off",
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isRtl       = readingMode == ReadingMode.RightToLeft
    val direction   = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    val centerTap by rememberUpdatedState(onCenterTap)
    val nextPage by rememberUpdatedState(onNextPage)
    val prevPage by rememberUpdatedState(onPrevPage)
    val pageContent: @Composable (Int) -> Unit = { spreadIndex ->
        val firstIndex = readerSpreadFirst(spreadIndex, doublePage, coverAlone)
        val firstPage = pages.getOrNull(firstIndex)
        val lastIndex = readerSpreadLast(spreadIndex, pages.size, doublePage, coverAlone)
        val secondPage = if (lastIndex > firstIndex) pages.getOrNull(lastIndex) else null
        if (firstPage != null) {
            if (secondPage == null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    MangaPageItem(
                        page = firstPage, loader = loader, readingMode = readingMode,
                        colorFilterType = colorFilterType, saturation = saturation, contrast = contrast, hue = hue, fitMode = fitMode, pageBackground = pageBackground, autoBackground = autoBackground,
                        showPageNumber = showPageNumber, invertTapZones = invertTapZones, tapLayout = tapLayout, tapGrid = tapGrid, longTapGrid = longTapGrid, cropBorders = cropBorders, wideRotation = wideRotation,
                        onCenterTap = onCenterTap, onNextPage = onNextPage, onPrevPage = onPrevPage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // One set of tap zones spans the entire spread, independent of page order.
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Row(Modifier.fillMaxSize().pointerInput(readingMode, invertTapZones, tapLayout, tapGrid, longTapGrid) {
                        fun dispatch(offset: androidx.compose.ui.geometry.Offset, grid: String) {
                            when (readerTapAction(tapLayout, isRtl, invertTapZones,
                                offset.x / size.width.coerceAtLeast(1), offset.y / size.height.coerceAtLeast(1), grid)) {
                                ReaderTapAction.MENU -> centerTap()
                                ReaderTapAction.NEXT -> nextPage()
                                ReaderTapAction.PREVIOUS -> prevPage()
                                ReaderTapAction.NONE -> Unit
                            }
                        }
                        detectTapGestures(onTap = { dispatch(it, tapGrid) },
                            onLongPress = if (tapLayout == "Custom" && longTapGrid.any { it != 'X' }) ({ dispatch(it, longTapGrid) }) else null)
                    }) {
                        listOf(firstPage, secondPage).forEach { page ->
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                MangaPageItem(
                                    page = page, loader = loader, readingMode = readingMode,
                                    colorFilterType = colorFilterType, saturation = saturation, contrast = contrast, hue = hue, fitMode = fitMode, pageBackground = pageBackground, autoBackground = autoBackground,
                                    showPageNumber = showPageNumber, invertTapZones = invertTapZones, tapLayout = tapLayout, tapGrid = tapGrid, longTapGrid = longTapGrid, cropBorders = cropBorders, wideRotation = wideRotation, tapsEnabled = false,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        if (readingMode == ReadingMode.Vertical) {
            VerticalPager(
                state = pagerState,
                modifier = modifier.fillMaxSize(),
                key = { index -> pages.getOrNull(readerSpreadFirst(index, doublePage, coverAlone))?.index ?: index },
                beyondViewportPageCount = prefetchPages.coerceIn(0, 2)
            ) { pageContent(it) }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = modifier.fillMaxSize(),
                key = { index -> pages.getOrNull(readerSpreadFirst(index, doublePage, coverAlone))?.index ?: index },
                beyondViewportPageCount = prefetchPages.coerceIn(0, 2)
            ) { pageContent(it) }
        }
    }
}
