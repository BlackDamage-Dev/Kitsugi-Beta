package com.kitsugi.animelist.ui.screens.manga.components

import org.junit.Assert.*
import org.junit.Test

class ReaderCropBoundsTest {
    @Test fun findsSolidWhiteAndBlackMarginsWithSafetyPadding() {
        for ((border, ink) in listOf(0xffffffff.toInt() to 0xff000000.toInt(), 0xff000000.toInt() to 0xffffffff.toInt())) {
            val pixels = IntArray(100 * 100) { border }
            for (y in 20 until 80) for (x in 10 until 90) pixels[y * 100 + x] = ink
            assertEquals(ReaderCropBounds(8, 18, 92, 82), readerCropBounds(pixels, 100, 100))
        }
    }
    @Test fun blankImagesTransparencyAndMismatchedCornersAreLeftAlone() {
        val white = 0xffffffff.toInt()
        assertNull(readerCropBounds(IntArray(100 * 100) { white }, 100, 100))
        val transparent = IntArray(100 * 100) { 0x00ffffff }
        transparent[50 * 100 + 50] = 0xff000000.toInt()
        assertNull(readerCropBounds(transparent, 100, 100))
        val pixels = IntArray(100 * 100) { white }; pixels[0] = 0xff000000.toInt()
        assertNull(readerCropBounds(pixels, 100, 100))
        assertNull(readerCropBounds(IntArray(5), 100, 100))
    }
    @Test fun neverRemovesMoreThanQuarterOfAnySide() {
        val pixels = IntArray(100 * 100) { 0xffffffff.toInt() }
        pixels[50 * 100 + 50] = 0xff000000.toInt()
        val bounds = readerCropBounds(pixels, 100, 100)!!
        assertEquals(ReaderCropBounds(23, 23, 77, 77), bounds)
    }
}
