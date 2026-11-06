package com.kitsugi.animelist.ui.screens.manga.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val backgroundSampleSemaphore = Semaphore(2)

/** Decode a tiny sample from the already cached page; never downloads images for color. */
@Composable
internal fun rememberReaderAutoBackground(file: File?, enabled: Boolean, fallback: Color): Color {
    val color by produceState(fallback, file?.path, file?.lastModified(), enabled, fallback) {
        value = fallback
        if (!enabled || file == null || !file.isFile) return@produceState
        value = withContext(Dispatchers.IO) {
            backgroundSampleSemaphore.withPermit {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withPermit fallback
                var sample = 1
                while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 64 && sample < (1 shl 29)) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return@withPermit fallback
                try {
                    if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.width.toLong() * bitmap.height > 65_536) return@withPermit fallback
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    readerAverageEdgeColor(pixels, bitmap.width, bitmap.height)?.let { Color(it) } ?: fallback
                } finally { bitmap.recycle() }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { fallback } catch (_: OutOfMemoryError) { fallback }
            }
        }
    }
    return color
}
