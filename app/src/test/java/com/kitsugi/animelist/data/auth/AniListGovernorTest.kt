package com.kitsugi.animelist.data.auth

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AniListGovernor] birim testleri.
 */
class AniListGovernorTest {

    @Before
    fun setUp() {
        AniListGovernor.resetForTests()
    }

    @After
    fun tearDown() {
        AniListGovernor.resetForTests()
    }

    @Test
    fun `detectServiceDown detects plain string and JSON errors`() {
        val plain = "The AniList API has been temporarily disabled due to severe stability issues."
        assertNotNull(AniListGovernor.detectServiceDown(plain))

        val json = JSONObject().apply {
            put("errors", JSONArray().apply {
                put(JSONObject().put("message", "API temporarily disabled"))
            })
        }.toString()
        assertNotNull(AniListGovernor.detectServiceDown(json))

        assertNull(AniListGovernor.detectServiceDown("Normal error message"))
        assertNull(AniListGovernor.detectServiceDown(null))
    }

    @Test
    fun `markServiceDown engages service down state`() {
        assertFalse(AniListGovernor.isServiceDownNow())

        val reason = "temporarily disabled for maintenance"
        AniListGovernor.markServiceDown(reason, 503)

        assertTrue(AniListGovernor.isServiceDownNow())
        assertEquals(reason, AniListGovernor.serviceDownMessage())

        val gate = AniListGovernor.acquireBlocking(maxWaitMs = 100L)
        assertTrue(gate is AniListGovernor.Gate.Blocked)
        val blocked = gate as AniListGovernor.Gate.Blocked
        assertEquals(AniListGovernor.BlockedReason.SERVICE_DOWN, blocked.reason)
    }

    @Test
    fun `classifyErrors detects GraphQL status 429 as RateLimited`() {
        val errors429 = JSONArray().apply {
            put(JSONObject().apply {
                put("message", "Too Many Requests.")
                put("status", 429)
            })
        }
        val outcome = AniListGovernor.classifyErrors(errors429)
        assertTrue(outcome is AniListGovernor.Outcome.RateLimited)
        assertTrue(AniListGovernor.isRateLimitedNow())
    }

    @Test
    fun `classifyErrors detects invalid token as AuthFailure`() {
        val errorsAuth = JSONArray().apply {
            put(JSONObject().apply {
                put("message", "Invalid token provided.")
            })
        }
        val outcome = AniListGovernor.classifyErrors(errorsAuth)
        assertEquals(AniListGovernor.Outcome.AuthFailure, outcome)
    }

    @Test
    fun `classifyErrors returns Success for null or empty errors`() {
        assertEquals(AniListGovernor.Outcome.Success, AniListGovernor.classifyErrors(null))
        assertEquals(AniListGovernor.Outcome.Success, AniListGovernor.classifyErrors(JSONArray()))
    }
}
