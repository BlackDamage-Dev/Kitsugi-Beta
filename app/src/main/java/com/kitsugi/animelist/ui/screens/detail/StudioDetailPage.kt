package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.ui.components.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import androidx.compose.runtime.CompositionLocalProvider
import com.kitsugi.animelist.ui.components.LocalKitsugiGalleryLoading
import com.kitsugi.animelist.ui.components.LocalKitsugiTranslationLoading
import com.kitsugi.animelist.ui.components.KitsugiGalleryIconButton
import com.kitsugi.animelist.ui.components.KitsugiStickyHeaderReveal

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiPageEnter
import com.kitsugi.animelist.ui.components.KitsugiCinematicLoadingScreen
import com.kitsugi.animelist.ui.components.KitsugiRankingMediaCard
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.parseToMediaType
import com.kitsugi.animelist.utils.toLocalizedOrgName
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

sealed interface StudioDetailState {
    object Loading : StudioDetailState
    data class Error(
        val message: String,
        val platformErrorCode: com.kitsugi.animelist.data.platform.PlatformErrorCode? = null,
    ) : StudioDetailState
    data class Success(val detail: com.kitsugi.animelist.data.remote.KitsugiStudioDetail) : StudioDetailState
}

@Composable
fun StudioDetailPage(
    studioId: Int,
    source: String,
    onBackClick: () -> Unit,
    onMediaClick: (mediaId: Int, mediaType: String, mediaSource: String) -> Unit,
    name: String? = null,
    imageUrl: String? = null,
    titleLanguage: String = "ROMAJI",
    preferredTranslator: String = "DEFAULT"
) {
    val accentColor = LocalKitsugiAccent.current

    // Obtain ViewModel
    val viewModelKey = remember(source, studioId, name) {
        "studio_${source}_${studioId}_${name.orEmpty().trim().lowercase(java.util.Locale.ROOT)}"
    }
    val viewModel: StudioDetailViewModel = viewModel(key = viewModelKey)
    val galleryLoading by viewModel.galleryLoading.collectAsState()
    val translationLoading by viewModel.translationLoading.collectAsState()
    // Otomatik çeviri sürerken sayfadaki çeviri butonları galeri butonuyla aynı animasyonu gösterir.
    CompositionLocalProvider(
        LocalKitsugiGalleryLoading provides galleryLoading,
        LocalKitsugiTranslationLoading provides translationLoading
    ) {

    // Load studio in ViewModel
    LaunchedEffect(studioId, source, name, imageUrl) {
        viewModel.loadStudio(studioId, source, name, imageUrl)
    }

    // Collect state from ViewModel
    val state by viewModel.state.collectAsState()
    val isFavourite by viewModel.isFavourite.collectAsState()
    val isAniListSource = source.lowercase() == "anilist"
    val context = LocalContext.current
    val isAniListConnected = remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(context) != null }
    val showFavouriteButton = isAniListSource || isAniListConnected

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        when (val currentState = state) {
            is StudioDetailState.Loading -> {
                KitsugiCinematicLoadingScreen(
                    title = name.orEmpty(),
                    imageUrl = imageUrl,
                    source = source,
                    isLogoArtwork = source.trim().equals("tmdb", ignoreCase = true) ||
                        source.trim().equals("themoviedb", ignoreCase = true) ||
                        source.trim().equals("tmdb_network", ignoreCase = true) ||
                        source.trim().equals("tmdb-network", ignoreCase = true) ||
                        source.trim().equals("tmdbnetwork", ignoreCase = true),
                    onBackClick = onBackClick
                )
            }
            is StudioDetailState.Error -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentState.message,
                            color = KitsugiColors.AccentRed,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )

                        currentState.platformErrorCode?.let { code ->
                            Spacer(modifier = Modifier.height(12.dp))
                            PlatformErrorExplanationBlock(
                                code = code,
                                tint = KitsugiColors.AccentRed,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                viewModel.retry()
                            }
                        ) {
                            Text(stringResource(R.string.yeniden_dene), color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is StudioDetailState.Success -> {
                val detail = currentState.detail
                val galleryItems by viewModel.galleryItems.collectAsState()
                val translatedAbout by viewModel.translatedAbout.collectAsState()
                val showAboutOriginal by viewModel.showAboutOriginal.collectAsState()
                val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                val loadMoreFailed by viewModel.loadMoreFailed.collectAsState()
                var activeGalleryItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
                var activeGalleryIndex by remember { mutableStateOf(0) }

                StudioDetailSuccessContent(
                    detail = detail,
                    source = source,
                    accentColor = accentColor,
                    isFavourite = isFavourite,
                    showFavouriteButton = showFavouriteButton,
                    galleryItems = galleryItems,
                    titleLanguage = titleLanguage,
                    translatedAbout = translatedAbout,
                    preferredTranslator = preferredTranslator,
                    aboutHasTranslation = translatedAbout != null && translatedAbout != detail.about,
                    showAboutOriginal = showAboutOriginal,
                    onToggleAboutOriginal = { viewModel.toggleAboutOriginal() },
                    onTranslateAbout = { viewModel.translateAbout() },
                    onBackClick = onBackClick,
                    onToggleFavourite = { viewModel.toggleFavourite() },
                    isLoadingMore = isLoadingMore,
                    loadMoreFailed = loadMoreFailed,
                    onLoadMore = { viewModel.loadMoreWorks() },
                    onMediaClick = onMediaClick,
                    onGalleryClick = { items, idx ->
                        if (!galleryLoading) activeGalleryItems = items
                        activeGalleryIndex = idx
                    }
                )

                if (activeGalleryItems.isNotEmpty()) {
                    KitsugiImageGalleryDialog(
                        galleryItems = activeGalleryItems,
                        initialIndex = activeGalleryIndex,
                        title = detail.name.toLocalizedOrgName(),
                        onDismiss = { activeGalleryItems = emptyList() }
                    )
                }
            }
        }
    }
    }
}

/**
 * Stüdyo / yapımcı detay içeriği — keşfet "Tümünü Gör" (FullScreenMediaGridPage)
 * sayfalarıyla BİREBİR aynı davranış: ekran boyutuna ve dikey/yatay moda göre
 * uyarlanan kolon sayısı, grid/liste görünümleri, emojili tür çipleri, filtre
 * bottom sheet'i, kaydırınca beliren üst şerit (floating header) ve aşağı
 * kaydırınca çıkan yukarı kaydırma FAB'ı.
 */
@Composable
private fun StudioDetailSuccessContent(
    detail: com.kitsugi.animelist.data.remote.KitsugiStudioDetail,
    source: String,
    accentColor: Color,
    isFavourite: Boolean,
    showFavouriteButton: Boolean,
    galleryItems: List<GalleryItem>,
    titleLanguage: String,
    translatedAbout: String? = null,
    preferredTranslator: String = "DEFAULT",
    aboutHasTranslation: Boolean = false,
    showAboutOriginal: Boolean = false,
    onToggleAboutOriginal: () -> Unit = {},
    onTranslateAbout: () -> Unit = {},
    onBackClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    isLoadingMore: Boolean = false,
    loadMoreFailed: Boolean = false,
    onLoadMore: () -> Unit = {},
    onMediaClick: (mediaId: Int, mediaType: String, mediaSource: String) -> Unit,
    onGalleryClick: (List<GalleryItem>, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTv = LocalIsTv.current

    // "Hakkında" kartı çeviri ikonu: her yerde olduğu gibi doğrudan harici çeviri
    // uygulamasını açar (ham, orijinal metinle).
    val onAboutTranslateClick: (String) -> Unit = { _ ->
        val about = detail.about
        if (!about.isNullOrBlank()) context.openTranslator(about, preferredTranslator)
    }
    val onAboutCopyClick: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("studio_about", text))
        android.widget.Toast.makeText(context, context.getString(R.string.toast_copied_clipboard), android.widget.Toast.LENGTH_SHORT).show()
    }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── Keşfet sayfalarıyla aynı duyarlı kolon hesabı ─────────────────────────
    val columnCount = rememberKitsugiGridColumns()

    // Grid/Liste görünümü tercihi kalıcı olarak hatırlanır
    val gridPrefs = remember(context) { context.getSharedPreferences("kitsugi_ui_prefs", android.content.Context.MODE_PRIVATE) }
    var isGridView by remember { mutableStateOf(gridPrefs.getBoolean("studio_detail_is_grid_view", true)) }
    LaunchedEffect(isGridView) {
        gridPrefs.edit().putBoolean("studio_detail_is_grid_view", isGridView).apply()
    }

    // ── Filtre / sıralama durumu ──────────────────────────────────────────────
    var selectedTypeId by rememberSaveable { mutableStateOf("ALL") }
    var selectedSortOption by rememberSaveable { mutableStateOf(StudioSortOption.DEFAULT) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val hasActiveFilter = selectedTypeId != "ALL" || selectedSortOption != StudioSortOption.DEFAULT

    val displayedWorks = remember(detail, selectedTypeId, selectedSortOption) {
        val filtered = detail.mediaWorks.filter {
            studioTypeMatches(selectedTypeId, it.mediaType.parseToMediaType())
        }
        when (selectedSortOption) {
            StudioSortOption.DEFAULT -> filtered
            StudioSortOption.TITLE_ASC -> filtered.sortedBy { it.mediaTitle.lowercase() }
            StudioSortOption.TITLE_DESC -> filtered.sortedByDescending { it.mediaTitle.lowercase() }
        }
    }

    // ── Scroll state'leri + üst şerit / yukarı FAB tetikleyicileri ────────────
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // ── Sonsuz kaydırma: liste sonuna yaklaşınca bir sonraki yapım sayfası istenir ──
    val nearListEnd by remember(isGridView) {
        derivedStateOf {
            if (isGridView) {
                val info = gridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= 0 && last >= info.totalItemsCount - 8
            } else {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= 0 && last >= info.totalItemsCount - 8
            }
        }
    }
    LaunchedEffect(nearListEnd, detail.hasMoreWorks, loadMoreFailed, isLoadingMore) {
        if (nearListEnd && detail.hasMoreWorks && !loadMoreFailed && !isLoadingMore) onLoadMore()
    }

    // Only replace the original controls after they have actually left the viewport.
    // Index 1 is the about card on many studios, so an index-only check showed
    // the floating filter/view buttons while the originals were still on screen.
    val controlsIndex = if (detail.about.isNullOrBlank()) 1 else 2
    val showFloatingHeader by remember(isGridView, controlsIndex) {
        derivedStateOf {
            if (isGridView) {
                val visible = gridState.layoutInfo.visibleItemsInfo
                val control = visible.firstOrNull { it.key == "studio_controls" }
                if (control != null) {
                    control.offset.y + control.size.height <= 0
                } else {
                    (visible.firstOrNull()?.index ?: 0) > controlsIndex
                }
            } else {
                val visible = listState.layoutInfo.visibleItemsInfo
                val control = visible.firstOrNull { it.key == "studio_controls_list" }
                if (control != null) {
                    control.offset + control.size <= 0
                } else {
                    (visible.firstOrNull()?.index ?: 0) > controlsIndex
                }
            }
        }
    }

    val showScrollToTop by remember(isGridView) {
        derivedStateOf {
            if (isGridView) gridState.firstVisibleItemIndex > 3
            else listState.firstVisibleItemIndex > 3
        }
    }

    val onOpenGallery: (() -> Unit)? = if (galleryItems.isNotEmpty()) {
        { onGalleryClick(galleryItems, 0) }
    } else null

    // Kontroller başlığı (başlık + filtre + görünüm toggle + çipler + sayaç)
    val controlsHeader: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.inline_titles_1_s_1351b33, detail.mediaWorks.size),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                // Filtre ve Sıralama butonu
                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hasActiveFilter) accentColor.copy(alpha = 0.25f) else KitsugiColors.Surface)
                ) {
                    Icon(
                        Icons.FilterList,
                        contentDescription = stringResource(R.string.filtre_ve_siralama),
                        tint = if (hasActiveFilter) accentColor else KitsugiColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Grid ↔ Liste toggle
                IconButton(
                    onClick = {
                        scope.launch {
                            if (isGridView) {
                                val gridIndex = gridState.firstVisibleItemIndex
                                val gridOffset = gridState.firstVisibleItemScrollOffset
                                val listIndex = if (gridIndex == 0) 0 else gridIndex + 1
                                isGridView = false
                                kotlinx.coroutines.delay(10)
                                listState.scrollToItem(listIndex, gridOffset)
                            } else {
                                val listIndex = listState.firstVisibleItemIndex
                                val listOffset = listState.firstVisibleItemScrollOffset
                                val gridIndex = if (listIndex <= 1) 0 else listIndex - 1
                                isGridView = true
                                kotlinx.coroutines.delay(10)
                                gridState.scrollToItem(gridIndex, listOffset)
                            }
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Surface)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.ListAlt else Icons.GridView,
                        contentDescription = if (isGridView) stringResource(R.string.settings_list_layout) else stringResource(R.string.cd_grid_view),
                        tint = accentColor
                    )
                }
            }

            // Emojili hızlı tür çipleri (Nested LazyRow ana ızgarayı yeniden ölçüyordu)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 12.dp, bottom = 4.dp)
            ) {
                STUDIO_TYPE_FILTERS.forEach { typeFilter ->
                    val isSelected = selectedTypeId == typeFilter.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.22f)
                                else KitsugiColors.Surface
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) accentColor else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedTypeId = if (isSelected && typeFilter.id != "ALL") "ALL" else typeFilter.id
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = typeFilter.displayLabel(),
                            color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Sayaç + aktif filtre açıklaması + Temizle
            val activeType = STUDIO_TYPE_FILTERS.firstOrNull { it.id == selectedTypeId }
            val activeTypeLabel = activeType?.takeIf { it.id != "ALL" }?.displayLabel()
            val activeSortLabel = if (selectedSortOption != StudioSortOption.DEFAULT) selectedSortOption.displayLabel() else null
            val activeFilterDesc = buildString {
                activeTypeLabel?.let { append(" • $it") }
                activeSortLabel?.let { append(" • $it") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.inline_1_s_items_2_s_8700704, displayedWorks.size, activeFilterDesc),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasActiveFilter) {
                    Text(
                        text = stringResource(R.string.temizle),
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            selectedTypeId = "ALL"
                            selectedSortOption = StudioSortOption.DEFAULT
                        }
                    )
                }
            }
        }
    }

    KitsugiPageEnter {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isGridView) {
                // ── GRID MODU ─────────────────────────────────────────────────
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    state = gridState,
                    modifier = if (isTv) Modifier.width(960.dp) else Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "studio_hero", span = { GridItemSpan(maxLineSpan) }) {
                        StudioHeroHeader(
                            detail = detail,
                            source = source,
                            accentColor = accentColor,
                            onBackClick = onBackClick,
                            isFavourite = isFavourite,
                            showFavouriteButton = showFavouriteButton,
                            onToggleFavourite = onToggleFavourite,
                            onGalleryClick = onOpenGallery,
                            height = if (isLandscape) 260.dp else 340.dp
                        )
                    }

                    if (!detail.about.isNullOrBlank()) {
                        item(key = "studio_about", span = { GridItemSpan(maxLineSpan) }) {
                            StudioAboutSection(
                                about = detail.about,
                                onGalleryClick = onGalleryClick,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                translatedAbout = translatedAbout,
                                onTranslateClick = onAboutTranslateClick,
                                onCopyClick = onAboutCopyClick,
                                aboutHasTranslation = aboutHasTranslation,
                                showAboutOriginal = showAboutOriginal,
                                onToggleAboutOriginal = onToggleAboutOriginal
                            )
                        }
                    }

                    item(key = "studio_controls", span = { GridItemSpan(maxLineSpan) }) {
                        controlsHeader()
                    }

                    if (displayedWorks.isEmpty()) {
                        item(key = "studio_empty", span = { GridItemSpan(maxLineSpan) }) {
                            KitsugiEmptyState(
                                title = stringResource(R.string.studio_page_empty_title),
                                subtitle = stringResource(R.string.bu_studyo_icin_gosterilecek_yapim_bulunamadi),
                                icon = Icons.SearchOff
                            )
                        }
                    } else {
                        gridItems(
                            displayedWorks,
                            key = { work -> "${work.source}_${work.mediaId}_g" }
                        ) { work ->
                            KitsugiExploreMediaCard(
                                result = work.toSearchResult(),
                                onClick = { onMediaClick(work.mediaId, work.mediaType, work.source) },
                                titleLanguage = titleLanguage,
                                forceVertical = !isLandscape
                            )
                        }
                    }
                }
            } else {
                // ── LİSTE MODU ────────────────────────────────────────────────
                LazyColumn(
                    state = listState,
                    modifier = if (isTv) Modifier.width(960.dp) else Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "studio_hero_list") {
                        StudioHeroHeader(
                            detail = detail,
                            source = source,
                            accentColor = accentColor,
                            onBackClick = onBackClick,
                            isFavourite = isFavourite,
                            showFavouriteButton = showFavouriteButton,
                            onToggleFavourite = onToggleFavourite,
                            onGalleryClick = onOpenGallery,
                            height = if (isLandscape) 260.dp else 340.dp
                        )
                    }

                    if (!detail.about.isNullOrBlank()) {
                        item(key = "studio_about_list") {
                            StudioAboutSection(
                                about = detail.about,
                                onGalleryClick = onGalleryClick,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                translatedAbout = translatedAbout,
                                onTranslateClick = onAboutTranslateClick,
                                onCopyClick = onAboutCopyClick,
                                aboutHasTranslation = aboutHasTranslation,
                                showAboutOriginal = showAboutOriginal,
                                onToggleAboutOriginal = onToggleAboutOriginal
                            )
                        }
                    }

                    item(key = "studio_controls_list") {
                        controlsHeader()
                    }

                    if (displayedWorks.isEmpty()) {
                        item(key = "studio_empty_list") {
                            KitsugiEmptyState(
                                title = stringResource(R.string.studio_page_empty_title),
                                subtitle = stringResource(R.string.bu_studyo_icin_gosterilecek_yapim_bulunamadi),
                                icon = Icons.SearchOff
                            )
                        }
                    } else {
                        itemsIndexed(
                            displayedWorks,
                            key = { _, work -> "${work.source}_${work.mediaId}_list" }
                        ) { index, work ->
                            KitsugiRankingMediaCard(
                                result = work.toSearchResult(),
                                rankIndex = index + 1,
                                onClick = { onMediaClick(work.mediaId, work.mediaType, work.source) },
                                titleLanguage = titleLanguage
                            )
                        }
                    }
                }
            }

            // ── Sonraki sayfa durumu (sonsuz kaydırma) ───────────────────────
            StudioLoadMoreIndicator(
                isLoading = isLoadingMore,
                failed = loadMoreFailed,
                accentColor = accentColor,
                onRetry = onLoadMore,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )

            // ── Kaydırınca beliren üst şerit (floating header) ────────────────
            KitsugiStickyHeaderReveal(
                visible = showFloatingHeader,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = (if (isTv) Modifier.width(960.dp) else Modifier.fillMaxWidth())
                        .height(64.dp)
                        .background(KitsugiColors.Surface.copy(alpha = 0.92f))
                        .padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = detail.name.toLocalizedOrgName(),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onOpenGallery != null) {
                        KitsugiGalleryIconButton(onClick = onOpenGallery, accentColor = accentColor)
                    }
                    IconButton(onClick = {
                        val url = com.kitsugi.animelist.utils.ShareUtils.buildStudioUrl(source, detail.id, detail.name.toLocalizedOrgName())
                        com.kitsugi.animelist.utils.ShareUtils.shareText(context, detail.name.toLocalizedOrgName(), url)
                    }) {
                        Icon(Icons.Share, contentDescription = stringResource(R.string.action_share), tint = KitsugiColors.TextSecondary)
                    }
                    if (showFavouriteButton) {
                        IconButton(onClick = onToggleFavourite) {
                            Icon(
                                imageVector = if (isFavourite) Icons.Favorite else Icons.FavoriteBorder,
                                contentDescription = if (isFavourite) stringResource(R.string.action_favourite_remove) else "Favori Yap",
                                tint = if (isFavourite) accentColor else KitsugiColors.TextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (hasActiveFilter) accentColor.copy(alpha = 0.25f) else Color.Transparent)
                    ) {
                        Icon(
                            Icons.FilterList,
                            contentDescription = stringResource(R.string.filtre_ve_siralama),
                            tint = if (hasActiveFilter) accentColor else KitsugiColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = {
                        scope.launch {
                            if (isGridView) {
                                val gridIndex = gridState.firstVisibleItemIndex
                                val gridOffset = gridState.firstVisibleItemScrollOffset
                                val listIndex = if (gridIndex == 0) 0 else gridIndex + 1
                                isGridView = false
                                kotlinx.coroutines.delay(10)
                                listState.scrollToItem(listIndex, gridOffset)
                            } else {
                                val listIndex = listState.firstVisibleItemIndex
                                val listOffset = listState.firstVisibleItemScrollOffset
                                val gridIndex = if (listIndex <= 1) 0 else listIndex - 1
                                isGridView = true
                                kotlinx.coroutines.delay(10)
                                gridState.scrollToItem(gridIndex, listOffset)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isGridView) Icons.ListAlt else Icons.GridView,
                            contentDescription = if (isGridView) stringResource(R.string.cd_list_view) else stringResource(R.string.cd_grid_view),
                            tint = accentColor
                        )
                    }
                }
            }

            // ── Aşağı kaydırınca çıkan yukarı kaydırma FAB'ı ──────────────────
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(16.dp)) {
                            scope.launch {
                                if (isGridView) gridState.animateScrollToItem(0)
                                else listState.animateScrollToItem(0)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.cd_scroll_top),
                        tint = KitsugiColors.Background,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        KitsugiStudioFilterBottomSheet(
            initialTypeId = selectedTypeId,
            initialSortOption = selectedSortOption,
            onDismissRequest = { showFilterSheet = false },
            onApply = { typeId, sortOption ->
                selectedTypeId = typeId
                selectedSortOption = sortOption
            },
            onReset = {
                selectedTypeId = "ALL"
                selectedSortOption = StudioSortOption.DEFAULT
            }
        )
    }
}

/** Studio loading follows the same hero / about / controls / works geometry as the loaded page. */
@Composable
private fun StudioDetailLoadingSkeleton(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val isGridView = remember(context) {
        context.getSharedPreferences("kitsugi_ui_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("studio_detail_is_grid_view", true)
    }
    val heroHeight = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 260.dp else 340.dp
    KitsugiShimmerProvider {
        val brush = LocalShimmerBrush.current ?: return@KitsugiShimmerProvider
        val hero: @Composable () -> Unit = {
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                KitsugiShimmerBlock(brush, Modifier.fillMaxSize(), 22.dp)
                IconButton(onClick = onBackClick, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Icon(Icons.ArrowBack, contentDescription = stringResource(R.string.geri), tint = KitsugiColors.TextPrimary)
                }
                KitsugiShimmerBlock(brush, Modifier.align(Alignment.BottomStart).padding(20.dp).width(190.dp).height(28.dp))
            }
        }
        val about: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(KitsugiColors.Surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KitsugiShimmerBlock(brush, Modifier.width(105.dp).height(22.dp))
                repeat(3) { line ->
                    KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(if (line == 2) 0.6f else 1f).height(14.dp))
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitsugiShimmerBlock(brush, Modifier.width(170.dp).height(28.dp))
                    Spacer(Modifier.weight(1f))
                    repeat(2) { KitsugiShimmerBlock(brush, Modifier.padding(start = 6.dp).size(48.dp)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { KitsugiShimmerBlock(brush, Modifier.width(70.dp).height(36.dp), 20.dp) }
                }
                KitsugiShimmerBlock(brush, Modifier.width(75.dp).height(14.dp))
            }
        }
        val card: @Composable (Boolean) -> Unit = { wide ->
            if (wide) {
                Row(Modifier.fillMaxWidth().height(144.dp).clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    KitsugiShimmerBlock(brush, Modifier.size(width = 86.dp, height = 120.dp), 16.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.85f).height(16.dp))
                        KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.65f).height(12.dp))
                        KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.75f).height(12.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(KitsugiColors.Surface).padding(14.dp)) {
                    KitsugiShimmerBlock(brush, Modifier.fillMaxWidth().aspectRatio(2f / 3f), 20.dp)
                    Spacer(Modifier.height(12.dp))
                    KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.85f).height(16.dp))
                    Spacer(Modifier.height(8.dp))
                    KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.6f).height(12.dp))
                    Spacer(Modifier.height(8.dp))
                    KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.75f).height(12.dp))
                }
            }
        }
        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(rememberKitsugiGridColumns()),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { hero() }
                item(span = { GridItemSpan(maxLineSpan) }) { about() }
                item(span = { GridItemSpan(maxLineSpan) }) { controls() }
                items(count = 8) { card(heroHeight == 260.dp) }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { hero() }
                item { about() }
                item { controls() }
                items(6) { card(true) }
            }
        }
    }
}

/** Alt ortada: sonraki sayfa yüklenirken küçük gösterge; hata olursa "tekrar dene" düğmesi. */
@Composable
private fun StudioLoadMoreIndicator(
    isLoading: Boolean,
    failed: Boolean,
    accentColor: Color,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> Box(
            modifier = modifier
                .background(KitsugiColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            CircularProgressIndicator(
                color = accentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        }
        failed -> TextButton(onClick = onRetry, modifier = modifier) {
            Text(
                text = stringResource(R.string.studio_page_load_more_failed),
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
