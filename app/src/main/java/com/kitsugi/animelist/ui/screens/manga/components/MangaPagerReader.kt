package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Yatay sayfa çevirmeli okuma modu (LTR / RTL) ve dikey pager.
 *
 * — LTR (Soldan Sağa): Manhua, Batı çizgi romanları için
 * — RTL (Sağdan Sola): Japonca manga için (pager `LayoutDirection.Rtl` ile
 *   çizilir, böylece sayfa 1 sağda başlar)
 *
 * Her sayfa [MangaPageItem] bileşeni ile çizilir.
 * Çift sayfa modu (`doublePage`) açıkken iki sayfa yan yana birleşik yayılım olarak gösterilir.
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
    cropBorders: Boolean = false,
    mirrorPages: Boolean = false,
    showPageNumber: Boolean = true,
    tapZonesEnabled: Boolean = true,
    invertTapZones: Boolean = false,
    tapLayout: String = "Default",
    tapGrid: String = DEFAULT_READER_TAP_GRID,
    longTapGrid: String = DEFAULT_READER_LONG_TAP_GRID,
    doublePage: Boolean = false,
    coverAlone: Boolean = false,
    stitchDualPages: Boolean = true,
    prefetchPages: Int = 2,
    resetZoomSignal: Int = 0,
    wideRotation: String = "Off",
    onCenterTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    /** Uzun basınca galeriyi aç (sayfa indeksi ile). */
    onLongPressPage: ((Int) -> Unit)? = null,
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    onSourceLogin: (() -> Unit)? = null,
    onPageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isRtl = readingMode == ReadingMode.RightToLeft
    val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val reportPageChanged by rememberUpdatedState(onPageChanged)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { index -> reportPageChanged(index) }
    }

    val centerTap by rememberUpdatedState(onCenterTap)
    val nextPage by rememberUpdatedState(onNextPage)
    val prevPage by rememberUpdatedState(onPrevPage)

    val spreadPages = remember(pages) {
        val opts = com.kitsugi.animelist.data.manga.MangaReaderOptions()
        pages.filter { page ->
            page.aspectRatio?.let { com.kitsugi.animelist.ui.screens.manga.MangaReaderPaging.isSpread(it, opts) } == true
        }.map { it.index }.toSet()
    }
    val pageGroups = remember(pages.size, doublePage, coverAlone, spreadPages) {
        com.kitsugi.animelist.ui.screens.manga.MangaReaderPaging.groups(
            pages.size,
            com.kitsugi.animelist.data.manga.MangaReaderOptions(dualPage = doublePage, firstPageSingle = coverAlone),
            false,
            spreadPages
        )
    }

    val pageContent: @Composable (Int) -> Unit = { spreadIndex ->
        val indices = pageGroups.getOrNull(spreadIndex) ?: listOf(spreadIndex)
        val firstPage = indices.firstOrNull()?.let { pages.getOrNull(it) }
        val secondPage = if (indices.size > 1) pages.getOrNull(indices[1]) else null
        if (firstPage != null) {
            if (secondPage == null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    MangaPageItem(
                        page = firstPage,
                        loader = loader,
                        readingMode = readingMode,
                        colorFilterType = colorFilterType,
                        saturation = saturation,
                        contrast = contrast,
                        hue = hue,
                        fitMode = fitMode,
                        pageBackground = pageBackground,
                        autoBackground = autoBackground,
                        cropBorders = cropBorders,
                        mirrorPages = mirrorPages,
                        showPageNumber = showPageNumber,
                        tapZonesEnabled = tapZonesEnabled,
                        invertTapZones = invertTapZones,
                        tapLayout = tapLayout,
                        tapGrid = tapGrid,
                        longTapGrid = longTapGrid,
                        resetZoomSignal = if (spreadIndex == pagerState.currentPage) resetZoomSignal else 0,
                        wideRotation = wideRotation,
                        onCenterTap = onCenterTap,
                        onLongPress = onLongPress,
                        onLongPressPage = onLongPressPage,
                        onNextPage = onNextPage,
                        onPrevPage = onPrevPage,
                        onSourceLogin = onSourceLogin,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                if (stitchDualPages) {
                    MangaDualSpread(
                        firstPage = firstPage,
                        secondPage = secondPage,
                        loader = loader,
                        readingMode = readingMode,
                        colorFilterType = colorFilterType,
                        fitMode = fitMode,
                        stitch = stitchDualPages,
                        onCenterTap = onCenterTap,
                        onNextPage = onNextPage,
                        onPrevPage = onPrevPage,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    // Yayılım dokunmaları: Initial-pass gözlemci. detectTapGestures
                    // (Main pass) çocuk ZoomableAsyncImage olayları tükettiği için
                    // burada hiç tetiklenmiyordu → menü / sağ-sol / uzun basma ölüydü.
                    val haptics = LocalHapticFeedback.current
                    val longPress by rememberUpdatedState(onLongPress)
                    val longPressPage by rememberUpdatedState(onLongPressPage)
                    val spreadFirst by rememberUpdatedState(firstPage.index)
                    val spreadLast by rememberUpdatedState(secondPage?.index ?: firstPage.index)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .readerTapGestures(
                                tapZonesEnabled = tapZonesEnabled,
                                rtl = isRtl,
                                invertTapZones = invertTapZones,
                                tapLayout = tapLayout.ifBlank { "Default" },
                                tapGrid = tapGrid,
                                longTapGrid = longTapGrid,
                                haptics = haptics,
                                onAction = { action ->
                                    when (action) {
                                        ReaderTapAction.MENU -> centerTap()
                                        ReaderTapAction.NEXT -> nextPage()
                                        ReaderTapAction.PREVIOUS -> prevPage()
                                        ReaderTapAction.NONE -> Unit
                                    }
                                },
                                onLongPress = { xFraction ->
                                    longPress()
                                    longPressPage?.invoke(
                                        readerSpreadPressedPage(xFraction, isRtl, spreadFirst, spreadLast)
                                    )
                                },
                            )
                    ) {
                        listOf(firstPage, secondPage).forEach { page ->
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                MangaPageItem(
                                    page = page,
                                    loader = loader,
                                    readingMode = readingMode,
                                    colorFilterType = colorFilterType,
                                    saturation = saturation,
                                    contrast = contrast,
                                    hue = hue,
                                    fitMode = fitMode,
                                    pageBackground = pageBackground,
                                    autoBackground = autoBackground,
                                    cropBorders = cropBorders,
                                    mirrorPages = mirrorPages,
                                    showPageNumber = showPageNumber,
                                    tapZonesEnabled = tapZonesEnabled,
                                    invertTapZones = invertTapZones,
                                    tapLayout = tapLayout,
                                    tapGrid = tapGrid,
                                    longTapGrid = longTapGrid,
                                    resetZoomSignal = if (spreadIndex == pagerState.currentPage) resetZoomSignal else 0,
                                    wideRotation = wideRotation,
                                    tapsEnabled = false,
                                    onCenterTap = onCenterTap,
                                    onLongPress = onLongPress,
                                    onLongPressPage = onLongPressPage,
                                    onNextPage = onNextPage,
                                    onPrevPage = onPrevPage,
                                    onSourceLogin = onSourceLogin,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
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
                key = { index -> (pageGroups.getOrNull(index) ?: listOf(index)).firstOrNull() ?: index },
                beyondViewportPageCount = prefetchPages.coerceIn(0, 2)
            ) { pageIndex ->
                pageContent(pageIndex)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = modifier.fillMaxSize(),
                key = { index -> (pageGroups.getOrNull(index) ?: listOf(index)).firstOrNull() ?: index },
                beyondViewportPageCount = prefetchPages.coerceIn(0, 2)
            ) { pageIndex ->
                pageContent(pageIndex)
            }
        }
    }
}
