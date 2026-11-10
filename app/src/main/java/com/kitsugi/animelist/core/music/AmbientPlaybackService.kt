package com.kitsugi.animelist.core.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kitsugi.animelist.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 🎵 Ortam Sesi Ön Plan Servisi
 *
 * **Neden gerekli:** Android 17 (API 37) itibarıyla arka planda ses çalmak
 * için çalışan bir foreground service zorunlu. Aksi hâlde uygulama arka
 * plana geçtiğinde sistem sesi kesiyor.
 *
 * Motor `AmbientAudioEngine.get()` singleton olduğu için servis ayrı bir
 * çalar oluşturmaz — aynı motoru gözler ve bildirimi günceller. Böylece
 * okuyucudaki mute/volume değişiklikleri bildirime de anında yansır.
 *
 * Bildirimden yapılabilenler:
 *  - Duraklat / devam
 *  - Tümünü durdur
 *  - Uyku zamanlayıcısını iptal et
 *  - Uygulamaya dön
 */
class AmbientPlaybackService : Service() {

    companion object {
        private const val TAG = "AmbientPlaybackService"
        private const val CHANNEL_ID = "kitsugi_ambient_playback"
        private const val NOTIFICATION_ID = 7411

        const val ACTION_START = "com.kitsugi.animelist.ambient.START"
        const val ACTION_STOP = "com.kitsugi.animelist.ambient.STOP"
        const val ACTION_TOGGLE = "com.kitsugi.animelist.ambient.TOGGLE"
        const val ACTION_CANCEL_TIMER = "com.kitsugi.animelist.ambient.CANCEL_TIMER"

        /**
         * Servisi başlatır (ses çalmaya başladığında çağrılır).
         *
         * **Sessiz başarısızlık:** Android 12+ arka plandan foreground
         * servis başlatmayı kısıtlar, Android 13+ bildirim izni ister.
         * Bu koşullar sağlanmazsa `ForegroundServiceStartNotAllowedException`
         * fırlar — uygulamayı çökertmek yerine ses yalnızca ön planda
         * çalar. Bu kabul edilebilir bir gerilemedir.
         */
        fun start(context: Context) {
            val intent = Intent(context, AmbientPlaybackService::class.java)
                .setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { error ->
                Log.w("AmbientPlaybackService", "Ön plan servisi başlatılamadı — yalnızca ön planda çalacak", error)
            }
        }

        /** Servisi durdurur (tüm kanallar kapandığında). */
        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AmbientPlaybackService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private lateinit var engine: AmbientAudioEngine

    override fun onCreate() {
        super.onCreate()
        engine = AmbientAudioEngine.get(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // ⚠️ `fromService = true` ŞART.
                //
                // Eski hâli `engine.stopAllChannels()` çağırıyordu; o da
                // sonunda servise yeniden `ACTION_STOP` yolluyordu →
                // "servis → motor → servis" sonsuz döngüsü. Her turda
                // `startForeground` + `onCreate` çalıştığı için ana thread
                // sürekli meşgul kalıyor, uygulama kilitleniyor ve bir daha
                // ses çalmıyordu.
                engine.stopAllChannels(fromService = true)
                engine.cancelSleepTimer()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> engine.togglePlayPause()
            ACTION_CANCEL_TIMER -> engine.cancelSleepTimer()
            else -> Unit
        }

        // Foreground'a al — Android 14+ tip belirtmek zorunlu
        promoteToForeground()
        startObserving()
        return START_STICKY
    }

    /** Motoru gözler, bildirimi günceller; kanal kalmazsa kendini durdurur. */
    private fun startObserving() {
        if (observeJob != null) return
        observeJob = serviceScope.launch {
            engine.channels.collectLatest { channels ->
                if (channels.isEmpty()) {
                    stopSelf()
                    return@collectLatest
                }
                updateNotification()
            }
        }
        // Zamanlayıcı değişimi de bildirime yansısın
        serviceScope.launch {
            engine.sleepTimer.collect { updateNotification() }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ortam Sesi",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Okuyucu ortam sesi — okuma sırasında çalar"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val channels = engine.channels.value
        val isPlaying = engine.isPlaying.value
        val timer = engine.sleepTimer.value

        val title = when {
            channels.isEmpty() -> "Ortam sesi"
            channels.size == 1 -> channels.first().sound.title
            else -> "${channels.size} ses çalıyor"
        }
        val subtitle = buildString {
            if (channels.size > 1) {
                append(channels.take(3).joinToString(", ") { it.sound.title })
            } else {
                append(channels.firstOrNull()?.sound?.subtitle.orEmpty())
            }
            if (timer.running) {
                if (isNotEmpty()) append(" · ")
                append("⏱ ${timer.display}")
            }
        }.ifBlank { "Kitsugi ortam sesi" }

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPi = openIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val togglePi = PendingIntent.getService(
            this, 1,
            Intent(this, AmbientPlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 2,
            Intent(this, AmbientPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentIntent(contentPi)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                if (isPlaying) "Duraklat" else "Devam",
                togglePi
            )
            .addAction(0, "Durdur", stopPi)
            .apply {
                if (timer.running) {
                    addAction(
                        0, "Süreyi iptal",
                        PendingIntent.getService(
                            this@AmbientPlaybackService, 3,
                            Intent(this@AmbientPlaybackService, AmbientPlaybackService::class.java)
                                .setAction(ACTION_CANCEL_TIMER),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
