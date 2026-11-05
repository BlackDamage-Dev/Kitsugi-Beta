package com.kitsugi.animelist.ui.screens.fullscreen

import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onGloballyPositioned
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kitsugi.animelist.data.cloudstream.CsVideoInterceptorFactory
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.core.player.QualityOption
import com.kitsugi.animelist.data.repository.AddonStreamRepository
import com.kitsugi.animelist.data.repository.StreamProbe
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.data.trailer.YoutubeChunkedDataSourceFactory
import com.kitsugi.animelist.ui.screens.fullscreen.components.PauseOverlay
import com.kitsugi.animelist.ui.screens.fullscreen.components.QuickRateBar
import com.kitsugi.animelist.ui.screens.fullscreen.components.FeedbackBubble
import com.kitsugi.animelist.ui.screens.fullscreen.components.KeyboardActionIndicator
import com.kitsugi.animelist.ui.screens.fullscreen.components.KeyboardShortcutsHelpOverlay
import com.kitsugi.animelist.ui.screens.fullscreen.components.PlayerInlineLoadingOverlay
import com.kitsugi.animelist.ui.screens.fullscreen.components.PlayerLoadingView
import com.kitsugi.animelist.ui.screens.fullscreen.components.PlayerErrorView
import com.kitsugi.animelist.ui.screens.fullscreen.components.SubtitleStyleSettings
import com.kitsugi.animelist.ui.screens.fullscreen.components.StreamInfoData
import com.kitsugi.animelist.ui.screens.fullscreen.components.MetaCastMember
import com.kitsugi.animelist.ui.screens.fullscreen.components.TorrentOverlay
import com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption
import com.kitsugi.animelist.core.p2p.P2pStreamingEngine
import com.kitsugi.animelist.core.p2p.P2pSettingsRepository
import com.kitsugi.animelist.core.p2p.P2pStreamingState
import com.kitsugi.animelist.core.p2p.isStreaming
import com.kitsugi.animelist.core.p2p.downloadSpeed
import com.kitsugi.animelist.core.p2p.uploadSpeed
import com.kitsugi.animelist.core.p2p.peers
import com.kitsugi.animelist.core.p2p.seeds
import com.kitsugi.animelist.core.p2p.bufferProgress
import com.kitsugi.animelist.ui.screens.fullscreen.controls.PlayerControls
import com.kitsugi.animelist.ui.screens.fullscreen.controls.PlayerKeyboardShortcuts
import com.kitsugi.animelist.ui.screens.fullscreen.controls.PlayerKeyAction
import com.kitsugi.animelist.core.player.TrackFormatUtils
import com.kitsugi.animelist.ui.screens.fullscreen.controls.PlayerSheetsHost
import com.kitsugi.animelist.ui.screens.fullscreen.controls.PlayerPanelsHost
import com.kitsugi.animelist.ui.screens.fullscreen.controls.PlayerDialogsHost
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.BrightnessOverlay
import android.util.Log
import com.kitsugi.animelist.core.player.PlayerLogger
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.AudioOutputRouteDetector
import com.kitsugi.animelist.core.player.AudioRoute
import com.kitsugi.animelist.core.player.engine.PlayerEngineType
import com.kitsugi.animelist.core.player.engine.PlayerEngineSelector
import com.kitsugi.animelist.core.player.engine.Media3PlayerEngine
import com.kitsugi.animelist.core.player.engine.MpvPlayerEngine
import com.kitsugi.animelist.utils.KitsugiStrings
// Legacy gesture imports removed
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import com.kitsugi.animelist.ui.screens.fullscreen.components.PreviewGenerator
import com.kitsugi.animelist.ui.screens.fullscreen.components.formatMs
import com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

private data class PendingAudioExport(
    val track: TrackOption? = null,
    val tracks: List<TrackOption> = emptyList(),
    val providerAudioIndex: Int? = null,
    val providerAudioUrl: String? = null,
    val videoUrl: String?,
    val audioUrl: String?,
    val sourceIndex: Int
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun KitsugiFullscreenPlayerScreen(
    videoId: String?,
    videoUrl: String?,
    audioUrl: String?,
    title: String,
    requestHeaders: Map<String, String> = emptyMap(),
    initialSubtitles: List<SubtitleInput> = emptyList(),
    streamSources: List<StreamSource> = emptyList(),
    initialIndex: Int = -1,
    malId: Int? = null,
    aniListId: Int? = null,
    tmdbId: Int? = null,
    bangumiId: Int? = null,
    imdbId: String? = null,
    season: Int = 1,
    episode: Int = 1,
    animeTitle: String = "",
    posterUrl: String? = null,
    titleEnglish: String? = null,
    titleRomaji: String? = null,
    titleNative: String? = null,
    synonyms: List<String> = emptyList(),
    startYear: Int? = null,
    description: String? = null,
    castList: List<MetaCastMember> = emptyList(),
    isMovie: Boolean = false,
    cs3Url: String? = null,
    cs3ApiName: String? = null,
    resumePosition: Long = 0L,
    historyAnimeId: String? = null,
    autoResume: Boolean = false,
    mediaType: com.kitsugi.animelist.model.MediaType? = null,
    knownImdbId: String? = null,
    treatAsTrailer: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) break
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }
    val scope   = rememberCoroutineScope()
    
    var isInPipMode by remember { mutableStateOf(activity?.isInPictureInPictureMode ?: false) }
    DisposableEffect(activity) {
        val compActivity = activity as? androidx.activity.ComponentActivity
        if (compActivity == null) return@DisposableEffect onDispose {}
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        compActivity.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            compActivity.removeOnPictureInPictureModeChangedListener(listener)
        }
    }
    
    val mainFocusRequester = remember { FocusRequester() }
    // Tracks whether the focusable root Box is fully attached to the layout tree.
    // We must NOT call requestFocus() before this is true — doing so throws IllegalStateException.
    var isFocusTargetAttached by remember { mutableStateOf(false) }

    LaunchedEffect(isFocusTargetAttached) {
        if (isFocusTargetAttached) {
            try {
                mainFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("KitsugiPlayer", "Failed to request initial focus", e)
            }
        }
    }
    
    val sharedPrefs = remember {
        context.getSharedPreferences("MyWebViewPrefs", Context.MODE_PRIVATE)
    }

    val dataStore = remember { SettingsDataStore(context.applicationContext) }
    val appSettingsState = dataStore.settingsFlow.collectAsState(initial = null)
    val appSettings = appSettingsState.value
    var isSettingsLoaded by remember { mutableStateOf(false) }

    // ViewModel Integration
    val viewModel: KitsugiPlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val subtitlesPicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addSubtitle(uri)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addAudio(uri)
        }
    }

    // On Android 8-9 the public Downloads directory needs a runtime write grant.
    var pendingAudioExport by remember { mutableStateOf<PendingAudioExport?>(null) }
    val audioStoragePermission = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingAudioExport?.let { pending ->
            when {
                !granted -> Toast.makeText(context, "Ses indirmek için depolama izni gerekli", Toast.LENGTH_LONG).show()
                viewModel.currentVideoUrl.value != pending.videoUrl ||
                    viewModel.currentAudioUrl.value != pending.audioUrl ||
                    viewModel.currentSourceIndex.value != pending.sourceIndex ->
                    Toast.makeText(context, "Kaynak değişti; sesi tekrar seçin", Toast.LENGTH_LONG).show()
                else -> pending.providerAudioIndex?.let { index ->
                    viewModel.downloadProviderAudioToStorage(index, pending.providerAudioUrl)
                } ?: pending.track?.let { viewModel.downloadAudioToStorage(it, pending.tracks) }
            }
        }
        pendingAudioExport = null
    }
    
    // ── İzleme geçmişi için animeId türet ────────────────────────────────────
    val watchHistoryAnimeId = remember(aniListId, malId, cs3Url, animeTitle) {
        when {
            aniListId != null -> aniListId.toString()
            malId != null     -> malId.toString()
            !cs3Url.isNullOrBlank() -> cs3Url.hashCode().toString()
            else              -> animeTitle.hashCode().toString()
        }
    }

    // ── Periyodik izleme konumu kaydı (her 10 sn) ───────────────────────────
    // Not: currentEpisode aşağıda (satır ~263) collectAsState() ile tanımlanıyor,
    // burada duplicate oluşmasın diye viewModel StateFlow'larına doğrudan erişiyoruz.
    LaunchedEffect(watchHistoryAnimeId) {
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            val currentPos = viewModel.pos.value
            val currentDur = viewModel.duration.value
            val ep = viewModel.currentEpisode.value
            if (currentPos > 0L && currentDur > 0L && watchHistoryAnimeId.isNotBlank()) {
                com.kitsugi.animelist.data.local.WatchHistoryManager.updateProgress(
                    animeId    = watchHistoryAnimeId,
                    episode    = ep,
                    positionMs = currentPos,
                    durationMs = currentDur
                )
            }
        }
    }

    LaunchedEffect(videoId, videoUrl, audioUrl, title, initialIndex, episode, tmdbId, isMovie, synonyms, bangumiId, imdbId, historyAnimeId, autoResume, treatAsTrailer) {
        viewModel.initialize(
            videoId = videoId,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            title = title,
            requestHeaders = requestHeaders,
            initialSubtitles = initialSubtitles,
            streamSources = streamSources,
            initialIndex = initialIndex,
            malId = malId,
            aniListId = aniListId,
            tmdbId = tmdbId,
            bangumiId = bangumiId,
            imdbId = imdbId,
            season = season,
            episode = episode,
            animeTitle = animeTitle,
            titleEnglish = titleEnglish,
            titleRomaji = titleRomaji,
            titleNative = titleNative,
            synonyms = synonyms,
            startYear = startYear,
            isMovie = isMovie,
            activity = activity,
            cs3Url = cs3Url,
            cs3ApiName = cs3ApiName,
            historyAnimeId = historyAnimeId,
            autoResume = autoResume,
            resumePositionMs = resumePosition,
            mediaType = mediaType,
            knownImdbId = knownImdbId ?: imdbId,
            treatAsTrailer = treatAsTrailer,
            posterUrl = posterUrl
        )
    }

    val currentVideoUrl by viewModel.currentVideoUrl.collectAsState()
    val currentAudioUrl by viewModel.currentAudioUrl.collectAsState()
    val currentHeaders by viewModel.currentHeaders.collectAsState()
    val currentSubtitles by viewModel.currentSubtitles.collectAsState()
    val currentTitle by viewModel.currentTitle.collectAsState()
    val currentSourceIndex by viewModel.currentSourceIndex.collectAsState()
    val currentStreamSources by viewModel.currentStreamSources.collectAsState()
    val currentAddonName by viewModel.currentAddonName.collectAsState()

    // ── Kalite (açık akışın GERÇEK video varyantları) ────────────────────────
    // rapidrame / closeload kaynakları tek bir master.m3u8 verir ve eklenti kaliteyi
    // bildirmez (Qualities.Unknown). Kalite listesi bu yüzden master playlist'in
    // kendisinden ölçülür; kaynak/parça değişince sıfırlanır.
    var qualityOptions by remember(currentSourceIndex) { mutableStateOf<List<QualityOption>>(emptyList()) }
    var qualityMasterUrl by remember(currentSourceIndex) { mutableStateOf<String?>(null) }
    var isQualityVariantsLoading by remember(currentSourceIndex) { mutableStateOf(false) }
    var selectedQuality by remember(currentSourceIndex) { mutableStateOf<QualityOption?>(null) }
    /**
     * Varyant URL'si oynatılamazsa (imzalı CDN token'ı süresi dolmuş / 403) master'a
     * **bir kez** geri dönmek için kullanılan işaret. Aynı URL'de döngüye girilmez.
     */
    var variantFallbackUrl by remember { mutableStateOf<String?>(null) }
    val currentEpisode by viewModel.currentEpisode.collectAsState()
    val episodesList by viewModel.episodesList.collectAsState()
    val episodesLoading by viewModel.episodesLoading.collectAsState()
    val quickRatePrompt by viewModel.quickRatePrompt.collectAsState()
    val userCancelledBinge by viewModel.userCancelledBingeFlow.collectAsState()
    val isResolvingStream by viewModel.isResolvingStreamFlow.collectAsState()
    val nextEpisodeLoading by viewModel.nextEpisodeLoading.collectAsState()
    val playbackSource by viewModel.playbackSource.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasError by viewModel.hasError.collectAsState()
    val errorDetails by viewModel.errorDetails.collectAsState()
    val isAutoSwitching by viewModel.isAutoSwitching.collectAsState()

    // P2P Torrent lifecycle
    DisposableEffect(Unit) {
        onDispose {
            P2pStreamingEngine.stopStream()
        }
    }

    // AniSkip state
    val skipIntervals by viewModel.skipIntervals.collectAsState()
    val aniSkipAutoSkip by viewModel.aniSkipAutoSkip.collectAsState()
    val aniSkipEnabled by viewModel.aniSkipEnabled.collectAsState()
    val animeSkipClientId by viewModel.animeSkipClientId.collectAsState()


    val sleepTimerSecondsLeft by viewModel.sleepTimerSecondsLeft.collectAsState()
    val currentBrightness by viewModel.currentBrightness.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    // ── New Aniyomi-style reactive UI state ──────────────────────────────────
    val sheetShown by viewModel.sheetShown.collectAsState()
    val dismissSheet by viewModel.dismissSheet.collectAsState()
    val panelShown by viewModel.panelShown.collectAsState()
    val dialogShown by viewModel.dialogShown.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val currentChapter by viewModel.currentChapter.collectAsState()
    val areControlsLocked by viewModel.areControlsLocked.collectAsState()

    LaunchedEffect(currentSourceIndex, currentVideoUrl) {
        val url = currentVideoUrl?.trim().orEmpty()
        if (url.isEmpty()) return@LaunchedEffect

        // Kullanıcı listeden bir varyant seçtiyse master'ı yeniden ölçmeyiz:
        // oynatılan URL zaten bilinen bir varyanttır, liste korunur.
        if (url != qualityMasterUrl && qualityOptions.any { it.switchUrl == url }) return@LaunchedEffect

        val source = currentStreamSources.getOrNull(currentSourceIndex) ?: return@LaunchedEffect
        qualityMasterUrl = url
        isQualityVariantsLoading = true
        val measured = runCatching {
            StreamProbe.qualityOptions(source.copy(url = url))
        }.getOrDefault(emptyList())
        qualityOptions = measured
        // Aynı seçim hâlâ geçerliyse (ör. DASH tavanı) korunur; aksi hâlde "Otomatik".
        val selectionStillValid = selectedQuality?.let { selected ->
            measured.any { it.key == selected.key }
        } == true
        if (!selectionStillValid) selectedQuality = null
        isQualityVariantsLoading = false
    }

    // Kalite sayfası açıldığında liste boşsa yeniden ölç: geçici ağ hatası / 403
    // yüzünden boş kalan bir sonuç, kullanıcı sayfayı yeniden açınca tekrar denenir.
    LaunchedEffect(sheetShown) {
        if (sheetShown != KitsugiSheets.QualityTracks) return@LaunchedEffect
        if (qualityOptions.isNotEmpty() || isQualityVariantsLoading) return@LaunchedEffect
        val url = currentVideoUrl?.trim().orEmpty()
        val source = currentStreamSources.getOrNull(currentSourceIndex) ?: return@LaunchedEffect
        if (url.isEmpty()) return@LaunchedEffect
        isQualityVariantsLoading = true
        qualityOptions = runCatching {
            StreamProbe.qualityOptions(source.copy(url = url))
        }.getOrDefault(emptyList())
        isQualityVariantsLoading = false
    }

    LaunchedEffect(panelShown, sheetShown, isFocusTargetAttached) {
        if (panelShown == KitsugiPanels.None && sheetShown == KitsugiSheets.None && isFocusTargetAttached) {
            try {
                mainFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("KitsugiPlayer", "Failed to restore focus after panel/sheet close", e)
            }
        }
    }
    
    // Autoplay / binge card (replaces full-screen countdown)
    val isAutoplayEnabled = appSettings?.isAutoplayEnabled ?: true
    val showBingeCard by viewModel.showBingeCardState.collectAsState()
    val customButtons by viewModel.customButtons.collectAsState()
    val statisticsPage by viewModel.statisticsPage.collectAsState()
    val audioChannels by viewModel.audioChannels.collectAsState()

    val bingeCountdownSec by viewModel.bingeCountdownSec.collectAsState()

    // Track selections
    var textTrackOptions by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var audioTrackOptions by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var videoQualityTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var isVideoQualityAuto by remember { mutableStateOf(true) }
    var isSubtitleDisabled by remember { mutableStateOf(false) }

    // Subtitle style, delay, hız ve video filtreleri
    var subtitleStyle by remember { mutableStateOf(SubtitleStyleSettings()) }
    var subtitleDelayMs by remember { mutableStateOf(0L) }
    var secondarySubtitleDelayMs by remember { mutableStateOf(0L) }
    var subtitleSpeed by remember { mutableStateOf(1f) }
    var videoFilterValues by remember { mutableStateOf<Map<KitsugiVideoFilters, Int>>(emptyMap()) }

    // Audio delay & boost
    var audioDelayMs by remember { mutableStateOf(0L) }
    var audioBoostLevel by remember { mutableStateOf(0f) }
    var audioDelaySaveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var topBarHeightState by remember { mutableStateOf(0f) }
    var bottomControlsHeightState by remember { mutableStateOf(0f) }

    var currentAspectMode by remember { mutableStateOf(com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL) }
    var screenOrientationState by remember { mutableStateOf(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR) }

    LaunchedEffect(appSettings) {
        if (appSettings != null && !isSettingsLoaded) {
            subtitleStyle = SubtitleStyleSettings(
                size = appSettings.defaultSubtitleSize,
                textColor = appSettings.defaultSubtitleColor,
                bold = appSettings.subtitleBold,
                outlineEnabled = appSettings.subtitleOutlineEnabled,
                italic = appSettings.subtitleItalic,
                font = appSettings.subtitleFont,
                fontSize = 55, // MPV default, persisted via subtitleFontScale/pos but we init from 55
                borderSize = appSettings.subtitleBorderSize.toInt().coerceIn(0, 10),
                borderStyle = appSettings.subtitleBorderStyle,
                shadowOffset = appSettings.subtitleShadowOffset.toInt().coerceIn(0, 10),
                justification = appSettings.subtitleJustification,
                backgroundColor = appSettings.subtitleBackgroundColor,
                borderColor = appSettings.subtitleBorderColor,
                fontScale = appSettings.subtitleFontScale,
                pos = appSettings.subtitlePos,
                overrideAss = appSettings.subtitleOverrideAss,
                verticalOffset = 0
            )
            subtitleSpeed = appSettings.subtitleSpeed
            videoFilterValues = runCatching {
                val json = org.json.JSONObject(appSettings.videoFiltersJson)
                KitsugiVideoFilters.entries.associateWith { json.optInt(it.mpvProperty, 0).coerceIn(-100, 100) }
            }.getOrDefault(emptyMap())
            subtitleDelayMs = appSettings.subtitleDelayMs
            audioDelayMs = appSettings.defaultAudioDelayMs
            audioBoostLevel = appSettings.defaultAudioBoost
            secondarySubtitleDelayMs = appSettings.secondarySubtitleDelayMs
            currentAspectMode = runCatching { com.kitsugi.animelist.core.player.PlayerAspectMode.valueOf(appSettings.aspectMode) }.getOrDefault(com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL)
            val initialOrientation = when (appSettings.defaultPlayerOrientation) {
                "SENSOR_LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                "SENSOR_PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
            screenOrientationState = initialOrientation
            activity?.requestedOrientation = initialOrientation
            isSettingsLoaded = true
        }
    }

    // ── Çökme teşhisi: "son ekran" izi ───────────────────────────────────────
    // Oynatıcı ayrı bir Activity'de çalıştığı için AppRoot içindeki ekran izleyici
    // burayı hiç görmüyordu; çökme raporlarında "SON BİLİNEN EKRAN: baslangic"
    // yazıyor ve hatanın nerede olduğu anlaşılamıyordu.
    LaunchedEffect(currentTitle, currentEpisode, currentVideoUrl) {
        runCatching {
            com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                .noteScreen("Oynatıcı: $currentTitle (Bölüm $currentEpisode)")
        }
    }

    LaunchedEffect(videoFilterValues, isSettingsLoaded) {
        if (isSettingsLoaded) {
            delay(400)
            val json = org.json.JSONObject()
            videoFilterValues.forEach { (filter, value) -> json.put(filter.mpvProperty, value) }
            dataStore.setVideoFiltersJson(json.toString())
        }
    }

    LaunchedEffect(currentAspectMode) {
        if (isSettingsLoaded) {
            viewModel.setAspectMode(currentAspectMode)
        }
    }

    // Stream diagnostics
    var streamInfoData by remember { mutableStateOf(StreamInfoData(playerEngine = "ExoPlayer")) }

    val mediaIdForHistory = remember(malId, aniListId, cs3Url, animeTitle) {
        malId ?: aniListId
            ?: if (!cs3Url.isNullOrBlank()) cs3Url.hashCode()
               else animeTitle.hashCode()
    }
    val resumeStore = remember(context) { com.kitsugi.animelist.core.player.PlayerResumeStore(context) }
    val activeResumeSource = currentStreamSources.getOrNull(currentSourceIndex)
    val resumeIdentity = remember(
        mediaIdForHistory, season, currentEpisode, currentAddonName,
        activeResumeSource?.name, activeResumeSource?.title, activeResumeSource?.url,
        activeResumeSource?.infoHash, activeResumeSource?.fileIndex, currentVideoUrl
    ) {
        com.kitsugi.animelist.core.player.PlayerResumeIdentity(
            mediaId = mediaIdForHistory,
            season = season,
            episode = currentEpisode,
            addonName = activeResumeSource?.addonName ?: currentAddonName,
            sourceName = activeResumeSource?.title?.takeIf { it.isNotBlank() }
                ?: activeResumeSource?.name?.takeIf { it.isNotBlank() }
                ?: currentTitle,
            sourceUrl = activeResumeSource?.infoHash?.let { "torrent:$it:${activeResumeSource.fileIndex ?: -1}" }
                ?: activeResumeSource?.url
                ?: currentVideoUrl
                ?: videoUrl
        )
    }
    val latestResumeIdentity by rememberUpdatedState(resumeIdentity)
    var savedPos by remember(resumeIdentity) { mutableStateOf<Long?>(null) }
    var showResumeDialog by remember(resumeIdentity) { mutableStateOf(false) }
    var pendingResumePos by remember(resumeIdentity) { mutableStateOf(0L) }
    var hasCheckedResume by remember(resumeIdentity) { mutableStateOf(false) }

    LaunchedEffect(resumeIdentity) {
        val exactStreamPosition = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            resumeStore.get(resumeIdentity)
        }
        savedPos = exactStreamPosition.takeIf { it > 0L }
            ?: resumePosition.takeIf { it > 0L && currentEpisode == episode && currentSourceIndex == initialIndex }
    }

    fun playEpisode(targetEp: Int) {
        viewModel.resetAutoSwitch()
        viewModel.playEpisode(
            targetEp = targetEp,
            activity = activity,
            onAlternativeRequired = {
                viewModel.showSheet(KitsugiSheets.SourceTracks)
                Toast.makeText(context, KitsugiStrings.get(R.string.ayni_kaynak_bulunamadi_diger_kaynaklar_listeleniyor), Toast.LENGTH_LONG).show()
            },
            onResolutionFailed = {
                viewModel.showSheet(KitsugiSheets.SourceTracks)
                Toast.makeText(context, KitsugiStrings.get(R.string.secilen_kaynak_cozumlenemedi_lutfen_listeden_baska_bir_kaynak_se), Toast.LENGTH_LONG).show()
            }
        )
    }

    fun playNextEpisode() {
        viewModel.playNextEpisode(
            activity = activity,
            onAlternativeRequired = {
                viewModel.showSheet(KitsugiSheets.SourceTracks)
                Toast.makeText(context, KitsugiStrings.get(R.string.ayni_kaynak_bulunamadi_diger_kaynaklar_listeleniyor), Toast.LENGTH_LONG).show()
            },
            onResolutionFailed = {
                viewModel.showSheet(KitsugiSheets.SourceTracks)
                Toast.makeText(context, KitsugiStrings.get(R.string.secilen_kaynak_cozumlenemedi_lutfen_listeden_baska_bir_kaynak_se), Toast.LENGTH_LONG).show()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { isFocusTargetAttached = true },
        contentAlignment = Alignment.Center
    ) {
        when {
            isAutoSwitching -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    KitsugiEmojiLabel(
                        label = "Yedek kaynak deneniyor...",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        iconSize = 14.dp,
                        icon = Icons.Zap
                    )
                    Text(
                        text = "Kaynak: ${currentAddonName.orEmpty()} → sonraki",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Sadece playbackSource yokken yüklenme göster; settings asla video oynatmayı ENGELLEMEMELI
            isLoading && playbackSource == null -> {
                PlayerLoadingView()
            }

            hasError || playbackSource == null -> {
                PlayerErrorView(
                    message = errorDetails,
                    canOpenExternal = !currentVideoUrl.isNullOrBlank(),
                    onBack = onBack,
                    onOpenExternal = {
                        val urlVal = currentVideoUrl
                        if (!urlVal.isNullOrBlank()) {
                            PlayerLogger.logExternalPlayerLaunch(
                                context   = context,
                                url       = urlVal,
                                addonName = currentAddonName,
                                title     = currentTitle,
                                manual    = true
                            )
                            val launched = KitsugiFullscreenPlayerActivity.launchExternalPlayer(
                                context = context,
                                videoUrl = urlVal,
                                title = currentTitle,
                                positionMs = savedPos ?: 0L,
                                headers = currentHeaders,
                                subtitles = currentSubtitles
                            )
                            // v2.4.319: Harici oynatıcı açılamadıysa ekranı KAPATMA.
                            if (launched) {
                                onBack()
                            } else {
                                Toast.makeText(
                                    context,
                                    KitsugiStrings.get(R.string.harici_oynatici_baslatilamadi),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onRetry = { viewModel.retryCurrentSource() },
                    onSwitchSource = {
                        if (currentStreamSources.isNotEmpty()) {
                            viewModel.showSheet(KitsugiSheets.SourceTracks)
                        } else {
                            KitsugiStreamActivity.start(
                                context = context,
                                malId = malId,
                                aniListId = aniListId,
                                tmdbId = tmdbId,
                                episode = currentEpisode,
                                season = season,
                                isMovie = isMovie,
                                title = animeTitle.ifBlank { title },
                                posterUrl = posterUrl,
                                titleEnglish = titleEnglish,
                                titleRomaji = titleRomaji,
                                titleNative = titleNative,
                                synonyms = synonyms,
                                startYear = startYear,
                                description = description,
                                cast = castList,
                                isAutoplay = true
                            )
                            activity?.finish()
                        }
                    },
                    switchSourceText = if (currentStreamSources.isNotEmpty()) null else "Kaynakları Yeniden Ara"
                )
            }

            else -> {
                val source = playbackSource

                // settings null ise varsayılan kullan — settings hiçbir zaman oynatmayı engellemez
                val safeSettings = appSettings ?: com.kitsugi.animelist.data.settings.AppSettings()

                val formattedTitle = remember(currentTitle, safeSettings.showPlayerTitle, safeSettings.titleLimitType) {
                    if (!safeSettings.showPlayerTitle) {
                        ""
                    } else {
                        when (safeSettings.titleLimitType) {
                            "LIMIT_20" -> if (currentTitle.length > 20) currentTitle.take(20) + "..." else currentTitle
                            "LIMIT_40" -> if (currentTitle.length > 40) currentTitle.take(40) + "..." else currentTitle
                            else -> currentTitle
                        }
                    }
                }

                // Determine which engine type to use based on the CURRENT source URL.
                // This is computed once per source URL — engine only rebuilds if the type changes
                // Collect active engine type from ViewModel (set by settings + updated on fallback).
                // This drives which engine is instantiated below without local ad-hoc state.
                val activeEngineType by viewModel.activeEngineType.collectAsState()

                LaunchedEffect(activeEngineType, currentVideoUrl) {
                    if (activeEngineType == PlayerEngineType.EXTERNAL) {
                        val urlVal = currentVideoUrl
                        if (!urlVal.isNullOrBlank()) {
                            PlayerLogger.logExternalPlayerLaunch(
                                context   = context,
                                url       = urlVal,
                                addonName = currentAddonName,
                                title     = currentTitle,
                                manual    = false
                            )
                            val launched = KitsugiFullscreenPlayerActivity.launchExternalPlayer(
                                context = context,
                                videoUrl = urlVal,
                                title = currentTitle,
                                positionMs = savedPos ?: 0L,
                                headers = currentHeaders,
                                subtitles = currentSubtitles
                            )
                            // v2.4.319: Harici oynatıcı açılamadıysa ekranı KAPATMA.
                            if (launched) {
                                onBack()
                            } else {
                                Toast.makeText(
                                    context,
                                    KitsugiStrings.get(R.string.harici_oynatici_baslatilamadi),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                val playerEngine = remember(context, activeEngineType) {
                    when (activeEngineType) {
                        PlayerEngineType.MPV -> MpvPlayerEngine(context, safeSettings)
                        else -> Media3PlayerEngine(context, safeSettings)
                    }
                }

                LaunchedEffect(playerEngine, safeSettings) {
                    (playerEngine as? MpvPlayerEngine)?.updateSettings(safeSettings)
                }

                // Motor değişirse (Media3 ↔ MPV) çözünürlük tavanı yeni motora yeniden
                // uygulanır; aksi hâlde seçilen kalite sessizce kaybolurdu.
                LaunchedEffect(playerEngine, selectedQuality) {
                    playerEngine.setMaxVideoHeight(
                        selectedQuality?.takeIf { it.switchUrl == null }?.height
                    )
                }

                LaunchedEffect(playerEngine, skipIntervals) { viewModel.updateSkipChapters(skipIntervals) }

                // Seçili motor bir özelliği desteklemiyorsa (altyazı gecikmesi/hızı, renk
                // filtreleri, negatif ses gecikmesi) kontrolü sessizce yutmuyoruz: MPV
                // motoruna geçiyoruz. Konum korunur, yeni motor kaldığı yerden devam eder.
                fun switchToMpvForFeature(feature: String) {
                    if (activeEngineType == PlayerEngineType.MPV) return
                    viewModel.switchEngineForFeature(PlayerEngineType.MPV)
                    Toast.makeText(
                        context,
                        "$feature için MPV motoruna geçildi — kaldığınız yerden devam edecek.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // ─── PiP / bildirim tuşları köprüsü ──────────────────────────────────
                // PiP penceresindeki Oynat/Duraklat/Sonraki tuşları ve Activity durdurma
                // olayları bu köprü üzerinden oynatıcıya iletilir.
                val fullscreenActivity = activity as? KitsugiFullscreenPlayerActivity
                DisposableEffect(playerEngine, fullscreenActivity) {
                    val bridge = object : KitsugiFullscreenPlayerActivity.PipPlayerCallback {
                        override fun onPipPlay() { viewModel.play() }
                        override fun onPipPause() { viewModel.pause() }
                        override fun onPipSkipNext() { playNextEpisode() }
                        override fun onPipSkipPrevious() { viewModel.changeEpisode(true) }
                        override fun onPipSkipForward() { viewModel.seekBy(10000) }
                    }
                    fullscreenActivity?.setPipPlayerCallback(bridge)
                    onDispose { fullscreenActivity?.setPipPlayerCallback(null) }
                }

                // ─── Audio Output Route Detector (T1.3) ─────────────────────────────
                val routeDetector = remember(context) { AudioOutputRouteDetector(context) }
                val activeAudioRoute by routeDetector.observeRouteChanges().collectAsState(initial = AudioRoute.SPEAKER)

                LaunchedEffect(playerEngine, activeAudioRoute, audioDelayMs, safeSettings.audioDelayPerRouteJson) {
                    val json = safeSettings.audioDelayPerRouteJson
                    val routeDelay = AudioOutputRouteDetector.getDelayForRoute(json, activeAudioRoute)
                    playerEngine.setAudioDelay(audioDelayMs + routeDelay)
                    Log.d("KitsugiPlayer", "Audio delay updated: manual=$audioDelayMs, route=$activeAudioRoute, routeDelay=$routeDelay")
                }

                // Motor değişimi (MEDIA3 ↔ MPV) veya panel değişikliğinde altyazı/ses
                // gecikmelerini, altyazı hızını ve renk filtrelerini yeni motora uygula.
                // Eskiden bu değerler yalnızca kullanıcı kaydırıcıyı oynattığında
                // gönderiliyordu; motor değişince kayboluyordu.
                LaunchedEffect(
                    playerEngine,
                    subtitleDelayMs,
                    secondarySubtitleDelayMs,
                    subtitleSpeed,
                    videoFilterValues
                ) {
                    playerEngine.setSubtitleDelay(subtitleDelayMs)
                    playerEngine.setSecondarySubtitleDelay(secondarySubtitleDelayMs)
                    playerEngine.setSubtitleSpeed(subtitleSpeed)
                    playerEngine.setVideoFilters(
                        videoFilterValues.mapKeys { (filter, _) -> filter.mpvProperty }
                    )
                }

                var secondarySubtitleId by remember(playerEngine) { mutableStateOf(-1) }
                var currentPosition by remember { mutableStateOf(0L) }
                var duration by remember { mutableStateOf(0L) }
                // Motor hazır olana kadar "oynatılıyor" varsayımı yanlıştı: ilk karede
                // duraklatma ikonu gösteriliyor ve ilk yükleme sırasında yanlış durum
                // bildirilebiliyordu.
                var isPlayingState by remember { mutableStateOf(false) }
                var isBufferingState by remember { mutableStateOf(false) }
                var isPlaybackEnded by remember { mutableStateOf(false) }

                // PiP RemoteAction ikonlarının (Oynat/Duraklat) ve medya bildiriminin
                // oynatma durumuyla senkron kalması için.
                LaunchedEffect(playerEngine, isPlayingState, currentEpisode, currentTitle, currentPosition, playbackSpeed, streamInfoData) {
                    fullscreenActivity?.updateMediaSession(
                        title = currentTitle.ifBlank { title },
                        episode = currentEpisode,
                        isPlaying = isPlayingState,
                        positionMs = currentPosition,
                        durationMs = duration,
                        hasNext = episodesList.any { (it.episodeNumber ?: 0) > currentEpisode },
                        hasPrevious = currentEpisode > 1,
                        playbackSpeed = playbackSpeed,
                        engine = playerEngine
                    )
                }
                var isEngineReady by remember(resumeIdentity) { mutableStateOf(false) }
                var resumeReadyIdentity by remember { mutableStateOf<com.kitsugi.animelist.core.player.PlayerResumeIdentity?>(null) }
                var lastPreparedResumeIdentity by remember { mutableStateOf<com.kitsugi.animelist.core.player.PlayerResumeIdentity?>(null) }
                val latestPreparedResumeIdentity by rememberUpdatedState(lastPreparedResumeIdentity)
                
                var aspectFeedback by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(playerEngine, currentAspectMode) {
                    playerEngine.setAspectMode(currentAspectMode)
                }

                LaunchedEffect(playerEngine, playbackSpeed) {
                    playerEngine.setPlaybackSpeed(playbackSpeed)
                }

                // ─── Gesture Controller (T2.1 + T2.7) ─────────────────────────────────
                // Legacy rememberPlayerGestureController removed (Centralized GestureHandler used in PlayerControls)

                // ─── PiP Panel and Controls Reset (T2.3) ──────────────────────────────
                LaunchedEffect(isInPipMode) {
                    if (isInPipMode) {
                        viewModel.showPanel(KitsugiPanels.None)
                        viewModel.showSheet(KitsugiSheets.None)
                    }
                }



                LaunchedEffect(aspectFeedback) {
                    if (aspectFeedback != null) {
                        delay(1000)
                        aspectFeedback = null
                    }
                }

                LaunchedEffect(playerEngine, currentVideoUrl) {
                    videoQualityTracks = emptyList()
                    isVideoQualityAuto = true
                }

                // Poll metadata/cache even while paused; save history only during playback.
                LaunchedEffect(playerEngine, mediaIdForHistory, currentEpisode, currentAddonName, resumeIdentity) {
                    run {
                        while (true) {
                            currentPosition = playerEngine.currentPosition
                            duration = playerEngine.duration.coerceAtLeast(0L)
                            isPlayingState = playerEngine.isPlaying
                            viewModel.updateReadAhead(playerEngine.bufferedPosition / 1000)
                            videoQualityTracks = playerEngine.videoQualityTracks
                            isVideoQualityAuto = playerEngine.isVideoQualityAuto
                            if (currentPosition > 0L && duration > 0L && playerEngine.isPlaying) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    resumeStore.save(resumeIdentity, currentPosition, duration)
                                }
                                viewModel.saveProgress(
                                    mediaId = mediaIdForHistory,
                                    episode = currentEpisode,
                                    lastPositionMs = currentPosition,
                                    durationMs = duration,
                                    addonName = currentAddonName
                                )
                                val targetHistoryId = historyAnimeId ?: mediaIdForHistory.toString()
                                com.kitsugi.animelist.data.local.WatchHistoryManager.updateProgress(
                                    animeId = targetHistoryId,
                                    episode = currentEpisode,
                                    positionMs = currentPosition,
                                    durationMs = duration
                                )
                            }
                            viewModel.onPositionChanged(currentPosition, duration, playerEngine.isPlaying)
                            delay(1000)
                        }
                    }
                }

                val nextEpNum = currentEpisode + 1
                val nextEpTitle = episodesList.find { it.episodeNumber == nextEpNum }?.title ?: "Bölüm $nextEpNum"
                val nextEpThumbnail = episodesList.find { it.episodeNumber == nextEpNum }?.thumbnail

                val shouldShowBingeCard = showBingeCard && !isInPipMode

                val isTorrentStream = remember(currentSourceIndex, currentStreamSources, currentVideoUrl) {
                    val currentSource = currentStreamSources.getOrNull(currentSourceIndex)
                    currentSource?.isTorrent == true ||
                        currentVideoUrl?.contains("127.0.0.1") == true ||
                        currentVideoUrl?.contains("localhost") == true
                }

                LaunchedEffect(playerEngine, audioBoostLevel) {
                    playerEngine.setVolume(1.0f + audioBoostLevel)
                }

                LaunchedEffect(playbackSource, isLoading, hasError, isAutoSwitching) {
                    Log.d("KitsugiPlayerDebug", "UI State: playbackSource=${playbackSource != null}, isLoading=$isLoading, hasError=$hasError, isAutoSwitching=$isAutoSwitching")
                }

                // ─── Buffering Watchdog (12s Timeout / 60s for local P2P) ─────────────
                var bufferingWatchdogJob by remember { mutableStateOf<Job?>(null) }
                LaunchedEffect(isBufferingState, activeEngineType, currentVideoUrl) {
                    if (isBufferingState) {
                        bufferingWatchdogJob?.cancel()
                        val isLocalP2p = currentVideoUrl?.let {
                            it.contains("127.0.0.1") || it.contains("localhost")
                        } == true
                        val timeoutMs = if (isLocalP2p) 60_000L else 12_000L
                        bufferingWatchdogJob = scope.launch {
                            delay(timeoutMs)
                            if (isBufferingState) {
                                Log.w("KitsugiPlayerDebug", "Buffering watchdog: Stream stuck buffering for ${timeoutMs / 1000}s. Delegating to errorRecovery.")
                                viewModel.orchestrator.errorRecovery.onPlaybackError(5004, "Arabelleğe alma zaman aşımına uğradı (${timeoutMs / 1000}sn)")
                            }
                        }
                    } else {
                        bufferingWatchdogJob?.cancel()
                        bufferingWatchdogJob = null
                    }
                }

                DisposableEffect(playerEngine) {
                    Log.d("KitsugiPlayerDebug", "playerEngine DisposableEffect initialized")
                    viewModel.setActiveEngine(playerEngine)
                    val listener = object : PlayerEngine.Listener {
                        override fun onPlayingChanged(isPlaying: Boolean) {
                            isPlayingState = isPlaying
                            viewModel.updatePlayerState(playerEngine.currentState, isPlaying)
                        }
                        override fun onPositionChanged(positionMs: Long, durationMs: Long) {
                            currentPosition = positionMs
                            duration = durationMs
                            viewModel.onPositionChanged(positionMs, durationMs, playerEngine.isPlaying)
                        }
                        override fun onBufferedPositionChanged(positionMs: Long) {
                            viewModel.updateReadAhead(positionMs / 1000)
                        }
                        override fun onChaptersChanged(items: List<com.kitsugi.animelist.core.player.PlayerChapter>) {
                            viewModel.setMediaChapters(items)
                        }
                        override fun onSecondarySubtitleChanged(trackId: Int) {
                            secondarySubtitleId = trackId
                        }
                        override fun onStateChanged(state: PlayerEngine.State) {
                            Log.d("KitsugiPlayerDebug", "onStateChanged: state=$state, isPlaying=${playerEngine.isPlaying}")
                            isPlayingState = playerEngine.isPlaying
                            isBufferingState = state == PlayerEngine.State.BUFFERING
                            // TASK_042 — PlaybackState StateFlow güncelle
                            viewModel.updatePlayerState(state, playerEngine.isPlaying)
                            if (state == PlayerEngine.State.READY) {
                                isPlaybackEnded = false
                                duration = playerEngine.duration.coerceAtLeast(0L)
                                Log.d("KitsugiPlayerDebug", "Player READY: duration=$duration, savedPos=$savedPos")
                                isEngineReady = true
                                resumeReadyIdentity = latestResumeIdentity
                                playerEngine.setPlaybackSpeed(playbackSpeed)
                                val mpvEngine = playerEngine as? com.kitsugi.animelist.core.player.engine.MpvPlayerEngine
                                mpvEngine?.mpvView?.mpv?.setPropertyInt("user-data/current-anime/intro-length", viewModel.getAnimeSkipIntroLength())
                                viewModel.orchestrator.errorRecovery.onPlaybackReady()
                                viewModel.prefetchNextEpisode()
                            } else if (state == PlayerEngine.State.ENDED) {
                                Log.d("KitsugiPlayerDebug", "Player ENDED: calling onEpisodeEnded")
                                isPlaybackEnded = true
                                resumeStore.clear(latestResumeIdentity)
                                viewModel.onEpisodeEnded(duration, currentPosition)
                            }
                        }

                        override fun onPlaybackError(errorCode: Int, errorMsg: String, cause: Throwable?) {
                            Log.e("KitsugiPlayerDebug", "onPlaybackError: code=$errorCode, message=$errorMsg", cause)
                            viewModel.setPlayerError(errorCode, errorMsg)
                            PlayerLogger.logPlaybackError(
                                context   = context,
                                url       = currentVideoUrl,
                                addonName = currentAddonName,
                                title     = currentTitle,
                                errorCode = errorCode,
                                errorMsg  = errorMsg,
                                cause     = cause
                             )
                            // Seçilen varyant URL'si oynatılamıyorsa (imzalı CDN token'ı süresi
                            // dolmuş, 403, süresi geçmiş sorgu) kullanıcıyı kilitli bırakma:
                            // master'a bir kez dönüp aynı kaliteyi tavan olarak koru.
                            val failingVariant = selectedQuality
                            val masterUrl = qualityMasterUrl
                            val source = currentStreamSources.getOrNull(currentSourceIndex)
                            if (failingVariant?.switchUrl != null &&
                                !masterUrl.isNullOrBlank() &&
                                source != null &&
                                variantFallbackUrl != failingVariant.switchUrl
                            ) {
                                variantFallbackUrl = failingVariant.switchUrl
                                viewModel.clearPlayerError()
                                playerEngine.setMaxVideoHeight(failingVariant.height)
                                selectedQuality = failingVariant.copy(switchUrl = null)
                                Toast.makeText(
                                    context,
                                    KitsugiStrings.get(R.string.quality_variant_failed_back_to_master),
                                    Toast.LENGTH_LONG
                                ).show()
                                viewModel.changeStreamSource(currentSourceIndex, source, masterUrl)
                                return
                            }
                            // Delegate entirely to the orchestrator's unified error recovery pipeline
                            viewModel.orchestrator.errorRecovery.onPlaybackError(errorCode, errorMsg)
                        }

                        override fun onTracksChanged(
                            audioTracks: List<TrackOption>,
                            subtitleTracks: List<TrackOption>
                        ) {
                            Log.d("KitsugiPlayerDebug", "onTracksChanged: audioTracks=${audioTracks.size}, subTracks=${subtitleTracks.size}")
                            audioTrackOptions = audioTracks
                            textTrackOptions = subtitleTracks
                            videoQualityTracks = playerEngine.videoQualityTracks
                            isVideoQualityAuto = playerEngine.isVideoQualityAuto
                            isSubtitleDisabled = playerEngine.isSubtitleDisabled
                            streamInfoData = playerEngine.activeStreamInfo
                        }

                        override fun onEngineEvent(property: String, value: String) {
                            if (property == "user-data/current-anime/intro-length") {
                                viewModel.setAnimeSkipIntroLength(value.toIntOrNull() ?: 85)
                            } else {
                                viewModel.handleLuaInvocation(property, value)
                            }
                        }
                    }
                    playerEngine.addListener(listener)
                    onDispose {
                        Log.d("KitsugiPlayerDebug", "playerEngine DisposableEffect onDispose called")
                        viewModel.setActiveEngine(null)
                        val finalPos = playerEngine.currentPosition
                        if (finalPos > 0L && duration > 0L) {
                            resumeStore.save(latestPreparedResumeIdentity ?: latestResumeIdentity, finalPos, duration)
                            viewModel.saveProgress(
                                mediaId = mediaIdForHistory,
                                episode = currentEpisode,
                                lastPositionMs = finalPos,
                                durationMs = duration,
                                addonName = currentAddonName
                            )
                            val targetHistoryId = historyAnimeId ?: mediaIdForHistory.toString()
                            com.kitsugi.animelist.data.local.WatchHistoryManager.updateProgress(
                                animeId = targetHistoryId,
                                episode = currentEpisode,
                                positionMs = finalPos,
                                durationMs = duration
                            )
                            viewModel.onPlayerLeaving(finalPos, duration)
                            com.kitsugi.animelist.data.local.WatchHistoryManager.flush()
                        }
                        playerEngine.release()

                        if (activity != null && appSettings?.frameRateMatchingMode == com.kitsugi.animelist.data.settings.FrameRateMatchingMode.START_STOP) {
                            com.kitsugi.animelist.core.player.FrameRateUtils.restoreOriginalDisplayMode(activity)
                        } else {
                            com.kitsugi.animelist.core.player.FrameRateUtils.clearOriginalDisplayMode()
                        }
                    }
                }

                // KEY FIX: Use URL string + headers hash instead of source object reference.
                // PlaybackSource objects are frequently recreated by ViewModel (e.g. on recomposition)
                // even when the URL hasn't changed. Using the object reference as key caused
                // unnecessary player restarts every ~10s, resetting position to 0 each time.
                var lastPreparedUrl by remember { mutableStateOf<String?>(null) }
                // Son hazırlanan akışın kimliği (URL + başlıklar + ayrı ses + motor). Yalnızca
                // altyazı listesi değiştiyse akış yeniden hazırlanmaz (bkz. addExternalSubtitles).
                val lastPreparedStream = remember {
                    java.util.concurrent.atomic.AtomicReference<PreparedStreamIdentity?>(null)
                }
                val activePlaybackStream = currentStreamSources.getOrNull(currentSourceIndex)
                val prepareKey = remember(
                    source?.videoUrl, currentHeaders, currentSubtitles,
                    activePlaybackStream?.csStreamType, activePlaybackStream?.csExtractorData
                ) {
                    listOf(
                        source?.videoUrl, currentHeaders.hashCode(), currentSubtitles.map { it.url }.hashCode(),
                        activePlaybackStream?.csStreamType, activePlaybackStream?.csExtractorData
                    )
                }
                LaunchedEffect(playerEngine, prepareKey) {
                    // EXTERNAL tercihinde Activity kapanmadan dahili motoru hazırlamaya
                    // çalışmak kısa süreliğine ikinci bir oynatma başlatabiliyordu.
                    if (activeEngineType == PlayerEngineType.EXTERNAL) return@LaunchedEffect
                    val safeVideoUrl = source?.videoUrl ?: return@LaunchedEffect
                    val streamIdentity = PreparedStreamIdentity(
                        videoUrl = safeVideoUrl,
                        headersHash = currentHeaders.hashCode(),
                        audioUrl = source?.audioUrl,
                        engine = playerEngine
                    )
                    // Eklenti altyazıları video başladıktan 1–9 sn sonra gelince eskiden TÜM oynatıcı
                    // yeniden hazırlanıyordu (v2.4.369 günlüğündeki çift "[OYNAT] Başladı", aynı CDN
                    // adresine ikinci istek → HTTP 429 riski, yeniden tamponlama). Destekleyen motor
                    // (mpv) yeni altyazıları akışı kesmeden ekler; desteklemeyen motor tam hazırlığa düşer.
                    if (streamIdentity == lastPreparedStream.get() &&
                        playerEngine.addExternalSubtitles(currentSubtitles)
                    ) {
                        Log.d("KitsugiPlayer", "Yalnızca altyazı listesi değişti (${currentSubtitles.size}) → akış yeniden hazırlanmadı")
                        return@LaunchedEffect
                    }
                    isPlaybackEnded = false
                    isEngineReady = false
                    resumeReadyIdentity = null
                    Log.d("KitsugiPlayerDebug", "LaunchedEffect(source) prepare() starting. url=$safeVideoUrl")
                    PlayerLogger.logPlaybackStart(
                        context   = context,
                        url       = safeVideoUrl,
                        addonName = currentAddonName,
                        title     = currentTitle,
                        isCS      = currentStreamSources.getOrNull(currentSourceIndex)?.isCS == true
                    )
                    
                    playerEngine.setPlaybackSpeed(playbackSpeed)

                    val activeSource = currentStreamSources.getOrNull(currentSourceIndex)
                    // Yedek motora geçişte hata veren motorun konumundan devam et (yeni motor 0'dan başlamasın).
                    val switchedResumeMs = viewModel.takePendingResumeAfterEngineSwitch()
                    val startPos = if (safeVideoUrl == lastPreparedUrl) {
                        switchedResumeMs ?: playerEngine.currentPosition
                    } else {
                        0L
                    }
                    if (lastPreparedResumeIdentity != null && resumeIdentity != lastPreparedResumeIdentity) {
                        lastPreparedResumeIdentity?.let { previousIdentity ->
                            resumeStore.save(previousIdentity, playerEngine.currentPosition, playerEngine.duration)
                        }
                    }
                    lastPreparedUrl = safeVideoUrl
                    lastPreparedResumeIdentity = resumeIdentity
                    lastPreparedStream.set(streamIdentity)
                    viewModel.onHistoryEnginePrepared(currentEpisode)

                    playerEngine.prepare(
                        videoUrl = safeVideoUrl,
                        audioUrl = source?.audioUrl,
                        externalAudioTracks = source?.externalAudioTracks.orEmpty(),
                        streamTypeHint = source?.streamTypeHint,
                        headers = currentHeaders,
                        subtitles = currentSubtitles,
                        startPositionMs = startPos,
                        addonName = currentAddonName,
                        // Eklenti başlığı HTML/CSS artığı içerebiliyor; ses/altyazı
                        // parçası adına temizlenmiş metin verilir (2026-11-01).
                        streamTitle = com.kitsugi.animelist.utils.StreamLabelSanitizer
                            .sanitize(activeSource?.title)
                            .ifBlank {
                                com.kitsugi.animelist.utils.StreamLabelSanitizer
                                    .sanitize(activeSource?.name)
                            }
                            .takeIf { it.isNotBlank() },
                        qualityValue = activeSource?.qualityValue,
                        csStreamType = activeSource?.csStreamType,
                        csExtractorData = activeSource?.csExtractorData
                    )
                    Log.d("KitsugiPlayer", "prepare() ÇAĞRILDI: url=$safeVideoUrl addon=${currentAddonName} startPos=$startPos")
                }

                LaunchedEffect(savedPos, isEngineReady, resumeReadyIdentity, resumeIdentity, isLoading, nextEpisodeLoading) {
                    val pos = savedPos
                    if (pos != null && isEngineReady && resumeReadyIdentity == resumeIdentity &&
                        !isLoading && !nextEpisodeLoading && !hasCheckedResume) {
                        hasCheckedResume = true
                        if (pos >= 10_000L && pos < duration - 10_000L) {
                            playerEngine.pause()
                            pendingResumePos = pos
                            showResumeDialog = true
                        }
                    }
                }

                var seekFeedback  by remember { mutableStateOf<String?>(null) }
                // true = kullanıcı sağ tarafa tıkladı → gösterge sola (TopStart) gider
                var seekFeedbackOnRightSide by remember { mutableStateOf(true) }
                var playPauseIcon by remember { mutableStateOf<ImageVector?>(null) }

                val controlsShown by viewModel.controlsShown.collectAsState()

                // ─── Klavye / gamepad kısayolları (v2.4.341) ───────────────────────
                // Kullanıcı isteği: "klavye ile bir şeyleri yönetirken sürekli oynatıcı
                // arayüzüm gelip gitmesin; sadece tetiklediğim tuşun özelliği görünüp yok olsun."
                //
                // Bu yüzden tuş basımları artık `viewModel.showControls()` ÇAĞIRMIYOR.
                // Yalnızca `keyboardController.indicator` (küçük OSD) güncelleniyor.
                val keyboardEngineState = rememberUpdatedState(playerEngine)
                val latestSettingsForKeys = rememberUpdatedState(safeSettings)
                val keyboardTable = remember(safeSettings.customBindings, safeSettings.disabledShortcuts) {
                    com.kitsugi.animelist.core.player.input.PlayerShortcutSettings.buildTable(
                        disabledRaw = safeSettings.disabledShortcuts,
                        overridesRaw = safeSettings.customBindings,
                    )
                }
                val keyboardRouter = remember {
                    com.kitsugi.animelist.core.player.input.PlayerKeyRouter(
                        bindingsProvider = { keyboardTable }
                    )
                }
                val keyboardController = remember(keyboardRouter) {
                    com.kitsugi.animelist.ui.screens.fullscreen.controls.PlayerKeyboardController(
                        router = keyboardRouter,
                        callbacksProvider = {
                            val engine = keyboardEngineState.value
                            val cfg = latestSettingsForKeys.value
                            com.kitsugi.animelist.ui.screens.fullscreen.controls.PlayerKeyboardCallbacks(
                                keyboardEnabled = cfg.keyboardEnabled,
                                showIndicators = cfg.keyboardOsdEnabled,
                                indicatorDurationScale = cfg.keyboardOsdDurationScale,
                                autoDetectExternalKeyboard = cfg.autoDetectExternalKeyboard,
                                gamepadEnabled = cfg.gamepadEnabled,
                                opensFullOsd = cfg.keyboardOpensFullOsd,
                                onRefreshFullOsd = { viewModel.showControls() },
                                holdToAccelerate = cfg.keyboardHoldToAccelerate,
                                seekStepMs = cfg.keyboardSeekStepSeconds * 1000L,
                                largeSeekStepMs = cfg.keyboardLargeSeekSeconds * 1000L,
                                preciseSeekStepMs = cfg.keyboardPreciseSeekSeconds * 1000L,
                                positionMs = { engine.currentPosition },
                                durationMs = { engine.duration.coerceAtLeast(0L) },
                                onSeekBy = { delta ->
                                    val pos = engine.currentPosition
                                    val dur = engine.duration.coerceAtLeast(0L)
                                    engine.seekTo((pos + delta).coerceIn(0L, if (dur > 0L) dur else Long.MAX_VALUE))
                                },
                                onSeekTo = { target -> engine.seekTo(target.coerceAtLeast(0L)) },
                                isPlaying = { engine.isPlaying },
                                onPlayPause = { viewModel.togglePlay() },
                                maxVolume = { viewModel.maxVolume },
                                volume = { viewModel.currentVolume.value },
                                mpvVolume = { viewModel.currentMPVVolume.value },
                                onVolumeChange = { viewModel.changeVolumeTo(it) },
                                onVolumeBoostChange = { viewModel.changeMPVVolumeTo(it) },
                                volumeBoostSupported = { latestSettingsForKeys.value.volumeBoostCap > 100 },
                                speed = { viewModel.playbackSpeed.value },
                                onSpeedChange = { viewModel.setPlaybackSpeed(it, persist = false) },
                                onCycleSubtitle = {
                                    when {
                                        textTrackOptions.isEmpty() -> "Altyazı parçası yok"
                                        isSubtitleDisabled -> {
                                            textTrackOptions.firstOrNull()?.let { engine.selectTrack(it) }
                                            isSubtitleDisabled = false
                                            "Altyazı açıldı"
                                        }
                                        else -> {
                                            val next = textTrackOptions.firstOrNull { !it.isSelected }
                                            if (next == null) {
                                                engine.disableSubtitles()
                                                isSubtitleDisabled = true
                                                "Altyazı kapalı"
                                            } else {
                                                engine.selectTrack(next)
                                                "Altyazı: ${next.label}"
                                            }
                                        }
                                    }
                                },
                                onToggleSubtitleVisibility = {
                                    if (isSubtitleDisabled) {
                                        textTrackOptions.firstOrNull()?.let { engine.selectTrack(it) }
                                        isSubtitleDisabled = false
                                        true
                                    } else {
                                        engine.disableSubtitles()
                                        isSubtitleDisabled = true
                                        false
                                    }
                                },
                                subtitleDelaySupported = { engine.supportsSubtitleDelay },
                                onSubtitleDelayChange = { delta ->
                                    val next = (subtitleDelayMs + delta).coerceIn(-30_000L, 30_000L)
                                    subtitleDelayMs = next
                                    engine.setSubtitleDelay(next)
                                    next
                                },
                                onCycleAudio = {
                                    if (audioTrackOptions.isEmpty()) {
                                        "Ses parçası yok"
                                    } else {
                                        val target = audioTrackOptions.firstOrNull { !it.isSelected }
                                            ?: audioTrackOptions.first()
                                        engine.selectTrack(target)
                                        "Ses: ${target.label}"
                                    }
                                },
                                onNextEpisode = {
                                    val hasNext = episodesList.any { it.episodeNumber == currentEpisode + 1 } ||
                                        currentEpisode < (episodesList.lastOrNull()?.episodeNumber ?: Int.MAX_VALUE)
                                    if (hasNext) { playNextEpisode(); true } else false
                                },
                                onPreviousEpisode = {
                                    if (currentEpisode > 1) { playEpisode(currentEpisode - 1); true } else false
                                },
                                onChapterJump = { forward ->
                                    val list = chapters
                                    if (list.isEmpty()) {
                                        null
                                    } else {
                                        val posSec = engine.currentPosition / 1000f
                                        val target = if (forward) list.firstOrNull { it.start > posSec + 0.5f }
                                        else list.lastOrNull { it.start < posSec - 0.5f }
                                        if (target == null) null else {
                                            engine.seekTo((target.start * 1000f).toLong())
                                            target.name
                                        }
                                    }
                                },
                                onCycleAspect = {
                                    val modes = com.kitsugi.animelist.core.player.PlayerAspectMode.entries
                                    val nextMode = modes[(currentAspectMode.ordinal + 1) % modes.size]
                                    currentAspectMode = nextMode
                                    when (nextMode) {
                                        com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL -> "Orijinal"
                                        com.kitsugi.animelist.core.player.PlayerAspectMode.FIT -> "Sığdır"
                                        com.kitsugi.animelist.core.player.PlayerAspectMode.FILL -> "Doldur"
                                        com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_16_9 -> "16:9"
                                        com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_4_3 -> "4:3"
                                        com.kitsugi.animelist.core.player.PlayerAspectMode.ZOOM -> "Yakınlaştır (Kırp)"
                                    }
                                },
                                onCycleOrientation = {
                                    val next = when (screenOrientationState) {
                                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                    }
                                    screenOrientationState = next
                                    activity?.requestedOrientation = next
                                    when (next) {
                                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> "Yönlendirme: Otomatik"
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "Yönlendirme: Yatay (Kilitli)"
                                        else -> "Yönlendirme: Dikey (Kilitli)"
                                    }
                                },
                                onToggleStatistics = {
                                    if (engine !is com.kitsugi.animelist.core.player.engine.MpvPlayerEngine) {
                                        null
                                    } else {
                                        val next = if (viewModel.statisticsPage.value == 0) 1 else 0
                                        viewModel.updateStatisticsPage(next)
                                        if (next == 0) "İstatistikler kapalı" else "İstatistikler açık"
                                    }
                                },
                                onScreenshot = {
                                    scope.launch {
                                        val stream = viewModel.takeScreenshot(
                                            context.cacheDir.absolutePath,
                                            latestSettingsForKeys.value.screenshotSubtitles
                                        )
                                        if (stream != null) {
                                            viewModel.saveImage({ stream }, engine.currentPosition.toInt())
                                        }
                                    }
                                },
                                onOpenSettings = {
                                    viewModel.showPanel(KitsugiPanels.None)
                                    viewModel.showSheet(KitsugiSheets.More)
                                },
                                onToggleUi = { viewModel.toggleControls() },
                                onToggleLock = {
                                    if (viewModel.areControlsLocked.value) {
                                        viewModel.unlockControls(); false
                                    } else {
                                        viewModel.lockControls(); true
                                    }
                                },
                            )
                        }
                    )
                }
                val keyboardIndicator = keyboardController.indicator
                val shortcutsEnabled = safeSettings.keyboardEnabled

                // Gösterge kendi kendine kaybolur — tam OSD'nin kapatma zamanlayıcısına dokunmaz.
                LaunchedEffect(keyboardIndicator?.seq) {
                    if (keyboardIndicator == null) return@LaunchedEffect
                    delay(keyboardController.indicatorDwellMs())
                    if (keyboardController.indicator?.seq == keyboardIndicator.seq) {
                        keyboardController.clearIndicator()
                    }
                }

                LaunchedEffect(controlsShown) {
                    if (!controlsShown && isFocusTargetAttached) {
                        try {
                            mainFocusRequester.requestFocus()
                        } catch (e: Exception) {
                            Log.e("KitsugiPlayer", "Failed to request focus on controls hide", e)
                        }
                    }
                }

                LaunchedEffect(isPlayingState) {
                    if (!isPlayingState) viewModel.showControls()
                }

                LaunchedEffect(seekFeedback) {
                    if (seekFeedback != null) { delay(650); seekFeedback = null }
                }
                LaunchedEffect(playPauseIcon) {
                    if (playPauseIcon != null) { delay(500); playPauseIcon = null }
                }
                fun cycleAspectMode() {
                    val modes = com.kitsugi.animelist.core.player.PlayerAspectMode.entries
                    val nextMode = modes[(currentAspectMode.ordinal + 1) % modes.size]
                    currentAspectMode = nextMode
                    aspectFeedback = when (nextMode) {
                        com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL -> "Orijinal"
                        com.kitsugi.animelist.core.player.PlayerAspectMode.FIT -> "Sığdır"
                        com.kitsugi.animelist.core.player.PlayerAspectMode.FILL -> "Doldur"
                        com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_16_9 -> "16:9"
                        com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_4_3 -> "4:3"
                        com.kitsugi.animelist.core.player.PlayerAspectMode.ZOOM -> "Yakınlaştır (Kırp)"
                    }
                }

                fun cycleScreenOrientation() {
                    val nextOrientation = when (screenOrientationState) {
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    }
                    screenOrientationState = nextOrientation
                    activity?.requestedOrientation = nextOrientation
                    aspectFeedback = when (nextOrientation) {
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> KitsugiStrings.get(R.string.yonlendirme_otomatik)
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "Yönlendirme: Yatay (Kilitli)"
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> "Yönlendirme: Dikey (Kilitli)"
                        else -> ""
                    }
                }

                fun executePlayerKeyAction(action: PlayerKeyAction): Boolean {
                    when (action) {
                        PlayerKeyAction.TogglePlayPause -> {
                            viewModel.togglePlay()
                            viewModel.showControls()
                        }
                        PlayerKeyAction.Play -> viewModel.play()
                        PlayerKeyAction.Pause -> {
                            viewModel.pause()
                            viewModel.showControls()
                        }
                        PlayerKeyAction.SeekBackward, PlayerKeyAction.SeekBackwardLarge -> {
                            val amount = if (action == PlayerKeyAction.SeekBackwardLarge) 60 else 10
                            viewModel.seekBy(-amount * 1000L)
                            seekFeedback = "-${amount}s"
                            seekFeedbackOnRightSide = false
                            viewModel.showControls()
                        }
                        PlayerKeyAction.SeekForward, PlayerKeyAction.SeekForwardLarge -> {
                            val amount = if (action == PlayerKeyAction.SeekForwardLarge) 60 else 10
                            viewModel.seekBy(amount * 1000L)
                            seekFeedback = "+${amount}s"
                            seekFeedbackOnRightSide = true
                            viewModel.showControls()
                        }
                        PlayerKeyAction.PreviousEpisode -> viewModel.changeEpisode(previous = true)
                        PlayerKeyAction.NextEpisode -> playNextEpisode()
                        PlayerKeyAction.VolumeDown -> viewModel.adjustVolumeBy(-1)
                        PlayerKeyAction.VolumeUp -> viewModel.adjustVolumeBy(1)
                        PlayerKeyAction.BrightnessDown -> viewModel.adjustBrightnessBy(-0.05f)
                        PlayerKeyAction.BrightnessUp -> viewModel.adjustBrightnessBy(0.05f)
                        PlayerKeyAction.ToggleMute -> viewModel.toggleMute()
                        PlayerKeyAction.SpeedDown, PlayerKeyAction.SpeedUp -> {
                            val delta = if (action == PlayerKeyAction.SpeedDown) -0.25f else 0.25f
                            val newSpeed = (playbackSpeed + delta).coerceIn(0.25f, 4f)
                            viewModel.setPlaybackSpeed(newSpeed, persist = false)
                            aspectFeedback = "Hız: ${"%.2fx".format(newSpeed)}"
                        }
                        PlayerKeyAction.SpeedReset -> {
                            viewModel.setPlaybackSpeed(1f, persist = false)
                            aspectFeedback = "Hız: 1,00x"
                        }
                        PlayerKeyAction.PlaybackSpeed -> viewModel.showSheet(KitsugiSheets.PlaybackSpeed)
                        PlayerKeyAction.SubtitleTracks -> viewModel.showSheet(KitsugiSheets.SubtitleTracks)
                        PlayerKeyAction.ToggleSubtitles -> {
                            val shouldEnable = isSubtitleDisabled || textTrackOptions.none { it.isSelected }
                            if (shouldEnable) {
                                val option = textTrackOptions.firstOrNull { it.isSelected } ?: textTrackOptions.firstOrNull()
                                if (option != null) {
                                    playerEngine.selectTrack(option)
                                    isSubtitleDisabled = false
                                    Toast.makeText(context, "Altyazı açıldı: ${option.label}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Bu videoda altyazı parçası yok.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                playerEngine.disableSubtitles()
                                isSubtitleDisabled = true
                                Toast.makeText(context, "Altyazı kapatıldı", Toast.LENGTH_SHORT).show()
                            }
                        }
                        PlayerKeyAction.AudioTracks -> viewModel.showSheet(KitsugiSheets.AudioTracks)
                        PlayerKeyAction.CycleAudioTrack -> {
                            if (audioTrackOptions.isEmpty()) {
                                Toast.makeText(context, "Bu videoda başka ses parçası yok.", Toast.LENGTH_SHORT).show()
                            } else {
                                val selected = audioTrackOptions.indexOfFirst { it.isSelected }
                                val next = (selected + 1 + audioTrackOptions.size) % audioTrackOptions.size
                                val option = audioTrackOptions[next]
                                playerEngine.selectTrack(option)
                                Toast.makeText(context, "Ses: ${option.label}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        PlayerKeyAction.QualityTracks -> viewModel.showSheet(KitsugiSheets.QualityTracks)
                        PlayerKeyAction.Chapters -> {
                            if (chapters.isEmpty()) {
                                Toast.makeText(context, "Bu videoda bölüm/chapter bilgisi yok.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.showSheet(KitsugiSheets.Chapters)
                            }
                        }
                        PlayerKeyAction.Screenshot -> viewModel.showSheet(KitsugiSheets.Screenshot)
                        PlayerKeyAction.VideoFilters -> viewModel.showPanel(KitsugiPanels.VideoFilters)
                        PlayerKeyAction.More -> viewModel.showSheet(KitsugiSheets.More)
                        PlayerKeyAction.EpisodeList -> viewModel.showDialog(KitsugiDialogs.EpisodeList)
                        PlayerKeyAction.CycleAspectRatio -> cycleAspectMode()
                        PlayerKeyAction.RotateScreen -> cycleScreenOrientation()
                        PlayerKeyAction.ToggleControlsLock -> {
                            if (areControlsLocked) viewModel.unlockControls() else viewModel.lockControls()
                            viewModel.showControls()
                        }
                        PlayerKeyAction.ToggleControls -> {
                            if (controlsShown) viewModel.hideControls() else viewModel.showControls()
                        }
                        PlayerKeyAction.PictureInPicture -> {
                            if (!safeSettings.pipEnabled || activity == null) {
                                Toast.makeText(context, "Resim içinde resim ayarlardan kapalı veya bu cihazda kullanılamıyor.", Toast.LENGTH_SHORT).show()
                            } else {
                                com.kitsugi.animelist.core.player.PlayerPipHelper.enterPipSafe(
                                    activity = activity,
                                    playerEngine = playerEngine,
                                    isPlaying = isPlayingState,
                                    hasNext = episodesList.any { (it.episodeNumber ?: 0) > currentEpisode },
                                )
                            }
                        }
                        PlayerKeyAction.ShowKeyboardShortcuts -> viewModel.showDialog(KitsugiDialogs.KeyboardShortcuts)
                    }
                    return true
                }


                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(mainFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Back || event.key == Key.Escape) {
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                                return@onPreviewKeyEvent when {
                                    panelShown != KitsugiPanels.None || sheetShown != KitsugiSheets.None || dialogShown != KitsugiDialogs.None -> {
                                        viewModel.showPanel(KitsugiPanels.None)
                                        viewModel.showSheet(KitsugiSheets.None)
                                        viewModel.showDialog(KitsugiDialogs.None)
                                        mainFocusRequester.requestFocus()
                                        true
                                    }
                                    controlsShown -> {
                                        viewModel.hideControls()
                                        mainFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> {
                                        onBack()
                                        true
                                    }
                                }
                            }

                            if (panelShown != KitsugiPanels.None || sheetShown != KitsugiSheets.None || dialogShown != KitsugiDialogs.None) return@onPreviewKeyEvent false

                            val nativeEvent = event.nativeKeyEvent
                            val action = PlayerKeyboardShortcuts.actionFor(nativeEvent)
                            val isOsdNavigationKey = when (nativeEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                android.view.KeyEvent.KEYCODE_ENTER,
                                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                                android.view.KeyEvent.KEYCODE_DPAD_UP,
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> true
                                else -> false
                            }
                            val isExplicitBrightnessShortcut = nativeEvent.isCtrlPressed &&
                                (nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN)

                            // OSD açıkken D-pad/Enter odak gezintisinin ve buton aktivasyonunun
                            // önüne geçme. Ctrl+Yukarı/Aşağı ise bilinçli bir global kısayoldur.
                            if (controlsShown && isOsdNavigationKey && !isExplicitBrightnessShortcut) {
                                if (event.type == KeyEventType.KeyDown) viewModel.showControls()
                                return@onPreviewKeyEvent false
                            }

                            if (action == null) {
                                if (controlsShown) {
                                    if (event.type == KeyEventType.KeyDown) viewModel.showControls()
                                    return@onPreviewKeyEvent false
                                }
                                // Eşlenmemiş tuşlar MPV input.conf özel kısayollarına gider.
                                return@onPreviewKeyEvent playerEngine.handleKey(nativeEvent)
                            }

                            // KeyDown'da yapılan eylemin KeyUp'ta MPV'ye ikinci kez gitmesini engelle.
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                            if (nativeEvent.repeatCount > 0 && !PlayerKeyboardShortcuts.isRepeatable(action)) {
                                return@onPreviewKeyEvent true
                            }
                            return@onPreviewKeyEvent executePlayerKeyAction(action)
                        }
                        .pointerInput(playerEngine, safeSettings, isInPipMode) {
                            if (isInPipMode || !safeSettings.gestureZoomEnabled) return@pointerInput
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom > 1.10f && currentAspectMode != com.kitsugi.animelist.core.player.PlayerAspectMode.ZOOM) {
                                    currentAspectMode = com.kitsugi.animelist.core.player.PlayerAspectMode.ZOOM
                                    aspectFeedback = "Yakınlaştır (Kırp)"
                                    viewModel.showControls()
                                } else if (zoom < 0.90f && currentAspectMode != com.kitsugi.animelist.core.player.PlayerAspectMode.FIT) {
                                    currentAspectMode = com.kitsugi.animelist.core.player.PlayerAspectMode.FIT
                                    aspectFeedback = "Sığdır"
                                    viewModel.showControls()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Video View
                    //
                    // `key(playerEngine)`: motor değiştiğinde (ör. Media3 → MPV özellik
                    // geçişi) AndroidView'ın `factory`'si yeniden çalışmalı. Aksi hâlde
                    // aynı düğüm eski, serbest bırakılmış motorun görünümünü tutuyor;
                    // yeni motorun yüzeyi hiç oluşmadığı için ses çalıp görüntü gelmiyordu.
                    key(playerEngine) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                playerEngine.createVideoView(ctx)
                            },
                            update = { _ ->
                                playerEngine.setAspectMode(currentAspectMode)
                                playerEngine.setSubtitleStyle(subtitleStyle)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    BrightnessOverlay(
                        brightness = currentBrightness,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Legacy gesture progress bars and overlays removed (GestureHandler & PlayerControls manage overlays)

                    val openExternalPlayerAction: (() -> Unit)? = if (!currentVideoUrl.isNullOrBlank()) {
                        {
                            val urlVal = currentVideoUrl
                            if (!urlVal.isNullOrBlank()) {
                                val resumeMs = playerEngine.currentPosition.takeIf { it > 0L }
                                    ?: currentPosition.takeIf { it > 0L }
                                    ?: (savedPos ?: 0L)
                                playerEngine.pause()
                                if (resumeMs > 0L && duration > 0L) {
                                    viewModel.saveProgress(
                                        mediaId = mediaIdForHistory,
                                        episode = currentEpisode,
                                        lastPositionMs = resumeMs,
                                        durationMs = duration,
                                        addonName = currentAddonName
                                    )
                                    val targetHistoryId = historyAnimeId ?: mediaIdForHistory.toString()
                                    com.kitsugi.animelist.data.local.WatchHistoryManager.updateProgress(
                                        animeId = targetHistoryId,
                                        episode = currentEpisode,
                                        positionMs = resumeMs,
                                        durationMs = duration
                                    )
                                    com.kitsugi.animelist.data.local.WatchHistoryManager.flush()
                                }
                                PlayerLogger.logExternalPlayerLaunch(
                                    context   = context,
                                    url       = urlVal,
                                    addonName = currentAddonName,
                                    title     = currentTitle,
                                    manual    = true
                                )
                                KitsugiFullscreenPlayerActivity.launchExternalPlayer(
                                    context          = context,
                                    videoUrl         = urlVal,
                                    title            = currentTitle.ifBlank { title },
                                    positionMs       = resumeMs,
                                    headers          = currentHeaders,
                                    subtitles        = currentSubtitles,
                                    preferredPackage = safeSettings.preferredExternalPlayerPackage.takeIf { it.isNotBlank() }
                                )
                            }
                        }
                    } else null

                    var lastPipToastEpisode by remember { mutableStateOf(currentEpisode) }
                    LaunchedEffect(currentEpisode, isInPipMode, safeSettings.pipEpisodeToasts) {
                        if (currentEpisode != lastPipToastEpisode) {
                            lastPipToastEpisode = currentEpisode
                            if (isInPipMode && safeSettings.pipEpisodeToasts) {
                                Toast.makeText(
                                    context,
                                    KitsugiStrings.get(R.string.inline_episode_1_s_dc662be, currentEpisode),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    // Centralized OSD controls (Aniyomi style)
                    if (!isInPipMode) {
                        PlayerControls(
                            viewModel = viewModel,
                            onBackClick = onBack,
                            onRotateClick = {
                                val nextOrientation = when (screenOrientationState) {
                                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                }
                                screenOrientationState = nextOrientation
                                activity?.requestedOrientation = nextOrientation
                                val text = when (nextOrientation) {
                                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> KitsugiStrings.get(R.string.yonlendirme_otomatik)
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "Yönlendirme: Yatay (Kilitli)"
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> "Yönlendirme: Dikey (Kilitli)"
                                    else -> ""
                                }
                                aspectFeedback = text
                            },
                            onAspectClick = {
                                val modes = com.kitsugi.animelist.core.player.PlayerAspectMode.values()
                                val nextMode = modes[(currentAspectMode.ordinal + 1) % modes.size]
                                currentAspectMode = nextMode
                                val text = when (nextMode) {
                                    com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL -> "Orijinal"
                                    com.kitsugi.animelist.core.player.PlayerAspectMode.FIT -> "Sığdır"
                                    com.kitsugi.animelist.core.player.PlayerAspectMode.FILL -> "Doldur"
                                    com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_16_9 -> "16:9"
                                    com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_4_3 -> "4:3"
                                    com.kitsugi.animelist.core.player.PlayerAspectMode.ZOOM -> "Yakınlaştır (Kırp)"
                                }
                                aspectFeedback = text
                            },
                            onOpenExternalPlayer = openExternalPlayerAction,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // AniSkip Auto-Skip logic (immediate mode; Netflix countdown mode is handled in PlayerControls)
                    run {
                        val positionSec = currentPosition / 1000L
                        val activeSkip = skipIntervals.firstOrNull { interval ->
                            positionSec >= interval.startTime.toLong() &&
                            positionSec < interval.endTime.toLong()
                        }
                        if (activeSkip != null) {
                            LaunchedEffect(activeSkip, aniSkipAutoSkip, safeSettings.enableNetflixStyleIntroSkip) {
                                if (aniSkipAutoSkip && !safeSettings.enableNetflixStyleIntroSkip) {
                                    playerEngine.seekTo((activeSkip.endTime * 1000).toLong())
                                }
                            }
                        }
                    }

                    val bubbleTopPadding = if (controlsShown) 72.dp else 28.dp

                    // Double Tap Seek feedback (sol/sağ — hangi tarafa dokunulduysa o taraf)
                    FeedbackBubble(
                        text = seekFeedback,
                        icon = if (seekFeedback?.startsWith("+") == true) Icons.FastForward else Icons.FastRewind,
                        modifier = Modifier
                            .align(
                                if (seekFeedbackOnRightSide) Alignment.TopStart
                                else                         Alignment.TopEnd
                            )
                            .padding(
                                top   = bubbleTopPadding,
                                start = if (seekFeedbackOnRightSide) 24.dp else 0.dp,
                                end   = if (seekFeedbackOnRightSide) 0.dp  else 24.dp
                            )
                    )

                    // Aspect ratio feedback overlay
                    FeedbackBubble(
                        text = aspectFeedback,
                        icon = Icons.AspectRatio,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = bubbleTopPadding + if (seekFeedback != null) 52.dp else 0.dp)
                    )

                    // ─── Klavye kısayolu göstergesi (küçük, kısa ömürlü OSD) ─────────
                    if (!isInPipMode) {
                        val controlsReserveDp = if (controlsShown) 124.dp else 24.dp
                        val subtitlePos = subtitleStyle.pos.coerceIn(0, 150)
                        val subtitleFraction = ((150 - subtitlePos) / 150f * 0.78f + 0.08f)
                        val subtitleBlockDp = if (subtitleStyle.size > 0) 44.dp else 0.dp
                        val bottomPadDp = controlsReserveDp +
                            (220f * subtitleFraction).dp.coerceAtLeast(subtitleBlockDp)

                        KeyboardActionIndicator(
                            message = keyboardIndicator,
                            imageVector = com.kitsugi.animelist.ui.screens.fullscreen.controls
                                .keyboardActionIcon(keyboardIndicator?.action),
                            reduceMotion = safeSettings.reducePlayerMotion,
                            accentColor = LocalKitsugiAccent.current,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = bottomPadDp)
                        )
                    }

                    // ─── Klavye kısayolları yardım ekranı (mpv `?` / VLC yardımı) ──────
                    if (keyboardController.helpVisible && safeSettings.keyboardHelpEnabled && !isInPipMode) {
                        KeyboardShortcutsHelpOverlay(
                            visible = true,
                            effectiveBindings = keyboardTable,
                            disabledActions = com.kitsugi.animelist.core.player.input.PlayerShortcutSettings
                                .parseDisabled(safeSettings.disabledShortcuts),
                            onDismiss = { keyboardController.dismissHelp() },
                            accentColor = LocalKitsugiAccent.current,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // ─── Çıkış onayı (yanlış tuşla oturum kaybını önler) ───────────────
                    if (keyboardController.exitConfirmVisible && !isInPipMode) {
                        AlertDialog(
                            onDismissRequest = { keyboardController.dismissExitConfirm() },
                            containerColor = KitsugiColors.Surface,
                            titleContentColor = KitsugiColors.TextPrimary,
                            textContentColor = KitsugiColors.TextSecondary,
                            shape = RoundedCornerShape(26.dp),
                            title = {
                                Text(
                                    text = "Oynatıcıdan çıkılsın mı?",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Text(
                                    text = "Klavyeden çıkış tuşuna bastınız (Esc / Q / gamepad B). " +
                                        "İzlemeye devam etmek için İptal'i seçin.\n\n" +
                                        "İpucu: tuşlarla yalnızca kısayol kullanmak istiyorsanız Esc, " +
                                        "önce arayüzü kapatır; ikinci basışta bu onay çıkar.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    keyboardController.dismissExitConfirm()
                                    onBack()
                                }) {
                                    Text("Çık", color = LocalKitsugiAccent.current, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { keyboardController.dismissExitConfirm() }) {
                                    Text("İptal", color = KitsugiColors.TextSecondary)
                                }
                            }
                        )
                    }

                    val hasNextEpisode = episodesList.isNotEmpty() && (episodesList.any { it.episodeNumber == nextEpNum } || currentEpisode < (episodesList.lastOrNull()?.episodeNumber ?: Int.MAX_VALUE))
                    PlaybackEndedOverlay(
                        visible = isPlaybackEnded && !isAutoplayEnabled && !isInPipMode,
                        hasNextEpisode = hasNextEpisode,
                        onReplay = {
                            isPlaybackEnded = false
                            playerEngine.seekTo(0L)
                            playerEngine.play()
                        },
                        onPlayNext = {
                            isPlaybackEnded = false
                            playNextEpisode()
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // ── "Hâlâ izliyor musunuz?" uyarısı ────────────────────────────────
                    // StillWatchingController'ın prompt'u daha önce hiçbir UI'a bağlı
                    // değildi; oturum limiti ve hareketsizlik uyarısı görünmüyordu.
                    if (viewModel.showStillWatchingPrompt && !isInPipMode) {
                        val remaining = viewModel.stillWatchingCountdownSec ?: 0
                        AlertDialog(
                            onDismissRequest = { viewModel.onStillWatchingDismissed() },
                            containerColor = KitsugiColors.Surface,
                            titleContentColor = KitsugiColors.TextPrimary,
                            textContentColor = KitsugiColors.TextSecondary,
                            shape = RoundedCornerShape(26.dp),
                            title = {
                                Text(
                                    text = "Hâlâ izliyor musunuz?",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Text(
                                    text = if (remaining > 0) {
                                        "Oynatma uzun süredir devam ediyor. $remaining saniye içinde yanıt vermezseniz oynatma duraklatılacak."
                                    } else {
                                        "Oynatma duraklatıldı. Devam etmek için onaylayın."
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { viewModel.onStillWatchingConfirmed() }) {
                                    Text("Evet, devam et", color = LocalKitsugiAccent.current, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.onStillWatchingDismissed() }) {
                                    Text("Hayır, duraklat", color = KitsugiColors.TextSecondary)
                                }
                            }
                        )
                    }

                    // TorrentOverlay (P2P live stats isolated host)
                    P2pTorrentStatsOverlayHost(
                        viewModel = viewModel,
                        isTorrentStream = isTorrentStream,
                        isInPipMode = isInPipMode,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 70.dp, start = 24.dp)
                    )

                    // ── Hızlı puanlama çubuğu (bölüm/film bittiğinde) ──────────────────
                    QuickRateBar(
                        prompt = quickRatePrompt,
                        onRate = { score -> viewModel.rateCurrentMedia(score) },
                        onDismiss = { viewModel.dismissQuickRate() },
                        visible = !isInPipMode && !areControlsLocked,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 82.dp)
                    )

                    // Search/Fetch loading overlay for next episode
                    if (nextEpisodeLoading && !isInPipMode) {
                        PlayerInlineLoadingOverlay(message = KitsugiStrings.get(R.string.sonraki_bolum_kaynaklari_araniyor))
                    }

                    // Stream URL resolution loading overlay
                    if (isResolvingStream && !isInPipMode) {
                        PlayerInlineLoadingOverlay(message = KitsugiStrings.get(R.string.kaynak_baglantisi_cozumleniyor))
                    }

                    // Buffering is handled reactively by MiddlePlayerControls (single unified spinner)

                    // Ses parçası seçiminin GERÇEKTEN uygulandığını doğrulamak için güncel listeye
                    // erişim (kullanıcı raporu 2026-10-12: "3 ses seçeneği hiçbir şey değiştirmiyor,
                    // hep aynı İngilizce ses oynuyor").
                    val latestAudioOptions = rememberUpdatedState(audioTrackOptions)

                    // ── New Reactive Sheet Host (Aniyomi-style) ───────────────────────
                    PlayerSheetsHost(
                        sheetShown = sheetShown,
                        dismissSheet = dismissSheet,
                        onDismissRequest = {
                            viewModel.showSheet(KitsugiSheets.None)
                            viewModel.resetDismissSheet()
                        },
                        customButtons = customButtons,
                        onClickCustomButton = viewModel::executeCustomButton,
                        onLongClickCustomButton = viewModel::executeCustomButtonLongPress,
                        onOpenPanel = { panel -> viewModel.showPanel(panel) },
                        onShowSheet = { sheet -> viewModel.showSheet(sheet) },
                        currentSpeed = playbackSpeed,
                        onSpeedChange = { speed ->
                            viewModel.setPlaybackSpeed(speed, persist = false)
                        },
                        onSetSpeedAsDefault = { speed -> viewModel.setPlaybackSpeed(speed, persist = true) },
                        currentChapter = currentChapter,
                        chapters = chapters,
                        onSeekToChapter = { seg ->
                            playerEngine.seekTo((seg.start * 1000f).toLong())
                        },
                        subtitleTracks = textTrackOptions.mapIndexed { index, option ->
                            com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.SubtitleTrackInfo(
                                id = index,
                                label = option.label,
                                language = option.language,
                                formatTag = TrackFormatUtils.subtitleFormat(
                                    codec = option.codec,
                                    mimeType = option.mimeType,
                                    source = option.externalUri,
                                    declaredFormat = option.declaredFormat,
                                ),
                            )
                        },
                        selectedSubtitleIndex = if (isSubtitleDisabled) -1 else textTrackOptions.indexOfFirst { it.isSelected },
                        selectedSecondarySubtitleIndex = textTrackOptions.indexOfFirst { it.trackIndex == secondarySubtitleId },
                        supportsSecondarySubtitles = playerEngine.supportsSecondarySubtitles,
                        onSelectSecondarySubtitle = { idx ->
                            val id = textTrackOptions.getOrNull(idx)?.trackIndex ?: -1
                            playerEngine.selectSecondarySubtitle(if (id == secondarySubtitleId) -1 else id)
                        },
                        onSelectSubtitle = { idx ->
                            if (idx == -99) {
                                playerEngine.disableSubtitles()
                            } else {
                                textTrackOptions.getOrNull(idx)?.let { playerEngine.selectTrack(it) }
                            }
                        },
                        onAddSubtitleFile = {
                            if (playerEngine.supportsExternalTracks) subtitlesPicker.launch(arrayOf("*/*"))
                            else switchToMpvForFeature("Harici altyazı ekleme")
                        },
                        subtitleInputs = currentSubtitles,
                        onDownloadSubtitle = { subtitle -> viewModel.downloadSubtitleToStorage(subtitle) },
                        audioTracks = audioTrackOptions.mapIndexed { index, option ->
                            com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.AudioTrackInfo(
                                id = index,
                                label = option.label,
                                language = option.language,
                                formatTag = TrackFormatUtils.audioFormat(
                                    codec = option.codec,
                                    mimeType = option.mimeType,
                                    source = option.externalUri,
                                ),
                                channelCount = option.channelCount,
                            )
                        },
                        audioTrackLabels = audioTrackOptions.map { it.label },
                        selectedAudioIndex = audioTrackOptions.indexOfFirst { it.isSelected },
                        onSelectAudio = { idx ->
                            val picked = audioTrackOptions.getOrNull(idx)
                            if (picked != null) {
                                playerEngine.selectTrack(picked)
                                // Motorun seçimi uygulayıp uygulamadığını kısa bir gecikmeyle kontrol et:
                                // uygulanmadıysa kullanıcı sessizce eski sesi dinlemek yerine bilgilendirilir.
                                scope.launch {
                                    kotlinx.coroutines.delay(900L)
                                    val options = latestAudioOptions.value
                                    val applied = options.any { it.isSelected && it.label == picked.label }
                                    if (options.isNotEmpty() && !applied) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Ses parçası değişmedi: ${picked.label}. Kaynak tek ses sunuyor ya da cihaz bu parçayı çözemiyor olabilir.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        onAddAudioFile = {
                            if (playerEngine.supportsExternalTracks) audioPicker.launch(arrayOf("*/*"))
                            else switchToMpvForFeature("Harici ses ekleme")
                        },
                        onDownloadAudio = { idx ->
                            audioTrackOptions.getOrNull(idx)?.let { track ->
                                val needsGrant = android.os.Build.VERSION.SDK_INT < 29 &&
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (needsGrant) {
                                    pendingAudioExport = PendingAudioExport(
                                        track = track, tracks = audioTrackOptions.toList(),
                                        videoUrl = viewModel.currentVideoUrl.value,
                                        audioUrl = viewModel.currentAudioUrl.value,
                                        sourceIndex = viewModel.currentSourceIndex.value
                                    )
                                    audioStoragePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else viewModel.downloadAudioToStorage(track, audioTrackOptions)
                            }
                        },
                        onDownloadProviderAudio = { providerIndex ->
                            val url = currentStreamSources.getOrNull(currentSourceIndex)
                                ?.externalAudioSources?.getOrNull(providerIndex)?.url
                            if (url != null) {
                                val needsGrant = android.os.Build.VERSION.SDK_INT < 29 &&
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (needsGrant) {
                                    pendingAudioExport = PendingAudioExport(
                                        providerAudioIndex = providerIndex, providerAudioUrl = url,
                                        videoUrl = viewModel.currentVideoUrl.value,
                                        audioUrl = viewModel.currentAudioUrl.value,
                                        sourceIndex = viewModel.currentSourceIndex.value
                                    )
                                    audioStoragePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else viewModel.downloadProviderAudioToStorage(providerIndex, url)
                            }
                        },
                        externalAudioUrlsInPlayer = audioTrackOptions.mapNotNull {
                            it.audioSource?.takeIf { url -> it.audioIsExternal && url.isNotBlank() }
                        }.toSet(),
                        videoQualityTracks = videoQualityTracks,
                        isVideoQualityAuto = isVideoQualityAuto,
                        onSelectVideoQuality = { track ->
                            playerEngine.selectVideoQuality(track)
                            isVideoQualityAuto = playerEngine.isVideoQualityAuto
                            viewModel.showSheet(KitsugiSheets.None)
                        },
                        qualityOptions = qualityOptions,
                        qualityAutoSubtitle = run {
                            val profile = com.kitsugi.animelist.core.player.QualityProfile
                                .deserialize(safeSettings.qualityProfileJson)
                            if (profile.preference ==
                                com.kitsugi.animelist.core.player.QualityPreference.AUTO
                            ) {
                                null
                            } else {
                                val cap = profile.effectiveMaxVideoHeight
                                val capText = cap?.let { "${it}p" } ?: profile.preference.label
                                KitsugiStrings.get(R.string.quality_auto_profile_hint, capText)
                            }
                        },
                        selectedQuality = selectedQuality,
                        isQualityVariantsLoading = isQualityVariantsLoading,
                        onSelectQuality = { option ->
                            val src = currentStreamSources.getOrNull(currentSourceIndex)
                            val master = qualityMasterUrl ?: currentVideoUrl
                            // Uygulanamayacak seçim (ör. yalnızca motor tavanıyla
                            // uygulanabilen DASH kalitesi + MPV) işaretlenmez; kullanıcı
                            // bilgilendirilir. Aksi hâlde arayüz "seçili" gösterirken
                            // oynatıcı hiçbir şey yapmamış olurdu.
                            val needsCap = option != null && option.switchUrl == null
                            val capUnsupported = needsCap && activeEngineType != PlayerEngineType.MEDIA3
                            if (capUnsupported) {
                                Toast.makeText(
                                    context,
                                    KitsugiStrings.get(R.string.quality_cap_not_supported_engine),
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (src != null && !master.isNullOrBlank()) {
                                selectedQuality = option
                                variantFallbackUrl = null
                                viewModel.showSheet(KitsugiSheets.None)
                                // HLS varyantı → URL değişir, tavan kaldırılır.
                                // DASH / ayrı ses gruplu HLS → URL aynı (veya master) kalır,
                                // tavan uygulanır. Böylece ses grubu korunur.
                                playerEngine.setMaxVideoHeight(if (needsCap) option?.height else null)
                                val target = option?.switchUrl ?: master
                                if (target != currentVideoUrl) {
                                    // Aynı kaynak, aynı başlıklar ve altyazılar; yalnızca
                                    // çözünürlük değişir. Oynatıcı mevcut saniyeden devam eder.
                                    viewModel.changeStreamSource(currentSourceIndex, src, target)
                                }
                            }
                        },
                        streamSources = currentStreamSources,
                        selectedSourceIndex = currentSourceIndex,
                        onSelectSource = { idx ->
                            val src = currentStreamSources.getOrNull(idx) ?: return@PlayerSheetsHost
                            viewModel.showSheet(KitsugiSheets.None)
                            viewModel.isResolvingStream = true
                            scope.launch {
                                val repo = AddonStreamRepository(context)
                                val resolvedUrl = try {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        repo.resolveStreamUrl(src)
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }
                                viewModel.isResolvingStream = false
                                if (resolvedUrl != null) {
                                    viewModel.changeStreamSource(idx, src, resolvedUrl)
                                } else {
                                    android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.kaynak_cozumlenemedi), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        sleepTimerSecondsLeft = sleepTimerSecondsLeft,
                        onStartSleepTimer = { secs -> viewModel.startTimer(secs) },
                        selectedDecoder = viewModel.currentDecoder.collectAsState().value,
                        onSelectDecoder = { dec -> viewModel.updateDecoder(dec) },
                        statisticsPage = statisticsPage,
                        onSelectStatisticsPage = { page ->
                            if (activeEngineType != PlayerEngineType.MPV) switchToMpvForFeature("Oynatıcı istatistikleri")
                            viewModel.updateStatisticsPage(page)
                        },
                        audioChannels = audioChannels,
                        onSelectAudioChannels = { ch ->
                            if (activeEngineType != PlayerEngineType.MPV) switchToMpvForFeature("Ses kanal düzeni")
                            viewModel.updateAudioChannels(ch)
                        },
                        isLocalSource = viewModel.isLocalSource,
                        // Gömülü (MKV içi) altyazılar VM'in dış altyazı listesinde görünmez;
                        // yerel parça listesi boş değilse de altyazı vardır.
                        hasSubTracks = textTrackOptions.isNotEmpty() ||
                            viewModel.hasSubTracks.collectAsState().value,
                        showSubtitles = viewModel.screenshotShowSubtitles.collectAsState().value,
                        onToggleShowSubtitles = { viewModel.toggleScreenshotShowSubtitles(it) },
                        cachePath = context.cacheDir.absolutePath,
                        onSetAsArt = { artType, stream -> viewModel.setAsArt(artType, stream) },
                        onSaveScreenshot = { stream -> viewModel.saveImage(stream, playerEngine.currentPosition.toInt()) },
                        onShareScreenshot = { stream -> viewModel.shareImage(stream, playerEngine.currentPosition.toInt()) },
                        takeScreenshot = { path, showSubs -> viewModel.takeScreenshot(path, showSubs) },
                        onOpenExternalPlayer = openExternalPlayerAction,
                        reduceMotion = safeSettings.reducePlayerMotion,
                        modifier = Modifier.fillMaxSize()
                    )

                    // ── New Reactive Panel Host (Aniyomi-style) ──────────────────────
                    PlayerPanelsHost(
                        panelShown = panelShown,
                        onDismissRequest = { viewModel.showPanel(KitsugiPanels.None) },
                        reduceMotion = safeSettings.reducePlayerMotion,
                        currentAudioDelayMs = audioDelayMs.toInt(),
                        onAudioDelayChanged = { delay ->
                            if (delay < 0L && !playerEngine.supportsNegativeAudioDelay) {
                                switchToMpvForFeature("Negatif ses gecikmesi")
                            }
                            audioDelayMs = delay
                            playerEngine.setAudioDelay(delay)
                            // Kaydırıcı basılı tutulduğunda her 50 ms'de DataStore'a yazmak
                            // yerine 400 ms debounce ile kalıcılaştır.
                            audioDelaySaveJob?.cancel()
                            audioDelaySaveJob = scope.launch {
                                kotlinx.coroutines.delay(400)
                                dataStore.setDefaultAudioDelayMs(delay)
                            }
                        },
                        currentSubDelayMs = subtitleDelayMs.toInt(),
                        currentSecondaryDelayMs = secondarySubtitleDelayMs.toInt(),
                        currentSubSpeed = subtitleSpeed,
                        onSubDelayChanged = { delay ->
                            val unsupportedPositive = delay != 0 && !playerEngine.supportsSubtitleDelay
                            val unsupportedNegative = delay < 0 && !playerEngine.supportsNegativeSubtitleDelay
                            if (unsupportedPositive || unsupportedNegative) {
                                switchToMpvForFeature("Altyazı gecikmesi")
                            }
                            subtitleDelayMs = delay.toLong()
                            playerEngine.setSubtitleDelay(delay.toLong())
                            scope.launch { dataStore.setSubtitleDelayMs(delay.toLong()) }
                        },
                        onSecondarySubDelayChanged = { delay ->
                            if (!playerEngine.supportsSecondarySubtitleDelay) {
                                switchToMpvForFeature("İkincil altyazı gecikmesi")
                            }
                            secondarySubtitleDelayMs = delay.toLong()
                            playerEngine.setSecondarySubtitleDelay(delay.toLong())
                            scope.launch { dataStore.setSecondarySubtitleDelayMs(delay.toLong()) }
                        },
                        onSubSpeedChanged = { speed ->
                            if (speed != 1f && !playerEngine.supportsSubtitleSpeed) {
                                switchToMpvForFeature("Altyazı hızı")
                            }
                            subtitleSpeed = speed
                            playerEngine.setSubtitleSpeed(speed)
                            scope.launch { dataStore.setSubtitleSpeed(speed) }
                        },
                        filterValues = videoFilterValues,
                        onFilterChange = { filter, value ->
                            if (value != 0 && !playerEngine.supportsVideoFilters) {
                                switchToMpvForFeature("Video filtreleri")
                            }
                            videoFilterValues = videoFilterValues + (filter to value)
                            playerEngine.setVideoFilters(mapOf(filter.mpvProperty to value))
                        },
                        onResetAllFilters = {
                            videoFilterValues = emptyMap()
                            playerEngine.setVideoFilters(
                                KitsugiVideoFilters.entries.associate { it.mpvProperty to 0 }
                            )
                        },
                        subtitleStyle = subtitleStyle,
                        onSubtitleStyleChange = { style ->
                            subtitleStyle = style
                            playerEngine.setSubtitleStyle(style)
                            scope.launch {
                                dataStore.setDefaultSubtitleSize(style.size)
                                dataStore.setDefaultSubtitleColor(style.textColor)
                                dataStore.setSubtitleBold(style.bold)
                                dataStore.setSubtitleOutlineEnabled(style.outlineEnabled)
                                dataStore.setSubtitleItalic(style.italic)
                                dataStore.setSubtitleFontSize(style.fontSize)
                                dataStore.setSubtitleFont(style.font)
                                dataStore.setSubtitleBorderStyle(style.borderStyle)
                                dataStore.setSubtitleBorderSize(style.borderSize.toFloat())
                                dataStore.setSubtitleShadowOffset(style.shadowOffset.toFloat())
                                dataStore.setSubtitleJustification(style.justification)
                                dataStore.setSubtitleBackgroundColor(style.backgroundColor)
                                dataStore.setSubtitleBorderColor(style.borderColor)
                                dataStore.setSubtitleFontScale(style.fontScale)
                                dataStore.setSubtitlePos(style.pos)
                                dataStore.setSubtitleOverrideAss(style.overrideAss)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )

                    // ── New Reactive Dialog Host (Aniyomi-style) ─────────────────────
                    PlayerDialogsHost(
                        dialogShown = dialogShown,
                        episodes = episodesList,
                        currentEpisode = currentEpisode,
                        onPlayEpisode = { ep ->
                            viewModel.showDialog(KitsugiDialogs.None)
                            playEpisode(ep)
                        },
                        onDismissRequest = { viewModel.showDialog(KitsugiDialogs.None) },
                        modifier = Modifier.align(Alignment.Center),
                        episodesLoading = episodesLoading,
                        isMovie = isMovie,
                        onRetryEpisodes = { viewModel.refreshEpisodes() }
                    )

                    if (showResumeDialog) {
                    AlertDialog(
                    onDismissRequest = {
                    showResumeDialog = false
                    playerEngine.play()
                    },
                    containerColor = KitsugiColors.Surface,
                    titleContentColor = KitsugiColors.TextPrimary,
                    textContentColor = KitsugiColors.TextSecondary,
                    shape = RoundedCornerShape(26.dp),
                    title = {
                    Text(
                    text = KitsugiStrings.get(R.string.inline_resume_where_you_left_off_e18b1b2),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                    )
                    },
                    text = {
                    Text(
                    text = KitsugiStrings.get(R.string.inline_the_video_was_last_paused_f2c687e, formatMs(pendingResumePos)),
                    style = MaterialTheme.typography.bodyMedium
                    )
                    },
                    confirmButton = {
                    TextButton(
                    onClick = {
                    showResumeDialog = false
                    playerEngine.seekTo(pendingResumePos)
                    playerEngine.play()
                    }
                    ) {
                    Text(KitsugiStrings.get(R.string.action_yes), color = LocalKitsugiAccent.current, fontWeight = FontWeight.Bold)
                    }
                    },
                    dismissButton = {
                    TextButton(
                    onClick = {
                    showResumeDialog = false
                    playerEngine.seekTo(0L)
                    playerEngine.play()
                    }
                    ) {
                    Text(stringResource(R.string.hayir), color = KitsugiColors.TextSecondary)
                    }
                    }
                    )
                    
                    }

                }
            }
        }
    }

}

@Composable
fun PlaybackEndedOverlay(
    visible: Boolean,
    hasNextEpisode: Boolean,
    onReplay: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.bolum_bitti),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Replay button
                KitsugiButton(
                    onClick = onReplay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Replay,
                        contentDescription = stringResource(R.string.yeniden_oynat),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.yeniden_oynat), fontWeight = FontWeight.SemiBold)
                }

                // Next Episode button
                if (hasNextEpisode) {
                    KitsugiButton(
                        onClick = onPlayNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KitsugiColors.Accent,
                            contentColor = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.SkipNext,
                            contentDescription = stringResource(R.string.sonraki_bolum),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.sonraki_bolum), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun P2pTorrentStatsOverlayHost(
    viewModel: KitsugiPlayerViewModel,
    isTorrentStream: Boolean,
    isInPipMode: Boolean,
    modifier: Modifier = Modifier
) {
    val p2pSettings by P2pSettingsRepository.uiState.collectAsState()
    val p2pStreamingState by P2pStreamingEngine.state.collectAsState()
    val controlsShown by viewModel.controlsShown.collectAsState()
    val areControlsLocked by viewModel.areControlsLocked.collectAsState()
    val sheetShown by viewModel.sheetShown.collectAsState()
    val panelShown by viewModel.panelShown.collectAsState()
    val dialogShown by viewModel.dialogShown.collectAsState()

    var isSessionDismissed by remember { mutableStateOf(false) }

    val isStreaming = p2pStreamingState.isStreaming
    LaunchedEffect(isStreaming) {
        if (isStreaming) isSessionDismissed = false
    }

    val isAnyMenuShown = sheetShown != KitsugiSheets.None ||
            panelShown != com.kitsugi.animelist.ui.screens.fullscreen.KitsugiPanels.None ||
            dialogShown != KitsugiDialogs.None

    val areControlsVisible = controlsShown && !areControlsLocked && !isAnyMenuShown

    val showTorrentOverlay = (isTorrentStream || isStreaming) &&
        !p2pSettings.hideTorrentStats &&
        !isSessionDismissed &&
        !isInPipMode &&
        isStreaming &&
        areControlsVisible

    TorrentOverlay(
        visible = showTorrentOverlay,
        downloadSpeedBytes = p2pStreamingState.downloadSpeed,
        uploadSpeedBytes = p2pStreamingState.uploadSpeed,
        seeders = p2pStreamingState.seeds,
        peers = p2pStreamingState.peers,
        bufferPercent = com.kitsugi.animelist.core.p2p.bufferPercent(p2pStreamingState.bufferProgress),
        onDismiss = {
            isSessionDismissed = true
        },
        onInteraction = {
            viewModel.showControls()
        },
        modifier = modifier
    )
}

/**
 * Oynatıcıya en son verilen akışın kimliği. Aynı kimlikle yalnızca altyazı listesi değiştiğinde
 * akış yeniden hazırlanmaz. [engine] referans eşitliğiyle karşılaştırılır (motor değişince —
 * ör. yedek motora geçiş — tam hazırlık yapılır).
 */
private data class PreparedStreamIdentity(
    val videoUrl: String,
    val headersHash: Int,
    val audioUrl: String?,
    val engine: com.kitsugi.animelist.core.player.engine.PlayerEngine
)
