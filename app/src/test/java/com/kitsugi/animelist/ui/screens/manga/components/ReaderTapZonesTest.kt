package com.kitsugi.animelist.ui.screens.manga.components

import com.kitsugi.animelist.data.manga.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapZonesTest {
    @Test fun volumeKeysCanBeInverted() {
        assertEquals(true, volumeKeyIsNext(isVolumeDown = true, inverted = false))
        assertEquals(false, volumeKeyIsNext(isVolumeDown = false, inverted = false))
        assertEquals(false, volumeKeyIsNext(isVolumeDown = true, inverted = true))
        assertEquals(true, volumeKeyIsNext(isVolumeDown = false, inverted = true))
    }

    @Test fun autoScrollRateIsDensityIndependentAndBounded() {
        assertEquals(2f, autoScrollStepPixels(40, 1f), 0.001f)
        assertEquals(4f, autoScrollStepPixels(40, 2f), 0.001f)
        assertEquals(0.5f, autoScrollStepPixels(0, 1f), 0.001f)
        assertEquals(8f, autoScrollStepPixels(500, 1f), 0.001f)
    }

    @Test fun webtoonDimensionsAreValidatedAndBounded() {
        assertEquals(null, webtoonAspectRatio(0, 100))
        assertEquals(null, webtoonAspectRatio(100, -1))
        assertEquals(0.5f, webtoonAspectRatio(100, 200)!!, 0.0001f)
        assertEquals(0.02f, webtoonAspectRatio(1, 10000)!!, 0.0001f)
        assertEquals(100f, webtoonAspectRatio(10000, 1)!!, 0.0001f)
    }

    @Test fun layoutsPreserveDefaultAndSupportOneHand() {
        for (rtl in listOf(false, true)) for (inverted in listOf(false, true)) {
            assertEquals(ReaderTapAction.MENU, readerTapAction("Default", rtl, inverted, 0.5f, 0.5f))
            val expected = if (rtl != inverted) ReaderTapAction.NEXT else ReaderTapAction.PREVIOUS
            assertEquals(expected, readerTapAction("Default", rtl, inverted, 0.1f, 0.5f))
            assertEquals(ReaderTapAction.MENU, readerTapAction("LeftHand", rtl, inverted, 0.1f, 0.1f))
            assertEquals(ReaderTapAction.MENU, readerTapAction("RightHand", rtl, inverted, 0.9f, 0.1f))
            assertEquals(if (inverted) ReaderTapAction.PREVIOUS else ReaderTapAction.NEXT,
                readerTapAction("LeftHand", rtl, inverted, 0.9f, 0.9f))
        }
        assertEquals(ReaderTapAction.MENU, readerTapAction("Default", false, false, Float.NaN, 0.5f))
    }

    @Test fun webtoonTapUsesVisibleViewportRatherThanLongImage() {
        val viewport = ReaderTapViewport(top = 100f, height = 800f)
        // The image starts above the window; the tap remains in the lower half of the viewport.
        assertEquals(0.75f, readerTapY(offset = 1200f, itemTop = -500f, viewport = viewport, itemHeight = 5000), 0.001f)
        assertEquals(0.5f, readerTapY(offset = 250f, itemTop = 0f, viewport = null, itemHeight = 500), 0.001f)
    }

    @Test fun sideTapsFollowDirectionAndCanBeInverted() {
        for (mode in ReadingMode.values()) {
            for (inverted in listOf(false, true)) {
                val expectedLeft = (mode == ReadingMode.RightToLeft) != inverted
                assertEquals(expectedLeft, readerTapAction("Default", mode == ReadingMode.RightToLeft, inverted, 0.1f, 0.5f) == ReaderTapAction.NEXT)
                assertEquals(!expectedLeft, readerTapAction("Default", mode == ReadingMode.RightToLeft, inverted, 0.9f, 0.5f) == ReaderTapAction.NEXT)
            }
        }
    }
}
