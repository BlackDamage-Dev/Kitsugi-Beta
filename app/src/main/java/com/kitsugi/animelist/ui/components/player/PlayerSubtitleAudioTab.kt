package com.kitsugi.animelist.ui.components.player

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import com.kitsugi.animelist.ui.theme.gradient.Checkbox
import com.kitsugi.animelist.ui.theme.gradient.Icon
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
import com.kitsugi.animelist.ui.components.KitsugiSettingsSection
import com.kitsugi.animelist.ui.components.KitsugiSettingsListItem
import com.kitsugi.animelist.ui.components.KitsugiSettingsSwitchItem
import com.kitsugi.animelist.ui.components.KitsugiSettingsDivider
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.components.KitsugiDropdownItem
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Oynatıcı Ayarları — Altyazı & Ses sekmesi.
 * Altyazı boyutu, rengi, kalınlık, kenarlık, ses güçlendirme ve gecikmeyi içerir.
 */
@Composable
internal fun PlayerSubtitleAudioTab(
    defaultSubtitleSize: Int,
    defaultSubtitleColor: Int,
    subtitleBold: Boolean,
    subtitleOutlineEnabled: Boolean,
    defaultAudioBoost: Float,
    defaultAudioDelayMs: Long,
    preferredSubtitleLanguages: String,
    addonSubtitleStartupMode: String,
    accentColor: Color,
    // T1.3 – Rota başına gecikme
    speakerDelayMs: Long = 0L,
    bluetoothDelayMs: Long = 0L,
    wiredDelayMs: Long = 0L,
    hdmiDelayMs: Long = 0L,
    activeAudioRoute: com.kitsugi.animelist.core.player.AudioRoute = com.kitsugi.animelist.core.player.AudioRoute.SPEAKER,
    onRouteDelayChanged: (speaker: Long, bluetooth: Long, wired: Long, hdmi: Long) -> Unit = { _, _, _, _ -> },
    onDefaultSubtitleSizeSelected: (Int) -> Unit,
    onDefaultSubtitleColorSelected: (Int) -> Unit,
    onSubtitleBoldChanged: (Boolean) -> Unit,
    onSubtitleOutlineEnabledChanged: (Boolean) -> Unit,
    onDefaultAudioBoostSelected: (Float) -> Unit,
    onDefaultAudioDelayMsSelected: (Long) -> Unit,
    onPreferredSubtitleLanguagesSelected: (String) -> Unit,
    onAddonSubtitleStartupModeSelected: (String) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    var sizeDropdownExpanded by remember { mutableStateOf(false) }
    var colorDropdownExpanded by remember { mutableStateOf(false) }
    var boostDropdownExpanded by remember { mutableStateOf(false) }
    var delayDropdownExpanded by remember { mutableStateOf(false) }
    var startupModeDropdownExpanded by remember { mutableStateOf(false) }

    val sizeOptions = listOf(
        12 to stringResource(R.string.sub_size_12),
        14 to stringResource(R.string.sub_size_14),
        16 to stringResource(R.string.sub_size_16),
        18 to stringResource(R.string.sub_size_18),
        20 to stringResource(R.string.sub_size_20),
        24 to stringResource(R.string.sub_size_24)
    )

    val colorOptions = listOf(
        0xFFFFFFFF.toInt() to stringResource(R.string.sub_color_white),
        0xFFFFFF00.toInt() to stringResource(R.string.sub_color_yellow),
        0xFF00FF00.toInt() to stringResource(R.string.sub_color_green),
        0xFF00FFFF.toInt() to stringResource(R.string.sub_color_blue),
        0xFFFF0000.toInt() to stringResource(R.string.sub_color_red)
    )

    val boostOptions = listOf(
        0.0f to stringResource(R.string.sub_opacity_normal),
        0.25f to stringResource(R.string.sub_opacity_low),
        0.5f to stringResource(R.string.sub_opacity_mid),
        0.75f to stringResource(R.string.sub_opacity_high),
        1.0f to stringResource(R.string.sub_opacity_max)
    )

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

    // (Dil seçimi artık checkbox paneli ile yapılıyor — prefLangOptions kaldırıldı)

    val startupModeOptions = listOf(
        "ALL_SUBTITLES" to stringResource(R.string.sub_load_all),
        "PREFERRED_ONLY" to stringResource(R.string.sub_load_preferred)
    )

    val currentSizeName = sizeOptions.find { it.first == defaultSubtitleSize }?.second ?: stringResource(R.string.sub_size_16)
    val currentColorName = colorOptions.find { it.first == defaultSubtitleColor }?.second ?: stringResource(R.string.sub_color_white)
    val currentBoostName = boostOptions.find { it.first == defaultAudioBoost }?.second ?: stringResource(R.string.sub_opacity_normal)
    val currentDelayName = delayOptions.find { it.first == defaultAudioDelayMs }?.second ?: stringResource(R.string.sub_delay_ontime)
    val currentStartupModeName = startupModeOptions.find { it.first == addonSubtitleStartupMode }?.second ?: stringResource(R.string.sub_load_preferred)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Altyazı Görünümü
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.sub_section_appearance),
                subtitle = stringResource(R.string.sub_section_appearance_desc)
            ) {
                // Size
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.sub_default_size),
                        description = stringResource(R.string.sub_default_size_desc),
                        value = currentSizeName,
                        icon = Icons.Subtitles,
                        iconColor = accentColor,
                        onClick = { sizeDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = sizeDropdownExpanded, onDismissRequest = { sizeDropdownExpanded = false }) {
                        sizeOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == defaultSubtitleSize,
                                onClick = {
                                    onDefaultSubtitleSizeSelected(option.first)
                                    sizeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Color
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.sub_default_color),
                        description = stringResource(R.string.sub_default_color_desc),
                        value = currentColorName,
                        icon = Icons.Subtitles,
                        iconColor = accentColor,
                        onClick = { colorDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = colorDropdownExpanded, onDismissRequest = { colorDropdownExpanded = false }) {
                        colorOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == defaultSubtitleColor,
                                onClick = {
                                    onDefaultSubtitleColorSelected(option.first)
                                    colorDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Bold
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.sub_bold),
                    description = stringResource(R.string.sub_bold_desc),
                    icon = Icons.Subtitles,
                    iconColor = accentColor,
                    checked = subtitleBold,
                    onCheckedChange = onSubtitleBoldChanged
                )

                KitsugiSettingsDivider()

                // Outline
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.sub_outline),
                    description = stringResource(R.string.sub_outline_desc),
                    icon = Icons.Subtitles,
                    iconColor = accentColor,
                    checked = subtitleOutlineEnabled,
                    onCheckedChange = onSubtitleOutlineEnabledChanged
                )
            }
        }

        // Ses & Altyazı Tercihleri
        item {
            KitsugiSettingsSection(
                title = stringResource(R.string.sub_section_audio),
                subtitle = stringResource(R.string.sub_section_audio_desc)
            ) {
                // Audio Boost
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.sub_audio_boost),
                        description = stringResource(R.string.sub_audio_boost_desc),
                        value = currentBoostName,
                        icon = Icons.VolumeUp,
                        iconColor = accentColor,
                        onClick = { boostDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = boostDropdownExpanded, onDismissRequest = { boostDropdownExpanded = false }) {
                        boostOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == defaultAudioBoost,
                                onClick = {
                                    onDefaultAudioBoostSelected(option.first)
                                    boostDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Audio Delay
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.sub_audio_delay),
                        description = stringResource(R.string.sub_audio_delay_desc),
                        value = currentDelayName,
                        icon = Icons.VolumeUp,
                        iconColor = accentColor,
                        onClick = { delayDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = delayDropdownExpanded, onDismissRequest = { delayDropdownExpanded = false }) {
                        delayOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == defaultAudioDelayMs,
                                onClick = {
                                    onDefaultAudioDelayMsSelected(option.first)
                                    delayDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // ─── Altyazı Dil Tercihi — çoklu seçim ────────────────
                // Seçili dilleri virgülle ayrılmış Set olarak yönet
                val selectedLangs = remember(preferredSubtitleLanguages) {
                    preferredSubtitleLanguages.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
                }

                val availableLangs = listOf(
                    "tr" to stringResource(R.string.sub_lang_tr),
                    "en" to stringResource(R.string.sub_lang_en),
                    "ja" to stringResource(R.string.sub_lang_ja),
                    "fr" to stringResource(R.string.sub_lang_fr),
                    "de" to stringResource(R.string.sub_lang_de),
                    "es" to stringResource(R.string.sub_lang_es),
                    "pt" to stringResource(R.string.sub_lang_pt),
                    "ar" to stringResource(R.string.sub_lang_ar),
                    "ko" to stringResource(R.string.sub_lang_ko),
                    "zh" to stringResource(R.string.sub_lang_zh)
                )

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KitsugiArtIcon(art = KitsugiArt.Subtitles, size = 22.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sub_preferred_langs),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = KitsugiColors.TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.sub_preferred_langs_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = KitsugiColors.TextSecondary
                            )
                        }
                    }

                    availableLangs.forEach { (code, label) ->
                        val isChecked = selectedLangs.contains(code)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 34.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val updated = selectedLangs.toMutableSet()
                                    if (checked) updated.add(code) else updated.remove(code)
                                    // En az bir dil seçili olmalı
                                    if (updated.isNotEmpty()) {
                                        onPreferredSubtitleLanguagesSelected(updated.joinToString(","))
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = accentColor,
                                    uncheckedColor = KitsugiColors.TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isChecked) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // ─── Altyazı Yükleme Modu ──────────────────────────────
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.sub_load_mode),
                        description = stringResource(R.string.sub_load_mode_desc),
                        value = currentStartupModeName,
                        icon = Icons.Subtitles,
                        iconColor = accentColor,
                        onClick = { startupModeDropdownExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = startupModeDropdownExpanded, onDismissRequest = { startupModeDropdownExpanded = false }) {
                        startupModeOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == addonSubtitleStartupMode,
                                onClick = {
                                    onAddonSubtitleStartupModeSelected(option.first)
                                    startupModeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Rota Bazlı Ses Gecikmesi
        item {
            val routeLabel = when (activeAudioRoute) {
                com.kitsugi.animelist.core.player.AudioRoute.BLUETOOTH -> stringResource(R.string.sub_route_bt)
                com.kitsugi.animelist.core.player.AudioRoute.WIRED     -> stringResource(R.string.sub_route_wired)
                com.kitsugi.animelist.core.player.AudioRoute.HDMI      -> stringResource(R.string.sub_route_hdmi)
                com.kitsugi.animelist.core.player.AudioRoute.SPEAKER   -> stringResource(R.string.sub_route_speaker)
                com.kitsugi.animelist.core.player.AudioRoute.OTHER     -> stringResource(R.string.sub_route_other)
            }
            KitsugiSettingsSection(
                title = stringResource(R.string.sub_route_delay_section),
                subtitle = stringResource(R.string.sub_route_delay_desc, routeLabel)
            ) {
                val routeDelayOptions = listOf(
                    -500L to "-500 ms",
                    -300L to "-300 ms",
                    -200L to "-200 ms",
                    -150L to "-150 ms",
                    -100L to "-100 ms",
                    0L to stringResource(R.string.sub_delay_ontime_sp),
                    100L to "+100 ms",
                    150L to "+150 ms",
                    200L to "+200 ms",
                    300L to "+300 ms",
                    500L to "+500 ms"
                )

                // Speaker
                var routeSpeakerExpanded by remember { mutableStateOf(false) }
                val isSpeakerActive = activeAudioRoute == com.kitsugi.animelist.core.player.AudioRoute.SPEAKER
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.sub_delay_speaker),
                        description = stringResource(R.string.sub_delay_speaker_desc),
                        value = routeDelayOptions.find { it.first == speakerDelayMs }?.second ?: "${speakerDelayMs} ms",
                        icon = Icons.VolumeUp,
                        iconColor = if (isSpeakerActive) accentColor else KitsugiColors.TextSecondary,
                        onClick = { routeSpeakerExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = routeSpeakerExpanded, onDismissRequest = { routeSpeakerExpanded = false }) {
                        routeDelayOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == speakerDelayMs,
                                onClick = {
                                    onRouteDelayChanged(option.first, bluetoothDelayMs, wiredDelayMs, hdmiDelayMs)
                                    routeSpeakerExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Bluetooth
                var routeBtExpanded by remember { mutableStateOf(false) }
                val isBtActive = activeAudioRoute == com.kitsugi.animelist.core.player.AudioRoute.BLUETOOTH
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.sub_delay_bt),
                        description = stringResource(R.string.sub_delay_bt_desc),
                        value = routeDelayOptions.find { it.first == bluetoothDelayMs }?.second ?: "${bluetoothDelayMs} ms",
                        icon = Icons.VolumeUp,
                        iconColor = if (isBtActive) accentColor else KitsugiColors.TextSecondary,
                        onClick = { routeBtExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = routeBtExpanded, onDismissRequest = { routeBtExpanded = false }) {
                        routeDelayOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == bluetoothDelayMs,
                                onClick = {
                                    onRouteDelayChanged(speakerDelayMs, option.first, wiredDelayMs, hdmiDelayMs)
                                    routeBtExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // Wired
                var routeWiredExpanded by remember { mutableStateOf(false) }
                val isWiredActive = activeAudioRoute == com.kitsugi.animelist.core.player.AudioRoute.WIRED
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.sub_delay_wired),
                        description = stringResource(R.string.sub_delay_wired_desc),
                        value = routeDelayOptions.find { it.first == wiredDelayMs }?.second ?: "${wiredDelayMs} ms",
                        icon = Icons.VolumeUp,
                        iconColor = if (isWiredActive) accentColor else KitsugiColors.TextSecondary,
                        onClick = { routeWiredExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = routeWiredExpanded, onDismissRequest = { routeWiredExpanded = false }) {
                        routeDelayOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == wiredDelayMs,
                                onClick = {
                                    onRouteDelayChanged(speakerDelayMs, bluetoothDelayMs, option.first, hdmiDelayMs)
                                    routeWiredExpanded = false
                                }
                            )
                        }
                    }
                }

                KitsugiSettingsDivider()

                // HDMI
                var routeHdmiExpanded by remember { mutableStateOf(false) }
                val isHdmiActive = activeAudioRoute == com.kitsugi.animelist.core.player.AudioRoute.HDMI
                Box {
                    KitsugiSettingsListItem(
                        title = stringResource(R.string.sub_delay_hdmi),
                        description = stringResource(R.string.sub_delay_hdmi_desc),
                        value = routeDelayOptions.find { it.first == hdmiDelayMs }?.second ?: "${hdmiDelayMs} ms",
                        icon = Icons.SettingsInputHdmi,
                        iconColor = if (isHdmiActive) accentColor else KitsugiColors.TextSecondary,
                        onClick = { routeHdmiExpanded = true }
                    )
                    KitsugiDropdownMenu(expanded = routeHdmiExpanded, onDismissRequest = { routeHdmiExpanded = false }) {
                        routeDelayOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == hdmiDelayMs,
                                onClick = {
                                    onRouteDelayChanged(speakerDelayMs, bluetoothDelayMs, wiredDelayMs, option.first)
                                    routeHdmiExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
