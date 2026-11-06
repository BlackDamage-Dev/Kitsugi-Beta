package com.kitsugi.animelist.core.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

object SubtitleFileCache {
    private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024 // 50 MB
    private const val MAX_SINGLE_SUBTITLE_BYTES = 20 * 1024 * 1024 // 20 MB

    private val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(com.kitsugi.animelist.core.network.IPv4FirstDns())
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, "subtitles")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCachedFile(
        context: Context,
        url: String,
        headers: Map<String, String>? = null
    ): File? {
        val hash = cacheKey(url, headers) ?: return null
        val dir = getCacheDir(context)
        val file = dir.listFiles()?.find { it.name.startsWith("$hash.") && it.isFile }
        if (file != null) {
            file.setLastModified(System.currentTimeMillis())
            // Eski sürümler uzantısı olmayan veya sıkıştırılmış (.gz/.zip) yanıtları ham
            // olarak .srt diye saklayabiliyordu; mevcut önbellek girdisini gerekirse açıp düzelt.
            val rawCached = runCatching { file.readBytes() }.getOrNull()
            if (rawCached != null && rawCached.isNotEmpty()) {
                val (unpacked, entryName) = unpackCompressedSubtitleIfNeeded(rawCached)
                val detected = TrackFormatUtils.detectSubtitleContentFormat(unpacked)
                    ?: entryName?.let { guessExtension(it, null) }
                if (unpacked !== rawCached && unpacked.isNotEmpty()) {
                    val targetExt = detected ?: file.extension.ifBlank { "srt" }
                    val corrected = File(dir, "$hash.$targetExt")
                    runCatching {
                        corrected.writeBytes(unpacked)
                        if (corrected.absolutePath != file.absolutePath) file.delete()
                    }
                    if (corrected.isFile && corrected.length() > 0L) return corrected
                } else if (detected != null && !detected.equals(file.extension, ignoreCase = true)) {
                    val corrected = File(dir, "$hash.$detected")
                    if (corrected.exists() || file.renameTo(corrected)) {
                        return corrected
                    }
                }
            }
        }
        return file
    }

    suspend fun cacheSubtitle(
        context: Context,
        url: String,
        headers: Map<String, String>? = null,
        clientOverride: OkHttpClient? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            // Yerel Dosya ("file:///..." veya "/data/...") çoktan bu cihazda:
            // OkHttp bu şemaları indiremez; dosyayı olduğu gibi kabul et.
            // (AniSub cihazda açılan RAR/ZIP paketleri bu yoldan gelir.)
            val localFile = resolveLocalSubtitleFile(url)
            if (localFile != null) {
                if (localFile.isFile && localFile.length() > 0) {
                    localFile.setLastModified(System.currentTimeMillis())
                    Log.d("SubtitleFileCache", "Yerel altyazı kullanılıyor: ${localFile.absolutePath}")
                    return@withContext localFile
                }
                Log.w("SubtitleFileCache", "Yerel altyazı bulunamadı/boş: $url")
                return@withContext null
            }

            val existing = getCachedFile(context, url, headers)
            if (existing != null && existing.length() > 0) {
                Log.d("SubtitleFileCache", "Cache hit: ${existing.name}")
                return@withContext existing
            }

            Log.d("SubtitleFileCache", "Cache miss, downloading subtitle: $url")
            val client = clientOverride ?: defaultHttpClient

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .apply {
                    headers?.forEach { (k, v) ->
                        if (k.isNotBlank() && v.isNotBlank()) runCatching { header(k, v) }
                    }
                }
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SubtitleFileCache", "Failed to download subtitle, HTTP ${response.code}: $url")
                    return@withContext null
                }

                val rawBytes = response.body?.bytes() ?: return@withContext null
                if (rawBytes.isEmpty() || rawBytes.size > MAX_SINGLE_SUBTITLE_BYTES) {
                    return@withContext null
                }
                val (bodyBytes, extractedEntryName) = unpackCompressedSubtitleIfNeeded(rawBytes)
                if (bodyBytes.isEmpty()) return@withContext null

                // İçerik imzası > arşiv içindeki dosya adı > Content-Type > URL uzantısı.
                val ext = TrackFormatUtils.detectSubtitleContentFormat(bodyBytes)
                    ?: extractedEntryName?.let { guessExtension(it, null) }
                    ?: guessExtension(url, response.header("Content-Type"))
                val hash = cacheKey(url, headers) ?: return@withContext null
                val cacheDir = getCacheDir(context)
                val tempFile = File(cacheDir, "${hash}_${Thread.currentThread().id}_${System.nanoTime()}.$ext")
                tempFile.writeBytes(bodyBytes)

                val finalFile = File(cacheDir, "$hash.$ext")
                val stored = if (tempFile.renameTo(finalFile)) {
                    true
                } else {
                    runCatching {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                        finalFile.isFile && finalFile.length() > 0L
                    }.getOrDefault(false)
                }
                if (stored) {
                    Log.d("SubtitleFileCache", "Successfully cached subtitle to ${finalFile.name}")
                    runLruCleanup(context)
                    return@withContext finalFile
                } else {
                    tempFile.delete()
                }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (e: Exception) {
            Log.e("SubtitleFileCache", "Error caching subtitle: $url", e)
        }
        null
    }

    /**
     * Stremio altyazı eklentilerinin (YTS, OpenSubtitles .gz, SubDL, TurkceAltyazi vb.)
     * sıkıştırılmış `.gz` veya `.zip` yanıtlarını otomatik açar.
     */
    internal fun unpackCompressedSubtitleIfNeeded(rawBytes: ByteArray): Pair<ByteArray, String?> {
        if (rawBytes.size >= 2 &&
            rawBytes[0] == 0x1F.toByte() &&
            rawBytes[1] == 0x8B.toByte()
        ) {
            val gunzipped = runCatching {
                java.util.zip.GZIPInputStream(rawBytes.inputStream()).use { gis ->
                    readBoundedBytes(gis, MAX_SINGLE_SUBTITLE_BYTES)
                }
            }.getOrNull()
            if (gunzipped != null && gunzipped.isNotEmpty()) {
                return gunzipped to null
            }
        }
        if (rawBytes.size >= 4 &&
            rawBytes[0] == 0x50.toByte() &&
            rawBytes[1] == 0x4B.toByte() &&
            rawBytes[2] == 0x03.toByte() &&
            rawBytes[3] == 0x04.toByte()
        ) {
            val subtitleExtensions = setOf("ass", "ssa", "srt", "vtt", "ttml", "dfxp", "sub")
            runCatching {
                java.util.zip.ZipInputStream(rawBytes.inputStream()).use { zis ->
                    var fallbackBytes: ByteArray? = null
                    var fallbackName: String? = null
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (!entry.isDirectory) {
                            val name = entry.name
                            val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
                            val entryBytes = readBoundedBytes(zis, MAX_SINGLE_SUBTITLE_BYTES)
                            if (entryBytes.isNotEmpty()) {
                                if (ext in subtitleExtensions) {
                                    return entryBytes to name
                                }
                                if (fallbackBytes == null) {
                                    fallbackBytes = entryBytes
                                    fallbackName = name
                                }
                            }
                        }
                        zis.closeEntry()
                    }
                    if (fallbackBytes != null) return fallbackBytes!! to fallbackName
                }
            }
        }
        return rawBytes to null
    }

    private fun readBoundedBytes(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return ByteArray(0)
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * "file:///..." veya mutlak yol biçimindeki yerel altyazı adresini
     * [File] olarak çözer; uzak adreslerde null döner.
     */
    private fun resolveLocalSubtitleFile(url: String): File? {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("file://", ignoreCase = true) -> {
                val path = android.net.Uri.parse(trimmed).path ?: trimmed.substring("file://".length)
                if (path.isBlank()) null else File(path)
            }
            trimmed.startsWith("file:/", ignoreCase = true) ->
                File(trimmed.substring("file:".length))
            trimmed.startsWith("/") -> File(trimmed.substringBefore('?'))
            else -> null
        }
    }

    fun guessExtension(url: String, contentType: String? = null): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val cType = contentType?.lowercase() ?: ""
        return when {
            path.endsWith(".ass") || path.endsWith(".ssa") || cType.contains("ass") || cType.contains("ssa") -> "ass"
            path.endsWith(".vtt") || cType.contains("vtt") -> "vtt"
            path.endsWith(".srt") || cType.contains("subrip") || cType.contains("srt") -> "srt"
            path.endsWith(".ttml") || cType.contains("ttml") -> "ttml"
            path.endsWith(".dfxp") || cType.contains("dfxp") -> "dfxp"
            path.endsWith(".sub")  || cType.contains("sub") -> "sub"
            path.endsWith(".txt") -> "txt"
            url.contains(".ass", ignoreCase = true) || url.contains(".ssa", ignoreCase = true) -> "ass"
            url.contains(".vtt", ignoreCase = true) -> "vtt"
            url.contains(".srt", ignoreCase = true) -> "srt"
            else -> "srt"
        }
    }

    private fun cacheKey(url: String, headers: Map<String, String>?): String? {
        if (headers.isNullOrEmpty()) return md5(url)
        val stableHeaders = headers.entries
            .sortedBy { it.key.lowercase(java.util.Locale.ROOT) }
            .joinToString("\n") { "${it.key.lowercase(java.util.Locale.ROOT)}:${it.value}" }
        return md5("$url\n$stableHeaders")
    }

    private fun md5(input: String): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun runLruCleanup(context: Context) {
        val dir = getCacheDir(context)
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_SIZE_BYTES) return

        val sortedFiles = files.sortedBy { it.lastModified() }
        for (file in sortedFiles) {
            val size = file.length()
            if (file.delete()) {
                totalSize -= size
                Log.d("SubtitleFileCache", "Cleaned up cached subtitle: ${file.name}")
                if (totalSize <= MAX_CACHE_SIZE_BYTES) break
            }
        }
    }
}
