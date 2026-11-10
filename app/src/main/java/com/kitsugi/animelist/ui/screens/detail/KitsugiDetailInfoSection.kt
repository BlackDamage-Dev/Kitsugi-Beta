package com.kitsugi.animelist.ui.screens.detail

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.data.remote.KitsugiTag
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiMarkdownText
import com.kitsugi.animelist.ui.components.KitsugiOriginalToggleButton
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.ui.components.KitsugiDetailActionButton
import com.kitsugi.animelist.ui.components.UiverseButtonPalette
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.data.local.MangaMappingEntity
import coil3.compose.AsyncImage
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayScore
import com.kitsugi.animelist.utils.toTurkishGenre
import com.kitsugi.animelist.utils.toEnglishGenreForSearch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.toLocalizedOrgName
import com.kitsugi.animelist.utils.toLocalizedMediaTypeString
import com.kitsugi.animelist.utils.NextAiringFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickActions(
    entry: MediaEntry,
    externalUrl: String?,
    onIncrementProgressClick: () -> Unit,
    onToggleFavoriteClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onOpenExternalClick: () -> Unit,
    onWatchClick: (() -> Unit)? = null,
    onReadClick: (() -> Unit)? = null,
    mangaMapping: MangaMappingEntity? = null,
    onLinkMangaClick: (() -> Unit)? = null,
    onUnlinkMangaClick: (() -> Unit)? = null,
    primaryFocusRequester: FocusRequester? = null,
    tabBarFocusRequester: FocusRequester? = null
) {
    val isWatchable = entry.type == MediaType.Anime || entry.type == MediaType.TvShow || entry.type == MediaType.Movie

    // Anime, Dizi ve Film ise İzle butonunu öne çıkar
    if (isWatchable && onWatchClick != null) {
        KitsugiDetailActionButton(
            text = stringResource(R.string.detail_watch),
            icon = Icons.PlayArrow,
            onClick = onWatchClick,
            palette = UiverseButtonPalette.Default,
            modifier = Modifier
                .then(if (primaryFocusRequester != null) Modifier.focusRequester(primaryFocusRequester) else Modifier)
                .then(if (tabBarFocusRequester != null) Modifier.focusProperties { right = tabBarFocusRequester } else Modifier)
        )
        Spacer(modifier = Modifier.height(6.dp))
    }

    // Manga ise Oku butonunu öne çıkar
    if (entry.type == MediaType.Manga && onReadClick != null) {
        KitsugiDetailActionButton(
            text = stringResource(R.string.oku),
            icon = Icons.AutoStories,
            onClick = onReadClick,
            palette = UiverseButtonPalette.Default,
            modifier = Modifier
                .then(if (primaryFocusRequester != null) Modifier.focusRequester(primaryFocusRequester) else Modifier)
                .then(if (tabBarFocusRequester != null) Modifier.focusProperties { right = tabBarFocusRequester } else Modifier)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (mangaMapping != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!mangaMapping.mangaThumbnail.isNullOrBlank()) {
                        com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                            model = mangaMapping.mangaThumbnail,
                            contentDescription = null,
                            isAdult = entry.isAdult,
                            modifier = Modifier
                                .width(40.dp)
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.detail_match_manga),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = mangaMapping.mangaTitle,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Kaynak: ${mangaMapping.mangaSource}",
                            color = LocalKitsugiAccent.current,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(KitsugiColors.AccentRed.copy(alpha = 0.16f))
                            .tvClickable(shape = RoundedCornerShape(14.dp)) { onUnlinkMangaClick?.invoke() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.detail_disconnect),
                            color = KitsugiColors.AccentRed,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(KitsugiColors.SurfaceSoft)
                            .tvClickable(shape = RoundedCornerShape(14.dp)) { onLinkMangaClick?.invoke() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.detail_rematch),
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(KitsugiColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.detail_manga_unmatched),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    val btnModifier = if (tabBarFocusRequester != null) Modifier.focusProperties { right = tabBarFocusRequester } else Modifier
    val firstBtnModifier = btnModifier.then(
        if (primaryFocusRequester != null && !isWatchable && entry.type != MediaType.Manga) {
            Modifier.focusRequester(primaryFocusRequester)
        } else Modifier
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionButton(
            text = "+1 ${progressUnit(entry)}",
            primary = true,
            modifier = firstBtnModifier,
            onClick = onIncrementProgressClick
        )

        ActionButton(
            text = if (entry.isFavorite) stringResource(R.string.action_favourite_remove) else stringResource(R.string.action_favourite_add),
            primary = false,
            modifier = btnModifier,
            onClick = onToggleFavoriteClick
        )

        ActionButton(
            text = stringResource(R.string.duzenle),
            primary = false,
            modifier = btnModifier,
            onClick = onEditClick
        )

        if (externalUrl != null) {
            ActionButton(
                text = stringResource(R.string.kaynakta_ac),
                primary = false,
                modifier = btnModifier,
                onClick = onOpenExternalClick
            )
        }

        ActionButton(
            text = stringResource(R.string.action_delete),
            primary = false,
            danger = true,
            modifier = btnModifier,
            onClick = onDeleteClick
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    primary: Boolean,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    val backgroundColor = when {
        danger -> KitsugiColors.AccentRed.copy(alpha = 0.16f)
        primary -> accentColor
        else -> KitsugiColors.Surface
    }

    val textColor = when {
        danger -> KitsugiColors.AccentRed
        primary -> KitsugiColors.Background
        else -> accentColor
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .tvClickable(shape = RoundedCornerShape(999.dp), onClick = onClick)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MainStatsGrid(
    entry: MediaEntry,
    scoreFormat: String,
    hideScores: Boolean
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 3
    ) {
        MiniStatCard(
            label = stringResource(R.string.detail_stat_score),
            value = entry.getDisplayScore(scoreFormat, hideScores),
            modifier = Modifier.weight(1f)
        )

        MiniStatCard(
            label = stringResource(R.string.ilerleme),
            value = entryProgressText(entry),
            modifier = Modifier.weight(1f)
        )

        if (entry.malId != null) {
            MiniStatCard(
                label = "ID",
                value = entry.malId.toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MiniStatCard(
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

@Composable
internal fun DetailOverviewStatsCard(
    detail: KitsugiMediaDetail
) {
    val rank = detail.rank
    val scoredBy = detail.scoredBy
    val members = detail.members ?: detail.popularity
    val popularityRank = detail.popularityRank
    val accent = LocalKitsugiAccent.current
    val items = buildList {
        rank?.let { add(Triple(stringResource(R.string.detail_stat_rank), "#$it", accent)) }
        scoredBy?.let { add(Triple(stringResource(R.string.detail_stat_votes), formatCompactNumber(it), KitsugiColors.AccentGreen)) }
        members?.let { add(Triple(stringResource(R.string.detail_stat_members), formatCompactNumber(it), KitsugiColors.AccentBlue)) }
        popularityRank?.let { add(Triple(stringResource(R.string.detail_stat_popularity), "#$it", KitsugiColors.AccentOrange)) }
    }

    // Shikimori gibi sağlayıcılar sıralama vermeyip yalnızca oy/üye sayısı verebilir.
    // Bilinmeyen değerlerle boş kutular üretmek yerine yalnızca gerçek istatistikleri göster.
    if (items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.istatistikler),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (label, value, color) ->
                StatOverviewItem(
                    label = label,
                    value = value,
                    accentColor = color,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatOverviewItem(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = accentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private fun formatCompactNumber(number: Int): String {
    val javaLocale = java.util.Locale("tr", "TR")
    val formatter = java.text.NumberFormat.getInstance(javaLocale)
    return formatter.format(number)
}

@Composable
internal fun DateInfoCard(
    entry: MediaEntry
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Tarihler",
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DateMiniBox(
                label = stringResource(R.string.detail_date_start),
                value = entry.startDate ?: stringResource(R.string.label_unknown),
                modifier = Modifier.weight(1f)
            )

            DateMiniBox(
                label = stringResource(R.string.detail_date_end),
                value = entry.endDate ?: stringResource(R.string.label_unknown),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DateMiniBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(12.dp)
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = value,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun DetailSynopsisCard(
    synopsisState: SynopsisState,
    originalText: String? = null,
    onTranslateClick: ((String) -> Unit)? = null,
    onCopyClick: ((String) -> Unit)? = null,
    onImageGalleryRequest: ((urls: List<String>, index: Int) -> Unit)? = null,
    title: String? = null,
    /** Uygulama içi çeviri var mı (orijinal/çeviri geçiş düğmesi için). */
    hasTranslation: Boolean = false,
    /** Şu an orijinal metin gösteriliyor mu. Metni çağıran taraf seçer; bu değer yalnızca düğme etiketini belirler. */
    showingOriginal: Boolean = false,
    onToggleOriginal: (() -> Unit)? = null
) {
    val resolvedTitle = title ?: stringResource(R.string.inline_description_6f80065)
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
                text = resolvedTitle,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            val accent = LocalKitsugiAccent.current
            val displayedText = (synopsisState as? SynopsisState.Success)?.text

            // Orijinal / çeviri geçişi (çeviri varken)
            if (hasTranslation && onToggleOriginal != null) {
                KitsugiOriginalToggleButton(
                    showingOriginal = showingOriginal,
                    onClick = onToggleOriginal,
                    tint = accent
                )
            }

            // Translate button — otomatik çeviri sürerken galeri butonuyla aynı yüklenme animasyonu
            if (onTranslateClick != null && !originalText.isNullOrBlank()) {
                KitsugiTranslateIconButton(
                    onClick = { onTranslateClick(originalText) },
                    tint = accent
                )
            }

            // Copy button
            if (onCopyClick != null && !displayedText.isNullOrBlank()) {
                IconButton(onClick = { onCopyClick(displayedText) }) {
                    Icon(
                        imageVector = Icons.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = KitsugiColors.TextSecondary
                    )
                }
            }
        }

        if (synopsisState is SynopsisState.Success) {
            val text = synopsisState.text
            val isLongText = text.length > 200 || text.count { it == '\n' } >= 4
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
                        text = text,
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
        } else if (synopsisState is SynopsisState.Loading) {
            DetailSynopsisLoadingSkeleton()
        } else if (synopsisState is SynopsisState.Error) {
            Text(
                text = stringResource(R.string.detail_synopsis_error),
                color = KitsugiColors.AccentRed,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun DetailExternalLinkCard(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryGenresChipRow(
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
internal fun EntryInfoSection(
    detail: KitsugiMediaDetail,
    mediaType: MediaType,
    fallbackYear: Int? = null,
    onSearchQuery: (String) -> Unit = {},
    onSeasonClick: (com.kitsugi.animelist.ui.screens.search.DetailSeasonMetadata) -> Unit = {}
) {
    // Kayıt arama sonucunda genel "Anime" olarak işaretlenmiş olsa bile detaydaki
    // gerçek tür (Movie/TvShow/Manga) bilgi satırlarının görünürlüğünü belirler.
    val effectiveMediaType = detail.type ?: mediaType
    val isManga = effectiveMediaType == MediaType.Manga
    val seasonMetadata = com.kitsugi.animelist.ui.screens.search.resolveDetailSeasonMetadata(
        rawSeason = detail.rawSeason ?: detail.season,
        displayedSeason = detail.season,
        seasonYear = detail.seasonYear,
        startDate = detail.startDate,
        fallbackYear = detail.year ?: fallbackYear,
        deriveFromDate = effectiveMediaType == MediaType.Anime
    )
    val nextAiring = NextAiringFormat.parse(detail.nextAiringEpisode)
    val nextAiringText = when {
        nextAiring.isMachineFormat -> {
            val episode = nextAiring.episode ?: NextAiringFormat.UNKNOWN_EPISODE
            val date = NextAiringFormat.formatDate(nextAiring.epoch!!)
            if (episode > 0) {
                stringResource(R.string.detail_info_next_episode_value, episode, date)
            } else {
                date
            }
        }
        else -> nextAiring.legacyText
    }
    val knownTitles = listOfNotNull(
        detail.title, detail.titleEnglish, detail.titleJapanese,
        detail.titleRomaji, detail.titleRussian
    ).map { it.trim().lowercase(Locale.ROOT) }.toSet()
    val synonymNames = cleanDetailSynonyms(detail.synonyms)
        .filterNot { it.lowercase(Locale.ROOT) in knownTitles }

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(KitsugiColors.Surface)
        ) {
            Text(
                text = stringResource(R.string.detail_info_header),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            val aliasesLabel = stringResource(R.string.detail_info_aliases)
            val nameRows = mutableMapOf<String, List<String>>()
            val rows = buildList {
                fun addNames(label: String, values: List<String>) {
                    if (values.isEmpty()) return
                    add(label to values.joinToString(", "))
                    nameRows[label] = values
                }

                if (!detail.status.isNullOrBlank()) add(stringResource(R.string.detail_info_status) to detail.status)
                seasonMetadata.displayLabel?.let { add(stringResource(R.string.detail_info_season) to it) }
                if (!detail.format.isNullOrBlank()) {
                    add(stringResource(R.string.detail_info_format) to detail.format.toLocalizedMediaTypeString())
                }
                if (!detail.startDate.isNullOrBlank()) add(stringResource(R.string.detail_date_start) to detail.startDate)
                if (!detail.endDate.isNullOrBlank()) add(stringResource(R.string.detail_date_end) to detail.endDate)
                if (!nextAiringText.isNullOrBlank()) {
                    add(stringResource(R.string.detail_info_next_episode) to nextAiringText)
                }
                if (!detail.sourceMaterial.isNullOrBlank()) add(stringResource(R.string.detail_info_source) to detail.sourceMaterial)

                if (isManga) {
                    detail.total?.let { add(stringResource(R.string.detail_info_chapters) to it.toString()) }
                    detail.volumes?.let { add(stringResource(R.string.detail_info_volumes) to it.toString()) }
                } else {
                    detail.total?.let { add(stringResource(R.string.detail_info_episodes) to it.toString()) }
                    detail.episodesAired?.let { aired ->
                        // Tamamlanmış yapımda toplam sayının aynısını ikinci kez gösterme.
                        if (detail.total == null || aired != detail.total) {
                            val value = detail.total?.let { total -> "$aired / $total" } ?: aired.toString()
                            add(stringResource(R.string.detail_info_episodes_aired) to value)
                        }
                    }
                    if (detail.studios.isNotEmpty()) {
                        add(stringResource(R.string.detail_info_studio) to detail.studios.joinToString(", ") { it.name.toLocalizedOrgName() })
                    }
                    if (!detail.episodeDuration.isNullOrBlank()) add(stringResource(R.string.detail_info_duration) to detail.episodeDuration)
                    if (!detail.broadcast.isNullOrBlank()) add(stringResource(R.string.detail_info_broadcast) to detail.broadcast)
                    if (!detail.rating.isNullOrBlank()) add(stringResource(R.string.detail_info_rating) to detail.rating)
                }

                addNames(stringResource(R.string.detail_section_publishers), detail.publishers.orEmpty())
                addNames(stringResource(R.string.detail_info_licensors), detail.licensors.orEmpty())

                if (!detail.titleEnglish.isNullOrBlank()) add(stringResource(R.string.detail_info_english) to detail.titleEnglish)
                if (!detail.titleJapanese.isNullOrBlank()) add(stringResource(R.string.detail_info_japanese) to detail.titleJapanese)
                if (!detail.titleRomaji.isNullOrBlank() &&
                    !detail.titleRomaji.trim().equals(detail.title?.trim(), ignoreCase = true)
                ) {
                    add(stringResource(R.string.detail_info_romaji) to detail.titleRomaji)
                }
                if (!detail.titleRussian.isNullOrBlank()) add(stringResource(R.string.detail_info_russian) to detail.titleRussian)
                addNames(aliasesLabel, synonymNames)
                addNames(stringResource(R.string.detail_info_fansubbers), detail.fansubbers.orEmpty())
                addNames(stringResource(R.string.detail_info_fandubbers), detail.fandubbers.orEmpty())
            }
            rows.forEachIndexed { index, (label, value) ->
                val onValueClick: (() -> Unit)? = if (
                    label == stringResource(R.string.detail_info_season) && seasonMetadata.apiSeason != null && seasonMetadata.year != null
                ) {
                    { onSeasonClick(seasonMetadata) }
                } else {
                    null
                }
                DetailInfoValueRow(
                    label = label,
                    value = value,
                    names = nameRows[label].orEmpty(),
                    onValueClick = onValueClick
                )
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = KitsugiColors.Background.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
