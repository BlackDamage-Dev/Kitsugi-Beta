package com.kitsugi.animelist.core.notifications

import android.content.Context
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.auth.TmdbAccountOperationMutex
import com.kitsugi.animelist.data.auth.TmdbAccountSyncCoordinator
import com.kitsugi.animelist.data.auth.TmdbAuthStore
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.notifications.NotificationArchiveStore
import com.kitsugi.animelist.data.remote.TmdbAccountSnapshot
import com.kitsugi.animelist.model.MediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * One account-scoped snapshot baseline shared by background work and the notification screen.
 * Delivery is awaited before the baseline advances. Re-delivery after a crash is safe because
 * both Android notifications and archive records use stable, type-sensitive IDs.
 */
object TmdbAccountNotificationPoller {
    private const val PREFS_NAME = "Kitsugi_notified_prefs"
    private val pollMutex = Mutex()

    data class Result(
        val snapshot: TmdbAccountSnapshot,
        val newEntries: List<MediaEntry>,
        val archivedNotifications: List<NotificationArchiveStore.ArchivedNotif>
    )

    suspend fun poll(
        context: Context,
        deliver: suspend (Result) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        pollMutex.withLock {
            // Production login/logout uses this same mutex, so the account cannot change between
            // the complete snapshot, local import, delivery and durable baseline commit.
            TmdbAccountOperationMutex.lock.withLock {
                val appContext = context.applicationContext
                val sessionId = TmdbAuthStore.sessionId(appContext)
                val accountId = TmdbAuthStore.accountId(appContext)
                if (sessionId.isNullOrBlank() || accountId == null) {
                    throw IOException("TMDB kullanıcı oturumu bağlı değil")
                }
                val db = KitsugiDatabase.getDatabase(appContext)
                val repository = MediaEntryRepository(db.mediaEntryDao(), db.pendingSyncDao(), appContext)
                val snapshot = TmdbAccountSyncCoordinator.refreshLocked(appContext, repository)
                if (!TmdbAuthStore.matchesSession(appContext, sessionId, accountId)) {
                    throw IOException("TMDB hesabı bildirim snapshot'ı sırasında değişti")
                }
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val preferenceKey = tmdbSnapshotPreferenceKey(accountId)
                val previous = prefs.getStringSet(preferenceKey, null)
                val current = tmdbSnapshotFingerprints(snapshot)
                val newEntries = newTmdbAccountEntries(previous, snapshot)

                val body = appContext.getString(R.string.notification_tmdb_account_item_added)
                val archived = newEntries.map { entry ->
                    NotificationArchiveStore.ArchivedNotif(
                        id = "tmdb_account_${accountId}_added_${entry.accountKey()}",
                        source = "TMDB",
                        title = entry.title,
                        body = body,
                        tsMs = snapshot.fetchedAtMs,
                        imageUrl = entry.imageUrl,
                        mediaId = entry.tmdbId,
                        mediaType = when (entry.type) {
                            com.kitsugi.animelist.model.MediaType.Movie -> "movie"
                            com.kitsugi.animelist.model.MediaType.TvShow -> "tv"
                            com.kitsugi.animelist.model.MediaType.Manga -> "manga"
                            else -> "anime"
                        },
                        mediaTitle = entry.title,
                        typeIdentifier = "ACCOUNT_ADDITION"
                    )
                }
                val result = Result(snapshot, newEntries, archived)

                // No ack before the caller has shown and durably archived every new item.
                deliver(result)
                if (!TmdbAuthStore.matchesSession(appContext, sessionId, accountId)) {
                    throw IOException("TMDB hesabı bildirim teslimi sırasında değişti")
                }
                val saved = prefs.edit().putStringSet(preferenceKey, current).commit()
                if (!saved) throw IOException("TMDB bildirim baseline kaydedilemedi")
                result
            }
        }
    }
}

suspend fun archiveTmdbAccountNotifications(
    context: Context,
    notifications: List<NotificationArchiveStore.ArchivedNotif>
) {
    if (notifications.isEmpty()) return
    NotificationArchiveStore.mergeSource(
        ctx = context.applicationContext,
        source = "TMDB",
        fresh = notifications
    )
}
