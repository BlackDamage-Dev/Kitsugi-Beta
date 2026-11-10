package com.kitsugi.animelist.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class MangaBrightnessTest {
    @Test fun clampsLegacyAndOutOfRangeValues() {
        assertEquals(0.1f, normalizeMangaBrightness(0f), 0f)
        assertEquals(0.1f, normalizeMangaBrightness(-1f), 0f)
        assertEquals(1f, normalizeMangaBrightness(2f), 0f)
        assertEquals(0.6f, normalizeMangaBrightness(0.6f), 0f)
    }

    @Test fun resetsNonFiniteValues() {
        assertEquals(1f, normalizeMangaBrightness(Float.NaN), 0f)
        assertEquals(1f, normalizeMangaBrightness(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, normalizeMangaBrightness(Float.NEGATIVE_INFINITY), 0f)
    }
}
