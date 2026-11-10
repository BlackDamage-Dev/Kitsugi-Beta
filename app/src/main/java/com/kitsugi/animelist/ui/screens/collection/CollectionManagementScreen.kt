package com.kitsugi.animelist.ui.screens.collection
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * V2-D04 – CollectionManagementScreen
 *
 * Top-level screen listing all user collections/folders with
 * create, reorder and delete controls.
 */
data class CollectionFolder(
    val id: String,
    val name: String,
    val emoji: String,
    val itemCount: Int,
    val coverUrl: String? = null,
    val isPrivate: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionManagementScreen(
    collections: List<CollectionFolder>,
    onCreateCollection: () -> Unit,
    onOpenFolder: (CollectionFolder) -> Unit,
    onDeleteFolder: (CollectionFolder) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf<CollectionFolder?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inline_my_collections_ff95e62), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.ArrowBack, contentDescription = stringResource(R.string.geri))
                    }
                },
                actions = {
                    IconButton(onClick = onCreateCollection) {
                        Icon(Icons.Add, contentDescription = stringResource(R.string.yeni_koleksiyon))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateCollection) {
                Icon(Icons.Add, contentDescription = stringResource(R.string.yeni_koleksiyon))
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (collections.isEmpty()) {
            // T2-05: Standardized empty state using KitsugiEmptyState
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                KitsugiEmptyState(
                    title = stringResource(R.string.henuz_koleksiyon_yok),
                    subtitle = stringResource(R.string.favori_iceriklerini_duzenlemek_icin_koleksiyon_olustur),
                    icon = Icons.FolderOpen,
                    actionText = stringResource(R.string.koleksiyon_olustur),
                    onActionClick = onCreateCollection
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "${collections.size} koleksiyon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(collections, key = { it.id }) { folder ->
                    CollectionFolderRow(
                        folder = folder,
                        onClick = { onOpenFolder(folder) },
                        onDelete = { showDeleteDialog = folder }
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { folder ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.koleksiyonu_sil)) },
            text = { Text(stringResource(R.string.x_x_koleksiyonu_kalici_olarak_silinecek_icindeki_x_oge_listeden, folder.emoji, folder.name, folder.itemCount)) },
            confirmButton = {
                KitsugiButton(
                    onClick = { onDeleteFolder(folder); showDeleteDialog = null }
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.iptal)) }
            }
        )
    }
}

@Composable
private fun CollectionFolderRow(
    folder: CollectionFolder,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(folder.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (folder.isPrivate) {
                            Icon(Icons.Lock, contentDescription = "Gizli", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(stringResource(R.string.inline_1_s_items_2d83e8a, folder.itemCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row {
                IconButton(onClick = onDelete) {
                    Icon(Icons.DeleteOutline, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                }
                Icon(Icons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
