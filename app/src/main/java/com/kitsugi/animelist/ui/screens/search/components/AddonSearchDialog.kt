package com.kitsugi.animelist.ui.screens.search.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonSearchDialog(
    onDismissRequest: () -> Unit,
    onSeeAllAddonSection: ((apiName: String, title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)? = null
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var exploreApiName by rememberSaveable { mutableStateOf<String?>(null) }
    val exploreApi = remember(exploreApiName) {
        if (exploreApiName == null) null
        else APIHolder.allProviders.firstOrNull { it.name == exploreApiName }
    }
    var isLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Pair<MainAPI, SearchResponse>>>(emptyList()) }
    var activeApis by remember { mutableStateOf<List<MainAPI>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var activeDetailItem by remember { mutableStateOf<Pair<MainAPI, SearchResponse>?>(null) }

    // +18 UYUMU (2026-10-14): çoklu eklenti arama diyaloğu da göster/gizle sözleşmesine
    // uyar — yetişkin (NSFW türü veya bilinen yetişkin eklenti) sonuçlar ayar kapalıyken elenir.
    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    val visibleResults = remember(searchResults, showAdultContent) {
        if (showAdultContent) searchResults
        else searchResults.filter { !isAddonAdultResponse(it.second) }
    }

    // Load active addons
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val db = KitsugiDatabase.getDatabase(context.applicationContext)
                val enabledPlugins = db.csPluginDao().getEnabledPlugins()
                for (plugin in enabledPlugins) {
                    try {
                        CsPluginLoader.loadExtension(context, plugin.id)
                    } catch (e: Exception) {
                        Log.e("AddonSearchDialog", "Failed to load extension ${plugin.name}: ${e.message}")
                    }
                }
                // v2.4.240 (KN-D): tek normalize edilmiş eşleştirme. Eskiden burada
                // `File(api.sourcePlugin).nameWithoutExtension == plugin.id` birebir
                // karşılaştırması yapılıyordu; dosya adı ile DB id'si farklıysa (Türkçe
                // İ/ş/ğ, büyük/küçük harf) liste BOŞ kalıyor ve arama tek bir eklenti bile
                // denemeden "sonuç yok" dönüyordu.
                val enabledIds = enabledPlugins.map { it.id }
                activeApis = APIHolder.allProviders.filter { api ->
                    CsStreamRunner.isApiEnabledFor(api, enabledIds)
                }
            } catch (e: Exception) {
                Log.e("AddonSearchDialog", "Error fetching active extensions: ${e.message}")
            }
        }
    }

    val performSearch = {
        if (query.isNotBlank()) {
            // v2.4.240 (KN-D-2): yeni aramada oturum bloklarını temizle — aksi halde
            // bir önceki turda 3 hataya düşen eklenti bu aramada SESSİZCE atlanır.
            CsPluginStatusTracker.clear()
            CsStreamRunner.clearUnsupportedMethodsCache()
            isLoading = true
            hasSearched = true
            keyboardController?.hide()
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    val apiList = activeApis
                    val list = mutableListOf<Pair<MainAPI, SearchResponse>>()
                    supervisorScope {
                        val jobs = apiList.map { api ->
                            async {
                                try {
                                    val searchRes = CsStreamRunner.searchWithCfRetry(api, query)
                                    synchronized(list) {
                                        searchRes.forEach { list.add(api to it) }
                                    }
                                } catch (e: Exception) {
                                    Log.e("AddonSearchDialog", "Search failed for provider ${api.name}: ${e.message}")
                                }
                            }
                        }
                        jobs.forEach {
                            try {
                                it.await()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    list
                }
                searchResults = results
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = KitsugiColors.Background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = KitsugiColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Eklenti Arama",
                        color = KitsugiColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Input Field
                var isFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            color = if (isFocused) accentColor else KitsugiColors.Border,
                            shape = RoundedCornerShape(22.dp)
                        )
                        .background(KitsugiColors.Surface)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Search,
                            contentDescription = stringResource(R.string.ara),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )

                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            cursorBrush = SolidColor(accentColor),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = KitsugiColors.TextPrimary,
                                fontWeight = FontWeight.Normal
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    performSearch()
                                }
                            ),
                            decorationBox = { innerTextField ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.inline_search_all_installed_extensions_590b4c9),
                                        color = KitsugiColors.TextMuted,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    query = ""
                                    searchResults = emptyList()
                                    hasSearched = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Close,
                                    contentDescription = stringResource(R.string.temizle),
                                    tint = KitsugiColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selected Plugin Tag / Chip removed, click explore instead

                // Loading State
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiPlasmaLoader(size = 48.dp)
                    }
                } else {
                    // Content Area
                    if (hasSearched && visibleResults.isNotEmpty()) {
                        val groupedResults = remember(visibleResults) {
                            visibleResults.groupBy { it.first }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(groupedResults.keys.toList(), key = { "grp_${java.io.File(it.sourcePlugin ?: "").nameWithoutExtension}_${it.name}" }) { api ->
                                val itemsForApi = groupedResults[api] ?: emptyList()
                                if (itemsForApi.isNotEmpty()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = api.name,
                                            color = KitsugiColors.TextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )

                                        val snapRowState = androidx.compose.foundation.lazy.rememberLazyListState()
                                        LazyRow(
                                            state = snapRowState,
                                            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(snapRowState),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(itemsForApi) { (_, response) ->
                                                ResultItemCard(
                                                    title = response.name,
                                                    imageUrl = response.posterUrl,
                                                    apiName = api.name,
                                                    quality = response.quality?.name,
                                                    onClick = {
                                                        activeDetailItem = api to response
                                                    },
                                                    isAdult = isAddonAdultResponse(response)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (hasSearched && visibleResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.inline_no_results_found_03b621d),
                                color = KitsugiColors.TextMuted,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        // Show active addons list (similar to comments card style)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.inline_installed_extensions_select_one_to_9595e4d),
                                color = KitsugiColors.TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            if (activeApis.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.inline_no_installed_extensions_found_bd1713b),
                                        color = KitsugiColors.TextMuted
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(activeApis, key = { "api_${java.io.File(it.sourcePlugin ?: "").nameWithoutExtension}_${it.name}" }) { api ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(KitsugiColors.Surface)
                                                .clickable {
                                                    exploreApiName = api.name
                                                }
                                                .border(
                                                    width = 1.dp,
                                                    color = Color.Transparent,
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .padding(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Extension,
                                                    contentDescription = "Eklenti",
                                                    tint = accentColor,
                                                    modifier = Modifier.size(24.dp)
                                                )

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = api.name,
                                                        color = KitsugiColors.TextPrimary,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = api.mainUrl,
                                                        color = KitsugiColors.TextMuted,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Text(
                                                    text = stringResource(R.string.inline_explore_2aeb544),
                                                    color = accentColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (exploreApi != null) {
        val capturedApi = exploreApi!!
        AddonExploreDialog(
            api = capturedApi,
            onDismissRequest = { exploreApiName = null },
            onSeeAllClick = if (onSeeAllAddonSection != null) {
                { title, mainPageData, horizontalImages, initialItems ->
                    onSeeAllAddonSection(
                        capturedApi.name, title, mainPageData, horizontalImages, initialItems
                    )
                }
            } else null
        )
    }

    // ── Detail Dialog ─────────────────────────────────────────────────────────
    activeDetailItem?.let { (api, response) ->
        KitsugiAddonDetailDialog(
            api = api,
            url = response.url,
            onDismissRequest = { activeDetailItem = null }
        )
    }
}

@Composable
fun ResultItemCard(
    title: String,
    imageUrl: String?,
    apiName: String,
    quality: String?,
    onClick: () -> Unit,
    /** +18 UYUMU (2026-10-14): öğe yetişkinse görsel blurAdultMedia ile bulanıklaştırılır. */
    isAdult: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    // Bazı Türk siteleri (DiziBox, SezonlukDizi vb.) görselleri
                    // Referer header olmadan engeller. Referer olarak sitenin kendi
                    // mainUrl'ini gönderiyoruz — hotlink korumasını aşmak için.
                    // +18 UYUMU (2026-10-14): istek KitsugiNsfwImage'e verilir; bitmap-blur
                    // yolu başlıkları koruyarak dönüşümü ekler (ham görsel asla sızmaz).
                    val imageRequest = remember(imageUrl) {
                        val headers = NetworkHeaders.Builder()
                            .set(
                                "Referer",
                                try {
                                    val uri = android.net.Uri.parse(imageUrl)
                                    "${uri.scheme}://${uri.host}/"
                                } catch (_: Exception) { imageUrl }
                            )
                            .set(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                            )
                            .build()
                        coil3.request.ImageRequest.Builder(context)
                            .data(imageUrl)
                            .httpHeaders(headers)
                            .build()
                    }
                    com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                        model = imageRequest,
                        contentDescription = title,
                        isAdult = isAdult,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Extension,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Quality Badge in top-right overlay
                if (!quality.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = quality,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = apiName,
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
