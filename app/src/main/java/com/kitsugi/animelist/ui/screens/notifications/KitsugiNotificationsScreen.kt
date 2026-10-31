package com.kitsugi.animelist.ui.screens.notifications

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.uniqueItems
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.kitsugi.animelist.ui.components.KitsugiDetailActionButton
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiPullToRefreshIndicator
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
    val description: String,
    /** Listem kaynak secicisiyle birebir ayni marka rengi (ortak tema dili). */
    val brandColor: Color
) {
    ANILIST(
        label = "AniList",
        logoId = "anilist",
        description = KitsugiStrings.get(R.string.yayin_takvimi_aktivite_begenileri_yorumlar_ve_forum_bildirimleri),
        brandColor = Color(0xFF02A9FF)
    ),
    MAL(
        label = "MyAnimeList",
        logoId = "mal",
        description = KitsugiStrings.get(R.string.kulup_mesajlari_arkadaslik_istekleri_ve_liste_bildirimleri),
        brandColor = Color(0xFF2E51A2)
    ),
    TMDB_SIMKL(
        label = "TMDB & Simkl",
        logoId = "simkl",
        description = KitsugiStrings.get(R.string.dizi_film_ve_anime_yeni_bolum_yayin_bildirimleri),
        brandColor = Color(0xFFE5A00D)
    ),
    KITSU(
        label = "Kitsu",
        logoId = "kitsu",
        description = KitsugiStrings.get(R.string.topluluk_etkilesimleri_yorumlar_ve_takipci_bildirimleri),
        brandColor = Color(0xFFFD755C)
    ),
    SHIKIMORI(
        label = "Shikimori",
        logoId = "shikimori",
        description = KitsugiStrings.get(R.string.kullanici_mesajlari_kulup_ve_yayin_guncellemeleri),
        brandColor = Color(0xFF8E44AD)
    ),
    BANGUMI(
        label = "Bangumi",
        logoId = "bangumi",
        description = KitsugiStrings.get(R.string.izleme_listesi_yayin_takvimi_bildirimleri),
        brandColor = Color(0xFFF09199)
    ),
    TMDB_ACCOUNT(
        label = "TMDB",
        logoId = "tmdb",
        description = KitsugiStrings.get(R.string.notif_notice_tmdb_account),
        brandColor = Color(0xFF01B4E4)
    )
}

/** Bildirim kartındaki kaynak adından ("Simkl", "MyAnimeList"...) marka rengi. */
private fun notifBrandColorForSource(source: String): Color = when (source.trim().lowercase()) {
    "anilist" -> NotifPlatform.ANILIST.brandColor
    "myanimelist", "mal", "jikan" -> NotifPlatform.MAL.brandColor
    "simkl" -> NotifPlatform.TMDB_SIMKL.brandColor
    "tmdb_account", "tmdb" -> NotifPlatform.TMDB_ACCOUNT.brandColor
    "kitsu" -> NotifPlatform.KITSU.brandColor
    "shikimori" -> NotifPlatform.SHIKIMORI.brandColor
    "bangumi" -> NotifPlatform.BANGUMI.brandColor
    else -> NotifPlatform.ANILIST.brandColor
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
    isTmdbConnected: Boolean = false,
    tmdbUsername: String = "",
    onBack: () -> Unit,
    onOpenApiDetail: ((mediaId: Int, source: String, mediaType: String?, title: String?, imageUrl: String?) -> Unit)? = null,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)? = null,
    onOpenMalAccountSettings: () -> Unit = {},
    /** Bagli olmayan kaynagin "Hesabi Bagla" eylemi (sayfa sirasi: 0=AniList ... 5=Bangumi). */
    onConnectPlatform: ((Int) -> Unit)? = null,
    /** MAL tek oturum kurulduğunda artan sayaç — bildirimler kendiliğinden tazelenir. */
    malSessionRefreshToken: Int = 0,
    /**
     * Akıllı bildirim butonu: kullanıcı bildirim butonuna hangi sekmeden bastıysa
     * (Listem / Keşfet / Arama / Profil) orada seçili olan kaynağın kimliği.
     * Sayfa doğrudan o kaynağın sekmesiyle açılır.
     *
     * `null` (veya "Tümü" / bildirimi olmayan bir kaynak) ise eski davranış korunur:
     * bağlı olan ilk hesabın bildirimleri gösterilir.
     */
    preferredSource: String? = null,
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
    val tmdbAccountState by viewModel.tmdbAccount.collectAsState()
    val aniListUnread  by viewModel.aniListUnread.collectAsState()
    val diagState      by viewModel.diagnostics.collectAsState()
    val resolvingMediaTitle by viewModel.resolvingMedia.collectAsState()

    // ── Sayfa + filtre state ──
    //
    // AKILLI BİLDİRİM BUTONU: butona basılan sekmede (Listem / Keşfet / Arama / Profil)
    // hangi kaynak seçiliyse bildirimler doğrudan o kaynağın sekmesinde açılır.
    // Kaynak "Tümü" ise ya da bildirim sekmesi yoksa eski davranış korunur
    // (bağlı olan ilk hesabın bildirimleri).
    val requestedPage = remember(preferredSource) { notificationPageForSource(preferredSource) }
    val fallbackPage = when {
        isAniListConnected   -> NOTIF_PAGE_ANILIST
        isMalConnected       -> NOTIF_PAGE_MAL
        isSimklConnected     -> NOTIF_PAGE_TMDB_SIMKL
        isKitsuConnected     -> NOTIF_PAGE_KITSU
        isShikimoriConnected -> NOTIF_PAGE_SHIKIMORI
        isBangumiConnected   -> NOTIF_PAGE_BANGUMI
        isTmdbConnected      -> NOTIF_PAGE_TMDB_ACCOUNT
        else                 -> NOTIF_PAGE_ANILIST
    }

    val pagerState = rememberPagerState(
        initialPage = requestedPage ?: fallbackPage,
        pageCount = { 7 }
    )

    // Kaynak kimliği değişirse (ör. arkada farklı bir kaynakla açılmış sayfa yeniden
    // kullanılırsa) sayfa seçili kaynağa kaydırılır. Kullanıcı sayfa içinde kaynağı elle
    // değiştirdiğinde bu efekt tetiklenmez — seçimi korunur.
    LaunchedEffect(requestedPage) {
        if (requestedPage != null && pagerState.currentPage != requestedPage) {
            pagerState.scrollToPage(requestedPage)
        }
    }

    val aniListPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { AniListFilter.entries.size }
    )
    val currentAniListFilter = AniListFilter.entries[aniListPagerState.currentPage]

    // ── İlk yükleme — sayfa, filtre veya başlık dili değiştiğinde ──
    // Bangumi koleksiyon/takvim uçları yalnızca özgün (CJK) başlığı döndürür; başlık dili
    // ayarı değişince Bangumi sekmesi yeniden yüklenmezse eski dildeki/özgün başlık ekranda
    // kalıcı kalır (bkz. kullanıcı bildirimi — "Bangumi'nin her yerde seçili dile uyması").
    // NOT: Bağlantı kapısı yalnızca AniList'te var. Diğer kaynakların ViewModel'i
    // hesap bağlı değilken de yerel kayıt/takvim yedeği üretir (Kitsu, Simkl,
    // Bangumi) ya da giriş gerektiğini state ile söyler (Shikimori); eskiden
    // buradaki `if (isXConnected)` kapıları o yedekleri ulaşılamaz yapıyordu.
    LaunchedEffect(pagerState.currentPage, currentAniListFilter, appSettings.titleLanguage) {
        when (pagerState.currentPage) {
            0 -> if (isAniListConnected) viewModel.loadAniList(
                filter = currentAniListFilter,
                resetPage = true,
                mediaEntries = mediaEntries
            )
            1 -> viewModel.loadMal(mediaEntries)
            2 -> viewModel.loadTmdbSimkl(mediaEntries)
            3 -> viewModel.loadKitsu(mediaEntries)
            4 -> viewModel.loadShikimori(mediaEntries)
            5 -> viewModel.loadBangumi(mediaEntries, appSettings.titleLanguage)
            6 -> if (isTmdbConnected) viewModel.loadTmdbAccount()
        }
    }

    // Tek giriş tamamlandı: web oturumu (bildirimler) artık aynı hesapta. Eski hâlde liste
    // "yeniden giriş yapın" kartında kilitli kalıyordu; sinyal gelince kendini tazeler.
    LaunchedEffect(malSessionRefreshToken) {
        if (malSessionRefreshToken > 0 && isMalConnected) viewModel.loadMal(mediaEntries)
    }

    // A missing or mismatched website session is resolved from Settings → MAL account,
    // where the shared in-app OAuth WebView can complete/reconnect the account.

    // Kaynak seçici + özet satırı için ortak sayaçlar (toplam / okunmamış).
    // Eskiden yalnızca AniList'in okunmamış rozeti vardı; diğer kaynaklar sessizdi.
    val aniListAllState by viewModel.getAniListState(AniListFilter.ALL).collectAsState()
    val malTotalCount = remember(malState) {
        viewModel.getMalCategoryCounts()[MalNotificationCategory.ALL] ?: malState.items.size
    }
    val platformItemCounts: Map<NotifPlatform, Int> = mapOf(
        NotifPlatform.ANILIST to aniListAllState.items.size,
        NotifPlatform.MAL to malTotalCount,
        NotifPlatform.TMDB_SIMKL to simklState.items.size,
        NotifPlatform.KITSU to kitsuState.items.size,
        NotifPlatform.SHIKIMORI to shikimoriState.items.size,
        NotifPlatform.BANGUMI to bangumiState.items.size,
        NotifPlatform.TMDB_ACCOUNT to tmdbAccountState.items.size
    )
    val platformUnreadCounts: Map<NotifPlatform, Int> = mapOf(
        NotifPlatform.ANILIST to (aniListUnread ?: aniListAllState.items.count { it.isUnread }),
        NotifPlatform.MAL to malState.items.count { it.isUnread },
        NotifPlatform.TMDB_SIMKL to simklState.items.count { it.isUnread },
        NotifPlatform.KITSU to kitsuState.items.count { it.isUnread },
        NotifPlatform.SHIKIMORI to shikimoriState.items.count { it.isUnread },
        NotifPlatform.BANGUMI to bangumiState.items.count { it.isUnread },
        NotifPlatform.TMDB_ACCOUNT to tmdbAccountState.items.count { it.isUnread }
    )

    val malListState       = rememberLazyListState()
    val tmdbSimklListState = rememberLazyListState()
    val kitsuListState     = rememberLazyListState()
    val shikimoriListState = rememberLazyListState()
    val bangumiListState   = rememberLazyListState()
    val tmdbAccountListState = rememberLazyListState()

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
            6 -> viewModel.loadTmdbAccount()
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
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = stringResource(R.string.notif_diag_action),
                    tint = KitsugiColors.TextPrimary
                )
            }
            IconButton(onClick = { refreshCurrentPage() }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.notif_action_refresh),
                    tint = NotifPlatform.entries.getOrElse(pagerState.currentPage) { NotifPlatform.ANILIST }.brandColor
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
            NotifPlatform.TMDB_ACCOUNT -> isTmdbConnected
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
                unreadCount = platformUnreadCounts[currentPlatform] ?: 0,
                onClick = { showSourcePickerSheet = true }
            )
            Spacer(Modifier.weight(1f))
            val summaryTotal = platformItemCounts[currentPlatform] ?: 0
            val summaryUnread = platformUnreadCounts[currentPlatform] ?: 0
            if (summaryTotal > 0) {
                Text(
                    text = buildString {
                        append(stringResource(R.string.notif_items_count, summaryTotal))
                        if (summaryUnread > 0) {
                            append(" • ")
                            append(stringResource(R.string.x_yeni, summaryUnread.toString()))
                        }
                    },
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── "Arka planda ne oluyor?" (v2.4.341) ──
        // Bildirimler yalnızca okuma değil; arka plan eşitlemesinin durumu da burada
        // görünür: hangi kaynak neden bekliyor, kaç değişiklik kuyrukta. Sorun yoksa
        // kart hiç çizilmez.
        com.kitsugi.animelist.ui.components.SourceSyncStatusCard(
            modifier = Modifier.padding(horizontal = if (isLandscape) 18.dp else 16.dp)
        )

        HorizontalDivider(
            color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Pager ──
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                // ──────────────── Page 0: AniList ────────────────
                0 -> {
                    if (!isAniListConnected) {
                        NotifStatusState(
                            platform = NotifPlatform.ANILIST,
                            title = stringResource(R.string.notif_login_required_anilist),
                            subtitle = NotifPlatform.ANILIST.description,
                            actionText = if (onConnectPlatform != null) stringResource(R.string.hesabi_bagla) else null,
                            onAction = onConnectPlatform?.let { connect -> { connect(0) } },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AniListFilterHeader(
                                activeFilter = currentAniListFilter,
                                onFilterSelected = { filter ->
                                    scope.launch {
                                        aniListPagerState.animateScrollToPage(filter.ordinal)
                                    }
                                },
                                accentColor = NotifPlatform.ANILIST.brandColor
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

                                NotifListPage(
                                    state = filterState,
                                    isConnected = true,
                                    platform = NotifPlatform.ANILIST,
                                    accentColor = accentColor,
                                    listState = listState,
                                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                                    loginRequiredMessage = stringResource(R.string.notif_login_required_anilist),
                                    onRetry = {
                                        viewModel.loadAniList(
                                            filter = filter,
                                            resetPage = true,
                                            mediaEntries = mediaEntries
                                        )
                                    },
                                    onUserProfileClick = onUserProfileClick,
                                    hasMore = filterState.hasMore,
                                    onLoadMore = {
                                        viewModel.loadAniList(
                                            filter = filter,
                                            resetPage = false,
                                            mediaEntries = mediaEntries
                                        )
                                    },
                                    onItemClick = { notif ->
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
                            accentColor = NotifPlatform.MAL.brandColor,
                            onCategorySelected = { cat -> viewModel.setMalFilter(cat) }
                        )
                        NotifListPage(
                            state = malState,
                            // Native akış her zaman denenir; web oturumu onarımı ayrı karttadır.
                            isConnected = true,
                            platform = NotifPlatform.MAL,
                            accentColor = accentColor,
                            listState = malListState,
                            emptyMessage = if (malCategory == MalNotificationCategory.ALL) {
                                stringResource(R.string.notif_empty_no_notifications)
                            } else {
                                stringResource(R.string.mal_notif_filter_empty)
                            },
                            loginRequiredMessage = stringResource(R.string.notif_login_required_mal),
                            onRetry = { viewModel.loadMal(mediaEntries) },
                            onUserProfileClick = onUserProfileClick,
                            onItemClick = { notif -> openMalNotification(notif) },
                            hasMore = malState.hasMore,
                            onLoadMore = { viewModel.loadMalMore() },
                            topContent = {
                                item(key = "mal_web_session_action") {
                                    MalWebSessionActionCard(
                                        title = stringResource(
                                            when {
                                                malState.malAccountMismatch -> R.string.notif_mal_account_mismatch_action
                                                malState.requiresMalWebLogin || !isMalConnected -> R.string.notif_mal_web_connect
                                                else -> R.string.notif_mal_web_refresh
                                            }
                                        ),
                                        hint = when {
                                            malState.malAccountMismatch -> stringResource(
                                                R.string.notif_mal_account_mismatch_hint,
                                                malState.malOAuthUsername ?: "—",
                                                malState.malWebUsername ?: "—"
                                            )
                                            malState.requiresMalWebLogin || !isMalConnected ->
                                                stringResource(R.string.notif_mal_web_login_hint)
                                            else -> stringResource(R.string.notif_mal_web_refresh_hint)
                                        },
                                        accentColor = NotifPlatform.MAL.brandColor,
                                        onClick = {
                                            if (malState.malAccountMismatch || malState.requiresMalWebLogin || !isMalConnected) {
                                                onOpenMalAccountSettings()
                                            } else {
                                                viewModel.loadMal(mediaEntries)
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }
                }

                // ──────────────── Page 2: TMDB & Simkl ────────────────
                2 -> NotifListPage(
                    state = simklState,
                    isConnected = isSimklConnected,
                    platform = NotifPlatform.TMDB_SIMKL,
                    accentColor = accentColor,
                    listState = tmdbSimklListState,
                    emptyMessage = stringResource(R.string.notif_empty_list_simkl),
                    loginRequiredMessage = stringResource(R.string.notif_login_required_simkl),
                    onRetry = { viewModel.loadTmdbSimkl(mediaEntries) },
                    onUserProfileClick = onUserProfileClick,
                    onConnectClick = onConnectPlatform?.let { connect -> { connect(2) } },
                    onItemClick = { notif ->
                        notif.mediaId?.let { id ->
                            onOpenApiDetail?.invoke(id, "simkl", notif.mediaType, notif.mediaTitle ?: notif.title, notif.imageUrl)
                        }
                    }
                )

                // ──────────────── Page 3: Kitsu ────────────────
                3 -> NotifListPage(
                    state = kitsuState,
                    isConnected = isKitsuConnected,
                    platform = NotifPlatform.KITSU,
                    accentColor = accentColor,
                    listState = kitsuListState,
                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                    loginRequiredMessage = stringResource(R.string.notif_notice_kitsu_not_connected),
                    onRetry = { viewModel.loadKitsu(mediaEntries) },
                    onUserProfileClick = onUserProfileClick,
                    onConnectClick = onConnectPlatform?.let { connect -> { connect(3) } },
                    onItemClick = { notif ->
                        notif.mediaId?.let { id ->
                            onOpenApiDetail?.invoke(id, "kitsu", notif.mediaType, notif.mediaTitle ?: notif.title, notif.imageUrl)
                        }
                    }
                )

                // ──────────────── Page 4: Shikimori ────────────────
                4 -> NotifListPage(
                    state = shikimoriState,
                    isConnected = isShikimoriConnected,
                    platform = NotifPlatform.SHIKIMORI,
                    accentColor = accentColor,
                    listState = shikimoriListState,
                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                    loginRequiredMessage = stringResource(R.string.notif_login_required_shikimori),
                    onRetry = { viewModel.loadShikimori(mediaEntries) },
                    onUserProfileClick = onUserProfileClick,
                    onConnectClick = onConnectPlatform?.let { connect -> { connect(4) } },
                    onItemClick = { notif ->
                        notif.mediaId?.let { id ->
                            onOpenApiDetail?.invoke(id, "shikimori", notif.mediaType, notif.mediaTitle ?: notif.title, notif.imageUrl)
                        }
                    }
                )

                // ──────────────── Page 6: TMDB account changes ────────────────
                6 -> NotifListPage(
                    state = tmdbAccountState,
                    isConnected = isTmdbConnected,
                    platform = NotifPlatform.TMDB_ACCOUNT,
                    accentColor = accentColor,
                    listState = tmdbAccountListState,
                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                    loginRequiredMessage = stringResource(R.string.settings_notifications_connect_account, "TMDB"),
                    onRetry = { viewModel.loadTmdbAccount() },
                    onUserProfileClick = onUserProfileClick,
                    onConnectClick = onConnectPlatform?.let { connect -> { connect(6) } },
                    onItemClick = { notif ->
                        notif.mediaId?.let { id ->
                            onOpenApiDetail?.invoke(id, "tmdb", notif.mediaType, notif.mediaTitle ?: notif.title, notif.imageUrl)
                        }
                    }
                )

                // ──────────────── Page 5: Bangumi ────────────────
                5 -> NotifListPage(
                    state = bangumiState,
                    isConnected = isBangumiConnected,
                    platform = NotifPlatform.BANGUMI,
                    accentColor = accentColor,
                    listState = bangumiListState,
                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                    loginRequiredMessage = stringResource(R.string.notif_login_required_bangumi),
                    onRetry = { viewModel.loadBangumi(mediaEntries, appSettings.titleLanguage) },
                    onUserProfileClick = onUserProfileClick,
                    onConnectClick = onConnectPlatform?.let { connect -> { connect(5) } },
                    onItemClick = { notif ->
                        // mediaId Bangumi stableId'sidir (500M+); detay yonlendirmesi
                        // bu kimlikle yerel kaydi bulur, bulamazsa API detay acar.
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
            isTmdbConnected = isTmdbConnected,
            aniListUsername = appSettings.anilistUsername,
            malUsername = appSettings.malUsername,
            simklUsername = appSettings.simklUsername,
            kitsuUsername = appSettings.kitsuUsername,
            shikimoriUsername = appSettings.shikimoriUsername,
            bangumiUsername = appSettings.bangumiUsername,
            tmdbUsername = tmdbUsername,
            aniListUnread = aniListUnread ?: 0,
            itemCounts = platformItemCounts.mapKeys { it.key.ordinal },
            unreadCounts = platformUnreadCounts.mapKeys { it.key.ordinal },
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

// ─── Ortak Bildirim Listesi (6 kaynağın tamamı) ─────────────────────────────

private enum class NotifDateGroup(val labelResId: Int) {
    UPCOMING(R.string.notif_group_upcoming),
    TODAY(R.string.notif_group_today),
    YESTERDAY(R.string.notif_group_yesterday),
    THIS_WEEK(R.string.notif_group_this_week),
    OLDER(R.string.notif_group_older),
    NO_DATE(R.string.notif_group_other)
}

private fun notifDateGroup(tsMs: Long?, nowMs: Long): NotifDateGroup {
    if (tsMs == null) return NotifDateGroup.NO_DATE
    fun startOfDay(ms: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = ms
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todayStart = startOfDay(nowMs)
    return when {
        tsMs >= todayStart + 86_400_000L -> NotifDateGroup.UPCOMING
        tsMs >= todayStart -> NotifDateGroup.TODAY
        tsMs >= todayStart - 86_400_000L -> NotifDateGroup.YESTERDAY
        tsMs >= todayStart - 6L * 86_400_000L -> NotifDateGroup.THIS_WEEK
        else -> NotifDateGroup.OLDER
    }
}

private fun LazyListScope.groupedNotifItems(
    items: List<NotifItem>,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)?,
    onItemClick: (NotifItem) -> Unit
) {
    val grouped = items.groupBy { notifDateGroup(it.tsMs, System.currentTimeMillis()) }
    NotifDateGroup.entries.forEach { group ->
        val groupItems = grouped[group].orEmpty()
        if (groupItems.isEmpty()) return@forEach
        item(key = "group_${group.name}") {
            NotifDateGroupHeader(labelResId = group.labelResId, count = groupItems.size)
        }
        // Aynı bildirim farklı zaman damgasıyla iki kez gelirse iki ayrı tarih grubuna düşebilir;
        // grup ön eki gruplar arası, uniqueItems grup içi benzersizliği garanti eder.
        uniqueItems(groupItems, key = { "${group.name}:${it.id}" }) { notif ->
            NotifItemRow(
                notif = notif,
                brandColor = notifBrandColorForSource(notif.source),
                onUserProfileClick = onUserProfileClick,
                onClick = { onItemClick(notif) }
            )
        }
    }
}

@Composable
private fun NotifDateGroupHeader(labelResId: Int, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(labelResId),
            color = KitsugiColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = count.toString(),
            color = KitsugiColors.TextMuted,
            fontSize = 11.sp
        )
    }
}

/** Tüm kaynaklarda ortak durum ekranı: bağlı değil / hata / boş. */
@Composable
private fun NotifStatusState(
    platform: NotifPlatform?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    isError: Boolean = false
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp)
        ) {
            if (platform != null) {
                KitsugiPlatformLogo(platformId = platform.logoId, size = 56.dp)
            } else {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = if (isError) KitsugiColors.AccentRed else KitsugiColors.TextMuted,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = KitsugiColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                KitsugiDetailActionButton(text = actionText, onClick = onAction)
            }
        }
    }
}

@Composable
private fun LoadMoreErrorRow(
    message: String,
    errorCode: PlatformErrorCode?,
    onRetry: (() -> Unit)?
) {
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
        TextButton(onClick = { onRetry?.invoke() }) {
            Text(stringResource(R.string.notif_mal_load_more_retry))
        }
        errorCode?.let { code -> PlatformErrorCodeChip(code = code) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifListPage(
    state: NotifUiState,
    isConnected: Boolean,
    platform: NotifPlatform,
    accentColor: Color,
    listState: androidx.compose.foundation.lazy.LazyListState,
    emptyMessage: String,
    loginRequiredMessage: String,
    onRetry: () -> Unit,
    onItemClick: (NotifItem) -> Unit,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)? = null,
    onConnectClick: (() -> Unit)? = null,
    hasMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    topContent: (LazyListScope.() -> Unit)? = null
) {
    // Bağlı değil + gösterilecek hiçbir şey yok: çıkmaz sokak yerine bağlantı eylemi.
    // Yerel yedek üreten kaynaklarda (Kitsu/Simkl/Bangumi) liste dolu gelir ve
    // açıklama notu bağlantı durumunu söyler; bu dal yalnızca gerçek boşlukta çalışır.
    if (!isConnected && state.items.isEmpty() && !state.isLoading) {
        NotifStatusState(
            platform = platform,
            title = loginRequiredMessage,
            subtitle = platform.description,
            actionText = if (onConnectClick != null) stringResource(R.string.hesabi_bagla) else null,
            onAction = onConnectClick,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // Sonsuz kaydırma: listenin sonuna yaklaşıldığında kaynağın sonraki sayfası istenir.
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

    val isRefreshing = state.isLoading && state.items.isNotEmpty()
    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRetry,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            KitsugiPullToRefreshIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            topContent?.invoke(this)

            if (!state.notice.isNullOrBlank()) {
                item(key = "notice") {
                    SourceNoticeBanner(text = state.notice, platform = platform)
                }
            }

            when {
                state.isLoading && state.items.isEmpty() -> {
                    item { LoadingState(accentColor) }
                }
                state.error != null && state.items.isEmpty() -> {
                    val errText = state.error.orEmpty()
                    item {
                        NotifStatusState(
                            platform = null,
                            title = errText,
                            subtitle = null,
                            actionText = stringResource(R.string.notif_mal_load_more_retry),
                            onAction = onRetry,
                            isError = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    state.errorCode?.let { code ->
                        item(key = "state_error_code") {
                            PlatformErrorCodeChip(
                                code = code,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
                state.items.isEmpty() -> {
                    item {
                        NotifStatusState(
                            platform = platform,
                            title = emptyMessage,
                            subtitle = stringResource(R.string.notif_empty_subtitle),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    state.loadMoreError?.let { message ->
                        item(key = "load_more_error_empty") {
                            LoadMoreErrorRow(message, state.loadMoreErrorCode, onLoadMore)
                        }
                    }
                }
                else -> {
                    groupedNotifItems(state.items, onUserProfileClick, onItemClick)
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
                        item(key = "load_more_error") {
                            LoadMoreErrorRow(message, state.loadMoreErrorCode, onLoadMore)
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
                imageVector = Icons.Rounded.NotificationsNone,
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
private fun SourceNoticeBanner(text: String, platform: NotifPlatform? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.Surface)
            .border(
                width = 1.dp,
                color = (platform?.brandColor ?: KitsugiColors.TextMuted).copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (platform != null) {
            KitsugiPlatformLogo(platformId = platform.logoId, size = 16.dp)
        } else {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
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
                Text(text = state.error, color = KitsugiColors.AccentRed, fontSize = 12.sp)
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
        else -> KitsugiColors.AccentRed
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
            Text(text = error, color = KitsugiColors.AccentRed, fontSize = 11.sp, lineHeight = 15.sp)
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
    accentColor: Color
) {
    // AniList ve MAL sekmeleri AYNI bileşenden çizilir: ikisi de soldan
    // başlar, taşarsa yatay kayar; AniList filtresi geniş ekranda tüm
    // ekrana yayılıp sekmeleri sağa-sola fırlatmaz.
    NotificationFilterTabs(
        tabs = AniListFilter.entries.map { filter ->
            NotificationFilterTab(
                label = stringResource(filter.labelResId),
                isActive = activeFilter == filter,
                onClick = { onFilterSelected(filter) }
            )
        },
        accentColor = accentColor
    )
}

// ─── MAL Alt Filtre Header (myanimelist.net/notification sol menüsü) ─────────

@Composable
private fun MalFilterHeader(
    selectedCategory: MalNotificationCategory,
    counts: Map<MalNotificationCategory, Int>,
    accentColor: Color,
    onCategorySelected: (MalNotificationCategory) -> Unit
) {
    NotificationFilterTabs(
        tabs = MalNotificationCategory.entries.map { cat ->
            NotificationFilterTab(
                label = stringResource(cat.labelResId),
                // Web'de 0 olan kategoriler de gösterilir — kullanıcı tüm
                // filtrelerin varlığını görür.
                count = counts[cat] ?: 0,
                isActive = selectedCategory == cat,
                onClick = { onCategorySelected(cat) }
            )
        },
        accentColor = accentColor
    )
}

// ─── Ortak Filtre Sekmeleri ───────────────────────────────────────────────────
//
// Tüm bildirim kaynakları (AniList, MAL) aynı görünümü kullanır:
// tek satır, soldan başlayan, taştığında yatay kaydırılan hap sekmeler.
// Ölçüler tek yerde tanımlıdır ki bir kaynakta sağa kaçan öğe kalmasın.

private data class NotificationFilterTab(
    val label: String,
    val count: Int = 0,
    val isActive: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun NotificationFilterTabs(
    tabs: List<NotificationFilterTab>,
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
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface)
                    .horizontalScroll(scrollState)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { tab ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (tab.isActive) accentColor else Color.Transparent)
                            .tvClickable(shape = RoundedCornerShape(22.dp), onClick = tab.onClick)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tab.label,
                                color = if (tab.isActive) KitsugiColors.Background else KitsugiColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (tab.isActive) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                            if (tab.count > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (tab.isActive) KitsugiColors.Background.copy(alpha = 0.22f)
                                            else accentColor.copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.count.toString(),
                                        color = if (tab.isActive) KitsugiColors.Background else accentColor,
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
    brandColor: Color,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)? = null,
    onClick: () -> Unit
) {
    // Medya bağlantısı olmayan kullanıcı bildirimleri (takip vb.) avatar gibi
    // yuvarlak, medya posterleri köşeli gösterilir. Eskiden userId varlığı
    // yeterliydi; aktivite beğenilerinde medya posteri de yuvarlak kırpılıyordu.
    val isUserAvatar = notif.mediaId == null && notif.userId != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Her bildirim satırı aynı taban yüksekliğe sahip: tek satır
            // gövdesi olan bildirimlerle iki satırlık olanlar arasında
            // düzensiz sıçramalar oluşmaz.
            .heightIn(min = 72.dp)
            .background(if (notif.isUnread) brandColor.copy(alpha = 0.06f) else Color.Transparent)
            .tvClickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(if (isUserAvatar) CircleShape else RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceStrong)
                .then(
                    if (isUserAvatar && notif.userName != null && onUserProfileClick != null) {
                        Modifier.tvClickable(shape = CircleShape) {
                            onUserProfileClick(notif.userId!!, notif.userName, notif.userAvatarUrl)
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
                    imageVector = Icons.Rounded.NotificationsNone,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            // Okunmamış göstergesi (MAL / Shikimori gerçek okundu bilgisi taşır)
            if (notif.isUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(brandColor)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            // ── Sabit sıra: başlık SOLDAN, kaynak rozeti HER ZAMAN SAĞDA ──
            // Önceden başlık kısa olduğunda rozet başlığın hemen yanına
            // düşüyor (solda), başlık uzun olduğunda en sağa kayıyordu.
            // Artık başlık kalan alanı doldurur; rozet tüm satırlarda aynı
            // hizada, satırın sağ ucunda durur.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = notif.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (notif.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
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
            // Tarih her satırda aynı yerde: gövdenin altında, solda.
            // Tarihi olmayan satırlarda da aynı yükseklik korunur ki liste
            // satırları arasında düzensiz boşluklar oluşmasın.
            Spacer(Modifier.height(4.dp))
            Text(
                text = notif.dateText.orEmpty(),
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
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

// ─── Bildirim Kaynak Seçici Hap Buton ─────────────────────────────────────────

@Composable
private fun NotificationSourceSelectorPill(
    selectedPlatform: NotifPlatform,
    isConnected: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brandColor = selectedPlatform.brandColor
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .border(
                width = 1.dp,
                color = brandColor.copy(alpha = 0.5f),
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
                    .background(brandColor)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
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
            imageVector = Icons.Rounded.ArrowDropDown,
            contentDescription = stringResource(R.string.bildirim_kaynagi_sec),
            tint = brandColor,
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
    isTmdbConnected: Boolean,
    aniListUsername: String,
    malUsername: String,
    simklUsername: String,
    kitsuUsername: String,
    shikimoriUsername: String,
    bangumiUsername: String,
    tmdbUsername: String,
    aniListUnread: Int,
    itemCounts: Map<Int, Int> = emptyMap(),
    unreadCounts: Map<Int, Int> = emptyMap(),
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
                    NotifPlatform.TMDB_ACCOUNT -> isTmdbConnected
                }
                val username = when (platform) {
                    NotifPlatform.ANILIST -> aniListUsername
                    NotifPlatform.MAL -> malUsername
                    NotifPlatform.TMDB_SIMKL -> simklUsername
                    NotifPlatform.KITSU -> kitsuUsername
                    NotifPlatform.SHIKIMORI -> shikimoriUsername
                    NotifPlatform.BANGUMI -> bangumiUsername
                    NotifPlatform.TMDB_ACCOUNT -> tmdbUsername
                }

                val brandColor = platform.brandColor
                val itemCount = itemCounts[index] ?: 0
                val unreadCount = if (platform == NotifPlatform.ANILIST) {
                    maxOf(aniListUnread, unreadCounts[index] ?: 0)
                } else {
                    unreadCounts[index] ?: 0
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) brandColor.copy(alpha = 0.14f)
                            else KitsugiColors.SurfaceSoft.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) brandColor else Color.Transparent,
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
                                    color = KitsugiColors.TextPrimary
                                )
                            )
                            if (isConnected) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.AccentGreen.copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (username.isNotBlank()) "@$username" else stringResource(R.string.notif_diag_connected),
                                        color = KitsugiColors.AccentGreen,
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

                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(brandColor)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.x_yeni, unreadCount.toString()),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (itemCount > 0) {
                                platform.description + " • " + stringResource(R.string.notif_items_count, itemCount)
                            } else {
                                platform.description
                            },
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
                                .background(brandColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.secili),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
