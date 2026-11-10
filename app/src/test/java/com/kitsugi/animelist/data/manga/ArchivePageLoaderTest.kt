package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.manga.loader.naturalArchivePathCompare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePageLoaderTest {
    @Test
    fun pageNumbersUseNaturalOrderAcrossFoldersAndLeadingZeroes() {
        val entries = listOf(
            "chapter/page10.jpg", "chapter/page2.jpg", "chapter/page1.jpg",
            "chapter/page02.jpg", "chapter2/page1.jpg", "chapter10/page1.jpg",
        )
        assertEquals(listOf(
            "chapter/page1.jpg", "chapter/page2.jpg", "chapter/page02.jpg",
            "chapter/page10.jpg", "chapter2/page1.jpg", "chapter10/page1.jpg",
        ), entries.sortedWith(::naturalArchivePathCompare))
    }

    @Test
    fun veryLargeNumericRunsDoNotOverflow() {
        assertTrue(naturalArchivePathCompare("page99999999999999999999.jpg", "page100000000000000000000.jpg") < 0)
    }

    @Test
    fun equalNumbersRetainDeterministicCaseAndSuffixOrdering() {
        assertEquals(0, naturalArchivePathCompare("page1.jpg", "page1.jpg"))
        assertTrue(naturalArchivePathCompare("a.jpg", "b.jpg") < 0)
        assertTrue(naturalArchivePathCompare("a1.jpg", "a1a.jpg") < 0)
    }
}
