package com.kitsugi.animelist.ui.screens.fullscreen.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.AutoPlaySwitch
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.ControlsButton

@Composable
fun TopRightPlayerControls(
    autoPlayEnabled: Boolean,
    onToggleAutoPlay: (Boolean) -> Unit,
    onSubtitlesClick: () -> Unit,
    onSubtitlesLongClick: () -> Unit,
    onAudioClick: () -> Unit,
    onAudioLongClick: () -> Unit,
    onQualityClick: () -> Unit,
    isEpisodeOnline: Boolean?,
    onMoreClick: () -> Unit,
    onMoreLongClick: () -> Unit,
    onOpenExternalClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AutoPlaySwitch(
            isChecked = autoPlayEnabled,
            onToggleAutoPlay = onToggleAutoPlay,
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 8.dp)
                .size(width = 48.dp, height = 24.dp),
        )
        ControlsButton(
            icon = Icons.Subtitles,
            onClick = onSubtitlesClick,
            onLongClick = onSubtitlesLongClick,
            horizontalSpacing = 8.dp,
        )
        ControlsButton(
            icon = Icons.Audiotrack,
            onClick = onAudioClick,
            onLongClick = onAudioLongClick,
            horizontalSpacing = 8.dp,
        )
        if (isEpisodeOnline == true) {
            ControlsButton(
                icon = Icons.HighQuality,
                onClick = onQualityClick,
                onLongClick = onQualityClick,
                horizontalSpacing = 8.dp,
            )
        }
        if (onOpenExternalClick != null) {
            ControlsButton(
                icon = Icons.OpenInNew,
                onClick = onOpenExternalClick,
                onLongClick = onOpenExternalClick,
                horizontalSpacing = 8.dp,
            )
        }
        ControlsButton(
            icon = Icons.MoreVert,
            onClick = onMoreClick,
            onLongClick = onMoreLongClick,
            horizontalSpacing = 8.dp,
        )
    }
}
