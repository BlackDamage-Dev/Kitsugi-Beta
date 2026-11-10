package com.kitsugi.animelist.core.p2p

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.nuvio.engine.NuvioEngine
import com.nuvio.engine.NuvioEngineConfig
import com.nuvio.engine.NuvioEventType
import com.nuvio.engine.NuvioStream
import com.nuvio.engine.NuvioTorrentProfile
import com.nuvio.engine.NuvioUploadMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

private const val TAG = "KitsugiP2pEngine"
private const val DIAGNOSTIC_TAG = "KitsugiP2PDiag"
private const val DIAGNOSTIC_SAMPLE_INTERVAL_MS = 1_000L

object P2pStreamingEngine {
    private data class EngineConfigurationKey(
        val uploadEnabled: Boolean,
        val uploadLimitBytesPerSecond: Long,
        val torrentProfile: P2pTorrentProfile,
        val diskCacheCapacityBytes: Long,
    )

    private data class DetachedStream(
        val engine: NuvioEngine?,
        val streamId: String?,
        val torrentId: String?,
    )

    private val _state = MutableStateFlow<P2pStreamingState>(P2pStreamingState.Idle)
    val state: StateFlow<P2pStreamingState> = _state.asStateFlow()

    private val _cacheState = MutableStateFlow(P2pCacheUiState())
    val cacheState: StateFlow<P2pCacheUiState> = _cacheState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleLock = Any()
    private val startMutex = Mutex()
    private var statsJob: Job? = null
    private var cleanupJob: Job? = null
    private var engineEventsJob: Job? = null
    private var streamGeneration = 0L

    @Volatile
    private var currentTorrentId: String? = null

    @Volatile
    private var currentStreamId: String? = null

    private var appContext: Context? = null

    @Volatile
    private var engine: NuvioEngine? = null
    private var engineConfigurationKey: EngineConfigurationKey? = null
    private val knownTorrentIds = mutableSetOf<String>()
    private var diagnosticRequestSequence = 0L

    fun initialize(context: Context) {
        appContext = context.applicationContext
        // Önceki oturumdan (uygulama öldürüldüğü için kapanış temizliği çalışmamış olabilir)
        // kalan torrent parçalarını, motor henüz açılmadan süpür.
        scope.launch { sweepLeftoverCacheOnLaunch() }
    }

    /**
     * Açılışta, motor henüz çalışmıyorken ve aktif akış yokken önbellek artıklarını siler.
     * "Video kapanınca temizle" ayarı kapalıysa dokunmaz.
     */
    private suspend fun sweepLeftoverCacheOnLaunch() {
        startMutex.withLock {
            if (engine != null || _state.value.isStreaming) return
            if (!P2pSettingsRepository.isClearCacheOnCloseEnabled()) return
            val context = appContext ?: return
            val dir = File(context.cacheDir, "p2p-engine/payload")
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return
            children.forEach { child -> runCatching { child.deleteRecursively() } }
        }
    }

    suspend fun startStream(request: P2pStreamRequest): String = withContext(Dispatchers.IO) {
        startMutex.withLock { startStreamLocked(request) }
    }

    suspend fun clearCache(): P2pCacheClearResult = withContext(Dispatchers.IO) {
        startMutex.withLock {
            check(
                _state.value !is P2pStreamingState.Streaming &&
                        _state.value !is P2pStreamingState.Connecting
            ) {
                "Aktif oynatma sırasında torrent önbelleği temizlenemez"
            }
            _cacheState.value = _cacheState.value.copy(isClearing = true)
            try {
                val activeEngine = ensureEngine()
                if (!_cacheState.value.hasMeasurement) {
                    delay(DIAGNOSTIC_SAMPLE_INTERVAL_MS + 100L)
                    val initial = activeEngine.stats.value
                    updateCacheState(
                        initial.diskCacheUsedBytes,
                        initial.diskCacheProtectedBytes,
                    )
                }
                val before = activeEngine.stats.value
                activeEngine.reclaimDiskCache(0L)
                delay(DIAGNOSTIC_SAMPLE_INTERVAL_MS + 100L)
                val after = activeEngine.stats.value
                updateCacheState(after.diskCacheUsedBytes, after.diskCacheProtectedBytes)
                P2pCacheClearResult(
                    reclaimedBytes = (before.diskCacheUsedBytes - after.diskCacheUsedBytes).coerceAtLeast(0L),
                    remainingBytes = after.diskCacheUsedBytes,
                    protectedBytes = after.diskCacheProtectedBytes,
                )
            } finally {
                _cacheState.value = _cacheState.value.copy(isClearing = false)
            }
        }
    }

    private suspend fun startStreamLocked(request: P2pStreamRequest): String {
        val requestSequence = nextDiagnosticRequestSequence()
        val startedAtMs = SystemClock.elapsedRealtime()
        val phase = AtomicReference("stop_previous")
        Log.i(
            DIAGNOSTIC_TAG,
            "start request=$requestSequence phase=accepted hash=${diagnosticId(request.infoHash)} " +
                    "fileIndex=${request.fileIdx ?: -1} filenameHint=${!request.filename.isNullOrBlank()} " +
                    "requestTrackers=${request.trackers.size}",
        )
        stopStreamNow(shutdownEngine = false)
        val generation = beginStreamGeneration()

        var activeEngine: NuvioEngine? = null
        var payloadDownloadBaseline = 0L
        var preparedStream: NuvioStream? = null
        var attached = false
        // Bu başlatma sırasında motora eklenen torrent (akış bağlanamazsa da kaldırılmalı).
        var addedTorrentId: String? = null
        var startupStatsJob: Job? = null
        return try {
            phase.set("build_magnet")
            val magnetUri = buildP2pMagnetUri(
                request.infoHash,
                (DEFAULT_TRACKERS + request.trackers).distinct(),
            )

            phase.set("ensure_engine")
            val resolvedEngine = ensureEngine()
            activeEngine = resolvedEngine
            payloadDownloadBaseline = resolvedEngine.stats.value.totalPayloadDownloadBytes
            ensureCurrentGeneration(generation)

            startupStatsJob = startStartupStatsPolling(
                activeEngine = resolvedEngine,
                generation = generation,
                phase = phase,
                requestSequence = requestSequence,
                startedAtMs = startedAtMs,
            )

            phase.set("add_magnet")
            val canonicalHash = canonicalP2pInfoHash(request.infoHash)
            val reusedTorrent = canonicalHash in knownTorrentIds
            val torrentId = if (reusedTorrent) {
                canonicalHash
            } else {
                resolvedEngine.addMagnet(magnetUri).also {
                    knownTorrentIds += it
                    addedTorrentId = it
                }
            }
            ensureCurrentGeneration(generation)

            phase.set("prepare_stream")
            val stream = resolvedEngine.prepareStream(
                torrentId = torrentId,
                fileIndex = request.fileIdx,
                filenameHint = request.filename,
            )
            preparedStream = stream
            currentCoroutineContext().ensureActive()

            phase.set("attach_route")
            if (!attachStreamIfCurrent(generation, torrentId, stream.id)) {
                withContext(NonCancellable) {
                    stopPreparedStream(resolvedEngine, stream.id)
                }
                preparedStream = null
                throw CancellationException("P2P stream start was cancelled")
            }
            attached = true

            startStatsPolling(
                activeEngine = resolvedEngine,
                stream = stream,
                generation = generation,
                requestSequence = requestSequence,
                startedAtMs = startedAtMs,
                payloadDownloadBaseline = payloadDownloadBaseline,
            )
            val initialAggregate = resolvedEngine.stats.value
            if (!publishStreamingIfCurrent(
                    generation = generation,
                    state = P2pStreamingState.Streaming(
                        localUrl = stream.url,
                        downloadSpeed = initialAggregate.downloadRateBytesPerSecond,
                        uploadSpeed = initialAggregate.uploadRateBytesPerSecond,
                        peers = initialAggregate.connectedPeers,
                        seeds = initialAggregate.connectedSeeds,
                        bufferProgress = 0f,
                        totalProgress = 0f,
                        downloadedBytes = (initialAggregate.totalPayloadDownloadBytes - payloadDownloadBaseline).coerceAtLeast(0L),
                    ),
                )
            ) {
                throw CancellationException("P2P stream start was cancelled")
            }
            Log.i(TAG, "P2P Stream ready: ${stream.url}")
            stream.url
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                cleanupFailedStart(
                    generation = generation,
                    activeEngine = activeEngine,
                    preparedStream = preparedStream,
                    attached = attached,
                    addedTorrentId = addedTorrentId,
                    terminalState = P2pStreamingState.Idle,
                )
            }
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "P2P stream error", error)
            val terminalState = P2pStreamingState.Error(
                error.message ?: "Bilinmeyen torrent hatası oluştu"
            )
            withContext(NonCancellable) {
                cleanupFailedStart(
                    generation = generation,
                    activeEngine = activeEngine,
                    preparedStream = preparedStream,
                    attached = attached,
                    addedTorrentId = addedTorrentId,
                    terminalState = terminalState,
                )
            }
            throw error
        } finally {
            startupStatsJob?.cancel()
        }
    }

    fun stopStream() {
        scheduleStop(shutdownEngine = false)
    }

    fun shutdown() {
        scheduleStop(shutdownEngine = true)
    }

    private fun scheduleStop(shutdownEngine: Boolean) {
        val detached = detachActiveStream()
        val previousCleanup = cleanupJob
        cleanupJob = scope.launch {
            previousCleanup?.join()
            cleanupDetachedStream(detached, shutdownEngine)
        }
    }

    private suspend fun stopStreamNow(shutdownEngine: Boolean) {
        cleanupJob?.join()
        val detached = detachActiveStream()
        cleanupDetachedStream(detached, shutdownEngine)
    }

    private fun detachActiveStream(): DetachedStream {
        val detached: Pair<DetachedStream, Job?> = synchronized(lifecycleLock) {
            streamGeneration += 1
            val value = DetachedStream(engine = engine, streamId = currentStreamId, torrentId = currentTorrentId)
            val job = statsJob
            currentTorrentId = null
            currentStreamId = null
            statsJob = null
            _state.value = P2pStreamingState.Idle
            value to job
        }
        detached.second?.cancel()
        return detached.first
    }

    private fun detachGenerationIfCurrent(
        generation: Long,
        terminalState: P2pStreamingState,
    ): DetachedStream? {
        val detached: Pair<DetachedStream, Job?>? = synchronized(lifecycleLock) {
            if (streamGeneration != generation) return@synchronized null
            streamGeneration += 1
            val value = DetachedStream(engine = engine, streamId = currentStreamId, torrentId = currentTorrentId)
            val job = statsJob
            currentTorrentId = null
            currentStreamId = null
            statsJob = null
            _state.value = terminalState
            value to job
        }
        detached?.second?.cancel()
        return detached?.first
    }

    private suspend fun cleanupDetachedStream(detached: DetachedStream, shutdownEngine: Boolean) {
        detached.streamId?.let { streamId ->
            stopPreparedStream(detached.engine, streamId)
        }
        // Oynatıcı kapandı / akış değişti: torrent'i motordan kaldır ve parçalarını diskten geri al.
        // Motor, AKTİF (korunan) torrent verisini tahliye etmez ("reclaim limited by protected
        // torrent data"); torrent kaldırılmadan önbellek sıfırlanamaz ve sınır hiç uygulanmazdı.
        if (detached.streamId != null && P2pSettingsRepository.isClearCacheOnCloseEnabled()) {
            reclaimCacheAfterStream(detached.engine, detached.torrentId)
        }
        if (shutdownEngine) {
            closeEngine(detached.engine)
        }
    }

    /**
     * Akış durdurulduktan sonra: torrent'i motordan kaldırır (koruma kalksın), ardından
     * önbelleği sıfır hedefe indirir. Kaldırılan torrent, bir sonraki açılışta yeniden eklenir.
     */
    private suspend fun reclaimCacheAfterStream(target: NuvioEngine?, torrentId: String?) {
        if (target == null) return
        withContext(NonCancellable) {
            if (torrentId != null) {
                runCatching { target.removeTorrent(torrentId) }
                    .onFailure { Log.w(TAG, "P2P torrent kaldırılamadı: ${it.message}") }
                knownTorrentIds.remove(torrentId)
            }
            runCatching { target.reclaimDiskCache(0L) }
                .onFailure { Log.w(TAG, "P2P önbelleği kapanışta boşaltılamadı: ${it.message}") }
        }
    }

    private suspend fun cleanupFailedStart(
        generation: Long,
        activeEngine: NuvioEngine?,
        preparedStream: NuvioStream?,
        attached: Boolean,
        addedTorrentId: String?,
        terminalState: P2pStreamingState,
    ) {
        if (attached) {
            detachGenerationIfCurrent(generation, terminalState)?.let { detached ->
                cleanupDetachedStream(detached, shutdownEngine = false)
            }
        } else {
            preparedStream?.let { stream -> stopPreparedStream(activeEngine, stream.id) }
            detachGenerationIfCurrent(generation, terminalState)
            // Bağlanamayan torrent motorda korunmaya devam etmesin.
            if (addedTorrentId != null && P2pSettingsRepository.isClearCacheOnCloseEnabled()) {
                reclaimCacheAfterStream(activeEngine, addedTorrentId)
            }
        }
    }

    private suspend fun stopPreparedStream(activeEngine: NuvioEngine?, streamId: String) {
        try {
            activeEngine?.stopStream(streamId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "Error stopping stream", error)
        }
    }

    private suspend fun ensureEngine(): NuvioEngine {
        P2pSettingsRepository.ensureLoaded()
        val settings = P2pSettingsRepository.uiState.value
        val uploadLimitBytes = if (settings.uploadLimitKbps > 0L) settings.uploadLimitKbps * 1024L else 0L
        val configurationKey = EngineConfigurationKey(
            uploadEnabled = settings.enableUpload,
            uploadLimitBytesPerSecond = uploadLimitBytes,
            torrentProfile = settings.torrentProfile,
            diskCacheCapacityBytes = settings.cacheSize.bytes,
        )
        engine?.takeIf { engineConfigurationKey == configurationKey }?.let {
            return it
        }

        closeEngine(engine)
        currentCoroutineContext().ensureActive()
        val context = requireContext()
        val stateDirectory = File(context.noBackupFilesDir, "p2p-engine/state")
        val cacheDirectory = File(context.cacheDir, "p2p-engine/payload")
        check(stateDirectory.mkdirs() || stateDirectory.isDirectory) {
            "Could not create P2P engine state directory"
        }
        check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) {
            "Could not create P2P engine cache directory"
        }

        val config = NuvioEngineConfig(
            dataDirectory = stateDirectory,
            cacheDirectory = cacheDirectory,
            diskCacheCapacityBytes = configurationKey.diskCacheCapacityBytes,
            torrentProfile = when (configurationKey.torrentProfile) {
                P2pTorrentProfile.SOFT -> NuvioTorrentProfile.Soft
                P2pTorrentProfile.BALANCED -> NuvioTorrentProfile.Balanced
                P2pTorrentProfile.FAST -> NuvioTorrentProfile.Fast
            },
            uploadMode = when {
                !configurationKey.uploadEnabled -> NuvioUploadMode.Disabled
                configurationKey.uploadLimitBytesPerSecond > 0L -> NuvioUploadMode.Limited
                else -> NuvioUploadMode.Unlimited
            },
            uploadLimitBytesPerSecond = configurationKey.uploadLimitBytesPerSecond,
            streamInactivityTimeoutMilliseconds = 0,
        )

        return NuvioEngine.create(config).also { created ->
            engine = created
            engineConfigurationKey = configurationKey
            observeEngineEvents(created)
            Log.i(TAG, "Using P2P Engine ${NuvioEngine.version} (${NuvioEngine.protocolBackendVersion})")
        }
    }

    private suspend fun closeEngine(target: NuvioEngine?) {
        if (target == null) return
        if (engine === target) {
            engineEventsJob?.cancel()
            engineEventsJob = null
            engine = null
            engineConfigurationKey = null
            knownTorrentIds.clear()
        }
        withContext(NonCancellable) {
            try {
                target.shutdown()
            } catch (error: Exception) {
                Log.w(TAG, "Error shutting down P2P engine", error)
            }
        }
    }

    private fun observeEngineEvents(activeEngine: NuvioEngine) {
        engineEventsJob?.cancel()
        engineEventsJob = scope.launch {
            activeEngine.events.collect { event ->
                if (engine !== activeEngine) return@collect
                when (event.type) {
                    NuvioEventType.TorrentError -> {
                        synchronized(lifecycleLock) {
                            if (engine !== activeEngine) return@synchronized
                            if (currentTorrentId != null && event.torrentId == currentTorrentId) {
                                streamGeneration += 1
                                statsJob?.cancel()
                                statsJob = null
                                _state.value = P2pStreamingState.Error(
                                    event.message?.trim()?.takeIf { it.isNotEmpty() } ?: KitsugiStrings.get(R.string.torrent_baglanti_hatasi)
                                )
                            }
                        }
                    }
                    NuvioEventType.StreamStopped -> {
                        if (event.requestId != 0L) return@collect
                        synchronized(lifecycleLock) {
                            if (engine !== activeEngine) return@synchronized
                            if (currentStreamId != null && event.streamId == currentStreamId) {
                                streamGeneration += 1
                                currentTorrentId = null
                                currentStreamId = null
                                statsJob?.cancel()
                                statsJob = null
                                _state.value = P2pStreamingState.Error(
                                    event.message?.trim()?.takeIf { it.isNotEmpty() } ?: KitsugiStrings.get(R.string.akis_durduruldu)
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun startStatsPolling(
        activeEngine: NuvioEngine,
        stream: NuvioStream,
        generation: Long,
        requestSequence: Long,
        startedAtMs: Long,
        payloadDownloadBaseline: Long,
    ) {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                if (!isCurrentGeneration(generation)) return@launch
                val currentState = _state.value
                if (currentState is P2pStreamingState.Streaming) {
                    val route = try {
                        activeEngine.currentStreamStats(stream.id)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        null
                    }
                    val aggregate = activeEngine.stats.value
                    updateCacheState(
                        aggregate.diskCacheUsedBytes,
                        aggregate.diskCacheProtectedBytes,
                    )
                    updateStreamingIfCurrent(generation) { latestState ->
                        latestState.copy(
                            downloadSpeed = aggregate.downloadRateBytesPerSecond,
                            uploadSpeed = aggregate.uploadRateBytesPerSecond,
                            peers = aggregate.connectedPeers,
                            seeds = aggregate.connectedSeeds,
                            bufferProgress = normalizeP2pBufferProgress(route?.bufferProgress)
                                ?: latestState.bufferProgress,
                            totalProgress = route?.fileProgress ?: latestState.totalProgress,
                            downloadedBytes = (aggregate.totalPayloadDownloadBytes - payloadDownloadBaseline).coerceAtLeast(0L),
                            verifiedBytes = route?.verifiedFileBytes ?: latestState.verifiedBytes,
                            deliveredBytes = route?.deliveredBytes ?: latestState.deliveredBytes,
                        )
                    }
                }
                delay(DIAGNOSTIC_SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun updateCacheState(usedBytes: Long, protectedBytes: Long) {
        _cacheState.value = _cacheState.value.copy(
            usedBytes = usedBytes,
            protectedBytes = protectedBytes,
            hasMeasurement = true,
        )
    }

    private fun startStartupStatsPolling(
        activeEngine: NuvioEngine,
        generation: Long,
        phase: AtomicReference<String>,
        requestSequence: Long,
        startedAtMs: Long,
    ): Job = scope.launch {
        while (isActive) {
            val aggregate = activeEngine.stats.value
            updateConnectingIfCurrent(
                generation = generation,
                phase = phase.get(),
                downloadSpeed = aggregate.downloadRateBytesPerSecond,
                uploadSpeed = aggregate.uploadRateBytesPerSecond,
                peers = aggregate.connectedPeers,
                seeds = aggregate.connectedSeeds,
            )
            delay(DIAGNOSTIC_SAMPLE_INTERVAL_MS)
        }
    }

    private fun beginStreamGeneration(): Long = synchronized(lifecycleLock) {
        streamGeneration += 1
        _state.value = P2pStreamingState.Connecting()
        streamGeneration
    }

    private fun updateConnectingIfCurrent(
        generation: Long,
        phase: String,
        downloadSpeed: Long,
        uploadSpeed: Long,
        peers: Int,
        seeds: Int,
    ) = synchronized(lifecycleLock) {
        if (streamGeneration != generation || _state.value !is P2pStreamingState.Connecting) {
            return@synchronized
        }
        _state.value = P2pStreamingState.Connecting(
            phase = phase,
            downloadSpeed = downloadSpeed,
            uploadSpeed = uploadSpeed,
            peers = peers,
            seeds = seeds,
        )
    }

    private fun nextDiagnosticRequestSequence(): Long = synchronized(lifecycleLock) {
        diagnosticRequestSequence += 1
        diagnosticRequestSequence
    }

    private fun attachStreamIfCurrent(
        generation: Long,
        torrentId: String,
        streamId: String,
    ): Boolean = synchronized(lifecycleLock) {
        if (streamGeneration != generation) return@synchronized false
        currentTorrentId = torrentId
        currentStreamId = streamId
        true
    }

    private fun publishStreamingIfCurrent(
        generation: Long,
        state: P2pStreamingState.Streaming,
    ): Boolean = synchronized(lifecycleLock) {
        if (streamGeneration != generation || currentStreamId == null) {
            return@synchronized false
        }
        _state.value = state
        true
    }

    private fun updateStreamingIfCurrent(
        generation: Long,
        update: (P2pStreamingState.Streaming) -> P2pStreamingState.Streaming,
    ) = synchronized(lifecycleLock) {
        if (streamGeneration != generation) return@synchronized
        val current = _state.value as? P2pStreamingState.Streaming ?: return@synchronized
        _state.value = update(current)
    }

    private fun isCurrentGeneration(generation: Long): Boolean =
        synchronized(lifecycleLock) { streamGeneration == generation }

    private fun ensureCurrentGeneration(generation: Long) {
        if (!isCurrentGeneration(generation)) {
            throw CancellationException("P2P stream start was cancelled")
        }
    }

    private fun requireContext(): Context =
        appContext ?: throw P2pStreamingException("P2P streaming engine is not initialized")

    private fun diagnosticId(value: String?): String =
        value?.trim()?.take(12)?.ifBlank { "none" } ?: "none"

    private val DEFAULT_TRACKERS = listOf(
        "udp://zer0day.ch:1337/announce",
        "udp://tracker.publictracker.xyz:6969/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://open.stealth.si:80/announce",
        "http://tracker.renfei.net:8080/announce",
        "udp://udp.tracker.projectk.org:23333/announce",
        "udp://tracker.tryhackx.org:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.startwork.cv:1337/announce",
        "udp://tracker.qu.ax:6969/announce",
        "udp://tracker.plx.im:6969/announce",
        "udp://tracker.nyaa.vc:6969/announce",
        "udp://tracker.iperson.xyz:6969/announce",
        "udp://tracker.gmi.gd:6969/announce",
        "udp://tracker.fnix.net:6969/announce",
        "udp://tracker.flatuslifir.is:6969/announce",
        "udp://tracker.ducks.party:1984/announce",
        "udp://tracker.bluefrog.pw:2710/announce",
    )
}
