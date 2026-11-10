package com.kitsugi.animelist.data.manga.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.screens.offline.DownloadsActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException

class MangaDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString("id") ?: return@withContext Result.failure()
        val manager = MangaDownloadManager.get(applicationContext)
        val store = manager.store
        manager.chapterLock(downloadId).withLock {
            var record = store.get(downloadId) ?: return@withLock Result.failure()
            if (record.workId != id.toString() || record.status == MangaDownloadStatus.Paused) return@withLock Result.failure()
            val temp = File(applicationContext.cacheDir, "manga_export/$downloadId")
            try {
                setForeground(notification(record))
                slots.withPermit {
                    temp.mkdirs()
                    record = store.update(downloadId) { it.copy(status = MangaDownloadStatus.Downloading, error = null) }
                    val source = manager.resolve(record)
                    // Populate source-specific chapter caches/memo after process restart where needed.
                    val chapter = withTimeoutOrNull(60_000) {
                        source.fetchChapterList(record.mangaUrl).firstOrNull { it.url == record.chapterUrl } ?: record.chapter()
                    } ?: throw IOException("Bölüm bilgisi alınırken zaman aşımı")
                    val pages = withTimeoutOrNull(60_000) { source.fetchPageList(chapter) }
                        ?: throw IOException("Sayfa listesi alınırken zaman aşımı")
                    if (pages.isEmpty()) throw IOException("Eklenti bölüm için sayfa döndürmedi")
                    if (record.totalPages != 0 && record.totalPages != pages.size && record.pages.isNotEmpty()) {
                        error("Kaynağın sayfa sayısı değişti. Bölümü silip yeniden indirin; eski ve yeni sayfalar karıştırılmadı.")
                    }
                    record = store.update(downloadId) { it.copy(totalPages = pages.size) }
                    // Use LIST order, never filename/URL sorting or completion order of requests.
                    pages.forEachIndexed { index, page ->
                        currentCoroutineContext().ensureActive()
                        val latest = store.get(downloadId) ?: throw CancellationException()
                        if (latest.status == MangaDownloadStatus.Paused || latest.workId != id.toString()) throw CancellationException()
                        val saved = latest.pages.firstOrNull { it.index == index }
                        if (saved != null && MangaPageExport.readable(applicationContext, saved)) return@forEachIndexed
                        val input = File(temp, "page.input")
                        val converted = File(temp, "page.png")
                        try {
                            if (temp.usableSpace < 160L * 1024 * 1024) throw IOException("Manga indirmek için en az 160 MB boş çalışma alanı gerekli")
                            withTimeoutOrNull(90_000) {
                                if (page.imageUrl.isNullOrBlank()) page.imageUrl = source.fetchImageUrl(page)
                                if (page.imageUrl.isNullOrBlank()) throw IOException("Eklenti boş görsel adresi döndürdü")
                                source.getImage(page).use { MangaPageExport.receive(it, input) }
                                true
                            } ?: throw IOException("Görsel indirilirken zaman aşımı")
                            val image = MangaPageExport.normalize(input, converted)
                            currentCoroutineContext().ensureActive()
                            if (saved != null) MangaPageExport.delete(applicationContext, saved)
                            val exported = MangaPageExport.save(applicationContext, record, index, image) { pending ->
                                // Pending URI survives cancellation, process death and partial writes.
                                store.update(downloadId) { it.copy(pages = it.pages.filterNot { p -> p.index == index } + pending) }
                            }
                            record = store.update(downloadId) {
                                it.copy(pages = (it.pages.filterNot { p -> p.index == index } + exported).sortedBy { p -> p.index })
                            }
                            setForeground(notification(record))
                        } finally { input.delete(); converted.delete() }
                    }
                    currentCoroutineContext().ensureActive()
                    record = store.get(downloadId) ?: throw CancellationException()
                    if (record.status == MangaDownloadStatus.Paused) throw CancellationException()
                    check(record.hasAllPages && record.pages.all { MangaPageExport.readable(applicationContext, it) }) {
                        "Bazı sayfalar eksik; bölüm tamamlandı olarak işaretlenmedi"
                    }
                    store.update(downloadId) { if (it.status == MangaDownloadStatus.Paused) it else it.copy(status = MangaDownloadStatus.Complete, error = null) }
                }
                Result.success()
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                failOrRetry(store, downloadId, IOException("Kaynak isteği zaman aşımına uğradı", e))
            } catch (e: CancellationException) {
                throw e // WorkManager/explicit pause decides state; never turn cancellation into success.
            } catch (e: Exception) {
                failOrRetry(store, downloadId, e)
            } finally { temp.deleteRecursively() }
        }
    }

    private suspend fun failOrRetry(store: MangaDownloadStore, downloadId: String, error: Exception): Result {
        val retry = error is IOException && runAttemptCount < 2
        store.update(downloadId) { old ->
            if (old.status == MangaDownloadStatus.Paused) old
            else old.copy(status = if (retry) MangaDownloadStatus.Queued else MangaDownloadStatus.Error,
                error = (error.message ?: "Manga indirilemedi").take(350))
        }
        return if (retry) Result.retry() else Result.failure()
    }

    private fun notification(record: MangaDownload): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.manga_download_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, record.id.hashCode(),
            Intent(context, DownloadsActivity::class.java).putExtra("manga_tab", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(record.mangaTitle)
            .setContentText("${record.chapterName} · ${record.completedPages}/${record.totalPages}")
            .setProgress(record.totalPages, record.completedPages, record.totalPages == 0)
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.manga_download_pause),
                WorkManager.getInstance(context).createCancelPendingIntent(id)).build()
        val notificationId = 100_000 + (record.id.hashCode() and 0x0fffffff)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }

    companion object {
        private const val CHANNEL = "manga_downloads"
        private val slots = Semaphore(2) // At most two chapters; pages within each chapter stay sequential.
    }
}
