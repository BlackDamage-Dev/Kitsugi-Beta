package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.ambient.AmbientAudioContext
import com.kitsugi.animelist.data.ambient.AmbientAudioDownloadManager
import com.kitsugi.animelist.data.ambient.AmbientAudioDownloadState
import com.kitsugi.animelist.data.ambient.AmbientAudioDownloadStatus
import com.kitsugi.animelist.data.ambient.AmbientAudioMode
import com.kitsugi.animelist.data.ambient.AmbientAudioPlanner
import com.kitsugi.animelist.data.ambient.AmbientAudioRepository
import com.kitsugi.animelist.data.ambient.AmbientAudioSource
import com.kitsugi.animelist.data.ambient.AmbientAudioTrack
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import kotlinx.coroutines.launch

@Composable
fun MangaAmbientAudioSheet(
    manga: MangaDetails,
    chapter: MangaChapter,
    playback: MangaAmbientAudioPlayback,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { AmbientAudioRepository(context) }
    val playbackPreferences = remember(context) {
        context.getSharedPreferences("ambient_audio_settings", android.content.Context.MODE_PRIVATE)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var mode by remember(manga.url) { mutableStateOf(AmbientAudioMode.AUTO) }
    var query by remember(manga.url, chapter.url) { mutableStateOf(manga.title.take(120)) }
    var manualQuery by remember(manga.url, chapter.url) { mutableStateOf(false) }
    var onlinePlaybackEnabled by remember(manga.url) {
        mutableStateOf(playbackPreferences.getBoolean("online_playback_enabled", true))
    }
    var enabledSources by remember(manga.url) {
        mutableStateOf(AmbientAudioSource.entries.toSet())
    }
    var tracks by remember { mutableStateOf(emptyList<AmbientAudioTrack>()) }
    var warnings by remember { mutableStateOf(emptyList<String>()) }
    var isSearching by remember { mutableStateOf(false) }
    var usedCache by remember { mutableStateOf(false) }
    var titleMatchCount by remember { mutableIntStateOf(0) }
    var contextMatchCount by remember { mutableIntStateOf(0) }
    val downloadStates by AmbientAudioDownloadManager.downloads.collectAsState()

    val audioContext = remember(manga.url, manga.title, manga.source, chapter.url, manga.description, manga.genre) {
        AmbientAudioContext(
            title = manga.title,
            chapterTitle = chapter.name,
            description = manga.description.orEmpty(),
            genres = manga.genre,
        )
    }
    val plan = remember(audioContext, mode) { AmbientAudioPlanner.createPlan(audioContext, mode) }

    LaunchedEffect(tracks.map { it.id }) {
        tracks.forEach { AmbientAudioDownloadManager.restoreSaved(context, it) }
    }

    LaunchedEffect(plan.titleQuery, manga.url, chapter.url) {
        if (!manualQuery || query.isBlank()) query = plan.titleQuery.take(120)
    }

    fun playTracks(selectedTracks: List<AmbientAudioTrack>, startIndex: Int = 0) {
        val localStates = selectedTracks.mapNotNull { candidate ->
            downloadStates[candidate.id]
                ?.takeIf { it.status == AmbientAudioDownloadStatus.COMPLETED && !it.contentUri.isNullOrBlank() }
                ?.let { candidate.id to it }
        }.toMap()
        val localUris = localStates.mapValues { (_, state) -> state.contentUri.orEmpty() }
        val localTypes = selectedTracks.mapNotNull { candidate ->
            localStates[candidate.id]?.let { state ->
                candidate.id to if (state.converted) "m4a" else candidate.fileType
            }
        }.toMap()
        playback.playQueue(
            tracks = selectedTracks,
            startIndex = startIndex,
            localFileUris = localUris,
            localFileTypes = localTypes,
            allowOnline = onlinePlaybackEnabled,
        )
    }

    fun playTrack(track: AmbientAudioTrack) {
        // A direct Play always replaces the previous single track or queue.
        playTracks(listOf(track))
    }

    fun addTrackToQueue(track: AmbientAudioTrack) {
        val saved = downloadStates[track.id]?.takeIf {
            it.status == AmbientAudioDownloadStatus.COMPLETED && !it.contentUri.isNullOrBlank()
        }
        playback.addToQueue(
            track = track,
            localFileUri = saved?.contentUri,
            localFileType = saved?.let { if (it.converted) "m4a" else track.fileType },
            allowOnline = onlinePlaybackEnabled,
        )
    }

    LaunchedEffect(playback.isPlaying, playback.currentTrack?.id) {
        if (playback.isPlaying) playback.currentTrack?.let(repository::recordPlayed)
    }

    fun search(searchQuery: String, autoPlayBest: Boolean, titleFirst: Boolean = false) {
        val trimmedQuery = searchQuery.trim()
        if (isSearching || enabledSources.isEmpty() || (!titleFirst && trimmedQuery.isBlank())) return
        isSearching = true
        warnings = emptyList()
        tracks = emptyList()
        usedCache = false
        titleMatchCount = 0
        contextMatchCount = 0
        scope.launch {
            try {
                val currentPlan = AmbientAudioPlanner.createPlan(audioContext, mode)
                val result = if (titleFirst) {
                    repository.searchForManga(enabledSources, currentPlan)
                } else {
                    repository.search(trimmedQuery, enabledSources, currentPlan)
                }
                tracks = result.tracks
                warnings = result.warnings
                usedCache = result.fromCache
                titleMatchCount = result.titleMatchCount
                contextMatchCount = result.contextMatchCount
                if (autoPlayBest) {
                    val best = result.tracks.firstOrNull()
                    if (best != null && onlinePlaybackEnabled) playTracks(listOf(best))
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                warnings = listOf("Müzik araması tamamlanamadı. Bağlantını kontrol edip tekrar deneyebilirsin.")
            } finally {
                isSearching = false
            }
        }
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        heightFraction = 0.92f,
        innerScrollState = listState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 760.dp)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color(0xFF9BE7D2), modifier = Modifier.size(26.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(stringResource(R.string.ambient_audio_title), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = manga.title,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Text("×", color = Color.White, fontSize = 28.sp)
                }
            }

            ActiveTrackCard(
                playback = playback,
                onTogglePlayback = { playback.togglePlayback() },
                onSkipPrevious = { playback.skipToPrevious() },
                onSkipNext = { playback.skipToNext() },
                onStop = { playback.stop() },
                onVolumeChange = { playback.setVolume(it) },
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171A26)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Color(0xFF9BE7D2), modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("${stringResource(R.string.ambient_audio_match)}: ${plan.moodLabel}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(plan.reason, color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AmbientAudioMode.entries.forEach { item ->
                    val label = when (item) {
                        AmbientAudioMode.AUTO -> stringResource(R.string.ambient_audio_auto)
                        AmbientAudioMode.MUSIC -> stringResource(R.string.ambient_audio_music)
                        AmbientAudioMode.AMBIENCE -> stringResource(R.string.ambient_audio_ambience)
                    }
                    FilterChip(
                        selected = mode == item,
                        onClick = {
                            mode = item
                            manualQuery = false
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF24413F),
                            selectedLabelColor = Color(0xFF9BE7D2),
                            containerColor = Color(0xFF171A26),
                            labelColor = Color.White.copy(alpha = 0.82f),
                        ),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it.take(120)
                        manualQuery = true
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.ambient_audio_search_hint)) },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF42D3A7),
                        unfocusedBorderColor = Color(0xFF3A3D4C),
                        focusedLabelColor = Color(0xFF9BE7D2),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.58f),
                        cursorColor = Color(0xFF9BE7D2),
                    ),
                )
                IconButton(
                    onClick = { search(query, autoPlayBest = false) },
                    enabled = !isSearching && query.isNotBlank() && enabledSources.isNotEmpty(),
                    modifier = Modifier.size(48.dp),
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(color = Color(0xFF9BE7D2), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.ambient_audio_search), tint = Color(0xFF9BE7D2))
                    }
                }
            }

            Button(
                onClick = {
                    manualQuery = false
                    query = plan.titleQuery.take(120)
                    search(plan.titleQuery, autoPlayBest = onlinePlaybackEnabled, titleFirst = true)
                },
                enabled = !isSearching && enabledSources.isNotEmpty() && plan.titleQuery.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C7365),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF2A2C37),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isSearching) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    stringResource(
                        if (onlinePlaybackEnabled) R.string.ambient_audio_auto_play
                        else R.string.ambient_audio_auto_search,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ambient_audio_online_playback),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = onlinePlaybackEnabled,
                    onClick = {
                        val enabled = !onlinePlaybackEnabled
                        onlinePlaybackEnabled = enabled
                        playbackPreferences.edit().putBoolean("online_playback_enabled", enabled).apply()
                        if (!enabled) playback.stop()
                    },
                    label = {
                        Text(
                            stringResource(
                                if (onlinePlaybackEnabled) R.string.ambient_audio_online_on
                                else R.string.ambient_audio_online_off,
                            ),
                            fontSize = 10.sp,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF24413F),
                        selectedLabelColor = Color(0xFF9BE7D2),
                        containerColor = Color(0xFF171A26),
                        labelColor = Color.White.copy(alpha = 0.82f),
                    ),
                )
            }

            Text(
                stringResource(R.string.ambient_audio_sources),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AmbientAudioSource.entries.forEach { source ->
                    val selected = source in enabledSources
                    FilterChip(
                        selected = selected,
                        onClick = {
                            enabledSources = if (selected) enabledSources - source else enabledSources + source
                        },
                        label = {
                            Text(
                                sourceName(source),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF24413F),
                            selectedLabelColor = Color(0xFF9BE7D2),
                            containerColor = Color(0xFF171A26),
                            labelColor = Color.White.copy(alpha = 0.82f),
                        ),
                    )
                }
            }

            Text(
                text = stringResource(R.string.ambient_audio_sources_notice),
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        isSearching -> stringResource(R.string.ambient_audio_results)
                        titleMatchCount + contextMatchCount > 0 -> stringResource(
                            R.string.ambient_audio_queue_counts,
                            titleMatchCount,
                            contextMatchCount,
                        )
                        usedCache -> stringResource(R.string.ambient_audio_cached)
                        else -> stringResource(R.string.ambient_audio_results)
                    },
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (tracks.isNotEmpty() && !isSearching && (onlinePlaybackEnabled || tracks.any {
                        downloadStates[it.id]?.status == AmbientAudioDownloadStatus.COMPLETED
                    })
                ) {
                    TextButton(
                        onClick = {
                            val playableIndices = tracks.indices.filter { index ->
                                onlinePlaybackEnabled || downloadStates[tracks[index].id]?.status == AmbientAudioDownloadStatus.COMPLETED
                            }
                            val currentIndex = tracks.indexOfFirst { it.id == playback.currentTrack?.id }
                            val nextIndex = playableIndices.firstOrNull { it > currentIndex }
                                ?: playableIndices.firstOrNull()
                            if (nextIndex != null) playTrack(tracks[nextIndex])
                        },
                    ) {
                        Text(stringResource(R.string.ambient_audio_another), color = Color(0xFF9BE7D2), fontSize = 11.sp)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (warnings.isNotEmpty()) {
                    items(warnings) { warning ->
                        Text(warning, color = Color(0xFFFFCC80), fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
                if (tracks.isEmpty() && !isSearching && warnings.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.ambient_audio_empty_hint),
                            color = Color.White.copy(alpha = 0.52f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
                items(tracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        isSelected = playback.currentTrack?.id == track.id,
                        isPlayable = onlinePlaybackEnabled || downloadStates[track.id]?.status == AmbientAudioDownloadStatus.COMPLETED,
                        isQueued = track.id in playback.queuedTrackIds,
                        onPlay = { playTrack(track) },
                        onAddToQueue = { addTrackToQueue(track) },
                        onOpenSource = { url -> runCatching { uriHandler.openUri(url) } },
                        downloadState = downloadStates[track.id],
                        onDownload = { AmbientAudioDownloadManager.start(context, track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveTrackCard(
    playback: MangaAmbientAudioPlayback,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onStop: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    val active = playback.currentTrack
    if (active == null) {
        playback.error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 10.sp) }
        return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202B31)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color(0xFF9BE7D2), modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(active.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${active.creator} · ${active.sourceLabel}", color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (playback.queueLength > 1) {
                        Text("${playback.queuePosition}/${playback.queueLength}", color = Color.White.copy(alpha = 0.48f), fontSize = 9.sp)
                    }
                }
                IconButton(onClick = onSkipPrevious, enabled = playback.hasPreviousTrack) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.ambient_audio_skip_previous), tint = Color.White.copy(alpha = 0.72f))
                }
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) stringResource(R.string.ambient_audio_pause) else stringResource(R.string.ambient_audio_play),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onSkipNext, enabled = playback.hasNextTrack) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.ambient_audio_skip_next), tint = Color.White.copy(alpha = 0.72f))
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.ambient_audio_stop), tint = Color.White.copy(alpha = 0.72f))
                }
            }
            Slider(
                value = playback.volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.height(24.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF42D3A7),
                    inactiveTrackColor = Color(0xFF40514F),
                    thumbColor = Color(0xFF9BE7D2),
                ),
            )
            playback.error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 10.sp) }
        }
    }
}

@Composable
private fun sourceName(source: AmbientAudioSource): String = when (source) {
    AmbientAudioSource.FREESOUND -> stringResource(R.string.ambient_audio_source_freesound)
    AmbientAudioSource.WIKIMEDIA_AUDIO -> stringResource(R.string.ambient_audio_source_wikimedia)
    AmbientAudioSource.INTERNET_ARCHIVE -> stringResource(R.string.ambient_audio_source_archive)
    AmbientAudioSource.AUDIUS -> stringResource(R.string.ambient_audio_source_audius)
    AmbientAudioSource.WIKIMEDIA_COMMONS -> stringResource(R.string.ambient_audio_source_commons)
    AmbientAudioSource.CCMIXTER -> stringResource(R.string.ambient_audio_source_ccmixter)
    AmbientAudioSource.FREE_TO_USE -> stringResource(R.string.ambient_audio_source_freetouse)
    AmbientAudioSource.RADIO_BROWSER -> stringResource(R.string.ambient_audio_source_radiobrowser)
}

@Composable
private fun TrackCard(
    track: AmbientAudioTrack,
    isSelected: Boolean,
    isPlayable: Boolean,
    isQueued: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenSource: (String) -> Unit,
    downloadState: AmbientAudioDownloadState?,
    onDownload: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF263D3A) else Color(0xFF171A26),
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 8.dp),
        ) {
            Text(
                track.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val duration = if (track.provider.equals("radio_browser", ignoreCase = true)) {
                stringResource(R.string.ambient_audio_live_radio)
            } else {
                track.durationMs?.let(::formatDuration) ?: stringResource(R.string.ambient_audio_duration_unknown)
            }
            Text(
                text = "${track.creator} · ${track.sourceLabel} · $duration",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (track.landingUrl.startsWith("https://")) {
                    TextButton(onClick = { onOpenSource(track.landingUrl) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ambient_audio_open_source), fontSize = 10.sp, color = Color(0xFF9BE7D2))
                    }
                }
                Spacer(Modifier.weight(1f))

                val canDownload = track.downloadUrl?.startsWith("https://", ignoreCase = true) == true &&
                    !track.provider.equals("radio_browser", ignoreCase = true)
                if (canDownload) {
                    val downloadLabel = when (downloadState?.status) {
                        AmbientAudioDownloadStatus.QUEUED,
                        AmbientAudioDownloadStatus.DOWNLOADING -> stringResource(
                            R.string.ambient_audio_downloading,
                            ((downloadState?.progress ?: 0f) * 100).toInt(),
                        )
                        AmbientAudioDownloadStatus.CONVERTING -> stringResource(R.string.ambient_audio_converting)
                        AmbientAudioDownloadStatus.SAVING -> stringResource(R.string.ambient_audio_saving)
                        AmbientAudioDownloadStatus.COMPLETED -> stringResource(
                            when {
                                downloadState?.converted == true -> R.string.ambient_audio_downloaded_converted
                                !downloadState?.message.isNullOrBlank() -> R.string.ambient_audio_downloaded_original
                                else -> R.string.ambient_audio_downloaded
                            },
                        )
                        AmbientAudioDownloadStatus.FAILED -> stringResource(R.string.ambient_audio_download_retry)
                        null -> stringResource(R.string.ambient_audio_download)
                    }
                    val isDownloading = downloadState?.status?.let {
                        it in setOf(
                            AmbientAudioDownloadStatus.QUEUED,
                            AmbientAudioDownloadStatus.DOWNLOADING,
                            AmbientAudioDownloadStatus.CONVERTING,
                            AmbientAudioDownloadStatus.SAVING,
                        )
                    } == true
                    val isDownloadFinished = downloadState?.status == AmbientAudioDownloadStatus.COMPLETED
                    IconButton(
                        onClick = onDownload,
                        enabled = downloadState?.status == null || downloadState?.status == AmbientAudioDownloadStatus.FAILED,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDownloadFinished) Color(0xFF24413F) else Color(0xFF202B31))
                            .semantics { contentDescription = downloadLabel },
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                color = Color(0xFF9BE7D2),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(21.dp),
                            )
                        } else {
                            Icon(
                                imageVector = when {
                                    isDownloadFinished -> Icons.Rounded.DownloadDone
                                    downloadState?.status == AmbientAudioDownloadStatus.FAILED -> Icons.Rounded.Refresh
                                    else -> Icons.Rounded.CloudDownload
                                },
                                contentDescription = downloadLabel,
                                tint = if (isDownloadFinished) Color(0xFF9BE7D2) else Color.White,
                                modifier = Modifier.size(23.dp),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onAddToQueue,
                    enabled = isPlayable && !isQueued,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isQueued) Color(0xFF24413F) else Color(0xFF202B31)),
                ) {
                    Icon(
                        imageVector = if (isQueued) Icons.Rounded.PlaylistAddCheck else Icons.Rounded.PlaylistAdd,
                        contentDescription = stringResource(
                            if (isQueued) R.string.ambient_audio_in_queue else R.string.ambient_audio_add_to_queue,
                        ),
                        tint = if (isPlayable) Color(0xFF9BE7D2) else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(23.dp),
                    )
                }

                IconButton(
                    onClick = onPlay,
                    enabled = isPlayable,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) Color(0xFF24413F) else Color(0xFF202B31)),
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.ambient_audio_play),
                        tint = if (isPlayable) Color(0xFF9BE7D2) else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return "%d:%02d".format(minutes, remainingSeconds)
}
