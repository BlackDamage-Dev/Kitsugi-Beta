package com.kitsugi.animelist.core.player.audio.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.amr.AmrExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.kitsugi.animelist.core.download.DownloadExportNaming
import com.kitsugi.animelist.core.player.GainAudioProcessor
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfo
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Kitsugi'nin çift motorlu, biçim-bağımsız ("ses formatı farketmeksizin") dahili ses yöneticisi.
 *
 * 1. Birincil Motor (`Media3 + FFmpeg + IAMF + MPEG-H`):
 *    - `EXTENSION_RENDERER_MODE_PREFER` + `enableDecoderFallback(true)` ile FFmpeg ses çözücüsü,
 *    - Ham ADTS `.aac`, VBR/CBR `.mp3` ve `.amr` dosyalarında tam süre ve sarma için CBR/Index seeking,
 *    - `GainAudioProcessor` ile +12 dB'ye kadar ses güçlendirme (Volume Boost),
 *    - Hız (`0.5x`–`2.5x`) ve Ton Koruma (Pitch) desteği.
 * 2. İkincil Otomatik Yedek Motor (`KitsugiMpvAudioHost` — `libmpv` / `libavformat` + `libavcodec`):
 *    - `.wma`, `.ape`, `.wv`, `.mpc`, `.tta`, `.tak`, ham `.dts`/`.thd`, `.caf`, `.aiff`, `.dsf`/`.dff`
 *      gibi konteynerlerde doğrudan, diğer tüm biçimlerde ise Media3 herhangi bir ayrıştırma/çözme
 *      hatası döndürdüğü anda **otomatik ve kesintisiz** devreye girer.
 */
class KitsugiAudioPlayerManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private val _artworkBitmap = MutableStateFlow<Bitmap?>(null)
    val artworkBitmap: StateFlow<Bitmap?> = _artworkBitmap.asStateFlow()

    private val gainProcessor = GainAudioProcessor()
    private var exoPlayer: ExoPlayer? = null
    private var mpvHost: KitsugiMpvAudioHost? = null

    private var progressTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var probeJob: Job? = null
    private var sleepDeadlineElapsedMs: Long = 0L

    private val exoListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_state.value.backendKind != AudioBackendKind.MEDIA3_FFMPEG) return
            val player = exoPlayer ?: return
            _state.update {
                it.copy(
                    isPlaying = isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.takeIf { d -> d > 0L } ?: it.durationMs,
                    audioSessionId = player.audioSessionId
                )
            }
            updateTickerState()
            syncForegroundService()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_state.value.backendKind != AudioBackendKind.MEDIA3_FFMPEG) return
            val player = exoPlayer ?: return
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.update { it.copy(isBuffering = true) }
                }
                Player.STATE_READY -> {
                    val dur = player.duration.takeIf { it > 0L } ?: _state.value.durationMs
                    _state.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = dur,
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            audioSessionId = player.audioSessionId,
                            errorMessage = null
                        )
                    }
                    syncForegroundService()
                }
                Player.STATE_ENDED -> {
                    _state.update { it.copy(isBuffering = false, isPlaying = false) }
                    onActiveTrackEnded()
                }
                Player.STATE_IDLE -> {
                    _state.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (_state.value.backendKind != AudioBackendKind.MEDIA3_FFMPEG) return
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSelected(i)) continue
                    val format = group.getTrackFormat(i)
                    val codec = AudioFormatSupport.normalizeCodecLabel(
                        format.codecs ?: format.sampleMimeType
                    )
                    val sampleRate = format.sampleRate.takeIf { it > 0 } ?: 0
                    val channels = format.channelCount.takeIf { it > 0 } ?: 0
                    val bitrateKbps = format.bitrate.takeIf { it > 0 }?.let { it / 1000 } ?: 0
                    val container = format.containerMimeType
                        ?.substringAfter('/', "")
                        ?.takeIf { it.isNotBlank() }
                    _state.update { st ->
                        st.copy(
                            streamSpecs = st.streamSpecs.copy(
                                codec = codec ?: st.streamSpecs.codec,
                                sampleRateHz = if (sampleRate > 0) sampleRate else st.streamSpecs.sampleRateHz,
                                channelCount = if (channels > 0) channels else st.streamSpecs.channelCount,
                                bitrateKbps = if (bitrateKbps > 0) bitrateKbps else st.streamSpecs.bitrateKbps,
                                container = container ?: st.streamSpecs.container
                            )
                        )
                    }
                    return
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (_state.value.backendKind != AudioBackendKind.MEDIA3_FFMPEG) return
            val item = _state.value.currentItem ?: return
            val resumePos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _state.value.positionMs
            Log.w(
                TAG,
                "Media3 ses hatası (${error.errorCodeName}: ${error.message}) -> libmpv yedeğine geçiliyor: ${item.title}"
            )
            runCatching { exoPlayer?.stop() }
            startOnMpvBackend(item, startPositionMs = resumePos, autoPlay = true)
        }
    }

    private val mpvListener = object : KitsugiMpvAudioHost.Listener {
        override fun onPlayingChanged(playing: Boolean) {
            if (_state.value.backendKind != AudioBackendKind.MPV_FFMPEG) return
            _state.update { it.copy(isPlaying = playing) }
            updateTickerState()
            syncForegroundService()
        }

        override fun onBufferingChanged(buffering: Boolean) {
            if (_state.value.backendKind != AudioBackendKind.MPV_FFMPEG) return
            _state.update { it.copy(isBuffering = buffering) }
        }

        override fun onProgressChanged(positionMs: Long, durationMs: Long) {
            if (_state.value.backendKind != AudioBackendKind.MPV_FFMPEG) return
            _state.update {
                it.copy(
                    positionMs = positionMs,
                    durationMs = if (durationMs > 0L) durationMs else it.durationMs
                )
            }
        }

        override fun onStreamSpecsChanged(specs: AudioStreamSpecs) {
            if (_state.value.backendKind != AudioBackendKind.MPV_FFMPEG) return
            _state.update { st ->
                st.copy(
                    streamSpecs = AudioStreamSpecs(
                        codec = specs.codec ?: st.streamSpecs.codec,
                        sampleRateHz = if (specs.sampleRateHz > 0) specs.sampleRateHz else st.streamSpecs.sampleRateHz,
                        channelCount = if (specs.channelCount > 0) specs.channelCount else st.streamSpecs.channelCount,
                        bitrateKbps = if (specs.bitrateKbps > 0) specs.bitrateKbps else st.streamSpecs.bitrateKbps,
                        container = specs.container ?: st.streamSpecs.container
                    )
                )
            }
        }

        override fun onEnded() {
            if (_state.value.backendKind != AudioBackendKind.MPV_FFMPEG) return
            _state.update { it.copy(isPlaying = false, isBuffering = false) }
            onActiveTrackEnded()
        }

        override fun onError(message: String) {
            if (_state.value.backendKind != AudioBackendKind.MPV_FFMPEG) return
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = message
                )
            }
            syncForegroundService()
        }
    }

    fun playSingle(item: AudioPlayerItem, openFullSheet: Boolean = false) {
        playQueue(listOf(item), startIndex = 0, openFullSheet = openFullSheet)
    }

    fun playQueue(
        items: List<AudioPlayerItem>,
        startIndex: Int = 0,
        openFullSheet: Boolean = false
    ) {
        if (items.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        val shuffleOrder = if (_state.value.shuffleEnabled) {
            buildShuffleOrder(items.size, safeIndex)
        } else {
            emptyList()
        }
        _state.update {
            it.copy(
                queue = items,
                currentIndex = safeIndex,
                shuffleOrder = shuffleOrder,
                isExpanded = openFullSheet || it.isExpanded,
                errorMessage = null
            )
        }
        loadCurrentItem(startPositionMs = 0L, autoPlay = true)
    }

    /**
     * Harici dosya yöneticilerinden (`ACTION_VIEW`) gelen `content://` veya `file://` ses
     * bağlantılarını üst verisini çözümleyerek oynatır.
     */
    fun playExternalUri(uri: Uri, titleHint: String? = null, openFullSheet: Boolean = true) {
        scope.launch {
            val item = withContext(Dispatchers.IO) {
                buildItemFromUri(uri, titleHint)
            }
            playSingle(item, openFullSheet = openFullSheet)
        }
    }

    fun jumpToQueueIndex(index: Int) {
        val q = _state.value.queue
        if (index !in q.indices) return
        if (index == _state.value.currentIndex) {
            if (!_state.value.isPlaying) play()
            return
        }
        _state.update { it.copy(currentIndex = index, errorMessage = null) }
        loadCurrentItem(startPositionMs = 0L, autoPlay = true)
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun play() {
        val current = _state.value
        if (current.currentItem == null) return
        when (current.backendKind) {
            AudioBackendKind.MEDIA3_FFMPEG -> {
                val player = ensureExoPlayer()
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0L)
                } else if (player.playbackState == Player.STATE_IDLE) {
                    loadCurrentItem(startPositionMs = current.positionMs, autoPlay = true)
                    return
                }
                player.playWhenReady = true
                player.play()
            }
            AudioBackendKind.MPV_FFMPEG -> {
                ensureMpvHost().play()
            }
        }
        syncForegroundService(forcePlaying = true)
    }

    fun pause() {
        when (_state.value.backendKind) {
            AudioBackendKind.MEDIA3_FFMPEG -> exoPlayer?.pause()
            AudioBackendKind.MPV_FFMPEG -> mpvHost?.pause()
        }
        _state.update { it.copy(isPlaying = false) }
        updateTickerState()
        syncForegroundService()
    }

    fun seekTo(positionMs: Long) {
        val dur = _state.value.durationMs
        val bounded = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        when (_state.value.backendKind) {
            AudioBackendKind.MEDIA3_FFMPEG -> exoPlayer?.seekTo(bounded)
            AudioBackendKind.MPV_FFMPEG -> mpvHost?.seekTo(bounded)
        }
        _state.update { it.copy(positionMs = bounded) }
        syncForegroundService()
    }

    fun seekBy(deltaMs: Long) {
        seekTo(_state.value.positionMs + deltaMs)
    }

    fun skipNext() {
        val st = _state.value
        val nextIdx = AudioFormatSupport.resolveNextIndex(
            currentIndex = st.currentIndex,
            queueSize = st.queue.size,
            repeatMode = st.repeatMode,
            shuffleOrder = st.shuffleOrder.takeIf { st.shuffleEnabled },
            autoAdvance = false
        )
        if (nextIdx != null) {
            _state.update { it.copy(currentIndex = nextIdx, errorMessage = null) }
            loadCurrentItem(startPositionMs = 0L, autoPlay = true)
        } else {
            seekTo(0L)
        }
    }

    fun skipPrevious() {
        val st = _state.value
        if (st.positionMs > 3_500L) {
            seekTo(0L)
            return
        }
        val prevIdx = AudioFormatSupport.resolvePrevIndex(
            currentIndex = st.currentIndex,
            queueSize = st.queue.size,
            repeatMode = st.repeatMode,
            shuffleOrder = st.shuffleOrder.takeIf { st.shuffleEnabled }
        )
        if (prevIdx != null && prevIdx != st.currentIndex) {
            _state.update { it.copy(currentIndex = prevIdx, errorMessage = null) }
            loadCurrentItem(startPositionMs = 0L, autoPlay = true)
        } else {
            seekTo(0L)
        }
    }

    fun toggleShuffle() {
        _state.update { st ->
            val nextShuffle = !st.shuffleEnabled
            val order = if (nextShuffle && st.queue.isNotEmpty()) {
                buildShuffleOrder(st.queue.size, st.currentIndex.coerceAtLeast(0))
            } else {
                emptyList()
            }
            st.copy(shuffleEnabled = nextShuffle, shuffleOrder = order)
        }
    }

    fun cycleRepeatMode() {
        _state.update { it.copy(repeatMode = it.repeatMode.next()) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(0.5f, 2.5f)
        val keepPitch = _state.value.preservePitch
        _state.update { it.copy(playbackSpeed = safe) }
        applySpeedAndPitchToBackends(safe, keepPitch)
        syncForegroundService()
    }

    fun setPreservePitch(preserve: Boolean) {
        val speed = _state.value.playbackSpeed
        _state.update { it.copy(preservePitch = preserve) }
        applySpeedAndPitchToBackends(speed, preserve)
    }

    fun setVolumeBoostDb(db: Float) {
        val safe = db.coerceIn(0f, 12f)
        gainProcessor.setGainDb(safe)
        mpvHost?.setVolumeBoostDb(safe)
        _state.update { it.copy(volumeBoostDb = safe) }
    }

    fun setExpanded(expanded: Boolean) {
        _state.update { it.copy(isExpanded = expanded) }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null || minutes <= 0) {
            sleepDeadlineElapsedMs = 0L
            _state.update { it.copy(sleepTimerRemainingMs = null, sleepStopAtTrackEnd = false) }
            return
        }
        val durationMs = minutes * 60_000L
        sleepDeadlineElapsedMs = android.os.SystemClock.elapsedRealtime() + durationMs
        _state.update { it.copy(sleepTimerRemainingMs = durationMs, sleepStopAtTrackEnd = false) }
        sleepTimerJob = scope.launch {
            while (isActive) {
                val rem = sleepDeadlineElapsedMs - android.os.SystemClock.elapsedRealtime()
                if (rem <= 0L) {
                    _state.update { it.copy(sleepTimerRemainingMs = null) }
                    pause()
                    break
                }
                _state.update { it.copy(sleepTimerRemainingMs = rem) }
                delay(1_000L)
            }
        }
    }

    fun setSleepStopAtTrackEnd(enabled: Boolean) {
        if (enabled) {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            sleepDeadlineElapsedMs = 0L
            _state.update { it.copy(sleepTimerRemainingMs = null, sleepStopAtTrackEnd = true) }
        } else {
            _state.update { it.copy(sleepStopAtTrackEnd = false) }
        }
    }

    /**
     * Kullanıcı İndirilenler'den bir ses dosyasını sildiğinde, silinen parça kuyrukta ise
     * temiz biçimde kuyruktan düşürür.
     */
    fun onAudioFileDeleted(deletedPath: String?, deletedUri: String? = null) {
        val st = _state.value
        if (st.queue.isEmpty()) return
        val matches: (AudioPlayerItem) -> Boolean = { item ->
            (!deletedPath.isNullOrBlank() && item.filePath == deletedPath) ||
                (!deletedUri.isNullOrBlank() && item.uriString == deletedUri)
        }
        if (st.queue.none(matches)) return

        val currentRemoved = st.currentItem?.let(matches) == true
        val remaining = st.queue.filterNot(matches)
        if (remaining.isEmpty()) {
            stopAndClear()
            return
        }
        if (currentRemoved) {
            val nextIdx = st.currentIndex.coerceIn(0, remaining.lastIndex)
            val wasPlaying = st.isPlaying
            _state.update {
                it.copy(
                    queue = remaining,
                    currentIndex = nextIdx,
                    shuffleOrder = if (it.shuffleEnabled) buildShuffleOrder(remaining.size, nextIdx) else emptyList()
                )
            }
            loadCurrentItem(startPositionMs = 0L, autoPlay = wasPlaying)
        } else {
            val currentId = st.currentItem?.id
            val newIdx = remaining.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            _state.update {
                it.copy(
                    queue = remaining,
                    currentIndex = newIdx,
                    shuffleOrder = if (it.shuffleEnabled) buildShuffleOrder(remaining.size, newIdx) else emptyList()
                )
            }
        }
    }

    fun stopAndClear() {
        progressTickerJob?.cancel()
        progressTickerJob = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        probeJob?.cancel()
        probeJob = null
        runCatching { exoPlayer?.stop() }
        runCatching { exoPlayer?.clearMediaItems() }
        runCatching { mpvHost?.stopPlayback() }
        _artworkBitmap.value = null
        _state.update {
            AudioPlayerState(
                playbackSpeed = it.playbackSpeed,
                preservePitch = it.preservePitch,
                volumeBoostDb = it.volumeBoostDb,
                repeatMode = it.repeatMode,
                shuffleEnabled = it.shuffleEnabled
            )
        }
        KitsugiAudioPlaybackService.stop(appContext)
    }

    private fun onActiveTrackEnded() {
        val st = _state.value
        if (st.sleepStopAtTrackEnd) {
            _state.update { it.copy(sleepStopAtTrackEnd = false, isPlaying = false) }
            syncForegroundService()
            return
        }
        val nextIdx = AudioFormatSupport.resolveNextIndex(
            currentIndex = st.currentIndex,
            queueSize = st.queue.size,
            repeatMode = st.repeatMode,
            shuffleOrder = st.shuffleOrder.takeIf { st.shuffleEnabled },
            autoAdvance = true
        )
        if (nextIdx != null) {
            if (nextIdx == st.currentIndex) {
                seekTo(0L)
                play()
            } else {
                _state.update { it.copy(currentIndex = nextIdx, errorMessage = null) }
                loadCurrentItem(startPositionMs = 0L, autoPlay = true)
            }
        } else {
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
            seekTo(0L)
            pause()
        }
    }

    private fun loadCurrentItem(startPositionMs: Long, autoPlay: Boolean) {
        val item = _state.value.currentItem ?: return
        probeJob?.cancel()
        _artworkBitmap.value = null

        val initialSpecs = AudioStreamSpecs(
            container = item.formatBadge.takeIf { it.isNotBlank() && it != "AUDIO" }
        )
        _state.update {
            it.copy(
                positionMs = startPositionMs,
                durationMs = item.durationMs.coerceAtLeast(0L),
                isBuffering = true,
                streamSpecs = initialSpecs,
                errorMessage = null
            )
        }

        probeJob = scope.launch {
            probeArtworkAndStreamInfo(item)
        }

        val nameOrUri = item.filePath ?: item.uriString
        if (AudioFormatSupport.prefersMpvDirectly(nameOrUri, item.mimeType)) {
            runCatching { exoPlayer?.stop() }
            startOnMpvBackend(item, startPositionMs = startPositionMs, autoPlay = autoPlay)
        } else {
            runCatching { mpvHost?.stopPlayback() }
            startOnExoBackend(item, startPositionMs = startPositionMs, autoPlay = autoPlay)
        }
    }

    private fun startOnExoBackend(item: AudioPlayerItem, startPositionMs: Long, autoPlay: Boolean) {
        _state.update { it.copy(backendKind = AudioBackendKind.MEDIA3_FFMPEG) }
        val player = ensureExoPlayer()
        applySpeedAndPitchToBackends(_state.value.playbackSpeed, _state.value.preservePitch)
        gainProcessor.setGainDb(_state.value.volumeBoostDb)

        val uri = resolveMediaUri(item)
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)
        item.mimeType?.takeIf { it.isNotBlank() }?.let { mediaItemBuilder.setMimeType(it) }

        runCatching {
            player.setMediaItem(mediaItemBuilder.build(), startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = autoPlay
            if (autoPlay) syncForegroundService(forcePlaying = true)
        }.onFailure { t ->
            Log.w(TAG, "ExoPlayer hazırlama hatası, MPV'ye geçiliyor: ${t.message}")
            startOnMpvBackend(item, startPositionMs = startPositionMs, autoPlay = autoPlay)
        }
    }

    private fun startOnMpvBackend(item: AudioPlayerItem, startPositionMs: Long, autoPlay: Boolean) {
        _state.update {
            it.copy(
                backendKind = AudioBackendKind.MPV_FFMPEG,
                audioSessionId = 0,
                errorMessage = null
            )
        }
        val host = ensureMpvHost()
        host.setSpeedAndPitch(_state.value.playbackSpeed, _state.value.preservePitch)
        host.setVolumeBoostDb(_state.value.volumeBoostDb)
        val source = item.filePath?.takeIf { File(it).exists() } ?: item.uriString
        host.load(source, startPositionMs = startPositionMs, autoPlay = autoPlay)
        if (autoPlay) syncForegroundService(forcePlaying = true)
    }

    private fun ensureExoPlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                val caps = AudioCapabilities.getCapabilities(context)
                return DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(caps)
                    .setAudioProcessors(arrayOf(gainProcessor))
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(true)
                    .build()
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setAdtsExtractorFlags(
                AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                    AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
            )
            .setMp3ExtractorFlags(
                Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                    Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING
            )
            .setAmrExtractorFlags(
                AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                    AmrExtractor.FLAG_ENABLE_INDEX_SEEKING
            )
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(appContext, extractorsFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { player ->
                player.addListener(exoListener)
                exoPlayer = player
            }
    }

    private fun ensureMpvHost(): KitsugiMpvAudioHost {
        mpvHost?.let { return it }
        return KitsugiMpvAudioHost(appContext, mpvListener).also { mpvHost = it }
    }

    private fun applySpeedAndPitchToBackends(speed: Float, preservePitch: Boolean) {
        val pitch = if (preservePitch) 1.0f else speed
        exoPlayer?.playbackParameters = PlaybackParameters(speed, pitch)
        mpvHost?.setSpeedAndPitch(speed, preservePitch)
    }

    private fun updateTickerState() {
        progressTickerJob?.cancel()
        progressTickerJob = null
        if (!_state.value.isPlaying) return
        progressTickerJob = scope.launch {
            while (isActive && _state.value.isPlaying) {
                when (_state.value.backendKind) {
                    AudioBackendKind.MEDIA3_FFMPEG -> {
                        val p = exoPlayer
                        if (p != null) {
                            val pos = p.currentPosition.coerceAtLeast(0L)
                            val dur = p.duration.takeIf { it > 0L } ?: _state.value.durationMs
                            _state.update { it.copy(positionMs = pos, durationMs = dur) }
                        }
                    }
                    AudioBackendKind.MPV_FFMPEG -> {
                        val h = mpvHost
                        if (h != null) {
                            val pos = h.currentPositionMs()
                            val dur = h.durationMs().takeIf { it > 0L } ?: _state.value.durationMs
                            _state.update { it.copy(positionMs = pos, durationMs = dur) }
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    private fun syncForegroundService(forcePlaying: Boolean = false) {
        val st = _state.value
        if (!st.isActive) return
        KitsugiAudioPlaybackService.sync(appContext, isPlaying = forcePlaying || st.isPlaying)
    }

    private fun resolveMediaUri(item: AudioPlayerItem): Uri {
        val path = item.filePath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) return Uri.fromFile(file)
        }
        return if (item.uriString.startsWith("/")) {
            Uri.fromFile(File(item.uriString))
        } else {
            Uri.parse(item.uriString)
        }
    }

    private suspend fun probeArtworkAndStreamInfo(item: AudioPlayerItem) = withContext(Dispatchers.IO) {
        var embeddedArt: Bitmap? = null
        var retrieverDurMs = 0L
        var retrieverBitrateKbps = 0
        var retrieverSampleRate = 0

        val retriever = MediaMetadataRetriever()
        try {
            val path = item.filePath?.takeIf { File(it).exists() }
            if (path != null) {
                retriever.setDataSource(path)
            } else {
                retriever.setDataSource(appContext, resolveMediaUri(item))
            }
            retriever.embeddedPicture?.let { bytes ->
                embeddedArt = decodeScaledBitmap(bytes)
            }
            retrieverDurMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retrieverBitrateKbps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()?.let { it / 1000 } ?: 0
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                retrieverSampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull() ?: 0
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { retriever.release() }
        }

        if (embeddedArt == null && !item.artworkUrlOrPath.isNullOrBlank()) {
            val artFile = when {
                item.artworkUrlOrPath.startsWith("file://") -> File(Uri.parse(item.artworkUrlOrPath).path.orEmpty())
                item.artworkUrlOrPath.startsWith("/") -> File(item.artworkUrlOrPath)
                else -> null
            }
            if (artFile != null && artFile.exists()) {
                embeddedArt = runCatching { decodeScaledFileBitmap(artFile.absolutePath) }.getOrNull()
            }
        }

        // FFmpeg tabanlı NextLib MediaInfo ile kodek / örnekleme hızı / kanal sayısını tamamla.
        var ffCodec: String? = null
        var ffSampleRate = 0
        var ffChannels = 0
        var ffBitrateKbps = 0
        var ffContainer: String? = null
        var ffDurMs = 0L
        var mediaInfo: MediaInfo? = null
        try {
            val probeUri = resolveMediaUri(item)
            mediaInfo = MediaInfoBuilder().from(context = appContext, uri = probeUri).build()
            if (mediaInfo != null) {
                ffDurMs = mediaInfo.duration.coerceAtLeast(0L)
                ffContainer = mediaInfo.format.substringBefore(',').takeIf { it.isNotBlank() }
                val audio = mediaInfo.audioStreams.firstOrNull()
                if (audio != null) {
                    ffCodec = AudioFormatSupport.normalizeCodecLabel(audio.codecName)
                    ffSampleRate = audio.sampleRate
                    ffChannels = audio.channels
                    ffBitrateKbps = (audio.bitRate / 1000L).toInt().coerceAtLeast(0)
                }
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { mediaInfo?.release() }
        }

        withContext(Dispatchers.Main.immediate) {
            if (_state.value.currentItem?.id != item.id) return@withContext
            if (embeddedArt != null) {
                _artworkBitmap.value = embeddedArt
            }
            _state.update { st ->
                val resolvedDur = when {
                    st.durationMs > 0L -> st.durationMs
                    retrieverDurMs > 0L -> retrieverDurMs
                    ffDurMs > 0L -> ffDurMs
                    else -> 0L
                }
                st.copy(
                    durationMs = resolvedDur,
                    streamSpecs = AudioStreamSpecs(
                        codec = st.streamSpecs.codec ?: ffCodec,
                        sampleRateHz = when {
                            st.streamSpecs.sampleRateHz > 0 -> st.streamSpecs.sampleRateHz
                            ffSampleRate > 0 -> ffSampleRate
                            else -> retrieverSampleRate
                        },
                        channelCount = if (st.streamSpecs.channelCount > 0) st.streamSpecs.channelCount else ffChannels,
                        bitrateKbps = when {
                            st.streamSpecs.bitrateKbps > 0 -> st.streamSpecs.bitrateKbps
                            ffBitrateKbps > 0 -> ffBitrateKbps
                            else -> retrieverBitrateKbps
                        },
                        container = st.streamSpecs.container ?: ffContainer
                    )
                )
            }
            syncForegroundService()
        }
    }

    private fun buildItemFromUri(uri: Uri, titleHint: String?): AudioPlayerItem {
        var displayName: String? = null
        var sizeBytes = 0L
        if (uri.scheme == "content") {
            runCatching {
                appContext.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        val filePath = if (uri.scheme == "file" || uri.scheme == null) uri.path else null
        if (displayName.isNullOrBlank()) {
            displayName = filePath?.let { File(it).name } ?: uri.lastPathSegment
        }
        if (sizeBytes <= 0L && !filePath.isNullOrBlank()) {
            sizeBytes = runCatching { File(filePath).length() }.getOrDefault(0L)
        }
        val safeFileName = displayName?.takeIf { it.isNotBlank() } ?: titleHint ?: "Ses Parçası"
        val parsed = DownloadExportNaming.parseAudioFileName(safeFileName)
        val mime = if (uri.scheme == "content") {
            runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
        } else {
            null
        }
        return AudioPlayerItem(
            id = uri.toString(),
            uriString = uri.toString(),
            filePath = filePath,
            title = titleHint?.takeIf { it.isNotBlank() } ?: parsed.displayTitle,
            subtitle = parsed.trackLabel ?: parsed.animeTitle,
            animeTitle = parsed.animeTitle,
            episode = parsed.episode,
            trackLabel = parsed.trackLabel,
            formatBadge = parsed.extension.uppercase(Locale.ROOT).ifBlank { "AUDIO" },
            mimeType = mime,
            sizeBytes = sizeBytes
        )
    }

    private fun buildShuffleOrder(size: Int, currentIndex: Int): List<Int> {
        if (size <= 1) return (0 until size).toList()
        val others = (0 until size).filter { it != currentIndex }.shuffled()
        return listOf(currentIndex) + others
    }

    private fun decodeScaledBitmap(bytes: ByteArray, maxDim: Int = 512): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun decodeScaledFileBitmap(path: String, maxDim: Int = 512): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val TAG = "KitsugiAudioManager"

        @Volatile
        private var instance: KitsugiAudioPlayerManager? = null

        fun getInstance(context: Context): KitsugiAudioPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: KitsugiAudioPlayerManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
