package com.kitsugi.animelist.data.manga.download

import android.content.Context
import androidx.work.*
import com.kitsugi.animelist.data.manga.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** WorkManager owns lifetime/restarts; this object only coordinates manifests and UI actions. */
class MangaDownloadManager private constructor(private val context: Context) {
    val store = MangaDownloadStore.get(context)
    private val work = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
        android.util.Log.e("MangaDownloadManager", "İndirme durumu izlenemedi", error)
    })
    private val sources = ConcurrentHashMap<String, MangaSource>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val commands = Mutex()
    internal fun chapterLock(id: String) = locks.getOrPut(id) { Mutex() }

    init {
        scope.launch {
            store.load()
            // Includes cancellations performed from the foreground notification/system scheduler.
            work.getWorkInfosByTagFlow(TAG).collect { infos ->
                infos.filter { it.state.isFinished }.forEach { info ->
                    val record = store.downloads.value.firstOrNull { it.workId == info.id.toString() } ?: return@forEach
                    if (record.status in setOf(MangaDownloadStatus.Queued, MangaDownloadStatus.Downloading)) {
                        store.update(record.id) { old ->
                            if (old.workId != record.workId || old.status !in setOf(MangaDownloadStatus.Queued, MangaDownloadStatus.Downloading)) old
                            else old.copy(status = if (info.state == WorkInfo.State.CANCELLED) MangaDownloadStatus.Paused else MangaDownloadStatus.Error,
                                error = if (info.state == WorkInfo.State.CANCELLED) null else "İndirme tamamlanamadı; yeniden deneyin.")
                        }
                    }
                }
            }
        }
    }

    suspend fun enqueue(source: MangaSource, details: MangaDetails, chapter: MangaChapter) = withContext(Dispatchers.IO) {
        val key = source.stableSourceKey()
        val id = MangaDownloadNaming.id(key, source.name, source.lang, details.url, chapter.url)
        sources[id] = source
        commands.withLock {
            val old = store.get(id)
            if (old?.status == MangaDownloadStatus.Complete && old.hasAllPages && old.pages.all { MangaPageExport.readable(context, it) }) return@withLock
            if (work.getWorkInfosForUniqueWork(workName(id)).get().any { !it.state.isFinished }) return@withLock
            val record = old ?: MangaDownload(
                id, key, source.name, source.lang, details.url, details.title, chapter.url,
                chapter.name, chapter.chapterNumber.takeIf { it.isFinite() } ?: -1f, chapter.scanlator,
                MangaDownloadNaming.relativePath("$key\u0000${source.lang}", source.name, details.title, details.url, chapter)
            )
            schedule(record)
        }
        id
    }

    suspend fun resume(id: String) = withContext(Dispatchers.IO) {
        commands.withLock {
            val record = store.get(id) ?: return@withLock
            if (record.status == MangaDownloadStatus.Complete && record.hasAllPages && record.pages.all { MangaPageExport.readable(context, it) }) return@withLock
            if (work.getWorkInfosForUniqueWork(workName(id)).get().any { !it.state.isFinished }) return@withLock
            schedule(record)
        }
    }

    private suspend fun schedule(record: MangaDownload) {
        val request = OneTimeWorkRequestBuilder<MangaDownloadWorker>()
            .setInputData(workDataOf("id" to record.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG).build()
        store.put(record.copy(workId = request.id.toString(), status = MangaDownloadStatus.Queued, error = null))
        try {
            work.enqueueUniqueWork(workName(record.id), ExistingWorkPolicy.REPLACE, request).result.get()
        } catch (e: Exception) {
            store.update(record.id) { it.copy(status = MangaDownloadStatus.Error, error = "İndirme kuyruğu başlatılamadı: ${e.message}") }
            throw e
        }
    }

    suspend fun pause(id: String) = withContext(Dispatchers.IO) {
        commands.withLock {
            store.update(id) { it.copy(status = MangaDownloadStatus.Paused, error = null) }
            work.cancelUniqueWork(workName(id)).result.get()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        commands.withLock {
            val record = store.get(id) ?: return@withLock
            store.update(id) { it.copy(status = MangaDownloadStatus.Paused) }
            work.cancelUniqueWork(workName(id)).result.get()
            // Do not delete files while a canceled worker still owns an output stream.
            chapterLock(id).withLock {
                val current = store.get(id) ?: record
                val remaining = current.pages.toMutableList()
                try {
                    current.pages.forEach { page ->
                        MangaPageExport.delete(context, page)
                        remaining.remove(page)
                    }
                    MangaPageExport.deleteUnjournaledRows(context, current)
                    store.remove(id)
                } catch (e: Exception) {
                    store.update(id) { it.copy(pages = remaining, status = MangaDownloadStatus.Error,
                        error = "Bazı manga dosyaları silinemedi; izinleri kontrol edin: ${e.message}") }
                    throw e
                }
            }
        }
    }

    internal suspend fun resolve(record: MangaDownload): MangaSource {
        sources[record.id]?.let { return it }
        MangaExtensionLoader.loadAllExtensions(context)
        KotatsuExtensionAdapter.initializeLazy(context)
        // Initialization may be lazy after process death. Include inactive language parsers, too.
        val source = withTimeoutOrNull(20_000L) {
            var found: MangaSource? = null
            while (found == null) {
                found = (MangaExtensionLoader.getLoadedSources() + KotatsuExtensionAdapter.getLoadedSources()).firstOrNull {
                    it.stableSourceKey() == record.sourceKey && it.name == record.sourceName && it.lang == record.sourceLang
                }
                if (found == null) delay(250)
            }
            found
        } ?: error("${record.sourceName} eklentisi bulunamadı. Eklentiyi yükleyip tekrar deneyin.")
        sources[record.id] = source
        return source
    }

    companion object {
        const val TAG = "kitsugi-manga-download"
        private fun workName(id: String) = "manga-download-$id"
        @Volatile private var instance: MangaDownloadManager? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: MangaDownloadManager(context.applicationContext).also { instance = it }
        }
    }
}
