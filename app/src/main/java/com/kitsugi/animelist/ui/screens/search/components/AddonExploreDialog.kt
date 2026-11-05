@file:Suppress("UNUSED_PARAMETER")
package com.kitsugi.animelist.ui.screens.search.components

import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.ui.utils.uniqueItems
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.ui.components.*
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.ui.utils.tvClickable

// ─────────────────────────────────────────────────────────────────────────────
// In-memory cache
// ─────────────────────────────────────────────────────────────────────────────

object AddonExploreCache {
    private val cache = BoundedCache<String, List<HomePageList>>("addon.explore", 12)
    fun get(apiName: String): List<HomePageList>? = cache[apiName]
    fun put(apiName: String, data: List<HomePageList>) { cache[apiName] = data }
    fun clear(apiName: String) { cache.remove(apiName) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Page — Keşfet / Arama sayfalarıyla birebir aynı tema
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AddonExplorePage(
    api: MainAPI,
    onBackClick: () -> Unit,
    onSeeAllClick: ((title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)? = null,
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false,
    initialQuery: String = ""
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResponse>>(emptyList()) }
    var isHomeLoading by remember { mutableStateOf(false) }
    var homeLists by remember { mutableStateOf<List<HomePageList>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var activeDetailUrl by remember { mutableStateOf<String?>(null) }

    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    val visibleSearchResults = remember(searchResults, showAdultContent) {
        if (showAdultContent) searchResults else searchResults.filter { !isAddonAdultResponse(it) }
    }

    BackHandler {
        when {
            activeDetailUrl != null -> activeDetailUrl = null
            hasSearched -> {
                searchQuery = ""
                searchResults = emptyList()
                hasSearched = false
                keyboardController?.hide()
            }
            else -> onBackClick()
        }
    }

    val loadHomeFeed = { forceRefresh: Boolean ->
        isHomeLoading = true
        scope.launch {
            val cached = if (forceRefresh) null else AddonExploreCache.get(api.name)
            if (cached != null) {
                homeLists = cached
                isHomeLoading = false
            } else {
                val dataList = withContext(Dispatchers.IO) {
                    val list = mutableListOf<HomePageList>()
                    try {
                        runCatching { CsStreamRunner.ensureCfClearance(api) }
                        for (pageData in api.mainPage) {
                            try {
                                val req = MainPageRequest(pageData.name, pageData.data, pageData.horizontalImages)
                                api.getMainPage(1, req)?.let { list.addAll(it.items) }
                            } catch (t: Throwable) {
                                Log.e("AddonExploreDialog", "Page load failed: ${pageData.name} — ${t.message}")
                                CsPluginStatusTracker.recordSkip(
                                    api.name,
                                    t.localizedMessage ?: "${pageData.name}: ${t.javaClass.simpleName}",
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("AddonExploreDialog", "mainPage list failed: ${t.message}")
                        CsPluginStatusTracker.recordSkip(
                            api.name,
                            t.localizedMessage ?: t.javaClass.simpleName,
                        )
                    }
                    list
                }
                homeLists = dataList
                AddonExploreCache.put(api.name, dataList)
                isHomeLoading = false
            }
        }
    }

    LaunchedEffect(api.name) { loadHomeFeed(false) }

    val performSearch = {
        if (searchQuery.isNotBlank()) {
            isSearchLoading = true
            hasSearched = true
            keyboardController?.hide()
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    try {
                        runCatching { CsStreamRunner.ensureCfClearance(api) }
                        CsStreamRunner.searchWithCfRetry(api, searchQuery)
                    } catch (t: Throwable) {
                        CsPluginStatusTracker.recordSkip(
                            api.name,
                            t.localizedMessage ?: t.javaClass.simpleName,
                        )
                        emptyList()
                    }
                }
                searchResults = results
                isSearchLoading = false
            }
        }
    }

    // "Tümünü Gör" ile önceden doldurulmuş sorgu geldiyse otomatik arama yap
    // (ana aramadaki kaynağa özel "Tümünü Gör" sayfasının eklenti karşılığı).
    LaunchedEffect(api.name, initialQuery) {
        if (initialQuery.isNotBlank()) {
            searchQuery = initialQuery
            performSearch()
        }
    }

    val heroResults = remember(homeLists, showAdultContent) {
        homeLists.asSequence()
            .map { shelf -> shelf.list.filter { showAdultContent || !isAddonAdultResponse(it) } }
            .firstOrNull { it.isNotEmpty() }
            ?.take(10)
            ?.map { it.toAddonSearchResult() } ?: emptyList()
    }
    val isBlocked = CsPluginStatusTracker.isBlocked(api.name)
    val errorCode = CsPluginStatusTracker.getErrorCode(api.name)
    val isCfProtected = remember(api.name) {
        CsStreamRunner.CF_PROTECTED_PLUGINS.contains(api.name) || api.usesWebView
    }

    val lazyListState = com.kitsugi.animelist.ui.utils.rememberRetainedLazyListState(
        contentReady = homeLists.isNotEmpty() || hasSearched
    )
    val pullRefreshState = rememberPullToRefreshState()
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val showFab by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 1 } }
    val anyLoading = isHomeLoading || isSearchLoading
    val placeholder = stringResource(R.string.search_placeholder_default)

    KitsugiShimmerProvider(active = anyLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KitsugiColors.Background)
        ) {
            PullToRefreshBox(
                isRefreshing = isHomeLoading,
                onRefresh = { loadHomeFeed(true) },
                modifier = Modifier.fillMaxSize(),
                state = pullRefreshState,
                indicator = {
                    KitsugiPullToRefreshIndicator(
                        state = pullRefreshState,
                        isRefreshing = isHomeLoading,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    // ── Üst Başlık: Geri + Eklenti adı + açıklama — Keşfet başlığıyla aynı stil
                    item(key = "addon_top_header") {
                        // Keşfet başlığıyla birebir: 28dp üst boşluk, headlineLarge kalın, 18dp alt boşluk
                        Spacer(modifier = Modifier.height(28.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.ArrowBack,
                                    contentDescription = stringResource(R.string.geri),
                                    tint = KitsugiColors.TextPrimary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Extension,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = api.name,
                                        color = KitsugiColors.TextPrimary,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = api.mainUrl,
                                    color = KitsugiColors.TextMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    // ── Arama çubuğu — Ana Arama sayfasındaki KitsugiCosmicSearchBar ile birebir aynı
                    item(key = "addon_search_bar") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KitsugiCosmicSearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { performSearch() },
                                onClearQuery = {
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    hasSearched = false
                                    keyboardController?.hide()
                                },
                                placeholder = if (searchQuery.isBlank()) "${api.name} içinde ara…" else placeholder,
                                modifier = Modifier.weight(1f)
                            )
                            CosmicCompanionButton(
                                onClick = { loadHomeFeed(true) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Refresh,
                                        contentDescription = stringResource(R.string.yenile),
                                        tint = KitsugiColors.TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    when {
                        isHomeLoading && homeLists.isEmpty() && !hasSearched -> {
                            item(key = "addon_initial_loading") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp, horizontal = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    KitsugiPlasmaLoader(size = 48.dp)
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.api_name_yukleniyor, api.name),
                                        color = KitsugiColors.TextMuted,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        hasSearched -> {
                            when {
                                isSearchLoading -> {
                                    item(key = "addon_search_loading") {
                                        // Arama sayfasındaki shimmer ile birebir aynı
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                KitsugiShimmerSearchResultList(itemCount = 4)
                                            }
                                        }
                                    }
                                }
                                visibleSearchResults.isEmpty() -> {
                                    item(key = "addon_search_empty") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            KitsugiEmptyState(
                                                title = stringResource(R.string.sonuc_bulunamadi),
                                                subtitle = stringResource(R.string.inline_no_results_found_for_1_3ed9f56, searchQuery),
                                                icon = Icons.SearchOff
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    item(key = "addon_search_header") {
                                        Text(
                                            text = stringResource(R.string.inline_1_s_2_s_results_1656e6f, searchQuery, visibleSearchResults.size),
                                            color = KitsugiColors.TextPrimary,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                        )
                                    }
                                    // Sonuçlar — Ana Arama'daki SearchResultRow ile birebir aynı kart
                                    uniqueItems(
                                        items = visibleSearchResults,
                                        key = { it.url }
                                    ) { item ->
                                        val result = item.toAddonSearchResult()
                                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                                            com.kitsugi.animelist.ui.screens.search.SearchResultRow(
                                                result = result,
                                                alreadyInList = false,
                                                mediaEntry = null,
                                                onItemClick = { activeDetailUrl = item.url },
                                                onAddClick = { activeDetailUrl = item.url },
                                                titleLanguage = titleLanguage,
                                                scoreFormat = scoreFormat,
                                                hideScores = hideScores,
                                                showAddButton = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // ── Vitrin — Keşfet'teki KitsugiHeroSection ile birebir aynı
                            if (heroResults.isNotEmpty()) {
                                item(key = "hero") {
                                    KitsugiHeroSection(
                                        items = heroResults,
                                        alreadyInList = { false },
                                        onInfoClick = { item ->
                                            item.cs3Url?.let { activeDetailUrl = it }
                                        },
                                        titleLanguage = titleLanguage,
                                        scoreFormat = scoreFormat,
                                        hideScores = hideScores,
                                        blurAdultMedia = blurAdultMedia,
                                        isVisible = true
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            } else if (isHomeLoading) {
                                item(key = "hero_shimmer") {
                                    KitsugiShimmerHeroSection()
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            } else {
                                item(key = "hero_spacer") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            // Uyarı şeridi — Arama/Keşfet hata bandıyla aynı
                            if (isBlocked || isCfProtected || errorCode != null) {
                                item(key = "warning") {
                                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                        AddonStatusPill(
                                            isBlocked = isBlocked,
                                            blockReason = if (isBlocked || errorCode != null)
                                                CsPluginStatusTracker.getErrorMessage(api.name) ?: "Ağ hatası"
                                            else null,
                                            errorCode = errorCode,
                                        )
                                    }
                                }
                            }

                            if (homeLists.isEmpty() && !isHomeLoading) {
                                item(key = "empty") {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp, horizontal = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        KitsugiEmptyState(
                                            title = stringResource(R.string.kesfet_icerigi_yuklenemedi),
                                            subtitle = stringResource(R.string.inline_no_results_found_03b621d),
                                            icon = Icons.SearchOff
                                        )
                                    }
                                }
                            }

                            // Raflar — Keşfet'teki KitsugiHorizontalMediaSection ile birebir aynı
                            uniqueItems(homeLists, key = { "cat_${it.name}" }) { homeList ->
                                val rowResults = remember(homeList, showAdultContent) {
                                    homeList.list.map { it.toAddonSearchResult() }
                                        .filter { showAdultContent || !it.isAdult }
                                }
                                if (rowResults.isNotEmpty()) {
                                    val matchingPage = remember(homeList.name) {
                                        api.mainPage.firstOrNull { it.name == homeList.name }
                                    }
                                    KitsugiHorizontalMediaSection(
                                        title = homeList.name,
                                        results = rowResults,
                                        isLoading = isHomeLoading,
                                        alreadyInList = { false },
                                        onItemClick = { item ->
                                            item.cs3Url?.let { activeDetailUrl = it }
                                        },
                                        onSeeAllClick = if (onSeeAllClick != null && matchingPage != null) {
                                            {
                                                onSeeAllClick(
                                                    homeList.name,
                                                    matchingPage.data,
                                                    matchingPage.horizontalImages,
                                                    homeList.list
                                                )
                                            }
                                        } else null,
                                        titleLanguage = titleLanguage,
                                        scoreFormat = scoreFormat,
                                        hideScores = hideScores,
                                        blurAdultMedia = blurAdultMedia
                                    )
                                    Spacer(modifier = Modifier.height(26.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Yukarı Çık FAB — Keşfet/Arama sayfalarıyla aynı
            KitsugiScrollToTopFab(
                visible = showFab,
                isLandscape = isLandscape,
                bottomBarScrollState = null,
                fallbackBottomBarVisible = true,
                onClick = {
                    scope.launch { lazyListState.animateScrollToItem(0) }
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }

    // ── Detay Diyaloğu
    activeDetailUrl?.let { detailUrl ->
        KitsugiAddonDetailDialog(
            api = api,
            url = detailUrl,
            onDismissRequest = { activeDetailUrl = null }
        )
    }
}

/**
 * Geriye dönük uyumluluk köprüsü
 */
@Composable
fun AddonExploreDialog(
    api: MainAPI,
    onDismissRequest: () -> Unit,
    onSeeAllClick: ((title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)? = null,
    initialQuery: String = ""
) {
    AddonExplorePage(
        api = api,
        onBackClick = onDismissRequest,
        onSeeAllClick = onSeeAllClick,
        initialQuery = initialQuery
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// Status Warning Pill — küçük uyarı satırı
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddonStatusPill(
    isBlocked: Boolean,
    blockReason: String?,
    errorCode: String?,
) {
    val bgColor = if (isBlocked) KitsugiColors.AccentRed.copy(0.14f) else KitsugiColors.AccentOrange.copy(0.14f)
    val borderColor = if (isBlocked) KitsugiColors.AccentRed else KitsugiColors.AccentOrange
    val errorTitle = errorCode
        ?.let { PlatformErrorCode.fromId(it) }
        ?.let { stringResource(it.titleRes) }
    val text = when {
        isBlocked -> stringResource(
            R.string.platform_error_plugin_blocked,
            errorTitle ?: stringResource(R.string.platform_error_unknown_reason),
            blockReason ?: stringResource(R.string.platform_error_unknown_reason),
        )
        errorTitle != null -> "⚠️ $errorTitle"
        else -> stringResource(R.string.platform_error_cloudflare_warning)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                color = borderColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            if (errorCode != null) PlatformErrorCodeHint(code = errorCode)
        }
    }
}
