package com.kitsugi.animelist.data.trailer

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * DataSource for YouTube streams. Progressive googlevideo files can optionally use
 * YouTube's query-parameter range requests to avoid throttling. HLS manifests and segments
 * must pass through unchanged: adding a `range` query to a signed HLS URL corrupts the
 * request and can leave the inline player stuck at 00:00.
 */
@UnstableApi
class YoutubeChunkedDataSourceFactory(
    private val chunkSizeBytes: Long = CHUNK_SIZE,
    private val chunkGoogleVideoStreams: Boolean = true,
    private val requestHeaders: Map<String, String> = emptyMap()
) : DataSource.Factory {

    companion object {
        private const val TAG = "YTChunkedDS"
        /** 10 MB chunks – large enough to avoid too many requests, small enough to dodge throttle. */
        private const val CHUNK_SIZE = 10L * 1024 * 1024
    }

    override fun createDataSource(): DataSource {
        val headers = YouTubeRequestPolicy.youtubePlaybackHeaders(requestHeaders)
        val userAgent = headers.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?: YouTubeRequestPolicy.STREAM_USER_AGENT
        val nonUserAgentHeaders = headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }

        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(nonUserAgentHeaders)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        return YoutubeChunkedDataSource(
            upstream = upstream,
            chunkSize = chunkSizeBytes.coerceAtLeast(1L),
            chunkGoogleVideoStreams = chunkGoogleVideoStreams
        )
    }

    private class YoutubeChunkedDataSource(
        private val upstream: DefaultHttpDataSource,
        private val chunkSize: Long,
        private val chunkGoogleVideoStreams: Boolean
    ) : DataSource {

        private var currentUri: Uri? = null
        private var isYouTubeStream = false
        private var totalContentLength = C.LENGTH_UNSET.toLong()
        private var currentChunkStart = 0L
        private var currentChunkEnd = 0L
        private var bytesReadInChunk = 0L
        private var originalDataSpec: DataSpec? = null

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            currentUri = dataSpec.uri
            isYouTubeStream = chunkGoogleVideoStreams &&
                YouTubeRequestPolicy.isGoogleVideoHost(dataSpec.uri.host.orEmpty())

            if (!isYouTubeStream) {
                return upstream.open(dataSpec)
            }

            originalDataSpec = dataSpec
            currentChunkStart = dataSpec.position
            totalContentLength = dataSpec.length
            return openNextChunk()
        }

        private fun openNextChunk(): Long {
            val spec = originalDataSpec ?: throw IllegalStateException("No DataSpec")
            val end = if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                minOf(currentChunkStart + chunkSize - 1, currentChunkStart + totalContentLength - 1)
            } else {
                currentChunkStart + chunkSize - 1
            }
            currentChunkEnd = end

            // YouTube format URLs can already carry a range. Replace, rather than append,
            // so every requested chunk advances instead of repeatedly reading the first.
            val rangedUri = if (spec.uri.getQueryParameter("range") != null) {
                val builder = spec.uri.buildUpon().clearQuery()
                for (paramName in spec.uri.queryParameterNames) {
                    if (!paramName.equals("range", ignoreCase = true)) {
                        for (value in spec.uri.getQueryParameters(paramName)) {
                            builder.appendQueryParameter(paramName, value)
                        }
                    }
                }
                builder.appendQueryParameter("range", "$currentChunkStart-$currentChunkEnd").build()
            } else {
                spec.uri.buildUpon()
                    .appendQueryParameter("range", "$currentChunkStart-$currentChunkEnd")
                    .build()
            }

            val chunkedSpec = spec.buildUpon()
                .setUri(rangedUri)
                .setPosition(0L)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()

            bytesReadInChunk = 0L
            upstream.open(chunkedSpec)
            return totalContentLength
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!isYouTubeStream) return upstream.read(buffer, offset, length)

            val bytesRead = upstream.read(buffer, offset, length)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                val chunkBytesReceived = bytesReadInChunk
                upstream.close()

                // A short chunk is the end of this response (normally the last file chunk).
                if (chunkBytesReceived < (currentChunkEnd - currentChunkStart + 1)) {
                    return C.RESULT_END_OF_INPUT
                }

                currentChunkStart += chunkBytesReceived
                if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                    totalContentLength -= chunkBytesReceived
                    if (totalContentLength <= 0L) return C.RESULT_END_OF_INPUT
                }

                return try {
                    openNextChunk()
                    upstream.read(buffer, offset, length).also { nextRead ->
                        if (nextRead > 0) bytesReadInChunk += nextRead
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open next chunk at $currentChunkStart: ${e.message}")
                    // A failed CDN request is not end-of-media. Silently returning EOF used
                    // to freeze/end playback without giving the source fallback a chance.
                    throw if (e is java.io.IOException) e else java.io.IOException(
                        "Failed to open YouTube chunk at $currentChunkStart",
                        e
                    )
                }
            }

            if (bytesRead > 0) bytesReadInChunk += bytesRead
            return bytesRead
        }

        override fun getUri(): Uri? = upstream.uri ?: currentUri

        override fun close() {
            upstream.close()
            currentUri = null
            isYouTubeStream = false
            totalContentLength = C.LENGTH_UNSET.toLong()
            currentChunkStart = 0L
            currentChunkEnd = 0L
            bytesReadInChunk = 0L
            originalDataSpec = null
        }
    }
}
