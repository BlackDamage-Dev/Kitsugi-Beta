package com.kitsugi.animelist.ui.screens.list

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * V2-F03 – CustomListEditorDialog
 *
 * AniList özel liste oluşturma/düzenleme/silme dialogu.
 * AniHyou CustomListEditorDialog referans alındı.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomListEditorDialog(
    customLists: List<String>,
    onCreateList: (String) -> Unit,
    onRenameList: (oldName: String, newName: String) -> Unit,
    onDeleteList: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newListName by remember { mutableStateOf("") }
    var editingList by remember { mutableStateOf<String?>(null) }
    var editedName by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    KitsugiSheetOrDialog(onDismiss = onDismiss, heightFraction = 0.75f, innerScrollState = listState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ozel_listeler),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Close, contentDescription = stringResource(R.string.kapat))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // New list input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.inline_new_list_name_34ce687)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newListName.isNotBlank()) {
                                onCreateList(newListName.trim())
                                newListName = ""
                            }
                        }
                    )
                )
                FilledTonalIconButton(
                    onClick = {
                        if (newListName.isNotBlank()) {
                            onCreateList(newListName.trim())
                            newListName = ""
                        }
                    }
                ) {
                    Icon(Icons.Add, contentDescription = stringResource(R.string.ekle))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (customLists.isEmpty()) {
                Text(
                    text = stringResource(R.string.inline_no_custom_lists_yet_create_e9762f7),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    itemsIndexed(customLists, key = { index, name -> "${name}_$index" }) { _, listName ->
                        CustomListRow(
                            name = listName,
                            isEditing = editingList == listName,
                            editedName = editedName,
                            onEditedNameChange = { editedName = it },
                            onEditClick = {
                                editingList = listName
                                editedName = listName
                            },
                            onSaveEdit = {
                                if (editedName.isNotBlank() && editedName != listName) {
                                    onRenameList(listName, editedName.trim())
                                }
                                editingList = null
                            },
                            onCancelEdit = { editingList = null },
                            onDeleteClick = { onDeleteList(listName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_ok), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CustomListRow(
    name: String,
    isEditing: Boolean,
    editedName: String,
    onEditedNameChange: (String) -> Unit,
    onEditClick: () -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = onEditedNameChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSaveEdit() })
                )
                IconButton(onClick = onSaveEdit) {
                    Icon(Icons.Add, contentDescription = stringResource(R.string.kaydet))
                }
                IconButton(onClick = onCancelEdit) {
                    Icon(Icons.Close, contentDescription = stringResource(R.string.iptal))
                }
            } else {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Edit,
                        contentDescription = stringResource(R.string.duzenle),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
