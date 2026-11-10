package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption
import com.kitsugi.animelist.ui.theme.gradient.RadioButton
import com.kitsugi.animelist.ui.theme.gradient.Text

/** Deliberately has no StreamSource input: provider links are not video qualities. */
@Composable
fun VideoQualitySheet(
    tracks: List<TrackOption>,
    isAuto: Boolean,
    onSelect: (TrackOption?) -> Unit,
    onDismissRequest: () -> Unit,
    dismissSheet: Boolean = false,
) {
    PlayerSheet(onDismissRequest = onDismissRequest, dismissEvent = dismissSheet) {
        Column {
            TrackSheetTitle(title = stringResource(R.string.player_video_quality))
            if (tracks.size <= 1) {
                Text(
                    text = stringResource(R.string.player_video_quality_unavailable),
                    modifier = Modifier.padding(16.dp),
                )
                tracks.firstOrNull()?.let { Text(it.label, modifier = Modifier.padding(16.dp)) }
            } else {
                LazyColumn {
                    item {
                        VideoQualityRow(stringResource(R.string.player_video_quality_auto), isAuto) { onSelect(null) }
                    }
                    items(tracks) { track ->
                        VideoQualityRow(track.label, !isAuto && track.isSelected) { onSelect(track) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoQualityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
