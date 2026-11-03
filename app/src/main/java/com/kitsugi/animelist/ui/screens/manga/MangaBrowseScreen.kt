package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.res.Configuration
import androidx.compose.animation.core.*
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.ui.app.AddonViewModel
import com.kitsugi.animelist.ui.app.MangaViewModel
import com.kitsugi.animelist.ui.components.KitsugiAddonsSettingsDialog
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun MangaBrowseScreen(
    repository: MangaSourceRepository,
    initialQuery: String? = null,
    vm: MangaBrowseViewModel = viewModel(factory = MangaBrowseViewModel.Factory(repository)),
    onMangaClick: (MangaSource, MangaDetails) -> Unit = { _, _ -> },
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

    // ── GitHub Vekil Sunucu ayarı (Manga Kaynakları sekmesindeki diyalog için) ──
    // Diyalog bu iki parametre geçilmediği sürece varsayılan (false / no-op) değerlerle
    // açılıyordu; anahtar hiçbir zaman açılamıyordu.
    val settingsDataStore = remember(context) { SettingsDataStore(context.applicationContext) }
    val appSettings by settingsDataStore.settingsFlow.collectAsState(
        initial = com.kitsugi.animelist.data.settings.AppSettings()
    )
    val settingsScope = rememberCoroutineScope()

    LaunchedEffect(reposList) {
        addonViewModel.syncRepos(reposList)
    }

    LaunchedEffect(addonViewModel) {
        addonViewModel.onShowMessage = { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(mangaViewModel) {
        mangaViewModel.onShowMessage = { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(initialQuery) {
        if (vm.lastInitialQuery != initialQuery) {
            vm.lastInitialQuery = initialQuery
            if (!initialQuery.isNullOrBlank()) {
                vm.search(initialQuery)
            } else {
                vm.search("")
            }
        }
    }

    // Kurulu kaynak seti değiştiğinde (eklenti kurulumu / silme / aktif-pasif)
    // browse ViewModel'indeki kaynak listesini tazele. Önceden hiç çağrılmıyordu:
    // ayarlar penceresinden eklenti kurup kapatınca "kaynak yok" ekranı kalıyordu.
    // Anahtara aktif/pasif durumunu DA katıyoruz: getInstalledSources() kapalı
    // kaynakları da döndürdüğü için sadece key listesi değişmez ve toggle sonrası
    // browse tarafındaki getAvailableSources() listesi eski kalırdı.
    val installedSourceKeys = mangaViewModel.mangaSources
        .map { "${it.stableSourceKey()}:${mangaViewModel.isMangaSourceEnabled(it)}" }
        .sorted()
        .joinToString("|")
    LaunchedEffect(installedSourceKeys) {
        vm.refreshSources()
    }
    val accentColor = LocalKitsugiAccent.current
    val cols = rememberKitsugiGridColumns()
    val gridState = rememberLazyGridState()

    // Snapshot delegated props → allow smart cast
    val query          = ui.searchQuery
    val states         = ui.sourceStates
    val selFilter      = ui.selectedSourceFilter
    val popular        = ui.popularMangas
    val hasNext        = ui.hasNextPage

    // Keep the actual source with each result. Display names are not identities:
    // Kotatsu and Mihon can expose the same title/name with different URL contracts.
    val mergedMangas = remember(states, selFilter, query, popular) {
        val raw = if (query.isNotBlank()) {
            states.filter { selFilter == null || it.source.stableSourceKey() == selFilter.stableSourceKey() }
                .flatMap { state -> state.mangas.map { state.source to it } }
                .sortedWith(
                    compareByDescending<Pair<MangaSource, MangaDetails>> {
                        com.kitsugi.animelist.data.manga.MangaTitleMatcher.getBestSimilarityScore(query, it.second)
                    }.thenByDescending { repository.getSourcePriority(it.first) }
                )
        } else {
            selFilter?.let { source -> popular.map { source to it } }.orEmpty()
        }
        raw.distinctBy { "${it.first.stableSourceKey()}|${it.second.url}" }
    }

    // +18 UYUMU (2026-10-14): uzantı gözat/arama sonuçları da göster/gizle sözleşmesine
    // uyar — yetişkin (hentai/doujin vb.) başlıklar ayar kapalıyken listelenmez.
    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    val visibleMangas = remember(mergedMangas, showAdultContent) {
        if (showAdultContent) mergedMangas else mergedMangas.filter { !it.second.isAdultContent }
    }

    val backdropUrl = visibleMangas.firstOrNull()?.second?.thumbnailUrl

    Box(Modifier.fillMaxSize().background(KitsugiColors.Background)) {

        // ── Backdrop ──
        if (!backdropUrl.isNullOrBlank()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = backdropUrl, contentDescription = null, contentScale = ContentScale.Crop,
                isAdult = visibleMangas.firstOrNull()?.second?.isAdultContent == true,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f }
            )
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(KitsugiColors.Background.copy(alpha = 0.55f), KitsugiColors.Background))
        ))

        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ── Top bar ──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    colors  = IconButtonDefaults.iconButtonColors(containerColor = KitsugiColors.SurfaceStrong.copy(0.5f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.ArrowBack, "Geri", tint = KitsugiColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.inline_discover_manga_eb24f62), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.TravelExplore, null, tint = accentColor, modifier = Modifier.padding(end = 8.dp).size(22.dp))
            }

            // ── Info card (CS3 portrait style) ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(KitsugiColors.Surface.copy(0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // mini poster
                Box(
                    Modifier.size(width = 52.dp, height = 76.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(KitsugiColors.SurfaceStrong)
                ) {
                    if (!backdropUrl.isNullOrBlank()) {
                        com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                            model = backdropUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            isAdult = visibleMangas.firstOrNull()?.second?.isAdultContent == true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Book, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (query.isNotBlank()) "Arama: \"$query\"" else selFilter?.name ?: stringResource(R.string.inline_discover_manga_eb24f62),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (query.isNotBlank())
                            "${visibleMangas.size} sonuç · ${states.size} kaynak"
                        else
                            "${popular.size} manga · ${selFilter?.lang?.uppercase() ?: ""}",
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Search bar ──
            OutlinedTextField(
                value = query,
                onValueChange = { vm.search(it) },
                placeholder   = { Text(stringResource(R.string.manga_ara), color = KitsugiColors.TextMuted) },
                leadingIcon   = { Icon(Icons.Search, null, tint = KitsugiColors.TextMuted) },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = KitsugiColors.Border,
                    cursorColor          = accentColor,
                    focusedTextColor     = KitsugiColors.TextPrimary,
                    unfocusedTextColor   = KitsugiColors.TextPrimary,
                    focusedContainerColor   = KitsugiColors.Surface.copy(0.5f),
                    unfocusedContainerColor = KitsugiColors.Surface.copy(0.3f)
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(10.dp))

            // ── Source chips — ALWAYS premium capsule style ──
            // Sadece kaynaklar gerçekten boşsa VE arama yüklenip bitmişse göster.
            // Arama devam ediyorken (sourceStates doluyken) bu mesajı gösterme.
            val sourcesEmpty = ui.sources.isEmpty() && states.isEmpty() && !ui.isLoadingPopular
            if (sourcesEmpty) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptySourcesHint(onOpenSettings = { showSettingsDialog = true })
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    if (query.isNotBlank()) {
                        // "Tümü" chip
                        val totalCount  = states.sumOf { it.mangas.size }
                        val anyLoading  = states.any { it.isLoading }
                        item {
                            MangaChip(
                                label     = stringResource(R.string.tumu),
                                count     = totalCount,
                                isLoading = anyLoading,
                                error     = null,
                                isSelected = selFilter == null,
                                accent    = accentColor,
                                isAll     = true,
                                onClick   = { vm.selectSourceFilter(null) }
                            )
                        }
                        items(states, key = { it.source.stableSourceKey() }) { st ->
                            MangaChip(
                                label     = st.source.name,
                                count     = st.mangas.size,
                                isLoading = st.isLoading,
                                error     = st.error,
                                errorCode = st.errorCode,
                                isSelected = selFilter?.stableSourceKey() == st.source.stableSourceKey(),
                                accent    = accentColor,
                                isAll     = false,
                                onClick   = {
                                    vm.selectSourceFilter(if (selFilter?.stableSourceKey() == st.source.stableSourceKey()) null else st.source)
                                }
                            )
                        }
                    } else {
                        // Popular mode → still premium capsule chips
                        items(ui.sources) { src ->
                            MangaChip(
                                label      = "${src.name}  [${src.lang.uppercase()}]",
                                count      = if (selFilter?.stableSourceKey() == src.stableSourceKey()) popular.size else 0,
                                isLoading  = selFilter?.stableSourceKey() == src.stableSourceKey() && ui.isLoadingPopular,
                                error      = null,
                                isSelected = selFilter?.stableSourceKey() == src.stableSourceKey(),
                                accent     = accentColor,
                                isAll      = false,
                                onClick    = { vm.selectSourceFilter(src) }
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp), color = KitsugiColors.SurfaceStrong.copy(0.5f))

                // ── Grid ──
                Box(Modifier.weight(1f)) {
                    val loading = if (query.isNotBlank()) states.any { it.isLoading } else ui.isLoadingPopular
                    when {
                        loading && mergedMangas.isEmpty() -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(cols),
                                verticalArrangement   = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(8) {
                                    MangaCardShimmer()
                                }
                            }
                        }
                        !loading && visibleMangas.isEmpty() ->
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                ) {
                                    Icon(Icons.SearchOff, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        ui.popularError?.takeIf { query.isBlank() } ?: stringResource(R.string.sonuc_bulunamadi),
                                        color = KitsugiColors.TextSecondary
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    KitsugiButton(onClick = { vm.refreshSources() }) {
                                        Text("Tekrar Dene", color = Color.White)
                                    }
                                }
                            }
                        else -> {
                            val hasNextPageToShow = if (query.isBlank()) hasNext
                                else (selFilter != null && (states.firstOrNull { it.source.stableSourceKey() == selFilter.stableSourceKey() }?.hasNextPage == true))
                            // Eskiden `LaunchedEffect(mergedMangas.size)` vardı: her yeni sonuç
                            // geldiğinde tekrar çalışıp bütün sayfaları arka arkaya (sonsuz)
                            // indiriyordu. Artık yalnızca kullanıcı listenin sonuna yaklaşınca
                            // ve gerçekten yüklenecek sayfa varsa tetikleniyor.
                            val shouldAutoLoad by remember(visibleMangas.size, hasNextPageToShow, loading) {
                                derivedStateOf {
                                    if (!hasNextPageToShow || loading || visibleMangas.isEmpty()) {
                                        false
                                    } else {
                                        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                                            ?: Int.MAX_VALUE
                                        lastVisible >= visibleMangas.size - 2
                                    }
                                }
                            }
                            LaunchedEffect(shouldAutoLoad) {
                                if (shouldAutoLoad) vm.loadNextPage()
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(cols),
                                state   = gridState,
                                verticalArrangement   = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                            // 1) ÖNCE başarıyla veri gelen kaynakların mangaları (en üstte).
                            items(visibleMangas, key = { "${it.first.stableSourceKey()}|${it.second.url}" }) { (source, manga) ->
                                MangaCard(manga, accentColor) { onMangaClick(source, manga) }
                            }
                            // 2) SONRA hâlâ yüklenen kaynakların iskelet (placeholder) kartları — EN ALTTA.
                            //    Böylece başarıyla gelen sonuçlar her zaman üstte kalır, yüklenenler onları aşağı itmez.
                            if (query.isNotBlank()) {
                                val loadingStates = states.filter { it.isLoading && (selFilter == null || selFilter.stableSourceKey() == it.source.stableSourceKey()) }
                                items(loadingStates, key = { "sk_${it.source.stableSourceKey()}" }) {
                                    SkeletonCard(it.source.name, accentColor)
                                }
                            }
                            val hasNextPageToShow = if (query.isBlank()) hasNext
                                else (selFilter != null && (states.firstOrNull { it.source.stableSourceKey() == selFilter.stableSourceKey() }?.hasNextPage == true))

                            if (query.isBlank() || selFilter != null) {
                                item(span = { GridItemSpan(cols) }) {
                                    androidx.compose.animation.AnimatedVisibility(visible = hasNextPageToShow) {
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
        }
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
            // ── GitHub Vekil Sunucu (jsDelivr) — eksik geçilen parametreler ─────
            useGithubProxy = appSettings.useGithubProxy,
            onUseGithubProxyChanged = { enabled ->
                settingsScope.launch { settingsDataStore.setUseGithubProxy(enabled) }
            },
            mangaSources = mangaViewModel.mangaSources,
            onInstallMangaExtension = { mangaViewModel.installMangaExtension(it) },
            onDeleteMangaExtension = { mangaViewModel.deleteMangaExtension(it) },
            onGetMangaSourceEnabled = { mangaViewModel.isMangaSourceEnabled(it) },
            onToggleMangaSource = { source, enabled -> mangaViewModel.toggleMangaSource(source, enabled) },
            // Manga taraflı ayarlar diyalogu (initialTab = 2) da gerçek callback'i alsın;
            // aksi halde buradaki "Toplu Sil" butonu ölü kalırdı.
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
            isAll     -> Icon(Icons.Extension, null, tint = if (isSelected) accent else KitsugiColors.TextSecondary, modifier = Modifier.size(13.dp))
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
private fun SkeletonCard(sourceName: String, accent: Color) {
    val inf = rememberInfiniteTransition(label = "sk")
    val sh by inf.animateFloat(0.25f, 0.07f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sh")
    Card(
        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
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
private fun MangaCard(manga: MangaDetails, accent: Color, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).tvClickable(shape = RoundedCornerShape(12.dp)) { onClick() },
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceSoft),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
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

// ─── Empty sources hint ───────────────────────────────────────────────────────

@Composable
private fun EmptySourcesHint(onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Extension, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.inline_no_manga_extensions_have_been_e92d6be), color = KitsugiColors.TextSecondary, fontWeight = FontWeight.Medium)
            KitsugiButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.inline_edit_extensions_134b588), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Manga card shimmer ───────────────────────────────────────────────────────

@Composable
private fun MangaCardShimmer() {
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
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceSoft),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = alpha * 0.1f))
        ) {
            // Text placeholders
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
