package com.kitsugi.animelist.ui.screens.notifications

import android.app.Application
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.account.KitsugiAccountRepository
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.data.auth.MalImportManager
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.data.auth.TmdbAuthStore
import com.kitsugi.animelist.data.notifications.NotifSeenStore
import com.kitsugi.animelist.data.notifications.NotificationArchiveStore
import com.kitsugi.animelist.data.notifications.NotificationDiagnostics
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiNameLocalizer
import com.kitsugi.animelist.data.remote.BangumiSubjectTitleResolver
import com.kitsugi.animelist.data.remote.BangumiTitleCache
import com.kitsugi.animelist.data.remote.JikanSearchClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient
import com.kitsugi.animelist.data.remote.KitsugiAniListNotificationClient
import com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClient
import com.kitsugi.animelist.data.remote.SimklCalendarClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.utils.PreferenceHelpers
import com.kitsugi.animelist.data.remote.malweb.MalAccountMatch
import com.kitsugi.animelist.data.remote.malweb.MalNotificationsClient
import com.kitsugi.animelist.data.remote.malweb.MalNotificationsParseException
import com.kitsugi.animelist.data.remote.malweb.MalWebSessionRequiredException
import com.kitsugi.animelist.data.remote.malweb.MalNotificationCategory
import com.kitsugi.animelist.data.remote.malweb.MalNotificationWebEndpoints
import com.kitsugi.animelist.data.remote.malweb.MalWebSessionStore
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

enum class AniListFilter(
    val labelResId: Int,
    val group: KitsugiAniListNotificationClient.NotificationGroup
) {
    ALL(R.string.notif_filter_all, KitsugiAniListNotificationClient.NotificationGroup.ALL),
    AIRING(R.string.notif_filter_airing, KitsugiAniListNotificationClient.NotificationGroup.AIRING),
    ACTIVITY(R.string.notif_filter_activity, KitsugiAniListNotificationClient.NotificationGroup.ACTIVITY),
    FORUM(R.string.notif_filter_forum, KitsugiAniListNotificationClient.NotificationGroup.FORUM),
    FOLLOWS(R.string.notif_filter_follows, KitsugiAniListNotificationClient.NotificationGroup.FOLLOWS),
    MEDIA(R.string.notif_filter_media, KitsugiAniListNotificationClient.NotificationGroup.MEDIA)
}

// ─── Veri Modeli ─────────────────────────────────────────────────────────────

data class NotifItem(
    val id: String,
    val imageUrl: String?,
    val title: String,
    val body: String,
    val dateText: String?,
    val isUnread: Boolean = false,
    val mediaId: Int? = null,
    val activityId: Int? = null,
    val mediaType: String? = null,
    val typeIdentifier: String? = null,
    val categoryName: String? = null,
    /**
     * Bildirimin işaret ettiği medyanın GERÇEK adı (varsa).
     *
     * MAL'da kart başlığı MAL'ın kategori adıydı ("New Related Anime"); detay sayfası kart
     * başlığıyla açıldığı için ekranda anime adı yerine kategori adı kalıyordu
     * (bkz. PLAN_TASK_MAL_BILDIRIM_DETAY_VE_SAYFALAMA_2026-10-12.md). Bu alan hem kart
     * başlığı hem detay yönlendirmesi için kullanılır; boşsa [title]'a düşülür.
     */
    val mediaTitle: String? = null,
    val userId: Int? = null,
    val userName: String? = null,
    val userAvatarUrl: String? = null,
    val source: String = "AniList",
    /**
     * Sıralama ve arşiv birleştirme için epoch ms.
     * Kaynak tarafında tutulmayan kaynaklarda (MAL/Simkl/Kitsu/Bangumi)
     * arşiv listesi bu alanla yeni → eski sıralanır.
     */
    val tsMs: Long? = null,
    /** Güvenli platform URL'si; notification item tıklanınca açılır. */
    val targetUrl: String? = null
)

// ─── UI State ─────────────────────────────────────────────────────────────────

data class NotifUiState(
    val items: List<NotifItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val errorCode: PlatformErrorCode? = null,
    val hasMore: Boolean = true,
    val page: Int = 1,
    /** MAL sayfası açık bir next URL sunduysa, güvenli aynı-kaynak bağlantısı. */
    val nextPageUrl: String? = null,
    /** Daha eski MAL bildirimlerini yükleme hatası; mevcut kartlar korunur ve yeniden denenebilir. */
    val loadMoreError: String? = null,
    val loadMoreErrorCode: PlatformErrorCode? = null,
    /** Kullanıcıya gösterilecek kaynak açıklaması veya geçici akış uyarısı. */
    val notice: String? = null,
    /** MAL's private website feed requires a first-party WebView session cookie. */
    val requiresMalWebLogin: Boolean = false,
    /**
     * Web oturumu ile bağlı OAuth hesabı farklı kişilere ait. Bildirimler gösterilmez ve
     * kullanıcıdan doğru hesapla yeniden giriş yapması istenir.
     */
    val malAccountMismatch: Boolean = false,
    /** Eşleşme uyarısında gösterilmek üzere: bağlı OAuth hesabının kullanıcı adı. */
    val malOAuthUsername: String? = null,
    /** Eşleşme uyarısında gösterilmek üzere: web oturumunun sahibi kullanıcı adı. */
    val malWebUsername: String? = null
)

/** Bildirim Teşhisi ekran durumu. */
data class NotifDiagnosticsUiState(
    val isRunning: Boolean = false,
    val rows: List<NotificationDiagnostics.Row> = emptyList(),
    val error: String? = null,
    val errorCode: PlatformErrorCode? = null
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class KitsugiNotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context get() = getApplication<Application>().applicationContext
    private val settingsDataStore = SettingsDataStore(application)

    // ── AniList ──
    private val _aniListStates = AniListFilter.entries.associateWith { MutableStateFlow(NotifUiState()) }
    val aniListStates: Map<AniListFilter, StateFlow<NotifUiState>> = _aniListStates.mapValues { it.value.asStateFlow() }

    /**
     * Filtre başına SABİT akış döner.
     *
     * Daha önce her çağrıda `asStateFlow()` ile yeni bir sarmalayıcı üretiliyordu; Compose
     * `collectAsState(flow)` akış kimliği değişince aboneliği yeniden kurduğu için sekme
     * geçişlerinde liste bir an boş görünüp yeniden doluyordu. Artık haritadaki akış
     * doğrudan paylaşılır.
     */
    fun getAniListState(filter: AniListFilter): StateFlow<NotifUiState> =
        aniListStates[filter] ?: _aniListStates.getValue(filter).asStateFlow()

    /** AniList okunmamış bildirim sayısı (sekme rozeti için). */
    private val _aniListUnread = MutableStateFlow<Int?>(null)
    val aniListUnread: StateFlow<Int?> = _aniListUnread.asStateFlow()

    // ── MAL ──
    // Not: ilk durumda "web oturumu yok" denmez. Oturum kararı MAL'a yapılan gerçek istekle
    // verilir (bkz. loadMal); aksi halde ekran açılır açılmaz gereksiz giriş penceresi çıkardı.
    private val _mal = MutableStateFlow(NotifUiState())
    private var malReloadQueued = false
    val mal: StateFlow<NotifUiState> = _mal.asStateFlow()

    // MAL kategori filtresi — sol menüyle aynı (All / New Related Anime 39 / Now Airing 4 …)
    private val _malCategory = MutableStateFlow(MalNotificationCategory.ALL)
    val malCategory: StateFlow<MalNotificationCategory> = _malCategory.asStateFlow()

    /** MAL için ham (filtresiz) liste — filtre değişiminde yeniden çekilmeden filtrelenir. */
    private var malUnfilteredItems: List<NotifItem> = emptyList()
    private var malUnfilteredNotice: String? = null
    private var malRawHasMore: Boolean = false
    private var malRawPage: Int = 1
    private var malRawNextPageUrl: String? = null
    /** İlk canlı WebView/HTML yanıtındaki satır sayısı; yerel arşiv sayısı değildir. */
    private var malRawBatchSize: Int? = null
    /** `.notificaiton-category-nav` üzerinden okunan gerçek sunucu sayıları ve adresleri. */
    private var malSidebarCounts: Map<MalNotificationCategory, Int> = emptyMap()
    private var malCategoryUrls: Map<MalNotificationCategory, String> = emptyMap()
    private var malCoverLookup: Map<Int, String> = emptyMap()

    // ── TMDB+Simkl ──
    private val _tmdbSimkl = MutableStateFlow(NotifUiState())
    val tmdbSimkl: StateFlow<NotifUiState> = _tmdbSimkl.asStateFlow()

    // ── TMDB account changes (locally archived; TMDB has no notification API) ──
    private val _tmdbAccount = MutableStateFlow(NotifUiState())
    val tmdbAccount: StateFlow<NotifUiState> = _tmdbAccount.asStateFlow()

    // ── Kitsu ──
    private val _kitsu = MutableStateFlow(NotifUiState())
    val kitsu: StateFlow<NotifUiState> = _kitsu.asStateFlow()

    // ── Shikimori ──
    private val _shikimori = MutableStateFlow(NotifUiState())
    val shikimori: StateFlow<NotifUiState> = _shikimori.asStateFlow()

    // ── Bangumi ──
    private val _bangumi = MutableStateFlow(NotifUiState())
    val bangumi: StateFlow<NotifUiState> = _bangumi.asStateFlow()

    // ── Teşhis ──
    private val _diagnostics = MutableStateFlow(NotifDiagnosticsUiState())
    val diagnostics: StateFlow<NotifDiagnosticsUiState> = _diagnostics.asStateFlow()

    /** 5 kaynağın bildirim altyapısını canlı isteklerle test eder. */
    fun runDiagnostics(mediaEntries: List<MediaEntry>) {
        if (_diagnostics.value.isRunning) return
        viewModelScope.launch {
            _diagnostics.value = NotifDiagnosticsUiState(isRunning = true)
            try {
                val rows = NotificationDiagnostics.runAll(ctx, mediaEntries)
                _diagnostics.value = NotifDiagnosticsUiState(isRunning = false, rows = rows)
            } catch (e: Exception) {
                _diagnostics.value = NotifDiagnosticsUiState(
                    isRunning = false,
                    error = e.message ?: e.javaClass.simpleName,
                    errorCode = com.kitsugi.animelist.data.platform.PlatformErrorCatalog.classify(error = e)
                )
            }
        }
    }

    // ─── Bildirim Arşivi (kaynak tarafında tutulmayanların yedeği) ───────────

    /** Canlı kayıtları arşive yazar; listeyi DEĞİŞTİRMEZ (sayfa eklemede kullanılır). */
    private fun writeArchive(
        source: String,
        live: List<NotifItem>,
        retainOldWhere: ((NotificationArchiveStore.ArchivedNotif) -> Boolean)? = null
    ) {
        if (live.isEmpty() && retainOldWhere == null) return
        NotificationArchiveStore.mergeSourceAsync(
            ctx = ctx,
            source = source,
            fresh = live.map { it.toArchived() },
            retainOldWhere = retainOldWhere
        )

        // Cloud sync is scheduled by the store AFTER its local write completes.
    }

    /**
     * Kaynak tarafında kalıcı bildirim ucu OLMAYAN kaynaklar için (MAL, Simkl,
     * Kitsu, Bangumi) canlı listeyi yerel arşivle birleştirir.
     */
    private fun archiveAndMerge(
        source: String,
        live: List<NotifItem>,
        retainOldWhere: ((NotificationArchiveStore.ArchivedNotif) -> Boolean)? = null,
        writeLive: Boolean = true
    ): List<NotifItem> {
        if (writeLive) writeArchive(source, live, retainOldWhere = retainOldWhere)

        val liveIds = live.map { it.id }.toSet()
        val stored = NotificationArchiveStore.loadSource(ctx, source)
        val fromArchive = stored
            .filter { (retainOldWhere == null || retainOldWhere(it)) && it.id !in liveIds }
            .map { it.toNotifItem() }

        // Ortak sözleşme: TÜM kaynaklar yeni → eski sıralanır; tarihi olmayanlar
        // (örn. Kitsu "listede" kayıtları) en sonda kalır. Eskiden sıralama yalnızca
        // arşiv doluyken uygulanıyordu; ilk açılışta kaynak sırası neyse o görünüyordu.
        return (live + fromArchive).sortedWith(
            compareByDescending<NotifItem> { it.tsMs ?: Long.MIN_VALUE }
        )
    }

    // ─── "Gördüm" defteri (sunucuda okundu durumu olmayan kaynaklar) ─────────

    /**
     * Kaydın `isUnread` değerini yerel deftere göre yeniden hesaplar.
     *
     * [serverState] true ise sunucunun `read` alanı esas alınır (Shikimori): kayıt
     * yalnızca sunucu "okunmadı" diyorsa **ve** yerelde görülmemişse yeni kalır.
     * false ise (Simkl / Kitsu / Bangumi / MAL türetilmiş kayıtlar) yenilik tamamen
     * yerel defterden gelir: görülmemiş + son 72 saat.
     */
    private fun List<NotifItem>.withLocalSeen(
        sourceKey: String,
        serverState: Boolean = false
    ): List<NotifItem> = NotifSeenStore.applyTo(
        context = ctx,
        source = sourceKey,
        items = this,
        idOf = { it.id },
        tsOf = { it.tsMs },
        unreadOf = { it.isUnread },
        hadServerState = serverState,
        copy = { item, unread -> if (item.isUnread == unread) item else item.copy(isUnread = unread) }
    )

    /** Bir bildirim açıldığında çağrılır: rozet düşsün, "Okunmamış" sekmesinden çıksın. */
    fun markNotificationSeen(item: NotifItem, sourceKey: String) {
        NotifSeenStore.markSeen(ctx, sourceKey, item.id)
        markSeenInState(sourceKey, setOf(item.id))
    }

    /** Kaynağın yüklü tüm bildirimlerini okundu sayar ("Tümünü okundu işaretle"). */
    fun markAllNotificationsSeen(sourceKey: String) {
        val current = when (sourceKey) {
            "simkl" -> _tmdbSimkl.value
            "kitsu" -> _kitsu.value
            "shikimori" -> _shikimori.value
            "bangumi" -> _bangumi.value
            "anilist" -> getAniListState(AniListFilter.ALL).value
            else -> null
        } ?: return
        val ids = current.items.filter { it.isUnread }.map { it.id }.toSet()
        if (ids.isEmpty()) return
        NotifSeenStore.markAllSeen(ctx, sourceKey, ids)
        markSeenInState(sourceKey, ids)
    }

    /** Yüklü listelerde isUnread=false yapar (ağ isteği yok). */
    private fun markSeenInState(sourceKey: String, ids: Set<String>) {
        fun NotifUiState.cleared(): NotifUiState =
            copy(items = items.map { if (it.id in ids && it.isUnread) it.copy(isUnread = false) else it })

        when (sourceKey) {
            "simkl" -> _tmdbSimkl.value = _tmdbSimkl.value.cleared()
            "kitsu" -> _kitsu.value = _kitsu.value.cleared()
            "shikimori" -> _shikimori.value = _shikimori.value.cleared()
            "bangumi" -> _bangumi.value = _bangumi.value.cleared()
            "anilist" -> AniListFilter.entries.forEach { filter ->
                val flow = _aniListStates.getValue(filter)
                flow.value = flow.value.cleared()
            }
            "mal" -> _mal.value = _mal.value.cleared()
        }
    }

    private fun NotifItem.toArchived(): NotificationArchiveStore.ArchivedNotif =
        NotificationArchiveStore.ArchivedNotif(
            id = id,
            source = source,
            title = title,
            body = body,
            dateText = dateText,
            tsMs = tsMs,
            imageUrl = imageUrl,
            mediaId = mediaId,
            mediaType = mediaType,
            mediaTitle = mediaTitle,
            typeIdentifier = typeIdentifier,
            categoryName = categoryName
        )

    private fun NotificationArchiveStore.ArchivedNotif.toNotifItem(): NotifItem =
        NotifItem(
            id = id,
            imageUrl = imageUrl,
            title = title,
            body = body,
            dateText = dateText,
            tsMs = tsMs,
            mediaId = mediaId,
            mediaType = mediaType,
            mediaTitle = mediaTitle,
            typeIdentifier = typeIdentifier,
            categoryName = categoryName,
            source = source
        )

    // ─── AniList Yükleyici ────────────────────────────────────────────────────

    fun loadAniList(
        filter: AniListFilter,
        resetPage: Boolean = false,
        mediaEntries: List<MediaEntry> = emptyList()
    ) {
        val stateFlow = _aniListStates[filter] ?: return
        val current = stateFlow.value
        if (!resetPage && !current.hasMore) return
        if (current.isLoading) return

        val page = if (resetPage) 1 else current.page

        viewModelScope.launch {
            stateFlow.value = if (resetPage) {
                NotifUiState(isLoading = true)
            } else {
                current.copy(isLoading = true, error = null)
            }

            try {
                val token = ExternalAuthManager.getAniListToken(ctx)
                    ?: run {
                        stateFlow.value = stateFlow.value.copy(
                            isLoading = false,
                            error = ctx.getString(R.string.notif_login_required_anilist)
                        )
                        return@launch
                    }

                val client = KitsugiAniListNotificationClient()

                // Okunmamış sayaç yalnızca gerçekten sıfırlanabilsin: sadece "Tümü"
                // sekmesinin ilk sayfasında reset istenir (AniHyou davranışı).
                val shouldResetCount = resetPage && filter == AniListFilter.ALL
                val result = client.fetchNotifications(
                    accessToken = token,
                    page = page,
                    perPage = 25,
                    group = filter.group,
                    resetCount = shouldResetCount
                )

                if (shouldResetCount || _aniListUnread.value == null) {
                    // Sayaç okunamazsa akışı bozmuyoruz; yalnızca rozet güncellenmez.
                    _aniListUnread.value = client.fetchUnreadCount(token) ?: _aniListUnread.value
                }

                val mapped = result.notifications.map { n -> n.toNotifItem() }

                val existingItems = if (resetPage) emptyList() else stateFlow.value.items
                stateFlow.value = NotifUiState(
                    items = existingItems + mapped,
                    isLoading = false,
                    error = null,
                    hasMore = result.hasNextPage,
                    page = if (result.hasNextPage) page + 1 else page,
                    notice = null
                )
            } catch (e: Exception) {
                stateFlow.value = stateFlow.value.copy(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName), errorCode = com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve("anilist", e)
                )
            }
        }
    }

    /** AniList bildirimini UI modeline çevirir. */
    private fun KitsugiAniListNotificationClient.KitsugiNotification.toNotifItem(): NotifItem {
        val body = when (type) {
            "AIRING" -> buildString {
                airingContexts?.getOrNull(0)?.let { append(it) }
                append(" ")
                airingContexts?.getOrNull(1)?.let { append(it) }
                append(" ${episode}")
                airingContexts?.getOrNull(2)?.let { append(it) }
            }.trim().ifBlank { ctx.getString(R.string.notif_body_airing, episode) }
            "FOLLOWING"                 -> ctx.getString(R.string.notif_body_following, userName ?: "")
            "ACTIVITY_MESSAGE"          -> ctx.getString(R.string.notif_body_activity_message, userName ?: "")
            "ACTIVITY_REPLY"            -> ctx.getString(R.string.notif_body_activity_reply, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_reply_fallback))
            "ACTIVITY_REPLY_SUBSCRIBED" -> ctx.getString(R.string.notif_body_activity_reply_subscribed, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_reply_subscribed_fallback))
            "ACTIVITY_MENTION"          -> ctx.getString(R.string.notif_body_activity_mention, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_mention_fallback))
            "ACTIVITY_LIKE"             -> ctx.getString(R.string.notif_body_activity_like, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_like_fallback))
            "ACTIVITY_REPLY_LIKE"       -> ctx.getString(R.string.notif_body_activity_reply_like, userName ?: "", context ?: ctx.getString(R.string.notif_body_activity_reply_like_fallback))
            "THREAD_COMMENT_MENTION"    -> ctx.getString(R.string.notif_body_thread_comment_mention, userName ?: "")
            "THREAD_COMMENT_REPLY"      -> ctx.getString(R.string.notif_body_thread_comment_reply, userName ?: "")
            "THREAD_COMMENT_SUBSCRIBED" -> ctx.getString(R.string.notif_body_thread_comment_subscribed, userName ?: "")
            "THREAD_COMMENT_LIKE"       -> ctx.getString(R.string.notif_body_thread_comment_like, userName ?: "")
            "THREAD_LIKE"               -> ctx.getString(R.string.notif_body_thread_like, userName ?: "", threadTitle ?: ctx.getString(R.string.notif_body_thread_like_fallback))
            "RELATED_MEDIA_ADDITION"    -> ctx.getString(R.string.notif_body_related_media_addition, mediaTitle ?: "", context ?: ctx.getString(R.string.notif_body_related_media_addition_fallback))
            "MEDIA_DATA_CHANGE"         -> ctx.getString(R.string.notif_body_media_data_change, mediaTitle ?: "", context ?: ctx.getString(R.string.notif_body_media_data_change_fallback))
            "MEDIA_MERGE"               -> ctx.getString(R.string.notif_body_media_merge, mediaTitle ?: "", context ?: ctx.getString(R.string.notif_body_media_merge_fallback))
            "MEDIA_DELETION"            -> ctx.getString(R.string.notif_body_media_deletion, deletedMediaTitle ?: ctx.getString(R.string.notif_body_media_deletion_fallback))
            else                        -> context ?: ctx.getString(R.string.notif_body_new)
        }
        return NotifItem(
            id = "al_${id}",
            imageUrl = mediaCoverUrl ?: userAvatarUrl,
            title = mediaTitle ?: userName ?: threadTitle ?: "AniList",
            body = body,
            // Ortak tarih sözleşmesi: AniList de artık tsMs taşır ve diğer
            // kaynaklarla aynı birleşik biçimde ("Bugün 14:32" vb.) gösterilir.
            dateText = createdAt?.let { formatNotifTimestamp(it * 1000L) } ?: dateText,
            mediaId = mediaId,
            activityId = activityId,
            mediaType = mediaType?.lowercase(),
            mediaTitle = mediaTitle,
            userId = userId,
            userName = userName,
            userAvatarUrl = userAvatarUrl,
            // AniList `createdAt` değeri epoch SANİYEDİR; zaman grupları (Bugün/Dün/…)
            // ve "en yeni önce" sıralaması bu alan olmadan çalışamıyordu (tüm satırlar
            // "Tarihsiz" oluyordu).
            tsMs = createdAt?.let { if (it > 10_000_000_000L) it else it * 1000L },
            source = "AniList"
        )
    }

    // ─── MAL Yükleyici ───────────────────────────────────────────────────────

    /**
     * Loads MAL's actual account notifications from the signed-in website feed and maps them
     * into the native notification list. MAL OAuth remains useful for API/list operations, but
     * MAL's private notification page authenticates with its own WebView session cookie.
     * If that session is absent, retain the calendar-based fallback and expose the MAL Settings
     * reconnect action; never treat the OAuth bearer token as a site cookie.
     */
    fun loadMal(mediaEntries: List<MediaEntry>) {
        if (_mal.value.isLoading) {
            if (!malReloadQueued) {
                malReloadQueued = true
                viewModelScope.launch {
                    _mal.first { !it.isLoading }
                    malReloadQueued = false
                    loadMal(mediaEntries)
                }
            }
            return
        }
        _mal.value = _mal.value.copy(isLoading = true, error = null)
        malCoverLookup = mediaEntries.mapNotNull { entry ->
            val id = entry.malId
            val img = entry.imageUrl?.takeIf(String::isNotBlank)
            if (id != null && img != null) id to img else null
        }.toMap()
        viewModelScope.launch {
            var attemptedWithCookie = false
            var webFeedError: Exception? = null
            try {
                // Karar tahminle değil, MAL'a sorularak verilir: kavanozda MAL çerezi varsa
                // (OAuth girişi uygulama içi WebView'de yapıldıysa MALHLOGSESSID buradadır)
                // besleme bir kez denenir; MAL giriş sayfasına düşerse oturum gerçekten yoktur.
                val sessionCookie = MalWebSessionStore.browserCookieHeader()
                android.util.Log.d(
                    "KitsugiNotif",
                    "MAL web çerezleri: ${MalWebSessionStore.presentCookieNames(sessionCookie).ifEmpty { listOf("yok") }}"
                )
                if (sessionCookie != null) {
                    attemptedWithCookie = true
                    val feed = MalNotificationsClient.fetch(sessionCookie)
                    val identity = fetchMalOAuthIdentity()
                    val match = MalAccountMatch.status(
                        oauthUsername = identity.username,
                        webUsername = feed.sessionUsername,
                        oauthAvailable = identity.tokenAvailable
                    )
                    if (match == MalAccountMatch.Status.MISMATCHED) {
                        android.util.Log.w(
                            "KitsugiNotif",
                            "MAL account mismatch: oauth=${identity.username} web=${feed.sessionUsername}"
                        )
                        _mal.value = NotifUiState(
                            items = emptyList(),
                            isLoading = false,
                            hasMore = false,
                            error = ctx.getString(R.string.notif_mal_account_mismatch_error),
                            requiresMalWebLogin = true,
                            malAccountMismatch = true,
                            malOAuthUsername = identity.username,
                            malWebUsername = feed.sessionUsername
                        )
                        return@launch
                    }
                    malSidebarCounts = feed.categoryCounts
                    malCategoryUrls = feed.categoryUrls
                    val mapped = feed.items
                        .map { it.toNotifItem() }
                        .sortedWith(compareByDescending<NotifItem> { it.tsMs ?: Long.MIN_VALUE })
                    // Gerçek MAL web akışı aktifken eski takvim yedek kayıtları (`mal_<id>_<ep>`)
                    // temizlenir; aksi hâlde `Tümü` rozeti 25 + 3 = 28 gibi şişer.
                    val merged = archiveAndMerge(
                        source = "MyAnimeList",
                        live = mapped,
                        retainOldWhere = { it.id.startsWith(MalNotificationsClient.WEB_ID_PREFIX) }
                    )
                    val sidebarTotal = feed.categoryCounts[MalNotificationCategory.ALL]
                    val effectiveHasMore = if (sidebarTotal != null && merged.size >= sidebarTotal) {
                        false
                    } else {
                        feed.hasNextPage
                    }
                    val totalDisplayed = maxOf(merged.size, sidebarTotal ?: merged.size)
                    val feedNotice = if (match == MalAccountMatch.Status.UNVERIFIED) {
                        ctx.getString(R.string.notif_notice_mal_web_feed_unverified, totalDisplayed)
                    } else {
                        ctx.getString(R.string.notif_notice_mal_web_feed, totalDisplayed)
                    }
                    setMalStateWithFilter(
                        items = merged,
                        isLoading = false,
                        hasMore = effectiveHasMore,
                        page = feed.page,
                        nextPageUrl = feed.nextPageUrl,
                        batchSize = feed.items.size,
                        notice = feedNotice,
                        requiresLogin = false,
                        oauthUser = identity.username,
                        webUser = feed.sessionUsername
                    )
                    // İlk sayfa ekrana anında çizildikten hemen sonra eksik kalan eski
                    // bildirimleri (ör. 26..45) arka planda tamamla ve arşive yaz.
                    if (effectiveHasMore) {
                        loadMalMore()
                    }
                    return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                webFeedError = e
                android.util.Log.w("KitsugiNotif", "MAL account feed unavailable: ${e.message}")
            }

            try {
                val (calendarItems, calendarNotice) = loadMalAiringFallback(mediaEntries)
                val needsWebLogin = !attemptedWithCookie ||
                    webFeedError is MalWebSessionRequiredException
                val notice = when {
                    !attemptedWithCookie -> ctx.getString(
                        R.string.notif_notice_mal_web_session_required,
                        calendarNotice
                    )
                    webFeedError is MalWebSessionRequiredException -> ctx.getString(
                        R.string.notif_notice_mal_web_session_required,
                        calendarNotice
                    )
                    webFeedError is MalNotificationsParseException -> ctx.getString(
                        R.string.notif_notice_mal_web_parse_failed,
                        calendarNotice
                    )
                    else -> ctx.getString(
                        R.string.notif_notice_mal_web_fetch_failed,
                        calendarNotice
                    )
                }
                setMalStateWithFilter(
                    items = archiveAndMerge("MyAnimeList", calendarItems),
                    isLoading = false,
                    hasMore = false,
                    notice = notice,
                    requiresLogin = needsWebLogin
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("KitsugiNotif", "MAL fallback load failed: ${e.message}", e)
                _mal.value = _mal.value.copy(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName), errorCode = com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve("mal", e),
                    requiresMalWebLogin = !attemptedWithCookie ||
                        webFeedError is MalWebSessionRequiredException
                )
            }
        }
    }

    /**
     * Loads older MAL website notifications. Continuation is accepted only when it contains
     * IDs that are not already visible; transient failures preserve the feed and expose retry.
     */
    fun loadMalMore() {
        val current = _mal.value
        if (current.isLoading || !current.hasMore ||
            current.requiresMalWebLogin || current.malAccountMismatch
        ) {
            return
        }
        _mal.value = current.copy(isLoading = true, loadMoreError = null)
        viewModelScope.launch {
            try {
                val cookie = MalWebSessionStore.browserCookieHeader()
                if (cookie.isNullOrBlank()) {
                    _mal.value = current.copy(
                        isLoading = false,
                        hasMore = false,
                        requiresMalWebLogin = true,
                        loadMoreError = null
                    )
                    return@launch
                }

                // NotifItem kimlikleri `mal_web_<id>` öneklidir; MalNotificationsClient ise
                // ham `<id>` ile çalışır. Önek soyulmazsa her aday sayfa "yeni kayıt" sanılıp
                // sonsuz döngüye girer ve WebView devam motoruna hiç ulaşılamaz.
                val knownRawIds = MalNotificationsClient.normalizeKnownIds(malUnfilteredItems.map { it.id })
                val existingNotifIds = malUnfilteredItems.map { it.id }.toSet()
                val nextPage = malRawPage + 1
                val feed = MalNotificationsClient.fetchMore(
                    context = ctx,
                    cookieHeader = cookie,
                    page = nextPage,
                    nextPageUrl = malRawNextPageUrl,
                    knownIds = knownRawIds,
                    category = _malCategory.value.takeIf { it != MalNotificationCategory.ALL },
                    batchSize = malRawBatchSize,
                    categoryUrls = malCategoryUrls
                )
                if (feed == null) {
                    _mal.value = _mal.value.copy(isLoading = false, hasMore = false, loadMoreError = null)
                    malRawHasMore = false
                    return@launch
                }

                val identity = fetchMalOAuthIdentity()
                val match = MalAccountMatch.status(
                    oauthUsername = identity.username,
                    webUsername = feed.sessionUsername,
                    oauthAvailable = identity.tokenAvailable
                )
                if (match == MalAccountMatch.Status.MISMATCHED) {
                    android.util.Log.w(
                        "KitsugiNotif",
                        "MAL account mismatch during pagination: oauth=${identity.username} web=${feed.sessionUsername}"
                    )
                    _mal.value = _mal.value.copy(
                        items = emptyList(),
                        isLoading = false,
                        hasMore = false,
                        error = ctx.getString(R.string.notif_mal_account_mismatch_error),
                        requiresMalWebLogin = true,
                        malAccountMismatch = true,
                        malOAuthUsername = identity.username,
                        malWebUsername = feed.sessionUsername,
                        loadMoreError = null
                    )
                    return@launch
                }

                if (feed.categoryCounts.isNotEmpty()) {
                    malSidebarCounts = feed.categoryCounts
                }
                if (feed.categoryUrls.isNotEmpty()) {
                    malCategoryUrls = malCategoryUrls + feed.categoryUrls
                }

                val beforeCount = malUnfilteredItems.size
                val fresh = feed.items
                    .filter { MalNotificationsClient.rawNotificationId(it.id) !in knownRawIds }
                    .map { it.toNotifItem() }
                    .filter { it.id !in existingNotifIds }
                val mergedUnfiltered = (malUnfilteredItems + fresh)
                    .distinctBy { it.id }
                    .sortedWith(compareByDescending<NotifItem> { it.tsMs ?: Long.MIN_VALUE })

                if (fresh.isEmpty() || mergedUnfiltered.size <= beforeCount) {
                    _mal.value = _mal.value.copy(isLoading = false, hasMore = false, loadMoreError = null)
                    malRawHasMore = false
                } else {
                    writeArchive(
                        source = "MyAnimeList",
                        live = fresh,
                        retainOldWhere = { it.id.startsWith(MalNotificationsClient.WEB_ID_PREFIX) }
                    )
                    val sidebarTotal = malSidebarCounts[MalNotificationCategory.ALL]
                    val stillHasMore = if (sidebarTotal != null && mergedUnfiltered.size >= sidebarTotal) {
                        false
                    } else {
                        feed.hasNextPage
                    }
                    malUnfilteredItems = mergedUnfiltered
                    malRawHasMore = stillHasMore
                    malRawPage = feed.page
                    malRawNextPageUrl = feed.nextPageUrl
                    val totalDisplayed = maxOf(mergedUnfiltered.size, sidebarTotal ?: mergedUnfiltered.size)
                    malUnfilteredNotice = if (match == MalAccountMatch.Status.UNVERIFIED) {
                        ctx.getString(R.string.notif_notice_mal_web_feed_unverified, totalDisplayed)
                    } else {
                        ctx.getString(R.string.notif_notice_mal_web_feed, totalDisplayed)
                    }
                    val category = _malCategory.value
                    val filtered = if (category == MalNotificationCategory.ALL) {
                        mergedUnfiltered
                    } else {
                        mergedUnfiltered.filter { item ->
                            category.typeIdentifiers.any { it.equals(item.typeIdentifier, ignoreCase = true) }
                        }
                    }
                    _mal.value = _mal.value.copy(
                        items = filtered,
                        isLoading = false,
                        hasMore = stillHasMore,
                        page = feed.page,
                        nextPageUrl = feed.nextPageUrl,
                        notice = malUnfilteredNotice,
                        malOAuthUsername = identity.username,
                        malWebUsername = feed.sessionUsername,
                        loadMoreError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MalWebSessionRequiredException) {
                android.util.Log.w("KitsugiNotif", "MAL web session expired during pagination: ${e.message}")
                _mal.value = current.copy(
                    isLoading = false,
                    hasMore = false,
                    requiresMalWebLogin = true,
                    loadMoreError = null
                )
            } catch (e: Exception) {
                android.util.Log.w("KitsugiNotif", "MAL notifications could not be continued: ${e.message}")
                _mal.value = current.copy(
                    isLoading = false,
                    hasMore = true,
                    loadMoreError = ctx.getString(R.string.notif_mal_load_more_failed),
                    loadMoreErrorCode = com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve("mal", e)
                )
            }
        }
    }

    // ── Bildirim → medya kimliği çözümü ───────────────────────────────────────

    /** Araması süren bildirim başlığı; doluysa ekranda kısa bir yükleme göstergesi çıkar. */
    private val _resolvingMedia = MutableStateFlow<String?>(null)
    val resolvingMedia: StateFlow<String?> = _resolvingMedia.asStateFlow()

    /**
     * Bildirimin medya kimliği yoksa (ör. MAL `related_anime_add` kaydı yalnızca ad ve tür
     * taşıyorsa) normal arama API'siyle MAL kimliği bulunur; böylece detay sayfası
     * herhangi bir MAL detay sayfası gibi açılır (tarayıcıya veya yanlış kayda düşmez).
     */
    fun resolveMalMedia(
        title: String,
        mediaType: String?,
        onResolved: (JikanSearchResult?) -> Unit
    ) {
        val query = title.trim()
        if (query.isEmpty() || _resolvingMedia.value != null) {
            onResolved(null)
            return
        }
        _resolvingMedia.value = query
        viewModelScope.launch {
            val result = try {
                val type = if (mediaType?.contains("manga", ignoreCase = true) == true) {
                    MediaType.Manga
                } else {
                    MediaType.Anime
                }
                JikanSearchClient()
                    .searchMALOnly(query = query, mediaType = type, showAdultContent = true)
                    .firstOrNull { it.malId > 0 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("KitsugiNotif", "Bildirim medyası çözülemedi: ${e.message}")
                null
            }
            _resolvingMedia.value = null
            onResolved(result)
        }
    }

    private data class MalOAuthIdentity(
        /** `users/@me` ile okunan bağlı hesap adı; okunamadıysa `null`. */
        val username: String?,
        /** MAL OAuth jetonu mevcut mu? (ad okunamasa bile jeton var olabilir) */
        val tokenAvailable: Boolean
    )

    // ── MAL Kategori Filtresi ────────────────────────────────────────────────

    /**
     * MAL bildirimlerini sol menüdeki gibi kategoriye göre filtreler.
     * Filtreleme **istemci tarafındadır** — sunucu filtrelemeyi desteklemiyorsa bile doğru sonuç verir.
     * Sayılar ekran görüntülerindeki gibi (All 45, Related 39, Airing 4…) hesaplanır.
     */
    fun setMalFilter(category: MalNotificationCategory) {
        if (_malCategory.value == category) return
        _malCategory.value = category
        applyMalFilter()
        val expected = malSidebarCounts[category] ?: 0
        val currentCount = _mal.value.items.size
        if (malRawHasMore && (currentCount == 0 || currentCount < expected)) {
            loadMalMore()
        }
    }

    /**
     * Okunmamış MAL bildirimi sayısı — kategori filtresinden BAĞIMSIZ ham listeden
     * hesaplanır; böylece başlıktaki "47 bildirim • 25 yeni" özeti seçili kategoriyle
     * birlikte yanlış sayı göstermez.
     */
    fun getMalUnreadCount(): Int = malUnfilteredItems.count { it.isUnread }

    fun getMalCategoryCounts(): Map<MalNotificationCategory, Int> {
        if (malUnfilteredItems.isEmpty() && malSidebarCounts.isEmpty()) return emptyMap()
        val localCounts = mutableMapOf<MalNotificationCategory, Int>()
        MalNotificationCategory.entries.forEach { localCounts[it] = 0 }
        localCounts[MalNotificationCategory.ALL] = malUnfilteredItems.size
        for (item in malUnfilteredItems) {
            val cat = MalNotificationCategory.fromTypeIdentifier(item.typeIdentifier)
            if (cat != MalNotificationCategory.ALL) {
                localCounts[cat] = (localCounts[cat] ?: 0) + 1
            }
        }
        val mergedCounts = mutableMapOf<MalNotificationCategory, Int>()
        MalNotificationCategory.entries.forEach { cat ->
            mergedCounts[cat] = maxOf(malSidebarCounts[cat] ?: 0, localCounts[cat] ?: 0)
        }
        return mergedCounts
    }

    private fun applyMalFilter() {
        val category = _malCategory.value
        val filtered = if (category == MalNotificationCategory.ALL) {
            malUnfilteredItems
        } else {
            malUnfilteredItems.filter { item ->
                category.typeIdentifiers.any { it.equals(item.typeIdentifier, ignoreCase = true) }
            }
        }
        val current = _mal.value
        // Filtre değişiminde sayfalama korunur (ham değerler), ancak liste filtrelenir
        _mal.value = current.copy(
            items = filtered,
            // Filtreli görünümde "daha fazla yükle" ham listenin devamı varsa gösterilir
            hasMore = malRawHasMore,
            notice = malUnfilteredNotice
        )
    }

    private fun setMalStateWithFilter(
        items: List<NotifItem>,
        isLoading: Boolean = false,
        hasMore: Boolean = false,
        page: Int = 1,
        nextPageUrl: String? = null,
        /** Live response size only; archive rows must never affect offset calculation. */
        batchSize: Int? = null,
        notice: String? = null,
        requiresLogin: Boolean = false,
        mismatch: Boolean = false,
        oauthUser: String? = null,
        webUser: String? = null,
        error: String? = null
    ) {
        // Ham veriyi sakla — filtre değişiminde yeniden çekmeden uygulanır
        malUnfilteredItems = items
        malUnfilteredNotice = notice
        malRawHasMore = hasMore
        malRawPage = page
        malRawNextPageUrl = nextPageUrl
        malRawBatchSize = batchSize?.takeIf { it > 0 }
        val category = _malCategory.value
        val filtered = if (category == MalNotificationCategory.ALL) items
        else items.filter { item -> category.typeIdentifiers.any { it.equals(item.typeIdentifier, ignoreCase = true) } }
        _mal.value = NotifUiState(
            items = filtered,
            isLoading = isLoading,
            hasMore = hasMore,
            page = page,
            nextPageUrl = nextPageUrl,
            notice = notice,
            requiresMalWebLogin = requiresLogin,
            malAccountMismatch = mismatch,
            malOAuthUsername = oauthUser,
            malWebUsername = webUser,
            error = error
        )
    }

    /**
     * Bağlı MAL OAuth hesabının kullanıcı adını okur.
     *
     * Web oturumundan bağımsız bir kimliktir: OAuth jetonu `ExternalAuthManager` içinde, web
     * oturumu ise WebView çerez deposunda durur. Bildirimler ikisi AYNI hesabı gösterdiğinde
     * gösterilir.
     */
    private suspend fun fetchMalOAuthIdentity(): MalOAuthIdentity = withContext(Dispatchers.IO) {
        val token = runCatching { ExternalAuthManager.getOrRefreshMalToken(ctx) }.getOrNull()
        if (token.isNullOrBlank()) return@withContext MalOAuthIdentity(username = null, tokenAvailable = false)
        val username = runCatching { MalImportManager.fetchUserName(token) }.getOrNull()
        MalOAuthIdentity(username = username, tokenAvailable = true)
    }

    /**
     * WebView'in MAL web oturumu çerezlerini temizler ve arayüzü yeniden giriş akışına
     * yönlendirmek için [onCleared] çağırır (ör. giriş WebView'ini açan ekran).
     *
     * Yalnızca myanimelist.net çerezleri silinir; OAuth jetonları ve diğer platformların
     * oturumları olduğu gibi kalır.
     */
    fun clearMalWebSession(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            val cleared = MalWebSessionStore.clearSession()
            _mal.value = NotifUiState(
                items = emptyList(),
                isLoading = false,
                hasMore = false,
                notice = ctx.getString(R.string.notif_mal_web_session_cleared),
                requiresMalWebLogin = true
            )
            if (!cleared) {
                android.util.Log.w("KitsugiNotif", "MAL web oturumu çerezleri temizlenemedi")
            }
            onCleared()
        }
    }

    private suspend fun loadMalAiringFallback(mediaEntries: List<MediaEntry>): Pair<List<NotifItem>, String> {
        val nowSec = System.currentTimeMillis() / 1000L
        val fromSec = nowSec - 7L * 24 * 3600L
        val airings = KitsugiAiringCalendarClient().fetchAiringWindow(fromSec, nowSec + 2L * 3600L)

        val malEntries = mediaEntries.filter {
            (it.source.equals("jikan", ignoreCase = true) || it.source.equals("mal", ignoreCase = true)) &&
                (it.status == WatchStatus.Watching || it.status == WatchStatus.Repeating)
        }
        val malIds = malEntries.mapNotNull { it.malId }.toSet()
        val matched = airings
            .filter { it.malId != null && malIds.contains(it.malId) }
            .sortedByDescending { it.airingAt }

        val items = matched.map { entry ->
            val aired = entry.airingAt <= nowSec
            NotifItem(
                id = "mal_${entry.malId}_${entry.episode}",
                imageUrl = entry.coverUrl,
                title = entry.title,
                body = if (aired)
                    ctx.getString(R.string.notif_mal_episode_released, entry.episode)
                else
                    ctx.getString(R.string.notif_mal_episode_upcoming, entry.episode),
                dateText = formatNotifTimestamp(entry.airingAt * 1000L),
                tsMs = entry.airingAt * 1000L,
                mediaId = entry.malId,
                mediaType = "anime",
                source = "MyAnimeList"
            )
        }

        val notice = when {
            malEntries.isEmpty() -> ctx.getString(R.string.notif_notice_mal_no_list)
            items.isEmpty() -> ctx.getString(R.string.notif_notice_mal_no_airing, malIds.size)
            else -> ctx.getString(R.string.notif_notice_mal_feed, items.size, malIds.size)
        }
        return items to notice
    }

    private fun MalNotificationsClient.Notification.toNotifItem(): NotifItem {
        val createdAtMs = createdAt?.let { if (it > 10_000_000_000L) it else it * 1000L }
            ?: parseMalDisplayTimestampMs(createdAtForDisplay ?: date)
        val plainTitle = displayTitle()
        val title = if (isDeleted &&
            !plainTitle.contains("deleted", ignoreCase = true) &&
            !plainTitle.contains("silin", ignoreCase = true)
        ) {
            plainTitle + ctx.getString(R.string.notif_mal_deleted_suffix)
        } else plainTitle
        val cardTitle = mediaTitle ?: title
        val cardBody = if (mediaTitle != null) {
            listOfNotNull(
                displayTitle().takeIf { it.isNotBlank() },
                animeMediaType?.trim()?.takeIf { it.isNotEmpty() },
                date?.trim()?.takeIf { it.isNotEmpty() && typeIdentifier.equals("on_air", ignoreCase = true) }
            ).joinToString(" • ").ifBlank { displayBody() }
        } else {
            displayBody()
        }
        val resolvedImage = imageUrl ?: mediaId?.let { malCoverLookup[it] }
        return NotifItem(
            id = "${MalNotificationsClient.WEB_ID_PREFIX}$id",
            imageUrl = resolvedImage,
            title = cardTitle,
            body = cardBody,
            // Birleşik biçim önceliklidir; sunucu metni yalnızca tarih
            // çözülemediğinde yedek olarak kalır (tüm kaynaklarda aynı görünüm).
            dateText = createdAtMs?.let(::formatNotifTimestamp)
                ?: createdAtForDisplay?.takeIf(String::isNotBlank)
                ?: date?.takeIf(String::isNotBlank),
            isUnread = !isRead,
            mediaId = mediaId,
            mediaType = mediaType,
            typeIdentifier = typeIdentifier,
            categoryName = categoryName,
            mediaTitle = mediaTitle,
            userName = friendName ?: commentUserName ?: quoteUserName ?: postedUserName ?: senderName,
            userAvatarUrl = imageUrl,
            source = "MyAnimeList",
            targetUrl = normalizedTargetUrl(),
            tsMs = createdAtMs
        )
    }

    // ─── TMDB+Simkl Yükleyici ────────────────────────────────────────────────

    /**
     * Simkl'in de bildirim ucu yoktur. Doğru yöntem (Simkl resmî dokümanı):
     * kullanıcının "watching" listesi + v2 CDN takvimi (`data.simkl.in/calendar/v2/...`)
     * birleştirilir. Token yoksa yerel kayıtlarla eşleştirme yapılır.
     */
    fun loadTmdbSimkl(mediaEntries: List<MediaEntry>) {
        if (_tmdbSimkl.value.isLoading) return
        viewModelScope.launch {
            _tmdbSimkl.value = NotifUiState(isLoading = true)
            try {
                val calendarClient = SimklCalendarClient()
                val calendar = calendarClient.fetchAll()
                val token = ExternalAuthManager.getSimklToken(ctx)

                val now = System.currentTimeMillis()
                val windowStart = now - 7L * 24 * 3600_000
                val windowEnd = now + 7L * 24 * 3600_000
                val window = calendar.filter { it.airingAtMs in windowStart..windowEnd }

                var matched: List<SimklCalendarClient.SimklCalendarEntry> = emptyList()
                var notice: String

                val watchIds = if (!token.isNullOrBlank()) {
                    runCatching { calendarClient.fetchWatchingIds(token) }.getOrNull()
                } else null

                if (watchIds != null && !watchIds.isEmpty) {
                    matched = window.filter { watchIds.matches(it) }
                    notice = if (matched.isEmpty())
                        ctx.getString(R.string.notif_notice_simkl_no_airing)
                    else
                        ctx.getString(R.string.notif_notice_simkl_feed_api, matched.size)
                } else {
                    // Token yok/okunamadı → yerel kayıtlarla eşleştir (eski davranışın düzeltilmiş hâli)
                    val relevant = mediaEntries.filter { me ->
                        (me.source.equals("simkl", ignoreCase = true) || me.source.equals("tmdb", ignoreCase = true)) &&
                            (me.status == WatchStatus.Watching || me.status == WatchStatus.Repeating)
                    }
                    val simklIds = relevant.mapNotNull { it.simklId }.toSet()
                    val tmdbIds = relevant.mapNotNull { it.tmdbId }.toSet()
                    val malIds = relevant.mapNotNull { it.malId }.toSet()
                    matched = window.filter { entry ->
                        simklIds.contains(entry.simklId) ||
                            (entry.tmdbId != null && tmdbIds.contains(entry.tmdbId)) ||
                            (entry.malId != null && malIds.contains(entry.malId))
                    }
                    notice = when {
                        token.isNullOrBlank() -> ctx.getString(R.string.notif_notice_simkl_not_connected)
                        matched.isEmpty() -> ctx.getString(R.string.notif_notice_simkl_no_airing)
                        else -> ctx.getString(R.string.notif_notice_simkl_feed_local, matched.size)
                    }
                }

                val items = matched
                    .sortedByDescending { it.airingAtMs }
                    .map { entry ->
                        val aired = entry.airingAtMs <= now
                        NotifItem(
                            id = "simkl_${entry.simklId}_${entry.episode ?: 0}_${entry.airingAtMs}",
                            imageUrl = entry.posterUrl,
                            title = entry.title,
                            body = buildString {
                                val label = entry.episodeLabel
                                if (label != null) append(label) else append(ctx.getString(R.string.notif_simkl_movie_release))
                                append(" • ")
                                append(
                                    if (aired) ctx.getString(R.string.notif_state_aired)
                                    else ctx.getString(R.string.notif_state_upcoming)
                                )
                                if (entry.isFinale) append(" • ").append(ctx.getString(R.string.notif_badge_finale))
                            },
                            dateText = formatNotifTimestamp(entry.airingAtMs),
                            tsMs = entry.airingAtMs,
                            mediaId = entry.simklId,
                            mediaType = if (entry.isMovie) "movie" else "tv",
                            // Ortak filtre şeridi (Yayınlanan / Yaklaşan) bu alanı kullanır.
                            typeIdentifier = if (aired) "aired" else "upcoming",
                            source = "Simkl"
                        )
                    }

                _tmdbSimkl.value = NotifUiState(
                    items = archiveAndMerge("Simkl", items).withLocalSeen("simkl"),
                    isLoading = false,
                    hasMore = false,
                    notice = notice
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiNotif", "Simkl load failed: ${e.message}", e)
                _tmdbSimkl.value = NotifUiState(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName), errorCode = com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve("simkl", e)
                )
            }
        }
    }

    // ─── Kitsu Yükleyici ──────────────────────────────────────────────────────

    /**
     * Kitsu'nun genel API'sinde bildirim kaynağı **yoktur** (JSON:API kaynakları:
     * anime, manga, library-entries, comments, posts...). Bu sekme takip listesini ve
     * Kitsu'nun `nextRelease` alanından gelen "sonraki bölüm" tarihlerini gösterir.
     */
    fun loadKitsu(mediaEntries: List<MediaEntry> = emptyList()) {
        if (_kitsu.value.isLoading) return
        viewModelScope.launch {
            _kitsu.value = NotifUiState(isLoading = true)
            try {
                val token = ExternalAuthManager.getKitsuToken(ctx)
                val userId = ExternalAuthManager.getKitsuUserId(ctx)
                val items = mutableListOf<NotifItem>()

                if (!token.isNullOrBlank() && !userId.isNullOrBlank()) {
                    val entries = KitsuApiClient.fetchAllLibraryEntries(token, userId)
                    val activeEntries = entries.filter {
                        it.status.equals("current", true) || it.status.equals("watching", true)
                    }
                    val upcoming = activeEntries
                        .mapNotNull { entry ->
                            val release = parseIsoToEpochMs(entry.nextRelease) ?: return@mapNotNull null
                            entry to release
                        }
                        .sortedBy { it.second }

                    upcoming.forEach { (entry, releaseMs) ->
                        val aired = releaseMs <= System.currentTimeMillis()
                        items.add(
                            NotifItem(
                                id = "kitsu_next_${entry.id}",
                                imageUrl = entry.imageUrl,
                                title = entry.title,
                                body = ctx.getString(
                                    if (aired) R.string.notif_kitsu_episode_released
                                    else R.string.notif_kitsu_episode_upcoming,
                                    entry.progress + 1
                                ),
                                dateText = formatNotifTimestamp(releaseMs),
                                tsMs = releaseMs,
                                mediaId = entry.animeId ?: entry.mangaId,
                                mediaType = if (entry.animeId != null) "anime" else "manga",
                                typeIdentifier = "release",
                                source = "Kitsu"
                            )
                        )
                    }

                    // Sonraki bölüm tarihi olmayan aktif kayıtlar liste olarak gösterilir.
                    activeEntries
                        .filter { it.nextRelease.isNullOrBlank() }
                        .take(30)
                        .forEach { entry ->
                            items.add(
                                NotifItem(
                                    id = "kitsu_${entry.id}",
                                    imageUrl = entry.imageUrl,
                                    title = entry.title,
                                    body = if (entry.progress > 0)
                                        ctx.getString(R.string.notif_kitsu_progress, entry.progress)
                                    else
                                        ctx.getString(R.string.notif_kitsu_in_list),
                                    dateText = null,
                                    tsMs = null,
                                    mediaId = entry.animeId ?: entry.mangaId,
                                    mediaType = if (entry.animeId != null) "anime" else "manga",
                                    typeIdentifier = "list",
                                    source = "Kitsu"
                                )
                            )
                        }
                }

                if (items.isEmpty()) {
                    val kitsuEntries = mediaEntries.filter {
                        it.source.equals("kitsu", ignoreCase = true) &&
                            (it.status == WatchStatus.Watching || it.status == WatchStatus.Repeating)
                    }
                    kitsuEntries.forEach { me ->
                        items.add(
                            NotifItem(
                                id = "kitsu_local_${me.id}",
                                imageUrl = me.imageUrl,
                                title = me.title,
                                body = ctx.getString(R.string.notif_kitsu_in_list),
                                dateText = null,
                                mediaId = me.malId ?: me.id,
                                mediaType = "anime",
                                typeIdentifier = "list",
                                source = "Kitsu"
                            )
                        )
                    }
                }

                val notice = if (token.isNullOrBlank())
                    ctx.getString(R.string.notif_notice_kitsu_not_connected)
                else
                    ctx.getString(R.string.notif_notice_kitsu_api)

                _kitsu.value = NotifUiState(
                    items = archiveAndMerge("Kitsu", items).withLocalSeen("kitsu"),
                    isLoading = false,
                    hasMore = false,
                    notice = notice
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiNotif", "Kitsu load failed: ${e.message}", e)
                _kitsu.value = NotifUiState(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName), errorCode = com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve("kitsu", e)
                )
            }
        }
    }

    // ─── Bangumi Yükleyici ───────────────────────────────────────────────────

    /**
     * Bangumi (bgm.tv) API'sinde herkese açık bir bildirim ucu **yoktur**
     * (kaynaklar: 条目, 章节, 人物, 角色, 收藏, 目录...). Bu sekme diğer
     * "bildirim ucu olmayan" kaynaklarla (MAL, Kitsu) aynı mantığı izler:
     *
     *  1. Kullanıcının Bangumi'deki izleme listesi (在看 / DOING koleksiyonu);
     *     hesap bağlı değilse yerel Kitsugi kayıtları.
     *  2. Çapraz kimlik çözümü (önbellekli) ile MAL ID'leri.
     *  3. Yayın takvimi (son 7 gün + yaklaşan) eşleşmesi → "bölüm yayınlandı /
     *     yayınlanacak" bildirimleri.
     *
     * Üretilen kayıtlar [archiveAndMerge] ile yerel arşive + buluta yazılır;
     * pencereden düşen bölümler kaybolmaz.
     */
    private data class BangumiWatching(
        val stableId: Int,
        val title: String,
        val imageUrl: String?,
        val progress: Int,
        val updatedAtMs: Long?
    )

    /**
     * Bangumi `/v0/users/{username}/collections` ve yerel kayıtlar yalnızca özgün (çoğunlukla
     * Japonca/Çince) başlığı sağlar; `BangumiSlimSubject.displayTitle` İngilizce/romaji alanı
     * hiç içermez. Bu, Bildirimler → Bangumi sekmesinin seçili başlık dilini (ör. İngilizce)
     * yok sayıp her zaman özgün adı göstermesine yol açıyordu (bkz. kullanıcı bildirimi).
     *
     * Listem/Profil sekmeleriyle aynı sözleşmeyi uygular: önce kalıcı [BangumiTitleCache]'den
     * anında bir karşılık aranır, hâlâ CJK ise (veya İngilizce seçiliyken güvenilir İngilizce
     * yoksa) [BangumiSubjectTitleResolver] ile arka planda çözülür ve sonuç kalıcı önbelleğe
     * yazılır — aynı kayıt bir daha hangi ekranda görünürse görünsün seçilen dilde kalır.
     */
    private suspend fun resolveBangumiWatchingTitles(
        watching: List<BangumiWatching>,
        titleLanguage: String
    ): Map<Int, String> {
        if (watching.isEmpty()) return emptyMap()
        val wantsLatin = !titleLanguage.equals("NATIVE", ignoreCase = true) &&
            !titleLanguage.equals("JAPANESE_STAFF", ignoreCase = true)

        val resolvedTitles = mutableMapOf<Int, String>()
        val pendingRequests = mutableListOf<BangumiSubjectTitleResolver.Request>()
        val uniqueWatching = watching.distinctBy { it.stableId }

        for (w in uniqueWatching) {
            // BangumiTitleCache her zaman GERÇEK subject_id (raw) ile anahtarlanır; bu
            // ekrandaki `stableId` ise uygulama genelindeki offset'li kimliktir (bkz.
            // BangumiIdNamespace). İkisini karıştırmak önbelleği hep ıskalardı.
            val rawId = BangumiIdNamespace.rawIdOrNull(w.stableId)
            val localized = BangumiNameLocalizer.entity(w.title)
            val base = localized.displayFor(titleLanguage)
            val cachedBest = BangumiTitleCache.localizedTitleFor(rawId, base, titleLanguage) ?: base
            resolvedTitles[w.stableId] = cachedBest
            if (!wantsLatin) continue

            val cached = BangumiTitleCache.get(rawId)
            val hasReliableEnglish = BangumiSubjectTitleResolver.hasReliableEnglish(
                BangumiTitleCache.LatinTitles(
                    localized.romaji ?: cached?.romaji,
                    localized.english ?: cached?.english,
                    localized.native ?: cached?.native
                )
            )
            val needsEnglish = titleLanguage.equals("ENGLISH", ignoreCase = true) && !hasReliableEnglish
            val needsAnyLatin = PreferenceHelpers.hasCjkCharacters(cachedBest)
            if (needsEnglish || needsAnyLatin) {
                BangumiSubjectTitleResolver.Request.fromRow(w.stableId, w.title, "anime")
                    ?.let(pendingRequests::add)
            }
        }

        if (wantsLatin && pendingRequests.isNotEmpty()) {
            // Bildirimler sekmesi genelde zaten birkaç ağ çağrısı bekliyor (koleksiyon +
            // çapraz kimlik + takvim); çözümleyici burada ÖN PLAN modunda (background=false)
            // çalıştırılır ki ilk açılışta da doğru dil görünsün, sonraki ziyaretler önbellekten
            // anında gelir.
            // Bangumi infobox isteği yerine öncelikle önbellek ve AniList toplu araması kullanılır.
            runCatching { BangumiSubjectTitleResolver.resolve(pendingRequests, background = false) }
            for (w in uniqueWatching) {
                val rawId = BangumiIdNamespace.rawIdOrNull(w.stableId)
                val base = resolvedTitles[w.stableId] ?: w.title
                BangumiTitleCache.localizedTitleFor(rawId, base, titleLanguage)?.let {
                    resolvedTitles[w.stableId] = it
                }
            }
        }
        return resolvedTitles
    }

    fun loadTmdbAccount() {
        if (_tmdbAccount.value.isLoading) return
        viewModelScope.launch {
            _tmdbAccount.value = _tmdbAccount.value.copy(isLoading = true, error = null, errorCode = null)
            try {
                val notificationsEnabled = settingsDataStore.settingsFlow.first().tmdbNotificationsEnabled
                val live = if (TmdbAuthStore.isConnected(ctx) && notificationsEnabled) {
                    val result = com.kitsugi.animelist.core.notifications.TmdbAccountNotificationPoller.poll(ctx) { polled ->
                        com.kitsugi.animelist.core.notifications.archiveTmdbAccountNotifications(
                            ctx, polled.archivedNotifications
                        )
                    }
                    result.archivedNotifications.map { it.toNotifItem() }
                } else emptyList()
                _tmdbAccount.value = NotifUiState(
                    items = archiveAndMerge("TMDB", live, writeLive = false),
                    isLoading = false,
                    hasMore = false,
                    notice = ctx.getString(R.string.notif_notice_tmdb_account)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _tmdbAccount.value = _tmdbAccount.value.copy(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName),
                    errorCode = PlatformFailureRegistry.reportAndResolve("tmdb", e)
                )
            }
        }
    }

    fun loadBangumi(mediaEntries: List<MediaEntry> = emptyList(), titleLanguage: String? = null) {
        if (_bangumi.value.isLoading) return
        viewModelScope.launch {
            _bangumi.value = NotifUiState(isLoading = true)
            try {
                val token = runCatching { BangumiAuthStore.getValidToken(ctx) }.getOrNull()
                val username = if (!token.isNullOrBlank()) {
                    runCatching { BangumiAuthStore.resolveUsername(ctx) }.getOrNull()
                } else null
                val resolvedTitleLanguage = titleLanguage
                    ?: runCatching { settingsDataStore.settingsFlow.first().titleLanguage }.getOrDefault("ENGLISH")

                val watching = mutableListOf<BangumiWatching>()

                // 1) Bağlıysa Bangumi'nin kendi izleme listesini çek (在看)
                if (!token.isNullOrBlank() && !username.isNullOrBlank()) {
                    runCatching {
                        BangumiApiClient.getUserCollections(
                            token = token,
                            username = username,
                            collectionType = BangumiApiClient.CollectionType.DOING,
                            limit = 50
                        )
                    }.getOrNull()?.data?.forEach { col ->
                        val stableId = BangumiIdNamespace.stableIdFromRaw(col.subjectId) ?: return@forEach
                        watching.add(
                            BangumiWatching(
                                stableId = stableId,
                                title = col.subject?.displayTitle ?: "Bangumi",
                                imageUrl = col.subject?.images?.poster,
                                progress = col.epStatus,
                                updatedAtMs = parseIsoToEpochMs(col.updatedAt)
                            )
                        )
                    }
                }

                // 2) API yok/boşsa yerel Bangumi kayıtları (malId zaten stableId'dir)
                if (watching.isEmpty()) {
                    mediaEntries
                        .filter {
                            it.source.equals(BangumiIdNamespace.SOURCE, ignoreCase = true) &&
                                (it.status == WatchStatus.Watching || it.status == WatchStatus.Repeating)
                        }
                        .take(50)
                        .forEach { me ->
                            val stableId = me.malId ?: return@forEach
                            if (BangumiIdNamespace.isStableId(stableId)) {
                                watching.add(
                                    BangumiWatching(
                                        stableId = stableId,
                                        title = me.title,
                                        imageUrl = me.imageUrl,
                                        progress = me.progress,
                                        updatedAtMs = if (me.updatedAt > 0) me.updatedAt else null
                                    )
                                )
                            }
                        }
                }

                // 2.5) Bangumi'nin koleksiyon/takvim uçları yalnızca özgün (Japonca/Çince) adı
                // döndürür; seçili başlık diline uygun Latin karşılığı burada çözülür (bkz.
                // BangumiProfileContent ile aynı sözleşme). Böylece Bildirimler sekmesi de
                // Listem/Profil ile aynı başlığı gösterir.
                val localizedBangumiTitles = resolveBangumiWatchingTitles(watching, resolvedTitleLanguage)
                val watchingWithTitles = watching.map { w ->
                    localizedBangumiTitles[w.stableId]?.let { w.copy(title = it) } ?: w
                }

                // 3) Çapraz kimlik → MAL ID (bellek + dosya önbellekli; paralel)
                val resolved = coroutineScope {
                    watchingWithTitles.take(50).map { w ->
                        async(Dispatchers.IO) {
                            val cross = runCatching {
                                KitsugiBangumiDetailClient.resolveCrossIds(w.stableId, MediaType.Anime)
                            }.getOrNull()
                            w to cross?.malId?.takeIf { it in 1..99_999_999 }
                        }
                    }.map { it.await() }
                }
                val malToStable = resolved
                    .filter { it.second != null }
                    .associate { (w, mal) -> mal!! to w.stableId }
                val malIds = malToStable.keys

                // 4) Yayın takvimi eşleşmesi (son 7 gün + 2 saat sonrası)
                val nowSec = System.currentTimeMillis() / 1000L
                val airings = KitsugiAiringCalendarClient()
                    .fetchAiringWindow(nowSec - 7L * 24 * 3600L, nowSec + 2L * 3600L)
                val matched = airings
                    .filter { it.malId != null && malIds.contains(it.malId) }
                    .sortedByDescending { it.airingAt }

                val items = mutableListOf<NotifItem>()
                val matchedMalIds = matched.mapNotNull { it.malId }.toSet()

                matched.forEach { entry ->
                    val aired = entry.airingAt <= nowSec
                    items.add(
                        NotifItem(
                            id = "bangumi_${entry.malId}_${entry.episode}",
                            imageUrl = entry.coverUrl,
                            title = entry.title,
                            body = if (aired)
                                ctx.getString(R.string.notif_mal_episode_released, entry.episode)
                            else
                                ctx.getString(R.string.notif_mal_episode_upcoming, entry.episode),
                            dateText = formatNotifTimestamp(entry.airingAt * 1000L),
                            tsMs = entry.airingAt * 1000L,
                            mediaId = entry.malId?.let { malToStable[it] },
                            mediaType = "anime",
                            // Ortak filtre şeridi (Yayın / İlerleme) bu alanı kullanır.
                            typeIdentifier = "airing",
                            source = "Bangumi"
                        )
                    )
                }

                // Takvime düşmeyen izlenenler: ilerleme durumu olarak listelenir.
                resolved
                    .filter { (w, mal) -> mal == null || mal !in matchedMalIds }
                    .forEach { (w, _) ->
                        items.add(
                            NotifItem(
                                id = "bangumi_watching_${w.stableId}",
                                imageUrl = w.imageUrl,
                                title = w.title,
                                body = if (w.progress > 0)
                                    ctx.getString(R.string.notif_kitsu_progress, w.progress)
                                else
                                    ctx.getString(R.string.notif_bangumi_in_list),
                                dateText = w.updatedAtMs?.let { formatNotifTimestamp(it) },
                                tsMs = w.updatedAtMs,
                                mediaId = w.stableId,
                                mediaType = "anime",
                                typeIdentifier = "watching",
                                source = "Bangumi"
                            )
                        )
                    }

                val notice = when {
                    token.isNullOrBlank() -> ctx.getString(R.string.notif_notice_bangumi_not_connected)
                    watching.isEmpty() -> ctx.getString(R.string.notif_notice_bangumi_no_list)
                    else -> ctx.getString(R.string.notif_notice_bangumi_feed, watching.size, matched.size)
                }

                _bangumi.value = NotifUiState(
                    items = archiveAndMerge("Bangumi", items).withLocalSeen("bangumi"),
                    isLoading = false,
                    hasMore = false,
                    notice = notice
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiNotif", "Bangumi load failed: ${e.message}", e)
                _bangumi.value = NotifUiState(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName), errorCode = com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve("bangumi", e)
                )
            }
        }
    }

    // ─── Shikimori Yükleyici ──────────────────────────────────────────────────

    /**
     * Shikimori'de **gerçek** bildirim ucu vardır:
     *   GET /api/users/:id/messages?type=notifications  (ayrıca: inbox, private, sent, news)
     *   GET /api/users/:id/unread_messages              → { messages, news, notifications }
     * İkisi de `messages` OAuth izni ister. İzin yoksa izleme geçmişine (history) düşülür
     * ve kullanıcıya izni nasıl vereceği açıkça söylenir.
     */
    fun loadShikimori(mediaEntries: List<MediaEntry> = emptyList()) {
        if (_shikimori.value.isLoading) return
        viewModelScope.launch {
            _shikimori.value = NotifUiState(isLoading = true)
            try {
                val token = runCatching { ExternalAuthManager.getOrRefreshShikimoriToken(ctx) }
                    .getOrNull() ?: ExternalAuthManager.getShikimoriToken(ctx)
                var userId = ExternalAuthManager.getShikimoriUserId(ctx)
                if (!token.isNullOrBlank() && userId == null && runCatching { ExternalAuthManager.ensureShikimoriUserResolved(ctx) }.getOrDefault(false)) {
                    userId = ExternalAuthManager.getShikimoriUserId(ctx)
                }

                if (token.isNullOrBlank() || userId == null) {
                    _shikimori.value = NotifUiState(
                        isLoading = false,
                        hasMore = false,
                        error = ctx.getString(R.string.notif_login_required_shikimori)
                    )
                    return@launch
                }

                val unread = runCatching { ShikimoriApiClient.fetchUnreadCounts(token, userId) }.getOrNull()

                var scopeMissing = false
                val realNotifications = mutableListOf<ShikimoriApiClient.ShikimoriMessage>()
                try {
                    realNotifications += ShikimoriApiClient.fetchMessages(token, userId, "notifications", limit = 30)
                    realNotifications += ShikimoriApiClient.fetchMessages(token, userId, "news", limit = 15)
                } catch (_: ShikimoriApiClient.ShikimoriScopeException) {
                    scopeMissing = true
                }

                if (realNotifications.isNotEmpty()) {
                    val items = realNotifications
                        .sortedByDescending { parseIsoToEpochMs(it.createdAt) ?: 0L }
                        .map { msg -> msg.toNotifItem() }

                    val unreadText = unread?.let {
                        ctx.getString(R.string.notif_notice_shikimori_unread, it.notifications, it.news)
                    } ?: ""

                    _shikimori.value = NotifUiState(
                        items = items.withLocalSeen("shikimori", serverState = true),
                        isLoading = false,
                        hasMore = false,
                        notice = ctx.getString(R.string.notif_notice_shikimori_real, items.size) + unreadText
                    )
                    return@launch
                }

                // Gerçek bildirim yok / izin yok → izleme geçmişi (history) akışına düş.
                val history = ShikimoriApiClient.fetchUserHistory(token, userId, limit = 40)
                val historyItems = history.map { h ->
                    val historyTsMs = parseIsoToEpochMs(h.createdAt)
                    NotifItem(
                        id = "shikimori_${h.id}",
                        imageUrl = h.targetImageUrl,
                        title = h.targetTitle,
                        body = h.description.ifBlank { ctx.getString(R.string.notif_shikimori_activity_fallback) },
                        // Eskiden ham "2026-10-21" metni gösteriliyordu; artık
                        // diğer kaynaklarla aynı birleşik biçim + tsMs sıralaması.
                        dateText = historyTsMs?.let { formatNotifTimestamp(it) } ?: h.createdAt.take(10),
                        tsMs = historyTsMs,
                        mediaId = h.targetId?.toInt(),
                        mediaType = h.targetType ?: "anime",
                        typeIdentifier = "history",
                        source = "Shikimori"
                    )
                }

                val notice = if (scopeMissing)
                    ctx.getString(R.string.notif_notice_shikimori_scope_missing)
                else
                    ctx.getString(R.string.notif_notice_shikimori_history_only)

                _shikimori.value = NotifUiState(
                    items = historyItems.sortedWith(
                        compareByDescending<NotifItem> { it.tsMs ?: Long.MIN_VALUE }
                    ),
                    isLoading = false,
                    hasMore = false,
                    notice = notice
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiNotif", "Shikimori load failed: ${e.message}", e)
                _shikimori.value = NotifUiState(
                    isLoading = false,
                    error = ctx.getString(R.string.notif_error_load, e.message ?: e.javaClass.simpleName), errorCode = com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve("shikimori", e)
                )
            }
        }
    }

    private fun ShikimoriApiClient.ShikimoriMessage.toNotifItem(): NotifItem {
        val kindLabel = when (kind.lowercase()) {
            "anons" -> ctx.getString(R.string.notif_shikimori_kind_anons)
            "news" -> ctx.getString(R.string.notif_shikimori_kind_news)
            else -> ctx.getString(R.string.notif_shikimori_kind_generic)
        }
        val text = stripBbcode(body).ifBlank { stripHtml(htmlBody) }
        val messageTsMs = parseIsoToEpochMs(createdAt)
        return NotifItem(
            id = "shikimori_msg_${type}_${id}",
            imageUrl = targetImageUrl ?: fromAvatarUrl,
            title = targetTitle ?: kindLabel,
            body = buildString {
                append(kindLabel)
                if (text.isNotBlank()) append(" • ").append(text.take(160))
            },
            dateText = messageTsMs?.let { formatNotifTimestamp(it) } ?: createdAt.take(10),
            tsMs = messageTsMs,
            isUnread = !read,
            mediaId = linkedId?.toInt(),
            mediaType = linkedType?.lowercase(),
            userId = fromUserId,
            userName = fromNickname,
            userAvatarUrl = fromAvatarUrl,
            // Ortak filtre şeridi (Bildirimler / Haberler) bu alanı kullanır.
            typeIdentifier = type,
            source = "Shikimori"
        )
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    /**
     * Tum kaynaklarda tek tarih bicimi: bugun -> "Bugun 14:32", dun -> "Dun 09:10",
     * ayni yil -> "12 Eki 14:32", daha eski -> "12 Eki 2025 14:32".
     * Eskiden her kaynak kendi bicimini kullaniyordu (MAL sunucu metni, Shikimori
     * ham "2026-10-21", digerleri "dd MMM yyyy HH:mm").
     */
    private fun formatNotifTimestamp(ms: Long): String {
        val nowCal = java.util.Calendar.getInstance()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        fun startOfDay(c: java.util.Calendar): Long =
            (c.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        val dayDiff = (startOfDay(nowCal) - startOfDay(cal)) / 86_400_000L
        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
        return when {
            dayDiff == 0L -> ctx.getString(R.string.notif_date_today, timeText)
            dayDiff == 1L -> ctx.getString(R.string.notif_date_yesterday, timeText)
            cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) ->
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(ms))
            else -> SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(ms))
        }
    }

    private fun parseIsoToEpochMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        return try {
            when {
                text.endsWith("Z", true) -> Instant.parse(text).toEpochMilli()
                text.length >= 19 -> OffsetDateTime.parse(text).toInstant().toEpochMilli()
                else -> null
            }
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun stripBbcode(raw: String): String =
        raw.replace(Regex("\\[/?[a-zA-Z0-9=*]+[^\\]]*\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun stripHtml(raw: String): String =
        raw.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
}

private val MAL_MONTH_INDEX = mapOf(
    "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3,
    "may" to 4, "jun" to 5, "jul" to 6, "aug" to 7,
    "sep" to 8, "sept" to 8, "oct" to 9, "nov" to 10, "dec" to 11
)

private val MAL_DISPLAY_DATE_REGEX = Regex(
    """^([A-Za-z]{3,4})\.?\s+(\d{1,2})(?:,\s*(\d{4}))?(?:,?\s+(\d{1,2}):(\d{2})\s*([AaPp][Mm]))?$"""
)

private val MAL_RELATIVE_AGO_REGEX = Regex(
    """^(\d+)\s+(second|minute|hour|day)s?\s+ago$""",
    RegexOption.IGNORE_CASE
)

/**
 * Parses MAL's US-English display timestamps when `createdAt` epoch seconds are omitted
 * (for example `"Dec 20, 2025 11:49 PM"`, current-year `"Jan 2, 8:03 PM"`, or `"3 hours ago"`).
 * Avoids `SimpleDateFormat` so JDK 17+ CLDR `\u202F` narrow-nbsp and device locales never break parsing.
 */
internal fun parseMalDisplayTimestampMs(
    raw: String?,
    nowMs: Long = System.currentTimeMillis()
): Long? {
    val clean = raw
        ?.replace('\u00A0', ' ')
        ?.replace('\u202F', ' ')
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null

    MAL_RELATIVE_AGO_REGEX.matchEntire(clean)?.let { rel ->
        val amount = rel.groupValues[1].toLongOrNull() ?: return null
        val unitMs = when (rel.groupValues[2].lowercase(Locale.ROOT)) {
            "second" -> 1_000L
            "minute" -> 60_000L
            "hour" -> 3_600_000L
            "day" -> 86_400_000L
            else -> return null
        }
        return nowMs - amount * unitMs
    }

    val match = MAL_DISPLAY_DATE_REGEX.matchEntire(clean) ?: return null
    val month = MAL_MONTH_INDEX[match.groupValues[1].lowercase(Locale.ROOT)] ?: return null
    val day = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    val explicitYear = match.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
    val rawHour = match.groupValues[4].takeIf(String::isNotEmpty)?.toIntOrNull()
    val minute = match.groupValues[5].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
    val amPm = match.groupValues[6].lowercase(Locale.ROOT)

    val hour24 = if (rawHour == null) {
        0
    } else {
        if (rawHour !in 1..12 || minute !in 0..59) return null
        when (amPm) {
            "pm" -> if (rawHour == 12) 12 else rawHour + 12
            "am" -> if (rawHour == 12) 0 else rawHour
            else -> return null
        }
    }

    val utc = java.util.TimeZone.getTimeZone("UTC")
    fun buildEpochMs(year: Int): Long? = runCatching {
        java.util.Calendar.getInstance(utc).apply {
            isLenient = false
            clear()
            set(year, month, day, hour24, minute, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }.getOrNull()

    if (explicitYear != null) {
        return buildEpochMs(explicitYear)
    }

    val refYear = java.util.Calendar.getInstance(utc).apply { timeInMillis = nowMs }
        .get(java.util.Calendar.YEAR)
    val candidate = buildEpochMs(refYear) ?: return null
    return if (candidate > nowMs + 7L * 24 * 3600_000L) {
        buildEpochMs(refYear - 1) ?: candidate
    } else {
        candidate
    }
}
