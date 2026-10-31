package com.kitsugi.animelist.ui.screens.detail
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.kitsugi.animelist.data.trailer.playbackCandidates
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
private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * YouTube bağlantısından video kimliğini çıkarır.
 *
 * ESKİ DAVRANIŞ (hata): yalnızca `watch?v=` (v İLK parametre olacak şekilde) ve
 * `/embed/` biçimleri tanınıyordu. `youtube.com/watch?app=desktop&v=...`,
 * `youtube.com/shorts/...`, `youtube.com/live/...`, `music.youtube.com/watch?...`
 * gibi tamamen geçerli adresler tanınmayınca video "doğrudan medya dosyası" sanılıp
 * Media3'e veriliyordu; Media3 bir HTML sayfası indirmeye çalışıp hata veriyordu.
 * Kullanıcı bunu "bazı videolar oynatılmıyor" olarak görüyordu (2026-10-22 raporu).
 */
private fun extractHost(url: String): String {
    val noScheme = url.substringAfter("://", url).substringAfter("//")
    val hostPart = noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    return hostPart.substringBefore(':').lowercase().removePrefix("www.").trimEnd('.')
}

private fun extractPath(url: String): String {
    val noScheme = url.substringAfter("://", url).substringAfter("//")
    val afterHost = noScheme.substringAfter('/', "")
    return afterHost.substringBefore('?').substringBefore('#')
}

private fun extractQueryParam(url: String, key: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    for (param in query.split('&')) {
        val parts = param.split('=', limit = 2)
        if (parts.size == 2 && parts[0] == key) {
            return parts[1]
        }
    }
    return null
}

internal fun extractYouTubeId(url: String): String? {
    if (url.isBlank()) return null
    val trimmed = url.trim()
    if (YOUTUBE_VIDEO_ID_REGEX.matches(trimmed)) return trimmed

    val normalized = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        // Protokol-göreli adres ("//www.youtube.com/embed/...") bazı sağlayıcılardan gelir.
        trimmed.startsWith("//") -> "https:$trimmed"
        else -> "https://$trimmed"
    }

    return runCatching {
        val host = extractHost(normalized)
        val isYouTubeHost = host == "youtu.be" ||
            host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com")
        if (!isYouTubeHost) return@runCatching null

        if (host == "youtu.be") {
            val segments = extractPath(normalized).split('/').filter { it.isNotEmpty() }
            return@runCatching segments.firstOrNull()
                ?.takeIf { YOUTUBE_VIDEO_ID_REGEX.matches(it) }
        }

        // `v` parametresi nerede olursa olsun (app=desktop, feature=share vb. önce gelebilir).
        extractQueryParam(normalized, "v")
            ?.takeIf { YOUTUBE_VIDEO_ID_REGEX.matches(it) }
            ?.let { return@runCatching it }

        val segments = extractPath(normalized).split('/').filter { it.isNotEmpty() }
        val pathKind = segments.firstOrNull()?.lowercase()
        if (segments.size >= 2 &&
            pathKind in setOf("embed", "shorts", "live", "v", "watch", "e")
        ) {
            return@runCatching segments[1].takeIf { YOUTUBE_VIDEO_ID_REGEX.matches(it) }
        }
        null
    }.getOrNull()
}

/** Adres bir YouTube *sayfası* mı? (kimlik çıkarılamasa bile dış uygulamada açılır) */
internal fun isYouTubePageUrl(url: String): Boolean = runCatching {
    if (url.isBlank()) return false
    val trimmed = url.trim()
    val normalized = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        else -> "https://$trimmed"
    }
    val host = extractHost(normalized)
    host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
}.getOrDefault(false)

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
    // Motor durumu, hata kartından da erişilebilsin diye üstte tutulur: kullanıcı
    // "Diğer motor" ile aynı videoyu öteki motorla yeniden başlatabilir.
    var activeEngineType by remember(videoId) {
        mutableStateOf(initialInlineEngineType(configuredPreference = null, isYouTubeSource = true))
    }
    var didTryAlternateEngine by remember(videoId) { mutableStateOf(false) }
    var engineAttempt by remember(videoId) { mutableIntStateOf(0) }

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
                    KitsugiButton(
                        onClick = {
                            InAppYouTubeExtractor.shared.invalidatePlaybackSource(videoId)
                            hasError = false
                            engineAttempt++
                            retryCount++
                        }
                    ) {
                        Text(stringResource(R.string.action_retry), color = KitsugiColors.Background)
                    }
                    KitsugiButton(
                        onClick = {
                            // İmzalı YouTube adresleri motordan motora farklı davranır:
                            // Media3 açamadığı akışı MPV (veya tersi) açabiliyor.
                            activeEngineType = activeEngineType.alternateInlineEngine()
                            didTryAlternateEngine = false
                            hasError = false
                            engineAttempt++
                            retryCount++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.16f)
                        )
                    ) {
                        Text(stringResource(R.string.inline_try_other_engine), color = Color.White)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitsugiButton(
                        onClick = {
                            runCatching {
                                uriHandler.openUri("https://www.youtube.com/watch?v=$videoId")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.16f)
                        )
                    ) {
                        Text(stringResource(R.string.inline_open_in_browser_eed20ab), color = Color.White)
                    }
                }
            }
        }
    } else {
        val source = playbackSource!!
        val dataStore = remember { com.kitsugi.animelist.data.settings.SettingsDataStore(context.applicationContext) }
        val appSettingsState = dataStore.settingsFlow.collectAsState(initial = null)
        val appSettings = appSettingsState.value
        val safeSettings = remember(appSettings) {
            (appSettings ?: com.kitsugi.animelist.data.settings.AppSettings()).forCompactInlinePlayback()
        }

        // YouTube'un imzalı Googlevideo/HLS adresleri Media3'ün özel istek politikasını
        // gerektirir (tam ekran oynatıcıyla aynı yol). MPV yalnızca bir kez yedek olarak
        // denenir. Her yeniden deneme TAZE bir motor örneği kurar: hata sonrası serbest
        // bırakılmış yüzey/çözücü kalıntıları yeni denemeyi de bozabiliyordu.
        val sourceCandidates = remember(source) { source.playbackCandidates() }
        var activeAttempt by remember(videoId, source) {
            mutableStateOf(
                InlinePlaybackAttempt(
                    sourceIndex = 0,
                    engine = initialInlineEngineType(
                        configuredPreference = appSettings?.playerPreference,
                        isYouTubeSource = true
                    )
                )
            )
        }
        var attemptedSources by remember(videoId, source) {
            mutableStateOf<Set<InlinePlaybackAttempt>>(emptySet())
        }
        val activeSource = sourceCandidates.getOrElse(activeAttempt.sourceIndex) { sourceCandidates[0] }
        val playerEngine = remember(context, activeAttempt.engine, videoId, activeAttempt.sourceIndex) {
            when (activeAttempt.engine) {
                PlayerEngineType.MPV -> MpvPlayerEngine(context, safeSettings)
                else -> Media3PlayerEngine(context, safeSettings)
            }
        }

        var isPlayingState by remember(videoId) { mutableStateOf(false) }
        var currentPosition by remember(videoId) { mutableStateOf(0L) }
        var duration by remember(videoId) { mutableStateOf(0L) }
        // İlk kare göründükten sonra ara bellek dalgalanmaları yükleme animasyonunu
        // yeniden göstermemeli; aksi hâlde akıcı oynayan video "donuyor" gibi görünüyor.
        var hasStartedPlayback by remember(playerEngine) { mutableStateOf(false) }
        var playerState by remember(playerEngine) { mutableStateOf(PlayerEngine.State.IDLE) }
        val resumedState = rememberInlinePlayerResumed()

        fun handlePlaybackFailure(
            failedAttempt: InlinePlaybackAttempt,
            failure: InlinePlaybackFailure,
            reason: String
        ) {
            if (failedAttempt != activeAttempt) return
            val tried = attemptedSources + failedAttempt
            attemptedSources = tried
            val next = nextInlinePlaybackAttempt(
                failed = failedAttempt,
                failure = failure,
                sourceCount = sourceCandidates.size,
                attempted = tried
            )
            if (next != null) {
                Log.w(
                    "KitsugiYouTubePlayer",
                    "${failedAttempt.engine}/source-${failedAttempt.sourceIndex} did not start " +
                        "($reason); trying ${next.engine}/source-${next.sourceIndex}"
                )
                hasStartedPlayback = false
                isPlayingState = false
                playerState = PlayerEngine.State.BUFFERING
                activeAttempt = next
            } else {
                Log.e("KitsugiYouTubePlayer", "All inline playback attempts failed for $videoId ($reason)")
                InAppYouTubeExtractor.shared.invalidatePlaybackSource(videoId)
                hasError = true
            }
        }

        DisposableEffect(playerEngine, activeAttempt) {
            var released = false
            val attemptForListener = activeAttempt
            val listener = object : PlayerEngine.Listener {
                override fun onStateChanged(state: PlayerEngine.State) {
                    if (!released) {
                        playerState = state
                        isPlayingState = playerEngine.isPlaying
                    }
                }

                /**
                 * MPV'de duraklat/oynat geçişi yalnızca bu geri çağrı ile gelir;
                 * uygulanmadığında mini oynatıcının oynat/duraklat düğmesi eski
                 * durumda donuyor ve ikinci dokunuş videoyu duraklatılmış bırakıyordu.
                 */
                override fun onPlayingChanged(isPlaying: Boolean) {
                    if (released) return
                    isPlayingState = isPlaying
                }

                override fun onPlaybackError(errorCode: Int, errorMsg: String, cause: Throwable?) {
                    if (released) return
                    Log.e("KitsugiYouTubePlayer", "Playback error: $errorMsg (code: $errorCode)")
                    handlePlaybackFailure(attemptForListener, InlinePlaybackFailure.SOURCE, errorMsg)
                }

                override fun onPositionChanged(positionMs: Long, durationMs: Long) {
                    if (released) return
                    currentPosition = positionMs
                    duration = durationMs
                    // Resume konumu ilk frame kanıtı değildir; gerçek video çıkışını bekle.
                    if (inlineVideoVisible(playerEngine) || positionMs > 0L) hasStartedPlayback = true
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
            }
        }

        DisposableEffect(playerEngine) {
            onDispose {
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

        LaunchedEffect(source, activeAttempt) {
            val attemptedNow = activeAttempt
            playerState = PlayerEngine.State.BUFFERING
            try {
                val reachedReady = withTimeoutOrNull(INLINE_PLAYER_START_TIMEOUT_MS) {
                    playerEngine.prepare(
                        videoUrl = activeSource.videoUrl,
                        audioUrl = activeSource.audioUrl,
                        headers = YouTubeRequestPolicy.headersForPlaybackSource(activeSource),
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
                    handlePlaybackFailure(
                        attemptedNow,
                        InlinePlaybackFailure.SOURCE,
                        "no playback progress within ${INLINE_PLAYER_START_TIMEOUT_MS}ms"
                    )
                    return@LaunchedEffect
                }
                if (!reachedReady) return@LaunchedEffect

                // Ses çalarken video parçası hiç seçilmemiş olabilir (cihazın çözemediği
                // kodek ya da 403 dönen imzalı YouTube video akışı). Media3 bu durumda
                // hata üretmediği için ekran siyah kalır; bekçi önce çözücüyü 1 ms'lik
                // bir seek ile dürter, hâlâ kare yoksa diğer motora geçer.
                val audioOnly = waitForInlineVideoTrack(
                    engine = playerEngine,
                    isResumed = { resumedState.value },
                    nudgeRenderer = {
                        playerEngine.seekTo(playerEngine.currentPosition + 1L)
                    }
                )
                if (audioOnly && attemptedNow == activeAttempt) {
                    handlePlaybackFailure(
                        attemptedNow,
                        InlinePlaybackFailure.AUDIO_ONLY,
                        "audio-only playback (no video track selected)"
                    )
                }
            } catch (e: CancellationException) {
                // The player can be disposed while prepare or network IO is in flight.
                throw e
            } catch (e: Exception) {
                if (attemptedNow != activeAttempt) return@LaunchedEffect
                Log.e("KitsugiYouTubePlayer", "Prepare error", e)
                handlePlaybackFailure(
                    attemptedNow,
                    InlinePlaybackFailure.SOURCE,
                    e.message ?: e.javaClass.simpleName
                )
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
            isBuffering = !hasStartedPlayback && !hasError,
            doubleTapSeekSeconds = safeSettings.doubleTapSeekSeconds,
            controlsAutoHideMs = com.kitsugi.animelist.core.player.PlayerUiPolicy
                .clampOsdHideDelayMs(safeSettings.playerTimeToDisappearMs),
            onFullscreen = {
                playerEngine.pause()
                KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                    context = context,
                    videoUrl = activeSource.videoUrl,
                    audioUrl = activeSource.audioUrl,
                    headers = YouTubeRequestPolicy.headersForPlaybackSource(activeSource)
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
                // Tampon göstergesi artık `KitsugiPlayerGestureWrapper`'ın merkez
                // durumunda çiziliyor; burada ikinci bir halka çizmek iki animasyonun
                // üst üste binmesine yol açıyordu.
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Satır içi oynatıcı kontrolleri (Gesture Wrapper)
//
//  Tasarım kuralı: TAM EKRAN OYNATICI (PlayerControls + MiddlePlayerControls)
//  referans alınır. Orada ortadaki alan tek bir "merkez durumu" gösterir
//  (Seek / Loading / PlayPause / Hidden) ve bu durumlar ASLA üst üste binmez.
//  Eski mini oynatıcı ise +/-10 sn geri bildirimini ve ortadaki oynat/duraklat
//  düğmesini aynı merkeze çiziyordu: "◀◀ ▶ 0s" gibi okunamaz bir yığın
//  oluşuyordu (2026-10-22 kullanıcı raporu). Artık:
//
//   • Ortada yalnızca TEK bilgi var: tampon çemberi veya oynat/duraklat.
//   • Kısayol (çift dokunma) geri bildirimi, tıklanan TARAFTA (sol/sağ) çıkar.
//   • Alt çubuk ana oynatıcıyla aynı düzende: ilerleme çubuğu + süre + oynat
//     + tam ekran; oynatırken ayarlardaki süre sonunda kendiliğinden gizlenir.
//   • Çift dokunma bölgeleri ana oynatıcıyla aynı: sol %40 geri, sağ %40 ileri,
//     orta bölge oynat/duraklat. Saniye değeri ayarlardan gelir.
// ---------------------------------------------------------------------------

/** Mini oynatıcının ortasında aynı anda gösterilebilecek tek durum. */
private enum class InlineCenterOverlay { Buffering, PlayPause, Hidden }

/** Çift dokunma kısayolunun yönü ve hedefi. */
private data class InlineSeekShortcut(
    val forward: Boolean,
    val seconds: Int,
    val targetMs: Long
)

@Composable
fun KitsugiPlayerGestureWrapper(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: (() -> Unit)? = null,
    /** İlk kare gelene kadar ortada dönen çember gösterilir. */
    isBuffering: Boolean = false,
    /** Ayarlar > Jestler > çift dokunma saniyesi (ana oynatıcıyla ortak). */
    doubleTapSeekSeconds: Int = 10,
    /** Ayarlar > Oynatıcı > kontrol gizlenme süresi (ana oynatıcıyla ortak). */
    controlsAutoHideMs: Long = 3_000L,
    content: @Composable () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val latestPosition by rememberUpdatedState(currentPosition)
    val latestDuration by rememberUpdatedState(duration)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnPlayPause by rememberUpdatedState(onPlayPause)

    var controlsVisible by remember { mutableStateOf(true) }
    var controlsToken by remember { mutableIntStateOf(0) }
    var seekShortcut by remember { mutableStateOf<InlineSeekShortcut?>(null) }
    var previewFraction by remember { mutableStateOf<Float?>(null) }

    val seekSeconds = doubleTapSeekSeconds.coerceIn(1, 120)
    // Duraklatılmışken (ve tampon sırasında) kontroller hep görünür kalır.
    val showControls = controlsVisible || !isPlaying || isBuffering

    // Ana oynatıcıdaki gibi: yalnızca oynatırken ve tampon dışındayken gizle.
    LaunchedEffect(controlsVisible, controlsToken, isPlaying, isBuffering, controlsAutoHideMs) {
        if (controlsVisible && isPlaying && !isBuffering) {
            delay(controlsAutoHideMs.coerceAtLeast(1_000L))
            controlsVisible = false
        }
    }

    // Kısayol geri bildirimi kısa süre sonra kaybolur (Ana oynatıcı: 800 ms).
    LaunchedEffect(seekShortcut) {
        if (seekShortcut != null) {
            delay(800L)
            seekShortcut = null
        }
    }

    val centerOverlay = when {
        isBuffering -> InlineCenterOverlay.Buffering
        showControls -> InlineCenterOverlay.PlayPause
        else -> InlineCenterOverlay.Hidden
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Video yüzeyi
        content()

        // Dokunma katmanı — ana oynatıcının bölge oranlarıyla aynı.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(seekSeconds) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) controlsToken++
                        },
                        onDoubleTap = { offset ->
                            controlsVisible = true
                            controlsToken++
                            val total = latestDuration
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            when {
                                offset.x < width * 2f / 5f -> {
                                    val target = (latestPosition - seekSeconds * 1_000L).coerceAtLeast(0L)
                                    latestOnSeek(target)
                                    seekShortcut = InlineSeekShortcut(
                                        forward = false,
                                        seconds = seekSeconds,
                                        targetMs = target
                                    )
                                }
                                offset.x > width * 3f / 5f -> {
                                    val forward = latestPosition + seekSeconds * 1_000L
                                    val target = if (total > 0L) forward.coerceAtMost(total) else forward
                                    latestOnSeek(target)
                                    seekShortcut = InlineSeekShortcut(
                                        forward = true,
                                        seconds = seekSeconds,
                                        targetMs = target
                                    )
                                }
                                else -> latestOnPlayPause()
                            }
                        }
                    )
                }
        )

        // Kısayol balonu: TIKLANAN TARAFTA çıkar, ortadaki oynat düğmesiyle çakışmaz.
        AnimatedVisibility(
            visible = seekShortcut != null,
            modifier = Modifier
                .align(
                    if (seekShortcut?.forward == true) Alignment.CenterEnd else Alignment.CenterStart
                )
                .padding(horizontal = 14.dp),
            enter = fadeIn(animationSpec = tween(140)) + scaleIn(initialScale = 0.88f),
            exit = fadeOut(animationSpec = tween(160))
        ) {
            val shortcut = seekShortcut
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.66f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (shortcut?.forward == true) Icons.FastForward
                        else Icons.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (shortcut != null) "${shortcut.seconds}s" else "",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = formatInlinePlayerTime(shortcut?.targetMs ?: 0L),
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Ortadaki alan — aynı anda TEK durum (Ana oynatıcı: MiddlePlayerControls).
        AnimatedContent(
            targetState = centerOverlay,
            modifier = Modifier.align(Alignment.Center),
            transitionSpec = {
                fadeIn(animationSpec = tween(160)) togetherWith fadeOut(animationSpec = tween(160))
            },
            label = "inlineCenterOverlay"
        ) { state ->
            when (state) {
                InlineCenterOverlay.Buffering -> {
                    KitsugiPlasmaLoader(size = 34.dp)
                }
                InlineCenterOverlay.PlayPause -> {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .tvClickable(shape = CircleShape) {
                                latestOnPlayPause()
                                controlsVisible = true
                                controlsToken++
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Pause else Icons.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.inline_player_pause else R.string.inline_player_play
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                InlineCenterOverlay.Hidden -> Spacer(modifier = Modifier.size(1.dp))
            }
        }

        // Alt çubuk — ana oynatıcının alt barının sade karşılığı.
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
            val shownPosition = previewFraction
                ?.let { fraction -> (duration * fraction).toLong() }
                ?: currentPosition
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 4.dp)
            ) {
                InlinePlayerSeekBar(
                    positionMs = currentPosition,
                    durationMs = duration,
                    accentColor = accentColor,
                    onPreviewFraction = { previewFraction = it },
                    onSeekFraction = { fraction ->
                        val total = latestDuration
                        if (total > 0L) latestOnSeek((total * fraction).toLong())
                        controlsVisible = true
                        controlsToken++
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
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
                            contentDescription = stringResource(
                                if (isPlaying) R.string.inline_player_pause else R.string.inline_player_play
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = buildString {
                            append(formatInlinePlayerTime(shownPosition))
                            append(" / ")
                            append(
                                if (duration > 0L) formatInlinePlayerTime(duration)
                                else formatInlinePlayerTime(0L, unknown = true)
                            )
                        },
                        color = Color.White.copy(alpha = 0.88f),
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
 * Satır içi oynatıcının ilerleme çubuğu (ana oynatıcının [SeekbarWithTimers]
 * düzeninin sade karşılığı).
 *
 * Dokunma ve sürükleme TEK bir jest döngüsünde işlenir. Eskiden iki ayrı
 * `pointerInput` bloğu vardı; dokunma dedektörü `down` olayını tükettiği için
 * sürükleme bazen hiç başlamıyordu. Ayrıca sürüklerken hedef konum üstteki
 * süre metninde gösterilir; kullanıcı nereye atlayacağını önceden görür.
 */
@Composable
private fun InlinePlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    accentColor: Color,
    onPreviewFraction: (Float?) -> Unit,
    onSeekFraction: (Float) -> Unit
) {
    val canSeek = durationMs > 0L
    val baseFraction = if (canSeek) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val isDragging = dragFraction >= 0f
    val shown = if (isDragging) dragFraction else baseFraction
    val latestOnSeek by rememberUpdatedState(onSeekFraction)
    val latestOnPreview by rememberUpdatedState(onPreviewFraction)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(canSeek) {
                if (!canSeek) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    var dragging = false
                    var fractionNow = (down.position.x / width).coerceIn(0f, 1f)
                    dragFraction = fractionNow
                    latestOnPreview(fractionNow)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val travelX = change.position.x - down.position.x
                        if (!dragging && kotlin.math.abs(travelX) > viewConfiguration.touchSlop) {
                            dragging = true
                        }
                        fractionNow = (change.position.x / width).coerceIn(0f, 1f)
                        if (dragging) {
                            dragFraction = fractionNow
                            latestOnPreview(fractionNow)
                            change.consume()
                        }
                        if (!change.pressed) {
                            // Bırakma olayını da tüket: aksi hâlde alttaki dokunma
                            // katmanı bunu "kontrolleri aç/kapat" olarak yorumluyor ve
                            // çubuğa dokunup atlarken alt çubuk göz kırpıyordu.
                            change.consume()
                            break
                        }
                    }
                    latestOnSeek(fractionNow)
                    dragFraction = -1f
                    latestOnPreview(null)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 3.dp.toPx()
            val centerY = size.height / 2f
            val topLeftY = centerY - trackHeight / 2f
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.32f),
                topLeft = Offset(0f, topLeftY),
                size = Size(size.width, trackHeight),
                cornerRadius = corner
            )
            val progressWidth = (size.width * shown).coerceIn(0f, size.width)
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(0f, topLeftY),
                size = Size(progressWidth, trackHeight),
                cornerRadius = corner
            )
            val thumbRadius = (if (isDragging) 7.dp else 5.5.dp).toPx()
            // Çok dar genişliklerde `coerceIn(min, max)` min > max ile çöker; üst sınırı
            // alt sınıra sabitleyerek yuvarlak topu her zaman geçerli tutuyoruz.
            val maxThumbX = (size.width - thumbRadius).coerceAtLeast(thumbRadius)
            drawCircle(
                color = accentColor,
                radius = thumbRadius,
                center = Offset(
                    x = progressWidth.coerceIn(thumbRadius, maxThumbX),
                    y = centerY
                )
            )
        }
    }
}

private fun formatInlinePlayerTime(ms: Long, unknown: Boolean = false): String {
    if (unknown) return "--:--"
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
