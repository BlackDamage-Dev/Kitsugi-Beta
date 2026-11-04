@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.ui.theme.gradient.FloatingActionButton
import com.kitsugi.animelist.ui.components.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.kitsugi.animelist.ui.components.KitsugiPullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.normalizeAiringTitle
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiHeroSection
import com.kitsugi.animelist.ui.components.isHomeHeroMedia
import com.kitsugi.animelist.ui.components.KitsugiShimmerHeroSection
import com.kitsugi.animelist.ui.components.KitsugiErrorState
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.components.kitsugiCardFrame
import com.kitsugi.animelist.ui.components.KitsugiTopAction
import com.kitsugi.animelist.ui.components.KitsugiTopActionRow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec

/**
 * Keşfet türev verileri (forSource kopyaları + distinctBy kimlik dizeleri + başlık
 * normalizasyonu) kaynak durumları her değiştiğinde doğrudan kompozisyon içinde
 * hesaplanırsa — "Tümü" modunda her kaynak tamamlandığında — ana thread yükleme
 * sırasında anlık bloke olur; bu da kullanıcının gördüğü kasma/donma/lag'dir.
 * Hesaplama Dispatchers.Default'a taşınır; ana thread yalnızca hazır sonucu
 * state'e yazar. İlk kompozisyon boş durumla bilindiğinden pahalı değildir.
 */
@Composable
private fun <T> rememberExploreDerived(
    key1: Any?,
    key2: Any?,
    compute: () -> T
): T {
    var value by remember { mutableStateOf(compute()) }
    LaunchedEffect(key1, key2) {
        value = withContext(Dispatchers.Default) { compute() }
    }
    return value
}

@Composable
fun ExploreScreen(
    currentEntries: List<MediaEntry>,
    showAdultContent: Boolean,
    onAddSelectionToList: (ApiSearchSelection) -> Unit,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>, platform: ExplorePlatform) -> Unit,
    onNavigateToWatchHistory: () -> Unit = {},
    onNavigateToDownloads: (() -> Unit)? = null,
    onOpenApiDetail: (JikanSearchResult) -> Unit,
    onEditEntry: (MediaEntry) -> Unit,
    onOpenMangaReader: () -> Unit = {},
    onOpenAiringCalendar: () -> Unit = {},
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollPositionChange: (index: Int, offset: Int) -> Unit = { _, _ -> },
    viewModel: ExploreViewModel = viewModel(),
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    showAnimeLogos: Boolean = false,
    isSimklConnected: Boolean = false,
    blurAdultMedia: Boolean = false,
    // AniHyou paritesi: romanlar ayrı raf yerine manga raflarına karışabilir
    separateNovelsManga: Boolean = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.separateNovelsManga,
    onOpenNotifications: () -> Unit = {},
    isNotificationsVisible: Boolean = false,
    onOpenDownloads: () -> Unit = {},
    /** Alt navigasyon barı görünüyor mu? — "Yukarı Çık" FAB'ı alt barın üstünde hizalanır. */
    isBottomBarVisible: Boolean = true,
    bottomBarScrollState: com.kitsugi.animelist.utils.ScrollVisibilityState? = null,
    /** Liste tepesine dönüldüğünde çağrılır (diğer ana sayfalarla aynı sözleşme). */
    onScrollReset: (() -> Unit)? = null,
    /** TMDB hatası — Ayarlar/Entegrasyonlar sayfasına yönlendir */
    onRedirectToSettings: (() -> Unit)? = null,
    /** AniList/MAL hatası — Giriş yap sayfasına yönlendir */
    onRedirectToAuth: (() -> Unit)? = null
) {
    val accentColor = LocalKitsugiAccent.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val onSeeAllForSelected: (String, ExploreCategoryType, List<JikanSearchResult>) -> Unit = { title, category, results ->
        onSeeAllSection(title, category, results, viewModel.selectedPlatform)
    }

    val filteredTopAnime = remember(viewModel.topAnime, showAdultContent) { viewModel.topAnime.filter { showAdultContent || !it.isAdult } }
    val filteredAiringAnime = remember(viewModel.airingAnime, showAdultContent) { viewModel.airingAnime.filter { showAdultContent || !it.isAdult } }
    val filteredUpcomingAnime = remember(viewModel.upcomingAnime, showAdultContent) { viewModel.upcomingAnime.filter { showAdultContent || !it.isAdult } }
    val filteredTopManga = remember(viewModel.topManga, showAdultContent) { viewModel.topManga.filter { showAdultContent || !it.isAdult } }
    val filteredPublishingManga = remember(viewModel.publishingManga, showAdultContent) { viewModel.publishingManga.filter { showAdultContent || !it.isAdult } }
    val filteredTrendingAnime = remember(viewModel.trendingAnime, showAdultContent) { viewModel.trendingAnime.filter { showAdultContent || !it.isAdult } }
    val filteredTopRatedAnime = remember(viewModel.topRatedAnime, showAdultContent) { viewModel.topRatedAnime.filter { showAdultContent || !it.isAdult } }
    val filteredTopRatedManga = remember(viewModel.topRatedManga, showAdultContent) { viewModel.topRatedManga.filter { showAdultContent || !it.isAdult } }
    val filteredMovieAnime = remember(viewModel.movieAnime, showAdultContent) { viewModel.movieAnime.filter { showAdultContent || !it.isAdult } }
    val filteredBangumiTvShows = remember(viewModel.bangumiTvShows, showAdultContent) { viewModel.bangumiTvShows.filter { showAdultContent || !it.isAdult } }
    val filteredBangumiMovies = remember(viewModel.bangumiMovies, showAdultContent) { viewModel.bangumiMovies.filter { showAdultContent || !it.isAdult } }
    val filteredManhwaManhua = remember(viewModel.manhwaManhua, showAdultContent) { viewModel.manhwaManhua.filter { showAdultContent || !it.isAdult } }
    val filteredNovels = remember(viewModel.novels, showAdultContent) { viewModel.novels.filter { showAdultContent || !it.isAdult } }
    val filteredSeasonalAnime = remember(viewModel.seasonalAnime, showAdultContent) { viewModel.seasonalAnime.filter { showAdultContent || !it.isAdult } }
    val filteredTrendingManga = remember(viewModel.trendingManga, showAdultContent) { viewModel.trendingManga.filter { showAdultContent || !it.isAdult } }
    val filteredNewlyAddedAnime = remember(viewModel.newlyAddedAnime, showAdultContent) { viewModel.newlyAddedAnime.filter { showAdultContent || !it.isAdult } }
    val filteredNewlyAddedManga = remember(viewModel.newlyAddedManga, showAdultContent) { viewModel.newlyAddedManga.filter { showAdultContent || !it.isAdult } }
    val filteredAiringSoonAnime = remember(viewModel.airingSoonAnime, showAdultContent) { viewModel.airingSoonAnime.filter { showAdultContent || !it.isAdult } }
    val filteredUpcomingMediaTmdb = remember(viewModel.upcomingMediaTmdb, showAdultContent) { viewModel.upcomingMediaTmdb.filter { showAdultContent || !it.isAdult } }
    val filteredSimklContinueMovies = remember(viewModel.simklContinueMovies, showAdultContent) { viewModel.simklContinueMovies.filter { showAdultContent || !it.isAdult } }
    val filteredSimklPlannedMovies = remember(viewModel.simklPlannedMovies, showAdultContent) { viewModel.simklPlannedMovies.filter { showAdultContent || !it.isAdult } }
    val filteredSimklContinueSeries = remember(viewModel.simklContinueSeries, showAdultContent) { viewModel.simklContinueSeries.filter { showAdultContent || !it.isAdult } }
    val filteredSimklPlannedSeries = remember(viewModel.simklPlannedSeries, showAdultContent) { viewModel.simklPlannedSeries.filter { showAdultContent || !it.isAdult } }

    // AniHyou paritesi: ayrım kapalıysa romanlar manga rafına karışır; açıksa ayrı "Noveller" rafı.
    val effectiveNovels = if (separateNovelsManga) filteredNovels else emptyList()
    val effectiveTopManga = if (separateNovelsManga) filteredTopManga else filteredTopManga + filteredNovels

    // Filtrelenmiş payload yalnızca girdi listeleri değiştiğinde yeniden kurulur —
    // her kompozisyonda 19 alanlık data-class üretmek + equals çağırmak gereksiz maliyetti.
    val filteredSourcePayload = remember(
        filteredTopAnime, filteredAiringAnime, filteredUpcomingAnime,
        effectiveTopManga, filteredPublishingManga, filteredTrendingAnime,
        filteredMovieAnime, filteredSeasonalAnime,
        filteredSimklContinueMovies, filteredSimklPlannedMovies,
        filteredSimklContinueSeries, filteredSimklPlannedSeries,
        filteredAiringSoonAnime, filteredTrendingManga,
        filteredNewlyAddedAnime, filteredNewlyAddedManga, filteredUpcomingMediaTmdb,
        filteredTopRatedAnime, filteredTopRatedManga,
        filteredBangumiTvShows, filteredBangumiMovies,
        filteredManhwaManhua, effectiveNovels
    ) {
        ExplorePayload(
            topAnime = filteredTopAnime,
            airingAnime = filteredAiringAnime,
            upcomingAnime = filteredUpcomingAnime,
            topManga = effectiveTopManga,
            publishingManga = filteredPublishingManga,
            trendingAnime = filteredTrendingAnime,
            movieAnime = filteredMovieAnime,
            seasonalAnime = filteredSeasonalAnime,
            simklContinueMovies = filteredSimklContinueMovies,
            simklPlannedMovies = filteredSimklPlannedMovies,
            simklContinueSeries = filteredSimklContinueSeries,
            simklPlannedSeries = filteredSimklPlannedSeries,
            airingSoonAnime = filteredAiringSoonAnime,
            trendingManga = filteredTrendingManga,
            newlyAddedAnime = filteredNewlyAddedAnime,
            newlyAddedManga = filteredNewlyAddedManga,
            upcomingMediaTmdb = filteredUpcomingMediaTmdb,
            topRatedAnime = filteredTopRatedAnime,
            topRatedManga = filteredTopRatedManga,
            bangumiTvShows = filteredBangumiTvShows,
            bangumiMovies = filteredBangumiMovies,
            manhwaManhua = filteredManhwaManhua,
            novels = effectiveNovels
        )
    }
    // Türetilen bölüm listeleri ana thread'de hesaplanmaz — yükleme sırasında her
    // kaynak durumu değişiminde kompozisyonu bloke edip kasmaya yol açıyordu.
    val selectedSourceSections = rememberExploreDerived(viewModel.selectedPlatform, filteredSourcePayload) {
        if (viewModel.selectedPlatform == ExplorePlatform.ALL) emptyList()
        else sourceSections(viewModel.selectedPlatform, filteredSourcePayload)
    }
    val visibleSelectedSourceSections = remember(selectedSourceSections, separateNovelsManga) {
        selectedSourceSections.filter { separateNovelsManga || it.category != ExploreCategoryType.NOVELS }
    }
    // Single-source shortcuts are derived from the same rows below. Providers with eager
    // catalogs omit empty categories; AniList retains supported-but-not-yet-loaded shelves.
    val selectedCategorySections = remember(visibleSelectedSourceSections, viewModel.selectedPlatform) {
        if (viewModel.selectedPlatform == ExplorePlatform.AniList) visibleSelectedSourceSections
        else visibleSelectedSourceSections.filter { it.results.isNotEmpty() }
    }

    // "Tümü" modu bölümleri: yalnızca kaynak durumları/ayar değişince hesaplanır.
    // allSourceSections() her çağrısında forSource kopyaları + distinctBy string
    // hesapları yapar; bu tek blok Dispatchers.Default üzerinde tek geçişte üretilir.
    val allSectionsDerived = rememberExploreDerived(viewModel.allSourceStates, showAdultContent) {
        val list = allSourceSections(viewModel.allSourceStates, showAdultContent)
        val byPlatform = list.groupBy { it.platform }
        Triple(
            list,
            byPlatform,
            byPlatform.mapValues { (_, sections) ->
                sections.flatMap { it.results }.distinctBy { it.exploreIdentity() }.size
            }
        )
    }
    val allSectionsList = allSectionsDerived.first
    val allSectionsByPlatform = allSectionsDerived.second
    val allSectionsDistinctCounts = allSectionsDerived.third

    // Tümü modunda tek ORTAK "Yakında Yayında" şeridi: tüm kaynakların yayın
    // takvimi verisinin BİRLEŞİMİ — AniList/MAL bölüm takvimi + TMDB vizyon/bölüm
    // tarihleri birlikte gösterilir (kimlik + normalize başlıkla tekilleştirilir,
    // yayın zamanına göre sıralanır). Kaynak modunda şerit kaynağa özgü kalır.
    val sharedAiringSoon = rememberExploreDerived(viewModel.allSourceStates, showAdultContent) {
        fun List<JikanSearchResult>?.ready() =
            this.orEmpty().filter { showAdultContent || !it.isAdult }
        // Öncelik sırası: bölüm numarası taşıyan kaynaklar önce (duplikasyonda
        // daha zengin kayıt kazanır), TMDB vizyon listesi sonra eklenir.
        val priority = listOf(ExplorePlatform.AniList, ExplorePlatform.MAL, ExplorePlatform.TMDB)
        val order = priority + ExplorePlatform.sources.filter { it !in priority }
        val byIdentity = LinkedHashMap<String, JikanSearchResult>()
        val seenTitles = mutableSetOf<String>()
        for (platform in order) {
            for (item in viewModel.allSourceStates[platform]?.payload?.airingSoonAnime.ready()) {
                val titleKey = normalizeAiringTitle(item.title)
                if (titleKey != null && !seenTitles.add(titleKey)) continue
                val identity = item.exploreIdentity()
                if (!byIdentity.containsKey(identity)) byIdentity[identity] = item
            }
        }
        byIdentity.values.sortedBy {
            com.kitsugi.animelist.utils.NextAiringFormat.parse(it.nextAiringEpisode).epoch
                ?: Long.MAX_VALUE
        }
    }
    val airingSoonTitle = stringResource(R.string.explore_airing_soon)

    // The video-only hero uses one item per source/shelf in ALL, two in a selected
    // source, plus the countdown rail. Artwork is verified before it can be displayed.
    val heroSourceSections = if (viewModel.selectedPlatform == ExplorePlatform.ALL) allSectionsList else visibleSelectedSourceSections
    val heroSections = remember(heroSourceSections) {
        heroSourceSections.map { section ->
            section.copy(results = section.results.filter(::isHomeHeroMedia))
        }.filter { it.results.isNotEmpty() }
    }
    val heroAiringSoon = if (viewModel.selectedPlatform == ExplorePlatform.ALL) sharedAiringSoon else filteredAiringSoonAnime
    LaunchedEffect(viewModel.selectedPlatform, heroSections, heroAiringSoon, viewModel.heroRefreshSeed) {
        viewModel.prepareHomeHeroes(heroSections, heroAiringSoon, viewModel.selectedPlatform)
    }
    val heroItems = remember(viewModel.homeHeroItems, showAdultContent) {
        viewModel.homeHeroItems.filter { showAdultContent || !it.isAdult }
    }

    val entryMap = remember(currentEntries) {
        generateExploreEntryMap(currentEntries)
    }

    val getMediaEntry = remember(entryMap) {
        { result: JikanSearchResult ->
            getMediaEntryFromMap(result, entryMap)
        }
    }

    val isAlreadyInList = remember(getMediaEntry) {
        { result: JikanSearchResult ->
            getMediaEntry(result) != null
        }
    }

    val onLongClickItem = remember(getMediaEntry) {
        { result: JikanSearchResult ->
            val entry = getMediaEntry(result)
            if (entry != null) {
                onEditEntry(entry)
            } else {
                onAddSelectionToList(ApiSearchSelection(result = result, synopsis = null))
            }
        }
    }

    val hasCatalogContent = heroItems.isNotEmpty() ||
        (if (viewModel.selectedPlatform == ExplorePlatform.ALL) {
            allSectionsList.isNotEmpty() || sharedAiringSoon.isNotEmpty()
        } else {
            selectedSourceSections.any { it.results.isNotEmpty() } || filteredAiringSoonAnime.isNotEmpty()
        })
    val lazyListState = com.kitsugi.animelist.ui.utils.rememberRetainedLazyListState(
        contentReady = hasCatalogContent,
        initialIndex = initialScrollIndex,
        initialOffset = initialScrollOffset
    )

    val allSourcesScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Kalan yapışkan başlık: kaynak seçici pill satırı (~48dp). Kaynak başlıkları bu
    // yüksekliğin ALTINDA oturmalı ki sticky pill'in arkasına gizlenmesinler.
    val stickyToggleHeight = with(density) { 56.dp.roundToPx() }
    var collapsedSourceNames by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(emptyList<String>()) }

    var activeRankingSheetData by remember { mutableStateOf<Triple<String, MediaType, List<JikanSearchResult>>?>(null) }
    var isCategoriesExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var showSourceSheet by remember { mutableStateOf(false) }

    LaunchedEffect(lazyListState, hasCatalogContent) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (hasCatalogContent) onScrollPositionChange(index, offset)
        }
    }

    val isTvDevice = LocalIsTvDevice.current

    // ── "Yukarı Çık" FAB — arama/liste sayfalarıyla aynı davranış, alt barla uyumlu ──
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val showFab by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 1 } }
    // PERF: hero'nun görünürlük bilgisini derivedStateOf içine al — doğrudan
    // firstVisibleItemIndex okumak her kaydırma karesinde hero'yu ve tüm
    // alt ağacını recompose ediyordu.
    val isHeroVisible by remember { derivedStateOf { lazyListState.firstVisibleItemIndex == 0 } }

    // Manuel olarak tepeye dönüldüğünde sıfırlama sinyali ver (diğer sayfalarla aynı sözleşme).
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (firstIndex, scrollOffset) ->
                if (firstIndex == 0 && scrollOffset == 0) {
                    onScrollReset?.invoke()
                }
            }
    }

    val isCatalogEmpty = viewModel.selectedPlatform != ExplorePlatform.ALL &&
        !viewModel.isLoading && viewModel.errorMessage == null &&
        visibleSelectedSourceSections.none { it.results.isNotEmpty() } &&
        filteredAiringSoonAnime.isEmpty()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
            val pullRefreshState = rememberPullToRefreshState()
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides if (isTvDevice) tvSpec else LocalBringIntoViewSpec.current
            ) {
                PullToRefreshBox(
                    isRefreshing = viewModel.isLoading,
                    onRefresh = { viewModel.loadData(forceRefresh = true) },
                    modifier = Modifier.fillMaxSize(),
                    state = pullRefreshState,
                    indicator = {
                        KitsugiPullToRefreshIndicator(
                            state = pullRefreshState,
                            isRefreshing = viewModel.isLoading,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    KitsugiShimmerProvider(active = viewModel.isLoading) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            if (heroItems.isNotEmpty()) {
                                item(key = "explore_hero") {
                                    KitsugiHeroSection(
                                        items = heroItems,
                                        artworkPrepared = true,
                                        alreadyInList = isAlreadyInList,
                                        onInfoClick = onOpenApiDetail,
                                        titleLanguage = titleLanguage,
                                        scoreFormat = scoreFormat,
                                        hideScores = hideScores,
                                        showAnimeLogos = showAnimeLogos,
                                        blurAdultMedia = blurAdultMedia,
                                        isVisible = isHeroVisible
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            } else if (viewModel.isLoading || viewModel.isHeroLoading) {
                                item(key = "explore_hero") {
                                    KitsugiShimmerHeroSection()
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            } else {
                                item {
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }

                            // Header / Title and Toggle as STICKY HEADER
                            stickyHeader(key = "explore_platform_toggle") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = KitsugiColors.Background.copy(alpha = 0.95f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 6.dp)
                                    ) {
                                        if (isTvDevice) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.kesfet),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Black,
                                                    color = KitsugiColors.TextPrimary
                                                )
                                                ExploreSourceEngineSelectorPill(
                                                    selectedPlatform = viewModel.selectedPlatform,
                                                    onClick = { showSourceSheet = true }
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ExploreSourceEngineSelectorPill(
                                                    selectedPlatform = viewModel.selectedPlatform,
                                                    onClick = { showSourceSheet = true },
                                                    modifier = Modifier.weight(1f)
                                                )

                                                // 🎲 Rastgele + 🔔 Bildirim → ortak üst çubuk aksiyon satırı.
                                                // Sıra ve görünüm uygulamanın her yerinde aynıdır; bildirim
                                                // her zaman en sağdaki buton olarak kalır.
                                                KitsugiTopActionRow(
                                                    actions = buildList {
                                                        add(KitsugiTopAction.HISTORY)
                                                        if (onNavigateToDownloads != null) add(KitsugiTopAction.DOWNLOADS)
                                                        add(KitsugiTopAction.RANDOM)
                                                        if (isNotificationsVisible) add(KitsugiTopAction.NOTIFICATIONS)
                                                    },
                                                    onActionClick = { action ->
                                                        when (action) {
                                                            KitsugiTopAction.RANDOM -> {
                                                                val randomPool = mutableListOf<JikanSearchResult>()
                                                                if (viewModel.selectedPlatform == ExplorePlatform.ALL) {
                                                                    randomPool.addAll(
                                                                        allSectionsList
                                                                            .flatMap { it.results }
                                                                            .distinctBy { it.exploreIdentity() }
                                                                    )
                                                                } else {
                                                                    randomPool.addAll(
                                                                        visibleSelectedSourceSections
                                                                            .flatMap { it.results }
                                                                            .distinctBy { it.exploreIdentity() }
                                                                    )
                                                                }
                                                                randomPool.filter { showAdultContent || !it.isAdult }
                                                                    .randomOrNull()?.let(onOpenApiDetail)
                                                            }
                                                            KitsugiTopAction.HISTORY -> onNavigateToWatchHistory()
                                                            KitsugiTopAction.DOWNLOADS -> (onNavigateToDownloads ?: onOpenDownloads).invoke()
                                                            KitsugiTopAction.NOTIFICATIONS -> onOpenNotifications()
                                                            else -> Unit
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Önbellek Bildirim Banner'ı
                            if (viewModel.isShowingCachedData) {
                                item(key = "cached_data_banner") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 6.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(KitsugiColors.Surface.copy(alpha = 0.5f))
                                            .kitsugiCardFrame(RoundedCornerShape(12.dp), highlightColor = accentColor.copy(alpha = 0.3f))
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            com.kitsugi.animelist.ui.theme.gradient.Icon(
                                                imageVector = Icons.Satellite,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.inline_offline_cache_mode_63b3c6b),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = KitsugiColors.TextPrimary
                                                )
                                                Text(
                                                    text = stringResource(R.string.inline_the_internet_connection_is_down_56227de),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = KitsugiColors.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Hata mesajı
                            if (viewModel.errorMessage != null) {
                                item(key = "error_message") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        val errorType = viewModel.exploreErrorType
                                        KitsugiErrorState(
                                            message = viewModel.errorMessage.orEmpty(),
                                            onRetryClick = { viewModel.loadData(forceRefresh = true) },
                                            actionButton = when {
                                                errorType == ExploreErrorType.TmdbError && onRedirectToSettings != null -> ({
                                                    OutlinedButton(
                                                        onClick = { onRedirectToSettings.invoke() },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = LocalKitsugiAccent.current
                                                        ),
                                                        border = ButtonDefaults.outlinedButtonBorder
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Key,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = stringResource(R.string.inline_set_tmdb_api_key_d93a3ee),
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                })
                                                (errorType == ExploreErrorType.AniListError || errorType == ExploreErrorType.MalError) && onRedirectToAuth != null -> ({
                                                    OutlinedButton(
                                                        onClick = { onRedirectToAuth.invoke() },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = LocalKitsugiAccent.current
                                                        ),
                                                        border = ButtonDefaults.outlinedButtonBorder
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Login,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = stringResource(R.string.giris_yap),
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                })
                                                else -> null
                                            }
                                        )
                                        viewModel.platformErrorCode?.let { code ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            PlatformErrorExplanationBlock(code = code, tint = KitsugiColors.AccentRed)
                                        }
                                    }
                                }
                            }


                            if (viewModel.selectedPlatform == ExplorePlatform.ALL) {
                                allSourcesExploreSections(
                                    states = viewModel.allSourceStates,
                                    sectionsByPlatform = allSectionsByPlatform,
                                    distinctCounts = allSectionsDistinctCounts,
                                    airingSoonShelf = sharedAiringSoon,
                                    airingSoonTitle = airingSoonTitle,
                                    airingSoonIsLoading = viewModel.isLoading,
                                    onOpenAiringCalendar = onOpenAiringCalendar,
                                    collapsedSources = ExplorePlatform.sources.filter { it.name in collapsedSourceNames }.toSet(),
                                    onToggleSource = { source ->
                                        collapsedSourceNames = if (source.name in collapsedSourceNames) collapsedSourceNames - source.name
                                            else collapsedSourceNames + source.name
                                    },
                                    startIndex = 2 + (if (viewModel.isShowingCachedData) 1 else 0) + (if (viewModel.errorMessage != null) 1 else 0),
                                    onJumpToIndex = { index -> allSourcesScope.launch {
                                        lazyListState.animateScrollToItem(index, stickyToggleHeight)
                                    } },
                                    onRetrySource = viewModel::retrySource,
                                    loadedCategories = viewModel.aniListLoadedCategories,
                                    loadingCategories = viewModel.aniListLoadingCategories,
                                    failedCategories = viewModel.aniListFailedCategories,
                                    onLoadCategory = viewModel::loadExploreSection,
                                    onRetryCategory = viewModel::retryExploreSection,
                                    alreadyInList = isAlreadyInList,
                                    getMediaEntry = getMediaEntry,
                                    onItemClick = onOpenApiDetail,
                                    onLongClickItem = onLongClickItem,
                                    onSeeAllSection = onSeeAllSection,
                                    titleLanguage = titleLanguage,
                                    scoreFormat = scoreFormat,
                                    hideScores = hideScores,
                                    blurAdultMedia = blurAdultMedia,
                                    // Telefon: yapışkan kaynak çubuğu kaldırıldı —
                                    // yukarı çıkma için yüzen FAB kullanılıyor (alt barla uyumlu).
                                    showSourceJumpBar = false
                                )
                            } else if (isCatalogEmpty) {
                                item {
                                    KitsugiEmptyState(
                                        title = stringResource(R.string.explore_source_empty_title),
                                        subtitle = stringResource(R.string.explore_source_empty_subtitle)
                                    )
                                }
                            } else {
                                val isTmdbFamilySource = viewModel.selectedPlatform == ExplorePlatform.TMDB ||
                                    viewModel.selectedPlatform == ExplorePlatform.SIMKL
                                item(key = "selected_explore_categories") {
                                    ExploreCategoriesSection(
                                        isExpanded = isCategoriesExpanded,
                                        onExpandedChange = { isCategoriesExpanded = it },
                                        accentColor = accentColor,
                                        sections = selectedCategorySections,
                                        onSeeAllSection = onSeeAllForSelected
                                    )
                                    Spacer(modifier = Modifier.height(26.dp))
                                }

                                // Tüm kaynaklardaki ortak yayın şeridi aynı noktada: kategori
                                // kısayollarının hemen altında, katalog raflarından önce.
                                if (filteredAiringSoonAnime.isNotEmpty() || viewModel.isLoading) {
                                    item(key = "selected_airing_soon") {
                                        ExploreAiringSoonSection(
                                            title = stringResource(R.string.explore_airing_soon),
                                            airingSoonAnime = filteredAiringSoonAnime,
                                            isLoading = viewModel.isLoading,
                                            alreadyInList = isAlreadyInList,
                                            onItemClick = onOpenApiDetail,
                                            onLongClickItem = onLongClickItem,
                                            onOpenAiringCalendar = onOpenAiringCalendar,
                                            accentColor = accentColor,
                                            titleLanguage = titleLanguage,
                                            blurAdultMedia = blurAdultMedia
                                        )
                                        Spacer(modifier = Modifier.height(26.dp))
                                    }
                                }

                                if (!isTmdbFamilySource || viewModel.tmdbModernHomeEnabled) {
                                    sourceExploreSections(
                                        sections = visibleSelectedSourceSections,
                                        platform = viewModel.selectedPlatform,
                                        isLoading = viewModel.isLoading,
                                        loadedCategories = viewModel.aniListLoadedCategories,
                                        loadingCategories = viewModel.aniListLoadingCategories,
                                        failedCategories = viewModel.aniListFailedCategories,
                                        onLoadCategory = viewModel::loadExploreSection,
                                        onRetryCategory = viewModel::retryExploreSection,
                                        isAlreadyInList = isAlreadyInList,
                                        getMediaEntry = getMediaEntry,
                                        onItemClick = onOpenApiDetail,
                                        onLongClickItem = onLongClickItem,
                                        onSeeAllSection = onSeeAllForSelected,
                                        titleLanguage = titleLanguage,
                                        scoreFormat = scoreFormat,
                                        hideScores = hideScores,
                                        blurAdultMedia = blurAdultMedia
                                    )
                                    if (viewModel.selectedPlatform == ExplorePlatform.SIMKL) {
                                        simklUserExploreSections(
                                            viewModel = viewModel,
                                            isAlreadyInList = isAlreadyInList,
                                            getMediaEntry = getMediaEntry,
                                            onItemClick = onOpenApiDetail,
                                            onLongClickItem = onLongClickItem,
                                            onSeeAllSection = onSeeAllForSelected,
                                            onNavigateToWatchHistory = onNavigateToWatchHistory,
                                            titleLanguage = titleLanguage,
                                            scoreFormat = scoreFormat,
                                            hideScores = hideScores,
                                            blurAdultMedia = blurAdultMedia,
                                            context = context
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── "Yukarı Çık" FAB — aşağı kaydırınca belirir, alt bar üstünde hizalanır ──
            com.kitsugi.animelist.ui.components.KitsugiScrollToTopFab(
                visible = showFab,
                isLandscape = isLandscape,
                bottomBarScrollState = bottomBarScrollState,
                fallbackBottomBarVisible = isBottomBarVisible,
                onClick = {
                    onScrollReset?.invoke()
                    scope.launch { lazyListState.animateScrollToItem(0) }
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }

    val rankingSheet = activeRankingSheetData
    if (rankingSheet != null) {
        com.kitsugi.animelist.ui.components.KitsugiRankingBottomSheet(
            title = rankingSheet.first,
            mediaType = rankingSheet.second,
            platform = viewModel.selectedPlatform,
            initialResults = rankingSheet.third,
            alreadyInList = isAlreadyInList,
            onItemClick = onOpenApiDetail,
            onDismissRequest = { activeRankingSheetData = null },
            titleLanguage = titleLanguage,
            hideScores = hideScores,
            showAdultContent = showAdultContent,
            blurAdultMedia = blurAdultMedia,
            getMediaEntry = getMediaEntry
        )
    }

    if (showSourceSheet) {
        ExploreSourcePickerSheet(
            selectedPlatform = viewModel.selectedPlatform,
            onSelectPlatform = { platform -> viewModel.selectPlatform(platform) },
            onDismiss = { showSourceSheet = false }
        )
    }
}
