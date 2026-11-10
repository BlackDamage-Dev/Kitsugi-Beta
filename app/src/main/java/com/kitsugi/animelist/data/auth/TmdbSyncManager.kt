package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.data.local.toDomain
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import com.kitsugi.animelist.data.remote.TmdbAccountClient
import com.kitsugi.animelist.model.MediaEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Writes only explicitly changed native TMDB states; Kitsugi-only fields stay local. */
object TmdbSyncManager {
    data class SyncResult(
        val errors: List<String> = emptyList(),
        val unresolvedMedia: Boolean = false,
        val accountMismatch: Boolean = false
    ) {
        val isSuccess: Boolean get() = errors.isEmpty() && !unresolvedMedia && !accountMismatch
    }

    suspend fun syncEntryToTmdb(
        context: Context,
        entry: MediaEntry,
        fields: TmdbWriteFields = TmdbWriteFields.All,
        expectedAccountId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        TmdbAccountOperationMutex.lock.withLock {
            if (!fields.any) return@withLock SyncResult()
            val sessionId = TmdbAuthStore.sessionId(context)
            val accountId = TmdbAuthStore.accountId(context)
            if (sessionId.isNullOrBlank() || accountId == null) {
                return@withLock SyncResult(errors = listOf("TMDB kullanıcı oturumu bağlı değil"))
            }
            if (expectedAccountId != null && accountId != expectedAccountId) {
                return@withLock SyncResult(accountMismatch = true)
            }
            val tmdbId = entry.tmdbId
            if (!supportsTmdbAccountType(entry.type) || tmdbId == null || tmdbId <= 0) {
                return@withLock SyncResult(
                    unresolvedMedia = true,
                    errors = listOf("TMDB için doğrulanmış film/TV kimliği çözülemedi")
                )
            }
            try {
                val existingAccountEntry = if (!isTmdbAccountSource(entry.source) && (fields.watchlist || fields.rating)) {
                    com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context)
                        .mediaEntryDao().getAll()
                        .filter {
                            isTmdbAccountSource(it.source) && it.tmdbId == tmdbId && it.type == entry.type.name
                        }
                        .maxByOrNull { it.updatedAt }
                        ?.toDomain()
                } else null
                val nativeState = if (fields.watchlist || fields.rating) {
                    resolveTmdbNativeWriteState(entry, existingAccountEntry)
                } else null

                val remoteState = TmdbAccountClient.fetchAccountState(sessionId, entry.type, tmdbId)
                ensureSessionCurrent(context, sessionId, accountId)
                val pendingFields = fields.onlyChangesFrom(
                    remoteWatchlist = remoteState.watchlist,
                    remoteFavorite = remoteState.favorite,
                    remoteRating = remoteState.rating,
                    desiredWatchlist = nativeState?.watchlist ?: false,
                    desiredFavorite = entry.isFavorite,
                    desiredRating = nativeState?.rating
                )
                if (!pendingFields.any) return@withLock SyncResult()

                // Sequential official calls share the snapshot lock. A failed later field is
                // retried with the same mask; earlier calls are idempotent.
                if (pendingFields.watchlist) {
                    ensureSessionCurrent(context, sessionId, accountId)
                    TmdbAccountClient.setWatchlist(
                        sessionId, accountId, entry.type, tmdbId, nativeState!!.watchlist
                    )
                }
                if (pendingFields.favorite) {
                    ensureSessionCurrent(context, sessionId, accountId)
                    TmdbAccountClient.setFavorite(
                        sessionId, accountId, entry.type, tmdbId, entry.isFavorite
                    )
                }
                if (pendingFields.rating) {
                    ensureSessionCurrent(context, sessionId, accountId)
                    TmdbAccountClient.setRating(
                        sessionId, entry.type, tmdbId, nativeState!!.rating
                    )
                }
                SyncResult()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                PlatformFailureRegistry.reportAndResolve("tmdb", error)
                SyncResult(errors = listOf(error.message ?: "TMDB yazma hatası"))
            }
        }
    }

    /** Explicit local delete clears all three native states so the row is not re-imported. */
    suspend fun deleteEntryFromTmdb(
        context: Context,
        entry: MediaEntry,
        expectedAccountId: Int? = null
    ): SyncResult =
        withContext(Dispatchers.IO) {
            TmdbAccountOperationMutex.lock.withLock {
                val sessionId = TmdbAuthStore.sessionId(context)
                val accountId = TmdbAuthStore.accountId(context)
                if (sessionId.isNullOrBlank() || accountId == null) {
                    return@withLock SyncResult(errors = listOf("TMDB kullanıcı oturumu bağlı değil"))
                }
                if (expectedAccountId != null && accountId != expectedAccountId) {
                    return@withLock SyncResult(accountMismatch = true)
                }
                val tmdbId = entry.tmdbId
                if (!supportsTmdbAccountType(entry.type) || tmdbId == null || tmdbId <= 0) {
                    return@withLock SyncResult(
                        unresolvedMedia = true,
                        errors = listOf("TMDB için doğrulanmış film/TV kimliği çözülemedi")
                    )
                }
                try {
                    val remoteState = TmdbAccountClient.fetchAccountState(sessionId, entry.type, tmdbId)
                    ensureSessionCurrent(context, sessionId, accountId)
                    if (remoteState.watchlist) {
                        TmdbAccountClient.setWatchlist(sessionId, accountId, entry.type, tmdbId, false)
                        ensureSessionCurrent(context, sessionId, accountId)
                    }
                    if (remoteState.favorite) {
                        TmdbAccountClient.setFavorite(sessionId, accountId, entry.type, tmdbId, false)
                        ensureSessionCurrent(context, sessionId, accountId)
                    }
                    if (remoteState.rating != null) {
                        TmdbAccountClient.setRating(sessionId, entry.type, tmdbId, null)
                    }
                    SyncResult()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    PlatformFailureRegistry.reportAndResolve("tmdb", error)
                    SyncResult(errors = listOf(error.message ?: "TMDB silme hatası"))
                }
            }
        }

    private fun ensureSessionCurrent(context: Context, sessionId: String, accountId: Int) {
        if (!TmdbAuthStore.matchesSession(context, sessionId, accountId)) {
            throw java.io.IOException("TMDB hesabı yazma sırasında değişti; kalan alanlar gönderilmedi")
        }
    }
}
