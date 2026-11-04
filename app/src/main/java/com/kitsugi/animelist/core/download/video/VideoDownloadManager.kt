package com.kitsugi.animelist.core.download.video

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.player.OfflinePlaybackHelper
import com.kitsugi.animelist.data.trailer.InAppYouTubeExtractor
import com.kitsugi.animelist.data.trailer.YouTubeRequestPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Detay sayfasındaki küçük videoların (fragman / PV / OP / ED / müzik) indirme motoru.
 *
 * ## Neden böyle bir motor?
 * Bu videolar tek tip gelmiyor:
 *  - **Doğrudan dosya** (`.mp4` / `.webm`) — AnimeThemes, eklenti çıktıları.
 *  - **HLS** (`.m3u8`) — YouTube'un TV istemcisi, birçok eklenti. Parçalar indirilip
 *    tek bir dosyada birleştirilir; kullanıcı yine tek dosya görür.
 *  - **Ayrı ses + görüntü akışları** — YouTube adaptif formatları. Bunlar önce HLS /
 *    muxed progressive sürümle indirilmeye çalışılır; olmazsa iki akış indirilip
 *    [MediaStreamMuxer] ile **yeniden kodlanmadan** tek dosyaya yazılır.
 *
 * Bu sayede "sesi ve görüntüsü ayrı akışlarda sunuluyor; tek dosya olarak indirilemiyor"
 * durumu ortadan kalkar: motor her koşulda elde edilebilen en iyi tek dosyayı üretir.
 *
 * ## Durum paylaşımı
 * İndirme işleri uygulama kapsamlı bir [CoroutineScope]'ta çalışır; UI tarafı
 * [states] akışından satır bazlı ilerleme okur. Aynı satır için ikinci bir istek
 * başlatılmaz (çift dosya / çift trafik yok).
 */
object VideoDownloadManager {

    private const val TAG = "VideoDownloadManager"
    private const val COPY_BUFFER = 64 * 1024
    private const val PROGRESS_STEP_BYTES = 512 * 1024L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _states = MutableStateFlow<Map<String, VideoDownloadState>>(emptyMap())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val youtubeExtractor = InAppYouTubeExtractor()

    val states: StateFlow<Map<String, VideoDownloadState>> = _states.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun stateOf(key: String): VideoDownloadState = _states.value[key] ?: VideoDownloadState()

    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
        publish(key, VideoDownloadState())
    }

    /**
     * İndirmeyi başlatır. [request.url] başarısız olursa [VideoDownloadRequest.alternateUrls]
     * sırayla denenir; tüm adresler tükenirse durum `FAILED` olur ve nedeni taşınır.
     */
    fun start(context: Context, request: VideoDownloadRequest) {
        if (request.url.isBlank()) return
        if (jobs[request.key]?.isActive == true) return

        val appContext = context.applicationContext
        val key = request.key
        publish(key, VideoDownloadState(phase = VideoDownloadPhase.RESOLVING))

        jobs[key] = scope.launch {
            try {
                performDownload(appContext, request)
            } catch (cancel: CancellationException) {
                publish(key, VideoDownloadState())
                throw cancel
            } catch (t: Throwable) {
                Log.e(TAG, "Download failed for '${request.title}'", t)
                val reason = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                publish(key, VideoDownloadState(phase = VideoDownloadPhase.FAILED, message = reason))
                toast(appContext, appContext.getString(R.string.video_download_failed, reason))
            } finally {
                jobs.remove(key)
            }
        }
    }

    // ─── Akış ────────────────────────────────────────────────────────────────

    private data class ResolvedStream(val videoUrl: String, val audioUrl: String? = null)

    private data class DownloadOutcome(val file: File, val message: String? = null)

    private suspend fun performDownload(context: Context, request: VideoDownloadRequest) {
        val candidates = buildList {
            add(request.url)
            addAll(request.alternateUrls)
        }
            .map { it.trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct()

        if (candidates.isEmpty()) throw IOException("geçersiz video adresi")

        var lastError: Throwable? = null
        for ((index, candidate) in candidates.withIndex()) {
            try {
                val outcome = downloadCandidate(context, request, candidate, index, candidates.size)
                onCompleted(context, request, outcome)
                return
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                lastError = t
                Log.w(
                    TAG,
                    "Candidate ${index + 1}/${candidates.size} failed for '${request.title}': ${t.message}"
                )
            }
        }
        throw lastError ?: IOException("video indirilemedi")
    }

    private suspend fun downloadCandidate(
        context: Context,
        request: VideoDownloadRequest,
        url: String,
        candidateIndex: Int,
        candidateCount: Int
    ): DownloadOutcome {
        publish(request.key, VideoDownloadState(phase = VideoDownloadPhase.RESOLVING))

        val stream = resolveStream(url)
        val headers = YouTubeRequestPolicy.headersForInlineUrl(stream.videoUrl)
        val baseName = VideoSourceClassifier.sanitizeFileName(
            if (candidateCount > 1 && candidateIndex > 0) "${request.title} (${candidateIndex + 1})" else request.title
        )
        val dir = OfflinePlaybackHelper.getDownloadsDir(context)

        val kind = VideoSourceClassifier.kindOf(stream.videoUrl)
        val isHls = kind == VideoSourceKind.HLS ||
            (kind == VideoSourceKind.UNKNOWN && isHlsManifest(stream.videoUrl, headers))

        // `.mpd` (DASH) manifest dosyası doğrudan indirilip oynatılamaz; YouTube'ın DASH
        // akışları zaten [InAppYouTubeExtractor] tarafından çözümlenip doğrudan segment/
        // akış adresleriyle geliyor. Doğrudan bir `.mpd` adresi geldiyse sonraki aday
        // denenebilmesi için hata fırlat.
        if (kind == VideoSourceKind.DASH && stream.audioUrl == null) {
            throw IOException("DASH manifest (.mpd) doğrudan indirilemez; alternate adres denensin")
        }

        return when {
            isHls -> DownloadOutcome(
                downloadHls(context, request, stream.videoUrl, headers, dir, baseName)
            )

            stream.audioUrl != null -> downloadSeparate(
                context = context,
                request = request,
                videoUrl = stream.videoUrl,
                audioUrl = stream.audioUrl,
                headers = headers,
                dir = dir,
                baseName = baseName
            )

            else -> DownloadOutcome(
                downloadSingleFile(context, request, stream.videoUrl, headers, dir, baseName)
            )
        }
    }

    /** YouTube sayfaları için gerçek akış adreslerini çıkarır; diğer kaynaklar aynen geçer. */
    private suspend fun resolveStream(url: String): ResolvedStream {
        if (!VideoSourceClassifier.isYouTubeUrl(url)) return ResolvedStream(url)
        val source = youtubeExtractor.extractPlaybackSource(url)
            ?: throw IOException("YouTube akışı çözümlenemedi")
        val videoUrl = source.videoUrl.takeIf { it.isNotBlank() }
            ?: throw IOException("YouTube akış adresi boş")
        return ResolvedStream(videoUrl, source.audioUrl?.takeIf { it.isNotBlank() })
    }

    // ─── Doğrudan dosya ──────────────────────────────────────────────────────

    private suspend fun downloadSingleFile(
        context: Context,
        request: VideoDownloadRequest,
        url: String,
        headers: Map<String, String>,
        dir: File,
        baseName: String
    ): File {
        val partFile = File(tempDir(context), "single-${stableName(request.key)}.part")
        publish(request.key, VideoDownloadState(phase = VideoDownloadPhase.DOWNLOADING))
        val contentType = streamToFile(url, headers, partFile) { downloaded, total ->
            publishProgress(request.key, downloaded, total, baseFraction = 0f, spanFraction = 1f)
        }
        val extension = VideoSourceClassifier.extensionFor(url, contentType)
        val target = uniqueFile(dir, baseName, extension)
        moveFile(partFile, target)
        return target
    }

    // ─── HLS ────────────────────────────────────────────────────────────────

    private suspend fun downloadHls(
        context: Context,
        request: VideoDownloadRequest,
        manifestUrl: String,
        headers: Map<String, String>,
        dir: File,
        baseName: String
    ): File {
        var mediaUrl = manifestUrl
        var content = fetchText(manifestUrl, headers)

        if (HlsPlaylistParser.isMasterPlaylist(content)) {
            val best = HlsPlaylistParser.pickBestVariant(
                HlsPlaylistParser.parseMaster(content, manifestUrl)
            ) ?: throw IOException("HLS varyantı bulunamadı")
            mediaUrl = best.url
            content = fetchText(mediaUrl, headers)
        }

        val playlist = HlsPlaylistParser.parseMedia(content, mediaUrl)
        if (playlist.segments.isEmpty()) throw IOException("HLS parçası bulunamadı")

        val extension = if (playlist.isFragmentedMp4) "mp4" else "ts"
        val target = uniqueFile(dir, baseName, extension)
        val partFile = File(tempDir(context), "hls-${stableName(request.key)}.part")
        val keyCache = HashMap<String, ByteArray>()

        publish(request.key, VideoDownloadState(phase = VideoDownloadPhase.DOWNLOADING))

        FileOutputStream(partFile, false).use { out ->
            playlist.initSegmentUrl?.let { initUrl ->
                val initBytes = fetchBytes(initUrl, headers, playlist.initSegmentRange)
                out.write(decryptIfNeeded(initBytes, playlist.initSegmentKey, keyCache, 0))
            }
            val total = playlist.segments.size
            playlist.segments.forEachIndexed { index, segment ->
                currentCoroutineContext().ensureActive()
                val bytes = decryptIfNeeded(
                    data = fetchBytes(segment.url, headers, segment.byteRange),
                    key = segment.key,
                    keyCache = keyCache,
                    sequence = segment.sequence
                )
                out.write(bytes)
                publish(
                    request.key,
                    VideoDownloadState(
                        phase = VideoDownloadPhase.DOWNLOADING,
                        progress = ((index + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    )
                )
            }
            out.flush()
        }

        if (partFile.length() == 0L) throw IOException("HLS indirmesi boş")
        moveFile(partFile, target)
        return target
    }

    // ─── Ayrı ses + görüntü ─────────────────────────────────────────────────

    private suspend fun downloadSeparate(
        context: Context,
        request: VideoDownloadRequest,
        videoUrl: String,
        audioUrl: String,
        headers: Map<String, String>,
        dir: File,
        baseName: String
    ): DownloadOutcome {
        val temp = tempDir(context)
        val videoPart = File(temp, "video-${stableName(request.key)}.part")
        val audioPart = File(temp, "audio-${stableName(request.key)}.part")

        publish(request.key, VideoDownloadState(phase = VideoDownloadPhase.DOWNLOADING))
        val videoType = streamToFile(videoUrl, headers, videoPart) { downloaded, total ->
            publishProgress(request.key, downloaded, total, baseFraction = 0f, spanFraction = 0.8f)
        }
        val audioType = streamToFile(audioUrl, headers, audioPart) { downloaded, total ->
            publishProgress(request.key, downloaded, total, baseFraction = 0.8f, spanFraction = 0.15f)
        }

        publish(
            request.key,
            VideoDownloadState(phase = VideoDownloadPhase.MERGING, progress = 0.95f)
        )

        val preferWebm = VideoSourceClassifier.looksLikeWebm(videoType, videoUrl) &&
            VideoSourceClassifier.looksLikeWebm(audioType, audioUrl)
        val mergedExtension = if (preferWebm) "webm" else "mp4"
        val target = uniqueFile(dir, baseName, mergedExtension)

        if (MediaStreamMuxer.mux(videoPart, audioPart, target, preferWebm)) {
            runCatching { videoPart.delete() }
            runCatching { audioPart.delete() }
            return DownloadOutcome(target)
        }

        // Birleştirme bu cihazda mümkün olmadı: iki akışı da eksiksiz sakla.
        val videoTarget = uniqueFile(
            dir,
            "$baseName (görüntü)",
            VideoSourceClassifier.extensionFor(videoUrl, videoType)
        )
        val audioTarget = uniqueFile(
            dir,
            "$baseName (ses)",
            VideoSourceClassifier.extensionFor(audioUrl, audioType).let { if (it == "webm") "webm" else "m4a" }
        )
        moveFile(videoPart, videoTarget)
        moveFile(audioPart, audioTarget)
        Log.w(TAG, "Mux unavailable; saved split files for '${request.title}'")
        return DownloadOutcome(
            file = videoTarget,
            message = context.getString(R.string.video_download_split_saved)
        )
    }

    // ─── HTTP yardımcıları ───────────────────────────────────────────────────

    private fun requestBuilder(url: String, headers: Map<String, String>): Request.Builder {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) builder.header(name, value)
        }
        builder.header("Accept", "video/*,application/octet-stream,application/vnd.apple.mpegurl,*/*;q=0.8")
        return builder
    }

    private fun isHlsManifest(url: String, headers: Map<String, String>): Boolean = runCatching {
        httpClient.newCall(
            requestBuilder(url, headers).header("Range", "bytes=0-7").build()
        ).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val type = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
            if (type.contains("mpegurl") || type.contains("m3u")) return@use true
            val body = response.body ?: return@use false
            val head = ByteArray(8)
            val read = body.byteStream().use { it.read(head) }
            val text = if (read > 0) String(head, 0, read, Charsets.UTF_8) else ""
            text.contains("#EXTM3U", ignoreCase = true)
        }
    }.getOrDefault(false)

    private suspend fun fetchText(url: String, headers: Map<String, String>): String =
        httpClient.newCall(requestBuilder(url, headers).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("boş yanıt")
            body.string()
        }

    private suspend fun fetchBytes(
        url: String,
        headers: Map<String, String>,
        byteRange: LongRange? = null
    ): ByteArray {
        val builder = requestBuilder(url, headers)
        if (byteRange != null) {
            builder.header("Range", "bytes=${byteRange.first}-${byteRange.last}")
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("boş yanıt")
            val bytes = body.bytes()
            if (byteRange == null) return bytes

            val needed = (byteRange.last - byteRange.first + 1).toInt().coerceAtLeast(1)
            return if (response.code == 206) {
                bytes.copyOfRange(0, minOf(needed, bytes.size))
            } else {
                val start = byteRange.first.toInt().coerceIn(0, bytes.size)
                bytes.copyOfRange(start, minOf(start + needed, bytes.size))
            }
        }
    }

    /**
     * Yanıtı dosyaya yazar; toplam boyut bilinmiyorsa `total = -1` ile bildirir.
     * Dönen değer `Content-Type` (uzantı tahmini için).
     */
    private suspend fun streamToFile(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): String {
        val call = httpClient.newCall(requestBuilder(url, headers).build())
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("boş yanıt")
            val contentType = response.header("Content-Type").orEmpty()
            val total = body.contentLength()
            target.parentFile?.mkdirs()

            FileOutputStream(target, false).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var downloaded = 0L
                    var lastReported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= PROGRESS_STEP_BYTES ||
                            (total > 0 && downloaded >= total)
                        ) {
                            lastReported = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                    output.flush()
                    if (downloaded <= 0L) throw IOException("boş dosya")
                    onProgress(downloaded, total)
                }
            }
            return contentType
        }
    }

    /** AES-128 şifreli HLS parçalarını çözer; anahtarlar indirme boyunca önbelleklenir. */
    private suspend fun decryptIfNeeded(
        data: ByteArray,
        key: HlsKey?,
        keyCache: MutableMap<String, ByteArray>,
        sequence: Int
    ): ByteArray {
        if (key == null) return data
        if (!key.method.equals("AES-128", ignoreCase = true)) {
            throw IOException("desteklenmeyen HLS şifrelemesi: ${key.method}")
        }
        val keyUri = key.uri ?: throw IOException("HLS anahtar adresi yok")
        val secret = keyCache[keyUri] ?: fetchBytes(keyUri, emptyMap()).also { keyCache[keyUri] = it }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(secret, "AES"),
            IvParameterSpec(ivBytes(key.ivHex, sequence))
        )
        return cipher.doFinal(data)
    }

    private fun ivBytes(hex: String?, sequence: Int): ByteArray {
        val iv = ByteArray(16)
        if (!hex.isNullOrBlank()) {
            val clean = hex.removePrefix("0x").removePrefix("0X")
            var index = 0
            while (index < 16 && index * 2 + 1 < clean.length) {
                iv[index] = clean.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
                index++
            }
            return iv
        }
        var value = sequence.toLong()
        for (index in 15 downTo 8) {
            iv[index] = (value and 0xFF).toByte()
            value = value shr 8
        }
        return iv
    }

    // ─── Durum / dosya yardımcıları ──────────────────────────────────────────

    private fun publishProgress(
        key: String,
        downloaded: Long,
        total: Long,
        baseFraction: Float,
        spanFraction: Float
    ) {
        val fraction = if (total > 0L) {
            (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            // Toplam boyut yok (chunked): yarım saatte bir bitmeyecek bir çubuk göstermektense
            // sabit, küçük bir "devam ediyor" ilerlemesi göstermek daha dürüst.
            0.35f
        }
        publish(
            key,
            VideoDownloadState(
                phase = VideoDownloadPhase.DOWNLOADING,
                progress = (baseFraction + fraction * spanFraction).coerceIn(0f, 1f)
            )
        )
    }

    private fun publish(key: String, state: VideoDownloadState) {
        _states.update { current -> current + (key to state) }
    }

    private fun onCompleted(context: Context, request: VideoDownloadRequest, outcome: DownloadOutcome) {
        publish(
            request.key,
            VideoDownloadState(
                phase = VideoDownloadPhase.COMPLETED,
                progress = 1f,
                message = outcome.message,
                fileName = outcome.file.name,
                outputPath = outcome.file.absolutePath
            )
        )
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(outcome.file.absolutePath), null, null)
        }
        val text = outcome.message
            ?: context.getString(R.string.video_download_completed, outcome.file.name)
        toast(context, text)
    }

    private fun toast(context: Context, text: String) {
        mainHandler.post {
            runCatching { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
        }
    }

    private fun tempDir(context: Context): File =
        File(context.cacheDir, "kitsugi_video_downloads").apply { mkdirs() }

    private fun stableName(key: String): String =
        Integer.toHexString(key.hashCode()).ifBlank { "video" }

    private fun uniqueFile(dir: File, baseName: String, extension: String): File {
        dir.mkdirs()
        val safeExtension = extension.removePrefix(".").ifBlank { "mp4" }
        var candidate = File(dir, "$baseName.$safeExtension")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($counter).$safeExtension")
            counter += 1
        }
        return candidate
    }

    private fun moveFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) return
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, COPY_BUFFER) }
        }
        runCatching { source.delete() }
    }
}
