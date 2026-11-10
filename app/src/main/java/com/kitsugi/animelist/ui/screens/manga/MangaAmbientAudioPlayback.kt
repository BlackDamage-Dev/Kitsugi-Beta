package com.kitsugi.animelist.ui.screens.manga

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.kitsugi.animelist.data.ambient.AmbientAudioFormats
import com.kitsugi.animelist.data.ambient.AmbientAudioTrack
import com.kitsugi.animelist.data.trailer.InAppYouTubeExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** A reader-scoped playlist player: playback continues while the sheet is closed and stops on exit. */
@OptIn(UnstableApi::class)
class MangaAmbientAudioPlayback internal constructor(context: Context) {
    /**
     * `kitsugi-yt://<videoId>` işaretçisini gerçek YouTube ses akışına çevirir.
     *
     * YouTube imzalı adresleri kısa ömürlüdür ve istemci UA'sı olmadan 403
     * dönebiliyor; bu yüzden adres **ve** başlıklar tam çalma anında, indirme
     * katmanında uygulanır. Çözüm başarısız olursa yalnızca o parça atlanır.
     */
    /**
     * Ortam sesi **yalnızca sestir**: video izi tamamen devre dışı.
     * Kaynak video içeren bir akış (ör. YouTube birleşik/HLS) olsa bile
     * dekoder video açmaz, pil ve veri harcanmaz.
     */
    private val audioOnlyTrackSelector = DefaultTrackSelector(context.applicationContext).apply {
        setParameters(
            buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        )
    }

    /** Sıradaki YouTube parçasını önceden çözer: parça geçişinde boşluk olmaz. */
    private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val youTubeStreamResolver = ResolvingDataSource.Resolver { dataSpec ->
        val uri = dataSpec.uri
        if (uri.scheme?.equals(YOUTUBE_STREAM_SCHEME, ignoreCase = true) != true) {
            dataSpec
        } else {
            val videoId = (uri.host ?: uri.path?.trimStart('/')).orEmpty()
            if (videoId.isBlank()) throw IOException("YouTube video kimliği okunamadı")
            val source = runBlocking {
                InAppYouTubeExtractor.shared.extractPlaybackSource(
                    "https://www.youtube.com/watch?v=$videoId"
                )
            } ?: throw IOException("YouTube akışı çözülemedi")
            val directUrl = source.audioUrl ?: source.videoUrl
            dataSpec
                .withUri(Uri.parse(directUrl))
                .withRequestHeaders(source.requestHeaders)
        }
    }

    internal val player: ExoPlayer = ExoPlayer.Builder(
        context.applicationContext,
        DefaultRenderersFactory(context.applicationContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
    ).setTrackSelector(audioOnlyTrackSelector)
     .setMediaSourceFactory(
        DefaultMediaSourceFactory(
            ResolvingDataSource.Factory(
                DefaultDataSource.Factory(context.applicationContext),
                youTubeStreamResolver,
            )
        )
    ).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        repeatMode = Player.REPEAT_MODE_OFF
        volume = DEFAULT_VOLUME
    }

    var currentTrack by mutableStateOf<AmbientAudioTrack?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var fadeJob: Job? = null
    private var internalVolume by mutableFloatStateOf(DEFAULT_VOLUME)
    val volume: Float get() = internalVolume
    var queuePosition by mutableIntStateOf(0)
        private set
    var queueLength by mutableIntStateOf(0)
        private set
    var hasNextTrack by mutableStateOf(false)
        private set
    var hasPreviousTrack by mutableStateOf(false)
        private set
    var queueFinished by mutableStateOf(false)
        private set
    var queuedTrackIds by mutableStateOf<Set<String>>(emptySet())
        private set

    private var queue: List<AmbientAudioTrack> = emptyList()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
            isPlaying = isPlayingNow
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentTrack = queue.firstOrNull { it.id == mediaItem?.mediaId }
            queueFinished = false
            updateNavigationState()
            prefetchNextYouTubeTrack()
            // Yumuşak geçiş: yeni parça sıfır sesle başlayıp kısa sürede
            // kullanıcı seviyesine çıkar (aniden patlama olmaz).
            runCatching { player.volume = 0f }
            fadeInVolume()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                isPlaying = false
                queueFinished = true
            }
            updateNavigationState()
        }

        override fun onPlayerError(playbackException: PlaybackException) {
            if (player.hasNextMediaItem()) {
                // A dead/stale stream should not pin the reader's queue on one failed catalog item.
                error = null
                player.seekToNextMediaItem()
                player.prepare()
                player.playWhenReady = true
            } else {
                isPlaying = false
                error = "Bu ses kaynağı şu anda oynatılamadı. Başka bir parça deneyebilirsin."
            }
        }
    }

    init {
        player.addListener(listener)
    }

    fun play(track: AmbientAudioTrack) = playQueue(listOf(track), allowOnline = true)

    /** Replace any current item/queue; a direct Play action is always a single-track playback. */
    fun playQueue(
        tracks: List<AmbientAudioTrack>,
        startIndex: Int = 0,
        localFileUris: Map<String, String> = emptyMap(),
        localFileTypes: Map<String, String> = emptyMap(),
        allowOnline: Boolean = true,
        /**
         * `true` ise liste bitince başa döner (okuma boyunca kesintisiz ortam
         * sesi). Tek parça çalarken daima `false`.
         */
        repeatQueue: Boolean = false,
    ) {
        val selectedTrackId = tracks.getOrNull(startIndex)?.id
        val playable = tracks.mapNotNull { track ->
            createMediaItem(
                track = track,
                localFileUri = localFileUris[track.id],
                localFileType = localFileTypes[track.id],
                allowOnline = allowOnline,
            )?.let { track to it }
        }
        if (playable.isEmpty()) {
            error = if (allowOnline) "Güvenli bir ses akışı bulunamadı." else "Çevrimdışı çalmak için önce bu parçayı indirmelisin."
            return
        }
        val selectedIndex = playable.indexOfFirst { it.first.id == selectedTrackId }
        if (selectedIndex < 0) {
            error = "Bu parça çevrimdışı kaydedilmemiş."
            return
        }
        val safeTracks = playable.map { it.first }
        val items = playable.map { it.second }

        error = null
        queueFinished = false
        queue = safeTracks
        queuedTrackIds = safeTracks.map { it.id }.toSet()
        queueLength = safeTracks.size
        currentTrack = safeTracks[selectedIndex]
        queuePosition = selectedIndex + 1
        player.repeatMode = if (repeatQueue && safeTracks.size > 1) {
            Player.REPEAT_MODE_ALL
        } else {
            Player.REPEAT_MODE_OFF
        }
        runCatching {
            player.setMediaItems(items, selectedIndex, 0L)
            player.prepare()
            player.playWhenReady = true
            updateNavigationState()
        }.onFailure {
            isPlaying = false
            error = "Ses bağlantısı açılamadı. Farklı bir parça deneyebilirsin."
        }
    }

    /** Append a track to the active queue without interrupting its current item. */
    fun addToQueue(
        track: AmbientAudioTrack,
        localFileUri: String? = null,
        localFileType: String? = null,
        allowOnline: Boolean = true,
    ): Boolean {
        if (track.id in queuedTrackIds) return false
        val item = createMediaItem(track, localFileUri, localFileType, allowOnline)
        if (item == null) {
            error = if (allowOnline) "Güvenli bir ses akışı bulunamadı." else "Çevrimdışı sıraya eklemek için önce parçayı indir."
            return false
        }

        if (queue.isEmpty() || player.mediaItemCount == 0) {
            playQueue(
                tracks = listOf(track),
                localFileUris = localFileUri?.let { mapOf(track.id to it) }.orEmpty(),
                localFileTypes = localFileType?.let { mapOf(track.id to it) }.orEmpty(),
                allowOnline = allowOnline,
            )
            return track.id in queuedTrackIds
        }

        val previousQueue = queue
        val updatedQueue = previousQueue + track
        val wasEnded = player.playbackState == Player.STATE_ENDED
        queue = updatedQueue
        queuedTrackIds = updatedQueue.map { it.id }.toSet()
        queueFinished = false
        return runCatching {
            player.addMediaItem(item)
            queueLength = updatedQueue.size
            updateNavigationState()
            error = null
            if (wasEnded) {
                player.seekTo(updatedQueue.lastIndex, 0L)
                player.prepare()
                player.playWhenReady = true
            }
            true
        }.getOrElse {
            queue = previousQueue
            queuedTrackIds = previousQueue.map { it.id }.toSet()
            queueLength = previousQueue.size
            error = "Parça sıraya eklenemedi. Tekrar deneyebilirsin."
            false
        }
    }

    private fun createMediaItem(
        track: AmbientAudioTrack,
        localFileUri: String?,
        localFileType: String?,
        allowOnline: Boolean,
    ): MediaItem? {
        val safeLocalUri = localFileUri?.takeIf {
            it.startsWith("content://", ignoreCase = true) || it.startsWith("file://", ignoreCase = true)
        }
        val uri = safeLocalUri ?: track.streamUrl.takeIf {
            allowOnline && (
                it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("$YOUTUBE_STREAM_SCHEME://", ignoreCase = true)
                )
        } ?: return null
        val fileType = if (safeLocalUri != null) localFileType ?: track.fileType else track.fileType
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMimeType(AmbientAudioFormats.mimeType(fileType))
            .build()
    }

    /** Kuyruk karıştırma: parça sırası rastgele, "sonraki" yine çalışır. */
    /**
     * Sesi [FADE_IN_MS] boyunca 0 → [internalVolume] arasında yükseltir.
     * Parça geçişlerinde "pat" sesini engeller.
     */
    private fun fadeInVolume() {
        fadeJob?.cancel()
        val target = internalVolume
        fadeJob = prefetchScope.launch {
            val steps = (FADE_IN_MS / FADE_STEP_MS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                delay(FADE_STEP_MS)
                val next = target * (step.toFloat() / steps)
                // ExoPlayer yalnızca oluşturulduğu iş parçacığından (main) erişilebilir.
                withContext(Dispatchers.Main.immediate) {
                    runCatching { player.volume = next.coerceIn(0f, 1f) }
                }
            }
        }
    }

    fun setShuffle(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
    }

    fun togglePlayback() {
        if (currentTrack == null) return
        error = null
        if (player.isPlaying || player.playWhenReady) player.pause() else player.play()
    }

    fun skipToNext() {
        if (!player.hasNextMediaItem()) return
        error = null
        player.seekToNextMediaItem()
        player.playWhenReady = true
    }

    fun skipToPrevious() {
        if (!player.hasPreviousMediaItem()) return
        error = null
        player.seekToPreviousMediaItem()
        player.playWhenReady = true
    }

    fun setVolume(value: Float) {
        fadeJob?.cancel()
        internalVolume = value.coerceIn(0f, 1f)
        player.volume = internalVolume
    }

    fun stop() {
        runCatching {
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
        queue = emptyList()
        queuedTrackIds = emptySet()
        currentTrack = null
        queuePosition = 0
        queueLength = 0
        isPlaying = false
        hasNextTrack = false
        hasPreviousTrack = false
        queueFinished = false
        error = null
    }

    internal fun release() {
        runCatching { fadeJob?.cancel() }
        runCatching { prefetchScope.cancel() }
        runCatching { player.removeListener(listener) }
        runCatching { player.release() }
    }

    /**
     * Sıradaki parça bir YouTube işaretçisiyse ses adresini şimdiden çöz.
     * `InAppYouTubeExtractor` sonucu videoId başına önbelleğe aldığı için
     * parça başladığında çözüm anında hazır olur.
     */
    private fun prefetchNextYouTubeTrack() {
        val nextIndex = player.currentMediaItemIndex + 1
        val next = queue.getOrNull(nextIndex) ?: return
        val url = next.streamUrl
        if (!url.startsWith("kitsugi-yt://", ignoreCase = true)) return
        val videoId = url.substringAfter("kitsugi-yt://").substringBefore('/')
        if (videoId.isBlank()) return
        prefetchScope.launch {
            runCatching {
                InAppYouTubeExtractor.shared.extractPlaybackSource(
                    "https://www.youtube.com/watch?v=$videoId"
                )
            }
        }
    }

    private fun updateNavigationState() {
        hasNextTrack = player.hasNextMediaItem()
        hasPreviousTrack = player.hasPreviousMediaItem()
        queuePosition = if (queue.isEmpty() || player.currentMediaItemIndex < 0) {
            0
        } else {
            player.currentMediaItemIndex + 1
        }
    }

    private companion object {
        const val DEFAULT_VOLUME = 0.35f
        /** Parça başında yumuşak ses yükselme süresi. */
        const val FADE_IN_MS = 700L
        const val FADE_STEP_MS = 50L
        /** YouTube akış işaretçisi: `kitsugi-yt://<videoId>`. */
        const val YOUTUBE_STREAM_SCHEME = "kitsugi-yt"
    }
}

@Composable
fun rememberMangaAmbientAudioPlayback(): MangaAmbientAudioPlayback {
    val context = LocalContext.current.applicationContext
    val playback = remember(context) { MangaAmbientAudioPlayback(context) }
    DisposableEffect(playback) {
        onDispose { playback.release() }
    }
    return playback
}
