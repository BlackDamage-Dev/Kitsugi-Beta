package com.kitsugi.animelist.data.manga

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsugi.animelist.data.manga.loader.MangaCache
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import uy.kohesive.injekt.Injekt

/** Contract exercise for the host bridge; it does not claim to test the remote site. */
class MihonMangaIntegrationTest {
    private class Translation : Filter.Select<String>("Çeviri Durumu", arrayOf("Hepsi", "Tamamlanan", "Devam Eden", "Bırakılan", "Olmayan"))
    private class FakeSource(private val png: ByteArray) : HttpSource() {
        override val name = "Manga-TR test"
        override val lang = "tr"
        override val supportsLatest = true
        override val baseUrl = "https://manga-tr.com"
        var requestedFragment: String? = null
        var requestedReferer: String? = null
        var selected: Int? = null
        var requestedPage: Int? = null
        var failSearchWithLinkageError = false
        var failImageWithLinkageError = false
        var returnDetailsWithoutUrl = false
        var chapterUrl: String? = null
        var updateCalls = 0
        private val updateActive = java.util.concurrent.atomic.AtomicBoolean(false)

        override fun headersBuilder(): Headers.Builder = Headers.Builder().add("Referer", baseUrl)
        override val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestedFragment = chain.request().url.fragment
            requestedReferer = chain.request().header("Referer")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(png.toResponseBody()).build()
        }.build()
        override fun imageRequest(page: Page): okhttp3.Request {
            if (failImageWithLinkageError) throw NoSuchMethodError("fixtureMissingImageMethod")
            return super.imageRequest(page)
        }
        override fun getFilterList() = FilterList(Translation())
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
            if (failSearchWithLinkageError) throw NoSuchMethodError("fixtureMissingMethod")
            requestedPage = page
            selected = (filters[0] as Translation).state
            return MangasPage(emptyList(), page < 3)
        }
        override suspend fun getMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
            check(updateActive.compareAndSet(false, true)) { "same-manga update overlapped" }
            try {
                updateCalls++
                delay(75)
                fun chapter(url: String, group: String) = SChapter.create().apply {
                    this.url = url; name = "1. Bölüm"; chapter_number = 1f; scanlator = group
                }
                val details = if (returnDetailsWithoutUrl) SManga.create().apply { title = "Chainsaw Man" } else manga
                return SMangaUpdate(details, listOf(chapter("/id-1", "Bağımsız"), chapter("/id-2", "Diğer"), chapter("/id-1", "Tekrar")))
            } finally { updateActive.set(false) }
        }
        override suspend fun getPageList(chapter: SChapter): List<Page> {
            chapterUrl = chapter.url
            return listOf(Page(0, imageUrl = "https://image.mngtr.site/${System.nanoTime()}.png#100,201"))
        }
    }

    @Test fun cookieJarHonorsCookieDomainAndPathInsteadOfResponseOrigin() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedCache = java.io.File(base.cacheDir, "cookie-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : android.content.ContextWrapper(base) {
            override fun getCacheDir(): java.io.File = isolatedCache
        }
        try {
            val jar = eu.kanade.tachiyomi.network.WebViewCookieJar(context)
            val forum = okhttp3.HttpUrl.Builder().scheme("https").host("forum.example.test").build()
            val main = okhttp3.HttpUrl.Builder().scheme("https").host("example.test").build()
            val chapter = main.newBuilder().addPathSegment("reader").build()
            val shared = okhttp3.Cookie.Builder().name("session").value("first").domain("example.test").path("/").build()
            val forumOnly = okhttp3.Cookie.Builder().name("private").value("forum").hostOnlyDomain("forum.example.test").path("/").build()
            jar.saveFromResponse(forum, listOf(shared, forumOnly))
            assertEquals(listOf("session"), jar.loadForRequest(main).map { it.name })
            assertEquals(setOf("session", "private"), jar.loadForRequest(forum).map { it.name }.toSet())
            val replacement = okhttp3.Cookie.Builder().name("session").value("second").domain("example.test").path("/").build()
            val reader = okhttp3.Cookie.Builder().name("session").value("reader").domain("example.test").path("/reader").build()
            jar.saveFromResponse(main, listOf(replacement, reader))
            assertEquals(listOf("reader", "second"), jar.loadForRequest(chapter).map { it.value })
            assertEquals("second", jar.loadForRequest(forum).single { it.name == "session" }.value)
            val unrelated = okhttp3.HttpUrl.Builder().scheme("https").host("example.test.attacker.test").build()
            assertTrue(jar.loadForRequest(unrelated).isEmpty())
            val deleted = okhttp3.Cookie.Builder().name("session").value("").domain("example.test").path("/").expiresAt(0).build()
            jar.saveFromResponse(forum, listOf(deleted))
            assertTrue(jar.loadForRequest(main).isEmpty())
            assertEquals("reader", eu.kanade.tachiyomi.network.WebViewCookieJar(context).loadForRequest(chapter).single().value)
        } finally {
            isolatedCache.deleteRecursively()
        }
    }

    @Test fun webViewSyncDoesNotBroadenUnknownCookieDomains() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedCache = java.io.File(base.cacheDir, "webview-cookie-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : android.content.ContextWrapper(base) {
            override fun getCacheDir(): java.io.File = isolatedCache
        }
        val manager = android.webkit.CookieManager.getInstance()
        try {
            val host = "https://example.test/"
            manager.setCookie(host, "manga_test_only=secret; Path=/; Secure")
            manager.flush()
            val jar = eu.kanade.tachiyomi.network.WebViewCookieJar(context)
            jar.syncFromWebView(host)
            val main = okhttp3.HttpUrl.Builder().scheme("https").host("example.test").build()
            val sub = okhttp3.HttpUrl.Builder().scheme("https").host("forum.example.test").build()
            assertTrue(jar.loadForRequest(main).any { it.name == "manga_test_only" })
            assertFalse(jar.loadForRequest(sub).any { it.name == "manga_test_only" })
        } finally {
            manager.setCookie("https://example.test/", "manga_test_only=; Path=/; Max-Age=0")
            isolatedCache.deleteRecursively()
        }
    }

    @Test fun hostInterceptorNamesMatchKeiSourceRuntimeChecks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val names = NetworkHelper(context).client.interceptors.map { it.javaClass.simpleName }
        assertTrue(names.contains("UncaughtExceptionInterceptor"))
        assertTrue(names.contains("UserAgentInterceptor"))
        assertTrue(names.contains("CloudflareInterceptor"))
    }

    @Test fun pluginLinkageFailureIsReportedAsSourceError() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Injekt.addSingleton(NetworkHelper::class.java, NetworkHelper(context))
        val fake = FakeSource(byteArrayOf()).apply { failSearchWithLinkageError = true }
        val wrapper = MihonSourceWrapper(fake, context, "eu.kanade.tachiyomi.extension.tr.mangatr")
        val error = runCatching { wrapper.fetchSearchManga(1, "Chainsaw Man") }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
        assertTrue(error?.cause is NoSuchMethodError)
        Unit
    }

    @Test fun detailsKeepTheRequestedUrlWhenPluginDoesNotSetIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Injekt.addSingleton(NetworkHelper::class.java, NetworkHelper(context))
        val fake = FakeSource(byteArrayOf()).apply { returnDetailsWithoutUrl = true }
        val wrapper = MihonSourceWrapper(fake, context, "eu.kanade.tachiyomi.extension.tr.mangatr")
        val details = wrapper.fetchMangaDetails("/manga-chainsaw-man.html")
        assertEquals("/manga-chainsaw-man.html", details.url)
        assertEquals("Chainsaw Man", details.title)
    }

    @Test fun pluginImageLinkageFailureIsReportedAsSourceError() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Injekt.addSingleton(NetworkHelper::class.java, NetworkHelper(context))
        val fake = FakeSource(byteArrayOf()).apply { failImageWithLinkageError = true }
        val wrapper = MihonSourceWrapper(fake, context, "eu.kanade.tachiyomi.extension.tr.mangatr")
        val error = runCatching { wrapper.getImage(MangaPage(0, "", "https://image.mngtr.site/a.png")) }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
        assertTrue(error?.cause is NoSuchMethodError)
        Unit
    }

    @Test fun invalidMangaTrImagePermutationFailsBeforePluginRequest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Injekt.addSingleton(NetworkHelper::class.java, NetworkHelper(context))
        val fake = FakeSource(byteArrayOf())
        val wrapper = MihonSourceWrapper(fake, context, "eu.kanade.tachiyomi.extension.tr.mangatr")
        val error = runCatching { wrapper.getImage(MangaPage(0, "", "https://image.mngtr.site/a.png#100,200")) }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
        assertTrue(error?.message?.contains("geçersiz") == true)
        assertNull(fake.requestedFragment)
        Unit
    }

    @Test fun bridgePreservesUrlsGroupsFilterPositionsAndSourceProcessedImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Injekt.addSingleton(NetworkHelper::class.java, NetworkHelper(context))
        val bitmap = Bitmap.createBitmap(2, 4, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val fake = FakeSource(png)
        val source = MihonSourceWrapper(fake, context, "eu.kanade.tachiyomi.extension.tr.mangatr")
        val chapters = coroutineScope {
            val details = async { source.fetchMangaDetails("/manga-example.html") }
            val list = async { source.fetchChapterList("/manga-example.html") }
            details.await()
            list.await()
        }
        assertEquals(2, fake.updateCalls)
        assertEquals(listOf("/id-1", "/id-2"), chapters.map { it.url })
        assertEquals(listOf("Bağımsız", "Diğer"), chapters.map { it.scanlator })
        assertEquals("Devam Eden", source.selectFilters().single().values[2])
        assertTrue(source.fetchFilteredManga(2, mapOf(0 to 2)).hasNextPage)
        assertEquals(2, fake.selected)
        assertEquals(2, fake.requestedPage)

        val cache = MangaCache(context)
        val loader = MangaPageLoaderV2(context, source, cache)
        try {
            loader.loadPageList(chapters[1])
            assertEquals("/id-2", fake.chapterUrl)
            val page = loader.pages.value.single()
            loader.loadPage(page)
            withTimeout(10_000) {
                while (page.status != MangaPageStatus.Ready && page.status != MangaPageStatus.Error) delay(20)
            }
            assertEquals(MangaPageStatus.Ready, page.status)
            assertEquals("100,201", fake.requestedFragment)
            assertEquals("https://manga-tr.com", fake.requestedReferer)
            assertArrayEquals(png, page.localFile!!.readBytes())
            assertEquals(0.5f, page.aspectRatio!!, 0.001f)
            assertEquals(cache.getImageFile(mangaImageCacheKey(source.pkgName, page.imageUrl!!)), page.localFile)
            page.localFile?.delete()
        } finally { loader.recycle() }
        Unit
    }
}
