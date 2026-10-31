package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.ui.components.KitsugiGalleryIconButton
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.KitsugiStudioDetail
import com.kitsugi.animelist.data.remote.KitsugiStaffMediaWork
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.toLocalizedOrgName
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import com.kitsugi.animelist.utils.parseToMediaType
import com.kitsugi.animelist.ui.components.KitsugiMarkdownText
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp

// Icons
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons

/**
 * Stüdyo kuruluş tarihini "1 Ekim 1998" biçiminde gösterir — ham ISO
 * ("1998-10-01T00:00:00+00:00") dizgesi asla olduğu gibi gösterilmez.
 */
internal fun formatStudioEstablished(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return com.kitsugi.animelist.utils.KitsugiDateUtils
        .formatBirthdayAndCalculateAge(raw, null)
        .first ?: raw
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioHeroHeader(
    detail: KitsugiStudioDetail,
    source: String,
    accentColor: Color,
    onBackClick: () -> Unit,
    isFavourite: Boolean = false,
    showFavouriteButton: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    onGalleryClick: (() -> Unit)? = null,
    height: Dp = 340.dp
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        // Logo / Fallback box
        if (!detail.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = detail.imageUrl,
                contentDescription = detail.name.toLocalizedOrgName(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            // Blur or dark layer over image for visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                KitsugiColors.Background.copy(alpha = 0.4f),
                                KitsugiColors.Background
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.2f),
                                KitsugiColors.Background
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = detail.name.toLocalizedOrgName().take(2).uppercase(),
                    color = accentColor,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 40.dp)
                )
            }
        }

        // Top Action Bar: Back (left) + Share & Favourite (right)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = KitsugiColors.TextPrimary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onGalleryClick != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiGalleryIconButton(onClick = onGalleryClick, accentColor = accentColor)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {
                        val url = com.kitsugi.animelist.utils.ShareUtils.buildStudioUrl(source, detail.id, detail.name.toLocalizedOrgName())
                        com.kitsugi.animelist.utils.ShareUtils.shareText(context, detail.name.toLocalizedOrgName(), url)
                    }) {
                        Icon(
                            imageVector = Icons.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                }

                if (showFavouriteButton) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onToggleFavourite) {
                            Icon(
                                imageVector = if (isFavourite) Icons.Favorite else Icons.FavoriteBorder,
                                contentDescription = if (isFavourite) stringResource(R.string.action_favourite_remove) else stringResource(R.string.action_favourite_add),
                                tint = if (isFavourite) accentColor else KitsugiColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Details Column (Name & Info Row)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                // Şirket adı TR/EN dil dosyasından (bangumi_studio_* vb.); bilinmeyen ad
                // Latin/orijinal hâliyle kalır — bkz. MASTER 2026-10-11.
                text = detail.name.toLocalizedOrgName(),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailPill(
                    text = if (detail.isMain) stringResource(R.string.studio_hero_main) else stringResource(R.string.studio_hero_producer),
                    color = accentColor
                )

                DetailSourcePill(
                    source = source,
                    color = if (source.equals("tmdb", ignoreCase = true)) Color(0xFFFFB800) else KitsugiColors.TextSecondary
                )

                if (detail.established != null) {
                    DetailPill(
                        text = stringResource(R.string.studio_hero_established, formatStudioEstablished(detail.established) ?: detail.established),
                        color = KitsugiColors.TextSecondary
                    )
                }

                if (detail.favorites != null && detail.favorites > 0) {
                    DetailPill(
                        text = stringResource(R.string.studio_hero_favorites, detail.favorites),
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun StudioMediaGridItem(
    work: KitsugiStaffMediaWork,
    titleLanguage: String = "ROMAJI",
    onMediaClick: (mediaId: Int, mediaType: String, mediaSource: String) -> Unit
) {
    val displayTitle = work.getDisplayTitle(titleLanguage)

    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(105.dp)
            .tvClickable { onMediaClick(work.mediaId, work.mediaType, work.source) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(11f / 16f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(KitsugiColors.Surface)
        ) {
            if (!work.mediaImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = work.mediaImageUrl,
                    contentDescription = displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayTitle.take(2).uppercase(),
                        color = KitsugiColors.TextMuted,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = displayTitle,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Stüdyo "Hakkında" kartı — detay sayfasındaki Açıklama kartıyla (DetailSynopsisCard)
 * BİREBİR aynı davranış: otomatik çeviri açıksa çevrilmiş metin, çeviri (3. parti)
 * butonu, kopyala butonu ve uzun metinlerde "Daha fazla / Daha az" genişletme.
 */
@Composable
internal fun StudioAboutSection(
    about: String,
    onGalleryClick: (List<GalleryItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    translatedAbout: String? = null,
    onTranslateClick: ((String) -> Unit)? = null,
    onCopyClick: ((String) -> Unit)? = null,
    aboutHasTranslation: Boolean = false,
    showAboutOriginal: Boolean = false,
    onToggleAboutOriginal: (() -> Unit)? = null
) {
    Box(modifier = modifier) {
        val aboutTitle = stringResource(R.string.studio_about_title)
        DetailSynopsisCard(
            title = aboutTitle,
            synopsisState = SynopsisState.Success(if (showAboutOriginal) about else translatedAbout ?: about),
            originalText = about,
            onTranslateClick = onTranslateClick,
            onCopyClick = onCopyClick,
            hasTranslation = aboutHasTranslation,
            showingOriginal = showAboutOriginal,
            onToggleOriginal = onToggleAboutOriginal,
            onImageGalleryRequest = { urls, idx ->
                val items = urls.map { url -> GalleryItem(url = url, category = GalleryCategory.OTHER, source = aboutTitle) }
                onGalleryClick(items, idx)
            }
        )
    }
}

@Composable
internal fun StudioDetailLeftPanel(
    detail: KitsugiStudioDetail,
    source: String,
    studioId: Int,
    accentColor: Color,
    isFavourite: Boolean,
    showFavouriteButton: Boolean,
    galleryItems: List<GalleryItem>,
    onBackClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onGalleryClick: (List<GalleryItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    translatedAbout: String? = null,
    onTranslateClick: ((String) -> Unit)? = null,
    onCopyClick: ((String) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(KitsugiColors.Background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            if (!detail.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = detail.imageUrl,
                    contentDescription = detail.name.toLocalizedOrgName(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(KitsugiColors.Background.copy(alpha = 0.4f), KitsugiColors.Background))
                    )
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.2f), KitsugiColors.Background))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(detail.name.toLocalizedOrgName().take(2).uppercase(), color = accentColor, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                }
            }
            // Top Action Bar: Back (left) + Share & Favourite (right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (galleryItems.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiGalleryIconButton(onClick = {
                                onGalleryClick(galleryItems, 0)
                            }, accentColor = accentColor)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            val url = com.kitsugi.animelist.utils.ShareUtils.buildStudioUrl(source, studioId, detail.name.toLocalizedOrgName())
                            com.kitsugi.animelist.utils.ShareUtils.shareText(context, detail.name.toLocalizedOrgName(), url)
                        }) {
                            Icon(
                                imageVector = Icons.Share,
                                contentDescription = stringResource(R.string.action_share),
                                tint = KitsugiColors.TextPrimary
                            )
                        }
                    }

                    if (showFavouriteButton) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onToggleFavourite) {
                                Icon(
                                    imageVector = if (isFavourite) Icons.Favorite else Icons.FavoriteBorder,
                                    contentDescription = if (isFavourite) stringResource(R.string.action_favourite_remove) else stringResource(R.string.action_favourite_add),
                                    tint = if (isFavourite) accentColor else KitsugiColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(detail.name.toLocalizedOrgName(), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(12.dp))
            DetailPill(text = if (detail.isMain) stringResource(R.string.studio_hero_main) else stringResource(R.string.studio_hero_producer), color = accentColor)
            Spacer(modifier = Modifier.height(8.dp))
            if (detail.established != null) {
                DetailPill(
                    text = stringResource(R.string.studio_hero_established, formatStudioEstablished(detail.established) ?: detail.established),
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (detail.favorites != null && detail.favorites > 0) {
                DetailPill(text = stringResource(R.string.studio_hero_favorites, detail.favorites), color = accentColor)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (!detail.about.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                StudioAboutSection(
                    about = detail.about,
                    onGalleryClick = onGalleryClick,
                    translatedAbout = translatedAbout,
                    onTranslateClick = onTranslateClick,
                    onCopyClick = onCopyClick
                )
            }
        }
    }
}

// ── Keşfet "Tümünü Gör" sayfalarıyla birebir aynı filtreleme dili ──────────────

/** Stüdyo yapımları için tür çipi (keşfet sayfasındaki emojili tür çiplerinin karşılığı). */
internal data class StudioTypeFilter(val id: String, val emoji: String, @StringRes val nameRes: Int) {
    @Composable
    @ReadOnlyComposable
    fun displayLabel(): String = "$emoji ${stringResource(nameRes)}"
}

internal val STUDIO_TYPE_FILTERS = listOf(
    StudioTypeFilter("ALL", "✨", R.string.studio_type_all),
    StudioTypeFilter("ANIME", "🎬", R.string.studio_type_anime),
    StudioTypeFilter("MANGA", "📖", R.string.studio_type_manga),
    StudioTypeFilter("MOVIE", "🎥", R.string.studio_type_movie),
    StudioTypeFilter("TV", "📺", R.string.studio_type_tv)
)

internal enum class StudioSortOption(val emoji: String, @StringRes val titleRes: Int) {
    DEFAULT("🏆", R.string.studio_sort_default),
    TITLE_ASC("🔤", R.string.studio_sort_title_asc),
    TITLE_DESC("🔠", R.string.studio_sort_title_desc);

    @Composable
    @ReadOnlyComposable
    fun displayLabel(): String = "$emoji ${stringResource(titleRes)}"
}

/** Stüdyo yapımını keşfet kartlarıyla aynı görsel dili paylaşan JikanSearchResult'e çevirir. */
internal fun KitsugiStaffMediaWork.toSearchResult(): JikanSearchResult = JikanSearchResult(
    malId = mediaId,
    title = mediaTitle,
    subtitle = staffRole,
    type = mediaType.parseToMediaType(),
    total = null,
    score = null,
    isAdult = false,
    imageUrl = mediaImageUrl,
    year = null,
    source = source,
    titleEnglish = titleEnglish,
    titleJapanese = titleJapanese,
    titleRomaji = titleRomaji
)

internal fun studioTypeMatches(typeId: String, type: MediaType): Boolean = when (typeId) {
    "ALL" -> true
    "ANIME" -> type == MediaType.Anime
    "MANGA" -> type == MediaType.Manga
    "MOVIE" -> type == MediaType.Movie
    "TV" -> type == MediaType.TvShow
    else -> true
}

/**
 * Stüdyo/yapımcı detay sayfası için "Filtre ve Sıralama" bottom sheet'i —
 * [com.kitsugi.animelist.ui.components.KitsugiMediaFilterBottomSheet] ile aynı
 * görsel dili kullanır (sıralama + tür çipleri), ancak stüdyo verisine uyarlanır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KitsugiStudioFilterBottomSheet(
    initialTypeId: String,
    initialSortOption: StudioSortOption,
    onDismissRequest: () -> Unit,
    onApply: (typeId: String, sortOption: StudioSortOption) -> Unit,
    onReset: () -> Unit,
    typeFilters: List<StudioTypeFilter> = STUDIO_TYPE_FILTERS
) {
    val accentColor = LocalKitsugiAccent.current

    var selectedTypeId by remember { mutableStateOf(initialTypeId) }
    var selectedSortOption by remember { mutableStateOf(initialSortOption) }

    KitsugiSheetOrDialog(onDismiss = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        selectedTypeId = "ALL"
                        selectedSortOption = StudioSortOption.DEFAULT
                        onReset()
                        onDismissRequest()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.inline_reset_8fb7f0b),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = stringResource(R.string.inline_filter_sort_dfe53ba),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                KitsugiButton(
                    onClick = {
                        onApply(selectedTypeId, selectedSortOption)
                        onDismissRequest()
                    },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_apply),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Sort,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.inline_sort_ad5c074),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioSortOption.entries.forEach { sort ->
                        val isSelected = selectedSortOption == sort
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.22f)
                                    else KitsugiColors.SurfaceSoft
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedSortOption = sort }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sort.displayLabel(),
                                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.FilterAlt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.inline_content_type_a4a22c7),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    typeFilters.forEach { typeFilter ->
                        val isSelected = selectedTypeId == typeFilter.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.22f)
                                    else KitsugiColors.SurfaceSoft
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedTypeId = typeFilter.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = typeFilter.displayLabel(),
                                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
