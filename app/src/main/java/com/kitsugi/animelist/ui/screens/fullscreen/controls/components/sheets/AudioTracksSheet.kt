package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.RadioButton
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

data class AudioTrackInfo(
    val id: Int,
    val label: String,
    val language: String? = null,
)

@Composable
fun AudioTracksSheet(
    tracks: List<AudioTrackInfo>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onAddAudioFile: () -> Unit,
    onOpenDelayPanel: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GenericTracksSheet(
        tracks = tracks,
        onDismissRequest = onDismissRequest,
        header = {
            TrackSheetTitle(
                title = stringResource(R.string.ses_parcalari),
                actions = {
                    TextButton(onClick = onOpenDelayPanel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(imageVector = Icons.MoreTime, contentDescription = null)
                            Text("Gecikme")
                        }
                    }
                },
            )
            AddTrackRow(title = KitsugiStrings.get(R.string.harici_ses_dosyasi_ekle), onClick = onAddAudioFile)
        },
        track = { track ->
            AudioTrackRow(
                title = buildAudioTitle(track),
                isSelected = selectedId == track.id,
                onClick = { onSelect(track.id) },
            )
        },
        modifier = modifier,
    )
}

@Composable
fun AudioTrackRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
        )
    }
}

private fun buildAudioTitle(track: AudioTrackInfo): String {
    return when {
        track.id == -1 -> track.label
        track.language.isNullOrBlank() -> "${track.id}. ${track.label}"
        track.label.isNotBlank() -> "${track.id}. ${track.label} [${track.language}]"
        else -> "${track.id}. [${track.language}]"
    }
}
