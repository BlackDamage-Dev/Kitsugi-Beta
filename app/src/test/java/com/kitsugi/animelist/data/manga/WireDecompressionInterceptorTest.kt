package com.kitsugi.animelist.data.manga

import eu.kanade.tachiyomi.network.interceptor.WireDecompressionInterceptor
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import okhttp3.CompressionInterceptor
import okhttp3.Gzip
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.brotli.Brotli
import okhttp3.zstd.Zstd
import okio.ByteString.Companion.decodeHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireDecompressionInterceptorTest {

    private val interceptor = WireDecompressionInterceptor()

    @Test
    fun decompressesBrotliWhenExtensionSetsExplicitAcceptEncoding() {
        val brotliHex =
            "1bce00009c05ceb9f028d14e416230f718960a537b0922d2f7b6adef56532c08dff44551516690131494db" +
                "6021c7e3616c82c1bc2416abb919aaa06e8d30d82cc2981c2f5c900bfb8ee29d5c03deb1c0dacff80e" +
                "abe82ba64ed250a497162006824684db917963ecebe041b352a3e62d629cc97b95cac24265b175171e" +
                "5cb384cd0912aeb5b5dd9555f2dd1a9b20688201"
        val wireBytes = brotliHex.decodeHex().toByteArray()

        var observedAcceptEncoding: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(CompressionInterceptor(Zstd, Brotli, Gzip))
            .addInterceptor(interceptor)
            .addInterceptor(
                Interceptor { chain ->
                    observedAcceptEncoding = chain.request().header("Accept-Encoding")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Encoding", "br")
                        .header("Content-Length", wireBytes.size.toString())
                        .body(wireBytes.toResponseBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                },
            )
            .build()

        val request = Request.Builder()
            .url("https://manga-tr.com/arama.html?icerik=Berserk")
            .header("Accept-Encoding", "gzip, deflate, br")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals("gzip, deflate, br", observedAcceptEncoding)
            assertNull(response.header("Content-Encoding"))
            assertNull(response.header("Content-Length"))
            val text = response.body!!.string()
            assertTrue(text.contains("\"brotli\": true"))
        }
    }

    @Test
    fun decompressesGzipAndStripsRedundantIdentityTokenFromAjaxHeaders() {
        val html = """<article class="bento-ep-card"><a class="bento-ep-title-link" href="/id-1">Berserk 1</a></article>"""
        val gz = ByteArrayOutputStream().also { bos ->
            GZIPOutputStream(bos).use { it.write(html.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()

        var observedAcceptEncoding: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(
                Interceptor { chain ->
                    observedAcceptEncoding = chain.request().header("Accept-Encoding")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Encoding", "gzip")
                        .body(gz.toResponseBody("text/html; charset=utf-8".toMediaType()))
                        .build()
                },
            )
            .build()

        val request = Request.Builder()
            .url("https://manga-tr.com/cek/fetch_pages_manga.php")
            .header("Accept-Encoding", "gzip, deflate, br")
            .addHeader("Accept-Encoding", "identity")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals("identity", observedAcceptEncoding)
            assertNull(response.header("Content-Encoding"))
            assertEquals(html, checkNotNull(response.body).string())
        }
    }

    @Test
    fun decompressesDeflateWireBody() {
        val html = "<div class=\"arama-card\"><a class=\"arama-card__title\" href=\"/manga-berserk.html\">Berserk</a></div>"
        val deflated = ByteArrayOutputStream().also { bos ->
            DeflaterOutputStream(bos).use { it.write(html.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Encoding", "deflate")
                        .body(deflated.toResponseBody("text/html; charset=utf-8".toMediaType()))
                        .build()
                },
            )
            .build()

        val request = Request.Builder()
            .url("https://manga-tr.com/arama.html?icerik=Berserk")
            .header("Accept-Encoding", "gzip, deflate, br")
            .build()

        client.newCall(request).execute().use { response ->
            assertNull(response.header("Content-Encoding"))
            assertEquals(html, response.body!!.string())
        }
    }

    @Test
    fun zstdShimAdvertisesSafeEncodingAndDoesNotPassRawCompressedBytes() {
        assertEquals("gzip", Zstd.encoding)
        assertEquals("br", Brotli.encoding)
    }
}
