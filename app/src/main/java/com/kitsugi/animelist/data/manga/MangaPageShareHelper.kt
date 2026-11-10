package com.kitsugi.animelist.data.manga

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MangaPageShareHelper {
    suspend fun share(context: Context, page: MangaPage, loader: MangaPageLoaderV2) = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "reader-share").apply { mkdirs() }
        val file = page.textParagraphs?.let { paragraphs ->
            File(directory, "chapter-${page.index + 1}.txt").apply { writeText(paragraphs.joinToString("\n\n")) }
        } ?: run {
            loader.loadPage(page)
            val imageUrl = page.imageUrl ?: page.url
            val source = loader.cache.getImageFile(imageUrl).takeIf { it.exists() }
                ?: if (imageUrl.startsWith("file:")) File(java.net.URI(imageUrl)) else null
                ?: throw java.io.IOException("Paylaşılacak sayfa henüz hazır değil")
            File(directory, "page-${page.index + 1}.${source.extension.ifBlank { "jpg" }}").apply { source.copyTo(this, overwrite = true) }
        }
        val uri = FileProvider.getUriForFile(context, "com.kitsugi.animelist.fileprovider", file)
        val mime = if (file.extension == "txt") "text/plain" else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "image/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Okuduğun sayfayı paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
