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
