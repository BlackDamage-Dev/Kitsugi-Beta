@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.app.MalProfileState
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun MalProfileContent(
    viewModel: KitsugiProfileViewModel,
    state: MalProfileState,
    mediaEntries: List<MediaEntry>,
    appSettings: AppSettings,
    onEntryClick: (MediaEntry) -> Unit,
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onFavoriteCharacterClick: (charId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStaffClick: (staffId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onOpenFavoriteSheet: (title: String, items: List<ProfileFavoriteItem>, onClick: (ProfileFavoriteItem) -> Unit) -> Unit,
    onOpenStatsClick: (() -> Unit)? = null,
    isLandscape: Boolean,
    accentColor: Color,
    onImageClick: ((urls: List<String>, initialIndex: Int, title: String) -> Unit)? = null,
    onFavoriteStudioClick: ((studioId: Int, source: String, name: String?, imageUrl: String?) -> Unit)? = null,
    onGenreClick: (String) -> Unit = {},
    onUserProfileClick: (userId: Int, username: String, avatarUrl: String?) -> Unit = { _, _, _ -> },
    onLoadMoreFriends: (() -> Unit)? = null,
    onLoadMoreReviews: (() -> Unit)? = null,
    onLoadMoreRecommendations: (() -> Unit)? = null,
    onLoadMoreClubs: (() -> Unit)? = null
) {
    var activeTab by rememberSaveable { mutableIntStateOf(viewModel.malActiveTab) }
    LaunchedEffect(activeTab) { viewModel.malActiveTab = activeTab }

    var statsMediaType by rememberSaveable { mutableIntStateOf(viewModel.malStatsMediaType) }
    LaunchedEffect(statsMediaType) { viewModel.malStatsMediaType = statsMediaType }

    var statsSubTab by rememberSaveable { mutableIntStateOf(viewModel.malStatsSubTab) }
    LaunchedEffect(statsSubTab) { viewModel.malStatsSubTab = statsSubTab }

    var socialFilter by rememberSaveable { mutableIntStateOf(viewModel.malSocialFilter) }
    LaunchedEffect(socialFilter) { viewModel.malSocialFilter = socialFilter }

    var favoritesFilter by rememberSaveable { mutableIntStateOf(viewModel.malFavoritesFilter) }
    LaunchedEffect(favoritesFilter) { viewModel.malFavoritesFilter = favoritesFilter }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.malScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.malScrollOffset
    )

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.updateMalScroll(index, offset)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = viewModel.malActiveTab,
        pageCount = { 5 }
    )

    LaunchedEffect(pagerState.currentPage) {
        activeTab = pagerState.currentPage
        viewModel.malActiveTab = pagerState.currentPage
    }

    ProfileFavoritesAutoLoad(
        listState = listState,
        enabled = activeTab == 4 && socialFilter == 0 && state.friendsHasNextPage && onLoadMoreFriends != null,
        count = state.socialState.followers.size,
        onLoadMore = { onLoadMoreFriends?.invoke() }
    )
    ProfileFavoritesAutoLoad(
        listState = listState,
        enabled = activeTab == 4 && socialFilter == 1 && state.reviewsHasNextPage && onLoadMoreReviews != null,
        count = state.reviews.size,
        onLoadMore = { onLoadMoreReviews?.invoke() }
    )
    ProfileFavoritesAutoLoad(
        listState = listState,
        enabled = activeTab == 4 && socialFilter == 2 && state.recommendationsHasNextPage && onLoadMoreRecommendations != null,
        count = state.recommendations.size,
        onLoadMore = { onLoadMoreRecommendations?.invoke() }
    )
    ProfileFavoritesAutoLoad(
        listState = listState,
        enabled = activeTab == 4 && socialFilter == 3 && state.clubsHasNextPage && onLoadMoreClubs != null,
        count = state.clubs.size,
        onLoadMore = { onLoadMoreClubs?.invoke() }
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isLandscape) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner and Avatar
        item {
            val context = LocalContext.current
            val avatarUrl = state.avatarUrl?.takeIf { it.isNotBlank() }
            val username = state.name.ifBlank { stringResource(R.string.myanimelist_kullanicisi) }
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
                            else Brush.horizontalGradient(listOf(accentColor, KitsugiColors.AccentBlue))
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, KitsugiColors.Background.copy(alpha = 0.8f))))
                )

                // Share butonu – sağ üst köşe
                if (state.name.isNotBlank()) {
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
                            val url = com.kitsugi.animelist.utils.ShareUtils.buildProfileUrl("myanimelist", state.name)
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
                        val details = buildList {
                            if (state.location.isNotBlank()) add(state.location)
                            if (state.joinedAt.isNotBlank()) {
                                val dateStr = state.joinedAt.substringBefore("T")
                                add(KitsugiStrings.get(R.string.profile_joined_at, dateStr))
                            }
                        }.joinToString(" • ")

                        if (details.isNotBlank()) {
                            Text(
                                text = details,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // Top 5 Icon Sub-Tabs + Sub-filters (Sticky at top)
        stickyHeader(key = "mal_tabs_header") {
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
                        Icons.BarChart to stringResource(R.string.profile_tab_statistics),
                        Icons.Star to stringResource(R.string.profile_tab_favorites),
                        Icons.People to stringResource(R.string.profile_tab_social)
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

                    if (pagerState.currentPage == 3) {
                        val favoritesFilterState = rememberLazyListState()
                        androidx.compose.foundation.lazy.LazyRow(
                            state = favoritesFilterState,
                            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(favoritesFilterState),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(4) { idx ->
                                val label = listOf(
                                    stringResource(R.string.profile_anime_list),
                                    stringResource(R.string.profile_manga_list),
                                    stringResource(R.string.profile_favorite_characters),
                                    stringResource(R.string.profile_section_staff)
                                )[idx]
                                ProfileFilterChip(
                                    text = label,
                                    isSelected = favoritesFilter == idx,
                                    accentColor = accentColor,
                                    onClick = { favoritesFilter = idx }
                                )
                            }
                        }
                    } else if (pagerState.currentPage == 4) {
                        val socialFilterState = rememberLazyListState()
                        val socialLabels = listOf(
                            stringResource(R.string.profile_mal_friends_count, state.socialState.followers.size),
                            stringResource(R.string.profile_mal_tab_count, stringResource(R.string.detail_tab_reviews), state.reviews.size),
                            stringResource(R.string.profile_mal_tab_count, stringResource(R.string.detail_tab_recommendations), state.recommendations.size),
                            stringResource(R.string.profile_mal_tab_count, stringResource(R.string.profile_mal_clubs), state.clubs.size)
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            state = socialFilterState,
                            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(socialFilterState),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(socialLabels.size) { index ->
                                ProfileFilterChip(
                                    text = socialLabels[index],
                                    isSelected = socialFilter == index,
                                    accentColor = accentColor,
                                    onClick = { socialFilter = index }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pager Content
        item(key = "content") {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val pageHeights = viewModel.malPageHeights
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
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(KitsugiColors.Surface)
                                        .padding(18.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.inline_myanimelist_profile_overview_6996234),
                                        color = KitsugiColors.TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        StatCard(stringResource(R.string.profile_anime_entries), state.animeStats?.count?.toString() ?: "0")
                                        StatCard(stringResource(R.string.profile_manga_entries), state.mangaStats?.count?.toString() ?: "0")
                                    }
                                }
                                MalAboutExtras(state = state, accentColor = accentColor, autoTranslateEnabled = appSettings.autoTranslateEnabled)
                            }
                            1 -> MalActivitySection(
                                state = state,
                                accentColor = accentColor,
                                onOpenMedia = { e ->
                                    onFavoriteMediaClick(
                                        e.malId,
                                        if (e.isManga) MediaType.Manga else MediaType.Anime,
                                        "jikan",
                                        e.title,
                                        e.imageUrl
                                    )
                                }
                            ) {
                                val malEntries = remember(mediaEntries) {
                                    mediaEntries.filter {
                                        it.source.equals("myanimelist", true) || it.source.equals("mal", true) || it.source.equals("jikan", true)
                                    }
                                }
                                if (malEntries.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = stringResource(R.string.henuz_myanimelist_aktivitesi_bulunmuyor), color = KitsugiColors.TextMuted)
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        malEntries.take(15).forEach { entry ->
                                            ProfileActivityRow(
                                                title = entry.title,
                                                imageUrl = entry.imageUrl,
                                                statusStr = entry.status.localizedLabel(),
                                                progressStr = stringResource(R.string.profile_episode_progress, entry.progress.toString()),
                                                onClick = { onEntryClick(entry) }
                                            )
                                        }
                                    }
                                }
                            }
                            2 -> MalStatisticsSection(
                                state = state,
                                statsMediaType = statsMediaType,
                                onStatsMediaTypeChange = { statsMediaType = it },
                                statsSubTab = statsSubTab,
                                onStatsSubTabChange = { statsSubTab = it },
                                accentColor = accentColor,
                                onGenreClick = onGenreClick,
                                onStudioClick = onFavoriteStudioClick,
                                onVoiceActorClick = onFavoriteStaffClick
                            )
                            3 -> {
                                val currentFavList = when (favoritesFilter) {
                                    0 -> state.favoriteAnime
                                    1 -> state.favoriteManga
                                    2 -> state.favoriteCharacters
                                    3 -> state.favoriteStaff
                                    else -> emptyList()
                                }

                                val filterTitle = when (favoritesFilter) {
                                    0 -> stringResource(R.string.profile_favorite_anime)
                                    1 -> stringResource(R.string.profile_favorite_manga)
                                    2 -> stringResource(R.string.profile_favorite_characters)
                                    3 -> stringResource(R.string.profile_favorite_staff)
                                    else -> stringResource(R.string.profile_favorites)
                                }

                                if (currentFavList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = stringResource(R.string.favori_oge_bulunamadi), color = KitsugiColors.TextMuted)
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "$filterTitle (${currentFavList.size})",
                                                color = KitsugiColors.TextPrimary,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(KitsugiColors.SurfaceStrong)
                                                    .clickable {
                                                        onOpenFavoriteSheet(filterTitle, currentFavList) { item ->
                                                            item.id.toIntOrNull()?.let { id ->
                                                                when (favoritesFilter) {
                                                                    0 -> onFavoriteMediaClick(id, MediaType.Anime, "jikan", item.title, item.imageUrl)
                                                                    1 -> onFavoriteMediaClick(id, MediaType.Manga, "jikan", item.title, item.imageUrl)
                                                                    2 -> onFavoriteCharacterClick(id, "jikan", item.title, item.imageUrl)
                                                                    3 -> onFavoriteStaffClick(id, "jikan", item.title, item.imageUrl)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.tumunu_gor),
                                                    color = accentColor,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        val gridColumns = if (isLandscape) 3 else 2
                                        currentFavList.chunked(gridColumns).forEach { rowItems ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                rowItems.forEach { item ->
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                item.id.toIntOrNull()?.let { id ->
                                                                    when (favoritesFilter) {
                                                                        0 -> onFavoriteMediaClick(id, MediaType.Anime, "jikan", item.title, item.imageUrl)
                                                                        1 -> onFavoriteMediaClick(id, MediaType.Manga, "jikan", item.title, item.imageUrl)
                                                                        2 -> onFavoriteCharacterClick(id, "jikan", item.title, item.imageUrl)
                                                                        3 -> onFavoriteStaffClick(id, "jikan", item.title, item.imageUrl)
                                                                    }
                                                                }
                                                            },
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .aspectRatio(0.7f)
                                                                .clip(RoundedCornerShape(14.dp))
                                                                .background(KitsugiColors.Surface)
                                                        ) {
                                                            if (item.imageUrl.isNotBlank()) {
                                                                KitsugiNsfwImage(
                                                                    model = item.imageUrl,
                                                                    contentDescription = item.title,
                                                                    isAdult = item.isAdult,
                                                                    blurAdultMedia = appSettings.blurAdultMedia,
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = ContentScale.Crop
                                                                )
                                                            } else {
                                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                    Icon(imageVector = Icons.Favorite, contentDescription = null, tint = accentColor)
                                                                }
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = item.title,
                                                            color = KitsugiColors.TextPrimary,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            textAlign = TextAlign.Center,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                if (rowItems.size < gridColumns) {
                                                    repeat(gridColumns - rowItems.size) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            4 -> {
                                when (socialFilter) {
                                    0 -> {
                                        val friendList = state.socialState.followers
                                        if (friendList.isEmpty()) {
                                            if (state.isExtraLoading) {
                                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentColor) }
                                            } else if (state.friendsLoadMoreFailed && onLoadMoreFriends != null) {
                                                MalLoadMoreRetry(accentColor, onLoadMoreFriends)
                                            } else {
                                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                                    Text(stringResource(R.string.profile_mal_no_friends), color = KitsugiColors.TextMuted)
                                                }
                                            }
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                val gridColumns = if (isLandscape) 3 else 2
                                                friendList.chunked(gridColumns).forEach { rowItems ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        rowItems.forEach { user ->
                                                            Column(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .clip(RoundedCornerShape(16.dp))
                                                                    .background(KitsugiColors.Surface)
                                                                    .clickable { onUserProfileClick(user.id, user.name, user.avatarUrl) }
                                                                    .padding(12.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                            ) {
                                                                AsyncImage(
                                                                    model = user.avatarUrl ?: "",
                                                                    contentDescription = user.name,
                                                                    modifier = Modifier.size(56.dp).clip(CircleShape),
                                                                    contentScale = ContentScale.Crop
                                                                )
                                                                Spacer(modifier = Modifier.height(8.dp))
                                                                Text(
                                                                    text = user.name,
                                                                    color = KitsugiColors.TextPrimary,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        repeat(gridColumns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                                    }
                                                }
                                                if (state.isLoadingMoreFriends) CircularProgressIndicator(modifier = Modifier.size(22.dp).align(Alignment.CenterHorizontally), color = accentColor)
                                                if (state.friendsLoadMoreFailed && onLoadMoreFriends != null) {
                                                    MalLoadMoreRetry(accentColor, onLoadMoreFriends)
                                                }
                                            }
                                        }
                                    }
                                    1 -> MalReviewsSection(
                                        state = state,
                                        accentColor = accentColor,
                                        onRetry = onLoadMoreReviews,
                                        onOpenMedia = { review ->
                                            onFavoriteMediaClick(
                                                review.malId,
                                                if (review.kind.equals("manga", true)) MediaType.Manga else MediaType.Anime,
                                                "jikan",
                                                review.title,
                                                review.imageUrl
                                            )
                                        }
                                    )
                                    2 -> MalRecommendationsSection(
                                        state = state,
                                        accentColor = accentColor,
                                        onRetry = onLoadMoreRecommendations,
                                        onOpenMedia = { recommendation ->
                                            onFavoriteMediaClick(
                                                recommendation.malId,
                                                if (recommendation.kind.equals("manga", true)) MediaType.Manga else MediaType.Anime,
                                                "jikan",
                                                recommendation.title,
                                                recommendation.imageUrl
                                            )
                                        }
                                    )
                                    else -> MalClubsSection(state, accentColor, onLoadMoreClubs)
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

