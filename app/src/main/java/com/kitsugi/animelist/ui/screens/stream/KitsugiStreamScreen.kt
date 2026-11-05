package com.kitsugi.animelist.ui.screens.stream

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.core.player.ExternalPlayerInput
import com.kitsugi.animelist.core.player.ExternalPlayerLauncher
import com.kitsugi.animelist.data.repository.AddonStreamRepository
import com.kitsugi.animelist.data.repository.StreamSorter
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.screens.fullscreen.components.MetaCastMember
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import android.webkit.CookieManager
import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog
import kotlinx.coroutines.launch
import com.kitsugi.animelist.ui.app.AddonViewModel
import com.kitsugi.animelist.ui.components.KitsugiAddonsSettingsDialog
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.data.remote.DebridResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import android.util.Log
import com.kitsugi.animelist.core.player.SubtitleInput


import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage

import com.kitsugi.animelist.core.player.QualityProfile
import com.kitsugi.animelist.core.player.QualityDataHelper
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Stream picker screen.
 *
 * All fetch work is delegated to [StreamViewModel], which survives:
 *  - backgrounding the app
 *  - screen rotation
 *  - navigating away and back within the same Activity
 *
 * The Composable only reads [StateFlow]s from the ViewModel, making it
 * purely presentational with no lifecycle-coupled data loading.
 */
@Composable
fun KitsugiStreamScreen(
    malId: Int?,
    aniListId: Int?,
    tmdbId: Int? = null,
    mediaType: MediaType? = null,
    bangumiId: Int? = null,
    episode: Int,
    season: Int,
    isMovie: Boolean = false,
    title: String,
    posterUrl: String?,
    titleEnglish: String? = null,
    titleRomaji: String? = null,
    titleNative: String? = null,
    synonyms: List<String> = emptyList(),
    startYear: Int? = null,
    description: String? = null,
    castList: List<MetaCastMember> = emptyList(),
    isAutoplay: Boolean = false,
    isDownloadMode: Boolean = false,
    cs3Url: String? = null,
    cs3ApiName: String? = null,
    knownImdbId: String? = null,
    onBack: () -> Unit,
    onLaunchExternalPlayer: ((input: ExternalPlayerInput, streamKey: String) -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()
    var showManualSearchDialog by remember { mutableStateOf(false) }

    // ── ViewModel (survives backgrounding & rotation) ──────────────────────────
    val viewModel: StreamViewModel = viewModel()
    val addonViewModel: AddonViewModel = viewModel()

    // ── Collect ViewModel state ───────────────────────────────────────────────
    val addonStates    by viewModel.addonStates.collectAsState()
    val isResolvingId  by viewModel.isResolvingId.collectAsState()
    val idResolveFailed by viewModel.idResolveFailed.collectAsState()
    val imdbId         by viewModel.imdbId.collectAsState()
    val scopedSourceName by viewModel.scopedSourceName.collectAsState()
    val webViewState   by viewModel.webViewDialogState.collectAsState()

    val addonsList by addonViewModel.addonsList.collectAsState(initial = emptyList())
    val reposList by addonViewModel.reposList.collectAsState(initial = emptyList())
    val csPluginsList by addonViewModel.csPluginsList.collectAsState(initial = emptyList())

    // ── Local UI-only state (ephemeral, fine to live in Composable) ───────────
    val repository     = remember { AddonStreamRepository(context) }
    val streamPrefs    = remember { context.getSharedPreferences(KitsugiStreamActivity.PREFS_NAME, Context.MODE_PRIVATE) }
    val dataStore      = remember { SettingsDataStore(context) }
    val playerPrefs    = remember { context.getSharedPreferences("MyWebViewPrefs", Context.MODE_PRIVATE) }
    val appSettings    by dataStore.settingsFlow.collectAsState(initial = AppSettings())

    // ── Race condition guard: her yeni stream seçiminde öncekini iptal et ──────
    var activeStreamJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var selectedAddonFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var resolvingSource     by remember { mutableStateOf<StreamSource?>(null) }
    var resolvingError      by remember { mutableStateOf<String?>(null) }
    var pendingPlayAction   by remember { mutableStateOf<PendingPlayAction?>(null) }
    var showSettingsDialog  by remember { mutableStateOf(false) }
    /**
     * v2.4.319: Oynatıcı başlatılamazsa kullanıcıya SEBEBİ gösterilir. Önceden hata
     * sessizce yutuluyor, ekran hiçbir şey yapmıyormuş gibi görünüyordu.
     */
    var playbackStartError by remember { mutableStateOf<String?>(null) }

    val allStreams = remember(addonStates, appSettings.qualityProfileJson) {
        val rawList = addonStates.flatMap { it.streams }
        val profile = QualityProfile.deserialize(appSettings.qualityProfileJson)
        val filtered = QualityDataHelper.filterByBitrate(rawList, profile.maxBitrateKbps)
        val sorted = StreamSorter.sort(filtered)
        QualityDataHelper.sortByProfile(sorted, profile)
    }
    val isAnyLoading = addonStates.any { it.isLoading }

    var isAutoplayAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(reposList) {
        addonViewModel.syncRepos(reposList)
    }

    LaunchedEffect(addonViewModel) {
        addonViewModel.onShowMessage = { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ── Build alternative titles list once ────────────────────────────────────
    val alternativeTitles = remember(titleEnglish, titleRomaji, titleNative, synonyms, title) {
        buildList {
            titleEnglish?.takeIf { it.isNotBlank() && it != title }?.let { add(it) }
            titleRomaji?.takeIf { it.isNotBlank() && it != title }?.let { add(it) }
            titleNative?.takeIf { it.isNotBlank() && it != title }?.let { add(it) }
            synonyms.asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it != title }
                .forEach { add(it) }
        }.distinctBy { com.kitsugi.animelist.data.cloudstream.CsTitleMatcher.normalizeTitleForMatch(it) }
    }

    // ── Trigger fetch — ViewModel guards against duplicate/redundant calls ────
    LaunchedEffect(malId, aniListId, tmdbId, bangumiId, episode, season, cs3Url, cs3ApiName, isMovie, mediaType, title, startYear, alternativeTitles, knownImdbId) {
        viewModel.startFetch(
            malId             = malId,
            aniListId         = aniListId,
            tmdbId            = tmdbId,
            bangumiId         = bangumiId,
            episode           = episode,
            season            = season,
            title             = title,
            alternativeTitles = alternativeTitles,
            startYear         = startYear,
            cs3Url            = cs3Url,
            cs3ApiName        = cs3ApiName,
            isMovie           = isMovie,
            mediaType         = mediaType,
            knownImdbId       = knownImdbId
        )
    }

    // ── Player launcher ───────────────────────────────────────────────────────
    val launchPlayer = remember(context, allStreams, streamPrefs, onLaunchExternalPlayer, description, castList, synonyms) {
        { source: StreamSource, resolvedUrl: String, engine: String ->
            val watchHistoryAnimeId = if (aniListId != null) aniListId.toString()
                                      else if (malId != null) malId.toString()
                                      else if (!cs3Url.isNullOrBlank()) cs3Url.hashCode().toString()
                                      else title.hashCode().toString()

            val historyEntry = com.kitsugi.animelist.data.local.WatchHistoryManager.history.value.find {
                it.animeId == watchHistoryAnimeId && it.episode == episode
            }
            val resumePositionMs = historyEntry?.positionMs ?: 0L
            val streamKey = (source.infoHash ?: resolvedUrl).hashCode().toString()
            // `indexOf` referans eşitliğine dayanır. Kaynak listesi filtre/sıralama sonrası
            // yeniden üretildiğinde -1 dönebiliyor; bu durumda oynatıcıda kaynak indeksi -1
            // kalıyor ve "kaynak değiştir" çalışmıyordu. İçerik eşleşmesiyle yedekle.
            val resolvedSourceIndex = allStreams.indexOf(source).takeIf { it >= 0 }
                ?: allStreams.indexOfFirst {
                    it.addonName == source.addonName && it.url == source.url
                }

            // ── İzleme geçmişine kaydet ─────────────────────────────────────
            // Geçmiş yazımı oynatmayı ASLA engellemez: disk/veri hatası fırlatırsa
            // yutulur, video yine açılır. (Önceden bu çağrı try bloğunun DIŞINDAYDI;
            // burada fırlayan bir istisna "izle'ye basınca uygulama kapanıyor"
            // şikâyetinin doğrudan sebebi olabiliyordu.)
            runCatching {
                com.kitsugi.animelist.data.local.WatchHistoryManager.record(
                    com.kitsugi.animelist.data.model.WatchHistoryEntry(
                        animeId = watchHistoryAnimeId,
                        animeTitle = title,
                        posterUrl = source.thumbnailUrl.takeIf { !it.isNullOrBlank() } ?: posterUrl,
                        episode = episode,
                        season = season,
                        isMovie = isMovie,
                        quality = source.quality,
                        source = source.addonName,
                        malId = malId,
                        aniListId = aniListId,
                        tmdbId = tmdbId,
                        streamUrl = resolvedUrl,
                        streamHeaders = source.requestHeaders,
                        // Eklenti metinleri HTML/CSS artığı içerebiliyor ("… .fa-secondary{opacity:.4} DUAL").
                        // Geçmişe temiz hâli yazılır; aksi hâlde oynatıcı ve izleme geçmişi
                        // bozuk metin gösteriyordu (2026-11-01).
                        streamTitle = com.kitsugi.animelist.utils.StreamLabelSanitizer.sanitize(source.title),
                        streamName = com.kitsugi.animelist.utils.StreamLabelSanitizer.sanitize(source.name),
                        cs3Url = cs3Url,
                        cs3ApiName = cs3ApiName
                    )
                )
            }.onFailure { e ->
                Log.w("KitsugiStreamScreen", "İzleme geçmişi yazılamadı (oynatma devam ediyor): ${e.message}", e)
            }

            try {
                if (engine == "exoplayer") {
                    val started = KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                        context = context, videoUrl = resolvedUrl,
                        audioUrl = source.externalAudioUrl,
                        title = context.getString(R.string.title_bolum_episode, title, episode), headers = source.requestHeaders,
                        subtitles = source.subtitles, allSources = allStreams,
                        currentSourceIndex = resolvedSourceIndex,
                        malId = malId, aniListId = aniListId, tmdbId = tmdbId, bangumiId = bangumiId, imdbId = imdbId ?: knownImdbId, season = season, episode = episode,
                        animeTitle = title, posterUrl = posterUrl,
                        titleEnglish = titleEnglish, titleRomaji = titleRomaji, titleNative = titleNative,
                        synonyms = synonyms,
                        startYear = startYear, description = description, cast = castList,
                        isMovie = isMovie,
                        cs3Url = cs3Url,
                        cs3ApiName = cs3ApiName,
                        resumePositionMs = resumePositionMs
                    )
                    if (!started) playbackStartError = KitsugiStrings.get(R.string.stream_play_failed_generic)
                } else {
                    if (onLaunchExternalPlayer != null) {
                        val input = ExternalPlayerLauncher.createInput(
                            url = resolvedUrl, title = context.getString(R.string.title_bolum_episode, title, episode),
                            headers = source.requestHeaders, resumePositionMs = resumePositionMs,
                            subtitles = source.subtitles
                        )
                        onLaunchExternalPlayer(input, streamKey)
                    } else {
                        val started = KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                            context = context, videoUrl = resolvedUrl,
                            audioUrl = source.externalAudioUrl,
                            title = context.getString(R.string.title_bolum_episode, title, episode), headers = source.requestHeaders,
                            subtitles = source.subtitles, allSources = allStreams,
                            currentSourceIndex = resolvedSourceIndex,
                            malId = malId, aniListId = aniListId, tmdbId = tmdbId, bangumiId = bangumiId, imdbId = imdbId ?: knownImdbId, season = season, episode = episode,
                            animeTitle = title, posterUrl = posterUrl,
                            titleEnglish = titleEnglish, titleRomaji = titleRomaji, titleNative = titleNative,
                            synonyms = synonyms,
                            startYear = startYear, description = description, cast = castList,
                            isMovie = isMovie,
                            cs3Url = cs3Url,
                            cs3ApiName = cs3ApiName,
                            resumePositionMs = resumePositionMs
                        )
                        if (!started) playbackStartError = KitsugiStrings.get(R.string.stream_play_failed_generic)
                    }
                }
            } catch (e: Throwable) {
                Log.e("KitsugiStreamScreen", "Oynatıcı başlatılamadı: ${e.message}", e)
                android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.oynatici_baslatilamadi_x, e.message ?: e.javaClass.simpleName), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val handlePlayStream = remember(appSettings, launchPlayer) {
        { source: StreamSource, resolvedUrl: String ->
            val engine = when (appSettings.playerPreference) {
                "EXTERNAL" -> "mpv"
                "ASK"      -> "ask"
                else       -> "exoplayer"
            }
            val streamKey = (source.infoHash ?: resolvedUrl).hashCode().toString()
            if (engine == "ask") {
                pendingPlayAction = PendingPlayAction(source, resolvedUrl, streamKey)
            } else {
                launchPlayer(source, resolvedUrl, engine)
            }
            Unit
        }
    }

    val onCancelResolving = remember {
        {
            activeStreamJob?.cancel()
            activeStreamJob = null
            resolvingSource = null
            resolvingError = null
        }
    }

    var showP2pConsentDialog by remember { mutableStateOf(false) }
    var pendingP2pSource by remember { mutableStateOf<StreamSource?>(null) }
    var pendingIsDownload by remember { mutableStateOf(false) }

    val executeStreamResolution = { source: StreamSource, isDl: Boolean ->
        val isTorrent = source.isTorrent
        val hasDebrid = !DebridResolver(context).getApiKey().isNullOrBlank()
        val p2pEnabled = com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isP2pEnabled()

        if (isTorrent && !hasDebrid && !p2pEnabled) {
            if (!com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isConsentGranted()) {
                pendingP2pSource = source
                pendingIsDownload = isDl
                showP2pConsentDialog = true
            } else {
                com.kitsugi.animelist.core.p2p.P2pSettingsRepository.setP2pEnabled(true)
            }
        }

        if (!isTorrent || hasDebrid || com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isP2pEnabled()) {
            activeStreamJob?.cancel()
            resolvingSource = source
            resolvingError = null

            val job = scope.launch {
                val resolvedUrl = try {
                    withContext(Dispatchers.IO) {
                        repository.resolveStreamUrl(source)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("KitsugiStreamScreen", "Akış çözümlenirken hata oluştu", e)
                    null
                }

                resolvingSource = null
                if (resolvedUrl == null || resolvedUrl.startsWith("magnet:", ignoreCase = true) || resolvedUrl.startsWith("torrent://", ignoreCase = true)) {
                    resolvingError = if (isTorrent) "P2P akış motoru başlatılamadı veya torrent çözümlenemedi." else "Akış linki çözümlenemedi."
                    return@launch
                }

                if (isDl) {
                    val isEphemeralLocalUrl = resolvedUrl.startsWith("http://127.0.0.1", ignoreCase = true) ||
                        resolvedUrl.startsWith("http://localhost", ignoreCase = true)
                    if (isEphemeralLocalUrl) {
                        resolvingError = "P2P akışları doğrudan indirilemez. Bu bölümü indirmek için Ayarlar > Debrid menüsünden Debrid hesabı ekleyebilirsiniz."
                        return@launch
                    }

                    com.kitsugi.animelist.data.local.AnimeDownloadManager.addDownload(
                        context = context,
                        animeId = if (aniListId != null) aniListId.toString()
                        else if (malId != null) malId.toString()
                        else if (tmdbId != null) tmdbId.toString()
                        else if (bangumiId != null) "bgm_$bangumiId"
                        else title.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_').take(30).ifBlank { "media" },
                        animeTitle = title,
                        posterUrl = source.thumbnailUrl.takeIf { !it.isNullOrBlank() } ?: posterUrl,
                        episode = episode,
                        season = season,
                        url = resolvedUrl,
                        quality = source.quality ?: "Bilinmeyen",
                        requestHeaders = source.requestHeaders ?: emptyMap(),
                        subtitles = source.subtitles,
                        malId = malId,
                        aniListId = aniListId,
                        tmdbId = tmdbId,
                        source = source.addonName,
                        // İndirilenler ekranı da bu metinleri gösteriyor; geçmişle aynı
                        // şekilde temizlenmiş hâli kaydedilir.
                        streamTitle = com.kitsugi.animelist.utils.StreamLabelSanitizer.sanitize(source.title),
                        streamName = com.kitsugi.animelist.utils.StreamLabelSanitizer.sanitize(source.name)
                    )
                    android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.indirme_kuyruga_eklendi), android.widget.Toast.LENGTH_SHORT).show()
                    context.startActivity(
                        android.content.Intent(context, com.kitsugi.animelist.ui.screens.offline.DownloadsActivity::class.java)
                    )
                } else {
                    handlePlayStream(source, resolvedUrl)
                }
            }
            activeStreamJob = job
        }
    }

    // T1.8 – Autoplay Selection Logic (with Consent Guard)
    LaunchedEffect(allStreams, isAnyLoading) {
        if (isAutoplay && !isAnyLoading && allStreams.isNotEmpty() && !isAutoplayAttempted) {
            isAutoplayAttempted = true
            val lastAddonName = streamPrefs.getString("last_addon_name", null)
            val bestSource = com.kitsugi.animelist.core.player.StreamAutoPlaySelector.selectBestStream(
                currentAddonName = lastAddonName,
                currentStreamSource = null,
                nextEpisodeStreams = allStreams
            )
            if (bestSource != null) {
                val isTorrent = bestSource.isTorrent
                val hasDebrid = !DebridResolver(context).getApiKey().isNullOrBlank()
                val p2pAllowed = com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isP2pEnabled() &&
                    com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isConsentGranted()

                if (!isTorrent || hasDebrid || p2pAllowed) {
                    executeStreamResolution(bestSource, false)
                }
            }
        }
    }

    if (showP2pConsentDialog) {
        com.kitsugi.animelist.ui.components.p2p.P2pConsentDialog(
            onDismiss = {
                showP2pConsentDialog = false
                pendingP2pSource = null
            },
            onConsentApproved = {
                showP2pConsentDialog = false
                val pending = pendingP2pSource
                val isDl = pendingIsDownload
                pendingP2pSource = null
                if (pending != null) {
                    executeStreamResolution(pending, isDl)
                }
            }
        )
    }

    // ── Oynatıcı başlatma hatası ──────────────────────────────────────────────
    playbackStartError?.let { message ->
        AlertDialog(
            onDismissRequest = { playbackStartError = null },
            title = {
                Text(
                    stringResource(R.string.stream_play_failed_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(message, color = KitsugiColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { playbackStartError = null }) {
                    Text(stringResource(R.string.action_ok), color = accentColor)
                }
            },
            containerColor = KitsugiColors.Surface
        )
    }

    BackHandler {
        if (resolvingSource != null) {
            onCancelResolving()
        } else {
            onBack()
        }
    }

    // ── Main content ──────────────────────────────────────────────────────────
    StreamScreenContent(
        title = title, posterUrl = posterUrl, episode = episode, season = season, isMovie = isMovie,
        isDownloadMode = isDownloadMode,
        imdbId = imdbId, accentColor = accentColor,
        addonStates = addonStates, allStreams = allStreams,
        isResolvingId = isResolvingId, idResolveFailed = idResolveFailed,
        isAnyLoading = isAnyLoading, selectedAddonFilter = selectedAddonFilter,
        onAddonFilterChange = { selectedAddonFilter = it },
        onStreamSelected = { source ->
            streamPrefs.edit().putString("last_addon_name", source.addonName).apply()
            executeStreamResolution(source, isDownloadMode)
        },
        onDownloadSelected = { source ->
            streamPrefs.edit().putString("last_addon_name", source.addonName).apply()
            executeStreamResolution(source, true)
        },
        onBack = onBack,
        resolvingSource = resolvingSource, resolvingError = resolvingError,
        onResolvingErrorDismiss = { resolvingError = null },
        onCancelResolving = onCancelResolving,
        pendingPlayAction = pendingPlayAction,
        onPendingDismiss = { pendingPlayAction = null },
        playerPrefs = playerPrefs, launchPlayer = launchPlayer,
        onPendingDone = { pendingPlayAction = null },
        onRememberChoice = { engine ->
            scope.launch {
                val pref = if (engine == "mpv") "EXTERNAL" else "INTERNAL"
                dataStore.setPlayerPreference(pref)
            }
            playerPrefs.edit().putString("default_player_engine", engine).apply()
        },
        onVerifyPlugin = { addonDisplayName ->
            viewModel.onVerifyPlugin(addonDisplayName)
        },
        onRetryPlugin = { addonDisplayName ->
            viewModel.retryPlugin(addonDisplayName)
        },
        onOpenSettings = { showSettingsDialog = true },
        onOpenHistory = onOpenHistory,
        onOpenDownloads = onOpenDownloads,
        onManualSearchClick = { showManualSearchDialog = true },
        onRefreshAll = { viewModel.refreshAll() },
        scopedSourceName = scopedSourceName,
        onSearchAllSources = { viewModel.searchAllSources() }
    )

    if (showManualSearchDialog) {
        var queryText by remember { mutableStateOf(title) }
        var isSearching by remember { mutableStateOf(false) }
        var searchResults by remember { mutableStateOf<List<com.kitsugi.animelist.data.remote.JikanSearchResult>>(emptyList()) }

        AlertDialog(
            onDismissRequest = { showManualSearchDialog = false },
            title = { Text("Eklentilerde Manuel Arama", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = { Text(stringResource(R.string.inline_enter_an_anime_series_or_1d8d8bb), color = KitsugiColors.TextMuted) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    isSearching = true
                                    scope.launch {
                                        try {
                                            val raw = com.kitsugi.animelist.data.cloudstream.CsStreamRunner.searchAllAddons(context, queryText)
                                            searchResults = raw.map { (api, response) ->
                                                com.kitsugi.animelist.data.remote.JikanSearchResult(
                                                    malId = response.url.hashCode(),
                                                    title = response.name,
                                                    subtitle = api.name,
                                                    type = com.kitsugi.animelist.model.MediaType.Anime,
                                                    total = null,
                                                    score = null,
                                                    isAdult = false,
                                                    imageUrl = response.posterUrl,
                                                    year = null,
                                                    source = "cs3",
                                                    cs3Url = response.url,
                                                    cs3ApiName = api.name
                                                )
                                            }
                                        } catch (e: Exception) {
                                            Log.e("ManualSearch", "Search failed: ${e.message}")
                                        } finally {
                                            isSearching = false
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Search, contentDescription = stringResource(R.string.ara), tint = accentColor)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            KitsugiPlasmaLoader(size = 36.dp)
                        }
                    } else if (searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.inline_no_results_search_or_try_7468bcc), color = KitsugiColors.TextMuted)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(searchResults.size) { index ->
                                val res = searchResults[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.3f))
                                        .tvClickable {
                                            showManualSearchDialog = false
                                            viewModel.startFetch(
                                                malId = malId,
                                                aniListId = aniListId,
                                                tmdbId = tmdbId,
                                                bangumiId = bangumiId,
                                                episode = episode,
                                                season = season,
                                                title = title,
                                                alternativeTitles = alternativeTitles,
                                                startYear = startYear,
                                                cs3Url = res.cs3Url,
                                                cs3ApiName = res.cs3ApiName,
                                                isMovie = isMovie,
                                                mediaType = mediaType,
                                                knownImdbId = knownImdbId
                                            )
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 40.dp, height = 60.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(KitsugiColors.SurfaceStrong)
                                    ) {
                                        if (!res.imageUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = res.imageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(res.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(res.subtitle ?: "", color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(Icons.KeyboardArrowRight, contentDescription = null, tint = KitsugiColors.TextMuted)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showManualSearchDialog = false }) {
                    Text(stringResource(R.string.kapat), color = accentColor)
                }
            },
            containerColor = KitsugiColors.Surface
        )
    }

    if (showSettingsDialog) {
        val isTv = LocalIsTvDevice.current
        if (isTv) {
            TvStreamSettingsDialog(
                addonViewModel = addonViewModel,
                useGithubProxy = appSettings.useGithubProxy,
                onUseGithubProxyChanged = { enabled ->
                    scope.launch { dataStore.setUseGithubProxy(enabled) }
                },
                onDismiss = { showSettingsDialog = false }
            )
        } else {
            KitsugiAddonsSettingsDialog(
                addons = addonsList,
                initialDebridToken = addonViewModel.debridToken,
                repos = reposList,
                repoPlugins = addonViewModel.repoPluginsState,
                repoLoadingState = addonViewModel.repoLoadingState,
                csPlugins = csPluginsList,
                initialTab = 1, // Focuses directly on Video Sağlayıcıları
                onAddAddon = { addonViewModel.addAddon(it) },
                onToggleAddon = { addon, enabled -> addonViewModel.toggleAddon(addon, enabled) },
                onDeleteAddon = { addonViewModel.deleteAddon(it) },
                onSaveDebridToken = { addonViewModel.saveDebridToken(it) },
                onAddRepo = { addonViewModel.addRepo(it) },
                onDeleteRepo = { addonViewModel.deleteRepo(it) },
                onFetchRepoPlugins = { addonViewModel.fetchRepoPlugins(it) },
                onInstallPlugin = { plugin, onResult -> addonViewModel.installPlugin(plugin, onResult) },
                onInstallAllPlugins = { repoUrl, repoName, plugins ->
                    addonViewModel.installAllPlugins(repoUrl, repoName, plugins, addonsList, csPluginsList)
                },
                onUpdateAllPlugins = { repoUrl, repoName, plugins ->
                    addonViewModel.updateAllPlugins(repoUrl, repoName, plugins, csPluginsList)
                },
                bulkInstallRepoUrl = addonViewModel.bulkInstallRepoUrl,
                bulkInstallRepoName = addonViewModel.bulkInstallRepoName,
                bulkInstallDone = addonViewModel.bulkInstallDone,
                bulkInstallTotal = addonViewModel.bulkInstallTotal,
                bulkInstallCurrentName = addonViewModel.bulkInstallCurrentName,
                bulkInstallResultMessage = addonViewModel.bulkInstallResultMessage,
                onClearBulkInstallResult = { addonViewModel.clearBulkInstallResult() },
                onToggleCsPlugin = { plugin, enabled -> addonViewModel.toggleCsPlugin(plugin, enabled) },
                onUninstallCsPlugin = { addonViewModel.uninstallCsPlugin(it) },
                onUninstallAllCsPlugins = { addonViewModel.uninstallAllCsPlugins() },
                // ── GitHub Vekil Sunucu (jsDelivr) ────────────────────────────────
                // Bu parametreler eskiden GEÇİLMİYORDU: diyalog varsayılan (false / no-op)
                // değerlerle açılıyordu ve anahtar hiçbir zaman AÇILAMIYORDU.
                useGithubProxy = appSettings.useGithubProxy,
                onUseGithubProxyChanged = { enabled ->
                    scope.launch { dataStore.setUseGithubProxy(enabled) }
                },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }

    // ── WebView captcha dialog — driven by ViewModel state ────────────────────
    val state = webViewState
    if (state != null) {
        KitsugiWebViewDialog(
            title = state.displayName,
            url   = state.url,
            onDismiss = {
                viewModel.onWebViewDismissed(
                    dismissedPluginId   = state.pluginId,
                    dismissedDisplayName = state.displayName,
                    title               = title,
                    alternativeTitles   = alternativeTitles,
                    startYear           = startYear,
                    season              = season,
                    episode             = episode,
                    malId               = malId,
                    aniListId           = aniListId,
                    tmdbId              = tmdbId,
                    bangumiId           = bangumiId,
                    isMovie             = isMovie,
                    mediaType           = mediaType
                )
            }
        )
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TvStreamSettingsDialog(
    addonViewModel: AddonViewModel,
    useGithubProxy: Boolean,
    onUseGithubProxyChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val addons by addonViewModel.addonsList.collectAsState(initial = emptyList())
    val repos by addonViewModel.reposList.collectAsState(initial = emptyList())
    val csPlugins by addonViewModel.csPluginsList.collectAsState(initial = emptyList())

    var showDebridDialog by remember { mutableStateOf(false) }
    var showAddRepoDialog by remember { mutableStateOf(false) }

    // Fetch repo plugins in background when repos load
    LaunchedEffect(repos) {
        repos.forEach { repo ->
            addonViewModel.fetchRepoPlugins(repo.repoUrl)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight()
                .heightIn(max = screenHeight * 0.9f)
                .clip(KitsugiTvTokens.Shapes.dialog as RoundedCornerShape)
                .background(KitsugiColors.Surface)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.tv.material3.Text(
                    text = stringResource(R.string.akis_eklenti_ayarlari),
                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.kapat),
                        tint = KitsugiColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
            val listState = rememberLazyListState()

            CompositionLocalProvider(LocalBringIntoViewSpec provides tvSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .dpadVerticalFastScroll(scrollableState = listState),
                    verticalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.itemGap)
                ) {
                    // Debrid Token
                    item {
                        val isConnected = addonViewModel.debridToken.isNotBlank()
                        TvSettingsActionRow(
                            title = "RealDebrid / Alldebrid Token",
                            description = if (isConnected) stringResource(R.string.token_tanimli_x, addonViewModel.debridToken.take(8)) else stringResource(R.string.hesap_bagli_degil_debrid_tokeninizi_girmek_icin_tiklayin),
                            actionText = if (isConnected) stringResource(R.string.inline_update_e9a7033) else stringResource(R.string.tv_connect),
                            onClick = { showDebridDialog = true }
                        )
                    }

                    // GitHub Vekil Sunucu (jsDelivr) — telefonda diyalog içinde, TV'de bu satırda
                    item {
                        TvSettingsActionRow(
                            title = "GitHub Vekil Sunucu (jsDelivr)",
                            description = if (useGithubProxy) {
                                "Etkin: raw.githubusercontent.com istekleri jsDelivr CDN üzerinden gider."
                            } else {
                                "Kapalı: eklenti/repo güncellemeleri doğrudan GitHub'a gider. Ağ engeli varsa açın."
                            },
                            actionText = if (useGithubProxy) "Açık" else "Kapalı",
                            onClick = { onUseGithubProxyChanged(!useGithubProxy) }
                        )
                    }

                    // Repository Headers
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.tv.material3.Text(
                            text = stringResource(R.string.eklenti_havuzlari_repos),
                            style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.tv.material3.MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Repos List
                    itemsIndexed(repos) { _, repo ->
                        val plugins = addonViewModel.repoPluginsState[repo.repoUrl]
                        val isCurrentlyInstalling = addonViewModel.bulkInstallRepoUrl == repo.repoUrl
                        val repoDesc = when {
                            isCurrentlyInstalling -> "Eklentiler kuruluyor: ${addonViewModel.bulkInstallDone}/${addonViewModel.bulkInstallTotal}"
                            plugins != null -> "${plugins.size} eklenti mevcut. Kurmak veya güncellemek için dokunun."
                            else -> "Eklentiler yükleniyor..."
                        }

                        TvSettingsActionRow(
                            title = repo.name,
                            description = repoDesc,
                            actionText = if (plugins != null && !isCurrentlyInstalling) "Hepsini Kur" else "Bekleyin",
                            onClick = {
                                if (plugins != null && !isCurrentlyInstalling) {
                                    addonViewModel.installAllPlugins(repo.repoUrl, repo.name, plugins, addons, csPlugins)
                                }
                            }
                        )
                    }

                    item {
                        TvSettingsActionRow(
                            title = stringResource(R.string.yeni_repo_ekle),
                            description = stringResource(R.string.yeni_bir_eklenti_havuzu_url_si_ekleyin),
                            actionText = stringResource(R.string.ekle),
                            onClick = { showAddRepoDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Dialogs for TV Inputs
    if (showDebridDialog) {
        var tokenInput by remember { mutableStateOf(addonViewModel.debridToken) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDebridDialog = false },
            title = { com.kitsugi.animelist.ui.theme.gradient.Text("Debrid Tokeni Gir", color = Color.White) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { com.kitsugi.animelist.ui.theme.gradient.Text("Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = androidx.tv.material3.MaterialTheme.colorScheme.primary,
                        focusedBorderColor = androidx.tv.material3.MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    )
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        addonViewModel.saveDebridToken(tokenInput)
                        showDebridDialog = false
                    }
                ) {
                    com.kitsugi.animelist.ui.theme.gradient.Text(stringResource(R.string.kaydet), color = androidx.tv.material3.MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDebridDialog = false }) {
                    com.kitsugi.animelist.ui.theme.gradient.Text(stringResource(R.string.vazgec), color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = KitsugiColors.BackgroundElevated,
            textContentColor = Color.White
        )
    }

    if (showAddRepoDialog) {
        var repoUrlInput by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { com.kitsugi.animelist.ui.theme.gradient.Text(stringResource(R.string.yeni_repo_ekle_3154d60), color = Color.White) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = repoUrlInput,
                    onValueChange = { repoUrlInput = it },
                    label = { com.kitsugi.animelist.ui.theme.gradient.Text("Repo URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = androidx.tv.material3.MaterialTheme.colorScheme.primary,
                        focusedBorderColor = androidx.tv.material3.MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    )
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (repoUrlInput.isNotBlank()) {
                            addonViewModel.addRepo(repoUrlInput)
                        }
                        showAddRepoDialog = false
                    }
                ) {
                    com.kitsugi.animelist.ui.theme.gradient.Text(stringResource(R.string.ekle), color = androidx.tv.material3.MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAddRepoDialog = false }) {
                    com.kitsugi.animelist.ui.theme.gradient.Text(stringResource(R.string.vazgec), color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = KitsugiColors.BackgroundElevated,
            textContentColor = Color.White
        )
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsActionRow(
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape
            )
            .tvClickable(shape = KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape) { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(KitsugiTvTokens.Spacing.contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            androidx.tv.material3.Text(
                text = title,
                style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            androidx.tv.material3.Text(
                text = description,
                style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.width(KitsugiTvTokens.Spacing.contentPadding))

        Box(
            modifier = Modifier
                .width(100.dp)
                .height(36.dp)
                .clip(KitsugiTvTokens.Shapes.chip as RoundedCornerShape)
                .background(Color.White.copy(alpha = 0.1f))
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.2f),
                    KitsugiTvTokens.Shapes.chip as RoundedCornerShape
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.tv.material3.Text(
                text = actionText,
                style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
