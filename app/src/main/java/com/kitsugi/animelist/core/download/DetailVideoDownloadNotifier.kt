package com.kitsugi.animelist.core.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Videolar sekmesindeki indirmeler için ilerleme/tamamlanma bildirimleri.
 *
 * Neden ayrı bir kanal? Fragman/OP-ED indirmeleri bölüm indirmelerinden bağımsızdır:
 * kullanıcı 12 saniyelik bir teaser indirirken "bölüm indiriliyor" bildirimi görmemeli.
 */
internal object DetailVideoDownloadNotifier {

    private const val CHANNEL_ID = "kitsugi_detail_video_downloads"
    private const val TAG = "DetailVideoNotifier"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lastProgressAt = ConcurrentHashMap<Int, Long>()
    private val channelReady = AtomicBoolean(false)

    fun progress(context: Context, notifId: Int, label: String, fraction: Float?) {
        // Bildirim güncellemesi saniyede en fazla 2 kez: 60 Hz bildirim akışı
        // SystemUI'yi ve ana iş parçacığını boğuyordu.
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastProgressAt[notifId] ?: 0L
        if (now - last < 500L) return
        lastProgressAt[notifId] = now

        scope.launch {
            runCatching {
                ensureChannel(context)
                if (!canPostNotifications(context)) return@runCatching
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(KitsugiStrings.get(R.string.detail_video_downloading_title, label))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                if (fraction != null) {
                    builder.setProgress(100, (fraction.coerceIn(0f, 1f) * 100f).toInt(), false)
                } else {
                    builder.setProgress(0, 0, true)
                }
                manager.notify(notifId, builder.build())
            }
        }
    }

    fun completed(context: Context, notifId: Int, label: String, fileName: String) {
        lastProgressAt.remove(notifId)
        scope.launch {
            runCatching {
                ensureChannel(context)
                if (!canPostNotifications(context)) return@runCatching
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val openIntent = Intent(context, com.kitsugi.animelist.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pending = PendingIntent.getActivity(
                    context,
                    notifId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(KitsugiStrings.get(R.string.detail_video_download_done_title))
                    .setContentText(fileName)
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                manager.notify(notifId, notification)
            }
        }
    }

    fun failed(context: Context, notifId: Int, label: String, message: String?) {
        lastProgressAt.remove(notifId)
        scope.launch {
            runCatching {
                ensureChannel(context)
                if (!canPostNotifications(context)) return@runCatching
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(KitsugiStrings.get(R.string.detail_video_download_failed_title))
                    .setContentText(message ?: label)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                manager.notify(notifId, notification)
            }
        }
    }

    fun cancel(context: Context, notifId: Int) {
        lastProgressAt.remove(notifId)
        scope.launch {
            runCatching {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(notifId)
            }
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (!channelReady.compareAndSet(false, true)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                KitsugiStrings.get(R.string.detail_video_download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = KitsugiStrings.get(R.string.detail_video_download_channel_desc)
                setShowBadge(false)
            }
            runCatching { manager.createNotificationChannel(channel) }
                .onFailure { Log.w(TAG, "Kanal oluşturulamadı: ${it.message}") }
        }
    }
}
