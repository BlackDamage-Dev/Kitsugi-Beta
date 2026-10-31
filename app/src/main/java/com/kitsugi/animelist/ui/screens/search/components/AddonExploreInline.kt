package com.kitsugi.animelist.ui.screens.search.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.util.Log
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.model.MediaType
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
import com.kitsugi.animelist.R
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
                    // v2.4.239: satır-içi eklenti vitrini de CF gate'li.
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
    // ── Hero Banner ─────────────────────────────────────────────────────────
    item(key = "inline_hero_${api.name}") {
        // +18 UYUMU (2026-10-14): vitrin öğesi de göster/gizle sözleşmesine uyar.
        val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
        val heroItem = state.homeLists.asSequence()
            .map { shelf -> shelf.list.filter { showAdultContent || !isAddonAdultResponse(it) } }
            .firstOrNull { it.isNotEmpty() }
            ?.firstOrNull()
        val accentColor = LocalKitsugiAccent.current
        val context = LocalContext.current

        InlineHeroBanner(
            item = heroItem,
            isAdult = isAddonAdultResponse(heroItem),
            apiName = api.name,
            accentColor = accentColor,
            onPlayClick = {
                heroItem?.let { item ->
                    if (onItemClick != null) {
                        onItemClick(api, item.url)
                    } else {
                        com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity.start(
                            context = context,
                            malId = null, aniListId = null,
                            episode = 1, season = 1,
                            isMovie = item.type == TvType.Movie || item.type == TvType.AnimeMovie,
                            mediaType = item.type.toKitsugiStreamMediaType(),
                            title = item.name,
                            posterUrl = item.posterUrl,
                            cs3Url = item.url,
                            cs3ApiName = api.name
                        )
                    }
                }
            },
            onRefresh = { state.load(api, forceRefresh = true) }
        )
    }

    // ── Uyarı Pill'i (CF / Engel) ──────────────────────────────────────────
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
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(borderColor.copy(0.12f))
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
                    state.errorCode?.let { PlatformErrorCodeHint(code = it) }
                }
            }
        }
    }

    // ── Yükleniyor ──────────────────────────────────────────────────────────
    if (state.isLoading && state.homeLists.isEmpty()) {
        item(key = "inline_loading_${api.name}") {
            val accentColor = LocalKitsugiAccent.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KitsugiPlasmaLoader(size = 48.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(KitsugiStrings.get(R.string.inline_loading_1_s_79690b1, api.name), color = KitsugiColors.TextMuted, fontSize = 14.sp)
                }
            }
        }
    }

    // ── Boş Durum ──────────────────────────────────────────────────────────
    if (!state.isLoading && state.homeLists.isEmpty()) {
        item(key = "inline_empty_${api.name}") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(KitsugiStrings.get(R.string.inline_explore_content_couldn_t_be_61c6694), color = KitsugiColors.TextMuted)
            }
        }
    }

    // ── Kategori Satırları ──────────────────────────────────────────────────
    items(state.homeLists, key = { "inline_cat_${api.name}_${it.name}" }) { homeList ->
        // +18 UYUMU (2026-10-14): raf tamamen yetişkin içerikten oluşuyorsa ve ayar
        // kapalıysa raf hiç çizilmez (boş başlık bırakılmaz).
        val shelfShowAdult = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
        if (homeList.list.any { shelfShowAdult || !isAddonAdultResponse(it) }) {
            val accentColor = LocalKitsugiAccent.current
            val context = LocalContext.current
            val matchingPage = remember(homeList.name) {
                api.mainPage.firstOrNull { it.name == homeList.name }
            }
            InlineCategoryRow(
                homeList = homeList,
                api = api,
                accentColor = accentColor,
                context = context,
                matchingPage = matchingPage,
                onSeeAllClick = onSeeAllClick,
                onItemClick = onItemClick
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bileşenler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InlineHeroBanner(
    item: SearchResponse?,
    apiName: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onPlayClick: () -> Unit,
    onRefresh: () -> Unit,
    /** +18 UYUMU (2026-10-14): öğe yetişkinse poster blurAdultMedia ile bulanıklaştırılır. */
    isAdult: Boolean = false
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        // Poster
        if (item != null && !item.posterUrl.isNullOrBlank()) {
            val req = remember(item.posterUrl) {
                val headers = NetworkHeaders.Builder()
                    .set(
                        "Referer",
                        try {
                            val u = android.net.Uri.parse(item.posterUrl)
                            "${u.scheme}://${u.host}/"
                        } catch (_: Exception) { item.posterUrl!! }
                    )
                    .set("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()
                coil3.request.ImageRequest.Builder(context)
                    .data(item.posterUrl!!)
                    .httpHeaders(headers)
                    .build()
            }
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = req,
                contentDescription = item.name,
                isAdult = isAdult,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().background(KitsugiColors.Surface))
        }

        // Alt gradient
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(0.2f),
                    1f to Color.Black.copy(0.88f)
                )
            )
        )

        // Sağ üst: eklenti adı + yenile
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(0.55f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Extension,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = apiName,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(0.45f))
                    .clickable { onRefresh() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Refresh, "Yenile", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        // Alt: başlık + oynat
        if (item != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable { onPlayClick() }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                ) {
                    Text("▶  Oynat", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun InlineCategoryRow(
    homeList: HomePageList,
    api: MainAPI,
    accentColor: androidx.compose.ui.graphics.Color,
    context: android.content.Context,
    matchingPage: com.lagradost.cloudstream3.MainPageData?,
    onSeeAllClick: ((apiName: String, title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)?,
    onItemClick: ((api: MainAPI, url: String) -> Unit)? = null
) {
    // +18 UYUMU (2026-10-14): satır öğeleri ve "Tümünü Gör" başlangıç listesi de
    // göster/gizle sözleşmesine uyar.
    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    val visibleItems = remember(homeList, showAdultContent) {
        homeList.list.filter { showAdultContent || !isAddonAdultResponse(it) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Başlık + Tümü →
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = homeList.name,
                color = KitsugiColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onSeeAllClick != null && matchingPage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onSeeAllClick(
                                api.name,
                                homeList.name,
                                matchingPage.data,
                                matchingPage.horizontalImages,
                                visibleItems
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.tumu), color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(
                        imageVector = Icons.ChevronRight,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Yatay poster listesi
        val snapRowState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyRow(
            state = snapRowState,
            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(snapRowState),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(visibleItems) { item ->
                ResultItemCard(
                    title = item.name,
                    imageUrl = item.posterUrl,
                    apiName = api.name,
                    quality = item.quality?.name,
                    isAdult = isAddonAdultResponse(item),
                    onClick = {
                        if (onItemClick != null) {
                            onItemClick(api, item.url)
                        } else {
                            com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity.start(
                                context = context,
                                malId = null, aniListId = null,
                                episode = 1, season = 1,
                                isMovie = item.type == TvType.Movie || item.type == TvType.AnimeMovie,
                                mediaType = item.type.toKitsugiStreamMediaType(),
                                title = item.name,
                                posterUrl = item.posterUrl,
                                cs3Url = item.url,
                                cs3ApiName = api.name
                            )
                        }
                    }
                )
            }
        }
    }
}
