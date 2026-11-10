package com.kitsugi.animelist.core.p2p

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P2pBufferProgressTest {
    @Test
    fun `normalizes fraction and percentage formats`() {
        assertEquals(0.1f, normalizeP2pBufferProgress(0.1f)!!, 0.0001f)
        assertEquals(0.1f, normalizeP2pBufferProgress(10f)!!, 0.0001f)
        assertEquals(1f, normalizeP2pBufferProgress(100f)!!, 0.0001f)
    }

    @Test
    fun `rejects non-finite negative and out-of-range samples`() {
        assertNull(normalizeP2pBufferProgress(Float.NaN))
        assertNull(normalizeP2pBufferProgress(Float.POSITIVE_INFINITY))
        assertNull(normalizeP2pBufferProgress(-0.1f))
        assertNull(normalizeP2pBufferProgress(101f))
    }

    @Test
    fun `buffer percent is always bounded`() {
        assertEquals(0, bufferPercent(Float.NaN))
        assertEquals(10, bufferPercent(0.1f))
        assertEquals(10, bufferPercent(10f))
        assertEquals(100, bufferPercent(100f))
        assertEquals(0, bufferPercent(101f))
    }
}
