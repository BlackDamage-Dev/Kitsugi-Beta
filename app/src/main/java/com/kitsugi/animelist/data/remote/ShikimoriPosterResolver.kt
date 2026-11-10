package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.data.auth.PlatformRateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Shikimori poster (kapak görseli) çözümleyicisi.
 *
 * ## Sorun
 * Shikimori'nin REST API'si (`/api/animes`, `/api/users/:id/anime_rates`, `/api/characters/:id` …)
 * `image` alanını hâlâ **eski Paperclip eki** üzerinden üretiyor:
 *
 * ```ruby
 * has_attached_file :image,
 *   url: '/system/animes/:style/:id.:extension',
 *   default_url: '/assets/globals/missing_:style.jpg'   # ← "404 not found" kızı
 * ```
 *
 * Shikimori kapakları yıllar önce yeni `Poster` tablosuna (Shrine) taşıdı. Yeni/yeniden
 * yüklenen yapımların **eski Paperclip eki yok**, bu yüzden REST yanıtı sessizce
 * `/assets/globals/missing_original.jpg` döndürüyor. Uygulama bunu geçerli bir URL sanıp
 * indiriyor ve ekran, "404 not found" tişörtlü aynı yer tutucu görselle doluyor.
 * Eski kayıtlarda (ör. 80'ler yapımları) Paperclip eki durduğu için onlar normal görünüyor —
 * ekrandaki "bazıları var, çoğu yok" tablosunun sebebi tam olarak budur.
 *
 * ## Çözüm
 * Gerçek kapaklar yalnızca **GraphQL** yüzeyinde var:
 *
 * ```graphql
 * { animes(ids: "1,2,3", limit: 50, censored: false) { id poster { originalUrl mainUrl } } }
 * ```
 *
 * `poster` alanı `ImageUrlGenerator#cdn_poster_url` ile **mutlak** CDN adresleri döndürür
 * (ör. `https://nyaa.shikimori.one/system/posters/...`). Bu nesne:
 *
 *  1. REST'ten gelen yer tutucu görselleri [absoluteUrl] ile eler (null'a çevirir),
 *  2. Eksik kalan kimlikleri 50'lik gruplar hâlinde GraphQL'den toplu çözer,
 *  3. Sonucu bellekte önbelleğe alır (aynı oturumda tekrar istek atılmaz).
 *
 * GraphQL okuma uçları kimlik doğrulaması istemez; istekler [PlatformRateLimiter] ile
 * Shikimori'nin 5 istek/sn sınırına uyacak şekilde sıraya alınır.
 */
object ShikimoriPosterResolver {

    private const val TAG = "ShikimoriPoster"

    /** Shikimori'nin güncel kanonik host'u (shikimori.one / .me / .org buraya yönleniyor). */
    const val HOST = "https://shikimori.io"

    /** Paperclip'in "görsel yok" varsayılanı: `/assets/globals/missing_original.jpg` vb. */
    private const val MISSING_ASSET_MARKER = "/assets/globals/missing"

    /** Shikimori GraphQL `animes`/`mangas`/`characters`/`people` sorgularında sayfa başına üst sınır. */
    private const val BATCH_SIZE = 50

    /** Bellek önbelleği bu sayıyı aşarsa sıfırlanır (uzun oturumlarda sınırsız büyümeyi önler). */
    private const val CACHE_LIMIT = 6000

    private const val USER_AGENT = "KitsugiApp/2.4 (Android)"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

    /** Yalnızca testlerde kullanılır; sahte bir sunucuya yönlendirir (ör. `http://127.0.0.1:1234`). */
    @Volatile
    internal var hostOverride: String? = null

    private val activeHost: String
        get() = hostOverride ?: com.kitsugi.animelist.data.auth.ShikimoriApiClient.preferredBaseUrl()

    private val cache = BoundedCache<String, Poster>("shikimori.poster", 1500)

    /** GraphQL'de poster taşıyan dört varlık türü. */
    enum class Kind(internal val field: String, internal val idsAsList: Boolean) {
        ANIME("animes", false),
        MANGA("mangas", false),
        CHARACTER("characters", true),
        PERSON("people", true)
    }

    /**
     * Çözümlenmiş kapak adresleri.
     *
     * @param originalUrl yüklenen tam boy görsel (detay sayfası / tam ekran için).
     * @param mainUrl 225 px genişliğinde webp türevi (liste ve ızgara kartları için).
     */
    data class Poster(
        val originalUrl: String? = null,
        val mainUrl: String? = null
    ) {
        /** Liste/ızgara kartları: küçük türev öncelikli. */
        val cardUrl: String? get() = mainUrl ?: originalUrl

        /** Detay/tam ekran: tam boy öncelikli. */
        val fullUrl: String? get() = originalUrl ?: mainUrl

        val isEmpty: Boolean get() = originalUrl == null && mainUrl == null
    }

    /**
     * Shikimori'nin "görsel yok" yer tutucusunu tanır.
     * Paperclip varsayılanı tüm boyutlar için `/assets/globals/missing_*` yoluna düşer
     * (`missing_original.jpg`, `missing_preview.jpg`, `missing_x48.jpg`, `missing_avatar/x160.png`).
     */
    fun isMissingImage(raw: String?): Boolean {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return true
        return value.substringBefore('?').contains(MISSING_ASSET_MARKER)
    }

    /**
     * REST yanıtındaki göreli görsel yolunu mutlak adrese çevirir.
     *
     * Yer tutucu ("404 not found") görselleri ve boş değerler **null** döner; böylece arayüz
     * sahte bir kapak yerine baş harf/boş durum gösterir ve birleşik "Tümü" görünümünde
     * AniList/MAL gibi gerçek kapağı olan kayıtlar temsilci seçilebilir.
     */
    fun absoluteUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (isMissingImage(value)) return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://", ignoreCase = true) -> value
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("/") -> activeHost + value
            else -> "$activeHost/$value"
        }
    }

    /** Tek bir kaydın kapağını çözer (iç tarafta toplu sorgu ve önbellek kullanılır). */
    suspend fun posterFor(kind: Kind, id: Int): Poster? = resolve(kind, listOf(id))[id]

    /**
     * REST görselini doğrular; yer tutucu ise gerçek kapağı GraphQL'den getirir.
     *
     * @param preferFull detay/tam ekran için `true`, liste kartları için `false`.
     */
    suspend fun bestImageUrl(
        kind: Kind,
        id: Int,
        restImageUrl: String?,
        preferFull: Boolean = false
    ): String? {
        val direct = absoluteUrl(restImageUrl)
        if (direct != null) return direct
        if (id <= 0) return null
        val poster = posterFor(kind, id) ?: return null
        return if (preferFull) poster.fullUrl else poster.cardUrl
    }

    /**
     * Verilen kimliklerin kapaklarını toplu çözer. Önbellekte olanlar ağa çıkmaz,
     * kalanlar [BATCH_SIZE]'lık gruplar hâlinde tek GraphQL isteğiyle alınır.
     *
     * Ağ hatasında o grup **önbelleğe yazılmaz**, böylece sonraki denemede tekrar sorulur.
     */
    suspend fun resolve(kind: Kind, ids: Collection<Int>): Map<Int, Poster> {
        val wanted = ids.filter { it > 0 }.distinct()
        if (wanted.isEmpty()) return emptyMap()

        val result = HashMap<Int, Poster>(wanted.size)
        val pending = mutableListOf<Int>()
        for (id in wanted) {
            val cached = cache[cacheKey(kind, id)]
            if (cached != null) result[id] = cached else pending.add(id)
        }
        if (pending.isEmpty()) return result

        withContext(Dispatchers.IO) {
            for (chunk in pending.chunked(BATCH_SIZE)) {
                val fetched = fetchBatch(kind, chunk) ?: continue
                for (id in chunk) {
                    // Yanıtta hiç dönmeyen kimlik = Shikimori'de gerçekten kapak yok.
                    // Negatif sonucu da önbelleğe alıp aynı isteği tekrarlamıyoruz.
                    val poster = fetched[id] ?: Poster()
                    cache[cacheKey(kind, id)] = poster
                    result[id] = poster
                }
            }
        }
        trimCacheIfNeeded()
        return result
    }

    /** Test ve oturum kapanışı için önbelleği boşaltır. */
    fun clearCache() {
        cache.clear()
    }

    // ─── İç yardımcılar ───────────────────────────────────────────────────────

    private fun cacheKey(kind: Kind, id: Int): String = "${kind.field}:$id"

    private fun trimCacheIfNeeded() {
        if (cache.size > CACHE_LIMIT) cache.clear()
    }

    /**
     * GraphQL sorgusunu üretir.
     *
     * `animes`/`mangas` kimlikleri virgülle ayrılmış **String** olarak,
     * `characters`/`people` ise **[ID]** listesi olarak alır (Shikimori şema farkı).
     * Kimlikler `Int` olduğundan satır içi yazmak güvenlidir.
     */
    internal fun buildQuery(kind: Kind, ids: List<Int>): String {
        val joined = ids.joinToString(",")
        val idsArgument = if (kind.idsAsList) "[$joined]" else "\"$joined\""
        // censored: false → hentai/yaoi/yuri kayıtları da kimlikle sorulduğunda dönsün.
        val censored = if (kind.idsAsList) "" else ", censored: false"
        val limit = ids.size.coerceIn(1, BATCH_SIZE)
        return "{ ${kind.field}(ids: $idsArgument, limit: $limit$censored) " +
            "{ id poster { originalUrl mainUrl } } }"
    }

    /**
     * GraphQL yanıtını ayrıştırır.
     *
     * @return kimlik → poster eşlemesi, yanıt kullanılamazsa **null** (hata ≠ "kapak yok").
     */
    internal fun parseResponse(body: String?, kind: Kind): Map<Int, Poster>? {
        val raw = body?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        root.optJSONArray("errors")?.let { errors ->
            if (errors.length() > 0) {
                Log.w(TAG, "GraphQL hata döndürdü: ${errors.optJSONObject(0)?.optString("message")}")
            }
        }
        val data = root.optJSONObject("data") ?: return null
        val array = data.optJSONArray(kind.field) ?: return null

        val out = HashMap<Int, Poster>(array.length())
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val id = node.optString("id").trim().toIntOrNull()
                ?: node.optInt("id").takeIf { it > 0 }
                ?: continue
            val posterObj = node.optJSONObject("poster")
            out[id] = Poster(
                originalUrl = absoluteUrl(posterObj?.optString("originalUrl")),
                mainUrl = absoluteUrl(posterObj?.optString("mainUrl"))
            )
        }
        return out
    }

    private suspend fun fetchBatch(kind: Kind, ids: List<Int>): Map<Int, Poster>? {
        if (ids.isEmpty()) return emptyMap()
        val payload = JSONObject().put("query", buildQuery(kind, ids)).toString()
        val request = Request.Builder()
            .url("$activeHost/api/graphql")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            PlatformRateLimiter.acquire("shikimori")
            val mirrors = hostOverride?.let { listOf(it) }
                ?: com.kitsugi.animelist.data.auth.ShikimoriApiClient.baseUrlCandidates()
            com.kitsugi.animelist.data.auth.ShikimoriApiClient.executeShikimori(
                request = request,
                candidateBaseUrls = mirrors
            ).use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GraphQL poster isteği başarısız: HTTP ${response.code} (${kind.field}, ${ids.size} kimlik)")
                    return@use null
                }
                parseResponse(response.body?.string(), kind)
            }
        }.getOrElse { err ->
            Log.w(TAG, "GraphQL poster isteği hata verdi (${kind.field}): ${err.message}")
            null
        }
    }
}
