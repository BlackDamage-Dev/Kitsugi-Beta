package com.kitsugi.animelist.ui.screens.detail
import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.util.Log
import com.kitsugi.animelist.ui.components.KitsugiDetailReveal
import com.kitsugi.animelist.ui.components.animateDetailContentSize
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.kitsugi.animelist.ui.screens.detail.components.SharedTrailerOverlay
import com.kitsugi.animelist.data.trailer.YouTubeRequestPolicy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import android.widget.Toast
import com.kitsugi.animelist.data.local.AnimeDownloadManager
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.utils.KitsugiStrings
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.engine.PlayerEngineType
import com.kitsugi.animelist.core.player.engine.Media3PlayerEngine
import com.kitsugi.animelist.core.player.engine.MpvPlayerEngine
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.data.remote.KitsugiMediaVideoAggregator
import com.kitsugi.animelist.data.remote.MediaVideoCategory
import com.kitsugi.animelist.data.remote.MediaVideoItem
import com.kitsugi.animelist.data.remote.canonicalVideoSource
import com.kitsugi.animelist.data.remote.extractCanonicalYouTubeId
import com.kitsugi.animelist.data.remote.mergeAndOrderMediaVideos
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogos
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R


// ---------------------------------------------------------------------------
//  Fragman Kartı (tıklanınca inline player açılır; tam ekran butonu ile overlay)
// ---------------------------------------------------------------------------
@Composable
fun KitsugiTrailerCard(trailerUrl: String, mediaTitle: String = "") {
    val accentColor = LocalKitsugiAccent.current
    val uriHandler = LocalUriHandler.current
    val videoId = remember(trailerUrl) { extractYouTubeId(trailerUrl) }
    var showPlayer by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(false) }

    // Full-screen overlay
    if (showOverlay) {
        SharedTrailerOverlay(
            title = if (mediaTitle.isNotBlank()) "$mediaTitle — Fragman" else "Fragman",
            trailerUrl = trailerUrl,
            onDismiss = { showOverlay = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .animateDetailContentSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.inline_preview_c9d9dd2),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        val isPlayerOpen = showPlayer && videoId != null

        // Fragman satırı — oynatıcı açıkken de üstte görünür kalır
        // (Açılış/Kapanış müziklerindeki aktif satır kartıyla aynı davranış ve görünüm)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isPlayerOpen) accentColor.copy(alpha = 0.15f)
                    else accentColor.copy(alpha = 0.12f)
                )
                .tvClickable(shape = RoundedCornerShape(16.dp)) {
                    if (videoId != null) {
                        showPlayer = !showPlayer
                    } else {
                        runCatching { uriHandler.openUri(trailerUrl) }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlayerOpen) Icons.KeyboardArrowUp else Icons.PlayArrow,
                    contentDescription = if (isPlayerOpen) stringResource(R.string.settings_close) else "Oynat",
                    tint = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.inline_watch_trailer_8b06108),
                    color = if (isPlayerOpen) accentColor else KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (videoId != null) stringResource(R.string.ui_play_in_app) else stringResource(R.string.youtube_da_ac),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        KitsugiDetailReveal(visible = isPlayerOpen) {
            Column(modifier = Modifier.fillMaxWidth()) {
                videoId?.let { selectedVideoId ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        KitsugiYouTubePlayer(
                            videoId = selectedVideoId,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Kapat butonu da aynı geçişin parçası olsun; satır ayrı ayrı pat diye belirmez.
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .tvClickable(shape = RoundedCornerShape(12.dp)) { showPlayer = false }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.KeyboardArrowUp,
                        contentDescription = null,
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.kapat),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Anime Temaları & TMDB Müzik Videoları / Fragmanlar Listesi
// ---------------------------------------------------------------------------
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ThemePlayerContainer(
    url: String,
    modifier: Modifier = Modifier
) {
    ThemePlayerContainer(urls = listOf(url), modifier = modifier)
}

@Composable
fun ThemePlayerContainer(
    urls: List<String>,
    modifier: Modifier = Modifier
) {
    val candidates = remember(urls) { urls.map { it.trim() }.filter { it.isNotBlank() }.distinct() }
    if (candidates.isEmpty()) return

    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val uriHandler = LocalUriHandler.current

    var candidateIndex by remember(candidates) { mutableStateOf(0) }
    val safeIndex = candidateIndex.coerceIn(0, candidates.lastIndex)
    val url = candidates[safeIndex]

    var isPlayerError by remember(url) { mutableStateOf(false) }
    var isPlayerLoading by remember(url) { mutableStateOf(true) }
    var retryTrigger by remember(url) { mutableStateOf(0) }
    // İlk kare göründükten sonra ara bellek dalgalanmaları yükleme animasyonunu
    // tekrar tekrar açıp kapatmamalı: video oynarken üstünde beliren halka,
    // akıcı bir akış bile "donuyor" gibi gösteriyordu.
    var hasStartedPlayback by remember(url) { mutableStateOf(false) }

    val videoId = remember(url) { extractYouTubeId(url) }

    if (videoId != null) {
        KitsugiYouTubePlayer(videoId = videoId, modifier = modifier)
    } else if (isYouTubePageUrl(url)) {
        Box(
            modifier = modifier.background(Color.Black).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.inline_video_unsupported_title),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                KitsugiButton(
                    onClick = { runCatching { uriHandler.openUri(url) } },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text(
                        text = stringResource(R.string.inline_open_in_browser_eed20ab),
                        color = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor)
                    )
                }
            }
        }
    } else {
        val dataStore = remember { com.kitsugi.animelist.data.settings.SettingsDataStore(context.applicationContext) }
        val appSettingsState = dataStore.settingsFlow.collectAsState(initial = null)
        val appSettings = appSettingsState.value
        val safeSettings = remember(appSettings) { appSettings ?: com.kitsugi.animelist.data.settings.AppSettings() }

        val preferredEngineType = remember(url) { initialCompactThemeEngineType(url) }
        var activeEngineType by remember(url) {
            mutableStateOf(preferredEngineType)
        }
        var didTryAlternateEngine by remember(url) { mutableStateOf(false) }

        // Oynatıcı motoru artık `remember { }` içinde — yani COMPOSITION sırasında —
        // kurulmuyor (2026-11-05, bkz. [InlinePlaybackHandoff]).
        //
        // NEDEN: `remember` yeni düğüm kurulurken, eski düğümün `onDispose` bloğu ise aynı
        // karenin apply fazında çalışır. Yani satır değiştiğinde yeni `ExoPlayer` (MediaCodec
        // çözücüsü + AudioTrack + TextureView + OkHttp istemcisi) eski motor hâlâ ayaktayken
        // kuruluyordu. Hızlı dokunmalarda bu yığınlar üst üste binince cihazın sınırlı
        // MediaCodec havuzu tükeniyor, sonraki videolar "decoder init failed" ile açılmıyor
        // ve ses odağı çakışması yeni oynatıcıyı duraklatılmış bırakıyordu.
        // Motor artık el değiştirme kapısı beklenerek kurulur ve tek bir yerde bırakılır.
        val session = remember(url) { InlineThemeEngineSession() }
        var playerEngine by remember(url) { mutableStateOf<PlayerEngine?>(null) }

        var isPlayingState by remember(url) { mutableStateOf(false) }
        var currentPosition by remember(url) { mutableStateOf(0L) }
        var duration by remember(url) { mutableStateOf(0L) }
        val resumedState = rememberInlinePlayerResumed()

        fun failCurrentAttempt(failedEngine: PlayerEngineType, reason: String) {
            Log.w(
                "ThemePlayerContainer",
                "$failedEngine başarısız: $reason (aday ${safeIndex + 1}/${candidates.size})"
            )
            if (!didTryAlternateEngine) {
                didTryAlternateEngine = true
                val alternate = failedEngine.alternateInlineEngine()
                Log.w("ThemePlayerContainer", "Yedek motor deneniyor: $alternate")
                activeEngineType = alternate
                return
            }
            if (safeIndex + 1 < candidates.size) {
                val nextIndex = safeIndex + 1
                Log.w(
                    "ThemePlayerContainer",
                    "Sıradaki video adayı deneniyor: ${nextIndex + 1}/${candidates.size}"
                )
                candidateIndex = nextIndex
                didTryAlternateEngine = false
                activeEngineType = initialCompactThemeEngineType(candidates[nextIndex])
                return
            }
            Log.e("ThemePlayerContainer", "Tüm kompakt oynatma denemeleri tükendi ($url): $reason")
            isPlayerLoading = false
            isPlayerError = true
        }

        // Bu satır kapatıldığında (veya başka satıra geçildiğinde) motoru bırak.
        DisposableEffect(session) {
            onDispose {
                session.release()
            }
        }

        LaunchedEffect(url, retryTrigger, activeEngineType) {
            val attemptedEngineType = activeEngineType
            isPlayerError = false
            isPlayerLoading = true

            // Aynı oynatıcı konumunda ikinci bir motor asla yaşamasın: önce mevcut motoru
            // bırak, sonra el değiştirme aralığını bekle, en son yenisini kur.
            session.release()
            playerEngine = null
            InlinePlaybackHandoff.awaitEngineSlot()
            // Bekleme sırasında satır kapanmış/başka satıra geçilmiş olabilir. İptal edilmiş
            // bir efektin motor kurup sızdırmasını engelle.
            currentCoroutineContext().ensureActive()

            var currentEngine: PlayerEngine? = null
            val listener = object : PlayerEngine.Listener {
                override fun onStateChanged(state: PlayerEngine.State) {
                    val active = session.engine ?: return
                    if (active !== currentEngine) return
                    isPlayingState = active.isPlaying
                    isPlayerLoading = state == PlayerEngine.State.BUFFERING
                }

                override fun onPlaybackError(errorCode: Int, errorMsg: String, cause: Throwable?) {
                    val active = session.engine ?: return
                    if (active !== currentEngine) return
                    isPlayerLoading = false
                    Log.e("ThemePlayerContainer", "Oynatma hatası: $errorMsg (kod: $errorCode)")
                    failCurrentAttempt(attemptedEngineType, errorMsg)
                }

                override fun onPositionChanged(positionMs: Long, durationMs: Long) {
                    if (session.engine == null) return
                    currentPosition = positionMs
                    duration = durationMs
                    if (positionMs > 0L) hasStartedPlayback = true
                }

                override fun onTracksChanged(
                    audioTracks: List<com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption>,
                    subtitleTracks: List<com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption>
                ) {}
            }

            try {
                val created: PlayerEngine = when (attemptedEngineType) {
                    PlayerEngineType.MPV -> MpvPlayerEngine(context, safeSettings)
                    else -> Media3PlayerEngine(context, safeSettings)
                }
                currentEngine = created
                session.start(created, listener)
                playerEngine = created

                val reachedReady = withTimeoutOrNull(INLINE_PLAYER_START_TIMEOUT_MS) {
                    created.prepare(
                        videoUrl = url,
                        headers = YouTubeRequestPolicy.headersForInlineUrl(url),
                        startPositionMs = currentPosition
                    )
                    if (!resumedState.value) {
                        runCatching { created.pause() }
                        isPlayingState = false
                        false
                    } else {
                        created.play()
                        // READY is only a preparation signal. Fall back if playback never
                        // advances instead of leaving the compact surface black at 00:00.
                        while (!hasInlinePlaybackProgress(created.currentState, created.currentPosition)) {
                            if (!resumedState.value) return@withTimeoutOrNull false
                            delay(250L)
                        }
                        true
                    }
                }
                if (reachedReady == null) {
                    failCurrentAttempt(attemptedEngineType, "no playback progress within ${INLINE_PLAYER_START_TIMEOUT_MS}ms")
                    return@LaunchedEffect
                }
                if (!reachedReady) return@LaunchedEffect

                // Media3 VP8/VP9 gibi bazı codec'leri oynatamadığında veya video izi
                // yoksa Media3 hata vermez; ses çalar, görüntü hiç gelmez. MPV yazılım
                // çözücüyle aynı dosyayı oynatabildiği için bekçi motoru değiştirir.
                val audioOnly = waitForInlineVideoTrack(
                    engine = created,
                    isResumed = { resumedState.value }
                )
                Log.i(
                    "ThemePlayerContainer",
                    "engine=$attemptedEngineType visibleVideo=${inlineVideoVisible(created)} " +
                        "hasTrack=${created.hasVideoTrack} " +
                        "renderedFrame=${created.hasRenderedVideoFrame} url=${url.take(140)}"
                )
                if (audioOnly && attemptedEngineType == activeEngineType) {
                    failCurrentAttempt(
                        attemptedEngineType,
                        "audio-only playback (track=${created.hasVideoTrack}, " +
                            "rendered=${created.hasRenderedVideoFrame})"
                    )
                }
            } catch (e: CancellationException) {
                // Closing or replacing this row cancels prepare; do not trigger a false fallback.
                throw e
            } catch (e: Exception) {
                if (attemptedEngineType != activeEngineType) return@LaunchedEffect
                Log.e("ThemePlayerContainer", "Prepare error", e)
                failCurrentAttempt(attemptedEngineType, e.message ?: e.javaClass.simpleName)
            }
        }

        LaunchedEffect(resumedState.value, playerEngine) {
            if (!resumedState.value) {
                playerEngine?.let { runCatching { it.pause() } }
                isPlayingState = false
            }
        }

        LaunchedEffect(playerEngine, isPlayingState) {
            if (isPlayingState) {
                while (true) {
                    playerEngine?.let { engine ->
                        currentPosition = engine.currentPosition
                        duration = engine.duration.coerceAtLeast(0L)
                    }
                    delay(1000)
                }
            }
        }

        val liveEngine = playerEngine
        Box(
            modifier = if (activeEngineType == PlayerEngineType.MPV) {
                modifier.background(Color.Black)
            } else {
                modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)
            },
            contentAlignment = Alignment.Center
        ) {
            if (isPlayerError) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.inline_video_unsupported_title),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.inline_video_unsupported_body),
                        color = Color.White.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KitsugiButton(
                            onClick = {
                                runCatching {
                                    KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                                        context = context,
                                        videoUrl = url,
                                        headers = YouTubeRequestPolicy.headersForInlineUrl(url),
                                        treatAsTrailer = true
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text(
                                stringResource(R.string.inline_open_fullscreen),
                                color = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor)
                            )
                        }
                        KitsugiButton(
                            onClick = {
                                candidateIndex = 0
                                didTryAlternateEngine = false
                                activeEngineType = preferredEngineType
                                retryTrigger++
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Text(stringResource(R.string.action_retry), color = Color.White)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KitsugiButton(
                            onClick = {
                                activeEngineType = activeEngineType.alternateInlineEngine()
                                didTryAlternateEngine = false
                                retryTrigger++
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Text(stringResource(R.string.inline_try_other_engine), color = Color.White)
                        }
                    }
                }
            } else {
                KitsugiPlayerGestureWrapper(
                    isPlaying = isPlayingState,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPause = {
                        liveEngine?.let { if (isPlayingState) it.pause() else it.play() }
                    },
                    onSeek = { target ->
                        liveEngine?.seekTo(target)
                    },
                    modifier = Modifier.fillMaxSize(),
                    isBuffering = !hasStartedPlayback && !isPlayerError,
                    doubleTapSeekSeconds = safeSettings.doubleTapSeekSeconds,
                    controlsAutoHideMs = com.kitsugi.animelist.core.player.PlayerUiPolicy
                        .clampOsdHideDelayMs(safeSettings.playerTimeToDisappearMs),
                    onFullscreen = {
                        runCatching { liveEngine?.pause() }
                        KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                            context = context,
                            videoUrl = url,
                            headers = YouTubeRequestPolicy.headersForInlineUrl(url),
                            treatAsTrailer = true
                        )
                    }
                ) {
                    if (liveEngine != null) {
                        key(liveEngine) {
                            AndroidView(
                                factory = { ctx ->
                                    liveEngine.createVideoView(ctx)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (isPlayerLoading && !hasStartedPlayback) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            }
        }
    }
}

/**
 * Satır içi tema oynatıcısının motor yaşam döngüsü.
 *
 * Motor, composition sırasında değil [LaunchedEffect] içinde kurulur; bu yüzden
 * "şu an hangi motor ve hangi dinleyici bağlı" bilgisini hatırlayan küçük bir taşıyıcı
 * gerekir. [release] tekrar tekrar çağrılabilir (satır kapanışı + yeniden deneme).
 */
private class InlineThemeEngineSession {
    var engine: PlayerEngine? = null
        private set

    private var listener: PlayerEngine.Listener? = null

    fun start(engine: PlayerEngine, listener: PlayerEngine.Listener) {
        this.engine = engine
        this.listener = listener
        engine.addListener(listener)
    }

    /**
     * Motoru bırakır ve el değiştirme kapısına "boş" damgası bırakır.
     *
     * Sıra önemli: dinleyici önce ayrılır (serbest bırakılan oynatıcıdan gecikmiş
     * olay gelirse Compose durumu bozulmasın), sonra duraklatılır, en son serbest bırakılır.
     */
    fun release() {
        val current = engine
        val currentListener = listener
        engine = null
        listener = null
        if (current == null) return
        currentListener?.let { runCatching { current.removeListener(it) } }
        runCatching { current.pause() }
        runCatching { current.release() }
        InlinePlaybackHandoff.onInlineEngineReleased()
    }
}

private data class DetailVideoItem(
    val key: String,
    val title: String,
    val videoUrl: String?,
    val sources: List<String> = emptyList(),
    /**
     * İndirilmiş kopyanın yerel adresi (`file://…`). Doluysa satır çevrimdışı oynatılır;
     * indirme anahtarı her zaman [videoUrl] (kaynak bağlantı) üzerinden hesaplanır.
     */
    val localPlaybackUrl: String? = null,
    val alternateUrls: List<String> = emptyList()
) {
    /** Oynatıcıya verilecek adres: varsa yerel kopya, yoksa kaynak bağlantı. */
    val playbackUrl: String? get() = localPlaybackUrl ?: videoUrl
    val playbackUrls: List<String> get() = buildList {
        localPlaybackUrl?.let { add(it) }
        videoUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
        addAll(alternateUrls)
    }.distinct()
}

/**
 * Detay sayfasındaki videonun indirme durumu (UI için sadeleştirilmiş görünüm).
 * Kayıt yoksa haritada bulunmaz → "indirilebilir".
 */
private data class ClipDownloadUiState(
    val status: AnimeDownload.Status,
    val progress: Int,
    val errorMessage: String?
)

/**
 * Detay sayfasındaki bir video için **var olan** indirme kaydını bulur (yoksa `null`).
 * Anahtar [AnimeDownloadManager.clipKeyFor] ile üretilir; böylece aynı video farklı
 * sekmelerde/oturumlarda aynı kayda düşer.
 */
private fun findClipRecord(
    downloads: List<AnimeDownload>,
    videoUrl: String?
): AnimeDownload? {
    val key = videoUrl?.takeIf { it.isNotBlank() }
        ?.let { AnimeDownloadManager.clipKeyFor(it) }
        ?: return null
    return downloads.firstOrNull { it.isClip && it.clipKey == key }
}

/**
 * İndirilmiş klibin yerel oynatma adresi. Dosya gerçekten duruyorsa `file://…` döner;
 * kayıt "tamamlandı" olsa bile dosya silinmişse `null` döner (uzak kaynak kullanılır).
 */
private fun clipLocalPlaybackUrl(
    record: AnimeDownload?,
    videoDownloadState: com.kitsugi.animelist.core.download.video.VideoDownloadState? = null
): String? {
    if (videoDownloadState != null && videoDownloadState.phase == com.kitsugi.animelist.core.download.video.VideoDownloadPhase.COMPLETED) {
        val outPath = videoDownloadState.outputPath
        if (!outPath.isNullOrBlank()) {
            val f = java.io.File(outPath)
            if (f.isFile && f.length() > 0L) return "file://${f.absolutePath}"
        }
    }
    if (record == null || record.status != AnimeDownload.Status.COMPLETED) return null
    val path = record.localPath ?: return null
    val file = java.io.File(path)
    return if (file.isFile && file.length() > 0L) "file://${file.absolutePath}" else null
}

/**
 * Video kartı başlığının sağ üst köşesinde ve çok kaynaklı satırlarda gösterilen
 * [Logo + Kaynak Adı] rozeti (hero bölümündeki [DetailSourcePill] ile uyumlu görsel dil).
 */
@Composable
private fun DetailVideoSourceBadge(
    source: String,
    accentColor: Color,
    isSelected: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val cleanSource = canonicalVideoSource(source)
    if (cleanSource.isEmpty()) return
    val label = cleanSource.toFriendlySourceLabel()
    val pillShape = RoundedCornerShape(50)
    val bgColor = when {
        isSelected -> accentColor.copy(alpha = 0.18f)
        compact -> KitsugiColors.Surface.copy(alpha = 0.85f)
        else -> KitsugiColors.SurfaceSoft
    }
    val borderColor = when {
        isSelected -> accentColor.copy(alpha = 0.75f)
        else -> KitsugiColors.Border.copy(alpha = 0.5f)
    }
    val logoSize = if (compact) 12.dp else 14.dp
    val baseModifier = Modifier
        .clip(pillShape)
        .background(bgColor)
        .border(1.dp, borderColor, pillShape)
        .let { mod ->
            if (onClick != null) mod.tvClickable(shape = pillShape, onClick = onClick) else mod
        }
        .padding(
            horizontal = if (compact) 6.dp else 8.dp,
            vertical = if (compact) 2.dp else 4.dp
        )

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)
    ) {
        if (KitsugiPlatformLogos.resFor(cleanSource) != null) {
            KitsugiPlatformLogo(
                platformId = cleanSource,
                size = logoSize,
                cornerRadius = if (compact) 3.dp else 4.dp
            )
        } else {
            DetailPlatformBadge(
                source = cleanSource,
                modifier = Modifier.size(logoSize)
            )
        }
        Text(
            text = label,
            color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Video bağlantısını paylaşır. Oynatılabilir bağlantı yoksa (YouTube araması gerekiyorsa)
 * arama bağlantısı paylaşılır; alıcı her zaman açılabilir bir adres görür.
 */
private fun shareDetailVideo(context: android.content.Context, mediaTitle: String, item: DetailVideoItem) {
    val url = item.videoUrl?.takeIf { it.isNotBlank() }
        ?: "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(item.title, "UTF-8")}"
    val title = if (mediaTitle.isBlank()) item.title else "$mediaTitle · ${item.title}"
    com.kitsugi.animelist.utils.ShareUtils.shareText(context, title, url)
}

/**
 * Dedicated Videos tab shared by API-backed and list-backed media detail pages.
 * Aggregates trailers, openings, endings, and extra videos across all video-providing
 * sources and displays source logo + name badges on each group header and video row.
 */
@Composable
fun KitsugiMediaVideosTab(
    detail: com.kitsugi.animelist.data.remote.KitsugiMediaDetail?,
    /** Paylaşımda video başlığının önüne eklenen yapım adı (boşsa yalnızca video adı). */
    mediaTitle: String = "",
    /** Açılan detay sayfasının birincil kaynağı (ör. "anilist", "mal", "tmdb"). */
    primarySource: String = "",
    /** Çok kaynaklı video toplayıcıdan gelen birleştirilmiş video listesi. */
    aggregatedVideos: List<MediaVideoItem> = emptyList(),
    /** Arka planda diğer kaynaklardan video sorgusu devam ediyor mu? */
    isLoading: Boolean = false
) {
    val shareContext = LocalContext.current
    val onShareVideo: (DetailVideoItem) -> Unit = { item ->
        shareDetailVideo(shareContext, mediaTitle, item)
    }

    val effectiveVideos = remember(aggregatedVideos, detail, primarySource) {
        val seeded = KitsugiMediaVideoAggregator.seedFromDetail(detail, primarySource)
        when {
            aggregatedVideos.isEmpty() -> seeded
            seeded.isEmpty() -> aggregatedVideos
            else -> {
                val existingYtIds = aggregatedVideos.mapNotNull { extractCanonicalYouTubeId(it.videoUrl) }.toSet()
                val existingUrls = aggregatedVideos.mapNotNull { it.videoUrl?.trim()?.lowercase() }.toSet()
                val extraSeeded = seeded.filter { s ->
                    val yt = extractCanonicalYouTubeId(s.videoUrl)
                    val u = s.videoUrl?.trim()?.lowercase()
                    when {
                        yt != null -> yt !in existingYtIds
                        !u.isNullOrBlank() -> u !in existingUrls
                        else -> false
                    }
                }
                if (extraSeeded.isEmpty()) aggregatedVideos
                else mergeAndOrderMediaVideos(aggregatedVideos + extraSeeded, preferredSource = primarySource)
            }
        }
    }

    // ── Küçük video indirmeleri ───────────────────────────────────────────────
    // Tekilleştirme anahtarı yalnızca videonun kimliğidir; aynı video farklı sekmelerde
    // (TMDB/Simkl/arama) açılsa da tek kayıt ve tek dosya olur.
    val downloads by AnimeDownloadManager.downloads.collectAsState()
    val videoDownloads by com.kitsugi.animelist.core.download.video.VideoDownloadManager.states.collectAsState()
    val effectiveMediaTitle = mediaTitle.ifBlank { detail?.title.orEmpty() }.trim()

    val trailerItems = remember(effectiveVideos, downloads, videoDownloads) {
        effectiveVideos
            .filter { it.category == MediaVideoCategory.TRAILER }
            .map {
                DetailVideoItem(
                    key = it.key,
                    title = it.title,
                    videoUrl = it.videoUrl,
                    sources = it.sources,
                    localPlaybackUrl = clipLocalPlaybackUrl(findClipRecord(downloads, it.videoUrl), videoDownloads[it.key]),
                    alternateUrls = it.safeAlternateUrls
                )
            }
    }
    val openingItems = remember(effectiveVideos, downloads, videoDownloads) {
        effectiveVideos
            .filter { it.category == MediaVideoCategory.OPENING }
            .map {
                DetailVideoItem(
                    key = it.key,
                    title = it.title,
                    videoUrl = it.videoUrl,
                    sources = it.sources,
                    localPlaybackUrl = clipLocalPlaybackUrl(findClipRecord(downloads, it.videoUrl), videoDownloads[it.key]),
                    alternateUrls = it.safeAlternateUrls
                )
            }
    }
    val endingItems = remember(effectiveVideos, downloads, videoDownloads) {
        effectiveVideos
            .filter { it.category == MediaVideoCategory.ENDING }
            .map {
                DetailVideoItem(
                    key = it.key,
                    title = it.title,
                    videoUrl = it.videoUrl,
                    sources = it.sources,
                    localPlaybackUrl = clipLocalPlaybackUrl(findClipRecord(downloads, it.videoUrl), videoDownloads[it.key]),
                    alternateUrls = it.safeAlternateUrls
                )
            }
    }
    val extraItems = remember(effectiveVideos, downloads, videoDownloads) {
        effectiveVideos
            .filter { it.category == MediaVideoCategory.EXTRA }
            .map {
                DetailVideoItem(
                    key = it.key,
                    title = it.title,
                    videoUrl = it.videoUrl,
                    sources = it.sources,
                    localPlaybackUrl = clipLocalPlaybackUrl(findClipRecord(downloads, it.videoUrl), videoDownloads[it.key]),
                    alternateUrls = it.safeAlternateUrls
                )
            }
    }

    val downloadStates = remember(trailerItems, openingItems, endingItems, extraItems, downloads, videoDownloads) {
        val map = HashMap<String, ClipDownloadUiState>()
        fun putState(item: DetailVideoItem) {
            val key = item.key
            val vState = videoDownloads[key]
            if (vState != null && vState.phase != com.kitsugi.animelist.core.download.video.VideoDownloadPhase.IDLE) {
                val status = when (vState.phase) {
                    com.kitsugi.animelist.core.download.video.VideoDownloadPhase.RESOLVING,
                    com.kitsugi.animelist.core.download.video.VideoDownloadPhase.DOWNLOADING,
                    com.kitsugi.animelist.core.download.video.VideoDownloadPhase.MERGING -> AnimeDownload.Status.DOWNLOADING
                    com.kitsugi.animelist.core.download.video.VideoDownloadPhase.COMPLETED -> AnimeDownload.Status.COMPLETED
                    com.kitsugi.animelist.core.download.video.VideoDownloadPhase.FAILED -> AnimeDownload.Status.ERROR
                    com.kitsugi.animelist.core.download.video.VideoDownloadPhase.IDLE -> null
                }
                if (status != null) {
                    map[key] = ClipDownloadUiState(
                        status = status,
                        progress = vState.percent,
                        errorMessage = vState.message
                    )
                    return
                }
            }
            val record = findClipRecord(downloads, item.videoUrl) ?: return
            map[key] = ClipDownloadUiState(record.status, record.progress, record.errorMessage)
        }
        trailerItems.forEach { putState(it) }
        openingItems.forEach { putState(it) }
        endingItems.forEach { putState(it) }
        extraItems.forEach { putState(it) }
        map
    }

    val uriHandler = LocalUriHandler.current
    val accentColor = LocalKitsugiAccent.current
    var activeVideoKey by remember { mutableStateOf<String?>(null) }

    // Satır vurgusu anında değişir; OYNATICI ise ayrı bir anahtarla taşınır.
    //
    // NEDEN (2026-11-05 saha raporu): kullanıcı OP/ED satırlarına hızlı hızlı dokunduğunda
    // her dokunuş bir oynatıcı yığınını (ExoPlayer + MediaCodec çözücü + AudioTrack +
    // TextureView + OkHttp istemcisi) kurup yıkıyordu. Üst üste binen yığınlar cihazın
    // sınırlı çözücü havuzunu tüketip sonraki videoları "açılmıyor/hata" durumuna
    // düşürüyordu. Artık dokunmalar durulana kadar (bkz. [INLINE_PLAYER_TAP_COALESCE_MS])
    // yalnızca son seçim için TEK oynatıcı kurulur: kullanıcı 4 satıra hızlıca bassa da
    // tek motor açılır.
    var playerVideoKey by remember(trailerItems, openingItems, endingItems, extraItems) { mutableStateOf<String?>(null) }
    LaunchedEffect(activeVideoKey) {
        val target = activeVideoKey
        when {
            // Kapatma her zaman anında: oynatıcı bekletilmeden kaldırılır.
            target == null -> playerVideoKey = null
            playerVideoKey == target -> Unit
            // İlk açılışta bekleme yok: kullanıcı dokunduğu anda oynatıcı görünür.
            playerVideoKey == null -> playerVideoKey = target
            else -> {
                delay(INLINE_PLAYER_TAP_COALESCE_MS)
                if (activeVideoKey == target) playerVideoKey = target
            }
        }
    }

    val onSearchVideo: (DetailVideoItem) -> Unit = { item ->
        val query = java.net.URLEncoder.encode(item.title, "UTF-8")
        runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
    }
    val onDownloadVideo: (DetailVideoItem) -> Unit = { item ->
        val url = item.videoUrl
        if (url.isNullOrBlank()) {
            onSearchVideo(item)
        } else {
            com.kitsugi.animelist.core.download.video.VideoDownloadManager.start(
                context = shareContext,
                request = com.kitsugi.animelist.core.download.video.VideoDownloadRequest(
                    key = item.key,
                    title = if (effectiveMediaTitle.isBlank()) item.title else "$effectiveMediaTitle - ${item.title}",
                    url = url,
                    alternateUrls = item.alternateUrls
                )
            )
        }
    }
    val onCancelDownload: (DetailVideoItem) -> Unit = { item ->
        com.kitsugi.animelist.core.download.video.VideoDownloadManager.cancel(item.key)
        val url = item.videoUrl
        if (!url.isNullOrBlank()) {
            AnimeDownloadManager.cancelClipDownload(AnimeDownloadManager.clipKeyFor(url))
        }
    }

    LaunchedEffect(effectiveVideos) {
        val currentKey = activeVideoKey
        if (currentKey != null && effectiveVideos.none { it.key == currentKey }) {
            activeVideoKey = null
        }
    }

    val hasVideos = trailerItems.isNotEmpty() ||
        openingItems.isNotEmpty() ||
        endingItems.isNotEmpty() ||
        extraItems.isNotEmpty()

    Column(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 16.dp)
            .animateDetailContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!hasVideos) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(KitsugiColors.Surface)
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(R.string.ui_videos_loading),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.detail_video_empty),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            val selectVideo: (String) -> Unit = { key ->
                activeVideoKey = if (activeVideoKey == key) null else key
            }
            val collapseHidden: (Set<String>) -> Unit = { hiddenKeys ->
                if (activeVideoKey?.let { it in hiddenKeys } == true) activeVideoKey = null
            }

            if (trailerItems.isNotEmpty()) {
                DetailVideoGroup(
                    title = stringResource(R.string.inline_preview_c9d9dd2),
                    icon = Icons.Movie,
                    items = trailerItems,
                    activeVideoKey = activeVideoKey,
                    playerVideoKey = playerVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = selectVideo,
                    onSearch = onSearchVideo,
                    onCollapseHiddenItems = collapseHidden,
                    onShare = onShareVideo,
                    downloadStates = downloadStates,
                    onDownloadVideo = onDownloadVideo,
                    onCancelDownload = onCancelDownload
                )
            }

            if (openingItems.isNotEmpty()) {
                DetailVideoGroup(
                    title = stringResource(R.string.detail_openings),
                    icon = Icons.MusicNote,
                    items = openingItems,
                    activeVideoKey = activeVideoKey,
                    playerVideoKey = playerVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = selectVideo,
                    onSearch = onSearchVideo,
                    onCollapseHiddenItems = collapseHidden,
                    onShare = onShareVideo,
                    downloadStates = downloadStates,
                    onDownloadVideo = onDownloadVideo,
                    onCancelDownload = onCancelDownload
                )
            }

            if (endingItems.isNotEmpty()) {
                DetailVideoGroup(
                    title = stringResource(R.string.detail_endings),
                    icon = Icons.MusicNote,
                    items = endingItems,
                    activeVideoKey = activeVideoKey,
                    playerVideoKey = playerVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = selectVideo,
                    onSearch = onSearchVideo,
                    onCollapseHiddenItems = collapseHidden,
                    onShare = onShareVideo,
                    downloadStates = downloadStates,
                    onDownloadVideo = onDownloadVideo,
                    onCancelDownload = onCancelDownload
                )
            }

            if (extraItems.isNotEmpty()) {
                DetailVideoGroup(
                    title = stringResource(R.string.detail_video_extras),
                    icon = Icons.PlayCircle,
                    items = extraItems,
                    activeVideoKey = activeVideoKey,
                    playerVideoKey = playerVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = selectVideo,
                    onSearch = onSearchVideo,
                    onCollapseHiddenItems = collapseHidden,
                    onShare = onShareVideo,
                    downloadStates = downloadStates,
                    onDownloadVideo = onDownloadVideo,
                    onCancelDownload = onCancelDownload
                )
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ui_videos_loading),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/**
 * Videolar sekmesinin tek bir grubu (fragman / açılış / kapanış / klipler).
 *
 * Sağ üst köşede bu gruptaki videoları sağlayan kaynakların [Logo + İsim] rozetleri
 * yer alır; birden fazla kaynak varsa rozete dokunarak o kaynağa göre filtreleme
 * yapılabilir.
 */
@Composable
private fun DetailVideoGroup(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<DetailVideoItem>,
    /** Satır vurgusu: dokunulan an güncellenir. */
    activeVideoKey: String?,
    /** Oynatıcıyı taşıyan anahtar: hızlı dokunmalar durulduğunda güncellenir. */
    playerVideoKey: String?,
    accentColor: Color,
    onVideoSelected: (String) -> Unit,
    onSearch: (DetailVideoItem) -> Unit,
    onCollapseHiddenItems: (Set<String>) -> Unit = {},
    onShare: (DetailVideoItem) -> Unit = {},
    downloadStates: Map<String, ClipDownloadUiState> = emptyMap(),
    onDownloadVideo: (DetailVideoItem) -> Unit = {},
    onCancelDownload: (DetailVideoItem) -> Unit = {}
) {
    val initialCount = 3
    val groupSources = remember(items) {
        items.flatMap { it.sources }
            .map { canonicalVideoSource(it) }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    var selectedSourceFilter by remember(groupSources) { mutableStateOf<String?>(null) }
    val filteredItems = remember(items, selectedSourceFilter) {
        val activeFilter = selectedSourceFilter
        if (activeFilter == null || activeFilter !in groupSources) {
            items
        } else {
            items.filter { item -> item.sources.any { canonicalVideoSource(it) == activeFilter } }
        }
    }
    var expanded by remember(selectedSourceFilter) { mutableStateOf(false) }
    val hasMore = filteredItems.size > initialCount
    val showPerRowSources = groupSources.size > 1 || filteredItems.any { it.sources.size > 1 }
    val cardShape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // `background(color, shape)` çocukları KIRPMAZ; böylece kartın içindeki
            // olası bir SurfaceView (MPV yedeği) kırpma katmanına takılmaz.
            .background(KitsugiColors.Surface, cardShape)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.35f), cardShape)
            .animateDetailContentSize()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (filteredItems.size > 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = filteredItems.size.toString(),
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            if (groupSources.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val canFilter = groupSources.size > 1
                    groupSources.forEach { src ->
                        val isFilterActive = canFilter && selectedSourceFilter == src
                        DetailVideoSourceBadge(
                            source = src,
                            accentColor = accentColor,
                            isSelected = isFilterActive,
                            compact = false,
                            onClick = if (canFilter) {
                                {
                                    val next = if (selectedSourceFilter == src) null else src
                                    if (next != null) {
                                        val hidden = items
                                            .filterNot { item -> item.sources.any { canonicalVideoSource(it) == next } }
                                            .map { it.key }
                                            .toSet()
                                        onCollapseHiddenItems(hidden)
                                    }
                                    selectedSourceFilter = next
                                }
                            } else null
                        )
                    }
                }
            }
        }

        @Composable
        fun RenderVideoItem(item: DetailVideoItem) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))
                val isSelected = activeVideoKey == item.key && !item.playbackUrl.isNullOrBlank()
                // Oynatıcı yalnızca "oturmuş" seçimde kurulur (bkz. KitsugiMediaVideosTab).
                val isPlayerVisible = playerVideoKey == item.key && !item.playbackUrl.isNullOrBlank()
                DetailVideoRow(
                    item = item,
                    isSelected = isSelected,
                    accentColor = accentColor,
                    showSourceBadges = showPerRowSources,
                    downloadState = downloadStates[item.key],
                    onClick = {
                        if (item.playbackUrl.isNullOrBlank()) onSearch(item)
                        else onVideoSelected(item.key)
                    },
                    onShare = { onShare(item) },
                    onDownload = { onDownloadVideo(item) },
                    onCancelDownload = { onCancelDownload(item) }
                )

                // Oynatıcı aynı kartın içinde, satırın hemen altında açılır.
                if (isPlayerVisible) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        ThemePlayerContainer(
                            urls = item.playbackUrls,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .tvClickable(shape = RoundedCornerShape(9.dp)) { onVideoSelected(item.key) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.KeyboardArrowUp,
                                contentDescription = null,
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.kapat),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        val initialItems = remember(filteredItems) { filteredItems.take(initialCount) }
        val extraItems = remember(filteredItems) { filteredItems.drop(initialCount) }

        initialItems.forEach { item ->
            key(item.key) {
                RenderVideoItem(item)
            }
        }
        if (extraItems.isNotEmpty()) {
            KitsugiDetailReveal(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    extraItems.forEach { item ->
                        key(item.key) {
                            RenderVideoItem(item)
                        }
                    }
                }
            }
        }

        if (hasMore) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .tvClickable(shape = RoundedCornerShape(10.dp)) {
                            if (expanded) {
                                onCollapseHiddenItems(filteredItems.drop(initialCount).map { it.key }.toSet())
                            }
                            expanded = !expanded
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = if (expanded) stringResource(R.string.daha_az)
                        else stringResource(R.string.x_daha, filteredItems.size - initialCount),
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    KitsugiChevronIcon(
                        expanded = expanded,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailVideoRow(
    item: DetailVideoItem,
    isSelected: Boolean,
    accentColor: Color,
    showSourceBadges: Boolean = false,
    downloadState: ClipDownloadUiState? = null,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {}
) {
    val rowShape = RoundedCornerShape(12.dp)
    val canDownload = !item.videoUrl.isNullOrBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isSelected) accentColor.copy(alpha = 0.14f)
                else KitsugiColors.SurfaceSoft.copy(alpha = 0.6f)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(rowShape)
                .tvClickable(shape = rowShape, onClick = onClick)
                .padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.KeyboardArrowUp else Icons.PlayArrow,
                    contentDescription = null,
                    tint = if (isSelected) com.kitsugi.animelist.ui.theme.onAccentColor(accentColor) else accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Alt satır: indirme durumu varsa durum, yoksa mevcut oynatma ipucu.
                val state = downloadState
                val subtitle = when (state?.status) {
                    AnimeDownload.Status.DOWNLOADING ->
                        stringResource(R.string.kitsugi_clip_downloading, downloadState?.progress ?: 0)
                    AnimeDownload.Status.QUEUE -> stringResource(R.string.kitsugi_clip_queued)
                    AnimeDownload.Status.COMPLETED -> stringResource(R.string.kitsugi_clip_downloaded)
                    AnimeDownload.Status.PAUSED -> stringResource(R.string.kitsugi_clip_paused)
                    AnimeDownload.Status.ERROR ->
                        downloadState?.errorMessage?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.kitsugi_clip_error)
                    null -> if (item.videoUrl.isNullOrBlank()) stringResource(R.string.ui_open_youtube_search)
                    else stringResource(R.string.ui_play_in_app)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = subtitle,
                        color = when (state?.status) {
                            AnimeDownload.Status.COMPLETED -> KitsugiColors.AccentGreen
                            AnimeDownload.Status.ERROR -> KitsugiColors.AccentRed
                            AnimeDownload.Status.DOWNLOADING,
                            AnimeDownload.Status.QUEUE -> accentColor
                            else -> KitsugiColors.TextMuted
                        },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showSourceBadges && item.sources.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item.sources.take(3).forEach { src ->
                                DetailVideoSourceBadge(
                                    source = src,
                                    accentColor = accentColor,
                                    compact = true
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── İndirme düğmesi ──────────────────────────────────────────────────
        // Durumlar: kayıt yok → indir, indiriliyor/kuyrukta → iptal, tamam → onay,
        // duraklatıldı/hata → yeniden dene. Düğme yalnızca gerçek bir bağlantı varsa çizilir.
        if (canDownload) {
            val status = downloadState?.status
            val click = when (status) {
                AnimeDownload.Status.DOWNLOADING, AnimeDownload.Status.QUEUE -> onCancelDownload
                AnimeDownload.Status.COMPLETED -> ({ /* yerel kopya var; satıra dokununca oynatılır */ })
                else -> onDownload
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .tvClickable(shape = CircleShape, onClick = click),
                contentAlignment = Alignment.Center
            ) {
                when (status) {
                    AnimeDownload.Status.DOWNLOADING, AnimeDownload.Status.QUEUE -> {
                        CircularProgressIndicator(
                            progress = {
                                if (status == AnimeDownload.Status.QUEUE) 0f
                                else (downloadState?.progress ?: 0) / 100f
                            },
                            modifier = Modifier.size(17.dp),
                            color = accentColor,
                            strokeWidth = 2.dp
                        )
                    }
                    AnimeDownload.Status.COMPLETED -> Icon(
                        imageVector = Icons.CheckCircle,
                        contentDescription = stringResource(R.string.kitsugi_clip_downloaded),
                        tint = KitsugiColors.AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    AnimeDownload.Status.ERROR, AnimeDownload.Status.PAUSED -> Icon(
                        imageVector = Icons.Refresh,
                        contentDescription = stringResource(R.string.kitsugi_clip_retry_action),
                        tint = KitsugiColors.AccentRed,
                        modifier = Modifier.size(17.dp)
                    )
                    null -> Icon(
                        imageVector = Icons.Download,
                        contentDescription = stringResource(R.string.kitsugi_clip_download_action),
                        tint = accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Material IconButton 48 dp'lik dokunma hedefi ekleyip kompakt satırı şişiriyordu.
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .tvClickable(shape = CircleShape, onClick = onShare),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Share,
                contentDescription = stringResource(R.string.action_share),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun KitsugiThemesList(
    openings: List<com.kitsugi.animelist.data.remote.KitsugiTheme>,
    endings: List<com.kitsugi.animelist.data.remote.KitsugiTheme>
) {
    if (openings.isEmpty() && endings.isEmpty()) return

    val accentColor = LocalKitsugiAccent.current
    val uriHandler = LocalUriHandler.current
    var activeVideoUrl by remember { mutableStateOf<String?>(null) }
    var openingsExpanded by remember { mutableStateOf(false) }
    var endingsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // `background(color, shape)` köşeleri yuvarlar ama çocukları KIRPMAZ.
            // `clip()` kullanılsaydı MPV yedeğinin SurfaceView'ı kırpma katmanına takılıp
            // ses çalarken görüntüyü hiç çizmeyebilirdi (2026-10-21 saha raporu).
            .background(KitsugiColors.Surface, RoundedCornerShape(22.dp))
            .animateDetailContentSize()
            .padding(16.dp)
    ) {
        if (openings.isNotEmpty()) {
            ThemeVideoSection(
                title = stringResource(R.string.detail_openings),
                themes = openings,
                expanded = openingsExpanded,
                activeVideoUrl = activeVideoUrl,
                accentColor = accentColor,
                onExpandedChange = { openingsExpanded = it },
                onThemeClick = { theme ->
                    if (theme.videoUrl != null) {
                        activeVideoUrl = if (activeVideoUrl == theme.videoUrl) null else theme.videoUrl
                    } else {
                        val query = java.net.URLEncoder.encode(theme.label, "UTF-8")
                        runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                    }
                },
                onClosePlayer = { activeVideoUrl = null }
            )
        }

        if (endings.isNotEmpty()) {
            if (openings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = KitsugiColors.Background.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            ThemeVideoSection(
                title = stringResource(R.string.kapanis_muzikleri),
                themes = endings,
                expanded = endingsExpanded,
                activeVideoUrl = activeVideoUrl,
                accentColor = accentColor,
                onExpandedChange = { endingsExpanded = it },
                onThemeClick = { theme ->
                    if (theme.videoUrl != null) {
                        activeVideoUrl = if (activeVideoUrl == theme.videoUrl) null else theme.videoUrl
                    } else {
                        val query = java.net.URLEncoder.encode(theme.label, "UTF-8")
                        runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                    }
                },
                onClosePlayer = { activeVideoUrl = null }
            )
        }
    }
}

@Composable
private fun ThemeVideoSection(
    title: String,
    themes: List<com.kitsugi.animelist.data.remote.KitsugiTheme>,
    expanded: Boolean,
    activeVideoUrl: String?,
    accentColor: Color,
    onExpandedChange: (Boolean) -> Unit,
    onThemeClick: (com.kitsugi.animelist.data.remote.KitsugiTheme) -> Unit,
    onClosePlayer: () -> Unit
) {
    val initialCount = 3
    val hasMore = themes.size > initialCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateDetailContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (hasMore) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .tvClickable(shape = RoundedCornerShape(8.dp)) { onExpandedChange(!expanded) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (expanded) stringResource(R.string.daha_az)
                        else stringResource(R.string.x_daha, themes.size - initialCount),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    KitsugiChevronIcon(
                        expanded = expanded,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        @Composable
        fun RenderThemeItem(theme: com.kitsugi.animelist.data.remote.KitsugiTheme) {
            val isActive = activeVideoUrl == theme.videoUrl && activeVideoUrl != null
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(10.dp))
                ThemeRow(
                    theme = theme,
                    isActive = isActive,
                    accentColor = accentColor,
                    onClick = { onThemeClick(theme) }
                )
                if (isActive && theme.videoUrl != null) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        ThemePlayerContainer(
                            urls = listOfNotNull(theme.videoUrl) + theme.alternateVideoUrls,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onClosePlayer)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.KeyboardArrowUp,
                                contentDescription = null,
                                tint = KitsugiColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.kapat),
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        val initialThemes = remember(themes) { themes.take(initialCount) }
        val extraThemes = remember(themes) { themes.drop(initialCount) }

        initialThemes.forEachIndexed { index, theme ->
            key("${title}_init_${index}_${theme.label}") {
                RenderThemeItem(theme)
            }
        }
        if (extraThemes.isNotEmpty()) {
            KitsugiDetailReveal(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    extraThemes.forEachIndexed { index, theme ->
                        key("${title}_extra_${index}_${theme.label}") {
                            RenderThemeItem(theme)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(
    theme: com.kitsugi.animelist.data.remote.KitsugiTheme,
    isActive: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.15f) else KitsugiColors.SurfaceSoft)
            .tvClickable(shape = RoundedCornerShape(10.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isActive) accentColor else accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.KeyboardArrowUp else Icons.PlayArrow,
                contentDescription = null,
                tint = if (isActive) KitsugiColors.Background else accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.label,
                color = if (isActive) accentColor else KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = if (theme.videoUrl != null) stringResource(R.string.ui_play_in_app) else stringResource(R.string.ui_open_youtube_search),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
