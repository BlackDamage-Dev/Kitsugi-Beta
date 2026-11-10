package com.kitsugi.animelist.data.remote

import android.content.Context
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
import com.kitsugi.animelist.data.auth.PlatformRateLimiter
import com.kitsugi.animelist.data.auth.TmdbAuthStore
import com.kitsugi.animelist.data.auth.TmdbAccountState
import com.kitsugi.animelist.data.auth.tmdbRatingValue
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * TMDB's official v3 user-session API. The catalog API key is never treated as a user
 * credential. All account collections are paged and a failed page aborts the whole snapshot.
 * v4-only mixed/private custom-list writes are intentionally not faked with a v3 session.
 */
object TmdbAccountClient {
    private const val API_BASE = "https://api.themoviedb.org/3"
    private const val REDIRECT_URI = "malapp://auth"
    private const val MAX_PAGES = 500
    private val client = KitsugiHttpClient.metadataClient.newBuilder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Session(val sessionId: String, val accountId: Int, val username: String)

    suspend fun createAuthorizationUrl(context: Context): String = withContext(Dispatchers.IO) {
        val response = get("$API_BASE/authentication/token/new?api_key=${apiKey()}")
        val requestToken = response.optString("request_token").trim()
        if (requestToken.isBlank()) throw IOException(response.apiError("TMDB request token alınamadı"))
        TmdbAuthStore.savePendingRequestToken(context, requestToken)
        "https://www.themoviedb.org/authenticate/$requestToken?redirect_to=${encode(REDIRECT_URI)}"
    }

    suspend fun createSession(requestToken: String): Session = withContext(Dispatchers.IO) {
        val body = JSONObject().put("request_token", requestToken).toString().jsonBody()
        val response = post("$API_BASE/authentication/session/new?api_key=${apiKey()}", body)
        val sessionId = response.optString("session_id").trim()
        if (sessionId.isBlank()) throw IOException(response.apiError("TMDB oturumu oluşturulamadı"))
        val account = get("$API_BASE/account?api_key=${apiKey()}&session_id=${encode(sessionId)}")
        val accountId = account.optInt("id", 0)
        if (accountId <= 0) throw IOException(account.apiError("TMDB hesap bilgisi alınamadı"))
        Session(sessionId, accountId, account.optString("username").trim())
    }

    /** Backwards-compatible Watchlist-only read; new code should use [fetchAccountSnapshot]. */
    suspend fun fetchWatchlist(sessionId: String, accountId: Int): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            fetchCollection(sessionId, accountId, "watchlist", "movies") +
                fetchCollection(sessionId, accountId, "watchlist", "tv")
        }

    /**
     * Read Watchlist, favorites and ratings. The union represents the TMDB account tab;
     * an item can belong to more than one collection without creating duplicate local rows.
     */
    suspend fun fetchAccountSnapshot(sessionId: String, accountId: Int): TmdbAccountSnapshot =
        withContext(Dispatchers.IO) {
            val watchlist = fetchWatchlist(sessionId, accountId)
            val favorites = fetchCollection(sessionId, accountId, "favorite", "movies") +
                fetchCollection(sessionId, accountId, "favorite", "tv")
            val rated = fetchCollection(sessionId, accountId, "rated", "movies") +
                fetchCollection(sessionId, accountId, "rated", "tv")
            val union = LinkedHashMap<String, MediaEntry>()
            (watchlist + favorites + rated).forEach { incoming ->
                val key = incoming.accountKey()
                val previous = union[key]
                union[key] = if (previous == null) incoming else previous.copy(
                    isFavorite = previous.isFavorite || incoming.isFavorite,
                    tmdbWatchlist = previous.tmdbWatchlist == true || incoming.tmdbWatchlist == true,
                    tmdbRating = incoming.tmdbRating ?: previous.tmdbRating,
                    score = incoming.score ?: previous.score,
                    imageUrl = previous.imageUrl ?: incoming.imageUrl,
                    synopsis = previous.synopsis ?: incoming.synopsis,
                    titleEnglish = previous.titleEnglish ?: incoming.titleEnglish
                )
            }
            TmdbAccountSnapshot(union.values.toList(), watchlist, favorites, rated)
        }

    /** Read one item's native TMDB state without creating a local library row. */
    suspend fun fetchAccountState(
        sessionId: String,
        type: MediaType,
        tmdbId: Int
    ): TmdbAccountState = withContext(Dispatchers.IO) {
        val kind = accountKind(type)
        val response = get(
            "$API_BASE/$kind/$tmdbId/account_states?api_key=${apiKey()}&session_id=${encode(sessionId)}"
        )
        val ratedValue = response.opt("rated")
        val rating = when (ratedValue) {
            is JSONObject -> ratedValue.optDouble("value", 0.0).takeIf { it > 0.0 }
            is Number -> ratedValue.toDouble().takeIf { it > 0.0 }
            else -> null
        }
        TmdbAccountState(
            favorite = response.optBoolean("favorite", false),
            watchlist = response.optBoolean("watchlist", false),
            rating = rating
        )
    }

    suspend fun setWatchlist(
        sessionId: String,
        accountId: Int,
        type: MediaType,
        tmdbId: Int,
        value: Boolean
    ) = withContext(Dispatchers.IO) {
        require(tmdbId > 0) { "TMDB kimliği gerekli" }
        val body = JSONObject()
            .put("media_type", accountKind(type))
            .put("media_id", tmdbId)
            .put("watchlist", value)
            .toString().jsonBody()
        ensureWriteSuccess(post(accountUrl(accountId, "watchlist", sessionId), body), "Watchlist")
    }

    suspend fun setFavorite(
        sessionId: String,
        accountId: Int,
        type: MediaType,
        tmdbId: Int,
        value: Boolean
    ) = withContext(Dispatchers.IO) {
        require(tmdbId > 0) { "TMDB kimliği gerekli" }
        val body = JSONObject()
            .put("media_type", accountKind(type))
            .put("media_id", tmdbId)
            .put("favorite", value)
            .toString().jsonBody()
        ensureWriteSuccess(post(accountUrl(accountId, "favorite", sessionId), body), "Favori")
    }

    /** A null/zero score deletes the rating; otherwise TMDB's half-point scale is used. */
    suspend fun setRating(
        sessionId: String,
        type: MediaType,
        tmdbId: Int,
        score: Double?
    ) = withContext(Dispatchers.IO) {
        require(tmdbId > 0) { "TMDB kimliği gerekli" }
        val kind = accountKind(type)
        val rating = tmdbRatingValue(score)
        if (rating == null) {
            val response = delete(
                "$API_BASE/$kind/$tmdbId/rating?api_key=${apiKey()}&session_id=${encode(sessionId)}"
            )
            ensureWriteSuccess(response, "Puan silme")
        } else {
            val body = JSONObject().put("value", rating).toString().jsonBody()
            val response = post(
                "$API_BASE/$kind/$tmdbId/rating?api_key=${apiKey()}&session_id=${encode(sessionId)}",
                body
            )
            ensureWriteSuccess(response, "Puan")
        }
    }

    /** Official v3 account list metadata. v3 custom-list item writes are movie-only. */
    suspend fun fetchLists(sessionId: String, accountId: Int): List<TmdbListSummary> =
        withContext(Dispatchers.IO) {
            fetchPagedObjects(accountUrl(accountId, "lists", sessionId)).map { item ->
                TmdbListSummary(
                    id = item.optInt("id", 0),
                    name = item.optString("name").trim(),
                    description = item.optString("description"),
                    itemCount = item.optInt("item_count", 0)
                )
            }.filter { it.id > 0 && it.name.isNotBlank() }
        }

    suspend fun fetchListMovies(sessionId: String, listId: Int): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val response = get(
                "$API_BASE/list/$listId?api_key=${apiKey()}&session_id=${encode(sessionId)}"
            )
            val items = response.optJSONArray("items")
                ?: throw IOException("TMDB liste yanıtında items yok")
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index)
                        ?: throw IOException("TMDB liste öğesi okunamadı")
                    add(entryFromJson(item, "movies"))
                }
            }
        }

    suspend fun createList(
        sessionId: String,
        name: String,
        description: String = "",
        language: String = "tr"
    ): Int = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Liste adı gerekli" }
        val body = JSONObject()
            .put("name", name.trim())
            .put("description", description)
            .put("language", language)
            .toString().jsonBody()
        val response = post(
            "$API_BASE/list?api_key=${apiKey()}&session_id=${encode(sessionId)}",
            body
        )
        ensureWriteSuccess(response, "Liste oluşturma")
        response.optInt("list_id", 0).takeIf { it > 0 }
            ?: throw IOException("TMDB liste kimliği dönmedi")
    }

    /** Official item_status probe: avoids downloading every list just to render checkboxes. */
    suspend fun isMovieInList(sessionId: String, listId: Int, tmdbId: Int): Boolean =
        withContext(Dispatchers.IO) {
            require(listId > 0 && tmdbId > 0) { "Geçerli liste ve film kimliği gerekli" }
            val response = get(
                "$API_BASE/list/$listId/item_status?api_key=${apiKey()}&session_id=${encode(sessionId)}&movie_id=$tmdbId"
            )
            response.optBoolean("item_present", false)
        }

    suspend fun addMovieToList(sessionId: String, listId: Int, tmdbId: Int) =
        changeMovieListMembership(sessionId, listId, tmdbId, add = true)

    suspend fun removeMovieFromList(sessionId: String, listId: Int, tmdbId: Int) =
        changeMovieListMembership(sessionId, listId, tmdbId, add = false)

    private suspend fun changeMovieListMembership(
        sessionId: String,
        listId: Int,
        tmdbId: Int,
        add: Boolean
    ) = withContext(Dispatchers.IO) {
        require(listId > 0 && tmdbId > 0) { "Geçerli liste ve film kimliği gerekli" }
        val endpoint = if (add) "add_item" else "remove_item"
        val body = JSONObject().put("media_id", tmdbId).toString().jsonBody()
        val response = post(
            "$API_BASE/list/$listId/$endpoint?api_key=${apiKey()}&session_id=${encode(sessionId)}",
            body
        )
        ensureWriteSuccess(response, "Özel liste")
    }

    private suspend fun fetchCollection(
        sessionId: String,
        accountId: Int,
        collection: String,
        kind: String
    ): List<MediaEntry> {
        return fetchPagedObjects(accountUrl(accountId, "$collection/$kind", sessionId)).map { item ->
            val nativeRating = if (collection == "rated") {
                item.optDouble("rating", 0.0).takeIf { it > 0.0 }
            } else null
            val rating = nativeRating?.roundToInt()
            entryFromJson(
                item = item,
                kind = kind,
                favorite = collection == "favorite",
                watchlist = collection == "watchlist",
                nativeRating = nativeRating,
                score = rating
            )
        }
    }

    private suspend fun fetchPagedObjects(baseUrl: String): List<JSONObject> {
        val output = mutableListOf<JSONObject>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages) {
            if (page > MAX_PAGES) throw IOException("TMDB sayfalama güvenlik sınırı aşıldı")
            val separator = if (baseUrl.contains('?')) '&' else '?'
            val response = get("$baseUrl${separator}page=$page")
            val results = response.optJSONArray("results")
                ?: throw IOException("TMDB sayfa $page yanıtında results yok")
            for (index in 0 until results.length()) {
                output += results.optJSONObject(index)
                    ?: throw IOException("TMDB sayfa $page öğesi okunamadı")
            }
            totalPages = response.optInt("total_pages", page).coerceAtLeast(page)
            page += 1
        }
        return output
    }

    private fun entryFromJson(
        item: JSONObject,
        kind: String,
        favorite: Boolean = false,
        watchlist: Boolean = false,
        nativeRating: Double? = null,
        score: Int? = null
    ): MediaEntry {
        val tmdbId = item.optInt("id", 0)
        if (tmdbId <= 0) throw IOException("TMDB hesap öğesinde geçerli id yok")
        val isMovie = kind == "movies"
        val title = item.optString(if (isMovie) "title" else "name").trim()
        if (title.isBlank()) throw IOException("TMDB hesap öğesinde başlık yok ($tmdbId)")
        val originalTitle = item.optString(if (isMovie) "original_title" else "original_name")
            .trim().takeIf { it.isNotBlank() && it != title }
        val year = item.optString(if (isMovie) "release_date" else "first_air_date")
            .take(4).toIntOrNull()
        val typeLabel = if (isMovie) "Film" else "Dizi"
        return MediaEntry(
            id = 0,
            title = title,
            subtitle = year?.let { "$typeLabel • $it" } ?: typeLabel,
            type = if (isMovie) MediaType.Movie else MediaType.TvShow,
            status = WatchStatus.Planned,
            score = score,
            progress = 0,
            total = null,
            isFavorite = favorite,
            tmdbWatchlist = watchlist,
            tmdbRating = nativeRating,
            isAdult = item.optBoolean("adult", false),
            source = TMDB_ACCOUNT_SOURCE,
            malId = null,
            imageUrl = item.optString("poster_path").takeIf { it.isNotBlank() }
                ?.let { "https://image.tmdb.org/t/p/w500$it" },
            year = year,
            tmdbId = tmdbId,
            titleEnglish = originalTitle
        )
    }

    private fun MediaEntry.accountKey(): String = "${type.name}:${tmdbId}"

    private fun accountKind(type: MediaType): String = when (type) {
        MediaType.Movie -> "movie"
        MediaType.TvShow -> "tv"
        else -> throw IllegalArgumentException("TMDB hesap API'si yalnız film/TV satırlarını destekler")
    }

    private fun accountUrl(accountId: Int, path: String, sessionId: String): String =
        "$API_BASE/account/$accountId/$path?api_key=${apiKey()}&session_id=${encode(sessionId)}"

    private fun apiKey(): String = TmdbApiClient.getActiveApiKey()

    private suspend fun get(url: String): JSONObject = execute(Request.Builder().url(url).get().build())

    private suspend fun post(url: String, body: RequestBody): JSONObject =
        execute(Request.Builder().url(url).post(body).build())

    private suspend fun delete(url: String): JSONObject =
        execute(Request.Builder().url(url).delete().build())

    private suspend fun execute(request: Request): JSONObject {
        val authenticatedRequest = request.newBuilder().header("Accept", "application/json").build()
        var rateLimitRetried = false
        while (true) {
            // Every request—not merely every high-level sync operation—uses the shared limiter.
            PlatformRateLimiter.acquire("tmdb")
            var retryAfterSeconds: Long? = null
            val responseJson = client.newCall(authenticatedRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                if (response.code == 429 && !rateLimitRetried) {
                    retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
                    return@use null
                }
                if (!response.isSuccessful) {
                    throw IOException(json.apiError("TMDB HTTP ${response.code}"))
                }
                json
            }
            if (responseJson != null) return responseJson

            // One bounded retry for a real 429. Persistent failures still reach the pending
            // queue; a long Retry-After must not pin a UI/WorkManager coroutine for hours.
            val waitSeconds = (retryAfterSeconds ?: 3L).coerceIn(1L, 10L)
            rateLimitRetried = true
            PlatformRateLimiter.notifyRateLimited("tmdb", waitSeconds)
        }
    }

    private fun ensureWriteSuccess(response: JSONObject, operation: String) {
        val statusCode = response.optInt("status_code", -1)
        val success = response.optBoolean("success", statusCode in setOf(1, 12, 13))
        if (!success && statusCode !in setOf(1, 12, 13)) {
            throw IOException(response.apiError("TMDB $operation yazılamadı"))
        }
    }

    private fun String.jsonBody(): RequestBody =
        toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JSONObject.apiError(fallback: String): String {
        val detail = optString("status_message").takeIf { it.isNotBlank() } ?: fallback
        val statusCode = optInt("status_code", 0).takeIf { it > 0 }
        return statusCode?.let { "TMDB-$it: $detail" } ?: detail
    }
}
