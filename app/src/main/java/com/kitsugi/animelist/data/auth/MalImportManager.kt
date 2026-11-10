package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import okhttp3.Request
import com.kitsugi.animelist.data.remote.optNullableString
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

object MalImportManager {
    // MAL's nested field selector uses braces, but raw braces are invalid in a URI query
    // (java.net.URI rejects them before OkHttp can issue the request). Keep them percent-encoded;
    // MAL decodes the query parameter to the expected `list_status{...}` syntax.
    private const val ANIME_LIST_URL =
        "https://api.myanimelist.net/v2/users/@me/animelist" +
        "?fields=id,title,alternative_titles,main_picture,start_date,end_date,media_type,status,num_episodes,start_season,nsfw,genres,rating," +
        "list_status%7Bstatus,score,num_episodes_watched,start_date,finish_date,is_favorited,priority,is_rewatching,num_times_rewatched,rewatch_value,tags,comments,updated_at%7D" +
        "&limit=100&nsfw=true"

    private const val MANGA_LIST_URL =
        "https://api.myanimelist.net/v2/users/@me/mangalist" +
        "?fields=id,title,alternative_titles,main_picture,start_date,end_date,media_type,status,num_chapters,start_season,num_volumes,nsfw,genres," +
        "list_status%7Bstatus,score,num_chapters_read,num_volumes_read,start_date,finish_date,is_favorited,priority,is_rereading,num_times_reread,reread_value,tags,comments,updated_at%7D" +
        "&limit=100&nsfw=true"

    internal fun buildAnimeListUrl(showAdultContent: Boolean): String =
        ANIME_LIST_URL.replace("&nsfw=true", "&nsfw=$showAdultContent")

    internal fun buildMangaListUrl(showAdultContent: Boolean): String =
        MANGA_LIST_URL.replace("&nsfw=true", "&nsfw=$showAdultContent")

    suspend fun fetchAnimeList(
        accessToken: String,
        showAdultContent: Boolean = false,
        context: android.content.Context? = null
    ): List<MediaEntry> {
        val url = buildAnimeListUrl(showAdultContent)
        return withContext(Dispatchers.IO) {
            fetchWithOptionalAuthRetry(accessToken, context) { active ->
                fetchPaginatedList(active, url)
            }
        }
    }

    suspend fun fetchMangaList(
        accessToken: String,
        showAdultContent: Boolean = false,
        context: android.content.Context? = null
    ): List<MediaEntry> {
        val url = buildMangaListUrl(showAdultContent)
        return withContext(Dispatchers.IO) {
            fetchWithOptionalAuthRetry(accessToken, context) { active ->
                fetchPaginatedList(active, url)
            }
        }
    }

    suspend fun fetchAllLists(
        accessToken: String,
        showAdultContent: Boolean = false,
        context: android.content.Context? = null
    ): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            fetchWithOptionalAuthRetry(accessToken, context) { active ->
                val anime = fetchPaginatedList(active, buildAnimeListUrl(showAdultContent))
                val manga = fetchPaginatedList(active, buildMangaListUrl(showAdultContent))
                anime + manga
            }
        }
    }

    private suspend fun <T> fetchWithOptionalAuthRetry(
        accessToken: String,
        context: android.content.Context?,
        block: (String) -> T
    ): T {
        return try {
            block(accessToken)
        } catch (e: Exception) {
            val is401 = e.message?.contains("401") == true || e.message?.contains("invalid_token", ignoreCase = true) == true
            if (!is401 || context == null) throw e
            val refreshed = ExternalAuthManager.forceRefreshMalToken(context)
            if (refreshed.isNullOrBlank()) {
                ExternalAuthManager.markSessionExpired(context, "mal")
                throw IllegalStateException("MyAnimeList oturumu sona erdi (HTTP 401)", e)
            }
            block(refreshed)
        }
    }

    /**
     * `list_status` seçicisinden `is_favorited` alanını çıkarır.
     *
     * NEDEN: MAL API v2 `MyListStatusObject` şemasında `is_favorited` **YOKTUR** (resmî
     * şema: status, score, num_*_watched, is_rewatching, updated_at, start_date, finish_date,
     * priority, num_times_*, *_value, tags, comments). Favori alanı sunulmadığı için
     * (bağımsız istemciler de favorileri Jikan üzerinden okuyor) istek
     * `400 invalid field` ile **tümden** düşebilir; o zaman MAL içe aktarma hiç çalışmaz.
     * Bu yardımcı, 400 alındığında alanı atıp sayfayı yeniden dener.
     */
    internal fun withoutFavoritedField(url: String): String = url
        .replace("is_favorited,", "")
        .replace(",is_favorited", "")

    private fun fetchPaginatedList(
        accessToken: String,
        startUrl: String
    ): List<MediaEntry> {
        val allEntries = mutableListOf<MediaEntry>()
        var nextUrl: String? = startUrl
        // MAL bilinmeyen alanı reddederse yalnızca bu bayrak açılır ve sayfa tekrar denenir.
        var dropFavoritedField = false

        val visited = mutableSetOf<String>()
        while (nextUrl != null) {
            check(visited.add(nextUrl)) { "MAL sayfalama döngüsü algılandı" }
            val pageUri = java.net.URI(nextUrl)
            require(pageUri.scheme == "https" && pageUri.host == "api.myanimelist.net") { "Geçersiz MAL sayfa adresi" }
            val response = fetchPageWithFieldFallback(
                accessToken = accessToken,
                url = nextUrl,
                dropFavoritedField = { dropFavoritedField },
                onDropFavoritedField = { dropFavoritedField = true }
            )
            val root = JSONObject(response)
            val dataArray = root.getJSONArray("data")

            if (dataArray != null) {
                for (index in 0 until dataArray.length()) {
                    val wrapper = dataArray.optJSONObject(index) ?: continue
                    val node = wrapper.optJSONObject("node") ?: continue
                    val listStatus = wrapper.optJSONObject("list_status") ?: JSONObject()
                    allEntries.add(nodeToMediaEntry(node = node, listStatus = listStatus))
                }
            }

            nextUrl = root.optJSONObject("paging")?.optNullableString("next")
        }

        return allEntries
    }

    /**
     * Sayfayı getirir; `is_favorited` yüzünden 400 gelirse alanı atıp BİR KEZ daha dener.
     * Sonraki sayfalarda da alan atlanır (MAL'ın `paging.next` adresi aynı seçiciyi taşır).
     */
    private fun fetchPageWithFieldFallback(
        accessToken: String,
        url: String,
        dropFavoritedField: () -> Boolean,
        onDropFavoritedField: () -> Unit
    ): String {
        val firstTry = if (dropFavoritedField()) withoutFavoritedField(url) else url
        return try {
            getJson(accessToken = accessToken, urlString = firstTry)
        } catch (e: IllegalStateException) {
            val message = e.message.orEmpty()
            val looksLikeBadRequest = message.contains(": 400") || message.contains("invalid field") ||
                message.contains("bad_request")
            if (!dropFavoritedField() && looksLikeBadRequest && url.contains("is_favorited")) {
                onDropFavoritedField()
                getJson(accessToken = accessToken, urlString = withoutFavoritedField(url))
            } else {
                throw e
            }
        }
    }

    private fun nodeToMediaEntry(
        node: JSONObject,
        listStatus: JSONObject
    ): MediaEntry {
        val malId = node.optInt("id", 0)

        val title = node.optNullableString("title") ?: KitsugiStrings.get(R.string.basliksiz)

        val rawMediaType = node.optNullableString("media_type").orEmpty()
        val mediaType = if (rawMediaType == "manga" || rawMediaType == "novel" || rawMediaType == "one_shot" || rawMediaType == "manhwa" || rawMediaType == "manhua") {
            MediaType.Manga
        } else {
            MediaType.Anime
        }

        val startSeason = node.optJSONObject("start_season")
        val year = startSeason?.optInt("year", 0)?.takeIf { it > 0 }
            ?: node.optNullableString("start_date")
                .takeIf { it != null && it.length >= 4 }
                ?.take(4)
                ?.toIntOrNull()

        val format = convertMalFormat(rawMediaType)

        val subtitleParts = buildList {
            if (format.isNotBlank()) add(format)
            if (year != null) add(year.toString())
        }

        val picture = node.optJSONObject("main_picture")
        val imageUrl = picture?.optNullableString("large")
            ?: picture?.optNullableString("medium")

        val rawStatus = listStatus.optNullableString("status").orEmpty()
        val status = convertMalListStatus(rawStatus)

        val score = listStatus.optInt("score", 0).takeIf { it > 0 }

        // MAL'ın `my_list_status` nesnesinde ilerleme alanının adı UCUNA göre değişir:
        //  - liste ucu (`/users/@me/animelist`)      → `num_episodes_watched`
        //  - tek kayıt ucu (`/anime/{id}`)           → `num_watched_episodes`
        // MAL-Sync de bu yüzden iki adı eşitler ("// Handle api bug", MyAnimeList_api/single.ts).
        // Tek ad okumak, uç/yanıt değiştiğinde ilerlemeyi **0** okumak ve bu 0'ı diğer beş
        // sağlayıcıya yazmak demekti (sessiz veri kaybı). İkisi de okunur; büyük olan kazanır.
        val progress = if (mediaType == MediaType.Manga) {
            listStatus.optInt("num_chapters_read", 0)
        } else {
            maxOf(
                listStatus.optInt("num_episodes_watched", 0),
                listStatus.optInt("num_watched_episodes", 0)
            )
        }.coerceAtLeast(0)

        val total = if (mediaType == MediaType.Manga) {
            node.optInt("num_chapters", 0).takeIf { it > 0 }
        } else {
            node.optInt("num_episodes", 0).takeIf { it > 0 }
        }

        val volumeProgress = if (mediaType == MediaType.Manga) {
            listStatus.optInt("num_volumes_read", 0)
        } else {
            0
        }.coerceAtLeast(0)

        val priority = listStatus.optInt("priority", 0)
        // MAL API v2 `list_status` alanları: anime için `is_rewatching`, manga için
        // `is_rereading`. `is_repeating` MAL sözleşmesinde YOKTUR — eskiden bu alan
        // istendiği için tekrar durumu hiç okunamıyordu (ve geçersiz alan isteği
        // 400'e yol açabiliyordu). Eski kayıtlarla uyum için yine de okunur.
        // `rewatching`/`rereading` durum SÖZLERİ (eski MAL v1 / bazı istemciler) ayrı bir
        // WatchStatus değildir: MAL'da kayıt `watching`/`reading` kalır, tekrar bilgisi
        // bayrakla taşınır. `WatchStatus.Repeating`e ÇEVİRMEK yanlış olurdu çünkü geri
        // yazımda MAL `Repeating`i `completed` olarak kodluyor (bkz. MalSyncManager.toMalStatus)
        // — devam eden bir yapım içe aktarılırken "tamamlandı"ya dönerdi.
        val isRepeating = listStatus.optBoolean("is_rewatching", false) ||
            listStatus.optBoolean("is_rereading", false) ||
            listStatus.optBoolean("is_repeating", false) ||
            rawStatus.equals("rewatching", ignoreCase = true) ||
            rawStatus.equals("rereading", ignoreCase = true)
        val repeatCount = if (mediaType == MediaType.Manga) {
            listStatus.optInt("num_times_reread", 0)
        } else {
            listStatus.optInt("num_times_rewatched", 0)
        }.coerceAtLeast(0)

        val repeatValue = if (mediaType == MediaType.Manga) {
            listStatus.optInt("reread_value", 0)
        } else {
            listStatus.optInt("rewatch_value", 0)
        }.coerceAtLeast(0)

        val notes = listStatus.optNullableString("comments")

        val tagsJsonArray = listStatus.optJSONArray("tags")
        // MAL `tags` dizisi sözleşmede düz string listesidir; ancak bazı yanıtlar
        // {"name": "..."} nesnesi döndürüyor. Nesne gelirse `optString` ham JSON
        // metnini yazacağı için adı ayrıca okuyoruz.
        val tags = if (tagsJsonArray != null) {
            val list = mutableListOf<String>()
            for (i in 0 until tagsJsonArray.length()) {
                val element = tagsJsonArray.opt(i)
                val name = when (element) {
                    is JSONObject -> element.optNullableString("name")
                    is String -> element
                    else -> null
                }
                name?.takeIf { it.isNotBlank() }?.let { list.add(it) }
            }
            list.joinToString(", ").takeIf { it.isNotBlank() }
        } else null

        val isFavorite = listStatus.optBoolean("is_favorited", false)

        val startDate = listStatus.optNullableString("start_date")
            .takeIf { it?.isValidDateString() == true }

        val endDate = listStatus.optNullableString("finish_date")
            .takeIf { it?.isValidDateString() == true }

        val updatedAtString = listStatus.optNullableString("updated_at")
        val altTitles = node.optJSONObject("alternative_titles")
        val titleEnglish = altTitles?.optNullableString("en")
        val titleJapanese = altTitles?.optNullableString("ja")

        val updatedAt = if (!updatedAtString.isNullOrBlank()) {
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    java.time.Instant.parse(updatedAtString).epochSecond
                } else {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    (sdf.parse(updatedAtString)?.time ?: 0L) / 1000L
                }
            }.getOrDefault(0L)
        } else {
            0L
        }

        val rating = node.optNullableString("rating").orEmpty().lowercase()
        val isAdultRating = rating.contains("rx") || rating.contains("hentai")

        val genresArray = node.optJSONArray("genres")
        val hasAdultGenre = if (genresArray != null) {
            var found = false
            for (i in 0 until genresArray.length()) {
                val genreObj = genresArray.optJSONObject(i)
                if (genreObj != null) {
                    val genreName = genreObj.optNullableString("name").orEmpty().lowercase()
                    if (genreName == "hentai") {
                        found = true
                        break
                    }
                }
            }
            found
        } else false

        val isNsfwBlack = node.optNullableString("nsfw") == "black"
        val isAdult = isNsfwBlack || isAdultRating || hasAdultGenre

        return MediaEntry(
            id = 0,
            title = title,
            subtitle = if (subtitleParts.isEmpty()) {
                KitsugiStrings.get(R.string.myanimelist_ten_ice_aktarildi)
            } else {
                subtitleParts.joinToString(", ")
            },
            type = mediaType,
            status = status,
            score = score,
            progress = progress,
            total = total,
            isFavorite = isFavorite,
            isAdult = isAdult,
            source = "mal",
            malId = malId,
            imageUrl = imageUrl,
            year = year,
            synopsis = null,
            startDate = startDate,
            endDate = endDate,
            notes = notes,
            tags = tags,
            priority = priority,
            isRepeating = isRepeating,
            repeatCount = repeatCount,
            repeatValue = repeatValue,
            volumeProgress = volumeProgress,
            updatedAt = updatedAt,
            titleEnglish = titleEnglish,
            titleJapanese = titleJapanese
        )
    }

    private fun getJson(
        accessToken: String,
        urlString: String
    ): String {
        val request = Request.Builder()
            .url(urlString)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()

        try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorText = response.body?.string().orEmpty()
                    throw IllegalStateException(
                        "MAL API hatası: ${response.code} $errorText"
                    )
                }

                return response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            throw IllegalStateException("MAL bağlantı hatası: ${e.message}", e)
        }
    }

    private fun convertMalListStatus(
        malStatus: String
    ): WatchStatus {
        return when (malStatus.lowercase()) {
            "watching", "reading", "rewatching", "rereading" -> WatchStatus.Watching
            "completed" -> WatchStatus.Completed
            "dropped" -> WatchStatus.Dropped
            "on_hold", "hold", "paused" -> WatchStatus.Paused
            "plan_to_watch", "plan_to_read", "planning" -> WatchStatus.Planned
            else -> WatchStatus.Planned
        }
    }

    private fun convertMalFormat(
        malFormat: String
    ): String {
        return when (malFormat) {
            "tv" -> "TV"
            "ova" -> "OVA"
            "movie" -> "Movie"
            "special" -> "Special"
            "ona" -> "ONA"
            "music" -> "Music"
            "manga" -> "Manga"
            "novel" -> "Novel"
            "one_shot" -> "One Shot"
            "manhwa" -> "Manhwa"
            "manhua" -> "Manhua"
            else -> malFormat.replaceFirstChar { it.uppercase() }
        }
    }

    private fun String?.isValidDateString(): Boolean {
        if (this.isNullOrBlank()) return false

        val regex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        return matches(regex)
    }

    data class MalUserProfile(
        val name: String,
        val pictureUrl: String?
    )

    suspend fun fetchUserProfile(
        accessToken: String
    ): MalUserProfile {
        return withContext(Dispatchers.IO) {
            val response = getJson(accessToken, "https://api.myanimelist.net/v2/users/@me")
            val json = JSONObject(response)
            MalUserProfile(
                name = json.optNullableString("name") ?: KitsugiStrings.get(R.string.mal_kullanicisi),
                pictureUrl = json.optNullableString("picture")
            )
        }
    }

    /**
     * `users/@me` çağrısından **ham** kullanıcı adını döndürür; alan yoksa/boşsa `null`.
     *
     * [fetchUserProfile] ad bulunamadığında yerelleştirilmiş bir görüntü adına ("MAL
     * kullanıcısı") düşer; kimlik doğrulamada bu yedek ad kullanılamaz, aksi hâlde web
     * oturumuyla karşılaştırma her zaman "eşleşmiyor" üretirdi. Bu yüzden hesap eşlemesi
     * yalnızca bu katı işlevi kullanır.
     */
    suspend fun fetchUserName(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = getJson(accessToken, "https://api.myanimelist.net/v2/users/@me")
            JSONObject(response).optNullableString("name")?.trim()?.takeIf(String::isNotBlank)
        }.getOrNull()
    }
}