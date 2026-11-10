package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.*

/**
 * TMDB API'sinden medya detaylarını, görsellerini, izleme sağlayıcılarını
 * ve videolarını (fragman/tema) çeken istemci.
 *
 * [TmdbApiClient] tarafından delegate olarak kullanılır; doğrudan çağrılmamalıdır.
 */
internal data class TmdbDetailFeatures(
    val useBasicInfo: Boolean = true,
    val useDetails: Boolean = true,
    val useReleaseDates: Boolean = true,
    val useArtwork: Boolean = true,
    val useTrailers: Boolean = true,
    val useProductions: Boolean = true,
    val useNetworks: Boolean = true
)

internal object TmdbMediaDetailClient {

    private const val TAG = "TmdbMediaDetailClient"
    private const val IMG_W500 = "https://image.tmdb.org/t/p/w500"
    private const val IMG_W780 = "https://image.tmdb.org/t/p/w780"
    private const val IMG_W1280 = "https://image.tmdb.org/t/p/w1280"

    private fun appString(resId: Int, fallback: String, vararg args: Any): String {
        val context = KitsugiApplication.getInstance() ?: return fallback.format(*args)
        return runCatching { context.getString(resId, *args) }.getOrDefault(fallback.format(*args))
    }

    suspend fun fetchMediaDetail(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        features: TmdbDetailFeatures = TmdbDetailFeatures(),
        executeGet: suspend (String) -> String?
    ): KitsugiMediaDetail? {
        val typePath = if (isMovie) "movie" else "tv"
        val lang = language

        // append_to_response=alternative_titles,keywords: ek ağ isteği olmadan alternatif başlıklar
        // (TR / US / JP ...) ve anahtar kelimeler (etiketler) gelir.
        val trUrl = "https://api.themoviedb.org/3/$typePath/$tmdbId?api_key=$apiKey&language=$lang&append_to_response=alternative_titles,keywords"
        val enUrl = TmdbUrlUtils.englishVariant(trUrl)

        return try {
            val trResponse = executeGet(trUrl)
            val trJson = trResponse?.let { JSONObject(it) }

            val enResponse = if (lang.startsWith("en", ignoreCase = true)) trResponse else executeGet(enUrl)
            val enJson = if (lang.startsWith("en", ignoreCase = true)) trJson else enResponse?.let { JSONObject(it) }

            val finalOverview = if (!features.useDetails) {
                null
            } else {
                val trOverview = trJson?.optString("overview", "")
                if (trOverview.isNullOrBlank()) enJson?.optString("overview", "").orEmpty() else trOverview
            }

            val finalJson = trJson ?: enJson ?: return null

            val rawTrTitle = if (isMovie) trJson?.optString("title", "") else trJson?.optString("name", "")
            val rawEnTitle = if (isMovie) enJson?.optString("title", "") else enJson?.optString("name", "")
            val originalTitle = if (isMovie) finalJson.optString("original_title", "") else finalJson.optString("original_name", "")
            val originalLang = finalJson.optString("original_language", "")

            // Türkçe başlık yoksa veya TMDB orijinal (Japonca) başlığa düşmüşse
            // otomatik olarak İngilizce'ye, o da yoksa Romaji/Latin alternatife in.
            val alternatives = extractAlternativeTitles(finalJson)
            val altTr = MediaTitleResolver.pickAlternative(alternatives, listOf("TR"))
            val altEn = MediaTitleResolver.pickAlternative(alternatives, listOf("US", "GB"))
            val altRomaji = MediaTitleResolver.pickAlternative(alternatives, listOf("JP"))
            val altLatin = MediaTitleResolver.pickAlternative(alternatives, emptyList())

            val localizedFallback = MediaTitleResolver.isLocalizedFallback(rawTrTitle, originalTitle, lang, originalLang)
            val localizedCandidate = MediaTitleResolver.latin(rawTrTitle?.takeIf { !localizedFallback })
                ?: MediaTitleResolver.latin(altTr)
            val englishCandidate = MediaTitleResolver.latin(rawEnTitle)
                ?: MediaTitleResolver.latin(altEn)
            val romajiCandidate = MediaTitleResolver.latin(altRomaji)
                ?: MediaTitleResolver.latin(altLatin)
                ?: MediaTitleResolver.latin(originalTitle)

            val finalTitle = MediaTitleResolver.resolve(
                localized = localizedCandidate,
                english = englishCandidate,
                romaji = romajiCandidate,
                original = originalTitle.ifBlank { rawTrTitle.orEmpty() }
            )

            val titleEnglish = MediaTitleResolver.resolveEnglish(
                localized = localizedCandidate,
                english = englishCandidate,
                romaji = romajiCandidate,
                original = originalTitle
            ) ?: finalTitle
            val isJapanese = originalLang.equals("ja", ignoreCase = true) || com.kitsugi.animelist.utils.PreferenceHelpers.hasCjkCharacters(originalTitle)
            val titleJapanese = if (isJapanese) originalTitle.takeIf { it.isNotBlank() } else null

            // Türkçe başlık: TMDB alternative_titles'dan TR kodlu başlık veya language=tr-TR ile
            // çekilen başlık. Türk streaming siteleri için arama kritik — bu başlık
            // alternativeTitles listesine eklenir ve CsStreamRunner tarafından aranır.
            val titleTurkish = rawTrTitle?.takeIf { it.isNotBlank() && !localizedFallback }
                ?: altTr?.takeIf { it.isNotBlank() }

            // TMDB'de ayrı bir "romaji" alanı yoktur. Latin alternatif başlıklar eşleştirme
            // (stream/eklenti arama) için synonyms'e taşınır; detay ekranları birincil başlık
            // olarak yerelleştirilmiş (Türkçe) başlığı korur.
            val titleRomaji: String? = null
            val synonymList = alternatives
                .map { it.second.trim() }
                .filter { it.isNotBlank() && it != finalTitle && it != titleEnglish && it != titleJapanese }
                .distinct()
                .take(12)

            val posterPath = finalJson.optNullableString("poster_path") ?: ""
            val genresList = mutableListOf<String>()
            if (features.useBasicInfo) {
                val genresArray = finalJson.optJSONArray("genres")
                if (genresArray != null) {
                    for (i in 0 until genresArray.length()) {
                        genresList.add(genresArray.getJSONObject(i).optString("name", ""))
                    }
                }
            }

            val rating = if (features.useBasicInfo) finalJson.optDouble("vote_average", 0.0) else 0.0
            val voteCount = if (features.useBasicInfo) finalJson.optInt("vote_count", 0).takeIf { it > 0 } else null
            val tmdbPopularity = if (features.useBasicInfo) finalJson.optDouble("popularity", 0.0).toInt().takeIf { it > 0 } else null
            val nextEpisodeObj = finalJson.optJSONObject("next_episode_to_air")
            // nextAiringEpisode makine biçimi: "episode|epoch" (tüm kaynaklarda ortak).
            // Dizi: next_episode_to_air; bölüm numarası bilinmiyorsa -1 (Dizi işareti).
            // Film: gelecek vizyon tarihi → 0 (Film işareti).
            val nextAiring = if (features.useReleaseDates) {
                if (nextEpisodeObj != null) {
                    val epRaw = nextEpisodeObj.optInt("episode_number", 0)
                    val ep = if (epRaw > 0) epRaw else -1
                    val dateStr = nextEpisodeObj.optString("air_date")
                    val epoch = com.kitsugi.animelist.utils.NextAiringFormat.isoDateToEpoch(dateStr)
                    when {
                        epoch != null -> "$ep|$epoch"
                        // Tarih çözülemezse eski düz metin biçimi (NextAiringFormat.parse bunu da okur)
                        ep > 0 && dateStr.isNotBlank() -> "Bölüm $ep, $dateStr tarihinde yayında"
                        else -> null
                    }
                } else if (isMovie) {
                    val movieDate = finalJson.optString("release_date", "")
                    val epoch = com.kitsugi.animelist.utils.NextAiringFormat.isoDateToEpoch(movieDate)
                    if (epoch != null && epoch > System.currentTimeMillis() / 1000L) "0|$epoch" else null
                } else null
            } else null

            val releaseDate = if (features.useReleaseDates) {
                if (isMovie) finalJson.optString("release_date", "") else finalJson.optString("first_air_date", "")
            } else ""
            val status = if (features.useBasicInfo) finalJson.optString("status", "").toTurkishStatus() else null
            val year = if (features.useReleaseDates) releaseDate.split("-").firstOrNull()?.toIntOrNull() else null

            val totalEpisodes = if (!features.useBasicInfo) null else if (isMovie) 1 else finalJson.optInt("number_of_episodes", 0)
            val totalSeasonsVal = if (!features.useBasicInfo || isMovie) null else finalJson.optInt("number_of_seasons", 1)

            val durationVal = if (!features.useBasicInfo) null else if (isMovie) {
                val runtime = finalJson.optInt("runtime", 0)
                if (runtime > 0) "$runtime dk" else null
            } else {
                val runTimes = finalJson.optJSONArray("episode_run_time")
                if (runTimes != null && runTimes.length() > 0) {
                    val runTime = runTimes.optInt(0, 0)
                    if (runTime > 0) "$runTime dk" else null
                } else null
            }

            val endDateVal = if (!features.useReleaseDates) null else if (isMovie) {
                releaseDate.takeIf { it.isNotBlank() }
            } else {
                finalJson.optString("last_air_date", "").takeIf { it.isNotEmpty() }
            }

            val studiosList = mutableListOf<KitsugiStudio>()
            val producersList = mutableListOf<KitsugiStudio>()
            if (features.useProductions) {
                val productionCompanies = finalJson.optJSONArray("production_companies")
                if (productionCompanies != null) {
                    for (i in 0 until productionCompanies.length()) {
                        val company = productionCompanies.getJSONObject(i)
                        val id = company.optInt("id", 0)
                        val name = company.optString("name", "")
                        val logoPath = company.optString("logo_path", "").takeIf { it.isNotBlank() && it != "null" }
                        val companyLogoUrl = logoPath?.let { "https://image.tmdb.org/t/p/w300$it" }
                        if (name.isNotEmpty()) {
                            if (i == 0) {
                                studiosList.add(KitsugiStudio(id = id, name = name, isMain = true, source = "tmdb", imageUrl = companyLogoUrl))
                            } else {
                                producersList.add(KitsugiStudio(id = id, name = name, isMain = false, source = "tmdb", role = com.kitsugi.animelist.data.remote.StudioRole.PRODUCER, imageUrl = companyLogoUrl))
                            }
                        }
                    }
                }
            }

            val networksList = mutableListOf<KitsugiStudio>()
            if (features.useNetworks) {
                val networksArray = finalJson.optJSONArray("networks")
                if (networksArray != null) {
                    for (i in 0 until networksArray.length()) {
                        val network = networksArray.getJSONObject(i)
                        val id = network.optInt("id", 0)
                        val name = network.optString("name", "")
                        val logoPath = network.optString("logo_path", "").takeIf { it.isNotBlank() && it != "null" }
                        val logoUrl = logoPath?.let { "https://image.tmdb.org/t/p/w300$it" }
                        if (name.isNotBlank()) {
                            networksList.add(
                                KitsugiStudio(
                                    id = id,
                                    name = name,
                                    isMain = networksList.isEmpty(),
                                    source = "tmdb",
                                    role = com.kitsugi.animelist.data.remote.StudioRole.NETWORK,
                                    imageUrl = logoUrl
                                )
                            )
                        }
                    }
                }
            }

            val watchProviders = if (features.useNetworks) fetchWatchProviders(tmdbId, isMovie, apiKey, executeGet) else emptyList()
            val mediaImages = if (features.useArtwork) fetchMediaImages(tmdbId, isMovie, apiKey, executeGet) else emptyList()
            val (trailer, videoThemes) = if (features.useTrailers) {
                fetchVideos(tmdbId, isMovie, apiKey, language, executeGet)
            } else Pair(null, emptyList())

            val score100 = (rating * 10).toInt().coerceIn(0, 100)
            val tmdbTags = if (features.useBasicInfo) extractKeywords(finalJson, isMovie) else emptyList()

            KitsugiMediaDetail(
                synopsis = finalOverview,
                genres = genresList.toTurkishGenres(),
                status = status,
                season = null,
                sourceMaterial = null,
                studios = studiosList,
                producers = producersList,
                networks = networksList,
                rating = rating.takeIf { features.useBasicInfo }?.toString(),
                broadcast = null,
                episodeDuration = durationVal,
                startDate = releaseDate.takeIf { features.useReleaseDates && it.isNotBlank() },
                endDate = endDateVal,
                titleEnglish = titleEnglish,
                titleJapanese = titleJapanese,
                titleRomaji = titleRomaji,
                titleNative = originalTitle,
                titleTurkish = titleTurkish,
                synonyms = synonymList,
                openings = videoThemes,
                endings = emptyList(),
                trailerUrl = trailer,
                title = finalTitle,
                imageUrl = if (features.useArtwork && posterPath.isNotEmpty()) "$IMG_W500$posterPath" else null,
                score = if (features.useBasicInfo) rating.toInt().coerceIn(0, 10) else null,
                year = year,
                total = totalEpisodes,
                isAdult = features.useBasicInfo && finalJson.optBoolean("adult", false),
                realMalId = null,
                tags = tmdbTags,
                externalLinks = watchProviders,
                streamingLinks = watchProviders,
                streamingEpisodes = emptyList(),
                tmdbId = tmdbId,
                tmdbSeason = 1,
                format = if (isMovie) "MOVIE" else "TV",
                rawFormat = if (isMovie) "movie" else "tv",
                pictures = mediaImages,
                totalSeasons = totalSeasonsVal,
                nextAiringEpisode = nextAiring,
                meanScore = score100.takeIf { features.useBasicInfo },
                averageScore = score100.takeIf { features.useBasicInfo },
                popularity = tmdbPopularity,
                scoredBy = voteCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB details: ${e.message}", e)
            null
        }
    }

    /**
     * `append_to_response=alternative_titles` yanıtından (ülke kodu → başlık) çiftlerini çıkarır.
     * Film uçları `titles`, dizi uçları `results` dizisini döndürür.
     */
    private fun extractAlternativeTitles(json: JSONObject?): List<Pair<String, String>> {
        val altObj = json?.optJSONObject("alternative_titles") ?: return emptyList()
        val array = altObj.optJSONArray("titles") ?: altObj.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val altTitle = item.optString("title", "").trim()
            if (altTitle.isEmpty()) continue
            out.add(item.optString("iso_3166_1", "").uppercase() to altTitle)
        }
        return out
    }

    /**
     * `append_to_response=keywords` yanıtından TMDB anahtar kelimelerini (etiketleri) çıkarır.
     * Film uçları `keywords`, dizi uçları `results` dizisini döndürür.
     */
    private fun extractKeywords(json: JSONObject?, isMovie: Boolean): List<KitsugiTag> {
        val kwObj = json?.optJSONObject("keywords") ?: return emptyList()
        val array = (if (isMovie) kwObj.optJSONArray("keywords") else kwObj.optJSONArray("results"))
            ?: kwObj.optJSONArray("keywords")
            ?: kwObj.optJSONArray("results")
            ?: return emptyList()
        val out = mutableListOf<KitsugiTag>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) continue
            val id = item.optInt("id").takeIf { it > 0 }
            out.add(
                KitsugiTag(
                    id = id,
                    name = name,
                    isSpoiler = com.kitsugi.animelist.utils.KitsugiSpoilerRules.isIntrinsicSpoiler(name),
                    source = "tmdb"
                )
            )
        }
        return com.kitsugi.animelist.utils.KitsugiSpoilerRules.normalizeAndMergeTags(out)
    }

    suspend fun fetchMediaImages(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        executeGet: suspend (String) -> String?
    ): List<String> = withContext(Dispatchers.IO) {
        val typePath = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typePath/$tmdbId/images?api_key=$apiKey"
        try {
            val responseText = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(responseText)
            val list = mutableListOf<String>()

            val backdrops = root.optJSONArray("backdrops")
            if (backdrops != null) {
                for (i in 0 until minOf(backdrops.length(), 15)) {
                    val path = backdrops.getJSONObject(i).optNullableString("file_path") ?: ""
                    if (path.isNotEmpty()) list.add("$IMG_W1280$path")
                }
            }

            val posters = root.optJSONArray("posters")
            if (posters != null) {
                for (i in 0 until minOf(posters.length(), 15)) {
                    val path = posters.getJSONObject(i).optNullableString("file_path") ?: ""
                    if (path.isNotEmpty()) list.add("$IMG_W780$path")
                }
            }
            list.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB images: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchWatchProviders(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        executeGet: suspend (String) -> String?
    ): List<KitsugiExternalLink> = withContext(Dispatchers.IO) {
        val typePath = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typePath/$tmdbId/watch/providers?api_key=$apiKey"
        try {
            val responseText = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(responseText)
            val results = root.optJSONObject("results") ?: return@withContext emptyList()
            val providerObj = results.optJSONObject("TR")
                ?: results.optJSONObject("US")
                ?: results.keys().asSequence().firstOrNull()?.let { results.optJSONObject(it) }

            if (providerObj != null) {
                val tmdbLink = providerObj.optNullableString("link") ?: ""
                val list = mutableListOf<KitsugiExternalLink>()
                val flatrate = providerObj.optJSONArray("flatrate")
                if (flatrate != null) {
                    for (i in 0 until flatrate.length()) {
                        val item = flatrate.getJSONObject(i)
                        val name = item.optString("provider_name", "Streaming")
                        list.add(KitsugiExternalLink(site = name, url = tmdbLink, language = "TR"))
                    }
                }
                list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching watch providers: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchStructuredVideos(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        seasonNumber: Int? = null,
        executeGet: suspend (String) -> String?
    ): List<MediaVideoItem> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || apiKey.isBlank()) return@withContext emptyList()
        val typePath = if (isMovie) "movie" else "tv"

        val trUrl = "https://api.themoviedb.org/3/$typePath/$tmdbId/videos?api_key=$apiKey&language=$language"
        val enUrl = "https://api.themoviedb.org/3/$typePath/$tmdbId/videos?api_key=$apiKey&language=en-US"

        try {
            fun parseItems(arr: org.json.JSONArray?, prefix: String): List<MediaVideoItem> {
                if (arr == null) return emptyList()
                val list = mutableListOf<MediaVideoItem>()
                for (i in 0 until arr.length()) {
                    val video = arr.optJSONObject(i) ?: continue
                    val site = video.optString("site", "")
                    val type = video.optString("type", "")
                    val key = video.optString("key", "").trim()
                    val name = video.optString("name", "").trim()
                    if (!site.equals("YouTube", ignoreCase = true) || key.isEmpty()) continue
                    val typeLower = type.lowercase(java.util.Locale.ROOT)
                    val typeLabel = when (typeLower) {
                        "trailer" -> appString(R.string.source_video_trailer, "Trailer")
                        "teaser" -> appString(R.string.source_video_teaser, "Teaser")
                        "opening credits" -> appString(R.string.source_video_opening_credits, "Opening Credits")
                        "clip" -> appString(R.string.source_video_clip, "Clip")
                        "featurette" -> appString(R.string.source_video_featurette, "Featurette")
                        "behind the scenes" -> appString(R.string.source_video_behind_scenes, "Behind the scenes")
                        "bloopers" -> appString(R.string.source_video_bloopers, "Bloopers")
                        else -> type
                    }
                    val category = when (typeLower) {
                        "trailer", "teaser" -> MediaVideoCategory.TRAILER
                        "opening credits" -> MediaVideoCategory.OPENING
                        else -> MediaVideoCategory.EXTRA
                    }
                    val fullTitle = when {
                        name.isBlank() -> typeLabel
                        name.startsWith(typeLabel, ignoreCase = true) -> name
                        typeLabel.isNotBlank() -> "$typeLabel - $name"
                        else -> name
                    }
                    list += MediaVideoItem(
                        key = "tmdb_${prefix}_${tmdbId}_$key",
                        title = fullTitle,
                        videoUrl = "https://www.youtube.com/watch?v=$key",
                        category = category,
                        source = "tmdb"
                    )
                }
                return list
            }

            val trBody = executeGet(trUrl)
            val trItems = parseItems(trBody?.let { JSONObject(it).optJSONArray("results") }, "loc")

            val enItems = if (language.startsWith("en", ignoreCase = true)) {
                emptyList()
            } else {
                val enBody = executeGet(enUrl)
                parseItems(enBody?.let { JSONObject(it).optJSONArray("results") }, "en")
            }

            val seasonItems = if (!isMovie && seasonNumber != null && seasonNumber > 0) {
                val sUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber/videos?api_key=$apiKey&language=en-US"
                val sBody = executeGet(sUrl)
                parseItems(sBody?.let { JSONObject(it).optJSONArray("results") }, "s$seasonNumber")
            } else emptyList()

            val merged = LinkedHashMap<String, MediaVideoItem>()
            for (item in (trItems + seasonItems + enItems)) {
                val ytKey = extractCanonicalYouTubeId(item.videoUrl) ?: item.videoUrl.orEmpty()
                if (ytKey.isNotEmpty() && !merged.containsKey(ytKey)) {
                    merged[ytKey] = item
                }
            }
            val result = merged.values.toList()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB videos: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchVideos(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): Pair<String?, List<KitsugiTheme>> = withContext(Dispatchers.IO) {
        val structured = fetchStructuredVideos(tmdbId, isMovie, apiKey, language, null, executeGet)
        val primaryTrailer = structured.firstOrNull { it.category == MediaVideoCategory.TRAILER }?.videoUrl
            ?: structured.firstOrNull()?.videoUrl
        val themes = structured.map { KitsugiTheme(label = it.title, videoUrl = it.videoUrl, source = "tmdb") }
        Pair(primaryTrailer, themes)
    }
}
