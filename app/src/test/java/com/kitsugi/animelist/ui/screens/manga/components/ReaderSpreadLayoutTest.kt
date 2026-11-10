package com.kitsugi.animelist.ui.screens.manga.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSpreadLayoutTest {
    @Test fun coversEveryOriginalPageExactlyOnce() {
        for (count in 1..20) for (doublePage in listOf(false, true)) for (cover in listOf(false, true)) {
            val visualCount = readerSpreadCount(count, doublePage, cover)
            val actual = (0 until visualCount).flatMap { visual ->
                val first = readerSpreadFirst(visual, doublePage, cover)
                val last = readerSpreadLast(visual, count, doublePage, cover)
                (first..last).toList()
            }
            assertEquals((0 until count).toList(), actual)
            for (page in 0 until count) {
                val visual = readerSpreadForPage(page, doublePage, cover)
                assertEquals(true, page in readerSpreadFirst(visual, doublePage, cover)..readerSpreadLast(visual, count, doublePage, cover))
            }
        }
        assertEquals(0, readerSpreadCount(0, true, true))
    }

    @Test fun spreadLongPressTargetsThePhysicallyPressedHalf() {
        // LTR: ilk sayfa solda; RTL: ilk sayfa sağda.
        assertEquals(2, readerSpreadPressedPage(0.25f, rtl = false, first = 2, last = 3))
        assertEquals(3, readerSpreadPressedPage(0.75f, rtl = false, first = 2, last = 3))
        assertEquals(3, readerSpreadPressedPage(0.25f, rtl = true, first = 2, last = 3))
        assertEquals(2, readerSpreadPressedPage(0.75f, rtl = true, first = 2, last = 3))
        // Tek sayfalık yayılım (kapak / son tek sayfa) ve geçersiz koordinat.
        assertEquals(0, readerSpreadPressedPage(0.9f, rtl = true, first = 0, last = 0))
        assertEquals(2, readerSpreadPressedPage(Float.NaN, rtl = false, first = 2, last = 3))
    }

    @Test fun firstCoverIsStandaloneAndOddLastPageRemainsVisible() {
        assertEquals(3, readerSpreadCount(5, true, true))
        assertEquals(0, readerSpreadLast(0, 5, true, true))
        assertEquals(1, readerSpreadFirst(1, true, true))
        assertEquals(4, readerSpreadLast(2, 5, true, true))
        assertEquals(2, readerSpreadForPage(4, true, true))
        assertEquals(2, readerSpreadCount(3, true, false))
        assertEquals(2, readerSpreadLast(1, 3, true, false))
    }
}
