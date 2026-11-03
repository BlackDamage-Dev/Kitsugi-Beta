package com.kitsugi.animelist.ui.screens.search



import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.components.KitsugiCosmicSearchBar
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.app.AddonFullScreenGridState
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.ui.screens.search.components.KitsugiAddonDetailDialog
import com.kitsugi.animelist.ui.screens.search.components.toAddonSearchResult
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalBlurAdultMedia
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalShowAdultContent
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.SubtitleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

private data class PluginTypeGroup(
    val label: String,
    val emoji: String,
    val types: List<TvType>
)

private val pluginTypeGroups = listOf(
    PluginTypeGroup("Filmler", "🎬", listOf(TvType.Movie, TvType.AnimeMovie, TvType.Cartoon)),
    PluginTypeGroup("Diziler", "📺", listOf(TvType.TvSeries, TvType.AsianDrama)),
    PluginTypeGroup("Animasyon", "🎌", listOf(TvType.Anime, TvType.OVA)),
    PluginTypeGroup("+18", "🔞", listOf(TvType.NSFW)),
    PluginTypeGroup("Belgeseller", "📰", listOf(TvType.Documentary)),
    PluginTypeGroup(KitsugiStrings.get(R.string.canli), "📡", listOf(TvType.Live)),
    PluginTypeGroup(KitsugiStrings.get(R.string.bangumi_tag_other), "🔖", listOf(TvType.Others))
)

// Kalıcı önbellek — sayfa geçişlerinde 0 elemana düşmeyi ve scroll sıfırlanmasını önler
private var cachedActiveApis: List<MainAPI>? = null
private var savedPickerScrollIndex: Int = 0
private var savedPickerScrollOffset: Int = 0

/**
 * Eklenti Portalı arama sonuçlarının gezinme önbelleği.
 *
 * "Tümünü Gör" ızgarasına veya detay diyaloğuna gidip geri dönüldüğünde
 * ekran yeniden kurulur; ağ aramasını tekrarlamak yerine tamamlanmış sonuçlar
 * buradan geri yüklenir (ana arama sayfasının ViewModel korumasıyla aynı ilke).
 */
private object PortalSearchCache {
    var query: String? = null
    var groupLabel: String? = null
    var generation: Int = -1
    var apiNames: List<String> = emptyList()
    var results: Map<String, List<SearchResponse>> = emptyMap()
    var completed: Set<String> = emptySet()

    fun clear() {
        query = null
        groupLabel = null
        generation = -1
        apiNames = emptyList()
        results = emptyMap()
        completed = emptySet()
    }
}

@Composable
fun PluginPickerScreen(
    onBackClick: () -> Unit,
    searchViewModel: SearchViewModel,
    navState: AppNavigationState,
    onOpenApiDetail: (JikanSearchResult) -> Unit = {},
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = LocalBlurAdultMedia.current
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val showAdultContent = LocalShowAdultContent.current

    var activeApis by remember { mutableStateOf<List<MainAPI>>(cachedActiveApis ?: emptyList()) }
    var isLoadingActiveApis by remember { mutableStateOf(cachedActiveApis == null) }
    var selectedGroupLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var queryText by rememberSaveable { mutableStateOf("") }

    // ── Ana arama sayfasıyla AYNI arama mantığı ─────────────────────────────
    // Yazarken içerik araması BAŞLAMAZ; yalnızca klavyedeki "Ara" tuşu veya
    // arama butonu (KitsugiCosmicSearchBar) aramayı tetikler. Yazarken eklenti
    // listesi ada göre süzülmeye devam eder (mevcut portal davranışı).
    var submittedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var searchGeneration by rememberSaveable { mutableIntStateOf(0) }

    // Eklenti başına ilerlemeli sonuçlar: her eklenti tamamlandıkça kendi rafı
    // dolar (ana sayfadaki çoklu-platform raflarıyla birebir aynı akış).
    val searchResults = remember { mutableStateMapOf<String, List<SearchResponse>>() }
    val completedApis = remember { mutableStateListOf<String>() }
    var searchedApis by remember { mutableStateOf<List<MainAPI>>(emptyList()) }
    var scanFinished by remember { mutableStateOf(false) }
    var activeDetailItem by remember { mutableStateOf<Pair<MainAPI, SearchResponse>?>(null) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedPickerScrollIndex,
        initialFirstVisibleItemScrollOffset = savedPickerScrollOffset
    )
    val resultsListState = rememberLazyListState()

    // Sayfadan çıkıldığında en son scroll pozisyonunu sakla
    DisposableEffect(Unit) {
        onDispose {
            savedPickerScrollIndex = listState.firstVisibleItemIndex
            savedPickerScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }

    // Geri tuşu hiyerarşik yönetimi
    BackHandler {
        when {
            submittedQuery != null -> {
                submittedQuery = null
                queryText = ""
                PortalSearchCache.clear()
            }
            queryText.isNotEmpty() -> queryText = ""
            selectedGroupLabel != null -> selectedGroupLabel = null
            else -> onBackClick()
        }
    }

    // Eklentileri yükle ve önbelleğe al
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val db = KitsugiDatabase.getDatabase(context.applicationContext)
                val enabledPlugins = db.csPluginDao().getEnabledPlugins()
                for (plugin in enabledPlugins) {
                    try {
                        CsPluginLoader.loadExtension(context, plugin.id)
                    } catch (e: Exception) {
                        Log.e("PluginPickerScreen", "Load failed: ${plugin.name} — ${e.message}")
                    }
                }
                // v2.4.240 (KN-D): tek normalize edilmiş eşleştirme (bkz. isApiEnabledFor).
                val enabledIds = enabledPlugins.map { it.id }
                val loaded = APIHolder.allProviders.filter { api ->
                    CsStreamRunner.isApiEnabledFor(api, enabledIds)
                }.sortedBy { CsStreamRunner.normalizePluginKey(it.name) }

                cachedActiveApis = loaded
                withContext(Dispatchers.Main) {
                    activeApis = loaded
                    isLoadingActiveApis = false
                }
            } catch (e: Exception) {
                Log.e("PluginPickerScreen", "Error loading extensions: ${e.message}")
                withContext(Dispatchers.Main) {
                    isLoadingActiveApis = false
                }
            }
        }
    }

    // Kategori süzgeci (arama modunda hangi eklentilerin taranacağını belirler)
    val categoryApis = remember(activeApis, selectedGroupLabel) {
        val group = pluginTypeGroups.firstOrNull { it.label == selectedGroupLabel }
        if (group == null) activeApis
        else activeApis.filter { api ->
            if (group.label == "+18") {
                api.supportedTypes.contains(TvType.NSFW) ||
                api.name.contains("18", ignoreCase = true) ||
                api.name.contains("Adult", ignoreCase = true) ||
                api.name.contains("Porn", ignoreCase = true)
            } else {
                api.supportedTypes.any { group.types.contains(it) }
            }
        }
    }

    // Liste modu: kategori + yazılan metne göre eklenti adı süzgeci (0 ms gecikme)
    val filteredApis = remember(categoryApis, queryText) {
        if (queryText.isBlank()) categoryApis
        else {
            val q = queryText.trim().lowercase()
            categoryApis.filter { api ->
                api.name.lowercase().contains(q) ||
                api.mainUrl.lowercase().contains(q) ||
                api.lang.lowercase().contains(q)
            }
        }
    }

    // ── Çapraz eklenti araması (ana arama sayfasıyla aynı mantık) ───────────
    // Onaylanan sorgu tüm aktif eklentilere PARALEL gider; her eklenti
    // tamamlandıkça kendi rafı shimmer'dan gerçek kartlara döner. Efekt
    // anahtarları değişince (yeni sorgu / kategori / eklenti listesi) önceki
    // arama coroutine iptaliyle otomatik durur.
    LaunchedEffect(submittedQuery, searchGeneration, selectedGroupLabel, activeApis) {
        val q = submittedQuery
        scanFinished = false
        if (q.isNullOrBlank()) {
            searchResults.clear()
            completedApis.clear()
            searchedApis = emptyList()
            return@LaunchedEffect
        }
        val apis = categoryApis
        if (apis.isEmpty()) {
            searchedApis = emptyList()
            // Eklenti listesi hâlâ yükleniyorsa "sonuç yok" YANIP SÖNMESİN;
            // liste gelince efekt (activeApis anahtarı) aramayı yeniden başlatır.
            scanFinished = !isLoadingActiveApis
            return@LaunchedEffect
        }

        // Gezinme dönüşü: aynı sorgu + kategori + tur tamamlanmışsa ağa gitme.
        // (Kullanıcı "Ara"ya yeniden basarsa searchGeneration artar → taze arama.)
        val cache = PortalSearchCache
        if (cache.query == q && cache.groupLabel == selectedGroupLabel &&
            cache.generation == searchGeneration &&
            cache.apiNames == apis.map { it.name } &&
            cache.completed.size == apis.size
        ) {
            searchedApis = apis
            searchResults.clear()
            searchResults.putAll(cache.results)
            completedApis.clear()
            completedApis.addAll(apis.map { it.name })
            scanFinished = true
            return@LaunchedEffect
        }

        // Yeni tur: oturum bloklarını temizle (v2.4.240 KN-D-2 ile aynı ilke).
        CsPluginStatusTracker.clear()
        CsStreamRunner.clearUnsupportedMethodsCache()
        searchedApis = apis
        searchResults.clear()
        completedApis.clear()
        cache.query = q
        cache.groupLabel = selectedGroupLabel
        cache.generation = searchGeneration
        cache.apiNames = apis.map { it.name }
        cache.results = emptyMap()
        cache.completed = emptySet()
        resultsListState.scrollToItem(0)

        supervisorScope {
            apis.forEach { api ->
                launch {
                    val res = try {
                        withContext(Dispatchers.IO) {
                            CsStreamRunner.searchWithCfRetry(api, q)
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Log.e("PluginPickerScreen", "Search failed for ${api.name}: ${e.message}")
                        emptyList()
                    }
                    searchResults[api.name] = res
                    completedApis.add(api.name)
                    cache.results = searchResults.toMap()
                    cache.completed = completedApis.toSet()
                }
            }
        }
        scanFinished = true
    }

    // Görünür sonuç sayısı (+18 sözleşmesi her yüzeyde aynı)
    val totalVisibleResults = remember(searchResults.toMap(), showAdultContent) {
        searchResults.values.sumOf { list ->
            list.count { showAdultContent || !com.kitsugi.animelist.ui.screens.search.components.isAddonAdultResponse(it) }
        }
    }
    val isScanning = submittedQuery != null && !scanFinished

    // Alfabetik hızlı indeks haritası (# ve A-Z)
    val alphabet = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val letterIndexMap = remember(filteredApis) {
        val map = mutableMapOf<String, Int>()
        filteredApis.forEachIndexed { index, api ->
            val firstChar = api.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (firstChar in 'A'..'Z') firstChar.toString() else "#"
            if (!map.containsKey(key)) {
                map[key] = index + 1 // +1 Tüm Eklentiler başlığı için
            }
        }
        map
    }
    var activeScrollerLetter by remember { mutableStateOf<String?>(null) }

    // Kaydırmaya duyarlı üst bar gizleme/gösterme
    var isHeaderVisible by rememberSaveable { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -14f && isHeaderVisible &&
                    (listState.firstVisibleItemIndex > 0 || resultsListState.firstVisibleItemIndex > 0)
                ) {
                    isHeaderVisible = false
                } else if (delta > 14f && !isHeaderVisible) {
                    isHeaderVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState, resultsListState) {
        snapshotFlow {
            (listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset) to
                (resultsListState.firstVisibleItemIndex to resultsListState.firstVisibleItemScrollOffset)
        }.collect { (listPos, resultsPos) ->
            if (listPos.first == 0 && listPos.second == 0 && resultsPos.first == 0 && resultsPos.second == 0) {
                isHeaderVisible = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = KitsugiColors.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(nestedScrollConnection)
        ) {
            // ── Üst Başlık Barı (Sabit veya Kompakt) ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.ArrowBack,
                        contentDescription = stringResource(R.string.geri),
                        tint = KitsugiColors.TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Extension,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.eklenti_portali),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (submittedQuery != null)
                        KitsugiStrings.get(R.string.inline_1_s_results_b4b4d39, totalVisibleResults)
                    else
                        "${filteredApis.size} eklenti",
                    color = KitsugiColors.TextMuted,
                    fontSize = 13.sp
                )
            }

            // ── Kaydırmaya Duyarlı Arama ve Kategori Barı (Aşağı kaydırınca gizlenir) ─
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(220))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    // Ana arama sayfasıyla BİREBİR aynı arama çubuğu bileşeni:
                    // aynı görünüm, aynı "Ara" onayı, aynı X (temizle) davranışı.
                    KitsugiCosmicSearchBar(
                        query = queryText,
                        onQueryChange = {
                            queryText = it
                            if (it.isBlank()) {
                                submittedQuery = null
                                PortalSearchCache.clear()

                            }
                        },
                        onSearch = {
                            if (queryText.isNotBlank()) {
                                submittedQuery = queryText.trim()
                                searchGeneration++
                                keyboardController?.hide()
                            }
                        },
                        onClearQuery = {
                            queryText = ""
                            submittedQuery = null
                            PortalSearchCache.clear()
                            keyboardController?.hide()
                        },
                        placeholder = KitsugiStrings.get(R.string.eklentilerde_ara_x_eklenti, activeApis.size),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Kategori Çipleri (Tümü, Filmler, Diziler, +18, Animasyon vb.)
                    val availableGroups = remember(activeApis) {
                        pluginTypeGroups.filter { group ->
                            if (group.label == "+18") {
                                activeApis.any { api ->
                                    api.supportedTypes.contains(TvType.NSFW) ||
                                    api.name.contains("18", ignoreCase = true) ||
                                    api.name.contains("Adult", ignoreCase = true) ||
                                    api.name.contains("Porn", ignoreCase = true)
                                }
                            } else {
                                activeApis.any { api -> api.supportedTypes.any { group.types.contains(it) } }
                            }
                        }
                    }

                    if (availableGroups.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PluginTypeChip(
                                label = stringResource(R.string.tumu),
                                emoji = "🌐",
                                selected = selectedGroupLabel == null,
                                accentColor = accentColor,
                                onClick = { selectedGroupLabel = null }
                            )
                            availableGroups.forEach { group ->
                                PluginTypeChip(
                                    label = group.label,
                                    emoji = group.emoji,
                                    selected = selectedGroupLabel == group.label,
                                    accentColor = accentColor,
                                    onClick = {
                                        selectedGroupLabel = if (selectedGroupLabel == group.label) null else group.label
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))

            // ── İçerik: ARAMA MODU (ana sayfa paritesi) veya LİSTE MODU ──────────────
            if (submittedQuery != null) {
                // ARAMA MODU: eklenti başına yatay raf — ana arama sayfasındaki
                // MultiSearchSection ile AYNEN aynı bileşen (shimmer, kart,
                // kaydırma, "Tümünü Gör", TV odağı dahil).
                KitsugiShimmerProvider(active = isScanning) {
                    LazyColumn(
                        state = resultsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
                    ) {
                        // İlerleme satırı: "Eklentiler taranıyor (x/y) • N sonuç"
                        if (isScanning) {
                            item(key = "portal_scan_progress") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(KitsugiColors.Surface)
                                        .border(1.dp, KitsugiColors.Border, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    KitsugiPlasmaLoader(size = 20.dp)
                                    Text(
                                        text = KitsugiStrings.get(
                                            R.string.plugin_portal_scan_progress,
                                            completedApis.size, searchedApis.size, totalVisibleResults
                                        ),
                                        color = KitsugiColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        items(searchedApis, key = { "portal_shelf_${it.name}" }) { api ->
                            val raw = searchResults[api.name].orEmpty()
                            val isLoading = api.name !in completedApis
                            val q = submittedQuery.orEmpty()

                            // Ana sayfadaki raf mantığıyla aynı: CS3 → ortak kart
                            // modeli, +18 süzgeci, alakalılık sırası + tekilleştirme.
                            val shelfResults = remember(raw, showAdultContent, q) {
                                val converted = raw.map { it.toAddonSearchResult() }
                                    .filter { showAdultContent || !it.isAdult }
                                SearchRelevance.refine(q, converted)
                            }

                            MultiSearchSection(
                                title = api.name,
                                platformId = null,
                                results = shelfResults,
                                isLoading = isLoading,
                                isAlreadyInList = { false },
                                onItemClick = { result ->
                                    val response = raw.firstOrNull { it.url == result.cs3Url }
                                    if (response != null) activeDetailItem = api to response
                                },
                                onSeeAllClick = {
                                    val byUrl = raw.associateBy { it.url }
                                    val ordered = shelfResults.mapNotNull { r -> r.cs3Url?.let { byUrl[it] } }
                                    navState.addonFullScreenGridState = AddonFullScreenGridState(
                                        title = "${api.name} — \"$q\"",
                                        apiName = api.name,
                                        initialItems = ordered,
                                        mainPageData = "",
                                        horizontalImages = false,
                                        paginate = false
                                    )
                                },
                                titleLanguage = titleLanguage,
                                scoreFormat = scoreFormat,
                                hideScores = hideScores,
                                blurAdultMedia = blurAdultMedia,
                                headerIcon = {
                                    val langFlag = try {
                                        SubtitleHelper.getFlagFromIso(api.lang) ?: "🌐"
                                    } catch (_: Exception) { "🌐" }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(accentColor.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = langFlag, fontSize = 14.sp)
                                    }
                                }
                            )
                        }

                        // Tarama bitti ve hiçbir eklenti sonuç bulamadı → ana
                        // sayfadakiyle aynı boş durum bileşeni.
                        if (scanFinished && totalVisibleResults == 0) {
                            item(key = "portal_empty") {
                                KitsugiEmptyState(
                                    title = stringResource(R.string.sonuc_bulunamadi),
                                    subtitle = "\"$submittedQuery\"",
                                    icon = Icons.SearchOff
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(40.dp)) }
                    }
                }
            } else {
                // LİSTE MODU: Ana Liste ve Sağ Alfabetik Kaydıraç
                when {
                    isLoadingActiveApis && activeApis.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiPlasmaLoader(size = 46.dp)
                        }
                    }
                    filteredApis.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (queryText.isNotEmpty())
                                    KitsugiStrings.get(R.string.ui2_plugin_query_no_match, queryText)
                                else if (selectedGroupLabel != null)
                                    "Bu kategoride aktif eklenti yok."
                                else
                                    "Aktif eklenti bulunamadı.\nAyarlar -> Eklentilerim bölümünden eklenti etkinleştirin.",
                                color = KitsugiColors.TextMuted,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 34.dp, top = 6.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (queryText.isBlank() && selectedGroupLabel == null) {
                                    item(key = "all_plugins_header") {
                                        AllPluginsListItem(
                                            accentColor = accentColor,
                                            onClick = {
                                                searchViewModel.setSelectedPlugin(null, keepPlatformCs3 = true)
                                                onBackClick()
                                            }
                                        )
                                    }
                                }

                                items(
                                    items = filteredApis,
                                    key = { "${java.io.File(it.sourcePlugin ?: "").nameWithoutExtension}_${it.name}" }
                                ) { api ->
                                    PluginListItem(
                                        api = api,
                                        accentColor = accentColor,
                                        onClick = {
                                            searchViewModel.setSelectedPlugin(api.name, keepPlatformCs3 = true)
                                            navState.navigateToDetail(com.kitsugi.animelist.DetailScreen.AddonExplore(api.name))
                                        }
                                    )
                                }
                            }

                            // Sağ Kenar Alfabetik Kaydıraç (# ve A'dan Z'ye)
                            AlphabetScrollerRail(
                                alphabet = alphabet,
                                letterIndexMap = letterIndexMap,
                                activeLetter = activeScrollerLetter,
                                accentColor = accentColor,
                                onLetterSelect = { letter ->
                                    activeScrollerLetter = letter
                                    val targetIdx = letterIndexMap[letter]
                                        ?: if (letter == "#") 0
                                        else {
                                            val nextLetter = alphabet.dropWhile { it != letter }.firstOrNull { letterIndexMap.containsKey(it) }
                                            nextLetter?.let { letterIndexMap[it] }
                                        }
                                    if (targetIdx != null) {
                                        scope.launch {
                                            listState.scrollToItem(targetIdx)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    activeScrollerLetter = null
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(vertical = 12.dp)
                            )

                            // Sürükleme Sırasında Ortada Çıkan Büyük Harf Göstergesi
                            if (activeScrollerLetter != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(KitsugiColors.Surface.copy(alpha = 0.95f))
                                        .border(2.dp, accentColor, RoundedCornerShape(22.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = activeScrollerLetter ?: "",
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Black,
                                        color = accentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Detay Diyaloğu: ana arama sayfasındaki cs3 sonuçlarıyla aynı dialog ──
    activeDetailItem?.let { (api, response) ->
        KitsugiAddonDetailDialog(
            api = api,
            url = response.url,
            onDismissRequest = { activeDetailItem = null }
        )
    }
}

@Composable
private fun AlphabetScrollerRail(
    alphabet: List<String>,
    letterIndexMap: Map<String, Int>,
    activeLetter: String?,
    accentColor: Color,
    onLetterSelect: (String) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .pointerInput(alphabet, letterIndexMap) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val letterHeight = size.height / alphabet.size.toFloat()
                        val index = (offset.y / letterHeight).toInt().coerceIn(0, alphabet.size - 1)
                        onLetterSelect(alphabet[index])
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val letterHeight = size.height / alphabet.size.toFloat()
                        val index = (change.position.y / letterHeight).toInt().coerceIn(0, alphabet.size - 1)
                        onLetterSelect(alphabet[index])
                    }
                )
            }
            .pointerInput(alphabet, letterIndexMap) {
                detectTapGestures { offset ->
                    val letterHeight = size.height / alphabet.size.toFloat()
                    val index = (offset.y / letterHeight).toInt().coerceIn(0, alphabet.size - 1)
                    onLetterSelect(alphabet[index])
                    onDragEnd()
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { letter ->
                val hasItems = letterIndexMap.containsKey(letter)
                val isSelected = activeLetter == letter

                Text(
                    text = letter,
                    fontSize = if (isSelected) 12.sp else 9.sp,
                    fontWeight = if (isSelected) FontWeight.Black else if (hasItems) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> accentColor
                        hasItems -> KitsugiColors.TextPrimary.copy(alpha = 0.9f)
                        else -> KitsugiColors.TextMuted.copy(alpha = 0.25f)
                    }
                )
            }
        }
    }
}

@Composable
private fun PluginListItem(
    api: MainAPI,
    accentColor: Color,
    onClick: () -> Unit
) {
    val langFlag = try {
        SubtitleHelper.getFlagFromIso(api.lang) ?: "🌐"
    } catch (_: Exception) { "🌐" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = langFlag, fontSize = 18.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = api.name,
                color = KitsugiColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = api.mainUrl,
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val typeLabel = api.supportedTypes.take(2).joinToString(", ") { tvTypeLabel(it) }
        if (typeLabel.isNotEmpty()) {
            Text(
                text = typeLabel,
                color = if (api.supportedTypes.contains(TvType.NSFW)) Color(0xFFFF5722) else accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AllPluginsListItem(
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🌐", fontSize = 18.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tum_eklentiler),
                color = KitsugiColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.aktif_olan_tum_eklentilerde_ara),
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = stringResource(R.string.hepsi),
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PluginTypeChip(
    label: String,
    emoji: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val isNsfw = label == "+18"
    val activeColor = if (isNsfw) Color(0xFFFF5722) else accentColor
    val bgColor by animateColorAsState(
        targetValue = if (selected) activeColor else KitsugiColors.Background,
        animationSpec = tween(180), label = "chipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) activeColor else KitsugiColors.Border,
        animationSpec = tween(180), label = "chipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else KitsugiColors.TextSecondary,
        animationSpec = tween(180), label = "chipText"
    )
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$emoji $label",
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun tvTypeLabel(type: TvType): String = when (type) {
    TvType.Movie, TvType.AnimeMovie -> "Film"
    TvType.TvSeries -> "Dizi"
    TvType.Anime -> "Anime"
    TvType.AsianDrama -> "Asya"
    TvType.Cartoon -> "Çizgi film"
    TvType.Documentary -> "Belgesel"
    TvType.Live -> "Canlı"
    TvType.OVA -> "OVA"
    TvType.NSFW -> "NSFW"
    else -> type.name
}
