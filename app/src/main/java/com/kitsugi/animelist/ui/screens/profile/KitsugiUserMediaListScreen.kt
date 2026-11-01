@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import android.content.res.Configuration
import com.kitsugi.animelist.ui.components.PlatformErrorCodeChip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.kitsugi.animelist.ui.components.KitsugiPullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.matchesInSource
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiImagePreviewDialog
import com.kitsugi.animelist.ui.components.KitsugiInfoDialog
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.screens.mylist.EmptyListResultCard
import com.kitsugi.animelist.ui.screens.mylist.MyListFlatContent
import com.kitsugi.animelist.ui.screens.mylist.MyListSectionedContent
import com.kitsugi.animelist.ui.screens.mylist.MyListHeaderSection
import com.kitsugi.animelist.ui.screens.mylist.MyListSingleSourceBar
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_SORT_DEFAULT
import com.kitsugi.animelist.ui.screens.mylist.MyListGroupingIds
import com.kitsugi.animelist.ui.screens.mylist.applySort
import com.kitsugi.animelist.ui.screens.mylist.filterMyListEntries
import com.kitsugi.animelist.ui.screens.mylist.groupMyListEntries
import com.kitsugi.animelist.ui.screens.mylist.isExplicitMyListSort
import com.kitsugi.animelist.ui.screens.mylist.components.KitsugiListStatusBottomSheet
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Başka bir kullanıcının AniList kütüphanesi.
 *
 * Bu ekran ayrı bir "profil listesi" tasarımı üretmez; Listem'in üst alanını,
 * filtreleme/sıralama sözleşmesini, kartlarını ve kayan kontrollerini doğrudan kullanır.
 * Yalnızca başkasının ilerlemesini değiştirecek işlemler salt okunurdur.
 */
@Composable
fun KitsugiUserMediaListScreen(
    userId: Int,
    username: String,
    initialMediaType: MediaType,
    appSettings: AppSettings,
    mediaEntries: List<MediaEntry>,
    onBackClick: () -> Unit,
    onMediaClick: (JikanSearchResult) -> Unit,
    onLocalEntryClick: (MediaEntry) -> Unit,
    onListLayoutChange: (String) -> Unit = {},
    accentColor: Color = LocalKitsugiAccent.current,
    customViewModel: KitsugiUserMediaListViewModel? = null
) {
    val viewModel: KitsugiUserMediaListViewModel = customViewModel
        ?: androidx.lifecycle.viewmodel.compose.viewModel(key = "user_media_list_$userId")
    val state by viewModel.uiState.collectAsState()
    val colors = LocalKitsugiColors.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (isLandscape) 12.dp else 20.dp
    val isTvDevice = LocalIsTvDevice.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedListLayoutId by rememberSaveable(userId) {
        mutableStateOf(appSettings.selectedListLayoutId)
    }
    LaunchedEffect(appSettings.selectedListLayoutId) {
        selectedListLayoutId = appSettings.selectedListLayoutId
    }

    var searchQuery by rememberSaveable(userId) { mutableStateOf("") }
    var selectedStatusFilterId by rememberSaveable(userId) { mutableStateOf("all") }
    var selectedTypeFilterId by rememberSaveable(userId, initialMediaType.name) {
        mutableStateOf(if (initialMediaType == MediaType.Manga) "manga" else "anime")
    }
    var selectedFavoriteFilterId by rememberSaveable(userId) { mutableStateOf("all") }
    var selectedScoreFilterId by rememberSaveable(userId) { mutableStateOf("all") }
    var selectedYearFilterId by rememberSaveable(userId) { mutableStateOf("all") }
    var selectedExtraFilterId by rememberSaveable(userId) { mutableStateOf("all") }
    var selectedSortId by rememberSaveable(userId) { mutableStateOf(MY_LIST_SORT_DEFAULT) }
    // Gruplama, sıralamadan bağımsız: profil listesi de Listem ile aynı varsayılan
    // (eklenme tarihi) bölümlerini gösterir. Kullanıcı isterse durum/düz listeye geçer.
    var selectedGroupId by rememberSaveable(userId) { mutableStateOf(MyListGroupingIds.ADDED) }

    var showSearchField by rememberSaveable(userId) { mutableStateOf(false) }
    var showFilterPanel by rememberSaveable(userId) { mutableStateOf(false) }
    var showStatusBottomSheet by rememberSaveable(userId) { mutableStateOf(false) }
    var isCategoryFabVisible by rememberSaveable(userId) { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousOffset by remember { mutableIntStateOf(0) }
    var activeZoomEntry by remember { mutableStateOf<MediaEntry?>(null) }
    var infoMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        viewModel.loadUserMediaLibrary(userId)
    }

    val allListemEntries = remember(state.items, appSettings.showAdultContent) {
        state.items
            .asSequence()
            .filter { appSettings.showAdultContent || !it.isAdult }
            .map { it.toListemMediaEntry() }
            .toList()
            .let { com.kitsugi.animelist.ui.screens.mylist.deduplicateSingleSourceEntries(it) }
    }
    val sourceItemByListEntryId = remember(state.items) {
        state.items.associateBy { it.listEntryId }
    }

    val filteredEntries = remember(
        allListemEntries,
        searchQuery,
        selectedStatusFilterId,
        selectedTypeFilterId,
        selectedFavoriteFilterId,
        selectedScoreFilterId,
        selectedYearFilterId,
        selectedExtraFilterId
    ) {
        filterMyListEntries(
            entries = allListemEntries,
            searchQuery = searchQuery,
            selectedStatusFilterId = selectedStatusFilterId,
            selectedTypeFilterId = selectedTypeFilterId,
            selectedFavoriteFilterId = selectedFavoriteFilterId,
            selectedScoreFilterId = selectedScoreFilterId,
            selectedYearFilterId = selectedYearFilterId,
            selectedExtraFilterId = selectedExtraFilterId
        )
    }
    // Eklenme zamanı artık modelde taşınıyor (toListemMediaEntry, addedAt = AniList
    // createdAt): "Son/İlk eklenen" için buradaki özel durum kalktı, tek motor
    // (applySort) her iki ekranda da aynı sözleşmeyle sıralıyor.
    val visibleEntries = remember(filteredEntries, selectedSortId, appSettings.titleLanguage) {
        applySort(filteredEntries, selectedSortId, appSettings.titleLanguage)
    }
    val groupedVisibleEntries = remember(visibleEntries, selectedGroupId) {
        groupMyListEntries(visibleEntries, selectedGroupId)
    }

    val hasActiveFilters = selectedStatusFilterId != "all" ||
        selectedTypeFilterId != "all" ||
        selectedFavoriteFilterId != "all" ||
        selectedScoreFilterId != "all" ||
        selectedYearFilterId != "all" ||
        selectedExtraFilterId != "all" ||
        selectedGroupId != MyListGroupingIds.ADDED ||
        isExplicitMyListSort(selectedSortId)

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 3 }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > previousIndex ||
                    (index == previousIndex && offset > previousOffset + 15)
                val scrollingUp = index < previousIndex ||
                    (index == previousIndex && offset < previousOffset - 15)

                when {
                    index == 0 && offset < 40 -> isCategoryFabVisible = true
                    scrollingDown -> isCategoryFabVisible = false
                    scrollingUp -> isCategoryFabVisible = true
                }
                if (scrollingDown && index >= 1) {
                    showFilterPanel = false
                    showSearchField = false
                }
                previousIndex = index
                previousOffset = offset
            }
    }

    fun openEntry(entry: MediaEntry) {
        val item = sourceItemByListEntryId[entry.id] ?: return
        // AniList ayrıntısı her zaman AniList medya kimliğiyle açılır; MAL kimliği
        // yalnızca çapraz eşleştirme için realMalId alanında tutulur.
        val stableId = item.mediaId + 100_000_000
        val result = JikanSearchResult(
            malId = stableId,
            title = item.title,
            subtitle = item.format.orEmpty(),
            type = item.mediaType,
            total = item.total,
            score = item.roundedScore(),
            isAdult = item.isAdult,
            imageUrl = item.imageUrl,
            year = item.year,
            source = "anilist",
            realMalId = item.malId,
            titleEnglish = item.titleEnglish,
            titleJapanese = item.titleNative,
            titleRomaji = item.title,
            rawScoreDouble = item.score
        )
        val localEntry = mediaEntries.firstOrNull { it.matchesInSource(result) }
        if (localEntry != null) onLocalEntryClick(localEntry) else onMediaClick(result)
    }

    val headerSubtitle = when (selectedTypeFilterId) {
        "anime" -> stringResource(R.string.profile_anilist_anime_list)
        "manga" -> stringResource(R.string.profile_anilist_manga_list)
        else -> stringResource(R.string.profile_anilist_anime_manga_list)
    }

    // Üst bar, "Listem" ile aynı hareketi paylaşsın: aşağı kaydırınca yumuşakça
    // çekilir, yukarı kaydırınca yumuşakça geri gelir.
    val headerState = com.kitsugi.animelist.ui.components.rememberScrollAwareHeaderState(
        listState = listState
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(headerState.nestedScrollConnection)
            .background(colors.background)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.background,
            shadowElevation = 0.dp
        ) {
            MyListHeaderSection(
                selectedListLayoutId = selectedListLayoutId,
                onListLayoutChange = { layoutId ->
                    selectedListLayoutId = layoutId
                    onListLayoutChange(layoutId)
                },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                showSearchField = showSearchField,
                onSearchFieldToggle = { showSearchField = !showSearchField },
                showFilterPanel = showFilterPanel,
                onFilterPanelToggle = { showFilterPanel = !showFilterPanel },
                onHideFilters = { showFilterPanel = false },
                selectedStatusFilterId = selectedStatusFilterId,
                selectedTypeFilterId = selectedTypeFilterId,
                selectedFavoriteFilterId = selectedFavoriteFilterId,
                selectedScoreFilterId = selectedScoreFilterId,
                selectedYearFilterId = selectedYearFilterId,
                selectedExtraFilterId = selectedExtraFilterId,
                selectedSortId = selectedSortId,
                selectedGroupId = selectedGroupId,
                onStatusFilterChange = { selectedStatusFilterId = it },
                onTypeFilterChange = { selectedTypeFilterId = it },
                onFavoriteFilterChange = { selectedFavoriteFilterId = it },
                onScoreFilterChange = { selectedScoreFilterId = it },
                onYearFilterChange = { selectedYearFilterId = it },
                onExtraFilterChange = { selectedExtraFilterId = it },
                onSortChange = { selectedSortId = it },
                onGroupChange = { selectedGroupId = it },
                activeTabScrollState = listState,
                accentColor = accentColor,
                horizontalPadding = horizontalPadding,
                hasActiveFilters = hasActiveFilters,
                headerCollapsed = headerState.isCollapsed,
                headerTitle = username,
                headerSubtitle = headerSubtitle,
                onBackClick = onBackClick,
                searchPlaceholder = stringResource(R.string.profile_list_search_hint)
            )
        }

        MyListSingleSourceBar(
            sourceId = "anilist",
            sourceName = "AniList",
            visibleEntries = visibleEntries,
            onEntryClick = ::openEntry,
            onEmptyMessage = { infoMessage = KitsugiStrings.get(R.string.profile_list_empty) },
            accentColor = Color(0xFF02A9FF),
            horizontalPadding = horizontalPadding
        )

        val refreshState = rememberPullToRefreshState()
        val isRefreshing = state.isLoading && state.items.isNotEmpty()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadUserMediaLibrary(userId, forceRefresh = true) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = refreshState,
            indicator = {
                KitsugiPullToRefreshIndicator(
                    state = refreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        KitsugiPlasmaLoader(size = 48.dp)
                    }
                }

                state.error != null && state.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = state.error.orEmpty(),
                                color = colors.textMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            state.errorCode?.let { code -> PlatformErrorCodeChip(code = code) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(accentColor)
                                    .tvClickable(shape = RoundedCornerShape(14.dp)) {
                                        viewModel.loadUserMediaLibrary(userId, forceRefresh = true)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_retry),
                                    color = colors.background,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                else -> {
                    val gridColumns = when {
                        isLandscape && configuration.screenWidthDp >= 900 -> 6
                        isLandscape -> 5
                        configuration.screenWidthDp >= 600 -> 4
                        else -> 3
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding)
                            .then(if (isTvDevice) Modifier.dpadVerticalFastScroll(listState) else Modifier),
                        verticalArrangement = Arrangement.Top
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = KitsugiStrings.get(R.string.inline_1_s_results_b4b4d39, visibleEntries.size),
                                color = colors.textMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                            )
                            if (state.error != null) {
                                Text(
                                    text = state.error.orEmpty(),
                                    color = accentColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                )
                                state.errorCode?.let { code ->
                                    PlatformErrorCodeChip(
                                        code = code,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                    )
                                }
                            }
                        }

                        if (visibleEntries.isEmpty()) {
                            item {
                                EmptyListResultCard(
                                    searchQuery = searchQuery,
                                    selectedStatusFilterId = selectedStatusFilterId,
                                    selectedTypeFilterId = selectedTypeFilterId,
                                    selectedFavoriteFilterId = selectedFavoriteFilterId,
                                    selectedScoreFilterId = selectedScoreFilterId,
                                    selectedYearFilterId = selectedYearFilterId,
                                    selectedExtraFilterId = selectedExtraFilterId,
                                    selectedSortId = selectedSortId
                                )
                            }
                        } else if (selectedGroupId == MyListGroupingIds.NONE ||
                            selectedStatusFilterId == "completed" ||
                            !appSettings.separatedListStyle
                        ) {
                            // "Düz liste" gruplaması, Tamamlandı filtresi ya da "Ayrılmış liste
                            // tarzını kullan" kapalıysa düz akış.
                            MyListFlatContent(
                                visibleEntries = visibleEntries,
                                selectedListLayoutId = selectedListLayoutId,
                                titleLanguage = appSettings.titleLanguage,
                                scoreFormat = appSettings.scoreFormat,
                                hideScores = appSettings.hideScores,
                                blurAdultMedia = appSettings.blurAdultMedia,
                                gridColumns = gridColumns,
                                onEntryClick = ::openEntry,
                                // Başkasının listesi değiştirilemez; düzenleme kısayolu gizlenir.
                                onPosterLongClick = { imageUrl ->
                                    activeZoomEntry = visibleEntries.firstOrNull { it.imageUrl == imageUrl }
                                }
                            )
                        } else {
                            MyListSectionedContent(
                                sections = groupedVisibleEntries,
                                selectedListLayoutId = selectedListLayoutId,
                                titleLanguage = appSettings.titleLanguage,
                                scoreFormat = appSettings.scoreFormat,
                                hideScores = appSettings.hideScores,
                                blurAdultMedia = appSettings.blurAdultMedia,
                                gridColumns = gridColumns,
                                onEntryClick = ::openEntry,
                                // Başkasının listesi değiştirilemez; düzenleme kısayolu gizlenir.
                                onPosterLongClick = { imageUrl ->
                                    activeZoomEntry = visibleEntries.firstOrNull { it.imageUrl == imageUrl }
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(90.dp)) }
                    }
                }
            }
        }
    }

    if (!isTvDevice && state.items.isNotEmpty()) {
        val navigationBarPadding = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        val floatingBottomPadding by animateDpAsState(
            targetValue = 16.dp + navigationBarPadding,
            animationSpec = tween(durationMillis = 200),
            label = "user_list_fab_bottom_padding"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = isCategoryFabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = floatingBottomPadding)
                    .zIndex(10f)
            ) {
                val activeStatusLabel = when (selectedStatusFilterId) {
                    "watching" -> stringResource(R.string.mylist_filter_watching)
                    "completed" -> stringResource(R.string.mylist_filter_completed)
                    "planned" -> stringResource(R.string.mylist_filter_planned)
                    "dropped" -> stringResource(R.string.mylist_filter_dropped)
                    "paused" -> stringResource(R.string.mylist_filter_paused)
                    "adult" -> stringResource(R.string.filter_adult)
                    "favorites" -> stringResource(R.string.mylist_filter_favorites)
                    else -> stringResource(R.string.mylist_filter_all)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(999.dp)) {
                            showStatusBottomSheet = true
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KitsugiArtIcon(art = KitsugiArt.NavList, contentDescription = stringResource(R.string.search_filter_category), size = 20.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeStatusLabel,
                            color = colors.background,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black
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
                    .padding(end = 20.dp, bottom = floatingBottomPadding)
                    .zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(16.dp)) {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.yukari_git),
                        tint = colors.background,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showStatusBottomSheet) {
        KitsugiListStatusBottomSheet(
            entries = allListemEntries,
            selectedStatusFilterId = selectedStatusFilterId,
            showAdultContent = appSettings.showAdultContent,
            onStatusSelected = { selectedStatusFilterId = it },
            onDismissRequest = { showStatusBottomSheet = false }
        )
    }

    activeZoomEntry?.let { entry ->
        entry.imageUrl?.let { imageUrl ->
            KitsugiImagePreviewDialog(
                imageUrl = imageUrl,
                title = entry.title,
                isAdult = entry.isAdult,
                onDismiss = { activeZoomEntry = null }
            )
        }
    }

    infoMessage?.let { message ->
        KitsugiInfoDialog(
            title = KitsugiStrings.get(R.string.kullanici_listesi),
            message = message,
            onDismiss = { infoMessage = null }
        )
    }
}

/** AniList kullanıcı girdisini Listem kartlarının kullandığı ortak modele dönüştürür. */
private fun UserMediaListItem.toListemMediaEntry(): MediaEntry = MediaEntry(
    id = listEntryId,
    title = title,
    subtitle = format.orEmpty(),
    type = mediaType,
    status = status,
    score = roundedScore(),
    progress = progress,
    total = total,
    isFavorite = isFavorite,
    isAdult = isAdult,
    source = "anilist",
    malId = mediaId + 100_000_000,
    imageUrl = imageUrl,
    year = year,
    startDate = startDate,
    endDate = endDate,
    priority = priority,
    isRepeating = repeatCount > 0 || status == WatchStatus.Repeating,
    repeatCount = repeatCount,
    volumeProgress = volumeProgress,
    isPrivate = isPrivate,
    isHiddenFromStatusLists = isHiddenFromStatusLists,
    updatedAt = updatedAt,
    addedAt = createdAt,
    titleEnglish = titleEnglish,
    titleJapanese = titleNative,
    aniListEntryId = listEntryId
)
