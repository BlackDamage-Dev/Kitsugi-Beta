package com.kitsugi.animelist.core.download

import org.junit.Assert.*
import org.junit.Test

/**
 * T-IND-2 (2026-11-09): segment defteri ayrıştırma testleri (saf JVM).
 * Devam ettirme doğruluğu bu deftere bağlıdır: yinelenen/atlanan dilim = bozuk (kasan) video.
 */
class SegmentJournalTest {

    @Test
    fun parsesHeaderAndEntries() {
        val text = """
            #plan /hls2/01/00037/x.urlset/seg-1-v1-a1.ts 240 5120
            0 1048576
            1 1048576
            2 734000
        """.trimIndent()
        val state = SegmentJournal.parse(text)
        assertNotNull(state.header)
        assertEquals(240, state.header!!.total)
        assertEquals(5120L, state.header!!.initBytes)
        assertEquals(3, state.count)
        assertEquals(5120L + 1048576L + 1048576L + 734000L, state.expectedFileLength)
    }

    @Test
    fun garbageLinesIgnored() {
        val text = """
            #plan /a/seg.ts 10 0
            0 100
            bozuk satır
            1 abc
            -3 50
            2 300
        """.trimIndent()
        val state = SegmentJournal.parse(text)
        assertEquals(2, state.count) // 0 ve 2 geçerli; "1 abc" ve negatif atıldı
        assertEquals(400L, state.segmentBytes)
    }

    @Test
    fun emptyOrMissingJournalIsEmptyState() {
        val state = SegmentJournal.parse("")
        assertNull(state.header)
        assertEquals(0, state.count)
        assertEquals(0L, state.expectedFileLength)
    }

    @Test
    fun contiguousScanStopsAtGap() {
        // Downloader kesintisiz dizi taraması yapar: 0,1,[3] → 2 dilim kabul edilir.
        val text = """
            #plan /k 50 0
            0 10
            1 20
            3 40
        """.trimIndent()
        val state = SegmentJournal.parse(text)
        var contiguous = 0
        var bytes = 0L
        for ((idx, size) in state.entries) {
            if (idx != contiguous) break
            contiguous++
            bytes += size
        }
        assertEquals(2, contiguous)
        assertEquals(30L, bytes)
    }

    @Test
    fun lineFormatsRoundTrip() {
        val header = SegmentJournal.headerLine("/hls/x/seg.ts", 7, 99L)
        val entry = SegmentJournal.entryLine(3, 42L)
        val state = SegmentJournal.parse("$header\n0 1\n1 2\n2 3\n$entry\n")
        assertEquals("/hls/x/seg.ts", state.header!!.planKey)
        assertEquals(7, state.header!!.total)
        assertEquals(99L, state.header!!.initBytes)
        assertEquals(4, state.count)
        assertEquals(3 to 42L, state.entries.last())
    }
}
