package com.kitsugi.animelist.ui.screens.fullscreen.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import com.kitsugi.animelist.core.player.engine.PlayerEngineType

/**
 * T1.6 – PlayerRuntimeOrchestrator
 *
 * KitsugiPlayerViewModel'in tüm runtime controller'larını bir arada tutan
 * üst düzey kapsayıcı. Her controller tek bir sorumluluğa sahiptir:
 *
 * - [stillWatching]  → StillWatching prompt + binge session kuralları
 * - [audio]          → Ses rotası tespiti + route-bazlı gecikme uygulama
 * - [skip]           → AniSkip + AnimeSkip zaman damgası yükleme
 * - [errorRecovery]  → Engine + source fallback, retry zinciri
 *
 * ViewModel bu orchestrator'ı init bloğunda oluşturur ve tüm
 * controller state'lerini doğrudan orchestrator üzerinden okur.
 */
class PlayerRuntimeOrchestrator(
    private val scope: CoroutineScope,
    private val context: Context,
    getAniListToken: () -> String? = { null },
    onRetry: (attempt: Int) -> Unit = {},
    onFallback: () -> Unit = {},
    onFatal: (errorCode: Int, errorMsg: String) -> Unit = { _, _ -> },
    onAutoPlayNext: () -> Unit = {},
    onLoop: () -> Unit = {},
    onShowStillWatching: () -> Unit = {},
    /** "Hâlâ izliyor musunuz?" geri sayımı bittiğinde çağrılır (oynatmayı duraklat). */
    onStillWatchingTimeout: () -> Unit = {},
    onShowEndPrompt: () -> Unit = {},
    onCountdownTick: (remaining: Int) -> Unit = {},
    /** Called when a playback error triggers an engine switch (MEDIA3 → MPV). */
    onSwitchEngine: ((to: PlayerEngineType) -> Unit)? = null,
    /** Lambda returning the currently active engine type. */
    getCurrentEngine: (() -> PlayerEngineType)? = null,
    /** Whether the user has MPV enabled. */
    isMpvEnabled: (() -> Boolean)? = null
) {
    private val TAG = "PlayerOrchestrator"

    // ── Controller instances ───────────────────────────────────────────────────

    val stillWatching: PlayerStillWatchingController =
        PlayerStillWatchingController(scope, onPromptTimedOut = onStillWatchingTimeout)

    val audio: PlayerAudioController =
        PlayerAudioController(scope, context)

    val skip: PlayerSkipController =
        PlayerSkipController(scope, context)

    val errorRecovery: PlayerErrorRecoveryController =
        PlayerErrorRecoveryController(
            scope = scope,
            onRetry = onRetry,
            onFallback = onFallback,
            onFatal = onFatal,
            onSwitchEngine = onSwitchEngine,
            getCurrentEngine = getCurrentEngine,
            isMpvEnabled = isMpvEnabled,
            context = context
        )

    val scrobble: PlayerScrobbleController =
        PlayerScrobbleController(scope, context, getAniListToken)

    /**
     * Otomatik liste takibi: **hangi kaynaktan** açılırsa açılsın oynatılan içeriği
     * listeye işler ve altı kaynağa yayar. [scrobble]'dan farkı, tek bir platforma
     * bağlı olmaması ve ilerlemeyi bölüm numarasına göre yazmasıdır.
     */
    val autoTrack: PlayerAutoTrackController =
        PlayerAutoTrackController(scope, context)

    val autoplay: PlayerAutoplayController =
        PlayerAutoplayController(
            scope = scope,
            onAutoPlayNext = onAutoPlayNext,
            onLoop = onLoop,
            onShowStillWatching = onShowStillWatching,
            onShowEndPrompt = onShowEndPrompt,
            onCountdownTick = onCountdownTick
        )

    val live: com.kitsugi.animelist.core.player.live.LiveManager =
        com.kitsugi.animelist.core.player.live.LiveManager(scope)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Orchestrator'ı başlatır.
     * VM.init { } bloğunda bir kez çağrılır.
     */
    fun start(
        getActiveEngine: () -> com.kitsugi.animelist.core.player.engine.PlayerEngine?,
        liveHelperEnabled: Boolean = false
    ) {
        Log.d(TAG, "Starting all runtime controllers")
        skip.observeSettings()
        audio.startObserving(getActiveEngine)
        if (liveHelperEnabled) {
            live.start(
                getPositionMs = { getActiveEngine()?.currentPosition ?: 0L },
                getDurationMs = { getActiveEngine()?.duration ?: 0L }
            )
        }
    }

    /**
     * Orchestrator'ı durdurur.
     * VM.onCleared() içinde çağrılır.
     */
    fun stop() {
        Log.d(TAG, "Stopping all runtime controllers")
        audio.stopObserving()
        scrobble.clear()
        autoTrack.clear()
        autoplay.cancelCountdown()
        live.stop()
    }
}


