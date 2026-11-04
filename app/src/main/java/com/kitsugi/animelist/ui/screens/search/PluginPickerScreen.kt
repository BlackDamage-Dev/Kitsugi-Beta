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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.app.MangaViewModel
import com.kitsugi.animelist.ui.theme.LocalBlurAdultMedia
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.MangaDetailNavState
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiSettingsSegmentedTabs
import com.kitsugi.animelist.ui.components.KitsugiSettingsTabOption
import com.kitsugi.animelist.ui.screens.search.components.KitsugiAddonDetailDialog
import com.kitsugi.animelist.ui.screens.search.components.ResultItemCard
import com.kitsugi.animelist.ui.screens.search.components.isAddonAdultResponse
import com.kitsugi.animelist.ui.theme.LocalShowAdultContent
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.SubtitleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

/** Eklenti Portalı modu: Video (Cloudstream eklentileri) / Manga (manga kaynakları). */
private enum class PluginPortalMode { VIDEO, MANGA }

// Kalıcı önbellek — sayfa geçişlerinde 0 elemana düşmeyi ve scroll sıfırlanmasını önler
private var cachedActiveApis: List<MainAPI>? = null
private var savedPickerScrollIndex: Int = 0
private var savedPickerScrollOffset: Int = 0

@Composable
fun PluginPickerScreen(
    onBackClick: () -> Unit,
    searchViewModel: SearchViewModel,
    navState: AppNavigationState,
    mangaViewModel: MangaViewModel? = null,
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

    var activeApis by remember { mutableStateOf<List<MainAPI>>(cachedActiveApis ?: emptyList()) }
    var isLoadingActiveApis by remember { mutableStateOf(cachedActiveApis == null) }
    var selectedGroupLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var queryText by rememberSaveable { mutableStateOf("") }

    // ── Portal arama durumu ─────────────────────────────────────────────────
    // Arama PORTALIN İÇİNDE çalışır: arama butonuna / klavyedeki "Ara" tuşuna
    // basmak kullanıcıyı Kaynaklar arama sayfasına (SearchScreen) ATMAZ.
    // Video modunda aktif Cloudstream eklentilerinde, Manga modunda manga
    // kaynaklarında paralel içerik araması yapılır ve sonuçlar burada,
    // kaynağına göre gruplu gösterilir. (Eski davranış: portal araması yalnızca
    // eklenti ADI süzüyordu; "Tüm Eklentiler"e basınca setSelectedPlugin +
    // geri dönüş kullanıcıyı kaynak arama sayfasına fırlatıyordu.)
    var portalMode by rememberSaveable { mutableStateOf(PluginPortalMode.VIDEO) }

    // Video içerik araması
    var videoSearchResults by remember { mutableStateOf<List<Pair<MainAPI, com.lagradost.cloudstream3.SearchResponse>>>(emptyList()) }
    var isSearchingVideo by remember { mutableStateOf(false) }
    var hasSearchedVideo by remember { mutableStateOf(false) }
    var submittedVideoQuery by remember { mutableStateOf("") }
    var activeVideoDetail by remember { mutableStateOf<Pair<MainAPI, com.lagradost.cloudstream3.SearchResponse>?>(null) }

    // Manga kaynakları + içerik araması
    val mangaRepository = remember(context.applicationContext) { MangaSourceRepository(context.applicationContext) }
    var mangaSources by remember { mutableStateOf<List<MangaSource>>(emptyList()) }
    var isLoadingMangaSources by remember { mutableStateOf(true) }
    var selectedMangaSourceKey by rememberSaveable { mutableStateOf<String?>(null) }
    var mangaSearchResults by remember { mutableStateOf<List<Pair<MangaSource, MangaDetails>>>(emptyList()) }
    var isSearchingManga by remember { mutableStateOf(false) }
    var hasSearchedManga by remember { mutableStateOf(false) }
    var submittedMangaQuery by remember { mutableStateOf("") }

    val showAdultContent = LocalShowAdultContent.current

    // Durum tabanlı metin alanı: uzun metinlerde yatay kaydırma (pan) desteği.
    // (queryText rememberSaveable ile korunur ve LaunchedEffect ile senkronize edilir.)
    val pluginQueryState = remember { TextFieldState() }
    LaunchedEffect(queryText) {
        if (queryText != pluginQueryState.text.toString()) {
            pluginQueryState.setTextAndPlaceCursorAtEnd(queryText)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { pluginQueryState.text.toString() }.collect { t ->
            if (t != queryText) queryText = t
        }
    }

    // Sorgu, gönderilmiş aramadan farklılaşırsa eski sonuçlar geçersizdir:
    // portal göz atma (ad süzme) moduna döner; yeni arama yalnızca açık
    // onayla (arama butonu / klavye "Ara") tekrar çalışır.
    LaunchedEffect(queryText) {
        if (hasSearchedVideo && queryText.trim() != submittedVideoQuery) {
            hasSearchedVideo = false
            videoSearchResults = emptyList()
            isSearchingVideo = false
        }
        if (hasSearchedManga && queryText.trim() != submittedMangaQuery) {
            hasSearchedManga = false
            mangaSearchResults = emptyList()
            isSearchingManga = false
        }
        if (queryText.isBlank()) {
            hasSearchedVideo = false
            hasSearchedManga = false
            videoSearchResults = emptyList()
            mangaSearchResults = emptyList()
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedPickerScrollIndex,
        initialFirstVisibleItemScrollOffset = savedPickerScrollOffset
    )

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
            hasSearchedVideo || hasSearchedManga -> {
                hasSearchedVideo = false
                hasSearchedManga = false
                videoSearchResults = emptyList()
                mangaSearchResults = emptyList()
            }
            queryText.isNotEmpty() -> queryText = ""
            selectedMangaSourceKey != null -> selectedMangaSourceKey = null
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

    // Manga kaynaklarını yükle. Uzantılar arka planda bellek içi listeye
    // dolduğu için liste ilk anda boş gelebilir; kısa aralıklarla birkaç kez
    // yeniden dene (MangaViewModel ile aynı kaynak: getAvailableSources).
    LaunchedEffect(portalMode) {
        if (portalMode != PluginPortalMode.MANGA && mangaSources.isNotEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                var loadedSources = mangaRepository.getAvailableSources()
                var attempts = 0
                while (loadedSources.isEmpty() && attempts < 5) {
                    delay(600)
                    loadedSources = mangaRepository.getAvailableSources()
                    attempts++
                }
                val sorted = loadedSources.sortedBy { it.name.lowercase() }
                withContext(Dispatchers.Main) {
                    mangaSources = sorted
                    isLoadingMangaSources = false
                }
            } catch (e: Exception) {
                Log.e("PluginPickerScreen", "Manga kaynakları yüklenemedi: ${e.message}")
                withContext(Dispatchers.Main) { isLoadingMangaSources = false }
            }
        }
    }

    // Yalnızca kategoriye göre süzülmüş eklentiler — İÇERİK aramasının kapsamı
    // budur. Sorgu metni bu listeye UYGULANMAZ: "Naruto" yazıp aratınca
    // adı "Naruto" içermeyen eklentiler kapsam dışı kalmamalı.
    val categoryFilteredApis = remember(activeApis, selectedGroupLabel) {
        var list = activeApis
        if (selectedGroupLabel != null) {
            val group = pluginTypeGroups.firstOrNull { it.label == selectedGroupLabel }
            if (group != null) {
                list = list.filter { api ->
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
        }
        list
    }

    // Göz atma listesi: kategori + eklenti adı/adresi/dili süzgeci (0 ms gecikme)
    val filteredApis = remember(categoryFilteredApis, queryText) {
        var list = categoryFilteredApis
        if (queryText.isNotBlank()) {
            val q = queryText.trim().lowercase()
            list = list.filter { api ->
                api.name.lowercase().contains(q) ||
                api.mainUrl.lowercase().contains(q) ||
                api.lang.lowercase().contains(q)
            }
        }
        list
    }

    // Manga göz atma listesi: kaynak adı/adresi/dili süzgeci
    val filteredMangaSources = remember(mangaSources, queryText) {
        if (queryText.isBlank()) mangaSources
        else {
            val q = queryText.trim().lowercase()
            mangaSources.filter { source ->
                source.name.lowercase().contains(q) ||
                source.baseUrl.lowercase().contains(q) ||
                source.lang.lowercase().contains(q)
            }
        }
    }

    val selectedMangaSource = remember(mangaSources, selectedMangaSourceKey) {
        mangaSources.firstOrNull { it.stableSourceKey() == selectedMangaSourceKey }
    }

    // +18 sözleşmesi: ayar kapalıyken yetişkin sonuçlar portala da sızmaz.
    val visibleVideoResults = remember(videoSearchResults, showAdultContent) {
        if (showAdultContent) videoSearchResults
        else videoSearchResults.filter { !isAddonAdultResponse(it.second) }
    }
    val visibleMangaResults = remember(mangaSearchResults, showAdultContent) {
        if (showAdultContent) mangaSearchResults
        else mangaSearchResults.filter { !it.second.isAdultContent }
    }

    /**
     * Portal içi içerik araması — kullanıcıyı ASLA Kaynaklar arama sayfasına
     * göndermez, [searchViewModel]'e dokunmaz.
     *
     * - Video: seçili kategorideki aktif eklentilerde paralel arama
     *   ([CsStreamRunner.searchWithCfRetry]).
     * - Manga: kaynak seçiliyse yalnızca o kaynakta, yoksa tüm aday
     *   kaynaklarda paralel arama ([MangaSourceRepository.searchAllSources]).
     */
    fun performPortalSearch() {
        val q = queryText.trim()
        if (q.isBlank()) return
        keyboardController?.hide()
        when (portalMode) {
            PluginPortalMode.VIDEO -> {
                // Yeni aramada oturum bloklarını temizle (AddonSearchDialog ile aynı).
                CsPluginStatusTracker.clear()
                CsStreamRunner.clearUnsupportedMethodsCache()
                val apisToSearch = categoryFilteredApis
                submittedVideoQuery = q
                hasSearchedVideo = true
                isSearchingVideo = true
                videoSearchResults = emptyList()
                scope.launch {
                    val results = withContext(Dispatchers.IO) {
                        val list = mutableListOf<Pair<MainAPI, com.lagradost.cloudstream3.SearchResponse>>()
                        supervisorScope {
                            val jobs = apisToSearch.map { api ->
                                async {
                                    try {
                                        val searchRes = CsStreamRunner.searchWithCfRetry(api, q)
                                        synchronized(list) {
                                            searchRes.forEach { list.add(api to it) }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PluginPickerScreen", "Eklenti araması başarısız: ${api.name} — ${e.message}")
                                    }
                                }
                            }
                            jobs.forEach {
                                try { it.await() } catch (_: Exception) {}
                            }
                        }
                        list.toList()
                    }
                    // Kullanıcı bu arada sorguyu değiştirdiyse bayat sonucu basma.
                    if (queryText.trim() == q) {
                        videoSearchResults = results
                    }
                    isSearchingVideo = false
                }
            }
            PluginPortalMode.MANGA -> {
                val scopedSource = selectedMangaSource
                submittedMangaQuery = q
                hasSearchedManga = true
                isSearchingManga = true
                mangaSearchResults = emptyList()
                scope.launch {
                    val results = withContext(Dispatchers.IO) {
                        try {
                            if (scopedSource != null) {
                                val raw = scopedSource.fetchSearchManga(1, q)
                                mangaRepository.postProcessSearchResults(scopedSource, q, raw.mangas, relaxScoring = true)
                                    .map { manga -> scopedSource to manga }
                            } else {
                                mangaRepository.searchAllSources(q)
                            }
                        } catch (e: Exception) {
                            Log.e("PluginPickerScreen", "Manga araması başarısız: ${e.message}")
                            emptyList()
                        }
                    }
                    if (queryText.trim() == q) {
                        mangaSearchResults = results
                    }
                    isSearchingManga = false
                }
            }
        }
    }

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
                    text = if (portalMode == PluginPortalMode.VIDEO) {
                        "${filteredApis.size} eklenti"
                    } else {
                        "${filteredMangaSources.size} kaynak"
                    },
                    color = KitsugiColors.TextMuted,
                    fontSize = 13.sp
                )
            }

            // ── Video / Manga modu ──────────────────────────────────────────
            // Portal iki eklenti ailesini de kapsar: Video = Cloudstream
            // eklentileri, Manga = manga kaynakları. Arama her modda kendi
            // ailesinin içinde çalışır.
            KitsugiSettingsSegmentedTabs(
                tabs = listOf(
                    KitsugiSettingsTabOption(stringResource(R.string.bangumi_tag_video_tag), Icons.PlayCircle),
                    KitsugiSettingsTabOption(stringResource(R.string.media_type_manga), Icons.MenuBook)
                ),
                selectedIndex = if (portalMode == PluginPortalMode.VIDEO) 0 else 1,
                accentColor = accentColor,
                onSelect = { index ->
                    val nextMode = if (index == 0) PluginPortalMode.VIDEO else PluginPortalMode.MANGA
                    if (nextMode != portalMode) {
                        portalMode = nextMode
                        // Mod değişince öteki ailenin arama sonuçları ekranda kalmaz.
                        hasSearchedVideo = false
                        hasSearchedManga = false
                        videoSearchResults = emptyList()
                        mangaSearchResults = emptyList()
                        isSearchingVideo = false
                        isSearchingManga = false
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

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
                    // Sadece Eklenti Arama Çubuğu
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(KitsugiColors.Surface)
                            .border(1.dp, KitsugiColors.Border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Search,
                                contentDescription = null,
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )

                            BasicTextField(
                                state = pluginQueryState,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(accentColor),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                onKeyboardAction = {
                                    // Klavyedeki "Ara": portal İÇİNDE içerik araması
                                    // yapar; Kaynaklar arama sayfasına gitmez.
                                    performPortalSearch()
                                },
                                modifier = Modifier.weight(1f),
                                decorator = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (pluginQueryState.text.isEmpty()) {
                                            Text(
                                                text = if (portalMode == PluginPortalMode.VIDEO) {
                                                    KitsugiStrings.get(R.string.eklentilerde_ara_x_eklenti, activeApis.size)
                                                } else {
                                                    KitsugiStrings.get(R.string.manga_kaynaklarinda_ara_x_kaynak, mangaSources.size)
                                                },
                                                color = KitsugiColors.TextMuted,
                                                fontSize = 14.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )

                            if (queryText.isNotEmpty()) {
                                // Arama butonu: portal içinde, seçili ailenin
                                // (Video/Manga) eklentilerinde içerik arar.
                                IconButton(
                                    onClick = { performPortalSearch() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Search,
                                        contentDescription = stringResource(R.string.ara),
                                        tint = accentColor,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { queryText = "" },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Close,
                                        contentDescription = stringResource(R.string.temizle),
                                        tint = KitsugiColors.TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

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

                    // Kategori çipleri yalnızca Video moduna aittir (TvType grupları);
                    // Manga modunda kaynak kapsamı, listedeki kaynağa dokunarak seçilir.
                    if (portalMode == PluginPortalMode.VIDEO && availableGroups.isNotEmpty()) {
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

            // ── Ana Liste ve Sağ Alfabetik Kaydıraç ───────────────────────────────────
            when {
                // ── Portal içi arama çalışıyor ──────────────────────────────
                (portalMode == PluginPortalMode.VIDEO && isSearchingVideo) ||
                    (portalMode == PluginPortalMode.MANGA && isSearchingManga) -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            KitsugiPlasmaLoader(size = 46.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.araniyor),
                                color = KitsugiColors.TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // ── Video: eklenti içerik arama sonuçları (portal içinde) ──
                portalMode == PluginPortalMode.VIDEO && hasSearchedVideo -> {
                    if (visibleVideoResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.sonuc_bulunamadi),
                                color = KitsugiColors.TextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    } else {
                        val groupedVideo = remember(visibleVideoResults) {
                            visibleVideoResults.groupBy { it.first }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            groupedVideo.forEach { (api, apiResults) ->
                                item(key = "vgrp_${api.name}_${api.sourcePlugin?.hashCode() ?: 0}") {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = api.name,
                                                color = KitsugiColors.TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(
                                                text = KitsugiStrings.get(R.string.inline_1_s_results_b4b4d39, apiResults.size),
                                                color = KitsugiColors.TextMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            itemsIndexed(apiResults, key = { index, item -> "vres_${api.name}_${index}_${item.second.url.hashCode()}" }) { _, (_, response) ->
                                                ResultItemCard(
                                                    title = response.name,
                                                    imageUrl = response.posterUrl,
                                                    apiName = api.name,
                                                    quality = response.quality?.name,
                                                    onClick = { activeVideoDetail = api to response },
                                                    isAdult = isAddonAdultResponse(response)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Manga: kaynak içerik arama sonuçları (portal içinde) ──
                portalMode == PluginPortalMode.MANGA && hasSearchedManga -> {
                    if (visibleMangaResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.sonuc_bulunamadi),
                                color = KitsugiColors.TextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    } else {
                        val groupedManga = remember(visibleMangaResults) {
                            visibleMangaResults.groupBy { it.first }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            groupedManga.forEach { (source, sourceResults) ->
                                item(key = "mgrp_${source.stableSourceKey()}_${System.identityHashCode(source)}") {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = source.name,
                                                color = KitsugiColors.TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(
                                                text = KitsugiStrings.get(R.string.inline_1_s_results_b4b4d39, sourceResults.size),
                                                color = KitsugiColors.TextMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            itemsIndexed(sourceResults, key = { index, item -> "mres_${source.stableSourceKey()}_${index}_${item.second.url.hashCode()}" }) { _, (_, manga) ->
                                                MangaPortalResultCard(
                                                    manga = manga,
                                                    sourceName = source.name,
                                                    accentColor = accentColor,
                                                    onClick = {
                                                        navState.mangaDetailNavState = MangaDetailNavState(
                                                            source = source,
                                                            mangaDetails = manga
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Manga: kaynaklar yükleniyor ─────────────────────────────
                portalMode == PluginPortalMode.MANGA && isLoadingMangaSources && mangaSources.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiPlasmaLoader(size = 46.dp)
                    }
                }

                // ── Manga: kaynak bulunamadı / adı eşleşen kaynak yok ──────
                portalMode == PluginPortalMode.MANGA && filteredMangaSources.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (queryText.isNotEmpty())
                                KitsugiStrings.get(R.string.ui2_plugin_query_no_match, queryText)
                            else
                                stringResource(R.string.inline_no_manga_extensions_have_been_e92d6be),
                            color = KitsugiColors.TextMuted,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }

                // ── Manga: kaynak listesi (göz atma + arama kapsamı seçimi) ─
                portalMode == PluginPortalMode.MANGA -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item(key = "all_manga_sources_header") {
                            AllMangaSourcesListItem(
                                accentColor = accentColor,
                                selected = selectedMangaSourceKey == null,
                                onClick = { selectedMangaSourceKey = null }
                            )
                        }
                        items(
                            items = filteredMangaSources,
                            key = { it.stableSourceKey() }
                        ) { source ->
                            MangaSourceListItem(
                                source = source,
                                accentColor = accentColor,
                                selected = selectedMangaSourceKey == source.stableSourceKey(),
                                onClick = {
                                    // Kapsam seçimi: sonraki arama (arama butonu /
                                    // klavye "Ara") yalnızca bu kaynakta çalışır;
                                    // tekrar dokunmak kapsamı tüm kaynaklara açar.
                                    selectedMangaSourceKey =
                                        if (selectedMangaSourceKey == source.stableSourceKey()) null else source.stableSourceKey()
                                }
                            )
                        }
                    }
                }

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
                            if (selectedGroupLabel == null) {
                                item(key = "all_plugins_header") {
                                    AllPluginsListItem(
                                        accentColor = accentColor,
                                        onClick = {
                                            // "Tüm Eklentiler" artık kullanıcıyı
                                            // Kaynaklar arama sayfasına ATMAR:
                                            // sorgu varsa tüm aktif eklentilerde
                                            // portal içinde arar, yoksa tüm
                                            // eklenti listesini gösterir.
                                            if (queryText.isNotBlank()) performPortalSearch()
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
                                        // Eklentinin kendi keşfet sayfası portal
                                        // akışında açılır; global arama durumu
                                        // (SearchViewModel) artık hiç tetiklenmez,
                                        // böylece kullanıcı kaynak arama sayfasına
                                        // fırlatılmaz.
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

    // Video arama sonucuna dokununca eklenti detay önizlemesi portalda açılır.
    activeVideoDetail?.let { (api, response) ->
        KitsugiAddonDetailDialog(
            api = api,
            url = response.url,
            onDismissRequest = { activeVideoDetail = null }
        )
    }
}

@Composable
private fun MangaSourceListItem(
    source: MangaSource,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) accentColor.copy(alpha = 0.45f) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
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
                imageVector = Icons.MenuBook,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                color = KitsugiColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.baseUrl,
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = source.lang.uppercase(),
            color = if (selected) accentColor else KitsugiColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AllMangaSourcesListItem(
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) accentColor.copy(alpha = 0.45f) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
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
                text = stringResource(R.string.tum_manga_kaynaklari),
                color = KitsugiColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.aktif_tum_manga_kaynaklarinda_ara),
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
private fun MangaPortalResultCard(
    manga: MangaDetails,
    sourceName: String,
    accentColor: Color,
    onClick: () -> Unit
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
                com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                    model = manga.thumbnailUrl,
                    contentDescription = manga.title,
                    isAdult = manga.isAdultContent,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = manga.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = sourceName,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
