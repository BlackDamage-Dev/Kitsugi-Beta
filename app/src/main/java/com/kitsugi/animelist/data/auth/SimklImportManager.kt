package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.Request
import com.kitsugi.animelist.data.remote.optNullableString
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.network.SimklRateLimitPolicy

object SimklImportManager {

    private val client = com.kitsugi.animelist.core.network.KitsugiHttpClient.client
    // T3-01: BuildConfig'den alınır
    private val CLIENT_ID get() = com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID

    data class SimklUserProfile(
        val name: String,
        val avatarUrl: String?,
        val bannerUrl: String?,
        val joinedAt: String? = null,
        val location: String? = null,
        val bio: String? = null,
        val accountType: String? = null
    )

    suspend fun fetchUserProfile(token: String): SimklUserProfile {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.simkl.com/users/settings")
                .header("Authorization", "Bearer $token")
                .header("simkl-api-key", CLIENT_ID)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
                    if (context != null) {
                        com.kitsugi.animelist.data.auth.ExternalAuthManager.handleSimkl401(context)
                    }
                    throw com.kitsugi.animelist.data.repository.SimklAuthException("Simkl yetkilendirme hatası: 401")
                }
                if (!response.isSuccessful) {
                    throw IllegalStateException("Simkl profil hatası: ${response.code}")
                }
                val responseText = response.body?.string().orEmpty()
                val root = JSONObject(responseText)
                val user = root.optJSONObject("user")
                val name = user?.optNullableString("name") ?: KitsugiStrings.get(R.string.simkl_kullanicisi)
                val avatar = user?.optNullableString("avatar")
                val avatarUrl = if (!avatar.isNullOrBlank()) "https://simkl.in/avatars/${avatar}_m.jpg" else null

                val joinedAt = user?.optNullableString("joined_at")
                val location = user?.optNullableString("location")
                val bio = user?.optNullableString("bio")
                val account = root.optJSONObject("account")
                val accountType = account?.optNullableString("type")

                SimklUserProfile(
                    name = name,
                    avatarUrl = avatarUrl,
                    bannerUrl = null,
                    joinedAt = joinedAt,
                    location = location,
                    bio = bio,
                    accountType = accountType
                )
            }
        }
    }

    /**
     * Simkl'in **ucuz değişiklik imzası** (`/sync/activities`).
     *
     * Simkl'de `sync/all-items` uçları tam listeyi döndürür ve "şu tarihten sonra
     * değişenler" parametresi yoktur. Bu yüzden yenileme öncesi tek küçük istekle
     * kullanıcının film/dizi/anime listelerinde en son ne zaman bir şey değiştiği
     * öğrenilir; imza son senkronla aynıysa tam liste HİÇ çekilmez.
     *
     * @return revizyon imzası; uç kullanılamıyorsa/boşsa `null` (çağıran tam çekime düşer)
     */
    suspend fun fetchActivitiesRevision(token: String): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.simkl.com/sync/activities")
                    .header("Authorization", "Bearer $token")
                    .header("simkl-api-key", CLIENT_ID)
                    .header("User-Agent", "KitsugiApp/2.4")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank() || body.trim() == "null") return@use null
                    val root = runCatching { JSONObject(body) }.getOrNull() ?: return@use null

                    val parts = mutableListOf<String>()
                    root.optString("all").takeIf { it.isNotBlank() && it != "null" }
                        ?.let { parts.add("all=$it") }
                    for (key in listOf("movies", "shows", "anime", "tv_shows")) {
                        root.optJSONObject(key)?.let { parts.add("$key=$it") }
                    }
                    if (parts.isEmpty()) null else parts.joinToString("|")
                }
            }.getOrNull()
        }
    }

    suspend fun fetchAllLists(token: String): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val movies = fetchList(token, "movies")
            kotlinx.coroutines.delay(1500L)
            val shows = fetchList(token, "shows")
            kotlinx.coroutines.delay(1500L)
            val anime = fetchList(token, "anime")
            val merged = deduplicateImportedEntries(movies + shows + anime)
            val appContext = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            if (appContext != null) {
                merged.forEach { entry ->
                    val realMal = entry.malId?.takeIf { it in 1..99_999_999 }
                    val simkl = entry.simklId?.takeIf { it > 0 }
                    if (realMal != null && simkl != null) {
                        CrossProviderIdentityCache.writeBundle(
                            context = appContext,
                            type = entry.type,
                            ids = CrossProviderIds(
                                malId = realMal,
                                simklId = simkl,
                                tmdbId = entry.tmdbId?.takeIf { it > 0 }
                            ),
                            complete = false,
                            primaryKey = "${entry.type.name}:mal:$realMal"
                        )
                    }
                }
            }
            merged
        }
    }

    internal fun deduplicateImportedEntries(entries: List<MediaEntry>): List<MediaEntry> {
        if (entries.size <= 1) return entries
        val out = mutableListOf<MediaEntry>()
        for (incoming in entries) {
            val idx = out.indexOfFirst { existing ->
                com.kitsugi.animelist.model.MediaIdentity.librarySameMedia(existing, incoming)
            }
            if (idx < 0) {
                out += incoming
            } else {
                val prev = out[idx]
                // Anime + Movie çakışmasında gerçek MAL kimliği taşıyan Anime kaydı tercih edilir
                // ki "Tümü" görünümünde AniList/MAL/Kitsu/Shikimori/Bangumi ile doğrudan birleşsin.
                val preferIncomingPrimary = (incoming.type == MediaType.Anime && prev.type == MediaType.Movie) ||
                    (incoming.malId != null && prev.malId == null)
                val base = if (preferIncomingPrimary) incoming else prev
                val other = if (preferIncomingPrimary) prev else incoming
                out[idx] = base.copy(
                    score = base.score ?: other.score,
                    progress = maxOf(base.progress, other.progress),
                    total = base.total ?: other.total,
                    isAdult = base.isAdult || other.isAdult,
                    isFavorite = base.isFavorite || other.isFavorite,
                    malId = base.malId ?: other.malId,
                    tmdbId = base.tmdbId ?: other.tmdbId,
                    simklId = base.simklId ?: other.simklId,
                    imageUrl = base.imageUrl?.takeIf { it.isNotBlank() } ?: other.imageUrl,
                    year = base.year ?: other.year
                )
            }
        }
        return out
    }

    private suspend fun fetchList(token: String, type: String): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            var attempt = 0
            while (attempt < 4) {
                attempt++
                val request = Request.Builder()
                    // (E80) `memos=yes`: kullanıcının özel notu (`memo.text`, ≤140 krk) liste
                    // yanıtına eklenir. Olmasaydı yazıcı `private_memo` yazıyor ama içe
                    // aktarma notu hiç okumuyordu; üstelik `notes=null` gelen satır,
                    // smartImport'ta yerel notu siliyordu.
                    .url("https://api.simkl.com/sync/all-items/$type?memos=yes")
                    .header("Authorization", "Bearer $token")
                    .header("simkl-api-key", CLIENT_ID)
                    .header("User-Agent", "KitsugiApp/2.4")
                    .build()

                val entries = mutableListOf<MediaEntry>()

                try {
                    val (retryWaitMs, parsedEntries) = client.newCall(request).execute().use { response ->
                        if (response.code == 401) {
                            val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
                            if (context != null) {
                                com.kitsugi.animelist.data.auth.ExternalAuthManager.handleSimkl401(context)
                            }
                            throw com.kitsugi.animelist.data.repository.SimklAuthException("Simkl yetkilendirme hatası: 401")
                        }
                        if (response.code == 429) {
                            // RetryInterceptor has already honored Retry-After and retried this call.
                            // Do not multiply a persistent 429 into four more full-library requests.
                            throw com.kitsugi.animelist.data.repository.SimklRateLimitException(
                                "Simkl liste okuma hız sınırı [$type]: HTTP 429"
                            )
                        }
                        if (response.code == 400 && SimklRateLimitPolicy.isRateLimitedResponse(response)) {
                            throw com.kitsugi.animelist.data.repository.SimklRateLimitException(
                                "Simkl liste okuma hız sınırı [$type]: rate_limit"
                            )
                        }
                        if (!response.isSuccessful) {
                            android.util.Log.e("SimklImportManager", "Simkl fetchList [$type] failed: HTTP ${response.code}")
                            throw IllegalStateException("Simkl liste okuma hatası [$type]: HTTP ${response.code}")
                        }
                        val responseText = response.body?.string().orEmpty()
                        if (responseText.trim() == "null") return@use Pair(0L, emptyList<MediaEntry>())
                        val root = JSONObject(responseText)
                        val arrayKey = when (type) {
                            "movies" -> "movies"
                            "shows" -> "shows"
                            else -> "anime"
                        }
                        val jsonArray = root.optJSONArray(arrayKey) ?: return@use Pair(0L, emptyList<MediaEntry>())

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val statusStr = item.optNullableString("status")
                        val watchStatus = mapSimklStatus(statusStr)

                        val mediaObjKey = com.kitsugi.animelist.data.remote.SimklSyncContract.readMediaKey(type)
                        val mediaObj = item.optJSONObject(mediaObjKey)
                            ?: throw IllegalStateException("Simkl [$type]: $mediaObjKey nesnesi eksik")
                        val title = mediaObj.optNullableString("title") ?: KitsugiStrings.get(R.string.basliksiz)
                        val year = mediaObj.optInt("year", 0).takeIf { it > 0 }
                        val poster = mediaObj.optNullableString("poster")
                        val imageUrl = if (!poster.isNullOrBlank()) "https://simkl.in/posters/${poster}_m.jpg" else null

                        val ids = mediaObj.optJSONObject("ids")
                        val simklId = ids?.optInt("simkl", 0)?.takeIf { it > 0 }
                        val tmdbId = ids?.optInt("tmdb", 0)?.takeIf { it > 0 }
                        val malId = ids?.optInt("mal", 0)?.takeIf { it > 0 }

                        val mediaType = when (type) {
                            "movies" -> MediaType.Movie
                            "shows" -> MediaType.TvShow
                            else -> MediaType.Anime
                        }

                        val progress = when (mediaType) {
                            MediaType.Movie -> if (watchStatus == WatchStatus.Completed) 1 else 0
                            else -> item.optInt("watched_episodes_count", 0)
                        }

                        val total = when (mediaType) {
                            MediaType.Movie -> 1
                            else -> item.optInt("total_episodes_count", 0).takeIf { it > 0 }
                        }

                        // Parse rating if present (Simkl user_rating or rating)
                        val score = item.optInt("user_rating", 0).takeIf { it > 0 }
                            ?: item.optInt("rating", 0).takeIf { it > 0 }

                        // Simkl `/sync/all-items` yanıtı GERÇEK eklenme zamanını verir:
                        // `added_to_watchlist_at` (ISO-8601). Bu alan daha önce hiç
                        // okunmuyordu; "Son eklenen" Simkl'de `last_watched_at` vekiline
                        // düşüyordu, yani bir diziyi izlemek onu "listeye yeni eklenmiş"
                        // gibi en üste taşıyordu. Artık iki alan ayrı ayrı taşınıyor:
                        //   • addedAt   → added_to_watchlist_at (listeye eklenme)
                        //   • updatedAt → last_watched_at       (son aktivite)
                        // Bkz. https://api.simkl.org/api-reference/simkl/get-all-items
                        val addedToWatchlistAt = item.optNullableString("added_to_watchlist_at")?.let { raw ->
                            runCatching { java.time.Instant.parse(raw).epochSecond }.getOrDefault(0L)
                        } ?: 0L
                        val lastWatchedAt = item.optNullableString("last_watched_at")?.let { raw ->
                            runCatching { java.time.Instant.parse(raw).epochSecond }.getOrDefault(0L)
                        } ?: 0L

                        // malId SADECE ve SADECE Simkl API'sinin gerçekten 'mal' olarak döndüğü gerçek MAL ID'dir.
                        // Asla simklId veya uydurma ID malId yerine konulmaz! Aksi halde CrossSync rastgele MAL animeleriyle eşleştirir!
                        val realMalId = if (mediaType == MediaType.Anime && malId != null && malId > 0 && malId < 100_000_000) malId else null
                        val stableDbId = if (simklId != null) 150_000_000 + (simklId % 100_000_000) else (150_000_000 + i)

                        entries.add(
                            MediaEntry(
                                id = stableDbId,
                                title = title,
                                subtitle = when (mediaType) {
                                    MediaType.Movie -> "Film"
                                    MediaType.TvShow -> "Dizi"
                                    MediaType.Anime -> "Anime"
                                    else -> "Manga"
                                }.let { label ->
                                    if (year != null) "$label • $year" else label
                                },
                                type = mediaType,
                                status = watchStatus,
                                score = score,
                                progress = progress,
                                total = total,
                                isFavorite = false,
                                isAdult = com.kitsugi.animelist.data.remote.SimklAdultFlags.isAdult(mediaObj),
                                source = "simkl",
                                malId = realMalId,
                                imageUrl = imageUrl,
                                year = year,
                                synopsis = null,
                                notes = simklMemoText(item),
                                updatedAt = lastWatchedAt,
                                // Listeye eklenme zamanı Simkl'in kendi alanından gelir;
                                // alan yoksa 0 kalır ve `resolveAddedAt` ilk-görülme
                                // vekilini devreye alır.
                                addedAt = addedToWatchlistAt,
                                tmdbId = tmdbId,
                                simklId = simklId
                            )
                        )
                    }
                    Pair(0L, entries)
                }
                if (retryWaitMs == 0L) {
                    return@withContext parsedEntries
                }
                kotlinx.coroutines.delay(retryWaitMs)
            } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
                throw e
            } catch (e: com.kitsugi.animelist.data.repository.SimklRateLimitException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SimklImportManager", "fetchList [$type] failed: ${e.message}", e)
                if (attempt >= 4) throw e
                kotlinx.coroutines.delay(2000L * attempt)
            }
        }
        throw IllegalStateException("Simkl [$type]: hız sınırı nedeniyle liste alınamadı (4 deneme)")
    }
}

    /**
     * Simkl `memos=yes` yanıtındaki `memo` nesnesinden not metnini çıkarır (E80).
     *
     * Resmî sözleşme (api.simkl.org/guides/sync): alan adı tekil `memo`dur; içinde
     * `text` (≤140 karakter) ve `is_private` bulunur, boş not `{}` olarak gelir.
     * Yazma tarafı `private_memo` düz dizgesini kullanır — okuma/yazma asimetrisi
     * Simkl'in kendi sözleşmesidir. `unit` testleri için görünür.
     */
    internal fun simklMemoText(item: JSONObject): String? =
        item.optJSONObject("memo")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }

    /** `unit` testleri için görünür (E79 regresyonu). */
    internal fun mapSimklStatus(status: String?): WatchStatus {
        return when (status?.lowercase()) {
            "watching", "reading", "rewatching" -> WatchStatus.Watching
            "completed" -> WatchStatus.Completed
            "hold", "on_hold", "paused" -> WatchStatus.Paused
            // (E79) Simkl bırakılanları `notinteresting` kovasında tutar; bu eşleme yokken
            // bırakılan kayıtlar `else` dalından "planlandı" olarak içe aktarılıyordu.
            "dropped", "notinteresting" -> WatchStatus.Dropped
            "plantowatch", "plan_to_watch", "planning" -> WatchStatus.Planned
            else -> WatchStatus.Planned
        }
    }
}
