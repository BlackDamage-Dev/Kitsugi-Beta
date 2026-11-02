@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.ui.components.PlatformErrorCodeChip
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.res.Configuration
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.components.KitsugiActivityDetailBottomSheet
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiPullToRefreshIndicator
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.utils.ShareUtils
import kotlinx.coroutines.launch
import com.kitsugi.animelist.ui.screens.profile.tabs.UserProfileInfoTab
import com.kitsugi.animelist.ui.screens.profile.tabs.UserProfileActivityTab
import com.kitsugi.animelist.ui.screens.profile.tabs.UserProfileStatsTab
import com.kitsugi.animelist.ui.screens.profile.tabs.UserProfileFavoritesTab
import com.kitsugi.animelist.ui.screens.profile.tabs.UserProfileSocialTab
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun KitsugiUserProfileScreen(
    userId: Int,
    fallbackUsername: String? = null,
    fallbackAvatar: String? = null,
    appSettings: AppSettings,
    mediaEntries: List<MediaEntry>,
    onBackClick: () -> Unit,
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onFavoriteCharacterClick: (charId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStaffClick: (staffId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStudioClick: ((studioId: Int, source: String, name: String?, imageUrl: String?) -> Unit)? = null,
    onUserProfileClick: (userId: Int, username: String, avatarUrl: String?) -> Unit,
    onGenreClick: (genre: String) -> Unit = {},
    onTagClick: (tag: String) -> Unit = {},
    onOpenUserMediaList: (userId: Int, mediaType: MediaType) -> Unit = { _, _ -> },
    accentColor: Color? = null,
    customViewModel: KitsugiUserProfileViewModel? = null,
    onScrollReset: () -> Unit = {}
) {
    val resolvedAccent = accentColor ?: LocalKitsugiAccent.current
    val viewModel: KitsugiUserProfileViewModel = customViewModel ?: viewModel(key = "user_profile_$userId")

    LaunchedEffect(userId) { viewModel.loadUser(userId, fallbackUsername, fallbackAvatar) }

    val state by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiClient = remember { JikanApiClient() }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = viewModel.activeTab.coerceIn(0, 4),
        pageCount = { 5 }
    )
    LaunchedEffect(pagerState.currentPage) { viewModel.activeTab = pagerState.currentPage }
    val activeTab = pagerState.currentPage

    var activeFavoriteSheet by remember { mutableStateOf<Pair<String, List<ProfileFavoriteItem>>?>(null) }
    var activeActivityIdForDetail by remember { mutableStateOf<Int?>(null) }
    var activeGalleryImages by remember { mutableStateOf<Triple<List<String>, Int, String>?>(null) }

    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.scrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )

    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.updateScroll(index, offset)
            if (index == 0 && offset == 0) onScrollReset()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(KitsugiColors.Background)) {

        // ── Top bar ──────────────────────────────────────────────────────────
        Surface(modifier = Modifier.fillMaxWidth(), color = KitsugiColors.Surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.ArrowBack, contentDescription = stringResource(R.string.geri), tint = KitsugiColors.TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = state.name.ifBlank { fallbackUsername ?: stringResource(R.string.ui_profile_user_fallback) },
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.name.isNotBlank()) {
                    IconButton(onClick = {
                        ShareUtils.shareText(context, state.name, ShareUtils.buildProfileUrl("anilist", state.name))
                    }) {
                        Icon(Icons.Share, contentDescription = stringResource(R.string.profili_paylas), tint = KitsugiColors.TextPrimary)
                    }
                }
            }
        }

        // ── Favoriler: otomatik sayfalama (Daha Fazla Yükle butonu yok) ──
        val favFilter = viewModel.favoritesFilter
        val favCategoryKey = ProfileFavoriteCategoryKeys.getOrElse(favFilter) { "anime" }
        val favHasNext = when (favFilter) {
            0 -> state.favAnimeHasNext; 1 -> state.favMangaHasNext; 2 -> state.favCharHasNext
            3 -> state.favStaffHasNext; 4 -> state.favStudioHasNext; else -> false
        }
        val favCount = when (favFilter) {
            0 -> state.favoriteAnime.size; 1 -> state.favoriteManga.size; 2 -> state.favoriteCharacters.size
            3 -> state.favoriteStaff.size; 4 -> state.favoriteStudios.size; else -> 0
        }
        ProfileFavoritesAutoLoad(
            listState = listState,
            enabled = activeTab == 3 && favHasNext,
            count = favCount,
            onLoadMore = { viewModel.loadMoreFavorites(favCategoryKey) }
        )

        // ── Content ──────────────────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.name.isNotBlank(),
            onRefresh = { viewModel.loadUser(userId, fallbackUsername, fallbackAvatar, forceRefresh = true) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = pullRefreshState,
            indicator = {
                KitsugiPullToRefreshIndicator(
                    state = pullRefreshState,
                    isRefreshing = state.isLoading && state.name.isNotBlank(),
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            if (state.isLoading && state.name.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KitsugiPlasmaLoader(size = 50.dp)
                }
            } else {
                val username = state.name.ifBlank { fallbackUsername ?: stringResource(R.string.kullanici) }
                Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = if (isLandscape) 18.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Yükleme / işlem hatası: mesaj ve platform hata kodu ──
                    state.error?.let { errorText ->
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = errorText,
                                    color = KitsugiColors.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                state.errorCode?.let { code -> PlatformErrorCodeChip(code = code) }
                            }
                        }
                    }

                    // ── Banner + avatar header ────────────────────────────
                    item {
                        UserProfileHeaderCard(
                            state = state,
                            fallbackUsername = fallbackUsername,
                            fallbackAvatar = fallbackAvatar,
                            accentColor = resolvedAccent,
                            onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) }
                        )
                    }

                    // ── Action buttons ────────────────────────────────────
                    item {
                        UserProfileActionButtons(
                            state = state,
                            accentColor = resolvedAccent,
                            userId = state.userId,
                            viewModel = viewModel,
                            onOpenUserMediaList = onOpenUserMediaList
                        )
                    }

                    // ── Sticky tab bar ────────────────────────────────────
                    stickyHeader(key = "user_tabs") {
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
                                    onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                                    accentColor = resolvedAccent
                                )
                                // Sub-filter chips
                                when (activeTab) {
                                    2 -> {
                                        val subTabs = listOf(
                                            stringResource(R.string.profile_stats_general),
                                            stringResource(R.string.detail_section_genres),
                                            stringResource(R.string.profile_section_tags),
                                            stringResource(R.string.profile_section_staff),
                                            stringResource(R.string.character_tab_voice_actors),
                                            stringResource(R.string.detail_section_studios)
                                        )
                                        val s = rememberLazyListState()
                                        LazyRow(state = s,
                                            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(s),
                                            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(subTabs.size) { idx ->
                                                ProfileFilterChip(subTabs[idx], viewModel.statsSubTab == idx, resolvedAccent) { viewModel.statsSubTab = idx }
                                            }
                                        }
                                    }
                                    3 -> {
                                        val s = rememberLazyListState()
                                        val labels = listOf(
                                            stringResource(R.string.profile_anime_list),
                                            stringResource(R.string.profile_manga_list),
                                            stringResource(R.string.profile_favorite_characters),
                                            stringResource(R.string.profile_section_staff),
                                            stringResource(R.string.detail_section_studios)
                                        )
                                        LazyRow(state = s,
                                            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(s),
                                            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(5) { idx ->
                                                ProfileFilterChip(labels[idx], viewModel.favoritesFilter == idx, resolvedAccent) { viewModel.favoritesFilter = idx }
                                            }
                                        }
                                    }
                                    4 -> {
                                        val s = rememberLazyListState()
                                        LazyRow(state = s,
                                            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(s),
                                            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            item {
                                                ProfileFilterChip(
                                                    stringResource(R.string.profile_followers_count, state.socialState.followers.size),
                                                    viewModel.socialFilter == 0,
                                                    resolvedAccent
                                                ) { viewModel.socialFilter = 0 }
                                            }
                                            item {
                                                ProfileFilterChip(
                                                    stringResource(R.string.profile_following_count, state.socialState.following.size),
                                                    viewModel.socialFilter == 1,
                                                    resolvedAccent
                                                ) { viewModel.socialFilter = 1 }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Pager ─────────────────────────────────────────────
                    item(key = "user_content") {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val pageHeights = remember { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
                        val currentPage = pagerState.currentPage
                        val currentPageOffset = pagerState.currentPageOffsetFraction
                        val targetPage = if (currentPageOffset > 0f) currentPage + 1 else if (currentPageOffset < 0f) currentPage - 1 else currentPage
                        val currentH = pageHeights[currentPage] ?: 0
                        val targetH = pageHeights[targetPage] ?: currentH
                        val interpolatedH = remember(currentH, targetH, currentPageOffset) {
                            val px = if (currentH > 0 && targetH > 0) currentH + (targetH - currentH) * kotlin.math.abs(currentPageOffset)
                            else if (currentH > 0) currentH.toFloat() else 0f
                            if (px > 0f) with(density) { px.toDp() } else null
                        }
                        val screenH = LocalConfiguration.current.screenHeightDp

                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 0,
                            pageSpacing = 12.dp,
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                                .layout { measurable, constraints ->
                                    val minH = with(density) { (screenH - 64).dp.roundToPx() }.coerceAtLeast(0)
                                    val placeable = measurable.measure(constraints.copy(minHeight = minH, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                                    val h = (interpolatedH?.roundToPx()?.coerceAtLeast(minH) ?: placeable.height)
                                        .coerceIn(minH, 16_000_000)
                                    layout(placeable.width, h) { placeable.placeRelative(0, 0) }
                                }.clipToBounds()
                        ) { page ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .onGloballyPositioned {
                                        com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.recordStablePageHeight(pageHeights, page, it.size.height)
                                    },
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (page) {
                                    0 -> UserProfileInfoTab(
                                        state = state,
                                        accentColor = resolvedAccent,
                                        username = username,
                                        autoTranslateEnabled = appSettings.autoTranslateEnabled,
                                        preferredTranslator = appSettings.preferredTranslator,
                                        onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) }
                                    )
                                    1 -> UserProfileActivityTab(state, resolvedAccent, appSettings, userId, fallbackUsername, fallbackAvatar, viewModel, apiClient, onFavoriteMediaClick) { activeActivityIdForDetail = it }
                                    2 -> UserProfileStatsTab(state, resolvedAccent, viewModel, onGenreClick, onTagClick, onFavoriteStaffClick, onFavoriteStudioClick)
                                    3 -> UserProfileFavoritesTab(state, resolvedAccent, appSettings, viewModel, isLandscape, onFavoriteMediaClick, onFavoriteCharacterClick, onFavoriteStaffClick, onFavoriteStudioClick) { title, items -> activeFavoriteSheet = title to items }
                                    4 -> UserProfileSocialTab(state, resolvedAccent, viewModel, isLandscape, onUserProfileClick)
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }

                // Listem tarzı alt kontroller: kategori (sol) + yukarı kaydırma (sağ)
                ProfileFavoritesFloatingControls(
                    listState = listState,
                    visible = activeTab in 2..4,
                    selectedCategory = when (activeTab) { 2 -> viewModel.statsSubTab; 4 -> viewModel.socialFilter; else -> favFilter },
                    categoryCounts = when (activeTab) {
                        3 -> listOf(state.favoriteAnime.size, state.favoriteManga.size, state.favoriteCharacters.size,
                            state.favoriteStaff.size, state.favoriteStudios.size)
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
                        2 -> viewModel.statsSubTab = it
                        4 -> viewModel.socialFilter = it
                        else -> viewModel.favoritesFilter = it
                    } },
                    bottomOffset = 20.dp,
                    onScrollReset = onScrollReset
                )
                }
            }
        }
    }

    // ── Overlays ─────────────────────────────────────────────────────────────
    activeFavoriteSheet?.let { sheet ->
        val favoritesFilter = viewModel.favoritesFilter
        val currentHasNext = when (favoritesFilter) {
            0 -> state.favAnimeHasNext; 1 -> state.favMangaHasNext
            2 -> state.favCharHasNext; 3 -> state.favStaffHasNext
            4 -> state.favStudioHasNext; else -> false
        }
        val currentCategory = when (favoritesFilter) {
            0 -> "anime"; 1 -> "manga"; 2 -> "characters"; 3 -> "staff"; 4 -> "studios"; else -> "anime"
        }
        val currentFavList = when (favoritesFilter) {
            0 -> state.favoriteAnime; 1 -> state.favoriteManga
            2 -> state.favoriteCharacters; 3 -> state.favoriteStaff
            4 -> state.favoriteStudios; else -> sheet.second
        }
        FavoritesExpandedBottomSheet(
            title = sheet.first,
            items = currentFavList,
            blurAdultMedia = appSettings.blurAdultMedia,
            hasNextPage = currentHasNext,
            onLoadMore = { viewModel.loadMoreFavorites(currentCategory) },
            onItemClick = { item ->
                item.id.toIntOrNull()?.let { id ->
                    when (favoritesFilter) {
                        0 -> onFavoriteMediaClick(id, MediaType.Anime, "anilist", item.title, item.imageUrl)
                        1 -> onFavoriteMediaClick(id, MediaType.Manga, "anilist", item.title, item.imageUrl)
                        2 -> onFavoriteCharacterClick(id, "anilist", item.title, item.imageUrl)
                        3 -> onFavoriteStaffClick(id, "anilist", item.title, item.imageUrl)
                        4 -> onFavoriteStudioClick?.invoke(id, "anilist", item.title, item.imageUrl)
                    }
                }
                activeFavoriteSheet = null
            },
            onDismiss = { activeFavoriteSheet = null }
        )
    }

    activeActivityIdForDetail?.let { actId ->
        KitsugiActivityDetailBottomSheet(
            activityId = actId,
            apiClient = apiClient,
            titleLanguage = appSettings.titleLanguage.toString(),
            blurAdultMedia = appSettings.blurAdultMedia,
            onMediaClick = { mediaId, mType, source -> onFavoriteMediaClick(mediaId, mType, source, "", null) },
            onDismiss = { activeActivityIdForDetail = null; viewModel.loadUser(userId, fallbackUsername, fallbackAvatar) }
        )
    }

    activeGalleryImages?.let { (urls, idx, title) ->
        KitsugiImageGalleryDialog(imageUrls = urls, initialIndex = idx, title = title, onDismiss = { activeGalleryImages = null })
    }
}
