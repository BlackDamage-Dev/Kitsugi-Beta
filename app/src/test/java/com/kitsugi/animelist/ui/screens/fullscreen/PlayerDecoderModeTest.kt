package com.kitsugi.animelist.ui.screens.fullscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDecoderModeTest {

    @Test
    fun decoderEntriesContainExactlyFourOptions() {
        val entries = Decoder.entries
        assertEquals("Decoder entries should contain exactly 4 modes (Auto, SW, HW, HW+)", 4, entries.size)
        assertEquals(listOf(Decoder.Auto, Decoder.SW, Decoder.HW, Decoder.HWPlus), entries)
    }

    @Test
    fun decoderHasExactlyOneAutoTitle() {
        val autoTitles = Decoder.entries.filter { it.title == "Auto" }
        assertEquals("There should be exactly one Decoder entry titled 'Auto'", 1, autoTitles.size)
        assertEquals(Decoder.Auto, autoTitles.first())
    }

    @Test
    fun getDecoderFromValueResolvesValidValues() {
        assertEquals(Decoder.Auto, getDecoderFromValue("auto"))
        assertEquals(Decoder.SW, getDecoderFromValue("no"))
        assertEquals(Decoder.HW, getDecoderFromValue("mediacodec-copy"))
        assertEquals(Decoder.HWPlus, getDecoderFromValue("mediacodec"))
    }

    @Test
    fun getDecoderFromValueGracefullyFallsBackForLegacyOrInvalidValues() {
        assertEquals(Decoder.Auto, getDecoderFromValue("auto-copy"))
        assertEquals(Decoder.Auto, getDecoderFromValue("auto-safe"))
        assertEquals(Decoder.Auto, getDecoderFromValue(""))
        assertEquals(Decoder.Auto, getDecoderFromValue("unknown-mode"))
    }

    @Test
    fun decoderTitlesAndValuesMatchSpecifications() {
        assertEquals("Auto", Decoder.Auto.title)
        assertEquals("auto", Decoder.Auto.value)

        assertEquals("SW", Decoder.SW.title)
        assertEquals("no", Decoder.SW.value)

        assertEquals("HW", Decoder.HW.title)
        assertEquals("mediacodec-copy", Decoder.HW.value)

        assertEquals("HW+", Decoder.HWPlus.title)
        assertEquals("mediacodec", Decoder.HWPlus.value)
    }
}
