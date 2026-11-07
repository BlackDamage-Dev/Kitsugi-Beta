package com.kitsugi.animelist.data.manga.loader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.util.LruCache
import com.kitsugi.animelist.data.manga.MangaImageInfo
import com.kitsugi.animelist.data.manga.WebtoonLayout
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Decode only lazy, visible strips. No full-height textures and no per-image scroll/zoom state.
 * Two decodes at a time, <=2048px per bitmap edge, 16MiB reusable tile cache. Bitmaps still being
 * drawn are NOT recycled on eviction (a recycled bitmap on the RenderThread can crash the app).
 */
class MangaImageTileDecoder {
    private val decodes = Semaphore(2)
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    @Suppress("DEPRECATION")
    suspend fun decode(file: File, tile: WebtoonLayout.Tile, targetWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        val image = requireNotNull(tile.image)
        val region = image.sourceRegion(tile.top, tile.bottom)
        val sample = WebtoonLayout.sampleSize(region.width, region.height, targetWidth)
        val key = "${file.absolutePath}:${file.length()}:${image.orientation}:${tile.top}:${tile.bottom}:$sample"
        cache.get(key)?.let { return@withContext it }
        decodes.withPermit {
            cache.get(key)?.let { return@withPermit it }
            ensureActive()
            val decoder = runCatching { BitmapRegionDecoder.newInstance(file.absolutePath, false) }.getOrNull()
            val bitmap = if (decoder != null) {
                try {
                    val raw = decoder.decodeRegion(
                        Rect(region.left, region.top, region.right, region.bottom),
                        BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        },
                    ) ?: throw IOException("Unable to decode image strip")
                    upright(raw, image.orientation)
                } finally {
                    decoder.recycle()
                }
            } else {
                // GIF/AVIF and codecs without region decoding: bounded still-image fallback.
                // Never attempt an original-size decode of an unsupported 100,000px image.
                val fallbackSample = WebtoonLayout.sampleSize(image.rawWidth, image.rawHeight, targetWidth)
                val raw = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                    inSampleSize = fallbackSample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: throw IOException("Unable to decode image")
                val full = upright(raw, image.orientation)
                val top = (tile.top.toLong() * full.height / image.height).toInt().coerceIn(0, full.height - 1)
                val bottom = (tile.bottom.toLong() * full.height / image.height).toInt().coerceIn(top + 1, full.height)
                Bitmap.createBitmap(full, 0, top, full.width, bottom - top)
            }
            ensureActive()
            cache.put(key, bitmap)
            bitmap
        }
    }

    fun clear() = cache.evictAll()

    private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation !in 2..8) return bitmap
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        /** Must run off the main thread, before publishing MangaPageStatus.Ready. */
        fun readHeader(file: File): MangaImageInfo {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid image header")
            val orientation = runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            }.getOrDefault(1).coerceIn(1, 8)
            val info = MangaImageInfo(bounds.outWidth, bounds.outHeight, orientation)
            // A corrupt 1px-wide header must not allocate millions of lazy item descriptors.
            if (info.height.toLong() > info.width.toLong() * 4096) throw IOException("Image strip exceeds safe layout limit")
            return info
        }
    }
}
