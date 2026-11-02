package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.core.memory.BoundedCache
import org.json.JSONObject
import okhttp3.Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.*
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

internal data class SimklCrossIds(
    val simklId: Int,
    val malId: Int? = null,
    val tmdbId: Int? = null,
    val aniListId: Int? = null,
    val kitsuId: Int? = null,
    val imdbId: String? = null,
    val isMovie: Boolean = false,
    val title: String? = null
)

/**
 * Simkl API'sinden medya detaylarını ve çapraz ID eşleşmelerini çeker.
 */
internal object KitsugiSimklDetailClient {

    // extended=full objects may include relations/recommendations; keep the working set bounded
    // and let KitsugiMemoryGuard shrink it under Android memory pressure.
    private val rawJsonCache = BoundedCache<String, JSONObject>("simkl.rawJson", 250)
    private val crossIdCache = BoundedCache<String, SimklCrossIds>("simkl.crossIds", 1_000)
    private val externalSimklIdCache = BoundedCache<String, Int>("simkl.extId", 1_000)

    private fun parseOptionalId(ids: JSONObject?, key: String): Int? {
        if (ids == null) return null
        val fromStr = ids.optString(key, "").trim().toIntOrNull()
        if (fromStr != null && fromStr > 0) return fromStr
        val fromInt = ids.optInt(key, 0)
        return fromInt.takeIf { it > 0 }
    }

    suspend fun fetchSimklRawJson(
        simklId: Int,
        mediaType: MediaType
    ): JSONObject? {
        if (simklId <= 0) return null
        val typePath = when (mediaType) {
            MediaType.Movie -> "movies"
            MediaType.TvShow -> "tv"
            MediaType.Anime -> "anime"
            else -> "anime"
        }
        val cacheKey = "$typePath:$simklId"
        rawJsonCache[cacheKey]?.let { return it }

        val clientId = com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID
        val urlString = "https://api.simkl.com/$typePath/$simklId?client_id=$clientId&extended=full"
        val request = Request.Builder()
            .url(urlString)
            .header("Accept", "application/json")
            .build()

        // This suspend function is often called from Compose/ViewModel Main context. OkHttp's
        // execute() is blocking, so always move both socket I/O and response parsing off Main.
        return withContext(Dispatchers.IO) {
            try {
                com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val text = response.body?.string()?.takeIf { it.isNotBlank() && it.trim().startsWith("{") }
                        ?: return@withContext null
                    val obj = JSONObject(text)
                    if (obj.length() == 0) return@withContext null
                    rawJsonCache[cacheKey] = obj
                    obj
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("KitsugiSimklDetailClient", "fetchSimklRawJson simklId=$simklId failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Simkl öğesi için MAL, TMDB, AniList ve Kitsu çapraz ID'lerini çözümler.
     * Önce DetailCache, ardından Simkl extended=full API'si ve son olarak KitsugiIdResolver (ARM / AniList) kullanılır.
     */
    suspend fun resolveSimklCrossIds(
        simklId: Int,
        mediaType: MediaType,
        hintTmdbId: Int? = null,
        hintMalId: Int? = null
    ): SimklCrossIds {
        val typeKey = "${mediaType.name}:$simklId"
        val cached = crossIdCache[typeKey]
        if (cached != null &&
            (hintTmdbId == null || cached.tmdbId == hintTmdbId) &&
            (hintMalId == null || cached.malId == hintMalId) &&
            (cached.tmdbId != null || cached.malId != null || cached.aniListId != null)
        ) {
            return cached
        }

        val detail = DetailCache.getMediaDetail("simkl", simklId, mediaType.name)
            ?: DetailCache.getMediaDetail("simkl", simklId)
        var malId = hintMalId?.takeIf { it > 0 && it != simklId }
            ?: detail?.realMalId?.takeIf { it > 0 && it != simklId }
        var tmdbId = hintTmdbId?.takeIf { it > 0 }
            ?: detail?.tmdbId?.takeIf { it > 0 }
        var aniListId: Int? = null
        var kitsuId: Int? = null
        var imdbId: String? = null
        var isMovie = mediaType == MediaType.Movie
        var title: String? = detail?.titleEnglish ?: detail?.title

        // Eksik ID varsa Simkl extended=full JSON'dan çek
        if (malId == null || tmdbId == null || aniListId == null) {
            val obj = fetchSimklRawJson(simklId, mediaType)
            if (obj != null) {
                val ids = obj.optJSONObject("ids")
                if (tmdbId == null) tmdbId = parseOptionalId(ids, "tmdb")
                if (malId == null) malId = parseOptionalId(ids, "mal")
                aniListId = parseOptionalId(ids, "anilist")
                kitsuId = parseOptionalId(ids, "kitsu")
                imdbId = ids?.optString("imdb")?.takeIf { it.isNotBlank() && it != "null" }
                if (obj.optString("anime_type", "").equals("movie", ignoreCase = true) ||
                    obj.optString("type", "").equals("movie", ignoreCase = true)
                ) {
                    isMovie = true
                }
                if (title.isNullOrBlank()) {
                    title = MediaTitleResolver.nonBlank(
                        obj.optString("en_title"),
                        obj.optString("title")
                    )
                }
            }
        }

        // Anime veya çapraz ID'si hala eksik olan içerikler için KitsugiIdResolver (ARM + AniList) çalıştır
        if ((malId != null || aniListId != null || tmdbId != null) &&
            (malId == null || aniListId == null || tmdbId == null)
        ) {
            val resolved = runCatching {
                KitsugiIdResolver.resolveIds(
                    malId = malId,
                    aniListId = aniListId,
                    tmdbId = tmdbId,
                    mediaType = mediaType
                )
            }.getOrNull()
            if (resolved != null) {
                if (malId == null) malId = resolved.malId?.takeIf { it > 0 }
                if (aniListId == null) aniListId = resolved.aniListId?.takeIf { it > 0 }
                if (tmdbId == null) tmdbId = resolved.tmdbId?.takeIf { it > 0 }
                if (kitsuId == null) kitsuId = resolved.kitsuId?.takeIf { it > 0 }
                if (imdbId == null) imdbId = resolved.imdbId
            }
        }

        val result = SimklCrossIds(
            simklId = simklId,
            malId = malId,
            tmdbId = tmdbId,
            aniListId = aniListId,
            kitsuId = kitsuId,
            imdbId = imdbId,
            isMovie = isMovie,
            title = title
        )
        crossIdCache[typeKey] = result
        return result
    }

    /**
     * Simkl `extended=full` yanıtındaki `relations` dizisini [KitsugiRelation] listesine dönüştürür.
     */
    suspend fun fetchSimklNativeRelations(
        simklId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val obj = fetchSimklRawJson(simklId, mediaType) ?: return emptyList()
        val relArray = obj.optJSONArray("relations") ?: return emptyList()
        val list = mutableListOf<KitsugiRelation>()
        for (i in 0 until relArray.length()) {
            val item = relArray.optJSONObject(i) ?: continue
            val ids = item.optJSONObject("ids")
            val relSimklId = parseOptionalId(ids, "simkl")
                ?: item.optInt("simkl_id", 0).takeIf { it > 0 }
                ?: continue
            val relTitle = MediaTitleResolver.nonBlank(
                item.optString("en_title"),
                item.optString("title")
            ) ?: continue
            val poster = item.optString("poster", "").takeIf { it.isNotBlank() && it != "null" }
            val imgUrl = poster?.let { "https://simkl.in/posters/${it}_m.jpg" }
            val rawRelType = item.optString("relation_type", "").ifBlank { "Related" }
            val animeType = item.optString("anime_type", "").lowercase()
            val relMediaType = when {
                animeType == "movie" -> MediaType.Movie
                mediaType == MediaType.Movie -> MediaType.Movie
                mediaType == MediaType.TvShow -> MediaType.TvShow
                else -> MediaType.Anime
            }
            list.add(
                KitsugiRelation(
                    malId = relSimklId,
                    title = relTitle,
                    relationType = rawRelType.replaceFirstChar { it.uppercase() }.toTurkishRelationType(),
                    mediaType = relMediaType,
                    imageUrl = imgUrl,
                    source = "simkl",
                    titleEnglish = relTitle
                )
            )
        }
        return list
    }

    /**
     * Simkl `extended=full` yanıtındaki `users_recommendations` dizisini [KitsugiRelation] listesine dönüştürür.
     */
    suspend fun fetchSimklNativeRecommendations(
        simklId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val obj = fetchSimklRawJson(simklId, mediaType) ?: return emptyList()
        val recArray = obj.optJSONArray("users_recommendations") ?: return emptyList()
        val list = mutableListOf<KitsugiRelation>()
        for (i in 0 until recArray.length()) {
            val item = recArray.optJSONObject(i) ?: continue
            val ids = item.optJSONObject("ids")
            val recSimklId = parseOptionalId(ids, "simkl")
                ?: item.optInt("simkl_id", 0).takeIf { it > 0 }
                ?: continue
            val recTitle = MediaTitleResolver.nonBlank(
                item.optString("en_title"),
                item.optString("title")
            ) ?: continue
            val poster = item.optString("poster", "").takeIf { it.isNotBlank() && it != "null" }
            val imgUrl = poster?.let { "https://simkl.in/posters/${it}_m.jpg" }
            val year = item.optInt("year", 0).takeIf { it > 0 }
            val animeType = item.optString("anime_type", "").uppercase().ifBlank { null }
            list.add(
                KitsugiRelation(
                    malId = recSimklId,
                    title = recTitle,
                    relationType = animeType ?: year?.toString() ?: KitsugiStrings.get(R.string.oneri),
                    mediaType = mediaType,
                    imageUrl = imgUrl,
                    source = "simkl",
                    titleEnglish = recTitle
                )
            )
        }
        return list
    }

    /**
     * Jikan/AniList/TMDB istatistikleri alınamadığında Simkl `ratings` verisinden
     * temel istatistik ve sıralama modeli üretir.
     */
    suspend fun fetchSimklFallbackStats(
        simklId: Int,
        mediaType: MediaType
    ): KitsugiStats? {
        val obj = fetchSimklRawJson(simklId, mediaType) ?: return null
        val ratings = obj.optJSONObject("ratings") ?: return null
        val simklRating = ratings.optJSONObject("simkl")
        val malRating = ratings.optJSONObject("mal")
        val imdbRating = ratings.optJSONObject("imdb")

        val primaryRating = simklRating?.optDouble("rating", 0.0)?.takeIf { it > 0.0 }
            ?: malRating?.optDouble("rating", 0.0)?.takeIf { it > 0.0 }
            ?: imdbRating?.optDouble("rating", 0.0)?.takeIf { it > 0.0 }
            ?: return null

        val primaryVotes = (simklRating?.optInt("votes", 0) ?: 0) +
            (malRating?.optInt("votes", 0) ?: 0) +
            (imdbRating?.optInt("votes", 0) ?: 0)
        val safeVotes = primaryVotes.coerceAtLeast(10)

        val avgScore = primaryRating.coerceIn(1.0, 10.0)
        val centerBucket = avgScore.toInt().coerceIn(1, 10)
        val dist = mutableListOf<KitsugiScoreStat>()
        for (score in 1..10) {
            val distance = kotlin.math.abs(score - centerBucket)
            val weight = when (distance) {
                0 -> 0.35
                1 -> 0.22
                2 -> 0.10
                3 -> 0.04
                else -> 0.01
            }
            dist.add(KitsugiScoreStat(score = score, amount = (safeVotes * weight).toInt().coerceAtLeast(1)))
        }

        val rankings = mutableListOf<KitsugiRanking>()
        val rank = obj.optInt("rank", 0).takeIf { it > 0 }
            ?: malRating?.optInt("rank", 0)?.takeIf { it > 0 }
        if (rank != null) {
            rankings.add(
                KitsugiRanking(
                    rank = rank,
                    type = "RATED",
                    context = KitsugiStrings.get(R.string.simkl_mal_genel_siralama),
                    allTime = true
                )
            )
        }

        return KitsugiStats(
            watching = (safeVotes * 0.18).toInt(),
            completed = (safeVotes * 0.58).toInt(),
            paused = (safeVotes * 0.05).toInt(),
            dropped = (safeVotes * 0.04).toInt(),
            planned = (safeVotes * 0.15).toInt(),
            scoreDistribution = dist,
            rankings = rankings
        )
    }

    suspend fun fetchSimklDetailDirect(
        simklId: Int,
        mediaType: MediaType
    ): KitsugiMediaDetail? {
        return try {
            val obj = fetchSimklRawJson(simklId, mediaType) ?: return null

            val overview = obj.optString("overview", "")
            val poster = obj.optString("poster", "")
            val year = obj.optInt("year", 0)
            val runtime = obj.optInt("runtime", 0)
            val totalEpisodes = obj.optInt("total_episodes", 0)
            val genresArray = obj.optJSONArray("genres")
            val genresList = mutableListOf<String>()
            if (genresArray != null) {
                for (i in 0 until genresArray.length()) {
                    genresList.add(genresArray.getString(i))
                }
            }

            val ids = obj.optJSONObject("ids")
            val tmdbId = parseOptionalId(ids, "tmdb") ?: 0
            val realMalId = parseOptionalId(ids, "mal") ?: 0

            // ── Başlık zinciri: yerelleştirilmiş (Türkçe) → İngilizce → Romaji ──────
            val simklTitle = obj.optString("title", "")
            val simklEnglishTitle = MediaTitleResolver.nonBlank(
                obj.optString("en_title"),
                obj.optString("title_en"),
                obj.optString("title_english")
            )
            val simklRomaji = MediaTitleResolver.nonBlank(
                obj.optString("title_romaji"),
                obj.optString("romaji")
            )
            val simklNative = MediaTitleResolver.nonBlank(
                obj.optString("ja_title"),
                obj.optString("title_native"),
                obj.optString("title_japanese")
            )

            val isTmdbMovie = mediaType == MediaType.Movie ||
                obj.optString("anime_type", "").equals("movie", ignoreCase = true)
            val localizedTitle = if (tmdbId > 0) fetchTmdbLocalizedTitle(tmdbId, isTmdbMovie) else null

            val englishCandidate = MediaTitleResolver.latin(simklEnglishTitle)
                ?: MediaTitleResolver.latin(simklTitle)
            val resolvedTitle = MediaTitleResolver.resolve(
                localized = localizedTitle,
                english = englishCandidate,
                romaji = simklRomaji,
                original = simklNative ?: simklTitle
            )
            val resolvedTitleEnglish = MediaTitleResolver.resolveEnglish(
                localized = localizedTitle,
                english = englishCandidate,
                romaji = simklRomaji,
                original = simklNative
            ) ?: resolvedTitle
            val resolvedTitleJapanese = listOfNotNull(
                simklNative,
                simklTitle.takeIf { MediaTitleResolver.hasCjk(it) }
            ).firstOrNull { it.isNotBlank() && MediaTitleResolver.hasCjk(it) }

            val ratingsObj = obj.optJSONObject("ratings")
            val ratingObj = ratingsObj?.optJSONObject("simkl") ?: ratingsObj?.optJSONObject("mal")
            val ratingScore = ratingObj?.optDouble("rating", 0.0) ?: 0.0
            val votesCount = ratingObj?.optInt("votes", 0)?.takeIf { it > 0 }
            val rankVal = obj.optInt("rank", 0).takeIf { it > 0 }
                ?: ratingsObj?.optJSONObject("mal")?.optInt("rank", 0)?.takeIf { it > 0 }

            // Simkl pictures: büyük poster (_w.jpg) + orta poster (_m.jpg)
            val simklPictures = if (poster.isNotEmpty()) {
                listOfNotNull(
                    "https://simkl.in/posters/${poster}_w.jpg",
                    "https://simkl.in/posters/${poster}_m.jpg"
                )
            } else emptyList()

            val simklTags = genresList
                .filter { it.isNotBlank() && !com.kitsugi.animelist.ui.screens.search.CanonicalFilterBridge.isOfficialAniListGenre(it) }
                .map {
                    KitsugiTag(
                        name = it,
                        isSpoiler = com.kitsugi.animelist.utils.KitsugiSpoilerRules.isIntrinsicSpoiler(it),
                        source = "simkl"
                    )
                }

            // Simkl stüdyo/yapımcı **yayınlamaz** (API'de alan yok) ama `network`
            // alanını döndürür. Daha önce hiç okunmuyordu → Simkl kaynaklı
            // sayfalarda kanal listesi boştu.
            val simklNetworks = mutableListOf<KitsugiStudio>()
            fun addSimklNetwork(rawName: String?) {
                val name = rawName?.trim().orEmpty()
                if (name.isEmpty() || name == "null") return
                if (simklNetworks.any { it.name.equals(name, ignoreCase = true) }) return
                simklNetworks.add(
                    KitsugiStudio(
                        id = 0,
                        name = name,
                        isMain = false,
                        source = "simkl",
                        role = StudioRole.NETWORK
                    )
                )
            }
            addSimklNetwork(obj.optString("network", ""))
            obj.optJSONArray("networks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)
                    addSimklNetwork(if (item != null) item.optString("name", "") else arr.optString(i, ""))
                }
            }

            KitsugiMediaDetail(
                synopsis = overview,
                genres = genresList.toTurkishGenres(),
                status = obj.optString("status", "").toTurkishStatus(),
                season = obj.optString("season", "").takeIf { it.isNotBlank() && it != "null" },
                sourceMaterial = null,
                studios = emptyList(),
                producers = emptyList(),
                networks = simklNetworks,
                rating = if (ratingScore > 0.0) ratingScore.toString() else null,
                broadcast = null,
                episodeDuration = if (runtime > 0) "$runtime min".toTurkishDuration() else null,
                startDate = obj.optString("first_aired", "").takeIf { it.isNotBlank() && it != "null" }?.take(10),
                endDate = obj.optString("last_aired", "").takeIf { it.isNotBlank() && it != "null" }?.take(10),
                titleEnglish = resolvedTitleEnglish,
                titleJapanese = resolvedTitleJapanese,
                // 2026-11-02: Burada `null` bırakılıyordu → Simkl kaydından açılan akışta
                // AniList/MAL kaydına göre DAHA AZ arama varyantı oluşuyordu. Romaji alanı
                // Simkl'in kendi romaji alanından doldurulur (yoksa boş kalır).
                titleRomaji = simklRomaji,
                titleNative = simklNative ?: resolvedTitleJapanese,
                synonyms = listOfNotNull(simklRomaji, simklTitle.takeIf { it != resolvedTitle })
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct(),
                openings = emptyList(),
                endings = emptyList(),
                trailerUrl = extractSimklTrailerVideos(obj, simklId).firstOrNull()?.videoUrl,
                title = resolvedTitle,
                imageUrl = if (poster.isNotEmpty()) "https://simkl.in/posters/${poster}_m.jpg" else null,
                score = if (ratingScore > 0.0) (ratingScore * 10).toInt() else null,
                year = if (year > 0) year else null,
                total = if (totalEpisodes > 0) totalEpisodes else null,
                isAdult = com.kitsugi.animelist.data.remote.SimklAdultFlags.isAdult(obj),
                realMalId = if (realMalId > 0) realMalId else null,
                tags = simklTags,
                externalLinks = emptyList(),
                streamingLinks = emptyList(),
                streamingEpisodes = emptyList(),
                tmdbId = if (tmdbId > 0) tmdbId else null,
                tmdbSeason = 1,
                pictures = simklPictures,
                rank = rankVal,
                members = votesCount,
                type = mediaType,
                countryOfOrigin = obj.optString("country", "").trim().takeIf { it.isNotEmpty() && it != "null" }
            )
        } catch (e: Exception) {
            // T3-05: printStackTrace → Log.e
            android.util.Log.e("KitsugiSimklDetailClient", "fetchSimklDetailDirect simklId=$simklId failed: ${e.message}", e)
            null
        }
    }

    /**
     * Simkl kaydının TMDB karşılığından aktif dildeki (varsayılan: Türkçe) başlığı çeker.
     *
     * Simkl API'si Türkçe başlık sunmadığı için "Türkçe → İngilizce → Romaji" zincirinin
     * ilk adımı TMDB üzerinden tamamlanır. Tek bir hafif istek yapılır; aktif dil zaten
     * İngilizce ise ya da TMDB anahtarı yoksa istek atlanır.
     */
    private suspend fun fetchTmdbLocalizedTitle(tmdbId: Int, isMovie: Boolean): String? =
        withContext(Dispatchers.IO) {
            val language = TmdbApiClient.getActiveLanguage()
            if (language.startsWith("en", ignoreCase = true)) return@withContext null
            val apiKey = TmdbApiClient.getActiveApiKey()
            if (apiKey.isBlank()) return@withContext null
            val typePath = if (isMovie) "movie" else "tv"
            val url = "https://api.themoviedb.org/3/$typePath/$tmdbId?api_key=$apiKey&language=$language"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val text = response.body?.string() ?: return@withContext null
                    val obj = JSONObject(text)
                    val title = if (isMovie) obj.optString("title", "") else obj.optString("name", "")
                    MediaTitleResolver.latin(title)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("KitsugiSimklDetailClient", "TMDB yerelleştirilmiş başlık alınamadı (tmdb=$tmdbId): ${e.message}")
                null
            }
        }

    internal fun extractSimklTrailerVideos(obj: JSONObject?, simklId: Int): List<MediaVideoItem> {
        val arr = obj?.optJSONArray("trailers") ?: return emptyList()
        val list = mutableListOf<MediaVideoItem>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val rawYt = item.optString("youtube", "").trim()
            val ytId = extractCanonicalYouTubeId(rawYt) ?: continue
            val rawName = item.optString("name", "").trim()
            val label = rawName.ifBlank { KitsugiStrings.get(R.string.source_video_trailer) }
            list += MediaVideoItem(
                key = "simkl_trailer_${simklId}_$ytId",
                title = label,
                videoUrl = "https://www.youtube.com/watch?v=$ytId",
                category = MediaVideoCategory.TRAILER,
                source = "simkl"
            )
        }
        return list
    }

    /**
     * Harici kimliklerden (MAL / AniList / TMDB / IMDb) Simkl kimliğini çözümler.
     */
    suspend fun resolveSimklIdByExternalIds(
        malId: Int? = null,
        aniListId: Int? = null,
        tmdbId: Int? = null,
        imdbId: String? = null,
        mediaType: MediaType = MediaType.Anime
    ): Int? = withContext(Dispatchers.IO) {
        if (mediaType == MediaType.Manga) return@withContext null
        val clientId = com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID
        if (clientId.isBlank()) return@withContext null

        val queryPair: Pair<String, String> = when {
            malId != null && malId in 1..99_999_999 -> "mal" to malId.toString()
            aniListId != null && aniListId > 0 -> "anilist" to aniListId.toString()
            !imdbId.isNullOrBlank() -> "imdb" to imdbId.trim()
            tmdbId != null && tmdbId > 0 -> "tmdb" to tmdbId.toString()
            else -> return@withContext null
        }

        val typeHint = when (mediaType) {
            MediaType.Movie -> "movie"
            MediaType.TvShow -> "tv"
            else -> "anime"
        }
        val cacheKey = "$typeHint:${queryPair.first}:${queryPair.second}"
        externalSimklIdCache[cacheKey]?.let { cached ->
            return@withContext if (cached > 0) cached else null
        }

        val extraType = if (queryPair.first == "tmdb") "&type=$typeHint" else ""
        val urlString = "https://api.simkl.com/search/id?${queryPair.first}=${queryPair.second}$extraType&client_id=$clientId"
        val found = try {
            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("simkl")
            val request = Request.Builder()
                .url(urlString)
                .header("Accept", "application/json")
                .build()
            com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string()?.trim() ?: return@withContext null
                if (!body.startsWith("[")) return@withContext null
                val arr = org.json.JSONArray(body)
                var resolved = 0
                for (i in 0 until arr.length()) {
                    val entry = arr.optJSONObject(i) ?: continue
                    val id = parseOptionalId(entry.optJSONObject("ids"), "simkl") ?: 0
                    if (id > 0) {
                        resolved = id
                        break
                    }
                }
                resolved
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("KitsugiSimklDetailClient", "resolveSimklIdByExternalIds failed: ${e.message}")
            return@withContext null
        }

        externalSimklIdCache[cacheKey] = found
        found.takeIf { it > 0 }
    }

    /**
     * Simkl `extended=full` yanıtından fragman videolarını döndürür.
     */
    suspend fun fetchSimklVideos(
        simklId: Int,
        mediaType: MediaType
    ): List<MediaVideoItem> {
        if (simklId <= 0 || mediaType == MediaType.Manga) return emptyList()
        val cacheKey = when (mediaType) {
            MediaType.Movie -> "movies:$simklId"
            MediaType.TvShow -> "tv:$simklId"
            else -> "anime:$simklId"
        }
        if (!rawJsonCache.containsKey(cacheKey)) {
            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("simkl")
        }
        val obj = fetchSimklRawJson(simklId, mediaType) ?: return emptyList()
        return extractSimklTrailerVideos(obj, simklId)
    }
}
