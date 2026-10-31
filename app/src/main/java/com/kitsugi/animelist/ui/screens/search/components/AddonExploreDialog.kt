@file:Suppress("UNUSED_PARAMETER")
package com.kitsugi.animelist.ui.screens.search.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

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
// Main Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonExplorePage(
    api: MainAPI,
    onBackClick: () -> Unit,
    onSeeAllClick: ((title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)? = null,
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false
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

    // +18 UYUMU (2026-10-14): eklenti portalı da uygulamanın geri kalanıyla aynı
    // göster/gizle sözleşmesine uyar — yetişkin sonuçlar ayar kapalıyken elenir.
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
                        // v2.4.239: vitrin (Öne Çıkan) yüklemesi de gate'li — CF korumalı
                        // eklentide ana sayfa istekleri clearance'siz başlayıp 403 yemesin.
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
                        // v2.4.239: eklenti sayfası araması da CF gate'li — çıplak isim
                        // sorgusundan önce clearance çözülür (sağlıklı sitede 0 ms);
                        // boş sonuç + challenge kanıtı halinde sabırlı yeniden deneme yapılır.
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

    // Vitrin öğeleri — ana sayfa (Keşfet) vitriniyle aynı model üzerinden beslenir.
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

    Surface(
            modifier = Modifier.fillMaxSize(),
            color = KitsugiColors.Background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // ── Content area ───────────────────────────────────────────
                when {
                    isHomeLoading && homeLists.isEmpty() -> {
                        // Initial loading spinner
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                KitsugiPlasmaLoader(size = 48.dp)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.api_name_yukleniyor, api.name), color = KitsugiColors.TextMuted, fontSize = 14.sp)
                            }
                        }
                    }

                    hasSearched -> {
                        // Search results
                        when {
                            isSearchLoading -> {
                                Box(
                                    Modifier.fillMaxSize().padding(top = 88.dp),
                                    contentAlignment = Alignment.Center
                                ) { KitsugiPlasmaLoader(size = 48.dp) }
                            }
                            visibleSearchResults.isEmpty() -> {
                                Box(
                                    Modifier.fillMaxSize().padding(top = 88.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(stringResource(R.string.inline_no_results_found_for_1_3ed9f56, searchQuery), color = KitsugiColors.TextMuted)
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(top = 88.dp),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    item {
                                        Text(
                                            stringResource(R.string.inline_1_s_2_s_results_1656e6f, searchQuery, visibleSearchResults.size),
                                            color = KitsugiColors.TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                    items(visibleSearchResults.chunked(3)) { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            row.forEach { item ->
                                                Box(Modifier.weight(1f)) {
                                                    com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard(
                                                        result = item.toAddonSearchResult(),
                                                        onClick = {
                                                            activeDetailUrl = item.url
                                                        },
                                                        titleLanguage = titleLanguage,
                                                        scoreFormat = scoreFormat,
                                                        hideScores = hideScores,
                                                        blurAdultMedia = blurAdultMedia,
                                                        forceVertical = true
                                                    )
                                                }
                                            }
                                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Home feed — ana sayfa (Keşfet) ile birebir aynı bileşenler:
                        // KitsugiHeroSection vitrini + KitsugiHorizontalMediaSection rafları.
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            // Vitrin (hero carousel) — ana sayfadakiyle aynı sunum
                            item(key = "hero") {
                                com.kitsugi.animelist.ui.components.KitsugiHeroSection(
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

                            // Warning pill (CF / blocked)
                            if (isBlocked || isCfProtected || errorCode != null) {
                                item(key = "warning") {
                                    AddonStatusPill(
                                        isBlocked = isBlocked,
                                        blockReason = if (isBlocked || errorCode != null)
                                            CsPluginStatusTracker.getErrorMessage(api.name) ?: "Ağ hatası"
                                        else null,
                                        errorCode = errorCode,
                                    )
                                }
                            }

                            // Empty state
                            if (homeLists.isEmpty() && !isHomeLoading) {
                                item(key = "empty") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(R.string.kesfet_icerigi_yuklenemedi), color = KitsugiColors.TextMuted)
                                    }
                                }
                            }

                            // Kategori rafları — ana sayfadaki yatay medya raflarıyla aynı:
                            // aynı kutucuk (KitsugiExploreMediaCard), aynı kaydırma/snap
                            // mekaniği, aynı "Tümünü Gör" sözleşmesi.
                            items(homeLists, key = { "cat_${homeLists.indexOf(it)}_${it.name}" }) { homeList ->
                                val rowResults = remember(homeList, showAdultContent) {
                                    homeList.list.map { it.toAddonSearchResult() }
                                        .filter { showAdultContent || !it.isAdult }
                                }
                                if (rowResults.isNotEmpty()) {
                                    val matchingPage = remember(homeList.name) {
                                        api.mainPage.firstOrNull { it.name == homeList.name }
                                    }
                                    com.kitsugi.animelist.ui.components.KitsugiHorizontalMediaSection(
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

                // ── Floating search bar (always on top) ────────────────────
                AddonFloatingSearchBar(
                    apiName = api.name,
                    searchQuery = searchQuery,
                    hasSearched = hasSearched,
                    onQueryChange = { searchQuery = it },
                    onSearch = { performSearch() },
                    onClear = {
                        searchQuery = ""
                        searchResults = emptyList()
                        hasSearched = false
                        keyboardController?.hide()
                    },
                    onBack = {
                        if (hasSearched) {
                            searchQuery = ""
                            searchResults = emptyList()
                            hasSearched = false
                            keyboardController?.hide()
                        } else {
                            onBackClick()
                        }
                    },
                    onRefresh = { loadHomeFeed(true) },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

    // ── Detail Dialog (opens when a content card is tapped) ──────────────────
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
    onSeeAllClick: ((title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)? = null
) {
    AddonExplorePage(
        api = api,
        onBackClick = onDismissRequest,
        onSeeAllClick = onSeeAllClick
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating Search Bar — üstte sabit, transparan arka plan
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddonFloatingSearchBar(
    apiName: String,
    searchQuery: String,
    hasSearched: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Geri / Temizle butonu
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.45f))
                .clickable { if (hasSearched) onClear() else onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.ArrowBack, "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // Arama alanı
        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Color.Black.copy(0.55f))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Search, null, tint = Color.White.copy(0.65f), modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    cursorBrush = SolidColor(accentColor),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) Text(stringResource(R.string.ara), color = Color.White.copy(0.45f), style = MaterialTheme.typography.bodyMedium)
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(0.2f)).clickable { onClear() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Yenile butonu
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.45f))
                .clickable { onRefresh() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Refresh, "Yenile", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
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
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(10.dp)
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            if (errorCode != null) PlatformErrorCodeHint(code = errorCode)
        }
    }
}