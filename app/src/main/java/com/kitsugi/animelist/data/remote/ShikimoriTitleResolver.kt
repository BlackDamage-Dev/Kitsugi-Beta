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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shikimori kayıtları için İngilizce / Japonca başlık çözücüsü.
 *
 * ## Sorun
 * Shikimori REST arama ve `related` / `similar` uçları çoğunlukla yalnızca `name` (romaji)
 * ve `russian` alanlarını döndürür; İngilizce/Japonca varyantları içermez. Arama sonuçlarında
 * `name`'in `titleEnglish` alanında kullanılması İngilizce seçiliyken romaji gösteriyordu.
 * İlişki/öneri akışında Rusça'yı İngilizce alanına yazmak da aynı dil tercihi hatasına yol açıyordu.
 *
 * ## Çözüm
 * Shikimori GraphQL `animes` / `mangas` sorgusu `english` ve `japanese` alanlarını verir.
 * Kimlikler tek (en fazla 50'lik) istekle toplu çözülür; sonuç bellekte önbelleğe alınır.
 * Shikimori kimlikleri MAL kimliği olarak KULLANILMAZ — yalnızca Shikimori'nin kendi
 * kimlikleri sorgulanır.
 */
object ShikimoriTitleResolver {

    private const val TAG = "ShikimoriTitles"
    private const val BATCH_SIZE = 50
    private const val CACHE_LIMIT = 6000
    // Başlık zenginleştirme isteği arama kaynağını bekletmesin; yanıt gelmezse romaji yedeği var.
    private const val TITLE_CALL_TIMEOUT_SECONDS = 6L
    private const val USER_AGENT = "KitsugiApp/2.4 (Android)"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val titleHttpClient by lazy {
        KitsugiHttpClient.client.newBuilder()
            .callTimeout(TITLE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** Çözülmüş adlar. Boş değerler null'dır (ekranda romaji'ye düşülür). */
    data class Names(
        val english: String? = null,
        val japanese: String? = null
    )

    private val cache = BoundedCache<String, Names>("shikimori.title", 1500)

    /**
     * Verilen Shikimori kimliklerinin İngilizce/Japonca adlarını çözer.
     * Ağ hatasında o grup önbelleğe yazılmaz (sonraki denemede tekrar sorulur).
     */
    suspend fun resolve(
        kind: ShikimoriPosterResolver.Kind,
        ids: Collection<Int>
    ): Map<Int, Names> {
        val wanted = ids.filter { it > 0 }.distinct()
        if (wanted.isEmpty()) return emptyMap()

        val result = HashMap<Int, Names>(wanted.size)
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
                    // Yanıtta olmayan kimlik = isim yok; negatif sonucu da önbelleğe alıyoruz.
                    val names = fetched[id] ?: Names()
                    cache[cacheKey(kind, id)] = names
                    result[id] = names
                }
            }
        }
        if (cache.size > CACHE_LIMIT) cache.clear()
        return result
    }

    /**
     * Uygulamanın ortak arama modeli Shikimori kimliğini `malId` alanında taşır.
     * Çözülen başlık varyantlarını aynı kimliğe ekle; kaynak başlığı ve varsa önceden
     * gelen değerleri koru ki çözümleme başarısız olduğunda da sonuç kullanılabilsin.
     */
    internal fun applyToSearchResults(
        results: List<JikanSearchResult>,
        namesById: Map<Int, Names>
    ): List<JikanSearchResult> = results.map { result ->
        val names = namesById[result.malId] ?: return@map result
        result.copy(
            titleEnglish = names.english ?: result.titleEnglish,
            titleJapanese = names.japanese ?: result.titleJapanese
        )
    }

    private fun cacheKey(kind: ShikimoriPosterResolver.Kind, id: Int): String = "${kind.field}:$id"

    /** GraphQL sorgusu. Kimlikler Int olduğundan satır içi yazmak güvenlidir. */
    internal fun buildQuery(kind: ShikimoriPosterResolver.Kind, ids: List<Int>): String {
        val joined = ids.joinToString(",")
        val limit = ids.size.coerceIn(1, BATCH_SIZE)
        return "{ ${kind.field}(ids: \"$joined\", limit: $limit, censored: false) " +
            "{ id english japanese } }"
    }

    /**
     * Yanıtı ayrıştırır. `english` / `japanese` alanları şemaya göre dize ya da dize dizisi
     * olabildiğinden ikisi de desteklenir; ilk boş olmayan değer alınır.
     *
     * @return kimlik → ad eşlemesi; yanıt kullanılamazsa null.
     */
    internal fun parseResponse(body: String?, kind: ShikimoriPosterResolver.Kind): Map<Int, Names>? {
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
        val out = HashMap<Int, Names>(array.length())
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val id = node.optString("id").trim().toIntOrNull()
                ?: node.optInt("id").takeIf { it > 0 }
                ?: continue
            out[id] = Names(
                english = firstNonBlank(node.opt("english")),
                japanese = firstNonBlank(node.opt("japanese"))
            )
        }
        return out
    }

    /** Dize veya dize dizisinden ilk boş olmayan değeri döndürür. */
    private fun firstNonBlank(value: Any?): String? = when (value) {
        is String -> value.trim().takeIf { it.isNotEmpty() && it != "null" }
        is JSONArray -> (0 until value.length())
            .mapNotNull { idx -> value.optString(idx, "").trim().takeIf { it.isNotEmpty() && it != "null" } }
            .firstOrNull()
        else -> null
    }

    private suspend fun fetchBatch(
        kind: ShikimoriPosterResolver.Kind,
        ids: List<Int>
    ): Map<Int, Names>? {
        if (ids.isEmpty()) return emptyMap()
        val payload = JSONObject().put("query", buildQuery(kind, ids)).toString()
        val request = Request.Builder()
            .url("${ShikimoriPosterResolver.HOST}/api/graphql")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            PlatformRateLimiter.acquire("shikimori")
            titleHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GraphQL başlık isteği başarısız: HTTP ${response.code} (${kind.field}, ${ids.size} kimlik)")
                    return@use null
                }
                parseResponse(response.body?.string(), kind)
            }
        }.getOrElse { err ->
            Log.w(TAG, "GraphQL başlık isteği hata verdi (${kind.field}): ${err.message}")
            null
        }
    }
}
