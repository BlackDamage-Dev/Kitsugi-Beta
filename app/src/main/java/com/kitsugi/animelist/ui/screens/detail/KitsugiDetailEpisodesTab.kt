package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.components.KitsugiDetailReveal
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

// ---------------------------------------------------------------------------
//  Bölümler (Episodes) Tab  –  Accordion Sezon Görünümü
// ---------------------------------------------------------------------------

@Composable
fun EpisodesTabContent(
    state: DetailTabState<List<KitsugiStreamingEpisode>>,
    animeId: String = "",
    isAdult: Boolean = false,
    onEpisodeClick: (episode: KitsugiStreamingEpisode) -> Unit,
    onDownloadClick: (episode: KitsugiStreamingEpisode) -> Unit = {},
    episodeRatings: Map<Pair<Int, Int>, Double> = emptyMap(),
    targetSeason: Int? = null,
    totalSeasons: Int? = null,
    onSeasonSelected: ((Int) -> Unit)? = null,
    onRatingClick: ((season: Int, episode: Int) -> Unit)? = null
) {
    val accentColor = LocalKitsugiAccent.current
    val downloadMapFlow = remember(animeId) {
        com.kitsugi.animelist.data.local.AnimeDownloadManager.downloads
            .map { all ->
                if (animeId.isEmpty()) emptyMap()
                else all.filter { it.animeId == animeId }.associateBy { it.season to it.episode }
            }
            .distinctUntilChanged()
    }
    val downloadMap by downloadMapFlow.collectAsState(initial = emptyMap())

    when (state) {
        is DetailTabState.Loading -> {
            DetailEpisodesLoadingSkeleton(totalSeasons = totalSeasons)
        }
        is DetailTabState.Success -> {
            val episodes = state.data

            // Eğer birden fazla sezon varsa → Accordion görünüm
            if (totalSeasons != null && totalSeasons > 1) {
                SeasonAccordion(
                    episodes = episodes,
                    isAdult = isAdult,
                    totalSeasons = totalSeasons,
                    targetSeason = targetSeason,
                    episodeRatings = episodeRatings,
                    onSeasonSelected = onSeasonSelected,
                    onEpisodeClick = onEpisodeClick,
                    onDownloadClick = onDownloadClick,
                    onRatingClick = onRatingClick,
                    accentColor = accentColor,
                    animeId = animeId,
                    downloadMap = downloadMap
                )
            } else {
                // Tek sezon → Sayfalı/parçalı liste (Doraemon/One Piece gibi 1000+ bölümlü dizilerde donma ve ANR'yi önler)
                PaginatedEpisodesList(
                    episodes = episodes,
                    isAdult = isAdult,
                    targetSeason = targetSeason,
                    episodeRatings = episodeRatings,
                    accentColor = accentColor,
                    downloadMap = downloadMap,
                    onEpisodeClick = onEpisodeClick,
                    onDownloadClick = onDownloadClick,
                    onRatingClick = onRatingClick
                )
            }
        }
        is DetailTabState.Error -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.inline_an_error_occurred_while_loading_6facab0),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                DetailTabErrorCodeChip(platformErrorCode = state.platformErrorCode)
            }
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------------------
//  Accordion: Sezon başlıkları + bölümler
// ---------------------------------------------------------------------------

@Composable
private fun SeasonAccordion(
    episodes: List<KitsugiStreamingEpisode>,
    isAdult: Boolean = false,
    totalSeasons: Int,
    targetSeason: Int?,
    episodeRatings: Map<Pair<Int, Int>, Double>,
    onSeasonSelected: ((Int) -> Unit)?,
    onEpisodeClick: (KitsugiStreamingEpisode) -> Unit,
    onDownloadClick: (KitsugiStreamingEpisode) -> Unit,
    onRatingClick: ((season: Int, episode: Int) -> Unit)?,
    accentColor: Color,
    animeId: String,
    downloadMap: Map<Pair<Int, Int>, com.kitsugi.animelist.data.model.AnimeDownload>
) {
    // Hangi sezon açık? Başlangıçta aktif sezon (targetSeason) açık gelsin
    var expandedSeason by remember(targetSeason) {
        mutableStateOf(targetSeason ?: 1)
    }
    val defaultSeason = targetSeason ?: 1
    val episodesBySeason = remember(episodes, totalSeasons, defaultSeason) {
        val grouped = HashMap<Int, MutableList<KitsugiStreamingEpisode>>(totalSeasons * 2)
        for (ep in episodes) {
            val s = ep.seasonNumber ?: defaultSeason
            grouped.getOrPut(s) { ArrayList() }.add(ep)
        }
        grouped
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (season in 1..totalSeasons) {
            val isExpanded = expandedSeason == season

            key("season_acc_$season") {
                SeasonAccordionItem(
                    seasonNumber = season,
                    isAdult = isAdult,
                    isExpanded = isExpanded,
                    episodes = episodesBySeason[season].orEmpty(),
                    episodeRatings = episodeRatings,
                    targetSeason = season,
                    accentColor = accentColor,
                    onHeaderClick = {
                        // Aynı sezona basılırsa aç/kapat; farklı sezon basılırsa sadece onu aç
                        if (isExpanded) {
                            expandedSeason = -1
                        } else {
                            expandedSeason = season
                            onSeasonSelected?.invoke(season)
                        }
                    },
                    onEpisodeClick = onEpisodeClick,
                    onDownloadClick = onDownloadClick,
                    onRatingClick = onRatingClick,
                    animeId = animeId,
                    downloadMap = downloadMap
                )
            }
        }
    }
}

@Composable
private fun SeasonAccordionItem(
    seasonNumber: Int,
    isAdult: Boolean = false,
    isExpanded: Boolean,
    episodes: List<KitsugiStreamingEpisode>,
    episodeRatings: Map<Pair<Int, Int>, Double>,
    targetSeason: Int,
    accentColor: Color,
    onHeaderClick: () -> Unit,
    onEpisodeClick: (KitsugiStreamingEpisode) -> Unit,
    onDownloadClick: (KitsugiStreamingEpisode) -> Unit,
    onRatingClick: ((season: Int, episode: Int) -> Unit)?,
    animeId: String,
    downloadMap: Map<Pair<Int, Int>, com.kitsugi.animelist.data.model.AnimeDownload>
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "arrowRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isExpanded) accentColor.copy(alpha = 0.08f) else KitsugiColors.Surface)
            .border(
                width = if (isExpanded) 1.5.dp else 0.5.dp,
                color = if (isExpanded) accentColor.copy(alpha = 0.5f) else KitsugiColors.SurfaceStrong,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        // ── Sezon Başlığı (tıklanabilir) ────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(shape = RoundedCornerShape(16.dp), onClick = onHeaderClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sezon numarası rozeti
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isExpanded) accentColor else KitsugiColors.SurfaceStrong)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$seasonNumber",
                        color = if (isExpanded) Color.White else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                }

                Column {
                    Text(
                        text = "Sezon $seasonNumber",
                        color = if (isExpanded) accentColor else KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (episodes.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.inline_1_s_episodes_d91c30c, episodes.size),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.KeyboardArrowDown,
                contentDescription = if (isExpanded) stringResource(R.string.settings_close) else stringResource(R.string.ac),
                tint = if (isExpanded) accentColor else KitsugiColors.TextSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowRotation)
            )
        }

        // ── Bölümler (Animated Expand/Collapse) ─────────────────────────────
        KitsugiDetailReveal(visible = isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                PaginatedEpisodesList(
                    episodes = episodes,
                    isAdult = isAdult,
                    targetSeason = targetSeason,
                    episodeRatings = episodeRatings,
                    accentColor = accentColor,
                    downloadMap = downloadMap,
                    onEpisodeClick = onEpisodeClick,
                    onDownloadClick = onDownloadClick,
                    onRatingClick = onRatingClick
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Sayfalı/Parçalı Bölüm Listesi (Büyük listelerde ANR ve donmayı önler)
// ---------------------------------------------------------------------------

@Composable
private fun PaginatedEpisodesList(
    episodes: List<KitsugiStreamingEpisode>,
    isAdult: Boolean = false,
    targetSeason: Int?,
    episodeRatings: Map<Pair<Int, Int>, Double>,
    accentColor: Color,
    downloadMap: Map<Pair<Int, Int>, com.kitsugi.animelist.data.model.AnimeDownload>,
    onEpisodeClick: (KitsugiStreamingEpisode) -> Unit,
    onDownloadClick: (KitsugiStreamingEpisode) -> Unit,
    onRatingClick: ((season: Int, episode: Int) -> Unit)?
) {
    if (episodes.isEmpty()) {
        EmptyEpisodesMessage()
        return
    }

    val CHUNK_SIZE = 25
    val totalChunks = (episodes.size + CHUNK_SIZE - 1) / CHUNK_SIZE
    var selectedChunkIndex by remember(episodes.size) { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (totalChunks > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(totalChunks) { chunkIdx ->
                    val start = chunkIdx * CHUNK_SIZE + 1
                    val end = minOf((chunkIdx + 1) * CHUNK_SIZE, episodes.size)
                    val isSelected = selectedChunkIndex == chunkIdx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isSelected) accentColor else KitsugiColors.SurfaceStrong,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accentColor else KitsugiColors.SurfaceSoft,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(10.dp)) {
                                selectedChunkIndex = chunkIdx
                            }
                    ) {
                        Text(
                            text = "$start - $end",
                            color = if (isSelected) KitsugiColors.OnAccent else KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        val safeChunkIndex = selectedChunkIndex.coerceIn(0, (totalChunks - 1).coerceAtLeast(0))
        val startIndex = safeChunkIndex * CHUNK_SIZE
        val endIndex = minOf(startIndex + CHUNK_SIZE, episodes.size)
        val visibleEpisodes = if (startIndex < episodes.size) episodes.subList(startIndex, endIndex) else emptyList()
        // PERF: Sezon üst sınırları ve ilk bölüm eşleşme tablosu her satır için (50x) yeniden
        // oluşturulmak yerine yalnızca episodeRatings değiştiğinde O(R) tek geçişte hazırlanır.
        val ratingIndex = remember(episodeRatings) { EpisodeRatingIndex.build(episodeRatings) }

        visibleEpisodes.forEachIndexed { i, episode ->
            val index = startIndex + i
            androidx.compose.runtime.key("${episode.seasonNumber ?: 0}_${episode.episodeNumber ?: index}_$index") {
                val rating = ratingIndex.resolve(episode, targetSeason)
                val seasonKey = targetSeason ?: episode.seasonNumber ?: 1
                val epNum = episode.episodeNumber ?: (index + 1)
                val download = downloadMap[seasonKey to epNum]

                EpisodeRow(
                    episode = episode,
                    index = index,
                    isAdult = isAdult,
                    accentColor = accentColor,
                    imdbRating = rating,
                    download = download,
                    onClick = { onEpisodeClick(episode) },
                    onRatingClick = if (onRatingClick != null && episode.episodeNumber != null) {
                        { onRatingClick(targetSeason ?: episode.seasonNumber ?: 1, episode.episodeNumber) }
                    } else null,
                    onDownloadClick = { onDownloadClick(episode) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Yardımcı: Boş Liste Mesajı
// ---------------------------------------------------------------------------

@Composable
private fun EmptyEpisodesMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.inline_episode_information_not_found_85e5b4d),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.inline_this_source_may_not_provide_d109347),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------------------------------------------------------------------------
//  Yardımcı: Rating çözücü (önbelleklenmiş sezon aralıklarıyla O(S) / O(1))
// ---------------------------------------------------------------------------

private class EpisodeRatingIndex(
    private val ratings: Map<Pair<Int, Int>, Double>,
    private val firstByEpisodeNumber: Map<Int, Double>,
    private val seasonMaxEpisodes: List<Pair<Int, Int>>
) {
    fun resolve(episode: KitsugiStreamingEpisode, targetSeason: Int?): Double? {
        if (ratings.isEmpty()) return null
        val epNum = episode.episodeNumber ?: return null
        val sNum = targetSeason ?: episode.seasonNumber ?: 1

        // 1. Doğrudan sezon numarası ve bölüm numarası eşleşmesi
        val direct = ratings[sNum to epNum] ?: ratings[1 to epNum]
        if (direct != null) return direct

        // 2. Herhangi bir sezonda aynı bölüm numarasının doğrudan bulunması
        val simpleMatch = firstByEpisodeNumber[epNum]
        if (simpleMatch != null) return simpleMatch

        // 3. Mutlak bölüm numarası (Absolute Episode Number) → Sezon İçi Bölüm Hesaplama
        if (epNum > 0 && seasonMaxEpisodes.isNotEmpty()) {
            var cumulative = 0
            for ((season, maxEpInSeason) in seasonMaxEpisodes) {
                if (epNum <= cumulative + maxEpInSeason) {
                    val inSeasonEp = epNum - cumulative
                    return ratings[season to inSeasonEp]
                }
                cumulative += maxEpInSeason
            }
        }
        return null
    }

    companion object {
        fun build(episodeRatings: Map<Pair<Int, Int>, Double>): EpisodeRatingIndex {
            if (episodeRatings.isEmpty()) {
                return EpisodeRatingIndex(emptyMap(), emptyMap(), emptyList())
            }
            val firstByEp = HashMap<Int, Double>(episodeRatings.size)
            val maxBySeason = java.util.TreeMap<Int, Int>()
            for ((key, value) in episodeRatings) {
                val (season, ep) = key
                firstByEp.putIfAbsent(ep, value)
                if (season >= 1) {
                    val prev = maxBySeason[season]
                    if (prev == null || ep > prev) {
                        maxBySeason[season] = ep
                    }
                }
            }
            return EpisodeRatingIndex(
                ratings = episodeRatings,
                firstByEpisodeNumber = firstByEp,
                seasonMaxEpisodes = maxBySeason.entries.map { it.key to it.value }
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Episode Row
// ---------------------------------------------------------------------------

@Composable
private fun EpisodeRow(
    episode: KitsugiStreamingEpisode,
    index: Int,
    isAdult: Boolean = false,
    accentColor: androidx.compose.ui.graphics.Color,
    imdbRating: Double? = null,
    download: com.kitsugi.animelist.data.model.AnimeDownload? = null,
    onClick: () -> Unit,
    onRatingClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null
) {
    val hasThumbnail = !episode.thumbnail.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceStrong)
            .tvClickable(shape = RoundedCornerShape(14.dp), onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail veya bölüm numarası
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            if (hasThumbnail) {
                val epThumbModel = com.kitsugi.animelist.ui.components.GalleryThumbnailImagePolicy
                    .rememberThumbnailImageRequest(
                        url = episode.thumbnail,
                        widthDp = 100.dp,
                        heightDp = 60.dp
                    )
                com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                    model = epThumbModel ?: episode.thumbnail,
                    contentDescription = episode.title,
                    isAdult = isAdult,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KitsugiColors.Background.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    KitsugiArtIcon(art = KitsugiArt.Player, contentDescription = stringResource(R.string.oynat), size = 28.dp)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = accentColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Icon(
                        imageVector = Icons.PlayCircle,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Başlık + site + IMDb rozeti
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = episode.title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!episode.site.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = episode.site,
                            color = accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (imdbRating != null && imdbRating > 0.0) {
                    ImdbEpisodeRatingBadge(rating = imdbRating, onClick = onRatingClick)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Download button/status
            if (download != null) {
                when (download.status) {
                    com.kitsugi.animelist.data.model.AnimeDownload.Status.QUEUE -> {
                        KitsugiArtIcon(art = KitsugiArt.Download, contentDescription = "Kuyrukta", size = 22.dp)
                    }
                    com.kitsugi.animelist.data.model.AnimeDownload.Status.DOWNLOADING -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(24.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { download.progress / 100f },
                                modifier = Modifier.size(24.dp),
                                color = accentColor,
                                strokeWidth = 2.dp,
                                trackColor = KitsugiColors.SurfaceStrong
                            )
                            Text(
                                text = "${download.progress}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                    com.kitsugi.animelist.data.model.AnimeDownload.Status.COMPLETED -> {
                        KitsugiArtIcon(art = KitsugiArt.Download, contentDescription = stringResource(R.string.indirildi), size = 22.dp)
                    }
                    com.kitsugi.animelist.data.model.AnimeDownload.Status.ERROR -> {
                        KitsugiArtIcon(art = KitsugiArt.Warning, contentDescription = stringResource(R.string.hata), size = 22.dp)
                    }
                    com.kitsugi.animelist.data.model.AnimeDownload.Status.PAUSED -> {
                        KitsugiArtIcon(art = KitsugiArt.Pause, contentDescription = stringResource(R.string.duraklatildi), size = 22.dp)
                    }
                }
            } else {
                if (onDownloadClick != null) {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        KitsugiArtIcon(art = KitsugiArt.Download, contentDescription = stringResource(R.string.indir), size = 22.dp)
                    }
                }
            }

            KitsugiArtIcon(art = KitsugiArt.Play, contentDescription = stringResource(R.string.oynat), size = 22.dp)
        }
    }
}

// ---------------------------------------------------------------------------
//  IMDb Rozeti
// ---------------------------------------------------------------------------

@Composable
private fun ImdbEpisodeRatingBadge(
    rating: Double,
    onClick: (() -> Unit)? = null
) {
    val (backgroundColor, textColor) = when {
        rating >= 9.5 -> Color(0xFF2B83FA) to Color.White
        rating >= 9.0 -> Color(0xFF00C853) to Color.White
        rating >= 8.0 -> Color(0xFF2E7D32) to Color.White
        rating >= 7.0 -> Color(0xFFFBC02D) to Color.Black
        rating >= 6.0 -> Color(0xFFF57C00) to Color.White
        rating >= 5.0 -> Color(0xFFD32F2F) to Color.White
        else          -> Color(0xFF7B1FA2) to Color.White
    }

    Row(
        modifier = if (onClick != null) {
            Modifier.clip(RoundedCornerShape(4.dp)).tvClickable(shape = RoundedCornerShape(4.dp), onClick = onClick)
        } else {
            Modifier.clip(RoundedCornerShape(4.dp))
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF5C518))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "IMDb",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                letterSpacing = 0.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "%.1f".format(rating),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
