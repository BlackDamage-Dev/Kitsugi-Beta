package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

@Composable
fun <T> GenericTracksSheet(
    tracks: List<T>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEvent: Boolean = false,
    header: @Composable () -> Unit = {},
    track: @Composable (T) -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    PlayerSheet(onDismissRequest, dismissEvent = dismissEvent) {
        Column(modifier) {
            header()
            LazyColumn {
                items(tracks) { track(it) }
                item { footer() }
            }
        }
    }
}

/** Parçanın gerçek codec/biçim adını listede kompakt ve taşmayan bir rozetle gösterir. */
@Composable
fun TrackFormatTag(
    value: String,
    modifier: Modifier = Modifier,
) {
    if (value.isBlank()) return
    val accent = LocalKitsugiAccent.current
    Surface(
        modifier = modifier.widthIn(max = 112.dp),
        shape = RoundedCornerShape(7.dp),
        color = accent.copy(alpha = 0.14f),
        contentColor = accent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
    ) {
        Text(
            text = value,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun AddTrackRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .fillMaxHeight()
                .weight(1f)
                .padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Add, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(text = title)
        }
        actions()
    }
}

@Composable
fun TrackSheetTitle(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) { actions() }
    }
}

@Composable
fun SheetTitle(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) = TrackSheetTitle(title, modifier, actions)
