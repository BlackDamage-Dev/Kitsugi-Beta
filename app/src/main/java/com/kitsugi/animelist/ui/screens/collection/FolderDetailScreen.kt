package com.kitsugi.animelist.ui.screens.collection
import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * V2-D04 – FolderDetailScreen
 *
 * Shows the content of a single collection folder with a poster grid,
 * reorder mode and item removal controls.
 */
data class CollectionItem(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val mediaType: String,
    val year: Int?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folder: CollectionFolder,
    items: List<CollectionItem>,
    onNavigateBack: () -> Unit,
    onRemoveItem: (CollectionItem) -> Unit,
    onEditFolder: () -> Unit,
    onItemClick: (CollectionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var reorderMode by remember { mutableStateOf(false) }
    var itemToRemove by remember { mutableStateOf<CollectionItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(folder.emoji, style = MaterialTheme.typography.titleLarge)
                        Column {
                            Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.inline_1_s_items_2d83e8a, items.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.ArrowBack, contentDescription = stringResource(R.string.geri))
                    }
                },
                actions = {
                    if (reorderMode) {
                        TextButton(onClick = { reorderMode = false }) { Text("Bitti") }
                    } else {
                        IconButton(onClick = { reorderMode = true }) {
                            Icon(Icons.DragHandle, contentDescription = stringResource(R.string.sirayi_duzenle))
                        }
                        IconButton(onClick = onEditFolder) {
                            Icon(Icons.Edit, contentDescription = stringResource(R.string.klasoru_duzenle))
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.FolderOpen, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.inline_this_collection_is_empty_852ab9f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.inline_you_can_add_titles_from_a587f29), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(rememberKitsugiGridColumns()),
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    CollectionItemCard(
                        item = item,
                        reorderMode = reorderMode,
                        onClick = { if (!reorderMode) onItemClick(item) },
                        onRemove = { itemToRemove = item }
                    )
                }
            }
        }
    }

    itemToRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToRemove = null },
            title = { Text(stringResource(R.string.inline_remove_from_collection_7ac9883)) },
            text = { Text(stringResource(R.string.inline_remove_1_s_from_this_28bfbf1, item.title)) },
            confirmButton = {
                KitsugiButton(onClick = { onRemoveItem(item); itemToRemove = null }) { Text(KitsugiStrings.get(R.string.inline_remove_2b5bd53)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToRemove = null }) { Text(stringResource(R.string.iptal)) }
            }
        )
    }
}

@Composable
private fun CollectionItemCard(
    item: CollectionItem,
    reorderMode: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(2f / 3f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Poster placeholder — real impl uses AsyncImage(Coil)
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Movie, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Title overlay
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(4.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
            }

            if (reorderMode) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                ) {
                    Icon(Icons.RemoveCircle, contentDescription = stringResource(R.string.kaldir), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
