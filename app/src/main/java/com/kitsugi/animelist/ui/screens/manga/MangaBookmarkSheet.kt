package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaBookmark
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaBookmarkSheet(
    bookmarks: List<MangaBookmark>,
    onDismiss: () -> Unit,
    onOpen: (MangaBookmark) -> Unit,
    onDelete: (MangaBookmark) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF17171F)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.okuyucu_bookmarks), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (bookmarks.isEmpty()) {
                Text(stringResource(R.string.okuyucu_no_bookmarks), color = Color.White.copy(0.6f), modifier = Modifier.padding(vertical = 32.dp))
            } else LazyColumn(modifier = Modifier.padding(vertical = 12.dp)) {
                items(bookmarks, key = { "${it.chapterUrl}:${it.pageIndex}" }) { bookmark ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(bookmark) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bookmark.chapterName, color = Color.White, maxLines = 1)
                            Text(stringResource(R.string.okuyucu_page_number, bookmark.pageIndex + 1), color = Color.White.copy(0.6f), fontSize = 12.sp)
                        }
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.geri), tint = Color.White.copy(0.6f))
                        }
                    }
                }
            }
        }
    }
}
