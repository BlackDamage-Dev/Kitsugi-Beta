package com.kitsugi.animelist.core.download

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.documentfile.provider.DocumentFile
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.core.player.OfflinePlaybackHelper
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import android.media.MediaScannerConnection
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Küçük video indirmeleri için güvenlik tavanı. Bir klip (fragman/kamera arkası) pratikte
 * birkaç yüz MB'ı geçmez; sunucu yanlış bir akış (ör. koskoca bir canlı yayın arşivi)
 * döndürürse cihazın depolamasını doldurmasın.
 */
private const val MAX_CLIP_BYTES = 2L * 1024L * 1024L * 1024L

class AnimeDownloader(private val context: Context) {

    suspend fun download(
        download: AnimeDownload,
        onProgress: (Int, Long, Long, Int) -> Unit,
        onStatusChanged: (AnimeDownload.Status, String?) -> Unit
    ) {
        // Küçük videolar (fragman / kamera arkası / tema) her zaman uygulama içi indirilir:
        // harici indiricilere YouTube sayfa bağlantısı vermek işe yaramaz.
        if (download.isClip) {
            downloadClip(download, onProgress, onStatusChanged)
            return
        }

        val settings = SettingsDataStore(context).settingsFlow.first()
        val downloaderPref = settings.downloaderPreference

        if (downloaderPref == "INTERNAL") {
            downloadInternal(download, onProgress, onStatusChanged)
        } else {
            downloadExternal(download, downloaderPref, onStatusChanged)
        }
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

    private suspend fun downloadInternal(
        download: AnimeDownload,
        onProgress: (Int, Long, Long, Int) -> Unit,
        onStatusChanged: (AnimeDownload.Status, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        onStatusChanged(AnimeDownload.Status.DOWNLOADING, null)

        val settings = SettingsDataStore(context).settingsFlow.first()
        val safeAnimeId = download.animeId.ifBlank {
            download.animeTitle.lowercase()
                .replace(Regex("[^a-z0-9]"), "_")
                .trim('_')
                .take(30)
                .ifBlank { "media" }
        }
        val mediaId = "${safeAnimeId}_ep${download.episode}"

        // Dosya adları indirmeden ÖNCE tek seferde üretilir; silme akışı da aynı adı kullanır.
        val outFilename = DownloadExportNaming.exportFileName(download.animeTitle, download.episode)
        val baseName = outFilename.removeSuffix(".mp4")
        // Video doğrudan nihai konuma yazılır (Downloads/Kitsugi/Video/<Başlık>_Bölüm_N.mp4).
        // Ayrı çalışma klasörü ve sonradan kopyalama yok → çift dosya oluşmaz.
        val videoDir = OfflinePlaybackHelper.getDownloadsDir(context)
        val localFile = File(videoDir, outFilename)

        try {
            val isHls = download.url.contains(".m3u8", ignoreCase = true)
            if (isHls) {
                // Fetch the main playlist
                val playlistContent = getUrlContent(download.url, download.requestHeaders)
                
                // Parse lines
                val lines = playlistContent.lines()
                val isMasterPlaylist = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
                
                var mediaPlaylistUrl = download.url
                var mediaPlaylistContent = playlistContent
                
                if (isMasterPlaylist) {
                    var bestUrl: String? = null
                    var maxBandwidth = 0L
                    var i = 0
                    while (i < lines.size) {
                        val line = lines[i].trim()
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            val bandwidthRegex = Regex("""BANDWIDTH=(\d+)""")
                            val match = bandwidthRegex.find(line)
                            val bandwidth = match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                            
                            var nextLineIndex = i + 1
                            while (nextLineIndex < lines.size && lines[nextLineIndex].trim().startsWith("#")) {
                                nextLineIndex++
                            }
                            if (nextLineIndex < lines.size) {
                                val subUrl = lines[nextLineIndex].trim()
                                if (subUrl.isNotEmpty()) {
                                    if (bandwidth > maxBandwidth || bestUrl == null) {
                                        maxBandwidth = bandwidth
                                        bestUrl = subUrl
                                    }
                                }
                            }
                        }
                        i++
                    }
                    if (bestUrl != null) {
                        mediaPlaylistUrl = resolveUrl(download.url, bestUrl)
                        mediaPlaylistContent = getUrlContent(mediaPlaylistUrl, download.requestHeaders)
                    }
                }
                
                // Parse segments
                val segments = mutableListOf<String>()
                var initSegmentUrl: String? = null
                
                mediaPlaylistContent.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach
                    if (trimmed.startsWith("#")) {
                        if (trimmed.startsWith("#EXT-X-MAP:")) {
                            val uriRegex = Regex("""URI=["']([^"']+)["']""")
                            val match = uriRegex.find(trimmed)
                            val uri = match?.groupValues?.get(1)
                            if (uri != null) {
                                initSegmentUrl = resolveUrl(mediaPlaylistUrl, uri)
                            }
                        }
                        return@forEach
                    }
                    segments.add(resolveUrl(mediaPlaylistUrl, trimmed))
                }
                
                if (segments.isEmpty()) {
                    throw Exception("No segments found in HLS playlist")
                }
                
                val hasFile = localFile.exists() && localFile.length() > 0
                val startSegmentIndex = if (hasFile) download.downloadedSegments else 0
                val appendMode = startSegmentIndex > 0 && hasFile
                var bytesDownloaded = if (appendMode) localFile.length() else 0L
                var lastProgressEmitMs = 0L
                var lastReportedProgress = -1

                java.io.BufferedOutputStream(java.io.FileOutputStream(localFile, appendMode), 64 * 1024).use { outputStream ->
                    if (startSegmentIndex == 0 && initSegmentUrl != null) {
                        bytesDownloaded += downloadSegmentToStream(initSegmentUrl!!, download.requestHeaders, outputStream)
                    }
                    val totalSegments = segments.size
                    for (index in startSegmentIndex until totalSegments) {
                        coroutineContext.ensureActive()
                        val segmentUrl = segments[index]
                        bytesDownloaded += downloadSegmentToStream(segmentUrl, download.requestHeaders, outputStream)

                        val progress = (100 * (index + 1) / totalSegments).coerceIn(0, 100)
                        val now = android.os.SystemClock.elapsedRealtime()
                        val isFinalSegment = index + 1 == totalSegments
                        if (isFinalSegment ||
                            now - lastProgressEmitMs >= 250L ||
                            (progress != lastReportedProgress && now - lastProgressEmitMs >= 100L)
                        ) {
                            lastProgressEmitMs = now
                            lastReportedProgress = progress
                            onProgress(progress, bytesDownloaded, 0L, index + 1)
                        }
                    }
                    outputStream.flush()
                }
            } else {
                // Direct file download
                downloadDirectFile(download.url, download.requestHeaders, localFile, { p, b, t ->
                    onProgress(p, b, t, 0)
                }, download)
            }

            // ── Resolve and download subtitles ────────────────────────────────
            val allResolvedSubtitles = mutableListOf<com.kitsugi.animelist.core.player.SubtitleInput>()
            
            // First, add existing subtitles passed with the download
            if (!download.subtitles.isNullOrEmpty()) {
                allResolvedSubtitles.addAll(download.subtitles)
            }
            
            // Fetch external and OpenSubtitles in the background
            try {
                val dMalId = download.malId
                val dAniListId = download.aniListId
                val dTmdbId = download.tmdbId ?: if (dMalId == null && dAniListId == null) download.animeId.toIntOrNull() else null
                
                if (dMalId != null || dAniListId != null || dTmdbId != null) {
                    val resolvedIds = com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
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
                        val guessedFilename = download.url.let { url ->
                            try {
                                val lastSeg = android.net.Uri.parse(url).lastPathSegment
                                if (!lastSeg.isNullOrBlank() && lastSeg.contains(".")) lastSeg else null
                            } catch (_: Exception) { null }
                        }
                        val cleanedFilename = guessedFilename?.substringBefore("\n")?.substringBefore("\r")?.trim()
                        
                        val subRepo = com.kitsugi.animelist.data.repository.SubtitleRepositoryImpl(context)
                        val remoteSubs = mutableListOf<com.kitsugi.animelist.core.player.model.Subtitle>()
                        for (queryId in queryIds) {
                            try {
                                val subs = subRepo.getSubtitles(
                                    type = type,
                                    id = queryId,
                                    videoUrl = download.url,
                                    videoHeaders = download.requestHeaders,
                                    filename = cleanedFilename,
                                    aniListId = dAniListId,
                                    animeTitle = download.animeTitle,
                                    episode = download.episode
                                )
                                remoteSubs.addAll(subs)
                            } catch (e: Exception) {
                                android.util.Log.e("AnimeDownloader", "Failed to fetch subtitles for queryId=$queryId", e)
                            }
                        }
                        
                        for (sub in remoteSubs.distinctBy { it.url }) {
                            val friendlyLangName = com.kitsugi.animelist.core.player.PlayerSubtitleUtils.getFriendlyLanguageName(sub.lang)
                            allResolvedSubtitles.add(
                                com.kitsugi.animelist.core.player.SubtitleInput(
                                    url = sub.url,
                                    name = "$friendlyLangName (${sub.addonName})",
                                    lang = sub.lang
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AnimeDownloader", "Failed to resolve subtitles in downloader", e)
            }

            val dedupedSubs = allResolvedSubtitles.distinctBy { it.url }
            val dlLangs = settings.subtitleDownloadLanguages
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }

            val filteredSubs = if (dlLangs.isEmpty()) {
                dedupedSubs
            } else {
                dedupedSubs.filter { sub ->
                    dlLangs.any { targetLang ->
                        com.kitsugi.animelist.core.player.PlayerSubtitleUtils.matchesLanguageCode(sub.lang ?: "", targetLang)
                    }
                }
            }

            // Altyazılar videonun yanına, aynı taban adla tek konuma yazılır.
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
                    // Yalnızca video ile aynı host'taysa video istek başlıklarını kullan
                    val useHeaders = try {
                        val subHost = java.net.URI(subtitle.url).host
                        val videoHost = java.net.URI(download.url).host
                        if (subHost != null && subHost.equals(videoHost, ignoreCase = true)) {
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
                    android.util.Log.e("AnimeDownloader", "Failed to download subtitle: ${subtitle.name}", e)
                    runCatching { subFile.delete() }
                }
            }

            // Metadata: yalnızca video tamamlandıktan sonra yazılır (kısmi indirme listelenmez).
            val metaFile = File(videoDir, DownloadExportNaming.metadataFileName(baseName))
            val metaJson = JSONObject().apply {
                put("mediaId", mediaId)
                put("title", "${download.animeTitle} - Bölüm ${download.episode}")
                put("durationMs", 0)
                put("downloadedAtMs", System.currentTimeMillis())
            }
            metaFile.writeText(metaJson.toString(2))

            // Poster: best-effort, indirme başarısını etkilemez.
            val posterFile = File(
                DownloadExportNaming.publicImagesDir().also { it.mkdirs() },
                DownloadExportNaming.posterFileName(baseName)
            )
            downloadPosterIfAvailable(download.posterUrl, posterFile)

            // Özel (SAF) klasör seçiliyse aynı dosyanın kopyasını oluştur; eskisini önce siler.
            val customDirUriStr = settings.videoDownloadUri
            if (customDirUriStr.isNotBlank()) {
                try {
                    val customDirDoc = DocumentFile.fromTreeUri(context, Uri.parse(customDirUriStr))
                    if (customDirDoc != null && customDirDoc.exists()) {
                        customDirDoc.findFile(outFilename)?.delete()
                        val newFileDoc = customDirDoc.createFile("video/mp4", outFilename)
                        newFileDoc?.uri?.let { destUri ->
                            context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                                localFile.inputStream().use { inStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                        }

                        savedSubs.forEach { subFile ->
                            try {
                                val subMime = when (subFile.extension.lowercase()) {
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
                                android.util.Log.e("AnimeDownloader", "Failed to copy subtitle ${subFile.name} to custom SAF directory", subEx)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AnimeDownloader", "Failed to copy video to custom SAF directory", e)
                }
            }

            // Medya tarayıcıya bildir (MediaStore.insert yok: dosya zaten diskte, yinelenen kayıt oluşmaz).
            val toScan = buildList {
                add(localFile)
                addAll(savedSubs)
                if (posterFile.isFile) add(posterFile)
            }.map { it.absolutePath }.toTypedArray()
            MediaScannerConnection.scanFile(context, toScan, null, null)

            onStatusChanged(AnimeDownload.Status.COMPLETED, localFile.absolutePath)

        } catch (e: Exception) {
            android.util.Log.e("AnimeDownloader", "Download failed", e)
            onStatusChanged(AnimeDownload.Status.ERROR, null)
        }
    }

    /** Poster'ı `destFile`'a indirir. Hata olursa sessizce atlanır. */
    private suspend fun downloadPosterIfAvailable(url: String?, destFile: File) {
        if (url.isNullOrBlank() || !url.startsWith("http", ignoreCase = true)) return
        if (destFile.isFile && destFile.length() > 0) return
        try {
            val request = Request.Builder().url(url).build()
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body ?: return
                body.byteStream().use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("AnimeDownloader", "Poster download skipped: ${e.message}")
            runCatching { destFile.delete() }
        }
    }

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

    private suspend fun downloadDirectFile(
        url: String,
        headers: Map<String, String>,
        localFile: File,
        onProgress: (Int, Long, Long) -> Unit,
        download: AnimeDownload
    ) = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts) {
            attempts++
            try {
                val existingLength = if (localFile.exists()) localFile.length() else 0L
                val requestBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> requestBuilder.header(k, v) }
                
                if (existingLength > 0) {
                    requestBuilder.header("Range", "bytes=$existingLength-")
                }
                
                KitsugiHttpClient.client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 416) {
                            localFile.delete()
                            throw Exception("416 Range Not Satisfiable")
                        }
                        throw Exception("Failed to download file: ${response.code} ${response.message}")
                    }
                    
                    val isRange = response.code == 206
                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    val totalBytes = if (isRange) contentLength + existingLength else contentLength
                    val appendMode = isRange && existingLength > 0
                    
                    body.byteStream().use { inputStream ->
                        java.io.BufferedOutputStream(java.io.FileOutputStream(localFile, appendMode), 64 * 1024).use { outputStream ->
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
                                val now = android.os.SystemClock.elapsedRealtime()
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
                return@withContext // Success!
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("AnimeDownloader", "Direct download attempt $attempts failed: ${e.message}")
                if (attempts >= maxAttempts) {
                    throw e
                }
                delay(2000L * attempts)
            }
        }
    }

    private suspend fun downloadSubtitleFile(
        url: String,
        headers: Map<String, String>,
        destFile: File
    ) = withContext(Dispatchers.IO) {
        // Cihazda acilmis paket altyazilari (orn. AniSub RAR/ZIP) file:// olarak
        // gelebilir; agdan indirmek yerine dogrudan videonun yanina kopyalanir.
        val localPath = when {
            url.startsWith("file://", ignoreCase = true) -> url.substring("file://".length)
            url.startsWith("file:/", ignoreCase = true) -> url.substring("file:".length)
            url.startsWith("/") -> url.substringBefore('?')
            else -> null
        }
        if (localPath != null) {
            val source = File(localPath)
            if (!source.isFile || source.length() == 0L) {
                throw Exception("Local subtitle file not found: $url")
            }
            source.inputStream().use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return@withContext
        }

        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        KitsugiHttpClient.client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download subtitle: ${response.code} ${response.message}")
            }
            val body = response.body ?: throw Exception("Empty subtitle response body")
            body.byteStream().use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
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

    private fun downloadExternal(
        download: AnimeDownload,
        downloaderPref: String,
        onStatusChanged: (AnimeDownload.Status, String?) -> Unit
    ) {
        val pm = context.packageManager
        val cleanTitle = download.animeTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val filename = KitsugiStrings.get(R.string.x_bolum_x_mp4, cleanTitle, download.episode)

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
                        throw Exception("1DM not installed")
                    }
                    intent = Intent(Intent.ACTION_VIEW).apply {
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
                    if (!isPackageInstalled(pm, pkgName)) {
                        throw Exception("ADM not installed")
                    }
                    intent = Intent(Intent.ACTION_VIEW).apply {
                        component = ComponentName(pkgName, "$pkgName.AEditor")
                        putExtra("com.dv.get.ACTION_LIST_ADD", "${download.url}<info>$filename")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                else -> {
                    // System Browser / Default
                    intent = Intent(Intent.ACTION_VIEW).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setDataAndType(Uri.parse(download.url), "video/*")
                        putExtra("extra_filename", filename)
                    }
                }
            }
            context.startActivity(intent)
            onStatusChanged(AnimeDownload.Status.COMPLETED, null)
        } catch (e: Exception) {
            android.util.Log.e("AnimeDownloader", "Failed to launch external downloader", e)
            onStatusChanged(AnimeDownload.Status.ERROR, null)
        }
    }

    private fun isPackageInstalled(pm: android.content.pm.PackageManager, pkgName: String): Boolean {
        return try {
            pm.getPackageInfo(pkgName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
