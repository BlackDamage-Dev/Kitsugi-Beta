package com.kitsugi.animelist.ui.app

import com.kitsugi.animelist.data.auth.SourceSyncStatus
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.ui.utils.plainLabel
import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.data.auth.runSyncCatching
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
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydrator
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaEntry
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

    var isSimklSessionExpired by mutableStateOf(false)
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

        isSimklSessionExpired = context.getSharedPreferences("MyWebViewPrefs", Context.MODE_PRIVATE)
            .getBoolean("simkl_session_expired", false)
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
        ExternalAuthManager.disconnectAccount(
            context = context,
            serviceName = serviceName
        )

        viewModelScope.launch {
            val settings = SettingsDataStore(context)
            when (serviceName) {
                "anilist" -> settings.clearAniListProfileInfo()
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
            }
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
            else -> "MyAnimeList"
        }
        onShowMessage?.invoke(context.getString(R.string.account_sync_disconnected, displayName))
    }

    /**
     * Liste içe aktarma hatasını **kaynak adı + halk dili** ile özetler (v2.4.341).
     *
     * Eskiden her içe aktarma ekranı ham istisna metnini (`HTTP 401`,
     * `JSONException: Expected BEGIN_OBJECT`) gösteriyordu; kullanıcı bunun "şifrem
     * doldu mu, internet mi gitti, sunucu mu kapalı" olduğunu anlayamıyordu. Artık
     * neden [SourceSyncStatus] ile sınıflandırılır, kalıcı duruma yazılır (Listem
     * şeridi ve bildirim ekranı aynı cümleyi gösterir) ve kullanıcıya tek cümlelik
     * açıklama döner.
     */
    private fun importFailureMessage(
        source: SourceSyncStatus.Source,
        error: Throwable
    ): String {
        val text = error.message
        val reason = SourceSyncStatus.classify(text)
        SourceSyncStatus.reportFailure(context, source, reason, text)
        return SourceSyncStatus.describeAction(context, source, reason)
    }

    fun importAniListAnimeList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) {
        val token = ExternalAuthManager.getAniListToken(context)

        if (token.isNullOrBlank()) {
            onShowMessage?.invoke(context.getString(R.string.account_sync_token_missing, "AniList"))
            return
        }

        if (isAniListImportRunning) {
            onShowMessage?.invoke(context.getString(R.string.account_sync_import_running, "AniList"))
            return
        }

        isAniListImportRunning = true
        onShowMessage?.invoke(context.getString(R.string.account_sync_import_fetching, "AniList"))

        viewModelScope.launch(Dispatchers.IO) {
            val settingsDataStore = SettingsDataStore(context)

            runSyncCatching {
                runSyncCatching {
                    val profile = AniListImportManager.fetchUserProfile(token)
                    settingsDataStore.setAniListProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.avatarUrl.orEmpty(),
                        bannerImageUri = profile.bannerUrl.orEmpty()
                    )
                }
                AniListImportManager.fetchAllLists(token)
            }.onSuccess { importedEntries ->
                val importResult = runCatching {
                    repository.smartImport("anilist", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    // Liste uzaktan geldi: yerel "son eşitlenen favori" işaretleri geçersiz.
                    AniListSyncManager.resetFavoriteSyncMarkers(context)
                    // İçe aktarma başarılı: kaynağın hata durumu temizlenir (v2.4.341).
                    SourceSyncStatus.reportSuccess(context, SourceSyncStatus.Source.ANILIST)
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_success, importedEntries.size, "AniList"))
                } else {
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_save_failed, "AniList", importResult.exceptionOrNull()?.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }.onFailure { error ->
                // Kota (429) ayrı mesaj: liste biraz sonra yeniden çekilebilir, veri kaybı yok.
                val message = if (com.kitsugi.animelist.data.auth.AniListGate.isQuotaMessage(error.message)) {
                    val waitSeconds = (
                        com.kitsugi.animelist.data.auth.AniListGate.cooldownRemainingMs() / 1_000L
                    ).coerceAtLeast(1L)
                    context.getString(R.string.account_sync_anilist_quota_refresh, waitSeconds)
                } else {
                    error.message ?: context.getString(R.string.account_sync_import_failed, "AniList")
                }
                onShowMessage?.invoke(message)
            }

            isAniListImportRunning = false
        }
    }

    fun importMalAnimeList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) {
        if (isMalImportRunning) {
            onShowMessage?.invoke(context.getString(R.string.account_sync_import_running, "MyAnimeList"))
            return
        }

        isMalImportRunning = true
        onShowMessage?.invoke(context.getString(R.string.account_sync_import_fetching, "MyAnimeList"))

        viewModelScope.launch(Dispatchers.IO) {
            val token = ExternalAuthManager.getOrRefreshMalToken(context)

            if (token.isNullOrBlank()) {
                onShowMessage?.invoke(context.getString(R.string.account_sync_token_missing, "MyAnimeList"))
                isMalImportRunning = false
                return@launch
            }

            val settingsDataStore = SettingsDataStore(context)

            runSyncCatching {
                runSyncCatching {
                    val profile = MalImportManager.fetchUserProfile(token)
                    settingsDataStore.setMalProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.pictureUrl.orEmpty(),
                        bannerImageUri = ""
                    )
                }
                val showAdult = settingsDataStore.settingsFlow.first().showAdultContent
                MalImportManager.fetchAllLists(token, showAdult)
            }.onSuccess { importedEntries ->
                val importResult = runCatching {
                    repository.smartImport("mal", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    // İçe aktarma başarılı: kaynağın hata durumu temizlenir (v2.4.341).
                    SourceSyncStatus.reportSuccess(context, SourceSyncStatus.Source.MAL)
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_success, importedEntries.size, "MyAnimeList"))
                } else {
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_save_failed, "MyAnimeList", importResult.exceptionOrNull()?.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }.onFailure { error ->
                onShowMessage?.invoke(
                    importFailureMessage(SourceSyncStatus.Source.MAL, error)
                )
            }

            isMalImportRunning = false
        }
    }

    private class UnifiedSyncItem(
        var aniList: MediaEntry? = null,
        var mal: MediaEntry? = null,
        var simkl: MediaEntry? = null,
        var kitsu: MediaEntry? = null,
        var shikimori: MediaEntry? = null,
        var bangumi: MediaEntry? = null,
        var identityReviewRequired: Boolean = false,
        var identityReviewDetails: String? = null
    ) {
        val candidates: List<MediaEntry>
            get() = listOfNotNull(aniList, mal, simkl, kitsu, shikimori, bangumi)

        val primaryTitle: String
            get() = candidates.firstOrNull()?.titleEnglish?.takeIf { it.isNotBlank() }
                ?: candidates.firstOrNull()?.title ?: KitsugiStrings.get(R.string.cross_sync_vm_unknown_media)

        val mediaType: MediaType
            get() = candidates.firstOrNull()?.type ?: MediaType.Anime
    }

    private fun Int.isRealMalId(): Boolean = this in 1..99_999_999

    @Suppress("UNUSED_PARAMETER")
    private suspend fun resolveRealMalId(entry: MediaEntry, aniListToken: String?): Int? {
        val existing = entry.malId?.takeIf { it.isRealMalId() }
        if (existing != null) return existing

        val isAnimeOrManga = entry.type == MediaType.Anime || entry.type == MediaType.Manga
        if (!isAnimeOrManga) return null

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
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val kitsuUserId = ExternalAuthManager.getKitsuUserId(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            var shikimoriUserId = ExternalAuthManager.getShikimoriUserId(context)
            if (!shikimoriToken.isNullOrBlank() && shikimoriUserId == null &&
                ExternalAuthManager.ensureShikimoriUserResolved(context)
            ) {
                shikimoriUserId = ExternalAuthManager.getShikimoriUserId(context)
            }
            val bangumiToken = if (syncSettings.syncEnabledBangumi) BangumiAuthStore.getValidToken(context) else null

            val isAniList = syncSettings.syncEnabledAnilist && !aniListToken.isNullOrBlank()
            val isMal = syncSettings.syncEnabledMal && !malToken.isNullOrBlank()
            val isSimkl = syncSettings.syncEnabledSimkl && !simklToken.isNullOrBlank()
            val isKitsu = syncSettings.syncEnabledKitsu && !kitsuToken.isNullOrBlank() && !kitsuUserId.isNullOrBlank()
            val isShikimori = syncSettings.syncEnabledShikimori && !shikimoriToken.isNullOrBlank() && shikimoriUserId != null
            val isBangumi = syncSettings.syncEnabledBangumi && !bangumiToken.isNullOrBlank()

            val connectedPlatforms = mutableListOf<String>()
            if (isAniList) connectedPlatforms.add("AniList")
            if (isMal) connectedPlatforms.add("MyAnimeList")
            if (isSimkl) connectedPlatforms.add("Simkl")
            if (isKitsu) connectedPlatforms.add("Kitsu")
            if (isShikimori) connectedPlatforms.add("Shikimori")
            if (isBangumi) connectedPlatforms.add("Bangumi")

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

                // ── FAZ 2: Çoklu İndeks ile Unified Eşleştirme ────────────────────
                val sourceEntryTotal = aniListEntries.size + malEntries.size + simklEntries.size +
                    kitsuEntries.size + shikimoriEntries.size + bangumiEntries.size
                updateProgress(
                    context.getString(R.string.cross_sync_vm_matching_entries),
                    if (sourceEntryTotal > 0) context.getString(R.string.cross_sync_vm_scan_source_entries, sourceEntryTotal) else context.getString(R.string.cross_sync_vm_no_matchable_entries),
                    processed = 0,
                    total = sourceEntryTotal,
                    unit = context.getString(R.string.cross_sync_vm_source_entry_unit)
                )

                val unifiedItems = mutableListOf<UnifiedSyncItem>()
                val candidateIndex = com.kitsugi.animelist.data.auth.CrossSyncCandidateIndex<UnifiedSyncItem>()
                // Japanese franchise titles can be shared by distinct movies/sequels, so only
                // primary and English title aliases participate in the cross-sync safety check.
                fun titleAliases(entry: MediaEntry): Set<String> = listOfNotNull(
                    entry.title,
                    entry.titleEnglish
                ).map { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                    .filter { it.length >= 2 }
                    .toSet()

                fun describeIdentityCandidate(entry: MediaEntry): String = entry.crossSyncIdentityDiagnostic(context).trimEnd()

                fun clusterEntry(entry: MediaEntry, assignToPlatform: (UnifiedSyncItem) -> Unit) {
                    val identity = com.kitsugi.animelist.model.MediaIdentity
                    val entryKeys = identity.keys(entry)
                    val entryTitles = titleAliases(entry)
                    val matches = candidateIndex.possibleMatches(entry).filter { item ->
                        item.candidates.isNotEmpty() && item.candidates.all { identity.sameMedia(it, entry) }
                    }

                    val sameTitleIdConflicts = candidateIndex.sharingTitleAlias(entry).filter { item ->
                        item.candidates.any { candidate ->
                            candidate.type == entry.type &&
                                identity.conflictingIdentityKeys(candidate, entry).isNotEmpty() &&
                                titleAliases(candidate).intersect(entryTitles).isNotEmpty()
                        }
                    }

                    // Aynı başlık, farklı sağlayıcı kimliği (ör. 1999 ve 2011 Hunter x Hunter, remake'ler, aynı adlı
                    // sezonlar): `sameMedia` çelişen kimlikleri zaten birleştirmez; bu kayıtlar ayrı gruplar olarak
                    // kendi kimlikleriyle güvenle eşitlenebilir. Eskiden tüm kayıt "kimlik doğrulaması gerekli" diye
                    // izole edilip hiçbir hesaba yazılmıyordu; bu, Simkl/MAL/Shikimori'deki yüzlerce "atlandı"
                    // kaydının ana kaynağıydı. Artık yalnızca bilgi amaçlı rapora not düşülür.
                    if (sameTitleIdConflicts.isNotEmpty() && matches.isEmpty()) {
                        val conflictKeys = sameTitleIdConflicts.flatMap { group ->
                            group.candidates.flatMap { candidate -> identity.conflictingIdentityKeys(candidate, entry) }
                        }.distinct()
                        logEvent(
                            context.getString(R.string.cross_sync_log_category_matching),
                            context.getString(R.string.cross_sync_log_same_title_conflict, entry.title),
                            details = buildString {
                                appendLine(context.getString(R.string.cross_sync_detail_source_identity_conflict))
                                appendLine(context.getString(R.string.cross_sync_detail_incoming_record))
                                appendLine(describeIdentityCandidate(entry))
                                appendLine(context.getString(R.string.cross_sync_detail_existing_same_title))
                                sameTitleIdConflicts.flatMap { it.candidates }.distinct().forEach { candidate ->
                                    appendLine(describeIdentityCandidate(candidate))
                                }
                                appendLine(
                                    context.getString(
                                        R.string.cross_sync_detail_different_identity_fields,
                                        conflictKeys.joinToString().ifBlank { KitsugiStrings.get(R.string.cross_sync_diag_no_value) }
                                    )
                                )
                            },
                            includeInLiveLog = false
                        )
                    }

                    val item = when {
                        matches.isEmpty() -> UnifiedSyncItem().also { unifiedItems.add(it) }
                        matches.size == 1 -> {
                            val match = matches.single()
                            val sharedIds = match.candidates.flatMap { identity.keys(it).intersect(entryKeys) }.distinct()
                            val unrelatedTitleIds = if (sharedIds.isNotEmpty()) {
                                match.candidates.filter { candidate ->
                                    identity.keys(candidate).intersect(entryKeys).isNotEmpty() &&
                                        titleAliases(candidate).isNotEmpty() && entryTitles.isNotEmpty() &&
                                        titleAliases(candidate).intersect(entryTitles).isEmpty()
                                }
                            } else emptyList()
                            if (unrelatedTitleIds.isNotEmpty()) {
                                // Aynı sağlayıcı kimliği (ör. aynı MAL ID) paylaşan kayıtlar normalde aynı yapımdır;
                                // alias farkı çoğunlukla dil/çeviri farkıdır (Simkl İngilizce, Shikimori romaji, Kitsu
                                // kanonik). Birleştirme için iki koşul birden aranır: (a) yıllar uyumlu (bilinmiyor ya da
                                // en fazla 1 yıl fark) ve (b) başlıklar akraba (ortak ayırt edici kelime / ön ek / EN-JP
                                // alias). Aksi halde sağlayıcı eşlemesi hatalı kabul edilir ve yazma durdurulur — böylece
                                // yanlış eşlenmiş bir kimlik üzerinden kullanıcının eklemediği içerik hesaplara yazılmaz.
                                val suspiciousMatches = unrelatedTitleIds.filter { candidate ->
                                    !com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.yearsCompatible(candidate.year, entry.year) ||
                                        !com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.entriesLookRelated(candidate, entry)
                                }
                                if (suspiciousMatches.isNotEmpty()) {
                                    val details = buildString {
                                        appendLine(context.getString(R.string.cross_sync_detail_unrelated_shared_ids))
                                        appendLine(context.getString(R.string.cross_sync_detail_shared_ids, sharedIds.joinToString()))
                                        appendLine(context.getString(R.string.cross_sync_detail_incoming_record))
                                        appendLine(describeIdentityCandidate(entry))
                                        appendLine(context.getString(R.string.cross_sync_detail_matching_records))
                                        suspiciousMatches.forEach { appendLine(describeIdentityCandidate(it)) }
                                    }
                                    match.identityReviewRequired = true
                                    match.identityReviewDetails = details
                                } else {
                                    logEvent(
                                        context.getString(R.string.cross_sync_log_category_matching),
                                        context.getString(R.string.cross_sync_log_shared_identity_merged, entry.title),
                                        details = buildString {
                                            appendLine(context.getString(R.string.cross_sync_detail_shared_ids, sharedIds.joinToString()))
                                            appendLine(context.getString(R.string.cross_sync_detail_incoming_record))
                                            appendLine(describeIdentityCandidate(entry))
                                            appendLine(context.getString(R.string.cross_sync_detail_matching_records))
                                            unrelatedTitleIds.forEach { appendLine(describeIdentityCandidate(it)) }
                                        },
                                        includeInLiveLog = false
                                    )
                                }
                            }
                            match
                        }
                        else -> {
                            val keyMatches = matches.filter { candidateGroup ->
                                candidateGroup.candidates.any { identity.keys(it).intersect(entryKeys).isNotEmpty() }
                            }
                            val normalizedEntryTitle = identity.normalizedTitle(entry.title)
                            val exactTitleMatches = matches.filter { candidateGroup ->
                                candidateGroup.candidates.any { candidate ->
                                    val normalizedCandidateTitle = identity.normalizedTitle(candidate.title)
                                    normalizedCandidateTitle.isNotBlank() && normalizedCandidateTitle == normalizedEntryTitle
                                }
                            }
                            val resolvedMatch = when {
                                keyMatches.size == 1 -> keyMatches.single()
                                keyMatches.isEmpty() && exactTitleMatches.size == 1 -> exactTitleMatches.single()
                                else -> null
                            }
                            // Tekil seçim de tek aday dalındaki güvenlik kontrolünden geçmeli: seçilen grubun herhangi bir
                            // kaydı yıl olarak uyuşmuyorsa veya ortak kimlikli kayıt başlıkça akraba değilse grup birleştirilmez.
                            // Güncellemeler de grup doğruluğuna bağlı olduğu için bu durumda hiçbir hesaba yazılmaz.
                            val identityConflictedCandidates = resolvedMatch?.candidates.orEmpty().filter { candidate ->
                                val sharesId = identity.keys(candidate).intersect(entryKeys).isNotEmpty()
                                !com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.yearsCompatible(candidate.year, entry.year) ||
                                    (sharesId && titleAliases(candidate).intersect(entryTitles).isEmpty() &&
                                        !com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.entriesLookRelated(candidate, entry))
                            }
                            if (resolvedMatch != null && identityConflictedCandidates.isEmpty()) {
                                val details = buildString {
                                    appendLine(
                                        context.getString(
                                            R.string.cross_sync_detail_multiple_candidates,
                                            context.getString(if (keyMatches.size == 1) R.string.cross_sync_detail_unique_shared_id else R.string.cross_sync_detail_exact_title)
                                        )
                                    )
                                    appendLine(context.getString(R.string.cross_sync_detail_incoming_record))
                                    appendLine(describeIdentityCandidate(entry))
                                    appendLine(context.getString(R.string.cross_sync_detail_selected_candidate, matches.indexOf(resolvedMatch) + 1))
                                    appendLine(context.getString(R.string.cross_sync_detail_candidate_groups))
                                    matches.forEachIndexed { index, group ->
                                        appendLine(context.getString(R.string.cross_sync_detail_candidate, index + 1))
                                        group.candidates.forEach { appendLine(describeIdentityCandidate(it)) }
                                    }
                                }
                                logEvent(
                                    context.getString(R.string.cross_sync_log_category_matching),
                                    context.getString(
                                        R.string.cross_sync_detail_multiple_candidates,
                                        context.getString(if (keyMatches.size == 1) R.string.cross_sync_detail_unique_shared_id else R.string.cross_sync_detail_exact_title)
                                    ) + " " + entry.title,
                                    isWarning = true,
                                    details = details
                                )
                                resolvedMatch
                            } else {
                                val details = buildString {
                                    appendLine(
                                        if (resolvedMatch != null)
                                            context.getString(R.string.cross_sync_detail_rejected_title_year)
                                        else
                                            context.getString(R.string.cross_sync_detail_ambiguous_candidates)
                                    )
                                    appendLine(context.getString(R.string.cross_sync_detail_incoming_record))
                                    appendLine(describeIdentityCandidate(entry))
                                    appendLine(context.getString(R.string.cross_sync_detail_candidate_groups))
                                    matches.forEachIndexed { index, group ->
                                        appendLine(context.getString(R.string.cross_sync_detail_candidate, index + 1))
                                        group.candidates.forEach { appendLine(describeIdentityCandidate(it)) }
                                    }
                                }
                                UnifiedSyncItem(
                                    identityReviewRequired = true,
                                    identityReviewDetails = details
                                ).also { unifiedItems.add(it) }
                            }
                        }
                    }
                    assignToPlatform(item)
                    candidateIndex.add(item, entry)
                }

                var sourceEntriesProcessed = 0
                suspend fun clusterEntries(
                    entries: List<MediaEntry>,
                    assignToPlatform: (UnifiedSyncItem, MediaEntry) -> Unit
                ) {
                    for (entry in entries) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        clusterEntry(entry) { item -> assignToPlatform(item, entry) }
                        sourceEntriesProcessed++
                        if (sourceEntriesProcessed % 25 == 0 || sourceEntriesProcessed == sourceEntryTotal) {
                            updateProgress(
                                step = context.getString(R.string.cross_sync_vm_matching_entries),
                                detail = context.getString(R.string.cross_sync_vm_source_scan_progress, sourceEntriesProcessed, sourceEntryTotal, unifiedItems.size),
                                processed = sourceEntriesProcessed,
                                total = sourceEntryTotal,
                                unit = context.getString(R.string.cross_sync_vm_source_entry_unit)
                            )
                            kotlinx.coroutines.yield()
                        }
                    }
                }

                clusterEntries(aniListEntries) { item, entry -> item.aniList = entry }
                clusterEntries(malEntries) { item, entry -> item.mal = entry }
                clusterEntries(simklEntries) { item, entry -> item.simkl = entry }
                clusterEntries(kitsuEntries) { item, entry -> item.kitsu = entry }
                clusterEntries(shikimoriEntries) { item, entry -> item.shikimori = entry }
                clusterEntries(bangumiEntries) { item, entry -> item.bangumi = entry }

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
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val candidates = item.candidates
                    if (candidates.isEmpty()) {
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
                        val title = candidates.firstOrNull()?.title ?: context.getString(R.string.cross_sync_vm_unknown_media)
                        logEvent(
                            context.getString(R.string.cross_sync_log_category_matching),
                            context.getString(R.string.cross_sync_log_identity_review, title),
                            isWarning = true,
                            details = item.identityReviewDetails ?: candidates.joinToString("\n\n") { it.crossSyncIdentityDiagnostic(context) }
                        )
                        val reviewType = candidates.first().type
                        val reviewIsAnimeOrManga = reviewType == MediaType.Anime || reviewType == MediaType.Manga
                        if (isAniList && item.aniList == null && reviewIsAnimeOrManga) countSafetySkip("AniList")
                        if (isMal && item.mal == null && reviewIsAnimeOrManga) countSafetySkip("MyAnimeList")
                        if (isSimkl && item.simkl == null && reviewType != MediaType.Manga) countSafetySkip("Simkl")
                        if (isKitsu && item.kitsu == null && reviewIsAnimeOrManga) countSafetySkip("Kitsu")
                        if (isShikimori && item.shikimori == null && reviewIsAnimeOrManga) countSafetySkip("Shikimori")
                        if (isBangumi && item.bangumi == null && BangumiSyncManager.supportsMediaType(reviewType)) countSafetySkip("Bangumi")
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

                    val newest = candidates.maxByOrNull { it.updatedAt } ?: candidates.first()
                    val isAnimeOrManga = newest.type == MediaType.Anime || newest.type == MediaType.Manga

                    // 1. En yüksek ilerleme (bölüm sayısı asla gerilemez)
                    val maxProgress = candidates.maxOf { it.progress }
                    val maxVolumeProgress = candidates.maxOf { it.volumeProgress }

                    // 2. En gelişmiş izleme durumu
                    // Simkl'in `total` değeri "yayınlanmış bölüm sayısı"dır (devam eden dizilerde toplam değil);
                    // bu sayıdan context.getString(R.string.cross_sync_log_category_complete) çıkarımı yapmak devam eden yapımları yanlışlıkla bitmiş gösteriyordu.
                    // Çıkarım için yalnızca MAL/AniList/Kitsu/Shikimori toplamları kullanılır.
                    val totalCount = candidates
                        .filter { com.kitsugi.animelist.model.MediaIdentity.canonicalSource(it.source) != "simkl" }
                        .firstNotNullOfOrNull { it.total?.takeIf { t -> t > 0 } }
                    val singleSourceGroup = candidates.size == 1
                    val bestStatus = when {
                        totalCount != null && maxProgress >= totalCount -> WatchStatus.Completed
                        candidates.any { it.status == WatchStatus.Completed } && (totalCount == null || maxProgress >= (totalCount ?: 0)) -> WatchStatus.Completed
                        candidates.any { it.status == WatchStatus.Repeating } -> WatchStatus.Repeating
                        candidates.any { it.status == WatchStatus.Watching } -> WatchStatus.Watching
                        candidates.any { it.status == WatchStatus.Paused } -> WatchStatus.Paused
                        candidates.any { it.status == WatchStatus.Dropped } -> WatchStatus.Dropped
                        else -> newest.status
                    }

                    // 3. Puan (Eksik puanlar doldurulur, en güncel geçerli puan korunur)
                    val bestScore = candidates.filter { (it.score ?: 0) > 0 }.maxByOrNull { it.updatedAt }?.score
                        ?: newest.score

                    // 4. Başlangıç ve bitiş tarihleri (Eksik olanlar tamamlanır)
                    val bestStartDate = candidates.firstNotNullOfOrNull { it.startDate?.takeIf { d -> d.isNotBlank() } }
                    val bestEndDate = candidates.firstNotNullOfOrNull { it.endDate?.takeIf { d -> d.isNotBlank() } }

                    // 5. Notlar ve etiketler
                    val bestNotes = candidates.firstNotNullOfOrNull { it.notes?.takeIf { n -> n.isNotBlank() } }
                    val bestTags = candidates.firstNotNullOfOrNull { it.tags?.takeIf { t -> t.isNotBlank() } }
                    val bestPriority = candidates.firstNotNullOfOrNull { it.priority?.takeIf { p -> p > 0 } }
                    val bestRepeat = candidates.maxOf { it.repeatCount }
                    val isFav = candidates.any { it.isFavorite }

                    // 6. Harici ID çözümlemeleri
                    val realMalId = candidates.firstNotNullOfOrNull { it.malId?.takeIf { id -> id.isRealMalId() } }
                        ?: resolveRealMalId(newest, aniListToken)
                    val simklId = candidates.firstNotNullOfOrNull { it.simklId?.takeIf { id -> id > 0 } }
                    val tmdbId = candidates.firstNotNullOfOrNull { it.tmdbId?.takeIf { id -> id > 0 } }
                    val aniListEntryId = candidates.firstNotNullOfOrNull { it.aniListEntryId }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    // 6b. Kimlik güvencesi — yanlış içerik EKLENMESİNE karşı son savunma hattı.
                    // Güncellemeler platformun kendi kayıt kimliğiyle yapıldığı için güvenlidir; risk yalnızca
                    // "bu platformda yok → ekle" yolundadır. Grubun MAL kimliği doğal kaynaktan (MAL/Shikimori)
                    // gelmiyorsa ve en az iki bağımsız platform tarafından doğrulanmıyorsa, kataloğa karşı
                    // başlık + yıl doğrulaması yapılır. Reddedilen/doğrulanamayan kimlikle hiçbir platforma
                    // yeni kayıt eklenmez; mevcut kayıtların güncellenmesi etkilenmez.
                    val needsAnyAddition =
                        (isAniList && isAnimeOrManga && item.aniList == null) ||
                        (isMal && isAnimeOrManga && item.mal == null) ||
                        (isKitsu && isAnimeOrManga && item.kitsu == null) ||
                        (isShikimori && isAnimeOrManga && item.shikimori == null) ||
                        (isBangumi && BangumiSyncManager.supportsMediaType(newest.type) && item.bangumi == null) ||
                        (isSimkl && item.simkl == null)
                    val identityVerdict: com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict? =
                        if (realMalId != null && needsAnyAddition) {
                            val holderSources = candidates
                                .filter { it.malId == realMalId }
                                .map { it.source.lowercase() }
                                .distinct()
                            when {
                                holderSources.any { it in com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.NATIVE_MAL_SOURCES } ->
                                    com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict.Trusted(context.getString(R.string.cross_sync_identity_native_mal))
                                holderSources.size >= 2 ->
                                    com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict.Trusted(context.getString(R.string.cross_sync_identity_multiple_platforms, holderSources.joinToString("+")))
                                else -> com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.verifyMalId(
                                    malId = realMalId,
                                    mediaType = newest.type,
                                    localTitles = candidates.flatMap { com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.aliasesOf(it) },
                                    localYear = candidates.firstNotNullOfOrNull { it.year }
                                )
                            }
                        } else null
                    val additionsAllowed = identityVerdict?.allowsAdditions ?: true
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
                        progress = maxProgress,
                        volumeProgress = maxVolumeProgress,
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

                    if (realMalId != null && item.bangumi != null) {
                        com.kitsugi.animelist.data.remote.BangumiIdNamespace.rawIdFromStable(item.bangumi?.malId)?.let { subjectId ->
                            runCatching {
                                val bangumiSubjectType = com.kitsugi.animelist.data.remote.BangumiIdNamespace.subjectTypeFor(item.bangumi!!.type)
                                com.kitsugi.animelist.data.remote.BangumiLocalMappingCache.put(context, realMalId, bangumiSubjectType, subjectId)
                            }
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
                        if (current == null && !additionsAllowed) {
                            recordIdentitySkip("AniList", mergedEntry.title)
                        } else if (current == null) {
                            // AniList'te eksik -> EKLE!
                            // AniList hız/kota denetimi AniListGate'te (postAniList).
                            val res = runSyncCatching { AniListSyncManager.updateAniListEntry(aniListToken!!, mergedEntry, context = context) }
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
                            val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore) ||
                                    (current.startDate.isNullOrBlank() && !bestStartDate.isNullOrBlank()) ||
                                    (current.endDate.isNullOrBlank() && !bestEndDate.isNullOrBlank()))
                            if (needsUpdate) {
                                // AniList hız/kota denetimi AniListGate'te (postAniList).
                                val target = mergedEntry.copy(aniListEntryId = current.aniListEntryId)
                                val res = runSyncCatching { AniListSyncManager.updateAniListEntry(aniListToken!!, target, context = context) }
                                if (res.isSuccess && res.getOrNull() != null) {
                                    statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                    logEvent("AniList", context.getString(R.string.cross_sync_log_updated, "AniList", mergedEntry.title), isUpdate = true, details = mappingDetails)
                                } else {
                                    statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    val failure = res.exceptionOrNull()?.crossSyncDiagnostic(context)
                                        ?: context.getString(R.string.cross_sync_api_anilist_update_not_confirmed)
                                    logEvent("AniList", context.getString(R.string.cross_sync_log_failed_update, "AniList", mergedEntry.title), isError = true, details = "$failure\n$mappingDetails")
                                }
                            }
                        }
                    }

                    // ── 2. MyAnimeList Eşitleme (Anime & Manga, MAL ID gerektirir) ──
                    if (isMal && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (realMalId != null && item.mal == null && !additionsAllowed) {
                            recordIdentitySkip("MyAnimeList", mergedEntry.title)
                        } else if (realMalId != null) {
                            val current = item.mal
                            if (current == null) {
                                // MAL'da eksik -> EKLE! (kimlik güvencesinden geçti)
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("mal")
                                val target = mergedEntry.copy(malId = guardedMalId ?: realMalId)
                                val res = runSyncCatching { MalSyncManager.updateMalEntry(malToken!!, target, context = context) }
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
                                val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                        (current.progress < maxProgress) ||
                                        (bestScore != null && current.score != bestScore) ||
                                        (current.startDate.isNullOrBlank() && !bestStartDate.isNullOrBlank()) ||
                                        (current.endDate.isNullOrBlank() && !bestEndDate.isNullOrBlank()))
                                if (needsUpdate) {
                                    com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("mal")
                                    val target = mergedEntry.copy(malId = realMalId)
                                    val res = runSyncCatching { MalSyncManager.updateMalEntry(malToken!!, target, context = context) }
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
                        if (current == null && !additionsAllowed) {
                            recordIdentitySkip("Simkl", mergedEntry.title)
                        } else if (current == null) {
                            simklEntriesToSync.add(mergedEntry)
                        } else {
                            val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore))
                            if (needsUpdate) {
                                simklEntriesToSync.add(mergedEntry.copy(simklId = current.simklId))
                            }
                        }
                    }

                    // ── 4. Kitsu Eşitleme (Anime & Manga) ──
                    if (isKitsu && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.kitsu
                        // Kitsu sentetik kimlik aralığı 300M–400M; 400M+ Shikimori'ye ait, yanlışlıkla Kitsu ID sanılmasın.
                        val knownKitsuId = current?.malId?.takeIf { it in 300_000_001..399_999_999 }?.let { it - 300_000_000 }
                        if (current == null && !additionsAllowed) {
                            recordIdentitySkip("Kitsu", mergedEntry.title)
                        } else if (current == null) {
                            // Kitsu'da eksik -> EKLE! (hız sınırı artık KitsuApiClient içinde her istek için uygulanıyor)
                            val outcome = runSyncCatching {
                                KitsuSyncManager.syncEntryToKitsu(context, mergedEntry, knownKitsuMediaId = null)
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
                            val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore))
                            if (needsUpdate) {
                                val outcome = runSyncCatching {
                                    KitsuSyncManager.syncEntryToKitsu(context, mergedEntry, knownKitsuMediaId = knownKitsuId)
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
                        if (realMalId != null && item.shikimori == null && !additionsAllowed) {
                            recordIdentitySkip("Shikimori", mergedEntry.title)
                        } else if (realMalId != null) {
                            val current = item.shikimori
                            if (current == null) {
                                // Shikimori'de eksik -> EKLE! (kimlik güvencesinden geçti)
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("shikimori")
                                val target = mergedEntry.copy(malId = guardedMalId ?: realMalId)
                                val outcome = runSyncCatching { ShikimoriSyncManager.syncEntryToShikimori(context, target, knownMalId = realMalId) }
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
                                val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                        (current.progress < maxProgress) ||
                                        (bestScore != null && current.score != bestScore))
                                if (needsUpdate) {
                                    com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("shikimori")
                                    val target = mergedEntry.copy(malId = realMalId)
                                    val outcome = runSyncCatching { ShikimoriSyncManager.syncEntryToShikimori(context, target, knownMalId = realMalId) }
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
                        if (current == null && !additionsAllowed) {
                            recordIdentitySkip("Bangumi", mergedEntry.title)
                        } else if (current == null) {
                            // Bangumi'de eksik -> EKLE! (kimlik güvencesinden geçti)
                            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("bangumi")
                            val target = mergedEntry.copy(malId = guardedMalId ?: realMalId)
                            val outcome = runSyncCatching { BangumiSyncManager.syncEntryToBangumi(context, target) }
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
                            val needsUpdate = !singleSourceGroup && (
                                (current.status != bestStatus) ||
                                (current.progress < maxProgress) ||
                                (current.volumeProgress < maxVolumeProgress) ||
                                (bestScore != null && current.score != bestScore)
                            )
                            if (needsUpdate) {
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("bangumi")
                                val target = mergedEntry.copy(malId = current.malId ?: (guardedMalId ?: realMalId))
                                val outcome = runSyncCatching { BangumiSyncManager.syncEntryToBangumi(context, target) }
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
                        .onFailure { logEvent("AniList", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isMal) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "MyAnimeList"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("mal", MalImportManager.fetchAllLists(malToken!!, showAdult), allowDelete = false) }
                        .onFailure { logEvent("MyAnimeList", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isKitsu) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "Kitsu"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("kitsu", KitsuImportManager.fetchAllLists(context, kitsuToken!!, kitsuUserId!!), allowDelete = false) }
                        .onFailure { logEvent("Kitsu", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isShikimori) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "Shikimori"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("shikimori", ShikimoriImportManager.fetchAllLists(context, shikimoriToken!!, shikimoriUserId!!), allowDelete = false) }
                        .onFailure { logEvent("Shikimori", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isSimkl) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "Simkl"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    val refreshToken = ExternalAuthManager.getSimklToken(context) ?: error(context.getString(R.string.cross_sync_simkl_disconnected))
                    runSyncCatching { repository.smartImport("simkl", SimklImportManager.fetchAllLists(refreshToken), allowDelete = false) }
                        .onFailure { logEvent("Simkl", context.getString(R.string.cross_sync_log_local_update_failed, it.message ?: context.getString(R.string.cross_sync_vm_unknown_error)), isError = true, details = it.crossSyncDiagnostic(context)) }
                }
                if (isBangumi) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress(context.getString(R.string.cross_sync_vm_verifying_platform_list, "Bangumi"), context.getString(R.string.cross_sync_vm_latest_server_list), processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("bangumi", BangumiImportManager.fetchAllLists(context, bangumiToken!!), allowDelete = false) }
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

    fun importKitsuList(repository: MediaEntryRepository) {
        if (isKitsuImportRunning) return
        isKitsuImportRunning = true
        onShowMessage?.invoke(context.getString(R.string.account_sync_import_fetching, "Kitsu"))

        viewModelScope.launch(Dispatchers.IO) {
            val token = ExternalAuthManager.getKitsuToken(context)
            val userId = ExternalAuthManager.getKitsuUserId(context)

            if (token.isNullOrBlank() || userId.isNullOrBlank()) {
                onShowMessage?.invoke(context.getString(R.string.account_sync_import_credentials_missing, "Kitsu"))
                isKitsuImportRunning = false
                return@launch
            }

            runSyncCatching {
                runSyncCatching {
                    val profile = KitsuImportManager.fetchUserProfile(token)
                    SettingsDataStore(context).saveKitsuProfileInfo(profile.name, profile.avatarUrl)
                }
                KitsuImportManager.fetchAllLists(context, token, userId)
            }.onSuccess { importedEntries ->
                val importResult = runCatching {
                    repository.smartImport("kitsu", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    // İçe aktarma başarılı: kaynağın hata durumu temizlenir (v2.4.341).
                    SourceSyncStatus.reportSuccess(context, SourceSyncStatus.Source.KITSU)
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_success, importedEntries.size, "Kitsu"))
                } else {
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_save_failed, "Kitsu", importResult.exceptionOrNull()?.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }.onFailure { error ->
                onShowMessage?.invoke(importFailureMessage(SourceSyncStatus.Source.KITSU, error))
            }

            isKitsuImportRunning = false
        }
    }

    fun importShikimoriList(repository: MediaEntryRepository) {
        if (isShikimoriImportRunning) return
        isShikimoriImportRunning = true
        onShowMessage?.invoke(context.getString(R.string.account_sync_import_fetching, "Shikimori"))

        viewModelScope.launch(Dispatchers.IO) {
            val token = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            var userId = ExternalAuthManager.getShikimoriUserId(context)
            if (!token.isNullOrBlank() && userId == null &&
                ExternalAuthManager.ensureShikimoriUserResolved(context)
            ) {
                userId = ExternalAuthManager.getShikimoriUserId(context)
            }

            if (token.isNullOrBlank() || userId == null) {
                onShowMessage?.invoke(context.getString(R.string.account_sync_import_credentials_missing, "Shikimori"))
                isShikimoriImportRunning = false
                return@launch
            }

            runSyncCatching {
                runSyncCatching {
                    val profile = ShikimoriImportManager.fetchUserProfile(token)
                    SettingsDataStore(context).saveShikimoriProfileInfo(profile.nickname, profile.avatarUrl)
                }
                ShikimoriImportManager.fetchAllLists(context, token, userId)
            }.onSuccess { importedEntries ->
                val importResult = runCatching {
                    repository.smartImport("shikimori", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    // İçe aktarma başarılı: kaynağın hata durumu temizlenir (v2.4.341).
                    SourceSyncStatus.reportSuccess(context, SourceSyncStatus.Source.SHIKIMORI)
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_success, importedEntries.size, "Shikimori"))
                } else {
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_save_failed, "Shikimori", importResult.exceptionOrNull()?.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }.onFailure { error ->
                onShowMessage?.invoke(importFailureMessage(SourceSyncStatus.Source.SHIKIMORI, error))
            }

            isShikimoriImportRunning = false
        }
    }

    fun importKitsuList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) = importKitsuList(repository)

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

    /** Bangumi koleksiyonunu (想看/在看/看过) Kitsugi kütüphanesine içe aktarır. */
    fun importBangumiList(repository: MediaEntryRepository) {
        if (isBangumiImportRunning) return
        isBangumiImportRunning = true
        onShowMessage?.invoke(context.getString(R.string.account_sync_import_fetching, "Bangumi"))

        viewModelScope.launch(Dispatchers.IO) {
            val token = BangumiAuthStore.getValidToken(context)
            if (token.isNullOrBlank()) {
                onShowMessage?.invoke(context.getString(R.string.account_sync_import_login_required, "Bangumi"))
                isBangumiImportRunning = false
                return@launch
            }
            BangumiAuthStore.ensureUserResolved(context)

            runSyncCatching {
                BangumiImportManager.fetchAllLists(context, token)
            }.onSuccess { importedEntries ->
                val importResult = runCatching {
                    repository.smartImport("bangumi", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    // İçe aktarma başarılı: kaynağın hata durumu temizlenir (v2.4.341).
                    SourceSyncStatus.reportSuccess(context, SourceSyncStatus.Source.BANGUMI)
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_success, importedEntries.size, "Bangumi"))
                    // Koleksiyon satırları yalnızca özgün (Japonca/Çince) adla gelir.
                    // Aktarım biter bitmez Latin/İngilizce adları tamamla; aksi halde
                    // tarama yalnızca Listem ViewModel'i yaratıldığında çalıştığı için
                    // yeni satırlar uygulama kapanıp açılana dek CJK adla kalıyordu.
                    launchBangumiTitleHydration()
                } else {
                    onShowMessage?.invoke(
                        context.getString(R.string.account_sync_import_save_failed, "Bangumi", importResult.exceptionOrNull()?.message ?: context.getString(R.string.account_sync_unknown_error))
                    )
                }
            }.onFailure { error ->
                onShowMessage?.invoke(importFailureMessage(SourceSyncStatus.Source.BANGUMI, error))
            }

            isBangumiImportRunning = false
        }
    }

    fun importBangumiList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) = importBangumiList(repository)


    fun importShikimoriList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) = importShikimoriList(repository)

    fun importSimklList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) {
        val token = ExternalAuthManager.getSimklToken(context)

        if (token.isNullOrBlank()) {
            onShowMessage?.invoke(context.getString(R.string.account_sync_token_missing, "Simkl"))
            return
        }

        if (isSimklImportRunning) {
            onShowMessage?.invoke(context.getString(R.string.account_sync_import_running, "Simkl"))
            return
        }

        isSimklImportRunning = true
        onShowMessage?.invoke(context.getString(R.string.account_sync_import_fetching, "Simkl"))

        viewModelScope.launch(Dispatchers.IO) {
            val settingsDataStore = SettingsDataStore(context)

            runSyncCatching {
                runSyncCatching {
                    val profile = SimklImportManager.fetchUserProfile(token)
                    settingsDataStore.setSimklProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.avatarUrl.orEmpty(),
                        bannerImageUri = ""
                    )
                }
                SimklImportManager.fetchAllLists(token)
            }.onSuccess { importedEntries ->
                val importResult = runCatching {
                    repository.smartImport("simkl", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    // İçe aktarma başarılı: kaynağın hata durumu temizlenir (v2.4.341).
                    SourceSyncStatus.reportSuccess(context, SourceSyncStatus.Source.SIMKL)
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_success, importedEntries.size, "Simkl"))
                } else {
                    onShowMessage?.invoke(context.getString(R.string.account_sync_import_save_failed, "Simkl", importResult.exceptionOrNull()?.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }.onFailure { error ->
                onShowMessage?.invoke(
                    importFailureMessage(SourceSyncStatus.Source.SIMKL, error)
                )
            }

            isSimklImportRunning = false
        }
    }
}
