package com.kitsugi.animelist.ui.screens.notifications

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.notifications.NotificationDiagnostics
import com.kitsugi.animelist.data.remote.KitsugiAniListNotificationClient
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.components.PlatformErrorCodeChip
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import com.kitsugi.animelist.data.remote.malweb.MalNotificationCategory
import com.kitsugi.animelist.utils.KitsugiStrings

// ─── Platform seçimi ──────────────────────────────────────────────────────────

private enum class NotifPlatform(
    val label: String,
    val logoId: String,
    val description: String
) {
    ANILIST(
        label = "AniList",
        logoId = "anilist",
        description = KitsugiStrings.get(R.string.yayin_takvimi_aktivite_begenileri_yorumlar_ve_forum_bildirimleri)
    ),
    MAL(
        label = "MyAnimeList",
        logoId = "mal",
        description = KitsugiStrings.get(R.string.kulup_mesajlari_arkadaslik_istekleri_ve_liste_bildirimleri)
    ),
    TMDB_SIMKL(
        label = "TMDB & Simkl",
        logoId = "simkl",
        description = KitsugiStrings.get(R.string.dizi_film_ve_anime_yeni_bolum_yayin_bildirimleri)
    ),
    KITSU(
        label = "Kitsu",
        logoId = "kitsu",
        description = KitsugiStrings.get(R.string.topluluk_etkilesimleri_yorumlar_ve_takipci_bildirimleri)
    ),
    SHIKIMORI(
        label = "Shikimori",
        logoId = "shikimori",
        description = KitsugiStrings.get(R.string.kullanici_mesajlari_kulup_ve_yayin_guncellemeleri)
    ),
    BANGUMI(
        label = "Bangumi",
        logoId = "bangumi",
        description = KitsugiStrings.get(R.string.izleme_listesi_yayin_takvimi_bildirimleri)
    )
}

// ─── Ana Ekran ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun KitsugiNotificationsScreen(
    mediaEntries: List<MediaEntry>,
    isAniListConnected: Boolean,
    isMalConnected: Boolean,
    isSimklConnected: Boolean,
    isKitsuConnected: Boolean = false,
    isShikimoriConnected: Boolean = false,
    isBangumiConnected: Boolean = false,
    onBack: () -> Unit,
    onOpenApiDetail: ((mediaId: Int, source: String, mediaType: String?, title: String?, imageUrl: String?) -> Unit)? = null,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)? = null,
    onOpenMalAccountSettings: () -> Unit = {},
    /** MAL tek oturum kurulduğunda artan sayaç — bildirimler kendiliğinden tazelenir. */
    malSessionRefreshToken: Int = 0,
    viewModel: KitsugiNotificationsViewModel = viewModel()
) {
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val appSettings by settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val aniListToken = remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(context) }
    val apiClient = remember(aniListToken) { com.kitsugi.animelist.data.remote.JikanApiClient(aniListToken) }
    var activeActivityIdForDetail by remember { mutableStateOf<Int?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showSourcePickerSheet by remember { mutableStateOf(false) }

    // ── ViewModel state'lerini topla ──
    val malState       by viewModel.mal.collectAsState()
    val simklState     by viewModel.tmdbSimkl.collectAsState()
    val kitsuState     by viewModel.kitsu.collectAsState()
    val shikimoriState by viewModel.shikimori.collectAsState()
    val bangumiState   by viewModel.bangumi.collectAsState()
    val aniListUnread  by viewModel.aniListUnread.collectAsState()
    val diagState      by viewModel.diagnostics.collectAsState()
    val resolvingMediaTitle by viewModel.resolvingMedia.collectAsState()

    // ── Sayfa + filtre state ──
    val pagerState = rememberPagerState(
        initialPage = when {
            isAniListConnected   -> 0
            isMalConnected       -> 1
            isSimklConnected     -> 2
            isKitsuConnected     -> 3
            isShikimoriConnected -> 4
            isBangumiConnected   -> 5
            else                 -> 0
        },
        pageCount = { 6 }
    )

    val aniListPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { AniListFilter.entries.size }
    )
    val currentAniListFilter = AniListFilter.entries[aniListPagerState.currentPage]

    // ── İlk yükleme — sayfa, filtre veya başlık dili değiştiğinde ──
    // Bangumi koleksiyon/takvim uçları yalnızca özgün (CJK) başlığı döndürür; başlık dili
    // ayarı değişince Bangumi sekmesi yeniden yüklenmezse eski dildeki/özgün başlık ekranda
    // kalıcı kalır (bkz. kullanıcı bildirimi — "Bangumi'nin her yerde seçili dile uyması").
    LaunchedEffect(pagerState.currentPage, currentAniListFilter, appSettings.titleLanguage) {
        when (pagerState.currentPage) {
            0 -> if (isAniListConnected) viewModel.loadAniList(
                filter = currentAniListFilter,
                resetPage = true,
                mediaEntries = mediaEntries
            )
            1 -> viewModel.loadMal(mediaEntries)
            2 -> if (isSimklConnected) viewModel.loadTmdbSimkl(mediaEntries)
            3 -> if (isKitsuConnected) viewModel.loadKitsu(mediaEntries)
            4 -> if (isShikimoriConnected) viewModel.loadShikimori(mediaEntries)
            5 -> if (isBangumiConnected) viewModel.loadBangumi(mediaEntries, appSettings.titleLanguage)
        }
    }

    // Tek giriş tamamlandı: web oturumu (bildirimler) artık aynı hesapta. Eski hâlde liste
    // "yeniden giriş yapın" kartında kilitli kalıyordu; sinyal gelince kendini tazeler.
    LaunchedEffect(malSessionRefreshToken) {
        if (malSessionRefreshToken > 0 && isMalConnected) viewModel.loadMal(mediaEntries)
    }

    // A missing or mismatched website session is resolved from Settings → MAL account,
    // where the shared in-app OAuth WebView can complete/reconnect the account.

    val malListState       = rememberLazyListState()
    val tmdbSimklListState = rememberLazyListState()
    val kitsuListState     = rememberLazyListState()
    val shikimoriListState = rememberLazyListState()
    val bangumiListState   = rememberLazyListState()

    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen  = isLandscape || configuration.screenWidthDp > 600

    fun refreshCurrentPage() {
        when (pagerState.currentPage) {
            0 -> viewModel.loadAniList(currentAniListFilter, resetPage = true, mediaEntries = mediaEntries)
            1 -> viewModel.loadMal(mediaEntries)
            2 -> viewModel.loadTmdbSimkl(mediaEntries)
            3 -> viewModel.loadKitsu(mediaEntries)
            4 -> viewModel.loadShikimori(mediaEntries)
            5 -> viewModel.loadBangumi(mediaEntries, appSettings.titleLanguage)
        }
    }

    /** Güvenli MAL bağlantısını sistem tarayıcısında açar. */
    fun openTargetInBrowser(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    /**
     * MAL bildirim kartı tıklaması.
     *
     * Kart başlığı MAL'ın **kategori adıdır** ("New Related Anime"); detay sayfası daha önce
     * bu başlıkla ve yalnızca URL'den çıkarılan kimlikle açılıyordu. Artık detay, herhangi bir
     * MAL detay sayfası gibi **medyanın gerçek adı + MAL kimliği** ile açılır:
     *
     *  1. Kimlik varsa (URL'den) doğrudan API detayı açılır.
     *  2. Kimlik yoksa ad üzerinden normal arama API'siyle kimlik çözülür.
     *  3. Medya bilgisi hiç yoksa (yorum, arkadaşlık, forum…) bağlantı tarayıcıda açılır.
     */
    fun openMalNotification(notif: NotifItem) {
        val openDetail = onOpenApiDetail ?: run {
            notif.targetUrl?.let { openTargetInBrowser(it) }
            return
        }
        val realMediaTitle = notif.mediaTitle?.takeIf { it.isNotBlank() }
        val mediaId = notif.mediaId
        when {
            mediaId != null ->
                openDetail(mediaId, "jikan", notif.mediaType, realMediaTitle, notif.imageUrl)

            realMediaTitle != null -> viewModel.resolveMalMedia(realMediaTitle, notif.mediaType) { resolved ->
                if (resolved != null) {
                    openDetail(
                        resolved.malId,
                        "jikan",
                        notif.mediaType,
                        resolved.title,
                        resolved.imageUrl ?: notif.imageUrl
                    )
                } else {
                    notif.targetUrl?.let { openTargetInBrowser(it) }
                }
            }

            else -> notif.targetUrl?.let { openTargetInBrowser(it) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
            .statusBarsPadding()
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.ArrowBack,
                    contentDescription = stringResource(R.string.notif_action_back),
                    tint = KitsugiColors.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.notif_title),
                color = KitsugiColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            IconButton(onClick = {
                showDiagnostics = true
                viewModel.runDiagnostics(mediaEntries)
            }) {
                Icon(
                    imageVector = Icons.BugReport,
                    contentDescription = stringResource(R.string.notif_diag_action),
                    tint = KitsugiColors.TextPrimary
                )
            }
            IconButton(onClick = { refreshCurrentPage() }) {
                Icon(
                    imageVector = Icons.Refresh,
                    contentDescription = stringResource(R.string.notif_action_refresh),
                    tint = accentColor
                )
            }
        }

        val currentPlatform = NotifPlatform.entries.getOrElse(pagerState.currentPage) { NotifPlatform.ANILIST }
        val isCurrentConnected = when (currentPlatform) {
            NotifPlatform.ANILIST    -> isAniListConnected
            NotifPlatform.MAL        -> isMalConnected || !malState.requiresMalWebLogin
            NotifPlatform.TMDB_SIMKL -> isSimklConnected
            NotifPlatform.KITSU      -> isKitsuConnected
            NotifPlatform.SHIKIMORI  -> isShikimoriConnected
            NotifPlatform.BANGUMI    -> isBangumiConnected
        }

        // ── Platform Seçici Buton (Listem sayfası açılır panel tarzı) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isLandscape) 18.dp else 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NotificationSourceSelectorPill(
                selectedPlatform = currentPlatform,
                isConnected = isCurrentConnected,
                unreadCount = if (currentPlatform == NotifPlatform.ANILIST) (aniListUnread ?: 0) else 0,
                accentColor = accentColor,
                onClick = { showSourcePickerSheet = true }
            )
        }

        HorizontalDivider(
            color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Pager ──
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                // ──────────────── Page 0: AniList ────────────────
                0 -> {
                    if (!isAniListConnected) {
                        CenteredEmptyState(stringResource(R.string.notif_login_required_anilist))
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AniListFilterHeader(
                                activeFilter = currentAniListFilter,
                                onFilterSelected = { filter ->
                                    scope.launch {
                                        aniListPagerState.animateScrollToPage(filter.ordinal)
                                    }
                                },
                                isWideScreen = isWideScreen,
                                accentColor = accentColor
                            )

                            HorizontalPager(
                                state = aniListPagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) { filterPage ->
                                val filter = AniListFilter.entries[filterPage]
                                val filterState by viewModel.getAniListState(filter).collectAsState()
                                val listState = rememberLazyListState()

                                // Infinite scroll for this specific filter list
                                val shouldLoadMore by remember {
                                    derivedStateOf {
                                        val total = listState.layoutInfo.totalItemsCount
                                        val last  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        last >= total - 5 && total > 0
                                    }
                                }
                                LaunchedEffect(shouldLoadMore) {
                                    if (shouldLoadMore && !filterState.isLoading && filterState.hasMore) {
                                        viewModel.loadAniList(
                                            filter = filter,
                                            resetPage = false,
                                            mediaEntries = mediaEntries
                                        )
                                    }
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    when {
                                        filterState.isLoading && filterState.items.isEmpty() -> {
                                            item { LoadingState(accentColor) }
                                        }
                                        filterState.error != null && filterState.items.isEmpty() -> {
                                            val errText = filterState.error.orEmpty()
                                            item { CenteredEmptyState(errText) }
                                            filterState.errorCode?.let { code ->
                                                item(key = "filter_error_code_${filter}") {
                                                    PlatformErrorCodeChip(code = code, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                                                }
                                            }
                                        }
                                        filterState.items.isEmpty() -> {
                                            item { CenteredEmptyState(stringResource(R.string.notif_empty_no_notifications)) }
                                        }
                                        else -> {
                                            itemsIndexed(filterState.items, key = { idx, it -> "${idx}_${it.id}" }) { _, notif ->
                                                NotifItemRow(
                                                    notif = notif,
                                                    accentColor = accentColor,
                                                    onUserProfileClick = onUserProfileClick,
                                                    onClick = {
                                                        if (notif.activityId != null && notif.activityId > 0) {
                                                            activeActivityIdForDetail = notif.activityId
                                                        } else if (notif.mediaId != null) {
                                                            onOpenApiDetail?.invoke(notif.mediaId, "anilist", notif.mediaType, notif.mediaTitle ?: notif.title, notif.imageUrl)
                                                        } else if (notif.userId != null && notif.userName != null) {
                                                            onUserProfileClick?.invoke(notif.userId, notif.userName, notif.userAvatarUrl)
                                                        }
                                                    }
                                                )
                                            }
                                            // Sayfa sonu yükleyici
                                            if (filterState.isLoading) {
                                                item {
                                                    Box(
                                                        Modifier.fillMaxWidth().padding(16.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        KitsugiPlasmaLoader(size = 26.dp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ──────────────── Page 1: MAL ────────────────
                1 -> {
                    val malCategory by viewModel.malCategory.collectAsState()
                    val malCounts = remember(malState, malCategory) { viewModel.getMalCategoryCounts() }
                    Column(modifier = Modifier.fillMaxSize()) {
                        MalFilterHeader(
                            selectedCategory = malCategory,
                            counts = malCounts,
                            accentColor = accentColor,
                            onCategorySelected = { cat -> viewModel.setMalFilter(cat) }
                        )
                        SourceNotifPage(
                            state = malState,
                            // Keep the native feed visible; website-session repair is routed through the shared account connection.
                            isConnected = true,
                            loginRequiredMessage = stringResource(R.string.notif_login_required_mal),
                            emptyMessage = if (malCategory == MalNotificationCategory.ALL) {
                                stringResource(R.string.notif_empty_no_notifications)
                            } else {
                                stringResource(R.string.mal_notif_filter_empty)
                            },
                            accentColor = accentColor,
                            listState = malListState,
                            onUserProfileClick = onUserProfileClick,
                            malWebSessionTitle = stringResource(
                                when {
                                    malState.malAccountMismatch -> R.string.notif_mal_account_mismatch_action
                                    malState.requiresMalWebLogin || !isMalConnected -> R.string.notif_mal_web_connect
                                    else -> R.string.notif_mal_web_refresh
                                }
                            ),
                            malWebSessionHint = when {
                                malState.malAccountMismatch -> stringResource(
                                    R.string.notif_mal_account_mismatch_hint,
                                    malState.malOAuthUsername ?: "—",
                                    malState.malWebUsername ?: "—"
                                )
                                malState.requiresMalWebLogin || !isMalConnected -> stringResource(R.string.notif_mal_web_login_hint)
                                else -> stringResource(R.string.notif_mal_web_refresh_hint)
                            },
                            onMalWebSessionAction = {
                                if (malState.malAccountMismatch || malState.requiresMalWebLogin || !isMalConnected) {
                                    onOpenMalAccountSettings()
                                } else {
                                    viewModel.loadMal(mediaEntries)
                                }
                            },
                            onItemClick = { notif -> openMalNotification(notif) },
                            // Daha eski bildirimler varsa kaydırdıkça gelir.
                            hasMore = malState.hasMore,
                            onLoadMore = { viewModel.loadMalMore() }
                        )
                    }
                }

                // ──────────────── Page 2: TMDB & Simkl ────────────────
                2 -> SourceNotifPage(
                    state = simklState,
                    isConnected = isSimklConnected,
                    loginRequiredMessage = stringResource(R.string.notif_login_required_simkl),
                    emptyMessage = stringResource(R.string.notif_empty_list_simkl),
                    accentColor = accentColor,
                    listState = tmdbSimklListState,
                    onUserProfileClick = onUserProfileClick,
                    onItemClick = { notif ->
                        notif.mediaId?.let { id ->
                            onOpenApiDetail?.invoke(id, "simkl", notif.mediaType, notif.mediaTitle ?: notif.title, notif.imageUrl)
                        }
                    }
                )

                // ──────────────── Page 3: Kitsu ────────────────
                3 -> SourceNotifPage(
                    state = kitsuState,
                    isConnected = isKitsuConnected,
                    loginRequiredMessage = stringResource(R.string.notif_notice_kitsu_not_connected),
                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                    accentColor = accentColor,
                    listState = kitsuListState,
                    onUserProfileClick = onUserProfileClick,
                    onItemClick = { notif ->
                        notif.mediaId?.let { id ->
                            onOpenApiDetail?.invoke(id, "kitsu", notif.mediaType, notif.mediaTitle ?: notif.title, notif.imageUrl)
                        }
                    }
                )

                // ──────────────── Page 4: Shikimori ────────────────
                4 -> SourceNotifPage(
                    state = shikimoriState,
                    isConnected = isShikimoriConnected,
                    loginRequiredMessage = stringResource(R.string.notif_login_required_shikimori),
                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                    accentColor = accentColor,
                    listState = shikimoriListState,
                    onUserProfileClick = onUserProfileClick,
                    onItemClick = { notif ->
                        notif.mediaId?.let { id ->
                            onOpenApiDetail?.invoke(id, "shikimori", notif.mediaType, notif.mediaTitle ?: notif.title, notif.imageUrl)
                        }
                    }
                )

                // ──────────────── Page 5: Bangumi ────────────────
                5 -> SourceNotifPage(
                    state = bangumiState,
                    isConnected = isBangumiConnected,
                    loginRequiredMessage = stringResource(R.string.notif_login_required_bangumi),
                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                    accentColor = accentColor,
                    listState = bangumiListState,
                    onUserProfileClick = onUserProfileClick,
                    onItemClick = { notif ->
                        // mediaId Bangumi stableId'sidir (500M+); detay yönlendirmesi
                        // bu kimlikle yerel kaydı bulur, bulamazsa API detay açar.
                        notif.mediaId?.let { id ->
                            onOpenApiDetail?.invoke(id, "bangumi", notif.mediaType, notif.mediaTitle ?: notif.title, notif.imageUrl)
                        }
                    }
                )
            }
        }
    }

    activeActivityIdForDetail?.let { actId ->
        com.kitsugi.animelist.ui.components.KitsugiActivityDetailBottomSheet(
            activityId = actId,
            apiClient = apiClient,
            onUserProfileClick = { uid, uname, uavatar, _ ->
                if (uid != null) {
                    onUserProfileClick?.invoke(uid, uname, uavatar)
                }
            },
            onMediaClick = { mediaId, type, source ->
                onOpenApiDetail?.invoke(mediaId, source, type.name, null, null)
            },
            onDismiss = { activeActivityIdForDetail = null }
        )
    }

    // Bildirimin medya kimliği çözülürken kısa bir gösterge; detay o kimlikle açılır.
    if (resolvingMediaTitle != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KitsugiPlasmaLoader(size = 40.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.notif_resolving_media),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }


    if (showDiagnostics) {
        NotificationDiagnosticsSheet(
            state = diagState,
            onRerun = { viewModel.runDiagnostics(mediaEntries) },
            onDismiss = { showDiagnostics = false }
        )
    }

    if (showSourcePickerSheet) {
        NotificationSourcePickerSheet(
            selectedPlatformIndex = pagerState.currentPage,
            isAniListConnected = isAniListConnected,
            isMalConnected = isMalConnected || !malState.requiresMalWebLogin,
            isSimklConnected = isSimklConnected,
            isKitsuConnected = isKitsuConnected,
            isShikimoriConnected = isShikimoriConnected,
            isBangumiConnected = isBangumiConnected,
            aniListUsername = appSettings.anilistUsername,
            malUsername = appSettings.malUsername,
            simklUsername = appSettings.simklUsername,
            kitsuUsername = appSettings.kitsuUsername,
            shikimoriUsername = appSettings.shikimoriUsername,
            bangumiUsername = appSettings.bangumiUsername,
            aniListUnread = aniListUnread ?: 0,
            accentColor = accentColor,
            onSelectPlatform = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            onDismiss = { showSourcePickerSheet = false }
        )
    }
}

// ─── Kaynak sayfası (MAL / Simkl / Kitsu / Shikimori) ─────────────────────────

@Composable
private fun SourceNotifPage(
    state: NotifUiState,
    isConnected: Boolean,
    loginRequiredMessage: String,
    emptyMessage: String,
    accentColor: Color,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)?,
    onItemClick: (NotifItem) -> Unit,
    malWebSessionTitle: String? = null,
    malWebSessionHint: String? = null,
    onMalWebSessionAction: (() -> Unit)? = null,
    /** Kaynağın daha eski bildirimleri var mı? (kaydırdıkça istenir) */
    hasMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    val hasMalWebSessionAction =
        malWebSessionTitle != null && malWebSessionHint != null && onMalWebSessionAction != null

    if (!isConnected && state.items.isEmpty() && !hasMalWebSessionAction) {
        CenteredEmptyState(loginRequiredMessage)
        return
    }

    // Sonsuz kaydırma: listenin sonuna yaklaşıldığında kaynağın sonraki sayfası istenir.
    // MAL'ın web akışında akışın bittiği kararı veriyle verilir (ViewModel.loadMalMore).
    val nearListEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            (total == 0 && hasMore) || (total > 0 && last >= total - 8)
        }
    }
    LaunchedEffect(nearListEnd, hasMore, state.isLoading, state.loadMoreError) {
        if (nearListEnd && hasMore && !state.isLoading && state.loadMoreError == null) {
            onLoadMore?.invoke()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        if (hasMalWebSessionAction && malWebSessionTitle != null && malWebSessionHint != null && onMalWebSessionAction != null) {
            val sessionTitle = malWebSessionTitle
            val sessionHint = malWebSessionHint
            val sessionAction = onMalWebSessionAction
            item(key = "mal_web_session_action") {
                MalWebSessionActionCard(
                    title = sessionTitle,
                    hint = sessionHint,
                    accentColor = accentColor,
                    onClick = sessionAction
                )
            }
        }

        if (!isConnected && state.items.isEmpty()) {
            item(key = "login_required") { SourceNoticeBanner(text = loginRequiredMessage) }
        } else if (!state.notice.isNullOrBlank()) {
            item(key = "notice") { SourceNoticeBanner(text = state.notice) }
        }

        // MAL's account feed is fetched by the app and rendered in this native list.
        if (isConnected || state.items.isNotEmpty() || !hasMalWebSessionAction) {
            when {
                state.isLoading && state.items.isEmpty() -> {
                    item { LoadingState(accentColor) }
                }
                state.error != null && state.items.isEmpty() -> {
                    val errText = state.error.orEmpty()
                    item { CenteredEmptyState(errText) }
                    state.errorCode?.let { code ->
                        item(key = "state_error_code") {
                            PlatformErrorCodeChip(code = code, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                        }
                    }
                }
                state.items.isEmpty() -> {
                    item { CenteredEmptyState(emptyMessage) }
                    state.loadMoreError?.let { message ->
                        item(key = "mal_load_more_error_empty") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.weight(1f),
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 12.sp
                                )
                                TextButton(onClick = { onLoadMore?.invoke() }) {
                                    Text(stringResource(R.string.notif_mal_load_more_retry))
                                }
                                state.loadMoreErrorCode?.let { code -> PlatformErrorCodeChip(code = code) }
                            }
                        }
                    }
                }
                else -> {
                    itemsIndexed(state.items, key = { idx, it -> "${idx}_${it.id}" }) { _, notif ->
                        NotifItemRow(
                            notif = notif,
                            accentColor = accentColor,
                            onUserProfileClick = onUserProfileClick,
                            onClick = { onItemClick(notif) }
                        )
                    }
                    if (state.isLoading) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                KitsugiPlasmaLoader(size = 26.dp)
                            }
                        }
                    }
                    state.loadMoreError?.let { message ->
                        item(key = "mal_load_more_error") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.weight(1f),
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 12.sp
                                )
                                TextButton(onClick = { onLoadMore?.invoke() }) {
                                    Text(stringResource(R.string.notif_mal_load_more_retry))
                                }
                                state.loadMoreErrorCode?.let { code -> PlatformErrorCodeChip(code = code) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MalWebSessionActionCard(
    title: String,
    hint: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.NotificationsNone,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = hint,
                    color = KitsugiColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─── Kaynak bilgi notu (neden boş / hangi uç nokta kullanılıyor) ──────────────

@Composable
private fun SourceNoticeBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Info,
            contentDescription = null,
            tint = KitsugiColors.TextMuted,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

// ─── Bildirim Teşhisi Paneli ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationDiagnosticsSheet(
    state: NotifDiagnosticsUiState,
    onRerun: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = LocalKitsugiAccent.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KitsugiColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.notif_diag_title),
                color = KitsugiColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.notif_diag_subtitle),
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(14.dp))

            if (state.isRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitsugiPlasmaLoader(size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.notif_diag_running),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = state.error, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B), fontSize = 12.sp)
                state.errorCode?.let { code -> PlatformErrorCodeChip(code = code, modifier = Modifier.padding(top = 4.dp)) }
            }

            state.rows.forEach { row ->
                DiagnosticRow(row = row)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KitsugiTonalButtonCompat(
                    text = stringResource(R.string.notif_diag_run),
                    enabled = !state.isRunning,
                    onClick = onRerun
                )
                KitsugiTonalButtonCompat(
                    text = stringResource(R.string.notif_diag_close),
                    enabled = true,
                    onClick = onDismiss,
                    tint = accentColor
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(row: NotificationDiagnostics.Row) {
    val statusColor = when {
        !row.connected -> KitsugiColors.TextMuted
        row.ok -> KitsugiColors.AccentGreen
        else -> Color(0xFFFF6B6B)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.35f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            KitsugiPlatformLogo(platformId = row.source, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = row.source,
                color = KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    if (row.ok) R.string.notif_diag_ok else R.string.notif_diag_fail
                ),
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.notif_diag_endpoint) + ": " + row.endpoint,
            color = KitsugiColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!row.connected) {
                Text(
                    text = stringResource(R.string.notif_diag_not_connected),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            } else {
                Text(
                    text = stringResource(R.string.notif_diag_connected),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
            row.httpStatus?.let {
                Text(
                    text = stringResource(R.string.notif_diag_status, it),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
            row.itemCount?.let {
                Text(
                    text = stringResource(R.string.notif_diag_items, it),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
            Text(
                text = stringResource(R.string.notif_diag_duration, row.durationMs),
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = row.detail,
            color = KitsugiColors.TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        row.error?.let { error ->
            Spacer(Modifier.height(3.dp))
            Text(text = error, color = Color(0xFFFF6B6B), fontSize = 11.sp, lineHeight = 15.sp)
        }
        row.hint?.let { hint ->
            Spacer(Modifier.height(3.dp))
            Text(text = hint, color = KitsugiColors.TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        // Kaynağa özgü hata kodu + "neden çıktı / ne yapmalı" açıklaması.
        // Kod olmadan kullanıcı hatanın nedenini tahmin etmek zorunda kalıyordu.
        PlatformErrorCode.fromCode(row.errorCode)?.let { code ->
            Spacer(Modifier.height(6.dp))
            PlatformErrorExplanationBlock(code = code, tint = statusColor)
        }
    }
}

/** Teşhis panelindeki basit buton — Material3 TextButton'ın tema uyumlu sarmalayıcısı. */
@Composable
private fun KitsugiTonalButtonCompat(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = LocalKitsugiAccent.current
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.5f))
    ) {
        Text(text = text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── AniList Alt Filtre Header ────────────────────────────────────────────────

@Composable
private fun AniListFilterHeader(
    activeFilter: AniListFilter,
    onFilterSelected: (AniListFilter) -> Unit,
    isWideScreen: Boolean,
    accentColor: Color
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rowMod = if (isWideScreen) {
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface)
                    .padding(4.dp)
            } else {
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface)
                    .horizontalScroll(scrollState)
                    .padding(4.dp)
            }
            Row(
                modifier = rowMod,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isWideScreen) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(4.dp)
            ) {
                AniListFilter.entries.forEach { filter ->
                    val active    = activeFilter == filter
                    val itemMod   = if (isWideScreen) Modifier.weight(1f) else Modifier
                    Box(
                        modifier = itemMod
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (active) accentColor else Color.Transparent)
                            .tvClickable(shape = RoundedCornerShape(22.dp)) { onFilterSelected(filter) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(filter.labelResId),
                            color = if (active) KitsugiColors.Background else KitsugiColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── MAL Alt Filtre Header (myanimelist.net/notification sol menüsü) ─────────

@Composable
private fun MalFilterHeader(
    selectedCategory: MalNotificationCategory,
    counts: Map<MalNotificationCategory, Int>,
    accentColor: Color,
    onCategorySelected: (MalNotificationCategory) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface)
                    .horizontalScroll(scrollState)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MalNotificationCategory.entries.forEach { cat ->
                    val active = selectedCategory == cat
                    val count = counts[cat] ?: 0
                    // Web'de 0 olan kategoriler de gösterilir — kullanıcı tüm filtrelerin varlığını görür
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (active) accentColor else Color.Transparent)
                            .tvClickable(shape = RoundedCornerShape(22.dp)) { onCategorySelected(cat) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(cat.labelResId),
                                color = if (active) KitsugiColors.Background else KitsugiColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (active) KitsugiColors.Background.copy(alpha = 0.22f)
                                            else accentColor.copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = count.toString(),
                                        color = if (active) KitsugiColors.Background else accentColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Bildirim Satır Kartı ─────────────────────────────────────────────────────


@Composable
private fun NotifItemRow(
    notif: NotifItem,
    accentColor: Color,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(if (notif.userId != null) CircleShape else RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceStrong)
                .then(
                    if (notif.userId != null && notif.userName != null && onUserProfileClick != null) {
                        Modifier.tvClickable(shape = CircleShape) {
                            onUserProfileClick(notif.userId, notif.userName, notif.userAvatarUrl)
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!notif.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = notif.imageUrl,
                    contentDescription = notif.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.NotificationsNone,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            // Okunmamış göstergesi (Shikimori mesajları)
            if (notif.isUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = notif.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    KitsugiPlatformLogo(platformId = notif.source, size = 12.dp)
                    Text(
                        text = notif.source,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = notif.body,
                color = KitsugiColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!notif.dateText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = notif.dateText, color = KitsugiColors.TextMuted, fontSize = 11.sp)
            }
        }
    }
    HorizontalDivider(
        color = KitsugiColors.SurfaceStrong.copy(alpha = 0.35f),
        modifier = Modifier.padding(start = 80.dp, end = 16.dp)
    )
}

// ─── Yardımcı Composable'lar ──────────────────────────────────────────────────

@Composable
private fun LoadingState(accentColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KitsugiPlasmaLoader(size = 48.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.loading), color = KitsugiColors.TextMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CenteredEmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Notifications,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                color = KitsugiColors.TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

// ─── Bildirim Kaynak Seçici Hap Buton ─────────────────────────────────────────

@Composable
private fun NotificationSourceSelectorPill(
    selectedPlatform: NotifPlatform,
    isConnected: Boolean,
    unreadCount: Int,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KitsugiPlatformLogo(
            platformId = selectedPlatform.logoId,
            size = 18.dp
        )
        Text(
            text = selectedPlatform.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = KitsugiColors.TextPrimary
            )
        )
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accentColor)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = KitsugiColors.Background,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (isConnected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(KitsugiColors.AccentGreen)
            )
        }
        Icon(
            imageVector = Icons.ArrowDropDown,
            contentDescription = stringResource(R.string.bildirim_kaynagi_sec),
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── Bildirim Kaynak Seçim Modal Bottom Sheet (Listem Sayfası Tarzı) ──────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSourcePickerSheet(
    selectedPlatformIndex: Int,
    isAniListConnected: Boolean,
    isMalConnected: Boolean,
    isSimklConnected: Boolean,
    isKitsuConnected: Boolean,
    isShikimoriConnected: Boolean,
    isBangumiConnected: Boolean,
    aniListUsername: String,
    malUsername: String,
    simklUsername: String,
    kitsuUsername: String,
    shikimoriUsername: String,
    bangumiUsername: String,
    aniListUnread: Int,
    accentColor: Color,
    onSelectPlatform: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KitsugiColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.bildirim_kaynagi_sec),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            )
            Text(
                text = stringResource(R.string.inline_choose_the_platform_notifications__87df9fe),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = KitsugiColors.TextMuted
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            NotifPlatform.entries.forEachIndexed { index, platform ->
                val isSelected = index == selectedPlatformIndex
                val isConnected = when (platform) {
                    NotifPlatform.ANILIST -> isAniListConnected
                    NotifPlatform.MAL -> isMalConnected
                    NotifPlatform.TMDB_SIMKL -> isSimklConnected
                    NotifPlatform.KITSU -> isKitsuConnected
                    NotifPlatform.SHIKIMORI -> isShikimoriConnected
                    NotifPlatform.BANGUMI -> isBangumiConnected
                }
                val username = when (platform) {
                    NotifPlatform.ANILIST -> aniListUsername
                    NotifPlatform.MAL -> malUsername
                    NotifPlatform.TMDB_SIMKL -> simklUsername
                    NotifPlatform.KITSU -> kitsuUsername
                    NotifPlatform.SHIKIMORI -> shikimoriUsername
                    NotifPlatform.BANGUMI -> bangumiUsername
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.14f)
                            else KitsugiColors.SurfaceElevated.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .tvClickable(shape = RoundedCornerShape(16.dp)) {
                            onSelectPlatform(index)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KitsugiPlatformLogo(
                        platformId = platform.logoId,
                        size = 30.dp,
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = platform.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary
                                )
                            )
                            if (isConnected) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF27AE60).copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (username.isNotBlank()) "@$username" else stringResource(R.string.notif_diag_connected),
                                        color = Color(0xFF27AE60),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceStrong)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.bagli_degil),
                                        color = KitsugiColors.TextMuted,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (platform == NotifPlatform.ANILIST && aniListUnread > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accentColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.x_yeni, aniListUnread),
                                        color = accentColor,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = platform.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KitsugiColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Check,
                                contentDescription = stringResource(R.string.secili),
                                tint = KitsugiColors.Background,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
