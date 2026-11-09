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
    private var currentDownloadingKey: String? = null
    private lateinit var downloader: AnimeDownloader
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val CHANNEL_ID    = "anime_downloads_channel"
        private const val NOTIFICATION_ID = 10002

        /** 429 sonrası otomatik yeniden deneme sayısı. */
        private const val MAX_AUTO_RETRIES = 3

        /** Artan bekleme: 5 sn → 20 sn → 60 sn. */
        private val AUTO_RETRY_DELAYS_MS = longArrayOf(5_000L, 20_000L, 60_000L)

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
                val activeKey = currentDownloadingKey
                if (activeKey != null) {
                    val stillExistsAndDownloading = list.any {
                        it.uniqueKey == activeKey &&
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
        val initMsg = if (com.kitsugi.animelist.utils.isTurkish()) "İndirme sırası hazırlanıyor…" else "Preparing download queue…"
        startForeground(NOTIFICATION_ID, buildNotification(text = initMsg, progress = 0, indeterminate = true))
        
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
                    val hasDelayedWork = AnimeDownloadManager.downloads.value.any {
                        it.status == AnimeDownload.Status.QUEUE && it.retryAtMs != null
                    }
                    if (hasDelayedWork) {
                        delay(1_000L)
                        continue
                    }
                    stopSelf()
                    break
                } else {
                    val key = next.uniqueKey
                    currentDownloadingKey = key

                    val activeJob = launch {
                        try {
                            // Pre-fetch poster bitmap on IO thread for rich notifications
                            val posterBitmap = loadPosterBitmap(next.posterUrl)

                            var lastNotificationTime = 0L
                            downloader.download(
                                download = next,
                                onProgress = { progress, size, duration, segmentsDownloaded ->
                                    AnimeDownloadManager.updateProgressByKey(
                                        uniqueKey = key,
                                        progress = progress,
                                        downloadedBytes = size,
                                        totalBytes = duration,
                                        downloadedSegments = segmentsDownloaded
                                    )
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastNotificationTime >= 900L || progress == 100) {
                                        lastNotificationTime = currentTime
                                        notificationManager.notify(
                                            NOTIFICATION_ID,
                                            buildNotification(
                                                download = next,
                                                progress = progress,
                                                indeterminate = false,
                                                posterBitmap = posterBitmap
                                            )
                                        )
                                    }
                                },
                                onStatusChanged = { status, localPath, errorMessage ->
                                    val isRateLimited = errorMessage?.contains("429") == true ||
                                        errorMessage?.contains("hız sınırı", ignoreCase = true) == true ||
                                        errorMessage?.contains("rate limit", ignoreCase = true) == true
                                    if (status == AnimeDownload.Status.ERROR &&
                                        isRateLimited &&
                                        next.autoRetryCount < MAX_AUTO_RETRIES
                                    ) {
                                        val waitMs = AUTO_RETRY_DELAYS_MS[
                                            next.autoRetryCount.coerceIn(0, AUTO_RETRY_DELAYS_MS.lastIndex)
                                        ]
                                        AnimeDownloadManager.scheduleRetryByKey(
                                            uniqueKey = key,
                                            waitMs = waitMs
                                        )
                                        showRateLimitNotification(next, errorMessage.orEmpty(), waitMs)
                                    } else {
                                        AnimeDownloadManager.updateStatusByKey(
                                            uniqueKey = key,
                                            status = status,
                                            localPath = localPath,
                                            errorMessage = errorMessage
                                        )
                                        if (status == AnimeDownload.Status.COMPLETED) {
                                            showCompletedNotification(next, posterBitmap, success = true)
                                        } else if (status == AnimeDownload.Status.ERROR) {
                                            showCompletedNotification(next, posterBitmap, success = false, detail = errorMessage)
                                        }
                                    }
                                }
                            )
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (isActive) {
                                AnimeDownloadManager.updateStatusByKey(
                                    uniqueKey = key,
                                    status = AnimeDownload.Status.ERROR,
                                    errorMessage = e.message ?: KitsugiStrings.get(R.string.kitsugi_clip_error_generic)
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
                        currentDownloadingKey = null
                        activeDownloadJob = null
                    }
                }
                delay(600L)
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
        val tr = com.kitsugi.animelist.utils.isTurkish()
        val title = when {
            download == null -> if (tr) "İndiriliyor" else "Downloading"
            download.isClip -> "⬇️ ${download.clipTitle ?: download.animeTitle}"
            download.isAudioOnly -> "🎧 ${download.animeTitle}"
            else -> "⬇️ ${download.animeTitle}"
        }

        val contentText = if (download != null) {
            val pct = if (!indeterminate) " (%$progress)" else ""
            when {
                download.isClip -> "${download.animeTitle}$pct"
                download.isAudioOnly -> {
                    val ep = if (tr) "Bölüm ${download.episode}" else "Episode ${download.episode}"
                    val label = download.audioTrackLabel?.takeIf { it.isNotBlank() } ?: download.quality
                    "$ep • 🎧 $label$pct"
                }
                else -> {
                    val ep = if (tr) "Bölüm ${download.episode}" else "Episode ${download.episode}"
                    val q  = if (!download.quality.isNullOrBlank()) " • ${download.quality}" else ""
                    "$ep$q$pct"
                }
            }
        } else {
            text ?: (if (tr) "İndirme sırası hazırlanıyor…" else "Preparing download queue…")
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
                        }.ifBlank { if (download.isAudioOnly) "Ses İndiriliyor" else "Video İndiriliyor" }
                    )
            )
        }

        return builder.build()
    }

    // Convenience overload used at service start (no download object yet)
    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean): Notification =
        buildNotification(download = null, text = text, progress = progress, indeterminate = indeterminate)

    private fun showCompletedNotification(
        download: AnimeDownload,
        posterBitmap: Bitmap?,
        success: Boolean,
        detail: String? = null
    ) {
        val tr = com.kitsugi.animelist.utils.isTurkish()
        val emoji   = if (success) "✅" else "❌"
        val verb    = if (success) {
            if (tr) "indirildi" else "downloaded"
        } else {
            if (tr) "indirilemedi" else "failed to download"
        }
        val epInfo  = when {
            download.isClip -> download.clipTitle ?: "Video"
            download.isAudioOnly -> {
                val ep = if (tr) "Bölüm ${download.episode}" else "Episode ${download.episode}"
                val label = download.audioTrackLabel?.takeIf { it.isNotBlank() } ?: download.quality
                "$ep • 🎧 $label"
            }
            else -> if (tr) "Bölüm ${download.episode}" else "Episode ${download.episode}"
        }
        val quality = if (!download.isClip && !download.isAudioOnly && !download.quality.isNullOrBlank()) " • ${download.quality}" else ""
        val source  = if (!download.source.isNullOrBlank()) " • ${download.source}" else ""

        val subText = when {
            download.isAudioOnly -> if (tr) "Ses İndirme" else "Audio Download"
            else -> KitsugiStrings.get(R.string.video_indirme)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$emoji ${download.animeTitle}")
            .setContentText("$epInfo$quality $verb")
            .setSubText(subText)
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
            } else {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$epInfo$quality $verb" + (detail?.let { "\n$it" } ?: ""))
                        .setSummaryText(source.trim())
                )
            }
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$epInfo$quality $verb" + (detail?.let { "\n$it" } ?: ""))
                    .setSummaryText(source.trim())
            )
        }

        val notificationId = download.uniqueKey.hashCode()
        notificationManager.notify(notificationId, builder.build())
    }

    private fun showRateLimitNotification(download: AnimeDownload, detail: String, waitMs: Long) {
        val seconds = (waitMs / 1000L).coerceAtLeast(1L)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏳ ${download.animeTitle}")
            .setContentText("Bölüm ${download.episode} • kaynak hız sınırı, ${seconds} sn sonra devam edecek")
            .setSubText(KitsugiStrings.get(R.string.video_indirme))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Bölüm ${download.episode} • kaynak hız sınırı (429)\n" +
                            "${seconds} sn sonra kaldığı yerden devam edecek.\n$detail"
                    )
            )
        notificationManager.notify(download.uniqueKey.hashCode(), builder.build())
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
