package com.kitsugi.animelist

import com.kitsugi.animelist.utils.KitsugiStrings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.ui.app.AppViewModel
import com.kitsugi.animelist.ui.app.AuthViewModel
import com.kitsugi.animelist.ui.app.ProfileViewModel
import com.kitsugi.animelist.ui.app.PlayerSettingsViewModel
import com.kitsugi.animelist.ui.app.AddonViewModel
import com.kitsugi.animelist.ui.app.MangaViewModel
import com.kitsugi.animelist.ui.screens.explore.ExploreViewModel
import com.kitsugi.animelist.ui.screens.search.SearchViewModel
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.local.CloudstreamRepoEntity
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.ui.navigation.MainTab
import com.kitsugi.animelist.ui.screens.explore.ExploreScreen
import com.kitsugi.animelist.ui.screens.mylist.MyListScreen
import com.kitsugi.animelist.ui.screens.search.SearchScreen
import com.kitsugi.animelist.ui.components.BackupImportMode
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

data class TabPagesContext(
    val mediaEntries: List<MediaEntry>,
    val appSettings: AppSettings,
    val settingsDataStore: SettingsDataStore,
    val appViewModel: AppViewModel,
    val exploreViewModel: ExploreViewModel,
    val searchViewModel: SearchViewModel,
    val authViewModel: AuthViewModel,
    val profileViewModel: ProfileViewModel,
    val kitsugiProfileViewModel: KitsugiProfileViewModel,
    val playerSettingsViewModel: PlayerSettingsViewModel,
    val updateViewModel: com.kitsugi.animelist.core.update.AppUpdateViewModel,
    val addonViewModel: AddonViewModel,
    val mangaViewModel: MangaViewModel,
    val mediaRepository: MediaEntryRepository,
    val coroutineScope: CoroutineScope,
    val navState: AppNavigationState,
    val backupText: String,
    val importText: String,
    val importMode: BackupImportMode,
    val onPickProfileImageClick: () -> Unit,
    val onPickBannerImageClick: () -> Unit,
    val onExportBackupFileClick: () -> Unit,
    val onImportBackupFileClick: () -> Unit,
    val onOpenApiDetail: (JikanSearchResult) -> Unit,
    val onAddApiSelectionToList: (ApiSearchSelection) -> Unit,
    val onSeeAllSection: (String, ExploreCategoryType, List<JikanSearchResult>, ExplorePlatform) -> Unit,
    val onNavigateToWatchHistory: () -> Unit,
    val onOpenMangaReader: () -> Unit,
    val onEditEntry: (MediaEntry) -> Unit,
    val onSearchByGenre: (String) -> Unit = {},
    val onSearchByTag: (String) -> Unit = {},
    val bottomBarScrollState: com.kitsugi.animelist.utils.ScrollVisibilityState,
    val onScrollReset: () -> Unit = {}
)

@Composable
fun AppRootTabPages(
    key: AppStateKey.Tab,
    ctx: TabPagesContext
) {
    val context = LocalContext.current

    val addonsList by ctx.addonViewModel.addonsList.collectAsState(initial = emptyList())
    val reposList by ctx.addonViewModel.reposList.collectAsState(initial = emptyList())
    val csPluginsList by ctx.addonViewModel.csPluginsList.collectAsState(initial = emptyList())

    when (key.tab) {
        MainTab.Explore -> {
            ctx.navState.stateHolder.SaveableStateProvider(key = "root_tab_explore") {
                ExploreTabPage(ctx)
            }
        }

        MainTab.MyList -> {
            ctx.navState.stateHolder.SaveableStateProvider(key = "root_tab_mylist") {
                MyListTabPageWrapper(
                    appSettings = ctx.appSettings,
                    settingsDataStore = ctx.settingsDataStore,
                    coroutineScope = ctx.coroutineScope,
                    mediaEntries = ctx.mediaEntries,
                    mediaRepository = ctx.mediaRepository,
                    appViewModel = ctx.appViewModel,
                    authViewModel = ctx.authViewModel,
                    navState = ctx.navState,
                    context = context,
                    isBottomBarVisible = ctx.bottomBarScrollState.isVisible,
                    onScrollReset = ctx.onScrollReset
                )
            }
        }

        MainTab.Search -> {
            ctx.navState.stateHolder.SaveableStateProvider(key = "root_tab_search") {
                SearchTabPage(ctx)
            }
        }

        MainTab.Profile -> {
            ctx.navState.stateHolder.SaveableStateProvider(key = "root_tab_profile") {
                com.kitsugi.animelist.ui.screens.profile.KitsugiProfileScreen(
                    viewModel = ctx.kitsugiProfileViewModel,
                    mediaEntries = ctx.mediaEntries,
                    isAniListConnected = ctx.authViewModel.isAniListConnected,
                    isMalConnected = ctx.authViewModel.isMalConnected,
                    isSimklConnected = ctx.authViewModel.isSimklConnected,
                    isKitsuConnected = ctx.authViewModel.isKitsuConnected,
                    isShikimoriConnected = ctx.authViewModel.isShikimoriConnected,
                    isBangumiConnected = ctx.authViewModel.isBangumiConnected,
                    profileName = ctx.appSettings.profileName,
                    listTitle = ctx.appSettings.listTitle,
                    profileImageUri = ctx.appSettings.profileImageUri,
                    bannerImageUri = ctx.appSettings.bannerImageUri,
                    appSettings = ctx.appSettings,
                    onEntryClick = { entry ->
                        ctx.navState.navigateToDetail(DetailScreen.MediaDetail(entry.id))
                    },
                    onOpenSettingsClick = {
                        ctx.appViewModel.selectTab(MainTab.Settings)
                    },
                    onLoginAniList = { ctx.authViewModel.startExternalAuth("anilist") },
                    onLoginMal = { ctx.authViewModel.startMalOAuthWebSession() },
                    onLoginSimkl = { ctx.authViewModel.startExternalAuth("simkl") },
                    onLoginKitsu = {},
                    onLoginShikimori = {},
                    onKitsuAuthSubmit = { username, password, onComplete ->
                        ctx.authViewModel.loginKitsu(
                            username = username,
                            password = password,
                            onSuccess = { onComplete(true, null) },
                            onError = { onComplete(false, it) }
                        )
                    },
                    onShikimoriAuthSubmit = { clientId, clientSecret, authCode, onComplete ->
                        ctx.authViewModel.loginShikimori(
                            clientId = clientId,
                            clientSecret = clientSecret,
                            authCode = authCode,
                            onSuccess = { onComplete(true, null) },
                            onError = { onComplete(false, it) }
                        )
                    },
                    onBangumiAuthSubmit = { clientId, clientSecret, authCode, onComplete ->
                        ctx.authViewModel.loginBangumi(
                            clientId = clientId,
                            clientSecret = clientSecret,
                            authCode = authCode,
                            onSuccess = { onComplete(true, null) },
                            onError = { onComplete(false, it) }
                        )
                    },
                    onFavoriteMediaClick = { mediaId, mediaType, source, title, imageUrl ->
                        // AniList kaynaklı anime/manga favorilerinde gelen ID direkt AniList ID'sidir.
                        // KitsugiAniListDetailClient 100M+ offset'e göre id: vs idMal: ayrımı yapar.
                        // Offset eklenerek doğru sorgulama sağlanır.
                        val stableId = if (source.equals("anilist", ignoreCase = true)) mediaId + 100_000_000 else mediaId
                        // Favori API verisindeki adult bilgisini de taşı: içerik yerel listede
                        // olmasa bile açılış animasyonundaki poster ilk kareden itibaren bulanık kalmalı.
                        val profileFavoriteAdult = if (source.equals("anilist", ignoreCase = true)) {
                            val profileState = ctx.kitsugiProfileViewModel.aniListState.value
                            val favorites = if (mediaType == MediaType.Manga) {
                                profileState.favoriteManga
                            } else {
                                profileState.favoriteAnime
                            }
                            favorites.firstOrNull { it.id.toIntOrNull() == mediaId }?.isAdult
                        } else {
                            null
                        }
                        val resolvedIsAdult = profileFavoriteAdult ?: ctx.mediaEntries.firstOrNull { entry ->
                            entry.malId == stableId && entry.source.equals(source, ignoreCase = true)
                        }?.isAdult ?: false
                        val result = com.kitsugi.animelist.data.remote.JikanSearchResult(
                            malId = stableId,
                            title = title.ifBlank { KitsugiStrings.get(R.string.loading) },
                            subtitle = "",
                            type = mediaType,
                            total = null,
                            score = null,
                            isAdult = resolvedIsAdult,
                            imageUrl = imageUrl,
                            year = null,
                            source = source,
                            bangumiSubjectType = if (canonicalMediaSourceId(source) == "bangumi") {
                                BangumiIdNamespace.subjectTypeFor(mediaType)
                            } else null
                        )
                        ctx.navState.navigateToDetail(DetailScreen.ApiResultDetail(result))
                    },
                    onFavoriteCharacterClick = { charId, source, name, imageUrl ->
                        ctx.navState.navigateToDetail(DetailScreen.CharacterDetail(charId, source, name, imageUrl))
                    },
                    onFavoriteStaffClick = { staffId, source, name, imageUrl ->
                        ctx.navState.navigateToDetail(DetailScreen.StaffDetail(staffId, source, name, imageUrl))
                    },
                    onFavoriteStudioClick = { studioId, source, name, imageUrl ->
                        ctx.navState.navigateToDetail(DetailScreen.StudioDetail(studioId, source, name, imageUrl))
                    },
                    onOpenStatsClick = {
                        ctx.navState.navigateToDetail(DetailScreen.Stats)
                    },
                    isNotificationsVisible = ctx.authViewModel.isAniListConnected || ctx.authViewModel.isMalConnected || ctx.authViewModel.isSimklConnected,
                    onOpenNotifications = { ctx.navState.navigateToDetail(DetailScreen.Notifications) },
                    onOpenWatchHistory = { ctx.navState.navigateToDetail(DetailScreen.WatchHistory) },
                    onOpenDownloads = { ctx.navState.navigateToDetail(DetailScreen.Downloads) },
                    onGenreClick = ctx.onSearchByGenre,
                    onTagClick = ctx.onSearchByTag,
                    onUserProfileClick = { userId, username, avatarUrl ->
                        ctx.navState.navigateToDetail(DetailScreen.UserProfile(userId, username, avatarUrl))
                    },
                    onMalUserProfileClick = { userId, username, avatarUrl ->
                        ctx.navState.navigateToDetail(DetailScreen.UserProfile(userId, username, avatarUrl, "mal"))
                    },
                    isBottomBarVisible = ctx.bottomBarScrollState.isVisible,
                    onScrollReset = ctx.onScrollReset
                )
            }
        }

        MainTab.Settings -> {
            ctx.navState.stateHolder.SaveableStateProvider(key = "root_tab_settings") {
                SettingsTabPage(
                    addonsList = addonsList,
                    reposList = reposList,
                    csPluginsList = csPluginsList,
                    ctx = ctx,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun ExploreTabPage(ctx: TabPagesContext) {
    ExploreScreen(
        currentEntries = ctx.mediaEntries,
        showAdultContent = ctx.appSettings.showAdultContent,
        blurAdultMedia = ctx.appSettings.blurAdultMedia,
        onAddSelectionToList = ctx.onAddApiSelectionToList,
        onSeeAllSection = ctx.onSeeAllSection,
        onNavigateToWatchHistory = ctx.onNavigateToWatchHistory,
        onNavigateToDownloads = { ctx.navState.navigateToDetail(DetailScreen.Downloads) },
        onOpenApiDetail = ctx.onOpenApiDetail,
        onOpenMangaReader = ctx.onOpenMangaReader,
        onEditEntry = ctx.onEditEntry,
        onOpenAiringCalendar = {
            val preferredSource = when (ctx.exploreViewModel.selectedPlatform) {
                // Tümü: takvim tüm kaynakların birleşimi (AniList + TMDB + ...)
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.ALL -> "all"
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.MAL -> "jikan"
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.AniList -> "anilist"
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.TMDB -> "tmdb"
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.KITSU -> "kitsu"
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.SHIKIMORI -> "shikimori"
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.SIMKL -> "simkl"
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.BANGUMI -> "bangumi"
            }
            ctx.navState.navigateToDetail(DetailScreen.AiringCalendar(preferredSource))
        },
        initialScrollIndex = ctx.appViewModel.exploreScrollIndex,
        initialScrollOffset = ctx.appViewModel.exploreScrollOffset,
        onScrollPositionChange = { index, offset ->
            ctx.appViewModel.updateExploreScrollPosition(index, offset)
        },
        // Görünürlük state holder olarak geçirilir; burada okumak tüm sayfayı recompose eder.
        bottomBarScrollState = ctx.bottomBarScrollState,
        onScrollReset = ctx.onScrollReset,
        viewModel = ctx.exploreViewModel,
        titleLanguage = ctx.appSettings.titleLanguage,
        scoreFormat = ctx.appSettings.scoreFormat,
        hideScores = ctx.appSettings.hideScores,
        separateNovelsManga = ctx.appSettings.separateNovelsManga,
        onOpenNotifications = { ctx.navState.navigateToDetail(DetailScreen.Notifications) },
        isNotificationsVisible = ctx.authViewModel.isAniListConnected || ctx.authViewModel.isMalConnected || ctx.authViewModel.isSimklConnected,
        showAnimeLogos = ctx.appSettings.showAnimeLogos,
        isSimklConnected = ctx.authViewModel.isSimklConnected,
        // TMDB hata yönlendirmesi: Ayarlar → Entegrasyonlar bölümüne git
        onRedirectToSettings = {
            ctx.appViewModel.selectTab(MainTab.Settings)
        },
        // AniList / MAL hata yönlendirmesi: ilgili platform OAuth akışını başlat
        onRedirectToAuth = {
            val platform = ctx.exploreViewModel.selectedPlatform
            val provider = when (platform) {
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.AniList -> "anilist"
                com.kitsugi.animelist.ui.screens.explore.ExplorePlatform.MAL     -> "mal"
                else                                                              -> "anilist"
            }
            if (provider == "mal") ctx.authViewModel.startMalOAuthWebSession()
            else ctx.authViewModel.startExternalAuth(provider)
        }
    )
}


@Composable
private fun SearchTabPage(ctx: TabPagesContext) {
    SearchScreen(
        currentEntries = ctx.mediaEntries,
        showAdultContent = ctx.appSettings.showAdultContent,
        onOpenApiDetail = ctx.onOpenApiDetail,
        onAddSelectionToList = ctx.onAddApiSelectionToList,
        viewModel = ctx.searchViewModel,
        titleLanguage = ctx.appSettings.titleLanguage,
        staffNameLanguage = ctx.appSettings.staffNameLanguage,
        scoreFormat = ctx.appSettings.scoreFormat,
        hideScores = ctx.appSettings.hideScores,
        bottomBarScrollState = ctx.bottomBarScrollState,
        onScrollReset = ctx.onScrollReset,
        isNotificationsVisible = ctx.authViewModel.isAniListConnected || ctx.authViewModel.isMalConnected || ctx.authViewModel.isSimklConnected,
        onOpenNotifications = { ctx.navState.navigateToDetail(DetailScreen.Notifications) },
        onOpenWatchHistory = { ctx.navState.navigateToDetail(DetailScreen.WatchHistory) },
        onOpenDownloads = { ctx.navState.navigateToDetail(DetailScreen.Downloads) },
        onOpenSourceSearch = { engine, scope, shelfResults ->
            // "Tümünü Gör": kaynağın filtreleri + sonuçları ayrı sayfada açılır
            ctx.navState.navigateToDetail(
                DetailScreen.SourceSearchPage(
                    engine = engine,
                    query = ctx.searchViewModel.uiState.value.query,
                    scope = scope,
                    shelfResults = shelfResults
                )
            )
        },
        onSeeAllAddonSection = { apiName, title, mainPageData, horizontalImages, initialItems ->
            ctx.navState.addonFullScreenGridState = com.kitsugi.animelist.ui.app.AddonFullScreenGridState(
                title = title,
                apiName = apiName,
                initialItems = initialItems,
                mainPageData = mainPageData,
                horizontalImages = horizontalImages
            )
            // Tam ekran grid açılırken dialog state'ini koru — geri gelince yeniden açılır
        },
        addonExploreOpen = ctx.navState.addonExploreOpen,
        onAddonExploreOpenChange = { open -> ctx.navState.addonExploreOpen = open },
        onOpenPluginPicker = { ctx.navState.navigateToDetail(com.kitsugi.animelist.DetailScreen.PluginPicker) },
        onOpenAddonExplore = { apiName -> ctx.navState.navigateToDetail(com.kitsugi.animelist.DetailScreen.AddonExplore(apiName)) },
        onOpenCharacterDetail = { characterId, name, imageUrl ->
            ctx.navState.navigateToDetail(
                com.kitsugi.animelist.DetailScreen.CharacterDetail(
                    characterId = characterId,
                    source = "anilist",
                    name = name,
                    imageUrl = imageUrl
                )
            )
        },
        onOpenStaffDetail = { staffId, name, imageUrl ->
            ctx.navState.navigateToDetail(
                com.kitsugi.animelist.DetailScreen.StaffDetail(
                    staffId = staffId,
                    source = "anilist",
                    name = name,
                    imageUrl = imageUrl
                )
            )
        },
        onOpenStudioDetail = { studioId, source, name, imageUrl ->
            ctx.navState.navigateToDetail(
                com.kitsugi.animelist.DetailScreen.StudioDetail(
                    studioId = studioId,
                    source = source,
                    name = name,
                    imageUrl = imageUrl
                )
            )
        }
    )
}

@Composable
private fun SettingsTabPage(
    addonsList: List<ManagedAddonEntity>,
    reposList: List<CloudstreamRepoEntity>,
    csPluginsList: List<CsPluginEntity>,
    ctx: TabPagesContext,
    context: android.content.Context
) {
    SettingsScreenContent(
        ctx = SettingsContext(
            addonsList = addonsList,
            addonViewModel = ctx.addonViewModel,
            reposList = reposList,
            csPluginsList = csPluginsList,
            appSettings = ctx.appSettings,
            settingsDataStore = ctx.settingsDataStore,
            coroutineScope = ctx.coroutineScope,
            appViewModel = ctx.appViewModel,
            mediaEntries = ctx.mediaEntries,
            backupText = ctx.backupText,
            importText = ctx.importText,
            importMode = ctx.importMode,
            authViewModel = ctx.authViewModel,
            mediaRepository = ctx.mediaRepository,
            playerSettingsViewModel = ctx.playerSettingsViewModel,
            updateViewModel = ctx.updateViewModel,
            mangaViewModel = ctx.mangaViewModel,
            onPickProfileImageClick = ctx.onPickProfileImageClick,
            onPickBannerImageClick = ctx.onPickBannerImageClick,
            onExportBackupFileClick = ctx.onExportBackupFileClick,
            onImportBackupFileClick = ctx.onImportBackupFileClick,
            context = context,
            navState = ctx.navState
        )
    )
}

@Composable
private fun MyListTabPageWrapper(
    appSettings: AppSettings,
    settingsDataStore: SettingsDataStore,
    coroutineScope: CoroutineScope,
    mediaEntries: List<MediaEntry>,
    mediaRepository: MediaEntryRepository,
    appViewModel: AppViewModel,
    authViewModel: AuthViewModel,
    navState: AppNavigationState,
    context: android.content.Context,
    isBottomBarVisible: Boolean,
    onScrollReset: () -> Unit
) {
    MyListScreen(
        selectedListLayoutId = appSettings.selectedListLayoutId,
        onListLayoutChange = { layoutId ->
            coroutineScope.launch {
                settingsDataStore.setSelectedListLayoutId(layoutId)
            }
        },
        showAdultContent = appSettings.showAdultContent,
        appSettings = appSettings,
        searchQuery = appViewModel.myListSearchQuery,
        selectedStatusFilterId = appViewModel.myListStatusFilterId,
        selectedTypeFilterId = appViewModel.myListTypeFilterId,
        selectedFavoriteFilterId = appViewModel.myListFavoriteFilterId,
        selectedScoreFilterId = appViewModel.myListScoreFilterId,
        selectedYearFilterId = appViewModel.myListYearFilterId,
        selectedExtraFilterId = appViewModel.myListExtraFilterId,
        selectedSortId = appViewModel.myListSortId,
        initialScrollIndex = appViewModel.myListScrollIndex,
        initialScrollOffset = appViewModel.myListScrollOffset,
        selectedTabIndex = appViewModel.myListTabIndex,
        onTabIndexChange = { appViewModel.updateMyListTabIndex(context, it) },
        onSearchQueryChange = { appViewModel.updateMyListSearchQuery(it) },
        onStatusFilterChange = { appViewModel.updateMyListStatusFilter(context, it) },
        onTypeFilterChange = { appViewModel.updateMyListTypeFilter(context, it) },
        onFavoriteFilterChange = { appViewModel.updateMyListFavoriteFilter(context, it) },
        onScoreFilterChange = { appViewModel.updateMyListScoreFilter(context, it) },
        onYearFilterChange = { appViewModel.updateMyListYearFilter(context, it) },
        onExtraFilterChange = { appViewModel.updateMyListExtraFilter(context, it) },
        onSortChange = { appViewModel.updateMyListSort(context, it) },
        onScrollPositionChange = { index, offset ->
            appViewModel.updateMyListScrollPosition(index, offset)
        },
        onExternalSyncMessage = { appViewModel.showSnackbarMessage(it) },
        isAniListConnected = authViewModel.isAniListConnected,
        isMalConnected = authViewModel.isMalConnected,
        isSimklConnected = authViewModel.isSimklConnected,
        isSimklSessionExpired = authViewModel.isSimklSessionExpired,
        isKitsuConnected = authViewModel.isKitsuConnected,
        isShikimoriConnected = authViewModel.isShikimoriConnected,
        isBangumiConnected = authViewModel.isBangumiConnected,
        onLoginAniList = { authViewModel.startExternalAuth("anilist") },
        onLoginMal = { authViewModel.startMalOAuthWebSession() },
        onLoginSimkl = { authViewModel.startExternalAuth("simkl") },
        onSyncAniList = { authViewModel.importAniListAnimeList(mediaEntries, mediaRepository) },
        onSyncMal = { authViewModel.importMalAnimeList(mediaEntries, mediaRepository) },
        onSyncSimkl = { authViewModel.importSimklList(mediaEntries, mediaRepository) },
        onSyncKitsu = { authViewModel.importKitsuList(mediaEntries, mediaRepository) },
        onSyncShikimori = { authViewModel.importShikimoriList(mediaEntries, mediaRepository) },
        onSyncBangumi = { authViewModel.importBangumiList(mediaEntries, mediaRepository) },
        onKitsuAuthSubmit = { username, password, onComplete ->
            authViewModel.loginKitsu(
                username = username,
                password = password,
                onSuccess = { onComplete(true, null) },
                onError = { onComplete(false, it) }
            )
        },
        onShikimoriAuthSubmit = { clientId, clientSecret, authCode, onComplete ->
            authViewModel.loginShikimori(
                clientId = clientId,
                clientSecret = clientSecret,
                authCode = authCode,
                onSuccess = { onComplete(true, null) },
                onError = { onComplete(false, it) }
            )
        },
        onBangumiAuthSubmit = { clientId, clientSecret, authCode, onComplete ->
            authViewModel.loginBangumi(
                clientId = clientId,
                clientSecret = clientSecret,
                authCode = authCode,
                onSuccess = { onComplete(true, null) },
                onError = { onComplete(false, it) }
            )
        },
        onEntryClick = { entry ->
            navState.navigateToDetail(DetailScreen.MediaDetail(entry.id))
        },
        onSettingsClick = {
            appViewModel.selectTab(MainTab.Settings)
        },
        isBottomBarVisible = isBottomBarVisible,
        onScrollReset = onScrollReset,
        isNotificationsVisible = authViewModel.isAniListConnected || authViewModel.isMalConnected || authViewModel.isSimklConnected,
        onOpenNotifications = { navState.navigateToDetail(DetailScreen.Notifications) },
        onOpenWatchHistory = { navState.navigateToDetail(DetailScreen.WatchHistory) },
        onOpenDownloads = { navState.navigateToDetail(DetailScreen.Downloads) }
    )
}
