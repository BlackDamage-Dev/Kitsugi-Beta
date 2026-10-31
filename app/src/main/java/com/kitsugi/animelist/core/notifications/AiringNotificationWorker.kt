package com.kitsugi.animelist.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kitsugi.animelist.MainActivity
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.SimklImportManager
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.toDomain
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.data.remote.KitsugiAniListNotificationClient
import com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient
import com.kitsugi.animelist.data.remote.SimklCalendarClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.OffsetDateTime
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class AiringNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AiringWorker"
        private const val CHANNEL_ID = "Kitsugi_airing_channel"
        private const val PREFS_NAME = "Kitsugi_notified_prefs"
        private const val KEY_NOTIFIED_ANILIST = "notified_anilist_ids"
        private const val KEY_NOTIFIED_MAL = "notified_mal_keys"
        private const val KEY_NOTIFIED_SIMKL = "notified_simkl_keys"
        private const val KEY_NOTIFIED_KITSU = "notified_kitsu_keys"
        private const val KEY_NOTIFIED_SHIKIMORI = "notified_shikimori_ids"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "AiringNotificationWorker background check started")

        // Load settings
        val settings = SettingsDataStore(context).settingsFlow.first()

        // A saved toggle alone is not enough: source notifications must never poll or
        // produce local airing alerts after the corresponding account was disconnected.
        val aniListTokenForGate = ExternalAuthManager.getAniListToken(context)
        val malTokenForGate = ExternalAuthManager.getMalToken(context)
        val simklTokenForGate = ExternalAuthManager.getSimklToken(context)
        val kitsuTokenForGate = ExternalAuthManager.getKitsuToken(context)
        val shikimoriTokenForGate = ExternalAuthManager.getShikimoriToken(context)
        val bangumiConnected = ExternalAuthManager.getAuthState(context).isBangumiConnected
        val hasLinkedAccount = listOf(
            aniListTokenForGate,
            malTokenForGate,
            simklTokenForGate,
            kitsuTokenForGate,
            shikimoriTokenForGate
        ).any { !it.isNullOrBlank() } || bangumiConnected

        val isAiringEnabled = settings.airingNotificationsEnabled && hasLinkedAccount
        val isAniListEnabled = settings.aniListNotificationsEnabled && !aniListTokenForGate.isNullOrBlank()
        val isMalEnabled = settings.malNotificationsEnabled && !malTokenForGate.isNullOrBlank()
        val isSimklEnabled = settings.simklNotificationsEnabled && !simklTokenForGate.isNullOrBlank()
        val isKitsuEnabled = settings.kitsuNotificationsEnabled && !kitsuTokenForGate.isNullOrBlank()
        val isShikimoriEnabled = settings.shikimoriNotificationsEnabled && !shikimoriTokenForGate.isNullOrBlank()

        // If no notifications are enabled, stop immediately
        if (!isAiringEnabled && !isAniListEnabled && !isMalEnabled && !isSimklEnabled &&
            !isKitsuEnabled && !isShikimoriEnabled
        ) {
            Log.d(TAG, "No linked notification sources are enabled. Canceling stale periodic work.")
            NotificationScheduler.cancelIfNoActiveSources(context)
            return Result.success()
        }

        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Fetch watching entries from DB
        val db = KitsugiDatabase.getDatabase(context)
        val watchingEntries = try {
            val allEntities = db.mediaEntryDao().getAll()
            allEntities.filter { entity ->
                entity.status == WatchStatus.Watching.name ||
                entity.status == WatchStatus.Repeating.name
            }.map { it.toDomain() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load database watching entries: ${e.message}")
            emptyList()
        }

        // 2. AniList Polling
        if (isAniListEnabled) {
            val aniListToken = ExternalAuthManager.getAniListToken(context)
            if (!aniListToken.isNullOrBlank()) {
                try {
                    val client = KitsugiAniListNotificationClient()
                    val page = client.fetchNotifications(aniListToken, page = 1, perPage = 25)
                    val notifiedIds = sharedPrefs.getStringSet(KEY_NOTIFIED_ANILIST, emptySet())?.toMutableSet() ?: mutableSetOf()

                    for (notif in page.notifications) {
                        val notifIdStr = notif.id.toString()
                        if (!notifiedIds.contains(notifIdStr)) {
                            val title = notif.mediaTitle ?: notif.userName ?: notif.threadTitle ?: "AniList"
                            val body = formatAniListBody(notif)
                            val isMedia = notif.type in listOf("AIRING", "RELATED_MEDIA_ADDITION", "MEDIA_DATA_CHANGE", "MEDIA_MERGE")

                            showNotification(
                                id = notif.id,
                                title = title,
                                bodyText = body,
                                source = "AniList",
                                imageUrl = notif.mediaCoverUrl,
                                avatarUrl = notif.userAvatarUrl,
                                isMedia = isMedia,
                                mediaId = notif.mediaId
                            )
                            notifiedIds.add(notifIdStr)
                        }
                    }
                    val keptIds = notifiedIds.toList().takeLast(200).toSet()
                    sharedPrefs.edit().putStringSet(KEY_NOTIFIED_ANILIST, keptIds).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "AniList notifications fetch failed: ${e.message}", e)
                }
            }
        }

        // 3. MyAnimeList (Airing Calendar) Polling
        if (isMalEnabled || isAiringEnabled) {
            try {
                val calendarClient = KitsugiAiringCalendarClient()
                val nowMs = System.currentTimeMillis()
                // Hafta sınırına takılmayan kayan pencere: son 24 saat + önümüzdeki 1 saat.
                // (Eski hâli takvim haftasına bağlıydı; Pazartesi sabahı Pazar gecesi
                // yayınlanan bölümler gözden kaçabiliyordu.)
                val allEntries = calendarClient.fetchAiringWindow(
                    fromEpochSec = (nowMs - 24 * 60 * 60 * 1000L) / 1000L,
                    toEpochSec = (nowMs + 60 * 60 * 1000L) / 1000L
                )
                val now = nowMs

                val matched = allEntries.filter { entry ->
                    watchingEntries.any { me ->
                        (entry.malId != null && me.malId == entry.malId) ||
                        (me.source == "anilist" && me.malId == 100_000_000 + entry.aniListId)
                    }
                }

                val notifiedMalKeys = sharedPrefs.getStringSet(KEY_NOTIFIED_MAL, emptySet())?.toMutableSet() ?: mutableSetOf()

                for (entry in matched) {
                    val triggerMs = entry.airingAt * 1000L
                    // Recently aired (within past 24 hours)
                    if (triggerMs <= now && triggerMs > now - 24 * 60 * 60 * 1000L) {
                        val malKey = "${entry.malId}_${entry.episode}"
                        if (!notifiedMalKeys.contains(malKey)) {
                            val title = entry.title
                            val body = KitsugiStrings.get(R.string.bolum_x_artik_yayinda, entry.episode)
                            val malId = entry.malId ?: 0
                            val notifId = (malId * 1000 + entry.episode) and Int.MAX_VALUE

                            showNotification(
                                id = notifId,
                                title = title,
                                bodyText = body,
                                source = "MyAnimeList",
                                imageUrl = entry.coverUrl,
                                isMedia = true,
                                mediaId = malId
                            )
                            notifiedMalKeys.add(malKey)
                        }
                    }
                }
                val keptMalKeys = notifiedMalKeys.toList().takeLast(200).toSet()
                sharedPrefs.edit().putStringSet(KEY_NOTIFIED_MAL, keptMalKeys).apply()
            } catch (e: Exception) {
                Log.e(TAG, "MAL Airing Calendar fetch failed: ${e.message}", e)
            }
        }

        // 4. Simkl Polling (Watchlist Sync + v2 CDN takvim eşleşmesi)
        //    NOT: Simkl API'sinde bildirim ucu yoktur; resmî yöntem izleme listesi +
        //    https://data.simkl.in/calendar/v2/{tv|anime|movie_release}.json birleşimidir.
        if (isSimklEnabled) {
            val simklToken = ExternalAuthManager.getSimklToken(context)
            if (!simklToken.isNullOrBlank()) {
                try {
                    val importedEntries = SimklImportManager.fetchAllLists(simklToken)
                    // v2.4.341: repository kuyruk erişimiyle kurulur; sil-yeniden-yaz yerine
                    // birleştirme yapılır. Eski davranış, Simkl'de henüz push edilmemiş yerel
                    // kayıtları (ve kuyruk işlerini) her gece siliyordu.
                    val repository = MediaEntryRepository(
                        dao = db.mediaEntryDao(),
                        pendingSyncDao = db.pendingSyncDao(),
                        context = context
                    )
                    repository.smartImport("simkl", importedEntries, allowDelete = false)
                    Log.d(TAG, "Simkl background sync successful: ${importedEntries.size} entries")

                    val calendarClient = SimklCalendarClient()
                    val watchingIds = runCatching { calendarClient.fetchWatchingIds(simklToken) }.getOrNull()
                    val calendar = calendarClient.fetchAll()
                    val now = System.currentTimeMillis()
                    val notifiedSimklKeys = sharedPrefs.getStringSet(KEY_NOTIFIED_SIMKL, emptySet())?.toMutableSet() ?: mutableSetOf()

                    var matched = 0
                    for (entry in calendar) {
                        val isMatched = watchingIds != null && !watchingIds.isEmpty &&
                            watchingIds.matches(entry)
                        if (!isMatched) continue

                        val airingAt = entry.airingAtMs
                        // Yalnızca son 24 saat içinde yayınlananlar bildirilir.
                        if (airingAt > now || airingAt <= now - 24 * 60 * 60 * 1000L) continue
                        matched++

                        // Düzeltme: her bölüm kendi anahtarına sahip (eskiden bölüm ayırt
                        // edilmiyordu, bu yüzden dizi başına yalnızca bir bildirim gidiyordu).
                        val key = "simkl_${entry.simklId}_${entry.episode ?: 0}_$airingAt"
                        if (notifiedSimklKeys.contains(key)) continue

                        val label = entry.episodeLabel
                        showNotification(
                            id = key.hashCode() and 0x7fffffff,
                            title = entry.title,
                            bodyText = if (label != null) KitsugiStrings.get(R.string.notification_episode_released, label) else KitsugiStrings.get(R.string.notification_new_movie_release),
                            source = "Simkl",
                            imageUrl = entry.posterUrl,
                            isMedia = true,
                            mediaId = entry.simklId
                        )
                        notifiedSimklKeys.add(key)
                    }
                    Log.d(TAG, "Simkl airing matched=$matched")
                    val keptSimklKeys = notifiedSimklKeys.toList().takeLast(400).toSet()
                    sharedPrefs.edit().putStringSet(KEY_NOTIFIED_SIMKL, keptSimklKeys).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Simkl background sync failed: ${e.message}", e)
                }
            }
        }

        // 5. Kitsu Polling (takip listesi + nextRelease yayın tarihleri)
        //    Kitsu'nun genel API'sinde bildirim kaynağı yoktur; `nextRelease` alanı
        //    sonraki bölümün yayın zamanını verir ve bildirim bundan üretilir.
        if (isKitsuEnabled || isAiringEnabled) {
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val kitsuUserId = ExternalAuthManager.getKitsuUserId(context)
            if (!kitsuToken.isNullOrBlank() && !kitsuUserId.isNullOrBlank()) {
                try {
                    val entries = KitsuApiClient.fetchAllLibraryEntries(kitsuToken, kitsuUserId)
                    val active = entries.filter {
                        it.status.equals("current", true) || it.status.equals("watching", true)
                    }
                    val notifiedKitsuKeys = sharedPrefs.getStringSet(KEY_NOTIFIED_KITSU, emptySet())?.toMutableSet() ?: mutableSetOf()
                    val now = System.currentTimeMillis()

                    for (entry in active) {
                        val releaseMs = parseIsoToEpochMs(entry.nextRelease) ?: continue
                        if (releaseMs > now || releaseMs <= now - 24 * 60 * 60 * 1000L) continue

                        val key = "kitsu_${entry.id}_$releaseMs"
                        if (notifiedKitsuKeys.contains(key)) continue

                        showNotification(
                            id = key.hashCode() and 0x7fffffff,
                            title = entry.title,
                            bodyText = KitsugiStrings.get(R.string.bolum_entry_progress_1_yayinlandi, entry.progress + 1),
                            source = "Kitsu",
                            imageUrl = entry.imageUrl,
                            isMedia = true,
                            mediaId = entry.animeId ?: entry.mangaId
                        )
                        notifiedKitsuKeys.add(key)
                    }
                    val keptKitsuKeys = notifiedKitsuKeys.toList().takeLast(400).toSet()
                    sharedPrefs.edit().putStringSet(KEY_NOTIFIED_KITSU, keptKitsuKeys).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Kitsu polling failed: ${e.message}", e)
                }
            }
        }

        // 6. Shikimori Polling (gerçek bildirim ucu: GET /api/users/:id/messages)
        //    `messages` OAuth izni gerekir; izin yoksa sessizce atlanır ve loglanır.
        if (isShikimoriEnabled) {
            val shikiToken = runCatching { ExternalAuthManager.getOrRefreshShikimoriToken(context) }
                .getOrNull() ?: ExternalAuthManager.getShikimoriToken(context)
            val shikiUserId = ExternalAuthManager.getShikimoriUserId(context)
            if (!shikiToken.isNullOrBlank() && shikiUserId != null) {
                try {
                    val notifiedShikiIds = sharedPrefs.getStringSet(KEY_NOTIFIED_SHIKIMORI, emptySet())?.toMutableSet() ?: mutableSetOf()
                    val messages = ShikimoriApiClient.fetchMessages(
                        token = shikiToken,
                        userId = shikiUserId,
                        type = "notifications",
                        limit = 30
                    ) + ShikimoriApiClient.fetchMessages(
                        token = shikiToken,
                        userId = shikiUserId,
                        type = "news",
                        limit = 15
                    )

                    for (message in messages) {
                        // Okunmuş bildirimler için tekrar bildirim gönderilmez.
                        if (message.read) continue
                        val key = "${message.type}_${message.id}"
                        if (notifiedShikiIds.contains(key)) continue

                        val bodyText = message.body
                            .replace(Regex("\\[/?[a-zA-Z0-9=*]+[^\\]]*\\]"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .ifBlank { "Yeni Shikimori bildirimi" }

                        showNotification(
                            id = key.hashCode() and 0x7fffffff,
                            title = message.targetTitle ?: "Shikimori",
                            bodyText = bodyText.take(200),
                            source = "Shikimori",
                            imageUrl = message.targetImageUrl,
                            avatarUrl = message.fromAvatarUrl,
                            isMedia = message.targetImageUrl != null,
                            mediaId = message.linkedId?.toInt()
                        )
                        notifiedShikiIds.add(key)
                    }
                    val keptShikiKeys = notifiedShikiIds.toList().takeLast(400).toSet()
                    sharedPrefs.edit().putStringSet(KEY_NOTIFIED_SHIKIMORI, keptShikiKeys).apply()
                } catch (e: ShikimoriApiClient.ShikimoriScopeException) {
                    Log.w(TAG, "Shikimori 'messages' izni yok; bildirim atlanıyor: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Shikimori polling failed: ${e.message}", e)
                }
            }
        }

        NotificationScheduler.cancelIfNoActiveSources(context)
        return Result.success()
    }

    private fun formatAniListBody(notif: KitsugiAniListNotificationClient.KitsugiNotification): String {
        return when (notif.type) {
            "AIRING" -> "Bölüm ${notif.episode ?: 1} artık yayında! 🎬"
            "FOLLOWING" -> "${notif.userName ?: "Bir kullanıcı"} sizi takip etti."
            "ACTIVITY_MESSAGE" -> "${notif.userName ?: "Bir kullanıcı"} size mesaj gönderdi."
            "ACTIVITY_REPLY" -> "${notif.userName ?: "Bir kullanıcı"} aktivitenize yanıt verdi."
            "ACTIVITY_REPLY_SUBSCRIBED" -> "${notif.userName ?: "Bir kullanıcı"} abone olduğunuz aktiviteye yanıt verdi."
            "ACTIVITY_MENTION" -> "${notif.userName ?: "Bir kullanıcı"} sizden bahsetti."
            "ACTIVITY_LIKE" -> "${notif.userName ?: "Bir kullanıcı"} aktivitenizi beğendi."
            "ACTIVITY_REPLY_LIKE" -> "${notif.userName ?: "Bir kullanıcı"} yanıtınızı beğendi."
            "THREAD_COMMENT_MENTION" -> "${notif.userName ?: "Bir kullanıcı"} forumda sizi etiketledi."
            "THREAD_COMMENT_REPLY" -> "${notif.userName ?: "Bir kullanıcı"} forum yorumunuza yanıt verdi."
            "THREAD_COMMENT_SUBSCRIBED" -> "${notif.userName ?: "Bir kullanıcı"} takip ettiğiniz konuya yorum yaptı."
            "THREAD_COMMENT_LIKE" -> "${notif.userName ?: "Bir kullanıcı"} forum yorumunuzu beğendi."
            "THREAD_LIKE" -> "${notif.userName ?: "Bir kullanıcı"} forum konunuzu beğendi."
            "RELATED_MEDIA_ADDITION" -> "${notif.mediaTitle ?: "İçerik"} için yeni bağlantılı içerik eklendi."
            "MEDIA_DATA_CHANGE" -> "${notif.mediaTitle ?: "İçerik"} verileri güncellendi (${notif.reason ?: "Site veri güncellemesi"})."
            "MEDIA_MERGE" -> "${notif.mediaTitle ?: "İçerik"} başka bir içerikle birleştirildi."
            "MEDIA_DELETION" -> "${notif.deletedMediaTitle ?: "Bir içerik"} siteden silindi."
            else -> notif.context ?: "Yeni bir bildiriminiz var."
        }
    }

    private fun showNotification(
        id: Int,
        title: String,
        bodyText: String,
        source: String,
        imageUrl: String? = null,
        avatarUrl: String? = null,
        isMedia: Boolean = true,
        mediaId: Int? = null
    ) {
        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAV_TARGET", "NOTIFICATIONS")
            if (mediaId != null) putExtra("NAV_MEDIA_ID", mediaId)
            putExtra("NAV_SOURCE", source)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetUrl = avatarUrl ?: imageUrl
        val bitmap = if (!targetUrl.isNullOrBlank()) {
            downloadBitmap(targetUrl)
        } else null

        val displayTitle = "[$source] $title"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.kitsugi.animelist.R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(bodyText)
            .setSubText(source)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            if (isMedia) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setBigContentTitle(displayTitle)
                        .setSummaryText(bodyText)
                )
            } else {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(displayTitle)
                        .bigText(bodyText)
                )
            }
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(displayTitle)
                    .bigText(bodyText)
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot show notification: permission missing: ${e.message}")
        }
    }

    /** "2026-07-20T04:00:00Z" / "+09:00 ofsetli" ISO damgalarını epoch ms'e çevirir. */
    private fun parseIsoToEpochMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        return try {
            if (text.endsWith("Z", true)) Instant.parse(text).toEpochMilli()
            else OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadBitmap(urlString: String): android.graphics.Bitmap? {
        return try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.inputStream.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification image download failed for $urlString: ${e.message}")
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kitsugi Bildirimleri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = KitsugiStrings.get(R.string.notification_channel_description)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
