package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * AniList GraphQL API'sinden anime/manga detay bilgisini çeker ve parse eder.
 */
internal object KitsugiAniListDetailClient {

    data class AniListTagsAndAiring(
        val aniListId: Int?,
        val malId: Int?,
        val tags: List<KitsugiTag>,
        val nextAiringEpisode: String?,
        val genres: List<String> = emptyList(),
        val studios: List<KitsugiStudio> = emptyList()
    )

    // Long-lived detail browsing can touch thousands of media IDs; keep the alias cache bounded.
    private val tagsAndAiringCache = BoundedCache<String, AniListTagsAndAiring>("anilist.tagsAndAiring", 1_200)

    private fun parseAniListTags(arr: JSONArray?): List<KitsugiTag> {
        if (arr == null || arr.length() == 0) return emptyList()
        val tags = ArrayList<KitsugiTag>(arr.length())
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val tName = t.optNullableString("name") ?: continue
            val isSpoiler = t.optBoolean("isMediaSpoiler", false) ||
                t.optBoolean("isGeneralSpoiler", false) ||
                KitsugiSpoilerRules.isIntrinsicSpoiler(tName)
            tags.add(
                KitsugiTag(
                    name = tName,
                    rank = t.optionalPositiveInt("rank"),
                    isSpoiler = isSpoiler,
                    id = t.optionalPositiveInt("id"),
                    source = "anilist",
                    description = t.optNullableString("description"),
                    category = t.optNullableString("category")
                )
            )
        }
        return tags
    }

    suspend fun fetchDetail(stableId: Int, mediaType: MediaType): KitsugiMediaDetail? {
        val aniListId = if (stableId >= 100_000_000) stableId - 100_000_000 else null

        val idParam = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id" else "idMal: \$idMal"
        val typeStr = when (mediaType) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "ANIME"
            MediaType.Manga -> "MANGA"
        }

        val query = """
            query (${'$'}type: MediaType, $idParam) {
                Media($idFilter, type: ${'$'}type) {
                    id
                    idMal
                    format
                    countryOfOrigin
                    description(asHtml: false)
                    genres
                    status
                    season
                    seasonYear
                    source
                    duration
                    startDate { year month day }
                    endDate { year month day }
                    title { romaji english native }
                    synonyms
                    studios { edges { isMain node { id name } } }
                    trailer { id site }
                    streamingEpisodes { title thumbnail url site }
                    coverImage { extraLarge large }
                    bannerImage
                    meanScore
                    averageScore
                    popularity
                    favourites
                    isFavourite
                    episodes
                    chapters
                    isAdult
                    tags { id name description category rank isMediaSpoiler isGeneralSpoiler }
                    externalLinks { url site language type }
                    nextAiringEpisode { episode timeUntilAiring airingAt }
                    rankings { id rank type format year season allTime context }
                    stats {
                        statusDistribution { amount }
                        scoreDistribution { amount }
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("type", typeStr)
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", stableId)

        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return null
        return parseResponse(response, mediaType)
    }

    suspend fun fetchSynopsis(stableId: Int, mediaType: MediaType): String? {
        val aniListId = if (stableId >= 100_000_000) {
            stableId - 100_000_000
        } else {
            null
        }

        val query = if (aniListId != null) {
            """
                query (${ '$' }id: Int, ${ '$' }type: MediaType) {
                    Media(id: ${ '$' }id, type: ${ '$' }type) {
                        description(asHtml: false)
                    }
                }
            """.trimIndent()
        } else {
            """
                query (${ '$' }idMal: Int, ${ '$' }type: MediaType) {
                    Media(idMal: ${ '$' }idMal, type: ${ '$' }type) {
                        description(asHtml: false)
                    }
                }
            """.trimIndent()
        }

        val variables = JSONObject()
            .put(
                "type",
                when (mediaType) {
                    MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "ANIME"
                    MediaType.Manga -> "MANGA"
                }
            )

        if (aniListId != null) {
            variables.put("id", aniListId)
        } else {
            variables.put("idMal", stableId)
        }

        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return null
        return runCatching {
            val root = JSONObject(response)
            val media = root
                .optJSONObject("data")
                ?.optJSONObject("Media")
                ?: return@runCatching null

            media.optString("description")
                .cleanApiText()
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private suspend fun parseResponse(responseText: String, mediaType: MediaType): KitsugiMediaDetail? {
        val mediaObj = runCatching {
            JSONObject(responseText).optJSONObject("data")?.optJSONObject("Media")
        }.getOrNull() ?: return null

        var openings = emptyList<KitsugiTheme>()
        var endings = emptyList<KitsugiTheme>()
        if (mediaType == MediaType.Anime) {
            val aniListId = mediaObj.optionalPositiveInt("id")
            val malId = mediaObj.optionalPositiveInt("idMal")
            var themes = Pair<List<KitsugiTheme>, List<KitsugiTheme>>(emptyList(), emptyList())
            if (aniListId != null) {
                themes = KitsugiAnimeThemesClient.fetchAnimeThemes(aniListId, "AniList")
            }
            if (themes.first.isEmpty() && themes.second.isEmpty() && malId != null) {
                themes = KitsugiAnimeThemesClient.fetchAnimeThemes(malId, "MyAnimeList")
            }
            openings = themes.first
            endings = themes.second
        }

        return runCatching {
            val media = mediaObj

            val synopsis = media.optNullableString("description")?.cleanApiText()
            val genres = mutableListOf<String>()
            media.optJSONArray("genres")?.let { arr -> for (i in 0 until arr.length()) genres.add(arr.getString(i)) }

            val status = media.optNullableString("status")?.toTurkishStatus()
            val season = buildString {
                val s = media.optNullableString("season")
                val y = media.optInt("seasonYear").takeIf { it > 0 }
                if (s != null) append(s.replaceFirstChar { it.uppercase() })
                if (y != null) { if (isNotEmpty()) append(" "); append(y) }
            }.takeIf { it.isNotBlank() }?.toTurkishSeason()
            val sourceMaterial = media.optNullableString("source")?.toTurkishSourceMaterial()
            val duration = media.optInt("duration").takeIf { it > 0 }?.let { "$it dk" }

            fun dateStr(obj: JSONObject?): String? {
                val y = obj?.optInt("year")?.takeIf { it > 0 } ?: return null
                val m = obj.optInt("month").takeIf { it > 0 }
                val d = obj.optInt("day").takeIf { it > 0 }
                return buildString {
                    append(y)
                    if (m != null) append("-${m.toString().padStart(2, '0')}")
                    if (d != null) append("-${d.toString().padStart(2, '0')}")
                }
            }
            val startDate = dateStr(media.optJSONObject("startDate"))
            val endDate = dateStr(media.optJSONObject("endDate"))

            val titleEnglish = media.optJSONObject("title")?.optNullableString("english")
            val titleJapanese = media.optJSONObject("title")?.optNullableString("native")
            val synonyms = mutableListOf<String>()
            media.optJSONArray("synonyms")?.let { arr -> for (i in 0 until arr.length()) synonyms.add(arr.getString(i)) }

            val fallbackStudios = mutableListOf<KitsugiStudio>()
            media.optJSONObject("studios")?.optJSONArray("nodes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optInt("id")
                    val name = obj.optNullableString("name")
                    if (!name.isNullOrBlank()) {
                        fallbackStudios.add(KitsugiStudio(id = id, name = name, source = "anilist"))
                    }
                }
            }

            val trailerObj = media.optJSONObject("trailer")
            val trailerUrl = if (trailerObj != null) {
                val site = trailerObj.optNullableString("site")?.lowercase()
                val id = trailerObj.optNullableString("id")
                if (site == "youtube" && id != null) {
                    "https://www.youtube.com/watch?v=$id"
                } else null
            } else null

            val countryOfOrigin = media.optNullableString("countryOfOrigin")
            val isNonJapanese = countryOfOrigin != null && !countryOfOrigin.equals("JP", ignoreCase = true)
            val titleObject = media.optJSONObject("title")
            val titleRomaji = titleObject?.optNullableString("romaji")
            val aniListTitle = if (isNonJapanese && !titleEnglish.isNullOrBlank()) {
                titleEnglish
            } else {
                titleRomaji
                    ?: titleEnglish
                    ?: titleJapanese
                    ?: "Başlıksız"
            }

            val imageUrl = media.optJSONObject("coverImage")?.let { cover ->
                cover.optNullableString("extraLarge") ?: cover.optNullableString("large")
            }

            val meanScore = media.optionalPositiveInt("meanScore")
            val averageScore = media.optionalPositiveInt("averageScore")
            val popularity = media.optionalPositiveInt("popularity")
            val favorites = media.optionalPositiveInt("favourites")
            val isFavourite = media.optBoolean("isFavourite", false)

            val nextAiringObj = media.optJSONObject("nextAiringEpisode")
            val nextAiringEpisode = if (nextAiringObj != null) {
                val ep = nextAiringObj.optInt("episode")
                val airingAt = nextAiringObj.optLong("airingAt")
                "$ep|$airingAt"
            } else null

            var parsedRank: Int? = null
            var parsedPopularityRank: Int? = null
            media.optJSONArray("rankings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    val rType = r.optString("type")
                    val rVal = r.optionalPositiveInt("rank")
                    val allTime = r.optBoolean("allTime", false)
                    if (rType == "RATED" && (allTime || parsedRank == null)) {
                        parsedRank = rVal
                    }
                    if (rType == "POPULAR" && (allTime || parsedPopularityRank == null)) {
                        parsedPopularityRank = rVal
                    }
                }
            }

            var totalScoredBy: Int? = null
            media.optJSONObject("stats")?.optJSONArray("scoreDistribution")?.let { arr ->
                var sum = 0
                for (i in 0 until arr.length()) sum += arr.getJSONObject(i).optInt("amount")
                if (sum > 0) totalScoredBy = sum
            }

            var totalMembers: Int? = null
            media.optJSONObject("stats")?.optJSONArray("statusDistribution")?.let { arr ->
                var sum = 0
                for (i in 0 until arr.length()) sum += arr.getJSONObject(i).optInt("amount")
                if (sum > 0) totalMembers = sum
            }

            val score = (meanScore ?: averageScore)?.let { (it / 10.0).toInt().coerceIn(0, 10) }

            val year = media.optJSONObject("startDate")?.optionalPositiveInt("year") ?: media.optInt("seasonYear").takeIf { it > 0 }

            val total = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> media.optionalPositiveInt("episodes")
                MediaType.Manga -> media.optionalPositiveInt("chapters")
            }

            val isAdult = media.optBoolean("isAdult", false)
            val idMal = media.optionalPositiveInt("idMal")

            // Studios vs Producers (via edges.isMain)
            val parsedStudios = mutableListOf<KitsugiStudio>()
            val parsedProducers = mutableListOf<KitsugiStudio>()
            media.optJSONObject("studios")?.optJSONArray("edges")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val edge = arr.getJSONObject(i)
                    val node = edge.optJSONObject("node") ?: continue
                    val id = node.optInt("id")
                    val name = node.optNullableString("name") ?: continue
                    val isMainStudio = edge.optBoolean("isMain", false)
                    val studioObj = KitsugiStudio(id = id, name = name, isMain = isMainStudio, source = "anilist")
                    if (isMainStudio) parsedStudios.add(studioObj)
                    else parsedProducers.add(studioObj)
                }
            }

            // Tags
            val tags = parseAniListTags(media.optJSONArray("tags"))
            val parsedAniListId = media.optionalPositiveInt("id")
            if (tags.isNotEmpty() || nextAiringEpisode != null) {
                val cachedEntry = AniListTagsAndAiring(
                    aniListId = parsedAniListId,
                    malId = idMal,
                    tags = tags,
                    nextAiringEpisode = nextAiringEpisode
                )
                val typePrefix = if (mediaType == MediaType.Manga) "M" else "A"
                if (parsedAniListId != null) tagsAndAiringCache["$typePrefix:al:$parsedAniListId"] = cachedEntry
                if (idMal != null) tagsAndAiringCache["$typePrefix:mal:$idMal"] = cachedEntry
            }

            // Streaming Episodes
            val streamingEpisodes = mutableListOf<KitsugiStreamingEpisode>()
            media.optJSONArray("streamingEpisodes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val ep = arr.getJSONObject(i)
                    val epTitle = ep.optNullableString("title") ?: "Bölüm ${i + 1}"
                    val epThumb = ep.optNullableString("thumbnail")
                    val epUrl = ep.optNullableString("url")
                    val epSite = ep.optNullableString("site")
                    val epNum = KitsugiAnimeThemesClient.parseEpisodeNumberFromTitle(epTitle) ?: (i + 1)
                    streamingEpisodes.add(
                        KitsugiStreamingEpisode(
                            title = epTitle,
                            thumbnail = epThumb,
                            url = epUrl,
                            site = epSite,
                            seasonNumber = 1,
                            episodeNumber = epNum
                        )
                    )
                }
            }

            // External & Streaming links + TMDB ID extraction
            val externalLinks = mutableListOf<KitsugiExternalLink>()
            val streamingLinks = mutableListOf<KitsugiExternalLink>()
            var extractedTmdbId: Int? = null
            var extractedTmdbMovieId: Int? = null
            var extractedTmdbSeason: Int? = null
            media.optJSONArray("externalLinks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val url = obj.optNullableString("url")
                    val site = obj.optNullableString("site")
                    val lang = obj.optNullableString("language")
                    val type = obj.optString("type", "").uppercase()
                    if (url.isNullOrBlank() || site.isNullOrBlank()) continue
                    val link = KitsugiExternalLink(site = site, url = url, language = lang)
                    if (type == "STREAMING") streamingLinks.add(link)
                    else externalLinks.add(link)
                    if (url.contains("themoviedb.org", ignoreCase = true)) {
                        val movieId = Regex("""/movie/(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
                        val tvId = Regex("""/tv/(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
                        if (movieId != null && extractedTmdbMovieId == null) extractedTmdbMovieId = movieId
                        if (tvId != null && extractedTmdbId == null) {
                            extractedTmdbId = tvId
                            extractedTmdbSeason = Regex("""/season/(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
                        }
                    }
                }
            }

            // AniList pictures: coverImage + bannerImage
            val aniListPictures = buildList {
                val coverLarge = media.optJSONObject("coverImage")?.optNullableString("extraLarge")
                    ?: media.optJSONObject("coverImage")?.optNullableString("large")
                if (!coverLarge.isNullOrBlank()) add(coverLarge)
                val bannerImg = media.optNullableString("bannerImage")
                if (!bannerImg.isNullOrBlank()) add(bannerImg)
            }

            KitsugiMediaDetail(
                synopsis = synopsis,
                genres = genres.toTurkishGenres(),
                status = status,
                season = season,
                sourceMaterial = sourceMaterial,
                studios = if (parsedStudios.isNotEmpty()) parsedStudios else fallbackStudios,
                producers = parsedProducers,
                episodeDuration = duration,
                startDate = startDate,
                endDate = endDate,
                titleEnglish = titleEnglish,
                titleJapanese = titleJapanese,
                titleRomaji = if (isNonJapanese && !titleEnglish.isNullOrBlank()) titleEnglish else titleRomaji,
                titleNative = titleJapanese,
                synonyms = synonyms,
                trailerUrl = trailerUrl,
                title = aniListTitle,
                imageUrl = imageUrl,
                score = score,
                year = year,
                total = total,
                isAdult = isAdult,
                realMalId = idMal,
                tags = tags,
                externalLinks = externalLinks,
                streamingLinks = streamingLinks,
                streamingEpisodes = streamingEpisodes,
                openings = openings,
                endings = endings,
                tmdbId = if (media.optNullableString("format").equals("MOVIE", ignoreCase = true)) {
                    extractedTmdbMovieId ?: extractedTmdbId
                } else {
                    extractedTmdbId ?: extractedTmdbMovieId
                },
                tmdbSeason = extractedTmdbSeason,
                format = media.optNullableString("format"),
                rawFormat = media.optNullableString("format"),
                pictures = aniListPictures,
                nextAiringEpisode = nextAiringEpisode,
                meanScore = meanScore,
                averageScore = averageScore,
                popularity = popularity,
                favorites = favorites,
                rank = parsedRank,
                popularityRank = parsedPopularityRank,
                scoredBy = totalScoredBy,
                members = totalMembers ?: popularity,
                isFavourite = isFavourite
            )
        }.getOrNull()
    }

    suspend fun fetchNextAiringEpisodeOnly(aniListId: Int): String? {
        return fetchTagsAndAiringInfo(aniListId = aniListId, mediaType = MediaType.Anime)?.nextAiringEpisode
    }

    /**
     * Diğer kaynaklardan (Bangumi, MAL/Jikan, Shikimori, Kitsu, Simkl, TMDB vb.) açılan
     * yapımlar için AniList'in etiketlerini (`isMediaSpoiler`, `isGeneralSpoiler`, `rank`)
     * ve yayındaki sonraki bölüm bilgisini tek hafif sorguyla çeker.
     */
    suspend fun fetchTagsAndAiringInfo(
        aniListId: Int? = null,
        malId: Int? = null,
        searchTitle: String? = null,
        mediaType: MediaType = MediaType.Anime
    ): AniListTagsAndAiring? {
        val rawAniListId = when {
            aniListId == null || aniListId <= 0 -> null
            aniListId >= 100_000_000 -> aniListId - 100_000_000
            else -> aniListId
        }?.takeIf { it > 0 }
        val safeMalId = malId?.takeIf { it in 1..99_999_999 }
        val cleanTitle = searchTitle?.trim()?.takeIf { it.length >= 2 }

        val typePrefix = if (mediaType == MediaType.Manga) "M" else "A"
        if (rawAniListId != null) {
            tagsAndAiringCache["$typePrefix:al:$rawAniListId"]?.let { return it }
        }
        if (safeMalId != null) {
            tagsAndAiringCache["$typePrefix:mal:$safeMalId"]?.let { return it }
        }
        val titleCacheKey = cleanTitle?.lowercase(java.util.Locale.ROOT)?.let { "$typePrefix:q:$it" }
        if (titleCacheKey != null && rawAniListId == null && safeMalId == null) {
            tagsAndAiringCache[titleCacheKey]?.let { return it }
        }

        val typeStr = when (mediaType) {
            MediaType.Manga -> "MANGA"
            else -> "ANIME"
        }

        val (paramDecl, filterExpr, variables) = when {
            rawAniListId != null -> Triple(
                "\$id: Int",
                "id: \$id",
                JSONObject().put("type", typeStr).put("id", rawAniListId)
            )
            safeMalId != null -> Triple(
                "\$idMal: Int",
                "idMal: \$idMal",
                JSONObject().put("type", typeStr).put("idMal", safeMalId)
            )
            cleanTitle != null && (mediaType == MediaType.Anime || mediaType == MediaType.Manga) -> Triple(
                "\$search: String",
                "search: \$search",
                JSONObject().put("type", typeStr).put("search", cleanTitle)
            )
            else -> return null
        }

        val query = """
            query (${'$'}type: MediaType, $paramDecl) {
                Media($filterExpr, type: ${'$'}type) {
                    id
                    idMal
                    title { romaji english native }
                    synonyms
                    genres
                    studios { edges { isMain node { id name } } }
                    tags { id name description category rank isMediaSpoiler isGeneralSpoiler }
                    nextAiringEpisode { episode timeUntilAiring airingAt }
                }
            }
        """.trimIndent()

        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return null
        return runCatching {
            val media = JSONObject(response).optJSONObject("data")?.optJSONObject("Media") ?: return@runCatching null

            // Yalnızca başlıkla arandıysa alakasız bir yapımın etiketlerini yapıştırmamak için başlık uyumu denetlenir.
            if (rawAniListId == null && safeMalId == null && cleanTitle != null) {
                val titleObj = media.optJSONObject("title")
                val candidates = buildList {
                    titleObj?.optNullableString("romaji")?.let { add(it) }
                    titleObj?.optNullableString("english")?.let { add(it) }
                    titleObj?.optNullableString("native")?.let { add(it) }
                    media.optJSONArray("synonyms")?.let { synArr ->
                        for (i in 0 until synArr.length()) {
                            synArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }
                val normQuery = KitsugiSpoilerRules.normalizeLabel(cleanTitle)
                val matches = candidates.any { cand ->
                    val normCand = KitsugiSpoilerRules.normalizeLabel(cand)
                    normCand.isNotEmpty() && (normCand == normQuery || normCand.contains(normQuery) || normQuery.contains(normCand))
                }
                if (!matches) return@runCatching null
            }

            val resolvedAlId = media.optionalPositiveInt("id")
            val resolvedMalId = media.optionalPositiveInt("idMal")
            val tags = parseAniListTags(media.optJSONArray("tags"))
            val nextAiring = media.optJSONObject("nextAiringEpisode")?.let { nextObj ->
                val ep = nextObj.optInt("episode")
                val airingAt = nextObj.optLong("airingAt")
                if (ep > 0 && airingAt > 0L) "$ep|$airingAt" else null
            }

            // Türler ve stüdyolar: diğer kaynaklardan açılan sayfalarda birleştirilir
            // (bkz. KitsugiTaxonomyMerge / KitsugiTaxonomySources).
            val genres = media.optJSONArray("genres")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }.orEmpty()

            val studios = buildList {
                val edges = media.optJSONObject("studios")?.optJSONArray("edges") ?: return@buildList
                for (i in 0 until edges.length()) {
                    val edge = edges.optJSONObject(i) ?: continue
                    val node = edge.optJSONObject("node") ?: continue
                    val name = node.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    add(
                        KitsugiStudio(
                            id = node.optInt("id", 0).takeIf { it > 0 } ?: 0,
                            name = name,
                            isMain = edge.optBoolean("isMain", false),
                            source = "anilist",
                            role = StudioRole.STUDIO
                        )
                    )
                }
            }

            val result = AniListTagsAndAiring(
                aniListId = resolvedAlId,
                malId = resolvedMalId,
                tags = tags,
                nextAiringEpisode = nextAiring,
                genres = genres,
                studios = studios
            )
            if (resolvedAlId != null) tagsAndAiringCache["$typePrefix:al:$resolvedAlId"] = result
            if (resolvedMalId != null) tagsAndAiringCache["$typePrefix:mal:$resolvedMalId"] = result
            if (titleCacheKey != null) tagsAndAiringCache[titleCacheKey] = result
            result
        }.getOrNull()
    }
}
