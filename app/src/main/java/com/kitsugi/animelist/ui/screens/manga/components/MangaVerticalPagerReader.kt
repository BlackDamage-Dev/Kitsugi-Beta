package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Dikey sayfalama modu ("Dikey" okuma modu).
 *
 * NEDEN VAR?
 * ----------
 * `MangaReaderScreen` "Dikey" modu seçildiğinde de `MangaPagerReader`'ı
 * (HorizontalPager) çiziyordu; yani mod seçimi görsel olarak HİÇBİR ŞEY
 * değiştirmiyordu. Bu bileşen gerçek dikey sayfa geçişini sağlar:
 *
 * — Sayfalar arasında tek sayfalık "snap" ile dikey kaydırma (webtoon'un
 *   sürekli akışından farkı budur: burada her ekranda bir sayfa durur)
 * — `MangaPageItem` üzerinden Telephoto ile pinch/çift dokunma zoom
 * — Okuma ilerlemesi ve prefetch, `pagerState.currentPage` üzerinden bildirilir
 */
@Composable
fun MangaVerticalPagerReader(
    pages: List<MangaPage>,
    pagerState: PagerState,
    loader: MangaPageLoaderV2,
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
    val reportPageChanged by rememberUpdatedState(onPageChanged)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { index -> reportPageChanged(index) }
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        key = { index -> pages.getOrNull(index)?.index ?: index },
        beyondViewportPageCount = 1,
        pageSpacing = 4.dp,
    ) { pageIndex ->
        val page = pages.getOrNull(pageIndex) ?: return@VerticalPager
        MangaPageItem(
            page = page,
            loader = loader,
            readingMode = ReadingMode.Vertical,
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
