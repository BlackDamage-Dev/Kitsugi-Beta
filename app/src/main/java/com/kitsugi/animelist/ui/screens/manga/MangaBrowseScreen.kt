package com.kitsugi.animelist.ui.screens.manga

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSearchPlan
import com.kitsugi.animelist.data.manga.MangaSearchTitleMode
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.MangaTitleMatcher
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.app.AddonViewModel
import com.kitsugi.animelist.ui.app.MangaViewModel
import com.kitsugi.animelist.ui.components.KitsugiAddonsSettingsDialog
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiMotion
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.components.rememberKitsugiShimmerBrush
import com.kitsugi.animelist.ui.components.kitsugiCardFrame
import com.kitsugi.animelist.ui.components.kitsugiNeonGlow
import com.kitsugi.animelist.ui.components.MangaSourceAuthBanner
import com.kitsugi.animelist.ui.components.MangaSourceLoginDialog
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.screens.manga.components.MangaDiscoverChipRow
import com.kitsugi.animelist.ui.screens.manga.components.MangaDiscoverSearchPanel
import com.kitsugi.animelist.ui.screens.manga.components.MangaDiscoverSortRow
import com.kitsugi.animelist.ui.screens.manga.components.MangaDiscoverTopBar
import com.kitsugi.animelist.ui.screens.manga.components.MangaTitleModeChip
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.LocalShowAdultContent
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch

// ─── Sabitler ─────────────────────────────────────────────────────────────────
private val MangaStickyBarHeight: Dp = 57.dp

// ─── Kaynak renk paleti ───────────────────────────────────────────────────────
private val MANGA_SOURCE_TINTS = listOf(
    KitsugiColors.AccentPurple,
    KitsugiColors.AccentBlue,
    KitsugiColors.AccentTeal,
    KitsugiColors.AccentGreen,
    KitsugiColors.AccentYellow,
    KitsugiColors.AccentOrange,
    KitsugiColors.AccentPink,
    KitsugiColors.AccentIndigo,
    KitsugiColors.AccentSky
)

internal fun mangaSourceTint(key: String): Color {
    var hash = 0
    for (c in key) hash = hash * 31 + c.code
    return MANGA_SOURCE_TINTS[(hash and Int.MAX_VALUE) % MANGA_SOURCE_TINTS.size]
}

// ─── Screen ───────────────────────────────────────────────────────────────────

/**
 * Uzantı (eklenti) manga keşif ekranı.
 *
 * 2026-11-06 — Video eklenti keşif ekranı (Stream) paritesi:
 *  · Üst bar tek satır: geri + başlık + canlı özet + yardımcı ikonlar (dikey/yatay aynı).
 *  · Arama alanı + mod düğmesi + kaynak çipleri TEK kart içinde; kutucuklar ekranı kaplamaz.
 *  · "Yerel / çevrilmiş ad" düğmesi gerçekten çalışır: eserin adları (AniList +
 *    çeviri) çözümlenir ve her eklenti sırayla yerel ad → orijinal ad ile aranır.
 *  · Kaynak filtresi arama sırasında yalnızca GÖRÜNÜMÜ değiştirir; kart açıp geri
 *    dönüldüğünde "Tümü"den kendiliğinden çıkılmaz.
 */
@Composable
fun MangaBrowseScreen(
    repository: MangaSourceRepository,
    initialQuery: String? = null,
    /** Eklenti Portalı'ndan tek kaynakla açılış: bu kaynağın keşfeti önden seçilir. */
    initialSourceKey: String? = null,
    /** Animeden gelen diğer dillerdeki başlıklar (romaji / İngilizce / Japonca). */
    initialAlternates: List<String> = emptyList(),
    /**
     * "Oku" butonundan gelen, dil sırasına göre arama adayları.
     * İlk adla sonuç bulunamazsa sıradaki ad denenir.
     */
    initialQueryVariants: List<String> = emptyList(),
    /** Arama dili (ENGLISH / ROMAJI / NATIVE / LOCAL) — yedek sorgu sırasını belirler. */
    initialSearchLanguage: String? = null,
    /**
     * "Oku" akışından gelen serinin tüm adları. Dil çipine dokunulduğunda
     * sorgular bu kümeden **yeni dilde** yeniden üretilir (yoksa çip yalnız
     * etiket değiştirirdi).
     */
    initialTitleSet: com.kitsugi.animelist.data.manga.MangaTitleSet? = null,
    vm: MangaBrowseViewModel = viewModel(factory = MangaBrowseViewModel.Factory(repository, LocalContext.current.applicationContext)),
    onMangaClick: (MangaSource, MangaDetails) -> Unit = { _, _ -> },
    /**
     * Eklentiler diyaloğundaki kurulu manga kaynağından kendi **Keşfet**
     * sayfasını açar (eklenti portalı ile aynı hedef).
     */
    onExploreMangaSource: ((MangaSource) -> Unit)? = null,
    onOpenMangaLibrary: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val ui by vm.ui.collectAsState()

    val addonViewModel: AddonViewModel = viewModel()
    val mangaViewModel: MangaViewModel = viewModel()

    val addonsList by addonViewModel.addonsList.collectAsState(initial = emptyList())
    val reposList by addonViewModel.reposList.collectAsState(initial = emptyList())
    val csPluginsList by addonViewModel.csPluginsList.collectAsState(initial = emptyList())

    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSourceFilters by remember { mutableStateOf(false) }
    var loginSource by remember { mutableStateOf<MangaSource?>(null) }

    // ── GitHub Vekil Sunucu ayarı (Manga Kaynakları sekmesindeki diyalog için) ──
    val settingsDataStore = remember(context) { SettingsDataStore(context.applicationContext) }
    val appSettings by settingsDataStore.settingsFlow.collectAsState(
        initial = AppSettings()
    )
    val settingsScope = rememberCoroutineScope()

    LaunchedEffect(reposList) {
        addonViewModel.syncRepos(reposList)
    }

    // Eklenti listesi geç yüklenirse kaynak çipleri boş kalmasın: liste değişiminde
    // kaynaklar tazelenir. Seçili filtre BİLEREK korunur ("Tümü"den çıkılmaz).
    LaunchedEffect(ui.sources.isEmpty(), addonsList.size, csPluginsList.size) {
        if (ui.sources.isEmpty()) vm.refreshSources()
    }

    LaunchedEffect(addonViewModel) {
        addonViewModel.onShowMessage = { message: String -> showToast(context, message) }
    }

    LaunchedEffect(mangaViewModel) {
        mangaViewModel.onShowMessage = { message: String -> showToast(context, message) }
    }

    LaunchedEffect(initialQuery, initialQueryVariants, initialSearchLanguage, initialAlternates) {
        val effectiveVariants = initialQueryVariants.ifEmpty { initialAlternates }
        if (vm.lastInitialQuery != initialQuery || vm.lastSearchFingerprint != effectiveVariants) {
            vm.lastInitialQuery = initialQuery
            vm.lastSearchFingerprint = effectiveVariants
            if (!initialQuery.isNullOrBlank()) {
                vm.search(initialQuery, effectiveVariants)
            } else if (ui.searchQuery.isBlank()) {
                vm.search("")
            }
        }
    }

    LaunchedEffect(initialSourceKey, ui.sources) {
        val key = initialSourceKey
        if (!key.isNullOrBlank() && vm.lastInitialSourceKey != key) {
            if (vm.applyInitialSource(key)) vm.lastInitialSourceKey = key
        }
    }

    val accentColor = LocalKitsugiAccent.current
    val cols = rememberKitsugiGridColumns()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Snapshot delegated props → allow smart cast
    val query          = ui.searchQuery
    val states         = ui.sourceStates
    val selFilter      = ui.selectedSourceFilter
    val popular        = ui.popularMangas
    val hasNext        = ui.hasNextPage
    val titleMode      = ui.titleMode
    val sortMode       = ui.sortMode

    val authFailureSource = remember(states, selFilter, ui.popularErrorCode, query) {
        if (query.isBlank()) {
            selFilter?.takeIf {
                PlatformErrorCode.fromId(ui.popularErrorCode) == PlatformErrorCode.MangaAuth
            }
        } else {
            states.firstOrNull { state ->
                (selFilter == null || state.source.stableSourceKey() == selFilter.stableSourceKey()) &&
                    PlatformErrorCode.fromId(state.errorCode) == PlatformErrorCode.MangaAuth
            }?.source
        }
    }

    // Keep the actual source with each result. Display names are not identities.
    // Popüler listede her kart HANGİ kaynaktan geldiğini bilmeli; aksi hâlde "Tümü"
    // görünümünde karta dokunmak yanlış eklentiyle detay açar. Kaynak adı eşleşmezse
    // (ör. eklenti kaldırıldı) kart gizlenir.
    val sourcesByName = remember(ui.sources) { ui.sources.associateBy { it.name.lowercase() } }

    val mergedMangas = remember(states, selFilter, query, popular, sortMode, sourcesByName) {
        val raw = if (query.isNotBlank()) {
            states.filter { selFilter == null || it.source.stableSourceKey() == selFilter.stableSourceKey() }
                .flatMap { state -> state.mangas.map { state.source to it } }
                .let { pairs ->
                    when (sortMode) {
                        MangaSortMode.RELEVANCE -> pairs.sortedWith(
                            compareByDescending<Pair<MangaSource, MangaDetails>> {
                                MangaTitleMatcher.getSimilarityScore(query, it.second.title)
                            }.thenByDescending { repository.getSourcePriority(it.first) }
                        )
                        MangaSortMode.TITLE -> pairs.sortedWith(
                            compareBy<Pair<MangaSource, MangaDetails>> { it.second.title.lowercase() }
                        )
                    }
                }
        } else {
            val base = when {
                // Belirli bir kaynak seçili: hepsi o kaynağa ait.
                selFilter != null -> popular.map { selFilter to it }
                // "Tümü": popüler listesi birden çok kaynaktan birleştiği için kaynağı
                // manga.source alanından çöz.
                else -> popular.mapNotNull { manga ->
                    sourcesByName[manga.source.lowercase()]?.let { source -> source to manga }
                }
            }
            when (sortMode) {
                MangaSortMode.RELEVANCE -> base
                MangaSortMode.TITLE -> base.sortedWith(
                    compareBy<Pair<MangaSource, MangaDetails>> { it.second.title.lowercase() }
                )
            }
        }
        raw.distinctBy { "${it.first.stableSourceKey()}|${it.second.url}" }
    }

    // +18 UYUMU: uzantı gözat/arama sonuçları da göster/gizle sözleşmesine uyar.
    val showAdultContent = LocalShowAdultContent.current
    val visibleMangas = remember(mergedMangas, showAdultContent) {
        if (showAdultContent) mergedMangas else mergedMangas.filter { !it.second.isAdultContent }
    }

    val backdropUrl = visibleMangas.firstOrNull()?.second?.thumbnailUrl

    // ── Özet satırı (tek doğru kaynak) ────────────────────────────────────────
    val summaryText = when {
        query.isBlank() -> stringResource(
            R.string.manga_discover_popular_summary,
            popular.size,
            selFilter?.lang?.uppercase() ?: stringResource(R.string.tumu)
        )
        ui.isResolvingTitles -> stringResource(R.string.manga_discover_resolving_titles)
        else -> stringResource(
            R.string.manga_discover_summary,
            visibleMangas.size,
            states.count { it.mangas.isNotEmpty() },
            maxOf(ui.triedTitleCount, 1)
        )
    }

    val usingTitle = remember(ui.titlePlan, titleMode) {
        ui.titlePlan?.trimmedForDisplay()
    }
    var showSourcePicker by remember { mutableStateOf(false) }

    val sourcesEmpty = ui.sources.isEmpty() && states.isEmpty() && !ui.isLoadingPopular
    val isSearchMode = query.isNotBlank()
    val loading = if (isSearchMode) states.any { it.isLoading } else ui.isLoadingPopular
    val hasNextPageToShow = if (!isSearchMode) hasNext
        else (selFilter != null && (states.firstOrNull { it.source.stableSourceKey() == selFilter.stableSourceKey() }?.hasNextPage == true))

    // ── Katlanan başlık durumu (Stream ekranındaki HideOnScrollConnection ile aynı) ──
    val density = LocalDensity.current
    val gridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }
    val headerOffsetPx = remember { mutableFloatStateOf(0f) }
    val initialCollapsiblePx = with(density) { 188.dp.toPx() }
    val collapsibleHeightPx = remember { mutableFloatStateOf(initialCollapsiblePx) }
    val stickyBarPx = with(density) { MangaStickyBarHeight.toPx() }
    val nestedScrollConnection = remember(gridState) {
        MangaHideOnScrollConnection(headerOffsetPx, collapsibleHeightPx, gridState)
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 4 }
            .collect { atTop -> if (atTop) headerOffsetPx.floatValue = 0f }
    }
    val collapsed by remember {
        derivedStateOf {
            collapsibleHeightPx.floatValue > 0f &&
                headerOffsetPx.floatValue >= collapsibleHeightPx.floatValue * 0.6f
        }
    }

    val revealHeader: () -> Unit = {
        settingsScope.launch {
            gridState.animateScrollToItem(0)
            headerOffsetPx.floatValue = 0f
        }
    }
    val changeSearchName: () -> Unit = {
        settingsScope.launch {
            gridState.animateScrollToItem(0)
            headerOffsetPx.floatValue = 0f
            searchFocusRequester.requestFocus()
        }
    }
    val topContentPadding = with(density) {
        (collapsibleHeightPx.floatValue + stickyBarPx).toDp()
    } + 10.dp

    val bg = KitsugiColors.Background

    Box(Modifier.fillMaxSize().background(bg)) {

        // ── Backdrop: ilk sonucun kapağı + tema vurgulu aurora ──
        if (!backdropUrl.isNullOrBlank()) {
            KitsugiNsfwImage(
                model = backdropUrl, contentDescription = null, contentScale = ContentScale.Crop,
                isAdult = visibleMangas.firstOrNull()?.second?.isAdultContent == true,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.10f }
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to accentColor.copy(alpha = 0.10f),
                    0.34f to bg.copy(alpha = 0.92f),
                    1f to bg
                )
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            if (sourcesEmpty) {
                MangaDiscoverTopBar(
                    summary = summaryText,
                    accent = accentColor,
                    onBack = onBack,
                    onShowInfo = { showInfoDialog = true },
                    onOpenSettings = { showSettingsDialog = true },
                    onOpenLibrary = onOpenMangaLibrary,
                    isResolving = ui.isResolvingTitles
                )
                MangaDiscoverSearchPanel(
                    query = query,
                    onQueryChange = { vm.search(it) },
                    mode = titleMode,
                    onToggleMode = {
                        vm.setTitleMode(
                            if (titleMode == MangaSearchTitleMode.LOCALIZED) MangaSearchTitleMode.ORIGINAL
                            else MangaSearchTitleMode.LOCALIZED
                        )
                    },
                    accent = accentColor,
                    enabled = query.isNotBlank(),
                    onShowInfo = { showInfoDialog = true },
                    onChangeSearchName = changeSearchName,
                    focusRequester = searchFocusRequester,
                    compact = isLandscape,
                    chips = null
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptySourcesHint(onOpenSettings = { showSettingsDialog = true })
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    // 1) Izgara — daima tek bir kaydırılabilir liste (kısa liste koruması için).
                    KitsugiShimmerProvider(active = loading) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            state = gridState,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = topContentPadding,
                                bottom = 24.dp
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when {
                                loading && mergedMangas.isEmpty() -> {
                                    items(if (isLandscape) cols * 2 else 8) {
                                        MangaCardShimmer(compact = isLandscape)
                                    }
                                }
                                !loading && visibleMangas.isEmpty() -> {
                                    item(span = { GridItemSpan(cols) }) {
                                        Box(
                                            Modifier.fillMaxWidth().height(240.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(horizontal = 24.dp)
                                            ) {
                                                KitsugiEmptyState(
                                                    title = stringResource(R.string.sonuc_bulunamadi),
                                                    subtitle = stringResource(R.string.manga_browse_ui_empty_subtitle),
                                                    icon = Icons.Rounded.SearchOff,
                                                    modifier = Modifier.padding(horizontal = 16.dp)
                                                )
                                                if (query.isNotBlank()) {
                                                    MangaTitleModeChip(
                                                        mode = if (titleMode == MangaSearchTitleMode.LOCALIZED)
                                                            MangaSearchTitleMode.ORIGINAL else MangaSearchTitleMode.LOCALIZED,
                                                        accent = accentColor,
                                                        enabled = true,
                                                        onClick = {
                                                            vm.setTitleMode(
                                                                if (titleMode == MangaSearchTitleMode.LOCALIZED)
                                                                    MangaSearchTitleMode.ORIGINAL else MangaSearchTitleMode.LOCALIZED
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    // 1) ÖNCE başarıyla veri gelen kaynakların mangaları (en üstte).
                                    items(visibleMangas, key = { "${it.first.stableSourceKey()}|${it.second.url}" }) { (source, manga) ->
                                        MangaCard(
                                            manga = manga,
                                            sourceName = source.name,
                                            sourceTint = mangaSourceTint(source.stableSourceKey()),
                                            compact = isLandscape
                                        ) { onMangaClick(source, manga) }
                                    }
                                    // 2) SONRA hâlâ yüklenen kaynakların iskelet kartları — EN ALTTA.
                                    if (query.isNotBlank()) {
                                        val loadingStates = states.filter {
                                            it.isLoading && (selFilter == null || selFilter.stableSourceKey() == it.source.stableSourceKey())
                                        }
                                        items(loadingStates, key = { "sk_${it.source.stableSourceKey()}" }) {
                                            SkeletonCard(
                                                sourceName = it.source.name,
                                                sourceTint = mangaSourceTint(it.source.stableSourceKey()),
                                                compact = isLandscape
                                            )
                                        }
                                    }
                                    if (query.isBlank() || selFilter != null) {
                                        item(span = { GridItemSpan(cols) }) {
                                            LaunchedEffect(mergedMangas.size) { vm.loadNextPage() }
                                            if (hasNextPageToShow) {
                                                Box(Modifier.fillMaxWidth().height(64.dp), Alignment.Center) {
                                                    KitsugiPlasmaLoader(size = 32.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2) Üst overlay: [katlanan kısım] + [yapışkan kaynak çubuğu].
                    val headerTop = bg.copy(alpha = 0.88f)
                    val headerBottom = bg.copy(alpha = 0.97f)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationY = -headerOffsetPx.floatValue }
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged { collapsibleHeightPx.floatValue = it.height.toFloat() }
                                .background(Brush.verticalGradient(listOf(headerTop, headerBottom)))
                                .graphicsLayer {
                                    val max = collapsibleHeightPx.floatValue.coerceAtLeast(1f)
                                    val progress = (headerOffsetPx.floatValue / max).coerceIn(0f, 1f)
                                    alpha = 1f - progress * 0.85f
                                }
                        ) {
                            MangaDiscoverTopBar(
                                summary = summaryText,
                                accent = accentColor,
                                onBack = onBack,
                                onShowInfo = { showInfoDialog = true },
                                onOpenSettings = { showSettingsDialog = true },
                                onOpenLibrary = onOpenMangaLibrary,
                                isResolving = ui.isResolvingTitles
                            )
                            MangaDiscoverSearchPanel(
                                query = query,
                                onQueryChange = { vm.search(it) },
                                mode = titleMode,
                                onToggleMode = {
                                    vm.setTitleMode(
                                        if (titleMode == MangaSearchTitleMode.LOCALIZED) MangaSearchTitleMode.ORIGINAL
                                        else MangaSearchTitleMode.LOCALIZED
                                    )
                                },
                                accent = accentColor,
                                enabled = query.isNotBlank(),
                                onShowInfo = { showInfoDialog = true },
                                onChangeSearchName = changeSearchName,
                                focusRequester = searchFocusRequester,
                                compact = isLandscape,
                                chips = null
                            )
                            Spacer(Modifier.height(2.dp))
                            MangaDiscoverSortRow(
                                relevanceLabel = stringResource(R.string.manga_discover_sort_relevance),
                                titleLabel = stringResource(R.string.manga_discover_sort_title),
                                isRelevance = sortMode == MangaSortMode.RELEVANCE,
                                accent = accentColor,
                                label = stringResource(R.string.manga_discover_sort_label),
                                onSelectRelevance = { vm.setSortMode(MangaSortMode.RELEVANCE) },
                                onSelectTitle = { vm.setSortMode(MangaSortMode.TITLE) },
                                trailing = {
                                    if (query.isNotBlank() && !usingTitle.isNullOrBlank()) {
                                        Text(
                                            text = stringResource(R.string.manga_discover_using_title, usingTitle),
                                            color = KitsugiColors.TextMuted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = if (isLandscape) 260.dp else 150.dp)
                                        )
                                    }
                                }
                            )
                            authFailureSource?.let { source ->
                                MangaSourceAuthBanner(
                                    source = source,
                                    onLogin = { loginSource = source },
                                    onRetry = { vm.retrySource(source) },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        MangaStickySourceBar(
                            states = states,
                            sources = ui.sources,
                            isSearchMode = isSearchMode,
                            popular = popular,
                            isLoadingPopular = ui.isLoadingPopular,
                            selFilter = selFilter,
                            accent = accentColor,
                            collapsed = collapsed,
                            onSelect = { vm.selectSourceFilter(it) },
                            onOpenPicker = { showSourcePicker = true },
                            onRevealHeader = revealHeader
                        )
                    }
                }
            }
        }
    }

    if (showSourcePicker) {
        MangaSourcePickerSheet(
            states = states,
            sources = ui.sources,
            isSearchMode = isSearchMode,
            popular = popular,
            summaryText = summaryText,
            selectedKey = selFilter?.stableSourceKey(),
            accent = accentColor,
            onSelect = { source ->
                vm.selectSourceFilter(source)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showInfoDialog) {
        MangaSearchInfoDialog(
            mode = titleMode,
            plan = ui.titlePlan,
            triedTitleCount = ui.triedTitleCount,
            stateUsedTitles = states.mapNotNull { st -> st.usedTitle?.let { st.source.name to it } },
            onDismiss = { showInfoDialog = false }
        )
    }

    loginSource?.let { source ->
        MangaSourceLoginDialog(
            source = source,
            onSessionUpdated = {
                loginSource = null
                vm.retrySource(source)
            },
            onDismiss = { loginSource = null }
        )
    }

    if (showSettingsDialog) {
        KitsugiAddonsSettingsDialog(
            addons = addonsList,
            initialDebridToken = addonViewModel.debridToken,
            repos = reposList,
            repoPlugins = addonViewModel.repoPluginsState,
            repoLoadingState = addonViewModel.repoLoadingState,
            csPlugins = csPluginsList,
            initialTab = 2,
            onAddAddon = { addonViewModel.addAddon(it) },
            onToggleAddon = { addon, enabled -> addonViewModel.toggleAddon(addon, enabled) },
            onDeleteAddon = { addonViewModel.deleteAddon(it) },
            onSaveDebridToken = { addonViewModel.saveDebridToken(it) },
            onAddRepo = { addonViewModel.addRepo(it) },
            onDeleteRepo = { addonViewModel.deleteRepo(it) },
            onFetchRepoPlugins = { addonViewModel.fetchRepoPlugins(it) },
            onInstallPlugin = { plugin, onResult -> addonViewModel.installPlugin(plugin, onResult) },
            onInstallAllPlugins = { repoUrl, repoName, plugins ->
                addonViewModel.installAllPlugins(repoUrl, repoName, plugins, addonsList, csPluginsList)
            },
            onUpdateAllPlugins = { repoUrl, repoName, plugins ->
                addonViewModel.updateAllPlugins(repoUrl, repoName, plugins, csPluginsList)
            },
            bulkInstallRepoUrl = addonViewModel.bulkInstallRepoUrl,
            bulkInstallRepoName = addonViewModel.bulkInstallRepoName,
            bulkInstallDone = addonViewModel.bulkInstallDone,
            bulkInstallTotal = addonViewModel.bulkInstallTotal,
            bulkInstallCurrentName = addonViewModel.bulkInstallCurrentName,
            bulkInstallResultMessage = addonViewModel.bulkInstallResultMessage,
            onClearBulkInstallResult = { addonViewModel.clearBulkInstallResult() },
            onToggleCsPlugin = { plugin, enabled -> addonViewModel.toggleCsPlugin(plugin, enabled) },
            onUninstallCsPlugin = { addonViewModel.uninstallCsPlugin(it) },
            onUninstallAllCsPlugins = { addonViewModel.uninstallAllCsPlugins() },
            useGithubProxy = appSettings.useGithubProxy,
            onUseGithubProxyChanged = { enabled ->
                settingsScope.launch { settingsDataStore.setUseGithubProxy(enabled) }
            },
            onExploreMangaSource = { exploreSource ->
                // Bu diyalog gerçek bir Dialog katmanı; keşfet sayfası altta kalmasın
                // diye önce kapatılır, sonra hedef ekran itilir.
                showSettingsDialog = false
                onExploreMangaSource?.invoke(exploreSource)
            },
            mangaSources = mangaViewModel.mangaSources,
            onInstallMangaExtension = { mangaViewModel.installMangaExtension(it) },
            onDeleteMangaExtension = { mangaViewModel.deleteMangaExtension(it) },
            onGetMangaSourceEnabled = { mangaViewModel.isMangaSourceEnabled(it) },
            onToggleMangaSource = { source, enabled -> mangaViewModel.toggleMangaSource(source, enabled) },
            mangaBulkDelete = com.kitsugi.animelist.ui.components.addons.MangaBulkDeleteState(
                isDeleting = mangaViewModel.mangaIsBulkDeleting,
                hiddenBuiltInCount = mangaViewModel.mangaBulkDeleteHiddenBuiltInCount,
                onDeleteAll = { mangaViewModel.deleteAllMangaExtensions() },
                onRestoreBuiltIns = { mangaViewModel.restoreBulkDeletedMangaBuiltIns() }
            ),
            mangaRepos = mangaViewModel.mangaReposState,
            mangaRepoExtensions = mangaViewModel.mangaRepoExtensionsState,
            mangaRepoLoadingState = mangaViewModel.mangaRepoLoadingState,
            onAddMangaRepo = { mangaViewModel.addMangaRepo(it) },
            onDeleteMangaRepo = { mangaViewModel.deleteMangaRepo(it) },
            onFetchMangaRepo = { mangaViewModel.fetchMangaRepo(it) },
            onInstallMangaApk = { extension, onResult -> mangaViewModel.installMangaApk(extension, onResult) },
            onInstallAllMangaExtensions = { repoUrl, extensions -> mangaViewModel.installAllMangaExtensions(repoUrl, extensions) },
            onUpdateAllMangaExtensions = { repoUrl, extensions -> mangaViewModel.updateAllMangaExtensions(repoUrl, extensions) },
            mangaBulkInstallRepoUrl = mangaViewModel.mangaBulkInstallRepoUrl,
            mangaBulkInstallDone = mangaViewModel.mangaBulkInstallDone,
            mangaBulkInstallTotal = mangaViewModel.mangaBulkInstallTotal,
            mangaBulkInstallCurrentName = mangaViewModel.mangaBulkInstallCurrentName,
            onGetInstalledMangaVersionCode = { mangaViewModel.getInstalledVersionCode(it) },
            onGetInstalledMangaVersion = { mangaViewModel.getInstalledMangaExtensionVersion(it) },
            mangaSourceStateReport = mangaViewModel.mangaSourceStateReport,
            onGetMangaSourceHealthStatus = { mangaViewModel.getSourceHealthStatus(it) },
            onGetMangaSourceRuntimeStats = { mangaViewModel.getSourceRuntimeStats(it) },
            onGetMangaConfiguredDomain = { mangaViewModel.getConfiguredSourceDomain(it) },
            onGetMangaConfiguredBaseUrl = { mangaViewModel.getConfiguredSourceBaseUrl(it) },
            onGetMangaSourceUserAgent = { mangaViewModel.getSourceUserAgentOverride(it) },
            onGetMangaSourceSlowdownEnabled = { mangaViewModel.getSourceSlowdownEnabled(it) },
            onSetMangaSourceUserAgent = { source, ua -> mangaViewModel.setSourceUserAgentOverride(source, ua) },
            onSetMangaSourceSlowdownEnabled = { source, enabled -> mangaViewModel.setSourceSlowdownEnabled(source, enabled) },
            onSetMangaSourceDomain = { source, domain -> mangaViewModel.setSourceDomainOverride(source, domain) },
            onResetMangaSourceDiagnostics = { mangaViewModel.resetSourceDiagnostics(it) },
            onClearAllMangaSourceDiagnostics = { mangaViewModel.clearAllSourceDiagnostics() },
            onIsMangaSourceBusy = { mangaViewModel.isSourceBusy(it) },
            onQuickCheckMangaSource = { mangaViewModel.quickCheckSource(it) },
            onRefreshMangaSourceMirror = { mangaViewModel.refreshSourceMirror(it) },
            onClearMangaSourceMirror = { mangaViewModel.clearSourceMirror(it) },
            onForceCheckMangaUpdates = { mangaViewModel.forceCheckMangaUpdates(it) },
            untrustedRepoToConfirm = mangaViewModel.untrustedRepoToConfirm,
            untrustedSignatureToConfirm = mangaViewModel.untrustedSignatureToConfirm,
            onConfirmUntrustedRepo = { mangaViewModel.addMangaRepo(it, force = true) },
            onDismissUntrustedRepo = { mangaViewModel.clearUntrustedRepoConfirm() },
            onConfirmUntrustedSignature = { ext, hash -> mangaViewModel.installMangaApk(ext, force = true) },
            onDismissUntrustedSignature = { mangaViewModel.clearUntrustedSignatureConfirm() },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

// ─── Katlanan başlık: nested scroll bağlantısı ────────────────────────────────

private class MangaHideOnScrollConnection(
    private val offset: MutableFloatState,
    private val maxOffset: MutableFloatState,
    private val gridState: LazyGridState
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        val max = maxOffset.floatValue
        if (dy == 0f || max <= 0f) return Offset.Zero
        if (dy < 0f && !gridState.canScrollForward) return Offset.Zero
        val target = (offset.floatValue - dy).coerceIn(0f, max)
        val delta = target - offset.floatValue
        if (delta == 0f) return Offset.Zero
        offset.floatValue = target
        return Offset(0f, -delta)
    }
}

// ─── Yapışkan kaynak çubuğu (çipler + açılır buton) ──────────────────────────

@Composable
private fun MangaStickySourceBar(
    states: List<MangaSourceFetchState>,
    sources: List<MangaSource>,
    isSearchMode: Boolean,
    popular: List<MangaDetails>,
    isLoadingPopular: Boolean,
    selFilter: MangaSource?,
    accent: Color,
    collapsed: Boolean,
    onSelect: (MangaSource?) -> Unit,
    onOpenPicker: () -> Unit,
    onRevealHeader: () -> Unit
) {
    val bg = KitsugiColors.Background
    val stickyAlpha by animateFloatAsState(
        targetValue = if (collapsed) 0.985f else 0.92f,
        animationSpec = tween(KitsugiMotion.normalMillis),
        label = "manga_sticky_alpha"
    )
    val stickyElevation by animateDpAsState(
        targetValue = if (collapsed) 6.dp else 0.dp,
        animationSpec = tween(KitsugiMotion.normalMillis),
        label = "manga_sticky_elevation"
    )

    Surface(color = Color.Transparent, shadowElevation = stickyElevation) {
        Column(Modifier.fillMaxWidth().background(bg.copy(alpha = stickyAlpha))) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = collapsed,
                    enter = fadeIn(tween(KitsugiMotion.stickyHeaderEnterMillis)) +
                        scaleIn(tween(KitsugiMotion.stickyHeaderEnterMillis), initialScale = 0.7f),
                    exit = fadeOut(tween(KitsugiMotion.stickyHeaderExitMillis)) +
                        scaleOut(tween(KitsugiMotion.stickyHeaderExitMillis), targetScale = 0.7f)
                ) {
                    IconButton(onClick = onRevealHeader, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Search,
                            stringResource(R.string.manga_browse_back_to_search),
                            tint = accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                MangaDiscoverChipRow(modifier = Modifier.weight(1f)) {
                    if (isSearchMode) {
                        val totalCount = states.sumOf { it.mangas.size }
                        val anyLoading = states.any { it.isLoading }
                        item(key = "all_chip") {
                            MangaChip(
                                label = stringResource(R.string.tumu),
                                count = totalCount,
                                isLoading = anyLoading,
                                error = null,
                                isSelected = selFilter == null,
                                accent = accent,
                                isAll = true,
                                onClick = { onSelect(null) }
                            )
                        }
                        items(states, key = { it.source.stableSourceKey() }) { st ->
                            MangaChip(
                                label = st.source.name,
                                count = st.mangas.size,
                                isLoading = st.isLoading,
                                error = st.error,
                                errorCode = st.errorCode,
                                isSelected = selFilter?.stableSourceKey() == st.source.stableSourceKey(),
                                accent = accent,
                                isAll = false,
                                sourceTint = mangaSourceTint(st.source.stableSourceKey()),
                                onClick = {
                                    onSelect(
                                        if (selFilter?.stableSourceKey() == st.source.stableSourceKey()) null
                                        else st.source
                                    )
                                }
                            )
                        }
                    } else {
                        item(key = "all_chip") {
                            MangaChip(
                                label = stringResource(R.string.tumu),
                                count = popular.size,
                                isLoading = isLoadingPopular && selFilter == null,
                                error = null,
                                isSelected = selFilter == null,
                                accent = accent,
                                isAll = true,
                                onClick = { onSelect(null) }
                            )
                        }
                        items(sources, key = { it.stableSourceKey() }) { src ->
                            val sourcePopularCount = popular.count { it.source.equals(src.name, ignoreCase = true) }
                            MangaChip(
                                label = "${src.name}  [${src.lang.uppercase()}]",
                                count = sourcePopularCount,
                                isLoading = selFilter?.stableSourceKey() == src.stableSourceKey() && isLoadingPopular,
                                error = null,
                                isSelected = selFilter?.stableSourceKey() == src.stableSourceKey(),
                                accent = accent,
                                isAll = false,
                                sourceTint = mangaSourceTint(src.stableSourceKey()),
                                onClick = { onSelect(src) }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                Surface(
                    onClick = onOpenPicker,
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            stringResource(R.string.manga_browse_show_all_sources),
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f))
        }
    }
}

// ─── Kaynak seçici bottom sheet ───────────────────────────────────────────────

private data class MangaSourcePickerItem(
    val source: MangaSource,
    val count: Int,
    val isLoading: Boolean,
    val error: String?,
    val errorCode: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaSourcePickerSheet(
    states: List<MangaSourceFetchState>,
    sources: List<MangaSource>,
    isSearchMode: Boolean,
    popular: List<MangaDetails>,
    summaryText: String,
    selectedKey: String?,
    accent: Color,
    onSelect: (MangaSource?) -> Unit,
    onDismiss: () -> Unit
) {
    var pickerQuery by remember { mutableStateOf("") }
    val entries = remember(states, sources, isSearchMode, popular, selectedKey) {
        if (isSearchMode) {
            states.map {
                MangaSourcePickerItem(
                    source = it.source,
                    count = it.mangas.size,
                    isLoading = it.isLoading,
                    error = it.error,
                    errorCode = it.errorCode
                )
            }
        } else {
            sources.map { src ->
                val sourceCount = popular.count { it.source.equals(src.name, ignoreCase = true) }
                MangaSourcePickerItem(
                    source = src,
                    count = if (selectedKey == src.stableSourceKey()) popular.size else sourceCount,
                    isLoading = false,
                    error = null,
                    errorCode = null
                )
            }
        }
    }
    val visible = remember(entries, pickerQuery) {
        entries.filter { it.source.name.contains(pickerQuery.trim(), ignoreCase = true) }
    }
    val allCount = if (isSearchMode) states.sumOf { it.mangas.size } else popular.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Background
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.manga_browse_sources_title),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                summaryText,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pickerQuery,
                onValueChange = { pickerQuery = it },
                singleLine = true,
                placeholder = {
                    Text(stringResource(R.string.manga_browse_search_sources), color = KitsugiColors.TextMuted)
                },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = accent) },
                trailingIcon = {
                    if (pickerQuery.isNotEmpty()) {
                        IconButton(onClick = { pickerQuery = "" }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.temizle))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = KitsugiColors.TextPrimary,
                    unfocusedTextColor = KitsugiColors.TextPrimary,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = KitsugiColors.Border.copy(alpha = 0.5f)
                )
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item(key = "all") {
                    MangaSourcePickerRow(
                        name = stringResource(R.string.tumu),
                        status = stringResource(R.string.manga_browse_result_count, allCount),
                        count = allCount,
                        selected = selectedKey == null,
                        dotColor = accent,
                        accent = accent,
                        onClick = { onSelect(null) }
                    )
                }
                items(visible, key = { it.source.stableSourceKey() }) { entry ->
                    val status = when {
                        entry.isLoading -> stringResource(R.string.araniyor)
                        entry.error != null -> stringResource(R.string.manga_browse_source_error) +
                            (entry.errorCode?.let { " · $it" } ?: "")
                        else -> stringResource(R.string.manga_browse_result_count, entry.count)
                    }
                    MangaSourcePickerRow(
                        name = entry.source.name,
                        status = status,
                        count = entry.count,
                        selected = selectedKey == entry.source.stableSourceKey(),
                        dotColor = mangaSourceTint(entry.source.stableSourceKey()),
                        accent = accent,
                        onClick = { onSelect(entry.source) }
                    )
                }
                if (visible.isEmpty()) {
                    item(key = "no_match") {
                        Text(
                            stringResource(R.string.manga_browse_no_source_match),
                            modifier = Modifier.padding(16.dp),
                            color = KitsugiColors.TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MangaSourcePickerRow(
    name: String,
    status: String,
    count: Int,
    selected: Boolean,
    dotColor: Color,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    status,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (count > 0) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$count", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
            if (selected) {
                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Kaynak çipi ─────────────────────────────────────────────────────────────

/**
 * Uygulamanın ortak çip dili: 16 dp köşe, `Surface` zemin, seçiliyken vurgu
 * kenarlığı, durum noktası ve ayrı sayı/hata kodu rozeti.
 */
@Composable
fun MangaChip(
    label: String, count: Int, isLoading: Boolean, error: String?,
    isSelected: Boolean, accent: Color, isAll: Boolean, onClick: () -> Unit,
    errorCode: String? = null,
    sourceTint: Color = accent
) {
    val shape = RoundedCornerShape(16.dp)
    val errorTint = KitsugiColors.AccentRed
    val statusTint = when {
        error != null -> errorTint
        isSelected -> accent
        count > 0 -> sourceTint
        else -> KitsugiColors.TextMuted
    }
    val chipBackground = when {
        isSelected -> accent.copy(alpha = 0.16f)
        error != null -> errorTint.copy(alpha = 0.10f)
        else -> KitsugiColors.Surface.copy(alpha = 0.94f)
    }
    val outline = when {
        isSelected -> accent.copy(alpha = 0.55f)
        error != null -> errorTint.copy(alpha = 0.38f)
        else -> KitsugiColors.Border
    }

    Row(
        Modifier
            .clip(shape)
            .background(chipBackground, shape)
            .border(1.dp, outline, shape)
            .tvClickable(shape = shape) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        when {
            isLoading -> androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(11.dp), color = accent, strokeWidth = 1.5.dp
            )
            isAll -> Icon(
                Icons.Rounded.Extension,
                contentDescription = null,
                tint = if (isSelected) accent else KitsugiColors.TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            else -> Box(
                Modifier.size(7.dp).clip(CircleShape).background(statusTint.copy(alpha = 0.9f))
            )
        }
        Text(
            label,
            color = if (isSelected) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 132.dp)
        )
        if (count > 0 && !isAll) {
            MangaChipBadge(
                text = count.toString(),
                tint = statusTint,
                chipBackground = statusTint.copy(alpha = 0.16f)
            )
        }
        if (error != null && errorCode != null) {
            MangaChipBadge(
                text = errorCode,
                tint = errorTint,
                chipBackground = errorTint.copy(alpha = 0.14f),
                outlined = true
            )
        }
    }
}

@Composable
private fun MangaChipBadge(
    text: String,
    tint: Color,
    chipBackground: Color,
    outlined: Boolean = false
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .clip(shape)
            .background(chipBackground, shape)
            .then(
                if (outlined) Modifier.border(1.dp, tint.copy(alpha = 0.35f), shape)
                else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ─── Kaynak rozeti (kart / iskelet kart) ─────────────────────────────────────

@Composable
private fun MangaSourceBadge(
    sourceName: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .widthIn(max = 150.dp)
            .clip(shape)
            .background(KitsugiColors.Scrim.copy(alpha = 0.82f), shape)
            .border(1.dp, tint.copy(alpha = 0.5f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            Icons.Rounded.Extension,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(11.dp)
        )
        Text(
            sourceName,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MangaSourceRow(sourceName: String, sourceTint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(sourceTint))
        Spacer(Modifier.width(6.dp))
        Text(
            sourceName,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Manga sonuç kartı ───────────────────────────────────────────────────────

/**
 * Uygulamanın medya kartı dili: 2:3 poster, 20 dp yuvarlak köşe,
 * `kitsugiNeonGlow` çerçevesi, tema `Surface` zemini, kaynak rozeti ve
 * kartın altında sabit yükseklikte başlık + kaynak satırı.
 */
@Composable
private fun MangaCard(
    manga: MangaDetails,
    sourceName: String,
    sourceTint: Color,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        Modifier
            .fillMaxWidth()
            .kitsugiNeonGlow(shape)
            .tvClickable(shape = shape) { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        if (compact) {
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val rowHeight = when {
                screenWidthDp >= 1200 -> 160.dp
                screenWidthDp >= 800 -> 145.dp
                else -> 130.dp
            }
            val posterWidth = rowHeight * 0.62f
            Row(
                Modifier.fillMaxWidth().height(rowHeight).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(posterWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceSoft, RoundedCornerShape(14.dp))
                ) {
                    KitsugiNsfwImage(
                        model = manga.thumbnailUrl,
                        contentDescription = manga.title,
                        isAdult = manga.isAdultContent,
                        contentScale = ContentScale.Crop,
                        initials = manga.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        manga.title,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    MangaSourceRow(sourceName, sourceTint)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(sourceTint.copy(alpha = 0.8f))
                    )
                }
            }
        } else {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(shape)
                        .background(KitsugiColors.SurfaceSoft, shape),
                    contentAlignment = Alignment.Center
                ) {
                    KitsugiNsfwImage(
                        model = manga.thumbnailUrl,
                        contentDescription = manga.title,
                        isAdult = manga.isAdultContent,
                        contentScale = ContentScale.Crop,
                        initials = manga.title,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, KitsugiColors.Scrim.copy(alpha = 0.55f))
                                )
                            )
                    )
                    MangaSourceBadge(
                        sourceName = sourceName,
                        tint = sourceTint,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        manga.title,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    MangaSourceRow(sourceName, sourceTint)
                }
            }
        }
    }
}

// ─── İskelet kartlar ─────────────────────────────────────────────────────────

@Composable
private fun SkeletonCard(sourceName: String, sourceTint: Color, compact: Boolean = false) {
    MangaCardPlaceholder(sourceName = sourceName, sourceTint = sourceTint, compact = compact)
}

@Composable
private fun MangaCardShimmer(compact: Boolean = false) {
    MangaCardPlaceholder(compact = compact)
}

@Composable
private fun MangaCardPlaceholder(
    sourceName: String? = null,
    sourceTint: Color = KitsugiColors.AccentIndigo,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(20.dp)
    val posterShape = RoundedCornerShape(14.dp)
    val fallbackBrush = rememberKitsugiShimmerBrush()
    val brush = LocalShimmerBrush.current ?: fallbackBrush
    Card(
        Modifier.fillMaxWidth().kitsugiNeonGlow(shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        if (compact) {
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val rowHeight = when {
                screenWidthDp >= 1200 -> 160.dp
                screenWidthDp >= 800 -> 145.dp
                else -> 130.dp
            }
            Row(
                Modifier.fillMaxWidth().height(rowHeight).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.width(rowHeight * 0.62f).fillMaxHeight().clip(posterShape),
                    cornerRadius = 14.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.9f).height(13.dp),
                        cornerRadius = 999.dp
                    )
                    Spacer(Modifier.height(9.dp))
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.6f).height(10.dp),
                        cornerRadius = 999.dp
                    )
                    if (sourceName != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.araniyor),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxSize().clip(shape),
                        cornerRadius = 20.dp
                    )
                    if (sourceName != null) {
                        MangaSourceBadge(
                            sourceName = sourceName,
                            tint = sourceTint,
                            modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        )
                        Text(
                            stringResource(R.string.araniyor),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.85f).height(12.dp),
                        cornerRadius = 999.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                        cornerRadius = 999.dp
                    )
                }
            }
        }
    }
}

// ─── Kaynak yok durumunda premium boş kart ───────────────────────────────────

@Composable
private fun EmptySourcesHint(onOpenSettings: () -> Unit) {
    val accentBrush = LocalKitsugiAccentBrush.current
    val onAccent = LocalKitsugiOnAccent.current
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(KitsugiColors.Surface, shape)
                .kitsugiCardFrame(shape)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accentBrush, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Extension,
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                stringResource(R.string.inline_no_manga_extensions_have_been_e92d6be),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                stringResource(R.string.ui2_manga_builtin_sources),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            KitsugiButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.inline_edit_extensions_134b588), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Arama bilgisi diyaloğu ───────────────────────────────────────────────────

/**
 * "Arama: Yerel / Orijinal ad" düğmesinin ne yaptığını ve o sorguda hangi adların
 * denendiğini gösterir. Kullanıcı butonun gerçekten çalıştığını buradan doğrular.
 */
@Composable
private fun MangaSearchInfoDialog(
    mode: MangaSearchTitleMode,
    plan: MangaSearchPlan?,
    triedTitleCount: Int,
    stateUsedTitles: List<Pair<String, String>>,
    onDismiss: () -> Unit
) {
    val accent = LocalKitsugiAccent.current
    val modeHint = when (mode) {
        MangaSearchTitleMode.LOCALIZED -> stringResource(R.string.manga_discover_mode_localized_hint)
        MangaSearchTitleMode.ORIGINAL  -> stringResource(R.string.manga_discover_mode_original_hint)
    }
    val variants = plan?.safeVariants().orEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Translate, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.manga_discover_more_info),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = modeHint,
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp
                )
                if (variants.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.manga_discover_tried_titles),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        variants.forEachIndexed { index, variant ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (index == 0) accent else KitsugiColors.TextMuted.copy(0.6f))
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = variant,
                                    color = if (index == 0) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.manga_discover_tried_count, triedTitleCount),
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp
                    )
                }
                if (stateUsedTitles.isNotEmpty()) {
                    HorizontalDivider(color = KitsugiColors.SurfaceStrong.copy(0.5f))
                    stateUsedTitles.take(6).forEach { (sourceName, usedTitle) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                sourceName,
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                usedTitle,
                                color = KitsugiColors.TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_ok), color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Toast yardımcısı ────────────────────────────────────────────────────────

private fun showToast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

/** Render extension-supplied labels and return indices, not guessed site query values. */
@Composable
fun SourceSelectFiltersDialog(
    filters: List<com.kitsugi.animelist.data.manga.MangaSelectFilter>,
    current: Map<Int, Int>?,
    onDismiss: () -> Unit,
    onApply: (Map<Int, Int>) -> Unit,
) {
    val choices = remember(filters, current) {
        androidx.compose.runtime.mutableStateMapOf<Int, Int>().apply {
            filters.forEach { put(it.index, current?.get(it.index) ?: it.selected) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kaynak filtreleri") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))) {
                filters.forEach { filter ->
                    var expanded by remember(filter.index) { mutableStateOf(false) }
                    Text(filter.name, fontWeight = FontWeight.SemiBold)
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(filter.values.getOrNull(choices[filter.index] ?: filter.selected) ?: "")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            filter.values.forEachIndexed { index, label ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    choices[filter.index] = index
                                    expanded = false
                                })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(choices.toMap()) }) { Text("Uygula") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}
