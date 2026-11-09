package com.kitsugi.animelist.data.ambient

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.kitsugi.animelist.data.trailer.InAppYouTubeExtractor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class AmbientAudioDownloadStatus {
    QUEUED,
    DOWNLOADING,
    CONVERTING,
    SAVING,
    COMPLETED,
    FAILED,
}

internal data class AmbientAudioDownloadState(
    val status: AmbientAudioDownloadStatus,
    val progress: Float = 0f,
    val converted: Boolean = false,
    val contentUri: String? = null,
    val message: String? = null,
)

/** Direct-file downloads for providers that expose a real file URL; live radio is never downloadable. */
@OptIn(UnstableApi::class)
internal object AmbientAudioDownloadManager {
    private const val MAX_DOWNLOAD_BYTES = 250L * 1024L * 1024L
    private const val YOUTUBE_PREFIX = "kitsugi-yt://"
    private const val AAC_BIT_RATE = 128_000
    private const val AAC_SAMPLE_RATE = 44_100
    private const val AAC_CHANNEL_COUNT = 2
    private val MUSIC_RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/Kitsugi/Ambient"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = ConcurrentHashMap.newKeySet<String>()
    private val mutableDownloads = MutableStateFlow<Map<String, AmbientAudioDownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, AmbientAudioDownloadState>> = mutableDownloads.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    fun start(context: Context, track: AmbientAudioTrack): Boolean {
        // v2.4.484 — YouTube kaynaklı parçalar da indirilebilir: akış adresi indirme
        // anında çözülür ve dosya cihaza kalıcı olarak yazılır. İndirilen içerik
        // süresi dolan imzalı adresten bağımsızdır.
        val directUrl = track.downloadUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }
        val youTubeId = youTubeVideoId(track)
        if (directUrl == null && youTubeId == null) return false
        if (track.provider.equals("radio_browser", ignoreCase = true)) return false
        if (directUrl != null && !AmbientAudioFormats.isDirectAudioFile(track.fileType)) return false
        if (!activeDownloads.add(track.id)) return false

        mutableDownloads.update { it + (track.id to AmbientAudioDownloadState(AmbientAudioDownloadStatus.QUEUED)) }
        val appContext = context.applicationContext
        scope.launch {
            try {
                val resolved = if (directUrl != null) {
                    directUrl to emptyMap()
                } else {
                    resolveYouTubeStream(youTubeId.orEmpty())
                } ?: throw IOException("Parçanın ses adresi çözülemedi")
                val state = downloadAndPublish(appContext, track, resolved.first, resolved.second)
                if (state.status == AmbientAudioDownloadStatus.COMPLETED && state.contentUri != null) {
                    preferences(appContext).edit()
                        .putString(savedUriKey(track.id), state.contentUri)
                        .putBoolean(savedConvertedKey(track.id), state.converted)
                        .putString(savedMessageKey(track.id), state.message)
                        .apply()
                }
                mutableDownloads.update { it + (track.id to state) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableDownloads.update {
                    it + (track.id to AmbientAudioDownloadState(
                        status = AmbientAudioDownloadStatus.FAILED,
                        message = error.message.orEmpty().take(200),
                    ))
                }
            } finally {
                activeDownloads.remove(track.id)
            }
        }
        return true
    }

    fun restoreSaved(context: Context, track: AmbientAudioTrack) {
        if (track.id in activeDownloads ||
            mutableDownloads.value[track.id]?.status == AmbientAudioDownloadStatus.COMPLETED
        ) return
        val appContext = context.applicationContext
        scope.launch {
            val prefs = preferences(appContext)
            val savedUri = prefs.getString(savedUriKey(track.id), null) ?: return@launch
            val uri = Uri.parse(savedUri)
            val readable = withContext(Dispatchers.IO) {
                runCatching {
                    when (uri.scheme) {
                        "content" -> appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                        "file" -> File(uri.path.orEmpty()).isFile
                        else -> false
                    }
                }.getOrDefault(false)
            }
            if (track.id in activeDownloads ||
                mutableDownloads.value[track.id]?.status == AmbientAudioDownloadStatus.COMPLETED
            ) return@launch
            if (!readable) {
                prefs.edit()
                    .remove(savedUriKey(track.id))
                    .remove(savedConvertedKey(track.id))
                    .remove(savedMessageKey(track.id))
                    .apply()
                return@launch
            }
            mutableDownloads.update {
                it + (track.id to AmbientAudioDownloadState(
                    status = AmbientAudioDownloadStatus.COMPLETED,
                    progress = 1f,
                    converted = prefs.getBoolean(savedConvertedKey(track.id), false),
                    contentUri = savedUri,
                    message = prefs.getString(savedMessageKey(track.id), null),
                ))
            }
        }
    }

    /** YouTube işaretçisinden video kimliği (`kitsugi-yt://<id>` → `<id>`). */
    private fun youTubeVideoId(track: AmbientAudioTrack): String? =
        track.streamUrl
            .takeIf { it.startsWith(YOUTUBE_PREFIX, ignoreCase = true) }
            ?.substringAfter(YOUTUBE_PREFIX)
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }

    /**
     * İndirme anında gerçek ses adresini + başlıkları çözer. İndirilen dosya
     * cihaza yazıldığı için imzalı adresin süresi dolması sonucu etkilemez.
     */
    private suspend fun resolveYouTubeStream(videoId: String): Pair<String, Map<String, String>>? {
        if (videoId.isBlank()) return null
        val source = InAppYouTubeExtractor.shared.extractPlaybackSource(
            "https://www.youtube.com/watch?v=$videoId"
        ) ?: return null
        val url = source.audioUrl ?: source.videoUrl
        // Manifest (HLS/DASH) adresleri tek dosya olarak indirilemez; yalnızca
        // doğrudan ses/video akışı indirilir.
        if (url.contains("m3u8", ignoreCase = true) ||
            url.contains(".mpd", ignoreCase = true)
        ) return null
        return url to source.requestHeaders
    }

    private suspend fun downloadAndPublish(
        context: Context,
        track: AmbientAudioTrack,
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
    ): AmbientAudioDownloadState = withContext(Dispatchers.IO) {
        if (track.fileSizeBytes != null && track.fileSizeBytes > MAX_DOWNLOAD_BYTES) {
            throw IOException("Audio file exceeds the 250 MB offline-download limit")
        }
        val extension = AmbientAudioFormats.fileExtension(track.fileType).ifBlank { "audio" }
        val fileKey = digest(track.id).take(16)
        val original = File(context.cacheDir, "ambient_$fileKey.$extension")
        val convertedFile = File(context.cacheDir, "ambient_$fileKey.m4a")
        original.delete()
        convertedFile.delete()

        try {
            mutableDownloads.update {
                it + (track.id to AmbientAudioDownloadState(AmbientAudioDownloadStatus.DOWNLOADING))
            }
            downloadFile(url, original, requestHeaders) { progress ->
                mutableDownloads.update {
                    it + (track.id to AmbientAudioDownloadState(
                        status = AmbientAudioDownloadStatus.DOWNLOADING,
                        progress = progress,
                    ))
                }
            }

            var output = original
            var outputType = extension
            var converted = false
            var conversionNote: String? = null
            if (needsPortableCopy(original, extension)) {
                if (canEncodeAac()) {
                    mutableDownloads.update {
                        it + (track.id to AmbientAudioDownloadState(AmbientAudioDownloadStatus.CONVERTING))
                    }
                    try {
                        output = transcodeToAac(context, original, convertedFile)
                        outputType = "m4a"
                        converted = true
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the provider's original file if this device cannot decode/encode this input.
                        conversionNote = "AAC conversion was unavailable; the original audio file was saved."
                    }
                } else {
                    conversionNote = "This device has no AAC encoder; the original audio file was saved."
                }
            }

            mutableDownloads.update {
                it + (track.id to AmbientAudioDownloadState(
                    status = AmbientAudioDownloadStatus.SAVING,
                    progress = 1f,
                    converted = converted,
                ))
            }
            val uri = publish(context, output, track, outputType)
            AmbientAudioDownloadState(
                status = AmbientAudioDownloadStatus.COMPLETED,
                progress = 1f,
                converted = converted,
                contentUri = uri.toString(),
                message = conversionNote,
            )
        } finally {
            original.delete()
            convertedFile.delete()
        }
    }

    private fun downloadFile(
        url: String,
        destination: File,
        requestHeaders: Map<String, String> = emptyMap(),
        onProgress: (Float) -> Unit,
    ) {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "audio/*, application/octet-stream;q=0.9, */*;q=0.5")
            .header("User-Agent", "KitsugiBeta/2.x (Android ambient audio offline download)")
        // YouTube imzalı adresleri, adresi üreten istemcinin UA'sını bekler.
        requestHeaders.forEach { (name, value) -> builder.header(name, value) }
        val request = builder.get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Audio download failed: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Audio download returned no file")
            val contentType = body.contentType()?.let { "${it.type}/${it.subtype}".lowercase() }.orEmpty()
            if (contentType.startsWith("text/") || contentType.contains("json") || contentType.contains("xml")) {
                throw IOException("The provider returned a page instead of an audio file")
            }
            val totalBytes = body.contentLength()
            if (totalBytes > MAX_DOWNLOAD_BYTES) throw IOException("Audio file exceeds the 250 MB offline-download limit")
            var downloaded = 0L
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastReported = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > MAX_DOWNLOAD_BYTES) throw IOException("Audio file exceeds the 250 MB offline-download limit")
                        output.write(buffer, 0, count)
                        if (downloaded - lastReported >= PROGRESS_UPDATE_BYTES) {
                            lastReported = downloaded
                            if (totalBytes > 0L) onProgress((downloaded.toFloat() / totalBytes).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
            if (destination.length() <= 0L) throw IOException("Audio download was empty")
            onProgress(1f)
        }
    }

    private fun needsPortableCopy(file: File, extension: String): Boolean {
        val format = readAudioFormat(file)
        val decoderAvailable = format?.let {
            runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(it) != null
            }.getOrDefault(false)
        }
        return AmbientAudioFormats.shouldAttemptAacConversion(extension, decoderAvailable)
    }

    private fun readAudioFormat(file: File): MediaFormat? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount)
                .map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        } finally {
            extractor.release()
        }
    }.getOrNull()

    private fun canEncodeAac(): Boolean = runCatching {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AAC_SAMPLE_RATE,
            AAC_CHANNEL_COUNT,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) != null
    }.getOrDefault(false)

    private suspend fun transcodeToAac(context: Context, input: File, output: File): File =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                output.delete()
                val transformer = Transformer.Builder(context.applicationContext)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (!continuation.isActive) return
                            if (output.isFile && output.length() > 0L) continuation.resume(output)
                            else continuation.resumeWithException(IOException("AAC conversion returned an empty file"))
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    })
                    .build()
                continuation.invokeOnCancellation {
                    scope.launch(Dispatchers.Main.immediate) { runCatching { transformer.cancel() } }
                    output.delete()
                }
                val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                    .setRemoveVideo(true)
                    .build()
                try {
                    transformer.start(editedItem, output.absolutePath)
                } catch (error: Exception) {
                    output.delete()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }

    private fun publish(
        context: Context,
        sourceFile: File,
        track: AmbientAudioTrack,
        fileType: String,
    ): Uri {
        val extension = AmbientAudioFormats.fileExtension(fileType).ifBlank { "audio" }
        val fileName = "${safeFileStem(track.title)}_${digest(track.id).take(8)}.$extension"
        val mimeType = AmbientAudioFormats.mimeType(fileType)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(context, sourceFile, fileName, mimeType)
        } else {
            publishToAppMusicFolder(context, sourceFile, fileName)
        }
    }

    private fun publishToMediaStore(context: Context, sourceFile: File, fileName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, MUSIC_RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create an offline audio entry")
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> sourceFile.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("Could not write the offline audio file")
            val completed = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, completed, null, null)
            return uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun publishToAppMusicFolder(context: Context, sourceFile: File, fileName: String): Uri {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
            "Kitsugi/Ambient",
        )
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create the offline audio folder")
        val destination = File(directory, fileName)
        sourceFile.copyTo(destination, overwrite = true)
        return Uri.fromFile(destination)
    }

    private fun safeFileStem(value: String): String {
        val stem = value.trim()
            .replace(Regex("[^\\p{L}\\p{N}._ -]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.')
            .take(72)
        return stem.ifBlank { "ambient_audio" }
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(16)
        .joinToString("") { "%02x".format(it) }

    private fun preferences(context: Context) =
        context.getSharedPreferences(DOWNLOAD_PREFERENCES, Context.MODE_PRIVATE)

    private fun savedUriKey(trackId: String) = "saved_uri_${digest(trackId)}"
    private fun savedConvertedKey(trackId: String) = "saved_converted_${digest(trackId)}"
    private fun savedMessageKey(trackId: String) = "saved_message_${digest(trackId)}"

    private const val DOWNLOAD_PREFERENCES = "ambient_audio_downloads"
    private const val PROGRESS_UPDATE_BYTES = 256L * 1024L
}
