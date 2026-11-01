package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.kitsugi.animelist.utils.*
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

data class AniListPagedResult(
    val results: List<JikanSearchResult>,
    val hasNextPage: Boolean
)

class AniListSearchClient(
    private val accessToken: String? = null
) {

    suspend fun searchAniList(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean = false,
        status: String? = null,
        format: String? = null,
        formats: List<String>? = null,
        statuses: List<String>? = null,
        season: String? = null,
        seasonYear: Int? = null,
        genres: List<String>? = null,
        excludedGenres: List<String>? = null,
        tags: List<String>? = null,
        minYear: Int? = null,
        maxYear: Int? = null,
        minScore: Int? = null,
        maxScore: Int? = null,
        minEpCh: Int? = null,
        maxEpCh: Int? = null,
        minDuration: Int? = null,
        maxDuration: Int? = null,
        sort: List<String> = listOf("POPULARITY_DESC"),
        country: String? = null,
        sources: List<String>? = null,
        isAdult: Boolean? = null,
        isLicensed: Boolean? = null,
        page: Int = 1,
        perPage: Int = 24
    ): List<JikanSearchResult> {
        return searchAniListPaged(
            query = query,
            mediaType = mediaType,
            showAdultContent = showAdultContent,
            status = status,
            format = format,
            formats = formats,
            statuses = statuses,
            season = season,
            seasonYear = seasonYear,
            genres = genres,
            excludedGenres = excludedGenres,
            tags = tags,
            minYear = minYear,
            maxYear = maxYear,
            minScore = minScore,
            maxScore = maxScore,
            minEpCh = minEpCh,
            maxEpCh = maxEpCh,
            minDuration = minDuration,
            maxDuration = maxDuration,
            sort = sort,
            country = country,
            sources = sources,
            isAdult = isAdult,
            isLicensed = isLicensed,
            page = page,
            perPage = perPage
        ).results
    }

    suspend fun searchAniListPaged(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean = false,
        status: String? = null,
        format: String? = null,
        formats: List<String>? = null,
        statuses: List<String>? = null,
        season: String? = null,
        seasonYear: Int? = null,
        genres: List<String>? = null,
        excludedGenres: List<String>? = null,
        tags: List<String>? = null,
        minYear: Int? = null,
        maxYear: Int? = null,
        minScore: Int? = null,
        maxScore: Int? = null,
        minEpCh: Int? = null,
        maxEpCh: Int? = null,
        minDuration: Int? = null,
        maxDuration: Int? = null,
        sort: List<String> = listOf("POPULARITY_DESC"),
        country: String? = null,
        sources: List<String>? = null,
        isAdult: Boolean? = null,
        isLicensed: Boolean? = null,
        minimumTagRank: Int? = null,
        volumesGreater: Int? = null,
        volumesLesser: Int? = null,
        licensedBy: List<String>? = null,
        page: Int = 1,
        perPage: Int = 24,
        /** Hariç tutulacak AniList etiketleri (`tag_not_in`). */
        excludedTags: List<String>? = null
    ): AniListPagedResult {
        return withContext(Dispatchers.IO) {
            val allFormats = formats ?: format?.let { listOf(it) }
            val allStatuses = statuses ?: status?.let { listOf(it) }

            if (query.isBlank() && allStatuses.isNullOrEmpty() && allFormats.isNullOrEmpty() && season == null &&
                genres.isNullOrEmpty() && excludedGenres.isNullOrEmpty() && tags.isNullOrEmpty() &&
                excludedTags.isNullOrEmpty() &&
                minYear == null && maxYear == null && minScore == null && maxScore == null &&
                country == null && sources.isNullOrEmpty() && minEpCh == null && maxEpCh == null &&
                minDuration == null && maxDuration == null && isAdult == null && isLicensed == null &&
                minimumTagRank == null && volumesGreater == null && volumesLesser == null && licensedBy.isNullOrEmpty()
            ) {
                return@withContext AniListPagedResult(emptyList(), false)
            }
            requestAniListPaged(
                mediaType = mediaType,
                search = query.trim().takeIf { it.isNotBlank() },
                formats = allFormats,
                statuses = allStatuses,
                sort = sort,
                perPage = perPage,
                season = season,
                seasonYear = seasonYear,
                genres = genres,
                excludedGenres = excludedGenres,
                tags = tags,
                excludedTags = excludedTags,
                minYear = minYear,
                maxYear = maxYear,
                minScore = minScore,
                maxScore = maxScore,
                minEpCh = minEpCh,
                maxEpCh = maxEpCh,
                minDuration = minDuration,
                maxDuration = maxDuration,
                showAdultContent = showAdultContent,
                country = country,
                sources = sources,
                isAdult = isAdult,
                isLicensed = isLicensed,
                minimumTagRank = minimumTagRank,
                volumesGreater = volumesGreater,
                volumesLesser = volumesLesser,
                licensedBy = licensedBy,
                page = page
            )
        }
    }

    suspend fun searchCharacters(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
        sort: String = "FAVOURITES_DESC",
        isBirthday: Boolean? = null
    ): AniListPagedResult {
        return withContext(Dispatchers.IO) {
            if (query.isBlank() && isBirthday != true) return@withContext AniListPagedResult(emptyList(), false)
            val effectiveSort = if (query.isNotBlank() && sort == "FAVOURITES_DESC") "SEARCH_MATCH" else sort
            val q = """
                query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [CharacterSort], ${'$'}isBirthday: Boolean) {
                    Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                        pageInfo {
                            hasNextPage
                        }
                        characters(search: ${'$'}search, sort: ${'$'}sort, isBirthday: ${'$'}isBirthday) {
                            id
                            name {
                                userPreferred
                                native
                                full first middle last alternative
                            }
                            image {
                                large
                                medium
                            }
                            favourites
                        }
                    }
                }
            """.trimIndent()
            val vars = JSONObject().put("page", page).put("perPage", perPage)
            if (query.isNotBlank()) vars.put("search", query.trim())
            if (isBirthday == true) vars.put("isBirthday", true)
            vars.put("sort", org.json.JSONArray().put(effectiveSort))

            val resp = KitsugiApiBase.executeAniListQuery(q, vars, accessToken)
                ?: return@withContext AniListPagedResult(emptyList(), false)
            try {
                val root = JSONObject(resp)
                val pageObj = root.optJSONObject("data")?.optJSONObject("Page")
                val charsArray = pageObj?.optJSONArray("characters") ?: return@withContext AniListPagedResult(emptyList(), false)
                val hasNextPage = pageObj.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) ?: (charsArray.length() >= perPage)
                val list = mutableListOf<JikanSearchResult>()
                for (i in 0 until charsArray.length()) {
                    val item = charsArray.optJSONObject(i) ?: continue
                    val id = item.optInt("id", 0)
                    if (id <= 0) continue
                    val nameObj = item.optJSONObject("name")
                    val personName = nameObj.aniListPersonName()
                    val name = personName.preferred
                    val nativeName = personName.native
                    val imgObj = item.optJSONObject("image")
                    val imgUrl = imgObj?.optNullableString("large") ?: imgObj?.optNullableString("medium")
                    val favs = item.optInt("favourites", 0)
                    list.add(
                        JikanSearchResult(
                            malId = id,
                            title = name,
                            titleEnglish = personName.romanized,
                            titleJapanese = personName.native,
                            subtitle = if (!nativeName.isNullOrBlank()) nativeName else "Karakter",
                            type = MediaType.Anime,
                            total = null,
                            score = null,
                            isAdult = false,
                            imageUrl = imgUrl,
                            year = null,
                            source = "character_anilist",
                            favorites = favs
                        )
                    )
                }
                AniListPagedResult(list, hasNextPage)
            } catch (e: Exception) {
                AniListPagedResult(emptyList(), false)
            }
        }
    }

    suspend fun searchStaff(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
        sort: String = "FAVOURITES_DESC",
        isBirthday: Boolean? = null
    ): AniListPagedResult {
        return withContext(Dispatchers.IO) {
            if (query.isBlank() && isBirthday != true) return@withContext AniListPagedResult(emptyList(), false)
            val effectiveSort = if (query.isNotBlank() && sort == "FAVOURITES_DESC") "SEARCH_MATCH" else sort
            val q = """
                query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [StaffSort], ${'$'}isBirthday: Boolean) {
                    Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                        pageInfo {
                            hasNextPage
                        }
                        staff(search: ${'$'}search, sort: ${'$'}sort, isBirthday: ${'$'}isBirthday) {
                            id
                            name {
                                userPreferred
                                native
                                full first middle last alternative
                            }
                            image {
                                large
                                medium
                            }
                            primaryOccupations
                            favourites
                        }
                    }
                }
            """.trimIndent()
            val vars = JSONObject().put("page", page).put("perPage", perPage)
            if (query.isNotBlank()) vars.put("search", query.trim())
            if (isBirthday == true) vars.put("isBirthday", true)
            vars.put("sort", org.json.JSONArray().put(effectiveSort))

            val resp = KitsugiApiBase.executeAniListQuery(q, vars, accessToken)
                ?: return@withContext AniListPagedResult(emptyList(), false)
            try {
                val root = JSONObject(resp)
                val pageObj = root.optJSONObject("data")?.optJSONObject("Page")
                val staffArray = pageObj?.optJSONArray("staff") ?: return@withContext AniListPagedResult(emptyList(), false)
                val hasNextPage = pageObj.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) ?: (staffArray.length() >= perPage)
                val list = mutableListOf<JikanSearchResult>()
                for (i in 0 until staffArray.length()) {
                    val item = staffArray.optJSONObject(i) ?: continue
                    val id = item.optInt("id", 0)
                    if (id <= 0) continue
                    val nameObj = item.optJSONObject("name")
                    val personName = nameObj.aniListPersonName()
                    val name = personName.preferred
                    val nativeName = personName.native
                    val imgObj = item.optJSONObject("image")
                    val imgUrl = imgObj?.optNullableString("large") ?: imgObj?.optNullableString("medium")
                    val favs = item.optInt("favourites", 0)
                    val occupations = item.optJSONArray("primaryOccupations")
                    val occupationText = if (occupations != null && occupations.length() > 0) {
                        (0 until occupations.length()).map { occupations.optString(it) }.take(2).joinToString(", ")
                    } else "Personel / Seslendirmen"
                    list.add(
                        JikanSearchResult(
                            malId = id,
                            title = name,
                            titleEnglish = personName.romanized,
                            titleJapanese = personName.native,
                            subtitle = if (!nativeName.isNullOrBlank()) "$nativeName • $occupationText" else occupationText,
                            type = MediaType.Anime,
                            total = null,
                            score = null,
                            isAdult = false,
                            imageUrl = imgUrl,
                            year = null,
                            source = "staff_anilist",
                            favorites = favs
                        )
                    )
                }
                AniListPagedResult(list, hasNextPage)
            } catch (e: Exception) {
                AniListPagedResult(emptyList(), false)
            }
        }
    }

    suspend fun searchStudios(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
        sort: String = "FAVOURITES_DESC"
    ): AniListPagedResult {
        return withContext(Dispatchers.IO) {
            val q = """
                query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [StudioSort]) {
                    Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                        pageInfo {
                            hasNextPage
                        }
                        studios(search: ${'$'}search, sort: ${'$'}sort) {
                            id
                            name
                            favourites
                            isAnimationStudio
                        }
                    }
                }
            """.trimIndent()
            val vars = JSONObject().put("page", page).put("perPage", perPage)
            if (query.isNotBlank()) vars.put("search", query.trim())
            vars.put("sort", org.json.JSONArray().put(sort))

            val resp = KitsugiApiBase.executeAniListQuery(q, vars, accessToken)
                ?: return@withContext AniListPagedResult(emptyList(), false)
            try {
                val root = JSONObject(resp)
                val pageObj = root.optJSONObject("data")?.optJSONObject("Page")
                val studioArray = pageObj?.optJSONArray("studios") ?: return@withContext AniListPagedResult(emptyList(), false)
                val hasNextPage = pageObj.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) ?: (studioArray.length() >= perPage)
                val list = mutableListOf<JikanSearchResult>()
                for (i in 0 until studioArray.length()) {
                    val item = studioArray.getJSONObject(i)
                    val id = item.getInt("id")
                    val name = item.getString("name")
                    val favs = item.optInt("favourites", 0)
                    val isAnim = item.optBoolean("isAnimationStudio", true)
                    list.add(
                        JikanSearchResult(
                            malId = id,
                            title = name,
                            subtitle = if (isAnim) KitsugiStrings.get(R.string.animasyon_studyosu) else KitsugiStrings.get(R.string.yapimci_studyo),
                            type = MediaType.Anime,
                            total = null,
                            score = null,
                            isAdult = false,
                            imageUrl = null,
                            year = null,
                            source = "anilist",
                            favorites = favs
                        )
                    )
                }
                AniListPagedResult(list, hasNextPage)
            } catch (e: Exception) {
                AniListPagedResult(emptyList(), false)
            }
        }
    }


    suspend fun aniListTopRated(mediaType: MediaType, page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            requestAniList(mediaType = mediaType, search = null, status = null,
                sort = listOf("SCORE_DESC"), perPage = 20, page = page, showAdultContent = showAdultContent)
        }

    suspend fun aniListTopAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Anime,
                search = null,
                status = null,
                sort = listOf("POPULARITY_DESC"),
                perPage = 20,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListAiringAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Anime,
                search = null,
                status = "RELEASING",
                sort = listOf("POPULARITY_DESC"),
                perPage = 20,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListUpcomingAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Anime,
                search = null,
                status = "NOT_YET_RELEASED",
                sort = listOf("POPULARITY_DESC"),
                perPage = 20,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListTopManga(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Manga,
                search = null,
                status = null,
                sort = listOf("POPULARITY_DESC"),
                perPage = 20,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListPublishingManga(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Manga,
                search = null,
                status = "RELEASING",
                sort = listOf("POPULARITY_DESC"),
                perPage = 20,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListTrendingAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Anime,
                search = null,
                status = null,
                sort = listOf("TRENDING_DESC"),
                perPage = 20,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListMovieAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Anime,
                search = null,
                status = null,
                sort = listOf("POPULARITY_DESC"),
                perPage = 20,
                format = "MOVIE",
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListTrendingManga(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Manga,
                search = null,
                status = null,
                sort = listOf("TRENDING_DESC", "POPULARITY_DESC"),
                perPage = 20,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListNewlyAddedAnime(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Anime,
                search = null,
                status = null,
                sort = listOf("ID_DESC"),
                perPage = 20,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListNewlyAddedManga(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Manga,
                search = null,
                status = null,
                sort = listOf("ID_DESC"),
                perPage = 20,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    /**
     * AniList'in yerel "ek tür" rafı: NOVEL formatındaki light novel & romanlar.
     * (Bangumi REAL raflarıyla aynı fikir — kaynağın kendi yerel kategorisi.)
     */
    suspend fun aniListNovels(page: Int = 1, showAdultContent: Boolean = false): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            requestAniList(
                mediaType = MediaType.Manga,
                search = null,
                status = null,
                sort = listOf("POPULARITY_DESC"),
                perPage = 20,
                format = "NOVEL",
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    suspend fun aniListSeasonalAnime(
        page: Int = 1,
        showAdultContent: Boolean = false,
        year: Int? = null,
        season: String? = null,
        sort: List<String> = listOf("POPULARITY_DESC")
    ): List<JikanSearchResult> {
        return withContext(Dispatchers.IO) {
            val (currentSeason, currentYear) = getCurrentSeasonAndYear()
            requestAniList(
                mediaType = MediaType.Anime,
                search = null,
                status = null,
                sort = sort,
                perPage = 20,
                season = season?.uppercase() ?: currentSeason,
                seasonYear = year ?: currentYear,
                page = page,
                showAdultContent = showAdultContent
            )
        }
    }

    internal suspend fun requestAniList(
        mediaType: MediaType,
        search: String?,
        status: String?,
        sort: List<String>,
        perPage: Int,
        format: String? = null,
        season: String? = null,
        seasonYear: Int? = null,
        genres: List<String>? = null,
        excludedGenres: List<String>? = null,
        tags: List<String>? = null,
        minYear: Int? = null,
        maxYear: Int? = null,
        minScore: Int? = null,
        maxScore: Int? = null,
        page: Int = 1,
        showAdultContent: Boolean = false,
        country: String? = null,
        sources: List<String>? = null
    ): List<JikanSearchResult> {
        return requestAniListPaged(
            mediaType = mediaType,
            search = search,
            formats = format?.let { listOf(it) },
            statuses = status?.let { listOf(it) },
            sort = sort,
            perPage = perPage,
            season = season,
            seasonYear = seasonYear,
            genres = genres,
            excludedGenres = excludedGenres,
            tags = tags,
            minYear = minYear,
            maxYear = maxYear,
            minScore = minScore,
            maxScore = maxScore,
            page = page,
            showAdultContent = showAdultContent,
            country = country,
            sources = sources
        ).results
    }

    internal suspend fun requestAniListPaged(
        mediaType: MediaType,
        search: String?,
        formats: List<String>? = null,
        statuses: List<String>? = null,
        sort: List<String>,
        perPage: Int,
        season: String? = null,
        seasonYear: Int? = null,
        genres: List<String>? = null,
        excludedGenres: List<String>? = null,
        tags: List<String>? = null,
        minYear: Int? = null,
        maxYear: Int? = null,
        minScore: Int? = null,
        maxScore: Int? = null,
        minEpCh: Int? = null,
        maxEpCh: Int? = null,
        minDuration: Int? = null,
        maxDuration: Int? = null,
        page: Int = 1,
        showAdultContent: Boolean = false,
        country: String? = null,
        sources: List<String>? = null,
        isAdult: Boolean? = null,
        isLicensed: Boolean? = null,
        minimumTagRank: Int? = null,
        volumesGreater: Int? = null,
        volumesLesser: Int? = null,
        licensedBy: List<String>? = null,
        excludedTags: List<String>? = null
    ): AniListPagedResult {
        val query = """
            query (
                ${'$'}page: Int,
                ${'$'}perPage: Int,
                ${'$'}search: String,
                ${'$'}type: MediaType,
                ${'$'}sort: [MediaSort],
                ${'$'}formatIn: [MediaFormat],
                ${'$'}statusIn: [MediaStatus],
                ${'$'}season: MediaSeason,
                ${'$'}seasonYear: Int,
                ${'$'}genres: [String],
                ${'$'}excludedGenres: [String],
                ${'$'}tags: [String],
                ${'$'}excludedTags: [String],
                ${'$'}startDateGreater: FuzzyDateInt,
                ${'$'}startDateLess: FuzzyDateInt,
                ${'$'}averageScoreGreater: Int,
                ${'$'}averageScoreLess: Int,
                ${'$'}episodesGreater: Int,
                ${'$'}episodesLesser: Int,
                ${'$'}durationGreater: Int,
                ${'$'}durationLesser: Int,
                ${'$'}chaptersGreater: Int,
                ${'$'}chaptersLesser: Int,
                ${'$'}isAdult: Boolean,
                ${'$'}isLicensed: Boolean,
                ${'$'}countryOfOrigin: CountryCode,
                ${'$'}sourceIn: [MediaSource],
                ${'$'}minimumTagRank: Int,
                ${'$'}volumesGreater: Int,
                ${'$'}volumesLesser: Int,
                ${'$'}licensedByIn: [String]
            ) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    pageInfo {
                        hasNextPage
                        total
                        currentPage
                    }
                    media(
                        search: ${'$'}search,
                        type: ${'$'}type,
                        sort: ${'$'}sort,
                        format_in: ${'$'}formatIn,
                        status_in: ${'$'}statusIn,
                        season: ${'$'}season,
                        seasonYear: ${'$'}seasonYear,
                        genre_in: ${'$'}genres,
                        genre_not_in: ${'$'}excludedGenres,
                        tag_in: ${'$'}tags,
                        tag_not_in: ${'$'}excludedTags,
                        startDate_greater: ${'$'}startDateGreater,
                        startDate_lesser: ${'$'}startDateLess,
                        averageScore_greater: ${'$'}averageScoreGreater,
                        averageScore_lesser: ${'$'}averageScoreLess,
                        episodes_greater: ${'$'}episodesGreater,
                        episodes_lesser: ${'$'}episodesLesser,
                        duration_greater: ${'$'}durationGreater,
                        duration_lesser: ${'$'}durationLesser,
                        chapters_greater: ${'$'}chaptersGreater,
                        chapters_lesser: ${'$'}chaptersLesser,
                        isAdult: ${'$'}isAdult,
                        isLicensed: ${'$'}isLicensed,
                        countryOfOrigin: ${'$'}countryOfOrigin,
                        source_in: ${'$'}sourceIn,
                        minimumTagRank: ${'$'}minimumTagRank,
                        volumes_greater: ${'$'}volumesGreater,
                        volumes_lesser: ${'$'}volumesLesser,
                        licensedBy_in: ${'$'}licensedByIn
                    ) {
                        id
                        idMal
                        countryOfOrigin
                        title {
                            romaji
                            english
                            native
                        }
                        format
                        episodes
                        chapters
                        averageScore
                        popularity
                        favourites
                        isAdult
                        genres
                        startDate {
                            year
                        }
                        coverImage {
                            extraLarge
                            large
                        }
                        bannerImage
                        nextAiringEpisode {
                            episode
                            airingAt
                        }
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject()
            .put("page", page)
            .put("perPage", perPage)
            .put(
                "type",
                when (mediaType) {
                    MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "ANIME"
                    MediaType.Manga -> "MANGA"
                }
            )

        if (!sort.isNullOrEmpty()) {
            variables.put("sort", JSONArray(sort))
        }

        if (isAdult != null) {
            variables.put("isAdult", isAdult)
        } else if (!showAdultContent) {
            variables.put("isAdult", false)
        }

        if (isLicensed != null) {
            variables.put("isLicensed", isLicensed)
        }

        if (!search.isNullOrBlank()) {
            variables.put("search", search)
        }

        if (!statuses.isNullOrEmpty()) {
            variables.put("statusIn", JSONArray(statuses))
        }

        if (!formats.isNullOrEmpty()) {
            variables.put("formatIn", JSONArray(formats))
        }

        if (!season.isNullOrBlank()) {
            variables.put("season", season)
        }

        if (seasonYear != null && seasonYear > 0) {
            variables.put("seasonYear", seasonYear)
        }

        if (!genres.isNullOrEmpty()) {
            variables.put("genres", JSONArray(genres))
        }

        if (!excludedGenres.isNullOrEmpty()) {
            variables.put("excludedGenres", JSONArray(excludedGenres))
        }

        if (!tags.isNullOrEmpty()) {
            variables.put("tags", JSONArray(tags))
        }

        if (!excludedTags.isNullOrEmpty()) {
            variables.put("excludedTags", JSONArray(excludedTags))
        }

        if (minYear != null && minYear > 0) {
            variables.put("startDateGreater", minYear * 10000)
        }

        if (maxYear != null && maxYear > 0) {
            variables.put("startDateLess", (maxYear + 1) * 10000 - 1)
        }

        if (minScore != null && minScore > 0) {
            variables.put("averageScoreGreater", minScore)
        }

        if (maxScore != null && maxScore > 0) {
            variables.put("averageScoreLess", maxScore)
        }

        if (mediaType == MediaType.Manga) {
            if (minEpCh != null && minEpCh > 0) {
                variables.put("chaptersGreater", minEpCh - 1)
            }
            if (maxEpCh != null && maxEpCh > 0) {
                variables.put("chaptersLesser", maxEpCh + 1)
            }
        } else {
            if (minEpCh != null && minEpCh > 0) {
                variables.put("episodesGreater", minEpCh - 1)
            }
            if (maxEpCh != null && maxEpCh > 0) {
                variables.put("episodesLesser", maxEpCh + 1)
            }
            if (minDuration != null && minDuration > 0) {
                variables.put("durationGreater", minDuration - 1)
            }
            if (maxDuration != null && maxDuration > 0) {
                variables.put("durationLesser", maxDuration + 1)
            }
        }

        if (!country.isNullOrBlank()) {
            variables.put("countryOfOrigin", country)
        }

        if (!sources.isNullOrEmpty()) {
            variables.put("sourceIn", JSONArray(sources))
        }

        if (minimumTagRank != null && minimumTagRank > 0) {
            variables.put("minimumTagRank", minimumTagRank)
        }

        if (volumesGreater != null && volumesGreater > 0) {
            variables.put("volumesGreater", volumesGreater - 1)
        }

        if (volumesLesser != null && volumesLesser > 0) {
            variables.put("volumesLesser", volumesLesser + 1)
        }

        if (!licensedBy.isNullOrEmpty()) {
            variables.put("licensedByIn", JSONArray(licensedBy))
        }

        val responseText = KitsugiApiBase.executeAniListQuery(
            query = query,
            variables = variables,
            accessToken = accessToken
        ) ?: return AniListPagedResult(emptyList(), false)

        return parseAniListResponsePaged(
            jsonText = responseText,
            mediaType = mediaType,
            requestedPerPage = perPage
        )
    }

    // ── Keşfet toplu raf sorgusu (GraphQL alias) ─────────────────────────────────
    // AniList istekleri 700ms aralıkla sıralanır; 14 keşfet rafını tek tek çekmek
    // ~10-11 sn kuyruk üretiyordu. Alias'lı tek(ler) sorgu ile aynı veri 3 istekte
    // gelir. Bir öbek başarısızsa o öbeğin rafları eski tekil yoldan tamamlanır.

    /** Tek bir keşfet rafının toplu sorgudaki tanımı. */
    data class ExploreShelfSpec(
        val alias: String,
        val mediaType: MediaType,
        val sort: List<String>,
        val status: String? = null,
        val format: String? = null,
        val season: String? = null,
        val seasonYear: Int? = null,
        val countryOfOrigin: String? = null
    )

    private val EXPLORE_BATCH_SIZE = 6

    /**
     * Fetch only the requested shelves, in rate-limit-sized alias batches.
     * A failed batch is not fanned out into one request per shelf: the caller can keep already
     * loaded categories and let the user retry/open a single category when they need it.
     */
    suspend fun aniListExploreShelves(
        shelves: List<ExploreShelfSpec>,
        showAdultContent: Boolean
    ): Map<String, List<JikanSearchResult>> = withContext(Dispatchers.IO) {
        if (shelves.isEmpty()) return@withContext emptyMap()
        val out = linkedMapOf<String, List<JikanSearchResult>>()
        for (chunk in shelves.chunked(EXPLORE_BATCH_SIZE)) {
            try {
                out.putAll(fetchExploreShelfChunk(chunk, showAdultContent))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AniListServiceDownException) {
                if (out.isEmpty()) throw e
                break
            } catch (e: Exception) {
                // Do not retry a batch as several single-shelf requests; that multiplies traffic
                // precisely when the provider is slow or rate-limiting us.
                if (out.isEmpty()) throw e
                break
            }
        }
        out
    }

    /** Bir alias öbeğini tek GraphQL isteğiyle çeker; `data.<alias>.media` başına bir raf. */
    private suspend fun fetchExploreShelfChunk(
        chunk: List<ExploreShelfSpec>,
        showAdultContent: Boolean
    ): Map<String, List<JikanSearchResult>> {
        val query = buildString {
            append("query {\n")
            chunk.forEachIndexed { i, spec ->
                val args = buildList {
                    add("type: ${when (spec.mediaType) {
                        MediaType.Manga -> "MANGA"
                        else -> "ANIME"
                    }}")
                    add("sort: [${spec.sort.joinToString(", ")}]")
                    spec.format?.let { add("format_in: [$it]") }
                    spec.status?.let { add("status_in: [$it]") }
                    spec.season?.let { add("season: $it") }
                    spec.seasonYear?.let { add("seasonYear: $it") }
                    spec.countryOfOrigin?.let { add("countryOfOrigin: $it") }
                    if (!showAdultContent) add("isAdult: false")
                }.joinToString(", ")
                append("  s$i: Page(page: 1, perPage: 20) {\n")
                append("    media($args) {\n")
                append(EXPLORE_MEDIA_FIELDS)
                append("    }\n  }\n")
            }
            append("}")
        }

        val responseText = KitsugiApiBase.executeAniListQuery(
            query = query,
            variables = JSONObject(),
            accessToken = accessToken,
            throwOnServiceDown = true
        ) ?: throw IllegalStateException("AniList boş yanıt")

        val root = JSONObject(responseText)
        root.optJSONArray("errors")?.let { errors ->
            if (errors.length() > 0) throw IllegalStateException(errors.toString())
        }
        val data = root.optJSONObject("data") ?: throw IllegalStateException("AniList data alanı yok")

        val out = HashMap<String, List<JikanSearchResult>>(chunk.size)
        chunk.forEachIndexed { i, spec ->
            val pageObj = data.optJSONObject("s$i")
                ?: throw IllegalStateException("AniList alias s$i eksik")
            // Mevcut liste parser'ı `data.Page` bekler; alias sayfasını aynı şekle bük.
            val synthetic = JSONObject()
                .put("data", JSONObject().put("Page", pageObj))
            out[spec.alias] = parseAniListResponsePaged(
                jsonText = synthetic.toString(),
                mediaType = spec.mediaType,
                requestedPerPage = 20
            ).results
        }
        return out
    }

    private val EXPLORE_MEDIA_FIELDS = """
        id
        idMal
        countryOfOrigin
        title {
            romaji
            english
            native
        }
        format
        episodes
        chapters
        averageScore
        popularity
        favourites
        isAdult
        genres
        startDate {
            year
        }
        coverImage {
            extraLarge
            large
        }
        bannerImage
        nextAiringEpisode {
            episode
            airingAt
        }
    """.trimIndent() + "\n"

    private fun parseAniListResponse(
        jsonText: String,
        mediaType: MediaType
    ): List<JikanSearchResult> = parseAniListResponsePaged(jsonText, mediaType, 24).results

    private fun parseAniListResponsePaged(
        jsonText: String,
        mediaType: MediaType,
        requestedPerPage: Int
    ): AniListPagedResult {
        val root = JSONObject(jsonText)

        val errors = root.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            throw IllegalStateException(errors.toString())
        }

        val pageObj = root.optJSONObject("data")?.optJSONObject("Page")
        val mediaArray = pageObj?.optJSONArray("media") ?: return AniListPagedResult(emptyList(), false)
        val hasNextPage = pageObj.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false)
            ?: (mediaArray.length() >= requestedPerPage)

        val results = mutableListOf<JikanSearchResult>()

        for (index in 0 until mediaArray.length()) {
            val item = mediaArray.optJSONObject(index) ?: continue

            val aniListId = item.optInt("id", 0)
            val idMal = item.optionalPositiveInt("idMal")

            val fallbackId = if (aniListId > 0) {
                100_000_000 + aniListId
            } else {
                0
            }

            val stableId = idMal ?: fallbackId
            if (stableId <= 0) continue

            val countryOfOrigin = item.optNullableString("countryOfOrigin")
            val isNonJapanese = countryOfOrigin != null && !countryOfOrigin.equals("JP", ignoreCase = true)
            val titleObject = item.optJSONObject("title")
            val titleEnglish = titleObject?.optNullableString("english")
            val titleJapanese = titleObject?.optNullableString("native")
            val titleRomaji = titleObject?.optNullableString("romaji")
            val title = if (isNonJapanese && !titleEnglish.isNullOrBlank()) {
                titleEnglish
            } else {
                titleRomaji
                    ?: titleEnglish
                    ?: titleJapanese
                    ?: "Başlıksız"
            }

            val formatRaw = item.optNullableString("format")
            val format = if (formatRaw != null) {
                formatRaw
                    .replace("_", " ")
                    .lowercase()
                    .replaceFirstChar { char -> char.uppercase() }
            } else ""

            val year = item
                .optJSONObject("startDate")
                ?.optionalPositiveInt("year")

            val total = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> item.optionalPositiveInt("episodes")
                MediaType.Manga -> item.optionalPositiveInt("chapters")
            }

            val averageScore = item.optionalPositiveInt("averageScore")
            val score = averageScore
                ?.let { (it / 10.0).toInt().coerceIn(0, 10) }
            val popularity = item.optionalPositiveInt("popularity")
            val favourites = item.optionalPositiveInt("favourites")
            val rawScore = averageScore?.let { it / 10.0 }

            val genres = item.namesFromStringArray("genres")

            val subtitleParts = buildList {
                if (format.isNotBlank()) add(format.toTurkishMediaTypeString())
                if (year != null && year > 0) add(year.toString())
                addAll(genres.take(3).toTurkishGenres())
            }

            val subtitle = if (subtitleParts.isEmpty()) {
                when (mediaType) {
                    MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "AniList ile eklenen anime"
                    MediaType.Manga -> "AniList ile eklenen manga"
                }
            } else {
                subtitleParts.joinToString(", ")
            }

            val imageUrl = item
                .optJSONObject("coverImage")
                ?.let { cover ->
                    cover.optNullableString("extraLarge")
                        ?: cover.optNullableString("large")
                }

            val bannerImage = item.optNullableString("bannerImage")
            val isAdult = item.optBoolean("isAdult", false)

            // nextAiringEpisode — sadece ANIME tipinde dolu olur
            val nextAiringObj = item.optJSONObject("nextAiringEpisode")
            val nextAiringStr = if (nextAiringObj != null) {
                val ep = nextAiringObj.optInt("episode")
                val airingAt = nextAiringObj.optLong("airingAt")
                if (ep > 0 && airingAt > 0) "$ep|$airingAt" else null
            } else null

            if (title.isNotBlank()) {
                results.add(
                    JikanSearchResult(
                        malId = stableId,
                        title = title,
                        subtitle = subtitle,
                        type = mediaType,
                        total = total,
                        score = score,
                        isAdult = isAdult,
                        imageUrl = imageUrl,
                        year = year,
                        source = "anilist",
                        realMalId = idMal,
                        titleEnglish = titleEnglish,
                        titleJapanese = titleJapanese,
                        backdropUrl = bannerImage,
                        members = popularity,
                        favorites = favourites,
                        rawScoreDouble = rawScore,
                        nextAiringEpisode = nextAiringStr,
                        genres = genres
                    )
                )
            }
        }

        return AniListPagedResult(results, hasNextPage)
    }

    private fun getCurrentSeasonAndYear(): Pair<String, Int> {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) // 0-indexed
        val season = when (month) {
            java.util.Calendar.DECEMBER, java.util.Calendar.JANUARY, java.util.Calendar.FEBRUARY -> "WINTER"
            java.util.Calendar.MARCH, java.util.Calendar.APRIL, java.util.Calendar.MAY -> "SPRING"
            java.util.Calendar.JUNE, java.util.Calendar.JULY, java.util.Calendar.AUGUST -> "SUMMER"
            else -> "FALL"
        }
        return Pair(season, year)
    }
}
