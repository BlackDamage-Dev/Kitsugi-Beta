package com.kitsugi.animelist.ui.app

import com.kitsugi.animelist.data.remote.JikanGateway
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.KitsuApiClient.KitsuLibraryEntry
import com.kitsugi.animelist.data.auth.ShikimoriApiClient.ShikimoriRate
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.platform.ListPlatform
import com.kitsugi.animelist.data.platform.PlatformErrorCatalog
import com.kitsugi.animelist.data.platform.PlatformHttpException
import com.kitsugi.animelist.data.remote.optNullableString
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/** Cihaz üzeri MAL sayfaları sırayla (1.5 sn arayla) indirildiği için profil ek verilerine tanınan süre. */
private const val MAL_EXTRA_TIMEOUT_MS = 25_000L

class KitsugiProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val client = com.kitsugi.animelist.core.network.KitsugiHttpClient.client

    // Local library stats state
    var localStats by mutableStateOf(LocalStats())
        private set

    // Selected sub-tab persistence (0: AniList, 1: MAL, 2: Simkl)
    var activeSubTab by mutableStateOf(0)

    // Scroll konumunu observable state'te tutma. Her piksel yazımı kompozisyonu
    // yeniden kurup lazy listeyi initial offset'e geri zıplatıyordu.
    var aniListScrollIndex: Int = 0
    var aniListScrollOffset: Int = 0

    var malScrollIndex: Int = 0
    var malScrollOffset: Int = 0

    var simklScrollIndex: Int = 0
    var simklScrollOffset: Int = 0

    var kitsuScrollIndex: Int = 0
    var kitsuScrollOffset: Int = 0

    var shikimoriScrollIndex: Int = 0
    var shikimoriScrollOffset: Int = 0

    fun updateAniListScroll(index: Int, offset: Int) {
        aniListScrollIndex = index
        aniListScrollOffset = offset
    }

    fun updateMalScroll(index: Int, offset: Int) {
        malScrollIndex = index
        malScrollOffset = offset
    }

    fun updateSimklScroll(index: Int, offset: Int) {
        simklScrollIndex = index
        simklScrollOffset = offset
    }

    fun updateKitsuScroll(index: Int, offset: Int) {
        kitsuScrollIndex = index
        kitsuScrollOffset = offset
    }

    fun updateShikimoriScroll(index: Int, offset: Int) {
        shikimoriScrollIndex = index
        shikimoriScrollOffset = offset
    }

    // AniList Tab & Sub-Filter state persistence
    var aniListActiveTab by mutableIntStateOf(0)
    var aniListStatsMediaType by mutableIntStateOf(0)
    var aniListStatsSubTab by mutableIntStateOf(0)
    var aniListFavoritesFilter by mutableIntStateOf(0)
    var aniListSocialFilter by mutableIntStateOf(0)
    var aniListScoreDistType by mutableIntStateOf(0)
    var aniListLengthDistType by mutableIntStateOf(0)
    var aniListReleaseYearDistType by mutableIntStateOf(0)
    var aniListStartYearDistType by mutableIntStateOf(0)
    var aniListStatsSortType by mutableIntStateOf(0)

    // MAL Tab & Sub-Filter state persistence
    var malActiveTab by mutableIntStateOf(0)
    var malStatsMediaType by mutableIntStateOf(0)
    var malStatsSubTab by mutableIntStateOf(0)
    var malFavoritesFilter by mutableIntStateOf(0)
    var malSocialFilter by mutableIntStateOf(0)

    // Simkl Tab & Sub-Filter state persistence
    var simklActiveTab by mutableIntStateOf(0)

    // Kitsu Tab & Sub-Filter state persistence
    var kitsuActiveTab by mutableIntStateOf(0)

    // Shikimori Tab & Sub-Filter state persistence
    var shikimoriActiveTab by mutableIntStateOf(0)

    // Sekme (HorizontalPager) yükseklik önbellekleri:
    // Profil sekmesinden çıkıp ikinci kez geri dönüldüğünde (veya detay sayfasından geri
    // gelindiğinde) aktif sekmenin yüksekliği ilk kareden itibaren bilinsin; böylece
    // AnimatedContent geçişi sırasında çift measure/layout dalgası oluşmaz.
    val aniListPageHeights = androidx.compose.runtime.mutableStateMapOf<Int, Int>()
    val malPageHeights = androidx.compose.runtime.mutableStateMapOf<Int, Int>()
    val simklPageHeights = androidx.compose.runtime.mutableStateMapOf<Int, Int>()
    val kitsuPageHeights = androidx.compose.runtime.mutableStateMapOf<Int, Int>()
    val shikimoriPageHeights = androidx.compose.runtime.mutableStateMapOf<Int, Int>()

    // AniList profile UI state
    private val _aniListState = MutableStateFlow(AniListProfileState())
    val aniListState: StateFlow<AniListProfileState> = _aniListState.asStateFlow()

    // MyAnimeList profile UI state
    private val _malState = MutableStateFlow(MalProfileState())
    val malState: StateFlow<MalProfileState> = _malState.asStateFlow()

    // Simkl profile UI state
    private val _simklState = MutableStateFlow(SimklProfileState())
    val simklState: StateFlow<SimklProfileState> = _simklState.asStateFlow()

    // Kitsu profile UI state
    private val _kitsuState = MutableStateFlow(KitsuProfileState())
    val kitsuState: StateFlow<KitsuProfileState> = _kitsuState.asStateFlow()

    // Shikimori profile UI state
    private val _shikimoriState = MutableStateFlow(ShikimoriProfileState())
    val shikimoriState: StateFlow<ShikimoriProfileState> = _shikimoriState.asStateFlow()

    private val _bangumiState = MutableStateFlow(BangumiProfileState())
    val bangumiState: StateFlow<BangumiProfileState> = _bangumiState.asStateFlow()

    fun initLocalStats(entries: List<MediaEntry>) {
        viewModelScope.launch {
            val anime = entries.filter { it.type == MediaType.Anime || it.type == MediaType.Movie || it.type == MediaType.TvShow }
            val manga = entries.filter { it.type == MediaType.Manga }

            val animeScores = anime.mapNotNull { it.score }
            val avgAnimeScore = if (animeScores.isNotEmpty()) animeScores.average() else 0.0

            val mangaScores = manga.mapNotNull { it.score }
            val avgMangaScore = if (mangaScores.isNotEmpty()) mangaScores.average() else 0.0

            localStats = LocalStats(
                totalAnime = anime.size,
                watchingAnime = anime.count { it.status == WatchStatus.Watching },
                completedAnime = anime.count { it.status == WatchStatus.Completed },
                plannedAnime = anime.count { it.status == WatchStatus.Planned },
                pausedAnime = anime.count { it.status == WatchStatus.Paused },
                droppedAnime = anime.count { it.status == WatchStatus.Dropped },
                avgAnimeScore = avgAnimeScore,

                totalManga = manga.size,
                readingManga = manga.count { it.status == WatchStatus.Watching },
                completedManga = manga.count { it.status == WatchStatus.Completed },
                plannedManga = manga.count { it.status == WatchStatus.Planned },
                pausedManga = manga.count { it.status == WatchStatus.Paused },
                droppedManga = manga.count { it.status == WatchStatus.Dropped },
                avgMangaScore = avgMangaScore
            )
        }
    }

    fun refreshActiveProfile() {
        when (activeSubTab) {
            0 -> fetchAniListProfile(forceRefresh = true)
            1 -> fetchMalProfile(forceRefresh = true)
            2 -> fetchSimklProfile(forceRefresh = true)
            3 -> fetchKitsuProfile(forceRefresh = true)
            4 -> fetchShikimoriProfile(forceRefresh = true)
            5 -> fetchBangumiProfile(forceRefresh = true)
        }
    }

    // --- ANILIST API FETCHING ---
    fun fetchAniListProfile(forceRefresh: Boolean = false) {
        if (_aniListState.value.isLoading) return
        if (!forceRefresh && _aniListState.value.userId != null) {
            return
        }

        val token = ExternalAuthManager.getAniListToken(context)
        if (token.isNullOrBlank()) {
            _aniListState.update { it.copy(isConnected = false, isLoading = false, error = KitsugiStrings.get(R.string.profile_connection_not_found, "AniList")) }
            return
        }

        _aniListState.update { it.copy(isConnected = true, isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val profileQuery = """
                    query {
                        Viewer {
                            id
                            name
                            about(asHtml: true)
                            avatar {
                                large
                            }
                            bannerImage
                            donatorBadge
                            donatorTier
                            statistics {
                                anime {
                                    count
                                    episodesWatched
                                    minutesWatched
                                    meanScore
                                    standardDeviation
                                    scores(sort: MEAN_SCORE) {
                                        count
                                        minutesWatched
                                        meanScore
                                        score
                                    }
                                    lengths {
                                        length
                                        count
                                        minutesWatched
                                        meanScore
                                    }
                                    formats {
                                        count
                                        minutesWatched
                                        meanScore
                                        format
                                    }
                                    statuses {
                                        count
                                        minutesWatched
                                        meanScore
                                        status
                                    }
                                    countries {
                                        count
                                        minutesWatched
                                        meanScore
                                        country
                                    }
                                    releaseYears {
                                        count
                                        minutesWatched
                                        meanScore
                                        releaseYear
                                    }
                                    startYears {
                                        count
                                        minutesWatched
                                        meanScore
                                        startYear
                                    }
                                    genres(sort: COUNT_DESC) {
                                        count
                                        minutesWatched
                                        meanScore
                                        genre
                                    }
                                    tags(sort: COUNT_DESC, limit: 30) {
                                        count
                                        minutesWatched
                                        meanScore
                                        tag {
                                            id
                                            name
                                        }
                                    }
                                    staff(sort: COUNT_DESC, limit: 30) {
                                        count
                                        minutesWatched
                                        meanScore
                                        staff {
                                            id
                                            name {
                                                full
                                            }
                                            image {
                                                large
                                            }
                                        }
                                    }
                                    voiceActors(sort: COUNT_DESC, limit: 30) {
                                        count
                                        minutesWatched
                                        meanScore
                                        voiceActor {
                                            id
                                            name {
                                                full
                                            }
                                            image {
                                                large
                                            }
                                        }
                                    }
                                    studios(sort: COUNT_DESC, limit: 30) {
                                        count
                                        minutesWatched
                                        meanScore
                                        studio {
                                            id
                                            name
                                        }
                                    }
                                }
                                manga {
                                    count
                                    chaptersRead
                                    volumesRead
                                    meanScore
                                    standardDeviation
                                    scores(sort: MEAN_SCORE) {
                                        count
                                        chaptersRead
                                        meanScore
                                        score
                                    }
                                    lengths {
                                        length
                                        count
                                        chaptersRead
                                        meanScore
                                    }
                                    formats {
                                        count
                                        chaptersRead
                                        meanScore
                                        format
                                    }
                                    statuses {
                                        count
                                        chaptersRead
                                        meanScore
                                        status
                                    }
                                    countries {
                                        count
                                        chaptersRead
                                        meanScore
                                        country
                                    }
                                    releaseYears {
                                        count
                                        chaptersRead
                                        meanScore
                                        releaseYear
                                    }
                                    startYears {
                                        count
                                        chaptersRead
                                        meanScore
                                        startYear
                                    }
                                    genres(sort: COUNT_DESC) {
                                        count
                                        chaptersRead
                                        meanScore
                                        genre
                                    }
                                    tags(sort: COUNT_DESC, limit: 30) {
                                        count
                                        chaptersRead
                                        meanScore
                                        tag {
                                            id
                                            name
                                        }
                                    }
                                    staff(sort: COUNT_DESC, limit: 30) {
                                        count
                                        chaptersRead
                                        meanScore
                                        staff {
                                            id
                                            name {
                                                full
                                            }
                                            image {
                                                large
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()

                val jsonResponse = postGraphQl(token, profileQuery, JSONObject())
                val viewerJson = JSONObject(jsonResponse)
                    .getJSONObject("data")
                    .getJSONObject("Viewer")

                val id = viewerJson.getInt("id")
                val name = viewerJson.optNullableString("name") ?: KitsugiStrings.get(R.string.ui_profile_anilist_fallback)
                val avatar = viewerJson.optJSONObject("avatar")?.optNullableString("large")
                val banner = viewerJson.optNullableString("bannerImage")
                val about = viewerJson.optNullableString("about") ?: ""
                val donatorBadge = viewerJson.optNullableString("donatorBadge")
                val donatorTier = viewerJson.optInt("donatorTier", 0)

                // Parse stats
                val statisticsObj = viewerJson.optJSONObject("statistics")
                val aniAnimeStats = statisticsObj?.optJSONObject("anime")
                val aniMangaStats = statisticsObj?.optJSONObject("manga")

                val animeOverviewStats = parseDetailedOverviewStats(aniAnimeStats, isAnime = true)
                val mangaOverviewStats = parseDetailedOverviewStats(aniMangaStats, isAnime = false)

                val animeStats = if (aniAnimeStats != null) {
                    val statusesArray = aniAnimeStats.optJSONArray("statuses")
                    val statusesMap = mutableMapOf<String, Int>()
                    if (statusesArray != null) {
                        for (i in 0 until statusesArray.length()) {
                            val obj = statusesArray.getJSONObject(i)
                            statusesMap[obj.getString("status")] = obj.getInt("count")
                        }
                    }
                    AniListStats(
                        count = aniAnimeStats.optInt("count", 0),
                        episodesWatched = aniAnimeStats.optInt("episodesWatched", 0),
                        minutesWatched = aniAnimeStats.optInt("minutesWatched", 0),
                        meanScore = aniAnimeStats.optDouble("meanScore", 0.0),
                        watching = statusesMap["CURRENT"] ?: 0,
                        completed = statusesMap["COMPLETED"] ?: 0,
                        planned = statusesMap["PLANNING"] ?: 0,
                        paused = statusesMap["PAUSED"] ?: 0,
                        dropped = statusesMap["DROPPED"] ?: 0
                    )
                } else null

                val mangaStats = if (aniMangaStats != null) {
                    val statusesArray = aniMangaStats.optJSONArray("statuses")
                    val statusesMap = mutableMapOf<String, Int>()
                    if (statusesArray != null) {
                        for (i in 0 until statusesArray.length()) {
                            val obj = statusesArray.getJSONObject(i)
                            statusesMap[obj.getString("status")] = obj.getInt("count")
                        }
                    }
                    AniListStats(
                        count = aniMangaStats.optInt("count", 0),
                        episodesWatched = aniMangaStats.optInt("chaptersRead", 0),
                        minutesWatched = aniMangaStats.optInt("volumesRead", 0),
                        meanScore = aniMangaStats.optDouble("meanScore", 0.0),
                        watching = statusesMap["CURRENT"] ?: 0,
                        completed = statusesMap["COMPLETED"] ?: 0,
                        planned = statusesMap["PLANNING"] ?: 0,
                        paused = statusesMap["PAUSED"] ?: 0,
                        dropped = statusesMap["DROPPED"] ?: 0
                    )
                } else null

                // Favorileri ayrı paginated fetch ile çek (AniHyou gibi)
                val (favAnimeList, favAnimeHasNext) = fetchAniListFavoritesPaginated(token, id, "anime", 1)
                val (favMangaList, favMangaHasNext) = fetchAniListFavoritesPaginated(token, id, "manga", 1)
                val (favCharList, favCharHasNext) = fetchAniListFavoritesPaginated(token, id, "characters", 1)
                val (favStaffList, favStaffHasNext) = fetchAniListFavoritesPaginated(token, id, "staff", 1)
                val (favStudioList, favStudioHasNext) = fetchAniListFavoritesPaginated(token, id, "studios", 1)

                _aniListState.update {
                    it.copy(
                        isLoading = false,
                        userId = id,
                        name = name,
                        avatarUrl = avatar,
                        bannerUrl = banner,
                        about = about,
                        donatorBadge = donatorBadge,
                        donatorTier = donatorTier,
                        animeStats = animeStats,
                        mangaStats = mangaStats,
                        animeOverviewStats = animeOverviewStats,
                        mangaOverviewStats = mangaOverviewStats,
                        favoriteAnime = favAnimeList,
                        favoriteManga = favMangaList,
                        favoriteCharacters = favCharList,
                        favoriteStaff = favStaffList,
                        favoriteStudios = favStudioList,
                        favAnimePage = 1, favAnimeHasNext = favAnimeHasNext,
                        favMangaPage = 1, favMangaHasNext = favMangaHasNext,
                        favCharPage = 1, favCharHasNext = favCharHasNext,
                        favStaffPage = 1, favStaffHasNext = favStaffHasNext,
                        favStudioPage = 1, favStudioHasNext = favStudioHasNext
                    )
                }

                // Fetch activities & social
                fetchAniListActivities(token, id, 1)
                fetchAniListSocial(token, id)

            } catch (e: Exception) {
                val errorCode = PlatformErrorCatalog.classify(platform = ListPlatform.ANILIST, error = e)
                val userMessage = PlatformErrorCatalog.conciseBilingual(context, errorCode)
                _aniListState.update { it.copy(isLoading = false, error = userMessage, errorCode = errorCode) }
            }
        }
    }

    private suspend fun fetchAniListActivities(token: String, userId: Int, page: Int) {
        withContext(Dispatchers.IO) {
            try {
                val activityQuery = """
                    query (${'$'}userId: Int, ${'$'}page: Int) {
                        Page(page: ${'$'}page, perPage: 25) {
                            pageInfo {
                                hasNextPage
                            }
                            activities(userId: ${'$'}userId, sort: ID_DESC) {
                                __typename
                                ... on TextActivity {
                                    id
                                    text
                                    createdAt
                                    likeCount
                                    replyCount
                                    isLiked
                                    user {
                                        id
                                        name
                                        avatar {
                                            large
                                        }
                                    }
                                }
                                ... on ListActivity {
                                    id
                                    status
                                    progress
                                    createdAt
                                    likeCount
                                    replyCount
                                    isLiked
                                    user {
                                        id
                                        name
                                        avatar {
                                            large
                                        }
                                    }
                                    media {
                                        id
                                        type
                                        isAdult
                                        title {
                                            romaji
                                            english
                                        }
                                        coverImage {
                                            large
                                        }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()

                val variables = JSONObject()
                    .put("userId", userId)
                    .put("page", page)

                val jsonResponse = postGraphQl(token, activityQuery, variables)
                val pageObj = JSONObject(jsonResponse)
                    .getJSONObject("data")
                    .getJSONObject("Page")

                val hasNext = pageObj.getJSONObject("pageInfo").getBoolean("hasNextPage")
                val activityArray = pageObj.getJSONArray("activities")
                val list = mutableListOf<ProfileActivityItem>()

                for (i in 0 until activityArray.length()) {
                    val act = activityArray.getJSONObject(i)
                    val typename = act.optString("__typename")
                    val user = act.optJSONObject("user")
                    val actUserId = user?.optInt("id")
                    val userName = user?.optNullableString("name") ?: KitsugiStrings.get(R.string.notification_unknown_user)
                    val userAvatar = user?.optJSONObject("avatar")?.optNullableString("large")
                    val createdAt = act.optLong("createdAt", 0L)
                    val likeCount = act.optInt("likeCount", 0)
                    val replyCount = act.optInt("replyCount", 0)
                    val isLiked = act.optBoolean("isLiked", false)

                    if (typename == "TextActivity") {
                        list.add(
                            ProfileActivityItem.TextActivity(
                                id = act.getInt("id").toString(),
                                userId = actUserId,
                                userName = userName,
                                userAvatar = userAvatar,
                                text = act.optNullableString("text") ?: "",
                                createdAt = createdAt,
                                likeCount = likeCount,
                                isLiked = isLiked,
                                replyCount = replyCount
                            )
                        )
                    } else if (typename == "ListActivity") {
                        val media = act.optJSONObject("media")
                        val mediaId = media?.optInt("id")
                        val mediaType = media?.optNullableString("type")
                        val isAdult = media?.optBoolean("isAdult", false) ?: false
                        val mediaTitle = media?.optJSONObject("title")?.optNullableString("romaji") ?: media?.optJSONObject("title")?.optNullableString("english") ?: KitsugiStrings.get(R.string.profile_unknown_media)
                        val mediaImage = media?.optJSONObject("coverImage")?.optNullableString("large")
                        val status = act.optNullableString("status") ?: "updated"
                        val progress = act.optNullableString("progress")

                        list.add(
                            ProfileActivityItem.ListActivity(
                                id = act.getInt("id").toString(),
                                userId = actUserId,
                                userName = userName,
                                userAvatar = userAvatar,
                                mediaTitle = mediaTitle,
                                mediaImage = mediaImage,
                                status = status,
                                progress = progress,
                                createdAt = createdAt,
                                likeCount = likeCount,
                                isLiked = isLiked,
                                replyCount = replyCount,
                                mediaId = mediaId,
                                mediaType = mediaType,
                                isAdult = isAdult
                            )
                        )
                    }
                }

                _aniListState.update {
                    it.copy(
                        activities = if (page == 1) list else it.activities + list,
                        activitiesHasNext = hasNext,
                        activitiesPage = page
                    )
                }
            } catch (e: Exception) {
                // Fail silently for activity feed, but log
                android.util.Log.e("ProfileViewModel", "AniList activity fetch failed: ${e.message}")
            }
        }
    }

    fun loadNextAniListActivitiesPage() {
        val token = ExternalAuthManager.getAniListToken(context)
        val state = _aniListState.value
        if (token.isNullOrBlank() || state.userId == null || !state.activitiesHasNext) return

        viewModelScope.launch {
            fetchAniListActivities(token, state.userId, state.activitiesPage + 1)
        }
    }

    private suspend fun fetchAniListSocial(token: String, userId: Int) {
        withContext(Dispatchers.IO) {
            try {
                // Doğru AniList GraphQL şeması: User altında followers/following yok.
                // AniHyou referansına göre Page.followers(userId:) ve Page.following(userId:) kullanılmalı.
                val followersQuery = """
                    query (${'$'}userId: Int!) {
                        Page(page: 1, perPage: 100) {
                            followers(userId: ${'$'}userId) {
                                id
                                name
                                avatar { large }
                            }
                            pageInfo { hasNextPage }
                        }
                    }
                """.trimIndent()

                val followingQuery = """
                    query (${'$'}userId: Int!) {
                        Page(page: 1, perPage: 100) {
                            following(userId: ${'$'}userId) {
                                id
                                name
                                avatar { large }
                            }
                            pageInfo { hasNextPage }
                        }
                    }
                """.trimIndent()

                val variables = JSONObject().put("userId", userId)

                val followersList = mutableListOf<UserFollowItem>()
                try {
                    val fResponse = postGraphQl(token, followersQuery, variables)
                    val fPage = JSONObject(fResponse).getJSONObject("data").getJSONObject("Page")
                    fPage.optJSONArray("followers")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            followersList.add(
                                UserFollowItem(
                                    id = obj.getInt("id"),
                                    name = obj.optNullableString("name") ?: KitsugiStrings.get(R.string.kullanici),
                                    avatarUrl = obj.optJSONObject("avatar")?.optNullableString("large")
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileViewModel", "followers fetch failed: ${e.message}")
                }

                val followingList = mutableListOf<UserFollowItem>()
                try {
                    val fgResponse = postGraphQl(token, followingQuery, variables)
                    val fgPage = JSONObject(fgResponse).getJSONObject("data").getJSONObject("Page")
                    fgPage.optJSONArray("following")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            followingList.add(
                                UserFollowItem(
                                    id = obj.getInt("id"),
                                    name = obj.optNullableString("name") ?: KitsugiStrings.get(R.string.kullanici),
                                    avatarUrl = obj.optJSONObject("avatar")?.optNullableString("large")
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileViewModel", "following fetch failed: ${e.message}")
                }

                _aniListState.update {
                    it.copy(socialState = SocialState(followers = followersList, following = followingList))
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "AniList social fetch failed: ${e.message}")
            }
        }
    }

    // AniHyou gibi favorileri kategoriye göre ayrı ve paginated çek
    private suspend fun fetchAniListFavoritesPaginated(token: String, userId: Int, category: String, page: Int = 1): Pair<List<ProfileFavoriteItem>, Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val fieldBlock = when (category) {
                    "anime" -> """
                        anime(page: ${'$'}page, perPage: 25) {
                            nodes { id title { romaji english } coverImage { large } isAdult }
                            pageInfo { hasNextPage }
                        }"""
                    "manga" -> """
                        manga(page: ${'$'}page, perPage: 25) {
                            nodes { id title { romaji english } coverImage { large } isAdult }
                            pageInfo { hasNextPage }
                        }"""
                    "characters" -> """
                        characters(page: ${'$'}page, perPage: 25) {
                            nodes { id name { full } image { large } }
                            pageInfo { hasNextPage }
                        }"""
                    "staff" -> """
                        staff(page: ${'$'}page, perPage: 25) {
                            nodes { id name { full } image { large } }
                            pageInfo { hasNextPage }
                        }"""
                    "studios" -> """
                        studios(page: ${'$'}page, perPage: 25) {
                            nodes { id name }
                            pageInfo { hasNextPage }
                        }"""
                    else -> return@withContext Pair(emptyList(), false)
                }

                val query = """
                    query (${'$'}userId: Int, ${'$'}page: Int) {
                        User(id: ${'$'}userId) {
                            favourites {
                                $fieldBlock
                            }
                        }
                    }
                """.trimIndent()

                val variables = JSONObject().put("userId", userId).put("page", page)
                val response = postGraphQl(token, query, variables)
                val favObj = JSONObject(response).getJSONObject("data").getJSONObject("User").getJSONObject("favourites")
                val catObj = favObj.getJSONObject(category)
                val hasNext = catObj.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) ?: false
                val nodes = catObj.optJSONArray("nodes")
                val list = mutableListOf<ProfileFavoriteItem>()
                if (nodes != null) {
                    for (i in 0 until nodes.length()) {
                        val node = nodes.getJSONObject(i)
                        val item = when (category) {
                            "anime", "manga" -> ProfileFavoriteItem(
                                id = node.getInt("id").toString(),
                                title = node.optJSONObject("title")?.let {
                                    it.optNullableString("romaji") ?: it.optNullableString("english")
                                } ?: KitsugiStrings.get(R.string.isimsiz),
                                imageUrl = node.optJSONObject("coverImage")?.optNullableString("large") ?: "",
                                isAdult = node.optBoolean("isAdult", false)
                            )
                            "characters", "staff" -> ProfileFavoriteItem(
                                id = node.getInt("id").toString(),
                                title = node.optJSONObject("name")?.optNullableString("full") ?: KitsugiStrings.get(R.string.isimsiz),
                                imageUrl = node.optJSONObject("image")?.optNullableString("large") ?: ""
                            )
                            "studios" -> ProfileFavoriteItem(
                                id = node.getInt("id").toString(),
                                title = node.optString("name", KitsugiStrings.get(R.string.isimsiz)),
                                imageUrl = ""
                            )
                            else -> null
                        }
                        if (item != null) list.add(item)
                    }
                }
                Pair(list, hasNext)
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "favorites ($category) fetch failed: ${e.message}")
                Pair(emptyList(), false)
            }
        }
    }

    fun loadMoreFavorites(category: String) {
        val token = ExternalAuthManager.getAniListToken(context) ?: return
        val state = _aniListState.value
        val userId = state.userId ?: return

        viewModelScope.launch {
            val currentPage = when (category) {
                "anime" -> state.favAnimePage
                "manga" -> state.favMangaPage
                "characters" -> state.favCharPage
                "staff" -> state.favStaffPage
                "studios" -> state.favStudioPage
                else -> 1
            }
            val hasNext = when (category) {
                "anime" -> state.favAnimeHasNext
                "manga" -> state.favMangaHasNext
                "characters" -> state.favCharHasNext
                "staff" -> state.favStaffHasNext
                "studios" -> state.favStudioHasNext
                else -> false
            }
            if (!hasNext) return@launch

            val (newItems, nextHasNext) = fetchAniListFavoritesPaginated(token, userId, category, currentPage + 1)
            _aniListState.update { s ->
                when (category) {
                    "anime" -> s.copy(favoriteAnime = s.favoriteAnime + newItems, favAnimePage = currentPage + 1, favAnimeHasNext = nextHasNext)
                    "manga" -> s.copy(favoriteManga = s.favoriteManga + newItems, favMangaPage = currentPage + 1, favMangaHasNext = nextHasNext)
                    "characters" -> s.copy(favoriteCharacters = s.favoriteCharacters + newItems, favCharPage = currentPage + 1, favCharHasNext = nextHasNext)
                    "staff" -> s.copy(favoriteStaff = s.favoriteStaff + newItems, favStaffPage = currentPage + 1, favStaffHasNext = nextHasNext)
                    "studios" -> s.copy(favoriteStudios = s.favoriteStudios + newItems, favStudioPage = currentPage + 1, favStudioHasNext = nextHasNext)
                    else -> s
                }
            }
        }
    }

    fun parseDetailedOverviewStats(json: JSONObject?, isAnime: Boolean): DetailedUserOverviewStats {
        if (json == null) return DetailedUserOverviewStats()
        val count = json.optInt("count", 0)
        val epOrChap = if (isAnime) json.optInt("episodesWatched", 0) else json.optInt("chaptersRead", 0)
        val minutesOrVol = if (isAnime) json.optInt("minutesWatched", 0) else json.optInt("volumesRead", 0)
        val daysWatched = if (isAnime) (minutesOrVol / 60.0 / 24.0) else minutesOrVol.toDouble()
        val meanScore = json.optDouble("meanScore", 0.0)
        val stdDev = json.optDouble("standardDeviation", 0.0)

        val scoreList = mutableListOf<ScoreStatItem>()
        json.optJSONArray("scores")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                scoreList.add(
                    ScoreStatItem(
                        score = item.optInt("score", 0),
                        count = item.optInt("count", 0),
                        minutesWatched = item.optInt(if (isAnime) "minutesWatched" else "chaptersRead", 0),
                        meanScore = item.optDouble("meanScore", 0.0)
                    )
                )
            }
        }

        val lengthList = mutableListOf<LengthStatItem>()
        json.optJSONArray("lengths")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                lengthList.add(
                    LengthStatItem(
                        length = item.optString("length", KitsugiStrings.get(R.string.profile_status_unknown)),
                        count = item.optInt("count", 0),
                        minutesWatched = item.optInt(if (isAnime) "minutesWatched" else "chaptersRead", 0),
                        meanScore = item.optDouble("meanScore", 0.0)
                    )
                )
            }
        }

        val formatList = mutableListOf<FormatStatItem>()
        json.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                formatList.add(
                    FormatStatItem(
                        format = item.optString("format", KitsugiStrings.get(R.string.bangumi_tag_other)),
                        count = item.optInt("count", 0),
                        minutesWatched = item.optInt(if (isAnime) "minutesWatched" else "chaptersRead", 0),
                        meanScore = item.optDouble("meanScore", 0.0)
                    )
                )
            }
        }

        val statusList = mutableListOf<StatusStatItem>()
        json.optJSONArray("statuses")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                statusList.add(
                    StatusStatItem(
                        status = item.optString("status", KitsugiStrings.get(R.string.profile_status_unknown)),
                        count = item.optInt("count", 0),
                        minutesWatched = item.optInt(if (isAnime) "minutesWatched" else "chaptersRead", 0),
                        meanScore = item.optDouble("meanScore", 0.0)
                    )
                )
            }
        }

        val countryList = mutableListOf<CountryStatItem>()
        json.optJSONArray("countries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                countryList.add(
                    CountryStatItem(
                        country = item.optString("country", KitsugiStrings.get(R.string.bangumi_tag_other)),
                        count = item.optInt("count", 0),
                        minutesWatched = item.optInt(if (isAnime) "minutesWatched" else "chaptersRead", 0),
                        meanScore = item.optDouble("meanScore", 0.0)
                    )
                )
            }
        }

        val releaseYearList = mutableListOf<ReleaseYearStatItem>()
        json.optJSONArray("releaseYears")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                releaseYearList.add(
                    ReleaseYearStatItem(
                        releaseYear = item.optInt("releaseYear", 0),
                        count = item.optInt("count", 0),
                        minutesWatched = item.optInt(if (isAnime) "minutesWatched" else "chaptersRead", 0),
                        meanScore = item.optDouble("meanScore", 0.0)
                    )
                )
            }
        }

        val startYearList = mutableListOf<StartYearStatItem>()
        json.optJSONArray("startYears")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                startYearList.add(
                    StartYearStatItem(
                        startYear = item.optInt("startYear", 0),
                        count = item.optInt("count", 0),
                        minutesWatched = item.optInt(if (isAnime) "minutesWatched" else "chaptersRead", 0),
                        meanScore = item.optDouble("meanScore", 0.0)
                    )
                )
            }
        }

        val genreList = mutableListOf<RankedStatItem>()
        json.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                genreList.add(
                    RankedStatItem(
                        name = item.optString("genre", KitsugiStrings.get(R.string.profile_status_unknown)),
                        count = item.optInt("count", 0),
                        meanScore = item.optDouble("meanScore", 0.0),
                        timeSpentMinutes = if (isAnime) item.optInt("minutesWatched", 0) else null,
                        chaptersRead = if (!isAnime) item.optInt("chaptersRead", 0) else null
                    )
                )
            }
        }

        val tagList = mutableListOf<RankedStatItem>()
        json.optJSONArray("tags")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val tagObj = item.optJSONObject("tag")
                tagList.add(
                    RankedStatItem(
                        name = tagObj?.optString("name") ?: item.optString("tag", KitsugiStrings.get(R.string.profile_status_unknown)),
                        count = item.optInt("count", 0),
                        meanScore = item.optDouble("meanScore", 0.0),
                        timeSpentMinutes = if (isAnime) item.optInt("minutesWatched", 0) else null,
                        chaptersRead = if (!isAnime) item.optInt("chaptersRead", 0) else null
                    )
                )
            }
        }

        val staffList = mutableListOf<RankedStatItem>()
        json.optJSONArray("staff")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val staffObj = item.optJSONObject("staff")
                staffList.add(
                    RankedStatItem(
                        id = staffObj?.optInt("id"),
                        name = staffObj?.optJSONObject("name")?.optString("full") ?: KitsugiStrings.get(R.string.label_unknown),
                        count = item.optInt("count", 0),
                        meanScore = item.optDouble("meanScore", 0.0),
                        timeSpentMinutes = if (isAnime) item.optInt("minutesWatched", 0) else null,
                        chaptersRead = if (!isAnime) item.optInt("chaptersRead", 0) else null,
                        imageUrl = staffObj?.optJSONObject("image")?.optString("large")
                    )
                )
            }
        }

        val voiceActorList = mutableListOf<RankedStatItem>()
        json.optJSONArray("voiceActors")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val vaObj = item.optJSONObject("voiceActor")
                voiceActorList.add(
                    RankedStatItem(
                        id = vaObj?.optInt("id"),
                        name = vaObj?.optJSONObject("name")?.optString("full") ?: KitsugiStrings.get(R.string.label_unknown),
                        count = item.optInt("count", 0),
                        meanScore = item.optDouble("meanScore", 0.0),
                        timeSpentMinutes = if (isAnime) item.optInt("minutesWatched", 0) else null,
                        chaptersRead = if (!isAnime) item.optInt("chaptersRead", 0) else null,
                        imageUrl = vaObj?.optJSONObject("image")?.optString("large")
                    )
                )
            }
        }

        val studioList = mutableListOf<RankedStatItem>()
        json.optJSONArray("studios")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val studioObj = item.optJSONObject("studio")
                studioList.add(
                    RankedStatItem(
                        id = studioObj?.optInt("id"),
                        name = studioObj?.optString("name") ?: item.optString("studio", KitsugiStrings.get(R.string.bilinmeyen_studyo)),
                        count = item.optInt("count", 0),
                        meanScore = item.optDouble("meanScore", 0.0),
                        timeSpentMinutes = if (isAnime) item.optInt("minutesWatched", 0) else null,
                        chaptersRead = if (!isAnime) item.optInt("chaptersRead", 0) else null
                    )
                )
            }
        }

        var plannedDays = 0.0
        statusList.find { it.status.equals("PLANNING", ignoreCase = true) }?.let {
            plannedDays = if (isAnime) (it.count * 12.0 * 24.0 / 60.0 / 24.0) else it.count.toDouble()
        }

        return DetailedUserOverviewStats(
            count = count,
            episodesWatched = epOrChap,
            daysWatched = daysWatched,
            plannedDaysOrCount = plannedDays,
            meanScore = meanScore,
            standardDeviation = stdDev,
            scoreList = scoreList,
            lengthList = lengthList,
            formatList = formatList,
            statusList = statusList,
            countryList = countryList,
            releaseYearList = releaseYearList,
            startYearList = startYearList,
            genreList = genreList,
            tagList = tagList,
            staffList = staffList,
            voiceActorList = voiceActorList,
            studioList = studioList
        )
    }

    fun computeOverviewStatsFromEntries(entries: List<MediaEntry>, isAnime: Boolean): DetailedUserOverviewStats {
        if (entries.isEmpty()) return DetailedUserOverviewStats()
        val count = entries.size
        val totalProgress = entries.sumOf { it.progress.coerceAtLeast(0) }
        val daysWatched = if (isAnime) {
            totalProgress * 24.0 / 60.0 / 24.0
        } else {
            entries.sumOf { it.volumeProgress.coerceAtLeast(0) }.toDouble()
        }
        val scores = entries.mapNotNull { it.score?.takeIf { score -> score > 0 } }
        val meanScore = if (scores.isNotEmpty()) scores.average() else 0.0
        val variance = if (scores.size > 1) scores.sumOf { Math.pow(it - meanScore, 2.0) } / scores.size else 0.0
        val stdDev = Math.sqrt(variance)

        fun progressMetric(list: List<MediaEntry>): Int = if (isAnime) {
            list.sumOf { it.progress.coerceAtLeast(0) * 24 }
        } else {
            list.sumOf { it.progress.coerceAtLeast(0) }
        }

        fun averageScore(list: List<MediaEntry>): Double =
            list.mapNotNull { it.score?.takeIf { score -> score > 0 } }.average().takeIf { it.isFinite() } ?: 0.0

        val scoreCounts = (1..10).mapNotNull { score ->
            val matching = entries.filter { it.score == score }
            matching.takeIf { it.isNotEmpty() }?.let {
                ScoreStatItem(
                    score = score,
                    count = it.size,
                    minutesWatched = progressMetric(it),
                    meanScore = averageScore(it)
                )
            }
        }

        val statusGroup = entries.groupBy { it.status }.map { (status, matching) ->
            val label = when (status) {
                WatchStatus.Watching -> "CURRENT"
                WatchStatus.Completed -> "COMPLETED"
                WatchStatus.Planned -> "PLANNING"
                WatchStatus.Paused -> "PAUSED"
                WatchStatus.Dropped -> "DROPPED"
                WatchStatus.Repeating -> "REPEATING"
            }
            StatusStatItem(label, matching.size, progressMetric(matching), averageScore(matching))
        }

        val formatGroup = entries.groupBy { it.type }.map { (type, matching) ->
            val label = when (type) {
                MediaType.Anime -> "TV"
                MediaType.Movie -> "MOVIE"
                MediaType.TvShow -> "TV"
                MediaType.Manga -> "MANGA"
            }
            FormatStatItem(label, matching.size, progressMetric(matching), averageScore(matching))
        }

        val lengthGroup = entries.groupBy { it.total?.takeIf { total -> total > 0 } ?: 0 }
            .map { (length, matching) ->
                LengthStatItem(
                    length = if (length > 0) length.toString() else KitsugiStrings.get(R.string.label_unknown),
                    count = matching.size,
                    minutesWatched = progressMetric(matching),
                    meanScore = averageScore(matching)
                )
            }
            .sortedBy { it.length.toIntOrNull() ?: Int.MAX_VALUE }

        val releaseYears = entries.mapNotNull { entry -> entry.year?.takeIf { it > 0 }?.let { it to entry } }
            .groupBy({ it.first }, { it.second })
            .map { (year, matching) ->
                ReleaseYearStatItem(year, matching.size, progressMetric(matching), averageScore(matching))
            }
            .sortedBy { it.releaseYear }

        val startYears = entries.mapNotNull { entry ->
            entry.startDate?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2200 }?.let { it to entry }
        }.groupBy({ it.first }, { it.second })
            .map { (year, matching) ->
                StartYearStatItem(year, matching.size, progressMetric(matching), averageScore(matching))
            }
            .sortedBy { it.startYear }

        val plannedEntries = entries.count { it.status == WatchStatus.Planned }
        val plannedMetric = if (isAnime) plannedEntries * 0.2 else plannedEntries.toDouble()

        val tagList = entries.flatMap { entry ->
            entry.tags.orEmpty().split(',').map { it.trim() to entry }
        }.filter { it.first.isNotBlank() }
            .groupBy { it.first }
            .map { (tag, pairs) ->
                val matching = pairs.map { it.second }
                RankedStatItem(
                    name = tag,
                    count = matching.size,
                    meanScore = averageScore(matching),
                    timeSpentMinutes = if (isAnime) progressMetric(matching) else null,
                    chaptersRead = if (!isAnime) progressMetric(matching) else null
                )
            }
            .sortedByDescending { it.count }

        return DetailedUserOverviewStats(
            count = count,
            episodesWatched = totalProgress,
            daysWatched = daysWatched,
            plannedDaysOrCount = plannedMetric,
            meanScore = meanScore,
            standardDeviation = stdDev,
            scoreList = scoreCounts,
            lengthList = lengthGroup,
            formatList = formatGroup,
            statusList = statusGroup,
            releaseYearList = releaseYears,
            startYearList = startYears,
            tagList = tagList
        )
    }

    private suspend fun postGraphQl(token: String, query: String, variables: JSONObject): String {
        if (!com.kitsugi.animelist.data.remote.KitsugiApiBase.aniListGate(
                com.kitsugi.animelist.data.remote.AniListQuotaGate.Priority.INTERACTIVE
            )
        ) {
            throw IllegalStateException("AniList kotası dolu; profil isteği ertelendi.")
        }
        return withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("query", query)
                .put("variables", variables)
                .toString()

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .build()

            com.kitsugi.animelist.core.network.KitsugiHttpClient.aniListClient.newCall(request).execute().use { response ->
                val bodyText = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                com.kitsugi.animelist.data.remote.KitsugiApiBase
                    .extractAniListServiceDownMessage(bodyText)
                    ?.let { downMsg ->
                        throw com.kitsugi.animelist.data.remote.KitsugiApiBase
                            .recordAniListServiceDown(downMsg, response.code)
                    }
                if (!response.isSuccessful) {
                    // Keep raw JSON payloads out of user-facing exceptions when it's a normal HTTP error.
                    throw PlatformHttpException(response.code, "AniList profile request failed")
                }
                bodyText
            }
        }
    }

    // --- MYANIMELIST API FETCHING (via Official API & Jikan Fallback) ---
    fun fetchMalProfile(forceRefresh: Boolean = false) {
        if (_malState.value.isLoading) return
        if (!forceRefresh && _malState.value.name.isNotBlank()) {
            return
        }

        viewModelScope.launch {
            val token = ExternalAuthManager.getOrRefreshMalToken(context)
            if (token.isNullOrBlank()) {
                _malState.update { it.copy(isConnected = false, isLoading = false, error = KitsugiStrings.get(R.string.profile_connection_not_found, "MyAnimeList")) }
                return@launch
            }

            _malState.update { it.copy(isConnected = true, isLoading = true, error = null) }

            try {
                // Fetch profile metadata using MAL OAuth API with picture, anime_statistics, and manga_statistics fields
                val responseJson = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://api.myanimelist.net/v2/users/@me?fields=id,name,picture,gender,location,joined_at,anime_statistics,manga_statistics")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("MAL API error: ${response.code}")
                        }
                        response.body?.string() ?: ""
                    }
                }

                val userObj = JSONObject(responseJson)
                val username = userObj.optNullableString("name") ?: KitsugiStrings.get(R.string.mal_kullanicisi)
                val avatar = userObj.optNullableString("picture")
                val location = userObj.optNullableString("location") ?: ""
                val joinedAt = userObj.optNullableString("joined_at") ?: ""

                val animeStatsObj = userObj.optJSONObject("anime_statistics")
                val officialAnimeStats = if (animeStatsObj != null) {
                    val watching = animeStatsObj.optInt("num_items_watching", 0)
                    val completed = animeStatsObj.optInt("num_items_completed", 0)
                    val planned = animeStatsObj.optInt("num_items_plan_to_watch", 0)
                    val paused = animeStatsObj.optInt("num_items_on_hold", 0)
                    val dropped = animeStatsObj.optInt("num_items_dropped", 0)
                    val total = animeStatsObj.optInt("num_items", 0)
                    val meanScore = animeStatsObj.optDouble("mean_score", 0.0)
                    val episodes = animeStatsObj.optInt("num_episodes", 0)
                    val minutes = (animeStatsObj.optDouble("num_days", 0.0) * 24.0 * 60.0).toInt()

                    AniListStats(
                        count = total,
                        episodesWatched = episodes,
                        minutesWatched = minutes,
                        meanScore = meanScore,
                        watching = watching,
                        completed = completed,
                        planned = planned,
                        paused = paused,
                        dropped = dropped
                    )
                } else null

                val mangaStatsObj = userObj.optJSONObject("manga_statistics")
                val officialMangaStats = if (mangaStatsObj != null) {
                    val watching = mangaStatsObj.optInt("num_items_reading", 0)
                    val completed = mangaStatsObj.optInt("num_items_completed", 0)
                    val planned = mangaStatsObj.optInt("num_items_plan_to_read", 0)
                    val paused = mangaStatsObj.optInt("num_items_on_hold", 0)
                    val dropped = mangaStatsObj.optInt("num_items_dropped", 0)
                    val total = mangaStatsObj.optInt("num_items", 0)
                    val meanScore = mangaStatsObj.optDouble("mean_score", 0.0)
                    val chapters = mangaStatsObj.optInt("num_chapters", 0)
                    val volumes = mangaStatsObj.optInt("num_volumes", 0)

                    AniListStats(
                        count = total,
                        episodesWatched = chapters,
                        minutesWatched = volumes,
                        meanScore = meanScore,
                        watching = watching,
                        completed = completed,
                        planned = planned,
                        paused = paused,
                        dropped = dropped
                    )
                } else null

                _malState.update {
                    it.copy(
                        name = username,
                        avatarUrl = avatar,
                        location = location,
                        joinedAt = joinedAt,
                        animeStats = officialAnimeStats,
                        mangaStats = officialMangaStats,
                        animeDaysWatched = animeStatsObj?.optDouble("num_days", 0.0) ?: 0.0,
                        mangaDaysRead = mangaStatsObj?.optDouble("num_days", 0.0) ?: 0.0,
                        isLoading = false
                    )
                }

                // Arka planda güvenli favoriler ve arkadaşlar zenginleştirmesi (UI'ı asla bekletmez)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        fetchMalJikanData(username)
                    } catch (je: Exception) {
                        android.util.Log.e("ProfileViewModel", "Safe Jikan fetch failed: ${je.message}")
                    }
                }

            } catch (e: Exception) {
                val failureCode = PlatformFailureRegistry.reportAndResolve("mal", e)
                _malState.update { it.copy(isLoading = false, error = KitsugiStrings.get(R.string.profile_mal_load_failed), errorCode = failureCode) }
            }
        }
    }

    /**
     * MAL profilini zenginleştirir: istatistik, favoriler, son güncellemeler, hakkında, arkadaşlar,
     * geçmiş. Tüm istekler JikanGateway üzerinden gider → sıra: CİHAZ (MAL HTML, kullanıcının kendi
     * IP'si) → miribyou (Vercel) → Tenrai. Resmi MAL API'si bu verileri vermez.
     * Resmi API'den gelen değerler asla silinmez; yalnızca eksikler doldurulur.
     */
    private suspend fun fetchMalJikanData(username: String) {
        val encoded = java.net.URLEncoder.encode(username, "UTF-8")
        val base = "https://api.jikan.moe/v4/users/$encoded"
        _malState.update { it.copy(isExtraLoading = true) }

        suspend fun fetchData(url: String): String? = withTimeoutOrNull(MAL_EXTRA_TIMEOUT_MS) {
            when (val r = JikanGateway.fetch(url, JikanGateway.Priority.BACKGROUND, maxRetries = 1)) {
                is com.kitsugi.animelist.data.remote.JikanResult.Ok -> r.body
                else -> null
            }
        }

        try {
            coroutineScope {
                val fullJob = async(Dispatchers.IO) { runCatching { fetchData("$base/full") }.getOrNull() }
                val friendsJob = async(Dispatchers.IO) { runCatching { fetchData("$base/friends") }.getOrNull() }
                val historyJob = async(Dispatchers.IO) { runCatching { fetchData("$base/history") }.getOrNull() }
                val animeStatsJob = async(Dispatchers.IO) { runCatching { fetchData("$base/statistics/anime") }.getOrNull() }
                val mangaStatsJob = async(Dispatchers.IO) { runCatching { fetchData("$base/statistics/manga") }.getOrNull() }
                val reviewsJob = async(Dispatchers.IO) { runCatching { fetchData("$base/reviews?page=1") }.getOrNull() }
                val recommendationsJob = async(Dispatchers.IO) { runCatching { fetchData("$base/recommendations?page=1") }.getOrNull() }
                val clubsJob = async(Dispatchers.IO) { runCatching { fetchData("$base/clubs") }.getOrNull() }

                // Public MAL profile fields: biography, favorites, recent list updates and links.
                fullJob.await()?.let { body ->
                    runCatching { applyMalFullProfile(JSONObject(body).optJSONObject("data")) }
                        .onFailure { android.util.Log.w("ProfileViewModel", "MAL full profile parse failed: ${it.message}") }
                }

                // MAL exposes friends, not separate followers/following lists.
                friendsJob.await()?.let { body ->
                    runCatching {
                        val root = JSONObject(body)
                        val arr = root.optJSONArray("data") ?: JSONArray()
                        val friends = (0 until arr.length()).mapNotNull { i ->
                            val userObj = arr.optJSONObject(i)?.optJSONObject("user") ?: return@mapNotNull null
                            val friendName = userObj.optNullableString("username") ?: return@mapNotNull null
                            UserFollowItem(
                                id = friendName.hashCode(),
                                name = friendName,
                                avatarUrl = userObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    ?: userObj.optNullableString("image_url")
                            )
                        }
                        val hasNext = root.optJSONObject("pagination")?.optBoolean("has_next_page") ?: (friends.size >= 10)
                        _malState.update {
                            it.copy(
                                socialState = SocialState(followers = friends),
                                friendsPage = 1,
                                friendsHasNextPage = hasNext
                            )
                        }
                    }.onFailure { android.util.Log.w("ProfileViewModel", "MAL friends parse failed: ${it.message}") }
                }

                // MAL's public history is a real watch/read activity feed, distinct from followers.
                historyJob.await()?.let { body ->
                    runCatching {
                        val arr = JSONObject(body).optJSONArray("data") ?: return@runCatching
                        val items = (0 until minOf(arr.length(), 60)).mapNotNull { i ->
                            val o = arr.optJSONObject(i) ?: return@mapNotNull null
                            val entry = o.optJSONObject("entry") ?: return@mapNotNull null
                            val id = entry.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                            val isManga = entry.optString("type", "anime").equals("manga", ignoreCase = true)
                            MalActivityEntry(
                                malId = id,
                                isManga = isManga,
                                title = entry.optNullableString("name") ?: entry.optNullableString("title") ?: return@mapNotNull null,
                                imageUrl = null,
                                status = if (isManga) "Reading" else "Watching",
                                progress = o.optInt("increment", 0).takeIf { it > 0 },
                                total = null,
                                score = 0,
                                dateIso = o.optNullableString("date")
                            )
                        }
                        _malState.update { it.copy(history = items) }
                    }.onFailure { android.util.Log.w("ProfileViewModel", "MAL history parse failed: ${it.message}") }
                }

                animeStatsJob.await()?.let { body ->
                    runCatching {
                        val stats = MalProfileDataMapper.parseDetailedStatistics(body)
                        _malState.update {
                            it.copy(
                                animeScoreDistribution = stats.scores,
                                animeTypeDistribution = stats.types,
                                animeStatusDistribution = stats.statuses,
                                animeRatingDistribution = stats.ratings,
                                animeGenreStats = stats.genres,
                                animeStudioStats = stats.studios,
                                animeVoiceActorStats = stats.voiceActors
                            )
                        }
                    }.onFailure { android.util.Log.w("ProfileViewModel", "MAL anime statistics parse failed: ${it.message}") }
                }
                mangaStatsJob.await()?.let { body ->
                    runCatching {
                        val stats = MalProfileDataMapper.parseDetailedStatistics(body)
                        _malState.update {
                            it.copy(
                                mangaScoreDistribution = stats.scores,
                                mangaTypeDistribution = stats.types,
                                mangaStatusDistribution = stats.statuses,
                                mangaRatingDistribution = stats.ratings,
                                mangaGenreStats = stats.genres,
                                mangaStudioStats = stats.studios
                            )
                        }
                    }.onFailure { android.util.Log.w("ProfileViewModel", "MAL manga statistics parse failed: ${it.message}") }
                }

                reviewsJob.await()?.let { body ->
                    runCatching {
                        val page = MalProfileDataMapper.parseReviews(body)
                        _malState.update {
                            it.copy(reviews = page.items, reviewsPage = 1, reviewsHasNextPage = page.hasNextPage)
                        }
                    }.onFailure { android.util.Log.w("ProfileViewModel", "MAL reviews parse failed: ${it.message}") }
                }
                recommendationsJob.await()?.let { body ->
                    runCatching {
                        val page = MalProfileDataMapper.parseRecommendations(body)
                        _malState.update {
                            it.copy(
                                recommendations = page.items,
                                recommendationsPage = 1,
                                recommendationsHasNextPage = page.hasNextPage
                            )
                        }
                    }.onFailure { android.util.Log.w("ProfileViewModel", "MAL recommendations parse failed: ${it.message}") }
                }
                clubsJob.await()?.let { body ->
                    runCatching {
                        val clubs = MalProfileDataMapper.parseClubs(body)
                        _malState.update { it.copy(clubs = clubs) }
                    }.onFailure { android.util.Log.w("ProfileViewModel", "MAL clubs parse failed: ${it.message}") }
                }
            }
        } finally {
            _malState.update { it.copy(isExtraLoading = false) }
        }
    }

    private fun applyMalFullProfile(data: JSONObject?) {
        if (data == null) return

        val statsObj = data.optJSONObject("statistics")
        val animeStats = statsObj?.optJSONObject("anime")?.let { a ->
            AniListStats(
                count = a.optInt("total_entries", 0),
                episodesWatched = a.optInt("episodes_watched", 0),
                minutesWatched = (a.optDouble("days_watched", 0.0) * 24.0 * 60.0).toInt(),
                meanScore = a.optDouble("mean_score", 0.0),
                watching = a.optInt("watching", 0),
                completed = a.optInt("completed", 0),
                planned = a.optInt("plan_to_watch", 0),
                paused = a.optInt("on_hold", 0),
                dropped = a.optInt("dropped", 0)
            )
        }
        val mangaStats = statsObj?.optJSONObject("manga")?.let { m ->
            AniListStats(
                count = m.optInt("total_entries", 0),
                episodesWatched = m.optInt("chapters_read", 0),
                minutesWatched = m.optInt("volumes_read", 0),
                meanScore = m.optDouble("mean_score", 0.0),
                watching = m.optInt("reading", 0),
                completed = m.optInt("completed", 0),
                planned = m.optInt("plan_to_read", 0),
                paused = m.optInt("on_hold", 0),
                dropped = m.optInt("dropped", 0)
            )
        }

        fun imageOf(item: JSONObject): String {
            val images = item.optJSONObject("images")
            return images?.optJSONObject("jpg")?.optNullableString("large_image_url")
                ?: images?.optJSONObject("jpg")?.optNullableString("image_url")
                ?: images?.optJSONObject("webp")?.optNullableString("image_url")
                ?: ""
        }

        fun favList(key: String, nameKey: String): List<ProfileFavoriteItem> {
            val arr = data.optJSONObject("favorites")?.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                ProfileFavoriteItem(
                    id = id.toString(),
                    title = item.optNullableString(nameKey) ?: KitsugiStrings.get(R.string.isimsiz),
                    imageUrl = imageOf(item)
                )
            }
        }

        fun updates(key: String, isManga: Boolean): List<MalActivityEntry> {
            val arr = data.optJSONObject("updates")?.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val entry = o.optJSONObject("entry") ?: return@mapNotNull null
                val id = entry.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                val progressKey = if (isManga) "chapters_read" else "episodes_seen"
                val totalKey = if (isManga) "chapters_total" else "episodes_total"
                MalActivityEntry(
                    malId = id,
                    isManga = isManga,
                    title = entry.optNullableString("title") ?: return@mapNotNull null,
                    imageUrl = imageOf(entry).ifBlank { null },
                    status = o.optNullableString("status") ?: "",
                    progress = if (o.isNull(progressKey)) null else o.optInt(progressKey),
                    total = if (o.isNull(totalKey)) null else o.optInt(totalKey),
                    score = o.optInt("score", 0),
                    dateIso = o.optNullableString("date")
                )
            }
        }

        val aboutHtml = data.optNullableString("about")
        val aboutText = aboutHtml?.let {
            runCatching { org.jsoup.Jsoup.parse(it).wholeText().replace(Regex("\\n{3,}"), "\n\n").trim() }.getOrNull()
        }.orEmpty()

        val recent = (updates("anime", false) + updates("manga", true))
            .sortedByDescending { it.dateIso.orEmpty() }

        _malState.update {
            it.copy(
                avatarUrl = it.avatarUrl ?: data.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url"),
                location = it.location.ifBlank { data.optNullableString("location").orEmpty() },
                joinedAt = it.joinedAt.ifBlank { data.optNullableString("joined").orEmpty() },
                gender = data.optNullableString("gender").orEmpty().ifBlank { it.gender },
                birthday = data.optNullableString("birthday").orEmpty().ifBlank { it.birthday },
                lastOnline = data.optNullableString("last_online").orEmpty().ifBlank { it.lastOnline },
                about = aboutText.ifBlank { it.about },
                aboutHtml = aboutHtml.orEmpty().ifBlank { it.aboutHtml },
                externalLinks = MalProfileDataMapper.parseExternalLinks(data).ifEmpty { it.externalLinks },
                animeStats = animeStats?.takeIf { s -> s.count > 0 } ?: it.animeStats ?: animeStats,
                mangaStats = mangaStats?.takeIf { s -> s.count > 0 } ?: it.mangaStats ?: mangaStats,
                animeDaysWatched = statsObj?.optJSONObject("anime")?.optDouble("days_watched", it.animeDaysWatched) ?: it.animeDaysWatched,
                mangaDaysRead = statsObj?.optJSONObject("manga")?.optDouble("days_read", it.mangaDaysRead) ?: it.mangaDaysRead,
                animeRepeatCount = statsObj?.optJSONObject("anime")?.optInt("rewatched", it.animeRepeatCount) ?: it.animeRepeatCount,
                mangaRepeatCount = statsObj?.optJSONObject("manga")?.optInt("reread", it.mangaRepeatCount) ?: it.mangaRepeatCount,
                favoriteAnime = favList("anime", "title").ifEmpty { it.favoriteAnime },
                favoriteManga = favList("manga", "title").ifEmpty { it.favoriteManga },
                favoriteCharacters = favList("characters", "name").ifEmpty { it.favoriteCharacters },
                favoriteStaff = favList("people", "name").ifEmpty { it.favoriteStaff },
                recentUpdates = recent.ifEmpty { it.recentUpdates }
            )
        }
    }

    private suspend fun fetchMalSupplementalPage(url: String): String? = withTimeoutOrNull(MAL_EXTRA_TIMEOUT_MS) {
        when (val result = JikanGateway.fetch(url, JikanGateway.Priority.BACKGROUND, maxRetries = 1)) {
            is com.kitsugi.animelist.data.remote.JikanResult.Ok -> result.body
            else -> null
        }
    }

    fun loadMoreMalFriends() {
        val current = _malState.value
        if (!current.friendsHasNextPage || current.isLoadingMoreFriends || current.name.isBlank()) return
        val page = current.friendsPage + 1
        _malState.update { it.copy(isLoadingMoreFriends = true, friendsLoadMoreFailed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.jikan.moe/v4/users/${java.net.URLEncoder.encode(current.name, "UTF-8")}/friends?page=$page"
                val body = fetchMalSupplementalPage(url)
                if (body == null) {
                    _malState.update { it.copy(friendsLoadMoreFailed = true) }
                    return@launch
                }
                val root = JSONObject(body)
                val arr = root.optJSONArray("data") ?: JSONArray()
                val newFriends = (0 until arr.length()).mapNotNull { i ->
                    val user = arr.optJSONObject(i)?.optJSONObject("user") ?: return@mapNotNull null
                    val name = user.optNullableString("username") ?: return@mapNotNull null
                    UserFollowItem(
                        id = name.hashCode(),
                        name = name,
                        avatarUrl = user.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                            ?: user.optNullableString("image_url")
                    )
                }
                val hasNext = root.optJSONObject("pagination")?.optBoolean("has_next_page") ?: (newFriends.size >= 10)
                _malState.update { state ->
                    val merged = (state.socialState.followers + newFriends).distinctBy { it.name.lowercase() }
                    state.copy(
                        socialState = state.socialState.copy(followers = merged),
                        friendsPage = page,
                        friendsHasNextPage = hasNext,
                        friendsLoadMoreFailed = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("ProfileViewModel", "MAL friends page failed: ${e.message}")
                _malState.update { it.copy(friendsLoadMoreFailed = true) }
            } finally {
                _malState.update { it.copy(isLoadingMoreFriends = false) }
            }
        }
    }

    fun loadMoreMalReviews() {
        val current = _malState.value
        if (!current.reviewsHasNextPage || current.isLoadingMoreReviews || current.name.isBlank()) return
        val page = current.reviewsPage + 1
        _malState.update { it.copy(isLoadingMoreReviews = true, reviewsLoadMoreFailed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(current.name, "UTF-8")
                val body = fetchMalSupplementalPage("https://api.jikan.moe/v4/users/$encoded/reviews?page=$page")
                if (body == null) {
                    _malState.update { it.copy(reviewsLoadMoreFailed = true) }
                    return@launch
                }
                val result = MalProfileDataMapper.parseReviews(body)
                _malState.update { state ->
                    state.copy(
                        reviews = (state.reviews + result.items).distinctBy { it.reviewId },
                        reviewsPage = page,
                        reviewsHasNextPage = result.hasNextPage,
                        reviewsLoadMoreFailed = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("ProfileViewModel", "MAL reviews page failed: ${e.message}")
                _malState.update { it.copy(reviewsLoadMoreFailed = true) }
            } finally {
                _malState.update { it.copy(isLoadingMoreReviews = false) }
            }
        }
    }

    fun loadMoreMalRecommendations() {
        val current = _malState.value
        if (!current.recommendationsHasNextPage || current.isLoadingMoreRecommendations || current.name.isBlank()) return
        val page = current.recommendationsPage + 1
        _malState.update { it.copy(isLoadingMoreRecommendations = true, recommendationsLoadMoreFailed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(current.name, "UTF-8")
                val body = fetchMalSupplementalPage("https://api.jikan.moe/v4/users/$encoded/recommendations?page=$page")
                if (body == null) {
                    _malState.update { it.copy(recommendationsLoadMoreFailed = true) }
                    return@launch
                }
                val result = MalProfileDataMapper.parseRecommendations(body)
                _malState.update { state ->
                    state.copy(
                        recommendations = (state.recommendations + result.items).distinctBy { it.recommendationId },
                        recommendationsPage = page,
                        recommendationsHasNextPage = result.hasNextPage,
                        recommendationsLoadMoreFailed = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("ProfileViewModel", "MAL recommendations page failed: ${e.message}")
                _malState.update { it.copy(recommendationsLoadMoreFailed = true) }
            } finally {
                _malState.update { it.copy(isLoadingMoreRecommendations = false) }
            }
        }
    }

    fun loadMoreMalClubs() {
        val current = _malState.value
        if (!current.clubsHasNextPage || current.isLoadingMoreClubs || current.name.isBlank()) return
        val page = current.clubsPage + 1
        _malState.update { it.copy(isLoadingMoreClubs = true, clubsLoadMoreFailed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(current.name, "UTF-8")
                val body = fetchMalSupplementalPage("https://api.jikan.moe/v4/users/$encoded/clubs?page=$page")
                if (body == null) {
                    _malState.update { it.copy(clubsLoadMoreFailed = true) }
                    return@launch
                }
                val result = MalProfileDataMapper.parseClubsPage(body)
                _malState.update { state ->
                    state.copy(
                        clubs = (state.clubs + result.items).distinctBy { it.clubId },
                        clubsPage = page,
                        clubsHasNextPage = result.hasNextPage,
                        clubsLoadMoreFailed = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("ProfileViewModel", "MAL clubs page failed: ${e.message}")
                _malState.update { it.copy(clubsLoadMoreFailed = true) }
            } finally {
                _malState.update { it.copy(isLoadingMoreClubs = false) }
            }
        }
    }

    // --- SIMKL API FETCHING ---
    fun fetchSimklProfile(forceRefresh: Boolean = false) {
        if (_simklState.value.isLoading) return
        if (!forceRefresh && _simklState.value.name.isNotBlank()) {
            return
        }

        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            _simklState.update { it.copy(isConnected = false, isLoading = false, error = KitsugiStrings.get(R.string.profile_connection_not_found, "Simkl")) }
            return
        }

        _simklState.update { it.copy(isConnected = true, isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val profile = com.kitsugi.animelist.data.auth.SimklImportManager.fetchUserProfile(token)
                val watchlist = com.kitsugi.animelist.data.auth.SimklImportManager.fetchAllLists(token)
                val simklOverview = watchlist
                    .filter { it.type == MediaType.Anime || it.type == MediaType.TvShow || it.type == MediaType.Movie }
                    .takeIf { it.isNotEmpty() }
                    ?.let { computeOverviewStatsFromEntries(it, isAnime = true) }

                var totalAnime = 0
                var totalShows = 0
                var totalMovies = 0
                var watching = 0
                var completed = 0
                var planned = 0
                var paused = 0
                var dropped = 0
                val scores = mutableListOf<Int>()

                watchlist.forEach { entry ->
                    when (entry.type) {
                        com.kitsugi.animelist.model.MediaType.Anime -> totalAnime++
                        com.kitsugi.animelist.model.MediaType.TvShow -> totalShows++
                        com.kitsugi.animelist.model.MediaType.Movie -> totalMovies++
                        else -> {}
                    }

                    when (entry.status) {
                        com.kitsugi.animelist.model.WatchStatus.Watching -> watching++
                        com.kitsugi.animelist.model.WatchStatus.Completed -> completed++
                        com.kitsugi.animelist.model.WatchStatus.Planned -> planned++
                        com.kitsugi.animelist.model.WatchStatus.Paused -> paused++
                        com.kitsugi.animelist.model.WatchStatus.Dropped -> dropped++
                        else -> planned++
                    }

                    entry.score?.let { scores.add(it) }
                }

                val avgScore = if (scores.isNotEmpty()) scores.average() else 0.0

                // Fetch recent watch history from Simkl /sync/history
                val recentHistoryList = mutableListOf<ProfileFavoriteItem>()
                try {
                    val historyUrl = "https://api.simkl.com/sync/history?limit=20"
                    val historyRequest = Request.Builder()
                        .url(historyUrl)
                        .header("Authorization", "Bearer $token")
                        .header("simkl-api-key", com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID)
                        .build()

                    withContext(Dispatchers.IO) {
                        client.newCall(historyRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string().orEmpty()
                                val root = JSONObject(body)
                                // History returns arrays: anime, shows, movies
                                listOf("anime", "shows", "movies").forEach { key ->
                                    root.optJSONArray(key)?.let { arr ->
                                        for (i in 0 until minOf(arr.length(), 20)) {
                                            val entry = arr.getJSONObject(i)
                                            // Item is nested under "show", "movie", or "anime" key
                                            val mediaObj = entry.optJSONObject("show")
                                                ?: entry.optJSONObject("movie")
                                                ?: entry.optJSONObject("anime")
                                                ?: continue
                                            val title = mediaObj.optNullableString("title") ?: continue
                                            val ids = mediaObj.optJSONObject("ids")
                                            val simklId = ids?.optInt("simkl", 0) ?: 0
                                            val posterSlug = mediaObj.optNullableString("poster")
                                            val imageUrl = if (!posterSlug.isNullOrBlank()) {
                                                "https://simkl.in/posters/${posterSlug}_m.jpg"
                                            } else ""
                                            if (simklId > 0) {
                                                recentHistoryList.add(
                                                    ProfileFavoriteItem(
                                                        id = simklId.toString(),
                                                        title = title,
                                                        imageUrl = imageUrl
                                                    )
                                                )
                                            }
                                            if (recentHistoryList.size >= 20) return@use
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (he: Exception) {
                    android.util.Log.w("ProfileViewModel", "Simkl history fetch failed: ${he.message}")
                }

                _simklState.update {
                    it.copy(
                        isLoading = false,
                        name = profile.name,
                        avatarUrl = profile.avatarUrl,
                        joinedAt = profile.joinedAt,
                        location = profile.location,
                        bio = profile.bio,
                        accountType = profile.accountType,
                        totalAnime = totalAnime,
                        totalShows = totalShows,
                        totalMovies = totalMovies,
                        watching = watching,
                        completed = completed,
                        planned = planned,
                        paused = paused,
                        dropped = dropped,
                        avgScore = avgScore,
                        animeOverviewStats = simklOverview,
                        mangaOverviewStats = null,
                        recentHistory = recentHistoryList
                    )
                }
            } catch (e: Exception) {
                val failureCode = PlatformFailureRegistry.reportAndResolve("simkl", e)
                _simklState.update { it.copy(isLoading = false, error = KitsugiStrings.get(R.string.profile_simkl_load_failed), errorCode = failureCode) }
            }
        }
    }

    // --- KITSU API FETCHING ---
    fun fetchKitsuProfile(forceRefresh: Boolean = false) {
        if (_kitsuState.value.isLoading) return
        if (!forceRefresh && _kitsuState.value.name.isNotBlank()) {
            return
        }

        val token = ExternalAuthManager.getKitsuToken(context)
        val userId = ExternalAuthManager.getKitsuUserId(context)
        if (token.isNullOrBlank()) {
            _kitsuState.update { it.copy(isConnected = false, isLoading = false, error = KitsugiStrings.get(R.string.profile_connection_not_found, "Kitsu")) }
            return
        }

        _kitsuState.update { it.copy(isConnected = true, isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val fullProfile = com.kitsugi.animelist.data.auth.KitsuApiClient.fetchFullUserProfile(token)
                val targetUserId = userId ?: fullProfile.id
                
                val entriesDeferred = async { com.kitsugi.animelist.data.auth.KitsuApiClient.fetchAllLibraryEntries(token, targetUserId) }
                val statsDeferred = async { com.kitsugi.animelist.data.auth.KitsuApiClient.fetchUserStats(targetUserId) }
                val favsDeferred = async { com.kitsugi.animelist.data.auth.KitsuApiClient.fetchUserFavorites(targetUserId) }

                val entries = entriesDeferred.await()
                val userStats = statsDeferred.await()
                val favorites = favsDeferred.await()

                var totalAnime = 0
                var totalManga = 0
                var watching = 0
                var completed = 0
                var planned = 0
                var paused = 0
                var dropped = 0
                val scores = mutableListOf<Double>()
                val favList = mutableListOf<ProfileFavoriteItem>()

                entries.forEach { entry ->
                    val isAnime = entry.animeId != null
                    if (isAnime) totalAnime++ else totalManga++

                    when (entry.status.lowercase()) {
                        "current" -> watching++
                        "completed" -> completed++
                        "planned" -> planned++
                        "on_hold" -> paused++
                        "dropped" -> dropped++
                        else -> planned++
                    }

                    entry.ratingTwenty?.let { r20 ->
                        if (r20 > 0) scores.add(r20 / 2.0)
                    }

                    if (favList.size < 30 && entry.title.isNotBlank()) {
                        favList.add(
                            ProfileFavoriteItem(
                                id = entry.id,
                                title = entry.title,
                                imageUrl = entry.imageUrl ?: ""
                            )
                        )
                    }
                }

                val avgScore = if (scores.isNotEmpty()) scores.average() else 0.0

                val timeConsumedSeconds = userStats?.timeConsumedSeconds ?: 0L
                val timeSpentStr = if (timeConsumedSeconds > 0) {
                    val days = timeConsumedSeconds / 86400
                    val hours = (timeConsumedSeconds % 86400) / 3600
                    if (days > 0) KitsugiStrings.get(R.string.profile_duration_days_hours, days, hours)
                    else KitsugiStrings.get(R.string.profile_duration_hours, hours)
                } else null
                val episodesCount = userStats?.episodesWatched?.takeIf { it > 0 }

                fun KitsuLibraryEntry.toMediaEntry(index: Int, type: MediaType): MediaEntry {
                    val mappedStatus = when (status.lowercase()) {
                        "current" -> WatchStatus.Watching
                        "completed" -> WatchStatus.Completed
                        "planned" -> WatchStatus.Planned
                        "on_hold" -> WatchStatus.Paused
                        "dropped" -> WatchStatus.Dropped
                        else -> WatchStatus.Planned
                    }
                    return MediaEntry(
                        id = id.toIntOrNull() ?: index,
                        title = title,
                        type = type,
                        status = mappedStatus,
                        score = ratingTwenty?.takeIf { it > 0 }?.let { (it / 2).coerceIn(1, 10) },
                        progress = progress,
                        total = total,
                        source = "kitsu",
                        year = startYear,
                        startDate = startedAt
                    )
                }
                val kitsuAnimeEntries = entries.mapIndexedNotNull { index, entry ->
                    if (entry.animeId == null) null else entry.toMediaEntry(index, MediaType.Anime)
                }
                val kitsuMangaEntries = entries.mapIndexedNotNull { index, entry ->
                    if (entry.mangaId == null) null else entry.toMediaEntry(index, MediaType.Manga)
                }
                val kitsuAnimeOverview = kitsuAnimeEntries.takeIf { it.isNotEmpty() }
                    ?.let { computeOverviewStatsFromEntries(it, isAnime = true) }
                val kitsuMangaOverview = kitsuMangaEntries.takeIf { it.isNotEmpty() }
                    ?.let { computeOverviewStatsFromEntries(it, isAnime = false) }

                _kitsuState.update {
                    it.copy(
                        isConnected = true,
                        isLoading = false,
                        error = null,
                        id = fullProfile.id,
                        name = fullProfile.name,
                        avatarUrl = fullProfile.avatarUrl,
                        bannerUrl = fullProfile.bannerUrl,
                        about = fullProfile.about,
                        location = fullProfile.location,
                        gender = fullProfile.gender,
                        birthday = fullProfile.birthday,
                        joinedAt = fullProfile.joinedAt,
                        waifuOrHusbando = fullProfile.waifuOrHusbando,
                        followersCount = fullProfile.followersCount,
                        followingCount = fullProfile.followingCount,
                        commentsCount = fullProfile.commentsCount,
                        reviewsCount = fullProfile.reviewsCount,
                        totalAnime = totalAnime,
                        totalManga = totalManga,
                        watching = watching,
                        completed = completed,
                        planned = planned,
                        paused = paused,
                        dropped = dropped,
                        avgScore = avgScore,
                        timeSpentDaysHours = timeSpentStr,
                        episodesWatched = episodesCount,
                        animeOverviewStats = kitsuAnimeOverview,
                        mangaOverviewStats = kitsuMangaOverview,
                        favorites = favorites,
                        libraryEntries = favList
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Kitsu profile fetch failed: ${e.message}")
                val failureCode = PlatformFailureRegistry.reportAndResolve("kitsu", e)
                _kitsuState.update {
                    it.copy(
                        isConnected = true,
                        isLoading = false,
                        error = KitsugiStrings.get(R.string.profile_load_failed),
                        errorCode = failureCode
                    )
                }
            }
        }
    }

    // --- BANGUMI API FETCHING ---
    fun fetchBangumiProfile(forceRefresh: Boolean = false) {
        if (_bangumiState.value.isLoading) return
        if (!forceRefresh && _bangumiState.value.lastUpdatedMs > 0L) return

        if (!com.kitsugi.animelist.data.auth.BangumiAuthStore.isConnected(context)) {
            // Oturum kapatıldıysa önceki hesabın verisi ekranda kalmasın.
            _bangumiState.update {
                BangumiProfileState(isConnected = false, isLoading = false, error = KitsugiStrings.get(R.string.profile_connection_not_found, "Bangumi"))
            }
            return
        }

        _bangumiState.update { it.copy(isConnected = true, isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val snapshot = com.kitsugi.animelist.data.auth.BangumiProfileManager.fetch(context)
                if (snapshot == null) {
                    _bangumiState.update {
                        BangumiProfileState(isConnected = false, isLoading = false, error = KitsugiStrings.get(R.string.profile_connection_not_found, "Bangumi"))
                    }
                    return@launch
                }
                val user = snapshot.user
                _bangumiState.update {
                    it.copy(
                        isConnected = true,
                        isLoading = false,
                        error = null,
                        userId = user.id,
                        userName = user.username,
                        nickname = user.nickname,
                        avatarUrl = user.avatarUrl,
                        sign = user.sign,
                        userGroup = user.userGroup,
                        collections = snapshot.collections,
                        characterFavorites = snapshot.characterFavorites,
                        personFavorites = snapshot.personFavorites,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val failureCode = PlatformFailureRegistry.reportAndResolve("bangumi", e)
                _bangumiState.update {
                    it.copy(isLoading = false, error = KitsugiStrings.get(R.string.profile_bangumi_load_failed), errorCode = failureCode)
                }
            }
        }
    }

    // --- SHIKIMORI API FETCHING ---
    fun fetchShikimoriProfile(forceRefresh: Boolean = false) {
        if (_shikimoriState.value.isLoading) return
        if (!forceRefresh && _shikimoriState.value.name.isNotBlank()) {
            return
        }

        val token = ExternalAuthManager.getShikimoriToken(context)
        val userId = ExternalAuthManager.getShikimoriUserId(context)

        // Token var ama kullanıcı kimliği eksikse (girişte whoami yanıt vermediyse)
        // önce kimliği tembel çözmeye çalış.
        if (!token.isNullOrBlank() && (userId == null || userId <= 0)) {
            viewModelScope.launch {
                if (ExternalAuthManager.ensureShikimoriUserResolved(context)) {
                    fetchShikimoriProfile(forceRefresh = true)
                } else {
                    _shikimoriState.update { it.copy(isConnected = false, isLoading = false, error = KitsugiStrings.get(R.string.profile_connection_not_found, "Shikimori")) }
                }
            }
            return
        }

        if (token.isNullOrBlank() || userId == null || userId <= 0) {
            _shikimoriState.update { it.copy(isConnected = false, isLoading = false, error = KitsugiStrings.get(R.string.profile_connection_not_found, "Shikimori")) }
            return
        }

        _shikimoriState.update { it.copy(isConnected = true, isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val validToken = ExternalAuthManager.getOrRefreshShikimoriToken(context) ?: token
                val fullProfile = com.kitsugi.animelist.data.auth.ShikimoriApiClient.fetchFullUserProfile(validToken, userId)
                
                val ratesDeferred = async { com.kitsugi.animelist.data.auth.ShikimoriApiClient.fetchAllUserRates(validToken, userId) }
                val favsDeferred = async { com.kitsugi.animelist.data.auth.ShikimoriApiClient.fetchUserFavorites(validToken, userId) }
                val historyDeferred = async { com.kitsugi.animelist.data.auth.ShikimoriApiClient.fetchUserHistory(validToken, userId) }

                val rates = ratesDeferred.await()
                val favorites = favsDeferred.await()
                val history = historyDeferred.await()

                val favList = mutableListOf<ProfileFavoriteItem>()
                val scores = mutableListOf<Int>()

                rates.forEach { rate ->
                    if (rate.score > 0) scores.add(rate.score)
                    if (favList.size < 30 && rate.title.isNotBlank()) {
                        favList.add(
                            ProfileFavoriteItem(
                                id = rate.id.toString(),
                                title = rate.title,
                                imageUrl = rate.imageUrl ?: ""
                            )
                        )
                    }
                }

                val avgScore = if (scores.isNotEmpty()) scores.average() else 0.0

                fun ShikimoriRate.toMediaEntry(isAnime: Boolean): MediaEntry {
                    val mappedStatus = when (status.lowercase()) {
                        "watching", "reading", "current" -> WatchStatus.Watching
                        "completed" -> WatchStatus.Completed
                        "on_hold", "on-hold", "paused" -> WatchStatus.Paused
                        "dropped" -> WatchStatus.Dropped
                        "rewatching", "rereading", "repeating" -> WatchStatus.Repeating
                        else -> WatchStatus.Planned
                    }
                    return MediaEntry(
                        id = id,
                        title = title,
                        type = if (isAnime) MediaType.Anime else MediaType.Manga,
                        status = mappedStatus,
                        score = score.takeIf { it > 0 },
                        progress = if (isAnime) episodes else chapters,
                        total = total,
                        source = "shikimori",
                        year = startYear,
                        volumeProgress = volumes
                    )
                }
                val shikimoriAnimeEntries = rates.filter { it.targetType.equals("Anime", ignoreCase = true) }
                    .map { it.toMediaEntry(isAnime = true) }
                val shikimoriMangaEntries = rates.filter { it.targetType.equals("Manga", ignoreCase = true) }
                    .map { it.toMediaEntry(isAnime = false) }
                val shikimoriAnimeOverview = shikimoriAnimeEntries.takeIf { it.isNotEmpty() }
                    ?.let { computeOverviewStatsFromEntries(it, isAnime = true) }
                val shikimoriMangaOverview = shikimoriMangaEntries.takeIf { it.isNotEmpty() }
                    ?.let { computeOverviewStatsFromEntries(it, isAnime = false) }

                _shikimoriState.update {
                    it.copy(
                        isConnected = true,
                        isLoading = false,
                        error = null,
                        id = fullProfile.id,
                        name = fullProfile.nickname,
                        avatarUrl = fullProfile.avatarUrl,
                        location = fullProfile.location,
                        lastOnline = fullProfile.lastOnline,
                        joinedAt = null,
                        bio = fullProfile.bio,
                        website = fullProfile.website,
                        sex = fullProfile.sex,
                        fullYears = fullProfile.fullYears,
                        watchingAnime = fullProfile.watchingAnime,
                        completedAnime = fullProfile.completedAnime,
                        onHoldAnime = fullProfile.onHoldAnime,
                        droppedAnime = fullProfile.droppedAnime,
                        plannedAnime = fullProfile.plannedAnime,
                        readingManga = fullProfile.readingManga,
                        completedManga = fullProfile.completedManga,
                        onHoldManga = fullProfile.onHoldManga,
                        droppedManga = fullProfile.droppedManga,
                        plannedManga = fullProfile.plannedManga,
                        animeScoreDist = fullProfile.animeScoreDist,
                        mangaScoreDist = fullProfile.mangaScoreDist,
                        avgScore = avgScore,
                        animeOverviewStats = shikimoriAnimeOverview,
                        mangaOverviewStats = shikimoriMangaOverview,
                        favorites = favorites,
                        history = history,
                        recentRates = favList
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Shikimori profile fetch failed: ${e.message}")
                val failureCode = PlatformFailureRegistry.reportAndResolve("shikimori", e)
                _shikimoriState.update {
                    it.copy(
                        isConnected = true,
                        isLoading = false,
                        error = KitsugiStrings.get(R.string.profile_load_failed),
                        errorCode = failureCode
                    )
                }
            }
        }
    }
}

// --- DATA STRUCTURES ---

data class LocalStats(
    val totalAnime: Int = 0,
    val watchingAnime: Int = 0,
    val completedAnime: Int = 0,
    val plannedAnime: Int = 0,
    val pausedAnime: Int = 0,
    val droppedAnime: Int = 0,
    val avgAnimeScore: Double = 0.0,

    val totalManga: Int = 0,
    val readingManga: Int = 0,
    val completedManga: Int = 0,
    val plannedManga: Int = 0,
    val pausedManga: Int = 0,
    val droppedManga: Int = 0,
    val avgMangaScore: Double = 0.0
)

data class AniListStats(
    val count: Int,
    val episodesWatched: Int,
    val minutesWatched: Int,
    val meanScore: Double,
    val watching: Int,
    val completed: Int,
    val planned: Int,
    val paused: Int,
    val dropped: Int
)

data class ProfileFavoriteItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val isAdult: Boolean = false
)

sealed class ProfileActivityItem {
    abstract val id: String
    abstract val userId: Int?
    abstract val userName: String
    abstract val userAvatar: String?
    abstract val createdAt: Long
    abstract val likeCount: Int
    abstract val isLiked: Boolean
    abstract val replyCount: Int

    data class TextActivity(
        override val id: String,
        override val userId: Int?,
        override val userName: String,
        override val userAvatar: String?,
        val text: String,
        override val createdAt: Long,
        override val likeCount: Int,
        override val isLiked: Boolean,
        override val replyCount: Int = 0
    ) : ProfileActivityItem()

    data class ListActivity(
        override val id: String,
        override val userId: Int?,
        override val userName: String,
        override val userAvatar: String?,
        val mediaTitle: String,
        val mediaImage: String?,
        val status: String,
        val progress: String?,
        override val createdAt: Long,
        override val likeCount: Int,
        override val isLiked: Boolean,
        override val replyCount: Int = 0,
        val mediaId: Int? = null,
        val mediaType: String? = null,
        val isAdult: Boolean = false
    ) : ProfileActivityItem()
}


data class ScoreStatItem(
    val score: Int,
    val count: Int,
    val minutesWatched: Int = 0,
    val meanScore: Double = 0.0
)

data class LengthStatItem(
    val length: String,
    val count: Int,
    val minutesWatched: Int = 0,
    val meanScore: Double = 0.0
)

data class FormatStatItem(
    val format: String,
    val count: Int,
    val minutesWatched: Int = 0,
    val meanScore: Double = 0.0
)

data class StatusStatItem(
    val status: String,
    val count: Int,
    val minutesWatched: Int = 0,
    val meanScore: Double = 0.0
)

data class CountryStatItem(
    val country: String,
    val count: Int,
    val minutesWatched: Int = 0,
    val meanScore: Double = 0.0
)

data class ReleaseYearStatItem(
    val releaseYear: Int,
    val count: Int,
    val minutesWatched: Int = 0,
    val meanScore: Double = 0.0
)

data class StartYearStatItem(
    val startYear: Int,
    val count: Int,
    val minutesWatched: Int = 0,
    val meanScore: Double = 0.0
)

data class RankedStatItem(
    val id: Int? = null,
    val name: String,
    val count: Int,
    val meanScore: Double = 0.0,
    val timeSpentMinutes: Int? = null,
    val chaptersRead: Int? = null,
    val imageUrl: String? = null
)

data class DetailedUserOverviewStats(
    val count: Int = 0,
    val episodesWatched: Int = 0,
    val daysWatched: Double = 0.0,
    val plannedDaysOrCount: Double = 0.0,
    val meanScore: Double = 0.0,
    val standardDeviation: Double = 0.0,
    val scoreList: List<ScoreStatItem> = emptyList(),
    val lengthList: List<LengthStatItem> = emptyList(),
    val formatList: List<FormatStatItem> = emptyList(),
    val statusList: List<StatusStatItem> = emptyList(),
    val countryList: List<CountryStatItem> = emptyList(),
    val releaseYearList: List<ReleaseYearStatItem> = emptyList(),
    val startYearList: List<StartYearStatItem> = emptyList(),
    val genreList: List<RankedStatItem> = emptyList(),
    val tagList: List<RankedStatItem> = emptyList(),
    val staffList: List<RankedStatItem> = emptyList(),
    val voiceActorList: List<RankedStatItem> = emptyList(),
    val studioList: List<RankedStatItem> = emptyList()
)

data class UserFollowItem(
    val id: Int,
    val name: String,
    val avatarUrl: String?
)

data class SocialState(
    val followers: List<UserFollowItem> = emptyList(),
    val following: List<UserFollowItem> = emptyList()
)

data class AniListProfileState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val errorCode: PlatformErrorCode? = null,
    val userId: Int? = null,
    val name: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val about: String = "",
    val donatorBadge: String? = null,
    val donatorTier: Int = 0,
    val animeStats: AniListStats? = null,
    val mangaStats: AniListStats? = null,
    val animeOverviewStats: DetailedUserOverviewStats? = null,
    val mangaOverviewStats: DetailedUserOverviewStats? = null,
    val favoriteAnime: List<ProfileFavoriteItem> = emptyList(),
    val favoriteManga: List<ProfileFavoriteItem> = emptyList(),
    val favoriteCharacters: List<ProfileFavoriteItem> = emptyList(),
    val favoriteStaff: List<ProfileFavoriteItem> = emptyList(),
    val favoriteStudios: List<ProfileFavoriteItem> = emptyList(),
    // Favoriler için pagination state'leri (AniHyou referanslı)
    val favAnimePage: Int = 1,
    val favAnimeHasNext: Boolean = false,
    val favMangaPage: Int = 1,
    val favMangaHasNext: Boolean = false,
    val favCharPage: Int = 1,
    val favCharHasNext: Boolean = false,
    val favStaffPage: Int = 1,
    val favStaffHasNext: Boolean = false,
    val favStudioPage: Int = 1,
    val favStudioHasNext: Boolean = false,
    val activities: List<ProfileActivityItem> = emptyList(),
    val activitiesHasNext: Boolean = false,
    val activitiesPage: Int = 1,
    val socialState: SocialState = SocialState()
)

data class MalProfileState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val errorCode: PlatformErrorCode? = null,
    val name: String = "",
    val avatarUrl: String? = null,
    val location: String = "",
    val joinedAt: String = "",
    val animeStats: AniListStats? = null,
    val mangaStats: AniListStats? = null,
    val animeOverviewStats: DetailedUserOverviewStats? = null,
    val mangaOverviewStats: DetailedUserOverviewStats? = null,
    val favoriteAnime: List<ProfileFavoriteItem> = emptyList(),
    val favoriteManga: List<ProfileFavoriteItem> = emptyList(),
    val favoriteCharacters: List<ProfileFavoriteItem> = emptyList(),
    val favoriteStaff: List<ProfileFavoriteItem> = emptyList(),
    val favoriteStudios: List<ProfileFavoriteItem> = emptyList(),
    val socialState: SocialState = SocialState(),
    // ── Cihaz üzeri MAL HTML / miribyou ile gelen ek alanlar ──
    val isExtraLoading: Boolean = false,
    val about: String = "",
    val aboutHtml: String = "",
    val externalLinks: List<MalExternalLink> = emptyList(),
    val gender: String = "",
    val birthday: String = "",
    val lastOnline: String = "",
    val animeDaysWatched: Double = 0.0,
    val mangaDaysRead: Double = 0.0,
    val animeRepeatCount: Int = 0,
    val mangaRepeatCount: Int = 0,
    /** Profil sayfasındaki "Last Anime/Manga Updates" (kapak, durum, ilerleme, puan, tarih). */
    val recentUpdates: List<MalActivityEntry> = emptyList(),
    /** /history sayfası: bölüm bölüm izleme/okuma geçmişi. */
    val history: List<MalActivityEntry> = emptyList(),
    // ── Yeni: Banner desteği ──
    val bannerUrl: String? = null,
    // ── Detaylı MAL istatistik sayfaları ──
    val animeScoreDistribution: List<ScoreStatItem> = emptyList(),
    val mangaScoreDistribution: List<ScoreStatItem> = emptyList(),
    val animeTypeDistribution: List<FormatStatItem> = emptyList(),
    val mangaTypeDistribution: List<FormatStatItem> = emptyList(),
    val animeStatusDistribution: List<StatusStatItem> = emptyList(),
    val mangaStatusDistribution: List<StatusStatItem> = emptyList(),
    val animeRatingDistribution: List<FormatStatItem> = emptyList(),
    val mangaRatingDistribution: List<FormatStatItem> = emptyList(),
    val animeGenreStats: List<RankedStatItem> = emptyList(),
    val mangaGenreStats: List<RankedStatItem> = emptyList(),
    val animeStudioStats: List<RankedStatItem> = emptyList(),
    val mangaStudioStats: List<RankedStatItem> = emptyList(),
    val animeVoiceActorStats: List<RankedStatItem> = emptyList(),
    // ── Reviews, recommendations, clubs and pagination ──
    val reviews: List<MalReview> = emptyList(),
    val reviewsPage: Int = 1,
    val reviewsHasNextPage: Boolean = false,
    val isLoadingMoreReviews: Boolean = false,
    val reviewsLoadMoreFailed: Boolean = false,
    val recommendations: List<MalRecommendation> = emptyList(),
    val recommendationsPage: Int = 1,
    val recommendationsHasNextPage: Boolean = false,
    val isLoadingMoreRecommendations: Boolean = false,
    val recommendationsLoadMoreFailed: Boolean = false,
    val clubs: List<MalClub> = emptyList(),
    val clubsPage: Int = 1,
    val clubsHasNextPage: Boolean = false,
    val isLoadingMoreClubs: Boolean = false,
    val clubsLoadMoreFailed: Boolean = false,
    val friendsPage: Int = 1,
    val friendsHasNextPage: Boolean = false,
    val isLoadingMoreFriends: Boolean = false,
    val friendsLoadMoreFailed: Boolean = false,
    // ── Yeni: Favori sayfalama ──
    val favAnimePage: Int = 1,
    val favAnimeHasNext: Boolean = false,
    val favMangaPage: Int = 1,
    val favMangaHasNext: Boolean = false,
    val favCharPage: Int = 1,
    val favCharHasNext: Boolean = false,
    val favStaffPage: Int = 1,
    val favStaffHasNext: Boolean = false,
    // ── Yeni: İstatistik alt sekmeleri ──
    val statsSubTab: Int = 0  // 0: Genel, 1: Türler, 2: Stüdyolar, 3: Seslendirme
)

/** MAL aktivite satırı (son güncelleme veya geçmiş kaydı). */
data class MalActivityEntry(
    val malId: Int,
    val isManga: Boolean,
    val title: String,
    val imageUrl: String?,
    /** MAL durum metni: Watching, Completed, On-Hold, Dropped, Plan to Watch, Reading... */
    val status: String,
    val progress: Int?,
    val total: Int?,
    val score: Int,
    val dateIso: String?
)

// ── MAL profile enrichment models ───────────────────────────────────────

data class MalExternalLink(
    val name: String,
    val url: String
)

data class MalReview(
    val reviewId: Int,
    val malId: Int,
    val kind: String, // "anime" veya "manga"
    val title: String,
    val url: String,
    val imageUrl: String?,
    val score: Int,
    val helpfulCount: Int,
    val dateIso: String?,
    val textPreview: String
)

data class MalRecommendation(
    val recommendationId: Int,
    val malId: Int,
    val kind: String,
    val title: String,
    val url: String,
    val imageUrl: String?,
    val recommendationText: String,
    val dateIso: String?
)

data class MalClub(
    val clubId: Int,
    val name: String,
    val url: String,
    val imageUrl: String?,
    val memberCount: Int,
    val category: String?
)

data class SimklProfileState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val errorCode: PlatformErrorCode? = null,
    val name: String = "",
    val avatarUrl: String? = null,
    val joinedAt: String? = null,
    val location: String? = null,
    val bio: String? = null,
    val accountType: String? = null,
    val totalAnime: Int = 0,
    val totalShows: Int = 0,
    val totalMovies: Int = 0,
    val watching: Int = 0,
    val completed: Int = 0,
    val planned: Int = 0,
    val paused: Int = 0,
    val dropped: Int = 0,
    val avgScore: Double = 0.0,
    val animeOverviewStats: DetailedUserOverviewStats? = null,
    val mangaOverviewStats: DetailedUserOverviewStats? = null,
    val recentHistory: List<ProfileFavoriteItem> = emptyList(),
    val socialState: SocialState = SocialState()
)

data class KitsuProfileState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val errorCode: PlatformErrorCode? = null,
    val id: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val about: String? = null,
    val location: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val joinedAt: String? = null,
    val waifuOrHusbando: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val commentsCount: Int = 0,
    val reviewsCount: Int = 0,
    val totalAnime: Int = 0,
    val totalManga: Int = 0,
    val watching: Int = 0,
    val completed: Int = 0,
    val planned: Int = 0,
    val paused: Int = 0,
    val dropped: Int = 0,
    val avgScore: Double = 0.0,
    val timeSpentDaysHours: String? = null,
    val episodesWatched: Int? = null,
    val animeOverviewStats: DetailedUserOverviewStats? = null,
    val mangaOverviewStats: DetailedUserOverviewStats? = null,
    val favorites: List<ProfileFavoriteItem> = emptyList(),
    val libraryEntries: List<ProfileFavoriteItem> = emptyList()
)

/** Bangumi profil sekmesinin durumu (veri kaynağı: BangumiProfileManager). */
data class BangumiProfileState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val errorCode: PlatformErrorCode? = null,
    val userId: Long = 0L,
    val userName: String = "",
    val nickname: String = "",
    val avatarUrl: String? = null,
    val sign: String? = null,
    val userGroup: Int? = null,
    val collections: List<com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiUserCollection> = emptyList(),
    val characterFavorites: List<com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiFavoriteItem> = emptyList(),
    val personFavorites: List<com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiFavoriteItem> = emptyList(),
    /** 0 → henüz yüklenmedi; yükleme başarılı olunca zaman damgası yazılır. */
    val lastUpdatedMs: Long = 0L
)

data class ShikimoriProfileState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val errorCode: PlatformErrorCode? = null,
    val id: Int = 0,
    val name: String = "",
    val avatarUrl: String? = null,
    val location: String? = null,
    val lastOnline: String? = null,
    val joinedAt: String? = null,
    val bio: String? = null,
    val website: String? = null,
    val sex: String? = null,
    val fullYears: Int? = null,
    val watchingAnime: Int = 0,
    val completedAnime: Int = 0,
    val onHoldAnime: Int = 0,
    val droppedAnime: Int = 0,
    val plannedAnime: Int = 0,
    val readingManga: Int = 0,
    val completedManga: Int = 0,
    val onHoldManga: Int = 0,
    val droppedManga: Int = 0,
    val plannedManga: Int = 0,
    val animeScoreDist: Map<Int, Int> = emptyMap(),
    val mangaScoreDist: Map<Int, Int> = emptyMap(),
    val avgScore: Double = 0.0,
    val animeOverviewStats: DetailedUserOverviewStats? = null,
    val mangaOverviewStats: DetailedUserOverviewStats? = null,
    val favorites: List<ProfileFavoriteItem> = emptyList(),
    val history: List<com.kitsugi.animelist.data.auth.ShikimoriApiClient.ShikimoriHistoryItem> = emptyList(),
    val recentRates: List<ProfileFavoriteItem> = emptyList()
)


