package com.kitsugi.animelist.ui.screens.offline

import androidx.compose.foundation.ExperimentalFoundationApi
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.kitsugi.animelist.ui.icons.KitsugiIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.ui.components.KitsugiAnimatedDeleteButton
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Izgaranın kenar boşluğu (LazyVerticalGrid contentPadding ile aynı olmalı). */
private val ImageGridPadding = 12.dp

/** Kutucuklar arası boşluk. */
private val ImageGridSpacing = 10.dp

/**
 * Ekran genişliğine göre sütun sayısı.
 *
 * Sabit sütun sayısı YOK: hedeflenen kutucuk genişliği (`tileSize`) ekran
 * genişliğine bölünür. Böylece
 * - dikey telefonda 2–3,
 * - yatay telefonda 4–6,
 * - tablet / katlanabilir / geniş ekranda 7–10
 * sütun otomatik çıkar. Ekran döndürüldüğünde veya pencere yeniden
 * boyutlandırıldığında ızgara kendiliğinden yeniden akar.
 */
private fun imageGridColumnCount(availableWidthPx: Float, spacingPx: Float, targetCellPx: Float): Int {
    val width = availableWidthPx - spacingPx
    if (width <= 0f) return 2
    val columns = ((width + spacingPx) / (targetCellPx + spacingPx)).toInt()
    return columns.coerceIn(2, 10)
}

/** Izgara satırı: ya grup başlığı ya da tek bir resim. */
private sealed interface ImageGridEntry {
    data class Header(val groupKey: String, val group: DownloadedImageGroup) : ImageGridEntry
    data class Photo(val groupKey: String, val item: DownloadedImageItem, val indexInGroup: Int) : ImageGridEntry
}

private fun sortImages(list: List<DownloadedImageItem>, sort: DownloadsSortOrder): List<DownloadedImageItem> =
    when (sort) {
        DownloadsSortOrder.NEWEST -> list.sortedByDescending { it.lastModified }
        DownloadsSortOrder.OLDEST -> list.sortedBy { it.lastModified }
        DownloadsSortOrder.NAME -> list.sortedBy { it.title.lowercase(Locale.ROOT) }
        DownloadsSortOrder.SIZE -> list.sortedByDescending { it.fileSizeBytes }
    }

@Composable
private fun sortLabel(sort: DownloadsSortOrder): String = stringResource(
    when (sort) {
        DownloadsSortOrder.NEWEST -> R.string.downloads_sort_newest
        DownloadsSortOrder.OLDEST -> R.string.downloads_sort_oldest
        DownloadsSortOrder.NAME -> R.string.downloads_sort_name
        DownloadsSortOrder.SIZE -> R.string.downloads_sort_size
    }
)

@Composable
private fun tileSizeLabel(size: DownloadsTileSize): String = stringResource(
    when (size) {
        DownloadsTileSize.AUTO -> R.string.downloads_tile_auto
        DownloadsTileSize.COMPACT -> R.string.downloads_tile_compact
        DownloadsTileSize.LARGE -> R.string.downloads_tile_large
    }
)

/**
 * İndirilen resimler sekmesi.
 *
 * - Kutucuk gösterimi **dikey/yatay ve ekran boyutuna göre otomatik** (sütun sayısı
 *   hesaplanır, kart genişliği hücreye oturur).
 * - Sıralama, gruplama ve kutucuk yoğunluğu kullanıcıya bırakıldı; seçim kalıcı.
 * - Uzun basınca **çoklu seçim** başlar; seçilenler tek seferde silinebilir.
 */
@Composable
fun DownloadedImagesTab(
    images: List<DownloadedImageItem>,
    accentColor: Color,
    onRefresh: () -> Unit,
    onDeleteImages: (List<File>) -> Unit = {},
    query: String = ""
) {
    val context = LocalContext.current

    // ── Kalıcı görünüm tercihleri ─────────────────────────────────────────────
    var tileSize by remember { mutableStateOf(DownloadsUiPrefs.tileSize(context)) }
    var sort by remember { mutableStateOf(DownloadsUiPrefs.imageSort(context)) }
    var layout by remember { mutableStateOf(DownloadsUiPrefs.imageLayout(context)) }

    // ── Galeri durumu (gruplu ve tek liste modunda aynı akış kullanılır) ─────
    var galleryItemsSource by remember { mutableStateOf<List<DownloadedImageItem>?>(null) }
    var galleryTitle by remember { mutableStateOf("") }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }
    var hiddenPaths by remember { mutableStateOf(setOf<String>()) }

    // ── Çoklu seçim ──────────────────────────────────────────────────────────
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var deleteSelectionPending by remember { mutableStateOf(false) }
    var groupPendingDelete by remember { mutableStateOf<DownloadedImageGroup?>(null) }

    val visibleImages = remember(images, hiddenPaths) {
        images.filterNot { it.file.absolutePath in hiddenPaths }
    }

    val queryText = query.trim().lowercase(Locale.ROOT)
    val searchedImages = remember(visibleImages, queryText) {
        if (queryText.isBlank()) visibleImages
        else visibleImages.filter { it.title.lowercase(Locale.ROOT).contains(queryText) }
    }

    val orderedImages = remember(searchedImages, sort) { sortImages(searchedImages, sort) }

    // Gruplama: içerik adına göre (mevcut davranış korunur) — arka planda hesaplanır.
    val groups by produceState(initialValue = emptyList<DownloadedImageGroup>(), key1 = searchedImages, key2 = sort) {
        value = if (searchedImages.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                val grouped = searchedImages
                    .groupBy { normalizeImageGroupKey(it.title) }
                    .map { (key, list) ->
                        DownloadedImageGroup(
                            displayName = list.maxByOrNull { it.lastModified }?.title?.trim()
                                ?.ifBlank { KitsugiStrings.get(R.string.downloads_unknown_content) }
                                ?: KitsugiStrings.get(R.string.downloads_unknown_content),
                            items = sortImages(list, sort)
                        )
                    }
                when (sort) {
                    DownloadsSortOrder.OLDEST -> grouped.sortedBy { g -> g.items.minOfOrNull { it.lastModified } ?: 0L }
                    DownloadsSortOrder.NAME -> grouped.sortedBy { it.displayName.lowercase(Locale.ROOT) }
                    DownloadsSortOrder.SIZE -> grouped.sortedByDescending { g -> g.items.sumOf { it.fileSizeBytes } }
                    DownloadsSortOrder.NEWEST -> grouped.sortedByDescending { g -> g.items.maxOfOrNull { it.lastModified } ?: 0L }
                }
            }
        }
    }

    // Izgara girdileri: GRUPLU modda başlık + resimler, TEK LİSTE modunda yalnızca resimler.
    val entries: List<ImageGridEntry> = remember(groups, orderedImages, layout) {
        if (layout == DownloadsImageLayout.FLAT) {
            orderedImages.mapIndexed { index, item ->
                ImageGridEntry.Photo(groupKey = "", item = item, indexInGroup = index)
            }
        } else {
            buildList<ImageGridEntry> {
                groups.forEach { group ->
                    val key = normalizeImageGroupKey(group.displayName)
                    add(ImageGridEntry.Header(groupKey = key, group = group))
                    group.items.forEachIndexed { index, item ->
                        add(ImageGridEntry.Photo(groupKey = key, item = item, indexInGroup = index))
                    }
                }
            }
        }
    }

    // Tek liste modunda galeri başlığı olarak sekme adı kullanılır (composable dışı
    // lambda içinde stringResource çağrılamadığı için burada çözülür).
    val downloadsTabLabel = stringResource(R.string.settings_downloads)

    // ── Galeri (tam ekran görüntüleyici) ─────────────────────────────────────
    val gallerySource = galleryItemsSource
    if (gallerySource != null && gallerySource.isNotEmpty()) {
        val filteredSource = remember(gallerySource, hiddenPaths) {
            gallerySource.filterNot { it.file.absolutePath in hiddenPaths }
        }
        // Galerideki tüm resimler silindiyse görüntüleyiciyi kapat (kompozisyon
        // sırasında state yazmamak için effect kullanılıyor).
        LaunchedEffect(filteredSource.isEmpty()) {
            if (filteredSource.isEmpty()) galleryItemsSource = null
        }
        if (filteredSource.isNotEmpty()) {
            val galleryItems = remember(filteredSource) {
                filteredSource.map { img ->
                    GalleryItem(
                        url = "file://${img.file.absolutePath}",
                        source = "Kitsugi",
                        category = GalleryCategory.OTHER,
                        description = img.title,
                        width = img.width.takeIf { it > 0 },
                        height = img.height.takeIf { it > 0 }
                    )
                }
            }
            KitsugiImageGalleryDialog(
                galleryItems = galleryItems,
                initialIndex = galleryInitialIndex.coerceIn(0, galleryItems.size - 1),
                title = galleryTitle,
                allowDownload = false,
                allowDelete = true,
                onItemsDeleted = { urls ->
                    hiddenPaths = hiddenPaths + urls.map { it.removePrefix("file://") }
                    onRefresh()
                },
                onDismiss = {
                    galleryItemsSource = null
                    galleryInitialIndex = 0
                }
            )
        }
    }

    val openGallery: (List<DownloadedImageItem>, String, Int) -> Unit = { source, title, index ->
        galleryItemsSource = source
        galleryTitle = title
        galleryInitialIndex = index
    }

    val toggleSelection: (DownloadedImageItem) -> Unit = { item ->
        val path = item.file.absolutePath
        selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
    }

    val exitSelection: () -> Unit = {
        selectionMode = false
        selectedPaths = emptySet()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Araç çubuğu: seçim modu ya da görünüm seçenekleri ────────────────
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = exitSelection, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.downloads_clear_selection),
                        tint = KitsugiColors.TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.downloads_selected_count, selectedPaths.size),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        selectedPaths = if (selectedPaths.size == searchedImages.size) {
                            emptySet()
                        } else {
                            searchedImages.mapTo(LinkedHashSet()) { it.file.absolutePath }
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SelectAll,
                        contentDescription = stringResource(R.string.downloads_select_all),
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = { if (selectedPaths.isNotEmpty()) deleteSelectionPending = true },
                    modifier = Modifier.size(34.dp),
                    enabled = selectedPaths.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = if (selectedPaths.isEmpty()) KitsugiColors.TextMuted else KitsugiColors.AccentRed,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        } else if (visibleImages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DownloadsFilterChip(
                    label = stringResource(R.string.downloads_refresh),
                    selected = false,
                    accentColor = accentColor,
                    leadingIcon = Icons.Rounded.Refresh,
                    onClick = onRefresh
                )
                DownloadsFilterChip(
                    label = stringResource(R.string.downloads_select_images),
                    selected = false,
                    accentColor = accentColor,
                    leadingIcon = Icons.Rounded.CheckCircle,
                    onClick = {
                        selectionMode = true
                        selectedPaths = emptySet()
                    }
                )
                Box {
                    var sortMenuOpen by remember { mutableStateOf(false) }
                    DownloadsFilterChip(
                        label = sortLabel(sort),
                        selected = sort != DownloadsSortOrder.NEWEST,
                        accentColor = accentColor,
                        leadingIcon = Icons.Rounded.Sort,
                        onClick = { sortMenuOpen = true }
                    )
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        DownloadsSortOrder.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sortLabel(option),
                                        fontSize = 13.sp,
                                        fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal,
                                        color = if (option == sort) accentColor else KitsugiColors.TextPrimary
                                    )
                                },
                                onClick = {
                                    sort = option
                                    DownloadsUiPrefs.setImageSort(context, option)
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }
                DownloadsFilterChip(
                    label = if (layout == DownloadsImageLayout.GROUPED) {
                        stringResource(R.string.downloads_group_on)
                    } else {
                        stringResource(R.string.downloads_group_off)
                    },
                    selected = layout == DownloadsImageLayout.FLAT,
                    accentColor = accentColor,
                    leadingIcon = Icons.Rounded.Layers,
                    onClick = {
                        val next = if (layout == DownloadsImageLayout.GROUPED) {
                            DownloadsImageLayout.FLAT
                        } else {
                            DownloadsImageLayout.GROUPED
                        }
                        layout = next
                        DownloadsUiPrefs.setImageLayout(context, next)
                    }
                )
                DownloadsFilterChip(
                    label = tileSizeLabel(tileSize),
                    selected = tileSize != DownloadsTileSize.AUTO,
                    accentColor = accentColor,
                    leadingIcon = Icons.Rounded.GridView,
                    onClick = {
                        val next = when (tileSize) {
                            DownloadsTileSize.AUTO -> DownloadsTileSize.COMPACT
                            DownloadsTileSize.COMPACT -> DownloadsTileSize.LARGE
                            DownloadsTileSize.LARGE -> DownloadsTileSize.AUTO
                        }
                        tileSize = next
                        DownloadsUiPrefs.setTileSize(context, next)
                    }
                )
            }
        }

        when {
            visibleImages.isEmpty() -> {
                DownloadsEmptyState(
                    icon = Icons.Rounded.ImageNotSupported,
                    title = stringResource(R.string.inline_no_downloaded_images_found_yet_02e4efb),
                    subtitle = stringResource(R.string.inline_images_downloaded_from_the_gallery_af98e85)
                )
            }
            entries.isEmpty() -> {
                DownloadsEmptyState(
                    icon = Icons.Rounded.ImageNotSupported,
                    title = stringResource(R.string.downloads_no_results),
                    subtitle = stringResource(R.string.downloads_no_results_hint)
                )
            }
            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val columns = remember(maxWidth, tileSize) {
                        val availablePx = with(density) { (maxWidth - ImageGridPadding * 2).toPx() }
                        val spacingPx = with(density) { ImageGridSpacing.toPx() }
                        val targetPx = with(density) { tileSize.targetCellWidthDp.dp.toPx() }
                        imageGridColumnCount(availablePx, spacingPx, targetPx)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ImageGridPadding,
                            end = ImageGridPadding,
                            top = 4.dp,
                            bottom = 28.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(ImageGridSpacing),
                        verticalArrangement = Arrangement.spacedBy(ImageGridSpacing)
                    ) {
                        // PERF: Tek tek `item {}` eklemek yerine sayı+anahtar+span tabanlı
                        // `items` kullanılıyor; yüzlerce resimde içerik lambası yeniden
                        // kurulmuyor, yalnızca görünür hücreler compose ediliyor.
                        items(
                            count = entries.size,
                            key = { index ->
                                when (val entry = entries[index]) {
                                    is ImageGridEntry.Header -> "header_${entry.groupKey}"
                                    is ImageGridEntry.Photo -> entry.item.file.absolutePath
                                }
                            },
                            span = { index ->
                                when (entries[index]) {
                                    is ImageGridEntry.Header -> GridItemSpan(maxLineSpan)
                                    else -> GridItemSpan(1)
                                }
                            },
                            contentType = { index ->
                                if (entries[index] is ImageGridEntry.Header) "header" else "photo"
                            }
                        ) { index ->
                            when (val entry = entries[index]) {
                                is ImageGridEntry.Header -> {
                                    ImageGroupHeader(
                                        group = entry.group,
                                        accentColor = accentColor,
                                        onDeleteGroup = { groupPendingDelete = entry.group }
                                    )
                                }

                                is ImageGridEntry.Photo -> {
                                    val isSelected = entry.item.file.absolutePath in selectedPaths
                                    DownloadedImageCard(
                                        item = entry.item,
                                        accentColor = accentColor,
                                        selected = isSelected,
                                        selectionMode = selectionMode,
                                        onClick = {
                                            if (selectionMode) {
                                                toggleSelection(entry.item)
                                            } else if (layout == DownloadsImageLayout.FLAT) {
                                                openGallery(orderedImages, downloadsTabLabel, entry.indexInGroup)
                                            } else {
                                                val group = groups.firstOrNull {
                                                    normalizeImageGroupKey(it.displayName) == entry.groupKey
                                                }
                                                if (group != null) {
                                                    openGallery(group.items, group.displayName, entry.indexInGroup)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedPaths = setOf(entry.item.file.absolutePath)
                                            } else {
                                                toggleSelection(entry.item)
                                            }
                                        },
                                        onOpenFolder = {
                                            openFolderInFileManager(
                                                context,
                                                entry.item.file.parentFile ?: entry.item.file
                                            )
                                        },
                                        onDelete = { onDeleteImages(listOf(entry.item.file)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Seçilenleri silme onayı ──────────────────────────────────────────────
    if (deleteSelectionPending) {
        AlertDialog(
            onDismissRequest = { deleteSelectionPending = false },
            containerColor = KitsugiColors.SurfaceSoft,
            title = {
                Text(
                    text = stringResource(R.string.downloads_delete_selected_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.downloads_delete_selected_message, selectedPaths.size),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val files = searchedImages
                        .filter { it.file.absolutePath in selectedPaths }
                        .map { it.file }
                    deleteSelectionPending = false
                    exitSelection()
                    onDeleteImages(files)
                }) {
                    Text(stringResource(R.string.action_delete), color = KitsugiColors.AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectionPending = false }) {
                    Text(stringResource(R.string.vazgec), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }

    // ── Grup silme onayı ─────────────────────────────────────────────────────
    groupPendingDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupPendingDelete = null },
            containerColor = KitsugiColors.SurfaceSoft,
            title = {
                Text(
                    text = stringResource(R.string.downloads_delete_group_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.downloads_delete_group_message, group.items.size),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val files = group.items.map { it.file }
                    groupPendingDelete = null
                    galleryItemsSource = null
                    hiddenPaths = hiddenPaths + files.map { it.absolutePath }
                    onDeleteImages(files)
                }) {
                    Text(stringResource(R.string.action_delete), color = KitsugiColors.AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupPendingDelete = null }) {
                    Text(stringResource(R.string.vazgec), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ImageGroupHeader(
    group: DownloadedImageGroup,
    accentColor: Color,
    onDeleteGroup: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = group.displayName,
            color = KitsugiColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(KitsugiColors.SurfaceStrong)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = stringResource(R.string.downloads_image_items_count, group.items.size),
                color = KitsugiColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        KitsugiAnimatedDeleteButton(
            onDelete = onDeleteGroup,
            size = 26.dp,
            expandedWidth = 78.dp,
            contentDescription = stringResource(R.string.bu_icerigin_tum_indirilen_resimlerini_sil)
        )
    }
}

/**
 * Tek resim kutucuğu.
 *
 * Kart genişliği ızgara hücresine **otomatik** oturur (sabit genişlik yok);
 * küçük resim alanı 4:3 oranını korur, böylece sütun sayısı/ekran boyutu ne olursa
 * olsun kutucuklar bozulmaz.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadedImageCard(
    item: DownloadedImageItem,
    accentColor: Color,
    onClick: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val dateStr = remember(item.lastModified) {
        if (item.lastModified > 0) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(item.lastModified))
        } else ""
    }
    val sizeStr = formatBytes(item.fileSizeBytes)
    val dimensionStr = remember(item.width, item.height) {
        if (item.width > 0 && item.height > 0) "${item.width}×${item.height}" else ""
    }

    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(KitsugiColors.SurfaceSoft)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accentColor else KitsugiColors.Border.copy(alpha = 0.4f),
                shape = shape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick ?: onClick
            )
    ) {
        Column {
            // Küçük resim: hücre genişliğine göre ölçeklenir (sabit yükseklik yok).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            ) {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                    contentScale = ContentScale.Crop
                )
                // Alt kısımda yumuşak karartma (bilgi alanına geçiş)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, KitsugiColors.SurfaceSoft)
                            )
                        )
                )
                // Boyut rozeti
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.82f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sizeStr,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Çözünürlük rozeti (varsa) — solda, boyut rozetiyle çakışmaz
                if (dimensionStr.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.82f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dimensionStr,
                            color = KitsugiColors.TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // Seçim işareti
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) accentColor else KitsugiColors.SurfaceStrong.copy(alpha = 0.7f)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (selected) accentColor else KitsugiColors.TextMuted,
                                shape = RoundedCornerShape(999.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = KitsugiColors.OnAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Bilgi alanı
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = item.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                if (dateStr.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateStr,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp
                    )
                }
                if (!selectionMode) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Klasörü aç
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .clickable(onClick = onOpenFolder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = stringResource(R.string.klasoru_ac),
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        // Sil
                        KitsugiAnimatedDeleteButton(
                            onDelete = onDelete,
                            size = 30.dp
                        )
                    }
                }
            }
        }
    }
}
