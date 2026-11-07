package com.kitsugi.animelist.ui.screens.manga.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * "İki sayfa yan yana" (çift sayfa) modu için yay (spread) bileşeni.
 *
 * — İki sayfa yan yana gösterilir; RTL (Sağdan Sola) modunda ilk sayfa sağda durur.
 * — [stitch] açıkken ve iki sayfanın birleşim yerinde beyaz kenar boşluğu varsa,
 *   sayfalar otomatik olarak tek bir görselde birleştirilir (kitap görünümü).
 * — Birleştirme yapılamazsa sayfalar yan yana normal şekilde gösterilir.
 */
@Composable
fun MangaDualSpread(
    firstPage: MangaPage,
    secondPage: MangaPage,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode = ReadingMode.RightToLeft,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    stitch: Boolean = true,
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Her iki sayfayı da görünür alana girince yükle
    LaunchedEffect(firstPage.index) { loader.loadPage(firstPage) }
    LaunchedEffect(secondPage.index) { loader.loadPage(secondPage) }

    val stitchKey = "${firstPage.url}|${secondPage.url}"
    var stitched by remember(stitchKey) { mutableStateOf(MangaSpreadStitcher.cached(stitchKey)) }

    val bothReady = firstPage.status == MangaPageStatus.Ready && secondPage.status == MangaPageStatus.Ready
    if (stitch && bothReady && stitched == null) {
        LaunchedEffect(stitchKey, stitch) {
            val leftUrl = firstPage.imageUrl ?: firstPage.url
            val rightUrl = secondPage.imageUrl ?: secondPage.url
            val leftFile = firstPage.cachedImageFile ?: firstPage.cachedFile ?: loader.cache.getImageFile(loader.cacheKey(firstPage, leftUrl))
            val rightFile = secondPage.cachedImageFile ?: secondPage.cachedFile ?: loader.cache.getImageFile(loader.cacheKey(secondPage, rightUrl))
            if (leftFile.exists() && rightFile.exists()) {
                val result = withContext(Dispatchers.Default) {
                    MangaSpreadStitcher.stitch(leftFile, rightFile)
                }
                if (result != null) {
                    MangaSpreadStitcher.put(stitchKey, result)
                    stitched = result
                }
            }
        }
    }

    val isRtl = readingMode == ReadingMode.RightToLeft

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(readingMode) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val leftEdge = width * 0.33f
                    val rightEdge = width * 0.66f
                    if (offset.x < leftEdge) {
                        if (isRtl) onNextPage() else onPrevPage()
                    } else if (offset.x > rightEdge) {
                        if (isRtl) onPrevPage() else onNextPage()
                    } else {
                        onCenterTap()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val stitchedBitmap = stitched
        if (stitchedBitmap != null) {
            // Otomatik birleştirilmiş yay — tek görsel, kitap görünümü
            val context = LocalContext.current
            val imageRequest = remember(stitchKey) {
                ImageRequest.Builder(context)
                    .data(stitchedBitmap)
                    .crossfade(200)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = stringResource(
                    R.string.manga_sayfasi_x,
                    firstPage.index + 1
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Birleştirme yoksa sayfalar yan yana (RTL modda ilk sayfa sağda)
            Row(modifier = Modifier.fillMaxWidth()) {
                val (left, right) = if (isRtl) secondPage to firstPage else firstPage to secondPage
                Box(modifier = Modifier.weight(1f)) {
                    MangaPageItem(
                        page = left,
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
                Box(modifier = Modifier.weight(1f)) {
                    MangaPageItem(
                        page = right,
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
    }
}

/**
 * "İki sayfa yan yana" modunda, sayfaların birleşim yerindeki beyaz kenar
 * boşluğunu algılayıp iki sayfayı tek görselde birleştiren yardımcı.
 *
 * — Yalnızca arada (kenarlarda) yeterli beyaz boşluk varsa birleştirir;
 *   boşluk yoksa null döner ve sayfalar yan yana gösterilir.
 * — Sonuçlar oturum boyunca LRU bellek önbelleğinde tutulur.
 */
object MangaSpreadStitcher {

    private val cache = object : LruCache<String, Bitmap>(16) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun cached(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /**
     * İki sayfayı birleştirir. Birleşim yerinde beyaz boşluk yoksa null döner.
     */
    fun stitch(leftFile: File, rightFile: File): Bitmap? {
        val left = decodeCapped(leftFile) ?: return null
        val right = decodeCapped(rightFile) ?: return null
        return try {
            val gapLeft = left.whitespaceFromRightEdge()
            val gapRight = right.whitespaceFromLeftEdge()
            val totalGap = gapLeft + gapRight
            if (totalGap < 8) {
                // Arada belirgin boşluk yok — birleştirme yapılmaz
                null
            } else {
                val leftKeep = (left.width - gapLeft).coerceAtLeast((left.width * 0.55f).toInt())
                val rightStart = gapRight.coerceAtMost((right.width * 0.45f).toInt())
                val rightKeep = right.width - rightStart
                val outWidth = leftKeep + rightKeep
                val outHeight = max(left.height, right.height)
                val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawBitmap(
                    left,
                    android.graphics.Rect(0, 0, leftKeep, left.height),
                    android.graphics.Rect(0, 0, leftKeep, left.height),
                    null
                )
                canvas.drawBitmap(
                    right,
                    android.graphics.Rect(rightStart, 0, right.width, right.height),
                    android.graphics.Rect(leftKeep, 0, outWidth, right.height),
                    null
                )
                out
            }
        } catch (_: Throwable) {
            null
        } finally {
            left.recycle()
            right.recycle()
        }
    }

    /** Maksimum ~1600 px'e küçültülmüş (inSampleSize) bitmap çözer. */
    private fun decodeCapped(file: File): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            val maxDim = max(bounds.outWidth, bounds.outHeight)
            while (maxDim / (sample * 2) >= 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Throwable) {
            null
        }
    }

    /** Sağ kenardan sola doğru beyaz kolon sayısını döner (sol sayfanın iç kenar boşluğu). */
    private fun Bitmap.whitespaceFromRightEdge(): Int {
        val maxScan = min(width / 3, 400)
        var gap = 0
        var x = width - 1
        while (x >= width - maxScan && columnIsWhite(x)) {
            gap++
            x--
        }
        return gap
    }

    /** Sol kenardan sağa doğru beyaz kolon sayısını döner (sağ sayfanın iç kenar boşluğu). */
    private fun Bitmap.whitespaceFromLeftEdge(): Int {
        val maxScan = min(width / 3, 400)
        var gap = 0
        while (gap < maxScan && columnIsWhite(gap)) {
            gap++
        }
        return gap
    }

    /** Kolonun büyük ölçüde beyaz olup olmadığını örnekleme yaparak kontrol eder. */
    private fun Bitmap.columnIsWhite(x: Int): Boolean {
        val step = max(height / 24, 1)
        var y = 0
        while (y < height) {
            val pixel = getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            if (luminance < 238.0) return false
            y += step
        }
        return true
    }
}
