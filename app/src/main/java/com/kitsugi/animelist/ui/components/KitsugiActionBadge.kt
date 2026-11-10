package com.kitsugi.animelist.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp

/**
 * Non-interactive corner mark for tappable pictures, including Markdown images.
 * Draw-only: keeps original image measurement, modifier scope, gestures and semantics.
 */
@Composable
fun Modifier.kitsugiActionBadge(
    glyph: ImageVector,
    visible: Boolean = true,
    alignment: Alignment = Alignment.BottomEnd
): Modifier {
    if (!visible) return this
    val painter = rememberVectorPainter(glyph)
    return drawWithContent {
        drawContent()
        val edge = minOf(24.dp.toPx(), size.width, size.height)
        if (edge <= 0f) return@drawWithContent
        val inset = minOf(3.dp.toPx(), edge / 8f)
        val position = alignment.align(
            IntSize(edge.roundToInt(), edge.roundToInt()),
            IntSize((size.width - inset * 2f).roundToInt().coerceAtLeast(0), (size.height - inset * 2f).roundToInt().coerceAtLeast(0)),
            layoutDirection
        )
        val x = (position.x + inset).coerceAtLeast(0f)
        val y = (position.y + inset).coerceAtLeast(0f)
        drawRoundRect(
            Color.Black.copy(alpha = 0.65f), topLeft = Offset(x, y),
            size = Size(edge, edge), cornerRadius = CornerRadius(edge / 4f)
        )
        withTransform({ translate(x + inset, y + inset) }) {
            with(painter) {
                draw(Size(edge - inset * 2f, edge - inset * 2f), colorFilter = ColorFilter.tint(Color.White))
            }
        }
    }
}
