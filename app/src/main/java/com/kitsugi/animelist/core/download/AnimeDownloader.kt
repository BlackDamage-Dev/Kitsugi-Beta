package com.kitsugi.animelist.core.download
import com.kitsugi.animelist.core.download.ClipDownloadSource
import com.kitsugi.animelist.core.download.DownloadExportNaming
import kotlinx.coroutines.delay


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.core.player.OfflinePlaybackHelper
import com.kitsugi.animelist.core.player.PlayerSubtitleUtils
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.model.DownloadKind
import com.kitsugi.animelist.data.model.ExternalAudioTrack
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.data.repository.SubtitleRepositoryImpl
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * İndirme tamamlandı ama dosya bu cihazda oynatılamıyor (desteklenmeyen codec).
 */
class UnsupportedMediaException(message: String) : IOException(message)

/**
 * Evrensel bölüm (video+ses) ve bağımsız ses indirme motoru.
 *
 * - Gömülü ve ayrık (`#EXT-X-MEDIA:TYPE=AUDIO`) HLS (`master.txt`, `playlist.txt`, `.m3u8`, uzantısız URL)
 * - `AES-128` şifreli HLS, `#EXT-X-MAP` (fMP4/CMAF), `#EXT-X-BYTERANGE`, sahte PNG/JPEG başlıklı `.ts` segmentleri
 * - MPEG-DASH (`.mpd` — `SegmentTemplate`, `SegmentTimeline`, `SegmentList`, `BaseURL`)
 * - CloudStream `ExtractorLink.audioTracks` ve YouTube 10 MB `range=` dilimli akışlar
 * - Media3 `Extractor` + `MediaMuxer` ile kayıpsız remux/mux ve bağımsız ses ayıklama (`M4A`, `AAC`, `OPUS`, `MP3`, `EAC3`, `AC3`, `FLAC`, `WAV`, `DTS`)
 */
private const val MAX_CLIP_BYTES = 2L * 1024L * 1024L * 1024L

class AnimeDownloader(private val context: Context) {

    companion object {
        private const val TAG = "AnimeDownloader"
        private const val SEGMENT_CONCURRENCY = 4
        private const val SEGMENT_BATCH_SIZE = 16
    }

    suspend fun download(
        download: AnimeDownload,
        onProgress: (Int, Long, Long, Int) -> Unit,
        onStatusChanged: (AnimeDownload.Status, String?, String?) -> Unit
    ) {
        val settings = SettingsDataStore(context).settingsFlow.first()
        val downloaderPref = settings.downloaderPreference

        if (download.isClip) {
            downloadClip(download, onProgress) { status, detail ->
                if (status == AnimeDownload.Status.ERROR) {
                    onStatusChanged(status, null, detail)
                } else {
                    onStatusChanged(status, detail, null)
                }
            }
            return
        }

        // Bağımsız ses indirmeleri (demux/remux gerektirdiğinden) her zaman dahili motorla indirilir
        if (downloaderPref == "INTERNAL" || download.isAudioOnly) {
            downloadInternal(download, onProgress, onStatusChanged)
        } else {
            downloadExternal(download, downloaderPref, onStatusChanged)
        }
    }

    private suspend fun downloadInternal(
        download: AnimeDownload,
        onProgress: (Int, Long, Long, Int) -> Unit,
        onStatusChanged: (AnimeDownload.Status, String?, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        onStatusChanged(AnimeDownload.Status.DOWNLOADING, null, null)

        val workId = abs(download.uniqueKey.hashCode())
        val workDir = File(context.cacheDir, "kitsugi_dl_work_$workId")
        var startedP2pForDownload = false
        try {
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()

            val settings = SettingsDataStore(context).settingsFlow.first()
            val preferredAudioLangs = settings.preferredAudioLanguages
                .split(',')
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("tr", "ja", "en") }

            var activeDownload = download
            if (!download.p2pInfoHash.isNullOrBlank()) {
                val quickHttp = StreamDownloadHttpClient(baseHeaders = download.safeHeaders)
                val existingLocalReachable = download.url.startsWith("http://127.0.0.1") &&
                    runCatching { quickHttp.peekStream(download.url, maxPeekBytes = 512) }.isSuccess
                if (!existingLocalReachable) {
                    val wasAlreadyStreaming = com.kitsugi.animelist.core.p2p.P2pStreamingEngine.state.value is
                        com.kitsugi.animelist.core.p2p.P2pStreamingState.Streaming
                    val p2pLocalUrl = com.kitsugi.animelist.core.p2p.P2pStreamingEngine.startStream(
                        com.kitsugi.animelist.core.p2p.P2pStreamRequest(
                            infoHash = download.p2pInfoHash,
                            fileIdx = download.p2pFileIdx,
                            filename = download.streamTitle,
                            trackers = download.p2pTrackers.orEmpty()
                        )
                    )
                    activeDownload = download.copy(url = p2pLocalUrl)
                    startedP2pForDownload = !wasAlreadyStreaming
                }
            }

            val http = StreamDownloadHttpClient(
                addonName = activeDownload.source,
                streamTitle = activeDownload.streamTitle ?: activeDownload.animeTitle,
                isCloudStream = activeDownload.isCloudStream || !activeDownload.source.isNullOrBlank(),
                qualityValue = activeDownload.qualityValue,
                baseHeaders = activeDownload.safeHeaders
            )

            val bytesCounter = AtomicLong(0L)

            val finalFile = when (activeDownload.effectiveKind) {
                DownloadKind.AUDIO -> executeStandaloneAudioDownload(
                    download = activeDownload,
                    http = http,
                    workDir = workDir,
                    preferredAudioLangs = preferredAudioLangs,
                    safUriStr = settings.videoDownloadUri,
                    bytesCounter = bytesCounter,
                    onProgress = onProgress
                )

                DownloadKind.VIDEO -> executeVideoWithAudioDownload(
                    download = activeDownload,
                    http = http,
                    workDir = workDir,
                    preferredAudioLangs = preferredAudioLangs,
                    subtitleLangsCsv = settings.subtitleDownloadLanguages,
                    safUriStr = settings.videoDownloadUri,
                    bytesCounter = bytesCounter,
                    onProgress = onProgress
                )
            }

            val verification = DownloadVerifier.verify(finalFile)
            if (!verification.playable) {
                Log.w(TAG, "İndirilen dosya doğrulanamadı: ${verification.warning} (${verification.details})")
                runCatching { finalFile.delete() }
                throw UnsupportedMediaException(
                    verification.warning ?: KitsugiStrings.get(R.string.indirme_hata_desteklenmeyen_format)
                )
            }

            onProgress(100, finalFile.length(), finalFile.length(), 0)
            onStatusChanged(AnimeDownload.Status.COMPLETED, finalFile.absolutePath, null)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val errorMsg = when (e) {
                is RateLimitedException -> KitsugiStrings.get(R.string.indirme_hata_hiz_siniri, e.message.orEmpty())
                is UnsupportedMediaException -> e.message ?: KitsugiStrings.get(R.string.indirme_hata_desteklenmeyen_format)
                else -> e.message
            }
            Log.e(TAG, "İndirme başarısız (${download.uniqueKey}): $errorMsg", e)
            onStatusChanged(AnimeDownload.Status.ERROR, null, errorMsg)
        } finally {
            if (startedP2pForDownload) {
                runCatching { com.kitsugi.animelist.core.p2p.P2pStreamingEngine.stopStream() }
            }
            runCatching { workDir.deleteRecursively() }
        }
    }

    // ── 1) Bağımsız (Standalone) Ses İndirme Akışı ──────────────────────────

    private suspend fun executeStandaloneAudioDownload(
        download: AnimeDownload,
        http: StreamDownloadHttpClient,
        workDir: File,
        preferredAudioLangs: List<String>,
        safUriStr: String,
        bytesCounter: AtomicLong,
        onProgress: (Int, Long, Long, Int) -> Unit
    ): File {
        val rawAudioSource = File(workDir, "raw_audio_source.bin")
        val matchedExternal = pickMatchingExternalTrack(download)
        val masterHint = download.safeHeaders[StreamDownloadHttpClient.META_MASTER_URL_HEADER]
            ?.trim()?.takeIf { it.isNotBlank() }

        val targetUrl = matchedExternal?.url ?: masterHint ?: download.url
        val targetHeaders = matchedExternal?.httpHeaders().orEmpty()

        val peek = http.peekStream(targetUrl, targetHeaders)
        when (peek.detectedFormat) {
            StreamFormatSniffer.DetectedFormat.HTML_PAGE ->
                throw IOException("Sunucu ses akışı yerine HTML koruma/hata sayfası döndürdü")

            StreamFormatSniffer.DetectedFormat.HLS_MANIFEST -> {
                downloadHlsAudioStream(
                    manifestUrl = peek.finalUrl,
                    extraHeaders = targetHeaders,
                    download = download,
                    http = http,
                    destFile = rawAudioSource,
                    preferredAudioLangs = preferredAudioLangs,
                    bytesCounter = bytesCounter
                ) { frac, segDone ->
                    onProgress((frac * 88f).toInt().coerceIn(0, 88), bytesCounter.get(), 0L, segDone)
                }
            }

            StreamFormatSniffer.DetectedFormat.DASH_MANIFEST -> {
                val (finalUrl, xml) = http.fetchText(peek.finalUrl, targetHeaders)
                val dash = DashManifestResolver.parse(finalUrl, xml)
                    ?: throw IOException("DASH (.mpd) manifest ayrıştırılamadı")
                val audioPlan = DashManifestResolver.selectPreferredAudio(
                    manifest = dash,
                    preferredLanguages = preferredAudioLangs,
                    requestedKey = download.safeAudioTrackKey,
                    requestedIndex = download.audioTrackIndex,
                    requestedLang = download.safeAudioTrackLang,
                    requestedLabel = download.safeAudioTrackLabel
                ) ?: throw IOException("DASH manifest içinde ses akışı bulunamadı")

                downloadDashRepresentation(
                    plan = audioPlan,
                    extraHeaders = targetHeaders,
                    http = http,
                    destFile = rawAudioSource,
                    bytesCounter = bytesCounter
                ) { frac, segDone ->
                    onProgress((frac * 88f).toInt().coerceIn(0, 88), bytesCounter.get(), 0L, segDone)
                }
            }

            else -> {
                http.downloadProgressiveToFile(
                    url = peek.finalUrl,
                    destFile = rawAudioSource,
                    extraHeaders = targetHeaders
                ) { frac ->
                    val len = rawAudioSource.length()
                    bytesCounter.set(len)
                    onProgress(
                        (frac * 88f).toInt().coerceIn(0, 88),
                        len,
                        peek.contentLength.coerceAtLeast(0L),
                        0
                    )
                }
            }
        }

        onProgress(90, rawAudioSource.length(), 0L, 0)
        val extractedDir = File(workDir, "extracted_audio")
        val extraction = MediaTrackRemuxer.extractAudioToStandaloneFile(
            sourceFile = rawAudioSource,
            destDir = extractedDir,
            baseNameWithoutExt = "standalone_out",
            requestedAudioIndex = if (matchedExternal != null) 0 else download.audioTrackIndex,
            requestedLang = download.safeAudioTrackLang,
            requestedLabel = download.safeAudioTrackLabel,
            preferredLanguages = preferredAudioLangs
        )
        onProgress(96, extraction.outputFile.length(), 0L, 0)

        val resolvedLang = download.safeAudioTrackLang.ifBlank { matchedExternal?.safeLang.orEmpty() }
        val resolvedLabel = download.safeAudioTrackLabel.ifBlank { matchedExternal?.safeLabel.orEmpty() }
        val audioDir = DownloadExportNaming.publicAudioDir().also { it.mkdirs() }
        val finalName = DownloadExportNaming.audioExportFileName(
            animeTitle = download.animeTitle,
            episode = download.episode,
            lang = resolvedLang,
            label = resolvedLabel,
            extension = extraction.extension
        )
        val finalFile = File(audioDir, finalName)
        extraction.outputFile.copyTo(finalFile, overwrite = true)

        val audioMime = DownloadExportNaming.audioMimeType(extraction.extension)
        copyToSafDirectoryIfConfigured(
            safUriStr = safUriStr,
            sourceFile = finalFile,
            mimeType = audioMime,
            subfolderName = "Audio"
        )
        runCatching {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(finalFile.absolutePath),
                arrayOf(audioMime),
                null
            )
        }
        return finalFile
    }

    private suspend fun downloadHlsAudioStream(
        manifestUrl: String,
        extraHeaders: Map<String, String>,
        download: AnimeDownload,
        http: StreamDownloadHttpClient,
        destFile: File,
        preferredAudioLangs: List<String>,
        bytesCounter: AtomicLong,
        onProgress: (Float, Int) -> Unit
    ) {
        val (resolvedUrl, body) = http.fetchText(manifestUrl, extraHeaders)
        when (val playlist = HlsManifestResolver.parse(resolvedUrl, body)) {
            is HlsManifestResolver.ParsedPlaylist.Master -> {
                val bestVariant = HlsManifestResolver.selectBestVariant(playlist)
                val renditions = HlsManifestResolver.audioRenditionsForVariant(playlist, bestVariant)
                val selectedAudio = HlsManifestResolver.selectPreferredAudioRendition(
                    renditions = renditions,
                    preferredLanguages = preferredAudioLangs,
                    requestedKey = download.safeAudioTrackKey,
                    requestedIndex = download.audioTrackIndex,
                    requestedLang = download.safeAudioTrackLang,
                    requestedLabel = download.safeAudioTrackLabel
                )
                if (selectedAudio != null && selectedAudio.isSeparateStream && !selectedAudio.uri.isNullOrBlank()) {
                    downloadHlsMediaOrNestedPlaylist(
                        playlistUrl = selectedAudio.uri,
                        extraHeaders = extraHeaders,
                        http = http,
                        destFile = destFile,
                        isAudioStream = true,
                        bytesCounter = bytesCounter,
                        onProgress = onProgress
                    )
                } else {
                    // Gömülü seste en düşük bant genişlikli varyantı seç (ses aynı, veri 5-10 kat daha az!)
                    val lightestVariant = playlist.variants
                        .minByOrNull { if (it.bandwidth > 0L) it.bandwidth else Long.MAX_VALUE }
                        ?: bestVariant
                        ?: throw IOException("HLS master listede indirilebilir akış bulunamadı")
                    downloadHlsMediaOrNestedPlaylist(
                        playlistUrl = lightestVariant.url,
                        extraHeaders = extraHeaders,
                        http = http,
                        destFile = destFile,
                        isAudioStream = false,
                        bytesCounter = bytesCounter,
                        onProgress = onProgress
                    )
                }
            }

            is HlsManifestResolver.ParsedPlaylist.Media -> {
                downloadHlsMediaSegments(
                    media = playlist,
                    extraHeaders = extraHeaders,
                    http = http,
                    destFile = destFile,
                    isPackedAudioCandidate = true,
                    bytesCounter = bytesCounter,
                    onProgress = onProgress
                )
            }

            null -> throw IOException("HLS ses listesi ayrıştırılamadı")
        }
    }

    // ── 2) Video + Ses İndirme Akışı ─────────────────────────────────────────

    private suspend fun executeVideoWithAudioDownload(
        download: AnimeDownload,
        http: StreamDownloadHttpClient,
        workDir: File,
        preferredAudioLangs: List<String>,
        subtitleLangsCsv: String,
        safUriStr: String,
        bytesCounter: AtomicLong,
        onProgress: (Int, Long, Long, Int) -> Unit
    ): File {
        val safeAnimeId = download.animeId.ifBlank {
            download.animeTitle.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]"), "_")
                .trim('_')
                .take(30)
                .ifBlank { "media" }
        }
        val mediaId = "${safeAnimeId}_ep${download.episode}"
        val baseName = DownloadExportNaming.exportBaseName(download.animeTitle, download.episode)
        val videoDir = OfflinePlaybackHelper.getDownloadsDir(context)

        val rawVideoFile = File(workDir, "raw_video.bin")
        val downloadedExternalAudios = ArrayList<Pair<ExternalAudioTrack, File>>()

        val peek = http.peekStream(download.url)
        when (peek.detectedFormat) {
            StreamFormatSniffer.DetectedFormat.HTML_PAGE ->
                throw IOException("Sunucu video yerine HTML koruma/hata sayfası döndürdü")

            StreamFormatSniffer.DetectedFormat.HLS_MANIFEST -> {
                val (resolvedUrl, body) = http.fetchText(peek.finalUrl)
                val parsed = HlsManifestResolver.parse(resolvedUrl, body)
                    ?: throw IOException("HLS manifest ayrıştırılamadı")

                when (parsed) {
                    is HlsManifestResolver.ParsedPlaylist.Master -> {
                        val chosenVariant = HlsManifestResolver.selectBestVariant(
                            master = parsed,
                            preferredMaxHeight = download.qualityValue
                        ) ?: throw IOException("HLS master listede video varyantı bulunamadı")

                        val separateRenditions = HlsManifestResolver.audioRenditionsForVariant(parsed, chosenVariant)
                            .filter { it.isSeparateStream && !it.uri.equals(chosenVariant.url, ignoreCase = true) }

                        val vWeight = if (separateRenditions.isNotEmpty()) 0.72f else 0.88f
                        downloadHlsMediaOrNestedPlaylist(
                            playlistUrl = chosenVariant.url,
                            extraHeaders = emptyMap(),
                            http = http,
                            destFile = rawVideoFile,
                            isAudioStream = false,
                            bytesCounter = bytesCounter
                        ) { frac, segDone ->
                            onProgress((frac * vWeight * 100f).toInt().coerceIn(0, 88), bytesCounter.get(), 0L, segDone)
                        }

                        val videoHasEmbeddedAudio = MediaTrackRemuxer.probeTracks(rawVideoFile).any { it.isAudio }
                        val renditionsToDownload = selectAudioRenditionsForVideoDownload(
                            renditions = separateRenditions,
                            preferredLanguages = preferredAudioLangs,
                            videoAlreadyHasAudio = videoHasEmbeddedAudio
                        )
                        renditionsToDownload.forEachIndexed { idx, rend ->
                            val audioUrl = rend.uri ?: return@forEachIndexed
                            val audioFile = File(workDir, "hls_audio_$idx.bin")
                            val baseP = vWeight + (idx.toFloat() / renditionsToDownload.size.coerceAtLeast(1)) * 0.16f
                            val spanP = 0.16f / renditionsToDownload.size.coerceAtLeast(1)
                            downloadHlsMediaOrNestedPlaylist(
                                playlistUrl = audioUrl,
                                extraHeaders = emptyMap(),
                                http = http,
                                destFile = audioFile,
                                isAudioStream = true,
                                bytesCounter = bytesCounter
                            ) { frac, segDone ->
                                onProgress(((baseP + frac * spanP) * 100f).toInt().coerceIn(0, 88), bytesCounter.get(), 0L, segDone)
                            }
                            downloadedExternalAudios.add(
                                ExternalAudioTrack(
                                    url = audioUrl,
                                    label = rend.name,
                                    lang = rend.normalizedLang,
                                    headers = download.safeHeaders,
                                    channels = rend.channels,
                                    isDefault = rend.isDefault
                                ) to audioFile
                            )
                        }
                    }

                    is HlsManifestResolver.ParsedPlaylist.Media -> {
                        val hasExternal = download.safeExternalAudioTracks.isNotEmpty() ||
                            !download.safeHeaders[StreamDownloadHttpClient.META_MASTER_URL_HEADER].isNullOrBlank()
                        val vWeight = if (hasExternal) 0.72f else 0.88f

                        downloadHlsMediaSegments(
                            media = parsed,
                            extraHeaders = emptyMap(),
                            http = http,
                            destFile = rawVideoFile,
                            isPackedAudioCandidate = false,
                            bytesCounter = bytesCounter
                        ) { frac, segDone ->
                            onProgress((frac * vWeight * 100f).toInt().coerceIn(0, 88), bytesCounter.get(), 0L, segDone)
                        }

                        val videoHasAudio = MediaTrackRemuxer.probeTracks(rawVideoFile).any { it.isAudio }
                        val candidateTracks = resolveExternalAudioCandidatesForMediaPlaylist(
                            download = download,
                            http = http,
                            preferredLanguages = preferredAudioLangs,
                            videoAlreadyHasAudio = videoHasAudio
                        )
                        candidateTracks.forEachIndexed { idx, ext ->
                            val audioFile = File(workDir, "ext_hls_audio_$idx.bin")
                            val baseP = vWeight + (idx.toFloat() / candidateTracks.size.coerceAtLeast(1)) * 0.16f
                            val spanP = 0.16f / candidateTracks.size.coerceAtLeast(1)
                            downloadAnyAudioTrackToFile(ext, http, audioFile, bytesCounter) { frac ->
                                onProgress(((baseP + frac * spanP) * 100f).toInt().coerceIn(0, 88), bytesCounter.get(), 0L, 0)
                            }
                            downloadedExternalAudios.add(ext to audioFile)
                        }
                    }
                }
            }

            StreamFormatSniffer.DetectedFormat.DASH_MANIFEST -> {
                val (finalUrl, xml) = http.fetchText(peek.finalUrl)
                val dash = DashManifestResolver.parse(finalUrl, xml)
                    ?: throw IOException("DASH (.mpd) manifest ayrıştırılamadı")

                val bestVideo = DashManifestResolver.selectBestVideo(dash, download.qualityValue)
                    ?: throw IOException("DASH manifest içinde video akışı bulunamadı")
                val distinctAudios = DashManifestResolver.groupDistinctAudioTracks(
                    audioReps = dash.audioRepresentations,
                    preferredVideoMime = bestVideo.mimeType
                )
                val audiosToFetch = selectDashAudiosForVideoDownload(distinctAudios, preferredAudioLangs)

                val vWeight = if (audiosToFetch.isNotEmpty()) 0.72f else 0.88f
                downloadDashRepresentation(
                    plan = bestVideo,
                    extraHeaders = emptyMap(),
                    http = http,
                    destFile = rawVideoFile,
                    bytesCounter = bytesCounter
                ) { frac, segDone ->
                    onProgress((frac * vWeight * 100f).toInt().coerceIn(0, 88), bytesCounter.get(), 0L, segDone)
                }

                audiosToFetch.forEachIndexed { idx, audioPlan ->
                    val audioFile = File(workDir, "dash_audio_$idx.bin")
                    val baseP = vWeight + (idx.toFloat() / audiosToFetch.size.coerceAtLeast(1)) * 0.16f
                    val spanP = 0.16f / audiosToFetch.size.coerceAtLeast(1)
                    downloadDashRepresentation(
                        plan = audioPlan,
                        extraHeaders = emptyMap(),
                        http = http,
                        destFile = audioFile,
                        bytesCounter = bytesCounter
                    ) { frac, segDone ->
                        onProgress(((baseP + frac * spanP) * 100f).toInt().coerceIn(0, 88), bytesCounter.get(), 0L, segDone)
                    }
                    downloadedExternalAudios.add(
                        ExternalAudioTrack(
                            url = finalUrl,
                            label = audioPlan.label,
                            lang = audioPlan.normalizedLang,
                            codecs = audioPlan.codecs,
                            channels = audioPlan.channels,
                            isDefault = idx == 0
                        ) to audioFile
                    )
                }
            }

            else -> {
                val extTracks = selectExternalTracksForVideoDownload(
                    tracks = download.safeExternalAudioTracks,
                    preferredLanguages = preferredAudioLangs
                )
                val vWeight = if (extTracks.isNotEmpty()) 0.74f else 0.88f
                http.downloadProgressiveToFile(
                    url = peek.finalUrl,
                    destFile = rawVideoFile
                ) { frac ->
                    val len = rawVideoFile.length()
                    bytesCounter.set(len)
                    onProgress(
                        (frac * vWeight * 100f).toInt().coerceIn(0, 88),
                        len,
                        peek.contentLength.coerceAtLeast(0L),
                        0
                    )
                }
                extTracks.forEachIndexed { idx, ext ->
                    val audioFile = File(workDir, "prog_ext_audio_$idx.bin")
                    val baseP = vWeight + (idx.toFloat() / extTracks.size.coerceAtLeast(1)) * 0.14f
                    val spanP = 0.14f / extTracks.size.coerceAtLeast(1)
                    downloadAnyAudioTrackToFile(ext, http, audioFile, bytesCounter) { frac ->
                        onProgress(((baseP + frac * spanP) * 100f).toInt().coerceIn(0, 88), bytesCounter.get(), 0L, 0)
                    }
                    downloadedExternalAudios.add(ext to audioFile)
                }
            }
        }

        onProgress(90, bytesCounter.get(), 0L, 0)
        val remuxResult = MediaTrackRemuxer.remuxVideoWithAudios(
            rawVideoFile = rawVideoFile,
            externalAudioInputs = downloadedExternalAudios,
            workDir = workDir,
            preferredLanguages = preferredAudioLangs
        )
        onProgress(95, remuxResult.outputFile.length(), 0L, 0)

        // Eski uzantılı kopya varsa temizle ve yeni video dosyasını hedefe taşı/kopyala
        DownloadExportNaming.VIDEO_EXTENSIONS.forEach { oldExt ->
            val oldCandidate = File(videoDir, "$baseName.$oldExt")
            if (oldCandidate.exists()) runCatching { oldCandidate.delete() }
        }
        val outFilename = DownloadExportNaming.exportFileName(
            animeTitle = download.animeTitle,
            episode = download.episode,
            extension = remuxResult.extension
        )
        val localFile = File(videoDir, outFilename)
        remuxResult.outputFile.copyTo(localFile, overwrite = true)

        // Yan (companion) ses dosyalarını videonun yanına yaz (çok dilli veya E-AC-3 vb. sesler için)
        val savedCompanionAudios = ArrayList<File>()
        val usedCompanionKeys = HashSet<String>()
        remuxResult.companionAudioFiles.forEachIndexed { idx, comp ->
            var langKey = comp.lang.ifBlank { "track${idx + 1}" }
            if (!usedCompanionKeys.add(langKey.lowercase(Locale.ROOT))) {
                langKey = "${langKey}_${idx + 1}"
                usedCompanionKeys.add(langKey.lowercase(Locale.ROOT))
            }
            val compName = DownloadExportNaming.companionAudioFileName(
                baseName = baseName,
                lang = langKey,
                name = comp.label,
                extension = comp.extension
            )
            val compDest = File(videoDir, compName)
            runCatching {
                comp.file.copyTo(compDest, overwrite = true)
                savedCompanionAudios.add(compDest)
            }.onFailure { e ->
                Log.w(TAG, "Yan ses dosyası yazılamadı ($compName): ${e.message}")
            }
        }

        // Altyazıları çöz ve videonun yanına kaydet (hem kaynağın hem OpenSubtitles/eklentilerin altyazıları)
        val savedSubs = resolveAndDownloadSubtitles(
            download = download,
            baseName = baseName,
            videoDir = videoDir,
            subtitleLangsCsv = subtitleLangsCsv,
            http = http
        )

        // Metadata (.json) — OfflinePlaybackHelper listesinin videoyu tanıması için
        val metaFile = File(videoDir, DownloadExportNaming.metadataFileName(baseName))
        val metaJson = JSONObject().apply {
            put("mediaId", mediaId)
            put("title", "${download.animeTitle} - Bölüm ${download.episode}")
            put("durationMs", 0)
            put("downloadedAtMs", System.currentTimeMillis())
        }
        metaFile.writeText(metaJson.toString(2))

        // Poster (.jpg)
        val posterFile = File(
            DownloadExportNaming.publicImagesDir().also { it.mkdirs() },
            DownloadExportNaming.posterFileName(baseName)
        )
        downloadPosterIfAvailable(download.posterUrl, posterFile)

        // Özel (SAF) klasör seçiliyse video + yan ses + yan altyazıları oraya da kopyala
        val videoMime = DownloadExportNaming.videoMimeType(remuxResult.extension)
        copyToSafDirectoryIfConfigured(
            safUriStr = safUriStr,
            sourceFile = localFile,
            mimeType = videoMime,
            subfolderName = null
        )
        savedCompanionAudios.forEach { compFile ->
            copyToSafDirectoryIfConfigured(
                safUriStr = safUriStr,
                sourceFile = compFile,
                mimeType = DownloadExportNaming.audioMimeType(compFile.extension),
                subfolderName = null
            )
        }
        savedSubs.forEach { subFile ->
            val subMime = when (subFile.extension.lowercase(Locale.ROOT)) {
                "ass", "ssa" -> "text/x-ass"
                "vtt" -> "text/vtt"
                else -> "application/x-subrip"
            }
            copyToSafDirectoryIfConfigured(
                safUriStr = safUriStr,
                sourceFile = subFile,
                mimeType = subMime,
                subfolderName = null
            )
        }

        val toScan = buildList {
            add(localFile)
            addAll(savedCompanionAudios)
            addAll(savedSubs)
            if (posterFile.isFile) add(posterFile)
        }.map { it.absolutePath }.toTypedArray()
        runCatching { MediaScannerConnection.scanFile(context, toScan, null, null) }

        return localFile
    }

    // ── HLS & DASH Segment Aktarımı ──────────────────────────────────────────

    private suspend fun downloadHlsMediaOrNestedPlaylist(
        playlistUrl: String,
        extraHeaders: Map<String, String>,
        http: StreamDownloadHttpClient,
        destFile: File,
        isAudioStream: Boolean,
        bytesCounter: AtomicLong,
        depth: Int = 0,
        onProgress: (Float, Int) -> Unit
    ) {
        if (depth > 3) throw IOException("Çok derin iç içe HLS yönlendirmesi")
        val (resolvedUrl, text) = http.fetchText(playlistUrl, extraHeaders)
        when (val parsed = HlsManifestResolver.parse(resolvedUrl, text)) {
            is HlsManifestResolver.ParsedPlaylist.Master -> {
                val nextUrl = HlsManifestResolver.selectBestVariant(parsed)?.url
                    ?: parsed.audioRenditions.firstOrNull { !it.uri.isNullOrBlank() }?.uri
                    ?: throw IOException("İç HLS listede medya akışı bulunamadı")
                downloadHlsMediaOrNestedPlaylist(
                    playlistUrl = nextUrl,
                    extraHeaders = extraHeaders,
                    http = http,
                    destFile = destFile,
                    isAudioStream = isAudioStream,
                    bytesCounter = bytesCounter,
                    depth = depth + 1,
                    onProgress = onProgress
                )
            }

            is HlsManifestResolver.ParsedPlaylist.Media -> {
                downloadHlsMediaSegments(
                    media = parsed,
                    extraHeaders = extraHeaders,
                    http = http,
                    destFile = destFile,
                    isPackedAudioCandidate = isAudioStream,
                    bytesCounter = bytesCounter,
                    onProgress = onProgress
                )
            }

            null -> throw IOException("Alt HLS medya listesi ayrıştırılamadı")
        }
    }

    private suspend fun downloadHlsMediaSegments(
        media: HlsManifestResolver.ParsedPlaylist.Media,
        extraHeaders: Map<String, String>,
        http: StreamDownloadHttpClient,
        destFile: File,
        isPackedAudioCandidate: Boolean,
        bytesCounter: AtomicLong,
        onProgress: (Float, Int) -> Unit
    ) {
        if (media.segments.isEmpty()) {
            throw IOException("HLS medya listesinde hiç segment yok")
        }
        destFile.parentFile?.mkdirs()

        for (seg in media.segments) {
            val enc = seg.encryption
            if (enc != null && enc.isEncrypted && !enc.isAes128) {
                throw IOException("DRM / ${enc.method} şifreli akışlar çevrimdışı indirilemiyor")
            }
        }

        val keyCache = HashMap<String, ByteArray>()
        val initCache = HashMap<String, ByteArray>()
        val total = media.segments.size
        var completed = 0
        var lastWrittenInitKey: String? = null
        var lastEmitMs = 0L

        val sampleUrl = media.segments.firstOrNull()?.url.orEmpty()
        val policy = HlsPolicySelector.forUrl(sampleUrl)
        val concurrency = if (policy.minRequestIntervalMs > 100L) 1 else SEGMENT_CONCURRENCY
        val gate = Semaphore(concurrency)

        BufferedOutputStream(FileOutputStream(destFile, false), 128 * 1024).use { out ->
            for (batch in media.segments.chunked(SEGMENT_BATCH_SIZE)) {
                currentCoroutineContext().ensureActive()

                for (seg in batch) {
                    val enc = seg.encryption
                    if (enc != null && enc.isAes128 && !enc.keyUrl.isNullOrBlank() && !keyCache.containsKey(enc.keyUrl)) {
                        keyCache[enc.keyUrl] = http.fetchBytes(enc.keyUrl, extraHeaders)
                    }
                    val init = seg.initSegment
                    if (init != null) {
                        val initEnc = init.encryption
                        if (initEnc != null && initEnc.isAes128 && !initEnc.keyUrl.isNullOrBlank() && !keyCache.containsKey(initEnc.keyUrl)) {
                            keyCache[initEnc.keyUrl] = http.fetchBytes(initEnc.keyUrl, extraHeaders)
                        }
                        val cacheId = "${init.url}|${init.byteRange?.offset ?: -1}|${init.byteRange?.length ?: -1}"
                        if (!initCache.containsKey(cacheId)) {
                            var initBytes = http.fetchBytes(init.url, extraHeaders, init.byteRange)
                            if (initEnc != null && initEnc.isAes128 && !initEnc.keyUrl.isNullOrBlank()) {
                                initBytes = HlsManifestResolver.decryptAes128(
                                    initBytes,
                                    keyCache[initEnc.keyUrl]!!,
                                    initEnc.resolveIv(init.mediaSequence)
                                )
                            }
                            initCache[cacheId] = initBytes
                        }
                    }
                }

                val fetchedBatch = coroutineScope {
                    batch.map { seg ->
                        async {
                            gate.withPermit {
                                if (policy.minRequestIntervalMs > 0L) {
                                    delay(policy.minRequestIntervalMs)
                                }
                                var data = http.fetchBytes(seg.url, extraHeaders, seg.byteRange)
                                val enc = seg.encryption
                                if (enc != null && enc.isAes128 && !enc.keyUrl.isNullOrBlank()) {
                                    val k = keyCache[enc.keyUrl]
                                        ?: throw IOException("AES-128 anahtarı alınamadı")
                                    data = HlsManifestResolver.decryptAes128(
                                        cipherBytes = data,
                                        keyBytes = k,
                                        ivBytes = enc.resolveIv(seg.sequenceNumber)
                                    )
                                }
                                data = StreamFormatSniffer.sanitizeDisguisedTsSegment(data)
                                if (seg.initSegment == null && (isPackedAudioCandidate ||
                                        (data.size >= 10 &&
                                            data[0] == 'I'.code.toByte() &&
                                            data[1] == 'D'.code.toByte() &&
                                            data[2] == '3'.code.toByte()))
                                ) {
                                    data = StreamFormatSniffer.stripLeadingId3Tags(data)
                                }
                                seg to data
                            }
                        }
                    }.awaitAll()
                }

                for ((seg, bytes) in fetchedBatch) {
                    val init = seg.initSegment
                    if (init != null) {
                        val cacheId = "${init.url}|${init.byteRange?.offset ?: -1}|${init.byteRange?.length ?: -1}"
                        if (cacheId != lastWrittenInitKey) {
                            initCache[cacheId]?.let {
                                out.write(it)
                                bytesCounter.addAndGet(it.size.toLong())
                            }
                            lastWrittenInitKey = cacheId
                        }
                    }
                    out.write(bytes)
                    bytesCounter.addAndGet(bytes.size.toLong())
                    completed++
                    val now = SystemClock.elapsedRealtime()
                    if (completed == total || now - lastEmitMs >= 150L) {
                        lastEmitMs = now
                        onProgress(completed.toFloat() / total.toFloat(), completed)
                    }
                }
            }
            out.flush()
        }
    }

    private suspend fun downloadDashRepresentation(
        plan: DashManifestResolver.DashRepresentationPlan,
        extraHeaders: Map<String, String>,
        http: StreamDownloadHttpClient,
        destFile: File,
        bytesCounter: AtomicLong,
        onProgress: (Float, Int) -> Unit
    ) {
        destFile.parentFile?.mkdirs()

        if (plan.initSegment == null && plan.mediaSegments.size == 1 && plan.mediaSegments[0].byteRange == null) {
            val baseCount = bytesCounter.get()
            http.downloadProgressiveToFile(
                url = plan.mediaSegments[0].url,
                destFile = destFile,
                extraHeaders = extraHeaders
            ) { frac ->
                bytesCounter.set(baseCount + destFile.length())
                onProgress(frac, 1)
            }
            return
        }

        val total = plan.mediaSegments.size.coerceAtLeast(1)
        var completed = 0
        var lastEmitMs = 0L

        BufferedOutputStream(FileOutputStream(destFile, false), 128 * 1024).use { out ->
            plan.initSegment?.let { init ->
                val initBytes = http.fetchBytes(init.url, extraHeaders, init.byteRange)
                out.write(initBytes)
                bytesCounter.addAndGet(initBytes.size.toLong())
            }

            val gate = Semaphore(SEGMENT_CONCURRENCY)
            for (batch in plan.mediaSegments.chunked(SEGMENT_BATCH_SIZE)) {
                currentCoroutineContext().ensureActive()
                val fetched = coroutineScope {
                    batch.map { seg ->
                        async {
                            gate.withPermit {
                                http.fetchBytes(seg.url, extraHeaders, seg.byteRange)
                            }
                        }
                    }.awaitAll()
                }
                for (bytes in fetched) {
                    out.write(bytes)
                    bytesCounter.addAndGet(bytes.size.toLong())
                    completed++
                    val now = SystemClock.elapsedRealtime()
                    if (completed == total || now - lastEmitMs >= 150L) {
                        lastEmitMs = now
                        onProgress(completed.toFloat() / total.toFloat(), completed)
                    }
                }
            }
            out.flush()
        }
    }

    private suspend fun downloadAnyAudioTrackToFile(
        track: ExternalAudioTrack,
        http: StreamDownloadHttpClient,
        destFile: File,
        bytesCounter: AtomicLong,
        onProgress: (Float) -> Unit
    ) {
        val headers = track.httpHeaders()
        val peek = http.peekStream(track.url, headers)
        when (peek.detectedFormat) {
            StreamFormatSniffer.DetectedFormat.HLS_MANIFEST -> {
                downloadHlsMediaOrNestedPlaylist(
                    playlistUrl = peek.finalUrl,
                    extraHeaders = headers,
                    http = http,
                    destFile = destFile,
                    isAudioStream = true,
                    bytesCounter = bytesCounter
                ) { frac, _ -> onProgress(frac) }
            }

            StreamFormatSniffer.DetectedFormat.DASH_MANIFEST -> {
                val (finalUrl, xml) = http.fetchText(peek.finalUrl, headers)
                val dash = DashManifestResolver.parse(finalUrl, xml)
                    ?: throw IOException("Harici DASH ses manifesti ayrıştırılamadı")
                val audioPlan = DashManifestResolver.selectPreferredAudio(
                    manifest = dash,
                    requestedLang = track.safeLang,
                    requestedLabel = track.safeLabel
                ) ?: throw IOException("Harici DASH ses akışı bulunamadı")
                downloadDashRepresentation(audioPlan, headers, http, destFile, bytesCounter) { frac, _ ->
                    onProgress(frac)
                }
            }

            StreamFormatSniffer.DetectedFormat.HTML_PAGE ->
                throw IOException("Harici ses URL'si HTML hata sayfası döndürdü")

            else -> {
                val startBytes = bytesCounter.get()
                http.downloadProgressiveToFile(
                    url = peek.finalUrl,
                    destFile = destFile,
                    extraHeaders = headers
                ) { frac ->
                    bytesCounter.set(startBytes + destFile.length())
                    onProgress(frac)
                }
            }
        }
    }

    // ── Yardımcılar: Ses Seçimi, Altyazı, Poster, SAF ve Harici İndirici ────

    private fun pickMatchingExternalTrack(download: AnimeDownload): ExternalAudioTrack? {
        val tracks = download.safeExternalAudioTracks
        if (tracks.isEmpty()) return null
        if (download.audioTrackIndex in tracks.indices) {
            return tracks[download.audioTrackIndex]
        }
        val reqLang = ExternalAudioTrack.normalizeLangCode(download.safeAudioTrackLang)
        if (reqLang.isNotBlank()) {
            tracks.firstOrNull {
                ExternalAudioTrack.normalizeLangCode(it.safeLang) == reqLang
            }?.let { return it }
        }
        if (download.safeAudioTrackLabel.isNotBlank()) {
            tracks.firstOrNull {
                it.safeLabel.equals(download.safeAudioTrackLabel, ignoreCase = true)
            }?.let { return it }
        }
        return tracks.firstOrNull()
    }

    private fun selectAudioRenditionsForVideoDownload(
        renditions: List<HlsManifestResolver.AudioRendition>,
        preferredLanguages: List<String>,
        videoAlreadyHasAudio: Boolean
    ): List<HlsManifestResolver.AudioRendition> {
        if (renditions.isEmpty()) return emptyList()
        val primary = HlsManifestResolver.selectPreferredAudioRendition(
            renditions = renditions,
            preferredLanguages = preferredLanguages
        ) ?: return emptyList()

        if (videoAlreadyHasAudio && renditions.size == 1) {
            return emptyList()
        }

        val selected = ArrayList<HlsManifestResolver.AudioRendition>()
        selected.add(primary)
        renditions.firstOrNull { other ->
            other.uri != primary.uri &&
                other.normalizedLang.isNotBlank() &&
                other.normalizedLang != primary.normalizedLang
        }?.let { selected.add(it) }
        return selected
    }

    private fun selectDashAudiosForVideoDownload(
        distinctAudios: List<DashManifestResolver.DashRepresentationPlan>,
        preferredLanguages: List<String>
    ): List<DashManifestResolver.DashRepresentationPlan> {
        if (distinctAudios.isEmpty()) return emptyList()
        return distinctAudios.sortedWith(
            compareBy<DashManifestResolver.DashRepresentationPlan> { rep ->
                val idx = preferredLanguages
                    .map { ExternalAudioTrack.normalizeLangCode(it) }
                    .indexOf(rep.normalizedLang)
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenByDescending { it.bandwidth }
        ).take(2)
    }

    private fun selectExternalTracksForVideoDownload(
        tracks: List<ExternalAudioTrack>,
        preferredLanguages: List<String>
    ): List<ExternalAudioTrack> {
        if (tracks.isEmpty()) return emptyList()
        return tracks.sortedBy { ext ->
            val norm = ExternalAudioTrack.normalizeLangCode(
                ext.safeLang.ifBlank { ExternalAudioTrack.inferLanguageFromText("${ext.safeLabel} ${ext.url}") }
            )
            val idx = preferredLanguages
                .map { ExternalAudioTrack.normalizeLangCode(it) }
                .indexOf(norm)
            if (idx >= 0) idx else Int.MAX_VALUE
        }.take(2)
    }

    private suspend fun resolveExternalAudioCandidatesForMediaPlaylist(
        download: AnimeDownload,
        http: StreamDownloadHttpClient,
        preferredLanguages: List<String>,
        videoAlreadyHasAudio: Boolean
    ): List<ExternalAudioTrack> {
        if (download.safeExternalAudioTracks.isNotEmpty()) {
            return selectExternalTracksForVideoDownload(download.safeExternalAudioTracks, preferredLanguages)
        }
        val masterHint = download.safeHeaders[StreamDownloadHttpClient.META_MASTER_URL_HEADER]
            ?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()

        return runCatching {
            val (finalMaster, text) = http.fetchText(masterHint)
            val master = HlsManifestResolver.parse(finalMaster, text) as? HlsManifestResolver.ParsedPlaylist.Master
                ?: return emptyList()
            val renditions = master.audioRenditions.filter { it.isSeparateStream }
            selectAudioRenditionsForVideoDownload(renditions, preferredLanguages, videoAlreadyHasAudio)
                .mapNotNull { r ->
                    val uri = r.uri ?: return@mapNotNull null
                    ExternalAudioTrack(
                        url = uri,
                        label = r.name,
                        lang = r.normalizedLang,
                        headers = download.safeHeaders,
                        channels = r.channels,
                        isDefault = r.isDefault
                    )
                }
        }.getOrDefault(emptyList())
    }

    private suspend fun resolveAndDownloadSubtitles(
        download: AnimeDownload,
        baseName: String,
        videoDir: File,
        subtitleLangsCsv: String,
        http: StreamDownloadHttpClient
    ): List<File> {
        val allResolvedSubtitles = mutableListOf<SubtitleInput>()
        allResolvedSubtitles.addAll(download.safeSubtitles)

        try {
            val dMalId = download.malId
            val dAniListId = download.aniListId
            val dTmdbId = download.tmdbId
                ?: if (dMalId == null && dAniListId == null) download.animeId.toIntOrNull() else null

            if (dMalId != null || dAniListId != null || dTmdbId != null) {
                val resolvedIds = KitsugiIdResolver.resolveIds(
                    malId = dMalId,
                    aniListId = dAniListId,
                    tmdbId = dTmdbId
                )
                val imdbId = resolvedIds.imdbId
                val kitsuId = resolvedIds.kitsuId
                val isMovieType = download.season == 0 && download.episode <= 1
                val type = if (isMovieType) "movie" else "series"
                val queryIds = mutableListOf<String>()
                if (!imdbId.isNullOrBlank()) {
                    queryIds.add(if (isMovieType) imdbId else "$imdbId:${download.season}:${download.episode}")
                }
                if (kitsuId != null) {
                    queryIds.add(if (isMovieType) "kitsu:$kitsuId" else "kitsu:$kitsuId:${download.episode}")
                }
                if (queryIds.isNotEmpty()) {
                    val guessedFilename = runCatching {
                        val lastSeg = Uri.parse(download.url).lastPathSegment
                        if (!lastSeg.isNullOrBlank() && lastSeg.contains(".")) lastSeg else null
                    }.getOrNull()?.substringBefore("\n")?.substringBefore("\r")?.trim()

                    val subRepo = SubtitleRepositoryImpl(context)
                    val remoteSubs = mutableListOf<com.kitsugi.animelist.core.player.model.Subtitle>()
                    for (queryId in queryIds) {
                        runCatching {
                            val results = subRepo.getSubtitles(
                                type = type,
                                id = queryId,
                                videoUrl = download.url,
                                videoHeaders = download.safeHeaders,
                                filename = guessedFilename,
                                aniListId = dAniListId,
                                animeTitle = download.animeTitle,
                                episode = download.episode
                            )
                            remoteSubs.addAll(
                                results.map {
                                    com.kitsugi.animelist.core.player.model.Subtitle(
                                        id = it.id,
                                        url = it.url,
                                        lang = it.lang,
                                        addonName = it.addonName,
                                        addonLogo = it.addonLogo,
                                        format = it.format
                                    )
                                }
                            )
                        }
                    }
                    for (sub in remoteSubs.distinctBy { it.url }) {
                        val friendly = PlayerSubtitleUtils.getFriendlyLanguageName(sub.lang)
                        allResolvedSubtitles.add(
                            SubtitleInput(
                                url = sub.url,
                                name = "$friendly (${sub.addonName})",
                                lang = sub.lang
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Harici altyazı çözümlemesi atlandı: ${e.message}")
        }

        val dedupedSubs = allResolvedSubtitles.distinctBy { it.url }
        val dlLangs = subtitleLangsCsv.split(',')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

        val filteredSubs = if (dlLangs.isEmpty()) {
            dedupedSubs
        } else {
            dedupedSubs.filter { sub ->
                dlLangs.any { targetLang ->
                    PlayerSubtitleUtils.matchesLanguageCode(sub.lang, targetLang)
                }
            }
        }

        val savedSubs = mutableListOf<File>()
        for (subtitle in filteredSubs) {
            val extension = when {
                subtitle.url.contains(".vtt", ignoreCase = true) -> "vtt"
                subtitle.url.contains(".ass", ignoreCase = true) -> "ass"
                subtitle.url.contains(".ssa", ignoreCase = true) -> "ssa"
                else -> "srt"
            }
            val subFile = File(
                videoDir,
                DownloadExportNaming.subtitleFileName(baseName, subtitle.lang, subtitle.name, extension)
            )
            runCatching {
                val subHost = runCatching { java.net.URI(subtitle.url).host }.getOrNull()
                val videoHost = runCatching { java.net.URI(download.url).host }.getOrNull()
                val extra = if (subHost != null && subHost.equals(videoHost, ignoreCase = true)) {
                    download.safeHeaders
                } else emptyMap()
                val bytes = http.fetchBytes(subtitle.url, extra)
                if (bytes.isNotEmpty()) {
                    subFile.writeBytes(bytes)
                    savedSubs.add(subFile)
                }
            }.onFailure {
                runCatching { subFile.delete() }
            }
        }
        return savedSubs
    }

    private fun copyToSafDirectoryIfConfigured(
        safUriStr: String,
        sourceFile: File,
        mimeType: String,
        subfolderName: String?
    ) {
        if (safUriStr.isBlank()) return
        runCatching {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(safUriStr)) ?: return
            if (!rootDoc.exists()) return
            val targetDocDir = if (!subfolderName.isNullOrBlank()) {
                rootDoc.findFile(subfolderName) ?: rootDoc.createDirectory(subfolderName) ?: rootDoc
            } else {
                rootDoc
            }
            targetDocDir.findFile(sourceFile.name)?.delete()
            val newDoc = targetDocDir.createFile(mimeType, sourceFile.name) ?: return
            context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
                sourceFile.inputStream().use { inp -> inp.copyTo(out) }
            }
        }.onFailure { e ->
            Log.w(TAG, "SAF kopyalama hatası (${sourceFile.name}): ${e.message}")
        }
    }

    private fun downloadPosterIfAvailable(url: String?, destFile: File) {
        if (url.isNullOrBlank() || !url.startsWith("http", ignoreCase = true)) return
        if (destFile.isFile && destFile.length() > 0L) return
        runCatching {
            val request = Request.Builder().url(url).build()
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body ?: return
                body.byteStream().use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun downloadExternal(
        download: AnimeDownload,
        downloaderPref: String,
        onStatusChanged: (AnimeDownload.Status, String?, String?) -> Unit
    ) {
        val pm = context.packageManager
        val cleanTitle = download.animeTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val filename = KitsugiStrings.get(R.string.x_bolum_x_mp4, cleanTitle, download.episode)

        try {
            val intent: Intent = when (downloaderPref) {
                "EXTERNAL_1DM" -> {
                    val pkgName = if (isPackageInstalled(pm, "idm.internet.download.manager.plus")) {
                        "idm.internet.download.manager.plus"
                    } else {
                        "idm.internet.download.manager"
                    }
                    if (!isPackageInstalled(pm, pkgName)) throw IOException("1DM kurulu değil")
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(download.url)
                        setPackage(pkgName)
                        putExtra("extra_filename", filename)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }

                "EXTERNAL_ADM" -> {
                    val pkgName = if (isPackageInstalled(pm, "com.dv.adm.pay")) {
                        "com.dv.adm.pay"
                    } else {
                        "com.dv.adm"
                    }
                    if (!isPackageInstalled(pm, pkgName)) throw IOException("ADM kurulu değil")
                    Intent(Intent.ACTION_VIEW).apply {
                        component = ComponentName(pkgName, "$pkgName.AEditor")
                        putExtra("com.dv.get.ACTION_LIST_ADD", "${download.url}<info>$filename")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }

                else -> {
                    Intent(Intent.ACTION_VIEW).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setDataAndType(Uri.parse(download.url), "video/*")
                        putExtra("extra_filename", filename)
                    }
                }
            }
            context.startActivity(intent)
            onStatusChanged(AnimeDownload.Status.COMPLETED, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Harici indirici başlatılamadı", e)
            onStatusChanged(AnimeDownload.Status.ERROR, null, e.message)
        }
    }

    private fun isPackageInstalled(pm: android.content.pm.PackageManager, pkgName: String): Boolean =
        try {
            pm.getPackageInfo(pkgName, 0)
            true
        } catch (_: Exception) {
            false
        }

    // ── Küçük video (klip) indirme ────────────────────────────────────────────

    /**
     * Detay sayfasındaki küçük videoyu indirir.
     *
     * Tasarım kuralları:
     *  - Kaynak **önce çözümlenir**; indirilebilir akış yoksa hiç dosya oluşturulmaz.
     *  - Yazma her zaman `<ad>.part` dosyasına yapılır; yalnızca başarılı bitişte son ada
     *    taşınır. Böylece yarıda kalan indirme "indirilmiş video" gibi görünmez.
     *  - Aynı video ikinci kez istenirse: dosya zaten varsa ağa hiç çıkılmaz (COMPLETED),
     *    aksi hâlde üzerine yazılır — çift dosya (`_1`, `_11`…) oluşmaz.
     *  - İptal/hata durumunda `.part` dosyası silinir; kullanıcıya net hata mesajı döner.
     */
    private suspend fun downloadClip(
        download: AnimeDownload,
        onProgress: (Int, Long, Long, Int) -> Unit,
        onStatusChanged: (AnimeDownload.Status, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        onStatusChanged(AnimeDownload.Status.DOWNLOADING, null)

        val clipDir = DownloadExportNaming.clipDir(context)
        val baseName = download.clipBaseName?.takeIf { it.isNotBlank() }
            ?: DownloadExportNaming.clipBaseName(
                mediaTitle = download.animeTitle,
                clipTitle = download.clipTitle ?: "Video",
                index = 1
            )

        // Daha önce tamamlanmış (kaydı kaybolmuş olabilecek) bir kopya var mı?
        findExistingClipFile(clipDir, baseName)?.let { existing ->
            if (existing.isFile && existing.length() > 0) {
                MediaScannerConnection.scanFile(context, arrayOf(existing.absolutePath), null, null)
                onStatusChanged(AnimeDownload.Status.COMPLETED, existing.absolutePath)
                return@withContext
            }
            runCatching { existing.delete() }
        }

        if (!ClipDownloadSource.hasFreeSpace(clipDir, 0L)) {
            onStatusChanged(
                AnimeDownload.Status.ERROR,
                KitsugiStrings.get(R.string.kitsugi_clip_error_no_space)
            )
            return@withContext
        }

        val resolved = when (val result = ClipDownloadSource.resolve(download.url)) {
            is ClipDownloadSource.Result.Error -> {
                onStatusChanged(
                    AnimeDownload.Status.ERROR,
                    clipFailureMessage(result)
                )
                return@withContext
            }
            is ClipDownloadSource.Result.Success -> result.resolved
        }

        val fileName = DownloadExportNaming.clipFileName(baseName, resolved.extension)
        val target = File(clipDir, fileName)
        val partFile = File(clipDir, "$fileName.part")

        try {
            if (resolved.isHls) {
                downloadHlsClip(resolved.url, partFile, download.requestHeaders, onProgress)
            } else {
                downloadClipFile(resolved.url, partFile, download.requestHeaders, onProgress)
            }

            if (!partFile.isFile || partFile.length() <= 0L) {
                runCatching { partFile.delete() }
                onStatusChanged(
                    AnimeDownload.Status.ERROR,
                    KitsugiStrings.get(R.string.kitsugi_clip_error_empty)
                )
                return@withContext
            }

            // Atomik bitiş: önce eski hedefi kaldır, sonra part → son ad.
            runCatching { if (target.exists()) target.delete() }
            val moved = runCatching { partFile.renameTo(target) }.getOrDefault(false)
            if (!moved) {
                // renameTo aynı birimde başarısız olduysa kopyala-sil yedeği.
                runCatching {
                    partFile.inputStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                    }
                }.onFailure {
                    runCatching { target.delete() }
                }
                runCatching { partFile.delete() }
                if (!target.isFile || target.length() <= 0L) {
                    runCatching { target.delete() }
                    onStatusChanged(
                        AnimeDownload.Status.ERROR,
                        KitsugiStrings.get(R.string.kitsugi_clip_error_generic)
                    )
                    return@withContext
                }
            }

            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
            onStatusChanged(AnimeDownload.Status.COMPLETED, target.absolutePath)
        } catch (e: kotlinx.coroutines.CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { partFile.delete() }
            }
            onStatusChanged(AnimeDownload.Status.PAUSED, null)
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("AnimeDownloader", "Klip indirme hatası (${download.clipKey})", e)
            runCatching { partFile.delete() }
            val reason = when {
                e is java.io.IOException && e !is ClipHttpException -> {
                    // Yazma sırasında disk dolduysa "ağ hatası" demek yanıltıcı olur.
                    if (!ClipDownloadSource.hasFreeSpace(clipDir, 0L)) {
                        KitsugiStrings.get(R.string.kitsugi_clip_error_no_space)
                    } else {
                        KitsugiStrings.get(R.string.kitsugi_clip_error_network)
                    }
                }
                else -> e.message?.takeIf { it.isNotBlank() }
                    ?.let { KitsugiStrings.get(R.string.kitsugi_clip_error_failed, it) }
                    ?: KitsugiStrings.get(R.string.kitsugi_clip_error_generic)
            }
            onStatusChanged(AnimeDownload.Status.ERROR, reason)
        }
    }

    /** Aynı taban ada sahip mevcut klip dosyası (uzantı ne olursa olsun). */
    private fun findExistingClipFile(dir: File, baseName: String): File? {
        if (!dir.isDirectory) return null
        val videoExts = setOf("mp4", "webm", "m4v", "mkv", "ts", "mov")
        return runCatching {
            dir.listFiles()?.firstOrNull { file ->
                file.isFile &&
                    file.name.startsWith("$baseName.", ignoreCase = true) &&
                    file.extension.lowercase() in videoExts
            }
        }.getOrNull()
    }

    private fun clipFailureMessage(error: ClipDownloadSource.Result.Error): String = when (error.failure) {
        ClipDownloadSource.Failure.UNSUPPORTED_URL ->
            KitsugiStrings.get(R.string.kitsugi_clip_error_unsupported)
        ClipDownloadSource.Failure.NO_STREAM ->
            KitsugiStrings.get(R.string.kitsugi_clip_error_no_stream)
        ClipDownloadSource.Failure.SEPARATE_AUDIO ->
            KitsugiStrings.get(R.string.kitsugi_clip_error_separate_audio)
        ClipDownloadSource.Failure.NETWORK ->
            KitsugiStrings.get(R.string.kitsugi_clip_error_network)
    }

    /** Progressive (tek dosya) klip indirmesi — yarıda kalırsa `.part` ile devam eder. */
    private suspend fun downloadClipFile(
        url: String,
        partFile: File,
        headers: Map<String, String>,
        onProgress: (Int, Long, Long, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                val existingLength = if (partFile.exists()) partFile.length() else 0L
                val requestBuilder = Request.Builder().url(url)
                headers.forEach { (key, value) -> requestBuilder.header(key, value) }
                if (existingLength > 0L) requestBuilder.header("Range", "bytes=$existingLength-")

                KitsugiHttpClient.client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 416 && existingLength > 0L) {
                        // Sunucu devam aralığını kabul etmiyor → baştan indir.
                        runCatching { partFile.delete() }
                        throw IllegalStateException("416 Range Not Satisfiable")
                    }
                    if (!response.isSuccessful) {
                        throw ClipHttpException(
                            response.code,
                            KitsugiStrings.get(R.string.kitsugi_clip_error_http, response.code)
                        )
                    }
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (contentType.startsWith("text/") ||
                        contentType.contains("json") ||
                        contentType.contains("html")
                    ) {
                        throw IllegalStateException(
                            KitsugiStrings.get(R.string.kitsugi_clip_error_invalid_content)
                        )
                    }

                    val body = response.body ?: throw IllegalStateException(
                        KitsugiStrings.get(R.string.kitsugi_clip_error_empty)
                    )
                    val isRange = response.code == 206
                    val append = isRange && existingLength > 0L
                    val contentLength = body.contentLength()
                    val totalBytes = if (isRange) contentLength + existingLength else contentLength

                    if (totalBytes > MAX_CLIP_BYTES) {
                        throw IllegalStateException(
                            KitsugiStrings.get(R.string.kitsugi_clip_error_too_large)
                        )
                    }

                    if (!ClipDownloadSource.hasFreeSpace(partFile.parentFile ?: partFile, totalBytes)) {
                        throw IllegalStateException(
                            KitsugiStrings.get(R.string.kitsugi_clip_error_no_space)
                        )
                    }

                    body.byteStream().use { input ->
                        java.io.BufferedOutputStream(
                            java.io.FileOutputStream(partFile, append),
                            64 * 1024
                        ).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesDownloaded = if (append) existingLength else 0L
                            var lastEmit = 0L
                            var lastProgress = -1
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesDownloaded += read
                                if (bytesDownloaded > MAX_CLIP_BYTES) {
                                    throw IllegalStateException(
                                        KitsugiStrings.get(R.string.kitsugi_clip_error_too_large)
                                    )
                                }
                                val progress = if (totalBytes > 0L) {
                                    (100L * bytesDownloaded / totalBytes).toInt().coerceIn(0, 100)
                                } else 0
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastEmit >= 250L || progress != lastProgress) {
                                    lastEmit = now
                                    lastProgress = progress
                                    onProgress(progress, bytesDownloaded, totalBytes, 0)
                                }
                            }
                            output.flush()
                            onProgress(100, bytesDownloaded, totalBytes, 0)
                        }
                    }
                }
                return@withContext
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("AnimeDownloader", "Klip indirme denemesi $attempt başarısız: ${e.message}")
                // 4xx (429 hariç) tekrar denemekle düzelmez → hemen bildir.
                val httpCode = (e as? ClipHttpException)?.code ?: 0
                if (httpCode in 400..499 && httpCode != 408 && httpCode != 429) break
                if (attempt < 3) delay(1_500L * attempt)
            }
        }
        throw lastError ?: IllegalStateException(
            KitsugiStrings.get(R.string.kitsugi_clip_error_generic)
        )
    }

    /**
     * HLS klip indirmesi: master → en iyi varyant, segmentler sırayla `.part` dosyasına
     * yazılır (TS birleştirme). YouTube HLS akışı ses+görüntüyü birlikte taşır.
     */
    private suspend fun downloadHlsClip(
        manifestUrl: String,
        partFile: File,
        headers: Map<String, String>,
        onProgress: (Int, Long, Long, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        suspend fun fetchPlaylist(url: String): String = try {
            getUrlContent(url, headers)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            throw IllegalStateException(KitsugiStrings.get(R.string.kitsugi_clip_error_network))
        } catch (e: Exception) {
            throw IllegalStateException(
                KitsugiStrings.get(R.string.kitsugi_clip_error_no_stream)
            )
        }

        val playlist = fetchPlaylist(manifestUrl)
        var mediaPlaylistUrl = manifestUrl
        var mediaPlaylist = playlist

        if (playlist.contains("#EXT-X-STREAM-INF", ignoreCase = true)) {
            var bestVariant: String? = null
            var bestBandwidth = -1L
            val lines = playlist.lines()
            var index = 0
            while (index < lines.size) {
                val line = lines[index].trim()
                if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                    val bandwidth = Regex("""BANDWIDTH=(\d+)""")
                        .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                    var next = index + 1
                    while (next < lines.size && lines[next].trim().startsWith("#")) next++
                    val candidate = lines.getOrNull(next)?.trim().orEmpty()
                    if (candidate.isNotEmpty() && bandwidth > bestBandwidth) {
                        bestBandwidth = bandwidth
                        bestVariant = candidate
                    }
                }
                index++
            }
            if (bestVariant == null) {
                throw IllegalStateException(KitsugiStrings.get(R.string.kitsugi_clip_error_no_stream))
            }
            mediaPlaylistUrl = resolveUrl(manifestUrl, bestVariant)
            mediaPlaylist = fetchPlaylist(mediaPlaylistUrl)
        }

        val segments = mutableListOf<String>()
        var initSegmentUrl: String? = null
        mediaPlaylist.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#")) {
                if (line.startsWith("#EXT-X-MAP:", ignoreCase = true)) {
                    Regex("""URI=["']([^"']+)["']""").find(line)?.groupValues?.getOrNull(1)
                        ?.let { initSegmentUrl = resolveUrl(mediaPlaylistUrl, it) }
                }
                return@forEach
            }
            segments += resolveUrl(mediaPlaylistUrl, line)
        }

        if (segments.isEmpty()) {
            throw IllegalStateException(KitsugiStrings.get(R.string.kitsugi_clip_error_no_stream))
        }

        // HLS'te parça sınırı bilinmediği için yarıda kalan dosyaya EKLEME yapılmaz;
        // aksi hâlde segmentler baştan tekrar yazılıp bozuk (çift) bir akış oluşurdu.
        runCatching { partFile.delete() }
        var bytesDownloaded = 0L
        var lastEmit = 0L
        var lastProgress = -1

        java.io.BufferedOutputStream(
            java.io.FileOutputStream(partFile, false),
            64 * 1024
        ).use { output ->
            if (initSegmentUrl != null) {
                bytesDownloaded += downloadSegmentToStream(initSegmentUrl!!, headers, output)
            }
            segments.forEachIndexed { index, segmentUrl ->
                coroutineContext.ensureActive()
                bytesDownloaded += downloadSegmentToStream(segmentUrl, headers, output)

                // Güvenlik tavanı ve periyodik boş alan kontrolü: uzun bir akış cihazı doldurmasın.
                if (bytesDownloaded > MAX_CLIP_BYTES) {
                    throw IllegalStateException(
                        KitsugiStrings.get(R.string.kitsugi_clip_error_too_large)
                    )
                }
                if (index % 20 == 0 && !ClipDownloadSource.hasFreeSpace(partFile, 0L)) {
                    throw IllegalStateException(
                        KitsugiStrings.get(R.string.kitsugi_clip_error_no_space)
                    )
                }

                val progress = (100 * (index + 1) / segments.size).coerceIn(0, 100)
                val now = android.os.SystemClock.elapsedRealtime()
                if (index + 1 == segments.size || now - lastEmit >= 250L ||
                    (progress != lastProgress && now - lastEmit >= 100L)
                ) {
                    lastEmit = now
                    lastProgress = progress
                    onProgress(progress, bytesDownloaded, 0L, index + 1)
                }
            }
            output.flush()
        }
    }

    /** HTTP durum kodunu hata mesajıyla taşıyan küçük istisna (tekrar deneme kararı için). */
    private class ClipHttpException(val code: Int, message: String) : Exception(message)



    private suspend fun getUrlContent(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        KitsugiHttpClient.client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to fetch HLS playlist: ${response.code} ${response.message}")
            }
            response.body?.string() ?: throw Exception("Empty playlist content")
        }
    }

    private suspend fun downloadSegmentToStream(
        url: String,
        headers: Map<String, String>,
        outputStream: java.io.OutputStream
    ): Long = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val requestBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> requestBuilder.header(k, v) }
                var copiedBytes = 0L
                KitsugiHttpClient.client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Failed to download segment: ${response.code} ${response.message}")
                    }
                    val body = response.body ?: throw Exception("Empty segment body")
                    body.byteStream().use { inputStream ->
                        copiedBytes = inputStream.copyTo(outputStream, 64 * 1024)
                    }
                }
                return@withContext copiedBytes // Success!
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                android.util.Log.w("AnimeDownloader", "Segment download attempt $attempt failed: ${e.message}")
                if (attempt < 3) {
                    delay(1000L * attempt)
                }
            }
        }
        throw lastException ?: Exception("Unknown error downloading segment")
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        try {
            val baseUri = java.net.URI(baseUrl)
            val resolvedUri = baseUri.resolve(relativeUrl)
            return resolvedUri.toString()
        } catch (e: Exception) {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                return relativeUrl
            }
            val baseWithoutQuery = baseUrl.substringBefore("?")
            val lastSlash = baseWithoutQuery.lastIndexOf('/')
            return if (lastSlash != -1) {
                val directory = baseWithoutQuery.substring(0, lastSlash + 1)
                directory + relativeUrl
            } else {
                relativeUrl
            }
        }
    }
}
