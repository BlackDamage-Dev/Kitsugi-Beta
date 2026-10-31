package com.kitsugi.animelist.ui.screens.collection

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * V2-D04 – CollectionEditorScreen
 *
 * Create or edit a collection folder: name, emoji, description, privacy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionEditorScreen(
    existingFolder: CollectionFolder? = null,
    onSave: (name: String, emoji: String, description: String, isPrivate: Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditing = existingFolder != null

    var name by remember { mutableStateOf(existingFolder?.name ?: "") }
    var emoji by remember { mutableStateOf(existingFolder?.emoji ?: "📁") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(existingFolder?.isPrivate ?: false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.ui_collection_edit) else stringResource(R.string.ui_collection_new), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Close, contentDescription = stringResource(R.string.kapat))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { if (isValid) onSave(name.trim(), emoji, description.trim(), isPrivate) },
                        enabled = isValid
                    ) {
                        Text(if (isEditing) stringResource(R.string.action_save) else stringResource(R.string.pd_create), fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Emoji + Name row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedCard(
                    onClick = { showEmojiPicker = true },
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(emoji, style = MaterialTheme.typography.headlineMedium)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 50) name = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.inline_collection_name_765515f)) },
                    placeholder = { Text(stringResource(R.string.inline_e_g_favorite_anime_051af68)) },
                    supportingText = { Text("${name.length}/50") },
                    isError = name.isBlank() && name.isNotEmpty(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true
                )
            }

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 200) description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.inline_description_optional_29350f6)) },
                placeholder = { Text(stringResource(R.string.inline_a_short_description_of_this_597ab17)) },
                supportingText = { Text("${description.length}/200") },
                maxLines = 3
            )

            // Privacy
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = if (isPrivate) Icons.Lock else Icons.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Gizli Koleksiyon", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.inline_only_you_can_see_this_c337f96), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }

            // Quick emoji suggestions
            if (showEmojiPicker) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.inline_choose_emoji_85437bf), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val quickEmojis = listOf("📁", "⭐", "❤️", "🔥", "🎬", "🎌", "🏆", "🎭", "👾", "🌸", "⚔️", "🤖", "🌙", "🎵", "📺", "🎮")
                        @Suppress("UNUSED_VARIABLE")
                        val rows = quickEmojis.chunked(8)
                        quickEmojis.chunked(8).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { e ->
                                    TextButton(
                                        onClick = { emoji = e; showEmojiPicker = false },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(4.dp)
                                    ) {
                                        Text(e, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
