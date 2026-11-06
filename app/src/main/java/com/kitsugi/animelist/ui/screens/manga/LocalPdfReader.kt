package com.kitsugi.animelist.ui.screens.manga

import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
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
import com.kitsugi.animelist.ui.theme.gradient.Text
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private class LocalPdfViewModelOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
private data class OpenPdf(val source: LocalPdfSource, val chapter: MangaChapter, val details: MangaDetails)

@Composable
internal fun LocalPdfReader(uri: Uri, tv: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owner = remember(uri) { LocalPdfViewModelOwner() }
    var pdf by remember(uri) { mutableStateOf<OpenPdf?>(null) }
    var error by remember(uri) { mutableStateOf(false) }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    LaunchedEffect(uri) {
        var temp: File? = null
        try {
            val opened = withContext(Dispatchers.IO) {
                val file = File.createTempFile("local-pdf-", ".pdf", context.cacheDir)
                temp = file
                copyLocalComic(context, uri, file)
                val count = withLocalPdf(file) { it.pageCount }
                require(count in 1..10_000) { "Unsupported PDF page count" }
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val length = input.read(buffer)
                        if (length < 0) break
                        if (length > 0) digest.update(buffer, 0, length)
                    }
                }
                val checksum = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val identity = readerSeriesId(uri.toString(), checksum)
                val title = localComicDisplayName(context, uri, "PDF")
                val url = "local-pdf://document/$identity"
                val details = MangaDetails(url = url, title = title, source = "Local PDF")
                val chapter = MangaChapter(url = "$url/chapter", name = title, mangaUrl = url)
                OpenPdf(LocalPdfSource(file, identity, chapter, details, count), chapter, details)
            }
            pdf = opened
            awaitCancellation()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = true
        } finally { temp?.delete() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val current = pdf
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
                if (error) Text(stringResource(R.string.reader_pdf_error)) else CircularProgressIndicator()
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_txt_close)) }
            }
        }
    }
}
