@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.res.Configuration
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.kitsugi.animelist.ui.components.KitsugiPullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.components.KitsugiActivityDetailBottomSheet
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiKitsuLoginDialog
import com.kitsugi.animelist.ui.components.KitsugiShikimoriLoginDialog
import com.kitsugi.animelist.ui.components.KitsugiBangumiLoginDialog
import com.kitsugi.animelist.ui.components.KitsugiTopAction
import com.kitsugi.animelist.ui.components.KitsugiTopActionRow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun KitsugiProfileScreen(
    viewModel: KitsugiProfileViewModel,
    mediaEntries: List<MediaEntry>,
    isAniListConnected: Boolean,
    isMalConnected: Boolean,
    isSimklConnected: Boolean,
    isKitsuConnected: Boolean = false,
    isShikimoriConnected: Boolean = false,
    isBangumiConnected: Boolean = false,
    profileName: String,
    listTitle: String,
    profileImageUri: String,
    bannerImageUri: String,
    appSettings: AppSettings,
    onEntryClick: (MediaEntry) -> Unit,
    onOpenSettingsClick: () -> Unit,
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onFavoriteCharacterClick: (charId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStaffClick: (staffId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStudioClick: ((studioId: Int, source: String, name: String?, imageUrl: String?) -> Unit)? = null,
    onOpenStatsClick: (() -> Unit)? = null,
    isNotificationsVisible: Boolean = false,
    onOpenNotifications: (() -> Unit)? = null,
    onOpenWatchHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onGenreClick: (String) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onLoginAniList: () -> Unit = {},
    onLoginMal: () -> Unit = {},
    onLoginSimkl: () -> Unit = {},
    onLoginKitsu: () -> Unit = {},
    onLoginShikimori: () -> Unit = {},
    onLoginBangumi: () -> Unit = {},
    onKitsuAuthSubmit: ((String, String, (Boolean, String?) -> Unit) -> Unit)? = null,
    onShikimoriAuthSubmit: ((String, String, String, (Boolean, String?) -> Unit) -> Unit)? = null,
    onBangumiAuthSubmit: ((String, String, String, (Boolean, String?) -> Unit) -> Unit)? = null,
    onUserProfileClick: (userId: Int, username: String, avatarUrl: String?) -> Unit = { _, _, _ -> },
    onMalUserProfileClick: (userId: Int, username: String, avatarUrl: String?) -> Unit = { _, _, _ -> },
    isBottomBarVisible: Boolean = true,
    onScrollReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val activeSubTab = viewModel.activeSubTab

    val aniListState by viewModel.aniListState.collectAsState()
    val malState by viewModel.malState.collectAsState()
    val simklState by viewModel.simklState.collectAsState()
    val kitsuState by viewModel.kitsuState.collectAsState()
    val shikimoriState by viewModel.shikimoriState.collectAsState()
    val bangumiState by viewModel.bangumiState.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiClient = remember { JikanApiClient() }

    var showSourcePickerSheet by remember { mutableStateOf(false) }
    var showKitsuLoginDialog by remember { mutableStateOf(false) }
    var showShikimoriLoginDialog by remember { mutableStateOf(false) }
    var showBangumiLoginDialog by remember { mutableStateOf(false) }

    var activeFavoriteSheet by remember { mutableStateOf<Pair<String, List<com.kitsugi.animelist.ui.app.ProfileFavoriteItem>>?>(null) }
    var onSheetItemClick by remember { mutableStateOf<((com.kitsugi.animelist.ui.app.ProfileFavoriteItem) -> Unit)?>(null) }
    var activeActivityIdForDetail by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }
    var activeGalleryImages by remember { mutableStateOf<Triple<List<String>, Int, String>?>(null) }

    val openFavoriteSheet: (String, List<com.kitsugi.animelist.ui.app.ProfileFavoriteItem>, (com.kitsugi.animelist.ui.app.ProfileFavoriteItem) -> Unit) -> Unit = { title, items, onClick ->
        activeFavoriteSheet = title to items
        onSheetItemClick = onClick
    }

    LaunchedEffect(activeSubTab, isAniListConnected, isMalConnected, isSimklConnected, isKitsuConnected, isShikimoriConnected, isBangumiConnected) {
        when (activeSubTab) {
            0 -> if (isAniListConnected && aniListState.userId == null && !aniListState.isLoading) viewModel.fetchAniListProfile()
            1 -> if (isMalConnected && malState.name.isBlank() && !malState.isLoading) viewModel.fetchMalProfile()
            2 -> if (isSimklConnected && simklState.name.isBlank() && !simklState.isLoading) viewModel.fetchSimklProfile()
            3 -> if (isKitsuConnected && kitsuState.name.isBlank() && !kitsuState.isLoading) viewModel.fetchKitsuProfile()
            4 -> if (isShikimoriConnected && shikimoriState.name.isBlank() && !shikimoriState.isLoading) viewModel.fetchShikimoriProfile()
            5 -> if (isBangumiConnected && bangumiState.lastUpdatedMs == 0L && !bangumiState.isLoading) viewModel.fetchBangumiProfile()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Tek kaynak seçici; platformlar açılır panelden değiştirilir ──
            val currentPlatform = ProfilePlatform.entries.getOrElse(activeSubTab) { ProfilePlatform.ANILIST }
            val isCurrentConnected = when (currentPlatform) {
                ProfilePlatform.ANILIST -> isAniListConnected
                ProfilePlatform.MAL -> isMalConnected
                ProfilePlatform.SIMKL -> isSimklConnected
                ProfilePlatform.KITSU -> isKitsuConnected
                ProfilePlatform.SHIKIMORI -> isShikimoriConnected
                ProfilePlatform.BANGUMI -> isBangumiConnected
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isLandscape) 18.dp else 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selector Pill (Brave / Explore tarzı açılır pencere butonu)
                ProfileSourceSelectorPill(
                    selectedPlatform = currentPlatform,
                    isConnected = isCurrentConnected,
                    onClick = { showSourcePickerSheet = true },
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.weight(1f))

                // 📊 İstatistik + 🔔 Bildirim → ortak üst çubuk aksiyon satırı.
                // Kanonik sıra: istatistik solda, bildirim her zaman en sağda.
                KitsugiTopActionRow(
                    actions = buildList {
                        if (onOpenWatchHistory != null) add(KitsugiTopAction.HISTORY)
                        if (onOpenDownloads != null) add(KitsugiTopAction.DOWNLOADS)
                        if (onOpenStatsClick != null) add(KitsugiTopAction.STATS)
                        if (isNotificationsVisible && onOpenNotifications != null) add(KitsugiTopAction.NOTIFICATIONS)
                    },
                    onActionClick = { action ->
                        when (action) {
                            KitsugiTopAction.HISTORY -> onOpenWatchHistory?.invoke()
                            KitsugiTopAction.DOWNLOADS -> onOpenDownloads?.invoke()
                            KitsugiTopAction.STATS -> onOpenStatsClick?.invoke()
                            KitsugiTopAction.NOTIFICATIONS -> onOpenNotifications?.invoke()
                            else -> Unit
                        }
                    }
                )
            }

            // ── Main content with pull-to-refresh ─────────────────────────
            val isProfileLoading = when (activeSubTab) {
                0 -> aniListState.isLoading
                1 -> malState.isLoading
                2 -> simklState.isLoading
                3 -> kitsuState.isLoading
                4 -> shikimoriState.isLoading
                5 -> bangumiState.isLoading
                else -> false
            }
            val pullRefreshState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = isProfileLoading,
                onRefresh = { viewModel.refreshActiveProfile() },
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = pullRefreshState,
                indicator = {
                    KitsugiPullToRefreshIndicator(
                        state = pullRefreshState,
                        isRefreshing = isProfileLoading,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (activeSubTab) {
                        0 -> ExternalProfileWrapper(
                            isConnected = isAniListConnected,
                            isLoading = aniListState.isLoading,
                            error = aniListState.error,
                            errorCode = aniListState.errorCode,
                            onConnectClick = onLoginAniList,
                            accentColor = accentColor,
                            platformName = "AniList",
                            onRetry = { viewModel.fetchAniListProfile(forceRefresh = true) }
                        ) {
                            AniListProfileContent(
                                viewModel = viewModel,
                                state = aniListState,
                                mediaEntries = mediaEntries,
                                appSettings = appSettings,
                                onEntryClick = onEntryClick,
                                onFavoriteMediaClick = onFavoriteMediaClick,
                                onFavoriteCharacterClick = onFavoriteCharacterClick,
                                onFavoriteStaffClick = onFavoriteStaffClick,
                                onLoadMoreActivities = { viewModel.loadNextAniListActivitiesPage() },
                                onOpenFavoriteSheet = openFavoriteSheet,
                                onOpenStatsClick = onOpenStatsClick,
                                onActivityClick = { actId -> activeActivityIdForDetail = actId },
                                onLikeClick = { actId ->
                                    coroutineScope.launch { apiClient.toggleLike(actId, "ACTIVITY") }
                                },
                                onDeleteClick = { actId -> showDeleteConfirmDialog = actId.toString() },
                                isLandscape = isLandscape,
                                onGenreClick = onGenreClick,
                                onTagClick = onTagClick,
                                onFavoriteStudioClick = onFavoriteStudioClick,
                                onUserProfileClick = onUserProfileClick,
                                accentColor = accentColor,
                                onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) },
                                isBottomBarVisible = isBottomBarVisible,
                                onScrollReset = onScrollReset
                            )
                        }
                        1 -> ExternalProfileWrapper(
                            isConnected = isMalConnected,
                            isLoading = malState.isLoading,
                            error = malState.error,
                            errorCode = malState.errorCode,
                            onConnectClick = onLoginMal,
                            accentColor = accentColor,
                            platformName = "MyAnimeList",
                            onRetry = { viewModel.fetchMalProfile(forceRefresh = true) }
                        ) {
                            MalProfileContent(
                                viewModel = viewModel,
                                state = malState,
                                mediaEntries = mediaEntries,
                                appSettings = appSettings,
                                onEntryClick = onEntryClick,
                                onFavoriteMediaClick = onFavoriteMediaClick,
                                onFavoriteCharacterClick = onFavoriteCharacterClick,
                                onFavoriteStaffClick = onFavoriteStaffClick,
                                onOpenFavoriteSheet = openFavoriteSheet,
                                onOpenStatsClick = onOpenStatsClick,
                                isLandscape = isLandscape,
                                accentColor = accentColor,
                                onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) },
                                onFavoriteStudioClick = onFavoriteStudioClick,
                                onGenreClick = onGenreClick,
                                onUserProfileClick = onMalUserProfileClick,
                                onLoadMoreFriends = { viewModel.loadMoreMalFriends() },
                                onLoadMoreReviews = { viewModel.loadMoreMalReviews() },
                                onLoadMoreRecommendations = { viewModel.loadMoreMalRecommendations() },
                                onLoadMoreClubs = { viewModel.loadMoreMalClubs() }
                            )
                        }
                        2 -> ExternalProfileWrapper(
                            isConnected = isSimklConnected,
                            isLoading = simklState.isLoading,
                            error = simklState.error,
                            errorCode = simklState.errorCode,
                            onConnectClick = onLoginSimkl,
                            accentColor = accentColor,
                            platformName = "Simkl",
                            onRetry = { viewModel.fetchSimklProfile(forceRefresh = true) }
                        ) {
                            SimklProfileContent(
                                viewModel = viewModel,
                                state = simklState,
                                mediaEntries = mediaEntries,
                                appSettings = appSettings,
                                onEntryClick = onEntryClick,
                                onFavoriteMediaClick = onFavoriteMediaClick,
                                onOpenFavoriteSheet = openFavoriteSheet,
                                onOpenStatsClick = onOpenStatsClick,
                                isLandscape = isLandscape,
                                accentColor = accentColor,
                                onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) }
                            )
                        }
                        3 -> ExternalProfileWrapper(
                            isConnected = isKitsuConnected,
                            isLoading = kitsuState.isLoading,
                            error = kitsuState.error,
                            errorCode = kitsuState.errorCode,
                            onConnectClick = { if (onKitsuAuthSubmit != null) showKitsuLoginDialog = true else onLoginKitsu() },
                            accentColor = accentColor,
                            platformName = "Kitsu",
                            onRetry = { viewModel.fetchKitsuProfile(forceRefresh = true) }
                        ) {
                            KitsuProfileContent(
                                viewModel = viewModel,
                                state = kitsuState,
                                mediaEntries = mediaEntries,
                                appSettings = appSettings,
                                onEntryClick = onEntryClick,
                                onFavoriteMediaClick = onFavoriteMediaClick,
                                onOpenFavoriteSheet = openFavoriteSheet,
                                onOpenStatsClick = onOpenStatsClick,
                                isLandscape = isLandscape,
                                accentColor = accentColor,
                                onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) }
                            )
                        }
                        5 -> ExternalProfileWrapper(
                            isConnected = isBangumiConnected,
                            isLoading = bangumiState.isLoading,
                            error = bangumiState.error,
                            errorCode = bangumiState.errorCode,
                            onConnectClick = { if (onBangumiAuthSubmit != null) showBangumiLoginDialog = true else onLoginBangumi() },
                            accentColor = accentColor,
                            platformName = "Bangumi",
                            onRetry = { viewModel.fetchBangumiProfile(forceRefresh = true) }
                        ) {
                            BangumiProfileContent(
                                state = bangumiState,
                                accentColor = accentColor,
                                titleLanguage = appSettings.titleLanguage,
                                onOpenAnimeOrManga = { subjectId, mediaType, title, imageUrl ->
                                    val stableId = com.kitsugi.animelist.data.remote.BangumiIdNamespace.stableIdFromRaw(subjectId)
                                    if (stableId != null) {
                                        onFavoriteMediaClick(stableId, mediaType, "bangumi", title, imageUrl)
                                    }
                                },
                                onCharacterClick = { charId, charName, charImageUrl ->
                                    onFavoriteCharacterClick(charId, "bangumi", charName, charImageUrl)
                                },
                                onPersonClick = { personId, personName, personImageUrl ->
                                    onFavoriteStaffClick(personId, "bangumi", personName, personImageUrl)
                                }
                            )
                        }
                        4 -> ExternalProfileWrapper(
                            isConnected = isShikimoriConnected,
                            isLoading = shikimoriState.isLoading,
                            error = shikimoriState.error,
                            errorCode = shikimoriState.errorCode,
                            onConnectClick = { if (onShikimoriAuthSubmit != null) showShikimoriLoginDialog = true else onLoginShikimori() },
                            accentColor = accentColor,
                            platformName = "Shikimori",
                            onRetry = { viewModel.fetchShikimoriProfile(forceRefresh = true) }
                        ) {
                            ShikimoriProfileContent(
                                viewModel = viewModel,
                                state = shikimoriState,
                                mediaEntries = mediaEntries,
                                appSettings = appSettings,
                                onEntryClick = onEntryClick,
                                onFavoriteMediaClick = onFavoriteMediaClick,
                                onOpenFavoriteSheet = openFavoriteSheet,
                                onOpenStatsClick = onOpenStatsClick,
                                isLandscape = isLandscape,
                                accentColor = accentColor,
                                onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Overlays / Dialogs ─────────────────────────────────────────────────
    activeFavoriteSheet?.let { sheet ->
        val filterTitle = sheet.first
        val currentFavCategory = when (viewModel.aniListFavoritesFilter) {
            0 -> "anime"; 1 -> "manga"; 2 -> "characters"; 3 -> "staff"; 4 -> "studios"; else -> "anime"
        }
        val currentFavList = if (activeSubTab == 0) {
            when (viewModel.aniListFavoritesFilter) {
                0 -> aniListState.favoriteAnime; 1 -> aniListState.favoriteManga
                2 -> aniListState.favoriteCharacters; 3 -> aniListState.favoriteStaff
                4 -> aniListState.favoriteStudios; else -> sheet.second
            }
        } else { sheet.second }
        val currentHasNext = if (activeSubTab == 0) {
            when (viewModel.aniListFavoritesFilter) {
                0 -> aniListState.favAnimeHasNext; 1 -> aniListState.favMangaHasNext
                2 -> aniListState.favCharHasNext; 3 -> aniListState.favStaffHasNext
                4 -> aniListState.favStudioHasNext; else -> false
            }
        } else false

        FavoritesExpandedBottomSheet(
            title = filterTitle,
            items = currentFavList,
            blurAdultMedia = appSettings.blurAdultMedia,
            hasNextPage = currentHasNext,
            onLoadMore = { viewModel.loadMoreFavorites(currentFavCategory) },
            onItemClick = { item -> onSheetItemClick?.invoke(item) },
            onDismiss = { activeFavoriteSheet = null; onSheetItemClick = null }
        )
    }

    activeActivityIdForDetail?.let { actId ->
        KitsugiActivityDetailBottomSheet(
            activityId = actId,
            apiClient = apiClient,
            titleLanguage = appSettings.titleLanguage.toString(),
            blurAdultMedia = appSettings.blurAdultMedia,
            onMediaClick = { mediaId, mType, source -> onFavoriteMediaClick(mediaId, mType, source, "", null) },
            onDismiss = { activeActivityIdForDetail = null; viewModel.fetchAniListProfile() }
        )
    }

    showDeleteConfirmDialog?.let { actIdStr ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(stringResource(R.string.aktiviteyi_sil), color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.inline_are_you_sure_you_want_d2a0328), color = KitsugiColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val actId = actIdStr.toIntOrNull() ?: 0
                        val success = apiClient.deleteActivity(actId)
                        if (success) viewModel.fetchAniListProfile()
                    }
                    showDeleteConfirmDialog = null
                }) {
                    Text(stringResource(R.string.action_delete), color = KitsugiColors.AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text(stringResource(R.string.iptal), color = KitsugiColors.TextMuted)
                }
            },
            containerColor = KitsugiColors.Surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    activeGalleryImages?.let { (urls, initialIdx, title) ->
        KitsugiImageGalleryDialog(
            imageUrls = urls,
            initialIndex = initialIdx,
            title = title,
            onDismiss = { activeGalleryImages = null }
        )
    }

    if (showSourcePickerSheet) {
        ProfileSourcePickerSheet(
            selectedPlatformIndex = activeSubTab,
            isAniListConnected = isAniListConnected,
            isMalConnected = isMalConnected,
            isSimklConnected = isSimklConnected,
            isKitsuConnected = isKitsuConnected,
            isShikimoriConnected = isShikimoriConnected,
            aniListUsername = aniListState.name.ifBlank { null },
            malUsername = malState.name.ifBlank { null },
            simklUsername = simklState.name.ifBlank { null },
            kitsuUsername = kitsuState.name.ifBlank { null },
            shikimoriUsername = shikimoriState.name.ifBlank { null },
            isBangumiConnected = isBangumiConnected,
            bangumiUsername = bangumiState.userName.ifBlank { null },
            onSelectPlatform = { index ->
                viewModel.activeSubTab = index
                showSourcePickerSheet = false
            },
            onDismiss = { showSourcePickerSheet = false }
        )
    }

    if (showBangumiLoginDialog && onBangumiAuthSubmit != null) {
        KitsugiBangumiLoginDialog(
            onDismiss = { showBangumiLoginDialog = false },
            onLogin = { clientId, clientSecret, authCode, callback ->
                onBangumiAuthSubmit(clientId, clientSecret, authCode) { success, error ->
                    callback(success, error)
                    if (success) {
                        showBangumiLoginDialog = false
                        viewModel.fetchBangumiProfile(forceRefresh = true)
                    }
                }
            }
        )
    }

    if (showKitsuLoginDialog && onKitsuAuthSubmit != null) {
        KitsugiKitsuLoginDialog(
            onDismiss = { showKitsuLoginDialog = false },
            onLogin = { username, password, callback ->
                onKitsuAuthSubmit(username, password) { success, error ->
                    callback(success, error)
                    if (success) {
                        showKitsuLoginDialog = false
                        viewModel.fetchKitsuProfile(forceRefresh = true)
                    }
                }
            }
        )
    }

    if (showShikimoriLoginDialog && onShikimoriAuthSubmit != null) {
        KitsugiShikimoriLoginDialog(
            onDismiss = { showShikimoriLoginDialog = false },
            onLogin = { clientId, clientSecret, authCode, callback ->
                onShikimoriAuthSubmit(clientId, clientSecret, authCode) { success, error ->
                    callback(success, error)
                    if (success) {
                        showShikimoriLoginDialog = false
                        viewModel.fetchShikimoriProfile(forceRefresh = true)
                    }
                }
            }
        )
    }
}
