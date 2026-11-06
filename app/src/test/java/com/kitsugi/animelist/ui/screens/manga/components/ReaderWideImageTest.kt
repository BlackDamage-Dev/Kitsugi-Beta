package com.kitsugi.animelist.ui.screens.manga.components

import org.junit.Assert.*
import org.junit.Test

class ReaderWideImageTest {
    @Test fun onlyClearlyWideImagesRotateInSelectedDirection() {
        assertEquals(0, readerWideRotationDegrees(100, 100, "Clockwise"))
        assertEquals(0, readerWideRotationDegrees(119, 100, "Clockwise"))
        assertEquals(90, readerWideRotationDegrees(120, 100, "Clockwise"))
        assertEquals(-90, readerWideRotationDegrees(180, 100, "CounterClockwise"))
        assertEquals(0, readerWideRotationDegrees(180, 100, "Off"))
        assertEquals(0, readerWideRotationDegrees(180, 0, "Clockwise"))
        assertEquals(0, readerWideRotationDegrees(Int.MAX_VALUE, Int.MAX_VALUE, "Clockwise"))
    }
}
