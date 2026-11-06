package com.kitsugi.animelist.ui.screens.manga.components
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import kotlinx.coroutines.flow.distinctUntilChanged

import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Sürekli dikey kaydırmalı Webtoon / Manhwa okuma modu.
 *
 * — Sayfalar LazyColumn içinde dikey olarak üst üste dizilir.
 * — Her sayfa MangaPageItem ile ZoomableAsyncImage üzerinden çizilir.
 * — Aktif görünen sayfayı tespit ederek MangaPageLoader.loadPage() tetiklenir.
 * — Sayfalar arası 4dp boşluk bırakılır; okunurken kesintisiz bir akış hissi sağlanır.
 *
 * Not: Webtoon resimlerinin en-boy oranı çok uzun olabilir (1:10+).
 *      Telephoto bunu otomatik olarak kiremit (tile) döşeyerek çizer,
 *      bu sayede büyük resimlerde RAM taşması (OOM) yaşanmaz.
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
    zoomEnabled: Boolean = true,
    wideRotation: String = "Off",
    autoNext: Boolean = false,
    pageBackground: Color = Color.Black,
    autoBackground: Boolean = false,
    showPageNumber: Boolean = true,
    invertTapZones: Boolean = false,
    tapLayout: String = "Default",
    tapGrid: String = DEFAULT_READER_TAP_GRID,
    longTapGrid: String = DEFAULT_READER_LONG_TAP_GRID,
    onCenterTap: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    hasNextChapter: Boolean = false,
    nextChapterName: String? = null,
    onGoToNextChapter: () -> Unit = {},
    onChapterEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tapViewport by remember { mutableStateOf<ReaderTapViewport?>(null) }
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)
    val currentGoToNextChapter by rememberUpdatedState(onGoToNextChapter)
    val currentOnChapterEnd by rememberUpdatedState(onChapterEnd)
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
    // This avoids skipping a short chapter merely because the footer is visible.
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
        state    = listState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val next = ReaderTapViewport(coordinates.positionInWindow().y, coordinates.size.height.toFloat())
                if (tapViewport != next) tapViewport = next
            }
            .background(pageBackground)
    ) {
        itemsIndexed(
            items = pages,
            key   = { _, page -> page.index }
        ) { _, page ->

            // Her sayfa görünür alana girdiğinde yüklemeyi başlat
            LaunchedEffect(page.index) {
                loader.loadPage(page)
            }

            // A LazyColumn gives its children unbounded height. Read dimensions only
            // from the already downloaded cache file; never decode the full bitmap.
            val imageUrl = page.imageUrl ?: page.url
            val aspectRatio by produceState<Float?>(null, page, page.status, imageUrl) {
                value = if (page.status == MangaPageStatus.Ready) withContext(Dispatchers.IO) {
                    val file = loader.cache.getImageFile(imageUrl)
                    if (!file.exists()) null else {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, options)
                        webtoonAspectRatio(options.outWidth, options.outHeight)
                    }
                } else null
            }
            val cacheFile = loader.cache.getImageFile(imageUrl)
            val crop = rememberReaderCrop(cacheFile, cropBorders && page.status == MangaPageStatus.Ready,
                if (page.status == MangaPageStatus.Ready) wideRotation else "Off")
            val displayedRatio = crop?.let { webtoonAspectRatio(it.width, it.height) } ?: aspectRatio
            val pageModifier = Modifier.fillMaxWidth()
                .padding(horizontal = sidePadding.coerceIn(0, 64).dp)
                .then(displayedRatio?.let { Modifier.aspectRatio(it) } ?: Modifier.height(480.dp))

            MangaPageItem(
                page        = page,
                loader      = loader,
                readingMode = ReadingMode.Webtoon,
                zoomEnabled = zoomEnabled,
                colorFilterType = colorFilterType,
                saturation = saturation,
                contrast = contrast,
                hue = hue,
                fitMode     = fitMode,
                pageBackground = pageBackground,
                autoBackground = autoBackground,
                showPageNumber = showPageNumber,
                invertTapZones = invertTapZones,
                tapLayout = tapLayout,
                tapGrid = tapGrid,
                longTapGrid = longTapGrid,
                tapViewport = tapViewport,
                imageOverride = crop?.file,
                onCenterTap = onCenterTap,
                onNextPage   = onNextPage,
                onPrevPage   = onPrevPage,
                modifier    = pageModifier
            )

            // Sayfalar arası ince ayırıcı
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pageGap.coerceIn(0, 32).dp)
                    .background(Color(0xFF1A1A1A))
            )
        }

        // ─── Bölüm Sonu Geçiş Kartı ───────────────────────────────────────────
        // Webtoon'da en alta gelindiğinde sonraki bölüme geçiş için görünür bir kart.
        item(key = "chapter_transition_footer") {
            ChapterTransitionFooter(
                hasNextChapter = hasNextChapter,
                nextChapterName = nextChapterName,
                onGoToNextChapter = {
                    currentOnChapterEnd()
                    currentGoToNextChapter()
                }
            )
        }
    }
}

@Composable
private fun ChapterTransitionFooter(
    hasNextChapter: Boolean,
    nextChapterName: String?,
    onGoToNextChapter: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasNextChapter) {
                Text(
                    text = stringResource(R.string.inline_end_of_episode_928d999),
                    color = Color.White.copy(0.5f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                KitsugiButton(
                    onClick = onGoToNextChapter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C4DFF),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = nextChapterName?.let { "Sonraki: $it" } ?: stringResource(R.string.inline_next_episode_ab03b91))
                }
            } else {
                Text(
                    text = stringResource(R.string.inline_you_re_on_the_last_4e9fc31),
                    color = Color.White.copy(0.5f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

/** Bounds invalid and extreme dimensions before passing them to Compose layout. */
internal fun webtoonAspectRatio(width: Int, height: Int): Float? =
    if (width <= 0 || height <= 0) null else (width.toFloat() / height).coerceIn(0.02f, 100f)
