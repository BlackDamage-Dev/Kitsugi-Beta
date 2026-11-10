package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.manga.OpdsAcquisition
import com.kitsugi.animelist.data.manga.OpdsCatalogClient
import com.kitsugi.animelist.data.manga.OpdsCredentials
import com.kitsugi.animelist.data.manga.OpdsFeed
import com.kitsugi.animelist.data.manga.OpdsNavigationItem
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun OpdsCatalogDialog(
    filesDirectory: File,
    onDismiss: () -> Unit,
    onOpen: (File) -> Unit,
) {
    var address by remember { mutableStateOf("https://") }
    var searchQuery by remember { mutableStateOf("") }
    var currentFeed by remember { mutableStateOf<OpdsFeed?>(null) }
    val history = remember { mutableStateListOf<String>() }

    var showAuthFields by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var downloadingTitle by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val client = remember { OpdsCatalogClient() }
    val scope = rememberCoroutineScope()

    val credentials = remember(username, password) {
        if (username.isNotBlank()) OpdsCredentials(username, password) else null
    }

    fun loadUrl(targetUrl: String, addToHistory: Boolean = true) {
        loading = true
        error = null
        scope.launch {
            runCatching {
                client.fetchFeed(targetUrl, credentials)
            }.onSuccess { feed ->
                if (addToHistory && currentFeed != null) {
                    history.add(address)
                }
                address = targetUrl
                currentFeed = feed
            }.onFailure {
                error = it.message ?: "Katalog yüklenemedi"
            }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (history.isNotEmpty()) {
                    IconButton(onClick = {
                        val prev = history.removeAt(history.lastIndex)
                        loadUrl(prev, addToHistory = false)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
                Text(
                    text = currentFeed?.title?.takeIf { it.isNotBlank() } ?: "OPDS Kataloğu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Katalog URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showAuthFields = !showAuthFields }) {
                        Text(if (showAuthFields) "Kimlik Bilgilerini Gizle" else "Kimlik Doğrulama (Basic Auth)")
                    }
                    TextButton(enabled = !loading, onClick = { loadUrl(address, addToHistory = false) }) {
                        Text("Yükle")
                    }
                }

                AnimatedVisibility(visible = showAuthFields) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Kullanıcı Adı") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Parola") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Search Bar if supported by feed
                currentFeed?.searchTemplateUrl?.let { template ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Katalogda ara...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            enabled = !loading && searchQuery.isNotBlank(),
                            onClick = {
                                loading = true
                                error = null
                                scope.launch {
                                    runCatching {
                                        client.search(template, searchQuery, credentials)
                                    }.onSuccess {
                                        history.add(address)
                                        currentFeed = it
                                    }.onFailure {
                                        error = it.message
                                    }
                                    loading = false
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Ara")
                        }
                    }
                }

                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                downloadingTitle?.let { title ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("İndiriliyor: $title", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        if (downloadProgress != null && downloadProgress!! >= 0f) {
                            LinearProgressIndicator(progress = { downloadProgress!! }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                }

                currentFeed?.let { feed ->
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        // Navigation subsections
                        if (feed.navigationItems.isNotEmpty()) {
                            item {
                                Text(
                                    "Kategoriler / Bölümler",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(feed.navigationItems, key = { "nav_${it.url}" }) { navItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !loading) {
                                            loadUrl(navItem.url, addToHistory = true)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        Text(navItem.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        navItem.subtitle?.let {
                                            Text(it, fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                            item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
                        }

                        // Publications / acquisitions
                        if (feed.acquisitions.isNotEmpty()) {
                            item {
                                Text(
                                    "Kitaplar ve Yayınlar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(feed.acquisitions, key = { "acq_${it.url}" }) { pub ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !loading && downloadingTitle == null) {
                                            downloadingTitle = pub.title
                                            downloadProgress = 0f
                                            error = null
                                            scope.launch {
                                                runCatching {
                                                    client.download(pub, filesDirectory, credentials) { read, total ->
                                                        if (total > 0) downloadProgress = read.toFloat() / total
                                                    }
                                                }.onSuccess(onOpen).onFailure {
                                                    error = it.message
                                                }
                                                downloadingTitle = null
                                                downloadProgress = null
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                        Text(pub.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        pub.author?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                                        pub.summary?.take(120)?.let { Text(it, fontSize = 11.sp, color = Color.DarkGray, maxLines = 2) }
                                    }
                                }
                            }
                        }

                        // Pagination next link
                        feed.nextPageUrl?.let { nextUrl ->
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    TextButton(
                                        enabled = !loading,
                                        onClick = { loadUrl(nextUrl, addToHistory = true) }
                                    ) {
                                        Text("Sonraki Sayfa →")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}
