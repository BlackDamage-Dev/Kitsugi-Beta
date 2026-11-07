package com.kitsugi.animelist.ui.screens.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.MangaSourceResult
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalShowAdultContent
import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Eklenti portalı → Manga eklentisi → Keşfet
//
// Video eklentilerinin "Eklenti Keşfet" sayfasıyla aynı sözleşme:
//   • kaynağa ait raflar (Popüler / Son Güncellemeler)
//   • raf başlığında "Tümünü Gör" → sayfa içi tam ekran grid (sayfalama)
//   • kaynak içinde arama
//   • bir mangaya dokununca kaynağın **detay + bölüm listesi** sayfası açılır
//
// Uydurma veri yok: "Son Güncellemeler" rafı yalnızca kaynak gerçekten
// destekliyorsa (MangaSource.supportsLatest) çizilir.
// ─────────────────────────────────────────────────────────────────────────────

/** Keşfet sayfasındaki raf kimlikleri. */
enum class MangaExploreRail(val id: String) {
    POPULAR("popular"),
    LATEST("latest")
}

/**
 * Keşfet sayfasının bellek içi anlık görüntüsü.
 *
 * Kullanıcı bir mangaya girip geri döndüğünde sayfa yeniden kompoze edilir;
 * bu önbellek sayesinde ağa tekrar çıkılmaz (video tarafındaki
 * [com.kitsugi.animelist.ui.screens.search.components.AddonExploreCache] ile aynı mantık).
 */
private data class MangaExploreSnapshot(
    val popular: List<MangaDetails>,
    val popularHasNext: Boolean,
    val latest: List<MangaDetails>,
    val latestHasNext: Boolean,
    /** Kullanıcı "Tümünü Gör" grid'inde miydi? (manga detayından dönüşte korunur) */
    val gridRailId: String? = null,
    val gridItems: List<MangaDetails> = emptyList(),
    val gridPage: Int = 1,
    val gridHasNext: Boolean = true,
    /** Mangaya girip dönüldüğünde arama sonuçları kaybolmasın. */
    val searchQuery: String = "",
    val searchResults: List<MangaDetails> = emptyList(),
    val searchPage: Int = 1,
    val searchHasNext: Boolean = false
)

private object MangaExploreCache {
    private val cache = BoundedCache<String, MangaExploreSnapshot>("manga.explore", 8)

    fun get(sourceKey: String): MangaExploreSnapshot? = cache[sourceKey]

    fun put(sourceKey: String, snapshot: MangaExploreSnapshot) {
        cache[sourceKey] = snapshot
    }

    fun remove(sourceKey: String) {
        cache.remove(sourceKey)
    }
}

/**
 * Ekran durumu tutucu.
 *
 * Sınıf `remember(sourceKey)` ile yaşar; bu yüzden ViewModel gibi aktivite
 * ömrü boyunca birikmez (onlarca kaynak gezilse bile bellek şişmez).
 */
@Stable
class MangaSourceExploreState(
    val source: MangaSource,
    private val repository: MangaSourceRepository,
    private val scope: CoroutineScope
) {
    val sourceKey: String get() = source.stableSourceKey()

    /**
     * Kaynak nesnesinden BİR KEZ okunur (Kotatsu parserinde `availableSortOrders`
     * her kompozisyonda sorgulanmasın).
     */
    val supportsLatest: Boolean = source.supportsLatest

    // ── Popüler rafı ──────────────────────────────────────────────────────────
    var popular by mutableStateOf<List<MangaDetails>>(emptyList())
        private set
    var popularLoading by mutableStateOf(false)
        private set
    var popularError by mutableStateOf<String?>(null)
        private set
    var popularErrorCode by mutableStateOf<String?>(null)
        private set
    var popularHasNext by mutableStateOf(true)
        private set

    // ── Son güncellemeler rafı ────────────────────────────────────────────────
    var latest by mutableStateOf<List<MangaDetails>>(emptyList())
        private set
    var latestLoading by mutableStateOf(false)
        private set
    var latestError by mutableStateOf<String?>(null)
        private set
    var latestErrorCode by mutableStateOf<String?>(null)
        private set
    var latestHasNext by mutableStateOf(true)
        private set

    // ── Kaynak içi arama ──────────────────────────────────────────────────────
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<MangaDetails>>(emptyList())
        private set
    var searchLoading by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var searchErrorCode by mutableStateOf<String?>(null)
        private set
    var searchHasNext by mutableStateOf(false)
        private set
    var searchPage by mutableStateOf(1)
        private set

    // ── "Tümünü Gör" tam ekran grid ───────────────────────────────────────────
    var gridRail by mutableStateOf<MangaExploreRail?>(null)
        private set
    var gridItems by mutableStateOf<List<MangaDetails>>(emptyList())
        private set
    var gridLoading by mutableStateOf(false)
        private set
    var gridError by mutableStateOf<String?>(null)
        private set
    var gridErrorCode by mutableStateOf<String?>(null)
        private set
    var gridHasNext by mutableStateOf(true)
        private set
    var gridPage by mutableStateOf(1)
        private set

    private var popularJob: Job? = null
    private var latestJob: Job? = null
    private var searchJob: Job? = null
    private var gridJob: Job? = null
    private var searchToken = 0

    /** İlk açılış: önbellek varsa anında göster, yoksa rafları çek. */
    fun start() {
        val cached = MangaExploreCache.get(sourceKey)
        if (cached != null) {
            popular = cached.popular
            popularHasNext = cached.popularHasNext
            latest = cached.latest
            latestHasNext = cached.latestHasNext
            // Mangaya girip dönüldüyse aynı "Tümünü Gör" grid'i yerinde açılsın.
            val restoredRail = cached.gridRailId?.let { id ->
                MangaExploreRail.values().firstOrNull { it.id == id }
            }
            if (restoredRail != null && cached.gridItems.isNotEmpty()) {
                gridRail = restoredRail
                gridItems = cached.gridItems
                gridPage = cached.gridPage
                gridHasNext = cached.gridHasNext
            }
            // Mangaya girip dönüldüyse arama sonuçları da yerinde kalsın.
            if (cached.searchQuery.isNotBlank()) {
                searchQuery = cached.searchQuery
                searchResults = cached.searchResults
                searchPage = cached.searchPage
                searchHasNext = cached.searchHasNext
            }
        }

        // Yarıda kalmış (sonuçsuz) bir arama geri yüklendiyse tekrar çalıştır;
        // kullanıcıya boş "sonuç bulunamadı" ekranı gösterme.
        if (gridRail == null && searchQuery.isNotBlank() && searchResults.isEmpty() && !searchLoading) {
            search(searchQuery)
        }

        val needPopular = popular.isEmpty() && !popularLoading
        val needLatest = supportsLatest && latest.isEmpty() && !latestLoading
        if (needPopular) {
            val job = loadPopular(force = false)
            if (needLatest) {
                // Raflar SIRAYLA yüklenir: bazı kaynaklarda eşzamanlı iki liste
                // isteği hız sınırına (HTTP 429) takılıyor.
                scope.launch {
                    job?.join()
                    if (latest.isEmpty() && !latestLoading) loadLatest(force = false)
                }
            }
        } else if (needLatest) {
            loadLatest(force = false)
        }
    }

    /** Ekranın sağ üstündeki yenile butonu: açık olan görünümü tazeler. */
    fun refresh() {
        when {
            gridRail != null -> loadGridFirstPage()
            searchQuery.isNotBlank() -> search(searchQuery)
            else -> {
                MangaExploreCache.remove(sourceKey)
                val job = loadPopular(force = true)
                if (supportsLatest) {
                    scope.launch {
                        job?.join()
                        loadLatest(force = true)
                    }
                }
            }
        }
    }

    // ── Veri çekme yardımcıları ───────────────────────────────────────────────

    private suspend fun fetchPopularPage(page: Int): MangaSourceResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            try {
                val result = source.fetchPopularManga(page)
                repository.recordPopularSuccess(source, System.currentTimeMillis() - startedAt)
                result.withSourceName()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                repository.recordPopularFailure(source, e, System.currentTimeMillis() - startedAt)
                throw e
            }
        }

    private suspend fun fetchLatestPage(page: Int): MangaSourceResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            try {
                val result = source.fetchLatestManga(page)
                repository.recordPopularSuccess(source, System.currentTimeMillis() - startedAt)
                result.withSourceName()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                repository.recordPopularFailure(source, e, System.currentTimeMillis() - startedAt)
                throw e
            }
        }

    private suspend fun fetchSearchPage(page: Int, query: String): MangaSourceResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            try {
                val result = source.fetchSearchManga(page, query)
                repository.recordSearchSuccess(source, System.currentTimeMillis() - startedAt)
                result.withSourceName()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                repository.recordSearchFailure(source, e, System.currentTimeMillis() - startedAt)
                throw e
            }
        }

    private fun MangaSourceResult.withSourceName(): MangaSourceResult =
        copy(mangas = mangas.map { it.copy(source = source.name) }.distinctBy { it.url })

    private fun persistSnapshot() {
        MangaExploreCache.put(
            sourceKey,
            MangaExploreSnapshot(
                popular = popular,
                popularHasNext = popularHasNext,
                latest = latest,
                latestHasNext = latestHasNext,
                gridRailId = gridRail?.id,
                gridItems = gridItems,
                gridPage = gridPage,
                gridHasNext = gridHasNext,
                searchQuery = searchQuery,
                searchResults = searchResults,
                searchPage = searchPage,
                searchHasNext = searchHasNext
            )
        )
    }

    // ── Raflar ────────────────────────────────────────────────────────────────

    fun loadPopular(force: Boolean = false): Job? {
        if (popularLoading) return null
        if (!force && popular.isNotEmpty()) return null
        popularJob?.cancel()
        // Bayrak SENKRON olarak işaretlenir: çift dokunuş aynı rafı iki kez çekmesin.
        popularLoading = true
        popularError = null
        popularErrorCode = null
        val job = scope.launch {
            try {
                val result = fetchPopularPage(1)
                popular = result.mangas
                popularHasNext = result.hasNextPage
                persistSnapshot()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                popularError = e.message ?: ""
                popularErrorCode = SourceFailureClassifier.classifyCode(e)
                popularHasNext = false
            } finally {
                popularLoading = false
            }
        }
        popularJob = job
        return job
    }

    fun loadLatest(force: Boolean = false): Job? {
        if (!supportsLatest || latestLoading) return null
        if (!force && latest.isNotEmpty()) return null
        latestJob?.cancel()
        latestLoading = true
        latestError = null
        latestErrorCode = null
        val job = scope.launch {
            try {
                val result = fetchLatestPage(1)
                latest = result.mangas
                latestHasNext = result.hasNextPage
                persistSnapshot()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                latestError = e.message ?: ""
                latestErrorCode = SourceFailureClassifier.classifyCode(e)
                latestHasNext = false
            } finally {
                latestLoading = false
            }
        }
        latestJob = job
        return job
    }

    // ── Arama ─────────────────────────────────────────────────────────────────

    fun search(text: String) {
        searchQuery = text
        val query = text.trim()
        searchToken += 1
        val token = searchToken
        searchJob?.cancel()

        if (query.isEmpty()) {
            searchResults = emptyList()
            searchError = null
            searchErrorCode = null
            searchHasNext = false
            searchPage = 1
            searchLoading = false
            persistSnapshot()
            return
        }

        // Grid açıkken yazmaya başlandıysa grid terk edilir; arama önceliklidir.
        if (gridRail != null) closeGrid()

        searchLoading = true
        searchError = null
        searchErrorCode = null
        searchJob = scope.launch {
            // Yazarken her tuşta istek atmayı engelleyen kısa debounce.
            delay(260)
            if (token != searchToken) return@launch
            try {
                val result = fetchSearchPage(1, query)
                if (token != searchToken) return@launch
                searchResults = result.mangas
                searchPage = 1
                searchHasNext = result.hasNextPage
                persistSnapshot()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (token != searchToken) return@launch
                searchResults = emptyList()
                searchHasNext = false
                searchError = e.message ?: ""
                searchErrorCode = SourceFailureClassifier.classifyCode(e)
                persistSnapshot()
            } finally {
                if (token == searchToken) searchLoading = false
            }
        }
    }

    fun loadMoreSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty() || searchLoading || !searchHasNext) return
        val nextPage = searchPage + 1
        val token = searchToken
        searchLoading = true
        searchJob = scope.launch {
            try {
                val result = fetchSearchPage(nextPage, query)
                if (token != searchToken) return@launch
                searchResults = (searchResults + result.mangas).distinctBy { it.url }
                searchPage = nextPage
                searchHasNext = result.hasNextPage
                persistSnapshot()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (token != searchToken) return@launch
                searchHasNext = false
            } finally {
                if (token == searchToken) searchLoading = false
            }
        }
    }

    // ── "Tümünü Gör" grid ─────────────────────────────────────────────────────

    fun openGrid(rail: MangaExploreRail) {
        gridRail = rail
        when (rail) {
            MangaExploreRail.POPULAR -> {
                gridItems = popular
                gridHasNext = popularHasNext
            }
            MangaExploreRail.LATEST -> {
                gridItems = latest
                gridHasNext = latestHasNext
            }
        }
        gridPage = 1
        gridError = null
        gridErrorCode = null
        persistSnapshot()
        // Önbellekten gelen ilk sayfa varsa hemen ekstra istek atma; boşsa çek.
        if (gridItems.isEmpty()) loadGridFirstPage()
    }

    fun closeGrid() {
        gridJob?.cancel()
        gridRail = null
        gridItems = emptyList()
        gridError = null
        gridErrorCode = null
        gridLoading = false
        gridPage = 1
        gridHasNext = true
        persistSnapshot()
    }

    fun loadGridFirstPage() {
        val rail = gridRail ?: return
        gridJob?.cancel()
        gridJob = scope.launch {
            gridLoading = true
            gridError = null
            gridErrorCode = null
            try {
                val result = when (rail) {
                    MangaExploreRail.POPULAR -> fetchPopularPage(1)
                    MangaExploreRail.LATEST -> fetchLatestPage(1)
                }
                gridItems = result.mangas
                gridPage = 1
                gridHasNext = result.hasNextPage
                if (rail == MangaExploreRail.POPULAR) {
                    popular = result.mangas
                    popularHasNext = result.hasNextPage
                } else {
                    latest = result.mangas
                    latestHasNext = result.hasNextPage
                }
                persistSnapshot()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                gridItems = emptyList()
                gridHasNext = false
                gridError = e.message ?: ""
                gridErrorCode = SourceFailureClassifier.classifyCode(e)
                persistSnapshot()
            } finally {
                gridLoading = false
            }
        }
    }

    fun loadMoreGrid() {
        val rail = gridRail ?: return
        if (gridLoading || !gridHasNext) return
        val nextPage = gridPage + 1
        gridJob = scope.launch {
            gridLoading = true
            try {
                val result = when (rail) {
                    MangaExploreRail.POPULAR -> fetchPopularPage(nextPage)
                    MangaExploreRail.LATEST -> fetchLatestPage(nextPage)
                }
                gridItems = (gridItems + result.mangas).distinctBy { it.url }
                gridPage = nextPage
                gridHasNext = result.hasNextPage
                persistSnapshot()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                gridHasNext = false
            } finally {
                gridLoading = false
            }
        }
    }
}

/** Yüklenen raflardaki türlerden keşfet çipleri üretir (en sık geçenler önce). */
private fun mangaExploreGenres(
    popular: List<MangaDetails>,
    latest: List<MangaDetails>
): List<String> = (popular + latest)
    .flatMap { it.genre }
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .groupingBy { it }
    .eachCount()
    .entries
    .sortedWith(
        compareByDescending<Map.Entry<String, Int>> { it.value }
            .thenBy { it.key }
    )
    .map { it.key }
    .take(12)

// ─────────────────────────────────────────────────────────────────────────────
// Ekran
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MangaSourceExploreScreen(
    source: MangaSource,
    repository: MangaSourceRepository,
    onMangaClick: (MangaSource, MangaDetails) -> Unit,
    onBack: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val showAdultContent = LocalShowAdultContent.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val state = remember(source.stableSourceKey()) {
        MangaSourceExploreState(source = source, repository = repository, scope = scope)
    }
    LaunchedEffect(state) { state.start() }

    // Sistem geri tuşu ve üst bar oku AYNI hiyerarşiyi izler:
    // grid → arama → sayfadan çık.
    val goBack: () -> Unit = {
        when {
            state.gridRail != null -> state.closeGrid()
            state.searchQuery.isNotBlank() -> {
                state.search("")
                keyboardController?.hide()
            }
            else -> onBack()
        }
    }

    BackHandler { goBack() }

    val popularVisible = remember(state.popular, showAdultContent) {
        if (showAdultContent) state.popular else state.popular.filterNot { it.isAdultContent }
    }
    val latestVisible = remember(state.latest, showAdultContent) {
        if (showAdultContent) state.latest else state.latest.filterNot { it.isAdultContent }
    }
    val searchVisible = remember(state.searchResults, showAdultContent) {
        if (showAdultContent) state.searchResults else state.searchResults.filterNot { it.isAdultContent }
    }
    val gridVisible = remember(state.gridItems, showAdultContent) {
        if (showAdultContent) state.gridItems else state.gridItems.filterNot { it.isAdultContent }
    }
    val genres = remember(popularVisible, latestVisible) {
        mangaExploreGenres(popularVisible, latestVisible)
    }

    Box(Modifier.fillMaxSize().background(KitsugiColors.Background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ── Üst bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = goBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.geri),
                        tint = KitsugiColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.manga_explore_header_subtitle, source.lang.uppercase()),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { state.refresh() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.yenile),
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Kaynak içi arama ───────────────────────────────────────────────
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { state.search(it) },
                placeholder = {
                    Text(
                        stringResource(R.string.manga_explore_search_hint, source.name),
                        color = KitsugiColors.TextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { state.search("") }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.manga_explore_clear_search),
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = KitsugiColors.Border,
                    cursorColor = accentColor,
                    focusedTextColor = KitsugiColors.TextPrimary,
                    unfocusedTextColor = KitsugiColors.TextPrimary,
                    focusedContainerColor = KitsugiColors.Surface.copy(alpha = 0.5f),
                    unfocusedContainerColor = KitsugiColors.Surface.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(10.dp))

            // ── İçerik ─────────────────────────────────────────────────────────
            when {
                state.gridRail != null -> MangaExploreFullGrid(
                    rail = state.gridRail!!,
                    mangas = gridVisible,
                    isLoading = state.gridLoading,
                    error = state.gridError,
                    errorCode = state.gridErrorCode,
                    hasNext = state.gridHasNext,
                    accentColor = accentColor,
                    onBack = { state.closeGrid() },
                    onRetry = { state.loadGridFirstPage() },
                    onLoadMore = { state.loadMoreGrid() },
                    onMangaClick = { manga -> onMangaClick(source, manga) },
                    modifier = Modifier.weight(1f)
                )

                state.searchQuery.isNotBlank() -> MangaExploreGrid(
                    mangas = searchVisible,
                    isLoading = state.searchLoading,
                    error = state.searchError,
                    errorCode = state.searchErrorCode,
                    hasNext = state.searchHasNext,
                    accentColor = accentColor,
                    headerTitle = stringResource(R.string.manga_explore_search_results, state.searchQuery.trim()),
                    onRetry = { state.search(state.searchQuery) },
                    onLoadMore = { state.loadMoreSearch() },
                    onMangaClick = { manga -> onMangaClick(source, manga) },
                    modifier = Modifier.weight(1f)
                )

                else -> MangaExploreHomeContent(
                    state = state,
                    popular = popularVisible,
                    latest = latestVisible,
                    genres = genres,
                    accentColor = accentColor,
                    baseUrl = source.baseUrl,
                    onGenreClick = { genre -> state.search(genre) },
                    onMangaClick = { manga -> onMangaClick(source, manga) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ana (raf) görünümü
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MangaExploreHomeContent(
    state: MangaSourceExploreState,
    popular: List<MangaDetails>,
    latest: List<MangaDetails>,
    genres: List<String>,
    accentColor: Color,
    baseUrl: String,
    onGenreClick: (String) -> Unit,
    onMangaClick: (MangaDetails) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        if (state.supportsLatest) {
            item(key = "manga_explore_latest_rail") {
                MangaExploreRailSection(
                    title = stringResource(R.string.manga_explore_latest),
                    icon = Icons.Rounded.Update,
                    mangas = latest,
                    isLoading = state.latestLoading,
                    error = state.latestError,
                    errorCode = state.latestErrorCode,
                    accentColor = accentColor,
                    onRetry = { state.loadLatest(force = true) },
                    onSeeAll = { state.openGrid(MangaExploreRail.LATEST) },
                    onMangaClick = onMangaClick
                )
            }
        }

        item(key = "manga_explore_popular_rail") {
            MangaExploreRailSection(
                title = stringResource(R.string.manga_explore_popular),
                icon = Icons.Rounded.LocalFireDepartment,
                mangas = popular,
                isLoading = state.popularLoading,
                error = state.popularError,
                errorCode = state.popularErrorCode,
                accentColor = accentColor,
                onRetry = { state.loadPopular(force = true) },
                onSeeAll = { state.openGrid(MangaExploreRail.POPULAR) },
                onMangaClick = onMangaClick
            )
        }

        if (genres.isNotEmpty() && !state.popularLoading) {
            item(key = "manga_explore_genres") {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.manga_explore_genres_title),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(genres, key = { it }) { genre ->
                            MangaExploreGenreChip(
                                text = genre,
                                accentColor = accentColor,
                                onClick = { onGenreClick(genre) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "manga_explore_source_footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Text(
                    text = stringResource(R.string.manga_explore_footer_title, state.source.name),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = baseUrl,
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MangaExploreRailSection(
    title: String,
    icon: ImageVector,
    mangas: List<MangaDetails>,
    isLoading: Boolean,
    error: String?,
    errorCode: String?,
    accentColor: Color,
    onRetry: () -> Unit,
    onSeeAll: () -> Unit,
    onMangaClick: (MangaDetails) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (mangas.isNotEmpty()) {
                TextButton(onClick = onSeeAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(
                        text = stringResource(R.string.tumunu_gor),
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        when {
            isLoading && mangas.isEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(6) { MangaExploreShimmerCard(width = 118.dp) }
                }
            }

            error != null && mangas.isEmpty() -> {
                MangaExploreErrorBlock(
                    message = error,
                    errorCode = errorCode,
                    accentColor = accentColor,
                    onRetry = onRetry
                )
            }

            mangas.isEmpty() -> {
                Text(
                    text = stringResource(R.string.manga_explore_rail_empty),
                    color = KitsugiColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(mangas, key = { it.url }) { manga ->
                        MangaExplorePosterCard(
                            manga = manga,
                            accentColor = accentColor,
                            width = 118.dp,
                            onClick = { onMangaClick(manga) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Arama sonuçları / "Tümünü Gör" grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MangaExploreGrid(
    mangas: List<MangaDetails>,
    isLoading: Boolean,
    error: String?,
    errorCode: String?,
    hasNext: Boolean,
    accentColor: Color,
    headerTitle: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onMangaClick: (MangaDetails) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = rememberKitsugiGridColumns()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "search_header", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = headerTitle,
                color = KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            isLoading && mangas.isEmpty() -> {
                items(count = columns * 2, key = { "search_sk_$it" }) {
                    MangaExploreShimmerCard(fillWidth = true)
                }
            }

            error != null && mangas.isEmpty() -> {
                item(key = "search_error", span = { GridItemSpan(maxLineSpan) }) {
                    MangaExploreErrorBlock(
                        message = error,
                        errorCode = errorCode,
                        accentColor = accentColor,
                        onRetry = onRetry
                    )
                }
            }

            mangas.isEmpty() -> {
                item(key = "search_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.sonuc_bulunamadi),
                            color = KitsugiColors.TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            else -> {
                gridItems(mangas, key = { it.url }) { manga ->
                    MangaExplorePosterCard(
                        manga = manga,
                        accentColor = accentColor,
                        width = null,
                        onClick = { onMangaClick(manga) }
                    )
                }
                if (hasNext) {
                    item(key = "search_footer", span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(mangas.size) { onLoadMore() }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiPlasmaLoader(size = 28.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaExploreFullGrid(
    rail: MangaExploreRail,
    mangas: List<MangaDetails>,
    isLoading: Boolean,
    error: String?,
    errorCode: String?,
    hasNext: Boolean,
    accentColor: Color,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onMangaClick: (MangaDetails) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = rememberKitsugiGridColumns()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "rail_header", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.geri),
                        tint = KitsugiColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when (rail) {
                        MangaExploreRail.POPULAR -> stringResource(R.string.manga_explore_popular)
                        MangaExploreRail.LATEST -> stringResource(R.string.manga_explore_latest)
                    },
                    color = KitsugiColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.manga_explore_count, mangas.size),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        when {
            isLoading && mangas.isEmpty() -> {
                items(count = columns * 2, key = { "rail_sk_$it" }) {
                    MangaExploreShimmerCard(fillWidth = true)
                }
            }

            error != null && mangas.isEmpty() -> {
                item(key = "rail_error", span = { GridItemSpan(maxLineSpan) }) {
                    MangaExploreErrorBlock(
                        message = error,
                        errorCode = errorCode,
                        accentColor = accentColor,
                        onRetry = onRetry
                    )
                }
            }

            mangas.isEmpty() -> {
                item(key = "rail_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.manga_explore_rail_empty),
                            color = KitsugiColors.TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            else -> {
                gridItems(mangas, key = { it.url }) { manga ->
                    MangaExplorePosterCard(
                        manga = manga,
                        accentColor = accentColor,
                        width = null,
                        onClick = { onMangaClick(manga) }
                    )
                }
                if (hasNext) {
                    item(key = "rail_footer", span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(mangas.size) { onLoadMore() }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiPlasmaLoader(size = 28.dp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ortak parçalar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Manga posteri kartı.
 *
 * @param width `null` ise kart bulunduğu hücreyi doldurur (grid), aksi halde
 *   sabit genişlikte kullanılır (yatay raflar).
 */
@Composable
private fun MangaExplorePosterCard(
    manga: MangaDetails,
    accentColor: Color,
    width: Dp?,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .clip(cardShape)
            .tvClickable(shape = cardShape, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(cardShape)
                .background(KitsugiColors.SurfaceStrong)
        ) {
            if (!manga.thumbnailUrl.isNullOrBlank()) {
                KitsugiNsfwImage(
                    model = manga.thumbnailUrl,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    isAdult = manga.isAdultContent,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Book,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = manga.title,
            color = KitsugiColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val author = manga.author
        if (!author.isNullOrBlank()) {
            Text(
                text = author,
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MangaExploreGenreChip(
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(accentColor.copy(alpha = 0.12f))
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun MangaExploreErrorBlock(
    message: String,
    errorCode: String?,
    accentColor: Color,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message.ifBlank { stringResource(R.string.kesfet_icerigi_yuklenemedi) },
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (errorCode != null) {
            Spacer(Modifier.height(6.dp))
            PlatformErrorCodeHint(code = errorCode)
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.yeniden_dene),
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MangaExploreShimmerCard(
    width: Dp? = null,
    fillWidth: Boolean = false
) {
    Column(
        modifier = if (fillWidth) Modifier.fillMaxWidth()
        else Modifier.width(width ?: 118.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.SurfaceSoft)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(KitsugiColors.SurfaceSoft)
        )
    }
}
