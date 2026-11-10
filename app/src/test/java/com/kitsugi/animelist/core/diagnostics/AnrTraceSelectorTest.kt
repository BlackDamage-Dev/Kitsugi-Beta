package com.kitsugi.animelist.core.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test

class AnrTraceSelectorTest {
    @Test
    fun mainThreadStackSurvivesLongRuntimeHeader() {
        val lines = (1..40).map { "runtime metadata $it" } +
            listOf("", "\"main\" prio=5 tid=1 Blocked", "  at com.kitsugi.animelist.data.local.MediaEntryRepository.deduplicateSameSourceDuplicates(MediaEntryRepository.kt:584)", "") +
            (1..40).map { "other thread $it" }
        val selected = AnrTraceSelector.select(lines, maxLines = 30)
        assertTrue(selected.contains("main thread"))
        assertTrue(selected.contains("deduplicateSameSourceDuplicates"))
    }
}
