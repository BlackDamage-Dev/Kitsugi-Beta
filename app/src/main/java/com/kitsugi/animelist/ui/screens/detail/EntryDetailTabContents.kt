package com.kitsugi.animelist.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.components.galleryCategoryIcon
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.LocalKitsugiGalleryLoading
import androidx.compose.ui.draw.alpha
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogos
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.kitsugi.animelist.ui.components.GalleryThumbnailImagePolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import com.kitsugi.animelist.ui.utils.localizedLabel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.MdbListRatings
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.data.remote.KitsugiTag
import com.kitsugi.animelist.ui.screens.search.DetailSeasonMetadata
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.remote.toStreamMediaIds
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R


/**
 * Tab 0 — Overview (Bilgi) content for MediaEntryDetailPage.
 */
@Composable
internal fun EntryDetailOverviewTab(
    entry: MediaEntry,
    detail: KitsugiMediaDetail?,
    synopsisState: SynopsisState,
    originalSynopsis: String?,
    externalUrl: String?,
    onSearchQuery: (String) -> Unit,
    onStudioClick: (id: Int, source: String, name: String?, url: String?) -> Unit,
    /** Manga yazarı çipi → kişi (Staff) detay sayfası. */
    onStaffClick: (id: Int, source: String, name: String?, url: String?) -> Unit,
    /** Dergi/serialization çipi → dergiye göre filtrelenmiş arama. */
    onMagazineClick: (id: Int, name: String) -> Unit,
    onGenreClick: (String) -> Unit = {},
    onTagClick: (KitsugiTag) -> Unit = {},
    onSearchBySeason: (DetailSeasonMetadata) -> Unit = {},
    mdbListRatings: MdbListRatings? = null,
    mdbListLoading: Boolean = false,
    mdbListShowImdb: Boolean = true,
    mdbListShowTomatoes: Boolean = true,
    mdbListShowMetacritic: Boolean = true,
    mdbListShowAudience: Boolean = false,
    mdbListShowLetterboxd: Boolean = false,
    mdbListShowTmdb: Boolean = false,
    mdbListShowTrakt: Boolean = false,
    onSettingsClick: (() -> Unit)? = null,
    preferredTranslator: String = "DEFAULT",
    onImageGalleryRequest: ((urls: List<String>, index: Int) -> Unit)? = null,
    galleryItems: List<GalleryItem> = emptyList(),
    galleryLoading: Boolean = false,
    onGalleryItemRequest: ((items: List<GalleryItem>, index: Int) -> Unit)? = null,
    onTranslateClick: (() -> Unit)? = null,
    synopsisHasTranslation: Boolean = false,
    showSynopsisOriginal: Boolean = false,
    onToggleSynopsisOriginal: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(horizontal = 0.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DetailSynopsisCard(
            synopsisState = synopsisState,
            originalText = originalSynopsis,
            // Çeviri ikonu: her yerde olduğu gibi harici çeviri uygulamasını açar.
            onTranslateClick = { textToTranslate ->
                context.openTranslator(textToTranslate, preferredTranslator)
            },
            onCopyClick = { textToCopy ->
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("synopsis", textToCopy))
                android.widget.Toast.makeText(context, context.getString(R.string.toast_copied_clipboard), android.widget.Toast.LENGTH_SHORT).show()
            },
            onImageGalleryRequest = onImageGalleryRequest,
            hasTranslation = synopsisHasTranslation,
            showingOriginal = showSynopsisOriginal,
            onToggleOriginal = onToggleSynopsisOriginal
        )


        // İstatistikler Kartı (Puan Sırası, Oy Sayısı, Üyeler, Popülerlik)
        if (detail != null) {
            DetailOverviewStatsCard(detail = detail)
        }

        // MDBList harici puanlar
        ApiMdbListRatingCard(
            ratings = mdbListRatings,
            isLoading = mdbListLoading,
            showImdb = mdbListShowImdb,
            showTomatoes = mdbListShowTomatoes,
            showMetacritic = mdbListShowMetacritic,
            showAudience = mdbListShowAudience,
            showLetterboxd = mdbListShowLetterboxd,
            showTmdb = mdbListShowTmdb,
            showTrakt = mdbListShowTrakt,
            onSettingsClick = onSettingsClick,
            mediaTitle = entry.title
        )

        // Türler
        if (!detail?.genres.isNullOrEmpty()) {
            EntryGenresChipRow(
                genres = detail!!.genres,
                tags = detail.tags,
                onGenreClick = onGenreClick
            )
        }

        // Stüdyolar + Yapımcılar + Yayıncılar + Dergiler
        if (detail != null &&
            (detail.studios.isNotEmpty() || detail.producers.isNotEmpty() || detail.networks.isNotEmpty() || detail.serializations.isNotEmpty())) {
            // Rol bazlı yönlendirme: yazar çipleri kişi sayfasına, dergi çipleri
            // filtreli aramaya, stüdyo/yapımcı/yayıncı çipleri stüdyo sayfasına gider.
            val studioChipClick: (com.kitsugi.animelist.data.remote.KitsugiStudio) -> Unit = { chip ->
                when (chip.role) {
                    com.kitsugi.animelist.data.remote.StudioRole.AUTHOR ->
                        onStaffClick(chip.id, chip.source, chip.name, chip.imageUrl)
                    com.kitsugi.animelist.data.remote.StudioRole.MAGAZINE -> {
                        val magSource = chip.source.lowercase(java.util.Locale.ROOT).trim()
                        val safeMagId = chip.id.takeIf {
                            it > 0 && (magSource == "jikan" || magSource == "mal" || magSource == "myanimelist")
                        } ?: 0
                        onMagazineClick(safeMagId, chip.name)
                    }
                    else -> {
                        val resolvedSource = resolveStudioClickSource(
                            studioSource = chip.source,
                            mediaSource = entry.source.lowercase(),
                            mediaType = entry.type,
                            role = chip.role
                        )
                        onStudioClick(chip.id, resolvedSource, chip.name, chip.imageUrl)
                    }
                }
            }
            KitsugiStudiosCard(
                studios = detail.studios,
                producers = detail.producers,
                networks = detail.networks + detail.serializations,
                onStudioClick = studioChipClick,
                onProducerClick = studioChipClick,
                onNetworkClick = studioChipClick
            )
        }

        // Etiketler (AniList)
        if (!detail?.tags.isNullOrEmpty()) {
            KitsugiTagsCard(
                tags = detail!!.tags,
                onTagClick = onTagClick
            )
        }

        // Bilgi satırları
        if (detail != null) {
            EntryInfoSection(
                detail = detail,
                mediaType = entry.type,
                fallbackYear = entry.year,
                onSearchQuery = onSearchQuery,
                onSeasonClick = onSearchBySeason
            )
        }

        // Yayıncı + Harici Linkler
        if (detail != null &&
            (detail.streamingLinks.isNotEmpty() || detail.externalLinks.isNotEmpty())) {
            KitsugiLinksCard(
                streamingLinks = detail.streamingLinks,
                externalLinks = detail.externalLinks
            )
        }

        // Tarihler
        DateInfoCard(entry = entry)

        if (externalUrl != null) {
            DetailExternalLinkCard(
                externalUrl = externalUrl
            )
        }
    }
}

private val GalleryOrderedCategories = listOf(
    GalleryCategory.POSTER,
    GalleryCategory.BACKDROP,
    GalleryCategory.LOGO,
    GalleryCategory.CLEARART,
    GalleryCategory.SQUARE,
    GalleryCategory.CHARACTER,
    GalleryCategory.PERSON,
    GalleryCategory.THUMBNAIL,
    GalleryCategory.BANNER,
    GalleryCategory.OTHER
)

private val GalleryThumbScrimBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.55f)
    )
)

private class GalleryIndexedEntry(
    val item: GalleryItem,
    val globalIndex: Int,
    val stableKey: String,
    val aspectRatio: Float,
    val cardWidthDp: Float
)

private class GallerySourceRowModel(
    val sourceName: String,
    val entries: List<GalleryIndexedEntry>
)

private class GalleryCategorySectionModel(
    val category: GalleryCategory,
    val sourceRows: List<GallerySourceRowModel>,
    val emoji: String = ""
)

private class GalleryPrecomputedModel(
    val availableCategories: List<GalleryCategory>,
    val categoryCounts: Map<GalleryCategory, Int>,
    val availableSources: List<Pair<String, Int>>,
    val sections: List<GalleryCategorySectionModel>
)

private fun galleryItemAspectRatio(category: GalleryCategory): Float = when (category) {
    GalleryCategory.BACKDROP -> 16f / 9f
    GalleryCategory.CLEARART -> 16f / 9f
    GalleryCategory.BANNER   -> 5.4f / 1f
    GalleryCategory.POSTER   -> 2f / 3f
    GalleryCategory.SQUARE   -> 1f
    GalleryCategory.LOGO     -> 3f / 1.4f
    else                     -> 1f
}

private fun gallerySourceBadgeColor(sourceLower: String, fallback: Color): Color = when (sourceLower) {
    "tmdb"                        -> Color(0xFFFFB800)
    "fanart.tv"                   -> Color(0xFF9C27B0)
    "anilist"                     -> Color(0xFF02A9FF)
    "simkl"                       -> Color(0xFFE50914)
    "kitsu"                       -> Color(0xFFE35A02)
    "shikimori"                   -> Color(0xFF4C86C8)
    "bangumi", "bgm"              -> Color(0xFFF09199)
    "jikan", "jikan (mal)", "mal" -> Color(0xFF2E51A2)
    else                          -> fallback
}

private fun precomputeGalleryModel(items: List<GalleryItem>): GalleryPrecomputedModel {
    val byCategory = java.util.EnumMap<GalleryCategory, MutableList<GalleryIndexedEntry>>(GalleryCategory::class.java)
    val sourceBuckets = LinkedHashMap<String, Pair<String, Int>>()

    items.forEachIndexed { index, item ->
        val aspect = galleryItemAspectRatio(item.category)
        val entry = GalleryIndexedEntry(
            item = item,
            globalIndex = index,
            stableKey = "${index}_${item.url}",
            aspectRatio = aspect,
            cardWidthDp = 100f * aspect
        )
        byCategory.getOrPut(item.category) { ArrayList() }.add(entry)

        val trimmedSource = item.source.trim()
        if (trimmedSource.isNotEmpty()) {
            val lower = trimmedSource.lowercase()
            val prev = sourceBuckets[lower]
            sourceBuckets[lower] = if (prev == null) trimmedSource to 1 else prev.first to (prev.second + 1)
        }
    }

    val availableCategories = ArrayList<GalleryCategory>(GalleryOrderedCategories.size)
    val categoryCounts = java.util.EnumMap<GalleryCategory, Int>(GalleryCategory::class.java)
    val sections = ArrayList<GalleryCategorySectionModel>(GalleryOrderedCategories.size)

    for (category in GalleryOrderedCategories) {
        val catEntries = byCategory[category]
        if (!catEntries.isNullOrEmpty()) {
            availableCategories.add(category)
            categoryCounts[category] = catEntries.size

            val sourceMap = java.util.TreeMap<String, MutableList<GalleryIndexedEntry>>()
            for (entry in catEntries) {
                val srcKey = entry.item.source.trim().lowercase()
                sourceMap.getOrPut(srcKey) { ArrayList() }.add(entry)
            }
            val rows = sourceMap.values.map { rowEntries ->
                GallerySourceRowModel(
                    sourceName = rowEntries.firstOrNull()?.item?.source.orEmpty(),
                    entries = rowEntries
                )
            }
            sections.add(
                GalleryCategorySectionModel(
                    category = category,
                    sourceRows = rows
                )
            )
        }
    }

    val availableSources = sourceBuckets.entries
        .sortedBy { it.key }
        .map { it.value }

    return GalleryPrecomputedModel(
        availableCategories = availableCategories,
        categoryCounts = categoryCounts,
        availableSources = availableSources,
        sections = sections
    )
}

/**
 * Fanart.tv + çok kaynaklı galeri önizleme kartı.
 * Yatay kaydırılabilir küçük resim listesi; her görselin üzerinde
 * kaynak (Fanart.tv / TMDB / Jikan) ve kategori (Logo / Arka Plan…) pill badge'leri gösterilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailGalleryCard(
    items: List<GalleryItem>,
    onItemClick: (index: Int) -> Unit,
    onOpenGallery: ((category: GalleryCategory?) -> Unit)? = null,
    onOpenSourceGallery: ((source: String) -> Unit)? = null,
    isAdult: Boolean = false
) {
    val accentColor = LocalKitsugiAccent.current
    // Galeri tüm kaynaklardan yüklenene kadar görseller ve kategori butonu tıklanamaz.
    val galleryLoading = LocalKitsugiGalleryLoading.current
    val galleryLoadingState = rememberUpdatedState(galleryLoading)
    val latestOnItemClickState = rememberUpdatedState(onItemClick)

    val precomputed = remember(items) { precomputeGalleryModel(items) }
    val initialSectionBatch = 3
    var mountedSectionCount by remember(precomputed) {
        mutableIntStateOf(minOf(initialSectionBatch, precomputed.sections.size))
    }
    LaunchedEffect(precomputed) {
        while (mountedSectionCount < precomputed.sections.size) {
            withFrameNanos { }
            mountedSectionCount = minOf(precomputed.sections.size, mountedSectionCount + 2)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.SurfaceSoft)
            .border(1.dp, KitsugiColors.Border, RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp)
    ) {
        // Section header and source/category picker sheet.
        var showCategoryMenu by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.detail_tab_images),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.gallery_summary, items.size, precomputed.availableCategories.size),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            IconButton(
                onClick = { showCategoryMenu = true },
                enabled = !galleryLoading
            ) {
                Icon(
                    imageVector = Icons.PhotoLibrary,
                    contentDescription = stringResource(R.string.cd_open_gallery),
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (showCategoryMenu) {
            val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showCategoryMenu = false },
                sheetState = pickerSheetState,
                containerColor = KitsugiColors.SurfaceStrong,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(KitsugiColors.TextMuted.copy(alpha = 0.45f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                        .kitsugiVerticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_tab_images),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )

                    Text(
                        text = stringResource(R.string.gallery_field_type),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                    GalleryPickerRow(
                        icon = Icons.FolderOpen,
                        label = stringResource(R.string.action_all_count, items.size),
                        selected = false,
                        onClick = {
                            showCategoryMenu = false
                            onOpenGallery?.invoke(null)
                        }
                    )
                    precomputed.availableCategories.forEach { category ->
                        val count = precomputed.categoryCounts[category] ?: 0
                        GalleryPickerRow(
                            icon = galleryCategoryIcon(category),
                            label = "${category.localizedLabel()} ($count)",
                            selected = false,
                            onClick = {
                                showCategoryMenu = false
                                onOpenGallery?.invoke(category)
                            }
                        )
                    }

                    if (precomputed.availableSources.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.gallery_field_source),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
                        )
                        precomputed.availableSources.forEach { (source, count) ->
                            GalleryPickerRow(
                                icon = null,
                                label = "$source ($count)",
                                source = source,
                                selected = false,
                                onClick = {
                                    showCategoryMenu = false
                                    onOpenSourceGallery?.invoke(source)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        precomputed.sections.forEachIndexed { sectionIndex, section ->
            if (sectionIndex < mountedSectionCount) {
                GalleryCategorySectionBlock(
                    section = section,
                    isAdult = isAdult,
                    accentColor = accentColor,
                    galleryLoadingState = galleryLoadingState,
                    onItemClickState = latestOnItemClickState
                )
            } else {
                // Henüz monte edilmemiş alt kategoriler için deterministik yükseklik ayır
                // (ilk karede 20 LazyRow yaratmadan sayfa boyunu sabit tutar).
                val placeholderHeight = 42.dp + (128.dp * section.sourceRows.size)
                Spacer(modifier = Modifier.fillMaxWidth().height(placeholderHeight))
            }
        }
    }
}

@Composable
private fun GalleryCategorySectionBlock(
    section: GalleryCategorySectionModel,
    isAdult: Boolean,
    accentColor: Color,
    galleryLoadingState: androidx.compose.runtime.State<Boolean>,
    onItemClickState: androidx.compose.runtime.State<(Int) -> Unit>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        val localizedCategoryLabel = section.category.localizedLabel()

        val catIcon = galleryCategoryIcon(section.category)
        KitsugiEmojiLabel(
            label = localizedCategoryLabel,
            icon = catIcon,
            iconSize = 14.dp,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        section.sourceRows.forEach { rowModel ->
            val sourceName = rowModel.sourceName
            val sourceLower = remember(sourceName) { sourceName.lowercase() }
            val sourceLogoRes = remember(sourceName) { KitsugiPlatformLogos.resFor(sourceName) }
            val badgeBgColor = remember(sourceLower, accentColor) {
                gallerySourceBadgeColor(sourceLower, accentColor)
            }
            val badgeTextColor = if (sourceLower == "tmdb" || badgeBgColor == Color(0xFFFFB800)) {
                Color.Black
            } else {
                Color.White
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sourceLogoRes != null) {
                    KitsugiPlatformLogo(platformId = sourceName, size = 18.dp, cornerRadius = 4.dp)
                }
                Text(
                    text = "$sourceName · ${rowModel.entries.size}",
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp
            val gridColumns = remember(screenWidthDp) {
                // GridCells.Adaptive(minSize = 150.dp) paritesi:
                // Küçük telefon (~300dp): 2 sütun
                // Orta telefon (~450dp): 3 sütun
                // Tablet dikey (~600dp): 4 sütun
                // Tablet yatay/büyük ekran (~900dp+): 5–6+ sütun
                when {
                    screenWidthDp >= 900 -> 6
                    screenWidthDp >= 600 -> 4
                    screenWidthDp >= 450 -> 3
                    else -> 2
                }
            }

            val chunkedRows = remember(rowModel.entries, gridColumns) {
                rowModel.entries.chunked(gridColumns)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                chunkedRows.forEach { rowEntries ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowEntries.forEach { entry ->
                            val item = entry.item
                            val cardShape = RoundedCornerShape(12.dp)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(entry.aspectRatio)
                                    .clip(cardShape)
                                    .border(1.dp, KitsugiColors.Border, cardShape)
                                    .graphicsLayer {
                                        alpha = if (galleryLoadingState.value) 0.5f else 1f
                                    }
                                    .tvClickable(shape = cardShape) {
                                        if (!galleryLoadingState.value) {
                                            onItemClickState.value(entry.globalIndex)
                                        }
                                    }
                            ) {
                                com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                                    model = item.url,
                                    contentDescription = "$localizedCategoryLabel – $sourceName",
                                    isAdult = isAdult,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(GalleryThumbScrimBrush)
                                )

                                if (sourceLogoRes != null) {
                                    KitsugiPlatformLogo(
                                        platformId = sourceName,
                                        size = 20.dp,
                                        cornerRadius = 4.dp,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(badgeBgColor)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = sourceName,
                                            color = badgeTextColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!item.description.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.description,
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        // Son satırda eksik kalan sütunları doldurarak eşit genişlik koruma:
                        repeat(gridColumns - rowEntries.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Gallery placeholders retain the horizontal thumbnail rail's real proportions. */
@Composable
internal fun DetailGalleryLoadingCard() {
    DetailGalleryLoadingSkeleton()
}

/**
 * Visible tab 9 — Episodes content for MediaEntryDetailPage.
 */
@Composable
internal fun EntryDetailEpisodesTab(
    entry: MediaEntry,
    detailState: KitsugiMediaDetail?,
    state: DetailTabState<List<KitsugiStreamingEpisode>>,
    episodeRatings: Map<Pair<Int, Int>, Double>,
    targetSeason: Int?,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeOptionsRequested: (KitsugiStreamingEpisode) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // `MediaEntry.id` is a local Room row ID. Resolve only source-specific external IDs.
    val streamIds = entry.toStreamMediaIds(
        resolvedRealMalId = detailState?.realMalId,
        resolvedTmdbId = entry.tmdbId ?: detailState?.tmdbId
    )
    val currentAnimeId = (streamIds.aniListId ?: streamIds.malId ?: streamIds.tmdbId ?: streamIds.bangumiId)
        ?.toString().orEmpty()

    EpisodesTabContent(
        state = state,
        animeId = currentAnimeId,
        isAdult = entry.isAdult,
        episodeRatings = episodeRatings,
        targetSeason = targetSeason,
        totalSeasons = detailState?.totalSeasons,
        onSeasonSelected = onSeasonSelected,
        onEpisodeClick = { episode ->
            val epNum = episode.episodeNumber
            if (epNum != null && epNum > 0) {
                val completedDownload = com.kitsugi.animelist.data.local.AnimeDownloadManager.downloads.value.find {
                    !it.isAudioOnly &&
                    it.animeId == currentAnimeId &&
                    it.episode == epNum &&
                    (it.season == (targetSeason ?: 1) || it.season == (episode.seasonNumber ?: 1)) &&
                    it.status == com.kitsugi.animelist.data.model.AnimeDownload.Status.COMPLETED &&
                    it.localPath != null
                }
                val offlineParams = completedDownload?.let { dl ->
                    com.kitsugi.animelist.core.player.OfflinePlaybackHelper.resolveDownloadPlaybackParams(
                        context = context,
                        download = dl,
                        fallbackTitle = context.getString(R.string.completeddownload_animetitle_bolum_completed, dl.animeTitle, dl.episode)
                    )
                }
                if (offlineParams != null) {
                    KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                        context = context,
                        videoUrl = offlineParams.videoUri,
                        audioUrl = offlineParams.audioUri,
                        title = offlineParams.title,
                        headers = offlineParams.headers,
                        subtitles = offlineParams.subtitles,
                        malId = streamIds.malId, aniListId = streamIds.aniListId,
                        tmdbId = streamIds.tmdbId, bangumiId = streamIds.bangumiId,
                        imdbId = detailState?.externalLinks?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                            ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() },
                        season = episode.seasonNumber ?: targetSeason ?: 1, episode = epNum,
                        animeTitle = entry.title, titleEnglish = detailState?.titleEnglish,
                        titleRomaji = detailState?.titleRomaji, titleNative = detailState?.titleNative,
                        synonyms = detailState?.synonyms.orEmpty(), startYear = entry.year,
                        isMovie = entry.type == com.kitsugi.animelist.model.MediaType.Movie
                    )
                } else {
                    KitsugiStreamActivity.start(
                        context = context,
                        malId = streamIds.malId,
                        aniListId = streamIds.aniListId,
                        tmdbId = streamIds.tmdbId,
                        bangumiId = streamIds.bangumiId,
                        mediaType = entry.type,
                        episode = epNum,
                        season = targetSeason ?: 1,
                        isMovie = entry.type == com.kitsugi.animelist.model.MediaType.Movie,
                        title = entry.title,
                        posterUrl = entry.imageUrl,
                        titleEnglish = detailState?.titleEnglish,
                        titleRomaji = detailState?.titleRomaji,
                        titleNative = detailState?.titleNative,
                        synonyms = detailState?.synonyms.orEmpty(),
                        startYear = entry.year,
                        imdbId = detailState?.externalLinks?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                            ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() }
                    )
                }
            } else {
                onEpisodeOptionsRequested(episode)
            }
        },
        onDownloadClick = { episode ->
            val epNum = episode.episodeNumber
            if (epNum != null && epNum > 0) {
                KitsugiStreamActivity.start(
                    context = context,
                    malId = streamIds.malId,
                    aniListId = streamIds.aniListId,
                    tmdbId = streamIds.tmdbId,
                    bangumiId = streamIds.bangumiId,
                    mediaType = entry.type,
                    episode = epNum,
                    season = targetSeason ?: 1,
                    isMovie = entry.type == com.kitsugi.animelist.model.MediaType.Movie,
                    title = entry.title,
                    posterUrl = entry.imageUrl,
                    titleEnglish = detailState?.titleEnglish,
                    titleRomaji = detailState?.titleRomaji,
                    titleNative = detailState?.titleNative,
                    synonyms = detailState?.synonyms.orEmpty(),
                    startYear = entry.year,
                    imdbId = detailState?.externalLinks?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                        ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() },
                    isDownloadMode = true
                )
            }
        },
        onRatingClick = { season, episode ->
            val tmdbId = detailState?.tmdbId
            if (tmdbId != null && tmdbId > 0) {
                val url = "https://www.themoviedb.org/tv/$tmdbId/season/$season/episode/$episode"
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    runCatching { uriHandler.openUri(url) }
                }
            }
        }
    )
}


@Composable
private fun GalleryPickerRow(
    icon: ImageVector?,
    label: String,
    selected: Boolean,
    source: String? = null,
    onClick: () -> Unit
) {
    val accent = LocalKitsugiAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) KitsugiColors.SurfaceSoft else KitsugiColors.SurfaceStrong)
            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (source != null) {
            val logoRes = KitsugiPlatformLogos.resFor(source)
            if (logoRes != null) KitsugiPlatformLogo(platformId = source, size = 22.dp, cornerRadius = 5.dp)
            else Text(text = source.take(1).uppercase(), color = accent, fontWeight = FontWeight.Bold)
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else KitsugiColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            color = if (selected) accent else KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}


