package com.kitsugi.animelist.core.player.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOnlyPlaybackDetectorTest {
    private fun check(
        detector: AudioOnlyPlaybackDetector,
        nowMs: Long,
        positionMs: Long,
        isPlaying: Boolean = true,
        state: PlayerEngine.State = PlayerEngine.State.READY,
        hasVideoTrack: Boolean = true,
        hasRenderedVideoFrame: Boolean = false
    ) = detector.shouldRecover(
        nowMs = nowMs,
        isPlaying = isPlaying,
        state = state,
        positionMs = positionMs,
        hasVideoTrack = hasVideoTrack,
        hasRenderedVideoFrame = hasRenderedVideoFrame
    )

    @Test
    fun resumedPositionDoesNotCountAsProgressUntilItActuallyAdvances() {
        val detector = AudioOnlyPlaybackDetector(graceMs = 6_000L)

        assertFalse(check(detector, nowMs = 0L, positionMs = 314_000L))
        assertFalse(check(detector, nowMs = 10_000L, positionMs = 314_000L))
        assertFalse(check(detector, nowMs = 20_000L, positionMs = 314_000L))

        assertFalse(check(detector, nowMs = 20_500L, positionMs = 314_500L))
        assertFalse(check(detector, nowMs = 25_000L, positionMs = 319_000L))
        assertTrue(check(detector, nowMs = 26_500L, positionMs = 320_500L))
    }

    @Test
    fun pauseAndBufferingRestartTheGraceWindow() {
        val detector = AudioOnlyPlaybackDetector(graceMs = 6_000L)
        assertFalse(check(detector, 0L, 1_000L))
        assertFalse(check(detector, 500L, 1_500L))

        assertFalse(check(detector, 1_000L, 1_500L, isPlaying = false))
        assertFalse(check(detector, 1_500L, 1_500L, state = PlayerEngine.State.BUFFERING))
        assertFalse(check(detector, 2_000L, 1_500L))
        assertFalse(check(detector, 2_500L, 2_000L))

        assertFalse(check(detector, 7_500L, 7_000L))
        assertTrue(check(detector, 8_500L, 8_000L))
    }

    @Test
    fun visibleFrameClearsPendingAudioOnlyDetection() {
        val detector = AudioOnlyPlaybackDetector(graceMs = 6_000L)
        assertFalse(check(detector, 0L, 1_000L))
        assertFalse(check(detector, 500L, 1_500L))
        assertFalse(check(detector, 1_000L, 2_000L, hasRenderedVideoFrame = true))

        assertFalse(check(detector, 20_000L, 21_000L, hasRenderedVideoFrame = true))
        assertFalse(check(detector, 20_500L, 21_000L))
        assertFalse(check(detector, 21_000L, 21_500L))
        assertFalse(check(detector, 26_000L, 26_000L))
        assertTrue(check(detector, 27_000L, 27_000L))
    }

    @Test
    fun backwardSeekDoesNotReuseThePreviousProgressWindow() {
        val detector = AudioOnlyPlaybackDetector(graceMs = 6_000L)
        assertFalse(check(detector, 0L, 100_000L))
        assertFalse(check(detector, 500L, 100_500L))
        assertFalse(check(detector, 1_000L, 30_000L))
        assertFalse(check(detector, 1_500L, 30_500L))
        assertFalse(check(detector, 7_000L, 36_000L))
        assertTrue(check(detector, 7_500L, 36_500L))
    }
}
