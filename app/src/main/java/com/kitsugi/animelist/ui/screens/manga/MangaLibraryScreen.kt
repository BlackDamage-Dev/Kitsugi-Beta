package com.kitsugi.animelist.ui.screens.manga

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiDropdownItem
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiScrollToTopFab
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch

/**
 * Manga **kitaplığı** ekranı.
 *
 * Mihon/TachiyomiSY'deki kitaplık karşılığı: kullanıcı takip ettiği serileri tek yerde
 * görür, kategorilere ayırır, ilerlemesini ve indirme durumunu görür ve tek dokunuşla
 * kaldığı yerden devam eder.
 *
 * Tasarım mevcut sayfalarla aynı dilde: sticky başlık yerine sabit üst satır + kaydırmada
 * görünen "yukarı çık" düğmesi (Keşfet'teki [KitsugiScrollToTopFab] ile birebir aynı).
 *
 * Veri kaynağı Room'dur ([com.kitsugi.animelist.data.local.MangaLibraryEntity]); ağ isteği
 * yalnızca kullanıcı bir seriye girdiğinde detay ekranında yapılır.
 */
@Composable
fun MangaLibraryScreen(
    availableSources: List<MangaSource>,
    onOpenManga: (MangaSource, com.kitsugi.animelist.data.manga.MangaDetails) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalKitsugiAccent.current
    val viewModel: MangaLibraryViewModel = viewModel(
        key = "manga_library",
        factory = MangaLibraryViewModel.Factory(context) { availableSources }
    )
    val uiState by viewModel.uiState.collectAsState()
    val rows = remember(uiState) { viewModel.visibleRows(uiState) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var sortMenuOpen by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<MangaLibraryViewModel.LibraryRow?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(KitsugiColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Üst satır ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.geri),
                        tint = KitsugiColors.TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.manga_library_title),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        // Kitaplığın özeti: kaç seri, kaç bölüm indirilmiş.
                        text = stringResource(
                            R.string.manga_library_summary,
                            rows.size,
                            uiState.rows.sumOf { it.downloadedChapters }
                        ),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Rounded.Sort,
                            contentDescription = stringResource(R.string.manga_library_sort),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    KitsugiDropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                        dialogTitle = stringResource(R.string.manga_library_sort)
                    ) {
                        MangaLibraryViewModel.SortMode.entries.forEach { mode ->
                            KitsugiDropdownItem(
                                text = mode.label(),
                                selected = uiState.sortMode == mode,
                                onClick = {
                                    viewModel.setSortMode(mode)
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.reload() }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.yenile),
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // ── Kategori çipleri ────────────────────────────────────────────
            if (uiState.categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item(key = "__all_categories__") {
                        LibraryChip(
                            text = stringResource(R.string.manga_library_all),
                            selected = uiState.selectedCategory == null,
                            accent = accent,
                            onClick = { viewModel.setCategoryFilter(null) }
                        )
                    }
                    items(uiState.categories, key = { it }) { category ->
                        LibraryChip(
                            text = category,
                            selected = uiState.selectedCategory == category,
                            accent = accent,
                            onClick = {
                                viewModel.setCategoryFilter(
                                    if (uiState.selectedCategory == category) null else category
                                )
                            }
                        )
                    }
                }
            }

            // ── Liste / boş durum ───────────────────────────────────────────
            when {
                uiState.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader(size = 46.dp)
                }

                rows.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.manga_library_empty_title),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.manga_library_empty_body),
                        color = KitsugiColors.TextMuted,
                        fontSize = 12.sp
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.entity.id }) { row ->
                        MangaLibraryCard(
                            row = row,
                            accent = accent,
                            onOpen = {
                                val source = availableSources.firstOrNull {
                                    it.name == row.entity.sourceName
                                }
                                if (source != null) {
                                    onOpenManga(
                                        source,
                                        com.kitsugi.animelist.data.manga.MangaDetails(
                                            url = row.entity.url,
                                            title = row.entity.title,
                                            thumbnailUrl = row.entity.thumbnailUrl,
                                            author = row.entity.author,
                                            source = row.entity.sourceName
                                        )
                                    )
                                }
                            },
                            onEdit = { editingRow = row }
                        )
                    }
                }
            }
        }

        // Keşfet ile birebir aynı yukarı çık düğmesi.
        val showScrollToTop by remember {
            androidx.compose.runtime.derivedStateOf {
                listState.firstVisibleItemIndex > 2 || listState.firstVisibleItemScrollOffset > 400
            }
        }
        KitsugiScrollToTopFab(
            visible = showScrollToTop,
            isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            bottomBarScrollState = null,
            fallbackBottomBarVisible = false,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }

    // ── Kategori düzenleme / kitaplıktan çıkarma ───────────────────────────
    val editing = editingRow
    if (editing != null) {
        var categoryText by remember(editing.entity.id) {
            mutableStateOf(editing.entity.category)
        }
        KitsugiSheetOrDialog(
            onDismiss = { editingRow = null },
            heightFraction = 0.42f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = editing.entity.title,
                        color = KitsugiColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { editingRow = null }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.kapat),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.manga_library_category_hint),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.manga_library_category_placeholder),
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitsugiButton(
                        onClick = {
                            viewModel.setCategory(editing, categoryText.trim())
                            editingRow = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.kaydet), fontSize = 12.sp)
                    }
                    KitsugiButton(
                        onClick = {
                            viewModel.removeFromLibrary(editing)
                            editingRow = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.manga_library_remove),
                            fontSize = 12.sp,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            }
        }
    }
}

/** Kitaplık kartı: kapak · başlık · kategori · ilerleme · indirme rozeti. */
@Composable
private fun MangaLibraryCard(
    row: MangaLibraryViewModel.LibraryRow,
    accent: Color,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    val entity = row.entity
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceSoft)
            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(KitsugiColors.SurfaceStrong)
        ) {
            KitsugiNsfwImage(
                model = entity.thumbnailUrl,
                contentDescription = entity.title,
                isAdult = false,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.title,
                color = KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                if (entity.category.isNotBlank()) append(entity.category)
                if (entity.lastChapterName != null) {
                    if (isNotEmpty()) append(" · ")
                    append(entity.lastChapterName)
                } else if (!row.sourceAvailable) {
                    if (isNotEmpty()) append(" · ")
                    append(stringResource(R.string.manga_library_source_missing))
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (row.sourceAvailable) KitsugiColors.TextMuted else Color(0xFFFFA94D),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entity.totalChapters > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = {
                            (entity.readChapters.toFloat() / entity.totalChapters.toFloat())
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape),
                        color = accent,
                        trackColor = KitsugiColors.SurfaceStrong
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${entity.readChapters}/${entity.totalChapters}",
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
        if (row.downloadedChapters > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = stringResource(
                        R.string.manga_downloaded_count,
                        row.downloadedChapters
                    ),
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = row.downloadedChapters.toString(),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.manga_library_edit),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun LibraryChip(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.18f) else KitsugiColors.SurfaceSoft)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) accent else KitsugiColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun MangaLibraryViewModel.SortMode.label(): String = when (this) {
    MangaLibraryViewModel.SortMode.RecentlyUpdated ->
        stringResource(R.string.manga_library_sort_recent)
    MangaLibraryViewModel.SortMode.TitleAsc ->
        stringResource(R.string.manga_library_sort_title_asc)
    MangaLibraryViewModel.SortMode.TitleDesc ->
        stringResource(R.string.manga_library_sort_title_desc)
    MangaLibraryViewModel.SortMode.UnreadDesc ->
        stringResource(R.string.manga_library_sort_unread)
    MangaLibraryViewModel.SortMode.LastRead ->
        stringResource(R.string.manga_library_sort_last_read)
}
