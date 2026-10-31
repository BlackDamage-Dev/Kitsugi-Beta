package com.kitsugi.animelist.ui.components.addons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import androidx.compose.foundation.lazy.itemsIndexed
import com.kitsugi.animelist.ui.components.KitsugiTonalButton
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiConfirmDialog

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint
import com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.tv.components.TvDialog
import androidx.compose.ui.platform.LocalClipboardManager
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

private const val ALL_LANG_FILTER = "__all__"
private enum class MangaTabSection { REPOS, INSTALLED }
private enum class MangaInstallState { IDLE, LOADING, SUCCESS, FAILURE }

@Composable
internal fun MangaExtensionsTab(
    sources: List<MangaSource>,
    accentColor: Color,
    onInstallExtension: (Uri) -> Unit,
    onDeleteExtension: (MangaSource) -> Unit,
    onDeleteExtensions: ((List<MangaSource>, (Int) -> Unit) -> Unit)? = null,
    onToggleSource: (MangaSource, Boolean) -> Unit = { _, _ -> },
    repos: List<String>,
    repoExtensions: Map<String, List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>?>,
    repoLoadingState: Map<String, Boolean>,
    onAddRepo: (String) -> Unit,
    onDeleteRepo: (String) -> Unit,
    onFetchRepo: (String) -> Unit,
    onInstallApk: (com.kitsugi.animelist.data.remote.MangaExtensionInfo, ((Boolean) -> Unit)?) -> Unit,
    onInstallAllExtensions: (String, List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>) -> Unit = { _, _ -> },
    onUpdateAllExtensions: (String, List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>) -> Unit = { _, _ -> },
    bulkInstallRepoUrl: String? = null,
    bulkInstallDone: Int = 0,
    bulkInstallTotal: Int = 0,
    bulkInstallCurrentName: String = "",
    /** Kurulu APK'nın versionCode'unu döndürür. Kurulu değilse -1L. */
    onGetInstalledVersionCode: (String) -> Long = { -1L },
    /** Kurulu APK'nın görüntü sürüm metnini döndürür (display için). */
    onGetInstalledVersion: (String) -> String? = { null },
    sourceStateReport: List<com.kitsugi.animelist.data.local.MangaSourceStateEntity> = emptyList(),
    onGetSourceHealthStatus: (MangaSource) -> com.kitsugi.animelist.data.manga.model.SourceHealthStatus = { com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Unknown },
    onGetSourceRuntimeStats: (MangaSource) -> com.kitsugi.animelist.data.manga.model.SourceRuntimeStats = { com.kitsugi.animelist.data.manga.model.SourceRuntimeStats() },
    onGetConfiguredDomain: (MangaSource) -> String? = { null },
    onGetConfiguredBaseUrl: (MangaSource) -> String = { it.baseUrl },
    onSetSourceDomain: (MangaSource, String?) -> Unit = { _, _ -> },
    onGetSourceUserAgent: (MangaSource) -> String? = { null },
    onGetSourceSlowdownEnabled: (MangaSource) -> Boolean = { false },
    onSetSourceUserAgent: (MangaSource, String?) -> Unit = { _, _ -> },
    onSetSourceSlowdownEnabled: (MangaSource, Boolean) -> Unit = { _, _ -> },
    onResetSourceDiagnostics: (MangaSource) -> Unit = {},
    onClearAllSourceDiagnostics: () -> Unit = {},
    onIsSourceBusy: (MangaSource) -> Boolean = { false },
    onQuickCheckSource: (MangaSource) -> Unit = {},
    onRefreshSourceMirror: (MangaSource) -> Unit = {},
    onClearSourceMirror: (MangaSource) -> Unit = {},
    onOpenSourceHealthScreen: () -> Unit = {},
    onForceCheckUpdates: (onResult: (String) -> Unit) -> Unit = {},
    onVerifySource: ((String, String) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    /** Null ise toplu silme butonu hiç çizilmez (callback'siz ölü buton yok). */
    bulkDelete: MangaBulkDeleteState? = null,
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onInstallExtension(it) } }

    var activeSection by rememberSaveable { mutableStateOf(MangaTabSection.REPOS) }
    var deleteTarget by remember { mutableStateOf<MangaSource?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var manageTarget by remember { mutableStateOf<MangaSource?>(null) }
    var reportOpen by remember { mutableStateOf(false) }
    var reportFilter by rememberSaveable { mutableStateOf("ALL") }
    var reportSort by rememberSaveable { mutableStateOf("UPDATED") }
    var newRepoUrl by rememberSaveable { mutableStateOf("") }
    var expandedRepo by rememberSaveable { mutableStateOf<String?>(null) }
    var langFilter by rememberSaveable { mutableStateOf(ALL_LANG_FILTER) }
    val installStates = remember { mutableStateMapOf<String, MangaInstallState>() }
    val installedSourceByKey = remember(sources) { sources.associateBy { it.stableSourceKey() } }
    val sourceStateByKey = remember(sourceStateReport) { sourceStateReport.associateBy { it.sourceKey } }
    // Oto-güncelleme durumu
    var autoUpdateRunning by remember { mutableStateOf(false) }
    var autoUpdateResult by remember { mutableStateOf<String?>(null) }
    val autoUpdateChecking = stringResource(R.string.manga_auto_update_checking)

    // Seçili dil filtresine göre repo'nun extensionlarını filtreler
    fun filteredExtensionsForBulk(plugins: List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>): List<com.kitsugi.animelist.data.remote.MangaExtensionInfo> {
        return if (langFilter == ALL_LANG_FILTER) plugins
        else plugins.filter { it.lang == langFilter }
    }

    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        try {
            val text = clipboardManager.getText()?.text?.trim() ?: ""
            if (text.isNotBlank()) {
                val isRepoUrl = text.startsWith("http://") || text.startsWith("https://") || text.contains("raw.githubusercontent.com")
                if (isRepoUrl && newRepoUrl.isBlank()) {
                    newRepoUrl = text
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MangaExtensionsTab", "Failed to auto-read clipboard", e)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Bilgilendirme şeridi (Eklentiler sayfasının ortak bileşeni) ────
        item {
            AddonInfoBanner(
                accentColor = accentColor,
                text = KitsugiStrings.get(R.string.manga_kaynaklari_mihon_tachiyomi_mex_eklentileridir_once_repolar)
            )
        }

        // Repo / kurulu kaynaklar için Cloudstream ile ortak, tema uyumlu seçici.
        item {
            AddonSubsectionTabs(
                tabs = listOf(
                    AddonSubsectionTab(stringResource(R.string.addon_repositories_count, repos.size), Icons.Language),
                    AddonSubsectionTab(stringResource(R.string.addon_installed_count, sources.size), Icons.Extension)
                ),
                selectedIndex = if (activeSection == MangaTabSection.REPOS) 0 else 1,
                accentColor = accentColor,
                onSelect = { activeSection = if (it == 0) MangaTabSection.REPOS else MangaTabSection.INSTALLED }
            )
        }

        if (activeSection == MangaTabSection.REPOS) {
            // Repo Ekle Kartı
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        KitsugiStrings.get(R.string.manga_repo_ekle),
                        color = KitsugiColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    // Kitsugi Manga Plugins (Resmi Varsayılan Depo) hızlı ekle
                    val kitsugiMangaUrl = com.kitsugi.animelist.data.remote.MangaRepoClient.KITSUGI_MANGA_REPO_URL
                    val hasKitsugiMangaRepo = repos.any {
                        com.kitsugi.animelist.data.remote.MangaRepoClient.isKitsugiMangaRepoUrl(it)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .tvClickable(shape = RoundedCornerShape(10.dp)) {
                                if (!hasKitsugiMangaRepo) onAddRepo(kitsugiMangaUrl)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kitsugi Manga Plugins (Önerilen)", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Varsayılan depo: Kitsugi Manga Plugins · eklentileri buradan kur", color = KitsugiColors.TextMuted, fontSize = 10.sp)
                        }
                        Text(
                            if (hasKitsugiMangaRepo) stringResource(R.string.addon_status_added) else KitsugiStrings.get(R.string.ekle_64b2d35),
                            color = if (hasKitsugiMangaRepo) KitsugiColors.TextMuted else accentColor,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }

                    // Keiyoushi hızlı ekle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.08f))
                            .tvClickable(shape = RoundedCornerShape(10.dp)) {
                                val url = com.kitsugi.animelist.data.remote.MangaRepoClient.KEIYOUSHI_INDEX_URL
                                if (!repos.contains(url)) onAddRepo(url)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Language,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.manga_keiyoushi_official_repo), color = accentColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(KitsugiStrings.get(R.string.inline_1_354_mihon_extensions_78_72ff2f1), color = KitsugiColors.TextMuted, fontSize = 10.sp)
                        }
                        Text(
                            if (repos.contains(com.kitsugi.animelist.data.remote.MangaRepoClient.KEIYOUSHI_INDEX_URL)) stringResource(R.string.addon_status_added) else KitsugiStrings.get(R.string.ekle_64b2d35),
                            color = if (repos.contains(com.kitsugi.animelist.data.remote.MangaRepoClient.KEIYOUSHI_INDEX_URL)) KitsugiColors.TextMuted else accentColor,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }

                    // Kotatsu-Redo (Futon) built-in kaynakları kartı
                    val kotatsuColor = accentColor
                    var kotatsuActiveLangs by remember {
                        mutableStateOf(com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.getActiveLangs())
                    }
                    val kotatsuAllLangs = remember {
                        val fromCatalog = com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.getAllLangs()
                        // Katalog henüz yüklenmemişse yaygın diller göster (TR her zaman ilk)
                        if (fromCatalog.isEmpty())
                            listOf("tr", "en", "ko", "ja", "zh", "es", "pt", "fr", "de", "ru")
                        else fromCatalog.take(12)
                    }
                    val kotatsuTotal = remember {
                        val c = com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.getSourceCount()
                        if (c > 0) c else 1300
                    }
                    val kotatsuActiveCount = remember(kotatsuActiveLangs) {
                        com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.getActiveSourceCount()
                            .let { if (it > 0) it else kotatsuActiveLangs.size * 74 }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(kotatsuColor.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Başlık satırı
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoAwesome,
                                contentDescription = null,
                                tint = kotatsuColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "🤖 Kotatsu-Redo (Futon)",
                                    color = kotatsuColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    KitsugiStrings.get(R.string.inline_1_s_sources_2_s_7f963f9, kotatsuTotal, kotatsuActiveLangs.size, kotatsuActiveCount),
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            // Aktif sayacı badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(kotatsuColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    stringResource(R.string.manga_status_automatic),
                                    color = kotatsuColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Dil seçici chip'ler
                        Text(
                            KitsugiStrings.get(R.string.inline_active_languages_only_sources_in_06e8c48),
                            color = KitsugiColors.TextMuted,
                            fontSize = 9.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            kotatsuAllLangs.forEach { lang ->
                                val isActive = lang in kotatsuActiveLangs
                                val langEmoji = when (lang) {
                                    "tr" -> "🇹🇷"
                                    "en" -> "🇬🇧"
                                    "ko" -> "🇰🇷"
                                    "ja" -> "🇯🇵"
                                    "zh" -> "🇨🇳"
                                    "es" -> "🇪🇸"
                                    "pt" -> "🇧🇷"
                                    "fr" -> "🇫🇷"
                                    "de" -> "🇩🇪"
                                    "ru" -> "🇷🇺"
                                    "be" -> "🇧🇾"
                                    "bg" -> "🇧🇬"
                                    "bn" -> "🇧🇩"
                                    "ar" -> "🇸🇦"
                                    "id" -> "🇮🇩"
                                    "it" -> "🇮🇹"
                                    "pl" -> "🇵🇱"
                                    "uk" -> "🇺🇦"
                                    "vi" -> "🇻🇳"
                                    "th" -> "🇹🇭"
                                    "mn" -> "🇲🇳"
                                    "ms" -> "🇲🇾"
                                    "hi" -> "🇮🇳"
                                    "fa" -> "🇮🇷"
                                    "ro" -> "🇷🇴"
                                    "cs" -> "🇨🇿"
                                    "sk" -> "🇸🇰"
                                    "hu" -> "🇭🇺"
                                    "el" -> "🇬🇷"
                                    "nl" -> "🇳🇱"
                                    "sv" -> "🇸🇪"
                                    "da" -> "🇩🇰"
                                    "fi" -> "🇫🇮"
                                    "nb" -> "🇳🇴"
                                    "az" -> "🇦🇿"
                                    "kk" -> "🇰🇿"
                                    "uz" -> "🇺🇿"
                                    "ky" -> "🇰🇬"
                                    "ka" -> "🇬🇪"
                                    "hy" -> "🇦🇲"
                                    "my" -> "🇲🇲"
                                    "km" -> "🇰🇭"
                                    "lo" -> "🇱🇦"
                                    "si" -> "🇱🇰"
                                    "ne" -> "🇳🇵"
                                    "ur" -> "🇵🇰"
                                    "he" -> "🇮🇱"
                                    "sr" -> "🇷🇸"
                                    "hr" -> "🇭🇷"
                                    else -> lang.uppercase().map { c ->
                                        String(Character.toChars(0x1F1E6 + (c.code - 'A'.code)))
                                    }.joinToString("")
                                }
                                val chipBg = if (isActive)
                                    kotatsuColor.copy(alpha = 0.22f)
                                else
                                    KitsugiColors.SurfaceStrong
                                val chipBorder = if (isActive) kotatsuColor else KitsugiColors.Border.copy(alpha = 0.4f)
                                val chipTextColor = if (isActive) kotatsuColor else KitsugiColors.TextSecondary

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(chipBg)
                                        .border(1.dp, chipBorder, RoundedCornerShape(50.dp))
                                        .tvClickable(shape = RoundedCornerShape(50.dp)) {
                                            com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.toggleLang(lang)
                                            kotatsuActiveLangs = com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.getActiveLangs()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = langEmoji,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = lang.uppercase(),
                                            color = chipTextColor,
                                            fontSize = 11.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Manuel URL girişi
                    OutlinedTextField(
                        value = newRepoUrl,
                        onValueChange = { newRepoUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://raw.githubusercontent.com/.../index.min.json", color = KitsugiColors.TextMuted, fontSize = 11.sp) },
                        label = { Text(stringResource(R.string.manga_repo_url), color = KitsugiColors.TextSecondary, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = KitsugiColors.Border,
                            focusedTextColor = KitsugiColors.TextPrimary,
                            unfocusedTextColor = KitsugiColors.TextPrimary
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        trailingIcon = {
                            if (newRepoUrl.isNotBlank()) {
                                IconButton(onClick = {
                                    onAddRepo(newRepoUrl.trim())
                                    newRepoUrl = ""
                                }) {
                                    Icon(Icons.Add, null, tint = accentColor, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    )
                }
            }

            // Repo Listesi
            if (repos.isEmpty()) {
                item {
                    AddonEmptyCard(
                        icon = Icons.CloudOff,
                        text = KitsugiStrings.get(R.string.henuz_repo_eklenmedi_keiyoushi_yi_hizlica_eklemek_icin_yukaridak)
                    )
                }
            }

            repos.distinct().forEachIndexed { repoIndex, repoUrl ->
                val repoPlugins = repoExtensions[repoUrl]
                val isLoading = repoLoadingState[repoUrl] == true
                val isExpanded = expandedRepo == repoUrl
                val repoName = com.kitsugi.animelist.data.remote.MangaRepoClient.displayNameForRepo(repoUrl)

                val totalCount = repoPlugins?.size ?: 0
                val installedCount = repoPlugins?.count { ext ->
                    sources.any { (it.pkgName.isNotEmpty() && it.pkgName == ext.pkg) || it.name == ext.name }
                } ?: 0

                // Repo'daki eklentilerin güncelleme durumu:
                // versionCode karşılaştırması — Mihon'daki hasUpdatedVer mantığı
                val hasUpdates = repoPlugins?.any { ext ->
                    val localCode = onGetInstalledVersionCode(ext.pkg)
                    localCode >= 0L && ext.versionCode.toLong() > localCode
                } ?: false

                item(key = "repo_idx_$repoIndex") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(KitsugiColors.SurfaceSoft)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvClickable(shape = RoundedCornerShape(14.dp)) {
                                    if (isExpanded) expandedRepo = null
                                    else {
                                        expandedRepo = repoUrl
                                        if (repoPlugins == null) onFetchRepo(repoUrl)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Storage, null, tint = accentColor, modifier = Modifier.size(18.dp)) }
                            Column(modifier = Modifier.weight(1f)) {
                                FlatName(repoName)
                                Text(
                                    if (repoPlugins != null) {
                                        if (installedCount == totalCount && totalCount > 0) "✓ $installedCount/$totalCount ${stringResource(R.string.addon_status_installed)}"
                                        else "$installedCount/$totalCount ${stringResource(R.string.addon_status_installed)}"
                                    } else stringResource(R.string.addon_status_not_loaded),
                                    color = if (installedCount == totalCount && totalCount > 0) Color(0xFF4CAF50) else KitsugiColors.TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (installedCount == totalCount && totalCount > 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            if (isLoading) KitsugiPlasmaLoader(size = 18.dp)
                            else if (repoPlugins == null) {
                                Icon(Icons.Refresh, null, tint = accentColor, modifier = Modifier.size(18.dp).tvClickable(shape = RoundedCornerShape(999.dp)) { onFetchRepo(repoUrl) })
                            } else {
                                val isBulkInstalling = bulkInstallRepoUrl == repoUrl
                                if (hasUpdates && !isBulkInstalling) {
                                    IconButton(
                                        onClick = { onUpdateAllExtensions(repoUrl, repoPlugins) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.ArrowCircleUp, stringResource(R.string.inline_update_e9a7033), tint = accentColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (!isBulkInstalling && installedCount < totalCount) {
                                    // TR hızlı indirme butonu (Keiyoushi repo ise göster)
                                    val trCount = repoPlugins.count { it.lang == "tr" }
                                    val trInstalledCount = repoPlugins.count { ext ->
                                        ext.lang == "tr" && sources.any { it.name == ext.name }
                                    }
                                    if (trCount > 0 && trInstalledCount < trCount) {
                                        IconButton(
                                            onClick = {
                                                val trOnly = repoPlugins.filter { it.lang == "tr" }
                                                onInstallAllExtensions(repoUrl, trOnly)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("🇹🇷", fontSize = 14.sp)
                                            }
                                        }
                                    }
                                    // Aktif dil filtresine göre toplu indirme
                                    val bulkList = filteredExtensionsForBulk(repoPlugins)
                                    val bulkLabel = if (langFilter == ALL_LANG_FILTER) {
                stringResource(R.string.manga_bulk_download_all)
            } else {
                stringResource(R.string.manga_bulk_download_language, langFilter.uppercase())
            }
                                    IconButton(
                                        onClick = { onInstallAllExtensions(repoUrl, bulkList) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.DownloadForOffline, bulkLabel, tint = accentColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            KitsugiChevronIcon(
                                expanded = isExpanded,
                                contentDescription = null,
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            IconButton(onClick = { onDeleteRepo(repoUrl) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.DeleteForever, null, tint = KitsugiColors.AccentRed.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            }
                        }

                        val isBulkInstalling = bulkInstallRepoUrl == repoUrl
                        if (isBulkInstalling && bulkInstallTotal > 0) {
                            val fraction = bulkInstallDone.toFloat() / bulkInstallTotal.toFloat()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(accentColor.copy(alpha = 0.08f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.addon_status_installing), color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("$bulkInstallDone / $bulkInstallTotal", color = KitsugiColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                                        Text(text = bulkInstallCurrentName, color = KitsugiColors.TextSecondary, fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                    color = accentColor,
                                    trackColor = accentColor.copy(alpha = 0.15f)
                                )
                            }
                        }

                        if (isExpanded && repoPlugins == null && !isLoading) {
                            HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.3f))
                            Text(
                                text = if (com.kitsugi.animelist.data.remote.MangaRepoClient.isKitsugiMangaRepoUrl(repoUrl))
                                    "Kitsugi Manga Plugins deposunda yayınlanmış index.json bulunamadı. Eklentilerin listelenmesi/kurulması için repo builds dalında index.json ve APK çıktıları yayınlanmalı."
                                else "Repo kataloğu alınamadı. Adresi ve ağ bağlantısını kontrol edip yeniden deneyin.",
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }

                        if (isExpanded && repoPlugins != null) {
                            HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.3f))

                            val allLangs = remember(repoPlugins) {
                                val langs = repoPlugins.map { it.lang }.distinct().sorted()
                                // TR'yi her zaman öne al
                                val prioritized = mutableListOf(ALL_LANG_FILTER)
                                if (langs.contains("tr")) prioritized.add("tr")
                                prioritized.addAll(langs.filter { it != "tr" })
                                prioritized
                            }
                            // Dil filtreleri - yatay kaydırmalı Row (LazyRow LazyColumn içinde crash verir)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                allLangs.forEach { l ->
                                    FilterChip(
                                        text = when {
                                            l == ALL_LANG_FILTER -> stringResource(R.string.notif_filter_all)
                                            l == "all" -> "Global"
                                            else -> l.uppercase()
                                        },
                                        selected = langFilter == l,
                                        onClick = { langFilter = l },
                                        accentColor = if (l == "tr") Color(0xFFE53935) else accentColor
                                    )
                                }
                            }

                            // Seçilen dile göre filtrele + kurulmamış olanları öne al
                            val filtered = remember(repoPlugins, langFilter) {
                                val base = if (langFilter == ALL_LANG_FILTER) repoPlugins
                                else repoPlugins.filter { it.lang == langFilter }
                                // Kurulmamışları üste al
                                val notInstalled = base.filter { ext -> sources.none { (it.pkgName.isNotEmpty() && it.pkgName == ext.pkg) || it.name == ext.name } }
                                val installed = base.filter { ext -> sources.any { (it.pkgName.isNotEmpty() && it.pkgName == ext.pkg) || it.name == ext.name } }
                                notInstalled + installed
                            }

                            filtered.forEach { ext ->
                                val installState = installStates[ext.pkg] ?: MangaInstallState.IDLE
                                val isInstalled = sources.any { (it.pkgName.isNotEmpty() && it.pkgName == ext.pkg) || it.name == ext.name }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!ext.iconUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ext.iconUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(accentColor.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                ext.lang.uppercase().take(2),
                                                color = accentColor, fontWeight = FontWeight.Bold, fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(ext.name, color = KitsugiColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (ext.isNsfw) Box(
                                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(KitsugiColors.AccentRed.copy(alpha = 0.2f)).padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) { Text("18+", color = KitsugiColors.AccentRed, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                                        }
                                        Text("v${ext.version} · ${if (ext.lang == "all") "Global" else ext.lang.uppercase()}", color = KitsugiColors.TextMuted, fontSize = 10.sp)
                                    }
                                    when (installState) {
                                        MangaInstallState.LOADING -> KitsugiPlasmaLoader(size = 20.dp)
                                        MangaInstallState.SUCCESS -> Icon(Icons.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                        MangaInstallState.FAILURE -> Icon(Icons.Error, null, tint = KitsugiColors.AccentRed, modifier = Modifier.size(20.dp))
                                        MangaInstallState.IDLE -> {
                                            if (isInstalled) {
                                                // versionCode karşılaştırması (Mihon'daki hasUpdatedVer)
                                                val localCode = onGetInstalledVersionCode(ext.pkg)
                                                val isBuiltInSource = sources.any { it.isBuiltIn && it.pkgName.equals(ext.pkg, ignoreCase = true) }
                                                val hasUpdate = !isBuiltInSource && localCode >= 0L && ext.versionCode.toLong() > localCode
                                                if (hasUpdate) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(accentColor.copy(alpha = 0.14f))
                                                            .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                                                installStates[ext.pkg] = MangaInstallState.LOADING
                                                                onInstallApk(ext) { success ->
                                                                    installStates[ext.pkg] = if (success) MangaInstallState.SUCCESS else MangaInstallState.FAILURE
                                                                }
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) { Text(KitsugiStrings.get(R.string.inline_update_e9a7033), color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                                } else {
                                                    Box(
                                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(KitsugiColors.SurfaceStrong).padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) { Text(stringResource(R.string.addon_status_installed), color = KitsugiColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(accentColor.copy(alpha = 0.15f))
                                                        .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                                            installStates[ext.pkg] = MangaInstallState.LOADING
                                                            onInstallApk(ext) { success ->
                                                                installStates[ext.pkg] = if (success) MangaInstallState.SUCCESS else MangaInstallState.FAILURE
                                                            }
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) { Text(stringResource(R.string.addon_action_install), color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Manuel APK yükleme
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .tvClickable(shape = RoundedCornerShape(14.dp)) { filePickerLauncher.launch("*/*") }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.FolderOpen, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(KitsugiStrings.get(R.string.inline_install_from_file_mex_apk_3efa4d4), color = accentColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(KitsugiStrings.get(R.string.inline_select_an_extension_file_from_816e4e2), color = KitsugiColors.TextMuted, fontSize = 10.sp)
                    }
                    Icon(Icons.ChevronRight, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // KURULU KAYNAKLAR BÖLÜMÜ
        if (activeSection == MangaTabSection.INSTALLED) {
            // ── Otomatik Güncelleme Kartı ─────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (autoUpdateRunning) {
                                KitsugiPlasmaLoader(size = 18.dp)
                            } else {
                                Icon(Icons.CloudSync, null, tint = accentColor, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                KitsugiStrings.get(R.string.keiyoushi_den_oto_guncelle),
                                color = KitsugiColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                autoUpdateResult ?: KitsugiStrings.get(R.string.ui_auto_update_daily),
                                color = if (autoUpdateResult?.startsWith("✅") == true) Color(0xFF4CAF50)
                                        else if (autoUpdateResult?.startsWith("❌") == true) androidx.compose.ui.graphics.Color(0xFFEF5350)
                                        else KitsugiColors.TextMuted,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        KitsugiButton(
                            onClick = {
                                if (!autoUpdateRunning) {
                                    autoUpdateRunning = true
                                    autoUpdateResult = autoUpdateChecking
                                    onForceCheckUpdates { result ->
                                        autoUpdateRunning = false
                                        autoUpdateResult = result
                                    }
                                }
                            },
                            enabled = !autoUpdateRunning
                        ) {
                            Text(
                                if (autoUpdateRunning) stringResource(R.string.manga_auto_update_check_button) else KitsugiStrings.get(R.string.inline_update_now_f5b0013),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            // ── Toplu Sil (her zaman tıklanabilir) ──────────────────────────────
            // Eski sürümde buton ya devre dışıydı ya da yalnızca yerleşik Kotatsu
            // kaynakları için `deleteExtension` çağırıyordu; her çağrı false döndüğü
            // için kullanıcı "basılmıyor" sanıyordu. Artık buton daima aktif, basınca
            // onay açılır ve işlemin sonucu mutlaka bildirilir.
            if (bulkDelete != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(KitsugiColors.SurfaceSoft)
                            .border(
                                1.dp,
                                KitsugiColors.AccentRed.copy(alpha = 0.35f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.manga_bulk_delete_action),
                                color = KitsugiColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                stringResource(R.string.manga_bulk_delete_hint, sources.size),
                                color = KitsugiColors.TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        // `enabled = false` BİLEREK kullanılmaz: silinecek kaynak kalmasa
                        // bile buton basılır ve nedenini anlatan sonuç mesajı gösterilir.
                        KitsugiButton(
                            onClick = { showBulkDeleteConfirm = true },
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KitsugiColors.AccentRed,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                if (bulkDelete.isDeleting) stringResource(R.string.manga_bulk_delete_progress)
                                else stringResource(R.string.manga_bulk_delete_action),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (bulkDelete.hiddenBuiltInCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        KitsugiTonalButton(
                            onClick = bulkDelete.onRestoreBuiltIns,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Restore,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                stringResource(
                                    R.string.manga_bulk_restore_action,
                                    bulkDelete.hiddenBuiltInCount
                                ),
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Sağlık Raporu Başlığı ─────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        KitsugiStrings.get(R.string.inline_source_health_report_4997e28),
                        color = KitsugiColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { reportOpen = true }) {
                            Text("Rapor", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onOpenSourceHealthScreen) {
                            Text("Tam Ekran", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (sources.isEmpty()) {
                // Eklentiler sayfasının ortak boş-durum kartı (bkz. StremioAddonTab.kt).
                item {
                    AddonEmptyCard(
                        icon = Icons.MenuBook,
                        text = KitsugiStrings.get(R.string.yuklu_manga_eklentisi_yok_repolar_sekmesinden_eklenti_kurabilirs)
                    )
                }
            }
            itemsIndexed(sources, key = { _, src -> "${src.name}_${src.lang}" }) { _, source ->
                val health = onGetSourceHealthStatus(source)
                val isBusy = onIsSourceBusy(source)
                val domain = onGetConfiguredDomain(source)
                val sourceState = sourceStateByKey[source.stableSourceKey()]
                val errorCode = PlatformErrorCode.fromMangaState(sourceState?.lastErrorCode, sourceState?.lastErrorType)?.id
                val isEnabled = health != com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Disabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceSoft)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(source.lang.uppercase().take(2), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            source.name,
                            color = if (isEnabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(onGetConfiguredBaseUrl(source).ifBlank { "Mihon APK" }, color = KitsugiColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            HealthBadge(health = health, accentColor = accentColor)
                            if (source.isBuiltIn) {
                                TinyInfoChip(text = "Varsayılan", tint = accentColor)
                            }
                            if (!domain.isNullOrBlank()) {
                                TinyInfoChip(text = domain, tint = accentColor)
                            }
                        }
                        if (errorCode != null) {
                            PlatformErrorCodeHint(code = errorCode)
                        }
                    }
                    // ── AÇ/KAPA SWITCH ──────────────────────────────────────────────
                    if (isBusy) {
                        KitsugiPlasmaLoader(size = 22.dp)
                    } else {
                        Switch(
                            checked = isEnabled,
                            enabled = !isBusy,
                            onCheckedChange = { enabled -> onToggleSource(source, enabled) },
                        )
                    }
                    IconButton(
                        onClick = {
                            val url = onGetConfiguredBaseUrl(source).ifBlank { source.baseUrl }
                            onVerifySource?.invoke(url, source.name)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Language, stringResource(R.string.manga_source_verify), tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { manageTarget = source }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Tune, stringResource(R.string.manga_source_manage), tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    if (!source.isBuiltIn) {
                        IconButton(onClick = { deleteTarget = source }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.DeleteForever, "Sil", tint = KitsugiColors.AccentRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (reportOpen) {
        val filteredReport = remember(sourceStateReport, reportFilter, reportSort) {
            val filtered = sourceStateReport.filter {
                reportFilter == "ALL" || it.healthStatus.equals(reportFilter, ignoreCase = true)
            }
            when (reportSort) {
                "FAILURES" -> filtered.sortedWith(
                    compareByDescending<com.kitsugi.animelist.data.local.MangaSourceStateEntity> { it.failureCount }
                        .thenByDescending { it.updatedAt }
                )
                "LATENCY" -> filtered.sortedWith(
                    compareByDescending<com.kitsugi.animelist.data.local.MangaSourceStateEntity> { it.avgImageMs + it.avgSearchMs }
                        .thenByDescending { it.updatedAt }
                )
                else -> filtered.sortedByDescending { it.updatedAt }
            }
        }
        val healthyCount = remember(sourceStateReport) { sourceStateReport.count { it.healthStatus.equals("Healthy", ignoreCase = true) } }
        val degradedCount = remember(sourceStateReport) { sourceStateReport.count { it.healthStatus.equals("Degraded", ignoreCase = true) } }
        val brokenCount = remember(sourceStateReport) { sourceStateReport.count { it.healthStatus.equals("Broken", ignoreCase = true) } }
        val avgSuccessRate = remember(sourceStateReport) {
            if (sourceStateReport.isEmpty()) "—" else {
                val rates = sourceStateReport.map {
                    val total = it.successCount + it.failureCount
                    if (total <= 0) 0.0 else (it.successCount * 100.0 / total)
                }
                "${"%.0f".format(rates.average())}%"
            }
        }
        AlertDialog(
            onDismissRequest = { reportOpen = false },
            containerColor = KitsugiColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.manga_report_title), color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(text = stringResource(R.string.tumu), selected = reportFilter == "ALL", onClick = { reportFilter = "ALL" }, accentColor = accentColor)
                        FilterChip(text = "Healthy", selected = reportFilter == "Healthy", onClick = { reportFilter = "Healthy" }, accentColor = accentColor)
                        FilterChip(text = "Degraded", selected = reportFilter == "Degraded", onClick = { reportFilter = "Degraded" }, accentColor = accentColor)
                        FilterChip(text = "Broken", selected = reportFilter == "Broken", onClick = { reportFilter = "Broken" }, accentColor = accentColor)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(text = "Captcha", selected = reportFilter == "CaptchaRequired", onClick = { reportFilter = "CaptchaRequired" }, accentColor = accentColor)
                        FilterChip(text = "429", selected = reportFilter == "RateLimited", onClick = { reportFilter = "RateLimited" }, accentColor = accentColor)
                        FilterChip(text = "Unknown", selected = reportFilter == "Unknown", onClick = { reportFilter = "Unknown" }, accentColor = accentColor)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(text = KitsugiStrings.get(R.string.guncel), selected = reportSort == "UPDATED", onClick = { reportSort = "UPDATED" }, accentColor = accentColor)
                        FilterChip(text = stringResource(R.string.hata), selected = reportSort == "FAILURES", onClick = { reportSort = "FAILURES" }, accentColor = accentColor)
                        FilterChip(text = stringResource(R.string.manga_health_latency), selected = reportSort == "LATENCY", onClick = { reportSort = "LATENCY" }, accentColor = accentColor)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryStatCard(label = "Healthy", value = healthyCount.toString(), tint = Color(0xFF4CAF50), modifier = Modifier.weight(1f))
                        SummaryStatCard(label = "Degraded", value = degradedCount.toString(), tint = Color(0xFFFFB300), modifier = Modifier.weight(1f))
                        SummaryStatCard(label = "Broken", value = brokenCount.toString(), tint = KitsugiColors.AccentRed, modifier = Modifier.weight(1f))
                    }
                    SummaryStatCard(label = KitsugiStrings.get(R.string.ortalama_basari), value = avgSuccessRate, tint = accentColor, modifier = Modifier.fillMaxWidth())

                    if (filteredReport.isEmpty()) {
                        Text(KitsugiStrings.get(R.string.inline_no_source_health_data_is_4dde44f), color = KitsugiColors.TextSecondary)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            items(filteredReport, key = { it.sourceKey }) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(KitsugiColors.SurfaceSoft)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(item.sourceName, color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TinyInfoChip(text = item.lang.uppercase(), tint = accentColor)
                                        TinyInfoChip(text = item.healthStatus, tint = accentColor)
                                    }
                                    SourceStatLine("Active domain", item.activeDomain ?: "—")
                                    SourceStatLine("Success / Failure", "${item.successCount} / ${item.failureCount}")
                                    SourceStatLine(stringResource(R.string.manga_success_rate), formatSuccessRate(item.successCount, item.failureCount))
                                    TinyInfoChip(text = "Trend", tint = trendTint(item.successCount, item.failureCount))
                                    SourceStatLine(stringResource(R.string.manga_last_checked), formatTimestamp(item.lastCheckedAt))
                                    SourceStatLine("Search / Image", "${formatMs(item.avgSearchMs)} / ${formatMs(item.avgImageMs)}")
                                    val errorCode = PlatformErrorCode.fromMangaState(item.lastErrorCode, item.lastErrorType)?.id
                                    if (errorCode != null) {
                                        PlatformErrorExplanationBlock(
                                            code = errorCode,
                                            rawReason = item.lastReason,
                                            cooldownUntil = item.cooldownUntil,
                                        )
                                    } else if (!item.lastReason.isNullOrBlank()) {
                                        SourceStatLine(stringResource(R.string.manga_last_reason), item.lastReason)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClearAllSourceDiagnostics) {
                        Text(stringResource(R.string.raporu_temizle), color = KitsugiColors.AccentRed, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { reportOpen = false }) {
                        Text(stringResource(R.string.kapat), color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    manageTarget?.let { src ->
        val health = onGetSourceHealthStatus(src)
        val stats = onGetSourceRuntimeStats(src)
        val configuredDomain = onGetConfiguredDomain(src)
        val configuredBaseUrl = onGetConfiguredBaseUrl(src)
        val isBusy = onIsSourceBusy(src)
        var editedDomain by remember(src) { mutableStateOf(configuredDomain.orEmpty()) }
        var editedUserAgent by remember(src) { mutableStateOf(onGetSourceUserAgent(src).orEmpty()) }
        var slowdownEnabled by remember(src) { mutableStateOf(onGetSourceSlowdownEnabled(src)) }
        val domainError = editedDomain.isNotBlank() && !isLikelyValidDomain(editedDomain)
        val userAgentError = editedUserAgent.isNotBlank() && !isLikelyValidUserAgent(editedUserAgent)
        val canSave = !isBusy && !domainError && !userAgentError

        val isTv = LocalIsTv.current
        if (isTv) {
            TvDialog(
                onDismiss = { manageTarget = null },
                title = src.name,
                subtitle = stringResource(R.string.manga_engine_language, src.engineType.name, src.lang.uppercase()),
                width = 520.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SourceStatLine("Base URL", configuredBaseUrl)
                    SourceStatLine("Configured domain", configuredDomain ?: "—")
                    SourceStatLine("Search avg", formatMs(stats.avgSearchMs))
                    SourceStatLine("Popular avg", formatMs(stats.avgPopularMs))
                    SourceStatLine("Chapters avg", formatMs(stats.avgChapterMs))
                    SourceStatLine("Pages avg", formatMs(stats.avgPageMs))
                    SourceStatLine("Image avg", formatMs(stats.avgImageMs))
                    SourceStatLine(stringResource(R.string.manga_success_failure), "${stats.successCount} / ${stats.failureCount}")

                    OutlinedTextField(
                        value = editedDomain,
                        onValueChange = { editedDomain = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        isError = domainError,
                        label = { Text("Domain override", fontSize = 12.sp) },
                        placeholder = { Text(stringResource(R.string.orn_manga_tr_com), fontSize = 11.sp) },
                        supportingText = {
                            if (domainError) {
                                Text(stringResource(R.string.gecerli_bir_alan_adi_girin_orn_manga_tr_com), fontSize = 11.sp)
                            }
                        },
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = editedUserAgent,
                        onValueChange = { editedUserAgent = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        isError = userAgentError,
                        label = { Text("User-Agent override", fontSize = 12.sp) },
                        placeholder = { Text(stringResource(R.string.varsayilani_kullan), fontSize = 11.sp) },
                        supportingText = {
                            if (userAgentError) {
                                Text(stringResource(R.string.user_agent_4_512_karakter_araliginda_olmali), fontSize = 11.sp)
                            }
                        },
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Slowdown", color = KitsugiColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = slowdownEnabled,
                            enabled = !isBusy,
                            onCheckedChange = {
                                slowdownEnabled = it
                                onSetSourceSlowdownEnabled(src, it)
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KitsugiTonalButton(onClick = { onQuickCheckSource(src) }, enabled = !isBusy) { Text(stringResource(R.string.saglik_testi)) }
                        KitsugiTonalButton(onClick = { onRefreshSourceMirror(src) }, enabled = !isBusy) { Text(stringResource(R.string.manga_find_mirror)) }
                        TextButton(onClick = {
                            editedDomain = ""
                            onClearSourceMirror(src)
                        }, enabled = !isBusy) { Text(stringResource(R.string.domain_temizle)) }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            editedUserAgent = ""
                            onSetSourceUserAgent(src, null)
                        }, enabled = !isBusy) {
                            Text(stringResource(R.string.ua_temizle), color = KitsugiColors.TextSecondary)
                        }
                        TextButton(onClick = { onResetSourceDiagnostics(src) }, enabled = !isBusy) {
                            Text(stringResource(R.string.sifirla), color = KitsugiColors.AccentRed)
                        }
                        Spacer(Modifier.width(8.dp))
                        KitsugiButton(
                            onClick = {
                                onSetSourceDomain(src, editedDomain.ifBlank { null })
                                onSetSourceUserAgent(src, editedUserAgent.ifBlank { null })
                                manageTarget = null
                            },
                            enabled = canSave
                        ) {
                            Text(stringResource(R.string.kaydet), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { manageTarget = null },
                containerColor = KitsugiColors.Surface,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(src.name, color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            HealthBadge(health = health, accentColor = accentColor)
                            TinyInfoChip(text = src.lang.uppercase(), tint = accentColor)
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SourceStatLine("Base URL", configuredBaseUrl)
                        SourceStatLine("Configured domain", configuredDomain ?: "—")
                        SourceStatLine("Search avg", formatMs(stats.avgSearchMs))
                        SourceStatLine("Popular avg", formatMs(stats.avgPopularMs))
                        SourceStatLine("Chapters avg", formatMs(stats.avgChapterMs))
                        SourceStatLine("Pages avg", formatMs(stats.avgPageMs))
                        SourceStatLine("Image avg", formatMs(stats.avgImageMs))
                        SourceStatLine(stringResource(R.string.manga_success_failure), "${stats.successCount} / ${stats.failureCount}")

                        OutlinedTextField(
                            value = editedDomain,
                            onValueChange = { editedDomain = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                            isError = domainError,
                            label = { Text("Domain override", fontSize = 12.sp) },
                            placeholder = { Text(stringResource(R.string.orn_manga_tr_com), fontSize = 11.sp) },
                            supportingText = {
                                if (domainError) {
                                    Text(stringResource(R.string.gecerli_bir_alan_adi_girin_orn_manga_tr_com), fontSize = 11.sp)
                                }
                            },
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = editedUserAgent,
                            onValueChange = { editedUserAgent = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                            isError = userAgentError,
                            label = { Text("User-Agent override", fontSize = 12.sp) },
                            placeholder = { Text(stringResource(R.string.varsayilani_kullan), fontSize = 11.sp) },
                            supportingText = {
                                if (userAgentError) {
                                    Text(stringResource(R.string.user_agent_4_512_karakter_araliginda_olmali), fontSize = 11.sp)
                                }
                            },
                            singleLine = true,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Slowdown", color = KitsugiColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = slowdownEnabled,
                                enabled = !isBusy,
                                onCheckedChange = {
                                    slowdownEnabled = it
                                    onSetSourceSlowdownEnabled(src, it)
                                }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KitsugiTonalButton(onClick = { onQuickCheckSource(src) }, enabled = !isBusy) { Text(stringResource(R.string.saglik_testi)) }
                            KitsugiTonalButton(onClick = { onRefreshSourceMirror(src) }, enabled = !isBusy) { Text(stringResource(R.string.manga_find_mirror)) }
                            TextButton(onClick = {
                                editedDomain = ""
                                onClearSourceMirror(src)
                            }, enabled = !isBusy) { Text(stringResource(R.string.domain_temizle)) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onSetSourceDomain(src, editedDomain.ifBlank { null })
                        onSetSourceUserAgent(src, editedUserAgent.ifBlank { null })
                        manageTarget = null
                    }, enabled = canSave) {
                        Text(stringResource(R.string.kaydet), color = accentColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            editedUserAgent = ""
                            onSetSourceUserAgent(src, null)
                        }, enabled = !isBusy) {
                            Text(stringResource(R.string.ua_temizle), color = KitsugiColors.TextSecondary)
                        }
                        TextButton(onClick = { onResetSourceDiagnostics(src) }, enabled = !isBusy) {
                            Text(KitsugiStrings.get(R.string.inline_reset_statistics_46f0f93), color = KitsugiColors.AccentRed)
                        }
                        TextButton(onClick = { manageTarget = null }) {
                            Text(stringResource(R.string.kapat), color = KitsugiColors.TextSecondary)
                        }
                    }
                }
            )
        }
    }

    deleteTarget?.let { src ->
        val isTv = LocalIsTv.current
        if (isTv) {
            TvDialog(
                onDismiss = { deleteTarget = null },
                title = stringResource(R.string.eklenti_silinsin_mi),
                subtitle = stringResource(R.string.src_name_eklentisi_diskten_silinecek_ve_kald, src.name),
                width = 440.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.iptal), color = KitsugiColors.TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    KitsugiButton(
                        onClick = { onDeleteExtension(src); deleteTarget = null }
                    ) {
                        Text(KitsugiStrings.get(R.string.action_delete), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                containerColor = KitsugiColors.Surface,
                shape = RoundedCornerShape(20.dp),
                title = { Text(stringResource(R.string.eklenti_silinsin_mi), color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.src_name_eklentisi_diskten_silinecek_ve_kald, src.name), color = KitsugiColors.TextSecondary) },
                confirmButton = {
                    TextButton(onClick = { onDeleteExtension(src); deleteTarget = null }) {
                        Text(KitsugiStrings.get(R.string.action_delete), color = KitsugiColors.AccentRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.iptal), color = KitsugiColors.TextSecondary)
                    }
                }
            )
        }
    }

    // ── Toplu silme onayı ────────────────────────────────────────────────────
    // KitsugiConfirmDialog hem telefon hem TV düzenini kendi içinde yönetir;
    // onay verildiğinde tek gerçek callback çağrılır (varsayılan no-op yok).
    if (showBulkDeleteConfirm && bulkDelete != null) {
        KitsugiConfirmDialog(
            title = stringResource(R.string.manga_bulk_delete_dialog_title),
            message = stringResource(
                R.string.manga_bulk_delete_dialog_message,
                sources.size
            ) + "\n\n" + stringResource(R.string.manga_bulk_delete_builtin_note),
            confirmText = stringResource(R.string.manga_bulk_delete_action),
            dismissText = stringResource(R.string.iptal),
            isDestructive = true,
            onConfirm = {
                showBulkDeleteConfirm = false
                bulkDelete.onDeleteAll()
            },
            onDismiss = { showBulkDeleteConfirm = false }
        )
    }
}

@Composable
private fun HealthBadge(
    health: com.kitsugi.animelist.data.manga.model.SourceHealthStatus,
    accentColor: Color,
) {
    val tint = when (health) {
        com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Healthy -> Color(0xFF4CAF50)
        com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Degraded -> Color(0xFFFFB300)
        com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Broken -> KitsugiColors.AccentRed
        com.kitsugi.animelist.data.manga.model.SourceHealthStatus.CaptchaRequired -> Color(0xFFFF7043)
        com.kitsugi.animelist.data.manga.model.SourceHealthStatus.RateLimited -> Color(0xFF42A5F5)
        com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Disabled -> KitsugiColors.TextMuted
        com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Unknown -> accentColor.copy(alpha = 0.7f)
    }
    TinyInfoChip(text = health.name, tint = tint)
}

@Composable
private fun TinyInfoChip(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(text, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SourceStatLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = KitsugiColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = KitsugiColors.TextPrimary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatMs(value: Long): String {
    return if (value <= 0L) "—" else "${value} ms"
}

private fun formatSuccessRate(success: Int, failure: Int): String {
    val total = success + failure
    if (total <= 0) return "—"
    val percent = (success * 100.0 / total)
    return "${"%.0f".format(percent)}%"
}

@Composable
private fun trendTint(success: Int, failure: Int): Color {
    val total = success + failure
    if (total <= 0) return KitsugiColors.TextMuted
    val rate = success.toDouble() / total.toDouble()
    return when {
        rate >= 0.85 -> Color(0xFF4CAF50)
        rate >= 0.55 -> Color(0xFFFFB300)
        else -> KitsugiColors.AccentRed
    }
}

private fun formatTimestamp(value: Long): String {
    if (value <= 0L) return "—"
    return try {
        java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(value))
    } catch (_: Exception) {
        value.toString()
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = KitsugiColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FlatName(name: String) {
    Text(name, color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun isLikelyValidDomain(domain: String): Boolean {
    if (domain.isBlank()) return false
    if (domain.contains("://")) return false
    if (domain.contains(" ")) return false
    val parts = domain.split('.')
    if (parts.size < 2) return false
    return parts.all { it.isNotEmpty() }
}

private fun isLikelyValidUserAgent(ua: String): Boolean {
    if (ua.length < 4 || ua.length > 512) return false
    if (ua.contains('\n') || ua.contains('\r')) return false
    return true
}
