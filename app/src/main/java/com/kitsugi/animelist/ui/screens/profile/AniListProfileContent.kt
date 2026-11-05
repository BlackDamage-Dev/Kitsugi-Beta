@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.utils.detailPagerHeight
import com.kitsugi.animelist.ui.utils.rememberDetailPagerContentHeightPx
import com.kitsugi.animelist.ui.utils.rememberDetailPagerMinHeightPx

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.AniListProfileState
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.screens.profile.tabs.*
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun AniListProfileContent(
    viewModel: KitsugiProfileViewModel,
    state: AniListProfileState,
    mediaEntries: List<MediaEntry>,
    appSettings: AppSettings,
    onEntryClick: (MediaEntry) -> Unit,
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onFavoriteCharacterClick: (charId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStaffClick: (staffId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStudioClick: ((studioId: Int, source: String, name: String?, imageUrl: String?) -> Unit)? = null,
    onLoadMoreActivities: () -> Unit,
    onOpenFavoriteSheet: (title: String, items: List<ProfileFavoriteItem>, onClick: (ProfileFavoriteItem) -> Unit) -> Unit,
    onOpenStatsClick: (() -> Unit)? = null,
    onActivityClick: ((Int) -> Unit)? = null,
    onLikeClick: ((Int) -> Unit)? = null,
    onDeleteClick: ((Int) -> Unit)? = null,
    onGenreClick: (String) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onUserProfileClick: (userId: Int, username: String, avatarUrl: String?) -> Unit = { _, _, _ -> },
    isLandscape: Boolean,
    accentColor: Color,
    onImageClick: ((urls: List<String>, initialIndex: Int, title: String) -> Unit)? = null,
    isBottomBarVisible: Boolean = true,
    onScrollReset: () -> Unit = {}
) {
    val context = LocalContext.current
    var activeTab by rememberSaveable { mutableIntStateOf(viewModel.aniListActiveTab) }
    LaunchedEffect(activeTab) { viewModel.aniListActiveTab = activeTab }

    var statsMediaType by rememberSaveable { mutableIntStateOf(viewModel.aniListStatsMediaType) }
    LaunchedEffect(statsMediaType) { viewModel.aniListStatsMediaType = statsMediaType }

    var statsSubTab by rememberSaveable { mutableIntStateOf(viewModel.aniListStatsSubTab) }
    LaunchedEffect(statsSubTab) { viewModel.aniListStatsSubTab = statsSubTab }

    var favoritesFilter by rememberSaveable { mutableIntStateOf(viewModel.aniListFavoritesFilter) }
    LaunchedEffect(favoritesFilter) { viewModel.aniListFavoritesFilter = favoritesFilter }

    var socialFilter by rememberSaveable { mutableIntStateOf(viewModel.aniListSocialFilter) }
    LaunchedEffect(socialFilter) { viewModel.aniListSocialFilter = socialFilter }

    var scoreDistType by rememberSaveable { mutableIntStateOf(viewModel.aniListScoreDistType) }
    LaunchedEffect(scoreDistType) { viewModel.aniListScoreDistType = scoreDistType }

    var lengthDistType by rememberSaveable { mutableIntStateOf(viewModel.aniListLengthDistType) }
    LaunchedEffect(lengthDistType) { viewModel.aniListLengthDistType = lengthDistType }

    var releaseYearDistType by rememberSaveable { mutableIntStateOf(viewModel.aniListReleaseYearDistType) }
    LaunchedEffect(releaseYearDistType) { viewModel.aniListReleaseYearDistType = releaseYearDistType }

    var startYearDistType by rememberSaveable { mutableIntStateOf(viewModel.aniListStartYearDistType) }
    LaunchedEffect(startYearDistType) { viewModel.aniListStartYearDistType = startYearDistType }

    var statSortType by rememberSaveable { mutableIntStateOf(viewModel.aniListStatsSortType) }
    LaunchedEffect(statSortType) { viewModel.aniListStatsSortType = statSortType }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.aniListScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.aniListScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.updateAniListScroll(index, offset)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = viewModel.aniListActiveTab,
        pageCount = { 5 }
    )
    LaunchedEffect(pagerState.currentPage) {
        activeTab = pagerState.currentPage
        viewModel.aniListActiveTab = pagerState.currentPage
    }

    // Favoriler: kategori seçimi ve otomatik sayfalama (Daha Fazla Yükle butonu yok)
    val favCategoryKey = ProfileFavoriteCategoryKeys.getOrElse(favoritesFilter) { "anime" }
    val favHasNext = when (favoritesFilter) {
        0 -> state.favAnimeHasNext; 1 -> state.favMangaHasNext; 2 -> state.favCharHasNext
        3 -> state.favStaffHasNext; 4 -> state.favStudioHasNext; else -> false
    }
    val favCount = when (favoritesFilter) {
        0 -> state.favoriteAnime.size; 1 -> state.favoriteManga.size; 2 -> state.favoriteCharacters.size
        3 -> state.favoriteStaff.size; 4 -> state.favoriteStudios.size; else -> 0
    }
    val favCategoryCounts = listOf(
        state.favoriteAnime.size, state.favoriteManga.size, state.favoriteCharacters.size,
        state.favoriteStaff.size, state.favoriteStudios.size
    )
    ProfileFavoritesAutoLoad(
        listState = listState,
        enabled = activeTab == 3 && favHasNext,
        count = favCount,
        onLoadMore = { viewModel.loadMoreFavorites(favCategoryKey) }
    )

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isLandscape) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Banner and Avatar ──
        item {
            val avatarUrl = state.avatarUrl?.takeIf { it.isNotBlank() }
            val bannerUrl = state.bannerUrl?.takeIf { it.isNotBlank() }
            val imageList = listOfNotNull(avatarUrl, bannerUrl)
            val username = state.name.ifBlank { stringResource(R.string.kullanici) }
            val bannerTitle = stringResource(R.string.profile_banner_title, username)
            val avatarTitle = stringResource(R.string.profile_avatar_title, username)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .then(
                        if (!bannerUrl.isNullOrBlank()) Modifier.clickable {
                            val idx = imageList.indexOf(bannerUrl).coerceAtLeast(0)
                            onImageClick?.invoke(imageList, idx, bannerTitle)
                        } else Modifier
                    )
            ) {
                if (!state.bannerUrl.isNullOrBlank()) {
                    AsyncImage(model = state.bannerUrl, contentDescription = bannerTitle, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(accentColor, KitsugiColors.AccentPink))))
                }
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, KitsugiColors.Background.copy(alpha = 0.8f)))))

                // Share button
                if (state.name.isNotBlank()) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape).background(KitsugiColors.Background.copy(alpha = 0.50f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            val url = com.kitsugi.animelist.utils.ShareUtils.buildProfileUrl("anilist", state.name)
                            com.kitsugi.animelist.utils.ShareUtils.shareText(context, state.name, url)
                        }) {
                            Icon(imageVector = Icons.Share, contentDescription = stringResource(R.string.profili_paylas), tint = KitsugiColors.TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Avatar overlay
                Row(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = state.avatarUrl ?: "",
                        contentDescription = avatarTitle,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(3.dp, KitsugiColors.Background, CircleShape)
                            .then(
                                if (!avatarUrl.isNullOrBlank()) Modifier.clickable {
                                    val idx = imageList.indexOf(avatarUrl).coerceAtLeast(0)
                                    onImageClick?.invoke(imageList, idx, avatarTitle)
                                } else Modifier
                            ),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = state.name, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (!state.donatorBadge.isNullOrBlank() && state.donatorTier > 0) {
                            Text(text = "Donator Tier ${state.donatorTier}", color = KitsugiColors.AccentOrange, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Sticky Tab Bar + Sub-filters ──
        stickyHeader(key = "anilist_tabs_header") {
            Surface(modifier = Modifier.fillMaxWidth(), color = KitsugiColors.Background) {
                Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val tabs = listOf(
                        Icons.Info to stringResource(R.string.character_tab_about),
                        Icons.ChatBubble to stringResource(R.string.profile_tab_activity),
                        Icons.BarChart to stringResource(R.string.profile_tab_statistics),
                        Icons.Star to stringResource(R.string.profile_tab_favorites),
                        Icons.People to stringResource(R.string.profile_tab_social)
                    )
                    ProfileHeaderIconTabs(
                        tabs = tabs,
                        selectedTab = pagerState.currentPage,
                        onTabSelected = { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } },
                        accentColor = accentColor
                    )

                    when (pagerState.currentPage) {
                        2 -> {
                            val statsSubTabState = androidx.compose.foundation.lazy.rememberLazyListState()
                            androidx.compose.foundation.lazy.LazyRow(
                                state = statsSubTabState,
                                flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(statsSubTabState),
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(6) { idx ->
                                    val label = listOf(
                                        stringResource(R.string.profile_stats_general),
                                        stringResource(R.string.detail_section_genres),
                                        stringResource(R.string.profile_section_tags),
                                        stringResource(R.string.profile_section_staff),
                                        stringResource(R.string.character_tab_voice_actors),
                                        stringResource(R.string.detail_section_studios)
                                    )[idx]
                                    ProfileFilterChip(text = label, isSelected = statsSubTab == idx, accentColor = accentColor, onClick = { statsSubTab = idx })
                                }
                            }
                        }
                        3 -> {
                            val favoritesFilterState = androidx.compose.foundation.lazy.rememberLazyListState()
                            androidx.compose.foundation.lazy.LazyRow(
                                state = favoritesFilterState,
                                flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(favoritesFilterState),
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(5) { idx ->
                                    val label = profileFavoriteCategoryLabels()[idx]
                                    ProfileFilterChip(text = label, isSelected = favoritesFilter == idx, accentColor = accentColor, onClick = { favoritesFilter = idx })
                                }
                            }
                        }
                        4 -> {
                            val socialFilterState = androidx.compose.foundation.lazy.rememberLazyListState()
                            androidx.compose.foundation.lazy.LazyRow(
                                state = socialFilterState,
                                flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(socialFilterState),
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item { ProfileFilterChip(text = stringResource(R.string.profile_followers_count, state.socialState.followers.size), isSelected = socialFilter == 0, accentColor = accentColor, onClick = { socialFilter = 0 }) }
                                item { ProfileFilterChip(text = stringResource(R.string.profile_following_count, state.socialState.following.size), isSelected = socialFilter == 1, accentColor = accentColor, onClick = { socialFilter = 1 }) }
                            }
                        }
                    }
                }
            }
        }

        // ── Pager Content ──
        item(key = "content") {
            val pageHeights = viewModel.aniListPageHeights
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
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 600.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                            com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.recordStablePageHeight(pageHeights, page, it.size.height)
                        },
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (page) {
                            0 -> AniListInfoTab(
                                state = state,
                                accentColor = accentColor,
                                autoTranslateEnabled = appSettings.autoTranslateEnabled,
                                preferredTranslator = appSettings.preferredTranslator,
                                onImageClick = onImageClick
                            )
                            1 -> AniListActivityTab(
                                state = state, accentColor = accentColor, appSettings = appSettings,
                                onFavoriteMediaClick = onFavoriteMediaClick,
                                onActivityClick = onActivityClick, onLikeClick = onLikeClick, onDeleteClick = onDeleteClick
                            )
                            2 -> AniListStatsTab(
                                state = state, accentColor = accentColor,
                                statsMediaType = statsMediaType, statsSubTab = statsSubTab,
                                scoreDistType = scoreDistType, lengthDistType = lengthDistType,
                                releaseYearDistType = releaseYearDistType, startYearDistType = startYearDistType,
                                statSortType = statSortType,
                                onStatsMediaTypeChange = { statsMediaType = it },
                                onScoreDistTypeChange = { scoreDistType = it },
                                onLengthDistTypeChange = { lengthDistType = it },
                                onReleaseYearDistTypeChange = { releaseYearDistType = it },
                                onStartYearDistTypeChange = { startYearDistType = it },
                                onStatSortTypeChange = { statSortType = it },
                                onGenreClick = onGenreClick, onTagClick = onTagClick,
                                onFavoriteStaffClick = onFavoriteStaffClick,
                                onFavoriteStudioClick = onFavoriteStudioClick
                            )
                            3 -> AniListFavoritesTab(
                                state = state, accentColor = accentColor, isLandscape = isLandscape,
                                favoritesFilter = favoritesFilter,
                                layoutId = appSettings.selectedListLayoutId,
                                blurAdultMedia = appSettings.blurAdultMedia,
                                viewModel = viewModel,
                                onFavoriteMediaClick = onFavoriteMediaClick,
                                onFavoriteCharacterClick = onFavoriteCharacterClick,
                                onFavoriteStaffClick = onFavoriteStaffClick,
                                onFavoriteStudioClick = onFavoriteStudioClick,
                                onOpenFavoriteSheet = onOpenFavoriteSheet
                            )
                            4 -> AniListSocialTab(
                                state = state, accentColor = accentColor, isLandscape = isLandscape,
                                socialFilter = socialFilter, onUserProfileClick = onUserProfileClick
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(if (isBottomBarVisible && !isLandscape) 140.dp else 80.dp)) }
    }

    val bottomOffset by animateDpAsState(
        targetValue = if (isLandscape) {
            16.dp
        } else if (isBottomBarVisible) {
            96.dp
        } else {
            20.dp
        },
        animationSpec = tween(durationMillis = 200),
        label = "profile_favorites_bottom_offset"
    )

    ProfileFavoritesFloatingControls(
        listState = listState,
        visible = activeTab in 2..4,
        selectedCategory = when (activeTab) { 2 -> statsSubTab; 4 -> socialFilter; else -> favoritesFilter },
        categoryCounts = when (activeTab) {
            3 -> favCategoryCounts
            4 -> listOf(state.socialState.followers.size, state.socialState.following.size)
            else -> emptyList()
        },
        categoryLabels = when (activeTab) {
            2 -> listOf(
                                        stringResource(R.string.profile_stats_general),
                                        stringResource(R.string.detail_section_genres),
                                        stringResource(R.string.profile_section_tags),
                                        stringResource(R.string.profile_section_staff),
                                        stringResource(R.string.character_tab_voice_actors),
                                        stringResource(R.string.detail_section_studios)
                                    )
            4 -> listOf(stringResource(R.string.profile_followers), stringResource(R.string.profile_following))
            else -> profileFavoriteCategoryLabels()
        },
        showCounts = activeTab != 2,
        onCategorySelected = { when (activeTab) {
            2 -> statsSubTab = it
            4 -> socialFilter = it
            else -> favoritesFilter = it
        } },
        bottomOffset = bottomOffset,
        onScrollReset = onScrollReset
    )
    }
}
