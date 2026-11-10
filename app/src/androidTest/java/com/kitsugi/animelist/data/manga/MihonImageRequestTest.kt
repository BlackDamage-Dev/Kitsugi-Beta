package com.kitsugi.animelist.data.manga

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Synthetic source: asserts the wrapper does not bypass source headers or interceptors. */
class MihonImageRequestTest {
    @Test fun sourceRequestAndInterceptorArePreserved() = runBlocking {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        var interceptorCalled = false
        var referer: String? = null
        var fragment: String? = null
        val source = object : HttpSource() {
            override val name = "Synthetic source"
            override val lang = "tr"
            override val supportsLatest = false
            override val baseUrl = "https://example.invalid"
            override val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                referer = request.header("Referer")
                fragment = request.url.fragment
                interceptorCalled = true
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(bytes.toResponseBody("image/png".toMediaType())).build()
            }.build()
            override fun imageRequest(page: Page): Request = Request.Builder()
                .url(page.imageUrl!!).header("Referer", "$baseUrl/reader").build()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wrapper = MihonSourceWrapper(source, context, "test.synthetic")
        val page = MangaPage(0, "/reader", "https://example.invalid/image#1,0")
        val actual = wrapper.getImage(page).use { it.readBytes() }
        assertTrue(interceptorCalled)
        assertEquals("https://example.invalid/reader", referer)
        assertEquals("1,0", fragment)
        assertArrayEquals(bytes, actual)
    }
}
