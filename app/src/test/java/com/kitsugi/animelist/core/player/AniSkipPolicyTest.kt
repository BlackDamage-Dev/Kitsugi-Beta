package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AniSkip/AnimeSkip segment politikasının sözleşmesi.
 *
 * Regresyon: kod bir dönem yalnızca `"outro"` türünü arıyordu, oysa hiçbir sağlayıcı bu türü
 * üretmiyor (AniSkip `ed`/`mixed-ed`, AnimeSkip `ending`). Sonuç: jeneriğe ulaşmak bölümü
 * tamamlamıyor, bitişte "sonraki bölüm" sayacı hiç başlamıyordu.
 */
class AniSkipPolicyTest {

    private data class Segment(val type: String, val start: Double, val end: Double)

    private fun outroStart(segments: List<Segment>): Double? = AniSkipPolicy.outroStartSec(
        intervals = segments,
        typeOf = { it.type },
        startOf = { it.start }
    )

    @Test
    fun `closing credits are recognised for every provider naming scheme`() {
        listOf("ed", "mixed-ed", "ending", "mixed-ending", "outro", "ED", " Ed ").forEach { type ->
            assertTrue("$type kapanış jeneriği sayılmalı", AniSkipPolicy.isOutroType(type))
        }
    }

    @Test
    fun `opening themes and recaps are not closing credits`() {
        listOf("op", "mixed-op", "opening", "intro", "recap", "").forEach { type ->
            assertFalse("$type kapanış jeneriği DEĞİL", AniSkipPolicy.isOutroType(type))
        }
        assertTrue(AniSkipPolicy.isIntroType("mixed-op"))
        assertTrue(AniSkipPolicy.isIntroType("opening"))
        assertFalse(AniSkipPolicy.isIntroType("recap"))
    }

    @Test
    fun `earliest closing credit wins so completion is not delayed`() {
        val segments = listOf(
            Segment("op", 15.0, 105.0),
            Segment("ed", 1320.0, 1410.0),
            Segment("mixed-ed", 1180.0, 1410.0)
        )
        assertEquals(1180.0, outroStart(segments)!!, 0.001)
    }

    @Test
    fun `episode without closing credits reports nothing`() {
        assertNull(outroStart(listOf(Segment("op", 10.0, 100.0), Segment("recap", 0.0, 10.0))))
        assertNull(outroStart(emptyList()))
    }

    @Test
    fun `zero start time is ignored so a broken timestamp cannot complete an episode instantly`() {
        assertNull(outroStart(listOf(Segment("ed", 0.0, 90.0))))
    }

    @Test
    fun `animeSkip wins per type and AniSkip fills the gaps`() {
        val ani = listOf(Segment("op", 10.0, 100.0), Segment("ed", 1300.0, 1400.0))
        val animeSkip = listOf(Segment("op", 12.0, 102.0))
        val merged = AniSkipPolicy.mergeIntervals(ani, animeSkip) { it.type }
        assertEquals(2, merged.size)
        assertEquals(12.0, merged.first { it.type == "op" }.start, 0.001)
        assertEquals(1300.0, merged.first { it.type == "ed" }.start, 0.001)
    }

    @Test
    fun `chapter guard only disables skip when the setting is on`() {
        assertTrue(AniSkipPolicy.shouldDisableOnChapters(hasChapters = true, disableOnChaptersSetting = true))
        assertFalse(AniSkipPolicy.shouldDisableOnChapters(hasChapters = true, disableOnChaptersSetting = false))
        assertFalse(AniSkipPolicy.shouldDisableOnChapters(hasChapters = false, disableOnChaptersSetting = true))
    }
}
