package com.kitsugi.animelist.ui.screens.detail
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kitsugi.animelist.data.trailer.InAppYouTubeExtractor
import com.kitsugi.animelist.data.trailer.TrailerPlaybackSource
import com.kitsugi.animelist.data.trailer.YouTubeRequestPolicy
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.engine.PlayerEngineType
import com.kitsugi.animelist.core.player.engine.Media3PlayerEngine
import com.kitsugi.animelist.core.player.engine.MpvPlayerEngine
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R


// ---------------------------------------------------------------------------
//  YouTube ID Extractor
// ---------------------------------------------------------------------------
internal fun extractYouTubeId(url: String): String? {
    if (url.isBlank()) return null
    val patterns = listOf(
        Regex("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([A-Za-z0-9_-]{11})"),
        Regex("(?:youtube\\.com|youtube-nocookie\\.com)/embed/([A-Za-z0-9_-]{11})")
    )
    for (pattern in patterns) {
        val match = pattern.find(url)
        if (match != null) return match.groupValues[1]
    }
    return null
}

// ---------------------------------------------------------------------------
//  Inline YouTube Player (Native ExoPlayer tabanlı, InAppYouTubeExtractor ile)
// ---------------------------------------------------------------------------
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun KitsugiYouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var playbackSource by remember(videoId) { mutableStateOf<TrailerPlaybackSource?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var hasError by remember(videoId) { mutableStateOf(false) }
    var retryCount by remember(videoId) { mutableIntStateOf(0) }

    LaunchedEffect(videoId, retryCount) {
        isLoading = true
        hasError = false
        playbackSource = null
        try {
            val source = InAppYouTubeExtractor.shared
                .extractPlaybackSource("https://www.youtube.com/watch?v=$videoId")
            if (source != null) {
                playbackSource = source
            } else {
                hasError = true
            }
        } catch (e: CancellationException) {
            // Closing or switching videos cancels extraction; it is not a playback failure.
            throw e
        } catch (e: Exception) {
            Log.e("KitsugiYouTubePlayer", "Extraction error for $videoId", e)
            hasError = true
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KitsugiPlasmaLoader(size = 46.dp)
                Text(
                    text = stringResource(R.string.loading),
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    } else if (hasError || playbackSource == null) {
        Box(
            modifier = modifier
                .background(Color.Black)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.inline_failed_to_load_trailer_cc304f6),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitsugiButton(onClick = { retryCount++ }) {
                        Text(stringResource(R.string.action_retry), color = KitsugiColors.Background)
                    }
                    KitsugiButton(
                        onClick = {
                            runCatching {
                                uriHandler.openUri("https://www.youtube.com/watch?v=$videoId")
                            }
                        }
                    ) {
                        Text(stringResource(R.string.inline_open_in_browser_eed20ab), color = KitsugiColors.Background)
                    }
                }
            }
        }
    } else {
        val source = playbackSource!!
        val dataStore = remember { com.kitsugi.animelist.data.settings.SettingsDataStore(context.applicationContext) }
        val appSettingsState = dataStore.settingsFlow.collectAsState(initial = null)
        val appSettings = appSettingsState.value
        val safeSettings = remember(appSettings) { appSettings ?: com.kitsugi.animelist.data.settings.AppSettings() }

        // YouTube's signed Googlevideo/HLS URLs must use Media3's dedicated request policy,
        // just like the fullscreen player. MPV remains a one-time fallback if Media3 fails.
        var activeEngineType by remember(videoId) {
            mutableStateOf(
                initialInlineEngineType(
                    configuredPreference = appSettings?.playerPreference,
                    isYouTubeSource = true
                )
            )
        }
        var didTryAlternateEngine by remember(videoId) { mutableStateOf(false) }
        val playerEngine = remember(context, activeEngineType, videoId) {
            when (activeEngineType) {
                PlayerEngineType.MPV -> MpvPlayerEngine(context, safeSettings)
                else -> Media3PlayerEngine(context, safeSettings)
            }
        }

        var isPlayingState by remember(videoId) { mutableStateOf(false) }
        var currentPosition by remember(videoId) { mutableStateOf(0L) }
        var duration by remember(videoId) { mutableStateOf(0L) }
        // İlk kare göründükten sonra ara bellek dalgalanmaları yükleme animasyonunu
        // yeniden göstermemeli; aksi hâlde akıcı oynayan video "donuyor" gibi görünüyor.
        var hasStartedPlayback by remember(videoId) { mutableStateOf(false) }
        var playerState by remember(playerEngine) { mutableStateOf(PlayerEngine.State.IDLE) }
        val engineTypeForPlayer = activeEngineType
        val resumedState = rememberInlinePlayerResumed()

        fun switchToAlternateEngine(failedEngine: PlayerEngineType, reason: String) {
            if (failedEngine != activeEngineType) return
            if (!didTryAlternateEngine) {
                didTryAlternateEngine = true
                val alternate = failedEngine.alternateInlineEngine()
                Log.w("KitsugiYouTubePlayer", "$failedEngine did not start ($reason); trying $alternate")
                activeEngineType = alternate
            } else {
                Log.e("KitsugiYouTubePlayer", "Both inline engines failed for $videoId ($reason)")
                hasError = true
            }
        }

        DisposableEffect(playerEngine) {
            var released = false
            val listener = object : PlayerEngine.Listener {
                override fun onStateChanged(state: PlayerEngine.State) {
                    if (!released) {
                        playerState = state
                        isPlayingState = playerEngine.isPlaying
                    }
                }

                override fun onPlaybackError(errorCode: Int, errorMsg: String, cause: Throwable?) {
                    if (released) return
                    Log.e("KitsugiYouTubePlayer", "Playback error: $errorMsg (code: $errorCode)")
                    switchToAlternateEngine(engineTypeForPlayer, errorMsg)
                }

                override fun onPositionChanged(positionMs: Long, durationMs: Long) {
                    if (released) return
                    currentPosition = positionMs
                    duration = durationMs
                    if (positionMs > 0L) hasStartedPlayback = true
                }

                override fun onTracksChanged(
                    audioTracks: List<com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption>,
                    subtitleTracks: List<com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption>
                ) {}
            }
            playerEngine.addListener(listener)
            onDispose {
                released = true
                playerEngine.removeListener(listener)
                runCatching { playerEngine.pause() }
                playerEngine.release()
            }
        }

        // Arka plana geçildiğinde dur; geri dönünce kendiliğinden başlatma.
        LaunchedEffect(resumedState.value, playerEngine) {
            if (!resumedState.value) {
                runCatching { playerEngine.pause() }
                isPlayingState = false
            }
        }

        LaunchedEffect(source, activeEngineType) {
            val attemptedEngine = engineTypeForPlayer
            playerState = PlayerEngine.State.BUFFERING
            try {
                val reachedReady = withTimeoutOrNull(INLINE_PLAYER_START_TIMEOUT_MS) {
                    playerEngine.prepare(
                        videoUrl = source.videoUrl,
                        audioUrl = source.audioUrl,
                        headers = YouTubeRequestPolicy.headersForInlineUrl(source.videoUrl),
                        startPositionMs = currentPosition
                    )
                    if (!resumedState.value) {
                        runCatching { playerEngine.pause() }
                        isPlayingState = false
                        false
                    } else {
                        playerEngine.play()
                        // READY alone is not enough: a player may be prepared yet remain black
                        // at 00:00 / 00:00. Require real timeline progress so the alternate
                        // engine fallback also catches a stalled-but-READY YouTube stream.
                        while (!hasInlinePlaybackProgress(playerEngine.currentState, playerEngine.currentPosition)) {
                            if (!resumedState.value) return@withTimeoutOrNull false
                            delay(250L)
                        }
                        true
                    }
                }
                if (reachedReady == null) {
                    switchToAlternateEngine(attemptedEngine, "no playback progress within ${INLINE_PLAYER_START_TIMEOUT_MS}ms")
                    return@LaunchedEffect
                }
                if (!reachedReady) return@LaunchedEffect

                // Ses çalarken video parçası hiç seçilmemiş olabilir (cihazın çözemediği
                // kodek ya da 403 dönen imzalı YouTube video akışı). Media3 bu durumda
                // hata üretmediği için ekran siyah kalır; bekçi diğer motora geçer.
                val audioOnly = waitForInlineVideoTrack(
                    engine = playerEngine,
                    isResumed = { resumedState.value }
                )
                if (audioOnly && attemptedEngine == activeEngineType) {
                    switchToAlternateEngine(attemptedEngine, "audio-only playback (no video track selected)")
                }
            } catch (e: CancellationException) {
                // The player can be disposed while prepare or network IO is in flight.
                throw e
            } catch (e: Exception) {
                if (attemptedEngine != activeEngineType) return@LaunchedEffect
                Log.e("KitsugiYouTubePlayer", "Prepare error", e)
                switchToAlternateEngine(attemptedEngine, e.message ?: e.javaClass.simpleName)
            }
        }

        LaunchedEffect(playerEngine, isPlayingState) {
            if (isPlayingState) {
                while (true) {
                    currentPosition = playerEngine.currentPosition
                    duration = playerEngine.duration.coerceAtLeast(0L)
                    delay(500)
                }
            }
        }

        KitsugiPlayerGestureWrapper(
            isPlaying = isPlayingState,
            currentPosition = currentPosition,
            duration = duration,
            onPlayPause = {
                if (isPlayingState) playerEngine.pause() else playerEngine.play()
            },
            onSeek = { target ->
                playerEngine.seekTo(target)
            },
            modifier = modifier,
            onFullscreen = {
                playerEngine.pause()
                KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                    context = context,
                    videoUrl = source.videoUrl,
                    audioUrl = source.audioUrl,
                    headers = YouTubeRequestPolicy.headersForInlineUrl(source.videoUrl)
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // KRİTİK: `AndroidView`'ın `factory`'si yalnızca düğüm ilk oluşturulurken
                // çalışır. Yedek motor devreye girdiğinde `playerEngine` değişiyor ama
                // aynı konumdaki AndroidView eski (artık serbest bırakılmış) motorun
                // görünümünü tutmaya devam ediyordu: yeni motorun hiç yüzeyi olmuyor,
                // ses çalıyor ve ekran siyah kalıyordu. `key(playerEngine)` düğümü yeni
                // motorla yeniden kurar; her motor kendi görünümünü alır.
                key(playerEngine) {
                    AndroidView(
                        factory = { ctx ->
                            playerEngine.createVideoView(ctx)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (playerState == PlayerEngine.State.BUFFERING && !hasStartedPlayback) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiPlasmaLoader(size = 38.dp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Satır içi oynatıcı kontrolleri (Gesture Wrapper)
//
//  - Tek dokunma: kontrolleri göster / gizle (oynatılırken 3 sn sonra kendiliğinden gizlenir).
//  - Ortadaki büyük düğme: oynat / duraklat. Alt çubuktaki düğme de aynısını yapar.
//  - Alt çubuk: sürüklenebilir ilerleme çubuğu, süre bilgisi ve tam ekran düğmesi.
//  - Çift dokunma: sol/sağ yarıdan 10 sn geri / ileri.
//  Tüm düğmeler kendi dokunma alanına sahiptir; oynatıcı yüzeyine düşen dokunmalar
//  ayrıca çift dokunma / kontrol gizleme için kullanılır.
// ---------------------------------------------------------------------------
@Composable
fun KitsugiPlayerGestureWrapper(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val latestPosition by rememberUpdatedState(currentPosition)
    val latestDuration by rememberUpdatedState(duration)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnPlayPause by rememberUpdatedState(onPlayPause)

    var controlsVisible by remember { mutableStateOf(true) }
    var controlsToken by remember { mutableIntStateOf(0) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }

    // Duraklatılmışken kontroller hep görünür; oynarken dokunmadan sonra gizlenir.
    val showControls = controlsVisible || !isPlaying

    LaunchedEffect(controlsVisible, controlsToken, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekFeedbackText) {
        if (seekFeedbackText != null) {
            delay(650)
            seekFeedbackText = null
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Video yüzeyi
        content()

        // Dokunma katmanı: tek dokunma kontrolleri açar/kapatır, çift dokunma 5 sn atlatır.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val isRightSide = offset.x > size.width / 2f
                            val total = latestDuration
                            val target = if (isRightSide) {
                                val forward = latestPosition + 10_000L
                                if (total > 0L) forward.coerceAtMost(total) else forward
                            } else {
                                (latestPosition - 10_000L).coerceAtLeast(0L)
                            }
                            latestOnSeek(target)
                            seekFeedbackText = if (isRightSide) "+10s" else "-10s"
                            controlsVisible = true
                            controlsToken++
                        },
                        onTap = {
                            if (controlsVisible) {
                                controlsVisible = false
                            } else {
                                controlsVisible = true
                                controlsToken++
                            }
                        }
                    )
                }
        )

        // Seek (+/- 5s) geri bildirimi
        AnimatedVisibility(
            visible = seekFeedbackText != null,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (seekFeedbackText?.startsWith("+") == true) Icons.FastForward else Icons.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = seekFeedbackText ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Ortadaki oynat / duraklat düğmesi.
        // Oynarken gizli kalır: videonun ortasında sürekli duran büyük "duraklat"
        // simgesi hem görüntüyü kapatıyor hem de ucuz duruyordu. Duraklatınca
        // (veya kontroller açıkken duraklatılmışsa) ortaya gelir; oynatırken kontrol
        // çubuğundaki düğme kullanılır.
        AnimatedVisibility(
            visible = showControls && !isPlaying,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .tvClickable(shape = CircleShape) {
                        latestOnPlayPause()
                        controlsVisible = true
                        controlsToken++
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Pause else Icons.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.inline_player_pause else R.string.inline_player_play),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Alt kontrol çubuğu: ilerleme, süre, oynat/duraklat ve tam ekran
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
                    .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 4.dp)
            ) {
                InlinePlayerSeekBar(
                    positionMs = currentPosition,
                    durationMs = duration,
                    accentColor = accentColor,
                    onSeekFraction = { fraction ->
                        val total = latestDuration
                        if (total > 0L) latestOnSeek((total * fraction).toLong())
                        controlsVisible = true
                        controlsToken++
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .tvClickable(shape = CircleShape) {
                                latestOnPlayPause()
                                controlsVisible = true
                                controlsToken++
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Pause else Icons.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.inline_player_pause else R.string.inline_player_play),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${formatInlinePlayerTime(currentPosition)} / ${formatInlinePlayerTime(duration)}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (onFullscreen != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .tvClickable(shape = CircleShape) { onFullscreen() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Fullscreen,
                                contentDescription = stringResource(R.string.inline_player_fullscreen),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Satır içi oynatıcının ilerleme çubuğu. Dokunarak ya da sürükleyerek konum seçilir;
 * sürükleme bitene kadar çubuk parmağı izler, bitince tek seferde arama yapılır.
 */
@Composable
private fun InlinePlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    accentColor: Color,
    onSeekFraction: (Float) -> Unit
) {
    val canSeek = durationMs > 0L
    val basis = if (canSeek) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val isDragging = dragFraction >= 0f
    val shown = if (isDragging) dragFraction else basis
    val latestOnSeekFraction by rememberUpdatedState(onSeekFraction)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(canSeek) {
                if (canSeek) {
                    detectTapGestures(
                        onTap = { offset ->
                            latestOnSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    )
                }
            }
            .pointerInput(canSeek) {
                if (canSeek) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (dragFraction >= 0f) latestOnSeekFraction(dragFraction)
                            dragFraction = -1f
                        },
                        onDragCancel = { dragFraction = -1f }
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(shown.coerceAtLeast(0.001f))
                .height(3.dp)
                .background(accentColor)
        )
        Box(
            modifier = Modifier
                .size(if (isDragging) 15.dp else 11.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Box(
            modifier = Modifier
                .weight((1f - shown).coerceAtLeast(0.001f))
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )
    }
}

private fun formatInlinePlayerTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
