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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.PlayCircle
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
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val uriHandler = LocalUriHandler.current

    var isPlayerError by remember(url) { mutableStateOf(false) }
    var isPlayerLoading by remember(url) { mutableStateOf(true) }
    var retryTrigger by remember(url) { mutableStateOf(0) }
    // İlk kare göründükten sonra ara bellek dalgalanmaları yükleme animasyonunu
    // tekrar tekrar açıp kapatmamalı: video oynarken üstünde beliren halka,
    // akıcı bir akışı bile "donuyor" gibi gösteriyordu.
    var hasStartedPlayback by remember(url) { mutableStateOf(false) }

    val videoId = remember(url) { extractYouTubeId(url) }

    if (videoId != null) {
        KitsugiYouTubePlayer(videoId = videoId, modifier = modifier)
    } else if (isYouTubePageUrl(url)) {
        // YouTube sayfası ama video kimliği yok (arama sonucu, kanal veya oynatma
        // listesi). Eskiden bu adres doğrudan Media3'e veriliyor, oynatıcı bir HTML
        // sayfasını indirmeye çalışıp hata veriyordu. Artık dürüst bir yönlendirme var.
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
                        stringResource(R.string.inline_open_in_browser_eed20ab),
                        color = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor)
                    )
                }
            }
        }
    } else {
        val dataStore = remember { com.kitsugi.animelist.data.settings.SettingsDataStore(context.applicationContext) }
        val appSettingsState = dataStore.settingsFlow.collectAsState(initial = null)
        val appSettings = appSettingsState.value
        val safeSettings = remember(appSettings) {
            (appSettings ?: com.kitsugi.animelist.data.settings.AppSettings()).forCompactInlinePlayback()
        }

        // Kompakt oynatıcı HER ZAMAN Media3 (TextureView) ile başlar.
        //
        // 2026-10-21 saha raporu: aynı sayfada fragman (Media3 + TextureView) sorunsuz
        // oynarken OP/ED dosyaları sesli ama siyah kalıyordu. Tek fark, varsayılan "MPV"
        // tercihi yüzünden OP/ED satırlarının MPV'nin SurfaceView'ını kullanmasıydı.
        // SurfaceView, Compose'un kırpılmış + `animateDetailContentSize()` ile ölçüsü
        // değişen kartında görünür bir yüzeye bağlanamıyor; mpv sesi çalıyor, kare
        // üretmiyor. TextureView aynı kabın içinde sorunsuz çiziliyor. Kullanıcının
        // genel motor tercihi TAM EKRANDA korunur; burada yalnızca çalışan yol seçilir.
        val preferredEngineType = initialCompactThemeEngineType(url)
        var activeEngineType by remember(url) {
            mutableStateOf(preferredEngineType)
        }
        var didTryAlternateEngine by remember(url) { mutableStateOf(false) }
        // Her yeniden deneme TAZE motor örneği kurar: hata sonrası serbest bırakılmış
        // çözücü/yüzey kalıntıları aynı örnekte yeni denemeyi de bozabiliyordu.
        var engineAttempt by remember(url) { mutableIntStateOf(0) }
        val playerEngine = remember(context, activeEngineType, url, engineAttempt) {
            when (activeEngineType) {
                PlayerEngineType.MPV -> MpvPlayerEngine(context, safeSettings)
                else -> Media3PlayerEngine(context, safeSettings)
            }
        }

        var isPlayingState by remember(url) { mutableStateOf(false) }
        var currentPosition by remember(url) { mutableStateOf(0L) }
        var duration by remember(url) { mutableStateOf(0L) }
        var playerState by remember(playerEngine) { mutableStateOf(PlayerEngine.State.IDLE) }
        val engineTypeForPlayer = activeEngineType
        val resumedState = rememberInlinePlayerResumed()

        fun switchToAlternateEngine(failedEngine: PlayerEngineType, reason: String) {
            if (failedEngine != activeEngineType) return
            if (!didTryAlternateEngine) {
                didTryAlternateEngine = true
                val alternate = failedEngine.alternateInlineEngine()
                Log.w("ThemePlayerContainer", "$failedEngine did not start ($reason); trying $alternate")
                // Yeni motor ilk kareyi üretene kadar tampon göstergesi görünmeli;
                // önceki motorun "oynatma başladı" bilgisi taşınmamalı.
                hasStartedPlayback = false
                // Eski motorun "oynatıyor" bilgisi yeni motora taşınmamalı; aksi hâlde
                // düğme duraklat ikonu gösterip ilk dokunuşta `pause()` çağırıyordu.
                isPlayingState = false
                isPlayerLoading = true
                activeEngineType = alternate
            } else {
                Log.e("ThemePlayerContainer", "Both inline engines failed for $url ($reason)")
                isPlayerLoading = false
                isPlayerError = true
            }
        }

        DisposableEffect(playerEngine) {
            var released = false
            val listener = object : PlayerEngine.Listener {
                override fun onStateChanged(state: PlayerEngine.State) {
                    if (released) return
                    playerState = state
                    isPlayingState = playerEngine.isPlaying
                    isPlayerLoading = state == PlayerEngine.State.BUFFERING
                }

                /**
                 * MPV motorunda duraklat/oynat geçişi yalnızca `onPlayingChanged` ile
                 * bildirilir; `onStateChanged` READY olarak kalır. Bu geri çağrı
                 * uygulanmadığı için arayüzdeki `isPlayingState` eski değerinde donuyor,
                 * kullanıcı duraklattıktan sonra oynat düğmesine bastığında tekrar
                 * `pause()` çağrılıyor ve video "açılmıyor" gibi görünüyordu.
                 */
                override fun onPlayingChanged(isPlaying: Boolean) {
                    if (released) return
                    isPlayingState = isPlaying
                }

                override fun onPlaybackError(errorCode: Int, errorMsg: String, cause: Throwable?) {
                    if (released) return
                    isPlayerLoading = false
                    Log.e("ThemePlayerContainer", "Oynatma hatası: $errorMsg (kod: $errorCode)")
                    switchToAlternateEngine(engineTypeForPlayer, errorMsg)
                }

                override fun onPositionChanged(positionMs: Long, durationMs: Long) {
                    if (released) return
                    currentPosition = positionMs
                    duration = durationMs
                    // Resume konumu ilk frame kanıtı değildir; gerçek video çıkışını bekle.
                    if (inlineVideoVisible(playerEngine)) hasStartedPlayback = true
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

        LaunchedEffect(resumedState.value, playerEngine) {
            if (!resumedState.value) {
                runCatching { playerEngine.pause() }
                isPlayingState = false
            }
        }

        LaunchedEffect(url, retryTrigger, activeEngineType, engineAttempt) {
            val attemptedEngine = engineTypeForPlayer
            isPlayerError = false
            isPlayerLoading = true
            playerState = PlayerEngine.State.BUFFERING
            try {
                val reachedReady = withTimeoutOrNull(INLINE_PLAYER_START_TIMEOUT_MS) {
                    playerEngine.prepare(
                        videoUrl = url,
                        headers = YouTubeRequestPolicy.headersForInlineUrl(url),
                        startPositionMs = currentPosition
                    )
                    if (!resumedState.value) {
                        runCatching { playerEngine.pause() }
                        isPlayingState = false
                        false
                    } else {
                        playerEngine.play()
                        // READY is only a preparation signal. Fall back if playback never
                        // advances instead of leaving the compact surface black at 00:00.
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

                // Doğrudan OP/ED dosyaları çoğunlukla VP9 `.webm`. Cihazın VP9 çözücüsü
                // yoksa Media3 hata vermez; ses çalar, görüntü hiç gelmez. Bekçi motoru
                // değiştirmeden önce çözücüyü 1 ms'lik bir seek ile dürtmeyi dener;
                // birçok donanım/GPU sürücüsü ilk kareyi ancak bu şekilde üretiyor.
                val audioOnly = waitForInlineVideoTrack(
                    engine = playerEngine,
                    isResumed = { resumedState.value },
                    nudgeRenderer = {
                        playerEngine.seekTo(playerEngine.currentPosition + 1L)
                    }
                )
                Log.i(
                    "ThemePlayerContainer",
                    "engine=$attemptedEngine visibleVideo=${inlineVideoVisible(playerEngine)} " +
                        "hasTrack=${playerEngine.hasVideoTrack} " +
                        "renderedFrame=${playerEngine.hasRenderedVideoFrame} url=${url.take(140)}"
                )
                if (audioOnly && attemptedEngine == activeEngineType) {
                    switchToAlternateEngine(
                        attemptedEngine,
                        "audio-only playback (track=${playerEngine.hasVideoTrack}, " +
                            "rendered=${playerEngine.hasRenderedVideoFrame})"
                    )
                }
            } catch (e: CancellationException) {
                // Closing or replacing this row cancels prepare; do not trigger a false fallback.
                throw e
            } catch (e: Exception) {
                if (attemptedEngine != activeEngineType) return@LaunchedEffect
                Log.e("ThemePlayerContainer", "Prepare error", e)
                switchToAlternateEngine(attemptedEngine, e.message ?: e.javaClass.simpleName)
            }
        }

        LaunchedEffect(playerEngine, isPlayingState) {
            if (isPlayingState) {
                while (true) {
                    currentPosition = playerEngine.currentPosition
                    duration = playerEngine.duration.coerceAtLeast(0L)
                    delay(1000)
                }
            }
        }

        // MPV bir SurfaceView kullanır; SurfaceView'ı kırpan bir Compose katmanı bazı
        // cihazlarda videoyu tamamen gizler (ses çalar, görüntü gelmez). Media3'ün
        // TextureView'ı kırpılmaktan etkilenmez; bu yüzden yalnızca yedek motor MPV'de
        // kırpmayı bırakıyoruz ve siyah zemini şekille çiziyoruz.
        Box(
            modifier = if (activeEngineType == PlayerEngineType.MPV) {
                modifier.background(Color.Black)
            } else {
                modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)
            },
            contentAlignment = Alignment.Center
        ) {
            if (isPlayerError) {
                // Dürüst son durak: iki motor da görüntü üretemedi. Eskiden yalnızca
                // "Tekrar Dene" vardı ve kullanıcı aynı siyah ekrana dönüyordu. Artık
                // birincil eylem tam ekran oynatıcı: MPV orada yazılım çözücüyle (VP9
                // dahil) açar; ikincil eylem yeniden deneme.
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
                                        headers = YouTubeRequestPolicy.headersForInlineUrl(url)
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
                                // Taze motor örneği + baştan hazırlık.
                                didTryAlternateEngine = false
                                activeEngineType = preferredEngineType
                                engineAttempt++
                                retryTrigger++
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Text(stringResource(R.string.action_retry), color = Color.White)
                        }
                    }
                    // MPV SurfaceView, kırpılmış/ölçüsü animasyonla değişen bir Compose
                    // kabında görüntü üretemeyebilir. Bu yüzden aynı dosya için tek
                    // çare motor değiştirmek değil; kullanıcıya elle de seçim sunulur.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KitsugiButton(
                            onClick = {
                                activeEngineType = activeEngineType.alternateInlineEngine()
                                didTryAlternateEngine = false
                                engineAttempt++
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
                        if (isPlayingState) playerEngine.pause() else playerEngine.play()
                    },
                    onSeek = { target ->
                        playerEngine.seekTo(target)
                    },
                    modifier = Modifier.fillMaxSize(),
                    isBuffering = !hasStartedPlayback && !isPlayerError,
                    doubleTapSeekSeconds = safeSettings.doubleTapSeekSeconds,
                    controlsAutoHideMs = com.kitsugi.animelist.core.player.PlayerUiPolicy
                        .clampOsdHideDelayMs(safeSettings.playerTimeToDisappearMs),
                    onFullscreen = {
                        playerEngine.pause()
                        KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                            context = context,
                            videoUrl = url,
                            headers = YouTubeRequestPolicy.headersForInlineUrl(url)
                        )
                    }
                ) {
                    // `AndroidView` fabrikası yalnızca düğüm ilk kurulurken çalışır. Motor
                    // yedeğe geçtiğinde aynı konumdaki düğüm eski motorun görünümünü tutuyor,
                    // yeni motor yüzey bulamadığı için ses çalıp görüntü vermiyordu.
                    key(playerEngine) {
                        AndroidView(
                            factory = { ctx ->
                                playerEngine.createVideoView(ctx)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // Tampon göstergesi artık kontrol katmanının merkez durumunda
                // (`InlineCenterOverlay.Buffering`) çiziliyor; burada ikinci bir halka
                // çizmek animasyonların üst üste binmesine yol açıyordu.
            }
        }
    }
}

private data class DetailVideoItem(
    val key: String,
    val title: String,
    val videoUrl: String?,
    val sources: List<String> = emptyList()
)

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

    val trailerItems = remember(effectiveVideos) {
        effectiveVideos
            .filter { it.category == MediaVideoCategory.TRAILER }
            .map { DetailVideoItem(it.key, it.title, it.videoUrl, it.sources) }
    }
    val openingItems = remember(effectiveVideos) {
        effectiveVideos
            .filter { it.category == MediaVideoCategory.OPENING }
            .map { DetailVideoItem(it.key, it.title, it.videoUrl, it.sources) }
    }
    val endingItems = remember(effectiveVideos) {
        effectiveVideos
            .filter { it.category == MediaVideoCategory.ENDING }
            .map { DetailVideoItem(it.key, it.title, it.videoUrl, it.sources) }
    }
    val extraItems = remember(effectiveVideos) {
        effectiveVideos
            .filter { it.category == MediaVideoCategory.EXTRA }
            .map { DetailVideoItem(it.key, it.title, it.videoUrl, it.sources) }
    }

    val uriHandler = LocalUriHandler.current
    val accentColor = LocalKitsugiAccent.current
    var activeVideoKey by remember { mutableStateOf<String?>(null) }
    // Videolar sekmesinden tek tek indirme: her satır kendi durumunu taşır.
    val downloadController = rememberVideoDownloadController(mediaTitle = mediaTitle)

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
            val onSearchVideo: (DetailVideoItem) -> Unit = { item ->
                val query = java.net.URLEncoder.encode(item.title, "UTF-8")
                runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
            }
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
                    accentColor = accentColor,
                    onVideoSelected = selectVideo,
                    onSearch = onSearchVideo,
                    onCollapseHiddenItems = collapseHidden,
                    onShare = onShareVideo,
                    downloadController = downloadController
                )
            }

            if (openingItems.isNotEmpty()) {
                DetailVideoGroup(
                    title = stringResource(R.string.detail_openings),
                    icon = Icons.MusicNote,
                    items = openingItems,
                    activeVideoKey = activeVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = selectVideo,
                    onSearch = onSearchVideo,
                    onCollapseHiddenItems = collapseHidden,
                    onShare = onShareVideo,
                    downloadController = downloadController
                )
            }

            if (endingItems.isNotEmpty()) {
                DetailVideoGroup(
                    title = stringResource(R.string.detail_endings),
                    icon = Icons.MusicNote,
                    items = endingItems,
                    activeVideoKey = activeVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = selectVideo,
                    onSearch = onSearchVideo,
                    onCollapseHiddenItems = collapseHidden,
                    onShare = onShareVideo,
                    downloadController = downloadController
                )
            }

            if (extraItems.isNotEmpty()) {
                DetailVideoGroup(
                    title = stringResource(R.string.detail_video_extras),
                    icon = Icons.Rounded.PlayCircle,
                    items = extraItems,
                    activeVideoKey = activeVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = selectVideo,
                    onSearch = onSearchVideo,
                    onCollapseHiddenItems = collapseHidden,
                    onShare = onShareVideo,
                    downloadController = downloadController
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
    activeVideoKey: String?,
    accentColor: Color,
    onVideoSelected: (String) -> Unit,
    onSearch: (DetailVideoItem) -> Unit,
    onCollapseHiddenItems: (Set<String>) -> Unit = {},
    onShare: (DetailVideoItem) -> Unit = {},
    downloadController: VideoDownloadController? = null
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
                val isSelected = activeVideoKey == item.key && !item.videoUrl.isNullOrBlank()
                DetailVideoRow(
                    item = item,
                    isSelected = isSelected,
                    accentColor = accentColor,
                    showSourceBadges = showPerRowSources,
                    onClick = {
                        if (item.videoUrl.isNullOrBlank()) onSearch(item)
                        else onVideoSelected(item.key)
                    },
                    onShare = { onShare(item) },
                    downloadState = downloadController?.stateOf(item.key) ?: VideoDownloadState.Idle,
                    onDownload = {
                        downloadController?.onDownloadClick(item.key, item.title, item.videoUrl)
                    }
                )

                // Oynatıcı aynı kartın içinde, satırın hemen altında açılır.
                if (isSelected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        ThemePlayerContainer(
                            url = item.videoUrl!!,
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
    onClick: () -> Unit,
    onShare: () -> Unit,
    downloadState: VideoDownloadState = VideoDownloadState.Idle,
    onDownload: () -> Unit = {}
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = when (downloadState) {
                            is VideoDownloadState.Running -> {
                                val percent = downloadState.progress
                                    ?.let { stringResource(R.string.detail_video_download_progress, (it * 100).toInt()) }
                                    ?: stringResource(R.string.detail_video_download_preparing)
                                stringResource(R.string.detail_video_download_running_status, percent)
                            }
                            is VideoDownloadState.Done -> stringResource(R.string.detail_video_download_done)
                            is VideoDownloadState.Failed -> downloadState.message
                            VideoDownloadState.Idle ->
                                if (item.videoUrl.isNullOrBlank()) stringResource(R.string.ui_open_youtube_search)
                                else stringResource(R.string.ui_play_in_app)
                        },
                        color = when (downloadState) {
                            is VideoDownloadState.Failed -> KitsugiColors.AccentRed
                            is VideoDownloadState.Done -> accentColor
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
        // İndirme düğmesi — paylaş düğmesinin hemen SOLUNDA.
        // Bağlantısı olmayan satırlarda da görünür (her satırda aynı düzen korunur);
        // basıldığında denetleyici "indirilebilir akış yok" uyarısı gösterir.
        VideoDownloadAction(
            state = downloadState,
            accentColor = accentColor,
            enabled = canDownload || downloadState !is VideoDownloadState.Idle,
            onDownload = onDownload
        )

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

/**
 * Satırdaki indirme düğmesi.
 *
 * Üç durumu vardır:
 *  - boşta: indirme ikonu,
 *  - indiriliyor: dairesel ilerleme + yüzde (dokununca iptal),
 *  - tamamlandı: onay ikonu (dokununca yeniden indirme seçeneği sunmaz, dosya zaten var).
 */
@Composable
private fun VideoDownloadAction(
    state: VideoDownloadState,
    accentColor: Color,
    enabled: Boolean,
    onDownload: () -> Unit
) {
    val isDone = state is VideoDownloadState.Done
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .tvClickable(shape = CircleShape) {
                if (!isDone) onDownload()
            },
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is VideoDownloadState.Running -> {
                CircularProgressIndicator(
                    progress = { state.progress ?: 0f },
                    modifier = Modifier.size(18.dp),
                    color = accentColor,
                    strokeWidth = 2.dp
                )
            }
            is VideoDownloadState.Done -> {
                Icon(
                    imageVector = Icons.CheckCircle,
                    contentDescription = stringResource(R.string.detail_video_download_done),
                    tint = accentColor,
                    modifier = Modifier.size(17.dp)
                )
            }
            is VideoDownloadState.Failed -> {
                Icon(
                    imageVector = Icons.Refresh,
                    contentDescription = stringResource(R.string.action_retry),
                    tint = KitsugiColors.AccentRed,
                    modifier = Modifier.size(17.dp)
                )
            }
            VideoDownloadState.Idle -> {
                Icon(
                    imageVector = Icons.Download,
                    contentDescription = stringResource(R.string.detail_video_download),
                    tint = if (enabled) KitsugiColors.TextMuted else KitsugiColors.TextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(17.dp)
                )
            }
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
                            url = theme.videoUrl,
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
