package com.kitsugi.animelist.ui.screens.manga

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class NovelSpeechVoiceOption(val id: String, val label: String, val networkRequired: Boolean)

@Composable
internal fun NovelSpeechVoicePicker(
    engine: TextToSpeech,
    selectedId: String,
    foreground: Color,
    background: Color,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val voices by produceState<List<NovelSpeechVoiceOption>?>(null, engine) {
        value = withContext(Dispatchers.IO) {
            try {
                engine.voices.orEmpty().map { voice ->
                    NovelSpeechVoiceOption(voice.name, voice.locale.getDisplayName(Locale.getDefault()), voice.isNetworkConnectionRequired)
                }.filter { it.id.isNotEmpty() }.distinctBy { it.id }.sortedWith(compareBy({ it.label }, { it.id }))
            } catch (_: Exception) { emptyList() }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).background(background).padding(16.dp)) {
            Text(stringResource(R.string.reader_tts_voice), color = foreground)
            TextButton(onClick = { onSelect(""); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text((if (selectedId.isEmpty()) "✓ " else "") + stringResource(R.string.reader_tts_voice_default), color = foreground)
            }
            if (voices == null) {
                Text(stringResource(R.string.reader_tts_voice_loading), color = foreground)
            } else if (voices.isNullOrEmpty()) {
                Text(stringResource(R.string.reader_tts_voice_empty), color = foreground)
            } else {
                val networkLabel = stringResource(R.string.reader_tts_voice_network)
                LazyColumn {
                    items(voices.orEmpty(), key = { it.id }) { voice ->
                        TextButton(onClick = { onSelect(voice.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                            val prefix = if (voice.id == selectedId) "✓ " else ""
                            val network = if (voice.networkRequired) " · $networkLabel" else ""
                            Text("$prefix${voice.label} · ${voice.id}$network", color = foreground)
                        }
                    }
                }
            }
        }
    }
}
