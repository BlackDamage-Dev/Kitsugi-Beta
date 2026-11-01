package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.icons.KitsugiIcon as Icon
import androidx.compose.ui.res.stringResource

import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.screens.settings.DeveloperLogsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiSystemSettingsDialog(
    totalEntryCount: Int,
    onExportFileClick: () -> Unit,
    onImportFileClick: () -> Unit,
    onDeleteAllClick: () -> Unit,
    dnsChoice: Int,
    onDnsChoiceSelected: (Int) -> Unit,
    download: com.kitsugi.animelist.ui.screens.settings.DownloadSettings,
    initialPage: Int = 0,
    embeddedMode: Boolean = false,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, 1), pageCount = { 2 })
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialPage) {
        val targetPage = initialPage.coerceIn(0, 1)
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }

    val dataManagementScrollState = rememberScrollState()
    val dnsSettingsScrollState = rememberScrollState()

    val activeScrollState = when (pagerState.currentPage) {
        0 -> dataManagementScrollState
        else -> dnsSettingsScrollState
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true,
        embeddedMode = embeddedMode,
        innerColumnScrollState = activeScrollState,
        sheetGesturesEnabled = false
    ) {
        // Header
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
                    Text(
                        text = stringResource(R.string.inline_system_data_settings_fc74ef9),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Close, contentDescription = stringResource(R.string.action_close), tint = KitsugiColors.TextSecondary)
                    }
                }
            }
            KitsugiSettingsSegmentedTabs(
                tabs = listOf(
                    KitsugiSettingsTabOption(stringResource(R.string.inline_data_management_4b7c656), Icons.Storage),
                    KitsugiSettingsTabOption("DNS (DoH)", Icons.Dns)
                ),
                selectedIndex = pagerState.currentPage,
                accentColor = accentColor,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
            )
        }

        // Body
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) { page ->
            when (page) {
                0 -> DataManagementTab(
                    totalEntryCount = totalEntryCount,
                    onExportFileClick = onExportFileClick,
                    onImportFileClick = onImportFileClick,
                    onDeleteAllClick = onDeleteAllClick,
                    accentColor = accentColor,
                    scrollState = dataManagementScrollState
                )
                1 -> DnsSettingsTab(
                    dnsChoice = dnsChoice,
                    onDnsChoiceSelected = onDnsChoiceSelected,
                    accentColor = accentColor,
                    scrollState = dnsSettingsScrollState
                )
            }
        }

        // In the full-screen settings shell, navigation is handled by the shared top back bar.
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
}


@Composable
private fun DataManagementTab(
    totalEntryCount: Int,
    onExportFileClick: () -> Unit,
    onImportFileClick: () -> Unit,
    onDeleteAllClick: () -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    var showDeveloperLogs by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.system_backup_reset_section)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.inline_export_list_entries_from_the_7657e48),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.inline_total_entries_1_s_6ee6416, (totalEntryCount).toString()),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.inline_note_export_import_only_includes_9010228),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                KitsugiButton(
                    enabled = totalEntryCount > 0,
                    onClick = onExportFileClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.inline_export_as_json_fff9b39), color = KitsugiColors.Background, fontWeight = FontWeight.Bold)
                }
                
                KitsugiButton(
                    onClick = onImportFileClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.inline_import_from_json_1572af3), color = KitsugiColors.Background, fontWeight = FontWeight.Bold)
                }
                
                KitsugiButton(
                    enabled = totalEntryCount > 0,
                    onClick = onDeleteAllClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.inline_delete_entire_list_174ee79), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        KitsugiSettingsSection(title = stringResource(R.string.system_developer_diagnostics_section)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.inline_open_the_diagnostics_panel_to_0b2bfee),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                KitsugiButton(
                    onClick = { showDeveloperLogs = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.inline_view_logcat_2bebdd3), color = KitsugiColors.Background, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDeveloperLogs) {
        DeveloperLogsDialog(
            onDismiss = { showDeveloperLogs = false }
        )
    }
}

@Composable
private fun DnsSettingsTab(
    dnsChoice: Int,
    onDnsChoiceSelected: (Int) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    val dnsOptions = remember(context) { buildDnsOptions(context) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        KitsugiSettingsSection(title = "DNS over HTTPS (DoH)") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.inline_choose_a_doh_provider_to_12c661b),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                dnsOptions.forEach { dnsOpt ->
                    val isSelected = dnsOpt.id == dnsChoice.toString()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) KitsugiColors.SurfaceSoft else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(12.dp)) { onDnsChoiceSelected(dnsOpt.id.toIntOrNull() ?: 0) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onDnsChoiceSelected(dnsOpt.id.toIntOrNull() ?: 0) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accentColor,
                                unselectedColor = KitsugiColors.TextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = dnsOpt.title,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = dnsOpt.description,
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageSettingsTab(
    download: com.kitsugi.animelist.ui.screens.settings.DownloadSettings,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val speedLimitOptions = remember(context) { buildSpeedLimitOptions(context) }
    val simultaneousDownloadsOptions = remember(context) { buildSimultaneousDownloadOptions(context) }
    val removeAfterReadOptions = remember(context) { buildRemoveAfterReadOptions(context) }
    val autoDownloadReadingOptions = remember(context) { buildAutoDownloadReadingOptions(context) }
    val autoDownloadWatchingOptions = remember(context) { buildAutoDownloadWatchingOptions(context) }
    val statusCategoryOptions = remember(context) { buildStatusCategoryOptions(context) }

    // Launcher for directory picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            download.onCustomImageDownloadUriChanged(it.toString())
        }
    }

    val videoFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            download.onVideoDownloadUriChanged(it.toString())
        }
    }

    val displayPath = if (download.customImageDownloadUri.isBlank()) {
        context.getString(R.string.system_default_download_location)
    } else {
        try {
            val treeUri = Uri.parse(download.customImageDownloadUri)
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            docFile?.name ?: treeUri.lastPathSegment ?: download.customImageDownloadUri
        } catch (e: Exception) {
            download.customImageDownloadUri
        }
    }

    val displayVideoPath = if (download.videoDownloadUri.isBlank()) {
        context.getString(R.string.system_default_video_download_location)
    } else {
        try {
            val treeUri = Uri.parse(download.videoDownloadUri)
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            docFile?.name ?: treeUri.lastPathSegment ?: download.videoDownloadUri
        } catch (e: Exception) {
            download.videoDownloadUri
        }
    }

    // Dynamic external downloader detection
    val is1dmInstalled = remember(context) {
        isPackageInstalled("idm.internet.download.manager", pm) ||
        isPackageInstalled("idm.internet.download.manager.plus", pm)
    }
    val isAdmInstalled = remember(context) {
        isPackageInstalled("com.dv.adm", pm) ||
        isPackageInstalled("com.dv.adm.pay", pm)
    }

    val localDownloaderOptions = remember(is1dmInstalled, isAdmInstalled, context) {
        listOf(
            "INTERNAL" to context.getString(R.string.system_downloader_internal),
            "EXTERNAL_1DM" to context.getString(if (is1dmInstalled) R.string.system_downloader_1dm_installed else R.string.system_downloader_1dm_not_installed),
            "EXTERNAL_ADM" to context.getString(if (isAdmInstalled) R.string.system_downloader_adm_installed else R.string.system_downloader_adm_not_installed),
            "EXTERNAL_SYSTEM" to context.getString(R.string.system_downloader_default_picker)
        )
    }

    var activePicker by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. İndirme Konumları ---
        KitsugiSettingsSection(title = context.getString(R.string.system_download_locations_section)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Resim indirme konumu
                Text(
                    text = stringResource(R.string.inline_image_download_location_cfac1ca),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KitsugiColors.SurfaceSoft, shape = RoundedCornerShape(12.dp))
                        .tvClickable(shape = RoundedCornerShape(12.dp)) { folderPickerLauncher.launch(null) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Folder, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(displayPath, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                if (download.customImageDownloadUri.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { download.onCustomImageDownloadUriChanged("") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.inline_reset_image_location_e352f15), color = KitsugiColors.TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Video indirme konumu
                Text(
                    text = stringResource(R.string.inline_video_download_location_a645733),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KitsugiColors.SurfaceSoft, shape = RoundedCornerShape(12.dp))
                        .tvClickable(shape = RoundedCornerShape(12.dp)) { videoFolderPickerLauncher.launch(null) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Folder, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(displayVideoPath, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                if (download.videoDownloadUri.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { download.onVideoDownloadUriChanged("") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.inline_reset_video_location_d964174), color = KitsugiColors.TextSecondary)
                    }
                }
            }
        }

        // --- 2. Genel İndirme Ayarları ---
        KitsugiSettingsSection(title = context.getString(R.string.system_general_download_settings)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                KitsugiSwitchSettingItem(
                    title = context.getString(R.string.system_download_only_wifi),
                    description = context.getString(R.string.system_download_only_wifi_description),
                    checked = download.downloadOnlyOverWifi,
                    onCheckedChange = download.onDownloadOnlyOverWifiChanged
                )

                KitsugiSwitchSettingItem(
                    title = context.getString(R.string.system_always_external_downloader_anime),
                    description = context.getString(R.string.system_external_downloader_anime_description),
                    checked = download.useExternalDownloader,
                    onCheckedChange = download.onUseExternalDownloaderChanged
                )

                KitsugiClickableSettingItem(
                    title = context.getString(R.string.system_external_downloader_preference),
                    description = context.getString(R.string.system_external_downloader_preference_description),
                    currentValue = localDownloaderOptions.firstOrNull { it.first == download.downloaderPreference }?.second,
                    onClick = { activePicker = "downloader" }
                )

                KitsugiClickableSettingItem(
                    title = context.getString(R.string.system_speed_limit_setting),
                    description = context.getString(R.string.system_speed_limit_description),
                    currentValue = speedLimitOptions.firstOrNull { it.first == download.downloadSpeedLimit }?.second,
                    onClick = { activePicker = "speed_limit" }
                )

                KitsugiSwitchSettingItem(
                    title = context.getString(R.string.system_save_chapters_cbz),
                    description = context.getString(R.string.system_save_chapters_cbz_description),
                    checked = download.saveChaptersAsCBZ,
                    onCheckedChange = download.onSaveChaptersAsCBZChanged
                )

                KitsugiSwitchSettingItem(
                    title = context.getString(R.string.system_split_tall_images),
                    description = context.getString(R.string.system_split_tall_images_description),
                    checked = download.splitTallImages,
                    onCheckedChange = download.onSplitTallImagesChanged
                )

                KitsugiClickableSettingItem(
                    title = context.getString(R.string.system_simultaneous_downloads_setting),
                    description = context.getString(R.string.system_simultaneous_downloads_description),
                    currentValue = simultaneousDownloadsOptions.firstOrNull { it.first == download.numberOfDownloads }?.second,
                    onClick = { activePicker = "simultaneous" }
                )
            }
        }

        // --- 3. Otomatik İndirme ---
        KitsugiSettingsSection(title = context.getString(R.string.system_automatic_downloads_section)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                KitsugiSwitchSettingItem(
                    title = context.getString(R.string.system_download_new_chapters),
                    description = context.getString(R.string.system_download_new_chapters_description),
                    checked = download.downloadNewChapters,
                    onCheckedChange = download.onDownloadNewChaptersChanged
                )

                if (download.downloadNewChapters) {
                    KitsugiClickableSettingItem(
                        title = context.getString(R.string.system_filter_manga_categories_included),
                        description = context.getString(R.string.system_only_download_manga_in_categories),
                        currentValue = formatCategories(context, download.downloadNewChapterCategories, statusCategoryOptions),
                        onClick = { activePicker = "include_manga_download" }
                    )
                    KitsugiClickableSettingItem(
                        title = context.getString(R.string.system_filter_manga_categories_excluded),
                        description = context.getString(R.string.system_do_not_download_manga_in_categories),
                        currentValue = formatCategories(context, download.downloadNewChapterCategoriesExclude, statusCategoryOptions),
                        onClick = { activePicker = "exclude_manga_download" }
                    )
                    KitsugiSwitchSettingItem(
                        title = context.getString(R.string.system_download_unread_chapters_only),
                        checked = download.downloadNewUnreadChaptersOnly,
                        onCheckedChange = download.onDownloadNewUnreadChaptersOnlyChanged
                    )
                }

                KitsugiSwitchSettingItem(
                    title = context.getString(R.string.system_download_new_videos),
                    description = context.getString(R.string.system_download_new_episodes_description),
                    checked = download.downloadNewEpisodes,
                    onCheckedChange = download.onDownloadNewEpisodesChanged
                )

                if (download.downloadNewEpisodes) {
                    KitsugiClickableSettingItem(
                        title = context.getString(R.string.system_filter_anime_categories_included),
                        description = context.getString(R.string.system_only_download_anime_in_categories),
                        currentValue = formatCategories(context, download.downloadNewEpisodeCategories, statusCategoryOptions),
                        onClick = { activePicker = "include_anime_download" }
                    )
                    KitsugiClickableSettingItem(
                        title = context.getString(R.string.system_filter_anime_categories_excluded),
                        description = context.getString(R.string.system_do_not_download_anime_in_categories),
                        currentValue = formatCategories(context, download.downloadNewEpisodeCategoriesExclude, statusCategoryOptions),
                        onClick = { activePicker = "exclude_anime_download" }
                    )
                    KitsugiSwitchSettingItem(
                        title = context.getString(R.string.system_download_unwatched_videos_only),
                        checked = download.downloadNewUnseenEpisodesOnly,
                        onCheckedChange = download.onDownloadNewUnseenEpisodesOnlyChanged
                    )
                }

                KitsugiClickableSettingItem(
                    title = context.getString(R.string.system_pre_download_while_reading_setting),
                    description = context.getString(R.string.system_pre_download_while_reading_description),
                    currentValue = autoDownloadReadingOptions.firstOrNull { it.first == download.autoDownloadWhileReading }?.second,
                    onClick = { activePicker = "auto_download_reading" }
                )

                KitsugiClickableSettingItem(
                    title = context.getString(R.string.system_pre_download_while_watching_setting),
                    description = context.getString(R.string.system_pre_download_while_watching_description),
                    currentValue = autoDownloadWatchingOptions.firstOrNull { it.first == download.autoDownloadWhileWatching }?.second,
                    onClick = { activePicker = "auto_download_watching" }
                )
            }
        }

        // --- 4. Otomatik Silme ---
        KitsugiSettingsSection(title = context.getString(R.string.system_automatic_removal_section)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                KitsugiSwitchSettingItem(
                    title = context.getString(R.string.system_remove_after_read),
                    description = context.getString(R.string.system_remove_after_read_description),
                    checked = download.removeAfterMarkedAsRead,
                    onCheckedChange = download.onRemoveAfterMarkedAsReadChanged
                )

                KitsugiClickableSettingItem(
                    title = context.getString(R.string.system_remove_chapters_automatically_setting),
                    description = context.getString(R.string.system_remove_chapters_automatically_description),
                    currentValue = removeAfterReadOptions.firstOrNull { it.first == download.removeAfterReadSlots }?.second,
                    onClick = { activePicker = "remove_after_read" }
                )

                KitsugiSwitchSettingItem(
                    title = context.getString(R.string.system_remove_bookmarked_chapters),
                    description = context.getString(R.string.system_remove_bookmarked_chapters_description),
                    checked = download.removeBookmarkedChapters,
                    onCheckedChange = download.onRemoveBookmarkedChaptersChanged
                )

                KitsugiSwitchSettingItem(
                    title = context.getString(R.string.system_download_filler_chapters),
                    description = context.getString(R.string.system_download_filler_chapters_description),
                    checked = download.downloadFillermarkedItems,
                    onCheckedChange = download.onDownloadFillermarkedItemsChanged
                )

                KitsugiClickableSettingItem(
                    title = context.getString(R.string.system_manga_categories_excluded_from_removal_setting),
                    description = context.getString(R.string.system_manga_categories_excluded_from_removal_description),
                    currentValue = formatCategories(context, download.removeExcludeCategories, statusCategoryOptions),
                    onClick = { activePicker = "exclude_manga_delete" }
                )

                KitsugiClickableSettingItem(
                    title = context.getString(R.string.system_anime_categories_excluded_from_removal_setting),
                    description = context.getString(R.string.system_anime_categories_excluded_from_removal_description),
                    currentValue = formatCategories(context, download.removeExcludeAnimeCategories, statusCategoryOptions),
                    onClick = { activePicker = "exclude_anime_delete" }
                )
            }
        }

        // --- 5. Altyazı İndirme Dil Tercihleri ---
        @OptIn(ExperimentalLayoutApi::class)
        @Suppress("UNUSED_EXPRESSION")
        KitsugiSettingsSection(title = context.getString(R.string.system_subtitle_download_language_preferences)) {
            val allSubLangs = remember(context) {
                listOf(
                    "tr" to context.getString(R.string.inline_turkish_efaad14),
                    "en" to context.getString(R.string.option_title_lang_english),
                    "ja" to context.getString(R.string.system_language_japanese),
                    "ar" to context.getString(R.string.player_ui_arabic),
                    "zh" to context.getString(R.string.player_ui_chinese),
                    "ko" to context.getString(R.string.system_language_korean),
                    "fr" to context.getString(R.string.player_ui_french),
                    "de" to context.getString(R.string.system_language_german),
                    "es" to context.getString(R.string.player_ui_spanish),
                    "pt" to context.getString(R.string.system_language_portuguese),
                    "it" to context.getString(R.string.player_ui_italian),
                    "ru" to context.getString(R.string.player_ui_russian),
                    "nl" to context.getString(R.string.system_language_dutch),
                    "pl" to context.getString(R.string.player_ui_polish),
                    "ro" to context.getString(R.string.system_language_romanian),
                    "vi" to context.getString(R.string.system_language_vietnamese),
                    "id" to context.getString(R.string.system_language_indonesian),
                    "th" to context.getString(R.string.system_language_thai)
                )
            }
            val selectedDlLangs = remember(download.subtitleDownloadLanguages) {
                mutableStateOf(
                    download.subtitleDownloadLanguages
                        .split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toMutableSet()
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = if (selectedDlLangs.value.isEmpty()) stringResource(R.string.ui_subtitle_languages_none_selected)
                    else stringResource(R.string.ui2_subtitle_selected_turkish_first, selectedDlLangs.value.joinToString(", ").uppercase()),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allSubLangs.forEach { (code, label) ->
                        val isSel = code in selectedDlLangs.value
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                val upd = selectedDlLangs.value.toMutableSet()
                                if (isSel) upd.remove(code) else upd.add(code)
                                selectedDlLangs.value = upd
                                download.onSubtitleDownloadLanguagesChanged(upd.joinToString(","))
                            },
                            label = {
                                Text(
                                    text = "$code · $label",
                                    fontSize = 12.sp,
                                    color = if (isSel) LocalKitsugiAccent.current else KitsugiColors.TextSecondary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalKitsugiAccent.current.copy(alpha = 0.18f),
                                containerColor = KitsugiColors.Surface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = isSel,
                                selectedBorderColor = LocalKitsugiAccent.current,
                                borderColor = KitsugiColors.TextSecondary.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }

    // --- Choice Dialogs ---
    when (activePicker) {
        "downloader" -> {
            KitsugiChoicePickerDialog(
                title = context.getString(R.string.system_downloader_preference_dialog_title),
                options = localDownloaderOptions,
                selectedOption = download.downloaderPreference,
                onOptionSelected = download.onDownloaderPreferenceSelected,
                onDismiss = { activePicker = null }
            )
        }
        "speed_limit" -> {
            KitsugiChoicePickerDialog(
                title = context.getString(R.string.system_speed_limit_dialog_title),
                options = speedLimitOptions,
                selectedOption = download.downloadSpeedLimit,
                onOptionSelected = download.onDownloadSpeedLimitChanged,
                onDismiss = { activePicker = null }
            )
        }
        "simultaneous" -> {
            KitsugiChoicePickerDialog(
                title = context.getString(R.string.system_simultaneous_downloads_dialog_title),
                options = simultaneousDownloadsOptions,
                selectedOption = download.numberOfDownloads,
                onOptionSelected = download.onNumberOfDownloadsSelected,
                onDismiss = { activePicker = null }
            )
        }
        "remove_after_read" -> {
            KitsugiChoicePickerDialog(
                title = context.getString(R.string.system_remove_chapters_dialog_title),
                options = removeAfterReadOptions,
                selectedOption = download.removeAfterReadSlots,
                onOptionSelected = download.onRemoveAfterReadSlotsSelected,
                onDismiss = { activePicker = null }
            )
        }
        "auto_download_reading" -> {
            KitsugiChoicePickerDialog(
                title = context.getString(R.string.system_pre_download_reading_dialog_title),
                options = autoDownloadReadingOptions,
                selectedOption = download.autoDownloadWhileReading,
                onOptionSelected = download.onAutoDownloadWhileReadingSelected,
                onDismiss = { activePicker = null }
            )
        }
        "auto_download_watching" -> {
            KitsugiChoicePickerDialog(
                title = context.getString(R.string.system_pre_download_watching_dialog_title),
                options = autoDownloadWatchingOptions,
                selectedOption = download.autoDownloadWhileWatching,
                onOptionSelected = download.onAutoDownloadWhileWatchingSelected,
                onDismiss = { activePicker = null }
            )
        }
        "exclude_manga_delete" -> {
            KitsugiMultiSelectPickerDialog(
                title = context.getString(R.string.system_manga_categories_excluded_from_removal_dialog_title),
                options = statusCategoryOptions,
                selectedOptions = download.removeExcludeCategories,
                onSave = download.onRemoveExcludeCategoriesChanged,
                onDismiss = { activePicker = null }
            )
        }
        "exclude_anime_delete" -> {
            KitsugiMultiSelectPickerDialog(
                title = context.getString(R.string.system_anime_categories_excluded_from_removal_dialog_title),
                options = statusCategoryOptions,
                selectedOptions = download.removeExcludeAnimeCategories,
                onSave = download.onRemoveExcludeAnimeCategoriesChanged,
                onDismiss = { activePicker = null }
            )
        }
        "include_manga_download" -> {
            KitsugiMultiSelectPickerDialog(
                title = context.getString(R.string.system_manga_new_chapter_download_categories_dialog_title),
                options = statusCategoryOptions,
                selectedOptions = download.downloadNewChapterCategories,
                onSave = download.onDownloadNewChapterCategoriesChanged,
                onDismiss = { activePicker = null }
            )
        }
        "exclude_manga_download" -> {
            KitsugiMultiSelectPickerDialog(
                title = context.getString(R.string.system_manga_excluded_categories_dialog_title),
                options = statusCategoryOptions,
                selectedOptions = download.downloadNewChapterCategoriesExclude,
                onSave = download.onDownloadNewChapterCategoriesExcludeChanged,
                onDismiss = { activePicker = null }
            )
        }
        "include_anime_download" -> {
            KitsugiMultiSelectPickerDialog(
                title = context.getString(R.string.system_anime_new_video_download_categories_dialog_title),
                options = statusCategoryOptions,
                selectedOptions = download.downloadNewEpisodeCategories,
                onSave = download.onDownloadNewEpisodeCategoriesChanged,
                onDismiss = { activePicker = null }
            )
        }
        "exclude_anime_download" -> {
            KitsugiMultiSelectPickerDialog(
                title = context.getString(R.string.system_anime_excluded_categories_dialog_title),
                options = statusCategoryOptions,
                selectedOptions = download.downloadNewEpisodeCategoriesExclude,
                onSave = download.onDownloadNewEpisodeCategoriesExcludeChanged,
                onDismiss = { activePicker = null }
            )
        }
    }
}

@Composable
fun <T> KitsugiChoicePickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Surface,
        titleContentColor = KitsugiColors.TextPrimary,
        textContentColor = KitsugiColors.TextSecondary,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) KitsugiColors.SurfaceSoft else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                onOptionSelected(value)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onOptionSelected(value)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accentColor,
                                unselectedColor = KitsugiColors.TextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = accentColor, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
fun <T> KitsugiMultiSelectPickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    selectedOptions: Set<T>,
    onSave: (Set<T>) -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    var tempSelected by remember { mutableStateOf(selectedOptions) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Surface,
        titleContentColor = KitsugiColors.TextPrimary,
        textContentColor = KitsugiColors.TextSecondary,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (value, label) ->
                    val isChecked = tempSelected.contains(value)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isChecked) KitsugiColors.SurfaceSoft else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                tempSelected = if (isChecked) {
                                    tempSelected - value
                                } else {
                                    tempSelected + value
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                tempSelected = if (checked) {
                                    tempSelected + value
                                } else {
                                    tempSelected - value
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = accentColor,
                                uncheckedColor = KitsugiColors.TextMuted,
                                checkmarkColor = KitsugiColors.Background
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(tempSelected)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_save), color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel), color = KitsugiColors.TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
fun KitsugiSwitchSettingItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp)) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KitsugiColors.Background,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = KitsugiColors.TextMuted,
                uncheckedTrackColor = KitsugiColors.SurfaceSoft
            )
        )
    }
}

@Composable
fun KitsugiClickableSettingItem(
    title: String,
    description: String? = null,
    currentValue: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(12.dp)) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (currentValue != null) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = currentValue,
                color = LocalKitsugiAccent.current,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun isPackageInstalled(packageName: String, packageManager: android.content.pm.PackageManager): Boolean {
    return try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }
}

private fun formatCategories(
    context: Context,
    categories: Set<String>,
    statusCategoryOptions: List<Pair<String, String>>
): String {
    if (categories.isEmpty()) return context.getString(R.string.system_none)
    return categories.mapNotNull { key ->
        statusCategoryOptions.firstOrNull { it.first == key }?.second
    }.joinToString(", ")
}

private fun buildSpeedLimitOptions(context: Context): List<Pair<Int, String>> = listOf(
    0 to context.getString(R.string.system_unlimited_speed),
    50 * 1024 to "50 KB/s",
    100 * 1024 to "100 KB/s",
    500 * 1024 to "500 KB/s",
    1024 * 1024 to "1 MB/s",
    2 * 1024 * 1024 to "2 MB/s",
    5 * 1024 * 1024 to "5 MB/s",
    10 * 1024 * 1024 to "10 MB/s"
)

private fun buildSimultaneousDownloadOptions(context: Context): List<Pair<Int, String>> = listOf(
    1 to context.getString(R.string.system_download_count_1),
    2 to context.getString(R.string.system_download_count_2),
    3 to context.getString(R.string.system_download_count_3),
    4 to context.getString(R.string.system_download_count_4),
    5 to context.getString(R.string.system_download_count_5)
)

private fun buildRemoveAfterReadOptions(context: Context): List<Pair<Int, String>> = listOf(
    -1 to context.getString(R.string.player_ui_disabled),
    0 to context.getString(R.string.system_last_read_chapter),
    1 to context.getString(R.string.system_keep_last_1_chapter),
    2 to context.getString(R.string.system_keep_last_2_chapters),
    3 to context.getString(R.string.system_keep_last_3_chapters),
    5 to context.getString(R.string.system_keep_last_5_chapters),
    10 to context.getString(R.string.system_keep_last_10_chapters)
)

private fun buildAutoDownloadReadingOptions(context: Context): List<Pair<Int, String>> = listOf(
    0 to context.getString(R.string.player_ui_disabled),
    1 to context.getString(R.string.system_pre_download_next_1_chapter),
    2 to context.getString(R.string.system_pre_download_next_2_chapters),
    3 to context.getString(R.string.system_pre_download_next_3_chapters),
    5 to context.getString(R.string.system_pre_download_next_5_chapters),
    10 to context.getString(R.string.system_pre_download_next_10_chapters)
)

private fun buildAutoDownloadWatchingOptions(context: Context): List<Pair<Int, String>> = listOf(
    0 to context.getString(R.string.player_ui_disabled),
    1 to context.getString(R.string.system_pre_download_next_1_video),
    2 to context.getString(R.string.system_pre_download_next_2_videos),
    3 to context.getString(R.string.system_pre_download_next_3_videos)
)

private fun buildStatusCategoryOptions(context: Context): List<Pair<String, String>> = listOf(
    "CURRENT" to context.getString(R.string.system_status_current),
    "PLANNING" to context.getString(R.string.system_status_planning),
    "COMPLETED" to context.getString(R.string.bangumi_tag_completed),
    "ON_HOLD" to context.getString(R.string.inline_paused_0cf0c04),
    "DROPPED" to context.getString(R.string.system_status_dropped)
)

private fun buildDnsOptions(context: Context): List<KitsugiChoiceOption> = listOf(
    KitsugiChoiceOption(id = "0", title = context.getString(R.string.system_dns_system_default), description = context.getString(R.string.system_dns_system_default_description)),
    KitsugiChoiceOption(id = "1", title = "Google DNS", description = context.getString(R.string.system_dns_google_description)),
    KitsugiChoiceOption(id = "2", title = "Cloudflare DNS", description = context.getString(R.string.system_dns_cloudflare_description)),
    KitsugiChoiceOption(id = "3", title = "AdGuard DNS", description = context.getString(R.string.system_dns_adguard_description)),
    KitsugiChoiceOption(id = "4", title = "DNS.WATCH", description = context.getString(R.string.system_dns_watch_description)),
    KitsugiChoiceOption(id = "5", title = "Quad9 DNS", description = context.getString(R.string.system_dns_quad9_description)),
    KitsugiChoiceOption(id = "6", title = "DNS.SB", description = context.getString(R.string.system_dns_sb_description)),
    KitsugiChoiceOption(id = "7", title = "Canadian Shield", description = context.getString(R.string.system_dns_canadian_shield_description))
)
