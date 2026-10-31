package com.kitsugi.animelist.ui.screens.fullscreen.components
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.animation.*
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun PostPlayBingeCard(
    visible: Boolean,
    nextEpisodeTitle: String,
    nextEpisodeNumber: Int,
    thumbnailUrl: String?,
    onPlayNext: () -> Unit,
    onCancel: () -> Unit,
    countdownSeconds: Int = 10,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        0.0f to Color.White.copy(alpha = 0.10f),
                        0.4f to Color(0xFF080818).copy(alpha = 0.82f),
                        1.0f to Color(0xFF050510).copy(alpha = 0.90f)
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.inline_next_episode_starts_c3d90b8),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$countdownSeconds sn",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp, 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.SurfaceSoft)
                    ) {
                        if (!thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = thumbnailUrl,
                                contentDescription = nextEpisodeTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Tv,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.inline_episode_1_s_dc662be, nextEpisodeNumber),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = nextEpisodeTitle.ifBlank { stringResource(R.string.inline_episode_1_s_dc662be, nextEpisodeNumber) },
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
 
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    KitsugiButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.iptal), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    KitsugiButton(
                        onClick = onPlayNext,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(stringResource(R.string.simdi_oynat), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
