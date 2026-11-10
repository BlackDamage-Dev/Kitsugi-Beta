package com.kitsugi.animelist.data.remote

import android.content.Context
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.core.memory.boundedSet
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType

/**
 * Kitsu kütüphane kayıtları için doğru Kitsu stable ID'sini (kitsuNumericId + 300_000_000) çözer.
 *
 * KitsuImportManager, gerçek MAL ID biliniyorsa `malId` alanına onu yazar; bilinmiyorsa
 * offset'li stable ID'yi saklar. Detay/sekmeler ise `malId`'yi doğrudan Kitsu ID gibi
 * kullanıyordu — bu da Listem > Kitsu sekmesinde alakasız yapımların açılmasına
 * neden oluyordu (örn. MAL 21 → Kitsu 21 tamamen farklı bir yapımdır).
 *
 * Çözüm sırası:
 *  1. `malId` zaten offset'li stable ID ise doğrudan kullanılır.
 *  2. İçe aktarma sırasında saklanan yerel MAL → Kitsu eşlemesi.
 *  3. Kitsu mappings API (`myanimelist/anime|manga` → Kitsu ID).
 *  4. Başlık + yıl ile Kitsu araması (son çare).
 *
 * Hiçbir yoldan çözülemezse null döner; çağıran taraf MAL (Jikan) üzerinden
 * doğru yapımı getirmelidir — asla rastgele bir Kitsu ID ile veri çekmemeli.
 */
object KitsuEntryIdResolver {
    const val KITSU_OFFSET = 300_000_000

    /** raw MAL ID → çözülmüş stable Kitsu ID (process belleği) */
    private val memoryCache = BoundedCache<Int, Int>("kitsu.entryId", 3000)
    /** Çözülemeyen MAL ID'ler — tekrar tekrar ağ çağrısı yapmamak için */
    private val failedIds = boundedSet<Int>("kitsu.entryId.failed", 3_000)

    fun stableIdOf(kitsuNumericId: Int): Int = KITSU_OFFSET + kitsuNumericId

    fun numericIdOf(stableOrRawId: Int): Int =
        if (stableOrRawId >= KITSU_OFFSET) stableOrRawId - KITSU_OFFSET else stableOrRawId

    /** Entry'nin `malId` değeri offset'li stable Kitsu ID ise true */
    fun isStableKitsuId(id: Int?): Boolean =
        id != null && id in (KITSU_OFFSET + 1)..(KITSU_OFFSET + 99_999_999)

    /** Suspend olmayan bakış — loadEntry ön bellek anahtarları için. */
    fun peek(rawMalId: Int?): Int? = rawMalId?.let { memoryCache[it] }

    /**
     * Kitsu kaynaklı bir [MediaEntry] için doğru Kitsu stable ID'sini döndürür.
     * Çözülemezse null döner.
     */
    suspend fun resolveStableId(entry: MediaEntry, context: Context?): Int? {
        val raw = entry.malId ?: return null
        if (isStableKitsuId(raw)) return raw
        if (raw <= 0) return null
        if (failedIds.contains(raw)) return null

        memoryCache[raw]?.let { return it }

        val isAnime = entry.type != MediaType.Manga

        // 1. İçe aktarma sırasında saklanan yerel MAL → Kitsu eşlemesi
        if (context != null) {
            val cached = runCatching {
                ExternalAuthManager.getKitsuMediaIdForMal(context, raw, isAnime)
            }.getOrNull()
            if (cached != null && cached > 0) {
                return rememberStable(raw, cached, context, isAnime)
            }
        }

        // 2. Kitsu mappings API (myanimelist/anime|manga → Kitsu ID)
        val viaMapping = runCatching {
            KitsuApiClient.lookupKitsuIdByExternalMapping(
                if (isAnime) "myanimelist/anime" else "myanimelist/manga",
                raw
            )
        }.getOrNull()
        if (viaMapping != null && viaMapping > 0) {
            return rememberStable(raw, viaMapping, context, isAnime)
        }

        // 3. Başlık + yıl ile Kitsu araması (son çare)
        val viaTitle = runCatching {
            KitsuApiClient.lookupKitsuId(entry.title, isAnime, entry.year)
        }.getOrNull()
        if (viaTitle != null && viaTitle > 0) {
            return rememberStable(raw, viaTitle, context, isAnime)
        }

        failedIds.add(raw)
        return null
    }

    private fun rememberStable(rawMalId: Int, kitsuNumericId: Int, context: Context?, isAnime: Boolean): Int {
        val stable = stableIdOf(kitsuNumericId)
        memoryCache[rawMalId] = stable
        failedIds.remove(rawMalId)
        if (context != null) {
            runCatching {
                ExternalAuthManager.saveKitsuMediaIdForMal(context, rawMalId, isAnime, kitsuNumericId)
            }
        }
        return stable
    }
}
