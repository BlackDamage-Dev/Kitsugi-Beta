@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.ui.theme.gradient.FloatingActionButton
import com.kitsugi.animelist.ui.components.PlatformErrorCodeChip
import com.kitsugi.animelist.ui.components.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.kitsugi.animelist.ui.icons.KitsugiEmojiIcons
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiTopAction
import com.kitsugi.animelist.ui.components.KitsugiTopActionRow
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.components.KitsugiShimmerSearchResultList
import com.kitsugi.animelist.ui.components.KitsugiShimmerResponsiveMediaRow
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiCosmicSearchBar
import com.kitsugi.animelist.ui.components.CosmicCompanionButton
import com.kitsugi.animelist.ui.screens.search.components.AddonExploreDialog
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchCountryChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchDateChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchEpChDurationChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchFormatChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchSourceChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchSortChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchStatusChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiTriFilterChip
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.kitsugiLazyKey
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
import com.lagradost.cloudstream3.APIHolder
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Kullanıcı bir kez kaydırdığında otomatik çekilebilecek azami sayfa sayısı.
 * Sonsuz sayfalama döngüsünü (bkz. `shouldLoadMore`) normal kaydırmayı bozmadan kapatır.
 */
private const val AUTO_LOAD_BUDGET_PER_SCROLL = 3

@Composable
fun SearchScreen(
    currentEntries: List<MediaEntry>,
    showAdultContent: Boolean,
    onOpenApiDetail: (JikanSearchResult) -> Unit,
    onAddSelectionToList: (ApiSearchSelection) -> Unit,
    viewModel: SearchViewModel = viewModel(),
    titleLanguage: String = "ROMAJI",
    staffNameLanguage: String = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.staffNameLanguage,
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    onSeeAllAddonSection: ((apiName: String, title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<com.lagradost.cloudstream3.SearchResponse>) -> Unit)? = null,
    // Eklenti Keşfet dialogı state'i — AnimatedContent geçişlerinde
    // yerel remember sıfırlanmaması için dışarıdan yönetilir (navState)
    addonExploreOpen: Boolean = false,
    onAddonExploreOpenChange: (Boolean) -> Unit = {},
    // Eklenti Portalı tam ekran navigasyonu
    onOpenPluginPicker: () -> Unit = {},
    // Eklenti Keşfet tam ekran navigasyonu
    onOpenAddonExplore: (String) -> Unit = {},
    // AniHyou Karakter ve Personel detay yönlendirmeleri
    onOpenCharacterDetail: ((characterId: Int, name: String?, imageUrl: String?) -> Unit)? = null,
    onOpenStaffDetail: ((staffId: Int, name: String?, imageUrl: String?) -> Unit)? = null,
    onOpenStudioDetail: ((studioId: Int, source: String, name: String?, imageUrl: String?) -> Unit)? = null,
    isBottomBarVisible: Boolean = true,
    bottomBarScrollState: com.kitsugi.animelist.utils.ScrollVisibilityState? = null,
    onScrollReset: (() -> Unit)? = null,
    // "Tümünü Gör" → kaynağa özel tam arama sayfasını ayrı ekranda açar
    onOpenSourceSearch: (SearchSourceEngine, SearchScope, List<JikanSearchResult>) -> Unit = { _, _, _ -> },
    // Alt sayfa olarak kullanıldığında büyük başlığı gizlemek için null verilebilir
    pageTitle: String? = "Arama",
    // Diğer sekmelerle aynı ortak üst çubuk bildirim butonu
    isNotificationsVisible: Boolean = false,
    onOpenNotifications: () -> Unit = {},
    onOpenWatchHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val accentColor = LocalKitsugiAccent.current
    val isTv = LocalIsTv.current
    val lazyListState = com.kitsugi.animelist.ui.utils.rememberRetainedLazyListState(
        contentReady = uiState.results.isNotEmpty() || !uiState.multiResults.isEmpty || !uiState.hasSearched
    )
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // Otomatik sayfalama emniyeti (bkz. aşağıdaki shouldLoadMore açıklaması).
    var autoLoadBudget by remember { mutableIntStateOf(AUTO_LOAD_BUDGET_PER_SCROLL) }
    var lastAutoLoadLoadedCount by remember { mutableIntStateOf(-1) }
    val isStudioSearch = uiState.selectedScope == SearchScope.STUDIO
    val onSearchResultClick: (JikanSearchResult) -> Unit = { result ->
        if (isStudioSearch && onOpenStudioDetail != null) {
            onOpenStudioDetail(result.malId, result.source, result.title, result.imageUrl)
        } else {
            onOpenApiDetail(result)
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (firstIndex, scrollOffset) ->
                // Kullanıcı kaydırdı: otomatik sayfalama bütçesi yenilenir (normal sonsuz
                // kaydırma birebir korunur; yalnızca kaydırma olmadan zincirlenen istek
                // döngüsü engellenir).
                if (autoLoadBudget != AUTO_LOAD_BUDGET_PER_SCROLL || lastAutoLoadLoadedCount != -1) {
                    autoLoadBudget = AUTO_LOAD_BUDGET_PER_SCROLL
                    lastAutoLoadLoadedCount = -1
                }
                if (firstIndex == 0 && scrollOffset == 0) {
                    onScrollReset?.invoke()
                }
            }
    }

    // AniHyou Infinite Scroll (OnBottomReached)
    //
    // EMNİYET KEMERİ: İstemcide sayfalama "sona ulaşıldı" koşulu yükleme bayrağını da
    // içerdiği için, her sayfa yüklemesi bittiğinde koşul yeniden true oluyor ve efekti
    // tekrar tetikliyordu. Sonuç: kullanıcı kaydırmasa bile art arda sayfa çekilmesi
    // (filtre nedeniyle görünen liste büyümediğinde hiç bitmeyen döngü → API 429 +
    // kaydırma kasması). Artık aynı sonuç kümesi için bir kez tetiklenir ve kaydırma
    // başına bütçe ile sınırlanır; kullanıcı kaydırınca bütçe yenilenir.
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = lazyListState.layoutInfo.totalItemsCount
            val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3 && !uiState.isLoading && !uiState.isLoadingMore && uiState.hasNextPage
        }
    }
    LaunchedEffect(shouldLoadMore, autoLoadBudget, uiState.results.size) {
        if (shouldLoadMore) {
            val loadedCount = uiState.results.size
            if (autoLoadBudget > 0 && loadedCount != lastAutoLoadLoadedCount) {
                autoLoadBudget -= 1
                lastAutoLoadLoadedCount = loadedCount
                viewModel.loadMore()
            }
        }
    }

    // Active Dialog States
    var openPlatformDialog by remember { mutableStateOf(false) }
    var openTmdbFormatDialog by remember { mutableStateOf(false) }
    var openTmdbGenreDialog by remember { mutableStateOf(false) }
    var openAddonSearchDialog by rememberSaveable { mutableStateOf(false) }
    var activeDetailCs3Item by remember { mutableStateOf<JikanSearchResult?>(null) }

    // Seçili eklentinin keşfet dialogunu aç/kapat
    // showPluginExploreDialog navState'ten gelir — AnimatedContent geçişlerinde sıfırlanmaz
    val selectedPluginApiName = uiState.selectedPluginApiName
    val selectedPluginApi = remember(selectedPluginApiName) {
        if (selectedPluginApiName == null) null
        else APIHolder.allProviders.firstOrNull { it.name == selectedPluginApiName }
    }

    val entryMap = remember(currentEntries) {
        com.kitsugi.animelist.ui.screens.explore.generateExploreEntryMap(currentEntries)
    }

    val getMediaEntry = remember(entryMap) {
        { result: JikanSearchResult ->
            com.kitsugi.animelist.ui.screens.explore.getMediaEntryFromMap(result, entryMap)
        }
    }

    val isAlreadyInList = remember(getMediaEntry) {
        { result: JikanSearchResult ->
            getMediaEntry(result) != null
        }
    }

    val isSearchResultAlreadyInList: (JikanSearchResult) -> Boolean = { result ->
        !isStudioSearch && isAlreadyInList(result)
    }
    val getSearchResultMediaEntry: (JikanSearchResult) -> MediaEntry? = { result ->
        if (isStudioSearch) null else getMediaEntry(result)
    }

    // Sonuç filtrelemesi: sonuçlar/filtre değişmediği sürece yeniden çalışmasın.
    // LazyColumn DSL'i her yeniden kurulduğunda yüzlerce sonucu tekrar süzmek
    // arama sayfasını gereksiz yere yavaşlatıyordu.
    val filteredResults = remember(uiState.results, showAdultContent, uiState.onMyList, isAlreadyInList) {
        uiState.results
            .filter { showAdultContent || !it.isAdult }
            .filter { result ->
                when (uiState.onMyList) {
                    true -> isAlreadyInList(result)
                    false -> !isAlreadyInList(result)
                    null -> true
                }
            }
    }

    val showIdleContent = !uiState.hasSearched && !uiState.isLoading
    val showFab by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 1 } }

    val isTmdbPlatform = uiState.selectedPlatform == SearchPlatform.TMDB
    val currentMediaType = uiState.selectedMediaType

    var showEnginePickerSheet by remember { mutableStateOf(false) }
    var showSourceEngineFilterSheet by remember { mutableStateOf(false) }

    // Keep canonical English values in state/API filters; localize only the visible labels.
    val tmdbGenres = listOf(
        "All", "Action", "Adventure", "Comedy", "Drama", "Fantasy",
        "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "Music", "History", "Animation"
    )
    val tmdbAllGenreLabel = stringResource(R.string.search_filter_all_genres)

    // Shimmer geçişleri tek paylaşımlı transition'dan çalışsın: "Tümü" aramasında
    // 7 platform rafı + sonuç listesi placeholder'ı aynı anda yüklenirken her biri
    // kendi sonsuz animasyonunu kuruyordu. Yüklenme yokken animasyon hiç çalışmaz.
    val anySearchLoading = uiState.isLoading || uiState.isLoadingMore || uiState.multiResults.isAnyLoading
    KitsugiShimmerProvider(active = anySearchLoading) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .then(if (isTv) Modifier.dpadVerticalFastScroll(lazyListState) else Modifier)
        ) {
            // Title & Search Input Section
            item {
                if (pageTitle != null) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = pageTitle,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        // 🔔 Bildirim → diğer sekmelerle aynı ortak üst çubuk butonu
                        // (kanonik sırada her zaman en sağda durur).
                        KitsugiTopActionRow(
                            actions = buildList {
                                if (onOpenWatchHistory != null) add(KitsugiTopAction.HISTORY)
                                if (onOpenDownloads != null) add(KitsugiTopAction.DOWNLOADS)
                                if (isNotificationsVisible) add(KitsugiTopAction.NOTIFICATIONS)
                            },
                            onActionClick = { action ->
                                when (action) {
                                    KitsugiTopAction.HISTORY -> onOpenWatchHistory?.invoke()
                                    KitsugiTopAction.DOWNLOADS -> onOpenDownloads?.invoke()
                                    KitsugiTopAction.NOTIFICATIONS -> onOpenNotifications()
                                    else -> Unit
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val placeholder = if (uiState.selectedEngine == SearchSourceEngine.ALL) {
                    stringResource(R.string.search_placeholder_all_sources)
                } else {
                    stringResource(
                        R.string.search_placeholder_source_scope,
                        uiState.selectedEngine.localizedLabel(),
                        uiState.selectedScope.localizedLabel()
                    )
                }

                // Uiverse Lakshay-art Cosmic Search Bar & Companion Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KitsugiCosmicSearchBar(
                        query = uiState.query,
                        onQueryChange = viewModel::setQuery,
                        onSearch = {
                            // Arama yalnızca burada tetiklenir (buton veya klavyedeki
                            // "Ara" onay tuşu) — yazarken kendiliğinden arama yok.
                            viewModel.onSearchAction()
                            keyboardController?.hide()
                        },
                        onClearQuery = {
                            viewModel.clearQuery()
                            keyboardController?.hide()
                        },
                        placeholder = placeholder,
                        leadingContent = {
                            SourceEngineSelectorPill(
                                selectedEngine = uiState.selectedEngine,
                                onClick = { showEnginePickerSheet = true },
                                // Yazarken motor adı gizlenir → arama butonuna yer açılır.
                                compact = uiState.query.isNotBlank()
                            )
                        },
                        onFilterClick = {
                            showSourceEngineFilterSheet = true
                        },
                        isFilterActive = uiState.hasFiltersApplied,
                        modifier = Modifier.weight(1f)
                    )

                    // Eklenti Portalı butonu (Uiverse cosmic companion button)
                    CosmicCompanionButton(
                        onClick = { onOpenPluginPicker() },
                        icon = {
                            Icon(
                                imageVector = Icons.Extension,
                                contentDescription = stringResource(R.string.eklenti_portali),
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Kompakt Arama Seçenekleri Özeti ─────────────────────────────
            // Kategori (kapsam), sıralama ve filtre kontrollerinin tamamı arama
            // çubuğunun sağındaki butonda (SourceEngineFilterSheet) toplandı;
            // ana sayfa dağınık bırakılmaz. Bu satır yalnızca varsayılandan
            // farklı bir ayar aktifse görünür ve aktif durumu özetler.
            item {
                SearchOptionsSummaryRow(
                    uiState = uiState,
                    onClick = { showSourceEngineFilterSheet = true }
                )
            }


            // Active Filters Inline Dismissible Chips Row
            // (Active filter pills removed – chips themselves show active state)

            // Search History Section
            if (showIdleContent && uiState.searchHistory.isNotEmpty()) {
                item {
                    SearchHistorySection(
                        history = uiState.searchHistory,
                        onHistoryItemClick = viewModel::applyHistoryItem,
                        onRemoveItem = viewModel::removeHistoryItem,
                        onClearAll = viewModel::clearHistory
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Shimmer Loading State
            //
            // "Tümü" sekmesinde AYRICA kopya shimmer rafları çizilmez: her raf kendi
            // yükleme bayrağını (multiResults.isLoading*) zaten taşıyor ve
            // MultiSearchSection verisi gelene kadar kendi shimmer'ını gösteriyor.
            // Eski kod `uiState.isLoading` boyunca üste 5 sabit shimmer rafı koyuyordu;
            // yedi kaynak tek tek tamamlandıkça gelmiş veriler bu animasyonlu
            // placeholder'ların ALTINDA birikiyor, yani verisi gelmiş rafın animasyonu
            // görünmeye devam ediyordu (üstelik Kitsu/Simkl listede yoktu → tutarsız).
            //
            // Diğer sekmelerde shimmer yalnızca henüz hiç sonuç yokken çizilir; aksi
            // halde yenileme/sayfalama sırasında mevcut satırların üstüne biniyordu.
            val showResultShimmer = uiState.isLoading &&
                uiState.currentTab != KitsugiSearchTab.All &&
                uiState.results.isEmpty()
            if (showResultShimmer) {
                item {
                    KitsugiShimmerSearchResultList(itemCount = 4)
                }
            }

            // Kaynak hataları: hangi kaynağın bu aramayı etkilediğini kod rozetleriyle göster
            if (!uiState.isLoading && uiState.sourceErrorCodes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.sourceErrorCodes.values.distinct().forEach { code ->
                            PlatformErrorCodeChip(code = code)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Empty / Error State
            if (!uiState.isLoading && uiState.hasSearched && uiState.errorMessage != null) {
                item {
                    KitsugiEmptyState(
                        title = stringResource(R.string.sonuc_bulunamadi),
                        subtitle = uiState.errorMessage,
                        icon = Icons.SearchOff
                    )
                    uiState.platformErrorCode?.let { code ->
                        Spacer(modifier = Modifier.height(12.dp))
                        PlatformErrorExplanationBlock(
                            code = code,
                            tint = KitsugiColors.AccentRed,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Search Results List (AniHyou-style filtered by adult and onMyList)
            if (uiState.currentTab == KitsugiSearchTab.All) {
                // Çoklu Platform Rafları (Seçenek C: All-in-One Multi Platform Search)
                item {
                    MultiSearchSection(
                        title = "AniList",
                        platformId = "anilist",
                        results = uiState.multiResults.aniListResults,
                        isLoading = uiState.multiResults.isLoadingAniList,
                        isAlreadyInList = isSearchResultAlreadyInList,
                        getMediaEntry = getSearchResultMediaEntry,
                        onItemClick = onSearchResultClick,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.ANILIST, uiState.selectedScope, uiState.multiResults.aniListResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "MyAnimeList",
                        platformId = "mal",
                        results = uiState.multiResults.malResults,
                        isLoading = uiState.multiResults.isLoadingMal,
                        isAlreadyInList = isSearchResultAlreadyInList,
                        getMediaEntry = getSearchResultMediaEntry,
                        onItemClick = onSearchResultClick,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.MAL, uiState.selectedScope, uiState.multiResults.malResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = stringResource(R.string.search_section_tmdb),
                        platformId = "tmdb",
                        results = uiState.multiResults.tmdbResults,
                        isLoading = uiState.multiResults.isLoadingTmdb,
                        isAlreadyInList = isSearchResultAlreadyInList,
                        getMediaEntry = getSearchResultMediaEntry,
                        onItemClick = onSearchResultClick,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.TMDB, uiState.selectedScope, uiState.multiResults.tmdbResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = stringResource(R.string.search_section_shikimori),
                        platformId = "shikimori",
                        results = uiState.multiResults.shikimoriResults,
                        isLoading = uiState.multiResults.isLoadingShikimori,
                        isAlreadyInList = isSearchResultAlreadyInList,
                        getMediaEntry = getSearchResultMediaEntry,
                        onItemClick = onSearchResultClick,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.SHIKIMORI, uiState.selectedScope, uiState.multiResults.shikimoriResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Kitsu",
                        platformId = "kitsu",
                        results = uiState.multiResults.kitsuResults,
                        isLoading = uiState.multiResults.isLoadingKitsu,
                        isAlreadyInList = isSearchResultAlreadyInList,
                        getMediaEntry = getSearchResultMediaEntry,
                        onItemClick = onSearchResultClick,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.KITSU, uiState.selectedScope, uiState.multiResults.kitsuResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Simkl",
                        platformId = "simkl",
                        results = uiState.multiResults.simklResults,
                        isLoading = uiState.multiResults.isLoadingSimkl,
                        isAlreadyInList = isSearchResultAlreadyInList,
                        getMediaEntry = getSearchResultMediaEntry,
                        onItemClick = onSearchResultClick,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.SIMKL, uiState.selectedScope, uiState.multiResults.simklResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = stringResource(R.string.search_section_bangumi),
                        platformId = "bangumi",
                        results = uiState.multiResults.bangumiResults,
                        isLoading = uiState.multiResults.isLoadingBangumi,
                        isAlreadyInList = isSearchResultAlreadyInList,
                        getMediaEntry = getSearchResultMediaEntry,
                        onItemClick = onSearchResultClick,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.BANGUMI, uiState.selectedScope, uiState.multiResults.bangumiResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }
            } else if (uiState.currentTab == KitsugiSearchTab.Character || uiState.currentTab == KitsugiSearchTab.Staff) {
                items(
                    items = filteredResults,
                    key = { "char_${it.source}_${it.type}_${it.malId}_${it.tmdbId ?: 0}" },
                    contentType = { _ -> "char_staff_row" }
                ) { result ->
                    val displayName = com.kitsugi.animelist.data.remote.displayPersonName(
                        result.title,
                        result.titleRomaji,
                        result.titleJapanese,
                        staffNameLanguage,
                        result.titleEnglish
                    )
                    CharacterStaffResultRow(
                        result = result.copy(
                            title = displayName,
                            subtitle = if (staffNameLanguage in listOf("NATIVE", "JAPANESE_STAFF")) {
                                when {
                                    result.subtitle == displayName -> "Karakter"
                                    result.subtitle.startsWith("$displayName • ") -> result.subtitle.removePrefix("$displayName • ")
                                    else -> result.subtitle
                                }
                            } else result.subtitle
                        ),
                        isStaff = uiState.currentTab == KitsugiSearchTab.Staff,
                        onClick = {
                            if (uiState.currentTab == KitsugiSearchTab.Staff) {
                                onOpenStaffDetail?.invoke(result.malId, displayName, result.imageUrl)
                            } else {
                                onOpenCharacterDetail?.invoke(result.malId, displayName, result.imageUrl)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else {
                items(
                    items = filteredResults,
                    key = { "res_${it.source}_${it.type}_${it.malId}_${it.tmdbId ?: 0}_${it.cs3Url.orEmpty().hashCode()}" },
                    contentType = { _ -> "search_row" }
                ) { result ->
                    SearchResultRow(
                        result = result,
                        alreadyInList = isSearchResultAlreadyInList(result),
                        mediaEntry = getSearchResultMediaEntry(result),
                        onItemClick = {
                            if (result.source == "cs3") {
                                activeDetailCs3Item = result
                            } else {
                                onSearchResultClick(result)
                            }
                        },
                        onAddClick = {
                            onAddSelectionToList(ApiSearchSelection(result = result, synopsis = null))
                        },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores,
                        showAddButton = !isStudioSearch
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Infinite scroll loading spinner
            if (uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiPlasmaLoader(
                            size = 32.dp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(90.dp)) }
        }

        // Scroll to Top FAB
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
    } // KitsugiShimmerProvider

    if (showEnginePickerSheet) {
        SourceEnginePickerSheet(
            selectedEngine = uiState.selectedEngine,
            onSelectEngine = {
                viewModel.setEngine(it)
                showEnginePickerSheet = false
            },
            onDismiss = { showEnginePickerSheet = false }
        )
    }

    if (showSourceEngineFilterSheet) {
        SourceEngineFilterSheet(
            uiState = uiState,
            viewModel = viewModel,
            onOpenGenresTags = {
                showSourceEngineFilterSheet = false
                viewModel.setFilterSheetOpen(true)
            },
            onDismiss = { showSourceEngineFilterSheet = false }
        )
    }

    // Genres Bottom Sheet Dialog
    if (uiState.isFilterSheetOpen) {
        GenresTagsSheet(
            currentFilters = SearchFilters(
                genres = uiState.genres,
                excludedGenres = uiState.excludedGenres,
                tags = uiState.tags
            ),
            onApplyFilters = {
                viewModel.updateFilters(it)
                viewModel.setFilterSheetOpen(false)
            },
            onDismiss = { viewModel.setFilterSheetOpen(false) }
        )
    }

    // Dialog: Platform selection
    if (openPlatformDialog) {
        val platforms = listOf(
            SearchPlatform.All,
            SearchPlatform.AniList,
            SearchPlatform.MAL,
            SearchPlatform.TMDB,
            SearchPlatform.Shikimori,
            SearchPlatform.Kitsu,
            SearchPlatform.Simkl,
            SearchPlatform.Bangumi
        )
        DialogWithRadioSelection(
            title = stringResource(R.string.search_select_platform),
            options = platforms,
            selectedOption = uiState.selectedPlatform,
            onOptionSelected = { plat ->
                if (plat != null) viewModel.setPlatform(plat)
            },
            onDismiss = { openPlatformDialog = false },
            optionLabel = { it.localizedLabel() }
        )
    }



    // Dialog: TMDB Format Selection
    if (openTmdbFormatDialog) {
        val tmdbTypes = listOf(MediaType.Movie, MediaType.TvShow)
        val movieLabel = stringResource(R.string.media_type_movie)
        val tvLabel = stringResource(R.string.media_type_tv)
        DialogWithRadioSelection(
            title = stringResource(R.string.search_select_type),
            options = tmdbTypes,
            selectedOption = currentMediaType,
            onOptionSelected = { mt ->
                if (mt != null) viewModel.setMediaType(mt)
            },
            onDismiss = { openTmdbFormatDialog = false },
            optionLabel = { if (it == MediaType.Movie) movieLabel else tvLabel }
        )
    }

    // Dialog: TMDB Genre Selection
    if (openTmdbGenreDialog) {
        val currentGenre = uiState.genres.firstOrNull() ?: "All"
        DialogWithRadioSelection(
            title = stringResource(R.string.search_select_genre),
            options = tmdbGenres,
            selectedOption = currentGenre,
            onOptionSelected = { g ->
                val updated = SearchFilters(
                    genres = if (g == null || g == "All") emptyList() else listOf(g)
                )
                viewModel.updateFilters(updated)
            },
            onDismiss = { openTmdbGenreDialog = false },
            optionLabel = { if (it == "All") tmdbAllGenreLabel else SearchTranslation.displayLabel(it) }
        )
    }

    if (openAddonSearchDialog) {
        com.kitsugi.animelist.ui.screens.search.components.AddonSearchDialog(
            onDismissRequest = { openAddonSearchDialog = false },
            onSeeAllAddonSection = onSeeAllAddonSection
        )
    }

    // Plugin picker is now handled via full-screen navigation (PluginPickerScreen)

    // ── Eklentiye Özel Keşfet (tam ekran sayfa navigasyonu) ──────────────
    val exploreApi = selectedPluginApi
    LaunchedEffect(addonExploreOpen, exploreApi?.name) {
        if (addonExploreOpen && exploreApi != null) {
            onAddonExploreOpenChange(false)
            onOpenAddonExplore(exploreApi.name)
        }
    }

    // ── KitsugiAddonDetailDialog ──────────────────────────────────────────
    activeDetailCs3Item?.let { result ->
        val api = remember(result.cs3ApiName) {
            APIHolder.allProviders.firstOrNull { it.name.equals(result.cs3ApiName, ignoreCase = true) }
        }
        if (api != null && !result.cs3Url.isNullOrBlank()) {
            com.kitsugi.animelist.ui.screens.search.components.KitsugiAddonDetailDialog(
                api = api,
                url = result.cs3Url,
                onDismissRequest = { activeDetailCs3Item = null }
            )
        }
    }
}

@Composable
fun SearchTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accentColor else KitsugiColors.Surface)
            .border(1.dp, if (selected) Color.Transparent else KitsugiColors.Border, shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SearchFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (selected) accentColor.copy(alpha = 0.5f) else KitsugiColors.Border, shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Kompakt Arama Seçenekleri Özeti ──────────────────────────────────────────

/**
 * Arama sayfasını dağınık göstermemek için kategori/sıralama/filtre
 * kontrolleri sağ üstteki butona (SourceEngineFilterSheet) taşındı.
 * Bu çip yalnızca varsayılandan farklı bir ayar aktifken görünür;
 * aktif kapsam + sıralama + filtre adedini tek satırda özetler ve
 * dokununca birleşik seçenekler sheet'ini açar.
 */
@Composable
private fun SearchOptionsSummaryRow(
    uiState: SearchUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val engine = uiState.selectedEngine

    val defaultScope = engine.availableScopes().first()
    val scopeChanged = uiState.selectedScope != defaultScope
    val sortChanged = getCurrentSortKey(uiState) != defaultSortKeyForEngine(engine)
    val activeFilterCount = uiState.activeFilterCount
    val activeFilterCountLabel = stringResource(R.string.search_active_filter_count, activeFilterCount)

    if (!scopeChanged && !sortChanged && activeFilterCount == 0) return

    val parts = buildList {
        if (scopeChanged) add(uiState.selectedScope.localizedDisplayLabel())
        if (sortChanged) add(KitsugiEmojiIcons.stripLeadingEmoji(localizedSourceSearchLabel(getSortDisplayLabel(uiState))))
        if (activeFilterCount > 0) add(activeFilterCountLabel)
    }

    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(accentColor.copy(alpha = 0.10f))
                .border(1.dp, accentColor.copy(alpha = 0.45f), shape)
                .tvClickable(shape = shape, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Kaynak kısaltması/emojisi yerine platformun orijinal logosu +
            // tam adı gösterilir (örn. 🌸 SHI → Shikimori logosu + "Shikimori").
            KitsugiPlatformLogo(
                platformId = engine.id,
                size = 16.dp,
                fallbackTint = accentColor
            )
            Text(
                text = "${engine.localizedLabel()} • ${parts.joinToString(" • ")}",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = stringResource(R.string.duzenle),
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}

// ─── Active Filters Chips Row ─────────────────────────────────────────────────

@Composable
fun ActiveFiltersChipsRow(
    filters: SearchFilters,
    onRemoveFilter: (filterType: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (filters.format != null) {
            ActiveFilterChip(label = stringResource(R.string.search_active_filter_format, filters.format), onCloseClick = { onRemoveFilter("format", "") })
        }
        if (filters.status != null) {
            val lbl = when (filters.status) {
                "AIRING" -> stringResource(R.string.search_status_airing)
                "FINISHED" -> stringResource(R.string.search_status_finished)
                "UPCOMING" -> stringResource(R.string.search_status_upcoming)
                "PUBLISHING" -> stringResource(R.string.search_status_publishing)
                "HIATUS" -> stringResource(R.string.search_status_hiatus_filter)
                "DISCONTINUED" -> stringResource(R.string.search_status_discontinued)
                else -> filters.status
            }
            ActiveFilterChip(label = stringResource(R.string.search_active_filter_status, lbl), onCloseClick = { onRemoveFilter("status", "") })
        }
        if (filters.season != null) {
            val lbl = when (filters.season) {
                "WINTER" -> stringResource(R.string.search_season_winter)
                "SPRING" -> stringResource(R.string.search_season_spring)
                "SUMMER" -> stringResource(R.string.search_season_summer)
                "FALL" -> stringResource(R.string.search_season_fall)
                else -> filters.season
            }
            ActiveFilterChip(label = stringResource(R.string.search_active_filter_season, lbl), onCloseClick = { onRemoveFilter("season", "") })
        }
        filters.genres.forEach { g ->
            val tr = SearchTranslation.displayLabel(g)
            ActiveFilterChip(label = "+ $tr", onCloseClick = { onRemoveFilter("genre", g) }, borderColor = Color(0xFF10B981))
        }
        filters.excludedGenres.forEach { g ->
            val tr = SearchTranslation.displayLabel(g)
            ActiveFilterChip(label = "- $tr", onCloseClick = { onRemoveFilter("excludedGenre", g) }, borderColor = Color(0xFFEF4444))
        }
        filters.tags.forEach { t ->
            val tr = SearchTranslation.displayLabel(t)
            ActiveFilterChip(label = "# $tr", onCloseClick = { onRemoveFilter("tag", t) })
        }
        if (filters.minYear != null || filters.maxYear != null) {
            ActiveFilterChip(label = stringResource(R.string.search_active_filter_year, filters.minYear ?: 1970, filters.maxYear ?: 2026), onCloseClick = { onRemoveFilter("year", "") })
        }
        if (filters.minScore != null || filters.maxScore != null) {
            ActiveFilterChip(label = "Puan: %${filters.minScore ?: 0}-%${filters.maxScore ?: 100}", onCloseClick = { onRemoveFilter("score", "") })
        }
        if (filters.sort != null && filters.sort != "POPULARITY_DESC") {
            val lbl = when (filters.sort) {
                "SCORE_DESC" -> "Puan"
                "TITLE_ROMAJI_ASC" -> stringResource(R.string.search_sort_title_asc)
                "TITLE_ROMAJI_DESC" -> stringResource(R.string.search_sort_title_desc)
                else -> filters.sort
            }
            ActiveFilterChip(label = stringResource(R.string.search_active_filter_sort, lbl), onCloseClick = { onRemoveFilter("sort", "") })
        }
    }
}

@Composable
fun ActiveFilterChip(
    label: String,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = LocalKitsugiAccent.current
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(KitsugiColors.Surface.copy(alpha = 0.6f))
            .border(1.dp, borderColor.copy(alpha = 0.5f), shape)
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = KitsugiColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .tvClickable(shape = RoundedCornerShape(8.dp), onClick = onCloseClick)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Close,
                contentDescription = stringResource(R.string.temizle),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * All-in-One Çoklu Platform Arama Bölümü (Yatay Kart Listesi - Keşfet Sayfası ile Birebir Aynı Mekanik ve Görünüm)
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun MultiSearchSection(
    title: String,
    platformId: String,
    results: List<JikanSearchResult>,
    isLoading: Boolean,
    isAlreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry? = { null },
    onItemClick: (JikanSearchResult) -> Unit,
    onSeeAllClick: () -> Unit,
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false
) {
    if (results.isEmpty() && !isLoading) return

    val accentColor = LocalKitsugiAccent.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTvDevice = LocalIsTvDevice.current
    val isTv = LocalIsTv.current
    val cardWidth = if (isLandscape) 260.dp else 180.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Platform Logosu + Başlık & "Tümünü Gör" Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KitsugiPlatformLogo(
                platformId = platformId,
                size = 24.dp,
                cornerRadius = 6.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (results.isNotEmpty()) {
                TextButton(
                    onClick = onSeeAllClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tumunu_gor),
                        color = accentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            isLoading && results.isEmpty() -> {
                // Gerçek kartlarla aynı yön, genişlik ve yatay kaydırma davranışı:
                // portrede 2:3 poster; yatayda poster + metin düzeni.
                KitsugiShimmerResponsiveMediaRow(
                    cardCount = 5,
                    cardWidth = cardWidth,
                    isLandscape = isLandscape && !isTv,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    cardSpacing = 12.dp,
                )
            }

            else -> {
                val lazyListState = androidx.compose.runtime.saveable.rememberSaveable(title, saver = LazyListState.Saver) { LazyListState() }

                if (isTvDevice) {
                    val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
                    val lastFocusedIndex = remember(results) { mutableStateOf(0) }
                    val focusRequesters = remember(results) { mutableMapOf<Int, FocusRequester>() }
                    val rowFocusRequester = remember { FocusRequester() }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides tvSpec) {
                        LazyRow(
                            state = lazyListState,
                            modifier = Modifier
                                .focusRequester(rowFocusRequester)
                                .focusRestorer {
                                    focusRequesters[lastFocusedIndex.value] ?: FocusRequester.Default
                                }
                                .focusGroup(),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = results,
                                // DİKKAT: anahtarda `type` ZORUNLU — "Tümünü Gör" rafı karma
                                // anime+manga listesi taşıyabilir; aynı kaynak+id iki farklı
                                // yapımı gösterebilir (bkz. KitsugiLazyKeys).
                                key = { idx, result -> result.kitsugiLazyKey("sr_tv", idx) },
                                contentType = { _, _ -> "h_media_card" }
                            ) { index, result ->
                                val requester = focusRequesters.getOrPut(index) { FocusRequester() }
                                KitsugiExploreMediaCard(
                                    result = result,
                                    alreadyInList = isAlreadyInList(result),
                                    mediaEntry = getMediaEntry(result),
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .focusRequester(requester)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                lastFocusedIndex.value = index
                                            }
                                        },
                                    onClick = { onItemClick(result) },
                                    titleLanguage = titleLanguage,
                                    scoreFormat = scoreFormat,
                                    hideScores = hideScores,
                                    blurAdultMedia = blurAdultMedia,
                                    forceVertical = false
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(
                        state = lazyListState,
                        flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(lazyListState),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = results,
                            key = { idx, result -> result.kitsugiLazyKey("sr", idx) },
                            contentType = { _, _ -> "h_media_card" }
                        ) { _, result ->
                            KitsugiExploreMediaCard(
                                result = result,
                                alreadyInList = isAlreadyInList(result),
                                mediaEntry = getMediaEntry(result),
                                modifier = Modifier.width(cardWidth),
                                onClick = { onItemClick(result) },
                                titleLanguage = titleLanguage,
                                scoreFormat = scoreFormat,
                                hideScores = hideScores,
                                blurAdultMedia = blurAdultMedia,
                                forceVertical = false
                            )
                        }
                    }
                }
            }
        }
    }
}

