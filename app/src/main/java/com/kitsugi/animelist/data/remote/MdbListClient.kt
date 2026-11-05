package com.kitsugi.animelist.data.remote
import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * MDBList API istemcisi.
 *
 * IMDb, Rotten Tomatoes, Metacritic, Letterboxd, TMDB ve Trakt puanlarını alır.
 * IMDb kimliği tercih edilir; IMDb sorgusu boşsa veya başarısızsa TMDB ID ile yeniden dener.
 */
data class MdbListRatings(
    val imdb: Double? = null,
    val tomatoes: Int? = null,
    val tomatoesAudience: Int? = null,
    val metacritic: Int? = null,
    val letterboxd: Double? = null,
    val tmdb: Double? = null,
    val trakt: Double? = null,
    val imdbId: String? = null
) {
    val isEmpty: Boolean
        get() = imdb == null && tomatoes == null && tomatoesAudience == null &&
            metacritic == null && letterboxd == null && tmdb == null && trakt == null
}

private fun MdbListRatings.withoutEmptyScores(): MdbListRatings = copy(
    imdb = imdb?.takeIf { it > 0.0 },
    tomatoes = tomatoes?.takeIf { it > 0 },
    tomatoesAudience = tomatoesAudience?.takeIf { it > 0 },
    metacritic = metacritic?.takeIf { it > 0 },
    letterboxd = letterboxd?.takeIf { it > 0.0 },
    tmdb = tmdb?.takeIf { it > 0.0 },
    trakt = trakt?.takeIf { it > 0.0 }
)

object MdbListClient {

    private const val TAG = "MdbListClient"
    private const val BASE_URL = "https://api.mdblist.com/"
    private const val QUOTA_URL_HOST = "api.mdblist.com"
    private const val CACHE_TTL_MS = 30L * 60L * 1_000L
    private const val MAX_CACHE_ENTRIES = 500
    private val imdbIdPattern = Regex("^tt\\d{5,12}$", RegexOption.IGNORE_CASE)

    private data class CacheEntry(val ratings: MdbListRatings, val expiresAt: Long)
    private val cache = BoundedCache<String, CacheEntry>("mdbList", MAX_CACHE_ENTRIES)

    /** Uygulama açılışında kalıcı kota deposunu bağlar (bkz. KitsugiApplication). */
    fun initialize(context: android.content.Context) = MdbListQuotaCenter.bind(context, "")

    /**
     * API anahtarını Matrix'in bilinen IMDb ID'si ile sınar.
     * Başarılı HTTP cevabına ek olarak MDBList'in response alanı da doğrulanır.
     */
    suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext false
        val url = buildLookupUrl(apiKey = apiKey, imdbId = "tt0133093", tmdbId = null)
            ?: return@withContext false
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        try {
            KitsugiHttpClient.mdbListClient.newCall(request).execute().use { response ->
                // Doğrulama da gerçek bir API çağrısıdır: dönen başlıklar kotayı günceller.
                recordQuotaFromResponse(apiKey, response)
                if (response.code == 429) {
                    // 429 = kimlik doğrulama geçti, günlük kota bitti. Anahtar geçerlidir;
                    // burada "geçersiz anahtar" demek kullanıcıyı yanıltırdı.
                    Log.w(TAG, "MDBList key validation reached the daily quota (HTTP 429)")
                    return@withContext true
                }
                if (!response.isSuccessful) {
                    Log.w(TAG, "MDBList key validation failed: HTTP ${response.code}")
                    return@withContext false
                }
                val body = response.body?.string() ?: return@withContext false
                val root = JSONObject(body)
                if (root.has("response") && !root.isNull("response")) {
                    isResponseAccepted(root)
                } else {
                    // Some response versions omit the response flag. Accept only a real
                    // Matrix rating payload, not an empty/error JSON object.
                    parseRatings(body, "tt0133093")?.isEmpty == false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "MDBList key validation failed (${e.javaClass.simpleName})")
            false
        }
    }

    /** Existing IMDb-ID API retained for callers. */
    suspend fun fetchRatings(imdbId: String, apiKey: String): MdbListRatings? =
        fetchRatings(imdbId = imdbId, tmdbId = null, mediaType = null, apiKey = apiKey)

    suspend fun fetchRatings(
        imdbId: String?,
        tmdbId: Int?,
        apiKey: String
    ): MdbListRatings? = fetchRatings(imdbId = imdbId, tmdbId = tmdbId, mediaType = null, apiKey = apiKey)

    /**
     * Gets scores by IMDb ID first, then tries TMDB when that lookup is empty or unavailable.
     * Some MDBList records do not have a usable IMDb mapping even when the TMDB record exists.
     *
     * @param mediaType "movie", "show", or "any" (default). Anime/TV shows map to "show",
     * movies map to "movie". Manga is not supported by MDBList and should not be looked up.
     */
    suspend fun fetchRatings(
        imdbId: String?,
        tmdbId: Int?,
        mediaType: String?,
        apiKey: String
    ): MdbListRatings? = withContext(Dispatchers.IO) {
        val normalizedApiKey = apiKey.trim()
        val normalizedImdbId = imdbId?.trim()?.lowercase()?.takeIf(imdbIdPattern::matches)
        val normalizedTmdbId = tmdbId?.takeIf { it > 0 }
        if (normalizedApiKey.isBlank() || (normalizedImdbId == null && normalizedTmdbId == null)) {
            return@withContext null
        }

        // Include both IDs in the key: an IMDb-only lookup can be empty while a TMDB fallback
        // for the same title succeeds, and those outcomes must not collide in the cache.
        val identity = when {
            normalizedImdbId != null && normalizedTmdbId != null -> "imdb:$normalizedImdbId|tmdb:$normalizedTmdbId"
            normalizedImdbId != null -> "imdb:$normalizedImdbId"
            else -> "tmdb:$normalizedTmdbId"
        }
        val typeSuffix = mediaType?.let { ":$it" } ?: ""
        val cacheKey = "${fingerprint(normalizedApiKey)}:$identity$typeSuffix"
        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { entry ->
            if (entry.expiresAt > now) return@withContext entry.ratings
            cache.remove(cacheKey, entry)
        }
        if (MdbListQuotaCenter.isExhausted(normalizedApiKey)) {
            // Günlük kota bitti: boşuna 429 üretmek yerine son bilinen puanlar gösterilir.
            // (Önbellek süresi dolmuş olsa bile, hiç yoktan iyidir.)
            return@withContext cache[cacheKey]?.ratings
        }

        val imdbResult = normalizedImdbId?.let { id ->
            requestRatings(normalizedApiKey, imdbId = id, tmdbId = null, mediaType = mediaType, identity = "imdb:$id")
        }
        if (imdbResult?.isEmpty == false) {
            cacheRatings(cacheKey, imdbResult, now)
            return@withContext imdbResult
        }

        if (normalizedTmdbId != null) {
            val tmdbResult = requestRatings(
                normalizedApiKey,
                imdbId = null,
                tmdbId = normalizedTmdbId,
                mediaType = mediaType,
                identity = "tmdb:$normalizedTmdbId"
            )
            if (tmdbResult == null) {
                // Keep any valid empty IMDb response for this visit, but don't cache it: the
                // fallback failed transiently and should be retried on the next detail visit.
                return@withContext imdbResult
            }
            val result = selectRatingsWithFallback(imdbResult, tmdbResult, normalizedImdbId)
                ?: return@withContext null
            cacheRatings(cacheKey, result, now)
            return@withContext result
        }

        val emptyResult = imdbResult ?: return@withContext null
        cacheRatings(cacheKey, emptyResult, now)
        emptyResult
    }

    private fun requestRatings(
        apiKey: String,
        imdbId: String?,
        tmdbId: Int?,
        mediaType: String? = null,
        identity: String
    ): MdbListRatings? {
        val url = buildLookupUrl(apiKey, imdbId, tmdbId, mediaType) ?: return null
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        return try {
            KitsugiHttpClient.mdbListClient.newCall(request).execute().use { response ->
                recordQuotaFromResponse(apiKey, response)
                if (!response.isSuccessful) {
                    Log.w(TAG, "MDBList request failed: HTTP ${response.code} for $identity")
                    return null
                }
                val body = response.body?.string() ?: return null
                parseRatings(body, imdbId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Do not cache transient network failures; a later detail visit should retry.
            Log.w(TAG, "MDBList request failed (${e.javaClass.simpleName}) for $identity")
            null
        }
    }

    private fun cacheRatings(cacheKey: String, ratings: MdbListRatings, now: Long) {
        if (cache.size >= MAX_CACHE_ENTRIES) {
            val expiredKeys = cache.entries
                .filter { it.value.expiresAt <= now }
                .map { it.key }
            expiredKeys.forEach(cache::remove)
            if (cache.size >= MAX_CACHE_ENTRIES) cache.keys.firstOrNull()?.let(cache::remove)
        }
        cache[cacheKey] = CacheEntry(ratings, now + CACHE_TTL_MS)
    }

    internal fun selectRatingsWithFallback(
        imdbResult: MdbListRatings?,
        tmdbResult: MdbListRatings?,
        preferredImdbId: String?
    ): MdbListRatings? = when {
        imdbResult?.isEmpty == false -> imdbResult
        tmdbResult?.isEmpty == false -> tmdbResult.copy(imdbId = tmdbResult.imdbId ?: preferredImdbId)
        imdbResult != null -> imdbResult.copy(imdbId = imdbResult.imdbId ?: preferredImdbId)
        tmdbResult != null -> tmdbResult.copy(imdbId = tmdbResult.imdbId ?: preferredImdbId)
        else -> null
    }

    internal fun buildLookupUrl(
        apiKey: String,
        imdbId: String?,
        tmdbId: Int?,
        mediaType: String? = null
    ): HttpUrl? {
        if (apiKey.isBlank()) return null
        val normalizedImdbId = imdbId?.trim()?.lowercase()?.takeIf(imdbIdPattern::matches)
        val normalizedTmdbId = tmdbId?.takeIf { it > 0 }
        if (normalizedImdbId == null && normalizedTmdbId == null) return null

        return HttpUrl.Builder()
            .scheme("https")
            .host("mdblist.com")
            .addPathSegments("api/")
            .addQueryParameter("apikey", apiKey.trim())
            .apply {
                if (normalizedImdbId != null) addQueryParameter("i", normalizedImdbId)
                else addQueryParameter("tmdb", normalizedTmdbId.toString())
            }
            .build()
    }

    /**
     * API anahtarının günlük istek hakkını `/user` uç noktasından okur.
     *
     * MDBList bu uç noktada hem gövdede (`api_requests` = limit, `api_requests_count` =
     * kullanılan) hem de `X-RateLimit-*` başlıklarında güncel kotayı döndürür. Ücretsiz ve
     * ücretli hesaplar aynı alanlarla geldiği için limit asla sabit bir sayı varsayılmaz.
     *
     * @return kota okunabildiyse doldurulmuş [MdbListQuota]; anahtar geçersizse `null`.
     */
    suspend fun fetchQuota(apiKey: String): MdbListQuota? = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext null

        val url = HttpUrl.Builder()
            .scheme("https")
            .host(QUOTA_URL_HOST)
            .addPathSegment("user")
            .addQueryParameter("apikey", key)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        try {
            KitsugiHttpClient.mdbListClient.newCall(request).execute().use { response ->
                val headerQuota = parseQuotaHeaders(
                    limitHeader = response.header("X-RateLimit-Limit") ?: response.header("RateLimit-Limit"),
                    remainingHeader = response.header("X-RateLimit-Remaining") ?: response.header("RateLimit-Remaining"),
                    resetHeader = response.header("X-RateLimit-Reset") ?: response.header("RateLimit-Reset")
                )
                if (response.code == 429) {
                    Log.w(TAG, "MDBList quota request rate limited (HTTP 429)")
                    return@withContext (headerQuota ?: MdbListQuota()).copy(
                        remaining = 0,
                        resetAtEpochSeconds = headerQuota?.resetAtEpochSeconds
                            ?: resetFromRetryAfter(response),
                        updatedAtMillis = System.currentTimeMillis()
                    )
                }
                if (!response.isSuccessful) {
                    Log.w(TAG, "MDBList quota request failed: HTTP ${response.code}")
                    return@withContext headerQuota
                }
                val body = response.body?.string()
                parseUserQuota(body, headerQuota)
            }
        } catch (e: Exception) {
            Log.w(TAG, "MDBList quota request failed (${e.javaClass.simpleName})")
            null
        }
    }

    /**
     * `/user` yanıtını kotaya çevirir. Gövde okunamazsa başlıklardan gelen değerler korunur.
     * Test edilebilir olması için ağdan bağımsızdır.
     */
    internal fun parseUserQuota(body: String?, headers: MdbListQuota?): MdbListQuota? {
        var limit = headers?.limit
        var remaining = headers?.remaining
        var plan = headers?.plan ?: MdbListPlan.UNKNOWN
        val resetAt = headers?.resetAtEpochSeconds

        if (!body.isNullOrBlank()) {
            try {
                val root = JSONObject(body)
                val declaredLimit = root.optInt("api_requests", -1).takeIf { it > 0 }
                val usedCount = root.optInt("api_requests_count", -1).takeIf { it >= 0 }
                limit = limit ?: declaredLimit
                val resolvedLimit = limit
                if (remaining == null && resolvedLimit != null && usedCount != null) {
                    remaining = (resolvedLimit - usedCount).coerceAtLeast(0)
                }

                val patronStatus = root.optString("patron_status").lowercase()
                val pledge = root.optInt("patreon_pledge", 0)
                if (patronStatus.isNotBlank() || pledge > 0) {
                    plan = if (patronStatus.contains("active") || pledge > 0) {
                        MdbListPlan.SUPPORTER
                    } else {
                        MdbListPlan.FREE
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MDBList quota JSON parse failed (${e.javaClass.simpleName})")
            }
        }

        if (limit == null && remaining == null) return null
        return MdbListQuota(
            limit = limit,
            remaining = remaining,
            resetAtEpochSeconds = resetAt,
            plan = plan,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * `X-RateLimit-Limit` / `-Remaining` / `-Reset` başlıklarını ayrıştırır.
     * Değerler okunamazsa `null` döner; uydurma sayı üretilmez.
     */
    internal fun parseQuotaHeaders(
        limitHeader: String?,
        remainingHeader: String?,
        resetHeader: String?
    ): MdbListQuota? {
        val limit = limitHeader?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val remaining = remainingHeader?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
        if (limit == null && remaining == null) return null
        return MdbListQuota(
            limit = limit,
            remaining = remaining,
            resetAtEpochSeconds = normalizeEpochSeconds(resetHeader?.trim()?.toLongOrNull()),
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    /** Bazı sürümler sıfırlanma anını milisaniye olarak yollar; saniyeye indirger. */
    internal fun normalizeEpochSeconds(value: Long?): Long? {
        val raw = value?.takeIf { it > 0L } ?: return null
        return if (raw > 100_000_000_000L) raw / 1_000L else raw
    }

    /**
     * Her MDBList yanıtında bulunan kota başlıklarını merkeze yazar. Ağ/ayrıştırma hatası
     * puanlama akışını asla bozmaz; bu yüzden tüm gövde korumalıdır.
     */
    private fun recordQuotaFromResponse(apiKey: String, response: Response) {
        try {
            val quota = parseQuotaHeaders(
                limitHeader = response.header("X-RateLimit-Limit") ?: response.header("RateLimit-Limit"),
                remainingHeader = response.header("X-RateLimit-Remaining") ?: response.header("RateLimit-Remaining"),
                resetHeader = response.header("X-RateLimit-Reset") ?: response.header("RateLimit-Reset")
            )
            if (quota != null) {
                MdbListQuotaCenter.recordHeaders(
                    apiKey = apiKey,
                    limit = quota.limit,
                    remaining = quota.remaining,
                    resetAtEpochSeconds = quota.resetAtEpochSeconds
                )
            }
            if (response.code == 429) {
                MdbListQuotaCenter.recordExhausted(
                    apiKey = apiKey,
                    resetAtEpochSeconds = quota?.resetAtEpochSeconds ?: resetFromRetryAfter(response)
                )
            }
        } catch (_: Throwable) {
            // Kota takibi yardımcı bir özelliktir; hiçbir koşulda puanlamayı düşürmemeli.
        }
    }

    /** `Retry-After` saniye cinsinden bekleme süresidir; mutlak sıfırlanma anına çevrilir. */
    private fun resetFromRetryAfter(response: Response): Long? {
        val seconds = response.header("Retry-After")?.trim()?.toLongOrNull()
            ?.takeIf { it > 0L } ?: return null
        return System.currentTimeMillis() / 1_000L + seconds
    }

    /** Kept internal so the API response contract can be tested without network access. */
    internal fun parseRatings(json: String, imdbId: String?): MdbListRatings? {
        return try {
            val root = JSONObject(json)
            if (!isResponseAccepted(root)) return null

            var imdb: Double? = null
            var tomatoes: Int? = null
            var tomatoesAudience: Int? = null
            var metacritic: Int? = null
            var letterboxd: Double? = null
            var tmdb: Double? = null
            var trakt: Double? = null

            fun addScore(category: ScoreCategory, raw: Double) {
                when (category) {
                    ScoreCategory.IMDB -> if (imdb == null) imdb = raw
                    ScoreCategory.TOMATOES -> if (tomatoes == null) tomatoes = raw.toInt().coerceIn(0, 100)
                    ScoreCategory.TOMATOES_AUDIENCE -> if (tomatoesAudience == null) tomatoesAudience = raw.toInt().coerceIn(0, 100)
                    ScoreCategory.METACRITIC -> if (metacritic == null) metacritic = raw.toInt().coerceIn(0, 100)
                    ScoreCategory.LETTERBOXD -> if (letterboxd == null) letterboxd = raw
                    ScoreCategory.TMDB -> if (tmdb == null) tmdb = normalizeValue(raw, category)
                    ScoreCategory.TRAKT -> if (trakt == null) trakt = raw
                }
            }

            fun consumeItem(item: JSONObject, fallbackSource: String? = null) {
                val source = item.optString("source", "").ifBlank { fallbackSource.orEmpty() }
                val category = scoreCategory(
                    source = source,
                    name = item.optString("name", ""),
                    type = item.optString("type", "")
                ) ?: return
                val raw = firstNumber(item.opt("value"), item.opt("rating"))
                    ?: scoreFallback(item.opt("score"), category)
                    ?: return
                addScore(category, raw)
            }

            fun consumeValue(source: String, value: Any?) {
                val item = value as? JSONObject
                if (item != null) {
                    consumeItem(item, fallbackSource = source)
                    return
                }
                val category = scoreCategory(source, name = "", type = "") ?: return
                val raw = firstNumber(value) ?: return
                addScore(category, raw)
            }

            val ratingsArray: JSONArray? = root.optJSONArray("ratings") ?: root.optJSONArray("scores")
            val ratingsObject: JSONObject? = root.optJSONObject("ratings") ?: root.optJSONObject("scores")
            if (ratingsArray != null || ratingsObject != null) {
                ratingsArray?.let { ratings ->
                    for (index in 0 until ratings.length()) {
                        ratings.optJSONObject(index)?.let { consumeItem(it) }
                    }
                }
                ratingsObject?.let { ratings ->
                    val keys = ratings.keys()
                    while (keys.hasNext()) {
                        val source = keys.next()
                        consumeValue(source, ratings.opt(source))
                    }
                }
                val currentRatings = MdbListRatings(
                    imdb = imdb,
                    tomatoes = tomatoes,
                    tomatoesAudience = tomatoesAudience,
                    metacritic = metacritic,
                    letterboxd = letterboxd,
                    tmdb = tmdb,
                    trakt = trakt,
                    imdbId = imdbId ?: root.optString("imdbid", "").takeIf(imdbIdPattern::matches)
                ).withoutEmptyScores()
                if (!currentRatings.isEmpty) return currentRatings
            }

            // Older MDBList responses expose scores as top-level fields. Keep common aliases
            // too, because the API has used both source names and rating-suffixed field names.
            val legacy = MdbListRatings(
                imdb = firstNumber(root.opt("imdbrating"), root.opt("imdb")),
                tomatoes = firstNumber(root.opt("tomatoesrating"), root.opt("rottentomatoesrating"), root.opt("tomatoes"))
                    ?.toInt()?.coerceIn(0, 100),
                tomatoesAudience = firstNumber(
                    root.opt("tomatoesaudiencerating"),
                    root.opt("rottentomatoesaudiencerating"),
                    root.opt("tomatoesaudience")
                )?.toInt()?.coerceIn(0, 100),
                metacritic = firstNumber(root.opt("metacriticrating"), root.opt("metacritic"))
                    ?.toInt()?.coerceIn(0, 100),
                letterboxd = firstNumber(root.opt("letterboxdrating"), root.opt("letterboxd")),
                tmdb = firstNumber(root.opt("tmdbrating"), root.opt("tmdb"), root.opt("themoviedb"), root.opt("themoviedatabase"))?.let { normalizeValue(it, ScoreCategory.TMDB) },
                trakt = firstNumber(root.opt("traktrating"), root.opt("trakt")),
                imdbId = imdbId ?: root.optString("imdbid", "").takeIf(imdbIdPattern::matches)
            ).withoutEmptyScores()

            when {
                !legacy.isEmpty -> legacy
                // A valid response with an empty ratings list is a real negative lookup and may be cached.
                root.has("response") || root.has("ratings") || root.has("scores") -> legacy
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "MDBList JSON parse failed (${e.javaClass.simpleName})")
            null
        }
    }

    private fun isResponseAccepted(root: JSONObject): Boolean {
        if (!root.has("response") || root.isNull("response")) return true
        return when (val response = root.opt("response")) {
            is Boolean -> response
            is Number -> response.toInt() == 1
            is String -> response.trim().equals("true", ignoreCase = true) || response.trim() == "1"
            else -> false
        }
    }

    private enum class ScoreCategory {
        IMDB, TOMATOES, TOMATOES_AUDIENCE, METACRITIC, LETTERBOXD, TMDB, TRAKT
    }

    private fun scoreCategory(source: String, name: String, type: String): ScoreCategory? {
        val normalized = "$source $name $type".lowercase().filter(Char::isLetterOrDigit)
        val isTomatoes = normalized.contains("tomato") || normalized.contains("rottentomato") || normalized.startsWith("rt")
        return when {
            normalized.contains("popcorn") ||
                (isTomatoes && (normalized.contains("audience") || normalized.contains("user"))) -> ScoreCategory.TOMATOES_AUDIENCE
            isTomatoes || normalized.contains("tomatometer") -> ScoreCategory.TOMATOES
            normalized.contains("metacritic") || normalized.contains("metascore") -> ScoreCategory.METACRITIC
            normalized.contains("letterboxd") -> ScoreCategory.LETTERBOXD
            normalized.contains("imdb") -> ScoreCategory.IMDB
            normalized.contains("trakt") -> ScoreCategory.TRAKT
            normalized.contains("tmdb") || normalized.contains("themoviedb") || normalized.contains("themoviedatabase") -> ScoreCategory.TMDB
            else -> null
        }
    }

    /** Prefer MDBList's human-readable value (e.g. "8.2/10" or "91%") over its normalized score. */
    private fun firstNumber(vararg values: Any?): Double? {
        for (value in values) {
            val number = parseNumber(value)
            if (number != null && number > 0.0) return number
        }
        return null
    }

    private fun parseNumber(value: Any?): Double? {
        val text = when (value) {
            null, JSONObject.NULL -> return null
            is Number -> value.toString()
            is String -> value.trim()
            else -> return null
        }
        val match = Regex("[-+]?\\d+(?:\\.\\d+)?").find(text) ?: return null
        return match.value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    /** MDBList's value uses the source scale; TMDB is commonly returned as 0–100. */
    private fun normalizeValue(value: Double, category: ScoreCategory): Double = when (category) {
        ScoreCategory.TMDB -> if (value > 10.0 && value <= 100.0) value / 10.0 else value
        else -> value
    }

    private fun scoreFallback(value: Any?, category: ScoreCategory): Double? {
        val score = parseNumber(value) ?: return null
        return when (category) {
            ScoreCategory.IMDB, ScoreCategory.TMDB ->
                if (score > 10.0 && score <= 100.0) score / 10.0 else score
            ScoreCategory.LETTERBOXD ->
                if (score > 5.0 && score <= 100.0) score / 20.0 else score
            // Trakt is displayed as a percentage, just like MDBList's value/score fields.
            ScoreCategory.TRAKT,
            ScoreCategory.TOMATOES,
            ScoreCategory.TOMATOES_AUDIENCE,
            ScoreCategory.METACRITIC -> score
        }
    }

    private fun fingerprint(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /** Tests may clear the process-local cache between scenarios. */
    internal fun clearCache() = cache.clear()
}
