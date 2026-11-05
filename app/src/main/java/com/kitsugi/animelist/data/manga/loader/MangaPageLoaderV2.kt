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

    /** A URL alone is not a unique image: source interceptors may transform identical URLs. */
    fun cacheKey(page: MangaPage, imageUrl: String): String =
        "${source.pkgName.length}:${source.pkgName}:${source.name.length}:${source.name}:" +
            "${page.cacheContext.length}:${page.cacheContext}:${page.url.length}:${page.url}:${page.index}:$imageUrl"

    // ─── Sayfa listesi yükleme ────────────────────────────────────────────────

    suspend fun loadPageList(chapter: com.kitsugi.animelist.data.manga.MangaChapter) =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            try {
                resetQueue()
                _pages.value = emptyList()

                if (archiveFile != null) {
                    val archiveLoader = ArchivePageLoader(context, archiveFile)
                    val entryNames = archiveLoader.loadPageList()
                    if (entryNames.isEmpty()) throw java.io.IOException("Arşivde sayfa bulunamadı")
                    _pages.value = entryNames.mapIndexed { index, entryName ->
                        com.kitsugi.animelist.data.manga.MangaPage(
                            index = index,
                            url = entryName
                        ).apply {
                            archivePath = entryName
                            cacheContext = chapter.url
                        }
                    }
                    val elapsed = System.currentTimeMillis() - t0
                    sourceStateStore.recordOperationSuccess(source, "pages", elapsed)
                    MangaLogger.logPageList(context, source.name, chapter.name,
                        success = true, pageCount = entryNames.size, elapsedMs = elapsed)
                    Log.d(TAG, "${chapter.name}: ${entryNames.size} sayfa alındı (${elapsed}ms) [archive]")
                    return@withContext
                }

                val fetched = kotlinx.coroutines.withTimeoutOrNull(45_000L) {
                    source.fetchPageList(chapter)
                } ?: throw java.io.IOException("Bölüm sayfaları alınırken zaman aşımı")
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (fetched.isEmpty()) throw java.io.IOException("Kaynak bu bölüm için sayfa döndürmedi")
                fetched.forEach { it.cacheContext = chapter.url }
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

    fun retryPage(page: MangaPage, invalidateCache: Boolean = false) {
        synchronized(prefetchJobs) {
            val previous = prefetchJobs.remove(page.index)
            previous?.cancel()
            page.status = MangaPageStatus.Queue
            // Reserve the slot before suspension: prefetch must not reuse the bad file
            // while a retry waits for the previous download to stop and evicts it.
            val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                previous?.join()
                if (invalidateCache) page.imageUrl?.let { cache.removeImage(cacheKey(page, it)) }
                if (page.archivePath == null && !page.sourceProvidedImageUrl) {
                    // Refresh resolved URLs (e.g. expired signed CDN addresses).
                    page.imageUrl = null
                }
                loadPageInternal(page)
            }
            prefetchJobs[page.index] = job
            job.invokeOnCompletion { prefetchJobs.remove(page.index, job) }
            job.start()
        }
    }

    // ─── İç yükleme mantığı ───────────────────────────────────────────────────

    private suspend fun loadPageInternal(page: MangaPage) {
        if (page.status == MangaPageStatus.Ready) return

        if (page.archivePath != null && archiveFile != null) {
            try {
                val entry = page.archivePath!!
                val archiveKey = "archive:${archiveFile.absolutePath}:${archiveFile.lastModified()}:$entry"
                page.imageUrl = archiveKey
                val storedKey = cacheKey(page, archiveKey)
                if (!cache.isImageInCache(storedKey)) {
                    val stream = ArchivePageLoader(context, archiveFile).loadPage(entry)
                        ?: throw java.io.IOException("Arşiv sayfası okunamadı")
                    stream.use { cache.putImageToCache(storedKey, it) }
                }
                val file = cache.getImageFile(storedKey)
                page.aspectRatio = readAspectRatio(file)
                page.localFile = file
                page.cachedFile = file
                page.stream = { file.inputStream() }
                page.status = MangaPageStatus.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                page.status = MangaPageStatus.Error
                Log.e(TAG, "Archive page error [${page.index}]: ${page.archivePath}", e)
            }
            return
        }

        semaphore.withPermit {
            val t0 = System.currentTimeMillis()
            val timeoutMs = source.imageFetchTimeoutMs.coerceAtLeast(IMAGE_TIMEOUT_MS)
            try {
                kotlinx.coroutines.withTimeout(timeoutMs) {
                    // 1. Sayfa URL'ini çöz (gerekiyorsa)
                    if (page.imageUrl.isNullOrEmpty()) {
                        page.status = MangaPageStatus.LoadPage
                        page.imageUrl = source.fetchImageUrl(page)
                    }

                    val imageUrl = page.imageUrl?.takeIf { it.isNotBlank() }
                        ?: throw java.io.IOException("Kaynak boş görsel adresi döndürdü")

                    val storedKey = cacheKey(page, imageUrl)
                    // 2. Disk cache'te var mı kontrol et
                    if (cache.isImageInCache(storedKey)) {
                        val file = cache.getImageFile(storedKey)
                        page.aspectRatio = readAspectRatio(file)
                        page.localFile = file
                        page.cachedFile = file
                        page.stream = { file.inputStream() }
                        page.status = MangaPageStatus.Ready
                        Log.v(TAG, "Cache hit [${page.index}]")
                        return@withTimeout
                    }

                    // 3. İndir: Birincil yol eklentinin kendi source.getImage'idir
                    // (Referer, custom user-agent, Cloudflare çerezleri ve interceptor'ları içerir).
                    // Hotlink korumalı CDN'ler (manga-tr, trmanga, webtoonhatti vb.) için bu zorunludur.
                    page.status = MangaPageStatus.DownloadImage

                    source.getImage(page).use { stream ->
                        cache.putImageToCache(storedKey, stream)
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    val file = cache.getImageFile(storedKey)
                    if (file.exists() && file.length() > 0L) {
                        page.aspectRatio = readAspectRatio(file)
                        page.localFile = file
                        page.cachedFile = file
                        page.stream = { file.inputStream() }
                        page.status = MangaPageStatus.Ready

                        val elapsed = System.currentTimeMillis() - t0
                        sourceStateStore.recordOperationSuccess(source, "image", elapsed)
                        Log.v(TAG, "Sayfa hazır [${page.index}] (${elapsed}ms)")
                    } else {
                        throw java.io.IOException("Cache file not found or empty")
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                page.status = MangaPageStatus.Error
                MangaLogger.logImageFetch(context, source.name, page.index,
                    page.imageUrl, success = false, elapsedMs = timeoutMs, isTimeout = true)
                Log.e(TAG, "Timeout [${page.index}] — ${timeoutMs}ms aşıldı")
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (prefetchJobs[page.index] === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    page.status = MangaPageStatus.Queue
                }
                throw e
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - t0
                page.status = MangaPageStatus.Error
                sourceStateStore.recordOperationFailure(source, "image",
                    reason = e.javaClass.simpleName, statusOverride = classifyStatus(e), elapsedMs = elapsed)
                MangaLogger.logImageFetch(context, source.name, page.index,
                    page.imageUrl, success = false, elapsedMs = elapsed, error = e)
                Log.e(TAG, "Sayfa hatası [${page.index}]: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun readAspectRatio(file: java.io.File): Float? {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0)
            (options.outWidth.toFloat() / options.outHeight).coerceIn(0.02f, 100f) else null
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
