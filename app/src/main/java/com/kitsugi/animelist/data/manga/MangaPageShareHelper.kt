package com.kitsugi.animelist.data.manga

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object MangaPageShareHelper {
    suspend fun resolvePageFile(context: Context, page: MangaPage, loader: MangaPageLoaderV2): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "reader-share").apply { mkdirs() }
        page.textParagraphs?.let { paragraphs ->
            File(directory, "chapter-${page.index + 1}.txt").apply { writeText(paragraphs.joinToString("\n\n")) }
        } ?: run {
            loader.loadPage(page)
            val imageUrl = page.imageUrl ?: page.url
            val source = loader.cache.getImageFile(imageUrl).takeIf { it.exists() }
                ?: if (imageUrl.startsWith("file:")) File(java.net.URI(imageUrl)) else null
                ?: throw java.io.IOException("Sayfa henüz hazır değil")
            File(directory, "page-${page.index + 1}.${source.extension.ifBlank { "jpg" }}").apply { source.copyTo(this, overwrite = true) }
        }
    }

    suspend fun share(context: Context, page: MangaPage, loader: MangaPageLoaderV2) = withContext(Dispatchers.IO) {
        val file = resolvePageFile(context, page, loader)
        val uri = FileProvider.getUriForFile(context, "com.kitsugi.animelist.fileprovider", file)
        val mime = if (file.extension == "txt") "text/plain" else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "image/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Okuduğun sayfayı paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun saveToGallery(context: Context, page: MangaPage, loader: MangaPageLoaderV2): File = withContext(Dispatchers.IO) {
        val sourceFile = resolvePageFile(context, page, loader)
        val filename = "kitsugi_${System.currentTimeMillis()}.${sourceFile.extension.ifBlank { "jpg" }}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/${sourceFile.extension.ifBlank { "jpeg" }}")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Kitsugi")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("Galeriye kaydedilemedi")
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kitsugi").apply { mkdirs() }
            val destFile = File(picturesDir, filename)
            sourceFile.copyTo(destFile, overwrite = true)
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = android.net.Uri.fromFile(destFile)
            }
            context.sendBroadcast(mediaScanIntent)
        }
        sourceFile
    }

    suspend fun setAsWallpaper(context: Context, page: MangaPage, loader: MangaPageLoaderV2) = withContext(Dispatchers.IO) {
        val file = resolvePageFile(context, page, loader)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw java.io.IOException("Görsel çözülemedi")
        val wallpaperManager = WallpaperManager.getInstance(context)
        wallpaperManager.setBitmap(bitmap)
    }
}
