package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.gradient.Text

@Composable
internal fun NovelSeekBar(percent: Int, foreground: Color, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(percent.coerceIn(0, 100).toFloat()) }
    val shown = if (dragging) value else percent.coerceIn(0, 100).toFloat()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.reader_novel_seek_percent, shown.toInt()), color = foreground)
        Slider(value = shown, onValueChange = { value = it; dragging = true }, valueRange = 0f..100f,
            onValueChangeFinished = { if (dragging) { onSeek(value); dragging = false } })
    }
}
