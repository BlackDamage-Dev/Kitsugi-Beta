package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Akış başlıklarından Dolby Vision ipucu çıkarma testleri.
 *
 * Torrentio/Stremio başlıkları gerçek dünyada yukarıdaki biçimlerde gelir;
 * yanlış pozitif (DVD, DVDRip) veya yanlış negatif (DV HDR10) renk düzeltme
 * yolunu bozar.
 */
class DolbyVisionStreamHintsTest {

    @Test
    fun `plain dv tag is detected without a profile`() {
        val hint = DolbyVisionStreamHints.detect(
            "Torrentio · VisionQuest S01E01 No Strings 2160p DSNP WEB-DL DDP5.1 Atmos DV HDR10"
        )
        assertTrue(hint != null)
        assertNull(hint!!.profile)
        assertTrue(hint.isProfileUnknown)
    }

    @Test
    fun `dolby vision phrase is detected`() {
        val hint = DolbyVisionStreamHints.detect("Movie 2026 2160p WEB-DL Dolby Vision DDP 5.1")
        assertTrue(hint != null)
        assertEquals("Dolby Vision", hint!!.matched)
    }

    @Test
    fun `dovi spelling is detected`() {
        val hint = DolbyVisionStreamHints.detect("4K DoVi HDR10+ DDP5.1")
        assertTrue(hint != null)
    }

    @Test
    fun `explicit codec string wins and yields the profile`() {
        val hint = DolbyVisionStreamHints.detect("show.s01e01.2160p.dvh1.05.mkv")
        assertEquals(5, hint?.profile)
        assertTrue(hint!!.isProfile5)
    }

    @Test
    fun `two digit codec profile is normalised`() {
        assertEquals(7, DolbyVisionStreamHints.detect("remux dvhe.07.06")?.profile)
        assertEquals(8, DolbyVisionStreamHints.detect("web dvhe.08.06")?.profile)
    }

    @Test
    fun `dv5 profile tag is detected`() {
        val hint = DolbyVisionStreamHints.detect("Show S01E01 2160p DV5 DDP5.1")
        assertEquals(5, hint?.profile)
    }

    @Test
    fun `profile tag with hdr10+ still resolves the dv profile`() {
        val hint = DolbyVisionStreamHints.detect("4K DV | HDR10+ | 5.56 GB")
        assertTrue(hint != null)
        assertNull(hint!!.profile)
    }

    @Test
    fun `dvd and dvdrip are not dolby vision`() {
        assertNull(DolbyVisionStreamHints.detect("Movie 2004 DVDRip XviD"))
        assertNull(DolbyVisionStreamHints.detect("Movie 2004 DVD5 PAL"))
    }

    @Test
    fun `hdr10 only streams are not dolby vision`() {
        assertNull(DolbyVisionStreamHints.detect("Show S01E01 2160p WEB-DL HDR10+ DDP5.1"))
        assertNull(DolbyVisionStreamHints.detect("Show S01E01 1080p WEB-DL DDP5.1"))
    }

    @Test
    fun `blank and null candidates are skipped`() {
        assertNull(DolbyVisionStreamHints.detect(null, "", "   "))
        assertEquals(5, DolbyVisionStreamHints.detect("", "  ", "Show 2160p DV 05")?.profile)
    }

    @Test
    fun `profile eight keeps hardware decode decisions unchanged`() {
        val hint = DolbyVisionStreamHints.detect("Show S01E01 2160p DV8 HDR10")
        assertEquals(8, hint?.profile)
        assertFalse(hint!!.isProfile5)
        assertFalse(hint.isProfileUnknown)
    }
}
