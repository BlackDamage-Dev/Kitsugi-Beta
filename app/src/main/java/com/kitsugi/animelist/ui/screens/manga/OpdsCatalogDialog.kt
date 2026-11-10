package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.manga.OpdsAcquisition
import com.kitsugi.animelist.data.manga.OpdsCatalogClient
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun OpdsCatalogDialog(filesDirectory: File, onDismiss: () -> Unit, onOpen: (File) -> Unit) {
    var address by remember { mutableStateOf("https://") }
    var publications by remember { mutableStateOf<List<OpdsAcquisition>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val client = remember { OpdsCatalogClient() }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OPDS kataloğu") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Katalog URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = !loading, onClick = {
                    loading = true; error = null
                    scope.launch {
                        runCatching { client.fetch(address) }.onSuccess { publications = it }
                            .onFailure { error = it.message }
                        loading = false
                    }
                }) { Text("Kataloğu yükle") }
                error?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
                if (loading) CircularProgressIndicator()
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(publications, key = { it.url }) { publication ->
                        Column(modifier = Modifier.fillMaxWidth().clickable(enabled = !loading) {
                            loading = true; error = null
                            scope.launch {
                                runCatching { client.download(publication, filesDirectory) }
                                    .onSuccess(onOpen).onFailure { error = it.message }
                                loading = false
                            }
                        }.padding(vertical = 12.dp)) {
                            Text(publication.title)
                            publication.author?.let { Text(it) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}
