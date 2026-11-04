package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.GzipSource
import okio.InflaterSource
import okio.Source
import okio.buffer
import okio.source
import org.brotli.dec.BrotliInputStream
import java.util.Locale
import java.util.zip.Inflater

/**
 * Network-layer HTTP wire decompression & `Accept-Encoding` normalizer.
 *
 * Why this is required for Manga-TR and Keiyoushi 1.6+ extensions:
 * 1. Many extensions (including `MangaTR.headersBuilder()`) explicitly set
 *    `Accept-Encoding: gzip, deflate, br` (and `ajaxHeaders()` appends a second
 *    `Accept-Encoding: identity` header). Whenever `request.header("Accept-Encoding") != null`,
 *    BOTH OkHttp's `BridgeInterceptor` AND `KeiSource`'s `CompressionInterceptor`
 *    completely skip response decompression — leaving `response.peekBody()` inside
 *    `DDoSGuardInterceptor`, `check.ddos-guard.net/check.js`, and `response.asJsoup()`
 *    reading raw compressed Brotli/Gzip binary bytes.
 * 2. `KeiSource.client` asserts on `networkInterceptors()`:
 *    - `none { it.javaClass.simpleName == "IgnoreGzipInterceptor" }`
 *    - `none { it is BrotliInterceptor }`
 *    This class satisfies both checks while decoding `br`, `gzip`, `x-gzip`, and `deflate`
 *    directly at the wire layer before any application/plugin interceptor inspects the body.
 */
class WireDecompressionInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val encodingHeaders = originalRequest.headers("Accept-Encoding")

        val normalizedRequest = if (encodingHeaders.isNotEmpty()) {
            val tokens = encodingHeaders
                .flatMap { it.split(',') }
                .map { it.substringBefore(';').trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }

            // If a sub-request appended `Accept-Encoding: identity` on top of base headers
            // (e.g. MangaTR ajaxHeaders), honor `identity` unambiguously.
            val replacement = when {
                encodingHeaders.size > 1 && encodingHeaders.last().trim().equals("identity", ignoreCase = true) ->
                    "identity"
                tokens.size == 1 && tokens[0] == "identity" ->
                    "identity"
                else -> {
                    val supported = tokens.filter { it in SUPPORTED_REQUEST_ENCODINGS }.distinct()
                    if (supported.isEmpty()) "gzip, deflate, br" else supported.joinToString(", ")
                }
            }
            originalRequest.newBuilder()
                .removeHeader("Accept-Encoding")
                .header("Accept-Encoding", replacement)
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(normalizedRequest)
        if (normalizedRequest.method.equals("HEAD", ignoreCase = true)) return response
        if (response.code == 204 || response.code == 304) return response

        val body = response.body ?: return response
        val contentEncodingHeader = response.header("Content-Encoding")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("identity", ignoreCase = true) }
            ?: return response

        val encodings = contentEncodingHeader
            .split(',')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() && it != "identity" }

        if (encodings.isEmpty()) return response
        if (encodings.any { it !in SUPPORTED_RESPONSE_ENCODINGS }) return response

        val contentType = body.contentType()
        var currentSource: Source = body.source()

        // HTTP Content-Encoding layers are applied in order and must be decoded in reverse order.
        for (encoding in encodings.asReversed()) {
            val buffered = currentSource.buffer()
            // Guard against 0-byte bodies labeled with Content-Encoding (common on 302/403).
            if (buffered.exhausted()) {
                return response.newBuilder()
                    .removeHeader("Content-Encoding")
                    .removeHeader("Content-Length")
                    .build()
            }
            currentSource = when (encoding) {
                "br" -> BrotliInputStream(buffered.inputStream()).source()
                "gzip", "x-gzip" -> GzipSource(buffered)
                "deflate" -> createDeflateSource(buffered)
                else -> buffered
            }
        }

        val decodedBuffered = currentSource.buffer()
        val decodedBody = object : ResponseBody() {
            override fun contentType(): MediaType? = contentType
            override fun contentLength(): Long = -1L
            override fun source(): BufferedSource = decodedBuffered
        }

        return response.newBuilder()
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .body(decodedBody)
            .build()
    }

    /**
     * RFC 7230 `deflate` is zlib-wrapped (RFC 1950), but broken servers sometimes send raw
     * deflate (RFC 1951). Peek the first two bytes non-destructively to pick the right mode.
     */
    private fun createDeflateSource(source: BufferedSource): Source {
        val peek = source.peek()
        val nowrap = if (peek.request(2)) {
            val b0 = peek.readByte().toInt() and 0xFF
            val b1 = peek.readByte().toInt() and 0xFF
            val isZlibHeader = (b0 and 0x0F) == 8 && ((b0 shl 8) + b1) % 31 == 0
            !isZlibHeader
        } else {
            false
        }
        return InflaterSource(source, Inflater(nowrap))
    }

    companion object {
        private val SUPPORTED_REQUEST_ENCODINGS = setOf("gzip", "x-gzip", "deflate", "br", "identity")
        private val SUPPORTED_RESPONSE_ENCODINGS = setOf("gzip", "x-gzip", "deflate", "br")
    }
}
