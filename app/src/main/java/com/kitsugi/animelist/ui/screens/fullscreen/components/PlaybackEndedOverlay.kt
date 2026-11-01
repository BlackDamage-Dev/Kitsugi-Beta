package com.kitsugi.animelist.ui.screens.fullscreen.components

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun PlaybackEndedOverlay(
    visible: Boolean,
    hasNextEpisode: Boolean,
    onReplay: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.bolum_bitti),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KitsugiButton(
                    onClick = onReplay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    KitsugiArtIcon(art = KitsugiArt.History, contentDescription = stringResource(R.string.yeniden_oynat), size = 20.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.yeniden_oynat), fontWeight = FontWeight.SemiBold)
                }

                if (hasNextEpisode) {
                    KitsugiButton(
                        onClick = onPlayNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KitsugiColors.Accent,
                            contentColor = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        KitsugiArtIcon(art = KitsugiArt.Next, contentDescription = stringResource(R.string.sonraki_bolum), size = 20.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.sonraki_bolum), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
