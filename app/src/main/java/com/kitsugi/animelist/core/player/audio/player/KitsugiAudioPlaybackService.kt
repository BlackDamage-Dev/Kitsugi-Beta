package com.kitsugi.animelist.core.player.audio.player

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
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.screens.audio.KitsugiAudioPlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Kitsugi'nin evrensel ses oynatıcısı için arka plan bildirim ve kilit ekranı servisi.
 *
 * Ekran kapandığında, başka bir uygulamaya geçildiğinde veya kulaklık/araç Bluetooth
 * düğmelerine basıldığında ses oynatımının kesintisiz sürmesini sağlar.
 */
class KitsugiAudioPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSessionCompat? = null
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val manager = KitsugiAudioPlayerManager.getInstance(applicationContext)

        val session = MediaSessionCompat(this, SESSION_TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = manager.play()
                override fun onPause() = manager.pause()
                override fun onSkipToNext() = manager.skipNext()
                override fun onSkipToPrevious() = manager.skipPrevious()
                override fun onSeekTo(pos: Long) = manager.seekTo(pos)
                override fun onFastForward() = manager.seekBy(10_000L)
                override fun onRewind() = manager.seekBy(-10_000L)
                override fun onStop() {
                    manager.stopAndClear()
                    stopSelfCleanly()
                }
            })
            isActive = true
        }
        mediaSession = session

        serviceScope.launch {
            combine(manager.state, manager.artworkBitmap) { state, artwork ->
                state to artwork
            }.collect { (state, artwork) ->
                val item = state.currentItem
                if (item == null) {
                    if (isForegroundStarted) {
                        stopSelfCleanly()
                    }
                    return@collect
                }
                updateMediaSession(session, state, item, artwork)
                val notification = buildNotification(session, state, item, artwork)
                postNotification(notification, state.isPlaying)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = KitsugiAudioPlayerManager.getInstance(applicationContext)
        when (intent?.action) {
            ACTION_TOGGLE -> manager.togglePlayPause()
            ACTION_PLAY -> manager.play()
            ACTION_PAUSE -> manager.pause()
            ACTION_PREV -> manager.skipPrevious()
            ACTION_NEXT -> manager.skipNext()
            ACTION_STOP -> {
                manager.stopAndClear()
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            ACTION_SYNC -> {
                val state = manager.state.value
                val item = state.currentItem
                val session = mediaSession
                if (item != null && session != null) {
                    val artwork = manager.artworkBitmap.value
                    updateMediaSession(session, state, item, artwork)
                    postNotification(buildNotification(session, state, item, artwork), state.isPlaying)
                } else if (!isForegroundStarted) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val manager = KitsugiAudioPlayerManager.getInstance(applicationContext)
        if (!manager.state.value.isPlaying) {
            manager.stopAndClear()
            stopSelfCleanly()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        mediaSession = null
        isForegroundStarted = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateMediaSession(
        session: MediaSessionCompat,
        state: AudioPlayerState,
        item: AudioPlayerItem,
        artwork: android.graphics.Bitmap?
    ) {
        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.title)
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                item.subtitle ?: item.animeTitle ?: getString(R.string.audio_player_default_subtitle)
            )
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                if (state.durationMs > 0L) state.durationMs else -1L
            )
        if (artwork != null && !artwork.isRecycled) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
        }
        runCatching { session.setMetadata(metaBuilder.build()) }

        val pbState = when {
            state.isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            state.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_STOP

        val playbackState = PlaybackStateCompat.Builder()
            .setState(pbState, state.positionMs.coerceAtLeast(0L), state.playbackSpeed)
            .setActions(actions)
            .build()
        runCatching { session.setPlaybackState(playbackState) }
    }

    private fun buildNotification(
        session: MediaSessionCompat,
        state: AudioPlayerState,
        item: AudioPlayerItem,
        artwork: android.graphics.Bitmap?
    ): Notification {
        val openIntent = Intent(this, KitsugiAudioPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            REQ_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPending = serviceActionPendingIntent(ACTION_PREV, REQ_PREV)
        val togglePending = serviceActionPendingIntent(ACTION_TOGGLE, REQ_TOGGLE)
        val nextPending = serviceActionPendingIntent(ACTION_NEXT, REQ_NEXT)
        val stopPending = serviceActionPendingIntent(ACTION_STOP, REQ_STOP)

        val subtitleText = buildList {
            item.subtitle?.takeIf { it.isNotBlank() }?.let { add(it) }
            val badges = state.streamSpecs.summaryBadges(item.formatBadge)
            if (badges.isNotEmpty()) add(badges.take(2).joinToString(" • "))
        }.joinToString(" · ").ifBlank { getString(R.string.audio_player_default_subtitle) }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (state.isPlaying) android.R.drawable.ic_media_play
                else android.R.drawable.ic_media_pause
            )
            .setContentTitle(item.title)
            .setContentText(subtitleText)
            .setContentIntent(contentPending)
            .setDeleteIntent(stopPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_previous,
                getString(R.string.audio_player_previous),
                prevPending
            )
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) getString(R.string.audio_player_pause) else getString(R.string.audio_player_play),
                togglePending
            )
            .addAction(
                android.R.drawable.ic_media_next,
                getString(R.string.audio_player_next),
                nextPending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.audio_player_close),
                stopPending
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (artwork != null && !artwork.isRecycled) {
            builder.setLargeIcon(artwork)
        }
        return builder.build()
    }

    private fun postNotification(notification: Notification, isPlaying: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isPlaying && !isForegroundStarted) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundStarted = true
            }.onFailure { t ->
                Log.w(TAG, "startForeground başarısız, normal bildirimle devam ediliyor: ${t.message}")
                runCatching { nm.notify(NOTIFICATION_ID, notification) }
            }
        } else if (isForegroundStarted) {
            runCatching { nm.notify(NOTIFICATION_ID, notification) }
            if (!isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
                } else {
                    @Suppress("DEPRECATION")
                    runCatching { stopForeground(false) }
                }
                isForegroundStarted = false
            }
        } else {
            runCatching { nm.notify(NOTIFICATION_ID, notification) }
        }
    }

    private fun stopSelfCleanly() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isForegroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            } else {
                @Suppress("DEPRECATION")
                runCatching { stopForeground(true) }
            }
            isForegroundStarted = false
        }
        runCatching { nm.cancel(NOTIFICATION_ID) }
        stopSelf()
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, KitsugiAudioPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.audio_player_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.audio_player_notification_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "KitsugiAudioService"
        const val CHANNEL_ID = "kitsugi_audio_playback"
        const val SESSION_TAG = "KitsugiStandaloneAudioSession"
        const val NOTIFICATION_ID = 9042

        const val ACTION_SYNC = "com.kitsugi.animelist.audio.SYNC"
        const val ACTION_TOGGLE = "com.kitsugi.animelist.audio.TOGGLE"
        const val ACTION_PLAY = "com.kitsugi.animelist.audio.PLAY"
        const val ACTION_PAUSE = "com.kitsugi.animelist.audio.PAUSE"
        const val ACTION_PREV = "com.kitsugi.animelist.audio.PREV"
        const val ACTION_NEXT = "com.kitsugi.animelist.audio.NEXT"
        const val ACTION_STOP = "com.kitsugi.animelist.audio.STOP"

        private const val REQ_OPEN = 9400
        private const val REQ_PREV = 9401
        private const val REQ_TOGGLE = 9402
        private const val REQ_NEXT = 9403
        private const val REQ_STOP = 9404

        fun sync(context: Context, isPlaying: Boolean) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KitsugiAudioPlaybackService::class.java)
                .setAction(ACTION_SYNC)
            runCatching {
                if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }.onFailure { t ->
                Log.w(TAG, "Ses servisi başlatılamadı: ${t.message}")
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                appContext.stopService(Intent(appContext, KitsugiAudioPlaybackService::class.java))
            }
        }
    }
}
