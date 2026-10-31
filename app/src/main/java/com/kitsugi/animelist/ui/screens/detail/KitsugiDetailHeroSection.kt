package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.ui.components.KitsugiGalleryIconButton
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.components.heroImageAlignment
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.copyOnDoubleTap
import com.kitsugi.animelist.utils.ShareUtils
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailHero(
    entry: MediaEntry,
    logoUrl: String?,
    onBackClick: () -> Unit,
    titleLanguage: String,
    blurAdultMedia: Boolean = false,
    /** Vitrinde o anda gerçekten gösterilen görselin URL'sini ve türünü döndürür. */
    onPosterClick: (String, GalleryCategory) -> Unit = { _, _ -> },
    onShareClick: (() -> Unit)? = null,
    onGalleryClick: (() -> Unit)? = null,
    nextAiring: String? = null,
    showFavoriteButton: Boolean = false,
    onToggleFavoriteClick: (() -> Unit)? = null,
    /** Kayıtla ilişkili galeri görselleri: yatay modda dikey poster, dikey modda yatay arka plan adayları vitrine akar. */
    galleryItems: List<GalleryItem> = emptyList()
) {
    val accentColor = LocalKitsugiAccent.current
    val statusColor = statusColor(entry.status)
    val context = LocalContext.current

    // ── Görsel seçimi: YÖN ⇄ GÖRSEL OTOMATİK EŞLEŞMESİ ─────────────────────────
    // KURAL: Bir yapımın görsel çifti sabittir (dikey-uzun-dar POSTER + yatay-geniş-kısa
    // ARKA PLAN); vitrin ekran yönüne göre doğru eşi OTOMATİK seçer ve görsel alanı
    // gösterdiği görselin şekline birebir uyup resmi TAM kaplar:
    //  - YATAY mod → en kaliteli DİKEY POSTER, uzun-dar kutuda tam kaplama.
    //  - DİKEY mod → en kaliteli YATAY ARKA PLAN, geniş-kısa bantta tam kaplama.
    // Eşlerden biri yoksa diğeri gösterilir; görsel alanı yine Crop ile TAM kaplanır.
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.toFloat()
    val isLandscapeScreen = configuration.screenWidthDp > configuration.screenHeightDp

    val posterUrl = remember(entry.imageUrl) { KitsugiHeroArtworkResolver.upgradeNativePosterUrl(entry.imageUrl) }
    val wideGalleryUrls = remember(galleryItems) {
        galleryItems.filter { isHorizontalGalleryItem(it) }.sortedByDescending { galleryPixels(it) }.map { it.url }
    }
    val tallGalleryUrls = remember(galleryItems) {
        galleryItems.filter { isVerticalGalleryItem(it) }.sortedByDescending { galleryPixels(it) }.map { it.url }
    }
    val tallCandidates = remember(posterUrl, tallGalleryUrls) {
        listOfNotNull(posterUrl) + tallGalleryUrls
    }
    // YÖN ⇄ GÖRSEL OTOMATİK EŞLEŞMESİ: yatay modda dikey poster birincil,
    // dikey modda yatay arka plan birincil (çift sabit olduğu için eşleşme otomatiktir).
    val imagePlan = remember(wideGalleryUrls, tallCandidates, isLandscapeScreen) {
        if (isLandscapeScreen) (tallCandidates + wideGalleryUrls).distinct()
        else (wideGalleryUrls + tallCandidates).distinct()
    }
    var imageIndex by remember(imagePlan) { mutableIntStateOf(0) }
    val heroImageModel = imagePlan.getOrNull(imageIndex)
    val chosenIsPoster = heroImageModel != null && heroImageModel !in wideGalleryUrls
    var loadedImageAspect by remember(heroImageModel) { mutableStateOf<Float?>(null) }
    val isVerticalArtwork = loadedImageAspect?.let { it < 1.05f } ?: chosenIsPoster

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        // GÖRSEL ALANININ ŞEKLİ: yönün varsayılan oranıyla başlar, görsel yüklenince
        // GERÇEK en-boy oranına kilitlenir → kutu, gösterdiği görselle birebir aynı
        // şekle gelir ve görsel (Crop ile) alanı TAM kaplar; kırpma/boşluk ≈ sıfır.
        //  - YATAY mod → 2:3 DİKEY poster kutusu (uzun-dar).
        //  - DİKEY mod → 16:9 YATAY arka plan bandı (geniş-kısa).
        val defaultBoxAspect = if (isLandscapeScreen) 2f / 3f else 16f / 9f
        val boxAspect = (loadedImageAspect ?: defaultBoxAspect).coerceIn(
            minimumValue = if (isLandscapeScreen) 0.5f else 1.0f,
            maximumValue = if (isLandscapeScreen) 1.6f else 2.2f
        )
        val bannerCapDp = if (isLandscapeScreen) screenHeightDp else screenHeightDp * 0.62f
        val bannerHeight = (maxWidth / boxAspect).coerceAtMost(bannerCapDp.dp)

        // Başlık, meta bilgileri ve rozetler ayrı bir koyu alan ayırmak yerine
        // görselin alt bölümüne bindirilir. Böylece hem dikey hem yatay düzende
        // vitrinin altında gereksiz boş bir blok kalmaz.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
        ) {
            val heroBoxWidthDp = maxWidth.value
            val imageAreaModifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .align(Alignment.TopCenter)
            val imageAreaAspect = if (bannerHeight.value > 0f) heroBoxWidthDp / bannerHeight.value else 1f
            val heroAlignmentValue = heroImageAlignment(
                chosenIsPoster = chosenIsPoster,
                heroAspect = imageAreaAspect,
                imageAspect = loadedImageAspect
            )
            val displayedImageUrl = heroImageModel
            if (!displayedImageUrl.isNullOrBlank()) {
                val displayedCategory = if (isVerticalArtwork) {
                    GalleryCategory.POSTER
                } else {
                    GalleryCategory.BACKDROP
                }
                KitsugiNsfwImage(
                    model = displayedImageUrl,
                    contentDescription = entry.getDisplayTitle(titleLanguage),
                    isAdult = entry.isAdult,
                    blurAdultMedia = blurAdultMedia,
                    modifier = imageAreaModifier
                        .tvClickable {
                            // Kaynak posteri değil, ekranda o anda seçilmiş gerçek
                            // poster/backdrop URL'sini galeriye aktar.
                            onPosterClick(displayedImageUrl, displayedCategory)
                        },
                    contentScale = ContentScale.Crop,
                    alignment = heroAlignmentValue,
                    onLoadingFailed = {
                        if (imageIndex < imagePlan.lastIndex) imageIndex += 1
                    },
                    onImageAspectResolved = { ratio -> loadedImageAspect = ratio },
                    highQuality = true
                )
            } else {
                Box(
                    modifier = imageAreaModifier
                        .background(statusColor.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.getDisplayTitle(titleLanguage).take(2).uppercase(),
                        color = statusColor,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                KitsugiColors.Background.copy(alpha = 0.05f),
                                KitsugiColors.Background.copy(alpha = 0.30f),
                                KitsugiColors.Background.copy(alpha = 0.72f),
                                KitsugiColors.Background
                            )
                        )
                    )
            )

            // ── Top action bar: Back (left) + Action buttons (right) ───────────────
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Geri butonu
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
                            contentDescription = stringResource(R.string.geri),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Galeri butonu (yalnızca içerik varsa görünür)
                    if (onGalleryClick != null) {
                        val accentColorLocal = LocalKitsugiAccent.current
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColorLocal.copy(alpha = 0.22f))
                                .then(
                                    Modifier.border(
                                        width = 1.dp,
                                        color = accentColorLocal.copy(alpha = 0.45f),
                                        shape = CircleShape
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiGalleryIconButton(onClick = onGalleryClick, accentColor = accentColorLocal)
                        }
                    }

                    // Paylaş butonu
                    val shareAction: () -> Unit = onShareClick ?: {
                        // Kimlik yoksa `entry.id` (yerel veritabanı satırı) ASLA kullanılmaz;
                        // çözülemezse bağlantısız paylaşılır.
                        val url = ShareUtils.buildMediaUrl(entry)
                        ShareUtils.shareText(context, entry.title, url)
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = shareAction) {
                            Icon(
                                imageVector = Icons.Share,
                                contentDescription = stringResource(R.string.paylas),
                                tint = KitsugiColors.TextPrimary
                            )
                        }
                    }

                    if (showFavoriteButton && onToggleFavoriteClick != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onToggleFavoriteClick) {
                                Icon(
                                    imageVector = if (entry.isFavorite) Icons.Favorite else Icons.FavoriteBorder,
                                    contentDescription = if (entry.isFavorite) stringResource(R.string.action_favourite_remove) else "Favori Yap",
                                    tint = if (entry.isFavorite) accentColor else KitsugiColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                val context = LocalContext.current
                val titleText = entry.getDisplayTitle(titleLanguage)
                val copyTitleGesture = Modifier.copyOnDoubleTap(context, titleText)

                if (!logoUrl.isNullOrBlank()) {
                    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
                    if (!logoFailed) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = entry.getDisplayTitle(titleLanguage),
                            modifier = Modifier
                                .height(75.dp)
                                .fillMaxWidth(0.85f)
                                .then(copyTitleGesture),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            onError = {
                                logoFailed = true
                            }
                        )
                    } else {
                        Text(
                            text = entry.getDisplayTitle(titleLanguage),
                            modifier = copyTitleGesture,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = entry.getDisplayTitle(titleLanguage),
                        modifier = copyTitleGesture,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Format
                    val formatIcon = if (entry.type == MediaType.Manga) Icons.Book else Icons.Tv
                    val formatText = when (entry.type) {
                        MediaType.Anime -> "TV"
                        else -> entry.type.localizedLabel()
                    }
                    MetadataIconText(icon = formatIcon, text = formatText, tint = accentColor)

                    // 2. Episodes / Chapters count
                    val countIcon = if (entry.type == MediaType.Manga) Icons.MenuBook else Icons.Schedule
                    val totalVal = entry.total
                    val countText = if (totalVal != null && totalVal > 0) {
                        if (entry.type == MediaType.Manga) stringResource(R.string.hero_count_volumes_chapters, totalVal)
                        else stringResource(R.string.hero_count_episodes, totalVal)
                    } else {
                        if (entry.type == MediaType.Manga) stringResource(R.string.hero_count_no_volumes)
                        else stringResource(R.string.hero_count_no_episodes)
                    }
                    MetadataIconText(icon = countIcon, text = countText, tint = accentColor)

                    // 3. Status
                    val statusIcon = Icons.RssFeed
                    MetadataIconText(icon = statusIcon, text = entry.status.localizedLabel(), tint = statusColor)
                }

                val airingText = rememberAiringCountdownText(nextAiring)
                if (airingText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Schedule,
                            contentDescription = stringResource(R.string.cd_airing_soon),
                            tint = KitsugiColors.AccentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.hero_airing_soon, airingText),
                            color = KitsugiColors.AccentOrange,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailSourcePill(
                        source = entry.source,
                        color = if (entry.source.equals("tmdb", ignoreCase = true)) Color(0xFFFFB800) else accentColor
                    )

                    if (entry.year != null) {
                        DetailPill(
                            text = entry.year.toString(),
                            color = KitsugiColors.TextSecondary
                        )
                    }

                    if (entry.isFavorite) {
                        DetailPill(
                            text = "★ Favori",
                            color = accentColor
                        )
                    }

                    if (entry.isAdult) {
                        DetailPill(
                            text = "+18",
                            color = KitsugiColors.AccentRed
                        )
                    }
                }


            }
        }
    }
}



private fun statusColor(status: WatchStatus): Color {
    return when (status) {
        WatchStatus.Watching   -> KitsugiColors.AccentBlue
        WatchStatus.Completed  -> KitsugiColors.AccentGreen
        WatchStatus.Planned    -> KitsugiColors.AccentOrange
        WatchStatus.Dropped    -> KitsugiColors.AccentRed
        WatchStatus.Paused     -> KitsugiColors.AccentPurple
        WatchStatus.Repeating  -> KitsugiColors.AccentBlue
    }
}

private fun mediaTypeLabel(entry: MediaEntry): String {
    return when (entry.type) {
        MediaType.Anime -> "ANIME"
        MediaType.Manga -> "MANGA"
        MediaType.Movie -> "FİLM"
        MediaType.TvShow -> "DİZİ"
    }
}

@Composable
private fun MetadataIconText(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
