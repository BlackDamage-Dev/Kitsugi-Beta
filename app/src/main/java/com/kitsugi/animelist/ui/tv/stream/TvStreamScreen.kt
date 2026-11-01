package com.kitsugi.animelist.ui.tv.stream

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.compose.AsyncImage
import com.kitsugi.animelist.core.player.ExternalPlayerInput
import com.kitsugi.animelist.core.player.ExternalPlayerLauncher
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.remote.AddonStreamClient
import com.kitsugi.animelist.data.remote.DebridResolver
import com.kitsugi.animelist.data.repository.AddonStreamRepository
import com.kitsugi.animelist.data.repository.StreamSorter
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.tv.player.TvPlayerActivity
import com.kitsugi.animelist.ui.screens.stream.AddonFetchState
import com.kitsugi.animelist.ui.screens.stream.StreamViewModel
import com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.screens.stream.DebridCacheState
import com.kitsugi.animelist.ui.screens.stream.getCacheState
import com.kitsugi.animelist.ui.screens.stream.resolveStreamQuality
import com.kitsugi.animelist.ui.screens.stream.resolveStreamSizeLabel
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
import com.kitsugi.animelist.ui.tv.input.isSelectKey
import com.kitsugi.animelist.ui.utils.requestFocusAfterFrames
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.kitsugi.animelist.core.player.QualityProfile
import com.kitsugi.animelist.core.player.QualityDataHelper

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvStreamScreen(
    args: TvStreamArgs,
    onBack: () -> Unit,
    onLaunchExternalPlayer: ((input: ExternalPlayerInput, streamKey: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()

    val viewModel: StreamViewModel = viewModel()
    val addonStates by viewModel.addonStates.collectAsStateWithLifecycle()
    val isResolvingId by viewModel.isResolvingId.collectAsStateWithLifecycle()
    val idResolveFailed by viewModel.idResolveFailed.collectAsStateWithLifecycle()
    val imdbId by viewModel.imdbId.collectAsStateWithLifecycle()
    val webViewState by viewModel.webViewDialogState.collectAsStateWithLifecycle()

    val repository = remember { AddonStreamRepository(context) }
    val streamPrefs = remember { context.getSharedPreferences("KitsugiStreamPrefs", Context.MODE_PRIVATE) }
    val dataStore = remember { SettingsDataStore(context) }
    val appSettings by dataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    var selectedAddonFilter by remember { mutableStateOf<String?>(null) }
    var resolvingSource by remember { mutableStateOf<StreamSource?>(null) }
    var resolvingError by remember { mutableStateOf<String?>(null) }
    // "Her zaman sor" tercihi TV'de yok sayılıyordu: diyalog açılmadan sessizce yerleşik
    // oynatıcıya düşülüyordu. Artık gerçek bir seçim diyaloğu gösterilir.
    var pendingAskPlay by remember { mutableStateOf<Pair<StreamSource, String>?>(null) }
    var rememberAskChoice by remember { mutableStateOf(false) }
    var activeStreamJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val cancelResolving = {
        activeStreamJob?.cancel()
        activeStreamJob = null
        resolvingSource = null
        resolvingError = null
    }

    BackHandler(enabled = resolvingSource != null) {
        cancelResolving()
    }

    val allStreams = remember(addonStates, appSettings.qualityProfileJson) {
        val rawList = addonStates.flatMap { it.streams }
        val profile = QualityProfile.deserialize(appSettings.qualityProfileJson)
        val filtered = QualityDataHelper.filterByBitrate(rawList, profile.maxBitrateKbps)
        val sorted = StreamSorter.sort(filtered)
        QualityDataHelper.sortByProfile(sorted, profile)
    }
    val filteredStreams = remember(allStreams, selectedAddonFilter) {
        if (selectedAddonFilter == null) allStreams else allStreams.filter { it.addonName == selectedAddonFilter }
    }

    val alternativeTitles = remember(args.titleEnglish, args.titleRomaji, args.titleNative, args.synonyms, args.title) {
        buildList {
            args.titleEnglish?.takeIf { it.isNotBlank() && it != args.title }?.let { add(it) }
            args.titleRomaji?.takeIf { it.isNotBlank() && it != args.title }?.let { add(it) }
            args.titleNative?.takeIf { it.isNotBlank() && it != args.title }?.let { add(it) }
            args.synonyms.asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it != args.title }
                .forEach { add(it) }
        }.distinctBy { com.kitsugi.animelist.data.cloudstream.CsTitleMatcher.normalizeTitleForMatch(it) }
    }

    LaunchedEffect(
        args.malId, args.aniListId, args.tmdbId, args.bangumiId, args.episode, args.season,
        args.isMovie, args.mediaType, args.title, args.startYear, alternativeTitles
    ) {
        viewModel.startFetch(
            malId = args.malId,
            aniListId = args.aniListId,
            tmdbId = args.tmdbId,
            bangumiId = args.bangumiId,
            episode = args.episode,
            season = args.season,
            title = args.title,
            alternativeTitles = alternativeTitles,
            startYear = args.startYear,
            isMovie = args.isMovie,
            mediaType = args.mediaType
        )
    }

    val launchPlayer = remember(context, allStreams, streamPrefs, onLaunchExternalPlayer) {
        { source: StreamSource, resolvedUrl: String, engine: String ->
            val streamKey = (source.infoHash ?: resolvedUrl).hashCode().toString()
            val resumePositionMs = streamPrefs.getLong("pos_$streamKey", 0L)
            // Referans eşitliği tutmadığında kaynak indeksi -1 kalıyor ve oynatıcı içinde
            // kaynak değiştirme devre dışı kalıyordu; içerikle eşleştir.
            val resolvedSourceIndex = allStreams.indexOf(source).takeIf { it >= 0 }
                ?: allStreams.indexOfFirst {
                    it.addonName == source.addonName && it.url == source.url
                }
            if (engine == "EXTERNAL" && onLaunchExternalPlayer != null) {
                val input = ExternalPlayerLauncher.createInput(
                    url = resolvedUrl,
                    title = context.getString(R.string.args_title_bolum_args_episode, args.title, args.episode),
                    headers = source.requestHeaders,
                    resumePositionMs = resumePositionMs,
                    subtitles = source.subtitles
                )
                onLaunchExternalPlayer(input, streamKey)
            } else {
                TvPlayerActivity.start(
                    context = context,
                    videoUrl = resolvedUrl,
                    title = context.getString(R.string.args_title_bolum_args_episode, args.title, args.episode),
                    headers = source.requestHeaders,
                    subtitles = source.subtitles ?: emptyList(),
                    allSources = allStreams,
                    currentSourceIndex = resolvedSourceIndex,
                    malId = args.malId,
                    aniListId = args.aniListId,
                    tmdbId = args.tmdbId,
                    season = args.season,
                    episode = args.episode,
                    animeTitle = args.title,
                    posterUrl = args.posterUrl,
                    titleEnglish = args.titleEnglish,
                    titleRomaji = args.titleRomaji,
                    titleNative = args.titleNative,
                    synonyms = args.synonyms,
                    isMovie = args.isMovie,
                    startYear = args.startYear,
                    description = args.description
                )
            }
        }
    }

    val handlePlayStream = remember(appSettings, launchPlayer) {
        { source: StreamSource, resolvedUrl: String ->
            val engine = appSettings.playerPreference
            if (engine == "ASK") {
                pendingAskPlay = source to resolvedUrl
            } else {
                launchPlayer(source, resolvedUrl, engine)
            }
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        // Background art (Poster blurred)
        if (!args.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = args.posterUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.15f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                KitsugiColors.Background.copy(alpha = 0.5f),
                                KitsugiColors.Background
                            )
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = KitsugiTvTokens.Spacing.screenHorizontal,
                    vertical = KitsugiTvTokens.Spacing.screenVertical
                ),
            horizontalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.contentPadding)
        ) {
            // Left Panel: Poster & Info
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.sm)
            ) {
                AsyncImage(
                    model = args.posterUrl,
                    contentDescription = args.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .clip(KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape),
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = args.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = KitsugiColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (args.isMovie) stringResource(R.string.bangumi_tag_movie) else stringResource(R.string.ui2_tv_stream_season_episode, args.season, args.episode),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )

                if (!args.description.isNullOrBlank()) {
                    Text(
                        text = args.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = KitsugiColors.TextSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Panel: Filter Tabs & Stream List
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.sm)
            ) {
                // Addon Filter Tabs Row
                val uniqueAddons = remember(addonStates) { addonStates.map { it.addonName } }
                if (uniqueAddons.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.sm),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        item {
                            FilterChipTv(
                                text = stringResource(R.string.hepsi),
                                isSelected = selectedAddonFilter == null,
                                onClick = { selectedAddonFilter = null }
                            )
                        }
                        items(uniqueAddons) { addonName ->
                            FilterChipTv(
                                text = addonName,
                                isSelected = selectedAddonFilter == addonName,
                                onClick = { selectedAddonFilter = addonName }
                            )
                        }
                    }
                }

                // Streams list container
                val listState = rememberLazyListState()
                val isAnyLoading = addonStates.any { it.isLoading }
                val hasErrors = addonStates.any { state ->
                    state.error != null && (selectedAddonFilter == null || state.addonName == selectedAddonFilter)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isResolvingId) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            KitsugiPlasmaLoader(size = 50.dp)
                        }
                    } else if (filteredStreams.isEmpty() && !isAnyLoading && !hasErrors) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.inline_no_streams_found_d834d54),
                                style = MaterialTheme.typography.titleMedium,
                                color = KitsugiColors.TextMuted
                            )
                            if (idResolveFailed) {
                                Text(
                                    text = stringResource(R.string.inline_failed_to_resolve_identity_185ada5),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Red.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        CompositionLocalProvider(
                            LocalBringIntoViewSpec provides KitsugiScrollDefaults.rememberTvCenteredSpec()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .dpadVerticalFastScroll(scrollableState = listState)
                                    .focusRestorer()
                                    .focusGroup(),
                                verticalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.sm)
                            ) {
                                itemsIndexed(filteredStreams) { index, stream ->
                                    TvStreamRowItem(
                                        stream = stream,
                                        index = index,
                                        accentColor = accentColor,
                                        onClick = {
                                            val isTorrent = stream.isTorrent
                                            val hasDebrid = !DebridResolver(context).getApiKey().isNullOrBlank()
                                            val p2pEnabled = com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isP2pEnabled()
                                            if (isTorrent && !hasDebrid && !p2pEnabled) {
                                                if (!com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isConsentGranted()) {
                                                    resolvingError = "Torrent akışları için Debrid hesabı veya Ayarlar'dan P2P izni gereklidir."
                                                    return@TvStreamRowItem
                                                } else {
                                                    com.kitsugi.animelist.core.p2p.P2pSettingsRepository.setP2pEnabled(true)
                                                }
                                            }
                                            activeStreamJob?.cancel()
                                            resolvingSource = stream
                                            resolvingError = null
                                            activeStreamJob = scope.launch {
                                                val resolvedUrl = try {
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                        repository.resolveStreamUrl(stream)
                                                    }
                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    null
                                                }
                                                resolvingSource = null
                                                if (resolvedUrl == null || resolvedUrl.startsWith("magnet:", ignoreCase = true) || resolvedUrl.startsWith("torrent://", ignoreCase = true)) {
                                                    resolvingError = if (isTorrent) "P2P akış motoru başlatılamadı veya torrent çözümlenemedi." else "Akış linki çözümlenemedi."
                                                    return@launch
                                                }
                                                handlePlayStream(stream, resolvedUrl)
                                            }
                                        }
                                    )
                                }

                                val activeErrors = addonStates.filter { state ->
                                    state.error != null && (selectedAddonFilter == null || state.addonName == selectedAddonFilter)
                                }

                                items(activeErrors) { addonState ->
                                    val isVerifyAvailable = addonState.addonName.startsWith("⚡ ")
                                    TvStreamErrorItem(
                                        addonState = addonState,
                                        accentColor = accentColor,
                                        isVerifyAvailable = isVerifyAvailable,
                                        onVerify = {
                                            viewModel.onVerifyPlugin(addonState.addonName)
                                        }
                                    )
                                }

                                if (isAnyLoading) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            KitsugiPlasmaLoader(size = 26.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Resolving Overlay Dialog
        resolvingSource?.let { source ->
            Dialog(
                onDismissRequest = cancelResolving,
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                Box(
                    modifier = Modifier
                        .size(280.dp, 200.dp)
                        .clip(KitsugiTvTokens.Shapes.dialog as RoundedCornerShape)
                        .background(KitsugiColors.BackgroundElevated)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KitsugiPlasmaLoader(size = 40.dp)
                        Text(
                            text = stringResource(R.string.inline_resolving_link_bf37591),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = source.addonName,
                            style = MaterialTheme.typography.labelSmall,
                            color = KitsugiColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        OutlinedButton(
                            onClick = cancelResolving,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, KitsugiColors.SurfaceStrong),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.padding(top = 4.dp).height(36.dp)
                        ) {
                            Text(stringResource(R.string.settings_cancel), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Error Dialog
        resolvingError?.let { err ->
            com.kitsugi.animelist.ui.tv.components.TvDialog(
                onDismiss = { resolvingError = null },
                title = stringResource(R.string.akis_hatasi),
                width = 420.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = err,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { resolvingError = null }) {
                            Text(stringResource(R.string.action_ok), color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // "Her zaman sor" oynatıcı seçimi (TV)
        pendingAskPlay?.let { (source, resolvedUrl) ->
            com.kitsugi.animelist.ui.tv.components.TvDialog(
                onDismiss = { pendingAskPlay = null },
                title = "Oynatıcı Seç",
                width = 420.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Bu bölümü hangi oynatıcıda açmak istersiniz?",
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (rememberAskChoice) {
                                    scope.launch { dataStore.setPlayerPreference("INTERNAL") }
                                }
                                pendingAskPlay = null
                                launchPlayer(source, resolvedUrl, "INTERNAL")
                            }
                        ) {
                            Text("Dahili Oynatıcı", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                if (rememberAskChoice) {
                                    scope.launch { dataStore.setPlayerPreference("EXTERNAL") }
                                }
                                pendingAskPlay = null
                                launchPlayer(source, resolvedUrl, "EXTERNAL")
                            }
                        ) {
                            Text("Harici Oynatıcı", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = rememberAskChoice,
                            onCheckedChange = { rememberAskChoice = it }
                        )
                        Text(
                            text = "Seçimimi hatırla",
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Captcha WebView Dialog
        webViewState?.let { state ->
            KitsugiWebViewDialog(
                title = state.displayName,
                url = state.url,
                onDismiss = {
                    viewModel.onWebViewDismissed(
                        dismissedPluginId = state.pluginId,
                        dismissedDisplayName = state.displayName,
                        title = args.title,
                        alternativeTitles = alternativeTitles,
                        startYear = args.startYear,
                        season = args.season,
                        episode = args.episode,
                        malId = args.malId,
                        aniListId = args.aniListId,
                        tmdbId = args.tmdbId,
                        bangumiId = args.bangumiId,
                        isMovie = args.isMovie,
                        mediaType = args.mediaType
                    )
                }
            )
        }
    }
}

@Composable
private fun FilterChipTv(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) LocalKitsugiAccent.current else KitsugiColors.Surface)
            .border(
                BorderStroke(
                    width = if (isFocused) KitsugiTvTokens.Cards.focusedBorderWidth else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected || isFocused) Color.White else KitsugiColors.TextSecondary
        )
    }
}

@Composable
private fun TvStreamRowItem(
    stream: StreamSource,
    index: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    // Kalite/boyut yalnızca kanıt varsa gösterilir (tahmin yok).
    val quality = remember(stream) { resolveStreamQuality(stream).label.orEmpty() }
    val size = remember(stream) { resolveStreamSizeLabel(stream).orEmpty() }
    val cacheState = remember(stream) { getCacheState(stream) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.08f) else KitsugiColors.Surface)
            .border(
                BorderStroke(
                    width = if (isFocused) KitsugiTvTokens.Cards.focusedBorderWidth else 0.dp,
                    color = if (isFocused) accentColor else Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .tvClickable(shape = RoundedCornerShape(8.dp), onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stream Index / Quality Badge
        val indexText = if (quality.isNotBlank()) quality else "${index + 1}"
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(KitsugiColors.SurfaceStrong),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = indexText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                color = accentColor
            )
        }

        // Title and Provider Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Rich Badges Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Addon Name Badge
                TvStreamBadge(
                    text = stream.addonName,
                    color = KitsugiColors.AccentPurple,
                    bgAlpha = 0.15f,
                    bgColor = KitsugiColors.AccentPurple
                )

                // Debrid Cache Badge — yalnızca torrent/debrid akışlarında anlamlıdır
                if (stream.isTorrent) {
                    val (cacheText, cacheColor) = when (cacheState) {
                        DebridCacheState.CACHED     -> "Önbellek" to KitsugiColors.AccentGreen
                        DebridCacheState.NOT_CACHED -> "İndir" to KitsugiColors.AccentOrange
                        DebridCacheState.P2P        -> "P2P" to KitsugiColors.AccentBlue
                    }
                    TvStreamBadge(text = cacheText, color = cacheColor, bgAlpha = 0.15f, bgColor = cacheColor)
                }

                // Size Badge
                if (size.isNotBlank()) {
                    TvStreamBadge(
                        text = size,
                        color = KitsugiColors.TextSecondary,
                        bgAlpha = 0.1f,
                        bgColor = Color.White
                    )
                }
            }

            Text(
                text = stream.name ?: stringResource(R.string.ui_stream_unnamed),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KitsugiColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!stream.title.isNullOrBlank() && stream.title != stream.name) {
                Text(
                    text = stream.title.trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = KitsugiColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Indicator (Play Icon)
        KitsugiArtIcon(art = KitsugiArt.Play, size = 24.dp)
    }
}

@Composable
private fun TvStreamBadge(text: String, color: Color, bgAlpha: Float, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor.copy(alpha = bgAlpha))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvStreamErrorItem(
    addonState: AddonFetchState,
    accentColor: Color,
    isVerifyAvailable: Boolean,
    onVerify: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = if (isFocused) Color.White.copy(alpha = 0.08f) else KitsugiColors.Surface
    val borderColor = if (isFocused) Color.Red else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(
                BorderStroke(
                    width = if (isFocused) KitsugiTvTokens.Cards.focusedBorderWidth else 0.dp,
                    color = borderColor
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isVerifyAvailable) {
                    Modifier.tvClickable(shape = RoundedCornerShape(8.dp), onClick = onVerify)
                } else Modifier
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Red indicator / Warning icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Red.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVerifyAvailable) Icons.Extension else Icons.Close,
                contentDescription = null,
                tint = Color.Red
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = addonState.addonName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = KitsugiColors.TextPrimary
            )

            Text(
                text = addonState.error ?: stringResource(R.string.ui_error_unknown),
                style = MaterialTheme.typography.labelSmall,
                color = KitsugiColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            addonState.errorCode?.let { code ->
                PlatformErrorExplanationBlock(code = code)
            }

            if (isVerifyAvailable && isFocused) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.inline_press_ok_on_the_remote_9ad02f0),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
        }
    }
}
