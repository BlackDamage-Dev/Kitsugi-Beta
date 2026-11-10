package com.kitsugi.animelist.core.download

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.trailer.InAppYouTubeExtractor
import com.kitsugi.animelist.data.trailer.YouTubeHlsMediaPlaylist
import com.kitsugi.animelist.data.trailer.YouTubeDownloadEncoder
import com.kitsugi.animelist.data.trailer.YouTubeRequestPolicy
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Videolar sekmesindeki tek bir videoyu (fragman, teaser, açılış/kapanış) cihaza indirir.
 *
 * NEDEN AYRI BİR MOTOR?
 * Detay sayfası "videolar" satırları iki tür kaynak taşır:
 *  1. **YouTube** — `KitsugiYouTubePlayer`'ın çalabileceği fragman/teaser/tema videoları.
 *     Bunların gerçek medya adresi imzalı ve kısa ömürlüdür; doğrudan `DownloadManager`
 *     ile indirilemez (imza süresi dolar, HLS master playlist tek dosya değildir).
 *  2. **Doğrudan dosya** — AnimeThemes `.webm`/`.mp4` dosyaları.
 *
 * Bu sınıf iki yolu da yönetir:
 *  - HLS: playlist segmentlerine ayrılır, `EXT-X-MAP` init segmenti başa yazılır,
 *  - ayrı video+ses (YouTube adaptive): iki akış indirilip tek `.mp4` içine mux edilir,
 *  - progressive / doğrudan dosya: tek akış kopyalanır.
 *
 * Dosyalar `Download/Kitsugi/Video/<Başlık>_<Etiket>.<uzantı>` altına yazılır; uygulama
 * klasörüne yazmak zorunda kalınırsa (izin/scoped storage) oraya düşer.
 */
object DetailVideoDownloader {

    private const val TAG = "DetailVideoDownloader"

    /** Küçük oynatıcı videosu için üst sınır — kazara dev bir arşiv indirmeyi engeller. */
    private const val MAX_FILE_BYTES = 900L * 1024L * 1024L // 900 MB

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    data class Result(
        val success: Boolean,
        val fileLabel: String? = null,
        val errorMessage: String? = null
    )

    /**
     * [videoUrl] YouTube izleme adresi ya da doğrudan medya dosyası olabilir.
     * [onProgress] 0..1 arası ilerleme yüzdesi (bilinmiyorsa `null` geçilir).
     */
    suspend fun download(
        context: Context,
        videoUrl: String,
        title: String,
        mediaTitle: String = "",
        onProgress: (Float?, String) -> Unit = { _, _ -> },
        onStatus: (String) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        if (videoUrl.isBlank()) {
            return@withContext Result(false, errorMessage = KitsugiStrings.get(R.string.detail_video_download_error_generic))
        }

        onStatus(KitsugiStrings.get(R.string.detail_video_download_preparing))

        val displayName = buildDisplayName(title = title, mediaTitle = mediaTitle)
        val targetDir = KitsugiVideoDir.resolve(context)

        try {
            val youtubeId = InAppYouTubeExtractor.shared.extractVideoId(videoUrl)
            if (youtubeId != null) {
                downloadYouTube(
                    context = context,
                    videoUrl = videoUrl,
                    displayName = displayName,
                    targetDir = targetDir,
                    onProgress = onProgress,
                    onStatus = onStatus
                )
            } else {
                downloadDirect(
                    context = context,
                    url = videoUrl,
                    displayName = displayName,
                    targetDir = targetDir,
                    onProgress = onProgress,
                    onStatus = onStatus
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "İndirme başarısız: ${e.message}", e)
            Result(false, errorMessage = e.message?.takeIf { it.isNotBlank() }
                ?: KitsugiStrings.get(R.string.detail_video_download_error_generic))
        }
    }

    // ── YouTube yolu ──────────────────────────────────────────────────────────

    private suspend fun downloadYouTube(
        context: Context,
        videoUrl: String,
        displayName: String,
        targetDir: File,
        onProgress: (Float?, String) -> Unit,
        onStatus: (String) -> Unit
    ): Result {
        val candidates = InAppYouTubeExtractor.shared.resolveDownloadCandidates(videoUrl)
            ?: return Result(false, errorMessage = KitsugiStrings.get(R.string.detail_video_download_error_no_stream))

        // 1) HLS: segmentleri sırayla birleştir. Video ve ses tek akıştadır.
        val hlsUrl = candidates.hlsManifestUrl
        if (hlsUrl != null) {
            val playlist = fetchMediaPlaylist(hlsUrl)
            if (playlist != null && playlist.isUsable) {
                val extension = if (playlist.isTransportStream) "ts" else "mp4"
                val file = uniqueFile(targetDir, displayName, extension)
                val hlsResult: Result? = try {
                    downloadPlaylistAsFile(
                        playlist = playlist,
                        destination = file,
                        onProgress = onProgress,
                        onStatus = onStatus
                    )
                    publishToGalleryIfPossible(context, file)
                    Result(true, fileLabel = file.name)
                } catch (e: CancellationException) {
                    runCatching { file.delete() }
                    throw e
                } catch (e: Exception) {
                    runCatching { file.delete() }
                    Log.w(TAG, "HLS indirme başarısız, diğer akışa geçilecek: ${e.message}")
                    null
                }
                if (hlsResult != null) return hlsResult
            } else {
                Log.w(
                    TAG,
                    "HLS playlist kullanılamadı (şifreli=${playlist?.isEncrypted}, " +
                        "segment=${playlist?.segmentUrls?.size ?: 0})"
                )
            }
        }

        // 2) Ayrı video + ses: ikisini indirip tek MP4 içine mux et.
        val videoStream = candidates.adaptiveVideoUrl
        if (videoStream != null) {
            onStatus(KitsugiStrings.get(R.string.detail_video_download_preparing_streams))
            val videoTemp = File.createTempFile("kitsugi_dl_v_", ".bin", targetDir)
            val audioTemp = candidates.adaptiveAudioUrl?.let {
                File.createTempFile("kitsugi_dl_a_", ".bin", targetDir)
            }
            try {
                downloadStreamToFile(videoStream, videoTemp, 0f, 0.7f, onProgress)
                if (audioTemp != null) {
                    downloadStreamToFile(candidates.adaptiveAudioUrl!!, audioTemp, 0.7f, 0.95f, onProgress)
                }
                onStatus(KitsugiStrings.get(R.string.detail_video_download_merging))
                val output = uniqueFile(targetDir, displayName, "mp4")
                val merged = YouTubeDownloadEncoder.mux(videoFile = videoTemp, audioFile = audioTemp, output = output)
                if (merged && output.length() > 0L) {
                    onProgress(1f, "")
                    publishToGalleryIfPossible(context, output)
                    return Result(true, fileLabel = output.name)
                }
                runCatching { output.delete() }
                Log.w(TAG, "Mux başarısız; yalnızca video akışı kaydedilecek")
                // Mux edilemezse video akışını olduğu gibi (sessiz) saklamak yerine
                // progressive akışa düşmek daha iyi: kullanıcı sesli bir dosya bekliyor.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Adaptif akış indirilemedi: ${e.message}")
            } finally {
                runCatching { videoTemp.delete() }
                audioTemp?.let { runCatching { it.delete() } }
            }
        }

        // 3) Progressive (video+ses tek dosya, genelde 360p/720p).
        val progressive = candidates.progressiveUrl
        if (progressive != null) {
            onStatus(KitsugiStrings.get(R.string.detail_video_download_started))
            val file = uniqueFile(targetDir, displayName, "mp4")
            return try {
                downloadStreamToFile(progressive, file, 0f, 1f, onProgress)
                onProgress(1f, "")
                publishToGalleryIfPossible(context, file)
                Result(true, fileLabel = file.name)
            } catch (e: CancellationException) {
                runCatching { file.delete() }
                throw e
            }
        }

        return Result(false, errorMessage = KitsugiStrings.get(R.string.detail_video_download_error_no_stream))
    }

    /**
     * HLS manifest'ini media playlist'e indirger. Master playlist gelirse en yüksek
     * varyant seçilir; çözümleme mantığı extractor'ın `resolveHlsMediaPlaylist`
     * yardımcısında tek yerde tutulur (aynı YouTube istek başlıkları kullanılır).
     */
    private suspend fun fetchMediaPlaylist(
        manifestUrl: String
    ): YouTubeHlsMediaPlaylist? = InAppYouTubeExtractor.shared.resolveHlsMediaPlaylist(manifestUrl)

    private suspend fun downloadPlaylistAsFile(
        playlist: YouTubeHlsMediaPlaylist,
        destination: File,
        onProgress: (Float?, String) -> Unit,
        onStatus: (String) -> Unit
    ) {
        onStatus(KitsugiStrings.get(R.string.detail_video_download_started))
        val total = (playlist.segmentUrls.size + if (playlist.initSegmentUrl != null) 1 else 0)
            .coerceAtLeast(1)
        var completed = 0

        destination.outputStream().use { output ->
            // fMP4 init segmenti MUTLAKA önce yazılmalı.
            playlist.initSegmentUrl?.let { initUrl ->
                copyUrl(initUrl, output) { _, _ -> }
                completed++
                onProgress(completed.toFloat() / total, "")
            }
            for (segmentUrl in playlist.segmentUrls) {
                currentCoroutineContext().ensureActive()
                copyUrl(segmentUrl, output) { withinSegment, _ ->
                    // `withinSegment` 0..1 arası segment içi ilerleme; tamamlanan
                    // segmentlerle toplanınca çubuk sıçramadan ilerler.
                    val fraction = (completed + withinSegment) / total
                    onProgress(fraction.coerceIn(0f, 1f), "")
                }
                completed++
                onProgress(completed.toFloat() / total, "")
            }
        }

        if (destination.length() == 0L) {
            throw IOException("Boş dosya üretildi")
        }
    }

    // ── Doğrudan dosya yolu ───────────────────────────────────────────────────

    private suspend fun downloadDirect(
        context: Context,
        url: String,
        displayName: String,
        targetDir: File,
        onProgress: (Float?, String) -> Unit,
        onStatus: (String) -> Unit
    ): Result {
        onStatus(KitsugiStrings.get(R.string.detail_video_download_started))
        val extension = extensionOf(url)
        val file = uniqueFile(targetDir, displayName, extension)
        return try {
            downloadStreamToFile(url, file, 0f, 1f, onProgress)
            onProgress(1f, "")
            publishToGalleryIfPossible(context, file)
            Result(true, fileLabel = file.name)
        } catch (e: CancellationException) {
            runCatching { file.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { file.delete() }
            Result(false, errorMessage = e.message ?: KitsugiStrings.get(R.string.detail_video_download_error_generic))
        }
    }

    // ── Ağ yardımcıları ───────────────────────────────────────────────────────

    private fun requestFor(url: String): Request {
        val builder = Request.Builder().url(url)
        YouTubeRequestPolicy.headersForInlineUrl(url).forEach { (name, value) ->
            builder.addHeader(name, value)
        }
        return builder.build()
    }

    /**
     * [url] içeriğini [sink]'e kopyalar. [progress] geri çağrısı (dosya içi oran, toplam bayt)
     * alır; toplam boyut bilinmiyorsa oran 0..0 aralığında kalır.
     */
    private suspend fun copyUrl(
        url: String,
        sink: OutputStream,
        progress: (Float, Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        var written = 0L
        httpClient.newCall(requestFor(url)).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Boş yanıt")
            val total = body.contentLength().takeIf { it > 0L }
            val buffer = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    sink.write(buffer, 0, read)
                    written += read
                    if (written > MAX_FILE_BYTES) throw IOException("Dosya boyutu sınırı aşıldı")
                    progress(
                        if (total != null) (written.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f,
                        written
                    )
                }
            }
        }
        written
    }

    /**
     * Tek bir akışı dosyaya indirir. [scaleStart]–[scaleEnd] aralığı, çağıranın toplam
     * ilerlemesine göre ölçeklenir (ayrı video+ses indirmelerinde çubuk iki kez sıfırlanmasın).
     */
    private suspend fun downloadStreamToFile(
        url: String,
        destination: File,
        scaleStart: Float,
        scaleEnd: Float,
        onProgress: (Float?, String) -> Unit
    ) {
        destination.outputStream().use { output ->
            copyUrl(url, output) { fraction, _ ->
                val scaled = scaleStart + (scaleEnd - scaleStart) * fraction
                onProgress(scaled.coerceIn(0f, 1f), "")
            }
        }
        if (destination.length() == 0L) throw IOException("Boş dosya üretildi")
    }

    // ── Dosya yardımcıları ────────────────────────────────────────────────────

    private fun buildDisplayName(title: String, mediaTitle: String): String {
        val raw = if (mediaTitle.isBlank()) title else "${mediaTitle}_${title}"
        val sanitized = raw
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "video" }
        return sanitized.take(90)
    }

    private fun uniqueFile(dir: File, baseName: String, extension: String): File {
        dir.mkdirs()
        var candidate = File(dir, "$baseName.$extension")
        var counter = 1
        while (candidate.exists() && counter < 100) {
            candidate = File(dir, "${baseName}_$counter.$extension")
            counter++
        }
        return candidate
    }

    private fun extensionOf(url: String): String {
        val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault(url)
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "webm", "mp4", "mkv", "mov", "m4v", "ts", "m4a", "mp3" -> ext
            else -> "mp4"
        }
    }

    /**
     * İndirilen dosyayı galeri/dosya yöneticisi görsün diye MediaStore'a da bildirir.
     * Başarısız olursa indirme yine geçerlidir; bu yalnızca görünürlük içindir.
     */
    private fun publishToGalleryIfPossible(context: Context, file: File) {
        runCatching {
            android.media.MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                arrayOf("video/*"),
                null
            )
        }
    }

    /** Bildirim kimliği tabanı — bölüm indirmeleriyle (10000+) çakışmaz. */
    const val NOTIFICATION_ID_BASE = 7100
}
