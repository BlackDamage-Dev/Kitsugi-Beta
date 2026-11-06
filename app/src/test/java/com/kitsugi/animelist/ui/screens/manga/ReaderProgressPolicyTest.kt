package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class ReaderProgressPolicyTest {
    @Test fun pausesWritesButKeepsSessionCompletionSeparate() {
        val paused = readerProgressDecision(true, false, false, false, 9, 10)
        assertTrue(paused.completed)
        assertFalse(paused.persist)
        val afterResume = readerProgressDecision(false, false, true, true, 0, 10)
        assertFalse(afterResume.completed)
        assertTrue(afterResume.persist)
        assertTrue(readerProgressDecision(false, true, false, true, 0, 10).completed)
    }
    @Test fun webtoonFinishesOnlyWithAnEndEvent() {
        assertFalse(readerProgressDecision(false, false, false, true, 9, 10).completed)
        assertTrue(readerProgressDecision(false, true, false, true, 9, 10).completed)
    }
}
