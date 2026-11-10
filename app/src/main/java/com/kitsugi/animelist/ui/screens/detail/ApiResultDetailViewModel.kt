package com.kitsugi.animelist.ui.screens.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClient
import com.kitsugi.animelist.data.remote.KitsugiSimklDetailClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.MdbListClient
import com.kitsugi.animelist.data.remote.MdbListQuotaCenter
import com.kitsugi.animelist.data.remote.MdbListRatings
import com.kitsugi.animelist.data.remote.resolveMdbListMediaIds
import com.kitsugi.animelist.data.remote.KitsugiCharacter
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.data.remote.KitsugiShikimoriClient
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.data.remote.KitsugiReview
import com.kitsugi.animelist.data.remote.KitsugiStaff
import com.kitsugi.animelist.data.remote.KitsugiStats
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import com.kitsugi.animelist.data.remote.KitsugiMediaVideoAggregator
import com.kitsugi.animelist.data.remote.KitsugiTheme
import com.kitsugi.animelist.data.remote.KnownVideoMediaIds
import com.kitsugi.animelist.data.remote.MediaVideoCategory
import com.kitsugi.animelist.data.remote.MediaVideoItem
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.ui.components.HeroArtworkImagePolicy
import com.kitsugi.animelist.data.remote.resolveMediaGalleryIdentity
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** Legacy data-tab indexes consumed by loadTab; the Videos pager page is mapped out by the UI. */
private const val TAB_REVIEWS = 7
private const val TAB_EPISODES = 8

/** Bir sekme verisi bu süreden uzun sürerse "yüklenemedi" durumuna düşer (skeleton sonsuz kalmaz). */
private const val TAB_FETCH_TIMEOUT_MS = 25_000L

/** Bölüm listesi çok bölümlü serilerde (ör. 1000+ bölüm) sayfa sayfa çekilir; daha uzun sınır. */
private const val TAB_EPISODES_FETCH_TIMEOUT_MS = 90_000L

/**
 * Vitrin (hero) görsel planının çözümleme zaman tavanı.
 * Sayfa açılışı bu adım yüzünden asla dakikalarca beklenmez; tavan aşılırsa vitrin
 * kendi yedek zinciriyle (kaynak görseli → galeri) devam eder.
 */
private const val HERO_ARTWORK_RESOLVE_TIMEOUT_MS = 5_000L

class ApiResultDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val apiClient = JikanApiClient()
    private val translationManager = TranslationManager(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val TAG = "ApiResultDetailVM"

    private val tmdbVideoClient = TmdbApiClient()

    // --- StateFlows ---

    private val _detailState = MutableStateFlow<KitsugiMediaDetail?>(null)
    val detailState: StateFlow<KitsugiMediaDetail?> = _detailState.asStateFlow()

    /**
     * Detay alınamadığında kaynağın `KTS-…` hata kodu (yoksa null). Başarılı detayda sıfırlanır;
     * yeniden denemede son hata yüklenme ekranında görünmeye devam eder.
     */
    private val _detailErrorCode = MutableStateFlow<com.kitsugi.animelist.data.platform.PlatformErrorCode?>(null)
    val detailErrorCode: StateFlow<com.kitsugi.animelist.data.platform.PlatformErrorCode?> = _detailErrorCode.asStateFlow()

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

    private val _detailLoading = MutableStateFlow(true)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _translatedSynopsis = MutableStateFlow<String?>(null)
    val translatedSynopsis: StateFlow<String?> = _translatedSynopsis.asStateFlow()

    /**
     * Açıklama çevirisi (otomatik ya da elle) sürerken `true`.
     *
     * Detay sayfası bu değeri `LocalKitsugiTranslationLoading` üzerinden sayfadaki çeviri
     * butonlarına verir; butonlar çeviri bitene kadar galeri butonuyla birebir aynı
     * yüklenme (nabız + dönen halka) animasyonunu gösterir.
     */
    private val _translationLoading = MutableStateFlow(false)
    val translationLoading: StateFlow<Boolean> = _translationLoading.asStateFlow()

    /** Açıklamada orijinal metin gösteriliyor mu (çeviri varken "Orijinal" / "Çeviri" geçişi). */
    private val _showSynopsisOriginal = MutableStateFlow(false)
    val showSynopsisOriginal: StateFlow<Boolean> = _showSynopsisOriginal.asStateFlow()

    private val _logoUrl = MutableStateFlow<String?>(null)
    val logoUrl: StateFlow<String?> = _logoUrl.asStateFlow()

    private val _episodeRatings = MutableStateFlow<Map<Pair<Int, Int>, Double>>(emptyMap())
    val episodeRatings: StateFlow<Map<Pair<Int, Int>, Double>> = _episodeRatings.asStateFlow()

    private val _resolvedTmdbId = MutableStateFlow<Int?>(null)
    val resolvedTmdbId: StateFlow<Int?> = _resolvedTmdbId.asStateFlow()

    // --- MDBList Puanları ---
    private val _mdbListRatings = MutableStateFlow<MdbListRatings?>(null)
    val mdbListRatings: StateFlow<MdbListRatings?> = _mdbListRatings.asStateFlow()

    private val _mdbListLoading = MutableStateFlow(false)
    val mdbListLoading: StateFlow<Boolean> = _mdbListLoading.asStateFlow()

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

    /** Çalışan sekme yüklemeleri (sekme indeksi → iş). Tek uçuş ve iptal için. */
    private val tabJobs = HashMap<Int, Job>()

    /** Sekme işlerinin hangi parametrelerle (kaynak + kimlik + MAL kimliği) çalıştığı. */
    private val tabRequestKeys = HashMap<Int, String>()

    private val _targetSeason = MutableStateFlow<Int>(1)
    val targetSeason: StateFlow<Int> = _targetSeason.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    /**
     * Detay sayfası VİTRİN görsel planı — TMDB (`/original`) + Fanart.tv kalite
     * sıralamasıyla çözülmüş en kaliteli yatay arka plan ve poster adayları.
     *
     * Sayfa render edilmeden **ÖNCE** doldurulur; böylece vitrin ilk kareden itibaren
     * en kaliteli görseli gösterir, önce düşük çözünürlüklü kaynak görselini basıp
     * sonra değiştirmez.
     */
    private val _heroArtwork = MutableStateFlow<KitsugiHeroArtworkResolver.HeroArtwork?>(null)
    val heroArtwork: StateFlow<KitsugiHeroArtworkResolver.HeroArtwork?> = _heroArtwork.asStateFlow()

    /** Galeri tüm kaynaklardan (detay + galeri + yenileme) bitene kadar true. */
    private val galleryTracker = GalleryLoadTracker()
    val galleryLoading: StateFlow<Boolean> = galleryTracker.loading

    private val _videoItems = MutableStateFlow<List<MediaVideoItem>>(emptyList())
    val videoItems: StateFlow<List<MediaVideoItem>> = _videoItems.asStateFlow()

    private val videosTracker = GalleryLoadTracker()
    val videosLoading: StateFlow<Boolean> = videosTracker.loading

    /** Her yeni sonuç navigasyonunda artar — UI bu trigger’ı izleyerek tab’ı 0’a sıfırlar. */
    private val _pageResetTrigger = MutableStateFlow(0)
    val pageResetTrigger: StateFlow<Int> = _pageResetTrigger.asStateFlow()

    // --- Cache / Lock Key ---
    private var currentFetchKey: String? = null
    private var lastResult: JikanSearchResult? = null
    private var synopsisTranslateJob: Job? = null
    private var mdbListFetchJob: Job? = null
    private var mdbListFetchGeneration = 0L
    private var observedMdbListConfig: Pair<Boolean, String>? = null

    /** Çeviri işlerinin nesil sayacı — eski işlerin yüklenme durumunu kapatmasını engeller. */
    @Volatile private var translationGeneration = 0
    @Volatile private var charactersFetchedWithDetail: Boolean = false
    private var lastAutoTranslateSetting: Boolean? = null
    private var lastTargetLangSetting: String? = null

    init {
        viewModelScope.launch {
            DetailCache.characterPortraitVersion.collect {
                val res = lastResult ?: return@collect
                val cached = DetailCache.getMediaCharacters(res.source, res.malId)
                val current = (_charactersState.value as? DetailTabState.Success)?.data
                val base = current ?: cached ?: return@collect
                var changed = false
                val updated = base.map { ch ->
                    val isActorDup = !ch.imageUrl.isNullOrBlank() &&
                        ch.voiceActors.any { va -> DetailCache.isSameImageResource(ch.imageUrl, va.imageUrl) }
                    if (!ch.imageUrl.isNullOrBlank() && !isActorDup) {
                        ch
                    } else {
                        val portrait = DetailCache.getCachedCharacterPortrait(ch.name)
                            ?.takeUnless { p -> ch.voiceActors.any { va -> DetailCache.isSameImageResource(p, va.imageUrl) } }
                        if (!portrait.isNullOrBlank() && portrait != ch.imageUrl) {
                            changed = true
                            ch.copy(imageUrl = portrait, isRealMediaRole = false)
                        } else if (!ch.isRealMediaRole && isActorDup) {
                            changed = true
                            ch.copy(imageUrl = null)
                        } else ch
                    }
                }
                if (changed) {
                    DetailCache.putMediaCharacters(res.source, res.malId, updated)
                    if (current != null) {
                        _charactersState.value = DetailTabState.Success(updated)
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val newMdbListConfig = settings.mdbListEnabled to settings.mdbListApiKey.trim()
                val previousMdbListConfig = observedMdbListConfig
                observedMdbListConfig = newMdbListConfig
                if (previousMdbListConfig != null && previousMdbListConfig != newMdbListConfig) {
                    lastResult?.let(::refreshMdbListRatings)
                }

                val prevAuto = lastAutoTranslateSetting
                val prevTarget = lastTargetLangSetting
                lastAutoTranslateSetting = settings.autoTranslateEnabled
                lastTargetLangSetting = settings.translateTargetLanguage
                if (prevAuto == null && prevTarget == null) return@collect
                if (prevAuto != settings.autoTranslateEnabled || prevTarget != settings.translateTargetLanguage) {
                    val res = lastResult ?: return@collect
                    val raw = _detailState.value?.synopsis ?: return@collect
                    val isRussian = raw.any { it in '\u0400'..'\u04FF' }
                    if (settings.autoTranslateEnabled || isRussian) {
                        triggerSynopsisTranslationIfNeeded(
                            result = res,
                            rawSynopsis = raw,
                            autoTranslate = settings.autoTranslateEnabled,
                            expectedFetchKey = currentFetchKey,
                            forceRetranslate = prevTarget != settings.translateTargetLanguage
                        )
                    } else {
                        cancelSynopsisTranslation()
                        _translatedSynopsis.value = null
                    }
                }
            }
        }
    }

    private fun triggerSynopsisTranslationIfNeeded(
        result: JikanSearchResult,
        rawSynopsis: String?,
        autoTranslate: Boolean,
        expectedFetchKey: String?,
        forceRetranslate: Boolean = false
    ) {
        if (rawSynopsis.isNullOrBlank()) return
        val isRussian = rawSynopsis.any { it in '\u0400'..'\u04FF' }
        if (!autoTranslate && !isRussian) return

        if (!forceRetranslate) {
            val cachedTr = DetailCache.getTranslation(
                "synopsis", result.source, result.malId, sourceText = rawSynopsis
            )
            // Önbellekteki çeviri kaynak metinle eşdeğerse (Türkçe metnin "çevirisi")
            // çeviri sayılmaz; orijinal gösterilir.
            if (!cachedTr.isNullOrBlank() &&
                !com.kitsugi.animelist.utils.translationMatchesSource(rawSynopsis, cachedTr)
            ) {
                _translatedSynopsis.value = cachedTr
                return
            }
        }

        startSynopsisTranslation(
            result = result,
            rawSynopsis = rawSynopsis,
            expectedFetchKey = expectedFetchKey
        )
    }

    /**
     * Açıklama çevirisini başlatır ve çeviri boyunca [translationLoading]'i açık tutar.
     *
     * Kısmi (stream) sonuçlar geldiğinde de yüklenme durumu kapanmaz; buton ancak çeviri
     * tamamen bittiğinde (ya da iptal edildiğinde) normal hâline döner.
     */
    private fun startSynopsisTranslation(
        result: JikanSearchResult,
        rawSynopsis: String,
        expectedFetchKey: String?
    ) {
        val generation = ++translationGeneration
        synopsisTranslateJob?.cancel()
        synopsisTranslateJob = viewModelScope.launch {
            try {
                _translationLoading.value = true
                val tr = withContext(Dispatchers.IO) {
                    translationManager.translateToTarget(rawSynopsis) { partial ->
                        if (currentFetchKey == expectedFetchKey &&
                            partial.isNotBlank() &&
                            !com.kitsugi.animelist.utils.translationMatchesSource(rawSynopsis, partial)
                        ) {
                            _translatedSynopsis.value = partial
                        }
                    }
                }
                if (currentFetchKey == expectedFetchKey && !tr.isNullOrBlank()) {
                    if (com.kitsugi.animelist.utils.translationMatchesSource(rawSynopsis, tr)) {
                        // Kaynak metin zaten hedef dilde: çeviri yok, düğme de yok.
                        DetailCache.removeTranslation("synopsis", result.source, result.malId)
                        _translatedSynopsis.value = null
                        return@launch
                    }
                    DetailCache.putTranslation(
                        "synopsis", result.source, result.malId, tr, sourceText = rawSynopsis
                    )
                    _translatedSynopsis.value = tr
                }
            } finally {
                // Daha yeni bir çeviri işi başladıysa yüklenme durumunu ona bırak.
                if (generation == translationGeneration) {
                    _translationLoading.value = false
                }
            }
        }
    }

    /** Çalışan açıklama çevirisini bırakır ve yüklenme animasyonunu kapatır. */
    private fun cancelSynopsisTranslation() {
        translationGeneration++
        synopsisTranslateJob?.cancel()
        synopsisTranslateJob = null
        _translationLoading.value = false
            // Yeni kayıt / otomatik çeviri kapandı: eski orijinal-geçiş durumu geçerli değil.
        _showSynopsisOriginal.value = false
    }

    /** Orijinal / çeviri geçişi (açıklama). Çeviri bellekte kalır, yeniden çevrilmez. */
    fun toggleSynopsisOriginal() {
        _showSynopsisOriginal.value = !_showSynopsisOriginal.value
    }

    fun translateSynopsis() {
        val res = lastResult ?: return
        val raw = _detailState.value?.synopsis ?: return
        triggerSynopsisTranslationIfNeeded(
            result = res,
            rawSynopsis = raw,
            autoTranslate = true,
            expectedFetchKey = currentFetchKey,
            forceRetranslate = true
        )
    }

    fun loadResult(result: JikanSearchResult, showAnimeLogos: Boolean, forceRefresh: Boolean = false) {
        if (forceRefresh) {
            currentFetchKey = null
            DetailCache.removeMediaDetail(result.source, result.malId)
            DetailCache.removeTranslation("synopsis", result.source, result.malId)
            DetailCache.removeMediaCharacters(result.source, result.malId)
            DetailCache.removeMediaStaff(result.source, result.malId)
            DetailCache.removeMediaRelations(result.source, result.malId)
            DetailCache.removeMediaRecommendations(result.source, result.malId)
            DetailCache.removeMediaReviews(result.source, result.malId, result.type.name)
            DetailCache.removeMediaEpisodes(result.source, result.malId)
            DetailCache.removeMediaVideos(result.source, result.malId)
            DetailCache.clearFanartCache() // Fanart galeri önbelleğini temizle
        }

        val newKey = "${result.source}:${result.malId}:${result.type.name}"
        if (newKey == currentFetchKey) {
            Log.d(TAG, "loadResult: Cache hit for key=$newKey — skipping")
            return
        }

        Log.d(TAG, "loadResult: New key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        cancelSynopsisTranslation()
        cancelTabLoads() // eski sonucun sekme işleri yeni state'e yazmasın
        lastResult = result
        _pageResetTrigger.value += 1 // Signal UI to scroll back to first tab

        val cachedDetail = DetailCache.getMediaDetail(result.source, result.malId, result.type.name)
        val cachedDetailSynopsis = cachedDetail?.synopsis
        val cachedSynopsisTranslation = cachedDetailSynopsis?.let { raw ->
            DetailCache.getTranslation("synopsis", result.source, result.malId, sourceText = raw)
        }
        val cachedHeroArtwork = KitsugiHeroArtworkResolver.getCached(result)
        val cachedVideos = DetailCache.getMediaVideos(result.source, result.malId, _targetSeason.value)
        _videoItems.value = cachedVideos ?: KitsugiMediaVideoAggregator.seedFromDetail(cachedDetail, result.source)
        videosTracker.reset()

        _detailState.value = cachedDetail
        _detailLoading.value = cachedDetail == null
        // Çeviri yalnızca **aynı** özet metnine aitse gösterilir.
        _translatedSynopsis.value = cachedSynopsisTranslation?.takeIf { tr ->
            !cachedDetailSynopsis.isNullOrBlank() &&
                !com.kitsugi.animelist.utils.translationMatchesSource(cachedDetailSynopsis, tr)
        }
        _logoUrl.value = null
        _episodeRatings.value = emptyMap()
        _resolvedTmdbId.value = null
        _mdbListRatings.value = null
        _mdbListLoading.value = false
        refreshMdbListRatings(result)
        _galleryItems.value = emptyList()
        _heroArtwork.value = cachedHeroArtwork
        heroArtworkKey = cachedHeroArtwork?.let { KitsugiHeroArtworkResolver.cacheKey(result) }
        galleryTracker.reset()

        val cachedCharacters = DetailCache.getMediaCharacters(result.source, result.malId)
        charactersFetchedWithDetail = cachedCharacters != null && cachedDetail != null
        if (cachedCharacters != null && cachedCharacters.isEmpty()) {
            DetailCache.removeMediaCharacters(result.source, result.malId)
            _charactersState.value = DetailTabState.Loading
        } else {
            _charactersState.value = if (cachedCharacters != null) DetailTabState.Success(cachedCharacters) else DetailTabState.Loading
        }

        val cachedStaff = DetailCache.getMediaStaff(result.source, result.malId)
        if (cachedStaff != null && cachedStaff.isEmpty()) {
            DetailCache.removeMediaStaff(result.source, result.malId)
            _staffState.value = DetailTabState.Loading
        } else {
            _staffState.value = if (cachedStaff != null) DetailTabState.Success(cachedStaff) else DetailTabState.Loading
        }

        val cachedRelations = DetailCache.getMediaRelations(result.source, result.malId)
        if (cachedRelations != null && cachedRelations.isEmpty()) {
            DetailCache.removeMediaRelations(result.source, result.malId)
            _relationsState.value = DetailTabState.Loading
        } else {
            _relationsState.value = if (cachedRelations != null) DetailTabState.Success(cachedRelations) else DetailTabState.Loading
        }

        val cachedRecommendations = DetailCache.getMediaRecommendations(result.source, result.malId)
        if (cachedRecommendations != null && cachedRecommendations.isEmpty()) {
            DetailCache.removeMediaRecommendations(result.source, result.malId)
            _recommendationsState.value = DetailTabState.Loading
        } else {
            _recommendationsState.value = if (cachedRecommendations != null) DetailTabState.Success(cachedRecommendations) else DetailTabState.Loading
        }

        val cachedStats = DetailCache.getMediaStats(result.source, result.malId)
        _statsState.value = if (DetailCache.hasMediaStats(result.source, result.malId)) DetailTabState.Success(cachedStats) else DetailTabState.Loading

        val cachedReviews = DetailCache.getMediaReviews(result.source, result.malId, result.type.name)
        _reviewsState.value = if (cachedReviews != null) DetailTabState.Success(cachedReviews) else DetailTabState.Loading

        val cachedEpisodes = DetailCache.getMediaEpisodes(result.source, result.malId)
        // Boş liste cache'deyse yeniden yükleme yapılabilmesi için sil
        if (cachedEpisodes != null && cachedEpisodes.isEmpty()) {
            DetailCache.removeMediaEpisodes(result.source, result.malId)
            _episodesState.value = DetailTabState.Loading
        } else {
            _episodesState.value = if (cachedEpisodes != null) DetailTabState.Success(cachedEpisodes) else DetailTabState.Loading
        }

        // Detail fetch — sayfa render için kritik; öncelikli coroutine
        val detailJob = viewModelScope.launch {
            try {
                fetchDetail(result)
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
                fetchFanartGallery(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Fanart gallery: ${e.message}", e)
            } finally {
                galleryTracker.end(galleryToken)
            }
        }

        // Ortak videolar sekmesi: tüm video sağlayan kaynakları paralel sorgular
        val videosToken = videosTracker.begin()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fetchAggregatedVideos(result, newKey)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error fetching aggregated videos: ${e.message}", e)
            } finally {
                videosTracker.end(videosToken)
            }
        }


        // Logo — bağımsız, en düşük öncelikli
        viewModelScope.launch {
            try {
                fetchLogo(result, showAnimeLogos)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching logo: ${e.message}", e)
            }
        }
    }

    fun setTargetSeason(season: Int, result: JikanSearchResult, realMalId: Int?) {
        _targetSeason.value = season
        _episodesState.value = DetailTabState.Loading
        DetailCache.removeMediaEpisodes(result.source, result.malId)
        // Eski kod burada loadTab(7) (Yorumlar) çağırıyordu: bölümler hiç yeniden yüklenmiyor,
        // Bölümler sekmesi sonsuza dek spinner'da kalıyordu. Doğrusu bölüm sekmesi (8).
        loadTab(TAB_EPISODES, result, realMalId, force = true)
        viewModelScope.launch {
            val showLogos = runCatching { settingsDataStore.settingsFlow.first().showAnimeLogos }.getOrDefault(true)
            if (showLogos) {
                runCatching { fetchLogo(result, true) }
            }
            // The detail gallery has its own season-aware cache and includes TMDB season logos.
            if (_galleryItems.value.isNotEmpty()) {
                runCatching { fetchFanartGallery(result) }
            }
            val seasonVideoToken = videosTracker.begin()
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    fetchAggregatedVideos(result, currentFetchKey)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                } finally {
                    videosTracker.end(seasonVideoToken)
                }
            }
        }
    }

    /** TMDB/TR zenginleştirmesinin zaman tavanı — sayfa asıl detayla açıkken bu adım
     *  asla kullanıcıyı dakika dakika bekletmesin. */
     private val enrichTimeoutMillis: Long get() = 20_000L

    /** Vitrin görsel planının son çözümlendiği kimlik anahtarı (tek uçuş için). */
    private var heroArtworkKey: String? = null

    /**
     * Vitrin (hero) görsel planını çözer ve en kaliteli adayı önceden yükler.
     *
     * Sayfa render edilmeden **önce** çağrılır:
     *  1. Detaydan gelen kimlikler (TMDB/MAL/başlıklar) birincil sonuçla birleştirilir —
     *     çözümleyici doğru TMDB/Fanart.tv eşleşmesini bulabilsin diye.
     *  2. [KitsugiHeroArtworkResolver] TMDB + Fanart.tv adaylarını çözünürlük,
     *     16:9 oran, yazısız temizlik ve topluluk oylarına göre sıralar.
     *  3. En kaliteli aday [HeroArtworkImagePolicy] ile **tam 8-bit renkte** ve sınırlı
     *     ölçüde decode edilir → sayfa açıldığında bitmap hazırdır.
     *
     * Herhangi bir adım başarısız olursa sessizce çıkılır; vitrin kendi yedek
     * zinciriyle (kaynak görseli → galeri) devam eder.
     */
    private suspend fun resolveHeroArtwork(
        result: JikanSearchResult,
        detail: KitsugiMediaDetail?
    ) {
        val heroItem = if (detail != null) {
            result.copy(
                tmdbId = detail.tmdbId ?: result.tmdbId,
                realMalId = detail.realMalId ?: result.realMalId,
                imageUrl = if (!detail.imageUrl.isNullOrBlank()) detail.imageUrl else result.imageUrl,
                backdropUrl = detail.bannerImage ?: result.backdropUrl,
                year = detail.year ?: result.year,
                titleEnglish = detail.titleEnglish ?: result.titleEnglish,
                titleRomaji = detail.titleRomaji ?: result.titleRomaji,
                titleJapanese = detail.titleJapanese ?: result.titleJapanese,
                type = detail.type ?: result.type
            )
        } else {
            result
        }

        val key = KitsugiHeroArtworkResolver.cacheKey(heroItem)
        if (key == heroArtworkKey && _heroArtwork.value != null) return

        // Not: CancellationException yeniden firlatilir — yoksa kapsam iptali yutulur.
        val artwork = try {
            withTimeoutOrNull(HERO_ARTWORK_RESOLVE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    KitsugiHeroArtworkResolver.resolveArtwork(heroItem)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "resolveHeroArtwork failed for ${result.title}: ${e.message}")
            null
        } ?: return

        heroArtworkKey = key
        _heroArtwork.value = artwork

        // En kaliteli yatay adayi sayfa acilmadan decode et (ilk kare aninda gelsin).
        // Manga gibi yatay görseli olmayan kayitlarda poster adayi kullanilir.
        val preloadUrls = artwork.backdropCandidates.ifEmpty { artwork.posterCandidates }
        try {
            HeroArtworkImagePolicy.preload(context = context, urls = preloadUrls)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Hero artwork preload failed for ${result.title}: ${e.message}")
        }
    }

    private suspend fun fetchDetail(result: JikanSearchResult) {
        // TMDB devre dışıysa TMDB zenginleştirmesini atla
        val settings = runCatching { settingsDataStore.settingsFlow.first() }.getOrNull()
        val tmdbEnabled = settings?.tmdbEnabled ?: true

        val cached = DetailCache.getMediaDetail(result.source, result.malId, result.type.name)
        // ── Aşama 1: BİRİNCİL DETAY (kaynak + fallback + Room önbelleği) ─────────────
        // Sayfa bu veriyle HEMEN açılır; TMDB/TR zenginleştirmesi için beklenmez.
        val detail = if (cached != null) {
            cached
        } else {
            _detailLoading.value = true
            val fetched = try {
                withContext(Dispatchers.IO) {
                    apiClient.fetchPrimaryDetail(
                        source = result.source,
                        externalId = result.malId,
                        mediaType = result.type,
                        // TMDB devre dışıysa tmdbId gönderme
                        tmdbId = if (tmdbEnabled) result.tmdbId else null,
                        realMalId = result.realMalId,
                        title = result.title
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during apiClient.fetchPrimaryDetail: ${e.message}", e)
                com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report(result.source, error = e)
                null
            }
            if (fetched != null) {
                DetailCache.putMediaDetail(result.source, result.malId, fetched, result.type.name)
            }
            fetched
        }

        _detailState.value = detail
        _detailErrorCode.value = if (detail == null) {
            com.kitsugi.animelist.data.platform.PlatformFailureRegistry.latest(result.source)?.code
                ?: com.kitsugi.animelist.data.platform.PlatformErrorCode.COM_1020
        } else {
            null
        }

        val expectedFetchKey = currentFetchKey
        val autoTranslate = settings?.autoTranslateEnabled ?: false

        // Aşama 1'de özet geldiyse vitrin görselini ve Aşama 2 zenginleştirmesini beklemeden
        // çeviriyi hemen arka planda başlat!
        if (!detail?.synopsis.isNullOrBlank()) {
            triggerSynopsisTranslationIfNeeded(
                result = result,
                rawSynopsis = detail?.synopsis,
                autoTranslate = autoTranslate,
                expectedFetchKey = expectedFetchKey
            )
        }

        // Birincil detay hazır olduğu anda sayfayı aç. TMDB/Fanart seçimi ve görsel
        // preload'u loading ekranında bekletmez; mevcut kaynak görseliyle başlayan vitrin,
        // yüksek kaliteli aday hazır olunca güncellenir.
        _detailLoading.value = false

        if (detail != null) {
            viewModelScope.launch {
                resolveHeroArtwork(result, detail)
            }
            // ── Aşama 2: TMDB/TR ZENGİNLEŞTİRME — zaman tavanlı, arka planda ─────────
            // Bangumi'de bu adım aynı zamanda çapraz kimlik çözümü (AniList → MAL → ARM) ve
            // MAL/AniList/TMDB birleştirmesidir; TMDB kapalı olsa bile çalışmalıdır (TMDB'ye
            // bağlı kısımlar istemcide zaten ayarı kontrol eder).
            val isBangumiResult = result.source.equals("bangumi", ignoreCase = true)
            val enriched = try {
                withTimeoutOrNull(enrichTimeoutMillis) {
                    withContext(Dispatchers.IO) {
                        apiClient.enrichDetail(
                            source = result.source,
                            externalId = result.malId,
                            mediaType = result.type,
                            detail = detail,
                            tmdbId = if (tmdbEnabled) result.tmdbId else null,
                            realMalId = result.realMalId,
                            title = result.title
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Enrichment failed, keeping primary detail: ${e.message}")
                null
            }

            val finalDetail = enriched ?: detail
            if (enriched != null && enriched != detail) {
                DetailCache.putMediaDetail(result.source, result.malId, enriched, result.type.name)
                _detailState.value = enriched
                viewModelScope.launch {
                    resolveHeroArtwork(result, enriched)
                }
            }

            val determinedSeason = KitsugiEpisodeRatingsRepository.determineTargetSeason(
                tmdbSeason = finalDetail.tmdbSeason,
                title = result.title,
                titleEnglish = finalDetail.titleEnglish,
                synonyms = finalDetail.synonyms.orEmpty()
            )
            _targetSeason.value = determinedSeason

            // Detaydan gelen TMDB ID veya resimler varsa ve galeri henüz kısıtlıysa galeriyi zenginleştir.
            // Bangumi'de galeri ilk açılışta yalnızca kapakla dolar; çapraz kimlikler (TMDB/MAL/AniList)
            // detayla birlikte çözüldüğü için galeri HER ZAMAN bir kez yenilenir.
            val movieForGallery = KitsugiEpisodeRatingsRepository.prefersMovieLogo(
                type = finalDetail.type ?: result.type,
                format = finalDetail.format,
                rawFormat = finalDetail.rawFormat,
                subtitle = result.subtitle,
                title = listOfNotNull(finalDetail.title, result.title, result.titleEnglish).joinToString(" | ")
            )
            // Film kimliği detayla birlikte netleşir; ilk galeri dizi görselleriyle dolmuş olabilir.
            if (_galleryItems.value.size <= 2 || isBangumiResult || movieForGallery) {
                val refreshToken = galleryTracker.begin()
                viewModelScope.launch {
                    try {
                        fetchFanartGallery(result.copy(tmdbId = finalDetail.tmdbId ?: result.tmdbId))
                    } catch (e: Exception) {
                        Log.e(TAG, "Post-detail gallery refresh failed: ${e.message}")
                    } finally {
                        galleryTracker.end(refreshToken)
                    }
                }
            }

            // Aşama 2 yeni bir özet getirdiyse veya henüz çeviri başlamadıysa çeviriyi tetikle
            val rawSynopsis = finalDetail.synopsis
            if (!rawSynopsis.isNullOrBlank() && (rawSynopsis != detail.synopsis || _translatedSynopsis.value == null)) {
                triggerSynopsisTranslationIfNeeded(
                    result = result,
                    rawSynopsis = rawSynopsis,
                    autoTranslate = autoTranslate,
                    expectedFetchKey = expectedFetchKey,
                    forceRetranslate = rawSynopsis != detail.synopsis
                )
            }

            // Fetch episode ratings
            fetchEpisodeRatings(result, finalDetail)

            // ── Fragman yedek zinciri ─────────────────────────────────────────
            // Kaynağın kendisi YouTube fragmanı vermediyse (trailerUrl boş), arka planda
            // sırayla TMDB → diğer metadata kaynakları → Cloudstream eklentileri denenir.
            // Bulunan fragman detay durumuna ve önbelleğe yazılır; kart kendiliğinden belirir.
            if (finalDetail.trailerUrl.isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        val trailer = com.kitsugi.animelist.data.trailer.DetailTrailerFallback.resolve(
                            context = getApplication(),
                            title = finalDetail.title ?: result.title,
                            year = finalDetail.year ?: result.year,
                            type = result.type,
                            tmdbId = finalDetail.tmdbId ?: result.tmdbId ?: _resolvedTmdbId.value,
                            malId = finalDetail.realMalId ?: result.realMalId ?: result.malId,
                            sourceName = result.source
                        )
                        if (!trailer.isNullOrBlank()) {
                            val current = _detailState.value
                            if (current != null && current.trailerUrl.isNullOrBlank()) {
                                val updated = current.copy(trailerUrl = trailer)
                                _detailState.value = updated
                                DetailCache.putMediaDetail(result.source, result.malId, updated)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fallback trailer resolution failed: ${e.message}")
                    }
                }
            }

            // Detay geldikten sonra logo yeniden seçilir: film/sezon/dil ancak o zaman kesinleşir.
            // İlk çekim dizi logosunu koymuş olsa bile doğru logo onun yerine geçer.
            val showLogos = settings?.showAnimeLogos ?: true
            if (showLogos) {
                viewModelScope.launch {
                    try {
                        fetchLogo(result.copy(realMalId = finalDetail.realMalId ?: result.realMalId, tmdbId = finalDetail.tmdbId ?: result.tmdbId), showLogos)
                    } catch (e: Exception) {
                        Log.e(TAG, "Post-detail logo fetch failed: ${e.message}")
                    }
                }
            }

            // Detail yüklendi → karakter sekmesi detay gelmeden önce çekildiyse ve eksik kurgusal
            // portre varsa, artık tür/başlık/realMalId bilindiğinden karakter görsellerini tamamla.
            val currentChars = (_charactersState.value as? DetailTabState.Success)?.data
            if (!charactersFetchedWithDetail && currentChars != null &&
                currentChars.any { it.imageUrl.isNullOrBlank() || it.isRealMediaRole }
            ) {
                DetailCache.removeMediaCharacters(result.source, result.malId)
                loadTab(2, result, result.realMalId ?: finalDetail.realMalId, force = true)
            }

            // Detail yüklendi → eğer TvShow/Anime ise ve episode state'i boş/loading ise,
            // artık tmdbId biliniyor — episode'ları otomatik olarak yeniden çek.
            val hasTvEps = result.type == com.kitsugi.animelist.model.MediaType.Anime ||
                result.type == com.kitsugi.animelist.model.MediaType.TvShow
            if (hasTvEps) {
                val epState = _episodesState.value
                val needsEpLoad = epState !is DetailTabState.Success ||
                    (epState is DetailTabState.Success && epState.data.isEmpty())
                if (needsEpLoad) {
                    android.util.Log.d(TAG, "fetchDetail: detail loaded, auto-triggering episode load for ${result.source}/${result.malId}")
                    loadTab(TAB_EPISODES, result, result.realMalId ?: finalDetail.realMalId)
                }
            }
        }
    }

    private suspend fun fetchEpisodeRatings(result: JikanSearchResult, detail: KitsugiMediaDetail) {
        val settings = runCatching { settingsDataStore.settingsFlow.first() }.getOrNull()
        val tmdbEnabled = settings?.tmdbEnabled ?: true
        if (!tmdbEnabled) {
            _episodeRatings.value = emptyMap()
            _resolvedTmdbId.value = null
            return
        }

        val tmdbId = detail.tmdbId
        var foundRatings = emptyMap<Pair<Int, Int>, Double>()
        var resolvedId: Int? = null

        withContext(Dispatchers.IO) {
            when {
                tmdbId != null && tmdbId > 0 -> {
                    resolvedId = tmdbId
                    foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatings(tmdbId)
                }
                result.source.equals("anilist", ignoreCase = true) -> {
                    val stableId = result.malId
                    if (stableId >= 100_000_000) {
                        // Saf AniList ID (offset'li): 100M’ı çıkarıp AniList ID'yi al
                        val aniListId = stableId - 100_000_000
                        if (aniListId > 0) {
                            foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByAniListId(aniListId)
                            resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForAniList(aniListId)
                        }
                    } else {
                        // stableId < 100M → MAL ID ile döndü
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(stableId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(stableId)
                    }

                    if (foundRatings.isEmpty()) {
                        val malId = detail.realMalId
                            ?: if (stableId < 100_000_000) stableId else null
                        if (malId != null && malId > 0) {
                            foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(malId)
                            resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(malId)
                        }
                    }
                }
                result.source.equals("kitsu", ignoreCase = true) -> {
                    val stableId = result.malId
                    val kitsuId = stableId - 300_000_000
                    if (kitsuId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByKitsuId(kitsuId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForKitsu(kitsuId)
                    }
                }
                result.source.equals("bangumi", ignoreCase = true) -> {
                    // Bangumi stableId'si (500M+) MAL ID DEĞİLDİR: bölüm puanları çözülen
                    // çapraz kimliklerle (TMDB → MAL → AniList) alınır.
                    val cross = runCatching {
                        KitsugiBangumiDetailClient.resolveCrossIds(result.malId, result.type)
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
                result.source.equals("shikimori", ignoreCase = true) -> {
                    // Shikimori ID'si MAL ID'si DEĞİLDİR: bölüm puanları gerçek MAL ID'si
                    // üzerinden alınır (aksi hâlde alakasız yapımın puanları gösteriliyordu).
                    val malId = detail.realMalId?.takeIf { it > 0 }
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(result.malId)
                    if (malId != null && malId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(malId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(malId)
                    }
                }
                else -> {
                    val malId = result.malId
                    if (malId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(malId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(malId)
                    }
                }
            }
        }

        _episodeRatings.value = foundRatings
        _resolvedTmdbId.value = resolvedId
    }

    private fun logoSelection(result: JikanSearchResult): Pair<Boolean, Int?> {
        val detail = _detailState.value
        val isMovie = KitsugiEpisodeRatingsRepository.prefersMovieLogo(
            type = detail?.type ?: result.type,
            format = detail?.format,
            rawFormat = detail?.rawFormat,
            subtitle = result.subtitle,
            title = listOfNotNull(detail?.title, detail?.titleEnglish, result.title, result.titleEnglish).joinToString(" | ")
        )
        val season = (_targetSeason.value.takeIf { it > 0 }
            ?: KitsugiEpisodeRatingsRepository.determineTargetSeason(
                tmdbSeason = detail?.tmdbSeason,
                title = detail?.title ?: result.title,
                titleEnglish = detail?.titleEnglish ?: result.titleEnglish,
                synonyms = detail?.synonyms.orEmpty()
            )).takeIf { it > 0 }
        return isMovie to season
    }

    private suspend fun fetchLogo(result: JikanSearchResult, showAnimeLogos: Boolean) {
        val isManga = result.type == MediaType.Manga || _detailState.value?.type == MediaType.Manga
        if (!showAnimeLogos || isManga) {
            _logoUrl.value = null
            return
        }
        val stableId = result.malId
        val (isMovie, season) = logoSelection(result)
        val logoTitles = listOfNotNull(
            result.title, result.titleEnglish, result.titleJapanese, result.titleRomaji,
            _detailState.value?.title, _detailState.value?.titleEnglish,
            _detailState.value?.titleRomaji, _detailState.value?.titleJapanese
        ).filter { it.isNotBlank() }.distinct()
        val explicitTmdb = (result.tmdbId ?: _detailState.value?.tmdbId)?.takeIf { it > 0 }
        val logo = withContext(Dispatchers.IO) {
            val explicitLogo = explicitTmdb?.let {
                KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
            }?.takeIf { it.isNotBlank() }
            if (explicitLogo != null) {
                explicitLogo
            } else when {
                result.source.equals("tmdb", ignoreCase = true) -> {
                    val tmdbId = result.tmdbId ?: if (stableId > 0) stableId else null
                    if (tmdbId != null && tmdbId > 0) {
                        KitsugiEpisodeRatingsRepository.getLogoUrl(tmdbId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                    } else null
                }
                result.source.equals("anilist", ignoreCase = true) -> {
                    if (stableId >= 100_000_000) {
                        val aniListId = stableId - 100_000_000
                        KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(
                            aniListId, fallbackMalId = result.realMalId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles
                        )
                    } else {
                        KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                    }
                }
                result.source.equals("kitsu", ignoreCase = true) -> {
                    val kitsuId = if (stableId >= 300_000_000) stableId - 300_000_000 else stableId
                    KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(kitsuId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                }
                result.source.equals("simkl", ignoreCase = true) -> {
                    val cross = com.kitsugi.animelist.data.remote.KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = stableId,
                        mediaType = result.type,
                        hintTmdbId = result.tmdbId ?: _detailState.value?.tmdbId,
                        hintMalId = result.realMalId ?: _detailState.value?.realMalId
                    )
                    val simklTitles = (logoTitles + listOfNotNull(cross.title)).distinct()
                    val effectiveIsMovie = isMovie || cross.isMovie
                    val realMal = (result.realMalId ?: _detailState.value?.realMalId ?: cross.malId)
                        ?.takeIf { it > 0 && it != stableId }
                    val tmdb = (result.tmdbId ?: _detailState.value?.tmdbId ?: cross.tmdbId)
                        ?.takeIf { it > 0 }
                    tmdb?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                        ?.takeIf { it.isNotBlank() }
                        ?: realMal?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, fallbackAniListId = cross.aniListId, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                            ?.takeIf { it.isNotBlank() }
                        ?: cross.aniListId?.takeIf { it > 0 }?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(it, fallbackMalId = realMal, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                            ?.takeIf { it.isNotBlank() }
                        ?: cross.kitsuId?.takeIf { it > 0 }?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(it, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                            ?.takeIf { it.isNotBlank() }
                }
                result.source.equals("jikan", ignoreCase = true) ||
                result.source.equals("mal", ignoreCase = true) -> {
                    if (stableId > 0) KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) else null
                }
                result.source.equals("bangumi", ignoreCase = true) -> {
                    // NOT: stableId burada Bangumi kimliğidir (500M+) — MAL ID'si olarak KULLANILMAZ.
                    val cross = runCatching {
                        KitsugiBangumiDetailClient.resolveCrossIds(stableId, result.type)
                    }.getOrNull()
                    val malId = result.realMalId?.takeIf { it in 1..99_999_999 }
                        ?: _detailState.value?.realMalId?.takeIf { it in 1..99_999_999 }
                        ?: cross?.malId
                    val aniListId = cross?.aniListId
                    val tmdb = result.tmdbId ?: _detailState.value?.tmdbId ?: cross?.tmdbId
                    when {
                        malId != null && malId > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(malId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                        aniListId != null && aniListId > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(aniListId, fallbackMalId = null, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                        tmdb != null && tmdb > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrl(tmdb, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                        else -> null
                    }
                }
                result.source.equals("shikimori", ignoreCase = true) -> {
                    // NOT: stableId burada Shikimori ID'sidir — MAL ID'si olarak KULLANILMAZ.
                    val malId = result.realMalId ?: _detailState.value?.realMalId
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(stableId)
                    if (malId != null && malId > 0) KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(malId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) else null
                }
                stableId > 0 && !result.source.equals("simkl", ignoreCase = true) -> {
                    KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
                }
                else -> null
            }
        }
        _logoUrl.value = logo
    }

    /**
     * Sekme verisini yükler.
     *
     * - Aynı sekme zaten yükleniyorsa yeni istek başlatılmaz (LaunchedEffect birden çok kez
     *   tetiklense bile tek uçuş). Eskiden her tetiklemede yeni bir istek kuyruğa giriyordu.
     * - Her veri çağrısı [TAB_FETCH_TIMEOUT_MS] ile sınırlıdır; süre dolarsa sekme Error'a düşer.
     * - [force] yalnızca kasıtlı yeniden yüklemede (ör. sezon değişimi) kullanılır.
     */
    fun loadTab(tabIndex: Int, result: JikanSearchResult, realMalId: Int?, force: Boolean = false) {
        // Tek uçuş anahtarı: aynı parametrelerle çalışan istek varsa yenisini başlatma.
        // Ancak parametreler iyileştiğinde (ör. detaydan gerçek MAL ID'si geldiğinde)
        // eski isteği iptal edip yeniden denenir — aksi hâlde sekme, kimliksiz yapılan
        // ilk denemenin boş sonucuyla ("bulunamadı" ekranı) kilitli kalıyordu.
        val requestKey = "${result.source}|${result.malId}|${realMalId ?: 0}"
        val running = tabJobs[tabIndex]
        if (!force && running != null && running.isActive && tabRequestKeys[tabIndex] == requestKey) return
        running?.cancel()
        tabRequestKeys[tabIndex] = requestKey

        val malId = result.malId
        val effectiveRealMalId = realMalId
            ?: _detailState.value?.realMalId
            ?: result.realMalId
        val isManga = result.type == MediaType.Manga || _detailState.value?.type == MediaType.Manga
        // TMDB kaynaklı içerikte malId zaten tmdbId'dir; manga içeriklerinde TMDB ID asla kullanılmaz
        val tmdbId = if (isManga) null else (_detailState.value?.tmdbId
            ?: result.tmdbId
            ?: if (result.source.equals("tmdb", ignoreCase = true)) result.malId else null)
        tabJobs[tabIndex] = viewModelScope.launch {
            val settings = runCatching { settingsDataStore.settingsFlow.first() }.getOrNull()
            val tmdbEnabled    = settings?.tmdbEnabled    ?: true
            val useCredits     = settings?.tmdbUseCredits  ?: true
            val useEpisodes    = settings?.tmdbUseEpisodes ?: true
            val useMoreLikeThis = settings?.tmdbUseMoreLikeThis ?: true
            val useCollections = settings?.tmdbUseCollections ?: true
            try {
                when (tabIndex) {
                    1 -> {
                        // Resimler (Gallery) sekmesi — görseller loadGallery ile yüklenir
                    }
                    2 -> {
                        val currentSuccess = _charactersState.value as? DetailTabState.Success
                        val hasIncompleteCharacters = !charactersFetchedWithDetail &&
                            _detailState.value != null &&
                            currentSuccess != null &&
                            currentSuccess.data.any { it.imageUrl.isNullOrBlank() || it.isRealMediaRole }
                        val needsRefetch = force ||
                            currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaCharacters(result.source, malId) == null) ||
                            hasIncompleteCharacters
                        if (needsRefetch) {
                            if (currentSuccess == null || currentSuccess.data.isEmpty()) {
                                _charactersState.value = DetailTabState.Loading
                            }
                            val hadDetailAtFetch = _detailState.value != null
                            val resolvedTitle = _detailState.value?.title
                                ?: _detailState.value?.titleEnglish
                                ?: _detailState.value?.titleRomaji
                                ?: result.title
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchCharacters(
                                    source = result.source,
                                    externalId = result.malId,
                                    mediaType = result.type,
                                    realMalId = effectiveRealMalId,
                                    // Credits devre dışıysa ya da TMDB tamamen kapalıysa tmdbId gönderme
                                    tmdbId = if (tmdbEnabled && useCredits) tmdbId else null,
                                    title = resolvedTitle
                                )
                            } ?: run {
                                if (currentSuccess == null || currentSuccess.data.isEmpty()) {
                                    _charactersState.value = DetailTabState.Error(tabFailureCode(result.source))
                                }
                                return@launch
                            }
                            val data = fetched.value
                            charactersFetchedWithDetail = hadDetailAtFetch
                            if (data.isNotEmpty()) {
                                DetailCache.putMediaCharacters(result.source, malId, data)
                            }
                            _charactersState.value = DetailTabState.Success(data)
                            warmBangumiCharacterNames(result.source, malId, data)
                        }
                    }
                    3 -> {
                        val currentSuccess = _staffState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaStaff(result.source, malId) == null)
                        if (needsRefetch) {
                            _staffState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchStaff(
                                    result.source, result.malId, result.type,
                                    tmdbId = if (tmdbEnabled && useCredits) tmdbId else null,
                                    realMalId = effectiveRealMalId
                                )
                            } ?: run {
                                _staffState.value = DetailTabState.Error(tabFailureCode(result.source))
                                return@launch
                            }
                            val data = fetched.value
                            if (data.isNotEmpty()) {
                                DetailCache.putMediaStaff(result.source, malId, data)
                            }
                            _staffState.value = DetailTabState.Success(data)
                            warmBangumiStaffNames(result.source, malId, data)
                        }
                    }
                    4 -> {
                        val currentSuccess = _recommendationsState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaRecommendations(result.source, malId) == null)
                        if (needsRefetch) {
                            _recommendationsState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchRecommendations(
                                    result.source, result.malId, result.type,
                                    tmdbId = if (tmdbEnabled && useMoreLikeThis) tmdbId else null,
                                    realMalId = effectiveRealMalId,
                                    title = result.title
                                )
                            } ?: run {
                                _recommendationsState.value = DetailTabState.Error(tabFailureCode(result.source))
                                return@launch
                            }
                            val data = fetched.value
                            if (data.isNotEmpty()) {
                                DetailCache.putMediaRecommendations(result.source, malId, data)
                            }
                            _recommendationsState.value = DetailTabState.Success(data)
                            warmBangumiRelationTitles(result.source, malId, data, recommendations = true)
                        }
                    }
                    5 -> {
                        val currentSuccess = _relationsState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaRelations(result.source, malId) == null)
                        if (needsRefetch) {
                            _relationsState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchRelations(
                                    result.source, result.malId, result.type,
                                    tmdbId = if (tmdbEnabled && useCollections) tmdbId else null,
                                    realMalId = effectiveRealMalId,
                                    title = result.title
                                )
                            } ?: run {
                                _relationsState.value = DetailTabState.Error(tabFailureCode(result.source))
                                return@launch
                            }
                            val data = fetched.value
                            if (data.isNotEmpty()) {
                                DetailCache.putMediaRelations(result.source, malId, data)
                            }
                            _relationsState.value = DetailTabState.Success(data)
                            warmBangumiRelationTitles(result.source, malId, data, recommendations = false)
                        }
                    }
                    6 -> {
                        if (_statsState.value !is DetailTabState.Success) {
                            _statsState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchStats(
                                    result.source, result.malId, result.type,
                                    realMalId = effectiveRealMalId,
                                    tmdbId = if (tmdbEnabled) tmdbId else null
                                )
                            } ?: run {
                                _statsState.value = DetailTabState.Error(tabFailureCode(result.source))
                                return@launch
                            }
                            val data = fetched.value
                            if (data != null) {
                                DetailCache.putMediaStats(result.source, malId, data)
                                _statsState.value = DetailTabState.Success(data)
                            } else {
                                _statsState.value = DetailTabState.Success(null)
                            }
                        }
                    }
                    TAB_REVIEWS -> {
                        val currentSuccess = _reviewsState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaReviews(result.source, malId, result.type.name) == null)
                        if (needsRefetch) {
                            _reviewsState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout {
                                apiClient.fetchReviews(
                                    result.source, result.malId, result.type,
                                    tmdbId = if (tmdbEnabled) tmdbId else null,
                                    realMalId = effectiveRealMalId
                                )
                            } ?: run {
                                _reviewsState.value = DetailTabState.Error(tabFailureCode(result.source))
                                return@launch
                            }
                            val data = fetched.value
                            if (data.isNotEmpty()) {
                                DetailCache.putMediaReviews(result.source, malId, data, result.type.name)
                            }
                            _reviewsState.value = DetailTabState.Success(data)
                        }
                    }
                    TAB_EPISODES -> {
                        val currentEpisodes = _episodesState.value
                        // Boş liste veya Loading ise yeniden yükle; force ise her zaman
                        val needsEpFetch = force ||
                            currentEpisodes !is DetailTabState.Success ||
                            (currentEpisodes is DetailTabState.Success && currentEpisodes.data.isEmpty())
                        if (needsEpFetch) {
                            _episodesState.value = DetailTabState.Loading
                            val fetched = fetchTabWithTimeout(TAB_EPISODES_FETCH_TIMEOUT_MS) {
                                apiClient.fetchEpisodes(
                                    source = result.source,
                                    externalId = result.malId,
                                    mediaType = result.type,
                                    realMalId = effectiveRealMalId,
                                    totalEpisodes = result.total ?: _detailState.value?.total,
                                    context = context,
                                    targetSeason = _targetSeason.value,
                                    // Episodes (TMDB bölüm başlıkları/thumbnail'ları) devre dışıysa tmdbId gönderme
                                    tmdbId = if (tmdbEnabled && useEpisodes) tmdbId else null
                                )
                            } ?: run {
                                _episodesState.value = DetailTabState.Error(tabFailureCode(result.source))
                                return@launch
                            }
                            val data = fetched.value
                            if (data.isNotEmpty()) {
                                DetailCache.putMediaEpisodes(result.source, malId, data)
                            }
                            _episodesState.value = DetailTabState.Success(data)
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Sonuç değişti ya da sekme yeniden başlatıldı: state'e dokunma, yeni yükleme yönetir.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading tab data for index $tabIndex: ${e.message}", e)
                when (tabIndex) {
                    2 -> _charactersState.value = DetailTabState.Error(tabFailureCode(result.source))
                    3 -> _staffState.value = DetailTabState.Error(tabFailureCode(result.source))
                    4 -> _recommendationsState.value = DetailTabState.Error(tabFailureCode(result.source))
                    5 -> _relationsState.value = DetailTabState.Error(tabFailureCode(result.source))
                    6 -> _statsState.value = DetailTabState.Error(tabFailureCode(result.source))
                    TAB_REVIEWS -> _reviewsState.value = DetailTabState.Error(tabFailureCode(result.source))
                    TAB_EPISODES -> _episodesState.value = DetailTabState.Error(tabFailureCode(result.source))
                }
            }
        }
    }

    /**
     * Sekme veri çağrısını süre sınırıyla çalıştırır. Süre dolarsa null döner ve iş iptal edilir.
     *
     * ÖNEMLİ: `withTimeoutOrNull { withContext(IO) { bloklayan ağ çağrısı } }` deseni sert bir sınır
     * DEĞİLDİR: withContext, bloklayan çağrı bitene kadar dönmez. Burada iş bir ÇOCUK `async` olarak
     * başlatılır ve yalnızca `await()` sınırlanır; süre dolunca UI beklemeyi bırakır.
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

    /** Önceki sonucun sekme yüklemelerini iptal eder; eski veri yeni sonucun state'ine yazılamaz. */
    private fun cancelTabLoads() {
        tabJobs.values.forEach { it.cancel() }
        tabJobs.clear()
        tabRequestKeys.clear()
    }

    private fun refreshMdbListRatings(result: JikanSearchResult) {
        val requestKey = currentFetchKey ?: return
        mdbListFetchGeneration++
        val generation = mdbListFetchGeneration
        mdbListFetchJob?.cancel()
        _mdbListRatings.value = null
        _mdbListLoading.value = false
        mdbListFetchJob = viewModelScope.launch {
            try {
                fetchMdbListRatings(result, requestKey, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "MDBList rating fetch failed", e)
                if (generation == mdbListFetchGeneration && currentFetchKey == requestKey) {
                    _mdbListRatings.value = null
                    _mdbListLoading.value = false
                }
            }
        }
    }

    private suspend fun fetchMdbListRatings(
        result: JikanSearchResult,
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
        val isManga = result.type == MediaType.Manga || _detailState.value?.type == MediaType.Manga
        if (isManga) {
            _mdbListRatings.value = null
            _mdbListLoading.value = false
            return
        }

        _mdbListLoading.value = true
        try {
            var detail = _detailState.value
            val rawId = result.malId
            val source = result.source.trim().lowercase()
            val isAniList = source == "anilist"
            val isBangumi = source == "bangumi" || source == "bgm"
            val isKitsu = source == "kitsu"
            val isTmdb = source == "tmdb"
            val isSimkl = source == "simkl"
            val aniListId = if (isAniList && rawId >= 100_000_000) rawId - 100_000_000 else null
            var tmdbId = detail?.tmdbId ?: result.tmdbId ?: _resolvedTmdbId.value
            if (tmdbId == null && isTmdb) tmdbId = rawId.takeIf { it > 0 }
            var imdbId = detail?.externalLinks
                ?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() }
            var kitsuId = if (isKitsu) {
                com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(rawId)
            } else null
            var bangumiImdbId: String? = null

            val realMalId = when {
                aniListId != null -> result.realMalId?.takeIf { it in 1..99_999_999 }
                isAniList -> result.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: rawId.takeIf { it in 1..99_999_999 }
                isBangumi -> {
                    val crossIds = KitsugiBangumiDetailClient.resolveCrossIds(rawId, result.type)
                    bangumiImdbId = crossIds.imdbId
                    tmdbId = tmdbId ?: crossIds.tmdbId
                    detail?.realMalId?.takeIf { it in 1..99_999_999 } ?: crossIds.malId
                }
                isKitsu -> detail?.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: result.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(rawId)
                isTmdb -> detail?.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: result.realMalId?.takeIf { it in 1..99_999_999 }
                isSimkl -> {
                    val crossIds = KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = rawId,
                        mediaType = result.type,
                        hintTmdbId = tmdbId,
                        hintMalId = result.realMalId ?: detail?.realMalId
                    )
                    tmdbId = tmdbId ?: crossIds.tmdbId
                    if (imdbId.isNullOrBlank()) imdbId = crossIds.imdbId
                    if (kitsuId == null) kitsuId = crossIds.kitsuId
                    detail?.realMalId?.takeIf { it in 1..99_999_999 }
                        ?: result.realMalId?.takeIf { it in 1..99_999_999 }
                        ?: crossIds.malId
                }
                source == "shikimori" -> detail?.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: KitsugiIdResolver.resolveMalIdFromShikimori(rawId)
                else -> detail?.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: result.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: rawId.takeIf { it in 1..99_999_999 }
            }

            if (imdbId.isNullOrBlank()) imdbId = bangumiImdbId
            if (imdbId.isNullOrBlank() || tmdbId == null) {
                val resolved = KitsugiIdResolver.resolveIds(
                    malId = realMalId,
                    aniListId = aniListId,
                    tmdbId = tmdbId,
                    mediaType = result.type,
                    kitsuId = kitsuId,
                    bangumiId = rawId.takeIf { isBangumi }
                )
                if (imdbId.isNullOrBlank()) imdbId = resolved.imdbId
                if (tmdbId == null) tmdbId = resolved.tmdbId
            }

            // Detail metadata may contain an IMDb link/TMDB ID that is not available on the
            // original search result. If identity resolution found nothing, retry after the
            // already-running detail request completes (bounded so a slow provider cannot hang).
            if (imdbId.isNullOrBlank() && (tmdbId == null || tmdbId <= 0) && _detailLoading.value) {
                withTimeoutOrNull(TAB_FETCH_TIMEOUT_MS) { _detailLoading.first { !it } }
                if (!isCurrentRequest()) return
                detail = _detailState.value
                tmdbId = tmdbId ?: detail?.tmdbId ?: result.tmdbId ?: _resolvedTmdbId.value
                if (imdbId.isNullOrBlank()) {
                    imdbId = detail?.externalLinks
                        ?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                        ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() }
                }
                if (imdbId.isNullOrBlank() || tmdbId == null) {
                    val resolved = KitsugiIdResolver.resolveIds(
                        malId = detail?.realMalId?.takeIf { it in 1..99_999_999 } ?: realMalId,
                        aniListId = aniListId,
                        tmdbId = tmdbId,
                        mediaType = result.type,
                        kitsuId = kitsuId,
                        bangumiId = rawId.takeIf { isBangumi }
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
                Log.d(TAG, "MDBList skipped: no IMDb or TMDB identity is available")
                if (isCurrentRequest()) _mdbListRatings.value = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MDBList rating fetch failed", e)
            if (isCurrentRequest()) _mdbListRatings.value = null
        } finally {
            if (isCurrentRequest()) _mdbListLoading.value = false
        }

        viewModelScope.launch {
            MdbListQuotaCenter.refresh(settings.mdbListApiKey, maxAgeMillis = 10L * 60_000L)
        }
    }

    private suspend fun fetchFanartGallery(result: JikanSearchResult) {
        val isManga = result.type == MediaType.Manga || _detailState.value?.type == MediaType.Manga
        if (isManga) {
            // Manga içerikleri TMDB veya Fanart.tv'de yer almaz. Kitsu manga ID'lerinin ARM API
            // üzerinden rastgele animelerle (örn. Berserk -> Hungry Heart) eşleşmesini ve yanlış
            // resimlerin/logoların galeriye dolmasını engelliyoruz. Yalnızca eserin kendi görselleri kullanılır.
            val currentDetail = _detailState.value
            val coverUrl = currentDetail?.imageUrl ?: result.imageUrl
            val searchImg = result.imageUrl
            val existingItems = buildList {
                if (!coverUrl.isNullOrBlank()) {
                    val src = determineGallerySource(coverUrl, result.source)
                    add(GalleryItem(url = coverUrl, source = src, category = GalleryCategory.POSTER))
                }
                if (!searchImg.isNullOrBlank() && searchImg != coverUrl) {
                    val src = determineGallerySource(searchImg, result.source)
                    add(GalleryItem(url = searchImg, source = src, category = GalleryCategory.POSTER))
                }
                currentDetail?.bannerImage?.let { bannerUrl ->
                    if (bannerUrl.isNotBlank() && bannerUrl != coverUrl && bannerUrl != searchImg) {
                        val src = determineGallerySource(bannerUrl, result.source)
                        add(GalleryItem(url = bannerUrl, source = src, category = GalleryCategory.BACKDROP))
                    }
                }
                currentDetail?.pictures?.forEach { picUrl ->
                    if (picUrl.isNotBlank() && picUrl != coverUrl && picUrl != searchImg && picUrl != currentDetail.bannerImage) {
                        val src = determineGallerySource(picUrl, result.source)
                        add(GalleryItem(url = picUrl, source = src, category = GalleryCategory.POSTER))
                    }
                }
            }
            // ── MAL (Jikan) /pictures: manga galerisine ek görseller ──────────────
            // Manga TMDB/Fanart.tv'de yok; en zengin ek görsel kaynağı MAL'dir. Kayıt hangi
            // platformdan açılmış olursa olsun (AniList/Kitsu/Bangumi/…) MAL kimliği
            // çözülebiliyorsa görseller galeriye katılır.
            val mangaMalId = resolveMediaGalleryIdentity(result, currentDetail, null).malId
                ?: result.realMalId?.takeIf { it in 1..99_999_999 }
                ?: currentDetail?.realMalId?.takeIf { it in 1..99_999_999 }
            val malItems = fetchMalGalleryItems(mangaMalId, MediaType.Manga)

            _galleryItems.value = (existingItems + malItems)
                .distinctBy { KitsugiBangumiDetailClient.galleryDedupKey(it.url) }
            return
        }

        val isBangumiResult = result.source.equals("bangumi", ignoreCase = true)
        // Bangumi: Fanart.tv / TMDB / Shikimori görselleri için çapraz kimlikler (TMDB, MAL, AniList,
        // Kitsu) çözülür. Sonuç kalıcı önbelleklidir; ilk çözüm 15 sn ile sınırlıdır.
        val bangumiCross = if (isBangumiResult) {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(15_000L) {
                    runCatching { KitsugiBangumiDetailClient.resolveCrossIds(result.malId, result.type) }.getOrNull()
                }
            }
        } else null

        val tmdbId = withContext(Dispatchers.IO) {
            val stableId = result.malId
            val detailTmdb = _detailState.value?.tmdbId
            when {
                detailTmdb != null && detailTmdb > 0 -> detailTmdb
                result.tmdbId != null && result.tmdbId > 0 -> result.tmdbId
                result.source.equals("tmdb", ignoreCase = true) -> if (stableId > 0) stableId else null
                result.source.equals("anilist", ignoreCase = true) -> {
                    if (stableId >= 100_000_000) {
                        val aniListId = stableId - 100_000_000
                        KitsugiEpisodeRatingsRepository.resolveTmdbIdFromAniList(aniListId)
                    } else {
                        KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(stableId)
                    }
                }
                result.source.equals("kitsu", ignoreCase = true) -> {
                    val kitsuId = stableId - 300_000_000
                    KitsugiEpisodeRatingsRepository.resolveTmdbIdFromKitsu(kitsuId)
                }
                result.source.equals("shikimori", ignoreCase = true) -> {
                    // Shikimori ID'si MAL ID değildir: TMDB kimliği gerçek MAL ID'si
                    // üzerinden çözülür (aksi hâlde galeri alakasız yapımın görselleriyle doluyordu).
                    val malId = _detailState.value?.realMalId?.takeIf { it > 0 }
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(stableId)
                    if (malId != null && malId > 0) KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId) else null
                }
                isBangumiResult -> {
                    // Bangumi stableId'si MAL ID DEĞİLDİR; yalnızca çözülen çapraz kimlikler kullanılır.
                    bangumiCross?.tmdbId?.takeIf { it > 0 }
                        ?: bangumiCross?.malId?.takeIf { it > 0 }
                            ?.let { KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(it) }
                        ?: bangumiCross?.aniListId?.takeIf { it > 0 }
                            ?.let { KitsugiEpisodeRatingsRepository.resolveTmdbIdFromAniList(it) }
                }
                stableId > 0 -> KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(stableId)
                else -> null
            }
        }

        val isMovie = KitsugiEpisodeRatingsRepository.prefersMovieLogo(
            type = _detailState.value?.type ?: result.type,
            format = _detailState.value?.format,
            rawFormat = _detailState.value?.rawFormat,
            subtitle = result.subtitle,
            title = listOfNotNull(_detailState.value?.title, result.title, result.titleEnglish).joinToString(" | ")
        )

        // Fanart.tv TV/Anime için TVDB ID gerekir; Room cache'de yoksa
        // fallback olarak MAL ID ve AniList ID'yi de gönderiyoruz.
        val fallbackMalId: Int? = when {
            result.source.equals("anilist", ignoreCase = true) -> result.realMalId
            result.source.equals("kitsu", ignoreCase = true) -> null
            // Shikimori: result.malId Shikimori kimliğidir → gerçek MAL ID'si çözülür.
            result.source.equals("shikimori", ignoreCase = true) ->
                _detailState.value?.realMalId?.takeIf { it > 0 }
                    ?: KitsugiIdResolver.resolveMalIdFromShikimori(result.malId)
            // Bangumi: result.malId Bangumi stableId'sidir → çözülen gerçek MAL ID'si kullanılır.
            isBangumiResult ->
                bangumiCross?.malId ?: _detailState.value?.realMalId?.takeIf { it in 1..99_999_999 }
            !result.source.equals("tmdb", ignoreCase = true) -> if (result.malId > 0) result.malId else null
            else -> null
        }
        val fallbackAniListId: Int? = when {
            result.source.equals("anilist", ignoreCase = true) && result.malId >= 100_000_000 ->
                result.malId - 100_000_000
            isBangumiResult -> bangumiCross?.aniListId
            else -> null
        }
        val fallbackKitsuId: Int? = when {
            result.source.equals("kitsu", ignoreCase = true) && result.malId >= 300_000_000 ->
                result.malId - 300_000_000
            isBangumiResult -> bangumiCross?.kitsuId
            else -> null
        }

        // Kitsu listesi/detayında tür "Anime" olarak gelir; film olan Kitsu kaydını alt türünden anlarız.
        // MAL/AniList listeleri de filmleri "Anime/TV" olarak taşıyabildiği için son çare
        // ARM/animeapi medya türüdür (isTmdbMovie): aksi halde film galerisine ve logosuna
        // TMDB ID çakışması/TVDB sızıntısı ile TV dizisinin görselleri karışır.
        val galleryDetail = _detailState.value
        val galleryIsMovie = isMovie ||
            galleryDetail?.type == MediaType.Movie ||
            KitsugiEpisodeRatingsRepository.isKitsuMovieId(fallbackKitsuId) ||
            KitsugiEpisodeRatingsRepository.isTmdbMovie(tmdbId)
        // TMDB kimliği çapraz eşlemeyle bulunduğundan görseller başlık doğrulamasından geçer.
        val galleryTitles = listOfNotNull(
            result.title, result.titleEnglish, result.titleJapanese, result.titleRomaji,
            galleryDetail?.title, galleryDetail?.titleEnglish, galleryDetail?.titleRomaji,
            galleryDetail?.titleJapanese
        ).filter { it.isNotBlank() }.distinct()

        // MAL (Jikan) kimliği: Jikan/MAL kayıtlarında doğrudan, diğer kaynaklarda çapraz
        // eşlemeden gelir. Bulunamazsa istek yapılmaz (yanlış eserin görselleri katılmaz).
        val malGalleryId = resolveMediaGalleryIdentity(result, _detailState.value, bangumiCross).malId
            ?: fallbackMalId?.takeIf { it in 1..99_999_999 }
            ?: _detailState.value?.realMalId?.takeIf { it in 1..99_999_999 }

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
                val isAnime = result.type == MediaType.Anime
                val shikimoriAnimeId = when {
                    result.source.equals("shikimori", ignoreCase = true) -> if (result.malId > 0) result.malId else null
                    result.source.equals("anilist", ignoreCase = true) -> result.realMalId ?: _detailState.value?.realMalId ?: (if (result.malId > 0 && result.malId < 100_000_000) result.malId else null)
                    result.source.equals("mal", ignoreCase = true) || result.source.equals("jikan", ignoreCase = true) -> if (result.malId > 0) result.malId else null
                    // Shikimori anime kimliği = MAL kimliği: Bangumi'de çözülen MAL ID ile ekran görüntüleri çekilir.
                    isBangumiResult -> bangumiCross?.malId ?: _detailState.value?.realMalId?.takeIf { it in 1..99_999_999 }
                    else -> _detailState.value?.realMalId
                }
                if (isAnime && shikimoriAnimeId != null && shikimoriAnimeId > 0) {
                    KitsugiShikimoriClient.fetchScreenshots(shikimoriAnimeId)
                } else emptyList()
            }
            val malDef = async(Dispatchers.IO) {
                fetchMalGalleryItems(
                    malId = malGalleryId,
                    mediaType = if (result.type == MediaType.Manga || _detailState.value?.type == MediaType.Manga) {
                        MediaType.Manga
                    } else {
                        MediaType.Anime
                    }
                )
            }
            // Quadruple: (Fanart, TMDB, Shikimori, MAL) — dördü PARALEL çekilir.
            listOf(fanartDef.await(), tmdbDef.await(), shikimoriDef.await(), malDef.await())
        }
        val malGalleryItems = malItems

        val currentDetail = _detailState.value
        val coverUrl = currentDetail?.imageUrl ?: result.imageUrl
        val searchImg = result.imageUrl
        val existingItems = buildList {
            if (!coverUrl.isNullOrBlank()) {
                val src = determineGallerySource(coverUrl, result.source)
                add(GalleryItem(url = coverUrl, source = src, category = GalleryCategory.POSTER))
            }
            if (!searchImg.isNullOrBlank() && searchImg != coverUrl) {
                val src = determineGallerySource(searchImg, result.source)
                add(GalleryItem(url = searchImg, source = src, category = GalleryCategory.POSTER))
            }
            currentDetail?.pictures?.forEach { url ->
                if (url.isNotBlank() && url != coverUrl && url != searchImg) {
                    val src = determineGallerySource(url, result.source)
                    val cat = determineGalleryCategory(url, GalleryCategory.POSTER)
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
     * MAL (Jikan) `/pictures` uç noktasından galeri öğeleri.
     *
     * MAL'A BAĞLI KAYNAKLAR: Jikan (MAL v4), Shikimori ve MAL kimliği çözülen tüm
     * platformlar (AniList/Kitsu/Simkl/Bangumi…) aynı galeriyi paylaşır. Görsel
     * kategorisi URL'den çıkarılır (poster / backdrop / thumbnail…). Kimlik yoksa
     * veya istek başarısızsa boş liste döner — galeri bu yüzden eksik kalmaz.
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
                source = determineGallerySource(clean, "jikan"),
                category = determineGalleryCategory(clean, GalleryCategory.POSTER),
                description = "MAL / Jikan ek görsel"
            )
        }
    }

    private fun determineGallerySource(url: String, fallbackSource: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("fanart.tv") -> "Fanart.tv"
            lowerUrl.contains("image.tmdb.org") || lowerUrl.contains("tmdb.org") -> "TMDB"
            lowerUrl.contains("shikimori.") -> "Shikimori"
            lowerUrl.contains("anilist.co") -> "AniList"
            lowerUrl.contains("simkl.in") || lowerUrl.contains("simkl.com") -> "Simkl"
            lowerUrl.contains("myanimelist.net") || lowerUrl.contains("jikan.moe") -> "Jikan (MAL)"
            lowerUrl.contains("kitsu.io") || lowerUrl.contains("kitsu.app") -> "Kitsu"
            lowerUrl.contains("bgm.tv") -> "Bangumi"
            else -> when (fallbackSource.lowercase()) {
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

    private fun determineGalleryCategory(url: String, defaultCategory: GalleryCategory): GalleryCategory {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("clearart") -> GalleryCategory.CLEARART
            lowerUrl.contains("logo") -> GalleryCategory.LOGO
            lowerUrl.contains("backdrop") || lowerUrl.contains("background") || lowerUrl.contains("/w1280") || lowerUrl.contains("showbackground") -> GalleryCategory.BACKDROP
            lowerUrl.contains("square") -> GalleryCategory.SQUARE
            lowerUrl.contains("poster") || lowerUrl.contains("/w780") || lowerUrl.contains("/w500") || lowerUrl.contains("/w342") || lowerUrl.contains("coverimage") || lowerUrl.contains("large_image_url") -> GalleryCategory.POSTER
            lowerUrl.contains("character") || lowerUrl.contains("actor") || lowerUrl.contains("voiceactor") -> GalleryCategory.CHARACTER
            lowerUrl.contains("thumb") || lowerUrl.contains("still") || lowerUrl.contains("/w300") || lowerUrl.contains("/w185") -> GalleryCategory.THUMBNAIL
            lowerUrl.contains("banner") -> GalleryCategory.BANNER
            else -> defaultCategory
        }
    }

    private suspend fun fetchAggregatedVideos(result: JikanSearchResult, expectedKey: String?) {
        if (expectedKey != null && currentFetchKey != expectedKey) return
        val season = _targetSeason.value
        DetailCache.getMediaVideos(result.source, result.malId, season)?.let { cached ->
            if (expectedKey == null || currentFetchKey == expectedKey) {
                _videoItems.value = cached
                enrichDetailWithCrossSourceVideos(result, cached)
            }
            return
        }

        val currentDetail = _detailState.value
        val initialSeed = KitsugiMediaVideoAggregator.seedFromDetail(currentDetail, result.source)
        if (initialSeed.isNotEmpty() && _videoItems.value.isEmpty()) {
            _videoItems.value = initialSeed
        }

        val imdbFromLinks = currentDetail?.externalLinks
            ?.firstOrNull { it.url.contains("imdb.com/title/") }
            ?.url?.substringAfter("/title/")?.substringBefore("/")?.substringBefore("?")

        val knownIds = KnownVideoMediaIds(
            malId = (currentDetail?.realMalId ?: result.realMalId)?.takeIf { it in 1..99_999_999 },
            tmdbId = (currentDetail?.tmdbId ?: result.tmdbId ?: _resolvedTmdbId.value)?.takeIf { it > 0 },
            imdbId = imdbFromLinks
        )

        val fallbackTitle = listOfNotNull(
            currentDetail?.title,
            result.title,
            currentDetail?.titleEnglish ?: result.titleEnglish,
            currentDetail?.titleRomaji ?: result.titleRomaji,
            currentDetail?.titleJapanese ?: result.titleJapanese
        ).firstOrNull { it.isNotBlank() }

        val merged = KitsugiMediaVideoAggregator.aggregateVideos(
            id = result.malId,
            source = result.source,
            mediaType = currentDetail?.type ?: result.type,
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

        if (expectedKey == null || currentFetchKey == expectedKey) {
            _videoItems.value = merged
            enrichDetailWithCrossSourceVideos(result, merged)
        }
    }

    private fun enrichDetailWithCrossSourceVideos(result: JikanSearchResult, videos: List<MediaVideoItem>) {
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
        DetailCache.putMediaDetail(result.source, result.malId, updated)
    }
}

