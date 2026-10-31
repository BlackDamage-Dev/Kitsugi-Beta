package com.kitsugi.animelist.ui.tv.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.ui.screens.manga.MangaBrowseViewModel
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.utils.dpadRepeatThrottle
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.rememberKitsugiShimmerBrush
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * WP-13 — TV-optimize edilmiş Manga Browse ekranı.
 *
 * Mobilden farkları:
 * - KitsugiTvTokens spacing/overscan alanı
 * - D-pad odak yönetimi: kaynak chip row + içerik grid ayrı focus group'lar
 * - `focusRestorer` ile grid'e geri dönüşte son odaklanılan kart geri gelir
 * - Arama alanı yalnızca klavye odağına geçince expand olur
 * - Backdrop blur yerine hafif alpha overlay (TV GPU bütçesi için)
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvMangaBrowseScreen(
    repository: MangaSourceRepository,
    vm: MangaBrowseViewModel = viewModel(factory = MangaBrowseViewModel.Factory(repository)),
    onMangaClick: (MangaSource, MangaDetails) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    val ui by vm.ui.collectAsState()
    val gridState = rememberLazyGridState()

    // Focus requesters
    val searchFocusRequester = remember { FocusRequester() }
    val chipRowFocusRequester = remember { FocusRequester() }
    val gridFocusRequester    = remember { FocusRequester() }

    var searchFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { onBack() }

    // Birleştirilmiş manga listesi (mobil mantığıyla aynı)
    val query     = ui.searchQuery
    val states    = ui.sourceStates
    val selFilter = ui.selectedSourceFilter
    val popular   = ui.popularMangas

    val mergedMangas = remember(states, selFilter, query, popular) {
        val raw = if (query.isNotBlank()) {
            val src = selFilter
            if (src != null) {
                (states.firstOrNull { it.source.name == src.name }?.mangas ?: emptyList())
                    .sortedByDescending {
                        com.kitsugi.animelist.data.manga.MangaTitleMatcher.getSimilarityScore(query, it.title)
                    }
            } else {
                val minScore = if (query.trim().length <= 3) 0.15 else 0.30
                states.flatMap { st ->
                    st.mangas.map { manga ->
                        val score = com.kitsugi.animelist.data.manga.MangaTitleMatcher.getSimilarityScore(query, manga.title)
                        Triple(manga, score, st.source)
                    }
                }
                .filter { (_, score, _) -> score >= minScore }
                .sortedByDescending { it.second }
                .map { it.first }
            }
        } else popular
        raw.distinctBy { "${it.source}_${it.url}" }
    }

    // +18 UYUMU (2026-10-14): TV uzantı gözatma ekranı da göster/gizle sözleşmesine uyar.
    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    val visibleMangas = remember(mergedMangas, showAdultContent) {
        if (showAdultContent) mergedMangas else mergedMangas.filter { !it.isAdultContent }
    }

    val backdropUrl = visibleMangas.firstOrNull()?.thumbnailUrl

    Box(Modifier.fillMaxSize().background(KitsugiColors.Background)) {

        // ── Hafif backdrop (TV: alpha overlay, blur yok) ──────────────────────
        if (!backdropUrl.isNullOrBlank()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = backdropUrl,
                contentDescription = null,
                isAdult = visibleMangas.firstOrNull()?.isAdultContent ?: false,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.12f }
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(KitsugiColors.Background.copy(0.7f), KitsugiColors.Background)
                )
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start  = KitsugiTvTokens.Spacing.screenHorizontal,
                    end    = KitsugiTvTokens.Spacing.screenHorizontal,
                    top    = KitsugiTvTokens.Spacing.screenVertical,
                    bottom = KitsugiTvTokens.Spacing.screenVertical
                )
        ) {

            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(KitsugiColors.SurfaceStrong.copy(0.5f))
                        .tvClickable(shape = RoundedCornerShape(8.dp)) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.ArrowBack,
                        contentDescription = stringResource(R.string.geri),
                        tint = KitsugiColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.inline_discover_manga_eb24f62),
                    style = MaterialTheme.typography.titleLarge,
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.TravelExplore,
                    contentDescription = null,
                    tint = KitsugiColors.AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Arama alanı ──────────────────────────────────────────────────
            val searchBorderAlpha by animateFloatAsState(
                targetValue = if (searchFocused) 1f else 0.4f,
                animationSpec = tween(200),
                label = "searchBorderAlpha"
            )
            OutlinedTextField(
                value = query,
                onValueChange = { vm.search(it) },
                placeholder = { Text(stringResource(R.string.manga_ara), color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodyMedium) },
                leadingIcon  = { Icon(Icons.Search, null, tint = KitsugiColors.TextMuted) },
                singleLine   = true,
                shape        = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor    = KitsugiColors.AccentBlue.copy(alpha = searchBorderAlpha),
                    unfocusedBorderColor  = KitsugiColors.Border.copy(alpha = searchBorderAlpha),
                    cursorColor           = KitsugiColors.AccentBlue,
                    focusedTextColor      = KitsugiColors.TextPrimary,
                    unfocusedTextColor    = KitsugiColors.TextPrimary,
                    focusedContainerColor    = KitsugiColors.Surface.copy(0.6f),
                    unfocusedContainerColor  = KitsugiColors.Surface.copy(0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .onFocusChanged { searchFocused = it.isFocused }
            )

            Spacer(Modifier.height(12.dp))

            // ── Kaynak chip satırı ────────────────────────────────────────────
            if (ui.sources.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(chipRowFocusRequester)
                        .focusGroup()
                        .focusRestorer()
                        .dpadRepeatThrottle(horizontalGateMs = 100L, verticalGateMs = Long.MAX_VALUE),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (query.isNotBlank()) {
                        val totalCount = states.sumOf { it.mangas.size }
                        val anyLoading = states.any { it.isLoading }
                        item {
                            TvMangaSourceChip(
                                label     = stringResource(R.string.tumu),
                                count     = totalCount,
                                isLoading = anyLoading,
                                isSelected = selFilter == null,
                                onClick = { vm.selectSourceFilter(null) }
                            )
                        }
                        items(states, key = { it.source.name }) { st ->
                            TvMangaSourceChip(
                                label      = st.source.name,
                                count      = st.mangas.size,
                                isLoading  = st.isLoading,
                                isSelected = selFilter?.name == st.source.name,
                                onClick    = {
                                    vm.selectSourceFilter(
                                        if (selFilter?.name == st.source.name) null else st.source
                                    )
                                }
                            )
                        }
                    } else {
                        items(ui.sources) { src ->
                            TvMangaSourceChip(
                                label      = "${src.name}  [${src.lang.uppercase()}]",
                                count      = if (selFilter?.name == src.name) popular.size else 0,
                                isLoading  = selFilter?.name == src.name && ui.isLoadingPopular,
                                isSelected = selFilter?.name == src.name,
                                onClick    = { vm.selectSourceFilter(src) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Manga grid ────────────────────────────────────────────────────
            val isLoading = if (query.isNotBlank()) states.any { it.isLoading } else ui.isLoadingPopular

            Box(Modifier.weight(1f)) {
                when {
                    isLoading && mergedMangas.isEmpty() -> {
                        // Skeleton grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            state = gridState,
                            modifier = Modifier.fillMaxSize()
                                .focusRequester(gridFocusRequester)
                                .focusGroup()
                                .focusRestorer(),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement   = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(10) {
                                TvMangaCardSkeleton()
                            }
                        }
                    }
                    !isLoading && visibleMangas.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Extension,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextMuted,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.inline_no_manga_found_1c8e980),
                                    color = KitsugiColors.TextSecondary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    stringResource(R.string.ayarlardan_manga_eklentisi_ekle),
                                    color = KitsugiColors.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            state = gridState,
                            modifier = Modifier.fillMaxSize()
                                .focusRequester(gridFocusRequester)
                                .focusGroup()
                                .focusRestorer(),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement   = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(visibleMangas, key = { _, m -> "${m.source}_${m.url}" }) { _, manga ->
                                TvMangaCard(
                                    manga = manga,
                                    onClick = {
                                        val src = states.firstOrNull { it.source.name == manga.source }?.source
                                            ?: ui.sources.firstOrNull { it.name == manga.source }
                                            ?: selFilter
                                            ?: states.firstOrNull()?.source
                                            ?: ui.sources.firstOrNull()
                                        if (src != null) onMangaClick(src, manga)
                                    }
                                )
                            }

                            // Sonraki sayfa yükleme trigger
                            if (ui.hasNextPage && !isLoading) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(5) }) {
                                    LaunchedEffect(mergedMangas.size) { vm.loadNextPage() }
                                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
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

// ── TV Manga kart bileşeni ────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMangaCard(
    manga: MangaDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .height(220.dp)
            .fillMaxWidth()
            .clip(cardShape)
            .tvClickable(
                shape       = cardShape,
                scaleFocused = 1.06f,
                borderWidth = 2.dp,
                onClick     = onClick
            )
            .background(KitsugiColors.Surface)
    ) {
        com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
            model = manga.thumbnailUrl,
            contentDescription = manga.title,
            isAdult = manga.isAdultContent,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(0.92f))
                    )
                )
                .padding(8.dp)
        ) {
            Column {
                Text(
                    manga.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!manga.source.isNullOrBlank()) {
                    Text(
                        manga.source,
                        color = KitsugiColors.AccentBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Manga ikonu — poster yoksa merkeze
        if (manga.thumbnailUrl.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Book,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

// ── TV Manga skeleton kart ────────────────────────────────────────────────────

@Composable
private fun TvMangaCardSkeleton(modifier: Modifier = Modifier) {
    // PERF: sabit fırça + tek animasyon saati üzerinden çizim; her karede yeni
    // Brush/shader üretilmez ve `background(brush)` ile gelen statik görünüm oluşmaz.
    val brush = rememberKitsugiShimmerBrush()
    KitsugiShimmerBlock(
        brush = brush,
        modifier = modifier
            .height(220.dp)
            .fillMaxWidth(),
        cornerRadius = 10.dp
    )
}

// ── TV Kaynak chip ────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMangaSourceChip(
    label: String,
    count: Int,
    isLoading: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(999.dp)
    val bg = when {
        isSelected -> KitsugiColors.AccentBlue.copy(0.35f)
        count > 0  -> KitsugiColors.AccentBlue.copy(0.12f)
        else       -> Color.White.copy(0.08f)
    }

    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(bg)
            .tvClickable(shape = chipShape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            isLoading -> KitsugiPlasmaLoader(size = 14.dp)
            count > 0 -> Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(KitsugiColors.AccentBlue)
            )
            else -> Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(KitsugiColors.TextMuted.copy(0.5f))
            )
        }
        Text(
            text = if (count > 0) "$label  $count" else label,
            color = if (isSelected || count > 0) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
