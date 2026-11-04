package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.background

/** A decorative action badge on tappable artwork; the original hit target is preserved. */
@Composable
fun KitsugiArtworkAction(
    glyph: ImageVector,
    modifier: Modifier = Modifier,
    showMark: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        content()
        if (showMark) Box(
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(3.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                .padding(3.dp)
        ) {
            Icon(glyph, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
