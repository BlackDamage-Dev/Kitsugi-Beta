@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import com.kitsugi.animelist.ui.components.KitsugiMotion
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kitsugi.animelist.ui.screens.mylist.components.KitsugiListStatusBottomSheet
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.data.local.MyListIdentityCrosswalk
import com.kitsugi.animelist.data.remote.BangumiTitleCache
import com.kitsugi.animelist.data.remote.matchesInSource
import com.kitsugi.animelist.data.remote.BangumiMediaEntryFactory
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.ui.components.KitsugiApiSearchDialog
import com.kitsugi.animelist.ui.components.KitsugiConfirmDialog
import com.kitsugi.animelist.ui.components.KitsugiInfoDialog
import com.kitsugi.animelist.ui.components.KitsugiImagePreviewDialog
import com.kitsugi.animelist.ui.components.KitsugiMediaEntryEditorDialog
import com.kitsugi.animelist.ui.components.KitsugiKitsuLoginDialog
import com.kitsugi.animelist.ui.components.KitsugiBangumiLoginDialog
import com.kitsugi.animelist.ui.components.KitsugiShikimoriLoginDialog
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.CompositionLocalProvider
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * [IntArray] için açık Saver: IntArray, Android Bundle tarafından yerel olarak desteklenir
 * (putIntArray). copyOf ile saklanarak mutasyondan yalıtılır.
 */
private val MyListTabPositionSaver: androidx.compose.runtime.saveable.Saver<IntArray, IntArray> =
    androidx.compose.runtime.saveable.Saver(
        save = { it.copyOf() },
        restore = { it.copyOf() }
    )

@Composable
fun MyListScreen(
    selectedListLayoutId: String,
    onListLayoutChange: (String) -> Unit,
    showAdultContent: Boolean,
    appSettings: com.kitsugi.animelist.data.settings.AppSettings,
    searchQuery: String,
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedFavoriteFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String,
    selectedSortId: String,
    selectedGroupId: String = MyListGroupingIds.ADDED,
    onGroupChange: (String) -> Unit = {},
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    selectedTabIndex: Int,
    onTabIndexChange: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onTypeFilterChange: (String) -> Unit,
    onFavoriteFilterChange: (String) -> Unit,
    onScoreFilterChange: (String) -> Unit,
    onYearFilterChange: (String) -> Unit,
    onExtraFilterChange: (String) -> Unit,
    onSortChange: (String) -> Unit,
    onScrollPositionChange: (index: Int, offset: Int) -> Unit,
    onExternalSyncMessage: (String) -> Unit,
    isAniListConnected: Boolean,
    isMalConnected: Boolean,
    isSimklConnected: Boolean,
    isSimklSessionExpired: Boolean,
    isKitsuConnected: Boolean = false,
    isShikimoriConnected: Boolean = false,
    isBangumiConnected: Boolean = false,
    isTmdbConnected: Boolean = false,
    tmdbUsername: String = "",
    onLoginTmdb: () -> Unit = {},
    onLoginAniList: () -> Unit,
    onLoginMal: () -> Unit,
    onLoginSimkl: () -> Unit,
    onLoginKitsu: () -> Unit = {},
    onLoginShikimori: () -> Unit = {},
    onLoginBangumi: () -> Unit = {},
    onSyncAniList: () -> Unit,
    onSyncMal: () -> Unit,
    onSyncSimkl: () -> Unit,
    onSyncKitsu: () -> Unit = {},
    onSyncShikimori: () -> Unit = {},
    onSyncBangumi: () -> Unit = {},
    onSyncTmdb: () -> Unit = {},
    onAddTmdbApiSelectionToList: ((ApiSearchSelection) -> Unit)? = null,
    /** Bağlı tüm kaynakları SIRAYLA yeniler (tek tek ağa çıkmaz). */
    onSyncAll: () -> Unit = {
        var startedSync = false
        if (isAniListConnected) { onSyncAniList(); startedSync = true }
        if (isMalConnected) { onSyncMal(); startedSync = true }
        if (isSimklConnected && !isSimklSessionExpired) { onSyncSimkl(); startedSync = true }
        if (isKitsuConnected) { onSyncKitsu(); startedSync = true }
        if (isShikimoriConnected) { onSyncShikimori(); startedSync = true }
        if (isBangumiConnected) { onSyncBangumi(); startedSync = true }
        if (isTmdbConnected) { onSyncTmdb(); startedSync = true }
        if (!startedSync) onExternalSyncMessage("Birleşik listeyi yenilemek için en az bir kaynağı bağla")
    },
    /** Verilen sekme (MY_LIST_*_TAB_INDEX) için kaynak senkronu sürüyor mu? */
    isTabSyncing: (Int) -> Boolean = { false },
    onKitsuAuthSubmit: (username: String, password: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onShikimoriAuthSubmit: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _, _ -> },
    onBangumiAuthSubmit: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _, _ -> },
    onEntryClick: (MediaEntry) -> Unit,
    onSettingsClick: () -> Unit,
    isBottomBarVisible: Boolean = true,
    onScrollReset: () -> Unit = {},
    isNotificationsVisible: Boolean = false,
    onOpenNotifications: () -> Unit = {},
    onOpenWatchHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val KitsugiColors = LocalKitsugiColors.current

    val viewModel: MyListViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    // ViewModel artık init{} içinde hazır — sadece sync mesajlarını dinle
    LaunchedEffect(Unit) {
        viewModel.syncMessages.collect { message ->
            onExternalSyncMessage(message)
        }
    }

    // Başlık dili değişince (örn. Romaji → İng) Bangumi satırlarının o dile ait eksik
    // varyantları tamamlansın; ViewModel yeniden yaratılmadığı için init taraması yetmez.
    // İlk kompozisyonda da çalışır ama taramalar kilitle sıraya girer, mükerrer iş olmaz.
    LaunchedEffect(appSettings.titleLanguage) {
        viewModel.refreshBangumiTitles(appSettings.titleLanguage)
    }

    val entries by viewModel.entriesFlow.collectAsState()

    // Save positions separately from LazyListState: an empty/loading list can clamp its
    // index to zero before the library finishes loading when returning from a detail page.
    //
    // PERF (2026-10-20): bu konumlar bilinçli olarak OKUNABİLİR Compose state'i DEĞİLDİR.
    // Eski sürüm MutableState<List<Int>> tutuyordu ve kaydırmanın HER karesinde iki liste
    // tahsis edip state yazıyordu; yazı, bu değerleri kompozisyonda okuyan tek kapsam
    // (bu ekran gövdesi) geçersiz kılıyordu → başlık, sekme çubuğu, tüm pager sayfaları ve
    // görünür kartlar HER KAREDE yeniden kompoze oluyordu. Hesaplar bağlanıp liste uzayınca
    // kaydırma/tıklama kilitleniyordu (geri besleme döngüsü — AppViewModel'deki
    // myListScrollIndex için aynı hata daha önce düzeltilmişti, bu yerel kopya atlanmıştı).
    // Düz IntArray: yazım hiçbir kompozisyonu geçersiz kılmaz; rememberSaveable sayesinde
    // süreç ölümünde/detay dönüşünde saklanır.
    val savedTabIndices = rememberSaveable(saver = MyListTabPositionSaver) {
        IntArray(MY_LIST_TAB_COUNT).also { positions ->
            if (selectedTabIndex in 0 until MY_LIST_TAB_COUNT) {
                positions[selectedTabIndex] = initialScrollIndex
            }
        }
    }
    val savedTabOffsets = rememberSaveable(saver = MyListTabPositionSaver) {
        IntArray(MY_LIST_TAB_COUNT).also { positions ->
            if (selectedTabIndex in 0 until MY_LIST_TAB_COUNT) {
                positions[selectedTabIndex] = initialScrollOffset
            }
        }
    }
    var restoredTabs by remember { mutableStateOf(emptySet<Int>()) }

    var isFabVisible by rememberSaveable { mutableStateOf(true) }
    // Üst başlığın görünürlüğü `headerState` ile (aşağıda, aktif sekmenin scroll
    // alanına bağlı) tek yerden yönetilir; MyListHeaderSection yalnızca animasyonu
    // çizer. Ekranda ikinci bir "showHeader" state'i tutmak çift kaynak yaratıyordu.
    var showScrollToTopState by remember { mutableStateOf(false) }
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }
    var showSearchField by rememberSaveable { mutableStateOf(false) }
    var showFilterPanel by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showStatusBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showApiSearchDialog by rememberSaveable { mutableStateOf(false) }
    var showKitsuLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showShikimoriLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showBangumiLoginDialog by rememberSaveable { mutableStateOf(false) }
    var activeZoomImageUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var activeZoomTitle by rememberSaveable { mutableStateOf("") }
    var activeZoomIsAdult by rememberSaveable { mutableStateOf(false) }

    // Six provider tabs plus a combined, deduplicated library tab.
    val tabPagerState = rememberPagerState(
        initialPage = selectedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1),
        pageCount = { MY_LIST_TAB_COUNT }
    )

    // Pager page change -> notify parent
    // İLK çalıştırmada settledPage yalnızca pager'ın kurulum anındaki sayfasıdır; bu an
    // bir "değişiklik" DEĞİLDİR. Ayrıca açılır menüden uzak bir kaynağa atlanırken ara
    // settledPage emisyonlarının seçimi geri çevirmemesi için hedef sayfa izlenir.
    var pagerInitialPageAnnounced by remember { mutableStateOf(false) }
    var programmaticTargetPage by remember { mutableIntStateOf(-1) }

    LaunchedEffect(selectedTabIndex) {
        val target = selectedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1)
        if (tabPagerState.currentPage != target) {
            programmaticTargetPage = target
            try {
                if (kotlin.math.abs(tabPagerState.currentPage - target) > 1) {
                    tabPagerState.scrollToPage(target)
                } else {
                    tabPagerState.animateScrollToPage(target)
                }
            } finally {
                if (programmaticTargetPage == target) {
                    programmaticTargetPage = -1
                }
            }
        }
    }

    LaunchedEffect(tabPagerState.settledPage) {
        if (!pagerInitialPageAnnounced) {
            pagerInitialPageAnnounced = true
        } else if (programmaticTargetPage >= 0 && tabPagerState.settledPage != programmaticTargetPage) {
            // Uzak sekmeye programatik geçiş tamamlanana dek ara sayfa bildirimlerini yok say.
        } else if (tabPagerState.settledPage != selectedTabIndex) {
            onTabIndexChange(tabPagerState.settledPage)
        }
    }

    // Scroll tracking is handled per-tab inside the HorizontalPager LaunchedEffect below.

    var duplicateMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var editingEntry by remember {
        mutableStateOf<MediaEntry?>(null)
    }
    var isAddingBangumiEntry by rememberSaveable { mutableStateOf(false) }

    var deletingEntry by remember {
        mutableStateOf<MediaEntry?>(null)
    }

    var showDetailedStats by rememberSaveable {
        mutableStateOf(false)
    }

    // Kimlik köprüsü + gruplama + kardeş başlık tamamlama + sekme bazlı kütüphane bölümlendirmesi
    // TEK bir arka plan hesabında üretilir; hiçbir kaynak sekmesi başka bir kaynağın kaydını sızdırmaz.
    val librarySnapshot by produceState(viewModel.cachedLibrarySnapshot, entries, showAdultContent) {
        val entriesForLibrary = entries
        val includeAdult = showAdultContent
        val appContext = context.applicationContext
        suspend fun publish(extras: Map<Int, Set<String>>) {
            val computed = withContext(Dispatchers.Default) {
                buildMyListLibrarySnapshot(
                    entries = entriesForLibrary,
                    identityExtras = extras,
                    includeAdult = includeAdult,
                    bangumiTitleLookup = { rawId -> BangumiTitleCache.get(rawId) }
                )
            }
            value = computed
            // Detay dönüşünde ilk karede eski sonuç hazır olsun diye ViewModel'de sakla.
            viewModel.rememberLibrarySnapshot(computed)
        }

        // İlk sonucu kalıcı köprülerle hemen yayınla; ağ doğrulaması listeyi boş/bekliyor
        // durumunda bırakmasın. İkinci tur yalnız Anime↔Movie/TvShow adaylarında çalışır.
        val cachedExtras = MyListIdentityCrosswalk.build(appContext, entriesForLibrary)
        publish(cachedExtras)
        if (MyListIdentityCrosswalk.crossTypeResolutionCandidates(entriesForLibrary).isNotEmpty()) {
            val resolvedExtras = MyListIdentityCrosswalk.build(
                context = appContext,
                entries = entriesForLibrary,
                allowNetwork = true
            )
            if (resolvedExtras != cachedExtras) publish(resolvedExtras)
        }
    }
    val isLibraryComputing = entries.isNotEmpty() && !librarySnapshot.isComputed
    val allSourceBadgesByEntryId = librarySnapshot.sourceBadgesByEntryId
    val tabCounts = remember(librarySnapshot) {
        List(MY_LIST_TAB_COUNT) { tabIdx -> librarySnapshot.countForTab(tabIdx) }
    }

    val safeSelectedTabIndex = selectedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1)
    val selectedTabEntries = librarySnapshot.entriesForTab(safeSelectedTabIndex)

    // Sorgusuz ("") filtrelenmiş + sıralanmış temel görünüm önbelleği:
    // 1) Kullanıcı karakterleri silip aramayı boşalttığında ("") 0.0 ms'de tek referans takasıyla
    //    tüm liste (ör. 829+ sonuç) yeniden filtreleme/sıralama yapılmadan geri yüklenir.
    // 2) Sıralama alt diziler üzerinde sıra koruyucu (order-preserving) olduğu için, arama sorgusu
    //    doluyken zaten sıralanmış olan `baseSortedWithoutQuery` taranır; böylece her tuş vuruşu ve
    //    silme adımında `applySort` maliyeti tamamen sıfırlanır.
    val baseSortedWithoutQuery by produceState(
        selectedTabEntries,
        selectedTabEntries,
        librarySnapshot.searchAliasesByEntryId,
        selectedStatusFilterId,
        selectedTypeFilterId,
        selectedFavoriteFilterId,
        selectedScoreFilterId,
        selectedYearFilterId,
        selectedExtraFilterId,
        selectedSortId,
        appSettings.titleLanguage
    ) {
        val tabItems = selectedTabEntries
        val aliases = librarySnapshot.searchAliasesByEntryId
        value = withContext(Dispatchers.Default) {
            val baseFiltered = filterMyListEntries(
                entries = tabItems,
                searchQuery = "",
                selectedStatusFilterId = selectedStatusFilterId,
                selectedTypeFilterId = selectedTypeFilterId,
                selectedFavoriteFilterId = selectedFavoriteFilterId,
                selectedScoreFilterId = selectedScoreFilterId,
                selectedYearFilterId = selectedYearFilterId,
                selectedExtraFilterId = selectedExtraFilterId,
                searchAliasesByEntryId = aliases
            )
            applySort(
                entries = baseFiltered,
                sortId = selectedSortId,
                titleLanguage = appSettings.titleLanguage
            )
        }
    }

    val baseSectionsWithoutQuery = remember(baseSortedWithoutQuery, selectedGroupId) {
        groupMyListEntries(baseSortedWithoutQuery, selectedGroupId)
    }

    val queryFilteredState by produceState<List<MediaEntry>?>(
        initialValue = null,
        baseSortedWithoutQuery,
        searchQuery,
        librarySnapshot.searchAliasesByEntryId
    ) {
        if (searchQuery.isBlank()) {
            value = null
            return@produceState
        }
        val sortedBase = baseSortedWithoutQuery
        val q = searchQuery
        val aliases = librarySnapshot.searchAliasesByEntryId
        value = withContext(Dispatchers.Default) {
            val filterJob = currentCoroutineContext()[Job]
            filterMyListEntries(
                entries = sortedBase,
                searchQuery = q,
                selectedStatusFilterId = "all",
                selectedTypeFilterId = "all",
                selectedFavoriteFilterId = "all",
                selectedScoreFilterId = "all",
                selectedYearFilterId = "all",
                selectedExtraFilterId = "all",
                searchAliasesByEntryId = aliases,
                cancellationCheck = { filterJob?.ensureActive() }
            )
        }
    }

    val visibleEntries: List<MediaEntry>
    val visibleSections: List<MyListSection>
    if (searchQuery.isBlank()) {
        visibleEntries = baseSortedWithoutQuery
        visibleSections = baseSectionsWithoutQuery
    } else {
        visibleEntries = queryFilteredState ?: baseSortedWithoutQuery
        visibleSections = remember(visibleEntries, selectedGroupId) {
            groupMyListEntries(visibleEntries, selectedGroupId)
        }
    }

    val listStats = remember(selectedTabEntries) {
        ListStats.from(selectedTabEntries)
    }

    fun openListEditor(entry: MediaEntry) {
        editingEntry = entry
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (isLandscape) 12.dp else 20.dp

    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
    val accentColor = com.kitsugi.animelist.ui.theme.LocalKitsugiAccent.current


    // Per-tab scroll states — declared at top level so FAB can reference them
    val tabScrollStates = (0 until MY_LIST_TAB_COUNT).map { page ->
        androidx.compose.runtime.key(page) {
            rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
                androidx.compose.foundation.lazy.LazyListState(savedTabIndices[page], savedTabOffsets[page])
            }
        }
    }
    val activeTabScrollState = tabScrollStates[selectedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1)]

    // ── Başlık (Listem + arama/filtre ikonları) kaydırmaya duyarlı ──────────────
    // Eskiden yalnızca "listenin tepesine dönünce" geri geliyordu ve yükseklik tek
    // karede 60.dp → 0.dp atladığı için bar pat diye kaybolup pat diye geri geliyordu.
    // Artık uygulamanın ortak kaydırma-duyarlı başlık hareketini kullanıyor: aşağı
    // kaydırınca yumuşakça çekiliyor, yukarı kaydırınca yumuşakça geri geliyor.
    val headerState = com.kitsugi.animelist.ui.components.rememberScrollAwareHeaderState(
        listState = activeTabScrollState
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(headerState.nestedScrollConnection)
            .background(KitsugiColors.background)
    ) {
        val hasActiveFilters = selectedStatusFilterId != "all" ||
                selectedTypeFilterId != "all" ||
                selectedFavoriteFilterId != "all" ||
                selectedScoreFilterId != "all" ||
                selectedYearFilterId != "all" ||
                selectedExtraFilterId != "all" ||
                selectedGroupId != MyListGroupingIds.ADDED ||
                isExplicitMyListSort(selectedSortId)

        // ── Sabit üst bar (başlık + arama + filtreleme) ──
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            color = KitsugiColors.background,
            shadowElevation = 0.dp
        ) {
            MyListHeaderSection(
                selectedListLayoutId = selectedListLayoutId,
                onListLayoutChange = onListLayoutChange,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                showSearchField = showSearchField,
                onSearchFieldToggle = { showSearchField = !showSearchField },
                showFilterPanel = showFilterPanel,
                onFilterPanelToggle = { showFilterPanel = !showFilterPanel },
                onHideFilters = { showFilterPanel = false },
                selectedStatusFilterId = selectedStatusFilterId,
                selectedTypeFilterId = selectedTypeFilterId,
                selectedFavoriteFilterId = selectedFavoriteFilterId,
                selectedScoreFilterId = selectedScoreFilterId,
                selectedYearFilterId = selectedYearFilterId,
                selectedExtraFilterId = selectedExtraFilterId,
                selectedSortId = selectedSortId,
                selectedGroupId = selectedGroupId,
                onStatusFilterChange = onStatusFilterChange,
                onTypeFilterChange = onTypeFilterChange,
                onFavoriteFilterChange = onFavoriteFilterChange,
                onScoreFilterChange = onScoreFilterChange,
                onYearFilterChange = onYearFilterChange,
                onExtraFilterChange = onExtraFilterChange,
                onSortChange = onSortChange,
                onGroupChange = onGroupChange,
                activeTabScrollState = activeTabScrollState,
                accentColor = accentColor,
                horizontalPadding = horizontalPadding,
                hasActiveFilters = hasActiveFilters,
                headerCollapsed = headerState.isCollapsed
            )
        }

        val currentVisibleEntries by rememberUpdatedState(visibleEntries)
        val onPickRandom = remember {
            {
                val list = currentVisibleEntries
                if (list.isNotEmpty()) {
                    onEntryClick(list.random())
                } else {
                    onExternalSyncMessage("Gösterilecek bir öğe yok")
                }
            }
        }

        // ── Sabit platform tab bar (AniList / MAL / Simkl) ──────────────────────
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 0.dp),
            color = KitsugiColors.background
        ) {
            MyListTabBar(
                selectedTabIndex = safeSelectedTabIndex,
                onTabIndexChange = onTabIndexChange,
                onPickRandomClick = onPickRandom,
                allEntries = librarySnapshot.combinedEntries,
                allLibraryEntryCount = librarySnapshot.combinedItems.size,
                tabCounts = tabCounts,
                isAniListConnected = isAniListConnected,
                isMalConnected = isMalConnected,
                isSimklConnected = isSimklConnected,
                isKitsuConnected = isKitsuConnected,
                isShikimoriConnected = isShikimoriConnected,
                isBangumiConnected = isBangumiConnected,
                isTmdbConnected = isTmdbConnected,
                tmdbUsername = tmdbUsername,
                anilistUsername = appSettings.anilistUsername,
                malUsername = appSettings.malUsername,
                simklUsername = appSettings.simklUsername,
                kitsuUsername = appSettings.kitsuUsername,
                shikimoriUsername = appSettings.shikimoriUsername,
                bangumiUsername = appSettings.bangumiUsername,
                onEntryClick = onEntryClick,
                onExternalSyncMessage = onExternalSyncMessage,
                accentColor = accentColor,
                horizontalPadding = horizontalPadding,
                isNotificationsVisible = isNotificationsVisible,
                onOpenNotifications = onOpenNotifications,
                onOpenWatchHistory = onOpenWatchHistory,
                onOpenDownloads = onOpenDownloads
            )
        }

        // ── AniList eşitleme durum şeridi (v2.4.341) ────────────────────────────
        // Kullanıcı geri bildirimi: "günlerdir hiçbir şey AniList ile eşitlenmemiş ama
        // nedenini anlamıyorum". Artık neden (kota / oturum / internet / kimlik / kapalı)
        // ve bekleyen değişiklik sayısı burada açıkça yazıyor; "Şimdi dene" kuyruğu zorlar.
        val syncHealth by viewModel.syncHealth.collectAsState()
        LaunchedEffect(Unit) {
            while (true) {
                viewModel.refreshSyncHealth()
                kotlinx.coroutines.delay(5_000L)
            }
        }
        if (syncHealth.visible) {
            SyncStatusBanner(
                title = stringResource(R.string.sync_status_title),
                lines = syncHealth.lines,
                pending = syncHealth.pending,
                isError = syncHealth.isError,
                onRetry = { viewModel.retryAllSourcesSyncNow() },
                modifier = Modifier.padding(horizontal = horizontalPadding)
            )
        }

        // ── HorizontalPager: her sekme kendi scroll alanı ──────────────────────
        // tabScrollStates declared at composable top level


        // activeTabScrollState is declared at composable top level (tabScrollStates[selectedTabIndex])

        LaunchedEffect(activeTabScrollState) {
            snapshotFlow {
                activeTabScrollState.firstVisibleItemIndex to activeTabScrollState.firstVisibleItemScrollOffset
            }.collect { (index, offset) ->
                if (selectedTabIndex in restoredTabs && entries.isNotEmpty()) {
                    onScrollPositionChange(index, offset)
                }
                val scrollingDown = index > prevIndex || (index == prevIndex && offset > prevOffset + 15)
                val scrollingUp   = index < prevIndex || (index == prevIndex && offset < prevOffset - 15)
                // Reset scroll/show bottom bar at top
                if (index == 0 && offset == 0) {
                    onScrollReset()
                }
                // Scroll-to-top FAB
                showScrollToTopState = index > 3
                // FAB (Tümü) görünürlüğü
                if (index == 0 && offset < 40) {
                    isFabVisible = true
                } else if (scrollingDown) {
                    isFabVisible = false
                } else if (scrollingUp) {
                    isFabVisible = true
                }
                // Hızlı aşağı scroll'da panel/arama otomatik kapat
                if (scrollingDown && index >= 1) {
                    if (showFilterPanel) showFilterPanel = false
                    if (showSearchField) showSearchField = false
                }
                prevIndex = index
                prevOffset = offset
            }
        }

        val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides if (isTvDevice) tvSpec else LocalBringIntoViewSpec.current
        ) {
            HorizontalPager(
                state = tabPagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = !isTvDevice
            ) { page ->
                val pageTabIndex = page
                val pageIsConnected = when (pageTabIndex) {
                    MY_LIST_ANILIST_TAB_INDEX -> isAniListConnected
                    MY_LIST_MAL_TAB_INDEX -> isMalConnected
                    MY_LIST_SIMKL_TAB_INDEX -> isSimklConnected && !isSimklSessionExpired
                    MY_LIST_TMDB_TAB_INDEX -> isTmdbConnected
                    MY_LIST_KITSU_TAB_INDEX -> isKitsuConnected
                    MY_LIST_SHIKIMORI_TAB_INDEX -> isShikimoriConnected
                    MY_LIST_BANGUMI_TAB_INDEX -> isBangumiConnected
                    MY_LIST_ALL_TAB_INDEX -> true // A combined library also includes locally cached records.
                    else -> false
                }
                val pageScrollState = tabScrollStates[pageTabIndex]
                val pageEntries = librarySnapshot.entriesForTab(pageTabIndex)
                val isActivePage = pageTabIndex == safeSelectedTabIndex
                val isGlobalSearchActive = searchQuery.isNotBlank() && safeSelectedTabIndex != MY_LIST_ALL_TAB_INDEX
                val useActiveVisible = isActivePage || isGlobalSearchActive
                val pageVisibleEntries = if (useActiveVisible) visibleEntries else pageEntries
                val pageVisibleSections = if (useActiveVisible) visibleSections else emptyList()

                // Wait until this page has actual items before restoring a deep position.
                // A loading/empty LazyColumn would otherwise reset the saved index to zero.
                LaunchedEffect(pageTabIndex, pageEntries.isNotEmpty()) {
                    if (pageEntries.isNotEmpty() && pageTabIndex !in restoredTabs) {
                        val savedIndex = savedTabIndices[pageTabIndex]
                        val savedOffset = savedTabOffsets[pageTabIndex]
                        // LazyListState.Saver konumu kendisi geri yüklediyse (detay dönüşü,
                        // süreç ölümü) dokunma; yalnızca boş liste yüzünden sıfıra
                        // kelepçelenmiş kayıp bir konumu kurtar. Koşulsuz scrollToItem,
                        // Saver'ın doğru restore ettiği sekmeleri başa çekebilirdi.
                        val alreadyAtSaved = pageScrollState.firstVisibleItemIndex == savedIndex &&
                                pageScrollState.firstVisibleItemScrollOffset == savedOffset
                        val hasSavedPosition = savedIndex > 0 || savedOffset > 0
                        if (!alreadyAtSaved && hasSavedPosition) {
                            pageScrollState.scrollToItem(savedIndex, savedOffset)
                        }
                        restoredTabs = restoredTabs + pageTabIndex
                    }
                }
                LaunchedEffect(pageScrollState, pageTabIndex, pageEntries.isNotEmpty(), restoredTabs.contains(pageTabIndex)) {
                    snapshotFlow { pageScrollState.firstVisibleItemIndex to pageScrollState.firstVisibleItemScrollOffset }
                        .collect { (index, offset) ->
                            if (pageTabIndex in restoredTabs && pageEntries.isNotEmpty()) {
                                // PERF: düz IntArray yazımı — Compose state yazımı DEĞİL.
                                // Eski sürüm burada her kaydırma karesinde iki MutableList
                                // tahsis edip MutableState yazıyordu; bu da tüm ekranın
                                // her karede yeniden kompoze olmasını tetikliyordu.
                                savedTabIndices[pageTabIndex] = index
                                savedTabOffsets[pageTabIndex] = offset
                            }
                        }
                }

                MyListContentPage(
                    pageTabIndex = pageTabIndex,
                    selectedTabIndex = safeSelectedTabIndex,
                    isConnected = pageIsConnected,
                    isSimklSessionExpired = isSimklSessionExpired,
                    isLibraryComputing = isLibraryComputing,
                    pageEntries = pageEntries,
                    sourceBadgesByEntryId = if (pageTabIndex == MY_LIST_ALL_TAB_INDEX) allSourceBadgesByEntryId else emptyMap(),
                    visibleEntries = pageVisibleEntries,
                    visibleSections = pageVisibleSections,
                    selectedGroupId = selectedGroupId,
                    searchQuery = searchQuery,
                    selectedStatusFilterId = selectedStatusFilterId,
                    selectedTypeFilterId = selectedTypeFilterId,
                    selectedFavoriteFilterId = selectedFavoriteFilterId,
                    selectedScoreFilterId = selectedScoreFilterId,
                    selectedYearFilterId = selectedYearFilterId,
                    selectedExtraFilterId = selectedExtraFilterId,
                    selectedSortId = selectedSortId,
                    selectedListLayoutId = selectedListLayoutId,
                    appSettings = appSettings,
                    pageScrollState = pageScrollState,
                    isSyncing = isTabSyncing(pageTabIndex),
                    onLogin = {
                        when (pageTabIndex) {
                            MY_LIST_ANILIST_TAB_INDEX -> onLoginAniList()
                            MY_LIST_MAL_TAB_INDEX -> onLoginMal()
                            // Keep the established Simkl login action. TMDB has its own
                            // account card in Settings; it must not replace Simkl onboarding.
                            MY_LIST_SIMKL_TAB_INDEX -> onLoginSimkl()
                            MY_LIST_KITSU_TAB_INDEX -> {
                                if (isKitsuConnected) onLoginKitsu()
                                else showKitsuLoginDialog = true
                            }
                            MY_LIST_SHIKIMORI_TAB_INDEX -> {
                                if (isShikimoriConnected) onLoginShikimori()
                                else showShikimoriLoginDialog = true
                            }
                            MY_LIST_BANGUMI_TAB_INDEX -> {
                                if (isBangumiConnected) onLoginBangumi()
                                else showBangumiLoginDialog = true
                            }
                            MY_LIST_TMDB_TAB_INDEX -> onLoginTmdb()
                        }
                    },
                    onRefresh = {
                        when (pageTabIndex) {
                            MY_LIST_ANILIST_TAB_INDEX -> onSyncAniList()
                            MY_LIST_MAL_TAB_INDEX -> onSyncMal()
                            MY_LIST_SIMKL_TAB_INDEX -> onSyncSimkl()
                            MY_LIST_TMDB_TAB_INDEX -> onSyncTmdb()
                            MY_LIST_KITSU_TAB_INDEX -> onSyncKitsu()
                            MY_LIST_SHIKIMORI_TAB_INDEX -> onSyncShikimori()
                            MY_LIST_BANGUMI_TAB_INDEX -> onSyncBangumi()
                            // Birleşik liste: kaynaklar TEK çağrıda, sırayla ve kendi
                            // TTL'lerine göre yenilenir (eskiden 6 içe aktarma aynı anda
                            // başlatılıyordu → paralel ağ trafiği + ardışık tam yeniden
                            // yazımlar → liste birkaç kez baştan çiziliyordu).
                            MY_LIST_ALL_TAB_INDEX -> onSyncAll()
                        }
                    },
                    onEntryClick = onEntryClick,
                    onEditEntry = { openListEditor(it) },
                    onPosterLongClick = { imageUrl ->
                        activeZoomImageUrl = imageUrl
                        val zoomEntry = pageVisibleEntries.find { it.imageUrl == imageUrl }
                            ?: pageEntries.find { it.imageUrl == imageUrl }
                        activeZoomTitle = zoomEntry?.getDisplayTitle(appSettings.titleLanguage)
                            ?: zoomEntry?.title
                            ?: ""
                        activeZoomIsAdult = zoomEntry?.isAdult == true
                    },
                    accentColor = accentColor,
                    horizontalPadding = horizontalPadding
                )
            }
        }

    } // end Column

    // ── Scroll-aware floating category button ──
    val isTv = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
    if (!isTv) {
        val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomPadding by animateDpAsState(
            targetValue = if (isLandscape) {
                16.dp
            } else if (isBottomBarVisible) {
                96.dp + navigationBarsPadding
            } else {
                16.dp + navigationBarsPadding
            },
            animationSpec = tween(durationMillis = 200),
            label = "fab_bottom_padding"
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Tümü (Kategori) button on the Bottom-Start (Bottom-Left)
            AnimatedVisibility(
                visible = isFabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        bottom = bottomPadding,
                        start = 20.dp
                    )
                    .zIndex(10f)
            ) {
                val activeStatusLabel = statusFilters.firstOrNull { it.id == selectedStatusFilterId }
                    ?.let { myListLocalizedTitle(it.title, it.titleRes) }
                    ?: if (selectedStatusFilterId == "adult") stringResource(R.string.filter_adult)
                    else if (selectedStatusFilterId == "favorites") stringResource(R.string.mylist_filter_favorites)
                    else stringResource(R.string.mylist_filter_all)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(999.dp), onClick = { showStatusBottomSheet = true })
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.FormatListBulleted,
                            contentDescription = "Kategori",
                            tint = KitsugiColors.background,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeStatusLabel,
                            color = KitsugiColors.background,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Scroll to Top button on the Bottom-End (Bottom-Right)
            AnimatedVisibility(
                visible = showScrollToTopState,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = bottomPadding,
                        end = 20.dp
                    )
                    .zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(16.dp)) {
                            coroutineScope.launch {
                                val activeState = tabScrollStates[selectedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1)]
                                activeState.animateScrollToItem(0)
                                onScrollReset()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.yukari_git),
                        tint = KitsugiColors.background,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showStatusBottomSheet) {
        KitsugiListStatusBottomSheet(
            entries = selectedTabEntries,
            selectedStatusFilterId = selectedStatusFilterId,
            showAdultContent = showAdultContent,
            onStatusSelected = { newStatusId ->
                onStatusFilterChange(newStatusId)
            },
            onDismissRequest = {
                showStatusBottomSheet = false
            }
        )
    }


    if (showAddDialog) {
        // Manüel bir başlık Bangumi hesabında varmış gibi gösterilmesin; yalnızca Bangumi
        // katalog sonucuyla doğrulanmış/aktarılmış stable subject ID'leri Bangumi sekmesine girer.
        val manualAddSource = if (selectedTabIndex == MY_LIST_BANGUMI_TAB_INDEX || selectedTabIndex == MY_LIST_TMDB_TAB_INDEX) {
            // Account tabs require a verified provider id; a typed title alone must not
            // manufacture a Bangumi/TMDB membership row. Use API search for those accounts.
            "manual"
        } else {
            defaultMyListSourceForTab(selectedTabIndex)
        }
        KitsugiMediaEntryEditorDialog(
            source = manualAddSource,
            onDismiss = {
                showAddDialog = false
            },
            onConfirm = { title, subtitle, type, status, isAdult, progress, total, score, isFavorite, startDate, endDate, notes, tags, priority, isRepeating, repeatCount, repeatValue, volumeProgress, isPrivate, isHiddenFromStatusLists, _, _, _ ->
                val newEntry = MediaEntry(
                    id = 0,
                    title = title,
                    subtitle = if (subtitle.isBlank()) {
                        "Manuel ${myListSourceDisplayName(manualAddSource)} Kaydı"
                    } else {
                        subtitle
                    },
                    type = type,
                    status = status,
                    score = score,
                    progress = progress,
                    total = total,
                    isFavorite = isFavorite,
                    isAdult = isAdult,
                    source = manualAddSource,
                    malId = null,
                    imageUrl = null,
                    year = null,
                    synopsis = null,
                    startDate = startDate,
                    endDate = endDate,
                    notes = notes,
                    tags = tags,
                    priority = priority,
                    isRepeating = isRepeating,
                    repeatCount = repeatCount,
                    repeatValue = repeatValue,
                    volumeProgress = volumeProgress,
                    isPrivate = isPrivate,
                    isHiddenFromStatusLists = isHiddenFromStatusLists
                )

                viewModel.insertEntry(newEntry)

                showAddDialog = false
            }
        )
    }

    if (showApiSearchDialog) {
        KitsugiApiSearchDialog(
            onDismiss = {
                showApiSearchDialog = false
            },
            onResultSelected = { selection ->
                val result = selection.result
                // Always honour the result's own source instead of guessing from tab index.
                // Normalize "jikan" to "mal" since they refer to the same service.
                val resolvedSource = when (result.source.lowercase()) {
                    "jikan" -> "mal"
                    "anilist", "mal", "simkl" -> result.source.lowercase()
                    else -> result.source
                }

                if ((selectedTabIndex == MY_LIST_TMDB_TAB_INDEX || canonicalMediaSourceId(result.source) == "tmdb") &&
                    onAddTmdbApiSelectionToList != null
                ) {
                    // Use the common add path: preserve the TMDB id, merge duplicate
                    // identities and run guarded writes to every connected provider.
                    onAddTmdbApiSelectionToList(selection)
                    showApiSearchDialog = false
                    return@KitsugiApiSearchDialog
                }

                // A provider result belongs only to that provider's list, not to a cross-provider copy.
                val alreadyExists = entries.firstOrNull { entry -> entry.matchesInSource(result) }

                if (alreadyExists != null) {
                    duplicateMessage = duplicateListMessage(alreadyExists)
                    showApiSearchDialog = false
                    return@KitsugiApiSearchDialog
                }

                if (canonicalMediaSourceId(result.source) == "bangumi") {
                    if (!isBangumiConnected) {
                        showApiSearchDialog = false
                        showBangumiLoginDialog = true
                        return@KitsugiApiSearchDialog
                    }
                    val bangumiEntry = BangumiMediaEntryFactory.fromSearchResult(result, selection.synopsis)
                    if (bangumiEntry == null) {
                        duplicateMessage = "Bangumi kaydı için geçerli subject kimliği veya başlık bulunamadı."
                        showApiSearchDialog = false
                        return@KitsugiApiSearchDialog
                    }
                    editingEntry = bangumiEntry
                    isAddingBangumiEntry = true
                    showApiSearchDialog = false
                    return@KitsugiApiSearchDialog
                }

                val newEntry = MediaEntry(
                    id = 0,
                    title = result.title,
                    subtitle = result.subtitle,
                    type = result.type,
                    status = WatchStatus.Planned,
                    // Yeni kayıtlar puansız başlar; API metadata puanı kullanıcı puanı değil
                    score = null,
                    progress = 0,
                    total = result.total,
                    isFavorite = false,
                    isAdult = result.isAdult,
                    source = resolvedSource,
                    malId = result.malId,
                    imageUrl = result.imageUrl,
                    year = result.year,
                    synopsis = selection.synopsis,
                    startDate = null,
                    endDate = null
                )

                viewModel.insertEntry(newEntry)

                showApiSearchDialog = false
            }
        )
    }

    duplicateMessage?.let { message ->
        KitsugiInfoDialog(
            title = "Zaten listede",
            message = message,
            onDismiss = {
                duplicateMessage = null
            }
        )
    }

    editingEntry?.let { entry ->
        KitsugiMediaEntryEditorDialog(
            initialEntry = entry,
            source = entry.source,
            scoreFormat = appSettings.scoreFormat,
            onDismiss = {
                editingEntry = null
                isAddingBangumiEntry = false
            },
            onDeleteClick = {
                editingEntry = null
                isAddingBangumiEntry = false
                viewModel.deleteEntryById(entry.id)
            },
            onConfirm = { title, subtitle, type, status, isAdult, progress, total, score, isFavorite, startDate, endDate, notes, tags, priority, isRepeating, repeatCount, repeatValue, volumeProgress, isPrivate, isHiddenFromStatusLists, advancedScores, tmdbWatchlist, tmdbRating ->
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

                // Kullanıcı tarihleri boş bıraktıysa durum değişimine göre otomatik doldur
                val resolvedStartDate = if (startDate.isNullOrBlank() &&
                    (status == WatchStatus.Watching || status == WatchStatus.Completed) &&
                    entry.startDate.isNullOrBlank()
                ) today else startDate

                val resolvedEndDate = if (endDate.isNullOrBlank() &&
                    status == WatchStatus.Completed &&
                    entry.endDate.isNullOrBlank()
                ) today else endDate

                val updatedEntry = entry.copy(
                    title = title,
                    subtitle = if (subtitle.isBlank()) {
                        "Manuel eklenen içerik"
                    } else {
                        subtitle
                    },
                    type = type,
                    status = status,
                    isAdult = isAdult,
                    progress = progress,
                    total = total,
                    score = score,
                    isFavorite = isFavorite,
                    startDate = resolvedStartDate,
                    endDate = resolvedEndDate,
                    notes = notes,
                    tags = tags,
                    priority = priority ?: 0,
                    isRepeating = isRepeating,
                    repeatCount = repeatCount,
                    repeatValue = repeatValue,
                    volumeProgress = volumeProgress,
                    isPrivate = isPrivate,
                    isHiddenFromStatusLists = isHiddenFromStatusLists,
                    tmdbWatchlist = tmdbWatchlist,
                    tmdbRating = tmdbRating
                )

                if (canonicalMediaSourceId(updatedEntry.source) == "bangumi") {
                    viewModel.saveBangumiEntry(updatedEntry, isNew = isAddingBangumiEntry)
                } else {
                    viewModel.updateEntry(updatedEntry, advancedScores = advancedScores)
                }

                editingEntry = null
                isAddingBangumiEntry = false
            }
        )
    }

    deletingEntry?.let { entry ->
        KitsugiConfirmDialog(
            title = stringResource(R.string.kaydi_sil),
            message = stringResource(R.string.entry_title_listenizden_kalici_olarak_kaldir, entry.title),
            confirmText = "Sil",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteEntryById(entry.id)

                deletingEntry = null
            },
            onDismiss = {
                deletingEntry = null
            }
        )
    }

    if (activeZoomImageUrl != null) {
        KitsugiImagePreviewDialog(
            imageUrl = activeZoomImageUrl!!,
            title = activeZoomTitle,
            isAdult = activeZoomIsAdult,
            onDismiss = {
                activeZoomImageUrl = null
                activeZoomTitle = ""
                activeZoomIsAdult = false
            }
        )
    }

    if (showKitsuLoginDialog) {
        KitsugiKitsuLoginDialog(
            onDismiss = { showKitsuLoginDialog = false },
            onLogin = { username, password, onComplete ->
                onKitsuAuthSubmit(username, password) { success, error ->
                    onComplete(success, error)
                    if (success) {
                        onSyncKitsu()
                    }
                }
            }
        )
    }

    if (showShikimoriLoginDialog) {
        KitsugiShikimoriLoginDialog(
            onDismiss = { showShikimoriLoginDialog = false },
            onLogin = { clientId, clientSecret, authCode, onComplete ->
                onShikimoriAuthSubmit(clientId, clientSecret, authCode) { success, error ->
                    onComplete(success, error)
                    if (success) {
                        onSyncShikimori()
                    }
                }
            }
        )
    }

    if (showBangumiLoginDialog) {
        KitsugiBangumiLoginDialog(
            onDismiss = { showBangumiLoginDialog = false },
            onLogin = { clientId, clientSecret, authCode, onComplete ->
                onBangumiAuthSubmit(clientId, clientSecret, authCode) { success, error ->
                    onComplete(success, error)
                    if (success) {
                        onSyncBangumi()
                    }
                }
            }
        )
    }
}

/**
 * "Arka planda ne oluyor?" şeridi (v2.4.341).
 *
 * Kaynak başına **tek satır** gösterir: "⏳ AniList istek sınırı doldu — 3 değişiklik
 * 42 sn sonra gönderilecek", "🔑 MyAnimeList oturumu geçersiz …". Metinler
 * [com.kitsugi.animelist.data.auth.SourceSyncStatus] tarafından üretilir; burada yalnızca
 * gösterim yapılır. "Şimdi dene" tüm kaynakların kuyruğunu zorlar.
 */
@androidx.compose.runtime.Composable
private fun SyncStatusBanner(
    title: String,
    lines: List<String>,
    pending: Int,
    isError: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Bilgi satırları (kapalı / bağlı değil) kırmızı hata gibi görünmesin: v2.4.341.
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
                lines.forEach { line ->
                    androidx.compose.material3.Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (pending > 0) {
                androidx.compose.material3.TextButton(onClick = onRetry) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.sync_retry_now),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
