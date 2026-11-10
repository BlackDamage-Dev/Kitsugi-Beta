package com.kitsugi.animelist.core.player

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OpenSubtitlesHasher {
    private const val HASH_CHUNK_SIZE = 65536 // 64 KB

    data class HashResult(val hash: String, val size: Long)

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(limit)
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (n < 0) break
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }


    internal fun validRange(code: Int, contentRange: String?, start: Long, end: Long, size: Long): Boolean =
        code == 206 && contentRange?.trim()?.equals("bytes $start-$end/$size", ignoreCase = true) == true

    internal fun canProbeUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) return false
        val lower = trimmed.lowercase(java.util.Locale.ROOT)
        if ((lower.contains("://127.0.0.1") || lower.contains("://localhost") || lower.contains("://[::1]")) &&
            (lower.contains(":11470") || lower.contains("/torrent/"))
        ) {
            return false
        }
        val path = lower.substringBefore('?').substringBefore('#')
        if (path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".ism")) {
            return false
        }
        return true
    }

    fun compute(url: String, headers: Map<String, String>? = null): HashResult? {
        if (!canProbeUrl(url)) {
            return null
        }
        // Playlists/segments are not complete files and cannot yield a meaningful movie hash.
        val path = url.substringBefore('?').lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return null
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // 1. Get content length (size)
            val headRequest = Request.Builder()
                .url(url)
                .apply {
                    headers?.forEach { (k, v) -> header(k, v) }
                }
                .head()
                .build()

            val size = client.newCall(headRequest).execute().use { response ->
                if (!response.isSuccessful) return null
                response.header("Content-Length")?.toLongOrNull()
            } ?: return null

            if (size < HASH_CHUNK_SIZE * 2) return null

            // 2. Fetch first 64KB
            val firstRangeRequest = Request.Builder()
                .url(url)
                .apply {
                    headers?.forEach { (k, v) -> header(k, v) }
                    header("Range", "bytes=0-${HASH_CHUNK_SIZE - 1}")
                }
                .build()

            val firstBytes = client.newCall(firstRangeRequest).execute().use { response ->
                if (response.code != 206 || response.header("Content-Range") != "bytes 0-${HASH_CHUNK_SIZE - 1}/$size") return null
                val body = response.body ?: return null
                if (body.contentLength() != -1L && body.contentLength() != HASH_CHUNK_SIZE.toLong()) return null
                body.byteStream().readNBytesCompat(HASH_CHUNK_SIZE + 1)
            } ?: return null

            if (firstBytes.size != HASH_CHUNK_SIZE) return null

            // 3. Fetch last 64KB
            val lastRangeRequest = Request.Builder()
                .url(url)
                .apply {
                    headers?.forEach { (k, v) -> header(k, v) }
                    header("Range", "bytes=${size - HASH_CHUNK_SIZE}-${size - 1}")
                }
                .build()

            val lastBytes = client.newCall(lastRangeRequest).execute().use { response ->
                if (response.code != 206 || response.header("Content-Range") != "bytes ${size - HASH_CHUNK_SIZE}-${size - 1}/$size") return null
                val body = response.body ?: return null
                if (body.contentLength() != -1L && body.contentLength() != HASH_CHUNK_SIZE.toLong()) return null
                body.byteStream().readNBytesCompat(HASH_CHUNK_SIZE + 1)
            } ?: return null

            if (lastBytes.size != HASH_CHUNK_SIZE) return null

            // Calculate checksum
            var head = size

            val firstBuffer = ByteBuffer.wrap(firstBytes).order(ByteOrder.LITTLE_ENDIAN)
            val longBuffer = firstBuffer.asLongBuffer()
            while (longBuffer.hasRemaining()) {
                head += longBuffer.get()
            }

            val lastBuffer = ByteBuffer.wrap(lastBytes).order(ByteOrder.LITTLE_ENDIAN)
            val lastLongBuffer = lastBuffer.asLongBuffer()
            while (lastLongBuffer.hasRemaining()) {
                head += lastLongBuffer.get()
            }

            return HashResult(String.format("%016x", head), size)
        } catch (e: Exception) {
            Log.e("OpenSubtitlesHasher", "Error computing hash for $url", e)
            return null
        }
    }
}
