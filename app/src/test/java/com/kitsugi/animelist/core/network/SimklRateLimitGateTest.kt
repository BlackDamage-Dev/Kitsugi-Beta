package com.kitsugi.animelist.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SimklRateLimitGateTest {

    @Test
    fun readRequestsFromSeparateCallersShareOnePacingTimeline() {
        var now = 1_000L
        val sleeps = mutableListOf<Long>()
        val gate = SimklRequestRateGate(
            nowElapsedMillis = { now },
            sleep = { millis -> sleeps += millis; now += millis },
            getIntervalMillis = 250L,
        )

        gate.acquire("GET")
        gate.acquire("GET")
        gate.acquire("GET")

        assertEquals(1_500L, now)
        assertEquals(listOf(250L, 250L), sleeps)
    }

    @Test
    fun writesUseTheirOwnSlowerBudget() {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val gate = SimklRequestRateGate(
            nowElapsedMillis = { now },
            sleep = { millis -> sleeps += millis; now += millis },
            getIntervalMillis = 250L,
            writeIntervalMillis = 1_250L,
        )

        gate.acquire("GET")
        gate.acquire("POST") // GET and write budgets are tracked independently.
        gate.acquire("POST")

        assertEquals(1_250L, now)
        assertEquals(1_250L, sleeps.sum())
    }

    @Test
    fun rateLimitCooldownPausesBothReadsAndWrites() {
        var now = 10_000L
        val sleeps = mutableListOf<Long>()
        val gate = SimklRequestRateGate(
            nowElapsedMillis = { now },
            sleep = { millis -> sleeps += millis; now += millis },
            getIntervalMillis = 250L,
            writeIntervalMillis = 1_250L,
        )

        gate.acquire("GET")
        gate.noteRateLimited(retryAfterMillis = 2_000L)
        gate.acquire("GET")
        gate.acquire("POST")

        assertEquals(12_000L, now)
        assertEquals(2_000L, sleeps.sum())
    }

    @Test
    fun absentRetryAfterUsesShortSharedCooldown() {
        var now = 0L
        val gate = SimklRequestRateGate(
            nowElapsedMillis = { now },
            sleep = { now += it },
            defaultCooldownMillis = 3_000L,
        )

        gate.noteRateLimited()
        gate.acquire("GET")

        assertEquals(3_000L, now)
    }

    @Test
    fun retryAfterAcceptsSecondsDatesAndCapsOverflow() {
        assertEquals(2_000L, SimklRateLimitPolicy.retryAfterMillis("2", nowEpochMillis = 0L))
        assertEquals(
            5_000L,
            SimklRateLimitPolicy.retryAfterMillis("Thu, 1 Jan 1970 00:00:05 GMT", nowEpochMillis = 0L),
        )
        assertEquals(
            SimklRateLimitPolicy.MAX_COOLDOWN_MS,
            SimklRateLimitPolicy.retryAfterMillis(Long.MAX_VALUE.toString(), nowEpochMillis = 0L),
        )
        assertEquals(null, SimklRateLimitPolicy.retryAfterMillis("not-a-date", nowEpochMillis = 0L))
    }

    @Test
    fun simklWriteLockMarkerIsDetectedRegardlessOfTurkishDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(SimklRateLimitPolicy.isRateLimitMessage("{\"error\":\"RATE_LIMIT\"}"))
            assertTrue(SimklRateLimitPolicy.isRateLimitMessage("Too Many Requests"))
            assertFalse(SimklRateLimitPolicy.isRateLimitMessage("invalid ids"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
