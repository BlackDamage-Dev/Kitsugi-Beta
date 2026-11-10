package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.manga.MangaNovelFont
import com.kitsugi.animelist.data.manga.MangaNovelSpeechController
import com.kitsugi.animelist.data.manga.MangaNovelTextAlign
import com.kitsugi.animelist.data.manga.MangaReaderOptions
import com.kitsugi.animelist.ui.theme.gradient.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MangaNovelPageItem(
    paragraphs: List<String>,
    pageIndex: Int,
    totalPages: Int,
    options: MangaReaderOptions,
    highlightState: MangaNovelSpeechController.SpeechHighlightState? = null,
    onCenterTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = options.readerTheme
    val currentTime = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(theme.backgroundArgb))
            .pointerInput(Unit) {
                detectTapGestures { onCenterTap() }
            },
    ) {
        // Novel header indicator: Clock
        if (options.novelShowBatteryAndClock) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = options.novelMarginDp.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentTime,
                    color = Color(theme.textArgb).copy(alpha = 0.45f),
                    fontSize = 10.sp,
                )
            }
        }

        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = options.novelMarginDp.dp,
                        vertical = if (options.novelShowBatteryAndClock) 28.dp else 24.dp
                    )
            ) {
                paragraphs.forEachIndexed { pIndex, paragraph ->
                    if (paragraph.startsWith("[img:") && paragraph.endsWith("]")) {
                        val imgUri = paragraph.removeSurrounding("[img:", "]")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imgUri,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    } else {
                        val annotatedText = remember(paragraph, highlightState, options.bionicReading) {
                            buildAnnotatedParagraph(
                                paragraph = paragraph,
                                paragraphIndex = pIndex,
                                highlightState = highlightState,
                                bionicReading = options.bionicReading,
                                textColor = Color(theme.textArgb)
                            )
                        }

                        Text(
                            text = annotatedText,
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
        }

        if (options.showProgressPercent) {
            Text(
                text = "${((pageIndex + 1) * 100 / totalPages.coerceAtLeast(1))}%",
                color = Color(theme.textArgb).copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
            )
        }
    }
}

private fun buildAnnotatedParagraph(
    paragraph: String,
    paragraphIndex: Int,
    highlightState: MangaNovelSpeechController.SpeechHighlightState?,
    bionicReading: Boolean,
    textColor: Color,
): AnnotatedString {
    val isHighlighted = highlightState != null && highlightState.paragraphIndex == paragraphIndex
    val targetSentence = highlightState?.sentenceText?.trim().orEmpty()

    return buildAnnotatedString {
        if (isHighlighted && targetSentence.isNotEmpty()) {
            val startIdx = paragraph.indexOf(targetSentence)
            if (startIdx >= 0) {
                // Pre-highlight text
                if (startIdx > 0) {
                    appendStyledText(paragraph.substring(0, startIdx), bionicReading, textColor)
                }
                // Highlighted active sentence
                withStyle(
                    SpanStyle(
                        background = Color(0x557C4DFF),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(paragraph.substring(startIdx, startIdx + targetSentence.length))
                }
                // Post-highlight text
                val afterIdx = startIdx + targetSentence.length
                if (afterIdx < paragraph.length) {
                    appendStyledText(paragraph.substring(afterIdx), bionicReading, textColor)
                }
                return@buildAnnotatedString
            }
        }

        appendStyledText(paragraph, bionicReading, textColor)
    }
}

private fun AnnotatedString.Builder.appendStyledText(
    text: String,
    bionicReading: Boolean,
    textColor: Color,
) {
    if (!bionicReading) {
        withStyle(SpanStyle(color = textColor)) {
            append(text)
        }
        return
    }

    val words = text.split(" ")
    words.forEachIndexed { i, word ->
        if (word.isNotEmpty()) {
            val boldLen = if (word.length <= 3) 1 else (word.length + 1) / 2
            withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Bold)) {
                append(word.take(boldLen))
            }
            withStyle(SpanStyle(color = textColor)) {
                append(word.drop(boldLen))
            }
        }
        if (i < words.lastIndex) append(" ")
    }
}
