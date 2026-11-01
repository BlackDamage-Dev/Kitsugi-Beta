package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType

class JikanApiClient(
    private val aniListToken: String? = null
) {
    private val jikanSearchClient = JikanSearchClient()
    private val aniListSearchClient = AniListSearchClient(accessToken = aniListToken)
    private val detailClient = KitsugiDetailClient()
    private val characterClient = KitsugiCharacterClient()
    private val staffClient = KitsugiStaffClient()
    private val mediaTabsClient = KitsugiMediaTabsClient()
    private val studioClient = KitsugiStudioClient()

    // Bölünmüş yeni istemciler
    private val mediaRelationsClient = KitsugiMediaRelationsClient()
    private val mediaSocialClient = KitsugiMediaSocialClient()
    private val mediaMutationsClient = KitsugiMediaMutationsClient()

    // Search Methods
    suspend fun search(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean = false,
        status: String? = null,
        format: String? = null,
        genreId: Int? = null,
        sort: String? = null,
        orderBy: String? = null
    ) = jikanSearchClient.search(query, mediaType, showAdultContent, status, format, genreId, sort, orderBy)

    suspend fun searchMALOnly(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean = false,
        status: String? = null,
        format: String? = null,
        genreId: Int? = null,
        sort: String? = null,
        orderBy: String? = null
    ) = jikanSearchClient.searchMALOnly(query, mediaType, showAdultContent, status, format, genreId, sort, orderBy)

    suspend fun searchAniList(
        query: String,
        mediaType: MediaType,
        showAdultContent: Boolean = false,
        status: String? = null,
        format: String? = null,
        season: String? = null,
        genres: List<String>? = null,
        excludedGenres: List<String>? = null,
        tags: List<String>? = null,
        minYear: Int? = null,
        maxYear: Int? = null,
        minScore: Int? = null,
        maxScore: Int? = null,
        sort: List<String> = listOf("POPULARITY_DESC"),
        country: String? = null,
        sources: List<String>? = null
    ) = aniListSearchClient.searchAniList(
        query = query,
        mediaType = mediaType,
        showAdultContent = showAdultContent,
        status = status,
        format = format,
        season = season,
        genres = genres,
        excludedGenres = excludedGenres,
        tags = tags,
        minYear = minYear,
        maxYear = maxYear,
        minScore = minScore,
        maxScore = maxScore,
        sort = sort,
        country = country,
        sources = sources
    )

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
        // Aşağıdaki üç filtre daha önce facade'da yoktu: AniList sheet'inde
        // "Minimum etiket güveni" ve "Lisansörler" seçimleri hiçbir isteğe
        // yazılmıyordu (seçim yapılıyor, sonuç değişmiyordu).
        minimumTagRank: Int? = null,
        minVolumes: Int? = null,
        maxVolumes: Int? = null,
        licensedBy: List<String>? = null,
        page: Int = 1,
        perPage: Int = 24
    ) = aniListSearchClient.searchAniListPaged(
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
        minimumTagRank = minimumTagRank,
        volumesGreater = minVolumes,
        volumesLesser = maxVolumes,
        licensedBy = licensedBy,
        page = page,
        perPage = perPage
    )

    suspend fun searchCharacters(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
        sort: String = "FAVOURITES_DESC",
        isBirthday: Boolean? = null
    ) = aniListSearchClient.searchCharacters(query, page, perPage, sort, isBirthday)

    suspend fun searchStaff(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
        sort: String = "FAVOURITES_DESC",
        isBirthday: Boolean? = null
    ) = aniListSearchClient.searchStaff(query, page, perPage, sort, isBirthday)

    suspend fun searchStudios(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
        sort: String = "FAVOURITES_DESC"
    ) = aniListSearchClient.searchStudios(query, page, perPage, sort)

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
         * Keşfet "Tümünü Gör" sayfası kullanır: tür facet'i olmadan yalnız sıralama istendiğinde
         * (başlık/tarih sıraları) resmî MAL sıralama ucu yerine Jikan liste ucunu zorlar.
         */
        applySortWithoutFacets: Boolean = false
    ) = jikanSearchClient.searchMalAdvanced(
        query, mediaType, showAdultContent, status, format, genres, excludedGenres,
        rating, minScore, maxScore, producerId, magazineId, letter, sort, orderBy,
        page, season, seasonYear, startYear, endYear, applySortWithoutFacets
    )

    suspend fun searchMalCharacters(query: String, page: Int = 1, orderBy: String = "favorites", sort: String = "desc", letter: String? = null) =
        jikanSearchClient.searchMalCharacters(query, page, orderBy, sort, letter)

    suspend fun searchMalPeople(query: String, page: Int = 1, orderBy: String = "favorites", sort: String = "desc", letter: String? = null) =
        jikanSearchClient.searchMalPeople(query, page, orderBy, sort, letter)

    suspend fun searchMalProducers(query: String, page: Int = 1, orderBy: String = "favorites", sort: String = "desc", letter: String? = null) =
        jikanSearchClient.searchMalProducers(query, page, orderBy, sort, letter)

    // Top & Filters
    suspend fun topAnime(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.topAnime(page, showAdultContent)
    suspend fun airingAnime(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.airingAnime(page, showAdultContent)
    suspend fun upcomingAnime(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.upcomingAnime(page, showAdultContent)
    suspend fun topManga(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.topManga(page, showAdultContent)
    suspend fun publishingManga(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.publishingManga(page, showAdultContent)
    suspend fun completedManga(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.completedManga(page, showAdultContent)
    suspend fun trendingAnime(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.trendingAnime(page, showAdultContent)
    suspend fun movieAnime(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.movieAnime(page, showAdultContent)
    suspend fun trendingManga(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.trendingManga(page, showAdultContent)
    suspend fun newlyAddedAnime(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.newlyAddedAnime(page, showAdultContent)
    suspend fun newlyAddedManga(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.newlyAddedManga(page, showAdultContent)
    suspend fun manhwaManhua(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.manhwaManhua(page, showAdultContent)
    suspend fun novelsShelf(page: Int = 1, showAdultContent: Boolean = false) = jikanSearchClient.novelsShelf(page, showAdultContent)
    suspend fun seasonalAnime(
        page: Int = 1,
        showAdultContent: Boolean = false,
        year: Int? = null,
        season: String? = null,
        sort: String? = null
    ) = jikanSearchClient.seasonalAnime(page, showAdultContent, year, season, sort)

    // AniList Specific Search Methods
    suspend fun aniListTopRated(mediaType: MediaType, page: Int = 1, showAdultContent: Boolean = false) =
        aniListSearchClient.aniListTopRated(mediaType, page, showAdultContent)

    suspend fun aniListTopAnime(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListTopAnime(page, showAdultContent)
    suspend fun aniListAiringAnime(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListAiringAnime(page, showAdultContent)
    suspend fun aniListUpcomingAnime(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListUpcomingAnime(page, showAdultContent)
    suspend fun aniListTopManga(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListTopManga(page, showAdultContent)
    suspend fun aniListPublishingManga(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListPublishingManga(page, showAdultContent)
    suspend fun aniListTrendingAnime(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListTrendingAnime(page, showAdultContent)
    suspend fun aniListMovieAnime(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListMovieAnime(page, showAdultContent)
    suspend fun aniListTrendingManga(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListTrendingManga(page, showAdultContent)
    suspend fun aniListNewlyAddedAnime(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListNewlyAddedAnime(page, showAdultContent)
    suspend fun aniListNewlyAddedManga(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListNewlyAddedManga(page, showAdultContent)
    suspend fun aniListNovels(page: Int = 1, showAdultContent: Boolean = false) = aniListSearchClient.aniListNovels(page, showAdultContent)

    /** Keşfet raflarını GraphQL alias öbekleriyle toplu çeker (bkz. AniListSearchClient). */
    suspend fun aniListExploreShelves(
        shelves: List<AniListSearchClient.ExploreShelfSpec>,
        showAdultContent: Boolean
    ) = aniListSearchClient.aniListExploreShelves(shelves, showAdultContent)
    suspend fun aniListSeasonalAnime(
        page: Int = 1,
        showAdultContent: Boolean = false,
        year: Int? = null,
        season: String? = null,
        sort: String? = null
    ) = aniListSearchClient.aniListSeasonalAnime(
        page = page,
        showAdultContent = showAdultContent,
        year = year,
        season = season,
        sort = if (sort != null) listOf(sort) else listOf("POPULARITY_DESC")
    )

    // Detail & Synopsis
    suspend fun fetchSynopsis(source: String, externalId: Int?, mediaType: MediaType) = detailClient.fetchSynopsis(source, externalId, mediaType)
    suspend fun fetchDetail(source: String, externalId: Int?, mediaType: MediaType, tmdbId: Int? = null, realMalId: Int? = null, title: String? = null) = detailClient.fetchDetail(source, externalId, mediaType, tmdbId, realMalId, title)

    /**
     * Kademeli detay akışı (detay sayfası için):
     *  - [fetchPrimaryDetail]: kaynak + fallback + önbellek (TMDB zenginleştirmesi YOK)
     *  - [enrichDetail]: TMDB/TR meta + next-airing (zaman tavanlı, arka planda)
     */
    suspend fun fetchPrimaryDetail(source: String, externalId: Int?, mediaType: MediaType, tmdbId: Int? = null, realMalId: Int? = null, title: String? = null) = detailClient.fetchPrimaryDetail(source, externalId, mediaType, tmdbId, realMalId, title)
    suspend fun enrichDetail(source: String, externalId: Int?, mediaType: MediaType, detail: KitsugiMediaDetail, tmdbId: Int? = null, realMalId: Int? = null, title: String? = null): KitsugiMediaDetail? = detailClient.enrichDetail(source, externalId, mediaType, detail, tmdbId, realMalId, title)

    // Characters & Staff
    suspend fun fetchCharacters(source: String, externalId: Int?, mediaType: MediaType, realMalId: Int? = null, tmdbId: Int? = null, title: String? = null) = characterClient.fetchCharacters(source, externalId, mediaType, realMalId, tmdbId, title)
    suspend fun fetchCharacterDetail(
        source: String,
        characterId: Int,
        name: String? = null,
        fallbackImageUrl: String? = null,
        isRealMediaRole: Boolean = false
    ) = characterClient.fetchCharacterDetail(source, characterId, name, fallbackImageUrl, isRealMediaRole)
    suspend fun fetchStaff(source: String, externalId: Int?, mediaType: MediaType, tmdbId: Int? = null, realMalId: Int? = null) = staffClient.fetchStaff(source, externalId, mediaType, tmdbId, realMalId)
    suspend fun fetchStaffDetail(source: String, staffId: Int, name: String? = null) = staffClient.fetchStaffDetail(source, staffId, name)
    suspend fun fetchStudioDetail(source: String, studioId: Int, name: String? = null) = studioClient.fetchStudioDetail(source, studioId, name)
    suspend fun fetchStudioWorksPage(source: String, studioId: Int, page: Int) = studioClient.fetchStudioWorksPage(source, studioId, page)

    // Relations, Stats, Reviews, Episodes
    suspend fun fetchRelations(source: String, externalId: Int?, mediaType: MediaType, tmdbId: Int? = null, realMalId: Int? = null, title: String? = null) = mediaRelationsClient.fetchRelations(source, externalId, mediaType, tmdbId, realMalId, title)
    suspend fun fetchRecommendations(source: String, externalId: Int?, mediaType: MediaType, tmdbId: Int? = null, realMalId: Int? = null, title: String? = null) = mediaRelationsClient.fetchRecommendations(source, externalId, mediaType, tmdbId, realMalId, title)
    suspend fun fetchStats(source: String, externalId: Int?, mediaType: MediaType, realMalId: Int? = null, tmdbId: Int? = null) = mediaSocialClient.fetchStats(source, externalId, mediaType, realMalId, tmdbId)
    suspend fun fetchReviews(source: String, externalId: Int?, mediaType: MediaType, page: Int = 1, tmdbId: Int? = null, realMalId: Int? = null) = mediaSocialClient.fetchReviews(source, externalId, mediaType, page, tmdbId, realMalId)
    suspend fun fetchEpisodes(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        realMalId: Int? = null,
        totalEpisodes: Int? = null,
        context: android.content.Context? = null,
        targetSeason: Int? = null,
        tmdbId: Int? = null
    ) = mediaTabsClient.fetchEpisodes(source, externalId, mediaType, realMalId, totalEpisodes, context, targetSeason, tmdbId)

    suspend fun fetchForumTopics(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        page: Int = 1,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ) = mediaSocialClient.fetchForumTopics(source, externalId, mediaType, page, tmdbId, realMalId)

    suspend fun fetchActivities(
        source: String,
        externalId: Int,
        page: Int = 1,
        mediaType: MediaType = MediaType.Anime,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ) = mediaSocialClient.fetchActivities(source, externalId, page, mediaType, tmdbId, realMalId)

    suspend fun fetchActivityReplies(activityId: Int) =
        mediaSocialClient.fetchActivityReplies(activityId)

    suspend fun fetchForumTopicReplies(topicId: Int, page: Int = 1, source: String = "anilist") =
        mediaSocialClient.fetchForumTopicReplies(topicId, page, source)

    suspend fun enrichForumTopicIfNeeded(topic: KitsugiForumTopic) =
        mediaSocialClient.enrichForumTopicIfNeeded(topic)

    suspend fun toggleLike(id: Int, type: String) =
        mediaMutationsClient.toggleLike(id, type)

    suspend fun postReply(targetId: Int, isActivity: Boolean, text: String, parentCommentId: Int? = null) =
        mediaMutationsClient.postReply(targetId, isActivity, text, parentCommentId)

    suspend fun toggleThreadSubscription(threadId: Int) =
        mediaMutationsClient.toggleThreadSubscription(threadId)

    suspend fun rateReview(reviewId: Int, rating: String) =
        mediaMutationsClient.rateReview(reviewId, rating)

    suspend fun deleteActivity(activityId: Int) =
        mediaMutationsClient.deleteActivity(activityId)
}