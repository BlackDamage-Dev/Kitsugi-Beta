package com.kitsugi.animelist.data.manga

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.kitsugi.animelist.MainActivity
import com.kitsugi.animelist.R

/**
 * Android Foreground Service ve MediaSession ile sürekli TTS oynatımı ve kalıcı bildirim.
 * Uygulama arka plana geçtiğinde sistem tarafından öldürülmesini engeller.
 */
class MangaSpeechService : Service() {

    interface Listener {
        fun onPlay()
        fun onPause()
        fun onStop()
        fun onNext()
        fun onPrevious()
    }

    private var mediaSession: MediaSessionCompat? = null
    private var isPlaying = false
    private var currentTitle = ""
    private var currentChapter = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        Log.d(TAG, "MangaSpeechService oluşturuldu")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (intent != null) {
            intent.getStringExtra(EXTRA_TITLE)?.let { currentTitle = it }
            intent.getStringExtra(EXTRA_CHAPTER)?.let { currentChapter = it }
            if (intent.hasExtra(EXTRA_IS_PLAYING)) {
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
            }
        }

        when (action) {
            ACTION_START, ACTION_UPDATE -> {
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                updatePlaybackState()
            }
            ACTION_PLAY -> {
                isPlaying = true
                updatePlaybackState()
                val notification = buildNotification()
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                listener?.onPlay()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                updatePlaybackState()
                val notification = buildNotification()
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                listener?.onPause()
            }
            ACTION_STOP -> {
                listener?.onStop()
                stopForegroundService()
            }
            ACTION_NEXT -> {
                listener?.onNext()
            }
            ACTION_PREVIOUS -> {
                listener?.onPrevious()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onDestroy() {
        Log.d(TAG, "MangaSpeechService durduruluyor")
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        listener = null
        super.onDestroy()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MangaSpeechSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    this@MangaSpeechService.isPlaying = true
                    updatePlaybackState()
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification())
                    listener?.onPlay()
                }

                override fun onPause() {
                    this@MangaSpeechService.isPlaying = false
                    updatePlaybackState()
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification())
                    listener?.onPause()
                }

                override fun onStop() {
                    listener?.onStop()
                    stopForegroundService()
                }

                override fun onSkipToNext() {
                    listener?.onNext()
                }

                override fun onSkipToPrevious() {
                    listener?.onPrevious()
                }
            })
        }
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(this, 0, launchIntent, flags)

        val prevIntent = Intent(this, MangaSpeechService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPending = PendingIntent.getService(this, 1, prevIntent, flags)

        val playPauseIntent = Intent(this, MangaSpeechService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePending = PendingIntent.getService(this, 2, playPauseIntent, flags)

        val nextIntent = Intent(this, MangaSpeechService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, flags)

        val stopIntent = Intent(this, MangaSpeechService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 4, stopIntent, flags)

        val title = currentTitle.ifBlank { getString(R.string.tts_notification_channel_name) }
        val content = currentChapter.ifBlank { getString(R.string.tts_notification_channel_desc) }

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) getString(R.string.tts_action_pause) else getString(R.string.tts_action_play)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.tts_action_previous), prevPending)
            .addAction(playPauseIcon, playPauseText, playPausePending)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.tts_action_next), nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.tts_action_stop), stopPending)
            .build()
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tts_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.tts_notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "MangaSpeechService"
        const val CHANNEL_ID = "kitsugi_manga_tts"
        const val NOTIFICATION_ID = 4099

        const val ACTION_START = "com.kitsugi.animelist.action.TTS_START"
        const val ACTION_UPDATE = "com.kitsugi.animelist.action.TTS_UPDATE"
        const val ACTION_PLAY = "com.kitsugi.animelist.action.TTS_PLAY"
        const val ACTION_PAUSE = "com.kitsugi.animelist.action.TTS_PAUSE"
        const val ACTION_STOP = "com.kitsugi.animelist.action.TTS_STOP"
        const val ACTION_NEXT = "com.kitsugi.animelist.action.TTS_NEXT"
        const val ACTION_PREVIOUS = "com.kitsugi.animelist.action.TTS_PREVIOUS"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHAPTER = "extra_chapter"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        @Volatile
        var listener: Listener? = null

        fun start(context: Context, title: String, chapter: String, isPlaying: Boolean = true) {
            val intent = Intent(context, MangaSpeechService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CHAPTER, chapter)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateState(context: Context, title: String, chapter: String, isPlaying: Boolean) {
            val intent = Intent(context, MangaSpeechService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CHAPTER, chapter)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MangaSpeechService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
