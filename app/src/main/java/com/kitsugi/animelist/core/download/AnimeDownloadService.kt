package com.kitsugi.animelist.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.local.AnimeDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URL
import com.kitsugi.animelist.utils.KitsugiStrings

class AnimeDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null
    private var activeDownloadJob: Job? = null
    private var currentDownloadingAnimeId: String? = null
    private var currentDownloadingEpisode: Int? = null
    private lateinit var downloader: AnimeDownloader
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val CHANNEL_ID    = "anime_downloads_channel"
        private const val NOTIFICATION_ID = 10002

        /** Fetch the poster bitmap synchronously (call from IO thread), downscaled for notifications. */
        fun loadPosterBitmap(url: String?): Bitmap? {
            if (url.isNullOrBlank()) return null
            return runCatching {
                val connection = URL(url).openConnection().also {
                    it.connectTimeout = 5_000
                    it.readTimeout    = 5_000
                }
                val raw = connection.inputStream.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
                val maxDim = 384
                if (raw.width > maxDim || raw.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(raw.width, raw.height).toFloat()
                    val targetW = (raw.width * scale).toInt().coerceAtLeast(1)
                    val targetH = (raw.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
                    if (scaled !== raw) raw.recycle()
                    scaled
                } else {
                    raw
                }
            }.getOrNull()
        }
    }

    override fun onCreate() {
        super.onCreate()
        downloader = AnimeDownloader(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        serviceScope.launch {
            AnimeDownloadManager.downloads.collect { list ->
                val activeId = currentDownloadingAnimeId
                val activeEp = currentDownloadingEpisode
                if (activeId != null && activeEp != null) {
                    val stillExistsAndDownloading = list.any {
                        it.animeId == activeId &&
                        it.episode == activeEp &&
                        (it.status == AnimeDownload.Status.DOWNLOADING || it.status == AnimeDownload.Status.QUEUE)
                    }
                    if (!stillExistsAndDownloading) {
                        activeDownloadJob?.cancel()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("İndirme sırası hazırlanıyor...", 0, true))
        
        if (downloadJob == null || downloadJob?.isCompleted == true) {
            startLoop()
        }
        
        return START_NOT_STICKY
    }

    private fun startLoop() {
        downloadJob = serviceScope.launch {
            while (isActive) {
                val next = AnimeDownloadManager.getNextDownload()
                if (next == null) {
                    // No more downloads, stop foreground service
                    delay(2000L) // Wait a brief moment
                    if (AnimeDownloadManager.getNextDownload() == null) {
                        stopSelf()
                        break
                    }
                } else {
                    currentDownloadingAnimeId = next.animeId
                    currentDownloadingEpisode = next.episode

                    val activeJob = launch {
                        try {
                            // Pre-fetch poster bitmap on IO thread for rich notifications
                            val posterBitmap = loadPosterBitmap(next.posterUrl)

                            var lastNotificationTime = 0L
                            downloader.download(
                                download = next,
                                onProgress = { progress, size, duration, segmentsDownloaded ->
                                    AnimeDownloadManager.updateProgress(
                                        animeId           = next.animeId,
                                        episode           = next.episode,
                                        progress          = progress,
                                        downloadedBytes   = size,
                                        totalBytes        = duration,
                                        downloadedSegments = segmentsDownloaded
                                    )
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastNotificationTime >= 1000L || progress == 100) {
                                        lastNotificationTime = currentTime
                                        notificationManager.notify(
                                            NOTIFICATION_ID,
                                            buildNotification(
                                                download    = next,
                                                progress    = progress,
                                                indeterminate = false,
                                                posterBitmap  = posterBitmap
                                            )
                                        )
                                    }
                                },
                                onStatusChanged = { status, detail ->
                                    AnimeDownloadManager.updateStatus(
                                        animeId = next.animeId,
                                        episode = next.episode,
                                        status  = status,
                                        detail  = detail
                                    )
                                    if (status == AnimeDownload.Status.COMPLETED) {
                                        showCompletedNotification(next, posterBitmap, success = true)
                                    } else if (status == AnimeDownload.Status.ERROR) {
                                        showCompletedNotification(next, posterBitmap, success = false)
                                    }
                                }
                            )
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (isActive) {
                                AnimeDownloadManager.updateStatus(
                                    animeId = next.animeId,
                                    episode = next.episode,
                                    status = AnimeDownload.Status.ERROR,
                                    detail = KitsugiStrings.get(R.string.kitsugi_clip_error_generic)
                                )
                            }
                        }
                    }

                    activeDownloadJob = activeJob
                    try {
                        activeJob.join()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        currentDownloadingAnimeId = null
                        currentDownloadingEpisode = null
                        activeDownloadJob = null
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun buildNotification(
        download: AnimeDownload? = null,
        text: String? = null,
        progress: Int = 0,
        indeterminate: Boolean = true,
        posterBitmap: Bitmap? = null
    ): Notification {
        val title = if (download != null)
            if (download.isClip) "⬇️ ${download.clipTitle ?: download.animeTitle}"
            else "⬇️ ${download.animeTitle}"
        else
            "Anime İndiriliyor"

        val contentText = if (download != null) {
            val pct = if (!indeterminate) " (%$progress)" else ""
            if (download.isClip) {
                "${download.animeTitle}$pct"
            } else {
                val ep = "Bölüm ${download.episode}"
                val q  = if (!download.quality.isNullOrBlank()) " • ${download.quality}" else ""
                "$ep$q$pct"
            }
        } else {
            text ?: "İndirme sırası hazırlanıyor…"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (posterBitmap != null) {
            builder.setLargeIcon(posterBitmap)
        }

        if (download != null) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
                    .setBigContentTitle(title)
                    .setSummaryText(
                        buildString {
                            if (!download.source.isNullOrBlank()) append(download.source)
                            // Eski indirmelerde bozuk eklenti metni olabilir; bildirimde temiz göster.
                            val cleanTitle = com.kitsugi.animelist.utils.StreamLabelSanitizer
                                .sanitize(download.streamTitle)
                            if (cleanTitle.isNotBlank()) append(" • $cleanTitle")
                        }.ifBlank { "Video İndiriliyor" }
                    )
            )
        }

        return builder.build()
    }

    // Convenience overload used at service start (no download object yet)
    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean): Notification =
        buildNotification(download = null, text = text, progress = progress, indeterminate = indeterminate)

    private fun showCompletedNotification(download: AnimeDownload, posterBitmap: Bitmap?, success: Boolean) {
        val emoji   = if (success) "✅" else "❌"
        val verb    = if (success) "indirildi" else "indirilemedi"
        val epInfo  = if (download.isClip) (download.clipTitle ?: "Video") else "Bölüm ${download.episode}"
        val quality = if (!download.isClip && !download.quality.isNullOrBlank()) " • ${download.quality}" else ""
        val source  = if (!download.source.isNullOrBlank()) " • ${download.source}" else ""

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$emoji ${download.animeTitle}")
            .setContentText("$epInfo$quality $verb")
            .setSubText(KitsugiStrings.get(R.string.video_indirme))
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (posterBitmap != null) {
            builder.setLargeIcon(posterBitmap)
            if (success) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(posterBitmap)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText("$epInfo$quality$source")
                )
            }
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$epInfo$quality $verb")
                    .setSummaryText(source.trim())
            )
        }

        val notificationId = download.animeId.hashCode() + download.episode
        notificationManager.notify(notificationId, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Anime İndirmeleri",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = KitsugiStrings.get(R.string.anime_indirme_ilerlemesini_gosterir)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
