package com.kitsugi.animelist.ui.screens.history

import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.local.WatchHistoryManager
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import com.kitsugi.animelist.ui.components.CompactProgressBar
import com.kitsugi.animelist.ui.components.KitsugiCosmicSearchBar
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.utils.StreamLabelSanitizer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * İzleme Geçmişi ekranı.
 *
 * ## 2026-11-01 kullanıcı isteği
 * "İzleme geçmişi daha kullanışlı olsun; sıralama ve benzeri güzel, mantıklı,
 * derli toplu özellikler eklensin."
 *
 * Bu sürümde eklenen yetenekler:
 *  - **Arama:** başlık, eklenti (kaynak) ve akış etiketinde Türkçe-duyarlı arama.
 *  - **Sıralama:** son izlenen / en eski / devam edenler önce / ilerleme / başlık / kaynak.
 *  - **Filtreler:** tümü, devam edenler, izlenenler, başlanmamış + dizi/film + kaynak.
 *  - **Gruplama:** aynı dizinin bölümleri tek kartta; kart içinden bölüm seçilebilir.
 *  - **Bölüm başlıkları:** Bugün / Dün / Bu hafta / Bu ay / Daha eski.
 *  - **Toplu seçim + geri alma:** uzun basınca işlem menüsü, çoklu seçim, silme sonrası
 *    "Geri al".
 *  - **Özet şerit:** kayıt sayısı, dizi sayısı, devam edenler ve yaklaşık izleme süresi.
 *  - **Metin temizliği:** eklentilerden gelen `.fa-secondary{opacity:.4}` gibi CSS/HTML
 *    artıkları gösterilmez (bkz. [StreamLabelSanitizer]).
 *  - Tamamlanmış bir bölüm açıldığında baştan başlar; yarıda kalan bölüm kaldığı yerden
 *    devam eder.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val history by WatchHistoryManager.history.collectAsState()
    val preferences = remember(context) { WatchHistoryPreferences(context) }

    // ── Kalıcı kullanıcı tercihleri ───────────────────────────────────────────
    var sortMode by remember { mutableStateOf(preferences.sortMode) }
    var progressFilter by remember { mutableStateOf(preferences.progressFilter) }
    var mediaFilter by remember { mutableStateOf(preferences.mediaFilter) }
    var groupBySeries by remember { mutableStateOf(preferences.groupBySeries) }

    // ── Oturumluk (kalıcı olmayan) durum ──────────────────────────────────────
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var expandedSeries by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<WatchHistoryEntry?>(null) }
    var undoBatch by remember { mutableStateOf<UndoBatch?>(null) }

    val formatters = remember { TimeFormatters(KitsugiStrings.locale()) }

    // ── Türetilmiş veriler ────────────────────────────────────────────────────
    val availableSources = remember(history) { WatchHistoryQuery.availableSources(history) }
    LaunchedEffect(availableSources) {
        val current = sourceFilter
        if (current != null && availableSources.none { it.equals(current, ignoreCase = true) }) {
            sourceFilter = null
        }
    }

    val visible = remember(history, query, sortMode, progressFilter, mediaFilter, sourceFilter) {
        WatchHistoryQuery.sort(
            WatchHistoryQuery.applyFilters(
                entries = history,
                query = query,
                progressFilter = progressFilter,
                mediaFilter = mediaFilter,
                sourceFilter = sourceFilter
            ),
            sortMode
        )
    }
    val stats = remember(history) { WatchHistoryQuery.stats(history) }

    // Seçim modunda gruplama kapatılır: her bölüm tek tek seçilebilsin.
    val effectiveGrouping = groupBySeries && !selectionMode
    val groups = remember(visible, effectiveGrouping) {
        if (effectiveGrouping) WatchHistoryQuery.groupBySeries(visible) else emptyList()
    }
    // Gruplu görünümde "Bugün/Dün" başlıkları yerine grup sırası kullanılır.
    val useSections = !effectiveGrouping && sortMode == WatchHistorySortMode.RECENT
    val rows: List<HistoryRow> = remember(visible, useSections) {
        if (useSections) {
            WatchHistoryQuery.withSections(visible).map { section -> HistoryRow.Section(section) }
        } else {
            visible.map { HistoryRow.Item(it) }
        }
    }

    fun resetFilters() {
        query = ""
        progressFilter = WatchHistoryProgressFilter.ALL
        mediaFilter = WatchHistoryMediaFilter.ALL
        sourceFilter = null
        preferences.progressFilter = WatchHistoryProgressFilter.ALL
        preferences.mediaFilter = WatchHistoryMediaFilter.ALL
    }

    fun keyOf(entry: WatchHistoryEntry) = "${entry.animeId}::${entry.episode}"

    fun deleteEntries(entries: List<WatchHistoryEntry>, label: String) {
        if (entries.isEmpty()) return
        WatchHistoryManager.removeMany(entries.map { it.animeId to it.episode })
        undoBatch = UndoBatch(entries, label)
        if (selectionMode) {
            // Seçim modu açık kalsın; yalnızca silinen öğeler seçimden düşsün.
            selectedKeys = selectedKeys - entries.map { keyOf(it) }.toSet()
        }
    }

    fun play(entry: WatchHistoryEntry, restart: Boolean = false) {
        playHistoryEntry(context = context, entry = entry, restart = restart)
    }

    // Geri alma şeridi 6 saniye sonra kendiliğinden kapanır.
    LaunchedEffect(undoBatch) {
        if (undoBatch != null) {
            delay(6000)
            undoBatch = null
        }
    }

    // Sistem geri tuşu önce ekranın kendi durumunu geri alır: seçim modundan çıkar,
    // arama açıksa aramayı kapatır. Aksi hâlde kullanıcı seçim yaparken yanlışlıkla
    // ekrandan tamamen çıkıyordu.
    BackHandler(enabled = selectionMode || searchOpen) {
        if (selectionMode) {
            selectionMode = false
            selectedKeys = emptySet()
        } else {
            searchOpen = false
            query = ""
        }
    }

    if (menuOpen) {
        val sheetScroll = rememberScrollState()
        KitsugiSheetOrDialog(onDismiss = { menuOpen = false }, innerColumnScrollState = sheetScroll) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(sheetScroll).padding(vertical = 12.dp)) {
                Text(text = stringResource(R.string.page_controls), color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            FilterBar(
                sortMode = sortMode,
                onSortChange = {
                    sortMode = it
                    preferences.sortMode = it
                },
                progressFilter = progressFilter,
                onProgressFilterChange = {
                    progressFilter = it
                    preferences.progressFilter = it
                },
                mediaFilter = mediaFilter,
                onMediaFilterChange = {
                    mediaFilter = it
                    preferences.mediaFilter = it
                },
                // Seçim modunda liste düz gösterilir; çipin durumu da listeyle uyumlu olsun.
                groupBySeries = effectiveGrouping,
                onToggleGrouping = {
                    if (selectionMode) {
                        selectionMode = false
                        selectedKeys = emptySet()
                    }
                    groupBySeries = !groupBySeries
                    preferences.groupBySeries = groupBySeries
                },
                availableSources = availableSources,
                sourceFilter = sourceFilter,
                onSourceFilterChange = { sourceFilter = it },
                showMediaChips = stats.movieCount > 0 && stats.seriesCount > 0,
                accentColor = accentColor
            )
                FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (searchOpen) query = ""; searchOpen = !searchOpen; menuOpen = false }) {
                        Text(stringResource(if (searchOpen) R.string.wh_search_close else R.string.wh_search_hint))
                    }
                    TextButton(onClick = { menuOpen = false; selectionMode = true; selectedKeys = emptySet() }) {
                        Text(stringResource(R.string.wh_selection_mode))
                    }
                    TextButton(onClick = { menuOpen = false; showClearDialog = true }) {
                        Text(stringResource(R.string.tumunu_temizle))
                    }
                    TextButton(onClick = { resetFilters() }) {
                        Text(stringResource(R.string.page_reset_filters))
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(KitsugiColors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Üst çubuk ─────────────────────────────────────────────────────
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selectedKeys.size,
                    allSelected = selectedKeys.size == visible.size && visible.isNotEmpty(),
                    accentColor = accentColor,
                    onSelectAll = {
                        selectedKeys = if (selectedKeys.size == visible.size) {
                            emptySet()
                        } else {
                            visible.map { keyOf(it) }.toSet()
                        }
                    },
                    onDelete = { if (selectedKeys.isNotEmpty()) showDeleteSelectedDialog = true },
                    onClose = {
                        selectionMode = false
                        selectedKeys = emptySet()
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 2.dp)) {
                        Text(
                            text = stringResource(R.string.settings_watch_history),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (history.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.wh_summary_records, stats.totalEntries),
                                color = KitsugiColors.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                            Box {
                                Icon(imageVector = Icons.Menu, contentDescription = stringResource(R.string.page_controls), tint = accentColor)
                                if (query.isNotBlank() || progressFilter != WatchHistoryProgressFilter.ALL || mediaFilter != WatchHistoryMediaFilter.ALL || sourceFilter != null || sortMode != WatchHistorySortMode.RECENT) {
                                    Box(Modifier.align(Alignment.TopEnd).size(7.dp).background(accentColor, CircleShape))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = KitsugiColors.Border)

            if (history.isEmpty()) {
                EmptyHistoryState()
                return@Column
            }

            // ── Arama ─────────────────────────────────────────────────────────
            AnimatedVisibility(visible = searchOpen) {
                KitsugiCosmicSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { },
                    onClearQuery = { query = "" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    placeholder = stringResource(R.string.wh_search_hint),
                    height = 46.dp
                )
            }

            // ── Filtre / sıralama şeridi ──────────────────────────────────────


            // ── Özet şerit ────────────────────────────────────────────────────
            StatsStrip(stats = stats, accentColor = accentColor)

            // ── İçerik ────────────────────────────────────────────────────────
            when {
                visible.isEmpty() -> FilterEmptyState(
                    accentColor = accentColor,
                    onReset = { resetFilters() }
                )

                effectiveGrouping -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups, key = { "group_${it.animeId}" }) { group ->
                        SeriesGroupCard(
                            group = group,
                            expanded = expandedSeries.contains(group.animeId),
                            accentColor = accentColor,
                            formatters = formatters,
                            onToggle = {
                                expandedSeries = if (expandedSeries.contains(group.animeId)) {
                                    expandedSeries - group.animeId
                                } else {
                                    expandedSeries + group.animeId
                                }
                            },
                            onPlay = { play(group.inProgressEntry ?: group.latestEntry) },
                            onPlayEpisode = { entry -> play(entry) },
                            onDeleteEpisode = { entry ->
                                deleteEntries(listOf(entry), entry.animeTitle)
                            },
                            onDeleteSeries = {
                                WatchHistoryManager.removeSeries(group.animeId)
                                undoBatch = UndoBatch(group.entries, group.title)
                            }
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = rows,
                        key = { row ->
                            when (row) {
                                is HistoryRow.Section -> "section_${row.section.titleRes}"
                                is HistoryRow.Item -> "item_${row.entry.animeId}_${row.entry.episode}"
                            }
                        }
                    ) { row ->
                        when (row) {
                            is HistoryRow.Section -> SectionHeader(row.section)
                            is HistoryRow.Item -> {
                                val entry = row.entry
                                val key = keyOf(entry)
                                HistoryEntryCard(
                                    entry = entry,
                                    accentColor = accentColor,
                                    selectionMode = selectionMode,
                                    selected = selectedKeys.contains(key),
                                    formatters = formatters,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedKeys = if (selectedKeys.contains(key)) {
                                                selectedKeys - key
                                            } else {
                                                selectedKeys + key
                                            }
                                        } else {
                                            play(entry)
                                        }
                                    },
                                    onLongClick = {
                                        if (selectionMode) {
                                            selectedKeys = if (selectedKeys.contains(key)) {
                                                selectedKeys - key
                                            } else {
                                                selectedKeys + key
                                            }
                                        } else {
                                            actionTarget = entry
                                        }
                                    },
                                    // Tek dokunuşla silme yok: yanlışlıkla silmeyi önlemek için
                                    // işlem menüsü açılır (menüde "geçmişten kaldır" var).
                                    onMoreClick = { actionTarget = entry }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Geri al şeridi ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = undoBatch != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
            enter = slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(160))
        ) {
            val batch = undoBatch
            if (batch != null) {
                UndoBar(
                    message = batch.label,
                    count = batch.entries.size,
                    onUndo = {
                        WatchHistoryManager.restore(batch.entries)
                        undoBatch = null
                    },
                    onDismiss = { undoBatch = null }
                )
            }
        }
    }

    // ── Uzun basma işlem menüsü ───────────────────────────────────────────────
    val target = actionTarget
    if (target != null) {
        EntryActionDialog(
            entry = target,
            seriesEntryCount = history.count { it.animeId == target.animeId },
            accentColor = accentColor,
            onDismiss = { actionTarget = null },
            onPlay = {
                actionTarget = null
                play(target)
            },
            onRestart = {
                actionTarget = null
                play(target, restart = true)
            },
            onSelect = {
                actionTarget = null
                selectionMode = true
                selectedKeys = setOf(keyOf(target))
            },
            onDeleteEntry = {
                actionTarget = null
                deleteEntries(listOf(target), target.animeTitle)
            },
            onDeleteSeries = {
                actionTarget = null
                val seriesEntries = history.filter { it.animeId == target.animeId }
                WatchHistoryManager.removeSeries(target.animeId)
                undoBatch = UndoBatch(seriesEntries, target.animeTitle)
            }
        )
    }

    // ── Tümünü temizle ────────────────────────────────────────────────────────
    val allClearedLabel = stringResource(R.string.wh_all_cleared)
    val selectedRecordsLabel = stringResource(R.string.wh_selected_records)
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = KitsugiColors.Surface,
            titleContentColor = KitsugiColors.TextPrimary,
            title = { Text(stringResource(R.string.gecmisi_temizle), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.wh_clear_all_body),
                    color = KitsugiColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val snapshot = history
                    WatchHistoryManager.clearAll()
                    undoBatch = UndoBatch(snapshot, allClearedLabel)
                    showClearDialog = false
                }) {
                    Text(
                        stringResource(R.string.temizle),
                        color = KitsugiColors.AccentRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.iptal), color = accentColor)
                }
            }
        )
    }

    // ── Seçilenleri sil ───────────────────────────────────────────────────────
    if (showDeleteSelectedDialog) {
        val chosen = history.filter { selectedKeys.contains(keyOf(it)) }
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            containerColor = KitsugiColors.Surface,
            titleContentColor = KitsugiColors.TextPrimary,
            title = { Text(stringResource(R.string.wh_delete_selected_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.wh_delete_selected_body, chosen.size),
                    color = KitsugiColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedDialog = false
                    deleteEntries(chosen, selectedRecordsLabel)
                }) {
                    Text(
                        stringResource(R.string.temizle),
                        color = KitsugiColors.AccentRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.iptal), color = accentColor)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Üst çubuk / şeritler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    accentColor: Color,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.wh_clear_selection),
                tint = KitsugiColors.TextPrimary
            )
        }
        Text(
            text = stringResource(R.string.wh_selected_count, selectedCount),
            color = KitsugiColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSelectAll) {
            Icon(
                imageVector = Icons.Rounded.DoneAll,
                contentDescription = stringResource(
                    if (allSelected) R.string.wh_clear_selection else R.string.wh_select_all
                ),
                tint = if (allSelected) accentColor else KitsugiColors.TextMuted
            )
        }
        IconButton(onClick = onDelete, enabled = selectedCount > 0) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.wh_delete_selected),
                tint = if (selectedCount > 0) KitsugiColors.AccentRed else KitsugiColors.TextMuted
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    sortMode: WatchHistorySortMode,
    onSortChange: (WatchHistorySortMode) -> Unit,
    progressFilter: WatchHistoryProgressFilter,
    onProgressFilterChange: (WatchHistoryProgressFilter) -> Unit,
    mediaFilter: WatchHistoryMediaFilter,
    onMediaFilterChange: (WatchHistoryMediaFilter) -> Unit,
    groupBySeries: Boolean,
    onToggleGrouping: () -> Unit,
    availableSources: List<String>,
    sourceFilter: String?,
    onSourceFilterChange: (String?) -> Unit,
    showMediaChips: Boolean,
    accentColor: Color
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var sourceMenuOpen by remember { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Sıralama
        run {
            Box {
                FilterChip(
                    label = stringResource(sortMode.labelRes),
                    leadingIcon = Icons.Rounded.Sort,
                    selected = sortMode != WatchHistorySortMode.RECENT,
                    trailingIcon = Icons.Rounded.ArrowDropDown,
                    accentColor = accentColor,
                    onClick = { sortMenuOpen = true }
                )
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false }
                ) {
                    Text(
                        text = stringResource(R.string.wh_sort_title),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    WatchHistorySortMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(mode.labelRes),
                                    color = if (mode == sortMode) accentColor else KitsugiColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (mode == sortMode) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (mode == sortMode) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            onClick = {
                                onSortChange(mode)
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // Gruplama
        run {
            FilterChip(
                label = stringResource(R.string.wh_group_short),
                leadingIcon = if (groupBySeries) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                selected = groupBySeries,
                accentColor = accentColor,
                onClick = onToggleGrouping
            )
        }



        // İlerleme filtreleri
        WatchHistoryProgressFilter.values().forEach { filter ->
            run {
                FilterChip(
                    label = stringResource(filter.labelRes),
                    selected = progressFilter == filter,
                    accentColor = accentColor,
                    onClick = { onProgressFilterChange(filter) }
                )
            }
        }

        if (showMediaChips) {

            WatchHistoryMediaFilter.values().forEach { filter ->
                run {
                    FilterChip(
                        label = stringResource(filter.labelRes),
                        leadingIcon = when (filter) {
                            WatchHistoryMediaFilter.ALL -> null
                            WatchHistoryMediaFilter.SERIES -> Icons.Rounded.PlayArrow
                            WatchHistoryMediaFilter.MOVIES -> Icons.Rounded.Movie
                        },
                        selected = mediaFilter == filter,
                        accentColor = accentColor,
                        onClick = { onMediaFilterChange(filter) }
                    )
                }
            }
        }

        // Kaynak filtresi (tek kaynak varsa gösterilmez)
        if (availableSources.size > 1) {

            run {
                Box {
                    FilterChip(
                        label = sourceFilter ?: stringResource(R.string.wh_source_all),
                        selected = sourceFilter != null,
                        trailingIcon = Icons.Rounded.ArrowDropDown,
                        accentColor = accentColor,
                        onClick = { sourceMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = sourceMenuOpen,
                        onDismissRequest = { sourceMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.wh_source_all),
                                    color = if (sourceFilter == null) accentColor else KitsugiColors.TextPrimary,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = if (sourceFilter == null) {
                                {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            onClick = {
                                onSourceFilterChange(null)
                                sourceMenuOpen = false
                            }
                        )
                        availableSources.forEach { source ->
                            val isActive = source.equals(sourceFilter, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = source,
                                        color = if (isActive) accentColor else KitsugiColors.TextPrimary,
                                        fontSize = 14.sp
                                    )
                                },
                                leadingIcon = if (isActive) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    onSourceFilterChange(source)
                                    sourceMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(width = 1.dp, height = 18.dp)
            .background(KitsugiColors.Border, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val shape = RoundedCornerShape(50)
    val bg = if (selected) accentColor.copy(alpha = 0.16f) else KitsugiColors.SurfaceSoft
    val fg = if (selected) accentColor else KitsugiColors.TextSecondary
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(bg, shape)
            .border(
                width = 1.dp,
                color = if (selected) accentColor.copy(alpha = 0.7f) else KitsugiColors.Border.copy(alpha = 0.6f),
                shape = shape
            )
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun StatsStrip(stats: WatchHistoryStats, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.Surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatCell(
            icon = Icons.Rounded.History,
            value = stats.totalEntries.toString(),
            label = stringResource(R.string.wh_stat_records),
            tint = accentColor,
            modifier = Modifier.weight(1f)
        )
        StatCell(
            icon = Icons.Rounded.PlayArrow,
            value = stats.continueCount.toString(),
            label = stringResource(R.string.wh_stat_continue),
            tint = KitsugiColors.AccentGreen,
            modifier = Modifier.weight(1f)
        )
        StatCell(
            icon = Icons.Rounded.Movie,
            value = stats.titleCount.toString(),
            label = stringResource(R.string.wh_stat_titles),
            tint = KitsugiColors.AccentBlue,
            modifier = Modifier.weight(1f)
        )
        StatCell(
            icon = Icons.Rounded.Timer,
            value = formatWatchTime(stats.watchedMs),
            label = stringResource(R.string.wh_stat_watch_time),
            tint = KitsugiColors.AccentOrange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$value $label", color = KitsugiColors.TextSecondary, fontSize = 10.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeader(section: HistorySection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(section.titleRes),
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 1.dp)
                .background(KitsugiColors.Border, RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.wh_section_count, section.entries.size),
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Kayıt kartı
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryEntryCard(
    entry: WatchHistoryEntry,
    accentColor: Color,
    selectionMode: Boolean,
    selected: Boolean,
    formatters: TimeFormatters,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val sourceLabel = remember(entry.streamName, entry.streamTitle) {
        StreamLabelSanitizer.stripAudioTokens(WatchHistoryQuery.sourceLabel(entry))
    }
    val audioLabel = remember(entry.streamName, entry.streamTitle) { WatchHistoryQuery.audioLabel(entry) }
    val completed = WatchHistoryQuery.isCompleted(entry)
    val hasDuration = WatchHistoryQuery.hasKnownDuration(entry)
    val progress = WatchHistoryQuery.progressOf(entry)
    val directPlayable = WatchHistoryQuery.isDirectPlayable(entry)
    val remainingMinutes = WatchHistoryQuery.remainingMs(entry) / 60000L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) accentColor.copy(alpha = 0.10f) else KitsugiColors.SurfaceSoft,
                shape
            )
            .then(
                if (selected) Modifier.border(1.5.dp, accentColor, shape)
                else Modifier.border(1.dp, KitsugiColors.Border.copy(alpha = 0.35f), shape)
            )
            .tvClickable(shape = shape, onLongClick = onLongClick, onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Kapak ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(KitsugiColors.SurfaceStrong)
        ) {
            if (!entry.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = entry.posterUrl,
                    contentDescription = entry.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, KitsugiColors.Background.copy(alpha = 0.45f)),
                                startY = 40f
                            )
                        )
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (hasDuration) {
                // Kapak altında ince ilerleme şeridi — kartı okumadan nerede kalındığı görünür.
                CompactProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    accentColor = if (completed) KitsugiColors.AccentGreen else accentColor,
                    trackColor = Color.Black.copy(alpha = 0.45f),
                    barHeight = 2.dp
                )
            }
        }

        // ── Bilgiler ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = entry.animeTitle,
                color = KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (sourceLabel.isNotBlank()) {
                Text(
                    text = sourceLabel,
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HistoryBadge(
                    text = if (entry.isMovie) stringResource(R.string.wh_movie)
                    else "S${entry.season} · E${entry.episode}",
                    color = accentColor,
                    bgAlpha = 0.16f
                )
                if (completed) {
                    HistoryBadge(
                        text = stringResource(R.string.wh_completed),
                        color = KitsugiColors.AccentGreen,
                        bgAlpha = 0.15f
                    )
                }
                if (!entry.quality.isNullOrBlank()) {
                    val qualityColor = when {
                        entry.quality.contains("4K", true) || entry.quality.contains("2160", true) ->
                            KitsugiColors.AccentRed
                        entry.quality.contains("1080", true) -> KitsugiColors.AccentBlue
                        entry.quality.contains("720", true) -> KitsugiColors.AccentGreen
                        else -> KitsugiColors.AccentOrange
                    }
                    HistoryBadge(text = entry.quality, color = qualityColor, bgAlpha = 0.15f)
                }
                if (audioLabel != null) {
                    HistoryBadge(text = audioLabel, color = KitsugiColors.AccentTeal, bgAlpha = 0.14f)
                }
                if (!entry.source.isNullOrBlank()) {
                    HistoryBadge(text = entry.source, color = KitsugiColors.AccentPurple, bgAlpha = 0.12f)
                }
                if (directPlayable) {
                    HistoryBadge(
                        text = stringResource(R.string.aninda),
                        color = KitsugiColors.AccentGreen,
                        bgAlpha = 0.13f
                    )
                }
            }

            if (hasDuration && !completed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = WatchHistoryQuery.formatPosition(entry.positionMs),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " / ${WatchHistoryQuery.formatPosition(entry.durationMs)}",
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (remainingMinutes > 0) {
                            stringResource(R.string.wh_remaining_min, remainingMinutes.toInt())
                        } else {
                            stringResource(R.string.wh_remaining_less)
                        },
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp
                    )
                }
                CompactProgressBar(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accentColor,
                    trackColor = KitsugiColors.Border.copy(alpha = 0.35f),
                    barHeight = 3.dp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = relativeTimeLabel(entry.watchedAtMs, formatters),
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        // ── Sağ aksiyon ───────────────────────────────────────────────────────
        if (selectionMode) {
            val circleTint = if (selected) accentColor else KitsugiColors.TextMuted
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Check,
                contentDescription = if (selected) stringResource(R.string.wh_selected)
                else stringResource(R.string.wh_select_all),
                tint = circleTint,
                modifier = Modifier.size(22.dp)
            )
        } else {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.wh_more_actions),
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryBadge(
    text: String,
    color: Color,
    bgAlpha: Float
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = bgAlpha), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Diziye göre gruplu kart
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesGroupCard(
    group: WatchHistorySeries,
    expanded: Boolean,
    accentColor: Color,
    formatters: TimeFormatters,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
    onPlayEpisode: (WatchHistoryEntry) -> Unit,
    onDeleteEpisode: (WatchHistoryEntry) -> Unit,
    onDeleteSeries: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KitsugiColors.SurfaceSoft, shape)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.35f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(shape = shape, onClick = onToggle)
                .padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KitsugiColors.SurfaceStrong)
            ) {
                if (!group.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = group.posterUrl,
                        contentDescription = group.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = group.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Film gruplarında "Film · 1 bölüm" gibi tuhaf bir ifade çıkmasın.
                val latestCode = group.latestEpisodeCode
                Text(
                    text = if (latestCode != null) {
                        stringResource(R.string.wh_group_summary, latestCode, group.episodeCount)
                    } else {
                        stringResource(R.string.wh_movie)
                    },
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    HistoryBadge(
                        text = stringResource(R.string.wh_group_completed, group.completedCount, group.episodeCount),
                        color = KitsugiColors.AccentGreen,
                        bgAlpha = 0.14f
                    )
                    if (group.inProgressEntry != null) {
                        HistoryBadge(
                            text = stringResource(R.string.wh_group_in_progress),
                            color = accentColor,
                            bgAlpha = 0.16f
                        )
                    }
                }
                Text(
                    text = relativeTimeLabel(group.latestEntry.watchedAtMs, formatters),
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            // Hızlı devam düğmesi
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentColor, CircleShape)
                    .tvClickable(shape = CircleShape, onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.wh_continue),
                    tint = KitsugiColors.OnAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.wh_collapse)
                else stringResource(R.string.wh_expand),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 9.dp, end = 9.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))
                group.entries.forEach { entry ->
                    SeriesEpisodeRow(
                        entry = entry,
                        accentColor = accentColor,
                        formatters = formatters,
                        onClick = { onPlayEpisode(entry) },
                        onDelete = { onDeleteEpisode(entry) }
                    )
                }
                TextButton(onClick = onDeleteSeries, modifier = Modifier.align(Alignment.End)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = KitsugiColors.AccentRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.wh_delete_series),
                        color = KitsugiColors.AccentRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesEpisodeRow(
    entry: WatchHistoryEntry,
    accentColor: Color,
    formatters: TimeFormatters,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val completed = WatchHistoryQuery.isCompleted(entry)
    val progress = WatchHistoryQuery.progressOf(entry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KitsugiColors.Surface, shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (entry.isMovie) stringResource(R.string.wh_movie) else "E${entry.episode}",
            color = if (completed) KitsugiColors.AccentGreen else accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(38.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (completed) stringResource(R.string.wh_completed)
                    else if (WatchHistoryQuery.hasKnownDuration(entry)) {
                        stringResource(
                            R.string.wh_progress_percent,
                            WatchHistoryQuery.percentOf(entry)
                        )
                    } else {
                        stringResource(R.string.wh_not_started_short)
                    },
                    color = KitsugiColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = relativeTimeLabel(entry.watchedAtMs, formatters),
                    color = KitsugiColors.TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            if (WatchHistoryQuery.hasKnownDuration(entry)) {
                CompactProgressBar(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = if (completed) KitsugiColors.AccentGreen else accentColor,
                    trackColor = KitsugiColors.Border.copy(alpha = 0.4f),
                    barHeight = 2.dp
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Boş durumlar / geri al / işlem menüsü
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyHistoryState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.inline_you_have_no_watch_history_28cb4be),
                color = KitsugiColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.inline_videos_you_start_watching_will_11087ce),
                color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun FilterEmptyState(accentColor: Color, onReset: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.wh_empty_filter_title),
                color = KitsugiColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.wh_empty_filter_body),
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp
            )
            TextButton(onClick = onReset) {
                Text(
                    text = stringResource(R.string.wh_reset_filters),
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun UndoBar(
    message: String,
    count: Int,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KitsugiColors.SurfaceStrong, shape)
            .border(1.dp, KitsugiColors.Border, shape)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (count > 1) stringResource(R.string.wh_deleted_many, count) else message,
            color = KitsugiColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onUndo) {
            Text(
                text = stringResource(R.string.wh_undo),
                color = KitsugiColors.AccentGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.wh_dismiss),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EntryActionDialog(
    entry: WatchHistoryEntry,
    seriesEntryCount: Int,
    accentColor: Color,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onSelect: () -> Unit,
    onDeleteEntry: () -> Unit,
    onDeleteSeries: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Surface,
        titleContentColor = KitsugiColors.TextPrimary,
        title = {
            Column {
                Text(
                    text = entry.animeTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isMovie) stringResource(R.string.wh_movie)
                    else stringResource(R.string.wh_episode_of_season, entry.season, entry.episode),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column {
                DialogAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(
                        if (WatchHistoryQuery.isCompleted(entry)) R.string.wh_restart
                        else R.string.wh_continue
                    ),
                    tint = accentColor,
                    onClick = if (WatchHistoryQuery.isCompleted(entry)) onRestart else onPlay
                )
                if (!WatchHistoryQuery.isCompleted(entry) && WatchHistoryQuery.hasKnownDuration(entry)) {
                    DialogAction(
                        icon = Icons.Rounded.Replay,
                        label = stringResource(R.string.wh_restart),
                        tint = KitsugiColors.TextSecondary,
                        onClick = onRestart
                    )
                }
                DialogAction(
                    icon = Icons.Rounded.SelectAll,
                    label = stringResource(R.string.wh_selection_mode),
                    tint = KitsugiColors.TextSecondary,
                    onClick = onSelect
                )
                DialogAction(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.wh_delete_episode),
                    tint = KitsugiColors.AccentRed,
                    onClick = onDeleteEntry
                )
                if (seriesEntryCount > 1) {
                    DialogAction(
                        icon = Icons.Rounded.DeleteSweep,
                        label = stringResource(R.string.wh_delete_series),
                        tint = KitsugiColors.AccentRed,
                        onClick = onDeleteSeries
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.iptal), color = accentColor)
            }
        }
    )
}

@Composable
private fun DialogAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text = label, color = KitsugiColors.TextPrimary, fontSize = 14.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Yardımcılar
// ─────────────────────────────────────────────────────────────────────────────

/** Listede karışık iki satır türü: bölüm başlığı ve kayıt. */
private sealed interface HistoryRow {
    data class Section(val section: HistorySection) : HistoryRow
    data class Item(val entry: WatchHistoryEntry) : HistoryRow
}

/** Geri alınabilecek silme işlemi. */
private data class UndoBatch(val entries: List<WatchHistoryEntry>, val label: String)

/** Tek bir yerelleştirilmiş biçimlendirici seti (liste başına bir kez oluşturulur). */
private class TimeFormatters(locale: Locale) {
    val clock: SimpleDateFormat = SimpleDateFormat("HH:mm", locale)
    val date: SimpleDateFormat = SimpleDateFormat("d MMM yyyy", locale)
    val dateTime: SimpleDateFormat = SimpleDateFormat("d MMM yyyy · HH:mm", locale)
}

/**
 * "Az önce / 12 dk önce / Bugün 21:40 / Dün 23:48 / 31 Eki 2026" biçiminde
 * insan diliyle zaman etiketi.
 */
@Composable
private fun relativeTimeLabel(ms: Long, formatters: TimeFormatters): String {
    val now = remember(ms) { System.currentTimeMillis() }
    val minutes = (now - ms).coerceAtLeast(0L) / 60000L
    val dayOffset = WatchHistoryQuery.dayOffset(ms, now)
    val date = java.util.Date(ms)
    return when {
        minutes < 1 -> stringResource(R.string.wh_time_now)
        minutes < 60 -> stringResource(R.string.wh_time_minutes_ago, minutes.toInt())
        dayOffset == 0 -> stringResource(R.string.wh_time_today, formatters.clock.format(date))
        dayOffset == 1 -> stringResource(R.string.wh_time_yesterday, formatters.clock.format(date))
        dayOffset < 365 -> formatters.dateTime.format(date)
        else -> formatters.date.format(date)
    }
}

/** Yaklaşık toplam izleme süresi: "3 sa 12 dk" / "42 dk" / "< 1 dk". */
@Composable
private fun formatWatchTime(totalMs: Long): String {
    val totalMinutes = totalMs / 60000L
    return when {
        totalMinutes < 1L -> stringResource(R.string.wh_time_under_minute)
        totalMinutes < 60L -> stringResource(R.string.wh_time_minutes, totalMinutes.toInt())
        else -> stringResource(
            R.string.wh_time_hours_minutes,
            (totalMinutes / 60L).toInt(),
            (totalMinutes % 60L).toInt()
        )
    }
}

/**
 * Bir kaydı oynatır.
 *
 * - [restart] `true` ise (ya da kayıt tamamlanmışsa) bölüm **baştan** açılır.
 * - Kayıtlı akış adresi hâlâ tazeyse oynatıcı doğrudan açılır (kaynak seçimi atlanır),
 *   aksi hâlde kaynak seçici otomatik oynatmayla başlatılır.
 */
private fun playHistoryEntry(
    context: android.content.Context,
    entry: WatchHistoryEntry,
    restart: Boolean
) {
    val resumePositionMs = if (restart || WatchHistoryQuery.isCompleted(entry)) 0L else entry.positionMs
    val episodeTitle = KitsugiStrings.get(R.string.title_bolum_episode, entry.animeTitle, entry.episode)
    val savedUrl = entry.streamUrl

    if (WatchHistoryQuery.isDirectPlayable(entry) && !savedUrl.isNullOrBlank()) {
        KitsugiFullscreenPlayerActivity.startWithStreamUrls(
            context = context,
            videoUrl = savedUrl,
            title = episodeTitle,
            headers = entry.streamHeaders,
            subtitles = emptyList(),
            malId = entry.malId,
            aniListId = entry.aniListId,
            tmdbId = entry.tmdbId,
            season = entry.season,
            episode = entry.episode,
            animeTitle = entry.animeTitle,
            posterUrl = entry.posterUrl,
            isMovie = entry.isMovie,
            cs3Url = entry.cs3Url,
            cs3ApiName = entry.cs3ApiName,
            resumePositionMs = resumePositionMs
        )
        return
    }

    // Kayıtlı adres bayatladı: kaynak seçicide oynatıcı kaynağı yeniden çözülsün.
    // "Baştan başlat" bu yolda da çalışmalı: oynatıcı, açılışta geçmişteki konumu
    // okuyup oradan devam ettiği için konum burada sıfırlanır (zaman damgası korunur).
    if (restart || WatchHistoryQuery.isCompleted(entry)) {
        WatchHistoryManager.resetPosition(entry.animeId, entry.episode)
    }
    if (!entry.source.isNullOrBlank()) {
        context.getSharedPreferences("KitsugiStreamPrefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("last_addon_name", entry.source)
            .apply()
    }
    KitsugiStreamActivity.start(
        context = context,
        malId = entry.malId,
        aniListId = entry.aniListId,
        tmdbId = entry.tmdbId,
        episode = entry.episode,
        season = entry.season,
        isMovie = entry.isMovie,
        title = entry.animeTitle,
        posterUrl = entry.posterUrl,
        isAutoplay = true,
        cs3Url = entry.cs3Url,
        cs3ApiName = entry.cs3ApiName
    )
}
