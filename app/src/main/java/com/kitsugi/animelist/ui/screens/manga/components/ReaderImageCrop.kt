package com.kitsugi.animelist.ui.screens.manga.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val cropSemaphore = Semaphore(1)
private const val MAX_REGION_PIXELS = 8_000_000L
private const val MAX_ROTATED_PIXELS = 4_000_000L

/** Returns a temporary, lossless transformed file or null to keep the original. */
internal suspend fun createCroppedReaderImage(
    source: File,
    cacheDir: File,
    crop: Boolean = true,
    rotation: String = "Off",
    onFileCreated: (File) -> Unit
): File? = withContext(Dispatchers.IO) {
    cropSemaphore.withPermit {
        try {
            if (!isStaticJpegOrPng(source)) return@withPermit null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withPermit null
            var left = 0; var top = 0; var right = bounds.outWidth; var bottom = bounds.outHeight
            if (crop) {
                var sample = 1
                while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val preview = BitmapFactory.decodeFile(source.path, options)
                if (preview != null) {
                    val previewW = preview.width
                    val previewH = preview.height
                    val detected = try {
                        val pixels = IntArray(preview.width * preview.height)
                        preview.getPixels(pixels, 0, preview.width, 0, 0, preview.width, preview.height)
                        readerCropBounds(pixels, preview.width, preview.height)
                    } finally { preview.recycle() }
                    if (detected != null) {
                        left = (detected.left.toLong() * bounds.outWidth / previewW).toInt().coerceIn(0, bounds.outWidth - 1)
                        top = (detected.top.toLong() * bounds.outHeight / previewH).toInt().coerceIn(0, bounds.outHeight - 1)
                        right = (detected.right.toLong() * bounds.outWidth / previewW).toInt().coerceIn(left + 1, bounds.outWidth)
                        bottom = (detected.bottom.toLong() * bounds.outHeight / previewH).toInt().coerceIn(top + 1, bounds.outHeight)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val degrees = readerWideRotationDegrees(right - left, bottom - top, rotation)
            if (left == 0 && top == 0 && right == bounds.outWidth && bottom == bounds.outHeight && degrees == 0) return@withPermit null
            val pixels = (right - left).toLong() * (bottom - top)
            if (pixels > if (degrees == 0) MAX_REGION_PIXELS else MAX_ROTATED_PIXELS) return@withPermit null
            @Suppress("DEPRECATION") val decoder = BitmapRegionDecoder.newInstance(source.path, false) ?: return@withPermit null
            var bitmap = try { decoder.decodeRegion(Rect(left, top, right, bottom), BitmapFactory.Options()) }
                finally { decoder.recycle() }
            if (bitmap == null) return@withPermit null
            var result: File? = null
            try {
                if (degrees != 0) {
                    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
                    if (rotated !== bitmap) bitmap.recycle()
                    bitmap = rotated
                }
                currentCoroutineContext().ensureActive()
                val file = File.createTempFile("reader-crop-", ".png", cacheDir)
                result = file
                onFileCreated(file)
                file.outputStream().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw java.io.IOException("Cannot encode transformed image")
                }
                currentCoroutineContext().ensureActive()
                result = null
                file
            } finally { bitmap.recycle(); result?.delete() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
