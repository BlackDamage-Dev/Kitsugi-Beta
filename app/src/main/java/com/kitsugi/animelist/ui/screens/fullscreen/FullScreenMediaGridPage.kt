@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kitsugi.animelist.ui.screens.fullscreen

import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import com.kitsugi.animelist.ui.components.KITSUGI_MEDIA_GENRES
import com.kitsugi.animelist.ui.components.KitsugiGridSortOption
import com.kitsugi.animelist.ui.components.KitsugiMediaFilterBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.components.KitsugiRankingMediaCard
import com.kitsugi.animelist.ui.components.DetailedSeasonalMediaCard
import com.kitsugi.animelist.ui.components.KitsugiSeasonalFilterBottomSheet
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.exploreIdentity
import com.kitsugi.animelist.ui.screens.explore.ExploreFilterClients
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.screens.explore.ExploreSourceFilters
import com.kitsugi.animelist.ui.screens.explore.fetchFilteredExplorePage
import com.kitsugi.animelist.ui.screens.explore.localizedLabel
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.kitsugiLazyKey
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/** Simkl uses 20-item API pages; its static trending chart files are sliced locally. */
private const val SIMKL_EXPLORE_PAGE_SIZE = 20
private const val SIMKL_MAX_API_PAGE = 20

/**
 * Kullanıcı bir kez kaydırdığında otomatik olarak çekilebilecek azami sayfa sayısı.
 *
 * Sınırsız otomatik sayfalama (eski davranış) sayfayı "döngüye" sokuyordu: her yükleme
 * bittiğinde tetikleyici yeniden ateşleniyor, kullanıcı hiç kaydırmasa bile art arda
 * istek gidiyordu (AniList 429 hız sınırı + GC fırtınası). Bütçe, normal sonsuz
 * kaydırmayı bozmadan bu kaçağı kapatır.
 */
private const val AUTO_LOAD_BUDGET_PER_SCROLL = 3

@Composable
fun FullScreenMediaGridPage(
    title: String,
    categoryType: ExploreCategoryType,
    platform: ExplorePlatform,
    initialResults: List<JikanSearchResult>,
    session: com.kitsugi.animelist.ui.app.FullScreenMediaGridState? = null,
    alreadyInList: (JikanSearchResult) -> Boolean,
    onItemClick: (JikanSearchResult) -> Unit,
    onBackClick: () -> Unit,
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    showAdultContent: Boolean = false,
    blurAdultMedia: Boolean = false,
    getMediaEntry: (JikanSearchResult) -> com.kitsugi.animelist.model.MediaEntry? = { null },
    onQuickAddToList: ((JikanSearchResult) -> Unit)? = null,
    onAddAllToList: ((List<JikanSearchResult>) -> Unit)? = null
) {
    val accentColor = LocalKitsugiAccent.current
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { JikanApiClient() }
    val tmdbApiClient = remember { TmdbApiClient() }
    val simklApiClient = remember { com.kitsugi.animelist.data.remote.SimklApiClient() }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columnCount = rememberKitsugiGridColumns()

    val context = androidx.compose.ui.platform.LocalContext.current
    val gridPrefs = remember(context) { context.getSharedPreferences("kitsugi_ui_prefs", android.content.Context.MODE_PRIVATE) }
    // Grid/Liste görünümü tercihi kalıcı olarak hatırlanır
    var isGridView by rememberSaveable { mutableStateOf(gridPrefs.getBoolean("fullscreen_media_grid_is_grid_view", false)) }
    LaunchedEffect(isGridView) {
        gridPrefs.edit().putBoolean("fullscreen_media_grid_is_grid_view", isGridView).apply()
    }

    val currentCalendar = remember { Calendar.getInstance() }
    val currentYear = remember { currentCalendar.get(Calendar.YEAR) }
    val currentSeasonStr = remember {
        val month = currentCalendar.get(Calendar.MONTH)
        when (month) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "WINTER"
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "SPRING"
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "SUMMER"
            else -> "FALL"
        }
    }

    var seasonalYear by remember { mutableIntStateOf(currentYear) }
    var seasonalSeason by remember { mutableStateOf(currentSeasonStr) }
    var seasonalSort by remember { mutableStateOf("POPULARITY_DESC") }
    var showFilterBottomSheet by remember { mutableStateOf(false) }

    var selectedGenreId by rememberSaveable { mutableStateOf("ALL") }
    var selectedSortOption by rememberSaveable { mutableStateOf(KitsugiGridSortOption.DEFAULT) }
    var showGeneralFilterBottomSheet by remember { mutableStateOf(false) }

    // ── Kaynağa gönderilen (uygulanmış) filtre ve sıralama ────────────────────
    // Çipteki seçim ile API'ye giden seçim ayrı tutulur: "Uygula" yalnız burayı değiştirir,
    // dolayısıyla sayfa baştan (1. sayfadan) filtrelenmiş olarak çekilir.
    var appliedGenreId by rememberSaveable { mutableStateOf("ALL") }
    var appliedSortOption by rememberSaveable { mutableStateOf(KitsugiGridSortOption.DEFAULT) }
    /**
     * Her filtre/sıralama uygulamasında artan jeton. Uçuştaki eski sayfa istekleri bu jetonu
     * karşılaştırıp sonucu yazmaz; böylece hızlı ardışık seçimlerde liste eski veriyle dolmaz.
     */
    var filterGeneration by remember { mutableIntStateOf(0) }
    /** Mevsim/yıl değişiminde liste baştan çekilmelidir (tohum listeye dönüş yeterli değil). */
    var forceFreshFetch by remember { mutableStateOf(false) }

    val isSeasonalAnime = categoryType == ExploreCategoryType.SEASONAL_ANIME &&
        platform != ExplorePlatform.TMDB && platform != ExplorePlatform.SIMKL
    val dynamicTitle = remember(isSeasonalAnime, title, seasonalSeason, seasonalYear) {
        if (isSeasonalAnime) {
            val sn = when (seasonalSeason.uppercase()) {
                "WINTER" -> "Kış"; "SPRING" -> "İlkbahar"; "SUMMER" -> "Yaz"; else -> "Sonbahar"
            }
            "${platform.label} · $sn $seasonalYear"
        } else title
    }

    // Filtrasyon öncesi "tohum" liste: filtre temizlendiğinde ağa çıkmadan geri dönülen başlangıç
    // verisi (keşfet rafından gelen ilk sonuçlar ya da sayfa hafızası).
    val seedResults = remember(platform, categoryType, title) { session?.cachedResults ?: initialResults }
    var loadedResults by remember(platform, categoryType, title) { mutableStateOf(seedResults) }
    var currentPage by remember(platform, categoryType, title) {
        mutableIntStateOf(session?.cachedPage ?: if (initialResults.isEmpty()) 0 else 1)
    }
    var isLoadingMore by remember { mutableStateOf(false) }
    // Simkl mixes page-based genre/premiere APIs with finite trending charts and an unpaged
    // airing schedule. Its own watchlists are already fully loaded and must not be re-fetched.
    val isSimklPersonalList = platform == ExplorePlatform.SIMKL && listOf(
        context.getString(com.kitsugi.animelist.R.string.explore_simkl_continue_watching_series),
        context.getString(com.kitsugi.animelist.R.string.explore_simkl_continue_watching_movies),
        context.getString(com.kitsugi.animelist.R.string.explore_simkl_plantowatch_series),
        context.getString(com.kitsugi.animelist.R.string.explore_simkl_plantowatch_movies)
    ).any { title.contains(it, ignoreCase = true) }
    // Kullanıcının kendi listeleri (Simkl izleme listeleri, TMDB izlemeye devam) kaynak
    // API'sinde filtrelenebilir bir katalog değildir → filtre yalnız cihazda uygulanır.
    val isTmdbPersonalList = platform == ExplorePlatform.TMDB &&
        (title.startsWith("İzlemeye Devam") || title.startsWith("Planladıklarım"))
    val isPersonalList = isSimklPersonalList || isTmdbPersonalList
    val simklCategoryUsesFiniteChart = platform == ExplorePlatform.SIMKL && categoryType in setOf(
        ExploreCategoryType.TOP_ANIME,
        ExploreCategoryType.MOVIE_ANIME,
        ExploreCategoryType.PUBLISHING_MANGA,
        ExploreCategoryType.TRENDING_ANIME
    )
    val simklCategoryHasPagination = !isSimklPersonalList && categoryType !in setOf(
        ExploreCategoryType.AIRING_ANIME,
        ExploreCategoryType.TRENDING_MANGA
    )
    var simklPageInitialized by remember(platform, categoryType, title, isSimklPersonalList) {
        mutableStateOf(platform != ExplorePlatform.SIMKL || isSimklPersonalList)
    }
    var simklTrendingResults by remember(platform, categoryType, title) { mutableStateOf<List<JikanSearchResult>?>(null) }
    var hasMorePages by remember(platform, categoryType, title, isSimklPersonalList) {
        mutableStateOf(session?.cachedHasMore ?: !isSimklPersonalList)
    }
    var loadError by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.SideEffect {
        session?.cachedResults = loadedResults
        session?.cachedPage = currentPage
        session?.cachedHasMore = hasMorePages
    }

    // ── Kaynak-tarafı filtre yetenekleri ──────────────────────────────────────
    // Seçilen tür/sıralama kaynağın API'sinde karşılığı varsa istek doğrudan filtreli gider
    // (sayfa 2, 3… aynı sırayı korur). Yoksa eski davranış korunur: elde tutulan liste
    // cihazda süzülür/sıralanır ve filtre sayfası bu çipi "📱 yerel" olarak işaretler.
    val serverGenreSupported = !isPersonalList && appliedGenreId != "ALL" &&
        ExploreSourceFilters.supportsGenre(platform, categoryType, appliedGenreId)
    val serverSortSupported = !isPersonalList && appliedSortOption != KitsugiGridSortOption.DEFAULT &&
        ExploreSourceFilters.supportsSort(platform, categoryType, appliedSortOption, appliedGenreId)
    val serverFilterActive = serverGenreSupported || serverSortSupported
    val localGenreFilter = appliedGenreId != "ALL" && !serverGenreSupported
    val localSort = appliedSortOption != KitsugiGridSortOption.DEFAULT && !serverSortSupported
    val filterClients = remember(apiClient, tmdbApiClient, simklApiClient) {
        ExploreFilterClients(jikan = apiClient, tmdb = tmdbApiClient, simkl = simklApiClient)
    }

    // ── Yaklaşan yayın tarihi zenginleştirme ──────────────────────────────────
    // TMDB "Yaklaşan Film ve Diziler" / Shikimori "Yayındaki Animeler" gibi liste
    // verileri öğenin kendi yayın takvimini taşımaz → kullanıcı detay sayfasına
    // girmeden kartlarda yaklaşan tarih görünmüyordu. Eksik `nextAiringEpisode`
    // alanları önbellekli yardımcıyla tamamlanır (AniList bölüm takvimi +
    // TMDB `next_episode_to_air`); eşleşmeyen öğeler değişmeden kalır.
    //
    // FIX: Eski kod `loadedResults` her değiştiğinde tüm listeyi yeniden enrich edip
    // doğrudan `loadedResults = enriched` yapıyordu. Hızlı scroll sırasında:
    //  1. Sayfa 1 enrich ediliyor (network)
    //  2. Kullanıcı scroll → sayfa 2 yükleniyor → loadedResults = p1+p2
    //  3. Sayfa 1 enrich bitiyor → loadedResults = enrich(p1) → p2 kayboluyor!
    //  4. Tekrar loadNextPage → p2 geri geliyor → "gidip geliyor" bug'ı.
    // Çözüm: enrich snapshot'ı al, bitince mevcut listeyle merge et (boyut büyüdüyse).
    LaunchedEffect(loadedResults) {
        val snapshot = loadedResults
        if (snapshot.isEmpty()) return@LaunchedEffect
        val enriched = com.kitsugi.animelist.data.remote.KitsugiUpcomingAirEnricher.enrich(snapshot)
        if (enriched === snapshot) return@LaunchedEffect
        val current = loadedResults
        if (current.size == snapshot.size) {
            if (enriched != current) loadedResults = enriched
        } else {
            val enrichedMap = enriched.associateBy { it.exploreIdentity() }
            val merged = current.map { cur -> enrichedMap[cur.exploreIdentity()] ?: cur }
            if (merged != current) loadedResults = merged
        }
    }

    suspend fun fetchSeasonalPage(page: Int): List<JikanSearchResult> = when (platform) {
        ExplorePlatform.AniList -> apiClient.aniListSeasonalAnime(page, showAdultContent, seasonalYear, seasonalSeason, seasonalSort)
        ExplorePlatform.MAL -> apiClient.seasonalAnime(page, showAdultContent, seasonalYear, seasonalSeason, seasonalSort)
        ExplorePlatform.KITSU -> com.kitsugi.animelist.data.remote.KitsuExploreClient.searchMediaAdvanced(
            mediaType = com.kitsugi.animelist.model.MediaType.Anime, page = page, limit = 20,
            season = seasonalSeason.lowercase(), seasonYear = seasonalYear,
            sort = when (seasonalSort) { "SCORE_DESC" -> "-averageRating"; "START_DATE_DESC" -> "-startDate"; else -> "-userCount" }
        )
        ExplorePlatform.SHIKIMORI -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
            mediaType = com.kitsugi.animelist.model.MediaType.Anime, page = page, limit = 20,
            season = "${seasonalSeason.lowercase()}_$seasonalYear",
            order = when (seasonalSort) { "SCORE_DESC" -> "ranked"; "START_DATE_DESC" -> "aired_on"; else -> "popularity" },
            censored = !showAdultContent
        )
        ExplorePlatform.BANGUMI -> {
            // ⚠️ Eski yol `seasonFor(year, month)` idi: Kış 2025 için **yalnız Ocak** ayında
            // yayınlananları listeliyordu (mevsimin üçte biri) ve mevsimsel filtrenin
            // "Puana göre / İlgiye göre" sıralaması hiç uygulanmıyordu.
            // Artık `air_date` **çeyrek** aralığı + seçilen sıralama kullanılır
            // (canlı prob V1–V5: `>=2025-01-01` + `<2025-04-01` → 89 kayıt).
            val bangumiSort = when (seasonalSort?.uppercase()) {
                "SCORE_DESC" -> com.kitsugi.animelist.data.auth.BangumiApiClient.SearchSort.SCORE
                "MEMBERS_DESC",
                "POPULARITY_DESC" -> com.kitsugi.animelist.data.auth.BangumiApiClient.SearchSort.HEAT
                // `START_DATE_DESC` sunucudaki arama ucunda yok (yayın tarihi sıralaması
                // yalnız göz atma ucunda `sort=date` olarak var). Çeyrek aralığını korumak
                // için sıra (`rank`) kullanılır; kullanıcı ayrıca sıralama seçmediyse
                // varsayılan davranış da budur.
                else -> com.kitsugi.animelist.data.auth.BangumiApiClient.SearchSort.RANK
            }
            com.kitsugi.animelist.data.remote.KitsugiBangumiClient.searchMediaAdvanced(
                mediaType = com.kitsugi.animelist.model.MediaType.Anime,
                query = "",
                page = page,
                limit = 20,
                sort = bangumiSort,
                includeAdult = showAdultContent,
                season = seasonalSeason,
                seasonYear = seasonalYear
            )
        }
        else -> emptyList()
    }

    /**
     * Bangumi "Tümünü Gör" sayfalaması. `limit/offset` tabanlıdır; kategori → Bangumi
     * şeridi eşlemesi ExploreViewModel.loadBangumiData() ile birebir aynıdır ki ilk sayfa
     * ve devam sayfaları aynı sıralamayı kullansın.
     */
    suspend fun bangumiPage(page: Int): List<JikanSearchResult> {
        val bangumi = com.kitsugi.animelist.data.remote.KitsugiBangumiClient
        val limit = 20
        val offset = (page - 1).coerceAtLeast(0) * limit
        val adult = showAdultContent
        return when (categoryType) {
            ExploreCategoryType.TOP_ANIME -> bangumi.topAnime(limit, offset = offset)
            ExploreCategoryType.TOP_RATED_ANIME -> bangumi.topRatedAnime(limit, offset = offset)
            ExploreCategoryType.TRENDING_ANIME -> bangumi.trendingAnime(limit, offset = offset)
            ExploreCategoryType.AIRING_ANIME -> bangumi.airingAnime(limit, offset = offset)
            ExploreCategoryType.UPCOMING_ANIME -> bangumi.upcomingAnime(limit, offset = offset)
            ExploreCategoryType.MOVIE_ANIME -> bangumi.movieAnime(limit, offset = offset)
            ExploreCategoryType.BANGUMI_TV -> bangumi.realTvShows(limit, offset = offset)
            ExploreCategoryType.BANGUMI_MOVIES -> bangumi.realMovies(limit, offset = offset)
            ExploreCategoryType.SEASONAL_ANIME -> fetchSeasonalPage(page)
            ExploreCategoryType.NEWLY_ADDED_ANIME -> bangumi.newlyAddedAnime(limit, offset = offset)
            ExploreCategoryType.TOP_MANGA -> bangumi.topManga(limit, offset = offset)
            ExploreCategoryType.TOP_RATED_MANGA -> bangumi.topRatedManga(limit, offset = offset)
            ExploreCategoryType.PUBLISHING_MANGA -> bangumi.publishingManga(limit, offset = offset)
            ExploreCategoryType.TRENDING_MANGA -> bangumi.trendingManga(limit, offset = offset)
            ExploreCategoryType.NEWLY_ADDED_MANGA -> bangumi.newlyAddedManga(limit, offset = offset)
            ExploreCategoryType.NOVELS -> bangumi.novelsShelf(limit, offset = offset)
            else -> emptyList()
        }.filter { adult || !it.isAdult }
    }

    suspend fun fetchSimklPage(page: Int): List<JikanSearchResult> = when (categoryType) {
        ExploreCategoryType.TOP_ANIME -> {
            val chart = simklTrendingResults ?: simklApiClient.getTrendingPeriod("tv", "week")
                .also { simklTrendingResults = it }
            chart.drop((page - 1) * SIMKL_EXPLORE_PAGE_SIZE).take(SIMKL_EXPLORE_PAGE_SIZE)
        }
        ExploreCategoryType.AIRING_ANIME -> if (page == 1) simklApiClient.getAiringMedia("tv") else emptyList()
        ExploreCategoryType.MOVIE_ANIME -> {
            val chart = simklTrendingResults ?: simklApiClient.getTrendingPeriod("movies", "week")
                .also { simklTrendingResults = it }
            chart.drop((page - 1) * SIMKL_EXPLORE_PAGE_SIZE).take(SIMKL_EXPLORE_PAGE_SIZE)
        }
        ExploreCategoryType.TOP_MANGA -> simklApiClient.getExploreGenrePage("tv", "rank", page, SIMKL_EXPLORE_PAGE_SIZE)
        // The Simkl explore tile is the popular-movies list, not an upcoming-release list.
        // `this-week` is not a valid movie year filter; use the documented weekly sort instead.
        ExploreCategoryType.UPCOMING_ANIME -> simklApiClient.getExploreGenrePage(
            "movies", "popular-this-week", page, SIMKL_EXPLORE_PAGE_SIZE
        )
        ExploreCategoryType.PUBLISHING_MANGA -> {
            val chart = simklTrendingResults ?: simklApiClient.getTrendingPeriod("movies", "week")
                .also { simklTrendingResults = it }
            chart.drop((page - 1) * SIMKL_EXPLORE_PAGE_SIZE).take(SIMKL_EXPLORE_PAGE_SIZE)
        }
        ExploreCategoryType.SEASONAL_ANIME -> simklApiClient.getExploreGenrePage("tv", "rank", page, SIMKL_EXPLORE_PAGE_SIZE)
        ExploreCategoryType.TRENDING_ANIME -> {
            val chart = simklTrendingResults ?: simklApiClient.getTrendingPeriod("anime", "week")
                .also { simklTrendingResults = it }
            chart.drop((page - 1) * SIMKL_EXPLORE_PAGE_SIZE).take(SIMKL_EXPLORE_PAGE_SIZE)
        }
        ExploreCategoryType.NEWLY_ADDED_ANIME -> simklApiClient.getExploreGenrePage("anime", "rank", page, SIMKL_EXPLORE_PAGE_SIZE)
        ExploreCategoryType.TRENDING_MANGA -> if (page == 1) simklApiClient.getAiringMedia("anime") else emptyList()
        ExploreCategoryType.UPCOMING_MEDIA_TMDB -> simklApiClient.getPremieresPage(
            "anime", "soon", page, SIMKL_EXPLORE_PAGE_SIZE
        )
        else -> simklApiClient.getExploreGenrePage("anime", "rank", page, SIMKL_EXPLORE_PAGE_SIZE)
    }

    /**
     * Kategorinin kendi (filtresiz) sayfası. Mevcut davranış birebir korunur; sunucu tarafı
     * filtre/sıralama etkin değilken sayfalama buradan yürür.
     */
    suspend fun baseExplorePage(np: Int): List<JikanSearchResult> = when (platform) {
        ExplorePlatform.MAL -> when (categoryType) {
            ExploreCategoryType.TOP_RATED_ANIME -> apiClient.topAnime(np, showAdultContent)
            ExploreCategoryType.TOP_RATED_MANGA -> apiClient.topManga(np, showAdultContent)
            ExploreCategoryType.TOP_ANIME -> apiClient.topAnime(np, showAdultContent)
            ExploreCategoryType.TRENDING_ANIME -> apiClient.trendingAnime(np, showAdultContent)
            ExploreCategoryType.AIRING_ANIME -> apiClient.airingAnime(np, showAdultContent)
            ExploreCategoryType.UPCOMING_ANIME -> apiClient.upcomingAnime(np, showAdultContent)
            ExploreCategoryType.MOVIE_ANIME -> apiClient.movieAnime(np, showAdultContent)
            ExploreCategoryType.SEASONAL_ANIME -> apiClient.seasonalAnime(np, showAdultContent, seasonalYear, seasonalSeason, seasonalSort)
            ExploreCategoryType.TOP_MANGA -> apiClient.topManga(np, showAdultContent)
            ExploreCategoryType.PUBLISHING_MANGA -> apiClient.publishingManga(np, showAdultContent)
            ExploreCategoryType.TRENDING_MANGA -> apiClient.trendingManga(np, showAdultContent)
            ExploreCategoryType.NEWLY_ADDED_ANIME -> apiClient.newlyAddedAnime(np, showAdultContent)
            ExploreCategoryType.NEWLY_ADDED_MANGA -> apiClient.newlyAddedManga(np, showAdultContent)
            ExploreCategoryType.MANHWA_MANHUA -> apiClient.manhwaManhua(np, showAdultContent)
            ExploreCategoryType.NOVELS -> apiClient.novelsShelf(np, showAdultContent)
            ExploreCategoryType.BANGUMI_TV,
            ExploreCategoryType.BANGUMI_MOVIES,
            ExploreCategoryType.UPCOMING_MEDIA_TMDB -> emptyList()
        }
        ExplorePlatform.AniList -> when (categoryType) {
            ExploreCategoryType.TOP_RATED_ANIME -> apiClient.aniListTopRated(com.kitsugi.animelist.model.MediaType.Anime, np, showAdultContent)
            ExploreCategoryType.TOP_RATED_MANGA -> apiClient.aniListTopRated(com.kitsugi.animelist.model.MediaType.Manga, np, showAdultContent)
            ExploreCategoryType.TOP_ANIME -> apiClient.aniListTopAnime(np, showAdultContent)
            ExploreCategoryType.TRENDING_ANIME -> apiClient.aniListTrendingAnime(np, showAdultContent)
            ExploreCategoryType.AIRING_ANIME -> apiClient.aniListAiringAnime(np, showAdultContent)
            ExploreCategoryType.UPCOMING_ANIME -> apiClient.aniListUpcomingAnime(np, showAdultContent)
            ExploreCategoryType.MOVIE_ANIME -> apiClient.aniListMovieAnime(np, showAdultContent)
            ExploreCategoryType.SEASONAL_ANIME -> apiClient.aniListSeasonalAnime(np, showAdultContent, seasonalYear, seasonalSeason, seasonalSort)
            ExploreCategoryType.TOP_MANGA -> apiClient.aniListTopManga(np, showAdultContent)
            ExploreCategoryType.PUBLISHING_MANGA -> apiClient.aniListPublishingManga(np, showAdultContent)
            ExploreCategoryType.TRENDING_MANGA -> apiClient.aniListTrendingManga(np, showAdultContent)
            ExploreCategoryType.NEWLY_ADDED_ANIME -> apiClient.aniListNewlyAddedAnime(np, showAdultContent)
            ExploreCategoryType.NEWLY_ADDED_MANGA -> apiClient.aniListNewlyAddedManga(np, showAdultContent)
            ExploreCategoryType.MANHWA_MANHUA -> {
                val korea = apiClient.searchAniListPaged(
                    query = "",
                    mediaType = com.kitsugi.animelist.model.MediaType.Manga,
                    showAdultContent = showAdultContent,
                    formats = listOf("MANGA"),
                    country = "KR",
                    sort = listOf("POPULARITY_DESC"),
                    page = np,
                    perPage = 20
                ).results
                val china = apiClient.searchAniListPaged(
                    query = "",
                    mediaType = com.kitsugi.animelist.model.MediaType.Manga,
                    showAdultContent = showAdultContent,
                    formats = listOf("MANGA"),
                    country = "CN",
                    sort = listOf("POPULARITY_DESC"),
                    page = np,
                    perPage = 20
                ).results
                (korea + china).distinctBy { it.exploreIdentity() }
            }
            ExploreCategoryType.NOVELS -> apiClient.aniListNovels(np, showAdultContent)
            ExploreCategoryType.BANGUMI_TV,
            ExploreCategoryType.BANGUMI_MOVIES,
            ExploreCategoryType.UPCOMING_MEDIA_TMDB -> emptyList()
        }
        ExplorePlatform.TMDB -> {
            if (title.startsWith("İzlemeye Devam") || title.startsWith("Planladıklarım")) emptyList()
            else when (categoryType) {
                ExploreCategoryType.TOP_ANIME -> tmdbApiClient.getTrendingAll(np)
                ExploreCategoryType.TRENDING_ANIME -> tmdbApiClient.getTrendingMedia(np)
                ExploreCategoryType.AIRING_ANIME -> tmdbApiClient.getTrendingShows(np)
                ExploreCategoryType.MOVIE_ANIME -> tmdbApiClient.getTrendingMovies(np)
                ExploreCategoryType.UPCOMING_ANIME -> tmdbApiClient.getPopularMovies(np)
                ExploreCategoryType.TOP_MANGA -> tmdbApiClient.getPopularShows(np)
                ExploreCategoryType.PUBLISHING_MANGA -> tmdbApiClient.getTopRatedMovies(np)
                ExploreCategoryType.SEASONAL_ANIME -> tmdbApiClient.getTopRatedShows(np)
                ExploreCategoryType.TRENDING_MANGA -> tmdbApiClient.getTopRatedAnime(np)
                ExploreCategoryType.NEWLY_ADDED_ANIME -> tmdbApiClient.getPopularMedia(np)
                ExploreCategoryType.UPCOMING_MEDIA_TMDB -> tmdbApiClient.getUpcomingMedia(np)
                else -> emptyList()
            }
        }
        ExplorePlatform.KITSU -> when (categoryType) {
            ExploreCategoryType.TOP_RATED_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.topRatedAnime(20, offset = (np - 1) * 20)
            ExploreCategoryType.TOP_RATED_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.topRatedManga(20, offset = (np - 1) * 20)
            ExploreCategoryType.TRENDING_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.trendingAnime(20, offset = (np - 1) * 20)
            ExploreCategoryType.MOVIE_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.movieAnime(20, offset = (np - 1) * 20)
            ExploreCategoryType.SEASONAL_ANIME -> fetchSeasonalPage(np)
            ExploreCategoryType.TOP_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.topAnime(20, offset = (np - 1) * 20)
            ExploreCategoryType.AIRING_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.airingAnime(20, offset = (np - 1) * 20)
            ExploreCategoryType.UPCOMING_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.upcomingAnime(20, offset = (np - 1) * 20)
            ExploreCategoryType.TOP_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.topManga(20, offset = (np - 1) * 20)
            ExploreCategoryType.PUBLISHING_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.publishingManga(20, offset = (np - 1) * 20)
            ExploreCategoryType.TRENDING_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.trendingManga(20, offset = (np - 1) * 20)
            ExploreCategoryType.NEWLY_ADDED_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.newlyAddedAnime(20, offset = (np - 1) * 20)
            ExploreCategoryType.NEWLY_ADDED_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.newlyAddedManga(20, offset = (np - 1) * 20)
            ExploreCategoryType.MANHWA_MANHUA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.manhwaManhua(20, offset = (np - 1) * 20)
            ExploreCategoryType.NOVELS -> com.kitsugi.animelist.data.remote.KitsuExploreClient.novelsShelf(20, offset = (np - 1) * 20)
            else -> emptyList()
        }
        ExplorePlatform.SHIKIMORI -> when (categoryType) {
            ExploreCategoryType.TRENDING_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.trendingAnime(limit = 20, page = np, censored = !showAdultContent)
            ExploreCategoryType.MOVIE_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.movieAnime(limit = 20, page = np, censored = !showAdultContent)
            ExploreCategoryType.SEASONAL_ANIME -> fetchSeasonalPage(np)
            ExploreCategoryType.TRENDING_MANGA -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, order = "popularity", page = np, limit = 20, censored = !showAdultContent)
            ExploreCategoryType.TOP_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, order = "ranked", page = np, limit = 20, censored = !showAdultContent)
            ExploreCategoryType.NEWLY_ADDED_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, order = "id_desc", page = np, limit = 20, censored = !showAdultContent)
            ExploreCategoryType.NEWLY_ADDED_MANGA -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, order = "id_desc", page = np, limit = 20, censored = !showAdultContent)
            ExploreCategoryType.AIRING_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.airingAnime(limit = 20, page = np, censored = !showAdultContent)
            ExploreCategoryType.UPCOMING_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, statuses = listOf("anons"), order = "popularity", page = np, limit = 20, censored = !showAdultContent)
            ExploreCategoryType.TOP_MANGA -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, order = "ranked", page = np, limit = 20, censored = !showAdultContent)
            ExploreCategoryType.PUBLISHING_MANGA -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, statuses = listOf("ongoing"), order = "popularity", page = np, limit = 20, censored = !showAdultContent)
            ExploreCategoryType.MANHWA_MANHUA -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, kinds = listOf("manhwa", "manhua"), order = "popularity", page = np, limit = 20, censored = !showAdultContent)
            ExploreCategoryType.NOVELS -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, kinds = listOf("light_novel", "novel"), order = "popularity", page = np, limit = 20, censored = !showAdultContent)
            else -> emptyList()
        }
        ExplorePlatform.BANGUMI -> bangumiPage(np)
        ExplorePlatform.SIMKL -> fetchSimklPage(np)
        else -> emptyList()
    }

    /**
     * Sayfa isteği. Kaynak API'si seçilen tür/sıralamayı destekliyorsa filtre doğrudan
     * kaynağa gönderilir ([fetchFilteredExplorePage]); aksi halde kategorinin kendi ucuna
     * düşülür ve filtre yalnızca cihazda uygulanır.
     */
    suspend fun fetchExplorePage(np: Int): List<JikanSearchResult> {
        if (!serverFilterActive) return baseExplorePage(np)
        val filtered = fetchFilteredExplorePage(
            platform = platform,
            categoryType = categoryType,
            // Sunucuda karşılığı olmayan seçenek yerelde uygulanacağı için buraya gönderilmez.
            genreId = if (serverGenreSupported) appliedGenreId else "ALL",
            sort = if (serverSortSupported) appliedSortOption else KitsugiGridSortOption.DEFAULT,
            page = np,
            showAdultContent = showAdultContent,
            clients = filterClients,
            seasonalSeason = if (isSeasonalAnime) seasonalSeason else null,
            seasonalYear = if (isSeasonalAnime) seasonalYear else null,
            seasonalSort = if (isSeasonalAnime) seasonalSort else null
        )
        // Filtresiz kategori sayfasına DÜŞÜLMEZ: aksi halde tür süzgeci uygulanmış bir listede
        // filtresiz kayıtlar görünürdü. Boş sayfa = "bu filtrede sonuç yok" ve sayfalama durur.
        return filtered
    }

    fun loadNextPage() {
        if (isLoadingMore || !hasMorePages) return
        isLoadingMore = true; loadError = null
        val generation = filterGeneration
        scope.launch {
            try {
                val isInitialSimklPage = platform == ExplorePlatform.SIMKL && !simklPageInitialized
                val np = if (isInitialSimklPage) 1 else currentPage + 1
                val newItems = fetchExplorePage(np)
                // Kullanıcı bu istek uçuştayken filtreyi değiştirdiyse sonucu yazma.
                if (generation != filterGeneration) return@launch
                if (isInitialSimklPage) {
                    if (newItems.isEmpty()) {
                        // Keep the already-visible chart data and allow the page-one request to be retried.
                        loadError = "Simkl listesinin ilk sayfası alınamadı. Tekrar deneyin."
                    } else {
                        loadedResults = newItems
                        currentPage = 1
                        simklPageInitialized = true
                        hasMorePages = simklCategoryHasPagination &&
                            newItems.size >= SIMKL_EXPLORE_PAGE_SIZE &&
                            (simklCategoryUsesFiniteChart || 1 < SIMKL_MAX_API_PAGE)
                    }
                } else {
                    val existingKeys = loadedResults.map { it.exploreIdentity() }.toHashSet()
                    val uniqueItems = newItems.filter { existingKeys.add(it.exploreIdentity()) }
                    if (uniqueItems.isNotEmpty()) {
                        loadedResults = loadedResults + uniqueItems
                        currentPage = np
                    }
                    // Sayfa boş ya da tamamı kopyaysa liste sona ermiş kabul edilir.
                    // (Kitsu gibi sayfalamayı yok sayan uçlar burada durur; sonsuz döngü olmaz.)
                    if (uniqueItems.isEmpty()) hasMorePages = false
                    if (platform == ExplorePlatform.SIMKL && simklCategoryHasPagination &&
                        (newItems.size < SIMKL_EXPLORE_PAGE_SIZE ||
                            (!simklCategoryUsesFiniteChart && np >= SIMKL_MAX_API_PAGE))
                    ) {
                        hasMorePages = false
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == filterGeneration) loadError = e.message ?: "Yükleme hatası"
            } finally {
                // Filtre değiştiyse yeni yükleme durumu o yönetir; eskisi elleçlemesin.
                if (generation == filterGeneration) isLoadingMore = false
            }
        }
    }

    LaunchedEffect(platform, categoryType, title, isSimklPersonalList) {
        if (!isSimklPersonalList && (platform == ExplorePlatform.SIMKL || loadedResults.isEmpty())) {
            loadNextPage()
        }
    }

    /**
     * Mevsimsel filtre sayfası uygulaması. Yıl/mevsim değişince liste baştan (1. sayfadan)
     * kaynaktan çekilir; tür süzgeci de seçiliyse aynı istekte kaynağa gönderilir.
     */
    fun applySeasonalFilter(season: String, year: Int, sort: String) {
        seasonalSeason = season; seasonalYear = year; seasonalSort = sort
        forceFreshFetch = true
        filterGeneration += 1
    }

    // Yerel (cihazda) filtre/sıralama YALNIZCA kaynağın uygulayamadığı seçenekler için çalışır:
    //  - Sunucu tarafında süzülmüş sayfalarda aynı türü bir de anahtar kelimeyle elemek, kaynağın
    //    döndürdüğü doğru kayıtları (ör. Çince etiketli Bangumi kayıtlarını) budayabilir.
    //  - Sunucu sıralaması etkinken listeyi yeniden dizmek, sayfa 2 geldiğinde sırayı bozarak
    //    "kaydırdıkça sıralama değişiyor" hatasını üretirdi.
    val displayedResults = remember(
        loadedResults,
        showAdultContent,
        selectedGenreId,
        selectedSortOption,
        localGenreFilter,
        localSort
    ) {
        val base = loadedResults.filter { showAdultContent || !it.isAdult }
        val filtered = if (!localGenreFilter || selectedGenreId == "ALL") {
            base
        } else {
            val genreObj = KITSUGI_MEDIA_GENRES.firstOrNull { it.id == selectedGenreId }
            if (genreObj != null) {
                // Bangumi kayıtlarının `genres` alanı **Çince/Japonca wiki etiketleridir**
                // (`恋爱`, `机战`…); Türkçe/İngilizce anahtar kelimelerle karşılaştırma hiç
                // eşleşmediği için bu kaynakta "📱 yerel" tür süzgeci **boş ızgara**
                // üretiyordu. Terim sözlükten kanonik Bangumi yazımına da çevrilip aranır.
                val nativeTerm = com.kitsugi.animelist.utils.BangumiTagDictionary
                    .canonicalTagFor(genreObj.keywords.firstOrNull().orEmpty())
                base.filter { item ->
                    val inGenres = item.genres.any { g ->
                        genreObj.keywords.any { kw -> g.contains(kw, ignoreCase = true) } ||
                            (nativeTerm != null && g.contains(nativeTerm, ignoreCase = true))
                    }
                    val inSubtitle = genreObj.keywords.any { kw ->
                        item.subtitle.contains(kw, ignoreCase = true)
                    }
                    inGenres || inSubtitle
                }
            } else {
                base
            }
        }

        if (!localSort) return@remember filtered

        when (selectedSortOption) {
            KitsugiGridSortOption.DEFAULT -> filtered
            KitsugiGridSortOption.SCORE_DESC -> filtered.sortedByDescending {
                it.rawScoreDouble ?: (it.score?.toDouble()?.div(10.0)) ?: 0.0
            }
            KitsugiGridSortOption.SCORE_ASC -> filtered.sortedWith(
                compareBy {
                    val s = it.rawScoreDouble ?: (it.score?.toDouble()?.div(10.0)) ?: 0.0
                    if (s <= 0.0) 999.0 else s
                }
            )
            KitsugiGridSortOption.POPULARITY_DESC -> filtered.sortedByDescending {
                it.members ?: it.favorites ?: 0
            }
            KitsugiGridSortOption.YEAR_DESC -> filtered.sortedByDescending {
                it.year ?: 0
            }
            KitsugiGridSortOption.YEAR_ASC -> filtered.sortedWith(
                compareBy {
                    val y = it.year ?: 0
                    if (y <= 0) 9999 else y
                }
            )
            KitsugiGridSortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            KitsugiGridSortOption.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
        }
    }

    // Liste scroll state + auto-load trigger.
    // FIX: rememberSaveable + Saver kullanımı Bundle'a yazıp geri okurken hızlı scroll
    // sırasında layoutInfo henüz güncellenmemişken eski index'i restore edip
    // "iç içe geçme" ve zıplama yapıyordu. rememberLazyListState / rememberLazyGridState
    // aynı Saver'ı kullanır ama Bundle'a yazmadan compose hafızasında tutar, daha stabil.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val shouldLoadMoreList by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            // totalItemsCount 0 iken tetiklenmesin
            if (listState.layoutInfo.totalItemsCount == 0) return@derivedStateOf false
            last.index >= listState.layoutInfo.totalItemsCount - 4
        }
    }

    // Grid scroll state + auto-load trigger
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val shouldLoadMoreGrid by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            if (gridState.layoutInfo.totalItemsCount == 0) return@derivedStateOf false
            last.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    // ── Filtre uygulama ve kaynaktan yeniden çekme ────────────────────────────
    /**
     * "Filtre ve Sıralama" sayfasından gelen seçimi uygular. Seçim kaynağın API'sinde
     * karşılığı olan bir tür/sıralama ise liste 1. sayfadan itibaren filtreli çekilir;
     * aksi halde eski davranış korunur (cihazda süzme/sıralama).
     */
    fun applyExploreFilter(genreId: String, sortOption: KitsugiGridSortOption) {
        selectedGenreId = genreId
        selectedSortOption = sortOption
        if (genreId == appliedGenreId && sortOption == appliedSortOption) return
        appliedGenreId = genreId
        appliedSortOption = sortOption
        filterGeneration += 1
    }

    suspend fun scrollToTopOfGrid() {
        runCatching {
            if (isGridView) gridState.scrollToItem(0) else listState.scrollToItem(0)
        }
    }

    /**
     * Uygulanan filtre/sıralama değiştiğinde listeyi yeniden kurar.
     * [forceFreshFetch] mevsim/yıl gibi kaynak parametrelerinin de değiştiği durumlarda
     * "tohum listeye dön" kısayolunu atlayıp sayfayı baştan çeker.
     */
    suspend fun reloadForActiveFilter(generation: Int, forceFreshFetch: Boolean) {
        if (generation != filterGeneration) return

        if (!forceFreshFetch && !serverFilterActive) {
            // Filtre yalnız cihazda uygulanıyor: başlangıç listesine dönmek yeterli, ağa çıkılmaz.
            loadedResults = seedResults
            currentPage = session?.cachedPage ?: if (seedResults.isEmpty()) 0 else 1
            hasMorePages = session?.cachedHasMore ?: !isSimklPersonalList
            loadError = null
            isLoadingMore = false
            simklTrendingResults = null
            scrollToTopOfGrid()
            return
        }

        // Liste baştan kurulur: sayfa 1, filtreli istek, kaydırma başa.
        loadedResults = emptyList()
        currentPage = 0
        hasMorePages = true
        loadError = null
        isLoadingMore = true
        simklTrendingResults = null
        simklPageInitialized = platform != ExplorePlatform.SIMKL
        try {
            val items = fetchExplorePage(1)
            if (generation != filterGeneration) return
            loadedResults = items
            currentPage = 1
            // Boş ilk sayfa = bu filtrede sonuç yok → sonsuz sağlamaya çalışıp döngüye girme.
            hasMorePages = items.isNotEmpty()
            scrollToTopOfGrid()
        } catch (e: Exception) {
            if (generation == filterGeneration) loadError = e.message ?: "Yükleme hatası"
        } finally {
            if (generation == filterGeneration) isLoadingMore = false
        }
    }

    // Filtre/sıralama (veya mevsim) değişti → 1. sayfayı kaynaktan yeniden çek.
    LaunchedEffect(filterGeneration) {
        if (filterGeneration == 0) return@LaunchedEffect
        val force = forceFreshFetch
        forceFreshFetch = false
        reloadForActiveFilter(filterGeneration, force)
    }

    // Süreç yeniden başlatıldığında `rememberSaveable` filtresini geri getirir ama liste
    // tohum hâlindedir (oturum önbelleği boş) → sunucu filtresi sayfa 1'den yeniden uygulanır.
    // Cihazda süzülen seçimlerde ağa çıkılmaz.
    LaunchedEffect(Unit) {
        val hasAppliedFilter = appliedGenreId != "ALL" ||
            appliedSortOption != KitsugiGridSortOption.DEFAULT
        if (filterGeneration == 0 && hasAppliedFilter && session?.cachedResults == null &&
            (serverGenreSupported || serverSortSupported)
        ) {
            filterGeneration += 1
        }
    }

    // ── Otomatik sayfalama emniyeti ────────────────────────────────────────────
    // ESKİ HATA: `isLoadingMore` tetikleyicinin anahtarıydı. Her sayfa yüklemesi
    // bittiğinde (true→false) efekt yeniden başlıyor, liste yerleşimi (layout) henüz
    // güncellenmediği için "sona ulaşıldı" koşulu hâlâ true okunuyor ve kullanıcı
    // kaydırmasa bile arka arkaya sayfa çekiliyordu. Filtre aktifken (görünen öğe
    // sayısı hiç artmadığında) bu döngü SONSUZA kadar sürüyordu: sayfa "döngüye
    // giriyor", API'ler art arda vuruluyor (AniList 429) ve liste/GC şişiyordu.
    // ÇÖZÜM: (1) aynı görünen öğe sayısı için ikinci kez tetiklenmez,
    //         (2) kaydırma başına en fazla AUTO_LOAD_BUDGET_PER_SCROLL sayfa çekilir,
    //         (3) kullanıcı kaydırdığında bütçe yenilenir.
    var autoLoadBudget by remember { mutableIntStateOf(AUTO_LOAD_BUDGET_PER_SCROLL) }
    var lastAutoLoadVisibleCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(selectedGenreId, selectedSortOption, seasonalSeason, seasonalYear, seasonalSort, filterGeneration) {
        autoLoadBudget = AUTO_LOAD_BUDGET_PER_SCROLL
        lastAutoLoadVisibleCount = -1
    }

    LaunchedEffect(listState, gridState) {
        // Kompozisyonda state okumadan kaydırma hareketini izle (recomposition yok).
        snapshotFlow {
            listOf(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                gridState.firstVisibleItemIndex,
                gridState.firstVisibleItemScrollOffset
            )
        }.collect {
            // Kullanıcı kaydırdı → bütçe ve "son yüklenen görünür öğe sayısı" sıfırlanır.
            // Böylece normal sonsuz kaydırma davranışı birebir korunur; yalnızca kimsenin
            // kaydırmadığı durumda arka arkaya istek atan kaçak döngü engellenir.
            if (autoLoadBudget != AUTO_LOAD_BUDGET_PER_SCROLL || lastAutoLoadVisibleCount != -1) {
                autoLoadBudget = AUTO_LOAD_BUDGET_PER_SCROLL
                lastAutoLoadVisibleCount = -1
            }
        }
    }

    LaunchedEffect(shouldLoadMoreList, isLoadingMore, hasMorePages, isGridView, autoLoadBudget, displayedResults.size) {
        if (!isGridView && shouldLoadMoreList && !isLoadingMore && hasMorePages) {
            val visibleCount = displayedResults.size
            if (autoLoadBudget > 0 && visibleCount != lastAutoLoadVisibleCount) {
                autoLoadBudget -= 1
                lastAutoLoadVisibleCount = visibleCount
                loadNextPage()
            }
        }
    }

    LaunchedEffect(shouldLoadMoreGrid, isLoadingMore, hasMorePages, isGridView, autoLoadBudget, displayedResults.size) {
        if (isGridView && shouldLoadMoreGrid && !isLoadingMore && hasMorePages) {
            val visibleCount = displayedResults.size
            if (autoLoadBudget > 0 && visibleCount != lastAutoLoadVisibleCount) {
                autoLoadBudget -= 1
                lastAutoLoadVisibleCount = visibleCount
                loadNextPage()
            }
        }
    }

    // Index'i kompozisyonda çıplak okumak, sınır geçişinde tüm ızgarayı
    // (yüzlerce kart) yeniden kurar ve kaydırma konumunu zıplatır.
    val showFloatingHeader by remember(isGridView) {
        derivedStateOf {
            if (isGridView) gridState.firstVisibleItemIndex >= 1
            else listState.firstVisibleItemIndex >= 1
        }
    }

    val showScrollToTop by remember(isGridView) {
        derivedStateOf {
            if (isGridView) {
                gridState.firstVisibleItemIndex > 3
            } else {
                listState.firstVisibleItemIndex > 3
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(KitsugiColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {

        if (isGridView) {
            // ── GRID MODU ────────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = gridState,
                modifier = if (isTv) Modifier.width(960.dp) else Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Başlık - tam genişlik span
                item(key = "grid_header", span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                        // Geri butonu
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onBackClick) {
                                Text(stringResource(R.string.geri), color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        // Başlık + toggle + filtre
                        val hasActiveFilter = selectedGenreId != "ALL" || selectedSortOption != KitsugiGridSortOption.DEFAULT
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dynamicTitle,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            // Toplu ekleme kısayolu — her iki görünümde de başlıkta (kompakt)
                            if (onAddAllToList != null) {
                                IconButton(
                                    onClick = { onAddAllToList?.invoke(displayedResults) },
                                    enabled = displayedResults.isNotEmpty(),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(KitsugiColors.Surface)
                                ) {
                                    Icon(
                                        Icons.PlaylistAdd,
                                        contentDescription = stringResource(R.string.tumunu_listeye_ekle),
                                        tint = accentColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            // Filtre ve Sıralama Butonu (Her kategoride aktif)
                            IconButton(
                                onClick = {
                                    if (isSeasonalAnime) {
                                        showFilterBottomSheet = true
                                    } else {
                                        showGeneralFilterBottomSheet = true
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (hasActiveFilter) accentColor.copy(alpha = 0.25f) else KitsugiColors.Surface)
                            ) {
                                Icon(
                                    Icons.FilterList,
                                    contentDescription = stringResource(R.string.filtre_ve_siralama),
                                    tint = if (hasActiveFilter) accentColor else KitsugiColors.TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Grid→Liste toggle
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val gridIndex = gridState.firstVisibleItemIndex
                                        val gridOffset = gridState.firstVisibleItemScrollOffset
                                        val listIndex = if (gridIndex == 0) 0 else gridIndex + 1
                                        isGridView = false
                                        kotlinx.coroutines.delay(10)
                                        listState.scrollToItem(listIndex, gridOffset)
                                    }
                                },
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitsugiColors.Surface)
                            ) {
                                Icon(Icons.ListAlt, contentDescription = KitsugiStrings.get(R.string.settings_list_layout), tint = accentColor)
                            }
                        }

                        // Nested LazyRow kaydırma sırasında ana ızgarayı yeniden ölçüyordu.
                        GridGenreChipRow(
                            selectedGenreId = selectedGenreId,
                            accentColor = accentColor,
                            onGenreClick = { genreId, isSelected ->
                                // Çipe dokunmak filtreyi hemen uygular; tür kaynağın API'sinde
                                // varsa liste o türle baştan çekilir.
                                applyExploreFilter(
                                    if (isSelected && genreId != "ALL") "ALL" else genreId,
                                    selectedSortOption
                                )
                            }
                        )

                        val activeGenre = KITSUGI_MEDIA_GENRES.firstOrNull { it.id == selectedGenreId }
                        val activeGenreLabel = activeGenre?.takeIf { it.id != "ALL" }?.displayLabel()
                        val activeSortLabel = if (selectedSortOption != KitsugiGridSortOption.DEFAULT) selectedSortOption.displayLabel() else null
                        val activeFilterDesc = buildString {
                            activeGenreLabel?.let { append(" • $it") }
                            activeSortLabel?.let { append(" • $it") }
                            // Sunucu/cihaz ayrımı emoji ile değil, ayrı ikon rozetleriyle gösterilir.
                            if (serverFilterActive) append(" • ${stringResource(R.string.filter_scope_server)}")
                            else if (localGenreFilter || localSort) append(" • ${stringResource(R.string.filter_scope_device)}")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.displayedresults_size_icerik_activefilterdes, displayedResults.size, activeFilterDesc),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasActiveFilter) {
                                Text(
                                    text = stringResource(R.string.temizle),
                                    color = accentColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        applyExploreFilter("ALL", KitsugiGridSortOption.DEFAULT)
                                    }
                                )
                            }
                        }
                    }
                }

                if (displayedResults.isEmpty() && !isLoadingMore) {
                    item(key = "grid_empty", span = { GridItemSpan(maxLineSpan) }) {
                        KitsugiEmptyState(
                            title = stringResource(R.string.henuz_icerik_yok),
                            subtitle = stringResource(R.string.bu_kategoride_gosterilecek_icerik_bulunamadi),
                            icon = Icons.SearchOff
                        )
                    }
                } else {
                    itemsIndexed(
                        items = displayedResults,
                        // PERF: key'de index kullanmak her filtreleme/sıralama/ekleme
                        // işleminde TÜM item'ların key'ini bozar ve Compose'un tüm
                        // kartları yeniden oluşturmasına (recomposition + rebind)
                        // sebep olur. Sabit identity kullanarak reuse maksimize edilir.
                        key = { idx, item -> item.kitsugiLazyKey("grid", idx) },
                        contentType = { _, _ -> "media_card" }
                    ) { index, result ->
                        val showRank = !isSeasonalAnime
                        KitsugiExploreMediaCard(
                            result = result,
                            alreadyInList = alreadyInList(result),
                            mediaEntry = getMediaEntry(result),
                            onClick = { onItemClick(result) },
                            titleLanguage = titleLanguage,
                            scoreFormat = scoreFormat,
                            hideScores = hideScores,
                            blurAdultMedia = blurAdultMedia,
                            forceVertical = !isLandscape,
                            rankIndex = if (showRank) index + 1 else null,
                            onQuickAddClick = onQuickAddToList?.let { quickAdd ->
                                { quickAdd(result) }
                            }
                        )
                    }
                }

                if (isLoadingMore) {
                    item(key = "grid_loading", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = accentColor) }
                    }
                }

                if (loadError != null) {
                    item(key = "grid_error", span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(loadError ?: KitsugiStrings.get(R.string.ui_error_generic_period), color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { loadNextPage() }) {
                                Text("Tekrar Dene", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

        } else {
            // ── LİSTE MODU ───────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = if (isTv) Modifier.width(960.dp) else Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "list_header") {
                    Column {
                        Spacer(modifier = Modifier.height(28.dp))
                        // Geri butonu
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onBackClick) {
                                Text(stringResource(R.string.geri), color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        // Başlık + toggle + filtre
                        val hasActiveFilter = selectedGenreId != "ALL" || selectedSortOption != KitsugiGridSortOption.DEFAULT
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dynamicTitle,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            // Toplu ekleme kısayolu — her iki görünümde de başlıkta (kompakt)
                            if (onAddAllToList != null) {
                                IconButton(
                                    onClick = { onAddAllToList?.invoke(displayedResults) },
                                    enabled = displayedResults.isNotEmpty(),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(KitsugiColors.Surface)
                                ) {
                                    Icon(
                                        Icons.PlaylistAdd,
                                        contentDescription = stringResource(R.string.tumunu_listeye_ekle),
                                        tint = accentColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            // Filtre ve Sıralama Butonu (Her kategoride aktif)
                            IconButton(
                                onClick = {
                                    if (isSeasonalAnime) {
                                        showFilterBottomSheet = true
                                    } else {
                                        showGeneralFilterBottomSheet = true
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (hasActiveFilter) accentColor.copy(alpha = 0.25f) else KitsugiColors.Surface)
                            ) {
                                Icon(
                                    Icons.FilterList,
                                    contentDescription = stringResource(R.string.filtre_ve_siralama),
                                    tint = if (hasActiveFilter) accentColor else KitsugiColors.TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Liste→Grid toggle
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val listIndex = listState.firstVisibleItemIndex
                                        val listOffset = listState.firstVisibleItemScrollOffset
                                        val gridIndex = if (listIndex <= 1) 0 else listIndex - 1
                                        isGridView = true
                                        kotlinx.coroutines.delay(10)
                                        gridState.scrollToItem(gridIndex, listOffset)
                                    }
                                },
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitsugiColors.Surface)
                            ) {
                                Icon(Icons.GridView, contentDescription = KitsugiStrings.get(R.string.cd_grid_view), tint = accentColor)
                            }
                        }

                        // Nested LazyRow kaydırma sırasında ana ızgarayı yeniden ölçüyordu.
                        GridGenreChipRow(
                            selectedGenreId = selectedGenreId,
                            accentColor = accentColor,
                            onGenreClick = { genreId, isSelected ->
                                // Çipe dokunmak filtreyi hemen uygular; tür kaynağın API'sinde
                                // varsa liste o türle baştan çekilir.
                                applyExploreFilter(
                                    if (isSelected && genreId != "ALL") "ALL" else genreId,
                                    selectedSortOption
                                )
                            }
                        )
                    }
                }

                item(key = "list_count") {
                    val hasActiveFilter = selectedGenreId != "ALL" || selectedSortOption != KitsugiGridSortOption.DEFAULT
                    val activeGenre = KITSUGI_MEDIA_GENRES.firstOrNull { it.id == selectedGenreId }
                    val activeGenreLabel = activeGenre?.takeIf { it.id != "ALL" }?.displayLabel()
                    val activeSortLabel = if (selectedSortOption != KitsugiGridSortOption.DEFAULT) selectedSortOption.displayLabel() else null
                    val activeFilterDesc = buildString {
                        activeGenreLabel?.let { append(" • $it") }
                        activeSortLabel?.let { append(" • $it") }
                        // Sunucu/cihaz ayrımı emoji ile değil, metin etiketiyle gösterilir.
                        if (serverFilterActive) append(" • ${stringResource(R.string.filter_scope_server)}")
                        else if (localGenreFilter || localSort) append(" • ${stringResource(R.string.filter_scope_device)}")
                    }

                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.displayedresults_size_icerik_activefilterdes, displayedResults.size, activeFilterDesc),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasActiveFilter) {
                                Text(
                                    text = stringResource(R.string.temizle),
                                    color = accentColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        applyExploreFilter("ALL", KitsugiGridSortOption.DEFAULT)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (displayedResults.isEmpty() && !isLoadingMore) {
                    item(key = "list_empty") {
                        KitsugiEmptyState(
                            title = stringResource(R.string.henuz_icerik_yok),
                            subtitle = stringResource(R.string.bu_kategoride_gosterilecek_icerik_bulunamadi),
                            icon = Icons.SearchOff
                        )
                    }
                } else {
                    itemsIndexed(
                        items = displayedResults,
                        // PERF: identity-based key (index yok)
                        key = { idx, item -> item.kitsugiLazyKey("list", idx) },
                        contentType = { _, _ -> if (isSeasonalAnime) "seasonal_card" else "ranking_card" }
                    ) { index, result ->
                        if (isSeasonalAnime) {
                            DetailedSeasonalMediaCard(
                                result = result,
                                alreadyInList = alreadyInList(result),
                                onClick = { onItemClick(result) },
                                titleLanguage = titleLanguage,
                                blurAdultMedia = blurAdultMedia,
                                onQuickAddClick = onQuickAddToList?.let { quickAdd ->
                                    { quickAdd(result) }
                                }
                            )
                        } else {
                            KitsugiRankingMediaCard(
                                result = result,
                                rankIndex = index + 1,
                                alreadyInList = alreadyInList(result),
                                mediaEntry = getMediaEntry(result),
                                onClick = { onItemClick(result) },
                                titleLanguage = titleLanguage,
                                hideScores = hideScores,
                                blurAdultMedia = blurAdultMedia,
                                onQuickAddClick = onQuickAddToList?.let { quickAdd ->
                                    { quickAdd(result) }
                                }
                            )
                        }
                    }
                }

                if (isLoadingMore) {
                    item(key = "list_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = accentColor) }
                    }
                }

                if (loadError != null) {
                    item(key = "list_error") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(loadError ?: KitsugiStrings.get(R.string.ui_error_generic_period), color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { loadNextPage() }) {
                                Text("Tekrar Dene", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Overlay Header ───────────────────────────────────────────
        // Yükseklik animasyonu ızgaranın viewport'unu her karede değiştirip
        // kaydırmayı zıplatıyordu. Overlay sabit yükseklikte, sadece solar.
        AnimatedVisibility(
            visible = showFloatingHeader,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = (if (isTv) Modifier.width(960.dp) else Modifier.fillMaxWidth())
                    .height(64.dp)
                    .background(KitsugiColors.Surface.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.ArrowBack, contentDescription = stringResource(R.string.geri), tint = KitsugiColors.TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dynamicTitle,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Toplu ekleme kısayolu — kaydırırken de erişilebilir (şeffaf zemin)
                if (onAddAllToList != null) {
                    IconButton(
                        onClick = { onAddAllToList?.invoke(displayedResults) },
                        enabled = displayedResults.isNotEmpty(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Transparent)
                    ) {
                        Icon(
                            Icons.PlaylistAdd,
                            contentDescription = stringResource(R.string.tumunu_listeye_ekle),
                            tint = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                // Filtre ve Sıralama Butonu (Her zaman toggle'dan önce gösterilir)
                val hasActiveFilterFloating = selectedGenreId != "ALL" || selectedSortOption != KitsugiGridSortOption.DEFAULT
                IconButton(
                    onClick = {
                        if (isSeasonalAnime) {
                            showFilterBottomSheet = true
                        } else {
                            showGeneralFilterBottomSheet = true
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hasActiveFilterFloating) accentColor.copy(alpha = 0.25f) else Color.Transparent)
                ) {
                    Icon(
                        Icons.FilterList,
                        contentDescription = stringResource(R.string.filtre_ve_siralama),
                        tint = if (hasActiveFilterFloating) accentColor else KitsugiColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Toggle – floating header'da da göster
                IconButton(onClick = {
                    scope.launch {
                        if (isGridView) {
                            val gridIndex = gridState.firstVisibleItemIndex
                            val gridOffset = gridState.firstVisibleItemScrollOffset
                            val listIndex = if (gridIndex == 0) 0 else gridIndex + 1
                            isGridView = false
                            kotlinx.coroutines.delay(10)
                            listState.scrollToItem(listIndex, gridOffset)
                        } else {
                            val listIndex = listState.firstVisibleItemIndex
                            val listOffset = listState.firstVisibleItemScrollOffset
                            val gridIndex = if (listIndex <= 1) 0 else listIndex - 1
                            isGridView = true
                            kotlinx.coroutines.delay(10)
                            gridState.scrollToItem(gridIndex, listOffset)
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (isGridView) Icons.ListAlt else Icons.GridView,
                        contentDescription = if (isGridView) KitsugiStrings.get(R.string.liste) else "Grid",
                        tint = accentColor
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor)
                    .tvClickable(shape = RoundedCornerShape(16.dp)) {
                        scope.launch {
                            if (isGridView) {
                                gridState.animateScrollToItem(0)
                            } else {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.yukari_git),
                    tint = KitsugiColors.Background,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (showFilterBottomSheet) {
            KitsugiSeasonalFilterBottomSheet(
                initialSeason = seasonalSeason,
                initialYear = seasonalYear,
                initialSort = seasonalSort,
                onDismissRequest = { showFilterBottomSheet = false },
                onApply = { s, y, sort -> applySeasonalFilter(s, y, sort) }
            )
        }

        if (showGeneralFilterBottomSheet) {
            KitsugiMediaFilterBottomSheet(
                initialGenreId = selectedGenreId,
                initialSortOption = selectedSortOption,
                // Kaynakta uygulanamayan çipler sayfada "📱 yerel" olarak işaretlenir; kullanıcı
                // hangi filtrenin API'ye gittiğini, hangisinin cihazda süzüldüğünü görür.
                sourceLabel = if (isPersonalList) null else platform.localizedLabel(),
                serverGenreIds = if (isPersonalList) emptySet()
                    else ExploreSourceFilters.serverGenreIds(platform, categoryType),
                serverSorts = if (isPersonalList) emptySet()
                    else ExploreSourceFilters.serverSorts(platform, categoryType, appliedGenreId),
                onDismissRequest = { showGeneralFilterBottomSheet = false },
                onApply = { genreId, sortOption -> applyExploreFilter(genreId, sortOption) },
                onReset = { applyExploreFilter("ALL", KitsugiGridSortOption.DEFAULT) }
            )
        }
    }
}

@Composable
private fun GridGenreChipRow(
    selectedGenreId: String,
    accentColor: Color,
    onGenreClick: (genreId: String, isSelected: Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        KITSUGI_MEDIA_GENRES.forEach { genre ->
            val isSelected = selectedGenreId == genre.id
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.22f)
                        else KitsugiColors.Surface
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) accentColor else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onGenreClick(genre.id, isSelected) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                KitsugiEmojiLabel(
                    label = genre.displayLabel(),
                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium,
                    iconSize = 13.dp,
                    icon = genre.icon
                )
            }
        }
    }
}