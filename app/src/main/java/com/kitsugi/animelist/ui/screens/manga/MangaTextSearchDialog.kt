package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.ui.theme.gradient.Text

@Composable
fun MangaTextSearchDialog(pages: List<MangaPage>, onDismiss: () -> Unit, onOpen: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, pages) {
        if (query.trim().length < 2) emptyList() else pages.flatMap { page ->
            page.textParagraphs.orEmpty().mapNotNull { paragraph ->
                val index = paragraph.indexOf(query.trim(), ignoreCase = true)
                if (index < 0) null else Triple(page.index, paragraph, index)
            }
        }.take(100)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bölümde ara") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("En az 2 karakter") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (query.trim().length >= 2 && results.isEmpty()) Text("Eşleşme bulunamadı", modifier = Modifier.padding(vertical = 16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(results, key = { "${it.first}:${it.third}:${it.second.hashCode()}" }) { result ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(result.first) }.padding(vertical = 10.dp)) {
                            Text("Sayfa ${result.first + 1}")
                            Text(result.second, maxLines = 3)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}
