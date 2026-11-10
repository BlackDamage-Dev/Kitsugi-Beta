package com.kitsugi.animelist.ui.screens.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the storage card's direction/hysteresis contract. */
class DownloadsOverviewScrollPolicyTest {

    private fun policy() = DownloadsOverviewScrollPolicy(
        hideThresholdPx = 28f,
        revealThresholdPx = 14f
    )

    @Test
    fun `small downward movement does not hide the card`() {
        val policy = policy()
        assertTrue(policy.onConsumedScroll(-10f, atTop = false, hasItems = true))
        assertTrue(policy.onConsumedScroll(-10f, atTop = false, hasItems = true))
        assertTrue(policy.isVisible)
    }

    @Test
    fun `deliberate downward scroll hides and upward scroll reveals`() {
        val policy = policy()
        assertFalse(policy.onConsumedScroll(-30f, atTop = false, hasItems = true))
        assertTrue(policy.onConsumedScroll(8f, atTop = false, hasItems = true).not())
        assertTrue(policy.onConsumedScroll(8f, atTop = false, hasItems = true))
    }

    @Test
    fun `direction change discards old travel instead of toggling on jitter`() {
        val policy = policy()
        policy.onConsumedScroll(-20f, atTop = false, hasItems = true)
        // Reverse direction: the previous -20px must not make +14px reveal early,
        // nor should a following -8px immediately reach the hide threshold.
        assertTrue(policy.onConsumedScroll(10f, atTop = false, hasItems = true))
        assertTrue(policy.isVisible)
        assertTrue(policy.onConsumedScroll(-8f, atTop = false, hasItems = true))
    }

    @Test
    fun `reaching the top always reveals the card`() {
        val policy = policy()
        policy.onConsumedScroll(-40f, atTop = false, hasItems = true)
        assertFalse(policy.isVisible)
        assertTrue(policy.onConsumedScroll(1f, atTop = true, hasItems = true))
    }

    @Test
    fun `empty content cannot hide the card`() {
        val policy = policy()
        assertTrue(policy.onConsumedScroll(-100f, atTop = false, hasItems = false))
        assertTrue(policy.isVisible)
    }

    @Test
    fun `ending a gesture clears partial travel`() {
        val policy = policy()
        policy.onConsumedScroll(-20f, atTop = false, hasItems = true)
        policy.endGesture()
        assertTrue(policy.onConsumedScroll(-10f, atTop = false, hasItems = true))
    }

    @Test
    fun `non finite and zero deltas are ignored`() {
        val policy = policy()
        assertTrue(policy.onConsumedScroll(Float.NaN, atTop = false, hasItems = true))
        assertTrue(policy.onConsumedScroll(Float.NEGATIVE_INFINITY, atTop = false, hasItems = true))
        assertTrue(policy.onConsumedScroll(0f, atTop = false, hasItems = true))
        assertTrue(policy.isVisible)
    }

    @Test
    fun `reset restores visible state for a new tab or search`() {
        val policy = policy()
        policy.onConsumedScroll(-40f, atTop = false, hasItems = true)
        policy.onConsumedScroll(-10f, atTop = false, hasItems = true)
        policy.reset()
        assertTrue(policy.isVisible)
        // Partial travel from the old context must not leak into the new one.
        assertTrue(policy.onConsumedScroll(-10f, atTop = false, hasItems = true))
    }
}
