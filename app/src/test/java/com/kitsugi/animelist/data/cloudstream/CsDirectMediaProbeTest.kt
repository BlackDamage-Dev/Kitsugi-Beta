package com.kitsugi.animelist.data.cloudstream

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real loopback HTTP exercises request headers, cookies and bounded reads. */
class CsDirectMediaProbeTest {
    private lateinit var server: ServerSocket
    private val executor = Executors.newCachedThreadPool()
    private val requests = Collections.synchronizedList(mutableListOf<Pair<String, Map<String, String>>>())
    private val releaseBody = CountDownLatch(1)

    private val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(
            Cookie.Builder().name("sid").value("shared-cookie").hostOnlyDomain(url.host).build()
        )
    }).build()

    @Before
    fun startServer() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        executor.submit {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                executor.submit { handle(socket) }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use {
            val input = it.getInputStream().bufferedReader()
            val first = input.readLine()?.split(' ') ?: return
            val method = first[0]
            val path = first[1].substringBefore('?')
            val observed = linkedMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) observed[line.substring(0, colon).lowercase(Locale.ROOT)] = line.substring(colon + 1).trim()
            }
            requests.add(method to observed)
            val (status, mime, body) = when (path) {
                "/head404" -> if (method == "HEAD") Triple(404, "text/plain", "")
                    else Triple(206, "text/plain", "#EXTM3U\n#EXT-X-VERSION:3\n")
                "/blocked" -> if (method == "HEAD") Triple(404, "text/plain", "")
                    else Triple(403, "text/html", "<html>forbidden</html>")
                "/html" -> if (method == "HEAD") Triple(200, "text/html", "")
                    else Triple(200, "video/mp4", "<!DOCTYPE html><html>quota exceeded</html>")
                "/head-ok" -> Triple(200, "video/mp4", "")
                "/drive-html" -> Triple(200, "text/html", "<html><form>confirm</form>" + "x".repeat(300_000))
                "/large" -> Triple(200, "video/mp4", "")
                else -> Triple(404, "text/plain", "")
            }
            val bytes = body.toByteArray()
            val length = if (path == "/large") 8_000_000 else bytes.size
            val output = it.getOutputStream()
            output.write("HTTP/1.1 $status Test\r\nContent-Type: $mime\r\nContent-Length: $length\r\nConnection: close\r\n\r\n".toByteArray())
            if (method != "HEAD") {
                if (path == "/large") {
                    // Ignore Range and keep an 8 MB response open after the prefix.
                    output.write(ByteArray(8192) { 1 })
                    output.flush()
                    releaseBody.await(3, TimeUnit.SECONDS)
                } else output.write(bytes)
            }
            output.flush()
        }
    }

    @After
    fun stopServer() {
        releaseBody.countDown()
        server.close()
        executor.shutdownNow()
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }

    private fun url(path: String) = "http://127.0.0.1:${server.localPort}$path"
    private val headers = mapOf(
        "referer" to "https://yourupload.example/embed/123",
        "Origin" to "https://yourupload.example",
        "User-Agent" to "HostSpecificUA",
        "X-Token" to "signed"
    )

    @Test
    fun `head fallback sends playback headers cookies and range`() = runBlocking {
        assertTrue(CsDirectMediaProbe(client).isPossiblyPlayable(url("/head404"), headers))
        assertEquals(listOf("HEAD", "GET"), requests.map { it.first })
        for ((_, observed) in requests) {
            assertEquals("https://yourupload.example/embed/123", observed["referer"])
            assertEquals("https://yourupload.example", observed["origin"])
            assertEquals("HostSpecificUA", observed["user-agent"])
            assertEquals("sid=shared-cookie", observed["cookie"])
            assertEquals("signed", observed["x-token"])
        }
        assertEquals("bytes=0-4095", requests.last().second["range"])
    }

    @Test
    fun `forbidden get remains inconclusive instead of dead`() = runBlocking {
        assertTrue(CsDirectMediaProbe(client).isPossiblyPlayable(url("/blocked"), headers))
    }

    @Test
    fun `html quota response is rejected even with video content type`() = runBlocking {
        assertFalse(CsDirectMediaProbe(client).isPossiblyPlayable(url("/html"), headers))
    }

    @Test
    fun `successful head does not consume signed media with a get`() = runBlocking {
        assertTrue(CsDirectMediaProbe(client).isPossiblyPlayable(url("/head-ok"), headers))
        assertEquals(listOf("HEAD"), requests.map { it.first })
    }

    @Test
    fun `drive warning html is bounded and available for confirmation parsing`() = runBlocking {
        val result = CsDirectMediaProbe(client).inspectDrive(url("/drive-html"), headers)
        assertEquals(CsMediaResponseKind.HTML, result.kind)
        assertEquals(262_144, result.html?.length)
        assertTrue(result.html.orEmpty().startsWith("<html><form>confirm</form>"))
        assertEquals("bytes=0-262143", requests.single().second["range"])
    }

    @Test
    fun `range ignored never downloads a complete movie`() = runBlocking {
        val result = withTimeout(2_000L) { CsDirectMediaProbe(client).inspect(url("/large"), headers) }
        assertEquals(CsMediaResponseKind.VIDEO, result)
        assertEquals("bytes=0-4095", requests.single().second["range"])
    }
}
