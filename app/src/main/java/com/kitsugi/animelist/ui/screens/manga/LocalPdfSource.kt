package com.kitsugi.animelist.ui.screens.manga

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Renders local PDF pages through the existing image-reader/cache pipeline. */
internal class LocalPdfSource(
    private val file: File,
    private val identity: String,
    private val chapter: MangaChapter,
    private val details: MangaDetails,
    private val pageCount: Int
) : MangaSource {
    override val name = "Local PDF"
    override val baseUrl = "local-pdf://reader"
    override val pkgName = "com.kitsugi.localpdf"
    override val lang = "all"
    private val renderMutex = Mutex()
    override suspend fun fetchPopularManga(page: Int) = MangaSourceResult(emptyList(), false)
    override suspend fun fetchSearchManga(page: Int, query: String) = MangaSourceResult(emptyList(), false)
    override suspend fun fetchMangaDetails(mangaUrl: String) = details
    override suspend fun fetchChapterList(mangaUrl: String) = listOf(chapter)
    override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = (0 until pageCount).map { index ->
        MangaPage(index = index, url = index.toString(), imageUrl = "local-pdf://page/$identity/$index")
    }

    override suspend fun getImage(page: MangaPage): InputStream = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            if (page.index !in 0 until pageCount || page.url != page.index.toString()) throw IOException("Unknown PDF page")
            currentCoroutineContext().ensureActive()
            withLocalPdf(file) { renderer ->
                if (renderer.pageCount != pageCount) throw IOException("PDF changed")
                renderer.openPage(page.index).use { pdfPage ->
                    val (width, height) = localPdfRenderSize(pdfPage.width, pdfPage.height) ?: throw IOException("Invalid PDF page dimensions")
                    val bitmap = try { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
                        catch (e: OutOfMemoryError) { throw IOException("Insufficient memory for PDF page", e) }
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        currentCoroutineContext().ensureActive()
                        val output = ByteArrayOutputStream()
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("PDF page rendering failed")
                        currentCoroutineContext().ensureActive()
                        ByteArrayInputStream(output.toByteArray())
                    } catch (e: OutOfMemoryError) {
                        throw IOException("Insufficient memory for PDF page", e)
                    } finally { bitmap.recycle() }
                }
            }
        }
    }
}

/** PdfRenderer owns the descriptor after successful construction. */
internal inline fun <T> withLocalPdf(file: File, block: (PdfRenderer) -> T): T {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = try { PdfRenderer(descriptor) } catch (e: Exception) { descriptor.close(); throw e }
    return renderer.use(block)
}
