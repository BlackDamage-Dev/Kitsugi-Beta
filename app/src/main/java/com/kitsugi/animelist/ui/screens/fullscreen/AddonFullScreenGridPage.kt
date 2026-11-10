@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kitsugi.animelist.ui.screens.fullscreen

import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import com.kitsugi.animelist.ui.utils.uniqueItemsIndexed
import com.kitsugi.animelist.ui.utils.uniqueItems
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.app.AddonFullScreenGridState
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiRankingMediaCard
import com.kitsugi.animelist.ui.screens.detail.KitsugiStudioFilterBottomSheet
import com.kitsugi.animelist.ui.screens.detail.StudioSortOption
import com.kitsugi.animelist.ui.screens.detail.studioTypeMatches
import com.kitsugi.animelist.ui.screens.search.components.ADDON_TYPE_FILTERS
import com.kitsugi.animelist.ui.screens.search.components.toAddonSearchResult
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainPageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Full-screen grid page for a single Cloudstream addon category.
 *
 * Ana sayfa / keşfet "Tümünü Gör" sayfalarıyla (FullScreenMediaGridPage,
 * StudioDetailPage) BİREBİR aynı görsel dil: emojili tür çipleri, filtre +
 * sıralama bottom sheet'i, grid ↔ liste görünümü, neon çerçeveli
 * KitsugiExploreMediaCard kutucukları, kaydırınca beliren üst şerit ve
 * yukarı kaydırma FAB'ı.
 */
@Composable
fun AddonFullScreenGridPage(
    state: AddonFullScreenGridState,
    onBackClick: () -> Unit,
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()

    // ── Layout ──────────────────────────────────────────────────────────────
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columnCount = rememberKitsugiGridColumns()

    // ── State ───────────────────────────────────────────────────────────────
    var loadedItems by remember { mutableStateOf(state.cachedItems ?: state.initialItems) }
    var currentPage by remember { mutableIntStateOf(state.cachedPage ?: if (state.initialItems.isEmpty()) 0 else 1) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(state.cachedHasMore ?: state.paginate) }
    var loadError by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.SideEffect {
        state.cachedItems = loadedItems
        state.cachedPage = currentPage
        state.cachedHasMore = hasMorePages
    }
    var apiReady by remember { mutableStateOf(false) }
    var activeDetailUrl by remember { mutableStateOf<String?>(null) }
    var activeDetailApiName by remember { mutableStateOf<String?>(null) }

    // ── Görünüm / filtre / sıralama (keşfet sayfalarıyla aynı sözleşme) ──────
    val gridPrefs = remember(context) { context.getSharedPreferences("kitsugi_ui_prefs", android.content.Context.MODE_PRIVATE) }
    var isGridView by remember { mutableStateOf(gridPrefs.getBoolean("addon_fullscreen_grid_is_grid_view", true)) }
    LaunchedEffect(isGridView) {
        gridPrefs.edit().putBoolean("addon_fullscreen_grid_is_grid_view", isGridView).apply()
    }
    var selectedTypeId by rememberSaveable { mutableStateOf("ALL") }
    var selectedSortOption by rememberSaveable { mutableStateOf(StudioSortOption.DEFAULT) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val hasActiveFilter = selectedTypeId != "ALL" || selectedSortOption != StudioSortOption.DEFAULT

    // CS3 öğelerini ana sayfa kart modeliyle aynı dile çevir
    val convertedResults = remember(loadedItems) {
        loadedItems.map { it.toAddonSearchResult() }
    }
    // +18 UYUMU (2026-10-14): "Tümünü Gör" ızgarası da göster/gizle sözleşmesine uyar.
    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    val displayedResults = remember(convertedResults, selectedTypeId, selectedSortOption, showAdultContent) {
        val filtered = convertedResults
            .filter { showAdultContent || !it.isAdult }
            .filter { studioTypeMatches(selectedTypeId, it.type) }
        when (selectedSortOption) {
            StudioSortOption.DEFAULT -> filtered
            StudioSortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            StudioSortOption.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
        }
    }

    // Resolve the MainAPI instance (may need to load the plugin first)
    LaunchedEffect(state.apiName) {
        withContext(Dispatchers.IO) {
            runCatching {
                val db = KitsugiDatabase.getDatabase(context.applicationContext)
                val plugins = db.csPluginDao().getEnabledPlugins()
                for (plugin in plugins) {
                    runCatching { CsPluginLoader.loadExtension(context, plugin.id) }
                }
            }
        }
        apiReady = true
    }

    fun resolveApi() = APIHolder.allProviders.firstOrNull { it.name.equals(state.apiName, ignoreCase = true) }

    fun loadNextPage() {
        if (isLoadingMore || !hasMorePages) return
        // Arama sonucu ızgarası (paginate=false): tüm sonuçlar zaten seed edildi;
        // boş mainPageData ile getMainPage çağırmak alakasız ana sayfa verisi yüklerdi.
        if (!state.paginate) { hasMorePages = false; return }
        val api = resolveApi() ?: run {
            loadError = context.getString(R.string.addon_page_not_found, state.apiName)
            return
        }
        isLoadingMore = true
        loadError = null
        scope.launch {
            try {
                val nextPage = currentPage + 1
                val request = MainPageRequest(state.title, state.mainPageData, state.horizontalImages)
                val response = withContext(Dispatchers.IO) {
                    runCatching { api.getMainPage(nextPage, request) }.getOrNull()
                }
                if (response != null) {
                    val newItems = response.items.flatMap { it.list }
                    if (newItems.isNotEmpty()) {
                        loadedItems = (loadedItems + newItems).distinctBy { it.url }
                        currentPage = nextPage
                    }
                    // Respect the plugin's own hasNext flag (CS3 standard)
                    hasMorePages = response.hasNext
                } else {
                    hasMorePages = false
                }
            } catch (e: Exception) {
                Log.e("AddonFullScreenGridPage", "Pagination failed: ${e.message}")
                loadError = e.message ?: context.getString(R.string.addon_page_load_error)
            } finally {
                isLoadingMore = false
            }
        }
    }

    // Initial load if no items were pre-seeded
    LaunchedEffect(apiReady) {
        if (apiReady && loadedItems.isEmpty()) {
            loadNextPage()
        }
    }

    // ── Grid scroll + auto-load trigger ─────────────────────────────────────
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = if (isGridView) gridState.layoutInfo else null
            if (info != null) {
                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                last.index >= info.totalItemsCount - 6
            } else {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                last.index >= listState.layoutInfo.totalItemsCount - 6
            }
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore && apiReady) loadNextPage() }

    val showFloatingHeader by remember(isGridView) {
        derivedStateOf {
            if (isGridView) gridState.firstVisibleItemIndex >= 1
            else listState.firstVisibleItemIndex >= 1
        }
    }
    val showScrollToTop by remember(isGridView) {
        derivedStateOf {
            if (isGridView) gridState.firstVisibleItemIndex > 3
            else listState.firstVisibleItemIndex > 3
        }
    }

    val openDetail: (JikanSearchResult) -> Unit = { result ->
        activeDetailUrl = result.cs3Url
        activeDetailApiName = state.apiName
    }

    val toggleView: () -> Unit = {
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
    }

    // ── Başlık + kontroller + çipler (keşfet "Tümünü Gör" başlığıyla aynı) ────
    val controlsHeader: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBackClick) {
                    Text(stringResource(R.string.geri), color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                // Eklenti adı badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KitsugiColors.Surface)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Extension,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = state.apiName,
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Filtre ve Sıralama butonu
                IconButton(
                    onClick = { showFilterSheet = true },
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
                // Grid ↔ Liste toggle
                IconButton(
                    onClick = toggleView,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Surface)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.ListAlt else Icons.GridView,
                        contentDescription = if (isGridView) stringResource(R.string.cd_list_view) else stringResource(R.string.cd_grid_view),
                        tint = accentColor
                    )
                }
            }

            // Emojili hızlı tür çipleri (Nested LazyRow ana ızgarayı yeniden ölçüyordu)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 12.dp, bottom = 4.dp)
            ) {
                ADDON_TYPE_FILTERS.forEach { typeFilter ->
                    val isSelected = selectedTypeId == typeFilter.id
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
                            .clickable {
                                selectedTypeId = if (isSelected && typeFilter.id != "ALL") "ALL" else typeFilter.id
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = typeFilter.displayLabel(),
                            color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Sayaç + aktif filtre açıklaması + Temizle
            val activeType = ADDON_TYPE_FILTERS.firstOrNull { it.id == selectedTypeId }
            val activeFilterDesc = buildString {
                val activeTypeLabel = activeType?.takeIf { it.id != "ALL" }?.displayLabel()
            val activeSortLabel = if (selectedSortOption != StudioSortOption.DEFAULT) selectedSortOption.displayLabel() else null
            activeTypeLabel?.let { append(" • $it") }
            activeSortLabel?.let { append(" • $it") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.studio_page_item_count, displayedResults.size) + activeFilterDesc,
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
                            selectedTypeId = "ALL"
                            selectedSortOption = StudioSortOption.DEFAULT
                        }
                    )
                }
            }
        }
    }

    val loadingFooter: @Composable () -> Unit = {
        if (isLoadingMore) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) { KitsugiPlasmaLoader(size = 46.dp) }
        }
    }

    val errorFooter: @Composable () -> Unit = {
        if (loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = loadError ?: stringResource(R.string.addon_page_generic_error),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { loadNextPage() }) {
                    Text("Tekrar Dene", color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Header ──────────────────────────────────────────────────
                item(key = "addon_grid_header", span = { GridItemSpan(maxLineSpan) }) {
                    controlsHeader()
                }

                // ── Empty state ──────────────────────────────────────────────
                if (displayedResults.isEmpty() && !isLoadingMore) {
                    item(key = "addon_grid_empty", span = { GridItemSpan(maxLineSpan) }) {
                        KitsugiEmptyState(
                            title = stringResource(R.string.henuz_icerik_yok),
                            subtitle = stringResource(R.string.addon_page_empty_subtitle),
                            icon = Icons.SearchOff
                        )
                    }
                }

                // ── Items — ana sayfa kutucuklarıyla birebir aynı kart ───────
                // CS3 eklentileri aynı URL'yi bir sayfada / sayfalar arasında tekrar döndürebiliyor.
                uniqueItems(
                    displayedResults,
                    key = { result -> "${state.apiName}_${result.cs3Url}" }
                ) { result ->
                    KitsugiExploreMediaCard(
                        result = result,
                        onClick = { openDetail(result) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores,
                        blurAdultMedia = blurAdultMedia,
                        forceVertical = !isLandscape
                    )
                }

                // ── Loading indicator ─────────────────────────────────────────
                item(key = "addon_grid_loading", span = { GridItemSpan(maxLineSpan) }) {
                    loadingFooter()
                }

                // ── Error / retry ────────────────────────────────────────────
                item(key = "addon_grid_error", span = { GridItemSpan(maxLineSpan) }) {
                    errorFooter()
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "addon_list_header") { controlsHeader() }

                if (displayedResults.isEmpty() && !isLoadingMore) {
                    item(key = "addon_list_empty") {
                        KitsugiEmptyState(
                            title = stringResource(R.string.henuz_icerik_yok),
                            subtitle = stringResource(R.string.addon_page_empty_subtitle),
                            icon = Icons.SearchOff
                        )
                    }
                }

                uniqueItemsIndexed(
                    displayedResults,
                    key = { _, result -> "${state.apiName}_${result.cs3Url}_list" }
                ) { index, result ->
                    KitsugiRankingMediaCard(
                        result = result,
                        rankIndex = index + 1,
                        onClick = { openDetail(result) },
                        titleLanguage = titleLanguage,
                        hideScores = hideScores,
                        blurAdultMedia = blurAdultMedia
                    )
                }

                item(key = "addon_list_loading") { loadingFooter() }
                item(key = "addon_list_error") { errorFooter() }
            }
        }

        // ── Floating header ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showFloatingHeader,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(KitsugiColors.Surface.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.ArrowBack,
                        contentDescription = stringResource(R.string.geri),
                        tint = KitsugiColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Eklenti adı — floating header'da
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.Surface)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Extension,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = state.apiName,
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hasActiveFilter) accentColor.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.Transparent)
                ) {
                    Icon(
                        Icons.FilterList,
                        contentDescription = stringResource(R.string.filtre_ve_siralama),
                        tint = if (hasActiveFilter) accentColor else KitsugiColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = toggleView) {
                    Icon(
                        imageVector = if (isGridView) Icons.ListAlt else Icons.GridView,
                        contentDescription = if (isGridView) KitsugiStrings.get(R.string.liste) else "Grid",
                        tint = accentColor
                    )
                }
            }
        }

        // ── Scroll to top FAB ─────────────────────────────────────────────────
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
                            if (isGridView) gridState.animateScrollToItem(0)
                            else listState.animateScrollToItem(0)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.cd_scroll_top),
                    tint = KitsugiColors.Background,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // ── Filtre ve Sıralama bottom sheet (keşfet/stüdyo sayfalarıyla aynı) ─────
    if (showFilterSheet) {
        KitsugiStudioFilterBottomSheet(
            initialTypeId = selectedTypeId,
            initialSortOption = selectedSortOption,
            onDismissRequest = { showFilterSheet = false },
            onApply = { typeId, sortOption ->
                selectedTypeId = typeId
                selectedSortOption = sortOption
            },
            onReset = {
                selectedTypeId = "ALL"
                selectedSortOption = StudioSortOption.DEFAULT
            },
            typeFilters = ADDON_TYPE_FILTERS
        )
    }

    // ── Detail Dialog ───────────────────────────────────────────────────
    activeDetailUrl?.let { detailUrl ->
        val detailApi = remember(activeDetailApiName) {
            APIHolder.allProviders.firstOrNull { it.name.equals(activeDetailApiName, ignoreCase = true) }
        }
        if (detailApi != null) {
            com.kitsugi.animelist.ui.screens.search.components.KitsugiAddonDetailDialog(
                api = detailApi,
                url = detailUrl,
                onDismissRequest = { activeDetailUrl = null; activeDetailApiName = null }
            )
        }
    }
}
