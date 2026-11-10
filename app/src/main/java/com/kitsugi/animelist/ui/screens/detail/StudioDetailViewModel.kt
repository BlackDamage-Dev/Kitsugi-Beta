package com.kitsugi.animelist.ui.screens.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiMediaMutationsClient
import com.kitsugi.animelist.data.remote.StudioSourceSupport
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudioDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val apiClient = JikanApiClient()
    private val mutationsClient = KitsugiMediaMutationsClient()
    private val translationManager = TranslationManager(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val TAG = "StudioDetailVM"

    private val _state = MutableStateFlow<StudioDetailState>(StudioDetailState.Loading)
    val state: StateFlow<StudioDetailState> = _state.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private val _isFavourite = MutableStateFlow(false)
    val isFavourite: StateFlow<Boolean> = _isFavourite.asStateFlow()

    /** Galeri tüm kaynaklardan (detay + galeri + yenileme) bitene kadar true. */
    private val galleryTracker = GalleryLoadTracker()
    val galleryLoading: StateFlow<Boolean> = galleryTracker.loading

    /**
     * "Hakkında" metninin gösterilecek hali — detay sayfasındaki Açıklama kartıyla
     * aynı sözleşme: önce ham metin, otomatik çeviri açıksa (veya metin Rusça ise)
     * Türkçe çevirisiyle değiştirilir.
     */
    private val _translatedAbout = MutableStateFlow<String?>(null)
    val translatedAbout: StateFlow<String?> = _translatedAbout.asStateFlow()

    /** "Hakkında" metninde orijinal gösteriliyor mu (çeviri varken "Orijinal" / "Çeviri" geçişi). */
    private val _showAboutOriginal = MutableStateFlow(false)
    val showAboutOriginal: StateFlow<Boolean> = _showAboutOriginal.asStateFlow()

    /**
     * "Hakkında" çevirisi (otomatik ya da elle) sürerken `true`.
     *
     * Detay sayfası bu değeri `LocalKitsugiTranslationLoading` üzerinden sayfadaki çeviri
     * butonuna verir; buton çeviri bitene kadar galeri butonuyla aynı yüklenme
     * (nabız + dönen halka) animasyonunu gösterir.
     */
    private val _translationLoading = MutableStateFlow(false)
    val translationLoading: StateFlow<Boolean> = _translationLoading.asStateFlow()

    /** Sonraki yapım sayfası yükleniyor mu (sonsuz kaydırma). */
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** Son sayfa isteği başarısız oldu; UI "tekrar dene" gösterir. */
    private val _loadMoreFailed = MutableStateFlow(false)
    val loadMoreFailed: StateFlow<Boolean> = _loadMoreFailed.asStateFlow()

    /** Bir sonraki çekilecek yapım sayfası (1. sayfa fetchStudioDetail ile gelir). */
    private var nextWorksPage = 2

    private var currentFetchKey: String? = null
    private var lastStudioId: Int = 0
    private var lastSource: String = ""
    private var lastStudioName: String? = null
    private var lastStudioImageUrl: String? = null
    private var aboutTranslateJob: kotlinx.coroutines.Job? = null

    /** Çeviri işlerinin nesil sayacı — eski işlerin yüklenme durumunu kapatmasını engeller. */
    @Volatile private var translationGeneration = 0
    private var lastAutoTranslateSetting: Boolean? = null
    private var lastTargetLangSetting: String? = null

    private fun studioCacheIdentity(name: String?): String? =
        StudioSourceSupport.normalizeName(name).takeIf { it.isNotEmpty() }

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val prevAuto = lastAutoTranslateSetting
                val prevTarget = lastTargetLangSetting
                lastAutoTranslateSetting = settings.autoTranslateEnabled
                lastTargetLangSetting = settings.translateTargetLanguage
                if (prevAuto == null && prevTarget == null) return@collect
                if (prevAuto != settings.autoTranslateEnabled || prevTarget != settings.translateTargetLanguage) {
                    val detail = (_state.value as? StudioDetailState.Success)?.detail ?: return@collect
                    val rawAbout = detail.about ?: return@collect
                    val expectedKey = currentFetchKey ?: return@collect
                    val isRussian = rawAbout.any { it in '\u0400'..'\u04FF' }
                    if (settings.autoTranslateEnabled || isRussian) {
                        triggerAboutTranslationIfNeeded(
                            rawAbout = rawAbout,
                            source = lastSource,
                            studioId = detail.id.takeIf { it > 0 } ?: lastStudioId,
                            name = lastStudioName ?: detail.name,
                            expectedFetchKey = expectedKey,
                            forceRetranslate = prevTarget != settings.translateTargetLanguage
                        )
                    } else {
                        cancelAboutTranslation()
                        // Orijinal metin "çeviri" olarak işaretlenmez.
                        _translatedAbout.value = null
                    }
                }
            }
        }
    }

    private fun triggerAboutTranslationIfNeeded(
        rawAbout: String?,
        source: String,
        studioId: Int,
        name: String?,
        expectedFetchKey: String?,
        forceRetranslate: Boolean = false
    ) {
        if (rawAbout.isNullOrBlank()) {
            _translatedAbout.value = null
            return
        }
        val identity = studioCacheIdentity(name)
        if (!forceRetranslate) {
            val cachedTr = DetailCache.getTranslation(
                "studio_about", source, studioId, identity, sourceText = rawAbout
            ) ?: DetailCache.getTranslation("studio_about", source, studioId, sourceText = rawAbout)
            if (!cachedTr.isNullOrBlank() &&
                !com.kitsugi.animelist.utils.translationMatchesSource(rawAbout, cachedTr)
            ) {
                _translatedAbout.value = cachedTr
                return
            }
        }

        // Orijinal metni "çeviri" gibi işaretlemeyiz; düğme yalnızca gerçek çeviri varken çıkar.
        _translatedAbout.value = null
        aboutTranslateJob?.cancel()
        val generation = ++translationGeneration
        aboutTranslateJob = viewModelScope.launch {
            try {
                val autoTranslate = runCatching {
                    settingsDataStore.settingsFlow.first().autoTranslateEnabled
                }.getOrDefault(false)
                val isRussian = rawAbout.any { it in '\u0400'..'\u04FF' }
                if (!autoTranslate && !isRussian && !forceRetranslate) return@launch

                _translationLoading.value = true
                val tr = withContext(Dispatchers.IO) {
                    translationManager.translateToTarget(rawAbout) { partial ->
                        if (currentFetchKey == expectedFetchKey &&
                            partial.isNotBlank() &&
                            !com.kitsugi.animelist.utils.translationMatchesSource(rawAbout, partial)
                        ) {
                            _translatedAbout.value = partial
                        }
                    }
                }
                if (currentFetchKey == expectedFetchKey && !tr.isNullOrBlank()) {
                    if (com.kitsugi.animelist.utils.translationMatchesSource(rawAbout, tr)) {
                        DetailCache.removeTranslation("studio_about", source, studioId, identity)
                        DetailCache.removeTranslation("studio_about", source, studioId)
                        _translatedAbout.value = null
                        return@launch
                    }
                    DetailCache.putTranslation("studio_about", source, studioId, tr, identity, sourceText = rawAbout)
                    if (studioId > 0) {
                        DetailCache.putTranslation("studio_about", source, studioId, tr, sourceText = rawAbout)
                    }
                    _translatedAbout.value = tr
                }
            } finally {
                // Daha yeni bir çeviri işi başladıysa yüklenme durumunu ona bırak.
                if (generation == translationGeneration) {
                    _translationLoading.value = false
                }
            }
        }
    }

    /** Çalışan "Hakkında" çevirisini bırakır ve yüklenme animasyonunu kapatır. */
    private fun cancelAboutTranslation() {
        translationGeneration++
        aboutTranslateJob?.cancel()
        aboutTranslateJob = null
        _translationLoading.value = false
        // Yeni stüdyo / otomatik çeviri kapandı: eski orijinal-geçiş durumu geçerli değil.
        _showAboutOriginal.value = false
    }

    /** Orijinal / çeviri geçişi ("Hakkında"). Çeviri bellekte kalır, yeniden çevrilmez. */
    fun toggleAboutOriginal() {
        _showAboutOriginal.value = !_showAboutOriginal.value
    }

    /**
     * @param imageUrl detay sayfasındaki çipten taşınan kurum logosu; kaynak API kendi logosunu
     * vermese de galeri/hero boş kalmasın diye yedek olarak kullanılır.
     */
    fun loadStudio(studioId: Int, source: String, name: String? = null, imageUrl: String? = null) {
        val canonicalSource = StudioSourceSupport.canonicalSource(source) ?: source.lowercase()
        val newKey = "$canonicalSource:$studioId:${StudioSourceSupport.normalizeName(name)}"
        if (newKey == currentFetchKey) {
            Log.d(TAG, "loadStudio: Already loading/loaded key=$newKey — skipping")
            return
        }

        Log.d(TAG, "loadStudio: New key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        cancelAboutTranslation()
        nextWorksPage = 2
        _isLoadingMore.value = false
        _loadMoreFailed.value = false
        lastStudioId = studioId
        lastSource = source
        lastStudioName = name?.takeIf { it.isNotBlank() }
        lastStudioImageUrl = imageUrl?.takeIf { it.isNotBlank() }

        val identity = studioCacheIdentity(lastStudioName)
        val cachedStudioDetail = DetailCache.getStudioDetail(source, studioId, identity)
            ?.takeIf { matchesExpectedStudio(source, lastStudioName, it.name) }
        val cachedAbout = cachedStudioDetail?.about
        val cachedAboutTr = DetailCache.getTranslation(
            "studio_about", source, studioId, identity, sourceText = cachedAbout
        ) ?: DetailCache.getTranslation("studio_about", source, studioId, sourceText = cachedAbout)

        _state.value = if (cachedStudioDetail != null) StudioDetailState.Success(cachedStudioDetail) else StudioDetailState.Loading
        _translatedAbout.value = cachedAboutTr?.takeIf { tr ->
            cachedAbout == null || cachedAbout.isBlank() ||
                !com.kitsugi.animelist.utils.translationMatchesSource(cachedAbout, tr)
        }
        if (cachedStudioDetail != null) _isFavourite.value = cachedStudioDetail.isFavourite

        galleryTracker.reset()
        viewModelScope.launch {
            fetchStudioDetail(studioId, source, expectedFetchKey = newKey, name = lastStudioName)
        }
    }

    fun retry() {
        val studioId = lastStudioId
        val source = lastSource
        val name = lastStudioName
        val expectedFetchKey = currentFetchKey ?: return
        // Kimliksiz (ad ile çözümlenen) Bangumi kurumlarında da yeniden deneme çalışmalı.
        if ((studioId > 0 || !name.isNullOrBlank()) && source.isNotEmpty()) {
            _state.value = StudioDetailState.Loading
            viewModelScope.launch {
                fetchStudioDetail(studioId, source, expectedFetchKey = expectedFetchKey, name = name, force = true)
            }
        }
    }

    /** Stüdyo detayı + galerisi bitene kadar galeri yükleniyor sayılır. */
    private suspend fun fetchStudioDetail(
        studioId: Int,
        source: String,
        expectedFetchKey: String,
        name: String? = null,
        force: Boolean = false
    ) {
        val token = galleryTracker.begin()
        try {
            fetchStudioDetailInternal(studioId, source, expectedFetchKey, name, force)
        } finally {
            galleryTracker.end(token)
        }
    }

    private suspend fun fetchStudioDetailInternal(
        studioId: Int,
        source: String,
        expectedFetchKey: String,
        name: String? = null,
        force: Boolean = false
    ) {
        val identity = studioCacheIdentity(name)
        val cached = DetailCache.getStudioDetail(source, studioId, identity)
            ?.takeIf { matchesExpectedStudio(source, name, it.name) }
        val detail = if (cached != null && !force) {
            cached
        } else {
            val fetched = withContext(Dispatchers.IO) {
                apiClient.fetchStudioDetail(source, studioId, name)
            }?.takeIf { matchesExpectedStudio(source, name, it.name) }

            if (currentFetchKey != expectedFetchKey) return
            if (fetched != null) {
                DetailCache.putStudioDetail(source, studioId, fetched, identity)
            }
            fetched
        }

        if (currentFetchKey != expectedFetchKey) return
        if (detail != null) {
            // Yeni ilk sayfa geldi: sonsuz kaydırma durumu baştan başlar.
            nextWorksPage = 2
            _isLoadingMore.value = false
            _loadMoreFailed.value = false
            _state.value = StudioDetailState.Success(detail)
            _isFavourite.value = detail.isFavourite

            // "Hakkında" — önce ham metin; otomatik çeviri açıksa veya metin Rusça ise
            // detay sayfasındaki Açıklama kartıyla aynı sözleşme izlenir ve Türkçeye çevrilir.
            triggerAboutTranslationIfNeeded(
                rawAbout = detail.about,
                source = source,
                studioId = detail.id.takeIf { it > 0 } ?: studioId,
                name = name ?: detail.name,
                expectedFetchKey = expectedFetchKey,
                forceRetranslate = force
            )

            // Build gallery from studio imageUrl (logo)
            val imageUrl = detail.imageUrl ?: lastStudioImageUrl
            if (!imageUrl.isNullOrBlank()) {
                val category = if (imageUrl.contains("logo", ignoreCase = true) ||
                    imageUrl.contains("image.tmdb.org", ignoreCase = true)) {
                    GalleryCategory.LOGO
                } else {
                    GalleryCategory.POSTER
                }
                val src = when {
                    imageUrl.contains("image.tmdb.org") || imageUrl.contains("tmdb.org") -> "TMDB"
                    imageUrl.contains("shikimori.") -> "Shikimori"
                    imageUrl.contains("anilist.co") -> "AniList"
                    imageUrl.contains("bgm.tv") || imageUrl.contains("bangumi.tv") -> "Bangumi"
                    else -> "Jikan"
                }
                _galleryItems.value = listOf(GalleryItem(url = imageUrl, source = src, category = category, description = lastStudioName))
            } else {
                _galleryItems.value = emptyList()
            }
        } else {
            // Kaynak HATA FIRLATMADAN boş döndüyse (tüm aramalar "bulunamadı") kod
            // "Sınıflandırılamayan hata" (KTS-COM-1020) değil, doğru teşhisi veren
            // "Eşleşme bulunamadı" (KTS-COM-1021) olmalı: kullanıcı ağ hatası sanmasın.
            val recentFailure = com.kitsugi.animelist.data.platform.PlatformFailureRegistry
                .latest(source)?.code
            val errorCode = recentFailure
                ?: com.kitsugi.animelist.data.platform.PlatformErrorCode.COM_1021
            _state.value = StudioDetailState.Error(
                com.kitsugi.animelist.KitsugiApplication.getInstance()
                    ?.getString(com.kitsugi.animelist.R.string.studio_page_load_failed)
                    ?: "Stüdyo detayları yüklenemedi.",
                errorCode,
            )
        }
    }

    /**
     * Sonraki yapım sayfasını çekip mevcut listeye ekler (sonsuz kaydırma).
     * Önbelleğe yazılmaz: önbellek ilk sayfayı tutar, yeniden açılışta tutarlı kalır.
     */
    fun loadMoreWorks() {
        val current = (_state.value as? StudioDetailState.Success)?.detail ?: return
        if (!current.hasMoreWorks || _isLoadingMore.value) return
        val expectedKey = currentFetchKey ?: return
        val page = nextWorksPage
        _isLoadingMore.value = true
        _loadMoreFailed.value = false

        viewModelScope.launch {
            // Çözümlenmiş kimlik (detail.id) kullanılır: Jikan'da chip kimliğinden farklı olabilir.
            val result = withContext(Dispatchers.IO) {
                apiClient.fetchStudioWorksPage(lastSource, current.id, page)
            }
            // Bu arada başka bir stüdyo açıldıysa sonucu uygulama.
            if (currentFetchKey != expectedKey) return@launch

            _isLoadingMore.value = false
            if (result == null) {
                _loadMoreFailed.value = true
                return@launch
            }
            val latest = (_state.value as? StudioDetailState.Success)?.detail ?: return@launch
            nextWorksPage = page + 1
            val merged = (latest.mediaWorks + result.works)
                .distinctBy { it.mediaType to it.mediaId }
            _state.value = StudioDetailState.Success(
                latest.copy(mediaWorks = merged, hasMoreWorks = result.hasMore)
            )
        }
    }

    /**
     * "Hakkında" metnini uygulama içi çeviri motoruyla Türkçeye çevirir —
     * detay sayfasındaki translateSynopsis() ile aynı davranış.
     */
    fun translateAbout() {
        val currentState = _state.value as? StudioDetailState.Success ?: return
        val raw = currentState.detail.about ?: return
        viewModelScope.launch {
            val tr = withContext(Dispatchers.IO) {
                translationManager.translateToTarget(raw)
            }
            if (!tr.isNullOrBlank()) {
                if (com.kitsugi.animelist.utils.translationMatchesSource(raw, tr)) {
                    DetailCache.removeTranslation("studio_about", lastSource, lastStudioId)
                    _translatedAbout.value = null
                } else {
                    DetailCache.putTranslation(
                        "studio_about", lastSource, lastStudioId, tr, sourceText = raw
                    )
                    _translatedAbout.value = tr
                }
            }
        }
    }

    /**
     * AniList stüdyo favori toggle — giriş yapılmış ve aniListId biliniyorsa çalışır.
     */
    fun toggleFavourite() {
        ExternalAuthManager.getAniListToken(context) ?: return
        val currentState = _state.value as? StudioDetailState.Success ?: return
        val detail = currentState.detail
        val targetId = detail.aniListId ?: if (lastSource.lowercase() == "anilist") detail.id else null
        if (targetId == null || targetId <= 0) return

        val newFav = !_isFavourite.value
        _isFavourite.value = newFav
        val updated = detail.copy(isFavourite = newFav)
        _state.value = StudioDetailState.Success(updated)
        DetailCache.putStudioDetail(lastSource, lastStudioId, updated)

        viewModelScope.launch(Dispatchers.IO) {
            val ok = mutationsClient.toggleFavourite("studio", targetId)
            if (!ok) {
                _isFavourite.value = !newFav
                val rolled = detail.copy(isFavourite = !newFav)
                _state.value = StudioDetailState.Success(rolled)
                DetailCache.putStudioDetail(lastSource, lastStudioId, rolled)
            }
        }
    }

    private fun matchesExpectedStudio(source: String, expectedName: String?, actualName: String): Boolean =
        when (StudioSourceSupport.canonicalSource(source)) {
            "jikan", "tmdb", StudioSourceSupport.TMDB_NETWORK_SOURCE ->
                StudioSourceSupport.namesMatch(expectedName, actualName)
            else -> true
        }
}
