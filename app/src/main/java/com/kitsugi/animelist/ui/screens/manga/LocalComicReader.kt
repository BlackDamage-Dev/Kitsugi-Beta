package com.kitsugi.animelist.ui.screens.manga

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private class LocalComicViewModelOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

private data class OpenComic(val source: MangaSource, val chapter: MangaChapter, val details: MangaDetails)

/** SAF archive uses the same manga reader rather than a second reader implementation. */
@Composable
internal fun LocalComicReader(uri: Uri, tv: Boolean = false, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var comic by remember(uri) { mutableStateOf<OpenComic?>(null) }
    var error by remember(uri) { mutableStateOf(false) }
    val owner = remember(uri) { LocalComicViewModelOwner() }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    LaunchedEffect(uri) {
        var temp: File? = null
        try {
            val opened = withContext(Dispatchers.IO) {
                val file = File.createTempFile("local-comic-", ".cbz", context.cacheDir)
                temp = file
                copyLocalComic(context, uri, file)
                val rar = file.inputStream().use { input ->
                    val signature = ByteArray(7)
                    var n = 0
                    while (n < signature.size) { val count = input.read(signature, n, signature.size - n); if (count < 0) break; if (count > 0) n += count }
                    n == 7 && signature.contentEquals(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00)) ||
                        n == 7 && signature.contentEquals(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01))
                }
                val entries = if (rar) LocalRarComic.list(file) else LocalComicArchive.list(file)
                require(entries.isNotEmpty()) { "No readable image pages" }
                val title = localComicDisplayName(context, uri)
                val identity = readerSeriesId(uri.toString())
                val url = "local-comic://document/$identity"
                val details = MangaDetails(url = url, title = title, source = "Local comic")
                val chapter = MangaChapter(url = "${url}/chapter", name = title, mangaUrl = url)
                val source = if (rar) LocalRarComicSource(file, context.cacheDir, entries, identity, chapter, details)
                    else LocalComicSource(file, entries, identity, chapter, details)
                OpenComic(source, chapter, details)
            }
            comic = opened
            awaitCancellation()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = true
        } finally {
            temp?.delete()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val current = comic
        if (current != null) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                if (tv) {
                    com.kitsugi.animelist.ui.tv.manga.TvMangaReaderScreen(chapter = current.chapter, mangaDetails = current.details, source = current.source, onBack = onDismiss)
                } else {
                    MangaReaderScreen(chapter = current.chapter, mangaDetails = current.details, source = current.source, onBack = onDismiss)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (error) Text(stringResource(R.string.reader_comic_error)) else CircularProgressIndicator()
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_txt_close)) }
            }
        }
    }
}

internal fun localComicDisplayName(context: Context, uri: Uri, fallback: String = "Comic"): String {
    val name = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (_: Exception) { null }
    return name?.takeIf { it.isNotBlank() }?.take(150) ?: fallback
}

internal suspend fun copyLocalComic(context: Context, uri: Uri, file: File) {
    val operationContext = currentCoroutineContext()
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open archive")
    input.use { source ->
        file.outputStream().use { output ->
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                operationContext.ensureActive()
                val count = source.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= LocalComicArchive.MAX_ARCHIVE_BYTES) { "Archive too large" }
                output.write(buffer, 0, count)
            }
        }
    }
}
