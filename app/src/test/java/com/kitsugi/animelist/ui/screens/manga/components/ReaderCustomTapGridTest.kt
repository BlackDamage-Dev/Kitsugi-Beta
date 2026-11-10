package com.kitsugi.animelist.ui.screens.manga.components

import org.junit.Assert.*
import org.junit.Test

class ReaderCustomTapGridTest {
    @Test fun mapsNinePhysicalCellsAndInvertsOnlyNextPrevious() {
        val grid = "PMNXPXNMN"
        val expected = listOf(ReaderTapAction.PREVIOUS, ReaderTapAction.MENU, ReaderTapAction.NEXT,
            ReaderTapAction.NONE, ReaderTapAction.PREVIOUS, ReaderTapAction.NONE,
            ReaderTapAction.NEXT, ReaderTapAction.MENU, ReaderTapAction.NEXT)
        for (i in 0..8) {
            val x = (i % 3 + .5f) / 3f
            val y = (i / 3 + .5f) / 3f
            assertEquals(expected[i], readerTapAction("Custom", true, false, x, y, grid))
            val inverted = when (expected[i]) {
                ReaderTapAction.PREVIOUS -> ReaderTapAction.NEXT
                ReaderTapAction.NEXT -> ReaderTapAction.PREVIOUS
                else -> expected[i]
            }
            assertEquals(inverted, readerTapAction("Custom", false, true, x, y, grid))
        }
        assertEquals(ReaderTapAction.NEXT, readerTapAction("Custom", false, false, 1f, 1f, grid))
        assertEquals(ReaderTapAction.NONE, readerTapAction("Custom", false, false, .5f, .5f, DEFAULT_READER_LONG_TAP_GRID))
    }
    @Test fun longPressUsesCustomGridOnlyWhenConfigured() {
        // Custom düzen + dolu uzun-basma ızgarası → ızgara eylemi.
        assertEquals(ReaderTapAction.NEXT, readerLongTapAction("Custom", "XXXXXXXXN", false, false, 0.9f, 0.9f))
        assertEquals(ReaderTapAction.MENU, readerLongTapAction("Custom", "XXXXMXXXX", true, false, 0.5f, 0.5f))
        // Boş ızgara veya Default düzen → null (varsayılan uzun basma: menü/galeri).
        assertNull(readerLongTapAction("Custom", DEFAULT_READER_LONG_TAP_GRID, false, false, 0.5f, 0.5f))
        assertNull(readerLongTapAction("Default", "XXXXXXXXN", false, false, 0.9f, 0.9f))
        assertNull(readerLongTapAction("LeftHand", "XXXXXXXXN", false, false, 0.9f, 0.9f))
    }

    @Test fun cyclesBothDirectionsAndMaintainsMenuAvailability() {
        assertEquals("MMNPMNPMN", cycleReaderTapGrid(DEFAULT_READER_TAP_GRID, 0))
        assertEquals("XMNPMNPMN", cycleReaderTapGrid(DEFAULT_READER_TAP_GRID, 0, -1))
        assertEquals(DEFAULT_READER_TAP_GRID, cycleReaderTapGrid(DEFAULT_READER_TAP_GRID, 9))
        assertFalse(readerTapGridsKeepMenu("XXXXXXXXX", "XXXXXXXXX"))
        assertFalse(readerTapGridsKeepMenu("XXXXXXXXX", "XXXXMXXXX"))
        assertTrue(readerTapGridsKeepMenu("XXXXMXXXX", "XXXXXXXXX"))
        assertFalse(readerTapGridsKeepMenu("BAD", "XXXXMXXXX"))
        assertEquals(ReaderTapAction.PREVIOUS, readerTapAction("Custom", false, false, 0f, 0f, "invalid"))
    }
}
