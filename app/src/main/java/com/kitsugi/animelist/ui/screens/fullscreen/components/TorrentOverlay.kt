package com.kitsugi.animelist.ui.screens.fullscreen.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun TorrentOverlay(
    visible: Boolean,
    downloadSpeedBytes: Long,
    uploadSpeedBytes: Long,
    seeders: Int,
    peers: Int,
    bufferPercent: Int,
    onDismiss: (() -> Unit)? = null,
    onInteraction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(250)) { -it / 2 } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(250)) { -it / 2 } + fadeOut(tween(250)),
        modifier = modifier
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xE6161824),
                            Color(0xE60E1018)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    onInteraction?.invoke()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.widthIn(min = 160.dp, max = 220.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.CloudDownload,
                            contentDescription = "Torrent",
                            tint = KitsugiColors.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.inline_torrent_link_86073aa),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (onDismiss != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Close,
                                contentDescription = stringResource(R.string.gizle),
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                val ds = android.text.format.Formatter.formatFileSize(LocalContext.current, downloadSpeedBytes) + "/s"
                val us = android.text.format.Formatter.formatFileSize(LocalContext.current, uploadSpeedBytes) + "/s"

                Text(
                    text = stringResource(R.string.inline_download_1_s_4c6ff04, ds),
                    color = KitsugiColors.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.inline_upload_1_s_943af78, us),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "Seeder: $seeders | Peer: $peers",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { bufferPercent / 100f },
                        color = KitsugiColors.Accent,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .width(84.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = "Tampon: $bufferPercent%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
