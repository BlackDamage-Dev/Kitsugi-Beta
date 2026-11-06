package com.kitsugi.animelist.core.player

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OpenSubtitlesHasher {
    private const val HASH_CHUNK_SIZE = 65536 // 64 KB

    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(com.kitsugi.animelist.core.network.IPv4FirstDns())
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    data class HashResult(val hash: String, val size: Long)

    internal fun validRange(code: Int, contentRange: String?, start: Long, end: Long, size: Long): Boolean =
        code == 206 && contentRange?.trim()?.equals("bytes $start-$end/$size", ignoreCase = true) == true

    /**
     * HLS/DASH adaptif manifestleri ve yerel P2P/torrent proxy adresleri OpenSubtitles hash'i
     * üretmeye uygun değildir (yerel P2P'de son 64 KB parça henüz inmemiş olduğundan isteği
     * bloklar ve tüm altyazı eklentilerini bekletir).
     */
    internal fun canProbeUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) return false
        val lower = trimmed.lowercase(java.util.Locale.ROOT)
        if (lower.contains("://127.0.0.1") || lower.contains("://localhost") || lower.contains("://[::1]")) {
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
        try {
            val client = sharedClient

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
                if (!validRange(response.code, response.header("Content-Range"), 0, HASH_CHUNK_SIZE.toLong() - 1, size)) return null
                val body = response.body ?: return null
                if (body.contentLength() != HASH_CHUNK_SIZE.toLong()) return null
                body.source().readByteArray(HASH_CHUNK_SIZE.toLong())
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
                if (!validRange(response.code, response.header("Content-Range"), size - HASH_CHUNK_SIZE, size - 1, size)) return null
                val body = response.body ?: return null
                if (body.contentLength() != HASH_CHUNK_SIZE.toLong()) return null
                body.source().readByteArray(HASH_CHUNK_SIZE.toLong())
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
