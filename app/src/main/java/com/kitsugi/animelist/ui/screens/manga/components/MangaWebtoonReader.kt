package com.kitsugi.animelist.ui.screens.manga.components
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import kotlinx.coroutines.flow.distinctUntilChanged

import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaReaderOptions
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
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    readerOptions: MangaReaderOptions = MangaReaderOptions(),
    onCenterTap: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    hasNextChapter: Boolean = false,
    nextChapterName: String? = null,
    onGoToNextChapter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Görünen ilk öğeyi takip et ve üst katmana bildir
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> onPageChanged(index) }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = readerOptions.sidePaddingDp.dp),
        modifier = modifier
            .fillMaxSize()
            .background(if (readerOptions.bookEffect) Color(0xFF17130E) else Color.Black)
    ) {
        itemsIndexed(
            items = pages,
            key   = { _, page -> page.index }
        ) { _, page ->

            // Her sayfa görünür alana girdiğinde yüklemeyi başlat
            LaunchedEffect(page.index) {
                loader.loadPage(page)
            }

            MangaPageItem(
                page        = page,
                loader      = loader,
                readingMode = ReadingMode.Webtoon,
                colorFilterType = colorFilterType,
                fitMode     = fitMode,
                readerOptions = readerOptions,
                allowWideRotation = false,
                onCenterTap = onCenterTap,
                onNextPage   = onNextPage,
                onPrevPage   = onPrevPage,
                modifier    = Modifier.fillMaxWidth()
            )

            // Sayfalar arası ince ayırıcı
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(readerOptions.pageGapDp.dp)
                    .background(if (readerOptions.bookEffect) Color(0xFF17130E) else Color(0xFF1A1A1A))
            )
        }

        // ─── Bölüm Sonu Geçiş Kartı ───────────────────────────────────────────
        // Webtoon'da en alta gelindiğinde sonraki bölüme geçiş için görünür bir kart.
        item(key = "chapter_transition_footer") {
            ChapterTransitionFooter(
                hasNextChapter = hasNextChapter,
                nextChapterName = nextChapterName,
                onGoToNextChapter = onGoToNextChapter
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
