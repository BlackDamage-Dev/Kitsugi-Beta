package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.icons.KitsugiEmojiIcons
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.screens.search.components.KitsugiAddonDetailDialog
import com.kitsugi.animelist.ui.screens.search.components.isAddonAdultResponse
import com.kitsugi.animelist.ui.screens.search.components.toAddonSearchResult
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalShowAdultContent
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.utils.isTurkish
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.SubtitleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private data class PluginTypeGroup(
    val label: String,
    val emoji: String,
    val types: List<TvType>
)

private data class PluginContentSearchGroup(
    val api: MainAPI,
    val items: List<JikanSearchResult>,
    val rows: List<List<JikanSearchResult>>
)

private data class ActiveAddonDetailTarget(
    val api: MainAPI,
    val url: String
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

private const val PLUGIN_SEARCH_DEBOUNCE_MS = 420L
private const val PER_PLUGIN_SEARCH_TIMEOUT_MS = 18_000L
private const val MAX_CONCURRENT_PLUGIN_SEARCHES = 6

@Composable
fun PluginPickerScreen(
    onBackClick: () -> Unit,
    searchViewModel: SearchViewModel,
    navState: AppNavigationState,
    onOpenApiDetail: (JikanSearchResult) -> Unit = {}
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val showAdultContent = LocalShowAdultContent.current
    val isTr = isTurkish()

    val settingsDataStore = remember(context) { SettingsDataStore(context.applicationContext) }
    val appSettings by settingsDataStore.settingsFlow.collectAsState(
        initial = com.kitsugi.animelist.data.settings.AppSettings()
    )

    var activeApis by remember { mutableStateOf<List<MainAPI>>(cachedActiveApis ?: emptyList()) }
    var isLoadingActiveApis by remember { mutableStateOf(cachedActiveApis == null) }
    var selectedGroupLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var queryText by rememberSaveable { mutableStateOf("") }

    // Eklenti içi gerçek içerik arama durumları
    var isSearchingContent by remember { mutableStateOf(false) }
    var searchedPluginCount by remember { mutableIntStateOf(0) }
    var totalSearchPluginCount by remember { mutableIntStateOf(0) }
    var lastExecutedQuery by remember { mutableStateOf("") }
    var contentSearchGroups by remember { mutableStateOf<List<PluginContentSearchGroup>>(emptyList()) }
    var selectedResultPluginFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDetailTarget by remember { mutableStateOf<ActiveAddonDetailTarget?>(null) }
    var contentSearchJob by remember { mutableStateOf<Job?>(null) }

    val pluginQueryState = remember {
        TextFieldState(initialText = queryText)
    }
    val hasInputText by remember {
        derivedStateOf { pluginQueryState.text.isNotEmpty() }
    }

    LaunchedEffect(queryText) {
        if (queryText != pluginQueryState.text.toString()) {
            pluginQueryState.setTextAndPlaceCursorAtEnd(queryText)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { pluginQueryState.text.toString() }.collect { current ->
            if (current != queryText) {
                queryText = current
            }
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedPickerScrollIndex,
        initialFirstVisibleItemScrollOffset = savedPickerScrollOffset
    )

    DisposableEffect(Unit) {
        onDispose {
            contentSearchJob?.cancel()
            savedPickerScrollIndex = listState.firstVisibleItemIndex
            savedPickerScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }

    fun clearSearch() {
        contentSearchJob?.cancel()
        isSearchingContent = false
        searchedPluginCount = 0
        totalSearchPluginCount = 0
        lastExecutedQuery = ""
        contentSearchGroups = emptyList()
        selectedResultPluginFilter = null
        pluginQueryState.clearText()
        queryText = ""
    }

    BackHandler {
        when {
            activeDetailTarget != null -> activeDetailTarget = null
            selectedResultPluginFilter != null -> selectedResultPluginFilter = null
            queryText.isNotEmpty() -> clearSearch()
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

    // Seçili kategoriye uyan eklentiler (hem liste hem içerik araması için baz küme)
    val categoryFilteredApis = remember(activeApis, selectedGroupLabel, showAdultContent) {
        var list = activeApis
        if (selectedGroupLabel != null) {
            val group = pluginTypeGroups.firstOrNull { it.label == selectedGroupLabel }
            if (group != null) {
                list = list.filter { api ->
                    if (group.label == "+18") {
                        isNsfwPlugin(api)
                    } else {
                        api.supportedTypes.any { group.types.contains(it) }
                    }
                }
            }
        } else if (!showAdultContent) {
            list = list.filterNot { isPureNsfwPlugin(it) }
        }
        list
    }

    // İsim/URL eşleşen eklentiler (hızlı eklenti geçişi korunur)
    val matchingNameApis = remember(categoryFilteredApis, queryText) {
        val q = queryText.trim().lowercase()
        if (q.isEmpty()) {
            categoryFilteredApis
        } else {
            categoryFilteredApis.filter { api ->
                api.name.lowercase().contains(q) ||
                    api.mainUrl.lowercase().contains(q) ||
                    api.lang.lowercase().contains(q)
            }
        }
    }

    fun executeCrossPluginContentSearch(rawQuery: String, force: Boolean = false) {
        val trimmed = rawQuery.trim()
        if (trimmed.length < 2) {
            contentSearchJob?.cancel()
            isSearchingContent = false
            searchedPluginCount = 0
            totalSearchPluginCount = 0
            lastExecutedQuery = ""
            contentSearchGroups = emptyList()
            selectedResultPluginFilter = null
            return
        }

        val targetApis = categoryFilteredApis
        if (targetApis.isEmpty()) {
            contentSearchJob?.cancel()
            isSearchingContent = false
            searchedPluginCount = 0
            totalSearchPluginCount = 0
            lastExecutedQuery = trimmed
            contentSearchGroups = emptyList()
            return
        }

        val searchSignature = "$trimmed|${selectedGroupLabel.orEmpty()}|${targetApis.size}|$showAdultContent"
        if (!force && searchSignature == lastExecutedQuery && (isSearchingContent || contentSearchGroups.isNotEmpty())) {
            return
        }

        contentSearchJob?.cancel()
        contentSearchJob = scope.launch {
            isSearchingContent = true
            searchedPluginCount = 0
            totalSearchPluginCount = targetApis.size
            lastExecutedQuery = searchSignature
            contentSearchGroups = emptyList()
            selectedResultPluginFilter = null

            val resultsMutex = Mutex()
            val accumulated = LinkedHashMap<String, PluginContentSearchGroup>()
            var finished = 0
            val semaphore = Semaphore(MAX_CONCURRENT_PLUGIN_SEARCHES)

            try {
                withContext(Dispatchers.IO) {
                    CsPluginStatusTracker.clear()
                    CsStreamRunner.clearUnsupportedMethodsCache()

                    supervisorScope {
                        for (api in targetApis) {
                            launch {
                                val pluginItems: List<JikanSearchResult> = semaphore.withPermit {
                                    try {
                                        withTimeoutOrNull(PER_PLUGIN_SEARCH_TIMEOUT_MS) {
                                            CsStreamRunner.ensurePluginReady(api)
                                            val rawResponses = CsStreamRunner.searchWithCfRetry(api, trimmed)
                                            rawResponses
                                                .asSequence()
                                                .filter { resp -> showAdultContent || !isAddonAdultResponse(resp) }
                                                .distinctBy { it.url }
                                                .map { resp ->
                                                    val mapped = resp.toAddonSearchResult()
                                                    if (mapped.source.isBlank()) {
                                                        mapped.copy(source = api.name, cs3ApiName = api.name)
                                                    } else {
                                                        mapped
                                                    }
                                                }
                                                .toList()
                                        } ?: emptyList()
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        Log.w("PluginPickerScreen", "[${api.name}] content search error: ${e.message}")
                                        emptyList()
                                    }
                                }

                                val snapshot: Pair<List<PluginContentSearchGroup>, Int> = resultsMutex.withLock {
                                    finished += 1
                                    if (pluginItems.isNotEmpty()) {
                                        accumulated[api.name] = PluginContentSearchGroup(
                                            api = api,
                                            items = pluginItems,
                                            rows = pluginItems.chunked(3)
                                        )
                                    }
                                    accumulated.values.toList() to finished
                                }

                                withContext(Dispatchers.Main) {
                                    contentSearchGroups = snapshot.first
                                    searchedPluginCount = snapshot.second
                                }
                            }
                        }
                    }
                }
            } finally {
                isSearchingContent = false
            }
        }
    }

    // Yazarken otomatik gerçek eklenti araması (debounced); silindiğinde anında sıfırlama
    LaunchedEffect(queryText, categoryFilteredApis, showAdultContent) {
        val trimmed = queryText.trim()
        if (trimmed.isEmpty()) {
            contentSearchJob?.cancel()
            isSearchingContent = false
            searchedPluginCount = 0
            totalSearchPluginCount = 0
            lastExecutedQuery = ""
            contentSearchGroups = emptyList()
            selectedResultPluginFilter = null
            return@LaunchedEffect
        }
        if (trimmed.length < 2) {
            contentSearchJob?.cancel()
            isSearchingContent = false
            contentSearchGroups = emptyList()
            return@LaunchedEffect
        }
        delay(PLUGIN_SEARCH_DEBOUNCE_MS)
        executeCrossPluginContentSearch(trimmed, force = false)
    }

    val totalFoundItemsCount = remember(contentSearchGroups) {
        contentSearchGroups.sumOf { it.items.size }
    }

    val visibleContentGroups = remember(contentSearchGroups, selectedResultPluginFilter) {
        val filterName = selectedResultPluginFilter
        if (filterName == null) {
            contentSearchGroups
        } else {
            contentSearchGroups.filter { it.api.name == filterName }
        }
    }

    // Alfabetik hızlı indeks haritası (# ve A-Z) — yalnızca arama kutusu boşken aktif
    val alphabet = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val letterIndexMap = remember(categoryFilteredApis, selectedGroupLabel) {
        val map = mutableMapOf<String, Int>()
        val headerOffset = if (selectedGroupLabel == null) 1 else 0
        categoryFilteredApis.forEachIndexed { index, api ->
            val firstChar = api.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (firstChar in 'A'..'Z') firstChar.toString() else "#"
            if (!map.containsKey(key)) {
                map[key] = index + headerOffset
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
                if (delta < -14f && isHeaderVisible && listState.firstVisibleItemIndex > 0) {
                    isHeaderVisible = false
                } else if (delta > 14f && !isHeaderVisible) {
                    isHeaderVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (index == 0 && offset == 0) {
                isHeaderVisible = true
            }
        }
    }

    val isQueryMode = queryText.isNotBlank()

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
            // ── Üst Başlık Barı ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = {
                    if (isQueryMode) clearSearch() else onBackClick()
                }) {
                    Icon(
                        imageVector = Icons.ArrowBack,
                        contentDescription = stringResource(R.string.geri),
                        tint = KitsugiColors.TextPrimary
                    )
                }
                KitsugiArtIcon(art = KitsugiArt.Extensions, size = 24.dp)
                Text(
                    text = stringResource(R.string.eklenti_portali),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isQueryMode && totalFoundItemsCount > 0) {
                        if (isTr) "$totalFoundItemsCount sonuç" else "$totalFoundItemsCount results"
                    } else {
                        "${categoryFilteredApis.size} eklenti"
                    },
                    color = if (isQueryMode && totalFoundItemsCount > 0) accentColor else KitsugiColors.TextMuted,
                    fontWeight = if (isQueryMode && totalFoundItemsCount > 0) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
            }

            // ── Kaydırmaya Duyarlı Arama ve Kategori Barı ─────────────────────────────
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
                    // Eklenti arama çubuğu — ANA ARAMA SAYFASIYLA BİREBİR AYNI bileşen.
                    // (KitsugiCosmicSearchBar: 56 dp yükseklik, 16 dp köşe, aynı
                    //  yazı tipi/odak rengi, aynı temizle + ara butonları.)
                    com.kitsugi.animelist.ui.components.KitsugiCosmicSearchBar(
                        query = queryText,
                        onQueryChange = { queryText = it },
                        onSearch = {
                            keyboardController?.hide()
                            executeCrossPluginContentSearch(queryText, force = true)
                        },
                        onClearQuery = {
                            clearSearch()
                        },
                        placeholder = KitsugiStrings.get(
                            R.string.eklentilerde_ara_x_eklenti,
                            categoryFilteredApis.size
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Kategori Çipleri (Tümü, Filmler, Diziler, +18, Animasyon vb.)
                    val availableGroups = remember(activeApis, showAdultContent) {
                        pluginTypeGroups.filter { group ->
                            if (group.label == "+18") {
                                showAdultContent && activeApis.any { isNsfwPlugin(it) }
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

            // ── Ana Alan: (A) Arama Modu = Gerçek Eklenti İçi İçerik Sonuçları, (B) Normal Mod = Eklenti Dizini ─
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

                isQueryMode -> {
                    val trimmed = queryText.trim()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Canlı arama durum bandı
                        item(key = "plugin_search_status", contentType = "status") {
                            PluginContentSearchStatusBanner(
                                query = trimmed,
                                isSearching = isSearchingContent,
                                searchedCount = searchedPluginCount,
                                totalPluginCount = totalSearchPluginCount.coerceAtLeast(categoryFilteredApis.size),
                                matchedPluginCount = contentSearchGroups.size,
                                totalItemCount = totalFoundItemsCount,
                                accentColor = accentColor,
                                isTr = isTr
                            )
                        }

                        // Birden fazla eklentide sonuç varsa eklenti filtresi çipleri
                        if (contentSearchGroups.size > 1) {
                            item(key = "plugin_result_filter_row", contentType = "filter_row") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PluginTypeChip(
                                        label = "${stringResource(R.string.tumu)} ($totalFoundItemsCount)",
                                        emoji = "🌐",
                                        selected = selectedResultPluginFilter == null,
                                        accentColor = accentColor,
                                        onClick = { selectedResultPluginFilter = null }
                                    )
                                    contentSearchGroups.forEach { group ->
                                        val flag = try {
                                            SubtitleHelper.getFlagFromIso(group.api.lang) ?: "🔌"
                                        } catch (_: Exception) {
                                            "🔌"
                                        }
                                        PluginTypeChip(
                                            label = "${group.api.name} (${group.items.size})",
                                            emoji = flag,
                                            selected = selectedResultPluginFilter == group.api.name,
                                            accentColor = accentColor,
                                            onClick = {
                                                selectedResultPluginFilter =
                                                    if (selectedResultPluginFilter == group.api.name) null else group.api.name
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Eklenti adı da aramayla eşleşiyorsa (ör. "dizipal" yazıldıysa) hızlı eklenti kısayolları
                        if (matchingNameApis.isNotEmpty() && selectedResultPluginFilter == null) {
                            item(key = "matching_plugins_header", contentType = "section_header") {
                                Text(
                                    text = if (isTr) {
                                        "Eşleşen Eklentiler (${matchingNameApis.size})"
                                    } else {
                                        "Matching Addons (${matchingNameApis.size})"
                                    },
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            items(
                                items = matchingNameApis.take(6),
                                key = { "match_api_${java.io.File(it.sourcePlugin ?: "").nameWithoutExtension}_${it.name}" },
                                contentType = { "plugin_row" }
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

                        // Gerçek eklenti içerik sonuçları (eklenti bazlı gruplar + 3 sütun poster grid)
                        if (trimmed.length < 2) {
                            item(key = "min_chars_hint", contentType = "empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isTr) {
                                            "Eklentilerin içinde içerik aramak için en az 2 karakter yazın."
                                        } else {
                                            "Type at least 2 characters to search across addons."
                                        },
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else if (visibleContentGroups.isEmpty() && isSearchingContent) {
                            item(key = "searching_loader", contentType = "loader") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        KitsugiPlasmaLoader(size = 44.dp)
                                        Text(
                                            text = if (isTr) {
                                                "\"$trimmed\" tüm eklentilerde aranıyor..."
                                            } else {
                                                "Searching addons for \"$trimmed\"..."
                                            },
                                            color = KitsugiColors.TextMuted,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        } else if (visibleContentGroups.isEmpty() && !isSearchingContent && matchingNameApis.isEmpty()) {
                            item(key = "no_content_found", contentType = "empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.inline_no_results_found_for_1_3ed9f56, trimmed),
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            visibleContentGroups.forEach { group ->
                                item(
                                    key = "group_hdr_${group.api.name}",
                                    contentType = "plugin_group_header"
                                ) {
                                    PluginContentGroupHeader(
                                        api = group.api,
                                        count = group.items.size,
                                        accentColor = accentColor,
                                        onOpenPluginClick = {
                                            searchViewModel.setSelectedPlugin(group.api.name, keepPlatformCs3 = true)
                                            navState.navigateToDetail(
                                                com.kitsugi.animelist.DetailScreen.AddonExplore(group.api.name)
                                            )
                                        }
                                    )
                                }

                                items(
                                    items = group.rows,
                                    key = { row ->
                                        "row_${group.api.name}_${row.firstOrNull()?.cs3Url ?: row.hashCode()}"
                                    },
                                    contentType = { "addon_poster_row" }
                                ) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        row.forEach { item ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                KitsugiExploreMediaCard(
                                                    result = item,
                                                    onClick = {
                                                        val url = item.cs3Url
                                                        if (!url.isNullOrBlank()) {
                                                            activeDetailTarget = ActiveAddonDetailTarget(
                                                                api = group.api,
                                                                url = url
                                                            )
                                                        } else {
                                                            onOpenApiDetail(item)
                                                        }
                                                    },
                                                    titleLanguage = appSettings.titleLanguage,
                                                    scoreFormat = appSettings.scoreFormat,
                                                    hideScores = appSettings.hideScores,
                                                    blurAdultMedia = appSettings.blurAdultMedia,
                                                    forceVertical = true
                                                )
                                            }
                                        }
                                        repeat(3 - row.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                categoryFilteredApis.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedGroupLabel != null)
                                "Bu kategoride aktif eklenti yok."
                            else
                                "Aktif eklenti bulunamadı.\nAyarlar -> Eklentilerim bölümünden eklenti etkinleştirin.",
                            color = KitsugiColors.TextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
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
                            if (selectedGroupLabel == null) {
                                item(key = "all_plugins_header", contentType = "all_plugins_header") {
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
                                items = categoryFilteredApis,
                                key = { "${java.io.File(it.sourcePlugin ?: "").nameWithoutExtension}_${it.name}" },
                                contentType = { "plugin_row" }
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
                                        val nextLetter = alphabet.dropWhile { it != letter }
                                            .firstOrNull { letterIndexMap.containsKey(it) }
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

    // Eklenti içerik kartına tıklandığında bölüm/kaynak seçici detay diyaloğu açılır
    activeDetailTarget?.let { target ->
        KitsugiAddonDetailDialog(
            api = target.api,
            url = target.url,
            onDismissRequest = { activeDetailTarget = null }
        )
    }
}

private fun isNsfwPlugin(api: MainAPI): Boolean =
    api.supportedTypes.contains(TvType.NSFW) ||
        CsStreamRunner.ADULT_PLUGINS_SET.contains(api.name) ||
        api.name.contains("18", ignoreCase = true) ||
        api.name.contains("Adult", ignoreCase = true) ||
        api.name.contains("Porn", ignoreCase = true)

private fun isPureNsfwPlugin(api: MainAPI): Boolean =
    CsStreamRunner.ADULT_PLUGINS_SET.contains(api.name) ||
        (api.supportedTypes.isNotEmpty() && api.supportedTypes.all { it == TvType.NSFW })

@Composable
private fun PluginContentSearchStatusBanner(
    query: String,
    isSearching: Boolean,
    searchedCount: Int,
    totalPluginCount: Int,
    matchedPluginCount: Int,
    totalItemCount: Int,
    accentColor: Color,
    isTr: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.Surface)
            .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isSearching) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = accentColor
            )
        } else {
            Icon(
                imageVector = Icons.Extension,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        val statusText = when {
            query.length < 2 -> if (isTr) {
                "Aramaya başlamak için en az 2 karakter girin"
            } else {
                "Enter at least 2 characters to search"
            }
            isSearching -> if (isTr) {
                "Eklentiler taranıyor ($searchedCount/$totalPluginCount) • $totalItemCount sonuç"
            } else {
                "Scanning addons ($searchedCount/$totalPluginCount) • $totalItemCount results"
            }
            else -> if (isTr) {
                "$totalPluginCount eklenti tarandı • $matchedPluginCount eklentide $totalItemCount sonuç"
            } else {
                "Scanned $totalPluginCount addons • $totalItemCount results in $matchedPluginCount addons"
            }
        }

        Text(
            text = statusText,
            color = KitsugiColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PluginContentGroupHeader(
    api: MainAPI,
    count: Int,
    accentColor: Color,
    onOpenPluginClick: () -> Unit
) {
    val langFlag = try {
        SubtitleHelper.getFlagFromIso(api.lang) ?: "🌐"
    } catch (_: Exception) {
        "🌐"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.Surface.copy(alpha = 0.72f))
            .clickable(onClick = onOpenPluginClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = langFlag, fontSize = 16.sp)
        Text(
            text = api.name,
            color = KitsugiColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }
        Icon(
            imageVector = Icons.ChevronRight,
            contentDescription = null,
            tint = KitsugiColors.TextMuted,
            modifier = Modifier.size(18.dp)
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
        SubtitleHelper.getFlagFromIso(api.lang)
    } catch (_: Exception) { null }

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
            if (langFlag != null) {
                Text(text = langFlag, fontSize = 18.sp)
            } else {
                Icon(
                    imageVector = Icons.Globe,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
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
            Icon(
                    imageVector = Icons.Globe,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
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
        KitsugiEmojiLabel(
            label = label,
            icon = KitsugiEmojiIcons.forEmoji(emoji),
            iconSize = 13.dp,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
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
