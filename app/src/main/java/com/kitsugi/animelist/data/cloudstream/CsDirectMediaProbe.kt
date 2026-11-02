package com.kitsugi.animelist.data.cloudstream

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Inspect only a small prefix of an ambiguous URL, without downloading an entire movie.
 * The caller supplies the same headers/cookie jar used for playback. Failure is UNKNOWN,
 * not evidence that a stream is dead. Cancelling the coroutine cancels the HTTP call.
 */
internal class CsDirectMediaProbe(private val client: OkHttpClient) {
    data class Inspection(val kind: CsMediaResponseKind, val html: String? = null)
    private data class HeadResult(val status: Int, val contentType: String?)

    /**
     * HEAD avoids consuming signed/single-use URLs when possible. If HEAD is unsupported,
     * says the URL is missing, or describes HTML, check a small GET before rejecting it.
     * Network errors, authorization failures and timeouts are inconclusive, not dead.
     */
    suspend fun isPossiblyPlayable(url: String, headers: Map<String, String>): Boolean {
        val head = head(url, headers) ?: return true
        val mime = head.contentType.orEmpty().substringBefore(';').trim()
        val needsGet = head.status in setOf(400, 404, 405, 410, 501) ||
            (head.status in 200..299 && (mime.equals("text/html", true) ||
                mime.equals("application/xhtml+xml", true)))
        if (!needsGet) return true
        return when (inspect(url, headers)) {
            CsMediaResponseKind.DEAD, CsMediaResponseKind.HTML -> false
            else -> true
        }
    }

    private suspend fun head(url: String, headers: Map<String, String>): HeadResult? {
        val request = try {
            Request.Builder().url(url).apply {
                headers.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank()) header(name, value)
                }
            }.head().build()
        } catch (_: IllegalArgumentException) {
            return null
        }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use { HeadResult(it.code, it.header("Content-Type")) }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    suspend fun inspect(url: String, headers: Map<String, String>): CsMediaResponseKind =
        inspectResponse(url, headers, includeHtml = false).kind

    /** Also retain a bounded warning page so a Drive confirmation form can be followed. */
    suspend fun inspectDrive(url: String, headers: Map<String, String>): Inspection =
        inspectResponse(url, headers, includeHtml = true)

    private suspend fun inspectResponse(url: String, headers: Map<String, String>, includeHtml: Boolean): Inspection {
        val request = try {
            Request.Builder().url(url).apply {
                headers.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank() && !name.equals("Range", ignoreCase = true)) {
                        header(name, value)
                    }
                }
                header("Range", if (includeHtml) "bytes=0-262143" else "bytes=0-4095")
            }.get().build()
        } catch (_: IllegalArgumentException) {
            return Inspection(CsMediaResponseKind.UNKNOWN)
        }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(Inspection(CsMediaResponseKind.UNKNOWN))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        try {
                            val sample = if (it.isSuccessful) it.peekBody(4096).bytes() else ByteArray(0)
                            val kind = CsMediaResponsePolicy.classify(it.code, it.header("Content-Type"), sample)
                            val html = if (includeHtml && kind == CsMediaResponseKind.HTML) it.peekBody(262144).string() else null
                            Inspection(kind, html)
                        } catch (_: IOException) {
                            Inspection(CsMediaResponseKind.UNKNOWN)
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }
}
