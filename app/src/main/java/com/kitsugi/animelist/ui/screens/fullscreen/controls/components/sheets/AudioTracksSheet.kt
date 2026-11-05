package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.RadioButton
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

data class AudioTrackInfo(
    val id: Int,
    val label: String,
    val language: String? = null,
    /** AAC, AC-3, E-AC-3, DTS, TrueHD, FLAC, Opus vb. gerçek ses codec'i. */
    val formatTag: String? = null,
    val channelCount: Int? = null,
)

@Composable
fun AudioTracksSheet(
    tracks: List<AudioTrackInfo>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onDownload: (Int) -> Unit,
    providerAudioIndices: List<Int> = emptyList(),
    onDownloadProvider: (Int) -> Unit = {},
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
                formatTag = track.formatTag,
                channelCount = track.channelCount,
                isSelected = selectedId == track.id,
                onClick = { onSelect(track.id) },
                onDownload = { onDownload(track.id) },
            )
        },
        footer = {
            // CloudStream AudioFile can provide several distinct URLs. Media3/mpv may only
            // expose the first one in the player's row list; the others remain downloadable.
            providerAudioIndices.forEach { index ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.eklentinin_ayri_sesi, index + 1),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDownloadProvider(index) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Download,
                            contentDescription = stringResource(R.string.ses_parcasini_indir),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun AudioTrackRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    formatTag: String? = null,
    channelCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (channelCount != null && channelCount > 0) {
            TrackFormatTag(
                value = when (channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> "$channelCount kanal"
                }
            )
        }
        if (!formatTag.isNullOrBlank()) {
            TrackFormatTag(value = formatTag)
        }
        IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Download,
                contentDescription = stringResource(R.string.ses_parcasini_indir),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun buildAudioTitle(track: AudioTrackInfo): String {
    if (track.id == -1) return track.label
    val rawLanguage = track.language?.trim().orEmpty()
    val friendlyLanguage = rawLanguage.takeIf { it.isNotBlank() }
        ?.let { com.kitsugi.animelist.core.player.PlayerSubtitleUtils.getFriendlyLanguageName(it) }
    val showLanguage = !friendlyLanguage.isNullOrBlank() &&
        !track.label.contains(rawLanguage, ignoreCase = true) &&
        !track.label.contains(friendlyLanguage, ignoreCase = true)
    return buildString {
        append(track.id)
        append(". ")
        if (track.label.isNotBlank()) append(track.label)
        if (showLanguage) {
            if (track.label.isNotBlank()) append(' ')
            append('[')
            append(friendlyLanguage)
            append(']')
        }
    }.trim()
}
