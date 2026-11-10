package com.kitsugi.animelist.data.auth

import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Bangumi subject NSFW bayrağını, koleksiyon API'sinin taşımadığı durumda detay uçlarından çözer.
 * Başarılı pozitif ve negatif sonuçlar bellekte sınırlı tutulur; ağ hataları önbelleğe alınmaz.
 */
internal object BangumiAdultResolver {
    private const val TAG = "BangumiAdultResolver"
    private const val PARALLEL_LOOKUPS = 6

    private val cache = BoundedCache<Int, Boolean>("bangumi.adult", 3000)

    /**
     * Başarılı şekilde çözülen subject ID'leri için `nsfw` bayrağını döndürür.
     * Ağ hatası alan ID'ler sonuç haritasında bulunmaz; böylece sonraki tarama yeniden dener.
     */
    suspend fun resolveAdultFlags(token: String?, subjectIds: Collection<Int>): Map<Int, Boolean> {
        if (token.isNullOrBlank()) return emptyMap()

        val wanted = subjectIds.filter { it > 0 }.distinct()
        if (wanted.isEmpty()) return emptyMap()

        val result = HashMap<Int, Boolean>(wanted.size)
        val pending = mutableListOf<Int>()
        wanted.forEach { subjectId ->
            val cached = cache[subjectId]
            if (cached == null) pending += subjectId else result[subjectId] = cached
        }

        for (chunk in pending.chunked(PARALLEL_LOOKUPS)) {
            val fetched = coroutineScope {
                chunk.map { subjectId ->
                    async(Dispatchers.IO) {
                        val isAdult = try {
                            BangumiApiClient.getSubject(subjectId, token).nsfw
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Log.w(TAG, "NSFW bayrağı çözülemedi (subject=$subjectId): ${error.message}")
                            null
                        }
                        subjectId to isAdult
                    }
                }.awaitAll()
            }

            fetched.forEach { (subjectId, isAdult) ->
                if (isAdult != null) {
                    cache[subjectId] = isAdult
                    result[subjectId] = isAdult
                }
            }
        }

        return result
    }

    /** Oturum kapanışlarında veya testlerde bellekteki sonuçları sıfırlar. */
    fun clearCache() = cache.clear()
}
