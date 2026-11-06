package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class NovelTextReplacementTest {
    @Test fun literalReplacementPreservesSegmentPositions() {
        val original = listOf("a.b a.b", "no match", "a.b")
        assertEquals(listOf("x x", "no match", "x"), applyNovelLiteralReplacement(original, "a.b", "x"))
        assertEquals(listOf("a.b a.b", "no match", "a.b"), original)
        assertEquals(listOf(" ", "no match", ""), applyNovelLiteralReplacement(original, "a.b", ""))
    }
    @Test fun boundsExpansionAndInput() {
        assertThrows(IllegalArgumentException::class.java) { applyNovelLiteralReplacement(listOf("abc"), "", "x") }
        assertThrows(IllegalArgumentException::class.java) { applyNovelLiteralReplacement(listOf("x".repeat(2048)), "x", "aaaa") }
        assertThrows(IllegalArgumentException::class.java) { applyNovelLiteralReplacement(listOf("x"), "x".repeat(101), "") }
    }
    @Test fun checksCancellationBeforeProcessingEachSegment() {
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            applyNovelLiteralReplacement(listOf("a", "a"), "a", "b") { if (++calls == 2) throw IllegalStateException() }
        }
    }
}
