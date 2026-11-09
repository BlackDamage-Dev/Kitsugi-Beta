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

    fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, "subtitles")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCachedFile(context: Context, url: String): File? {
        val hash = md5(url) ?: return null
        val dir = getCacheDir(context)
        val file = dir.listFiles()?.find { it.name.startsWith("$hash.") && it.isFile }
        if (file != null) {
            file.setLastModified(System.currentTimeMillis())
        }
        return file
    }

    suspend fun cacheSubtitle(context: Context, url: String, headers: Map<String, String>? = null, language: String? = null): File? = withContext(Dispatchers.IO) {
        try {
            // AniSub has already extracted these files locally. Do not send file:// to OkHttp.
            if (url.startsWith("file://")) {
                val local = File(java.net.URI(url)).canonicalFile
                val root = File(context.cacheDir, "anisub_subs").canonicalFile
                if (!local.path.startsWith(root.path + File.separator) || !local.isFile ||
                    local.length() !in 1..(5L * 1024 * 1024)) return@withContext null
                val original = local.readBytes()
                val normalized = SubtitleEncoding.toUtf8(original, language = language)
                if (!SubtitleContent.isUsable(normalized)) return@withContext null
                if (normalized.contentEquals(original)) return@withContext local
                val hash = md5(url) ?: return@withContext null
                return@withContext writeCacheFile(context, hash, local.extension.ifBlank { "srt" }, normalized)
            }
            if (!url.startsWith("https://") && !url.startsWith("http://")) return@withContext null
            val existing = getCachedFile(context, url)
            if (existing != null) {
                if (existing.length() in 1..(5L * 1024 * 1024)) {
                    val original = existing.readBytes()
                    val normalized = SubtitleEncoding.toUtf8(original, language = language)
                    if (SubtitleContent.isUsable(normalized)) {
                        if (original.contentEquals(normalized)) return@withContext existing
                        val hash = md5(url) ?: return@withContext null
                        return@withContext writeCacheFile(context, hash, existing.extension.ifBlank { "srt" }, normalized)
                    }
                }
                existing.delete()
            }

            Log.d("SubtitleFileCache", "Cache miss, downloading subtitle: $url")
            val client = OkHttpClient.Builder()
                .dns(com.kitsugi.animelist.core.network.IPv4FirstDns())
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .apply {
                    headers?.forEach { (k, v) -> header(k, v) }
                }
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SubtitleFileCache", "Failed to download subtitle, HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val maxBytes = 5 * 1024 * 1024
                if (body.contentLength() > maxBytes) return@withContext null
                val bodyBytes = body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > maxBytes) return@withContext null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val normalizedBytes = SubtitleEncoding.toUtf8(bodyBytes, response.header("Content-Type"), language)
                if (!SubtitleContent.isUsable(normalizedBytes)) {
                    Log.w("SubtitleFileCache", "Provider returned an empty response, error page, or unsupported archive")
                    return@withContext null
                }
                val prefix = normalizedBytes.take(512).toByteArray().toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
                val ext = when {
                    prefix.startsWith("webvtt") -> "vtt"
                    prefix.startsWith("[script info]") -> "ass"
                    else -> guessExtension(url, response.header("Content-Type"))
                }
                val hash = md5(url) ?: return@withContext null
                return@withContext writeCacheFile(context, hash, ext, normalizedBytes)
            }
        } catch (e: Exception) {
            Log.e("SubtitleFileCache", "Error caching subtitle: $url", e)
        }
        null
    }

    private fun writeCacheFile(context: Context, hash: String, extension: String, bytes: ByteArray): File? {
        if (bytes.size > 5 * 1024 * 1024) return null
        val dir = getCacheDir(context)
        val tempFile = File.createTempFile("${hash}_", ".tmp", dir)
        return try {
            tempFile.writeBytes(bytes)
            val finalFile = File(dir, "$hash.$extension")
            if (!tempFile.renameTo(finalFile)) return null
            runLruCleanup(context)
            finalFile
        } finally {
            tempFile.delete()
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
