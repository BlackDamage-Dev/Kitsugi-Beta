package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kitsu kütüphanesini içe aktarma yöneticisi.
 */
object KitsuImportManager {
    private const val KITSU_OFFSET = 300_000_000

    data class KitsuUserProfile(
        val name: String,
        val avatarUrl: String?
    )

    suspend fun fetchUserProfile(token: String): KitsuUserProfile {
        return withContext(Dispatchers.IO) {
            val user = KitsuApiClient.getCurrentUser(token)
            KitsuUserProfile(name = user.name, avatarUrl = user.avatarUrl)
        }
    }

    /** Kütüphanenin TAMAMINI çeker (tam uzlaştırma turu). */
    suspend fun fetchAllLists(context: Context, token: String, userId: String): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val entries = try {
                KitsuApiClient.fetchAllLibraryEntries(token, userId)
            } catch (e: Exception) {
                val isAuth = e.message?.contains("HTTP 401", ignoreCase = true) == true
                if (!isAuth) throw e
                val refreshed = ExternalAuthManager.forceRefreshKitsuToken(context)
                if (refreshed.isNullOrBlank()) {
                    ExternalAuthManager.markSessionExpired(context, "kitsu")
                    throw IllegalStateException("Kitsu oturumu sona erdi (HTTP 401)", e)
                }
                KitsuApiClient.fetchAllLibraryEntries(refreshed, userId)
            }
            mapEntries(context, entries)
        }
    }

    /**
     * **Delta (fark) çekimi**: yalnızca [sinceEpochSeconds]'ten sonra değişen kayıtlar.
     *
     * Kitsu `library-entries?filter[since]=…` süzgecini destekler (ISO-8601). Bu sayede
     * tek bir bölüm işaretlemesi için 500'lük sayfaların tamamı çekilmez.
     * Delta silmeleri göremez → çağıran `allowDelete = false` ile birleştirmelidir.
     */
    suspend fun fetchAllListsSince(
        context: Context,
        token: String,
        userId: String,
        sinceEpochSeconds: Long
    ): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            // Kitsu tam ISO-8601 bekler; belgelerdeki örnek biçim milisaniyeli:
            // "2013-03-25T00:00:00.000Z". `Instant.toString()` kesirli saniyeyi atabildiği
            // için sabit biçimleyici kullanıyoruz (UTC).
            val sinceIso = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(java.time.ZoneOffset.UTC)
                .format(java.time.Instant.ofEpochSecond(sinceEpochSeconds.coerceAtLeast(1L)))
            mapEntries(context, KitsuApiClient.fetchLibraryEntriesSince(token, userId, sinceIso))
        }
    }

    private fun mapEntries(
        context: Context,
        entries: List<KitsuApiClient.KitsuLibraryEntry>
    ): List<MediaEntry> {
        val byMediaKey = LinkedHashMap<String, MediaEntry>(entries.size)

        for (entry in entries) {
            val isManga = entry.mangaId != null
            val mediaId = (if (isManga) entry.mangaId else entry.animeId) ?: continue
            val isAnime = !isManga
            val mediaType = if (isManga) MediaType.Manga else MediaType.Anime

            // Cache entry ID for later updates/deletes
            ExternalAuthManager.saveKitsuLibraryEntryId(context, mediaId, isAnime, entry.id)

            val kitsuStableId = KITSU_OFFSET + mediaId
            val validMalId = entry.realMalId?.takeIf { it in 1..99_999_999 }
            val validAniListId = entry.realAniListId?.takeIf { it > 0 }
            if (validMalId != null) {
                ExternalAuthManager.saveKitsuMediaIdForMal(context, validMalId, isAnime, mediaId)
            }
            if (validMalId != null || validAniListId != null) {
                CrossProviderIdentityCache.writeBundle(
                    context = context,
                    type = mediaType,
                    ids = CrossProviderIds(
                        malId = validMalId,
                        aniListId = validAniListId,
                        kitsuId = mediaId
                    ),
                    complete = false,
                    primaryKey = "${mediaType.name}:kitsu:$mediaId"
                )
            }
            val status = KitsuSyncManager.kitsuStatusToWatchStatus(entry.status)
            val score = entry.ratingTwenty?.let { kotlin.math.round(it / 2.0).toInt().coerceIn(1, 10) }

            val domainEntry = MediaEntry(
                id = 0,
                title = entry.title,
                subtitle = "",
                titleEnglish = entry.titleEnglish,
                titleJapanese = entry.titleJapanese,
                imageUrl = entry.imageUrl ?: "",
                type = mediaType,
                status = status,
                progress = entry.progress,
                total = entry.total,
                score = score,
                isAdult = entry.isAdult,
                malId = kitsuStableId, // her zaman Kitsu stableId (300M aralığı)
                aniListEntryId = null,
                source = "kitsu",
                notes = entry.notes,
                isPrivate = entry.isPrivate,
                startDate = entry.startedAt,
                endDate = entry.finishedAt,
                isRepeating = entry.reconsuming,
                repeatCount = entry.reconsumeCount,
                updatedAt = entry.updatedAt,
                addedAt = entry.createdAt,
                // Yıl olmadan kimlik doğrulaması yapılamıyor; aynı isimli yapımlar
                // çapraz eşitlemede "inceleme gerekli" diye atlanıyordu.
                year = entry.startYear
            )
            val dedupeKey = "${mediaType.name}:$mediaId"
            val prev = byMediaKey[dedupeKey]
            if (prev == null || domainEntry.updatedAt >= prev.updatedAt) {
                byMediaKey[dedupeKey] = domainEntry
            }
        }
        return byMediaKey.values.toList()
    }
}
