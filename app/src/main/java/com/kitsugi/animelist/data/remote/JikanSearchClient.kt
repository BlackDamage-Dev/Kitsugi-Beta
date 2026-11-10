package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import okhttp3.Request
import com.kitsugi.animelist.utils.*
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class JikanSearchClient {
    private val aniListSearchClient = AniListSearchClient()

    private fun jikanSfw(showAdultContent: Boolean): String = if (showAdultContent) "false" else "true"

    /**
     * Jikan liste uç noktası (top / seasons / arama). Başarısızlıkta veya boş sonuçta boş döner;
     * çağıran taraf resmi MAL API ve AniList zincirine devam eder (AniList koruması korunur).
     */
    private suspend fun jikanListOrEmpty(path: String, mediaType: MediaType): List<JikanSearchResult> {
        val body = (JikanGateway.fetch("https://api.jikan.moe/v4/$path") as? JikanResult.Ok)?.body
            ?: return emptyList()
        return runCatching { parseJikanResponse(jsonText = body, mediaType = mediaType) }.getOrDefault(emptyList())
    }

    suspend fun search(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean = false,
        status: String? = null,
        format: String? = null,
        genreId: Int? = null,
        sort: String? = null,
        orderBy: String? = null
    ): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            if (query.isBlank() && status == null && format == null && genreId == null) {
                return@withContext emptyList()
            }

            // Kaynak sırası (kullanıcı kararı): 1) resmi MAL v2  2) Jikan  3) AniList.
            // 1. Resmi MyAnimeList v2 API birincil.
            val officialResults = searchOfficialMal(
                query = query,
                mediaType = mediaType,
                showAdultContent = showAdultContent,
                limit = 24
            )
            if (officialResults.isNotEmpty()) {
                return@withContext officialResults
            }

            // 2. Resmi MAL boşsa / ulaşılamazsa Jikan (kapalıysa veya hata verirse sessizce atlanır).
            val jikanFallback = runCatching { searchJikanFallback(query, mediaType, showAdultContent) }
                .getOrDefault(emptyList())
            if (jikanFallback.isNotEmpty()) return@withContext jikanFallback

            // 3. Jikan da boşsa AniList yedeği
            runCatching {
                aniListSearchClient.requestAniList(
                    mediaType = mediaType,
                    search = query.trim().takeIf { it.isNotBlank() },
                    status = if (status == "airing" || status == "publishing") "RELEASING"
                             else if (status == "complete") "FINISHED"
                             else if (status == "upcoming") "NOT_YET_RELEASED"
                             else null,
                    sort = if (orderBy == "score") listOf("SCORE_DESC") else listOf("POPULARITY_DESC"),
                    perPage = 24,
                    format = format?.uppercase(),
                    showAdultContent = showAdultContent
                )
            }.getOrDefault(emptyList())
        }
    }

    suspend fun searchMALOnly(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean = false,
        status: String? = null,
        format: String? = null,
        genreId: Int? = null,
        sort: String? = null,
        orderBy: String? = null,
        page: Int = 1
    ): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            if (query.isBlank() && status == null && format == null && genreId == null) {
                return@withContext emptyList()
            }

            // Kaynak sırası: 1) resmi MAL v2  2) Jikan.
            val official = searchOfficialMal(
                query = query,
                mediaType = mediaType,
                showAdultContent = showAdultContent,
                limit = 24,
                page = page
            )
            if (official.isNotEmpty()) {
                return@withContext official
            }

            // 2. Resmi MAL boşsa / ulaşılamazsa Jikan.
            val jikan = runCatching { searchJikanFallback(query, mediaType, showAdultContent, page) }
                .getOrDefault(emptyList())
            if (jikan.isNotEmpty()) {
                return@withContext jikan
            }

            emptyList()
        }
    }

    /**
     * Jikan v4 arama uç noktası (`/anime|/manga?q=`) — resmî MAL API veri döndürmediğinde
     * kullanılan yedek. Dönen kayıtlar MAL kimlikleriyle işaretlenir (`source = "mal"`).
     *
     * Sıralama BİLEREK gönderilmiyor: eski `order_by=members&sort=desc` parametresi
     * Jikan'ın kendi metin alakalılığını ezip sorguyla gevşek eşleşen EN POPÜLER
     * kayıtları öne çıkarıyordu (resmî MAL anahtarı yokken "MyAnimeList" rafı alakasız
     * popüler animeyle doluyordu). Parametre yoksa Jikan/MAL arama alakalılığı kullanılır.
     */
    private suspend fun searchJikanFallback(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean,
        page: Int = 1
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val endpoint = if (mediaType == MediaType.Manga) "manga" else "anime"
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val sfw = if (showAdultContent) "false" else "true"
        val url = URL("https://api.jikan.moe/v4/$endpoint?q=$encoded&limit=24&page=$page&sfw=$sfw")
        val json = KitsugiApiBase.executeGetRequestResilient(url) ?: return@withContext emptyList()
        val root = JSONObject(json)
        val data = root.optJSONArray("data") ?: return@withContext emptyList()
        val results = mutableListOf<JikanSearchResult>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val malId = item.optInt("mal_id", 0)
            if (malId <= 0) continue
            val title = item.optString("title", "").takeIf { it.isNotBlank() } ?: continue
            val titleEnglish = item.optNullableString("title_english")
            val titleJapanese = item.optNullableString("title_japanese")
            val imgObj = item.optJSONObject("images")?.optJSONObject("jpg")
            val imageUrl = imgObj?.optNullableString("large_image_url") ?: imgObj?.optNullableString("image_url")
            val score = item.optDouble("score", Double.NaN).takeIf { !it.isNaN() }?.toInt()?.coerceIn(0, 10)
            val year = item.optInt("year", 0).takeIf { it > 1900 }
                ?: item.optJSONObject("aired")?.optJSONObject("prop")?.optJSONObject("from")?.optInt("year", 0)
                    ?.takeIf { it > 1900 }
            val total = if (mediaType == MediaType.Manga) {
                item.optInt("chapters", 0).takeIf { it > 0 }
            } else {
                item.optInt("episodes", 0).takeIf { it > 0 }
            }
            val typeStr = item.optString("type", "")
            val rating = item.optString("rating", "")
            val isAdult = rating.contains("Rx", ignoreCase = true) || rating.contains("Hentai", ignoreCase = true)
            val resolvedType = when {
                mediaType == MediaType.Manga -> MediaType.Manga
                typeStr.equals("Movie", ignoreCase = true) -> MediaType.Movie
                else -> MediaType.Anime
            }
            results.add(
                JikanSearchResult(
                    malId = malId,
                    title = title,
                    subtitle = typeStr.ifBlank { "MAL" },
                    type = resolvedType,
                    total = total,
                    score = score,
                    isAdult = isAdult,
                    imageUrl = imageUrl,
                    year = year,
                    source = "mal",
                    titleEnglish = titleEnglish,
                    titleJapanese = titleJapanese,
                    members = item.optInt("members", 0).takeIf { it > 0 }
                )
            )
        }
        results
    }

    suspend fun searchMalAdvanced(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean = false,
        status: String? = null,
        format: String? = null,
        genres: List<Int>? = null,
        excludedGenres: List<Int>? = null,
        rating: String? = null,
        minScore: Double? = null,
        maxScore: Double? = null,
        producerId: Int? = null,
        magazineId: Int? = null,
        letter: String? = null,
        sort: String? = null,
        orderBy: String? = null,
        page: Int = 1,
        season: String? = null,
        seasonYear: Int? = null,
        startYear: Int? = null,
        endYear: Int? = null,
        /**
         * Yalnızca sıralama istendiğinde (tür/yıl gibi hiçbir facet yokken) resmî MAL sıralama
         * ucu yerine Jikan liste ucunu kullanmayı zorlar. Keşfet "Tümünü Gör" sayfası başlık ve
         * çıkış tarihi sıralarını bu yolla sunucudan alır; varsayılan `false` eski davranışı
         * (puan/popülerlik sıralama ucu) korur.
         */
        applySortWithoutFacets: Boolean = false
    ): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val endpoint = if (mediaType == MediaType.Manga) "manga" else "anime"

            // The seasonal endpoint natively applies only season+year; combine it with
            // Jikan's regular search endpoint whenever any other facet/sort is requested.
            val hasAdditionalSeasonFilters = !genres.isNullOrEmpty() || !excludedGenres.isNullOrEmpty() ||
                !status.isNullOrBlank() || !format.isNullOrBlank() || !rating.isNullOrBlank() ||
                minScore != null || maxScore != null || producerId != null || magazineId != null ||
                !letter.isNullOrBlank() || startYear != null || endYear != null ||
                (!sort.isNullOrBlank() && !sort.equals("desc", ignoreCase = true)) ||
                (!orderBy.isNullOrBlank() && !orderBy.equals("popularity", ignoreCase = true))
            if (query.isBlank() && season != null && seasonYear != null && endpoint == "anime" &&
                !hasAdditionalSeasonFilters
            ) {
                val offset = (page - 1).coerceAtLeast(0) * 24
                val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_episodes,media_type,genres,nsfw,rank,popularity,num_list_users"
                val sUrl = "https://api.myanimelist.net/v2/anime/season/$seasonYear/${season.lowercase()}?limit=24&offset=$offset&fields=$fields"
                val res = getOfficialMalRankingOrSeason(sUrl, mediaType)
                if (res.isNotEmpty()) return@withContext res
                val jikanSeason = jikanListOrEmpty("seasons/$seasonYear/${season.lowercase()}?page=$page", MediaType.Anime)
                if (jikanSeason.isNotEmpty()) return@withContext jikanSeason
            }

            // 2. Arama sorgusu varsa -> Resmi MAL Arama API'si veya Jikan'ın filtreli araması
            val queryHasJikanFilters = !genres.isNullOrEmpty() || !excludedGenres.isNullOrEmpty() ||
                !status.isNullOrBlank() || !format.isNullOrBlank() || !rating.isNullOrBlank() ||
                minScore != null || maxScore != null || producerId != null || magazineId != null ||
                startYear != null || endYear != null || season != null || seasonYear != null ||
                !letter.isNullOrBlank() ||
                (!orderBy.isNullOrBlank() && !orderBy.equals("popularity", true)) ||
                (!sort.isNullOrBlank() && !sort.equals("desc", true))
            if (query.isNotBlank() && !queryHasJikanFilters) {
                // Kaynak sırası: 1) resmi MAL v2  2) Jikan.
                // Boş arama sonucu ASLA popüler sıralamaya dönüşmemeli.
                val results = searchOfficialMal(
                    query = query,
                    mediaType = mediaType,
                    showAdultContent = showAdultContent,
                    page = page,
                    limit = 24
                )
                if (results.isNotEmpty()) return@withContext results
                return@withContext runCatching {
                    searchJikanFallback(query, mediaType, showAdultContent, page)
                }.getOrDefault(emptyList())
            }

            // Jikan's `/anime` and `/manga` endpoints accept q together with the
            // facet parameters.  The old implementation always used official MAL
            // text search here, silently dropping every selected genre/date/studio.
            if (query.isNotBlank()) {
                val params = mutableListOf("limit=24", "page=${page.coerceAtLeast(1)}", "sfw=${jikanSfw(showAdultContent)}")
                fun addQueryParam(key: String, value: String?) {
                    if (!value.isNullOrBlank()) params += "$key=${URLEncoder.encode(value, "UTF-8")}"
                }
                addQueryParam("q", query.trim())
                addQueryParam("start_date", startYear?.let { "$it-01-01" } ?: season?.let {
                    if (seasonYear == null || endpoint != "anime") null else when (it.lowercase()) {
                        "winter" -> "$seasonYear-01-01"
                        "spring" -> "$seasonYear-04-01"
                        "summer" -> "$seasonYear-07-01"
                        "fall", "autumn" -> "$seasonYear-10-01"
                        else -> null
                    }
                })
                addQueryParam("end_date", endYear?.let { "$it-12-31" } ?: season?.let {
                    if (seasonYear == null || endpoint != "anime") null else when (it.lowercase()) {
                        "winter" -> "$seasonYear-03-31"
                        "spring" -> "$seasonYear-06-30"
                        "summer" -> "$seasonYear-09-30"
                        "fall", "autumn" -> "$seasonYear-12-31"
                        else -> null
                    }
                })
                addQueryParam("genres", genres?.takeIf { it.isNotEmpty() }?.joinToString(","))
                addQueryParam("genres_exclude", excludedGenres?.takeIf { it.isNotEmpty() }?.joinToString(","))
                addQueryParam("status", status)
                addQueryParam("type", format)
                addQueryParam("rating", rating)
                addQueryParam("min_score", minScore?.toString())
                addQueryParam("max_score", maxScore?.toString())
                addQueryParam("producers", producerId?.toString())
                addQueryParam("magazines", magazineId?.toString())
                addQueryParam("letter", letter)
                addQueryParam("order_by", orderBy?.takeIf { it.isNotBlank() })
                addQueryParam("sort", sort?.takeIf { it.equals("asc", true) || it.equals("desc", true) })
                return@withContext jikanListOrEmpty("$endpoint?${params.joinToString("&")}", mediaType)
            }

            // Jikan supports native genre/date filters on /anime and /manga. MAL's
            // ranking endpoint does not accept them, so never fall back to an unfiltered
            // ranking list when a detail facet is active.
            val hasJikanFilters = !genres.isNullOrEmpty() || !excludedGenres.isNullOrEmpty() ||
                !status.isNullOrBlank() || !format.isNullOrBlank() || !rating.isNullOrBlank() ||
                minScore != null || maxScore != null || producerId != null || magazineId != null ||
                startYear != null || endYear != null || season != null || seasonYear != null ||
                (applySortWithoutFacets && !orderBy.isNullOrBlank())
            if (query.isBlank() && hasJikanFilters) {
                val params = mutableListOf("limit=24", "page=${page.coerceAtLeast(1)}", "sfw=${jikanSfw(showAdultContent)}")
                fun addParam(key: String, value: String?) {
                    if (!value.isNullOrBlank()) params += "$key=${URLEncoder.encode(value, "UTF-8")}"
                }
                val seasonRange = if (season != null && seasonYear != null && endpoint == "anime") {
                    when (season.lowercase()) {
                        "winter" -> "$seasonYear-01-01" to "$seasonYear-03-31"
                        "spring" -> "$seasonYear-04-01" to "$seasonYear-06-30"
                        "summer" -> "$seasonYear-07-01" to "$seasonYear-09-30"
                        "fall", "autumn" -> "$seasonYear-10-01" to "$seasonYear-12-31"
                        else -> null
                    }
                } else null
                val effectiveStartYear = startYear?.takeIf { it > 0 }
                val effectiveEndYear = endYear?.takeIf { it > 0 }
                addParam("start_date", effectiveStartYear?.let { "$it-01-01" } ?: seasonRange?.first)
                addParam("end_date", effectiveEndYear?.let { "$it-12-31" } ?: seasonRange?.second)
                addParam("genres", genres?.takeIf { it.isNotEmpty() }?.joinToString(","))
                addParam("genres_exclude", excludedGenres?.takeIf { it.isNotEmpty() }?.joinToString(","))
                addParam("status", status)
                addParam("type", format)
                addParam("rating", rating)
                addParam("min_score", minScore?.toString())
                addParam("max_score", maxScore?.toString())
                addParam("producers", producerId?.toString())
                addParam("magazines", magazineId?.toString())
                val sortField = orderBy?.takeIf { it.isNotBlank() }
                addParam("order_by", sortField)
                addParam("sort", sort?.takeIf { it.equals("asc", true) || it.equals("desc", true) })
                val filtered = jikanListOrEmpty("$endpoint?${params.joinToString("&")}", mediaType)
                return@withContext filtered
            }

            // 3. Yalnızca boş sorguda -> Resmi MAL Sıralama API'si
            val rankingType = when {
                status == "airing" -> "airing"
                status == "upcoming" -> "upcoming"
                orderBy == "score" -> "all"
                orderBy == "members" || orderBy == "popularity" -> "bypopularity"
                orderBy == "favorite" -> "favorite"
                mediaType == MediaType.Manga -> "all"
                else -> "bypopularity"
            }
            val offset = (page - 1).coerceAtLeast(0) * 24
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,${if (mediaType == MediaType.Manga) "num_chapters" else "num_episodes"},media_type,genres,nsfw,rank,popularity,num_list_users"
            val jikanRankingFilter = when {
                rankingType == "airing" && endpoint == "anime" -> "airing"
                rankingType == "airing" -> "publishing"
                rankingType == "upcoming" -> "upcoming"
                rankingType == "bypopularity" -> "bypopularity"
                rankingType == "favorite" -> "favorite"
                else -> null
            }
            val rankingUrl = "https://api.myanimelist.net/v2/$endpoint/ranking?ranking_type=$rankingType&limit=24&offset=$offset&fields=$fields"
            val rankingResults = getOfficialMalRankingOrSeason(rankingUrl, mediaType)
            if (rankingResults.isNotEmpty()) {
                return@withContext rankingResults
            }
            val jikanRanking = jikanListOrEmpty(
                "top/$endpoint?" + listOfNotNull(jikanRankingFilter?.let { "filter=$it" }, "page=$page").joinToString("&"),
                mediaType
            )
            if (jikanRanking.isNotEmpty()) return@withContext jikanRanking

            emptyList()
        }
    }

    suspend fun searchMalCharacters(
        query: String,
        page: Int = 1,
        orderBy: String = "favorites",
        sort: String = "desc",
        letter: String? = null
    ): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(4000L) {
                try {
                    val qParams = mutableListOf("limit=24", "page=$page", "order_by=$orderBy", "sort=$sort")
                    if (query.isNotBlank()) qParams.add("q=${URLEncoder.encode(query.trim(), "UTF-8")}")
                    if (!letter.isNullOrBlank()) qParams.add("letter=$letter")
                    val url = URL("https://api.jikan.moe/v4/characters?${qParams.joinToString("&")}")
                    val json = KitsugiApiBase.executeGetRequest(url) ?: return@withTimeoutOrNull emptyList()
                    val root = JSONObject(json)
                    val data = root.optJSONArray("data") ?: return@withTimeoutOrNull emptyList()
                    val list = mutableListOf<JikanSearchResult>()
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val id = item.getInt("mal_id")
                        val name = item.optString("name", "")
                        val favs = item.optInt("favorites", 0)
                        val imgObj = item.optJSONObject("images")?.optJSONObject("jpg")
                        val imgUrl = imgObj?.optNullableString("image_url")
                        list.add(
                            JikanSearchResult(
                                malId = id,
                                title = name,
                                subtitle = "Karakter (MAL)",
                                type = MediaType.Anime,
                                total = null,
                                score = null,
                                isAdult = false,
                                imageUrl = imgUrl,
                                year = null,
                                source = "mal",
                                favorites = favs
                            )
                        )
                    }
                    list
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }
    }

    suspend fun searchMalPeople(
        query: String,
        page: Int = 1,
        orderBy: String = "favorites",
        sort: String = "desc",
        letter: String? = null
    ): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(4000L) {
                try {
                    val qParams = mutableListOf("limit=24", "page=$page", "order_by=$orderBy", "sort=$sort")
                    if (query.isNotBlank()) qParams.add("q=${URLEncoder.encode(query.trim(), "UTF-8")}")
                    if (!letter.isNullOrBlank()) qParams.add("letter=$letter")
                    val url = URL("https://api.jikan.moe/v4/people?${qParams.joinToString("&")}")
                    val json = KitsugiApiBase.executeGetRequest(url) ?: return@withTimeoutOrNull emptyList()
                    val root = JSONObject(json)
                    val data = root.optJSONArray("data") ?: return@withTimeoutOrNull emptyList()
                    val list = mutableListOf<JikanSearchResult>()
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val id = item.getInt("mal_id")
                        val name = item.optString("name", "")
                        val favs = item.optInt("favorites", 0)
                        val birthday = item.optNullableString("birthday")
                        val imgObj = item.optJSONObject("images")?.optJSONObject("jpg")
                        val imgUrl = imgObj?.optNullableString("image_url")
                        list.add(
                            JikanSearchResult(
                                malId = id,
                                title = name,
                                subtitle = if (!birthday.isNullOrBlank()) KitsugiStrings.get(R.string.dogum_x, birthday.take(10)) else KitsugiStrings.get(R.string.kisi_seiyuu_mal),
                                type = MediaType.Anime,
                                total = null,
                                score = null,
                                isAdult = false,
                                imageUrl = imgUrl,
                                year = null,
                                source = "mal",
                                favorites = favs
                            )
                        )
                    }
                    list
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }
    }

    suspend fun searchMalProducers(
        query: String,
        page: Int = 1,
        orderBy: String = "favorites",
        sort: String = "desc",
        letter: String? = null
    ): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(4000L) {
                try {
                    val qParams = mutableListOf("limit=24", "page=$page", "order_by=$orderBy", "sort=$sort")
                    if (query.isNotBlank()) qParams.add("q=${URLEncoder.encode(query.trim(), "UTF-8")}")
                    if (!letter.isNullOrBlank()) qParams.add("letter=$letter")
                    val url = URL("https://api.jikan.moe/v4/producers?${qParams.joinToString("&")}")
                    val json = KitsugiApiBase.executeGetRequest(url) ?: return@withTimeoutOrNull emptyList()
                    val root = JSONObject(json)
                    val data = root.optJSONArray("data") ?: return@withTimeoutOrNull emptyList()
                    val list = mutableListOf<JikanSearchResult>()
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val id = item.getInt("mal_id")
                        val titles = item.optJSONArray("titles")
                        val name = if (titles != null && titles.length() > 0) {
                            titles.getJSONObject(0).optString("title", item.optString("name", KitsugiStrings.get(R.string.detail_info_studio)))
                        } else item.optString("name", "Stüdyo")
                        val favs = item.optInt("favorites", 0)
                        val count = item.optInt("count", 0)
                        val imageUrl = item.optJSONObject("images")?.let { images ->
                            images.optJSONObject("jpg")?.optNullableString("image_url")
                                ?: images.optJSONObject("webp")?.optNullableString("image_url")
                        }
                        list.add(
                            JikanSearchResult(
                                malId = id,
                                title = name,
                                subtitle = KitsugiStrings.get(R.string.studyo_count_yapim, count),
                                type = MediaType.Anime,
                                total = count,
                                score = null,
                                isAdult = false,
                                imageUrl = imageUrl,
                                year = null,
                                source = "mal",
                                favorites = favs
                            )
                        )
                    }
                    list
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }
    }

    private fun getOfficialMalRankingOrSeason(
        urlStr: String,
        mediaType: MediaType
    ): List<JikanSearchResult> {
        // T3-01: BuildConfig'den alınır
        val clientId = com.kitsugi.animelist.BuildConfig.MAL_CLIENT_ID
        val request = Request.Builder()
            .url(urlStr)
            .header("X-MAL-CLIENT-ID", clientId)
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("JikanSearchClient", "Official MAL API request failed: ${response.code} for URL: $urlStr")
                    emptyList()
                } else {
                    val responseText = response.body?.string().orEmpty()
                    parseOfficialMalResponse(responseText, mediaType)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JikanSearchClient", "Official MAL API request error: ${e.message} for URL: $urlStr")
            emptyList()
        }
    }

    suspend fun topAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_episodes,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/anime/ranking?ranking_type=all&limit=20&offset=$offset&fields=$fields"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Anime)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("top/anime?page=$page", MediaType.Anime)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching { aniListSearchClient.aniListTopAnime(page, showAdultContent) }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun airingAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_episodes,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/anime/ranking?ranking_type=airing&limit=20&offset=$offset&fields=$fields"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Anime)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("top/anime?filter=airing&page=$page", MediaType.Anime)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching { aniListSearchClient.aniListAiringAnime(page, showAdultContent) }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun upcomingAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_episodes,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/anime/ranking?ranking_type=upcoming&limit=20&offset=$offset&fields=$fields"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Anime)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("top/anime?filter=upcoming&page=$page", MediaType.Anime)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching { aniListSearchClient.aniListUpcomingAnime(page, showAdultContent) }.getOrDefault(emptyList())
            }
        }
    }

    /**
     * MAL'in yerel "ek tür" rafı: Manhwa & Manhua (Jikan `top/manga?type=` filtresi).
     * Bangumi REAL raflarıyla aynı fikir — kaynağın kendi yerel kategorisi.
     */
    suspend fun manhwaManhua(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> =
        mergedMangaTypeShelf(listOf("manhwa", "manhua"), page, showAdultContent)

    /** MAL'in yerel "ek tür" rafı: Novel & Light Novel. */
    suspend fun novelsShelf(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> =
        mergedMangaTypeShelf(listOf("novel", "lightnovel"), page, showAdultContent)

    private suspend fun mergedMangaTypeShelf(
        types: List<String>,
        page: Int,
        showAdultContent: Boolean
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val merged = types.flatMap { type ->
            jikanListOrEmpty("top/manga?page=$safePage&type=$type", MediaType.Manga)
        }
        merged.distinctBy { it.malId }
            .sortedByDescending { it.score ?: 0 }
            .filter { showAdultContent || !it.isAdult }
    }

    suspend fun topManga(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_chapters,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/manga/ranking?ranking_type=all&limit=20&offset=$offset&fields=$fields"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Manga)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("top/manga?page=$page", MediaType.Manga)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching { aniListSearchClient.aniListTopManga(page, showAdultContent) }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun publishingManga(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_chapters,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/manga/ranking?ranking_type=manga&limit=20&offset=$offset&fields=$fields"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Manga)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("top/manga?filter=publishing&page=$page", MediaType.Manga)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching { aniListSearchClient.aniListPublishingManga(page, showAdultContent) }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun completedManga(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_chapters,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/manga/ranking?ranking_type=manga&limit=20&offset=$offset&fields=$fields"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Manga)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("manga?status=complete&order_by=members&sort=desc&sfw=${jikanSfw(showAdultContent)}&page=$page", MediaType.Manga)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                emptyList()
            }
        }
    }

    suspend fun trendingAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_episodes,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/anime/ranking?ranking_type=bypopularity&limit=20&offset=$offset&fields=$fields"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Anime)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("top/anime?filter=bypopularity&page=$page", MediaType.Anime)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching { aniListSearchClient.aniListTrendingAnime(page, showAdultContent) }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun movieAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_episodes,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/anime/ranking?ranking_type=movie&limit=20&offset=$offset&fields=$fields"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Anime)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("top/anime?type=movie&page=$page", MediaType.Anime)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching { aniListSearchClient.aniListMovieAnime(page, showAdultContent) }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun trendingManga(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_chapters,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/manga/ranking?ranking_type=bypopularity&limit=20&offset=$offset&fields=$fields"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Manga)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("top/manga?filter=bypopularity&page=$page", MediaType.Manga)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching { aniListSearchClient.aniListTrendingManga(page, showAdultContent) }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun newlyAddedAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1).coerceAtLeast(0) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_episodes,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/anime/ranking?ranking_type=all&limit=20&offset=$offset&fields=$fields"
            val results = getOfficialMalRankingOrSeason(url, MediaType.Anime)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("seasons/now?page=$page", MediaType.Anime)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching {
                    aniListSearchClient.aniListNewlyAddedAnime(page, showAdultContent)
                }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun newlyAddedManga(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val offset = (page - 1).coerceAtLeast(0) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_chapters,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/manga/ranking?ranking_type=all&limit=20&offset=$offset&fields=$fields"
            val results = getOfficialMalRankingOrSeason(url, MediaType.Manga)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("manga?status=publishing&order_by=start_date&sort=desc&sfw=${jikanSfw(showAdultContent)}&page=$page", MediaType.Manga)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                runCatching {
                    aniListSearchClient.aniListNewlyAddedManga(page, showAdultContent)
                }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun seasonalAnime(
        page: Int = 1,
        showAdultContent: Boolean = false,
        year: Int? = null,
        season: String? = null,
        sort: String? = null
    ): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val calendar = java.util.Calendar.getInstance()
            val targetYear = year ?: calendar.get(java.util.Calendar.YEAR)
            val targetSeason = season?.lowercase() ?: run {
                val month = calendar.get(java.util.Calendar.MONTH)
                when (month) {
                    java.util.Calendar.DECEMBER, java.util.Calendar.JANUARY, java.util.Calendar.FEBRUARY -> "winter"
                    java.util.Calendar.MARCH, java.util.Calendar.APRIL, java.util.Calendar.MAY -> "spring"
                    java.util.Calendar.JUNE, java.util.Calendar.JULY, java.util.Calendar.AUGUST -> "summer"
                    else -> "fall"
                }
            }

            val malSort = when (sort) {
                "SCORE_DESC", "score" -> "anime_score"
                "START_DATE_DESC", "start_date" -> "anime_start_date"
                "END_DATE_DESC" -> "anime_start_date"
                else -> "anime_num_list_users"
            }


            val offset = (page - 1) * 20
            val fields = "id,title,main_picture,alternative_titles,start_date,mean,num_episodes,media_type,genres,nsfw"
            val url = "https://api.myanimelist.net/v2/anime/season/$targetYear/$targetSeason?limit=20&offset=$offset&fields=$fields&sort=$malSort"

            val results = getOfficialMalRankingOrSeason(url, MediaType.Anime)
            if (results.isNotEmpty()) {
                results
            } else {
                val jikanFallback = jikanListOrEmpty("seasons/$targetYear/$targetSeason?page=$page", MediaType.Anime)
                if (jikanFallback.isNotEmpty()) return@withContext jikanFallback
                val aniListSort = when (sort) {
                    "SCORE_DESC", "score" -> listOf("SCORE_DESC")
                    "START_DATE_DESC", "start_date" -> listOf("START_DATE_DESC")
                    "END_DATE_DESC" -> listOf("END_DATE_DESC")
                    else -> listOf("POPULARITY_DESC")
                }
                runCatching {
                    aniListSearchClient.aniListSeasonalAnime(
                        page = page,
                        showAdultContent = showAdultContent,
                        year = targetYear,
                        season = targetSeason,
                        sort = aniListSort
                    )
                }.getOrDefault(emptyList())
            }
        }
    }

    private suspend fun requestAndParseWithFallback(
        url: URL,
        mediaType: MediaType,
        fallback: suspend () -> List<JikanSearchResult>
    ): List<JikanSearchResult> {
        var lastError: Throwable? = null
        var attempt = 0

        while (true) {
            try {
                return KitsugiApiBase.runWithRateLimit {
                    requestAndParseJikan(
                        url = url,
                        mediaType = mediaType
                    )
                }
            } catch (error: Throwable) {
                lastError = error
                attempt += 1

                val category = KitsugiApiBase.classifyNetworkError(error)

                val shouldRetry = when (category) {
                    KitsugiApiBase.NetworkErrorCategory.RateLimited -> attempt <= 2
                    KitsugiApiBase.NetworkErrorCategory.GatewayProblem -> attempt <= 1
                    KitsugiApiBase.NetworkErrorCategory.Other -> attempt <= 1
                }

                if (!shouldRetry) {
                    break
                }

                val delayMs = when (category) {
                    KitsugiApiBase.NetworkErrorCategory.RateLimited -> 1_400L * attempt
                    KitsugiApiBase.NetworkErrorCategory.GatewayProblem -> 350L
                    KitsugiApiBase.NetworkErrorCategory.Other -> 450L
                }

                delay(delayMs)
            }
        }

        return runCatching {
            fallback()
        }.getOrElse { fallbackError ->
            val fallbackMsg = fallbackError.message.orEmpty()
            val jikanMsg = lastError?.message.orEmpty()
            val displayMsg = if (fallbackMsg.startsWith("Jikan API (MAL)") || fallbackMsg.contains("API hatası")) {
                jikanMsg.ifBlank { fallbackMsg }
            } else {
                "$jikanMsg | Alternatif Arama (AniList): $fallbackMsg"
            }
            throw IllegalStateException(displayMsg)
        }
    }

    private fun requestAndParseJikan(
        url: URL,
        mediaType: MediaType
    ): List<JikanSearchResult> {
        val body = when (val r = JikanGateway.fetchBlocking(url.toString(), maxRetries = 0)) {
            is JikanResult.Ok -> r.body
            is JikanResult.NotFound -> return emptyList()
            is JikanResult.RateLimited -> throw IllegalStateException(
                "Çok fazla istek yapıldı (Rate Limit). Lütfen birkaç saniye sonra tekrar dene."
            )
            is JikanResult.Failed -> throw IllegalStateException(
                when (r.code) {
                    502, 503, 504 -> "MyAnimeList (MAL) sunucuları şu anda yanıt vermiyor veya bakımda. Lütfen daha sonra tekrar dene."
                    else -> "Sunucu hatası oluştu (Kod: ${r.code}). Lütfen daha sonra tekrar dene."
                }
            )
        }
        return parseJikanResponse(jsonText = body, mediaType = mediaType)
    }

    private fun parseJikanResponse(
        jsonText: String,
        mediaType: MediaType
    ): List<JikanSearchResult> {
        val root = JSONObject(jsonText)
        val dataArray = root.optJSONArray("data") ?: return emptyList()

        val results = mutableListOf<JikanSearchResult>()

        for (index in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(index) ?: continue

            val malId = item.optInt("mal_id", 0)
            val titleEnglish = item.optNullableString("title_english")
            val titleJapanese = item.optNullableString("title_japanese")
            val title = item.optNullableString("title")
                ?: titleEnglish
                ?: "Başlıksız"

            val itemType = item.optNullableString("type").orEmpty()
            val year = extractYearFromJikan(item, mediaType)

            val scoreDouble = item.optDouble("score", Double.NaN)
            val score = if (scoreDouble.isNaN()) {
                null
            } else {
                scoreDouble.toInt().coerceIn(0, 10)
            }
            val rawScore = if (scoreDouble.isNaN()) null else scoreDouble
            val rankVal = item.optionalPositiveInt("rank")
            val membersVal = item.optionalPositiveInt("members")
            val favoritesVal = item.optionalPositiveInt("favorites")

            val total = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> item.optionalPositiveInt("episodes")
                MediaType.Manga -> item.optionalPositiveInt("chapters")
            }

            val genres = item.namesFromObjectArray("genres")
            val themes = item.namesFromObjectArray("themes")
            val demographics = item.namesFromObjectArray("demographics")

            val subtitleParts = buildList {
                if (itemType.isNotBlank()) add(itemType.toTurkishMediaTypeString())
                if (year != null && year > 0) add(year.toString())
                addAll(genres.take(3).toTurkishGenres())
            }

            val subtitle = if (subtitleParts.isEmpty()) {
                when (mediaType) {
                    MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "API ile eklenen anime"
                    MediaType.Manga -> "API ile eklenen manga"
                }
            } else {
                subtitleParts.joinToString(", ")
            }

            val isAdult = isAdultJikanItem(
                item = item,
                genres = genres,
                themes = themes,
                demographics = demographics
            )

            val imageUrl = extractJikanImageUrl(item)

            if (malId > 0 && title.isNotBlank()) {
                results.add(
                    JikanSearchResult(
                        malId = malId,
                        title = title,
                        subtitle = subtitle,
                        type = mediaType,
                        total = total,
                        score = score,
                        isAdult = isAdult,
                        imageUrl = imageUrl,
                        year = year,
                        source = "jikan",
                        titleEnglish = titleEnglish,
                        titleJapanese = titleJapanese,
                        rank = rankVal,
                        members = membersVal,
                        favorites = favoritesVal,
                        rawScoreDouble = rawScore,
                        genres = genres + themes
                    )
                )
            }
        }

        return results
    }

    private fun extractJikanImageUrl(item: JSONObject): String? {
        val images = item.optJSONObject("images") ?: return null
        val webp = images.optJSONObject("webp")
        val jpg = images.optJSONObject("jpg")

        return webp?.optNullableString("large_image_url")
            ?: webp?.optNullableString("image_url")
            ?: jpg?.optNullableString("large_image_url")
            ?: jpg?.optNullableString("image_url")
    }

    private fun extractYearFromJikan(
        item: JSONObject,
        mediaType: MediaType
    ): Int? {
        val directYear = item.optionalPositiveInt("year")
        if (directYear != null) return directYear

        val dateContainerKey = when (mediaType) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "aired"
            MediaType.Manga -> "published"
        }

        val fromDate = item
            .optJSONObject(dateContainerKey)
            ?.optString("from")
            .orEmpty()

        return fromDate
            .takeIf { it.length >= 4 }
            ?.take(4)
            ?.toIntOrNull()
    }

    private fun isAdultJikanItem(
        item: JSONObject,
        genres: List<String>,
        themes: List<String>,
        demographics: List<String>
    ): Boolean {
        val rating = item.optString("rating").lowercase()
        val allTags = genres + themes + demographics

        return rating.contains("hentai") ||
                rating.contains("rx") ||
                allTags.any { tag ->
                    tag.lowercase().contains("hentai")
                }
    }

    fun searchOfficialMal(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean = false,
        page: Int = 1,
        limit: Int = 24
    ): List<JikanSearchResult> {
        val clientId = com.kitsugi.animelist.BuildConfig.MAL_CLIENT_ID
        val offset = (page - 1).coerceAtLeast(0) * limit
        val endpoint = if (mediaType == MediaType.Manga) "manga" else "anime"
        val fields = "id,title,main_picture,alternative_titles,start_date,mean,${if (mediaType == MediaType.Manga) "num_chapters" else "num_episodes"},media_type,genres,nsfw,rank,popularity,num_list_users"
        val url = if (query.isNotBlank()) {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            "https://api.myanimelist.net/v2/$endpoint?q=$encodedQuery&limit=$limit&offset=$offset&fields=$fields"
        } else {
            "https://api.myanimelist.net/v2/$endpoint/ranking?ranking_type=all&limit=$limit&offset=$offset&fields=$fields"
        }

        val request = Request.Builder()
            .url(url)
            .header("X-MAL-CLIENT-ID", clientId)
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("JikanSearchClient", "Official MAL API search failed with HTTP code: ${response.code}")
                    return emptyList()
                }
                val responseText = response.body?.string().orEmpty()
                parseOfficialMalResponse(responseText, mediaType)
            }
        } catch (e: Exception) {
            android.util.Log.e("JikanSearchClient", "Official MAL API search error: ${e.message}")
            emptyList()
        }
    }

    private fun parseOfficialMalResponse(
        jsonText: String,
        mediaType: MediaType
    ): List<JikanSearchResult> {
        val root = JSONObject(jsonText)
        val dataArray = root.optJSONArray("data") ?: return emptyList()
        val results = mutableListOf<JikanSearchResult>()

        for (i in 0 until dataArray.length()) {
            val itemObj = dataArray.optJSONObject(i) ?: continue
            val node = itemObj.optJSONObject("node") ?: continue

            val malId = node.optInt("id", 0)
            val title = node.optString("title", KitsugiStrings.get(R.string.basliksiz))

            val altTitles = node.optJSONObject("alternative_titles")
            val titleEnglish = altTitles?.optNullableString("en")
            val titleJapanese = altTitles?.optNullableString("ja")

            val mainPic = node.optJSONObject("main_picture")
            val imageUrl = mainPic?.optNullableString("large") ?: mainPic?.optNullableString("medium")

            val startDate = node.optString("start_date", "")
            val year = startDate.take(4).toIntOrNull()

            val scoreDouble = node.optDouble("mean", Double.NaN)
            val score = if (scoreDouble.isNaN()) null else scoreDouble.toInt().coerceIn(0, 10)
            val rawScore = if (scoreDouble.isNaN()) null else scoreDouble
            val rankVal = node.optionalPositiveInt("rank")
            val membersVal = node.optionalPositiveInt("num_list_users")

            val total = if (mediaType == MediaType.Manga) {
                node.optionalPositiveInt("num_chapters")
            } else {
                node.optionalPositiveInt("num_episodes")
            }

            val rawType = node.optString("media_type", "")
            val nsfw = node.optString("nsfw", "white")

            val genresList = mutableListOf<String>()
            val genresArray = node.optJSONArray("genres")
            if (genresArray != null) {
                for (j in 0 until genresArray.length()) {
                    val genreObj = genresArray.optJSONObject(j) ?: continue
                    val genreName = genreObj.optString("name", "")
                    if (genreName.isNotBlank()) genresList.add(genreName)
                }
            }

            val isAdult = nsfw.equals("black", ignoreCase = true) ||
                genresList.any { it.contains("hentai", ignoreCase = true) }

            val subtitleParts = buildList {
                if (rawType.isNotBlank()) add(rawType.uppercase().toTurkishMediaTypeString())
                if (year != null && year > 0) add(year.toString())
                addAll(genresList.take(3).toTurkishGenres())
            }

            val subtitle = if (subtitleParts.isEmpty()) {
                if (mediaType == MediaType.Manga) "Manga" else "Anime"
            } else {
                subtitleParts.joinToString(", ")
            }

            if (malId > 0 && title.isNotBlank()) {
                results.add(
                    JikanSearchResult(
                        malId = malId,
                        title = title,
                        subtitle = subtitle,
                        type = mediaType,
                        total = total,
                        score = score,
                        isAdult = isAdult,
                        imageUrl = imageUrl,
                        year = year,
                        source = "mal",
                        titleEnglish = titleEnglish,
                        titleJapanese = titleJapanese,
                        genres = genresList,
                        rank = rankVal,
                        members = membersVal,
                        rawScoreDouble = rawScore
                    )
                )
            }
        }
        return results
    }
}
