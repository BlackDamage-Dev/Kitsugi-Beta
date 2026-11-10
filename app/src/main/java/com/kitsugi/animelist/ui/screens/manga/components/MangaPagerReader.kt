package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaReaderOptions
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.buildMangaDisplayPages
import com.kitsugi.animelist.data.manga.orderMangaSpreadPages
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2

/** Paged reader. Double-page grouping is shared with the screen's progress/navigation model. */
@Composable
fun MangaPagerReader(
    pages: List<MangaPage>,
    pagerState: PagerState,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode = ReadingMode.RightToLeft,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    doublePage: Boolean = false,
    readerOptions: MangaReaderOptions = MangaReaderOptions(),
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isRtl = readingMode == ReadingMode.RightToLeft
    val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val displayPages = remember(pages, doublePage) { buildMangaDisplayPages(pages, doublePage) }
    LaunchedEffect(readerOptions.autoScroll, readerOptions.autoScrollSpeed) {
        if (!readerOptions.autoScroll) return@LaunchedEffect
        while (isActive) {
            delay(24L)
            pagerState.dispatchRawDelta(readerOptions.autoScrollSpeed * 24f / 1000f)
        }
    }


    val pageContent: @Composable PagerScope.(Int) -> Unit = { displayIndex ->
        val displayPage = displayPages.getOrNull(displayIndex)
        if (displayPage != null) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if (displayPage.pages.size == 1) {
                MangaPageItem(
                    page = displayPage.pages.single(),
                    loader = loader,
                    readingMode = readingMode,
                    colorFilterType = colorFilterType,
                    fitMode = fitMode,
                    readerOptions = readerOptions,
                    totalPages = pages.size,
                    onCenterTap = onCenterTap,
                    onNextPage = onNextPage,
                    onPrevPage = onPrevPage,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DoublePageSpread(
                    displayPages = displayPage.pages,
                    loader = loader,
                    readingMode = readingMode,
                    colorFilterType = colorFilterType,
                    fitMode = fitMode,
                    readerOptions = readerOptions,
                    onCenterTap = onCenterTap,
                    onNextPage = onNextPage,
                    onPrevPage = onPrevPage,
                )
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        val pagerModifier = modifier.fillMaxSize()
        val contentPadding = PaddingValues(horizontal = readerOptions.sidePaddingDp.dp)
        if (readingMode == ReadingMode.Vertical) {
            VerticalPager(
                state = pagerState,
                modifier = pagerModifier,
                key = { index -> displayPages.getOrNull(index)?.key ?: index.toString() },
                contentPadding = contentPadding,
                pageSpacing = readerOptions.pageGapDp.dp,
                beyondViewportPageCount = 1,
                pageContent = pageContent,
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = pagerModifier,
                key = { index -> displayPages.getOrNull(index)?.key ?: index.toString() },
                contentPadding = contentPadding,
                pageSpacing = readerOptions.pageGapDp.dp,
                beyondViewportPageCount = 1,
                pageContent = pageContent,
            )
        }
    }
}

@Composable
private fun DoublePageSpread(
    displayPages: List<MangaPage>,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode,
    colorFilterType: ColorFilterType,
    fitMode: MangaFitMode,
    readerOptions: MangaReaderOptions,
    onCenterTap: () -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
) {
    val visualPages = remember(displayPages, readingMode) { orderMangaSpreadPages(displayPages, readingMode) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(readerOptions.pageGapDp.dp),
        ) {
            visualPages.forEachIndexed { index, page ->
                val visuallyFirst = index == 0
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        MangaPageItem(
                            page = page,
                            loader = loader,
                            readingMode = readingMode,
                            colorFilterType = colorFilterType,
                            fitMode = fitMode,
                            readerOptions = readerOptions,
                            totalPages = displayPages.size,
                            tapNavigationEnabled = true,
                            allowWideRotation = false,
                            tapHorizontalOrigin = if (visuallyFirst) 0f else 0.5f,
                            tapHorizontalSpan = 0.5f,
                            onCenterTap = onCenterTap,
                            onNextPage = onNextPage,
                            onPrevPage = onPrevPage,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
