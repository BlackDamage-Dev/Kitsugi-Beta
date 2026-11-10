package com.kitsugi.animelist.core.download

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.core.player.OfflinePlaybackHelper
import com.kitsugi.animelist.core.player.PlayerMediaSourceFactory
import com.kitsugi.animelist.core.player.PlayerSubtitleUtils
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.data.local.AnimeDownloadManager
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.model.DownloadKind
import com.kitsugi.animelist.data.model.ExternalAudioTrack
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.data.repository.SubtitleRepositoryImpl
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

class UnsupportedMediaException(message: String) : IOException(message)

class AnimeDownloader(private val context: Context) {

    companion object {
        private const val TAG = "AnimeDownloader"
        private const val IO_BUFFER_BYTES = 64 * 1024
        private const val PROBE_PEEK_BYTES = 8 * 1024
        private const val MAX_URL_HOPS = 4
        private const val MAX_MASTER_DEPTH = 4
        private const val MAX_CLIP_BYTES = 2L * 1024L * 1024L * 1024L
    }

    private data class FetchedText(
        val body: String,
        val finalUrl: String,
        val contentType: String?
    )

    private sealed class StreamProbeResult {
        data class Hls(val manifestText: String, val finalUrl: String) : StreamProbeResult()
        data class Progressive(val finalUrl: String) : StreamProbeResult()
    }

    private data class StagingResumeState(
        val sourceUrl: String = "",
        val externalAudioUrl: String = "",
        val videoPlaylistUrl: String = "",
        val audioPlaylistUrl: String = "",
        val videoSegmentsDone: Int = 0,
        val audioSegmentsDone: Int = 0,
        val firstAudioId3PtsUs: Long? = null
    )

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

        val settings = SettingsDataStore(context).settingsFlow.first()
        val mediaId = AnimeDownloadManager.buildMediaId(
            animeId = download.animeId,
            animeTitle = download.animeTitle,
            episode = download.episode
        )
        val baseName = DownloadExportNaming.exportBaseName(download.animeTitle, download.episode)
        val videoDir = OfflinePlaybackHelper.getDownloadsDir(context).also { it.mkdirs() }
        val stagingDir = DownloadExportNaming.stagingDir(videoDir, mediaId).also { it.mkdirs() }

        val stateFile = File(stagingDir, "state.json")
        val stagingVideoFile = File(stagingDir, "video.part")
        val stagingAudioFile = File(stagingDir, "audio.part")

                // Bağımsız ses indirme akışı
        if (download.effectiveKind == DownloadKind.AUDIO) {
            val preferredAudioLangs = settings.preferredAudioLanguages
                .split(',')
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("tr", "ja", "en") }
            val workId = Math.abs(download.uniqueKey.hashCode())
            val workDir = File(context.cacheDir, "kitsugi_dl_audio_$workId")
            try {
                if (workDir.exists()) workDir.deleteRecursively()
                workDir.mkdirs()
                val http = StreamDownloadHttpClient(
                    addonName = download.source,
                    streamTitle = download.streamTitle ?: download.animeTitle,
                    isCloudStream = download.isCloudStream || !download.source.isNullOrBlank(),
                    qualityValue = download.qualityValue,
                    baseHeaders = download.safeHeaders
                )
                val bytesCounter = AtomicLong(0L)
                val finalFile = executeStandaloneAudioDownload(
                    download = download,
                    http = http,
                    workDir = workDir,
                    preferredAudioLangs = preferredAudioLangs,
                    safUriStr = settings.videoDownloadUri,
                    bytesCounter = bytesCounter,
                    onProgress = onProgress
                )
                val verification = DownloadVerifier.verify(finalFile)
                if (!verification.playable) {
                    Log.w(TAG, "İndirilen ses doğrulanamadı: ${verification.warning} (${verification.details})")
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
                Log.e(TAG, "Ses indirme başarısız", e)
                val kind = DownloadResilience.classify(e)
                val message = buildErrorMessage(e, kind)
                onStatusChanged(AnimeDownload.Status.ERROR, null, message)
            } finally {
                runCatching { workDir.deleteRecursively() }
            }
            return@withContext
        }

        val (httpClient, effectiveHeaders) = DownloadHttpClientFactory.buildClient(download)

        try {
            val savedState = readStagingState(stateFile)
            val isSameSource = savedState != null &&
                savedState.sourceUrl == download.url &&
                savedState.externalAudioUrl == download.externalAudioUrl.orEmpty()

            if (!isSameSource) {
                runCatching { stagingVideoFile.delete() }
                runCatching { stagingAudioFile.delete() }
                runCatching { stateFile.delete() }
            }

            var currentState = if (isSameSource) {
                savedState!!
            } else {
                StagingResumeState(
                    sourceUrl = download.url,
                    externalAudioUrl = download.externalAudioUrl.orEmpty()
                )
            }

            val hlsExtraSubtitleRenditions = mutableListOf<HlsManifestParser.HlsSubtitleRendition>()

            // 1) Akış türünü tespit et (URL + Content-Type + ilk baytlar + düz URL yönlendirmesi + GDrive onayı)
            val probe = probeStreamKind(httpClient, download.url)
            var audioFirstId3PtsUs: Long? = currentState.firstAudioId3PtsUs

            when (probe) {
                is StreamProbeResult.Hls -> {
                    val preferredAudioLangs = buildPreferredAudioLanguages(
                        download = download,
                        settingsPreferred = settings.preferredAudioLanguages
                    )
                    val audioKindHint = inferAudioKindHint(
                        explicitKind = download.audioKind,
                        streamTitle = download.streamTitle,
                        streamName = download.streamName
                    )

                    val resolvedHls = resolveHlsStreams(
                        httpClient = httpClient,
                        initialManifest = probe.manifestText,
                        initialUrl = probe.finalUrl,
                        preferredAudioLangs = preferredAudioLangs,
                        audioKindHint = audioKindHint,
                        hasExternalAudio = !download.externalAudioUrl.isNullOrBlank()
                    )

                    hlsExtraSubtitleRenditions.addAll(resolvedHls.subtitleRenditions)

                    val hasSeparateAudio = resolvedHls.audioMedia != null || !download.externalAudioUrl.isNullOrBlank()
                    val videoProgressWeight = if (hasSeparateAudio) 82 else 96

                    // Video segmentlerini indir (duraklatılmışsa kaldığı segmentten devam eder)
                    val resumeVideoFrom = if (stagingVideoFile.isFile && stagingVideoFile.length() > 0L) {
                        currentState.videoSegmentsDone.coerceIn(0, resolvedHls.videoMedia.segments.size)
                    } else {
                        0
                    }

                    currentState = currentState.copy(videoPlaylistUrl = resolvedHls.videoMedia.baseUrl)
                    writeStagingState(stateFile, currentState)

                    val videoBytes = downloadHlsMediaToFile(
                        httpClient = httpClient,
                        media = resolvedHls.videoMedia,
                        destFile = stagingVideoFile,
                        startSegmentIndex = resumeVideoFrom,
                        progressStartPct = 0,
                        progressEndPct = videoProgressWeight,
                        baseBytesAccumulated = 0L,
                        onSegmentCommitted = { doneCount ->
                            currentState = currentState.copy(videoSegmentsDone = doneCount)
                            if (doneCount % 10 == 0 || doneCount == resolvedHls.videoMedia.segments.size) {
                                writeStagingState(stateFile, currentState)
                            }
                        },
                        onProgress = onProgress
                    )

                    // Ayrık HLS ses rendisyonu (#EXT-X-MEDIA:TYPE=AUDIO) veya harici ses URL'si varsa indir
                    if (resolvedHls.audioMedia != null) {
                        val audioMedia = resolvedHls.audioMedia
                        val resumeAudioFrom = if (stagingAudioFile.isFile && stagingAudioFile.length() > 0L) {
                            currentState.audioSegmentsDone.coerceIn(0, audioMedia.segments.size)
                        } else {
                            0
                        }
                        currentState = currentState.copy(audioPlaylistUrl = audioMedia.baseUrl)
                        writeStagingState(stateFile, currentState)

                        downloadHlsMediaToFile(
                            httpClient = httpClient,
                            media = audioMedia,
                            destFile = stagingAudioFile,
                            startSegmentIndex = resumeAudioFrom,
                            progressStartPct = videoProgressWeight,
                            progressEndPct = 96,
                            baseBytesAccumulated = videoBytes,
                            onFirstId3PtsUs = { ptsUs ->
                                if (audioFirstId3PtsUs == null) {
                                    audioFirstId3PtsUs = ptsUs
                                    currentState = currentState.copy(firstAudioId3PtsUs = ptsUs)
                                    writeStagingState(stateFile, currentState)
                                }
                            },
                            onSegmentCommitted = { doneCount ->
                                currentState = currentState.copy(audioSegmentsDone = doneCount)
                                if (doneCount % 15 == 0 || doneCount == audioMedia.segments.size) {
                                    writeStagingState(stateFile, currentState)
                                }
                            },
                            onProgress = onProgress
                        )
                    } else if (!download.externalAudioUrl.isNullOrBlank()) {
                        downloadExternalAudioStream(
                            httpClient = httpClient,
                            externalAudioUrl = download.externalAudioUrl,
                            destFile = stagingAudioFile,
                            progressStartPct = videoProgressWeight,
                            progressEndPct = 96,
                            baseBytesAccumulated = videoBytes,
                            onFirstId3PtsUs = { ptsUs ->
                                if (audioFirstId3PtsUs == null) audioFirstId3PtsUs = ptsUs
                            },
                            onProgress = onProgress
                        )
                    }
                }

                is StreamProbeResult.Progressive -> {
                    val hasExternalAudio = !download.externalAudioUrl.isNullOrBlank()
                    val videoMaxPct = if (hasExternalAudio) 85 else 96

                    val videoBytes = downloadProgressiveFile(
                        httpClient = httpClient,
                        url = probe.finalUrl,
                        destFile = stagingVideoFile,
                        progressStartPct = 0,
                        progressEndPct = videoMaxPct,
                        baseBytesAccumulated = 0L,
                        onProgress = { pct, dlBytes, totalBytes ->
                            onProgress(pct, dlBytes, totalBytes, 0)
                        }
                    )

                    if (hasExternalAudio) {
                        downloadExternalAudioStream(
                            httpClient = httpClient,
                            externalAudioUrl = download.externalAudioUrl!!,
                            destFile = stagingAudioFile,
                            progressStartPct = videoMaxPct,
                            progressEndPct = 96,
                            baseBytesAccumulated = videoBytes,
                            onFirstId3PtsUs = { ptsUs ->
                                if (audioFirstId3PtsUs == null) audioFirstId3PtsUs = ptsUs
                            },
                            onProgress = onProgress
                        )
                    }
                }
            }

            if (!stagingVideoFile.isFile || stagingVideoFile.length() == 0L) {
                throw IOException("İndirilen video dosyası boş")
            }

            // 2) Kapsayıcı birleştirme (Video + Ses Muxing / TS->MP4 kayıpsız dönüştürme)
            coroutineContext.ensureActive()
            val (finalVideoFile, finalCompanionAudioFile) = finalizeDownloadedMedia(
                stagingVideoFile = stagingVideoFile,
                stagingAudioFile = stagingAudioFile.takeIf { it.isFile && it.length() > 0L },
                videoDir = videoDir,
                baseName = baseName,
                sourceUrl = download.url,
                audioId3StartPtsUs = audioFirstId3PtsUs
            )

            val finalBytes = finalVideoFile.length() + (finalCompanionAudioFile?.length() ?: 0L)
            onProgress(98, finalBytes, finalBytes, download.downloadedSegments)

            // 3) Altyazıları çöz ve indir (eklenti altyazıları + HLS gömülü VTT + OpenSubtitles/SubDL)
            val savedSubs = downloadAllSubtitles(
                download = download,
                settingsSubtitleLangs = settings.subtitleDownloadLanguages,
                hlsSubtitleRenditions = hlsExtraSubtitleRenditions,
                httpClient = httpClient,
                effectiveVideoHeaders = effectiveHeaders,
                videoDir = videoDir,
                baseName = baseName
            )

            // 4) Metadata dosyasını yaz (video + varsa yan ses adı)
            val metaFile = File(videoDir, DownloadExportNaming.metadataFileName(baseName))
            val metaJson = JSONObject().apply {
                put("mediaId", mediaId)
                put("title", "${download.animeTitle} - Bölüm ${download.episode}")
                put("videoFile", finalVideoFile.name)
                if (finalCompanionAudioFile != null) {
                    put("audioFile", finalCompanionAudioFile.name)
                }
                put("durationMs", 0)
                put("downloadedAtMs", System.currentTimeMillis())
            }
            metaFile.writeText(metaJson.toString(2))

            // 5) Poster görselini indir (best-effort)
            val posterFile = File(
                DownloadExportNaming.publicImagesDir().also { it.mkdirs() },
                DownloadExportNaming.posterFileName(baseName)
            )
            downloadPosterIfAvailable(httpClient, download.posterUrl, posterFile)

            // 6) Özel SAF klasörü seçiliyse kopyala
            copyToCustomSafIfConfigured(
                customDirUriStr = settings.videoDownloadUri,
                finalVideoFile = finalVideoFile,
                finalCompanionAudioFile = finalCompanionAudioFile,
                savedSubs = savedSubs
            )

            // 7) Geçici çalışma dizinini temizle ve MediaScanner'a bildir
            runCatching { stagingDir.deleteRecursively() }

            val toScan = buildList {
                add(finalVideoFile)
                if (finalCompanionAudioFile != null) add(finalCompanionAudioFile)
                addAll(savedSubs)
                if (posterFile.isFile) add(posterFile)
            }.map { it.absolutePath }.toTypedArray()
            MediaScannerConnection.scanFile(context, toScan, null, null)

            onProgress(100, finalBytes, finalBytes, download.downloadedSegments)
            onStatusChanged(
                AnimeDownload.Status.COMPLETED,
                finalVideoFile.absolutePath,
                finalCompanionAudioFile?.absolutePath
            )
        } catch (ce: CancellationException) {
            // Duraklatma / iptal durumunda .staging_<mediaId> korunur ve PAUSED durumu ezilmez!
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${download.animeTitle} ep=${download.episode} url=${download.url}", e)
            val kind = DownloadResilience.classify(e)
            val message = buildErrorMessage(e, kind)
            onStatusChanged(AnimeDownload.Status.ERROR, null, message)
        }
    }

    // ── Akış Türü Tespiti (HLS / Progressive / Plain-URL Hop / GDrive) ────────

    private suspend fun probeStreamKind(
        httpClient: OkHttpClient,
        initialUrl: String
    ): StreamProbeResult {
        var currentUrl = initialUrl.trim()

        for (hop in 0 until MAX_URL_HOPS) {
            coroutineContext.ensureActive()

            if (HlsManifestParser.isLikelyHlsUrl(currentUrl)) {
                val fetched = fetchTextWithUrl(httpClient, currentUrl)
                val nextHop = HlsManifestParser.extractPlainUrlHop(fetched.body)
                if (nextHop != null && nextHop != currentUrl) {
                    currentUrl = HlsManifestParser.resolveUrl(fetched.finalUrl, nextHop)
                    continue
                }
                if (looksLikeHtmlDocument(fetched.contentType, fetched.body)) {
                    val gdriveConfirm = extractGoogleDriveConfirmUrl(fetched.body, fetched.finalUrl)
                    if (gdriveConfirm != null && gdriveConfirm != currentUrl) {
                        currentUrl = gdriveConfirm
                        continue
                    }
                    throw IOException("HLS manifesti yerine HTML hata sayfası döndü")
                }
                return StreamProbeResult.Hls(fetched.body, fetched.finalUrl)
            }

            // Uzantısız veya .mp4/.txt görünümlü URL'lerde ilk baytları kokla
            val request = Request.Builder().url(currentUrl).get().build()
            val hopDecision: Pair<String?, StreamProbeResult?> = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Akış bağlantısı başarısız: HTTP ${response.code}")
                }
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type")
                val body = response.body ?: throw IOException("Boş sunucu yanıtı")

                val buffered = BufferedInputStream(body.byteStream(), PROBE_PEEK_BYTES * 2)
                val head = ByteArray(PROBE_PEEK_BYTES)
                var headRead = 0
                while (headRead < head.size) {
                    val n = buffered.read(head, headRead, head.size - headRead)
                    if (n <= 0) break
                    headRead += n
                }

                // 1) HLS manifesti mi? (#EXTM3U / mpegurl / çıplak varyant listesi)
                if (HlsManifestParser.looksLikeHlsContent(contentType, head, headRead) ||
                    HlsManifestParser.isLikelyHlsUrl(finalUrl)
                ) {
                    val rest = buffered.readBytes()
                    val fullBytes = ByteArray(headRead + rest.size)
                    System.arraycopy(head, 0, fullBytes, 0, headRead)
                    System.arraycopy(rest, 0, fullBytes, headRead, rest.size)
                    val fullText = String(fullBytes, Charsets.UTF_8)

                    val plainHop = HlsManifestParser.extractPlainUrlHop(fullText)
                    if (plainHop != null) {
                        return@use HlsManifestParser.resolveUrl(finalUrl, plainHop) to null
                    }
                    return@use null to StreamProbeResult.Hls(fullText, finalUrl)
                }

                // 2) Kısa metin yanıtı -> tek satır https:// yönlendirmesi veya Google Drive onay formu mu?
                val contentLen = body.contentLength()
                val ctLower = contentType?.lowercase(Locale.ROOT).orEmpty()
                val headText = if (headRead > 0) String(head, 0, headRead, Charsets.UTF_8) else ""

                if (ctLower.startsWith("text/") || looksLikeHtmlDocument(contentType, headText) ||
                    (contentLen in 1..4096 && HlsManifestParser.extractPlainUrlHop(headText) != null)
                ) {
                    val rest = if (contentLen < 0 || contentLen <= 256 * 1024) buffered.readBytes() else ByteArray(0)
                    val fullBytes = ByteArray(headRead + rest.size)
                    System.arraycopy(head, 0, fullBytes, 0, headRead)
                    System.arraycopy(rest, 0, fullBytes, headRead, rest.size)
                    val fullText = String(fullBytes, Charsets.UTF_8)

                    val plainHop = HlsManifestParser.extractPlainUrlHop(fullText)
                    if (plainHop != null) {
                        return@use HlsManifestParser.resolveUrl(finalUrl, plainHop) to null
                    }

                    val gdriveConfirm = extractGoogleDriveConfirmUrl(fullText, finalUrl)
                    if (gdriveConfirm != null) {
                        return@use gdriveConfirm to null
                    }

                    if (looksLikeHtmlDocument(contentType, fullText)) {
                        throw IOException("Sunucu video yerine HTML sayfası döndürdü (403/koruma)")
                    }
                }

                null to StreamProbeResult.Progressive(finalUrl)
            }

            val (nextUrl, resolvedResult) = hopDecision
            if (resolvedResult != null) return resolvedResult
            if (!nextUrl.isNullOrBlank() && nextUrl != currentUrl) {
                currentUrl = nextUrl
                continue
            }
            return StreamProbeResult.Progressive(currentUrl)
        }

        return StreamProbeResult.Progressive(currentUrl)
    }

    // ── HLS Master -> Video + Ayrık Ses + Altyazı Çözümlemesi ─────────────────

    private data class ResolvedHlsStreams(
        val videoMedia: HlsManifestParser.ParsedHlsPlaylist.Media,
        val audioMedia: HlsManifestParser.ParsedHlsPlaylist.Media?,
        val subtitleRenditions: List<HlsManifestParser.HlsSubtitleRendition>
    )

    private suspend fun resolveHlsStreams(
        httpClient: OkHttpClient,
        initialManifest: String,
        initialUrl: String,
        preferredAudioLangs: List<String>,
        audioKindHint: String?,
        hasExternalAudio: Boolean
    ): ResolvedHlsStreams {
        var currentText = initialManifest
        var currentUrl = initialUrl
        var audioCandidates: List<HlsManifestParser.HlsAudioRendition> = emptyList()
        var subtitleRenditions: List<HlsManifestParser.HlsSubtitleRendition> = emptyList()

        var resolvedVideoMedia: HlsManifestParser.ParsedHlsPlaylist.Media? = null

        for (depth in 0 until MAX_MASTER_DEPTH) {
            coroutineContext.ensureActive()
            when (val parsed = HlsManifestParser.parse(currentText, currentUrl)) {
                is HlsManifestParser.ParsedHlsPlaylist.Media -> {
                    if (parsed.segments.isEmpty()) {
                        throw IOException("HLS medya listesinde segment bulunamadı")
                    }
                    resolvedVideoMedia = parsed
                    break
                }

                is HlsManifestParser.ParsedHlsPlaylist.Master -> {
                    val plan = HlsManifestParser.selectBestPlan(
                        master = parsed,
                        preferredAudioLanguages = preferredAudioLangs,
                        audioKindHint = audioKindHint,
                        hasExternalAudio = hasExternalAudio
                    ) ?: throw IOException("HLS master listesinde uygun video varyantı bulunamadı")

                    if (plan.audioCandidates.isNotEmpty()) {
                        audioCandidates = plan.audioCandidates
                    }
                    if (plan.subtitleRenditions.isNotEmpty()) {
                        subtitleRenditions = plan.subtitleRenditions
                    }

                    // Önce en iyi varyantı dene; 403/404 alırsa diğer video varyantlarına düş
                    val orderedVariants = buildList {
                        add(plan.selectedVariant)
                        parsed.variants
                            .filterNot { it.isAudioOnly || it.url == plan.selectedVariant.url }
                            .sortedByDescending { it.effectiveHeight * 1_000_000L + it.bandwidth }
                            .forEach { add(it) }
                    }

                    var fetchedVariant: FetchedText? = null
                    var lastErr: Exception? = null
                    for (candidateVariant in orderedVariants) {
                        try {
                            fetchedVariant = fetchTextWithUrl(httpClient, candidateVariant.url)
                            break
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            lastErr = e
                            Log.w(TAG, "HLS varyantı açılamadı (${candidateVariant.url}): ${e.message}")
                        }
                    }

                    val winner = fetchedVariant ?: throw (lastErr ?: IOException("Hiçbir HLS varyantı açılamadı"))
                    currentText = winner.body
                    currentUrl = winner.finalUrl
                }
            }
        }

        val finalVideoMedia = resolvedVideoMedia
            ?: throw IOException("HLS video medya listesi çözümlenemedi")

        // Ayrık ses rendisyonu adaylarını sırayla dene (#EXT-X-MEDIA:TYPE=AUDIO,URI=...)
        var resolvedAudioMedia: HlsManifestParser.ParsedHlsPlaylist.Media? = null
        for (audioCandidate in audioCandidates) {
            val audioUri = audioCandidate.uri ?: continue
            try {
                val media = resolveMediaPlaylistOnly(httpClient, audioUri)
                if (media.segments.isNotEmpty()) {
                    Log.i(
                        TAG,
                        "HLS ayrık ses rendisyonu seçildi: name=${audioCandidate.name}, " +
                            "lang=${audioCandidate.language}, segments=${media.segments.size}"
                    )
                    resolvedAudioMedia = media
                    break
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Ayrık ses rendisyonu açılamadı (${audioCandidate.name} -> $audioUri): ${e.message}")
            }
        }

        return ResolvedHlsStreams(
            videoMedia = finalVideoMedia,
            audioMedia = resolvedAudioMedia,
            subtitleRenditions = subtitleRenditions
        )
    }

    private suspend fun resolveMediaPlaylistOnly(
        httpClient: OkHttpClient,
        playlistUrl: String
    ): HlsManifestParser.ParsedHlsPlaylist.Media {
        var textFetch = fetchTextWithUrl(httpClient, playlistUrl)
        for (depth in 0 until MAX_MASTER_DEPTH) {
            val plainHop = HlsManifestParser.extractPlainUrlHop(textFetch.body)
            if (plainHop != null) {
                textFetch = fetchTextWithUrl(
                    httpClient,
                    HlsManifestParser.resolveUrl(textFetch.finalUrl, plainHop)
                )
                continue
            }
            when (val parsed = HlsManifestParser.parse(textFetch.body, textFetch.finalUrl)) {
                is HlsManifestParser.ParsedHlsPlaylist.Media -> return parsed
                is HlsManifestParser.ParsedHlsPlaylist.Master -> {
                    val nextUrl = parsed.variants.firstOrNull()?.url
                        ?: parsed.audioRenditions.firstOrNull { !it.uri.isNullOrBlank() }?.uri
                        ?: throw IOException("İç içe ses master listesi boş")
                    textFetch = fetchTextWithUrl(httpClient, nextUrl)
                }
            }
        }
        throw IOException("Ses medya listesi derinlik sınırını aştı")
    }

    // ── HLS Segment İndirici (fMP4 MAP + ByteRange + AES-128 + Sahte Başlık Temizliği) ──

    private suspend fun downloadHlsMediaToFile(
        httpClient: OkHttpClient,
        media: HlsManifestParser.ParsedHlsPlaylist.Media,
        destFile: File,
        startSegmentIndex: Int,
        progressStartPct: Int,
        progressEndPct: Int,
        baseBytesAccumulated: Long,
        onFirstId3PtsUs: ((Long) -> Unit)? = null,
        onSegmentCommitted: ((Int) -> Unit)? = null,
        onProgress: (Int, Long, Long, Int) -> Unit
    ): Long {
        val segments = media.segments
        if (segments.isEmpty()) throw IOException("Boş HLS segment listesi")
        if (startSegmentIndex >= segments.size && destFile.isFile && destFile.length() > 0L) {
            return destFile.length()
        }

        val appendMode = startSegmentIndex > 0 && destFile.isFile && destFile.length() > 0L
        val effectiveStart = if (appendMode) startSegmentIndex else 0
        var bytesWritten = if (appendMode) destFile.length() else 0L

        val keyCache = HashMap<String, ByteArray>()
        var lastWrittenInitKey: String? = if (appendMode && effectiveStart > 0) {
            segments[effectiveStart - 1].initSegment?.cacheKey
        } else {
            null
        }

        var lastEmitMs = 0L
        var lastReportedPct = -1
        var capturedFirstPts = false

        BufferedOutputStream(FileOutputStream(destFile, appendMode), IO_BUFFER_BYTES).use { out ->
            val total = segments.size
            for (index in effectiveStart until total) {
                coroutineContext.ensureActive()
                val seg = segments[index]

                // fMP4 / CMAF başlatma segmenti (#EXT-X-MAP)
                val initSeg = seg.initSegment
                if (initSeg != null && initSeg.cacheKey != lastWrittenInitKey) {
                    var initBytes = fetchSegmentBytesWithRetry(httpClient, initSeg.url, initSeg.byteRange)
                    val initEnc = initSeg.encryptionKey
                    if (initEnc != null && initEnc.isAes128) {
                        val keyBytes = fetchEncryptionKey(httpClient, initEnc.uri!!, keyCache)
                        initBytes = HlsSegmentProcessor.decryptAes128(initBytes, keyBytes, initEnc.ivHex, 0L)
                    }
                    out.write(initBytes)
                    bytesWritten += initBytes.size
                    lastWrittenInitKey = initSeg.cacheKey
                }

                // Medya segmentini indir
                var segBytes = fetchSegmentBytesWithRetry(httpClient, seg.url, seg.byteRange)

                // AES-128 şifreliyse çöz
                val enc = seg.encryptionKey
                if (enc != null && enc.isAes128) {
                    val keyBytes = fetchEncryptionKey(httpClient, enc.uri!!, keyCache)
                    segBytes = HlsSegmentProcessor.decryptAes128(
                        encrypted = segBytes,
                        keyBytes = keyBytes,
                        explicitIvHex = enc.ivHex,
                        sequenceNumber = seg.sequenceNumber
                    )
                }

                // Sahte PNG/JPG/JS başlığını veya tekrarlayan Packed-Audio ID3 etiketini temizle
                val sanitized = HlsSegmentProcessor.sanitizeSegment(segBytes)
                if (!capturedFirstPts && sanitized.id3TimestampUs != null) {
                    capturedFirstPts = true
                    onFirstId3PtsUs?.invoke(sanitized.id3TimestampUs)
                }

                if (sanitized.length > 0) {
                    out.write(sanitized.bytes, sanitized.offset, sanitized.length)
                    bytesWritten += sanitized.length
                }

                val doneCount = index + 1
                onSegmentCommitted?.invoke(doneCount)

                val span = (progressEndPct - progressStartPct).coerceAtLeast(1)
                val scaledPct = (progressStartPct + (span * doneCount) / total).coerceIn(0, 99)
                val now = SystemClock.elapsedRealtime()
                val isLast = doneCount == total
                if (isLast ||
                    now - lastEmitMs >= 250L ||
                    (scaledPct != lastReportedPct && now - lastEmitMs >= 100L)
                ) {
                    lastEmitMs = now
                    lastReportedPct = scaledPct
                    onProgress(scaledPct, baseBytesAccumulated + bytesWritten, 0L, doneCount)
                }
            }
            out.flush()
        }

        return bytesWritten
    }

    private suspend fun downloadExternalAudioStream(
        httpClient: OkHttpClient,
        externalAudioUrl: String,
        destFile: File,
        progressStartPct: Int,
        progressEndPct: Int,
        baseBytesAccumulated: Long,
        onFirstId3PtsUs: ((Long) -> Unit)? = null,
        onProgress: (Int, Long, Long, Int) -> Unit
    ) {
        try {
            when (val audioProbe = probeStreamKind(httpClient, externalAudioUrl)) {
                is StreamProbeResult.Hls -> {
                    val media = resolveMediaPlaylistOnly(httpClient, audioProbe.finalUrl)
                    downloadHlsMediaToFile(
                        httpClient = httpClient,
                        media = media,
                        destFile = destFile,
                        startSegmentIndex = 0,
                        progressStartPct = progressStartPct,
                        progressEndPct = progressEndPct,
                        baseBytesAccumulated = baseBytesAccumulated,
                        onFirstId3PtsUs = onFirstId3PtsUs,
                        onProgress = onProgress
                    )
                }

                is StreamProbeResult.Progressive -> {
                    downloadProgressiveFile(
                        httpClient = httpClient,
                        url = audioProbe.finalUrl,
                        destFile = destFile,
                        progressStartPct = progressStartPct,
                        progressEndPct = progressEndPct,
                        baseBytesAccumulated = baseBytesAccumulated,
                        onProgress = { pct, dl, total ->
                            onProgress(pct, dl, total, 0)
                        }
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Harici ses akışı indirilemedi ($externalAudioUrl): ${e.message}", e)
            runCatching { destFile.delete() }
            throw e
        }
    }

    // ── Kapsayıcı Sonlandırma (Tekil MP4/WebM Birleştirme veya Güvenli Yan Ses) ──

    private suspend fun finalizeDownloadedMedia(
        stagingVideoFile: File,
        stagingAudioFile: File?,
        videoDir: File,
        baseName: String,
        sourceUrl: String,
        audioId3StartPtsUs: Long?
    ): Pair<File, File?> {
        // Önce aynı taban adlı eski video ve yan ses kalıntılarını temizle
        deleteExistingMediaOfBase(videoDir, baseName)

        if (stagingAudioFile != null && stagingAudioFile.isFile && stagingAudioFile.length() > 0L) {
            when (val muxResult = MediaRemuxer.muxVideoAndAudio(
                videoFile = stagingVideoFile,
                audioFile = stagingAudioFile,
                outputDir = videoDir,
                baseName = baseName,
                audioId3StartPtsUs = audioId3StartPtsUs
            )) {
                is MediaRemuxer.MuxOutcome.MuxedSingleFile -> {
                    return muxResult.file to null
                }

                is MediaRemuxer.MuxOutcome.CompanionAudioRequired -> {
                    Log.w(TAG, "Tek dosyada mux yapılamadı (${muxResult.reason}); yan ses dosyası kaydediliyor")
                    val videoExt = HlsSegmentProcessor.detectContainerExtension(
                        file = stagingVideoFile,
                        fallbackExtension = guessVideoExtensionFromUrl(sourceUrl)
                    )
                    val audioExt = HlsSegmentProcessor.detectContainerExtension(
                        file = stagingAudioFile,
                        fallbackExtension = "m4a"
                    )
                    val targetVideo = File(videoDir, "$baseName.$videoExt")
                    val targetAudio = File(videoDir, DownloadExportNaming.companionAudioFileName(baseName, audioExt))
                    moveOrCopyFile(stagingVideoFile, targetVideo)
                    moveOrCopyFile(stagingAudioFile, targetAudio)
                    return targetVideo to targetAudio
                }
            }
        }

        // Tekil akış (gömülü sesli HLS veya doğrudan progressive dosya)
        val detectedExt = HlsSegmentProcessor.detectContainerExtension(
            file = stagingVideoFile,
            fallbackExtension = guessVideoExtensionFromUrl(sourceUrl)
        )

        if (detectedExt == "ts") {
            when (val tsOutcome = MediaRemuxer.remuxSingleTsToMp4IfLossless(
                tsFile = stagingVideoFile,
                outputDir = videoDir,
                baseName = baseName
            )) {
                is MediaRemuxer.SingleTsOutcome.RemuxedToMp4 -> {
                    return tsOutcome.mp4File to null
                }
                is MediaRemuxer.SingleTsOutcome.KeepOriginalTs -> {
                    val targetTs = File(videoDir, "$baseName.ts")
                    moveOrCopyFile(stagingVideoFile, targetTs)
                    return targetTs to null
                }
            }
        }

        val finalExt = if (detectedExt in DownloadExportNaming.VIDEO_EXTENSIONS) detectedExt else "mp4"
        val targetFile = File(videoDir, "$baseName.$finalExt")
        moveOrCopyFile(stagingVideoFile, targetFile)
        return targetFile to null
    }

    // ── Doğrudan (Progressive) İndirici (HTTP Range 206 Destekli) ─────────────

    private suspend fun downloadProgressiveFile(
        httpClient: OkHttpClient,
        url: String,
        destFile: File,
        progressStartPct: Int,
        progressEndPct: Int,
        baseBytesAccumulated: Long,
        onProgress: (Int, Long, Long) -> Unit
    ): Long {
        var attempt = 0
        val maxAttempts = 3
        while (attempt < maxAttempts) {
            attempt++
            try {
                val existingLen = if (destFile.isFile) destFile.length() else 0L
                val reqBuilder = Request.Builder().url(url).get()
                if (existingLen > 0L) {
                    reqBuilder.header("Range", "bytes=$existingLen-")
                }

                var writtenTotal = existingLen
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.code == 416) {
                        destFile.delete()
                        throw IOException("416 Range Not Satisfiable")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("Dosya indirilemedi: HTTP ${response.code} ${response.message}")
                    }

                    val isPartial = response.code == 206 && existingLen > 0L
                    val body = response.body ?: throw IOException("Boş yanıt gövdesi")
                    val contentLen = body.contentLength()
                    val totalBytes = when {
                        contentLen > 0L && isPartial -> existingLen + contentLen
                        contentLen > 0L -> contentLen
                        else -> 0L
                    }

                    writtenTotal = if (isPartial) existingLen else 0L
                    var lastEmitMs = 0L
                    var lastPct = -1

                    body.byteStream().use { input ->
                        BufferedOutputStream(FileOutputStream(destFile, isPartial), IO_BUFFER_BYTES).use { output ->
                            val buffer = ByteArray(IO_BUFFER_BYTES)
                            var readCount: Int
                            var checkedFirstChunk = isPartial

                            while (input.read(buffer).also { readCount = it } != -1) {
                                coroutineContext.ensureActive()
                                if (!checkedFirstChunk && readCount > 0) {
                                    checkedFirstChunk = true
                                    val sampleStr = String(buffer, 0, readCount.coerceAtMost(512), Charsets.UTF_8)
                                    if (looksLikeHtmlDocument(response.header("Content-Type"), sampleStr)) {
                                        throw IOException("Video yerine HTML koruma sayfası döndü")
                                    }
                                }

                                output.write(buffer, 0, readCount)
                                writtenTotal += readCount

                                val span = (progressEndPct - progressStartPct).coerceAtLeast(1)
                                val pct = if (totalBytes > 0L) {
                                    (progressStartPct + (span * writtenTotal / totalBytes).toInt())
                                        .coerceIn(progressStartPct, progressEndPct)
                                } else {
                                    progressStartPct
                                }

                                val now = SystemClock.elapsedRealtime()
                                if (now - lastEmitMs >= 250L || (pct != lastPct && now - lastEmitMs >= 100L)) {
                                    lastEmitMs = now
                                    lastPct = pct
                                    onProgress(pct, baseBytesAccumulated + writtenTotal, totalBytes)
                                }
                            }
                            output.flush()
                        }
                    }

                    onProgress(progressEndPct, baseBytesAccumulated + writtenTotal, totalBytes)
                }
                return writtenTotal
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Progressive indirme denemesi $attempt/$maxAttempts başarısız: ${e.message}")
                if (attempt >= maxAttempts) throw e
                delay(1500L * attempt)
            }
        }
        throw IOException("Progressive indirme başarısız")
    }

    // ── Altyazı Çözümleme & İndirme (Eklenti + HLS VTT + Harici Servisler) ───

    private suspend fun downloadAllSubtitles(
        download: AnimeDownload,
        settingsSubtitleLangs: String,
        hlsSubtitleRenditions: List<HlsManifestParser.HlsSubtitleRendition>,
        httpClient: OkHttpClient,
        effectiveVideoHeaders: Map<String, String>,
        videoDir: File,
        baseName: String
    ): List<File> {
        val allResolvedSubtitles = mutableListOf<SubtitleInput>()
        if (!download.subtitles.isNullOrEmpty()) {
            allResolvedSubtitles.addAll(download.subtitles)
        }

        // Harici altyazı sağlayıcıları (OpenSubtitles, SubDL, Stremio vb.)
        try {
            val dMalId = download.malId
            val dAniListId = download.aniListId
            val dTmdbId = download.tmdbId
                ?: if (dMalId == null && dAniListId == null) download.animeId.toIntOrNull() else null

            if (dMalId != null || dAniListId != null || dTmdbId != null) {
                val resolvedIds = KitsugiIdResolver.resolveIds(
                    malId = dMalId,
                    aniListId = dAniListId,
                    tmdbId = dTmdbId,
                    mediaType = if (download.isMovie == true) com.kitsugi.animelist.model.MediaType.Movie else null,
                    bangumiId = download.bangumiId,
                    expectedSeason = download.season
                )
                val imdbId = resolvedIds.imdbId
                val kitsuId = resolvedIds.kitsuId

                val isMovieType = download.season == 0 && download.episode <= 1
                val type = if (isMovieType) "movie" else "series"
                val queryIds = buildList {
                    if (!imdbId.isNullOrBlank()) {
                        add(if (isMovieType) imdbId else "$imdbId:${download.season}:${download.episode}")
                    }
                    if (kitsuId != null) {
                        add(if (isMovieType) "kitsu:$kitsuId" else "kitsu:$kitsuId:${download.episode}")
                    }
                }

                if (queryIds.isNotEmpty()) {
                    val guessedFilename = runCatching {
                        Uri.parse(download.url).lastPathSegment
                            ?.takeIf { it.isNotBlank() && it.contains('.') }
                            ?.substringBefore('\n')
                            ?.substringBefore('\r')
                            ?.trim()
                    }.getOrNull()

                    val subRepo = SubtitleRepositoryImpl(context)
                    for (queryId in queryIds) {
                        try {
                            val subs = subRepo.getSubtitles(
                                type = type,
                                id = queryId,
                                videoUrl = download.url,
                                videoHeaders = effectiveVideoHeaders,
                                filename = guessedFilename,
                                aniListId = dAniListId,
                                animeTitle = download.animeTitle,
                                episode = download.episode
                            )
                            for (sub in subs.distinctBy { it.url }) {
                                val friendly = PlayerSubtitleUtils.getFriendlyLanguageName(sub.lang)
                                allResolvedSubtitles += SubtitleInput(
                                    url = sub.url,
                                    name = "$friendly (${sub.addonName})",
                                    lang = sub.lang
                                )
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Altyazı sorgusu başarısız ($queryId): ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Harici altyazı çözümleme hatası: ${e.message}")
        }

        val dlLangs = settingsSubtitleLangs
            .split(",")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

        val dedupedSubs = allResolvedSubtitles.distinctBy { it.url }
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

        // 1) Doğrudan altyazı dosyalarını indir
        for (subtitle in filteredSubs) {
            coroutineContext.ensureActive()
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
            try {
                val subHeaders = DownloadHttpClientFactory.resolveSubtitleHeaders(
                    subtitleUrl = subtitle.url,
                    videoUrl = download.url,
                    effectiveVideoHeaders = effectiveVideoHeaders
                )
                downloadSimpleFile(httpClient, subtitle.url, subHeaders, subFile)
                if (subFile.isFile && subFile.length() > 0L) {
                    savedSubs += subFile
                } else {
                    runCatching { subFile.delete() }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Altyazı indirilemedi (${subtitle.name}): ${e.message}")
                runCatching { subFile.delete() }
            }
        }

        // 2) Master listede gömülü HLS #EXT-X-MEDIA:TYPE=SUBTITLES rendisyonlarını indir
        for (hlsSub in hlsSubtitleRenditions) {
            coroutineContext.ensureActive()
            val subUri = hlsSub.uri ?: continue
            val subLang = hlsSub.language?.ifBlank { "tr" } ?: "tr"
            if (dlLangs.isNotEmpty() && dlLangs.none { PlayerSubtitleUtils.matchesLanguageCode(subLang, it) }) {
                continue
            }
            val outVtt = File(
                videoDir,
                DownloadExportNaming.subtitleFileName(baseName, subLang, hlsSub.name, "vtt")
            )
            if (outVtt.exists()) continue
            try {
                downloadHlsSubtitlePlaylistToVtt(httpClient, subUri, outVtt)
                if (outVtt.isFile && outVtt.length() > 0L) {
                    savedSubs += outVtt
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "HLS gömülü altyazı indirilemedi (${hlsSub.name}): ${e.message}")
                runCatching { outVtt.delete() }
            }
        }

        return savedSubs
    }

    private suspend fun downloadHlsSubtitlePlaylistToVtt(
        httpClient: OkHttpClient,
        subUrl: String,
        destVtt: File
    ) {
        val fetched = fetchTextWithUrl(httpClient, subUrl)
        if (!HlsManifestParser.looksLikeHlsContent(fetched.contentType, fetched.body.toByteArray())) {
            destVtt.writeText(fetched.body)
            return
        }
        val media = when (val parsed = HlsManifestParser.parse(fetched.body, fetched.finalUrl)) {
            is HlsManifestParser.ParsedHlsPlaylist.Media -> parsed
            is HlsManifestParser.ParsedHlsPlaylist.Master -> return
        }
        val merged = StringBuilder("WEBVTT\n\n")
        for (seg in media.segments) {
            coroutineContext.ensureActive()
            val segText = fetchTextWithUrl(httpClient, seg.url).body
            val stripped = HlsManifestParser.stripBomAndLeadingWhitespace(segText)
            val bodyWithoutHeader = if (stripped.startsWith("WEBVTT", ignoreCase = true)) {
                stripped.substringAfter('\n', "").trimStart('\r', '\n')
            } else {
                stripped
            }
            if (bodyWithoutHeader.isNotBlank()) {
                merged.append(bodyWithoutHeader.trim()).append("\n\n")
            }
        }
        destVtt.writeText(merged.toString())
    }

    // ── Düşük Seviye Ağ Yardımcıları ──────────────────────────────────────────

    private suspend fun fetchTextWithUrl(httpClient: OkHttpClient, url: String): FetchedText {
        var lastEx: Exception? = null
        for (attempt in 1..3) {
            try {
                val request = Request.Builder().url(url).get().build()
                return httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} ${response.message} ($url)")
                    }
                    val contentType = response.header("Content-Type")
                    val bodyStr = response.body?.string() ?: throw IOException("Boş metin yanıtı")
                    FetchedText(
                        body = bodyStr,
                        finalUrl = response.request.url.toString(),
                        contentType = contentType
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastEx = e
                if (attempt < 3) delay(800L * attempt)
            }
        }
        throw lastEx ?: IOException("Metin indirilemedi: $url")
    }

    private suspend fun fetchEncryptionKey(
        httpClient: OkHttpClient,
        keyUrl: String,
        cache: MutableMap<String, ByteArray>
    ): ByteArray {
        cache[keyUrl]?.let { return it }
        val bytes = fetchSegmentBytesWithRetry(httpClient, keyUrl, null)
        cache[keyUrl] = bytes
        return bytes
    }

    private suspend fun fetchSegmentBytesWithRetry(
        httpClient: OkHttpClient,
        url: String,
        byteRange: HlsManifestParser.ByteRange?
    ): ByteArray {
        var lastEx: Exception? = null
        for (attempt in 1..4) {
            try {
                val builder = Request.Builder().url(url).get()
                if (byteRange != null) {
                    builder.header("Range", byteRange.toHttpRangeHeader())
                }
                return httpClient.newCall(builder.build()).execute().use { response ->
                    if (response.code == 429) {
                        val retryAfterSec = response.header("Retry-After")?.toLongOrNull() ?: 2L
                        throw RateLimitException(retryAfterSec.coerceIn(1L, 10L) * 1000L)
                    }
                    if (!response.isSuccessful) {
                        throw IOException("Segment indirilemedi: HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Boş segment gövdesi")
                    val out = ByteArrayOutputStream(
                        body.contentLength().takeIf { it in 1..(16 * 1024 * 1024) }?.toInt() ?: 65_536
                    )
                    body.byteStream().use { input ->
                        val buf = ByteArray(IO_BUFFER_BYTES)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            coroutineContext.ensureActive()
                            out.write(buf, 0, n)
                        }
                    }
                    val raw = out.toByteArray()
                    // Sunucu Range isteğine rağmen 200 OK ile tüm dosyayı döndürdüyse dilimi kes
                    if (byteRange != null && response.code == 200 &&
                        byteRange.offset + byteRange.length <= raw.size
                    ) {
                        raw.copyOfRange(
                            byteRange.offset.toInt(),
                            (byteRange.offset + byteRange.length).toInt()
                        )
                    } else {
                        raw
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (rle: RateLimitException) {
                lastEx = rle
                delay(rle.waitMs)
            } catch (e: Exception) {
                lastEx = e
                Log.w(TAG, "Segment denemesi $attempt/4 başarısız: ${e.message}")
                if (attempt < 4) delay(750L * attempt)
            }
        }
        throw lastEx ?: IOException("Segment indirilemedi")
    }

    private class RateLimitException(val waitMs: Long) : IOException("HTTP 429 Too Many Requests")

    private suspend fun downloadSimpleFile(
        httpClient: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        destFile: File
    ) {
        val reqBuilder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        httpClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} ${response.message}")
            }
            val body = response.body ?: throw IOException("Boş yanıt")
            body.byteStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private suspend fun downloadPosterIfAvailable(
        httpClient: OkHttpClient,
        url: String?,
        destFile: File
    ) {
        if (url.isNullOrBlank() || !url.startsWith("http", ignoreCase = true)) return
        if (destFile.isFile && destFile.length() > 0L) return
        try {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body ?: return
                body.byteStream().use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Poster indirme atlandı: ${e.message}")
            runCatching { destFile.delete() }
        }
    }

    // ── SAF Kopyalama, Durum Saklama & Yardımcılar ───────────────────────────

    private fun copyToCustomSafIfConfigured(
        customDirUriStr: String,
        finalVideoFile: File,
        finalCompanionAudioFile: File?,
        savedSubs: List<File>
    ) {
        if (customDirUriStr.isBlank()) return
        try {
            val customDirDoc = DocumentFile.fromTreeUri(context, Uri.parse(customDirUriStr))
            if (customDirDoc == null || !customDirDoc.exists()) return

            fun copyFileToSaf(srcFile: File, mimeType: String) {
                customDirDoc.findFile(srcFile.name)?.delete()
                val doc = customDirDoc.createFile(mimeType, srcFile.name) ?: return
                context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                    srcFile.inputStream().use { input -> input.copyTo(out) }
                }
            }

            copyFileToSaf(
                finalVideoFile,
                DownloadExportNaming.mimeTypeForVideoExtension(finalVideoFile.extension)
            )

            if (finalCompanionAudioFile != null && finalCompanionAudioFile.isFile) {
                copyFileToSaf(
                    finalCompanionAudioFile,
                    DownloadExportNaming.mimeTypeForAudioExtension(finalCompanionAudioFile.extension)
                )
            }

            savedSubs.forEach { subFile ->
                runCatching {
                    val subMime = when (subFile.extension.lowercase(Locale.ROOT)) {
                        "ass", "ssa" -> "text/x-ass"
                        "vtt" -> "text/vtt"
                        else -> "application/x-subrip"
                    }
                    copyFileToSaf(subFile, subMime)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Özel SAF klasörüne kopyalama başarısız", e)
        }
    }

    private fun deleteExistingMediaOfBase(videoDir: File, baseName: String) {
        val entries = videoDir.listFiles() ?: return
        for (entry in entries) {
            if (!entry.isFile) continue
            if (DownloadExportNaming.isVideoOfBase(entry.name, baseName) ||
                DownloadExportNaming.isCompanionAudioOfBase(entry.name, baseName)
            ) {
                runCatching { entry.delete() }
            }
        }
    }

    private fun moveOrCopyFile(src: File, dst: File) {
        if (dst.exists() && dst.absolutePath != src.absolutePath) {
            dst.delete()
        }
        if (src.absolutePath == dst.absolutePath) return
        if (!src.renameTo(dst)) {
            src.copyTo(dst, overwrite = true)
            src.delete()
        }
    }

    private fun buildPreferredAudioLanguages(
        download: AnimeDownload,
        settingsPreferred: String
    ): List<String> {
        val userPrefs = settingsPreferred
            .split(",")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
        val sourceLangs = download.audioLanguages.orEmpty()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
        return (userPrefs + sourceLangs + listOf("tr", "ja", "en")).distinct()
    }

    private fun inferAudioKindHint(
        explicitKind: String?,
        streamTitle: String?,
        streamName: String?
    ): String? {
        if (!explicitKind.isNullOrBlank()) return explicitKind
        val combined = "${streamTitle.orEmpty()} ${streamName.orEmpty()}".lowercase(Locale.ROOT)
        return when {
            combined.contains("dual") -> "dual"
            combined.contains("dublaj") || combined.contains("türkçe ses") || combined.contains("turkce ses") -> "dub"
            combined.contains("altyazı") || combined.contains("altyazi") -> "sub"
            else -> null
        }
    }

    private fun guessVideoExtensionFromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        val ext = path.substringAfterLast('.', "")
        return if (ext in DownloadExportNaming.VIDEO_EXTENSIONS) ext else "mp4"
    }

    private fun looksLikeHtmlDocument(contentType: String?, sample: String): Boolean {
        val ct = contentType?.lowercase(Locale.ROOT).orEmpty()
        if (ct.contains("text/html") || ct.contains("application/xhtml")) return true
        val trimmed = HlsManifestParser.stripBomAndLeadingWhitespace(sample).take(256).lowercase(Locale.ROOT)
        return trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html")
    }

    /**
     * Google Drive virüs tarama onay sayfasından (`download-form`) doğrudan indirme URL'sini çıkarır.
     */
    private fun extractGoogleDriveConfirmUrl(html: String, baseUrl: String): String? {
        if (!baseUrl.contains("google", ignoreCase = true) && !html.contains("download-form")) {
            return null
        }
        val formAction = Regex("""<form[^>]*id=["']download-form["'][^>]*action=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.replace("&amp;", "&")
            ?: return null

        val params = mutableListOf<String>()
        val inputRegex = Regex("""<input[^>]*type=["']hidden["'][^>]*>""", RegexOption.IGNORE_CASE)
        val nameRegex = Regex("""name=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val valRegex = Regex("""value=["']([^"']*)["']""", RegexOption.IGNORE_CASE)

        for (m in inputRegex.findAll(html)) {
            val tag = m.value
            val name = nameRegex.find(tag)?.groupValues?.getOrNull(1) ?: continue
            val value = valRegex.find(tag)?.groupValues?.getOrNull(1).orEmpty()
            params += "${ java.net.URLEncoder.encode(name, "UTF-8") }=${ java.net.URLEncoder.encode(value, "UTF-8") }"
        }
        val sep = if (formAction.contains('?')) "&" else "?"
        val resolvedAction = HlsManifestParser.resolveUrl(baseUrl, formAction)
        return if (params.isEmpty()) resolvedAction else "$resolvedAction$sep${params.joinToString("&")}"
    }

    private fun readStagingState(file: File): StagingResumeState? {
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            StagingResumeState(
                sourceUrl = json.optString("sourceUrl", ""),
                externalAudioUrl = json.optString("externalAudioUrl", ""),
                videoPlaylistUrl = json.optString("videoPlaylistUrl", ""),
                audioPlaylistUrl = json.optString("audioPlaylistUrl", ""),
                videoSegmentsDone = json.optInt("videoSegmentsDone", 0),
                audioSegmentsDone = json.optInt("audioSegmentsDone", 0),
                firstAudioId3PtsUs = if (json.has("firstAudioId3PtsUs")) json.optLong("firstAudioId3PtsUs") else null
            )
        }.getOrNull()
    }

    private fun writeStagingState(file: File, state: StagingResumeState) {
        runCatching {
            val json = JSONObject().apply {
                put("sourceUrl", state.sourceUrl)
                put("externalAudioUrl", state.externalAudioUrl)
                put("videoPlaylistUrl", state.videoPlaylistUrl)
                put("audioPlaylistUrl", state.audioPlaylistUrl)
                put("videoSegmentsDone", state.videoSegmentsDone)
                put("audioSegmentsDone", state.audioSegmentsDone)
                state.firstAudioId3PtsUs?.let { put("firstAudioId3PtsUs", it) }
            }
            file.writeText(json.toString())
        }
    }

    // ── Harici İndirme Yöneticileri (1DM / ADM / Tarayıcı) ────────────────────

    private fun downloadExternal(
        download: AnimeDownload,
        downloaderPref: String,
        onStatusChanged: (AnimeDownload.Status, String?, String?) -> Unit
    ) {
        val pm = context.packageManager
        val cleanTitle = DownloadExportNaming.sanitizeTitle(download.animeTitle)
        val filename = KitsugiStrings.get(R.string.x_bolum_x_mp4, cleanTitle, download.episode)
        val effectiveHeaders = DownloadHttpClientFactory.buildEffectiveHeaders(
            rawHeaders = download.requestHeaders,
            videoUrl = download.url
        )

        try {
            val intent: Intent
            when (downloaderPref) {
                "EXTERNAL_1DM" -> {
                    val pkgName = if (isPackageInstalled(pm, "idm.internet.download.manager.plus")) {
                        "idm.internet.download.manager.plus"
                    } else {
                        "idm.internet.download.manager"
                    }
                    if (!isPackageInstalled(pm, pkgName)) {
                        throw IOException("1DM not installed")
                    }
                    val headersBundle = Bundle().apply {
                        effectiveHeaders.forEach { (k, v) -> putString(k, v) }
                    }
                    intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(download.url)
                        setPackage(pkgName)
                        putExtra("extra_filename", filename)
                        putExtra("extra_headers", headersBundle)
                        putExtra("headers", headersBundle)
                        effectiveHeaders["Referer"]?.let { putExtra("extra_referer", it) }
                        effectiveHeaders["User-Agent"]?.let { putExtra("extra_useragent", it) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                "EXTERNAL_ADM" -> {
                    val pkgName = if (isPackageInstalled(pm, "com.dv.adm.pay")) {
                        "com.dv.adm.pay"
                    } else {
                        "com.dv.adm"
                    }
                    if (!isPackageInstalled(pm, pkgName)) {
                        throw IOException("ADM not installed")
                    }
                    intent = Intent(Intent.ACTION_VIEW).apply {
                        component = ComponentName(pkgName, "$pkgName.AEditor")
                        putExtra("com.dv.get.ACTION_LIST_ADD", "${download.url}<info>$filename")
                        effectiveHeaders["User-Agent"]?.let { putExtra("com.dv.get.USER_AGENT", it) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                else -> {
                    intent = Intent(Intent.ACTION_VIEW).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setDataAndType(Uri.parse(download.url), "video/*")
                        putExtra("extra_filename", filename)
                    }
                }
            }
            context.startActivity(intent)
            onStatusChanged(AnimeDownload.Status.COMPLETED, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch external downloader", e)
            onStatusChanged(AnimeDownload.Status.ERROR, null, null)
        }
    }

    private fun isPackageInstalled(pm: android.content.pm.PackageManager, pkgName: String): Boolean {
        return try {
            pm.getPackageInfo(pkgName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildErrorMessage(e: Exception, kind: DownloadResilience.ErrorKind): String {
        val raw = e.message?.lineSequence()?.firstOrNull()?.take(220)
        return when (kind) {
            DownloadResilience.ErrorKind.DNS ->
                "Sunucu adresi çözülemedi (CDN host'u ölmüş olabilir). Tekrar deneyin; uygulama adresi otomatik tazelemeyi denedi. ${raw ?: ""}"
            DownloadResilience.ErrorKind.RATE_LIMIT ->
                "Kaynak hız sınırı (429) uyguladı. Birkaç dakika sonra yeniden deneyin. ${raw ?: ""}"
            DownloadResilience.ErrorKind.STALE_URL ->
                "Video adresi bayat (404/410). Bölümü silip yeniden kuyruğa eklemek genellikle çözer. ${raw ?: ""}"
            else -> raw ?: e.javaClass.simpleName
        }.trim()
    }

    // ── Standalone Audio Download Pipeline ──────────────────────────────────────

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
        val concurrency = if (policy.minRequestIntervalMs > 100L) 1 else 4
        val gate = Semaphore(concurrency)

        BufferedOutputStream(FileOutputStream(destFile, false), 128 * 1024).use { out ->
            for (batch in media.segments.chunked(16)) {
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

            val gate = Semaphore(4)
            for (batch in plan.mediaSegments.chunked(16)) {
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

    // ── Küçük video (klip) indirme ────────────────────────────────────────────

    /**
     * Detay sayfasındaki küçük videoyu indirir.
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

            runCatching { if (target.exists()) target.delete() }
            val moved = runCatching { partFile.renameTo(target) }.getOrDefault(false)
            if (!moved) {
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
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                runCatching { partFile.delete() }
            }
            onStatusChanged(AnimeDownload.Status.PAUSED, null)
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Klip indirme hatası (${download.clipKey})", e)
            runCatching { partFile.delete() }
            val reason = when {
                e is IOException && e !is ClipHttpException -> {
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

    private fun findExistingClipFile(dir: File, baseName: String): File? {
        if (!dir.isDirectory) return null
        val videoExts = setOf("mp4", "webm", "m4v", "mkv", "ts", "mov")
        return runCatching {
            dir.listFiles()?.firstOrNull { file ->
                file.isFile &&
                    file.name.startsWith("$baseName.", ignoreCase = true) &&
                    file.extension.lowercase(Locale.ROOT) in videoExts
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
                        runCatching { partFile.delete() }
                        throw IllegalStateException("416 Range Not Satisfiable")
                    }
                    if (!response.isSuccessful) {
                        throw ClipHttpException(
                            response.code,
                            KitsugiStrings.get(R.string.kitsugi_clip_error_http, response.code)
                        )
                    }
                    val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
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
                        BufferedOutputStream(
                            FileOutputStream(partFile, append),
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
                                val now = SystemClock.elapsedRealtime()
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Klip indirme denemesi $attempt başarısız: ${e.message}")
                val httpCode = (e as? ClipHttpException)?.code ?: 0
                if (httpCode in 400..499 && httpCode != 408 && httpCode != 429) break
                if (attempt < 3) delay(1_500L * attempt)
            }
        }
        throw lastError ?: IllegalStateException(
            KitsugiStrings.get(R.string.kitsugi_clip_error_generic)
        )
    }

    private suspend fun downloadHlsClip(
        manifestUrl: String,
        partFile: File,
        headers: Map<String, String>,
        onProgress: (Int, Long, Long, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        suspend fun fetchPlaylist(url: String): String = try {
            getUrlContent(url, headers)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
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

        runCatching { partFile.delete() }
        var bytesDownloaded = 0L
        var lastEmit = 0L
        var lastProgress = -1

        BufferedOutputStream(
            FileOutputStream(partFile, false),
            64 * 1024
        ).use { output ->
            if (initSegmentUrl != null) {
                bytesDownloaded += downloadSegmentToStream(initSegmentUrl!!, headers, output)
            }
            segments.forEachIndexed { index, segmentUrl ->
                coroutineContext.ensureActive()
                bytesDownloaded += downloadSegmentToStream(segmentUrl, headers, output)

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
                val now = SystemClock.elapsedRealtime()
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
        outputStream: OutputStream
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
                return@withContext copiedBytes
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e
                Log.w(TAG, "Segment download attempt $attempt failed: ${e.message}")
                if (attempt < 3) {
                    delay(1000L * attempt)
                }
            }
        }
        throw lastException ?: Exception("Unknown error downloading segment")
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        try {
            val baseUri = URI(baseUrl)
            val resolvedUri = baseUri.resolve(relativeUrl)
            return resolvedUri.toString()
        } catch (_: Exception) {
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
