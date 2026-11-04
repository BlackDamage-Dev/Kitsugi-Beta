package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.layout.fillMaxSize
import com.kitsugi.animelist.ui.utils.uniqueLazyKeys
import androidx.compose.runtime.remember
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isRtl       = readingMode == ReadingMode.RightToLeft
    val direction   = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        val pageKeys = remember(pages) { pages.uniqueLazyKeys { it.index } }
        val pageContent: @Composable (Int) -> Unit = { pageIndex ->
            pages.getOrNull(pageIndex)?.let { page ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    MangaPageItem(
                        page = page,
                        loader = loader,
                        readingMode = readingMode,
                        colorFilterType = colorFilterType,
                        fitMode = fitMode,
                        onCenterTap = onCenterTap,
                        onNextPage = onNextPage,
                        onPrevPage = onPrevPage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        if (readingMode == ReadingMode.Vertical) {
            VerticalPager(
                state = pagerState,
                modifier = modifier.fillMaxSize(),
                key = { index -> pageKeys.getOrNull(index) ?: "page_$index" },
                beyondViewportPageCount = 2,
            ) { pageContent(it) }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = modifier.fillMaxSize(),
                key = { index -> pageKeys.getOrNull(index) ?: "page_$index" },
                beyondViewportPageCount = 2,
            ) { pageContent(it) }
        }
    }
}
