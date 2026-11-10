package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.ui.screens.manga.components.MangaReaderTapAction.*
import com.kitsugi.animelist.ui.screens.manga.components.readerTapAction
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaReaderTapActionTest {
    @Test fun horizontalModesHaveOppositePageDirections() {
        assertEquals(Previous, readerTapAction(ReadingMode.LeftToRight, 0.1f, 0.5f))
        assertEquals(Next, readerTapAction(ReadingMode.LeftToRight, 0.9f, 0.5f))
        assertEquals(Next, readerTapAction(ReadingMode.RightToLeft, 0.1f, 0.5f))
        assertEquals(Previous, readerTapAction(ReadingMode.RightToLeft, 0.9f, 0.5f))
    }

    @Test fun verticalModeUsesHeightNotWidth() {
        assertEquals(Previous, readerTapAction(ReadingMode.Vertical, 0.9f, 0.1f))
        assertEquals(Next, readerTapAction(ReadingMode.Vertical, 0.1f, 0.9f))
        assertEquals(Menu, readerTapAction(ReadingMode.Vertical, 0.1f, 0.5f))
    }

    @Test fun webtoonTapsNeverJumpPastLongPanels() {
        listOf(0.1f, 0.5f, 0.9f).forEach { x ->
            listOf(0.1f, 0.5f, 0.9f).forEach { y ->
                assertEquals(Menu, readerTapAction(ReadingMode.Webtoon, x, y))
            }
        }
    }

    @Test fun everyModeOpensMenuInCenter() {
        ReadingMode.entries.forEach { mode -> assertEquals(Menu, readerTapAction(mode, 0.5f, 0.5f)) }
    }
}
