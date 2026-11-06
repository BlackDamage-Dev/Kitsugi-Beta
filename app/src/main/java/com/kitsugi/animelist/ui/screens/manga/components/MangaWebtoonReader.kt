package com.kitsugi.animelist.ui.screens.manga.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.uniqueItemsIndexed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * Sürekli dikey kaydırmalı Webtoon / Manhwa okuma modu.
 *
 * Her sayfa dikey bir akışta peş peşe çizilir.
 * Sayfalar arasında boşluk ve kenar kırpma ayarları desteklenir.
 */
@Composable
fun MangaWebtoonReader(
    pages: List<MangaPage>,
    listState: LazyListState,
    loader: MangaPageLoaderV2,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    saturation: Int = 100,
    contrast: Int = 100,
    hue: Int = 0,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    sidePadding: Int = 0,
    pageGap: Int = 4,
    cropBorders: Boolean = false,
    mirrorPages: Boolean = false,
    zoomEnabled: Boolean = true,
    wideRotation: String = "Off",
    autoNext: Boolean = false,
    pageBackground: Color = Color.Black,
    autoBackground: Boolean = false,
    showPageNumber: Boolean = true,
    tapZonesEnabled: Boolean = true,
    invertTapZones: Boolean = false,
    tapLayout: String = "Default",
    tapGrid: String = DEFAULT_READER_TAP_GRID,
    longTapGrid: String = DEFAULT_READER_LONG_TAP_GRID,
    tapScroll: Boolean = false,
    scrollPercent: Int = 80,
    hasNextChapter: Boolean = false,
    nextChapterName: String? = null,
    /** Hedef bölümün çevirmen sürümü sayısı (>1 ise rozet gösterilir). */
    nextChapterVariantCount: Int = 0,
    showChapterTransition: Boolean = true,
    onCenterTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    /** Uzun basınca galeriyi aç (sayfa indeksi ile). */
    onLongPressPage: ((Int) -> Unit)? = null,
    onPageChanged: (Int) -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    onSourceLogin: (() -> Unit)? = null,
    onChapterEnd: () -> Unit = {},
    onGoToNextChapter: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var tapViewport by remember { mutableStateOf<ReaderTapViewport?>(null) }
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)
    val currentGoToNextChapter by rememberUpdatedState(onGoToNextChapter)
    val currentOnChapterEnd by rememberUpdatedState(onChapterEnd)
    val pageSizeMemory = remember { MangaPageSizeMemory() }

    // Görünen ilk öğeyi takip et ve üst katmana bildir
    LaunchedEffect(listState, pages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (pages.isNotEmpty()) currentOnPageChanged(index.coerceIn(0, pages.lastIndex))
            }
    }

    // A long last image is not complete just because its top is visible.
    LaunchedEffect(listState, pages) {
        var reported = false
        snapshotFlow {
            listState.isScrollInProgress &&
                listState.layoutInfo.visibleItemsInfo.any { it.key == "chapter_transition_footer" }
        }.distinctUntilChanged().collect { reachedFooter ->
            if (reachedFooter && !reported && pages.isNotEmpty()) {
                reported = true
                currentOnChapterEnd()
            }
        }
    }

    // Only advance after the user actually scrolls to the end of the footer.
    LaunchedEffect(listState, pages, autoNext, hasNextChapter) {
        if (autoNext && hasNextChapter && pages.isNotEmpty()) {
            var requested = false
            snapshotFlow {
                listState.isScrollInProgress && !listState.canScrollForward &&
                    listState.layoutInfo.visibleItemsInfo.any { it.key == "chapter_transition_footer" }
            }.distinctUntilChanged().collect { reachedEnd ->
                if (reachedEnd && !requested) {
                    requested = true
                    currentGoToNextChapter()
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val next = ReaderTapViewport(coordinates.positionInWindow().y, coordinates.size.height.toFloat())
                if (tapViewport != next) tapViewport = next
            }
            .background(pageBackground),
    ) {
        uniqueItemsIndexed(
            items = pages,
            key = { _, page -> page.index },
        ) { _, page ->
            val imageUrl = page.imageUrl ?: page.url
            val dimensions by produceState<Pair<Int, Int>?>(null, page, page.status, imageUrl) {
                value = if (page.status == MangaPageStatus.Ready) {
                    withContext(Dispatchers.IO) {
                        val key = loader.cacheKey(page, imageUrl)
                        loader.cache.getImageDimensions(key)
                    }
                } else null
            }

            val cacheFile = loader.cache.getImageFile(loader.cacheKey(page, imageUrl))
            val crop = rememberReaderCrop(
                source = cacheFile,
                enabled = cropBorders && page.status == MangaPageStatus.Ready,
                rotation = if (page.status == MangaPageStatus.Ready) wideRotation else "Off"
            )

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val maximumHeight = with(density) { 32_000.toDp() }
                val naturalHeight = dimensions?.let { (width, height) ->
                    maxWidth * (height.toFloat() / width)
                }
                val isExtremeStrip = naturalHeight != null && naturalHeight > maximumHeight

                val displayedRatio = crop?.let { webtoonAspectRatio(it.width, it.height) }
                    ?: dimensions?.let { (w, h) -> webtoonAspectRatio(w, h) }

                val pageModifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding.coerceIn(0, 64).dp)
                    .then(
                        if (isExtremeStrip) Modifier.height(maximumHeight)
                        else displayedRatio?.let { Modifier.aspectRatio(it) } ?: Modifier.height(480.dp)
                    )

                MangaPageItem(
                    page = page,
                    loader = loader,
                    readingMode = ReadingMode.Webtoon,
                    zoomEnabled = zoomEnabled,
                    colorFilterType = colorFilterType,
                    saturation = saturation,
                    contrast = contrast,
                    hue = hue,
                    fitMode = if (isExtremeStrip) MangaFitMode.FitScreen else fitMode,
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
                    tapViewport = tapViewport,
                    wideRotation = wideRotation,
                    imageOverride = crop?.file,
                    onCenterTap = onCenterTap,
                    onLongPress = onLongPress,
                    onLongPressPage = onLongPressPage,
                    onNextPage = onNextPage,
                    onPrevPage = onPrevPage,
                    onSourceLogin = onSourceLogin,
                    sizeMemory = pageSizeMemory,
                    modifier = pageModifier,
                )
            }

            // Sayfalar arası ince ayırıcı
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pageGap.coerceIn(0, 32).dp)
                    .background(Color(0xFF1A1A1A))
            )
        }

        // ─── Bölüm Sonu Geçiş Kartı ───────────────────────────────────────────
        item(key = "chapter_transition_footer") {
            ChapterTransitionFooter(
                hasNextChapter = hasNextChapter,
                nextChapterName = nextChapterName,
                variantCount = nextChapterVariantCount,
                compact = !showChapterTransition,
                onGoToNextChapter = {
                    currentOnChapterEnd()
                    currentGoToNextChapter()
                },
            )
        }
    }
}

@Composable
private fun ChapterTransitionFooter(
    hasNextChapter: Boolean,
    nextChapterName: String?,
    variantCount: Int,
    compact: Boolean,
    onGoToNextChapter: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 120.dp else 220.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            if (hasNextChapter) {
                Text(
                    text = stringResource(R.string.inline_end_of_episode_928d999),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                KitsugiButton(
                    onClick = onGoToNextChapter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KitsugiColors.Accent,
                        contentColor = KitsugiColors.OnAccent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.ArrowForward,
                        contentDescription = null,
                        tint = KitsugiColors.OnAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = nextChapterName?.let { stringResource(R.string.manga_reader_next_chapter, it) }
                            ?: stringResource(R.string.inline_next_episode_ab03b91)
                    )
                }
                if (variantCount > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.manga_next_chapter_multi, variantCount),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.inline_you_re_on_the_last_4e9fc31),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** Bounds invalid and extreme dimensions before passing them to Compose layout. */
internal fun webtoonAspectRatio(width: Int, height: Int): Float? =
    if (width <= 0 || height <= 0) null else (width.toFloat() / height).coerceIn(0.02f, 100f)
