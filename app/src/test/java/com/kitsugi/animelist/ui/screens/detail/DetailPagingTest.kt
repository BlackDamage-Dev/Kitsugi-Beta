package com.kitsugi.animelist.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailPagingTest {
    @Test
    fun `advances one batch rather than revealing the whole list`() {
        assertEquals(24, nextDetailPageCount(12, 327, 12))
    }

    @Test
    fun `last batch stops at the actual count`() {
        assertEquals(47, nextDetailPageCount(36, 47, 12))
        assertEquals(47, nextDetailPageCount(47, 47, 12))
    }

    @Test
    fun `empty and shorter replacement lists stay in range`() {
        assertEquals(0, nextDetailPageCount(12, 0, 12))
        assertEquals(3, nextDetailPageCount(24, 3, 12))
    }

    @Test
    fun `batch addition cannot overflow`() {
        assertEquals(Int.MAX_VALUE, nextDetailPageCount(Int.MAX_VALUE - 2, Int.MAX_VALUE, 12))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero batch is rejected`() {
        nextDetailPageCount(0, 47, 0)
    }
}
