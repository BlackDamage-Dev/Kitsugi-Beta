package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.layout.fillMaxSize
import com.kitsugi.animelist.ui.utils.uniqueLazyKeys
import androidx.compose.runtime.remember
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Yatay sayfa çevirmeli okuma modu (LTR / RTL).
 *
 * — LTR (Soldan Sağa): Manhua, Batı çizgi romanları için
 * — RTL (Sağdan Sola): Japonca manga için (pager `LayoutDirection.Rtl` ile
 *   çizilir, böylece sayfa 1 sağda başlar)
 *
 * Her sayfa [MangaPageItem] bileşeni ile çizilir:
 *   • Telephoto ZoomableAsyncImage → pinch-to-zoom, çift dokunma
 *   • MangaPageLoaderV2 ile çift yönlü prefetch + Coil disk önbelleği
 *
 * NOT: "Dikey" okuma modu artık burada değil, [MangaVerticalPagerReader]
 * içinde çizilir (önceden buraya düşüp yatay çalışıyordu).
 */
@Composable
fun MangaPagerReader(
    pages: List<MangaPage>,
    pagerState: PagerState,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode = ReadingMode.RightToLeft,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    cropBorders: Boolean = false,
    mirrorPages: Boolean = false,
    showPageNumber: Boolean = true,
    tapZonesEnabled: Boolean = true,
    invertTapZones: Boolean = false,
    onCenterTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    /** Uzun basınca galeriyi aç (sayfa indeksi ile). */
    onLongPressPage: ((Int) -> Unit)? = null,
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
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

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        val pageKeys = remember(pages) { pages.uniqueLazyKeys { it.index } }
        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            key = { index -> pageKeys.getOrNull(index) ?: "page_$index" },
            beyondViewportPageCount = 2, // Yan sayfaları belleğe al
        ) { pageIndex ->
            val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
            // Sayfa içeriği her zaman LTR çizilir: RTL yalnızca kaydırma yönünü
            // belirler, görselin kendisini aynalamaz.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                MangaPageItem(
                    page = page,
                    loader = loader,
                    readingMode = readingMode,
                    colorFilterType = colorFilterType,
                    fitMode = fitMode,
                    cropBorders = cropBorders,
                    mirrorPages = mirrorPages,
                    showPageNumber = showPageNumber,
                    tapZonesEnabled = tapZonesEnabled,
                    invertTapZones = invertTapZones,
                    onCenterTap = onCenterTap,
                    onLongPress = onLongPress,
                    onLongPressPage = onLongPressPage,
                    onNextPage = onNextPage,
                    onPrevPage = onPrevPage,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
