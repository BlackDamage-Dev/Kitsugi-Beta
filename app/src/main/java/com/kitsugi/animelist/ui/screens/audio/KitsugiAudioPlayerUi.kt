package com.kitsugi.animelist.ui.screens.audio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.player.audio.player.AudioFormatSupport
import com.kitsugi.animelist.core.player.audio.player.AudioPlayerItem
import com.kitsugi.animelist.core.player.audio.player.AudioPlayerState
import com.kitsugi.animelist.core.player.audio.player.AudioRepeatMode
import com.kitsugi.animelist.core.player.audio.player.KitsugiAudioPlayerManager
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private enum class ActiveAudioToolPanel {
    NONE,
    SPEED_AND_PITCH,
    VOLUME_BOOST,
    SLEEP_TIMER,
    QUEUE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiAudioPlayerHost(
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember(context) { KitsugiAudioPlayerManager.getInstance(context) }
    val state by manager.state.collectAsState()
    val artworkBitmap by manager.artworkBitmap.collectAsState()

    AnimatedVisibility(
        visible = state.isActive,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val currentItem = state.currentItem
        if (currentItem != null) {
            KitsugiMiniAudioPlayerBar(
                state = state,
                item = currentItem,
                artworkBitmap = artworkBitmap,
                onExpand = { manager.setExpanded(true) },
                onTogglePlay = { manager.togglePlayPause() },
                onPrev = { manager.skipPrevious() },
                onNext = { manager.skipNext() },
                onClose = { manager.stopAndClear() },
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .padding(bottom = bottomPadding)
            )
        }
    }

    if (state.isActive && state.isExpanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { manager.setExpanded(false) },
            sheetState = sheetState,
            containerColor = KitsugiColors.SurfaceStrong,
            contentColor = KitsugiColors.TextPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            KitsugiAudioPlayerFullContent(
                state = state,
                artworkBitmap = artworkBitmap,
                manager = manager,
                onCollapse = { manager.setExpanded(false) },
                onClosePlayer = { manager.stopAndClear() },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }
}

@Composable
fun KitsugiMiniAudioPlayerBar(
    state: AudioPlayerState,
    item: AudioPlayerItem,
    artworkBitmap: Bitmap?,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalKitsugiAccent.current
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(18.dp),
        color = KitsugiColors.SurfaceStrong,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioArtworkThumb(
                    artworkBitmap = artworkBitmap,
                    artworkUrlOrPath = item.artworkUrlOrPath,
                    isPlaying = state.isPlaying,
                    accent = accent,
                    size = 44.dp
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = KitsugiColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metaLine = buildString {
                        val badge = state.streamSpecs.codec ?: item.formatBadge
                        if (badge.isNotBlank()) append(badge)
                        if (!item.trackLabel.isNullOrBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(item.trackLabel)
                        }
                        if (state.durationMs > 0L) {
                            if (isNotEmpty()) append(" • ")
                            append(AudioFormatSupport.formatDuration(state.positionMs))
                            append(" / ")
                            append(AudioFormatSupport.formatDuration(state.durationMs))
                        }
                    }
                    Text(
                        text = metaLine,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.SkipPrevious,
                        contentDescription = stringResource(R.string.audio_player_previous),
                        tint = KitsugiColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    color = accent,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.isBuffering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Pause else Icons.PlayArrow,
                                contentDescription = if (state.isPlaying) {
                                    stringResource(R.string.audio_player_pause)
                                } else {
                                    stringResource(R.string.audio_player_play)
                                },
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.SkipNext,
                        contentDescription = stringResource(R.string.audio_player_next),
                        tint = KitsugiColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.audio_player_close),
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = accent,
                trackColor = KitsugiColors.SurfaceElevated
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiAudioPlayerFullContent(
    state: AudioPlayerState,
    artworkBitmap: Bitmap?,
    manager: KitsugiAudioPlayerManager,
    onCollapse: () -> Unit,
    onClosePlayer: () -> Unit,
    isStandaloneScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalKitsugiAccent.current
    val item = state.currentItem ?: return
    var activeTool by remember { mutableStateOf(ActiveAudioToolPanel.NONE) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .kitsugiVerticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Üst başlık çubuğu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (isStandaloneScreen) Icons.ArrowBack else Icons.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.audio_player_collapse),
                    tint = KitsugiColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.audio_player_header_title),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = state.backendKind.shortLabel,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = {
                    onClosePlayer()
                    if (isStandaloneScreen) onCollapse()
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = stringResource(R.string.audio_player_close),
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Albüm kapağı / Poster / Canlı ekolayzır diski
        Box(
            modifier = Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.26f),
                            KitsugiColors.SurfaceElevated,
                            KitsugiColors.Background
                        )
                    )
                )
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                artworkBitmap != null && !artworkBitmap.isRecycled -> {
                    Image(
                        bitmap = artworkBitmap.asImageBitmap(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                !item.artworkUrlOrPath.isNullOrBlank() -> {
                    AsyncImage(
                        model = item.artworkUrlOrPath,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Headphones,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        KitsugiLiveAudioEqualizerBars(
                            isPlaying = state.isPlaying,
                            color = accent,
                            barCount = 5,
                            maxHeight = 22.dp
                        )
                    }
                }
            }

            if (artworkBitmap != null || !item.artworkUrlOrPath.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    KitsugiLiveAudioEqualizerBars(
                        isPlaying = state.isPlaying,
                        color = accent,
                        barCount = 4,
                        maxHeight = 14.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Parça başlığı & alt başlık
        Text(
            text = item.title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        val secondaryLine = listOfNotNull(
            item.trackLabel?.takeIf { it.isNotBlank() },
            item.subtitle?.takeIf { it.isNotBlank() && it != item.trackLabel }
        ).distinct().joinToString(" • ")

        if (secondaryLine.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = secondaryLine,
                color = KitsugiColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))

        // Teknik ses rozetleri (kodek, kHz, kanal, bit hızı)
        val techBadges = remember(state.streamSpecs, item.formatBadge) {
            state.streamSpecs.summaryBadges(item.formatBadge)
        }
        if (techBadges.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                techBadges.forEach { badge ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, accent.copy(alpha = 0.32f))
                    ) {
                        Text(
                            text = badge,
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Hata uyarısı (varsa)
        if (!state.errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KitsugiColors.AccentRed.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.ErrorOutline,
                        contentDescription = null,
                        tint = KitsugiColors.AccentRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = state.errorMessage,
                        color = KitsugiColors.TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // İlerleme çubuğu (Seekbar)
        val liveFraction = if (state.durationMs > 0L) {
            (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val displayFraction = if (isScrubbing) scrubFraction else liveFraction
        val displayPositionMs = if (isScrubbing && state.durationMs > 0L) {
            (scrubFraction * state.durationMs).toLong()
        } else {
            state.positionMs
        }

        Slider(
            value = displayFraction,
            onValueChange = { value ->
                isScrubbing = true
                scrubFraction = value.coerceIn(0f, 1f)
            },
            onValueChangeFinished = {
                if (state.durationMs > 0L) {
                    manager.seekTo((scrubFraction * state.durationMs).toLong())
                }
                isScrubbing = false
            },
            enabled = state.durationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = KitsugiColors.SurfaceElevated
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = AudioFormatSupport.formatDuration(displayPositionMs),
                color = KitsugiColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (state.durationMs > 0L) {
                    AudioFormatSupport.formatDuration(state.durationMs)
                } else {
                    "--:--"
                },
                color = KitsugiColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(10.dp))

        // Ana oynatma kontrolleri
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { manager.toggleShuffle() }, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Shuffle,
                    contentDescription = stringResource(R.string.audio_player_shuffle),
                    tint = if (state.shuffleEnabled) accent else KitsugiColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { manager.skipPrevious() }, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.SkipPrevious,
                    contentDescription = stringResource(R.string.audio_player_previous),
                    tint = KitsugiColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(onClick = { manager.seekBy(-10_000L) }, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = Icons.Replay,
                    contentDescription = stringResource(R.string.audio_player_rewind_10s),
                    tint = KitsugiColors.TextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }

            Surface(
                onClick = { manager.togglePlayPause() },
                shape = CircleShape,
                color = accent,
                shadowElevation = 8.dp,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Pause else Icons.PlayArrow,
                            contentDescription = if (state.isPlaying) {
                                stringResource(R.string.audio_player_pause)
                            } else {
                                stringResource(R.string.audio_player_play)
                            },
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            IconButton(onClick = { manager.seekBy(10_000L) }, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = Icons.Forward10,
                    contentDescription = stringResource(R.string.audio_player_forward_10s),
                    tint = KitsugiColors.TextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }

            IconButton(onClick = { manager.skipNext() }, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.SkipNext,
                    contentDescription = stringResource(R.string.audio_player_next),
                    tint = KitsugiColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(onClick = { manager.cycleRepeatMode() }, modifier = Modifier.size(40.dp)) {
                val repeatActive = state.repeatMode != AudioRepeatMode.OFF
                Icon(
                    imageVector = if (state.repeatMode == AudioRepeatMode.ONE) {
                        Icons.RepeatOne
                    } else {
                        Icons.Repeat
                    },
                    contentDescription = stringResource(R.string.audio_player_repeat),
                    tint = if (repeatActive) accent else KitsugiColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Gelişmiş ses araçları şeridi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val speedLabel = String.format(Locale.US, "%.2gx", state.playbackSpeed)
            AudioToolChip(
                icon = Icons.Speed,
                label = speedLabel,
                selected = activeTool == ActiveAudioToolPanel.SPEED_AND_PITCH || state.playbackSpeed != 1.0f || !state.preservePitch,
                accent = accent,
                onClick = {
                    activeTool = if (activeTool == ActiveAudioToolPanel.SPEED_AND_PITCH) {
                        ActiveAudioToolPanel.NONE
                    } else {
                        ActiveAudioToolPanel.SPEED_AND_PITCH
                    }
                }
            )

            val boostLabel = if (state.volumeBoostDb > 0.05f) {
                "+${state.volumeBoostDb.roundToInt()} dB"
            } else {
                stringResource(R.string.audio_player_boost)
            }
            AudioToolChip(
                icon = Icons.VolumeUp,
                label = boostLabel,
                selected = activeTool == ActiveAudioToolPanel.VOLUME_BOOST || state.volumeBoostDb > 0.05f,
                accent = accent,
                onClick = {
                    activeTool = if (activeTool == ActiveAudioToolPanel.VOLUME_BOOST) {
                        ActiveAudioToolPanel.NONE
                    } else {
                        ActiveAudioToolPanel.VOLUME_BOOST
                    }
                }
            )

            val sleepLabel = when {
                state.sleepStopAtTrackEnd -> stringResource(R.string.audio_player_sleep_track_end_short)
                state.sleepTimerRemainingMs != null -> AudioFormatSupport.formatDuration(state.sleepTimerRemainingMs)
                else -> stringResource(R.string.audio_player_sleep_timer)
            }
            AudioToolChip(
                icon = Icons.Timer,
                label = sleepLabel,
                selected = activeTool == ActiveAudioToolPanel.SLEEP_TIMER ||
                    state.sleepStopAtTrackEnd ||
                    state.sleepTimerRemainingMs != null,
                accent = accent,
                onClick = {
                    activeTool = if (activeTool == ActiveAudioToolPanel.SLEEP_TIMER) {
                        ActiveAudioToolPanel.NONE
                    } else {
                        ActiveAudioToolPanel.SLEEP_TIMER
                    }
                }
            )

            AudioToolChip(
                icon = Icons.PlaylistPlay,
                label = stringResource(R.string.audio_player_queue_count, state.queue.size),
                selected = activeTool == ActiveAudioToolPanel.QUEUE,
                accent = accent,
                onClick = {
                    activeTool = if (activeTool == ActiveAudioToolPanel.QUEUE) {
                        ActiveAudioToolPanel.NONE
                    } else {
                        ActiveAudioToolPanel.QUEUE
                    }
                }
            )

            AudioToolChip(
                icon = Icons.Equalizer,
                label = stringResource(R.string.audio_player_equalizer),
                selected = false,
                accent = accent,
                onClick = { launchSystemEqualizer(context, state.audioSessionId) }
            )

            AudioToolChip(
                icon = Icons.Share,
                label = stringResource(R.string.audio_player_share),
                selected = false,
                accent = accent,
                onClick = { shareAudioItem(context, item) }
            )

            AudioToolChip(
                icon = Icons.OpenInNew,
                label = stringResource(R.string.audio_player_open_external),
                selected = false,
                accent = accent,
                onClick = { openAudioInExternalApp(context, item) }
            )
        }

        // Açılır araç panelleri
        AnimatedVisibility(
            visible = activeTool != ActiveAudioToolPanel.NONE,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = KitsugiColors.SurfaceElevated,
                border = BorderStroke(1.dp, KitsugiColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                when (activeTool) {
                    ActiveAudioToolPanel.SPEED_AND_PITCH -> {
                        SpeedAndPitchPanel(
                            speed = state.playbackSpeed,
                            preservePitch = state.preservePitch,
                            accent = accent,
                            onSpeedChange = { manager.setPlaybackSpeed(it) },
                            onPreservePitchChange = { manager.setPreservePitch(it) }
                        )
                    }
                    ActiveAudioToolPanel.VOLUME_BOOST -> {
                        VolumeBoostPanel(
                            boostDb = state.volumeBoostDb,
                            accent = accent,
                            onBoostChange = { manager.setVolumeBoostDb(it) }
                        )
                    }
                    ActiveAudioToolPanel.SLEEP_TIMER -> {
                        SleepTimerPanel(
                            remainingMs = state.sleepTimerRemainingMs,
                            stopAtTrackEnd = state.sleepStopAtTrackEnd,
                            accent = accent,
                            onSelectMinutes = { manager.setSleepTimerMinutes(it) },
                            onSelectTrackEnd = { manager.setSleepStopAtTrackEnd(it) }
                        )
                    }
                    ActiveAudioToolPanel.QUEUE -> {
                        AudioQueuePanel(
                            queue = state.queue,
                            currentIndex = state.currentIndex,
                            isPlaying = state.isPlaying,
                            accent = accent,
                            onSelectIndex = { manager.jumpToQueueIndex(it) }
                        )
                    }
                    ActiveAudioToolPanel.NONE -> Unit
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SpeedAndPitchPanel(
    speed: Float,
    preservePitch: Boolean,
    accent: Color,
    onSpeedChange: (Float) -> Unit,
    onPreservePitchChange: (Boolean) -> Unit
) {
    val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    Column(modifier = Modifier.padding(14.dp)) {
        Text(
            text = stringResource(R.string.audio_player_speed_title),
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val active = kotlin.math.abs(speed - preset) < 0.03f
                Surface(
                    onClick = { onSpeedChange(preset) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) accent else KitsugiColors.SurfaceStrong,
                    border = BorderStroke(1.dp, if (active) accent else KitsugiColors.Border)
                ) {
                    Text(
                        text = "${preset}x",
                        color = if (active) Color.White else KitsugiColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = KitsugiColors.Border)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.audio_player_preserve_pitch),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.audio_player_preserve_pitch_desc),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = preservePitch,
                onCheckedChange = onPreservePitchChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent)
            )
        }
    }
}

@Composable
private fun VolumeBoostPanel(
    boostDb: Float,
    accent: Color,
    onBoostChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.audio_player_boost_title),
                color = KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (boostDb <= 0.05f) {
                    stringResource(R.string.audio_player_boost_normal)
                } else {
                    String.format(Locale.US, "+%.1f dB", boostDb)
                },
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.audio_player_boost_desc),
            color = KitsugiColors.TextMuted,
            fontSize = 11.sp
        )
        Slider(
            value = boostDb,
            onValueChange = onBoostChange,
            valueRange = 0f..12f,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = KitsugiColors.SurfaceStrong
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0f, 3f, 6f, 9f, 12f).forEach { preset ->
                val active = kotlin.math.abs(boostDb - preset) < 0.35f
                Surface(
                    onClick = { onBoostChange(preset) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) accent else KitsugiColors.SurfaceStrong,
                    border = BorderStroke(1.dp, if (active) accent else KitsugiColors.Border)
                ) {
                    Text(
                        text = if (preset == 0f) "0 dB" else "+${preset.toInt()} dB",
                        color = if (active) Color.White else KitsugiColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerPanel(
    remainingMs: Long?,
    stopAtTrackEnd: Boolean,
    accent: Color,
    onSelectMinutes: (Int?) -> Unit,
    onSelectTrackEnd: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text(
            text = stringResource(R.string.audio_player_sleep_timer),
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isOff = remainingMs == null && !stopAtTrackEnd
            SleepChip(
                label = stringResource(R.string.audio_player_sleep_off),
                selected = isOff,
                accent = accent,
                onClick = { onSelectMinutes(null) }
            )
            listOf(15, 30, 45, 60).forEach { mins ->
                SleepChip(
                    label = stringResource(R.string.audio_player_sleep_minutes, mins),
                    selected = false,
                    accent = accent,
                    onClick = { onSelectMinutes(mins) }
                )
            }
            SleepChip(
                label = stringResource(R.string.audio_player_sleep_track_end),
                selected = stopAtTrackEnd,
                accent = accent,
                onClick = { onSelectTrackEnd(!stopAtTrackEnd) }
            )
        }
    }
}

@Composable
private fun SleepChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent else KitsugiColors.SurfaceStrong,
        border = BorderStroke(1.dp, if (selected) accent else KitsugiColors.Border)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else KitsugiColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AudioQueuePanel(
    queue: List<AudioPlayerItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    accent: Color,
    onSelectIndex: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = stringResource(R.string.audio_player_queue_count, queue.size),
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(queue, key = { idx, item -> "${idx}_${item.id}" }) { index, entry ->
                val active = index == currentIndex
                Surface(
                    onClick = { onSelectIndex(index) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) accent.copy(alpha = 0.16f) else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (active) {
                            KitsugiLiveAudioEqualizerBars(
                                isPlaying = isPlaying,
                                color = accent,
                                barCount = 3,
                                maxHeight = 14.dp
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = KitsugiColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                color = if (active) accent else KitsugiColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = listOfNotNull(entry.formatBadge, entry.trackLabel)
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (entry.durationMs > 0L) {
                            Text(
                                text = AudioFormatSupport.formatDuration(entry.durationMs),
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioToolChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else KitsugiColors.SurfaceElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accent.copy(alpha = 0.55f) else KitsugiColors.Border
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else KitsugiColors.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = if (selected) accent else KitsugiColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AudioArtworkThumb(
    artworkBitmap: Bitmap?,
    artworkUrlOrPath: String?,
    isPlaying: Boolean,
    accent: Color,
    size: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            artworkBitmap != null && !artworkBitmap.isRecycled -> {
                Image(
                    bitmap = artworkBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            !artworkUrlOrPath.isNullOrBlank() -> {
                AsyncImage(
                    model = artworkUrlOrPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                if (isPlaying) {
                    KitsugiLiveAudioEqualizerBars(
                        isPlaying = true,
                        color = accent,
                        barCount = 3,
                        maxHeight = 16.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AudioLines,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun KitsugiLiveAudioEqualizerBars(
    isPlaying: Boolean,
    color: Color,
    barCount: Int = 4,
    maxHeight: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "audio_eq")
    val durations = listOf(380, 520, 440, 610, 470)
    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            val animatedFraction by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = durations[index % durations.size],
                        delayMillis = index * 70,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "eq_bar_$index"
            )
            val fraction = if (isPlaying) animatedFraction else 0.28f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

private fun launchSystemEqualizer(context: Context, audioSessionId: Int) {
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        if (audioSessionId > 0) {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        }
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.audio_player_equalizer_unavailable),
            Toast.LENGTH_SHORT
        ).show()
    }
}

internal fun shareAudioItem(context: Context, item: AudioPlayerItem) {
    val shareUri = resolveShareableAudioUri(context, item)
    if (shareUri == null) {
        Toast.makeText(
            context,
            context.getString(R.string.audio_player_share_failed),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    val mime = item.mimeType?.takeIf { it.isNotBlank() } ?: "audio/*"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, item.title))
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.audio_player_share_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}

internal fun openAudioInExternalApp(context: Context, item: AudioPlayerItem) {
    val shareUri = resolveShareableAudioUri(context, item)
    if (shareUri == null) {
        Toast.makeText(
            context,
            context.getString(R.string.audio_player_external_failed),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    val mime = item.mimeType?.takeIf { it.isNotBlank() } ?: "audio/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(shareUri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, item.title))
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.audio_player_external_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}

internal fun resolveShareableAudioUri(context: Context, item: AudioPlayerItem): Uri? {
    if (item.uriString.startsWith("content://")) {
        return Uri.parse(item.uriString)
    }
    val file = item.filePath?.let { File(it) }
        ?: item.uriString.takeIf { it.startsWith("/") }?.let { File(it) }
        ?: item.uriString.takeIf { it.startsWith("file://") }?.let { File(Uri.parse(it).path.orEmpty()) }
    if (file != null && file.exists()) {
        return runCatching {
            FileProvider.getUriForFile(context, "com.kitsugi.animelist.fileprovider", file)
        }.getOrNull()
    }
    return null
}
