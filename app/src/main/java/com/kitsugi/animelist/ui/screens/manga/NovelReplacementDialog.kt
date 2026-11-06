package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.gradient.Text

@Composable
internal fun NovelReplacementDialog(foreground: Color, background: Color, busy: Boolean, error: Boolean,
                                    onApply: (String, String) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    var find by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().background(background).padding(16.dp)) {
            Text(stringResource(R.string.reader_replacement_description), color = foreground)
            OutlinedTextField(value = find, onValueChange = { if (it.length <= 100) find = it }, enabled = !busy,
                label = { Text(stringResource(R.string.reader_replacement_find), color = foreground) },
                textStyle = TextStyle(color = foreground), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = replacement, onValueChange = { if (it.length <= 100) replacement = it }, enabled = !busy,
                label = { Text(stringResource(R.string.reader_replacement_with), color = foreground) },
                textStyle = TextStyle(color = foreground), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (error) Text(stringResource(R.string.reader_replacement_error), color = foreground)
            Row {
                TextButton(enabled = !busy && find.isNotEmpty(), onClick = { onApply(find, replacement) }) {
                    Text(stringResource(R.string.reader_replacement_apply), color = foreground)
                }
                TextButton(enabled = !busy, onClick = onReset) { Text(stringResource(R.string.reader_replacement_reset), color = foreground) }
                TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.reader_txt_close), color = foreground) }
            }
        }
    }
}
