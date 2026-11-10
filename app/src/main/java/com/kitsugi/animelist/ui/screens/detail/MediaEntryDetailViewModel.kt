package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClient
import com.kitsugi.animelist.data.remote.KitsugiCharacter
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.data.remote.KitsugiShikimoriClient
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.data.remote.KitsugiReview
import com.kitsugi.animelist.data.remote.KitsugiStaff
import com.kitsugi.animelist.data.remote.KitsugiStats
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.kitsugi.animelist.ui.screens.detail.SynopsisTranslationController
import com.kitsugi.animelist.ui.screens.detail.SynopsisTranslationSettings
import com.kitsugi.animelist.ui.screens.detail.SynopsisTranslationState
import com.kitsugi.animelist.ui.screens.detail.synopsisTranslationCacheIdentity
import com.kitsugi.animelist.ui.screens.detail.SynopsisTranslationEntry
import com.kitsugi.animelist.ui.screens.detail.bestAvailableSynopsis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.kitsugi.animelist.data.local.MangaMappingEntity
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.remote.MdbListClient
import com.kitsugi.animelist.data.remote.MdbListQuotaCenter
import com.kitsugi.animelist.data.remote.MdbListRatings
import com.kitsugi.animelist.data.remote.KitsugiSimklDetailClient
import com.kitsugi.animelist.data.remote.resolveMdbListMediaIds
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.data.remote.KitsugiMediaVideoAggregator
import com.kitsugi.animelist.data.remote.KitsugiTheme
import com.kitsugi.animelist.data.remote.KnownVideoMediaIds
import com.kitsugi.animelist.data.remote.MediaVideoCategory
import com.kitsugi.animelist.data.remote.MediaVideoItem
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

/** Sekme verisi için üst süre sınırı — süre dolarsa sekme hata durumuna düşer, asılı kalmaz. */
private const val TAB_FETCH_TIMEOUT_MS = 25_000L

/** Bölüm listesi (çok kaynaklı: Shikimori/TMDB/Jikan) daha uzun sürebilir. */
private const val TAB_EPISODES_FETCH_TIMEOUT_MS = 45_000L

class MediaEntryDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val apiClient = JikanApiClient()
    private val translationManager = TranslationManager(context)
    private val mangaRepository = MangaSourceRepository(context)
    private val tmdbVideoClient = TmdbApiClient()
    private val settingsDataStore = SettingsDataStore(context)
    private val TAG = "MediaEntryDetailVM"

    /**
     * Sekme başına çalışan işler (tek uçuş). Sekme tetikleyicisi yeniden çalıştığında
     * aynı sekme için istek birikmesini engeller ve yeni sonuç geldiğinde eskisini iptal eder.
     */
    private val tabJobs = mutableMapOf<Int, Job>()

    /** Sekme işlerinin hangi parametrelerle (dış kimlik + MAL kimliği) çalıştığı. */
    private val tabRequestKeys = mutableMapOf<Int, String>()

    // --- StateFlows ---

    private val _mangaMapping = MutableStateFlow<MangaMappingEntity?>(null)
    val mangaMapping: StateFlow<MangaMappingEntity?> = _mangaMapping.asStateFlow()

    private val _detailState = MutableStateFlow<KitsugiMediaDetail?>(null)
    val detailState: StateFlow<KitsugiMediaDetail?> = _detailState.asStateFlow()

    private val _detailLoading = MutableStateFlow(true)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    /**
     * Detay alınamadığında kaynağın `KTS-…` hata kodu (yoksa null). Başarılı detayda sıfırlanır;
     * yeniden denemede son hata yüklenme ekranında görünmeye devam eder.
     */
    private val _detailErrorCode = MutableStateFlow<PlatformErrorCode?>(null)
    val detailErrorCode: StateFlow<PlatformErrorCode?> = _detailErrorCode.asStateFlow()

    /** Sekme hatası için kaynağın son `KTS-…` kodu; bilinmiyorsa genel sınıflandırılamayan kod. */
    private fun isBangumiSource(source: String): Boolean {
        val key = source.trim().lowercase()
        return key == "bangumi" || key == "bgm"
    }

    /**
     * Bangumi kadro adları için arka plan Latin adı çözümü: senkron geç süresi yetmediğinde
     * CJK kalan karakter/kişi adlarını çözer, kalıcı önbelleğe yazar ve sekme hâlâ aynı
     * veriyi gösteriyorsa ekranı günceller. İkinci ziyarette liste Latin adlarla açılır.
     */
    private fun warmBangumiCharacterNames(source: String, malId: Int, data: List<KitsugiCharacter>) {
        if (!isBangumiSource(source) || data.isEmpty()) return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching {
                    KitsugiBangumiDetailClient.enrichCharacterNamesFromBangumi(data, background = true)
                }.getOrNull()
            } ?: return@launch
            if (updated == data) return@launch
            if ((_charactersState.value as? DetailTabState.Success)?.data !== data) return@launch
            DetailCache.putMediaCharacters(source, malId, updated)
            _charactersState.value = DetailTabState.Success(updated)
        }
    }

    private fun warmBangumiStaffNames(source: String, malId: Int, data: List<KitsugiStaff>) {
        if (!isBangumiSource(source) || data.isEmpty()) return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching {
                    KitsugiBangumiDetailClient.enrichStaffNamesFromBangumi(data, background = true)
                }.getOrNull()
            } ?: return@launch
            if (updated == data) return@launch
            if ((_staffState.value as? DetailTabState.Success)?.data !== data) return@launch
            DetailCache.putMediaStaff(source, malId, updated)
            _staffState.value = DetailTabState.Success(updated)
        }
    }

    /**
     * Bangumi ilişkiler/öneriler için arka plan başlık çözümü: p1 liste uçları slim subject
     * döndürdüğünden CJK kalan başlıklar ortak çözümleyiciyle (AniList toplu arama + infobox
     * + TMDB) çözülür, kalıcı önbelleğe yazılır ve sekme güncellenir.
     */
    private fun warmBangumiRelationTitles(
        source: String,
        malId: Int,
        data: List<KitsugiRelation>,
        recommendations: Boolean
    ) {
        if (!isBangumiSource(source) || data.isEmpty()) return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching {
                    KitsugiBangumiDetailClient.enrichRelationTitles(data, background = true)
                }.getOrNull()
            } ?: return@launch
            if (updated == data) return@launch
            val currentState = if (recommendations) _recommendationsState.value else _relationsState.value
            if ((currentState as? DetailTabState.Success)?.data !== data) return@launch
            if (recommendations) {
                DetailCache.putMediaRecommendations(source, malId, updated)
                _recommendationsState.value = DetailTabState.Success(updated)
            } else {
                DetailCache.putMediaRelations(source, malId, updated)
                _relationsState.value = DetailTabState.Success(updated)
            }
        }
    }

    private fun tabFailureCode(source: String): com.kitsugi.animelist.data.platform.PlatformErrorCode =
        com.kitsugi.animelist.data.platform.PlatformFailureRegistry.latest(source)?.code
            ?: com.kitsugi.animelist.data.platform.PlatformErrorCode.COM_1020

    private val synopsisController = SynopsisTranslationController(
        scope = viewModelScope,
        readSettings = {
            val settings = settingsDataStore.settingsFlow.first()
            SynopsisTranslationSettings(
                enabled = settings.autoTranslateEnabled,
                sourceLanguage = settings.translateSourceLanguage.ifBlank { "auto" },
                targetLanguage = settings.translateTargetLanguage.ifBlank {
                    com.kitsugi.animelist.utils.KitsugiTranslatePrefs.resolvedTarget()
                }
            )
        },
        readCache = { entry, raw, settings ->
            DetailCache.getTranslation(
                "synopsis", entry.source, entry.stableId,
                synopsisTranslationCacheIdentity(entry, raw, settings)
            )
        },
        writeCache = { entry, raw, settings, translated ->
            DetailCache.putTranslation(
                "synopsis", entry.source, entry.stableId, translated,
                synopsisTranslationCacheIdentity(entry, raw, settings)
            )
        },
        translate = { raw, settings, onPartial ->
            translationManager.translateToResult(raw, settings.sourceLanguage, settings.targetLanguage, onPartial)
        }
    )
    internal val synopsisTranslation: StateFlow<SynopsisTranslationState> = synopsisController.state

    val synopsisState: StateFlow<SynopsisState> = synopsisController.state
        .map { state ->
            when {
                !state.original.isNullOrBlank() -> SynopsisState.Success(
                    if (state.showingOriginal) state.original else state.translated ?: state.original
                )
                state.error -> SynopsisState.Error
                else -> SynopsisState.Loading
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SynopsisState.Loading)

    val translatedSynopsis: StateFlow<String?> = synopsisController.state
        .map { it.translated }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val showSynopsisOriginal: StateFlow<Boolean> = synopsisController.state
        .map { it.showingOriginal }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val translationLoading: StateFlow<Boolean> = synopsisController.state
        .map { it.translating }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val originalSynopsis: StateFlow<String?> = synopsisController.state
        .map { it.original }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var synopsisFetchJob: Job? = null

    private val _logoUrl = MutableStateFlow<String?>(null)
    val logoUrl: StateFlow<String?> = _logoUrl.asStateFlow()

    private val _episodeRatings = MutableStateFlow<Map<Pair<Int, Int>, Double>>(emptyMap())
    val episodeRatings: StateFlow<Map<Pair<Int, Int>, Double>> = _episodeRatings.asStateFlow()

    private val _resolvedTmdbId = MutableStateFlow<Int?>(null)
    val resolvedTmdbId: StateFlow<Int?> = _resolvedTmdbId.asStateFlow()

    private val _charactersState = MutableStateFlow<DetailTabState<List<KitsugiCharacter>>>(DetailTabState.Loading)
    val charactersState: StateFlow<DetailTabState<List<KitsugiCharacter>>> = _charactersState.asStateFlow()

    private val _staffState = MutableStateFlow<DetailTabState<List<KitsugiStaff>>>(DetailTabState.Loading)
    val staffState: StateFlow<DetailTabState<List<KitsugiStaff>>> = _staffState.asStateFlow()

    private val _relationsState = MutableStateFlow<DetailTabState<List<KitsugiRelation>>>(DetailTabState.Loading)
    val relationsState: StateFlow<DetailTabState<List<KitsugiRelation>>> = _relationsState.asStateFlow()

    private val _recommendationsState = MutableStateFlow<DetailTabState<List<KitsugiRelation>>>(DetailTabState.Loading)
    val recommendationsState: StateFlow<DetailTabState<List<KitsugiRelation>>> = _recommendationsState.asStateFlow()

    private val _statsState = MutableStateFlow<DetailTabState<KitsugiStats?>>(DetailTabState.Loading)
    val statsState: StateFlow<DetailTabState<KitsugiStats?>> = _statsState.asStateFlow()

    private val _reviewsState = MutableStateFlow<DetailTabState<List<KitsugiReview>>>(DetailTabState.Loading)
    val reviewsState: StateFlow<DetailTabState<List<KitsugiReview>>> = _reviewsState.asStateFlow()

    private val _episodesState = MutableStateFlow<DetailTabState<List<KitsugiStreamingEpisode>>>(DetailTabState.Loading)
    val episodesState: StateFlow<DetailTabState<List<KitsugiStreamingEpisode>>> = _episodesState.asStateFlow()

    private val _targetSeason = MutableStateFlow<Int>(1)
    val targetSeason: StateFlow<Int> = _targetSeason.asStateFlow()

    private val _mdbListRatings = MutableStateFlow<MdbListRatings?>(null)
    val mdbListRatings: StateFlow<MdbListRatings?> = _mdbListRatings.asStateFlow()

    private val _mdbListLoading = MutableStateFlow(false)
    val mdbListLoading: StateFlow<Boolean> = _mdbListLoading.asStateFlow()

    /** Fanart.tv + TMDB + Jikan kaynaklarından gelen zengin galeri öğeleri */
    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    /** Galeri tüm kaynaklardan (detay + galeri + yenileme) bitene kadar true. */
    private val galleryTracker = GalleryLoadTracker()
    val galleryLoading: StateFlow<Boolean> = galleryTracker.loading

    private val _videoItems = MutableStateFlow<List<MediaVideoItem>>(emptyList())
    val videoItems: StateFlow<List<MediaVideoItem>> = _videoItems.asStateFlow()

    private val videosTracker = GalleryLoadTracker()
    val videosLoading: StateFlow<Boolean> = videosTracker.loading

    /** Her yeni entry navigasyonunda artar — UI bu trigger’ı izleyerek tab’ı 0’a sıfırlar. */
    private val _pageResetTrigger = MutableStateFlow(0)
    val pageResetTrigger: StateFlow<Int> = _pageResetTrigger.asStateFlow()

    // --- Cache / Lock Key ---
    private var currentFetchKey: String? = null
    private var lastEntry: MediaEntry? = null
    private var mangaMappingJob: Job? = null
    private var mdbListFetchJob: Job? = null
    private var mdbListFetchGeneration = 0L
    private var observedMdbListConfig: Pair<Boolean, String>? = null

    /** Çeviri işlerinin nesil sayacı — eski işlerin yüklenme durumunu kapatmasını engeller. */
    private var lastAutoTranslateSetting: Boolean? = null
    private var lastTargetLangSetting: String? = null
    private var lastSourceLangSetting: String? = null
    /**
     * `loadEntry` anında tek seferlik okunan otomatik çeviri ayarı.
     * Çeviri işinin içindeki ayar okuması geçici olarak başarısız olursa bu değere düşülür;
     * böylece ayar açıkken açıklamanın orijinal dilde kalması önlenir.
     */

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val newMdbListConfig = settings.mdbListEnabled to settings.mdbListApiKey.trim()
                val previousMdbListConfig = observedMdbListConfig
                observedMdbListConfig = newMdbListConfig
                if (previousMdbListConfig != null && previousMdbListConfig != newMdbListConfig) {
                    lastEntry?.let(::refreshMdbListRatings)
                }

                val prevAuto = lastAutoTranslateSetting
                val prevTarget = lastTargetLangSetting
                lastAutoTranslateSetting = settings.autoTranslateEnabled
                lastTargetLangSetting = settings.translateTargetLanguage
                if (prevAuto == null && prevTarget == null) return@collect
                if (prevAuto != settings.autoTranslateEnabled ||
                    prevTarget != settings.translateTargetLanguage ||
                    lastSourceLangSetting != settings.translateSourceLanguage) {
                    lastSourceLangSetting = settings.translateSourceLanguage
                    val entry = lastEntry ?: return@collect
                    val raw = synopsisController.state.value.original
                        ?: _detailState.value?.synopsis ?: entry.synopsis ?: return@collect
                    if (raw.isBlank()) return@collect
                    val isRussian = raw.any { it in '\u0400'..'\u04FF' }
                    if (settings.autoTranslateEnabled || isRussian) {
                        synopsisController.submit(raw, force = true)
                    } else {
                        synopsisController.disable()
                    }
                }
            }
        }
    }

    /** Orijinal / çeviri geçişi (açıklama). Çeviri bellekte kalır, yeniden çevrilmez. */
    fun toggleSynopsisOriginal() {
        synopsisController.toggleOriginal()
    }

    /** Accept a synopsis that arrives in Room after the detail page was opened. */
    fun onStoredSynopsisChanged(entry: MediaEntry) {
        bestAvailableSynopsis(_detailState.value?.synopsis, entry.synopsis, null)
            ?.let { synopsisController.submit(it) }
    }

    /**
     * Initializes state and starts background jobs to fetch all entry-specific details.
     * Prevents redundant scanning if [entry] hasn't changed.
     */
    fun loadEntry(entry: MediaEntry, showAnimeLogos: Boolean, forceRefresh: Boolean = false) {
        mangaMappingJob?.cancel()
        mangaMappingJob = viewModelScope.launch {
            mangaRepository.observeMangaMapping(entry.id).collect {
                _mangaMapping.value = it
            }
        }

        val stableId = cacheIdentityOf(entry)
        if (forceRefresh) {
            currentFetchKey = null
            DetailCache.removeMediaDetail(entry.source, stableId)
            DetailCache.removeTranslation("synopsis", entry.source, stableId)
            DetailCache.removeMediaCharacters(entry.source, stableId)
            DetailCache.removeMediaStaff(entry.source, stableId)
            DetailCache.removeMediaRelations(entry.source, stableId)
            DetailCache.removeMediaRecommendations(entry.source, stableId)
            DetailCache.removeMediaReviews(entry.source, stableId, entry.type.name)
            DetailCache.removeMediaEpisodes(entry.source, stableId)
            DetailCache.removeMediaVideos(entry.source, stableId)
            DetailCache.clearFanartCache() // Fanart galeri önbelleğini temizle
        }

        val newKey = "${entry.id}:${entry.source}:${entry.malId}"
        if (newKey == currentFetchKey) {
            Log.d(TAG, "loadEntry: Cache hit for key=$newKey — skipping")
            return
        }

        Log.d(TAG, "loadEntry: New key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        lastEntry = entry
        // Otomatik çeviri ayarını yükleme anında oku ve sakla: çeviri işi
        // arka planda yeniden okurken bir hata olursa bu değere düşecek.
        _pageResetTrigger.value += 1 // Signal UI to scroll back to first tab
        // Eski kaydın sekme işleri iptal edilir: bit gecikmeli yanıtları yeni kaydın
        // state'ine yazamaz (yanlış içerik göstermenin klasik yolu).
        cancelTabLoads()

        // Reset states
        val cachedDetail = DetailCache.getMediaDetail(entry.source, stableId, entry.type.name)
            ?: DetailCache.getMediaDetail(entry.source, stableId)
        val cachedVideos = DetailCache.getMediaVideos(entry.source, stableId, _targetSeason.value)
        _videoItems.value = cachedVideos ?: KitsugiMediaVideoAggregator.seedFromDetail(cachedDetail, entry.source)
        videosTracker.reset()

        _detailState.value = cachedDetail
        _detailLoading.value = cachedDetail == null
        if (cachedDetail != null) _detailErrorCode.value = null

        synopsisController.reset(SynopsisTranslationEntry(newKey, entry.source, stableId, entry.type.name))
        (cachedDetail?.synopsis?.takeIf { it.isNotBlank() } ?: entry.synopsis?.takeIf { it.isNotBlank() })
            ?.let { synopsisController.submit(it) }

        _logoUrl.value = null
        _episodeRatings.value = emptyMap()
        _resolvedTmdbId.value = null
        _mdbListRatings.value = null
        _mdbListLoading.value = false
        refreshMdbListRatings(entry)
        _galleryItems.value = emptyList()
        galleryTracker.reset()

        // Reset tab states to either cached values or Loading
        val malId = stableId
        val cachedCharacters = DetailCache.getMediaCharacters(entry.source, malId)
        if (cachedCharacters != null && cachedCharacters.isEmpty()) {
            DetailCache.removeMediaCharacters(entry.source, malId)
            _charactersState.value = DetailTabState.Loading
        } else {
            _charactersState.value = if (cachedCharacters != null) DetailTabState.Success(cachedCharacters) else DetailTabState.Loading
        }

        val cachedStaff = DetailCache.getMediaStaff(entry.source, malId)
        if (cachedStaff != null && cachedStaff.isEmpty()) {
            DetailCache.removeMediaStaff(entry.source, malId)
            _staffState.value = DetailTabState.Loading
        } else {
            _staffState.value = if (cachedStaff != null) DetailTabState.Success(cachedStaff) else DetailTabState.Loading
        }

        val cachedRelations = DetailCache.getMediaRelations(entry.source, malId)
        if (cachedRelations != null && cachedRelations.isEmpty()) {
            DetailCache.removeMediaRelations(entry.source, malId)
            _relationsState.value = DetailTabState.Loading
        } else {
            _relationsState.value = if (cachedRelations != null) DetailTabState.Success(cachedRelations) else DetailTabState.Loading
        }

        val cachedRecommendations = DetailCache.getMediaRecommendations(entry.source, malId)
        if (cachedRecommendations != null && cachedRecommendations.isEmpty()) {
            DetailCache.removeMediaRecommendations(entry.source, malId)
            _recommendationsState.value = DetailTabState.Loading
        } else {
            _recommendationsState.value = if (cachedRecommendations != null) DetailTabState.Success(cachedRecommendations) else DetailTabState.Loading
        }

        val cachedStats = DetailCache.getMediaStats(entry.source, malId)
        _statsState.value = if (DetailCache.hasMediaStats(entry.source, malId)) DetailTabState.Success(cachedStats) else DetailTabState.Loading

        val cachedReviews = DetailCache.getMediaReviews(entry.source, malId, entry.type.name)
        _reviewsState.value = if (cachedReviews != null) DetailTabState.Success(cachedReviews) else DetailTabState.Loading

        val cachedEpisodes = DetailCache.getMediaEpisodes(entry.source, malId)
        if (cachedEpisodes != null && cachedEpisodes.isEmpty()) {
            DetailCache.removeMediaEpisodes(entry.source, malId)
            _episodesState.value = DetailTabState.Loading
        } else {
            _episodesState.value = if (cachedEpisodes != null) DetailTabState.Success(cachedEpisodes) else DetailTabState.Loading
        }

        // Detail fetch — sayfa render için kritik; öncelikli coroutine
        val detailJob = viewModelScope.launch {
            try {
                fetchDetail(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching detail: ${e.message}", e)
                _detailLoading.value = false
            }
        }

        // Galeri + Fanart.tv: galeri, detay verisine (banner, tanıtım görselleri, TMDB kimliği)
        // bağlı olduğu için detay bitince başlar. Galeri tüm kaynaklarla bitene kadar "yükleniyor".
        val galleryToken = galleryTracker.begin()
        viewModelScope.launch {
            try {
                detailJob.join()
                fetchFanartGallery(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Fanart gallery (post-detail): ${e.message}", e)
            } finally {
                galleryTracker.end(galleryToken)
            }
        }

        // Ortak videolar sekmesi: tüm video sağlayan kaynakları paralel sorgular
        val videosToken = videosTracker.begin()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fetchAggregatedVideosForEntry(entry, newKey)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching aggregated videos: ${e.message}", e)
            } finally {
                videosTracker.end(videosToken)
            }
        }

        // Synopsis — bağımsız
        synopsisFetchJob?.cancel()
        val synopsisExpectedKey = newKey
        synopsisFetchJob = viewModelScope.launch {
            try {
                fetchSynopsis(entry, synopsisExpectedKey)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error fetching synopsis: ${e.message}", e)
                if (currentFetchKey == synopsisExpectedKey) {
                    synopsisController.showErrorIfEmpty()
                }
            }
        }


        // Logo — bağımsız, en düşük öncelikli
        viewModelScope.launch {
            try {
                fetchLogo(entry, showAnimeLogos)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching logo: ${e.message}", e)
            }
        }

    }

    /**
     * Kitsu kayıtları için bellek-içi önbellek (DetailCache) anahtarının kanonik kimliği.
     * Eski kayıtlarda `malId` gerçek MAL ID taşıyabildiğinden, anahtarı çözülen stableId
     * üzerinden kurmak yanlış yapımın önbelleğe yazılmasını engeller.
     */
    private var canonicalKitsuIdForEntry: Pair<Int, Int>? = null

    private fun cacheIdentityOf(entry: MediaEntry): Int {
        val raw = entry.malId ?: entry.id
        if (!entry.source.equals("kitsu", ignoreCase = true)) return raw
        if (com.kitsugi.animelist.data.remote.KitsuIdNamespace.isStableId(raw)) return raw
        canonicalKitsuIdForEntry?.let { (entryId, canonical) -> if (entryId == entry.id) return canonical }
        // Çözülememiş Kitsu kimliği: önbelleği kayıt satırının kendi anahtarına bağla —
        // böylece hiçbir zaman başka bir yapımın verisini okumayız.
        return -entry.id
    }

    /**
     * Kaydın dış (API) kimliği.
     *
     * Kitsu kayıtlarında kimlik alanı boş olabilir (eski içe aktarmalar ya da
     * `KitsuIdentityMigration` sonrası). Bu durumda yerel satır numarası (`entry.id`)
     * KESİNLİKLE dış kimlik olarak kullanılmaz: 42 gibi bir satır numarası MAL ID'si
     * sanılıp alakasız bir yapımın detayı getiriliyordu. Kitsu için 0 döneriz —
     * KitsugiDetailClient başlık üzerinden kanonik kimliği çözmeyi dener.
     */
    private fun externalIdOf(entry: MediaEntry): Int = when (entry.source.lowercase()) {
        "simkl" -> entry.simklId?.takeIf { it > 0 } ?: (if (entry.id > 0) entry.id else (entry.malId ?: 0))
        "kitsu" -> entry.malId?.takeIf { it > 0 } ?: 0
        // AniList kayıtlarında yerel satır numarası (entry.id) dış kimlik DEĞİLDİR; manuel
        // eklenen kayıtlarda malId boş olur ve satır numarası başka bir yapımın MAL ID'si
        // gibi okunup alakasız öneriler/detay getiriliyordu.
        "anilist" -> entry.malId?.takeIf { it > 0 } ?: 0
        else -> entry.malId ?: entry.id
    }

    private fun realMalIdOf(entry: MediaEntry): Int? = when (entry.source.lowercase()) {
        "simkl" -> {
            val m = entry.malId
            val s = entry.simklId
            if (m != null && m > 0 && m != s && m < 100_000_000) m else null
        }
        "anilist" -> {
            val m = entry.malId
            if (m != null && m > 0 && m < 100_000_000) m else null
        }
        "kitsu" -> com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(entry.malId)
        "bangumi", "shikimori", "tmdb" -> null
        else -> entry.malId?.takeIf { it in 1 until 100_000_000 }
    }

    private suspend fun fetchDetail(entry: MediaEntry) {
        // NOT (Kitsu): kimlik alanı her zaman 300M aralığındaki stableId olmalıdır. Eski
        // kayıtlarda bu alan gerçek MAL ID taşıyabiliyor; KitsugiDetailClient kimliği
        // kanonikleştirir (eşleme önbelleği → Kitsu mappings → sıkı başlık araması).
        // Kimlik hiç yoksa 0 geçilir ve istemci başlık üzerinden çözer (bkz. externalIdOf).
        val effectiveExternalId = externalIdOf(entry)

        val stableId = cacheIdentityOf(entry)
        if (entry.source.equals("kitsu", ignoreCase = true)) {
            com.kitsugi.animelist.data.remote.KitsuIdNamespace.stableIdOrNull(effectiveExternalId)?.let { canonicalKitsuIdForEntry = entry.id to it }
        }
        val cached = DetailCache.getMediaDetail(entry.source, stableId, entry.type.name)
            ?: DetailCache.getMediaDetail(entry.source, stableId)
        val detail = if (cached != null) {
            cached
        } else {
            _detailLoading.value = true
            val fetched = try {
                withContext(Dispatchers.IO) {
                    apiClient.fetchDetail(
                        source = entry.source,
                        externalId = effectiveExternalId,
                        mediaType = entry.type,
                        // TMDB zenginleştirmesi için entry'deki ID'leri ilet
                        tmdbId = entry.tmdbId,
                        // realMalId → Jikan/ARM için gerçek MAL ID'si; kaynak bazında hesaplanır
                        realMalId = realMalIdOf(entry),
                        title = entry.title
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during apiClient.fetchDetail: ${e.message}", e)
                PlatformFailureRegistry.report(entry.source, error = e)
                null
            }
            if (fetched != null) {
                DetailCache.putMediaDetail(entry.source, stableId, fetched, entry.type.name)
                if (effectiveExternalId > 0 && effectiveExternalId != stableId) {
                    DetailCache.putMediaDetail(entry.source, effectiveExternalId, fetched, entry.type.name)
                }
            }
            fetched
        }

        _detailState.value = detail
        _detailLoading.value = false
        _detailErrorCode.value = if (detail == null) {
            // Kaynak tarafında (HTTP / istisna) bildirilmiş güncel bir neden varsa onu, yoksa
            // genel "sınıflandırılamayan hata" kodunu göster; hata sessizce yutulmasın.
            PlatformFailureRegistry.latest(entry.source)?.code ?: PlatformErrorCode.COM_1020
        } else {
            null
        }
        if (detail == null && synopsisFetchJob?.isActive != true) {
            synopsisController.showErrorIfEmpty()
        }

        val expectedFetchKey = currentFetchKey

        if (detail != null) {
            val detailSynopsis = detail.synopsis?.takeIf { it.isNotBlank() }
            if (detailSynopsis != null) {
                synopsisController.submit(detailSynopsis)
            } else if (synopsisFetchJob?.isActive != true) {
                synopsisController.showErrorIfEmpty()
            }

            val determinedSeason = KitsugiEpisodeRatingsRepository.determineTargetSeason(
                tmdbSeason = detail.tmdbSeason,
                title = entry.title,
                titleEnglish = detail.titleEnglish,
                synonyms = detail.synonyms.orEmpty()
            )
            _targetSeason.value = determinedSeason

            // Detaydan gelen TMDB ID veya resimler varsa ve galeri henüz kısıtlıysa galeriyi zenginleştir.
            // Bangumi'de çapraz kimlikler detayla birlikte çözüldüğü için galeri her zaman bir kez yenilenir.
            val movieForGallery = KitsugiEpisodeRatingsRepository.prefersMovieLogo(
                type = detail.type ?: entry.type,
                format = detail.format,
                rawFormat = detail.rawFormat,
                subtitle = entry.subtitle,
                title = listOfNotNull(detail.title, entry.title, entry.titleEnglish).joinToString(" | ")
            )
            if (_galleryItems.value.size <= 2 || entry.source.equals("bangumi", ignoreCase = true) || movieForGallery) {
                val refreshToken = galleryTracker.begin()
                viewModelScope.launch {
                    try {
                        fetchFanartGallery(entry)
                    } catch (e: Exception) {
                        Log.e(TAG, "Post-detail gallery refresh failed: ${e.message}")
                    } finally {
                        galleryTracker.end(refreshToken)
                    }
                }
            }

            // Fetch episode ratings
            fetchEpisodeRatings(entry, detail)

            // ── Fragman yedek zinciri ─────────────────────────────────────────
            // Kaynağın kendisi YouTube fragmanı vermediyse (trailerUrl boş), arka planda
            // sırayla TMDB → diğer metadata kaynakları → Cloudstream eklentileri denenir.
            // Bulunan fragman detay durumuna ve önbelleğe yazılır; kart kendiliğinden belirir.
            if (detail.trailerUrl.isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        val trailer = com.kitsugi.animelist.data.trailer.DetailTrailerFallback.resolve(
                            context = getApplication(),
                            title = detail.title ?: entry.title,
                            year = detail.year ?: entry.year,
                            type = entry.type,
                            tmdbId = detail.tmdbId ?: entry.tmdbId,
                            malId = detail.realMalId ?: entry.malId,
                            sourceName = entry.source
                        )
                        if (!trailer.isNullOrBlank()) {
                            val current = _detailState.value
                            if (current != null && current.trailerUrl.isNullOrBlank()) {
                                val updated = current.copy(trailerUrl = trailer)
                                _detailState.value = updated
                                DetailCache.putMediaDetail(entry.source, stableId, updated)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fallback trailer resolution failed: ${e.message}")
                    }
                }
            }

            // Detay geldikten sonra logo yeniden seçilir: film/sezon ancak o zaman kesinleşir.
            val showLogos = runCatching { settingsDataStore.settingsFlow.first().showAnimeLogos }.getOrDefault(true)
            if (showLogos) {
                viewModelScope.launch {
                    try {
                        fetchLogo(entry, showLogos)
                    } catch (e: Exception) {
                        Log.e(TAG, "Post-detail logo fetch failed: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun fetchEpisodeRatings(entry: MediaEntry, detail: KitsugiMediaDetail) {
        val tmdbId = detail.tmdbId
        var foundRatings = emptyMap<Pair<Int, Int>, Double>()
        var resolvedId: Int? = null

        withContext(Dispatchers.IO) {
            when {
                tmdbId != null && tmdbId > 0 -> {
                    resolvedId = tmdbId
                    foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatings(tmdbId)
                }
                entry.source.equals("anilist", ignoreCase = true) -> {
                    val stableId = entry.malId ?: 0
                    if (stableId >= 100_000_000) {
                        val aniListId = stableId - 100_000_000
                        if (aniListId > 0) {
                            foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByAniListId(aniListId)
                            resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForAniList(aniListId)
                        }
                    } else if (stableId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(stableId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(stableId)
                    }

                    if (foundRatings.isEmpty()) {
                        val malId = detail.realMalId
                        if (malId != null && malId > 0) {
                            foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(malId)
                            resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(malId)
                        }
                    }
                }
                entry.source.equals("kitsu", ignoreCase = true) -> {
                    // Yalnızca 300M aralığındaki kanonik Kitsu stableId'si çözülür
                    val kitsuId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(entry.malId)
                    if (kitsuId != null && kitsuId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByKitsuId(kitsuId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForKitsu(kitsuId)
                    }
                }
                entry.source.equals("bangumi", ignoreCase = true) -> {
                    // Bangumi stableId'si MAL ID DEĞİLDİR: çözülen çapraz kimliklerle alınır.
                    val cross = runCatching {
                        KitsugiBangumiDetailClient.resolveCrossIds(entry.malId, entry.type)
                    }.getOrNull()
                    val malId = detail.realMalId?.takeIf { it in 1..99_999_999 } ?: cross?.malId
                    if (malId != null && malId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(malId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(malId)
                    }
                    if (foundRatings.isEmpty()) {
                        val aniListId = cross?.aniListId
                        if (aniListId != null && aniListId > 0) {
                            foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByAniListId(aniListId)
                            if (resolvedId == null) resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForAniList(aniListId)
                        }
                    }
                    if (resolvedId == null) resolvedId = cross?.tmdbId
                }
                entry.source.equals("shikimori", ignoreCase = true) -> {
                    // NOT: Shikimori ID'si MAL ID'si DEĞİLDİR. Önce gerçek MAL ID'si çözülür;
                    // bulunamazsa bölüm puanları sessizce boş kalır (alakasız yapımın puanları
                    // gösterilmez).
                    val malId = detail.realMalId?.takeIf { it > 0 }
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(entry.malId)
                    if (malId != null && malId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(malId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(malId)
                    }
                }
                else -> {
                    val malId = entry.malId
                    if (malId != null && malId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(malId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(malId)
                    }
                }
            }
        }

        _episodeRatings.value = foundRatings
        _resolvedTmdbId.value = resolvedId
    }

    private fun logoSelection(entry: MediaEntry): Pair<Boolean, Int?> {
        val detail = _detailState.value
        val isMovie = KitsugiEpisodeRatingsRepository.prefersMovieLogo(
            type = detail?.type ?: entry.type,
            format = detail?.format,
            rawFormat = detail?.rawFormat,
            subtitle = entry.subtitle,
            title = listOfNotNull(detail?.title, detail?.titleEnglish, entry.title, entry.titleEnglish).joinToString(" | ")
        )
        val season = (_targetSeason.value.takeIf { it > 0 }
            ?: KitsugiEpisodeRatingsRepository.determineTargetSeason(
                tmdbSeason = detail?.tmdbSeason,
                title = detail?.title ?: entry.title,
                titleEnglish = detail?.titleEnglish ?: entry.titleEnglish,
                synonyms = detail?.synonyms.orEmpty()
            )).takeIf { it > 0 }
        return isMovie to season
    }

    private suspend fun fetchLogo(entry: MediaEntry, showAnimeLogos: Boolean) {
        val isManga = entry.type == MediaType.Manga || _detailState.value?.type == MediaType.Manga
        if (!showAnimeLogos || isManga) {
            _logoUrl.value = null
            return
        }
        val stableId = entry.malId ?: 0
        val (isMovie, season) = logoSelection(entry)
        val logoTitles = listOfNotNull(
            entry.title, entry.titleEnglish, entry.titleJapanese,
            _detailState.value?.title, _detailState.value?.titleEnglish,
            _detailState.value?.titleRomaji, _detailState.value?.titleJapanese
        ).filter { it.isNotBlank() }.distinct()
        val explicitTmdb = (entry.tmdbId ?: _detailState.value?.tmdbId)?.takeIf { it > 0 }
        val logo = withContext(Dispatchers.IO) {
            val explicitLogo = explicitTmdb?.let {
                KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
            }?.takeIf { it.isNotBlank() }
            if (explicitLogo != null) {
                explicitLogo
            } else when {
                entry.source.equals("tmdb", ignoreCase = true) -> {
                    val tmdbId = entry.tmdbId ?: if (stableId > 0) stableId else null
                    if (tmdbId != null && tmdbId > 0) {
                        KitsugiEpisodeRatingsRepository.getLogoUrl(tmdbId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                    } else null
                }
                entry.source.equals("anilist", ignoreCase = true) -> {
                    if (stableId >= 100_000_000) {
                        val aniListId = stableId - 100_000_000
                        val realMal = _detailState.value?.realMalId
                        KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(aniListId, fallbackMalId = realMal, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                    } else {
                        KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                    }
                }
                entry.source.equals("kitsu", ignoreCase = true) -> {
                    val kitsuId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(stableId)
                    if (kitsuId != null && kitsuId > 0) {
                        KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(kitsuId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                    } else {
                        val malId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(stableId)
                        if (malId != null && malId > 0) KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(malId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) else null
                    }
                }
                entry.source.equals("simkl", ignoreCase = true) -> {
                    val detail = _detailState.value
                    val cross = com.kitsugi.animelist.data.remote.KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = stableId,
                        mediaType = entry.type,
                        hintTmdbId = entry.tmdbId ?: detail?.tmdbId,
                        hintMalId = detail?.realMalId
                    )
                    val simklTitles = (logoTitles + listOfNotNull(cross.title)).distinct()
                    val effectiveIsMovie = isMovie || cross.isMovie
                    val realMalId = (detail?.realMalId ?: cross.malId)?.takeIf { it > 0 && it != stableId }
                    val tmdbId = (entry.tmdbId ?: detail?.tmdbId ?: cross.tmdbId)?.takeIf { it > 0 }
                    tmdbId?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                        ?.takeIf { it.isNotBlank() }
                        ?: realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, fallbackAniListId = cross.aniListId, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                            ?.takeIf { it.isNotBlank() }
                        ?: cross.aniListId?.takeIf { it > 0 }?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(it, fallbackMalId = realMalId, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                            ?.takeIf { it.isNotBlank() }
                        ?: cross.kitsuId?.takeIf { it > 0 }?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(it, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                            ?.takeIf { it.isNotBlank() }
                }
                entry.source.equals("jikan", ignoreCase = true) ||
                entry.source.equals("mal", ignoreCase = true) -> {
                    if (stableId > 0) KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) else null
                }
                entry.source.equals("bangumi", ignoreCase = true) -> {
                    val cross = runCatching {
                        KitsugiBangumiDetailClient.resolveCrossIds(stableId, entry.type)
                    }.getOrNull()
                    val malId = _detailState.value?.realMalId?.takeIf { it in 1..99_999_999 } ?: cross?.malId
                    val aniListId = cross?.aniListId
                    val tmdb = entry.tmdbId ?: _detailState.value?.tmdbId ?: cross?.tmdbId
                    when {
                        malId != null && malId > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(malId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                        aniListId != null && aniListId > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(aniListId, fallbackMalId = null, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                        tmdb != null && tmdb > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrl(tmdb, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                        else -> null
                    }
                }
                entry.source.equals("shikimori", ignoreCase = true) -> {
                    val malId = _detailState.value?.realMalId?.takeIf { it > 0 }
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(stableId)
                    if (malId != null && malId > 0) KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(malId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) else null
                }
                stableId > 0 && !entry.source.equals("simkl", ignoreCase = true) &&
                    !entry.source.equals("kitsu", ignoreCase = true) -> {
                    KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                }
                else -> null
            }
        }
        _logoUrl.value = logo
    }

    /**
     * Fanart.tv galerisi + mevcut TMDB/Jikan resimlerini birleştirir.
     * Sonuçlar [_galleryItems] akışına yazılır; UI reaktif güncelleme alır.
     */
    private suspend fun fetchFanartGallery(entry: MediaEntry) {
        val isManga = entry.type == MediaType.Manga || _detailState.value?.type == MediaType.Manga
        if (isManga) {
            // Manga içerikleri TMDB veya Fanart.tv'de bulunmaz. ARM API üzerinden Kitsu manga ID'sinin
            // alakasız anime yapımları (örn. Berserk -> Hungry Heart) ile eşleştirilmesini ve galerinin
            // yanlış içeriklerle dolmasını engelliyoruz. Yalnızca eserin kendi görselleri eklenir.
            val currentDetail = _detailState.value
            val coverUrl = currentDetail?.imageUrl ?: entry.imageUrl
            val entryImg = entry.imageUrl
            val existingItems = buildList {
                if (!coverUrl.isNullOrBlank()) {
                    val src = determineSource(coverUrl, entry.source)
                    add(GalleryItem(url = coverUrl, source = src, category = GalleryCategory.POSTER))
                }
                if (!entryImg.isNullOrBlank() && entryImg != coverUrl) {
                    val src = determineSource(entryImg, entry.source)
                    add(GalleryItem(url = entryImg, source = src, category = GalleryCategory.POSTER))
                }
                currentDetail?.bannerImage?.let { bannerUrl ->
                    if (bannerUrl.isNotBlank() && bannerUrl != coverUrl && bannerUrl != entryImg) {
                        val src = determineSource(bannerUrl, entry.source)
                        add(GalleryItem(url = bannerUrl, source = src, category = GalleryCategory.BACKDROP))
                    }
                }
                currentDetail?.pictures?.forEach { picUrl ->
                    if (picUrl.isNotBlank() && picUrl != coverUrl && picUrl != entryImg && picUrl != currentDetail.bannerImage) {
                        val src = determineSource(picUrl, entry.source)
                        add(GalleryItem(url = picUrl, source = src, category = GalleryCategory.POSTER))
                    }
                }
            }
            // ── MAL (Jikan) /pictures: manga galerisine ek görseller ──────────────
            // Manga TMDB/Fanart.tv'de yok; en zengin ek görsel kaynağı MAL'dir. Kayıt hangi
            // platformdan açılmış olursa olsun gerçek MAL kimliği çözülebiliyorsa katılır.
            val mangaMalId = realMalIdForGallery(entry, bangumiCross = null)
            val malItems = fetchMalGalleryItems(mangaMalId, MediaType.Manga)

            _galleryItems.value = (existingItems + malItems)
                .distinctBy { KitsugiBangumiDetailClient.galleryDedupKey(it.url) }
            return
        }

        val isBangumiEntry = entry.source.equals("bangumi", ignoreCase = true)
        // Bangumi: Fanart.tv / TMDB / Shikimori görselleri için çapraz kimlikler (kalıcı önbellekli).
        val bangumiCross = if (isBangumiEntry) {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(15_000L) {
                    runCatching { KitsugiBangumiDetailClient.resolveCrossIds(entry.malId, entry.type) }.getOrNull()
                }
            }
        } else null

        val tmdbId = withContext(Dispatchers.IO) {
            val detailTmdb = _detailState.value?.tmdbId
            when {
                detailTmdb != null && detailTmdb > 0 -> detailTmdb
                entry.tmdbId != null && entry.tmdbId > 0 -> entry.tmdbId
                entry.source.equals("tmdb", ignoreCase = true) -> entry.malId?.takeIf { it > 0 }
                entry.source.equals("anilist", ignoreCase = true) -> {
                    val stableId = entry.malId ?: 0
                    if (stableId >= 100_000_000) {
                        val aniListId = stableId - 100_000_000
                        KitsugiEpisodeRatingsRepository.resolveTmdbIdFromAniList(aniListId)
                    } else {
                        KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(stableId)
                    }
                }
                entry.source.equals("kitsu", ignoreCase = true) -> {
                    val kitsuId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(entry.malId)
                    if (kitsuId != null && kitsuId > 0) {
                        KitsugiEpisodeRatingsRepository.resolveTmdbIdFromKitsu(kitsuId)
                    } else {
                        val malId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(entry.malId)
                        if (malId != null && malId > 0) KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId) else null
                    }
                }
                entry.source.equals("shikimori", ignoreCase = true) -> {
                    // Shikimori ID'si MAL ID'si DEĞİLDİR: TMDB kimliği yalnızca çözülen
                    // gerçek MAL ID'si üzerinden aranır (aksi hâlde galeri alakasız
                    // yapımın görselleriyle doluyordu).
                    val malId = _detailState.value?.realMalId?.takeIf { it > 0 }
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(entry.malId)
                    if (malId != null && malId > 0) KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId) else null
                }
                isBangumiEntry -> {
                    // Bangumi stableId'si MAL ID DEĞİLDİR; yalnızca çözülen çapraz kimlikler kullanılır.
                    bangumiCross?.tmdbId?.takeIf { it > 0 }
                        ?: bangumiCross?.malId?.takeIf { it > 0 }
                            ?.let { KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(it) }
                        ?: bangumiCross?.aniListId?.takeIf { it > 0 }
                            ?.let { KitsugiEpisodeRatingsRepository.resolveTmdbIdFromAniList(it) }
                }
                else -> entry.malId?.let { KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(it) }
            }
        }

        val isMovie = KitsugiEpisodeRatingsRepository.prefersMovieLogo(
            type = _detailState.value?.type ?: entry.type,
            format = _detailState.value?.format,
            rawFormat = _detailState.value?.rawFormat,
            subtitle = entry.subtitle,
            title = listOfNotNull(_detailState.value?.title, entry.title, entry.titleEnglish).joinToString(" | ")
        )

        val fallbackMalId: Int? = when {
            entry.source.equals("anilist", ignoreCase = true) -> {
                val m = entry.malId ?: 0
                if (m > 0 && m < 100_000_000) m else _detailState.value?.realMalId
            }
            // Kitsu kayıtlarında malId ya Kitsu stableId'dir (MAL ID değildir) ya da eski
            // kayıtlarda gerçek MAL ID. İkisini ayırıp yalnızca güvenli olanı kullanıyoruz.
            entry.source.equals("kitsu", ignoreCase = true) ->
                if (com.kitsugi.animelist.data.remote.KitsuIdNamespace.isStableId(entry.malId)) _detailState.value?.realMalId else com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(entry.malId)
            // Shikimori: entry.malId Shikimori kimliğidir; MAL ID'si detaydan/çözümden gelir.
            entry.source.equals("shikimori", ignoreCase = true) ->
                _detailState.value?.realMalId?.takeIf { it > 0 }
                    ?: KitsugiIdResolver.resolveMalIdFromShikimori(entry.malId)
            // Bangumi: entry.malId stableId'dir → çözülen gerçek MAL ID'si kullanılır.
            isBangumiEntry ->
                bangumiCross?.malId ?: _detailState.value?.realMalId?.takeIf { it in 1..99_999_999 }
            !entry.source.equals("tmdb", ignoreCase = true) -> entry.malId
            else -> null
        }
        val fallbackAniListId: Int? = if (entry.source.equals("anilist", ignoreCase = true)) {
            val m = entry.malId ?: 0
            if (m >= 100_000_000) m - 100_000_000 else null
        } else if (isBangumiEntry) {
            bangumiCross?.aniListId
        } else null
        val fallbackKitsuId: Int? = if (entry.source.equals("kitsu", ignoreCase = true)) {
            com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(entry.malId)
        } else if (isBangumiEntry) {
            bangumiCross?.kitsuId
        } else null

        // Kitsu listesi/detayında tür "Anime" olarak gelir; film olan Kitsu kaydını alt türünden anlarız.
        // Aksi halde film kaydının galerisine, TMDB ID'si aynı olan TV dizisinin görselleri karışır.
        val galleryDetail = _detailState.value
        val galleryIsMovie = isMovie ||
            galleryDetail?.type == MediaType.Movie ||
            KitsugiEpisodeRatingsRepository.isKitsuMovieId(fallbackKitsuId)
        // TMDB kimliği çapraz eşlemeyle bulunduğundan görseller başlık doğrulamasından geçer.
        val galleryTitles = listOfNotNull(
            entry.title, entry.titleEnglish, entry.titleJapanese,
            galleryDetail?.title, galleryDetail?.titleEnglish, galleryDetail?.titleRomaji,
            galleryDetail?.titleJapanese
        ).filter { it.isNotBlank() }.distinct()

        // MAL (Jikan) kimliği: MAL/Jikan kayıtlarında doğrudan, diğerlerinde çapraz eşlemeden.
        // Bulunamazsa istek yapılmaz — yanlış eserin görselleri galeriye karışmaz.
        val malGalleryId = realMalIdForGallery(entry, bangumiCross)

        val (fanartItems, tmdbItems, shikimoriItems, malItems) = coroutineScope {
            val fanartDef = async(Dispatchers.IO) {
                KitsugiEpisodeRatingsRepository.getFanartGalleryItems(
                    tmdbId = tmdbId ?: 0,
                    isMovie = galleryIsMovie,
                    fallbackMalId = fallbackMalId,
                    fallbackAniListId = fallbackAniListId,
                    fallbackKitsuId = fallbackKitsuId
                )
            }
            val tmdbDef = async(Dispatchers.IO) {
                if (tmdbId != null && tmdbId > 0) {
                    KitsugiEpisodeRatingsRepository.getTmdbGalleryItems(
                        tmdbId = tmdbId,
                        isMovie = galleryIsMovie,
                        expectedTitles = galleryTitles,
                        seasonNumber = if (galleryIsMovie) null else _targetSeason.value
                    )
                } else emptyList()
            }
            val shikimoriDef = async(Dispatchers.IO) {
                val isAnime = entry.type == MediaType.Anime
                val shikimoriAnimeId = when {
                    entry.source.equals("shikimori", ignoreCase = true) -> entry.malId?.takeIf { it > 0 }
                    entry.source.equals("anilist", ignoreCase = true) -> {
                        val m = entry.malId ?: 0
                        if (m > 0 && m < 100_000_000) m else _detailState.value?.realMalId
                    }
                    entry.source.equals("mal", ignoreCase = true) || entry.source.equals("jikan", ignoreCase = true) -> entry.malId?.takeIf { it > 0 }
                    // Shikimori anime kimliği = MAL kimliği: Bangumi'de çözülen MAL ID ile ekran görüntüleri çekilir.
                    isBangumiEntry -> bangumiCross?.malId ?: _detailState.value?.realMalId?.takeIf { it in 1..99_999_999 }
                    else -> _detailState.value?.realMalId
                }
                if (isAnime && shikimoriAnimeId != null && shikimoriAnimeId > 0) {
                    KitsugiShikimoriClient.fetchScreenshots(shikimoriAnimeId)
                } else emptyList()
            }
            val malDef = async(Dispatchers.IO) {
                fetchMalGalleryItems(
                    malId = malGalleryId,
                    mediaType = if (entry.type == MediaType.Manga || _detailState.value?.type == MediaType.Manga) {
                        MediaType.Manga
                    } else {
                        MediaType.Anime
                    }
                )
            }
            // (Fanart, TMDB, Shikimori, MAL) — dördü PARALEL çekilir.
            listOf(fanartDef.await(), tmdbDef.await(), shikimoriDef.await(), malDef.await())
        }
        val malGalleryItems = malItems

        // Mevcut TMDB/Jikan resimlerini de GalleryItem'a çevir ve birleştir
        val currentDetail = _detailState.value
        val coverUrl = currentDetail?.imageUrl ?: entry.imageUrl
        val entryImg = entry.imageUrl
        val existingItems = buildList {
            if (!coverUrl.isNullOrBlank()) {
                val src = determineSource(coverUrl, entry.source)
                add(GalleryItem(url = coverUrl, source = src, category = GalleryCategory.POSTER))
            }
            if (!entryImg.isNullOrBlank() && entryImg != coverUrl) {
                val src = determineSource(entryImg, entry.source)
                add(GalleryItem(url = entryImg, source = src, category = GalleryCategory.POSTER))
            }
            currentDetail?.pictures?.forEach { url ->
                if (url.isNotBlank() && url != coverUrl && url != entryImg) {
                    val src = determineSource(url, entry.source)
                    val cat = determineCategory(url, GalleryCategory.POSTER)
                    add(GalleryItem(url = url, source = src, category = cat))
                }
            }
        }

        // TMDB, Fanart ve Shikimori öğeleri çözünürlük, dil gibi zengin meta verilere sahiptir.
        // Önceden var olan öğeler zengin listede varsa metadata ile zenginleştirilsin.
        val richMap = (tmdbItems + fanartItems + shikimoriItems + malGalleryItems).associateBy { it.url }
        val enrichedExisting = existingItems.map { item ->
            richMap[item.url] ?: item
        }
        // `lain.bgm.tv` aynı kapağı farklı boyut yollarıyla verir (kopya görünürdü) → kanonik anahtarla tekilleştir.
        val allItems = (enrichedExisting + tmdbItems + fanartItems + shikimoriItems + malGalleryItems)
            .distinctBy { KitsugiBangumiDetailClient.galleryDedupKey(it.url) }

        val preferredLang = runCatching { KitsugiEpisodeRatingsRepository.getPreferredImageLanguage() }.getOrDefault("en")
        val sortedItems = allItems.sortedWith(
            compareBy(
                { item ->
                    when (item.category) {
                        GalleryCategory.POSTER -> 0
                        GalleryCategory.BACKDROP -> 1
                        GalleryCategory.LOGO -> 2
                        GalleryCategory.CLEARART -> 3
                        GalleryCategory.THUMBNAIL -> 4
                        GalleryCategory.CHARACTER -> 5
                        GalleryCategory.PERSON    -> 5
                        GalleryCategory.BANNER -> 6
                        GalleryCategory.SQUARE -> 7
                        GalleryCategory.OTHER -> 8
                    }
                },
                { item -> KitsugiEpisodeRatingsRepository.galleryLanguageRank(item.language, preferredLang) },
                { item -> KitsugiEpisodeRatingsRepository.gallerySeasonRank(item.season, _targetSeason.value) },
                { item -> if (item.url == coverUrl) 0 else 1 }
            )
        )
        _galleryItems.value = sortedItems
    }

    /**
     * Galeri için GÜVENLİ MAL kimliği.
     *
     * AniList/Kitsu/Bangumi "stable ID"leri veya Simkl/TMDB kimlikleri MAL kimliği DEĞİLDİR;
     * bunları Jikan'a göndermek yanlış eserin görsellerini galeriye doldururdu. Bu yüzden
     * yalnızca kanıtlanmış eşlemeler kullanılır; aksi halde detaydan çözülen `realMalId`'ye
     * düşülür ve o da yoksa hiç istek yapılmaz.
     */
    private fun realMalIdForGallery(
        entry: MediaEntry,
        bangumiCross: KitsugiBangumiDetailClient.CrossIds?
    ): Int? {
        val detail = _detailState.value
        val candidate = when {
            entry.source.equals("bangumi", ignoreCase = true) -> bangumiCross?.malId
            entry.source.equals("anilist", ignoreCase = true) -> entry.malId?.takeIf { it in 1..99_999_999 }
            entry.source.equals("kitsu", ignoreCase = true) ->
                if (com.kitsugi.animelist.data.remote.KitsuIdNamespace.isStableId(entry.malId)) {
                    null
                } else {
                    com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(entry.malId)
                }
            entry.source.equals("shikimori", ignoreCase = true) ->
                _detailState.value?.realMalId?.takeIf { it > 0 }
                    ?: runCatching { KitsugiIdResolver.resolveMalIdFromShikimori(entry.malId) }.getOrNull()
            entry.source.equals("tmdb", ignoreCase = true) -> null
            else -> entry.malId?.takeIf { it in 1..99_999_999 }
        }
        return candidate?.takeIf { it in 1..99_999_999 }
            ?: detail?.realMalId?.takeIf { it in 1..99_999_999 }
    }

    /**
     * MAL (Jikan) `/pictures` uç noktasından galeri öğeleri.
     * MAL'a bağlı kaynaklar (Jikan, Shikimori ve MAL kimliği çözülen tüm platformlar) bu
     * galeriyi paylaşır. Kimlik yoksa veya istek başarısızsa boş liste döner.
     */
    private suspend fun fetchMalGalleryItems(malId: Int?, mediaType: MediaType): List<GalleryItem> {
        if (malId == null || malId !in 1..99_999_999) return emptyList()
        val urls = runCatching {
            withTimeoutOrNull(12_000L) {
                com.kitsugi.animelist.data.remote.KitsugiMalDetailClient
                    .fetchGalleryPictures(malId, mediaType)
            }
        }.getOrNull().orEmpty()
        if (urls.isEmpty()) return emptyList()
        return urls.mapNotNull { url ->
            val clean = url.trim()
            if (clean.isBlank()) return@mapNotNull null
            GalleryItem(
                url = clean,
                source = determineSource(clean, "jikan"),
                category = determineCategory(clean, GalleryCategory.POSTER),
                description = "MAL / Jikan ek görsel"
            )
        }
    }

    private fun determineSource(url: String, fallbackSource: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("fanart.tv") -> "Fanart.tv"
            lowerUrl.contains("image.tmdb.org") || lowerUrl.contains("tmdb.org") -> "TMDB"
            lowerUrl.contains("shikimori.one") || lowerUrl.contains("shikimori.me") -> "Shikimori"
            lowerUrl.contains("anilist.co") -> "AniList"
            lowerUrl.contains("simkl.in") || lowerUrl.contains("simkl.com") -> "Simkl"
            lowerUrl.contains("myanimelist.net") || lowerUrl.contains("jikan.moe") -> "Jikan (MAL)"
            lowerUrl.contains("kitsu.io") || lowerUrl.contains("kitsu.app") -> "Kitsu"
            lowerUrl.contains("bgm.tv") -> "Bangumi"
            else -> {
                when (fallbackSource.lowercase()) {
                    "bangumi" -> "Bangumi"
                    "shikimori" -> "Shikimori"
                    "anilist" -> "AniList"
                    "tmdb" -> "TMDB"
                    "simkl" -> "Simkl"
                    "jikan", "mal" -> "Jikan (MAL)"
                    "kitsu" -> "Kitsu"
                    else -> fallbackSource.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
    }

    private fun determineCategory(url: String, defaultCategory: GalleryCategory): GalleryCategory {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("clearart")                                                                       -> GalleryCategory.CLEARART
            lowerUrl.contains("logo")                                                                           -> GalleryCategory.LOGO
            lowerUrl.contains("backdrop") || lowerUrl.contains("background") || lowerUrl.contains("/w1280") || lowerUrl.contains("showbackground") -> GalleryCategory.BACKDROP
            lowerUrl.contains("square")                                                                         -> GalleryCategory.SQUARE
            lowerUrl.contains("poster") || lowerUrl.contains("/w780") || lowerUrl.contains("/w500") || lowerUrl.contains("/w342") || lowerUrl.contains("coverimage") || lowerUrl.contains("large_image_url") -> GalleryCategory.POSTER
            lowerUrl.contains("character") || lowerUrl.contains("actor") || lowerUrl.contains("voiceactor")    -> GalleryCategory.CHARACTER
            lowerUrl.contains("thumb") || lowerUrl.contains("still") || lowerUrl.contains("/w300") || lowerUrl.contains("/w185") -> GalleryCategory.THUMBNAIL
            lowerUrl.contains("banner")                                                                         -> GalleryCategory.BANNER
            else -> defaultCategory
        }
    }

    private suspend fun fetchSynopsis(entry: MediaEntry, expectedFetchKey: String) {
        val available = bestAvailableSynopsis(_detailState.value?.synopsis, entry.synopsis, null)
        if (available != null) {
            if (currentFetchKey == expectedFetchKey) synopsisController.submit(available)
            return
        }
        val effectiveExternalId = externalIdOf(entry)
        val apiSynopsis = try {
            withContext(Dispatchers.IO) {
                apiClient.fetchSynopsis(
                    source = entry.source,
                    externalId = effectiveExternalId.takeIf { it > 0 } ?: entry.malId,
                    mediaType = entry.type
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        if (currentFetchKey != expectedFetchKey) return
        val raw = bestAvailableSynopsis(
            _detailState.value?.synopsis,
            synopsisController.state.value.original,
            apiSynopsis
        )
        if (raw != null) synopsisController.submit(raw)
        else if (!_detailLoading.value) synopsisController.showErrorIfEmpty()
    }

    fun translateSynopsis(entry: MediaEntry) {
        val raw = synopsisController.state.value.original ?: _detailState.value?.synopsis ?: entry.synopsis ?: return
        synopsisController.submit(raw, force = true)
    }

    /**
     * Lazy-loads tabs data when a specific tab index is selected.
     */
    /**
     * Sekme verisini yükler.
     *
     * - Aynı sekme zaten yükleniyorsa yeni istek başlatılmaz (tek uçuş): sekme
     *   tetikleyicisi birden çok kez çalışsa bile istekler birikmez.
     * - Her veri çağrısı süre tavanıyla sınırlıdır; aksi hâlde yavaş bir zincir
     *   (ör. Shikimori → MAL eşlemesi → Jikan) sekmeyi sonsuza dek skeleton'da bırakır.
     */
    fun loadTab(tabIndex: Int, entry: MediaEntry, realMalId: Int?) {
        val effectiveExternalId = externalIdOf(entry)
        // Tek uçuş anahtarı: aynı parametrelerle çalışan istek varsa yenisini başlatma;
        // parametreler iyileştiğinde (ör. detaydan gerçek MAL ID'si geldiğinde) ise
        // eski isteği iptal edip yeniden dene.
        val requestKey = "$effectiveExternalId|${realMalId ?: 0}"
        val running = tabJobs[tabIndex]
        if (running != null && running.isActive && tabRequestKeys[tabIndex] == requestKey) return
        running?.cancel()
        tabRequestKeys[tabIndex] = requestKey

        // Önbellek anahtarı Kitsu'da kanonik kimlik üzerinden kurulur (bkz. cacheIdentityOf)
        val malId = cacheIdentityOf(entry)
        // NOT: Shikimori ID'si MAL ID'si DEĞİLDİR. Bu yüzden Shikimori kayıtlarında
        // entry.malId'yi MAL ID gibi kullanmıyoruz; gerçek MAL ID'si detaydan gelir ya da
        // istemci tarafında (detay önbelleği → ARM → Shikimori API) çözülür.
        val effectiveRealMalId = realMalId
            ?: _detailState.value?.realMalId
            ?: (if (entry.source.equals("mal", true) || entry.source.equals("jikan", true)) entry.malId else null)
        val isManga = entry.type == MediaType.Manga || _detailState.value?.type == MediaType.Manga
        val tmdbId = if (isManga) null else (entry.tmdbId ?: _detailState.value?.tmdbId ?: _resolvedTmdbId.value)
        tabJobs[tabIndex] = viewModelScope.launch {
            try {
                when (tabIndex) {
                    1 -> {
                        // Resimler (Gallery) sekmesi — görseller loadGallery ile yüklenir
                    }
                    2 -> {
                        val currentSuccess = _charactersState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaCharacters(entry.source, malId) == null)
                        if (needsRefetch) {
                            _charactersState.value = DetailTabState.Loading
                            val resolvedTitle = _detailState.value?.title
                                ?: _detailState.value?.titleEnglish
                                ?: _detailState.value?.titleRomaji
                                ?: entry.title
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchCharacters(
                                    source = entry.source,
                                    externalId = effectiveExternalId,
                                    mediaType = entry.type,
                                    realMalId = effectiveRealMalId,
                                    tmdbId = tmdbId,
                                    title = resolvedTitle
                                )
                            } ?: run {
                                _charactersState.value = DetailTabState.Error(tabFailureCode(entry.source))
                                return@launch
                            }
                            val result = fetched.value
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaCharacters(entry.source, malId, result)
                            }
                            _charactersState.value = DetailTabState.Success(result)
                            warmBangumiCharacterNames(entry.source, malId, result)
                        }
                    }
                    3 -> {
                        val currentSuccess = _staffState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaStaff(entry.source, malId) == null)
                        if (needsRefetch) {
                            _staffState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchStaff(
                                    entry.source,
                                    effectiveExternalId,
                                    entry.type,
                                    tmdbId = tmdbId,
                                    realMalId = effectiveRealMalId
                                )
                            } ?: run {
                                _staffState.value = DetailTabState.Error(tabFailureCode(entry.source))
                                return@launch
                            }
                            val result = fetched.value
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaStaff(entry.source, malId, result)
                            }
                            _staffState.value = DetailTabState.Success(result)
                            warmBangumiStaffNames(entry.source, malId, result)
                        }
                    }
                    4 -> {
                        val currentSuccess = _recommendationsState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaRecommendations(entry.source, malId) == null)
                        if (needsRefetch) {
                            _recommendationsState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchRecommendations(
                                    entry.source,
                                    effectiveExternalId,
                                    entry.type,
                                    tmdbId = tmdbId,
                                    realMalId = effectiveRealMalId,
                                    title = entry.title
                                )
                            } ?: run {
                                _recommendationsState.value = DetailTabState.Error(tabFailureCode(entry.source))
                                return@launch
                            }
                            val result = fetched.value
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaRecommendations(entry.source, malId, result)
                            }
                            _recommendationsState.value = DetailTabState.Success(result)
                            warmBangumiRelationTitles(entry.source, malId, result, recommendations = true)
                        }
                    }
                    5 -> {
                        val currentSuccess = _relationsState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaRelations(entry.source, malId) == null)
                        if (needsRefetch) {
                            _relationsState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchRelations(
                                    entry.source,
                                    effectiveExternalId,
                                    entry.type,
                                    tmdbId = tmdbId,
                                    realMalId = effectiveRealMalId,
                                    title = entry.title
                                )
                            } ?: run {
                                _relationsState.value = DetailTabState.Error(tabFailureCode(entry.source))
                                return@launch
                            }
                            val result = fetched.value
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaRelations(entry.source, malId, result)
                            }
                            _relationsState.value = DetailTabState.Success(result)
                            warmBangumiRelationTitles(entry.source, malId, result, recommendations = false)
                        }
                    }
                    6 -> {
                        if (_statsState.value !is DetailTabState.Success) {
                            _statsState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchStats(
                                    entry.source,
                                    effectiveExternalId,
                                    entry.type,
                                    realMalId = effectiveRealMalId,
                                    tmdbId = tmdbId
                                )
                            } ?: run {
                                _statsState.value = DetailTabState.Error(tabFailureCode(entry.source))
                                return@launch
                            }
                            val result = fetched.value
                            if (result != null) {
                                DetailCache.putMediaStats(entry.source, malId, result)
                                _statsState.value = DetailTabState.Success(result)
                            } else {
                                _statsState.value = DetailTabState.Success(null)
                            }
                        }
                    }
                    7 -> {
                        val currentSuccess = _reviewsState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaReviews(entry.source, malId, entry.type.name) == null)
                        if (needsRefetch) {
                            _reviewsState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchReviews(
                                    entry.source,
                                    effectiveExternalId,
                                    entry.type,
                                    tmdbId = tmdbId,
                                    realMalId = effectiveRealMalId
                                )
                            } ?: run {
                                _reviewsState.value = DetailTabState.Error(tabFailureCode(entry.source))
                                return@launch
                            }
                            val result = fetched.value
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaReviews(entry.source, malId, result, entry.type.name)
                            }
                            _reviewsState.value = DetailTabState.Success(result)
                        }
                    }
                    8 -> {
                        val currentEpisodes = _episodesState.value
                        val needsEpFetch = currentEpisodes !is DetailTabState.Success ||
                            (currentEpisodes is DetailTabState.Success && currentEpisodes.data.isEmpty())
                        if (needsEpFetch) {
                            _episodesState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout(TAB_EPISODES_FETCH_TIMEOUT_MS) {
                                apiClient.fetchEpisodes(
                                    source = entry.source,
                                    externalId = effectiveExternalId,
                                    mediaType = entry.type,
                                    realMalId = effectiveRealMalId,
                                    totalEpisodes = maxOf(entry.total ?: _detailState.value?.total ?: 0, entry.progress),
                                    context = context,
                                    targetSeason = _targetSeason.value,
                                    tmdbId = tmdbId
                                )
                            } ?: run {
                                _episodesState.value = DetailTabState.Error(tabFailureCode(entry.source))
                                return@launch
                            }
                            val result = fetched.value
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaEpisodes(entry.source, malId, result)
                            }
                            _episodesState.value = DetailTabState.Success(result)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading tab data for index $tabIndex: ${e.message}", e)
                when (tabIndex) {
                    2 -> _charactersState.value = DetailTabState.Error(tabFailureCode(entry.source))
                    3 -> _staffState.value = DetailTabState.Error(tabFailureCode(entry.source))
                    4 -> _recommendationsState.value = DetailTabState.Error(tabFailureCode(entry.source))
                    5 -> _relationsState.value = DetailTabState.Error(tabFailureCode(entry.source))
                    6 -> _statsState.value = DetailTabState.Error(tabFailureCode(entry.source))
                    7 -> _reviewsState.value = DetailTabState.Error(tabFailureCode(entry.source))
                    8 -> _episodesState.value = DetailTabState.Error(tabFailureCode(entry.source))
                }
            }
        }
    }

    /**
     * Sekme veri çağrısını süre sınırıyla çalıştırır. Süre dolarsa null döner ve iş iptal edilir.
     *
     * ÖNEMLİ: `withTimeoutOrNull { withContext(IO) { bloklayan ağ çağrısı } }` sert bir sınır
     * DEĞİLDİR; burada iş bir ÇOCUK `async` olarak başlatılır ve yalnızca `await()` sınırlanır,
     * böylece yavaş bir ağ zinciri sekmeyi süresiz skeleton'da bırakamaz.
     */
    private suspend fun <T> CoroutineScope.fetchTabWithTimeout(
        timeoutMs: Long = TAB_FETCH_TIMEOUT_MS,
        block: suspend () -> T
    ): TabFetch<T>? {
        val work = async(Dispatchers.IO) { block() }
        val fetched: TabFetch<T>? = withTimeoutOrNull(timeoutMs) { TabFetch(work.await()) }
        if (fetched == null) work.cancel()
        return fetched
    }

    /** Süre sınırlı sekme çağrısının sarmalayıcısı (null değerli sonuçları ayırt etmek için). */
    private class TabFetch<T>(val value: T)

    /** Önceki kaydın sekme yüklemelerini iptal eder; eski veri yeni kaydın state'ine yazılamaz. */
    private fun cancelTabLoads() {
        tabJobs.values.forEach { it.cancel() }
        tabJobs.clear()
        tabRequestKeys.clear()
    }

    fun deleteMangaMapping(mediaId: Int) {
        viewModelScope.launch {
            mangaRepository.deleteMangaMapping(mediaId)
        }
    }

    fun setTargetSeason(season: Int, entry: MediaEntry) {
        _targetSeason.value = season
        _episodesState.value = DetailTabState.Loading
        DetailCache.removeMediaEpisodes(entry.source, entry.malId ?: 0)
        // Bölüm sekmesi = 8 (7 = Yorumlar). Yanlış indeks bölümleri hiç yüklemiyordu.
        loadTab(8, entry, _detailState.value?.realMalId)
        viewModelScope.launch {
            val showLogos = runCatching { settingsDataStore.settingsFlow.first().showAnimeLogos }.getOrDefault(true)
            if (showLogos) {
                runCatching { fetchLogo(entry, true) }
            }
            // Rebuild the image list using the selected season's TMDB artwork cache.
            if (_galleryItems.value.isNotEmpty()) {
                runCatching { fetchFanartGallery(entry) }
            }
            val seasonVideoToken = videosTracker.begin()
            try {
                fetchAggregatedVideosForEntry(entry, currentFetchKey)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
            } finally {
                videosTracker.end(seasonVideoToken)
            }
        }
    }

    private fun refreshMdbListRatings(entry: MediaEntry) {
        val requestKey = currentFetchKey ?: return
        mdbListFetchGeneration++
        val generation = mdbListFetchGeneration
        mdbListFetchJob?.cancel()
        _mdbListRatings.value = null
        _mdbListLoading.value = false
        mdbListFetchJob = viewModelScope.launch {
            try {
                fetchMdbListRatingsForEntry(entry, requestKey, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "MDBList entry rating fetch failed", e)
                if (generation == mdbListFetchGeneration && currentFetchKey == requestKey) {
                    _mdbListRatings.value = null
                    _mdbListLoading.value = false
                }
            }
        }
    }

    /**
     * Resolves provider-specific identities before querying MDBList. Stored IDs are not
     * interchangeable: a Bangumi/Kitsu/TMDB ID must never be sent as a MAL or IMDb ID.
     */
    private suspend fun fetchMdbListRatingsForEntry(
        entry: MediaEntry,
        requestKey: String,
        generation: Long
    ) {
        fun isCurrentRequest(): Boolean =
            generation == mdbListFetchGeneration && currentFetchKey == requestKey

        val context = getApplication<android.app.Application>().applicationContext
        val settings = settingsDataStore.settingsFlow.first()
        if (!isCurrentRequest()) return
        if (!settings.mdbListEnabled || settings.mdbListApiKey.isBlank()) {
            _mdbListRatings.value = null
            _mdbListLoading.value = false
            // Özellik kapalı/anahtar yok: detay kartında bayat kota rozeti çizilmesin.
            MdbListQuotaCenter.bind(context, "")
            return
        }

        // Kota anahtara özgüdür: diskteki son kayıt bu anahtarla eşleşiyorsa anında gösterilir.
        MdbListQuotaCenter.bind(context, settings.mdbListApiKey)

        // Manga/MDBList uyumsuz: MDBList film ve dizi puanları verir, manga için yok.
        if (entry.type == MediaType.Manga) {
            _mdbListRatings.value = null
            _mdbListLoading.value = false
            return
        }

        _mdbListLoading.value = true
        try {
            var detail = _detailState.value
            val rawSource = entry.source.trim().lowercase()
            val source = when (rawSource) {
                "bgm" -> "bangumi"
                "myanimelist", "jikan" -> "mal"
                else -> rawSource
            }
            val storedId = entry.malId?.takeIf { it > 0 }
            val isAniList = source == "anilist"
            val aniListId = storedId?.takeIf { isAniList && it >= 100_000_000 }
                ?.minus(100_000_000)
            var malId: Int? = when {
                source == "tmdb" -> detail?.realMalId?.takeIf { it in 1..99_999_999 }
                isAniList && aniListId != null -> detail?.realMalId?.takeIf { it in 1..99_999_999 }
                source == "bangumi" -> detail?.realMalId?.takeIf { it in 1..99_999_999 }
                source == "kitsu" -> detail?.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(storedId)
                else -> detail?.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: storedId?.takeIf { it in 1..99_999_999 }
            }
            var kitsuId: Int? = if (source == "kitsu") {
                com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(storedId)
            } else null
            val bangumiId = storedId.takeIf { source == "bangumi" }
            var tmdbId = entry.tmdbId ?: detail?.tmdbId ?: _resolvedTmdbId.value
            if (source == "tmdb" && tmdbId == null) tmdbId = storedId

            var imdbId = detail?.externalLinks
                ?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() }

            if (source == "simkl") {
                val simklId = entry.simklId?.takeIf { it > 0 }
                if (simklId != null) {
                    val crossIds = KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = simklId,
                        mediaType = entry.type,
                        hintTmdbId = tmdbId,
                        hintMalId = malId
                    )
                    if (malId == null) malId = crossIds.malId
                    if (tmdbId == null) tmdbId = crossIds.tmdbId
                    if (imdbId.isNullOrBlank()) imdbId = crossIds.imdbId
                    if (kitsuId == null) kitsuId = crossIds.kitsuId
                }
            }

            // AniList list entries can carry either its 100M-offset media ID or a real MAL ID.
            // Bangumi and Kitsu are passed through their own namespaces to the resolver.
            if (malId != null || aniListId != null || tmdbId != null || kitsuId != null || bangumiId != null) {
                val resolved = KitsugiIdResolver.resolveIds(
                    malId = malId,
                    aniListId = aniListId,
                    tmdbId = tmdbId,
                    mediaType = entry.type,
                    kitsuId = kitsuId,
                    bangumiId = bangumiId
                )
                if (imdbId.isNullOrBlank()) imdbId = resolved.imdbId
                if (tmdbId == null) tmdbId = resolved.tmdbId
            }

            // Detail metadata can expose external IDs that were not present on the library
            // row. Retry with it only if the row/resolver produced no usable MDBList identity.
            if (imdbId.isNullOrBlank() && (tmdbId == null || tmdbId <= 0) && _detailLoading.value) {
                withTimeoutOrNull(TAB_FETCH_TIMEOUT_MS) { _detailLoading.first { !it } }
                if (!isCurrentRequest()) return
                detail = _detailState.value
                tmdbId = tmdbId ?: detail?.tmdbId ?: _resolvedTmdbId.value
                if (source == "tmdb" && tmdbId == null) tmdbId = storedId
                if (imdbId.isNullOrBlank()) {
                    imdbId = detail?.externalLinks
                        ?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                        ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() }
                }
                if (imdbId.isNullOrBlank() || tmdbId == null) {
                    val resolved = KitsugiIdResolver.resolveIds(
                        malId = detail?.realMalId?.takeIf { it in 1..99_999_999 } ?: malId,
                        aniListId = aniListId,
                        tmdbId = tmdbId,
                        mediaType = entry.type,
                        kitsuId = kitsuId,
                        bangumiId = bangumiId
                    )
                    if (imdbId.isNullOrBlank()) imdbId = resolved.imdbId
                    if (tmdbId == null) tmdbId = resolved.tmdbId
                }
            }

            if (!isCurrentRequest()) return
            if (!imdbId.isNullOrBlank() || (tmdbId != null && tmdbId > 0)) {
                val ratings = MdbListClient.fetchRatings(
                    imdbId = imdbId,
                    tmdbId = tmdbId,
                    apiKey = settings.mdbListApiKey
                )
                if (isCurrentRequest()) _mdbListRatings.value = ratings
            } else {
                Log.d(TAG, "MDBList skipped: no IMDb or TMDB identity is available for source=$source")
                if (isCurrentRequest()) _mdbListRatings.value = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MDBList entry rating fetch failed", e)
            if (isCurrentRequest()) _mdbListRatings.value = null
        } finally {
            if (isCurrentRequest()) _mdbListLoading.value = false
        }

        viewModelScope.launch {
            MdbListQuotaCenter.refresh(settings.mdbListApiKey, maxAgeMillis = 10L * 60_000L)
        }
    }

    private suspend fun fetchAggregatedVideosForEntry(entry: MediaEntry, expectedKey: String?) {
        if (expectedKey != null && currentFetchKey != expectedKey) return
        val stableId = cacheIdentityOf(entry)
        val season = _targetSeason.value
        DetailCache.getMediaVideos(entry.source, stableId, season)?.let { cached ->
            if (expectedKey == null || currentFetchKey == expectedKey) {
                _videoItems.value = cached
                enrichEntryDetailWithCrossSourceVideos(entry, stableId, cached)
            }
            return
        }

        val currentDetail = _detailState.value
        val initialSeed = KitsugiMediaVideoAggregator.seedFromDetail(currentDetail, entry.source)
        if (initialSeed.isNotEmpty() && _videoItems.value.isEmpty()) {
            _videoItems.value = initialSeed
        }

        val imdbFromLinks = currentDetail?.externalLinks
            ?.firstOrNull { it.url.contains("imdb.com/title/") }
            ?.url?.substringAfter("/title/")?.substringBefore("/")?.substringBefore("?")

        val srcLower = entry.source.trim().lowercase()
        val rawStored = entry.malId
        val knownMalId = when {
            currentDetail?.realMalId?.let { it in 1..99_999_999 } == true -> currentDetail.realMalId
            srcLower in setOf("mal", "jikan", "myanimelist") -> rawStored?.takeIf { it in 1..99_999_999 }
            srcLower == "anilist" -> rawStored?.takeIf { it in 1..99_999_999 }
            else -> null
        }
        val knownAniListId = if (srcLower == "anilist" && rawStored != null && rawStored in 100_000_001..199_999_999) {
            rawStored - 100_000_000
        } else null
        val knownKitsuId = if (srcLower == "kitsu" && rawStored != null) {
            com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(rawStored)
                ?: rawStored.takeIf { it in 1..99_999_999 && knownMalId != it }
        } else null
        val knownShikimoriId = if (srcLower == "shikimori") rawStored?.takeIf { it > 0 } else null
        val knownSimklId = entry.simklId?.takeIf { it > 0 }
            ?: if (srcLower == "simkl") rawStored?.takeIf { it > 0 } else null
        val knownBangumiId = if (srcLower == "bangumi") {
            com.kitsugi.animelist.data.remote.BangumiIdNamespace.rawIdOrNull(rawStored)
        } else null

        val knownIds = KnownVideoMediaIds(
            malId = knownMalId,
            aniListId = knownAniListId,
            kitsuId = knownKitsuId,
            tmdbId = (entry.tmdbId ?: currentDetail?.tmdbId ?: _resolvedTmdbId.value)?.takeIf { it > 0 },
            simklId = knownSimklId,
            shikimoriId = knownShikimoriId,
            bangumiId = knownBangumiId,
            imdbId = imdbFromLinks
        )

        val fallbackTitle = listOfNotNull(
            currentDetail?.title,
            entry.title,
            currentDetail?.titleEnglish ?: entry.titleEnglish,
            currentDetail?.titleRomaji,
            currentDetail?.titleJapanese ?: entry.titleJapanese
        ).firstOrNull { it.isNotBlank() }

        val merged = KitsugiMediaVideoAggregator.aggregateVideos(
            id = rawStored ?: stableId,
            source = entry.source,
            mediaType = currentDetail?.type ?: entry.type,
            detail = currentDetail,
            tmdbClient = tmdbVideoClient,
            knownIds = knownIds,
            fallbackTitle = fallbackTitle,
            seasonNumber = season,
            onProgress = { partial ->
                if (expectedKey == null || currentFetchKey == expectedKey) {
                    _videoItems.value = partial
                }
            }
        )

        if (stableId > 0 && merged.isNotEmpty()) {
            DetailCache.putMediaVideos(entry.source, stableId, merged, season)
        }

        if (expectedKey == null || currentFetchKey == expectedKey) {
            _videoItems.value = merged
            enrichEntryDetailWithCrossSourceVideos(entry, stableId, merged)
        }
    }

    private fun enrichEntryDetailWithCrossSourceVideos(
        entry: MediaEntry,
        stableId: Int,
        videos: List<MediaVideoItem>
    ) {
        val current = _detailState.value ?: return
        val firstTrailer = videos.firstOrNull {
            it.category == MediaVideoCategory.TRAILER && !it.videoUrl.isNullOrBlank()
        }
        val crossOps = if (current.openings.isEmpty()) {
            videos.filter { it.category == MediaVideoCategory.OPENING }
                .map { KitsugiTheme(label = it.title, videoUrl = it.videoUrl, source = it.source) }
        } else emptyList()
        val crossEds = if (current.endings.isEmpty()) {
            videos.filter { it.category == MediaVideoCategory.ENDING }
                .map { KitsugiTheme(label = it.title, videoUrl = it.videoUrl, source = it.source) }
        } else emptyList()

        val needsTrailer = current.trailerUrl.isNullOrBlank() && firstTrailer != null
        val needsOps = current.openings.isEmpty() && crossOps.isNotEmpty()
        val needsEds = current.endings.isEmpty() && crossEds.isNotEmpty()
        if (!needsTrailer && !needsOps && !needsEds) return

        val updated = current.copy(
            trailerUrl = if (needsTrailer) firstTrailer!!.videoUrl else current.trailerUrl,
            trailerSource = if (needsTrailer) firstTrailer!!.source else current.trailerSource,
            openings = if (needsOps) crossOps else current.openings,
            endings = if (needsEds) crossEds else current.endings
        )
        _detailState.value = updated
        DetailCache.putMediaDetail(entry.source, stableId, updated)
    }
}
