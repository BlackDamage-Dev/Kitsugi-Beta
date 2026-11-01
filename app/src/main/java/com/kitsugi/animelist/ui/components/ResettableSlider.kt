package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Slider

/** A settings slider with an always-visible, accessible reset-to-default action. */
@Composable
fun ResettableSlider(
    value: Float,
    defaultValue: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onValueChange(defaultValue.coerceIn(valueRange.start, valueRange.endInclusive)) },
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            KitsugiArtIcon(art = KitsugiArt.Sync, contentDescription = stringResource(R.string.slider_reset_to_default), size = 20.dp)
        }
    }
}
