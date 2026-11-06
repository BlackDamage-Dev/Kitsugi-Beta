package com.kitsugi.animelist.data.manga.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MangaPageExportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun pngIsNotRecompressedOrShrunk() {
        val file = File.createTempFile("manga-png", ".input", context.cacheDir)
        val out = File(file.parentFile, "${file.name}.png")
        try {
            val bitmap = Bitmap.createBitmap(80, 5000, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val image = MangaPageExport.normalize(file, out)
            assertEquals(file, image.file)
            assertEquals("png", image.extension)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            assertEquals(5000, bounds.outHeight)
        } finally { file.delete(); out.delete() }
    }

    @Suppress("DEPRECATION")
    @Test fun webpIsActuallyConvertedToGalleryCompatiblePng() {
        val file = File.createTempFile("manga-webp", ".input", context.cacheDir)
        val out = File(file.parentFile, "${file.name}.png")
        try {
            val bitmap = Bitmap.createBitmap(80, 160, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP, 100, it) }
            bitmap.recycle()
            val image = MangaPageExport.normalize(file, out)
            assertEquals("image/png", image.mime)
            assertEquals(out, image.file)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(out.absolutePath, bounds)
            assertEquals("image/png", bounds.outMimeType)
            assertEquals(80, bounds.outWidth)
            assertEquals(160, bounds.outHeight)
        } finally { file.delete(); out.delete() }
    }

    @Test fun htmlChallengeIsNotExportedAsJpeg() {
        val file = File.createTempFile("manga-html", ".input", context.cacheDir)
        val out = File(file.parentFile, "${file.name}.png")
        try {
            file.writeText("<html>Cloudflare verification required</html>")
            try { MangaPageExport.normalize(file, out); fail("HTML must be rejected") }
            catch (_: IOException) { }
        } finally { file.delete(); out.delete() }
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun publishedPageHasJournalUriAndCanBeReadAndDeletedWithoutBroadStoragePermission() = runBlocking {
        val file = File.createTempFile("manga-publish", ".png", context.cacheDir)
        val title = "Kitsugi Export Test ${System.nanoTime()}"
        val record = MangaDownload("0123456789abcdef", "test", "Test", "tr", "/test", title,
            "/1", "Bölüm 1", 1f, relativePath = "Pictures/Kitsugi/Manga/Tests/$title/", totalPages = 1)
        var reserved: MangaDownloadedPage? = null
        try {
            val bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val saved = MangaPageExport.save(context, record, 0, MangaExportImage(file, "png", "image/png")) {
                reserved = it
                assertFalse(it.complete)
            }
            assertEquals(reserved?.uri, saved.uri)
            assertTrue(MangaPageExport.readable(context, saved))
            assertTrue(record.copy(pages = listOf(saved)).hasAllPages)
        } finally {
            reserved?.let { MangaPageExport.delete(context, it) }
            MangaPageExport.deleteUnjournaledRows(context, record)
            file.delete()
        }
    }
}
