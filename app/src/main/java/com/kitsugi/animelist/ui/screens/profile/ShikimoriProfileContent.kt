@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.utils.detailPagerHeight
import com.kitsugi.animelist.ui.utils.rememberDetailPagerContentHeightPx
import com.kitsugi.animelist.ui.utils.rememberDetailPagerMinHeightPx

import com.kitsugi.animelist.ui.theme.gradient.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.auth.ShikimoriApiClient.ShikimoriHistoryItem
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.app.ShikimoriProfileState
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCard
import com.kitsugi.animelist.ui.components.stats.KitsugiStatsCardHeader
import com.kitsugi.animelist.ui.screens.profile.tabs.DetailedProfileStatsOverview
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiOriginalToggleChip
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.ui.components.rememberAutoTranslatedText
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun ShikimoriProfileContent(
    viewModel: KitsugiProfileViewModel,
    state: ShikimoriProfileState,
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
    var activeTab by rememberSaveable { mutableIntStateOf(viewModel.shikimoriActiveTab) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = viewModel.shikimoriActiveTab.coerceIn(0, 2),
        pageCount = { 3 }
    )
    LaunchedEffect(pagerState.currentPage) {
        activeTab = pagerState.currentPage
        viewModel.shikimoriActiveTab = pagerState.currentPage
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.shikimoriScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.shikimoriScrollOffset
    )

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.updateShikimoriScroll(index, offset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isLandscape) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Banner and Avatar ───────────────────────────────────────────────
        item {
            val avatarUrl = state.avatarUrl?.takeIf { it.isNotBlank() }
            val username = state.name.ifBlank { stringResource(R.string.shikimori_kullanicisi) }
            val avatarTitle = stringResource(R.string.profile_avatar_title, username)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2E7D32), Color(0xFF1B5E20), KitsugiColors.SurfaceStrong)
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, KitsugiColors.Background.copy(alpha = 0.85f))
                            )
                        )
                )

                // Share butonu
                if (state.name.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.Background.copy(alpha = 0.6f))
                            .clickable {
                                com.kitsugi.animelist.utils.ShareUtils.shareText(
                                    context,
                                    state.name,
                                    com.kitsugi.animelist.utils.ShareUtils.buildProfileUrl("shikimori", state.name)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Share,
                            contentDescription = stringResource(R.string.paylas),
                            tint = KitsugiColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Avatar and User Info
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = avatarUrl ?: com.kitsugi.animelist.R.drawable.ic_logo_shikimori,
                        contentDescription = avatarTitle,
                        modifier = Modifier
                            .size(68.dp)
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
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = username,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            KitsugiPlatformLogo(platformId = "shikimori", size = 18.dp)
                        }

                        if (!state.location.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = state.location,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else if (!state.lastOnline.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.inline_last_online_1_s_ce2d27e, state.lastOnline.take(10)),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // ── Sticky Header: Icon Tabs ────────────────────────────────────────
        stickyHeader(key = "shiki_tabs_header") {
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
                        Icons.BarChart to stringResource(R.string.profile_tab_statistics),
                        Icons.Bookmarks to stringResource(R.string.profile_records)
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

        // ── Pager Content ───────────────────────────────────────────────────
        item(key = "shiki_content") {
            val pageHeights = viewModel.shikimoriPageHeights
            // ── KASMA FIX (2026-11-05) ────────────────────────────────────────────
            // Yükseklik interpolasyonu artık KOMPOZİSYONDA değil, yalnızca YERLEŞİM
            // fazında okunur (derivedStateOf + Modifier.layout). Böylece sekme
            // kaydırırken kompozisyon ve sekme içerikleri hiç çalışmaz.
            val pagerHeightPx = rememberDetailPagerContentHeightPx(pagerState, pageHeights)
            val minPagerHeightPx = rememberDetailPagerMinHeightPx()

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                userScrollEnabled = true,
                beyondViewportPageCount = 0,
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .detailPagerHeight(pagerHeightPx, minPagerHeightPx)
                    .clipToBounds()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
                                .recordStablePageHeight(pageHeights, page, coordinates.size.height)
                        }
                ) {
                    when (page) {
                        0 -> ShikimoriAboutTab(
                            state = state,
                            accentColor = accentColor,
                            autoTranslateEnabled = appSettings.autoTranslateEnabled,
                            preferredTranslator = appSettings.preferredTranslator,
                            onFavoriteMediaClick = onFavoriteMediaClick,
                            onOpenFavoriteSheet = onOpenFavoriteSheet
                        )
                        1 -> ShikimoriStatsTab(state = state, accentColor = accentColor)
                        2 -> ShikimoriRatesTab(
                            history = state.history,
                            entries = state.recentRates,
                            accentColor = accentColor,
                            onEntryClick = { item ->
                                val idInt = item.id.toIntOrNull() ?: 0
                                onFavoriteMediaClick(idInt, MediaType.Anime, "mal", item.title, item.imageUrl)
                            },
                            onHistoryClick = { hist ->
                                val idInt = hist.targetId?.toInt() ?: 0
                                val type = if (hist.targetType?.contains("manga", ignoreCase = true) == true) MediaType.Manga else MediaType.Anime
                                onFavoriteMediaClick(idInt, type, "mal", hist.targetTitle ?: "", hist.targetImage)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShikimoriAboutTab(
    state: ShikimoriProfileState,
    accentColor: Color,
    autoTranslateEnabled: Boolean = false,
    preferredTranslator: String = "DEFAULT",
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onOpenFavoriteSheet: (title: String, items: List<ProfileFavoriteItem>, onClick: (ProfileFavoriteItem) -> Unit) -> Unit
) {
    val context = LocalContext.current
    // Wiki mantığı: otomatik çeviri açıksa biyografi uygulama içinde çevrilir.
    val bioTranslation = rememberAutoTranslatedText(state.bio, autoTranslateEnabled)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bio Section
        if (!state.bio.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.hakkinda),
                            color = KitsugiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Shikimori Bio", state.bio))
                                    Toast.makeText(context, KitsugiStrings.get(R.string.metin_kopyalandi), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.ContentCopy, contentDescription = stringResource(R.string.action_copy), tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                            if (bioTranslation.hasTranslation) {
                                KitsugiOriginalToggleChip(
                                    showingOriginal = bioTranslation.showingOriginal,
                                    onClick = bioTranslation.toggleOriginal,
                                    tint = KitsugiColors.TextMuted
                                )
                            }
                            KitsugiTranslateIconButton(
                                onClick = { context.openTranslator(state.bio, preferredTranslator) },
                                modifier = Modifier.size(32.dp),
                                tint = KitsugiColors.TextMuted,
                                contentDescription = stringResource(R.string.cevir),
                                iconSize = 16.dp,
                                ringSize = 26.dp,
                                isLoading = bioTranslation.isLoading
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = bioTranslation.displayText,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Quick Stats Grid
        val totalAnime = state.watchingAnime + state.completedAnime + state.onHoldAnime + state.droppedAnime + state.plannedAnime
        val totalManga = state.readingManga + state.completedManga + state.onHoldManga + state.droppedManga + state.plannedManga

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShikimoriStatCard(
                title = "Anime",
                value = totalAnime.toString(),
                icon = Icons.Tv,
                modifier = Modifier.weight(1f),
                accentColor = accentColor
            )
            ShikimoriStatCard(
                title = "Manga",
                value = totalManga.toString(),
                icon = Icons.Book,
                modifier = Modifier.weight(1f),
                accentColor = Color(0xFF2E7D32)
            )
            ShikimoriStatCard(
                title = stringResource(R.string.detail_stat_score),
                value = if (state.avgScore > 0) String.format("%.1f", state.avgScore) else "-",
                icon = Icons.Star,
                modifier = Modifier.weight(1f),
                accentColor = KitsugiColors.AccentYellow
            )
        }

        // Favorites Horizontal Section
        if (state.favorites.isNotEmpty()) {
            FavoritesHorizontalSection(
                title = stringResource(R.string.profile_favorites),
                items = state.favorites,
                onSeeAllClick = {
                    onOpenFavoriteSheet(KitsugiStrings.get(R.string.profile_platform_favorites, "Shikimori"), state.favorites) { item ->
                        val idInt = item.id.toIntOrNull() ?: 0
                        onFavoriteMediaClick(idInt, MediaType.Anime, "mal", item.title, item.imageUrl)
                    }
                },
                onItemClick = { item ->
                    val idInt = item.id.toIntOrNull() ?: 0
                    onFavoriteMediaClick(idInt, MediaType.Anime, "mal", item.title, item.imageUrl)
                }
            )
        }

        // Account Attributes Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.inline_profile_details_645945c),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                if (!state.location.isNullOrBlank()) {
                    ShikiAttributeRow(icon = Icons.LocationOn, label = stringResource(R.string.profile_label_location), value = state.location)
                }
                if (!state.website.isNullOrBlank()) {
                    ShikiAttributeRow(icon = Icons.Language, label = stringResource(R.string.profile_label_website), value = state.website)
                }
                if (!state.sex.isNullOrBlank() || state.fullYears != null) {
                    val sexStr = when (state.sex?.lowercase()) {
                        "male" -> stringResource(R.string.profile_gender_male)
                        "female" -> stringResource(R.string.profile_gender_female)
                        else -> state.sex ?: ""
                    }
                    val ageStr = state.fullYears?.let { stringResource(R.string.profile_age_years, it) }.orEmpty()
                    val combo = listOf(sexStr, ageStr).filter { it.isNotBlank() }.joinToString(" • ")
                    ShikiAttributeRow(icon = Icons.Person, label = stringResource(R.string.cinsiyet_yas), value = combo)
                }
                if (!state.lastOnline.isNullOrBlank()) {
                    ShikiAttributeRow(icon = Icons.Schedule, label = stringResource(R.string.son_giris), value = state.lastOnline.take(16).replace("T", " "))
                }
                ShikiAttributeRow(icon = Icons.CheckCircle, label = stringResource(R.string.profile_completed_anime), value = state.completedAnime.toString())
                ShikiAttributeRow(icon = Icons.CheckCircle, label = stringResource(R.string.profile_completed_manga), value = state.completedManga.toString())
            }
        }
    }
}

@Composable
private fun ShikimoriStatsTab(
    state: ShikimoriProfileState,
    accentColor: Color
) {
    var selectedMediaType by rememberSaveable {
        mutableIntStateOf(if (state.animeOverviewStats != null || state.animeScoreDist.isNotEmpty()) 0 else 1)
    }
    var scoreDistType by rememberSaveable { mutableIntStateOf(0) }
    var lengthDistType by rememberSaveable { mutableIntStateOf(0) }
    var releaseYearDistType by rememberSaveable { mutableIntStateOf(0) }
    var startYearDistType by rememberSaveable { mutableIntStateOf(0) }
    val isAnime = selectedMediaType == 0
    val overview = if (isAnime) state.animeOverviewStats else state.mangaOverviewStats

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KitsugiStatsCard {
            KitsugiStatsCardHeader(
                title = stringResource(R.string.profile_stats_general),
                icon = Icons.BarChart,
                subtitle = "Shikimori",
                accentColor = accentColor
            )
            Spacer(Modifier.height(12.dp))
            ShikimoriStatsMediaSwitcher(
                selected = selectedMediaType,
                accentColor = accentColor,
                onSelected = { selectedMediaType = it }
            )
        }

        if (overview != null) {
            DetailedProfileStatsOverview(
                overview = overview,
                isAnime = isAnime,
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
            val distribution = if (isAnime) state.animeScoreDist else state.mangaScoreDist
            val scoreCount = distribution.values.sum()
            val statusItems = if (isAnime) {
                listOf(
                    Triple(stringResource(R.string.profile_status_watching), state.watchingAnime, KitsugiColors.AccentGreen),
                    Triple(stringResource(R.string.profile_status_completed), state.completedAnime, KitsugiColors.AccentBlue),
                    Triple(stringResource(R.string.profile_status_plan_to_watch_short), state.plannedAnime, KitsugiColors.AccentIndigo),
                    Triple(stringResource(R.string.profile_status_on_hold), state.onHoldAnime, KitsugiColors.TextMuted),
                    Triple(stringResource(R.string.profile_status_dropped), state.droppedAnime, KitsugiColors.AccentRed)
                )
            } else {
                listOf(
                    Triple(stringResource(R.string.profile_status_reading), state.readingManga, KitsugiColors.AccentGreen),
                    Triple(stringResource(R.string.profile_status_completed), state.completedManga, KitsugiColors.AccentBlue),
                    Triple(stringResource(R.string.profile_status_plan_to_read_short), state.plannedManga, KitsugiColors.AccentIndigo),
                    Triple(stringResource(R.string.profile_status_on_hold), state.onHoldManga, KitsugiColors.TextMuted),
                    Triple(stringResource(R.string.profile_status_dropped), state.droppedManga, KitsugiColors.AccentRed)
                )
            }
            if (scoreCount > 0 || statusItems.any { it.second > 0 }) {
                KitsugiStatsCard {
                    KitsugiStatsCardHeader(
                        title = if (isAnime) stringResource(R.string.anime_durum_dagilimi) else stringResource(R.string.manga_durum_dagilimi),
                        icon = Icons.DonutLarge,
                        subtitle = stringResource(R.string.detail_stats_status_subtitle, statusItems.sumOf { it.second }),
                        accentColor = accentColor
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalStatsBar(statusItems)
                    if (scoreCount > 0) {
                        Spacer(Modifier.height(16.dp))
                        KitsugiStatsCardHeader(
                            title = stringResource(R.string.puan_dagilimi),
                            icon = Icons.BarChart,
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(8.dp))
                        VerticalStatsBar((1..10).map { it.toString() to (distribution[it] ?: 0).toFloat() }, accentColor)
                    }
                }
            } else {
                KitsugiStatsCard {
                    Text(
                        text = stringResource(R.string.istatistik_verisi_bulunamadi),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ShikimoriStatsMediaSwitcher(
    selected: Int,
    accentColor: Color,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceStrong)
            .padding(4.dp)
    ) {
        listOf(stringResource(R.string.profile_anime_entries), stringResource(R.string.profile_manga_entries)).forEachIndexed { index, label ->
            val isSelected = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) accentColor else Color.Transparent)
                    .clickable { onSelected(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ShikimoriRatesTab(
    history: List<ShikimoriHistoryItem>,
    entries: List<ProfileFavoriteItem>,
    accentColor: Color,
    onEntryClick: (ProfileFavoriteItem) -> Unit,
    onHistoryClick: (ShikimoriHistoryItem) -> Unit
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(if (history.isNotEmpty()) 0 else 1) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (history.isNotEmpty() && entries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    label = { Text(stringResource(R.string.inline_activity_history_1_s_d4e081c, history.size), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    label = { Text(stringResource(R.string.inline_entries_1_s_d1f2007, entries.size), fontSize = 12.sp) }
                )
            }
        }

        if (selectedSubTab == 0 && history.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.forEach { item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHistoryClick(item) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.targetImage.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.targetImage,
                                    contentDescription = item.targetTitle,
                                    modifier = Modifier
                                        .size(44.dp, 60.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp, 60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(KitsugiColors.SurfaceStrong),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.History, contentDescription = null, tint = KitsugiColors.TextMuted)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.targetTitle ?: stringResource(R.string.ui_account_title),
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.createdAt.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.createdAt.take(16).replace("T", " "),
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.ChevronRight,
                                contentDescription = null,
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else if (entries.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KitsugiColors.Surface)
                            .clickable { onEntryClick(item) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!item.imageUrl.isNullOrBlank()) {
                            // 18+ blur burada da geçerli: kart, kullanıcının ayarına göre
                            // bulanıklaştırılır (düz AsyncImage sızıntıya yol açardı).
                            KitsugiNsfwImage(
                                model = item.imageUrl,
                                contentDescription = item.title,
                                isAdult = item.isAdult,
                                modifier = Modifier
                                    .size(44.dp, 60.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp, 60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(KitsugiColors.SurfaceStrong),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Movie, contentDescription = null, tint = KitsugiColors.TextMuted)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = item.title,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Bookmarks,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.inline_no_records_or_activity_were_e950767),
                        color = KitsugiColors.TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ShikimoriStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = KitsugiColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ShikiAttributeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
            Text(text = label, color = KitsugiColors.TextSecondary, fontSize = 13.sp)
        }
        Text(
            text = value,
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShikiStatusRow(
    label: String,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(text = label, color = KitsugiColors.TextSecondary, fontSize = 13.sp)
        }
        Text(
            text = count.toString(),
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
