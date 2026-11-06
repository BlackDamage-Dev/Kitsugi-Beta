package com.kitsugi.animelist.ui.screens.manga

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The file picker grants access to a single user-selected document. */
@Composable
internal fun NovelBookmarkExportButton(
    bookmarks: List<NovelBookmark>,
    foreground: Color,
    label: (NovelBookmark) -> String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val heading = stringResource(R.string.reader_bookmarks, bookmarks.size)
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val payload = pending
        pending = null
        if (uri != null && payload != null) scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
                    true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) { false }
            }
            Toast.makeText(context, if (success) R.string.reader_bookmark_export_success else R.string.reader_bookmark_export_error, Toast.LENGTH_SHORT).show()
        }
    }
    TextButton(enabled = bookmarks.isNotEmpty(), onClick = {
        pending = buildNovelBookmarkExport(heading, bookmarks, label)
        launcher.launch("Kitsugi-notes.txt")
    }) { Text(stringResource(R.string.reader_bookmark_export), color = foreground) }
}
