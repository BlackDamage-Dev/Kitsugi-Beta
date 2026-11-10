package com.kitsugi.animelist.data.manga.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Native PDF renderer with one cache file per immutable local document/page. */
class PdfPageLoader(private val context: Context, private val file: File) {
    suspend fun pageCount(): Int = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }
    }

    fun pages(chapterUrl: String, count: Int): List<MangaPage> = List(count) { index ->
        MangaPage(index = index, url = "$chapterUrl#pdf-$index", imageUrl = "$chapterUrl#pdf-$index").apply {
            archivePath = "pdf:$index"
            status = MangaPageStatus.Queue
        }
    }

    suspend fun renderPage(index: Int): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "manga-pdf-pages").apply { mkdirs() }
        val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}:$index"
        val name = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val output = File(directory, "$name.webp")
        if (output.isFile && output.length() > 0) {
            com.kitsugi.animelist.data.manga.MangaReaderStorage.pruneDerivedCache(directory, keep = output)
            return@withContext output
        }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(index in 0 until renderer.pageCount) { "PDF sayfası sınır dışında" }
                renderer.openPage(index).use { page ->
                    val scale = (1800f / page.width).coerceAtMost(3f)
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val temporary = File(directory, "$name.tmp")
                    temporary.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.WEBP, 90, it) }
                    bitmap.recycle()
                    if (!temporary.renameTo(output)) {
                        temporary.delete()
                        if (!output.exists()) throw java.io.IOException("PDF sayfa önbelleği yazılamadı")
                    }
                }
            }
        }
        com.kitsugi.animelist.data.manga.MangaReaderStorage.pruneDerivedCache(directory, keep = output)
        output
    }
}
