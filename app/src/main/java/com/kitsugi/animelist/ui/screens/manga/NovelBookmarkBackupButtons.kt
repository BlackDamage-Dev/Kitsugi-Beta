package com.kitsugi.animelist.ui.screens.manga

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

@Composable
internal fun NovelBookmarkBackupButtons(kind: String, bookKey: String, bookmarks: List<NovelBookmark>, foreground: Color, onChange: (List<NovelBookmark>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestBookmarks by rememberUpdatedState(bookmarks)
    val latestOnChange by rememberUpdatedState(onChange)
    var pending by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val data = pending; pending = null
        if (uri != null && data != null) scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                    out.bufferedWriter(Charsets.UTF_8).use { it.write(data) }; true
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
            }
            Toast.makeText(context, if (success) R.string.reader_bookmark_export_success else R.string.reader_bookmark_export_error, Toast.LENGTH_SHORT).show()
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = withContext(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { readBoundedNovel(it, 800_200) } ?: return@withContext null
                    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    decodeNovelBookmarkBackup(text, kind, bookKey)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
            }
            if (imported == null) Toast.makeText(context, R.string.reader_bookmark_restore_error, Toast.LENGTH_LONG).show()
            else {
                val old = latestBookmarks
                val merged = mergeNovelBookmarks(old, imported)
                latestOnChange(merged)
                Toast.makeText(context, context.getString(R.string.reader_bookmark_restore_result, merged.size - old.size), Toast.LENGTH_LONG).show()
            }
        }
    }
    Row {
        TextButton(enabled = bookmarks.isNotEmpty(), onClick = {
            pending = encodeNovelBookmarkBackup(kind, bookKey, bookmarks)
            save.launch("Kitsugi-bookmarks.kitsugi.txt")
        }) { Text(stringResource(R.string.reader_bookmark_backup), color = foreground) }
        TextButton(onClick = { restore.launch(arrayOf("text/plain", "application/octet-stream")) }) {
            Text(stringResource(R.string.reader_bookmark_restore), color = foreground)
        }
    }
}
