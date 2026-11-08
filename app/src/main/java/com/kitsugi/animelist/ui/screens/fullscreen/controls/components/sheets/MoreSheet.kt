package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.ui.screens.fullscreen.Decoder
import com.kitsugi.animelist.ui.screens.fullscreen.AudioChannels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.kitsugi.animelist.data.local.CustomButton
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MoreSheet(
    selectedDecoder: Decoder,
    onSelectDecoder: (Decoder) -> Unit,
    remainingTime: Int,
    onStartTimer: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    onEnterFiltersPanel: () -> Unit,
    customButtons: List<CustomButton>,
    onClickCustomButton: (CustomButton) -> Unit,
    onLongClickCustomButton: (CustomButton) -> Unit,
    statisticsPage: Int = 0,
    onSelectStatisticsPage: (Int) -> Unit = {},
    audioChannels: AudioChannels = AudioChannels.Auto,
    onSelectAudioChannels: (AudioChannels) -> Unit = {},
    onOpenExternalPlayer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    PlayerSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .kitsugiVerticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.daha_fazla),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    var isSleepTimerDialogShown by remember { mutableStateOf(false) }
                    TextButton(onClick = { isSleepTimerDialogShown = true }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(imageVector = Icons.Timer, contentDescription = null)
                            Text(
                                text = if (remainingTime == 0) {
                                    stringResource(R.string.ui_timer_label)
                                } else {
                                    DateUtils.formatElapsedTime(remainingTime.toLong())
                                },
                            )
                            if (isSleepTimerDialogShown) {
                                TimePickerDialog(
                                    remainingTime = remainingTime,
                                    onDismissRequest = { isSleepTimerDialogShown = false },
                                    onTimeSelect = onStartTimer,
                                )
                            }
                        }
                    }
                    TextButton(onClick = onEnterFiltersPanel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(imageVector = Icons.Tune, contentDescription = null)
                            Text(text = stringResource(R.string.filtreler))
                        }
                    }
                    if (onOpenExternalPlayer != null) {
                        TextButton(onClick = {
                            onDismissRequest()
                            onOpenExternalPlayer()
                        }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(imageVector = Icons.OpenInNew, contentDescription = null)
                                Text(text = stringResource(R.string.player_btn_external))
                            }
                        }
                    }
                }
            }

            Text(stringResource(R.string.inline_hardware_decoder_mode_335c79b), style = MaterialTheme.typography.titleMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Decoder.entries) { decoder ->
                    FilterChip(
                        selected = decoder == selectedDecoder,
                        onClick = { onSelectDecoder(decoder) },
                        label = { Text(text = decoder.title) },
                    )
                }
            }

            Text(stringResource(R.string.inline_statistics_page_403a430), style = MaterialTheme.typography.titleMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = 6) { page: Int ->
                    FilterChip(
                        selected = statisticsPage == page,
                        onClick = {
                            // MPV komutları ViewModel'da çalıştırılır
                            onSelectStatisticsPage(page)
                        },
                        label = {
                            Text(
                                text = if (page == 0) stringResource(R.string.player_afr_off) else "Sayfa $page"
                            )
                        },
                    )
                }
            }

            if (customButtons.isNotEmpty()) {
                Text(stringResource(R.string.inline_custom_buttons_7a0fd0e), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = Int.MAX_VALUE,
                ) {
                    customButtons.forEach { button ->
                        val inputChipInteractionSource = remember { MutableInteractionSource() }
                        Box {
                            FilterChip(
                                onClick = {},
                                label = { Text(text = button.name) },
                                selected = false,
                                interactionSource = inputChipInteractionSource,
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .combinedClickable(
                                        onClick = { onClickCustomButton(button) },
                                        onLongClick = { onLongClickCustomButton(button) },
                                        interactionSource = inputChipInteractionSource,
                                        indication = null,
                                    ),
                            )
                        }
                    }
                }
            }

            Text(stringResource(R.string.inline_audio_channels_d078a64), style = MaterialTheme.typography.titleMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = AudioChannels.entries.toList()) { ch: AudioChannels ->
                    FilterChip(
                        selected = audioChannels == ch,
                        onClick = { onSelectAudioChannels(ch) },
                        label = { Text(text = ch.title) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onTimeSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    remainingTime: Int = 0,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier.padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .kitsugiVerticalScroll(rememberScrollState())
                    .width(IntrinsicSize.Max)
                    .animateContentSize()
                    .padding(16.dp),
            ) {
                var currentLayoutType by rememberSaveable { mutableIntStateOf(0) }
                Text(
                    text = if (currentLayoutType == 1) {
                        stringResource(R.string.ui_timer_select)
                    } else {
                        "Zamanlayıcı Gir"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val state = rememberTimePickerState(
                    remainingTime / 3600,
                    (remainingTime % 3600) / 60,
                    is24Hour = true,
                )
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    if (currentLayoutType == 1) {
                        TimePicker(state = state)
                    } else {
                        TimeInput(state = state)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentLayoutType = if (currentLayoutType == 0) 1 else 0 }) {
                        Icon(
                            imageVector = if (currentLayoutType == 0) {
                                Icons.Schedule
                            } else {
                                Icons.KeyboardAlt
                            },
                            contentDescription = null,
                        )
                    }
                    Row {
                        if (remainingTime > 0) {
                            TextButton(onClick = {
                                onTimeSelect(0)
                                onDismissRequest()
                            }) {
                                Text(stringResource(R.string.iptal_et))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                onTimeSelect(state.hour * 3600 + state.minute * 60)
                                onDismissRequest()
                            },
                        ) {
                            Text(stringResource(R.string.settings_ok))
                        }
                    }
                }
            }
        }
    }
}
