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
        assertEquals(40f, autoScrollPixelsPerSecond(40, 1f), 0.001f)
        assertEquals(80f, autoScrollPixelsPerSecond(40, 2f), 0.001f)
        assertEquals(10f, autoScrollPixelsPerSecond(0, 1f), 0.001f)
        assertEquals(500f, autoScrollPixelsPerSecond(500, 1f), 0.001f)
        assertEquals(500f, autoScrollPixelsPerSecond(1000, 1f), 0.001f)
        assertEquals(0f, autoScrollPixelsPerSecond(40, -1f), 0.001f)
    }

    @Test fun autoScrollFrameDeltaTracksElapsedTimeAndClampsHitches() {
        // 120 px/sn × 16.67 ms ≈ 2 px (60 Hz kare); 8.33 ms ≈ 1 px (120 Hz kare).
        assertEquals(2f, autoScrollFramePixels(120f, 16_666_667L), 0.01f)
        assertEquals(1f, autoScrollFramePixels(120f, 8_333_333L), 0.01f)
        // Negatif süre (saat gerilemesi) sıfır üretir; uzun duraksama 100 ms'e
        // kelepçelenir ki tek karede büyük sıçrama olmasın.
        assertEquals(0f, autoScrollFramePixels(120f, -5L), 0.001f)
        assertEquals(12f, autoScrollFramePixels(120f, 1_000_000_000L), 0.001f)
        assertEquals(0f, autoScrollFramePixels(0f, 16_000_000L), 0.001f)
        // Kesirli piksel birikimi: 10 kare × 0.5 px = 5 px (carry ile korunur).
        assertEquals(5f, (1..10).sumOf { autoScrollFramePixels(30f, 16_666_667L).toDouble() }.toFloat(), 0.01f)
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
