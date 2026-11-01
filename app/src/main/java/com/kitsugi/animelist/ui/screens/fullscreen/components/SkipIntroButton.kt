package com.kitsugi.animelist.ui.screens.fullscreen.components

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.delay

@Composable
fun SkipIntroButton(
    visible: Boolean,
    typeLabel: String, // e.g. "Giriş", "Bitiş", "Özet"
    durationSeconds: Int = 10,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remainingSec by remember(visible) { mutableIntStateOf(durationSeconds) }
    
    LaunchedEffect(visible) {
        if (visible) {
            remainingSec = durationSeconds
            while (remainingSec > 0) {
                delay(1000)
                remainingSec -= 1
            }
        }
    }

    val shouldShow = visible && remainingSec > 0
    val accentColor = LocalKitsugiAccent.current

    // durationSeconds 0 gelirse (veya kalan süre beklenmedik biçimde hedefi aşarsa)
    // bölme 0/NaN/1'in üzerinde bir oran üretir; Modifier.fillMaxWidth geçersiz
    // oran değerinde IllegalArgumentException atarak çökmeye yol açıyordu.
    val countdownFraction = if (durationSeconds > 0) {
        (remainingSec.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.8f),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        Card(
            onClick = onSkip,
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitsugiArtIcon(art = KitsugiArt.Next, size = 20.dp)
                    Text(
                        text = "$typeLabel Atla",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Countdown bar at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(countdownFraction)
                            .height(3.dp)
                            .background(accentColor)
                    )
                }
            }
        }
    }
}
