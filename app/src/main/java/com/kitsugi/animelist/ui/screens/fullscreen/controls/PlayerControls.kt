package com.kitsugi.animelist.ui.screens.fullscreen.controls
import com.kitsugi.animelist.ui.screens.fullscreen.playerSurfaceColor
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.BrightnessSlider
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.VolumeSlider
import com.kitsugi.animelist.core.player.PlaybackState
import com.kitsugi.animelist.core.player.PlayerPipHelper
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiDialogs
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiPlayerViewModel
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiSheets
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.SeekbarWithTimers
import androidx.compose.ui.platform.LocalContext
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

// How long (ms) controls stay visible when playing
private const val CONTROLS_HIDE_DELAY_MS = 5_000L

/**
 * Top-level OSD controls composable.
 *
 * Binds directly to [KitsugiPlayerViewModel] — no prop-drilling.
 * The hide-timer lives here via LaunchedEffect (Aniyomi pattern).
 *
 * @param onBackClick   Navigate back / exit player.
 * @param onRotateClick Cycle screen orientation.
 */
@Composable
fun PlayerControls(
    viewModel: KitsugiPlayerViewModel,
    onBackClick: () -> Unit,
    onRotateClick: () -> Unit,
    onAspectClick: () -> Unit,
    onOpenExternalPlayer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // ── Collect all state from ViewModel ──────────────────────────────────────
    val controlsShown     by viewModel.controlsShown.collectAsState()
    val areControlsLocked by viewModel.areControlsLocked.collectAsState()
    val sheetShown        by viewModel.sheetShown.collectAsState()
    val panelShown        by viewModel.panelShown.collectAsState()
    val dialogShown       by viewModel.dialogShown.collectAsState()

    val isAnyMenuShown = sheetShown != KitsugiSheets.None ||
            panelShown != com.kitsugi.animelist.ui.screens.fullscreen.KitsugiPanels.None ||
            dialogShown != KitsugiDialogs.None

    val areControlsVisible = controlsShown && !isAnyMenuShown
    val paused            by viewModel.paused.collectAsState()
    val isLoading         by viewModel.isLoading.collectAsState()
    val isLoadingEpisode  by viewModel.nextEpisodeLoading.collectAsState()
    val posMs             by viewModel.pos.collectAsState()
    val durationMs        by viewModel.duration.collectAsState()
    val hasPrevious       by viewModel.hasPreviousEpisode.collectAsState()
    val hasNext           by viewModel.hasNextEpisode.collectAsState()
    val chapters          by viewModel.chapters.collectAsState()
    val currentChapter    by viewModel.currentChapter.collectAsState()
    val animeTitle        by viewModel.animeTitleFlow.collectAsState()
    val currentTitle      by viewModel.currentTitle.collectAsState()
    val settings          by viewModel.appSettings.collectAsState()
    val skipIntervals     by viewModel.skipIntervals.collectAsState()
    val bingeCountdown    by viewModel.bingeCountdownSec.collectAsState()
    val showBingeCard     by viewModel.showBingeCardState.collectAsState()
    val playerState       by viewModel.playerState.collectAsState()
    val controlsResetTrigger by viewModel.controlsResetTrigger.collectAsState()
    val customButton      by viewModel.primaryButton.collectAsState()
    val customButtonTitle by viewModel.primaryButtonTitle.collectAsState()
    val playbackSpeed     by viewModel.playbackSpeed.collectAsState()
    val context           = LocalContext.current

    val showLoadingCircle = settings.showLoadingCircle && (
        isLoading || isLoadingEpisode || playerState is PlaybackState.Buffering
    )
    val seekBarShown by viewModel.seekBarShown.collectAsState()
    val gestureSeekAmount by viewModel.gestureSeekAmount.collectAsState()
    val reduceMotion = settings.reducePlayerMotion
    val panelAlpha = com.kitsugi.animelist.core.player.PlayerUiPolicy.panelAlpha(settings.panelOpacity)

    // Formatted title respecting settings
    val mediaTitle = remember(currentTitle, settings.showPlayerTitle, settings.titleLimitType) {
        if (!settings.showPlayerTitle) {
            ""
        } else {
            when (settings.titleLimitType) {
                "LIMIT_20" -> if (currentTitle.length > 20) currentTitle.take(20) + "…" else currentTitle
                "LIMIT_40" -> if (currentTitle.length > 40) currentTitle.take(40) + "…" else currentTitle
                else       -> currentTitle
            }
        }
    }

    // Skip-intro button text (AniSkip aralığı varsa dinamik, yoksa null)
    val currentPosSec = posMs / 1000f
    val activeSkipInterval = remember(skipIntervals, currentPosSec) {
        skipIntervals.firstOrNull { interval ->
            currentPosSec >= interval.startTime && currentPosSec < interval.endTime
        }
    }
    var netflixSkipCountdownSec by remember(activeSkipInterval) { mutableStateOf<Int?>(null) }
    var dismissedSkipInterval by remember { mutableStateOf<com.kitsugi.animelist.data.remote.SkipInterval?>(null) }
    LaunchedEffect(
        activeSkipInterval,
        settings.aniSkipAutoSkip,
        settings.enableNetflixStyleIntroSkip,
        settings.waitingTimeIntroSkip,
        paused
    ) {
        val interval = activeSkipInterval
        if (interval == null || interval == dismissedSkipInterval || !settings.aniSkipAutoSkip || !settings.enableNetflixStyleIntroSkip) {
            netflixSkipCountdownSec = null
            return@LaunchedEffect
        }
        val waitSec = settings.waitingTimeIntroSkip.coerceIn(1, 30)
        for (elapsed in 0..waitSec) {
            val remaining = com.kitsugi.animelist.core.player.PlayerUiPolicy.computeSkipCountdownRemainingSec(
                autoSkipEnabled = settings.aniSkipAutoSkip,
                netflixStyleEnabled = settings.enableNetflixStyleIntroSkip,
                waitingTimeSec = waitSec,
                elapsedInIntervalSec = elapsed
            )
            netflixSkipCountdownSec = remaining
            if (remaining != null && remaining <= 0) {
                viewModel.seekTo((interval.endTime * 1000f).toLong())
                netflixSkipCountdownSec = null
                break
            }
            while (paused) delay(200)
            delay(1000)
        }
    }

    val skipIntroText = remember(activeSkipInterval, netflixSkipCountdownSec) {
        activeSkipInterval?.let { interval ->
            val baseLabel = when (interval.type) {
                "intro" -> "Giriş'i Atla"
                "outro" -> "Bitişi Atla"
                "recap" -> "Özeti Atla"
                else    -> "Atla"
            }
            val countdown = netflixSkipCountdownSec
            if (countdown != null && countdown > 0) "$baseLabel (${countdown}s)" else baseLabel
        }
    }

    // Sabit skip-intro süresi (SharedPrefs'ten, varsayılan 85sn)
    val introSkipLength = remember { viewModel.getAnimeSkipIntroLength() }

    // Generate lazily on the first drag; disk decode never runs on the UI thread.
    val previewUrl by viewModel.currentVideoUrl.collectAsState()
    val previewHeaders by viewModel.currentHeaders.collectAsState()
    val previewGenerator = remember(previewUrl, previewHeaders, settings.previewSeekbarEnabled) {
        previewUrl?.takeIf { settings.previewSeekbarEnabled && it.isNotBlank() }?.let {
            com.kitsugi.animelist.ui.screens.fullscreen.components.PreviewGenerator(context, it, previewHeaders)
        }
    }
    var previewPosition by remember(previewUrl) { mutableStateOf<Float?>(null) }
    var previewImage by remember(previewUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    androidx.compose.runtime.DisposableEffect(previewGenerator) {
        onDispose { previewGenerator?.release() }
    }
    LaunchedEffect(previewGenerator, previewPosition, durationMs) {
        previewImage = null
        val position = previewPosition ?: return@LaunchedEffect
        val generator = previewGenerator ?: return@LaunchedEffect
        if (durationMs <= 0L) return@LaunchedEffect
        generator.start(durationMs)
        while (true) {
            previewImage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                generator.getPreviewImage((position * 1000f / durationMs).coerceIn(0f, 1f))
            }
            delay(250)
        }
    }

    // Chapters as Segment list for seekbar
    val seekSegments = remember(chapters) {
        chapters.map { it.toSegment() }.toImmutableList()
    }

    // ── Hide-controls timer (Aniyomi LaunchedEffect pattern) ─────────────────
    var resetTimerTrigger by remember { mutableStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    val hideDelayMs = com.kitsugi.animelist.core.player.PlayerUiPolicy.clampOsdHideDelayMs(settings.playerTimeToDisappearMs)
    LaunchedEffect(controlsShown, paused, isScrubbing, resetTimerTrigger, controlsResetTrigger, hideDelayMs) {
        // A drag is an active user interaction: never remove the seekbar from under
        // the pointer. Start a fresh hide interval only after scrubbing ends.
        if (controlsShown && !paused && !isScrubbing) {
            delay(hideDelayMs)
            viewModel.hideControls()
        }
    }

    val isBrightnessSliderShown by viewModel.isBrightnessSliderShown.collectAsState()
    val isVolumeSliderShown by viewModel.isVolumeSliderShown.collectAsState()
    val brightness by viewModel.currentBrightness.collectAsState()
    val volume by viewModel.currentVolume.collectAsState()
    val mpvVolume by viewModel.currentMPVVolume.collectAsState()

    LaunchedEffect(volume, mpvVolume, isVolumeSliderShown) {
        if (isVolumeSliderShown) {
            delay(2000)
            viewModel.isVolumeSliderShown.value = false
        }
    }
    LaunchedEffect(brightness, isBrightnessSliderShown) {
        var ctx = context
        var activity: android.app.Activity? = null
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                activity = ctx
                break
            }
            ctx = ctx.baseContext
        }
        if (activity != null) {
            val params = activity.window.attributes
            if (brightness == -2f) {
                params.screenBrightness = -1f
            } else {
                params.screenBrightness = brightness.coerceIn(0.01f, 1f)
            }
            activity.window.attributes = params
        }
        if (isBrightnessSliderShown) {
            delay(2000)
            viewModel.isBrightnessSliderShown.value = false
        }
    }

    fun resetTimer() { resetTimerTrigger++ }

    val interactionSource = remember { MutableInteractionSource() }
    val seekAmount        by viewModel.doubleTapSeekAmount.collectAsState()
    val seekText          by viewModel.seekText.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {

        // ── Gesture layer (fullscreen, behind all controls) ───────────────────
        GestureHandler(
            viewModel         = viewModel,
            interactionSource = interactionSource,
            modifier          = Modifier.fillMaxSize()
        )

        // ── Double-tap seek ovals ─────────────────────────────────────────────
        DoubleTapToSeekOvals(
            amount            = seekAmount,
            text              = seekText,
            interactionSource = interactionSource,
            modifier          = Modifier.fillMaxSize()
        )

        // ── Gösterge yerleşimi (SABİT) ────────────────────────────────────────
        // Parlaklık göstergesi SAĞDA, ses göstergesi SOLDA gösterilir.
        // Bu yalnızca görsel göstergeleri etkiler; kaydırma (dokunma) bölgeleri
        // GestureHandler'daki swipeVolumeBrightnessSides ayarına göre değişmeye devam eder.
        // ── Brightness slider overlay (sağ kenar) ─────────────────────────────
        AnimatedVisibility(
            visible = isBrightnessSliderShown,
            enter = slideInHorizontally(androidx.compose.animation.core.tween(100)) { it } + fadeIn(),
            exit = slideOutHorizontally(androidx.compose.animation.core.tween(300)) { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(horizontal = 24.dp)
        ) {
            BrightnessSlider(
                brightness = if (brightness == -2f) viewModel.getSystemBrightness() else brightness,
                positiveRange = 0f..1f,
                negativeRange = 0f..0.75f,
            )
        }

        // ── Volume slider overlay (sol kenar) ─────────────────────────────────
        AnimatedVisibility(
            visible = isVolumeSliderShown,
            enter = slideInHorizontally(androidx.compose.animation.core.tween(100)) { -it } + fadeIn(),
            exit = slideOutHorizontally(androidx.compose.animation.core.tween(300)) { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 24.dp)
        ) {
            VolumeSlider(
                volume = volume,
                mpvVolume = mpvVolume,
                range = 0..viewModel.maxVolume,
                boostRange = if (settings.volumeBoostCap - 100 > 0) 0..(settings.volumeBoostCap - 100) else null,
                displayAsPercentage = settings.displayVolumeAsPercentage,
            )
        }

        // ── Gradient scrim ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = areControlsVisible && !areControlsLocked,
            enter   = fadeIn(),
            exit    = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )
        }

        if (areControlsLocked) {
            if (showLoadingCircle) {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp).align(Alignment.Center),
                    color = KitsugiColors.Accent,
                    strokeWidth = 4.dp
                )
            }
            // ── Lock icon (tap to unlock) ─────────────────────────────────────
            AnimatedVisibility(
                visible  = areControlsVisible,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            viewModel.unlockControls()
                            resetTimer()
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Lock,
                        contentDescription = KitsugiStrings.get(R.string.kilidi_ac),
                        tint               = Color.White,
                        modifier           = Modifier.size(36.dp)
                    )
                }
            }
        } else {
            // ── Top bar ───────────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = areControlsVisible,
                enter    = if (reduceMotion) fadeIn() else slideInVertically { -it } + fadeIn(),
                exit     = if (reduceMotion) fadeOut() else slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    TopLeftPlayerControls(
                        animeTitle   = animeTitle,
                        mediaTitle   = mediaTitle,
                        onTitleClick = {
                            viewModel.showDialog(KitsugiDialogs.EpisodeList)
                            resetTimer()
                        },
                        onBackClick  = onBackClick,
                        modifier     = Modifier.weight(1f)
                    )
                    TopRightPlayerControls(
                        autoPlayEnabled      = settings.isAutoplayEnabled,
                        onToggleAutoPlay     = { viewModel.setAutoPlay(it) },
                        onSubtitlesClick     = {
                            viewModel.showSheet(KitsugiSheets.SubtitleTracks)
                            resetTimer()
                        },
                        onSubtitlesLongClick = {
                            viewModel.showPanel(com.kitsugi.animelist.ui.screens.fullscreen.KitsugiPanels.SubtitleSettings)
                            resetTimer()
                        },
                        onAudioClick         = {
                            viewModel.showSheet(KitsugiSheets.AudioTracks)
                            resetTimer()
                        },
                        onAudioLongClick     = {
                            viewModel.showPanel(com.kitsugi.animelist.ui.screens.fullscreen.KitsugiPanels.AudioDelay)
                            resetTimer()
                        },
                        // Kısa dokunma → KALİTE, uzun dokunma → KAYNAK listesi.
                        onQualityClick       = {
                            viewModel.showSheet(KitsugiSheets.QualityTracks)
                            resetTimer()
                        },
                        onQualityLongClick   = {
                            viewModel.showSheet(KitsugiSheets.SourceTracks)
                            resetTimer()
                        },
                        onSourcesClick       = {
                            viewModel.showSheet(KitsugiSheets.SourceTracks)
                            resetTimer()
                        },
                        isEpisodeOnline      = currentTitle.isNotBlank(),
                        onMoreClick          = {
                            viewModel.showSheet(KitsugiSheets.More)
                            resetTimer()
                        },
                        onMoreLongClick      = {
                            viewModel.showPanel(com.kitsugi.animelist.ui.screens.fullscreen.KitsugiPanels.VideoFilters)
                            resetTimer()
                        },
                        onOpenExternalClick  = onOpenExternalPlayer,
                    )
                }
            }

            // ── Middle playback controls ──────────────────────────────────────
            MiddlePlayerControls(
                hasPrevious       = hasPrevious,
                onSkipPrevious    = {
                    viewModel.changeEpisode(previous = true)
                    resetTimer()
                },
                isLoading         = isLoading,
                isLoadingEpisode  = isLoadingEpisode,
                controlsShown     = areControlsVisible,
                areControlsLocked = areControlsLocked,
                showLoadingCircle = showLoadingCircle,
                paused            = paused,
                gestureSeekAmount = gestureSeekAmount,
                onPlayPauseClick  = {
                    viewModel.togglePlay()
                    resetTimer()
                },
                hasNext           = hasNext,
                onSkipNext        = {
                    viewModel.playNextEpisode(
                        activity              = null,
                        onAlternativeRequired = {},
                        onResolutionFailed    = {}
                    )
                    resetTimer()
                },
                enter             = fadeIn(),
                exit              = fadeOut(),
                modifier          = Modifier.align(Alignment.Center)
            )

            // ── Bottom bar ────────────────────────────────────────────────────
            val showGestureSeekBarOnly = !areControlsVisible && seekBarShown && gestureSeekAmount != null && settings.showSeekBarOnGesture
            AnimatedVisibility(
                visible  = areControlsVisible || showGestureSeekBarOnly,
                enter    = if (reduceMotion) fadeIn() else slideInVertically { it } + fadeIn(),
                exit     = if (reduceMotion) fadeOut() else slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SeekbarWithTimers(
                        position             = previewPosition ?: (posMs / 1000f),
                        duration             = (durationMs / 1000f).coerceAtLeast(0f),
                        readAheadValue       = viewModel.readAhead.collectAsState().value,
                        onValueChange        = {
                            isScrubbing = true
                            previewPosition = it
                            viewModel.onUserInteraction()
                            resetTimer()
                        },
                        onValueChangeFinished = {
                            previewPosition?.let {
                                viewModel.seekTo((it * 1000f).toLong())
                                viewModel.onUserInteraction()
                            }
                            previewPosition = null
                            previewImage = null
                            isScrubbing = false
                            resetTimer()
                        },
                        previewImage = previewImage,
                        previewPosition = previewPosition,
                        timersInverted       = Pair(false, settings.invertDurationTimer),
                        positionTimerOnClick  = {},
                        durationTimerOnCLick  = {
                            viewModel.toggleInvertDurationTimer()
                            resetTimer()
                        },
                        chapters             = seekSegments,
                        modifier             = Modifier.fillMaxWidth()
                    )

                    if (areControlsVisible) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        BottomLeftPlayerControls(
                            playbackSpeed       = playbackSpeed,
                            currentChapter      = currentChapter,
                            onLockControls      = {
                                viewModel.lockControls()
                                viewModel.showControls()
                            },
                            onCycleRotation     = {
                                onRotateClick()
                                resetTimer()
                            },
                            onPlaybackSpeedChange = { speed ->
                                viewModel.setPlaybackSpeed(speed)
                                resetTimer()
                            },
                            onOpenSheet         = { sheet ->
                                viewModel.showSheet(sheet)
                                resetTimer()
                            },
                            showChapters        = settings.showCurrentChapter && chapters.isNotEmpty()
                        )
                        BottomRightPlayerControls(
                            customButton         = customButton,
                            customButtonTitle    = customButtonTitle,
                            onPressCustomButton  = {
                                customButton?.let { viewModel.executeCustomButton(it) }
                                resetTimer()
                            },
                            onLongPressCustomButton = {
                                customButton?.let { viewModel.executeCustomButtonLongPress(it) }
                                resetTimer()
                            },
                            skipIntroButton      = skipIntroText,
                            onPressSkipIntroButton = {
                                skipIntervals.firstOrNull { interval ->
                                    currentPosSec >= interval.startTime && currentPosSec < interval.endTime
                                }?.let { interval ->
                                    viewModel.seekTo((interval.endTime * 1000f).toLong())
                                }
                                resetTimer()
                            },
                            fixedSkipSec         = introSkipLength,
                            onPressFixedSkip     = {
                                viewModel.seekTo(posMs + introSkipLength * 1000L)
                                resetTimer()
                            },
                            enableSkipIntro      = settings.enableSkipIntro,
                            isPipAvailable       = settings.pipEnabled,
                            onAspectClick        = {
                                onAspectClick()
                                resetTimer()
                            },
                            onPipClick           = {
                                val activity = (context as? android.app.Activity)
                                if (activity != null) {
                                    PlayerPipHelper.enterPipSafe(
                                        activity     = activity,
                                        playerEngine = null,
                                        isPlaying    = !paused,
                                        hasNext      = hasNext
                                    )
                                }
                                resetTimer()
                            }
                        )
                    }
                    }
                }
            }
        }

        // ── Floating Skip Intro button when OSD is hidden ─────────────────────
        AnimatedVisibility(
            visible  = !areControlsVisible && !areControlsLocked && settings.enableSkipIntro && skipIntroText != null && activeSkipInterval != dismissedSkipInterval,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 32.dp, end = 24.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.kitsugi.animelist.ui.screens.fullscreen.controls.components.FilledControlsButton(
                    text = skipIntroText ?: "Atla",
                    onClick = {
                        activeSkipInterval?.let { interval ->
                            viewModel.seekTo((interval.endTime * 1000f).toLong())
                        }
                    },
                    onLongClick = {
                        dismissedSkipInterval = activeSkipInterval
                    }
                )
            }
        }

        // ── Binge-card overlay ────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showBingeCard,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 96.dp, end = 24.dp)
        ) {
            Card(
                shape  = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = playerSurfaceColor(panelAlpha),
                    contentColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier  = Modifier.width(280.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = stringResource(R.string.sonraki_bolum),
                            fontWeight = FontWeight.Bold,
                            style      = MaterialTheme.typography.titleMedium
                        )
                        IconButton(
                            onClick  = { viewModel.cancelBingeCard() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Close,
                                contentDescription = stringResource(R.string.iptal),
                                modifier           = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text  = KitsugiStrings.get(R.string.inline_next_episode_starts_in_1_5ec8d9e, bingeCountdown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.cancelBingeCard() }) {
                            Text(stringResource(R.string.iptal))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        KitsugiButton(onClick = {
                            viewModel.playNextEpisode(
                                activity              = null,
                                onAlternativeRequired = {},
                                onResolutionFailed    = {}
                            )
                        }) {
                            Icon(
                                imageVector        = Icons.SkipNext,
                                contentDescription = null,
                                modifier           = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.simdi_oynat))
                        }
                    }
                }
            }
        }
    }
}

/** Convert [IndexedSegment] to seekbar [Segment]. */
private fun IndexedSegment.toSegment(): Segment = Segment(
    name  = name,
    start = start,
    color = color
)
