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
internal fun EpubChapterSeekBar(chapter: Int, chapterCount: Int, foreground: Color, onSeek: (Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(0f) }
    val current = if (chapterCount <= 1) 0f else chapter.coerceIn(0, chapterCount - 1).toFloat() / (chapterCount - 1)
    val shown = if (dragging) value else current
    val selected = epubChapterForFraction(shown, chapterCount)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.reader_epub_seek_chapter, selected + 1, chapterCount), color = foreground)
        Slider(value = shown, onValueChange = { value = it; dragging = true }, enabled = chapterCount > 1,
            onValueChangeFinished = { if (dragging) { onSeek(epubChapterForFraction(value, chapterCount)); dragging = false } })
    }
}
