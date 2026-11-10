package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayerResumeIdentityTest {
    @Test
    fun `different episodes and streams do not share resume keys`() {
        val base = PlayerResumeIdentity(42, 1, 3, "Addon A", "1080p", "https://media.example/video/3.mp4")

        assertNotEquals(base.storageKey(), base.copy(episode = 4).storageKey())
        assertNotEquals(base.storageKey(), base.copy(addonName = "Addon B").storageKey())
        assertNotEquals(base.storageKey(), base.copy(sourceUrl = "https://media.example/video/3-alt.mp4").storageKey())
    }

    @Test
    fun `rotating signed query params retain same resume identity`() {
        val first = PlayerResumeIdentity(
            42, 1, 3, "Addon A", "1080p",
            "https://media.example/video/3.mp4?id=abc&token=first&expires=100"
        )
        val refreshed = first.copy(
            sourceUrl = "https://media.example/video/3.mp4?expires=999&id=abc&token=second"
        )

        assertEquals(first.storageKey(), refreshed.storageKey())
    }
}
