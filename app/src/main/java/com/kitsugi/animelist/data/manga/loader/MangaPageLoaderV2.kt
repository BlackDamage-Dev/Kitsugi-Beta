package com.kitsugi.animelist.data.manga.loader

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceStateStore
import com.kitsugi.animelist.data.manga.MangaLogger
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** Source-owned image requests, bounded prefetch and validated local disk cache. */
class MangaPageLoaderV2(
    private val context: Context,
    val source: MangaSource,
    val cache: MangaCache,
    var preloadAhead: Int = 3,
    var keepBehind: Int = 1,
    /** Yerel ZIP/CBZ arşiv dosyası — null ise normal HTTP scraping akışı devam eder. */
    val archiveFile: java.io.File? = null,
    val localDocumentFile: java.io.File? = null,
    /** HTML/XHTML kaynaklarını resim yerine seçilebilir metin olarak yükler. */
    val preferText: Boolean = false,
) {
    companion object {
        private const val TAG = "MangaPageLoaderV2"
        private const val PARALLEL_LIMIT = 4
        private const val IMAGE_TIMEOUT_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(PARALLEL_LIMIT)
    private val sourceStateStore = MangaSourceStateStore(context)

    // pageIndex → aktif prefetch job
    private val prefetchJobs = ConcurrentHashMap<Int, Job>()

    private val _pages = MutableStateFlow<List<MangaPage>>(emptyList())
    val pages: StateFlow<List<MangaPage>> = _pages.asStateFlow()

    // ─── Sayfa listesi yükleme ────────────────────────────────────────────────

    suspend fun loadPageList(chapter: com.kitsugi.animelist.data.manga.MangaChapter) =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            try {
                resetQueue()
                _pages.value = emptyList()

                if (localDocumentFile != null) {
                    _pages.value = when (localDocumentFile.extension.lowercase()) {
                        "epub" -> EpubPageLoader(localDocumentFile).loadPages(chapter.url)
                        "pdf" -> PdfPageLoader(context, localDocumentFile).let { pdf -> pdf.pages(chapter.url, pdf.pageCount()) }
                        "txt", "md", "markdown", "fb2" -> TextDocumentPageLoader(localDocumentFile).loadPages(chapter.url)
                        else -> throw java.io.IOException("Desteklenmeyen yerel belge")
                    }
                    if (_pages.value.isEmpty()) throw java.io.IOException("Belgede okunabilir sayfa bulunamadı")
                } else if (archiveFile != null) {
                    val archiveLoader = ArchivePageLoader(context, archiveFile)
                    _pages.value = archiveLoader.loadPages(chapter.url)
                    if (_pages.value.isEmpty()) throw java.io.IOException("Arşivde sayfa bulunamadı")
                    val elapsed = System.currentTimeMillis() - t0
                    sourceStateStore.recordOperationSuccess(source, "pages", elapsed)
                    MangaLogger.logPageList(context, source.name, chapter.name,
                        success = true, pageCount = _pages.value.size, elapsedMs = elapsed)
                    Log.d(TAG, "${chapter.name}: ${_pages.value.size} sayfa alındı (${elapsed}ms) [archive]")
                    return@withContext
                }

                val fetched = kotlinx.coroutines.withTimeoutOrNull(45_000L) {
                    source.fetchPageList(chapter)
                } ?: throw java.io.IOException("Bölüm sayfaları alınırken zaman aşımı")
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (fetched.isEmpty()) throw java.io.IOException("Kaynak bu bölüm için sayfa döndürmedi")
                _pages.value = fetched
                val elapsed = System.currentTimeMillis() - t0
                sourceStateStore.recordOperationSuccess(source, "pages", elapsed)
                MangaLogger.logPageList(context, source.name, chapter.name,
                    success = true, pageCount = fetched.size, elapsedMs = elapsed)
                Log.d(TAG, "${chapter.name}: ${fetched.size} sayfa alındı (${elapsed}ms)")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val elapsed = System.currentTimeMillis() - t0
                sourceStateStore.recordOperationFailure(source, "pages",
                    reason = e.message, statusOverride = classifyStatus(e), elapsedMs = elapsed)
                MangaLogger.logPageList(context, source.name, chapter.name,
                    success = false, elapsedMs = elapsed, error = e)
                Log.e(TAG, "${chapter.name}: sayfa listesi alınamadı → ${e.message}", e)
                _pages.value = emptyList()
                throw e
            }
        }

    // ─── Ana giriş noktası: sayfa değişince çağrılır ─────────────────────────

    /**
     * Okuyucu mevcut sayfayı değiştirince bu fonksiyon çağrılmalıdır.
     * Keep-alive penceresi: [currentIndex - keepBehind .. currentIndex + preloadAhead]
     * Bu pencere dışındaki tüm devam eden prefetch'ler iptal edilir.
     */
    fun onPageChanged(currentIndex: Int) {
        val allPages = _pages.value
        if (allPages.isEmpty()) return

        val windowStart = (currentIndex - keepBehind).coerceAtLeast(0)
        val windowEnd   = (currentIndex + preloadAhead).coerceAtMost(allPages.size - 1)

        // Pencere dışı işleri iptal et
        val outsideKeys = prefetchJobs.keys.filter { it < windowStart || it > windowEnd }
        outsideKeys.forEach { prefetchJobs.remove(it)?.cancel() }

        // Pencere içindeki sayfaları öncelikli sırayla prefetch et
        // Önce currentIndex, sonra +1, +2, +3, sonra -1
        val priority = buildList {
            add(currentIndex)
            for (i in 1..preloadAhead) {
                val next = currentIndex + i
                if (next <= windowEnd) add(next)
            }
            val prev = currentIndex - keepBehind
            if (prev >= windowStart) add(prev)
        }

        priority.forEach { idx -> allPages.getOrNull(idx)?.let(::loadPage) }
    }

    /** Register before starting, and only remove our own job on completion. */
    fun loadPage(page: MangaPage) {
        synchronized(prefetchJobs) {
            if (page.status == MangaPageStatus.Ready || prefetchJobs.containsKey(page.index)) return
            val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                loadPageInternal(page)
            }
            prefetchJobs[page.index] = job
            job.invokeOnCompletion { prefetchJobs.remove(page.index, job) }
            job.start()
        }
    }

    fun retryPage(page: MangaPage) {
        val previous = prefetchJobs.remove(page.index)
        previous?.cancel()
        scope.launch {
            previous?.join()
            page.status = MangaPageStatus.Queue
            loadPage(page)
        }
    }

    // ─── İç yükleme mantığı ───────────────────────────────────────────────────

    private suspend fun loadPageInternal(page: MangaPage) {
        if (page.status == MangaPageStatus.Ready) return

        if (page.textParagraphs != null) {
            page.status = MangaPageStatus.Ready
            return
        }

        if (localDocumentFile?.extension?.equals("pdf", true) == true && page.archivePath?.startsWith("pdf:") == true) {
            val index = page.archivePath!!.removePrefix("pdf:").toIntOrNull()
                ?: throw java.io.IOException("Geçersiz PDF sayfa kimliği")
            page.imageUrl = PdfPageLoader(context, localDocumentFile).renderPage(index).toURI().toString()
            page.status = MangaPageStatus.Ready
            return
        }

        if (page.archivePath != null && archiveFile != null) {
            val archiveLoader = ArchivePageLoader(context, archiveFile)
            val file = archiveLoader.extractPageToCache(page.archivePath!!)
            if (file != null) {
                page.imageUrl = file.toURI().toString()
                page.status = MangaPageStatus.Ready
                Log.v(TAG, "Archive page ready [${page.index}]: ${page.archivePath}")
                return
            }
            page.status = MangaPageStatus.Error
            Log.e(TAG, "Archive page error [${page.index}]: ${page.archivePath}")
            return
        }

        semaphore.withPermit {
            val t0 = System.currentTimeMillis()
            try {
                kotlinx.coroutines.withTimeout(IMAGE_TIMEOUT_MS) {
                    // 1. Sayfa URL'ini çöz (gerekiyorsa)
                    if (page.imageUrl.isNullOrEmpty()) {
                        page.status = MangaPageStatus.LoadPage
                        page.imageUrl = source.fetchImageUrl(page)
                    }

                    val imageUrl = page.imageUrl?.takeIf { it.isNotBlank() }
                        ?: throw java.io.IOException("Kaynak boş içerik adresi döndürdü")

                    if (preferText) {
                        page.status = MangaPageStatus.DownloadImage
                        val html = source.getImage(page).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val paragraphs = com.kitsugi.animelist.data.manga.MangaTextParser.parse(html)
                        if (paragraphs.isEmpty()) throw java.io.IOException("Bölümde okunabilir metin bulunamadı")
                        page.textParagraphs = paragraphs
                        page.status = MangaPageStatus.Ready
                        return@withTimeout
                    }

                    // 2. Disk cache'te var mı kontrol et
                    if (cache.isImageInCache(imageUrl)) {
                        page.stream = { cache.getImageFile(imageUrl).inputStream() }
                        page.status = MangaPageStatus.Ready
                        Log.v(TAG, "Cache hit [${page.index}]: $imageUrl")
                        return@withTimeout
                    }

                    // 3. İndir: Birincil yol eklentinin kendi source.getImage'idir
                    // (Referer, custom user-agent, Cloudflare çerezleri ve interceptor'ları içerir).
                    // Hotlink korumalı CDN'ler (manga-tr, trmanga, webtoonhatti vb.) için bu zorunludur.
                    page.status = MangaPageStatus.DownloadImage

                    source.getImage(page).use { stream ->
                        cache.putImageToCache(imageUrl, stream)
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    val file = cache.getImageFile(imageUrl)
                    if (file.exists() && file.length() > 0L) {
                        page.stream = { file.inputStream() }
                        page.status = MangaPageStatus.Ready

                        val elapsed = System.currentTimeMillis() - t0
                        sourceStateStore.recordOperationSuccess(source, "image", elapsed)
                        Log.v(TAG, "Sayfa hazır [${page.index}] (${elapsed}ms): $imageUrl")
                    } else {
                        throw java.io.IOException("Cache file not found or empty for $imageUrl")
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                page.status = MangaPageStatus.Error
                MangaLogger.logImageFetch(context, source.name, page.index,
                    page.imageUrl, success = false, elapsedMs = IMAGE_TIMEOUT_MS, isTimeout = true)
                Log.e(TAG, "Timeout [${page.index}] — ${IMAGE_TIMEOUT_MS}ms aşıldı")
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (prefetchJobs[page.index] === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    page.status = MangaPageStatus.Queue
                }
                throw e
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - t0
                page.status = MangaPageStatus.Error
                sourceStateStore.recordOperationFailure(source, "image",
                    reason = e.message, statusOverride = classifyStatus(e), elapsedMs = elapsed)
                MangaLogger.logImageFetch(context, source.name, page.index,
                    page.imageUrl, success = false, elapsedMs = elapsed, error = e)
                Log.e(TAG, "Sayfa hatası [${page.index}]: ${e.message}")
            }
        }
    }

    // ─── Yardımcı fonksiyonlar ────────────────────────────────────────────────

    fun resetQueue() {
        prefetchJobs.values.forEach { it.cancel() }
        prefetchJobs.clear()
        Log.d(TAG, "Queue sıfırlandı (bölüm değişimi)")
    }

    fun recycle() {
        scope.cancel()
        prefetchJobs.clear()
        Log.d(TAG, "MangaPageLoaderV2 kapatıldı")
    }

    private fun classifyStatus(error: Throwable): SourceHealthStatus =
        SourceFailureClassifier.classifyStatus(error)
}
