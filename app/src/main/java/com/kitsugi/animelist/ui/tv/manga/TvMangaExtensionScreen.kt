package com.kitsugi.animelist.ui.tv.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import com.kitsugi.animelist.R
import androidx.compose.ui.res.stringResource
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import com.kitsugi.animelist.data.manga.model.SourceRuntimeStats
import com.kitsugi.animelist.data.remote.MangaExtensionInfo
import com.kitsugi.animelist.data.remote.MangaRepoClient
import com.kitsugi.animelist.ui.app.AddonViewModel
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint
import com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.utils.requestFocusAfterFrames

// ── Sayfa 1/3: package, imports, enum, ana scaffold ────────────────────────

private enum class TvExtTab { REPOS, INSTALLED }

/**
 * TV-native Manga Extension & Repo Manager.
 *
 * İki sekme:
 *  • **Repolar** — Keiyoushi hızlı ekle, manuel URL, repo listesi + eklenti indirme
 *  • **Kurulu**  — Yüklü kaynak listesi, sağlık badge'leri, kaldırma
 *
 * @param sources               Yüklü manga kaynaklarının listesi
 * @param repos                 Eklentili repo URL listesi
 * @param repoExtensions        Repo URL → eklenti bilgisi haritası
 * @param repoLoadingState      Repo URL → yükleniyor mu?
 * @param onAddRepo             Repo URL ekle callback'i
 * @param onDeleteRepo          Repo URL sil callback'i
 * @param onFetchRepo           Repo içeriğini çek callback'i
 * @param onInstallApk          Eklenti APK kur callback'i
 * @param onDeleteExtension     Kaynağı kaldır callback'i
 * @param onGetSourceHealth     Kaynak sağlık durumunu döndürür
 * @param onGetRuntimeStats     Kaynak çalışma istatistiklerini döndürür
 * @param onQuickCheckSource    Anlık sağlık kontrolü tetikler
 * @param onNavigateToHealth    Source Health ekranına git callback'i
 * @param onBack                Geri navigasyon callback'i
 */
@Composable
fun TvMangaExtensionScreen(
    sources: List<MangaSource> = emptyList(),
    repos: List<String> = emptyList(),
    repoExtensions: Map<String, List<MangaExtensionInfo>?> = emptyMap(),
    repoLoadingState: Map<String, Boolean> = emptyMap(),
    bulkInstallRepoUrl: String? = null,
    bulkInstallDone: Int = 0,
    bulkInstallTotal: Int = 0,
    bulkInstallCurrentName: String = "",
    onAddRepo: (String) -> Unit = {},
    onDeleteRepo: (String) -> Unit = {},
    onFetchRepo: (String) -> Unit = {},
    onInstallApk: (MangaExtensionInfo, ((Boolean) -> Unit)?) -> Unit = { _, _ -> },
    onInstallAllMangaExtensions: (String, List<MangaExtensionInfo>) -> Unit = { _, _ -> },
    onDeleteExtension: (MangaSource) -> Unit = {},
    onToggleSource: (MangaSource, Boolean) -> Unit = { _, _ -> },
    onGetSourceHealth: (MangaSource) -> SourceHealthStatus = { SourceHealthStatus.Unknown },
    onGetRuntimeStats: (MangaSource) -> SourceRuntimeStats = { SourceRuntimeStats() },
    onGetLastErrorCode: (MangaSource) -> String? = { null },
    onGetLastReason: (MangaSource) -> String? = { null },
    onGetCooldownUntil: (MangaSource) -> Long = { 0L },
    onQuickCheckSource: (MangaSource) -> Unit = {},
    onNavigateToHealth: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    BackHandler(enabled = true) { onBack() }

    var activeTab by remember { mutableStateOf(TvExtTab.REPOS) }
    val tabFocusRequesters = remember { List(2) { FocusRequester() } }
    val contentFocusRequester = remember { FocusRequester() }
    val repoListState = rememberLazyListState()
    val installedListState = rememberLazyListState()

    // Sol panel'deki tab'e focus ver: layout attach olduktan sonra
    LaunchedEffect(Unit) {
        tabFocusRequesters[0].requestFocusAfterFrames(frames = 2)
    }

    // Content'e focus atlamak için D-pad sağ tuşunu yakala
    var sidebarHasFocus by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        // Arka plan gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A2744).copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // ── Sol Sidebar: Sekme Paneli ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.85f))
                    .padding(top = 48.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Başlık
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(KitsugiColors.AccentBlue.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Extension,
                            contentDescription = null,
                            tint = KitsugiColors.AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            "Manga Eklentileri",
                            color = KitsugiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "${sources.size} kurulu kaynak",
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Sekme butonları
                listOf(
                    TvExtTab.REPOS to Triple(Icons.Storage, "Repolar", repos.size.toString()),
                    TvExtTab.INSTALLED to Triple(Icons.CheckCircle, "Kurulu", sources.size.toString())
                ).forEachIndexed { idx, (tab, triple) ->
                    val (icon, label, badge) = triple
                    TvExtTabButton(
                        icon = icon,
                        label = label,
                        badge = badge,
                        isSelected = activeTab == tab,
                        focusRequester = tabFocusRequesters[idx],
                        onSelect = {
                            activeTab = tab
                            sidebarHasFocus = false
                        },
                        onFocusChange = { hasFocus -> if (hasFocus) sidebarHasFocus = true },
                        onDpadRight = {
                            sidebarHasFocus = false
                            try { contentFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }

                Spacer(Modifier.weight(1f))

                // Source Health butonu
                TvExtActionButton(
                    icon = Icons.HealthAndSafety,
                    label = stringResource(R.string.inline_source_health_1595253),
                    color = KitsugiColors.AccentGreen,
                    onClick = onNavigateToHealth
                )

                // Geri
                TvExtActionButton(
                    icon = Icons.ArrowBack,
                    label = stringResource(R.string.geri),
                    color = KitsugiColors.TextMuted,
                    onClick = onBack
                )
            }

            // ── Sağ Panel: İçerik ─────────────────────────────────────────────
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { tab ->
                when (tab) {
                    TvExtTab.REPOS -> TvExtRepoTab(
                        repos = repos,
                        repoExtensions = repoExtensions,
                        repoLoadingState = repoLoadingState,
                        sources = sources,
                        bulkInstallRepoUrl = bulkInstallRepoUrl,
                        bulkInstallDone = bulkInstallDone,
                        bulkInstallTotal = bulkInstallTotal,
                        bulkInstallCurrentName = bulkInstallCurrentName,
                        onAddRepo = onAddRepo,
                        onDeleteRepo = onDeleteRepo,
                        onFetchRepo = onFetchRepo,
                        onInstallApk = onInstallApk,
                        onInstallAllMangaExtensions = onInstallAllMangaExtensions,
                        listState = repoListState,
                        contentFocusRequester = contentFocusRequester,
                        onDpadLeft = {
                            sidebarHasFocus = true
                            try { tabFocusRequesters[0].requestFocus() } catch (_: Exception) {}
                        }
                    )
                    TvExtTab.INSTALLED -> TvExtInstalledTab(
                        sources = sources,
                        onGetSourceHealth = onGetSourceHealth,
                        onGetRuntimeStats = onGetRuntimeStats,
                        onGetLastErrorCode = onGetLastErrorCode,
                        onGetLastReason = onGetLastReason,
                        onGetCooldownUntil = onGetCooldownUntil,
                        onQuickCheckSource = onQuickCheckSource,
                        onDeleteExtension = onDeleteExtension,
                        onToggleSource = onToggleSource,
                        listState = installedListState,
                        contentFocusRequester = contentFocusRequester,
                        onDpadLeft = {
                            sidebarHasFocus = true
                            try { tabFocusRequesters[1].requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }
}

// ── Sayfa 2/3: TabButton, ActionButton, RepoTab ────────────────────────────

@Composable
private fun TvExtTabButton(
    icon: ImageVector,
    label: String,
    badge: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    onDpadRight: () -> Unit = {}
) {
    var hasFocus by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        if (isSelected) KitsugiColors.AccentBlue.copy(alpha = 0.18f)
        else if (hasFocus) KitsugiColors.SurfaceStrong
        else Color.Transparent,
        tween(150)
    )
    val scale by animateFloatAsState(if (hasFocus) 1.03f else 1f, tween(150))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) KitsugiColors.AccentBlue.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                hasFocus = state.hasFocus
                onFocusChange(state.hasFocus)
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onSelect(); true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { onDpadRight(); true }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isSelected) KitsugiColors.AccentBlue else KitsugiColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            label,
            color = if (isSelected) KitsugiColors.AccentBlue else KitsugiColors.TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isSelected) KitsugiColors.AccentBlue.copy(alpha = 0.2f)
                    else KitsugiColors.SurfaceStrong
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                badge,
                color = if (isSelected) KitsugiColors.AccentBlue else KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TvExtActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (hasFocus) 1.03f else 1f, tween(150))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hasFocus) color.copy(alpha = 0.15f) else Color.Transparent)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode in listOf(
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER
                    )
                ) { onClick(); true } else false
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ── RepoTab ────────────────────────────────────────────────────────────────

@Composable
private fun TvExtRepoTab(
    repos: List<String>,
    repoExtensions: Map<String, List<MangaExtensionInfo>?>,
    repoLoadingState: Map<String, Boolean>,
    sources: List<MangaSource>,
    bulkInstallRepoUrl: String?,
    bulkInstallDone: Int,
    bulkInstallTotal: Int,
    bulkInstallCurrentName: String,
    onAddRepo: (String) -> Unit,
    onDeleteRepo: (String) -> Unit,
    onFetchRepo: (String) -> Unit,
    onInstallApk: (MangaExtensionInfo, ((Boolean) -> Unit)?) -> Unit,
    onInstallAllMangaExtensions: (String, List<MangaExtensionInfo>) -> Unit = { _, _ -> },
    listState: LazyListState,
    contentFocusRequester: FocusRequester,
    onDpadLeft: () -> Unit
) {
    var expandedRepo by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                ) { onDpadLeft(); true } else false
            }
            .focusRequester(contentFocusRequester),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    stringResource(R.string.inline_manga_repositories_455078d),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    stringResource(R.string.inline_manage_manga_extension_sources_1_2c1da29, (repos.size).toString()),
                    color = KitsugiColors.TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        // Keiyoushi hızlı ekle kartı
        item {
            TvExtQuickAddCard(
                repos = repos,
                onAddRepo = onAddRepo
            )
        }

        // Repo listesi
        if (repos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.CloudOff,
                            null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(stringResource(R.string.inline_no_repositories_added_yet_4131ec1), color = KitsugiColors.TextMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.inline_use_the_card_above_to_2ce9286),
                            color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        items(repos.distinct(), key = { it }) { repoUrl ->
            val repoPlugins = repoExtensions[repoUrl]
            val isLoading = repoLoadingState[repoUrl] == true
            val isExpanded = expandedRepo == repoUrl
            val isBulkInstalling = bulkInstallRepoUrl == repoUrl
            val repoName = when {
                MangaRepoClient.isKitsugiMangaRepoUrl(repoUrl) -> "🌸 Kitsugi Manga Plugins"
                repoUrl.contains("keiyoushi", ignoreCase = true) -> "⚡ Keiyoushi"
                else -> MangaRepoClient.displayNameForRepo(repoUrl)
            }
            val totalCount = repoPlugins?.size ?: 0
            val installedCount = repoPlugins?.count { ext ->
                sources.any { s -> (s.pkgName.isNotEmpty() && s.pkgName == ext.pkg) || s.name == ext.name }
            } ?: 0

            TvExtRepoCard(
                repoUrl = repoUrl,
                repoName = repoName,
                repoPlugins = repoPlugins,
                isLoading = isLoading,
                isExpanded = isExpanded,
                isBulkInstalling = isBulkInstalling,
                bulkInstallDone = bulkInstallDone,
                bulkInstallTotal = bulkInstallTotal,
                bulkInstallCurrentName = bulkInstallCurrentName,
                installedCount = installedCount,
                totalCount = totalCount,
                sources = sources,
                onToggleExpand = {
                    if (isExpanded) expandedRepo = null
                    else {
                        expandedRepo = repoUrl
                        if (repoPlugins == null) onFetchRepo(repoUrl)
                    }
                },
                onFetchRepo = { onFetchRepo(repoUrl) },
                onDeleteRepo = { onDeleteRepo(repoUrl) },
                onInstallApk = onInstallApk,
                onInstallAll = { list -> onInstallAllMangaExtensions(repoUrl, list) }
            )
        }
    }
}

// ── Sayfa 3/3: InstalledTab + helper composables ───────────────────────────

@Composable
private fun TvExtInstalledTab(
    sources: List<MangaSource>,
    onGetSourceHealth: (MangaSource) -> SourceHealthStatus,
    onGetRuntimeStats: (MangaSource) -> SourceRuntimeStats,
    onGetLastErrorCode: (MangaSource) -> String?,
    onGetLastReason: (MangaSource) -> String?,
    onGetCooldownUntil: (MangaSource) -> Long,
    onQuickCheckSource: (MangaSource) -> Unit,
    onDeleteExtension: (MangaSource) -> Unit,
    onToggleSource: (MangaSource, Boolean) -> Unit,
    listState: LazyListState,
    contentFocusRequester: FocusRequester,
    onDpadLeft: () -> Unit
) {
    var deleteTarget by remember { mutableStateOf<MangaSource?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                ) { onDpadLeft(); true } else false
            }
            .focusRequester(contentFocusRequester),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    "Kurulu Kaynaklar",
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    stringResource(R.string.inline_1_s_manga_sources_installed_b01a265, (sources.size).toString()),
                    color = KitsugiColors.TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        if (sources.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.BookmarkBorder,
                            null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(stringResource(R.string.kurulu_kaynak_yok), color = KitsugiColors.TextMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Repolar sekmesinden eklenti kurun.",
                            color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        items(sources, key = { "${it.baseUrl}_${it.name}" }) { source ->
            val health = onGetSourceHealth(source)
            val stats = onGetRuntimeStats(source)
            val errorCode = onGetLastErrorCode(source)
            val rawReason = onGetLastReason(source)
            val cooldownUntil = onGetCooldownUntil(source)

            TvExtSourceRow(
                source = source,
                health = health,
                stats = stats,
                errorCode = errorCode,
                rawReason = rawReason,
                cooldownUntil = cooldownUntil,
                onQuickCheck = { onQuickCheckSource(source) },
                onToggle = { enabled -> onToggleSource(source, enabled) },
                onDelete = { deleteTarget = source }
            )
        }
    }

    // Silme onay dialogu
    deleteTarget?.let { target ->
        TvExtConfirmDeleteDialog(
            sourceName = target.name,
            onConfirm = {
                onDeleteExtension(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

// ── QuickAddCard ───────────────────────────────────────────────────────────

@Composable
private fun TvExtQuickAddCard(
    repos: List<String>,
    onAddRepo: (String) -> Unit
) {
    val kitsugiMangaUrl = MangaRepoClient.KITSUGI_MANGA_REPO_URL
    val kitsugiAlreadyAdded = repos.any { MangaRepoClient.isKitsugiMangaRepoUrl(it) }
    val keiyoushiUrl = MangaRepoClient.KEIYOUSHI_INDEX_URL
    val alreadyAdded = repos.contains(keiyoushiUrl)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.inline_quick_extension_source_ccba258),
            color = KitsugiColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        // Kitsugi Manga Plugins kartı (resmi varsayılan depo)
        var kitsugiFocus by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (kitsugiFocus) KitsugiColors.AccentBlue.copy(alpha = 0.2f)
                    else KitsugiColors.AccentBlue.copy(alpha = 0.1f)
                )
                .border(
                    1.dp,
                    if (kitsugiFocus) KitsugiColors.AccentBlue.copy(alpha = 0.75f) else KitsugiColors.AccentBlue.copy(alpha = 0.3f),
                    RoundedCornerShape(10.dp)
                )
                .onFocusChanged { kitsugiFocus = it.hasFocus }
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode in listOf(
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER
                        ) && !kitsugiAlreadyAdded
                    ) { onAddRepo(kitsugiMangaUrl); true } else false
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Verified,
                null,
                tint = KitsugiColors.AccentBlue,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "🌸 Kitsugi Manga Plugins (Önerilen)",
                    color = KitsugiColors.AccentBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Varsayılan depo: Kitsugi Manga Plugins · eklentileri buradan kur",
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (kitsugiAlreadyAdded) KitsugiColors.AccentGreen.copy(alpha = 0.15f)
                        else KitsugiColors.AccentBlue.copy(alpha = 0.2f)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    if (kitsugiAlreadyAdded) "✓ Eklendi" else stringResource(R.string.ekle),
                    color = if (kitsugiAlreadyAdded) KitsugiColors.AccentGreen else KitsugiColors.AccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Keiyoushi kartı (focuslanabilir)
        var keiyoushiFocus by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (keiyoushiFocus) KitsugiColors.AccentBlue.copy(alpha = 0.18f)
                    else KitsugiColors.AccentBlue.copy(alpha = 0.08f)
                )
                .border(
                    1.dp,
                    if (keiyoushiFocus) KitsugiColors.AccentBlue.copy(alpha = 0.7f) else Color.Transparent,
                    RoundedCornerShape(10.dp)
                )
                .onFocusChanged { keiyoushiFocus = it.hasFocus }
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode in listOf(
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER
                        ) && !alreadyAdded
                    ) { onAddRepo(keiyoushiUrl); true } else false
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Language,
                null,
                tint = KitsugiColors.AccentBlue,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "⚡ Keiyoushi Resmi Reposu",
                    color = KitsugiColors.AccentBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    stringResource(R.string.inline_1_354_mihon_extensions_78_af394a0),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (alreadyAdded) KitsugiColors.AccentGreen.copy(alpha = 0.15f)
                        else KitsugiColors.AccentBlue.copy(alpha = 0.2f)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                KitsugiEmojiLabel(
                    label = if (alreadyAdded) stringResource(R.string.eklendi) else stringResource(R.string.ekle),
                    icon = if (alreadyAdded) Icons.Check else null,
                    iconSize = 11.dp,
                    color = if (alreadyAdded) KitsugiColors.AccentGreen else KitsugiColors.AccentBlue,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── RepoCard ───────────────────────────────────────────────────────────────

@Composable
private fun TvExtRepoCard(
    repoUrl: String,
    repoName: String,
    repoPlugins: List<MangaExtensionInfo>?,
    isLoading: Boolean,
    isExpanded: Boolean,
    isBulkInstalling: Boolean,
    bulkInstallDone: Int,
    bulkInstallTotal: Int,
    bulkInstallCurrentName: String,
    installedCount: Int,
    totalCount: Int,
    sources: List<MangaSource>,
    onToggleExpand: () -> Unit,
    onFetchRepo: () -> Unit,
    onDeleteRepo: () -> Unit,
    onInstallApk: (MangaExtensionInfo, ((Boolean) -> Unit)?) -> Unit,
    onInstallAll: (List<MangaExtensionInfo>) -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft)
    ) {
        // Repo başlık satırı
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (hasFocus) KitsugiColors.AccentBlue.copy(alpha = 0.08f)
                    else Color.Transparent
                )
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onToggleExpand(); true }
                            else -> false
                        }
                    } else false
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Storage, null, tint = KitsugiColors.AccentBlue, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    repoName,
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    if (repoPlugins != null) "$installedCount / $totalCount kurulu" else stringResource(R.string.ui_plugins_not_installed),
                    color = if (installedCount == totalCount && totalCount > 0) KitsugiColors.AccentGreen
                            else KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            }
            // Durum göstergesi
            when {
                isLoading -> KitsugiPlasmaLoader(size = 20.dp)
                repoPlugins == null -> Icon(
                    Icons.Refresh,
                    null,
                    tint = KitsugiColors.AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                !isBulkInstalling && repoPlugins != null && installedCount < totalCount -> Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(KitsugiColors.AccentBlue.copy(alpha = 0.14f))
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
                            ) {
                                onInstallAll(repoPlugins)
                                true
                            } else false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.DownloadForOffline, "Toplu kur", tint = KitsugiColors.AccentBlue, modifier = Modifier.size(20.dp))
                }
            }
            KitsugiChevronIcon(
                expanded = isExpanded,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
            // Sil butonu
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KitsugiColors.AccentRed.copy(alpha = 0.12f))
                    .onFocusChanged { }
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            event.nativeKeyEvent.keyCode in listOf(
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER
                            )
                        ) { onDeleteRepo(); true } else false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.DeleteForever,
                    null,
                    tint = KitsugiColors.AccentRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Toplu kurulum ilerleme barı
        if (isBulkInstalling && bulkInstallTotal > 0) {
            val fraction = bulkInstallDone.toFloat() / bulkInstallTotal.toFloat()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KitsugiColors.AccentBlue.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏳ Kuruluyor...", color = KitsugiColors.AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("$bulkInstallDone / $bulkInstallTotal", color = KitsugiColors.TextPrimary, fontSize = 11.sp)
                }
                if (bulkInstallCurrentName.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = Icons.Package,
                            contentDescription = null,
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(bulkInstallCurrentName, color = KitsugiColors.TextSecondary, fontSize = 10.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = KitsugiColors.AccentBlue,
                    trackColor = KitsugiColors.AccentBlue.copy(alpha = 0.15f)
                )
            }
        }

        if (isExpanded && repoPlugins == null && !isLoading) {
            Text(
                text = if (MangaRepoClient.isKitsugiMangaRepoUrl(repoUrl))
                    "Kitsugi Manga Plugins deposunda builds/index.json bulunamadı. Repo'da yayınlanmış katalog ve APK çıktıları gerekli."
                else "Repo kataloğu alınamadı. URL'yi ve bağlantıyı kontrol edip yenileyin.",
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // Genişletilmiş eklenti listesi
        AnimatedVisibility(visible = isExpanded && repoPlugins != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                repoPlugins?.take(30)?.forEach { ext ->
                    val isInstalled = sources.any { s ->
                        (s.pkgName.isNotEmpty() && s.pkgName == ext.pkg) || s.name == ext.name
                    }
                    TvExtPluginRow(
                        ext = ext,
                        isInstalled = isInstalled,
                        onInstall = { onInstallApk(ext, null) }
                    )
                }
                if ((repoPlugins?.size ?: 0) > 30) {
                    Text(
                        stringResource(R.string.inline_and_1_s_more_extensions_9ea82bd, ((repoPlugins?.size ?: 0) - 30).toString()),
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ── Plugin satırı ──────────────────────────────────────────────────────────

@Composable
private fun TvExtPluginRow(
    ext: MangaExtensionInfo,
    isInstalled: Boolean,
    onInstall: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hasFocus) KitsugiColors.SurfaceStrong else Color.Transparent)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode in listOf(
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER
                    ) && !isInstalled
                ) { onInstall(); true } else false
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!ext.iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = ext.iconUrl,
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(KitsugiColors.AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    ext.lang.uppercase().take(2),
                    color = KitsugiColors.AccentBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ext.name,
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "v${ext.version} · ${ext.lang.uppercase()}",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp
            )
        }
        if (ext.isNsfw) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(KitsugiColors.AccentRed.copy(alpha = 0.2f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) { Text("18+", color = KitsugiColors.AccentRed, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isInstalled) KitsugiColors.AccentGreen.copy(alpha = 0.15f)
                    else KitsugiColors.AccentBlue.copy(alpha = 0.15f)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                if (isInstalled) "Kurulu" else "Kur",
                color = if (isInstalled) KitsugiColors.AccentGreen else KitsugiColors.AccentBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── SourceRow (Kurulu sekmesi) ─────────────────────────────────────────────

@Composable
private fun TvExtSourceRow(
    source: MangaSource,
    health: SourceHealthStatus,
    stats: SourceRuntimeStats,
    errorCode: String?,
    rawReason: String?,
    cooldownUntil: Long,
    onQuickCheck: () -> Unit,
    onToggle: ((Boolean) -> Unit)? = null,
    onDelete: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (hasFocus) KitsugiColors.SurfaceStrong
                    else KitsugiColors.SurfaceSoft
                )
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onQuickCheck(); true }
                            else -> false
                        }
                    } else false
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Kaynak ikonu
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.MenuBook,
                    null,
                    tint = KitsugiColors.AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    source.name,
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        source.lang.uppercase(),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp
                    )
                    if (stats.successCount > 0 || stats.failureCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Check, null, tint = KitsugiColors.AccentGreen, modifier = Modifier.size(10.dp))
                            Text("${stats.successCount}", color = KitsugiColors.TextMuted, fontSize = 10.sp)
                            Icon(Icons.X, null, tint = KitsugiColors.AccentRed, modifier = Modifier.size(10.dp))
                            Text("${stats.failureCount}", color = KitsugiColors.TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
            // Sağlık badge'i ve etkin/devre dışı seçimi
            TvExtHealthBadge(health = health)
            Switch(
                checked = health != SourceHealthStatus.Disabled,
                onCheckedChange = { onToggle?.invoke(it) },
            )
            if (source.isBuiltIn) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.AccentBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Varsayılan", color = KitsugiColors.AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (errorCode != null) PlatformErrorCodeHint(code = errorCode)
            // Sil butonu (yalnızca harici kurulan eklentilerde)
            if (!source.isBuiltIn) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(KitsugiColors.AccentRed.copy(alpha = 0.1f))
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.keyCode in listOf(
                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER
                                )
                            ) { onDelete(); true } else false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Delete,
                        null,
                        tint = KitsugiColors.AccentRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        if (errorCode != null) {
            PlatformErrorExplanationBlock(
                code = errorCode,
                rawReason = rawReason,
                cooldownUntil = cooldownUntil,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

// ── HealthBadge ────────────────────────────────────────────────────────────

@Composable
private fun TvExtHealthBadge(health: SourceHealthStatus) {
    val (label, color) = when (health) {
        SourceHealthStatus.Healthy -> stringResource(R.string.manga_health_healthy) to KitsugiColors.AccentGreen
        SourceHealthStatus.Degraded -> stringResource(R.string.manga_health_degraded) to Color(0xFFFF9800)
        SourceHealthStatus.Broken -> stringResource(R.string.manga_health_broken) to KitsugiColors.AccentRed
        SourceHealthStatus.CaptchaRequired -> stringResource(R.string.manga_health_captcha) to Color(0xFFFF9800)
        SourceHealthStatus.RateLimited -> stringResource(R.string.manga_health_rate_limited) to Color(0xFFFF9800)
        SourceHealthStatus.Disabled -> stringResource(R.string.manga_health_disabled) to KitsugiColors.TextMuted
        SourceHealthStatus.Unknown -> stringResource(R.string.manga_health_unknown) to KitsugiColors.TextMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── ConfirmDeleteDialog ────────────────────────────────────────────────────

@Composable
private fun TvExtConfirmDeleteDialog(
    sourceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { confirmFocus.requestFocus() } catch (_: Exception) {}
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(KitsugiColors.SurfaceSoft)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.inline_remove_source_2ad0ff8),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                stringResource(R.string.inline_the_1_s_source_will_cac3e39, (sourceName).toString()),
                color = KitsugiColors.TextSecondary,
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TvExtDialogButton(label = stringResource(R.string.vazgec), color = KitsugiColors.TextMuted, onClick = onDismiss)
                TvExtDialogButton(
                    label = stringResource(R.string.kaldir),
                    color = KitsugiColors.AccentRed,
                    focusRequester = confirmFocus,
                    onClick = onConfirm
                )
            }
        }
    }
    }
}

@Composable
private fun TvExtDialogButton(
    label: String,
    color: Color,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    val mod = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Box(
        modifier = mod
            .clip(RoundedCornerShape(8.dp))
            .background(if (hasFocus) color.copy(alpha = 0.2f) else color.copy(alpha = 0.1f))
            .border(1.dp, if (hasFocus) color else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode in listOf(
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER
                    )
                ) { onClick(); true } else false
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
