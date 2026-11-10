package com.kitsugi.animelist.data.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientQueueRulesTest {

    private fun track(
        id: String,
        streamUrl: String = "https://example.com/$id.mp3",
        downloadUrl: String? = null,
        provider: String = "internet_archive",
        fileType: String = "mp3",
    ) = AmbientAudioTrack(
        id = id,
        title = "Track $id",
        creator = "creator",
        streamUrl = streamUrl,
        landingUrl = "https://example.com/landing/$id",
        provider = provider,
        source = "test",
        fileType = fileType,
        downloadUrl = downloadUrl,
    )

    @Test
    fun `repeat mode cycles off, one, all and back to off`() {
        assertEquals(AmbientQueueRules.REPEAT_ONE, AmbientQueueRules.nextRepeatMode(AmbientQueueRules.REPEAT_OFF))
        assertEquals(AmbientQueueRules.REPEAT_ALL, AmbientQueueRules.nextRepeatMode(AmbientQueueRules.REPEAT_ONE))
        assertEquals(AmbientQueueRules.REPEAT_OFF, AmbientQueueRules.nextRepeatMode(AmbientQueueRules.REPEAT_ALL))
    }

    @Test
    fun `sleep timer cycles through off, 15, 30, 60 and back to off`() {
        assertEquals(15, AmbientQueueRules.nextSleepTimerMinutes(0))
        assertEquals(30, AmbientQueueRules.nextSleepTimerMinutes(15))
        assertEquals(60, AmbientQueueRules.nextSleepTimerMinutes(30))
        assertEquals(0, AmbientQueueRules.nextSleepTimerMinutes(60))
        // Bilinmeyen bir değer de güvenli şekilde kapalıya/başa döner.
        assertEquals(15, AmbientQueueRules.nextSleepTimerMinutes(7))
    }

    @Test
    fun `continuation queue starts at the picked track and wraps to the earlier ones`() {
        val visible = listOf(track("a"), track("b"), track("c"), track("d"))

        val queue = AmbientQueueRules.buildQueueFrom(visible, pickedId = "c", blockedIds = emptySet())

        assertEquals(listOf("c", "d", "a", "b"), queue.map { it.id })
    }

    @Test
    fun `continuation queue skips blocked and non-streamable tracks`() {
        val visible = listOf(
            track("a"),
            track("b"),
            track("broken", streamUrl = "http://insecure.example.com/x.mp3"),
            track("c"),
        )

        val queue = AmbientQueueRules.buildQueueFrom(visible, pickedId = "a", blockedIds = setOf("b"))

        assertEquals(listOf("a", "c"), queue.map { it.id })
    }

    @Test
    fun `continuation queue is empty when the picked track cannot be played`() {
        val visible = listOf(track("a"), track("b"))

        assertTrue(AmbientQueueRules.buildQueueFrom(visible, pickedId = "b", blockedIds = setOf("b")).isEmpty())
        assertTrue(AmbientQueueRules.buildQueueFrom(visible, pickedId = "missing", blockedIds = emptySet()).isEmpty())
    }

    @Test
    fun `continuation queue respects the size limit`() {
        val visible = (1..40).map { track("t$it") }

        val queue = AmbientQueueRules.buildQueueFrom(visible, pickedId = "t1", blockedIds = emptySet())

        assertEquals(AmbientQueueRules.MAX_CONTINUATION_QUEUE, queue.size)
        assertEquals("t1", queue.first().id)
    }

    @Test
    fun `candidates exclude already queued and blocked tracks and keep order`() {
        val pool = listOf(track("a"), track("b"), track("c"), track("d"))

        val candidates = AmbientQueueRules.continuationCandidates(
            pool = pool,
            queuedIds = setOf("a"),
            blockedIds = setOf("c"),
        )

        assertEquals(listOf("b", "d"), candidates.map { it.id })
    }

    @Test
    fun `refill is requested only near the end of a continuous queue`() {
        val rule = AmbientQueueRules::shouldRequestMore

        assertTrue(rule(AmbientQueueRules.REFILL_THRESHOLD, AmbientQueueRules.REPEAT_ALL, true, true))
        assertFalse(rule(AmbientQueueRules.REFILL_THRESHOLD + 3, AmbientQueueRules.REPEAT_ALL, true, true))
        // Parça tekrarında kuyruk hiç bitmez; yenisine gerek yok.
        assertFalse(rule(0, AmbientQueueRules.REPEAT_ONE, true, true))
        // Sıralı devam veya çevrimiçi akış kapalıysa asla istenmez.
        assertFalse(rule(0, AmbientQueueRules.REPEAT_OFF, false, true))
        assertFalse(rule(0, AmbientQueueRules.REPEAT_OFF, true, false))
    }

    @Test
    fun `downloadable https tracks are accepted`() {
        val track = track("ok", downloadUrl = "https://example.com/ok.mp3")

        assertNull(AmbientQueueRules.downloadRefusalReason(track))
    }

    @Test
    fun `radio streams are refused with a clear reason`() {
        val radio = track("radio", provider = "radio_browser", streamUrl = "https://radio.example.com/live")

        assertTrue(AmbientQueueRules.downloadRefusalReason(radio)!!.contains("radyo"))
    }

    @Test
    fun `jamendo tracks without a download address explain why`() {
        val jamendo = track("j", provider = "jamendo", streamUrl = "https://example.com/j.mp3")

        assertTrue(AmbientQueueRules.downloadRefusalReason(jamendo)!!.contains("Jamendo"))
    }

    @Test
    fun `tracks without any download address get a generic reason`() {
        val plain = track("p", provider = "ccmixter", streamUrl = "https://example.com/p.mp3")

        assertNotNullReason(AmbientQueueRules.downloadRefusalReason(plain))
    }

    private fun assertNotNullReason(reason: String?) {
        assertTrue("indirme nedeni boş olmamalı", !reason.isNullOrBlank())
    }
}
