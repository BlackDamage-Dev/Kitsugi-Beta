package com.kitsugi.animelist.data.auth

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ShikimoriOAuthTest {

    @Test
    fun canonicalBaseUrlUsesShikimoriIo() {
        val authUrl = ShikimoriApiClient.buildAuthorizeUrl()
        assertTrue("Auth URL must use shikimori.io host", authUrl.startsWith("https://shikimori.io/oauth/authorize"))
        assertTrue("Auth URL must contain response_type=code", authUrl.contains("response_type=code"))
        assertTrue("Auth URL must contain scope=user_rates", authUrl.contains("scope=user_rates"))
        assertTrue(
            "The shared OAuth app's registered callback must be used for the default 1-tap flow",
            authUrl.contains("redirect_uri=aniyomi%3A%2F%2Fshikimori-auth")
        )
        assertEquals("aniyomi://shikimori-auth", ShikimoriApiClient.DEEP_LINK_REDIRECT_URI)
        assertEquals("kitsugi://shikimori-auth", ShikimoriApiClient.FALLBACK_DEEP_LINK_REDIRECT_URI)

        val appUrl = ShikimoriApiClient.buildNewApplicationUrl()
        assertTrue("Application URL must use shikimori.io host", appUrl.startsWith("https://shikimori.io/oauth/applications/new"))
        assertTrue("New applications should use the primary callback", appUrl.contains("aniyomi%3A%2F%2Fshikimori-auth"))
    }

    @Test
    fun sanitizeAuthCodeExtractsCleanToken() {
        // Plain code
        assertEquals("code_12345", ShikimoriApiClient.sanitizeAuthCode("code_12345"))
        assertEquals("code_12345", ShikimoriApiClient.sanitizeAuthCode("  code_12345  "))

        // Query param in URL
        assertEquals("code_12345", ShikimoriApiClient.sanitizeAuthCode("https://shikimori.io/oauth/authorize?code=code_12345&state=xyz"))
        assertEquals("code_12345", ShikimoriApiClient.sanitizeAuthCode("?code=code_12345"))
        assertEquals("code_12345", ShikimoriApiClient.sanitizeAuthCode("code=code_12345&foo=bar"))

        // Quotes and URL encoded characters
        assertEquals("code_12345", ShikimoriApiClient.sanitizeAuthCode("\"code_12345\""))
        assertEquals("code_12345", ShikimoriApiClient.sanitizeAuthCode("code%3Dcode_12345"))
    }

    @Test
    fun tokenExceptionCarriesStatusAndInvalidGrantFlag() {
        val exInvalidGrant = ShikimoriApiClient.ShikimoriTokenException("invalid grant", isInvalidGrant = true, status = 400)
        assertTrue(exInvalidGrant.isInvalidGrant)
        assertEquals(400, exInvalidGrant.status)

        val exExpired = ShikimoriApiClient.ShikimoriTokenException("unauthorized", isInvalidGrant = false, status = 401)
        assertFalse(exExpired.isInvalidGrant)
        assertEquals(401, exExpired.status)
    }

    @Test
    fun executeShikimoriPreservesAuthorizationHeaderAcrossRedirects() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val receivedAuthOnTarget = AtomicReference<String?>()
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            try {
                // Request 1: hits /legacy, gets 301 redirect to /canonical
                val s1 = serverSocket.accept()
                val reader1 = BufferedReader(InputStreamReader(s1.getInputStream(), StandardCharsets.UTF_8))
                while (true) {
                    val line = reader1.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val writer1 = PrintWriter(OutputStreamWriter(s1.getOutputStream(), StandardCharsets.UTF_8), true)
                writer1.print("HTTP/1.1 301 Moved Permanently\r\nLocation: http://127.0.0.1:$port/canonical\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                writer1.flush()
                s1.close()

                // Request 2: hits /canonical, check Authorization header
                val s2 = serverSocket.accept()
                val reader2 = BufferedReader(InputStreamReader(s2.getInputStream(), StandardCharsets.UTF_8))
                while (true) {
                    val line = reader2.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Authorization:", ignoreCase = true)) {
                        receivedAuthOnTarget.set(line.substringAfter(":").trim())
                    }
                }
                val body = "{\"ok\":true}"
                val writer2 = PrintWriter(OutputStreamWriter(s2.getOutputStream(), StandardCharsets.UTF_8), true)
                writer2.print("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                writer2.flush()
                s2.close()
            } catch (_: Exception) {
            }
        }

        try {
            val initialRequest = Request.Builder()
                .url("http://127.0.0.1:$port/legacy")
                .header("Authorization", "Bearer valid_test_token_123")
                .get()
                .build()

            val response = ShikimoriApiClient.executeShikimori(initialRequest)
            assertEquals(200, response.code)
            val body = response.body?.string()
            assertEquals("{\"ok\":true}", body)

            // CRITICAL CHECK: OkHttp strips Authorization on redirects by default.
            // Our custom executeShikimori handler MUST preserve it across redirects.
            assertEquals("Bearer valid_test_token_123", receivedAuthOnTarget.get())
        } finally {
            serverSocket.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun executeShikimoriFailsOverAcrossMirrorsOnHtmlBlockPageWhilePreservingAuthorization() {
        val blockedServer = ServerSocket(0)
        val healthyServer = ServerSocket(0)
        val receivedAuthOnMirror = AtomicReference<String?>()
        val executor = Executors.newFixedThreadPool(2)

        executor.submit {
            try {
                val s = blockedServer.accept()
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val html = "<!DOCTYPE html><html><body>ISP Access Restricted</body></html>"
                val writer = PrintWriter(OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)
                writer.print(
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${html.toByteArray(StandardCharsets.UTF_8).size}\r\n" +
                        "Connection: close\r\n\r\n$html"
                )
                writer.flush()
                s.close()
            } catch (_: Exception) {
            }
        }

        executor.submit {
            try {
                val s = healthyServer.accept()
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Authorization:", ignoreCase = true)) {
                        receivedAuthOnMirror.set(line.substringAfter(":").trim())
                    }
                }
                val json = "[{\"id\":99,\"target_id\":52588}]"
                val writer = PrintWriter(OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)
                writer.print(
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${json.toByteArray(StandardCharsets.UTF_8).size}\r\n" +
                        "Connection: close\r\n\r\n$json"
                )
                writer.flush()
                s.close()
            } catch (_: Exception) {
            }
        }

        try {
            val blockedOrigin = "http://127.0.0.1:${blockedServer.localPort}"
            val healthyOrigin = "http://127.0.0.1:${healthyServer.localPort}"
            val req = Request.Builder()
                .url("$blockedOrigin/api/users/1/anime_rates?page=1&limit=500")
                .header("Authorization", "Bearer mirror_token_456")
                .get()
                .build()

            ShikimoriApiClient.executeShikimori(
                request = req,
                candidateBaseUrls = listOf(blockedOrigin, healthyOrigin)
            ).use { response ->
                assertEquals(200, response.code)
                assertEquals("[{\"id\":99,\"target_id\":52588}]", response.body?.string())
                assertEquals("Bearer mirror_token_456", receivedAuthOnMirror.get())
            }
        } finally {
            blockedServer.close()
            healthyServer.close()
            executor.shutdownNow()
        }
    }
}
