package com.kitsugi.animelist.ui.app

import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.ui.utils.plainLabel
import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.data.auth.runSyncCatching
import com.kitsugi.animelist.data.auth.CrossSyncRetry
import com.kitsugi.animelist.data.auth.CrossSyncReportStore
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.AniListImportManager
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import com.kitsugi.animelist.data.auth.AniListSyncManager
import com.kitsugi.animelist.data.auth.MalSyncManager
import com.kitsugi.animelist.data.auth.MalImportManager
import com.kitsugi.animelist.data.auth.SimklImportManager
import com.kitsugi.animelist.data.auth.SimklSyncManager
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.data.auth.KitsuImportManager
import com.kitsugi.animelist.data.auth.KitsuSyncManager
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.data.auth.ShikimoriImportManager
import com.kitsugi.animelist.data.auth.ShikimoriSyncManager
import com.kitsugi.animelist.data.auth.BangumiAuthManager
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.BangumiImportManager
import com.kitsugi.animelist.data.auth.BangumiSyncManager
import com.kitsugi.animelist.data.auth.TmdbAuthStore
import com.kitsugi.animelist.data.auth.TmdbAccountSyncCoordinator
import com.kitsugi.animelist.data.auth.TmdbSyncManager
import com.kitsugi.animelist.data.auth.supportsTmdbAccountType
import com.kitsugi.animelist.data.auth.tmdbWatchlistValue
import com.kitsugi.animelist.data.remote.TmdbAccountClient
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydrator
import com.kitsugi.animelist.data.remote.MalAxisLibraryTitleHydrator
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.sync.ListRefreshMode
import com.kitsugi.animelist.data.sync.ListRefreshOutcome
import com.kitsugi.animelist.data.sync.SourceListSynchronizer
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors

/** [syncAllLists] içinde kaynaklar arası bekleme (kaynakları aynı anda dövmemek için). */
private const val SYNC_ALL_STAGGER_MS = 700L

private fun Throwable.crossSyncDiagnostic(context: Context): String = buildString {
    var current: Throwable? = this@crossSyncDiagnostic
    var depth = 0
    while (current != null && depth < 8) {
        val label = if (depth == 0) {
            KitsugiStrings.get(R.string.cross_sync_diag_error_label)
        } else {
            KitsugiStrings.get(R.string.cross_sync_diag_cause_label, depth)
        }
        appendLine(
            KitsugiStrings.get(
                R.string.cross_sync_diag_exception_line,
                label,
                current.javaClass.name,
                current.message ?: KitsugiStrings.get(R.string.cross_sync_diag_no_message)
            )
        )
        current.stackTrace.take(6).forEach { frame ->
            appendLine("  at ${frame.className}.${frame.methodName}(${frame.fileName ?: "?"}:${frame.lineNumber})")
        }
        current = current.cause
        depth++
    }
}

/**
 * `malId` alanı yalnızca gerçek MAL kimliği değil; uygulama Kitsu (id + 300M) ve AniList (id + 100M)
 * kayıtlarını da bu alanda iç ad alanıyla taşır. Raporda bunlar MAL kimliği gibi okunmasın diye etiketlenir.
 */
private fun describeMalIdField(context: Context, malId: Int?): String {
    val noValue = KitsugiStrings.get(R.string.cross_sync_diag_no_value)
    return when {
        malId == null -> noValue
        malId in 300_000_001..399_999_999 -> KitsugiStrings.get(
            R.string.cross_sync_diag_mal_kitsu,
            malId,
            malId - 300_000_000
        )
        malId in 100_000_001..299_999_999 -> KitsugiStrings.get(
            R.string.cross_sync_diag_mal_anilist,
            malId,
            malId - 100_000_000
        )
        else -> KitsugiStrings.get(R.string.cross_sync_diag_mal, malId)
    }
}

private fun MediaEntry.crossSyncIdentityDiagnostic(context: Context): String = buildString {
    fun clean(value: String?): String = value.orEmpty().replace('\n', ' ').replace('\r', ' ').trim()
    val noValue = KitsugiStrings.get(R.string.cross_sync_diag_no_value)
    appendLine(KitsugiStrings.get(R.string.cross_sync_diag_title, clean(title)))
    titleEnglish?.takeIf { it.isNotBlank() }?.let {
        appendLine(KitsugiStrings.get(R.string.cross_sync_diag_english_title, clean(it)))
    }
    titleJapanese?.takeIf { it.isNotBlank() }?.let {
        appendLine(KitsugiStrings.get(R.string.cross_sync_diag_japanese_title, clean(it)))
    }
    val typeLabel = when (type) {
        MediaType.Anime -> KitsugiStrings.get(R.string.media_type_anime)
        MediaType.Manga -> KitsugiStrings.get(R.string.media_type_manga)
        MediaType.Movie -> KitsugiStrings.get(R.string.media_type_movie)
        MediaType.TvShow -> KitsugiStrings.get(R.string.media_type_tv)
    }
    appendLine(KitsugiStrings.get(R.string.cross_sync_diag_type_year, typeLabel, year?.toString() ?: noValue))
    appendLine(KitsugiStrings.get(R.string.cross_sync_diag_source, clean(source)))
    val identityKeys = com.kitsugi.animelist.model.MediaIdentity.keys(this@crossSyncIdentityDiagnostic)
    appendLine(
        KitsugiStrings.get(
            R.string.cross_sync_diag_external_ids,
            identityKeys.takeIf { it.isNotEmpty() }?.joinToString() ?: noValue
        )
    )
    appendLine(
        KitsugiStrings.get(
            R.string.cross_sync_diag_raw_ids,
            describeMalIdField(context, malId),
            aniListEntryId?.toString() ?: noValue,
            malListId?.toString() ?: noValue,
            simklId?.toString() ?: noValue,
            tmdbId?.toString() ?: noValue
        )
    )
    appendLine(
        KitsugiStrings.get(
            R.string.cross_sync_diag_status_progress,
            status.plainLabel(),
            progress,
            total?.toString() ?: "?",
            volumeProgress,
            score?.toString() ?: noValue
        )
    )
    appendLine(
        KitsugiStrings.get(
            R.string.cross_sync_diag_dates,
            startDate ?: noValue,
            endDate ?: noValue,
            isFavorite.toString()
        )
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TvSyncEntryPoint {
    fun channelSyncService(): com.kitsugi.animelist.core.recommendations.TvChannelSyncService
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    var isAniListConnected by mutableStateOf(false)
        private set

    var isMalConnected by mutableStateOf(false)
        private set

    var isSimklConnected by mutableStateOf(false)
        private set

    var isKitsuConnected by mutableStateOf(false)
        private set

    var isShikimoriConnected by mutableStateOf(false)
        private set

    var isBangumiConnected by mutableStateOf(false)
        private set

    var isTmdbConnected by mutableStateOf(false)
        private set

    var tmdbUsername by mutableStateOf("")
        private set

    var isSimklSessionExpired by mutableStateOf(false)
        private set

    /** `E95`: sunucu tarafında düşen oturumlar (yeniden bağlanana kadar `true`). */
    var isAniListSessionExpired by mutableStateOf(false)
        private set
    var isMalSessionExpired by mutableStateOf(false)
        private set
    var isKitsuSessionExpired by mutableStateOf(false)
        private set
    var isShikimoriSessionExpired by mutableStateOf(false)
        private set
    var isBangumiSessionExpired by mutableStateOf(false)
        private set

    var isAniListImportRunning by mutableStateOf(false)
        private set

    var isMalImportRunning by mutableStateOf(false)
        private set

    var isSimklImportRunning by mutableStateOf(false)
        private set

    var isKitsuImportRunning by mutableStateOf(false)
        private set

    var isShikimoriImportRunning by mutableStateOf(false)
        private set

    var isBangumiImportRunning by mutableStateOf(false)
        private set

    /** "Tüm kaynakları yenile" akışı çalışıyor mu (tekrar tetiklemeyi engeller). */
    var isSyncAllListsRunning by mutableStateOf(false)
        private set

    var isTmdbImportRunning by mutableStateOf(false)
        private set

    var isCrossSyncRunning by mutableStateOf(false)
        private set

    var crossSyncState by mutableStateOf(com.kitsugi.animelist.model.CrossSyncProgressState())
        private set

    private var crossSyncJob: kotlinx.coroutines.Job? = null

    fun resetCrossSyncState() {
        if (!isCrossSyncRunning) {
            crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState()
        }
    }

    fun cancelCrossSync() {
        val job = crossSyncJob ?: return
        crossSyncState = crossSyncState.copy(
            currentStep = context.getString(R.string.cross_sync_vm_stopping),
            currentDetail = context.getString(R.string.cross_sync_vm_stop_detail_report),
            lastUpdatedAt = System.currentTimeMillis()
        )
        job.cancel(kotlinx.coroutines.CancellationException(context.getString(R.string.cross_sync_vm_cancel_reason)))
    }

    var onShowMessage: ((String) -> Unit)? = null

    init {
        refreshAuthState()
    }

    // ── OAuth WebView dialog state (eski haline restore) ─────────────────────
    // Harici tarayıcı yerine uygulama içi WebView kullanılacak (MAL basic auth popup fix)
    var oauthServiceToShow by mutableStateOf<String?>(null)
        private set

    fun showOAuthDialog(serviceName: String) {
        oauthServiceToShow = serviceName.lowercase()
    }

    /**
     * Giriş akışı GERÇEKTEN tamamlandığında (kod → jeton değişimi başarılı) çağrılır.
     *
     * Yalnızca bu durumda tek oturum doğrulaması yapılır: kullanıcı pencereyi iptal ederse
     * pencere yeniden açılmaz (web oturumu kurulmadığı için doğrulama da anlamsız olurdu).
     */
    fun noteOAuthSuccess(service: String?) {
        if (service.equals("mal", ignoreCase = true)) malOAuthSucceeded = true
    }

    fun dismissOAuthDialog() {
        val finishedService = oauthServiceToShow
        val succeeded = malOAuthSucceeded
        malOAuthSucceeded = false
        oauthServiceToShow = null
        refreshAuthState()
        // MAL'da tek girişin İKİ kimliği de bağlaması gerekir: OAuth jetonu (liste/senkron)
        // ve web oturumu (bildirimler). Giriş başarıyla kapandıktan hemen sonra doğrulanır;
        // web oturumu eksik ya da FARKLI bir hesaba aitse kullanıcıya tek dokunuşla tamamlama
        // akışı sunulur (bağlı hesap kesilmez). İptal edilen girişte hiçbir şey yapılmaz.
        if (succeeded && finishedService.equals("mal", ignoreCase = true) && isMalConnected) {
            finalizeMalUnifiedSession()
        }
    }

    fun refreshAuthState() {
        val state = ExternalAuthManager.getAuthState(context)
        isAniListConnected = state.isAniListConnected
        isMalConnected = state.isMalConnected
        isSimklConnected = state.isSimklConnected
        isKitsuConnected = state.isKitsuConnected
        isShikimoriConnected = state.isShikimoriConnected
        isBangumiConnected = state.isBangumiConnected
        isTmdbConnected = state.isTmdbConnected
        tmdbUsername = TmdbAuthStore.username(context)

        isSimklSessionExpired = ExternalAuthManager.isSessionExpired(context, "simkl")
        isAniListSessionExpired = ExternalAuthManager.isSessionExpired(context, "anilist")
        isMalSessionExpired = ExternalAuthManager.isSessionExpired(context, "mal")
        isKitsuSessionExpired = ExternalAuthManager.isSessionExpired(context, "kitsu")
        isShikimoriSessionExpired = ExternalAuthManager.isSessionExpired(context, "shikimori")
        isBangumiSessionExpired = ExternalAuthManager.isSessionExpired(context, "bangumi")
    }

    /** Starts MAL OAuth inside the app so its authenticated MAL cookies are shared with notifications. */
    fun startMalOAuthWebSession() {
        // Tek giriş akışı: doğrulama sonrası gerekirse yalnızca web çerezleri temizlenip
        // aynı akış bir kez daha açılır (kullanıcı bağlı hesabıyla web oturumu da açar).
        malWebReloginAttempted = false
        showOAuthDialog("mal")
    }

    // ── MAL tek oturum (API + web) durumu ──────────────────────────────────────

    /** Son doğrulanan MAL oturum durumu (null = henüz doğrulanmadı). */
    var malUnifiedStatus by mutableStateOf<com.kitsugi.animelist.data.auth.MalSessionCoordinator.Status?>(null)
        private set

    /** Doğrulama sürerken true (arayüz kısa bir gösterge gösterebilir). */
    var isMalSessionVerifying by mutableStateOf(false)
        private set

    /**
     * Tek oturum KURULDU sinyali (sayaç). Bildirimler ekranı bunu izleyip listeyi tazeler:
     * web oturumu bağlandıktan sonra kullanıcı elle yenilemek zorunda kalmasın.
     */
    var malSessionReadyToken by mutableStateOf(0)
        private set

    /** Tek giriş akışında web oturumu için ikinci geçiş yapıldı mı (sonsuz döngü koruması). */
    private var malWebReloginAttempted = false

    /** Son MAL giriş penceresi gerçekten başarıyla tamamlandı mı (iptal edilmedi mi)? */
    private var malOAuthSucceeded = false

    /**
     * Bildirimler ekranındaki "MAL oturumunu bağla / yenile" isteği.
     *
     * Web oturumu hazırsa hiçbir şey yapılmaz; değilse yalnızca MAL web çerezleri temizlenir
     * ve TEK bir giriş penceresi açılır (kullanıcı bağlı hesabıyla web oturumunu da açar).
     * Bağlı OAuth hesabı kesilmez — hedef: normal giriş ile bildirimler için giriş arasındaki
     * farkı kaldırmak.
     */
    fun ensureMalUnifiedSession(onNeedsLogin: () -> Unit = {}) {
        viewModelScope.launch {
            val status = runCatching {
                com.kitsugi.animelist.data.auth.MalSessionCoordinator.probe(context, verifyWeb = true)
            }.getOrNull()
            malUnifiedStatus = status
            if (status != null && status.webReady) {
                // Web oturumu zaten aynı hesapta: bildirim listesi kendini tazelesin
                // ("yeniden giriş yapın" kartında kilitli kalmasın).
                malSessionReadyToken++
                return@launch
            }
            // Web oturumu yok ya da farklı hesaba ait: yalnızca MAL çerezleri temizlenir ve
            // TEK bir giriş penceresi açılır — kullanıcı bağlı hesabıyla web oturumunu da açar.
            malWebReloginAttempted = false
            runCatching { com.kitsugi.animelist.data.auth.MalSessionCoordinator.clearWebSessionOnly() }
            onNeedsLogin()
            showOAuthDialog("mal")
        }
    }

    /**
     * Ayarlar sayfası açıldığında tek oturum durumunu pencere açmadan sorgular.
     * Yalnızca durumu görüntülemek içindir: giriş akışını tetiklemez.
     */
    fun probeMalUnifiedSession() {
        if (malUnifiedStatus != null || isMalSessionVerifying) return
        viewModelScope.launch {
            val status = runCatching {
                com.kitsugi.animelist.data.auth.MalSessionCoordinator.probe(context, verifyWeb = true)
            }.getOrNull()
            if (status != null) malUnifiedStatus = status
        }
    }

    /**
     * MAL girişi kapandıktan sonra iki kimliği doğrular.
     *
     * - Eşleşiyorsa: durum saklanır, kullanıcıya tek satırlık özet gösterilir.
     * - Eşleşmiyorsa/eksikse: yalnızca MAL web çerezleri silinir ve aynı giriş akışı BİR KEZ
     *   daha açılır. Böylece "normal giriş" ile "bildirimler için giriş" ayrımı kalkar: tek
     *   hesap, tek oturum, iki kimlik.
     */
    private fun finalizeMalUnifiedSession() {
        viewModelScope.launch {
            isMalSessionVerifying = true
            val status = try {
                com.kitsugi.animelist.data.auth.MalSessionCoordinator.verifyAndPersist(context)
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "MAL tek oturum doğrulanamadı: ${e.message}")
                null
            } finally {
                isMalSessionVerifying = false
            }
            malUnifiedStatus = status
            if (status == null) return@launch
            when (status.state) {
                com.kitsugi.animelist.data.auth.MalSessionCoordinator.State.LINKED -> {
                    onShowMessage?.invoke(
                        context.getString(R.string.mal_unified_session_linked, status.webUsername ?: "MAL")
                    )
                    // Bildirimler artık okunabilir: açık bildirim ekranı kendini tazeler.
                    malSessionReadyToken++
                }
                com.kitsugi.animelist.data.auth.MalSessionCoordinator.State.MISMATCHED -> {
                    if (!malWebReloginAttempted) {
                        malWebReloginAttempted = true
                        runCatching {
                            com.kitsugi.animelist.data.auth.MalSessionCoordinator.clearWebSessionOnly()
                        }
                        onShowMessage?.invoke(
                            context.getString(
                                R.string.mal_unified_session_mismatch_relogin,
                                status.oauthUsername ?: "MAL",
                                status.webUsername ?: "?"
                            )
                        )
                        showOAuthDialog("mal")
                    } else {
                        onShowMessage?.invoke(
                            context.getString(R.string.mal_unified_session_mismatch)
                        )
                    }
                }
                com.kitsugi.animelist.data.auth.MalSessionCoordinator.State.API_ONLY -> {
                    if (!malWebReloginAttempted) {
                        malWebReloginAttempted = true
                        runCatching {
                            com.kitsugi.animelist.data.auth.MalSessionCoordinator.clearWebSessionOnly()
                        }
                        onShowMessage?.invoke(context.getString(R.string.mal_unified_session_web_missing))
                        showOAuthDialog("mal")
                    } else {
                        onShowMessage?.invoke(context.getString(R.string.mal_unified_session_web_missing))
                    }
                }
                else -> Unit
            }
        }
    }

    /**
     * Re-establishes MAL's website session without disconnecting the stored MAL account.
     * Clearing only MAL's cookies forces the in-app OAuth WebView to authenticate a fresh
     * website session; notifications still verify that session against the OAuth account.
     */
    fun reconnectMalWebSession() {
        viewModelScope.launch {
            runCatching { com.kitsugi.animelist.data.auth.MalSessionCoordinator.clearWebSessionOnly() }
            // Tek giriş akışı: bu pencerede kullanıcı MAL'a giriş yapınca hem jeton hem web
            // oturumu AYNI hesaba kurulur; doğrulama dialog kapanınca yapılır.
            malWebReloginAttempted = false
            showOAuthDialog("mal")
        }
    }

    /** Uses the in-app OAuth WebView for MAL, AniList, and Simkl. */
    fun startExternalAuth(serviceName: String) {
        if (serviceName.equals("mal", ignoreCase = true)) {
            startMalOAuthWebSession()
            return
        }
        showOAuthDialog(serviceName)
    }

    /** Harici tarayıcı fallback — WebView başarısız olursa */
    fun startExternalAuthExternal(serviceName: String) {
        ExternalAuthManager.startAuthentication(
            context = context,
            serviceName = serviceName,
            onError = { message ->
                onShowMessage?.invoke(message)
            }
        )
    }

    fun disconnectExternalAccount(serviceName: String) {
        viewModelScope.launch {
            ExternalAuthManager.disconnectAccountCoordinated(
                context = context,
                serviceName = serviceName
            )
            val settings = SettingsDataStore(context)
            when (serviceName) {
                "anilist" -> {
                    settings.clearAniListProfileInfo()
                    com.kitsugi.animelist.data.auth.AniListSyncManager.clearSessionCaches()
                }
                "mal" -> {
                    settings.clearMalProfileInfo()
                    // Kaydedilen web kimliği de unutulur: bağlı hesap kalmadığı için ekranlarda
                    // eski oturum sahibi adı görünmesin.
                    com.kitsugi.animelist.data.auth.MalSessionCoordinator.forgetWebIdentity(context)
                    malUnifiedStatus = null
                }
                "simkl" -> settings.clearSimklProfileInfo()
                "kitsu" -> settings.clearKitsuProfileInfo()
                "shikimori" -> settings.clearShikimoriProfileInfo()
                "bangumi" -> settings.clearBangumiProfileInfo()
                "tmdb" -> Unit // Session/profile are owned by TmdbAuthStore.
            }
            // Hesap değişti: o kaynağın yenileme defteri sıfırlanır. Aksi halde yeni hesap
            // için "az önce senkron oldu" denip liste çekilmeden atlanabilirdi.
            SourceListSynchronizer.resetState(
                context,
                when (serviceName) {
                    "anilist" -> SourceListSynchronizer.ANILIST
                    "mal" -> SourceListSynchronizer.MAL
                    "simkl" -> SourceListSynchronizer.SIMKL
                    "kitsu" -> SourceListSynchronizer.KITSU
                    "shikimori" -> SourceListSynchronizer.SHIKIMORI
                    "bangumi" -> SourceListSynchronizer.BANGUMI
                    else -> serviceName
                }
            )

            refreshAuthState()
            com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)

            // B1.12: Clear the TV launcher channel and watch-next recommendation when disconnecting
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context, TvSyncEntryPoint::class.java
                )
                entryPoint.channelSyncService().clearAll()
                // Reconcile again from DB to show remaining active sources if any
                entryPoint.channelSyncService().reconcileFromDatabase()
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "Failed to clear TV channel during disconnect", e)
            }
        }

        val displayName = when (serviceName) {
            "anilist" -> "AniList"
            "simkl" -> "Simkl"
            "kitsu" -> "Kitsu"
            "shikimori" -> "Shikimori"
            "bangumi" -> "Bangumi"
            "tmdb" -> "TMDB"
            else -> "MyAnimeList"
        }
        onShowMessage?.invoke(context.getString(R.string.account_sync_disconnected, displayName))
    }

    /**
     * Full TMDB account refresh: Watchlist + favorites + ratings. The native account rows
     * live in their own source so the TMDB tab is real, while the combined tab groups them
     * with verified siblings. Progress/status/notes are preserved by the snapshot merger.
     */
    fun importTmdbWatchlist(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) {
        val sessionId = TmdbAuthStore.sessionId(context)
        val accountId = TmdbAuthStore.accountId(context)
        if (sessionId.isNullOrBlank() || accountId == null) {
            onShowMessage?.invoke(context.getString(R.string.account_sync_token_missing, "TMDB"))
            return
        }
        if (isTmdbImportRunning) {
            onShowMessage?.invoke(context.getString(R.string.account_sync_import_running, "TMDB"))
            return
        }
        isTmdbImportRunning = true
        onShowMessage?.invoke(context.getString(R.string.account_sync_import_fetching, "TMDB"))
        viewModelScope.launch(Dispatchers.IO) {
            runSyncCatching {
                com.kitsugi.animelist.data.auth.TmdbAccountSyncCoordinator.refresh(
                    context = context,
                    repository = repository,
                    fallbackEntries = currentEntries
                )
            }.onSuccess { snapshot ->
                onShowMessage?.invoke(
                    context.getString(R.string.account_sync_import_success, snapshot.entries.size, "TMDB")
                )
            }.onFailure { error ->
                onShowMessage?.invoke(
                    error.message ?: context.getString(R.string.account_sync_import_failed, "TMDB")
                )
            }
            isTmdbImportRunning = false
        }
    }

    /**
     * AniList listesini yeniler.
     *
     * @param mode [ListRefreshMode.USER_REFRESH] (varsayılan) tazelik/TTL politikasına
     *        uyar; [ListRefreshMode.USER_FORCE] tam listeyi baştan çeker.
     */
    fun importAniListAnimeList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            announceListRefresh(
                displayName = "AniList",
                source = SourceListSynchronizer.ANILIST,
                outcome = refreshAniListList(repository, mode)
            )
        }
    }

    fun importMalAnimeList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            announceListRefresh(
                displayName = "MyAnimeList",
                source = SourceListSynchronizer.MAL,
                outcome = refreshMalList(repository, mode)
            )
        }
    }

    /**
     * Bağlı TÜM kaynakları sırayla yeniler ("Tümü" sekmesindeki yenile).
     *
     * Eski davranış altı içe aktarmayı AYNI ANDA başlatıyordu: her kaynak kendi
     * sayfalama döngüsünü paralel koşuyor, ağ ve CPU doyuyor, liste ardı ardına
     * birkaç kez yeniden yayınlanıyordu. Artık kaynaklar arasında kısa bir bekleme
     * vardır (staggering — Mihon/Kotatsu'nun kitaplık güncellemesindeki yaklaşım) ve
     * her kaynak kendi TTL'ine göre tam/fark/atla kararını verir.
     */
    fun syncAllLists(
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) {
        if (isSyncAllListsRunning) {
            onShowMessage?.invoke("Tüm kaynaklar zaten yenileniyor...")
            return
        }
        isSyncAllListsRunning = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val refreshers: List<Pair<String, suspend () -> ListRefreshOutcome?>> = listOf(
                    "AniList" to { refreshAniListList(repository, mode) },
                    "MyAnimeList" to { refreshMalList(repository, mode) },
                    "Simkl" to { refreshSimklList(repository, mode) },
                    "Kitsu" to { refreshKitsuList(repository, mode) },
                    "Shikimori" to { refreshShikimoriList(repository, mode) },
                    "Bangumi" to { refreshBangumiList(repository, mode) }
                )

                var connectedCount = 0
                val changed = mutableListOf<String>()
                var unchanged = 0
                for ((index, entry) in refreshers.withIndex()) {
                    if (index > 0) kotlinx.coroutines.delay(SYNC_ALL_STAGGER_MS)
                    val outcome = runSyncCatching { entry.second.invoke() }.getOrNull() ?: continue
                    connectedCount++
                    val stats = (outcome as? ListRefreshOutcome.Completed)?.stats
                    if (stats != null && stats.hasChanges) {
                        changed += "${entry.first} +${stats.added}/~${stats.updated}"
                    } else {
                        unchanged++
                    }
                }

                val message = when {
                    connectedCount == 0 ->
                        "Birleşik listeyi yenilemek için en az bir kaynağı bağla"
                    changed.isEmpty() ->
                        "✅ Tüm kaynaklar güncel ($unchanged kaynak denetlendi)"
                    else ->
                        "🔄 Liste yenilendi: " + changed.joinToString(" • ")
                }
                onShowMessage?.invoke(message)
            } finally {
                isSyncAllListsRunning = false
            }
        }
    }

    private class UnifiedSyncItem(
        var aniList: MediaEntry? = null,
        var mal: MediaEntry? = null,
        var simkl: MediaEntry? = null,
        var tmdb: MediaEntry? = null,
        var kitsu: MediaEntry? = null,
        var shikimori: MediaEntry? = null,
        var bangumi: MediaEntry? = null,
        var identityReviewRequired: Boolean = false,
        var identityReviewDetails: String? = null,
        var identityReviewEntryIds: Set<Int> = emptySet()
    ) {
        val candidates: List<MediaEntry>
            get() = listOfNotNull(aniList, mal, simkl, tmdb, kitsu, shikimori, bangumi)

        val actionableCandidates: List<MediaEntry>
            get() = candidates.filterNot { it.id in identityReviewEntryIds }

        fun isIdentityReviewed(entry: MediaEntry): Boolean = entry.id in identityReviewEntryIds

        val primaryTitle: String
            get() = candidates.firstOrNull()?.titleEnglish?.takeIf { it.isNotBlank() }
                ?: candidates.firstOrNull()?.title ?: KitsugiStrings.get(R.string.cross_sync_vm_unknown_media)

        val mediaType: MediaType
            get() = candidates.firstOrNull()?.type ?: MediaType.Anime
    }

    private fun Int.isRealMalId(): Boolean = this in 1..99_999_999

    @Suppress("UNUSED_PARAMETER")
    private suspend fun resolveRealMalId(entry: MediaEntry, aniListToken: String?): Int? {
        val isAnimeOrManga = entry.type == MediaType.Anime || entry.type == MediaType.Manga
        if (!isAnimeOrManga) return null
        // Kaynak-duyarlı okuma: `malId` alanı film/dizi satırlarında TMDB kimliği, Shikimori
        // satırlarında ham target_id taşıyabilir — salt aralık kontrolü bunları MAL ID sanar.
        val existing = com.kitsugi.animelist.model.MediaIdentity.realMalId(entry)
        if (existing != null) return existing

        // Paketlenmiş Bangumi→MAL eşlemesi yalnız adaydır; MAL kataloğunda tür/başlık/yıl
        // doğrulaması geçmeden gerçek kimlik olarak kullanılmaz. Çelişkili çiftler elenmiştir.
        if (entry.type == MediaType.Anime) {
            val bangumiSubjectId = com.kitsugi.animelist.data.remote.BangumiIdNamespace
                .rawIdFromStable(entry.malId)
            val mappedMalId = bangumiSubjectId?.let {
                com.kitsugi.animelist.data.remote.BangumiMalCrosswalk.malIdForBangumi(context, it)
            }
            if (mappedMalId != null) {
                val mappedVerdict = com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.verifyMalId(
                    malId = mappedMalId,
                    mediaType = entry.type,
                    localTitles = com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.aliasesOf(entry),
                    localYear = entry.year
                )
                if (mappedVerdict is com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict.Verified) {
                    return mappedMalId
                }
            }
        }

        // Çapraz eşitlemenin daha önce öğrendiği eşlemeler (ağ istemeden): canlı eşitleme
        // bir yapımın MAL/AniList/Kitsu/Bangumi kimliklerini bir kez çözdüğünde kalıcıya yazar.
        runSyncCatching {
            com.kitsugi.animelist.data.auth.CrossProviderIdentityResolver
                .resolve(context, entry, allowNetwork = false).malId
        }.getOrNull()?.takeIf { it.isRealMalId() }?.let { return it }

        val rawAniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
            entry.malId - 100_000_000
        } else null

        val rawKitsuId = entry.malId?.takeIf { it in 300_000_001..399_999_999 }?.minus(300_000_000)

        // Not: AniList içe aktarımı `idMal` alanını zaten getiriyor; sentetik (100M+) kimlik taşıyan bir
        // AniList kaydı için tekrar Media(idMal) sorgusu atmak hem sonuçsuz hem de 30 istek/dk kotasını
        // tüketip gerçek yazma isteklerinin 429 almasına yol açıyordu. Bu adım bilinçli olarak kaldırıldı.

        // GÜVENLİK: ARM sorgusuna TMDB kimliği bilinçli olarak verilmez. TMDB'de film ve dizi kimlik uzayları
        // çakışır ve tek bir TMDB dizi kimliği bir franchise'ın tüm sezonlarını kapsar; ARM bu durumda rastgele
        // bir sezonun/yanlış bir yapımın MAL ID'sini döndürüyordu. Bu, kullanıcının hiç eklemediği animelerin
        // hesaplarına yazılmasının başlıca nedeniydi. Yalnızca birebir (1:1) kimlikler (AniList/Kitsu) kullanılır.
        val armMal = if (rawAniListId != null || rawKitsuId != null) {
            runSyncCatching {
                com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                    malId = null,
                    aniListId = rawAniListId,
                    tmdbId = null,
                    mediaType = entry.type,
                    kitsuId = rawKitsuId
                ).malId
            }.getOrNull()
        } else null
        if (armMal != null && armMal.isRealMalId()) return armMal

        // Simkl (150M), Shikimori (400M) ve Bangumi (500M) alanındaki satırlar yukarıdaki
        // zincirde hiç çözülemez; altı kaynaklık kimlik köprüsü onları da bilir.
        val fromCrossBridge = runSyncCatching {
            com.kitsugi.animelist.data.auth.CrossProviderIdentityResolver
                .resolve(context, entry).malId
        }.getOrNull()
        if (fromCrossBridge != null && fromCrossBridge.isRealMalId()) return fromCrossBridge

        val searchTitle = entry.titleEnglish?.takeIf { it.isNotBlank() }
            ?: entry.title.takeIf { it.isNotBlank() }
            ?: entry.titleJapanese?.takeIf { it.isNotBlank() }
        if (!searchTitle.isNullOrBlank()) {
            val jikanResults = runSyncCatching {
                // Jikan kota kapısı (JikanGateway) searchMALOnly içinde uygulanır.
                com.kitsugi.animelist.data.remote.JikanSearchClient().searchMALOnly(
                    query = searchTitle,
                    mediaType = entry.type
                )
            }.getOrNull()
            val candidateTitles = listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
                .map { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                .filter { it.isNotBlank() }
            val jikanMal = jikanResults?.filter { res ->
                res.type == entry.type && (entry.year == null || res.year == entry.year) &&
                    listOfNotNull(res.title, res.titleEnglish, res.titleJapanese).any { resTitle ->
                        val norm = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(resTitle)
                        candidateTitles.any { it == norm }
                    }
            }?.singleOrNull()?.let { res -> (res.realMalId ?: res.malId).takeIf { it.isRealMalId() } }
            if (jikanMal != null) return jikanMal
        }

        return null
    }

    fun syncPlatforms(
        repository: MediaEntryRepository
    ) {
        if (isCrossSyncRunning) {
            onShowMessage?.invoke(context.getString(R.string.cross_sync_vm_already_running))
            return
        }

        isCrossSyncRunning = true
        val syncStartedAt = System.currentTimeMillis()
        crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState(
            isRunning = true,
            currentStep = context.getString(R.string.cross_sync_vm_scanning_accounts),
            currentDetail = context.getString(R.string.cross_sync_vm_checking_accounts),
            progressPhaseStartedAt = syncStartedAt,
            lastUpdatedAt = syncStartedAt,
            startedAt = syncStartedAt
        )

        crossSyncJob = viewModelScope.launch(Dispatchers.IO) {
            val statsMap = mutableMapOf<String, com.kitsugi.animelist.model.CrossPlatformStats>()
            val recentLogs = mutableListOf<com.kitsugi.animelist.model.CrossSyncLogEntry>()
            val reportLogs = mutableListOf<com.kitsugi.animelist.model.CrossSyncLogEntry>()
            var issueCount = 0

            fun logEvent(
                platform: String,
                message: String,
                isAddition: Boolean = false,
                isUpdate: Boolean = false,
                isError: Boolean = false,
                isWarning: Boolean = false,
                details: String? = null,
                includeInLiveLog: Boolean = true,
                errorCode: com.kitsugi.animelist.data.platform.PlatformErrorCode? = null
            ) {
                // Hata/uyarı satırlarına, çağrı yerlerini tek tek değiştirmeye gerek kalmadan
                // kaynağa özgü hata kodunu iliştir. Kod ve açıklaması UI'da dil dosyasından çözülür.
                val resolvedCode = if (isError || isWarning) {
                    val candidate = errorCode ?: com.kitsugi.animelist.data.platform.PlatformErrorCatalog
                        .classifyMessage(platform, message, details)
                    when {
                        // Sınıflandırılamayan bir uyarıya yanıltıcı kod iliştirmeyelim…
                        candidate == com.kitsugi.animelist.data.platform.PlatformErrorCode.COM_1020 && !isError -> null
                        // …ama hatalar kodsuz kalmasın; "bilinmeyen" de bir sonuçtur.
                        else -> candidate
                    }
                } else null
                val entry = com.kitsugi.animelist.model.CrossSyncLogEntry(
                    platform = platform,
                    message = message,
                    isAddition = isAddition,
                    isUpdate = isUpdate,
                    isError = isError,
                    isWarning = isWarning,
                    details = details,
                    errorCode = resolvedCode?.code
                )
                reportLogs.add(entry)
                if (isError || isWarning) issueCount++
                if (includeInLiveLog) {
                    recentLogs.add(entry)
                    if (recentLogs.size > 500) recentLogs.removeAt(0)
                }
            }

            fun updateProgress(
                step: String,
                detail: String = "",
                processed: Int = crossSyncState.processedItems,
                total: Int = crossSyncState.totalItems,
                unit: String = context.getString(R.string.cross_sync_vm_item_unit)
            ) {
                val now = System.currentTimeMillis()
                val progressPhaseChanged = unit != crossSyncState.progressUnit || total != crossSyncState.totalItems
                crossSyncState = crossSyncState.copy(
                    isRunning = true,
                    isCompleted = false,
                    currentStep = step,
                    currentDetail = detail,
                    processedItems = processed,
                    totalItems = total,
                    progressUnit = unit,
                    progressPhaseStartedAt = if (progressPhaseChanged || crossSyncState.progressPhaseStartedAt == null) {
                        now
                    } else {
                        crossSyncState.progressPhaseStartedAt
                    },
                    lastUpdatedAt = now,
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    platformStats = statsMap.toMap(),
                    logs = recentLogs.toList(),
                    startedAt = syncStartedAt
                )
            }

            try {
            val syncSettings = SettingsDataStore(context).settingsFlow.first()

            val aniListToken = ExternalAuthManager.getAniListToken(context)
            val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
            val simklToken = ExternalAuthManager.getSimklToken(context)
            val kitsuToken = ExternalAuthManager.getOrRefreshKitsuToken(context)
            val kitsuUserId = ExternalAuthManager.getKitsuUserId(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            var shikimoriUserId = ExternalAuthManager.getShikimoriUserId(context)
            if (!shikimoriToken.isNullOrBlank() && shikimoriUserId == null &&
                ExternalAuthManager.ensureShikimoriUserResolved(context)
            ) {
                shikimoriUserId = ExternalAuthManager.getShikimoriUserId(context)
            }
            val bangumiToken = if (syncSettings.syncEnabledBangumi) BangumiAuthStore.getValidToken(context) else null
            val tmdbSessionId = if (syncSettings.syncEnabledTmdb) TmdbAuthStore.sessionId(context) else null
            val tmdbAccountId = if (syncSettings.syncEnabledTmdb) TmdbAuthStore.accountId(context) else null

            val isAniList = syncSettings.syncEnabledAnilist && !aniListToken.isNullOrBlank()
            val isMal = syncSettings.syncEnabledMal && !malToken.isNullOrBlank()
            val isSimkl = syncSettings.syncEnabledSimkl && !simklToken.isNullOrBlank()
            val isKitsu = syncSettings.syncEnabledKitsu && !kitsuToken.isNullOrBlank() && !kitsuUserId.isNullOrBlank()
            val isShikimori = syncSettings.syncEnabledShikimori && !shikimoriToken.isNullOrBlank() && shikimoriUserId != null
            val isBangumi = syncSettings.syncEnabledBangumi && !bangumiToken.isNullOrBlank()
            val isTmdb = syncSettings.syncEnabledTmdb && !tmdbSessionId.isNullOrBlank() && tmdbAccountId != null

            val connectedPlatforms = mutableListOf<String>()
            if (isAniList) connectedPlatforms.add("AniList")
            if (isMal) connectedPlatforms.add("MyAnimeList")
            if (isSimkl) connectedPlatforms.add("Simkl")
            if (isKitsu) connectedPlatforms.add("Kitsu")
            if (isShikimori) connectedPlatforms.add("Shikimori")
            if (isBangumi) connectedPlatforms.add("Bangumi")
            if (isTmdb) connectedPlatforms.add("TMDB")

            if (connectedPlatforms.size < 2) {
                val message = context.getString(R.string.cross_sync_vm_minimum_accounts)
                logEvent(context.getString(R.string.cross_sync_log_category_sync), message, isError = true)
                onShowMessage?.invoke(message)
                crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState(
                    isRunning = false,
                    currentStep = context.getString(R.string.cross_sync_vm_start_failed),
                    currentDetail = message,
                    errorMessage = context.getString(R.string.cross_sync_vm_minimum_accounts_short),
                    logs = recentLogs.toList(),
                    startedAt = syncStartedAt
                )
                return@launch
            }

            connectedPlatforms.forEach { name ->
                statsMap[name] = com.kitsugi.animelist.model.CrossPlatformStats(platformName = name)
            }
            updateProgress(context.getString(R.string.cross_sync_vm_scanning_accounts), context.getString(R.string.cross_sync_vm_fetching_libraries))

            val settingsDataStore = SettingsDataStore(context)
            runSyncCatching {
                val showAdult = settingsDataStore.settingsFlow.first().showAdultContent

                // ── FAZ 1: Kütüphane Listelerini Çek ──────────────────────────────
                val aniListEntries = if (isAniList) {
                    updateProgress(context.getString(R.string.cross_sync_vm_fetching_platform_list, "AniList"), context.getString(R.string.cross_sync_vm_reading_library))
                    val list = AniListImportManager.fetchAllLists(aniListToken!!)
                    statsMap["AniList"] = statsMap["AniList"]!!.copy(initialCount = list.size)
                    logEvent("AniList", context.getString(R.string.cross_sync_log_imported_entries, "AniList", list.size))
                    list
                } else emptyList()

                val malEntries = if (isMal) {
                    updateProgress(context.getString(R.string.cross_sync_vm_fetching_platform_list, "MyAnimeList"), context.getString(R.string.cross_sync_vm_reading_library))
                    val list = MalImportManager.fetchAllLists(malToken!!, showAdult)
                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.copy(initialCount = list.size)
                    logEvent("MyAnimeList", context.getString(R.string.cross_sync_log_imported_entries, "MyAnimeList", list.size))
                    list
                } else emptyList()

                val simklEntries = if (isSimkl) {
                    updateProgress(context.getString(R.string.cross_sync_vm_fetching_platform_list, "Simkl"), context.getString(R.string.cross_sync_vm_reading_simkl_library))
                    val list = SimklImportManager.fetchAllLists(simklToken!!)
                    statsMap["Simkl"] = statsMap["Simkl"]!!.copy(initialCount = list.size)
                    logEvent("Simkl", context.getString(R.string.cross_sync_log_imported_entries, "Simkl", list.size))
                    list
                } else emptyList()

                val kitsuEntries = if (isKitsu) {
                    updateProgress(context.getString(R.string.cross_sync_vm_fetching_platform_list, "Kitsu"), context.getString(R.string.cross_sync_vm_reading_library))
                    val list = KitsuImportManager.fetchAllLists(context, kitsuToken!!, kitsuUserId!!)
                    statsMap["Kitsu"] = statsMap["Kitsu"]!!.copy(initialCount = list.size)
                    logEvent("Kitsu", context.getString(R.string.cross_sync_log_imported_entries, "Kitsu", list.size))
                    list
                } else emptyList()

                val shikimoriEntries = if (isShikimori) {
                    updateProgress(context.getString(R.string.cross_sync_vm_fetching_platform_list, "Shikimori"), context.getString(R.string.cross_sync_vm_reading_library))
                    val list = ShikimoriImportManager.fetchAllLists(context, shikimoriToken!!, shikimoriUserId!!)
                    statsMap["Shikimori"] = statsMap["Shikimori"]!!.copy(initialCount = list.size)
                    logEvent("Shikimori", context.getString(R.string.cross_sync_log_imported_entries, "Shikimori", list.size))
                    list
                } else emptyList()

                val bangumiEntries = if (isBangumi) {
                    updateProgress(context.getString(R.string.cross_sync_vm_fetching_platform_list, "Bangumi"), context.getString(R.string.cross_sync_vm_reading_library))
                    val list = BangumiImportManager.fetchAllLists(context, bangumiToken!!)
                    statsMap["Bangumi"] = statsMap["Bangumi"]!!.copy(initialCount = list.size)
                    logEvent("Bangumi", context.getString(R.string.cross_sync_log_imported_entries, "Bangumi", list.size))
                    list
                } else emptyList()

                val tmdbEntries = if (isTmdb) {
                    updateProgress(context.getString(R.string.cross_sync_vm_fetching_platform_list, "TMDB"), context.getString(R.string.cross_sync_vm_reading_library))
                    val list = TmdbAccountClient.fetchAccountSnapshot(tmdbSessionId!!, tmdbAccountId!!).entries
                    statsMap["TMDB"] = statsMap["TMDB"]!!.copy(initialCount = list.size)
                    logEvent("TMDB", context.getString(R.string.cross_sync_log_imported_entries, "TMDB", list.size))
                    list
                } else emptyList()

                // ── FAZ 2: Çoklu İndeks ile Unified Eşleştirme ────────────────────
                val sourceEntryTotal = aniListEntries.size + malEntries.size + simklEntries.size +
                    kitsuEntries.size + shikimoriEntries.size + bangumiEntries.size + tmdbEntries.size
                updateProgress(
                    context.getString(R.string.cross_sync_vm_matching_entries),
                    if (sourceEntryTotal > 0) context.getString(R.string.cross_sync_vm_scan_source_entries, sourceEntryTotal) else context.getString(R.string.cross_sync_vm_no_matchable_entries),
                    processed = 0,
                    total = sourceEntryTotal,
                    unit = context.getString(R.string.cross_sync_vm_source_entry_unit)
                )

                val rawBatches = buildList {
                    add(com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.Slot.ANILIST to aniListEntries)
                    add(com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.Slot.MAL to malEntries)
                    add(com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.Slot.SIMKL to simklEntries)
                    add(com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.Slot.KITSU to kitsuEntries)
                    add(com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.Slot.SHIKIMORI to shikimoriEntries)
                    add(com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.Slot.BANGUMI to bangumiEntries)
                    if (isTmdb) {
                        add(com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.Slot.TMDB to tmdbEntries)
                    }
                }
                val indexedBatches = com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.assignClusterRowIds(rawBatches)
                val allIndexedEntries = indexedBatches.flatMap { it.second }
                val identityExtras = com.kitsugi.animelist.data.local.MyListIdentityCrosswalk.build(context, allIndexedEntries)

                fun describeIdentityCandidate(entry: MediaEntry): String = entry.crossSyncIdentityDiagnostic(context).trimEnd()

                val clusterSession = com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.Session(
                    identityExtras = identityExtras
                ) { diag ->
                    when (diag) {
                        is com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.DiagnosticEvent.SameTitleIdConflict -> {
                            logEvent(
                                context.getString(R.string.cross_sync_log_category_matching),
                                context.getString(R.string.cross_sync_log_same_title_conflict, diag.entry.title),
                                details = buildString {
                                    appendLine(context.getString(R.string.cross_sync_detail_source_identity_conflict))
                                    appendLine(context.getString(R.string.cross_sync_detail_incoming_record))
                                    appendLine(describeIdentityCandidate(diag.entry))
                                    appendLine(context.getString(R.string.cross_sync_detail_existing_same_title))
                                    diag.conflictingCandidates.forEach { candidate ->
                                        appendLine(describeIdentityCandidate(candidate))
                                    }
                                    appendLine(
                                        context.getString(
                                            R.string.cross_sync_detail_different_identity_fields,
                                            diag.conflictKeys.joinToString().ifBlank { KitsugiStrings.get(R.string.cross_sync_diag_no_value) }
                                        )
                                    )
                                },
                                includeInLiveLog = false
                            )
                        }
                        is com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.DiagnosticEvent.SharedIdentityMerged -> {
                            logEvent(
                                context.getString(R.string.cross_sync_log_category_matching),
                                context.getString(R.string.cross_sync_log_shared_identity_merged, diag.entry.title),
                                details = buildString {
                                    appendLine(context.getString(R.string.cross_sync_detail_shared_ids, diag.sharedIds.joinToString()))
                                    appendLine(context.getString(R.string.cross_sync_detail_incoming_record))
                                    appendLine(describeIdentityCandidate(diag.entry))
                                    appendLine(context.getString(R.string.cross_sync_detail_matching_records))
                                    diag.bridgedAgainst.forEach { appendLine(describeIdentityCandidate(it)) }
                                },
                                includeInLiveLog = false
                            )
                        }
                        is com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.DiagnosticEvent.DisambiguatedFromMultipleCandidates -> {
                            val details = buildString {
                                appendLine(
                                    context.getString(
                                        R.string.cross_sync_detail_multiple_candidates,
                                        context.getString(
                                            if (diag.byUniqueSharedId) R.string.cross_sync_detail_unique_shared_id
                                            else R.string.cross_sync_detail_exact_title
                                        )
                                    )
                                )
                                appendLine(context.getString(R.string.cross_sync_detail_incoming_record))
                                appendLine(describeIdentityCandidate(diag.entry))
                                appendLine(context.getString(R.string.cross_sync_detail_selected_candidate, diag.selectedGroupIndex))
                                appendLine(context.getString(R.string.cross_sync_detail_candidate_groups))
                                diag.candidateGroups.forEachIndexed { index, group ->
                                    appendLine(context.getString(R.string.cross_sync_detail_candidate, index + 1))
                                    group.forEach { appendLine(describeIdentityCandidate(it)) }
                                }
                            }
                            logEvent(
                                context.getString(R.string.cross_sync_log_category_matching),
                                context.getString(
                                    R.string.cross_sync_detail_multiple_candidates,
                                    context.getString(
                                        if (diag.byUniqueSharedId) R.string.cross_sync_detail_unique_shared_id
                                        else R.string.cross_sync_detail_exact_title
                                    )
                                ) + " " + diag.entry.title,
                                isWarning = true,
                                details = details
                            )
                        }
                        is com.kitsugi.animelist.data.auth.CrossSyncClusterEngine.DiagnosticEvent.MarkedIdentityReview -> Unit
                    }
                }

                var sourceEntriesProcessed = 0
                for ((slot, entries) in indexedBatches) {
                    for (entry in entries) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        clusterSession.addEntry(slot, entry)
                        sourceEntriesProcessed++
                        if (sourceEntriesProcessed % 25 == 0 || sourceEntriesProcessed == sourceEntryTotal) {
                            updateProgress(
                                step = context.getString(R.string.cross_sync_vm_matching_entries),
                                detail = context.getString(
                                    R.string.cross_sync_vm_source_scan_progress,
                                    sourceEntriesProcessed,
                                    sourceEntryTotal,
                                    clusterSession.groupsSnapshot().size
                                ),
                                processed = sourceEntriesProcessed,
                                total = sourceEntryTotal,
                                unit = context.getString(R.string.cross_sync_vm_source_entry_unit)
                            )
                            kotlinx.coroutines.yield()
                        }
                    }
                }

                val unifiedItems = clusterSession.groupsSnapshot().map { group ->
                    UnifiedSyncItem(
                        aniList = group.aniList,
                        mal = group.mal,
                        simkl = group.simkl,
                        tmdb = group.tmdb,
                        kitsu = group.kitsu,
                        shikimori = group.shikimori,
                        bangumi = group.bangumi,
                        identityReviewRequired = group.identityReviewRequired,
                        identityReviewDetails = group.identityReviewDetails,
                        identityReviewEntryIds = group.identityReviewEntryIds
                    )
                }

                logEvent(context.getString(R.string.cross_sync_log_category_analysis), context.getString(R.string.cross_sync_log_match_summary, unifiedItems.size))
                updateProgress(
                    context.getString(R.string.cross_sync_vm_starting),
                    context.getString(R.string.cross_sync_vm_planned_items, unifiedItems.size),
                    processed = 0,
                    total = unifiedItems.size,
                    unit = context.getString(R.string.cross_sync_vm_item_unit)
                )

                val simklEntriesToSync = mutableListOf<MediaEntry>()
                fun countSafetySkip(platform: String) {
                    statsMap[platform]?.let { statsMap[platform] = it.copy(skippedCount = it.skippedCount + 1) }
                }
                // Kayıt bazlı "atlandı" olayları artık tek tek uyarı üretmez; başlıklar toplanır ve
                // eşitleme sonunda platform başına tek bir özet uyarı yazılır (ayrıntılarda tam liste).
                val skippedTitlesByReason = linkedMapOf<String, MutableList<String>>()
                fun recordSkip(platform: String, reasonKey: String, title: String, message: String, details: String) {
                    statsMap[platform]?.let { statsMap[platform] = it.copy(skippedCount = it.skippedCount + 1) }
                    skippedTitlesByReason.getOrPut("$platform|$reasonKey") { mutableListOf() }.add(title)
                    logEvent(platform, message, details = details, includeInLiveLog = false)
                }
                fun flushSkipSummaries() {
                    skippedTitlesByReason.forEach { (key, titles) ->
                        if (titles.isEmpty()) return@forEach
                        val platform = key.substringBefore('|')
                        val reason = key.substringAfter('|')
                        logEvent(
                            platform,
                            context.getString(R.string.cross_sync_log_skipped_summary, platform, titles.size, reason),
                            isWarning = true,
                            details = context.getString(
                                R.string.cross_sync_log_skipped_entries,
                                titles.size,
                                titles.joinToString("\n") { "• $it" }
                            )
                        )
                    }
                    skippedTitlesByReason.clear()
                }

                // ── FAZ 3: Eksikleri Tamamlama ve Alan Senkronizasyonu (Asla Silme Yok) ──
                for ((index, item) in unifiedItems.withIndex()) {
                  try {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val allCandidates = item.candidates
                    val candidates = item.actionableCandidates
                    if (allCandidates.isEmpty()) {
                        if ((index + 1) % 25 == 0) kotlinx.coroutines.yield()
                        continue
                    }
                    if ((index + 1) % 10 == 1) {
                        updateProgress(
                            step = context.getString(R.string.cross_sync_vm_syncing_item, item.primaryTitle),
                            detail = context.getString(R.string.cross_sync_vm_processing_items, index + 1, unifiedItems.size),
                            processed = index,
                            total = unifiedItems.size
                        )
                    }
                    if (item.identityReviewRequired) {
                        val title = allCandidates.firstOrNull()?.title ?: context.getString(R.string.cross_sync_vm_unknown_media)
                        logEvent(
                            context.getString(R.string.cross_sync_log_category_matching),
                            context.getString(R.string.cross_sync_log_identity_review, title),
                            isWarning = true,
                            details = item.identityReviewDetails ?: allCandidates.joinToString("\n\n") { it.crossSyncIdentityDiagnostic(context) }
                        )
                        // Güvenli çekirdek yoksa eski davranış: grup tamamen güvenlikte kalır.
                        // Çekirdek varsa yalnız identityReviewEntryIds içindeki hedefler atlanır;
                        // ilgisiz/şüpheli satır bütün platformların güncellemesini engellemez.
                        if (candidates.isEmpty()) {
                            val reviewType = allCandidates.first().type
                            val reviewIsAnimeOrManga = reviewType == MediaType.Anime || reviewType == MediaType.Manga
                            if (isAniList && item.aniList == null && reviewIsAnimeOrManga) countSafetySkip("AniList")
                            if (isMal && item.mal == null && reviewIsAnimeOrManga) countSafetySkip("MyAnimeList")
                            if (isSimkl && item.simkl == null && reviewType != MediaType.Manga) countSafetySkip("Simkl")
                            if (isKitsu && item.kitsu == null && reviewIsAnimeOrManga) countSafetySkip("Kitsu")
                            if (isShikimori && item.shikimori == null && reviewIsAnimeOrManga) countSafetySkip("Shikimori")
                            if (isBangumi && item.bangumi == null && BangumiSyncManager.supportsMediaType(reviewType)) countSafetySkip("Bangumi")
                            if (isTmdb && item.tmdb == null && supportsTmdbAccountType(reviewType)) countSafetySkip("TMDB")
                            if ((index + 1) % 2 == 0 || index == unifiedItems.lastIndex) {
                                updateProgress(
                                    step = context.getString(R.string.cross_sync_vm_identity_review_skipped, title),
                                    detail = context.getString(R.string.cross_sync_vm_count_progress, index + 1, unifiedItems.size),
                                    processed = index + 1,
                                    total = unifiedItems.size
                                )
                            }
                            if ((index + 1) % 25 == 0) kotlinx.coroutines.yield()
                            continue
                        }
                    }

                    // (E75/E78) Birleşik kaydın tabanı aday sırasına bağlı seçilemez:
                    // "en yeni" kararı tek kaynaktan gelir (`CrossSyncMergePolicy.NEWEST_FIRST`).
                    val newest = com.kitsugi.animelist.data.auth.CrossSyncMergePolicy
                        .newestOrNull(candidates) ?: candidates.first()
                    val isAnimeOrManga = newest.type == MediaType.Anime || newest.type == MediaType.Manga

                    // ── BİRLEŞTİRME (tek ve test edilebilir kural) ──────────────────────
                    // Tüm alan kararları `CrossSyncMergePolicy` içinde: ilerleme gerilemez,
                    // ilerleme toplamı yakaladıysa durum Completed, çelişen durumlarda en son
                    // güncellenen kayıt kazanır, tarihler deterministik (başlangıç=en erken,
                    // bitiş=en geç), puan=en güncel geçerli puan. Birim testleri:
                    // `CrossSyncMergePolicyTest`.
                    val merge = com.kitsugi.animelist.data.auth.CrossSyncMergePolicy.merge(candidates)
                    val totalCount = merge.total
                    val bestStatus = merge.status
                    val maxProgress = merge.rawMaxProgress
                    val bestScore = merge.score
                    val bestStartDate = merge.startDate
                    val bestEndDate = merge.endDate
                    val bestRepeat = merge.repeatCount
                    val isFav = merge.isFavorite

                    // Not/etiket/öncelik BİLİNÇLİ OLARAK birleştirilmez: metin biçimleri ve
                    // öncelik ölçekleri sağlayıcıya göre farklıdır (MAL 0..2, AniList 0..255,
                    // Bangumi yok). Birleştirme, bir kaynağın notunu diğeriyle ezerdi.
                    val bestNotes = newest.notes
                    val bestTags = newest.tags
                    val bestPriority = newest.priority
                    val singleSourceGroup = candidates.size == 1

                    // 6. Harici ID çözümlemeleri
                    val realMalId = com.kitsugi.animelist.data.auth.CrossSyncClusterEngine
                        .extractCrosswalkOrCandidateMalId(candidates, clusterSession::keysOf)
                        ?: resolveRealMalId(newest, aniListToken)
                    val simklId = candidates.firstNotNullOfOrNull { it.simklId?.takeIf { id -> id > 0 } }
                    val tmdbId = candidates.firstNotNullOfOrNull { it.tmdbId?.takeIf { id -> id > 0 } }
                    val aniListEntryId = candidates.firstNotNullOfOrNull { it.aniListEntryId }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    // 6b. Kimlik güvencesi — yanlış içerik EKLENMESİNE karşı son savunma hattı (`E96`).
                    // Güncellemeler platformun kendi kayıt kimliğiyle yapıldığı için güvenlidir; risk yalnızca
                    // "bu platformda yok → ekle" yolundadır.
                    // ÖNEMLİ: Eski kod `Verdict.Trusted` döndürüp ardından `identityVerdict?.allowsAdditions`
                    // okuyordu; oysa `Verdict.Trusted.allowsAdditions == false` olduğundan MAL'da veya 2+
                    // platformda bulunan kayıtlarda %100 ekleme kilitlenmesi (deadlock) oluşuyordu!
                    // Doğru kapı: doğal MAL kaydı varsa doğrudan izin verilir; aksi halde MAL kataloğuna karşı
                    // doğrulama yapılır ve YALNIZCA açık katalog çelişkisi (`Verdict.Rejected`) eklemeyi durdurur
                    // (`CrossSyncIdentityGuard.allowsBulkAddition`).
                    val needsAnyAddition =
                        (isAniList && isAnimeOrManga && item.aniList == null) ||
                        (isMal && isAnimeOrManga && item.mal == null) ||
                        (isKitsu && isAnimeOrManga && item.kitsu == null) ||
                        (isShikimori && isAnimeOrManga && item.shikimori == null) ||
                        (isBangumi && BangumiSyncManager.supportsMediaType(newest.type) && item.bangumi == null) ||
                        (isSimkl && item.simkl == null) ||
                        (isTmdb && supportsTmdbAccountType(newest.type) && item.tmdb == null)
                    val identityVerdict: com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict? =
                        if (realMalId != null && needsAnyAddition) {
                            val holderSources = candidates
                                .filter { com.kitsugi.animelist.model.MediaIdentity.realMalId(it) == realMalId }
                                .map { it.source.lowercase() }
                                .distinct()
                            when {
                                holderSources.any { it in com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.NATIVE_MAL_SOURCES } ->
                                    com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict.Trusted(context.getString(R.string.cross_sync_identity_native_mal))
                                else -> com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.verifyMalId(
                                    malId = realMalId,
                                    mediaType = newest.type,
                                    localTitles = candidates.flatMap { com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.aliasesOf(it) },
                                    localYear = candidates.firstNotNullOfOrNull { it.year }
                                )
                            }
                        } else null
                    val additionsAllowed = com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.allowsBulkAddition(identityVerdict)
                    // Eklemelerde kullanılacak MAL kimliği: doğrulanmadıysa yok sayılır.
                    val guardedMalId: Int? = realMalId?.takeIf { additionsAllowed }
                    val identitySkipReason = context.getString(R.string.cross_sync_reason_identity_unverified, realMalId?.toString() ?: "?")
                    fun recordIdentitySkip(platform: String, title: String) {
                        recordSkip(
                            platform = platform,
                            reasonKey = identitySkipReason,
                            title = title,
                            message = context.getString(R.string.cross_sync_log_skipped, platform, identitySkipReason, title),
                            details = context.getString(
                                R.string.cross_sync_detail_identity_assurance,
                                identityVerdict?.describe() ?: "-"
                            ) + "\n" + context.getString(R.string.cross_sync_detail_no_new_entries)
                        )
                    }
                    fun recordIdentityReviewSkip(platform: String, reviewedEntry: MediaEntry) {
                        val reason = context.getString(R.string.cross_sync_reason_identity_review_target)
                        recordSkip(
                            platform = platform,
                            reasonKey = reason,
                            title = reviewedEntry.title,
                            message = context.getString(R.string.cross_sync_log_skipped, platform, reason, reviewedEntry.title),
                            details = context.getString(R.string.cross_sync_detail_identity_review_target) +
                                "\n" + reviewedEntry.crossSyncIdentityDiagnostic(context)
                        )
                    }
                    if (identityVerdict is com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict.Rejected) {
                        logEvent(
                            context.getString(R.string.cross_sync_log_category_matching),
                            context.getString(R.string.cross_sync_log_identity_rejected, newest.title, realMalId ?: 0),
                            isWarning = true,
                            details = buildString {
                                appendLine(identityVerdict.describe())
                                appendLine(context.getString(R.string.cross_sync_detail_suspicious_mal_identity))
                                appendLine(context.getString(R.string.cross_sync_detail_no_additions))
                                appendLine(context.getString(R.string.cross_sync_detail_source_platform_entries, candidates.size))
                                candidates.forEach { appendLine(it.crossSyncIdentityDiagnostic(context)) }
                            }
                        )
                    }

                    val bestTitleEnglish = candidates.firstNotNullOfOrNull { it.titleEnglish?.takeIf { e -> e.isNotBlank() } }
                    val bestTitleJapanese = candidates.firstNotNullOfOrNull { it.titleJapanese?.takeIf { j -> j.isNotBlank() } }

                    val mergedEntry = newest.copy(
                        titleEnglish = bestTitleEnglish ?: newest.titleEnglish,
                        titleJapanese = bestTitleJapanese ?: newest.titleJapanese,
                        status = bestStatus,
                        // Toplam biliniyorsa toplama kırpılmış ilerleme yazılır (MAL/Kitsu
                        // toplamı aşan değeri reddeder ve yazımın tamamı düşerdi).
                        progress = merge.progress,
                        volumeProgress = merge.volumeProgress,
                        isRepeating = merge.isRepeating,
                        score = bestScore,
                        startDate = bestStartDate,
                        endDate = bestEndDate,
                        notes = bestNotes,
                        tags = bestTags,
                        priority = bestPriority,
                        repeatCount = bestRepeat,
                        isFavorite = isFav,
                        // Doğrulanmamış gerçek MAL kimliği birleşik kayda taşınmaz (sentetik AniList/Kitsu kimlikleri korunur).
                        malId = if (additionsAllowed) (realMalId ?: newest.malId) else newest.malId?.takeIf { !it.isRealMalId() },
                        simklId = simklId ?: newest.simklId,
                        tmdbId = tmdbId ?: newest.tmdbId,
                        aniListEntryId = aniListEntryId ?: newest.aniListEntryId
                    )

                    /**
                     * Bu sağlayıcıya yazılacak alanlar: yalnızca gerçekten değişenler ve
                     * yalnızca sağlayıcının desteklediği alanlar. Tam yazım YAPILMAZ; aksi
                     * hâlde bir kaynağın notu/etiketi/önceliği başka kaynağınkiyle ezilirdi.
                     */
                    // NOT: tek kaynaklı grupta da yazım yapılır. "Grup tek kaynaklıysa
                    // dokunma" kuralı, ilerleme toplamı yakalamış ama durumu "izliyorum"
                    // kalmış satırların (ve toplamı aşan ilerlemenin) sonsuza kadar
                    // düzeltilmemesine yol açıyordu. Kural artık tek yerde: `dirtyFields`
                    // yalnızca gerçekten geride kalan alanı döner.
                    fun dirtyFor(provider: String, current: com.kitsugi.animelist.model.MediaEntry):
                        Set<com.kitsugi.animelist.data.auth.SyncField> =
                        com.kitsugi.animelist.data.auth.CrossSyncMergePolicy
                            .dirtyFieldsFor(current, merge, provider)

                    val learnedKitsuId = item.kitsu?.let { com.kitsugi.animelist.model.MediaIdentity.kitsuId(it) }
                    val learnedShikimoriId = item.shikimori?.let { com.kitsugi.animelist.model.MediaIdentity.shikimoriId(it) }
                    val learnedBangumiId = item.bangumi?.let { com.kitsugi.animelist.data.remote.BangumiIdNamespace.rawIdFromStable(it.malId) }
                    val learnedAniListId = item.aniList?.let { com.kitsugi.animelist.model.MediaIdentity.aniListId(it) }
                    val learnedSimklId = simklId ?: item.simkl?.simklId
                    val learnedTmdbId = tmdbId ?: item.simkl?.tmdbId ?: newest.tmdbId

                    if (realMalId != null && learnedKitsuId != null &&
                        (mergedEntry.type == com.kitsugi.animelist.model.MediaType.Anime || mergedEntry.type == com.kitsugi.animelist.model.MediaType.Manga)
                    ) {
                        runCatching {
                            ExternalAuthManager.saveKitsuMediaIdForMal(
                                context = context,
                                malId = realMalId,
                                isAnime = mergedEntry.type != com.kitsugi.animelist.model.MediaType.Manga,
                                mediaId = learnedKitsuId
                            )
                        }
                    }

                    val trustedBangumi = item.bangumi?.takeIf { !item.isIdentityReviewed(it) }
                    // Reddedilen MAL adayı kalıcı eşleme önbelleğine kanıt olarak yazılamaz.
                    if (guardedMalId != null && trustedBangumi != null) {
                        com.kitsugi.animelist.data.remote.BangumiIdNamespace.rawIdFromStable(trustedBangumi.malId)?.let { subjectId ->
                            runCatching {
                                val bangumiSubjectType = com.kitsugi.animelist.data.remote.BangumiIdNamespace.subjectTypeFor(trustedBangumi.type)
                                com.kitsugi.animelist.data.remote.BangumiLocalMappingCache.put(context, guardedMalId, bangumiSubjectType, subjectId)
                            }
                        }
                    }

                    val bundle = com.kitsugi.animelist.data.auth.CrossProviderIds(
                        malId = realMalId,
                        aniListId = learnedAniListId,
                        kitsuId = learnedKitsuId,
                        shikimoriId = learnedShikimoriId,
                        bangumiSubjectId = learnedBangumiId,
                        tmdbId = learnedTmdbId,
                        simklId = learnedSimklId
                    )
                    if (!bundle.isEmpty) {
                        runCatching {
                            com.kitsugi.animelist.data.auth.CrossProviderIdentityCache.writeBundle(
                                context = context,
                                type = mergedEntry.type,
                                ids = bundle,
                                complete = false,
                                primaryKey = realMalId?.let { "${mergedEntry.type.name}:mal:$it" }
                            )
                        }
                    }

                    val mappingDetails = buildString {
                        appendLine(context.getString(R.string.cross_sync_detail_source_platform_entries, candidates.size))
                        candidates.forEachIndexed { candidateIndex, candidate ->
                            appendLine(context.getString(R.string.cross_sync_detail_record_number, candidateIndex + 1))
                            appendLine(candidate.crossSyncIdentityDiagnostic(context))
                        }
                        appendLine(context.getString(R.string.cross_sync_detail_merged_values))
                        appendLine(mergedEntry.crossSyncIdentityDiagnostic(context))
                        appendLine(
                            context.getString(
                                R.string.cross_sync_detail_identity_assurance,
                                identityVerdict?.describe()
                                    ?: if (realMalId == null) context.getString(R.string.cross_sync_detail_no_mal_id)
                                    else context.getString(R.string.cross_sync_detail_identity_not_needed)
                            )
                        )
                    }
                    logEvent(
                        context.getString(R.string.cross_sync_log_category_matching),
                        context.getString(R.string.cross_sync_log_group_created, mergedEntry.title, candidates.size),
                        details = mappingDetails,
                        includeInLiveLog = false
                    )

                    // ── 1. AniList Eşitleme (Anime & Manga) ──
                    if (isAniList && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.aniList
                        if (current != null && item.isIdentityReviewed(current)) {
                            recordIdentityReviewSkip("AniList", current)
                        } else if (current == null && !additionsAllowed) {
                            recordIdentitySkip("AniList", mergedEntry.title)
                        } else if (current == null) {
                            // AniList'te eksik -> EKLE!
                            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("anilist")
                            val res = CrossSyncRetry.retrySyncWrite("anilist", mergedEntry.title) {
                                AniListSyncManager.updateAniListEntry(
                                    aniListToken!!, mergedEntry, context = context,
                                    knownMalId = realMalId
                                )
                            }
                            if (res.isSuccess && res.getOrNull() != null) {
                                statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("AniList", context.getString(R.string.cross_sync_log_added, "AniList", mergedEntry.title, bestStatus.plainLabel()), isAddition = true, details = mappingDetails)
                            } else if (res.isSuccess) {
                                // null = AniList'te hangi medya olduğu güvenle çözülemedi (MAL ID/ARM eşlemesi yok).
                                // Bu bir API hatası değil; kayıt atlanır ve sonda tek bir özet uyarı verilir.
                                recordSkip(
                                    platform = "AniList",
                                    reasonKey = context.getString(R.string.cross_sync_reason_anilist_media_id_unresolved),
                                    title = mergedEntry.title,
                                    message = context.getString(R.string.cross_sync_log_skipped, "AniList", context.getString(R.string.cross_sync_reason_anilist_media_id_unresolved), mergedEntry.title),
                                    details = mappingDetails
                                )
                            } else {
                                statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                val failure = res.exceptionOrNull()?.crossSyncDiagnostic(context)
                                    ?: context.getString(R.string.cross_sync_api_anilist_add_not_confirmed)
                                logEvent("AniList", context.getString(R.string.cross_sync_log_failed_add, "AniList", mergedEntry.title), isError = true, details = "$failure\n$mappingDetails")
                            }
                        } else {
                            val dirty = dirtyFor("anilist", current)
                            // Favori `SaveMediaListEntry`'nin parçası DEĞİLDİR: ayrı bir
                            // `ToggleFavourite` mutasyonudur. Yalnız favori değiştiyse liste
                            // kaydına içi boş bir mutasyon göndermek anlamsızdır (E42/E48).
                            val listFields = dirty - com.kitsugi.animelist.data.auth.SyncField.FAVORITE
                            val target = mergedEntry.copy(
                                aniListEntryId = current.aniListEntryId,
                                // Kaynak Bangumi/Kitsu olduğunda birleşik `source` gerçek MAL ID'yi
                                // kendi namespace'i sanabilir; AniList'in native sentetik ID'sini de koru.
                                malId = realMalId ?: current.malId ?: mergedEntry.malId
                            )
                            var writeOk = true
                            if (listFields.isNotEmpty()) {
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("anilist")
                                val res = CrossSyncRetry.retrySyncWrite("anilist", mergedEntry.title) {
                                    AniListSyncManager.updateAniListEntry(
                                        aniListToken!!, target, context = context, fields = listFields,
                                        knownMalId = realMalId
                                    )
                                }
                                writeOk = res.isSuccess && res.getOrNull() != null
                                if (!writeOk) {
                                    statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    val failure = res.exceptionOrNull()?.crossSyncDiagnostic(context)
                                        ?: context.getString(R.string.cross_sync_api_anilist_update_not_confirmed)
                                    logEvent("AniList", context.getString(R.string.cross_sync_log_failed_update, "AniList", mergedEntry.title), isError = true, details = "$failure\n$mappingDetails")
                                }
                            }
                            if (writeOk && com.kitsugi.animelist.data.auth.SyncField.FAVORITE in dirty) {
                                // Toggle TERSİNE dönebilir bir işlemdir: yazmadan önce uzak
                                // durum okunur, yalnız farklıysa çağrılır. Hiçbir kaynakta
                                // favori alanı yoksa kaldırma yönünde yazım YAPILMAZ.
                                // `aniListEntryId` liste kaydının ID'sidir; `Media.id` DEĞİLDİR.
                                val favoriteOutcome = runSyncCatching {
                                    val nativeMediaId = com.kitsugi.animelist.model.MediaIdentity.aniListId(current)
                                    val remoteFav = AniListSyncManager.getAniListMediaFavoriteStatus(
                                        aniListToken!!, target, context,
                                        knownAniListMediaId = nativeMediaId,
                                        knownMalId = realMalId
                                    ) ?: error("AniList favori medya kimliği çözülemedi: ${mergedEntry.title}")
                                    if (remoteFav != mergedEntry.isFavorite) {
                                        val toggled = AniListSyncManager.toggleAniListFavourite(
                                            aniListToken!!, target, context,
                                            knownAniListMediaId = nativeMediaId,
                                            knownMalId = realMalId
                                        )
                                        if (!toggled) error("AniList favori medya kimliği çözülemedi: ${mergedEntry.title}")
                                    }
                                }
                                if (favoriteOutcome.isFailure) {
                                    writeOk = false
                                    statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    val failure = favoriteOutcome.exceptionOrNull()?.crossSyncDiagnostic(context)
                                        ?: context.getString(R.string.cross_sync_api_anilist_update_not_confirmed)
                                    logEvent(
                                        "AniList",
                                        context.getString(R.string.cross_sync_log_failed_update, "AniList", mergedEntry.title),
                                        isError = true,
                                        details = "$failure\n$mappingDetails"
                                    )
                                }
                            }
                            if (writeOk) {
                                statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                logEvent("AniList", context.getString(R.string.cross_sync_log_updated, "AniList", mergedEntry.title), isUpdate = true, details = mappingDetails)
                            }
                        }
                    }

                    // ── 2. MyAnimeList Eşitleme (Anime & Manga, MAL ID gerektirir) ──
                    if (isMal && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.mal
                        if (current != null && item.isIdentityReviewed(current)) {
                            recordIdentityReviewSkip("MyAnimeList", current)
                        } else if (realMalId != null && current == null && !additionsAllowed) {
                            recordIdentitySkip("MyAnimeList", mergedEntry.title)
                        } else if (realMalId != null) {
                            if (current == null) {
                                // MAL'da eksik -> EKLE! (kimlik güvencesinden geçti)
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("mal")
                                val target = mergedEntry.copy(malId = guardedMalId ?: realMalId)
                                val res = CrossSyncRetry.retrySyncWrite("mal", mergedEntry.title) { MalSyncManager.updateMalEntry(malToken!!, target, context = context) }
                                if (res.isSuccess) {
                                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                    logEvent("MyAnimeList", context.getString(R.string.cross_sync_log_added, "MAL", mergedEntry.title, bestStatus.plainLabel()), isAddition = true, details = mappingDetails)
                                } else {
                                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    val failure = res.exceptionOrNull()?.crossSyncDiagnostic(context)
                                        ?: context.getString(R.string.cross_sync_api_mal_add_not_confirmed)
                                    logEvent("MyAnimeList", context.getString(R.string.cross_sync_log_failed_add, "MAL", mergedEntry.title), isError = true, details = "$failure\n$mappingDetails")
                                }
                            } else {
                                val dirty = dirtyFor("mal", current)
                                if (dirty.isNotEmpty()) {
                                    com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("mal")
                                    val target = mergedEntry.copy(malId = realMalId)
                                    val res = CrossSyncRetry.retrySyncWrite("mal", mergedEntry.title) {
                                        MalSyncManager.updateMalEntry(
                                            malToken!!, target, context = context, fields = dirty
                                        )
                                    }
                                    if (res.isSuccess) {
                                        statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                        logEvent("MyAnimeList", context.getString(R.string.cross_sync_log_updated, "MAL", mergedEntry.title), isUpdate = true, details = mappingDetails)
                                    } else {
                                        statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                        val failure = res.exceptionOrNull()?.crossSyncDiagnostic(context)
                                            ?: context.getString(R.string.cross_sync_api_mal_update_not_confirmed)
                                        logEvent("MyAnimeList", context.getString(R.string.cross_sync_log_failed_update, "MAL", mergedEntry.title), isError = true, details = "$failure\n$mappingDetails")
                                    }
                                }
                            }
                        } else {
                            recordSkip(
                                platform = "MyAnimeList",
                                reasonKey = context.getString(R.string.cross_sync_reason_mal_id_unverified),
                                title = mergedEntry.title,
                                message = context.getString(R.string.cross_sync_log_skipped, "MyAnimeList", context.getString(R.string.cross_sync_reason_mal_id_unverified), mergedEntry.title),
                                details = context.getString(R.string.cross_sync_detail_mal_id_resolution_failed) + "\n" + mappingDetails
                            )
                        }
                    }

                    // ── 3. Simkl Eşitleme (Anime, Dizi, Film — Tümü) ──
                    if (isSimkl && (mergedEntry.type == MediaType.Anime || mergedEntry.type == MediaType.TvShow || mergedEntry.type == MediaType.Movie)) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.simkl
                        if (current != null && item.isIdentityReviewed(current)) {
                            recordIdentityReviewSkip("Simkl", current)
                        } else if (current == null && !additionsAllowed) {
                            recordIdentitySkip("Simkl", mergedEntry.title)
                        } else if (current == null) {
                            // Doğrulanmış MAL kimliği varsa kaynak damgası "mal" yapılır; yoksa ham
                            // satır olduğu gibi kalır (kitsu/shikimori ham kimlikleri decode ile
                            // kendi uzayında okunur, asla MAL'e yazılmaz).
                            simklEntriesToSync.add(if (realMalId != null) mergedEntry.copy(malId = realMalId, source = "mal") else mergedEntry)
                        } else {
                            val dirty = dirtyFor("simkl", current)
                            if (dirty.isNotEmpty()) {
                                simklEntriesToSync.add((if (realMalId != null) mergedEntry.copy(malId = realMalId, source = "mal") else mergedEntry).copy(simklId = current.simklId))
                            }
                        }
                    }

                    // ── TMDB native account states (movie/TV only) ──
                    if (isTmdb && supportsTmdbAccountType(mergedEntry.type)) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.tmdb
                        val desiredWatchlist = current?.tmdbWatchlist ?: tmdbWatchlistValue(bestStatus)
                        val desiredRating = if (current != null && bestScore != null && current.score != bestScore) {
                            null // Use the merged integer score; do not keep a stale native half rating.
                        } else current?.tmdbRating
                        val writeFields = if (current == null) {
                            com.kitsugi.animelist.data.auth.tmdbWriteFieldsForInsert(
                                mergedEntry.copy(
                                    status = bestStatus,
                                    isFavorite = isFav,
                                    score = bestScore,
                                    tmdbWatchlist = desiredWatchlist,
                                    tmdbRating = desiredRating
                                )
                            )
                        } else {
                            com.kitsugi.animelist.data.auth.TmdbWriteFields(
                                // A missing legacy native flag may be backfilled, but an unknown
                                // remote Watchlist must never be cleared by a status-only merge.
                                watchlist = current.tmdbWatchlist == null && desiredWatchlist,
                                favorite = !singleSourceGroup && current.isFavorite != isFav,
                                rating = !singleSourceGroup && bestScore != null && current.score != bestScore
                            )
                        }
                        when {
                            !writeFields.any -> if (current == null) {
                                recordSkip(
                                    platform = "TMDB",
                                    reasonKey = context.getString(R.string.cross_sync_reason_tmdb_no_native_membership),
                                    title = mergedEntry.title,
                                    message = context.getString(
                                        R.string.cross_sync_log_skipped,
                                        "TMDB",
                                        context.getString(R.string.cross_sync_reason_tmdb_no_native_membership)
                                    ),
                                    details = context.getString(R.string.tmdb_editor_local_fields_note)
                                )
                            }
                            current == null && !additionsAllowed ->
                                recordIdentitySkip("TMDB", mergedEntry.title)
                            mergedEntry.tmdbId == null || mergedEntry.tmdbId <= 0 -> recordSkip(
                                platform = "TMDB",
                                reasonKey = context.getString(R.string.cross_sync_reason_tmdb_id_unverified),
                                title = mergedEntry.title,
                                message = context.getString(R.string.cross_sync_log_skipped, "TMDB", context.getString(R.string.cross_sync_reason_tmdb_id_unverified)),
                                details = context.getString(R.string.tmdb_identity_unresolved)
                            )
                            else -> {
                                val outcome = runSyncCatching {
                                    TmdbSyncManager.syncEntryToTmdb(
                                        context,
                                        mergedEntry.copy(
                                            tmdbWatchlist = desiredWatchlist,
                                            tmdbRating = desiredRating
                                        ),
                                        fields = writeFields
                                    )
                                }
                                val result = outcome.getOrNull()
                                if (result != null && result.isSuccess) {
                                    if (current == null) {
                                        statsMap["TMDB"] = statsMap["TMDB"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                        logEvent("TMDB", context.getString(R.string.cross_sync_log_added, "TMDB", mergedEntry.title, bestStatus.plainLabel()), isAddition = true, details = mappingDetails)
                                    } else {
                                        statsMap["TMDB"] = statsMap["TMDB"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                        logEvent("TMDB", context.getString(R.string.cross_sync_log_updated, "TMDB", mergedEntry.title), isUpdate = true, details = mappingDetails)
                                    }
                                } else {
                                    val failure = outcome.exceptionOrNull()?.crossSyncDiagnostic(context)
                                        ?: result?.errors?.joinToString("; ")
                                        ?: context.getString(R.string.cross_sync_vm_unknown_error)
                                    statsMap["TMDB"] = statsMap["TMDB"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("TMDB", context.getString(R.string.cross_sync_log_failed_update, "TMDB", mergedEntry.title), isError = true, details = "$failure\n$mappingDetails")
                                }
                            }
                        }
                    }

                    // ── 4. Kitsu Eşitleme (Anime & Manga) ──
                    if (isKitsu && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.kitsu
                        // Kitsu sentetik kimlik aralığı 300M–400M; 400M+ Shikimori'ye ait, yanlışlıkla Kitsu ID sanılmasın.
                        val knownKitsuId = current?.malId?.takeIf { it in 300_000_001..399_999_999 }?.let { it - 300_000_000 }
                        if (current != null && item.isIdentityReviewed(current)) {
                            recordIdentityReviewSkip("Kitsu", current)
                        } else if (current == null && !additionsAllowed) {
                            recordIdentitySkip("Kitsu", mergedEntry.title)
                        } else if (current == null) {
                            // Kitsu'da eksik -> EKLE! (hız sınırı artık KitsuApiClient içinde her istek için uygulanıyor)
                            val outcome = CrossSyncRetry.retrySyncWrite("kitsu", mergedEntry.title, errorTexts = { it.errors }) {
                                KitsuSyncManager.syncEntryToKitsu(context, mergedEntry, knownKitsuMediaId = null, knownMalId = realMalId)
                            }
                            val res = outcome.getOrNull()
                            if (res != null && res.errors.isEmpty()) {
                                statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("Kitsu", context.getString(R.string.cross_sync_log_added, "Kitsu", mergedEntry.title, bestStatus.plainLabel()), isAddition = true, details = mappingDetails + (res.resolvedVia?.let { "\n" + context.getString(R.string.cross_sync_detail_kitsu_identity_source, it) } ?: ""))
                            } else if (res != null && res.unresolvedMedia) {
                                recordSkip(
                                    platform = "Kitsu",
                                    reasonKey = context.getString(R.string.cross_sync_reason_kitsu_match_missing),
                                    title = mergedEntry.title,
                                    message = context.getString(R.string.cross_sync_log_skipped, "Kitsu", context.getString(R.string.cross_sync_reason_kitsu_match_missing), mergedEntry.title),
                                    details = mappingDetails
                                )
                            } else {
                                val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic(context)
                                    ?: res?.errors?.joinToString("; ")
                                    ?: context.getString(R.string.cross_sync_vm_unknown_error)
                                statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                logEvent("Kitsu", context.getString(R.string.cross_sync_log_error, "Kitsu", mergedEntry.title, errMsg), isError = true, details = "$mappingDetails\n$errMsg")
                            }
                        } else {
                            val dirty = dirtyFor("kitsu", current)
                            if (dirty.isNotEmpty()) {
                                val outcome = CrossSyncRetry.retrySyncWrite("kitsu", mergedEntry.title, errorTexts = { it.errors }) {
                                    KitsuSyncManager.syncEntryToKitsu(
                                        context, mergedEntry,
                                        knownKitsuMediaId = knownKitsuId,
                                        fields = dirty,
                                        knownMalId = realMalId
                                    )
                                }
                                val res = outcome.getOrNull()
                                if (res != null && res.errors.isEmpty()) {
                                    statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                    logEvent("Kitsu", context.getString(R.string.cross_sync_log_updated, "Kitsu", mergedEntry.title), isUpdate = true, details = mappingDetails)
                                } else if (res != null && res.unresolvedMedia) {
                                    recordSkip(
                                        platform = "Kitsu",
                                        reasonKey = context.getString(R.string.cross_sync_reason_kitsu_match_missing),
                                        title = mergedEntry.title,
                                        message = context.getString(R.string.cross_sync_log_skipped, "Kitsu", context.getString(R.string.cross_sync_reason_kitsu_match_missing), mergedEntry.title),
                                        details = mappingDetails
                                    )
                                } else {
                                    val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic(context)
                                        ?: res?.errors?.joinToString("; ")
                                        ?: context.getString(R.string.cross_sync_vm_unknown_error)
                                    statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("Kitsu", context.getString(R.string.cross_sync_log_failed_update, "Kitsu", mergedEntry.title) + " ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                                }
                            }
                        }
                    }

                    // ── 5. Shikimori Eşitleme (Anime & Manga, MAL ID gerektirir) ──
                    if (isShikimori && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.shikimori
                        if (current != null && item.isIdentityReviewed(current)) {
                            recordIdentityReviewSkip("Shikimori", current)
                        } else if (realMalId != null && current == null && !additionsAllowed) {
                            recordIdentitySkip("Shikimori", mergedEntry.title)
                        } else if (realMalId != null) {
                            if (current == null) {
                                // Shikimori'de eksik -> EKLE! (kimlik güvencesinden geçti)
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("shikimori")
                                val target = mergedEntry.copy(malId = guardedMalId ?: realMalId)
                                val outcome = CrossSyncRetry.retrySyncWrite("shikimori", mergedEntry.title, errorTexts = { it.errors }) { ShikimoriSyncManager.syncEntryToShikimori(context, target, knownMalId = realMalId) }
                                val res = outcome.getOrNull()
                                if (res != null && res.errors.isEmpty()) {
                                    statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                    logEvent("Shikimori", context.getString(R.string.cross_sync_log_added, "Shikimori", mergedEntry.title, bestStatus.plainLabel()), isAddition = true, details = mappingDetails)
                                } else {
                                    val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic(context)
                                        ?: res?.errors?.joinToString("; ")
                                        ?: context.getString(R.string.cross_sync_vm_unknown_error)
                                    statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("Shikimori", context.getString(R.string.cross_sync_log_error, "Shikimori", mergedEntry.title, errMsg), isError = true, details = "$mappingDetails\n$errMsg")
                                }
                            } else {
                                val dirty = dirtyFor("shikimori", current)
                                if (dirty.isNotEmpty()) {
                                    com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("shikimori")
                                    val target = mergedEntry.copy(malId = realMalId)
                                    val outcome = CrossSyncRetry.retrySyncWrite("shikimori", mergedEntry.title, errorTexts = { it.errors }) {
                                        ShikimoriSyncManager.syncEntryToShikimori(
                                            context, target, knownMalId = realMalId, fields = dirty
                                        )
                                    }
                                    val res = outcome.getOrNull()
                                    if (res != null && res.errors.isEmpty()) {
                                        statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                        logEvent("Shikimori", context.getString(R.string.cross_sync_log_updated, "Shikimori", mergedEntry.title), isUpdate = true, details = mappingDetails)
                                    } else {
                                        val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic(context)
                                            ?: res?.errors?.joinToString("; ")
                                            ?: context.getString(R.string.cross_sync_vm_unknown_error)
                                        statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                        logEvent("Shikimori", context.getString(R.string.cross_sync_log_failed_update, "Shikimori", mergedEntry.title) + " ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                                    }
                                }
                            }
                        } else {
                            recordSkip(
                                platform = "Shikimori",
                                reasonKey = context.getString(R.string.cross_sync_reason_shikimori_mal_id),
                                title = mergedEntry.title,
                                message = context.getString(R.string.cross_sync_log_skipped, "Shikimori", context.getString(R.string.cross_sync_reason_shikimori_mal_id), mergedEntry.title),
                                details = context.getString(R.string.cross_sync_detail_shikimori_mal_id_required) + "\n" + mappingDetails
                            )
                        }
                    }

                    // ── 6. Bangumi Eşitleme (Anime, manga, film ve dizi) ──
                    if (isBangumi && BangumiSyncManager.supportsMediaType(mergedEntry.type)) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.bangumi
                        if (current != null && item.isIdentityReviewed(current)) {
                            recordIdentityReviewSkip("Bangumi", current)
                        } else if (current == null && !additionsAllowed) {
                            recordIdentitySkip("Bangumi", mergedEntry.title)
                        } else if (current == null) {
                            // Bangumi'de eksik -> EKLE! (kimlik güvencesinden geçti)
                            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("bangumi")
                            val target = mergedEntry.copy(malId = guardedMalId ?: realMalId)
                            val outcome = CrossSyncRetry.retrySyncWrite("bangumi", mergedEntry.title, errorTexts = { it.errors }) {
                                BangumiSyncManager.syncEntryToBangumi(context, target, knownMalId = realMalId)
                            }
                            val res = outcome.getOrNull()
                            if (res != null && res.errors.isEmpty()) {
                                statsMap["Bangumi"] = statsMap["Bangumi"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("Bangumi", context.getString(R.string.cross_sync_log_added, "Bangumi", mergedEntry.title, bestStatus.plainLabel()), isAddition = true, details = mappingDetails)
                            } else if (res != null && res.unresolvedSubject) {
                                recordSkip(
                                    platform = "Bangumi",
                                    reasonKey = context.getString(R.string.cross_sync_reason_bangumi_match_missing),
                                    title = mergedEntry.title,
                                    message = context.getString(R.string.cross_sync_log_skipped, "Bangumi", context.getString(R.string.cross_sync_reason_bangumi_match_missing), mergedEntry.title),
                                    details = "$mappingDetails\n${res.errors.joinToString("; ")}"
                                )
                            } else {
                                val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic(context)
                                    ?: res?.errors?.joinToString("; ")
                                    ?: context.getString(R.string.cross_sync_vm_unknown_error)
                                statsMap["Bangumi"] = statsMap["Bangumi"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                logEvent("Bangumi", context.getString(R.string.cross_sync_log_error, "Bangumi", mergedEntry.title, errMsg), isError = true, details = "$mappingDetails\n$errMsg")
                            }
                        } else {
                            val dirty = dirtyFor("bangumi", current)
                            if (dirty.isNotEmpty()) {
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("bangumi")
                                val target = mergedEntry.copy(malId = current.malId ?: (guardedMalId ?: realMalId))
                                val outcome = CrossSyncRetry.retrySyncWrite("bangumi", mergedEntry.title, errorTexts = { it.errors }) {
                                    BangumiSyncManager.syncEntryToBangumi(
                                        context, target, fields = dirty, knownMalId = realMalId
                                    )
                                }
                                val res = outcome.getOrNull()
                                if (res != null && res.errors.isEmpty()) {
                                    statsMap["Bangumi"] = statsMap["Bangumi"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                    logEvent(
                                        "Bangumi",
                                        context.getString(R.string.cross_sync_log_updated_bangumi, mergedEntry.title, bestStatus.plainLabel(), maxProgress),
                                        isUpdate = true,
                                        details = mappingDetails
                                    )
                                } else {
                                    val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic(context)
                                        ?: res?.errors?.joinToString("; ")
                                        ?: context.getString(R.string.cross_sync_vm_unknown_error)
                                    statsMap["Bangumi"] = statsMap["Bangumi"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("Bangumi", context.getString(R.string.cross_sync_log_failed_update, "Bangumi", mergedEntry.title) + " ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                                }
                            }
                        }
                    }


                    if ((index + 1) % 2 == 0 || index == unifiedItems.size - 1) {
                        updateProgress(
                            step = context.getString(R.string.cross_sync_vm_syncing_item, mergedEntry.title),
                            detail = context.getString(R.string.cross_sync_vm_progress_percent, index + 1, unifiedItems.size, ((index + 1).toFloat() / unifiedItems.size * 100).toInt()),
                            processed = index + 1,
                            total = unifiedItems.size
                        )
                    }
                    if ((index + 1) % 25 == 0) kotlinx.coroutines.yield()
                  } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                  } catch (itemError: Exception) {
                    // 2026-11-02: tek bir kayıttaki beklenmedik hata (örn. kaynak biçim
                    // uyuşmazlığı "Format specifier '%3$s'") tüm eşitlemeyi 830/900'de
                    // durduruyordu. Artık kayıt atlanıp günlüğe yazılıyor ve döngü devam ediyor.
                    logEvent(
                        context.getString(R.string.cross_sync_log_category_error),
                        context.getString(R.string.cross_sync_log_item_failed, context.getString(R.string.cross_sync_log_category_sync), item.primaryTitle, itemError.message ?: itemError.javaClass.simpleName),
                        isError = true,
                        details = itemError.crossSyncDiagnostic(context)
                    )
                  }
                }

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // ── Simkl Toplu Senkronizasyonu (1 req/sn rate limit'e tam uyumlu batch) ──
                if (isSimkl && simklEntriesToSync.isNotEmpty()) {
                    updateProgress(
                        context.getString(R.string.cross_sync_vm_simkl_syncing),
                        context.getString(R.string.cross_sync_vm_simkl_queued, simklEntriesToSync.size),
                        processed = 0,
                        total = simklEntriesToSync.size,
                        unit = context.getString(R.string.cross_sync_vm_simkl_record_unit)
                    )
                    val chunks = simklEntriesToSync.chunked(35)
                    var simklAdded = 0
                    var simklErrors = 0
                    var simklSkipped = 0
                    // Kayıt bazlı muhasebe: Simkl `not_found` ile geri gönderdiği kayıtlar "atlandı",
                    // yalnızca HTTP/ağ seviyesinde yazılamayan kayıtlar "hata" sayılır. Eskiden grupta tek
                    // bir eşleşmeyen kayıt bile 35 kaydın tamamını hata olarak saydırıyordu (297 atlandı / 200 hata).
                    val simklUnmatchedTitles = mutableListOf<String>()
                    val simklProgressLimitedTitles = mutableListOf<String>()
                    val simklWarnings = mutableListOf<String>()
                    for ((idx, chunk) in chunks.withIndex()) {
                        val simklProcessed = minOf((idx + 1) * 35, simklEntriesToSync.size)
                        val progressText = context.getString(R.string.cross_sync_vm_simkl_progress, simklProcessed, simklEntriesToSync.size, (simklProcessed.toFloat() / simklEntriesToSync.size * 100).toInt())
                        updateProgress(
                            context.getString(R.string.cross_sync_vm_simkl_syncing),
                            progressText,
                            processed = simklProcessed,
                            total = simklEntriesToSync.size,
                            unit = context.getString(R.string.cross_sync_vm_simkl_record_unit)
                        )
                        val syncRes = SimklSyncManager.syncBatchToSimkl(context, chunk)
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = syncRes.addedCount
                        val notFound = syncRes.notFoundCount
                        simklAdded += count
                        simklSkipped += notFound
                        simklErrors += syncRes.failedCount
                        simklUnmatchedTitles.addAll(syncRes.unmatchedTitles)
                        simklProgressLimitedTitles.addAll(syncRes.unsupportedProgressTitles)
                        simklWarnings.addAll(syncRes.warnings)
                        val chunkDetails = chunk.joinToString("\n\n") { it.crossSyncIdentityDiagnostic(context) }
                        val groupLabel = context.getString(R.string.cross_sync_simkl_group_label, idx + 1, chunks.size)
                        if (syncRes.errors.isEmpty()) {
                            logEvent(
                                "Simkl",
                                context.getString(
                                    R.string.cross_sync_simkl_group_synced,
                                    groupLabel,
                                    count,
                                    if (notFound > 0) context.getString(R.string.cross_sync_simkl_not_found_suffix, notFound) else ""
                                ),
                                isAddition = count > 0,
                                details = buildString {
                                    if (syncRes.unmatchedTitles.isNotEmpty()) {
                                        appendLine(context.getString(R.string.cross_sync_simkl_unmatched_entries, syncRes.unmatchedTitles.size))
                                        syncRes.unmatchedTitles.forEach { appendLine("• $it") }
                                        appendLine()
                                    }
                                    append(context.getString(R.string.cross_sync_simkl_group_entries))
                                    appendLine()
                                    append(chunkDetails)
                                }
                            )
                        } else {
                            syncRes.errors.forEach { message ->
                                logEvent(
                                    "Simkl",
                                    context.getString(R.string.cross_sync_simkl_group_error, groupLabel, message),
                                    isError = true,
                                    details = context.getString(
                                        R.string.cross_sync_simkl_group_summary,
                                        count,
                                        notFound,
                                        syncRes.failedCount
                                    ) + "\n" + chunkDetails
                                )
                            }
                            if (count > 0) {
                                logEvent(
                                    "Simkl",
                                    context.getString(R.string.cross_sync_simkl_partially_synced, groupLabel, count),
                                    isAddition = true,
                                    details = chunkDetails,
                                    includeInLiveLog = false
                                )
                            }
                        }
                        if (idx < chunks.size - 1) {
                            // Simkl API 1 istek/saniye kuralına tam uyum
                            kotlinx.coroutines.delay(1200L)
                        }
                    }
                    if (simklUnmatchedTitles.isNotEmpty()) {
                        val distinctUnmatched = simklUnmatchedTitles.distinct()
                        logEvent(
                            "Simkl",
                            context.getString(R.string.cross_sync_simkl_catalog_not_found, distinctUnmatched.size),
                            isWarning = true,
                            details = context.getString(R.string.cross_sync_simkl_catalog_not_found_detail) + "\n\n" +
                                distinctUnmatched.joinToString("\n") { "• $it" }
                        )
                    }
                    if (simklProgressLimitedTitles.isNotEmpty()) {
                        val distinctLimited = simklProgressLimitedTitles.distinct()
                        logEvent(
                            "Simkl",
                            context.getString(R.string.cross_sync_simkl_progress_not_synced, distinctLimited.size),
                            isWarning = true,
                            details = context.getString(R.string.cross_sync_simkl_progress_not_supported_detail) + "\n\n" +
                                distinctLimited.joinToString("\n") { "• $it" }
                        )
                    }
                    if (simklWarnings.isNotEmpty()) {
                        logEvent(
                            "Simkl",
                            context.getString(R.string.cross_sync_simkl_partial_warnings, simklWarnings.size),
                            isWarning = true,
                            details = simklWarnings.joinToString("\n") { "• $it" }
                        )
                    }
                    statsMap["Simkl"] = statsMap["Simkl"]!!.let {
                        it.copy(
                            addedCount = it.addedCount + simklAdded,
                            errorCount = it.errorCount + simklErrors,
                            skippedCount = it.skippedCount + simklSkipped
                        )
                    }
                    kotlinx.coroutines.delay(1500L)
                }

                // Platform başına tek satırlık "atlandı" özetleri (ayrıntılarda tam başlık listesi)
                flushSkipSummaries()

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // Read back actual remote state; desired merged values are NOT receipts.
                updateProgress(
                    context.getString(R.string.cross_sync_vm_verifying_lists),
                    context.getString(R.string.cross_sync_vm_verifying_detail),
                    processed = 0,
                    total = 0,
                    unit = ""
                )
                if (isAniList) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "AniList"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("anilist", AniListImportManager.fetchAllLists(aniListToken!!), allowDelete = false) }
                        .onSuccess {
                            // Doğrulama turu listeyi BAŞTAN çekti: defter güncellenir ki
                            // kullanıcı hemen ardından "Yenile"ye bastığında aynı tam liste
                            // yeniden çekilmesin.
                            com.kitsugi.animelist.data.sync.ListSyncStateStore(context)
                                .recordSuccess("anilist", fullSync = true)
                        }
                        .onFailure { logEvent("AniList", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isMal) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "MyAnimeList"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("mal", MalImportManager.fetchAllLists(malToken!!, showAdult), allowDelete = false) }
                        .onSuccess {
                            // Doğrulama turu listeyi BAŞTAN çekti: defter güncellenir ki
                            // kullanıcı hemen ardından "Yenile"ye bastığında aynı tam liste
                            // yeniden çekilmesin.
                            com.kitsugi.animelist.data.sync.ListSyncStateStore(context)
                                .recordSuccess("mal", fullSync = true)
                        }
                        .onFailure { logEvent("MyAnimeList", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isKitsu) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "Kitsu"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    val freshKitsuToken = ExternalAuthManager.getOrRefreshKitsuToken(context) ?: kitsuToken!!
                    runSyncCatching { repository.smartImport("kitsu", KitsuImportManager.fetchAllLists(context, freshKitsuToken, kitsuUserId!!), allowDelete = false) }
                        .onSuccess {
                            // Doğrulama turu listeyi BAŞTAN çekti: defter güncellenir ki
                            // kullanıcı hemen ardından "Yenile"ye bastığında aynı tam liste
                            // yeniden çekilmesin.
                            com.kitsugi.animelist.data.sync.ListSyncStateStore(context)
                                .recordSuccess("kitsu", fullSync = true)
                        }
                        .onFailure { logEvent("Kitsu", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isShikimori) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "Shikimori"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    val freshShikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context) ?: shikimoriToken!!
                    runSyncCatching { repository.smartImport("shikimori", ShikimoriImportManager.fetchAllLists(context, freshShikimoriToken, shikimoriUserId!!), allowDelete = false) }
                        .onSuccess {
                            // Doğrulama turu listeyi BAŞTAN çekti: defter güncellenir ki
                            // kullanıcı hemen ardından "Yenile"ye bastığında aynı tam liste
                            // yeniden çekilmesin.
                            com.kitsugi.animelist.data.sync.ListSyncStateStore(context)
                                .recordSuccess("shikimori", fullSync = true)
                        }
                        .onFailure { logEvent("Shikimori", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isSimkl) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "Simkl"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    val refreshToken = ExternalAuthManager.getSimklToken(context) ?: error(context.getString(R.string.cross_sync_simkl_disconnected))
                    runSyncCatching { repository.smartImport("simkl", SimklImportManager.fetchAllLists(refreshToken), allowDelete = false) }
                        .onSuccess {
                            // Doğrulama turu listeyi BAŞTAN çekti: defter güncellenir ki
                            // kullanıcı hemen ardından "Yenile"ye bastığında aynı tam liste
                            // yeniden çekilmesin.
                            com.kitsugi.animelist.data.sync.ListSyncStateStore(context)
                                .recordSuccess("simkl", fullSync = true)
                        }
                        .onFailure { logEvent("Simkl", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isTmdb) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "TMDB"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    runSyncCatching {
                        TmdbAccountSyncCoordinator.refresh(context, repository, allowRemoteDelete = false)
                    }.onFailure {
                        logEvent("TMDB", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context))
                    }
                }
                if (isBangumi) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "Bangumi"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    val freshBangumiToken = BangumiAuthStore.getValidToken(context) ?: bangumiToken!!
                    runSyncCatching { repository.smartImport("bangumi", BangumiImportManager.fetchAllLists(context, freshBangumiToken), allowDelete = false) }
                        .onSuccess {
                            // Doğrulama turu listeyi BAŞTAN çekti: defter güncellenir ki
                            // kullanıcı hemen ardından "Yenile"ye bastığında aynı tam liste
                            // yeniden çekilmesin.
                            com.kitsugi.animelist.data.sync.ListSyncStateStore(context)
                                .recordSuccess("bangumi", fullSync = true)
                        }
                        .onFailure { logEvent("Bangumi", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                    // Doğrulama aktarımından sonra eksik Latin/İngilizce adları tamamla
                    // (çapraz senkron beklenmez; tarama arka planda, kilitle sıralanarak çalışır).
                    launchBangumiTitleHydration()
                }

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val hasSyncErrors = statsMap.values.any { it.errorCount > 0 } || reportLogs.any { it.isError }
                val hasWarnings = reportLogs.any { it.isWarning }
                val totalErrors = statsMap.values.sumOf { it.errorCount }
                val totalSkipped = statsMap.values.sumOf { it.skippedCount }
                val summary = when {
                    hasSyncErrors -> context.getString(R.string.cross_sync_vm_summary_partial, totalErrors, totalSkipped)
                    hasWarnings -> context.getString(R.string.cross_sync_vm_summary_skipped, totalSkipped)
                    else -> context.getString(R.string.cross_sync_vm_summary_complete)
                }
                // Özet satırı kendisi bir sorun değildir; hata/uyarı sayacına eklenmez (eskiden kırmızı
                // "hata" olarak listelenip issue sayısını şişiriyordu).
                logEvent(context.getString(R.string.cross_sync_log_category_complete), summary)

                crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState(
                    isRunning = false,
                    isCompleted = true,
                    currentStep = if (hasSyncErrors) context.getString(R.string.cross_sync_vm_partially_completed) else context.getString(R.string.cross_sync_vm_completed),
                    currentDetail = summary,
                    processedItems = unifiedItems.size,
                    totalItems = unifiedItems.size,
                    progressUnit = context.getString(R.string.cross_sync_vm_item_unit),
                    lastUpdatedAt = System.currentTimeMillis(),
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    platformStats = statsMap.toMap(),
                    logs = recentLogs.toList(),
                    startedAt = syncStartedAt
                )

                onShowMessage?.invoke(summary)
            }.onFailure { error ->
                logEvent(
                    context.getString(R.string.cross_sync_log_category_error),
                    context.getString(R.string.cross_sync_log_sync_error, error.message ?: error.javaClass.simpleName),
                    isError = true,
                    details = error.crossSyncDiagnostic(context)
                )
                crossSyncState = crossSyncState.copy(
                    isRunning = false,
                    isCompleted = false,
                    currentStep = context.getString(R.string.cross_sync_vm_error_title),
                    currentDetail = error.message ?: context.getString(R.string.cross_sync_vm_unknown_error),
                    errorMessage = error.message,
                    errorCode = com.kitsugi.animelist.data.platform.PlatformFailureRegistry.resolveSince(syncStartedAt, error),
                    logs = recentLogs.toList(),
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    lastUpdatedAt = System.currentTimeMillis(),
                    startedAt = syncStartedAt
                )
                onShowMessage?.invoke(context.getString(R.string.cross_sync_vm_error_while_syncing, error.message ?: context.getString(R.string.cross_sync_vm_unknown_error)))
            }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                logEvent(context.getString(R.string.cross_sync_log_category_sync), context.getString(R.string.cross_sync_log_cancelled), isWarning = true)
                throw cancelled
            } catch (error: Exception) {
                logEvent(
                    context.getString(R.string.cross_sync_log_category_sync),
                    context.getString(R.string.cross_sync_log_start_failed, error.message ?: error.javaClass.simpleName),
                    isError = true,
                    details = error.crossSyncDiagnostic(context)
                )
                crossSyncState = crossSyncState.copy(
                    isRunning = false,
                    isCompleted = false,
                    currentStep = context.getString(R.string.cross_sync_vm_error_title),
                    errorMessage = error.message,
                    errorCode = com.kitsugi.animelist.data.platform.PlatformFailureRegistry.resolveSince(syncStartedAt, error),
                    currentDetail = error.message ?: context.getString(R.string.cross_sync_vm_accounts_unreadable),
                    logs = recentLogs.toList(),
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    lastUpdatedAt = System.currentTimeMillis(),
                    startedAt = syncStartedAt
                )
                onShowMessage?.invoke(context.getString(R.string.cross_sync_vm_start_error_prefix, error.message ?: context.getString(R.string.cross_sync_vm_unknown_error)))
            } finally {
                if (crossSyncState.isRunning) {
                    logEvent(context.getString(R.string.cross_sync_log_category_sync), context.getString(R.string.cross_sync_log_stopped), isWarning = true)
                    crossSyncState = crossSyncState.copy(
                        isRunning = false,
                        currentStep = context.getString(R.string.cross_sync_vm_stopped),
                        currentDetail = context.getString(R.string.cross_sync_vm_stop_detail),
                        logs = recentLogs.toList(),
                        totalEventCount = reportLogs.size,
                        issueCount = issueCount,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                }
                val completedAt = System.currentTimeMillis()
                val finalState = crossSyncState.copy(
                    isRunning = false,
                    platformStats = statsMap.toMap(),
                    logs = recentLogs.toList(),
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    reportLogs = reportLogs.toList(),
                    startedAt = syncStartedAt,
                    finishedAt = completedAt,
                    lastUpdatedAt = completedAt
                )
                val reportLocation = runCatching { CrossSyncReportStore.save(context, finalState) }.getOrNull()
                crossSyncState = finalState.copy(reportSavedTo = reportLocation)
                isCrossSyncRunning = false
                crossSyncJob = null
            }
        }
    }

    fun loginKitsu(
        username: String,
        password: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runSyncCatching {
                val tokenResp = KitsuApiClient.loginWithPassword(username, password)
                val user = KitsuApiClient.getCurrentUser(tokenResp.accessToken)
                ExternalAuthManager.saveKitsuAuth(
                    context = context,
                    token = tokenResp.accessToken,
                    refreshToken = tokenResp.refreshToken,
                    userId = user.id,
                    username = user.name
                )
                val settings = SettingsDataStore(context)
                settings.saveKitsuProfileInfo(user.name, user.avatarUrl)
                user.name
            }.onSuccess { userName ->
                refreshAuthState()
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke(context.getString(R.string.account_sync_connected, "Kitsu", userName))
                    onSuccess()
                }
            }.onFailure { err ->
                val msg = err.message ?: context.getString(R.string.account_sync_login_failed, "Kitsu")
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke(msg)
                    onError(msg)
                }
            }
        }
    }

    fun loginShikimori(
        clientId: String = ShikimoriApiClient.DEFAULT_CLIENT_ID,
        clientSecret: String = ShikimoriApiClient.DEFAULT_CLIENT_SECRET,
        authCode: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val effectiveClientId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
        val effectiveClientSecret = clientSecret.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_SECRET }
        val cleanCode = ShikimoriApiClient.sanitizeAuthCode(authCode)

        viewModelScope.launch(Dispatchers.IO) {
            // Tarayıcı hangi redirect_uri ile açıldıysa token isteği de onunla eşleşmek zorunda
            // (Shikimori/Doorkeeper). Akış başlatılırken kaydedilen değeri kullan.
            val pendingRedirectUri = ExternalAuthManager.getShikimoriPendingRedirectUri(context)
                ?: ShikimoriApiClient.DEFAULT_REDIRECT_URI
            runSyncCatching {
                ExternalAuthManager.saveShikimoriCredentials(context, effectiveClientId, effectiveClientSecret)
                ShikimoriApiClient.exchangeCodeForToken(
                    clientId = effectiveClientId,
                    clientSecret = effectiveClientSecret,
                    code = cleanCode,
                    redirectUri = pendingRedirectUri
                )
            }.onSuccess { tokenResp ->
                // Kod bu noktada tüketilmiştir: token'u DERHAL sakla. Profil (whoami)
                // bakımı geçici olarak başarısız olsa bile geçerli oturum çöpe atılmaz;
                // kullanıcı kimliği ilk kullanımda tembel çözümlenir (bkz. ensureShikimoriUserResolved).
                ExternalAuthManager.saveShikimoriAuth(
                    context = context,
                    token = tokenResp.accessToken,
                    refreshToken = tokenResp.refreshToken,
                    expiresInSeconds = tokenResp.expiresIn,
                    userId = 0,
                    username = "Shikimori",
                    notify = false
                )
                val user = runSyncCatching {
                    ShikimoriApiClient.getCurrentUser(tokenResp.accessToken)
                }.getOrNull()
                if (user != null) {
                    ExternalAuthManager.saveShikimoriAuth(
                        context = context,
                        token = tokenResp.accessToken,
                        refreshToken = tokenResp.refreshToken,
                        expiresInSeconds = tokenResp.expiresIn,
                        userId = user.id,
                        username = user.nickname
                    )
                    SettingsDataStore(context).saveShikimoriProfileInfo(user.nickname, user.avatarUrl)
                } else {
                    // Kimlik henüz çözülemedi: tek bir Success yayınlamak otomatik
                    // aktarımı başlatır; aktarım kimliği tembel çözer.
                    ExternalAuthManager.saveShikimoriAuth(
                        context = context,
                        token = tokenResp.accessToken,
                        refreshToken = tokenResp.refreshToken,
                        expiresInSeconds = tokenResp.expiresIn,
                        userId = 0,
                        username = "Shikimori"
                    )
                }
                refreshAuthState()
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke(
                        if (user != null) context.getString(R.string.account_sync_connected, "Shikimori", user.nickname)
                        else context.getString(R.string.account_sync_connected_profile_missing, "Shikimori")
                    )
                    onSuccess()
                }
            }.onFailure { err ->
                val msg = err.message ?: context.getString(R.string.account_sync_login_failed, "Shikimori")
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke(msg)
                    onError(msg)
                }
            }
            // Akış tamamlandı (başarılı/başarısız): bekleyen redirect kaydını temizle.
            ExternalAuthManager.clearShikimoriPendingRedirectUri(context)
        }
    }

    fun importKitsuList(
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            announceListRefresh(
                displayName = "Kitsu",
                source = SourceListSynchronizer.KITSU,
                outcome = refreshKitsuList(repository, mode)
            )
        }
    }

    fun importShikimoriList(
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            announceListRefresh(
                displayName = "Shikimori",
                source = SourceListSynchronizer.SHIKIMORI,
                outcome = refreshShikimoriList(repository, mode)
            )
        }
    }

    fun importKitsuList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) = importKitsuList(repository, mode)

    /**
     * Bangumi (bgm.tv) OAuth girişi.

     * Shikimori'den farklı olarak Bangumi'de paylaşılan varsayılan uygulama kaydı yoktur:
     * token ucu `client_secret` istediği için her uygulama https://bgm.tv/dev/app üzerinden
     * kendi App ID / App Secret değerini almalıdır. Kimlik bilgileri burada saklanır ve
     * sonraki token yenilemelerinde yeniden kullanılır.
     */
    fun loginBangumi(
        clientId: String,
        clientSecret: String,
        authCode: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        BangumiAuthStore.saveCredentials(context, clientId, clientSecret)
        val pendingRedirectUri = BangumiAuthStore.getPendingRedirectUri(context)
        BangumiAuthManager.exchangeCode(
            context = context,
            code = authCode,
            redirectUri = pendingRedirectUri,
            onSuccess = {
                refreshAuthState()
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val token = BangumiAuthStore.getToken(context)
                        if (!token.isNullOrBlank()) {
                            val profile = BangumiImportManager.fetchUserProfile(token)
                            SettingsDataStore(context)
                                .saveBangumiProfileInfo(profile.nickname, profile.avatarUrl)
                        }
                    }
                }
                onSuccess()
            },
            onError = { message -> onError(message) }
        )
    }

    /**
     * Bangumi kütüphane satırlarının eksik Latin/İngilizce adlarını arka planda tamamlar.
     *
     * Bangumi koleksiyon ucu yalnızca özgün adı döndürür; satırlar böyle yazılırsa "Listem",
     * başlık dili İngilizce/Romaji seçili olsa bile Japonca/Çince ad gösterir. Tarama
     * [BangumiLibraryTitleHydrator] kilidiyle sıraya girer (Listem açılış taramasıyla
     * çakışmaz) ve sonuçları veritabanına yazdığı için liste Room akışıyla kendiliğinden
     * seçili dile döner. Çağıran iş (içe aktarım / çapraz senkron) beklenmez.
     */
    private fun launchBangumiTitleHydration() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val language = runCatching {
                    SettingsDataStore(context).settingsFlow.first().titleLanguage
                }.getOrDefault("ENGLISH")
                BangumiLibraryTitleHydrator.hydrate(KitsugiDatabase.getDatabase(context), language)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("AuthViewModel", "Bangumi başlık tamamlama atlandı: ${error.message}")
            }
        }
    }

    private fun launchMalAxisTitleHydration() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                MalAxisLibraryTitleHydrator.hydrate(KitsugiDatabase.getDatabase(context))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("AuthViewModel", "Simkl/Shikimori başlık tamamlama atlandı: ${error.message}")
            }
        }
    }

    /**
     * Bangumi koleksiyonunu (想看/在看/看过) Kitsugi kütüphanesine aktarır.
     *
     * Diğer kaynaklarla aynı yoldan geçer: TTL/delta politikası + fark-tabanlı yazım.
     * Bangumi'nin koleksiyon ucu "şu tarihten sonra değişenler" desteklemediği için
     * delta yoktur; buna karşılık tazelik penceresi ve fark-tabanlı yazım sayesinde
     * değişmeyen satırlar veritabanına yeniden yazılmaz.
     */
    fun importBangumiList(
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            announceListRefresh(
                displayName = "Bangumi",
                source = SourceListSynchronizer.BANGUMI,
                outcome = refreshBangumiList(repository, mode)
            )
        }
    }

    fun importBangumiList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) = importBangumiList(repository, mode)


    fun importShikimoriList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) = importShikimoriList(repository, mode)

    fun importSimklList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository,
        mode: ListRefreshMode = ListRefreshMode.USER_REFRESH
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            announceListRefresh(
                displayName = "Simkl",
                source = SourceListSynchronizer.SIMKL,
                outcome = refreshSimklList(repository, mode)
            )
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Liste yenileme çekirdeği
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Tek kaynağın yenilemesini çalıştırır (askıya alınabilir). Bağlı değilse `null`.
     *
     * Tek kaynak yenilemesi ve [syncAllLists] aynı yolu kullanır; böylece politika,
     * single-flight kilidi, delta/tam kararı ve fark-tabanlı yazım tek yerde kalır.
     */
    private suspend fun refreshAniListList(
        repository: MediaEntryRepository,
        mode: ListRefreshMode
    ): ListRefreshOutcome? {
        val token = ExternalAuthManager.getAniListToken(context) ?: return null
        if (isAniListImportRunning) return ListRefreshOutcome.AlreadyRunning(SourceListSynchronizer.ANILIST)
        isAniListImportRunning = true
        try {
            val outcome = SourceListSynchronizer.refresh(
                context = context,
                repository = repository,
                source = SourceListSynchronizer.ANILIST,
                mode = mode,
                supportsDelta = true,
                // Tam turda uzakta artık olmayan satırlar silinir; delta turunda yok sayılır.
                allowDelete = true,
                fetchFull = { AniListImportManager.fetchAllLists(token) },
                fetchDelta = { since -> AniListImportManager.fetchListsSince(token, since) }
            )
            if (outcome is ListRefreshOutcome.Completed) {
                // Profil bilgisi (ad/avatar/banner) yalnızca başarılı bir turdan sonra tazelenir.
                runSyncCatching {
                    val profile = AniListImportManager.fetchUserProfile(token)
                    SettingsDataStore(context).setAniListProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.avatarUrl.orEmpty(),
                        bannerImageUri = profile.bannerUrl.orEmpty()
                    )
                }
            }
            return outcome
        } finally {
            isAniListImportRunning = false
        }
    }

    private suspend fun refreshMalList(
        repository: MediaEntryRepository,
        mode: ListRefreshMode
    ): ListRefreshOutcome? {
        val token = ExternalAuthManager.getOrRefreshMalToken(context) ?: return null
        if (isMalImportRunning) return ListRefreshOutcome.AlreadyRunning(SourceListSynchronizer.MAL)
        isMalImportRunning = true
        try {
            val settingsDataStore = SettingsDataStore(context)
            val showAdult = runCatching { settingsDataStore.settingsFlow.first().showAdultContent }
                .getOrDefault(false)
            val outcome = SourceListSynchronizer.refresh(
                context = context,
                repository = repository,
                source = SourceListSynchronizer.MAL,
                mode = mode,
                // MAL API v2 "değişenler" süzgeci sunmuyor: delta yok, TTL + fark yazımı var.
                supportsDelta = false,
                // Tam turda uzakta artık olmayan satırlar silinir; delta turunda yok sayılır.
                allowDelete = true,
                // `nsfw=false` iken MAL +18 kayıtları yanıta hiç koymaz: bunlar silme adayı
                // olmamalı, yoksa kullanıcının +18 listesi her yenilemede silinirdi.
                keepAdultRows = !showAdult,
                fetchFull = { MalImportManager.fetchAllLists(token, showAdult) }
            )
            if (outcome is ListRefreshOutcome.Completed) {
                runSyncCatching {
                    val profile = MalImportManager.fetchUserProfile(token)
                    settingsDataStore.setMalProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.pictureUrl.orEmpty(),
                        bannerImageUri = ""
                    )
                }
            }
            return outcome
        } finally {
            isMalImportRunning = false
        }
    }

    private suspend fun refreshSimklList(
        repository: MediaEntryRepository,
        mode: ListRefreshMode
    ): ListRefreshOutcome? {
        val token = ExternalAuthManager.getSimklToken(context) ?: return null
        if (isSimklImportRunning) return ListRefreshOutcome.AlreadyRunning(SourceListSynchronizer.SIMKL)
        isSimklImportRunning = true
        try {
            val settingsDataStore = SettingsDataStore(context)
            val outcome = SourceListSynchronizer.refresh(
                context = context,
                repository = repository,
                source = SourceListSynchronizer.SIMKL,
                mode = mode,
                supportsDelta = true,
                // Tam turda uzakta artık olmayan satırlar silinir; delta turunda yok sayılır.
                allowDelete = true,
                fetchFull = { SimklImportManager.fetchAllLists(token) },
                // Simkl'de "son N tarihten sonra değişenler" yok; onun yerine ucuz
                // activities imzası kullanılır: imza aynıysa liste hiç çekilmez.
                revisionProbe = { SimklImportManager.fetchActivitiesRevision(token) }
            )
            if (outcome is ListRefreshOutcome.Completed) {
                runSyncCatching {
                    val profile = SimklImportManager.fetchUserProfile(token)
                    settingsDataStore.setSimklProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.avatarUrl.orEmpty(),
                        bannerImageUri = ""
                    )
                }
                launchMalAxisTitleHydration()
            }
            return outcome
        } finally {
            isSimklImportRunning = false
        }
    }

    private suspend fun refreshKitsuList(
        repository: MediaEntryRepository,
        mode: ListRefreshMode
    ): ListRefreshOutcome? {
        val token = ExternalAuthManager.getKitsuToken(context) ?: return null
        val userId = ExternalAuthManager.getKitsuUserId(context) ?: return null
        if (isKitsuImportRunning) return ListRefreshOutcome.AlreadyRunning(SourceListSynchronizer.KITSU)
        isKitsuImportRunning = true
        try {
            val outcome = SourceListSynchronizer.refresh(
                context = context,
                repository = repository,
                source = SourceListSynchronizer.KITSU,
                mode = mode,
                // Kitsu `library-entries?filter[since]=…` destekliyor → delta mümkün.
                supportsDelta = true,
                // Tam turda uzakta artık olmayan satırlar silinir; delta turunda yok sayılır.
                allowDelete = true,
                fetchFull = { KitsuImportManager.fetchAllLists(context, token, userId) },
                fetchDelta = { since ->
                    KitsuImportManager.fetchAllListsSince(context, token, userId, since)
                }
            )
            if (outcome is ListRefreshOutcome.Completed) {
                runSyncCatching {
                    val profile = KitsuImportManager.fetchUserProfile(token)
                    SettingsDataStore(context).saveKitsuProfileInfo(profile.name, profile.avatarUrl)
                }
            }
            return outcome
        } finally {
            isKitsuImportRunning = false
        }
    }

    private suspend fun refreshShikimoriList(
        repository: MediaEntryRepository,
        mode: ListRefreshMode
    ): ListRefreshOutcome? {
        val token = ExternalAuthManager.getOrRefreshShikimoriToken(context) ?: return null
        var userId = ExternalAuthManager.getShikimoriUserId(context)
        if (!token.isNullOrBlank() && userId == null &&
            ExternalAuthManager.ensureShikimoriUserResolved(context)
        ) {
            userId = ExternalAuthManager.getShikimoriUserId(context)
        }
        if (userId == null) return null
        if (isShikimoriImportRunning) return ListRefreshOutcome.AlreadyRunning(SourceListSynchronizer.SHIKIMORI)
        isShikimoriImportRunning = true
        try {
            val resolvedUserId = userId
            val outcome = SourceListSynchronizer.refresh(
                context = context,
                repository = repository,
                source = SourceListSynchronizer.SHIKIMORI,
                mode = mode,
                supportsDelta = false,
                // Tam turda uzakta artık olmayan satırlar silinir; delta turunda yok sayılır.
                allowDelete = true,
                fetchFull = { ShikimoriImportManager.fetchAllLists(context, token, resolvedUserId) }
            )
            if (outcome is ListRefreshOutcome.Completed) {
                runSyncCatching {
                    val profile = ShikimoriImportManager.fetchUserProfile(token)
                    SettingsDataStore(context).saveShikimoriProfileInfo(profile.nickname, profile.avatarUrl)
                }
                launchMalAxisTitleHydration()
            }
            return outcome
        } finally {
            isShikimoriImportRunning = false
        }
    }

    private suspend fun refreshBangumiList(
        repository: MediaEntryRepository,
        mode: ListRefreshMode
    ): ListRefreshOutcome? {
        val token = BangumiAuthStore.getValidToken(context) ?: return null
        if (isBangumiImportRunning) return ListRefreshOutcome.AlreadyRunning(SourceListSynchronizer.BANGUMI)
        isBangumiImportRunning = true
        try {
            BangumiAuthStore.ensureUserResolved(context)
            val outcome = SourceListSynchronizer.refresh(
                context = context,
                repository = repository,
                source = SourceListSynchronizer.BANGUMI,
                mode = mode,
                supportsDelta = false,
                // Tam turda uzakta artık olmayan satırlar silinir; delta turunda yok sayılır.
                allowDelete = true,
                fetchFull = { BangumiImportManager.fetchAllLists(context, token) }
            )
            if (outcome is ListRefreshOutcome.Completed) {
                // Koleksiyon satırları yalnızca özgün (Japonca/Çince) adla gelir; yeni
                // satırlar bir sonraki açılışı beklemeden Latin/İngilizce adla görünsün.
                launchBangumiTitleHydration()
            }
            return outcome
        } finally {
            isBangumiImportRunning = false
        }
    }

    /**
     * Yenileme sonucunu kullanıcıya tek satırda bildirir.
     *
     * "Başarılı" mesajı kayıt sayısını değil GERÇEK değişikliği anlatır: eskiden her
     * yenilemede "823 kayıt aktarıldı" deniyordu, oysa hiçbir şey değişmemiş olabilirdi.
     */
    private fun announceListRefresh(
        displayName: String,
        source: String,
        outcome: ListRefreshOutcome?
    ) {
        if (outcome == null) {
            // Kaynak bağlı değil / kimlik çözülemedi: sessiz kalmak yerine kullanıcıyı bilgilendir.
            onShowMessage?.invoke("⚠️ $displayName: hesap bağlı değil ya da oturum bulunamadı")
            return
        }

        val message = when (outcome) {
            is ListRefreshOutcome.Completed -> {
                val stats = outcome.stats
                // Bekleyen (henüz sunucuya gitmemiş) yerel düzenlemeler uzak veriyle
                // ezilmediyse bunu söylemek gerekir; aksi halde kullanıcı "güncelledim ama
                // eski değer görünüyor" diye düşünür.
                val deferredNote = if (stats.deferred > 0) {
                    " · ${stats.deferred} bekleyen düzenleme korundu"
                } else {
                    ""
                }
                if (!stats.hasChanges) {
                    "✅ $displayName: liste zaten güncel (${stats.fetched} kayıt denetlendi)$deferredNote"
                } else {
                    val parts = buildList {
                        if (stats.added > 0) add("${stats.added} yeni")
                        if (stats.updated > 0) add("${stats.updated} güncellendi")
                        if (stats.deleted > 0) add("${stats.deleted} kaldırıldı")
                    }
                    "✅ $displayName: " + parts.joinToString(", ") + deferredNote
                }
            }
            is ListRefreshOutcome.Skipped -> when (outcome.reason) {
                "unchanged" -> "⏱ $displayName: sunucuda değişiklik yok"
                "fresh" -> "⏱ $displayName: ${formatSyncAge(outcome.lastSuccessAt)} yenilendi"
                else -> "⏱ $displayName: yenileme ertelendi"
            }
            is ListRefreshOutcome.AlreadyRunning ->
                context.getString(R.string.account_sync_import_running, displayName)
            is ListRefreshOutcome.Failed -> "❌ $displayName: ${outcome.message}"
        }

        android.util.Log.d("KitsugiListRefresh", "$source -> ${outcome::class.java.simpleName}: $message")
        // "fresh" / "unchanged" durumları bilgi mesajıdır; hata gibi görünmesin.
        onShowMessage?.invoke(message)
    }

    private fun formatSyncAge(timestampMs: Long): String {
        if (timestampMs <= 0L) return "hiç"
        val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        return when {
            minutes < 1 -> "az önce"
            minutes < 60 -> "$minutes dk önce"
            minutes < 24 * 60 -> "${minutes / 60} sa önce"
            else -> "${minutes / (24 * 60)} gün önce"
        }
    }
}
