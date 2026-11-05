package com.kitsugi.animelist.data.manga

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.webview.InterceptedRequest
import org.koitharu.kotatsu.parsers.webview.InterceptionConfig as ParsersInterceptionConfig
import org.koitharu.kotatsu.parsers.model.Manga as KotatsuManga
import org.koitharu.kotatsu.parsers.model.MangaChapter as KotatsuChapter
import org.koitharu.kotatsu.parsers.model.MangaPage as KotatsuPage
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * KotatsuExtensionAdapter
 *
 * kotatsu-parsers-redo kütüphanesindeki 1300+ kaynağı Kitsugi'nun MangaSource
 * arayüzüne adapte eder.
 *
 * Futon ile aynı mantık:
 *   - MangaParserSource enum'undan tüm kaynaklar alınır (compile-time)
 *   - MangaLoaderContext üzerinden gerçek HTTP istekleri yapılır
 *   - Dil filtresi ile sadece seçili dildeki kaynaklar aktif olur
 */
object KotatsuExtensionAdapter {

    private const val TAG = "KotatsuExtAdapter"
    private const val PREFS_NAME = "kotatsu_adapter_prefs"
    private const val KEY_ACTIVE_LANGS = "active_langs"

    /**
     * Eski/bozuk veya Kitsugi'de birinci sınıf (native) karşılığı bulunan
     * Kotatsu ayrıştırıcıları kalıcı olarak devre dışıdır.
     */
    internal val DISABLED_PARSER_NAMES: Set<String> = setOf("GOLGEBAHCESI")

    fun isParserDisabled(parserName: String?): Boolean {
        val clean = parserName?.trim().orEmpty()
        return clean.isNotEmpty() && DISABLED_PARSER_NAMES.any { it.equals(clean, ignoreCase = true) }
    }

    // T4-11: Bounded scope — SupervisorJob so one child failure doesn't cancel siblings
    private val adapterScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + object : CoroutineExceptionHandler {
            override val key: kotlin.coroutines.CoroutineContext.Key<*> get() = CoroutineExceptionHandler.Key
            override fun handleException(context: kotlin.coroutines.CoroutineContext, exception: Throwable) {
                Log.e(TAG, "Uncaught coroutine error: ${exception.message}", exception)
            }
        }
    )

    // Tüm yüklü kaynaklar (ALL langs) — başlatma sonrası dolu olur
    private val allSources = mutableListOf<KotatsuMangaSource>()
    private var loaderContext: MangaLoaderContext? = null
    private var isInitialized = false
    @Volatile private var prefsLoaded = false
    @Volatile private var appContextRef: Context? = null

    /**
     * Aktif dil seti — varsayılan profilde BOŞTUR (yalnızca dahili Gölge Bahçesi aktiftir).
     * Kullanıcı Eklentiler ekranından manuel dil seçerse SharedPreferences'a kaydedilir.
     */
    private val activeLangs = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Synchronized
    fun loadSavedPrefs(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        if (prefsLoaded) return
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_ACTIVE_LANGS, emptySet()).orEmpty()
        synchronized(activeLangs) {
            activeLangs.clear()
            activeLangs.addAll(saved.filter { it.isNotBlank() })
        }
        prefsLoaded = true
    }

    private fun persistActiveLangs(context: Context? = appContextRef) {
        val ctx = context?.applicationContext ?: return
        val snapshot = synchronized(activeLangs) { activeLangs.toSet() }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_ACTIVE_LANGS, snapshot)
            .apply()
    }

    /** Aktif dilleri döner (UI chip gösterimi için) */
    fun getActiveLangs(): Set<String> = synchronized(activeLangs) { activeLangs.toSet() }

    /** Tek bir dili toggle eder (son dili de kapatmaya izin verir) */
    fun toggleLang(lang: String, context: Context? = appContextRef) {
        val shouldInit: Boolean
        synchronized(activeLangs) {
            if (lang in activeLangs) {
                activeLangs.remove(lang)
                shouldInit = false
            } else {
                activeLangs.add(lang)
                shouldInit = true
            }
        }
        persistActiveLangs(context)
        if (shouldInit && context != null) {
            initializeLazy(context)
        }
        Log.i(TAG, "Kotatsu dil toggle: $lang → aktif: ${getActiveLangs()}")
    }

    /** Aktif dil setini toptan günceller */
    fun setActiveLangs(langs: Set<String>, context: Context? = appContextRef) {
        if (context != null) {
            appContextRef = context.applicationContext
            prefsLoaded = true
        }
        synchronized(activeLangs) {
            activeLangs.clear()
            activeLangs.addAll(langs.filter { it.isNotBlank() })
        }
        persistActiveLangs(context)
        if (langs.isNotEmpty() && context != null) {
            initializeLazy(context)
        }
        Log.i(TAG, "Kotatsu aktif diller güncellendi: $langs")
    }

    /**
     * Tüm Kotatsu parser kaynaklarını MangaParserSource enum'undan yükler.
     * Bu metod KitsugiApplication.onCreate() → syncKotatsuSources() tarafından çağrılır.
     *
     * Futon'daki MangaSourcesRepository.allMangaSources ile birebir aynı mantık.
     */
    private var isInitializing = false

    /**
     * Tüm Kotatsu parser kaynaklarını MangaParserSource enum'undan yükler.
     * Bu metod KitsugiApplication.onCreate() → syncKotatsuSources() tarafından çağrılır.
     *
     * Futon'daki MangaSourcesRepository.allMangaSources ile birebir aynı mantık.
     */
    fun initialize(context: Context) {
        initializeLazy(context)
    }

    fun initializeLazy(context: Context) {
        if (isInitialized || isInitializing) return
        isInitializing = true
        // T4-11: Use bounded adapterScope instead of GlobalScope
        adapterScope.launch {
            try {
                loaderContext = KitsugiKotatsuContext(context)
                val ctx = loaderContext ?: run { isInitializing = false; return@launch }

                val result = mutableListOf<KotatsuMangaSource>()

                // MangaParserSource.entries → bozuk ve kalıcı devre dışı bırakılanlar hariç
                for (parserSource in MangaParserSource.entries) {
                    if (isParserDisabled(parserSource.name)) continue
                    if (parserSource.isBroken()) continue
                    try {
                        val parser = ctx.newParserInstance(parserSource)
                        result.add(
                            KotatsuMangaSource(
                                parserSource = parserSource,
                                parser = parser,
                                context = ctx as KitsugiKotatsuContext,
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Parser oluşturulamadı: ${parserSource.name} → ${e.message}")
                    }
                }

                synchronized(allSources) {
                    allSources.clear()
                    allSources.addAll(result)
                }
                isInitialized = true
                isInitializing = false

                val langCounts = result.groupBy { it.lang }.mapValues { it.value.size }
                Log.i(TAG, "Kotatsu init: ${result.size} kaynak | Dil: $langCounts | Aktif: $activeLangs")
            } catch (e: Exception) {
                isInitializing = false
                Log.e(TAG, "Kotatsu initialize hatası: ${e.message}", e)
            }
        }
    }

    /** Sadece aktif dillerdeki kaynakları döner (eski GOLGEBAHCESI her zaman hariç) */
    fun getActiveSources(): List<KotatsuMangaSource> {
        val langsSnapshot = getActiveLangs()
        if (langsSnapshot.isEmpty()) return emptyList()
        return synchronized(allSources) {
            allSources.filter {
                !isParserDisabled(it.parserSource.name) && it.lang in langsSnapshot
            }
        }
    }

    /** Tüm kaynaklar (istatistik/UI için) */
    fun getLoadedSources(): List<KotatsuMangaSource> = synchronized(allSources) {
        allSources.toList()
    }

    /**
     * Güncel JSON kataloğunu alır. Kotatsu parser'ları derleme zamanında (compile-time)
     * eklendiği için dinamik kod yüklenemez; ancak catalog SHA eşitlemesi için
     * bu metod çağrılır. Mevcut yüklü kaynak listesini döner.
     */
    fun loadSources(jsonStr: String): List<KotatsuMangaSource> {
        Log.i(TAG, "loadSources çağrıldı, JSON katalog boyutu: ${jsonStr.length} karakter")
        return getLoadedSources()
    }

    /** Dile göre filtrele */
    fun getSourcesByLang(lang: String): List<KotatsuMangaSource> = synchronized(allSources) {
        allSources.filter { it.lang.equals(lang, ignoreCase = true) }
    }

    fun getSourceCount(): Int = synchronized(allSources) { allSources.size }
    fun getActiveSourceCount(): Int = getActiveSources().size

    /** Kataloğun içerdiği tüm dil kodlarını döner (TR her zaman ilk) */
    fun getAllLangs(): List<String> = synchronized(allSources) {
        val langs = allSources.map { it.lang }.distinct().sorted().toMutableList()
        if (langs.remove("tr")) langs.add(0, "tr")
        return langs
    }

    // MangaParserSource.isBroken() yok diye Futon'dan alınan yardımcı
    private fun MangaParserSource.isBroken(): Boolean = try {
        // Bazı kaynakların contentType veya locale bilgisi eksik olabilir
        this.locale // null dönerse exception fırlayabilir
        false
    } catch (_: Exception) {
        true
    }
}

// ─── KitsugiSourceConfig — MangaSourceConfig implementasyonu ───────────────────
class KitsugiSourceConfig(
    context: Context,
    source: org.koitharu.kotatsu.parsers.model.MangaSource
) : org.koitharu.kotatsu.parsers.config.MangaSourceConfig {
    private val prefs = context.getSharedPreferences("kotatsu_src_${source.name.replace('/', '$')}", Context.MODE_PRIVATE)

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: org.koitharu.kotatsu.parsers.config.ConfigKey<T>): T {
        val value = when (key) {
            is org.koitharu.kotatsu.parsers.config.ConfigKey.UserAgent -> prefs.getString(key.key, key.defaultValue) ?: key.defaultValue
            is org.koitharu.kotatsu.parsers.config.ConfigKey.Domain -> prefs.getString(key.key, key.defaultValue) ?: key.defaultValue
            is org.koitharu.kotatsu.parsers.config.ConfigKey.ShowSuspiciousContent -> prefs.getBoolean(key.key, key.defaultValue)
            is org.koitharu.kotatsu.parsers.config.ConfigKey.SplitByTranslations -> prefs.getBoolean(key.key, key.defaultValue)
            is org.koitharu.kotatsu.parsers.config.ConfigKey.PreferredImageServer -> prefs.getString(key.key, key.defaultValue) ?: key.defaultValue
            is org.koitharu.kotatsu.parsers.config.ConfigKey.DisableUpdateChecking -> prefs.getBoolean(key.key, key.defaultValue)
            is org.koitharu.kotatsu.parsers.config.ConfigKey.InterceptCloudflare -> prefs.getBoolean(key.key, key.defaultValue)
            else -> key.defaultValue
        }
        return value as T
    }
}

// ─── BitmapWrapper — Kotatsu Bitmap implementasyonu ──────────────────────────
class BitmapWrapper private constructor(
    private val androidBitmap: android.graphics.Bitmap,
) : org.koitharu.kotatsu.parsers.bitmap.Bitmap, AutoCloseable {

    private val canvas by lazy { android.graphics.Canvas(androidBitmap) }

    override val height: Int
        get() = androidBitmap.height

    override val width: Int
        get() = androidBitmap.width

    override fun drawBitmap(sourceBitmap: org.koitharu.kotatsu.parsers.bitmap.Bitmap, src: org.koitharu.kotatsu.parsers.bitmap.Rect, dst: org.koitharu.kotatsu.parsers.bitmap.Rect) {
        val androidSourceBitmap = (sourceBitmap as BitmapWrapper).androidBitmap
        canvas.drawBitmap(androidSourceBitmap, src.toAndroidRect(), dst.toAndroidRect(), null)
    }

    override fun close() {
        androidBitmap.recycle()
    }

    companion object {
        fun create(width: Int, height: Int) = BitmapWrapper(
            android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888),
        )

        private fun org.koitharu.kotatsu.parsers.bitmap.Rect.toAndroidRect() = android.graphics.Rect(left, top, right, bottom)
    }
}

// ─── KitsugiKotatsuContext — MangaLoaderContext implementasyonu ─────────────────
class KitsugiKotatsuContext(private val context: Context) : MangaLoaderContext() {

    private val parsers = java.util.concurrent.ConcurrentHashMap<org.koitharu.kotatsu.parsers.model.MangaSource, MangaParser>()
    fun register(parser: MangaParser) { parsers[parser.source] = parser }

    private val network = eu.kanade.tachiyomi.network.NetworkHelper(context)
    private val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val parser = chain.request().tag(org.koitharu.kotatsu.parsers.model.MangaSource::class.java)?.let { parsers[it] }
            if (parser != null) parser.intercept(chain) else chain.proceed(chain.request())
        }
        .build()

    override val httpClient: okhttp3.OkHttpClient get() = client
    override val cookieJar: okhttp3.CookieJar get() = network.cookieJar

    override fun getConfig(source: org.koitharu.kotatsu.parsers.model.MangaSource): org.koitharu.kotatsu.parsers.config.MangaSourceConfig {
        return KitsugiSourceConfig(context, source)
    }

    override suspend fun evaluateJs(script: String): String? =
        throw UnsupportedOperationException("Bu kaynak JavaScript çalıştırma desteği gerektiriyor")

    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? =
        throw UnsupportedOperationException("Bu kaynak JavaScript çalıştırma desteği gerektiriyor")

    override fun getDefaultUserAgent(): String = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    override fun encodeBase64(data: ByteArray): String {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
    }

    override fun decodeBase64(data: String): ByteArray {
        return android.util.Base64.decode(data, android.util.Base64.DEFAULT)
    }

    override fun getPreferredLocales(): List<java.util.Locale> {
        return listOf(java.util.Locale.getDefault())
    }

    override fun requestBrowserAction(parser: org.koitharu.kotatsu.parsers.MangaParser, url: String): Nothing {
        throw UnsupportedOperationException("Browser action required for ${parser.source.name} at $url")
    }

    override fun redrawImageResponse(response: okhttp3.Response, redraw: (image: org.koitharu.kotatsu.parsers.bitmap.Bitmap) -> org.koitharu.kotatsu.parsers.bitmap.Bitmap): okhttp3.Response {
        response.close()
        throw UnsupportedOperationException("Bu kaynak görsel yeniden çizim desteği gerektiriyor")
    }

    override fun createBitmap(width: Int, height: Int): org.koitharu.kotatsu.parsers.bitmap.Bitmap {
        return BitmapWrapper.create(width, height)
    }

    override suspend fun interceptWebViewRequests(
        url: String,
        interceptorScript: String,
        timeout: Long
    ): List<org.koitharu.kotatsu.parsers.webview.InterceptedRequest> =
        throw UnsupportedOperationException("Bu kaynak WebView istek yakalama desteği gerektiriyor")

    override suspend fun interceptWebViewRequests(
        url: String,
        config: org.koitharu.kotatsu.parsers.webview.InterceptionConfig
    ): List<org.koitharu.kotatsu.parsers.webview.InterceptedRequest> =
        throw UnsupportedOperationException("Bu kaynak WebView istek yakalama desteği gerektiriyor")

    override suspend fun captureWebViewUrls(
        pageUrl: String,
        urlPattern: Regex,
        timeout: Long
    ): List<String> =
        throw UnsupportedOperationException("Bu kaynak WebView URL yakalama desteği gerektiriyor")
}

// ─── KotatsuMangaSource — Gerçek Parser Wrapper ──────────────────────────────
class KotatsuMangaSource(
    val parserSource: MangaParserSource,
    private val parser: MangaParser,
    private val context: KitsugiKotatsuContext,
) : MangaSource {

    init { context.register(parser) }

    private fun <T> boundedCache(limit: Int): MutableMap<String, T> =
        java.util.Collections.synchronizedMap(object : LinkedHashMap<String, T>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>): Boolean = size > limit
        })
    private val mangaCache = boundedCache<KotatsuManga>(100)
    private val chapterCache = boundedCache<KotatsuChapter>(2000)
    private val pageCache = boundedCache<KotatsuPage>(1000)
    private val offsets = boundedCache<Int>(256)
    private fun offsetKey(query: String, page: Int) = "${query.length}:$query:$page"
    private fun offset(query: String, page: Int): Int =
        if (page <= 1) 0 else offsets[offsetKey(query, page)] ?: ((page - 1) * 20)

    private fun result(query: String, page: Int, list: List<KotatsuManga>): MangaSourceResult {
        offsets[offsetKey(query, page + 1)] = offset(query, page) + list.size
        return MangaSourceResult(list.map { it.toMangaDetails() }, list.isNotEmpty())
    }

    override val name: String get() = parserSource.title
    override val lang: String get() = parserSource.locale ?: "unknown"
    override val pkgName: String get() = "kotatsu.${parserSource.name.lowercase()}"
    override val baseUrl: String get() = parser.domain.let { if (it.startsWith("http")) it else "https://$it" }

    override suspend fun fetchPopularManga(page: Int): MangaSourceResult = withContext(Dispatchers.IO) {
        try {
            val list = parser.getList(
                offset = offset("", page),
                filter = MangaListFilter(query = ""),
                order = SortOrder.POPULARITY.takeIf { it in parser.availableSortOrders } ?: parser.availableSortOrders.first(),
            )
            result("", page, list)
        } catch (e: Exception) {
            android.util.Log.w("KotatsuMangaSrc", "fetchPopularManga hata [${parserSource.name}]: ${e.message}")
            throw e
        }
    }

    override suspend fun fetchSearchManga(page: Int, query: String): MangaSourceResult = withContext(Dispatchers.IO) {
        try {
            val list = parser.getList(
                offset = offset(query, page),
                filter = MangaListFilter(query = query),
                order = SortOrder.RELEVANCE.takeIf { it in parser.availableSortOrders } ?: parser.availableSortOrders.first(),
            )
            result(query, page, list)
        } catch (e: Exception) {
            android.util.Log.w("KotatsuMangaSrc", "fetchSearchManga hata [${parserSource.name}]: ${e.message}")
            throw e
        }
    }

    override suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails = withContext(Dispatchers.IO) {
        try {
            val stub = KotatsuManga(
                id = mangaUrl.hashCode().toLong(),
                title = name,
                altTitles = emptySet(),
                url = mangaUrl,
                publicUrl = mangaUrl,
                rating = 0f,
                contentRating = ContentRating.SAFE,
                coverUrl = "",
                tags = emptySet(),
                state = null,
                authors = emptySet(),
                largeCoverUrl = null,
                description = null,
                chapters = null,
                source = parserSource,
            )
            val detail = parser.getDetails(mangaCache[mangaUrl] ?: stub)
            mangaCache[mangaUrl] = detail
            detail.toMangaDetails()
        } catch (e: Exception) {
            android.util.Log.w("KotatsuMangaSrc", "fetchMangaDetails hata [${parserSource.name}]: ${e.message}")
            throw e
        }
    }

    override suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter> = withContext(Dispatchers.IO) {
        try {
            val stub = KotatsuManga(
                id = mangaUrl.hashCode().toLong(),
                title = name,
                altTitles = emptySet(),
                url = mangaUrl,
                publicUrl = mangaUrl,
                rating = 0f,
                contentRating = ContentRating.SAFE,
                coverUrl = "",
                tags = emptySet(),
                state = null,
                authors = emptySet(),
                largeCoverUrl = null,
                description = null,
                chapters = null,
                source = parserSource,
            )
            val detail = parser.getDetails(mangaCache[mangaUrl] ?: stub)
            mangaCache[mangaUrl] = detail
            detail.chapters?.asReversed()?.map { chapter ->
                chapterCache[chapter.url] = chapter
                MangaChapter(
                    url = chapter.url,
                    name = chapter.title?.takeIf { it.isNotBlank() } ?: KitsugiStrings.get(R.string.inline_episode_1_s_dc662be, chapter.number),
                    scanlator = chapter.scanlator,
                    chapterNumber = chapter.number,
                    uploadDate = chapter.uploadDate,
                    mangaUrl = mangaUrl,
                )
            } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.w("KotatsuMangaSrc", "fetchChapterList hata [${parserSource.name}]: ${e.message}")
            throw e
        }
    }

    override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        try {
            val kotatsuChapter = KotatsuChapter(
                id = chapter.url.hashCode().toLong(),
                title = chapter.name,
                number = chapter.chapterNumber,
                volume = 0,
                url = chapter.url,
                scanlator = chapter.scanlator,
                uploadDate = chapter.uploadDate,
                branch = null,
                source = parserSource,
            )
            val pages = parser.getPages(chapterCache[chapter.url] ?: kotatsuChapter)
            pages.mapIndexed { index, page ->
                pageCache[page.url] = page
                MangaPage(
                    index = index,
                    url = page.url,
                    imageUrl = null, // preview is only a thumbnail; always resolve getPageUrl.
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("KotatsuMangaSrc", "fetchPageList hata [${parserSource.name}]: ${e.message}")
            throw e
        }
    }

    override suspend fun fetchImageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
        try {
            val kotatsuPage = KotatsuPage(
                id = page.url.hashCode().toLong(),
                url = page.url,
                preview = page.imageUrl,
                source = parserSource,
            )
            parser.getPageUrl(pageCache[page.url] ?: kotatsuPage)
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getImage(page: MangaPage): java.io.InputStream {
        val imageUrl = page.imageUrl ?: throw IllegalArgumentException("Image URL is null")
        val headersBuilder = okhttp3.Headers.Builder()
        try {
            headersBuilder.addAll(parser.getRequestHeaders())
        } catch (_: Exception) {}

        if (headersBuilder["User-Agent"] == null) {
            headersBuilder["User-Agent"] = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        if (headersBuilder["Referer"] == null) {
            headersBuilder["Referer"] = baseUrl
        }

        val request = okhttp3.Request.Builder()
            .url(imageUrl)
            .headers(headersBuilder.build())
            .tag(org.koitharu.kotatsu.parsers.model.MangaSource::class.java, parserSource)
            .build()

        return withContext(Dispatchers.IO) {
            val response = context.httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw java.io.IOException("HTTP error ${response.code}$imageUrl")
            }
            val body = response.body ?: throw java.io.IOException("Response body is null")
            object : java.io.FilterInputStream(body.byteStream()) {
                override fun close() {
                    super.close()
                    response.close()
                }
            }
        }
    }

    override fun toString(): String = "KotatsuMangaSource[${parserSource.name}/$lang]"

    // ── Yardımcı dönüşüm ──────────────────────────────────────────────────────

    private fun KotatsuManga.toMangaDetails(): MangaDetails {
        mangaCache[url] = this
        return MangaDetails(
            url = url,
            title = title,
            author = authors.joinToString(", ").takeIf { it.isNotBlank() },
            description = description?.let { org.jsoup.Jsoup.parse(it).text() },
            genre = tags.map { it.title },
            status = when (state) {
                org.koitharu.kotatsu.parsers.model.MangaState.ONGOING -> MangaStatus.Ongoing
                org.koitharu.kotatsu.parsers.model.MangaState.FINISHED -> MangaStatus.Completed
                org.koitharu.kotatsu.parsers.model.MangaState.ABANDONED -> MangaStatus.Cancelled
                org.koitharu.kotatsu.parsers.model.MangaState.PAUSED -> MangaStatus.OnHiatus
                else -> MangaStatus.Unknown
            },
            thumbnailUrl = largeCoverUrl?.takeIf { it.isNotBlank() } ?: coverUrl?.takeIf { it.isNotBlank() },
            source = pkgName,
            alternativeTitles = altTitles.filter { it.isNotBlank() },
        )
    }
}

// ─── Eski KotatsuSourceType (geriye dönük uyumluluk) ─────────────────────────

enum class KotatsuSourceType {
    DIRECT_PARSER,
    KEIYOUSHI_WITH_DOMAIN,
}
