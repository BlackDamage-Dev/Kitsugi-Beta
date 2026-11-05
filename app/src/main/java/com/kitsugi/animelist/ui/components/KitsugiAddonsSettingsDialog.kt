package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.net.Uri
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.local.CloudstreamRepoEntity
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.local.MangaSourceStateEntity
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.remote.CsPlugin
import com.kitsugi.animelist.ui.components.addons.CloudstreamExtensionsTab
import com.kitsugi.animelist.ui.components.addons.MangaBulkDeleteState
import com.kitsugi.animelist.ui.components.addons.MangaExtensionsTab
import com.kitsugi.animelist.ui.components.addons.StremioTab
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiAddonsSettingsDialog(
    addons: List<ManagedAddonEntity>,
    initialDebridToken: String,
    repos: List<CloudstreamRepoEntity>,
    repoPlugins: Map<String, List<CsPlugin>?>,
    repoLoadingState: Map<String, Boolean>,
    csPlugins: List<CsPluginEntity> = emptyList(),
    onAddAddon: (String) -> Unit,
    onToggleAddon: (ManagedAddonEntity, Boolean) -> Unit,
    onDeleteAddon: (ManagedAddonEntity) -> Unit,
    onSaveDebridToken: (String) -> Unit,
    onAddRepo: (String) -> Unit,
    onDeleteRepo: (CloudstreamRepoEntity) -> Unit,
    onFetchRepoPlugins: (String) -> Unit,
    onInstallPlugin: (CsPlugin, ((Boolean) -> Unit)?) -> Unit,
    onInstallAllPlugins: (repoUrl: String, repoName: String, plugins: List<CsPlugin>) -> Unit = { _, _, _ -> },
    onUpdateAllPlugins: (repoUrl: String, repoName: String, plugins: List<CsPlugin>) -> Unit = { _, _, _ -> },
    bulkInstallRepoUrl: String? = null,
    bulkInstallRepoName: String? = null,
    bulkInstallDone: Int = 0,
    bulkInstallTotal: Int = 0,
    bulkInstallCurrentName: String = "",
    bulkInstallResultMessage: String? = null,
    onClearBulkInstallResult: () -> Unit = {},
    onToggleCsPlugin: (CsPluginEntity, Boolean) -> Unit = { _, _ -> },
    onUninstallCsPlugin: (CsPluginEntity) -> Unit = {},
    onUninstallAllCsPlugins: () -> Unit = {},
    isCheckingUpdates: Boolean = false,
    availableUpdatesCount: Int = 0,
    onCheckForUpdates: () -> Unit = {},
    onUpdateAllPendingPlugins: () -> Unit = {},
    onRefreshRepo: ((String) -> Unit)? = null,
    // Manga extensions
    mangaSources: List<MangaSource> = emptyList(),
    onInstallMangaExtension: (Uri) -> Unit = {},
    onDeleteMangaExtension: (MangaSource) -> Unit = {},
    /** Kurulu tüm manga eklentilerini kaldırır (video eklentileriyle parite). */
    onUninstallAllMangaExtensions: () -> Unit = {},
    onGetMangaSourceEnabled: (MangaSource) -> Boolean = { true },
    onToggleMangaSource: (MangaSource, Boolean) -> Unit = { _, _ -> },
    /** Null → manga sekmesinde callback'siz "Toplu Sil" butonu çizilmez. */
    mangaBulkDelete: MangaBulkDeleteState? = null,
    // Manga repos (Keiyoushi / native)
    mangaRepos: List<String> = emptyList(),
    mangaRepoExtensions: Map<String, List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>?> = emptyMap(),
    mangaRepoLoadingState: Map<String, Boolean> = emptyMap(),
    onAddMangaRepo: (String) -> Unit = {},
    onDeleteMangaRepo: (String) -> Unit = {},
    onFetchMangaRepo: (String) -> Unit = {},
    onInstallMangaApk: (com.kitsugi.animelist.data.remote.MangaExtensionInfo, ((Boolean) -> Unit)?) -> Unit = { _, _ -> },
    onInstallAllMangaExtensions: (repoUrl: String, extensions: List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>) -> Unit = { _, _ -> },
    onUpdateAllMangaExtensions: (repoUrl: String, extensions: List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>) -> Unit = { _, _ -> },
    mangaBulkInstallRepoUrl: String? = null,
    mangaBulkInstallDone: Int = 0,
    mangaBulkInstallTotal: Int = 0,
    mangaBulkInstallCurrentName: String = "",
    /** versionCode karşılaştırması için (Mihon uyumlu) */
    onGetInstalledMangaVersionCode: (String) -> Long = { -1L },
    /** Görüntü amacıyla versionName metni */
    onGetInstalledMangaVersion: (String) -> String? = { null },
    onGetMangaSourceHealthStatus: (MangaSource) -> com.kitsugi.animelist.data.manga.model.SourceHealthStatus = { com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Unknown },
    onGetMangaSourceRuntimeStats: (MangaSource) -> com.kitsugi.animelist.data.manga.model.SourceRuntimeStats = { com.kitsugi.animelist.data.manga.model.SourceRuntimeStats() },
    onGetMangaConfiguredDomain: (MangaSource) -> String? = { null },
    onGetMangaConfiguredBaseUrl: (MangaSource) -> String = { it.baseUrl },
    onGetMangaSourceUserAgent: (MangaSource) -> String? = { null },
    onGetMangaSourceSlowdownEnabled: (MangaSource) -> Boolean = { false },
    onSetMangaSourceUserAgent: (MangaSource, String?) -> Unit = { _, _ -> },
    onSetMangaSourceSlowdownEnabled: (MangaSource, Boolean) -> Unit = { _, _ -> },
    onResetMangaSourceDiagnostics: (MangaSource) -> Unit = {},
    onClearAllMangaSourceDiagnostics: () -> Unit = {},
    onIsMangaSourceBusy: (MangaSource) -> Boolean = { false },
    onQuickCheckMangaSource: (MangaSource) -> Unit = {},
    onRefreshMangaSourceMirror: (MangaSource) -> Unit = {},
    onClearMangaSourceMirror: (MangaSource) -> Unit = {},
    onSetMangaSourceDomain: (MangaSource, String?) -> Unit = { _, _ -> },
    mangaSourceStateReport: List<MangaSourceStateEntity> = emptyList(),
    onOpenMangaSourceHealthScreen: () -> Unit = {},
    onForceCheckMangaUpdates: (onResult: (String) -> Unit) -> Unit = {},
    untrustedRepoToConfirm: String? = null,
    untrustedSignatureToConfirm: Pair<com.kitsugi.animelist.data.remote.MangaExtensionInfo, String>? = null,
    onConfirmUntrustedRepo: (String) -> Unit = {},
    onDismissUntrustedRepo: () -> Unit = {},
    onConfirmUntrustedSignature: (com.kitsugi.animelist.data.remote.MangaExtensionInfo, String) -> Unit = { _, _ -> },
    onDismissUntrustedSignature: () -> Unit = {},
    initialTab: Int = 0,
    onOpenDiagnostic: (() -> Unit)? = null,
    useGithubProxy: Boolean = false,
    onUseGithubProxyChanged: (Boolean) -> Unit = {},
    onExplorePlugin: ((String) -> Unit)? = null,
    onOpenPluginPicker: (() -> Unit)? = null,
    embeddedMode: Boolean = false,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    var webViewDialogUrl by remember { mutableStateOf<String?>(null) }
    var webViewDialogTitle by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 2), pageCount = { 3 })
    LaunchedEffect(initialTab) {
        val targetPage = initialTab.coerceIn(0, 2)
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }

    val stremioListState = rememberLazyListState()
    val cloudstreamListState = rememberLazyListState()
    val mangaListState = rememberLazyListState()

    val activeListState = when (pagerState.currentPage) {
        0 -> stremioListState
        1 -> cloudstreamListState
        else -> mangaListState
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true,
        embeddedMode = embeddedMode,
        innerScrollState = activeListState,
        sheetGesturesEnabled = false
    ) {
            // Header content (Title + Close button)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (embeddedMode) 4.dp else 8.dp)
            ) {
                if (!embeddedMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.eklenti_akis_ayarlari),
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.kaynaklari_ekleyin_guncelleyin_ve_yonetin),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Close, contentDescription = stringResource(R.string.kapat), tint = KitsugiColors.TextSecondary)
                        }
                    }
                }
                KitsugiSettingsSegmentedTabs(
                    tabs = listOf(
                        KitsugiSettingsTabOption(
                            stringResource(R.string.inline_torrent_streaming_89e2cac),
                            Icons.Bolt
                        ),
                        KitsugiSettingsTabOption(
                            stringResource(R.string.inline_video_providers_1e04646),
                            Icons.PlayCircle
                        ),
                        KitsugiSettingsTabOption(
                            stringResource(R.string.inline_manga_sources_03288ff),
                            Icons.AutoStories
                        )
                    ),
                    selectedIndex = pagerState.currentPage,
                    accentColor = accentColor,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }

            // Body content (HorizontalPager)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) { page ->
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Bulk install result banner ──────────────────────────────
                    if (bulkInstallResultMessage != null) {
                        val isError = bulkInstallResultMessage.startsWith("❌") || bulkInstallResultMessage.startsWith("⚠️")
                        val isInfo = bulkInstallResultMessage.startsWith("ℹ️")
                        val bannerColor = when {
                            isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                            isInfo -> accentColor.copy(alpha = 0.10f)
                            else -> KitsugiColors.AccentGreen.copy(alpha = 0.10f)
                        }
                        val textColor = when {
                            isError -> MaterialTheme.colorScheme.error
                            isInfo -> accentColor
                            else -> KitsugiColors.AccentGreen
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(bannerColor)
                                .border(1.dp, textColor.copy(alpha = 0.24f), RoundedCornerShape(13.dp))
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bulkInstallResultMessage,
                                color = textColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onClearBulkInstallResult,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Close,
                                    contentDescription = stringResource(R.string.kapat),
                                    tint = textColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    // Tab Content based on page index
                    when (page) {
                        0 -> StremioTab(
                            addons = addons,
                            initialDebridToken = initialDebridToken,
                            accentColor = accentColor,
                            onAddAddon = onAddAddon,
                            onToggleAddon = onToggleAddon,
                            onDeleteAddon = onDeleteAddon,
                            onSaveDebridToken = onSaveDebridToken,
                            listState = stremioListState
                        )
                        1 -> CloudstreamExtensionsTab(
                            repos = repos,
                            addons = addons,
                            csPlugins = csPlugins,
                            repoPlugins = repoPlugins,
                            repoLoadingState = repoLoadingState,
                            accentColor = accentColor,
                            onAddRepo = onAddRepo,
                            onDeleteRepo = onDeleteRepo,
                            onFetchRepoPlugins = onFetchRepoPlugins,
                            onInstallPlugin = onInstallPlugin,
                            onInstallAllPlugins = onInstallAllPlugins,
                            onUpdateAllPlugins = onUpdateAllPlugins,
                            bulkInstallRepoUrl = bulkInstallRepoUrl,
                            bulkInstallDone = bulkInstallDone,
                            bulkInstallTotal = bulkInstallTotal,
                            bulkInstallCurrentName = bulkInstallCurrentName,
                            onToggleCsPlugin = onToggleCsPlugin,
                            onUninstallCsPlugin = onUninstallCsPlugin,
                            onUninstallAllCsPlugins = onUninstallAllCsPlugins,
                            useGithubProxy = useGithubProxy,
                            onUseGithubProxyChanged = onUseGithubProxyChanged,
                            isCheckingUpdates = isCheckingUpdates,
                            availableUpdatesCount = availableUpdatesCount,
                            onCheckForUpdates = onCheckForUpdates,
                            onUpdateAllPendingPlugins = onUpdateAllPendingPlugins,
                            onRefreshRepo = onRefreshRepo,
                            onVerifyPlugin = { pluginId, pluginName ->
                                scope.launch(Dispatchers.IO) {
                                    val cs3File = java.io.File(context.filesDir, "cs_extensions/${pluginId}.cs3")
                                    var loadedPlugin = com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadedPlugins[cs3File.absolutePath] as? com.lagradost.cloudstream3.plugins.Plugin
                                    if (loadedPlugin == null) {
                                        com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadExtension(context, pluginId)
                                    }
                                    val api = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull { it.sourcePlugin == cs3File.absolutePath }
                                    val url = api?.mainUrl
                                    withContext(Dispatchers.Main) {
                                        if (!url.isNullOrBlank()) {
                                            webViewDialogUrl = url
                                            webViewDialogTitle = pluginName
                                        } else {
                                            android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.eklenti_adresi_bulunamadi), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onOpenDiagnostic = onOpenDiagnostic,
                            listState = cloudstreamListState,
                            onExplorePlugin = onExplorePlugin,
                            onOpenPluginPicker = onOpenPluginPicker
                        )
                        2 -> MangaExtensionsTab(
                            sources = mangaSources,
                            onVerifySource = { url, name ->
                                webViewDialogUrl = url
                                webViewDialogTitle = name
                            },
                            accentColor = accentColor,
                            onInstallExtension = onInstallMangaExtension,
                            onDeleteExtension = onDeleteMangaExtension,
                            onUninstallAllExtensions = onUninstallAllMangaExtensions,
                            onGetSourceEnabled = onGetMangaSourceEnabled,
                            onToggleSource = onToggleMangaSource,
                            repos = mangaRepos,
                            repoExtensions = mangaRepoExtensions,
                            repoLoadingState = mangaRepoLoadingState,
                            onAddRepo = onAddMangaRepo,
                            onDeleteRepo = onDeleteMangaRepo,
                            onFetchRepo = onFetchMangaRepo,
                            onInstallApk = onInstallMangaApk,
                            onInstallAllExtensions = onInstallAllMangaExtensions,
                            onUpdateAllExtensions = onUpdateAllMangaExtensions,
                            bulkInstallRepoUrl = mangaBulkInstallRepoUrl,
                            bulkInstallDone = mangaBulkInstallDone,
                            bulkInstallTotal = mangaBulkInstallTotal,
                            bulkInstallCurrentName = mangaBulkInstallCurrentName,
                            onGetInstalledVersionCode = onGetInstalledMangaVersionCode,
                            onGetInstalledVersion = onGetInstalledMangaVersion,
                            sourceStateReport = mangaSourceStateReport,
                            onGetSourceHealthStatus = onGetMangaSourceHealthStatus,
                            onGetSourceRuntimeStats = onGetMangaSourceRuntimeStats,
                            onGetConfiguredDomain = onGetMangaConfiguredDomain,
                            onGetConfiguredBaseUrl = onGetMangaConfiguredBaseUrl,
                            onSetSourceDomain = onSetMangaSourceDomain,
                            onGetSourceUserAgent = onGetMangaSourceUserAgent,
                            onGetSourceSlowdownEnabled = onGetMangaSourceSlowdownEnabled,
                            onSetSourceUserAgent = onSetMangaSourceUserAgent,
                            onSetSourceSlowdownEnabled = onSetMangaSourceSlowdownEnabled,
                            onResetSourceDiagnostics = onResetMangaSourceDiagnostics,
                            onClearAllSourceDiagnostics = onClearAllMangaSourceDiagnostics,
                            onIsSourceBusy = onIsMangaSourceBusy,
                            onQuickCheckSource = onQuickCheckMangaSource,
                            onRefreshSourceMirror = onRefreshMangaSourceMirror,
                            onClearSourceMirror = onClearMangaSourceMirror,
                            onOpenSourceHealthScreen = onOpenMangaSourceHealthScreen,
                            onForceCheckUpdates = onForceCheckMangaUpdates,
                            listState = mangaListState,
                            bulkDelete = mangaBulkDelete
                        )
                    }
                }
            }

            // Footer (Tamam button)
            // GÖMÜLÜ MOD: Ayarlar alt sayfasında zaten üstte geri oku vardır; burada ikinci
            // bir "Tamam" düğmesi (ve altında kalan boş şerit) gösterilmez.
            if (!embeddedMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_ok), color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
    }

    if (webViewDialogUrl != null) {
        KitsugiWebViewDialog(
            title = webViewDialogTitle ?: "",
            url = webViewDialogUrl!!,
            onDismiss = {
                webViewDialogUrl = null
                webViewDialogTitle = null
            }
        )
    }

    if (untrustedRepoToConfirm != null) {
        AlertDialog(
            onDismissRequest = onDismissUntrustedRepo,
            title = { Text(stringResource(R.string.guvenli_olmayan_kaynak), color = KitsugiColors.TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.eklemek_istediginiz_kaynak_guvenilir_eklenti_havuzlari_listesind, untrustedRepoToConfirm),
                    color = KitsugiColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmUntrustedRepo(untrustedRepoToConfirm) }) {
                    Text(stringResource(R.string.evet_ekle), color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUntrustedRepo) {
                    Text(stringResource(R.string.vazgec), color = KitsugiColors.TextSecondary)
                }
            },
            containerColor = KitsugiColors.SurfaceStrong
        )
    }

    if (untrustedSignatureToConfirm != null) {
        val (ext, hash) = untrustedSignatureToConfirm
        AlertDialog(
            onDismissRequest = onDismissUntrustedSignature,
            title = { Text(stringResource(R.string.guvenli_olmayan_eklenti_imzasi), color = KitsugiColors.TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.yuklemek_istediginiz_eklentinin_imzasi_guvenilir_imzalar_listesi, ext.name, ext.pkg, hash),
                    color = KitsugiColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmUntrustedSignature(ext, hash) }) {
                    Text(stringResource(R.string.guven_ve_yukle), color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUntrustedSignature) {
                    Text(stringResource(R.string.iptal_et), color = KitsugiColors.TextSecondary)
                }
            },
            containerColor = KitsugiColors.SurfaceStrong
        )
    }
}
