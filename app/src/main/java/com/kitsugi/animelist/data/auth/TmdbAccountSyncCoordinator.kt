package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.remote.TmdbAccountClient
import com.kitsugi.animelist.data.remote.TmdbAccountSnapshot
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Single snapshot/merge path shared by manual refresh, profile and background work. */
object TmdbAccountSyncCoordinator {
    suspend fun refresh(
        context: Context,
        repository: MediaEntryRepository,
        fallbackEntries: List<MediaEntry> = emptyList(),
        allowRemoteDelete: Boolean? = null
    ): TmdbAccountSnapshot = TmdbAccountOperationMutex.lock.withLock {
        refreshLocked(context, repository, fallbackEntries, allowRemoteDelete)
    }

    /** Caller holds [TmdbAccountOperationMutex], e.g. notification delivery + baseline commit. */
    internal suspend fun refreshLocked(
        context: Context,
        repository: MediaEntryRepository,
        fallbackEntries: List<MediaEntry> = emptyList(),
        allowRemoteDelete: Boolean? = null
    ): TmdbAccountSnapshot {
        val sessionId = TmdbAuthStore.sessionId(context)
        val accountId = TmdbAuthStore.accountId(context)
        if (sessionId.isNullOrBlank() || accountId == null) {
            throw IOException("TMDB kullanıcı oturumu bağlı değil")
        }
        val snapshot = TmdbAccountClient.fetchAccountSnapshot(sessionId, accountId)
        if (!TmdbAuthStore.matchesSession(context, sessionId, accountId)) {
            throw IOException("TMDB hesabı snapshot sırasında değişti; eski hesap verisi uygulanmadı")
        }
        val latestEntries = runCatching { repository.entriesFlow.first() }
            .getOrDefault(fallbackEntries)
        val merged = mergeTmdbAccountSnapshot(snapshot.entries, latestEntries)
        val syncEnabled = runCatching {
            SettingsDataStore(context).settingsFlow.first().syncEnabledTmdb
        }.getOrDefault(true)
        // Only a complete snapshot may delete. Pending local intent and disabled sync make
        // the operation additive, preventing a remote refresh from erasing user work.
        val allowDelete = allowRemoteDelete ?: (syncEnabled && !repository.hasPendingSyncJobs())
        if (!TmdbAuthStore.matchesSession(context, sessionId, accountId)) {
            throw IOException("TMDB hesabı yerel birleştirme öncesinde değişti; eski hesap verisi uygulanmadı")
        }
        repository.smartImport(TMDB_ACCOUNT_SOURCE, merged, allowDelete = allowDelete)
        repository.entriesFlow.first()
            .filter { isTmdbAccountSource(it.source) }
            .forEach { repository.mirrorTmdbAccountFieldsToSiblings(it) }
        return snapshot
    }
}
