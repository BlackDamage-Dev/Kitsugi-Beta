package com.kitsugi.animelist.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossWriteVerificationGateTest {
    @Test
    fun verifiedResultRemainsAllowedForEveryCachedTarget() = runBlocking {
        val gate = CrossWriteVerificationGate()
        var calls = 0
        val verify: suspend () -> Boolean = { calls++; true }

        assertFalse(gate.isBlocked("anilist", "mal", 21, verify))
        assertFalse(gate.isBlocked("kitsu", "mal", 21, verify))
        assertEquals(1, calls)
    }

    @Test
    fun rejectedResultRemainsBlockedForEveryCachedTarget() = runBlocking {
        val gate = CrossWriteVerificationGate()
        var calls = 0
        val verify: suspend () -> Boolean = { calls++; false }

        assertTrue(gate.isBlocked("anilist", "mal", 21, verify))
        assertTrue(gate.isBlocked("kitsu", "mal", 21, verify))
        assertEquals(1, calls)
    }

    @Test
    fun ownSourceAndMissingMalIdBypassWithoutPoisoningCache() = runBlocking {
        val gate = CrossWriteVerificationGate()
        var calls = 0
        val verify: suspend () -> Boolean = { calls++; true }

        assertFalse(gate.isBlocked("mal", "mal", 21, verify))
        assertFalse(gate.isBlocked("anilist", "mal", null, verify))
        assertEquals(0, calls)
        assertFalse(gate.isBlocked("anilist", "mal", 21, verify))
        assertEquals(1, calls)
    }
}
