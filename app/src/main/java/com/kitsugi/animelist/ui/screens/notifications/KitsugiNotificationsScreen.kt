package com.kitsugi.animelist.ui.screens.notifications

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.notifications.NotificationDiagnostics
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.remote.malweb.MalNotificationCategory
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.PlatformErrorCodeChip
import com.kitsugi.animelist.ui.components.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.launch

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

// ─── Kaynak başına istemci tarafı filtreler ──────────────────────────────────
//
// Tüm kaynaklar AYNI filtre şeridini ([NotifFilterBand]) kullanır. AniList ve
// MAL filtreleri sunucudan gelir; bildirim ucu olmayan kaynaklarda (Simkl,
// Kitsu, Shikimori, Bangumi) sekmeler yüklenen kayıtlar üzerinden türetilir.
// Böylece hiçbir kaynak "filtresiz, farklı görünen" bir ekran olarak kalmaz.

private const val KEY_ALL = "all"
private const val KEY_UNREAD = "unread"
private const val MAL_TAB_ALL = "mal_all"
private const val MAL_TAB_UNREAD = "mal_unread"

private fun malCategoryKey(category: MalNotificationCategory): String = "mal_cat_${category.name}"

private class LocalFilterSpec(
    val key: String,
    val labelRes: Int,
    val matches: (NotifItem) -> Boolean = { true }
)

private val SIMKL_FILTERS = listOf(
    LocalFilterSpec(KEY_ALL, R.string.notif_filter_all),
    LocalFilterSpec("aired", R.string.notif_filter_aired) { it.typeIdentifier == "aired" },
    LocalFilterSpec("upcoming", R.string.notif_filter_upcoming) { it.typeIdentifier == "upcoming" }
)

private val KITSU_FILTERS = listOf(
    LocalFilterSpec(KEY_ALL, R.string.notif_filter_all),
    LocalFilterSpec("release", R.string.notif_filter_airing) { it.typeIdentifier == "release" },
    LocalFilterSpec("anime", R.string.notif_filter_anime) { it.mediaType.equals("anime", true) },
    LocalFilterSpec("manga", R.string.notif_filter_manga) { it.mediaType.equals("manga", true) }
)

private val SHIKIMORI_FILTERS = listOf(
    LocalFilterSpec(KEY_ALL, R.string.notif_filter_all),
    LocalFilterSpec(KEY_UNREAD, R.string.notif_filter_unread) { it.isUnread },
    LocalFilterSpec("notifications", R.string.notif_filter_notifications) {
        it.typeIdentifier.equals("notifications", true)
    },
    LocalFilterSpec("news", R.string.notif_filter_news) { it.typeIdentifier.equals("news", true) },
    LocalFilterSpec("media", R.string.notif_filter_media) { it.mediaId != null }
)

private val BANGUMI_FILTERS = listOf(
    LocalFilterSpec(KEY_ALL, R.string.notif_filter_all),
    LocalFilterSpec("airing", R.string.notif_filter_airing) { it.typeIdentifier == "airing" },
    LocalFilterSpec("progress", R.string.notif_filter_progress) { it.typeIdentifier == "watching" }
)

/** Yerel filtre tanımlarından ortak şerit sekmelerini üretir (sayılar dahil). */
@Composable
private fun buildLocalTabs(
    specs: List<LocalFilterSpec>,
    items: List<NotifItem>
): List<NotifFilterTab> = specs.map { spec ->
    NotifFilterTab(
        key = spec.key,
        label = stringResource(spec.labelRes),
        count = items.count { spec.matches(it) },
        hasUnread = spec.key == KEY_UNREAD && items.any { it.isUnread }
    )
}

private fun List<NotifItem>.applyFilter(
    specs: List<LocalFilterSpec>,
    key: String
): List<NotifItem> {
    val spec = specs.firstOrNull { it.key == key } ?: return this
    if (spec.key == KEY_ALL) return this
    return filter { spec.matches(it) }
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

    // Ortak üst bölüm (kaydırmayla yumuşak toplanan bar + sabit filtre şeridi).
    val chrome = rememberNotifChromeCollapseState()

    // ── ViewModel state'leri ──
    val malState       by viewModel.mal.collectAsState()
    val simklState     by viewModel.tmdbSimkl.collectAsState()
    val kitsuState     by viewModel.kitsu.collectAsState()
    val shikimoriState by viewModel.shikimori.collectAsState()
    val bangumiState   by viewModel.bangumi.collectAsState()
    val aniListUnread  by viewModel.aniListUnread.collectAsState()
    val diagState      by viewModel.diagnostics.collectAsState()
    val resolvingMediaTitle by viewModel.resolvingMedia.collectAsState()
    val malCategory    by viewModel.malCategory.collectAsState()

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
    val aniListFilterFlow = remember(currentAniListFilter) {
        viewModel.getAniListState(currentAniListFilter)
    }
    val aniListState by aniListFilterFlow.collectAsState()

    // Yerel kaynak filtreleri — sayfa yeniden çizilse de seçim korunur.
    var malUnreadOnly by rememberSaveable { mutableStateOf(false) }
    var simklTab by rememberSaveable { mutableStateOf(KEY_ALL) }
    var kitsuTab by rememberSaveable { mutableStateOf(KEY_ALL) }
    var shikimoriTab by rememberSaveable { mutableStateOf(KEY_ALL) }
    var bangumiTab by rememberSaveable { mutableStateOf(KEY_ALL) }

    // ── İlk yükleme — sayfa, filtre veya başlık dili değiştiğinde ──
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

    // Tek giriş tamamlandı: web oturumu (bildirimler) artık aynı hesapta.
    LaunchedEffect(malSessionRefreshToken) {
        if (malSessionRefreshToken > 0 && isMalConnected) viewModel.loadMal(mediaEntries)
    }

    // Kaynak ya da AniList filtresi değiştiğinde (kaydırma dahil) üst bölüm yumuşakça
    // geri açılır: yeni sayfa listenin başından başladığı için toplanmış bar anlamsız kalırdı.
    LaunchedEffect(pagerState.currentPage, currentAniListFilter) {
        chrome.expandSmoothly()
    }

    val malListState       = rememberLazyListState()
    val tmdbSimklListState = rememberLazyListState()
    val kitsuListState     = rememberLazyListState()
    val shikimoriListState = rememberLazyListState()
    val bangumiListState   = rememberLazyListState()

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
     * Kart başlığı MAL'ın kategori adıdır ("New Related Anime"); detay sayfası
     * medyanın gerçek adı + MAL kimliği ile açılır:
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
    //  Ortak başlık verileri
    // ─────────────────────────────────────────────────────────────────────────

    val currentPlatform = NotifPlatform.entries.getOrElse(pagerState.currentPage) { NotifPlatform.ANILIST }
    val isCurrentConnected = when (currentPlatform) {
        NotifPlatform.ANILIST    -> isAniListConnected
        NotifPlatform.MAL        -> isMalConnected || !malState.requiresMalWebLogin
        NotifPlatform.TMDB_SIMKL -> isSimklConnected
        NotifPlatform.KITSU      -> isKitsuConnected
        NotifPlatform.SHIKIMORI  -> isShikimoriConnected
        NotifPlatform.BANGUMI    -> isBangumiConnected
    }

    // ── Kaynak sekmeleri (hepsi aynı bileşenle çizilir) ──
    val aniListTabs = AniListFilter.entries.map { filter ->
        NotifFilterTab(key = filter.name, label = stringResource(filter.labelResId))
    }

    val malCounts = remember(malState, malCategory) { viewModel.getMalCategoryCounts() }
    val malUnreadTotal = remember(malState) { viewModel.getMalUnreadCount() }
    val malCategoryTabs = MalNotificationCategory.entries
        .filter { it != MalNotificationCategory.ALL }
        .map { category ->
            NotifFilterTab(
                key = malCategoryKey(category),
                label = stringResource(category.labelResId),
                count = malCounts[category] ?: 0
            )
        }
    val malTabs = listOf(
        NotifFilterTab(
            key = MAL_TAB_ALL,
            label = stringResource(R.string.mal_notif_filter_all),
            count = malCounts[MalNotificationCategory.ALL] ?: malState.items.size
        ),
        NotifFilterTab(
            key = MAL_TAB_UNREAD,
            label = stringResource(R.string.notif_filter_unread),
            count = malUnreadTotal,
            hasUnread = malUnreadTotal > 0
        )
    ) + malCategoryTabs

    val simklTabs = buildLocalTabs(SIMKL_FILTERS, simklState.items)
    val kitsuTabs = buildLocalTabs(KITSU_FILTERS, kitsuState.items)
    val shikimoriTabs = buildLocalTabs(SHIKIMORI_FILTERS, shikimoriState.items)
    val bangumiTabs = buildLocalTabs(BANGUMI_FILTERS, bangumiState.items)

    val headerTabs = when (currentPlatform) {
        NotifPlatform.ANILIST    -> aniListTabs
        NotifPlatform.MAL        -> malTabs
        NotifPlatform.TMDB_SIMKL -> simklTabs
        NotifPlatform.KITSU      -> kitsuTabs
        NotifPlatform.SHIKIMORI  -> shikimoriTabs
        NotifPlatform.BANGUMI    -> bangumiTabs
    }

    // Dikkat: "Tümü" sekmesinin anahtarı MAL_TAB_ALL'dir; kategori anahtarıyla
    // karıştırılırsa şeritte hiçbir sekme seçili görünmez.
    val malSelectedKey = when {
        malUnreadOnly -> MAL_TAB_UNREAD
        malCategory == MalNotificationCategory.ALL -> MAL_TAB_ALL
        else -> malCategoryKey(malCategory)
    }
    val selectedTabKey = when (currentPlatform) {
        NotifPlatform.ANILIST    -> currentAniListFilter.name
        NotifPlatform.MAL        -> malSelectedKey
        NotifPlatform.TMDB_SIMKL -> simklTab
        NotifPlatform.KITSU      -> kitsuTab
        NotifPlatform.SHIKIMORI  -> shikimoriTab
        NotifPlatform.BANGUMI    -> bangumiTab
    }

    val platformTotal = when (currentPlatform) {
        NotifPlatform.ANILIST    -> aniListState.items.size
        NotifPlatform.MAL        -> malCounts[MalNotificationCategory.ALL] ?: malState.items.size
        NotifPlatform.TMDB_SIMKL -> simklState.items.size
        NotifPlatform.KITSU      -> kitsuState.items.size
        NotifPlatform.SHIKIMORI  -> shikimoriState.items.size
        NotifPlatform.BANGUMI    -> bangumiState.items.size
    }
    val platformNew = when (currentPlatform) {
        NotifPlatform.ANILIST    -> aniListUnread ?: 0
        NotifPlatform.MAL        -> malUnreadTotal
        NotifPlatform.TMDB_SIMKL -> simklState.items.count { it.isUnread }
        NotifPlatform.KITSU      -> kitsuState.items.count { it.isUnread }
        NotifPlatform.SHIKIMORI  -> shikimoriState.items.count { it.isUnread }
        NotifPlatform.BANGUMI    -> bangumiState.items.count { it.isUnread }
    }
    val currentLoading = when (currentPlatform) {
        NotifPlatform.ANILIST    -> aniListState.isLoading
        NotifPlatform.MAL        -> malState.isLoading
        NotifPlatform.TMDB_SIMKL -> simklState.isLoading
        NotifPlatform.KITSU      -> kitsuState.isLoading
        NotifPlatform.SHIKIMORI  -> shikimoriState.isLoading
        NotifPlatform.BANGUMI    -> bangumiState.isLoading
    }

    val headerSummary = when {
        !isCurrentConnected -> stringResource(R.string.bagli_degil)
        platformTotal <= 0 -> null
        // AniList'te "yeni" sayısı sunucudan GLOBAL gelir; seçili sekmenin kayıt
        // sayısından büyük çıkarsa "5 bildirim • 12 yeni" gibi tutarsız bir özet
        // yazılmasın diye toplamla sınırlanır.
        platformNew > 0 -> stringResource(
            R.string.notif_summary_total_new,
            platformTotal,
            platformNew.coerceAtMost(platformTotal)
        )
        else -> stringResource(R.string.notif_summary_total, platformTotal)
    }

    /**
     * Ortak sekme davranışı (tüm kaynaklar):
     *  • Farklı sekmeye geçiş → liste başına dön (yeni filtre en yeniden başlar).
     *  • Aynı sekmeye tekrar dokunma → listenin başına yumuşakça dön.
     *  • Her durumda üst bölüm yumuşakça açılır: kullanıcı hangi kaynağın hangi
     *    sekmesinde olduğunu görebilir.
     */
    fun selectTab(tab: NotifFilterTab) {
        fun scrollTarget(listState: androidx.compose.foundation.lazy.LazyListState, sameTab: Boolean) {
            scope.launch {
                if (sameTab) listState.animateScrollToItem(0) else listState.scrollToItem(0)
            }
        }

        when (currentPlatform) {
            NotifPlatform.ANILIST -> {
                AniListFilter.entries.firstOrNull { it.name == tab.key }?.let { filter ->
                    if (filter.name != currentAniListFilter.name) {
                        scope.launch { aniListPagerState.animateScrollToPage(filter.ordinal) }
                    }
                }
            }

            NotifPlatform.MAL -> {
                val sameTab = tab.key == malSelectedKey
                when (tab.key) {
                    MAL_TAB_UNREAD -> malUnreadOnly = true
                    MAL_TAB_ALL -> {
                        malUnreadOnly = false
                        viewModel.setMalFilter(MalNotificationCategory.ALL)
                    }
                    else -> {
                        malUnreadOnly = false
                        MalNotificationCategory.entries
                            .firstOrNull { malCategoryKey(it) == tab.key }
                            ?.let { viewModel.setMalFilter(it) }
                    }
                }
                scrollTarget(malListState, sameTab)
            }

            NotifPlatform.TMDB_SIMKL -> {
                val sameTab = tab.key == simklTab
                simklTab = tab.key
                scrollTarget(tmdbSimklListState, sameTab)
            }

            NotifPlatform.KITSU -> {
                val sameTab = tab.key == kitsuTab
                kitsuTab = tab.key
                scrollTarget(kitsuListState, sameTab)
            }

            NotifPlatform.SHIKIMORI -> {
                val sameTab = tab.key == shikimoriTab
                shikimoriTab = tab.key
                scrollTarget(shikimoriListState, sameTab)
            }

            NotifPlatform.BANGUMI -> {
                val sameTab = tab.key == bangumiTab
                bangumiTab = tab.key
                scrollTarget(bangumiListState, sameTab)
            }
        }
        scope.launch { chrome.expandSmoothly() }
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
        NotifChromeHeader(
            collapse = chrome,
            title = stringResource(R.string.notif_title),
            sourceLabel = currentPlatform.label,
            sourceLogoId = currentPlatform.logoId,
            isSourceConnected = isCurrentConnected,
            sourceBadge = if (currentPlatform == NotifPlatform.ANILIST) (aniListUnread ?: 0) else 0,
            summary = headerSummary,
            tabs = headerTabs,
            selectedTabKey = selectedTabKey,
            accentColor = accentColor,
            onBack = onBack,
            onOpenSourcePicker = { showSourcePickerSheet = true },
            onRefresh = { refreshCurrentPage() },
            onDiagnostics = {
                showDiagnostics = true
            },
            onSelectTab = { tab -> selectTab(tab) },
            isRefreshing = currentLoading
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
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            NotifCenteredEmptyState(
                                message = stringResource(R.string.notif_login_required_anilist)
                            )
                        }
                    } else {
                        HorizontalPager(
                            state = aniListPagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { filterPage ->
                            val filter = AniListFilter.entries[filterPage]
                            val filterFlow = remember(filter) { viewModel.getAniListState(filter) }
                            val filterState by filterFlow.collectAsState()
                            val listState = rememberLazyListState()

                            NotifFeedList(
                                state = filterState,
                                listState = listState,
                                collapse = chrome,
                                accentColor = accentColor,
                                isConnected = true,
                                loginRequiredMessage = stringResource(R.string.notif_login_required_anilist),
                                emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                                onUserProfileClick = onUserProfileClick,
                                hasMore = filterState.hasMore,
                                onLoadMore = {
                                    viewModel.loadAniList(
                                        filter = filter,
                                        resetPage = false,
                                        mediaEntries = mediaEntries
                                    )
                                },
                                onRefresh = {
                                    viewModel.loadAniList(
                                        filter = filter,
                                        resetPage = true,
                                        mediaEntries = mediaEntries
                                    )
                                },
                                onItemClick = { notif ->
                                    if (notif.activityId != null && notif.activityId > 0) {
                                        activeActivityIdForDetail = notif.activityId
                                    } else if (notif.mediaId != null) {
                                        onOpenApiDetail?.invoke(
                                            notif.mediaId,
                                            "anilist",
                                            notif.mediaType,
                                            notif.mediaTitle ?: notif.title,
                                            notif.imageUrl
                                        )
                                    } else if (notif.userId != null && notif.userName != null) {
                                        onUserProfileClick?.invoke(
                                            notif.userId,
                                            notif.userName,
                                            notif.userAvatarUrl
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // ──────────────── Page 1: MAL ────────────────
                1 -> {
                    val malDisplayState = remember(malState, malUnreadOnly) {
                        if (malUnreadOnly) {
                            malState.copy(items = malState.items.filter { it.isUnread })
                        } else {
                            malState
                        }
                    }
                    // "Okunmamış" sekmesi ham listenin istemci tarafı süzgecidir; yüklü
                    // sayfalarda hiç okunmamış kalmadıysa ama sunucuda daha eski sayfa
                    // varsa bir sonraki sayfa istenir (aksi hâlde sekme boş görünürdü).
                    LaunchedEffect(malUnreadOnly, malDisplayState.items.isEmpty(), malState.hasMore, malState.isLoading) {
                        if (malUnreadOnly &&
                            malDisplayState.items.isEmpty() &&
                            malState.hasMore &&
                            !malState.isLoading
                        ) {
                            viewModel.loadMalMore()
                        }
                    }
                    val malWebSessionTitle = stringResource(
                        when {
                            malState.malAccountMismatch -> R.string.notif_mal_account_mismatch_action
                            malState.requiresMalWebLogin || !isMalConnected -> R.string.notif_mal_web_connect
                            else -> R.string.notif_mal_web_refresh
                        }
                    )
                    val malWebSessionHint = when {
                        malState.malAccountMismatch -> stringResource(
                            R.string.notif_mal_account_mismatch_hint,
                            malState.malOAuthUsername ?: "—",
                            malState.malWebUsername ?: "—"
                        )
                        malState.requiresMalWebLogin || !isMalConnected -> stringResource(R.string.notif_mal_web_login_hint)
                        else -> stringResource(R.string.notif_mal_web_refresh_hint)
                    }

                    NotifFeedList(
                        state = malDisplayState,
                        listState = malListState,
                        collapse = chrome,
                        accentColor = accentColor,
                        // Yerel akış görünür kalır; web oturumu onarımı ortak hesap
                        // bağlantısı ekranından yapılır.
                        isConnected = true,
                        loginRequiredMessage = stringResource(R.string.notif_login_required_mal),
                        emptyMessage = when {
                            malUnreadOnly -> stringResource(R.string.notif_empty_no_unread)
                            malCategory == MalNotificationCategory.ALL ->
                                stringResource(R.string.notif_empty_no_notifications)
                            else -> stringResource(R.string.mal_notif_filter_empty)
                        },
                        onUserProfileClick = onUserProfileClick,
                        hasMore = malState.hasMore,
                        onLoadMore = { viewModel.loadMalMore() },
                        onRefresh = { viewModel.loadMal(mediaEntries) },
                        // Kart yalnızca bir eylem GEREKTİĞİNDE (hesap eşleşmiyor /
                        // web oturumu yok) gösterilir. Her şey yolundaysa liste başında
                        // boşuna yer kaplamaz: yenileme zaten aşağı çekme ve üst barda var.
                        actionCard = if (malState.malAccountMismatch ||
                            malState.requiresMalWebLogin ||
                            !isMalConnected
                        ) {
                            {
                                MalWebSessionActionCard(
                                    title = malWebSessionTitle,
                                    hint = malWebSessionHint,
                                    accentColor = accentColor,
                                    onClick = onOpenMalAccountSettings
                                )
                            }
                        } else {
                            null
                        },
                        onItemClick = { notif -> openMalNotification(notif) }
                    )
                }

                // ──────────────── Page 2: TMDB & Simkl ────────────────
                2 -> {
                    val items = remember(simklState.items, simklTab) { simklState.items.applyFilter(SIMKL_FILTERS, simklTab) }
                    NotifFeedList(
                        state = simklState.copy(items = items),
                        listState = tmdbSimklListState,
                        collapse = chrome,
                        accentColor = accentColor,
                        isConnected = isSimklConnected,
                        loginRequiredMessage = stringResource(R.string.notif_login_required_simkl),
                        emptyMessage = if (simklTab != KEY_ALL) {
                            stringResource(R.string.notif_empty_filter)
                        } else {
                            stringResource(R.string.notif_empty_list_simkl)
                        },
                        onUserProfileClick = onUserProfileClick,
                        onRefresh = { viewModel.loadTmdbSimkl(mediaEntries) },
                        onItemClick = { notif ->
                            notif.mediaId?.let { id ->
                                onOpenApiDetail?.invoke(
                                    id,
                                    "simkl",
                                    notif.mediaType,
                                    notif.mediaTitle ?: notif.title,
                                    notif.imageUrl
                                )
                            }
                        }
                    )
                }

                // ──────────────── Page 3: Kitsu ────────────────
                3 -> {
                    val items = remember(kitsuState.items, kitsuTab) { kitsuState.items.applyFilter(KITSU_FILTERS, kitsuTab) }
                    NotifFeedList(
                        state = kitsuState.copy(items = items),
                        listState = kitsuListState,
                        collapse = chrome,
                        accentColor = accentColor,
                        isConnected = isKitsuConnected,
                        loginRequiredMessage = stringResource(R.string.notif_notice_kitsu_not_connected),
                        emptyMessage = if (kitsuTab != KEY_ALL) {
                            stringResource(R.string.notif_empty_filter)
                        } else {
                            stringResource(R.string.notif_empty_no_notifications)
                        },
                        onUserProfileClick = onUserProfileClick,
                        onRefresh = { viewModel.loadKitsu(mediaEntries) },
                        onItemClick = { notif ->
                            notif.mediaId?.let { id ->
                                onOpenApiDetail?.invoke(
                                    id,
                                    "kitsu",
                                    notif.mediaType,
                                    notif.mediaTitle ?: notif.title,
                                    notif.imageUrl
                                )
                            }
                        }
                    )
                }

                // ──────────────── Page 4: Shikimori ────────────────
                4 -> {
                    val items = remember(shikimoriState.items, shikimoriTab) { shikimoriState.items.applyFilter(SHIKIMORI_FILTERS, shikimoriTab) }
                    NotifFeedList(
                        state = shikimoriState.copy(items = items),
                        listState = shikimoriListState,
                        collapse = chrome,
                        accentColor = accentColor,
                        isConnected = isShikimoriConnected,
                        loginRequiredMessage = stringResource(R.string.notif_login_required_shikimori),
                        emptyMessage = if (shikimoriTab != KEY_ALL) {
                            stringResource(R.string.notif_empty_filter)
                        } else {
                            stringResource(R.string.notif_empty_no_notifications)
                        },
                        onUserProfileClick = onUserProfileClick,
                        onRefresh = { viewModel.loadShikimori(mediaEntries) },
                        onItemClick = { notif ->
                            notif.mediaId?.let { id ->
                                onOpenApiDetail?.invoke(
                                    id,
                                    "shikimori",
                                    notif.mediaType,
                                    notif.mediaTitle ?: notif.title,
                                    notif.imageUrl
                                )
                            }
                        }
                    )
                }

                // ──────────────── Page 5: Bangumi ────────────────
                5 -> {
                    val items = remember(bangumiState.items, bangumiTab) { bangumiState.items.applyFilter(BANGUMI_FILTERS, bangumiTab) }
                    NotifFeedList(
                        state = bangumiState.copy(items = items),
                        listState = bangumiListState,
                        collapse = chrome,
                        accentColor = accentColor,
                        isConnected = isBangumiConnected,
                        loginRequiredMessage = stringResource(R.string.notif_login_required_bangumi),
                        emptyMessage = if (bangumiTab != KEY_ALL) {
                            stringResource(R.string.notif_empty_filter)
                        } else {
                            stringResource(R.string.notif_empty_no_notifications)
                        },
                        onUserProfileClick = onUserProfileClick,
                        onRefresh = { viewModel.loadBangumi(mediaEntries, appSettings.titleLanguage) },
                        onItemClick = { notif ->
                            // mediaId Bangumi stableId'sidir (500M+); detay yönlendirmesi
                            // bu kimlikle yerel kaydı bulur, bulamazsa API detay açar.
                            notif.mediaId?.let { id ->
                                onOpenApiDetail?.invoke(
                                    id,
                                    "bangumi",
                                    notif.mediaType,
                                    notif.mediaTitle ?: notif.title,
                                    notif.imageUrl
                                )
                            }
                        }
                    )
                }
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
                    chrome.expandSmoothly()
                }
            },
            onDismiss = { showSourcePickerSheet = false }
        )
    }
}

// ─── MAL oturum aksiyon kartı ────────────────────────────────────────────────

@Composable
private fun MalWebSessionActionCard(
    title: String,
    hint: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.Surface)
            .tvClickable(shape = RoundedCornerShape(14.dp), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                Text(
                    text = state.error,
                    color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                    fontSize = 12.sp
                )
                state.errorCode?.let { code ->
                    PlatformErrorCodeChip(code = code, modifier = Modifier.padding(top = 4.dp))
                }
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
            Text(
                text = stringResource(
                    if (!row.connected) R.string.notif_diag_not_connected else R.string.notif_diag_connected
                ),
                color = if (!row.connected) KitsugiColors.TextMuted else KitsugiColors.TextSecondary,
                fontSize = 11.sp
            )
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
                .verticalScroll(rememberScrollState())
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
                                imageVector = Icons.Rounded.Check,
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
