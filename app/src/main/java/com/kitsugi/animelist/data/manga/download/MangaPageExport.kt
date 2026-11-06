package com.kitsugi.animelist.data.manga.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

internal data class MangaExportImage(val file: File, val extension: String, val mime: String)

/** Disk-streamed downloads; never hold the whole chapter or original image bytes in RAM. */
internal object MangaPageExport {
    private const val MAX_INPUT_BYTES = 64L * 1024 * 1024
    private const val MAX_CONVERT_PIXELS = 32_000_000L
    private val conversionLock = Any()

    suspend fun receive(stream: InputStream, target: File) = withContext(Dispatchers.IO) {
        var total = 0L
        target.outputStream().use { out ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_INPUT_BYTES) throw IOException("Sayfa 64 MB güvenlik sınırını aşıyor")
                out.write(buffer, 0, n)
            }
        }
        if (total == 0L) throw IOException("Kaynak boş görsel döndürdü")
    }

    fun normalize(input: File, converted: File): MangaExportImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.absolutePath, bounds)
        // Keep common gallery formats byte-for-byte (including GIF animation and tall PNGs).
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            when (bounds.outMimeType) {
                "image/jpeg" -> return MangaExportImage(input, "jpg", "image/jpeg")
                "image/png" -> return MangaExportImage(input, "png", "image/png")
                "image/gif" -> return MangaExportImage(input, "gif", "image/gif")
            }
        }
        // Two chapters may download, but never decode two huge pages concurrently.
        return synchronized(conversionLock) {
            val runtime = Runtime.getRuntime()
            val availableHeap = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
            val pixelBudget = minOf(MAX_CONVERT_PIXELS, availableHeap.coerceAtLeast(0) / 8)
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(input)) { decoder, info, _ ->
                    if (info.size.width.toLong() * info.size.height > pixelBudget) {
                        throw IOException("Görsel dönüştürme için çok büyük; 32 MP veya kullanılabilir bellek sınırı aşıldı")
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Cihaz bu görsel biçimini çözemiyor veya kaynak görsel yerine HTML döndürdü")
                if (bounds.outWidth.toLong() * bounds.outHeight > pixelBudget) throw IOException("Görsel dönüştürme için yeterli bellek yok")
                BitmapFactory.decodeFile(input.absolutePath) ?: throw IOException("Görsel çözülemedi")
            }
            try {
                // PNG preserves text, line art and transparency; never relabel WebP/AVIF as JPEG.
                converted.outputStream().use {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("PNG dönüşümü başarısız")
                }
            } finally { bitmap.recycle() }
            MangaExportImage(converted, "png", "image/png")
        }
    }

    fun readable(context: Context, page: MangaDownloadedPage): Boolean = page.complete && runCatching {
        context.contentResolver.openAssetFileDescriptor(Uri.parse(page.uri), "r")?.use { it.length != 0L } == true
    }.getOrDefault(false)

    /** Reserve and journal a pending row BEFORE copying. A retry reuses that exact row/name. */
    suspend fun save(
        context: Context,
        download: MangaDownload,
        index: Int,
        image: MangaExportImage,
        onReserved: suspend (MangaDownloadedPage) -> Unit
    ): MangaDownloadedPage = withContext(Dispatchers.IO) {
        require(download.relativePath.startsWith("Pictures/Kitsugi/Manga/") && ".." !in download.relativePath.split('/'))
        val name = MangaDownloadNaming.pageName(download, index, image.extension)
        val resolver = context.contentResolver
        val uri: Uri
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val existing = resolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?",
                arrayOf(download.relativePath, name), null)?.use {
                if (it.moveToFirst()) ContentUris.withAppendedId(collection, it.getLong(0)) else null
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, image.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, download.relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            uri = existing ?: resolver.insert(collection, values) ?: throw IOException("Galeri kaydı oluşturulamadı")
            if (existing != null) resolver.update(uri, values, null, null)
        } else {
            val folder = File(Environment.getExternalStorageDirectory(), download.relativePath)
            check(folder.isDirectory || folder.mkdirs()) { "Manga klasörü oluşturulamadı; depolama iznini kontrol edin" }
            uri = Uri.fromFile(File(folder, name))
        }
        val reserved = MangaDownloadedPage(index, uri.toString(), name, image.mime)
        onReserved(reserved)
        currentCoroutineContext().ensureActive()
        if (uri.scheme == "file") {
            val target = File(requireNotNull(uri.path))
            val part = File(target.parentFile, "${target.name}.part")
            try {
                image.file.inputStream().use { input -> part.outputStream().use { input.copyTo(it) } }
                currentCoroutineContext().ensureActive()
                if (!part.renameTo(target)) throw IOException("Manga sayfası kaydedilemedi")
            } finally { part.delete() }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(image.mime), null)
        } else {
            resolver.openOutputStream(uri, "wt")?.use { output -> image.file.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("Görsel yazma akışı açılamadı")
            currentCoroutineContext().ensureActive()
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        reserved.copy(bytes = image.file.length(), complete = true)
    }

    /** Also removes a row reserved immediately before a process died, before its journal write. */
    fun deleteUnjournaledRows(context: Context, download: MangaDownload) {
        if (Build.VERSION.SDK_INT < 29) return
        require(download.relativePath.startsWith("Pictures/Kitsugi/Manga/"))
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uris = context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH}=?", arrayOf(download.relativePath), null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(ContentUris.withAppendedId(collection, cursor.getLong(0))) }
        }.orEmpty()
        uris.forEach { context.contentResolver.delete(it, null, null) }
    }

    fun delete(context: Context, page: MangaDownloadedPage) {
        val uri = Uri.parse(page.uri)
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path))
            val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kitsugi/Manga")
            require(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
            if (file.exists() && !file.delete()) throw IOException("Manga sayfası silinemedi")
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            var dir = file.parentFile
            while (dir != null && dir != root && dir.list()?.isEmpty() == true) {
                if (!dir.delete()) break
                dir = dir.parentFile
            }
        } else {
            require(uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY)
            context.contentResolver.delete(uri, null, null)
        }
    }
}
