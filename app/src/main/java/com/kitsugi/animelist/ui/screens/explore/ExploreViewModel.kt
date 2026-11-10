package com.kitsugi.animelist.ui.screens.explore

import android.app.Application
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.SimklSyncManager
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
// SimklApiClient: discovery için artık kullanılmıyor; sadece SimklSyncManager üzerinden watchlist sync'te kullanılır
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.kitsugi.animelist.model.MediaType

private fun <T> Set<T>.snapshot(): Set<T> = synchronized(this) { toSet() }

/** Filter on the source's stable Turkish labels first, then localize only the visible subtitle. */
private fun localizedSimklStatusItems(
    items: List<JikanSearchResult>,
    sourceStatus: String,
    displayStatus: String
): List<JikanSearchResult> = items
    .filter { it.subtitle.contains(sourceStatus) }
    .map { it.copy(subtitle = displayStatus) }

/**
 * Keşfet hatalarının türünü belirler — UI'da platforma özgü aksiyon butonları göstermek için.
 * - [TmdbError]: TMDB API anahtarı geçersiz veya eksik → Ayarlara yönlendir
 * - [AniListError]: AniList servis hatası veya token sorunları → Giriş yap
 * - [MalError]: MAL/Jikan servis hatası → Giriş yap
 * - [None]: Hata yok
 */
enum class ExploreErrorType { None, TmdbError, AniListError, MalError }

/**
 * Bangumi Keşfet şeridi başına üst süre sınırı.
 *
 * Şeritler paralel çalışır; bu tavan **toplam** süreyi değil, en yavaş şeridin süresini
 * sınırlar. Asılı bir istek tüm vitrini dakikalarca boş bırakmasın diye 30 sn seçildi
 * (önceki 15 sn, Bangumi hız sınırlayıcısı + AniList başlık çözümlemesi birlikte
 * olduğunda özellikle sezon şeritlerinde yetmiyordu — “geri kalan kısımlar gelmiyor”
 * şikayetinin kök nedeni).
 */
private const val BANGUMI_SHELF_TIMEOUT_MS = 30_000L

class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = JikanApiClient(
        aniListToken = ExternalAuthManager.getAniListToken(application)
    )
    // simklApiClient: discovery/trending için KALDIRILDI — sadece SimklSyncManager üzerinden watchlist sync
    // tmdbApiClient: settings'ten tmdbUserApiKey yüklenince rebuild edilir
    private var tmdbApiClient = TmdbApiClient()
    private val settingsDataStore = SettingsDataStore(application)
    private var tmdbUserApiKeyState = ""

    // Her platform için ayrı cache — platform geçişinde yeniden fetch yapılmaz
    private val platformCache = Companion.platformCache
    // Başarıyla yüklenen platformların seti — boş veri dönsün, takılmaması için
    private val loadedPlatforms = Companion.loadedPlatforms

    private var isFallbackInProgress = false
    private var isFirstLoad = true
    private var showAdultContentState = false
    private var loadJob: Job? = null
    private val sourceJobs = mutableMapOf<ExplorePlatform, Job>()
    private var requestGeneration = 0

    private var heroPreparationJob: Job? = null
    private var heroPreparationGeneration = 0
    private val homeHeroArtworkLoader by lazy {
        com.kitsugi.animelist.ui.components.HomeHeroArtworkLoader(getApplication())
    }
    private var preparedHeroSeed: Int? = null

    var heroRefreshSeed by mutableIntStateOf(kotlin.random.Random.nextInt())
        private set
    var homeHeroItems by mutableStateOf<List<JikanSearchResult>>(emptyList())
        private set
    var isHeroLoading by mutableStateOf(false)
        private set

    var allSourceStates by mutableStateOf<Map<ExplorePlatform, ExploreSourceState>>(emptyMap())
        private set

    /** AniList shelves are fetched in small batches as their rows enter the viewport. */
    var aniListLoadedCategories by mutableStateOf(Companion.loadedAniListCategories.snapshot())
        private set
    var aniListLoadingCategories by mutableStateOf(emptySet<ExploreCategoryType>())
        private set
    var aniListFailedCategories by mutableStateOf(Companion.failedAniListCategories.snapshot())
        private set

    private val pendingAniListCategoryLock = Any()
    private val pendingAniListCategories = linkedSetOf<ExploreCategoryType>()
    private var aniListCategoryQueueJob: Job? = null

    private fun cancelLoads() {
        requestGeneration++
        heroPreparationGeneration++
        heroPreparationJob?.cancel()
        isHeroLoading = false
        heroRefreshSeed = kotlin.random.Random.nextInt()
        loadJob?.cancel()
        loadJob = null
        sourceJobs.values.forEach { it.cancel() }
        sourceJobs.clear()
        cancelAniListCategoryLoads()
        isLoading = false
    }

    private fun cancelAniListCategoryLoads() {
        aniListCategoryQueueJob?.cancel()
        aniListCategoryQueueJob = null
        synchronized(pendingAniListCategoryLock) { pendingAniListCategories.clear() }
        aniListLoadingCategories = emptySet()
    }

    /**
     * Kalıcı (persisted) kaynak seçimi. Uygulama kapatılıp açıldığında Keşfet ekranı
     * kullanıcının son seçtiği kaynakla açılır — varsayılan: Tümü (7 kaynak birlikte).
     */
    var selectedPlatform by mutableStateOf(ExplorePlatform.ALL)
        private set

    private var platformRestored = false

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Sayfa hatasının platform hata kodu (Tümü sekmesinde tek bir kaynak yoktur). */
    var platformErrorCode by mutableStateOf<PlatformErrorCode?>(null)
        private set

    /** Hatanın kaynağını belirtir — UI'da platforma özgü yönlendirme butonu göstermek için */
    var exploreErrorType by mutableStateOf(ExploreErrorType.None)
        private set

    var isShowingCachedData by mutableStateOf(false)
        private set

    // TMDB entegrasyon durumu — settingsFlow'dan reaktif olarak güncellenir
    private var tmdbEnabledState = true
    var tmdbModernHomeEnabled by mutableStateOf(true)
        private set
    private var tmdbEnrichContinueWatchingState = true

    /**
     * Son görülen "Dizi & Film Başlık Dili" (tmdbLanguage) değeri.
     *
     * KRİTİK: Kullanıcı bu ayarı değiştirdiğinde (ör. English → Türkçe) TMDB şeritlerinin
     * tamamının YENİ dilde yeniden çekilmesi gerekir. Eski kontrol yalnızca
     * enabled/enrich/apiKey değişimlerini izliyordu; dil değişimi göz ardı edildiği için
     * bellek içi platformCache + allSourceStates eski dildeki başlıklarla yaşamaya devam
     * ediyordu — "Türkçe seçtim ama isimler hâlâ Türkçe değil" şikayetinin kök nedeni.
     */
    private var tmdbLanguageState = ""

    private val initialPayload: ExplorePayload?
        get() = platformCache[selectedPlatform]

    var topAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.topAnime ?: emptyList())
        private set

    var airingAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.airingAnime ?: emptyList())
        private set

    var upcomingAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.upcomingAnime ?: emptyList())
        private set

    var topManga by mutableStateOf<List<JikanSearchResult>>(initialPayload?.topManga ?: emptyList())
        private set

    var publishingManga by mutableStateOf<List<JikanSearchResult>>(initialPayload?.publishingManga ?: emptyList())
        private set

    var trendingAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.trendingAnime ?: emptyList())
        private set

    var topRatedAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.topRatedAnime.orEmpty())
        private set

    var topRatedManga by mutableStateOf<List<JikanSearchResult>>(initialPayload?.topRatedManga.orEmpty())
        private set

    var movieAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.movieAnime ?: emptyList())
        private set

    var bangumiTvShows by mutableStateOf<List<JikanSearchResult>>(initialPayload?.bangumiTvShows.orEmpty())
        private set

    var bangumiMovies by mutableStateOf<List<JikanSearchResult>>(initialPayload?.bangumiMovies.orEmpty())
        private set

    var manhwaManhua by mutableStateOf<List<JikanSearchResult>>(initialPayload?.manhwaManhua.orEmpty())
        private set

    var novels by mutableStateOf<List<JikanSearchResult>>(initialPayload?.novels.orEmpty())
        private set

    var seasonalAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.seasonalAnime ?: emptyList())
        private set

    var airingSoonAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.airingSoonAnime ?: emptyList())
        private set

    var trendingManga by mutableStateOf<List<JikanSearchResult>>(initialPayload?.trendingManga ?: emptyList())
        private set

    var newlyAddedAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.newlyAddedAnime ?: emptyList())
        private set

    var newlyAddedManga by mutableStateOf<List<JikanSearchResult>>(initialPayload?.newlyAddedManga ?: emptyList())
        private set

    /** TMDB'ye özgü "Yakında Yayında" içerikleri — ExploreCategoryType.UPCOMING_MEDIA_TMDB ile sayfalanır */
    var upcomingMediaTmdb by mutableStateOf<List<JikanSearchResult>>(initialPayload?.upcomingMediaTmdb ?: emptyList())
        private set

    // ── Simkl Kullanıcı Listeleri (NyanTV HomeSections.kt referans) ──────────────
    /** İzlemeye devam et — filmler (Simkl status=watching, isMovie=true) */
    var simklContinueMovies by mutableStateOf<List<JikanSearchResult>>(initialPayload?.simklContinueMovies ?: emptyList())
        private set

    /** Planladıklarım — filmler (Simkl status=plantowatch, isMovie=true) */
    var simklPlannedMovies by mutableStateOf<List<JikanSearchResult>>(initialPayload?.simklPlannedMovies ?: emptyList())
        private set

    /** İzlemeye devam et — diziler/anime (Simkl status=watching, isMovie=false) */
    var simklContinueSeries by mutableStateOf<List<JikanSearchResult>>(initialPayload?.simklContinueSeries ?: emptyList())
        private set

    /** Planladıklarım — diziler/anime (Simkl status=plantowatch, isMovie=false) */
    var simklPlannedSeries by mutableStateOf<List<JikanSearchResult>>(initialPayload?.simklPlannedSeries ?: emptyList())
        private set

    val isDataLoaded: Boolean
        get() = (selectedPlatform == ExplorePlatform.ALL && allSourceStates.values.any { it.payload != null }) ||
                selectedPlatform in loadedPlatforms ||
                topAnime.isNotEmpty() || airingAnime.isNotEmpty() || trendingAnime.isNotEmpty()

    init {
        viewModelScope.launch {
            // Kaynak seçimi kalıcılığı: açılışta kullanıcının son seçtiği kaynağa dön.
            // Ayar akışı collection'ından ÖNCE okunmalı ki ilk yükleme yanlış platformla yapılmasın.
            if (!platformRestored) {
                platformRestored = true
                val restored = runCatching {
                    settingsDataStore.lastExplorePlatformFlow.firstOrNull()?.let { name ->
                        ExplorePlatform.entries.firstOrNull { it.name == name }
                    }
                }.getOrNull() ?: ExplorePlatform.ALL
                if (restored != selectedPlatform) {
                    selectedPlatform = restored
                    if (restored == ExplorePlatform.ALL) {
                        loadAllSources(forceRefresh = false)
                    } else {
                        platformCache[restored]?.let { cached ->
                            applyPayload(cached)
                            loadedPlatforms.add(restored)
                        }
                    }
                }
            }

            settingsDataStore.settingsFlow.collect { settings ->
                val adultChanged = showAdultContentState != settings.showAdultContent
                showAdultContentState = settings.showAdultContent

                // TMDB toggle değişiklikleri
                val userKey = settings.tmdbUserApiKey
                // Dil değişimi (Dizi & Film Başlık Dili) de tam yeniden yükleme gerektirir:
                // önbellekteki başlıklar eski dilde çözümlenmiş olabilir.
                val tmdbLanguageChanged = tmdbLanguageState.isNotBlank() &&
                    tmdbLanguageState != settings.tmdbLanguage
                val tmdbChanged = tmdbEnabledState != settings.tmdbEnabled ||
                    tmdbEnrichContinueWatchingState != settings.tmdbEnrichContinueWatching ||
                    userKey != tmdbUserApiKeyState ||
                    tmdbLanguageChanged
                tmdbEnabledState = settings.tmdbEnabled
                tmdbModernHomeEnabled = settings.tmdbModernHomeEnabled
                tmdbEnrichContinueWatchingState = settings.tmdbEnrichContinueWatching
                tmdbLanguageState = settings.tmdbLanguage

                // tmdbUserApiKey değişince TmdbApiClient'i yeniden oluştur
                if (userKey != tmdbUserApiKeyState) {
                    tmdbUserApiKeyState = userKey
                    tmdbApiClient = TmdbApiClient(userApiKey = userKey)
                }

                if (adultChanged || tmdbChanged || isFirstLoad) {
                    val wasFirstLoad = isFirstLoad
                    isFirstLoad = false
                    
                    if (wasFirstLoad) {
                        // Prefetch işlemi arka planda devam ediyorsa bitmesini bekle
                        Companion.prefetchJob?.join()

                        val cached = platformCache[selectedPlatform]
                        if (cached != null) {
                            // Cache hit — hemen uygula, loading state'i kısa tut
                            isLoading = true
                            applyPayload(cached)
                            loadedPlatforms.add(selectedPlatform)
                            isLoading = false
                        } else {
                            // Cache miss — loadData() kendi finally bloğuyla isLoading'i yönetir
                            errorMessage = null
                            loadData(forceRefresh = true)
                        }
                    } else {
                        platformCache.clear()
                        loadedPlatforms.clear()
                        allSourceStates = emptyMap()
                        loadData(forceRefresh = true)
                    }
                }
            }
        }
    }

    fun selectPlatform(platform: ExplorePlatform, isFallback: Boolean = false) {
        if (selectedPlatform == platform) return
        if (!isFallback) {
            isFallbackInProgress = false
            // Yalnızca kullanıcının kendi seçimi kalıcı olur; otomatik fallback
            // (örn. TMDB anahtarı bozuksa AniList'e düşme) seçim olarak kaydedilmez.
            persistPlatform(platform)
        }
        cancelLoads()
        isShowingCachedData = false
        selectedPlatform = platform

        // Stale veriyi hemen temizle — eski platformun verisi yeni platformda gözükmesin
        clearPayload()

        if (platform == ExplorePlatform.ALL) {
            loadAllSources(forceRefresh = false)
            return
        }

        // Cache'de varsa anında yükle, yoksa fetch et
        val cached = platformCache[platform]
        if (cached != null) {
            applyPayload(cached)
            // Cache'den yüklenen platformu da "loaded" say
            loadedPlatforms.add(platform)
        } else {
            loadData(forceRefresh = true)
        }
    }

    /** Kullanıcının Keşfet kaynak seçimini disk'e yazar (uygulama yeniden açıldığında korunur). */
    private fun persistPlatform(platform: ExplorePlatform) {
        viewModelScope.launch {
            runCatching { settingsDataStore.setLastExplorePlatform(platform.name) }
        }
    }

    private fun applyPayload(payload: ExplorePayload) {
        topAnime = payload.topAnime
        airingAnime = payload.airingAnime
        upcomingAnime = payload.upcomingAnime
        topManga = payload.topManga
        publishingManga = payload.publishingManga
        trendingAnime = payload.trendingAnime
        topRatedAnime = payload.topRatedAnime.orEmpty()
        topRatedManga = payload.topRatedManga.orEmpty()
        movieAnime = payload.movieAnime
        bangumiTvShows = payload.bangumiTvShows.orEmpty()
        bangumiMovies = payload.bangumiMovies.orEmpty()
        manhwaManhua = payload.manhwaManhua.orEmpty()
        novels = payload.novels.orEmpty()
        seasonalAnime = payload.seasonalAnime
        airingSoonAnime = payload.airingSoonAnime
        trendingManga = payload.trendingManga
        newlyAddedAnime = payload.newlyAddedAnime
        newlyAddedManga = payload.newlyAddedManga
        upcomingMediaTmdb = payload.upcomingMediaTmdb

        simklContinueMovies = payload.simklContinueMovies
        simklPlannedMovies = payload.simklPlannedMovies
        simklContinueSeries = payload.simklContinueSeries
        simklPlannedSeries = payload.simklPlannedSeries
        if (selectedPlatform == ExplorePlatform.AniList) recordLoadedAniListShelves(payload)
    }

    /** Platform değişiminde UI'daki eski veriyi siler, loading skeleton gösterilir. */
    private fun clearPayload() {
        homeHeroItems = emptyList()
        preparedHeroSeed = null
        topAnime = emptyList()
        airingAnime = emptyList()
        upcomingAnime = emptyList()
        topManga = emptyList()
        publishingManga = emptyList()
        trendingAnime = emptyList()
        topRatedAnime = emptyList()
        topRatedManga = emptyList()
        movieAnime = emptyList()
        bangumiTvShows = emptyList()
        bangumiMovies = emptyList()
        manhwaManhua = emptyList()
        novels = emptyList()
        seasonalAnime = emptyList()
        airingSoonAnime = emptyList()
        trendingManga = emptyList()
        newlyAddedAnime = emptyList()
        newlyAddedManga = emptyList()
        upcomingMediaTmdb = emptyList()
        // Simkl kullanıcı şeritlerini temizleme — platform değişiminde de gözüksün
        errorMessage = null
        platformErrorCode = null
        exploreErrorType = ExploreErrorType.None
    }

    private fun resetAniListShelfState() {
        Companion.loadedAniListCategories.clear()
        Companion.failedAniListCategories.clear()
        aniListLoadedCategories = emptySet()
        aniListFailedCategories = emptySet()
        aniListLoadingCategories = emptySet()
    }

    private fun recordLoadedAniListShelves(payload: ExplorePayload) {
        val found = ExploreCategoryType.entries.filter { category ->
            when (category) {
                ExploreCategoryType.TOP_ANIME -> payload.topAnime.isNotEmpty()
                ExploreCategoryType.TOP_RATED_ANIME -> payload.topRatedAnime.orEmpty().isNotEmpty()
                ExploreCategoryType.TRENDING_ANIME -> payload.trendingAnime.isNotEmpty()
                ExploreCategoryType.AIRING_ANIME -> payload.airingAnime.isNotEmpty()
                ExploreCategoryType.UPCOMING_ANIME -> payload.upcomingAnime.isNotEmpty()
                ExploreCategoryType.MOVIE_ANIME -> payload.movieAnime.isNotEmpty()
                ExploreCategoryType.SEASONAL_ANIME -> payload.seasonalAnime.isNotEmpty()
                ExploreCategoryType.NEWLY_ADDED_ANIME -> payload.newlyAddedAnime.isNotEmpty()
                ExploreCategoryType.TOP_MANGA -> payload.topManga.isNotEmpty()
                ExploreCategoryType.TRENDING_MANGA -> payload.trendingManga.isNotEmpty()
                ExploreCategoryType.TOP_RATED_MANGA -> payload.topRatedManga.orEmpty().isNotEmpty()
                ExploreCategoryType.PUBLISHING_MANGA -> payload.publishingManga.isNotEmpty()
                ExploreCategoryType.NEWLY_ADDED_MANGA -> payload.newlyAddedManga.isNotEmpty()
                ExploreCategoryType.MANHWA_MANHUA -> payload.manhwaManhua.orEmpty().isNotEmpty()
                ExploreCategoryType.NOVELS -> payload.novels.orEmpty().isNotEmpty()
                ExploreCategoryType.BANGUMI_TV,
                ExploreCategoryType.BANGUMI_MOVIES,
                ExploreCategoryType.UPCOMING_MEDIA_TMDB -> false
            }
        }.filter { it in ANILIST_EXPLORE_SHELF_CATEGORIES }
        if (found.isNotEmpty()) {
            Companion.loadedAniListCategories.addAll(found)
            aniListLoadedCategories = aniListLoadedCategories + found
            aniListFailedCategories = aniListFailedCategories - found
            Companion.failedAniListCategories.removeAll(found.toSet())
        }
    }

    /** Triggered by a shelf entering view or by an explicit shelf retry. */
    fun loadExploreSection(platform: ExplorePlatform, category: ExploreCategoryType) {
        enqueueAniListCategory(platform, category, retry = false)
    }

    fun retryExploreSection(platform: ExplorePlatform, category: ExploreCategoryType) {
        enqueueAniListCategory(platform, category, retry = true)
    }

    private fun enqueueAniListCategory(
        platform: ExplorePlatform,
        category: ExploreCategoryType,
        retry: Boolean
    ) {
        if (platform != ExplorePlatform.AniList || category !in ANILIST_EXPLORE_SHELF_CATEGORIES) return
        if (selectedPlatform != ExplorePlatform.AniList && selectedPlatform != ExplorePlatform.ALL) return
        if (category in aniListLoadedCategories || category in aniListLoadingCategories) return
        if (category in aniListFailedCategories && !retry) return

        if (retry) {
            Companion.failedAniListCategories.remove(category)
            aniListFailedCategories = aniListFailedCategories - category
        }
        aniListLoadingCategories = aniListLoadingCategories + category
        synchronized(pendingAniListCategoryLock) { pendingAniListCategories.add(category) }
        if (aniListCategoryQueueJob?.isActive != true) {
            val generation = requestGeneration
            aniListCategoryQueueJob = viewModelScope.launch {
                drainAniListCategoryQueue(generation)
            }
        }
    }

    private suspend fun drainAniListCategoryQueue(generation: Int) {
        try {
            while (generation == requestGeneration) {
                kotlinx.coroutines.delay(ANILIST_SHELF_BATCH_DEBOUNCE_MS)
                val batch = synchronized(pendingAniListCategoryLock) {
                    pendingAniListCategories.toList().also { pendingAniListCategories.clear() }
                }
                if (batch.isEmpty()) break
                if (selectedPlatform != ExplorePlatform.AniList && selectedPlatform != ExplorePlatform.ALL) break

                val (season, year) = currentAniListSeasonAndYear()
                val requests = aniListExploreShelfRequests(batch, season, year)
                try {
                    val response = apiClient.aniListExploreShelves(requests.map { it.spec }, showAdultContentState)
                    val successful = batch.filter { category ->
                        val categoryRequests = requests.filter { it.category == category }
                        categoryRequests.isNotEmpty() && categoryRequests.all { response.containsKey(it.spec.alias) }
                    }
                    val failed = batch - successful.toSet()
                    val existing = platformCache[ExplorePlatform.AniList]
                        ?: allSourceStates[ExplorePlatform.AniList]?.payload
                    if (existing == null) {
                        markAniListCategoriesFailed(batch)
                        continue
                    }
                    val updated = successful.fold(existing) { payload, category ->
                        val items = requests.asSequence()
                            .filter { it.category == category }
                            .flatMap { response[it.spec.alias].orEmpty().asSequence() }
                            .distinctBy { it.exploreIdentity() }
                            .toList()
                        payload.withAniListShelf(category, items)
                    }
                    if (successful.isNotEmpty()) {
                        publishAniListPayload(updated)
                        Companion.loadedAniListCategories.addAll(successful)
                        Companion.failedAniListCategories.removeAll(successful.toSet())
                        aniListLoadedCategories = aniListLoadedCategories + successful
                        aniListFailedCategories = aniListFailedCategories - successful.toSet()
                    }
                    if (failed.isNotEmpty()) markAniListCategoriesFailed(failed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    markAniListCategoriesFailed(batch)
                    android.util.Log.w("ExploreViewModel", "AniList shelf batch failed: ${batch.joinToString()}", e)
                } finally {
                    aniListLoadingCategories = aniListLoadingCategories - batch.toSet()
                }
            }
        } finally {
            aniListCategoryQueueJob = null
            val hasPending = synchronized(pendingAniListCategoryLock) { pendingAniListCategories.isNotEmpty() }
            if (hasPending && (selectedPlatform == ExplorePlatform.AniList || selectedPlatform == ExplorePlatform.ALL)) {
                val generation = requestGeneration
                aniListCategoryQueueJob = viewModelScope.launch { drainAniListCategoryQueue(generation) }
            }
        }
    }

    private fun markAniListCategoriesFailed(categories: Collection<ExploreCategoryType>) {
        Companion.failedAniListCategories.addAll(categories)
        aniListFailedCategories = aniListFailedCategories + categories
        aniListLoadingCategories = aniListLoadingCategories - categories.toSet()
    }

    private fun publishAniListPayload(payload: ExplorePayload) {
        platformCache[ExplorePlatform.AniList] = payload
        loadedPlatforms.add(ExplorePlatform.AniList)
        recordLoadedAniListShelves(payload)
        if (selectedPlatform == ExplorePlatform.AniList) applyPayload(payload)
        allSourceStates[ExplorePlatform.AniList]?.let { state ->
            allSourceStates = allSourceStates + (ExplorePlatform.AniList to state.copy(payload = payload))
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(getApplication())
                db.exploreCacheDao().insertCategory(
                    com.kitsugi.animelist.data.local.ExploreCacheEntity(
                        categoryKey = exploreCacheKey(ExplorePlatform.AniList),
                        payloadJson = com.google.gson.Gson().toJson(payload),
                        cachedAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun ExplorePayload.withAniListShelf(
        category: ExploreCategoryType,
        items: List<JikanSearchResult>
    ): ExplorePayload = when (category) {
        ExploreCategoryType.TOP_ANIME -> copy(topAnime = items)
        ExploreCategoryType.TOP_RATED_ANIME -> copy(topRatedAnime = items)
        ExploreCategoryType.TRENDING_ANIME -> copy(trendingAnime = items)
        ExploreCategoryType.AIRING_ANIME -> copy(airingAnime = items)
        ExploreCategoryType.UPCOMING_ANIME -> copy(upcomingAnime = items)
        ExploreCategoryType.MOVIE_ANIME -> copy(movieAnime = items)
        ExploreCategoryType.SEASONAL_ANIME -> copy(seasonalAnime = items)
        ExploreCategoryType.NEWLY_ADDED_ANIME -> copy(newlyAddedAnime = items)
        ExploreCategoryType.TOP_MANGA -> copy(topManga = items)
        ExploreCategoryType.TRENDING_MANGA -> copy(trendingManga = items)
        ExploreCategoryType.TOP_RATED_MANGA -> copy(topRatedManga = items)
        ExploreCategoryType.PUBLISHING_MANGA -> copy(publishingManga = items)
        ExploreCategoryType.NEWLY_ADDED_MANGA -> copy(newlyAddedManga = items)
        ExploreCategoryType.MANHWA_MANHUA -> copy(manhwaManhua = items)
        ExploreCategoryType.NOVELS -> copy(novels = items)
        ExploreCategoryType.BANGUMI_TV,
        ExploreCategoryType.BANGUMI_MOVIES,
        ExploreCategoryType.UPCOMING_MEDIA_TMDB -> this
    }

    private fun currentAniListSeasonAndYear(): Pair<String, Int> {
        val calendar = java.util.Calendar.getInstance()
        val season = when (calendar.get(java.util.Calendar.MONTH)) {
            java.util.Calendar.DECEMBER, java.util.Calendar.JANUARY, java.util.Calendar.FEBRUARY -> "WINTER"
            java.util.Calendar.MARCH, java.util.Calendar.APRIL, java.util.Calendar.MAY -> "SPRING"
            java.util.Calendar.JUNE, java.util.Calendar.JULY, java.util.Calendar.AUGUST -> "SUMMER"
            else -> "FALL"
        }
        return season to calendar.get(java.util.Calendar.YEAR)
    }

    fun loadData(forceRefresh: Boolean = false) {
        // Eğer forceRefresh değilse ve zaten data yüklüyse veya yükleniyorsa bir şey yapma
        if (!forceRefresh && (isDataLoaded || isLoading)) return

        if (forceRefresh && (selectedPlatform == ExplorePlatform.AniList || selectedPlatform == ExplorePlatform.ALL)) {
            resetAniListShelfState()
        }
        cancelLoads()
        if (forceRefresh) homeHeroArtworkLoader.retryFailures()
        if (selectedPlatform == ExplorePlatform.ALL) {
            loadAllSources(forceRefresh)
            return
        }
        val generation = requestGeneration
        loadJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            exploreErrorType = ExploreErrorType.None

            val platformSnapshot = selectedPlatform

            try {
                val payload = fetchPlatform(platformSnapshot)
                currentCoroutineContext().ensureActive()

                if (selectedPlatform == platformSnapshot && generation == requestGeneration) {
                    platformCache[platformSnapshot] = payload
                    loadedPlatforms.add(platformSnapshot)
                    applyPayload(payload)
                    isFallbackInProgress = false
                    isShowingCachedData = false
                    exploreErrorType = ExploreErrorType.None

                    // Cache successfully loaded payload in database
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(getApplication())
                            val gson = com.google.gson.Gson()
                            val json = gson.toJson(payload)
                            db.exploreCacheDao().insertCategory(
                                com.kitsugi.animelist.data.local.ExploreCacheEntity(
                                    categoryKey = exploreCacheKey(platformSnapshot),
                                    payloadJson = json,
                                    cachedAtMs = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (selectedPlatform == platformSnapshot && generation == requestGeneration) {
                    // Try to fall back to local offline database cache — Room + Gson parse
                    // ana thread'de yapılmaz (viewModelScope Main üzerinde koşar).
                    val cached = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(getApplication())
                            db.exploreCacheDao().getCategory(exploreCacheKey(platformSnapshot))
                        }.getOrNull()
                    }
                    currentCoroutineContext().ensureActive()
                    if (generation != requestGeneration || selectedPlatform != platformSnapshot) return@launch
                    if (cached != null) {
                        val payload = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            runCatching { com.google.gson.Gson().fromJson(cached.payloadJson, ExplorePayload::class.java) }.getOrNull()
                        }
                        if (payload != null) {
                            android.util.Log.d("ExploreViewModel", "Serving offline explore cache for ${platformSnapshot.name}")
                            isShowingCachedData = true
                            platformCache[platformSnapshot] = payload
                            loadedPlatforms.add(platformSnapshot)
                            applyPayload(payload)
                            isFallbackInProgress = false
                            isLoading = false
                            return@launch
                        }
                    }

                    errorMessage = e.message ?: getApplication<Application>().getString(R.string.explore_source_unavailable)
                    platformErrorCode = if (platformSnapshot == ExplorePlatform.ALL) {
                        PlatformErrorCode.COM_1020
                    } else {
                        PlatformFailureRegistry.reportAndResolve(platformSnapshot.label, e)
                    }
                    if (!isFallbackInProgress) {
                        isFallbackInProgress = true
                        val nextPlatform = if (platformSnapshot == ExplorePlatform.AniList) {
                            ExplorePlatform.MAL
                        } else if (platformSnapshot == ExplorePlatform.TMDB) {
                            ExplorePlatform.AniList
                        } else {
                            ExplorePlatform.AniList
                        }
                        selectPlatform(nextPlatform, isFallback = true)
                    } else {
                        // Tüm fallback'ler tükendi — platforma özgü hata tipini belirt
                        isFallbackInProgress = false
                        exploreErrorType = when (platformSnapshot) {
                            ExplorePlatform.ALL       -> ExploreErrorType.None
                            ExplorePlatform.TMDB      -> ExploreErrorType.TmdbError
                            ExplorePlatform.AniList   -> ExploreErrorType.AniListError
                            ExplorePlatform.MAL       -> ExploreErrorType.MalError
                            ExplorePlatform.SIMKL     -> ExploreErrorType.None
                            ExplorePlatform.KITSU     -> ExploreErrorType.None
                            ExplorePlatform.SHIKIMORI -> ExploreErrorType.None
                            ExplorePlatform.BANGUMI   -> ExploreErrorType.None
                        }
                    }
                }
            } finally {
                if (coroutineContext[Job] == loadJob) {
                    isLoading = false
                }
            }
        }
    }

    private suspend fun fetchPlatform(platform: ExplorePlatform, allowFallback: Boolean = true): ExplorePayload = when (platform) {
        ExplorePlatform.ALL -> error("Tümü bir API kaynağı değildir")
        ExplorePlatform.AniList -> loadAniListData(allowFallback)
        ExplorePlatform.MAL -> loadMalData()
        ExplorePlatform.TMDB -> loadTmdbData()
        ExplorePlatform.SIMKL -> loadSimklData()
        ExplorePlatform.KITSU -> loadKitsuData()
        ExplorePlatform.SHIKIMORI -> loadShikimoriData()
        ExplorePlatform.BANGUMI -> loadBangumiData()
    }

    private fun loadAllSources(forceRefresh: Boolean) {
        allSourceStates = ExplorePlatform.sources.associateWith { platform ->
            ExploreSourceState(
                payload = (platformCache[platform] ?: allSourceStates[platform]?.payload)?.forSource(platform),
                isLoading = true,
                isCached = platformCache[platform] == null && allSourceStates[platform]?.isCached == true
            )
        }
        allSourceStates[ExplorePlatform.AniList]?.payload?.let(::recordLoadedAniListShelves)
        isLoading = true
        ExplorePlatform.sources.forEach { launchSource(it, forceRefresh) }
    }

    /** Only the failed source is retried; successful sources stay visible. */
    fun retrySource(platform: ExplorePlatform) {
        if (selectedPlatform != ExplorePlatform.ALL || platform == ExplorePlatform.ALL ||
            allSourceStates[platform]?.isLoading == true) return
        if (platform == ExplorePlatform.AniList) {
            cancelAniListCategoryLoads()
            resetAniListShelfState()
        }
        allSourceStates = allSourceStates + (platform to
            (allSourceStates[platform] ?: ExploreSourceState()).copy(isLoading = true, error = null))
        isLoading = true
        launchSource(platform, forceRefresh = true)
    }

    /**
     * Keşfet disk önbelleği anahtarı.
     *
     * Aktif TMDB dilini ve [MediaTitleResolver.VERSION] sürümünü içerir; böylece
     * dil değişiminde ya da başlık çözümleme mantığı güncellendiğinde (ör. CJK
     * başlıkların düzeltildiği sürümde) eski hatalı kayıtlar servis edilmez.
     */
    private fun exploreCacheKey(platform: ExplorePlatform): String {
        val language = com.kitsugi.animelist.data.remote.TmdbApiClient.getActiveLanguage().lowercase()
        return "explore_platform_${platform.name}_${language}_v${com.kitsugi.animelist.utils.MediaTitleResolver.VERSION}"
    }

    private fun launchSource(platform: ExplorePlatform, forceRefresh: Boolean) {
        val generation = requestGeneration
        sourceJobs[platform]?.cancel()
        sourceJobs[platform] = viewModelScope.launch {
            val cached = allSourceStates[platform]?.payload
            val needsFetch = forceRefresh || allSourceStates[platform]?.isCached == true || cached?.hasCatalogContent() != true
            val state = loadExploreSource(
                platformLabel = platform.label,
                cached = cached,
                forceRefresh = needsFetch,
                // Ağdan dönen payload'un forSource kopyası + Room/Gson okuması ana
                // thread'de yapılmaz; viewModelScope Main üzerinde koştuğu için yükleme
                // sırasında her kaynak tamamlandığında UI donuyordu.
                fetch = {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        fetchPlatform(platform, allowFallback = false)
                    }.forSource(platform)
                },
                readOffline = {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(getApplication())
                        db.exploreCacheDao().getCategory(exploreCacheKey(platform))?.let {
                            com.google.gson.Gson().fromJson(it.payloadJson, ExplorePayload::class.java)?.forSource(platform)
                        }
                    }
                },
                context = getApplication()
            )
            currentCoroutineContext().ensureActive()
            if (generation != requestGeneration || selectedPlatform != ExplorePlatform.ALL) return@launch
            if (platform == ExplorePlatform.AniList) state.payload?.let(::recordLoadedAniListShelves)
            allSourceStates = allSourceStates + (platform to state)
            isLoading = allSourceStates.values.any { it.isLoading }
            // Never mark a failed/empty/offline response as a successful in-memory load.
            if (state.error == null && state.payload != null) {
                platformCache[platform] = state.payload
                loadedPlatforms.add(platform)
                if (needsFetch) {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(getApplication())
                            db.exploreCacheDao().insertCategory(
                                com.kitsugi.animelist.data.local.ExploreCacheEntity(
                                    categoryKey = exploreCacheKey(platform),
                                    payloadJson = com.google.gson.Gson().toJson(state.payload),
                                    cachedAtMs = System.currentTimeMillis()
                                )
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("ExploreViewModel", "Explore cache write failed", e)
                    }
                }
            }
        }
    }

    /**
     * enrichWithTmdb: Sadece source="simkl" olan öğeler için (kullanıcı watchlist'i)
     * TMDB'den zenginleştirilmiş poster/puan çeker.
     * TMDB trending öğeleri zaten doğru veriye sahip olduğu için burada işleme alınmaz.
     */
    private suspend fun enrichWithTmdb(item: JikanSearchResult): JikanSearchResult {
        // TMDB kaynaklı öğeler zaten tam veriye sahip — tekrar TMDB'ye gitme
        if (item.source == "tmdb") return item
        val tmdbId = item.tmdbId ?: return item
        val isMovie = item.type == com.kitsugi.animelist.model.MediaType.Movie
        val details = tmdbApiClient.fetchMediaDetail(tmdbId, isMovie) ?: return item

        val typeStr = when (item.type) {
            com.kitsugi.animelist.model.MediaType.Movie -> R.string.media_type_movie
            com.kitsugi.animelist.model.MediaType.TvShow -> R.string.media_type_tv
            com.kitsugi.animelist.model.MediaType.Anime -> R.string.media_type_anime
            else -> R.string.media_type_anime
        }

        val context = getApplication<Application>()
        val subtitleParts = buildList {
            add(context.getString(typeStr))
            val yearVal = item.year ?: details.year
            if (yearVal != null && yearVal > 0) add(yearVal.toString())
            addAll(details.genres.take(3))
        }
        val richSubtitle = subtitleParts.joinToString(", ")

        val ratingInt = (details.score ?: 0) / 10
        val finalScore = if (ratingInt > 0) ratingInt else null

        val backdropUrl = details.pictures.firstOrNull { it.contains("/w1280/") } ?: item.backdropUrl

        return item.copy(
            title = details.title?.takeIf { it.isNotBlank() } ?: item.title,
            subtitle = richSubtitle,
            score = finalScore ?: item.score,
            year = details.year ?: item.year,
            imageUrl = details.imageUrl ?: item.imageUrl,
            backdropUrl = backdropUrl
        )
    }

    /**
     * Ortak "Yakında Yayında" (geri sayımlı) verisi.
     *
     * Kesin bölüm yayın saatleri yalnızca AniList `airingSchedules` üzerinde bulunur;
     * Kitsu / Shikimori / Bangumi / Simkl gibi bu bilgiyi sağlamayan kaynaklarda da aynı
     * takvim verisi gösterilir. Kimlikler gerçek AniList/MAL kimliğiyle taşınır — takvim
     * sayfasının (`AiringEntry.toJikanSearchResult`) kullandığı sözleşmenin aynısı; karta
     * tıklanınca detay AniList rotasından çözülür (kaynağa özel sözde kimlik ÜRETİLMEZ).
     */
    private suspend fun fetchSharedAiringSoon(): List<JikanSearchResult> {
        val cal = com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient()
        val upcoming = runCatching { cal.fetchUpcomingSchedule(limit = 40) }.getOrNull() ?: emptyList()
        val nowSec = System.currentTimeMillis() / 1000L
        return upcoming
            .filter { it.airingAt > nowSec }
            .sortedBy { it.airingAt }
            .take(15)
            .map { e ->
                JikanSearchResult(
                    malId = e.malId ?: e.aniListId,
                    title = e.getDisplayTitle(),
                    subtitle = getApplication<Application>().getString(R.string.explore_episode_subtitle, e.episode),
                    type = MediaType.Anime,
                    total = null,
                    score = e.averageScore,
                    isAdult = e.isAdult,
                    imageUrl = e.coverUrl,
                    year = null,
                    source = "anilist",
                    realMalId = e.malId,
                    titleEnglish = e.titleEnglish,
                    titleJapanese = e.titleNative,
                    nextAiringEpisode = "${e.episode}|${e.airingAt}"
                )
            }
    }

    /**
     * TMDB tab için veri kaynağı — TMDB trending/popular içerikleri çeker.
     * Simkl API'si yalnızca giriş yapmış kullanıcının watchlist'ini çekmek için
     * (SimklSyncManager) çağrılır. Unauthenticated discovery tamamen TMDB'ye taşındı.
     */
    private suspend fun loadTmdbData(): ExplorePayload = supervisorScope {
        val context = getApplication<Application>().applicationContext
        val simklToken = ExternalAuthManager.getSimklToken(context)

        // ── TMDB Discovery: her zaman aktif ──────────────────────────────────────
        val trendingMoviesDeferred = async { runCatching { tmdbApiClient.getTrendingMovies() }.getOrDefault(emptyList()) }
        val trendingShowsDeferred  = async { runCatching { tmdbApiClient.getTrendingShows() }.getOrDefault(emptyList()) }
        val popularMoviesDeferred  = async { runCatching { tmdbApiClient.getPopularMovies() }.getOrDefault(emptyList()) }
        val trendingAllDeferred    = async { runCatching { tmdbApiClient.getTrendingAll() }.getOrDefault(emptyList()) }
        val popularShowsDeferred   = async { runCatching { tmdbApiClient.getPopularShows() }.getOrDefault(emptyList()) }
        val topRatedMoviesDeferred = async { runCatching { tmdbApiClient.getTopRatedMovies() }.getOrDefault(emptyList()) }
        val topRatedShowsDeferred  = async { runCatching { tmdbApiClient.getTopRatedShows() }.getOrDefault(emptyList()) }

        val trendingMediaDeferred  = async { runCatching { tmdbApiClient.getTrendingMedia() }.getOrDefault(emptyList()) }
        val popularMediaDeferred   = async { runCatching { tmdbApiClient.getPopularMedia() }.getOrDefault(emptyList()) }
        val upcomingMediaDeferred  = async { runCatching { tmdbApiClient.getUpcomingMedia() }.getOrDefault(emptyList()) }
        val topRatedAnimeDeferred  = async { runCatching { tmdbApiClient.getTopRatedAnime() }.getOrDefault(emptyList()) }
        val airingSoonDeferred = async {
            val calendarClient = com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient()
            val upcoming = runCatching { calendarClient.fetchUpcomingSchedule(limit = 40, preferredSource = "tmdb") }.getOrNull() ?: emptyList()
            val nowSeconds = System.currentTimeMillis() / 1000L
            upcoming
                .filter { it.airingAt > nowSeconds }
                .sortedBy { it.airingAt }
                .take(15)
                .map { entry ->
                    val finalType = if (entry.episode == 0) MediaType.Movie else MediaType.TvShow
                    JikanSearchResult(
                        malId = entry.aniListId,
                        title = entry.title,
                        subtitle = getApplication<Application>().getString(
                            if (entry.episode == 0) R.string.media_type_movie else R.string.media_type_tv
                        ),
                        type = finalType,
                        total = null,
                        score = entry.averageScore,
                        isAdult = entry.isAdult,
                        imageUrl = entry.coverUrl,
                        year = null,
                        source = "tmdb",
                        realMalId = null,
                        titleEnglish = entry.titleEnglish,
                        titleJapanese = entry.titleNative,
                        // "episode|epoch" — Film=0, prömiyer=Bölüm 1, bilinmeyen=-1
                        nextAiringEpisode = "${entry.episode}|${entry.airingAt}",
                        tmdbId = entry.aniListId
                    )
                }
        }

        val moviesList    = trendingMoviesDeferred.await()
        val showsList     = trendingShowsDeferred.await()
        val popularMovies = popularMoviesDeferred.await()
        val allTrending   = trendingAllDeferred.await()
        val popularShows  = popularShowsDeferred.await()
        val topRatedMovies = topRatedMoviesDeferred.await()
        val topRatedShows  = topRatedShowsDeferred.await()

        val trendingMediaList = trendingMediaDeferred.await()
        val popularMediaList  = popularMediaDeferred.await()
        val upcomingMediaList = upcomingMediaDeferred.await()
        val topRatedAnimeList = topRatedAnimeDeferred.await()
        val airingSoonList    = airingSoonDeferred.await()

        // ── Authenticated: Simkl watchlist (sadece giriş yapılmışsa) ────────────────
        val userMoviesDeferred = if (!simklToken.isNullOrBlank()) {
            async { SimklSyncManager.fetchSimklWatchlist(context, "movies") }
        } else null
        val userShowsDeferred = if (!simklToken.isNullOrBlank()) {
            async { SimklSyncManager.fetchSimklWatchlist(context, "shows") }
        } else null

        // Kullanıcı listelerini filtrele
        val userMovies = userMoviesDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()
        val userShows  = userShowsDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()

        val watchingStatus = context.getString(R.string.explore_simkl_status_watching)
        val plannedStatus = context.getString(R.string.explore_simkl_status_planned)
        var continueMovies = localizedSimklStatusItems(userMovies, "İzleniyor", watchingStatus)
        var plannedMovies = localizedSimklStatusItems(userMovies, "Planlandı", plannedStatus)
        var continueSeries = localizedSimklStatusItems(userShows, "İzleniyor", watchingStatus)
        var plannedSeries = localizedSimklStatusItems(userShows, "Planlandı", plannedStatus)

        if (tmdbEnabledState && tmdbEnrichContinueWatchingState) {
            // Sadece "İzlemeye Devam Et" listelerini paralel olarak zenginleştir (maksimum ilk 8 öğe)
            val moviesToEnrich = continueMovies.take(8)
            val seriesToEnrich = continueSeries.take(8)
            val movieJobs = moviesToEnrich.map { item ->
                async { enrichWithTmdb(item) }
            }
            val seriesJobs = seriesToEnrich.map { item ->
                async { enrichWithTmdb(item) }
            }
            val enrichedMovies = movieJobs.mapIndexed { idx, job ->
                runCatching { job.await() }.getOrDefault(moviesToEnrich[idx])
            }
            val enrichedSeries = seriesJobs.mapIndexed { idx, job ->
                runCatching { job.await() }.getOrDefault(seriesToEnrich[idx])
            }
            continueMovies = enrichedMovies + continueMovies.drop(8)
            continueSeries = enrichedSeries + continueSeries.drop(8)
        }

        ExplorePayload(
            topAnime      = allTrending,       // Trend Her Şey (film + dizi karışık)
            airingAnime   = showsList,          // Trend Diziler
            upcomingAnime = popularMovies,      // Popüler Filmler
            topManga      = popularShows,       // Popüler Diziler
            publishingManga = topRatedMovies,   // En Yüksek Puanlı Filmler
            trendingAnime = trendingMediaList,  // Trend Animeler
            movieAnime    = moviesList,         // Trend Filmler
            seasonalAnime = topRatedShows,      // En Yüksek Puanlı Diziler
            newlyAddedAnime = popularMediaList, // Popüler Animeler
            trendingManga = topRatedAnimeList,  // En Yüksek Puanlı Animeler
            upcomingMediaTmdb = upcomingMediaList, // Yakında Yayında (Medya) — ayrı field
            simklContinueMovies = continueMovies,
            simklPlannedMovies = plannedMovies,
            simklContinueSeries = continueSeries,
            simklPlannedSeries = plannedSeries,
            airingSoonAnime = airingSoonList
        )
    }

    private suspend fun loadKitsuData(): ExplorePayload = supervisorScope {
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH)
        val year = cal.get(java.util.Calendar.YEAR)
        val currentSeason = when (month) {
            0, 1, 11 -> "winter"
            2, 3, 4 -> "spring"
            5, 6, 7 -> "summer"
            else -> "fall"
        }

        val topAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.topAnime(20) }.getOrDefault(emptyList()) }
        val topRatedAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.topRatedAnime(20) }.getOrDefault(emptyList()) }
        val topRatedMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.topRatedManga(20) }.getOrDefault(emptyList()) }
        val trendingAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.trendingAnime(20) }.getOrDefault(emptyList()) }
        val seasonalAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.seasonalAnime(currentSeason, year, 20) }.getOrDefault(emptyList()) }
        val airingAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.airingAnime(20) }.getOrDefault(emptyList()) }
        val upcomingAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.upcomingAnime(20) }.getOrDefault(emptyList()) }
        val newlyAddedAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.newlyAddedAnime(20) }.getOrDefault(emptyList()) }
        val movieAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.movieAnime(20) }.getOrDefault(emptyList()) }
        val topMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.topManga(20) }.getOrDefault(emptyList()) }
        val publishingMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.publishingManga(20) }.getOrDefault(emptyList()) }
        val trendingMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.trendingManga(20) }.getOrDefault(emptyList()) }
        val newlyAddedMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.newlyAddedManga(20) }.getOrDefault(emptyList()) }
        val manhwaManhuaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.manhwaManhua(20) }.getOrDefault(emptyList()) }
        val novelsDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.novelsShelf(20) }.getOrDefault(emptyList()) }
        // Kitsu bölüm yayın saati sağlamaz → geri sayımlı şerit ortak takvim verisinden beslenir
        val airingSoonDeferred = async { fetchSharedAiringSoon() }

        // Vitrin arka planları veri yolunu BLOKE ETMEZ: eksik backdrop'lar UI tarafında
        // ExploreViewModel.prepareHomeHeroes() ile asenkron çözülür ve hero anında güncellenir.
        // (Eski davranış: payload dönmeden önce 5 × TMDB araması bekleniyordu.)
        val enrichedTopAnime = runCatching { topAnimeDeferred.await() }.getOrDefault(emptyList())

        ExplorePayload(
            topAnime = enrichedTopAnime,
            airingAnime = airingAnimeDeferred.await(),
            upcomingAnime = upcomingAnimeDeferred.await(),
            topManga = topMangaDeferred.await(),
            publishingManga = publishingMangaDeferred.await(),
            trendingManga = trendingMangaDeferred.await(),
            newlyAddedAnime = newlyAddedAnimeDeferred.await(),
            newlyAddedManga = newlyAddedMangaDeferred.await(),
            trendingAnime = trendingAnimeDeferred.await(),
            movieAnime = movieAnimeDeferred.await(),
            seasonalAnime = seasonalAnimeDeferred.await(),
            topRatedAnime = topRatedAnimeDeferred.await(),
            topRatedManga = topRatedMangaDeferred.await(),
            manhwaManhua = manhwaManhuaDeferred.await(),
            novels = novelsDeferred.await(),
            airingSoonAnime = runCatching { airingSoonDeferred.await() }.getOrDefault(emptyList())
        )
    }

    /**
     * Bangumi (bgm.tv) Keşfet verisi.
     *
     * Tüm şeritler `POST /v0/search/subjects` + `GET /v0/subjects` uçlarından gelir ve
     * token gerektirmez (anonim okuma serbest); bağlı bir hesap varsa token eklenir ki
     * hız sınırı kullanıcı bazında sayılsın. Yetişkin içerik `nsfw` alanından süzülür.
     */
    private suspend fun loadBangumiData(): ExplorePayload = supervisorScope {
        val context = getApplication<Application>().applicationContext
        val bangumi = com.kitsugi.animelist.data.remote.KitsugiBangumiClient

        /**
         * Tek bir şeridin tüm vitrini kilitlemesini engeller.
         *
         * `supervisorScope` bir şerit hata verse bile diğerlerini sürdürür; ancak **asılı**
         * bir şerit (yanıtı gelmeyen istek) tüm `ExplorePayload`'un dönmesini geciktirir ve
         * kullanıcı bu sırada boş/iskelet ekran görür. Her şerit kendi süre tavanıyla
         * sınırlandığında vitrin en geç [BANGUMI_SHELF_TIMEOUT_MS] içinde çizilir.
         */
        suspend fun shelf(
            block: suspend () -> List<com.kitsugi.animelist.data.remote.JikanSearchResult>
        ): List<com.kitsugi.animelist.data.remote.JikanSearchResult> =
            withTimeoutOrNull(BANGUMI_SHELF_TIMEOUT_MS) {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()

        val topAnimeDeferred = async { shelf { bangumi.topAnime(20, context) } }
        val trendingAnimeDeferred = async { shelf { bangumi.trendingAnime(20, context) } }
        val topRatedAnimeDeferred = async { shelf { bangumi.topRatedAnime(20, context) } }
        val seasonalAnimeDeferred = async { shelf { bangumi.seasonalAnime(20, context) } }
        val airingAnimeDeferred = async { shelf { bangumi.airingAnime(20, context) } }
        val upcomingAnimeDeferred = async { shelf { bangumi.upcomingAnime(20, context) } }
        val movieAnimeDeferred = async { shelf { bangumi.movieAnime(20, context) } }
        val bangumiTvShowsDeferred = async { shelf { bangumi.realTvShows(20, context) } }
        val bangumiMoviesDeferred = async { shelf { bangumi.realMovies(20, context) } }
        val newlyAddedAnimeDeferred = async { shelf { bangumi.newlyAddedAnime(20, context) } }
        val topMangaDeferred = async { shelf { bangumi.topManga(20, context) } }
        val publishingMangaDeferred = async { shelf { bangumi.publishingManga(20, context) } }
        val trendingMangaDeferred = async { shelf { bangumi.trendingManga(20, context) } }
        val topRatedMangaDeferred = async { shelf { bangumi.topRatedManga(20, context) } }
        val newlyAddedMangaDeferred = async { shelf { bangumi.newlyAddedManga(20, context) } }
        val novelsDeferred = async { shelf { bangumi.novelsShelf(20, context) } }
        // Bangumi bölüm yayın saati sağlamaz → geri sayımlı şerit ortak takvim verisinden beslenir
        val airingSoonDeferred = async {
            withTimeoutOrNull(BANGUMI_SHELF_TIMEOUT_MS) { fetchSharedAiringSoon() }.orEmpty()
        }

        // Vitrin arka planları veri yolunu BLOKE ETMEZ — prepareHomeHeroes() asenkron çözer.
        val enrichedTopAnime = topAnimeDeferred.await()

        ExplorePayload(
            topAnime = enrichedTopAnime,
            airingAnime = airingAnimeDeferred.await(),
            upcomingAnime = upcomingAnimeDeferred.await(),
            topManga = topMangaDeferred.await(),
            publishingManga = publishingMangaDeferred.await(),
            trendingAnime = trendingAnimeDeferred.await(),
            movieAnime = movieAnimeDeferred.await(),
            bangumiTvShows = bangumiTvShowsDeferred.await(),
            bangumiMovies = bangumiMoviesDeferred.await(),
            seasonalAnime = seasonalAnimeDeferred.await(),
            trendingManga = trendingMangaDeferred.await(),
            newlyAddedAnime = newlyAddedAnimeDeferred.await(),
            topRatedAnime = topRatedAnimeDeferred.await(),
            topRatedManga = topRatedMangaDeferred.await(),
            newlyAddedManga = publishingMangaDeferred.await(),
            novels = novelsDeferred.await(),
            airingSoonAnime = airingSoonDeferred.await()
        )
    }

    private suspend fun loadShikimoriData(): ExplorePayload = supervisorScope {
        // Shikimori excludes Rx titles by default. Ask for uncensored results only when
        // the user enabled adult content; each returned item is still rated individually.
        val censored = !showAdultContentState
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH)
        val year = cal.get(java.util.Calendar.YEAR)
        val seasonPrefix = when (month) {
            0, 1, 11 -> "winter"
            2, 3, 4 -> "spring"
            5, 6, 7 -> "summer"
            else -> "fall"
        }
        val currentShikiSeason = "${seasonPrefix}_${year}"

        val topAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.topAnime(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        // Shikimori's catalogue id order is creation order; preserve both new anime/manga shelves.
        val newlyAddedAnimeDeferred = async {
            com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
                MediaType.Anime, order = "id_desc", limit = 20, censored = censored
            )
        }
        val newlyAddedMangaDeferred = async {
            com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
                MediaType.Manga, order = "id_desc", limit = 20, censored = censored
            )
        }
        val trendingAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.trendingAnime(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val seasonalAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.seasonalAnime(season = currentShikiSeason, limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val movieAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.movieAnime(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val airingAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.airingAnime(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val upcomingAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
                    com.kitsugi.animelist.model.MediaType.Anime,
                    statuses = listOf("anons"),
                    order = "popularity",
                    limit = 20,
                    censored = censored
                )
            }.getOrDefault(emptyList())
        }
        val topMangaDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.topManga(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val publishingMangaDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.publishingManga(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val trendingMangaDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
                    com.kitsugi.animelist.model.MediaType.Manga,
                    order = "popularity",
                    limit = 20,
                    censored = censored
                )
            }.getOrDefault(emptyList())
        }
        // Shikimori'nin yerel ek tür rafları: manhwa/manhua ve light novel & romanlar.
        val manhwaManhuaDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
                    com.kitsugi.animelist.model.MediaType.Manga,
                    kinds = listOf("manhwa", "manhua"),
                    order = "popularity",
                    limit = 20,
                    censored = censored
                )
            }.getOrDefault(emptyList())
        }
        val novelsDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
                    com.kitsugi.animelist.model.MediaType.Manga,
                    kinds = listOf("light_novel", "novel"),
                    order = "popularity",
                    limit = 20,
                    censored = censored
                )
            }.getOrDefault(emptyList())
        }
        // Shikimori bölüm yayın saati sağlamaz → geri sayımlı şerit ortak takvim verisinden beslenir
        val airingSoonDeferred = async { fetchSharedAiringSoon() }

        // Vitrin arka planları veri yolunu BLOKE ETMEZ — prepareHomeHeroes() asenkron çözer.
        val enrichedTopAnime = runCatching { topAnimeDeferred.await() }.getOrDefault(emptyList())

        ExplorePayload(
            topAnime = enrichedTopAnime,
            airingAnime = airingAnimeDeferred.await(),
            upcomingAnime = upcomingAnimeDeferred.await(),
            topManga = topMangaDeferred.await(),
            publishingManga = publishingMangaDeferred.await(),
            trendingManga = trendingMangaDeferred.await(),
            trendingAnime = trendingAnimeDeferred.await(),
            movieAnime = movieAnimeDeferred.await(),
            seasonalAnime = seasonalAnimeDeferred.await(),
            newlyAddedAnime = newlyAddedAnimeDeferred.await(),
            newlyAddedManga = newlyAddedMangaDeferred.await(),
            manhwaManhua = manhwaManhuaDeferred.await(),
            novels = novelsDeferred.await(),
            airingSoonAnime = runCatching { airingSoonDeferred.await() }.getOrDefault(emptyList())
        )
    }

    private suspend fun loadSimklData(): ExplorePayload = supervisorScope {
        val context = getApplication<Application>().applicationContext
        val simkl = com.kitsugi.animelist.data.remote.SimklApiClient()

        val allTrendingDeferred = async { runCatching { simkl.getBestMedia("tv/trending", com.kitsugi.animelist.model.MediaType.TvShow, 20) }.getOrDefault(emptyList()) }
        val airingTvDeferred = async { runCatching { simkl.getBestMedia("tv/best/airing", com.kitsugi.animelist.model.MediaType.TvShow, 20) }.getOrDefault(emptyList()) }
        val trendingMoviesDeferred = async { runCatching { simkl.getBestMedia("movies/trending", com.kitsugi.animelist.model.MediaType.Movie, 20) }.getOrDefault(emptyList()) }
        val topTvDeferred = async { runCatching { simkl.getBestMedia("tv/best/all-time", com.kitsugi.animelist.model.MediaType.TvShow, 20) }.getOrDefault(emptyList()) }
        val popularMoviesDeferred = async { runCatching { simkl.getBestMedia("movies/recent", com.kitsugi.animelist.model.MediaType.Movie, 20) }.getOrDefault(emptyList()) }
        val trendingAnimeDeferred = async { runCatching { simkl.getBestMedia("anime/trending", com.kitsugi.animelist.model.MediaType.Anime, 20) }.getOrDefault(emptyList()) }
        val topAnimeDeferred = async { runCatching { simkl.getBestMedia("anime/best/all-time", com.kitsugi.animelist.model.MediaType.Anime, 20) }.getOrDefault(emptyList()) }
        val airingAnimeDeferred = async { runCatching { simkl.getBestMedia("anime/best/airing", com.kitsugi.animelist.model.MediaType.Anime, 20) }.getOrDefault(emptyList()) }
        val upcomingAnimeDeferred = async { runCatching { simkl.getBestMedia("anime/best/upcoming", com.kitsugi.animelist.model.MediaType.Anime, 20) }.getOrDefault(emptyList()) }
        // Simkl "anime/best/upcoming" bölüm yayın saati taşımaz (geri sayım yok) →
        // "Yakında Yayında" şeridi ortak takvim verisiyle gerçek geri sayım gösterir
        val airingSoonDeferred = async { fetchSharedAiringSoon() }

        val simklToken = ExternalAuthManager.getSimklToken(context)
        val userMoviesDeferred = if (!simklToken.isNullOrBlank()) {
            async { SimklSyncManager.fetchSimklWatchlist(context, "movies") }
        } else null
        val userShowsDeferred = if (!simklToken.isNullOrBlank()) {
            async { SimklSyncManager.fetchSimklWatchlist(context, "shows") }
        } else null

        val userMovies = userMoviesDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()
        val userShows  = userShowsDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()

        val watchingStatus = context.getString(R.string.explore_simkl_status_watching)
        val plannedStatus = context.getString(R.string.explore_simkl_status_planned)
        val continueMovies = localizedSimklStatusItems(userMovies, "İzleniyor", watchingStatus)
        val plannedMovies = localizedSimklStatusItems(userMovies, "Planlandı", plannedStatus)
        val continueSeries = localizedSimklStatusItems(userShows, "İzleniyor", watchingStatus)
        val plannedSeries = localizedSimklStatusItems(userShows, "Planlandı", plannedStatus)

        val topAnimeList = topAnimeDeferred.await()
        val airingAnimeList = airingAnimeDeferred.await()
        val upcomingAnimeList = upcomingAnimeDeferred.await()
        val trendingAnimeList = trendingAnimeDeferred.await()
        val allTrendingList = allTrendingDeferred.await()
        val airingTvList = airingTvDeferred.await()
        val trendingMoviesList = trendingMoviesDeferred.await()
        val topTvList = topTvDeferred.await()
        val popularMoviesList = popularMoviesDeferred.await()

        ExplorePayload(
            topAnime = allTrendingList,            // Trend Her Şey
            airingAnime = airingTvList,            // Trend Diziler
            upcomingAnime = popularMoviesList,     // Popüler Filmler
            topManga = topTvList,                  // Popüler Diziler
            publishingManga = trendingMoviesList,  // En Yüksek Puanlı / Trend Filmler
            trendingAnime = trendingAnimeList,     // Trend Animeler
            movieAnime = trendingMoviesList,       // Trend Filmler
            seasonalAnime = topTvList,             // En Yüksek Puanlı Diziler
            newlyAddedAnime = topAnimeList,        // Popüler Animeler
            trendingManga = airingAnimeList,       // En Yüksek Puanlı Animeler
            upcomingMediaTmdb = upcomingAnimeList, // Yakında Yayında (prömiyerler — "Tümü" sayfası)
            simklContinueMovies = continueMovies,
            simklPlannedMovies = plannedMovies,
            simklContinueSeries = continueSeries,
            simklPlannedSeries = plannedSeries,
            airingSoonAnime = runCatching { airingSoonDeferred.await() }.getOrDefault(emptyList())
        )
    }

    private suspend fun loadMalData(): ExplorePayload = supervisorScope {
        val showAdult = showAdultContentState
        val topAnimeDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.topAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val airingAnimeDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.airingAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val upcomingAnimeDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.upcomingAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val trendingAnimeDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.trendingAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val movieAnimeDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.movieAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val seasonalAnimeDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.seasonalAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val topMangaDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.topManga(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val publishingMangaDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.publishingManga(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val trendingMangaDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.trendingManga(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val newlyAddedAnimeDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.newlyAddedAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val newlyAddedMangaDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.newlyAddedManga(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        // MAL'in yerel ek tür rafları (Bangumi REAL raflarının MAL karşılığı).
        val manhwaManhuaDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.manhwaManhua(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val novelsDeferred = async { withTimeoutOrNull(20_000L) { runCatching { apiClient.novelsShelf(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }

        val rawTopAnime = runCatching { topAnimeDeferred.await() }.getOrDefault(emptyList())

        // Vitrin arka planları veri yolunu BLOKE ETMEZ — eksik backdrop'lar UI tarafında
        // prepareHomeHeroes() ile TMDB'den asenkron çözülür (önbellekli).
        val enrichedTopAnime = rawTopAnime

        val airingSoonDeferred = async {
            val calendarClient = com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient()
            val upcoming = runCatching { calendarClient.fetchUpcomingSchedule(limit = 40) }.getOrNull() ?: emptyList()
            val nowSeconds = System.currentTimeMillis() / 1000L
            upcoming
                .filter { it.airingAt > nowSeconds && it.malId != null }
                .sortedBy { it.airingAt }
                .take(15)
                .map { entry ->
                    JikanSearchResult(
                        malId = entry.malId!!,
                        title = entry.getDisplayTitle(),
                        subtitle = getApplication<Application>().getString(R.string.explore_episode_subtitle, entry.episode),
                        type = MediaType.Anime,
                        total = null,
                        score = entry.averageScore,
                        isAdult = entry.isAdult,
                        imageUrl = entry.coverUrl,
                        year = null,
                        source = "mal",
                        realMalId = entry.malId,
                        titleEnglish = entry.titleEnglish,
                        titleJapanese = entry.titleNative,
                        nextAiringEpisode = "${entry.episode}|${entry.airingAt}"
                    )
                }
        }

        ExplorePayload(
            topAnime = enrichedTopAnime,
            airingAnime = runCatching { airingAnimeDeferred.await() }.getOrDefault(emptyList()),
            upcomingAnime = runCatching { upcomingAnimeDeferred.await() }.getOrDefault(emptyList()),
            topManga = runCatching { topMangaDeferred.await() }.getOrDefault(emptyList()),
            publishingManga = runCatching { publishingMangaDeferred.await() }.getOrDefault(emptyList()),
            trendingManga = runCatching { trendingMangaDeferred.await() }.getOrDefault(emptyList()),
            newlyAddedAnime = runCatching { newlyAddedAnimeDeferred.await() }.getOrDefault(emptyList()),
            newlyAddedManga = runCatching { newlyAddedMangaDeferred.await() }.getOrDefault(emptyList()),
            trendingAnime = runCatching { trendingAnimeDeferred.await() }.getOrDefault(emptyList()),
            movieAnime = runCatching { movieAnimeDeferred.await() }.getOrDefault(emptyList()),
            seasonalAnime = runCatching { seasonalAnimeDeferred.await() }.getOrDefault(emptyList()),
            airingSoonAnime = runCatching { airingSoonDeferred.await() }.getOrDefault(emptyList()),
            manhwaManhua = runCatching { manhwaManhuaDeferred.await() }.getOrDefault(emptyList()),
            novels = runCatching { novelsDeferred.await() }.getOrDefault(emptyList())
        )
    }

    private suspend fun loadAniListData(allowFallback: Boolean = true): ExplorePayload = supervisorScope {
        val showAdult = showAdultContentState
        val initialCategories = ANILIST_INITIAL_EXPLORE_CATEGORIES
        val (season, year) = currentAniListSeasonAndYear()
        val requests = aniListExploreShelfRequests(initialCategories, season, year)
        aniListLoadingCategories = aniListLoadingCategories + initialCategories

        val shelves = try {
            apiClient.aniListExploreShelves(requests.map { it.spec }, showAdult)
        } catch (e: CancellationException) {
            aniListLoadingCategories = aniListLoadingCategories - initialCategories.toSet()
            throw e
        } catch (e: Throwable) {
            markAniListCategoriesFailed(initialCategories)
            if (allowFallback) {
                android.util.Log.w("ExploreViewModel", "AniList initial shelves unavailable; using Kitsu fallback", e)
                return@supervisorScope loadKitsuData()
            }
            throw e
        }

        val loaded = initialCategories.filter { category ->
            val categoryRequests = requests.filter { it.category == category }
            categoryRequests.isNotEmpty() && categoryRequests.all { shelves.containsKey(it.spec.alias) }
        }
        val failed = initialCategories - loaded.toSet()
        val results = loaded.associateWith { category ->
            requests.asSequence()
                .filter { it.category == category }
                .flatMap { shelves[it.spec.alias].orEmpty().asSequence() }
                .distinctBy { it.exploreIdentity() }
                .toList()
        }
        if (loaded.isNotEmpty()) {
            Companion.loadedAniListCategories.addAll(loaded)
            Companion.failedAniListCategories.removeAll(loaded.toSet())
            aniListLoadedCategories = aniListLoadedCategories + loaded
            aniListFailedCategories = aniListFailedCategories - loaded.toSet()
        }
        if (failed.isNotEmpty()) markAniListCategoriesFailed(failed)
        aniListLoadingCategories = aniListLoadingCategories - initialCategories.toSet()

        if (results.values.none { it.isNotEmpty() }) {
            val emptyFailure = IllegalStateException("AniList keşfet rafları boş döndü")
            if (allowFallback) return@supervisorScope loadKitsuData()
            throw emptyFailure
        }

        val airingSoon = runCatching { fetchSharedAiringSoon() }.getOrDefault(emptyList())
        ExplorePayload(
            topAnime = results[ExploreCategoryType.TOP_ANIME].orEmpty(),
            airingAnime = results[ExploreCategoryType.AIRING_ANIME].orEmpty(),
            upcomingAnime = results[ExploreCategoryType.UPCOMING_ANIME].orEmpty(),
            topManga = emptyList(),
            publishingManga = emptyList(),
            trendingAnime = results[ExploreCategoryType.TRENDING_ANIME].orEmpty(),
            movieAnime = results[ExploreCategoryType.MOVIE_ANIME].orEmpty(),
            seasonalAnime = emptyList(),
            airingSoonAnime = airingSoon,
            topRatedAnime = results[ExploreCategoryType.TOP_RATED_ANIME].orEmpty(),
            // Remaining supported AniList shelves are fetched only when their row is approached.
            topRatedManga = null,
            trendingManga = emptyList(),
            newlyAddedAnime = emptyList(),
            newlyAddedManga = emptyList(),
            manhwaManhua = null,
            novels = null
        )
    }

    /** Resolve/verify artwork BEFORE selection. Recomposition and logo loads never reshuffle it. */
    fun prepareHomeHeroes(
        sections: List<ExploreSourceSection>,
        airingSoon: List<JikanSearchResult>,
        platform: ExplorePlatform
    ) {
        heroPreparationJob?.cancel()
        val preparation = ++heroPreparationGeneration
        val generation = requestGeneration
        val seed = heroRefreshSeed
        isHeroLoading = true
        heroPreparationJob = viewModelScope.launch {
            try {
                val prepared = resolveHomeHeroItems(
                    sections = homeHeroSections(sections, airingSoon),
                    itemsPerSection = if (platform == ExplorePlatform.ALL) HERO_PER_SECTION_ALL else HERO_PER_SECTION_SINGLE,
                    seed = seed,
                    resolve = homeHeroArtworkLoader::prepare
                )
                currentCoroutineContext().ensureActive()
                if (preparation == heroPreparationGeneration && generation == requestGeneration && selectedPlatform == platform) {
                    homeHeroItems = if (preparedHeroSeed == seed) {
                        com.kitsugi.animelist.ui.components.stableHomeHeroOrder(homeHeroItems, prepared)
                    } else prepared
                    preparedHeroSeed = seed
                }
            } finally {
                if (preparation == heroPreparationGeneration) isHeroLoading = false
            }
        }
    }

    companion object {
        val platformCache = java.util.concurrent.ConcurrentHashMap<ExplorePlatform, ExplorePayload>()
        val loadedPlatforms = java.util.Collections.synchronizedSet(mutableSetOf<ExplorePlatform>())
        internal val loadedAniListCategories = java.util.Collections.synchronizedSet(mutableSetOf<ExploreCategoryType>())
        internal val failedAniListCategories = java.util.Collections.synchronizedSet(mutableSetOf<ExploreCategoryType>())
        
        private val isPrefetchStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile
        var prefetchJob: kotlinx.coroutines.Job? = null

        fun prefetch(context: android.content.Context) {
            if (!isPrefetchStarted.compareAndSet(false, true)) return
            
            val app = context.applicationContext as android.app.Application
            
            prefetchJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                try {
                    val settingsDataStore = SettingsDataStore(app)
                    val settings = settingsDataStore.settingsFlow.first()
                    
                    val showAdult = settings.showAdultContent
                    val tmdbEnabled = settings.tmdbEnabled
                    val tmdbEnrich = settings.tmdbEnrichContinueWatching
                    val simklToken = ExternalAuthManager.getSimklToken(app)
                    
                    val tmdbApiClient = TmdbApiClient(userApiKey = settings.tmdbUserApiKey)

                    // Kullanıcının kalıcı Keşfet kaynağı TMDB değilse bu pahalı ön-yükleme
                    // boşa bant genişliği tüketir; kendi platformunun verisini açılışta
                    // kendisi çeker. Sadece TMDB / Tümü seçiliyse önbelleği dolduruyoruz.
                    val lastPlatformName = settingsDataStore.lastExplorePlatformFlow.first()
                    val lastPlatform = ExplorePlatform.entries.firstOrNull { it.name == lastPlatformName }
                        ?: ExplorePlatform.ALL
                    if (lastPlatform != ExplorePlatform.TMDB && lastPlatform != ExplorePlatform.ALL) {
                        android.util.Log.d("ExplorePrefetch", "Son seçili kaynak $lastPlatform — TMDB önbelleği atlanıyor")
                        return@launch
                    }

                    supervisorScope {
                        // Startup prefetch only fetches TMDB (default platform) to minimize latency and bandwidth
                        val tmdbPayload = runCatching {
                            val trendingMoviesDeferred = async { runCatching { tmdbApiClient.getTrendingMovies() }.getOrDefault(emptyList()) }
                            val trendingShowsDeferred  = async { runCatching { tmdbApiClient.getTrendingShows() }.getOrDefault(emptyList()) }
                            val popularMoviesDeferred  = async { runCatching { tmdbApiClient.getPopularMovies() }.getOrDefault(emptyList()) }
                            val trendingAllDeferred    = async { runCatching { tmdbApiClient.getTrendingAll() }.getOrDefault(emptyList()) }
                            val popularShowsDeferred   = async { runCatching { tmdbApiClient.getPopularShows() }.getOrDefault(emptyList()) }
                            val topRatedMoviesDeferred = async { runCatching { tmdbApiClient.getTopRatedMovies() }.getOrDefault(emptyList()) }
                            val topRatedShowsDeferred  = async { runCatching { tmdbApiClient.getTopRatedShows() }.getOrDefault(emptyList()) }
                            val trendingMediaDeferred  = async { runCatching { tmdbApiClient.getTrendingMedia() }.getOrDefault(emptyList()) }
                            val popularMediaDeferred   = async { runCatching { tmdbApiClient.getPopularMedia() }.getOrDefault(emptyList()) }
                            val upcomingMediaDeferred  = async { runCatching { tmdbApiClient.getUpcomingMedia() }.getOrDefault(emptyList()) }
                            val topRatedAnimeDeferred  = async { runCatching { tmdbApiClient.getTopRatedAnime() }.getOrDefault(emptyList()) }
                            val airingSoonDeferred = async {
                                val calendarClient = com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient()
                                val upcoming = runCatching { calendarClient.fetchUpcomingSchedule(limit = 40, preferredSource = "tmdb") }.getOrNull() ?: emptyList()
                                val nowSeconds = System.currentTimeMillis() / 1000L
                                upcoming
                                    .filter { it.airingAt > nowSeconds }
                                    .sortedBy { it.airingAt }
                                    .take(15)
                                    .map { entry ->
                                        val finalType = if (entry.episode == 0) MediaType.Movie else MediaType.TvShow
                                        JikanSearchResult(
                                            malId = entry.aniListId,
                                            title = entry.title,
                                            subtitle = if (entry.episode == 0) {
                                                app.getString(R.string.media_type_movie)
                                            } else {
                                                app.getString(R.string.explore_episode_subtitle, entry.episode)
                                            },
                                            type = finalType,
                                            total = null,
                                            score = entry.averageScore,
                                            isAdult = entry.isAdult,
                                            imageUrl = entry.coverUrl,
                                            year = null,
                                            source = "tmdb",
                                            realMalId = null,
                                            titleEnglish = entry.titleEnglish,
                                            titleJapanese = entry.titleNative,
                                            nextAiringEpisode = "${entry.episode}|${entry.airingAt}",
                                            tmdbId = entry.aniListId
                                        )
                                    }
                            }

                            val moviesList    = trendingMoviesDeferred.await()
                            val showsList     = trendingShowsDeferred.await()
                            val popularMovies = popularMoviesDeferred.await()
                            val allTrending   = trendingAllDeferred.await()
                            val popularShows  = popularShowsDeferred.await()
                            val topRatedMovies = topRatedMoviesDeferred.await()
                            val topRatedShows  = topRatedShowsDeferred.await()
                            val trendingMediaList = trendingMediaDeferred.await()
                            val popularMediaList  = popularMediaDeferred.await()
                            val upcomingMediaList = upcomingMediaDeferred.await()
                            val topRatedAnimeList = topRatedAnimeDeferred.await()
                            val airingSoonList    = airingSoonDeferred.await()

                            val userMoviesDeferred = if (!simklToken.isNullOrBlank()) {
                                async { SimklSyncManager.fetchSimklWatchlist(app, "movies") }
                            } else null
                            val userShowsDeferred = if (!simklToken.isNullOrBlank()) {
                                async { SimklSyncManager.fetchSimklWatchlist(app, "shows") }
                            } else null

                            val userMovies = userMoviesDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()
                            val userShows  = userShowsDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()

                            val watchingStatus = app.getString(R.string.explore_simkl_status_watching)
                            val plannedStatus = app.getString(R.string.explore_simkl_status_planned)
                            var continueMovies = localizedSimklStatusItems(userMovies, "İzleniyor", watchingStatus)
                            val plannedMovies = localizedSimklStatusItems(userMovies, "Planlandı", plannedStatus)
                            var continueSeries = localizedSimklStatusItems(userShows, "İzleniyor", watchingStatus)
                            val plannedSeries = localizedSimklStatusItems(userShows, "Planlandı", plannedStatus)

                            if (tmdbEnabled && tmdbEnrich) {
                                val moviesToEnrich = continueMovies.take(8)
                                val seriesToEnrich = continueSeries.take(8)
                                val movieJobs = moviesToEnrich.map { item ->
                                    async {
                                        if (item.source != "tmdb") {
                                            val tmdbId = item.tmdbId
                                            if (tmdbId != null) {
                                                val isMovie = item.type == MediaType.Movie
                                                val details = tmdbApiClient.fetchMediaDetail(tmdbId, isMovie)
                                                if (details != null) {
                                                    val typeStr = when (item.type) {
                                                        MediaType.Movie -> R.string.media_type_movie
                                                        MediaType.TvShow -> R.string.media_type_tv
                                                        MediaType.Anime -> R.string.media_type_anime
                                                        else -> R.string.media_type_anime
                                                    }
                                                    val subtitleParts = buildList {
                                                        add(app.getString(typeStr))
                                                        val yearVal = item.year ?: details.year
                                                        if (yearVal != null && yearVal > 0) add(yearVal.toString())
                                                        addAll(details.genres.take(3))
                                                    }
                                                    val ratingInt = (details.score ?: 0) / 10
                                                    val finalScore = if (ratingInt > 0) ratingInt else null
                                                    val backdropUrl = details.pictures.firstOrNull { it.contains("/w1280/") } ?: item.backdropUrl
                                                    item.copy(
                                                        title = details.title?.takeIf { it.isNotBlank() } ?: item.title,
                                                        subtitle = subtitleParts.joinToString(", "),
                                                        score = finalScore ?: item.score,
                                                        year = details.year ?: item.year,
                                                        imageUrl = details.imageUrl ?: item.imageUrl,
                                                        backdropUrl = backdropUrl
                                                    )
                                                } else item
                                            } else item
                                        } else item
                                    }
                                }
                                val seriesJobs = seriesToEnrich.map { item ->
                                    async {
                                        if (item.source != "tmdb") {
                                            val tmdbId = item.tmdbId
                                            if (tmdbId != null) {
                                                val isMovie = item.type == MediaType.Movie
                                                val details = tmdbApiClient.fetchMediaDetail(tmdbId, isMovie)
                                                if (details != null) {
                                                    val typeStr = when (item.type) {
                                                        MediaType.Movie -> R.string.media_type_movie
                                                        MediaType.TvShow -> R.string.media_type_tv
                                                        MediaType.Anime -> R.string.media_type_anime
                                                        else -> R.string.media_type_anime
                                                    }
                                                    val subtitleParts = buildList {
                                                        add(app.getString(typeStr))
                                                        val yearVal = item.year ?: details.year
                                                        if (yearVal != null && yearVal > 0) add(yearVal.toString())
                                                        addAll(details.genres.take(3))
                                                    }
                                                    val ratingInt = (details.score ?: 0) / 10
                                                    val finalScore = if (ratingInt > 0) ratingInt else null
                                                    val backdropUrl = details.pictures.firstOrNull { it.contains("/w1280/") } ?: item.backdropUrl
                                                    item.copy(
                                                        title = details.title?.takeIf { it.isNotBlank() } ?: item.title,
                                                        subtitle = subtitleParts.joinToString(", "),
                                                        score = finalScore ?: item.score,
                                                        year = details.year ?: item.year,
                                                        imageUrl = details.imageUrl ?: item.imageUrl,
                                                        backdropUrl = backdropUrl
                                                    )
                                                } else item
                                            } else item
                                        } else item
                                    }
                                }
                                continueMovies = movieJobs.mapIndexed { idx, job ->
                                    runCatching { job.await() }.getOrDefault(moviesToEnrich[idx])
                                }
                                continueSeries = seriesJobs.mapIndexed { idx, job ->
                                    runCatching { job.await() }.getOrDefault(seriesToEnrich[idx])
                                }
                                continueMovies = continueMovies + localizedSimklStatusItems(userMovies, "İzleniyor", watchingStatus).drop(8)
                                continueSeries = continueSeries + localizedSimklStatusItems(userShows, "İzleniyor", watchingStatus).drop(8)
                            }

                            ExplorePayload(
                                topAnime      = allTrending,
                                airingAnime   = showsList,
                                upcomingAnime = popularMovies,
                                topManga      = popularShows,
                                publishingManga = topRatedMovies,
                                trendingAnime = trendingMediaList,
                                movieAnime    = moviesList,
                                seasonalAnime = topRatedShows,
                                newlyAddedAnime = popularMediaList,
                                trendingManga = topRatedAnimeList,
                                upcomingMediaTmdb = upcomingMediaList, // Yakında Yayında (Medya)
                                simklContinueMovies = continueMovies,
                                simklPlannedMovies = plannedMovies,
                                simklContinueSeries = continueSeries,
                                simklPlannedSeries = plannedSeries,
                                airingSoonAnime = airingSoonList
                            )
                        }.getOrNull()

                        if (tmdbPayload != null) {
                            platformCache[ExplorePlatform.TMDB] = tmdbPayload
                            loadedPlatforms.add(ExplorePlatform.TMDB)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ExplorePrefetch", "Prefetch failed", e)
                }
            }
        }
    }
}

