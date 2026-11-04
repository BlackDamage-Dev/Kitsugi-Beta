package com.kitsugi.animelist.data.manga

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsugi.animelist.data.manga.loader.ChildFirstPathClassLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import uy.kohesive.injekt.Injekt

/** Published DEX fixtures with deterministic responses, not a live site test. */
class MangaTrPublishedApkTest {
    @Test fun publishedV24RunsThroughHostBridge() = runBlocking { verifyPublishedApk("mangatr-v24.apk") }
    @Test fun publishedV26RunsThroughHostBridge() = runBlocking { verifyPublishedApk("mangatr-v26.apk") }

    private suspend fun verifyPublishedApk(fixtureName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        assumeTrue("The CI-downloaded, checksum-pinned APK fixture is absent", assets.list("")!!.contains(fixtureName))
        val context = instrumentation.targetContext
        val apk = File(context.codeCacheDir, fixtureName)
        // Delete a previous read-only file before writing again.
        apk.delete()
        assets.open(fixtureName).use { input -> apk.outputStream().use { input.copyTo(it) } }
        assertTrue(apk.setReadOnly()) // Android 14 disallows writable dynamically loaded DEX.

        Injekt.addSingleton(Application::class.java, context.applicationContext as Application)
        Injekt.addSingleton(NetworkHelper::class.java, NetworkHelper(context))
        Injekt.addSingleton(Json::class.java, Json { ignoreUnknownKeys = true })
        val loader = ChildFirstPathClassLoader(apk.absolutePath, null, context.classLoader)
        val plugin = loader.loadClass("keiyoushi.source.Generated").getDeclaredConstructor().newInstance() as HttpSource
        // Trigger the real KeiSource client setup and its interceptor-name checks first.
        val originalClient = plugin.client
        // Two colored strips let us verify the real plugin's fragment-based
        // interceptor rearranges pixels, rather than merely fetching a URL.
        val bitmap = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            bitmap.setPixel(x, y, if (y < 16) Color.RED else Color.BLUE)
        }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val seen = mutableListOf<String>()
        val pageKey = "0123456789abcdef0123456789abcdef"
        val gateKey = "fixture-token"
        fun encrypt(text: String, key: String): String {
            val encoded = text.toByteArray(Charsets.UTF_8).mapIndexed { index, byte ->
                (byte.toInt() xor key[index % key.length].code).toByte()
            }.toByteArray()
            return android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP)
        }
        val config = """{"data":{"parts":"data-parts","order":"data-order","pageIndex":"data-page"}}"""
        val encryptedConfig = encrypt(encrypt(config, "boot|$pageKey|reader"), "gate|$gateKey|reader")
        val encryptedParts = encrypt("""["https://image.mngtr.site/page.png"]""", "attr|$pageKey|reader")
        val encryptedOrder = encrypt("[1,0]", "order|$pageKey|reader")
        val fixtureClient = originalClient.newBuilder().addInterceptor { chain ->
            val request = chain.request()
            synchronized(seen) { seen.add(request.url.encodedPath) }
            val path = request.url.encodedPath
            val body = when {
                path == "/arama.html" -> """<div class="arama-card"><a class="arama-card__title" href="/manga-chainsaw-man.html">Chainsaw Man</a></div>""".toResponseBody("text/html; charset=utf-8".toMediaType())
                path == "/manga-list-sayfala.html" -> """<div class="media-card"><a class="media-card__title" href="/manga-chainsaw-man.html">Chainsaw Man</a></div>""".toResponseBody("text/html; charset=utf-8".toMediaType())
                path == "/manga-chainsaw-man.html" -> """<h1 class="bento-hero-title">Chainsaw Man</h1><div id="results" data-manga="chainsaw-man"></div><script>var cfg={listKey:'fixture1234567890'}</script>""".toResponseBody("text/html; charset=utf-8".toMediaType())
                path == "/cek/fetch_pages_manga.php" -> {
                    val form = request.body as? okhttp3.FormBody
                    val offset = form?.let { f -> (0 until f.size).firstOrNull { f.name(it) == "offset" } }
                    val html = if (offset != null) "" else """<article class="bento-ep-card"><a class="bento-ep-title-link" href="/id-1"><span class="bento-ep-chapter-num">1.</span> Bölüm</a></article><article class="bento-ep-card"><a class="bento-ep-title-link" href="/id-2"><span class="bento-ep-chapter-num">1.</span> Bölüm</a></article>"""
                    html.toResponseBody("text/html; charset=utf-8".toMediaType())
                }
                path == "/id-1" || path == "/id-2" -> """<script type="application/json" id="rdm-fixture" data-key="$pageKey">$encryptedConfig</script><script>_fpx = "cek/gate.php"</script><div data-parts="$encryptedParts" data-order="$encryptedOrder" data-page="0"></div>""".toResponseBody("text/html; charset=utf-8".toMediaType())
                path == "/cek/gate.php" -> """{"k":"$gateKey"}""".toResponseBody("application/json; charset=utf-8".toMediaType())
                path == "/page.png" -> png.toResponseBody("image/png".toMediaType())
                else -> error("Unexpected fixture request: $path")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        // Inject only a test transport at the end of the plugin's real interceptor chain.
        // No production code or plugin implementation is replaced.
        // Release extensions can obfuscate both KeiSource and its delegate field.
        // Locate the initialized Lazy holding the exact client, not its name.
        var type: Class<*>? = plugin.javaClass
        var clientDelegate: java.lang.reflect.Field? = null
        while (type != null && type != HttpSource::class.java && clientDelegate == null) {
            clientDelegate = type.declaredFields.firstOrNull { field ->
                if (!Lazy::class.java.isAssignableFrom(field.type)) return@firstOrNull false
                field.isAccessible = true
                val value = field.get(plugin) as? Lazy<*>
                value != null && value.isInitialized() && value.value === originalClient
            }
            type = type.superclass
        }
        requireNotNull(clientDelegate).set(plugin, lazyOf(fixtureClient))
        val source = MihonSourceWrapper(plugin, context, "eu.kanade.tachiyomi.extension.tr.mangatr")
        val filters = source.selectFilters()
        assertTrue(filters.isNotEmpty())
        val first = filters.first { it.values.size > 1 }
        assertEquals("Chainsaw Man", source.fetchFilteredManga(2, mapOf(first.index to 1)).mangas.single().title)
        val search = source.fetchSearchManga(1, "Chainsaw Man")
        assertEquals("Chainsaw Man", search.mangas.single().title)
        val details = source.fetchMangaDetails("/manga-chainsaw-man.html")
        assertEquals("Chainsaw Man", details.title)
        assertEquals("/manga-chainsaw-man.html", details.url)
        val chapters = source.fetchChapterList("/manga-chainsaw-man.html")
        assertEquals(setOf("/id-1", "/id-2"), chapters.map { it.url }.toSet())
        val pages = source.fetchPageList(chapters.first())
        assertEquals("https://image.mngtr.site/page.png#1,0", pages.single().imageUrl)
        val plain = MangaPage(1, "", "https://image.mngtr.site/page.png")
        assertArrayEquals(png, source.getImage(plain).use { it.readBytes() })
        val processedBytes = source.getImage(pages.single()).use { it.readBytes() }
        val processed = requireNotNull(BitmapFactory.decodeByteArray(processedBytes, 0, processedBytes.size))
        try {
            val top = processed.getPixel(8, 4)
            val bottom = processed.getPixel(8, 27)
            assertTrue("First decoded strip should be blue", Color.blue(top) > Color.red(top) + 80)
            assertTrue("Second decoded strip should be red", Color.red(bottom) > Color.blue(bottom) + 80)
        } finally { processed.recycle() }
        assertTrue(seen.contains("/arama.html"))
        assertTrue(seen.contains("/cek/fetch_pages_manga.php"))
        assertTrue(seen.contains("/page.png"))
        Unit
    }
}
