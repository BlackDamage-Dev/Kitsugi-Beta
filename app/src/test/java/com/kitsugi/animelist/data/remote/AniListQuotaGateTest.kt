package com.kitsugi.animelist.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AniListQuotaGateTest {

    @Before
    fun setUp() {
        AniListQuotaGate.resetForTest()
    }

    @After
    fun tearDown() {
        AniListQuotaGate.resetForTest()
    }

    @Test
    fun defaultBudgetAndIntervalCalculation() {
        val snapshot = AniListQuotaGate.snapshot()
        assertEquals(30, snapshot.limit)
        // 30 - max(2, 3) = 27
        assertEquals(27, snapshot.budget)
        // ceil(60000 / 27) = 2223
        assertEquals(2223L, snapshot.minGapMs)
        assertEquals(0, snapshot.usedInWindow)
        assertEquals(0L, snapshot.cooldownRemainingMs)
    }

    @Test
    fun learnsLimitFromHttpResponseHeader() {
        AniListQuotaGate.onHttpResponse(limitHeader = 60, remainingHeader = 58)
        val snapshot = AniListQuotaGate.snapshot()
        assertEquals(60, snapshot.limit)
        // 60 - 6 = 54
        assertEquals(54, snapshot.budget)
        // ceil(60000 / 54) = 1112
        assertEquals(1112L, snapshot.minGapMs)
        assertEquals(58, snapshot.lastRemaining)
    }

    @Test
    fun onRateLimitedBlocksRequests() {
        AniListQuotaGate.onRateLimited(retryAfterMs = 15_000L)
        assertTrue(AniListQuotaGate.cooldownRemainingMs() in 14_000L..16_000L)

        // Request with short max wait should be rejected due to cooldown
        val decision = runBlocking {
            AniListQuotaGate.acquire(AniListQuotaGate.Priority.CRITICAL, maxWaitMs = 1_000L)
        }
        assertTrue(decision is AniListQuotaGate.Decision.Rejected)
        val rejected = decision as AniListQuotaGate.Decision.Rejected
        assertEquals(AniListQuotaGate.Reason.COOLDOWN, rejected.reason)
    }

    @Test
    fun priorityReservationRejectsLowPriorityWhenQuotaHeavy() {
        // Acquire immediate slot for critical
        val first = runBlocking {
            AniListQuotaGate.acquire(AniListQuotaGate.Priority.CRITICAL, maxWaitMs = 500L)
        }
        assertEquals(AniListQuotaGate.Decision.Granted, first)

        // Simulate artificially filled queue to test reservation
        // Budget is 27. Background threshold is 40% = 10 requests.
        // Let's learn limit = 10 (budget 8, 40% = 3)
        AniListQuotaGate.onHttpResponse(limitHeader = 10, remainingHeader = 5)
        val snap = AniListQuotaGate.snapshot()
        assertEquals(8, snap.budget) // 40% of 8 = 3

        // Acquire slots up to background limit
        runBlocking {
            AniListQuotaGate.acquire(AniListQuotaGate.Priority.CRITICAL, maxWaitMs = 60_000L)
            AniListQuotaGate.acquire(AniListQuotaGate.Priority.CRITICAL, maxWaitMs = 60_000L)
        }

        // Now starts.size >= 3 (40% of 8) -> Background should be rejected
        val bgDecision = runBlocking {
            AniListQuotaGate.acquire(AniListQuotaGate.Priority.BACKGROUND, maxWaitMs = 5_000L)
        }
        assertTrue(bgDecision is AniListQuotaGate.Decision.Rejected)
        val rejectedBg = bgDecision as AniListQuotaGate.Decision.Rejected
        assertEquals(AniListQuotaGate.Reason.QUOTA_RESERVED, rejectedBg.reason)
    }
}
