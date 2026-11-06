package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.gradient.Text

@Composable
internal fun NovelBookmarkDialog(
    bookmarks: List<NovelBookmark>,
    bookKey: String,
    kind: String,
    foreground: Color,
    background: Color,
    label: @Composable (NovelBookmark) -> String,
    exportLabel: (NovelBookmark) -> String,
    canNavigate: (NovelBookmark) -> Boolean = { true },
    onNavigate: (NovelBookmark) -> Unit,
    onChange: (List<NovelBookmark>) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<NovelBookmark?>(null) }
    var draft by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).background(background).padding(16.dp)) {
            Text(stringResource(R.string.reader_bookmarks, bookmarks.size), color = foreground)
            NovelBookmarkExportButton(bookmarks, foreground, exportLabel)
            NovelBookmarkBackupButtons(kind, bookKey, bookmarks, foreground, onChange)
            val target = editing
            if (target != null) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= MAX_BOOKMARK_NOTE) draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = foreground),
                    label = { Text(stringResource(R.string.reader_bookmark_note), color = foreground) },
                    minLines = 3,
                    maxLines = 8
                )
                Text(stringResource(R.string.reader_bookmark_note_limit, draft.length, MAX_BOOKMARK_NOTE), color = foreground)
                Row {
                    TextButton(onClick = { onChange(updateNovelBookmarkNote(bookmarks, target, draft)); editing = null }) {
                        Text(stringResource(R.string.reader_bookmark_save_note), color = foreground)
                    }
                    TextButton(onClick = { editing = null }) { Text(stringResource(R.string.reader_bookmark_cancel_note), color = foreground) }
                }
            } else {
                LazyColumn {
                    items(bookmarks, key = { "${it.index}:${it.offset}" }) { mark ->
                        Column {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(modifier = Modifier.weight(1f), enabled = canNavigate(mark), onClick = { onNavigate(mark) }) {
                                    Text(label(mark), color = foreground)
                                }
                                TextButton(onClick = { editing = mark; draft = mark.note }) {
                                    Text(stringResource(R.string.reader_bookmark_note), color = foreground)
                                }
                                TextButton(onClick = { onChange(bookmarks - mark) }) {
                                    Text(stringResource(R.string.reader_bookmark_delete), color = foreground)
                                }
                            }
                            if (mark.note.isNotBlank()) Text(mark.note, color = foreground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
