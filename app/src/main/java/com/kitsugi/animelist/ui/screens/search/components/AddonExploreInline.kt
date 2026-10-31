package com.kitsugi.animelist.ui.screens.search.components

import android.util.Log
import com.kitsugi.animelist.ui.utils.uniqueItems
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiShimmerHeroSection
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.utils.KitsugiStrings

private fun TvType?.toKitsugiStreamMediaType(): MediaType? = when (this) {
    TvType.Movie, TvType.AnimeMovie -> MediaType.Movie
    TvType.TvSeries, TvType.AsianDrama, TvType.Documentary -> MediaType.TvShow
    TvType.Anime, TvType.OVA, TvType.Cartoon -> MediaType.Anime
    else -> null
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline Plugin Explore — LazyListScope extension
// Arama ekranının LazyColumn'una doğrudan item'lar ekler (diyalog açmaz)
// Birebir Keşfet/Arama sayfalarıyla aynı görsel dil: aynı hero, aynı raflar,
// aynı kutucuk, aynı shimmer, aynı hata şeridi.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * State holder — SearchScreen seviyesinde remember ile tutulur.
 * Böylece eklenti değişince tamamen sıfırlanır.
 */
class AddonExploreInlineState(
    val apiName: String,
    val scope: CoroutineScope
) {
    var homeLists by mutableStateOf<List<HomePageList>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isBlocked by mutableStateOf(false)
    var isCfProtected by mutableStateOf(false)
    var errorCode by mutableStateOf<String?>(null)

    fun load(api: MainAPI, forceRefresh: Boolean = false) {
        if (isLoading) return
        isLoading = true
        isBlocked = CsPluginStatusTracker.isBlocked(api.name)
        errorCode = CsPluginStatusTracker.getErrorCode(api.name)
        isCfProtected = CsStreamRunner.CF_PROTECTED_PLUGINS.contains(api.name) || api.usesWebView
        scope.launch {
            val cached = if (forceRefresh) null else AddonExploreCache.get(api.name)
            if (cached != null) {
                homeLists = cached
                isLoading = false
                return@launch
            }
            val dataList = withContext(Dispatchers.IO) {
                val list = mutableListOf<HomePageList>()
                try {
                    runCatching { CsStreamRunner.ensureCfClearance(api) }
                    for (pageData in api.mainPage) {
                        try {
                            val req = MainPageRequest(pageData.name, pageData.data, pageData.horizontalImages)
                            api.getMainPage(1, req)?.let { list.addAll(it.items) }
                        } catch (t: Throwable) {
                            Log.e("AddonExploreInline", "Page load failed: ${pageData.name} — ${t.message}")
                            CsPluginStatusTracker.recordSkip(
                                api.name,
                                t.localizedMessage ?: "${pageData.name}: ${t.javaClass.simpleName}",
                            )
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("AddonExploreInline", "mainPage list failed: ${t.message}")
                    CsPluginStatusTracker.recordSkip(
                        api.name,
                        t.localizedMessage ?: t.javaClass.simpleName,
                    )
                }
                list
            }
            homeLists = dataList
            AddonExploreCache.put(api.name, dataList)
            errorCode = CsPluginStatusTracker.getErrorCode(api.name)
            isLoading = false
        }
    }
}

@Composable
fun rememberAddonExploreInlineState(apiName: String): AddonExploreInlineState {
    val scope = rememberCoroutineScope()
    return remember(apiName) {
        AddonExploreInlineState(apiName = apiName, scope = scope)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LazyListScope extension — SearchScreen'e item'lar ekler
// ─────────────────────────────────────────────────────────────────────────────

fun LazyListScope.addonExploreInlineSections(
    state: AddonExploreInlineState,
    api: MainAPI,
    onSeeAllClick: ((apiName: String, title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)? = null,
    onItemClick: ((api: MainAPI, url: String) -> Unit)? = null
) {
    // ── Hero Banner — Keşfet vitrinindeki KitsugiHeroSection ile birebir aynı
    item(key = "inline_hero_${api.name}") {
        val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
        val heroItems = state.homeLists.asSequence()
            .map { shelf -> shelf.list.filter { showAdultContent || !isAddonAdultResponse(it) } }
            .firstOrNull { it.isNotEmpty() }
            ?.take(8)
            ?.map { it.toAddonSearchResult() } ?: emptyList()
        val ctx = LocalContext.current
        if (heroItems.isNotEmpty()) {
            com.kitsugi.animelist.ui.components.KitsugiHeroSection(
                items = heroItems,
                alreadyInList = { false },
                onInfoClick = { result ->
                    result.cs3Url?.let { url ->
                        if (onItemClick != null) onItemClick(api, url)
                        else {
                            com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity.start(
                                context = ctx,
                                malId = null, aniListId = null,
                                episode = 1, season = 1,
                                isMovie = false,
                                mediaType = MediaType.Anime,
                                title = result.title,
                                posterUrl = result.imageUrl,
                                cs3Url = url,
                                cs3ApiName = api.name
                            )
                        }
                    }
                },
                isVisible = true
            )
            Spacer(modifier = Modifier.height(6.dp))
        } else if (state.isLoading) {
            com.kitsugi.animelist.ui.components.KitsugiShimmerHeroSection()
            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    // ── Uyarı şeridi — Arama/Keşfet hata bandıyla aynı stil (12dp radius, 0.14f alpha)
    if (state.isBlocked || state.isCfProtected || state.errorCode != null) {
        item(key = "inline_warning_${api.name}") {
            val errorTitle = state.errorCode
                ?.let { PlatformErrorCode.fromId(it) }
                ?.let { stringResource(it.titleRes) }
            val text = when {
                state.isBlocked -> stringResource(
                    R.string.platform_error_plugin_blocked,
                    errorTitle ?: stringResource(R.string.platform_error_unknown_reason),
                    CsPluginStatusTracker.getErrorMessage(api.name)
                        ?: stringResource(R.string.platform_error_unknown_reason),
                )
                errorTitle != null -> "⚠️ $errorTitle"
                else -> stringResource(R.string.platform_error_cloudflare_warning)
            }
            val borderColor = if (state.isBlocked) KitsugiColors.AccentRed else KitsugiColors.AccentOrange
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(borderColor.copy(0.14f))
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
                    state.errorCode?.let { PlatformErrorCodeHint(code = it) }
                }
            }
        }
    }

    // ── Yükleniyor — Aynı plasma loader, aynı padding
    if (state.isLoading && state.homeLists.isEmpty()) {
        item(key = "inline_loading_${api.name}") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KitsugiPlasmaLoader(size = 48.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = KitsugiStrings.get(R.string.inline_loading_1_s_79690b1, api.name),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // ── Boş durum — Aynı empty state
    if (!state.isLoading && state.homeLists.isEmpty()) {
        item(key = "inline_empty_${api.name}") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                com.kitsugi.animelist.ui.components.KitsugiEmptyState(
                    title = stringResource(R.string.kesfet_icerigi_yuklenemedi),
                    subtitle = stringResource(R.string.inline_explore_content_couldn_t_be_61c6694),
                    icon = Icons.SearchOff
                )
            }
        }
    }

    // ── Kategori rafları — Keşfet'teki KitsugiHorizontalMediaSection ile birebir aynı
    // Eklentiler aynı adlı birden çok raf döndürebiliyor (ör. iki "Filmler" rafı).
    uniqueItems(state.homeLists, key = { "inline_cat_${api.name}_${it.name}" }) { homeList ->
        val shelfShowAdult = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
        val visibleList = homeList.list.filter { shelfShowAdult || !isAddonAdultResponse(it) }
        if (visibleList.isNotEmpty()) {
            val context = LocalContext.current
            val matchingPage = remember(homeList.name) {
                api.mainPage.firstOrNull { it.name == homeList.name }
            }
            val rowResults = remember(visibleList) {
                visibleList.map { it.toAddonSearchResult() }
            }
            com.kitsugi.animelist.ui.components.KitsugiHorizontalMediaSection(
                title = homeList.name,
                results = rowResults,
                isLoading = state.isLoading,
                alreadyInList = { false },
                onItemClick = { result ->
                    result.cs3Url?.let { url ->
                        if (onItemClick != null) onItemClick(api, url)
                        else {
                            com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity.start(
                                context = context,
                                malId = null, aniListId = null,
                                episode = 1, season = 1,
                                isMovie = false,
                                mediaType = MediaType.Anime,
                                title = result.title,
                                posterUrl = result.imageUrl,
                                cs3Url = url,
                                cs3ApiName = api.name
                            )
                        }
                    }
                },
                onSeeAllClick = if (onSeeAllClick != null && matchingPage != null) {
                    {
                        onSeeAllClick(
                            api.name,
                            homeList.name,
                            matchingPage.data,
                            matchingPage.horizontalImages,
                            visibleList
                        )
                    }
                } else null
            )
            Spacer(modifier = Modifier.height(26.dp))
        }
    }
}
