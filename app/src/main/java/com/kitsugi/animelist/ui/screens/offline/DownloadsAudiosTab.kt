package com.kitsugi.animelist.ui.screens.offline

import android.app.Activity
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.download.DownloadExportNaming
import com.kitsugi.animelist.core.player.audio.AudioExportWorker
import com.kitsugi.animelist.core.player.audio.player.AudioFormatSupport
import com.kitsugi.animelist.core.player.audio.player.AudioPlayerItem
import com.kitsugi.animelist.core.player.audio.player.KitsugiAudioPlayerManager
import com.kitsugi.animelist.core.storage.DownloadStorage
import com.kitsugi.animelist.core.storage.MediaStoreFileDeleter
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.ui.components.KitsugiAnimatedDeleteButton
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.screens.audio.KitsugiLiveAudioEqualizerBars
import com.kitsugi.animelist.ui.screens.audio.openAudioInExternalApp
import com.kitsugi.animelist.ui.screens.audio.shareAudioItem
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal data class DownloadedAudioItem(
    val id: String,
    val file: File,
    val contentUri: Uri? = null,
    val fileName: String,
    val displayTitle: String,
    val animeTitle: String?,
    val episode: Int?,
    val trackLabel: String?,
    val languageBadge: String?,
    val formatExtension: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val durationMs: Long,
    val posterUrlOrPath: String?
) {
    val formatUpper: String
        get() = formatExtension.uppercase(Locale.ROOT).ifBlank { "AUDIO" }

    val groupTitle: String?
        get() = animeTitle?.takeIf { it.isNotBlank() }

    fun toAudioPlayerItem(): AudioPlayerItem {
        val uriStr = when {
            file.exists() -> Uri.fromFile(file).toString()
            contentUri != null -> contentUri.toString()
            else -> file.absolutePath
        }
        val sub = buildList {
            if (!animeTitle.isNullOrBlank() && episode != null) {
                add("$animeTitle • Bölüm $episode")
            } else if (!animeTitle.isNullOrBlank()) {
                add(animeTitle)
            }
            if (!trackLabel.isNullOrBlank()) add(trackLabel)
        }.distinct().joinToString(" · ").ifBlank { null }

        return AudioPlayerItem(
            id = id,
            uriString = uriStr,
            filePath = file.absolutePath.takeIf { file.exists() },
            title = displayTitle,
            subtitle = sub,
            animeTitle = animeTitle,
            episode = episode,
            trackLabel = trackLabel,
            formatBadge = formatUpper,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            artworkUrlOrPath = posterUrlOrPath
        )
    }

    fun folderForExplorer(): File {
        val parent = file.parentFile
        return if (parent != null && parent.exists()) parent else DownloadExportNaming.publicAudioDir()
    }
}

internal data class ActiveAudioExportUiItem(
    val workId: UUID,
    val title: String,
    val episode: Int?,
    val isMovie: Boolean,
    val trackLabel: String?,
    val percent: Int,
    val state: WorkInfo.State,
    val errorMessage: String?
)

internal suspend fun scanDownloadedAudioItems(
    context: Context,
    animeDownloads: List<AnimeDownload>
): List<DownloadedAudioItem> = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    DownloadExportNaming.ensureStandardDownloadDirs(appContext)

    val posterByTitle = LinkedHashMap<String, String>()
    animeDownloads.forEach { dl ->
        val key = dl.animeTitle.trim().lowercase(Locale.ROOT)
        if (key.isNotBlank() && !dl.posterUrl.isNullOrBlank() && key !in posterByTitle) {
            posterByTitle[key] = dl.posterUrl
        }
    }

    val mediaStoreEntries = DownloadStorage.scanMediaStoreAudios(appContext)
    val mediaStoreByName = mediaStoreEntries.associateBy { it.displayName.lowercase(Locale.ROOT) }

    val itemsByKey = LinkedHashMap<String, DownloadedAudioItem>()
    val roots = DownloadStorage.audioRoots(appContext)

    for (root in roots) {
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                    continue
                }
                if (!child.isFile) continue
                val ext = child.extension.lowercase(Locale.ROOT)
                if (ext !in DownloadStorage.AUDIO_EXTENSIONS) continue
                val canonical = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
                if (canonical in itemsByKey) continue

                val parsed = DownloadExportNaming.parseAudioFileName(child.name)
                val msEntry = mediaStoreByName[child.name.lowercase(Locale.ROOT)]
                val durationMs = msEntry?.durationMs?.takeIf { it > 0L }
                    ?: probeFastDurationMs(appContext, child, msEntry?.contentUri)
                val poster = resolvePosterForAudio(parsed.animeTitle, parsed.episode, posterByTitle)

                itemsByKey[canonical] = DownloadedAudioItem(
                    id = canonical,
                    file = child,
                    contentUri = msEntry?.contentUri,
                    fileName = child.name,
                    displayTitle = parsed.displayTitle,
                    animeTitle = parsed.animeTitle,
                    episode = parsed.episode,
                    trackLabel = parsed.trackLabel,
                    languageBadge = parsed.languageBadge,
                    formatExtension = parsed.extension.ifBlank { ext },
                    mimeType = msEntry?.mimeType ?: guessAudioMimeType(ext),
                    sizeBytes = child.length().takeIf { it > 0L } ?: (msEntry?.sizeBytes ?: 0L),
                    lastModifiedMillis = child.lastModified().takeIf { it > 0L }
                        ?: (msEntry?.dateModifiedMillis ?: 0L),
                    durationMs = durationMs,
                    posterUrlOrPath = poster
                )
            }
        }
    }

    // Scoped Storage altında yalnızca MediaStore üzerinden görünen dosyaları da ekle.
    val existingNames = itemsByKey.values.map { it.fileName.lowercase(Locale.ROOT) }.toHashSet()
    val extStorageRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
    for (entry in mediaStoreEntries) {
        if (!existingNames.add(entry.displayName.lowercase(Locale.ROOT))) continue
        val parsed = DownloadExportNaming.parseAudioFileName(entry.displayName)
        val reconstructedFile = if (extStorageRoot != null && !entry.relativePath.isNullOrBlank()) {
            File(File(extStorageRoot, entry.relativePath), entry.displayName)
        } else {
            File(DownloadExportNaming.publicAudioDir(), entry.displayName)
        }
        val dur = entry.durationMs.takeIf { it > 0L }
            ?: probeFastDurationMs(appContext, null, entry.contentUri)
        val poster = resolvePosterForAudio(parsed.animeTitle, parsed.episode, posterByTitle)
        val key = entry.contentUri.toString()
        itemsByKey[key] = DownloadedAudioItem(
            id = key,
            file = reconstructedFile,
            contentUri = entry.contentUri,
            fileName = entry.displayName,
            displayTitle = parsed.displayTitle,
            animeTitle = parsed.animeTitle,
            episode = parsed.episode,
            trackLabel = parsed.trackLabel,
            languageBadge = parsed.languageBadge,
            formatExtension = parsed.extension,
            mimeType = entry.mimeType ?: guessAudioMimeType(parsed.extension),
            sizeBytes = entry.sizeBytes,
            lastModifiedMillis = entry.dateModifiedMillis,
            durationMs = dur,
            posterUrlOrPath = poster
        )
    }

    itemsByKey.values.sortedByDescending { it.lastModifiedMillis }
}

internal fun findCompanionAudiosForEpisode(
    allAudios: List<DownloadedAudioItem>,
    animeTitle: String,
    episode: Int
): List<DownloadedAudioItem> {
    if (allAudios.isEmpty() || animeTitle.isBlank()) return emptyList()
    val normalizedTarget = normalizeTitleKey(animeTitle)
    if (normalizedTarget.isBlank()) return emptyList()
    return allAudios.filter { audio ->
        val audioKey = audio.animeTitle?.let(::normalizeTitleKey).orEmpty()
        audioKey == normalizedTarget && (audio.episode == episode || (episode <= 1 && audio.episode == null))
    }
}

private fun normalizeTitleKey(raw: String): String =
    DownloadExportNaming.sanitizeTitle(raw)
        .lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

private fun resolvePosterForAudio(
    animeTitle: String?,
    episode: Int?,
    posterByTitle: Map<String, String>
): String? {
    if (animeTitle.isNullOrBlank()) return null
    posterByTitle[animeTitle.trim().lowercase(Locale.ROOT)]?.let { return it }
    if (episode != null) {
        val base = DownloadExportNaming.exportBaseName(animeTitle, episode)
        val localPoster = File(DownloadExportNaming.publicImagesDir(), DownloadExportNaming.posterFileName(base))
        if (localPoster.exists()) return localPoster.absolutePath
    }
    return null
}

private fun probeFastDurationMs(context: Context, file: File?, contentUri: Uri?): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        when {
            file != null && file.exists() -> retriever.setDataSource(file.absolutePath)
            contentUri != null -> retriever.setDataSource(context, contentUri)
            else -> return 0L
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L
    } catch (_: Throwable) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

private fun guessAudioMimeType(ext: String): String = when (ext.lowercase(Locale.ROOT)) {
    "mp3", "mp2", "mp1", "mpa" -> "audio/mpeg"
    "m4a", "mp4a", "m4b", "m4r", "alac" -> "audio/mp4"
    "aac" -> "audio/aac"
    "flac" -> "audio/flac"
    "wav", "wave", "pcm" -> "audio/wav"
    "ogg", "oga", "opus", "spx" -> "audio/ogg"
    "webm", "weba" -> "audio/webm"
    "mka" -> "audio/x-matroska"
    "ac3" -> "audio/ac3"
    "eac3", "ec3" -> "audio/eac3"
    "ac4" -> "audio/ac4"
    "dts", "dtshd" -> "audio/vnd.dts"
    "amr" -> "audio/3gpp"
    "awb" -> "audio/amr-wb"
    "wma", "asf" -> "audio/x-ms-wma"
    "aiff", "aif", "aifc" -> "audio/aiff"
    else -> "audio/*"
}

@Composable
internal fun rememberActiveAudioExports(
    context: Context,
    onExportSucceeded: () -> Unit
): List<ActiveAudioExportUiItem> {
    val workInfosFlow = remember(context) {
        WorkManager.getInstance(context.applicationContext)
            .getWorkInfosByTagFlow(AudioExportWorker.WORK_TAG)
    }
    val workInfos by workInfosFlow.collectAsState(initial = emptyList())
    var lastSucceededIds by remember { mutableStateOf<Set<UUID>?>(null) }

    LaunchedEffect(workInfos) {
        val currentSucceeded = workInfos
            .filter { it.state == WorkInfo.State.SUCCEEDED }
            .map { it.id }
            .toSet()
        val previous = lastSucceededIds
        if (previous != null && (currentSucceeded - previous).isNotEmpty()) {
            onExportSucceeded()
        }
        lastSucceededIds = currentSucceeded
    }

    return remember(workInfos) {
        workInfos
            .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            .map { info ->
                ActiveAudioExportUiItem(
                    workId = info.id,
                    title = info.progress.getString("title")
                        ?: "Ses Dışa Aktarımı",
                    episode = null,
                    isMovie = false,
                    trackLabel = null,
                    percent = info.progress.getInt("percent", 0).coerceIn(0, 100),
                    state = info.state,
                    errorMessage = info.outputData.getString("error")
                )
            }
    }
}

@Composable
internal fun DownloadsAudioControlsRow(
    sortOrder: DownloadsSortOrder,
    onSortChange: (DownloadsSortOrder) -> Unit,
    groupedByMedia: Boolean,
    onGroupedChange: (Boolean) -> Unit,
    availableFormats: List<String>,
    selectedFormat: String?,
    onSelectFormat: (String?) -> Unit,
    isSelectionMode: Boolean,
    onToggleSelectionMode: () -> Unit,
    hasItems: Boolean,
    onPlayAll: () -> Unit,
    onRefresh: () -> Unit
) {
    val accent = LocalKitsugiAccent.current
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasItems) {
                Surface(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(12.dp),
                    color = accent,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.downloads_audios_play_all),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box {
                Surface(
                    onClick = { sortMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = KitsugiColors.SurfaceElevated,
                    border = BorderStroke(1.dp, KitsugiColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Sort,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = when (sortOrder) {
                                DownloadsSortOrder.NEWEST -> stringResource(R.string.downloads_sort_newest)
                                DownloadsSortOrder.OLDEST -> stringResource(R.string.downloads_sort_oldest)
                                DownloadsSortOrder.NAME -> stringResource(R.string.downloads_sort_name)
                                DownloadsSortOrder.SIZE -> stringResource(R.string.downloads_sort_size)
                            },
                            color = KitsugiColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    containerColor = KitsugiColors.SurfaceStrong
                ) {
                    listOf(
                        DownloadsSortOrder.NEWEST to R.string.downloads_sort_newest,
                        DownloadsSortOrder.OLDEST to R.string.downloads_sort_oldest,
                        DownloadsSortOrder.NAME to R.string.downloads_sort_name,
                        DownloadsSortOrder.SIZE to R.string.downloads_sort_size
                    ).forEach { (order, labelRes) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(labelRes),
                                    color = if (sortOrder == order) accent else KitsugiColors.TextPrimary,
                                    fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                if (sortOrder == order) {
                                    Icon(
                                        imageVector = Icons.Check,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            onClick = {
                                onSortChange(order)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Surface(
                onClick = { onGroupedChange(!groupedByMedia) },
                shape = RoundedCornerShape(12.dp),
                color = if (groupedByMedia) accent.copy(alpha = 0.16f) else KitsugiColors.SurfaceElevated,
                border = BorderStroke(
                    1.dp,
                    if (groupedByMedia) accent.copy(alpha = 0.5f) else KitsugiColors.Border
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Folder,
                        contentDescription = null,
                        tint = if (groupedByMedia) accent else KitsugiColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (groupedByMedia) {
                            stringResource(R.string.downloads_audios_grouped)
                        } else {
                            stringResource(R.string.downloads_audios_flat)
                        },
                        color = if (groupedByMedia) accent else KitsugiColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (hasItems) {
                Surface(
                    onClick = onToggleSelectionMode,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelectionMode) accent.copy(alpha = 0.18f) else KitsugiColors.SurfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isSelectionMode) accent else KitsugiColors.Border
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.ListChecks,
                            contentDescription = null,
                            tint = if (isSelectionMode) accent else KitsugiColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.downloads_audios_select),
                            color = if (isSelectionMode) accent else KitsugiColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Surface(
                onClick = onRefresh,
                shape = RoundedCornerShape(12.dp),
                color = KitsugiColors.SurfaceElevated,
                border = BorderStroke(1.dp, KitsugiColors.Border)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Refresh,
                        contentDescription = null,
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.yenile),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (availableFormats.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FormatFilterChip(
                    label = stringResource(R.string.downloads_audios_filter_all),
                    selected = selectedFormat == null,
                    accent = accent,
                    onClick = { onSelectFormat(null) }
                )
                availableFormats.forEach { fmt ->
                    FormatFilterChip(
                        label = fmt,
                        selected = selectedFormat == fmt,
                        accent = accent,
                        onClick = { onSelectFormat(if (selectedFormat == fmt) null else fmt) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatFilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent else KitsugiColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (selected) accent else KitsugiColors.Border)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else KitsugiColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
internal fun DownloadedAudiosTabContent(
    audioItems: List<DownloadedAudioItem>,
    activeExports: List<ActiveAudioExportUiItem>,
    isLoading: Boolean,
    listState: LazyListState,
    sortOrder: DownloadsSortOrder,
    groupedByMedia: Boolean,
    selectedFormat: String?,
    isSelectionMode: Boolean,
    onExitSelectionMode: () -> Unit,
    onRefreshRequested: () -> Unit,
    bottomExtraPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalKitsugiAccent.current
    val audioManager = remember(context) { KitsugiAudioPlayerManager.getInstance(context) }
    val playerState by audioManager.state.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var detailsTarget by remember { mutableStateOf<DownloadedAudioItem?>(null) }
    val expandedGroups = remember { mutableStateListOf<String>() }

    // Android 11+ sistem silme onayı başlatıcısı
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedIds.clear()
            onExitSelectionMode()
            onRefreshRequested()
        }
    }

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) selectedIds.clear()
    }

    val filteredAndSorted = remember(audioItems, searchQuery, sortOrder, selectedFormat) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        val filtered = audioItems.filter { item ->
            val formatOk = selectedFormat == null || item.formatUpper == selectedFormat
            val queryOk = q.isEmpty() ||
                item.displayTitle.lowercase(Locale.ROOT).contains(q) ||
                item.fileName.lowercase(Locale.ROOT).contains(q) ||
                (item.animeTitle?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                (item.trackLabel?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                item.formatUpper.lowercase(Locale.ROOT).contains(q)
            formatOk && queryOk
        }
        when (sortOrder) {
            DownloadsSortOrder.NEWEST -> filtered.sortedByDescending { it.lastModifiedMillis }
            DownloadsSortOrder.OLDEST -> filtered.sortedBy { it.lastModifiedMillis }
            DownloadsSortOrder.NAME -> filtered.sortedBy { it.displayTitle.lowercase(Locale.ROOT) }
            DownloadsSortOrder.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        }
    }

    fun startPlaybackFromList(target: DownloadedAudioItem, openSheet: Boolean = false) {
        val queueList = filteredAndSorted.ifEmpty { listOf(target) }
        val idx = queueList.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
        audioManager.playQueue(
            items = queueList.map { it.toAudioPlayerItem() },
            startIndex = idx,
            openFullSheet = openSheet
        )
    }

    fun deleteSingleAudio(target: DownloadedAudioItem) {
        scope.launch {
            val res = DownloadStorage.deleteAudio(context, target.file, target.contentUri)
            if (res.deleted) {
                audioManager.onAudioFileDeleted(
                    deletedPath = target.file.absolutePath,
                    deletedUri = target.contentUri?.toString()
                )
                onRefreshRequested()
            } else if (res.consentUris.isNotEmpty()) {
                val sender = MediaStoreFileDeleter.systemDeleteRequestSender(context, res.consentUris)
                if (sender != null) {
                    deleteConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } else if (!res.message.isNullOrBlank()) {
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                }
            } else if (!res.message.isNullOrBlank()) {
                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Çoklu seçim araç çubuğu
        AnimatedVisibility(
            visible = isSelectionMode && audioItems.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = KitsugiColors.SurfaceStrong,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.downloads_audios_selected_count, selectedIds.size),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (selectedIds.size == filteredAndSorted.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(filteredAndSorted.map { it.id })
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Check,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.tumunu_sec),
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = { showBatchDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Delete,
                                contentDescription = null,
                                tint = KitsugiColors.AccentRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.sil),
                                color = KitsugiColors.AccentRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(onClick = onExitSelectionMode, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Arama alanı (en az 2 ses dosyası varsa veya aktif arama varsa)
        if (audioItems.size >= 2 || searchQuery.isNotBlank()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.downloads_audios_search_hint),
                        color = KitsugiColors.TextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Search,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Close,
                                contentDescription = null,
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = KitsugiColors.Border,
                    focusedContainerColor = KitsugiColors.SurfaceElevated,
                    unfocusedContainerColor = KitsugiColors.SurfaceElevated,
                    focusedTextColor = KitsugiColors.TextPrimary,
                    unfocusedTextColor = KitsugiColors.TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        if (isLoading && audioItems.isEmpty() && activeExports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
            return@Column
        }

        if (filteredAndSorted.isEmpty() && activeExports.isEmpty()) {
            EmptyDownloadedAudiosState(
                hasAnyAudioOnDisk = audioItems.isNotEmpty(),
                onOpenAudioFolder = {
                    val dir = DownloadExportNaming.publicAudioDir()
                    runCatching { dir.mkdirs() }
                    openFolderInFileManager(context, dir)
                }
            )
            return@Column
        }

        val groupedMap = remember(filteredAndSorted, groupedByMedia) {
            if (!groupedByMedia) {
                emptyMap()
            } else {
                filteredAndSorted.groupBy { item ->
                    item.groupTitle ?: ""
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 6.dp,
                bottom = 96.dp + bottomExtraPadding
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Canlı dışa aktarılan / kuyrukta bekleyen ses işleri
            if (activeExports.isNotEmpty()) {
                items(activeExports, key = { "export_${it.workId}" }) { job ->
                    ActiveAudioExportCard(
                        item = job,
                        accent = accent,
                        onCancel = {
                            WorkManager.getInstance(context).cancelWorkById(job.workId)
                        }
                    )
                }
            }

            // 2. İndirilen ses dosyaları (gruplu veya düz liste)
            if (!groupedByMedia || groupedMap.keys.all { it.isEmpty() }) {
                items(filteredAndSorted, key = { it.id }) { audio ->
                    val isCurrent = playerState.currentItem?.id == audio.id
                    AudioDownloadRowCard(
                        item = audio,
                        isCurrentTrack = isCurrent,
                        isPlayingNow = isCurrent && playerState.isPlaying,
                        isSelectionMode = isSelectionMode,
                        isSelected = audio.id in selectedIds,
                        accent = accent,
                        onToggleSelect = {
                            if (audio.id in selectedIds) selectedIds.remove(audio.id)
                            else selectedIds.add(audio.id)
                        },
                        onPlayToggle = {
                            if (isCurrent) audioManager.togglePlayPause()
                            else startPlaybackFromList(audio, openSheet = false)
                        },
                        onOpenFullPlayer = {
                            if (isCurrent) audioManager.setExpanded(true)
                            else startPlaybackFromList(audio, openSheet = true)
                        },
                        onOpenFolder = {
                            openFolderInFileManager(context, audio.folderForExplorer())
                        },
                        onShowDetails = { detailsTarget = audio },
                        onDelete = { deleteSingleAudio(audio) }
                    )
                }
            } else {
                groupedMap.forEach { (groupKey, groupItems) ->
                    if (groupKey.isEmpty() || groupItems.size == 1) {
                        items(groupItems, key = { it.id }) { audio ->
                            val isCurrent = playerState.currentItem?.id == audio.id
                            AudioDownloadRowCard(
                                item = audio,
                                isCurrentTrack = isCurrent,
                                isPlayingNow = isCurrent && playerState.isPlaying,
                                isSelectionMode = isSelectionMode,
                                isSelected = audio.id in selectedIds,
                                accent = accent,
                                onToggleSelect = {
                                    if (audio.id in selectedIds) selectedIds.remove(audio.id)
                                    else selectedIds.add(audio.id)
                                },
                                onPlayToggle = {
                                    if (isCurrent) audioManager.togglePlayPause()
                                    else startPlaybackFromList(audio, openSheet = false)
                                },
                                onOpenFullPlayer = {
                                    if (isCurrent) audioManager.setExpanded(true)
                                    else startPlaybackFromList(audio, openSheet = true)
                                },
                                onOpenFolder = {
                                    openFolderInFileManager(context, audio.folderForExplorer())
                                },
                                onShowDetails = { detailsTarget = audio },
                                onDelete = { deleteSingleAudio(audio) }
                            )
                        }
                    } else {
                        val isCollapsed = groupKey in expandedGroups
                        item(key = "group_hdr_$groupKey") {
                            AudioGroupHeaderCard(
                                title = groupKey,
                                count = groupItems.size,
                                totalBytes = groupItems.sumOf { it.sizeBytes },
                                posterUrl = groupItems.firstOrNull { !it.posterUrlOrPath.isNullOrBlank() }?.posterUrlOrPath,
                                collapsed = isCollapsed,
                                accent = accent,
                                onToggleCollapse = {
                                    if (isCollapsed) expandedGroups.remove(groupKey)
                                    else expandedGroups.add(groupKey)
                                },
                                onPlayGroup = {
                                    audioManager.playQueue(
                                        items = groupItems.map { it.toAudioPlayerItem() },
                                        startIndex = 0,
                                        openFullSheet = false
                                    )
                                }
                            )
                        }
                        if (!isCollapsed) {
                            items(groupItems, key = { it.id }) { audio ->
                                val isCurrent = playerState.currentItem?.id == audio.id
                                AudioDownloadRowCard(
                                    item = audio,
                                    isCurrentTrack = isCurrent,
                                    isPlayingNow = isCurrent && playerState.isPlaying,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = audio.id in selectedIds,
                                    accent = accent,
                                    onToggleSelect = {
                                        if (audio.id in selectedIds) selectedIds.remove(audio.id)
                                        else selectedIds.add(audio.id)
                                    },
                                    onPlayToggle = {
                                        if (isCurrent) audioManager.togglePlayPause()
                                        else startPlaybackFromList(audio, openSheet = false)
                                    },
                                    onOpenFullPlayer = {
                                        if (isCurrent) audioManager.setExpanded(true)
                                        else startPlaybackFromList(audio, openSheet = true)
                                    },
                                    onOpenFolder = {
                                        openFolderInFileManager(context, audio.folderForExplorer())
                                    },
                                    onShowDetails = { detailsTarget = audio },
                                    onDelete = { deleteSingleAudio(audio) },
                                    indented = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Toplu silme onay penceresi
    if (showBatchDeleteConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            containerColor = KitsugiColors.SurfaceStrong,
            title = {
                Text(
                    text = stringResource(R.string.downloads_audios_batch_delete_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.downloads_audios_batch_delete_message, count),
                    color = KitsugiColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targets = audioItems.filter { it.id in selectedIds }
                        showBatchDeleteConfirm = false
                        scope.launch {
                            val consentUris = LinkedHashSet<Uri>()
                            targets.forEach { target ->
                                val res = DownloadStorage.deleteAudio(context, target.file, target.contentUri)
                                if (res.deleted) {
                                    audioManager.onAudioFileDeleted(
                                        deletedPath = target.file.absolutePath,
                                        deletedUri = target.contentUri?.toString()
                                    )
                                }
                                consentUris += res.consentUris
                            }
                            selectedIds.clear()
                            onExitSelectionMode()
                            onRefreshRequested()
                            if (consentUris.isNotEmpty()) {
                                MediaStoreFileDeleter.systemDeleteRequestSender(
                                    context,
                                    consentUris.toList()
                                )?.let { sender ->
                                    deleteConsentLauncher.launch(
                                        IntentSenderRequest.Builder(sender).build()
                                    )
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KitsugiColors.AccentRed)
                ) {
                    Icon(
                        imageVector = Icons.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = stringResource(R.string.sil), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = null,
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = stringResource(R.string.iptal), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }

    // Ses dosyası teknik detay penceresi
    detailsTarget?.let { target ->
        AudioFileDetailsDialog(
            item = target,
            onDismiss = { detailsTarget = null },
            onOpenFolder = {
                detailsTarget = null
                openFolderInFileManager(context, target.folderForExplorer())
            }
        )
    }
}

@Composable
private fun ActiveAudioExportCard(
    item: ActiveAudioExportUiItem,
    accent: Color,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = KitsugiColors.SurfaceStrong,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = KitsugiColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (item.state == WorkInfo.State.RUNNING) {
                            stringResource(R.string.downloads_audios_exporting_percent, item.percent)
                        } else {
                            stringResource(R.string.downloads_audios_export_queued)
                        },
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.iptal),
                        tint = KitsugiColors.AccentRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (item.state == WorkInfo.State.RUNNING && item.percent > 0) {
                LinearProgressIndicator(
                    progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                    color = accent,
                    trackColor = KitsugiColors.SurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            } else {
                LinearProgressIndicator(
                    color = accent,
                    trackColor = KitsugiColors.SurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun AudioGroupHeaderCard(
    title: String,
    count: Int,
    totalBytes: Long,
    posterUrl: String?,
    collapsed: Boolean,
    accent: Color,
    onToggleCollapse: () -> Unit,
    onPlayGroup: () -> Unit
) {
    Surface(
        onClick = onToggleCollapse,
        shape = RoundedCornerShape(16.dp),
        color = KitsugiColors.SurfaceStrong,
        border = BorderStroke(1.dp, KitsugiColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AudioLines,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stringResource(R.string.downloads_audios_track_count, count)} • ${formatBytes(totalBytes)}",
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = onPlayGroup, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.PlayArrow,
                    contentDescription = stringResource(R.string.audio_player_play),
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Icon(
                imageVector = if (collapsed) Icons.ExpandMore else Icons.ExpandLess,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AudioDownloadRowCard(
    item: DownloadedAudioItem,
    isCurrentTrack: Boolean,
    isPlayingNow: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    accent: Color,
    onToggleSelect: () -> Unit,
    onPlayToggle: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onOpenFolder: () -> Unit,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit,
    indented: Boolean = false
) {
    val context = LocalContext.current
    var overflowOpen by remember { mutableStateOf(false) }
    val dateStr = remember(item.lastModifiedMillis) {
        if (item.lastModifiedMillis > 0L) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(item.lastModifiedMillis))
        } else {
            null
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 10.dp else 0.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (isSelectionMode) onToggleSelect() else onOpenFullPlayer()
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrentTrack) KitsugiColors.SurfaceStrong else KitsugiColors.SurfaceElevated,
        border = BorderStroke(
            width = if (isCurrentTrack || isSelected) 1.3.dp else 1.dp,
            color = when {
                isCurrentTrack -> accent
                isSelected -> accent.copy(alpha = 0.7f)
                else -> KitsugiColors.Border
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = accent)
                )
            }

            // Sol kapak / Oynat simgesi
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .clickable(onClick = onPlayToggle),
                contentAlignment = Alignment.Center
            ) {
                if (!item.posterUrlOrPath.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrlOrPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCurrentTrack && isPlayingNow) {
                            KitsugiLiveAudioEqualizerBars(
                                isPlaying = true,
                                color = Color.White,
                                barCount = 3,
                                maxHeight = 16.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isCurrentTrack && isPlayingNow) Icons.Pause else Icons.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    if (isCurrentTrack && isPlayingNow) {
                        KitsugiLiveAudioEqualizerBars(
                            isPlaying = true,
                            color = accent,
                            barCount = 4,
                            maxHeight = 18.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AudioLines,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Orta bilgi sütunu
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle,
                    color = if (isCurrentTrack) accent else KitsugiColors.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Format etiketi (M4A, MP3, FLAC, EAC3, WEBM...)
                    AudioMiniBadge(
                        text = item.formatUpper,
                        bgColor = KitsugiColors.AccentTeal.copy(alpha = 0.16f),
                        textColor = KitsugiColors.AccentTeal
                    )

                    if (!item.languageBadge.isNullOrBlank()) {
                        AudioMiniBadge(
                            text = item.languageBadge,
                            bgColor = accent.copy(alpha = 0.16f),
                            textColor = accent
                        )
                    } else if (!item.trackLabel.isNullOrBlank()) {
                        AudioMiniBadge(
                            text = item.trackLabel.take(14),
                            bgColor = accent.copy(alpha = 0.14f),
                            textColor = accent
                        )
                    }

                    if (item.durationMs > 0L) {
                        Text(
                            text = AudioFormatSupport.formatDuration(item.durationMs),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                val bottomMeta = buildList {
                    if (item.sizeBytes > 0L) add(formatBytes(item.sizeBytes))
                    if (!item.trackLabel.isNullOrBlank() && !item.languageBadge.isNullOrBlank()) {
                        add(item.trackLabel)
                    }
                    if (!dateStr.isNullOrBlank()) add(dateStr)
                }.joinToString(" • ")

                Text(
                    text = bottomMeta,
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sağ eylem düğmeleri
            if (!isSelectionMode) {
                Surface(
                    onClick = onPlayToggle,
                    shape = CircleShape,
                    color = if (isCurrentTrack && isPlayingNow) accent else accent.copy(alpha = 0.16f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCurrentTrack && isPlayingNow) Icons.Pause else Icons.PlayArrow,
                            contentDescription = if (isCurrentTrack && isPlayingNow) {
                                stringResource(R.string.audio_player_pause)
                            } else {
                                stringResource(R.string.audio_player_play)
                            },
                            tint = if (isCurrentTrack && isPlayingNow) Color.White else accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(onClick = onOpenFolder, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.FolderOpen,
                        contentDescription = stringResource(R.string.klasoru_ac),
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Box {
                    IconButton(onClick = { overflowOpen = true }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.MoreVert,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                        containerColor = KitsugiColors.SurfaceStrong
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.downloads_audios_open_player),
                                    color = KitsugiColors.TextPrimary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Headphones,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                onOpenFullPlayer()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.audio_player_share),
                                    color = KitsugiColors.TextPrimary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Share,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                shareAudioItem(context, item.toAudioPlayerItem())
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.audio_player_open_external),
                                    color = KitsugiColors.TextPrimary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.OpenInNew,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                openAudioInExternalApp(context, item.toAudioPlayerItem())
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.downloads_audios_file_details),
                                    color = KitsugiColors.TextPrimary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Info,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                onShowDetails()
                            }
                        )
                    }
                }

                KitsugiAnimatedDeleteButton(
                    onDelete = onDelete,
                    size = 32.dp,
                    contentDescription = stringResource(R.string.sil)
                )
            }
        }
    }
}

@Composable
private fun AudioMiniBadge(
    text: String,
    bgColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyDownloadedAudiosState(
    hasAnyAudioOnDisk: Boolean,
    onOpenAudioFolder: () -> Unit
) {
    val accent = LocalKitsugiAccent.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(KitsugiColors.AccentTeal.copy(alpha = 0.14f))
                .border(1.dp, KitsugiColors.AccentTeal.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AudioLines,
                contentDescription = null,
                tint = KitsugiColors.AccentTeal,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (hasAnyAudioOnDisk) {
                stringResource(R.string.downloads_audios_search_empty)
            } else {
                stringResource(R.string.downloads_audios_empty_title)
            },
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.downloads_audios_empty_desc),
            color = KitsugiColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onOpenAudioFolder,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.FolderOpen,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.downloads_audios_open_folder_button),
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AudioFileDetailsDialog(
    item: DownloadedAudioItem,
    onDismiss: () -> Unit,
    onOpenFolder: () -> Unit
) {
    val accent = LocalKitsugiAccent.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.SurfaceStrong,
        title = {
            Text(
                text = stringResource(R.string.downloads_audios_file_details),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine(stringResource(R.string.downloads_detail_filename), item.fileName)
                DetailLine(stringResource(R.string.downloads_detail_format), "${item.formatUpper} (${item.mimeType ?: "audio/*"})")
                if (item.durationMs > 0L) {
                    DetailLine(stringResource(R.string.downloads_detail_duration), AudioFormatSupport.formatDuration(item.durationMs))
                }
                DetailLine(stringResource(R.string.downloads_detail_size), formatBytes(item.sizeBytes))
                DetailLine(stringResource(R.string.klasor_konumu), item.file.absolutePath)
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenFolder) {
                Icon(
                    imageVector = Icons.FolderOpen,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = stringResource(R.string.klasoru_ac), color = accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = null,
                    tint = KitsugiColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(text = stringResource(R.string.kapat), color = KitsugiColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = KitsugiColors.TextPrimary,
            fontSize = 12.5.sp
        )
    }
}
