package com.kitsugi.animelist.core.download

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
import com.kitsugi.animelist.core.player.PlayerMediaSourceFactory
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
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * İndirme tamamlandı ama dosya bu cihazda oynatılamıyor (desteklenmeyen codec).
 */
class UnsupportedMediaException(message: String) : IOException(message)

/**
 * Evrensel bölüm (video+ses) ve bağımsız ses indirme motoru (T-IND dayanıklılık mimarisi).
 *
 * - Rotasyonlu CDN host kurtarma (`recoverUrl` + `DownloadStreamResolver`)
 * - Çökmeye ve ağ kesintisine dayanıklı segment defteri (`SegmentJournal`) ile mükemmel resume hizası
 * - HTTP 429 hız sınırı pacing ve Retry-After yönetimi (`DownloadResilience`)
 * - BYTERANGE, EXT-X-MAP, AES-128 şifre çözme (`HlsPlaylistParser`)
 * - Cihaz codec uyumluluğu ve indirme sonrası dosya doğrulaması (`DeviceCodecSupport` + `DownloadVerifier`)
 */
class AnimeDownloader(private val context: Context) {

    companion object {
        private const val TAG = "AnimeDownloader"
        private const val MAX_CLIP_BYTES = 2L * 1024L * 1024L * 1024L
    }

    /** onStatusChanged(status, localPath, errorMessage) */
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

        // Bağımsız ses indirmeleri her zaman dahili motorla indirilir
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
        val preferredAudioLangs = settings.preferredAudioLanguages
            .split(',')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("tr", "ja", "en") }

        // Bağımsız ses indirme akışı
        if (download.effectiveKind == DownloadKind.AUDIO) {
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

        // Video indirme akışı
        val safeAnimeId = download.animeId.ifBlank {
            download.animeTitle.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]"), "_")
                .trim('_')
                .take(30)
                .ifBlank { "media" }
        }
        val mediaId = "${safeAnimeId}_ep${download.episode}"
        val outFilename = DownloadExportNaming.exportFileName(download.animeTitle, download.episode)
        val baseName = outFilename.removeSuffix(".mp4")
        val videoDir = OfflinePlaybackHelper.getDownloadsDir(context)
        val localFile = File(videoDir, outFilename)

        val policy = DownloadResilience.policyFor(download.source, download.url)

        try {
            val isHls = download.url.contains(".m3u8", ignoreCase = true) ||
                download.url.contains("master.txt", ignoreCase = true) ||
                download.url.contains("playlist.txt", ignoreCase = true) ||
                PlayerMediaSourceFactory.isHlsUrl(download.url)

            if (isHls) {
                downloadHls(download, policy, localFile, baseName, onProgress)
            } else {
                downloadDirectFile(download.url, download.requestHeaders, localFile, { p, b, t ->
                    onProgress(p, b, t, 0)
                }, download, policy)
            }

            finishDownload(download, settings, mediaId, baseName, videoDir, localFile, onStatusChanged)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Video indirme başarısız", e)
            val kind = DownloadResilience.classify(e)
            val message = buildErrorMessage(e, kind)
            onStatusChanged(AnimeDownload.Status.ERROR, null, message)
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

    // ── HLS İndirme Motoru (SegmentJournal + Re-resolve + AES-128 + BYTERANGE) ──

    private class HlsPlan(
        val mediaPlaylistUrl: String,
        val segments: List<HlsPlaylistParser.Segment>,
        val initSegmentUrl: String?,
        val unsupportedReason: String? = null
    )

    private suspend fun downloadHls(
        download: AnimeDownload,
        policy: DownloadResilience.SourceDownloadPolicy,
        localFile: File,
        baseName: String,
        onProgress: (Int, Long, Long, Int) -> Unit
    ) {
        var videoUrl = download.url
        var headers = download.requestHeaders
        var reResolveCount = 0

        fun canReResolve(): Boolean =
            policy.allowReResolve &&
                reResolveCount < policy.maxReResolve &&
                !download.recoverUrl.isNullOrBlank()

        suspend fun fetchPlan(url: String, hdrs: Map<String, String>): HlsPlan {
            var lastError: Exception? = null
            for (attempt in 1..policy.playlistAttempts) {
                coroutineContext.ensureActive()
                try {
                    return buildPlan(url, hdrs)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    lastError = e
                    val kind = DownloadResilience.classify(e)
                    Log.w(
                        TAG,
                        "playlist deneme $attempt/${policy.playlistAttempts} başarısız (${kind}): ${e.message}"
                    )
                    if (attempt < policy.playlistAttempts) {
                        delay(waitMs(e, attempt, policy))
                    }
                }
            }
            throw lastError ?: Exception("Playlist indirilemedi")
        }

        var plan = fetchPlan(videoUrl, headers)
        plan.unsupportedReason?.let { throw Exception(it) }
        var knownSegments = plan.segments.map { it.url }

        val keyCache = HashMap<String, ByteArray>()
        val journalFile = File(localFile.parentFile, DownloadExportNaming.journalFileName(baseName))
        var journalOut: FileOutputStream? = null

        fun journalWrite(line: String) {
            runCatching {
                journalOut?.write((line + "\n").toByteArray(Charsets.US_ASCII))
                journalOut?.flush()
            }
        }

        fun journalClose() {
            runCatching { journalOut?.close() }
            journalOut = null
        }

        fun journalRestart(initBytes: Long) {
            journalClose()
            journalFile.delete()
            journalOut = runCatching { FileOutputStream(journalFile, false) }.getOrNull()
            journalWrite(
                SegmentJournal.headerLine(
                    planKey = DownloadResilience.segmentPathKey(plan.segments.first().url),
                    total = plan.segments.size,
                    initBytes = initBytes
                )
            )
        }

        val planKey = DownloadResilience.segmentPathKey(plan.segments.first().url)
        val hasFile = localFile.exists() && localFile.length() > 0
        var segmentIndex = 0
        var appendMode = false

        if (hasFile && journalFile.isFile) {
            val state = runCatching { SegmentJournal.parse(journalFile.readText()) }
                .getOrDefault(SegmentJournal.EMPTY)
            val header = state.header
            var contiguous = 0
            var contiguousBytes = 0L
            for ((idx, size) in state.entries) {
                if (idx != contiguous) break
                contiguous++
                contiguousBytes += size
            }
            val resumable = header != null &&
                header.planKey == planKey &&
                header.total == plan.segments.size &&
                contiguous > 0

            if (resumable && header != null) {
                val expected = header.initBytes + contiguousBytes
                if (localFile.length() != expected) {
                    runCatching {
                        RandomAccessFile(localFile, "rw").use { it.setLength(expected) }
                    }
                    Log.w(
                        TAG,
                        "journal: dosya boyu uyuşmadı → ${localFile.length()} yerine $expected bayta kırpıldı"
                    )
                }
                segmentIndex = contiguous
                appendMode = true
                journalOut = runCatching { FileOutputStream(journalFile, true) }.getOrNull()
                Log.i(
                    TAG,
                    "journal resume: dilim=$segmentIndex/${plan.segments.size}, bayt=${localFile.length()}"
                )
            } else {
                journalFile.delete()
            }
        } else if (hasFile) {
            Log.w(TAG, "journal yok — kısmi dosya doğrulanamıyor, baştan indiriliyor")
        }

        if (segmentIndex >= plan.segments.size) {
            segmentIndex = 0
            appendMode = false
            journalClose()
            journalFile.delete()
        }

        var bytesDownloaded = if (appendMode) localFile.length() else 0L
        var lastProgressEmitMs = 0L
        var lastReportedProgress = -1
        var lastSegmentStartMs = 0L

        var out: BufferedOutputStream? = null
        fun openStream(append: Boolean) {
            out?.runCatching { flush(); close() }
            out = BufferedOutputStream(FileOutputStream(localFile, append), 64 * 1024)
        }

        fun closeStream() {
            out?.runCatching { flush(); close() }
            out = null
        }

        suspend fun writeInitSegment(): Long {
            val init = plan.initSegmentUrl ?: return 0L
            val bytes = downloadSegmentBytes(HlsPlaylistParser.Segment(url = init), headers, policy)
            out?.write(bytes)
            out?.flush()
            bytesDownloaded += bytes.size
            return bytes.size.toLong()
        }

        suspend fun recover(e: Exception, atIndex: Int): Int? {
            val kind = DownloadResilience.classify(e)
            if (!DownloadResilience.worthReResolving(kind) || !canReResolve()) return null
            reResolveCount++
            val fresh = DownloadStreamResolver.reResolve(
                recoverUrl = download.recoverUrl,
                recoverReferer = download.recoverReferer,
                currentUrl = videoUrl
            ) ?: return null

            val freshPlan = runCatching { fetchPlan(fresh.url, fresh.headers) }.getOrNull()
                ?: return null
            if (freshPlan.unsupportedReason != null) return null

            val resumeAt = DownloadResilience.reconcileResumeIndex(
                oldSegmentUrls = knownSegments,
                newSegmentUrls = freshPlan.segments.map { it.url },
                currentIndex = atIndex
            )
            Log.i(
                TAG,
                "adres tazelendi: eski host=${hostOf(videoUrl)} yeni host=${hostOf(fresh.url)} " +
                    "resume@$resumeAt (eski indis $atIndex)"
            )
            videoUrl = fresh.url
            headers = fresh.headers
            knownSegments = freshPlan.segments.map { it.url }
            plan = freshPlan
            if (resumeAt == 0) {
                closeStream()
                openStream(append = false)
                bytesDownloaded = 0L
                journalRestart(writeInitSegment())
            }
            return resumeAt
        }

        try {
            if (appendMode) {
                openStream(append = true)
            } else {
                openStream(append = false)
            }

            if (!appendMode) {
                try {
                    journalRestart(writeInitSegment())
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    segmentIndex = recover(e, 0) ?: throw e
                }
            }

            while (segmentIndex < plan.segments.size) {
                coroutineContext.ensureActive()

                if (policy.minSegmentIntervalMs > 0 && lastSegmentStartMs > 0L) {
                    val elapsed = SystemClock.elapsedRealtime() - lastSegmentStartMs
                    val wait = policy.minSegmentIntervalMs - elapsed
                    if (wait > 0) delay(wait)
                }
                lastSegmentStartMs = SystemClock.elapsedRealtime()

                val seg = plan.segments[segmentIndex]
                val segmentBytes = try {
                    val raw = downloadSegmentBytes(seg, headers, policy)
                    if (seg.keyUri != null) decryptSegment(raw, seg, headers, keyCache) else raw
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(
                        TAG,
                        "segment $segmentIndex başarısız (${DownloadResilience.classify(e)}): ${e.message} — politika=${policy.name}"
                    )
                    val resumed = recover(e, segmentIndex) ?: throw e
                    segmentIndex = resumed
                    onProgress(
                        (100 * segmentIndex / plan.segments.size).coerceIn(0, 100),
                        bytesDownloaded, 0L, segmentIndex
                    )
                    continue
                }

                out?.write(segmentBytes)
                out?.flush()
                journalWrite(SegmentJournal.entryLine(segmentIndex, segmentBytes.size.toLong()))
                bytesDownloaded += segmentBytes.size
                segmentIndex++

                val progress = (100 * segmentIndex / plan.segments.size).coerceIn(0, 100)
                val now = SystemClock.elapsedRealtime()
                val isFinalSegment = segmentIndex == plan.segments.size
                if (isFinalSegment ||
                    now - lastProgressEmitMs >= 250L ||
                    (progress != lastReportedProgress && now - lastProgressEmitMs >= 100L)
                ) {
                    lastProgressEmitMs = now
                    lastReportedProgress = progress
                    onProgress(progress, bytesDownloaded, 0L, segmentIndex)
                }
            }
            out?.flush()
            journalClose()
            runCatching { journalFile.delete() }
        } finally {
            closeStream()
            journalClose()
        }
    }

    private fun hostOf(url: String?): String? = try {
        URI(url).host
    } catch (_: Exception) {
        null
    }

    private suspend fun buildPlan(url: String, headers: Map<String, String>): HlsPlan {
        val content = getUrlContent(url, headers)
        val parsed = if (HlsPlaylistParser.isMasterPlaylist(content)) {
            val master = HlsPlaylistParser.parseMaster(content, url)
            val variant = master?.let { HlsPlaylistParser.selectVariant(it) }?.url
                ?: HlsPlaylistParser.pickVariantUrl(content, url)
                ?: throw Exception("Master playlist: varyant seçilemedi")
            val mediaContent = getUrlContent(variant, headers)
            HlsPlaylistParser.parseMediaPlaylist(mediaContent, variant)
        } else {
            HlsPlaylistParser.parseMediaPlaylist(content, url)
        }
        if (parsed.segments.isEmpty()) {
            throw Exception("No segments found in HLS playlist")
        }
        return HlsPlan(parsed.mediaPlaylistUrl, parsed.segments, parsed.initSegmentUrl, parsed.unsupportedReason)
    }

    // ── Ortak Bitiş ve Doğrulama Adımları ───────────────────────────────────────

    private suspend fun finishDownload(
        download: AnimeDownload,
        settings: com.kitsugi.animelist.data.settings.AppSettings,
        mediaId: String,
        baseName: String,
        videoDir: File,
        localFile: File,
        onStatusChanged: (AnimeDownload.Status, String?, String?) -> Unit
    ) {
        val allResolvedSubtitles = mutableListOf<SubtitleInput>()
        if (!download.subtitles.isNullOrEmpty()) {
            allResolvedSubtitles.addAll(download.subtitles)
        }

        try {
            val dMalId = download.malId
            val dAniListId = download.aniListId
            val dTmdbId = download.tmdbId ?: if (dMalId == null && dAniListId == null) download.animeId.toIntOrNull() else null

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
                    for (queryId in queryIds) {
                        try {
                            val subs = subRepo.getSubtitles(
                                type = type,
                                id = queryId,
                                videoUrl = download.url,
                                videoHeaders = download.requestHeaders,
                                filename = guessedFilename,
                                aniListId = dAniListId,
                                animeTitle = download.animeTitle,
                                episode = download.episode
                            )
                            for (sub in subs) {
                                val friendlyLangName = PlayerSubtitleUtils.getFriendlyLanguageName(sub.lang)
                                allResolvedSubtitles.add(
                                    SubtitleInput(
                                        url = sub.url,
                                        name = "$friendlyLangName (${sub.addonName})",
                                        lang = sub.lang
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch subtitles for queryId=$queryId", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve subtitles in downloader", e)
        }

        val dedupedSubs = allResolvedSubtitles.distinctBy { it.url }
        val dlLangs = settings.subtitleDownloadLanguages
            .split(",")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

        val filteredSubs = if (dlLangs.isEmpty()) {
            dedupedSubs
        } else {
            dedupedSubs.filter { sub ->
                dlLangs.any { targetLang ->
                    PlayerSubtitleUtils.matchesLanguageCode(sub.lang ?: "", targetLang)
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
            try {
                val useHeaders = try {
                    val subHost = URI(subtitle.url).host
                    val videoHost = URI(download.url).host
                    if (subHost != null && subHost.equals(videoHost, true)) {
                        download.requestHeaders
                    } else {
                        emptyMap()
                    }
                } catch (_: Exception) {
                    emptyMap()
                }

                downloadSubtitleFile(subtitle.url, useHeaders, subFile)
                savedSubs += subFile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download subtitle: ${subtitle.name}", e)
                runCatching { subFile.delete() }
            }
        }

        val metaFile = File(videoDir, DownloadExportNaming.metadataFileName(baseName))
        val metaJson = JSONObject().apply {
            put("mediaId", mediaId)
            put("title", "${download.animeTitle} - Bölüm ${download.episode}")
            put("durationMs", 0)
            put("downloadedAtMs", System.currentTimeMillis())
        }
        metaFile.writeText(metaJson.toString(2))

        val posterFile = File(
            DownloadExportNaming.publicImagesDir().also { it.mkdirs() },
            DownloadExportNaming.posterFileName(baseName)
        )
        downloadPosterIfAvailable(download.posterUrl, posterFile)

        val customDirUriStr = settings.videoDownloadUri
        if (customDirUriStr.isNotBlank()) {
            try {
                val customDirDoc = DocumentFile.fromTreeUri(context, Uri.parse(customDirUriStr))
                if (customDirDoc != null && customDirDoc.exists()) {
                    customDirDoc.findFile(localFile.name)?.delete()
                    val newFileDoc = customDirDoc.createFile("video/mp4", localFile.name)
                    newFileDoc?.uri?.let { destUri ->
                        context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                            localFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                    }

                    savedSubs.forEach { subFile ->
                        try {
                            val subMime = when (subFile.extension.lowercase(Locale.ROOT)) {
                                "ass", "ssa" -> "text/x-ass"
                                "vtt" -> "text/vtt"
                                else -> "application/x-subrip"
                            }
                            customDirDoc.findFile(subFile.name)?.delete()
                            val subDoc = customDirDoc.createFile(subMime, subFile.name)
                            subDoc?.uri?.let { subUri ->
                                context.contentResolver.openOutputStream(subUri)?.use { subOutStream ->
                                    subFile.inputStream().use { subInStream ->
                                        subInStream.copyTo(subOutStream)
                                    }
                                }
                            }
                        } catch (subEx: Exception) {
                            Log.e(TAG, "Failed to copy subtitle ${subFile.name} to custom SAF directory", subEx)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy video to custom SAF directory", e)
            }
        }

        // Cihaz oynatma doğrulaması
        val verification = DownloadVerifier.verify(localFile)
        if (!verification.playable) {
            Log.w(TAG, "İndirilen dosya doğrulanamadı: ${verification.warning} (${verification.details})")
            runCatching { localFile.delete() }
            throw UnsupportedMediaException(
                verification.warning ?: KitsugiStrings.get(R.string.indirme_hata_desteklenmeyen_format)
            )
        }

        val toScan = buildList {
            add(localFile)
            addAll(savedSubs)
            if (posterFile.isFile) add(posterFile)
        }.map { it.absolutePath }.toTypedArray()
        MediaScannerConnection.scanFile(context, toScan, null, null)

        onStatusChanged(AnimeDownload.Status.COMPLETED, localFile.absolutePath, null)
    }

    // ── HTTP Yardımcıları ────────────────────────────────────────────────────────

    private fun httpError(response: Response, what: String): DownloadResilience.SegmentHttpException {
        val retryAfter = DownloadResilience.parseRetryAfterMs(response.header("Retry-After"))
        return DownloadResilience.SegmentHttpException(
            code = response.code,
            retryAfterMs = retryAfter,
            message = "$what: ${response.code} ${response.message}"
        )
    }

    private fun waitMs(e: Exception, attempt: Int, policy: DownloadResilience.SourceDownloadPolicy): Long {
        val ra = (e as? DownloadResilience.SegmentHttpException)?.retryAfterMs
        return if (ra != null) minOf(ra, policy.retryAfterCapMs) else DownloadResilience.backoffMs(attempt, policy)
    }

    private suspend fun getUrlContent(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        KitsugiHttpClient.downloadClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw httpError(response, "Failed to fetch HLS playlist")
            }
            response.body?.string() ?: throw Exception("Empty playlist content")
        }
    }

    private suspend fun downloadSegmentBytes(
        seg: HlsPlaylistParser.Segment,
        headers: Map<String, String>,
        policy: DownloadResilience.SourceDownloadPolicy
    ): ByteArray = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (attempt in 1..policy.segmentAttempts) {
            try {
                val requestBuilder = Request.Builder().url(seg.url)
                headers.forEach { (k, v) -> requestBuilder.header(k, v) }
                if (seg.hasRange) {
                    requestBuilder.header(
                        "Range",
                        "bytes=${seg.rangeStart}-${seg.rangeStart + seg.rangeLen - 1}"
                    )
                }
                KitsugiHttpClient.downloadClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (seg.hasRange && response.code != 206) {
                        throw httpError(response, "Range istendi ama 206 dönmedi")
                    }
                    if (!response.isSuccessful) {
                        throw httpError(response, "Failed to download segment")
                    }
                    val body = response.body ?: throw Exception("Empty segment body")
                    return@withContext body.byteStream().readBytes()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e
                Log.w(
                    TAG,
                    "Segment download attempt $attempt/${policy.segmentAttempts} failed (${DownloadResilience.classify(e)}): ${e.message}"
                )
                if (attempt < policy.segmentAttempts) {
                    delay(waitMs(e, attempt, policy))
                }
            }
        }
        throw lastException ?: Exception("Unknown error downloading segment")
    }

    private suspend fun decryptSegment(
        encrypted: ByteArray,
        seg: HlsPlaylistParser.Segment,
        headers: Map<String, String>,
        keyCache: HashMap<String, ByteArray>
    ): ByteArray {
        val keyUri = seg.keyUri ?: return encrypted
        val key = keyCache[keyUri] ?: run {
            val useHeaders = if (hostOf(keyUri).equals(hostOf(seg.url), true)) headers else emptyMap()
            val fetched = fetchKey(keyUri, useHeaders)
            if (fetched.size != 16) {
                throw Exception("Geçersiz AES-128 anahtar boyutu: ${fetched.size} (beklenen 16)")
            }
            keyCache[keyUri] = fetched
            fetched
        }
        val iv = HlsPlaylistParser.ivBytesFor(seg)
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.IvParameterSpec(iv)
        )
        return cipher.doFinal(encrypted)
    }

    private suspend fun fetchKey(url: String, headers: Map<String, String>): ByteArray = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        KitsugiHttpClient.downloadClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw httpError(response, "Failed to fetch HLS key")
            }
            response.body?.bytes() ?: throw Exception("Empty key body")
        }
    }

    private suspend fun downloadDirectFile(
        url: String,
        headers: Map<String, String>,
        localFile: File,
        onProgress: (Int, Long, Long) -> Unit,
        download: AnimeDownload,
        policy: DownloadResilience.SourceDownloadPolicy
    ) = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = policy.segmentAttempts.coerceAtLeast(3)
        while (attempts < maxAttempts) {
            attempts++
            try {
                val existingLength = if (localFile.exists()) localFile.length() else 0L
                val requestBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> requestBuilder.header(k, v) }

                if (existingLength > 0) {
                    requestBuilder.header("Range", "bytes=$existingLength-")
                }

                KitsugiHttpClient.downloadClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 416) {
                            localFile.delete()
                            throw DownloadResilience.SegmentHttpException(416, null, "416 Range Not Satisfiable")
                        }
                        throw httpError(response, "Failed to download file")
                    }

                    val isRange = response.code == 206
                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    val totalBytes = if (isRange) contentLength + existingLength else contentLength
                    val appendMode = isRange && existingLength > 0

                    body.byteStream().use { inputStream ->
                        BufferedOutputStream(FileOutputStream(localFile, appendMode), 64 * 1024).use { outputStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var bytesDownloaded = if (appendMode) existingLength else 0L
                            var lastProgressEmitMs = 0L
                            var lastReportedProgress = -1
                            var lastReportedBytes = -1L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                coroutineContext.ensureActive()
                                outputStream.write(buffer, 0, bytesRead)
                                bytesDownloaded += bytesRead

                                val progress = if (totalBytes > 0) {
                                    (100 * bytesDownloaded / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastProgressEmitMs >= 250L ||
                                    (progress != lastReportedProgress && now - lastProgressEmitMs >= 100L)
                                ) {
                                    lastProgressEmitMs = now
                                    lastReportedProgress = progress
                                    lastReportedBytes = bytesDownloaded
                                    onProgress(progress, bytesDownloaded, totalBytes)
                                }
                            }
                            outputStream.flush()

                            val finalProgress = if (totalBytes > 0) {
                                (100 * bytesDownloaded / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                100
                            }
                            if (bytesDownloaded != lastReportedBytes || finalProgress != lastReportedProgress) {
                                onProgress(finalProgress, bytesDownloaded, totalBytes)
                            }
                        }
                    }
                }
                return@withContext
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Direct download attempt $attempts failed: ${e.message}")
                if (attempts >= maxAttempts) {
                    throw e
                }
                delay(waitMs(e, attempts, policy))
            }
        }
    }

    private suspend fun downloadSubtitleFile(
        url: String,
        headers: Map<String, String>,
        destFile: File
    ) = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        KitsugiHttpClient.downloadClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw httpError(response, "Failed to download subtitle")
            }
            val body = response.body ?: throw Exception("Empty subtitle response body")
            body.byteStream().use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
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
        }.onFailure { e ->
            Log.w(TAG, "Poster indirme atlandı: ${e.message}")
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
