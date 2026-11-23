package com.kitsugi.animelist.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon

/**
 * Whether floating Nuvio glass chrome (top action buttons, pills) is currently positioned
 * over dark full-bleed hero artwork (`true`) or over normal themed page background (`false`).
 */
val LocalNuvioOverHero = compositionLocalOf { true }

/**
 * NuvioMobile-inspired frosted glass pill container used for the floating navigation bar
 * and Explore floating top chrome (source selector & action cluster).
 *
 * Adapts to both dark and light themes while keeping crisp contrast when floating over
 * full-bleed hero artwork (`overHero = true`).
 */
@Composable
fun NuvioGlassPillSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    overHero: Boolean = false,
    shadowElevation: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val isLightTheme = KitsugiColors.Background.luminance() > 0.5f
    val useDarkGlass = overHero || !isLightTheme

    val baseFill = if (useDarkGlass) {
        Color(0xFF18191D).copy(alpha = if (overHero) 0.72f else 0.86f)
    } else {
        KitsugiColors.Surface.copy(alpha = 0.92f)
    }
    val topSheen = if (useDarkGlass) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.09f),
                Color.Transparent,
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                Color.Transparent,
            )
        )
    }
    val rimStroke = if (useDarkGlass) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.06f),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.12f),
                Color.Black.copy(alpha = 0.05f),
            )
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.40f),
            )
            .clip(shape)
            .background(baseFill)
            .background(topSheen)
            .border(width = 0.85.dp, brush = rimStroke, shape = shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * NuvioMobile-inspired circular icon pill (`NuvioBackButton` / compact `NuvioViewAllPill`).
 * Keeps a 48dp minimum interactive hit target while rendering a compact circular visual.
 */
@Composable
fun NuvioCircularActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualSize: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    containerColor: Color = KitsugiColors.SurfaceElevated,
    contentColor: Color = KitsugiColors.TextPrimary,
    borderColor: Color = KitsugiColors.Border.copy(alpha = 0.55f),
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(visualSize),
            shape = CircleShape,
            color = containerColor,
            border = BorderStroke(0.85.dp, borderColor),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (enabled) contentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/**
 * Responsive poster grid column count matching NuvioMobile's `CatalogScreen`.
 */
fun nuvioCatalogGridColumnsForWidth(widthDp: Dp): Int = when {
    widthDp < 420.dp -> 3
    widthDp < 600.dp -> 4
    widthDp < 840.dp -> 5
    widthDp < 1024.dp -> 6
    else -> 7
}
