package com.kitsugi.animelist.testing

import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Minimal loopback HTTP/1.1 server; avoids JDK-only modules and OkHttp version coupling. */
class LocalHttpTestServer(private val responses: List<Response>) : AutoCloseable {
    data class Response(val status: Int = 200, val headers: Map<String, String> = emptyMap(), val body: ByteArray = byteArrayOf())
    data class Request(val method: String, val path: String, val headers: Map<String, String>)
    private val socket = ServerSocket(0)
    private val requests = LinkedBlockingQueue<Request>()
    private val worker = thread(isDaemon = true, name = "subtitle-http-test") {
        try {
            for (response in responses) {
                socket.accept().use { connection ->
                    connection.soTimeout = 5000
                    val reader = connection.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val first = reader.readLine().split(" ")
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val colon = line.indexOf(':')
                        if (colon > 0) headers[line.substring(0, colon).lowercase()] = line.substring(colon + 1).trim()
                    }
                    requests.put(Request(first[0], first[1], headers))
                    val output = connection.getOutputStream()
                    val allHeaders = response.headers.toMutableMap()
                    if (allHeaders.keys.none { it.equals("Content-Length", true) }) allHeaders["Content-Length"] = response.body.size.toString()
                    output.write("HTTP/1.1 ${response.status} Test\r\nConnection: close\r\n".toByteArray())
                    allHeaders.forEach { (key, value) -> output.write("$key: $value\r\n".toByteArray()) }
                    output.write("\r\n".toByteArray())
                    if (first[0] != "HEAD") output.write(response.body)
                    output.flush()
                }
            }
        } catch (_: java.net.SocketException) { /* closed by test */ }
    }
    val url: String get() = "http://127.0.0.1:${socket.localPort}"
    fun takeRequest(): Request = requests.poll(10, TimeUnit.SECONDS) ?: error("Expected HTTP request")
    override fun close() { socket.close(); worker.join(10000) }
}
