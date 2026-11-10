package com.kitsugi.animelist.ui.screens.detail

import androidx.compose.ui.graphics.vector.ImageVector
import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import com.kitsugi.animelist.ui.components.animateDetailContentSize
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.MdbListQuota
import com.kitsugi.animelist.data.remote.MdbListQuotaCenter
import com.kitsugi.animelist.data.remote.MdbListRatings
import com.kitsugi.animelist.utils.toTurkishGenre
import com.kitsugi.animelist.utils.toEnglishGenreForSearch
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.data.remote.KitsugiTag
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.KitsugiMarkdownText
import com.kitsugi.animelist.ui.components.KitsugiOriginalToggleButton
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayScore
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.toLocalizedOrgName

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitsugi.animelist.utils.copyOnDoubleTap

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApiHero(
    result: JikanSearchResult,
    alreadyInList: Boolean,
    onBackClick: () -> Unit,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    logoUrl: String? = null
) {
    val accentColor = LocalKitsugiAccent.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(470.dp)
    ) {
        if (!result.imageUrl.isNullOrBlank()) {
            // +18 içeriklerde her koşulda bulanıklık
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = result.imageUrl,
                contentDescription = result.getDisplayTitle(titleLanguage),
                isAdult = result.isAdult,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accentColor.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = result.getDisplayTitle(titleLanguage).take(2).uppercase(),
                    color = accentColor,
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

        TextButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.geri),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            // Logo veya metin başlık
            val context = LocalContext.current
            val titleText = result.getDisplayTitle(titleLanguage)
            val copyTitleGesture = Modifier.copyOnDoubleTap(context, titleText)

            if (!logoUrl.isNullOrBlank()) {
                var logoFailed by remember(logoUrl) { mutableStateOf(false) }
                if (!logoFailed) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = titleText,
                        modifier = Modifier
                            .height(75.dp)
                            .fillMaxWidth(0.85f)
                            .then(copyTitleGesture),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        onError = { logoFailed = true }
                    )
                } else {
                    Text(
                        text = titleText,
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
                    text = titleText,
                    modifier = copyTitleGesture,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApiDetailPill(
                    text = when (result.type) {
                        MediaType.Anime -> "ANIME"
                        MediaType.Manga -> stringResource(R.string.media_type_manga).uppercase()
                        MediaType.Movie -> stringResource(R.string.media_type_movie).uppercase()
                        MediaType.TvShow -> stringResource(R.string.media_type_tv).uppercase()
                    },
                    color = accentColor
                )

                DetailSourcePill(
                    source = result.source,
                    color = if (result.source.equals("tmdb", ignoreCase = true)) Color(0xFFFFB800) else accentColor
                )

                if (result.year != null) {
                    ApiDetailPill(
                        text = result.year.toString(),
                        color = KitsugiColors.TextSecondary
                    )
                }

                if (!hideScores) {
                    ApiDetailPill(
                        text = result.getDisplayScore(scoreFormat, hideScores),
                        color = accentColor
                    )
                }

                if (alreadyInList) {
                    ApiDetailPill(
                        text = "Listede",
                        color = KitsugiColors.AccentGreen
                    )
                }

                if (result.isAdult) {
                    ApiDetailPill(
                        text = "+18",
                        color = KitsugiColors.AccentRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = result.subtitle,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApiStatsGrid(
    result: JikanSearchResult,
    scoreFormat: String,
    hideScores: Boolean
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 3
    ) {
        ApiMiniStatCard(
            label = stringResource(R.string.gallery_field_type),
            value = when (result.type) {
                MediaType.Anime -> stringResource(R.string.media_type_anime).uppercase()
                MediaType.Manga -> stringResource(R.string.media_type_manga).uppercase()
                MediaType.Movie -> stringResource(R.string.media_type_movie).uppercase()
                MediaType.TvShow -> stringResource(R.string.media_type_tv).uppercase()
            },
            modifier = Modifier.weight(1f)
        )

        ApiMiniStatCard(
            label = stringResource(R.string.detail_info_source),
            value = result.source.toFriendlySourceLabel().uppercase(),
            modifier = Modifier.weight(1f)
        )

        ApiMiniStatCard(
            label = "ID",
            value = result.malId.toString(),
            modifier = Modifier.weight(1f)
        )

        ApiMiniStatCard(
            label = stringResource(R.string.detail_info_year),
            value = result.year?.toString() ?: stringResource(R.string.label_unknown),
            modifier = Modifier.weight(1f)
        )

        ApiMiniStatCard(
            label = stringResource(R.string.toplam),
            value = result.total?.toString() ?: stringResource(R.string.label_unknown),
            modifier = Modifier.weight(1f)
        )

        ApiMiniStatCard(
            label = "Skor",
            value = if (hideScores) stringResource(R.string.label_unknown) else result.getDisplayScore(scoreFormat, hideScores),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ApiMiniStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(KitsugiColors.Surface)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = accentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Açıklama iskeleti yalnızca gösterilecek metin henüz yokken kullanılır.
 * Otomatik çeviri yavaşlasa bile kaynaktan gelen ham metin ekranda kalır.
 */
internal fun shouldShowApiSynopsisSkeleton(synopsis: String?, isDetailLoading: Boolean): Boolean =
    synopsis.isNullOrBlank() && isDetailLoading

@Composable
internal fun ApiSynopsisCard(
    synopsis: String?,
    isLoading: Boolean,
    onTranslateClick: (() -> Unit)? = null,
    onCopyClick: (() -> Unit)? = null,
    onImageGalleryRequest: ((urls: List<String>, index: Int) -> Unit)? = null,
    /** Uygulama içi çeviri var mı (orijinal/çeviri geçiş düğmesi için). */
    hasTranslation: Boolean = false,
    /** Şu an orijinal metin gösteriliyor mu (yalnızca düğme etiketi; metni çağıran taraf seçer). */
    showingOriginal: Boolean = false,
    onToggleOriginal: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.inline_description_6f80065),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Orijinal / çeviri geçişi (çeviri varken)
            if (hasTranslation && onToggleOriginal != null) {
                KitsugiOriginalToggleButton(
                    showingOriginal = showingOriginal,
                    onClick = onToggleOriginal,
                    tint = LocalKitsugiAccent.current
                )
            }

            // Translate button — otomatik çeviri sürerken galeri butonuyla aynı yüklenme animasyonu
            if (onTranslateClick != null && !synopsis.isNullOrBlank()) {
                KitsugiTranslateIconButton(
                    onClick = onTranslateClick,
                    tint = LocalKitsugiAccent.current
                )
            }

            // Copy button
            if (onCopyClick != null && !synopsis.isNullOrBlank()) {
                IconButton(onClick = onCopyClick) {
                    Icon(
                        imageVector = Icons.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = KitsugiColors.TextSecondary
                    )
                }
            }
        }

        if (shouldShowApiSynopsisSkeleton(synopsis, isLoading)) {
            DetailSynopsisLoadingSkeleton()
        } else if (synopsis.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.detail_synopsis_none),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            val isLongText = synopsis.length > 200 || synopsis.count { it == '\n' } >= 4
            var isExpanded by remember { mutableStateOf(false) }
            val surfaceColor = KitsugiColors.Surface

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateDetailContentSize()
                    .then(if (isLongText && !isExpanded) Modifier.heightIn(max = 110.dp) else Modifier)
            ) {
                SelectionContainer {
                    KitsugiMarkdownText(
                        text = synopsis,
                        modifier = Modifier.fillMaxWidth(),
                        onImageGalleryRequest = onImageGalleryRequest
                    )
                }

                if (isLongText && !isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, surfaceColor)
                                )
                            )
                    )
                }
            }

            if (isLongText) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .tvClickable(shape = RoundedCornerShape(8.dp)) { isExpanded = !isExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.VisibilityOff else Icons.Visibility,
                        contentDescription = null,
                        tint = LocalKitsugiAccent.current,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExpanded) stringResource(R.string.action_see_less) else stringResource(R.string.action_see_more),
                        color = LocalKitsugiAccent.current,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun ApiTrailerCard(
    trailerUrl: String
) {
    val uriHandler = LocalUriHandler.current
    val accentColor = LocalKitsugiAccent.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .tvClickable(shape = RoundedCornerShape(22.dp), onClick = {
                runCatching { uriHandler.openUri(trailerUrl) }
            })
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Fragman",
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.detail_trailer_tap),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Play,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApiGenresChipRow(
    genres: List<String>,
    tags: List<KitsugiTag> = emptyList(),
    onGenreClick: (String) -> Unit = {}
) {
    KitsugiGenresCard(
        genres = genres,
        tags = tags,
        onGenreClick = onGenreClick
    )
}

@Composable
internal fun ApiInfoSection(
    detail: KitsugiMediaDetail,
    mediaType: MediaType,
    fallbackYear: Int? = null,
    onSearchQuery: (String) -> Unit = {},
    onSeasonClick: (com.kitsugi.animelist.ui.screens.search.DetailSeasonMetadata) -> Unit = {}
) {
    // Arama sonucu detayında ve kütüphane detayında aynı alanlar gösterilir.
    // İki kartın farklı kurallarla çatallanmasını önlemek için ortak bileşen kullanılır.
    EntryInfoSection(
        detail = detail,
        mediaType = mediaType,
        fallbackYear = fallbackYear,
        onSearchQuery = onSearchQuery,
        onSeasonClick = onSeasonClick
    )
}

@Composable
internal fun ApiThemesList(
    openings: List<com.kitsugi.animelist.data.remote.KitsugiTheme>,
    endings: List<com.kitsugi.animelist.data.remote.KitsugiTheme>
) {
    val accentColor = LocalKitsugiAccent.current
    val uriHandler = LocalUriHandler.current

    val INITIAL_COUNT = 3
    var openingsExpanded by remember { mutableStateOf(false) }
    var endingsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .animateDetailContentSize()
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (openings.isNotEmpty()) {
            val visibleOpenings = if (openingsExpanded) openings else openings.take(INITIAL_COUNT)
            val hasMoreOpenings = openings.size > INITIAL_COUNT

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.detail_openings),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (hasMoreOpenings) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .tvClickable(shape = RoundedCornerShape(8.dp)) { openingsExpanded = !openingsExpanded }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (openingsExpanded) stringResource(R.string.daha_az) else stringResource(R.string.x_daha, openings.size - INITIAL_COUNT),
                            color = accentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        KitsugiChevronIcon(
                            expanded = openingsExpanded,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            visibleOpenings.forEach { theme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .tvClickable(shape = RoundedCornerShape(8.dp)) {
                            if (!theme.videoUrl.isNullOrBlank()) {
                                runCatching { uriHandler.openUri(theme.videoUrl) }
                            } else {
                                val query = java.net.URLEncoder.encode(theme.label, "UTF-8")
                                runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (!theme.videoUrl.isNullOrBlank()) Icons.Play else Icons.Search,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = theme.label,
                        color = accentColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (endings.isNotEmpty()) {
            if (openings.isNotEmpty()) {
                HorizontalDivider(color = KitsugiColors.Background.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
            val visibleEndings = if (endingsExpanded) endings else endings.take(INITIAL_COUNT)
            val hasMoreEndings = endings.size > INITIAL_COUNT

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.detail_endings),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (hasMoreEndings) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .tvClickable(shape = RoundedCornerShape(8.dp)) { endingsExpanded = !endingsExpanded }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (endingsExpanded) stringResource(R.string.daha_az) else stringResource(R.string.x_daha, endings.size - INITIAL_COUNT),
                            color = accentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        KitsugiChevronIcon(
                            expanded = endingsExpanded,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            visibleEndings.forEach { theme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .tvClickable(shape = RoundedCornerShape(8.dp)) {
                            if (!theme.videoUrl.isNullOrBlank()) {
                                runCatching { uriHandler.openUri(theme.videoUrl) }
                            } else {
                                val query = java.net.URLEncoder.encode(theme.label, "UTF-8")
                                runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (!theme.videoUrl.isNullOrBlank()) Icons.Play else Icons.Search,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = theme.label,
                        color = accentColor.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ApiExternalLinkCard(
    externalUrl: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.detail_external_link),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = externalUrl,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
internal fun ApiActionButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    val backgroundColor = when {
        primary && enabled -> accentColor
        primary && !enabled -> KitsugiColors.AccentGreen
        else -> KitsugiColors.Surface
    }

    val textColor = when {
        primary -> KitsugiColors.Background
        else -> accentColor
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .tvClickable(
                enabled = enabled,
                shape = RoundedCornerShape(999.dp),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ApiDetailPill(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Harici Puanlar (MDBList) ────────────────────────────────────────────────

/**
 * Harici bir puan kaynağının gösterim modeli (IMDb, Rotten Tomatoes, Metacritic,
 * Letterboxd, TMDB, Trakt).
 */
internal data class MdbRatingItem(
    /** Marka ikonu (HD vektör) — emoji yerine. */
    val icon: ImageVector,
    val label: String,
    val value: String,
    val accent: Color,
    val onClick: (() -> Unit)?
)

// Marka renkleri, koyu tema üzerinde okunacak parlaklıkta seçildi: eski sürümdeki
// tam doygun arka planlar yerine bu renkler yalnızca metin + hafif zemin tonu olarak
// kullanılır (bkz. [MdbRatingBadge]).
private val MdbImdbAccent = Color(0xFFF5C518)
private val MdbTomatoAccent = Color(0xFFFF4B32)
private val MdbAudienceAccent = Color(0xFFFF9500)
private val MdbLetterboxdAccent = Color(0xFF40BCF4)
private val MdbTmdbAccent = Color(0xFF01D277)
private val MdbTraktAccent = Color(0xFFED1C24)

/** Metacritic'in kendi eşikleri: 61+ yeşil, 40–60 sarı, altı kırmızı. */
private fun mdbMetacriticAccent(score: Int): Color = when {
    score >= 61 -> Color(0xFF66CC33)
    score >= 40 -> Color(0xFFFFCC33)
    else -> Color(0xFFFF4D4D)
}

/**
 * Seçili kaynaklardan gösterilecek puanları üretir.
 *
 * Yalnızca geçerli (0'dan büyük) puanlar listeye girer; MDBList bazı kayıtlar için
 * "puan yok" anlamında 0 döndürür ve ekranda "0 Metacritic" gibi anlamsız kutular
 * görünmesi istenmez.
 */
private fun buildMdbRatingItems(
    ratings: MdbListRatings?,
    mediaTitle: String,
    uriHandler: UriHandler,
    showImdb: Boolean,
    showTomatoes: Boolean,
    showMetacritic: Boolean,
    showAudience: Boolean,
    showLetterboxd: Boolean,
    showTmdb: Boolean,
    showTrakt: Boolean
): List<MdbRatingItem> {
    if (ratings == null) return emptyList()
    val imdbId = ratings.imdbId

    fun encodedTitle(): String =
        runCatching { java.net.URLEncoder.encode(mediaTitle, "UTF-8") }.getOrDefault("")

    fun openExternal(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    return buildList {
        val imdbScore = ratings.imdb
        if (showImdb && imdbScore != null && imdbScore > 0.0) {
            add(
                MdbRatingItem(
                    icon = Icons.StarSolid,
                    label = "IMDb",
                    value = String.format("%.1f", imdbScore),
                    accent = MdbImdbAccent,
                    onClick = {
                        val id = imdbId
                        if (!id.isNullOrBlank()) openExternal("https://www.imdb.com/title/$id")
                        else openExternal("https://www.imdb.com/find?q=${encodedTitle()}")
                    }
                )
            )
        }

        val tomatoScore = ratings.tomatoes
        if (showTomatoes && tomatoScore != null && tomatoScore > 0) {
            add(
                MdbRatingItem(
                    icon = Icons.CircleCheck,
                    label = "Tomatometer",
                    value = "$tomatoScore%",
                    accent = MdbTomatoAccent,
                    onClick = { openExternal("https://www.rottentomatoes.com/search?search=${encodedTitle()}") }
                )
            )
        }

        val audienceScore = ratings.tomatoesAudience
        if (showAudience && audienceScore != null && audienceScore > 0) {
            add(
                MdbRatingItem(
                    icon = Icons.Popcorn,
                    label = "Audience",
                    value = "$audienceScore%",
                    accent = MdbAudienceAccent,
                    onClick = { openExternal("https://www.rottentomatoes.com/search?search=${encodedTitle()}") }
                )
            )
        }

        val metacriticScore = ratings.metacritic
        if (showMetacritic && metacriticScore != null && metacriticScore > 0) {
            add(
                MdbRatingItem(
                    icon = Icons.Award,
                    label = "Metacritic",
                    value = "$metacriticScore",
                    accent = mdbMetacriticAccent(metacriticScore),
                    onClick = { openExternal("https://www.metacritic.com/search/${encodedTitle()}/") }
                )
            )
        }

        val letterboxdScore = ratings.letterboxd
        if (showLetterboxd && letterboxdScore != null && letterboxdScore > 0.0) {
            add(
                MdbRatingItem(
                    icon = Icons.Clapperboard,
                    label = "Letterboxd",
                    value = String.format("%.1f", letterboxdScore),
                    accent = MdbLetterboxdAccent,
                    onClick = {
                        val id = imdbId
                        if (!id.isNullOrBlank()) openExternal("https://letterboxd.com/imdb/$id")
                        else openExternal("https://letterboxd.com/search/${encodedTitle()}")
                    }
                )
            )
        }

        val tmdbScore = ratings.tmdb
        if (showTmdb && tmdbScore != null && tmdbScore > 0.0) {
            add(
                MdbRatingItem(
                    icon = Icons.Film,
                    label = "TMDB",
                    value = String.format("%.1f", tmdbScore),
                    accent = MdbTmdbAccent,
                    onClick = { openExternal("https://www.themoviedb.org/search?query=${encodedTitle()}") }
                )
            )
        }

        val traktScore = ratings.trakt
        if (showTrakt && traktScore != null && traktScore > 0.0) {
            add(
                MdbRatingItem(
                    icon = Icons.Tv,
                    label = "Trakt",
                    value = String.format("%.0f%%", traktScore),
                    accent = MdbTraktAccent,
                    onClick = {
                        val id = imdbId
                        if (!id.isNullOrBlank()) openExternal("https://trakt.tv/search/imdb/$id")
                        else openExternal("https://trakt.tv/search?query=${encodedTitle()}")
                    }
                )
            )
        }
    }
}

/**
 * Harici puanları (IMDb, Rotten Tomatoes, Metacritic, Letterboxd, TMDB, Trakt) MDBList
 * API'sinden gelen verilerle gösterir.
 *
 * Tasarım dili uygulamanın geri kalanıyla aynıdır: "İstatistikler" ve "Türler" blokları gibi
 * `Surface` zeminli tek kart; içinde marka rengine hafifçe boyanmış (alpha 0.14) puan
 * yastıkları. Böylece harici puanlar sayfaya "yapıştırılmış" görünmez, tamamen okunur kalır.
 * Yastıklar sabit genişlikte bir şeride değil, ekran genişliğine eşit dağılan bir ızgaraya
 * yerleşir; hiçbir puan yatay kaydırma olmadan kesilmeden görünür.
 */
@Composable
internal fun ApiMdbListRatingCard(
    ratings: MdbListRatings?,
    isLoading: Boolean,
    showImdb: Boolean = true,
    showTomatoes: Boolean = true,
    showMetacritic: Boolean = true,
    showAudience: Boolean = false,
    showLetterboxd: Boolean = false,
    showTmdb: Boolean = false,
    showTrakt: Boolean = false,
    onSettingsClick: (() -> Unit)? = null,
    mediaTitle: String = ""
) {
    val accentColor = LocalKitsugiAccent.current
    val uriHandler = LocalUriHandler.current

    // Kota tek bir merkezden gelir: aynı anahtar için Ayarlar sayfası ve bu kart aynı sayıyı
    // gösterir. Günlük sıfırlanma geçtiyse bayat "0" yerine yenilenmiş değer çizilir.
    val storedQuota by MdbListQuotaCenter.quota.collectAsState()
    val quota = remember(storedQuota) { storedQuota?.rolledOver() }
    val exhaustedQuota = quota?.takeIf { it.isExhausted }
    val context = LocalContext.current

    // Kota dolduğu anda tek seferlik uyarı (ayarlar paneliyle ortak bayrak).
    LaunchedEffect(exhaustedQuota != null, exhaustedQuota?.resetAtEpochSeconds) {
        if (exhaustedQuota != null && MdbListQuotaCenter.consumeExhaustedNotice()) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.mdblist_quota_exhausted_toast),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val ratingItems = buildMdbRatingItems(
        ratings = ratings,
        mediaTitle = mediaTitle,
        uriHandler = uriHandler,
        showImdb = showImdb,
        showTomatoes = showTomatoes,
        showMetacritic = showMetacritic,
        showAudience = showAudience,
        showLetterboxd = showLetterboxd,
        showTmdb = showTmdb,
        showTrakt = showTrakt
    )

    // Yükleniyor değilken gösterilecek puan yoksa kart gizlenir. Tek istisna: günlük kota
    // bittiyse uyarı yine gösterilir; aksi hâlde kullanıcı puanların neden gelmediğini
    // hiçbir yerde göremezdi.
    if (!isLoading && ratingItems.isEmpty() && exhaustedQuota == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Başlık ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.detail_external_ratings),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(accentColor.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "MDBList",
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }

                // Kalan günlük kota: puanların neden gelmediğini tek bakışta anlatır.
                if (quota != null && (quota.isKnown || quota.isExhausted)) {
                    MdbQuotaBadge(quota = quota)
                }

                if (onSettingsClick != null) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Settings,
                            contentDescription = stringResource(R.string.ayarlar),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (exhaustedQuota != null) {
            MdbQuotaExhaustedNotice(quota = exhaustedQuota)
        }

        // ── Gövde ────────────────────────────────────────────────────────────
        if (isLoading) {
            DetailRatingBadgesLoadingSkeleton()
        } else if (ratingItems.isNotEmpty()) {
            MdbRatingGrid(items = ratingItems)
        }
    }
}

/** Kota sayılarını yerel binlik ayırıcısıyla yazar (1.000 / 25.000). */
private fun formatMdbQuotaCount(value: Int): String =
    java.text.NumberFormat.getIntegerInstance().format(value)

/** Sıfırlanma anını kullanıcının yerel saatinde kısa biçimde yazar. */
private fun formatMdbQuotaReset(epochSeconds: Long?): String? {
    val epoch = epochSeconds?.takeIf { it > 0L } ?: return null
    return runCatching {
        java.time.Instant.ofEpochSecond(epoch)
            .atZone(java.time.ZoneId.systemDefault())
            .format(
                java.time.format.DateTimeFormatter.ofPattern(
                    "d MMM HH:mm",
                    java.util.Locale.getDefault()
                )
            )
    }.getOrNull()
}

/**
 * Kart başlığındaki küçük kota rozeti.
 *
 * - Normal: "998/1.000" (nötr renk)
 * - Yüzde 10 ve altı: turuncu
 * - Tükendi: kırmızı "Kota doldu"
 *
 * Limit API'den geldiği için ücretsiz ve ücretli hesaplar aynı rozetle çalışır.
 */
@Composable
private fun MdbQuotaBadge(quota: MdbListQuota) {
    val exhausted = quota.isExhausted
    val color = when {
        exhausted -> KitsugiColors.AccentRed
        quota.isLow -> KitsugiColors.AccentOrange
        else -> KitsugiColors.TextSecondary
    }
    val label = if (exhausted) {
        stringResource(R.string.mdblist_quota_exhausted_inline)
    } else {
        stringResource(
            R.string.mdblist_quota_remaining_short,
            formatMdbQuotaCount(quota.remaining ?: 0),
            formatMdbQuotaCount(quota.limit ?: 0)
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (exhausted) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black
        )
    }
}

/** Günlük kota bittiğinde kart gövdesinde gösterilen uyarı. */
@Composable
private fun MdbQuotaExhaustedNotice(quota: MdbListQuota) {
    val warningColor = KitsugiColors.AccentRed
    val resetLabel = formatMdbQuotaReset(quota.resetAtEpochSeconds)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(warningColor.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.mdblist_quota_exhausted_title),
                color = warningColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (resetLabel != null) {
                    stringResource(R.string.mdblist_quota_exhausted_desc, resetLabel)
                } else {
                    stringResource(R.string.mdblist_quota_exhausted_desc_unknown)
                },
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Puan yastıklarını satırlara böler. Sütun sayısı öğe sayısına göre seçilir; son satırda
 * eksik kalan hücreler [Spacer] ile doldurulur, böylece hiçbir yastık gereksiz yere
 * genişlemez ve ızgara hizalı kalır.
 */
@Composable
private fun MdbRatingGrid(items: List<MdbRatingItem>) {
    if (items.isEmpty()) return

    val columns = when {
        items.size <= 2 -> 2
        items.size == 3 -> 3
        items.size == 4 -> 4
        items.size == 5 -> 3
        items.size == 6 -> 3
        else -> 4
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    MdbRatingBadge(
                        item = item,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Koyu temada marka rengi olduğu gibi kullanılır. Açık temada (beyaz/açık yüzey) aynı parlak
 * renk okunmaz olacağından renk siyaha doğru koyulaştırılır; skor metni 18sp kalın olduğu
 * için bu oran rahatça okunur kalır.
 */
private fun Color.forRatingSurface(surface: Color): Color =
    if (surface.luminance() > 0.5f) {
        Color(red = red * 0.55f, green = green * 0.55f, blue = blue * 0.55f, alpha = alpha)
    } else {
        this
    }

@Composable
private fun MdbRatingBadge(
    item: MdbRatingItem,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val accent = item.accent.forRatingSurface(KitsugiColors.Surface)

    Column(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .let { base ->
                val click = item.onClick
                if (click != null) base.tvClickable(shape = shape, onClick = click) else base
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Büyük skor değeri — marka renginde
        Text(
            text = item.value,
            color = accent,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        // Emoji + etiket satırı
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = item.label,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp
            )
        }
    }
}
