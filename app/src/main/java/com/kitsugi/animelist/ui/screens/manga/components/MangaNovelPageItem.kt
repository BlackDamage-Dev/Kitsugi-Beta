package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.manga.MangaNovelFont
import com.kitsugi.animelist.data.manga.MangaNovelTextAlign
import com.kitsugi.animelist.data.manga.MangaReaderOptions
import com.kitsugi.animelist.ui.theme.gradient.Text

@Composable
fun MangaNovelPageItem(
    paragraphs: List<String>,
    pageIndex: Int,
    totalPages: Int,
    options: MangaReaderOptions,
    onCenterTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = options.readerTheme
    Box(
        modifier = modifier.fillMaxSize().background(Color(theme.backgroundArgb)).pointerInput(Unit) {
            detectTapGestures { onCenterTap() }
        },
    ) {
        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = options.novelMarginDp.dp, vertical = 32.dp)) {
                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        color = Color(theme.textArgb),
                        fontSize = options.novelFontSizeSp.sp,
                        lineHeight = (options.novelFontSizeSp * options.novelLineHeight).sp,
                        fontFamily = when (options.novelFontFamily) {
                            MangaNovelFont.Sans -> FontFamily.Default
                            MangaNovelFont.Serif -> FontFamily.Serif
                            MangaNovelFont.Monospace -> FontFamily.Monospace
                        },
                        textAlign = when (options.novelTextAlign) {
                            MangaNovelTextAlign.Start -> TextAlign.Start
                            MangaNovelTextAlign.Justify -> TextAlign.Justify
                            MangaNovelTextAlign.Center -> TextAlign.Center
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height((options.novelFontSizeSp * 0.75f).dp))
                }
            }
        }
        if (options.showProgressPercent) {
            Text(
                text = "${((pageIndex + 1) * 100 / totalPages.coerceAtLeast(1))}%",
                color = Color(theme.textArgb).copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }
    }
}
