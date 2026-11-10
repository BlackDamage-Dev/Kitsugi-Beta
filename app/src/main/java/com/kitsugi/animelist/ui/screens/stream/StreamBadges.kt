package com.kitsugi.animelist.ui.screens.stream

import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint

/**
 * A pill-shaped chip showing an addon's loading/success/error status,
 * with animated pulsing while loading.
 */
@Composable
fun AddonStatusChip(
    state: AddonFetchState,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "chipPulse"
    )

    val bgColor = when {
        isSelected                 -> accentColor.copy(alpha = 0.35f)
        state.isLoading            -> accentColor.copy(alpha = pulseAlpha * 0.25f)
        state.streams.isNotEmpty() -> accentColor.copy(alpha = 0.12f)
        state.error != null        -> Color.Red.copy(alpha = 0.15f)
        else                       -> Color.White.copy(alpha = 0.08f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .tvClickable(shape = RoundedCornerShape(999.dp)) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = accentColor
                )
            }
            state.addonName == "Tümü" -> {
                Icon(
                    imageVector = Icons.Extension,
                    contentDescription = null,
                    tint = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
            state.streams.isNotEmpty() -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        }
        Text(
            text = buildString {
                append(state.addonName)
                // v2.4.239: "Tümü" çipi de toplam kaynak sayısını gösterir — kullanıcının
                // "tüm video kaynakları listede mi?" sorusunu ekranda doğrudan yanıtlar.
                if (state.streams.isNotEmpty()) {
                    append("  ${state.streams.size}")
                }
            },
            color = if (isSelected || state.streams.isNotEmpty()) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected || state.streams.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
        )
        if (state.error != null && state.errorCode != null) {
            PlatformErrorCodeHint(code = state.errorCode)
        }
    }
}
