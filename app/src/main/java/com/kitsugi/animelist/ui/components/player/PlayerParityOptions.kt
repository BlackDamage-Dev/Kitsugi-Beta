package com.kitsugi.animelist.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.components.KitsugiSettingsItem
import com.kitsugi.animelist.ui.components.KitsugiSettingsSection
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Keep persisted keys/values intact; each group is a separate lazy item. */
fun LazyListScope.playerParityOptions(settings: AppSettings, store: SettingsDataStore, scope: CoroutineScope) {
    item(key = "player_advanced_audio") { AudioOptions(settings, store, scope) }
    item(key = "player_advanced_subtitles") { SubtitlesOptions(settings, store, scope) }
    item(key = "player_advanced_appearance") { AppearanceOptions(settings, store, scope) }
    item(key = "player_advanced_memory") { MemoryOptions(settings, store, scope) }
    item(key = "player_advanced_pip") { PipOptions(settings, store, scope) }
    item(key = "player_advanced_skipping") { SkippingOptions(settings, store, scope) }
    item(key = "player_advanced_gestures") { GesturesOptions(settings, store, scope) }
}

@Composable
private fun AudioOptions(settings: AppSettings, store: SettingsDataStore, scope: CoroutineScope) {
    ParitySection(stringResource(R.string.player_advanced_audio)) {
        SavedTextOption(
            stringResource(R.string.player_audio_languages),
            settings.preferredAudioLanguages,
            Icons.VolumeUp
        ) { scope.launch { store.setPreferredAudioLanguages(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_pitch),
            settings.audioPitchCorrection
        ) { scope.launch { store.setAudioPitchCorrection(it) } }
    }
}

@Composable
private fun SubtitlesOptions(settings: AppSettings, store: SettingsDataStore, scope: CoroutineScope) {
    ParitySection(stringResource(R.string.player_advanced_subtitles)) {
        SavedTextOption(
            stringResource(R.string.player_subtitle_whitelist),
            settings.subtitleWhitelist,
            Icons.Subtitles
        ) { scope.launch { store.setSubtitleWhitelist(it) } }
        SavedTextOption(
            stringResource(R.string.player_subtitle_blacklist),
            settings.subtitleBlacklist,
            Icons.Subtitles
        ) { scope.launch { store.setSubtitleBlacklist(it) } }
        SavedTextOption(
            stringResource(R.string.player_subtitle_font),
            settings.subtitleFont,
            Icons.TextFields
        ) { scope.launch { store.setSubtitleFont(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_ass),
            settings.subtitleOverrideAss
        ) { scope.launch { store.setSubtitleOverrideAss(it) } }
        ParitySliderOption(
            value = settings.subtitleFontScale,
            valueRange = 0.5f..3f,
            steps = 0,
            label = { stringResource(R.string.player_font_scale, it) }
        ) { scope.launch { store.setSubtitleFontScale(it) } }
        ParitySliderOption(
            value = settings.subtitlePos.toFloat(),
            valueRange = 0f..150f,
            steps = 0,
            label = { stringResource(R.string.player_sub_position, it.toInt()) }
        ) { scope.launch { store.setSubtitlePos(it.toInt()) } }
        ParityChoiceOption(
            stringResource(R.string.player_border_style),
            settings.subtitleBorderStyle,
            listOf("outline-and-shadow" to stringResource(R.string.player_advanced_border_outline), "opaque-box" to stringResource(R.string.player_advanced_border_opaque), "background-box" to stringResource(R.string.player_advanced_border_background)),
            Icons.Subtitles
        ) { scope.launch { store.setSubtitleBorderStyle(it) } }
    }
}

@Composable
private fun AppearanceOptions(settings: AppSettings, store: SettingsDataStore, scope: CoroutineScope) {
    ParitySection(stringResource(R.string.player_advanced_appearance)) {
        ParitySwitchRow(
            stringResource(R.string.player_advanced_loading),
            settings.showLoadingCircle
        ) { scope.launch { store.setShowLoadingCircle(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_chapter),
            settings.showCurrentChapter
        ) { scope.launch { store.setShowCurrentChapter(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_remaining),
            settings.invertDurationTimer
        ) { scope.launch { store.setInvertDurationTimer(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_volume),
            settings.displayVolumeAsPercentage
        ) { scope.launch { store.setDisplayVolumeAsPercentage(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_seek),
            settings.showSeekBarOnGesture
        ) { scope.launch { store.setShowSeekBarOnGesture(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_motion),
            settings.reducePlayerMotion
        ) { scope.launch { store.setReducePlayerMotion(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_status),
            settings.showSystemStatusBar
        ) { scope.launch { store.setShowSystemStatusBar(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_cutout),
            settings.playerFullscreenCutout
        ) { scope.launch { store.setPlayerFullscreenCutout(it) } }
        ParitySliderOption(
            value = settings.playerTimeToDisappearMs / 1000f,
            valueRange = 1f..15f,
            steps = 13,
            label = { stringResource(R.string.player_advanced_hide_time, it.toInt()) }
        ) { scope.launch { store.setPlayerTimeToDisappearMs(it.toInt() * 1000) } }
        ParitySliderOption(
            value = settings.panelOpacity.toFloat(),
            valueRange = 40f..100f,
            steps = 0,
            label = { stringResource(R.string.player_advanced_opacity, it.toInt()) }
        ) { scope.launch { store.setPanelOpacity(it.toInt()) } }
    }
}

@Composable
private fun MemoryOptions(settings: AppSettings, store: SettingsDataStore, scope: CoroutineScope) {
    ParitySection(stringResource(R.string.player_advanced_memory)) {
        ParitySwitchRow(
            stringResource(R.string.player_advanced_position),
            settings.preserveWatchingPosition
        ) { scope.launch { store.setPreserveWatchingPosition(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_brightness),
            settings.rememberPlayerBrightness
        ) { scope.launch { store.setRememberPlayerBrightness(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_remember_volume),
            settings.rememberPlayerVolume
        ) { scope.launch { store.setRememberPlayerVolume(it) } }
        ParitySliderOption(
            value = settings.scrobbleThreshold * 100f,
            valueRange = 50f..100f,
            steps = 0,
            label = { stringResource(R.string.player_advanced_scrobble, it.toInt()) }
        ) { scope.launch { store.setScrobbleThreshold(it / 100f) } }
    }
}

@Composable
private fun PipOptions(settings: AppSettings, store: SettingsDataStore, scope: CoroutineScope) {
    ParitySection(stringResource(R.string.player_advanced_pip)) {
        ParitySwitchRow(
            stringResource(R.string.player_advanced_pip_toasts),
            settings.pipEpisodeToasts
        ) { scope.launch { store.setPipEpisodeToasts(it) } }
        ParitySwitchRow(stringResource(R.string.player_advanced_pip_exit), settings.pipOnExit) { scope.launch { store.setPipOnExit(it) } }
    }
}

@Composable
private fun SkippingOptions(settings: AppSettings, store: SettingsDataStore, scope: CoroutineScope) {
    ParitySection(stringResource(R.string.player_advanced_skipping)) {
        ParitySwitchRow(
            stringResource(R.string.player_advanced_skip_button),
            settings.enableSkipIntro
        ) { scope.launch { store.setEnableSkipIntro(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_skip_chapters),
            settings.aniSkipDisableOnChapters
        ) { scope.launch { store.setAniSkipDisableOnChapters(it) } }
        ParitySwitchRow(
            stringResource(R.string.player_advanced_skip_auto),
            settings.enableNetflixStyleIntroSkip
        ) { scope.launch { store.setEnableNetflixStyleIntroSkip(it) } }
        if (settings.enableNetflixStyleIntroSkip) {
            ParitySliderOption(
                value = settings.waitingTimeIntroSkip.toFloat(),
                valueRange = 1f..15f,
                steps = 13,
                label = { stringResource(R.string.player_advanced_skip_wait, it.toInt()) }
            ) { scope.launch { store.setWaitingTimeIntroSkip(it.toInt()) } }
        }
    }
}

@Composable
private fun GesturesOptions(settings: AppSettings, store: SettingsDataStore, scope: CoroutineScope) {
    ParitySection(stringResource(R.string.player_advanced_gestures)) {
        val options = listOf("SEEK" to stringResource(R.string.player_advanced_gesture_seek), "PLAY_PAUSE" to stringResource(R.string.player_advanced_gesture_play), "CUSTOM" to stringResource(R.string.player_advanced_gesture_custom), "SWITCH_SUB" to stringResource(R.string.player_advanced_gesture_sub), "NONE" to stringResource(R.string.player_advanced_gesture_none))
        ParityChoiceOption(
            stringResource(R.string.player_advanced_tap_left),
            settings.leftDoubleTapGesture,
            options,
            Icons.TouchApp
        ) { scope.launch { store.setLeftDoubleTapGesture(it) } }
        ParityChoiceOption(
            stringResource(R.string.player_advanced_tap_center),
            settings.centerDoubleTapGesture,
            options,
            Icons.TouchApp
        ) { scope.launch { store.setCenterDoubleTapGesture(it) } }
        ParityChoiceOption(
            stringResource(R.string.player_advanced_tap_right),
            settings.rightDoubleTapGesture,
            options,
            Icons.TouchApp
        ) { scope.launch { store.setRightDoubleTapGesture(it) } }
    }
}

@Composable
private fun ParitySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val accent = LocalKitsugiAccent.current
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = accent,
        surface = KitsugiColors.Surface,
        onSurface = KitsugiColors.TextPrimary,
        onSurfaceVariant = KitsugiColors.TextSecondary,
        secondaryContainer = KitsugiColors.SurfaceStrong
    )) {
        KitsugiSettingsSection(title = title, modifier = Modifier.padding(bottom = 12.dp), content = content)
    }
}

@Composable
private fun ParitySwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = KitsugiColors.TextPrimary)
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(
            checkedTrackColor = LocalKitsugiAccent.current,
            checkedThumbColor = KitsugiColors.Background,
            uncheckedTrackColor = KitsugiColors.SurfaceStrong,
            uncheckedThumbColor = KitsugiColors.TextSecondary
        ))
    }
}

@Composable
private fun ParitySliderOption(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    label: @Composable (Float) -> String,
    onSave: (Float) -> Unit
) {
    var draft by remember(value) { mutableFloatStateOf(value.coerceIn(valueRange)) }
    val title = label(draft)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = KitsugiColors.TextPrimary)
        Slider(value = draft, onValueChange = { draft = it }, valueRange = valueRange,
            steps = steps, onValueChangeFinished = { onSave(draft) },
            modifier = Modifier.semantics { contentDescription = title },
            colors = SliderDefaults.colors(
                thumbColor = LocalKitsugiAccent.current,
                activeTrackColor = LocalKitsugiAccent.current,
                inactiveTrackColor = KitsugiColors.SurfaceStrong
            ))
    }
}

/** Editors are opened on demand instead of leaving four forms on the page. */
@Composable
private fun SavedTextOption(label: String, value: String, icon: ImageVector, onSave: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    KitsugiSettingsItem(title = label,
        description = value.ifBlank { stringResource(R.string.player_advanced_not_set) },
        icon = icon, iconColor = LocalKitsugiAccent.current, onClick = { editing = true })
    if (editing) {
        var draft by rememberSaveable(value) { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = KitsugiColors.Surface,
            title = { Text(label, color = KitsugiColors.TextPrimary) },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it },
                    label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { onSave(draft.trim()); editing = false }, enabled = draft.trim() != value) {
                    Text(stringResource(R.string.action_save), color = if (draft.trim() != value)
                        LocalKitsugiAccent.current else KitsugiColors.TextMuted)
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.action_cancel), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ParityChoiceOption(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    icon: ImageVector,
    onSave: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    KitsugiSettingsItem(title = label, description = options.firstOrNull { it.first == value }?.second ?: value,
        icon = icon, iconColor = LocalKitsugiAccent.current, onClick = { expanded = true })
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            containerColor = KitsugiColors.Surface,
            title = { Text(label, color = KitsugiColors.TextPrimary) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(options, key = { it.first }) { (key, title) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = value == key, role = Role.RadioButton,
                                    onClick = { onSave(key); expanded = false })
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = value == key, onClick = null)
                            Text(title, modifier = Modifier.weight(1f), color = KitsugiColors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { expanded = false }) {
                    Text(stringResource(R.string.action_cancel), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }
}
