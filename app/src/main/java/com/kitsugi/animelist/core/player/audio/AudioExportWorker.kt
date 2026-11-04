package com.kitsugi.animelist.core.player.audio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** A persistent, cancelable foreground job. Credentials remain in the app-private WorkManager DB. */
class AudioExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId = (id.hashCode() and 0x7fffffff).coerceAtLeast(10_001)
    private val channel = "kitsugi_audio_exports"
    private var reported = -1

    private fun notification(title: String, detail: String, progress: Int? = null, ongoing: Boolean = true) =
        NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.take(80))
            .setContentText(detail.take(120))
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .apply {
                if (progress != null && ongoing) setProgress(100, progress.coerceIn(0, 100), false)
                if (ongoing) addAction(android.R.drawable.ic_menu_close_clear_cancel, "İptal",
                    WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            }.build()

    override suspend fun doWork(): Result {
        val payload = inputData.getString("payload") ?: return Result.failure()
        val request = try { JSONObject(payload) } catch (_: Exception) { return Result.failure() }
        val title = request.optString("title").take(90).ifBlank { "Ses dosyası" }
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(channel, "Ses indirmeleri", NotificationManager.IMPORTANCE_LOW)
        )
        val initial = notification(title, "Ses dosyası hazırlanıyor")
        if (Build.VERSION.SDK_INT >= 29) setForeground(
            ForegroundInfo(notificationId, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        ) else setForeground(ForegroundInfo(notificationId, initial))

        val folder = File(applicationContext.cacheDir, "audio-export-$id")
        val sourceFile = File(folder, "source.part")
        val output = File(folder, "audio.part")
        try {
            // Don't leave old large partial downloads forever after a killed process.
            applicationContext.cacheDir.listFiles()?.filter {
                it.name.startsWith("audio-export-") && it != folder &&
                    System.currentTimeMillis() - it.lastModified() > 7L * 24 * 60 * 60 * 1000
            }?.forEach { it.deleteRecursively() }
            require(folder.isDirectory || folder.mkdirs()) { "Geçici indirme klasörü açılamadı" }
            val source = request.getString("source")
            val headers = request.getJSONObject("headers").let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            }
            val selected = AudioTrackIdentity(
                request.optString("language").takeIf { it.isNotBlank() },
                request.optString("name").takeIf { it.isNotBlank() },
                request.optString("external").takeIf { it.isNotBlank() }
            )
            val other = request.getJSONArray("tracks")
            val tracks = (0 until other.length()).map { n ->
                other.getJSONObject(n).let { AudioTrackIdentity(it.optString("lang"), it.optString("name"),
                    it.optString("external").takeIf(String::isNotBlank)) }
            }
            val declared = request.optString("transport").takeIf { it in setOf("hls", "dash", "progressive") }
            val known = AudioExportTransport.known(source, declared)
            val inspectFlv = known == "flv" &&
                (source.startsWith("http://", true) || source.startsWith("https://", true))
            val safeCopy = known == null && AudioExportTransport.requiresSafeCopy(headers) &&
                (source.startsWith("http://", true) || source.startsWith("https://", true))
            val inspected = if (inspectFlv || safeCopy ||
                known == null && AudioExportTransport.needsSniff(source, declared))
                AudioExportHttp.inspect(source, headers, sourceFile, ::progress) else null
            val transport = inspected?.transport ?: known
            var independent = request.optBoolean("independent", false)
            var manifestSelectedAudio = false
            val input = when (transport) {
                "hls" -> {
                    val separate = HlsAudioExport.materialize(source, headers, selected, tracks, sourceFile,
                        ::progress, initial = inspected?.manifest)
                    manifestSelectedAudio = separate
                    independent = independent || separate
                    sourceFile.absolutePath
                }
                "dash" -> {
                    DashAudioExport.materialize(source, headers, selected, tracks,
                        request.optInt("bitrate").takeIf { it > 0 }, sourceFile, ::progress,
                        initial = inspected?.manifest)
                    manifestSelectedAudio = true
                    independent = true
                    sourceFile.absolutePath
                }
                "flv" -> when {
                    inspected?.localFile != null -> inspected.localFile.absolutePath
                    source.startsWith("http://", true) || source.startsWith("https://", true) -> {
                        sourceFile.outputStream().buffered().use { out ->
                            AudioExportHttp.append(source, source, headers, null, out, folder, 0)
                        }
                        sourceFile.absolutePath
                    }
                    source.startsWith("file://", true) -> File(java.net.URI(source)).absolutePath
                    source.startsWith('/') -> source
                    source.startsWith("content://", true) -> {
                        val uri = android.net.Uri.parse(source)
                        val stream = applicationContext.contentResolver.openInputStream(uri)
                            ?: error("FLV dosyası açılamadı")
                        stream.use { local -> sourceFile.outputStream().buffered().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var count = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = local.read(buffer)
                                if (n < 0) break
                                require(count + n <= AudioExportHttp.MAX_TEMP_BYTES) { "FLV dosyası çok büyük" }
                                out.write(buffer, 0, n)
                                count += n
                            }
                        } }
                        sourceFile.absolutePath
                    }
                    else -> error("FLV adres türü desteklenmiyor")
                }
                else -> {
                    require(!AudioExportSpec.needsManifestName(selected, tracks)) {
                        "Aynı dilde birden çok gömülü ses var; yanlış parça kaydedilmedi"
                    }
                    inspected?.localFile?.absolutePath ?: source
                }
            }
            progress(72)
            val sharedFileRows = tracks.count { it.externalUrl == selected.externalUrl }
            val result = try {
                if (transport == "flv" && sharedFileRows == 1) {
                    val jobContext = currentCoroutineContext()
                    try {
                        FlvAudioExport.export(File(input), output) { jobContext.ensureActive() }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (first: Exception) {
                        // Native extractors may support another FLV codec; never publish the
                        // partial ADTS/MP3 from the rejected pure demux attempt.
                        output.delete()
                        try {
                            AudioDemuxer.export(applicationContext, input, emptyMap(), selected.language,
                                output, true, independent, requireKnownLanguage = false)
                        } catch (second: Exception) {
                            second.addSuppressed(first)
                            throw second
                        }
                    }
                } else AudioDemuxer.export(applicationContext, input,
                    if (input == source) headers else emptyMap(), selected.language,
                    output, transport != null || inspected?.localFile != null, independent,
                    requireKnownLanguage = sharedFileRows > 1 && !manifestSelectedAudio)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (mismatch: AudioTrackSelectionException) {
                // A validated standalone file must not bypass a known language/identity mismatch.
                throw mismatch
            } catch (original: Exception) {
                if (!independent) throw original
                output.delete()
                try {
                    StandaloneAudio.copy(applicationContext, input, if (input == source) headers else emptyMap(), output)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    throw original
                }
            }
            require(output.length() > 0) { "Ses dosyası boş" }
            progress(94)
            val basename = AudioExportSpec.filenamePart(title, "Video") +
                if (request.optBoolean("movie", false)) "" else " - Bölüm ${request.optInt("episode", 1)}"
            val audioLabel = listOfNotNull(selected.language, selected.name)
                .filter { it.isNotBlank() }.distinct().joinToString(" ").ifBlank { "Ses" }
            val filename = AudioExportSpec.filenamePart(basename, "Video") + "." +
                AudioExportSpec.filenamePart(audioLabel, "Ses") + ".${result.extension}"
            val pathSaved = publish(output, filename, result.mime)
            progress(100)
            notifications.notify(notificationId, notification(title, "Kaydedildi: $pathSaved", ongoing = false))
            return Result.success(workDataOf("saved" to pathSaved))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            // Native/HTTP exceptions can include signed URLs or provider credentials. Never
            // show raw exception text in a notification or persist it as WorkManager output.
            val detail = when (e) {
                is IllegalArgumentException, is IllegalStateException ->
                    e.message?.takeIf { !it.contains("://") && !it.contains("Bearer ", true) }
                        ?.take(100) ?: "Ses kaynağı güvenle işlenemedi"
                is IOException -> "Ağ bağlantısı veya medya kaynağı başarısız. Kaynağı yenileyin."
                else -> "Bu ses biçimi cihazda çıkarılamadı"
            }
            notifications.notify(notificationId, notification(title, "İndirilemedi: $detail", ongoing = false))
            return Result.failure(workDataOf("error" to detail))
        } finally {
            folder.deleteRecursively()
        }
    }

    private suspend fun progress(value: Int) {
        val bounded = value.coerceIn(0, 100)
        if (bounded <= reported) return
        reported = bounded
        setProgress(workDataOf("percent" to bounded))
        notifications.notify(notificationId, notification(inputData.getString("title") ?: "Ses indiriliyor",
            "Ses kaydediliyor · %$bounded", bounded))
    }

    private suspend fun publish(file: File, name: String, mime: String): String {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Kitsugi/Audio")
                put(MediaStore.Downloads.IS_PENDING, 1)
                // If the process is killed mid-copy, MediaStore can expire the hidden partial.
                put(MediaStore.Downloads.DATE_EXPIRES, System.currentTimeMillis() / 1000 + 86_400)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Ses için indirme alanı açılamadı")
            try {
                val stream = context.contentResolver.openOutputStream(uri) ?: error("İndirme dosyası açılamadı")
                stream.use { out -> file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                    }
                } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                values.putNull(MediaStore.Downloads.DATE_EXPIRES)
                check(context.contentResolver.update(uri, values, null, null) > 0) { "İndirme yayımlanamadı" }
                val actualName = runCatching { context.contentResolver.query(
                    uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } }.getOrNull() ?: name
                return "Downloads/Kitsugi/Audio/$actualName"
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            error("Android 9 ve öncesinde depolama izni gerekli")
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi/Audio")
        require(dir.isDirectory || dir.mkdirs()) { "İndirme klasörü oluşturulamadı" }
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.')
        val dest = (0..100).map { index -> File(dir, "$stem${if (index == 0) "" else " ($index)"}.$ext") }
            .firstOrNull { it.createNewFile() } ?: error("Boş dosya adı bulunamadı")
        try {
            dest.outputStream().use { out -> file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    out.write(buffer, 0, count)
                }
            } }
            return dest.absolutePath
        } catch (e: Exception) { dest.delete(); throw e }
    }

    companion object {
        internal fun enqueue(context: Context, source: String, headers: Map<String, String>, selected: AudioTrackIdentity,
                    tracks: List<AudioTrackIdentity>, independent: Boolean, title: String,
                    episode: Int, movie: Boolean, bitrate: Int?, transport: String? = null) {
            require(transport == null || transport in setOf("hls", "dash", "progressive")) { "Medya türü geçersiz" }
            val safeTitle = title.take(90)
            val payload = JSONObject().apply {
                put("source", source)
                put("headers", JSONObject(headers))
                put("language", selected.language)
                put("name", selected.name)
                put("external", selected.externalUrl)
                put("tracks", JSONArray().apply { tracks.forEach { row -> put(JSONObject().apply {
                    put("lang", row.language); put("name", row.name); put("external", row.externalUrl)
                }) } })
                put("independent", independent)
                put("title", safeTitle)
                put("episode", episode)
                put("movie", movie)
                put("bitrate", bitrate)
                put("transport", transport)
            }.toString()
            require(payload.toByteArray(Charsets.UTF_8).size < 9_000) { "Kaynak başlıkları indirme kuyruğu için çok uzun" }
            val network = source.startsWith("http://", true) || source.startsWith("https://", true)
            val data: Data = workDataOf("payload" to payload, "title" to safeTitle)
            val work = OneTimeWorkRequestBuilder<AudioExportWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(
                    if (network) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED).build())
                .addTag("kitsugi-audio-export").build()
            WorkManager.getInstance(context).enqueue(work)
        }
    }
}
