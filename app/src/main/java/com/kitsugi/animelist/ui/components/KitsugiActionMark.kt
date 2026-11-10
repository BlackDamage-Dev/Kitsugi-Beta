package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.gradient.Icon

/** Decorative action mark: the adjacent localized label supplies accessibility text. */
@Composable
fun RowScope.KitsugiActionMark(glyph: ImageVector) {
    Icon(imageVector = glyph, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
}
