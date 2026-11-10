package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bangumi infobox'ından gelen **serbest metin** kurum adlarını kurumun kendi küresel adına
 * çözer (stüdyo / yapımcı / yayıncı ağ / yayımcı / dergi).
 *
 * Neden gerekli:
 *  Detay sayfasındaki kurum adları `动画制作` / `製作` / `播放电视台` / `出版社` / `连载杂志`
 *  infobox alanlarından **serbest metin** olarak gelir (kullanıcı girer, kimlik taşımaz):
 *  `株式会社マッパ`, `集英社`, `TBSテレビ` ... Sözlük ([com.kitsugi.animelist.utils.BangumiOrgDictionary])
 *  yalnızca bilinen adları kapsar; Bangumi'de binlerce kurum kaydı olduğu için sözlükle
 *  "tamamı"na ulaşmak mümkün değildir.
 *
 *  Bangumi'de kurumlar ayrı bir kayıt türü değil, 人物 kayıtlarının `type = 2` (公司) alt
 *  kümesidir ve arama yanıtındaki `name` alanı kurumun **özgün/küresel** adıdır
 *  (`name_cn` ise Çince yerelleştirmedir). Yani tek bir arama isteği, sözlükte olmayan bir
 *  kurum için bile küresel adı verir.
 *
 * Kurallar:
 *  • **Ek katmandır, davranış bozmaz.** Çözüm bulunamazsa ya da ağ/hata olursa ad olduğu
 *    gibi döner; sayfa açılışı asla bu adıma takılmaz ([TIMEOUT_MS] tavanı var).
 *  • **Uydurma ad üretilmez.** Yalnızca Bangumi'nin kendi kaydettiği ad kullanılır; aday
 *    adlar [StudioSourceSupport.namesMatch] ile doğrulanır.
 *  • Sonuçlar (bulunamayanlar dahil) önbelleğe alınır; aynı ad için ikinci istek atılmaz.
 *  • Gösterim katmanındaki yerelleştirme ([com.kitsugi.animelist.utils.toLocalizedOrgName])
 *    bundan bağımsız çalışmaya devam eder; arama ve kimlik doğrulama daima Latin adla yürür.
 */
internal object BangumiOrgResolver {

    /** Bangumi 人物 kayıt türü: 1 = 个人 (kişi), 2 = 公司 (şirket), 3 = 组合 (grup). */
    private const val TYPE_COMPANY = 2

    /**
     * Detay sayfası bu adıma takılmasın diye toplam süre tavanı.
     *
     * Yalnızca **CJK** kurum adları çözülür; zaten Latin olan adlar için istek atılmaz.
     * Ekranı açan çağrı bu adımı beklediği için tavan 2,5 sn'den 1,2 sn'ye indirildi.
     */
    private const val TIMEOUT_MS = 1_200L

    /** Kurum başına istek tavanı (çözüm bulunamazsa tekrar denenmez). */
    private const val SEARCH_LIMIT = 5

    /**
     * Normalleştirilmiş kurum adı → çözülen küresel ad.
     * Boş metin "çözülemedi" anlamındadır (negatif önbellek).
     */
    // Includes negative lookups; cap the process-lifetime cache so browsing new titles cannot grow it forever.
    private val cache = BoundedCache<String, String>("bangumi.orgResolver", 1_000)

    /**
     * Bir detayın tüm kurum adlarını çözer. Kurum listeleri eşzamanlı çözülür; toplam süre
     * [TIMEOUT_MS] ile sınırlıdır. Hiçbir ad değişmezse aynı örnek döner (gereksiz kopya yok).
     */
    suspend fun resolveDetailOrgNames(detail: KitsugiMediaDetail): KitsugiMediaDetail =
        coroutineScope {
            val targets = (detail.studios + detail.producers + detail.networks + detail.serializations)
                .map { it.name }
                .filter { it.isNotBlank() && PreferenceHelpers.hasCjkCharacters(it) }
                .distinct()
            val pending = targets.filter { cache[cacheKey(it)] == null }
            if (pending.isEmpty()) return@coroutineScope applyCache(detail)

            val resolved = withTimeoutOrNull(TIMEOUT_MS) {
                pending.map { name ->
                    async(Dispatchers.IO) { name to (resolveOne(name) ?: "") }
                }.awaitAll()
            }
            if (resolved != null) {
                for ((name, value) in resolved) cache[cacheKey(name)] = value
            }
            // Zaman aşımında önbelleğe yazılmaz: sonraki açılışta yeniden denenir.
            applyCache(detail)
        }

    /** Tek bir adı çözer; bulunamazsa null. Ağ hatası yutulur (sayfa açılışı bozulmasın). */
    private suspend fun resolveOne(rawName: String): String? = runCatching {
        val page = BangumiApiClient.searchPersons(
            keyword = rawName,
            type = TYPE_COMPANY,
            limit = SEARCH_LIMIT
        )
        val candidates = page.data
        if (candidates.isEmpty()) return@runCatching null

        // 1) Ad birebir eşleşmeli: özgün ad ya da Çince ad üzerinden.
        val exact = candidates.firstOrNull { entity ->
            entity.type == TYPE_COMPANY &&
                (
                    StudioSourceSupport.namesMatch(rawName, entity.name) ||
                        (entity.nameCn.isNotBlank() && StudioSourceSupport.namesMatch(rawName, entity.nameCn))
                    )
        }
        // 2) Birebir eşleşme yoksa ilk şirket kaydı; o da yoksa çözüm yok.
        val chosen = exact ?: candidates.firstOrNull { it.type == TYPE_COMPANY } ?: return@runCatching null

        chosen.name.trim().takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Önbellekteki çözümleri detaya uygular. */
    private fun applyCache(detail: KitsugiMediaDetail): KitsugiMediaDetail {
        var changed = false
        val studios = detail.studios.map { resolveCached(it).also { r -> if (r !== it) changed = true } }
        val producers = detail.producers.map { resolveCached(it).also { r -> if (r !== it) changed = true } }
        val networks = detail.networks.map { resolveCached(it).also { r -> if (r !== it) changed = true } }
        val serializations = detail.serializations.map { resolveCached(it).also { r -> if (r !== it) changed = true } }
        if (!changed) return detail
        return detail.copy(
            studios = studios,
            producers = producers,
            networks = networks,
            serializations = serializations
        )
    }

    private fun resolveCached(studio: KitsugiStudio): KitsugiStudio {
        val resolved = cache[cacheKey(studio.name)] ?: return studio
        if (resolved.isBlank() || resolved == studio.name) return studio
        return studio.copy(name = resolved)
    }

    /**
     * Önbellek anahtarı. [StudioSourceSupport.normalizeName] ile aynı normalizasyon
     * kullanılır; böylece `株式会社マッパ` ve `マッパ` aynı anahtara düşmez ama
     * büyük/küçük harf ve boşluk farkları aynı anahtarı verir.
     */
    private fun cacheKey(name: String): String =
        StudioSourceSupport.normalizeName(name).ifBlank { name.trim() }

    /** Test/araç kullanımı için önbelleği sıfırlar. */
    internal fun clearCacheForTesting() = cache.clear()

    /** Çözüm tavanını aşan listelerde gereksiz istek atılmasın diye tek tek çözüm. */
    suspend fun resolveName(rawName: String): String? = withContext(Dispatchers.IO) {
        if (rawName.isBlank()) return@withContext null
        cache[cacheKey(rawName)]?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val resolved = withTimeoutOrNull(TIMEOUT_MS) { resolveOne(rawName) } ?: return@withContext null
        cache[cacheKey(rawName)] = resolved
        resolved
    }
}
