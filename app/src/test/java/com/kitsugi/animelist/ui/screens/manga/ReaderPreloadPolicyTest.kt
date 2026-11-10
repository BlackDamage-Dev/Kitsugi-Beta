package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPreloadPolicyTest {
    @Test fun automaticAndCustomCountsAreBounded() {
        assertEquals(3, readerPreloadCount(-1, true))
        assertEquals(2, readerPreloadCount(-1, false))
        assertEquals(0, readerPreloadCount(0, true))
        assertEquals(5, readerPreloadCount(100, false))
    }
    @Test fun progressTimestampsAreStrictlyIncreasing() {
        val timestamps = (1..1000).map { nextReaderProgressTimestamp() }
        assertEquals(true, timestamps.zipWithNext().all { (a, b) -> b > a })
    }
    @Test fun scrollPercentIsBounded() {
        assertEquals(0.8f, readerScrollFraction(80), 0.001f)
        assertEquals(0.25f, readerScrollFraction(-1), 0.001f)
        assertEquals(1f, readerScrollFraction(200), 0.001f)
    }
}
