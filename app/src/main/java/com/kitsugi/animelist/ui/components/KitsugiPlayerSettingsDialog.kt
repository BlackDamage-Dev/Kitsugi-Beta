package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.player.playerParityOptions
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Context
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.CustomButton
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.launch
import java.io.File

enum class PlayerSettingsSubScreen {
    Main,
    DahiliOynatici,
    Hareketler,
    KodCozucu,
    Altyazilar,
    Ses,
    OzelButonlar,
    KodDuzenleyici,
    Gelismis
}

/**
 * Oynatıcı Ayarları üst sekme bandı.
 *
 * Bölümler artık alt sayfa değil, **kaydırmalı sekme**: sağa/sola kaydırarak
 * (veya banda dokunarak) bölümler arasında geçilir — "Görünüm & Tercihler"
 * bandıyla aynı davranış. [Main] listesi kaldırıldığı için sekmeye karşılık gelmez.
 */
internal data class PlayerSettingsTab(
    val subScreen: PlayerSettingsSubScreen,
    val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

internal val PlayerSettingsTabs: List<PlayerSettingsTab> = listOf(
    PlayerSettingsTab(PlayerSettingsSubScreen.DahiliOynatici, R.string.pd_tab_internal, Icons.PlayCircle),
    PlayerSettingsTab(PlayerSettingsSubScreen.Hareketler, R.string.pd_sub_gestures, Icons.Swipe),
    PlayerSettingsTab(PlayerSettingsSubScreen.KodCozucu, R.string.pd_tab_decoder, Icons.Memory),
    PlayerSettingsTab(PlayerSettingsSubScreen.Altyazilar, R.string.pd_tab_subtitles, Icons.Subtitles),
    PlayerSettingsTab(PlayerSettingsSubScreen.Ses, R.string.pd_sub_audio, Icons.VolumeUp),
    PlayerSettingsTab(PlayerSettingsSubScreen.OzelButonlar, R.string.pd_tab_custom_buttons, Icons.SmartButton),
    PlayerSettingsTab(PlayerSettingsSubScreen.KodDuzenleyici, R.string.pd_tab_code_editor, Icons.Code),
    PlayerSettingsTab(PlayerSettingsSubScreen.Gelismis, R.string.pd_tab_advanced, Icons.Settings)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KitsugiPlayerSettingsDialog(
    playerPreference: String,
    preferredExternalPlayerPackage: String,
    isAutoplayEnabled: Boolean,
    skipIntroDurationSec: Int,
    defaultSubtitleSize: Int,
    defaultSubtitleColor: Int,
    subtitleBold: Boolean,
    subtitleOutlineEnabled: Boolean,
    defaultAudioBoost: Float,
    defaultAudioDelayMs: Long,
    minBufferMs: Int,
    maxBufferMs: Int,
    bufferForPlaybackMs: Int,
    bufferForPlaybackAfterRebufferMs: Int,
    backBufferDurationMs: Int,
    dv7HandlingMode: com.kitsugi.animelist.data.settings.Dv7HandlingMode,
    stripHdr10PlusSei: Boolean,
    preferredSubtitleLanguages: String,
    addonSubtitleStartupMode: String,
    qualityProfileJson: String,
    // T1.3 – Rota bazlı gecikme
    speakerDelayMs: Long = 0L,
    bluetoothDelayMs: Long = 0L,
    wiredDelayMs: Long = 0L,
    hdmiDelayMs: Long = 0L,
    activeAudioRoute: com.kitsugi.animelist.core.player.AudioRoute = com.kitsugi.animelist.core.player.AudioRoute.SPEAKER,
    onRouteDelayChanged: (speaker: Long, bluetooth: Long, wired: Long, hdmi: Long) -> Unit = { _, _, _, _ -> },
    onPlayerPreferenceSelected: (String) -> Unit,
    onPreferredExternalPlayerPackageSelected: (String) -> Unit,
    onAutoplayEnabledChanged: (Boolean) -> Unit,
    onSkipIntroDurationSecSelected: (Int) -> Unit,
    onDefaultSubtitleSizeSelected: (Int) -> Unit,
    onDefaultSubtitleColorSelected: (Int) -> Unit,
    onSubtitleBoldChanged: (Boolean) -> Unit,
    onSubtitleOutlineEnabledChanged: (Boolean) -> Unit,
    onDefaultAudioBoostSelected: (Float) -> Unit,
    onDefaultAudioDelayMsSelected: (Long) -> Unit,
    onPreferredSubtitleLanguagesSelected: (String) -> Unit,
    onAddonSubtitleStartupModeSelected: (String) -> Unit,
    onBufferSettingsChanged: (min: Int, max: Int, playback: Int, rebuffer: Int, back: Int) -> Unit,
    onDv7HandlingModeSelected: (com.kitsugi.animelist.data.settings.Dv7HandlingMode) -> Unit,
    onStripHdr10PlusSeiChanged: (Boolean) -> Unit,
    onQualityProfileSelected: (String) -> Unit,
    // T1.9
    parallelRangeEnabled: Boolean = false,
    onParallelRangeEnabledChanged: (Boolean) -> Unit = {},
    // T1.4
    frameRateMatchingMode: com.kitsugi.animelist.data.settings.FrameRateMatchingMode = com.kitsugi.animelist.data.settings.FrameRateMatchingMode.OFF,
    resolutionMatchingEnabled: Boolean = false,
    onFrameRateMatchingModeSelected: (com.kitsugi.animelist.data.settings.FrameRateMatchingMode) -> Unit = {},
    onResolutionMatchingEnabledChanged: (Boolean) -> Unit = {},
    // T2.1 + T2.7 – Gesture Ayarları
    gestureVolumeEnabled: Boolean = true,
    gestureBrightnessEnabled: Boolean = true,
    gestureZoomEnabled: Boolean = true,
    doubleTapSeekSeconds: Int = 10,
    holdSpeedMultiplier: Float = 2.0f,
    gestureScrollSensitivity: Float = 1.0f,
    onGestureVolumeEnabledChanged: (Boolean) -> Unit = {},
    onGestureBrightnessEnabledChanged: (Boolean) -> Unit = {},
    onGestureZoomEnabledChanged: (Boolean) -> Unit = {},
    onDoubleTapSeekSecondsSelected: (Int) -> Unit = {},
    onHoldSpeedMultiplierSelected: (Float) -> Unit = {},
    onGestureScrollSensitivityChanged: (Float) -> Unit = {},
    // T2.2 – Önizleme Seekbar
    previewSeekbarEnabled: Boolean = true,
    onPreviewSeekbarEnabledChanged: (Boolean) -> Unit = {},
    // T1.1 – Görüntü Oranı
    aspectMode: com.kitsugi.animelist.core.player.PlayerAspectMode = com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL,
    onAspectModeSelected: (com.kitsugi.animelist.core.player.PlayerAspectMode) -> Unit = {},
    liveHelperEnabled: Boolean = true,
    onLiveHelperEnabledChanged: (Boolean) -> Unit = {},
    enableAssExtractor: Boolean = true,
    onEnableAssExtractorChanged: (Boolean) -> Unit = {},
    showPlayerTitle: Boolean = true,
    onShowPlayerTitleChanged: (Boolean) -> Unit = {},
    showPlayerResolution: Boolean = true,
    onShowPlayerResolutionChanged: (Boolean) -> Unit = {},
    showMediaInfo: Boolean = true,
    onShowMediaInfoChanged: (Boolean) -> Unit = {},
    // ─── T1-01 – StillWatching + PostPlayMode + AutoplaySessionLimit ──────────
    stillWatchingEnabled: Boolean = true,
    onStillWatchingEnabledChanged: (Boolean) -> Unit = {},
    stillWatchingThresholdMinutes: Int = 90,
    onStillWatchingThresholdMinutesChanged: (Int) -> Unit = {},
    postPlayMode: String = "AUTO_PLAY_NEXT",
    onPostPlayModeChanged: (String) -> Unit = {},
    autoplaySessionLimit: Int = 0,
    onAutoplaySessionLimitChanged: (Int) -> Unit = {},
    // ─── T1-03 – Ses Gelişmiş ────────────────────────────────────────────────
    gainBoostDb: Float = 0f,
    onGainBoostDbChanged: (Float) -> Unit = {},
    subtitleDelayMs: Long = 0L,
    onSubtitleDelayMsChanged: (Long) -> Unit = {},
    // ─── T1-04 – Dekoder Önceliği (Telefon) ──────────────────────────────────
    decoderPriority: Int = 0,
    onDecoderPriorityChanged: (Int) -> Unit = {},
    embeddedMode: Boolean = false,
    initialSubScreen: PlayerSettingsSubScreen = PlayerSettingsSubScreen.Main,
    hideEmbeddedHeader: Boolean = false,
    onSubScreenChanged: (PlayerSettingsSubScreen) -> Unit = {},
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // SharedPreferences for direct setting fields
    val settingsDataStore = remember { SettingsDataStore(context) }
    val appSettingsFlow = remember { settingsDataStore.settingsFlow }
    val appSettings by appSettingsFlow.collectAsState(initial = null)

    // Database flow for Custom Buttons
    val db = remember { KitsugiDatabase.getDatabase(context) }
    val customButtonsFlow = remember { db.customButtonDao().subscribeAll() }
    val customButtons by customButtonsFlow.collectAsState(initial = emptyList())

    // ─── Üst sekme bandı + kaydırmalı sayfalar (Görünüm & Tercihler ile aynı mantık) ───
    val pagerState = rememberPagerState(
        initialPage = PlayerSettingsTabs.indexOfFirst { it.subScreen == initialSubScreen }.coerceAtLeast(0),
        pageCount = { PlayerSettingsTabs.size }
    )
    LaunchedEffect(initialSubScreen) {
        val target = PlayerSettingsTabs.indexOfFirst { it.subScreen == initialSubScreen }
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        onSubScreenChanged(PlayerSettingsTabs[pagerState.currentPage].subScreen)
    }

    // File Editor dialog states
    var editingFile by remember { mutableStateOf<File?>(null) }
    var fileContentText by remember { mutableStateOf("") }
    
    // Custom Button dialog states
    var showButtonEditDialog by remember { mutableStateOf<CustomButton?>(null) }
    var showButtonAddDialog by remember { mutableStateOf(false) }

    // Common Dropdown state placeholders
    var playerDropdownExpanded by remember { mutableStateOf(false) }
    var extPackageDropdownExpanded by remember { mutableStateOf(false) }
    var introDropdownExpanded by remember { mutableStateOf(false) }
    var stillWatchingDropdownExpanded by remember { mutableStateOf(false) }
    var postPlayDropdownExpanded by remember { mutableStateOf(false) }
    var autoplayLimitDropdownExpanded by remember { mutableStateOf(false) }
    
    var doubleTapSeekDropdownExpanded by remember { mutableStateOf(false) }
    var holdSpeedDropdownExpanded by remember { mutableStateOf(false) }
    
    var dvDropdownExpanded by remember { mutableStateOf(false) }
    var decoderDropdownExpanded by remember { mutableStateOf(false) }
    var mpvHwdecDropdownExpanded by remember { mutableStateOf(false) }
    var mpvGpuDropdownExpanded by remember { mutableStateOf(false) }
    var mpvDebandDropdownExpanded by remember { mutableStateOf(false) }
    
    var subSizeDropdownExpanded by remember { mutableStateOf(false) }
    var subColorDropdownExpanded by remember { mutableStateOf(false) }
    var subJustificationDropdownExpanded by remember { mutableStateOf(false) }
    var subBgColorDropdownExpanded by remember { mutableStateOf(false) }
    var subBorderColorDropdownExpanded by remember { mutableStateOf(false) }
    var subStartupDropdownExpanded by remember { mutableStateOf(false) }
    
    var volBoostCapDropdownExpanded by remember { mutableStateOf(false) }
    var audioBoostDropdownExpanded by remember { mutableStateOf(false) }
    var audioDelayDropdownExpanded by remember { mutableStateOf(false) }
    
    var bufferMinExp by remember { mutableStateOf(false) }
    var bufferMaxExp by remember { mutableStateOf(false) }
    var bufferPlayExp by remember { mutableStateOf(false) }
    var bufferRebExp by remember { mutableStateOf(false) }
    var bufferBackExp by remember { mutableStateOf(false) }

    BackHandler(enabled = embeddedMode) {
        onDismiss()
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true,
        embeddedMode = embeddedMode,
        sheetGesturesEnabled = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KitsugiColors.Surface)
        ) {
            if (!hideEmbeddedHeader) {
                // Header Content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (embeddedMode) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.ArrowBack,
                                    contentDescription = stringResource(R.string.notif_action_back),
                                    tint = KitsugiColors.TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.pd_title),
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!embeddedMode) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Close,
                                contentDescription = stringResource(R.string.settings_close),
                                tint = KitsugiColors.TextSecondary
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

            }

            // Üst sekme bandı — 8 bölüm, ekrana sığmazsa kendi içinde kaydırılır
            KitsugiSettingsSegmentedTabs(
                tabs = PlayerSettingsTabs.map {
                    KitsugiSettingsTabOption(title = stringResource(it.titleRes), icon = it.icon)
                },
                selectedIndex = pagerState.currentPage,
                accentColor = accentColor,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Body Content — sağa/sola kaydırma ile bölüm geçişi
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                // Her sekmenin kendi kaydırma konumu korunur
                val pageScrollState = rememberLazyListState()
                when (PlayerSettingsTabs[page].subScreen) {
                    // Not: "Main" menü listesi kaldırıldı; bölümler artık sekme bandından seçilir.
                    PlayerSettingsSubScreen.Main -> Unit
                    PlayerSettingsSubScreen.DahiliOynatici -> {
                        LazyColumn(
                            state = pageScrollState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_section_engine)) {
                                    // Player Preference Selection
                                    Box {
                                        val playerOptions = listOf(
                                            "MPV"      to stringResource(R.string.player_engine_internal_mpv),
                                            "INTERNAL" to stringResource(R.string.player_engine_internal_exo),
                                            "EXTERNAL" to stringResource(R.string.player_engine_external),
                                            "ASK"      to stringResource(R.string.player_engine_ask)
                                        )
                                        val currentPlayerName = playerOptions.find { it.first == playerPreference }?.second ?: stringResource(R.string.player_engine_internal_mpv)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_default_video_player),
                                            description = stringResource(R.string.pd_default_video_player_desc),
                                            value = currentPlayerName,
                                            icon = Icons.PlayArrow,
                                            iconColor = accentColor,
                                            onClick = { playerDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = playerDropdownExpanded, onDismissRequest = { playerDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_default_video_player)) {
                                            playerOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == playerPreference,
                                                    onClick = {
                                                        onPlayerPreferenceSelected(option.first)
                                                        playerDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (playerPreference == "EXTERNAL") {
                                        KitsugiSettingsDivider()
                                        Box {
                                            val extOptions = listOf(
                                                "" to stringResource(R.string.pd_not_selected),
                                                "com.mxtech.videoplayer.ad" to "MX Player",
                                                "com.mxtech.videoplayer.pro" to "MX Player Pro",
                                                "org.videolan.vlc" to "VLC Player",
                                                "is.xyz.mpv" to "MPV Player",
                                                "com.brouken.player" to "Just Player"
                                            )
                                            val currentExtName = extOptions.find { it.first == preferredExternalPlayerPackage }?.second ?: stringResource(R.string.pd_external_system_player)
                                            KitsugiSettingsListItem(
                                                title = stringResource(R.string.pd_external_player_app),
                                                description = stringResource(R.string.pd_external_player_app_desc),
                                                value = currentExtName,
                                                icon = Icons.SettingsInputComponent,
                                                iconColor = accentColor,
                                                onClick = { extPackageDropdownExpanded = true }
                                            )
                                            KitsugiDropdownMenu(expanded = extPackageDropdownExpanded, onDismissRequest = { extPackageDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_external_player_app)) {
                                                extOptions.forEach { option ->
                                                    KitsugiDropdownItem(
                                                        text = option.second,
                                                        selected = option.first == preferredExternalPlayerPackage,
                                                        onClick = {
                                                            onPreferredExternalPlayerPackageSelected(option.first)
                                                            extPackageDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_autoplay_transitions)) {
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_autoplay),
                                        description = stringResource(R.string.pd_autoplay_next),
                                        checked = isAutoplayEnabled,
                                        icon = Icons.FastForward,
                                        iconColor = accentColor,
                                        onCheckedChange = onAutoplayEnabledChanged
                                    )
                                    
                                    KitsugiSettingsDivider()
                                    
                                    Box {
                                        val introOptions = listOf(
                                            0 to stringResource(R.string.player_intro_off),
                                            3 to stringResource(R.string.player_intro_3),
                                            5 to stringResource(R.string.player_intro_5),
                                            10 to stringResource(R.string.player_intro_10),
                                            15 to stringResource(R.string.player_intro_15)
                                        )
                                        val currentIntroName = introOptions.find { it.first == skipIntroDurationSec }?.second ?: stringResource(R.string.player_intro_5)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_intro_skip_duration),
                                            description = stringResource(R.string.pd_intro_skip_desc),
                                            value = currentIntroName,
                                            icon = Icons.SkipNext,
                                            iconColor = accentColor,
                                            onClick = { introDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = introDropdownExpanded, onDismissRequest = { introDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_intro_skip_duration)) {
                                            introOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == skipIntroDurationSec,
                                                    onClick = {
                                                        onSkipIntroDurationSecSelected(option.first)
                                                        introDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_player_ui_info)) {
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_show_episode_title),
                                        description = stringResource(R.string.pd_show_episode_title_desc),
                                        checked = showPlayerTitle,
                                        icon = Icons.Title,
                                        iconColor = accentColor,
                                        onCheckedChange = onShowPlayerTitleChanged
                                    )
                                    KitsugiSettingsDivider()
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_show_resolution),
                                        description = stringResource(R.string.pd_show_resolution_desc),
                                        checked = showPlayerResolution,
                                        icon = Icons.Hd,
                                        iconColor = accentColor,
                                        onCheckedChange = onShowPlayerResolutionChanged
                                    )
                                    KitsugiSettingsDivider()
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_media_codec_info),
                                        description = stringResource(R.string.pd_show_media_info_desc),
                                        checked = showMediaInfo,
                                        icon = Icons.Info,
                                        iconColor = accentColor,
                                        onCheckedChange = onShowMediaInfoChanged
                                    )
                                }
                            }

                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_inactivity_rules)) {
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_still_watching),
                                        description = stringResource(R.string.pd_still_watching_desc),
                                        checked = stillWatchingEnabled,
                                        icon = Icons.QuestionMark,
                                        iconColor = accentColor,
                                        onCheckedChange = onStillWatchingEnabledChanged
                                    )
                                    if (stillWatchingEnabled) {
                                        KitsugiSettingsDivider()
                                        Box {
                                            val thresholdOptions = listOf(
                                                45 to stringResource(R.string.pd_threshold_45),
                                                90 to stringResource(R.string.pd_threshold_90),
                                                120 to stringResource(R.string.pd_threshold_120),
                                                180 to stringResource(R.string.pd_threshold_180)
                                            )
                                            val currentThresholdName = thresholdOptions.find { it.first == stillWatchingThresholdMinutes }?.second ?: "${stillWatchingThresholdMinutes} Dakika"
                                            KitsugiSettingsListItem(
                                                title = stringResource(R.string.player_inactivity_threshold),
                                                description = stringResource(R.string.pd_threshold_desc),
                                                value = currentThresholdName,
                                                icon = Icons.HourglassEmpty,
                                                iconColor = accentColor,
                                                onClick = { stillWatchingDropdownExpanded = true }
                                            )
                                            KitsugiDropdownMenu(expanded = stillWatchingDropdownExpanded, onDismissRequest = { stillWatchingDropdownExpanded = false }, dialogTitle = stringResource(R.string.player_inactivity_threshold)) {
                                                thresholdOptions.forEach { option ->
                                                    KitsugiDropdownItem(
                                                        text = option.second,
                                                        selected = option.first == stillWatchingThresholdMinutes,
                                                        onClick = {
                                                            onStillWatchingThresholdMinutesChanged(option.first)
                                                            stillWatchingDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    Box {
                                        val postOptions = listOf(
                                            "MANUAL" to stringResource(R.string.pd_post_play_manual),
                                            "AUTO_PLAY_NEXT" to stringResource(R.string.pd_post_play_direct),
                                            "BINGE_PROMPT" to stringResource(R.string.pd_post_play_ask)
                                        )
                                        val currentPostName = postOptions.find { it.first == postPlayMode }?.second ?: stringResource(R.string.pd_post_play_direct)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.player_post_play_mode),
                                            description = stringResource(R.string.player_post_play_mode_desc),
                                            value = currentPostName,
                                            icon = Icons.QueuePlayNext,
                                            iconColor = accentColor,
                                            onClick = { postPlayDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = postPlayDropdownExpanded, onDismissRequest = { postPlayDropdownExpanded = false }, dialogTitle = stringResource(R.string.player_post_play_mode)) {
                                            postOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == postPlayMode,
                                                    onClick = {
                                                        onPostPlayModeChanged(option.first)
                                                        postPlayDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    Box {
                                        val limitOptions = listOf(
                                            0 to stringResource(R.string.pd_session_unlimited),
                                            1 to stringResource(R.string.pd_session_1),
                                            2 to stringResource(R.string.pd_session_2),
                                            3 to stringResource(R.string.player_limit_3),
                                            5 to stringResource(R.string.player_limit_5)
                                        )
                                        val currentLimitName = limitOptions.find { it.first == autoplaySessionLimit }?.second ?: stringResource(R.string.pd_session_n, autoplaySessionLimit)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_autoplay_limit),
                                            description = stringResource(R.string.pd_session_limit_desc),
                                            value = currentLimitName,
                                            icon = Icons.Timer,
                                            iconColor = accentColor,
                                            onClick = { autoplayLimitDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = autoplayLimitDropdownExpanded, onDismissRequest = { autoplayLimitDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_autoplay_limit)) {
                                            limitOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == autoplaySessionLimit,
                                                    onClick = {
                                                        onAutoplaySessionLimitChanged(option.first)
                                                        autoplayLimitDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PlayerSettingsSubScreen.Hareketler -> {
                        LazyColumn(
                            state = pageScrollState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_scroll_gesture_controls)) {
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_volume_gesture),
                                        description = stringResource(R.string.pd_volume_gesture_desc),
                                        checked = gestureVolumeEnabled,
                                        icon = Icons.VolumeUp,
                                        iconColor = accentColor,
                                        onCheckedChange = onGestureVolumeEnabledChanged
                                    )
                                    KitsugiSettingsDivider()
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_brightness_gesture),
                                        description = stringResource(R.string.pd_brightness_gesture_desc),
                                        checked = gestureBrightnessEnabled,
                                        icon = Icons.BrightnessMedium,
                                        iconColor = accentColor,
                                        onCheckedChange = onGestureBrightnessEnabledChanged
                                    )
                                    KitsugiSettingsDivider()
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_pinch_zoom),
                                        description = stringResource(R.string.pd_pinch_zoom_desc),
                                        checked = gestureZoomEnabled,
                                        icon = Icons.ZoomIn,
                                        iconColor = accentColor,
                                        onCheckedChange = onGestureZoomEnabledChanged
                                    )
                                }
                            }

                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_gesture_sensitivities)) {
                                    // Scroll Sensitivity Slider
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.pd_scroll_sens, "%.1f".format(gestureScrollSensitivity)),
                                            color = KitsugiColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ResettableSlider(
                                            value = gestureScrollSensitivity,
                                            defaultValue = 1.0f,
                                            onValueChange = onGestureScrollSensitivityChanged,
                                            valueRange = 0.5f..2.5f
                                        )
                                    }

                                    KitsugiSettingsDivider()

                                    // Double Tap Seek Seconds selection
                                    Box {
                                        val seekSecOptions = listOf(5, 10, 15, 20, 30)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_double_tap_seek),
                                            description = stringResource(R.string.pd_double_tap_seek_desc),
                                            value = "${doubleTapSeekSeconds} Saniye",
                                            icon = Icons.DoubleArrow,
                                            iconColor = accentColor,
                                            onClick = { doubleTapSeekDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = doubleTapSeekDropdownExpanded, onDismissRequest = { doubleTapSeekDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_double_tap_seek)) {
                                            seekSecOptions.forEach { seconds ->
                                                KitsugiDropdownItem(
                                                    text = stringResource(R.string.pd_seconds, seconds),
                                                    selected = seconds == doubleTapSeekSeconds,
                                                    onClick = {
                                                        onDoubleTapSeekSecondsSelected(seconds)
                                                        doubleTapSeekDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    // Hold Speed Multiplier
                                    Box {
                                        val holdOptions = listOf(1.5f, 2.0f, 2.5f, 3.0f)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.player_hold_speed),
                                            description = stringResource(R.string.pd_hold_speed_desc),
                                            value = "${holdSpeedMultiplier}x",
                                            icon = Icons.Speed,
                                            iconColor = accentColor,
                                            onClick = { holdSpeedDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = holdSpeedDropdownExpanded, onDismissRequest = { holdSpeedDropdownExpanded = false }, dialogTitle = stringResource(R.string.player_hold_speed)) {
                                            holdOptions.forEach { multiplier ->
                                                KitsugiDropdownItem(
                                                    text = "${multiplier}x",
                                                    selected = multiplier == holdSpeedMultiplier,
                                                    onClick = {
                                                        onHoldSpeedMultiplierSelected(multiplier)
                                                        holdSpeedDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_advanced_gestures)) {
                                    val swipeSides = appSettings?.swipeVolumeBrightnessSides ?: true
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_invert_gestures),
                                        description = if (swipeSides) stringResource(R.string.pd_invert_off) else stringResource(R.string.pd_invert_on),
                                        checked = !swipeSides,
                                        icon = Icons.CompareArrows,
                                        iconColor = accentColor,
                                        onCheckedChange = {
                                            scope.launch {
                                                settingsDataStore.setSwipeVolumeBrightnessSides(!it)
                                            }
                                        }
                                    )

                                    KitsugiSettingsDivider()

                                    val horizontalSeek = appSettings?.horizontalSeekGestureEnabled ?: true
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_horizontal_seek_gesture),
                                        description = stringResource(R.string.pd_horizontal_seek),
                                        checked = horizontalSeek,
                                        icon = Icons.SettingsEthernet,
                                        iconColor = accentColor,
                                        onCheckedChange = {
                                            scope.launch {
                                                settingsDataStore.setHorizontalSeekGestureEnabled(it)
                                            }
                                        }
                                    )

                                    KitsugiSettingsDivider()

                                    val preciseSeek = appSettings?.preciseSeeking ?: false
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_precise_seek_mode),
                                        description = stringResource(R.string.pd_precise_seek),
                                        checked = preciseSeek,
                                        icon = Icons.CenterFocusWeak,
                                        iconColor = accentColor,
                                        onCheckedChange = {
                                            scope.launch {
                                                settingsDataStore.setPreciseSeeking(it)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    PlayerSettingsSubScreen.KodCozucu -> {
                        LazyColumn(
                            state = pageScrollState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {


                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_mpv_configs)) {
                                    Box {
                                        val hwdecOptions = listOf("auto", "auto-safe", "no")
                                        val currentHwdec = appSettings?.mpvHwdecMode ?: "auto-safe"
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_mpv_hwdec),
                                            description = stringResource(R.string.pd_mpv_hwdec_desc),
                                            value = currentHwdec,
                                            icon = Icons.Memory,
                                            iconColor = accentColor,
                                            onClick = { mpvHwdecDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = mpvHwdecDropdownExpanded, onDismissRequest = { mpvHwdecDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_mpv_hwdec)) {
                                            hwdecOptions.forEach { mode ->
                                                KitsugiDropdownItem(
                                                    text = mode,
                                                    selected = mode == currentHwdec,
                                                    onClick = {
                                                        scope.launch { settingsDataStore.setMpvHwdecMode(mode) }
                                                        mpvHwdecDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    Box {
                                        val gpuOptions = listOf("gpu", "gpu-next")
                                        val currentGpu = appSettings?.mpvGpuRenderer ?: "gpu"
                                        KitsugiSettingsListItem(
                                            title = "MPV GPU Renderer Backend",
                                            description = stringResource(R.string.pd_mpv_gpu_desc),
                                            value = currentGpu,
                                            icon = Icons.SettingsApplications,
                                            iconColor = accentColor,
                                            onClick = { mpvGpuDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = mpvGpuDropdownExpanded, onDismissRequest = { mpvGpuDropdownExpanded = false }, dialogTitle = "MPV GPU Renderer Backend") {
                                            gpuOptions.forEach { renderer ->
                                                KitsugiDropdownItem(
                                                    text = renderer,
                                                    selected = renderer == currentGpu,
                                                    onClick = {
                                                        scope.launch { settingsDataStore.setMpvGpuRenderer(renderer) }
                                                        mpvGpuDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    val dvAutoFix = appSettings?.mpvDolbyVisionAutoFix ?: true
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_mpv_dv_autofix),
                                        description = stringResource(R.string.pd_mpv_dv_autofix_desc),
                                        checked = dvAutoFix,
                                        icon = Icons.Rounded.HdrOn,
                                        iconColor = accentColor,
                                        onCheckedChange = {
                                            scope.launch { settingsDataStore.setMpvDolbyVisionAutoFix(it) }
                                        }
                                    )

                                    KitsugiSettingsDivider()

                                    Box {
                                        val debandOptions = listOf("none", "cpu", "gpu")
                                        val currentDeband = appSettings?.mpvDebandMode ?: "none"
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_mpv_deband),
                                            description = stringResource(R.string.pd_mpv_deband_desc),
                                            value = currentDeband,
                                            icon = Icons.BlurOn,
                                            iconColor = accentColor,
                                            onClick = { mpvDebandDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = mpvDebandDropdownExpanded, onDismissRequest = { mpvDebandDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_mpv_deband)) {
                                            debandOptions.forEach { mode ->
                                                KitsugiDropdownItem(
                                                    text = mode,
                                                    selected = mode == currentDeband,
                                                    onClick = {
                                                        scope.launch { settingsDataStore.setMpvDebandMode(mode) }
                                                        mpvDebandDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    val forceYuv = appSettings?.mpvForceYuv420p ?: false
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_mpv_yuv),
                                        description = stringResource(R.string.pd_mpv_yuv_desc),
                                        checked = forceYuv,
                                        icon = Icons.ColorLens,
                                        iconColor = accentColor,
                                        onCheckedChange = {
                                            scope.launch { settingsDataStore.setMpvForceYuv420p(it) }
                                        }
                                    )

                                    KitsugiSettingsDivider()

                                    val demuxerCache = appSettings?.mpvDemuxerCacheMb ?: 64
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.pd_mpv_demuxer, demuxerCache),
                                            color = KitsugiColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ResettableSlider(
                                            value = demuxerCache.toFloat(),
                                            defaultValue = 64f,
                                            onValueChange = { scope.launch { settingsDataStore.setMpvDemuxerCacheMb(it.toInt()) } },
                                            valueRange = 8f..512f,
                                            steps = 63
                                        )
                                    }
                                }
                            }


                        }
                    }

                    PlayerSettingsSubScreen.Altyazilar -> {
                        LazyColumn(
                            state = pageScrollState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_sub_style)) {
                                    Box {
                                        val sizeOptions = listOf(
                                            12 to stringResource(R.string.sub_size_12),
                                            14 to stringResource(R.string.sub_size_14),
                                            16 to stringResource(R.string.sub_size_16),
                                            18 to stringResource(R.string.sub_size_18),
                                            20 to stringResource(R.string.sub_size_20),
                                            24 to stringResource(R.string.sub_size_24)
                                        )
                                        val currentSizeLabel = sizeOptions.find { it.first == defaultSubtitleSize }?.second ?: "${defaultSubtitleSize}sp"
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_sub_font_size),
                                            description = stringResource(R.string.pd_sub_font_size_desc),
                                            value = currentSizeLabel,
                                            icon = Icons.TextFields,
                                            iconColor = accentColor,
                                            onClick = { subSizeDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = subSizeDropdownExpanded, onDismissRequest = { subSizeDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_sub_font_size)) {
                                            sizeOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == defaultSubtitleSize,
                                                    onClick = {
                                                        onDefaultSubtitleSizeSelected(option.first)
                                                        subSizeDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    Box {
                                        val colorOptions = listOf(
                                            0xFFFFFFFF.toInt() to stringResource(R.string.sub_color_white),
                                            0xFFFFFF00.toInt() to stringResource(R.string.color_yellow),
                                            0xFF00FF00.toInt() to stringResource(R.string.color_green),
                                            0xFF00FFFF.toInt() to stringResource(R.string.color_blue),
                                            0xFFFF0000.toInt() to stringResource(R.string.color_red)
                                        )
                                        val currentColorLabel = colorOptions.find { it.first == defaultSubtitleColor }?.second ?: stringResource(R.string.pd_custom)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_sub_font_color),
                                            description = stringResource(R.string.pd_sub_font_color_desc),
                                            value = currentColorLabel,
                                            icon = Icons.ColorLens,
                                            iconColor = accentColor,
                                            onClick = { subColorDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = subColorDropdownExpanded, onDismissRequest = { subColorDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_sub_font_color)) {
                                            colorOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == defaultSubtitleColor,
                                                    onClick = {
                                                        onDefaultSubtitleColorSelected(option.first)
                                                        subColorDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_sub_bold),
                                        description = stringResource(R.string.pd_sub_bold_desc),
                                        checked = subtitleBold,
                                        icon = Icons.FormatBold,
                                        iconColor = accentColor,
                                        onCheckedChange = onSubtitleBoldChanged
                                    )

                                    KitsugiSettingsDivider()

                                    val italicSub = appSettings?.subtitleItalic ?: false
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_sub_italic),
                                        description = stringResource(R.string.pd_sub_italic_desc),
                                        checked = italicSub,
                                        icon = Icons.FormatItalic,
                                        iconColor = accentColor,
                                        onCheckedChange = {
                                            scope.launch { settingsDataStore.setSubtitleItalic(it) }
                                        }
                                    )

                                    KitsugiSettingsDivider()

                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_sub_outline),
                                        description = stringResource(R.string.pd_sub_outline_desc),
                                        checked = subtitleOutlineEnabled,
                                        icon = Icons.FormatPaint,
                                        iconColor = accentColor,
                                        onCheckedChange = onSubtitleOutlineEnabledChanged
                                    )

                                    KitsugiSettingsDivider()

                                    Box {
                                        val justificationOptions = listOf(
                                            "left" to stringResource(R.string.pd_left),
                                            "center" to stringResource(R.string.pd_align_mid),
                                            "right" to stringResource(R.string.pd_align_right)
                                        )
                                        val currentJust = appSettings?.subtitleJustification ?: "center"
                                        val currentJustLabel = justificationOptions.find { it.first == currentJust }?.second ?: "Orta"
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_sub_alignment),
                                            description = stringResource(R.string.pd_sub_alignment_desc),
                                            value = currentJustLabel,
                                            icon = Icons.FormatAlignLeft,
                                            iconColor = accentColor,
                                            onClick = { subJustificationDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = subJustificationDropdownExpanded, onDismissRequest = { subJustificationDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_sub_alignment)) {
                                            justificationOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == currentJust,
                                                    onClick = {
                                                        scope.launch { settingsDataStore.setSubtitleJustification(option.first) }
                                                        subJustificationDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    Box {
                                        val bgColors = listOf(
                                            0 to stringResource(R.string.pd_bg_transparent),
                                            0xFF000000.toInt() to stringResource(R.string.pd_black),
                                            0x80000000.toInt() to stringResource(R.string.pd_bg_semi_black)
                                        )
                                        val currentBg = appSettings?.subtitleBackgroundColor ?: 0
                                        val currentBgLabel = bgColors.find { it.first == currentBg }?.second ?: stringResource(R.string.pd_custom)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_bg_color),
                                            description = stringResource(R.string.pd_bg_color_desc),
                                            value = currentBgLabel,
                                            icon = Icons.SelectAll,
                                            iconColor = accentColor,
                                            onClick = { subBgColorDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = subBgColorDropdownExpanded, onDismissRequest = { subBgColorDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_bg_color)) {
                                            bgColors.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == currentBg,
                                                    onClick = {
                                                        scope.launch { settingsDataStore.setSubtitleBackgroundColor(option.first) }
                                                        subBgColorDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    val shadowOffset = appSettings?.subtitleShadowOffset ?: 1.5f
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.pd_shadow_offset, "%.1f".format(shadowOffset)),
                                            color = KitsugiColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ResettableSlider(
                                            value = shadowOffset,
                                            defaultValue = 1.5f,
                                            onValueChange = { scope.launch { settingsDataStore.setSubtitleShadowOffset(it) } },
                                            valueRange = 0f..8f
                                        )
                                    }

                                    KitsugiSettingsDivider()

                                    Box {
                                        val borderColors = listOf(
                                            0xFF000000.toInt() to stringResource(R.string.pd_border_black),
                                            0xFFFFFFFF.toInt() to stringResource(R.string.sub_color_white)
                                        )
                                        val currentBorderColor = appSettings?.subtitleBorderColor ?: 0xFF000000.toInt()
                                        val currentBorderColorLabel = borderColors.find { it.first == currentBorderColor }?.second ?: stringResource(R.string.pd_custom)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_border_color),
                                            description = stringResource(R.string.pd_border_color_desc),
                                            value = currentBorderColorLabel,
                                            icon = Icons.BorderOuter,
                                            iconColor = accentColor,
                                            onClick = { subBorderColorDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = subBorderColorDropdownExpanded, onDismissRequest = { subBorderColorDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_border_color)) {
                                            borderColors.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == currentBorderColor,
                                                    onClick = {
                                                        scope.launch { settingsDataStore.setSubtitleBorderColor(option.first) }
                                                        subBorderColorDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    val borderSize = appSettings?.subtitleBorderSize ?: 1.5f
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.pd_border_size, "%.1f".format(borderSize)),
                                            color = KitsugiColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ResettableSlider(
                                            value = borderSize,
                                            defaultValue = 1.5f,
                                            onValueChange = { scope.launch { settingsDataStore.setSubtitleBorderSize(it) } },
                                            valueRange = 0f..6f
                                        )
                                    }
                                }
                            }

                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_sub_lang_prefs)) {
                                    // ── Chip tabanlı çok-seçimli dil seçici ──────────────────
                                    // Dil kodları ISO 639-1 standardında virgülle ayrılmış string
                                    // olarak kaydedilir (örn: "tr,en"). Varsayılan: "tr"
                                    val allSubtitleLanguages = listOf(
                                        "tr" to stringResource(R.string.pd_lang_tr),
                                        "en" to stringResource(R.string.option_title_lang_english),
                                        "ja" to stringResource(R.string.option_title_lang_native),
                                        "ar" to stringResource(R.string.pd_lang_ar),
                                        "zh" to stringResource(R.string.pd_lang_zh),
                                        "ko" to stringResource(R.string.pd_lang_ko),
                                        "fr" to stringResource(R.string.pd_lang_fr),
                                        "de" to stringResource(R.string.pd_lang_de),
                                        "es" to stringResource(R.string.pd_lang_es),
                                        "pt" to stringResource(R.string.pd_lang_pt),
                                        "it" to stringResource(R.string.pd_lang_it),
                                        "ru" to stringResource(R.string.pd_lang_ru),
                                        "nl" to stringResource(R.string.pd_lang_nl),
                                        "pl" to stringResource(R.string.pd_lang_pl),
                                        "sv" to stringResource(R.string.pd_lang_sv),
                                        "no" to stringResource(R.string.pd_lang_no),
                                        "da" to stringResource(R.string.pd_lang_da),
                                        "fi" to stringResource(R.string.pd_lang_fi),
                                        "uk" to stringResource(R.string.pd_lang_uk),
                                        "ro" to stringResource(R.string.pd_lang_ro),
                                        "cs" to stringResource(R.string.pd_lang_cs),
                                        "hu" to stringResource(R.string.pd_lang_hu),
                                        "he" to stringResource(R.string.pd_lang_he),
                                        "id" to stringResource(R.string.pd_lang_id),
                                        "th" to stringResource(R.string.pd_lang_th),
                                        "vi" to stringResource(R.string.pd_lang_vi)
                                    )
                                    val selectedLangs = remember(preferredSubtitleLanguages) {
                                        mutableStateOf(
                                            preferredSubtitleLanguages
                                                .split(",")
                                                .map { it.trim().lowercase() }
                                                .filter { it.isNotBlank() }
                                                .toMutableSet()
                                        )
                                    }

                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Language,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.sub_preferred_langs),
                                                    color = KitsugiColors.TextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = if (selectedLangs.value.isEmpty())
                                                        stringResource(R.string.pd_lang_none)
                                                    else
                                                        stringResource(R.string.pd_lang_selection_info, selectedLangs.value.joinToString(", ").uppercase()),
                                                    color = KitsugiColors.TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        // Chip grid
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            allSubtitleLanguages.forEach { (code, label) ->
                                                val isSelected = code in selectedLangs.value
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        val updated = selectedLangs.value.toMutableSet()
                                                        if (isSelected) {
                                                            updated.remove(code)
                                                        } else {
                                                            updated.add(code)
                                                        }
                                                        selectedLangs.value = updated
                                                        val saved = updated.joinToString(",")
                                                        onPreferredSubtitleLanguagesSelected(saved)
                                                    },
                                                    label = {
                                                        Text(
                                                            text = "$code · $label",
                                                            fontSize = 12.sp,
                                                            color = if (isSelected) accentColor else KitsugiColors.TextSecondary
                                                        )
                                                    },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                                        selectedLabelColor = accentColor,
                                                        containerColor = KitsugiColors.Surface
                                                    ),
                                                    border = FilterChipDefaults.filterChipBorder(
                                                        enabled = true,
                                                        selected = isSelected,
                                                        selectedBorderColor = accentColor,
                                                        borderColor = KitsugiColors.TextSecondary.copy(alpha = 0.3f)
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    Box {
                                        val startupModeOptions = listOf(
                                            "ALL_SUBTITLES" to stringResource(R.string.sub_load_all),
                                            "PREFERRED_ONLY" to stringResource(R.string.sub_load_preferred)
                                        )
                                        val currentStartupModeName = startupModeOptions.find { it.first == addonSubtitleStartupMode }?.second ?: stringResource(R.string.sub_load_preferred)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_sub_load_mode),
                                            description = stringResource(R.string.pd_sub_load_mode_desc),
                                            value = currentStartupModeName,
                                            icon = Icons.FilterList,
                                            iconColor = accentColor,
                                            onClick = { subStartupDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = subStartupDropdownExpanded, onDismissRequest = { subStartupDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_sub_load_mode)) {
                                            startupModeOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == addonSubtitleStartupMode,
                                                    onClick = {
                                                        onAddonSubtitleStartupModeSelected(option.first)
                                                        subStartupDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PlayerSettingsSubScreen.Ses -> {
                        LazyColumn(
                            state = pageScrollState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_audio_boost)) {
                                    Box {
                                        val capOptions = listOf(100, 120, 150, 180, 200)
                                        val currentCap = appSettings?.volumeBoostCap ?: 200
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_volume_cap),
                                            description = stringResource(R.string.pd_volume_cap_desc),
                                            value = "%$currentCap",
                                            icon = Icons.Equalizer,
                                            iconColor = accentColor,
                                            onClick = { volBoostCapDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = volBoostCapDropdownExpanded, onDismissRequest = { volBoostCapDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_volume_cap)) {
                                            capOptions.forEach { cap ->
                                                KitsugiDropdownItem(
                                                    text = "%$cap",
                                                    selected = cap == currentCap,
                                                    onClick = {
                                                        scope.launch { settingsDataStore.setVolumeBoostCap(cap) }
                                                        volBoostCapDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    KitsugiSettingsDivider()

                                    Box {
                                        val boostOptions = listOf(
                                            0.0f to stringResource(R.string.sub_opacity_normal),
                                            0.25f to stringResource(R.string.sub_opacity_low),
                                            0.5f to stringResource(R.string.sub_opacity_mid),
                                            0.75f to stringResource(R.string.sub_opacity_high),
                                            1.0f to stringResource(R.string.sub_opacity_max)
                                        )
                                        val currentBoostName = boostOptions.find { it.first == defaultAudioBoost }?.second ?: stringResource(R.string.sub_opacity_normal)
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.pd_default_boost),
                                            description = stringResource(R.string.pd_default_boost_desc),
                                            value = currentBoostName,
                                            icon = Icons.VolumeUp,
                                            iconColor = accentColor,
                                            onClick = { audioBoostDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = audioBoostDropdownExpanded, onDismissRequest = { audioBoostDropdownExpanded = false }, dialogTitle = stringResource(R.string.pd_default_boost)) {
                                            boostOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == defaultAudioBoost,
                                                    onClick = {
                                                        onDefaultAudioBoostSelected(option.first)
                                                        audioBoostDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_section_audio_delay)) {
                                    Box {
                                        val delayOptions = listOf(
                                            0L to stringResource(R.string.sub_delay_ontime),
                                            -100L to "-100 ms",
                                            -200L to "-200 ms",
                                            -300L to "-300 ms",
                                            -500L to "-500 ms",
                                            100L to "+100 ms",
                                            200L to "+200 ms",
                                            300L to "+300 ms",
                                            500L to "+500 ms"
                                        )
                                        val currentDelayName = delayOptions.find { it.first == defaultAudioDelayMs }?.second ?: "${defaultAudioDelayMs} ms"
                                        KitsugiSettingsListItem(
                                            title = stringResource(R.string.sub_audio_delay),
                                            description = stringResource(R.string.pd_default_audio_delay_desc),
                                            value = currentDelayName,
                                            icon = Icons.AvTimer,
                                            iconColor = accentColor,
                                            onClick = { audioDelayDropdownExpanded = true }
                                        )
                                        KitsugiDropdownMenu(expanded = audioDelayDropdownExpanded, onDismissRequest = { audioDelayDropdownExpanded = false }, dialogTitle = stringResource(R.string.sub_audio_delay)) {
                                            delayOptions.forEach { option ->
                                                KitsugiDropdownItem(
                                                    text = option.second,
                                                    selected = option.first == defaultAudioDelayMs,
                                                    onClick = {
                                                        onDefaultAudioDelayMsSelected(option.first)
                                                        audioDelayDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_route_delays)) {
                                    // Speaker Delay Slider
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.pd_speaker_delay, speakerDelayMs),
                                            color = KitsugiColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ResettableSlider(
                                            value = speakerDelayMs.toFloat(),
                                            defaultValue = 0f,
                                            onValueChange = { onRouteDelayChanged(it.toLong(), bluetoothDelayMs, wiredDelayMs, hdmiDelayMs) },
                                            valueRange = -1000f..1000f
                                        )
                                    }

                                    KitsugiSettingsDivider()

                                    // Bluetooth Delay Slider
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.pd_bt_delay, bluetoothDelayMs),
                                            color = KitsugiColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ResettableSlider(
                                            value = bluetoothDelayMs.toFloat(),
                                            defaultValue = 0f,
                                            onValueChange = { onRouteDelayChanged(speakerDelayMs, it.toLong(), wiredDelayMs, hdmiDelayMs) },
                                            valueRange = -1000f..1000f
                                        )
                                    }

                                    KitsugiSettingsDivider()

                                    // Wired Headphones Delay Slider
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.pd_wired_delay, wiredDelayMs),
                                            color = KitsugiColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ResettableSlider(
                                            value = wiredDelayMs.toFloat(),
                                            defaultValue = 0f,
                                            onValueChange = { onRouteDelayChanged(speakerDelayMs, bluetoothDelayMs, it.toLong(), hdmiDelayMs) },
                                            valueRange = -1000f..1000f
                                        )
                                    }

                                    KitsugiSettingsDivider()

                                    // HDMI Output Delay Slider
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.pd_hdmi_delay, hdmiDelayMs),
                                            color = KitsugiColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ResettableSlider(
                                            value = hdmiDelayMs.toFloat(),
                                            defaultValue = 0f,
                                            onValueChange = { onRouteDelayChanged(speakerDelayMs, bluetoothDelayMs, wiredDelayMs, it.toLong()) },
                                            valueRange = -1000f..1000f
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PlayerSettingsSubScreen.OzelButonlar -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            KitsugiButton(
                                onClick = { showButtonAddDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Icon(Icons.Add, contentDescription = null, tint = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.pd_add_custom_button), color = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor))
                            }

                            LazyColumn(
                                state = pageScrollState,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (customButtons.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                stringResource(R.string.pd_no_custom_buttons),
                                                color = KitsugiColors.TextSecondary
                                            )
                                        }
                                    }
                                } else {
                                    items(customButtons, key = { it.id }) { btn ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = KitsugiColors.SurfaceSoft
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = btn.name,
                                                        color = KitsugiColors.TextPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.pd_button_click, btn.content),
                                                        color = KitsugiColors.TextSecondary,
                                                        maxLines = 1,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { showButtonEditDialog = btn }) {
                                                        Icon(
                                                            Icons.Edit,
                                                            contentDescription = stringResource(R.string.pd_edit),
                                                            tint = KitsugiColors.TextSecondary
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                db.customButtonDao().delete(btn.id)
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            Icons.Delete,
                                                            contentDescription = stringResource(R.string.action_delete),
                                                            tint = accentColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PlayerSettingsSubScreen.KodDuzenleyici -> {
                        var scriptFolderState by remember { mutableStateOf("scripts") }
                        val scriptsDir = remember(context) { File(context.filesDir, "scripts").apply { mkdirs() } }
                        val scriptOptsDir = remember(context) { File(context.filesDir, "script-opts").apply { mkdirs() } }
                        val activeDir = if (scriptFolderState == "scripts") scriptsDir else scriptOptsDir
                        
                        var filesList by remember { mutableStateOf<List<File>>(emptyList()) }
                        var showCreateFileDialog by remember { mutableStateOf(false) }

                        LaunchedEffect(scriptFolderState) {
                            filesList = activeDir.listFiles()?.toList() ?: emptyList()
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Folder Tab Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KitsugiButton(
                                    onClick = { scriptFolderState = "scripts" },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (scriptFolderState == "scripts") accentColor else KitsugiColors.SurfaceSoft
                                    )
                                ) {
                                    Text(
                                        "Scripts (Lua)",
                                        color = if (scriptFolderState == "scripts") Color.White else KitsugiColors.TextPrimary
                                    )
                                }
                                KitsugiButton(
                                    onClick = { scriptFolderState = "script-opts" },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (scriptFolderState == "script-opts") accentColor else KitsugiColors.SurfaceSoft
                                    )
                                ) {
                                    Text(
                                        "Script Options (Conf)",
                                        color = if (scriptFolderState == "script-opts") Color.White else KitsugiColors.TextPrimary
                                    )
                                }
                            }

                            KitsugiButton(
                                onClick = { showCreateFileDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Icon(Icons.Add, contentDescription = null, tint = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.pd_new_file), color = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor))
                            }

                            LazyColumn(
                                state = pageScrollState,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (filesList.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                stringResource(R.string.bu_dizinde_dosya_bulunmuyor),
                                                color = KitsugiColors.TextSecondary
                                            )
                                        }
                                    }
                                } else {
                                    items(filesList) { file ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = KitsugiColors.SurfaceSoft
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = file.name,
                                                    color = KitsugiColors.TextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Row {
                                                    IconButton(
                                                        onClick = {
                                                            editingFile = file
                                                            fileContentText = file.readText()
                                                        }
                                                    ) {
                                                        Icon(
                                                            Icons.Edit,
                                                            contentDescription = stringResource(R.string.pd_edit),
                                                            tint = KitsugiColors.TextSecondary
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            file.delete()
                                                            filesList = activeDir.listFiles()?.toList() ?: emptyList()
                                                        }
                                                    ) {
                                                        Icon(
                                                            Icons.Delete,
                                                            contentDescription = stringResource(R.string.action_delete),
                                                            tint = accentColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Create file dialog
                        if (showCreateFileDialog) {
                            var newFileName by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { showCreateFileDialog = false },
                                containerColor = KitsugiColors.Surface,
                                title = { Text(stringResource(R.string.pd_new_file), color = KitsugiColors.TextPrimary) },
                                text = {
                                    OutlinedTextField(
                                        value = newFileName,
                                        onValueChange = { newFileName = it },
                                        label = { Text(stringResource(R.string.pd_file_name)) },
                                        placeholder = { Text(if (scriptFolderState == "scripts") "script.lua" else "opts.conf") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accentColor,
                                            focusedLabelColor = accentColor,
                                            focusedTextColor = KitsugiColors.TextPrimary,
                                            unfocusedTextColor = KitsugiColors.TextPrimary
                                        )
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            if (newFileName.isNotBlank()) {
                                                val suffix = if (scriptFolderState == "scripts") ".lua" else ".conf"
                                                val finalName = if (newFileName.endsWith(suffix)) newFileName else newFileName + suffix
                                                File(activeDir, finalName).writeText("")
                                                filesList = activeDir.listFiles()?.toList() ?: emptyList()
                                            }
                                            showCreateFileDialog = false
                                        }
                                    ) {
                                        Text(stringResource(R.string.pd_create), color = accentColor)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCreateFileDialog = false }) {
                                        Text(stringResource(R.string.pd_cancel), color = KitsugiColors.TextSecondary)
                                    }
                                }
                            )
                        }
                    }

                    PlayerSettingsSubScreen.Gelismis -> {
                        LazyColumn(
                            state = pageScrollState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            appSettings?.let { settings ->
                                playerParityOptions(settings, settingsDataStore, scope)
                            }
                            item {
                                KitsugiSettingsSection(title = stringResource(R.string.pd_user_mpv_files)) {
                                    val prefs = remember(context) { context.getSharedPreferences("kitsugi_prefs", Context.MODE_PRIVATE) }
                                    var userFilesEnabled by remember {
                                        mutableStateOf(prefs.getBoolean("mpv_user_files_enabled", true))
                                    }
                                    KitsugiSettingsSwitchItem(
                                        title = stringResource(R.string.pd_enable_user_files),
                                        description = stringResource(R.string.pd_enable_user_files_desc),
                                        checked = userFilesEnabled,
                                        icon = Icons.FolderOpen,
                                        iconColor = accentColor,
                                        onCheckedChange = {
                                            userFilesEnabled = it
                                            prefs.edit().putBoolean("mpv_user_files_enabled", it).apply()
                                        }
                                    )

                                    KitsugiSettingsDivider()

                                    KitsugiSettingsItem(
                                        title = stringResource(R.string.pd_edit_mpv_conf),
                                        description = stringResource(R.string.pd_edit_mpv_conf_desc),
                                        icon = Icons.EditNote,
                                        iconColor = accentColor,
                                        onClick = {
                                            val f = File(context.filesDir, "mpv.conf")
                                            if (!f.exists()) f.createNewFile()
                                            editingFile = f
                                            fileContentText = f.readText()
                                        }
                                    )

                                    KitsugiSettingsDivider()

                                    KitsugiSettingsItem(
                                        title = stringResource(R.string.pd_edit_input_conf),
                                        description = stringResource(R.string.pd_edit_input_conf_desc),
                                        icon = Icons.Keyboard,
                                        iconColor = accentColor,
                                        onClick = {
                                            val f = File(context.filesDir, "input.conf")
                                            if (!f.exists()) f.createNewFile()
                                            editingFile = f
                                            fileContentText = f.readText()
                                        }
                                    )
                                }
                            }


                        }
                    }
                }
            }

            if (!embeddedMode) {
                KitsugiSettingsDivider()

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_ok), color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Text Editor Dialog (Fullscreen style Dialog)
    if (editingFile != null) {
        Dialog(
            onDismissRequest = { editingFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = KitsugiColors.Surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = editingFile?.name ?: stringResource(R.string.pd_code_editor),
                            color = KitsugiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row {
                            TextButton(onClick = { editingFile = null }) {
                                Text(stringResource(R.string.settings_cancel), color = KitsugiColors.TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            KitsugiButton(
                                onClick = {
                                    editingFile?.writeText(fileContentText)
                                    editingFile = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text(stringResource(R.string.kaydet), color = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor))
                            }
                        }
                    }
                    KitsugiSettingsDivider()
                    OutlinedTextField(
                        value = fileContentText,
                        onValueChange = { fileContentText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            cursorColor = accentColor,
                            focusedTextColor = KitsugiColors.TextPrimary,
                            unfocusedTextColor = KitsugiColors.TextPrimary
                        )
                    )
                }
            }
        }
    }

    // Custom Button Edit Dialog
    if (showButtonEditDialog != null) {
        val btn = showButtonEditDialog!!
        var name by remember(btn) { mutableStateOf(btn.name) }
        var contentVal by remember(btn) { mutableStateOf(btn.content) }
        var longPressContentVal by remember(btn) { mutableStateOf(btn.longPressContent) }
        var onStartupVal by remember(btn) { mutableStateOf(btn.onStartup) }

        AlertDialog(
            onDismissRequest = { showButtonEditDialog = null },
            containerColor = KitsugiColors.Surface,
            title = { Text(stringResource(R.string.pd_edit_custom_button), color = KitsugiColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.pd_button_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, cursorColor = accentColor, focusedLabelColor = accentColor, focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary)
                    )
                    OutlinedTextField(
                        value = contentVal,
                        onValueChange = { contentVal = it },
                        label = { Text(stringResource(R.string.pd_code_on_click)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, cursorColor = accentColor, focusedLabelColor = accentColor, focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary)
                    )
                    OutlinedTextField(
                        value = longPressContentVal,
                        onValueChange = { longPressContentVal = it },
                        label = { Text(stringResource(R.string.pd_code_on_hold)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, cursorColor = accentColor, focusedLabelColor = accentColor, focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary)
                    )
                    OutlinedTextField(
                        value = onStartupVal,
                        onValueChange = { onStartupVal = it },
                        label = { Text(stringResource(R.string.pd_code_on_start)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, cursorColor = accentColor, focusedLabelColor = accentColor, focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            db.customButtonDao().update(
                                btn.copy(
                                    name = name,
                                    content = contentVal,
                                    longPressContent = longPressContentVal,
                                    onStartup = onStartupVal
                                )
                            )
                        }
                        showButtonEditDialog = null
                    }
                ) {
                    Text(stringResource(R.string.kaydet), color = accentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showButtonEditDialog = null }) {
                    Text(stringResource(R.string.settings_cancel), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }

    // Custom Button Add Dialog
    if (showButtonAddDialog) {
        var name by remember { mutableStateOf("") }
        var contentVal by remember { mutableStateOf("") }
        var longPressContentVal by remember { mutableStateOf("") }
        var onStartupVal by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showButtonAddDialog = false },
            containerColor = KitsugiColors.Surface,
            title = { Text(stringResource(R.string.pd_add_custom_button), color = KitsugiColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.pd_button_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, cursorColor = accentColor, focusedLabelColor = accentColor, focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary)
                    )
                    OutlinedTextField(
                        value = contentVal,
                        onValueChange = { contentVal = it },
                        label = { Text(stringResource(R.string.pd_code_on_click)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, cursorColor = accentColor, focusedLabelColor = accentColor, focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary)
                    )
                    OutlinedTextField(
                        value = longPressContentVal,
                        onValueChange = { longPressContentVal = it },
                        label = { Text(stringResource(R.string.pd_code_on_hold)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, cursorColor = accentColor, focusedLabelColor = accentColor, focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary)
                    )
                    OutlinedTextField(
                        value = onStartupVal,
                        onValueChange = { onStartupVal = it },
                        label = { Text(stringResource(R.string.pd_code_on_start)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, cursorColor = accentColor, focusedLabelColor = accentColor, focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val count = db.customButtonDao().getAll().size.toLong()
                            db.customButtonDao().insert(
                                CustomButton(
                                    name = name,
                                    content = contentVal,
                                    longPressContent = longPressContentVal,
                                    onStartup = onStartupVal,
                                    isFavorite = false,
                                    sortIndex = count
                                )
                            )
                        }
                        showButtonAddDialog = false
                    }
                ) {
                    Text(stringResource(R.string.ekle), color = accentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showButtonAddDialog = false }) {
                    Text(stringResource(R.string.settings_cancel), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }
}
