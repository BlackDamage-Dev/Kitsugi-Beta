package com.kitsugi.animelist.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-20 `FORTIFY: write: count 18446744073709551615 > SSIZE_MAX` → `SIGABRT`
 * (`thread=MPEG4Writer`) çökmesinin regresyon testleri.
 *
 * Çökmenin kaynağı, `MediaMuxer.writeSampleData`'ya negatif / aşırı örnek boyutu veya
 * bozuk PTS verilmesiydi. [MuxSampleGuard] bu değerleri yazımdan önce süzer.
 */
class MuxSampleGuardTest {

    @Test
    fun `negatif boyut yazilamaz`() {
        // readSampleData akış sonunda -1 döner; bu değer BufferInfo.size'a yazılırsa
        // MPEG4Writer size_t(-1) hesaplar ve süreç SIGABRT alır.
        assertFalse(MuxSampleGuard.isWritableSampleSize(-1))
        assertFalse(MuxSampleGuard.isWritableSampleSize(Int.MIN_VALUE))
    }

    @Test
    fun `sifir boyut yazilamaz`() {
        assertFalse(MuxSampleGuard.isWritableSampleSize(0))
    }

    @Test
    fun `asiri buyuk boyut yazilamaz`() {
        assertFalse(MuxSampleGuard.isWritableSampleSize(MuxSampleGuard.MAX_SAMPLE_BYTES + 1))
        assertFalse(MuxSampleGuard.isWritableSampleSize(Int.MAX_VALUE))
    }

    @Test
    fun `normal boyutlar yazilabilir`() {
        assertTrue(MuxSampleGuard.isWritableSampleSize(1))
        assertTrue(MuxSampleGuard.isWritableSampleSize(1_024))
        assertTrue(MuxSampleGuard.isWritableSampleSize(MuxSampleGuard.MAX_SAMPLE_BYTES))
    }

    @Test
    fun `negatif pts sifira cekilir`() {
        assertEquals(0L, MuxSampleGuard.clampPtsUs(-1L))
        assertEquals(0L, MuxSampleGuard.clampPtsUs(-33_333L))
    }

    @Test
    fun `absurt pts ust sinira cekilir`() {
        assertEquals(MuxSampleGuard.MAX_PTS_US, MuxSampleGuard.clampPtsUs(Long.MAX_VALUE))
        assertEquals(MuxSampleGuard.MAX_PTS_US, MuxSampleGuard.clampPtsUs(MuxSampleGuard.MAX_PTS_US + 1L))
    }

    @Test
    fun `normal pts korunur`() {
        assertEquals(0L, MuxSampleGuard.clampPtsUs(0L))
        assertEquals(80_000L, MuxSampleGuard.clampPtsUs(80_000L))
    }

    @Test
    fun `ilk pts oldugu gibi kalir`() {
        assertEquals(56_777L, MuxSampleGuard.nextMonotonicPtsUs(56_777L, -1L, 21_333L))
    }

    @Test
    fun `esit pts adim kadar ilerletilir`() {
        assertEquals(1_000L + 21_333L, MuxSampleGuard.nextMonotonicPtsUs(1_000L, 1_000L, 21_333L))
    }

    @Test
    fun `azalan pts kesinlikle artana cevrilir`() {
        val next = MuxSampleGuard.nextMonotonicPtsUs(500L, 9_000L, 33_333L)
        assertEquals(9_000L + 33_333L, next)
        assertTrue(next > 9_000L)
    }

    @Test
    fun `adim sifir olsa bile pts artar`() {
        val next = MuxSampleGuard.nextMonotonicPtsUs(10L, 10L, 0L)
        assertTrue(next > 10L)
    }

    @Test
    fun `writableSample gecersiz boyutta null doner`() {
        assertNull(MuxSampleGuard.writableSample(-1, 0L, -1L, 21_333L))
        assertNull(MuxSampleGuard.writableSample(0, 0L, -1L, 21_333L))
    }

    @Test
    fun `writableSample gecerli ornekte boyut ve pts doner`() {
        val decision = MuxSampleGuard.writableSample(
            size = 512,
            rawPtsUs = 42_000L,
            lastWrittenUs = -1L,
            stepUs = 21_333L
        )
        assertEquals(512 to 42_000L, decision)
    }

    @Test
    fun `ses ornek bayragi her zaman sifir`() {
        // AAC/Opus/AC-3 örneklerinde anahtar kare kavramı yoktur.
        assertEquals(0, MuxSampleGuard.audioSampleFlags())
    }

    @Test
    fun `video bayragi yalnizca senkron ornekte anahtar kare olur`() {
        val keyFrame = MuxSampleGuard.videoSampleFlags(
            sourceFlags = 1,
            keyFrameBit = 1,
            codecKeyFrame = 1
        )
        val deltaFrame = MuxSampleGuard.videoSampleFlags(
            sourceFlags = 0,
            keyFrameBit = 1,
            codecKeyFrame = 1
        )
        assertEquals(1, keyFrame)
        assertEquals(0, deltaFrame)
    }

    @Test
    fun `ilk pts ofseti pts pozitifken negatiftir`() {
        assertEquals(-80_000L, MuxSampleGuard.baseShiftFor(80_000L))
        assertEquals(0L, MuxSampleGuard.baseShiftFor(-5L))
    }
}
