package com.kitsugi.animelist.ui.screens.search.components
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kitsugi.animelist.data.cloudstream.CsEpisodeMatcher
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.model.MediaType
import com.lagradost.cloudstream3.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.safeBlur

sealed interface DetailLoadState {
    object Loading : DetailLoadState
    data class Error(val message: String) : DetailLoadState
    data class Success(val response: LoadResponse) : DetailLoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiAddonDetailDialog(
    api: MainAPI,
    url: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()

    var loadState by remember(url) { mutableStateOf<DetailLoadState>(DetailLoadState.Loading) }

    fun loadDetail() {
        loadState = DetailLoadState.Loading
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    CsStreamRunner.safeLoad(api, url)
                }
                if (response != null) {
                    loadState = DetailLoadState.Success(response)
                } else {
                    loadState = DetailLoadState.Error("İçerik bilgileri yüklenemedi.")
                }
            } catch (e: Exception) {
                Log.e("KitsugiAddonDetail", "safeLoad hatası", e)
                loadState = DetailLoadState.Error(e.localizedMessage ?: "Bir hata oluştu.")
            }
        }
    }

    LaunchedEffect(url) {
        loadDetail()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = KitsugiColors.Background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ── Loading state ──────────────────────────────────────────
                if (loadState is DetailLoadState.Loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            KitsugiPlasmaLoader(size = 50.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.yukleniyor), color = KitsugiColors.TextMuted, fontSize = 14.sp)
                        }
                    }
                }

                // ── Error state ────────────────────────────────────────────
                if (loadState is DetailLoadState.Error) {
                    val err = loadState as DetailLoadState.Error
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(err.message, color = KitsugiColors.TextMuted)
                            KitsugiButton(
                                onClick = { loadDetail() }
                            ) {
                                Text("Tekrar Dene", color = Color.White)
                            }
                        }
                    }
                }

                // ── Success state ──────────────────────────────────────────
                if (loadState is DetailLoadState.Success) {
                    val response = (loadState as DetailLoadState.Success).response
                    DetailContent(
                        api = api,
                        loadResponse = response,
                        accentColor = accentColor,
                        context = context,
                        onDismissRequest = onDismissRequest
                    )
                }

                // ── Close button (top right) ──────────────────────────────
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Close, contentDescription = stringResource(R.string.kapat), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    api: MainAPI,
    loadResponse: LoadResponse,
    accentColor: Color,
    context: Context,
    onDismissRequest: () -> Unit
) {
    // ── Parse Dub status for anime ────────────────────────────────────────
    val dubStatuses = remember(loadResponse) {
        if (loadResponse is AnimeLoadResponse) {
            loadResponse.episodes.keys.filter { loadResponse.episodes[it]?.isNotEmpty() == true }
        } else {
            emptyList()
        }
    }

    var selectedDubStatus by remember(dubStatuses) {
        mutableStateOf(dubStatuses.firstOrNull())
    }

    val rawEpisodesList = remember(loadResponse, selectedDubStatus) {
        when (loadResponse) {
            is AnimeLoadResponse -> {
                selectedDubStatus?.let { loadResponse.episodes[it] } ?: emptyList()
            }
            is TvSeriesLoadResponse -> {
                loadResponse.episodes
            }
            else -> emptyList()
        }
    }

    // ── Group episodes by season ──────────────────────────────────────────
    val episodesBySeason = remember(rawEpisodesList) {
        rawEpisodesList.groupBy { CsEpisodeMatcher.getEpisodeSeason(it) ?: 1 }
    }

    val seasons = remember(episodesBySeason) {
        episodesBySeason.keys.sorted()
    }

    var selectedSeason by remember(seasons) {
        mutableStateOf(seasons.firstOrNull() ?: 1)
    }

    val displayEpisodes = remember(episodesBySeason, selectedSeason) {
        episodesBySeason[selectedSeason] ?: emptyList()
    }

    val isEpisodeBased = loadResponse is AnimeLoadResponse || loadResponse is TvSeriesLoadResponse

    val streamMediaType = when (loadResponse) {
        is AnimeLoadResponse -> MediaType.Anime
        is TvSeriesLoadResponse -> MediaType.TvShow
        is MovieLoadResponse -> MediaType.Movie
        else -> null
    }

    // +18 UYUMU (2026-10-14): eklenti detay diyaloğundaki posterler de blurAdultMedia
    // sözleşmesine uyar — NSFW türü veya bilinen yetişkin eklenti ise görsel bulanıklaştırılır.
    val isAdultResponse = runCatching { loadResponse.type == TvType.NSFW }.getOrDefault(false) ||
        CsStreamRunner.ADULT_PLUGINS_SET.contains(api.name)

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background backdrop for premium look
        if (!loadResponse.posterUrl.isNullOrBlank()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = loadResponse.posterUrl,
                contentDescription = null,
                isAdult = isAdultResponse,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    // ÇÖKME ÖNLEMİ: koşulsuz `.blur(20.dp)` → güvenli sarmalayıcı
                    // (API 31 altı atlanır, yarıçap 16dp ile sınırlanır).
                    .safeBlur(16.dp)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                KitsugiColors.Background.copy(alpha = 0.5f),
                                KitsugiColors.Background
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Hero Section (Poster + Title + Metadata) ─────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Poster Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(110.dp)
                            .height(160.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                            model = loadResponse.posterUrl,
                            contentDescription = loadResponse.name,
                            isAdult = isAdultResponse,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Metadata details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Extension Badge
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Extension, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                            Text(api.name, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = loadResponse.name,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Meta details row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            loadResponse.year?.let {
                                Text(it.toString(), color = KitsugiColors.TextMuted, fontSize = 13.sp)
                            }
                            loadResponse.duration?.let { durationMin ->
                                Text("${durationMin} dk", color = KitsugiColors.TextMuted, fontSize = 13.sp)
                            }
                            val scoreObj = loadResponse.score
                            if (scoreObj != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = scoreObj.toFloat(10).toString().take(3),
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // Tags/Genres (doğal spoiler etiketler gizlenir)
                        val tags = loadResponse.tags
                            ?.filterNot { com.kitsugi.animelist.utils.KitsugiSpoilerRules.isIntrinsicSpoiler(it) }
                        if (!tags.isNullOrEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tags.take(4).forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(KitsugiColors.Surface)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(tag, color = KitsugiColors.TextSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Synopsis / Plot ──────────────────────────────────────────────
            val plot = loadResponse.plot
            if (!plot.isNullOrBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.aciklama),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = plot,
                            color = KitsugiColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }

            // ── Interactive Selectors (DubStatus and Seasons) ────────────────
            if (isEpisodeBased) {
                // Dub Status Selector pills
                if (dubStatuses.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            itemsIndexed(dubStatuses) { _, status ->
                                val selected = selectedDubStatus == status
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) accentColor else KitsugiColors.Surface)
                                        .clickable { selectedDubStatus = status }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = status.name,
                                        color = if (selected) Color.White else KitsugiColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Season Selector pills
                if (seasons.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            itemsIndexed(seasons) { _, seasonNum ->
                                val selected = selectedSeason == seasonNum
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) accentColor else KitsugiColors.Surface)
                                        .clickable { selectedSeason = seasonNum }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${seasonNum}. Sezon",
                                        color = if (selected) Color.White else KitsugiColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Episodes / Direct Play ───────────────────────────────────────
            if (!isEpisodeBased) {
                // Direct play button for Movies, Torrents, LiveStreams
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiButton(
                            onClick = {
                                com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity.start(
                                    context = context,
                                    malId = null,
                                    aniListId = null,
                                    episode = 1,
                                    season = 1,
                                    isMovie = true,
                                    mediaType = streamMediaType,
                                    title = loadResponse.name,
                                    posterUrl = loadResponse.posterUrl,
                                    cs3Url = loadResponse.url,
                                    cs3ApiName = api.name
                                )
                                // Dialog'u kapatma — stream'den geri gelince bilgi sayfası açık kalsın
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.PlayArrow, contentDescription = null, tint = Color.White)
                                Text(stringResource(R.string.oynat), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            } else {
                // Episodes header
                item {
                    Text(
                        text = stringResource(R.string.inline_episodes_1_s_b40a1e6, displayEpisodes.size),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }

                // Render episodes cards
                if (displayEpisodes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.inline_no_episodes_found_this_season_4862fcf), color = KitsugiColors.TextMuted)
                        }
                    }
                } else {
                    itemsIndexed(displayEpisodes) { _, ep ->
                        val epNum = CsEpisodeMatcher.getEpisodeNumber(ep) ?: 1
                        val epSeason = CsEpisodeMatcher.getEpisodeSeason(ep) ?: 1
                        val epName = CsEpisodeMatcher.getEpisodeName(ep)
                        val epPlot = getEpisodeDescription(ep)

                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable {
                                    com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity.start(
                                        context = context,
                                        malId = null,
                                        aniListId = null,
                                        episode = epNum,
                                        season = epSeason,
                                        isMovie = false,
                                        mediaType = streamMediaType,
                                        title = loadResponse.name,
                                        posterUrl = loadResponse.posterUrl,
                                        cs3Url = loadResponse.url,
                                        cs3ApiName = api.name
                                    )
                                    // Dialog'u kapatma — stream'den geri gelince bilgi sayfası açık kalsın
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.inline_1_s_episode_6f7288b, epNum),
                                            color = KitsugiColors.TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (!epName.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = epName,
                                                color = KitsugiColors.TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.PlayArrow,
                                        contentDescription = stringResource(R.string.oynat),
                                        tint = accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                if (!epPlot.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = epPlot,
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 12.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
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

private fun getEpisodeDescription(ep: Any): String? {
    return try {
        val field = ep.javaClass.getDeclaredField("description")
        field.isAccessible = true
        field.get(ep) as? String
    } catch (_: Exception) {
        try {
            val field = ep.javaClass.getDeclaredField("plot")
            field.isAccessible = true
            field.get(ep) as? String
        } catch (_: Exception) { null }
    }
}
