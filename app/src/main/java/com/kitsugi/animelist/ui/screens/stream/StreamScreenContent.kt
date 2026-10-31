package com.kitsugi.animelist.ui.screens.stream

import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.repository.StreamProbe
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiTopAction
import com.kitsugi.animelist.ui.components.KitsugiTopActionButton
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

/** Üst barın yüksekliği — içerik bu kadar boşlukla başlar. */
private val TOP_BAR_HEIGHT = 58.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun StreamScreenContent(
    title: String,
    posterUrl: String?,
    episode: Int,
    season: Int,
    isMovie: Boolean = false,
    isDownloadMode: Boolean = false,
    imdbId: String?,
    accentColor: Color,
    addonStates: List<AddonFetchState>,
    allStreams: List<StreamSource>,
    isResolvingId: Boolean,
    idResolveFailed: Boolean,
    isAnyLoading: Boolean,
    selectedAddonFilter: String?,
    onAddonFilterChange: (String?) -> Unit,
    onStreamSelected: (StreamSource) -> Unit,
    onDownloadSelected: (StreamSource) -> Unit,
    onBack: () -> Unit,
    resolvingSource: StreamSource?,
    resolvingError: String?,
    onResolvingErrorDismiss: () -> Unit,
    onCancelResolving: () -> Unit = {},
    pendingPlayAction: PendingPlayAction?,
    onPendingDismiss: () -> Unit,
    playerPrefs: SharedPreferences,
    launchPlayer: (StreamSource, String, String) -> Unit,
    onPendingDone: () -> Unit,
    onRememberChoice: (String) -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onVerifyPlugin: ((addonName: String) -> Unit)? = null,
    onRetryPlugin: ((addonName: String) -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onManualSearchClick: (() -> Unit)? = null
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── Local Search & Tag Filtering State ─────────────────────────────────────
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTagFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val sortedAddonStates = remember(addonStates) {
        addonStates.sortedWith(
            compareByDescending<AddonFetchState> { it.streams.isNotEmpty() }
                .thenByDescending { it.isLoading }
        )
    }

    // ── Real-time Synchronized Stream & Addon Filtering ─────────────────────────
    val matchingAddonStates = remember(sortedAddonStates, selectedAddonFilter, searchQuery, selectedTagFilter) {
        val query = searchQuery.trim().lowercase()
        val tag = selectedTagFilter?.lowercase()

        // PERF: Aktif filtre yokken akış başına kalite/dil çözümlemesi (regex + önbellek
        // sorgusu) hiç yapılmaz. Kaynaklar çekilirken `addonStates` her eklenti bittiğinde
        // güncelleniyor; eskiden her güncellemede 60+ akış baştan taranıyor ve liste
        // "veri çekerken" kilitleniyordu. Filtresiz durumda liste aynen geçirilir.
        if (query.isEmpty() && tag == null) {
            return@remember sortedAddonStates.filter { addonState ->
                (selectedAddonFilter == null || addonState.addonName == selectedAddonFilter) &&
                    (addonState.isLoading || addonState.error != null || addonState.streams.isNotEmpty())
            }
        }

        sortedAddonStates.mapNotNull { addonState ->
            if (selectedAddonFilter != null && addonState.addonName != selectedAddonFilter) {
                return@mapNotNull null
            }

            val matchingStreams = addonState.streams.filter { stream ->
                // Filtreleme de rozetlerle aynı kanıta dayalı bilgiyi kullanır.
                val qualityInfo = resolveStreamQuality(stream, StreamProbe.cached(stream.url))
                val qual = qualityInfo.label.orEmpty()
                val langInfo = resolveStreamLangInfo(stream, StreamProbe.cached(stream.url))
                val langType = langInfo.kind.toLangType()

                val matchesQuery = query.isEmpty() ||
                    stream.name.lowercase().contains(query) ||
                    stream.title.lowercase().contains(query) ||
                    stream.addonName.lowercase().contains(query) ||
                    (stream.url != null && stream.url.lowercase().contains(query)) ||
                    qual.lowercase().contains(query) ||
                    langType.label.lowercase().contains(query) ||
                    ((query == "tr" || query == "türkçe" || query == "turkce") && (langType == StreamLangType.SUB || langType == StreamLangType.DUB || langType == StreamLangType.DUAL))

                val matchesTag = when (tag) {
                    "altyazı" -> langType == StreamLangType.SUB || langType == StreamLangType.DUAL ||
                        langInfo.subtitleLanguages.isNotEmpty()
                    "dublaj" -> langType == StreamLangType.DUB || langType == StreamLangType.DUAL
                    "1080p" -> qualityInfo.height != null && qualityInfo.height >= 1080
                    "2160p" -> qualityInfo.height != null && qualityInfo.height >= 2160
                    "720p" -> qualityInfo.height == 720
                    "debrid" -> stream.infoHash != null || stream.url?.contains("magnet") == true
                    else -> true
                }

                matchesQuery && matchesTag
            }

            if (matchingStreams.isEmpty() && !addonState.isLoading && addonState.error == null) {
                null
            } else {
                addonState.copy(streams = matchingStreams)
            }
        }
    }

    var qualitySort by rememberSaveable { mutableStateOf(false) }
    val filteredAddonStates = remember(matchingAddonStates, qualitySort) {
        if (!qualitySort) matchingAddonStates else matchingAddonStates.map { state ->
            state.copy(streams = state.streams.sortedByDescending {
                resolveStreamQuality(it, StreamProbe.cached(it.url)).height ?: -1
            })
        }
    }
    val totalFilteredStreamsCount = remember(filteredAddonStates) {
        filteredAddonStates.sumOf { it.streams.size }
    }
    val hasActiveFilter = searchQuery.isNotBlank() || selectedTagFilter != null || selectedAddonFilter != null

    CompositionLocalProvider(LocalStreamSort provides StreamSortState(qualitySort) { qualitySort = !qualitySort }) {
        Box(modifier = Modifier.fillMaxSize().background(KitsugiColors.Background)) {
            // Arka plan: medya posteri çok soluk + üstte vurgu rengi ışıması.
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.10f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                KitsugiColors.Background.copy(alpha = 0.72f),
                                KitsugiColors.Background
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
            )

            if (isLandscape) {
                LandscapeLayout(
                    title = title, posterUrl = posterUrl, episode = episode, season = season, isMovie = isMovie,
                    isDownloadMode = isDownloadMode,
                    imdbId = imdbId, accentColor = accentColor, addonStates = sortedAddonStates,
                    allStreams = allStreams, selectedAddonFilter = selectedAddonFilter,
                    searchQuery = searchQuery, onQueryChange = { searchQuery = it },
                    selectedTagFilter = selectedTagFilter, onTagSelect = { selectedTagFilter = it },
                    onAddonFilterChange = onAddonFilterChange, onBack = onBack,
                    isResolvingId = isResolvingId, idResolveFailed = idResolveFailed, isAnyLoading = isAnyLoading,
                    filteredAddonStates = filteredAddonStates, totalFilteredStreamsCount = totalFilteredStreamsCount,
                    hasActiveFilter = hasActiveFilter, onStreamSelected = onStreamSelected,
                    onDownloadSelected = onDownloadSelected,
                    onVerifyPlugin = onVerifyPlugin, onOpenSettings = onOpenSettings,
                    onRetryPlugin = onRetryPlugin,
                    onOpenHistory = onOpenHistory,
                    onOpenDownloads = onOpenDownloads,
                    onManualSearchClick = onManualSearchClick
                )
            } else {
                PortraitLayout(
                    title = title, posterUrl = posterUrl, episode = episode, season = season, isMovie = isMovie,
                    isDownloadMode = isDownloadMode,
                    imdbId = imdbId, accentColor = accentColor, addonStates = sortedAddonStates,
                    allStreams = allStreams, selectedAddonFilter = selectedAddonFilter,
                    searchQuery = searchQuery, onQueryChange = { searchQuery = it },
                    selectedTagFilter = selectedTagFilter, onTagSelect = { selectedTagFilter = it },
                    onAddonFilterChange = onAddonFilterChange, onBack = onBack,
                    isResolvingId = isResolvingId, idResolveFailed = idResolveFailed, isAnyLoading = isAnyLoading,
                    filteredAddonStates = filteredAddonStates, totalFilteredStreamsCount = totalFilteredStreamsCount,
                    hasActiveFilter = hasActiveFilter, onStreamSelected = onStreamSelected,
                    onDownloadSelected = onDownloadSelected,
                    onVerifyPlugin = onVerifyPlugin, onOpenSettings = onOpenSettings,
                    onRetryPlugin = onRetryPlugin,
                    onOpenHistory = onOpenHistory,
                    onOpenDownloads = onOpenDownloads,
                    onManualSearchClick = onManualSearchClick
                )
            }

            // ── Resolving overlay ────────────────────────────────────────────────
            if (resolvingSource != null) {
                BackHandler(enabled = true) {
                    onCancelResolving()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onCancelResolving() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .widthIn(max = 420.dp)
                            .padding(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* Kart içindeki dokunuşlar dışarı sızmasın */ }
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                IconButton(
                                    onClick = onCancelResolving,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.kapat),
                                        tint = KitsugiColors.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                CircularProgressIndicator(
                                    color = accentColor,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.Center)
                                )
                            }

                            val isDebrid = resolvingSource.infoHash != null || resolvingSource.url?.contains("magnet") == true
                            val message = if (isDebrid) stringResource(R.string.debrid_uzerinden_cozumleniyor) else stringResource(R.string.akis_baglantisi_cozuluyor)
                            Text(
                                text = message,
                                color = KitsugiColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = resolvingSource.title,
                                color = KitsugiColors.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(4.dp))

                            OutlinedButton(
                                onClick = onCancelResolving,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, KitsugiColors.SurfaceStrong),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = KitsugiColors.TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .tvClickable(shape = RoundedCornerShape(12.dp)) { onCancelResolving() }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = KitsugiColors.TextSecondary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.settings_cancel),
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ── Error dialog ─────────────────────────────────────────────────────
            if (resolvingError != null) {
                AlertDialog(
                    onDismissRequest = onResolvingErrorDismiss,
                    title = { Text(stringResource(R.string.hata), color = KitsugiColors.TextPrimary) },
                    text = { Text(resolvingError, color = KitsugiColors.TextSecondary) },
                    confirmButton = { TextButton(onClick = onResolvingErrorDismiss) { Text(stringResource(R.string.action_ok), color = accentColor) } },
                    containerColor = KitsugiColors.Surface
                )
            }

            // ── Player picker dialog ─────────────────────────────────────────────
            if (pendingPlayAction != null) {
                val playAction = pendingPlayAction
                var rememberChoice by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = onPendingDismiss,
                    containerColor = KitsugiColors.Surface, titleContentColor = KitsugiColors.TextPrimary,
                    textContentColor = KitsugiColors.TextSecondary, shape = RoundedCornerShape(26.dp),
                    title = { Text(stringResource(R.string.inline_choose_a_player_70a8dc8), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.inline_which_player_would_you_like_a9168aa), color = KitsugiColors.TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            PlayerPickerCard(
                                icon = { Icon(Icons.Rounded.PlayCircle, null, tint = accentColor) },
                                label = stringResource(R.string.player_engine_internal_exo), sub = stringResource(R.string.uygulama_ici_kesintisiz_oynatma),
                                accentColor = accentColor,
                                onClick = {
                                    if (rememberChoice) onRememberChoice("exoplayer")
                                    onPendingDone()
                                    launchPlayer(playAction.source, playAction.resolvedUrl, "exoplayer")
                                }
                            )
                            PlayerPickerCard(
                                icon = { Icon(Icons.AutoMirrored.Rounded.Launch, null, tint = accentColor) },
                                label = stringResource(R.string.harici_oynatici_chooser), sub = stringResource(R.string.mx_player_mi_video_veya_digerleri),
                                accentColor = accentColor,
                                onClick = {
                                    if (rememberChoice) onRememberChoice("mpv")
                                    onPendingDone()
                                    launchPlayer(playAction.source, playAction.resolvedUrl, "mpv")
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .tvClickable(shape = RoundedCornerShape(12.dp)) { rememberChoice = !rememberChoice }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it }, colors = CheckboxDefaults.colors(checkedColor = accentColor, uncheckedColor = KitsugiColors.TextMuted, checkmarkColor = KitsugiColors.Surface))
                                Text(stringResource(R.string.inline_remember_my_choice_a65f9af), color = KitsugiColors.TextPrimary)
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = onPendingDismiss) { Text(stringResource(R.string.settings_cancel), color = accentColor) } }
                )
            }
        }
    }
}

@Composable
private fun PlayerPickerCard(icon: @Composable () -> Unit, label: String, sub: String, accentColor: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().tvClickable(shape = RoundedCornerShape(16.dp)) { onClick() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(accentColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { icon() }
            Column { Text(label, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold); Text(sub, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Üst bar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Kaydırma ile yumuşakça gizlenen / geri gelen üst bar.
 *
 * NEDEN NESTED SCROLL: Önceki sürümde bar, `firstVisibleItemIndex >= 2` olunca
 * `AnimatedVisibility` ile aniden açılıp kapanıyordu; sticky başlığın yüksekliği bir anda
 * değiştiği için liste zıplıyordu. Burada bar, listenin kaydırma mesafesine **birebir**
 * bağlı olarak yukarı kayar (translationY) — parmakla birlikte akar, animasyon sıçraması
 * olmaz. Liste en başa dönerse bar kendini geri getirir.
 */
@Composable
private fun CollapsibleTopBarBox(
    listState: LazyListState,
    barHeight: Dp = TOP_BAR_HEIGHT,
    /**
     * v2.4.319: Yatay düzende sol kontrol rayı da bar ile birlikte kaymalı; aksi halde bar
     * gizlendiğinde rayın üstünde boş bir şerit kalıyordu. Aynı ofset paylaşılır.
     */
    offsetState: MutableFloatState? = null,
    bar: @Composable () -> Unit,
    content: @Composable (topContentPadding: Dp) -> Unit
) {
    val density = LocalDensity.current
    val barPx = with(density) { barHeight.toPx() }
    val offset = offsetState ?: remember { mutableFloatStateOf(0f) }
    val connection = remember(barPx, offset, listState) { HideOnScrollConnection(offset, barPx, listState) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 4 }
            .collect { atTop -> if (atTop) offset.floatValue = 0f }
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds().nestedScroll(connection)) {
        content(barHeight + 8.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .graphicsLayer {
                    translationY = -offset.floatValue
                    val progress = (offset.floatValue / barPx).coerceIn(0f, 1f)
                    alpha = 1f - progress * 0.25f
                }
        ) { bar() }
    }
}

/** Kaydırma deltasını bar ofsetine çeviren bağlantı. 0 = görünür, maxOffset = tamamen gizli. */
private class HideOnScrollConnection(
    private val offset: MutableFloatState,
    private val maxOffset: Float,
    private val listState: LazyListState
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy == 0f || maxOffset <= 0f) return Offset.Zero
        // Kısa listelerde (kaydırılamayan içerik) aşırı kaydırma barı gizlemesin.
        // Yalnızca liste gerçekten aşağı kayabiliyorsa gizleme tüketilir;
        // geri gösterme her durumda çalışır.
        if (dy < 0f && !listState.canScrollForward) return Offset.Zero
        val target = (offset.floatValue - dy).coerceIn(0f, maxOffset)
        val delta = target - offset.floatValue
        if (delta == 0f) return Offset.Zero
        offset.floatValue = target
        // Ofset arttıysa (bar yukarı kaydı) scroll deltasının o kısmını biz tükettik.
        return Offset(0f, -delta)
    }
}

@Composable
private fun StreamTopBar(
    title: String,
    subtitle: String,
    imdbId: String?,
    accentColor: Color,
    onBack: () -> Unit,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onManualSearchClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = KitsugiColors.Background.copy(alpha = 0.94f)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.55f),
                        contentColor = KitsugiColors.TextPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                imdbId?.let {
                    Spacer(Modifier.width(6.dp))
                    ImdbBadge(it)
                }
                trailing()
                // Kanonik sıra: Geçmiş → İndirmeler → Arama (uygulamanın her yerinde aynı
                // ortak üst çubuk butonları; bkz. KitsugiTopActionBar.kt).
                if (onOpenHistory != null) {
                    KitsugiTopActionButton(
                        action = KitsugiTopAction.HISTORY,
                        onClick = onOpenHistory,
                        size = 34.dp,
                        cornerRadius = 10.dp,
                        iconSize = 17.dp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
                if (onOpenDownloads != null) {
                    KitsugiTopActionButton(
                        action = KitsugiTopAction.DOWNLOADS,
                        onClick = onOpenDownloads,
                        size = 34.dp,
                        cornerRadius = 10.dp,
                        iconSize = 17.dp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
                if (onManualSearchClick != null) {
                    KitsugiTopActionButton(
                        action = KitsugiTopAction.SEARCH,
                        onClick = onManualSearchClick,
                        size = 34.dp,
                        cornerRadius = 10.dp,
                        iconSize = 17.dp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            HorizontalDivider(color = KitsugiColors.SurfaceStrong.copy(alpha = 0.45f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dikey (portrait)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortraitLayout(
    title: String, posterUrl: String?, episode: Int, season: Int, isMovie: Boolean = false,
    isDownloadMode: Boolean = false, imdbId: String?,
    accentColor: Color, addonStates: List<AddonFetchState>, allStreams: List<StreamSource>,
    selectedAddonFilter: String?, searchQuery: String, onQueryChange: (String) -> Unit,
    selectedTagFilter: String?, onTagSelect: (String?) -> Unit,
    onAddonFilterChange: (String?) -> Unit, onBack: () -> Unit,
    isResolvingId: Boolean, idResolveFailed: Boolean, isAnyLoading: Boolean,
    filteredAddonStates: List<AddonFetchState>, totalFilteredStreamsCount: Int, hasActiveFilter: Boolean,
    onStreamSelected: (StreamSource) -> Unit, onDownloadSelected: (StreamSource) -> Unit, onVerifyPlugin: ((String) -> Unit)?, onOpenSettings: (() -> Unit)?,
    onRetryPlugin: ((String) -> Unit)? = null,
    onOpenHistory: (() -> Unit)?,
    onOpenDownloads: (() -> Unit)? = null,
    onManualSearchClick: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val subtitle = mediaSubtitle(isMovie, isDownloadMode, season, episode)

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        CollapsibleTopBarBox(
            listState = listState,
            bar = {
                StreamTopBar(
                    title = title,
                    subtitle = subtitle,
                    imdbId = imdbId,
                    accentColor = accentColor,
                    onBack = onBack,
                    onOpenDownloads = onOpenDownloads,
                    onOpenHistory = onOpenHistory,
                    onManualSearchClick = onManualSearchClick
                )
            }
        ) { topPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = topPadding, bottom = 32.dp)
            ) {
                item(key = "search_bar") {
                    StreamSearchBar(
                        searchQuery = searchQuery,
                        onQueryChange = onQueryChange,
                        selectedTagFilter = selectedTagFilter,
                        onTagSelect = onTagSelect,
                        accentColor = accentColor
                    )
                }

                item(key = "addon_selector") {
                    AddonChipsRow(
                        addonStates = addonStates,
                        allStreams = allStreams,
                        selectedAddonFilter = selectedAddonFilter,
                        searchQuery = searchQuery,
                        accentColor = accentColor,
                        onFilterChange = onAddonFilterChange
                    )
                }

                item(key = "results_summary") {
                    ResultsSummaryRow(
                        videoCount = totalFilteredStreamsCount,
                        sourceCount = filteredAddonStates.count { it.streams.isNotEmpty() },
                        isLoading = isAnyLoading,
                        accentColor = accentColor,
                        hasActiveFilter = hasActiveFilter,
                        onClearFilters = {
                            onQueryChange("")
                            onTagSelect(null)
                            onAddonFilterChange(null)
                        }
                    )
                }

                renderStateOrResults(
                    isResolvingId = isResolvingId,
                    addonStates = addonStates,
                    isAnyLoading = isAnyLoading,
                    allStreams = allStreams,
                    totalFilteredStreamsCount = totalFilteredStreamsCount,
                    hasActiveFilter = hasActiveFilter,
                    searchQuery = searchQuery,
                    selectedTagFilter = selectedTagFilter,
                    accentColor = accentColor,
                    filteredAddonStates = filteredAddonStates,
                    posterUrl = posterUrl,
                    listState = listState,
                    onClearFilters = {
                        onQueryChange("")
                        onTagSelect(null)
                        onAddonFilterChange(null)
                    },
                    onOpenSettings = onOpenSettings,
                    onVerifyPlugin = onVerifyPlugin,
                    onRetryPlugin = onRetryPlugin,
                    onStreamSelected = onStreamSelected,
                    onDownloadSelected = onDownloadSelected
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Yatay (landscape)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LandscapeLayout(
    title: String, posterUrl: String?, episode: Int, season: Int, isMovie: Boolean = false,
    isDownloadMode: Boolean = false, imdbId: String?,
    accentColor: Color, addonStates: List<AddonFetchState>, allStreams: List<StreamSource>,
    selectedAddonFilter: String?, searchQuery: String, onQueryChange: (String) -> Unit,
    selectedTagFilter: String?, onTagSelect: (String?) -> Unit,
    onAddonFilterChange: (String?) -> Unit, onBack: () -> Unit,
    isResolvingId: Boolean, idResolveFailed: Boolean, isAnyLoading: Boolean,
    filteredAddonStates: List<AddonFetchState>, totalFilteredStreamsCount: Int, hasActiveFilter: Boolean,
    onStreamSelected: (StreamSource) -> Unit, onDownloadSelected: (StreamSource) -> Unit, onVerifyPlugin: ((String) -> Unit)?, onOpenSettings: (() -> Unit)?,
    onRetryPlugin: ((String) -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onManualSearchClick: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val subtitle = mediaSubtitle(isMovie, isDownloadMode, season, episode)
    val barOffset = remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        CollapsibleTopBarBox(
            listState = listState,
            offsetState = barOffset,
            bar = {
                StreamTopBar(
                    title = title,
                    subtitle = subtitle,
                    imdbId = imdbId,
                    accentColor = accentColor,
                    onBack = onBack,
                    onOpenDownloads = onOpenDownloads,
                    onOpenHistory = onOpenHistory,
                    onManualSearchClick = onManualSearchClick
                )
            }
        ) { topPadding ->
            Row(modifier = Modifier.fillMaxSize()) {
                // Sol kontrol rayı — bağımsız kaydırılabilir; üst bar gizlenirken o da
                // aynı ofsetle yukarı kayar (boşluk kalmaz).
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 240.dp, max = 300.dp)
                        .fillMaxWidth(0.34f)
                        .graphicsLayer { translationY = -barOffset.floatValue }
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 6.dp, top = topPadding, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StreamSearchBar(
                        searchQuery = searchQuery,
                        onQueryChange = onQueryChange,
                        selectedTagFilter = selectedTagFilter,
                        onTagSelect = onTagSelect,
                        accentColor = accentColor,
                        chipsHorizontally = false
                    )
                    AddonChipsRow(
                        addonStates = addonStates,
                        allStreams = allStreams,
                        selectedAddonFilter = selectedAddonFilter,
                        searchQuery = searchQuery,
                        accentColor = accentColor,
                        onFilterChange = onAddonFilterChange
                    )
                    ResultsSummaryRow(
                        videoCount = totalFilteredStreamsCount,
                        sourceCount = filteredAddonStates.count { it.streams.isNotEmpty() },
                        isLoading = isAnyLoading,
                        accentColor = accentColor,
                        hasActiveFilter = hasActiveFilter,
                        onClearFilters = {
                            onQueryChange("")
                            onTagSelect(null)
                            onAddonFilterChange(null)
                        }
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(top = topPadding, bottom = 24.dp)
                ) {
                    renderStateOrResults(
                        isResolvingId = isResolvingId,
                        addonStates = addonStates,
                        isAnyLoading = isAnyLoading,
                        allStreams = allStreams,
                        totalFilteredStreamsCount = totalFilteredStreamsCount,
                        hasActiveFilter = hasActiveFilter,
                        searchQuery = searchQuery,
                        selectedTagFilter = selectedTagFilter,
                        accentColor = accentColor,
                        filteredAddonStates = filteredAddonStates,
                        posterUrl = posterUrl,
                        listState = listState,
                        onClearFilters = {
                            onQueryChange("")
                            onTagSelect(null)
                            onAddonFilterChange(null)
                        },
                        onOpenSettings = onOpenSettings,
                        onVerifyPlugin = onVerifyPlugin,
                        onRetryPlugin = onRetryPlugin,
                        onStreamSelected = onStreamSelected,
                        onDownloadSelected = onDownloadSelected
                    )
                }
            }
        }
    }
}

/** Liste gövdesi: yükleme / boş / filtre boş / sonuç durumlarının tek kaynağı. */
@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.renderStateOrResults(
    isResolvingId: Boolean,
    addonStates: List<AddonFetchState>,
    isAnyLoading: Boolean,
    allStreams: List<StreamSource>,
    totalFilteredStreamsCount: Int,
    hasActiveFilter: Boolean,
    searchQuery: String,
    selectedTagFilter: String?,
    accentColor: Color,
    filteredAddonStates: List<AddonFetchState>,
    posterUrl: String?,
    listState: LazyListState,
    onClearFilters: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onVerifyPlugin: ((String) -> Unit)?,
    onRetryPlugin: ((String) -> Unit)?,
    onStreamSelected: (StreamSource) -> Unit,
    onDownloadSelected: (StreamSource) -> Unit
) {
    when {
        isResolvingId -> {
            item(key = "resolving_id") { ResolvingIdState() }
        }
        addonStates.isEmpty() && !isResolvingId -> {
            item(key = "no_addons") { NoAddonsState(onOpenSettings = onOpenSettings) }
        }
        !isAnyLoading && allStreams.isEmpty() -> {
            item(key = "empty_streams") { EmptyStreamsState(onOpenSettings = onOpenSettings) }
        }
        !isAnyLoading && totalFilteredStreamsCount == 0 && hasActiveFilter -> {
            item(key = "filtered_empty") {
                FilteredEmptyState(
                    searchQuery = searchQuery,
                    tagFilter = selectedTagFilter,
                    accentColor = accentColor,
                    onClearFilters = onClearFilters
                )
            }
        }
        else -> {
            renderStreamResultsItems(
                addonStates = filteredAddonStates,
                accentColor = accentColor,
                onStreamSelected = onStreamSelected,
                onDownloadSelected = onDownloadSelected,
                onVerifyPlugin = onVerifyPlugin,
                onOpenSettings = onOpenSettings,
                onRetryPlugin = onRetryPlugin,
                posterFallbackUrl = posterUrl,
                // Ölçüm yalnızca görüş alanındaki kartlarda yapılır (kasma önlemi).
                listState = listState
            )
        }
    }
}

@Composable
private fun mediaSubtitle(isMovie: Boolean, isDownloadMode: Boolean, season: Int, episode: Int): String =
    if (isMovie) {
        if (isDownloadMode) stringResource(R.string.ui2_movie_download) else stringResource(R.string.bangumi_tag_movie)
    } else {
        if (isDownloadMode) stringResource(R.string.ui2_stream_download_season_episode, season, episode)
        else stringResource(R.string.ui2_stream_season_episode, season, episode)
    }

// ─────────────────────────────────────────────────────────────────────────────
// Arama / etiketler / kaynak seçimi
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedTagFilter: String?,
    onTagSelect: (String?) -> Unit,
    accentColor: Color,
    chipsHorizontally: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    stringResource(R.string.kaynak_eklenti_veya_video_ara_dizilla_sibnet_dublaj),
                    color = KitsugiColors.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = if (searchQuery.isNotBlank()) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.temizle),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.55f),
                unfocusedContainerColor = KitsugiColors.Surface.copy(alpha = 0.55f),
                focusedBorderColor = accentColor.copy(alpha = 0.7f),
                unfocusedBorderColor = KitsugiColors.Border.copy(alpha = 0.5f),
                focusedTextColor = KitsugiColors.TextPrimary,
                unfocusedTextColor = KitsugiColors.TextPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )

        val tags = listOf(
            "altyazı" to stringResource(R.string.stream_tag_sub),
            "dublaj" to stringResource(R.string.stream_tag_dub),
            "2160p" to "4K",
            "1080p" to "1080p+",
            "720p" to "720p",
            "debrid" to "⚡ Debrid"
        )

        val chipContent: @Composable () -> Unit = {
            tags.forEach { (tagKey, tagLabel) ->
                val isSelected = selectedTagFilter == tagKey
                FilterChip(
                    selected = isSelected,
                    onClick = { onTagSelect(if (isSelected) null else tagKey) },
                    label = {
                        Text(
                            tagLabel,
                            fontSize = 11.sp,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.22f),
                        selectedLabelColor = accentColor,
                        containerColor = KitsugiColors.Surface.copy(alpha = 0.5f),
                        labelColor = KitsugiColors.TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = KitsugiColors.Border.copy(alpha = 0.45f),
                        selectedBorderColor = accentColor.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        if (chipsHorizontally) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                item(key = "chips") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chipContent() }
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) { chipContent() }
        }
    }
}

/**
 * Kaynak (eklenti) seçimi + sıralama. Tam ekran yüksekliğinde bir panel açmaz; tek satırlık
 * kompakt bir kart olarak kalır, detaylı seçim modal bottom sheet'te yapılır.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddonChipsRow(
    addonStates: List<AddonFetchState>,
    allStreams: List<StreamSource>,
    selectedAddonFilter: String?,
    searchQuery: String,
    accentColor: Color,
    onFilterChange: (String?) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var addonQuery by remember { mutableStateOf("") }
    val loadingCount = addonStates.count { it.isLoading }
    val readyCount = addonStates.count { it.streams.isNotEmpty() }
    val sort = LocalStreamSort.current

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = { showPicker = true },
            shape = RoundedCornerShape(16.dp),
            color = KitsugiColors.Surface.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        selectedAddonFilter ?: stringResource(R.string.stream_all_sources),
                        color = KitsugiColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.stream_source_summary, readyCount, addonStates.size, allStreams.size),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (loadingCount > 0) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = accentColor, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Rounded.ExpandMore, stringResource(R.string.stream_choose_source), tint = accentColor, modifier = Modifier.size(20.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = sort.toggle,
                shape = RoundedCornerShape(10.dp),
                color = KitsugiColors.Surface.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, KitsugiColors.Border.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(15.dp), tint = accentColor)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (sort.byQuality) R.string.stream_sort_quality else R.string.stream_sort_default),
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selectedAddonFilter != null) {
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { onFilterChange(null) }) {
                    Text(stringResource(R.string.stream_all_sources), color = accentColor, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            containerColor = KitsugiColors.Background
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.stream_choose_source),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.stream_source_summary, readyCount, addonStates.size, allStreams.size),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = addonQuery, onValueChange = { addonQuery = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.stream_search_addons), color = KitsugiColors.TextMuted) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = accentColor) },
                    trailingIcon = {
                        if (addonQuery.isNotEmpty()) IconButton(onClick = { addonQuery = "" }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.temizle))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = KitsugiColors.Border.copy(alpha = 0.5f)
                    )
                )
                val visible = remember(addonStates, addonQuery) {
                    addonStates.filter { it.addonName.contains(addonQuery.trim(), ignoreCase = true) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item(key = "all") {
                        SourcePickerRow(
                            name = stringResource(R.string.stream_all_sources),
                            status = stringResource(R.string.stream_video_count, allStreams.size),
                            selected = selectedAddonFilter == null,
                            accentColor = accentColor
                        ) {
                            onFilterChange(null); showPicker = false
                        }
                    }
                    items(visible, key = { it.addonName }) { state ->
                        val status = when {
                            state.isLoading -> stringResource(R.string.stream_source_loading)
                            state.requiresVerification -> stringResource(R.string.stream_source_verify)
                            state.error != null -> stringResource(R.string.stream_source_error)
                            else -> stringResource(R.string.stream_video_count, state.streams.size)
                        }
                        SourcePickerRow(
                            name = state.addonName,
                            status = status,
                            selected = selectedAddonFilter == state.addonName,
                            accentColor = accentColor
                        ) {
                            onFilterChange(state.addonName); showPicker = false
                        }
                    }
                    if (visible.isEmpty()) item {
                        Text(
                            stringResource(R.string.stream_no_addon_match),
                            modifier = Modifier.padding(16.dp),
                            color = KitsugiColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePickerRow(
    name: String, status: String, selected: Boolean, accentColor: Color, onClick: () -> Unit
) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name, color = KitsugiColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(status, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = accentColor)
        }
    }
}

@Composable
private fun ResultsSummaryRow(
    videoCount: Int,
    sourceCount: Int,
    isLoading: Boolean,
    accentColor: Color,
    hasActiveFilter: Boolean,
    onClearFilters: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(12.dp), color = accentColor, strokeWidth = 1.5.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = stringResource(R.string.stream_results_summary, videoCount, sourceCount),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (hasActiveFilter) {
            Text(
                text = stringResource(R.string.stream_filter_clear),
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClearFilters() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun FilteredEmptyState(
    searchQuery: String,
    tagFilter: String?,
    accentColor: Color,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FilterAltOff,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.stream_filter_empty_title),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            val desc = buildString {
                if (searchQuery.isNotBlank()) append("\"$searchQuery\" ")
                if (tagFilter != null) append("[$tagFilter] ")
                append(stringResource(R.string.stream_filter_empty_desc))
            }
            Text(
                text = desc,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            KitsugiButton(
                onClick = onClearFilters,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f), contentColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.FilterAltOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.stream_filter_clear), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ImdbBadge(imdbId: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF5C518))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            imdbId,
            color = Color.Black,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

private data class StreamSortState(val byQuality: Boolean, val toggle: () -> Unit)
private val LocalStreamSort = compositionLocalOf { StreamSortState(false) {} }
