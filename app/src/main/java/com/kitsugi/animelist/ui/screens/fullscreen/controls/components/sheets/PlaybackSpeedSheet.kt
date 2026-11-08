package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

private val DEFAULT_SPEED_PRESETS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlaybackSpeedSheet(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onSetAsDefault: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { com.kitsugi.animelist.data.settings.SettingsDataStore(context) }
    val settings by store.settingsFlow.collectAsState(initial = com.kitsugi.animelist.data.settings.AppSettings())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var speedPresets by remember(settings.speedPresets) {
        mutableStateOf(settings.speedPresets.split(",").mapNotNull { it.toFloatOrNull() }
            .filter { it.isFinite() && it in 0.1f..6f }.distinct())
    }
    fun savePresets(values: List<Float>) {
        speedPresets = values.distinct().sorted()
        scope.launch { store.setSpeedPresets(speedPresets.joinToString(",")) }
    }

    PlayerSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = modifier
                .kitsugiVerticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            // Speed label + slider
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.inline_playback_speed_afada5b), style = MaterialTheme.typography.titleMedium)
                    Text("%.2fx".format(speed), style = MaterialTheme.typography.titleMedium)
                }
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.1f..6f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Preset chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = { savePresets(DEFAULT_SPEED_PRESETS) }) {
                    Icon(Icons.RestartAlt, null)
                }
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(speedPresets.sorted(), key = { index, value -> "${value}_$index" }) { _, preset ->
                        InputChip(
                            selected = speed.toFixed() == preset.toFixed(),
                            onClick = { onSpeedChange(preset) },
                            label = { Text("${preset}x") },
                            trailingIcon = {
                                Icon(
                                    Icons.Close,
                                    null,
                                    modifier = Modifier.clickable {
                                        savePresets(speedPresets - preset)
                                    },
                                )
                            },
                        )
                    }
                }
                FilledTonalIconButton(onClick = {
                    if (speed !in speedPresets) savePresets(speedPresets + speed.toFixed())
                }) {
                    Icon(Icons.Add, null)
                }
            }

            // Actions
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KitsugiButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSetAsDefault(speed) },
                ) { Text(stringResource(R.string.inline_make_default_59f76d5)) }
                FilledIconButton(onClick = { onSpeedChange(1f) }) {
                    Icon(Icons.RestartAlt, null)
                }
            }
        }
    }
}

fun Float.toFixed(precision: Int = 2): Float {
    val factor = 10.0f.pow(precision)
    return (this * factor).roundToInt() / factor
}
