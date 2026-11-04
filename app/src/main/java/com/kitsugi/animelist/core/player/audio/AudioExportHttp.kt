package com.kitsugi.animelist.core.player.audio

import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.core.network.RetryInterceptor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Shared HTTP policy for the nine published providers: no credential forwarding on redirects. */
internal object AudioExportHttp {
    const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024
    const val MAX_TEMP_BYTES = 2_000_000_000L
    private const val MIN_FREE_BYTES = 96L * 1024 * 1024
    private val client by lazy {
        KitsugiHttpClient.client.newBuilder()
            .followRedirects(false).followSslRedirects(false)
            // A signed one-shot media URL must not be retried invisibly after a failed read.
            .retryOnConnectionFailure(false)
            .readTimeout(35, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is RetryInterceptor } }
            .apply {
                // CS3 and the player share the cookie jar. RFC domain scoping still applies;
                // explicit Cookie/Authorization headers below never cross an origin boundary.
                runCatching {
                    uy.kohesive.injekt.Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java).cookieJar
                }.getOrNull()?.let { cookieJar(it) }
            }.build()
    }

    data class Range(val start: Long, val end: Long) {
        init { require(start >= 0 && end >= start && end - start < MAX_TEMP_BYTES) { "Geçersiz byte aralığı" } }
        val length: Long get() = end - start + 1
    }

    fun resolve(base: String, relative: String): String = AudioExportAddress.resolve(base, relative)
    fun sameOrigin(a: String, b: String): Boolean = AudioExportAddress.sameOrigin(a, b)
    fun headersFor(root: String, target: String, headers: Map<String, String>): Map<String, String> =
        AudioExportAddress.headersFor(root, target, headers)

    /** The response and cancel handle remain open through [block], including streamed reads. */
    suspend fun <T> withResponse(root: String, url: String, headers: Map<String, String>,
                                 range: Range? = null, block: suspend (Response) -> T): T {
        var current = resolve(root, url)
        repeat(6) {
            currentCoroutineContext().ensureActive()
            val builder = Request.Builder().url(current).header("Accept-Encoding", "identity")
            headersFor(root, current, headers).forEach { (key, value) -> builder.header(key, value) }
            if (range != null) builder.header("Range", "bytes=${range.start}-${range.end}")
            val call: Call = client.newCall(builder.build())
            val cancellation = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            try {
                val response = call.execute()
                response.use { result ->
                    if (result.code in listOf(301, 302, 303, 307, 308)) {
                        val location = result.header("Location") ?: error("Eksik HTTP yönlendirme adresi")
                        current = resolve(current, location)
                    } else {
                        require(result.isSuccessful &&
                            (if (range == null) result.code != 206 else result.code == 206)) {
                            "Medya isteği başarısız: HTTP ${result.code}"
                        }
                        if (range != null) {
                            val expected = Regex("bytes ${range.start}-${range.end}/(?:[0-9]+|\\*)", RegexOption.IGNORE_CASE)
                            require(result.header("Content-Range")?.let { expected.matches(it.trim()) } == true) {
                                "Sunucu istenen byte aralığını döndürmedi"
                            }
                        }
                        return block(result)
                    }
                }
            } finally {
                cancellation?.dispose()
            }
        }
        error("Çok fazla HTTP yönlendirmesi")
    }

    suspend fun text(root: String, url: String, headers: Map<String, String>): Pair<String, String> =
        withResponse(root, url, headers) { response ->
            val body = response.body ?: error("Boş manifest")
            require(body.contentLength() <= MAX_PLAYLIST_BYTES) { "Manifest çok büyük" }
            val buffer = ByteArray(8192)
            val bytes = ByteArrayOutputStream()
            body.byteStream().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(bytes.size() + n <= MAX_PLAYLIST_BYTES) { "Manifest çok büyük" }
                    bytes.write(buffer, 0, n)
                }
            }
            response.request.url.toString() to bytes.toString("UTF-8").removePrefix("\uFEFF")
        }

    data class Inspection(val transport: String?, val manifest: Pair<String, String>? = null,
                          val localFile: File? = null)

    /**
     * An opaque URL may be a manifest, FLV or progressive file. Read it ONCE: do not
     * consume a signed one-shot GET merely to guess its type, then request it again.
     * The complete manifest is returned to the parser; progressive media is bounded
     * and spooled to a seekable file for Android's MediaExtractor.
     */
    suspend fun inspect(url: String, headers: Map<String, String>, local: File,
                        onProgress: suspend (Int) -> Unit = {}): Inspection =
        withResponse(url, url, headers) { response ->
            val body = response.body ?: error("Medya yanıtı boş")
            val declared = body.contentLength()
            val type = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
            body.byteStream().use { input ->
                val prefix = ByteArray(2048)
                var count = 0
                while (count < prefix.size) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(prefix, count, prefix.size - count)
                    if (n <= 0) break
                    count += n
                    if (count >= 512) break
                }
                require(count > 0) { "Medya yanıtı boş" }
                val text = String(prefix, 0, count, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n')
                val transport = when {
                    "mpegurl" in type || text.startsWith("#EXTM3U") -> "hls"
                    "dash+xml" in type || Regex("<(?:[A-Za-z_][\\w.-]*:)?MPD(?:\\s|>)")
                        .containsMatchIn(text) -> "dash"
                    "video/x-flv" in type || "application/x-flv" in type || text.startsWith("FLV\u0001") -> "flv"
                    else -> null
                }
                if (transport in setOf("hls", "dash")) {
                    require(declared <= MAX_PLAYLIST_BYTES && count <= MAX_PLAYLIST_BYTES) { "Manifest çok büyük" }
                    val bytes = ByteArrayOutputStream().apply { write(prefix, 0, count) }
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(bytes.size() + n <= MAX_PLAYLIST_BYTES) { "Manifest çok büyük" }
                        bytes.write(buffer, 0, n)
                    }
                    require(declared < 0 || bytes.size().toLong() == declared) { "Manifest eksik" }
                    Inspection(transport, response.request.url.toString() to
                        bytes.toString("UTF-8").removePrefix("\uFEFF"))
                } else {
                    require(declared < 0 || declared <= MAX_TEMP_BYTES) { "Geçici indirme boyut sınırını aşıyor" }
                    var received = count.toLong()
                    local.outputStream().buffered().use { out ->
                        out.write(prefix, 0, count)
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            require(received + n <= MAX_TEMP_BYTES) { "Geçici indirme boyut sınırını aşıyor" }
                            if (received / (8 * 1024 * 1024) != (received + n) / (8 * 1024 * 1024)) {
                                require(local.parentFile!!.usableSpace > MIN_FREE_BYTES + n) {
                                    "Depolama alanı yetersiz"
                                }
                            }
                            out.write(buffer, 0, n)
                            received += n
                            if (declared > 0) onProgress(5 + (received * 65 / declared).toInt().coerceIn(0, 65))
                        }
                    }
                    require(declared < 0 || received == declared) { "Eksik medya dosyası" }
                    Inspection(transport, localFile = local)
                }
            }
        }

    /** Bounded copy; a short 200/206 or HTML login page must not become a 'completed' export. */
    suspend fun append(root: String, url: String, headers: Map<String, String>, range: Range?,
                       output: OutputStream, workDir: File, alreadyWritten: Long, maxBytes: Long = MAX_TEMP_BYTES,
                       transform: (java.io.InputStream) -> java.io.InputStream = { it }): Long =
        withResponse(root, url, headers, range) { response ->
            val body = response.body ?: error("Boş medya parçası")
            val declared = range?.length ?: body.contentLength()
            require(declared < 0 || alreadyWritten + declared <= maxBytes) { "Geçici indirme boyut sınırını aşıyor" }
            val raw = body.byteStream()
            var received = 0L
            val counter = object : java.io.FilterInputStream(raw) {
                override fun read(): Int = super.read().also { if (it >= 0) received++ }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                    `in`.read(bytes, offset, length).also { if (it > 0) received += it }
            }
            var written = 0L
            transform(counter).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(alreadyWritten + written + n <= maxBytes) { "Geçici indirme boyut sınırını aşıyor" }
                    if ((written and (8L * 1024 * 1024 - 1)) < n) {
                        require(workDir.usableSpace > MIN_FREE_BYTES + n) { "Depolama alanı yetersiz" }
                    }
                    output.write(buffer, 0, n)
                    written += n
                }
            }
            require(received > 0 && (declared < 0 || received == declared)) { "Eksik medya parçası" }
            require(written > 0) { "Boş ses parçası" }
            written
        }
}
