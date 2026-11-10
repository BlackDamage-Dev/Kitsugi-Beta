package com.kitsugi.animelist.ui.components.player

import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.core.player.PlayerAspectMode
import com.kitsugi.animelist.core.player.QualityProfile
import com.kitsugi.animelist.core.player.QualityPreference
import com.kitsugi.animelist.ui.screens.fullscreen.components.QualityProfileDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.core.player.ExternalPlayerPackages
import com.kitsugi.animelist.ui.components.ResettableSlider
import com.kitsugi.animelist.ui.components.KitsugiSettingsSection
import com.kitsugi.animelist.ui.components.KitsugiSettingsListItem
import com.kitsugi.animelist.ui.components.KitsugiSettingsSwitchItem
import com.kitsugi.animelist.ui.components.KitsugiSettingsDivider
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.components.KitsugiDropdownItem

/**
 * Oynatıcı Ayarları — Genel sekmesi.
 * Oynatıcı tercihi, otomatik oynatma ve intro atlama süresini içerir.
 */
@Composable
internal fun PlayerGeneralTab(
    playerPreference: String,
    preferredExternalPlayerPackage: String,
    isAutoplayEnabled: Boolean,
    skipIntroDurationSec: Int,
    dv7HandlingMode: com.kitsugi.animelist.data.settings.Dv7HandlingMode,
    stripHdr10PlusSei: Boolean,
    qualityProfileJson: String,
    accentColor: Color,
    frameRateMatchingMode: com.kitsugi.animelist.data.settings.FrameRateMatchingMode,
    resolutionMatchingEnabled: Boolean,
    aspectMode: PlayerAspectMode = PlayerAspectMode.ORIGINAL,
    onAspectModeSelected: (PlayerAspectMode) -> Unit = {},
    gestureVolumeEnabled: Boolean = true,
    gestureBrightnessEnabled: Boolean = true,
    gestureZoomEnabled: Boolean = true,
    doubleTapSeekSeconds: Int = 10,
    holdSpeedMultiplier: Float = 2.0f,
    gestureScrollSensitivity: Float = 1.0f,
    previewSeekbarEnabled: Boolean = true,
    onPlayerPreferenceSelected: (String) -> Unit,
    onPreferredExternalPlayerPackageSelected: (String) -> Unit,
    onAutoplayEnabledChanged: (Boolean) -> Unit,
    onSkipIntroDurationSecSelected: (Int) -> Unit,
    onDv7HandlingModeSelected: (com.kitsugi.animelist.data.settings.Dv7HandlingMode) -> Unit,
    onStripHdr10PlusSeiChanged: (Boolean) -> Unit,
    onQualityProfileSelected: (String) -> Unit,
    onFrameRateMatchingModeSelected: (com.kitsugi.animelist.data.settings.FrameRateMatchingMode) -> Unit,
    onResolutionMatchingEnabledChanged: (Boolean) -> Unit,
    onGestureVolumeEnabledChanged: (Boolean) -> Unit = {},
    onGestureBrightnessEnabledChanged: (Boolean) -> Unit = {},
    onGestureZoomEnabledChanged: (Boolean) -> Unit = {},
    onDoubleTapSeekSecondsSelected: (Int) -> Unit = {},
    onHoldSpeedMultiplierSelected: (Float) -> Unit = {},
    onGestureScrollSensitivityChanged: (Float) -> Unit = {},
    onPreviewSeekbarEnabledChanged: (Boolean) -> Unit = {},
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
    stillWatchingEnabled: Boolean = true,
    onStillWatchingEnabledChanged: (Boolean) -> Unit = {},
    stillWatchingThresholdMinutes: Int = 90,
    onStillWatchingThresholdMinutesChanged: (Int) -> Unit = {},
    postPlayMode: String = "AUTO_PLAY_NEXT",
    onPostPlayModeChanged: (String) -> Unit = {},
    autoplaySessionLimit: Int = 0,
    onAutoplaySessionLimitChanged: (Int) -> Unit = {},
    gainBoostDb: Float = 0f,
    onGainBoostDbChanged: (Float) -> Unit = {},
    subtitleDelayMs: Long = 0L,
    onSubtitleDelayMsChanged: (Long) -> Unit = {},
    decoderPriority: Int = 0,
    onDecoderPriorityChanged: (Int) -> Unit = {},
    // ─── MPV Gelişmiş Ayarları (Aniyomi'den uyarlama) ──────────────────────────
    mpvGpuRenderer: String = "gpu",
    onMpvGpuRendererSelected: (String) -> Unit = {},
    mpvHwdecMode: String = "auto-safe",
    onMpvHwdecModeSelected: (String) -> Unit = {},
    mpvDebandMode: String = "none",
    onMpvDebandModeSelected: (String) -> Unit = {},
    mpvForceYuv420p: Boolean = false,
    onMpvForceYuv420pChanged: (Boolean) -> Unit = {},
    mpvDemuxerCacheMb: Int = 64,
    onMpvDemuxerCacheMbChanged: (Int) -> Unit = {},
    volumeBoostCap: Int = 200,
    onVolumeBoostCapChanged: (Int) -> Unit = {},
    // ─── Yeni Jest Ayarları ────────────────────────────────────────────
    swipeVolumeBrightnessSides: Boolean = true,
    onSwipeVolumeBrightnessSidesChanged: (Boolean) -> Unit = {},
    horizontalSeekGestureEnabled: Boolean = true,
    onHorizontalSeekGestureEnabledChanged: (Boolean) -> Unit = {},
    preciseSeeking: Boolean = false,
    onPreciseSeekingChanged: (Boolean) -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    var showQualityProfileDialog by remember { mutableStateOf(false) }
    var playerDropdownExpanded by remember { mutableStateOf(false) }
    var introDropdownExpanded by remember { mutableStateOf(false) }
    var dvDropdownExpanded by remember { mutableStateOf(false) }
    var afrDropdownExpanded by remember { mutableStateOf(false) }
    var seekDropdownExpanded by remember { mutableStateOf(false) }
    var holdDropdownExpanded by remember { mutableStateOf(false) }
    var aspectDropdownExpanded by remember { mutableStateOf(false) }
    var thresholdDropdownExpanded by remember { mutableStateOf(false) }
    var postPlayDropdownExpanded by remember { mutableStateOf(false) }
    var sessionLimitDropdownExpanded by remember { mutableStateOf(false) }
    var decoderDropdownExpanded by remember { mutableStateOf(false) }
    var mpvGpuDropdownExpanded by remember { mutableStateOf(false) }
    var mpvHwdecDropdownExpanded by remember { mutableStateOf(false) }
    var mpvDebandDropdownExpanded by remember { mutableStateOf(false) }

    val playerOptions = listOf(
        "INTERNAL" to stringResource(R.string.player_engine_internal_exo),
        "MPV" to stringResource(R.string.player_engine_internal_mpv),
        "EXTERNAL" to stringResource(R.string.player_engine_external),
        "ASK" to stringResource(R.string.player_engine_ask)
    )

    val introOptions = listOf(
        0 to stringResource(R.string.player_intro_off),
        3 to stringResource(R.string.player_intro_3),
        5 to stringResource(R.string.player_intro_5),
        10 to stringResource(R.string.player_intro_10),
        15 to stringResource(R.string.player_intro_15)
    )

    val dvOptions = listOf(
        com.kitsugi.animelist.data.settings.Dv7HandlingMode.AUTO to stringResource(R.string.player_dv_auto),
        com.kitsugi.animelist.data.settings.Dv7HandlingMode.OFF to stringResource(R.string.player_dv_off),
        com.kitsugi.animelist.data.settings.Dv7HandlingMode.DV81_LIBDOVI to stringResource(R.string.player_dv_convert),
        com.kitsugi.animelist.data.settings.Dv7HandlingMode.HDR10_BASE_LAYER to stringResource(R.string.player_dv_base),
        com.kitsugi.animelist.data.settings.Dv7HandlingMode.STRIP_DV to stringResource(R.string.player_dv_strip)
    )

    val currentPlayerName = playerOptions.find { it.first == playerPreference }?.second
        ?: stringResource(R.string.player_engine_internal_exo)
    val currentIntroName = introOptions.find { it.first == skipIntroDurationSec }?.second
        ?: stringResource(R.string.player_intro_5)
    val currentDvName = dvOptions.find { it.first == dv7HandlingMode }?.second
        ?: stringResource(R.string.player_dv_auto)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Oynatıcı Tercihleri
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.player_section_preferences),
                subtitle = stringResource(R.string.player_section_preferences_desc)
            ) {
                // Tercih Edilen Oynatıcı
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_preferred),
                        description = stringResource(R.string.player_preferred_desc),
                        value = currentPlayerName,
                        icon = Icons.PlayArrow,
                        iconColor = accentColor,
                        onClick = { playerDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = playerDropdownExpanded, onDismissRequest = { playerDropdownExpanded = false }) {
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

                // Tercih Edilen Harici Oynatıcı (if EXTERNAL)
                if (playerPreference == "EXTERNAL") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var externalPlayerDropdownExpanded by remember { mutableStateOf(false) }
                    val currentExternalPlayerName = ExternalPlayerPackages.players.find { it.packageName == preferredExternalPlayerPackage }?.name ?: stringResource(R.string.player_system_default)

                    KitsugiSettingsDivider()

                    Box {
                        KitsugiSettingsListItem(
                            title = stringResource(R.string.player_preferred_external),
                            description = stringResource(R.string.player_preferred_external_desc),
                            value = currentExternalPlayerName,
                            icon = Icons.Launch,
                            iconColor = accentColor,
                            onClick = { externalPlayerDropdownExpanded = true }
                        )
                        KitsugiDropdownMenu(expanded = externalPlayerDropdownExpanded, onDismissRequest = { externalPlayerDropdownExpanded = false }) {
                            ExternalPlayerPackages.players.forEach { playerDef ->
                                val isInstalled = playerDef.packageName.isEmpty() || playerDef.isInstalled(context)
                                KitsugiDropdownItem(
                                    text = if (isInstalled) playerDef.name else stringResource(R.string.player_external_not_installed, playerDef.name),
                                    selected = playerDef.packageName == preferredExternalPlayerPackage,
                                    onClick = {
                                        if (isInstalled) {
                                            onPreferredExternalPlayerPackageSelected(playerDef.packageName)
                                        } else {
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${playerDef.packageName}"))
                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(playerDef.storeUrl))
                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            }
                                        }
                                        externalPlayerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Otomatik Sonraki Bölüm (Switch)
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_auto_next),
                    description = stringResource(R.string.player_auto_next_desc),
                    icon = Icons.SkipNext,
                    iconColor = accentColor,
                    checked = isAutoplayEnabled,
                    onCheckedChange = onAutoplayEnabledChanged
                )

                KitsugiSettingsDivider()

                // İntro Atlama Buton Süresi
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_intro_skip),
                        description = stringResource(R.string.player_intro_skip_desc),
                        value = currentIntroName,
                        icon = Icons.Forward10,
                        iconColor = accentColor,
                        onClick = { introDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = introDropdownExpanded, onDismissRequest = { introDropdownExpanded = false }) {
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

                KitsugiSettingsDivider()

                // Kalite Profili
                val profile = remember(qualityProfileJson) { QualityProfile.deserialize(qualityProfileJson) }
                val preferenceName = when (profile.preference) {
                    QualityPreference.AUTO       -> stringResource(R.string.player_quality_auto)
                    QualityPreference.P1080      -> stringResource(R.string.player_quality_1080)
                    QualityPreference.P720       -> stringResource(R.string.player_quality_720)
                    QualityPreference.P480       -> stringResource(R.string.player_quality_480)
                    QualityPreference.DATA_SAVER -> stringResource(R.string.player_quality_data_saver)
                }
                KitsugiSettingsListItem(
                    title = stringResource(R.string.player_quality_profile),
                    description = stringResource(R.string.player_quality_desc),
                    value = preferenceName,
                    icon = Icons.Hd,
                    iconColor = accentColor,
                    onClick = { showQualityProfileDialog = true }
                )
            }
        }

        // Video İşleme & Uyum
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.player_section_processing),
                subtitle = stringResource(R.string.player_section_processing_desc)
            ) {
                // Dolby Vision (DV7) İşleme Modu
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_dv_mode),
                        description = stringResource(R.string.player_dv_mode_desc),
                        value = currentDvName,
                        icon = Icons.Settings,
                        iconColor = accentColor,
                        onClick = { dvDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = dvDropdownExpanded, onDismissRequest = { dvDropdownExpanded = false }) {
                        dvOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == dv7HandlingMode,
                                onClick = {
                                    onDv7HandlingModeSelected(option.first)
                                    dvDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // HDR10+ SEI Metadatasını Ayıkla
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_hdr10_sei),
                    description = stringResource(R.string.player_hdr10_sei_desc),
                    icon = Icons.FilterCenterFocus,
                    iconColor = accentColor,
                    checked = stripHdr10PlusSei,
                    onCheckedChange = onStripHdr10PlusSeiChanged
                )

                KitsugiSettingsDivider()

                // Görüntü Oranı
                val aspectOptions = listOf(
                    PlayerAspectMode.ORIGINAL  to stringResource(R.string.player_aspect_original),
                    PlayerAspectMode.FIT       to stringResource(R.string.player_aspect_fit),
                    PlayerAspectMode.FILL      to stringResource(R.string.player_aspect_fill),
                    PlayerAspectMode.ZOOM      to stringResource(R.string.player_aspect_zoom),
                    PlayerAspectMode.CROP_16_9 to stringResource(R.string.player_aspect_169),
                    PlayerAspectMode.CROP_4_3  to stringResource(R.string.player_aspect_43)
                )
                val currentAspectName = aspectOptions.find { it.first == aspectMode }?.second
                    ?: stringResource(R.string.player_aspect_original)
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_aspect),
                        description = stringResource(R.string.player_aspect_desc),
                        value = currentAspectName,
                        icon = Icons.AspectRatio,
                        iconColor = accentColor,
                        onClick = { aspectDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = aspectDropdownExpanded, onDismissRequest = { aspectDropdownExpanded = false }) {
                        aspectOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == aspectMode,
                                onClick = {
                                    onAspectModeSelected(option.first)
                                    aspectDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Kare Hızı Eşleme (AFR)
                val afrOptions = listOf(
                    com.kitsugi.animelist.data.settings.FrameRateMatchingMode.OFF to stringResource(R.string.player_afr_off),
                    com.kitsugi.animelist.data.settings.FrameRateMatchingMode.START to stringResource(R.string.player_afr_start),
                    com.kitsugi.animelist.data.settings.FrameRateMatchingMode.START_STOP to stringResource(R.string.player_afr_start_stop)
                )
                val currentAfrName = afrOptions.find { it.first == frameRateMatchingMode }?.second ?: stringResource(R.string.player_afr_off)
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_afr),
                        description = stringResource(R.string.player_afr_desc),
                        value = currentAfrName,
                        icon = Icons.Sync,
                        iconColor = accentColor,
                        onClick = { afrDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = afrDropdownExpanded, onDismissRequest = { afrDropdownExpanded = false }) {
                        afrOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == frameRateMatchingMode,
                                onClick = {
                                    onFrameRateMatchingModeSelected(option.first)
                                    afrDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Çözünürlük Eşleme
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_res_match),
                    description = stringResource(R.string.player_res_match_desc),
                    icon = Icons.SettingsOverscan,
                    iconColor = accentColor,
                    checked = resolutionMatchingEnabled,
                    onCheckedChange = onResolutionMatchingEnabledChanged
                )
            }
        }

        // Arayüz & Yardımcılar
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.player_section_ui),
                subtitle = stringResource(R.string.player_section_ui_desc)
            ) {
                // Canlı Yardımcı
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_live_assistant),
                    description = stringResource(R.string.player_live_assistant_desc),
                    icon = Icons.Help,
                    iconColor = accentColor,
                    checked = liveHelperEnabled,
                    onCheckedChange = onLiveHelperEnabledChanged
                )

                KitsugiSettingsDivider()

                // ASS Altyazı Ayıklayıcı
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_ass_extractor),
                    description = stringResource(R.string.player_ass_extractor_desc),
                    icon = Icons.Subtitles,
                    iconColor = accentColor,
                    checked = enableAssExtractor,
                    onCheckedChange = onEnableAssExtractorChanged
                )

                KitsugiSettingsDivider()

                // Oynatıcı Başlığını Göster
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_show_title),
                    description = stringResource(R.string.player_show_title_desc),
                    icon = Icons.Title,
                    iconColor = accentColor,
                    checked = showPlayerTitle,
                    onCheckedChange = onShowPlayerTitleChanged
                )

                KitsugiSettingsDivider()

                // Oynatıcı Çözünürlüğünü Göster
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_show_resolution),
                    description = stringResource(R.string.player_show_resolution_desc),
                    icon = Icons.SettingsOverscan,
                    iconColor = accentColor,
                    checked = showPlayerResolution,
                    onCheckedChange = onShowPlayerResolutionChanged
                )

                KitsugiSettingsDivider()

                // Medya Bilgisini Göster
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_show_media_info),
                    description = stringResource(R.string.player_show_media_info_desc),
                    icon = Icons.Info,
                    iconColor = accentColor,
                    checked = showMediaInfo,
                    onCheckedChange = onShowMediaInfoChanged
                )

                KitsugiSettingsDivider()

                // Önizleme Seekbarı
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_preview_seekbar),
                    description = stringResource(R.string.player_preview_seekbar_desc),
                    icon = Icons.Preview,
                    iconColor = accentColor,
                    checked = previewSeekbarEnabled,
                    onCheckedChange = onPreviewSeekbarEnabledChanged
                )
            }
        }

        // Otomatik Oynatma & Seans
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.player_section_autoplay),
                subtitle = stringResource(R.string.player_section_autoplay_desc)
            ) {
                // Hâlâ İzliyor Musun?
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_are_you_watching),
                    description = stringResource(R.string.player_are_you_watching_desc),
                    icon = Icons.Tv,
                    iconColor = accentColor,
                    checked = stillWatchingEnabled,
                    onCheckedChange = onStillWatchingEnabledChanged
                )

                // Hareketsizlik Eşiği
                if (stillWatchingEnabled) {
                    KitsugiSettingsDivider()
                    val thresholdOptions = listOf(30 to stringResource(R.string.player_threshold_30), 60 to stringResource(R.string.player_threshold_60), 90 to stringResource(R.string.player_threshold_90), 120 to stringResource(R.string.player_threshold_120))
                    val currentThresholdName = thresholdOptions.find { it.first == stillWatchingThresholdMinutes }?.second ?: stringResource(R.string.player_threshold_90)
                    Box {
                        KitsugiSettingsListItem(
                            title = stringResource(R.string.player_inactivity_threshold),
                            description = stringResource(R.string.player_inactivity_threshold_desc),
                            value = currentThresholdName,
                            icon = Icons.HourglassEmpty,
                            iconColor = accentColor,
                            onClick = { thresholdDropdownExpanded = true }
                        )
                        KitsugiDropdownMenu(expanded = thresholdDropdownExpanded, onDismissRequest = { thresholdDropdownExpanded = false }) {
                            thresholdOptions.forEach { opt ->
                                KitsugiDropdownItem(
                                    text = opt.second,
                                    selected = opt.first == stillWatchingThresholdMinutes,
                                    onClick = {
                                        onStillWatchingThresholdMinutesChanged(opt.first)
                                        thresholdDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Sonraki Bölüm Modu
                val postPlayOptions = listOf(
                    "AUTO_PLAY_NEXT" to stringResource(R.string.player_post_play_auto),
                    "BINGE_PROMPT" to stringResource(R.string.player_post_play_ask),
                    "MANUAL" to stringResource(R.string.player_post_play_manual)
                )
                val currentPostPlayName = postPlayOptions.find { it.first == postPlayMode }?.second ?: stringResource(R.string.player_post_play_auto)
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_post_play_mode),
                        description = stringResource(R.string.player_post_play_mode_desc),
                        value = currentPostPlayName,
                        icon = Icons.Forward,
                        iconColor = accentColor,
                        onClick = { postPlayDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = postPlayDropdownExpanded, onDismissRequest = { postPlayDropdownExpanded = false }) {
                        postPlayOptions.forEach { opt ->
                            KitsugiDropdownItem(
                                text = opt.second,
                                selected = opt.first == postPlayMode,
                                onClick = {
                                    onPostPlayModeChanged(opt.first)
                                    postPlayDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Oturum Limiti
                val limitOptions = listOf(0 to stringResource(R.string.player_limit_unlimited), 3 to stringResource(R.string.player_limit_3), 5 to stringResource(R.string.player_limit_5), 10 to stringResource(R.string.player_limit_10), 20 to stringResource(R.string.player_limit_20))
                val currentLimitName = limitOptions.find { it.first == autoplaySessionLimit }?.second ?: stringResource(R.string.player_limit_unlimited)
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_session_limit),
                        description = stringResource(R.string.player_session_limit_desc),
                        value = currentLimitName,
                        icon = Icons.SlowMotionVideo,
                        iconColor = accentColor,
                        onClick = { sessionLimitDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = sessionLimitDropdownExpanded, onDismissRequest = { sessionLimitDropdownExpanded = false }) {
                        limitOptions.forEach { opt ->
                            KitsugiDropdownItem(
                                text = opt.second,
                                selected = opt.first == autoplaySessionLimit,
                                onClick = {
                                    onAutoplaySessionLimitChanged(opt.first)
                                    sessionLimitDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Ses & Altyazı Gelişmiş
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.player_section_audio_sub),
                subtitle = stringResource(R.string.player_section_audio_sub_desc)
            ) {
                // Dekoder Önceliği
                val decoderOptions = listOf(
                    0 to stringResource(R.string.player_decoder_hw),
                    1 to stringResource(R.string.player_decoder_fallback),
                    2 to stringResource(R.string.player_decoder_sw)
                )
                val currentDecoderName = decoderOptions.find { it.first == decoderPriority }?.second
                    ?: stringResource(R.string.player_decoder_hw)
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_decoder_priority),
                        description = stringResource(R.string.player_decoder_priority_desc),
                        value = currentDecoderName,
                        icon = Icons.Memory,
                        iconColor = accentColor,
                        onClick = { decoderDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = decoderDropdownExpanded, onDismissRequest = { decoderDropdownExpanded = false }) {
                        decoderOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == decoderPriority,
                                onClick = {
                                    onDecoderPriorityChanged(option.first)
                                    decoderDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Slider values styled cleanly
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.player_gain_boost_label, String.format("%.1f", gainBoostDb)),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "0 dB = normal; pozitif = amplifikasyon; negatif = azaltma",
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    ResettableSlider(
                        value = gainBoostDb,
                        defaultValue = 0f,
                        onValueChange = onGainBoostDbChanged,
                        valueRange = -10f..20f,
                        steps = 59
                    )
                }

                KitsugiSettingsDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val subtitleDelaySeconds = subtitleDelayMs / 1000f
                    val clampedDelaySeconds = subtitleDelaySeconds.coerceIn(-10f, 10f)
                    Text(
                        stringResource(R.string.player_subtitle_delay_label, String.format("%.1f", subtitleDelaySeconds)),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.player_subtitle_delay_hint),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    ResettableSlider(
                        value = clampedDelaySeconds,
                        defaultValue = 0f,
                        onValueChange = { onSubtitleDelayMsChanged((it * 1000).toLong()) },
                        valueRange = -10f..10f,
                        steps = 199
                    )
                }
            }
        }

        // Jestler
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.player_section_gestures),
                subtitle = stringResource(R.string.player_section_gestures_desc)
            ) {
                // Ses Jesti
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_gesture_volume),
                    description = stringResource(R.string.player_gesture_volume_desc),
                    icon = Icons.VolumeUp,
                    iconColor = accentColor,
                    checked = gestureVolumeEnabled,
                    onCheckedChange = onGestureVolumeEnabledChanged
                )

                KitsugiSettingsDivider()

                // Parlaklık Jesti
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_gesture_brightness),
                    description = stringResource(R.string.player_gesture_brightness_desc),
                    icon = Icons.BrightnessMedium,
                    iconColor = accentColor,
                    checked = gestureBrightnessEnabled,
                    onCheckedChange = onGestureBrightnessEnabledChanged
                )

                KitsugiSettingsDivider()

                // Zoom Jesti
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_gesture_zoom),
                    description = stringResource(R.string.player_gesture_zoom_desc),
                    icon = Icons.ZoomIn,
                    iconColor = accentColor,
                    checked = gestureZoomEnabled,
                    onCheckedChange = onGestureZoomEnabledChanged
                )

                KitsugiSettingsDivider()

                // Çift Dokunuş İleri/Geri Süresi
                val seekOptions = listOf(5 to "5s", 10 to "10s", 15 to "15s", 20 to "20s", 30 to "30s")
                val currentSeekName = seekOptions.find { it.first == doubleTapSeekSeconds }?.second ?: "10s"
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_double_tap_seek),
                        description = stringResource(R.string.player_double_tap_seek_desc),
                        value = currentSeekName,
                        icon = Icons.Forward10,
                        iconColor = accentColor,
                        onClick = { seekDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = seekDropdownExpanded, onDismissRequest = { seekDropdownExpanded = false }) {
                        seekOptions.forEach { opt ->
                            KitsugiDropdownItem(
                                text = opt.second,
                                selected = opt.first == doubleTapSeekSeconds,
                                onClick = {
                                    onDoubleTapSeekSecondsSelected(opt.first)
                                    seekDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Basılı Tutma Hız Çarpanı
                val holdOptions = listOf(1.5f to "1.5x", 2.0f to "2x", 2.5f to "2.5x", 3.0f to "3x")
                val currentHoldName = holdOptions.find { it.first == holdSpeedMultiplier }?.second ?: "2x"
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_hold_speed),
                        description = stringResource(R.string.player_hold_speed_desc),
                        value = currentHoldName,
                        icon = Icons.Speed,
                        iconColor = accentColor,
                        onClick = { holdDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = holdDropdownExpanded, onDismissRequest = { holdDropdownExpanded = false }) {
                        holdOptions.forEach { opt ->
                            KitsugiDropdownItem(
                                text = opt.second,
                                selected = opt.first == holdSpeedMultiplier,
                                onClick = {
                                    onHoldSpeedMultiplierSelected(opt.first)
                                    holdDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Kaydırma Hassasiyeti
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val scrollLabel = when {
                        gestureScrollSensitivity <= 0.6f -> stringResource(R.string.player_scroll_speed_slow, gestureScrollSensitivity)
                        gestureScrollSensitivity >= 1.6f -> stringResource(R.string.player_scroll_speed_fast, gestureScrollSensitivity)
                        else -> stringResource(R.string.player_scroll_speed_normal, gestureScrollSensitivity)
                    }
                    Text(
                        stringResource(R.string.player_scroll_sensitivity_label, scrollLabel),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    ResettableSlider(
                        value = gestureScrollSensitivity,
                        defaultValue = 1.0f,
                        onValueChange = onGestureScrollSensitivityChanged,
                        valueRange = 0.5f..2.0f,
                        steps = 14
                    )
                }
            }
        }

        // MPV Gelişmiş Motor Ayarları
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.player_section_mpv),
                subtitle = stringResource(R.string.player_section_mpv_desc)
            ) {
                // GPU Renderer
                val gpuOptions = listOf("gpu" to stringResource(R.string.player_mpv_gpu), "gpu-next" to stringResource(R.string.player_mpv_gpu_next))
                val currentGpuName = gpuOptions.find { it.first == mpvGpuRenderer }?.second ?: stringResource(R.string.player_mpv_gpu)
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_mpv_gpu_renderer),
                        description = stringResource(R.string.player_mpv_gpu_renderer_desc),
                        value = currentGpuName,
                        icon = Icons.Memory,
                        iconColor = accentColor,
                        onClick = { mpvGpuDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = mpvGpuDropdownExpanded, onDismissRequest = { mpvGpuDropdownExpanded = false }) {
                        gpuOptions.forEach { opt ->
                            KitsugiDropdownItem(
                                text = opt.second,
                                selected = opt.first == mpvGpuRenderer,
                                onClick = { onMpvGpuRendererSelected(opt.first); mpvGpuDropdownExpanded = false }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Hwdec Modu
                val hwdecOptions = listOf(
                    "auto-safe" to stringResource(R.string.player_mpv_hwdec_auto_safe),
                    "auto" to stringResource(R.string.player_mpv_hwdec_auto_all),
                    "mediacodec" to stringResource(R.string.player_mpv_hwdec_direct),
                    "mediacodec-copy" to stringResource(R.string.player_mpv_hwdec_copy),
                    "no" to stringResource(R.string.player_mpv_hwdec_sw)
                )
                val currentHwdecName = hwdecOptions.find { it.first == mpvHwdecMode }?.second ?: stringResource(R.string.player_mpv_hwdec_auto_safe)
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_mpv_hwdec),
                        description = stringResource(R.string.player_mpv_hwdec_desc),
                        value = currentHwdecName,
                        icon = Icons.DeveloperBoard,
                        iconColor = accentColor,
                        onClick = { mpvHwdecDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = mpvHwdecDropdownExpanded, onDismissRequest = { mpvHwdecDropdownExpanded = false }) {
                        hwdecOptions.forEach { opt ->
                            KitsugiDropdownItem(
                                text = opt.second,
                                selected = opt.first == mpvHwdecMode,
                                onClick = { onMpvHwdecModeSelected(opt.first); mpvHwdecDropdownExpanded = false }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Debanding
                val debandOptions = listOf("none" to stringResource(R.string.player_mpv_deband_off), "cpu" to stringResource(R.string.player_mpv_deband_cpu), "gpu" to stringResource(R.string.player_mpv_deband_gpu))
                val currentDebandName = debandOptions.find { it.first == mpvDebandMode }?.second ?: stringResource(R.string.player_mpv_deband_off)
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.player_mpv_deband),
                        description = stringResource(R.string.player_mpv_deband_desc),
                        value = currentDebandName,
                        icon = Icons.Gradient,
                        iconColor = accentColor,
                        onClick = { mpvDebandDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = mpvDebandDropdownExpanded, onDismissRequest = { mpvDebandDropdownExpanded = false }) {
                        debandOptions.forEach { opt ->
                            KitsugiDropdownItem(
                                text = opt.second,
                                selected = opt.first == mpvDebandMode,
                                onClick = { onMpvDebandModeSelected(opt.first); mpvDebandDropdownExpanded = false }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // YUV420P Zorla
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_mpv_yuv),
                    description = stringResource(R.string.player_mpv_yuv_desc),
                    icon = Icons.VideoSettings,
                    iconColor = accentColor,
                    checked = mpvForceYuv420p,
                    onCheckedChange = onMpvForceYuv420pChanged
                )

                KitsugiSettingsDivider()

                // Demuxer Önbelleği
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.player_mpv_demuxer_cache, mpvDemuxerCacheMb),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.player_mpv_demuxer_desc),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    ResettableSlider(
                        value = mpvDemuxerCacheMb.toFloat(),
                        defaultValue = 64f,
                        onValueChange = { onMpvDemuxerCacheMbChanged(it.toInt()) },
                        valueRange = 8f..256f,
                        steps = 30
                    )
                }

                KitsugiSettingsDivider()

                // Ses Boost Sınırı
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.player_mpv_volume_cap, volumeBoostCap),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.player_mpv_volume_cap_desc),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    ResettableSlider(
                        value = volumeBoostCap.toFloat(),
                        defaultValue = 200f,
                        onValueChange = { onVolumeBoostCapChanged(it.toInt()) },
                        valueRange = 100f..200f,
                        steps = 19
                    )
                }
            }
        }

        // Yeni Jest Ayarları
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.player_section_gestures_adv),
                subtitle = stringResource(R.string.player_section_gestures_adv_desc)
            ) {
                // Ses/Parlaklık Yer Değiştir
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_gesture_swap_sides),
                    description = stringResource(R.string.player_gesture_swap_sides_desc),
                    icon = Icons.SwapHoriz,
                    iconColor = accentColor,
                    checked = !swipeVolumeBrightnessSides,
                    onCheckedChange = { onSwipeVolumeBrightnessSidesChanged(!it) }
                )

                KitsugiSettingsDivider()

                // Yatay Seek Jesti
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_gesture_horizontal_seek),
                    description = stringResource(R.string.player_gesture_horizontal_seek_desc),
                    icon = Icons.SwipeRight,
                    iconColor = accentColor,
                    checked = horizontalSeekGestureEnabled,
                    onCheckedChange = onHorizontalSeekGestureEnabledChanged
                )

                KitsugiSettingsDivider()

                // Hassas Seek Modu
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.player_precise_seek),
                    description = stringResource(R.string.player_precise_seek_desc),
                    icon = Icons.ControlCamera,
                    iconColor = accentColor,
                    checked = preciseSeeking,
                    onCheckedChange = onPreciseSeekingChanged
                )
            }
        }
    }

    if (showQualityProfileDialog) {
        val currentProfile = remember(qualityProfileJson) { QualityProfile.deserialize(qualityProfileJson) }
        QualityProfileDialog(
            currentProfile = currentProfile,
            onDismiss = { showQualityProfileDialog = false },
            onProfileSelected = { newProfile ->
                onQualityProfileSelected(QualityProfile.serialize(newProfile))
                showQualityProfileDialog = false
            }
        )
    }
}
