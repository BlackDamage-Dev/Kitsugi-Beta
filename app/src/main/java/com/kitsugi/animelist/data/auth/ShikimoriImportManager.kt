package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shikimori.one kütüphanesini içe aktarma yöneticisi.
 */
object ShikimoriImportManager {
    private const val TAG = "ShikimoriImportManager"

    data class ShikimoriUserProfile(
        val id: Int,
        val nickname: String,
        val avatarUrl: String?
    )

    suspend fun fetchUserProfile(token: String): ShikimoriUserProfile {
        return withContext(Dispatchers.IO) {
            val user = ShikimoriApiClient.getCurrentUser(token)
            ShikimoriUserProfile(id = user.id, nickname = user.nickname, avatarUrl = user.avatarUrl)
        }
    }

    suspend fun fetchAllLists(context: Context, token: String, userId: Int): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val rates = fetchRatesWithRetry(context, token, userId)
            val unresolvedAnimeIds = rates.asSequence()
                .filter { it.targetType == "Anime" && it.ageRating.isNullOrBlank() && !it.isAdult }
                .map { it.targetId }
                .distinct()
                .toList()
            val unresolvedMangaIds = rates.asSequence()
                .filter { it.targetType == "Manga" && it.ageRating.isNullOrBlank() && !it.isAdult }
                .map { it.targetId }
                .distinct()
                .toList()
            // `user_rates` yanıtındaki gömülü anime/manga nesneleri `AnimeSerializer`/
            // `MangaSerializer` ile üretilir ve `rating`/`genres` alanlarını taşımaz;
            // bu yüzden +18 bilgisi GraphQL üzerinden toplu çözülür (ShikimoriAdultResolver).
            // Shikimori geçici olarak bu isteği reddederse import yine de devam eder.
            val adultAnimeIds = runCatching {
                com.kitsugi.animelist.data.remote.ShikimoriAdultResolver.resolveAdultIds(
                    com.kitsugi.animelist.data.remote.ShikimoriAdultResolver.Kind.ANIME,
                    unresolvedAnimeIds
                )
            }.getOrElse { error ->
                Log.w(TAG, "Shikimori anime adult metadata lookup failed: ${error.message}")
                emptySet()
            }
            val adultMangaIds = runCatching {
                com.kitsugi.animelist.data.remote.ShikimoriAdultResolver.resolveAdultIds(
                    com.kitsugi.animelist.data.remote.ShikimoriAdultResolver.Kind.MANGA,
                    unresolvedMangaIds
                )
            }.getOrElse { error ->
                Log.w(TAG, "Shikimori manga adult metadata lookup failed: ${error.message}")
                emptySet()
            }
            val result = mutableListOf<MediaEntry>()

            for (rate in rates) {
                // Shikimori targetId doğrudan MAL ID'dir
                val malId = rate.targetId
                val isManga = rate.targetType == "Manga"

                // Cache Shikimori user_rate ID for future updates/deletes
                ExternalAuthManager.saveShikimoriRateId(context, malId, rate.targetType, rate.id)

                val status = ShikimoriSyncManager.shikimoriStatusToWatchStatus(rate.status)
                val progress = if (isManga) rate.chapters else rate.episodes

                result.add(
                    MediaEntry(
                        id = 0,
                        title = rate.title,
                        subtitle = "",
                        titleEnglish = rate.titleEnglish,
                        titleJapanese = rate.titleJapanese,
                        imageUrl = rate.imageUrl ?: "",
                        type = if (isManga) MediaType.Manga else MediaType.Anime,
                        status = status,
                        progress = progress,
                        total = rate.total,
                        score = rate.score.takeIf { it > 0 },
                        isAdult = rate.isAdult || when (rate.targetType) {
                            "Anime" -> rate.targetId in adultAnimeIds
                            "Manga" -> rate.targetId in adultMangaIds
                            else -> false
                        },
                        malId = malId,
                        aniListEntryId = null,
                        source = "shikimori",
                        notes = rate.notes,
                        // Shikimori'de tekrar durumu ayrı bir `status` değeridir ("rewatching");
                        // `rewatches` ise TAMAMLANMIŞ tekrar sayısıdır. Bayrağı sayıdan türetmek
                        // bitmiş yapımları "şu an tekrar izleniyor" gibi göstermeye yol açardı.
                        isRepeating = status == WatchStatus.Repeating,
                        repeatCount = rate.rewatches,
                        volumeProgress = if (isManga) rate.volumes else 0,
                        updatedAt = rate.updatedAt,
                        addedAt = rate.createdAt,
                        year = rate.startYear
                    )
                )
            }
            result
                .groupBy { "${it.type.name}:${it.malId ?: it.title}" }
                .values
                .map { dupes -> dupes.maxByOrNull { it.updatedAt } ?: dupes.first() }
        }
    }

    /**
     * Shikimori liste çekimini dener; HTTP 401 (token sunucu tarafında iptal
     * edilmiş ya da süresi dolmuş ama yerel `expiresAt` güncellenmemiş) alırsa
     * refresh token ile zorla yeni bir access token alır ve bir kez daha dener.
     */
    private suspend fun fetchRatesWithRetry(
        context: Context,
        token: String,
        userId: Int
    ): List<ShikimoriApiClient.ShikimoriRate> {
        return try {
            ShikimoriApiClient.fetchAllUserRates(token, userId)
        } catch (e: Exception) {
            // Sadece HTTP 401 hatasında retry yap; diğer hatalar olduğu gibi fırlatılır.
            if (!e.message.orEmpty().contains("HTTP 401")) throw e

            Log.w(TAG, "fetchAllUserRates 401 – token yenileme deneniyor", e)
            val newToken = ExternalAuthManager.forceRefreshShikimoriToken(context)
            if (newToken.isNullOrBlank()) {
                Log.e(TAG, "Token yenilenemedi, Shikimori oturumu sona ermiş olabilir")
                throw Exception("Shikimori oturumu sona erdi (HTTP 401). Lütfen Shikimori hesabıyla tekrar giriş yapın.")
            }
            Log.i(TAG, "Shikimori token yenilendi, liste yeniden çekiliyor...")
            ShikimoriApiClient.fetchAllUserRates(newToken, userId)
        }
    }
}
