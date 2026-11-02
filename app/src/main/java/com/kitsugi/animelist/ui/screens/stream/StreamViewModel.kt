package com.kitsugi.animelist.ui.screens.stream

import android.app.Application
import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsPluginTypeCompatibility
import com.kitsugi.animelist.data.cloudstream.CsOwnedPluginProfiles
import com.kitsugi.animelist.data.cloudstream.CloudStreamErrorClassifier
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.cloudstream.CloudflareBlockException
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.remote.AddonStreamClient
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.data.remote.buildStremioStreamTasks
import com.kitsugi.animelist.data.remote.toStreamRoute
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.repository.EpisodeStreamFilter
import com.kitsugi.animelist.data.repository.StreamSorter
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.screens.fullscreen.components.MetaCastMember
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.UnsafeSSL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.data.cloudstream.diag.CsTrace
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * ViewModel for the stream picker screen.
 *
 * All network fetch work lives here so that it survives:
 *  - the app being sent to the background
 *  - screen rotation / configuration changes
 *  - navigating away and back (within the same Activity back-stack)
 *
 * A "fetch key" tracks which (malId, aniListId, episode, season) combination
 * is currently loaded. [startFetch] is a no-op if the same combination is
 * already fetched or in progress, so the UI can call it safely on every
 * recomposition without triggering a redundant scan.
 */
class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val TAG = "StreamViewModel"

    // ── Public state ──────────────────────────────────────────────────────────

    private val _addonStates = MutableStateFlow<List<AddonFetchState>>(emptyList())
    val addonStates: StateFlow<List<AddonFetchState>> = _addonStates.asStateFlow()

    private val _isResolvingId = MutableStateFlow(false)
    val isResolvingId: StateFlow<Boolean> = _isResolvingId.asStateFlow()

    private val _idResolveFailed = MutableStateFlow(false)
    val idResolveFailed: StateFlow<Boolean> = _idResolveFailed.asStateFlow()

    private val _imdbId = MutableStateFlow<String?>(null)
    val imdbId: StateFlow<String?> = _imdbId.asStateFlow()

    /** Non-null when a Cloudflare WebView dialog should be shown. */
    private val _webViewDialogState = MutableStateFlow<WebViewDialogState?>(null)
    val webViewDialogState: StateFlow<WebViewDialogState?> = _webViewDialogState.asStateFlow()

    /**
     * Kullanıcı bir eklentinin KENDİ sayfasından (keşfet/arama → eklenti detayı → İzle ya da
     * bölüm listesi) geldiyse arama yalnızca o eklentide kalır ve burada o eklentinin adı
     * tutulur. Null = ortak (tüm kaynaklar) arama.
     *
     * Ekran bu değeri kullanıcıya "Yalnızca ⚡ X eklentisi arandı" bilgisi + "Tüm kaynaklarda
     * ara" düğmesi olarak gösterir. Düğme [searchAllSources] çağırır.
     */
    private val _scopedSourceName = MutableStateFlow<String?>(null)
    val scopedSourceName: StateFlow<String?> = _scopedSourceName.asStateFlow()

    // ── Internal guards ───────────────────────────────────────────────────────

    /**
     * Identifies the active fetch. Format: "malId:aniListId:season:episode".
     * Null = nothing fetched yet.
     */
    @Volatile private var currentFetchKey: String? = null

    /** Son [startFetch] parametreleri — tek eklenti yeniden denemesi ([retryPlugin]) için. */
    private data class LastFetchParams(
        val malId: Int?,
        val aniListId: Int?,
        val tmdbId: Int?,
        val bangumiId: Int?,
        val simklId: Int?,
        val kitsuId: Int?,
        val episode: Int,
        val season: Int,
        val title: String,
        val alternativeTitles: List<String>,
        val startYear: Int?,
        val isMovie: Boolean,
        val mediaType: MediaType?,
        val knownImdbId: String? = null,
        val cs3Url: String? = null,
        val cs3ApiName: String? = null,
        val allSources: Boolean = false,
    )

    @Volatile private var lastFetchParams: LastFetchParams? = null

    /** Whether a fetch coroutine is currently running for [currentFetchKey]. */
    @Volatile private var isFetchInProgress = false

    private val stateUpdateMutex = Mutex()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts a stream fetch for the given parameters.
     *
     * Safe to call on every recomposition – returns immediately when the same
     * key is already loaded or a fetch is already in progress.
     */
    fun startFetch(
        malId: Int?,
        aniListId: Int?,
        tmdbId: Int? = null,
        bangumiId: Int? = null,
        simklId: Int? = null,
        kitsuId: Int? = null,
        episode: Int,
        season: Int,
        title: String,
        alternativeTitles: List<String>,
        startYear: Int?,
        cs3Url: String? = null,
        cs3ApiName: String? = null,
        isMovie: Boolean = false,
        mediaType: MediaType? = null,
        knownImdbId: String? = null,
        allSources: Boolean = false
    ) {
        val newKey = "$malId:$aniListId:$tmdbId:$bangumiId:$simklId:$kitsuId:$isMovie:${mediaType?.name}:$season:$episode:$startYear:$cs3ApiName:$cs3Url:${title.hashCode()}:${alternativeTitles.hashCode()}:$knownImdbId:$allSources"

        lastFetchParams = LastFetchParams(
            malId = malId, aniListId = aniListId, tmdbId = tmdbId, bangumiId = bangumiId, simklId = simklId, kitsuId = kitsuId,
            episode = episode, season = season, title = title,
            alternativeTitles = alternativeTitles, startYear = startYear,
            isMovie = isMovie, mediaType = mediaType,
            knownImdbId = knownImdbId,
            cs3Url = cs3Url, cs3ApiName = cs3ApiName,
            allSources = allSources,
        )

        // ── Cache hit: same combination, data already present ─────────────────
        if (newKey == currentFetchKey) {
            Log.d(TAG, "startFetch: cache hit for key=$newKey — skipping")
            return
        }

        // ── New combination: reset everything and start fresh ─────────────────
        Log.d(TAG, "startFetch: new key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        isFetchInProgress = true

        // Clear per-session plugin blocklist so every new anime retries all plugins
        CsPluginStatusTracker.clear()
        // Clear "unsupported method" cache — önceki animeden kalan NotImplementedError işaretleri
        // temizlenmezse o eklentinin search() metodu bir sonraki anime için hiç denenmez (0 sonuç).
        CsStreamRunner.clearUnsupportedMethodsCache()

        _addonStates.value = emptyList()
        _isResolvingId.value = true
        _idResolveFailed.value = false
        _imdbId.value = null

        viewModelScope.launch {
            fetchStreams(
                malId = malId,
                aniListId = aniListId,
                tmdbId = tmdbId,
                bangumiId = bangumiId,
                simklId = simklId,
                kitsuId = kitsuId,
                episode = episode,
                season = season,
                title = title,
                alternativeTitles = alternativeTitles,
                startYear = startYear,
                cs3Url = cs3Url,
                cs3ApiName = cs3ApiName,
                isMovie = isMovie,
                mediaType = mediaType,
                knownImdbId = knownImdbId,
                allSources = allSources
            )
            isFetchInProgress = false
        }
    }

    /**
     * "Tüm kaynaklarda ara" — eklentiye özel kapsamı bırakır ve son [startFetch]
     * parametreleriyle ortak aramayı (Stremio addon'ları + tüm CS eklentileri) çalıştırır.
     *
     * NEDEN: Kullanıcı bir eklentinin kendi sayfasından "İzle" dediğinde arama o eklentide
     * kalmalı; diğer kaynaklar kendiliğinden devreye girince liste karışıyordu. Genişletme
     * artık kullanıcının açık tercihi.
     */
    fun searchAllSources() {
        val params = lastFetchParams ?: run {
            Log.w(TAG, "searchAllSources: Önceki fetch parametresi yok — genişletme atlandı")
            return
        }
        CsTrace.info(_scopedSourceName.value ?: "kaynak", "scope", "Kullanıcı tüm kaynaklarda aramayı seçti")
        // Aynı kombinasyonun önbellek anahtarını geçersiz kıl ki ortak arama gerçekten koşsun.
        currentFetchKey = null
        startFetch(
            malId = params.malId,
            aniListId = params.aniListId,
            tmdbId = params.tmdbId,
            bangumiId = params.bangumiId,
            simklId = params.simklId,
            kitsuId = params.kitsuId,
            episode = params.episode,
            season = params.season,
            title = params.title,
            alternativeTitles = params.alternativeTitles,
            startYear = params.startYear,
            cs3Url = null,
            cs3ApiName = null,
            isMovie = params.isMovie,
            mediaType = params.mediaType,
            knownImdbId = params.knownImdbId,
            allSources = true
        )
    }

    /**
     * TÜM kaynakları baştan arar (kaynak panelindeki "Yeniden ara").
     *
     * NEDEN GEREKLİ: [startFetch] aynı anahtar için "cache hit" döndürüp hiçbir şey
     * yapmıyor; tek eklenti yeniden denemesi ([retryPlugin]) ise yalnızca CloudStream
     * eklentileri için çalışıyor. Tüm kaynaklar hata verdiğinde ya da boş döndüğünde
     * kullanıcının ekrandan tam bir yeniden arama tetikleme yolu yoktu.
     *
     * Anahtar sıfırlanır, eklenti engel defteri ve "desteklenmeyen metot" önbelleği
     * temizlenir; böylece önceki animeden kalan negatif izler yeni aramayı zehirlemez.
     */
    fun refreshAll() {
        val params = lastFetchParams ?: run {
            Log.w(TAG, "refreshAll: Önceki fetch parametresi yok — yeniden arama atlandı")
            return
        }
        if (isFetchInProgress) {
            Log.d(TAG, "refreshAll: arama sürüyor — yinelenen istek atlandı")
            return
        }
        // Cache anahtarını geçersiz kıl: aşağıdaki startFetch artık "cache hit" saymaz.
        currentFetchKey = null
        CsPluginStatusTracker.clear()
        CsStreamRunner.clearUnsupportedMethodsCache()
        startFetch(
            malId = params.malId,
            aniListId = params.aniListId,
            tmdbId = params.tmdbId,
            bangumiId = params.bangumiId,
            simklId = params.simklId,
            kitsuId = params.kitsuId,
            episode = params.episode,
            season = params.season,
            title = params.title,
            alternativeTitles = params.alternativeTitles,
            startYear = params.startYear,
            cs3Url = params.cs3Url,
            cs3ApiName = params.cs3ApiName,
            isMovie = params.isMovie,
            mediaType = params.mediaType,
            knownImdbId = params.knownImdbId,
        )
    }

    /**
     * Dismisses the WebView captcha dialog and retries the affected plugin.
     */
    fun onWebViewDismissed(
        dismissedPluginId: String,
        dismissedDisplayName: String,
        title: String,
        alternativeTitles: List<String>,
        startYear: Int?,
        season: Int,
        episode: Int,
        malId: Int? = null,
        aniListId: Int? = null,
        tmdbId: Int? = null,
        bangumiId: Int? = null,
        simklId: Int? = null,
        kitsuId: Int? = null,
        isMovie: Boolean = false,
        mediaType: MediaType? = null
    ) {
        CookieManager.getInstance().flush()
        _webViewDialogState.value = null
        rerunCsPlugin(
            pluginMatcher = { it.id == dismissedPluginId },
            displayName = dismissedDisplayName,
            params = LastFetchParams(
                malId = malId, aniListId = aniListId, tmdbId = tmdbId, bangumiId = bangumiId, simklId = simklId, kitsuId = kitsuId,
                episode = episode, season = season, title = title,
                alternativeTitles = alternativeTitles, startYear = startYear,
                isMovie = isMovie, mediaType = mediaType,
                knownImdbId = lastFetchParams?.knownImdbId,
            ),
        )
    }

    /**
     * v2.4.256: Tek bir CloudStream eklentisini GERÇEKTEN yeniden çalıştırır.
     *
     * Eskiden hata kartında yalnızca CF/WAF için "Doğrula" vardı. Zaman aşımı, DNS veya
     * "akış bulunamadı" gibi durumlarda ekran, aynı bölüm açık kaldığı sürece (cache hit)
     * son kaydı göstermeye devam ediyordu; kullanıcı yeni bir istek tetikleyemiyordu.
     * Bu metot eklentinin tracker kaydını (blok, hata mesajı, hata kodu) siler ve son
     * [startFetch] parametreleriyle yalnızca o eklentiyi baştan dener.
     */
    fun retryPlugin(addonDisplayName: String) {
        val params = lastFetchParams ?: run {
            Log.w(TAG, "retryPlugin: Önceki fetch parametresi yok — yeniden deneme atlandı ($addonDisplayName)")
            return
        }
        val pluginName = addonDisplayName.removePrefix("⚡ ").trim()
        CsTrace.info(pluginName, "retry", "Kullanıcı yeniden deneme istedi")
        rerunCsPlugin(
            pluginMatcher = { it.name.equals(pluginName, ignoreCase = true) },
            displayName = addonDisplayName,
            params = params,
        )
    }

    /**
     * CS eklentisi API döndürmediğinde gerçek yükleme hatasını ([CsPluginLoader.takeLastLoadError])
     * kullanıcıya gösterir. Eskiden bu neden hiç okunmadığı için her hata aynı jenerik mesaja düşüyordu.
     */
    private fun csLoadFailureMessage(scraperId: String): String {
        val cause = CsPluginLoader.takeLastLoadError(scraperId)
        return if (cause != null) "Eklenti yüklenemedi: $cause" else "Eklenti yüklenemedi (DEX/manifest hatası)"
    }

    private fun rerunCsPlugin(
        pluginMatcher: (com.kitsugi.animelist.data.local.CsPluginEntity) -> Boolean,
        displayName: String,
        params: LastFetchParams,
    ) {
        val dismissedDisplayName = displayName
        val title = params.title
        val alternativeTitles = params.alternativeTitles
        val startYear = params.startYear
        val season = params.season
        val episode = params.episode
        val malId = params.malId
        val aniListId = params.aniListId
        val tmdbId = params.tmdbId
        val bangumiId = params.bangumiId
        val simklId = params.simklId
        val kitsuId = params.kitsuId
        val isMovie = params.isMovie
        val mediaType = params.mediaType

        // Reset addon state to "loading" so the user sees a spinner
        updateAddonStateSync(dismissedDisplayName, isLoading = true, streams = emptyList(), error = null)

        viewModelScope.launch {
            val db = KitsugiDatabase.getDatabase(context)
            val csPlugin = db.csPluginDao().getEnabledPlugins()
                .firstOrNull(pluginMatcher) ?: run {
                    updateAddonStateSync(dismissedDisplayName, isLoading = false, error = "Eklenti bulunamadı veya devre dışı")
                    return@launch
                }
            // Eski hata/blok kaydı yeni denemede "anında" geri gösterilmesin.
            CsPluginStatusTracker.clearPluginStatus(csPlugin.id)
            CsPluginStatusTracker.clearPluginStatus(csPlugin.name)
            try {
                val apis = withContext(Dispatchers.IO) {
                    CsPluginLoader.loadExtension(context, csPlugin.id)
                }
                if (apis.isEmpty()) {
                    CsTrace.error(csPlugin.name, "load", "Yeniden deneme: yükleme başarısız")
                    updateAddonStateSync(
                        dismissedDisplayName,
                        isLoading = false,
                        error = csLoadFailureMessage(csPlugin.id),
                    )
                    return@launch
                }
                // Tracker anahtarları api.name'le tutulur — yalnızca plugin.id temizlemek
                // "Doğrula" sonrası tekrar denemede eklentinin hâlâ bloklu sanılmasına
                // yol açıyordu. Bu yüzden id, eklenti adı ve yüklenen tüm api adları temizlenir.
                CsPluginStatusTracker.clearPluginStatus(csPlugin.id)
                CsPluginStatusTracker.clearPluginStatus(csPlugin.name)
                apis.forEach { CsPluginStatusTracker.clearPluginStatus(it.name) }
                val compatibleApis = apis.filter { api ->
                    CsOwnedPluginProfiles.supportsRequest(
                        providerName = api.name,
                        pluginVersion = CsPluginLoader.manifestVersionForSource(api.sourcePlugin),
                        mediaType = mediaType,
                        isMovie = isMovie,
                        supportedTypes = api.supportedTypes.map { it.name }
                    )
                }
                if (compatibleApis.isEmpty()) {
                    val profileReason = apis.asSequence().mapNotNull { api ->
                        CsOwnedPluginProfiles.episodeUnsupportedReason(
                            providerName = api.name,
                            pluginVersion = CsPluginLoader.manifestVersionForSource(api.sourcePlugin),
                            mediaType = mediaType,
                            isMovie = isMovie
                        )
                    }.firstOrNull()
                    val message = profileReason ?: mediaType?.let {
                        "Bu eklenti ${CsPluginTypeCompatibility.description(it)} içeriklerini desteklemiyor."
                    } ?: "Bu eklenti bu içerik türünü desteklemiyor."
                    updateAddonStateSync(dismissedDisplayName, isLoading = false, error = message)
                    return@launch
                }
                // Yeniden deneme de ana akışla AYNI kimlik dönüşümünü kullanır: ham stableId
                // (Kitsu/AniList offset'li) CS'ye MAL/AniList olarak gitmez, çözülmüş kimlikler geçer.
                val retryMal = malId?.takeIf { it in 1..99_999_999 }
                val retryAniList = when {
                    aniListId != null && aniListId >= 100_000_000 -> aniListId - 100_000_000
                    aniListId != null && aniListId > 0 -> aniListId
                    else -> null
                }
                val retryKitsu = kitsuId ?: malId?.takeIf { it >= 300_000_000 }?.let { it - 300_000_000 }
                val retryIdsBase = KitsugiIdResolver.resolveIds(
                    malId = retryMal,
                    aniListId = retryAniList,
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    kitsuId = retryKitsu,
                    bangumiId = bangumiId,
                    wikidataImdbFallback = true
                )
                val retryKnownImdb = params.knownImdbId?.trim()?.takeIf { Regex("tt[0-9]{5,12}").matches(it) }
                val retryIds = if (retryKnownImdb != null) retryIdsBase.copy(imdbId = retryKnownImdb) else retryIdsBase
                val csStreams = mutableListOf<StreamSource>()
                for (api in compatibleApis) {
                    val streams = CsStreamRunner.getStreams(
                        api = api, title = title, alternativeTitles = alternativeTitles,
                        year = startYear, season = season, episode = episode,
                        malId = retryMal ?: retryIds.malId,
                        aniListId = retryAniList ?: retryIds.aniListId,
                        tmdbId = tmdbId ?: retryIds.tmdbId,
                        kitsuId = retryKitsu,
                        bangumiId = bangumiId, simklId = simklId, isMovie = isMovie, mediaType = mediaType,
                        preResolvedIds = retryIds
                    )
                    csStreams.addAll(streams)
                }
                val apiName = apis.firstOrNull()?.name ?: csPlugin.name
                val diagMsg = if (csStreams.isEmpty()) {
                    CsPluginStatusTracker.getErrorMessage(csPlugin.name)
                        ?: CsPluginStatusTracker.getErrorMessage(apiName)
                        ?: if (apis.isEmpty()) csLoadFailureMessage(csPlugin.id) else null
                } else null
                val diagCode = if (csStreams.isEmpty()) {
                    CsPluginStatusTracker.getErrorCode(csPlugin.name)
                        ?: CsPluginStatusTracker.getErrorCode(apiName)
                        ?: if (apis.isEmpty()) PlatformErrorCode.CloudStreamPluginUnsupported.id
                        else PlatformErrorCode.CloudStreamNoStreams.id
                } else null
                updateAddonStateSync(
                    dismissedDisplayName,
                    isLoading = false,
                    streams = csStreams,
                    error = if (csStreams.isEmpty()) (diagMsg?.let { "⚠️ $it" } ?: "Bu içerik için akış bulunamadı") else null,
                    errorCode = diagCode,
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                updateAddonStateSync(
                    dismissedDisplayName,
                    isLoading = false,
                    error = "Hata: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    errorCode = CloudStreamErrorClassifier.classify(e).id,
                )
            }
        }
    }

    /**
     * Resolves the Cloudflare-gated plugin URL and signals the UI to open a WebView.
     */
    fun onVerifyPlugin(addonDisplayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = KitsugiDatabase.getDatabase(context)
            val allCsPlugins = db.csPluginDao().getEnabledPlugins()
            val pluginName = addonDisplayName.removePrefix("⚡ ").trim()
            val plugin = allCsPlugins.firstOrNull { it.name.equals(pluginName, ignoreCase = true) }
                ?: return@launch

            val cs3File = java.io.File(context.filesDir, "cs_extensions/${plugin.id}.cs3")
            if (!CsPluginLoader.loadedPlugins.containsKey(cs3File.absolutePath)) {
                CsPluginLoader.loadExtension(context, plugin.id)
            }
            val api = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull {
                it.sourcePlugin == cs3File.absolutePath ||
                    it.name.equals(pluginName, ignoreCase = true)
            }
            val url = api?.mainUrl
            if (url.isNullOrBlank()) {
                Log.w(TAG, "onVerifyPlugin: Provider not found for $pluginName")
                return@launch
            }
            withContext(Dispatchers.Main) {
                _webViewDialogState.value = WebViewDialogState(
                    url = url,
                    displayName = addonDisplayName,
                    pluginId = plugin.id
                )
            }
        }
    }

    // ── Core fetch ────────────────────────────────────────────────────────────

    private suspend fun fetchStreams(
        malId: Int?,
        aniListId: Int?,
        tmdbId: Int? = null,
        bangumiId: Int? = null,
        simklId: Int? = null,
        kitsuId: Int? = null,
        episode: Int,
        season: Int,
        title: String,
        alternativeTitles: List<String>,
        startYear: Int?,
        cs3Url: String? = null,
        cs3ApiName: String? = null,
        isMovie: Boolean = false,
        mediaType: MediaType? = null,
        knownImdbId: String? = null,
        allSources: Boolean = false
    ) {
        val directUrl = cs3Url?.trim()?.takeIf { it.isNotBlank() }
        val directApiName = cs3ApiName?.trim()?.takeIf { it.isNotBlank() }

        // ── 0. TEK EKLENTİ (KAPSAMLI) MOD ─────────────────────────────────────
        // Kullanıcı bir eklentinin kendi sayfasından "İzle"/bölüm seçtiyse kaynak YALNIZCA
        // o eklentiden gelir: önce doğrudan URL, olmazsa eklentinin KENDİ başlık araması.
        // Diğer eklentiler ve Stremio addon'ları kendiliğinden devreye GİRMEZ; genişletme
        // kullanıcının "Tüm kaynaklarda ara" tercihine bırakılır ([searchAllSources]).
        if (directApiName != null && StreamSourceScope.useSingleSource(directApiName, allSources)) {
            _isResolvingId.value = false
            val found = runScopedPluginFetch(
                apiName = directApiName,
                directUrl = directUrl,
                malId = malId,
                aniListId = aniListId,
                tmdbId = tmdbId,
                bangumiId = bangumiId,
                simklId = simklId,
                kitsuId = kitsuId,
                episode = episode,
                season = season,
                title = title,
                alternativeTitles = alternativeTitles,
                startYear = startYear,
                isMovie = isMovie,
                mediaType = mediaType,
                knownImdbId = knownImdbId
            )
            Log.i(
                TAG,
                "Tek eklenti modu ($directApiName): ${if (found) "kaynak bulundu" else "kaynak yok"} — diğer kaynaklara geçilmedi"
            )
            return
        }
        // Kapsam dışı (tüm kaynaklar): ekranın "tek eklenti" şeridi kapanır. Bu yol hem
        // kullanıcının "Tüm kaynaklarda ara" tercihinde hem de manuel aramada seçilen
        // eklentisiz bir sonuçta çalışır — her iki durumda da liste artık ortaktır.
        if (_scopedSourceName.value != null) {
            _scopedSourceName.value = null
            Log.i(TAG, "Tüm kaynaklarda arama modu — tek eklenti kapsamı kapatıldı")
        }

        // ── 1. Resolve IDs ────────────────────────────────────────────────────
        val realAniListId = when {
            aniListId != null && aniListId >= 100_000_000 -> aniListId - 100_000_000
            aniListId != null && aniListId > 0 -> aniListId
            else -> null
        }

        // Kitsu stableId offset: 300_000_000 + kitsuId
        // AniList stableId offset: 100_000_000 + aniListId
        // MAL ID ise offset'siz doğrudan değer
        val kitsuNumericId: Int? = kitsuId?.takeIf { it > 0 } ?: when {
            malId != null && malId >= 300_000_000 -> malId - 300_000_000
            else -> null
        }
        val realMalId: Int? = when {
            malId != null && malId >= 300_000_000 -> null  // Kitsu kaynaklı — MAL ID değil
            malId != null && malId >= 100_000_000 -> null  // AniList offset — MAL ID değil
            malId != null && malId > 0 -> malId
            else -> null
        }

        // Detay sayfasının bildiği IMDb (tt...) çözücü sonucundan önce gelir: aynı kayıt, güvenilir kaynak.
        val knownImdb = knownImdbId?.trim()?.takeIf { Regex("tt[0-9]{5,12}").matches(it) }
        val resolvedIds = KitsugiIdResolver.resolveIds(
            malId = realMalId,
            aniListId = realAniListId,
            tmdbId = tmdbId,
            mediaType = mediaType,
            kitsuId = kitsuNumericId,
            bangumiId = bangumiId,
            // Akış yolu: IMDb yoksa Wikidata P345 köprüsünü dene (IMDb ile sorgulanan addon'lar için).
            wikidataImdbFallback = true
        ).let { r -> if (knownImdb != null) r.copy(imdbId = knownImdb) else r }
        _isResolvingId.value = false

        val resolvedImdbId = resolvedIds.imdbId
        val resolvedKitsuId = resolvedIds.kitsuId ?: kitsuNumericId
        val hasAnyRoutedIdentity = !resolvedImdbId.isNullOrBlank() || resolvedKitsuId != null ||
            resolvedIds.tmdbId != null || resolvedIds.aniListId != null || resolvedIds.malId != null ||
            resolvedIds.tvdbId != null || simklId != null || bangumiId != null
        if (!hasAnyRoutedIdentity) {
            // ID çözümlenemedi — yine de CS plugin'leri başlık bazlı aramaya devam edecek.
            // idResolveFailed sadece bilgilendirme amaçlı set ediliyor, bloklamıyor.
            Log.w(TAG, "ID çözümleme başarısız — title-based fallback ile devam ediliyor. title='$title'")
            _idResolveFailed.value = true
        }
        _imdbId.value = resolvedImdbId

        val stremioLookup = com.kitsugi.animelist.data.remote.StremioVideoId.forContent(
            isMovie = isMovie,
            imdbId = resolvedImdbId,
            kitsuId = resolvedKitsuId,
            season = season,
            episode = episode,
            tmdbId = resolvedIds.tmdbId ?: tmdbId,
            aniListId = resolvedIds.aniListId ?: realAniListId,
            malId = resolvedIds.malId ?: realMalId,
            tvdbId = resolvedIds.tvdbId,
            bangumiId = bangumiId,
            simklId = simklId
        )
        val imdbVideoId = stremioLookup.imdbVideoId
        val kitsuVideoId = stremioLookup.kitsuVideoId
        val additionalVideoIds = stremioLookup.additionalVideoIds
        val contentType = stremioLookup.contentType

        // ── 2. Stremio görevleri (ortak yönlendirme kuralları: StremioStreamRouting) ──────────
        // Her addon yalnızca kabul ettiği kimlik ailesiyle sorgulanır; istek yolu addon'un
        // ilan ettiği türü kullanır (anime-only addon → /stream/anime/...).
        val db = KitsugiDatabase.getDatabase(context)
        val addonClient = AddonStreamClient()
        val allAddons = db.managedAddonDao().getEnabledAddons()
        val tasks = buildStremioStreamTasks(
            routes = allAddons.map { it.toStreamRoute() },
            contentType = contentType,
            imdbVideoId = imdbVideoId,
            kitsuVideoId = kitsuVideoId,
            additionalVideoIds = additionalVideoIds
        )

        if (tasks.isNotEmpty()) {
            val uniqueAddons = tasks.map { it.addonName }.distinct()
            _addonStates.value = uniqueAddons.map { name ->
                AddonFetchState(addonName = name, manifestUrl = "")
            }
        }

        // ── 3. Run Stremio + CS plugin fetches in parallel ────────────────────
        kotlinx.coroutines.supervisorScope {
            // Stremio tasks
            val stremioJobs = tasks.map { task ->
                launch {
                    try {
                        val streams = addonClient.fetchStreams(task.manifestUrl, task.requestType, task.videoId)
                        val sources = streams.map { item ->
                            val nameText  = item.name  ?: "Bilinmeyen Akış"
                            val titleText = item.title ?: "İsimsiz Dosya"
                            val parsedQuality = StreamSorter.parseQualityFromTitle("$titleText $nameText")
                            StreamSource(
                                addonName    = task.addonName,
                                name         = nameText,
                                title        = titleText,
                                url          = item.url,
                                infoHash     = item.infoHash,
                                fileIndex    = item.fileIdx ?: item.fileIndex,
                                trackers     = item.trackers,
                                requestHeaders = item.behaviorHints?.proxyHeaders?.request,
                                subtitleFilename = item.behaviorHints?.filename,
                                subtitleVideoHash = item.behaviorHints?.videoHash,
                                subtitleVideoSize = item.behaviorHints?.videoSize,
                                quality      = parsedQuality,
                                qualityValue = StreamSorter.parseQualityValue(parsedQuality)
                            )
                        }
                        val filteredSources = EpisodeStreamFilter.filterForEpisode(sources, season, episode)
                        val addonIdx = tasks.map { it.addonName }.distinct().indexOf(task.addonName)
                        if (addonIdx >= 0) {
                            stateUpdateMutex.withLock {
                                val list = _addonStates.value.toMutableList()
                                val existing = list[addonIdx]
                                val seen = existing.streams
                                    .mapNotNull { it.infoHash?.lowercase() ?: it.url?.lowercase() }
                                    .toHashSet()
                                val fresh = filteredSources.filter { s ->
                                    val key = s.infoHash?.lowercase() ?: s.url?.lowercase() ?: return@filter true
                                    seen.add(key)
                                }
                                list[addonIdx] = existing.copy(
                                    isLoading = false,
                                    streams   = StreamSorter.sort(existing.streams + fresh)
                                )
                                _addonStates.value = list
                            }
                        }
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        val addonIdx = tasks.map { it.addonName }.distinct().indexOf(task.addonName)
                        if (addonIdx >= 0) {
                            stateUpdateMutex.withLock {
                                val list = _addonStates.value.toMutableList()
                                list[addonIdx] = list[addonIdx].copy(
                                    isLoading = false,
                                    error     = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                                )
                                _addonStates.value = list
                            }
                        }
                    }
                }
            }

            // CS plugin tasks
            val installedCsPlugins = withContext(Dispatchers.IO) { db.csPluginDao().getAllPlugins() }
            val enabledCsPlugins = installedCsPlugins.filter { it.enabled }
            CsTrace.setInventory(installedCsPlugins.map {
                "${it.name} v${it.version} (id=${it.id}) enabled=${it.enabled} ${it.downloadUrl}"
            })
            CsTrace.session(
                "Arama: '$title' S${season}E${episode} movie=$isMovie yil=$startYear mal=$malId anilist=$aniListId tmdb=$tmdbId — " +
                    "CloudStream ${enabledCsPlugins.size}/${installedCsPlugins.size} etkin"
            )
            if (enabledCsPlugins.isEmpty() && installedCsPlugins.isNotEmpty()) {
                val message = context.getString(com.kitsugi.animelist.R.string.cs_all_plugins_disabled_error)
                Log.w(TAG, "${installedCsPlugins.size} kurulu CloudStream eklentisinin tamamı devre dışı")
                CsTrace.warn("CloudStream", "inventory", message)
                stateUpdateMutex.withLock {
                    _addonStates.value = _addonStates.value + AddonFetchState(
                        addonName = KitsugiStrings.get(R.string.cloudstream_video_saglayicilari),
                        isLoading = false,
                        error = message
                    )
                }
            }
            if (enabledCsPlugins.isNotEmpty()) {
                val csPlaceholders = enabledCsPlugins.map { plugin ->
                    AddonFetchState(
                        addonName   = "⚡ ${plugin.name}",
                        manifestUrl = plugin.downloadUrl,
                        isLoading   = true
                    )
                }
                stateUpdateMutex.withLock {
                    _addonStates.value = _addonStates.value + csPlaceholders
                }
                Log.d(TAG, "CS eklentileri paralel yükleniyor: ${enabledCsPlugins.map { it.name }}")

                for (plugin in enabledCsPlugins) {
                    launch {
                        val csDisplayName = "⚡ ${plugin.name}"
                        try {
                            // Tracker kayıtları api.name ile tutulur; plugin.id tek başına yetersizdir.
                            if (CsPluginStatusTracker.isBlocked(plugin.id) || CsPluginStatusTracker.isBlocked(plugin.name)) {
                                val reason = CsPluginStatusTracker.getErrorMessage(plugin.name)
                                    ?: CsPluginStatusTracker.getErrorMessage(plugin.id)
                                    ?: "Tekrarlı hata"
                                Log.w(TAG, "[$csDisplayName] Engellendi: $reason")
                                CsTrace.warn(plugin.name, "skip", "Engellendi: $reason")
                                updateAddonState(
                                    csDisplayName,
                                    isLoading = false,
                                    error = "Engellendi: $reason",
                                    errorCode = CsPluginStatusTracker.getErrorCode(plugin.name)
                                        ?: CsPluginStatusTracker.getErrorCode(plugin.id)
                                        ?: PlatformErrorCode.CloudStreamPluginUnsupported.id,
                                )
                                return@launch
                            }

                            Log.d(TAG, "[$csDisplayName] loadExtension başlatılıyor...")
                            CsTrace.info(plugin.name, "load", "loadExtension başlıyor")
                            val apis = withContext(Dispatchers.IO) {
                                CsPluginLoader.loadExtension(context, plugin.id)
                            }
                            if (apis.isEmpty()) {
                                Log.e(TAG, "[$csDisplayName] 0 API döndü — DEX yüklenemedi")
                                CsTrace.error(plugin.name, "load", "0 API döndü — DEX/manifest yüklenemedi")
                                updateAddonState(
                                    csDisplayName,
                                    isLoading = false,
                                    error = csLoadFailureMessage(plugin.id),
                                    errorCode = PlatformErrorCode.CloudStreamPluginUnsupported.id,
                                )
                                return@launch
                            }

                            val compatibleApis = apis.filter { api ->
                                CsOwnedPluginProfiles.supportsRequest(
                                    providerName = api.name,
                                    pluginVersion = CsPluginLoader.manifestVersionForSource(api.sourcePlugin),
                                    mediaType = mediaType,
                                    isMovie = isMovie,
                                    supportedTypes = api.supportedTypes.map { it.name }
                                )
                            }
                            if (compatibleApis.isEmpty()) {
                                val profileReason = apis.asSequence().mapNotNull { api ->
                                    CsOwnedPluginProfiles.episodeUnsupportedReason(
                                        providerName = api.name,
                                        pluginVersion = CsPluginLoader.manifestVersionForSource(api.sourcePlugin),
                                        mediaType = mediaType,
                                        isMovie = isMovie
                                    )
                                }.firstOrNull()
                                val message = profileReason ?: mediaType?.let {
                                    "Bu eklenti ${CsPluginTypeCompatibility.description(it)} içeriklerini desteklemiyor."
                                } ?: "Bu eklenti bu içerik türünü desteklemiyor."
                                CsTrace.info(plugin.name, "type-filter", message)
                                updateAddonState(csDisplayName, isLoading = false, error = message)
                                return@launch
                            }

                            val csStreams = mutableListOf<StreamSource>()
                            for (api in compatibleApis) {
                                val streams = CsStreamRunner.getStreams(
                                    api              = api,
                                    title            = title,
                                    alternativeTitles = alternativeTitles,
                                    year             = startYear,
                                    season           = season,
                                    episode          = episode,
                                    malId            = realMalId ?: resolvedIds.malId,
                                    aniListId        = realAniListId ?: resolvedIds.aniListId,
                                    tmdbId           = tmdbId ?: resolvedIds.tmdbId,
                                    kitsuId          = kitsuNumericId,
                                    bangumiId        = bangumiId,
                                    simklId          = simklId,
                                    isMovie          = isMovie,
                                    mediaType        = mediaType,
                                    // v2.4.250: yukarıda çözülen ID'leri aynen geçir —
                                    // eklenti başına ARM/TMDB fırtınası dursun.
                                    preResolvedIds   = resolvedIds
                                )
                                csStreams.addAll(streams)
                            }
                            Log.d(TAG, "[$csDisplayName] Toplam ${csStreams.size} stream bulundu")
                            CsTrace.info(plugin.name, "result", "Toplam ${csStreams.size} stream bulundu")
                            // Akış boşsa SESSİZ "akış bulunamadı" yerine tracker'daki GERÇEK
                            // hatayı göster (DNS/CF/timeout/parse) — kullanıcı nedenini görsün.
                            val apiName = apis.firstOrNull()?.name ?: plugin.name
                            val diagMsg = if (csStreams.isEmpty()) {
                                CsPluginStatusTracker.getErrorMessage(plugin.name)
                                    ?: CsPluginStatusTracker.getErrorMessage(apiName)
                            } else null
                            val diagCode = if (csStreams.isEmpty()) {
                                CsPluginStatusTracker.getErrorCode(plugin.name)
                                    ?: CsPluginStatusTracker.getErrorCode(apiName)
                                    ?: PlatformErrorCode.CloudStreamNoStreams.id
                            } else null
                            updateAddonState(
                                csDisplayName,
                                isLoading = false,
                                streams = csStreams,
                                error = diagMsg?.let { "⚠️ $it" },
                                errorCode = diagCode,
                            )

                        } catch (e: Throwable) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Log.e(TAG, "[$csDisplayName] KRİTİK HATA: ${e.javaClass.simpleName}: ${e.message}", e)
                            CsTrace.error(plugin.name, "pipeline", "KRİTİK HATA: ${e.javaClass.simpleName}: ${e.message}", e)

                            val isWebViewUiFailure = e is android.view.WindowManager.BadTokenException ||
                                e.cause is android.view.WindowManager.BadTokenException ||
                                e.message?.contains("token null", ignoreCase = true) == true ||
                                e.message?.contains("BadTokenException", ignoreCase = true) == true ||
                                e.message?.contains("activity running", ignoreCase = true) == true

                            val isCfBlock = e is CloudflareBlockException
                            val isTimeout = e is java.util.concurrent.TimeoutException ||
                                e.message?.contains("zaman aşımı", ignoreCase = true) == true ||
                                e.message?.contains("timeout", ignoreCase = true) == true

                            when {
                                isCfBlock -> {
                                    Log.w(TAG, "[$csDisplayName] Açık bot/WAF challenge tespit edildi")
                                    updateAddonState(
                                        csDisplayName,
                                        isLoading = false,
                                        error = "🔐 Bot/WAF doğrulaması gerekiyor (Doğrula butonuna bas)",
                                        requiresVerification = true,
                                        errorCode = PlatformErrorCode.CloudStreamProtection.id
                                    )
                                }
                                isTimeout -> {
                                    Log.w(TAG, "[$csDisplayName] Ağ/site zaman aşımı; CF/WAF olarak sınıflandırılmadı")
                                    updateAddonState(
                                        csDisplayName,
                                        isLoading = false,
                                        error = "⏱ İstek zaman aşımına uğradı; ağ veya sağlayıcı yanıt vermedi.",
                                        errorCode = PlatformErrorCode.CloudStreamTimeout.id,
                                    )
                                }
                                isWebViewUiFailure -> {
                                    Log.w(TAG, "[$csDisplayName] WebView doğrulama penceresi açılamadı")
                                    updateAddonState(
                                        csDisplayName,
                                        isLoading = false,
                                        error = "Doğrulama penceresi açılamadı; ekrandan yeniden deneyin.",
                                        errorCode = CloudStreamErrorClassifier.classify(e).id,
                                    )
                                }
                                else -> updateAddonState(
                                    csDisplayName,
                                    isLoading = false,
                                    error = "Hata: ${e.localizedMessage ?: e.message ?: e.javaClass.simpleName}",
                                    errorCode = CloudStreamErrorClassifier.classify(e).id,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Tek eklenti (kapsamlı) arama ──────────────────────────────────────────

    /**
     * Eklentiye özel arama. Sıra:
     *  1. Doğrudan sağlayıcı URL'si ([CsStreamRunner.getStreamsForUrl]).
     *  2. Aynı eklentinin KENDİ başlık araması ([CsStreamRunner.getStreams]).
     *
     * Her iki adım da yalnızca bu eklentinin API'lerini kullanır; başka bir kaynak
     * (Stremio addon'ı veya başka CS eklentisi) sonuçlara karışmaz. Bu yüzden kullanıcı
     * eklentinin kendi keşfet/arama akışından "İzle" dediğinde liste tutarlı kalır.
     *
     * @return true → en az bir kaynak bulundu.
     */
    private suspend fun runScopedPluginFetch(
        apiName: String,
        directUrl: String?,
        malId: Int?,
        aniListId: Int?,
        tmdbId: Int?,
        bangumiId: Int?,
        simklId: Int?,
        kitsuId: Int?,
        episode: Int,
        season: Int,
        title: String,
        alternativeTitles: List<String>,
        startYear: Int?,
        isMovie: Boolean,
        mediaType: MediaType?,
        knownImdbId: String?
    ): Boolean {
        val plugin = withContext(Dispatchers.IO) {
            KitsugiDatabase.getDatabase(context).csPluginDao().getEnabledPlugins().firstOrNull {
                it.id.equals(apiName, ignoreCase = true) ||
                    it.name.equals(apiName, ignoreCase = true)
            }
        }
        if (plugin == null) {
            val display = StreamSourceScope.displayName(apiName)
            _scopedSourceName.value = display.removePrefix("⚡ ").trim()
            _addonStates.value = listOf(
                AddonFetchState(
                    addonName = display,
                    isLoading = false,
                    error = "Eklenti kurulu değil veya devre dışı — diğer kaynaklar kendiliğinden aranmaz"
                )
            )
            CsTrace.warn(apiName, "scope", "Tek eklenti modu: eklenti bulunamadı/devre dışı")
            return false
        }

        val displayName = "⚡ ${plugin.name}"
        _scopedSourceName.value = plugin.name
        _addonStates.value = listOf(
            AddonFetchState(addonName = displayName, manifestUrl = plugin.downloadUrl, isLoading = true)
        )
        CsTrace.info(
            plugin.name,
            "scope",
            "Tek eklenti modu: yalnızca bu eklenti aranıyor (S${season}E${episode}, doğrudan URL=${directUrl != null})"
        )

        // Aynı eklenti için önceki oturum hataları/blokları yeni denemeyi gizlemesin.
        CsPluginStatusTracker.clearPluginStatus(plugin.id)
        CsPluginStatusTracker.clearPluginStatus(plugin.name)

        try {
            val apis = withContext(Dispatchers.IO) {
                CsPluginLoader.loadExtension(context, plugin.id)
            }
            if (apis.isEmpty()) {
                CsTrace.error(plugin.name, "load", "Tek eklenti modu: yükleme başarısız")
                updateAddonStateSync(displayName, isLoading = false, error = csLoadFailureMessage(plugin.id))
                return false
            }
            apis.forEach { CsPluginStatusTracker.clearPluginStatus(it.name) }

            val compatibleApis = apis.filter { api ->
                CsPluginTypeCompatibility.supports(mediaType, api.supportedTypes.map { it.name })
            }
            if (compatibleApis.isEmpty()) {
                val message = mediaType?.let {
                    "Bu eklenti ${CsPluginTypeCompatibility.description(it)} içeriklerini desteklemiyor."
                } ?: "Bu eklenti bu içerik türünü desteklemiyor."
                updateAddonStateSync(displayName, isLoading = false, error = message)
                return false
            }

            val collected = mutableListOf<StreamSource>()

            // 1) Doğrudan URL (kullanıcının tıkladığı sağlayıcı sayfası).
            if (directUrl != null) {
                for (api in compatibleApis) {
                    val forUrl = CsStreamRunner.getStreamsForUrl(
                        api = api,
                        url = directUrl,
                        season = season,
                        episode = episode,
                        isMovie = isMovie,
                        mediaType = mediaType
                    )
                    if (forUrl.isNotEmpty()) {
                        collected.addAll(forUrl)
                        break
                    }
                }
                if (collected.isEmpty()) {
                    CsTrace.warn(
                        plugin.name,
                        "direct-url",
                        "Doğrudan URL akış vermedi — aynı eklentinin kendi aramasına geçildi"
                    )
                }
            }

            // 2) Aynı eklentinin kendi başlık araması (yalnızca bu eklenti).
            if (collected.isEmpty()) {
                // Kimlik çözümü yalnızca başlık aramasına girer: doğrudan URL yolu (yukarıdaki
                // hızlı yol) ARM/TMDB isteği beklemeden çalışır.
                val realMal = malId?.takeIf { it in 1..99_999_999 }
                val realAniList = when {
                    aniListId != null && aniListId >= 100_000_000 -> aniListId - 100_000_000
                    aniListId != null && aniListId > 0 -> aniListId
                    else -> null
                }
                val kitsu = malId?.takeIf { it >= 300_000_000 }?.let { it - 300_000_000 }
                val resolved = try {
                    KitsugiIdResolver.resolveIds(
                        malId = realMal,
                        aniListId = realAniList,
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        kitsuId = kitsu ?: kitsuId,
                        bangumiId = bangumiId,
                        wikidataImdbFallback = true
                    )
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    ResolvedIdsFallback.empty
                }
                val knownImdb = knownImdbId?.trim()?.takeIf { Regex("tt[0-9]{5,12}").matches(it) }
                val effectiveIds = if (knownImdb != null) resolved.copy(imdbId = knownImdb) else resolved
                for (api in compatibleApis) {
                    val found = CsStreamRunner.getStreams(
                        api = api,
                        title = title,
                        alternativeTitles = alternativeTitles,
                        year = startYear,
                        season = season,
                        episode = episode,
                        malId = realMal ?: effectiveIds.malId,
                        aniListId = realAniList ?: effectiveIds.aniListId,
                        tmdbId = tmdbId ?: effectiveIds.tmdbId,
                        isMovie = isMovie,
                        mediaType = mediaType,
                        bangumiId = bangumiId,
                        simklId = simklId,
                        kitsuId = kitsu ?: kitsuId ?: effectiveIds.kitsuId,
                        preResolvedIds = effectiveIds
                    )
                    collected.addAll(found)
                }
            }

            val diagApiName = apis.firstOrNull()?.name ?: plugin.name
            val diagMsg = if (collected.isEmpty()) {
                CsPluginStatusTracker.getErrorMessage(plugin.name)
                    ?: CsPluginStatusTracker.getErrorMessage(diagApiName)
            } else null
            val diagCode = if (collected.isEmpty()) {
                CsPluginStatusTracker.getErrorCode(plugin.name)
                    ?: CsPluginStatusTracker.getErrorCode(diagApiName)
                    ?: PlatformErrorCode.CloudStreamNoStreams.id
            } else null
            updateAddonStateSync(
                displayName,
                isLoading = false,
                streams = collected,
                error = if (collected.isEmpty()) diagMsg?.let { "⚠️ $it" } else null,
                errorCode = diagCode,
            )
            CsTrace.info(
                plugin.name,
                "scope",
                "Tek eklenti modu bitti: ${collected.size} kaynak (diğer kaynaklar aranmadı)"
            )
            return collected.isNotEmpty()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            CsTrace.error(plugin.name, "scope", "Tek eklenti modu hata: ${e.javaClass.simpleName}: ${e.message}", e)
            updateAddonStateSync(
                displayName,
                isLoading = false,
                error = "Hata: ${e.localizedMessage ?: e.javaClass.simpleName}",
                errorCode = CloudStreamErrorClassifier.classify(e).id,
            )
            return false
        }
    }

    /** Tek eklenti modunda ID çözümü patlarsa aramaya kimliksiz devam edilir. */
    private object ResolvedIdsFallback {
        val empty = com.kitsugi.animelist.data.remote.ResolvedIds()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun updateAddonState(
        addonName: String,
        isLoading: Boolean,
        streams: List<StreamSource> = emptyList(),
        error: String? = null,
        requiresVerification: Boolean = false,
        errorCode: String? = null,
    ) {
        val resolvedError = if (error == null && streams.isEmpty() && !isLoading) {
            "Bu içerik için akış bulunamadı"
        } else error
        val resolvedErrorCode = errorCode ?: resolvedError?.let {
            CloudStreamErrorClassifier.classifyText(
                text = it,
                noStreams = error == null && streams.isEmpty() && !isLoading,
                pluginUnsupported = requiresVerification.not() && it.contains("engellendi", ignoreCase = true),
            ).id
        }
        stateUpdateMutex.withLock {
            withContext(Dispatchers.Main) {
                _addonStates.value = _addonStates.value.toMutableList().also { list ->
                    val i = list.indexOfFirst { it.addonName == addonName }
                    if (i >= 0) {
                        list[i] = list[i].copy(
                            isLoading = isLoading,
                            streams   = streams,
                            error     = resolvedError,
                            requiresVerification = requiresVerification,
                            errorCode = resolvedErrorCode,
                        )
                    }
                }
            }
        }
    }

    private fun updateAddonStateSync(
        addonName: String,
        isLoading: Boolean,
        streams: List<StreamSource> = emptyList(),
        error: String? = null,
        requiresVerification: Boolean = false,
        errorCode: String? = null,
    ) {
        val resolvedError = if (error == null && streams.isEmpty() && !isLoading) {
            "Bu içerik için akış bulunamadı"
        } else error
        val resolvedErrorCode = errorCode ?: resolvedError?.let {
            CloudStreamErrorClassifier.classifyText(
                text = it,
                noStreams = error == null && streams.isEmpty() && !isLoading,
                pluginUnsupported = requiresVerification.not() && it.contains("engellendi", ignoreCase = true),
            ).id
        }
        val current = _addonStates.value.toMutableList()
        val i = current.indexOfFirst { it.addonName == addonName }
        if (i >= 0) {
            current[i] = current[i].copy(
                isLoading = isLoading,
                streams   = streams,
                error     = resolvedError,
                requiresVerification = requiresVerification,
                errorCode = resolvedErrorCode,
            )
            _addonStates.value = current
        }
    }

    @OptIn(Prerelease::class, UnsafeSSL::class)
    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "onCleared: Watch screen closed. Cancelling all ongoing network calls for stream resolution.")
        try {
            com.lagradost.cloudstream3.app.baseClient.dispatcher.cancelAll()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to cancel app.baseClient calls: ${e.message}")
        }
        try {
            com.lagradost.cloudstream3.insecureApp.baseClient.dispatcher.cancelAll()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to cancel insecureApp.baseClient calls: ${e.message}")
        }
        try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.dispatcher.cancelAll()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to cancel KitsugiHttpClient calls: ${e.message}")
        }
    }
}

/** State for the Cloudflare WebView verification dialog. */
data class WebViewDialogState(
    val url: String,
    val displayName: String,
    val pluginId: String
)
