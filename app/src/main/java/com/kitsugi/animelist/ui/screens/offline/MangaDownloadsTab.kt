package com.kitsugi.animelist.ui.screens.offline

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ChapterRecognition
import com.kitsugi.animelist.data.manga.download.*
import com.kitsugi.animelist.ui.screens.manga.components.rememberMangaDownloadPermissionGate
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MangaDownloadsTab(
    downloads: List<MangaDownload>,
    onOpen: (MangaDownload) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val manager = remember { MangaDownloadManager.get(context) }
    val scope = rememberCoroutineScope()
    val permissionGate = rememberMangaDownloadPermissionGate()
    val accent = LocalKitsugiAccent.current
    var deleting by remember { mutableStateOf<MangaDownload?>(null) }
    var busy by remember { mutableStateOf(setOf<String>()) }
    val groups = remember(downloads) {
        downloads.groupBy { listOf(it.sourceKey, it.sourceName, it.sourceLang, it.mangaUrl) }.values
            .sortedBy { it.first().mangaTitle.lowercase(Locale.ROOT) }
    }
    fun perform(record: MangaDownload, action: suspend () -> Unit) {
        if (record.id in busy) return
        busy = busy + record.id
        scope.launch {
            try { action() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Toast.makeText(context, e.message ?: context.getString(R.string.manga_download_failed), Toast.LENGTH_LONG).show()
            } finally { busy = busy - record.id }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "manga_download_summary") {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(stringResource(R.string.manga_download_storage_summary, downloads.size,
                    String.format(Locale.ROOT, "%.1f", downloads.sumOf { it.sizeBytes } / (1024.0 * 1024))),
                    fontWeight = FontWeight.Bold, color = accent)
                Text(stringResource(R.string.manga_download_folder_hint), fontSize = 12.sp, color = KitsugiColors.TextMuted)
            }
        }
        if (downloads.isEmpty()) item(key = "manga_empty") {
            Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(56.dp), tint = accent)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.manga_download_empty), color = KitsugiColors.TextSecondary)
            }
        }
        groups.forEach { group ->
            val first = group.first()
            item(key = "manga_group_${first.sourceKey}_${first.sourceName}_${first.sourceLang}_${first.mangaUrl}") {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(first.mangaTitle, fontWeight = FontWeight.Bold, color = KitsugiColors.TextPrimary)
                    Text("${first.sourceName} · ${group.count { it.status == MangaDownloadStatus.Complete }}/${group.size}",
                        fontSize = 12.sp, color = KitsugiColors.TextSecondary)
                }
            }
            items(group.sortedBy {
                ChapterRecognition.parseChapterNumber(it.mangaTitle, it.chapterName, it.chapterNumber.toDouble())
            }, key = { it.id }) { record ->
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(record.chapterName, fontWeight = FontWeight.SemiBold, color = KitsugiColors.TextPrimary)
                        Text("${record.status.label()} · ${record.completedPages}/${record.totalPages}", fontSize = 12.sp, color = accent)
                        if (record.totalPages > 0) {
                            LinearProgressIndicator(progress = { record.completedPages.toFloat() / record.totalPages },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = accent)
                        }
                        record.error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            val enabled = record.id !in busy
                            if (record.status == MangaDownloadStatus.Complete) {
                                TextButton(onClick = { onOpen(record) }, enabled = enabled) {
                                    Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.manga_download_read_offline))
                                }
                            }
                            IconButton(onClick = { openMangaDownloadFolder(context, record.relativePath) }) {
                                Icon(Icons.Rounded.FolderOpen, stringResource(R.string.manga_download_open_folder), tint = accent)
                            }
                            val active = record.status in setOf(MangaDownloadStatus.Downloading, MangaDownloadStatus.Queued)
                            IconButton(enabled = enabled, onClick = {
                                if (active) perform(record) { manager.pause(record.id) }
                                else permissionGate { perform(record) { manager.resume(record.id) } }
                            }) {
                                Icon(if (active) Icons.Rounded.Pause else Icons.Rounded.Refresh,
                                    stringResource(if (active) R.string.manga_download_pause else R.string.manga_download_resume), tint = accent)
                            }
                            IconButton(onClick = { deleting = record }, enabled = enabled) {
                                Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.manga_download_delete), tint = KitsugiColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.manga_download_delete)) },
            text = { Text(stringResource(R.string.manga_download_delete_confirm, record.mangaTitle, record.chapterName)) },
            confirmButton = { TextButton(onClick = {
                deleting = null
                perform(record) { manager.delete(record.id) }
            }) { Text(stringResource(R.string.manga_download_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun MangaDownloadStatus.label() = stringResource(when (this) {
    MangaDownloadStatus.Queued -> R.string.manga_download_status_queued
    MangaDownloadStatus.Downloading -> R.string.manga_download_status_downloading
    MangaDownloadStatus.Paused -> R.string.manga_download_status_paused
    MangaDownloadStatus.Error -> R.string.manga_download_status_error
    MangaDownloadStatus.Complete -> R.string.manga_download_status_complete
})

/** Use a documents URI, never relax StrictMode or expose file:// paths to other apps. */
fun openMangaDownloadFolder(context: Context, relativePath: String = "Pictures/Kitsugi/Manga/") {
    val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:${relativePath.trimEnd('/')}")
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(view) }
    catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { Toast.makeText(context, relativePath, Toast.LENGTH_LONG).show() }
    }
}
