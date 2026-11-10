package com.kitsugi.animelist.ui.screens.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClient
import com.kitsugi.animelist.data.remote.KitsugiMediaMutationsClient
import com.kitsugi.animelist.data.remote.KitsugiStaffDetail
import com.kitsugi.animelist.data.remote.KitsugiPersonImageAggregator
import com.kitsugi.animelist.data.remote.RateLimitException
import com.kitsugi.animelist.data.remote.ResourceNotFoundException
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class StaffDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val apiClient = JikanApiClient()
    private val mutationsClient = KitsugiMediaMutationsClient()
    private val translationManager = TranslationManager(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val TAG = "StaffDetailVM"

    private val _state = MutableStateFlow<StaffDetailState>(StaffDetailState.Loading)
    val state: StateFlow<StaffDetailState> = _state.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    /** Galeri tüm kaynaklardan (detay + galeri + yenileme) bitene kadar true. */
    private val galleryTracker = GalleryLoadTracker()
    val galleryLoading: StateFlow<Boolean> = galleryTracker.loading

    private val _translatedBio = MutableStateFlow<String?>(null)
    val translatedBio: StateFlow<String?> = _translatedBio.asStateFlow()

    /** Biyografide orijinal metin gösteriliyor mu (çeviri varken "Orijinal" / "Çeviri" geçişi). */
    private val _showBioOriginal = MutableStateFlow(false)
    val showBioOriginal: StateFlow<Boolean> = _showBioOriginal.asStateFlow()

    /**
     * Biyografi çevirisi (otomatik ya da elle) sürerken `true`.
     *
     * Detay sayfası bu değeri `LocalKitsugiTranslationLoading` üzerinden sayfadaki çeviri
     * butonuna verir; buton çeviri bitene kadar galeri butonuyla aynı yüklenme
     * (nabız + dönen halka) animasyonunu gösterir.
     */
    private val _translationLoading = MutableStateFlow(false)
    val translationLoading: StateFlow<Boolean> = _translationLoading.asStateFlow()

    private val _isFavourite = MutableStateFlow(false)
    val isFavourite: StateFlow<Boolean> = _isFavourite.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var currentFetchKey: String? = null
    private var lastStaffId: Int = 0
    private var lastSource: String = ""
    private var lastStaffName: String? = null
    private var bioTranslateJob: kotlinx.coroutines.Job? = null

    /** Çeviri işlerinin nesil sayacı — eski işlerin yüklenme durumunu kapatmasını engeller. */
    @Volatile private var translationGeneration = 0
    private var lastAutoTranslateSetting: Boolean? = null
    private var lastTargetLangSetting: String? = null

    private fun staffCacheIdentity(name: String?): String? =
        name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val prevAuto = lastAutoTranslateSetting
                val prevTarget = lastTargetLangSetting
                lastAutoTranslateSetting = settings.autoTranslateEnabled
                lastTargetLangSetting = settings.translateTargetLanguage
                if (prevAuto == null && prevTarget == null) return@collect
                if (prevAuto != settings.autoTranslateEnabled || prevTarget != settings.translateTargetLanguage) {
                    val detail = (_state.value as? StaffDetailState.Success)?.detail ?: return@collect
                    val bio = detail.biography ?: return@collect
                    val expectedKey = currentFetchKey ?: return@collect
                    val isRussian = bio.any { it in '\u0400'..'\u04FF' }
                    if (settings.autoTranslateEnabled || isRussian) {
                        triggerBioTranslationIfNeeded(
                            bio = bio,
                            source = lastSource,
                            staffId = lastStaffId,
                            name = lastStaffName ?: detail.name,
                            expectedFetchKey = expectedKey,
                            forceRetranslate = prevTarget != settings.translateTargetLanguage
                        )
                    } else {
                        cancelBioTranslation()
                        _translatedBio.value = null
                    }
                }
            }
        }
    }

    private fun triggerBioTranslationIfNeeded(
        bio: String?,
        source: String,
        staffId: Int,
        name: String?,
        expectedFetchKey: String?,
        forceRetranslate: Boolean = false
    ) {
        if (bio.isNullOrBlank()) return
        val identity = staffCacheIdentity(name)
        if (!forceRetranslate) {
            val cachedTranslation = DetailCache.getTranslation(
                "bio_staff", source, staffId, identity, sourceText = bio
            ) ?: DetailCache.getTranslation("bio_staff", source, staffId, sourceText = bio)
            if (!cachedTranslation.isNullOrBlank() &&
                !com.kitsugi.animelist.utils.translationMatchesSource(bio, cachedTranslation)
            ) {
                _translatedBio.value = cachedTranslation
                return
            }
        }

        bioTranslateJob?.cancel()
        val generation = ++translationGeneration
        bioTranslateJob = viewModelScope.launch {
            try {
                val autoTranslate = runCatching { settingsDataStore.settingsFlow.first().autoTranslateEnabled }.getOrDefault(false)
                val isRussian = bio.any { it in '\u0400'..'\u04FF' }
                if (!autoTranslate && !isRussian && !forceRetranslate) return@launch

                _translationLoading.value = true
                val tr = withContext(Dispatchers.IO) {
                    translationManager.translateToTarget(bio) { partial ->
                        if (currentFetchKey == expectedFetchKey &&
                            partial.isNotBlank() &&
                            !com.kitsugi.animelist.utils.translationMatchesSource(bio, partial)
                        ) {
                            _translatedBio.value = partial
                        }
                    }
                }
                if (currentFetchKey == expectedFetchKey && tr.isNotBlank()) {
                    if (com.kitsugi.animelist.utils.translationMatchesSource(bio, tr)) {
                        DetailCache.removeTranslation("bio_staff", source, staffId, identity)
                        DetailCache.removeTranslation("bio_staff", source, staffId)
                        _translatedBio.value = null
                        return@launch
                    }
                    DetailCache.putTranslation("bio_staff", source, staffId, tr, identity, sourceText = bio)
                    DetailCache.putTranslation("bio_staff", source, staffId, tr, sourceText = bio)
                    _translatedBio.value = tr
                }
            } finally {
                // Daha yeni bir çeviri işi başladıysa yüklenme durumunu ona bırak.
                if (generation == translationGeneration) {
                    _translationLoading.value = false
                }
            }
        }
    }

    /** Çalışan biyografi çevirisini bırakır ve yüklenme animasyonunu kapatır. */
    private fun cancelBioTranslation() {
        translationGeneration++
        bioTranslateJob?.cancel()
        bioTranslateJob = null
        _translationLoading.value = false
        // Yeni kişi / otomatik çeviri kapandı: eski orijinal-geçiş durumu geçerli değil.
        _showBioOriginal.value = false
    }

    /** Orijinal / çeviri geçişi (biyografi). Çeviri bellekte kalır, yeniden çevrilmez. */
    fun toggleBioOriginal() {
        _showBioOriginal.value = !_showBioOriginal.value
    }

    fun translateBio() {
        val detail = (_state.value as? StaffDetailState.Success)?.detail ?: return
        val bio = detail.biography ?: return
        triggerBioTranslationIfNeeded(
            bio = bio,
            source = lastSource,
            staffId = lastStaffId,
            name = lastStaffName ?: detail.name,
            expectedFetchKey = currentFetchKey,
            forceRetranslate = true
        )
    }

    fun loadStaff(staffId: Int, source: String, name: String? = null) {
        val newKey = "$source:$staffId:${staffCacheIdentity(name).orEmpty()}"
        if (newKey == currentFetchKey) {
            Log.d(TAG, "loadStaff: Cache hit for key=$newKey — skipping")
            return
        }

        Log.d(TAG, "loadStaff: New key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        cancelBioTranslation()
        lastStaffId = staffId
        lastSource = source
        lastStaffName = name

        val identity = staffCacheIdentity(name)
        val cachedStaffDetail = DetailCache.getStaffDetail(source, staffId)
        val cachedBio = cachedStaffDetail?.biography
        val cachedBioTranslation = DetailCache.getTranslation(
            "bio_staff", source, staffId, identity, sourceText = cachedBio
        ) ?: DetailCache.getTranslation("bio_staff", source, staffId, sourceText = cachedBio)

        _state.value = if (cachedStaffDetail != null) StaffDetailState.Success(cachedStaffDetail) else StaffDetailState.Loading
        _translatedBio.value = cachedBioTranslation?.takeIf { tr ->
            cachedBio == null || cachedBio.isBlank() ||
                !com.kitsugi.animelist.utils.translationMatchesSource(cachedBio, tr)
        }

        galleryTracker.reset()
        viewModelScope.launch {
            fetchStaffDetail(staffId, source, name)
        }
    }

    fun retry() {
        val staffId = lastStaffId
        val source = lastSource
        val name = lastStaffName
        // Kimlik yerine ada göre açılan kişilerde (staffId<=0) da yeniden denenebilsin.
        if ((staffId > 0 || !name.isNullOrBlank()) && source.isNotEmpty()) {
            _state.value = StaffDetailState.Loading
            _translatedBio.value = null
            viewModelScope.launch {
                fetchStaffDetail(staffId, source, name, force = true)
            }
        }
    }

    fun forceRefresh() {
        val staffId = lastStaffId
        val source = lastSource
        val name = lastStaffName
        if ((staffId > 0 || !name.isNullOrBlank()) && source.isNotEmpty()) {
            _isRefreshing.value = true
            viewModelScope.launch {
                try {
                    fetchStaffDetail(staffId, source, name, force = true)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
    }

    /** Kişi detayı + galerisi bitene kadar galeri yükleniyor sayılır. */
    private suspend fun fetchStaffDetail(staffId: Int, source: String, name: String? = null, force: Boolean = false) {
        val token = galleryTracker.begin()
        try {
            fetchStaffDetailInternal(staffId, source, name, force)
        } finally {
            galleryTracker.end(token)
        }
    }

    private suspend fun fetchStaffDetailInternal(staffId: Int, source: String, name: String? = null, force: Boolean = false) {
        val expectedFetchKey = currentFetchKey
        val identity = staffCacheIdentity(name)
        if (force) {
            DetailCache.removeStaffDetail(source, staffId)
            DetailCache.removeTranslation("bio_staff", source, staffId, identity)
            DetailCache.removeTranslation("bio_staff", source, staffId)
        }
        val cached = DetailCache.getStaffDetail(source, staffId)
        val detail = if (cached != null && !force) {
            cached
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.fetchStaffDetail(source, staffId, name)
                }
            }.onFailure { err ->
                val msg = when (err) {
                    is RateLimitException -> "Hız limitine takıldık. Lütfen birkaç saniye bekleyip tekrar deneyin."
                    is ResourceNotFoundException -> "Bu ekip üyesi bulunamadı."
                    is java.io.IOException -> "Ağ bağlantısı hatası. Lütfen bağlantınızı kontrol edin."
                    else -> "Ekip üyesi detayları yüklenemedi."
                }
                Log.e(TAG, "fetchStaffDetail error: ${err.message}", err)
                if (_state.value !is StaffDetailState.Success) {
                    _state.value = StaffDetailState.Error(msg, com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve(source, err))
                }
            }.getOrNull()
        }

        if (currentFetchKey != expectedFetchKey) return
        if (detail != null) {
            DetailCache.putStaffDetail(source, staffId, detail)
            _state.value = StaffDetailState.Success(detail)
            _isFavourite.value = detail.isFavourite

            // Biyografi çevirisini galeri ve ek ağ çağrılarını beklemeden HEMEN başlat!
            triggerBioTranslationIfNeeded(
                bio = detail.biography,
                source = source,
                staffId = staffId,
                name = name ?: detail.name,
                expectedFetchKey = expectedFetchKey,
                forceRetranslate = force
            )

            if (isBangumiSource(source)) {
                refreshBangumiFavourite(detail.id)
                warmBangumiStaffTitles(detail)
            }

            // Build gallery from imageUrl + Jikan /people pictures
            // Kimlik ada göre çözümlendiyse galeri de çözümlenmiş kimlikle istenir.
            buildStaffGallery(detail.id.takeIf { it > 0 } ?: staffId, source, detail.imageUrl)
        } else if (_state.value !is StaffDetailState.Success) {
            _state.value = StaffDetailState.Error(
                KitsugiStrings.get(R.string.ekip_uyesi_detaylari_yuklenemedi),
                com.kitsugi.animelist.data.platform.PlatformFailureRegistry.currentOrUnknown(source),
            )
        }
    }

    /**
     * Kişi (seslendirmen / oyuncu / personel) galerisi: birincil görsel + **tüm kaynaklardan**
     * toplanan ek görseller (bkz. [KitsugiPersonImageAggregator]).
     *
     * Kimlik çözüm kuralları:
     *  - Jikan/MAL: kimlik zaten MAL kişi kimliği.
     *  - Shikimori: kişi kimliği MAL kimliğiyle aynıdır.
     *  - AniList/Bangumi/TMDB/Kitsu kimlikleri MAL uzayında DEĞİLDİR — Jikan sorgusu yapılmaz
     *    (eski koddaki 404/alakasız-kişi riski korunmuş olur).
     *  - TMDB: kaynak tmdb ise kimlik TMDB kişi kimliğidir → profil fotoğrafı listesi açılır;
     *    diğer kaynaklarda kimlik isimle (sıkı eşleşme) çözülür.
     */
    private suspend fun buildStaffGallery(staffId: Int, source: String, mainImageUrl: String?) {
        val detail = (_state.value as? StaffDetailState.Success)?.detail
        val canonical = when (source.lowercase().trim()) {
            "mal", "jikan", "myanimelist" -> "jikan"
            else -> source.lowercase().trim()
        }

        val malId = when (canonical) {
            "jikan" -> staffId.takeIf { it > 0 }
            "shikimori" -> detail?.id?.takeIf { it > 0 }
            else -> null
        }
        val aniListId = detail?.aniListId?.takeIf { it > 0 }
            ?: if (canonical == "anilist" && staffId < 100_000_000) staffId.takeIf { it > 0 } else null

        val names = buildList {
            detail?.name?.let(::add)
            lastStaffName?.let(::add)
            detail?.nativeName?.let(::add)
            detail?.romanizedName?.let(::add)
            detail?.englishName?.let(::add)
            detail?.alternativeNames?.let(::addAll)
        }.filter { it.isNotBlank() }.distinct()

        val tmdbEnabled = runCatching { settingsDataStore.settingsFlow.first() }
            .getOrNull()?.tmdbEnabled ?: true

        val friendlySource = when (source.lowercase().trim()) {
            "kitsu" -> "Kitsu"
            "anilist" -> "AniList"
            "shikimori" -> "Shikimori"
            "simkl" -> "Simkl"
            "tmdb" -> "TMDB"
            "bangumi", "bgm" -> "Bangumi"
            "mal", "jikan" -> "MyAnimeList"
            else -> "Kişi"
        }
        val description = detail?.name ?: lastStaffName

        // Publish the hero photo immediately. Other providers can take noticeably longer;
        // keeping this first item available lets the photo open in the gallery meanwhile.
        _galleryItems.value = ensureStaffHeroGalleryItem(
            galleryItems = emptyList(),
            imageUrl = mainImageUrl,
            source = friendlySource,
            description = description
        )

        val aggregated = runCatching {
            KitsugiPersonImageAggregator.aggregate(
                kind = KitsugiPersonImageAggregator.PersonKind.PERSON,
                source = source,
                id = staffId,
                malId = malId,
                aniListId = aniListId,
                names = names,
                tmdbEnabled = tmdbEnabled
            )
        }.getOrDefault(emptyList())

        val items = buildList {
            if (!mainImageUrl.isNullOrBlank()) {
                add(GalleryItem(url = mainImageUrl, source = friendlySource, category = GalleryCategory.PERSON, description = description))
            }
            for (img in aggregated) {
                if (img.url != mainImageUrl && img.url.isNotBlank()) {
                    add(GalleryItem(url = img.url, source = img.source, category = GalleryCategory.PERSON, description = description))
                }
            }
        }.distinctBy { it.url }

        _galleryItems.value = items
    }

    /**
     * AniList ekip üyesi favori toggle — giriş yapılmış ve aniListId biliniyorsa çalışır.
     */
    fun toggleFavourite() {
        if (isBangumiSource(lastSource)) {
            toggleBangumiFavourite()
            return
        }
        ExternalAuthManager.getAniListToken(context) ?: return
        val currentState = _state.value as? StaffDetailState.Success ?: return
        val detail = currentState.detail
        val targetId = detail.aniListId ?: if (lastSource.lowercase() == "anilist") detail.id else null
        if (targetId == null || targetId <= 0) return

        val newFav = !_isFavourite.value
        _isFavourite.value = newFav
        val updated = detail.copy(isFavourite = newFav)
        _state.value = StaffDetailState.Success(updated)
        DetailCache.putStaffDetail(lastSource, lastStaffId, updated)

        viewModelScope.launch(Dispatchers.IO) {
            val ok = mutationsClient.toggleFavourite("staff", targetId)
            if (!ok) {
                _isFavourite.value = !newFav
                val rolled = detail.copy(isFavourite = !newFav)
                _state.value = StaffDetailState.Success(rolled)
                DetailCache.putStaffDetail(lastSource, lastStaffId, rolled)
            }
        }
    }

    private fun isBangumiSource(source: String): Boolean {
        val key = source.trim().lowercase()
        return key == "bangumi" || key == "bgm"
    }

    /** Bangumi kişi favori durumunu token sahibinin koleksiyonundan okur (bağlıysa). */
    private fun refreshBangumiFavourite(personId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!BangumiAuthStore.isConnected(context)) return@launch
            val token = BangumiAuthStore.getValidToken(context) ?: return@launch
            val username = BangumiAuthStore.getUsername(context)?.takeIf { it.isNotBlank() } ?: "-"
            val favourite = runCatching {
                BangumiApiClient.isPersonFavourite(token, username, personId)
            }.getOrNull() ?: return@launch
            _isFavourite.value = favourite
            val current = (_state.value as? StaffDetailState.Success)?.detail ?: return@launch
            if (current.id != personId) return@launch
            val updated = current.copy(isFavourite = favourite)
            _state.value = StaffDetailState.Success(updated)
            DetailCache.putStaffDetail(lastSource, lastStaffId, updated)
        }
    }

    /**
     * Uzun filmografiler için arka plan başlık çözümü: senkron zenginleştirmenin süre
     * bütçesine sığmayan CJK başlıkları çözer, kalıcı önbelleğe yazar ve değişiklik
     * varsa ekranı günceller. Böylece ikinci ziyarette liste tamamen Latin başlıkla açılır.
     */
    private fun warmBangumiStaffTitles(detail: KitsugiStaffDetail) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching { KitsugiBangumiDetailClient.enrichStaffDetailNames(detail, background = true) }
                    .getOrNull()
            } ?: return@launch
            if (updated == detail) return@launch
            if (lastStaffId != detail.id || !isBangumiSource(lastSource)) return@launch
            // Eşzamanlı favori güncellemesinin isFavourite alanını ezmesini engelle.
            val merged = updated.copy(
                isFavourite = (_state.value as? StaffDetailState.Success)?.detail?.isFavourite
                    ?: updated.isFavourite
            )
            DetailCache.putStaffDetail(lastSource, detail.id, merged)
            _state.value = StaffDetailState.Success(merged)
        }
    }

    /**
     * Bangumi kişi favorisi: `POST` / `DELETE /v0/persons/{id}/collect`.
     * İyimser güncelleme yapılır; istek başarısız olursa önceki duruma dönülür.
     */
    private fun toggleBangumiFavourite() {
        if (!BangumiAuthStore.isConnected(context)) return
        val currentState = _state.value as? StaffDetailState.Success ?: return
        val detail = currentState.detail
        val newFav = !_isFavourite.value
        _isFavourite.value = newFav
        val updated = detail.copy(isFavourite = newFav)
        _state.value = StaffDetailState.Success(updated)
        DetailCache.putStaffDetail(lastSource, lastStaffId, updated)

        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val token = BangumiAuthStore.getValidToken(context) ?: return@runCatching false
                BangumiApiClient.setPersonFavourite(token, detail.id, newFav)
                true
            }.getOrDefault(false)
            if (!ok) {
                _isFavourite.value = !newFav
                val rolled = detail.copy(isFavourite = !newFav)
                _state.value = StaffDetailState.Success(rolled)
                DetailCache.putStaffDetail(lastSource, lastStaffId, rolled)
            }
        }
    }
}
