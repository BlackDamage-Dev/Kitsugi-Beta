package com.kitsugi.animelist.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Bangumi **karakter ve kişi (seiyuu / staff) adları** için ortak Latin (romaji / İngilizce)
 * ad çözümleyicisi.
 *
 * Sorun: Bangumi'nin karakter/kişi uçları (`/v0/characters/{id}`, `/v0/persons/{id}`,
 * `/v0/persons/{id}/characters` …) adları çoğunlukla yalnızca özgün (Japonca/Çince) biçimde
 * döndürür; infobox'ta romaji / İngilizce alanı çok seyrektir. Bu yüzden uygulamanın başlık
 * dili tercihi (İngilizce / Romaji) karakter ve kişi adlarında işlemez, ekran CJK adla kalır.
 *
 * Çözüm: AniList'in herkese açık GraphQL ucunda karakter (`characters(search:)`) ve kadro
 * (`staff(search:)`) araması yapılıp, adayın `name.native` / `name.full` / `name.alternative`
 * alanlarından biri Bangumi'nin özgün adıyla BİREBİR (normalize) eşleşirse ad varyantları
 * alınır. Kısmi / bulanık eşleşme asla kabul edilmez — yanlış kişinin adı gösterilmez.
 *
 * Kademe düzeni (diğer çözümleyicilerle aynı sözleşme):
 *  1. Kalıcı önbellek ([prefs]) — daha önce çözülen ad için ağ isteği yapılmaz.
 *  2. AniList toplu (aliased) arama — tek istekte birden çok ad.
 *  3. Olumsuz önbellek — arama gerçekten yapıldıysa ve Latin ad yoksa yazılır; böylece aynı
 *     ad için her ekranda yeniden arama yapılmaz. Ağ hatası / zaman aşımı olumsuz sayılmaz.
 *
 * Tüm sonuçlar kalıcı yazılır; böylece kişi detayında çözülen bir karakter adı, konu kadrosu
 * sekmesinde de ağ isteği olmadan Latin görünür.
 */
internal object BangumiEntityNameResolver {

    private const val TAG = "BangumiEntityNameResolver"

    internal enum class Kind { CHARACTER, PERSON }

    /** Bangumi_entity kimliği + eşleme için özgün (CJK) ad ve yedek olarak liste adı. */
    internal data class Request(
        val kind: Kind,
        val rawId: Int,
        val nativeName: String?,
        val fallbackName: String? = null
    )

    /** Çözülmüş Latin ad varyantları. `native` yalnızca yedek / NATIVE tercihi için tutulur. */
    internal data class NameVariants(
        val romaji: String?,
        val english: String?,
        val native: String?
    ) {
        val hasLatin: Boolean get() = romaji != null || english != null
    }

    private const val PREFS_NAME = "bangumi_entity_name_cache_v1"
    private const val MAX_ENTRIES = 1200
    private const val NEGATIVE_TTL_MS = 6 * 60 * 60 * 1000L

    private const val SYNC_PASS_LIMIT = 12
    private const val BACKGROUND_PASS_LIMIT = 48
    private const val CHUNK_SIZE = 6
    private const val SYNC_BATCH_TIMEOUT_MS = 6_000L
    private const val BACKGROUND_BATCH_TIMEOUT_MS = 20_000L

    private val searchSemaphore = Semaphore(2)
    private val memory = BoundedCache<String, NameVariants>("bangumi.entityNameCache", MAX_ENTRIES)
    private val negativeCache = BoundedCache<String, Long>("bangumi.entityNameCache.negative", 1024)

    @Volatile
    private var loaded = false
    private val loadLock = Any()

    // ── Kalıcı önbellek ──────────────────────────────────────────────────────

    internal fun cacheKey(kind: Kind, rawId: Int): String =
        (if (kind == Kind.CHARACTER) "c" else "p") + rawId

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun appContext(): Context? = KitsugiApplication.getInstance()?.applicationContext

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            runCatching {
                val context = appContext() ?: return@runCatching
                for ((key, value) in prefs(context).all) {
                    val text = value as? String ?: continue
                    val parsed = parse(text) ?: continue
                    memory[key] = parsed
                }
            }
            loaded = true
        }
    }

    private fun parse(json: String): NameVariants? = runCatching {
        val obj = JSONObject(json)
        val variants = NameVariants(
            romaji = usableLatin(obj.optString("r").takeIf { it.isNotBlank() }),
            english = usableLatin(obj.optString("e").takeIf { it.isNotBlank() }),
            native = obj.optString("n").takeIf { it.isNotBlank() }?.trim()
        )
        variants.takeIf { it.hasLatin }
    }.getOrNull()

    private fun getCache(key: String): NameVariants? {
        ensureLoaded()
        return memory[key]
    }

    private suspend fun putCache(key: String, variants: NameVariants) {
        if (!variants.hasLatin) return
        ensureLoaded()
        val previous = memory[key]
        val merged = NameVariants(
            romaji = variants.romaji ?: previous?.romaji,
            english = variants.english ?: previous?.english,
            native = variants.native ?: previous?.native
        )
        memory[key] = merged
        val context = appContext() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val store = prefs(context)
                val editor = store.edit()
                if (store.all.size >= MAX_ENTRIES) editor.clear()
                val payload = JSONObject()
                merged.romaji?.let { payload.put("r", it) }
                merged.english?.let { payload.put("e", it) }
                merged.native?.let { payload.put("n", it) }
                editor.putString(key, payload.toString())
                editor.apply()
            }
        }
    }

    private fun isNegative(key: String): Boolean {
        val at = negativeCache[key] ?: return false
        return System.currentTimeMillis() - at < NEGATIVE_TTL_MS
    }

    private fun markNegative(key: String) {
        negativeCache[key] = System.currentTimeMillis()
    }

    // ── Çözümleme ────────────────────────────────────────────────────────────

    /**
     * Verilen karakter/kişi istekleri için Latin ad varyantlarını çözer.
     *
     * [cacheOnly] modunda yalnızca kalıcı önbellek okunur (ağ YOK) — senkron istemci geçleri
     * ilk çizimi geciktirmemek için bu modda çalışır; ağ işini arka plan geçi üstlenir.
     * Dönen harita [cacheKey] anahtarlıdır.
     */
    suspend fun resolve(
        requests: List<Request>,
        background: Boolean = false,
        cacheOnly: Boolean = false
    ): Map<String, NameVariants> {
        val result = ConcurrentHashMap<String, NameVariants>()
        val pending = mutableListOf<Request>()
        for (req in requests.filter { it.rawId > 0 }.distinctBy { cacheKey(it.kind, it.rawId) }) {
            val key = cacheKey(req.kind, req.rawId)
            val cached = getCache(key)
            if (cached != null && cached.hasLatin) result[key] = cached
            if (cached == null && !isNegative(key)) pending += req
        }
        if (pending.isEmpty() || cacheOnly) return result

        val limit = if (background) BACKGROUND_PASS_LIMIT else SYNC_PASS_LIMIT
        val batchTimeout = if (background) BACKGROUND_BATCH_TIMEOUT_MS else SYNC_BATCH_TIMEOUT_MS
        val searched = ConcurrentHashMap.newKeySet<String>()
        withTimeoutOrNull(batchTimeout) {
            coroutineScope {
                pending.take(limit)
                    .groupBy { it.kind }
                    .map { (kind, kindRequests) ->
                        async(Dispatchers.IO) {
                            try {
                                searchSemaphore.withPermit { resolveKind(kind, kindRequests) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                android.util.Log.w(TAG, "AniList ad araması başarısız ($kind): ${e.message}")
                                null
                            }
                        }
                    }.awaitAll().forEach { chunkResults ->
                        if (chunkResults == null) return@forEach
                        for ((key, variants) in chunkResults) {
                            searched += key
                            if (variants != null) {
                                putCache(key, variants)
                                result[key] = mergeWithCache(key, variants)
                            }
                        }
                    }
            }
        }
        // Arama gerçekten yapıldıysa ve Latin ad bulunamadıysa olumsuz önbellek;
        // zaman aşımının dışındaki kayıtlar sonraki açılışta yeniden denenebilir.
        for (req in pending.take(limit)) {
            val key = cacheKey(req.kind, req.rawId)
            if (key in searched && result[key]?.hasLatin != true) markNegative(key)
        }
        return result
    }

    private fun mergeWithCache(key: String, variants: NameVariants): NameVariants {
        val cached = memory[key] ?: return variants
        return NameVariants(
            romaji = variants.romaji ?: cached.romaji,
            english = variants.english ?: cached.english,
            native = variants.native ?: cached.native
        )
    }

    private suspend fun resolveKind(
        kind: Kind,
        requests: List<Request>
    ): Map<String, NameVariants?> = coroutineScope {
        requests.chunked(CHUNK_SIZE).map { chunk ->
            async(Dispatchers.IO) { matchChunk(kind, chunk) }
        }.awaitAll().fold(mutableMapOf<String, NameVariants?>()) { acc, chunkResult ->
            chunkResult?.let { acc.putAll(it) }
            acc
        }
    }

    private suspend fun matchChunk(
        kind: Kind,
        chunk: List<Request>
    ): Map<String, NameVariants?>? {
        val (query, variables) = aliasedQuery(kind, chunk)
        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return null
        val data = runCatching { JSONObject(response) }.getOrNull()
            ?.optJSONObject("data") ?: return null
        val out = HashMap<String, NameVariants?>()
        for ((index, req) in chunk.withIndex()) {
            val key = cacheKey(req.kind, req.rawId)
            val alias = (if (kind == Kind.CHARACTER) "c" else "p") + index
            val field = if (kind == Kind.CHARACTER) "characters" else "staff"
            val variants = parseNodes(data.optJSONObject(alias)?.optJSONArray(field))
            val keys = matchKeysFor(req.nativeName, req.fallbackName)
            val match = if (keys.isEmpty()) null else pickMatch(keys, variants)
            out[key] = match?.let {
                NameVariants(romaji = it.romaji, english = it.english, native = it.native)
            }
        }
        return out
    }

    /** Tek istekte N aliased `Page(characters|staff(search:))` sorgusu kurar. */
    internal fun aliasedQuery(kind: Kind, chunk: List<Request>): Pair<String, JSONObject> {
        val field = if (kind == Kind.CHARACTER) "characters" else "staff"
        val aliasPrefix = if (kind == Kind.CHARACTER) "c" else "p"
        val sb = StringBuilder("query(")
        chunk.forEachIndexed { index, _ ->
            if (index > 0) sb.append(", ")
            sb.append("\$s").append(index).append(": String")
        }
        sb.append(") {\n")
        chunk.forEachIndexed { index, _ ->
            sb.append(aliasPrefix).append(index)
                .append(": Page(page: 1, perPage: 5) { ")
                .append(field).append("(search: \$s").append(index)
                .append(") { name { full native alternative } } }\n")
        }
        sb.append("}")
        val variables = JSONObject()
        chunk.forEachIndexed { index, req ->
            variables.put("s$index", req.nativeName?.trim()?.takeIf { it.isNotEmpty() } ?: req.fallbackName.orEmpty())
        }
        return sb.toString() to variables
    }

    private fun parseNodes(array: org.json.JSONArray?): List<KitsugiAniListPersonBridge.NameVariant> {
        if (array == null) return emptyList()
        val out = ArrayList<KitsugiAniListPersonBridge.NameVariant>(array.length())
        for (i in 0 until array.length()) {
            val name = array.optJSONObject(i)?.optJSONObject("name") ?: continue
            val full = name.optString("full").trim()
            if (full.isEmpty()) continue
            val native = name.optString("native").trim().takeIf { it.isNotEmpty() }
            val alternatives = mutableListOf<String>()
            name.optJSONArray("alternative")?.let { alt ->
                for (j in 0 until alt.length()) {
                    alt.optString(j).trim().takeIf { it.isNotEmpty() }?.let { alternatives.add(it) }
                }
            }
            out += KitsugiAniListPersonBridge.NameVariant(full = full, native = native, alternatives = alternatives)
        }
        return out
    }

    // ── Saf yardımcılar (birim testleri) ─────────────────────────────────────

    /** Eşleme anahtarları: özgün ad + liste adı (normalleştirilmiş). */
    internal fun matchKeysFor(nativeName: String?, fallbackName: String?): Set<String> =
        listOfNotNull(nativeName, fallbackName)
            .mapNotNull { KitsugiAniListPersonBridge.normalizeKey(it) }
            .toSet()

    /**
     * Birebir (normalize) eşleşme arar: adayın native / full / alternative adlarından biri
     * istek anahtarlarıyla aynıysa kabul edilir. Kısmi eşleşme yoktur.
     */
    internal fun pickMatch(
        keys: Set<String>,
        variants: List<KitsugiAniListPersonBridge.NameVariant>
    ): KitsugiAniListPersonBridge.NameVariant? {
        if (keys.isEmpty()) return null
        return variants.firstOrNull { variant -> variant.matchKeys.any { it in keys } }
    }

    private fun usableLatin(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && PreferenceHelpers.isLatinText(it) }
}
