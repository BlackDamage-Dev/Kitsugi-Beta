package com.kitsugi.animelist.core.player.engine

/** Recovery error used when the timeline/audio advances but no video frame reaches the output. */
internal const val AUDIO_ONLY_PLAYBACK_ERROR_CODE = 9103
internal const val AUDIO_ONLY_PLAYBACK_GRACE_MS = 6_000L

/**
 * Detects the otherwise silent "audio plays, screen stays black" failure.
 *
 * READY and a non-zero position are not sufficient: a resumed video already has a non-zero
 * position and Media3/MPV can both keep playing audio when video decoding or output fails.
 * This detector therefore requires fresh timeline progress while playback is active, then
 * allows a grace period for the first video frame. Pausing, buffering, seeking backwards or
 * seeing a valid frame resets the detection window.
 *
 * The class is Android/JNI independent so the recovery policy can be tested without a device.
 */
internal class AudioOnlyPlaybackDetector(
    private val graceMs: Long = AUDIO_ONLY_PLAYBACK_GRACE_MS,
    private val minimumProgressDeltaMs: Long = 100L
) {
    private var lastPositionMs: Long? = null
    private var firstProgressAtMs: Long? = null

    fun shouldRecover(
        nowMs: Long,
        isPlaying: Boolean,
        state: PlayerEngine.State,
        positionMs: Long,
        hasVideoTrack: Boolean,
        hasRenderedVideoFrame: Boolean
    ): Boolean {
        if (hasVideoTrack && hasRenderedVideoFrame) {
            reset()
            return false
        }

        if (!isPlaying || state != PlayerEngine.State.READY) {
            reset()
            return false
        }

        val safePosition = positionMs.coerceAtLeast(0L)
        val previousPosition = lastPositionMs
        lastPositionMs = safePosition

        if (previousPosition == null) return false
        if (safePosition < previousPosition) {
            // A seek/new load is a new observation window, not continuous audio progress.
            firstProgressAtMs = null
            return false
        }
        if (safePosition - previousPosition < minimumProgressDeltaMs) return false

        val progressStartedAt = firstProgressAtMs
        if (progressStartedAt == null) {
            firstProgressAtMs = nowMs
            return false
        }

        return nowMs - progressStartedAt >= graceMs
    }

    fun reset() {
        lastPositionMs = null
        firstProgressAtMs = null
    }
}
