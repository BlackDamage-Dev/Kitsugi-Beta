package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.engine.PlayerEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.kitsugi.animelist.data.settings.AppSettings
import org.junit.Test

class InlinePlayerSupportTest {
    @Test
    fun playerEnginesAlternateInBothDirections() {
        assertEquals(PlayerEngineType.MPV, PlayerEngineType.MEDIA3.alternateInlineEngine())
        assertEquals(PlayerEngineType.MEDIA3, PlayerEngineType.MPV.alternateInlineEngine())
    }

    @Test
    fun youtubeInlinePlaybackUsesMedia3BeforeMpvRegardlessOfGeneralPreference() {
        assertEquals(PlayerEngineType.MEDIA3, initialInlineEngineType("MPV", isYouTubeSource = true))
        assertEquals(PlayerEngineType.MEDIA3, initialInlineEngineType("MEDIA3", isYouTubeSource = true))
    }

    @Test
    fun directInlineVideosKeepConfiguredEnginePreference() {
        assertEquals(PlayerEngineType.MPV, initialInlineEngineType("MPV", isYouTubeSource = false))
        assertEquals(PlayerEngineType.MEDIA3, initialInlineEngineType("MEDIA3", isYouTubeSource = false))
        assertEquals(PlayerEngineType.MEDIA3, initialInlineEngineType(null, isYouTubeSource = false))
    }

    @Test
    fun compactThemePlaybackStartsWithMedia3ForReliableComposeRendering() {
        // Fragman (Media3 + TextureView) aynı kartta çalışırken OP/ED (MPV + SurfaceView)
        // siyah kalıyordu; kompakt tema oynatıcısı artık her zaman Media3 ile başlar.
        assertEquals(PlayerEngineType.MEDIA3, initialCompactThemeEngineType())
    }

    @Test
    fun nonWebmCompactThemeStartsOnMedia3WithoutTouchingDeviceCodecs() {
        // `.mp4`/`.m4v` kaynaklarda VP9 ön kontrolü yapılmaz: her cihazda Media3 ile
        // başlanır, gerekiyorsa bekçi MPV'ye geçer.
        assertEquals(
            PlayerEngineType.MEDIA3,
            initialCompactThemeEngineType("https://cdn.example/opening-1.mp4")
        )
        assertEquals(PlayerEngineType.MEDIA3, initialCompactThemeEngineType(""))
    }

    @Test
    fun selectedButUnrenderedVideoIsStillAudioOnlyAfterGrace() {
        // hasVisibleVideo = hasVideoTrack && hasRenderedVideoFrame olduğu için, video
        // parçası seçili olsa da hiç kare çizilmediyse bekçi ses-only hükmü verir.
        assertFalse(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS - 1L,
                state = PlayerEngine.State.READY,
                positionMs = 4_000L,
                hasVisibleVideo = false
            )
        )
        assertTrue(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS,
                state = PlayerEngine.State.READY,
                positionMs = 4_000L,
                hasVisibleVideo = false
            )
        )
    }

    @Test
    fun preparedButStalledPlayerDoesNotCountAsStarted() {
        assertFalse(hasInlinePlaybackProgress(PlayerEngine.State.READY, 0L))
        assertFalse(hasInlinePlaybackProgress(PlayerEngine.State.BUFFERING, 0L))
        assertTrue(hasInlinePlaybackProgress(PlayerEngine.State.READY, 1L))
        assertTrue(hasInlinePlaybackProgress(PlayerEngine.State.ENDED, 0L))
    }

    @Test
    fun audioOnlyPlaybackIsNotDetectedDuringTheGracePeriod() {
        // Video parçası henüz seçilmemiş olabilir: ses başladı diye hemen motor değişmemeli.
        assertFalse(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS - 1_000L,
                state = PlayerEngine.State.READY,
                positionMs = 3_500L,
                hasVisibleVideo = false
            )
        )
    }

    @Test
    fun audioOnlyPlaybackIsDetectedWhenTimelineAdvancesWithoutVideo() {
        assertTrue(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS,
                state = PlayerEngine.State.READY,
                positionMs = 6_200L,
                hasVisibleVideo = false
            )
        )
    }

    @Test
    fun videoFrameVerdictNudgesTheRendererBeforeSwitchingEngines() {
        // Ses ilerliyor ama kare yok: önce çözücüyü dürt (motoru değiştirme).
        assertEquals(
            InlineVideoVerdict.NUDGE_RENDERER,
            inlineVideoVerdict(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS,
                state = PlayerEngine.State.READY,
                positionMs = 2_000L,
                hasVisibleVideo = false,
                nudgeUsed = false
            )
        )
        // Dürtmeden sonra tanınan ek süre doldu: artık yedek motora geçilir.
        assertEquals(
            InlineVideoVerdict.SWITCH_ENGINE,
            inlineVideoVerdict(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS + INLINE_NUDGE_GRACE_MS,
                state = PlayerEngine.State.READY,
                positionMs = 4_000L,
                hasVisibleVideo = false,
                nudgeUsed = true
            )
        )
    }

    @Test
    fun videoFrameVerdictWaitsBeforeTheGracePeriod() {
        assertEquals(
            InlineVideoVerdict.WAITING,
            inlineVideoVerdict(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS - 1L,
                state = PlayerEngine.State.READY,
                positionMs = 1_000L,
                hasVisibleVideo = false,
                nudgeUsed = false
            )
        )
        // Zaman çizgisi hiç ilerlemiyorsa (henüz tampon) hüküm verilmez.
        assertEquals(
            InlineVideoVerdict.WAITING,
            inlineVideoVerdict(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS * 4,
                state = PlayerEngine.State.BUFFERING,
                positionMs = 0L,
                hasVisibleVideo = false,
                nudgeUsed = false
            )
        )
    }

    @Test
    fun videoFrameVerdictStopsWatchingOnlyWhenAFrameIsActuallyDrawn() {
        assertEquals(
            InlineVideoVerdict.HEALTHY,
            inlineVideoVerdict(
                elapsedMs = INLINE_VIDEO_FRAME_MAX_WAIT_MS,
                state = PlayerEngine.State.READY,
                positionMs = 10_000L,
                hasVisibleVideo = true,
                nudgeUsed = true
            )
        )
        // Eski bekçi 10. saniyede pes ediyordu; yavaş açılan dosya sonsuza kadar
        // siyah kalıyordu. Yeni bekçi üst sınıra kadar izler, sonra motoru değiştirir.
        assertEquals(
            InlineVideoVerdict.SWITCH_ENGINE,
            inlineVideoVerdict(
                elapsedMs = INLINE_VIDEO_FRAME_MAX_WAIT_MS,
                state = PlayerEngine.State.READY,
                positionMs = 30_000L,
                hasVisibleVideo = false,
                nudgeUsed = true
            )
        )
        assertTrue(INLINE_VIDEO_FRAME_MAX_WAIT_MS > INLINE_AUDIO_ONLY_GRACE_MS + 4_000L)
    }

    @Test
    fun healthyVideoPlaybackNeverTriggersTheAudioOnlyGuard() {
        assertFalse(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS * 3,
                state = PlayerEngine.State.READY,
                positionMs = 20_000L,
                hasVisibleVideo = true
            )
        )
        // Ses ilerlemiyorsa (hâlâ 0 ms) tanı konmaz; bu ayrı bir bekleme durumudur.
        assertFalse(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS * 3,
                state = PlayerEngine.State.BUFFERING,
                positionMs = 0L,
                hasVisibleVideo = false
            )
        )
    }

    @Test
    fun slowButProgressingStartIsNotAbandoned() {
        // Yavaş bağlantı: hazırlık uzun sürüyor ama tampon büyümeye devam ediyor.
        // Eski sabit 18 sn kuralı bu dosyayı "oynatılamadı" kartına düşürüyordu.
        assertFalse(
            inlineStartShouldAbandon(
                elapsedMs = 60_000L,
                sinceProgressMs = 1_000L,
                state = PlayerEngine.State.BUFFERING,
                positionMs = 0L
            )
        )
    }

    @Test
    fun genuineStallSwitchesToTheBackupEngine() {
        assertTrue(
            inlineStartShouldAbandon(
                elapsedMs = 30_000L,
                sinceProgressMs = INLINE_PLAYER_STALL_TIMEOUT_MS,
                state = PlayerEngine.State.BUFFERING,
                positionMs = 0L
            )
        )
        assertFalse(
            inlineStartShouldAbandon(
                elapsedMs = 30_000L,
                sinceProgressMs = INLINE_PLAYER_STALL_TIMEOUT_MS - 1L,
                state = PlayerEngine.State.BUFFERING,
                positionMs = 0L
            )
        )
    }

    @Test
    fun hardCapPreventsAnEndlessSkeleton() {
        assertTrue(
            inlineStartShouldAbandon(
                elapsedMs = INLINE_PLAYER_START_HARD_CAP_MS,
                sinceProgressMs = 0L,
                state = PlayerEngine.State.BUFFERING,
                positionMs = 0L
            )
        )
        // Oynatma gerçekten ilerliyorsa hiçbir süre hükmü verilmez.
        assertFalse(
            inlineStartShouldAbandon(
                elapsedMs = INLINE_PLAYER_START_HARD_CAP_MS * 2,
                sinceProgressMs = 0L,
                state = PlayerEngine.State.READY,
                positionMs = 4_000L
            )
        )
    }

    @Test
    fun sourceFailureMovesToDifferentUrlBeforeChangingEngine() {
        val failed = InlinePlaybackAttempt(0, PlayerEngineType.MEDIA3)
        val next = nextInlinePlaybackAttempt(
            failed = failed,
            failure = InlinePlaybackFailure.SOURCE,
            sourceCount = 3,
            attempted = setOf(failed)
        )

        assertEquals(InlinePlaybackAttempt(1, PlayerEngineType.MEDIA3), next)
    }

    @Test
    fun audioOnlyFailureKeepsSourceAndChangesDecoderFirst() {
        val failed = InlinePlaybackAttempt(1, PlayerEngineType.MEDIA3)
        val next = nextInlinePlaybackAttempt(
            failed = failed,
            failure = InlinePlaybackFailure.AUDIO_ONLY,
            sourceCount = 3,
            attempted = setOf(failed)
        )

        assertEquals(InlinePlaybackAttempt(1, PlayerEngineType.MPV), next)
    }

    @Test
    fun fallbackNeverRepeatsAttemptAndStopsAtHardCap() {
        var current = InlinePlaybackAttempt(0, PlayerEngineType.MEDIA3)
        val attempted = mutableSetOf<InlinePlaybackAttempt>()
        val sequence = mutableListOf<InlinePlaybackAttempt>()

        repeat(MAX_INLINE_PLAYBACK_ATTEMPTS + 2) {
            attempted += current
            val next = nextInlinePlaybackAttempt(
                failed = current,
                failure = InlinePlaybackFailure.SOURCE,
                sourceCount = 3,
                attempted = attempted
            ) ?: return@repeat
            assertFalse(next in attempted)
            sequence += next
            current = next
        }

        assertEquals(MAX_INLINE_PLAYBACK_ATTEMPTS - 1, sequence.size)
        assertNull(
            nextInlinePlaybackAttempt(
                failed = current,
                failure = InlinePlaybackFailure.SOURCE,
                sourceCount = 3,
                attempted = attempted
            )
        )
    }

    @Test
    fun compactPlaybackUsesSmallStartupBufferWithoutMutatingFullscreenSettings() {
        val original = AppSettings()
        val compact = original.forCompactInlinePlayback()

        assertEquals(1_000, compact.bufferForPlaybackMs)
        assertEquals(2_000, compact.bufferForPlaybackAfterRebufferMs)
        assertEquals(5_000, compact.minBufferMs)
        assertEquals(20_000, compact.maxBufferMs)
        assertEquals(3_500, original.bufferForPlaybackMs)
        assertEquals(45_000, original.maxBufferMs)
    }

    @Test
    fun compactPlaybackDoesNotIncreaseAlreadySmallerBuffers() {
        val original = AppSettings(
            playback = com.kitsugi.animelist.data.settings.PlayerPlaybackSettings(
                minBufferMs = 4_000,
                maxBufferMs = 10_000,
                bufferForPlaybackMs = 500,
                bufferForPlaybackAfterRebufferMs = 1_000
            )
        ).forCompactInlinePlayback()

        assertEquals(4_000, original.minBufferMs)
        assertEquals(10_000, original.maxBufferMs)
        assertEquals(500, original.bufferForPlaybackMs)
        assertEquals(1_000, original.bufferForPlaybackAfterRebufferMs)
    }
}
