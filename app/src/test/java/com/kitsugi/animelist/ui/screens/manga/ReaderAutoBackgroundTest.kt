package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.ui.screens.manga.components.readerAverageEdgeColor
import org.junit.Assert.*
import org.junit.Test

class ReaderAutoBackgroundTest {
    @Test fun samplesEdgesAndIgnoresCenterAndTransparency() {
        val white = 0xffffffff.toInt()
        val red = 0xffff0000.toInt()
        assertEquals(white, readerAverageEdgeColor(intArrayOf(white, white, white, white, red, white, white, white, white), 3, 3))
        assertEquals(red, readerAverageEdgeColor(intArrayOf(red, 0x00000000, red), 3, 1))
        assertNull(readerAverageEdgeColor(intArrayOf(0, 0), 2, 1))
    }
    @Test fun returnsNullForInvalidBounds() {
        assertNull(readerAverageEdgeColor(intArrayOf(1), 2, 2))
        assertNull(readerAverageEdgeColor(intArrayOf(), Int.MAX_VALUE, Int.MAX_VALUE))
        assertNull(readerAverageEdgeColor(intArrayOf(), 0, 0))
    }
}
