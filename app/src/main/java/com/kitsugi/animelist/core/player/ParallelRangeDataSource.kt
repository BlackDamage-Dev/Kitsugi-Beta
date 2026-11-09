package com.kitsugi.animelist.core.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.kitsugi.animelist.core.memory.KitsugiMemoryGuard
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * T1.9 – ParallelRangeDataSource
 *
 * Paralel HTTP Range istekleri ile progressive video indirmeyi hızlandıran ExoPlayer DataSource.
 *
 * ÖZELLİKLER:
 * - MKV / MP4 gibi progressive içerikler için çok bağlantılı chunk indirme
 * - Reusable ByteBuffer pool (JVM heap) — GC baskısını azaltır
 * - HLS (.m3u8) / DASH (.mpd) / altyazı URL'lerinde otomatik devre dışı
 * - Tek bağlantı moduna sorunsuz fallback (hata durumunda)
 * - BitrateAwareLoadControl ile uyumlu; düşük bitrate'te tek bağlantıya geçer
 *
 * SINIRLAMALAR:
 * - Sunucu Accept-Ranges desteği gerektirmez (eksikse tek bağlantı modu devreye girer)
 * - HEAD isteği yoktur — ilk chunk sonucuna göre karar verilir
 */
@OptIn(UnstableApi::class)
class ParallelRangeDataSource(
    private val upstream: DataSource,
    private val parallelConnections: Int = DEFAULT_PARALLEL_CONNECTIONS,
    private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
    private val upstreamFactory: DataSource.Factory? = null
) : DataSource {

    companion object {
        private const val TAG = "ParallelRangeDS"

        /** Varsayılan paralel bağlantı sayısı */
        const val DEFAULT_PARALLEL_CONNECTIONS = 3

        /** Varsayılan chunk boyutu: 512 KB */
        const val DEFAULT_CHUNK_SIZE_BYTES = 512L * 1024L

        /** Minimum paralel mod için içerik boyutu: 1 MB */
        private const val MIN_CONTENT_LENGTH_FOR_PARALLEL = 1L * 1024L * 1024L
        private const val MAX_BUFFERS_PER_CHUNK_SIZE = 8

        // Buffer pool — chunk boyutuna göre ayrılmış ConcurrentLinkedDeque havuzu
        private val globalBufferPool = ConcurrentHashMap<Long, ConcurrentLinkedDeque<ByteBuffer>>()

        init {
            KitsugiMemoryGuard.registerClearer("player.parallelRangeBuffers") { fraction ->
                globalBufferPool.values.forEach { pool ->
                    if (fraction <= 0f) {
                        pool.clear()
                    } else {
                        val keep = (pool.size * fraction).toInt()
                        while (pool.size > keep) pool.pollFirst() ?: break
                    }
                }
            }
        }

        private fun bufferPool(chunkSize: Long): ConcurrentLinkedDeque<ByteBuffer> =
            globalBufferPool.computeIfAbsent(chunkSize) { ConcurrentLinkedDeque() }

        private fun acquireBuffer(chunkSize: Long): ByteBuffer {
            val pool = bufferPool(chunkSize)
            val buf = pool.pollLast()
            if (buf != null) {
                buf.clear()
                return buf
            }
            return ByteBuffer.allocate(chunkSize.toInt())
        }

        private fun releaseBuffer(chunkSize: Long, buffer: ByteBuffer) {
            val pool = bufferPool(chunkSize)
            if (pool.size < MAX_BUFFERS_PER_CHUNK_SIZE) {
                buffer.clear()
                pool.addLast(buffer)
            }
        }
    }

    // ── Runtime state ─────────────────────────────────────────────────────────
    private var openedSpec: DataSpec? = null
    private var contentLength: Long = -1L
    private var isParallelMode: Boolean = false
    private var readPositionInChunk: Int = 0

    // Paralel mod chunk yönetimi. Bound worker count per DataSource and stop it on close.
    @Volatile
    private var executor: ExecutorService = newChunkExecutor()
    @Volatile
    private var isClosed = true
    private val pendingChunks = ArrayDeque<ChunkBuffer>()

    private fun newChunkExecutor(): ExecutorService =
        Executors.newFixedThreadPool(parallelConnections.coerceIn(1, 8))

    private fun submitChunkTask(task: FutureTask<Unit>) {
        synchronized(this) {
            if (isClosed) throw RejectedExecutionException("DataSource is closed")
            if (executor.isShutdown) executor = newChunkExecutor()
            executor.execute(task)
        }
    }
    private var currentChunkBuffer: ChunkBuffer? = null
    private val downloadedBytes = AtomicLong(0L)
    private val totalChunksDispatched = AtomicInteger(0)
    private var nextChunkOffset: Long = 0L
    private var requestedOffset: Long = 0L
    private var bytesRemaining: Long = Long.MAX_VALUE

    // Tek bağlantı mod (HLS/DASH/altyazı/kısa içerik)
    private var singleInputStream: InputStream? = null
    private var singleBytesRemaining: Long = Long.MAX_VALUE

    private data class ChunkBuffer(
        val offset: Long,
        @Volatile var future: Future<*>?,
        val buffer: ByteBuffer,
        @Volatile var bytesWritten: Int = 0,
        @Volatile var error: Exception? = null,
        @Volatile var done: Boolean = false
    )

    // ── DataSource.Factory ────────────────────────────────────────────────────

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val parallelConnections: Int = DEFAULT_PARALLEL_CONNECTIONS,
        private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES
    ) : DataSource.Factory {

        override fun createDataSource(): DataSource {
            return ParallelRangeDataSource(
                upstream = upstreamFactory.createDataSource(),
                parallelConnections = parallelConnections,
                chunkSizeBytes = chunkSizeBytes,
                upstreamFactory = upstreamFactory
            )
        }
    }

    // ── TransferListener desteği ──────────────────────────────────────────────

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    // ── open ──────────────────────────────────────────────────────────────────

    override fun open(dataSpec: DataSpec): Long {
        synchronized(this) {
            isClosed = false
            if (executor.isShutdown) executor = newChunkExecutor()
        }
        openedSpec = dataSpec
        readPositionInChunk = 0
        pendingChunks.clear()
        currentChunkBuffer = null
        downloadedBytes.set(0L)
        totalChunksDispatched.set(0)

        val uri = dataSpec.uri
        val uriStr = uri.toString().lowercase()

        // HLS / DASH / SmoothStreaming / altyazı → tek bağlantı
        val isAdaptive = uriStr.contains(".m3u8") || uriStr.contains(".mpd") ||
                uriStr.contains(".ism") || uriStr.contains("m3u8") ||
                uriStr.contains("Kitsugi_type=subtitle")
        val isLocalFile = uriStr.startsWith("file://") || uriStr.startsWith("content://")

        // Parallel mode needs an independent DataSource per range request; sharing [upstream]
        // across worker threads races its open/read/close lifecycle.
        if (isAdaptive || isLocalFile || parallelConnections <= 1 || upstreamFactory == null) {
            isParallelMode = false
            return openSingleMode(dataSpec)
        }

        // Progressive içerik — paralel mod denenir
        return try {
            openParallelMode(dataSpec)
        } catch (e: Exception) {
            Log.w(TAG, "Paralel mod açılamadı, tek bağlantıya fallback: ${e.message}")
            isParallelMode = false
            openSingleMode(dataSpec)
        }
    }

    private fun openSingleMode(dataSpec: DataSpec): Long {
        isParallelMode = false
        val bytesOpened = upstream.open(dataSpec)
        singleBytesRemaining = when {
            dataSpec.length != androidx.media3.common.C.LENGTH_UNSET.toLong() -> dataSpec.length
            bytesOpened != androidx.media3.common.C.LENGTH_UNSET.toLong() && bytesOpened >= 0L -> bytesOpened
            else -> Long.MAX_VALUE
        }
        return bytesOpened
    }

    private fun openParallelMode(dataSpec: DataSpec): Long {
        // İlk chunk'u tek bağlantı ile aç, içerik uzunluğunu öğren
        val firstRangeLength = dataSpec.length.takeIf { it > 0L }
            ?.coerceAtMost(chunkSizeBytes) ?: chunkSizeBytes
        val firstSpec = dataSpec.buildUpon()
            .setPosition(dataSpec.position)
            .setLength(firstRangeLength)
            .build()

        val firstLength = upstream.open(firstSpec)

        if (firstLength <= 0) {
            // İçerik yok veya uzunluk bilinmiyor — tek bağlantı yap
            isParallelMode = false
            singleBytesRemaining = firstLength
            return firstLength
        }

        // İlk chunk buffer'ını oluştur
        val firstBuffer = acquireBuffer(chunkSizeBytes)
        val firstChunk = ChunkBuffer(
            offset = dataSpec.position,
            future = null, // ilk chunk open() içinde senkron doldurulur
            buffer = firstBuffer
        )

        // İlk chunk'u hemen oku (zaten açık bağlantı)
        try {
            var totalRead = 0
            val tempBuf = ByteArray(8192)
            while (totalRead < firstRangeLength) {
                val n = readFromUpstream(tempBuf, 0, (firstRangeLength - totalRead).coerceAtMost(tempBuf.size.toLong()).toInt())
                if (n <= 0) break
                firstBuffer.put(tempBuf, 0, n)
                totalRead += n
            }
            firstChunk.bytesWritten = totalRead
            firstChunk.done = true
        } catch (e: Exception) {
            firstChunk.error = e
            firstChunk.done = true
        } finally {
            upstream.close()
        }

        requestedOffset = dataSpec.position
        nextChunkOffset = dataSpec.position + chunkSizeBytes
        contentLength = dataSpec.length.takeIf { it > 0 } ?: Long.MAX_VALUE
        bytesRemaining = contentLength

        isParallelMode = true
        pendingChunks.addLast(firstChunk)

        // Önceden ek chunk'ları dispatch et
        dispatchPrefetchChunks(dataSpec, parallelConnections - 1)

        firstBuffer.flip()
        return contentLength
    }

    /** Sıradaki N chunk'ı arka planda indir */
    private fun dispatchPrefetchChunks(dataSpec: DataSpec, count: Int) {
        repeat(count) {
            val offset = nextChunkOffset
            if (contentLength != Long.MAX_VALUE && offset >= dataSpec.position + contentLength) return
            nextChunkOffset += chunkSizeBytes

            val rangeLength = if (contentLength == Long.MAX_VALUE) {
                chunkSizeBytes
            } else {
                minOf(chunkSizeBytes, (dataSpec.position + contentLength - offset).coerceAtLeast(0L))
            }
            if (rangeLength <= 0L) return
            val chunkSpec = dataSpec.buildUpon()
                .setPosition(offset)
                .setLength(rangeLength)
                .build()

            val buf = acquireBuffer(chunkSizeBytes)
            val chunk = ChunkBuffer(offset = offset, future = null, buffer = buf)
            val task = FutureTask<Unit> {
                var ds: DataSource? = null
                try {
                    ds = upstreamFactory?.createDataSource() ?: upstream
                    ds.open(chunkSpec)
                    var totalRead = 0
                    val tempBuf = ByteArray(8192)
                    while (totalRead < rangeLength) {
                        val toRead = (rangeLength - totalRead).coerceAtMost(tempBuf.size.toLong()).toInt()
                        val n = ds.read(tempBuf, 0, toRead)
                        if (n <= 0) break
                        buf.put(tempBuf, 0, n)
                        totalRead += n
                    }
                    chunk.bytesWritten = totalRead
                } catch (e: Exception) {
                    chunk.error = e
                } finally {
                    try { ds?.close() } catch (_: Exception) {}
                    chunk.done = true
                }
                Unit
            }
            chunk.future = task
            pendingChunks.addLast(chunk)
            try {
                submitChunkTask(task)
            } catch (e: RejectedExecutionException) {
                chunk.error = e
                chunk.done = true
            }
        }
    }

    // ── read ──────────────────────────────────────────────────────────────────

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        return if (isParallelMode) {
            readParallel(buffer, offset, length)
        } else {
            readSingle(buffer, offset, length)
        }
    }

    private fun readSingle(buffer: ByteArray, offset: Int, length: Int): Int {
        if (singleBytesRemaining == 0L) return -1
        return try {
            readFromUpstream(buffer, offset, length.coerceAtMost(singleBytesRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
                .also { n -> if (n > 0) singleBytesRemaining -= n }
        } catch (e: IOException) {
            throw e
        }
    }

    private fun readParallel(buffer: ByteArray, offset: Int, length: Int): Int {
        val chunk = currentChunkBuffer ?: run {
            // Sıradaki chunk'ı al
            val next = pendingChunks.removeFirstOrNull() ?: return -1
            // Keep a bounded sliding window of prefetched ranges until the requested resource ends.
            val prefetchTarget = (parallelConnections - 1).coerceAtLeast(0)
            val missingPrefetch = (prefetchTarget - pendingChunks.size).coerceAtLeast(0)
            if (missingPrefetch > 0) {
                openedSpec?.let { dispatchPrefetchChunks(it, missingPrefetch) }
            }
            // Worker'ın Future'ında bekle; 5 ms aralıkla polling yapıp loader thread'ini uyandırma.
            if (!next.done) {
                val future = next.future ?: throw IOException("Chunk ${next.offset} için worker yok")
                try {
                    future.get(30L, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    future.cancel(true)
                    throw IOException("Chunk ${next.offset} indirme zaman aşımı", e)
                } catch (e: InterruptedException) {
                    future.cancel(true)
                    Thread.currentThread().interrupt()
                    throw IOException("Chunk ${next.offset} indirme iptal edildi", e)
                } catch (e: ExecutionException) {
                    throw IOException("Chunk ${next.offset} worker hatası", e.cause ?: e)
                } catch (e: java.util.concurrent.CancellationException) {
                    throw IOException("Chunk ${next.offset} indirme iptal edildi", e)
                }
            }
            next.error?.let { throw IOException("Chunk ${next.offset} indirme hatası", it) }
            if (!next.done) throw IOException("Chunk ${next.offset} tamamlanmadı")
            next.buffer.flip()
            currentChunkBuffer = next
            next
        }

        val buf = chunk.buffer
        if (!buf.hasRemaining()) {
            releaseBuffer(chunkSizeBytes, buf)
            currentChunkBuffer = null
            return readParallel(buffer, offset, length)
        }

        val toRead = length.coerceAtMost(buf.remaining())
        buf.get(buffer, offset, toRead)
        return toRead
    }

    private fun readFromUpstream(buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            upstream.read(buffer, offset, length)
        } catch (e: Exception) {
            -1
        }
    }

    // ── getUri ────────────────────────────────────────────────────────────────

    override fun getUri(): Uri? = openedSpec?.uri

    // ── getResponseHeaders ────────────────────────────────────────────────────

    override fun getResponseHeaders(): Map<String, List<String>> {
        return if (upstream is HttpDataSource) upstream.responseHeaders else emptyMap()
    }

    // ── close ─────────────────────────────────────────────────────────────────

    override fun close() {
        val chunksToDispose = synchronized(this) {
            isClosed = true
            val chunks = ArrayList<ChunkBuffer>(pendingChunks.size + 1)
            currentChunkBuffer?.let { chunks.add(it) }
            chunks.addAll(pendingChunks)
            currentChunkBuffer = null
            pendingChunks.clear()
            executor.shutdownNow()
            chunks
        }
        try {
            upstream.close()
        } catch (_: Exception) {}

        // Cancel outstanding ranges and stop this per-source pool so playback churn cannot
        // leave worker threads alive after the DataSource is closed. Incomplete buffers are
        // deliberately not returned to the shared pool: a cancelled provider read may ignore
        // interruption and still be writing into them.
        chunksToDispose.forEach { it.future?.cancel(true) }
        chunksToDispose.filter { it.done }.forEach { releaseBuffer(chunkSizeBytes, it.buffer) }

        isParallelMode = false
        singleInputStream = null
        singleBytesRemaining = Long.MAX_VALUE
        openedSpec = null
    }
}
