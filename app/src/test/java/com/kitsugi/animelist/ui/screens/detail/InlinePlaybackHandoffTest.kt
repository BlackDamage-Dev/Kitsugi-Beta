package com.kitsugi.animelist.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hızlı dokunmalarda oynatıcı yığınlarının üst üste binmesini engelleyen el değiştirme
 * (handoff) mantığının sözleşmesi.
 */
class InlinePlaybackHandoffTest {

    @Test
    fun `no previous release means no wait`() {
        assertEquals(0L, inlineEngineHandoffDelayMs(lastReleaseAtMs = 0L, nowMs = 5_000L))
    }

    @Test
    fun `engine released just now forces a full handoff window`() {
        assertEquals(
            INLINE_PLAYER_HANDOFF_MS,
            inlineEngineHandoffDelayMs(lastReleaseAtMs = 10_000L, nowMs = 10_000L)
        )
    }

    @Test
    fun `remaining part of the handoff window is awaited`() {
        assertEquals(
            INLINE_PLAYER_HANDOFF_MS - 100L,
            inlineEngineHandoffDelayMs(lastReleaseAtMs = 10_000L, nowMs = 10_100L)
        )
    }

    @Test
    fun `old releases no longer delay a new player`() {
        assertEquals(
            0L,
            inlineEngineHandoffDelayMs(
                lastReleaseAtMs = 10_000L,
                nowMs = 10_000L + INLINE_PLAYER_HANDOFF_MS
            )
        )
        assertEquals(
            0L,
            inlineEngineHandoffDelayMs(
                lastReleaseAtMs = 10_000L,
                nowMs = 10_000L + INLINE_PLAYER_HANDOFF_MS + 5_000L
            )
        )
    }

    @Test
    fun `tap coalescing is shorter than the start timeout`() {
        // Dokunma birleştirme penceresi, "başlamadı" bekçisinden kısa olmalı; aksi hâlde
        // kullanıcı oynatıcıyı görmeden hata ekranı gelebilir.
        assertTrue(INLINE_PLAYER_TAP_COALESCE_MS > 0L)
        assertTrue(INLINE_PLAYER_HANDOFF_MS > 0L)
        assertTrue(INLINE_PLAYER_TAP_COALESCE_MS < INLINE_PLAYER_START_TIMEOUT_MS)
        assertTrue(INLINE_PLAYER_HANDOFF_MS < INLINE_PLAYER_START_TIMEOUT_MS)
    }
}
