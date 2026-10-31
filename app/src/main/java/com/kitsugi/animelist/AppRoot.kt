package com.kitsugi.animelist

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.components.KitsugiMotion
import com.kitsugi.animelist.ui.components.detailEnterContentTransform
import com.kitsugi.animelist.ui.components.detailExitContentTransform
import com.kitsugi.animelist.ui.components.tabContentTransform
import android.content.res.Configuration
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.theme.gradient.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalDensity
import com.kitsugi.animelist.utils.rememberScrollVisibilityState
import com.kitsugi.animelist.utils.rememberScrollConnection
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.local.MediaEntryBackup
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.NetworkMonitor
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.CloudstreamRepoEntity
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.remote.CsPlugin
import com.kitsugi.animelist.data.repository.CloudstreamRepoRepository
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.matches
import com.kitsugi.animelist.data.remote.matchesInSource
import com.kitsugi.animelist.data.remote.firstMatchingInSource
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.data.remote.BangumiMediaEntryFactory
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.EntryAutoRules
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.app.AppDialogHost
import com.kitsugi.animelist.ui.app.AppViewModel
import com.kitsugi.animelist.ui.app.AuthViewModel
import com.kitsugi.animelist.ui.app.PlayerSettingsViewModel
import com.kitsugi.animelist.ui.app.ProfileViewModel
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.app.AddonViewModel
import com.kitsugi.animelist.ui.app.MangaViewModel
import com.kitsugi.animelist.ui.app.rememberAppNavigationState
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.DetailScreen
import com.kitsugi.animelist.ui.app.FullScreenMediaGridState
import com.kitsugi.animelist.ui.components.KitsugiApiSearchDialog
import com.kitsugi.animelist.ui.components.KitsugiConfirmDialog
import com.kitsugi.animelist.ui.components.KitsugiMediaEntryEditorDialog
import com.kitsugi.animelist.ui.components.KitsugiBangumiLoginDialog
import com.kitsugi.animelist.ui.components.KitsugiMediaGridDialog
import com.kitsugi.animelist.ui.components.KitsugiUpdateDialog
import com.kitsugi.animelist.core.update.AppUpdateViewModel
import com.kitsugi.animelist.ui.navigation.AppBottomBar
import com.kitsugi.animelist.ui.navigation.AppNavigationRail
import com.kitsugi.animelist.ui.navigation.MainTab
import com.kitsugi.animelist.ui.screens.anime.AnimeScreen
import com.kitsugi.animelist.ui.screens.detail.ApiResultDetailPage
import com.kitsugi.animelist.ui.screens.detail.MediaEntryDetailPage
import com.kitsugi.animelist.ui.screens.detail.CharacterDetailPage
import com.kitsugi.animelist.ui.screens.detail.StaffDetailPage
import com.kitsugi.animelist.ui.screens.detail.StudioDetailPage
import com.kitsugi.animelist.ui.screens.explore.ExploreScreen
import com.kitsugi.animelist.ui.screens.explore.ExploreViewModel
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.generateExploreEntryMap
import com.kitsugi.animelist.ui.screens.explore.getMediaEntryFromMap
import com.kitsugi.animelist.ui.screens.fullscreen.FullScreenMediaGridPage
import com.kitsugi.animelist.ui.screens.mylist.MyListScreen
import com.kitsugi.animelist.ui.screens.search.SearchScreen
import com.kitsugi.animelist.ui.screens.search.SearchViewModel
import com.kitsugi.animelist.ui.screens.search.DetailSearchFilterRequest
import com.kitsugi.animelist.ui.screens.settings.SettingsScreen
import com.kitsugi.animelist.data.manga.MangaExtensionLoader
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.remote.MangaExtensionInfo
import com.kitsugi.animelist.data.remote.MangaRepoClient
import com.kitsugi.animelist.ui.screens.manga.MangaBrowseScreen
import com.kitsugi.animelist.ui.screens.manga.MangaBrowseViewModel
import com.kitsugi.animelist.ui.screens.manga.MangaDetailScreen
import com.kitsugi.animelist.ui.screens.manga.MangaReaderScreen
import com.kitsugi.animelist.ui.screens.manga.MangaScreen
import com.kitsugi.animelist.ui.screens.manga.MangaSourceHealthScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalBlurAdultMedia
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiAccentForThemeId
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppRoot(
    appViewModel: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val exploreViewModel: ExploreViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val kitsugiProfileViewModel: KitsugiProfileViewModel = viewModel()
    val playerSettingsViewModel: PlayerSettingsViewModel = viewModel()
    val addonViewModel: AddonViewModel = viewModel()
    val mangaViewModel: MangaViewModel = viewModel()
    val mangaBrowseViewModel: MangaBrowseViewModel = viewModel(
        factory = MangaBrowseViewModel.Factory(mangaViewModel.mangaRepository)
    )
    val updateViewModel: AppUpdateViewModel = viewModel()
    val updateUiState by updateViewModel.uiState.collectAsState()
    val navState = rememberAppNavigationState()

    val settingsDataStore = remember {
        SettingsDataStore(context.applicationContext)
    }

    val mediaRepository = remember {
        val db = KitsugiDatabase.getDatabase(context.applicationContext)
        MediaEntryRepository(
            dao = db.mediaEntryDao(),
            pendingSyncDao = db.pendingSyncDao(),
            context = context.applicationContext,
            onExternalSyncMessage = { message ->
                appViewModel.showSnackbarMessage(message)
            }
        )
    }

    val networkMonitor = remember {
        NetworkMonitor(context.applicationContext)
    }

    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    val appSettings by settingsDataStore.settingsFlow.collectAsState(
        initial = AppSettings()
    )

    val mediaEntries by mediaRepository.entriesFlow.collectAsState(
        initial = emptyList()
    )

    val latestMediaEntries by rememberUpdatedState(mediaEntries)

    val addonsList by addonViewModel.addonsList.collectAsState(
        initial = emptyList()
    )

    val reposList by addonViewModel.reposList.collectAsState(
        initial = emptyList()
    )

    val csPluginsList by addonViewModel.csPluginsList.collectAsState(
        initial = emptyList()
    )

    LaunchedEffect(reposList) {
        addonViewModel.syncRepos(reposList)
    }

    // Set callback to route ViewModel messages to the global app snackbar
    addonViewModel.onShowMessage = { message -> appViewModel.showSnackbarMessage(message) }
    mangaViewModel.onShowMessage = { message -> appViewModel.showSnackbarMessage(message) }

    val selectedTab = appViewModel.selectedTab
    val showGlobalSearch = appViewModel.showGlobalSearch
    val backupText = appViewModel.backupText
    val importText = appViewModel.importText
    val importMode = appViewModel.importMode
    val snackbarMessage = appViewModel.snackbarMessage

    val activeAccentColor = LocalKitsugiAccent.current

    var pendingExportText by remember {
        mutableStateOf("")
    }
    var pendingMangaSourceReportText by remember {
        mutableStateOf("")
    }

    LaunchedEffect(navState.detailBackStack) {
        // Çıkan (pop edilen) detay ekranının 280ms'lik AnimatedContent geçişi tamamlanıp
        // SaveableStateProvider.onDispose çalıştıktan SONRA stateHolder.removeState çağrılmalıdır.
        // Aksi halde hem geçiş ortasında aktif ViewModel/state silinir hem de SaveableStateProvider
        // 280ms sonra kapanırken state'i tekrar savedStates haritasına yazıp kalıcı bellek sızıntısı üretir.
        kotlinx.coroutines.delay(350L)
        navState.clearPreviousScreens()
    }

    // İlk karede senkron açılış sekmesi: DataStore disk okumasını beklemeden önce
    // senkron önbellekten (kitsugi_boot_prefs) son sekmeyi yükle; böylece uygulama
    // Explore'dan başlayıp sonradan Profile'a sıçramaz ve çökme/kapanma anında
    // son sekme kaybolmaz.
    remember(Unit) {
        if (!appViewModel.hasSetInitialTab) {
            val bootPrefs = context.getSharedPreferences("kitsugi_boot_prefs", android.content.Context.MODE_PRIVATE)
            val bootDefaultTab = bootPrefs.getString("default_tab", null)
            val bootLastUsedTab = bootPrefs.getString("last_used_tab", null)
            if (bootDefaultTab != null || bootLastUsedTab != null) {
                val effectiveDefault = bootDefaultTab ?: "LAST_USED"
                val targetName = if (effectiveDefault == "LAST_USED") (bootLastUsedTab ?: "Explore") else effectiveDefault
                val targetTab = MainTab.entries.firstOrNull { it.name == targetName } ?: MainTab.Explore
                appViewModel.setInitialTab(targetTab)
            }
        }
        true
    }

    LaunchedEffect(Unit) {
        val initialSettings = settingsDataStore.settingsFlow.first()
        runCatching {
            context.getSharedPreferences("kitsugi_boot_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("default_tab", initialSettings.defaultTab)
                .putString("last_used_tab", initialSettings.lastUsedTab)
                .apply()
        }
        if (!appViewModel.hasSetInitialTab) {
            val targetTab = when (initialSettings.defaultTab) {
                "LAST_USED" -> {
                    val lastUsed = initialSettings.lastUsedTab
                    MainTab.entries.firstOrNull { it.name == lastUsed } ?: MainTab.Explore
                }
                else -> {
                    MainTab.entries.firstOrNull { it.name == initialSettings.defaultTab } ?: MainTab.Explore
                }
            }
            appViewModel.setInitialTab(targetTab)
        }
    }

    // Filtreleri ve son seçili Listem sekmesini İLK kompozisyondan ÖNCE senkron geri yükle.
    // LaunchedEffect içinde geri yükleme yarışıyordu: Listem ilk karede açılıyorsa pager
    // varsayılan (0) sayfada kuruluyor ve settledPage etkisi kayıtlı seçimi anında geri
    // yazıyordu — uygulama kapanıp açılınca son seçim hep kayboluyordu. SharedPreferences
    // ve düz token okumaları ucuzdur; Keşfet/Arama/sekme geri yüklemelerindeki ilk kare
    // "sıçrama" flash'ını da engeller. (remember bir kez çalışır; idempotenttir.)
    remember(Unit) {
        appViewModel.loadFilters(context)
        true
    }

    LaunchedEffect(Unit) {
        val handleDeepLinkItem = { link: com.kitsugi.animelist.core.deeplink.TvDeepLink ->
            when (link) {
                is com.kitsugi.animelist.core.deeplink.TvDeepLink.Detail -> {
                    val malIdInt = link.mediaId.toIntOrNull() ?: 0
                    val syntheticResult = JikanSearchResult(
                        malId = malIdInt,
                        title = context.getString(R.string.yukleniyor),
                        subtitle = "",
                        type = link.mediaType ?: com.kitsugi.animelist.model.MediaType.Anime,
                        total = null,
                        score = null,
                        isAdult = false,
                        imageUrl = null,
                        year = null,
                        source = link.source
                    )
                    navState.navigateToDetail(DetailScreen.ApiResultDetail(syntheticResult))
                }
                is com.kitsugi.animelist.core.deeplink.TvDeepLink.Character -> {
                    navState.navigateToDetail(DetailScreen.CharacterDetail(characterId = link.characterId, source = link.source))
                }
                is com.kitsugi.animelist.core.deeplink.TvDeepLink.Staff -> {
                    navState.navigateToDetail(DetailScreen.StaffDetail(staffId = link.staffId, source = link.source))
                }
                is com.kitsugi.animelist.core.deeplink.TvDeepLink.Studio -> {
                    navState.navigateToDetail(DetailScreen.StudioDetail(studioId = link.studioId, source = link.source))
                }
                is com.kitsugi.animelist.core.deeplink.TvDeepLink.UserProfile -> {
                    appViewModel.selectTab(MainTab.Profile)
                }
                is com.kitsugi.animelist.core.deeplink.TvDeepLink.Manga -> {
                    navState.openMangaBrowse(mediaId = link.mangaId.toIntOrNull())
                }
                is com.kitsugi.animelist.core.deeplink.TvDeepLink.Notifications -> {
                    // Cihaz bildirimine dokunuldu: Bildirimler sayfasını aç ve
                    // bildirimin geldiği kaynağın sekmesini seç.
                    com.kitsugi.animelist.ui.screens.notifications.NotifOpenRequest.request(link.source)
                    navState.navigateToDetail(DetailScreen.Notifications)
                }
                else -> {}
            }
        }

        val pending = com.kitsugi.animelist.core.deeplink.DeepLinkHandler.drainPending()
        if (pending != null) {
            handleDeepLinkItem(pending)
        }

        com.kitsugi.animelist.core.deeplink.DeepLinkHandler.deepLinkFlow.collect { link ->
            handleDeepLinkItem(link)
        }
    }

    val triggerSearch: (String) -> Unit = { query ->
        navState.detailBackStack = emptyList<DetailScreen>()
        appViewModel.selectTab(MainTab.Search)
        searchViewModel.setQuery(query)
        searchViewModel.search()
    }

    // Genre / Tag tıklamalarında Search sekmesine git ve filtreyi pre-populate et
    val triggerSearchByGenre: (String) -> Unit = { genreEnglish ->
        navState.detailBackStack = emptyList<DetailScreen>()
        appViewModel.selectTab(MainTab.Search)
        searchViewModel.setGenreFilter(genreEnglish)
    }

    val triggerSearchByTag: (String) -> Unit = { tag ->
        navState.detailBackStack = emptyList<DetailScreen>()
        appViewModel.selectTab(MainTab.Search)
        searchViewModel.setTagFilter(tag)
    }

    val triggerDetailSearchFilter: (DetailSearchFilterRequest) -> Unit = { request ->
        navState.detailBackStack = emptyList<DetailScreen>()
        appViewModel.selectTab(MainTab.Search)
        searchViewModel.applyDetailFilterRequest(request)
    }

    val activeScreen = navState.detailBackStack.lastOrNull()

    val selectedDetailEntry = if (activeScreen is DetailScreen.MediaDetail) {
        mediaEntries.firstOrNull { it.id == activeScreen.entryId }
    } else null

    val selectedApiResult = if (activeScreen is DetailScreen.ApiResultDetail) {
        activeScreen.result
    } else null

    val selectedCharacterIdAndSource = if (activeScreen is DetailScreen.CharacterDetail) {
        Pair(activeScreen.characterId, activeScreen.source)
    } else null

    val selectedStaffIdAndSource = if (activeScreen is DetailScreen.StaffDetail) {
        Pair(activeScreen.staffId, activeScreen.source)
    } else null

    val selectedStudioIdAndSource = if (activeScreen is DetailScreen.StudioDetail) {
        Pair(activeScreen.studioId, activeScreen.source)
    } else null

    var editingEntry by remember {
        mutableStateOf<MediaEntry?>(null)
    }
    var isAddingBangumiEntry by remember { mutableStateOf(false) }
    var showBangumiLoginDialog by remember { mutableStateOf(false) }
    // Düzenleyicinin kaynağı, yalnızca o sağlayıcının kendi üyelik kaydı için kullanılır.
    var editingSourceHint by remember {
        mutableStateOf<String?>(null)
    }

    var deletingEntry by remember {
        mutableStateOf<MediaEntry?>(null)
    }

    var showMediaGridDialog by remember {
        mutableStateOf(false)
    }

    var mediaGridDialogTitle by remember {
        mutableStateOf("")
    }

    var mediaGridDialogResults by remember {
        mutableStateOf<List<JikanSearchResult>>(emptyList())
    }

    val bottomBarScrollState = rememberScrollVisibilityState(initialVisible = true)
    val bottomBarScrollConnection = rememberScrollConnection(bottomBarScrollState)

    LaunchedEffect(selectedTab, navState.detailBackStack, navState.fullScreenGridState) {
        bottomBarScrollState.show()
        if (appViewModel.hasSetInitialTab) {
            coroutineScope.launch {
                settingsDataStore.setLastUsedTab(selectedTab.name)
            }
        }
    }

    val profileImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            profileViewModel.updateProfileImageUri(uri = uri.toString())
        }
    }

    val bannerImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            profileViewModel.updateBannerImageUri(uri = uri.toString())
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) {
            appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.yedek_disa_aktarma_iptal_edildi))
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(pendingExportText)
                }
            } ?: error("Dosya yazılamadı")
        }.onSuccess {
            appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.yedek_dosyasi_olusturuldu))
            pendingExportText = ""
        }.onFailure { error ->
            appViewModel.showSnackbarMessage(
                error.message ?: KitsugiStrings.get(R.string.yedek_dosyasi_olusturulamadi)
            )
            pendingExportText = ""
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.yedek_ice_aktarma_iptal_edildi))
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            } ?: error("Dosya okunamadı")
        }.onSuccess { jsonText ->
            appViewModel.importBackupJsonText(
                jsonText = jsonText,
                currentEntries = latestMediaEntries,
                repository = mediaRepository
            )
        }.onFailure { error ->
            appViewModel.showSnackbarMessage(
                error.message ?: KitsugiStrings.get(R.string.yedek_dosyasi_okunamadi)
            )
        }
    }

    val exportMangaSourceReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri == null) {
            appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.source_raporu_disa_aktarma_iptal_edildi))
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(pendingMangaSourceReportText)
                }
            } ?: error("Dosya yazılamadı")
        }.onSuccess {
            appViewModel.showSnackbarMessage("Manga source raporu dosyaya kaydedildi")
            pendingMangaSourceReportText = ""
        }.onFailure { error ->
            appViewModel.showSnackbarMessage(
                error.message ?: "Source raporu dosyaya kaydedilemedi"
            )
            pendingMangaSourceReportText = ""
        }
    }

    // Auth mesajları appViewModel snackbar'ına yönlendiriliyor
    authViewModel.onShowMessage = { message -> appViewModel.showSnackbarMessage(message) }
    profileViewModel.onShowMessage = { message -> appViewModel.showSnackbarMessage(message) }
    playerSettingsViewModel.onShowMessage = { message -> appViewModel.showSnackbarMessage(message) }

    LaunchedEffect(Unit) {
        authViewModel.refreshAuthState()
        if (appSettings.autoUpdateCheckEnabled) {
            updateViewModel.checkForUpdates(silent = true)
        }

        launch {
            networkMonitor.isOnlineFlow.collect { isOnline ->
                if (isOnline) {
                    // Bağlantı (geri) geldi: yeniden deneme bütçesi tazelenir — 5 denemeyi
                    // aşmış, "asla denenmeyecek" duruma düşmüş işler de bu oturumda kurtarılır.
                    val count = mediaRepository.drainPendingQueue(freshBudget = true)
                    if (count > 0) {
                        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.cevrimdisi_kaydedilen_x_islem_senkronize_edildi, count))
                    }
                }
            }
        }

        ExternalAuthManager.authEvents.collect { event ->
            when (event) {
                is ExternalAuthManager.AuthEvent.Success -> {
                    authViewModel.refreshAuthState()
                    com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)

                    appViewModel.showSnackbarMessage(
                        when (event.serviceName) {
                            "anilist"   -> "AniList bağlantısı başarılı, liste aktarılıyor..."
                            "simkl"     -> "Simkl bağlantısı başarılı, liste aktarılıyor..."
                            "kitsu"     -> "Kitsu bağlantısı başarılı, liste aktarılıyor..."
                            "shikimori" -> "Shikimori bağlantısı başarılı, liste aktarılıyor..."
                            "bangumi"   -> "Bangumi bağlantısı başarılı, koleksiyon aktarılıyor..."
                            else        -> "MyAnimeList bağlantısı başarılı, liste aktarılıyor..."
                        }
                    )

                    when (event.serviceName) {
                        "anilist"   -> authViewModel.importAniListAnimeList(latestMediaEntries, mediaRepository)
                        "simkl"     -> authViewModel.importSimklList(latestMediaEntries, mediaRepository)
                        "kitsu"     -> authViewModel.importKitsuList(latestMediaEntries, mediaRepository)
                        "shikimori" -> authViewModel.importShikimoriList(latestMediaEntries, mediaRepository)
                        "bangumi"   -> authViewModel.importBangumiList(latestMediaEntries, mediaRepository)
                        else        -> authViewModel.importMalAnimeList(latestMediaEntries, mediaRepository)
                    }

                    launch {
                        val count = mediaRepository.drainPendingQueue()
                        if (count > 0) {
                            appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.cevrimdisi_kaydedilen_x_islem_senkronize_edildi, count))
                        }
                    }
                }

                is ExternalAuthManager.AuthEvent.Error -> {
                    appViewModel.showSnackbarMessage(event.message)
                }

                is ExternalAuthManager.AuthEvent.SessionExpired -> {
                    authViewModel.refreshAuthState()
                    com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
                    appViewModel.showSnackbarMessage(
                        when (event.serviceName) {
                            "simkl" -> "Simkl oturum süresi doldu. Lütfen tekrar bağlanın."
                            else -> "${event.serviceName} oturum süresi doldu."
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage

        if (message != null) {
            snackbarHostState.showSnackbar(message)
            appViewModel.clearSnackbarMessage()
        }
    }

    fun addApiSelectionToList(selection: ApiSearchSelection) {
        if (canonicalMediaSourceId(selection.result.source) == "bangumi") {
            if (!authViewModel.isBangumiConnected) {
                showBangumiLoginDialog = true
                appViewModel.showSnackbarMessage("Bangumi koleksiyonuna eklemek için önce hesabını bağla.")
                return
            }
            val existing = mediaEntries.firstOrNull { it.matchesInSource(selection.result) }
            if (existing != null) {
                editingEntry = existing
                editingSourceHint = "bangumi"
                isAddingBangumiEntry = false
                return
            }
            val newEntry = BangumiMediaEntryFactory.fromSearchResult(selection.result, selection.synopsis)
            if (newEntry == null) {
                appViewModel.showSnackbarMessage("Bangumi kaydı için geçerli subject kimliği veya başlık bulunamadı.")
                return
            }
            editingEntry = newEntry
            editingSourceHint = "bangumi"
            isAddingBangumiEntry = true
            return
        }

        appViewModel.addApiSelectionToList(
            selection = selection,
            currentEntries = mediaEntries,
            repository = mediaRepository,
            isAniListConnected = authViewModel.isAniListConnected,
            isMalConnected = authViewModel.isMalConnected,
            isSimklConnected = authViewModel.isSimklConnected,
            isBangumiConnected = authViewModel.isBangumiConnected,
            isKitsuConnected = authViewModel.isKitsuConnected,
            isShikimoriConnected = authViewModel.isShikimoriConnected
        )
    }

    fun createBackupFile() {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd-HHmm",
            Locale.US
        ).format(Date())

        val filename = "Kitsugi-backup-$timestamp.json"

        pendingExportText = MediaEntryBackup.exportToJson(mediaEntries)

        exportBackupLauncher.launch(filename)
    }

    fun exportMangaSourceReportFile(text: String) {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd-HHmm",
            Locale.US
        ).format(Date())

        val filename = "Kitsugi-manga-sources-$timestamp.txt"

        pendingMangaSourceReportText = text

        exportMangaSourceReportLauncher.launch(filename)
    }

    fun openBackupFile() {
        importBackupLauncher.launch(
            arrayOf(
                "application/json",
                "text/*",
                "*/*"
            )
        )
    }

    val entryMap = remember(mediaEntries) {
        generateExploreEntryMap(mediaEntries)
    }

    val getMediaEntryLambda: (JikanSearchResult) -> MediaEntry? = remember(entryMap) {
        { result: JikanSearchResult -> getMediaEntryFromMap(result, entryMap) }
    }

    // Yerel `fun` yerine remember'lı lambda: yerel fonksiyon referansları (`::fn`) her
    // yeniden kompozisyonda yeni nesne olur → FullScreenMediaGridPage / arama gibi çocuk
    // composable'lar hiçbir zaman "değişmedi" diyemez, her AppRoot yenilemesinde tüm
    // kartlar yeniden kompoze olurdu.
    val getMediaEntry: (JikanSearchResult) -> MediaEntry? = getMediaEntryLambda
    val isAlreadyInList: (JikanSearchResult) -> Boolean = remember(getMediaEntryLambda) {
        { result: JikanSearchResult -> getMediaEntryLambda(result) != null }
    }

    fun openFullScreenSection(
        title: String,
        categoryType: ExploreCategoryType,
        results: List<JikanSearchResult>,
        platform: ExplorePlatform
    ) {
        navState.fullScreenGridState = FullScreenMediaGridState(
            title = title,
            categoryType = categoryType,
            platform = platform,
            initialResults = results
        )
    }

    fun openApiDetail(result: JikanSearchResult) {
        navState.navigateToDetail(DetailScreen.ApiResultDetail(result))
    }

    fun incrementEntryProgress(entry: MediaEntry) {
        val nextProgress = if (entry.total != null) {
            (entry.progress + 1).coerceAtMost(entry.total)
        } else {
            entry.progress + 1
        }

        val nextStatus = if (entry.total != null && nextProgress >= entry.total) {
            WatchStatus.Completed
        } else if (entry.status == WatchStatus.Planned) {
            WatchStatus.Watching
        } else {
            entry.status
        }

        // Aynı kural motoru: "İzlemeye başla" anında başlangıç tarihi bugüne dolar,
        // son bölüme gelindiğinde durum Tamamlandı'ya çekilip bitiş tarihi yazılır.
        val auto = EntryAutoRules.resolveOnStatusChange(
            status = nextStatus,
            startDate = entry.startDate,
            endDate = entry.endDate,
            progress = nextProgress,
            total = entry.total,
            statusChanged = nextStatus != entry.status
        )

        val updatedEntry = entry.copy(
            progress = auto.progress,
            status = nextStatus,
            startDate = auto.startDate,
            endDate = auto.endDate
        )

        coroutineScope.launch {
            mediaRepository.update(updatedEntry)
        }
    }

    var showExitConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        when {
            navState.mangaReaderNavState != null          -> navState.mangaReaderNavState = null
            navState.mangaDetailNavState != null          -> navState.mangaDetailNavState = null
            navState.mangaSourceHealthOpen                -> navState.closeMangaSourceHealth()
            navState.mangaBrowseOpen                      -> {
                mangaBrowseViewModel.reset()
                navState.closeMangaBrowse()
            }
            navState.detailBackStack.isNotEmpty() && (
                navState.addonFullScreenGridState?.let { navState.detailBackStack.size > it.openingStackDepth } == true ||
                navState.fullScreenGridState?.let { navState.detailBackStack.size > it.openingStackDepth } == true
            ) -> navState.popDetailStack()
            navState.addonFullScreenGridState != null     -> {
                navState.addonFullScreenGridState?.savedStateKey?.let(navState.stateHolder::removeState)
                navState.addonFullScreenGridState = null
            }
            navState.fullScreenGridState != null          -> {
                navState.fullScreenGridState?.savedStateKey?.let(navState.stateHolder::removeState)
                navState.fullScreenGridState = null
            }
            navState.detailBackStack.isNotEmpty()         -> navState.popDetailStack()
            appViewModel.popTabHistory()                  -> { /* Tab history popped */ }
            else                                          -> showExitConfirmDialog = true
        }
    }

    val isInFullScreenMode = navState.fullScreenGridState != null ||
            navState.addonFullScreenGridState != null ||
            navState.detailBackStack.isNotEmpty() ||
            navState.mangaBrowseOpen ||
            navState.mangaDetailNavState != null ||
            navState.mangaReaderNavState != null ||
            navState.mangaSourceHealthOpen ||
            navState.isSettingsSubPageOpen

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
    val touchBringIntoView = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberTouchPassthroughSpec()
    CompositionLocalProvider(
        LocalBlurAdultMedia provides appSettings.blurAdultMedia,
        com.kitsugi.animelist.ui.theme.LocalShowAdultContent provides appSettings.showAdultContent,
        // Telefon/tablette odaklı kart listeyi geri çekmesin. TV kendi spec'ini korur.
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides if (isTvDevice) {
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current
        } else {
            touchBringIntoView
        }
    ) {
        Scaffold(
            containerColor = KitsugiColors.Background,
            modifier = if (isInFullScreenMode) Modifier else Modifier.nestedScroll(bottomBarScrollConnection),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    snackbar = { snackbarData ->
                        Snackbar(
                            snackbarData = snackbarData,
                            containerColor = KitsugiColors.SurfaceStrong,
                            contentColor = KitsugiColors.TextPrimary,
                            actionColor = activeAccentColor,
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                )
            },
            bottomBar = {
                // Yatay modda NavigationRail kullanıldığı için BottomBar gizleniyor
                if (!isInFullScreenMode && !isLandscape) {
                    val density = LocalDensity.current
                    val bottomBarOffset by animateFloatAsState(
                        targetValue = if (appSettings.fixedNavBar || bottomBarScrollState.isVisible) 0f else with(density) { 140.dp.toPx() },
                        animationSpec = tween(durationMillis = KitsugiMotion.fastMillis + 50),
                        label = "bottom_bar_offset"
                    )
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationY = bottomBarOffset
                            }
                    ) {
                        AppBottomBar(
                            currentTab = selectedTab,
                            onTabSelected = { tab ->
                                appViewModel.selectTab(tab)
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            // Yatay modda NavigationRail + içerik yan yana; dikey modda normal tam ekran içerik
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                        end = innerPadding.calculateEndPadding(LayoutDirection.Ltr)
                    )
            ) {
                if (isLandscape && !isInFullScreenMode) {
                    AppNavigationRail(
                        currentTab = selectedTab,
                        onTabSelected = { tab -> appViewModel.selectTab(tab) }
                    )
                }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(KitsugiColors.Background)
            ) {
                val activeApiResult = selectedApiResult
                val activeFullScreenGrid = navState.fullScreenGridState
                val activeDetailEntry = selectedDetailEntry

                val activeAddonFullScreenGrid = navState.addonFullScreenGridState

                val activeCharacter = selectedCharacterIdAndSource
                val activeStaff = selectedStaffIdAndSource
                val activeStudio = selectedStudioIdAndSource

                val currentDepth = (if (activeFullScreenGrid != null) 1 else 0) +
                                   (if (activeAddonFullScreenGrid != null) 1 else 0) +
                                   (if (navState.mangaBrowseOpen) 1 else 0) +
                                   (if (navState.mangaDetailNavState != null) 1 else 0) +
                                   (if (navState.mangaReaderNavState != null) 1 else 0) +
                                   (if (navState.mangaSourceHealthOpen) 1 else 0) +
                                   navState.detailBackStack.size
                val currentAppStateKey = when {
                    navState.mangaReaderNavState != null  -> AppStateKey.MangaReader(depth = currentDepth)
                    navState.mangaDetailNavState != null  -> AppStateKey.MangaDetail(depth = currentDepth)
                    navState.mangaSourceHealthOpen        -> AppStateKey.MangaSourceHealth(depth = currentDepth)
                    navState.mangaBrowseOpen              -> AppStateKey.MangaBrowse(depth = currentDepth)
                    activeStudio != null         -> AppStateKey.StudioDetail(activeStudio.first, activeStudio.second, depth = currentDepth, name = (activeScreen as? DetailScreen.StudioDetail)?.name, imageUrl = (activeScreen as? DetailScreen.StudioDetail)?.imageUrl)
                    activeStaff != null          -> AppStateKey.StaffDetail(activeStaff.first, activeStaff.second, depth = currentDepth, name = (activeScreen as? DetailScreen.StaffDetail)?.name, imageUrl = (activeScreen as? DetailScreen.StaffDetail)?.imageUrl)
                    activeCharacter != null      -> AppStateKey.CharacterDetail(activeCharacter.first, activeCharacter.second, depth = currentDepth, name = (activeScreen as? DetailScreen.CharacterDetail)?.name, imageUrl = (activeScreen as? DetailScreen.CharacterDetail)?.imageUrl, isRealMediaRole = (activeScreen as? DetailScreen.CharacterDetail)?.isRealMediaRole ?: false)
                    activeApiResult != null      -> AppStateKey.ApiResultDetail(activeApiResult, depth = currentDepth)
                    activeDetailEntry != null    -> AppStateKey.MediaDetail(activeDetailEntry.id, depth = currentDepth)
                    activeFullScreenGrid != null ->
                        AppStateKey.FullScreenGrid(activeFullScreenGrid, depth = currentDepth)
                    activeAddonFullScreenGrid != null ->
                        AppStateKey.AddonFullScreenGrid(activeAddonFullScreenGrid, depth = currentDepth)
                    activeScreen is DetailScreen.AiringCalendar -> AppStateKey.AiringCalendar(depth = currentDepth, preferredSource = activeScreen.preferredSource)
                    activeScreen is DetailScreen.Stats -> AppStateKey.Stats(depth = currentDepth)
                    activeScreen is DetailScreen.Favourites -> AppStateKey.Favourites(depth = currentDepth)
                    activeScreen is DetailScreen.About -> AppStateKey.About(depth = currentDepth)
                    activeScreen is DetailScreen.UserProfile -> AppStateKey.UserProfile(activeScreen.userId, depth = currentDepth, username = activeScreen.username, avatarUrl = activeScreen.avatarUrl, source = activeScreen.source)
                    activeScreen is DetailScreen.UserMediaList -> AppStateKey.UserMediaList(activeScreen.userId, depth = currentDepth, username = activeScreen.username, initialMediaType = activeScreen.initialMediaType)
                    activeScreen is DetailScreen.Notifications -> AppStateKey.Notifications(depth = currentDepth)
                    activeScreen is DetailScreen.Downloads -> AppStateKey.Downloads(depth = currentDepth)
                    activeScreen is DetailScreen.WatchHistory -> AppStateKey.WatchHistory(depth = currentDepth)
                    activeScreen is DetailScreen.PluginPicker -> AppStateKey.PluginPicker(depth = currentDepth)
                    activeScreen is DetailScreen.AddonExplore -> AppStateKey.AddonExplore(activeScreen.apiName, depth = currentDepth)
                    activeScreen is DetailScreen.SourceSearchPage -> AppStateKey.SourceSearchPage(activeScreen.engine, activeScreen.query, activeScreen.scope, activeScreen.shelfResults, depth = currentDepth)
                    else                         -> AppStateKey.Tab(selectedTab)
                }

                // Çökme raporu için "son ekran" bilgisini kaydet (sessiz ölümlerde tek ipucu bu olur)
                LaunchedEffect(currentAppStateKey) {
                    try {
                        com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                            .noteScreen(currentAppStateKey.toString())
                    } catch (_: Throwable) {}
                }

                AppNavigationContent(
                    currentAppStateKey = currentAppStateKey,
                    navState = navState,
                    mediaEntries = mediaEntries,
                    appSettings = appSettings,
                    settingsDataStore = settingsDataStore,
                    appViewModel = appViewModel,
                    exploreViewModel = exploreViewModel,
                    searchViewModel = searchViewModel,
                    authViewModel = authViewModel,
                    profileViewModel = profileViewModel,
                    kitsugiProfileViewModel = kitsugiProfileViewModel,
                    playerSettingsViewModel = playerSettingsViewModel,
                    updateViewModel = updateViewModel,
                    addonViewModel = addonViewModel,
                    mangaViewModel = mangaViewModel,
                    mangaBrowseViewModel = mangaBrowseViewModel,
                    mediaRepository = mediaRepository,
                    coroutineScope = coroutineScope,
                    backupText = backupText,
                    importText = importText,
                    importMode = importMode,
                    profileImagePickerLauncher = profileImagePickerLauncher,
                    bannerImagePickerLauncher = bannerImagePickerLauncher,
                    onEditEntry = { editingEntry = it; editingSourceHint = null; isAddingBangumiEntry = false },
                    onEditEntryWithSource = { entry, source ->
                        editingEntry = entry
                        editingSourceHint = source
                        isAddingBangumiEntry = false
                    },
                    onDeleteEntry = { editingEntry = null; editingSourceHint = null; isAddingBangumiEntry = false; deletingEntry = it },
                    onIncrementEntryProgress = ::incrementEntryProgress,
                    onAddApiSelectionToList = ::addApiSelectionToList,
                    onLoginBangumi = { showBangumiLoginDialog = true },
                    onExportBackupFileClick = ::createBackupFile,
                    onImportBackupFileClick = ::openBackupFile,
                    onOpenApiDetail = ::openApiDetail,
                    onSeeAllSection = ::openFullScreenSection,
                    onOpenMangaReader = { navState.openMangaBrowse() },
                    exportMangaSourceReportFile = ::exportMangaSourceReportFile,
                    triggerSearch = triggerSearch,
                    triggerSearchByGenre = triggerSearchByGenre,
                    triggerSearchByTag = triggerSearchByTag,
                    triggerDetailSearchFilter = triggerDetailSearchFilter,
                    isAlreadyInList = isAlreadyInList,
                    getMediaEntry = getMediaEntry,
                    bottomBarScrollState = bottomBarScrollState
                )
            } // Box (içerik alanı)
            } // Row (landscape layout)
        }

        AppDialogHost(
            showGlobalSearch = showGlobalSearch,
            onDismissGlobalSearch = { appViewModel.closeGlobalSearch() },
            onGlobalSearchResultSelected = { selection ->
                addApiSelectionToList(selection)
            },

            editingEntry = editingEntry,
            editingSourceHint = editingSourceHint,
            scoreFormat = appSettings.scoreFormat,
            onDismissEditing = {
                editingEntry = null
                editingSourceHint = null
                isAddingBangumiEntry = false
            },
            onDeleteEditingEntry = { entry ->
                coroutineScope.launch { mediaRepository.deleteById(entry.id) }
                isAddingBangumiEntry = false
            },
            onConfirmEdit = { title, subtitle, type, status, isAdult, progress, total, score, isFavorite, startDate, endDate, notes, tags, priority, isRepeating, repeatCount, repeatValue, volumeProgress, isPrivate, isHiddenFromStatusLists, advancedScores ->
                val entry = editingEntry ?: return@AppDialogHost
                val addBangumiMembership = isAddingBangumiEntry
                val isBangumiEntry = canonicalMediaSourceId(entry.source) == "bangumi"
                // Durum → tarih/ilerleme otomasyonu (tek kural motoru: EntryAutoRules).
                // Düzenleyici paneli bu kuralları canlı olarak zaten uygular; buradaki
                // çözümleme, panelin atlandığı ya da tarihin elle temizlendiği durumlar
                // için son bir güvenlik ağıdır. Dolu tarihler asla ezilmez.
                val auto = EntryAutoRules.resolveOnStatusChange(
                    status = status,
                    startDate = startDate,
                    endDate = endDate,
                    progress = progress,
                    total = total,
                    // Sadece durum gerçekten değiştiyse ilerleme tamamlanır; kullanıcı
                    // "Tamamlandı" bir kaydın sadece puanını düzenliyorsa ilerleme aynı kalır.
                    statusChanged = status != entry.status
                )
                val updatedEntry = entry.copy(
                    title = title,
                    subtitle = if (subtitle.isBlank()) KitsugiStrings.get(R.string.manuel_eklenen_icerik) else subtitle,
                    type = type,
                    status = status,
                    isAdult = isAdult,
                    progress = auto.progress,
                    total = total,
                    score = score,
                    isFavorite = isFavorite,
                    startDate = auto.startDate,
                    endDate = auto.endDate,
                    notes = notes,
                    tags = tags,
                    priority = priority ?: 0,
                    isRepeating = isRepeating,
                    repeatCount = repeatCount,
                    repeatValue = repeatValue,
                    volumeProgress = volumeProgress,
                    isPrivate = isPrivate,
                    isHiddenFromStatusLists = isHiddenFromStatusLists
                )
                if (isBangumiEntry) {
                    // Diğer kaynaklarla AYNI hat: önce yerele yaz, sonra ExternalListSyncManager
                    // Bangumi dahil altı kaynağa birden yaysın. Eski akış önce Bangumi'ye yazıp
                    // başarısızlıkta (token/kapsam/ağ) kaydı yerele bile kaydetmiyordu — kullanıcının
                    // seçtiği durum/puan/tarih/bölüm tümüyle kayboluyor, diğer kaynaklara da
                    // hiç gidilmiyordu. Artık başarısız yazımlar bekleyen senkron kuyruğuna girer.
                    val persistedEntry = updatedEntry.copy(
                        source = com.kitsugi.animelist.data.remote.BangumiIdNamespace.SOURCE
                    )
                    coroutineScope.launch {
                        if (addBangumiMembership) {
                            mediaRepository.insert(persistedEntry.copy(id = 0), syncExternal = true)
                        } else {
                            mediaRepository.update(persistedEntry, syncExternal = true)
                        }
                    }
                    editingEntry = null
                    editingSourceHint = null
                    isAddingBangumiEntry = false
                } else {
                    coroutineScope.launch { mediaRepository.update(updatedEntry, advancedScores = advancedScores) }
                    editingEntry = null
                    editingSourceHint = null
                    isAddingBangumiEntry = false
                }
            },

            deletingEntry = deletingEntry,
            onConfirmDelete = { entry ->
                coroutineScope.launch { mediaRepository.deleteById(entry.id) }
                navState.popDetailStack()
                deletingEntry = null
            },
            onDismissDelete = { deletingEntry = null },

            showExitConfirmDialog = showExitConfirmDialog,
            onConfirmExit = { showExitConfirmDialog = false },
            onDismissExit = { showExitConfirmDialog = false },

            showMediaGridDialog = showMediaGridDialog,
            mediaGridDialogTitle = mediaGridDialogTitle,
            mediaGridDialogResults = mediaGridDialogResults,
            isAlreadyInList = isAlreadyInList,
            onMediaGridItemClick = { result ->
                navState.navigateToDetail(DetailScreen.ApiResultDetail(result))
            },
            onDismissMediaGrid = { showMediaGridDialog = false },
            getMediaEntry = getMediaEntryLambda
        )

        KitsugiUpdateDialog(
            state = updateUiState,
            onUpdateClick = { updateViewModel.startDownloadAndInstall() },
            onRetryInstallClick = { updateViewModel.retryInstall() },
            onDismiss = { updateViewModel.dismissUpdate() }
        )

        // ── OAuth WebView Dialog (MAL/AniList/Simkl için eski haline restore) ────
        // Harici tarayıcı yerine uygulama içi WebView ile giriş, basic auth popup fix
        val oauthService = authViewModel.oauthServiceToShow
        if (oauthService != null) {
            com.kitsugi.animelist.ui.components.KitsugiOAuthWebViewDialog(
                serviceName = oauthService,
                onDismiss = { authViewModel.dismissOAuthDialog() },
                onSuccess = { svc ->
                    // MAL'da tek giriş: başarılı girişten sonra iki kimlik (uygulama API'si +
                    // web oturumu) doğrulanır. Eksik/farklı hesap varsa tek ek adım açılır.
                    authViewModel.noteOAuthSuccess(svc)
                    authViewModel.dismissOAuthDialog()
                    // Başarı mesajı zaten authEvents flow'undan geliyor
                },
                onError = { err ->
                    // Hata durumunda da dialog açık kalabilir, kullanıcı kapatabilir
                }
            )
        }

        if (showBangumiLoginDialog) {
            KitsugiBangumiLoginDialog(
                onDismiss = { showBangumiLoginDialog = false },
                onLogin = { clientId, clientSecret, authCode, onComplete ->
                    authViewModel.loginBangumi(
                        clientId = clientId,
                        clientSecret = clientSecret,
                        authCode = authCode,
                        onSuccess = {
                            onComplete(true, null)
                            showBangumiLoginDialog = false
                        },
                        onError = { error -> onComplete(false, error) }
                    )
                }
            )
        }
    }
}

@Composable
private fun AppNavigationContent(
    currentAppStateKey: AppStateKey,
    navState: AppNavigationState,
    mediaEntries: List<MediaEntry>,
    appSettings: AppSettings,
    settingsDataStore: SettingsDataStore,
    appViewModel: AppViewModel,
    exploreViewModel: ExploreViewModel,
    searchViewModel: SearchViewModel,
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    kitsugiProfileViewModel: KitsugiProfileViewModel,
    playerSettingsViewModel: PlayerSettingsViewModel,
    updateViewModel: AppUpdateViewModel,
    addonViewModel: AddonViewModel,
    mangaViewModel: MangaViewModel,
    mangaBrowseViewModel: MangaBrowseViewModel,
    mediaRepository: MediaEntryRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    backupText: String,
    importText: String,
    importMode: com.kitsugi.animelist.ui.components.BackupImportMode,
    profileImagePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    bannerImagePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onEditEntry: (MediaEntry) -> Unit,
    /**
     * Detay sayfasından gelen düzenleme: [entry]'nin yanı sıra sayfanın kaynağını da taşır.
     * Varsayılan olarak [onEditEntry] gibi davranır (kaynak ipucu yok).
     */
    onEditEntryWithSource: (MediaEntry, String) -> Unit = { entry, _ -> onEditEntry(entry) },
    onDeleteEntry: (MediaEntry) -> Unit,
    onIncrementEntryProgress: (MediaEntry) -> Unit,
    onAddApiSelectionToList: (ApiSearchSelection) -> Unit,
    onLoginBangumi: () -> Unit,
    onExportBackupFileClick: () -> Unit,
    onImportBackupFileClick: () -> Unit,
    onOpenApiDetail: (JikanSearchResult) -> Unit,
    onSeeAllSection: (String, ExploreCategoryType, List<JikanSearchResult>, ExplorePlatform) -> Unit,
    onOpenMangaReader: () -> Unit,
    exportMangaSourceReportFile: (String) -> Unit,
    triggerSearch: (String) -> Unit,
    triggerSearchByGenre: (String) -> Unit = {},
    triggerSearchByTag: (String) -> Unit = {},
    triggerDetailSearchFilter: (DetailSearchFilterRequest) -> Unit = {},
    isAlreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    bottomBarScrollState: com.kitsugi.animelist.utils.ScrollVisibilityState
) {

    AnimatedContent(
        targetState = currentAppStateKey,
        label = "page_transition",
        transitionSpec = {
            val fromTab = initialState is AppStateKey.Tab
            val toTab = targetState is AppStateKey.Tab
            when {
                fromTab && toTab -> tabContentTransform()
                initialState.depth > targetState.depth -> detailExitContentTransform()
                else -> detailEnterContentTransform()
            }
        }
    ) { key ->
        when (key) {
            is AppStateKey.StudioDetail,
            is AppStateKey.StaffDetail,
            is AppStateKey.CharacterDetail,
            is AppStateKey.ApiResultDetail,
            is AppStateKey.AiringCalendar,
            is AppStateKey.Stats,
            is AppStateKey.Favourites,
            is AppStateKey.About,
            is AppStateKey.UserProfile,
            is AppStateKey.UserMediaList,
            is AppStateKey.Notifications,
            is AppStateKey.Downloads,
            is AppStateKey.WatchHistory,
            is AppStateKey.MediaDetail -> {
                AppRootDetailPages(
                    key = key,
                    navState = navState,
                    mediaEntries = mediaEntries,
                    appSettings = appSettings,
                    settingsDataStore = settingsDataStore,
                    appViewModel = appViewModel,
                    authViewModel = authViewModel,
                    mediaRepository = mediaRepository,
                    coroutineScope = coroutineScope,
                    updateViewModel = updateViewModel,
                    onEditEntry = onEditEntry,
                    onEditEntryWithSource = onEditEntryWithSource,
                    onDeleteEntry = onDeleteEntry,
                    onIncrementEntryProgress = onIncrementEntryProgress,
                    onAddApiSelectionToList = onAddApiSelectionToList,
                    onLoginBangumi = onLoginBangumi,
                    onReadMangaClick = { entry, mapping ->
                        if (mapping != null) {
                            val source = mangaViewModel.mangaRepository.getSourceByName(mapping.mangaSource)
                            if (source != null) {
                                navState.mangaDetailNavState = MangaDetailNavState(
                                    source = source,
                                    mangaDetails = com.kitsugi.animelist.data.manga.MangaDetails(
                                        url = mapping.mangaUrl,
                                        title = mapping.mangaTitle,
                                        thumbnailUrl = mapping.mangaThumbnail,
                                        source = mapping.mangaSource
                                    )
                                )
                            } else {
                                navState.openMangaBrowse(entry.title, entry.id)
                            }
                        } else {
                            navState.openMangaBrowse(entry.title, entry.id)
                        }
                    },
                    triggerSearch = triggerSearch,
                    triggerSearchByGenre = triggerSearchByGenre,
                    triggerSearchByTag = triggerSearchByTag,
                    triggerDetailSearchFilter = triggerDetailSearchFilter,
                    onScrollReset = { bottomBarScrollState.show() }
                )
            }

            is AppStateKey.FullScreenGrid -> {
                val gridKey = "full_grid_${key.depth}_${System.identityHashCode(key.state)}"
                if (key.state.savedStateKey == null) key.state.openingStackDepth = navState.detailBackStack.size
                key.state.savedStateKey = gridKey
                navState.stateHolder.SaveableStateProvider(key = gridKey) {
                    FullScreenMediaGridPage(
                        title = key.state.title,
                        categoryType = key.state.categoryType,
                        platform = key.state.platform,
                        initialResults = key.state.initialResults,
                        session = key.state,
                        alreadyInList = isAlreadyInList,
                        onItemClick = onOpenApiDetail,
                        onBackClick = {
                            navState.fullScreenGridState = null
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(350L)
                                navState.stateHolder.removeState(gridKey)
                            }
                        },
                        titleLanguage = appSettings.titleLanguage,
                        scoreFormat = appSettings.scoreFormat,
                        hideScores = appSettings.hideScores,
                        getMediaEntry = getMediaEntry,
                        // Kart kısayolu: listede ise düzenle panelini aç, değilse tekil ekleme akışına
                        // (AppRoot sarmalayıcısı Bangumi diyaloğunu da yönetir).
                        onQuickAddToList = { result ->
                            val entry = getMediaEntry(result)
                            if (entry != null) {
                                onEditEntry(entry)
                            } else {
                                onAddApiSelectionToList(ApiSearchSelection(result = result, synopsis = null))
                            }
                        },
                        // Başlıktaki toplu ekleme: görünen tüm sonuçlar tek seferde eklenir;
                        // AppViewModel tek bir özet bildirim gösterir.
                        onAddAllToList = { results ->
                            appViewModel.addApiSelectionsToList(
                                selections = results.map { ApiSearchSelection(result = it, synopsis = null) },
                                currentEntries = mediaEntries,
                                repository = mediaRepository,
                                isAniListConnected = authViewModel.isAniListConnected,
                                isMalConnected = authViewModel.isMalConnected,
                                isSimklConnected = authViewModel.isSimklConnected,
                                isBangumiConnected = authViewModel.isBangumiConnected,
                                isKitsuConnected = authViewModel.isKitsuConnected,
                                isShikimoriConnected = authViewModel.isShikimoriConnected
                            )
                        }
                    )
                }
            }

            is AppStateKey.AddonFullScreenGrid -> {
                val gridKey = "addon_grid_${key.depth}_${System.identityHashCode(key.state)}"
                if (key.state.savedStateKey == null) key.state.openingStackDepth = navState.detailBackStack.size
                key.state.savedStateKey = gridKey
                navState.stateHolder.SaveableStateProvider(key = gridKey) {
                    com.kitsugi.animelist.ui.screens.fullscreen.AddonFullScreenGridPage(
                        state = key.state,
                        onBackClick = {
                            navState.addonFullScreenGridState = null
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(350L)
                                navState.stateHolder.removeState(gridKey)
                            }
                        },
                        titleLanguage = appSettings.titleLanguage,
                        scoreFormat = appSettings.scoreFormat,
                        hideScores = appSettings.hideScores,
                        blurAdultMedia = appSettings.blurAdultMedia
                    )
                }
            }

            is AppStateKey.PluginPicker -> {
                navState.stateHolder.SaveableStateProvider(key = "plugin_picker_${key.depth}") {
                    com.kitsugi.animelist.ui.screens.search.PluginPickerScreen(
                        onBackClick = { navState.popDetailStack() },
                        searchViewModel = searchViewModel,
                        navState = navState,
                        onOpenApiDetail = onOpenApiDetail
                    )
                }
            }

            is AppStateKey.AddonExplore -> {
                navState.stateHolder.SaveableStateProvider(key = "addon_explore_${key.depth}_${key.apiName}") {
                    val api = remember(key.apiName) {
                        com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull { it.name.equals(key.apiName, ignoreCase = true) }
                    }
                    if (api != null) {
                        com.kitsugi.animelist.ui.screens.search.components.AddonExplorePage(
                            api = api,
                            onBackClick = { navState.popDetailStack() },
                            titleLanguage = appSettings.titleLanguage,
                            scoreFormat = appSettings.scoreFormat,
                            hideScores = appSettings.hideScores,
                            blurAdultMedia = appSettings.blurAdultMedia,
                            onSeeAllClick = { title, mainPageData, horizontalImages, initialItems ->
                                navState.addonFullScreenGridState = com.kitsugi.animelist.ui.app.AddonFullScreenGridState(
                                    title = title,
                                    apiName = api.name,
                                    initialItems = initialItems,
                                    mainPageData = mainPageData,
                                    horizontalImages = horizontalImages
                                )
                            }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            navState.popDetailStack()
                        }
                    }
                }
            }

            is AppStateKey.SourceSearchPage -> {
                navState.stateHolder.SaveableStateProvider(key = "source_search_${key.depth}_${key.engine.id}") {
                    com.kitsugi.animelist.ui.screens.search.SourceSearchPage(
                        engine = key.engine,
                        retainedOwner = navState.sourceSearchOwner("source_search_${key.depth}_${key.engine.id}"),
                        initialQuery = key.query,
                        initialScope = key.scope,
                        initialResults = key.shelfResults,
                        mediaEntries = mediaEntries,
                        showAdultContent = appSettings.showAdultContent,
                        titleLanguage = appSettings.titleLanguage,
                        scoreFormat = appSettings.scoreFormat,
                        hideScores = appSettings.hideScores,
                        onBackClick = { navState.popDetailStack() },
                        onOpenApiDetail = onOpenApiDetail,
                        onAddSelectionToList = onAddApiSelectionToList,
                        onOpenPluginPicker = { navState.navigateToDetail(DetailScreen.PluginPicker) },
                        onOpenAddonExplore = { apiName -> navState.navigateToDetail(DetailScreen.AddonExplore(apiName)) },
                        onOpenCharacterDetail = { characterId, name, imageUrl ->
                            navState.navigateToDetail(DetailScreen.CharacterDetail(characterId, "anilist", name, imageUrl))
                        },
                        onOpenStaffDetail = { staffId, name, imageUrl ->
                            navState.navigateToDetail(DetailScreen.StaffDetail(staffId, "anilist", name, imageUrl))
                        },
                        onOpenStudioDetail = { studioId, source, name, imageUrl ->
                            navState.navigateToDetail(DetailScreen.StudioDetail(studioId, source, name, imageUrl))
                        }
                    )
                }
            }

            is AppStateKey.MangaReader,
            is AppStateKey.MangaDetail,
            is AppStateKey.MangaSourceHealth,
            is AppStateKey.MangaBrowse -> {
                AppRootMangaPages(
                    key = key,
                    navState = navState,
                    mangaViewModel = mangaViewModel,
                    mangaBrowseViewModel = mangaBrowseViewModel,
                    coroutineScope = coroutineScope,
                    onExportMangaSourceReportFile = exportMangaSourceReportFile
                )
            }

            is AppStateKey.Tab -> {
                AppRootTabPages(
                    key = key,
                    ctx = TabPagesContext(
                        mediaEntries = mediaEntries,
                        appSettings = appSettings,
                        settingsDataStore = settingsDataStore,
                        appViewModel = appViewModel,
                        exploreViewModel = exploreViewModel,
                        searchViewModel = searchViewModel,
                        authViewModel = authViewModel,
                        profileViewModel = profileViewModel,
                        kitsugiProfileViewModel = kitsugiProfileViewModel,
                        playerSettingsViewModel = playerSettingsViewModel,
                        updateViewModel = updateViewModel,
                        addonViewModel = addonViewModel,
                        mangaViewModel = mangaViewModel,
                        mediaRepository = mediaRepository,
                        coroutineScope = coroutineScope,
                        navState = navState,
                        backupText = backupText,
                        importText = importText,
                        importMode = importMode,
                        onPickProfileImageClick = { profileImagePickerLauncher.launch("image/*") },
                        onPickBannerImageClick = { bannerImagePickerLauncher.launch("image/*") },
                        onExportBackupFileClick = onExportBackupFileClick,
                        onImportBackupFileClick = onImportBackupFileClick,
                        onOpenApiDetail = onOpenApiDetail,
                        onAddApiSelectionToList = onAddApiSelectionToList,
                        onSeeAllSection = onSeeAllSection,
                        onNavigateToWatchHistory = { navState.navigateToDetail(DetailScreen.WatchHistory) },
                        onOpenMangaReader = onOpenMangaReader,
                        onEditEntry = onEditEntry,
                        onSearchByGenre = triggerSearchByGenre,
                        onSearchByTag = triggerSearchByTag,
                        bottomBarScrollState = bottomBarScrollState,
                        onScrollReset = { bottomBarScrollState.show() }
                    )
                )
            }
        }
    }
}