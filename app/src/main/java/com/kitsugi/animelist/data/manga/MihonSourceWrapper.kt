package com.kitsugi.animelist.data.manga

import android.content.Context
import com.kitsugi.animelist.data.manga.engines.InertiaMangaEngine
import com.kitsugi.animelist.data.manga.engines.SvelteMangaEngine
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import android.util.Log
import tachiyomi.core.common.util.lang.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * MihonSourceWrapper
 *
 * Bir Mihon/Tachiyomi APK'sından yüklenen eu.kanade.tachiyomi.source.Source nesnesini,
 * Kitsugi'nun yerel MangaSource interface'ine adapte eden köprü sınıfı.
 *
 * Bu sayede Keiyoushi gibi repolardan indirilen binlerce eklenti,
 * hiçbir değişiklik yapılmadan Kitsugi içinde çalışabilir.
 *
 * @param mihonSource Mihon APK'dan PathClassLoader ile yüklenmiş Source nesnesi
 * @param context Android Context (gerekli olduğu durumlarda)
 * @param pkgName Kaynak paket adı
 */
class MihonSourceWrapper(
    private val mihonSource: Source,
    private val context: Context,
    override val pkgName: String,
    override val engineType: ExtensionEngine = ExtensionEngine.UNKNOWN,
) : MangaSource {

    private val TAG = "MihonSourceWrapper"
    private val httpSource: HttpSource? get() = mihonSource as? HttpSource
    private val sourceConfigStore = SourceConfigStore(context)
    private val mirrorResolver = SourceMirrorResolver(context, sourceConfigStore)
    // KeiSource rejects concurrent updates for the same manga URL. Use bounded
    // striped locks so detail and chapter requests cannot collide or leak keys.
    private val mangaUpdateLocks = Array(32) { Mutex() }
    private fun updateLock(url: String): Mutex = mangaUpdateLocks[(url.hashCode() and Int.MAX_VALUE) % mangaUpdateLocks.size]

    // ── Lazy Fallback Engine'ler ───────────────────────────────────────────────

    private val networkClient by lazy {
        try {
            uy.kohesive.injekt.Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java).client
        } catch (_: Exception) {
            okhttp3.OkHttpClient.Builder().build()
        }
    }

    private val svelteEngine: SvelteMangaEngine? by lazy {
        if (engineType == ExtensionEngine.SVELTE) {
            val cdn = SvelteMangaEngine.cdnUrlFromPkg(pkgName)
            SvelteMangaEngine(rawBaseUrl(), cdn, networkClient)
        } else null
    }

    private val inertiaEngine: InertiaMangaEngine? by lazy {
        if (engineType == ExtensionEngine.INERTIA) {
            InertiaMangaEngine(rawBaseUrl(), networkClient)
        } else null
    }

    override val name: String get() = mihonSource.name
    override val lang: String get() = mihonSource.lang

    override val originalBaseUrl: String get() = rawBaseUrl()

    /** Read the public contract; subclasses need not declare a baseUrl field. */
    override val baseUrl: String
        get() = sourceConfigStore.getPreferredBaseUrl(this, rawBaseUrl()) ?: rawBaseUrl()

    private fun rawBaseUrl(): String = httpSource?.baseUrl.orEmpty()

    private fun applyRuntimeSourceConfig() {
        runCatching {
            mirrorResolver.applyStoredDomainPreference(mihonSource.id, this)
        }
    }

    private suspend fun maybeSlowdown() {
        if (sourceConfigStore.getSlowdownEnabled(this)) {
            kotlinx.coroutines.delay(350L)
        }
    }

    private suspend fun <T> withRecovery(block: suspend () -> T): T {
        applyRuntimeSourceConfig()
        maybeSlowdown()
        return try {
            block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val switched = mirrorResolver.tryResolveAndActivateMirror(this, e)
            if (!switched) throw e
            applyRuntimeSourceConfig()
            maybeSlowdown()
            block()
        }
    }

    // ── Popüler Manga ────────────────────────────────────────────────────────

    override suspend fun fetchPopularManga(page: Int): MangaSourceResult = withRecovery {
        val t0 = System.currentTimeMillis()
        try {
            val pageResult = mihonSource.getPopularManga(page)
            val result = MangaSourceResult(
                mangas = pageResult.mangas.map { it.toMangaDetails() },
                hasNextPage = pageResult.hasNextPage
            )
            MangaLogger.logPopular(context, mihonSource.name, success = true,
                resultCount = result.mangas.size, elapsedMs = System.currentTimeMillis() - t0)
            result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "${mihonSource.name} APK popular başarısız (engine=$engineType): ${e.message}")
            val fallback = when (engineType) {
                ExtensionEngine.SVELTE  -> svelteEngine?.fetchPopularManga(page)
                ExtensionEngine.INERTIA -> inertiaEngine?.fetchPopularManga(page)
                else -> null
            }
            if (fallback != null) {
                MangaLogger.logPopular(context, mihonSource.name, success = true,
                    resultCount = fallback.mangas.size, elapsedMs = System.currentTimeMillis() - t0)
                fallback
            } else {
                MangaLogger.logPopular(context, mihonSource.name, success = false,
                    elapsedMs = System.currentTimeMillis() - t0, error = e)
                throw e
            }
        }
    }

    // ── Arama ────────────────────────────────────────────────────────────────

    override suspend fun fetchSearchManga(page: Int, query: String): MangaSourceResult = withRecovery {
        val t0 = System.currentTimeMillis()
        try {
            val raw = mihonSource.getSearchManga(page, query, FilterList())
            val result = MangaSourceResult(
                mangas = raw.mangas.map { it.toMangaDetails() },
                hasNextPage = raw.hasNextPage
            )
            MangaLogger.logSearch(context, mihonSource.name, query, success = true,
                resultCount = result.mangas.size, elapsedMs = System.currentTimeMillis() - t0)
            result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "${mihonSource.name} APK search başarısız (engine=$engineType): ${e.message}")
            val fallback = when (engineType) {
                ExtensionEngine.SVELTE  -> svelteEngine?.searchManga(query, page)
                ExtensionEngine.INERTIA -> inertiaEngine?.searchManga(query, page)
                else -> null
            }
            if (fallback != null) {
                MangaLogger.logSearch(context, mihonSource.name, query, success = true,
                    resultCount = fallback.mangas.size, elapsedMs = System.currentTimeMillis() - t0)
                fallback
            } else {
                MangaLogger.logSearch(context, mihonSource.name, query, success = false,
                    elapsedMs = System.currentTimeMillis() - t0, error = e)
                throw e
            }
        }
    }

    // ── Manga Detayları ───────────────────────────────────────────────────────

    override suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails {
        val stub = SManga.create().apply { url = mangaUrl; title = "" }
        val t0 = System.currentTimeMillis()
        return try {
            withRecovery {
                // extensionLib 1.6+: getMangaUpdate (suspend API) — KeiSource/Madara/Themesia'nın tek desteklediği yol.
                // extensionLib 1.4: getMangaUpdate'in CatalogueSource varsayılan impl'i fetchMangaDetails().awaitSingle()'a köprüler.
                val update = updateLock(mangaUrl).withLock {
                    kotlinx.coroutines.withTimeout(20_000L) {
                        mihonSource.getMangaUpdate(
                            manga        = stub,
                            chapters     = emptyList(),
                            fetchDetails = true,
                            fetchChapters = false
                        )
                    }
                }
                // Manga-TR's update parser creates a fresh SManga without assigning
                // its lateinit url. Preserve the URL used for this exact request.
                val details = update.manga.toMangaDetails(fallbackUrl = mangaUrl)
                MangaLogger.logMangaDetails(context, mihonSource.name, mangaUrl, success = true)
                details
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "${mihonSource.name} APK detay başarısız (engine=$engineType): ${e.message}")
            try {
                val fallback = when (engineType) {
                    ExtensionEngine.SVELTE  -> svelteEngine?.fetchMangaDetails(mangaUrl)
                    ExtensionEngine.INERTIA -> inertiaEngine?.fetchMangaDetails(mangaUrl)
                    else -> null
                }
                if (fallback != null) {
                    MangaLogger.logMangaDetails(context, mihonSource.name, mangaUrl, success = true)
                    fallback
                } else {
                    MangaLogger.logMangaDetails(context, mihonSource.name, mangaUrl, success = false, error = e)
                    throw e
                }
            } catch (fe: Exception) {
                if (fe is kotlinx.coroutines.CancellationException) throw fe
                Log.e(TAG, "${mihonSource.name} fallback detay da başarısız: ${fe.message}")
                MangaLogger.logMangaDetails(context, mihonSource.name, mangaUrl, success = false, error = fe)
                throw fe
            }
        }
    }

    // ── Bölüm Listesi ─────────────────────────────────────────────────────────

    override suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter> {
        val t0 = System.currentTimeMillis()
        return try {
            withRecovery {
                val stub = SManga.create().apply { url = mangaUrl; title = "" }

                // extensionLib 1.6+: getMangaUpdate ile hem detay hem bölüm tek seferde.
                // fetchDetails=true alarak mangaTitle'ı da çekiyoruz (ChapterRecognition için).
                val update = updateLock(mangaUrl).withLock {
                    kotlinx.coroutines.withTimeout(180_000L) {
                        mihonSource.getMangaUpdate(
                            manga         = stub,
                            chapters      = emptyList(),
                            fetchDetails  = true,
                            fetchChapters = true
                        )
                    }
                }

                val mangaTitle = update.manga.title.takeIf { it.isNotBlank() && !it.startsWith("/") } ?: ""
                val rawChapters = update.chapters

                Log.d(TAG, "${mihonSource.name}: ${rawChapters.size} bölüm alındı — $mangaUrl")
                val mapped = rawChapters.distinctBy { it.url }.map { it.toMangaChapter(mangaUrl, mangaTitle) }
                    .sortedWith(
                        compareByDescending<MangaChapter> {
                            if (it.chapterNumber >= 0f) it.chapterNumber else Float.NEGATIVE_INFINITY
                        }
                    )
                MangaLogger.logChapterList(context, mihonSource.name, mangaUrl, success = true,
                    chapterCount = mapped.size, elapsedMs = System.currentTimeMillis() - t0)
                mapped
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "${mihonSource.name} fetchChapterList hata: ${e.message}")
            MangaLogger.logChapterList(context, mihonSource.name, mangaUrl, success = false,
                elapsedMs = System.currentTimeMillis() - t0, error = e)
            // ONEMLI: Hatayi yutup emptyList() DONDURMUYORUZ.
            //
            // Eski davranis iki somut zarara yol aciyordu:
            //  1) MangaReaderViewModel.loadChapterList exception gormedigi icin
            //     recordOperationSuccess(...) cagiriyordu -> kaynak saglik istatistigi
            //     bozuk kaynagi "basarili" diye kaydediyordu.
            //  2) Kullanici bos bolum listesi goruyordu ("bos ekran"); hata mesaji yerine
            //     "bolum bulunamadi" anlami cikiyordu.
            //
            // Cagiranlarin hepsi (MangaDetailViewModel.loadChapters,
            // MangaReaderViewModel.loadChapterList, SourceHealthService.evaluate)
            // try/catch icinde; exception'i yuzeye cikarmak yeni cokme yuzeyi acmaz.
            throw e
        }
    }

    // ── Sayfa Listesi ─────────────────────────────────────────────────────────

    // getPageList() — suspend API üzerinden çağırır.
    // CatalogueSource.getPageList() → fetchPageList().awaitSingle() köprüsü devreye girer,
    // bu da HttpSource.fetchPageList() → HTTP isteği + pageListParse() zincirini tetikler.
    override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> {
        val sChapter = SChapter.create().apply {
            url = chapter.url
            name = chapter.name
            chapter_number = chapter.chapterNumber
            scanlator = chapter.scanlator
        }
        val t0 = System.currentTimeMillis()
        return try {
            withRecovery {
                val pages = mihonSource.getPageList(sChapter).mapIndexed { index, page ->
                    MangaPage(
                        index = index,
                        url = page.url,
                        imageUrl = page.imageUrl
                    )
                }
                MangaLogger.logPageList(context, mihonSource.name, chapter.name, success = true,
                    pageCount = pages.size, elapsedMs = System.currentTimeMillis() - t0)
                pages
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = e.message?.lowercase() ?: ""
            if (isMangaTrLoginRequired(pkgName, e.message)) throw e
            val isCaptcha = msg.contains("captcha") || msg.contains("webview") ||
                msg.contains("doğrula") || msg.contains("verify") || msg.contains("challenge")
            if (isCaptcha) {
                Log.w(TAG, "${mihonSource.name}: Captcha/WebView engeli tespit edildi")
                MangaLogger.logCaptchaDetected(context, mihonSource.name, "fetchPageList", chapter.name)
                throw CaptchaRequiredException(mihonSource.name, e.message ?: "Captcha gerekli")
            }
            Log.e(TAG, "${mihonSource.name} fetchPageList hata: ${e.message}")
            MangaLogger.logPageList(context, mihonSource.name, chapter.name, success = false,
                elapsedMs = System.currentTimeMillis() - t0, error = e)
            throw e
        }
    }

    // ── Resim URL Çözümleme ───────────────────────────────────────────────────

    override suspend fun fetchImageUrl(page: MangaPage): String {
        if (!page.imageUrl.isNullOrBlank()) return page.imageUrl!!

        // HttpSource.getImageUrl bir `suspend` fonksiyondur; reflection (getMethod)
        // ile bulunamaz çünkü derlenmiş imzası getImageUrl(Page, Continuation) olur.
        // Bu yüzden kaynağı HttpSource'a cast edip DOĞRUDAN çağırıyoruz.
        val mihonPage = eu.kanade.tachiyomi.source.model.Page(
            index = page.index,
            url = page.url,
            imageUrl = page.imageUrl
        )
        return try {
            withRecovery {
                val httpSource = mihonSource as? eu.kanade.tachiyomi.source.online.HttpSource
                httpSource?.getImageUrl(mihonPage) ?: page.url
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getImage(page: MangaPage): java.io.InputStream {
        try {
            applyRuntimeSourceConfig()
            maybeSlowdown()
            val source = httpSource ?: return super<MangaSource>.getImage(page)
            if (pkgName.endsWith(".mangatr", ignoreCase = true) &&
                isInvalidMangaTrImageOrder(page.imageUrl?.toHttpUrlOrNull()?.fragment)
            ) {
                throw java.io.IOException("Eklenti geçersiz görsel parça sırası döndürdü; sayfa işlenemiyor")
            }
            // Do not rebuild the request/client: imageRequest overrides can carry chapter
            // referers, signed URLs, decryption interceptors and the source cookie jar.
            val response = source.getImage(eu.kanade.tachiyomi.source.model.Page(
                index = page.index, url = page.url, imageUrl = page.imageUrl
            ))
            val body = response.body
            if (!response.isSuccessful || body == null) {
                response.close()
                throw java.io.IOException("Görsel isteği başarısız: HTTP ${response.code}")
            }
            return object : java.io.FilterInputStream(body.byteStream()) {
                override fun close() {
                    try { super.close() } finally { response.close() }
                }
            }
        } catch (e: LinkageError) {
            throw java.io.IOException("Eklenti çalışma zamanı uyumsuzluğu: ${e.javaClass.simpleName}: ${e.message.orEmpty()}", e)
        }
    }

    // ── Dönüşüm Yardımcıları ──────────────────────────────────────────────────

    private fun SManga.toMangaDetails(fallbackUrl: String? = null): MangaDetails = MangaDetails(
        url = try {
            url.takeIf { it.isNotBlank() } ?: fallbackUrl.orEmpty()
        } catch (e: UninitializedPropertyAccessException) {
            fallbackUrl ?: throw e
        },
        title = title,
        author = author,
        artist = artist,
        description = description?.let { org.jsoup.Jsoup.parse(it).text() },
        genre = getGenres() ?: emptyList(),
        thumbnailUrl = thumbnail_url,
        status = when (status) {
            SManga.ONGOING -> MangaStatus.Ongoing
            SManga.COMPLETED -> MangaStatus.Completed
            SManga.LICENSED -> MangaStatus.Licensed
            SManga.PUBLISHING_FINISHED -> MangaStatus.PublicationComplete
            SManga.CANCELLED -> MangaStatus.Cancelled
            SManga.ON_HIATUS -> MangaStatus.OnHiatus
            else -> MangaStatus.Unknown
        },
        source = name
    )

    private fun SChapter.toMangaChapter(mangaUrl: String, mangaTitle: String = ""): MangaChapter {
        // Kaynak geçerli bir chapter_number verdiyse onu kullan; aksi halde
        // (çoğu HTML kaynağı -1 döndürür) bölüm adından ChapterRecognition ile ayıkla.
        val resolvedNumber: Float = if (chapter_number >= 0f) {
            chapter_number
        } else {
            ChapterRecognition.parseChapterNumber(
                mangaTitle = mangaTitle,
                chapterName = name,
                chapterNumber = chapter_number.toDouble()
            ).toFloat()
        }
        return MangaChapter(
            url = url,
            name = name,
            chapterNumber = resolvedNumber,
            scanlator = scanlator,
            uploadDate = date_upload,
            mangaUrl = mangaUrl
        )
    }
}
