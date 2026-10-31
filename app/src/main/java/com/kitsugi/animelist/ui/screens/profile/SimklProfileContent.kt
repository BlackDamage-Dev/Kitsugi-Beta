@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.Context
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.settings.AppSettings
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.app.SimklProfileState
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCard
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCardHeader
import com.kitsugi.animelist.ui.screens.profile.tabs.DetailedProfileStatsOverview
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun SimklProfileContent(
    viewModel: KitsugiProfileViewModel,
    state: SimklProfileState,
    mediaEntries: List<MediaEntry>,
    appSettings: AppSettings,
    onEntryClick: (MediaEntry) -> Unit,
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onOpenFavoriteSheet: (title: String, items: List<ProfileFavoriteItem>, onClick: (ProfileFavoriteItem) -> Unit) -> Unit,
    onOpenStatsClick: (() -> Unit)? = null,
    isLandscape: Boolean,
    accentColor: Color,
    onImageClick: ((urls: List<String>, initialIndex: Int, title: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var activeTab by rememberSaveable { mutableIntStateOf(viewModel.simklActiveTab) }
    var scoreDistType by rememberSaveable { mutableIntStateOf(0) }
    var lengthDistType by rememberSaveable { mutableIntStateOf(0) }
    var releaseYearDistType by rememberSaveable { mutableIntStateOf(0) }
    var startYearDistType by rememberSaveable { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = viewModel.simklActiveTab.coerceIn(0, 2),
        pageCount = { 3 }
    )
    LaunchedEffect(pagerState.currentPage) {
        activeTab = pagerState.currentPage
        viewModel.simklActiveTab = pagerState.currentPage
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.simklScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.simklScrollOffset
    )

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.updateSimklScroll(index, offset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isLandscape) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner and Avatar
        item {
            val avatarUrl = state.avatarUrl?.takeIf { it.isNotBlank() }
            val username = state.name.ifBlank { stringResource(R.string.simkl_kullanicisi) }
            val avatarTitle = stringResource(R.string.profile_avatar_title, username)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (KitsugiColors.IsGradient) KitsugiColors.AccentBrush
                            else Brush.horizontalGradient(listOf(accentColor, KitsugiColors.AccentOrange))
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, KitsugiColors.Background.copy(alpha = 0.8f))))
                )

                // Share butonu – sağ üst köşe
                if (state.name.isNotBlank()) {
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.Background.copy(alpha = 0.50f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            val url = com.kitsugi.animelist.utils.ShareUtils.buildProfileUrl("simkl", state.name)
                            com.kitsugi.animelist.utils.ShareUtils.shareText(context, state.name, url)
                        }) {
                            Icon(
                                imageVector = Icons.Share,
                                contentDescription = stringResource(R.string.profili_paylas),
                                tint = KitsugiColors.TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Avatar overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = state.avatarUrl ?: "",
                        contentDescription = avatarTitle,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(3.dp, KitsugiColors.Background, CircleShape)
                            .then(
                                if (!avatarUrl.isNullOrBlank()) {
                                    Modifier.clickable {
                                        onImageClick?.invoke(listOf(avatarUrl), 0, avatarTitle)
                                    }
                                } else Modifier
                            ),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = state.name,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!state.accountType.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.inline_account_type_1_s_eb9dd8f, state.accountType),
                                color = KitsugiColors.AccentOrange,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Top 3 Icon Sub-Tabs (Sticky at top)
        stickyHeader(key = "simkl_tabs_header") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = KitsugiColors.Background
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val tabs = listOf(
                        Icons.Info to stringResource(R.string.character_tab_about),
                        Icons.ChatBubble to stringResource(R.string.profile_tab_activity),
                        Icons.BarChart to stringResource(R.string.profile_tab_statistics)
                    )
                    ProfileHeaderIconTabs(
                        tabs = tabs,
                        selectedTab = pagerState.currentPage,
                        onTabSelected = { page ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(page)
                            }
                        },
                        accentColor = accentColor
                    )
                }
            }
        }

        // Pager Content
        item(key = "content") {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val pageHeights = viewModel.simklPageHeights
            val currentPage = pagerState.currentPage
            val currentPageOffset = pagerState.currentPageOffsetFraction
            val targetPage = if (currentPageOffset > 0f) currentPage + 1 else if (currentPageOffset < 0f) currentPage - 1 else currentPage

            val currentHeightPx = pageHeights[currentPage] ?: 0
            val targetHeightPx = pageHeights[targetPage] ?: currentHeightPx

            val interpolatedHeightDp = remember(currentHeightPx, targetHeightPx, currentPageOffset) {
                val heightPx = if (currentHeightPx > 0 && targetHeightPx > 0) {
                    currentHeightPx + (targetHeightPx - currentHeightPx) * kotlin.math.abs(currentPageOffset)
                } else if (currentHeightPx > 0) {
                    currentHeightPx.toFloat()
                } else {
                    0f
                }
                if (heightPx > 0f) with(density) { heightPx.toDp() } else null
            }

            val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                userScrollEnabled = true,
                beyondViewportPageCount = 0,
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val minPagerHeightPx = with(density) { (screenHeightDp - 64).dp.roundToPx() }.coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = minPagerHeightPx,
                                maxHeight = androidx.compose.ui.unit.Constraints.Infinity
                            )
                        )
                        val height = (interpolatedHeightDp?.roundToPx()?.coerceAtLeast(minPagerHeightPx) ?: placeable.height)
                            .coerceIn(minPagerHeightPx, 16_000_000)
                        layout(placeable.width, height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .clipToBounds()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 600.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
                                    .recordStablePageHeight(pageHeights, page, coordinates.size.height)
                            },
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (page) {
                            0 -> {
                                if (!state.bio.isNullOrBlank()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(KitsugiColors.Surface)
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.hakkinda),
                                                color = KitsugiColors.TextPrimary,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                KitsugiTranslateIconButton(
                                                    onClick = { context.openTranslator(state.bio) },
                                                    modifier = Modifier.size(28.dp),
                                                    tint = accentColor,
                                                    contentDescription = stringResource(R.string.cevir),
                                                    iconSize = 16.dp,
                                                    ringSize = 24.dp
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("about", state.bio))
                                                        Toast.makeText(context, KitsugiStrings.get(R.string.toast_copied_clipboard), Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.ContentCopy,
                                                        contentDescription = stringResource(R.string.action_copy),
                                                        tint = KitsugiColors.TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        com.kitsugi.animelist.ui.components.KitsugiHtmlWebView(
                                            html = state.bio,
                                            modifier = Modifier.fillMaxWidth(),
                                            onImageClick = { urls, idx ->
                                                onImageClick?.invoke(urls, idx, "${state.name} Biyografisi")
                                            }
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(KitsugiColors.Surface)
                                        .padding(18.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.inline_simkl_profile_overview_5d1a6c1),
                                        color = KitsugiColors.TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        StatCard(stringResource(R.string.profile_anime_entries), state.totalAnime.toString())
                                        StatCard(stringResource(R.string.profile_tv_show_entries), state.totalShows.toString())
                                        StatCard(stringResource(R.string.profile_movie_entries), state.totalMovies.toString())
                                    }
                                }
                            }
                            1 -> {
                                if (state.recentHistory.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        state.recentHistory.forEach { item ->
                                            ProfileActivityRow(
                                                title = item.title,
                                                imageUrl = item.imageUrl,
                                                statusStr = stringResource(R.string.profile_status_watched),
                                                progressStr = null,
                                                onClick = {
                                                    item.id.toIntOrNull()?.let { id ->
                                                        onFavoriteMediaClick(id, MediaType.Anime, "simkl", item.title, item.imageUrl)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = stringResource(R.string.henuz_simkl_aktivitesi_bulunmuyor), color = KitsugiColors.TextMuted)
                                    }
                                }
                            }
                            2 -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    KitsugiStatsCard {
                                        KitsugiStatsCardHeader(
                                            title = stringResource(R.string.istatistikler),
                                            icon = Icons.BarChart,
                                            subtitle = "Simkl",
                                            accentColor = accentColor
                                        )
                                    }
                                    val overview = state.animeOverviewStats
                                    if (overview != null) {
                                        DetailedProfileStatsOverview(
                                            overview = overview,
                                            isAnime = true,
                                            accentColor = accentColor,
                                            scoreDistType = scoreDistType,
                                            lengthDistType = lengthDistType,
                                            releaseYearDistType = releaseYearDistType,
                                            startYearDistType = startYearDistType,
                                            onScoreDistTypeChange = { scoreDistType = it },
                                            onLengthDistTypeChange = { lengthDistType = it },
                                            onReleaseYearDistTypeChange = { releaseYearDistType = it },
                                            onStartYearDistTypeChange = { startYearDistType = it }
                                        )
                                    } else {
                                        KitsugiStatsCard {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                StatCard(stringResource(R.string.profile_total_anime), state.totalAnime.toString())
                                                StatCard(stringResource(R.string.profile_total_tv_shows), state.totalShows.toString())
                                                StatCard(stringResource(R.string.profile_total_movies), state.totalMovies.toString())
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            HorizontalStatsBar(
                                                listOf(
                                                    Triple(stringResource(R.string.profile_status_watching), state.watching, KitsugiColors.AccentGreen),
                                                    Triple(stringResource(R.string.profile_status_completed), state.completed, KitsugiColors.AccentBlue),
                                                    Triple(stringResource(R.string.profile_status_plan_to_watch_short), state.planned, KitsugiColors.AccentIndigo),
                                                    Triple(stringResource(R.string.profile_status_on_hold), state.paused, KitsugiColors.TextMuted),
                                                    Triple(stringResource(R.string.profile_status_dropped), state.dropped, KitsugiColors.AccentRed)
                                                )
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

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

