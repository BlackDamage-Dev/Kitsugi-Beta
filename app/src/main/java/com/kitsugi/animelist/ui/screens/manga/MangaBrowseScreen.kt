package com.kitsugi.animelist.ui.screens.manga

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
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
import com.kitsugi.animelist.ui.theme.LocalShowAdultContent
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch

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

    Box(Modifier.fillMaxSize().background(KitsugiColors.Background)) {

        // ── Backdrop ──
        if (!backdropUrl.isNullOrBlank()) {
            KitsugiNsfwImage(
                model = backdropUrl, contentDescription = null, contentScale = ContentScale.Crop,
                isAdult = visibleMangas.firstOrNull()?.second?.isAdultContent == true,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.16f }
            )
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(KitsugiColors.Background.copy(alpha = 0.55f), KitsugiColors.Background))
        ))

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {

            // ── Kompakt üst bar (video keşif paritesi) ──
            MangaDiscoverTopBar(
                summary = summaryText,
                accent = accentColor,
                onBack = onBack,
                onShowInfo = { showInfoDialog = true },
                onOpenSettings = { showSettingsDialog = true },
                onOpenLibrary = onOpenMangaLibrary,
                isResolving = ui.isResolvingTitles
            )

            // ── Arama paneli: mod + arama + kaynak çipleri tek kart içinde ──
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
                compact = isLandscape
            ) {
                val sourcesEmpty = ui.sources.isEmpty() && states.isEmpty() && !ui.isLoadingPopular
                if (sourcesEmpty) {
                    // Eklenti yoksa: tek satır bilgi + doğrudan eklenti yönetimi.
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Extension, null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.inline_no_manga_extensions_have_been_e92d6be),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.inline_edit_extensions_134b588),
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .tvClickable(shape = RoundedCornerShape(999.dp)) { showSettingsDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    MangaDiscoverChipRow {
                        if (query.isNotBlank()) {
                            val totalCount = states.sumOf { it.mangas.size }
                            val anyLoading = states.any { it.isLoading }
                            item(key = "all_chip") {
                                MangaChip(
                                    label = stringResource(R.string.tumu),
                                    count = totalCount,
                                    isLoading = anyLoading,
                                    error = null,
                                    isSelected = selFilter == null,
                                    accent = accentColor,
                                    isAll = true,
                                    onClick = { vm.selectSourceFilter(null) }
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
                                    accent = accentColor,
                                    isAll = false,
                                    onClick = {
                                        vm.selectSourceFilter(
                                            if (selFilter?.stableSourceKey() == st.source.stableSourceKey()) null else st.source
                                        )
                                    }
                                )
                            }
                        } else {
                            // Popüler modda da "Tümü" çipi vardır: seçili kaynak yokken
                            // (Tümü) en iyi 3 kaynağın popüler listesi birleşik gösterilir.
                            item(key = "all_chip") {
                                MangaChip(
                                    label = stringResource(R.string.tumu),
                                    count = popular.size,
                                    isLoading = ui.isLoadingPopular && selFilter == null,
                                    error = null,
                                    isSelected = selFilter == null,
                                    accent = accentColor,
                                    isAll = true,
                                    onClick = { vm.selectSourceFilter(null) }
                                )
                            }
                            items(ui.sources, key = { it.stableSourceKey() }) { src ->
                                val sourcePopularCount = popular.count { it.source.equals(src.name, ignoreCase = true) }
                                MangaChip(
                                    label = "${src.name}  [${src.lang.uppercase()}]",
                                    count = sourcePopularCount,
                                    isLoading = selFilter?.stableSourceKey() == src.stableSourceKey() && ui.isLoadingPopular,
                                    error = null,
                                    isSelected = selFilter?.stableSourceKey() == src.stableSourceKey(),
                                    accent = accentColor,
                                    isAll = false,
                                    onClick = { vm.selectSourceFilter(src) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // ── Sıralama satırı (video keşif paritesi) ──
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

            HorizontalDivider(color = KitsugiColors.SurfaceStrong.copy(0.4f))

            authFailureSource?.let { source ->
                MangaSourceAuthBanner(
                    source = source,
                    onLogin = { loginSource = source },
                    onRetry = { vm.retrySource(source) },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            // ── Grid ──
            Box(Modifier.weight(1f)) {
                val loading = if (query.isNotBlank()) states.any { it.isLoading } else ui.isLoadingPopular
                when {
                    loading && mergedMangas.isEmpty() -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            verticalArrangement   = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(if (isLandscape) cols * 2 else 8) {
                                MangaCardShimmer(compact = isLandscape)
                            }
                        }
                    }
                    !loading && visibleMangas.isEmpty() ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Icon(Icons.Rounded.SearchOff, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(44.dp))
                                Text(
                                    stringResource(R.string.manga_discover_no_result),
                                    color = KitsugiColors.TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    stringResource(R.string.manga_discover_no_result_hint),
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 11.sp,
                                    style = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                )
                                Spacer(Modifier.height(4.dp))
                                MangaTitleModeChip(
                                    mode = if (titleMode == MangaSearchTitleMode.LOCALIZED)
                                        MangaSearchTitleMode.ORIGINAL else MangaSearchTitleMode.LOCALIZED,
                                    accent = accentColor,
                                    enabled = query.isNotBlank(),
                                    onClick = {
                                        vm.setTitleMode(
                                            if (titleMode == MangaSearchTitleMode.LOCALIZED)
                                                MangaSearchTitleMode.ORIGINAL else MangaSearchTitleMode.LOCALIZED
                                        )
                                    }
                                )
                            }
                        }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        verticalArrangement   = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1) ÖNCE başarıyla veri gelen kaynakların mangaları (en üstte).
                        items(visibleMangas, key = { "${it.first.stableSourceKey()}|${it.second.url}" }) { (source, manga) ->
                            MangaCard(manga, accentColor, compact = isLandscape) { onMangaClick(source, manga) }
                        }
                        // 2) SONRA hâlâ yüklenen kaynakların iskelet kartları — EN ALTTA.
                        if (query.isNotBlank()) {
                            val loadingStates = states.filter {
                                it.isLoading && (selFilter == null || selFilter.stableSourceKey() == it.source.stableSourceKey())
                            }
                            items(loadingStates, key = { "sk_${it.source.stableSourceKey()}" }) {
                                SkeletonCard(it.source.name, accentColor, compact = isLandscape)
                            }
                        }
                        val hasNextPageToShow = if (query.isBlank()) hasNext
                            else (selFilter != null && (states.firstOrNull { it.source.stableSourceKey() == selFilter.stableSourceKey() }?.hasNextPage == true))

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

// ─── Premium capsule chip ─────────────────────────────────────────────────────

@Composable
fun MangaChip(
    label: String, count: Int, isLoading: Boolean, error: String?,
    isSelected: Boolean, accent: Color, isAll: Boolean, onClick: () -> Unit,
    errorCode: String? = null
) {
    val inf = rememberInfiniteTransition(label = "p")
    val pulse by inf.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a"
    )
    val bg = when {
        isSelected  -> accent.copy(alpha = 0.35f)
        isLoading   -> accent.copy(alpha = pulse * 0.22f)
        count > 0   -> accent.copy(alpha = 0.12f)
        error != null -> Color.Red.copy(alpha = 0.15f)
        else        -> Color.White.copy(alpha = 0.07f)
    }
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(bg).tvClickable(shape = RoundedCornerShape(999.dp)) { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            isLoading -> CircularProgressIndicator(Modifier.size(10.dp), color = accent, strokeWidth = 1.5.dp)
            isAll     -> Icon(Icons.Rounded.Extension, null, tint = if (isSelected) accent else KitsugiColors.TextSecondary, modifier = Modifier.size(13.dp))
            count > 0 -> Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            error != null -> Box(Modifier.size(7.dp).clip(CircleShape).background(Color.Red.copy(0.7f)))
            else      -> Box(Modifier.size(7.dp).clip(CircleShape).background(KitsugiColors.TextMuted.copy(0.5f)))
        }
        Text(
            buildString {
                append(label)
                if (count > 0 && !isAll) append("  $count")
            },
            color = if (isSelected || count > 0) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected || count > 0) FontWeight.Bold else FontWeight.Normal
        )
        if (error != null && errorCode != null) {
            Text(
                errorCode,
                color = Color.Red.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Skeleton card ────────────────────────────────────────────────────────────

@Composable
private fun SkeletonCard(sourceName: String, accent: Color, compact: Boolean = false) {
    val inf = rememberInfiniteTransition(label = "sk")
    val sh by inf.animateFloat(0.25f, 0.07f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sh")
    Card(
        Modifier.fillMaxWidth().height(if (compact) 168.dp else 210.dp).clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceSoft.copy(0.6f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(sh)))
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))).padding(8.dp)
            ) {
                Column {
                    Text(stringResource(R.string.araniyor), color = KitsugiColors.TextSecondary, fontSize = 11.sp)
                    Text(sourceName, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ─── Manga card ───────────────────────────────────────────────────────────────

@Composable
private fun MangaCard(
    manga: MangaDetails,
    accent: Color,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().height(if (compact) 168.dp else 210.dp).clip(RoundedCornerShape(12.dp))
            .tvClickable(shape = RoundedCornerShape(12.dp)) { onClick() },
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceSoft),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            KitsugiNsfwImage(
                model = manga.thumbnailUrl,
                contentDescription = manga.title,
                isAdult = manga.isAdultContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.93f)))).padding(8.dp)
            ) {
                Column {
                    Text(manga.title, color = KitsugiColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!manga.source.isNullOrBlank())
                        Text(manga.source, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Manga card shimmer ───────────────────────────────────────────────────────

@Composable
private fun MangaCardShimmer(compact: Boolean = false) {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 168.dp else 210.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceSoft),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = alpha * 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha * 0.15f))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha * 0.15f))
                )
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
